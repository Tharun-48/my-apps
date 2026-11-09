@echo off
title Discord Rust Port (Unofficial)
if not exist "%~dp0target\debug\WebView2Loader.dll" (
    copy "%~dp0WebView2Loader.dll" "%~dp0target\debug\WebView2Loader.dll" >nul
)
if exist "%~dp0target\debug\discord-rust-port-unofficial.exe" (
    start "" "%~dp0target\debug\discord-rust-port-unofficial.exe"
) else (
    start "" "%~dp0target\debug\discord-rust.exe"
)
