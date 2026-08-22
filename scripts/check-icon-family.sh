#!/usr/bin/env bash
# Une seule famille d'icônes (tâche 2c.3) : Material Symbols Rounded, via
# `AppSymbol` + `AppIcon`.
#
# La famille Material Icons (`androidx.compose.material.icons`) a longtemps
# coexisté sous l'alias `AppIcons`, déclaré « legacy, supprimé en 2c.3 » et
# resté en place à mi-migration : les MÊMES concepts s'y dessinaient depuis
# deux jeux différents — `AppSymbol.Play` et `AppIcons.Play` cohabitaient
# jusque dans le panneau de contrôle du Lecteur. Ce script empêche la
# famille legacy de revenir par une importation distraite.
set -euo pipefail

MATCHES=$(grep -rln 'androidx\.compose\.material\.icons\|\bAppIcons\b' \
    --include='*.kt' \
    -- */src/main */*/src/main 2>/dev/null || true)

if [[ -n "$MATCHES" ]]; then
    echo "Famille d'icônes interdite (Material Icons / AppIcons) détectée :"
    echo "$MATCHES"
    echo
    echo "Utiliser AppSymbol + AppIcon (Material Symbols Rounded)."
    echo "Symbole manquant : ajouter une entrée à AppSymbol plutôt qu'un import Material Icons."
    exit 1
fi
echo "OK : une seule famille d'icônes (AppSymbol / Material Symbols Rounded)."
