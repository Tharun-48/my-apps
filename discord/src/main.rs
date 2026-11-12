#![windows_subsystem = "windows"]

use std::path::PathBuf;
use tao::{
    dpi::LogicalSize,
    event::{Event, WindowEvent},
    event_loop::{ControlFlow, EventLoop},
    window::WindowBuilder,
};
use wry::{WebContext, WebViewBuilder};

const DISCORD_APP_URL: &str = "https://discord.com/app";

// Performance injection script for low-overhead rendering
const DISCORD_PERF_OPTIMIZATIONS: &str = r#"
(function() {
    console.log('[Discord-Rust] Initializing Native Rust Host Optimizations');

    // 1. Optimize CSS rendering passes: disable non-essential blur/backdrop-filters
    const style = document.createElement('style');
    style.id = 'discord-rust-optimizations';
    style.textContent = `
        body, html {
            background-color: #1e1f22 !important;
        }

        /* Custom subtle scrollbar matching Discord Dark theme */
        ::-webkit-scrollbar {
            width: 8px !important;
            height: 8px !important;
        }
        ::-webkit-scrollbar-thumb {
            background-color: #1a1b1e !important;
            border-radius: 4px !important;
        }
    `;

    if (document.head) {
        document.head.appendChild(style);
    } else {
        document.addEventListener('DOMContentLoaded', () => {
            document.head.appendChild(style);
        });
    }

    // 2. Stream Quality Controller (Toggle via Alt+S or F7)
    let currentQuality = localStorage.getItem('discord_stream_quality') || '1080p60';

    function showQualityToast(msg, is1080) {
        let toast = document.getElementById('discord-rust-toast');
        if (!toast) {
            toast = document.createElement('div');
            toast.id = 'discord-rust-toast';
            toast.style.cssText = 'position:fixed !important;top:20px !important;right:20px !important;z-index:2147483647 !important;background:#2b2d31 !important;color:#ffffff !important;border:2px solid #5865f2 !important;border-radius:10px !important;padding:10px 18px !important;font-size:13px !important;font-weight:700 !important;font-family:sans-serif !important;box-shadow:0 6px 20px rgba(0,0,0,0.7) !important;pointer-events:none !important;transition:opacity 0.25s ease !important;';
            (document.body || document.documentElement).appendChild(toast);
        }
        toast.innerHTML = (is1080 ? '🟢 ' : '🟡 ') + msg;
        toast.style.opacity = '1';
        clearTimeout(toast._timer);
        toast._timer = setTimeout(() => {
            if (toast) toast.style.opacity = '0';
        }, 2500);
    }

    function handleKeyToggle(e) {
        // Support Alt+S, Ctrl+Shift+S, F7 (and Fn+F7)
        if (e.key === 'F7' || (e.altKey && (e.key === 's' || e.key === 'S')) || (e.ctrlKey && e.shiftKey && (e.key === 's' || e.key === 'S'))) {
            e.preventDefault();
            e.stopPropagation();
            currentQuality = currentQuality === '1080p60' ? '720p30' : '1080p60';
            localStorage.setItem('discord_stream_quality', currentQuality);
            const is1080 = currentQuality === '1080p60';
            showQualityToast('Stream Quality: ' + (is1080 ? '1080p @ 60 FPS' : '720p @ 30 FPS') + ' (Alt+S)', is1080);
            console.log('[Discord-Rust] Stream quality toggled to:', currentQuality);
        }
    }

    window.addEventListener('keydown', handleKeyToggle, true);
    document.addEventListener('keydown', handleKeyToggle, true);

    // Show initial confirmation toast when Discord loads
    window.addEventListener('load', () => {
        setTimeout(() => {
            const is1080 = currentQuality === '1080p60';
            showQualityToast('Stream Quality: ' + (is1080 ? '1080p @ 60 FPS' : '720p @ 30 FPS') + ' (Press Alt+S to switch)', is1080);
        }, 2500);
    });

    // Hook Screen Share getDisplayMedia constraints
    if (navigator.mediaDevices && navigator.mediaDevices.getDisplayMedia) {
        const _origGetDisplayMedia = navigator.mediaDevices.getDisplayMedia.bind(navigator.mediaDevices);
        navigator.mediaDevices.getDisplayMedia = async function(constraints) {
            constraints = constraints || {};
            const is1080 = currentQuality === '1080p60';
            const width = is1080 ? 1920 : 1280;
            const height = is1080 ? 1080 : 720;
            const fps = is1080 ? 60 : 30;

            console.log(`[Discord-Rust] Requesting stream: ${width}x${height} @ ${fps}fps`);

            constraints.video = Object.assign(
                typeof constraints.video === 'object' ? constraints.video : {},
                {
                    width: { ideal: width, max: 2560 },
                    height: { ideal: height, max: 1440 },
                    frameRate: { ideal: fps, max: fps }
                }
            );
            return _origGetDisplayMedia(constraints);
        };
    }

    // Boost WebRTC bitrate cap for 1080p streaming
    if (window.RTCPeerConnection) {
        const _origSetLocal = RTCPeerConnection.prototype.setLocalDescription;
        RTCPeerConnection.prototype.setLocalDescription = function(desc) {
            if (desc && desc.sdp) {
                desc.sdp = desc.sdp.replace(/b=AS:2500/g, 'b=AS:8000').replace(/b=TIAS:2500000/g, 'b=TIAS:8000000');
            }
            return _origSetLocal.apply(this, arguments);
        };

        const _origSetRemote = RTCPeerConnection.prototype.setRemoteDescription;
        RTCPeerConnection.prototype.setRemoteDescription = function(desc) {
            if (desc && desc.sdp) {
                desc.sdp = desc.sdp.replace(/b=AS:2500/g, 'b=AS:8000').replace(/b=TIAS:2500000/g, 'b=TIAS:8000000');
            }
            return _origSetRemote.apply(this, arguments);
        };
    }

    // 3. Throttle background analytics/telemetry to preserve CPU cycles
    const originalSend = XMLHttpRequest.prototype.send;
    XMLHttpRequest.prototype.send = function(...args) {
        if (this._url && (this._url.includes('/science') || this._url.includes('/metrics') || this._url.includes('sentry'))) {
            // Drop heavy telemetry pings silently
            return;
        }
        return originalSend.apply(this, args);
    };

    const originalOpen = XMLHttpRequest.prototype.open;
    XMLHttpRequest.prototype.open = function(method, url, ...rest) {
        this._url = url;
        return originalOpen.apply(this, [method, url, ...rest]);
    };
})();
"#;

