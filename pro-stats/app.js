document.addEventListener('DOMContentLoaded', () => {
    const btnBattery = document.getElementById('btn-perm-battery');
    const btnProcess = document.getElementById('btn-perm-process'); // Usage Access
    const btnShizuku = document.getElementById('btn-perm-shizuku');
    const grantBtn = document.getElementById('grant-btn');
    const btnText = grantBtn.querySelector('.btn-text');

    let batteryGranted = false;
    let processGranted = false;
    let shizukuGranted = false;

    const mockProcesses = [
        { name: "system_server", pkg: "android", cpu: 4.2, ram: 184.2, isSystem: true },
        { name: "System UI", pkg: "com.android.systemui", cpu: 2.1, ram: 112.5, isSystem: true },
        { name: "Chrome", pkg: "com.android.chrome", cpu: 12.8, ram: 342.0, isSystem: false },
        { name: "WhatsApp", pkg: "com.whatsapp", cpu: 0.5, ram: 98.4, isSystem: false },
        { name: "YouTube", pkg: "com.google.android.youtube", cpu: 18.4, ram: 256.1, isSystem: false },
        { name: "ProStats (Active)", pkg: "com.example.prostats", cpu: 1.5, ram: 45.2, isSystem: false }
    ];

    const mockCores = [1820, 2040, 2040, 2040, 2420, 2420, 2840, 2840];

    // Helper to call native bridge safely
    const callNative = (method) => {
        if (typeof AndroidBridge !== 'undefined' && AndroidBridge[method]) {
            AndroidBridge[method]();
        } else {
            console.log(`Called native method: ${method}`);
        }
    };

    // Handle button clicks to simulate settings navigation
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
            if (!batteryGranted) {
                btnBattery.click();
            }
            setTimeout(() => {
                if (!processGranted) {
                    btnProcess.click();
                }
            }, 500);
        } else {
            btnText.textContent = "INITIALIZING MONITORING...";
            grantBtn.style.pointerEvents = 'none';
            
            setTimeout(() => {
                showDashboard(shizukuGranted);
            }, 1500);
        }

        // Ripple effect
        const ripple = document.createElement('span');
        ripple.classList.add('ripple-effect');
        grantBtn.appendChild(ripple);
        const rect = grantBtn.getBoundingClientRect();
        const x = e.clientX ? e.clientX - rect.left : rect.width / 2;
        const y = e.clientY ? e.clientY - rect.top : rect.height / 2;
        ripple.style.left = `${x}px`;
        ripple.style.top = `${y}px`;
        setTimeout(() => ripple.remove(), 600);
    });

    function showDashboard(isShizuku) {
        document.querySelector('.header-title').textContent = "PROSTATS DASHBOARD";
        grantBtn.style.display = 'none';

        const mainContent = document.querySelector('.main-content');
        
        let cpuVal = 14;
        let ramUsed = 5.8;
        let batCurrent = -162;
        
        mainContent.innerHTML = `
            <!-- Top Grid Row (SOT & Battery Temp) -->
            <div style="display: flex; gap: 16px; margin-bottom: 16px;">
                <!-- SOT Card -->
                <div class="permission-item" style="flex: 1; padding: 20px; text-align: left; display: block;">
                    <div style="font-size: 11px; font-weight: bold; color: var(--text-secondary); letter-spacing: 1px;">SCREEN-ON TIME</div>
                    <div style="font-size: 24px; font-weight: bold; color: #a78bfa; margin-top: 12px;">5h 12m</div>
                    <div style="font-size: 11px; color: var(--text-secondary); margin-top: 4px;">Active Foreground Today</div>
                </div>
                <!-- Temp Card -->
                <div class="permission-item" style="flex: 1; padding: 20px; text-align: left; display: block;">
                    <div style="font-size: 11px; font-weight: bold; color: var(--text-secondary); letter-spacing: 1px;">BATTERY TEMP</div>
                    <div style="font-size: 24px; font-weight: bold; color: var(--accent); margin-top: 12px;">36.2°C</div>
                    <div style="font-size: 11px; color: var(--text-secondary); margin-top: 4px;">Cool (Optimal)</div>
                </div>
            </div>

            <!-- Battery Diagnostics Card -->
            <div class="permission-item" style="padding: 20px; margin-bottom: 16px; display: block; text-align: left;">
                <div style="font-size: 11px; font-weight: bold; color: var(--text-secondary); letter-spacing: 1px; margin-bottom: 16px;">BATTERY DIAGNOSTICS</div>
                <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px;">
                    <div>
                        <div style="font-size: 14px; font-weight: bold; color: white;">Health: Good</div>
                        <div style="font-size: 12px; color: var(--text-secondary); margin-top: 2px;">Voltage: 4.12 V</div>
                    </div>
                    <div style="text-align: right;">
                        <div id="bat-current-val" style="font-size: 14px; font-weight: bold; color: var(--accent-orange);">${batCurrent} mA</div>
                        <div style="font-size: 12px; color: var(--text-secondary); margin-top: 2px;">Status: Discharging (Li-poly)</div>
                    </div>
                </div>
                <div style="font-size: 12px; color: var(--text-secondary); margin-top: 8px; border-top: 1px solid rgba(255,255,255,0.05); padding-top: 8px;">
                    System Thermal State: <span style="color: var(--accent); font-weight: 500;">Normal</span>
                </div>
            </div>

            <!-- CPU Clusters Frequencies -->
            <div class="permission-item" style="padding: 20px; margin-bottom: 16px; display: block; text-align: left;">
                <div style="font-size: 11px; font-weight: bold; color: var(--text-secondary); letter-spacing: 1px; margin-bottom: 16px;">CPU CLUSTER FREQUENCIES</div>
                <div style="display: grid; grid-template-columns: repeat(4, 1fr); gap: 8px;" id="cpu-clusters-container">
                    ${mockCores.map((freq, index) => `
                        <div style="background: rgba(255,255,255,0.05); border: 1px solid rgba(255,255,255,0.03); border-radius: 10px; padding: 8px; text-align: center;">
                            <div style="font-size: 9px; color: var(--text-secondary);">Core ${index}</div>
                            <div class="core-freq-val" style="font-size: 10px; font-weight: bold; color: white; margin-top: 4px;">${freq} MHz</div>
                        </div>
                    `).join('')}
                </div>
            </div>

            <!-- CPU Load Card -->
            <div class="permission-item" style="padding: 20px; margin-bottom: 16px; display: block; text-align: left;">
                <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px;">
                    <div style="font-size: 11px; font-weight: bold; color: var(--text-secondary); letter-spacing: 1px;">SYSTEM CPU LOAD</div>
                    <div id="cpu-numeric-val" style="font-size: 14px; font-weight: bold; color: white;">14%</div>
                </div>
                <div style="width: 100%; height: 6px; background: rgba(255,255,255,0.08); border-radius: 3px; overflow: hidden;">
                    <div id="cpu-bar-fill" style="width: 14%; height: 100%; background: var(--accent); border-radius: 3px; transition: width 0.8s ease-out;"></div>
                </div>
            </div>

            <!-- RAM Card -->
            <div class="permission-item" style="padding: 20px; margin-bottom: 24px; display: block; text-align: left;">
                <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px;">
                    <div style="font-size: 11px; font-weight: bold; color: var(--text-secondary); letter-spacing: 1px;">RAM ALLOCATION</div>
                    <div id="ram-numeric-val" style="font-size: 14px; font-weight: bold; color: white;">5.8 GB / 8.0 GB</div>
                </div>
                <div style="width: 100%; height: 6px; background: rgba(255,255,255,0.08); border-radius: 3px; overflow: hidden;">
                    <div id="ram-bar-fill" style="width: 72.5%; height: 100%; background: var(--accent-orange); border-radius: 3px; transition: width 1.2s ease-out;"></div>
                </div>
            </div>

            <!-- Drill-down Button -->
            <button id="view-processes-btn" class="grant-btn" style="display: block; margin-top: auto; border-radius: 16px; background: white; color: black;">
                <span style="font-weight: bold; font-size: 14px; letter-spacing: 1px;">MANAGE RUNNING PROCESSES</span>
            </button>
        `;

        // Update CPU and RAM loads periodically
        const intervalId = setInterval(() => {
            const dashboardCheck = document.getElementById('view-processes-btn');
            if (!dashboardCheck) {
                clearInterval(intervalId);
                return;
            }
            cpuVal = Math.max(5, Math.min(95, cpuVal + (Math.floor(Math.random() * 10) - 5)));
            ramUsed = Math.max(4.5, Math.min(7.8, ramUsed + (Math.random() * 0.4 - 0.2)));
            batCurrent = Math.max(-450, Math.min(-90, batCurrent + (Math.floor(Math.random() * 20) - 10)));
            
            const cpuNum = document.getElementById('cpu-numeric-val');
            const cpuFill = document.getElementById('cpu-bar-fill');
            const ramNum = document.getElementById('ram-numeric-val');
            const ramFill = document.getElementById('ram-bar-fill');
            const batNum = document.getElementById('bat-current-val');

            if (cpuNum) cpuNum.textContent = `${cpuVal}%`;
            if (cpuFill) cpuFill.style.width = `${cpuVal}%`;
            if (ramNum) ramNum.textContent = `${ramUsed.toFixed(1)} GB / 8.0 GB`;
            if (ramFill) ramFill.style.width = `${(ramUsed / 8.0 * 100).toFixed(1)}%`;
            if (batNum) batNum.textContent = `${batCurrent} mA`;

            // Fluctuating core frequencies
            const coreLabels = document.querySelectorAll('.core-freq-val');
            coreLabels.forEach((label, idx) => {
                const variation = Math.floor(Math.random() * 150) - 75;
                const base = mockCores[idx];
                label.textContent = `${Math.max(1000, base + variation)} MHz`;
            });
        }, 1500);

        // Click handler to open processes list
        document.getElementById('view-processes-btn').addEventListener('click', () => {
            clearInterval(intervalId);
            showProcessList(isShizuku);
        });
    }

    function showProcessList(isShizuku) {
        document.querySelector('.header-title').innerHTML = `
            <div style="display: flex; align-items: center; gap: 12px; cursor: pointer;" id="back-to-dashboard-btn">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round" style="width: 20px; height: 20px; color: white;">
                    <line x1="19" y1="12" x2="5" y2="12"></line>
                    <polyline points="12 19 5 12 12 5"></polyline>
                </svg>
                <span>RUNNING PROCESSES</span>
            </div>
        `;

        const mainContent = document.querySelector('.main-content');
        mainContent.innerHTML = `
            <div class="summary-box" style="margin-bottom: 20px;">
                <div style="background: ${isShizuku ? 'rgba(74,222,128,0.1)' : 'rgba(251,146,60,0.1)'}; border: 1px solid ${isShizuku ? 'var(--accent)' : 'var(--accent-orange)'}; border-radius: 12px; padding: 12px; display: flex; align-items: center; gap: 12px;">
                    <div style="color: ${isShizuku ? 'var(--accent)' : 'var(--accent-orange)'}; font-weight: bold; font-size: 0.9rem;">
                        ${isShizuku ? 'Pro Mode (Shizuku)' : 'Basic Mode (Usage Access)'}
                    </div>
                    <div style="color: var(--text-secondary); font-size: 0.8rem; flex: 1;">
                        ${isShizuku ? 'Real-time CPU/RAM stats active.' : 'To see live CPU/RAM stats, enable Shizuku.'}
                    </div>
                </div>
            </div>

            <!-- Sorting tabs -->
            <div style="display: flex; gap: 8px; margin-bottom: 16px;">
                <button id="sort-cpu" style="flex: 1; padding: 8px; background: rgba(255,255,255,0.05); color: var(--accent); border: none; border-radius: 8px; font-size: 0.8rem; cursor: pointer; font-weight: 600;">CPU</button>
                <button id="sort-ram" style="flex: 1; padding: 8px; background: transparent; color: var(--text-secondary); border: none; border-radius: 8px; font-size: 0.8rem; cursor: pointer;">RAM</button>
                <button id="sort-name" style="flex: 1; padding: 8px; background: transparent; color: var(--text-secondary); border: none; border-radius: 8px; font-size: 0.8rem; cursor: pointer;">Name</button>
            </div>

            <!-- Process List -->
            <div class="permission-list" id="process-list"></div>
        `;

        const listContainer = document.getElementById('process-list');
        let currentSort = "cpu";

        const renderList = () => {
            const sorted = [...mockProcesses].sort((a, b) => {
                if (currentSort === "cpu") return b.cpu - a.cpu;
                if (currentSort === "ram") return b.ram - a.ram;
                return a.name.localeCompare(b.name);
            });

            listContainer.innerHTML = sorted.map(p => `
                <div class="permission-item" style="padding: 16px; cursor: pointer; display: flex; justify-content: space-between; align-items: center;" onclick="window.manageMockProcess('${p.name}', '${p.pkg}')">
                    <div style="display: flex; align-items: center; gap: 12px;">
                        <!-- Mock icon container -->
                        <div style="width: 36px; height: 36px; border-radius: 8px; background: rgba(255,255,255,0.06); display: flex; align-items: center; justify-content: center; font-weight: bold; color: var(--text-secondary); font-size: 0.75rem;">
                            ${p.name.substring(0, 2).toUpperCase()}
                        </div>
                        <div>
                            <div style="font-weight: 600; font-size: 0.95rem; color: white;">${p.name}</div>
                            <div style="font-size: 0.75rem; color: var(--text-secondary); margin-top: 2px;">${p.pkg}</div>
                        </div>
                    </div>
                    <div style="text-align: right;">
                        <div style="font-weight: 600; font-size: 0.9rem; color: ${p.cpu > 10 ? 'var(--accent-orange)' : 'var(--accent)'};">
                            ${isShizuku ? p.cpu.toFixed(1) + '%' : 'Active'}
                        </div>
                        <div style="font-size: 0.75rem; color: var(--text-secondary); margin-top: 2px;">
                            ${isShizuku ? p.ram.toFixed(1) + ' MB' : 'Background'}
                        </div>
                    </div>
                </div>
            `).join('');
        };

        window.manageMockProcess = (name, pkg) => {
            if (!isShizuku) {
                alert(`Cannot manage '${name}' without Shizuku (Pro Mode).`);
                return;
            }
            const action = confirm(`Manage '${name}':\n\nClick OK to Force Stop (SIGKILL)\nClick Cancel to Freeze App (pm disable)`);
            if (action) {
                alert(`Sent SIGKILL to package '${pkg}'`);
                const index = mockProcesses.findIndex(x => x.pkg === pkg);
                if (index !== -1) mockProcesses.splice(index, 1);
                renderList();
            } else {
                alert(`Froze package '${pkg}'`);
            }
        };

        // Setup Sorting Event Listeners
        const btnCpu = document.getElementById('sort-cpu');
        const btnRam = document.getElementById('sort-ram');
        const btnName = document.getElementById('sort-name');

        const setSortActive = (activeBtn, sortKey) => {
            [btnCpu, btnRam, btnName].forEach(b => {
                b.style.background = "transparent";
                b.style.color = "var(--text-secondary)";
            });
            activeBtn.style.background = "rgba(255,255,255,0.05)";
            activeBtn.style.color = "var(--accent)";
            currentSort = sortKey;
            renderList();
        };

        btnCpu.addEventListener('click', () => setSortActive(btnCpu, "cpu"));
        btnRam.addEventListener('click', () => setSortActive(btnRam, "ram"));
        btnName.addEventListener('click', () => setSortActive(btnName, "name"));

        renderList();

        // Simulate fluctuations
        const processIntervalId = setInterval(() => {
            const listCheck = document.getElementById('process-list');
            if (!listCheck) {
                clearInterval(processIntervalId);
                return;
            }
            mockProcesses.forEach(p => {
                if (p.isSystem) {
                    p.cpu = Math.max(0.1, p.cpu + (Math.random() * 2 - 1));
                    p.ram = Math.max(50, p.ram + (Math.random() * 4 - 2));
                } else {
                    p.cpu = Math.max(0.1, p.cpu + (Math.random() * 6 - 3));
                    p.ram = Math.max(20, p.ram + (Math.random() * 10 - 5));
                }
            });
            renderList();
        }, 2000);

        // Back to dashboard handler
        document.getElementById('back-to-dashboard-btn').addEventListener('click', () => {
            clearInterval(processIntervalId);
            showDashboard(isShizuku);
        });
    }
});
