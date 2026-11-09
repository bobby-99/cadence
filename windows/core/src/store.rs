//! The desktop's own list: kept in one JSON file, changed through a few operations that stamp
//! `updatedAt`, and synced with the shared folder.

use crate::recurrence::{date_from_epoch_day, epoch_day, Recurrence};
use crate::sync::{self, SyncDoc, SyncTask};
use crate::{new_uuid, now_ms};
use chrono::NaiveDate;
use serde::{Deserialize, Serialize};
use std::path::{Path, PathBuf};

#[derive(Clone, Debug, Default, Serialize, Deserialize)]
pub struct Settings {
    #[serde(default)]
    pub sync_folder: Option<String>,
    #[serde(default)]
    pub last_sync_ms: i64,
    #[serde(default)]
    pub last_sync_error: String,
}

#[derive(Serialize, Deserialize, Default)]
struct StateFile {
    #[serde(default)]
    tasks: Vec<SyncTask>,
    #[serde(default)]
    deleted: Vec<sync::Deleted>,
    #[serde(default)]
    settings: Settings,
}

pub struct Store {
    path: Option<PathBuf>,
    pub doc: SyncDoc,
    pub settings: Settings,
}

#[derive(Debug, Default, PartialEq)]
pub struct SyncReport {
    /// This device's list changed because of what was in the file.
    pub applied: bool,
    /// The file was written.
    pub wrote: bool,
}

#[derive(Debug, PartialEq)]
pub enum SyncError {
    NoFolder,
    /// The folder or file could not be read; nothing was changed.
    Unreadable(String),
    NotAFile,
    TooNew,
    WriteFailed(String),
}

impl std::fmt::Display for SyncError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            SyncError::NoFolder => write!(f, "No sync folder chosen"),
            SyncError::Unreadable(e) => write!(f, "Could not read the sync file: {e}"),
            SyncError::NotAFile => write!(f, "The sync file is not readable. Nothing was changed."),
            SyncError::TooNew => write!(f, "The sync file is from a newer Cadence. Update this app."),
            SyncError::WriteFailed(e) => write!(f, "Could not write the sync file: {e}"),
        }
    }
}

/// What to change on a task from the editor.
#[derive(Clone, Debug, Default)]
pub struct NewTask {
    pub title: String,
    pub notes: String,
    pub due_date: Option<NaiveDate>,
    /// minutes after midnight
    pub due_time: Option<i64>,
    pub priority: i64,
    pub recurrence: Option<String>,
    pub reminders: String,
}

impl Store {
    /// A list that is never saved (for tests).
    pub fn in_memory() -> Store {
        Store { path: None, doc: SyncDoc::default(), settings: Settings::default() }
    }

    /// Loads the list from `path`, or starts empty if there is no file yet.
    pub fn load(path: &Path) -> Store {
        let mut s = Store { path: Some(path.to_path_buf()), doc: SyncDoc::default(), settings: Settings::default() };
        if let Ok(text) = std::fs::read_to_string(path) {
            if let Ok(f) = serde_json::from_str::<StateFile>(&text) {
                for t in f.tasks {
                    s.doc.tasks.insert(t.uuid.clone(), t);
                }
                for d in f.deleted {
                    s.doc.deleted.insert(d.uuid, d.at);
                }
                s.settings = f.settings;
            }
        }
        s
    }

    pub fn default_path() -> PathBuf {
        let base = std::env::var_os("APPDATA")
            .map(PathBuf::from)
            .or_else(|| std::env::var_os("HOME").map(|h| PathBuf::from(h).join(".local").join("share")))
            .unwrap_or_else(|| PathBuf::from("."));
        base.join("Cadence").join("state.json")
    }

    pub fn save(&self) {
        let Some(path) = &self.path else { return };
        let f = StateFile {
            tasks: self.doc.tasks.values().cloned().collect(),
            deleted: self.doc.deleted.iter().map(|(u, at)| sync::Deleted { uuid: u.clone(), at: *at }).collect(),
            settings: self.settings.clone(),
        };
        if let Some(dir) = path.parent() {
            let _ = std::fs::create_dir_all(dir);
        }
        if let Ok(json) = serde_json::to_string(&f) {
            let tmp = path.with_extension("json.tmp");
            if std::fs::write(&tmp, json).is_ok() {
                let _ = std::fs::rename(&tmp, path);
            }
        }
    }

    // ---- changing the list ----

    pub fn add(&mut self, n: NewTask) -> String {
        let now = now_ms();
        let uuid = new_uuid();
        let series = n.recurrence.as_ref().map(|_| uuid.clone());
        self.doc.tasks.insert(uuid.clone(), SyncTask {
            uuid: uuid.clone(), title: n.title, notes: n.notes,
            due_date: n.due_date.map(epoch_day), due_time: n.due_time, priority: n.priority,
            completed: false, completed_at: None, created_at: now, reminders: n.reminders,
            recurrence: n.recurrence, occurrence: 1, series, duration_min: None, updated_at: now,
        });
        self.doc.deleted.remove(&uuid);
        uuid
    }

