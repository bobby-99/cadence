//! Shared logic for Cadence for Windows: the task model, repeat rules, the sync file and merge.
//! No UI code lives here, so everything can be tested on any machine.

pub mod quick;
pub mod recurrence;
pub mod store;
pub mod sync;

pub use store::Store;
pub use sync::{SyncDoc, SyncTask};

/// Milliseconds since the Unix epoch.
pub fn now_ms() -> i64 {
    std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .map(|d| d.as_millis() as i64)
        .unwrap_or(0)
}

/// A fresh identity: 32 lowercase hex digits (the same shape the phone uses).
pub fn new_uuid() -> String {
    uuid::Uuid::new_v4().simple().to_string()
}
