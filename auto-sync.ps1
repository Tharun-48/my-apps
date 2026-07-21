# =============================================
# Auto-Sync Script for my-apps GitHub Repo
# Watches for file changes and auto-commits + pushes
# =============================================

$repoPath = "C:\Users\SoloWanderer\Documents\antigravity\my-apps"
$git = "C:\Program Files\Git\cmd\git.exe"
$debounceSeconds = 5   # Wait 5s after last change before committing

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Auto-Sync: my-apps GitHub Watcher" -ForegroundColor Cyan
Write-Host "  Repo: $repoPath" -ForegroundColor Gray
Write-Host "  Debounce: ${debounceSeconds}s after last change" -ForegroundColor Gray
Write-Host "  Press Ctrl+C to stop." -ForegroundColor Yellow
Write-Host "========================================" -ForegroundColor Cyan

# Set up a FileSystemWatcher
$watcher = New-Object System.IO.FileSystemWatcher
$watcher.Path = $repoPath
$watcher.IncludeSubdirectories = $true
$watcher.EnableRaisingEvents = $true

# Ignore .git internals, build folders, temp files
$ignorePatterns = @("\\\.git\\", "\\build\\", "\\obj\\", "\.class$", "\.tmp$")

$lastChangeTime = [datetime]::MinValue
$pendingSync = $false

function Should-Ignore($path) {
    foreach ($pattern in $ignorePatterns) {
        if ($path -match $pattern) {
            return $true
        }
    }
    return $false
}

function Sync-ToGitHub {
    Write-Host ""
    Write-Host "[$(Get-Date -Format 'HH:mm:ss')] Detected changes. Syncing to GitHub..." -ForegroundColor Yellow

    # Check if there's anything to commit
    $status = & $git -C $repoPath status --porcelain
    if (-not $status) {
        Write-Host "[$(Get-Date -Format 'HH:mm:ss')] Nothing to commit. Skipping." -ForegroundColor Gray
        return
    }

    # Stage all changes
    & $git -C $repoPath add -A
    if ($LASTEXITCODE -ne 0) {
        Write-Host "[$(Get-Date -Format 'HH:mm:ss')] ERROR: git add failed." -ForegroundColor Red
        return
    }

    # Create commit message with timestamp
    $timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
    $commitMsg = "Auto-sync: $timestamp"

    & $git -C $repoPath commit -m $commitMsg
    if ($LASTEXITCODE -ne 0) {
        Write-Host "[$(Get-Date -Format 'HH:mm:ss')] ERROR: git commit failed." -ForegroundColor Red
        return
    }

    # Push to GitHub
    Write-Host "[$(Get-Date -Format 'HH:mm:ss')] Pushing to GitHub..." -ForegroundColor Cyan
    & $git -C $repoPath push origin main
    if ($LASTEXITCODE -eq 0) {
        Write-Host "[$(Get-Date -Format 'HH:mm:ss')] Pushed successfully!" -ForegroundColor Green
    } else {
        Write-Host "[$(Get-Date -Format 'HH:mm:ss')] Push failed. Check your connection/token." -ForegroundColor Red
    }
}

# Register change events
$action = {
    $path = $Event.SourceEventArgs.FullPath
    if (-not (Should-Ignore $path)) {
        $script:lastChangeTime = [datetime]::Now
        $script:pendingSync = $true
    }
}

Register-ObjectEvent $watcher "Changed" -Action $action | Out-Null
Register-ObjectEvent $watcher "Created" -Action $action | Out-Null
Register-ObjectEvent $watcher "Deleted" -Action $action | Out-Null
Register-ObjectEvent $watcher "Renamed" -Action $action | Out-Null

Write-Host "[$(Get-Date -Format 'HH:mm:ss')] Watching for file changes..." -ForegroundColor Green

# Main loop: check if debounce time has passed since last change
try {
    while ($true) {
        Start-Sleep -Milliseconds 1000

        if ($pendingSync) {
            $elapsed = ([datetime]::Now - $lastChangeTime).TotalSeconds
            if ($elapsed -ge $debounceSeconds) {
                $pendingSync = $false
                Sync-ToGitHub
                Write-Host "[$(Get-Date -Format 'HH:mm:ss')] Watching for file changes..." -ForegroundColor Green
            }
        }
    }
} finally {
    $watcher.EnableRaisingEvents = $false
    $watcher.Dispose()
    Write-Host "Auto-sync stopped." -ForegroundColor Yellow
}