    pub fn update(&mut self, uuid: &str, n: NewTask) {
        let now = now_ms();
        if let Some(t) = self.doc.tasks.get_mut(uuid) {
            t.title = n.title;
            t.notes = n.notes;
            t.due_date = n.due_date.map(epoch_day);
            t.due_time = n.due_time;
            t.priority = n.priority;
            t.reminders = n.reminders;
            if t.recurrence.is_none() && n.recurrence.is_some() && t.series.is_none() {
                t.series = Some(t.uuid.clone());
            }
            t.recurrence = n.recurrence;
            t.updated_at = now;
        }
    }

    /// Completes a task. A repeating one gets its next occurrence. Returns the new task's uuid, if any.
    pub fn complete(&mut self, uuid: &str, today: NaiveDate) -> Option<String> {
        let now = now_ms();
        let t = self.doc.tasks.get_mut(uuid)?;
        if t.completed {
            return None;
        }
        t.completed = true;
        t.completed_at = Some(now);
        t.updated_at = now;
        let snapshot = t.clone();
        let rule = snapshot.recurrence.as_deref().and_then(Recurrence::parse)?;
        let due = snapshot.due_date.and_then(date_from_epoch_day)?;
        let next = rule.next(due, today, today, snapshot.occurrence)?;
        let id = new_uuid();
        self.doc.tasks.insert(id.clone(), SyncTask {
            uuid: id.clone(), completed: false, completed_at: None, due_date: Some(epoch_day(next)),
            occurrence: snapshot.occurrence + 1, created_at: now, updated_at: now,
            series: snapshot.series.clone().or(Some(snapshot.uuid.clone())), ..snapshot
        });
        Some(id)
    }

    /// Reopens a task and removes the follow-up occurrence it created, if that is still untouched.
    pub fn uncomplete(&mut self, uuid: &str) {
        let now = now_ms();
        let Some(t) = self.doc.tasks.get(uuid).cloned() else { return };
        if t.recurrence.is_some() {
            if let Some(series) = &t.series {
                let follow: Option<String> = self.doc.tasks.values()
                    .find(|x| x.series.as_ref() == Some(series) && x.occurrence == t.occurrence + 1 && !x.completed)
                    .map(|x| x.uuid.clone());
                if let Some(f) = follow {
                    self.delete(&f);
                }
            }
        }
        if let Some(t) = self.doc.tasks.get_mut(uuid) {
            t.completed = false;
            t.completed_at = None;
            t.updated_at = now;
        }
    }

    pub fn delete(&mut self, uuid: &str) {
        if self.doc.tasks.remove(uuid).is_some() {
            self.doc.deleted.insert(uuid.to_string(), now_ms());
        }
    }

    /// Puts a task back after an Undo.
    pub fn restore(&mut self, mut t: SyncTask) {
        t.updated_at = now_ms();
        self.doc.deleted.remove(&t.uuid);
        self.doc.tasks.insert(t.uuid.clone(), t);
    }

    // ---- sync ----

    /// One round of sync with the folder. Returns what happened; on any problem the list is untouched.
    pub fn sync(&mut self, device: &str) -> Result<SyncReport, SyncError> {
        let folder = self.settings.sync_folder.clone().filter(|f| !f.trim().is_empty()).ok_or(SyncError::NoFolder)?;
        self.sync_with(Path::new(&folder), device, now_ms())
    }

    pub fn sync_with(&mut self, folder: &Path, device: &str, now: i64) -> Result<SyncReport, SyncError> {
        let file = folder.join(sync::FILE);
        let remote = match std::fs::read_to_string(&file) {
            Ok(text) => sync::parse(&text).map_err(|e| match e {
                sync::ParseError::TooNew => SyncError::TooNew,
                sync::ParseError::Invalid => SyncError::NotAFile,
            })?,
            Err(e) if e.kind() == std::io::ErrorKind::NotFound => {
                if !folder.is_dir() {
                    return Err(SyncError::Unreadable("the folder is missing".into()));
                }
                SyncDoc::default()
            }
            Err(e) => return Err(SyncError::Unreadable(e.to_string())),
        };
        let merged = sync::merge(&self.doc, &remote, now);
        let applied = merged != self.doc;
        let mut report = SyncReport { applied, wrote: false };
        if merged != remote {
            let tmp = folder.join(format!("{}.tmp", sync::FILE));
            std::fs::write(&tmp, sync::to_json(&merged, device, now)).map_err(|e| SyncError::WriteFailed(e.to_string()))?;
            std::fs::rename(&tmp, &file).map_err(|e| SyncError::WriteFailed(e.to_string()))?;
            report.wrote = true;
        }
        self.doc = merged;
        Ok(report)
    }

