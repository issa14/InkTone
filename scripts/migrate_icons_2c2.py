#!/usr/bin/env python3
"""Migration mécanique Icon → AppIcon pour le rollout 2c.2.
Ne traite QUE les cas simples (Icon(AppIcons.X, ...) → AppIcon(AppIcons.X, ...)).
Les conditionnels if/else sont à gérer manuellement."""

import re
import os
import glob

MODULES = [
    "feature/library",
    "feature/reader",
    "feature/settings",
    "feature/statistics",
    "feature/search",
    "feature/sync",
    "feature/onboarding",
    "core/ui",
    "app",
]

def migrate_file(filepath):
    with open(filepath, 'r') as f:
        content = f.read()

    original = content

    # 1. Ajouter import AppIcon si AppIcons est déjà importé
    if 'import com.inktone.core.designsystem.AppIcons' in content \
       and 'import com.inktone.core.designsystem.AppIcon' not in content:
        content = content.replace(
            'import com.inktone.core.designsystem.AppIcons',
            'import com.inktone.core.designsystem.AppIcon\nimport com.inktone.core.designsystem.AppIcons'
        )

    # 2. Icon(AppIcons.X, ...) → AppIcon(AppIcons.X, ...)
    #    (attention : ne pas toucher aux Icon() qui n'utilisent PAS AppIcons)
    content = re.sub(r'\bIcon\(AppIcons\.', 'AppIcon(AppIcons.', content)

    if content != original:
        with open(filepath, 'w') as f:
            f.write(content)
        return True
    return False

if __name__ == '__main__':
    count = 0
    for mod in MODULES:
        for root, dirs, files in os.walk(mod):
            for f in files:
                if f.endswith('.kt'):
                    fp = os.path.join(root, f)
                    if migrate_file(fp):
                        count += 1
                        print(f"  ✓ {fp}")
    print(f"\n{count} fichiers migrés.")
