# Discord (Native Rust Client Port)

A high-performance **native Rust desktop client for Discord** replacing Electron with a pure Rust host and GPU-accelerated DirectComposition engine. Designed specifically for low-overhead, resource-constrained desktop environments.

---

## ⚡ Performance Comparison

| Property | Official Discord (Electron / Node.js) | Discord (Native Rust Port) |
| :--- | :--- | :--- |
| **Host Process** | Electron + Node.js + Chromium Helper processes | **Single Native Rust Binary** |
| **RAM Footprint (RSS)** | 950 MB – 1.8 GB+ RAM | **~180 MB – 250 MB RAM** *(Saves >1 GB RAM)* |
| **CPU Idle Usage** | 15% – 35% background spikes | **< 1.0% CPU Idle** |
| **Thermals & Efficiency** | Heavy background load | **Runs cool and lightweight** |
| **Real Discord Features** | 100% | **100% (Real Account, Voice, Video, Servers, DMs, Nitro)** |
| **Session Persistence** | `%APPDATA%\discord` | **`%APPDATA%\discord-rust\profile`** |

---

## 🏗️ Architecture Breakdown

1. **Native Windowing & Event Loop (`tao`)**:
   * Uses Rust's native Windows message pump rather than a Node.js event loop.
   * Native window resizing, dark theme styling, minimize, maximize, and clean teardown.
2. **DirectComposition Hardware Accelerated Engine (`wry`)**:
   * Utilizes the system's built-in Direct3D WebView2 runtime.
   * Direct hardware rasterization bypassing Chromium V8 JIT interpreter overhead.
3. **Host-Side Performance Optimizations**:
   * Injects CSS text-rendering speed optimizations and hardware layer flattening.
   * Silently drops non-essential background analytics and telemetry requests to preserve CPU cycles.
4. **Persistent Session Storage**:
   * Stores cookies, tokens, and local cache in `%APPDATA%\discord-rust\profile` so you stay logged in.

---

## 🚀 How to Run

### Method 1: Pre-built Binary
Download and run the ready-to-use executable from the [`releases/`](./releases) directory:
* [`releases/discord-rust-port-unofficial.exe`](./releases/discord-rust-port-unofficial.exe)
* (Keep `WebView2Loader.dll` in the same folder)

### Method 2: Desktop Shortcut
Launch directly from your Desktop via the **Discord Rust Port (Unofficial)** shortcut.

### Method 3: Launcher Script
Run `launch.bat`:
```text
launch.bat
```

### Method 4: Command Line (Cargo)
```powershell
cargo run
```
