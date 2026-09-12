package com.nexappra.filerecovery.di

import com.nexappra.filerecovery.data.repository.SettingsRepositoryImpl
import com.nexappra.filerecovery.domain.repository.SettingsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SettingsRepositoryModule {

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(
        implementation: SettingsRepositoryImpl,
    ): SettingsRepository
}