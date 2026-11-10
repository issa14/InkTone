package com.inktone.feature.reader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import com.inktone.domain.model.Annotation
import com.inktone.domain.model.Bookmark

/**
 * États d'AFFICHAGE du Lecteur — bandeau (HUD), panneaux, dialogues, popups —
 * regroupés en un seul objet plutôt qu'en une vingtaine de variables locales
 * de `ReaderScreen`.
 *
 * Purement des décisions d'affichage, jamais des données : la source de
 * vérité reste `ReaderUiState` (MVI, Blueprint §4.4). Regroupés pour deux
 * raisons :
 * - les sous-composables du Lecteur reçoivent CET objet (stable, même
 *   instance) et ne lisent que les champs qui les concernent — une
 *   recomposition ne se propage qu'aux lecteurs du champ modifié ;
 * - les règles de bascule (cycle d'appui, auto-masquage) sont des méthodes
 *   testables en JVM, plus des fonctions locales enfouies dans l'écran.
 */
@Stable
internal class ReaderChromeState {

    // Tache 9bis.3.1 : HUD (panneau de controle + boutons) visible par
    // defaut, se masque seul apres 4s (ImmersiveReaderChrome), un appui
    // sur la zone de lecture le fait reapparaitre.
    var isHudVisible by mutableStateOf(true)

    // Bug réel trouvé à l'audit : le délai de 4s ne redémarrait jamais
    // pendant qu'on interagit avec le HUD (il se masquait sous les
    // doigts de l'utilisateur au milieu d'une action) — ce compteur,
    // incrémenté à chaque interaction, force ImmersiveReaderChrome à
    // relancer son délai.
    var hudActivityTick by mutableIntStateOf(0)
        private set

    // B.2/B.3 — états d'affichage des panneaux de réglages in-reader
    var showSettingsPanel by mutableStateOf(false)
    var showTtsPanel by mutableStateOf(false)

    // 3d.4 — remplace le cycle nextSleepTimerMinutes : le tap sur Veille
    // ouvre désormais un panneau (puces + roue), au lieu de cycler
    // silencieusement 15→30→45→60 sans rien afficher.
    var showSleepTimerPanel by mutableStateOf(false)

    // Correctif Lot 21, tâche 5 — la note de signet n'ouvre plus un
    // AlertDialog modal à CHAQUE création : un Snackbar transitoire porte
    // l'action « Ajouter une note » ; la saisie ne s'ouvre que si
    // l'utilisateur la demande explicitement.
    var showBookmarkNoteDialog by mutableStateOf(false)

    // Lot 22, tâche 11 — édition depuis BookmarkPanel : l'élément en cours
    // d'édition porte lui-même sa note actuelle pour le pré-remplissage.
    var editingBookmark by mutableStateOf<Bookmark?>(null)
    var editingAnnotation by mutableStateOf<Annotation?>(null)

    // Lot 23, tâche 12 — menu contextuel au tap sur une annotation déjà
    // posée dans le texte (mode SCROLL). `tappedAnnotationBounds` porte
    // les bornes fenêtre remontées par `BookBlockItem.onAnnotationTapped`.
    var tappedAnnotation by mutableStateOf<Annotation?>(null)
        private set
    var tappedAnnotationBounds by mutableStateOf<Rect?>(null)
        private set

    // 3d.3 — visibilité locale de la barre de luminosité
    // (ReaderUiState.readerBrightness porte la vraie donnée).
    //
    // Bug réel trouvé à la vérification device (lot 3d) : la barre ne
    // disparaissait plus jamais une fois ouverte, y compris aux
    // réapparitions ultérieures du HUD. Corrigé : ouvrir la barre masque
    // le HUD (au lieu de le garder visible) et démarre un délai
    // d'auto-masquage, relancé à chaque ajustement
    // (brightnessBarActivityTick).
    var showBrightnessBar by mutableStateOf(false)
    var brightnessBarActivityTick by mutableIntStateOf(0)
        private set

