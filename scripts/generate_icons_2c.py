#!/usr/bin/env python3
"""
Génère les VectorDrawables Material Symbols Rounded pour le lot 2c.
Fetch depuis le CDN Google Fonts → parsing du path SVG → VectorDrawable XML.
"""

import urllib.request
import xml.etree.ElementTree as ET
import os
import re
import math

CDN = "https://fonts.gstatic.com/s/i/short-term/release/materialsymbolsrounded"
OUT_DIR = "core/designsystem/src/main/res/drawable"

# --- Mapping complet des icônes du lot 2c ---
# (nom_logique, nom_material_symbols, a_etat_rempli, auto_mirror)
ICONS = [
    # Navigation — à état (nav sélectionnable)
    ("symbol_reading", "menu_book", True, True),       # Reading
    ("symbol_recents", "history", True, False),         # Recents
    ("symbol_stats", "bar_chart", True, False),         # Stats
    ("symbol_sync", "sync", True, False),               # Sync
    ("symbol_theme", "palette", True, False),           # Theme/Appearance
    ("symbol_settings", "settings", True, False),       # Settings

    # Bascules — à état
    ("symbol_bookmark", "bookmark", True, False),
    ("symbol_favorite", "favorite", True, False),
    ("symbol_pin", "push_pin", True, False),

    # TTS — à état
    ("symbol_speaking", "volume_up", True, True),

    # Modes segmentés — à état
    ("symbol_reading_mode_paged", "view_day", True, False),
    ("symbol_reading_mode_scroll", "import_contacts", True, False),
    ("symbol_view_grid", "widgets", True, False),
    ("symbol_view_list", "view_list", True, True),
    ("symbol_cover_only", "photo_library", True, False),

    # Statuts — mono, glyphe plein au repos
    ("symbol_error", "error", False, False),
    ("symbol_warning", "warning", False, False),
    ("symbol_success", "check_circle", False, False),

    # Mono — contour au repos
    ("symbol_info", "info", False, False),
    ("symbol_search", "search", False, False),
    ("symbol_delete", "delete", False, False),
    ("symbol_edit", "edit", False, False),
    ("symbol_add", "add", False, False),
    ("symbol_remove", "remove", False, False),
    ("symbol_play", "play_arrow", False, False),
    ("symbol_pause", "pause", False, False),
    ("symbol_back", "arrow_back", False, True),
    ("symbol_toc", "list", False, True),
    ("symbol_chapter_previous", "navigate_before", False, True),
    ("symbol_chapter_next", "navigate_next", False, True),
    ("symbol_sentence_previous", "skip_previous", False, False),
    ("symbol_sentence_next", "skip_next", False, False),
    ("symbol_refresh", "refresh", False, False),
    ("symbol_filter", "filter_list", False, False),
    ("symbol_chevron_down", "keyboard_arrow_down", False, False),
    ("symbol_more_actions", "more_vert", False, False),
    ("symbol_copy", "content_copy", False, False),
    ("symbol_highlight", "highlight", False, False),
    ("symbol_brightness", "light_mode", False, False),
    ("symbol_sleep_timer", "bedtime", False, False),
    ("symbol_format_size", "format_size", False, False),
    ("symbol_device", "smartphone", False, False),
    ("symbol_cloud_connected", "cloud_done", False, False),
    ("symbol_cloud_disconnected", "cloud_off", False, False),
    ("symbol_visual_reading", "visibility", False, False),
    ("symbol_tts_listening", "headphones", False, False),
    ("symbol_streak", "local_fire_department", False, False),
    ("symbol_mic", "mic", False, False),
    ("symbol_data", "save", False, False),
    ("symbol_presets", "bolt", False, False),
    ("symbol_note", "edit_note", False, False),
    ("symbol_accessibility", "accessibility", False, False),
    ("symbol_pronunciation", "record_voice_over", False, False),
    ("symbol_add_circle", "add_circle", False, False),
]

# --- Path parser SVG → VectorDrawable ---

