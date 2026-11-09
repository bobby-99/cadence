#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

use cadence_core::quick;
use cadence_core::recurrence::{date_from_epoch_day, epoch_day, Recurrence};
use cadence_core::store::{NewTask, Store};
use cadence_core::SyncTask;
use chrono::{Local, NaiveDate};
use slint::{ComponentHandle, ModelRc, VecModel};
use std::cell::RefCell;
use std::rc::Rc;
use std::time::SystemTime;

slint::include_modules!();

fn device_name() -> String {
    std::env::var("COMPUTERNAME").or_else(|_| std::env::var("HOSTNAME")).unwrap_or_else(|_| "PC".into())
}

fn time_parts(min: Option<i64>) -> (String, String) {
    match min {
        None => ("All".into(), "day".into()),
        Some(m) => {
            let (h, mm) = (m / 60, m % 60);
            let h12 = if h % 12 == 0 { 12 } else { h % 12 };
            (format!("{h12}:{mm:02}"), if h < 12 { "am".into() } else { "pm".into() })
        }
    }
}

fn day_label(d: NaiveDate, today: NaiveDate) -> String {
    match (d - today).num_days() {
        0 => "Today".into(),
        1 => "Tomorrow".into(),
        -1 => "Yesterday".into(),
        _ => d.format("%a %-d %b").to_string(),
    }
}

fn to_item(t: &SyncTask, today: NaiveDate, now_min: i64) -> TaskItem {
    let date = t.due_date.and_then(date_from_epoch_day);
    let overdue = !t.completed
        && date.map_or(false, |d| d < today || (d == today && t.due_time.map_or(false, |m| m < now_min)));
    let (top, bottom) = time_parts(t.due_time);
    let mut sub: Vec<String> = Vec::new();
    if let Some(d) = date {
        sub.push(day_label(d, today));
    }
    if let Some(r) = t.recurrence.as_deref().and_then(Recurrence::parse) {
        sub.push(format!("\u{21BB} {}", r.describe()));
    }
    match t.priority {
        1 => sub.push("Low".into()),
        2 => sub.push("Medium".into()),
        3 => sub.push("High".into()),
        _ => {}
    }
    TaskItem {
        uuid: t.uuid.clone().into(),
        title: t.title.clone().into(),
        time_top: top.into(),
        time_bottom: bottom.into(),
        sub: sub.join("  \u{00B7}  ").into(),
        priority: t.priority as i32,
        done: t.completed,
        overdue,
    }
}

fn refresh(ui: &MainWindow, store: &Store) {
    let now = Local::now();
    let today = now.date_naive();
    let now_min = (now.format("%H").to_string().parse::<i64>().unwrap_or(0)) * 60 + now.format("%M").to_string().parse::<i64>().unwrap_or(0);

    let mut open: Vec<&SyncTask> = store.doc.tasks.values().filter(|t| !t.completed).collect();
    // undated last; otherwise by date, then by time (all-day first), then by creation
    open.sort_by_key(|t| (t.due_date.unwrap_or(i64::MAX), t.due_time.unwrap_or(-1), t.created_at));
    let mut done: Vec<&SyncTask> = store.doc.tasks.values().filter(|t| t.completed).collect();
    done.sort_by_key(|t| std::cmp::Reverse(t.completed_at.unwrap_or(0)));
    done.truncate(15);

    let today_total = store.doc.tasks.values().filter(|t| t.due_date == Some(epoch_day(today))).count();
    let today_done = store.doc.tasks.values().filter(|t| t.due_date == Some(epoch_day(today)) && t.completed).count();
    let today_left = today_total - today_done;
    let (heading, sub) = if open.is_empty() {
        ("All clear.".to_string(), "Nothing left to do".to_string())
    } else if today_left == 0 {
        ("All clear today.".to_string(), format!("{today_done} of {today_total} done"))
    } else {
        (format!("{today_left} to do today."), format!("{today_done} of {today_total} done"))
    };

    let items: Vec<TaskItem> = open.iter().chain(done.iter()).map(|t| to_item(t, today, now_min)).collect();
    ui.set_tasks(ModelRc::new(VecModel::from(items)));
    ui.set_date_line(today.format("%a %-d %b").to_string().to_uppercase().into());
    ui.set_heading(heading.into());
    ui.set_sub_heading(sub.into());

    let s = &store.settings;
    ui.set_sync_on(s.sync_folder.is_some());
    ui.set_folder_text(s.sync_folder.clone().unwrap_or_default().into());
    ui.set_sync_error(!s.last_sync_error.is_empty());
    ui.set_sync_text(if !s.last_sync_error.is_empty() {
        s.last_sync_error.clone()
    } else if s.last_sync_ms > 0 {
        let t = chrono::DateTime::from_timestamp_millis(s.last_sync_ms).map(|d| d.with_timezone(&Local).format("Last sync %-d %b, %-I:%M %p").to_string()).unwrap_or_default();
        t
    } else {
        String::new()
    }
    .into());
}

