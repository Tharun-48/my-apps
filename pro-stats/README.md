# ProStats

**ProStats** is an advanced, lightweight system diagnostics and battery monitoring application for Android devices. It combines a modern Material You interface with a high-performance native Rust core to deliver real-time hardware telemetry and accurate battery analytics with minimal power consumption.

---

## 🌟 Key Features

### 🔋 Battery Analytics & Screen-On Time (SOT)
* **Interactive SOT Scrubber**: An interactive timeline graph that lets you drag and inspect your exact battery drain and screen-on time across any hour of the day.
* **Deep Sleep & Standby Tracking**: Monitors how well your device sleeps when the screen is turned off and measures idle battery loss per hour.
* **Battery Health & Charge Speeds**: Displays real-time charging wattage, battery temperature alarms, estimated wear, and cycle counts.
* **Smart Baselines**: Automatically resets stats when you unplug your phone from a charge so your daily numbers stay accurate.

### ⚡ High-Accuracy Process Diagnostics
* **Shizuku Integration**: Inspect background tasks and system services with elevated permissions without needing to root your device.
* **System Dumpsys**: Retrieves accurate per-process CPU, memory, and wakelock statistics straight from Android's system framework.

### 🦀 Native Rust Core Engine
* **Zero Garbage Collection Lag**: Heavy telemetry processing and mathematical scoring are written in compiled native Rust (`libcore_rs.so`).
* **Energy-Efficient**: Built to perform complex monitoring in the background while keeping CPU wakeups and power draw close to zero.

### 🌐 Network & Hardware Telemetry
* **Real-Time Bandwidth Monitor**: Live 1-second network speed polling showing current upload and download rates.
* **Hardware Overview**: Real-time CPU frequencies, RAM allocation, GPU utilization, and device thermals.

### 🎨 Material You & OLED Deep Black
* **Dynamic Color Theming**: Automatically adapts its color palette to match your Android wallpaper (Monet theming).
* **Pure OLED Black Mode**: Features a true `#000000` pitch-black theme to maximize battery savings on AMOLED and OLED screens.

---

## 🏗️ Technical Architecture

| Component | Technology | Purpose |
| :--- | :--- | :--- |
| **User Interface** | Jetpack Compose + Material 3 | Fluid, modern, responsive UI with wallpaper color adaptation |
| **Native Core** | Rust (`core-rs`) via JNI | Memory-safe, high-speed telemetry calculations and analytics |
| **System Service Access** | Shizuku API | Elevated non-root access to detailed Android performance metrics |
| **Architecture Pattern** | MVVM + Kotlin Coroutines | Clean separation of UI state, background data streams, and hardware events |

---

## 📲 Installation & Setup

1. **Download the APK**: Get the latest pre-compiled release from the `releases/` directory.
2. **Install on Android**: Open the APK file on your device and tap **Install**.
3. **Optional (Shizuku Permissions)**:
   * For deep per-process diagnostics, open **Shizuku** and grant permission to ProStats.
   * Standard battery tracking and hardware stats work out-of-the-box without extra tools.
4. **OEM Autostart**: If you use Xiaomi, Samsung, OnePlus, Oppo, or Vivo devices, follow the built-in step-by-step setup cards to ensure background battery tracking is not killed by the OS.
