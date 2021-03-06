package org.stepic.droid.di.storage

import android.content.Context
import dagger.BindsInstance
import dagger.Component
import org.stepic.droid.features.deadlines.storage.dao.DeadlinesBannerDao
import org.stepic.droid.features.deadlines.storage.operations.DeadlinesRecordOperations
import org.stepic.droid.storage.operations.DatabaseFacade

@Component(modules = [StorageModule::class])
@StorageSingleton
interface StorageComponent {

    @Component.Builder
    interface Builder {
        fun build(): StorageComponent

        @BindsInstance
        fun context(context: Context): Builder
    }

    val databaseFacade: DatabaseFacade

    val deadlinesRecordOperations: DeadlinesRecordOperations
    val deadlinesBannerDao: DeadlinesBannerDao
}
