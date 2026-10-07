# ProStats — Release Notes & Version Updates

### **Version 2.5 (Current Release)**
- **Rust Native Engine (`core-rs`)**: High-performance, memory-safe native core powering battery health scoring and telemetry calculations with zero garbage-collector overhead.
- **Refined Symmetrical Classic Logo**: Fixed arrow skew and center crossover with a mathematically balanced 45° surge vector icon and adaptive launcher shortcuts.
- **Default Material You & OLED Deep Black**:
  - Material You dynamic system colors are now active by default across System Default, Dark, and Light modes.
  - Added dedicated **Deep / Pure Black (OLED)** toggle for true `#000000` power savings while preserving vibrant Monet dynamic accents.
- **Real-Time Network Bandwidth & Interfaces**: Live 1-second periodic bandwidth polling measuring download and upload speeds (KB/s and MB/s) and detected physical/virtual interface states.
- **Step-by-Step Onboarding with Autostart**: Dynamic one-by-one setup card flow with 1-2 line explanations and non-blocking OEM Autostart configuration for Xiaomi, Samsung, Oppo, Vivo, and OnePlus.
- **Charging SOT Baseline Reset**: Auto-reset baseline threshold options and manual reset actions ensuring uninterrupted graph tracking upon unplugging from charge.

---

### **Version 2.4**
- **Material You Dynamic Theming**: Full Android 12+ Monet dynamic color palette integration with AMOLED and dark modes.
- **Enhanced Screen-On Time (SOT)**: Real-time SOT tracking with auto-reset baselines upon full charge.
- **Deep Sleep & Standby Drain**: Added deep sleep ratio calculations and per-hour screen-off discharge rate metrics.
- **In-App Update Checker**: Automated checks against GitHub releases to notify users when a new APK is published.

---

### **Version 2.3**
- **Hardware Telemetry**: Real-time CPU core frequencies, GPU utilization monitoring, RAM usage, and thermal headroom.
- **Battery Health Estimator**: Cycle counts, estimated wear, charging power (watts), and high-temperature alarms.
- **Customizable Overlays**: Floating real-time HUD showing FPS, CPU load, and battery current.