def parse_svg_path_to_absolute(path_d):
    """
    Convertit un path SVG relatif/absolu en commandes absolues normalisées.
    Retourne une liste de tuples (cmd, coords[]).
    ViewBox Material Symbols = 0 -960 960 960 → on translate Y de +960
    et on scale tout par 24/960 = 1/40 = 0.025.
    """
    scale = 24.0 / 960.0
    translate_y = 960.0  # pour passer de y∈[-960,0] à y∈[0,960]

    tokens = re.findall(r'[a-df-zA-DF-Z]|[-+]?\d*\.?\d+(?:[eE][-+]?\d+)?', path_d)
    commands = []
    i = 0
    current = [0.0, 0.0]
    first_point = [0.0, 0.0]

    while i < len(tokens):
        cmd_str = tokens[i]
        if re.match(r'^[a-df-zA-DF-Z]$', cmd_str):
            cmd = cmd_str
            i += 1
        else:
            cmd = 'L'  # continuation implicite
        # Lire les coordonnées selon la commande
        nums = []
        while i < len(tokens) and re.match(r'^[-+]?\d*\.?\d+(?:[eE][-+]?\d+)?$', tokens[i]):
            nums.append(float(tokens[i]))
            i += 1

        upper = cmd.upper()
        is_relative = cmd.islower()

        if upper == 'Z':
            current = first_point[:]
            commands.append(('Z', []))
            continue

        j = 0
        while j < len(nums):
            if upper == 'M':
                x, y = nums[j], nums[j+1]
                if is_relative:
                    current[0] += x; current[1] += y
                else:
                    current[0] = x; current[1] = y
                first_point = current[:]
                commands.append(('M', [round(current[0], 2), round(current[1], 2)]))
                j += 2
                # Si M a plus de 2 coords, les paires suivantes sont des L
                upper = 'L'; is_relative = cmd.islower()  # hérite du mode rel/abs
            elif upper == 'L':
                x, y = nums[j], nums[j+1]
                if is_relative:
                    current[0] += x; current[1] += y
                else:
                    current[0] = x; current[1] = y
                commands.append(('L', [round(current[0], 2), round(current[1], 2)]))
                j += 2
            elif upper == 'H':
                x = nums[j]
                if is_relative:
                    current[0] += x
                else:
                    current[0] = x
                commands.append(('L', [round(current[0], 2), round(current[1], 2)]))
                j += 1
            elif upper == 'V':
                y = nums[j]
                if is_relative:
                    current[1] += y
                else:
                    current[1] = y
                commands.append(('L', [round(current[0], 2), round(current[1], 2)]))
                j += 1
            elif upper == 'C':
                # courbe cubique : x1 y1 x2 y2 x y
                pts = nums[j:j+6]
                if is_relative:
                    for k in range(0, 6, 2):
                        current[0] += pts[k]; current[1] += pts[k+1]
                        pts[k] = current[0]; pts[k+1] = current[1]
                else:
                    current[0] = pts[4]; current[1] = pts[5]
                commands.append(('C', [round(v, 2) for v in pts]))
                j += 6
            elif upper == 'Q':
                pts = nums[j:j+4]
                if is_relative:
                    for k in range(0, 4, 2):
                        current[0] += pts[k]; current[1] += pts[k+1]
                        pts[k] = current[0]; pts[k+1] = current[1]
                else:
                    current[0] = pts[2]; current[1] = pts[3]
                commands.append(('Q', [round(v, 2) for v in pts]))
                j += 4
            else:
                # skip unknown
                j = len(nums)

    # Appliquer translation Y + scale
    result = []
    for cmd, coords in commands:
        if cmd == 'Z':
            result.append('Z')
        else:
            scaled = []
            for k in range(0, len(coords), 2):
                sx = round(coords[k] * scale, 3)
                sy = round((coords[k+1] + translate_y) * scale, 3)
                scaled.extend([sx, sy])
            # Tronquer les zéros inutiles
            formatted = []
            for v in scaled:
                if v == int(v):
                    formatted.append(str(int(v)))
                else:
                    formatted.append(f"{v:.3f}".rstrip('0').rstrip('.'))
            result.append(f"{cmd}{','.join(formatted)}")
    return ' '.join(result)


