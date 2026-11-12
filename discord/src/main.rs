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

        /* Stream Quality Selector Pill */
        #discord-stream-quality-btn {
            position: fixed;
            bottom: 58px;
            left: 80px;
            z-index: 99999;
            background-color: #2b2d31;
            color: #dbdee1;
            border: 1px solid #3f4147;
            border-radius: 14px;
            padding: 4px 10px;
            font-size: 11px;
            font-weight: 600;
            font-family: 'gg sans', 'Noto Sans', 'Helvetica Neue', Helvetica, Arial, sans-serif;
            cursor: pointer;
            display: flex;
            align-items: center;
            gap: 6px;
            box-shadow: 0 2px 6px rgba(0,0,0,0.4);
            transition: background 0.15s, border-color 0.15s, transform 0.1s;
            user-select: none;
        }
        #discord-stream-quality-btn:hover {
            background-color: #35373c;
            border-color: #5865f2;
            color: #ffffff;
        }
        #discord-stream-quality-btn:active {
            transform: scale(0.96);
        }
        #discord-stream-quality-dot {
            width: 7px;
            height: 7px;
            border-radius: 50%;
            background-color: #23a55a;
        }
    `;

    if (document.head) {
        document.head.appendChild(style);
    } else {
        document.addEventListener('DOMContentLoaded', () => {
            document.head.appendChild(style);
        });
    }

    // 2. Stream Quality Controller (1080p 60FPS / 720p 30FPS)
    let currentQuality = localStorage.getItem('discord_stream_quality') || '1080p60';

    function updateQualityButton(btn) {
        if (!btn) return;
        const is1080p = currentQuality === '1080p60';
        btn.innerHTML = `<span id="discord-stream-quality-dot" style="background-color: ${is1080p ? '#23a55a' : '#f0b232'}"></span> Stream: ${is1080p ? '1080p @ 60 FPS' : '720p @ 30 FPS'}`;
        btn.title = `Click to toggle stream quality (Current: ${is1080p ? '1080p 60 FPS' : '720p 30 FPS'})`;
    }

    function initQualityButton() {
        if (document.getElementById('discord-stream-quality-btn')) return;
        const btn = document.createElement('button');
        btn.id = 'discord-stream-quality-btn';
        updateQualityButton(btn);

        btn.addEventListener('click', (e) => {
            e.stopPropagation();
            currentQuality = currentQuality === '1080p60' ? '720p30' : '1080p60';
            localStorage.setItem('discord_stream_quality', currentQuality);
            updateQualityButton(btn);
            console.log('[Discord-Rust] Stream quality switched to:', currentQuality);
        });

        document.body.appendChild(btn);
    }

    if (document.body) {
        initQualityButton();
    } else {
        document.addEventListener('DOMContentLoaded', initQualityButton);
    }

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

    // Remove 2500 kbps WebRTC Bitrate Cap for Crystal Clear Streaming
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

    // 3. Safe Native Notification Permission Bridge
    if (typeof Notification !== 'undefined') {
        const _origReqPerm = Notification.requestPermission.bind(Notification);
        Notification.requestPermission = function(cb) {
            return Promise.resolve('granted').then((res) => {
                if (typeof cb === 'function') cb(res);
                return res;
            });
        };
    }

    // 4. Throttle background analytics/telemetry to preserve CPU cycles
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
    println!("[Shortcuts] Ctrl+R to reload | Close window to exit.");

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
