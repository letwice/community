#!/usr/bin/env bash
# Shared helpers for the macOS/Linux scripts (setup.sh, start.sh, stop.sh, desktop.sh).
# Written for people who have never used a terminal: every problem comes with plain instructions.

set -u

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$PROJECT_ROOT" || exit 1

APP_PORTS=(9600 9601 9602 9603 9700)
WEB_URL="http://localhost:9700"

if [ -t 1 ]; then
  BOLD=$'\033[1m'; GREEN=$'\033[32m'; YELLOW=$'\033[33m'; RED=$'\033[31m'; BLUE=$'\033[34m'; RESET=$'\033[0m'
else
  BOLD=""; GREEN=""; YELLOW=""; RED=""; BLUE=""; RESET=""
fi

title()   { printf '\n%s%s%s\n' "$BOLD" "$1" "$RESET"; }
step()    { printf '\n%s▶ %s%s\n' "$BLUE$BOLD" "$1" "$RESET"; }
ok()      { printf '  %s✔%s %s\n' "$GREEN" "$RESET" "$1"; }
warn()    { printf '  %s!%s %s\n' "$YELLOW" "$RESET" "$1"; }
fail()    { printf '  %s✖%s %s\n' "$RED" "$RESET" "$1"; }
info()    { printf '    %s\n' "$1"; }

# Prints a message and stops the script. Launcher windows stay open so the message can be read.
die() {
  printf '\n%s%s%s\n' "$RED$BOLD" "$1" "$RESET"
  [ $# -gt 1 ] && printf '%s\n' "${@:2}"
  exit 1
}

ask_yes_no() {  # ask_yes_no "Question?" -> returns 0 for yes
  local answer
  printf '\n  %s [y/N] ' "$1"
  read -r answer
  [[ "$answer" =~ ^[Yy] ]]
}

detect_os() {
  case "$(uname -s)" in
    Darwin) OS="macos"; if [ "$(uname -m)" = "arm64" ]; then CHIP="Apple chip (M1/M2/M3/M4)"; else CHIP="Intel chip"; fi ;;
    Linux)  OS="linux"; CHIP="$(uname -m)" ;;
    MINGW*|MSYS*|CYGWIN*) OS="windows-bash"; CHIP="" ;;
    *)      OS="unknown"; CHIP="" ;;
  esac
}

# ------------------------------------------------------------------------------------------- Docker

docker_install_help() {
  fail "Docker Desktop is not installed. The app runs inside Docker, so it is required."
  case "$OS" in
    macos)
      if [ "$(uname -m)" = "arm64" ]; then
        local url="https://desktop.docker.com/mac/main/arm64/Docker.dmg"
      else
        local url="https://desktop.docker.com/mac/main/amd64/Docker.dmg"
      fi
      cat <<MSG

    How to install Docker Desktop on this Mac ($CHIP):
      1. Download it: $url
      2. Open the downloaded "Docker.dmg" and drag the Docker whale into Applications.
      3. Open Docker from your Applications folder and accept the terms.
         (A personal account is not needed — you can choose "Skip" / "Continue without signing in".)
      4. Wait until the whale icon in the top menu bar stops animating.
      5. Double-click "Setup" again.
MSG
      if command -v brew >/dev/null 2>&1; then
        if ask_yes_no "Homebrew is installed. Install Docker Desktop for you now with Homebrew?"; then
          brew install --cask docker && open -a Docker && info "Docker Desktop is starting. Run Setup again once the whale icon is steady."
        fi
      else
        open "$url" 2>/dev/null || true
      fi
      ;;
    linux)
      cat <<MSG

    How to install Docker on Linux:
      Follow https://docs.docker.com/desktop/setup/install/linux/ (Docker Desktop)
      or https://docs.docker.com/engine/install/ (Docker Engine + the "docker compose" plugin).
      Then run ./scripts/setup.sh again.
MSG
      ;;
    *)
      info "Install Docker Desktop from https://www.docker.com/products/docker-desktop/ and run Setup again."
      ;;
  esac
  exit 1
}

# Makes sure Docker is installed and running; starts Docker Desktop if it is closed.
ensure_docker() {
  if ! command -v docker >/dev/null 2>&1; then
    docker_install_help
  fi
  ok "Docker is installed ($(docker --version 2>/dev/null | sed 's/,.*//'))"

  if ! docker info >/dev/null 2>&1; then
    warn "Docker Desktop is not running — starting it now…"
    if [ "$OS" = "macos" ]; then
      open -a Docker 2>/dev/null || die "Could not start Docker Desktop." "Open Docker from your Applications folder, wait for the whale icon to settle, then try again."
    else
      info "Please start Docker (Docker Desktop, or: sudo systemctl start docker)."
    fi
    local waited=0
    printf '    Waiting for Docker to be ready'
    until docker info >/dev/null 2>&1; do
      if [ $waited -ge 180 ]; then
        printf '\n'
        die "Docker did not start within 3 minutes." \
            "Open Docker Desktop yourself. If it asks you to accept terms or finish an update, do that first, then try again."
      fi
      printf '.'; sleep 3; waited=$((waited + 3))
    done
    printf '\n'
  fi
  ok "Docker is running"

  if ! docker compose version >/dev/null 2>&1; then
    die "Docker Compose v2 is missing." "Update Docker Desktop to the latest version (Docker menu → Check for updates), then try again."
  fi
  ok "Docker Compose is available"
}