def fetch_svg(icon_name, style):
    """Récupère le SVG depuis le CDN Google Fonts."""
    url = f"{CDN}/{icon_name}/{style}/24px.svg"
    req = urllib.request.Request(url, headers={'User-Agent': 'InkTone/2c-icon-generator'})
    with urllib.request.urlopen(req) as resp:
        return resp.read().decode('utf-8')


def svg_to_vector(svg_content, auto_mirror=False):
    """Convertit un SVG Material Symbols en VectorDrawable Android."""
    root = ET.fromstring(svg_content)
    ns = {'svg': 'http://www.w3.org/2000/svg'}
    paths = root.findall('.//svg:path', ns)

    path_datas = []
    for p in paths:
        d = p.get('d', '')
        if d:
            path_datas.append(parse_svg_path_to_absolute(d))

    combined = ' '.join(path_datas)
    am = 'android:autoMirrored="true"\n    ' if auto_mirror else ''

    # Pour les statuts (Error, Warning, Success), on utilise FILL 1 en default
    # → le SVG fill1 a déjà les formes pleines, pas besoin de changer le fillColor
    xml = f'''<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24"
    {am}>
    <path
        android:fillColor="#FF000000"
        android:pathData="{combined}" />
</vector>'''
    return xml


def generate():
    os.makedirs(OUT_DIR, exist_ok=True)
    manifest_rows = []

    for logical, material, has_state, auto_mirror in ICONS:
        base = logical

        # Statuts → le default est déjà plein (fill1), pas de _outlined
        is_status = logical in ('symbol_error', 'symbol_warning', 'symbol_success')

        if has_state:
            # Variante FILL 0 (outlined) — repos
            svg0 = fetch_svg(material, 'default')
            fname0 = f"ic_{base}_outlined.xml"
            vd0 = svg_to_vector(svg0, auto_mirror=auto_mirror)
            with open(os.path.join(OUT_DIR, fname0), 'w') as f:
                f.write(vd0)

            # Variante FILL 1 (filled) — sélectionné
            svg1 = fetch_svg(material, 'fill1')
            fname1 = f"ic_{base}_filled.xml"
            vd1 = svg_to_vector(svg1, auto_mirror=auto_mirror)
            with open(os.path.join(OUT_DIR, fname1), 'w') as f:
                f.write(vd1)

            manifest_rows.append((logical, material, fname0, fname1, 'Oui' if auto_mirror else 'Non'))
            print(f"  ✓ {logical} → {fname0} + {fname1}")
        else:
            # Mono — seul le variant défaut
            style = 'fill1' if is_status else 'default'
            svg = fetch_svg(material, style)
            fname = f"ic_{base}.xml"
            vd = svg_to_vector(svg, auto_mirror=auto_mirror)
            with open(os.path.join(OUT_DIR, fname), 'w') as f:
                f.write(vd)

            manifest_rows.append((logical, material, fname, '—', 'Oui' if auto_mirror else 'Non'))
            print(f"  ✓ {logical} → {fname}")

    # Générer le manifeste
    manifest = """# Manifeste des assets — Lot 2c Iconographie

Généré automatiquement depuis Material Symbols Rounded (Google Fonts CDN).
Paramètres : `wght 400`, `GRAD 0`, `opsz 24`, grille 24dp.

| Nom logique | Symbole Google | Fichier (FILL 0 / repos) | Fichier (FILL 1 / actif) | AutoMirrored |
|:---|:---|:---|:---|:---|
"""
    for logical, material, f0, f1, am in manifest_rows:
        manifest += f"| `{logical}` | `{material}` | `{f0}` | `{f1}` | {am} |\n"

    manifest += f"\n**Total** : {len(manifest_rows)} icônes générées.\n"

    manifest_path = "docs/execution/ASSETS_ICONES_2C.md"
    with open(manifest_path, 'w') as f:
        f.write(manifest)
    print(f"\n📄 Manifeste : {manifest_path}")
    print(f"📁 Assets    : {OUT_DIR}/ ({len(manifest_rows)} fichiers)")


if __name__ == '__main__':
    generate()
