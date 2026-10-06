# Starts the whole app.
#   -WithTestData  reset to a fresh demo database, then run the automated API tests, which also add test data
#   -Yes           don't ask for confirmation before resetting
#   -NoBrowser     don't open the web browser at the end
param(
    [switch]$WithTestData,
    [switch]$Yes,
    [switch]$NoBrowser
)
. (Join-Path $PSScriptRoot 'common.ps1')

Write-Title "Community Store - start"

Write-Step "Checking Docker"
Assert-Docker
Assert-AppPorts
Select-MysqlPort

if ($WithTestData) {
    Write-Step "Preparing a fresh demo database"
    Write-Warn "Starting with test data RESETS the app: everything created so far (users, orders, events...) is deleted."
    if (-not $Yes -and -not (Confirm-YesNo "Reset and load test data?")) {
        Write-Info "Nothing was changed. To start without resetting, double-click Start.cmd instead."
        exit 0
    }
    Invoke-Quiet { docker compose --profile tests down -v --remove-orphans } | Out-Null
    Write-Ok "Database reset"
}

Write-Step "Starting the app (the first start can take a few minutes)"
if (-not (Invoke-Visible { docker compose up -d --build --remove-orphans })) {
    Stop-WithError "The app could not be started." @("Make sure Docker Desktop is running, then try again. Did you run Setup first?")
}

Write-Step "Waiting for everything to be ready"
Wait-ForApp

if ($WithTestData) {
    Write-Step "Running the automated tests (this also creates test data)"
    if (Invoke-Visible { docker compose --profile tests run --rm --build api-tests }) {
        Write-Ok "All automated tests passed - test data is loaded"
    } else {
        Write-Warn "Some automated tests failed (details above). The app is still running and usable."
    }
}

Write-Title "Community Store is running"
Show-Accounts
if (-not $NoBrowser) { Start-Process $WebUrl }
exit 0
