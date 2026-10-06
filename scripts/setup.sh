#!/usr/bin/env bash
# One-time setup: checks what the app needs, explains how to install anything missing, and prepares the app.
source "$(dirname "$0")/common.sh"
detect_os

title "Community Store — setup"
[ "$OS" = "windows-bash" ] && die "This is the Mac/Linux script." "On Windows, double-click Setup.cmd instead."
info "Computer: $OS ${CHIP:+($CHIP)}"

step "1/5  Checking Docker"
ensure_docker
check_docker_memory

step "2/5  Checking network ports"
check_app_ports
choose_mysql_port

step "3/5  Checking Java (optional — only for the desktop app)"
if command -v java >/dev/null 2>&1 && java -version 2>&1 | grep -Eq 'version "(1[7-9]|[2-9][0-9])'; then
  ok "Java is installed ($(java -version 2>&1 | head -1))"
else
  warn "Java 17 or newer is not installed. That's fine — the web app does not need it."
  info "Only needed for the optional desktop app (\"Open desktop app\"). To install it later:"
  if [ "$OS" = "macos" ]; then
    info "  • Download the macOS installer (.pkg) from https://adoptium.net/temurin/releases/?version=17"
    command -v brew >/dev/null 2>&1 && info "  • or run: brew install --cask temurin@17"
  else
    info "  • https://adoptium.net/temurin/releases/?version=17 (or your package manager, e.g. openjdk-17-jdk)"
  fi
fi

step "4/5  Downloading and building the app (first time: 5-10 minutes)"
info "Docker downloads the database and builds the app. Later starts are much faster."
docker compose pull --quiet mysql || die "Could not download the database image." "Check your internet connection and try again."
docker compose --profile tests build || die "Building the app failed." "Check your internet connection and try again. If it keeps failing, send the text above to whoever supports you."
ok "App is built"

step "5/5  Done"
ok "Setup complete!"
info "Next: double-click ${BOLD}Start${RESET} (or ${BOLD}Start with test data${RESET} for a fuller demo)."
