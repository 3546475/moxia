package org.example.moxia.update

import com.google.gson.Gson
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.example.moxia.data.update.UpdateException
import org.example.moxia.data.update.UpdateFailure
import org.example.moxia.data.update.UpdateFetcher
import org.example.moxia.data.update.UpdateSource
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.net.InetSocketAddress

/**
 * Exercises the real HTTP path against a local server: the static manifest source, the GitHub
 * releases fallback, and the failures that must be reported instead of guessed at. No device and
 * no internet.
 */
class UpdateFetcherTest {

    private lateinit var server: HttpServer
    private lateinit var baseUrl: String
    private val routes = mutableMapOf<String, Pair<Int, String>>()
    private val fetcher by lazy {
        UpdateFetcher(OkHttpClient(), Gson(), githubApiBase = baseUrl)
    }

    @Before
    fun start() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange: HttpExchange ->
            val entry = routes[exchange.requestURI.path]
            if (entry == null) {
                respond(exchange, 404, "not found")
            } else {
                respond(exchange, entry.first, entry.second)
            }
        }
        server.start()
        baseUrl = "http://127.0.0.1:${server.address.port}"
    }

    @After
    fun stop() {
        server.stop(0)
    }

    @Test
    fun `a static manifest is read as a release`() = runTest {
        route("/update.json", MANIFEST)
        val info = fetcher.fetch(UpdateSource.Manifest("$baseUrl/update.json"))
        assertEquals(1_002_003, info.versionCode)
        assertEquals("1.2.3", info.versionName)
        assertEquals("stable", info.channel)
        assertEquals("notes", info.releaseNotes)
        assertEquals("https://e/a.apk", info.apkUrl?.get("arm64-v8a"))
        assertEquals("hash-a", info.apkSha256?.get("arm64-v8a"))
    }

    @Test
    fun `a manifest without digests is still usable`() = runTest {
        route("/update.json", """{"versionCode": 7, "apkUrl": {"universal": "https://e/u.apk"}}""")
        val info = fetcher.fetch(UpdateSource.Manifest("$baseUrl/update.json"))
        assertEquals(7, info.versionCode)
        assertNull(info.apkSha256)
    }

    @Test
    fun `a manifest with no version code is rejected rather than treated as current`() = runTest {
        route("/update.json", """{"versionName": "1.2.3"}""")
        assertFailsWith(UpdateFailure.MANIFEST) {
            fetcher.fetch(UpdateSource.Manifest("$baseUrl/update.json"))
        }
    }

    @Test
    fun `a 404 is a failure, not an empty release`() = runTest {
        assertFailsWith(UpdateFailure.MANIFEST) {
            fetcher.fetch(UpdateSource.Manifest("$baseUrl/missing.json"))
        }
    }

    @Test
    fun `a release from the GitHub API is normalised into the same shape`() = runTest {
        route("/repos/owner/repo/releases/latest", GITHUB_RELEASE)
        val info = fetcher.fetch(UpdateSource.GithubRelease("owner/repo"))
        // The version code comes from the tag with the same rule the release workflow uses,
        // because that API offers no version code at all.
        assertEquals(1_002_003, info.versionCode)
        assertEquals("1.2.3", info.versionName)
        assertEquals("notes", info.releaseNotes)
        assertNotNull(info.apkUrl?.get("arm64-v8a"))
        assertNotNull(info.apkUrl?.get("universal"))
        // No checksum exists on that API, so verification degrades to the archive check.
        assertNull(info.apkSha256)
    }

    @Test
    fun `a release without APK assets is a failure`() = runTest {
        route(
            "/repos/owner/repo/releases/latest",
            """{"tag_name":"v1.2.3","body":"n","assets":[
                 {"name":"checksums.txt","browser_download_url":"https://e/c.txt"}]}""",
        )
        assertFailsWith(UpdateFailure.MANIFEST) {
            fetcher.fetch(UpdateSource.GithubRelease("owner/repo"))
        }
    }

    @Test
    fun `a release whose tag is not a version is a failure rather than version zero`() = runTest {
        route("/repos/owner/repo/releases/latest", """{"tag_name":"latest","assets":[]}""")
        assertFailsWith(UpdateFailure.MANIFEST) {
            fetcher.fetch(UpdateSource.GithubRelease("owner/repo"))
        }
    }

    private fun route(path: String, body: String, status: Int = 200) {
        routes[path] = status to body
    }

    private fun respond(exchange: HttpExchange, status: Int, body: String) {
        val bytes = body.toByteArray()
        exchange.responseHeaders.add("Content-Type", "application/json")
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    private suspend fun assertFailsWith(expected: UpdateFailure, block: suspend () -> Unit) {
        try {
            block()
            fail("expected $expected")
        } catch (e: UpdateException) {
            assertEquals(expected, e.failure)
        }
    }

    private companion object {
        val MANIFEST = """
            {
              "schemaVersion": 1,
              "channel": "stable",
              "versionCode": 1002003,
              "versionName": "1.2.3",
              "tag": "v1.2.3",
              "releaseNotes": "notes",
              "apkUrl": {
                "arm64-v8a": "https://e/a.apk",
                "universal": "https://e/u.apk"
              },
              "apkSha256": { "arm64-v8a": "hash-a", "universal": "hash-u" }
            }
        """.trimIndent()

        val GITHUB_RELEASE = """
            {
              "tag_name": "v1.2.3",
              "body": "notes",
              "assets": [
                { "name": "moxia-v1.2.3-arm64-v8a.apk", "browser_download_url": "https://e/a.apk" },
                { "name": "moxia-v1.2.3-universal.apk", "browser_download_url": "https://e/u.apk" }
              ]
            }
        """.trimIndent()
    }
}
