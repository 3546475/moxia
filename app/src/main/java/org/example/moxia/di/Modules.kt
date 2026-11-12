package org.example.moxia.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import okhttp3.OkHttpClient
import org.example.moxia.data.DataStoreManager
import org.example.moxia.data.DraftManager
import org.example.moxia.data.cache.MemoCacheDatabase
import org.example.moxia.data.cache.MemoCacheRepository
import org.example.moxia.data.audit.AuditDatabase
import org.example.moxia.data.audit.LocalRecoveryService
import org.example.moxia.data.audit.SyncAuditLogger
import org.example.moxia.data.media.AttachmentCacheManager
import org.example.moxia.data.media.AttachmentUploadDao
import org.example.moxia.data.media.AttachmentUploadQueue
import org.example.moxia.data.media.CachedAttachmentDao
import org.example.moxia.data.network.ConnectivityObserver
import org.example.moxia.data.offline.AttachmentCacheStore
import org.example.moxia.data.offline.NotificationCacheStore
import org.example.moxia.data.offline.NotificationsSnapshotData
import org.example.moxia.data.offline.SessionCacheStore
import org.example.moxia.data.offline.SessionSnapshotData
import org.example.moxia.data.store.DataStoreSnapshotStore
import org.example.moxia.data.store.RoomAttachmentMetaStore
import org.example.moxia.data.store.SnapshotStore
import org.example.moxia.data.sync.SyncRepository
import org.example.moxia.data.sync.SyncWorkScheduler
import org.example.moxia.data.sync.WorkManagerSyncWorkScheduler
import org.example.moxia.viewmodel.MemosViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import org.koin.core.qualifier.named
import java.util.concurrent.TimeUnit

val appModule = module {
    // DataStore
    single<DataStore<Preferences>> {
        PreferenceDataStoreFactory.create(
            produceFile = { androidContext().preferencesDataStoreFile("settings") }
        )
    }

    single { DataStoreManager(get(), onAccountsChanged = { android.app.backup.BackupManager(androidContext()).dataChanged() }) }

    single { DraftManager(androidContext()) }

    // Room
    single { MemoCacheDatabase.getInstance(androidContext()) }
    single<org.example.moxia.data.cache.MemoDao> { org.example.moxia.data.backup.GuardedMemoDao(get<MemoCacheDatabase>().memoDao()) }
    single { get<MemoCacheDatabase>().pendingOpDao() }
    single<CachedAttachmentDao> { org.example.moxia.data.backup.GuardedAttachmentDao(get<MemoCacheDatabase>().cachedAttachmentDao()) }
    single { get<MemoCacheDatabase>().attachmentUploadDao() }
    single<org.example.moxia.data.media.CachedAttachmentMetaDao> { org.example.moxia.data.backup.GuardedAttachmentMetaDao(get<MemoCacheDatabase>().cachedAttachmentMetaDao()) }
    // The audit log lives in its own database so it survives cache-DB corruption.
    single { AuditDatabase.getInstance(androidContext()) }
    single { get<AuditDatabase>().syncAuditDao() }
    single { MemoCacheRepository(get()) }
    single { SyncRepository(get(), get<MemoCacheDatabase>()) }
    single { SyncAuditLogger(get()) }
    single { AttachmentUploadQueue(androidContext(), get(), get()) }
    single { org.example.moxia.data.backup.BackupService(androidContext(), get(), get(), get()) }
    single { LocalRecoveryService(get(), get()) }
    single<SyncWorkScheduler> { WorkManagerSyncWorkScheduler(androidContext()) }

    // Network state monitoring
    single { ConnectivityObserver(androidContext()) }

    // Offline attachment downloads
    single {
        AttachmentCacheManager(
            context = androidContext(),
            dao = get<CachedAttachmentDao>(),
            okHttpClient = get(),
            dataStoreManager = get(),
            isWifiProvider = { get<ConnectivityObserver>().isWifi.value }
        )
    }

    // --- Offline-first cache layers ---
    // Storage SPI adapters (data/store/); business code consumes the
    // facades below, never these adapters directly.
    single { RoomAttachmentMetaStore(get()) }
    single<SnapshotStore<SessionSnapshotData>>(named("session")) {
        DataStoreSnapshotStore(
            dataStoreManager = get(),
            domain = "session",
            type = SessionSnapshotData::class.java
        )
    }
    single<SnapshotStore<NotificationsSnapshotData>>(named("notifications")) {
        DataStoreSnapshotStore(
            dataStoreManager = get(),
            domain = "notifications",
            type = NotificationsSnapshotData::class.java
        )
    }

    // Cache-abstraction facades (data/offline/)
    single { SessionCacheStore(get(named("session"))) }
    single { NotificationCacheStore(get(named("notifications"))) }
    single { AttachmentCacheStore(get(), get(), get()) }
}

val networkModule = module {
    single {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}

val updateModule = module {
    // Self-update shares the app's HTTP client (proxy and TLS settings stay in one place) and its
    // DataStore. The controller is application-scoped so a download outlives the dialog.
    single { com.google.gson.Gson() }
    single { org.example.moxia.data.update.UpdateFetcher(get(), get()) }
    single {
        org.example.moxia.data.update.UpdateRepository(
            context = androidContext(),
            client = get(),
            fetcher = get(),
            dataStore = get(),
        )
    }
    single {
        org.example.moxia.data.update.UpdateController(
            repository = get(),
            dataStore = get(),
        )
    }
}

val viewModelModule = module {
    // One instance for the whole process: the composer, the settings overlays and the main feed
    // all read and write the same lists, so an optimistic write is visible everywhere at once.
    single {
        MemosViewModel(
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get()
        )
    }
}
