use argon2::{Algorithm, Argon2, Params, Version};
use opaque_ke::CipherSuite;
use generic_array::{ArrayLength, GenericArray};
use zeroize::Zeroizing;

pub struct SerenitySuite;

impl CipherSuite for SerenitySuite {
    type OprfCs = opaque_ke::Ristretto255;
    type KeyExchange = opaque_ke::TripleDh<opaque_ke::Ristretto255, sha2::Sha512>;
    type Ksf = SerenityKsf;
}

pub struct SerenityKsf(Argon2<'static>);

impl Default for SerenityKsf {
    fn default() -> Self { ksf() }
}

impl opaque_ke::ksf::Ksf for SerenityKsf {
    fn hash<L: ArrayLength<u8>>(&self, input: GenericArray<u8, L>)
        -> Result<GenericArray<u8, L>, opaque_ke::errors::InternalError> {
        let input = Zeroizing::new(input);
        let mut output = Zeroizing::new(GenericArray::default());
        // Delegate all hashing to RustCrypto. Own and erase its working allocation.
        let mut memory = Zeroizing::new(vec![argon2::Block::default(); self.0.params().block_count()]);
        self.0.hash_password_into_with_memory(&input, &[0u8; 16], &mut output, &mut *memory)
            .map_err(|_| opaque_ke::errors::InternalError::KsfError)?;
        Ok((*output).clone())
    }
}

pub fn ksf() -> SerenityKsf {
    // Always pass this explicitly: Argon2::default() has different costs.
    SerenityKsf(Argon2::new(
        Algorithm::Argon2id,
        Version::V0x13,
        Params::new(65_536, 3, 4, None).expect("fixed valid Argon2 parameters"),
    ))
}
