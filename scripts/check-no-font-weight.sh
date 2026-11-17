#!/usr/bin/env bash
# Audit UI P1-18 : le poids du texte des écrans vient de l'échelle (Type.kt)
# ou de `TextStyle.emphasized()`, jamais d'un `fontWeight =` posé écran par
# écran. Exceptions : le rendu du texte des livres (reader/rendering,
# reader/pagination) et toute ligne marquée `// texte du livre`.
set -euo pipefail

MATCHES=$(grep -rnE 'fontWeight\s*=' --include='*.kt' feature/*/src/main 2>/dev/null \
    | grep -vE 'feature/reader/src/main/kotlin/com/inktone/feature/reader/(rendering|pagination)/' \
    | grep -v '// texte du livre' || true)

if [[ -n "$MATCHES" ]]; then
    echo "Poids de texte réglé hors de l'échelle typographique :"
    echo "$MATCHES"
    echo "Utiliser le style de l'échelle, ou TextStyle.emphasized() pour une mise en relief."
    exit 1
fi
echo "OK : aucun fontWeight posé dans les écrans."
