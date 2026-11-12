package eu.kanade.tachiyomi.extension

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkerParameters
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.extension.anime.AnimeExtensionManager
import eu.kanade.tachiyomi.extension.anime.api.AnimeExtensionApi
import eu.kanade.tachiyomi.extension.manga.MangaExtensionManager
import eu.kanade.tachiyomi.extension.manga.api.MangaExtensionApi
import eu.kanade.tachiyomi.util.system.workManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.minutes

/**
 * Fork: periodically checks every installed anime/manga extension for updates and installs them
 * in the background. Anything that could not be installed silently (e.g. the system installer
 * wants user confirmation) falls back to the usual "updates available" notification.
 */
class ExtensionAutoUpdateJob(private val context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        if (!Injekt.get<SourcePreferences>().autoUpdateExtensions().get()) return Result.success()

        return try {
            updateAnime()
            updateManga()
            Result.success()
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Extension auto-update failed" }
            Result.retry()
        }
    }

    private suspend fun updateAnime() {
        val manager = Injekt.get<AnimeExtensionManager>()
        manager.findAvailableExtensions()
        val pending = AnimeExtensionApi().checkForUpdates(context, notify = false).orEmpty()
        val failed = pending.filterNot { ext -> install(ext.name, manager.updateExtension(ext)) }
        if (failed.isNotEmpty()) {
            ExtensionUpdateNotifier(context).promptUpdates(names = failed.map { it.name }, anime = true)
        }
    }

    private suspend fun updateManga() {
        val manager = Injekt.get<MangaExtensionManager>()
        manager.findAvailableExtensions()
        val pending = MangaExtensionApi().checkForUpdates(context, notify = false).orEmpty()
        val failed = pending.filterNot { ext -> install(ext.name, manager.updateExtension(ext)) }
        if (failed.isNotEmpty()) {
            ExtensionUpdateNotifier(context).promptUpdates(failed.map { it.name })
        }
    }

    /** @return true when the extension ended up installed. */
    private suspend fun install(name: String, steps: Flow<InstallStep>): Boolean {
        val result = withTimeoutOrNull(INSTALL_TIMEOUT) { steps.first { it.isCompleted() } }
        logcat { "Extension auto-update: $name -> $result" }
        return result == InstallStep.Installed
    }

    companion object {
        private const val TAG = "ExtensionAutoUpdate"
        private const val INTERVAL_HOURS = 6L
        private val INSTALL_TIMEOUT = 5.minutes

        fun setupTask(context: Context, enabled: Boolean? = null) {
            val on = enabled ?: Injekt.get<SourcePreferences>().autoUpdateExtensions().get()
            if (on) {
                val request = PeriodicWorkRequestBuilder<ExtensionAutoUpdateJob>(INTERVAL_HOURS, TimeUnit.HOURS)
                    .addTag(TAG)
                    .setConstraints(
                        Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .setRequiresBatteryNotLow(true)
                            .build(),
                    )
                    .build()
                context.workManager.enqueueUniquePeriodicWork(TAG, ExistingPeriodicWorkPolicy.UPDATE, request)
            } else {
                context.workManager.cancelUniqueWork(TAG)
            }
        }
    }
}
