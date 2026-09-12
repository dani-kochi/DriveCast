package dev.dani.drivecast.di

import dev.dani.drivecast.data.location.DeviceLocationProvider
import dev.dani.drivecast.data.repository.DriveCastRepository
import dev.dani.drivecast.data.repository.DriveCastRepositoryImpl
import dev.dani.drivecast.domain.location.LocationProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindDriveCastRepository(impl: DriveCastRepositoryImpl): DriveCastRepository

    @Binds
    @Singleton
    abstract fun bindLocationProvider(impl: DeviceLocationProvider): LocationProvider
}
