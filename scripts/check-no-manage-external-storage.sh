#!/usr/bin/env bash
# Blueprint §10.3 / §14.8 (K5) : MANAGE_EXTERNAL_STORAGE est interdit.
set -euo pipefail

MATCHES=$(grep -rl "MANAGE_EXTERNAL_STORAGE" \
    --include='AndroidManifest.xml' --include='*.kt' \
    -- */src 2>/dev/null || true)

if [[ -n "$MATCHES" ]]; then
    echo "MANAGE_EXTERNAL_STORAGE détecté (interdit — ADR-015) :"
    echo "$MATCHES"
    exit 1
fi
echo "OK : MANAGE_EXTERNAL_STORAGE absent."
