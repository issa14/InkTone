#!/usr/bin/env bash
# Mesure de fluidité du Lecteur sur appareil réel (Blueprint §11.2 :
# « Défilement : 60 fps ; aucune frame > 32 ms »).
#
# Rejoue un scénario de gestes via `adb shell input`, puis relève
# `dumpsys gfxinfo` (remis à zéro juste avant) et, si l'app est instrumentée,
# le nombre de recompositions par zone (tag logcat `RC`).
#
# Prérequis : l'app est installée, le Lecteur est OUVERT au premier plan sur
# le livre voulu, dans le mode voulu (défilement ou paginé). Le script ne
# navigue pas dans l'app : il ne fait que rejouer des gestes.
#
# Scénarios :
#   scroll  N gestes de défilement vertical (mode défilement)
#   page    N gestes de tournage de page horizontal (mode paginé)
#   idle    D secondes sans geste (lecture audio, minuteur armé...)
#
# Usage : scripts/measure-reader-frames.sh <scroll|page|idle> [N|D] [libellé]
# Résultats ajoutés à build/reader-frames.tsv (une ligne par mesure).
#
# Mesuré sur une version debug : les valeurs absolues sont pessimistes
# (pas de compilation anticipée, pas de R8), seule la comparaison
# avant/après d'une même version est significative.
set -euo pipefail

PKG=com.inktone.app
SCENARIO=${1:?scénario requis : scroll, page ou idle}
AMOUNT=${2:-20}
LABEL=${3:-sans-libelle}
OUT=build/reader-frames.tsv

read -r WIDTH HEIGHT < <(adb shell wm size | tr -d '\r' | sed -n 's/.*: \([0-9]*\)x\([0-9]*\)/\1 \2/p' | tail -1)

adb shell dumpsys gfxinfo "$PKG" reset > /dev/null
adb logcat -c

case "$SCENARIO" in
    scroll)
        # Du bas vers le haut, au centre, en 300 ms : un geste de lecture,
        # pas un lancer. Pause entre deux gestes pour laisser l'inertie finir.
        for ((i = 0; i < AMOUNT; i++)); do
            adb shell input swipe $((WIDTH / 2)) $((HEIGHT * 70 / 100)) $((WIDTH / 2)) $((HEIGHT * 30 / 100)) 300
            sleep 0.8
        done
        ;;
    page)
        for ((i = 0; i < AMOUNT; i++)); do
            adb shell input swipe $((WIDTH * 85 / 100)) $((HEIGHT / 2)) $((WIDTH * 15 / 100)) $((HEIGHT / 2)) 200
            sleep 1.0
        done
        ;;
    idle)
        sleep "$AMOUNT"
        ;;
    *)
        echo "Scénario inconnu : $SCENARIO" >&2
        exit 1
        ;;
esac

GFX=$(adb shell dumpsys gfxinfo "$PKG" | tr -d '\r')
field() { echo "$GFX" | sed -n "s/^$1: *\([0-9]*\).*/\1/p" | head -1; }

TOTAL=$(field "Total frames rendered")
JANKY=$(field "Janky frames")
P50=$(field "50th percentile")
P90=$(field "90th percentile")
P99=$(field "99th percentile")
SLOW_UI=$(field "Number Slow UI thread")

# Images au-delà d'un seuil, depuis l'histogramme CPU (première ligne
# HISTOGRAM, pas celle du GPU) : chaque seau « Nms=compte » compte les
# images d'au moins N ms.
HISTO=$(echo "$GFX" | grep -m1 '^HISTOGRAM:' | sed 's/^HISTOGRAM: //')
over() {
    echo "$HISTO" | tr ' ' '\n' | awk -F'ms=' -v t="$1" '$1 + 0 > t { s += $2 } END { print s + 0 }'
}
OVER16=$(over 16)
OVER32=$(over 32)

# Recompositions par zone (instrumentation temporaire, tag `RC`) : vide si
# l'app n'est pas instrumentée.
RECOMP=$(adb logcat -d -s RC:I | sed -n 's/.*RC *: *//p' | sort | uniq -c | sort -rn | awk '{ printf "%s=%s ", $2, $1 }')

mkdir -p "$(dirname "$OUT")"
if [[ ! -f "$OUT" ]]; then
    printf 'date\tlibelle\tscenario\tquantite\timages\tjank\t>16ms\t>32ms\tp50\tp90\tp99\tui_lent\trecompositions\n' > "$OUT"
fi
printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n' \
    "$(date +%F_%T)" "$LABEL" "$SCENARIO" "$AMOUNT" "$TOTAL" "$JANKY" "$OVER16" "$OVER32" \
    "$P50" "$P90" "$P99" "$SLOW_UI" "$RECOMP" >> "$OUT"

echo "Scénario $SCENARIO ($AMOUNT) — $LABEL"
echo "  images : $TOTAL   jank : $JANKY   >16 ms : $OVER16   >32 ms : $OVER32"
echo "  p50 / p90 / p99 : ${P50} / ${P90} / ${P99} ms   UI lent : $SLOW_UI"
[[ -n "$RECOMP" ]] && echo "  recompositions : $RECOMP"
echo "  (ajouté à $OUT)"
