//! The sync file (`cadence-sync.json`), its parser and the merge. See docs/SYNC.md.

use serde::{Deserialize, Serialize};
use std::collections::BTreeMap;

pub const FORMAT: &str = "cadence-sync";
pub const VERSION: i64 = 1;
pub const FILE: &str = "cadence-sync.json";
pub const KEEP_DELETIONS_MS: i64 = 90 * 86_400_000;

#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct SyncTask {
    pub uuid: String,
    #[serde(default)]
    pub title: String,
    #[serde(default)]
    pub notes: String,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub due_date: Option<i64>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub due_time: Option<i64>,
    #[serde(default)]
    pub priority: i64,
    #[serde(default)]
    pub completed: bool,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub completed_at: Option<i64>,
    #[serde(default)]
    pub created_at: i64,
    #[serde(default)]
    pub reminders: String,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub recurrence: Option<String>,
    #[serde(default = "one")]
    pub occurrence: i64,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub series: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub duration_min: Option<i64>,
    #[serde(default)]
    pub updated_at: i64,
}

fn one() -> i64 {
    1
}

#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
pub struct Deleted {
    pub uuid: String,
    #[serde(default)]
    pub at: i64,
}

/// What one device knows: its live tasks and its deletions, both keyed by uuid.
#[derive(Clone, Debug, Default, PartialEq)]
pub struct SyncDoc {
    pub tasks: BTreeMap<String, SyncTask>,
    pub deleted: BTreeMap<String, i64>,
}

#[derive(Serialize, Deserialize)]
struct FileRepr {
    format: String,
    #[serde(default)]
    version: i64,
    #[serde(default)]
    device: String,
    #[serde(default, rename = "writtenAt")]
    written_at: i64,
    #[serde(default)]
    tasks: Vec<SyncTask>,
    #[serde(default)]
    deleted: Vec<Deleted>,
}

#[derive(Debug, PartialEq)]
pub enum ParseError {
    /// Not a Cadence sync file (or not JSON at all).
    Invalid,
    /// Written by a newer version of Cadence.
    TooNew,
}

pub fn parse(text: &str) -> Result<SyncDoc, ParseError> {
    let f: FileRepr = serde_json::from_str(text).map_err(|_| ParseError::Invalid)?;
    if f.format != FORMAT {
        return Err(ParseError::Invalid);
    }
    if f.version > VERSION {
        return Err(ParseError::TooNew);
    }
    let mut doc = SyncDoc::default();
    for t in f.tasks {
        if !t.uuid.trim().is_empty() {
            doc.tasks.insert(t.uuid.clone(), t);
        }
    }
    for d in f.deleted {
        doc.deleted.insert(d.uuid, d.at);
    }
    Ok(doc)
}

pub fn to_json(doc: &SyncDoc, device: &str, now: i64) -> String {
    let f = FileRepr {
        format: FORMAT.into(),
        version: VERSION,
        device: device.into(),
        written_at: now,
        tasks: doc.tasks.values().cloned().collect(),
        deleted: doc.deleted.iter().map(|(u, at)| Deleted { uuid: u.clone(), at: *at }).collect(),
    };
    serde_json::to_string_pretty(&f).unwrap_or_default()
}

/// Newest change wins per uuid; a deletion wins when it is at least as new as the task.
/// Symmetrical: `merge(a, b) == merge(b, a)`.
pub fn merge(local: &SyncDoc, remote: &SyncDoc, now: i64) -> SyncDoc {
    let mut out = SyncDoc::default();
    let mut keys: Vec<&String> = Vec::new();
    keys.extend(local.tasks.keys());
    keys.extend(remote.tasks.keys());
    keys.extend(local.deleted.keys());
    keys.extend(remote.deleted.keys());
    keys.sort();
    keys.dedup();
    for u in keys {
        let task = match (local.tasks.get(u), remote.tasks.get(u)) {
            (None, r) => r,
            (l, None) => l,
            (Some(l), Some(r)) => Some(if r.updated_at > l.updated_at { r } else { l }),
        };
        let del = match (local.deleted.get(u), remote.deleted.get(u)) {
            (Some(a), Some(b)) => Some(*a.max(b)),
            (Some(a), None) => Some(*a),
            (None, Some(b)) => Some(*b),
            (None, None) => None,
        };
        match (task, del) {
            (t, Some(d)) if t.map_or(true, |t| d >= t.updated_at) => {
                if d >= now - KEEP_DELETIONS_MS {
                    out.deleted.insert(u.clone(), d);
                }
            }
            (Some(t), _) => {
                out.tasks.insert(u.clone(), t.clone());
            }
            _ => {}
        }
    }
    out
}

