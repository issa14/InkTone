package com.inktone.infrastructure.worker.di

import android.content.Context
import androidx.work.WorkManager
import com.inktone.domain.service.ImportScheduler
import com.inktone.infrastructure.worker.WorkManagerImportScheduler
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object WorkManagerModule {
    @Provides
    @Singleton
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager = WorkManager.getInstance(context)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class ImportSchedulerModule {
    @Binds
    @Singleton
    abstract fun bindImportScheduler(impl: WorkManagerImportScheduler): ImportScheduler
}
