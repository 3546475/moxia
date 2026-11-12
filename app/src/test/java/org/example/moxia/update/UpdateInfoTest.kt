package org.example.moxia.update

import com.google.gson.Gson
import org.example.moxia.data.update.ABI_PREFERENCE
import org.example.moxia.data.update.AUTO_CHECK_INTERVAL_MS
import org.example.moxia.data.update.UpdateInfo
import org.example.moxia.data.update.abiForAssetName
import org.example.moxia.data.update.channelMatches
import org.example.moxia.data.update.isSelfUpdateChannel
import org.example.moxia.data.update.pickApk
import org.example.moxia.data.update.shouldAutoCheckUpdate
import org.example.moxia.data.update.updateChannel
import org.example.moxia.data.update.versionCodeFromTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure update logic: no device, no network, no Android framework. */
class UpdateInfoTest {

    private val gson = Gson()

    // --- pickApk ---------------------------------------------------------------------------

    @Test
    fun `arm64 device prefers the arm64 package`() {
        val info = manifest("arm64-v8a", "armeabi-v7a", "universal")
        val choice = pickApk(listOf("arm64-v8a", "armeabi-v7a"), info)
        assertEquals("arm64-v8a", choice?.abi)
    }

    @Test
    fun `32-bit device falls back to armeabi-v7a when arm64 is also published`() {
        val info = manifest("arm64-v8a", "armeabi-v7a", "universal")
        val choice = pickApk(listOf("armeabi-v7a"), info)
        assertEquals("armeabi-v7a", choice?.abi)
    }

    @Test
    fun `missing preferred package falls back to universal`() {
        val info = manifest("universal")
        assertEquals("universal", pickApk(listOf("arm64-v8a"), info)?.abi)
    }

    @Test
    fun `universal wins even when the device did not advertise it`() {
        val info = manifest("universal")
        assertEquals("universal", pickApk(emptyList(), info)?.abi)
    }

    @Test
    fun `no package at all yields no choice`() {
        assertNull(pickApk(listOf("arm64-v8a"), UpdateInfo(versionCode = 2)))
    }

    @Test
    fun `digest is taken from the same ABI that supplied the URL`() {
        val info = UpdateInfo(
            versionCode = 2,
            apkUrl = mapOf("arm64-v8a" to "https://x/a.apk", "universal" to "https://x/u.apk"),
            apkSha256 = mapOf("arm64-v8a" to "aaa", "universal" to "uuu"),
        )
        val choice = pickApk(listOf("arm64-v8a"), info)
        assertEquals("https://x/a.apk", choice?.url)
        assertEquals("aaa", choice?.sha256)
    }

    @Test
    fun `preference order covers every ABI the manifest may publish`() {
        assertEquals(listOf("arm64-v8a", "armeabi-v7a", "universal"), ABI_PREFERENCE)
    }

    // --- versionCodeFromTag ----------------------------------------------------------------

    @Test
    fun `three-component tags map monotonically`() {
        assertEquals(1_000_000, versionCodeFromTag("v1.0.0"))
        assertEquals(1_002_003, versionCodeFromTag("v1.2.3"))
        assertEquals(2_000_000, versionCodeFromTag("v2.0.0"))
        assertTrue(versionCodeFromTag("v1.2.3")!! > versionCodeFromTag("v1.2.2")!!)
    }

    @Test
    fun `insider tags keep the numeric part`() {
        assertEquals(1_002_003, versionCodeFromTag("v1.2.3.i"))
    }

    @Test
    fun `a moving or non-version tag is rejected instead of being read as zero`() {
        assertNull(versionCodeFromTag("latest"))
        assertNull(versionCodeFromTag("v1.2"))
        assertNull(versionCodeFromTag(""))
        assertNull(versionCodeFromTag(null))
        assertNull(versionCodeFromTag("vX.Y.Z"))
    }

    @Test
    fun `out of range components are rejected rather than overflowing`() {
        assertNull(versionCodeFromTag("v100.0.0"))
        assertNull(versionCodeFromTag("v1.1000.0"))
        assertNull(versionCodeFromTag("v1.0.1000"))
    }

    // --- channels --------------------------------------------------------------------------

    @Test
    fun `application id suffix decides the channel`() {
        assertEquals("stable", updateChannel("org.example.moxia"))
        assertEquals("canary", updateChannel("org.example.moxia.canary"))
        assertEquals("insider", updateChannel("org.example.moxia.insider"))
        assertEquals("debug", updateChannel("org.example.moxia.debug"))
    }

    @Test
    fun `only stable and debug offer self update`() {
        assertTrue(isSelfUpdateChannel("stable"))
        // Debug is included so the flow can be exercised from a development build.
        assertTrue(isSelfUpdateChannel("debug"))
        assertFalse(isSelfUpdateChannel("canary"))
        assertFalse(isSelfUpdateChannel("insider"))
    }

    @Test
    fun `a manifest without a channel is accepted and a mismatched one is not`() {
        assertTrue(channelMatches(null, "stable"))
        assertTrue(channelMatches("", "stable"))
        assertTrue(channelMatches("stable", "stable"))
        assertFalse(channelMatches("insider", "stable"))
    }

