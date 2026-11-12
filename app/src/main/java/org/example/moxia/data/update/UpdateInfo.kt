package org.example.moxia.data.update

import com.google.gson.annotations.SerializedName

/**
 * One release, as either a static manifest or the GitHub Releases API describes it.
 *
 * Every field but [versionCode] is optional, because the manifest is a static file: an older
 * client has to keep reading a newer manifest (and a newer client an older one). A missing field
 * must never fail parsing.
 *
 * Only [versionCode] has judgement power. Comparing version *names* is wrong ("1.10" sorts before
 * "1.9" as a string), and the canary workflow force-moves a `latest` tag on every push, so a tag is
 * not a reliable version either.
 */
data class UpdateInfo(
    @SerializedName("versionCode") val versionCode: Int = 0,
    @SerializedName("versionName") val versionName: String? = null,
    @SerializedName("tag") val tag: String? = null,
    @SerializedName("channel") val channel: String? = null,
    @SerializedName("releaseNotes") val releaseNotes: String? = null,
    @SerializedName("apkUrl") val apkUrl: Map<String, String>? = null,
    @SerializedName("apkSha256") val apkSha256: Map<String, String>? = null,
)

/**
 * ABI preference order. [pickApk] and the digest lookup walk the same list; a download that picked
 * its URL by one order and its hash by another would fail every time.
 */
val ABI_PREFERENCE: List<String> = listOf("arm64-v8a", "armeabi-v7a", "universal")

/** The single ABI/URL/hash triple a download is pinned to, so they cannot disagree. */
data class ApkChoice(val abi: String, val url: String, val sha256: String?)

/**
 * Picks the APK for [deviceAbis] from the manifest's per-ABI maps.
 *
 * A device lists every ABI it can run (`arm64-v8a` phones also advertise `armeabi-v7a`), so the
 * first preferred ABI the manifest actually ships wins. The universal APK is the last resort even
 * when the device did not advertise it: it is the one package that is known to run anywhere.
 */
fun pickApk(deviceAbis: List<String>, info: UpdateInfo): ApkChoice? {
    val urls = info.apkUrl ?: return null
    val hashes = info.apkSha256
    for (abi in ABI_PREFERENCE) {
        val url = urls[abi] ?: continue
        if (abi == "universal" || abi in deviceAbis) return ApkChoice(abi, url, hashes?.get(abi))
    }
    val universal = urls["universal"] ?: return null
    return ApkChoice("universal", universal, hashes?.get("universal"))
}

/**
 * Picks the release a device should install among the manifests that answered.
 *
 * Two rules, applied in this order:
 * - a manifest that belongs to another channel, is not newer than the installed build, or ships no
 *   package this device can run is not an update at all;
 * - among the remaining ones the highest version code wins, and a tie keeps the earlier entry:
 *   the list is ordered by preference (CNB before GitHub), so the preferred mirror keeps the tie.
 */
fun selectBestUpdate(
    candidates: List<UpdateInfo>,
    localVersionCode: Int,
    localChannel: String,
    deviceAbis: List<String>,
): UpdateInfo? {
    var best: UpdateInfo? = null
    for (candidate in candidates) {
        if (candidate.versionCode <= localVersionCode) continue
        if (!channelMatches(candidate.channel, localChannel)) continue
        if (pickApk(deviceAbis, candidate) == null) continue
        if (best == null || candidate.versionCode > best.versionCode) best = candidate
    }
    return best
}

/**
 * Version codes are derived from the release tag: `v1.2.3` -> 1002003.
 *
 * The installed base still reports versionCode 1, so any value produced by this rule is larger and
 * can replace it. Returns null for tags that are not three numeric components, which is how the
 * moving `latest` tag is rejected instead of being read as version 0.
 */
fun versionCodeFromTag(tag: String?): Int? {
    val raw = tag?.trim()?.removePrefix("v") ?: return null
    if (raw.isEmpty()) return null
    val core = raw.substringBefore('-')
    val parts = core.split('.')
    if (parts.size < 3) return null
    val major = parts[0].toIntOrNull() ?: return null
    val minor = parts[1].toIntOrNull() ?: return null
    val patch = parts[2].takeWhile { it.isDigit() }.toIntOrNull() ?: return null
    if (major !in 0..99 || minor !in 0..999 || patch !in 0..999) return null
    return major * 1_000_000 + minor * 1_000 + patch
}

/**
 * Which release channel this build belongs to. The application id suffix is the only truth:
 * the three channels install side by side and must never offer each other's builds.
 */
fun updateChannel(applicationId: String): String = when {
    applicationId.endsWith(".canary") -> "canary"
    applicationId.endsWith(".insider") -> "insider"
    applicationId.endsWith(".debug") -> "debug"
    else -> "stable"
}

/**
 * Which channels offer self-update. Canary and insider rebuild on every push, so pinning a release
 * to them would be a promise the release process cannot keep. `debug` is included only so the flow
 * can be exercised from a development build; it offers the same packages as stable.
 */
fun isSelfUpdateChannel(channel: String): Boolean = channel == "stable" || channel == "debug"

/**
 * A manifest entry for another channel is not an update for this install.
 *
 * A debug build is the one exception: it ships the same packages as stable, so it reads the
 * stable manifest and hands the stable build to the installer.
 */
fun channelMatches(remoteChannel: String?, localChannel: String): Boolean =
    remoteChannel.isNullOrBlank() ||
        remoteChannel == localChannel ||
        (localChannel == "debug" && remoteChannel == "stable")

/**
 * Cold-start throttling: at most one automatic check per day, measured from the last *successful*
 * one. A timestamp in the future means the clock was moved back, so the check runs anyway instead
 * of being skipped forever.
 */
fun shouldAutoCheckUpdate(
    lastCheckAt: Long,
    now: Long,
    intervalMs: Long = AUTO_CHECK_INTERVAL_MS,
): Boolean = lastCheckAt <= 0L || lastCheckAt > now || now - lastCheckAt >= intervalMs

const val AUTO_CHECK_INTERVAL_MS: Long = 24L * 60 * 60 * 1000

/** What a check concluded, independent of how it will be shown. */
sealed interface UpdateCheck {
    data class Available(val info: UpdateInfo) : UpdateCheck
    data object UpToDate : UpdateCheck
}

/** Why a check, download or install failed; the UI turns this into localized copy. */
enum class UpdateFailure {
    NETWORK,
    MANIFEST,
    NO_APK_FOR_DEVICE,
    DOWNLOAD,
    VERIFY,
    INSTALL_PERMISSION,
    INSTALL,
}