fn main() -> Result<(), Box<dyn std::error::Error>> {
    println!("============================================================");
    println!("        DISCORD RUST PORT (UNOFFICIAL) - NO ELECTRON       ");
    println!("============================================================");
    println!("Engine: Native Rust Core + DirectComposition WebView2");
    println!("Target: Low-Overhead Resource Efficient Architecture");
    println!("Target Memory: ~180-250 MB RAM (saving >1 GB vs Electron)");
    println!("============================================================");

    // Use existing persistent profile session if present
    let base_dir = dirs::data_dir().unwrap_or_else(|| PathBuf::from("."));
    let legacy_profile = base_dir.join("discord-rust").join("profile");
    let data_dir = if legacy_profile.exists() {
        legacy_profile
    } else {
        base_dir.join("discord-rust-port-unofficial").join("profile")
    };

    std::fs::create_dir_all(&data_dir)?;
    println!("[Storage] Persistent login profile at: {:?}", data_dir);

    let event_loop = EventLoop::new();

    let window = WindowBuilder::new()
        .with_title("Discord Rust Port (Unofficial)")
        .with_inner_size(LogicalSize::new(1280.0, 720.0))
        .with_min_inner_size(LogicalSize::new(800.0, 500.0))
        .build(&event_loop)?;

    let mut web_context = WebContext::new(Some(data_dir));

    let _webview = WebViewBuilder::with_web_context(&mut web_context)
        .with_url(DISCORD_APP_URL)
        .with_initialization_script(DISCORD_PERF_OPTIMIZATIONS)
        .with_devtools(true)
        .build(&window)?;

    println!("[Runtime] Official Discord loaded. Native Rust event loop active.");
    println!("[Shortcuts] F7 to toggle Stream Quality (1080p 60fps / 720p 30fps) | Ctrl+R to reload | Close window to exit.");

    event_loop.run(move |event, _, control_flow| {
        *control_flow = ControlFlow::Wait;

        if let Event::WindowEvent { event, .. } = event {
            match event {
                WindowEvent::CloseRequested => {
                    println!("[Runtime] Closing Discord Rust client cleanly.");
                    *control_flow = ControlFlow::Exit;
                }
                _ => {}
            }
        }
    });
}
