#!/usr/bin/env bash
# Starts the whole app. Options:
#   --with-test-data   reset to a fresh demo database, then run the automated API tests, which also add test data
#   --yes              don't ask for confirmation before resetting
#   --no-browser       don't open the web browser at the end
source "$(dirname "$0")/common.sh"
detect_os

WITH_TEST_DATA=0; ASSUME_YES=0; OPEN_BROWSER=1
for arg in "$@"; do
  case "$arg" in
    --with-test-data) WITH_TEST_DATA=1 ;;
    --yes|-y)         ASSUME_YES=1 ;;
    --no-browser)     OPEN_BROWSER=0 ;;
    *) die "Unknown option: $arg" "Usage: scripts/start.sh [--with-test-data] [--yes] [--no-browser]" ;;
  esac
done

title "Community Store — start"
[ "$OS" = "windows-bash" ] && die "This is the Mac/Linux script." "On Windows, double-click Start.cmd instead."

step "Checking Docker"
ensure_docker
check_app_ports
choose_mysql_port

if [ $WITH_TEST_DATA -eq 1 ]; then
  step "Preparing a fresh demo database"
  warn "Starting with test data RESETS the app: everything created so far (users, orders, events…) is deleted."
  if [ $ASSUME_YES -eq 0 ] && ! ask_yes_no "Reset and load test data?"; then
    info "Nothing was changed. To start without resetting, double-click Start instead."
    exit 0
  fi
  docker compose --profile tests down -v --remove-orphans >/dev/null 2>&1
  ok "Database reset"
fi

step "Starting the app (the first start can take a few minutes)"
docker compose up -d --build --remove-orphans || die "The app could not be started." "Make sure Docker Desktop is running, then try again. Did you run Setup first?"

step "Waiting for everything to be ready"
wait_for_app

if [ $WITH_TEST_DATA -eq 1 ]; then
  step "Running the automated tests (this also creates test data)"
  if docker compose --profile tests run --rm --build api-tests; then
    ok "All automated tests passed — test data is loaded"
  else
    warn "Some automated tests failed (details above). The app is still running and usable."
  fi
fi

title "${GREEN}✔ Community Store is running${RESET}"
print_accounts
[ $OPEN_BROWSER -eq 1 ] && open_browser "$WEB_URL"
exit 0
