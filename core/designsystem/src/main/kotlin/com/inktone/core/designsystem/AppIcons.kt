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
    MoreActions(R.drawable.ic_symbol_more_actions),
    Copy(R.drawable.ic_symbol_copy),
    Highlight(R.drawable.ic_symbol_highlight),
    Brightness(R.drawable.ic_symbol_brightness),
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

// --- Compatibilité transitoire (supprimé en 2c.3) ---
// Ces alias permettent aux modules non encore migrés de référencer les
// anciens noms via AppIcons.*. Ils seront retirés quand tous les modules
// seront passés à AppSymbol + AppIcon.

object AppIcons {
    val Bookmark = AppSymbol.Bookmark
    val Favorite = AppSymbol.Favorite
    val Pin = AppSymbol.Pin
    val Error = AppSymbol.Error
    val Warning = AppSymbol.Warning
    val Success = AppSymbol.Success
    val Info = AppSymbol.Info
    val Search = AppSymbol.Search
    val Delete = AppSymbol.Delete
    val Edit = AppSymbol.Edit
    val Add = AppSymbol.Add
    val Remove = AppSymbol.Remove
    val Play = AppSymbol.Play
    val Pause = AppSymbol.Pause
    val Back = AppSymbol.Back
    val Toc = AppSymbol.Toc
    val Refresh = AppSymbol.Refresh
    val Filter = AppSymbol.Filter
    val ChevronDown = AppSymbol.ChevronDown
    val MoreActions = AppSymbol.MoreActions
    val Copy = AppSymbol.Copy
    val Highlight = AppSymbol.Highlight
    val Brightness = AppSymbol.Brightness
    val SleepTimer = AppSymbol.SleepTimer
    val Reading = AppSymbol.Reading
    val Recents = AppSymbol.Recents
    val Stats = AppSymbol.Stats
    val Sync = AppSymbol.Sync
    val Theme = AppSymbol.Theme
    val Settings = AppSymbol.Settings
    val Speaking = AppSymbol.Speaking
    val ReadingModePaged = AppSymbol.ReadingModePaged
    val ReadingModeScroll = AppSymbol.ReadingModeScroll
    val ViewGrid = AppSymbol.ViewGrid
    val ViewList = AppSymbol.ViewList
    val CoverOnly = AppSymbol.CoverOnly
    val Device = AppSymbol.Device
    val CloudConnected = AppSymbol.CloudConnected
    val CloudDisconnected = AppSymbol.CloudDisconnected
    val VisualReading = AppSymbol.VisualReading
    val TtsListening = AppSymbol.TtsListening
    val Streak = AppSymbol.Streak
    val Mic = AppSymbol.Mic
    val Data = AppSymbol.Data
    val Presets = AppSymbol.Presets
    val Note = AppSymbol.Note
    val Accessibility = AppSymbol.Accessibility
    val Pronunciation = AppSymbol.Pronunciation
    val AddCircle = AppSymbol.AddCircle
    val ChapterPrevious = AppSymbol.ChapterPrevious
    val ChapterNext = AppSymbol.ChapterNext
    val SentencePrevious = AppSymbol.SentencePrevious
    val SentenceNext = AppSymbol.SentenceNext
    val Appearance = AppSymbol.Theme
}
