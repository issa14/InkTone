#!/usr/bin/env python3
"""Migration 2c.2 v2 : Icon(AppIcons.X) → AppIcon(AppSymbol.X).
Mapping correct des noms legacy vers AppSymbol."""

import re, os

# Mapping des noms AppIcons legacy → AppSymbol
LEGACY_MAP = {
    'Bookmark': 'Bookmark', 'BookmarkAdd': 'Bookmark',
    'Note': 'Note', 'Copy': 'Copy', 'Highlight': 'Highlight',
    'Hint': None,  # pas d'équivalent
    'Favorite': 'Favorite', 'FavoriteBorder': 'Favorite',
    'Pin': 'Pin', 'PinOutlined': 'Pin',
    'MoreActions': 'MoreActions', 'Delete': 'Delete',
    'Success': 'Success', 'SuccessOutlined': 'Success',
    'Error': 'Error', 'ErrorOutlined': 'Error',
    'Warning': 'Warning', 'WarningOutlined': 'Warning',
    'Info': 'Info',
    'Presets': 'Presets', 'Reading': 'Reading',
    'Device': 'Device', 'Appearance': 'Theme',
    'Accessibility': 'Accessibility', 'Data': 'Data',
    'Pronunciation': 'Pronunciation', 'Settings': 'Settings',
    'Mic': 'Mic', 'Speaking': 'Speaking',
    'Loading': None,
    'Refresh': 'Refresh', 'ChevronDown': 'ChevronDown',
    'Stats': 'Stats', 'Sync': 'Sync',
    'CloudConnected': 'CloudConnected', 'CloudDisconnected': 'CloudDisconnected',
    'Recents': 'Recents',
    'Edit': 'Edit', 'AddCircle': 'AddCircle',
    'VisualReading': 'VisualReading', 'TtsListening': 'TtsListening',
    'Streak': 'Streak',
    'Search': 'Search', 'Filter': 'Filter',
    'Toc': 'Toc', 'Back': 'Back',
    'ReadingModePaged': 'ReadingModePaged', 'ReadingModeScroll': 'ReadingModeScroll',
    'Theme': 'Theme', 'Brightness': 'Brightness',
    'ViewGrid': 'ViewGrid', 'ViewList': 'ViewList', 'CoverOnly': 'CoverOnly',
    'ChapterPrevious': 'ChapterPrevious', 'ChapterNext': 'ChapterNext',
    'SentencePrevious': 'SentencePrevious', 'SentenceNext': 'SentenceNext',
    'Play': 'Play', 'Pause': 'Pause',
    'Add': 'Add', 'Remove': 'Remove',
    'SleepTimer': 'SleepTimer',
}

def migrate_file(filepath):
    with open(filepath) as f:
        content = f.read()
    original = content

    # 1. Ajouter imports AppIcon + AppSymbol si absents
    if 'import com.inktone.core.designsystem.AppIcons' in content:
        if 'import com.inktone.core.designsystem.AppIcon' not in content:
            content = content.replace(
                'import com.inktone.core.designsystem.AppIcons',
                'import com.inktone.core.designsystem.AppIcon\nimport com.inktone.core.designsystem.AppIcons'
            )
        if 'import com.inktone.core.designsystem.AppSymbol' not in content:
            content = content.replace(
                'import com.inktone.core.designsystem.AppIcons',
                'import com.inktone.core.designsystem.AppIcons\nimport com.inktone.core.designsystem.AppSymbol'
            )

    # 2. Icon(AppIcons.XXX, …) → AppIcon(AppSymbol.YYY, …)
    def replace_icon(m):
        legacy_name = m.group(1)
        symbol_name = LEGACY_MAP.get(legacy_name)
        if symbol_name is None:
            return m.group(0)  # garder inchangé (pas d'équivalent)
        return f'AppIcon(AppSymbol.{symbol_name}, '

    content = re.sub(r'\bIcon\(AppIcons\.(\w+)\s*,', replace_icon, content)

    # 3. AppIcon(AppIcons.XXX, …) → AppIcon(AppSymbol.YYY, …)
    #    (cas où un précédent run a déjà mis AppIcon mais encore AppIcons)
    def replace_appicon(m):
        legacy_name = m.group(1)
        symbol_name = LEGACY_MAP.get(legacy_name)
        if symbol_name is None:
            return m.group(0)
        return f'AppIcon(AppSymbol.{symbol_name}, '

    content = re.sub(r'\bAppIcon\(AppIcons\.(\w+)\s*,', replace_appicon, content)

    # 4. Fusion conditionnelle Favorite/FavoriteBorder dans AppIcon
    content = re.sub(
        r'AppIcon\(AppSymbol\.Favorite,\s*selected\s*=\s*publication\.isFavorite\s*,',
        'AppIcon(AppSymbol.Favorite, selected = publication.isFavorite,',
        content
    )
    # Cas Icon(if (x) AppIcons.Favorite else AppIcons.FavoriteBorder, …)
    content = re.sub(
        r'Icon\(\s*if\s*\(publication\.isFavorite\)\s*AppIcons\.Favorite\s*else\s*AppIcons\.FavoriteBorder\s*,',
        'AppIcon(AppSymbol.Favorite, selected = publication.isFavorite,',
        content
    )
    content = re.sub(
        r'Icon\(\s*if\s*\(([^)]+)\)\s*AppIcons\.Favorite\s*else\s*AppIcons\.FavoriteBorder\s*,',
        r'AppIcon(AppSymbol.Favorite, selected = \1,',
        content
    )

    # 5. Fusion conditionnelle Pin/PinOutlined
    content = re.sub(
        r'Icon\(\s*if\s*\(publication\.isPinned\)\s*AppIcons\.Pin\s*else\s*AppIcons\.PinOutlined\s*,',
        'AppIcon(AppSymbol.Pin, selected = publication.isPinned,',
        content
    )
    content = re.sub(
        r'Icon\(\s*if\s*\(item\.isPinned\)\s*AppIcons\.Pin\s*else\s*AppIcons\.PinOutlined\s*,',
        'AppIcon(AppSymbol.Pin, selected = item.isPinned,',
        content
    )
    content = re.sub(
        r'Icon\(\s*if\s*\(([^)]+)\)\s*AppIcons\.Pin\s*else\s*AppIcons\.PinOutlined\s*,',
        r'AppIcon(AppSymbol.Pin, selected = \1,',
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
        for fn in sorted(files):
            if fn.endswith('.kt'):
                fp = os.path.join(root, fn)
                if migrate_file(fp):
                    count += 1
                    print(f"  ✓ {os.path.relpath(fp)}")
    print(f"\n{count} fichiers migrés dans {module}")
