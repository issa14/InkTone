#!/usr/bin/env bash
# Audit UI P2-1 : les espacements de la grille (4, 8, 12, 16, 24, 32 dp) passent
# par InkToneSpacing, jamais en littéral dans un padding, un spacedBy ou un
# Spacer. Les valeurs hors grille (tailles d'icônes, 20 dp…) ne sont pas visées.
set -euo pipefail

GRID='(4|8|12|16|24|32)\.dp\b'
# Le littéral suit la parenthèse ou un caractère qui n'est ni chiffre ni point
# (« 0.8.dp » et « 18.dp » ne sont pas des valeurs de la grille).
MATCHES=$(grep -rnE "(padding|PaddingValues)\(([^()]*[^0-9.()])?$GRID|spacedBy\($GRID|Spacer\(Modifier\.(height|width)\($GRID\)" \
    --include='*.kt' feature/*/src/main core/ui/src/main 2>/dev/null || true)

if [[ -n "$MATCHES" ]]; then
    echo "Espacement de la grille écrit en littéral :"
    echo "$MATCHES"
    echo "Utiliser InkToneSpacing (xs 4, sm 8, md 12, lg 16, xl 24, xxl 32)."
    exit 1
fi
echo "OK : aucun espacement de la grille en littéral."
