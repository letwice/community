#!/bin/bash
# macOS: double-click to run. (If macOS blocks it the first time: right-click → Open → Open.)
cd "$(dirname "$0")" || exit 1
./scripts/desktop.sh
echo
read -n 1 -s -r -p "Press any key to close this window…"
echo
