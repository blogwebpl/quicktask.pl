mod cipher_suite;
mod login;
mod registration;

pub use login::*;
pub use registration::*;
use base64::{engine::general_purpose::URL_SAFE_NO_PAD, Engine};
use std::sync::Mutex;
use zeroize::Zeroizing;

uniffi::setup_scaffolding!();

#[derive(Debug, thiserror::Error, uniffi::Error)]
pub enum OpaqueError {
    #[error("Invalid protocol message")]
    InvalidMessage,
    #[error("Authentication failed")]
    AuthenticationFailed,
    #[error("State was already consumed or is unavailable")]
    StateConsumed,
    #[error("Protocol operation failed")]
    ProtocolFailure,
    #[error("Invalid unpadded URL-safe Base64")]
    InvalidBase64,
}

#[derive(Default, uniffi::Record)]
pub struct Identifiers {
    pub client: Option<Vec<u8>>,
    pub server: Option<Vec<u8>>,
}

impl Identifiers {
    fn borrowed(&self) -> opaque_ke::Identifiers<'_> {
        opaque_ke::Identifiers {
            client: self.client.as_deref(),
            server: self.server.as_deref(),
        }
    }
}

/// Owns key material in Rust. Only bytes() makes a foreign-language copy.
#[derive(uniffi::Object)]
pub struct SecretBytes {
    inner: Mutex<Option<Zeroizing<Vec<u8>>>>,
}

impl std::fmt::Debug for SecretBytes {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.write_str("SecretBytes([REDACTED])")
    }
}

impl SecretBytes {
    fn new(bytes: &[u8]) -> std::sync::Arc<Self> {
        std::sync::Arc::new(Self { inner: Mutex::new(Some(Zeroizing::new(bytes.to_vec()))) })
    }
}

#[uniffi::export]
impl SecretBytes {
    pub fn bytes(&self) -> Result<Vec<u8>, OpaqueError> {
        self.inner.lock().map_err(|_| OpaqueError::StateConsumed)?
            .as_ref().map(|b| b.to_vec()).ok_or(OpaqueError::StateConsumed)
    }

    pub fn clear(&self) -> Result<(), OpaqueError> {
        self.inner.lock().map_err(|_| OpaqueError::StateConsumed)?.take();
        Ok(())
    }
}

#[uniffi::export]
pub fn base64_url_encode(bytes: Vec<u8>) -> String {
    let bytes = Zeroizing::new(bytes);
    URL_SAFE_NO_PAD.encode(&*bytes)
}

#[uniffi::export]
pub fn base64_url_decode(encoded: String) -> Result<Vec<u8>, OpaqueError> {
    let encoded = Zeroizing::new(encoded);
    URL_SAFE_NO_PAD.decode(encoded.as_bytes()).map_err(|_| OpaqueError::InvalidBase64)
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::sync::{Arc, Barrier};

    #[test]
    fn explicit_clear_revokes_all_secret_handles() {
        let secret = SecretBytes::new(&[42; 64]);
        let other_handle = Arc::clone(&secret);
        assert_eq!(secret.bytes().unwrap().len(), 64);
        secret.clear().unwrap();
        assert!(matches!(other_handle.bytes(), Err(OpaqueError::StateConsumed)));
        secret.clear().unwrap();
    }

    #[test]
    fn concurrent_finish_consumes_registration_state_once() {
        let start = client_registration_start(b"test-only".to_vec()).unwrap();
        let barrier = Arc::new(Barrier::new(2));
        let handles: Vec<_> = (0..2).map(|_| {
            let state = Arc::clone(&start.client_registration_state);
            let barrier = Arc::clone(&barrier);
            std::thread::spawn(move || {
                barrier.wait();
                client_registration_finish(b"test-only".to_vec(), state, vec![0], None).err().unwrap()
            })
        }).collect();
        let errors: Vec<_> = handles.into_iter().map(|t| t.join().unwrap()).collect();
        assert_eq!(errors.iter().filter(|e| matches!(e, OpaqueError::InvalidMessage)).count(), 1);
        assert_eq!(errors.iter().filter(|e| matches!(e, OpaqueError::StateConsumed)).count(), 1);
    }

    #[test]
    fn canceled_login_cannot_finish() {
        let start = client_login_start(b"test-only".to_vec()).unwrap();
        start.client_login_state.clear().unwrap();
        assert!(matches!(client_login_finish(b"test-only".to_vec(), start.client_login_state, vec![], None),
            Err(OpaqueError::StateConsumed)));
    }
}
