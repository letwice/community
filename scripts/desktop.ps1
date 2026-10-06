# Opens the optional JavaFX desktop app (admin/vendor/leader tools). Needs Java 17+ and the app running.
. (Join-Path $PSScriptRoot 'common.ps1')

Write-Title "Community Store - desktop app"
if (-not (Test-Java17)) {
    Stop-WithError "The desktop app needs Java 17 or newer." @(
        "Install it from https://adoptium.net/temurin/releases/?version=17 (choose the .msi installer), then try again.",
        "The web app ($WebUrl) does not need Java.")
}
Write-Ok "Java 17 or newer found"
if ((Get-HttpStatus 'http://localhost:9600/auth/.well-known/jwks.json') -ne '200') {
    Write-Warn "The app doesn't seem to be running - double-click Start.cmd first, or you won't be able to log in."
}

Write-Step "Building and opening the desktop app (the first time downloads a few things)"
$mvnw = Join-Path $ProjectRoot 'mvnw.cmd'
if (-not (Invoke-Visible { & $mvnw -q -B -pl service-ui-desktop -am -DskipTests install })) {
    Stop-WithError "Could not build the desktop app."
}
Invoke-Visible { & $mvnw -q -B -pl service-ui-desktop javafx:run } | Out-Null
