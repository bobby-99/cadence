//! Repeat rules: parsing the phone's JSON form and working out the next date.
//! A direct port of `Recurrence.kt`, so both apps repeat tasks the same way.

use chrono::{Datelike, Duration, NaiveDate, Weekday};
use serde_json::Value;
use std::collections::BTreeSet;

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Unit { Day, Week, Month, Year }
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum MonthMode { DayOfMonth, NthWeekday, LastDay }
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum EndType { Never, OnDate, AfterCount }
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Basis { FromDue, FromCompletion }

#[derive(Clone, Debug, PartialEq)]
pub struct Recurrence {
    pub unit: Unit,
    pub interval: i64,
    /// 1 = Monday .. 7 = Sunday
    pub week_days: BTreeSet<u32>,
    pub month_mode: MonthMode,
    pub day_of_month: u32,
    pub nth: u32,
    pub nth_weekday: u32,
    pub end_type: EndType,
    pub end_date: Option<NaiveDate>,
    pub end_count: i64,
    pub basis: Basis,
}

const EPOCH_FROM_CE: i32 = 719_163;

pub fn date_from_epoch_day(d: i64) -> Option<NaiveDate> {
    NaiveDate::from_num_days_from_ce_opt(d as i32 + EPOCH_FROM_CE)
}

pub fn epoch_day(d: NaiveDate) -> i64 {
    (d.num_days_from_ce() - EPOCH_FROM_CE) as i64
}

fn weekday_of(n: u32) -> Weekday {
    match n { 1 => Weekday::Mon, 2 => Weekday::Tue, 3 => Weekday::Wed, 4 => Weekday::Thu, 5 => Weekday::Fri, 6 => Weekday::Sat, _ => Weekday::Sun }
}

impl Recurrence {
    /// Reads the JSON the phone stores (`{"u":"WEEK","i":1,"wd":[6],...}`). Missing keys take defaults.
    pub fn parse(s: &str) -> Option<Recurrence> {
        let v: Value = serde_json::from_str(s).ok()?;
        let o = v.as_object()?;
        let str_of = |k: &str, d: &str| o.get(k).and_then(|x| x.as_str()).unwrap_or(d).to_string();
        let int_of = |k: &str, d: i64| o.get(k).and_then(|x| x.as_i64()).unwrap_or(d);
        Some(Recurrence {
            unit: match str_of("u", "DAY").as_str() { "WEEK" => Unit::Week, "MONTH" => Unit::Month, "YEAR" => Unit::Year, _ => Unit::Day },
            interval: int_of("i", 1).max(1),
            week_days: o.get("wd").and_then(|x| x.as_array()).map(|a| a.iter().filter_map(|n| n.as_u64()).map(|n| n as u32).filter(|n| (1..=7).contains(n)).collect()).unwrap_or_default(),
            month_mode: match str_of("mm", "DAY_OF_MONTH").as_str() { "NTH_WEEKDAY" => MonthMode::NthWeekday, "LAST_DAY" => MonthMode::LastDay, _ => MonthMode::DayOfMonth },
            day_of_month: int_of("dom", 0).max(0) as u32,
            nth: int_of("nth", 1).max(1) as u32,
            nth_weekday: int_of("nwd", 1).clamp(1, 7) as u32,
            end_type: match str_of("et", "NEVER").as_str() { "ON_DATE" => EndType::OnDate, "AFTER_COUNT" => EndType::AfterCount, _ => EndType::Never },
            end_date: o.get("ed").and_then(|x| x.as_i64()).and_then(date_from_epoch_day),
            end_count: int_of("ec", 0),
            basis: if str_of("b", "FROM_DUE") == "FROM_COMPLETION" { Basis::FromCompletion } else { Basis::FromDue },
        })
    }

