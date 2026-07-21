document.addEventListener('DOMContentLoaded', () => {
    const permBattery = document.getElementById('perm-battery');
    const permProcess = document.getElementById('perm-process'); // This is Usage Access
    const permShizuku = document.getElementById('perm-shizuku');
    const grantBtn = document.getElementById('grant-btn');
    const btnText = grantBtn.querySelector('.btn-text');

    // Helper to call native bridge safely
    const callNative = (method) => {
        if (typeof AndroidBridge !== 'undefined' && AndroidBridge[method]) {
            AndroidBridge[method]();
        } else {
            console.log(`Called native method: ${method}`);
        }
    };

    // Handle toggles
    permBattery.addEventListener('change', () => {
        const item = document.getElementById('item-battery');
        if (permBattery.checked) {
            callNative('openBatterySettings');
            item.classList.add('active');
            item.style.borderColor = 'var(--text-primary)';
        } else {
            item.classList.remove('active');
            item.style.borderColor = 'var(--border)';
        }
        updateButtonState();
    });

    permProcess.addEventListener('change', () => {
        const item = document.getElementById('item-process');
        if (permProcess.checked) {
            callNative('openUsageAccessSettings');
            item.classList.add('active');
            item.style.borderColor = 'var(--text-primary)';
        } else {
            item.classList.remove('active');
            item.style.borderColor = 'var(--border)';
        }
        updateButtonState();
    });

    permShizuku.addEventListener('change', () => {
        const item = document.getElementById('item-shizuku');
        if (permShizuku.checked) {
            callNative('openShizukuApp');
            item.classList.add('active');
            item.style.borderColor = 'var(--text-primary)';
        } else {
            item.classList.remove('active');
            item.style.borderColor = 'var(--border)';
        }
        updateButtonState();
    });

    function updateButtonState() {
        // Shizuku is optional, only Battery and Usage Access (Process) are required
        const requiredChecked = permBattery.checked && permProcess.checked;
        if (requiredChecked) {
            btnText.textContent = "START MONITORING";
            grantBtn.classList.add('granted');
        } else {
            btnText.textContent = "GRANT REQUIRED & START MONITORING";
            grantBtn.classList.remove('granted');
        }
    }

    grantBtn.addEventListener('click', (e) => {
        const requiredChecked = permBattery.checked && permProcess.checked;
        
        if (!requiredChecked) {
            // Request required permissions sequentially
            if (!permBattery.checked) {
                permBattery.checked = true;
                permBattery.dispatchEvent(new Event('change'));
            }
            // Add a slight delay before triggering the next intent so they don't overlap
            setTimeout(() => {
                if (!permProcess.checked) {
                    permProcess.checked = true;
                    permProcess.dispatchEvent(new Event('change'));
                }
            }, 500);
        } else {
            // Proceed - transition to active monitoring view
            btnText.textContent = "INITIALIZING MONITORING...";
            grantBtn.style.pointerEvents = 'none';
            
            setTimeout(() => {
                document.querySelector('.main-content').innerHTML = `
                    <div class="summary-box" style="text-align: center; margin-top: 60px; animation: fadeIn 0.5s ease-out;">
                        <div class="icon-container" style="width: 80px; height: 80px; margin: 0 auto 24px; background: rgba(74, 222, 128, 0.1); display: flex; justify-content: center; align-items: center; border-radius: 50%;">
                            <svg viewBox="0 0 24 24" fill="none" stroke="#4ade80" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round" style="width: 40px; height: 40px;">
                                <polyline points="20 6 9 17 4 12"></polyline>
                            </svg>
                        </div>
                        <h2 style="font-size: 1.8rem; margin-bottom: 12px; color: var(--accent); font-weight: 600;">Monitoring Active</h2>
                        <p style="color: var(--text-secondary); font-size: 1.05rem; line-height: 1.5; font-weight: 300;">Pro Stats is now successfully monitoring battery diagnostics and process usage in the background.</p>
                    </div>
                `;
                document.querySelector('.header-title').textContent = "MONITORING ACTIVE";
                grantBtn.style.display = 'none';
            }, 1500);
        }

        // Ripple animation
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

    // Simulate CPU graph animation & readings
    const cpuValue = document.querySelector('.readout-value');
    const cpuFill = document.querySelector('.progress-fill');
    if (cpuValue && cpuFill) {
        setInterval(() => {
            const val = Math.floor(Math.random() * 20) + 10;
            cpuValue.textContent = `${val}%`;
            cpuFill.style.width = `${val}%`;
        }, 2000);
    }
});
