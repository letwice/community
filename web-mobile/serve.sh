#!/usr/bin/env bash
# Serves the mobile SPA on http://localhost:9700 (the origin allowed by the backend CORS config).
exec python3 "$(dirname "$0")/serve.py"
