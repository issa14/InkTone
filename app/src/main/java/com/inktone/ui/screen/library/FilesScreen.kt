package com.inktone.ui.screen.library

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inktone.ui.theme.ttsActive
import java.io.File
import java.io.FileFilter

private val SUPPORTED_EXTENSIONS = setOf("epub", "pdf", "mobi")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilesScreen(
    onFileSelected: (File) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    // Permission MANAGE_EXTERNAL_STORAGE (API 30+)
    var hasPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager()
            else true
        )
    }

    // Relancer la vérif au retour des paramètres
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        hasPermission = if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager()
        else true
    }

    // Recheck permission quand l'écran reprend le focus
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 30) {
            hasPermission = Environment.isExternalStorageManager()
        }
    }

    // ── ÉCRAN PERMISSION ─────────────────────────────
    if (!hasPermission) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Outlined.Lock, null, modifier = Modifier.size(56.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                Spacer(Modifier.height(16.dp))
                Text("Accès au stockage requis", fontSize = 17.sp,
                    fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onBackground)
                Spacer(Modifier.height(6.dp))
                Text("Pour parcourir vos fichiers EPUB,",
                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Android nécessite une autorisation.",
                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = {
                        if (Build.VERSION.SDK_INT >= 30) {
                            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                            permissionLauncher.launch(intent)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Outlined.Settings, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Accorder l'accès dans les paramètres")
                }
            }
        }
        return
    }

    // ── EXPLORATEUR NORMAL ────────────────────────────
    var currentDir by remember {
        mutableStateOf(Environment.getExternalStorageDirectory())
    }

    val (dirs, files) = remember(currentDir, hasPermission) {
        val all = currentDir?.listFiles(
            FileFilter { it.isDirectory || it.extension.lowercase() in SUPPORTED_EXTENSIONS }
        )
            ?.sortedWith(compareBy<File> { if (it.isDirectory) 0 else 1 }.thenBy { it.name.lowercase() })
            ?: emptyList()
        all.partition { it.isDirectory }
    }

    if (currentDir == null || (dirs.isEmpty() && files.isEmpty())) {
        EmptyDir()
    } else {
        LazyColumn(
            contentPadding = PaddingValues(vertical = 4.dp)
        ) {
            items(dirs, key = { it.absolutePath }) { dir ->
                FileRow(dir.name, isDirectory = true) { currentDir = dir }
            }
            items(files, key = { it.absolutePath }) { file ->
                FileRow(file.name, isDirectory = false) { onFileSelected(file) }
            }
        }
    }
}

@Composable
private fun EmptyDir() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Outlined.Folder, null, modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f))
            Spacer(Modifier.height(12.dp))
            Text("Dossier vide ou inaccessible", fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun FileRow(name: String, isDirectory: Boolean, onClick: () -> Unit) {
    Surface(color = Color.Transparent, onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (isDirectory) Icons.Outlined.Folder else Icons.Outlined.Description,
                null, tint = if (isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.ttsActive,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(16.dp))
            Text(name, fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground,
                fontWeight = if (isDirectory) FontWeight.Medium else FontWeight.Normal,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
