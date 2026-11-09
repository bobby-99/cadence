// Renders the window to a PPM image without a display (for checking the layout). Not part of the app.
use slint::platform::software_renderer::{MinimalSoftwareWindow, PremultipliedRgbaColor, RepaintBufferType, TargetPixel};
use slint::platform::{Platform, WindowAdapter};
use slint::{ComponentHandle, ModelRc, PhysicalSize, VecModel};
use std::rc::Rc;

slint::include_modules!();

struct P(Rc<MinimalSoftwareWindow>);
impl Platform for P {
    fn create_window_adapter(&self) -> Result<Rc<dyn WindowAdapter>, slint::PlatformError> { Ok(self.0.clone()) }
}

#[derive(Clone, Copy, Default)]
struct Px([u8; 3]);
impl TargetPixel for Px {
    fn blend(&mut self, c: PremultipliedRgbaColor) {
        let ia = 255 - c.alpha as u16;
        for (d, s) in self.0.iter_mut().zip([c.red, c.green, c.blue]) { *d = (s as u16 + *d as u16 * ia / 255) as u8; }
    }
    fn from_rgb(r: u8, g: u8, b: u8) -> Self { Px([r, g, b]) }
}

fn item(t: &str, top: &str, bot: &str, sub: &str, pr: i32, done: bool, od: bool) -> TaskItem {
    TaskItem { uuid: t.into(), title: t.into(), time_top: top.into(), time_bottom: bot.into(), sub: sub.into(), priority: pr, done, overdue: od }
}

fn main() {
    let args: Vec<String> = std::env::args().collect();
    let sync_open = args.get(2).map_or(false, |a| a == "sync");
    let win = MinimalSoftwareWindow::new(RepaintBufferType::NewBuffer);
    slint::platform::set_platform(Box::new(P(win.clone()))).unwrap();
    let ui = MainWindow::new().unwrap();
    win.set_size(PhysicalSize::new(440, 720));
    ui.set_date_line("MON 9 NOV".into());
    ui.set_heading("2 to do today.".into());
    ui.set_sub_heading("1 of 3 done".into());
    ui.set_tasks(ModelRc::new(VecModel::from(vec![
        item("Wish Tharani birthday", "All", "day", "Today  \u{00B7}  Medium", 2, false, false),
        item("Call mom", "6:00", "pm", "Today  \u{00B7}  High", 3, false, true),
        item("Run", "6:00", "am", "Sat 14 Nov  \u{00B7}  \u{21BB} Every Sat", 0, false, false),
        item("Backup", "8:00", "am", "Tue 1 Dec  \u{00B7}  \u{21BB} Every month on the 1st", 1, false, false),
        item("Study", "6:00", "pm", "Yesterday", 0, true, false),
    ])));
    ui.set_sync_on(true);
    ui.set_folder_text("C:\\Users\\bobby\\Sync\\Cadence".into());
    ui.set_sync_text("Last sync 9 Nov, 5:30 PM".into());
    ui.set_show_sync(sync_open);
    ui.show().unwrap();
    let (w, h) = (440usize, 720usize);
    let mut buf = vec![Px::default(); w * h];
    win.draw_if_needed(|r| { r.render(&mut buf, w); });
    let mut out = format!("P6\n{w} {h}\n255\n").into_bytes();
    for p in &buf { out.extend_from_slice(&p.0); }
    std::fs::write(&args[1], out).unwrap();
}
