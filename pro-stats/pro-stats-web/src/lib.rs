// ==============================================================================
// ProStats WebAssembly Engine — Compiled directly from Rust to Wasm
// Eliminates raw JavaScript calculations and delivers native-speed telemetry.
// ==============================================================================

#[unsafe(no_mangle)]
pub extern "C" fn rust_battery_health(cycles: i32, current_mah: i32, design_mah: i32) -> i32 {
    let capacity_ratio = if design_mah > 0 && current_mah > 0 {
        (current_mah as f64 / design_mah as f64).clamp(0.0, 1.05)
    } else {
        1.0
    };

    let cycle_wear = if cycles > 0 {
        let c = cycles as f64;
        let degradation = 0.00022 * c + 0.00000004 * c * c;
        (1.0 - degradation).clamp(0.40, 1.0)
    } else {
        1.0
    };

    ((capacity_ratio * 0.80 + cycle_wear * 0.20) * 100.0).round().clamp(0.0, 100.0) as i32
}

#[unsafe(no_mangle)]
pub extern "C" fn rust_discharge_rate(drain_pct: i32, elapsed_ms: i64) -> f32 {
    if elapsed_ms <= 10_000 || drain_pct <= 0 {
        return 0.0;
    }
    let hours = (elapsed_ms as f64) / 3_600_000.0;
    ((drain_pct as f64) / hours).clamp(0.1, 100.0) as f32
}

#[unsafe(no_mangle)]
pub extern "C" fn rust_time_to_empty(current_level: i32, discharge_rate: f32) -> i64 {
    if current_level <= 0 || discharge_rate < 0.1 {
        return 0;
    }
    let hours = ((current_level as f64) / (discharge_rate as f64)).clamp(0.0, 120.0);
    (hours * 3_600_000.0) as i64
}

#[unsafe(no_mangle)]
pub extern "C" fn rust_time_to_full(soc_pct: i32, design_mah: i32, current_ma: i32) -> i64 {
    if current_ma <= 50 || design_mah <= 0 || soc_pct >= 100 {
        return 0;
    }

    let soc = (soc_pct as f64).clamp(0.0, 99.0);
    let cap = design_mah as f64;
    let i_charge = current_ma as f64;

    let hours = if soc < 80.0 {
        let cc_hours = ((80.0 - soc) / 100.0 * cap) / i_charge;
        let cv_hours = (0.20 * cap / i_charge) * 1.45;
        cc_hours + cv_hours
    } else {
        let remaining_mah = (100.0 - soc) / 100.0 * cap;
        let taper = 1.3 + (soc - 80.0) / 20.0 * 0.6;
        (remaining_mah / i_charge) * taper
    };

    (hours.clamp(0.0, 24.0) * 3_600_000.0) as i64
}

#[unsafe(no_mangle)]
pub extern "C" fn rust_transfer_rate(current_bytes: i64, prev_bytes: i64, elapsed_ms: i64) -> i64 {
    if elapsed_ms <= 0 || current_bytes < prev_bytes {
        return 0;
    }
    let delta = current_bytes - prev_bytes;
    let seconds = (elapsed_ms as f64) / 1000.0;
    if seconds <= 0.0 { 0 } else { ((delta as f64) / seconds) as i64 }
}

#[unsafe(no_mangle)]
pub extern "C" fn rust_cluster_freq(core_index: i32, tick: i32) -> i32 {
    let base_freq = match core_index {
        0..=3 => 1800,  // Efficiency cores
        4..=5 => 2420,  // Performance cores
        _ => 2840,      // Prime cores
    };
    let variation = ((core_index * 37 + tick * 43) % 200) - 100;
    (base_freq + variation).max(800)
}

#[unsafe(no_mangle)]
pub extern "C" fn rust_simulated_cpu(tick: i32) -> f32 {
    let wave = ((tick as f64 * 0.25).sin() * 8.0) + 14.0;
    wave.clamp(3.0, 95.0) as f32
}

#[unsafe(no_mangle)]
pub extern "C" fn rust_simulated_current(tick: i32) -> i32 {
    let base = -165;
    let jitter = (tick * 17) % 25 - 12;
    base + jitter
}

#[unsafe(no_mangle)]
pub extern "C" fn rust_simulated_temp(tick: i32) -> f32 {
    let base = 36.2;
    let jitter = (((tick % 10) as f64) * 0.05) - 0.25;
    (base + jitter) as f32
}