    // 3e.1 (point A5) — pendant la lecture TTS, le panneau unifié est
    // remplacé par la barre pilule ; ce drapeau rappelle le panneau complet
    // par-dessus la barre le temps d'un appui, pour atteindre
    // Sommaire/TT/etc. sans interrompre la lecture.
    var showFullPanelOverlay by mutableStateOf(false)
        private set

    // 3e.2 — repli de la barre pilule en bouton unique après 4s
    // d'inactivité, réutilisant le délai d'ImmersiveReaderChrome
    // ([onHudAutoHide]) : pas de second minuteur. isHudVisible reste vrai
    // pendant le repli — seule la forme de son contenu change.
    var isPillCollapsed by mutableStateOf(false)
        private set

    /** Interaction avec le HUD : le garde visible et relance son délai d'auto-masquage. */
    fun keepHudVisible() {
        isHudVisible = true
        hudActivityTick++
    }

    /**
     * Appui sur la zone de lecture. Ferme la barre de luminosité si elle
     * est ouverte, sinon applique le basculement HUD habituel — un appui
     * pendant l'ajustement de luminosité doit fermer la barre, pas rouvrir
     * le HUD par-dessus dans le même geste.
     */
    fun onReadingAreaTap(isPlaying: Boolean) {
        if (showBrightnessBar) {
            showBrightnessBar = false
        } else if (isPlaying) {
            // Pendant le TTS, la barre pilule remplace le panneau unifié
            // (retiré du HUD standard) : un premier appui rappelle le
            // panneau complet, un second referme tout, un troisième
            // ramène la barre pilule — le même rythme à trois temps que le
            // cycle HUD habituel, appliqué à un état de plus.
            if (showFullPanelOverlay) {
                showFullPanelOverlay = false
                isHudVisible = false
            } else if (isHudVisible) {
                showFullPanelOverlay = true
                isPillCollapsed = false
            } else {
                keepHudVisible()
                isPillCollapsed = false
            }
        } else if (isHudVisible) {
            isHudVisible = false
        } else {
            keepHudVisible()
        }
    }

    /**
     * Fin du délai d'inactivité du HUD. 3e.2 — pendant le TTS (barre pilule
     * affichée, pas l'overlay panneau complet), l'inactivité replie la barre
     * au lieu de masquer tout le HUD : la lecture et le surlignage restent
     * visibles en permanence via le bouton replié.
     */
    fun onHudAutoHide(isPlaying: Boolean) {
        if (isPlaying && !showFullPanelOverlay) {
            isPillCollapsed = true
        } else {
            isHudVisible = false
        }
    }

    /** Fin de lecture : la barre pilule et son overlay n'ont plus de raison d'être. */
    fun onPlaybackStopped() {
        showFullPanelOverlay = false
        isPillCollapsed = false
    }

    /** Déploie la barre pilule repliée. */
    fun expandPill() {
        isPillCollapsed = false
        keepHudVisible()
    }

    /**
     * Bug réel corrigé (vérification device, lot 3d) : ouvrir la barre de
     * luminosité masque le HUD au lieu de le garder visible — la barre prend
     * sa place plutôt que de s'empiler dessous.
     */
    fun openBrightnessBar() {
        isHudVisible = false
        showBrightnessBar = true
        brightnessBarActivityTick++
    }

    /** Ajustement de luminosité : relance le délai d'auto-masquage de la barre. */
    fun onBrightnessAdjusted() {
        brightnessBarActivityTick++
    }

    fun showAnnotationMenu(annotation: Annotation, boundsInWindow: Rect) {
        tappedAnnotation = annotation
        tappedAnnotationBounds = boundsInWindow
    }

    fun dismissAnnotationMenu() {
        tappedAnnotation = null
        tappedAnnotationBounds = null
    }
}

@Composable
internal fun rememberReaderChromeState(): ReaderChromeState = remember { ReaderChromeState() }
