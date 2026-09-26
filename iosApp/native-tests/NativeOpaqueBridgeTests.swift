import Foundation
import OpaqueKmp
import XCTest
@testable import QuickTask

// The iosAppNativeTests scheme runs these checks in the QuickTask host app.
// They require the actual Rust/Swift framework and Apple runtimes.
final class NativeOpaqueBridgeTests: XCTestCase {
    func testRegistrationAndLoginUseTheNativeLibrary() throws {
        let bridge = NativeOpaqueBridge()
        let password = "Native OPAQUE: zażółć gęślą 🔐"
        let registration = try bridge.startRegistration(password: password, stateId: "registration")
        let login = try bridge.startLogin(password: password, stateId: "login")
        XCTAssertFalse(try OpaqueKmp.base64UrlDecode(encoded: registration).isEmpty)
        XCTAssertFalse(try OpaqueKmp.base64UrlDecode(encoded: login).isEmpty)
        bridge.discardState(stateId: "registration")
        bridge.discardState(stateId: "login")
    }

    func testMalformedResponseConsumesLoginStateAndDoesNotExposeInputs() throws {
        let bridge = NativeOpaqueBridge()
        let password = "synthetic-password-never-in-errors"
        let response = "invalid response never in errors!"
        _ = try bridge.startLogin(password: password, stateId: "login")
        XCTAssertThrowsError(try bridge.finishLogin(
            password: password,
            stateId: "login",
            loginResponse: response,
            email: "synthetic@example.test",
            serverOrigin: "https://compatibility.example.test"
        )) { error in
            let description = (error as NSError).localizedDescription
            XCTAssertFalse(description.contains(password))
            XCTAssertFalse(description.contains(response))
        }
        // Reusing the handle is possible only if the failed finish removed its old state.
        XCTAssertNoThrow(try bridge.startLogin(password: password, stateId: "login"))
    }

    func testWrongOperationConsumesItsState() throws {
        let bridge = NativeOpaqueBridge()
        _ = try bridge.startLogin(password: "synthetic", stateId: "same-handle")
        XCTAssertThrowsError(try bridge.finishRegistration(
            password: "synthetic",
            stateId: "same-handle",
            registrationResponse: "invalid",
            email: "synthetic@example.test",
            serverOrigin: "https://compatibility.example.test"
        ))
        XCTAssertNoThrow(try bridge.startRegistration(password: "synthetic", stateId: "same-handle"))
    }

    func testRegistrationAndLoginShareThePendingLimitAndDiscardReleasesCapacity() throws {
        let bridge = NativeOpaqueBridge()
        for index in 0..<16 {
            _ = try bridge.startLogin(password: "synthetic", stateId: "login-\(index)")
            _ = try bridge.startRegistration(password: "synthetic", stateId: "registration-\(index)")
        }
        XCTAssertThrowsError(try bridge.startLogin(password: "synthetic", stateId: "overflow"))
        bridge.discardState(stateId: "registration-0")
        XCTAssertNoThrow(try bridge.startLogin(password: "synthetic", stateId: "overflow"))
        XCTAssertThrowsError(try bridge.startLogin(password: "synthetic", stateId: "login-0"))
    }
}
