#!/usr/bin/env bash
# Opens the optional JavaFX desktop app (admin/vendor/leader tools). Needs Java 17+ and the app running.
source "$(dirname "$0")/common.sh"
detect_os

title "Community Store — desktop app"
if ! command -v java >/dev/null 2>&1 || ! java -version 2>&1 | grep -Eq 'version "(1[7-9]|[2-9][0-9])'; then
  die "The desktop app needs Java 17 or newer." \
      "Install it from https://adoptium.net/temurin/releases/?version=17 (choose the .pkg installer on a Mac), then try again." \
      "The web app ($WEB_URL) does not need Java."
fi
ok "Java found: $(java -version 2>&1 | head -1)"
[ "$(http_status http://localhost:9600/auth/.well-known/jwks.json)" = "200" ] || warn "The app doesn't seem to be running — double-click Start first, or you won't be able to log in."

step "Building and opening the desktop app (the first time downloads a few things)"
./mvnw -q -B -pl service-ui-desktop -am -DskipTests install || die "Could not build the desktop app."
./mvnw -q -B -pl service-ui-desktop javafx:run