#[cfg(test)]
mod tests {
    use super::*;

    const NOW: i64 = 1_800_000_000_000;

    fn t(uuid: &str, title: &str, at: i64) -> SyncTask {
        SyncTask {
            uuid: uuid.into(), title: title.into(), notes: String::new(), due_date: None, due_time: None,
            priority: 0, completed: false, completed_at: None, created_at: 1, reminders: String::new(),
            recurrence: None, occurrence: 1, series: None, duration_min: None, updated_at: at,
        }
    }

    fn doc(tasks: &[SyncTask], deleted: &[(&str, i64)]) -> SyncDoc {
        SyncDoc {
            tasks: tasks.iter().map(|x| (x.uuid.clone(), x.clone())).collect(),
            deleted: deleted.iter().map(|(u, a)| (u.to_string(), *a)).collect(),
        }
    }

    #[test]
    fn newer_edit_wins() {
        let m = merge(&doc(&[t("a", "old", 100)], &[]), &doc(&[t("a", "new", 200)], &[]), NOW);
        assert_eq!(m.tasks["a"].title, "new");
    }

    #[test]
    fn deletion_rules() {
        let gone = merge(&doc(&[t("a", "x", 100)], &[]), &doc(&[], &[("a", NOW - 10)]), NOW);
        assert!(gone.tasks.is_empty());
        assert_eq!(gone.deleted["a"], NOW - 10);
        let back = merge(&doc(&[t("a", "x", NOW)], &[]), &doc(&[], &[("a", NOW - 10)]), NOW);
        assert!(back.tasks.contains_key("a") && back.deleted.is_empty());
    }

    #[test]
    fn symmetrical() {
        let a = doc(&[t("a", "A1", 5), t("c", "C", 9)], &[("d", NOW - 5)]);
        let b = doc(&[t("a", "A2", 7), t("d", "D", 1)], &[("c", NOW - 3)]);
        assert_eq!(merge(&a, &b, NOW), merge(&b, &a, NOW));
    }

    #[test]
    fn old_deletions_forgotten() {
        let m = merge(&doc(&[], &[("a", NOW - KEEP_DELETIONS_MS - 1)]), &SyncDoc::default(), NOW);
        assert!(m.deleted.is_empty());
    }

    #[test]
    fn round_trip_and_refusals() {
        let mut x = t("a", "Call mom", 5);
        x.due_date = Some(20000);
        x.due_time = Some(1080);
        x.priority = 3;
        x.recurrence = Some("{\"u\":\"DAY\"}".into());
        x.series = Some("a".into());
        let d = doc(&[x], &[("z", 9)]);
        assert_eq!(parse(&to_json(&d, "pc", NOW)).unwrap(), d);
        assert_eq!(parse("{\"format\":\"cadence-sync\",\"version\":99}"), Err(ParseError::TooNew));
        assert_eq!(parse("{\"format\":\"other\"}"), Err(ParseError::Invalid));
        assert_eq!(parse("nope"), Err(ParseError::Invalid));
    }

    /// A file written by the phone (org.json output): nulls and extra fields must not matter.
    #[test]
    fn reads_phone_style_file() {
        let s = r#"{"format":"cadence-sync","version":1,"device":"Pixel","writtenAt":1,
          "tasks":[{"uuid":"ab","title":"Run","notes":"","dueDate":20000,"priority":2,"completed":false,
          "createdAt":5,"reminders":"0","recurrence":"{\"u\":\"WEEK\"}","occurrence":3,"series":"ab","updatedAt":7,"future":"x"}],
          "deleted":[{"uuid":"zz","at":4}]}"#;
        let d = parse(s).unwrap();
        assert_eq!(d.tasks["ab"].occurrence, 3);
        assert_eq!(d.tasks["ab"].due_time, None);
        assert_eq!(d.deleted["zz"], 4);
    }
}
