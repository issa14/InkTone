package com.inktone.feature.opds

import com.inktone.core.designsystem.InkToneSpacing

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.inktone.core.designsystem.AppIcon
import com.inktone.core.designsystem.AppSymbol
import com.inktone.core.designsystem.InkToneTopBar
import com.inktone.core.designsystem.InkToneEmptyState
import com.inktone.domain.model.OpdsCatalog

/**
 * Écran OPDS (Lot 13, tâche 13.4/13.6, drawer b4) — tableau de bord des
 * catalogues au repos, navigation dans un flux quand un catalogue est
 * ouvert. Le bouton Retour (système et UI) remonte d'un niveau de flux,
 * puis revient au tableau de bord, puis ferme l'écran — règle d'or UX
 * d'`OPDS.md` §1.2, reliée à `OpdsViewModel.goBack()`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogDashboardScreen(
    viewModel: OpdsViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
    onMenuClick: () -> Unit = {},
    onOpenPublication: (String) -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showSheet by remember { mutableStateOf(false) }
    var editingCatalog by remember { mutableStateOf<OpdsCatalog?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                OpdsEffect.CloseScreen -> onBack()
                is OpdsEffect.ShowMessage -> snackbarHostState.showSnackbar(effect.message)
                is OpdsEffect.DownloadComplete -> {
                    val result = snackbarHostState.showSnackbar(
                        message = "« ${effect.bookTitle} » ajouté à la bibliothèque",
                        actionLabel = "Lire maintenant",
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        onOpenPublication(effect.publicationId)
                    }
                }
            }
        }
    }

    BackHandler { viewModel.onIntent(OpdsIntent.GoBack) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (val s = state) {
                            is OpdsUiState.Feed -> s.title
                            is OpdsUiState.Dashboard -> "Catalogues en ligne"
                        },
                    )
                },
                navigationIcon = {
                    // Lot 18 — le tableau de bord est une destination
                    // principale du drawer (hamburger) ; un flux ouvert
                    // dans un catalogue reste une profondeur, avec sa
                    // flèche de retour d'origine (règle d'or `OPDS.md`
                    // §1.2 : remonter d'un niveau de flux, pas fermer).
                    if (state is OpdsUiState.Dashboard) {
                        IconButton(onClick = onMenuClick) {
                            AppIcon(AppSymbol.Menu, contentDescription = "Ouvrir le menu")
                        }
                    } else {
                        IconButton(onClick = { viewModel.onIntent(OpdsIntent.GoBack) }) {
                            AppIcon(AppSymbol.Back, contentDescription = "Retour")
                        }
                    }
                },
                actions = {
                    val s = state
                    if (s is OpdsUiState.Feed && s.searchTemplateUrl != null) {
                        IconButton(onClick = { showSearch = true }) {
                            AppIcon(AppSymbol.Search, contentDescription = "Rechercher")
                        }
                    }
                },
                colors = InkToneTopBar.colors(),
            )
        },
        floatingActionButton = {
            if (state is OpdsUiState.Dashboard) {
                FloatingActionButton(onClick = { editingCatalog = null; showSheet = true }) {
                    AppIcon(AppSymbol.Add, contentDescription = "Ajouter un catalogue")
                }
            }
        },
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding)) {
            when (val s = state) {
                is OpdsUiState.Dashboard -> CatalogDashboardContent(
                    state = s,
                    onOpenCatalog = { viewModel.onIntent(OpdsIntent.OpenCatalog(it)) },
                    onRemoveCatalog = { viewModel.onIntent(OpdsIntent.RemoveCatalog(it)) },
                    onEditCatalog = { editingCatalog = it; showSheet = true },
                    onAddDefault = { name, url -> viewModel.onIntent(OpdsIntent.AddCatalog(name, url, null, null)) },
                )
                is OpdsUiState.Feed -> OpdsFeedScreen(
                    state = s,
                    onOpenNavigation = { viewModel.onIntent(OpdsIntent.OpenNavigation(it)) },
                    onLoadNextPage = { viewModel.onIntent(OpdsIntent.LoadNextPage(it)) },
                    onDownloadBook = { viewModel.onIntent(OpdsIntent.DownloadBook(it)) },
                    onRetry = { viewModel.onIntent(OpdsIntent.RetryFeed) },
                    httpClient = viewModel.httpClient,
                )
            }
        }
    }

    if (showSheet) {
        val editing = editingCatalog
        AddCatalogBottomSheet(
            initial = editing,
            onConfirm = { name, url, username, password ->
                if (editing == null) {
                    viewModel.onIntent(OpdsIntent.AddCatalog(name, url, username, password))
                } else {
                    viewModel.onIntent(OpdsIntent.UpdateCatalog(editing.id, name, url, username, password))
                }
                showSheet = false
            },
            onDismiss = { showSheet = false },
        )
    }

    if (showSearch) {
        // Clavier ouvert à l'ouverture du dialogue : il ne sert qu'à saisir.
        val searchFocusRequester = remember { FocusRequester() }
        LaunchedEffect(Unit) { searchFocusRequester.requestFocus() }
        AlertDialog(
            onDismissRequest = { showSearch = false },
            title = { Text("Rechercher") },
            text = {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    singleLine = true,
                    label = { Text("Titre, auteur…") },
                    modifier = Modifier.fillMaxWidth().focusRequester(searchFocusRequester),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.onIntent(OpdsIntent.Search(searchQuery))
                        showSearch = false
                        searchQuery = ""
                    },
                    enabled = searchQuery.isNotBlank(),
                ) { Text("Rechercher") }
            },
            dismissButton = {
                TextButton(onClick = { showSearch = false }) { Text("Annuler") }
            },
        )
    }
}

@Composable
private fun CatalogDashboardContent(
    state: OpdsUiState.Dashboard,
    onOpenCatalog: (OpdsCatalog) -> Unit,
    onRemoveCatalog: (String) -> Unit,
    onEditCatalog: (OpdsCatalog) -> Unit,
    onAddDefault: (String, String) -> Unit,
) {
    when {
        state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        state.catalogs.isEmpty() -> InkToneEmptyState(
            icon = AppSymbol.Library,
            title = "Aucun catalogue",
            message = "Ajoutez-en un avec le bouton +, ou choisissez une suggestion.",
        ) {
            DefaultCatalogSuggestion(
                label = DefaultCatalogs.EBOOKS_GRATUITS.first,
                url = DefaultCatalogs.EBOOKS_GRATUITS.second,
                onAdd = onAddDefault,
            )
            Spacer(Modifier.height(InkToneSpacing.sm))
            DefaultCatalogSuggestion(
                label = DefaultCatalogs.UNGLUE_IT.first,
                url = DefaultCatalogs.UNGLUE_IT.second,
                onAdd = onAddDefault,
            )
        }
        else -> LazyColumn(
            // contentPadding et non padding : les cartes défilent jusqu'au bord
            // au lieu d'être coupées 12 dp avant.
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(InkToneSpacing.md),
            verticalArrangement = Arrangement.spacedBy(InkToneSpacing.md),
        ) {
            items(state.catalogs, key = { it.id }) { catalog ->
                CatalogCard(
                    catalog = catalog,
                    onClick = { onOpenCatalog(catalog) },
                    onEdit = { onEditCatalog(catalog) },
                    onRemove = { onRemoveCatalog(catalog.id) },
                )
            }
        }
    }
}

/** Carte d'un catalogue par défaut, ajoutable en un geste depuis le Dashboard vide. */
@Composable
private fun DefaultCatalogSuggestion(
    label: String,
    url: String,
    onAdd: (String, String) -> Unit,
) {
    Card(onClick = { onAdd(label, url) }) {
        Column(Modifier.fillMaxWidth().padding(InkToneSpacing.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(AppSymbol.AddCircle, contentDescription = null)
                Spacer(Modifier.width(InkToneSpacing.sm))
                Text(
                    label,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Text(
                url,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun CatalogCard(
    catalog: OpdsCatalog,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
) {
    var confirmRemove by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }

    Card(onClick = onClick) {
        Column(Modifier.fillMaxWidth().padding(InkToneSpacing.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(AppSymbol.Article, contentDescription = null)
                Spacer(Modifier.width(InkToneSpacing.sm))
                Text(
                    catalog.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                // Un menu plutôt que Modifier et Supprimer côte à côte : l'action
                // destructive n'est plus à un doigt de l'édition (audit UI P2-10).
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        AppIcon(AppSymbol.MoreActions, contentDescription = "Actions sur ${catalog.name}")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Modifier") },
                            leadingIcon = { AppIcon(AppSymbol.Edit, contentDescription = null) },
                            onClick = { menuOpen = false; onEdit() },
                        )
                        DropdownMenuItem(
                            text = { Text("Supprimer") },
                            leadingIcon = { AppIcon(AppSymbol.Delete, contentDescription = null) },
                            onClick = { menuOpen = false; confirmRemove = true },
                        )
                    }
                }
            }
            Text(
                catalog.rootUrl,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (catalog.hasCredentials) {
                Spacer(Modifier.height(InkToneSpacing.sm))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(
                        AppSymbol.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(InkToneSpacing.xs))
                    Text(
                        "Protégé",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }

    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text("Supprimer le catalogue ?") },
            text = { Text("« ${catalog.name} » sera retiré de vos catalogues.") },
            confirmButton = {
                TextButton(onClick = { confirmRemove = false; onRemove() }) { Text("Supprimer") }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemove = false }) { Text("Annuler") }
            },
        )
    }
}

/** Catalogues par défaut proposés en pré-remplissage (décision actée §10, jamais imposés).
 * Gutenberg (403), Feedbooks (flux vide depuis Cantook Market) et Gallica (403)
 * sont écartés — vérifié empiriquement le 2026-08-14, pas supposé. */
private object DefaultCatalogs {
    val EBOOKS_GRATUITS = "Ebooks gratuits" to "https://www.ebooksgratuits.com/opds/"
    val UNGLUE_IT = "Unglue.it" to "https://unglue.it/api/opds/"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddCatalogBottomSheet(
    initial: OpdsCatalog?,
    onConfirm: (name: String, rootUrl: String, username: String?, password: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var rootUrl by remember { mutableStateOf(initial?.rootUrl.orEmpty()) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    val isEdit = initial != null
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = InkToneSpacing.xl).padding(bottom = InkToneSpacing.xxl),
            verticalArrangement = Arrangement.spacedBy(InkToneSpacing.md),
        ) {
            Text(
                if (isEdit) "Modifier le catalogue" else "Ajouter un catalogue",
                style = MaterialTheme.typography.titleLarge,
            )

            if (!isEdit) {
                // Catalogues par défaut : pré-remplissage du formulaire
                // (décision actée §1.4), uniquement en mode ajout.
                Row(horizontalArrangement = Arrangement.spacedBy(InkToneSpacing.sm)) {
                    OutlinedButton(onClick = {
                        name = DefaultCatalogs.EBOOKS_GRATUITS.first
                        rootUrl = DefaultCatalogs.EBOOKS_GRATUITS.second
                    }) { Text("Ebooks gratuits") }
                    OutlinedButton(onClick = {
                        name = DefaultCatalogs.UNGLUE_IT.first
                        rootUrl = DefaultCatalogs.UNGLUE_IT.second
                    }) { Text("Unglue.it") }
                }
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nom") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = rootUrl,
                onValueChange = { rootUrl = it },
                label = { Text("Adresse du catalogue") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Nom d'utilisateur") },
                supportingText = { Text(if (isEdit) "Laisser vide pour ne pas le changer" else "Facultatif") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Mot de passe") },
                supportingText = { Text(if (isEdit) "Laisser vide pour ne pas le changer" else "Facultatif") },
                singleLine = true,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        AppIcon(
                            if (passwordVisible) AppSymbol.VisibilityOff else AppSymbol.Visibility,
                            contentDescription = if (passwordVisible) "Masquer le mot de passe" else "Afficher le mot de passe",
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )

            // Avertissement cleartext — non désactivable (décision actée §10).
            if (rootUrl.trim().startsWith("http://")) {
                Text(
                    "Connexion non chiffrée : à réserver à un serveur de confiance sur votre réseau local.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            val hasUsername = username.isNotBlank()
            val hasPassword = password.isNotBlank()
            val credentialsValid = hasUsername == hasPassword
            val formValid = name.isNotBlank() && rootUrl.isNotBlank() && credentialsValid

            if (!credentialsValid) {
                Text(
                    "Renseignez le nom d'utilisateur et le mot de passe ensemble.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            TextButton(
                onClick = { onConfirm(name.trim(), rootUrl.trim(), username.trim().ifBlank { null }, password.ifBlank { null }) },
                enabled = formValid,
                modifier = Modifier.align(Alignment.End),
            ) { Text(if (isEdit) "Enregistrer" else "Ajouter") }
        }
    }
}