    /// Modification time of the sync file, to notice another device writing it.
    pub fn sync_file_stamp(&self) -> Option<std::time::SystemTime> {
        let folder = self.settings.sync_folder.as_ref()?;
        std::fs::metadata(Path::new(folder).join(sync::FILE)).and_then(|m| m.modified()).ok()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn d(y: i32, m: u32, day: u32) -> NaiveDate { NaiveDate::from_ymd_opt(y, m, day).unwrap() }

    fn nt(title: &str) -> NewTask { NewTask { title: title.into(), ..Default::default() } }

    fn temp(name: &str) -> PathBuf {
        let p = std::env::temp_dir().join(format!("cadence-test-{}-{}", name, new_uuid()));
        std::fs::create_dir_all(&p).unwrap();
        p
    }

    #[test]
    fn completing_a_repeat_makes_the_next_one() {
        let mut s = Store::in_memory();
        let id = s.add(NewTask {
            title: "Run".into(), due_date: Some(d(2026, 11, 14)), due_time: Some(360),
            recurrence: Some(r#"{"u":"WEEK","i":1,"wd":[6]}"#.into()), ..Default::default()
        });
        let next = s.complete(&id, d(2026, 11, 9)).expect("next occurrence");
        assert!(s.doc.tasks[&id].completed);
        let n = &s.doc.tasks[&next];
        assert_eq!((n.occurrence, n.due_date, n.due_time), (2, Some(epoch_day(d(2026, 11, 21))), Some(360)));
        assert_eq!(n.series.as_deref(), Some(id.as_str()));
        // undo removes the follow-up and leaves a deletion mark for it
        s.uncomplete(&id);
        assert!(!s.doc.tasks.contains_key(&next) && s.doc.deleted.contains_key(&next));
        assert!(!s.doc.tasks[&id].completed);
    }

    #[test]
    fn two_devices_converge_through_a_folder() {
        let folder = temp("sync");
        let mut pc = Store::in_memory();
        let mut phone = Store::in_memory();
        let a = pc.add(nt("From the PC"));
        pc.sync_with(&folder, "pc", now_ms()).unwrap();
        phone.add(nt("From the phone"));
        let r = phone.sync_with(&folder, "phone", now_ms()).unwrap();
        assert!(r.applied && r.wrote);
        assert_eq!(phone.doc.tasks.len(), 2);
        // PC picks the phone's task up, then deletes its own; the phone follows
        pc.sync_with(&folder, "pc", now_ms()).unwrap();
        assert_eq!(pc.doc, phone.doc);
        pc.delete(&a);
        pc.sync_with(&folder, "pc", now_ms() + 5).unwrap();
        phone.sync_with(&folder, "phone", now_ms() + 10).unwrap();
        assert_eq!(phone.doc.tasks.len(), 1);
        assert_eq!(pc.doc.tasks.keys().collect::<Vec<_>>(), phone.doc.tasks.keys().collect::<Vec<_>>());
        // nothing left to do: a further round writes nothing
        let again = phone.sync_with(&folder, "phone", now_ms() + 20).unwrap();
        assert_eq!(again, SyncReport { applied: false, wrote: false });
        let _ = std::fs::remove_dir_all(folder);
    }

    #[test]
    fn a_broken_or_newer_file_changes_nothing() {
        let folder = temp("bad");
        let mut s = Store::in_memory();
        s.add(nt("keep me"));
        std::fs::write(folder.join(sync::FILE), "not json").unwrap();
        assert_eq!(s.sync_with(&folder, "pc", now_ms()), Err(SyncError::NotAFile));
        std::fs::write(folder.join(sync::FILE), r#"{"format":"cadence-sync","version":9}"#).unwrap();
        assert_eq!(s.sync_with(&folder, "pc", now_ms()), Err(SyncError::TooNew));
        assert_eq!(std::fs::read_to_string(folder.join(sync::FILE)).unwrap(), r#"{"format":"cadence-sync","version":9}"#);
        assert_eq!(s.doc.tasks.len(), 1);
        assert!(matches!(s.sync_with(&folder.join("missing"), "pc", now_ms()), Err(SyncError::Unreadable(_))));
        let _ = std::fs::remove_dir_all(folder);
    }

    #[test]
    fn save_and_load() {
        let dir = temp("state");
        let path = dir.join("state.json");
        let mut s = Store::load(&path);
        s.settings.sync_folder = Some("X".into());
        let id = s.add(nt("persist"));
        s.delete(&id);
        s.add(nt("stay"));
        s.save();
        let t = Store::load(&path);
        assert_eq!(t.doc.tasks.len(), 1);
        assert!(t.doc.deleted.contains_key(&id));
        assert_eq!(t.settings.sync_folder.as_deref(), Some("X"));
        let _ = std::fs::remove_dir_all(dir);
    }
}
