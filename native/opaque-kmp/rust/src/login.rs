use crate::{cipher_suite::{ksf, SerenitySuite}, Identifiers, OpaqueError, SecretBytes};
use opaque_ke::{rand::rngs::OsRng, ClientLogin, ClientLoginFinishParameters, CredentialResponse};
use std::sync::{Arc, Mutex};
use zeroize::Zeroizing;

#[derive(uniffi::Object)]
pub struct LoginState {
    inner: Mutex<Option<ClientLogin<SerenitySuite>>>,
}

impl std::fmt::Debug for LoginState {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.write_str("LoginState([REDACTED])")
    }
}

#[uniffi::export]
impl LoginState {
    pub fn clear(&self) -> Result<(), OpaqueError> {
        self.inner.lock().map_err(|_| OpaqueError::StateConsumed)?.take();
        Ok(())
    }
}

#[derive(uniffi::Record)]
pub struct LoginStartResult {
    pub credential_request: Vec<u8>,
    pub client_login_state: Arc<LoginState>,
}

#[derive(uniffi::Record)]
pub struct LoginFinishResult {
    pub credential_finalization: Vec<u8>,
    pub session_key: Arc<SecretBytes>,
    pub export_key: Arc<SecretBytes>,
    pub server_static_public_key: Vec<u8>,
}

#[uniffi::export]
pub fn client_login_start(password: Vec<u8>) -> Result<LoginStartResult, OpaqueError> {
    let password = Zeroizing::new(password);
    let result = ClientLogin::<SerenitySuite>::start(&mut OsRng, &password)
        .map_err(|_| OpaqueError::ProtocolFailure)?;
    Ok(LoginStartResult {
        credential_request: result.message.serialize().to_vec(),
        client_login_state: Arc::new(LoginState { inner: Mutex::new(Some(result.state)) }),
    })
}

#[uniffi::export]
pub fn client_login_finish(
    password: Vec<u8>,
    client_login_state: Arc<LoginState>,
    credential_response: Vec<u8>,
    identifiers: Option<Identifiers>,
) -> Result<LoginFinishResult, OpaqueError> {
    let password = Zeroizing::new(password);
    let state = client_login_state.inner.lock().map_err(|_| OpaqueError::StateConsumed)?
        .take().ok_or(OpaqueError::StateConsumed)?;
    let response = CredentialResponse::deserialize(&credential_response)
        .map_err(|_| OpaqueError::InvalidMessage)?;
    let identifiers = identifiers.unwrap_or_default();
    let ksf = ksf();
    let result = state.finish(&mut OsRng, &password, response,
        ClientLoginFinishParameters::new(None, identifiers.borrowed(), Some(&ksf)))
        .map_err(|_| OpaqueError::AuthenticationFailed)?;
    let session_key = Zeroizing::new(result.session_key);
    let export_key = Zeroizing::new(result.export_key);
    Ok(LoginFinishResult {
        credential_finalization: result.message.serialize().to_vec(),
        session_key: SecretBytes::new(&session_key),
        export_key: SecretBytes::new(&export_key),
        server_static_public_key: result.server_s_pk.serialize().to_vec(),
    })
}
