package com.natighajiyev.alphametricsanalyticssdk.di

import android.content.Context
import androidx.room.Room
import com.natighajiyev.analytics_core.db.SpatialBufferDao
import com.natighajiyev.analytics_core.db.SpatialDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context
    ): SpatialDatabase {
        return Room.databaseBuilder(
            context,
            SpatialDatabase::class.java,
            "spatial_analytics_buffer.db"
        )
        .fallbackToDestructiveMigration() 
        .build()
    }

    @Provides
    @Singleton
    fun provideSpatialBufferDao(database: SpatialDatabase): SpatialBufferDao {
        return database.spatialBufferDao()
    }
}