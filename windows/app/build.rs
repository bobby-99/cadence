fn main() {
    slint_build::compile("ui/main.slint").expect("slint build");
    #[cfg(windows)]
    {
        if std::path::Path::new("../../docs/icon.ico").exists() {
            let mut res = winresource::WindowsResource::new();
            res.set_icon("../../docs/icon.ico");
            res.set("ProductName", "Cadence");
            res.set("FileDescription", "Cadence for Windows");
            let _ = res.compile();
        }
    }
}
