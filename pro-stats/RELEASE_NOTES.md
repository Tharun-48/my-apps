# ProStats — Release Notes & Version Updates

### **Version 2.5 (Current Release)**
- **Interactive Timeline Scrubber**: Added an interactive draggable timeline scrubber with parallel vertical indicator line on the battery usage chart. Drag or tap anywhere on the graph or adjust the time slider to inspect active app drain during that specific timeframe.
- **Real-Time Battery & Hardware Thermal Monitoring**: Fixed battery temperature refresh issues by integrating direct hardware sensor queries with instant kernel sysfs power supply fallback.
- **Refined Battery Analytics**:
  - Reorganized into **Battery Usage Stats** with simplified range options (*Since Charge* and *24h*).
  - Cleaned up diagnostic logging panels for a streamlined, lightweight settings experience.
- **Material Design 3 Polish**:
  - Dynamic versioning displays (`v2.5`) across all screens.
  - Smoothed out gauge transitions and elevated hairline surface borders.

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
