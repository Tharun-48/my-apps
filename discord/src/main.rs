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

const DISCORD_PERF_OPTIMIZATIONS: &str = r#"
(function() {
    // 1. CSS: background color + custom scrollbar
    const style = document.createElement('style');
    style.id = 'discord-rust-optimizations';
    style.textContent = `
        body, html {
            background-color: #1e1f22 !important;
        }
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

    // 2. Block telemetry via XHR
    const originalOpen = XMLHttpRequest.prototype.open;
    const originalSend = XMLHttpRequest.prototype.send;
    XMLHttpRequest.prototype.open = function(method, url, ...rest) {
        this._url = url;
        return originalOpen.apply(this, [method, url, ...rest]);
    };
    XMLHttpRequest.prototype.send = function(...args) {
        if (this._url && (this._url.includes('/science') || this._url.includes('/metrics') || this._url.includes('sentry'))) {
            return;
        }
        return originalSend.apply(this, args);
    };

    // 3. Block telemetry via fetch()
    const originalFetch = window.fetch;
    window.fetch = function(url, ...args) {
        if (typeof url === 'string' && (url.includes('/science') || url.includes('/metrics') || url.includes('sentry'))) {
            return Promise.resolve(new Response('', { status: 200 }));
        }
        return originalFetch.apply(this, [url, ...args]);
    };
})();
"#;

fn main() -> Result<(), Box<dyn std::error::Error>> {
    // Safe browser flags: reduce background services, limit renderer processes
    std::env::set_var(
        "WEBVIEW2_ADDITIONAL_BROWSER_ARGUMENTS",
        "--renderer-process-limit=1 --disable-features=Translate,OptimizationHints,MediaRouter",
    );

    // Use existing persistent profile session if present
    let base_dir = dirs::data_dir().unwrap_or_else(|| PathBuf::from("."));
    let legacy_profile = base_dir.join("discord-rust").join("profile");
    let data_dir = if legacy_profile.exists() {
        legacy_profile
    } else {
        base_dir.join("discord-rust-port-unofficial").join("profile")
    };

    std::fs::create_dir_all(&data_dir)?;

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
        .with_user_agent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36")
        .with_devtools(false)
        .build(&window)?;

    event_loop.run(move |event, _, control_flow| {
        *control_flow = ControlFlow::Wait;

        if let Event::WindowEvent { event, .. } = event {
            if let WindowEvent::CloseRequested = event {
                *control_flow = ControlFlow::Exit;
            }
        }
    });
}
