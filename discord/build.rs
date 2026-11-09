use std::path::Path;

fn main() {
    // Ensure WebView2Loader.dll is copied alongside the target executable
    let out_dir = std::env::var("OUT_DIR").unwrap();
    let target_dir = Path::new(&out_dir)
        .ancestors()
        .nth(3)
        .unwrap();

    let manifest_dir = std::env::var("CARGO_MANIFEST_DIR").unwrap();
    let dll_source = Path::new(&manifest_dir).join("WebView2Loader.dll");

    if dll_source.exists() {
        let dest = target_dir.join("WebView2Loader.dll");
        let _ = std::fs::copy(&dll_source, &dest);
    }
}
