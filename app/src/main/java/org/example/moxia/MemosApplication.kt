package org.example.moxia

import android.app.Application
import android.os.Build
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import coil3.svg.SvgDecoder
import kotlinx.coroutines.launch
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import org.example.moxia.data.DataStoreManager
import org.example.moxia.data.sync.SyncWorkScheduler
import org.example.moxia.di.appModule
import org.example.moxia.di.networkModule
import org.example.moxia.di.updateModule
import org.example.moxia.di.viewModelModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class MemosApplication : Application(), SingletonImageLoader.Factory {

    /**
     * Offline attachment cache manager, resolved lazily from Koin
     * (usable from deep composables without DI plumbing).
     */
    val attachmentCacheManager: org.example.moxia.data.media.AttachmentCacheManager
        get() = org.koin.core.context.GlobalContext.get()
            .get<org.example.moxia.data.media.AttachmentCacheManager>()

    override fun onCreate() {
        super.onCreate()
        instance = this
        org.example.moxia.data.StartupMirror.load(this)
        org.example.moxia.data.backup.BackupCoordinator.beginStartupRecovery()

        startKoin {
            androidLogger(org.koin.core.logger.Level.ERROR)
            androidContext(this@MemosApplication)
            modules(appModule, networkModule, updateModule, viewModelModule)
        }

        // Silent, throttled (24h) check that only refreshes the persisted "newer version exists"
        // fact; it never downloads and never opens a screen.
        org.example.moxia.data.update.UpdateHub.controller.maybeAutoCheck()

        registerActivityLifecycleCallbacks(object : android.app.Application.ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: android.app.Activity) =
                org.example.moxia.data.AppVisibility.onActivityStarted()

            override fun onActivityStopped(activity: android.app.Activity) =
                org.example.moxia.data.AppVisibility.onActivityStopped()

            override fun onActivityCreated(activity: android.app.Activity, savedInstanceState: android.os.Bundle?) = Unit
            override fun onActivityResumed(activity: android.app.Activity) = Unit
            override fun onActivityPaused(activity: android.app.Activity) = Unit
            override fun onActivitySaveInstanceState(activity: android.app.Activity, outState: android.os.Bundle) = Unit
            override fun onActivityDestroyed(activity: android.app.Activity) = Unit
        })

        // Complete incoming or interrupted restores before creating sessions or scheduling work.
        val startupScope = kotlinx.coroutines.CoroutineScope(
            kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO
        )

        // Keep the long-press entries in sync with the strings and icons this build shipped.
        startupScope.launch { AppShortcuts.publish(this@MemosApplication) }

        startupScope.launch {
            // Resolved on the IO thread: building the service and its graph used to sit between
            // the tap and the first frame.
            val backup = org.koin.core.context.GlobalContext.get().get<org.example.moxia.data.backup.BackupService>()
            try {
                backup.recoverInterruptedRestore().onFailure {
                    org.example.moxia.data.backup.BackupCoordinator.recoveryError.value = "Restore was interrupted. Retry to finish restoring your data."
                }
                if (org.example.moxia.data.backup.BackupCoordinator.recoveryError.value == null) {
                    backup.restoreNativeIfPresent().onFailure {
                        org.example.moxia.data.backup.BackupCoordinator.recoveryError.value = getString(R.string.backup_native_recovery_failed)
                    }
                }
            } finally { org.example.moxia.data.backup.BackupCoordinator.finishStartupRecovery() }

            // Re-arm durable outbox work only after startup recovery finishes.
            if (org.example.moxia.data.backup.BackupCoordinator.recoveryError.value != null) return@launch
            val koin = org.koin.core.context.GlobalContext.get()
            val scheduler = koin.get<SyncWorkScheduler>()
            koin.get<DataStoreManager>().getAccounts().forEach { scheduler.schedule(it.id) }
        }
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        val dispatcher = Dispatcher().apply {
            maxRequests = 5
            maxRequestsPerHost = 5
        }

        val okHttpClient = OkHttpClient.Builder()
            .dispatcher(dispatcher)
            .build()

        return ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { okHttpClient }))
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    add(AnimatedImageDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
                add(SvgDecoder.Factory())
            }
            .crossfade(true)
            .build()
    }

    companion object {
        lateinit var instance: MemosApplication
            private set
    }
}
