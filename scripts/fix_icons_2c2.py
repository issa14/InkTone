#!/usr/bin/env python3
"""Correcteur 2c.2 — imports AppIcon, tokens legacy, fusions conditionnelles."""

import re
import os
import glob

def fix_file(filepath):
    with open(filepath, 'r') as f:
        content = f.read()
    original = content

    # 1. Ajouter import AppIcon si AppIcons est importé mais AppIcon non
    if 'import com.inktone.core.designsystem.AppIcons' in content \
       and 'import com.inktone.core.designsystem.AppIcon' not in content:
        content = content.replace(
            'import com.inktone.core.designsystem.AppIcons',
            'import com.inktone.core.designsystem.AppIcon\nimport com.inktone.core.designsystem.AppIcons'
        )

    # 2. Fusion Favorite/FavoriteBorder
    content = re.sub(
        r'if \(publication\.isFavorite\) AppIcons\.Favorite else AppIcons\.FavoriteBorder',
        'AppIcons.Favorite, selected = publication.isFavorite',
        content
    )
    # Cas générique: if (x) AppIcons.Favorite else AppIcons.FavoriteBorder
    content = re.sub(
        r'if \(([^)]+)\) AppIcons\.Favorite else AppIcons\.FavoriteBorder',
        r'AppIcons.Favorite, selected = \1',
        content
    )

    # 3. Fusion Pin/PinOutlined
    content = re.sub(
        r'if \(publication\.isPinned\) AppIcons\.Pin else AppIcons\.PinOutlined',
        'AppIcons.Pin, selected = publication.isPinned',
        content
    )
    content = re.sub(
        r'if \(item\.isPinned\) AppIcons\.Pin else AppIcons\.PinOutlined',
        'AppIcons.Pin, selected = item.isPinned',
        content
    )
    content = re.sub(
        r'if \(([^)]+)\) AppIcons\.Pin else AppIcons\.PinOutlined',
        r'AppIcons.Pin, selected = \1',
        content
    )

    # 4. Fusion Error/ErrorOutlined (sync module)
    content = re.sub(r'AppIcons\.ErrorOutlined', 'AppIcons.Error', content)
    # 5. Fusion Warning/WarningOutlined
    content = re.sub(r'AppIcons\.WarningOutlined', 'AppIcons.Warning', content)
    # 6. Fusion Success/SuccessOutlined
    content = re.sub(r'AppIcons\.SuccessOutlined', 'AppIcons.Success', content)

    if content != original:
        with open(filepath, 'w') as f:
            f.write(content)
        return True
    return False

if __name__ == '__main__':
    count = 0
    for mod in ['feature', 'app', 'core/ui']:
        for root, dirs, files in os.walk(mod):
            for fn in files:
                if fn.endswith('.kt'):
                    fp = os.path.join(root, fn)
                    if fix_file(fp):
                        count += 1
                        print(f"  ✓ {fp}")
    print(f"\n{count} fichiers corrigés.")
