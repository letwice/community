# One-time setup: checks what the app needs, explains how to install anything missing, and prepares the app.
. (Join-Path $PSScriptRoot 'common.ps1')

Write-Title "Community Store - setup"
Write-Info "Computer: Windows ($(Get-ChipDescription))"

Write-Step "1/5  Checking Docker"
Assert-Docker
Test-DockerMemory

Write-Step "2/5  Checking network ports"
Assert-AppPorts
Select-MysqlPort

Write-Step "3/5  Checking Java (optional - only for the desktop app)"
if (Test-Java17) {
    Write-Ok "Java 17 or newer is installed"
} else {
    Write-Warn "Java 17 or newer is not installed. That's fine - the web app does not need it."
    Write-Info "Only needed for the optional desktop app ('Open desktop app.cmd'). To install it later:"
    Write-Info "  - Download the Windows installer (.msi) from https://adoptium.net/temurin/releases/?version=17"
    if (Test-CommandExists 'winget') { Write-Info "  - or run: winget install -e --id EclipseAdoptium.Temurin.17.JDK" }
}

Write-Step "4/5  Downloading and building the app (first time: 5-10 minutes)"
Write-Info "Docker downloads the database and builds the app. Later starts are much faster."
if (-not (Invoke-Visible { docker compose pull --quiet mysql })) {
    Stop-WithError "Could not download the database image." @("Check your internet connection and try again.")
}
if (-not (Invoke-Visible { docker compose --profile tests build })) {
    Stop-WithError "Building the app failed." @("Check your internet connection and try again. If it keeps failing, send the text above to whoever supports you.")
}
Write-Ok "App is built"

Write-Step "5/5  Done"
Write-Ok "Setup complete!"
Write-Info "Next: double-click 'Start.cmd' (or 'Start with test data.cmd' for a fuller demo)."