/// One round of sync; records the result in the settings. Returns true if the list changed.
fn do_sync(store: &mut Store) -> bool {
    if store.settings.sync_folder.is_none() {
        return false;
    }
    match store.sync(&device_name()) {
        Ok(r) => {
            store.settings.last_sync_ms = cadence_core::now_ms();
            store.settings.last_sync_error.clear();
            store.save();
            r.applied
        }
        Err(e) => {
            store.settings.last_sync_error = e.to_string();
            store.save();
            false
        }
    }
}

fn main() {
    let ui = MainWindow::new().expect("window");
    let store = Rc::new(RefCell::new(Store::load(&Store::default_path())));
    let stamp: Rc<RefCell<Option<SystemTime>>> = Rc::new(RefCell::new(None));

    do_sync(&mut store.borrow_mut());
    *stamp.borrow_mut() = store.borrow().sync_file_stamp();
    refresh(&ui, &store.borrow());

    // after any change: save, sync, redraw
    let apply = {
        let store = store.clone();
        let stamp = stamp.clone();
        let weak = ui.as_weak();
        Rc::new(move || {
            let mut s = store.borrow_mut();
            s.save();
            do_sync(&mut s);
            *stamp.borrow_mut() = s.sync_file_stamp();
            if let Some(ui) = weak.upgrade() {
                refresh(&ui, &s);
            }
        })
    };

    {
        let store = store.clone();
        let apply = apply.clone();
        ui.on_add(move |text| {
            let today = Local::now().date_naive();
            let q = quick::parse(text.as_str(), today);
            store.borrow_mut().add(NewTask {
                title: q.title, due_date: q.date, due_time: q.time, priority: q.priority,
                reminders: if q.date.is_some() { "0".into() } else { String::new() }, ..Default::default()
            });
            apply();
        });
    }
    {
        let store = store.clone();
        let apply = apply.clone();
        ui.on_toggle(move |id| {
            let today = Local::now().date_naive();
            {
                let mut s = store.borrow_mut();
                let done = s.doc.tasks.get(id.as_str()).map_or(false, |t| t.completed);
                if done { s.uncomplete(id.as_str()); } else { s.complete(id.as_str(), today); }
            }
            apply();
        });
    }
    {
        let store = store.clone();
        let apply = apply.clone();
        ui.on_remove(move |id| {
            store.borrow_mut().delete(id.as_str());
            apply();
        });
    }
    {
        let store = store.clone();
        let apply = apply.clone();
        ui.on_rename(move |id, title| {
            let title = title.trim().to_string();
            if !title.is_empty() {
                let mut s = store.borrow_mut();
                if let Some(t) = s.doc.tasks.get(id.as_str()).cloned() {
                    s.update(id.as_str(), NewTask {
                        title, notes: t.notes, due_date: t.due_date.and_then(date_from_epoch_day), due_time: t.due_time,
                        priority: t.priority, recurrence: t.recurrence, reminders: t.reminders,
                    });
                }
            }
            apply();
        });
    }
    {
        let store = store.clone();
        let apply = apply.clone();
        ui.on_cycle_priority(move |id| {
            {
                let mut s = store.borrow_mut();
                if let Some(t) = s.doc.tasks.get(id.as_str()).cloned() {
                    s.update(id.as_str(), NewTask {
                        title: t.title, notes: t.notes, due_date: t.due_date.and_then(date_from_epoch_day), due_time: t.due_time,
                        priority: (t.priority + 1) % 4, recurrence: t.recurrence, reminders: t.reminders,
                    });
                }
            }
            apply();
        });
    }
    {
        let store = store.clone();
        let apply = apply.clone();
        ui.on_pick_folder(move || {
            if let Some(dir) = rfd::FileDialog::new().set_title("Choose the sync folder").pick_folder() {
                store.borrow_mut().settings.sync_folder = Some(dir.to_string_lossy().into_owned());
                apply();
            }
        });
    }
    {
        let store = store.clone();
        let apply = apply.clone();
        ui.on_stop_sync(move || {
            {
                let mut s = store.borrow_mut();
                s.settings.sync_folder = None;
                s.settings.last_sync_error.clear();
            }
            apply();
        });
    }
    {
        let apply = apply.clone();
        ui.on_sync_now(move || apply());
    }

    // notice the phone writing the file (checks one small file's timestamp every 3 seconds),
    // and keep "overdue" and the date line fresh
    let timer = slint::Timer::default();
    {
        let store = store.clone();
        let stamp = stamp.clone();
        let weak = ui.as_weak();
        timer.start(slint::TimerMode::Repeated, std::time::Duration::from_secs(3), move || {
            let mut s = store.borrow_mut();
            let now_stamp = s.sync_file_stamp();
            if now_stamp != *stamp.borrow() {
                do_sync(&mut s);
                *stamp.borrow_mut() = s.sync_file_stamp();
            }
            if let Some(ui) = weak.upgrade() {
                refresh(&ui, &s);
            }
        });
    }

    ui.run().expect("run");
    store.borrow().save();
}
