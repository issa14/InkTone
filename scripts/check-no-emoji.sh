#!/usr/bin/env bash
# Blueprint §12.5 / §14.8 (K12) : aucun emoji dans le code de production.
set -euo pipefail

MATCHES=$(grep -rlP '[\x{1F300}-\x{1FAFF}\x{2600}-\x{27BF}]' \
    --include='*.kt' \
    -- */src/main 2>/dev/null || true)

if [[ -n "$MATCHES" ]]; then
    echo "Emoji(s) détecté(s) dans le code de production :"
    echo "$MATCHES"
    exit 1
fi
echo "OK : aucun emoji dans les sources de production."
