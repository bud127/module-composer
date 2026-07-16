#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"

curl -fsS "${BASE_URL}/actuator/health"
echo
curl -fsS "${BASE_URL}/api/server"
echo
curl -fsS "${BASE_URL}/api/payment/health"
echo
curl -fsS "${BASE_URL}/api/notification/health"
echo
