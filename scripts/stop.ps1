# Stops the app. Your data is kept. Use -Reset to also delete all data (fresh demo next start).
param([switch]$Reset)
. (Join-Path $PSScriptRoot 'common.ps1')

Write-Title "Community Store - stop"
if (-not (Test-CommandExists 'docker') -or -not (Test-DockerRunning)) {
    Write-Ok "Docker isn't running, so the app is already stopped."
    exit 0
}
if ($Reset) {
    if (-not (Confirm-YesNo "Delete ALL app data (users, orders, events) and stop?")) { exit 0 }
    if (Invoke-Visible { docker compose --profile tests down -v --remove-orphans }) {
        Write-Ok "Stopped and reset. The next Start begins with fresh demo data."
    }
} else {
    if (Invoke-Visible { docker compose --profile tests stop }) {
        Write-Ok "Stopped. Your data is kept for next time."
    }
}
Write-Info "You can also quit Docker Desktop now if you like."
