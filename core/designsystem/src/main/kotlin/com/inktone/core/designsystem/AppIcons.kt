package com.inktone.core.designsystem

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
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
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Accessibility
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.BrightnessMedium
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Contrast
import androidx.compose.material.icons.outlined.Delete
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
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.ViewDay
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material.icons.outlined.Widgets

/**
 * Point d'entrée unique pour les icônes de l'app (Material Symbols,
 * Tâche 9bis.1.1, porté du legacy). Convention : `Outlined` par défaut,
 * `Filled` pour les marqueurs d'état qui doivent rester lisibles à
 * petite taille (marque-page, statut). K12 — jamais d'emoji.
 */
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
    // Lot 8 — item de drawer Récents. Distinct de `Loading` (sablier,
    // défaut historique corrigé au lot 1, ne pas réintroduire).
    val Recents = Icons.Outlined.History

    // Tache 7.3/7.2 — statistiques : modes de session (historique par
    // ouvrage) et carte objectif du jour (série avec flamme).
    val VisualReading = Icons.Outlined.Visibility
    val TtsListening = Icons.Outlined.Headphones
    val Streak = Icons.Outlined.LocalFireDepartment

    val Search = Icons.Outlined.Search
    val Filter = Icons.Outlined.FilterList
    val Toc = Icons.AutoMirrored.Outlined.List
    val Back = Icons.AutoMirrored.Outlined.ArrowBack
    val ReadingModePaged = Icons.Outlined.ViewDay
    val ReadingModeScroll = Icons.Outlined.ImportContacts
    // Tache 3b.6 — bascule cyclique de theme (Clair -> Sombre -> Sepia).
    val Theme = Icons.Outlined.Contrast
    val Brightness = Icons.Outlined.BrightnessMedium

    // Tâche 1c — icônes de disposition de bibliothèque
    val ViewGrid = Icons.Outlined.Widgets
    val ViewList = Icons.AutoMirrored.Outlined.ViewList
    val CoverOnly = Icons.Outlined.PhotoLibrary

    // Tâche 3e.1 — barre pilule TTS : chevron simple pour le chapitre
    // (saut large), icône skip pour la phrase (saut fin) — deux glyphes
    // distincts pour deux granularités différentes, jamais le même.
    val ChapterPrevious = Icons.AutoMirrored.Filled.NavigateBefore
    val ChapterNext = Icons.AutoMirrored.Filled.NavigateNext
    val SentencePrevious = Icons.Filled.SkipPrevious
    val SentenceNext = Icons.Filled.SkipNext
}
