package org.example.moxia.update

import org.example.moxia.data.update.UpdateInfo
import org.example.moxia.data.update.UpdateSource
import org.example.moxia.data.update.selectBestUpdate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/** Which release wins when more than one mirror answered. */
class UpdateSelectionTest {

    private fun manifest(
        versionCode: Int,
        channel: String = "stable",
        abis: List<String> = listOf("arm64-v8a"),
    ) = UpdateInfo(
        versionCode = versionCode,
        versionName = "1.0.0",
        tag = "v1.0.0",
        channel = channel,
        apkUrl = abis.associateWith { "https://example.invalid/$it.apk" },
        apkSha256 = abis.associateWith { "0".repeat(64) },
    )

    @Test
    fun `the preferred mirror keeps a tie`() {
        val cnb = manifest(1_002_003)
        val github = manifest(1_002_003)
        val best = selectBestUpdate(listOf(cnb, github), 1_000_000, "stable", listOf("arm64-v8a"))
        assertSame(cnb, best)
    }

    @Test
    fun `a newer mirror wins over a stale one`() {
        // The CNB pipeline publishes on its own; when it lags behind, the GitHub copy is newer.
        val cnb = manifest(1_002_003)
        val github = manifest(1_003_000)
        val best = selectBestUpdate(listOf(cnb, github), 1_000_000, "stable", listOf("arm64-v8a"))
        assertEquals(1_003_000, best?.versionCode)
    }

    @Test
    fun `a manifest of another channel is ignored`() {
        val insider = manifest(1_002_003, channel = "insider")
        val stable = manifest(1_002_003)
        val best = selectBestUpdate(listOf(insider, stable), 1_000_000, "stable", listOf("arm64-v8a"))
        assertSame(stable, best)
    }

    @Test
    fun `a manifest that is not newer than the installed build is ignored`() {
        val best = selectBestUpdate(
            listOf(manifest(1_000_000), manifest(999_999)),
            1_000_000,
            "stable",
            listOf("arm64-v8a"),
        )
        assertNull(best)
    }

    @Test
    fun `a manifest without a package for this device is ignored`() {
        val best = selectBestUpdate(
            listOf(manifest(1_002_003, abis = listOf("armeabi-v7a"))),
            1_000_000,
            "stable",
            listOf("arm64-v8a"),
        )
        assertNull(best)
    }

    @Test
    fun `the built-in sources are the two mirrors, CNB first`() {
        val sources = UpdateSource.defaults()
        assertEquals(
            listOf(
                UpdateSource.Manifest(UpdateSource.DEFAULT_MANIFEST_URL),
                UpdateSource.Manifest(UpdateSource.GITHUB_MANIFEST_URL),
            ),
            sources,
        )
        assertEquals("cnb.cool", sources.first().id.substringAfter("://").substringBefore('/'))
        assertEquals("raw.githubusercontent.com", sources.last().id.substringAfter("://").substringBefore('/'))
    }
}