    @Test
    fun `a debug build consumes the stable manifest but not the other way round`() {
        assertTrue(channelMatches("stable", "debug"))
        assertFalse(channelMatches("debug", "stable"))
    }

    // --- throttling ------------------------------------------------------------------------

    @Test
    fun `first check runs immediately`() {
        assertTrue(shouldAutoCheckUpdate(0L, 1_000L))
    }

    @Test
    fun `a check inside the interval is skipped`() {
        val now = 10 * AUTO_CHECK_INTERVAL_MS
        assertFalse(shouldAutoCheckUpdate(now - 1_000L, now))
    }

    @Test
    fun `a check outside the interval runs again`() {
        val now = 10 * AUTO_CHECK_INTERVAL_MS
        assertTrue(shouldAutoCheckUpdate(now - AUTO_CHECK_INTERVAL_MS, now))
    }

    @Test
    fun `a timestamp in the future still checks instead of sleeping forever`() {
        val now = 1_000L
        assertTrue(shouldAutoCheckUpdate(now + AUTO_CHECK_INTERVAL_MS, now))
    }

    // --- parsing ---------------------------------------------------------------------------

    @Test
    fun `a manifest without digests still parses`() {
        val json = """
            {
              "versionCode": 1002003,
              "versionName": "1.2.3",
              "releaseNotes": "notes",
              "apkUrl": { "arm64-v8a": "https://example.com/a.apk" }
            }
        """.trimIndent()
        val info = gson.fromJson(json, UpdateInfo::class.java)
        assertEquals(1_002_003, info.versionCode)
        assertEquals("1.2.3", info.versionName)
        assertNull(info.apkSha256)
        assertNotNull(pickApk(listOf("arm64-v8a"), info))
    }

    @Test
    fun `unknown fields are ignored so a newer manifest still parses`() {
        val json = """
            {
              "versionCode": 7,
              "schemaVersion": 1,
              "minSupportedVersionCode": 3,
              "signature": "abc",
              "apkUrl": { "universal": "https://example.com/u.apk" }
            }
        """.trimIndent()
        val info = gson.fromJson(json, UpdateInfo::class.java)
        assertEquals(7, info.versionCode)
        assertEquals("https://example.com/u.apk", info.apkUrl?.get("universal"))
    }

    @Test
    fun `an empty manifest parses to the default rather than throwing`() {
        val info = gson.fromJson("{}", UpdateInfo::class.java)
        assertEquals(0, info.versionCode)
        assertNull(pickApk(listOf("arm64-v8a"), info))
    }

    /**
     * Verbatim output of `scripts/gen_update_json.py`, so the release script and the client cannot
     * drift apart without a failing test.
     */
    @Test
    fun `the manifest produced by the release script is understood`() {
        val json = """
            {
              "schemaVersion": 1,
              "channel": "stable",
              "versionCode": 1002003,
              "versionName": "1.2.3",
              "tag": "v1.2.3",
              "publishedAt": null,
              "releaseNotes": "Moxia v1.2.3 is available.",
              "apkUrl": {
                "arm64-v8a": "https://example.com/releases/v1.2.3/moxia-v1.2.3-arm64-v8a.apk",
                "armeabi-v7a": "https://example.com/releases/v1.2.3/moxia-v1.2.3-armeabi-v7a.apk",
                "universal": "https://example.com/releases/v1.2.3/moxia-v1.2.3-universal.apk"
              },
              "apkSha256": {
                "arm64-v8a": "6178f5af611cf83bfa403b51233dfdb7f27f160407535942f081ad1505c04acc",
                "armeabi-v7a": "ceaeb409a01eb49af3db93972de96ce83f88fdddfcc90be228d8edb58f6f2324",
                "universal": "c9d721b036615930e4dc239dc9a8f6e91b614e6177b5026111df65b70fff9104"
              },
              "apkSize": {
                "arm64-v8a": 55030596,
                "armeabi-v7a": 50831230,
                "universal": 96638379
              }
            }
        """.trimIndent()
        val info = gson.fromJson(json, UpdateInfo::class.java)
        assertEquals(1_002_003, info.versionCode)
        assertEquals("1.2.3", info.versionName)
        assertEquals("stable", info.channel)
        val choice = pickApk(listOf("arm64-v8a", "armeabi-v7a"), info)
        assertEquals("arm64-v8a", choice?.abi)
        assertEquals(
            "6178f5af611cf83bfa403b51233dfdb7f27f160407535942f081ad1505c04acc",
            choice?.sha256,
        )
    }

    // --- asset names -----------------------------------------------------------------------

    @Test
    fun `release asset names map to ABI keys`() {
        assertEquals("arm64-v8a", abiForAssetName("moxia-v1.2.3-arm64-v8a.apk"))
        assertEquals("armeabi-v7a", abiForAssetName("moxia-v1.2.3-armeabi-v7a.apk"))
        assertEquals("universal", abiForAssetName("moxia-v1.2.3-universal.apk"))
        assertNull(abiForAssetName("moxia-v1.2.3.apk"))
        assertNull(abiForAssetName("checksums.txt"))
    }

    private fun manifest(vararg abis: String): UpdateInfo = UpdateInfo(
        versionCode = 2,
        apkUrl = abis.associateWith { "https://example.com/$it.apk" },
    )
}
