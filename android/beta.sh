#!/usr/bin/env bash
# Runs a fastlane lane capped at 5 GB RAM and low CPU priority. Usage: ./beta.sh [lane] [options]
set -euo pipefail
cd "$(dirname "$0")"
[ $# -eq 0 ] && set -- beta
exec systemd-run --user --scope -q -p MemoryMax=5G -p MemorySwapMax=0 --working-directory="$PWD" \
  nice -n 15 bundle exec fastlane "$@"
