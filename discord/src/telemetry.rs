use std::time::{Duration, Instant};
use sysinfo::{Pid, ProcessesToUpdate, System};

pub struct TelemetryTracker {
    system: System,
    pid: Pid,
    pub last_frame_time: Duration,
    pub frame_counter: u64,
    pub fps: f64,
    pub last_fps_update: Instant,
    pub memory_rss_mb: f64,
    pub cpu_usage_pct: f32,
    pub gateway_ping_ms: u64,
}

impl TelemetryTracker {
    pub fn new() -> Self {
        let mut system = System::new();
        let pid = Pid::from_u32(std::process::id());
        system.refresh_processes(ProcessesToUpdate::Some(&[pid]));

        Self {
            system,
            pid,
            last_frame_time: Duration::from_micros(380),
            frame_counter: 0,
            fps: 60.0,
            last_fps_update: Instant::now(),
            memory_rss_mb: 42.5,
            cpu_usage_pct: 0.8,
            gateway_ping_ms: 24,
        }
    }

    pub fn mark_frame(&mut self, render_time: Duration) {
        self.last_frame_time = render_time;
        self.frame_counter += 1;

        let elapsed = self.last_fps_update.elapsed();
        if elapsed >= Duration::from_millis(500) {
            self.fps = (self.frame_counter as f64) / elapsed.as_secs_f64();
            self.frame_counter = 0;
            self.last_fps_update = Instant::now();

            // Refresh system process memory for current process
            self.system.refresh_processes(ProcessesToUpdate::Some(&[self.pid]));
            if let Some(proc) = self.system.process(self.pid) {
                // Resident Set Size (RSS) in bytes
                self.memory_rss_mb = (proc.memory() as f64) / (1024.0 * 1024.0);
                self.cpu_usage_pct = proc.cpu_usage();
            }
        }
    }
}
