package com.inktone.core.designsystem

import androidx.annotation.DrawableRes
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource

/**
 * Lot 2c — icône Material Symbols Rounded pilotée par l'état.
 *
 * Chaque valeur référence un ou deux VectorDrawables (24dp, grille
 * Material Symbols Rounded, `wght 400`, `GRAD 0`, `opsz 24`).
 * - `default` : glyphe de repos (contour, ou plein pour un statut).
 * - `activeFill` : override quand `selected = true` (icônes à état).
 *   `null` pour les icônes mono (pas de variant sélectionné).
 *
 * Les paires legacy (`Favorite`/`FavoriteBorder`, `Pin`/`PinOutlined`,
 * `Success`/`SuccessOutlined`, etc.) sont fusionnées en un seul symbole
 * + `selected`. Les anciens noms `*Border`/`*Outlined`/`*Filled` sont
 * supprimés.
 */
enum class AppSymbol(
    @DrawableRes val default: Int,
    @DrawableRes val activeFill: Int? = null,
) {
    // --- Navigation — à état ---
    Reading(R.drawable.ic_symbol_reading_outlined, R.drawable.ic_symbol_reading_filled),
    Recents(R.drawable.ic_symbol_recents_outlined, R.drawable.ic_symbol_recents_filled),
    Stats(R.drawable.ic_symbol_stats_outlined, R.drawable.ic_symbol_stats_filled),
    Sync(R.drawable.ic_symbol_sync_outlined, R.drawable.ic_symbol_sync_filled),
    Theme(R.drawable.ic_symbol_theme_outlined, R.drawable.ic_symbol_theme_filled),
    Settings(R.drawable.ic_symbol_settings_outlined, R.drawable.ic_symbol_settings_filled),

    // --- Bascules — à état ---
    Bookmark(R.drawable.ic_symbol_bookmark_outlined, R.drawable.ic_symbol_bookmark_filled),
    Favorite(R.drawable.ic_symbol_favorite_outlined, R.drawable.ic_symbol_favorite_filled),
    Pin(R.drawable.ic_symbol_pin_outlined, R.drawable.ic_symbol_pin_filled),

    // --- TTS — à état ---
    Speaking(R.drawable.ic_symbol_speaking_outlined, R.drawable.ic_symbol_speaking_filled),

    // --- Modes segmentés — à état ---
    ReadingModePaged(R.drawable.ic_symbol_reading_mode_paged_outlined, R.drawable.ic_symbol_reading_mode_paged_filled),
    ReadingModeScroll(R.drawable.ic_symbol_reading_mode_scroll_outlined, R.drawable.ic_symbol_reading_mode_scroll_filled),
    ViewGrid(R.drawable.ic_symbol_view_grid_outlined, R.drawable.ic_symbol_view_grid_filled),
    ViewList(R.drawable.ic_symbol_view_list_outlined, R.drawable.ic_symbol_view_list_filled),
    CoverOnly(R.drawable.ic_symbol_cover_only_outlined, R.drawable.ic_symbol_cover_only_filled),

    // --- Statuts — mono, glyphe plein au repos ---
    Error(R.drawable.ic_symbol_error),
    Warning(R.drawable.ic_symbol_warning),
    Success(R.drawable.ic_symbol_success),

    // --- Mono — contour au repos ---
    Info(R.drawable.ic_symbol_info),
    Search(R.drawable.ic_symbol_search),
    Delete(R.drawable.ic_symbol_delete),
    Edit(R.drawable.ic_symbol_edit),
    Add(R.drawable.ic_symbol_add),
    Remove(R.drawable.ic_symbol_remove),
    Play(R.drawable.ic_symbol_play),
    Pause(R.drawable.ic_symbol_pause),
    Back(R.drawable.ic_symbol_back),
    Toc(R.drawable.ic_symbol_toc),
    ChapterPrevious(R.drawable.ic_symbol_chapter_previous),
    ChapterNext(R.drawable.ic_symbol_chapter_next),
    SentencePrevious(R.drawable.ic_symbol_sentence_previous),
    SentenceNext(R.drawable.ic_symbol_sentence_next),
    Refresh(R.drawable.ic_symbol_refresh),
    Filter(R.drawable.ic_symbol_filter),
    ChevronDown(R.drawable.ic_symbol_chevron_down),
    /**
     * Triangle PLEIN d'ouverture de menu — affordance conventionnelle d'un
     * déroulant. À préférer à [ChevronDown] quand l'indice doit tenir à côté
     * d'un texte gras : un chevron `wght 400` réduit se lit comme un filet,
     * là où une forme pleine garde sa masse à n'importe quelle taille.
     */
    ArrowDropDown(R.drawable.ic_symbol_arrow_drop_down),
    MoreActions(R.drawable.ic_symbol_more_actions),
    Menu(R.drawable.ic_symbol_menu),
    Sort(R.drawable.ic_symbol_sort),
    Copy(R.drawable.ic_symbol_copy),
    Highlight(R.drawable.ic_symbol_highlight),
    Brightness(R.drawable.ic_symbol_brightness),
    FormatSize(R.drawable.ic_symbol_format_size),
    SleepTimer(R.drawable.ic_symbol_sleep_timer),
    Device(R.drawable.ic_symbol_device),
    CloudConnected(R.drawable.ic_symbol_cloud_connected),
    CloudDisconnected(R.drawable.ic_symbol_cloud_disconnected),
    VisualReading(R.drawable.ic_symbol_visual_reading),
    TtsListening(R.drawable.ic_symbol_tts_listening),
    Streak(R.drawable.ic_symbol_streak),
    Mic(R.drawable.ic_symbol_mic),
    Data(R.drawable.ic_symbol_data),
    Presets(R.drawable.ic_symbol_presets),
    Note(R.drawable.ic_symbol_note),
    Accessibility(R.drawable.ic_symbol_accessibility),
    Pronunciation(R.drawable.ic_symbol_pronunciation),
    AddCircle(R.drawable.ic_symbol_add_circle),

    // --- Lot 2c.2 — icônes ajoutées pour le rollout modules ---
    DarkMode(R.drawable.ic_symbol_dark_mode),
    Close(R.drawable.ic_symbol_close),
    Download(R.drawable.ic_symbol_download),
    ChevronRight(R.drawable.ic_symbol_chevron_right),
    Article(R.drawable.ic_symbol_article),
    Speed(R.drawable.ic_symbol_speed),
    ArrowForward(R.drawable.ic_symbol_arrow_forward),
    VolumeDown(R.drawable.ic_symbol_volume_down),
    DeleteSweep(R.drawable.ic_symbol_delete_sweep),

    // --- Lot 2c.2 settings — icônes ajoutées pour le rollout ---
    SlowMotionVideo(R.drawable.ic_symbol_slow_motion_video),
    FastForward(R.drawable.ic_symbol_fast_forward),
    KeyboardArrowDown(R.drawable.ic_symbol_keyboard_arrow_down),
    KeyboardArrowUp(R.drawable.ic_symbol_keyboard_arrow_up),
    Lock(R.drawable.ic_symbol_lock),
    Upload(R.drawable.ic_symbol_upload),
    WarningAmber(R.drawable.ic_symbol_warning_amber),
    SearchOff(R.drawable.ic_symbol_search_off),
    Timer(R.drawable.ic_symbol_timer),
    TrendingUp(R.drawable.ic_symbol_trending_up),
    Visibility(R.drawable.ic_symbol_visibility),
    VisibilityOff(R.drawable.ic_symbol_visibility_off),
}

/**
 * Composable d'icône Material Symbols Rounded (Lot 2c).
 *
 * Remplace `Icon(Icons.*, ...)` par un rendu VectorDrawable tintable
 * avec gestion native de l'état sélectionné (contour → plein).
 *
 * @param symbol le symbole à afficher.
 * @param contentDescription description d'accessibilité (null si décoratif).
 * @param selected passe au glyphe plein si le symbole le supporte.
 * @param tint couleur de teinte (défaut : `LocalContentColor.current`).
 */
@Composable
fun AppIcon(
    symbol: AppSymbol,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    tint: Color = LocalContentColor.current,
) {
    val res = if (selected && symbol.activeFill != null) symbol.activeFill else symbol.default
    Icon(
        painter = painterResource(id = res),
        contentDescription = contentDescription,
        modifier = modifier,
        tint = tint,
    )
}
