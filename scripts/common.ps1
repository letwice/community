# Shared helpers for the Windows scripts (setup.ps1, start.ps1, stop.ps1, desktop.ps1).
# Compatible with Windows PowerShell 5.1 (built into Windows 10/11) and PowerShell 7.
# Written for people who have never used a terminal: every problem comes with plain instructions.

$ErrorActionPreference = 'Stop'
$ProjectRoot = Split-Path -Parent $PSScriptRoot
Set-Location $ProjectRoot

$AppPorts = @(9600, 9601, 9602, 9603, 9700)
$WebUrl = 'http://localhost:9700'

function Write-Title([string]$Text) { Write-Host ""; Write-Host $Text -ForegroundColor White }
function Write-Step([string]$Text)  { Write-Host ""; Write-Host "> $Text" -ForegroundColor Cyan }
function Write-Ok([string]$Text)    { Write-Host "  [OK] $Text" -ForegroundColor Green }
function Write-Warn([string]$Text)  { Write-Host "  [!]  $Text" -ForegroundColor Yellow }
function Write-Fail([string]$Text)  { Write-Host "  [X]  $Text" -ForegroundColor Red }
function Write-Info([string]$Text)  { Write-Host "       $Text" }

# Stops the script with a message. The .cmd launchers keep the window open so it can be read.
function Stop-WithError([string]$Message, [string[]]$Help = @()) {
    Write-Host ""
    Write-Host $Message -ForegroundColor Red
    foreach ($line in $Help) { Write-Host $line }
    exit 1
}

function Confirm-YesNo([string]$Question) {
    Write-Host ""
    $answer = Read-Host "  $Question [y/N]"
    return $answer -match '^[Yy]'
}

function Test-CommandExists([string]$Name) {
    return [bool](Get-Command $Name -ErrorAction SilentlyContinue)
}

# Runs a native command quietly and returns $true when it succeeds.
function Invoke-Quiet([scriptblock]$Command) {
    $previous = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        & $Command *> $null
        return ($LASTEXITCODE -eq 0)
    } catch {
        return $false
    } finally {
        $ErrorActionPreference = $previous
    }
}

# Runs a native command showing its output; returns $true when it succeeds.
function Invoke-Visible([scriptblock]$Command) {
    $previous = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        & $Command
        return ($LASTEXITCODE -eq 0)
    } finally {
        $ErrorActionPreference = $previous
    }
}

function Get-ChipDescription {
    if ($env:PROCESSOR_ARCHITECTURE -eq 'ARM64') { return 'arm64' }
    return 'amd64'
}

# ------------------------------------------------------------------------------------------- Docker

function Show-DockerInstallHelp {
    Write-Fail "Docker Desktop is not installed. The app runs inside Docker, so it is required."
    $arch = Get-ChipDescription
    $url = "https://desktop.docker.com/win/main/$arch/Docker%20Desktop%20Installer.exe"
    Write-Host ""
    Write-Info "How to install Docker Desktop on Windows:"
    Write-Info "  1. Download the installer: $url"
    Write-Info "  2. Run 'Docker Desktop Installer.exe' and keep the default options"
    Write-Info "     (leave 'Use WSL 2 instead of Hyper-V' ticked)."
    Write-Info "  3. Restart the computer if the installer asks you to."
    Write-Info "  4. Open Docker Desktop from the Start menu and accept the terms."
    Write-Info "     (A Docker account is not needed - choose 'Skip' / 'Continue without signing in'.)"
    Write-Info "  5. If Docker says WSL needs updating, follow its prompt (or run 'wsl --update')."
    Write-Info "  6. Double-click Setup.cmd again."
    if (Test-CommandExists 'winget') {
        if (Confirm-YesNo "Install Docker Desktop for you now with winget (Windows Package Manager)?") {
            if (Invoke-Visible { winget install -e --id Docker.DockerDesktop --accept-package-agreements --accept-source-agreements }) {
                Write-Ok "Docker Desktop installed. Restart the computer if asked, open Docker Desktop, then run Setup again."
            } else {
                Write-Warn "winget could not install it. Please use the download link above."
            }
        }
    } else {
        Start-Process $url
    }
    exit 1
}

function Test-DockerRunning { return (Invoke-Quiet { docker info }) }

function Start-DockerDesktop {
    $candidates = @(
        (Join-Path $env:ProgramFiles 'Docker\Docker\Docker Desktop.exe'),
        (Join-Path $env:LOCALAPPDATA 'Docker\Docker Desktop.exe')
    )
    foreach ($exe in $candidates) {
        if (Test-Path $exe) { Start-Process $exe; return $true }
    }
    return $false
}

# Makes sure Docker is installed and running; starts Docker Desktop if it is closed.
function Assert-Docker {
    if (-not (Test-CommandExists 'docker')) { Show-DockerInstallHelp }
    $version = (& docker --version 2>$null) -replace ',.*', ''
    Write-Ok "Docker is installed ($version)"

    if (-not (Test-DockerRunning)) {
        Write-Warn "Docker Desktop is not running - starting it now..."
        if (-not (Start-DockerDesktop)) {
            Stop-WithError "Could not find Docker Desktop to start it." @("Open Docker Desktop from the Start menu, wait until it says 'Engine running', then try again.")
        }
        Write-Host -NoNewline "       Waiting for Docker to be ready"
        $waited = 0
        while (-not (Test-DockerRunning)) {
            if ($waited -ge 240) {
                Write-Host ""
                Stop-WithError "Docker did not start within 4 minutes." @(
                    "Open Docker Desktop yourself. If it asks you to accept terms, update WSL or restart, do that first, then try again.")
            }
            Write-Host -NoNewline "."
            Start-Sleep -Seconds 3
            $waited += 3
        }
        Write-Host ""
    }
    Write-Ok "Docker is running"

    if (-not (Invoke-Quiet { docker compose version })) {
        Stop-WithError "Docker Compose v2 is missing." @("Update Docker Desktop to the latest version, then try again.")
    }
    Write-Ok "Docker Compose is available"
}

