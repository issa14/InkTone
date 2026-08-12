#!/usr/bin/env python3
"""Migration 2c.2 : Icon(AppIcons.X) → AppIcon(AppIcons.X) par module.
Gère les imports, les fusions conditionnelles, et nettoie les Icons.*."""

import re, os, glob

MODULE = None  # défini par --module

def migrate_file(filepath):
    with open(filepath) as f:
        content = f.read()
    original = content

    # 1. Ajouter import AppIcon si AppIcons est importé
    if 'import com.inktone.core.designsystem.AppIcons' in content \
       and 'import com.inktone.core.designsystem.AppIcon' not in content:
        content = content.replace(
            'import com.inktone.core.designsystem.AppIcons',
            'import com.inktone.core.designsystem.AppIcon\nimport com.inktone.core.designsystem.AppIcons'
        )

    # 2. Icon(AppIcons.X, …) → AppIcon(AppIcons.X, …)
    #    Attention : ne matche PAS Icon(if (…) AppIcons…) — géré séparément
    content = re.sub(r'\bIcon\(AppIcons\.', 'AppIcon(AppIcons.', content)

    # 3. Fusion Favorite/FavoriteBorder dans AppIcon (déjà dans un appel Icon/AppIcon)
    #    Pattern: if (publication.isFavorite) AppIcons.Favorite else AppIcons.FavoriteBorder
    #    → remplacer le if/else par AppIcons.Favorite, selected = …
    content = re.sub(
        r'AppIcon\(\s*if\s*\(publication\.isFavorite\)\s*AppIcons\.Favorite\s*else\s*AppIcons\.FavoriteBorder\s*,',
        'AppIcon(AppIcons.Favorite, selected = publication.isFavorite,',
        content
    )
    # Cas générique: if (x) AppIcons.Favorite else AppIcons.FavoriteBorder dans AppIcon
    content = re.sub(
        r'AppIcon\(\s*if\s*\(([^)]+)\)\s*AppIcons\.Favorite\s*else\s*AppIcons\.FavoriteBorder\s*,',
        r'AppIcon(AppIcons.Favorite, selected = \1,',
        content
    )
    # Même chose mais dans Icon(…) — ces cas n'ont pas encore été migrés
    content = re.sub(
        r'Icon\(\s*if\s*\(publication\.isFavorite\)\s*AppIcons\.Favorite\s*else\s*AppIcons\.FavoriteBorder\s*,',
        'AppIcon(AppIcons.Favorite, selected = publication.isFavorite,',
        content
    )
    content = re.sub(
        r'Icon\(\s*if\s*\(([^)]+)\)\s*AppIcons\.Favorite\s*else\s*AppIcons\.FavoriteBorder\s*,',
        r'AppIcon(AppIcons.Favorite, selected = \1,',
        content
    )

    # 4. Fusion Pin/PinOutlined
    content = re.sub(
        r'Icon\(\s*if\s*\(publication\.isPinned\)\s*AppIcons\.Pin\s*else\s*AppIcons\.PinOutlined\s*,',
        'AppIcon(AppIcons.Pin, selected = publication.isPinned,',
        content
    )
    content = re.sub(
        r'Icon\(\s*if\s*\(item\.isPinned\)\s*AppIcons\.Pin\s*else\s*AppIcons\.PinOutlined\s*,',
        'AppIcon(AppIcons.Pin, selected = item.isPinned,',
        content
    )
    content = re.sub(
        r'Icon\(\s*if\s*\(([^)]+)\)\s*AppIcons\.Pin\s*else\s*AppIcons\.PinOutlined\s*,',
        r'AppIcon(AppIcons.Pin, selected = \1,',
        content
    )

    if content != original:
        with open(filepath, 'w') as f:
            f.write(content)
        return True
    return False

if __name__ == '__main__':
    import sys
    module = sys.argv[1] if len(sys.argv) > 1 else 'feature/library'
    count = 0
    for root, dirs, files in os.walk(module):
        for fn in files:
            if fn.endswith('.kt'):
                fp = os.path.join(root, fn)
                if migrate_file(fp):
                    count += 1
                    print(f"  ✓ {fp}")
    print(f"\n{count} fichiers migrés dans {module}")
