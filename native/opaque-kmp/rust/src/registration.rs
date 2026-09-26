use crate::{cipher_suite::{ksf, SerenitySuite}, Identifiers, OpaqueError, SecretBytes};
use opaque_ke::{rand::rngs::OsRng, ClientRegistration, ClientRegistrationFinishParameters, RegistrationResponse};
use std::sync::{Arc, Mutex};
use zeroize::Zeroizing;

#[derive(uniffi::Object)]
pub struct RegistrationState {
    inner: Mutex<Option<ClientRegistration<SerenitySuite>>>,
}

impl std::fmt::Debug for RegistrationState {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.write_str("RegistrationState([REDACTED])")
    }
}

#[uniffi::export]
impl RegistrationState {
    pub fn clear(&self) -> Result<(), OpaqueError> {
        self.inner.lock().map_err(|_| OpaqueError::StateConsumed)?.take();
        Ok(())
    }
}

#[derive(uniffi::Record)]
pub struct RegistrationStartResult {
    pub registration_request: Vec<u8>,
    pub client_registration_state: Arc<RegistrationState>,
}

#[derive(uniffi::Record)]
pub struct RegistrationFinishResult {
    pub registration_record: Vec<u8>,
    pub export_key: Arc<SecretBytes>,
    pub server_static_public_key: Vec<u8>,
}

#[uniffi::export]
pub fn client_registration_start(password: Vec<u8>) -> Result<RegistrationStartResult, OpaqueError> {
    let password = Zeroizing::new(password);
    let result = ClientRegistration::<SerenitySuite>::start(&mut OsRng, &password)
        .map_err(|_| OpaqueError::ProtocolFailure)?;
    Ok(RegistrationStartResult {
        registration_request: result.message.serialize().to_vec(),
        client_registration_state: Arc::new(RegistrationState { inner: Mutex::new(Some(result.state)) }),
    })
}

#[uniffi::export]
pub fn client_registration_finish(
    password: Vec<u8>,
    client_registration_state: Arc<RegistrationState>,
    registration_response: Vec<u8>,
    identifiers: Option<Identifiers>,
) -> Result<RegistrationFinishResult, OpaqueError> {
    let password = Zeroizing::new(password);
    // Consume before parsing, so every finish attempt is single-use, also on failure.
    let state = client_registration_state.inner.lock().map_err(|_| OpaqueError::StateConsumed)?
        .take().ok_or(OpaqueError::StateConsumed)?;
    let response = RegistrationResponse::deserialize(&registration_response)
        .map_err(|_| OpaqueError::InvalidMessage)?;
    let identifiers = identifiers.unwrap_or_default();
    let ksf = ksf();
    let result = state.finish(&mut OsRng, &password, response,
        ClientRegistrationFinishParameters::new(identifiers.borrowed(), Some(&ksf)))
        .map_err(|_| OpaqueError::ProtocolFailure)?;
    let export_key = Zeroizing::new(result.export_key);
    Ok(RegistrationFinishResult {
        registration_record: result.message.serialize().to_vec(),
        export_key: SecretBytes::new(&export_key),
        server_static_public_key: result.server_s_pk.serialize().to_vec(),
    })
}
