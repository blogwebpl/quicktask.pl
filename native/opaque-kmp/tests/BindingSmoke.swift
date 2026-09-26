import Foundation
import OpaqueKmp

func smoke() throws {
    let password = Data("test-only-password".utf8)
    let registration = try clientRegistrationStart(password: password)
    assert(registration.registrationRequest.count == 32)
    try registration.clientRegistrationState.clear()
    let login = try clientLoginStart(password: password)
    try login.clientLoginState.clear()
}
