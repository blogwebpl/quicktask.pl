import Dispatch
import Foundation
import OpaqueKmp
import Shared

/// Calls the same Rust wrapper as Android; no protocol state is serialized into Kotlin.
final class NativeOpaqueBridge: NSObject, AppleOpaqueBridge {
    private enum State {
        case login(OpaqueKmp.LoginState)
        case registration(OpaqueKmp.RegistrationState)

        func clear() {
            switch self {
            case .login(let value): try? value.clear()
            case .registration(let value): try? value.clear()
            }
        }
    }

    private struct Pending {
        let state: State
        let expiresAt: TimeInterval
    }

    private enum Failure: LocalizedError {
        case authenticationFailed
        case invalidMessage
        case stateUnavailable
        case tooManyOperations
        case protocolFailure

        var errorDescription: String? {
            switch self {
            case .authenticationFailed: return "OPAQUE authentication failed (401)."
            case .invalidMessage: return "Invalid OPAQUE message (400)."
            case .stateUnavailable: return "OPAQUE state expired or was already used."
            case .tooManyOperations: return "Too many pending OPAQUE operations."
            case .protocolFailure: return "OPAQUE operation failed."
            }
        }
    }

    // Kotlin calls this synchronous interface on Dispatchers.Default. A serial queue also
    // bounds concurrent Argon2 memory use and owns all Rust state handles.
    private let queue: DispatchQueue
    private let expiryTimer: DispatchSourceTimer
    private var pending: [String: Pending] = [:]
    private let lifetime: TimeInterval = 120
    private let maximumPending = 32

    override init() {
        let serialQueue = DispatchQueue(label: "pl.quicktask.opaque", qos: .userInitiated)
        queue = serialQueue
        expiryTimer = DispatchSource.makeTimerSource(queue: serialQueue)
        super.init()
        expiryTimer.schedule(deadline: .now() + 1, repeating: .seconds(1))
        expiryTimer.setEventHandler { [weak self] in self?.pruneExpired() }
        expiryTimer.resume()
    }

    deinit {
        expiryTimer.cancel()
        // No caller can be using the instance once deinit begins. Do not sync onto
        // queue here: the last reference may have been released by the expiry handler.
        pending.values.forEach { $0.state.clear() }
    }

    func startLogin(password: String, stateId: String) throws -> String {
        try perform {
            try reserve(stateId)
            var passwordBytes = Data(password.utf8)
            defer { passwordBytes.resetBytes(in: 0..<passwordBytes.count) }
            let result = try OpaqueKmp.clientLoginStart(password: passwordBytes)
            pending[stateId] = Pending(
                state: .login(result.clientLoginState),
                expiresAt: ProcessInfo.processInfo.systemUptime + lifetime
            )
            return OpaqueKmp.base64UrlEncode(bytes: result.credentialRequest)
        }
    }

    func startRegistration(password: String, stateId: String) throws -> String {
        try perform {
            try reserve(stateId)
            var passwordBytes = Data(password.utf8)
            defer { passwordBytes.resetBytes(in: 0..<passwordBytes.count) }
            let result = try OpaqueKmp.clientRegistrationStart(password: passwordBytes)
            pending[stateId] = Pending(
                state: .registration(result.clientRegistrationState),
                expiresAt: ProcessInfo.processInfo.systemUptime + lifetime
            )
            return OpaqueKmp.base64UrlEncode(bytes: result.registrationRequest)
        }
    }

    func finishLogin(
        password: String,
        stateId: String,
        loginResponse: String,
        email: String,
        serverOrigin: String
    ) throws -> Shared.OpaqueFinishResult {
        try perform {
            let saved = try take(stateId)
            defer { saved.clear() }
            guard case .login(let state) = saved else { throw Failure.stateUnavailable }
            var passwordBytes = Data(password.utf8)
            defer { passwordBytes.resetBytes(in: 0..<passwordBytes.count) }
            let result = try OpaqueKmp.clientLoginFinish(
                password: passwordBytes,
                clientLoginState: state,
                credentialResponse: OpaqueKmp.base64UrlDecode(encoded: loginResponse),
                identifiers: OpaqueKmp.Identifiers(client: Data(email.utf8), server: Data(serverOrigin.utf8))
            )
            defer {
                try? result.sessionKey.clear()
                try? result.exportKey.clear()
            }
            var exportBytes = try result.exportKey.bytes()
            defer { exportBytes.resetBytes(in: 0..<exportBytes.count) }
            return Shared.OpaqueFinishResult(
                finishLoginRequest: OpaqueKmp.base64UrlEncode(bytes: result.credentialFinalization),
                exportKey: OpaqueKmp.base64UrlEncode(bytes: exportBytes)
            )
        }
    }

    func finishRegistration(
        password: String,
        stateId: String,
        registrationResponse: String,
        email: String,
        serverOrigin: String
    ) throws -> Shared.OpaqueRegisterFinishResult {
        try perform {
            let saved = try take(stateId)
            defer { saved.clear() }
            guard case .registration(let state) = saved else { throw Failure.stateUnavailable }
            var passwordBytes = Data(password.utf8)
            defer { passwordBytes.resetBytes(in: 0..<passwordBytes.count) }
            let result = try OpaqueKmp.clientRegistrationFinish(
                password: passwordBytes,
                clientRegistrationState: state,
                registrationResponse: OpaqueKmp.base64UrlDecode(encoded: registrationResponse),
                identifiers: OpaqueKmp.Identifiers(client: Data(email.utf8), server: Data(serverOrigin.utf8))
            )
            defer { try? result.exportKey.clear() }
            var exportBytes = try result.exportKey.bytes()
            defer { exportBytes.resetBytes(in: 0..<exportBytes.count) }
            return Shared.OpaqueRegisterFinishResult(
                registrationRecord: OpaqueKmp.base64UrlEncode(bytes: result.registrationRecord),
                exportKey: OpaqueKmp.base64UrlEncode(bytes: exportBytes)
            )
        }
    }

    func discardState(stateId: String) {
        queue.sync { pending.removeValue(forKey: stateId)?.state.clear() }
    }

    private func reserve(_ stateId: String) throws {
        guard !stateId.isEmpty, pending[stateId] == nil else { throw Failure.stateUnavailable }
        guard pending.count < maximumPending else { throw Failure.tooManyOperations }
    }

    private func take(_ stateId: String) throws -> State {
        guard let value = pending.removeValue(forKey: stateId) else { throw Failure.stateUnavailable }
        return value.state
    }

    private func pruneExpired() {
        let now = ProcessInfo.processInfo.systemUptime
        let expired = pending.filter { $0.value.expiresAt <= now }.map(\.key)
        expired.forEach { pending.removeValue(forKey: $0)?.state.clear() }
    }

    private func perform<T>(_ action: () throws -> T) throws -> T {
        try queue.sync {
            pruneExpired()
            do {
                return try action()
            } catch let error as Failure {
                throw error
            } catch let error as OpaqueKmp.OpaqueError {
                switch error {
                case .AuthenticationFailed: throw Failure.authenticationFailed
                case .InvalidMessage, .InvalidBase64: throw Failure.invalidMessage
                case .StateConsumed: throw Failure.stateUnavailable
                case .ProtocolFailure: throw Failure.protocolFailure
                @unknown default: throw Failure.protocolFailure
                }
            } catch {
                // Never forward Rust/UniFFI diagnostics or protocol inputs into application logs.
                throw Failure.protocolFailure
            }
        }
    }
}
