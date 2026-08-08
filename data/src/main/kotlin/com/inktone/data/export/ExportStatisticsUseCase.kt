package com.inktone.data.export

import android.content.Context
import com.inktone.domain.service.ExportFormat
import com.inktone.domain.service.StatisticsExportService
import com.inktone.infrastructure.database.dao.ReadingSessionDao
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExportStatisticsUseCase @Inject constructor(
    private val readingSessionDao: ReadingSessionDao,
    @ApplicationContext private val context: Context,
) : StatisticsExportService {

    override suspend fun exportCsv(): File = export(ExportFormat.CSV)
    override suspend fun exportJson(): File = export(ExportFormat.JSON)

    private suspend fun export(format: ExportFormat): File = withContext(Dispatchers.IO) {
        val sessions = readingSessionDao.getAll()
        val dir = File(context.cacheDir, "exports")
        dir.mkdirs()

        // Nettoie les fichiers d'export de plus de 24h (évite l'accumulation)
        val cutoff = System.currentTimeMillis() - 86_400_000L
        dir.listFiles()?.forEach { f ->
            if (f.lastModified() < cutoff) f.delete()
        }

        val timestamp = System.currentTimeMillis()
        val file = File(dir, "inktone_statistics_$timestamp.${format.extension}")

        val content = when (format) {
            ExportFormat.CSV -> buildString {
                appendLine("id,publicationId,startTimestamp,endTimestamp,visualDurationMs,ttsDurationMs")
                sessions.forEach { s ->
                    appendLine("${s.id.escapeCsv()},${s.publicationId.escapeCsv()},${s.startedAt},${s.endedAt ?: ""},${s.visualDurationMs},${s.ttsDurationMs}")
                }
            }
            ExportFormat.JSON -> {
                val array = JSONArray()
                sessions.forEach { s ->
                    array.put(JSONObject().apply {
                        put("id", s.id)
                        put("publicationId", s.publicationId)
                        put("startTimestamp", s.startedAt)
                        put("endTimestamp", s.endedAt ?: JSONObject.NULL)
                        put("visualDurationMs", s.visualDurationMs)
                        put("ttsDurationMs", s.ttsDurationMs)
                    })
                }
                array.toString(2)
            }
        }
        file.writeText(content)
        file
    }

    /** Échappe une valeur CSV : guillemets si contient virgule, guillemet ou saut de ligne. */
    private fun String.escapeCsv(): String =
        if (any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"${replace("\"", "\"\"")}\""
        else this
}
