package com.inktone.core.testing.fake

import com.inktone.domain.service.StatisticsExportService
import java.io.File

class FakeStatisticsExportService : StatisticsExportService {
    override suspend fun exportCsv(): File =
        File.createTempFile("test-export", ".csv")

    override suspend fun exportJson(): File =
        File.createTempFile("test-export", ".json")
}