    /// Short text for a list row: "Every Sat", "Every 2 weeks", "Every month on the 1st".
    pub fn describe(&self) -> String {
        let n = self.interval;
        let base = match self.unit {
            Unit::Day => if n == 1 { "Every day".into() } else { format!("Every {n} days") },
            Unit::Week => {
                let work: BTreeSet<u32> = (1..=5).collect();
                let wkend: BTreeSet<u32> = [6, 7].into_iter().collect();
                let names: Vec<String> = self.week_days.iter().map(|d| weekday_of(*d).to_string()).collect();
                if self.week_days == work && n == 1 { "Every weekday".into() }
                else if self.week_days == wkend && n == 1 { "Every weekend".into() }
                else if self.week_days.is_empty() { if n == 1 { "Every week".into() } else { format!("Every {n} weeks") } }
                else if n == 1 { format!("Every {}", names.join(", ")) }
                else { format!("Every {n} weeks on {}", names.join(", ")) }
            }
            Unit::Month => {
                let every = if n == 1 { "Every month".to_string() } else { format!("Every {n} months") };
                match self.month_mode {
                    MonthMode::DayOfMonth => if self.day_of_month > 0 { format!("{every} on the {}", ordinal(self.day_of_month)) } else { every },
                    MonthMode::LastDay => format!("{every} on the last day"),
                    MonthMode::NthWeekday => {
                        let which = if self.nth >= 5 { "last".to_string() } else { ordinal(self.nth) };
                        format!("{every} on the {which} {}", weekday_of(self.nth_weekday))
                    }
                }
            }
            Unit::Year => if n == 1 { "Every year".into() } else { format!("Every {n} years") },
        };
        let mut s = base;
        match self.end_type {
            EndType::OnDate => if let Some(d) = self.end_date { s.push_str(&format!(", until {}", d.format("%-d %b %Y"))) },
            EndType::AfterCount => if self.end_count > 0 { s.push_str(&format!(", {} {}", self.end_count, if self.end_count == 1 { "time" } else { "times" })) },
            EndType::Never => {}
        }
        if self.basis == Basis::FromCompletion { s.push_str(", after completion"); }
        s
    }

    /// The first occurrence strictly after `base`.
    pub fn step(&self, base: NaiveDate) -> NaiveDate {
        let n = self.interval.max(1);
        match self.unit {
            Unit::Day => base + Duration::days(n),
            Unit::Week => {
                if self.week_days.is_empty() {
                    return base + Duration::weeks(n);
                }
                let base_monday = monday_of(base);
                let mut d = base + Duration::days(1);
                for _ in 0..(7 * n + 14) {
                    let weeks = (monday_of(d) - base_monday).num_days() / 7;
                    if self.week_days.contains(&d.weekday().number_from_monday()) && weeks % n == 0 {
                        return d;
                    }
                    d += Duration::days(1);
                }
                base + Duration::weeks(n)
            }
            Unit::Month => {
                let (y, m) = add_months(base.year(), base.month(), n);
                match self.month_mode {
                    MonthMode::DayOfMonth => {
                        let dom = if self.basis == Basis::FromDue && self.day_of_month > 0 { self.day_of_month } else { base.day() };
                        clamp_day(y, m, dom)
                    }
                    MonthMode::LastDay => clamp_day(y, m, 31),
                    MonthMode::NthWeekday => nth_weekday_of(y, m, weekday_of(self.nth_weekday), self.nth),
                }
            }
            Unit::Year => {
                let y = base.year() + n as i32;
                clamp_day(y, base.month(), base.day())
            }
        }
    }

    /// Next due date after completing an occurrence, or `None` when the series has ended.
    /// `count` = occurrences made so far including the one being completed.
    pub fn next(&self, due: NaiveDate, completed_on: NaiveDate, today: NaiveDate, count: i64) -> Option<NaiveDate> {
        if self.end_type == EndType::AfterCount && self.end_count > 0 && count >= self.end_count {
            return None;
        }
        let base = if self.basis == Basis::FromCompletion { completed_on } else { due };
        let mut n = self.step(base);
        if self.basis == Basis::FromDue {
            let mut guard = 0;
            while n < today && guard < 4000 {
                n = self.step(n);
                guard += 1;
            }
        }
        if self.end_type == EndType::OnDate {
            if let Some(e) = self.end_date {
                if n > e { return None; }
            }
        }
        Some(n)
    }
}

fn monday_of(d: NaiveDate) -> NaiveDate {
    d - Duration::days(d.weekday().num_days_from_monday() as i64)
}

fn add_months(y: i32, m: u32, n: i64) -> (i32, u32) {
    let total = y as i64 * 12 + (m as i64 - 1) + n;
    ((total.div_euclid(12)) as i32, (total.rem_euclid(12) + 1) as u32)
}

fn days_in_month(y: i32, m: u32) -> u32 {
    let (ny, nm) = add_months(y, m, 1);
    (NaiveDate::from_ymd_opt(ny, nm, 1).unwrap() - NaiveDate::from_ymd_opt(y, m, 1).unwrap()).num_days() as u32
}

fn clamp_day(y: i32, m: u32, d: u32) -> NaiveDate {
    NaiveDate::from_ymd_opt(y, m, d.min(days_in_month(y, m)).max(1)).unwrap()
}

fn nth_weekday_of(y: i32, m: u32, wd: Weekday, nth: u32) -> NaiveDate {
    let last = clamp_day(y, m, 31);
    let last_on = |from: NaiveDate| from - Duration::days(((from.weekday().num_days_from_monday() + 7 - wd.num_days_from_monday()) % 7) as i64);
    if nth >= 5 {
        return last_on(last);
    }
    let first_of = clamp_day(y, m, 1);
    let shift = (wd.num_days_from_monday() + 7 - first_of.weekday().num_days_from_monday()) % 7;
    let first = first_of + Duration::days(shift as i64) + Duration::weeks(nth as i64 - 1);
    if first.month() == m { first } else { last_on(last) }
}

