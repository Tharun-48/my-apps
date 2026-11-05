/**
 * ProStats Web Dashboard — Powered by Rust WebAssembly (`pro_stats_web.wasm`)
 * High-performance, zero-GC native calculation and telemetry engine.
 */

let wasmExports = null;

// Initialize the Rust WebAssembly Core
async function initRustWasm() {
    try {
        const response = await fetch('pro_stats_web.wasm');
        const bytes = await response.arrayBuffer();
        const { instance } = await WebAssembly.instantiate(bytes, {});
        wasmExports = instance.exports;
        console.log('[ProStats] Rust WebAssembly Engine initialized successfully (v2.5).');
    } catch (e) {
        console.warn('[ProStats] WebAssembly fallback active:', e);
    }
}

document.addEventListener('DOMContentLoaded', async () => {
    await initRustWasm();

    const btnBattery = document.getElementById('btn-perm-battery');
    const btnProcess = document.getElementById('btn-perm-process');
    const btnShizuku = document.getElementById('btn-perm-shizuku');
    const grantBtn = document.getElementById('grant-btn');
    const btnText = grantBtn.querySelector('.btn-text');

    let batteryGranted = false;
    let processGranted = false;
    let shizukuGranted = false;
    let tickCount = 0;

    const mockProcesses = [
        { name: "ProStats Core (Rust)", pkg: "com.example.prostats", cpu: 0.8, ram: 18.5, isSystem: false, state: "Foreground (Native Core)" },
        { name: "Chrome", pkg: "com.android.chrome", cpu: 12.8, ram: 342.0, isSystem: false, state: "Foreground" },
        { name: "Spotify", pkg: "com.spotify.music", cpu: 2.4, ram: 145.8, isSystem: false, state: "Foreground Service" },
        { name: "System UI", pkg: "com.android.systemui", cpu: 2.1, ram: 112.5, isSystem: true, state: "Foreground Service" },
        { name: "WhatsApp", pkg: "com.whatsapp", cpu: 0.5, ram: 98.4, isSystem: false, state: "Active Background" },
        { name: "YouTube", pkg: "com.google.android.youtube", cpu: 18.4, ram: 256.1, isSystem: false, state: "Recent (< 2m ago)" },
        { name: "system_server", pkg: "android", cpu: 4.2, ram: 184.2, isSystem: true, state: "Active Background" }
    ];

    const callNative = (method) => {
        if (typeof AndroidBridge !== 'undefined' && AndroidBridge[method]) {
            AndroidBridge[method]();
        } else {
            console.log(`[ProStats Native Bridge] ${method}`);
        }
    };

    btnBattery.addEventListener('click', () => {
        const item = document.getElementById('item-battery');
        batteryGranted = !batteryGranted;
        if (batteryGranted) {
            callNative('openBatterySettings');
            btnBattery.textContent = "GRANTED";
            btnBattery.classList.add('granted');
            item.classList.add('active');
            item.style.borderColor = 'var(--text-primary)';
        } else {
            btnBattery.textContent = "GRANT";
            btnBattery.classList.remove('granted');
            item.classList.remove('active');
            item.style.borderColor = 'var(--border)';
        }
        updateButtonState();
    });

    btnProcess.addEventListener('click', () => {
        const item = document.getElementById('item-process');
        processGranted = !processGranted;
        if (processGranted) {
            callNative('openUsageAccessSettings');
            btnProcess.textContent = "GRANTED";
            btnProcess.classList.add('granted');
            item.classList.add('active');
            item.style.borderColor = 'var(--text-primary)';
        } else {
            btnProcess.textContent = "GRANT";
            btnProcess.classList.remove('granted');
            item.classList.remove('active');
            item.style.borderColor = 'var(--border)';
        }
        updateButtonState();
    });

    btnShizuku.addEventListener('click', () => {
        const item = document.getElementById('item-shizuku');
        shizukuGranted = !shizukuGranted;
        if (shizukuGranted) {
            callNative('openShizukuApp');
            btnShizuku.textContent = "GRANTED";
            btnShizuku.classList.add('granted');
            item.classList.add('active');
            item.style.borderColor = 'var(--text-primary)';
        } else {
            btnShizuku.textContent = "OPTIONAL";
            btnShizuku.classList.remove('granted');
            item.classList.remove('active');
            item.style.borderColor = 'var(--border)';
        }
        updateButtonState();
    });

    function updateButtonState() {
        const requiredChecked = batteryGranted && processGranted;
        if (requiredChecked) {
            btnText.textContent = "START MONITORING";
            grantBtn.classList.add('granted');
        } else {
            btnText.textContent = "GRANT REQUIRED & START MONITORING";
            grantBtn.classList.remove('granted');
        }
    }

    grantBtn.addEventListener('click', (e) => {
        const requiredChecked = batteryGranted && processGranted;
        if (!requiredChecked) {
            if (!batteryGranted) btnBattery.click();
            setTimeout(() => { if (!processGranted) btnProcess.click(); }, 400);
        } else {
            btnText.textContent = "INITIALIZING RUST ENGINE...";
            grantBtn.style.pointerEvents = 'none';
            setTimeout(() => { showDashboard(shizukuGranted); }, 1000);
        }
    });

    function showDashboard(isShizuku) {
        document.querySelector('.header-title').textContent = "PROSTATS DASHBOARD (RUST CORE)";
        grantBtn.style.display = 'none';

        const mainContent = document.querySelector('.main-content');
        
        // Calculate health metrics using Rust WASM module
        const healthScore = wasmExports ? wasmExports.rust_battery_health(120, 4850, 5000) : 98;
        const dischargeRate = wasmExports ? wasmExports.rust_discharge_rate(12, 7200000) : 6.0;
        const timeToEmptyMs = wasmExports ? Number(wasmExports.rust_time_to_empty(82, dischargeRate)) : 49200000;
        const hoursRemaining = (timeToEmptyMs / 3600000).toFixed(1);

        mainContent.innerHTML = `
            <!-- Top Grid Row (SOT & Battery Temp) -->
            <div style="display: flex; gap: 16px; margin-bottom: 16px;">
                <div class="permission-item" style="flex: 1; padding: 20px; text-align: left; display: block;">
                    <div style="font-size: 11px; font-weight: bold; color: var(--text-secondary); letter-spacing: 1px;">SCREEN-ON TIME</div>
                    <div style="font-size: 24px; font-weight: bold; color: #a78bfa; margin-top: 12px;">5h 12m</div>
                    <div style="font-size: 11px; color: var(--text-secondary); margin-top: 4px;">Active Foreground Today</div>
                </div>
                <div class="permission-item" style="flex: 1; padding: 20px; text-align: left; display: block;">
                    <div style="font-size: 11px; font-weight: bold; color: var(--text-secondary); letter-spacing: 1px;">BATTERY TEMP</div>
                    <div id="bat-temp-val" style="font-size: 24px; font-weight: bold; color: var(--accent); margin-top: 12px;">36.2°C</div>
                    <div style="font-size: 11px; color: var(--text-secondary); margin-top: 4px;">Rust Thermal Sensor</div>
                </div>
            </div>

            <!-- Battery Diagnostics Card -->
            <div class="permission-item" style="padding: 20px; margin-bottom: 16px; display: block; text-align: left;">
                <div style="font-size: 11px; font-weight: bold; color: var(--text-secondary); letter-spacing: 1px; margin-bottom: 16px;">BATTERY DIAGNOSTICS (RUST ACCELERATED)</div>
                <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px;">
                    <div>
                        <div style="font-size: 14px; font-weight: bold; color: white;">Health Score: ${healthScore}% (Excellent)</div>
                        <div style="font-size: 12px; color: var(--text-secondary); margin-top: 2px;">Remaining: ~${hoursRemaining}h (${dischargeRate.toFixed(1)}%/hr)</div>
                    </div>
                    <div style="text-align: right;">
                        <div id="bat-current-val" style="font-size: 14px; font-weight: bold; color: var(--accent-orange);">-162 mA</div>
                        <div style="font-size: 12px; color: var(--text-secondary); margin-top: 2px;">Status: Discharging</div>
                    </div>
                </div>
                <div style="font-size: 12px; color: var(--text-secondary); margin-top: 8px; border-top: 1px solid rgba(255,255,255,0.05); padding-top: 8px;">
                    Rust Wasm Engine: <span style="color: #4ade80; font-weight: 500;">Active (Zero GC)</span>
                </div>
            </div>

            <!-- CPU Clusters Frequencies -->
            <div class="permission-item" style="padding: 20px; margin-bottom: 16px; display: block; text-align: left;">
                <div style="font-size: 11px; font-weight: bold; color: var(--text-secondary); letter-spacing: 1px; margin-bottom: 16px;">CPU CLUSTER FREQUENCIES (8 CORES)</div>
                <div style="display: grid; grid-template-columns: repeat(4, 1fr); gap: 8px;" id="cpu-clusters-container">
                    ${[0,1,2,3,4,5,6,7].map(i => {
                        const freq = wasmExports ? wasmExports.rust_cluster_freq(i, 0) : 1800;
                        return `
                        <div style="background: rgba(255,255,255,0.03); border: 1px solid var(--border); padding: 8px; border-radius: 8px; text-align: center;">
                            <div style="font-size: 10px; color: var(--text-secondary);">CORE ${i}</div>
                            <div id="core-freq-${i}" style="font-size: 14px; font-weight: bold; color: var(--text-primary); margin-top: 4px;">${freq} MHz</div>
                        </div>`;
                    }).join('')}
                </div>
            </div>

            <!-- Live Processes Card -->
            <div class="permission-item" style="padding: 20px; display: block; text-align: left;">
                <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px;">
                    <div style="font-size: 11px; font-weight: bold; color: var(--text-secondary); letter-spacing: 1px;">ACTIVE PROCESSES</div>
                    <div style="font-size: 10px; background: rgba(255,255,255,0.08); padding: 2px 8px; border-radius: 12px; color: #a78bfa;">
                        ${isShizuku ? 'SHIZUKU MODE' : 'NATIVE MODE'}
                    </div>
                </div>
                <div id="process-list-container">
                    ${mockProcesses.map(p => `
                        <div style="display: flex; justify-content: space-between; align-items: center; padding: 10px 0; border-bottom: 1px solid rgba(255,255,255,0.03);">
                            <div>
                                <div style="font-size: 13px; font-weight: 500; color: white;">${p.name}</div>
                                <div style="font-size: 11px; color: var(--text-secondary); margin-top: 2px;">${p.state}</div>
                            </div>
                            <div style="text-align: right;">
                                <div style="font-size: 12px; font-weight: bold; color: ${p.cpu > 5 ? 'var(--accent-orange)' : 'var(--text-primary)'};">${p.cpu}% CPU</div>
                                <div style="font-size: 11px; color: var(--text-secondary); margin-top: 2px;">${p.ram} MB</div>
                            </div>
                        </div>
                    `).join('')}
                </div>
            </div>
        `;

        // Start real-time Rust telemetry loop (every 1 second)
        setInterval(() => {
            tickCount++;
            if (wasmExports) {
                // Query core frequencies from Rust WebAssembly
                for (let i = 0; i < 8; i++) {
                    const freq = wasmExports.rust_cluster_freq(i, tickCount);
                    const el = document.getElementById(`core-freq-${i}`);
                    if (el) el.textContent = `${freq} MHz`;
                }

                // Query simulated current and temperature from Rust
                const cur = wasmExports.rust_simulated_current(tickCount);
                const temp = wasmExports.rust_simulated_temp(tickCount);
                const curEl = document.getElementById('bat-current-val');
                const tempEl = document.getElementById('bat-temp-val');
                if (curEl) curEl.textContent = `${cur} mA`;
                if (tempEl) tempEl.textContent = `${temp.toFixed(1)}°C`;
            }
        }, 1000);
    }
});
