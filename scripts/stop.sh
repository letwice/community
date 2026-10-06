#!/usr/bin/env bash
# Stops the app. Your data is kept. Use --reset to also delete all data (fresh demo next start).
source "$(dirname "$0")/common.sh"
detect_os

title "Community Store — stop"
if ! command -v docker >/dev/null 2>&1 || ! docker info >/dev/null 2>&1; then
  ok "Docker isn't running, so the app is already stopped."
  exit 0
fi
if [ "${1:-}" = "--reset" ]; then
  ask_yes_no "Delete ALL app data (users, orders, events) and stop?" || exit 0
  docker compose --profile tests down -v --remove-orphans && ok "Stopped and reset. The next Start begins with fresh demo data."
else
  docker compose --profile tests stop && ok "Stopped. Your data is kept for next time."
fi
info "You can also quit Docker Desktop now if you like."