pub fn ordinal(n: u32) -> String {
    let suffix = if (11..=13).contains(&(n % 100)) { "th" } else { match n % 10 { 1 => "st", 2 => "nd", 3 => "rd", _ => "th" } };
    format!("{n}{suffix}")
}

#[cfg(test)]
mod tests {
    use super::*;

    fn d(y: i32, m: u32, day: u32) -> NaiveDate { NaiveDate::from_ymd_opt(y, m, day).unwrap() }
    fn rec(j: &str) -> Recurrence { Recurrence::parse(j).unwrap() }

    #[test]
    fn epoch_days_match_the_phone() {
        assert_eq!(epoch_day(d(1970, 1, 1)), 0);
        assert_eq!(epoch_day(d(2026, 11, 9)), 20_766);
        assert_eq!(date_from_epoch_day(20_766), Some(d(2026, 11, 9)));
    }

    #[test]
    fn daily_and_weekly() {
        assert_eq!(rec(r#"{"u":"DAY","i":3}"#).step(d(2026, 1, 30)), d(2026, 2, 2));
        // every Mon/Wed/Fri: after Friday comes Monday
        let r = rec(r#"{"u":"WEEK","i":1,"wd":[1,3,5]}"#);
        assert_eq!(r.step(d(2026, 11, 6)), d(2026, 11, 9));
        // every 2 weeks on Saturday, from Saturday 14 Nov
        let r = rec(r#"{"u":"WEEK","i":2,"wd":[6]}"#);
        assert_eq!(r.step(d(2026, 11, 14)), d(2026, 11, 28));
    }

    #[test]
    fn months_clamp_and_nth() {
        let r = rec(r#"{"u":"MONTH","i":1,"mm":"DAY_OF_MONTH","dom":31}"#);
        assert_eq!(r.step(d(2026, 1, 31)), d(2026, 2, 28));
        let r = rec(r#"{"u":"MONTH","i":1,"mm":"LAST_DAY"}"#);
        assert_eq!(r.step(d(2026, 1, 15)), d(2026, 2, 28));
        // second Tuesday of December 2026 is the 8th
        let r = rec(r#"{"u":"MONTH","i":1,"mm":"NTH_WEEKDAY","nth":2,"nwd":2}"#);
        assert_eq!(r.step(d(2026, 11, 10)), d(2026, 12, 8));
        // last Friday of December 2026 is the 25th
        let r = rec(r#"{"u":"MONTH","i":1,"mm":"NTH_WEEKDAY","nth":5,"nwd":5}"#);
        assert_eq!(r.step(d(2026, 11, 10)), d(2026, 12, 25));
    }

    #[test]
    fn year_leap_day() {
        assert_eq!(rec(r#"{"u":"YEAR","i":1}"#).step(d(2024, 2, 29)), d(2025, 2, 28));
    }

    #[test]
    fn next_skips_the_past_and_respects_ends() {
        let r = rec(r#"{"u":"DAY","i":1}"#);
        // overdue by a week: the next one is not in the past
        assert_eq!(r.next(d(2026, 11, 1), d(2026, 11, 8), d(2026, 11, 8), 1), Some(d(2026, 11, 8)));
        // after completion counts from the day it was done
        let r = rec(r#"{"u":"DAY","i":2,"b":"FROM_COMPLETION"}"#);
        assert_eq!(r.next(d(2026, 11, 1), d(2026, 11, 8), d(2026, 11, 8), 1), Some(d(2026, 11, 10)));
        let r = rec(r#"{"u":"DAY","i":1,"et":"AFTER_COUNT","ec":3}"#);
        assert_eq!(r.next(d(2026, 11, 8), d(2026, 11, 8), d(2026, 11, 8), 3), None);
        let r = rec(r#"{"u":"DAY","i":1,"et":"ON_DATE","ed":20766}"#);
        assert_eq!(r.next(d(2026, 11, 9), d(2026, 11, 9), d(2026, 11, 9), 1), None);
    }

    #[test]
    fn describes_like_the_phone() {
        assert_eq!(rec(r#"{"u":"WEEK","i":1,"wd":[6]}"#).describe(), "Every Sat");
        assert_eq!(rec(r#"{"u":"WEEK","i":1,"wd":[1,2,3,4,5]}"#).describe(), "Every weekday");
        assert_eq!(rec(r#"{"u":"MONTH","i":1,"mm":"DAY_OF_MONTH","dom":1}"#).describe(), "Every month on the 1st");
        assert_eq!(rec(r#"{"u":"DAY","i":1,"b":"FROM_COMPLETION"}"#).describe(), "Every day, after completion");
    }
}
