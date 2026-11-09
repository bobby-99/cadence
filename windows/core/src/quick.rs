//! A small version of the phone's "type it the way you say it" parser: a date word, a time and a
//! priority. The full parser (repeats, durations, many more phrases) is a later port of `TaskParser.kt`.

use chrono::{Datelike, Duration, NaiveDate};

#[derive(Debug, Default, PartialEq)]
pub struct Quick {
    pub title: String,
    pub date: Option<NaiveDate>,
    /// minutes after midnight
    pub time: Option<i64>,
    pub priority: i64,
}

const WEEKDAYS: [(&str, u32); 14] = [
    ("monday", 1), ("mon", 1), ("tuesday", 2), ("tue", 2), ("wednesday", 3), ("wed", 3), ("thursday", 4),
    ("thu", 4), ("friday", 5), ("fri", 5), ("saturday", 6), ("sat", 6), ("sunday", 7), ("sun", 7),
];

fn clock(word: &str) -> Option<i64> {
    let w = word.to_lowercase();
    let (body, pm, am) = if let Some(b) = w.strip_suffix("pm") { (b, true, false) } else if let Some(b) = w.strip_suffix("am") { (b, false, true) } else { (w.as_str(), false, false) };
    if body.is_empty() || !body.chars().all(|c| c.is_ascii_digit() || c == ':') {
        return None;
    }
    // a bare number is not a time; it needs am/pm or a colon
    if !pm && !am && !body.contains(':') {
        return None;
    }
    let (h, m) = match body.split_once(':') {
        Some((h, m)) => (h.parse::<i64>().ok()?, m.parse::<i64>().ok()?),
        None => (body.parse::<i64>().ok()?, 0),
    };
    if m > 59 || h > 23 || ((pm || am) && !(1..=12).contains(&h)) {
        return None;
    }
    let h = if pm { h % 12 + 12 } else if am { h % 12 } else { h };
    Some(h * 60 + m)
}

pub fn parse(input: &str, today: NaiveDate) -> Quick {
    let mut q = Quick::default();
    let mut kept: Vec<&str> = Vec::new();
    let words: Vec<&str> = input.split_whitespace().collect();
    let mut i = 0;
    while i < words.len() {
        let w = words[i];
        let lw = w.to_lowercase();
        if !w.is_empty() && w.chars().all(|c| c == '!') && w.len() <= 3 {
            q.priority = w.len() as i64;
        } else if matches!(lw.as_str(), "today" | "tonight") {
            q.date = Some(today);
            if lw == "tonight" && q.time.is_none() { q.time = Some(20 * 60); }
        } else if matches!(lw.as_str(), "tomorrow" | "tmrw" | "tmr" | "tom") {
            q.date = Some(today + Duration::days(1));
        } else if let Some((_, n)) = WEEKDAYS.iter().find(|(name, _)| *name == lw) {
            let ahead = (*n as i64 - today.weekday().number_from_monday() as i64).rem_euclid(7);
            q.date = Some(today + Duration::days(if ahead == 0 { 7 } else { ahead }));
        } else if lw == "next" && words.get(i + 1).map_or(false, |n| n.eq_ignore_ascii_case("week")) {
            q.date = Some(today + Duration::days(7 - today.weekday().num_days_from_monday() as i64));
            i += 1;
        } else if matches!(lw.as_str(), "morning" | "evening" | "night" | "noon") {
            q.time = Some(match lw.as_str() { "morning" => 8 * 60, "evening" => 18 * 60, "night" => 22 * 60, _ => 13 * 60 });
        } else if let Some(t) = clock(w) {
            q.time = Some(t);
        } else if lw == "at" && words.get(i + 1).and_then(|n| clock(n)).is_some() {
            // "at 6pm": drop the "at"
        } else {
            kept.push(w);
        }
        i += 1;
    }
    if q.time.is_some() && q.date.is_none() {
        q.date = Some(today);
    }
    q.title = kept.join(" ");
    if q.title.is_empty() {
        q.title = input.trim().to_string();
        q.date = None;
        q.time = None;
        q.priority = 0;
    }
    q
}

#[cfg(test)]
mod tests {
    use super::*;
    fn day() -> NaiveDate { NaiveDate::from_ymd_opt(2026, 11, 9).unwrap() } // a Monday

    #[test]
    fn typical_phrases() {
        let q = parse("Call mom tomorrow 6pm !!", day());
        assert_eq!((q.title.as_str(), q.date, q.time, q.priority), ("Call mom", Some(day() + Duration::days(1)), Some(18 * 60), 2));
        let q = parse("Run errands tmrw morning !!!", day());
        assert_eq!((q.title.as_str(), q.time, q.priority), ("Run errands", Some(8 * 60), 3));
        let q = parse("Gym fri at 7:30am", day());
        assert_eq!((q.title.as_str(), q.date, q.time), ("Gym", Some(day() + Duration::days(4)), Some(450)));
        let q = parse("Pay bills 12am", day());
        assert_eq!(q.time, Some(0));
    }

    #[test]
    fn numbers_and_plain_text_stay_in_the_title() {
        assert_eq!(parse("Read 30 pages", day()).title, "Read 30 pages");
        assert_eq!(parse("Buy milk", day()), Quick { title: "Buy milk".into(), ..Default::default() });
        // only a date word: keep the text rather than leave an empty title
        assert_eq!(parse("tomorrow", day()).title, "tomorrow");
    }
}
