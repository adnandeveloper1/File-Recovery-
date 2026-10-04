package com.nexappra.filerecovery.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.nexappra.filerecovery.data.repository.AndroidStorageRepository
import com.nexappra.filerecovery.data.repository.DataStoreAppPreferencesRepository
import com.nexappra.filerecovery.data.repository.MediaStorePhotoScanRepository
import com.nexappra.filerecovery.data.repository.MediaStoreRecoveryScanRepository
import com.nexappra.filerecovery.domain.repository.AppPreferencesRepository
import com.nexappra.filerecovery.domain.repository.PhotoScanRepository
import com.nexappra.filerecovery.domain.repository.RecoveryScanRepository
import com.nexappra.filerecovery.domain.repository.StorageRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindPremiumRepository(repository: com.nexappra.filerecovery.data.billing.PlayBillingRepository): com.nexappra.filerecovery.domain.repository.PremiumRepository

    @Binds
    @Singleton
    abstract fun bindStorageRepository(
        storageRepository: AndroidStorageRepository,
    ): StorageRepository

    @Binds
    @Singleton
    abstract fun bindAppPreferencesRepository(
        appPreferencesRepository: DataStoreAppPreferencesRepository,
    ): AppPreferencesRepository

    @Binds
    @Singleton
    abstract fun bindPhotoScanRepository(
        photoScanRepository: MediaStorePhotoScanRepository,
    ): PhotoScanRepository

    @Binds
    @Singleton
    abstract fun bindRecoveryScanRepository(
        recoveryScanRepository: MediaStoreRecoveryScanRepository,
    ): RecoveryScanRepository
}

@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {
    @Provides
    @Singleton
    fun providePreferencesDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> = PreferenceDataStoreFactory.create {
        context.preferencesDataStoreFile("file_recovery_preferences")
    }
}
