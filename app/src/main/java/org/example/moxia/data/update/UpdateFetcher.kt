package org.example.moxia.data.update

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/** A failure carrying the machine-readable [failure] so the UI can localize the copy. */
class UpdateException(val failure: UpdateFailure, message: String, cause: Throwable? = null) :
    Exception(message, cause)

/**
 * Turns an [UpdateSource] into an [UpdateInfo], so everything downstream (version comparison,
 * download, install) is shared no matter where the release came from.
 */
class UpdateFetcher(
    private val client: OkHttpClient,
    private val gson: Gson,
    /** Overridable so the release-API mapping can be tested against a local server. */
    private val githubApiBase: String = DEFAULT_GITHUB_API,
) {

    suspend fun fetch(source: UpdateSource): UpdateInfo = withContext(Dispatchers.IO) {
        when (source) {
            is UpdateSource.Manifest -> fetchManifest(source.url)
            is UpdateSource.GithubRelease -> fetchLatestRelease(source.repo)
        }
    }

    /**
     * Static manifest. Unknown fields are ignored and missing ones stay null — the file outlives
     * the client that reads it, so neither side may require the other to be the same version.
     */
    private suspend fun fetchManifest(url: String): UpdateInfo {
        val body = getJson(url, accept = "application/json")
        val info = runCatching { gson.fromJson(body, UpdateInfo::class.java) }.getOrNull()
            ?: throw UpdateException(UpdateFailure.MANIFEST, "Manifest is not a release object")
        if (info.versionCode <= 0) {
            throw UpdateException(UpdateFailure.MANIFEST, "Manifest carries no version code")
        }
        return info
    }

    /**
     * GitHub Releases fallback. The release body doubles as the update notes and the tag is the
     * only version the API offers, so the version code is derived from it with the same rule the
     * release workflow uses. No checksum exists there, so verification degrades to the ZIP check.
     */
    private suspend fun fetchLatestRelease(repo: String): UpdateInfo {
        val body = getJson(
            url = "${githubApiBase.trimEnd('/')}/repos/$repo/releases/latest",
            accept = "application/vnd.github+json",
        )
        val root = runCatching { JsonParser.parseString(body).asJsonObject }.getOrNull()
            ?: throw UpdateException(UpdateFailure.MANIFEST, "Release is not a JSON object")
        val tag = root.stringOrNull("tag_name")
        val versionCode = versionCodeFromTag(tag)
            ?: throw UpdateException(UpdateFailure.MANIFEST, "Release tag is not a version: $tag")
        val urls = linkedMapOf<String, String>()
        root.getAsJsonArray("assets")?.forEach { element ->
            val asset = element.asJsonObject
            val name = asset.stringOrNull("name") ?: return@forEach
            val download = asset.stringOrNull("browser_download_url") ?: return@forEach
            abiForAssetName(name)?.let { urls[it] = download }
        }
        if (urls.isEmpty()) {
            throw UpdateException(UpdateFailure.MANIFEST, "Release $tag has no APK assets")
        }
        return UpdateInfo(
            versionCode = versionCode,
            versionName = tag?.removePrefix("v"),
            tag = tag,
            channel = "stable",
            releaseNotes = root.stringOrNull("body"),
            apkUrl = urls,
        )
    }

    /**
     * GET with bounded retries. Only transport failures and throttling/server errors are retried:
     * a 404 or a 403 will not become a 200 by asking again, and retrying it just delays the
     * fallback to the next source.
     */
    private suspend fun getJson(url: String, accept: String): String {
        var attempt = 0
        var lastError: Exception? = null
        while (attempt < MAX_ATTEMPTS) {
            attempt++
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("Accept", accept)
                    .header("User-Agent", USER_AGENT)
                    // A cached manifest would hide a release for as long as the cache lives.
                    .header("Cache-Control", "no-cache")
                    .get()
                    .build()
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) return response.body.string()
                    val code = response.code
                    if (code != 429 && code !in 500..599) {
                        throw UpdateException(UpdateFailure.MANIFEST, "HTTP $code")
                    }
                    lastError = UpdateException(UpdateFailure.NETWORK, "HTTP $code")
                    val retryAfterMs = response.header("Retry-After")?.toLongOrNull()
                        ?.times(1_000L)
                        ?.coerceAtMost(MAX_RETRY_AFTER_MS)
                    delay(retryAfterMs ?: backoffMs(attempt))
                }
            } catch (e: UpdateException) {
                throw e
            } catch (e: IOException) {
                lastError = e
                delay(backoffMs(attempt))
            }
        }
        throw UpdateException(
            UpdateFailure.NETWORK,
            lastError?.message ?: "No response",
            lastError,
        )
    }

    private fun backoffMs(attempt: Int): Long = when (attempt) {
        1 -> 1_000L
        2 -> 4_000L
        else -> 16_000L
    }

    private fun JsonObject.stringOrNull(name: String): String? =
        get(name)?.takeIf { !it.isJsonNull }?.asString

    private companion object {
        const val MAX_ATTEMPTS = 3
        const val MAX_RETRY_AFTER_MS = 60_000L
        const val USER_AGENT = "Moxia-Update"
        const val DEFAULT_GITHUB_API = "https://api.github.com"
    }
}

/**
 * Maps a release asset name to the ABI key the manifest uses. The release workflow names the
 * packages `moxia-<tag>-<abi>.apk`, so a suffix match is enough and it survives tag changes.
 */
internal fun abiForAssetName(name: String): String? = when {
    name.endsWith("-arm64-v8a.apk") -> "arm64-v8a"
    name.endsWith("-armeabi-v7a.apk") -> "armeabi-v7a"
    name.endsWith("-universal.apk") -> "universal"
    else -> null
}
