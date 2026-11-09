# My Apps

A collection of high-performance, lightweight applications and tools designed for maximum efficiency.

---

## 📱 ProStats (Android)

**ProStats** is an advanced system diagnostics and battery monitoring application for Android devices.

### Key Highlights:
* **Deep System Diagnostics**: High-accuracy process tracking using Shizuku and native dumpsys.
* **Screen-on-Time (SOT) Scrubber**: Interactive battery timeline to monitor power usage throughout the day.
* **Native Rust Acceleration**: Heavy calculation algorithms powered by a compiled native Rust core.
* **Modern Material UI**: Clean Jetpack Compose interface with full OLED dark mode support.

📂 **Folder**: [`pro-stats/`](./pro-stats)

---

## 💬 Discord (Rust Client)

**Discord (Rust)** is a lightweight desktop client port running the real Discord platform without Electron bloat.

### Key Highlights:
* **Massive Memory Savings**: Uses ~180 MB – 250 MB of RAM instead of Electron's 1.2+ GB.
* **Direct Hardware Acceleration**: Native DirectComposition GPU rendering with zero Node.js overhead.
* **Low Background Load**: Injected optimizations filter non-essential telemetry to keep the system fast and cool.
* **Full Discord Features**: Real account login, voice/video calls, screen sharing, servers, and DMs.

📂 **Folder**: [`discord/`](./discord)  
📖 **Documentation**: [`discord/README.md`](./discord/README.md)

---

## 📁 Repository Overview

| Item | Description |
| :--- | :--- |
| **`pro-stats/`** | Android system monitor & battery analytics project |
| **`discord/`** | Lightweight native Rust desktop client for Discord |
