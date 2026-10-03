#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
REV="32e9e3a93b205f786929697446ae669cf0a84579"
BASE="https://huggingface.co/Cactus-Compute/needle2/resolve/${REV}"

mkdir -p "${ROOT}/app/src/main/assets" "${ROOT}/app/src/main/cpp/third_party"

curl -fL --retry 5 --retry-all-errors -o "${ROOT}/app/src/main/assets/needle2.cact" "${BASE}/needle2.cact?download=true"
curl -fL --retry 5 --retry-all-errors -o "${ROOT}/app/src/main/cpp/third_party/libneedle.a" "${BASE}/android-arm64/libneedle.a?download=true"
curl -fL --retry 5 --retry-all-errors -o "${ROOT}/app/src/main/cpp/third_party/needle.h" "${BASE}/android-arm64/needle.h?download=true"

test "$(wc -c < "${ROOT}/app/src/main/assets/needle2.cact")" -gt 10000000
test "$(wc -c < "${ROOT}/app/src/main/cpp/third_party/libneedle.a")" -gt 1000000

echo "Needle 2 assets fetched at ${REV}"
