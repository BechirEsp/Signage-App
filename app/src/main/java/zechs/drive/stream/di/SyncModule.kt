package zechs.drive.stream.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import zechs.drive.stream.data.sync.CloudMediaProvider
import zechs.drive.stream.data.sync.DriveMediaProvider
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SyncModule {

    @Binds
    @Singleton
    abstract fun bindCloudMediaProvider(
        provider: DriveMediaProvider
    ): CloudMediaProvider
}