check_docker_memory() {
  local bytes
  bytes="$(docker info --format '{{.MemTotal}}' 2>/dev/null || echo 0)"
  local gb=$((bytes / 1024 / 1024 / 1024))
  if [ "$bytes" -gt 0 ] && [ "$bytes" -lt 3758096384 ]; then
    warn "Docker only has about ${gb} GB of memory. The app needs at least 4 GB."
    info "Docker Desktop → Settings (gear icon) → Resources → Memory: set 4 GB or more, then Apply & Restart."
  else
    ok "Docker has enough memory (${gb} GB)"
  fi
}

# ------------------------------------------------------------------------------------------- Ports

port_in_use() { lsof -nP -iTCP:"$1" -sTCP:LISTEN >/dev/null 2>&1; }

app_is_running() { [ -n "$(docker compose ps --status running -q 2>/dev/null)" ]; }

check_app_ports() {
  if app_is_running; then
    ok "The app is already running — its ports are in use by the app itself"
    return
  fi
  local busy=()
  for port in "${APP_PORTS[@]}"; do
    port_in_use "$port" && busy+=("$port")
  done
  if [ ${#busy[@]} -gt 0 ]; then
    die "These ports are already used by another program: ${busy[*]}" \
        "Close the other program (or restart the computer) and try again. To see what is using a port, run: lsof -i :${busy[0]}"
  fi
  ok "Ports ${APP_PORTS[*]} are free"
}

# Picks a free port for the database and remembers it in .env (read automatically by docker compose).
choose_mysql_port() {
  if [ -f .env ] && grep -q '^MYSQL_HOST_PORT=' .env; then
    ok "Database port: $(grep '^MYSQL_HOST_PORT=' .env | cut -d= -f2) (from .env)"
    return
  fi
  local port
  for port in 3306 3307 3308 3309 3310 3311 3312; do
    if ! port_in_use "$port"; then
      echo "MYSQL_HOST_PORT=$port" >> .env
      ok "Database port: $port (saved in .env)"
      return
    fi
  done
  die "Could not find a free port for the database (tried 3306-3312)."
}

# ------------------------------------------------------------------------------------------- Waiting

http_status() { curl -s -o /dev/null -m 5 -w '%{http_code}' "$1" 2>/dev/null || echo 000; }

# wait_for "Name" url -> waits until the URL answers (200, or 401 for protected endpoints)
wait_for() {
  local name="$1" url="$2" waited=0 status
  printf '    %-24s' "$name"
  while :; do
    status="$(http_status "$url")"
    if [ "$status" = "200" ] || [ "$status" = "401" ]; then
      printf '%s✔ ready%s\n' "$GREEN" "$RESET"
      return 0
    fi
    if [ $waited -ge 300 ]; then
      printf '%s✖ not responding%s\n' "$RED" "$RESET"
      return 1
    fi
    printf '.'; sleep 3; waited=$((waited + 3))
  done
}

wait_for_app() {
  local failed=0
  wait_for "Sign-in service"        "http://localhost:9600/auth/.well-known/jwks.json"           || failed=1
  wait_for "User service"           "http://localhost:9601/service-user/system/health"           || failed=1
  wait_for "Notification service"   "http://localhost:9602/service-notification/system/health"   || failed=1
  wait_for "Product service"        "http://localhost:9603/service-product/system/health"        || failed=1
  wait_for "Web app"                "$WEB_URL"                                                   || failed=1
  if [ $failed -ne 0 ]; then
    die "Some parts of the app did not start." \
        "Try Stop, then Start again. If it keeps failing, save the log for whoever supports you:" \
        "  docker compose logs > community-store-log.txt"
  fi
}

print_accounts() {
  cat <<MSG

  ${BOLD}Open the app:${RESET} $WEB_URL

  Demo accounts (password for all: ${BOLD}Password123!${RESET})
    Student ............ student@university.com
    Vendor ............. vendor@community.store
    Community leader ... leader@community.store
    Faculty ............ faculty@university.com
    Admin .............. admin@community.store

  When you're finished, double-click ${BOLD}Stop${RESET} (your data is kept).
MSG
}

open_browser() {
  case "$OS" in
    macos) open "$1" 2>/dev/null || true ;;
    linux) xdg-open "$1" >/dev/null 2>&1 || true ;;
  esac
}
