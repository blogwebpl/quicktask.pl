//! Test-only stdin/stdout bridge. Never ship this executable or use real credentials.
use opaque_kmp::*;
use serde_json::{json, Value};
use std::{collections::HashMap, io::{self, BufRead, Write}, sync::Arc};

fn bytes(v: &Value, name: &str) -> Result<Vec<u8>, OpaqueError> {
    base64_url_decode(v[name].as_str().ok_or(OpaqueError::InvalidMessage)?.to_owned())
}

fn main() {
    let mut registrations: HashMap<u64, Arc<RegistrationState>> = HashMap::new();
    let mut logins: HashMap<u64, Arc<LoginState>> = HashMap::new();
    let mut next_id = 0u64;
    for line in io::stdin().lock().lines() {
        let line = zeroize::Zeroizing::new(line.expect("stdin read failed"));
        let mut v: Value = serde_json::from_str(&line).expect("invalid test JSON");
        let result = (|| -> Result<Value, OpaqueError> {
            let password = v["password"].as_str().unwrap_or_default().as_bytes().to_vec();
            let password = zeroize::Zeroizing::new(password);
            let ids = v.get("identifiers").map(|ids| Identifiers {
                client: ids["client"].as_str().map(|s| s.as_bytes().to_vec()),
                server: ids["server"].as_str().map(|s| s.as_bytes().to_vec()),
            });
            let id = v["state"].as_u64().unwrap_or_default();
            match v["op"].as_str().unwrap_or_default() {
                "registrationStart" => {
                    let r = client_registration_start(password.to_vec())?;
                    next_id += 1;
                    registrations.insert(next_id, r.client_registration_state);
                    Ok(json!({"state": next_id, "registrationRequest": base64_url_encode(r.registration_request)}))
                }
                "registrationFinish" => {
                    let state = registrations.remove(&id).ok_or(OpaqueError::StateConsumed)?;
                    let r = client_registration_finish(password.to_vec(), state, bytes(&v, "response")?, ids)?;
                    Ok(json!({"registrationRecord": base64_url_encode(r.registration_record),
                        "exportKey": base64_url_encode(r.export_key.bytes()?),
                        "serverStaticPublicKey": base64_url_encode(r.server_static_public_key)}))
                }
                "loginStart" => {
                    let r = client_login_start(password.to_vec())?;
                    next_id += 1;
                    logins.insert(next_id, r.client_login_state);
                    Ok(json!({"state": next_id, "startLoginRequest": base64_url_encode(r.credential_request)}))
                }
                "loginFinish" => {
                    let state = logins.remove(&id).ok_or(OpaqueError::StateConsumed)?;
                    let r = client_login_finish(password.to_vec(), state, bytes(&v, "response")?, ids)?;
                    Ok(json!({"finishLoginRequest": base64_url_encode(r.credential_finalization),
                        "sessionKey": base64_url_encode(r.session_key.bytes()?),
                        "exportKey": base64_url_encode(r.export_key.bytes()?),
                        "serverStaticPublicKey": base64_url_encode(r.server_static_public_key)}))
                }
                _ => Err(OpaqueError::InvalidMessage),
            }
        })();
        if let Some(Value::String(password)) = v.get_mut("password") {
            zeroize::Zeroize::zeroize(password);
        }
        let output = match result {
            Ok(value) => value,
            Err(error) => json!({"error": error.to_string()}),
        };
        // This is the test transport, not an application log. The parent never prints it.
        let output = zeroize::Zeroizing::new(output.to_string());
        println!("{}", &*output);
        io::stdout().flush().expect("stdout write failed");
    }
}
