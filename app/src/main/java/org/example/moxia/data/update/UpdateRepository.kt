package org.example.moxia.data.update

import android.content.Context
import android.os.Build
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.example.moxia.BuildConfig
import org.example.moxia.data.DataStoreManager
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.zip.ZipFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The whole check -> download -> install chain.
 *
 * Invariants that the rest of the app relies on:
 * - a *successful* check always records its timestamp; a failed one never does, so an offline
 *   device retries on the next cold start instead of sleeping for a day;
 * - a downloaded package exists only when it is complete, a valid ZIP and (when the manifest
 *   carries one) matches its digest; anything else is deleted before returning;
 * - installing only means "the session was submitted" — the system's confirmation page and the
 *   result broadcast decide whether the app was actually replaced.
 */
class UpdateRepository(
    private val context: Context,
    private val client: OkHttpClient,
    private val fetcher: UpdateFetcher,
    private val dataStore: DataStoreManager,
    private val localVersionCode: Int = BuildConfig.VERSION_CODE,
    private val localChannel: String = updateChannel(BuildConfig.APPLICATION_ID),
    private val deviceAbis: List<String> = Build.SUPPORTED_ABIS.toList(),
    private val now: () -> Long = System::currentTimeMillis,
) {

    private val downloadMutex = Mutex()

    /**
     * Asks every source and keeps the best answer.
     *
     * The sources are independent mirrors of the same release: CNB publishes one `update.json`,
     * GitHub another. Either may be unreachable, stale or missing, so no single source decides —
     * the highest version code among the manifests that answered wins and a tie keeps the
     * preferred mirror. A source that cannot be reached is not an error by itself; only when no
     * source answers at all does the check fail.
     */
    suspend fun check(): Result<UpdateCheck> = withContext(Dispatchers.IO) {
        if (!isSelfUpdateChannel(localChannel)) return@withContext Result.success(UpdateCheck.UpToDate)
        val sources = resolveSources()
        val answers = mutableListOf<UpdateInfo>()
        var lastFailure: UpdateException? = null
        for (source in sources) {
            try {
                answers += fetcher.fetch(source)
            } catch (e: UpdateException) {
                lastFailure = e
            } catch (e: IOException) {
                lastFailure = UpdateException(UpdateFailure.NETWORK, e.message ?: "Network failed", e)
            }
        }
        if (answers.isEmpty()) {
            return@withContext Result.failure(
                lastFailure ?: UpdateException(UpdateFailure.NETWORK, "No update source answered")
            )
        }
        // Timestamp is written only here: after a source produced a readable manifest.
        dataStore.saveUpdateLastCheckAt(now())
        val best = selectBestUpdate(answers, localVersionCode, localChannel, deviceAbis)
        return@withContext Result.success(evaluate(best))
    }

    private suspend fun evaluate(best: UpdateInfo?): UpdateCheck {
        if (best == null) {
            // Same version, another channel, or nothing this device can install: not news. Clearing
            // the stored fact is what makes the badge disappear after the user upgrades.
            dataStore.clearKnownUpdate()
            return UpdateCheck.UpToDate
        }
        dataStore.saveKnownUpdate(best.versionCode, best.versionName ?: best.tag ?: "")
        return UpdateCheck.Available(best)
    }

    /**
     * A source the user selected is authoritative — no silent fallback, otherwise "which manifest
     * counts" would depend on transient connectivity. Without a selection the built-in order is
     * used, and the http hosts it may use for APKs stay empty (the defaults are https).
     */
    private suspend fun resolveSources(): List<UpdateSource> {
        val selected = UpdateSource.fromStored(dataStore.updateSourceUrl.first())
        // Without a selection the built-in order applies; with one, only that source counts.
        return selected?.let { listOf(it) } ?: UpdateSource.defaults()
    }

    /**
     * Hosts the user explicitly pointed at over plain http. `network_security_config` permits
     * cleartext globally because user-configured Memos servers live on LANs, so the update path has
     * to narrow it itself: otherwise a manifest served over http could point the package anywhere.
     * Resolved at download time so a source changed after the check still governs the download.
     */
    private suspend fun cleartextHosts(): Set<String> {
        val selected = UpdateSource.fromStored(dataStore.updateSourceUrl.first()) ?: return emptySet()
        return when (selected) {
            is UpdateSource.Manifest -> selected.url.toHttpUrlOrNull()
                ?.takeIf { !it.isHttps }
                ?.host
                ?.let { setOf(it) }
                .orEmpty()

            is UpdateSource.GithubRelease -> emptySet()
        }
    }

    private fun isAllowedDownload(url: String, cleartextHosts: Set<String>): Boolean {
        val parsed = url.toHttpUrlOrNull() ?: return false
        if (parsed.isHttps) return true
        return parsed.host in cleartextHosts
    }

    /**
     * Downloads the package this device should install. Concurrent calls queue up instead of
     * writing the same `.part` file twice.
     */
    suspend fun download(
        info: UpdateInfo,
        onProgress: (Int?) -> Unit = {},
    ): Result<File> = downloadMutex.withLock {
        withContext(Dispatchers.IO) {
            val choice = pickApk(deviceAbis, info)
                ?: return@withContext Result.failure(
                    UpdateException(UpdateFailure.NO_APK_FOR_DEVICE, "No package for this device")
                )
            if (!isAllowedDownload(choice.url, cleartextHosts())) {
                return@withContext Result.failure(
                    UpdateException(UpdateFailure.MANIFEST, "Refusing a cleartext package URL")
                )
            }
            val dir = File(context.cacheDir, CACHE_DIR).apply { mkdirs() }
            val target = File(dir, "moxia-${info.versionCode}.apk")
            // Keep the file about to be reused; drop every other package and stale partial.
            prune(dir, keep = target.name)
            if (target.isUsableApk(choice.sha256)) {
                onProgress(100)
                return@withContext Result.success(target)
            }
            target.delete()
            val part = File(dir, "${target.name}.part")
            part.delete()
            try {
                downloadTo(part, choice.url, choice.sha256, onProgress)
                if (!part.isZip()) throw UpdateException(UpdateFailure.VERIFY, "Not an APK")
                if (!part.renameTo(target)) {
                    throw UpdateException(UpdateFailure.DOWNLOAD, "Could not move the package")
                }
                onProgress(100)
                Result.success(target)
            } catch (e: UpdateException) {
                part.delete()
                target.delete()
                onProgress(null)
                Result.failure(e)
            } catch (e: IOException) {
                part.delete()
                target.delete()
                onProgress(null)
                Result.failure(UpdateException(UpdateFailure.DOWNLOAD, e.message ?: "Download failed", e))
            }
        }
    }

    private fun downloadTo(part: File, url: String, sha256: String?, onProgress: (Int?) -> Unit) {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/octet-stream")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw UpdateException(UpdateFailure.DOWNLOAD, "HTTP ${response.code}")
            }
            val body = response.body
            val total = body.contentLength()
            val digest = MessageDigest.getInstance("SHA-256")
            var written = 0L
            var reported = -1
            body.byteStream().use { input ->
                part.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        written += read
                        if (total > 0) {
                            val percent = (written * 100 / total).toInt().coerceIn(0, 100)
                            if (percent != reported) {
                                reported = percent
                                onProgress(percent)
                            }
                        }
                    }
                }
            }
            // A dropped connection ends the stream early and looks like a successful download
            // unless the lengths are compared.
            if (total > 0 && written != total) {
                throw UpdateException(UpdateFailure.DOWNLOAD, "Truncated: $written of $total bytes")
            }
            if (written == 0L) throw UpdateException(UpdateFailure.DOWNLOAD, "Empty response")
            if (sha256.isNullOrBlank()) {
                onProgress(100)
                return
            }
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            if (!actual.equals(sha256.trim(), ignoreCase = true)) {
                throw UpdateException(UpdateFailure.VERIFY, "SHA-256 mismatch")
            }
            onProgress(100)
        }
    }

    /** Hands the package to the system installer; the confirmation page is the system's. */
    fun install(apk: File): InstallOutcome = UpdateInstaller(context).install(apk)

    /**
     * Reuse is only granted to a file that would also pass a fresh download's checks — the
     * "cache exists, just use it" shortcut is what once shipped a truncated package to installers.
     */
    private fun File.isUsableApk(sha256: String?): Boolean {
        if (!isFile || length() <= 0L) return false
        if (!isZip()) return false
        if (sha256.isNullOrBlank()) return true
        return digestOf(this).equals(sha256.trim(), ignoreCase = true)
    }

    private fun digestOf(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /** Drops packages from earlier versions so the cache cannot grow one APK per release. */
    private fun prune(dir: File, keep: String) {
        dir.listFiles()?.forEach { file ->
            val isPackage = file.name.endsWith(".apk") || file.name.endsWith(".part")
            if (file.isFile && isPackage && file.name != keep) file.delete()
        }
    }

    private companion object {
        const val CACHE_DIR = "updates"
    }
}

private fun File.isZip(): Boolean = try {
    ZipFile(this).use { it.entries().hasMoreElements() }
} catch (_: IOException) {
    false
}
