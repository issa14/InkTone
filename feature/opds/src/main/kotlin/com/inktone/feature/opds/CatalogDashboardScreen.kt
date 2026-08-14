package com.inktone.feature.opds

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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.inktone.core.designsystem.AppIcon
import com.inktone.core.designsystem.AppSymbol
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
    onOpenPublication: (String) -> Unit = {},
) {
    val state by viewModel.state.collectAsState()
    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
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
                            is OpdsUiState.Dashboard -> "Catalogues OPDS"
                        },
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { viewModel.onIntent(OpdsIntent.GoBack) }) {
                        AppIcon(AppSymbol.Back, contentDescription = "Retour")
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
            )
        },
        floatingActionButton = {
            val onDashboard = state is OpdsUiState.Dashboard
            if (onDashboard) {
                var showSheet by remember { mutableStateOf(false) }
                FloatingActionButton(onClick = { showSheet = true }) {
                    AppIcon(AppSymbol.Add, contentDescription = "Ajouter un catalogue")
                }
                if (showSheet) {
                    AddCatalogBottomSheet(
                        onConfirm = { name, url, username, password ->
                            viewModel.onIntent(OpdsIntent.AddCatalog(name, url, username, password))
                            showSheet = false
                        },
                        onDismiss = { showSheet = false },
                    )
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
                )
                is OpdsUiState.Feed -> OpdsFeedScreen(
                    state = s,
                    onOpenNavigation = { viewModel.onIntent(OpdsIntent.OpenNavigation(it)) },
                    onLoadNextPage = { viewModel.onIntent(OpdsIntent.LoadNextPage(it)) },
                    onDownloadBook = { viewModel.onIntent(OpdsIntent.DownloadBook(it)) },
                    httpClient = viewModel.httpClient,
                )
            }
        }
    }

    if (showSearch) {
        AlertDialog(
            onDismissRequest = { showSearch = false },
            title = { Text("Rechercher") },
            text = {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    singleLine = true,
                    label = { Text("Titre, auteur…") },
                    modifier = Modifier.fillMaxWidth(),
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
) {
    when {
        state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        state.catalogs.isEmpty() -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text(
                "Aucun catalogue.\nAjoutez-en un avec le bouton + (ex. Gutenberg ou Feedbooks).",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        else -> LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 160.dp),
            modifier = Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(state.catalogs, key = { it.id }) { catalog ->
                CatalogCard(
                    catalog = catalog,
                    onClick = { onOpenCatalog(catalog) },
                    onRemove = { onRemoveCatalog(catalog.id) },
                )
            }
        }
    }
}

@Composable
private fun CatalogCard(
    catalog: OpdsCatalog,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    var confirmRemove by remember { mutableStateOf(false) }

    Card(onClick = onClick) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(AppSymbol.Article, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    catalog.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { confirmRemove = true }) {
                    AppIcon(AppSymbol.Delete, contentDescription = "Supprimer ${catalog.name}")
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
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(
                        AppSymbol.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(4.dp))
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

/** Catalogues par défaut proposés en pré-remplissage (décision actée §10, jamais imposés). */
private object DefaultCatalogs {
    val GUTENBERG = "Gutenberg" to "https://www.gutenberg.org/ebooks.opds"
    val FEEDBOOKS = "Feedbooks" to "https://catalog.feedbooks.com/catalog.atom"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddCatalogBottomSheet(
    onConfirm: (name: String, rootUrl: String, username: String?, password: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var rootUrl by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Ajouter un catalogue", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

            // Catalogues par défaut : pré-remplissage du formulaire, pas
            // des lignes non supprimables imposées (décision actée §1.4).
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    name = DefaultCatalogs.GUTENBERG.first
                    rootUrl = DefaultCatalogs.GUTENBERG.second
                }) { Text("Gutenberg") }
                OutlinedButton(onClick = {
                    name = DefaultCatalogs.FEEDBOOKS.first
                    rootUrl = DefaultCatalogs.FEEDBOOKS.second
                }) { Text("Feedbooks") }
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
                label = { Text("URL racine") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Nom d'utilisateur (optionnel)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Mot de passe (optionnel)") },
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
                    "Ce catalogue n'est pas chiffré — à réserver à un serveur de confiance sur votre réseau local.",
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
            ) { Text("Ajouter") }
        }
    }
}