function Test-DockerMemory {
    $bytes = 0
    try { $bytes = [int64](& docker info --format '{{.MemTotal}}' 2>$null) } catch { $bytes = 0 }
    $gb = [math]::Floor($bytes / 1GB)
    if ($bytes -gt 0 -and $bytes -lt 3.5GB) {
        Write-Warn "Docker only has about $gb GB of memory. The app needs at least 4 GB."
        Write-Info "Docker Desktop -> Settings (gear icon) -> Resources: give it 4 GB or more (or free up memory in WSL)."
    } else {
        Write-Ok "Docker has enough memory ($gb GB)"
    }
}

# ------------------------------------------------------------------------------------------- Ports

function Test-PortInUse([int]$Port) {
    try {
        return [bool](Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue)
    } catch {
        # Fallback for systems without the NetTCPIP module.
        return [bool](netstat -ano | Select-String -Pattern "^\s*TCP\s+\S+:$Port\s+\S+\s+LISTENING")
    }
}

function Test-AppRunning {
    $previous = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try { $ids = & docker compose ps --status running -q 2>$null } finally { $ErrorActionPreference = $previous }
    return [bool]$ids
}

function Assert-AppPorts {
    if (Test-AppRunning) {
        Write-Ok "The app is already running - its ports are in use by the app itself"
        return
    }
    $busy = @($AppPorts | Where-Object { Test-PortInUse $_ })
    if ($busy.Count -gt 0) {
        Stop-WithError "These ports are already used by another program: $($busy -join ', ')" @(
            "Close the other program (or restart the computer) and try again.",
            "To see what is using a port, run: Get-Process -Id (Get-NetTCPConnection -LocalPort $($busy[0])).OwningProcess")
    }
    Write-Ok "Ports $($AppPorts -join ', ') are free"
}

# Picks a free port for the database and remembers it in .env (read automatically by docker compose).
function Select-MysqlPort {
    $envFile = Join-Path $ProjectRoot '.env'
    if (Test-Path $envFile) {
        $line = Select-String -Path $envFile -Pattern '^MYSQL_HOST_PORT=(\d+)' | Select-Object -First 1
        if ($line) { Write-Ok "Database port: $($line.Matches[0].Groups[1].Value) (from .env)"; return }
    }
    foreach ($port in 3306..3312) {
        if (-not (Test-PortInUse $port)) {
            # ASCII without BOM so docker compose can read it.
            [System.IO.File]::AppendAllText($envFile, "MYSQL_HOST_PORT=$port`n", (New-Object System.Text.UTF8Encoding($false)))
            Write-Ok "Database port: $port (saved in .env)"
            return
        }
    }
    Stop-WithError "Could not find a free port for the database (tried 3306-3312)."
}

# ------------------------------------------------------------------------------------------- Waiting

function Get-HttpStatus([string]$Url) {
    # curl.exe ships with Windows 10 (1803+) and 11; it avoids PowerShell's exceptions on 401.
    $previous = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $status = & curl.exe -s -o NUL -m 5 -w '%{http_code}' $Url 2>$null
        if ($status) { return [string]$status } else { return '000' }
    } catch {
        return '000'
    } finally {
        $ErrorActionPreference = $previous
    }
}

function Wait-ForService([string]$Name, [string]$Url) {
    Write-Host -NoNewline ("       {0,-24}" -f $Name)
    $waited = 0
    while ($true) {
        $status = Get-HttpStatus $Url
        if ($status -eq '200' -or $status -eq '401') {
            Write-Host " ready" -ForegroundColor Green
            return $true
        }
        if ($waited -ge 300) {
            Write-Host " not responding" -ForegroundColor Red
            return $false
        }
        Write-Host -NoNewline "."
        Start-Sleep -Seconds 3
        $waited += 3
    }
}

function Wait-ForApp {
    $results = @(
        (Wait-ForService 'Sign-in service' 'http://localhost:9600/auth/.well-known/jwks.json'),
        (Wait-ForService 'User service' 'http://localhost:9601/service-user/system/health'),
        (Wait-ForService 'Notification service' 'http://localhost:9602/service-notification/system/health'),
        (Wait-ForService 'Product service' 'http://localhost:9603/service-product/system/health'),
        (Wait-ForService 'Web app' $WebUrl)
    )
    if ($results -contains $false) {
        Stop-WithError "Some parts of the app did not start." @(
            "Try Stop, then Start again. If it keeps failing, save the log for whoever supports you:",
            "  docker compose logs > community-store-log.txt")
    }
}

function Show-Accounts {
    Write-Host ""
    Write-Host "  Open the app: $WebUrl" -ForegroundColor White
    Write-Host ""
    Write-Host "  Demo accounts (password for all: Password123!)"
    Write-Host "    Student ............ student@university.com"
    Write-Host "    Vendor ............. vendor@community.store"
    Write-Host "    Community leader ... leader@community.store"
    Write-Host "    Faculty ............ faculty@university.com"
    Write-Host "    Admin .............. admin@community.store"
    Write-Host ""
    Write-Host "  When you're finished, double-click Stop.cmd (your data is kept)."
}

function Test-Java17 {
    if (-not (Test-CommandExists 'java')) { return $false }
    $previous = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try { $versionLine = (& java -version 2>&1 | Select-Object -First 1 | Out-String) } finally { $ErrorActionPreference = $previous }
    return ($versionLine -match 'version "(1[7-9]|[2-9][0-9])')
}
