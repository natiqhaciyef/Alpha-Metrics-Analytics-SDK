package com.natighajiyev.analytics_core.di

import com.natighajiyev.analytics_core.db.RoomBufferRepository
import com.natighajiyev.analytics_core.engine.AnalyticsEngine
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object EngineModule {

    @Provides
    @Singleton
    fun provideAnalyticsEngine(
        storageRepository: RoomBufferRepository
    ): AnalyticsEngine = AnalyticsEngine(storageRepository = storageRepository)
}