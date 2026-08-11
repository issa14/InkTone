package com.inktone.core.designsystem

import androidx.annotation.DrawableRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.SlowMotionVideo
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Accessibility
import androidx.compose.material.icons.outlined.AddCircle
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.BrightnessMedium
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Contrast
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Error
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.ImportContacts
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.ViewDay
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material.icons.outlined.Widgets
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
    Menu(R.drawable.ic_symbol_menu),
    Sort(R.drawable.ic_symbol_sort),
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

// --- Compatibilité ImageVector (legacy, supprimé en 2c.3) ---
// Ces alias exposent les anciennes valeurs ImageVector pour que le code
// existant continue de compiler pendant le rollout progressif 2c.2.
// Ils seront supprimés une fois tous les modules migrés vers AppIcon.

@Suppress("DEPRECATION")
object AppIcons {
    val Bookmark = Icons.Filled.Bookmark
    val BookmarkAdd = Icons.Outlined.BookmarkAdd
    val Note = Icons.Outlined.EditNote
    val Copy = Icons.Outlined.ContentCopy
    val Highlight = Icons.Filled.Highlight
    val Hint = Icons.Outlined.Lightbulb
    val Favorite = Icons.Filled.Favorite
    val FavoriteBorder = Icons.Outlined.FavoriteBorder
    val Pin = Icons.Filled.PushPin
    val PinOutlined = Icons.Outlined.PushPin
    val MoreActions = Icons.Filled.MoreVert
    val Menu = Icons.Outlined.Menu
    val Sort = Icons.AutoMirrored.Filled.Sort
    val Delete = Icons.Outlined.Delete

    val Success = Icons.Filled.CheckCircle
    val SuccessOutlined = Icons.Outlined.CheckCircle
    val Error = Icons.Filled.Error
    val ErrorOutlined = Icons.Outlined.Error
    val Warning = Icons.Filled.Warning
    val WarningOutlined = Icons.Outlined.Warning
    val Info = Icons.Outlined.Info

    val Presets = Icons.Outlined.Bolt
    val Reading = Icons.AutoMirrored.Outlined.MenuBook
    val Device = Icons.Outlined.Smartphone
    val Appearance = Icons.Outlined.Palette
    val Accessibility = Icons.Outlined.Accessibility
    val Data = Icons.Outlined.Save
    val Pronunciation = Icons.Outlined.RecordVoiceOver
    val Settings = Icons.Outlined.Settings

    val Mic = Icons.Outlined.Mic
    val Speaking = Icons.AutoMirrored.Outlined.VolumeUp
    val Loading = Icons.Outlined.HourglassEmpty
    val Refresh = Icons.Outlined.Refresh
    val ChevronDown = Icons.Outlined.KeyboardArrowDown
    val Stats = Icons.Outlined.BarChart
    val Sync = Icons.Outlined.CloudSync
    val CloudConnected = Icons.Outlined.CloudDone
    val CloudDisconnected = Icons.Outlined.CloudOff
    val Recents = Icons.Outlined.History

    val Edit = Icons.Outlined.Edit
    val AddCircle = Icons.Outlined.AddCircle
    val VisualReading = Icons.Outlined.Visibility
    val TtsListening = Icons.Outlined.Headphones
    val Streak = Icons.Outlined.LocalFireDepartment

    val Search = Icons.Outlined.Search
    val Filter = Icons.Outlined.FilterList
    val Toc = Icons.AutoMirrored.Outlined.List
    val Back = Icons.AutoMirrored.Outlined.ArrowBack
    val ReadingModePaged = Icons.Outlined.ViewDay
    val ReadingModeScroll = Icons.Outlined.ImportContacts
    val Theme = Icons.Outlined.Contrast
    val Brightness = Icons.Outlined.BrightnessMedium

    val ViewGrid = Icons.Outlined.Widgets
    val ViewList = Icons.AutoMirrored.Outlined.ViewList
    val CoverOnly = Icons.Outlined.PhotoLibrary

    val ChapterPrevious = Icons.AutoMirrored.Filled.NavigateBefore
    val ChapterNext = Icons.AutoMirrored.Filled.NavigateNext
    val SentencePrevious = Icons.Filled.SkipPrevious
    val SentenceNext = Icons.Filled.SkipNext

    // Icônes inline centralisées (2c.1)
    val Play = Icons.Filled.PlayArrow
    val Pause = Icons.Filled.Pause
    val Add = Icons.Outlined.Add
    val Remove = Icons.Outlined.Remove
    val SleepTimer = Icons.Outlined.Bedtime

    // Icônes ajoutées pour le rollout 2c.2 modules
    val DarkMode = Icons.Filled.DarkMode
    val Close = Icons.Outlined.Close
    val Download = Icons.Outlined.Download
    val ChevronRight = Icons.Outlined.ChevronRight
    val Article = Icons.Outlined.Article
    val Speed = Icons.Outlined.Speed
    val ArrowForward = Icons.AutoMirrored.Filled.ArrowForward
    val VolumeDown = Icons.AutoMirrored.Filled.VolumeDown
    val DeleteSweep = Icons.Filled.DeleteSweep

    // Icônes ajoutées pour le rollout settings 2c.2
    val SlowMotionVideo = Icons.Filled.SlowMotionVideo
    val FastForward = Icons.Filled.FastForward
    val KeyboardArrowDown = Icons.Filled.KeyboardArrowDown
    val KeyboardArrowUp = Icons.Filled.KeyboardArrowUp
    val Lock = Icons.Filled.Lock
    val Upload = Icons.Filled.Upload
    val WarningAmber = Icons.Filled.WarningAmber
    val SearchOff = Icons.Outlined.SearchOff
}
