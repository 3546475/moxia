package org.example.moxia.data.update

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Where a release manifest comes from. Two shapes cover everything the app ships:
 *
 * - [Manifest] is a static JSON file. Being static, it can be mirrored anywhere, which is the whole
 *   point: the default source is a CNB raw file because GitHub is not reachable for every user.
 * - [GithubRelease] reads the Releases API directly, so a repository whose workflow already
 *   publishes APKs needs no extra publishing step. It is the built-in fallback, not the default:
 *   the unauthenticated API allows 60 requests per hour per IP, and users behind one carrier-grade
 *   NAT share that budget.
 */
sealed interface UpdateSource {

    /** Stable identity used both as a DataStore value and as the source's key. */
    val id: String

    data class Manifest(val url: String) : UpdateSource {
        override val id: String get() = url
    }

    data class GithubRelease(val repo: String) : UpdateSource {
        override val id: String get() = "github:$repo"
    }

    companion object {
        /**
         * The shipped default: `update.json` on the CNB mirror's main branch, which the release
         * workflow writes. CNB is reachable from mainland China without a proxy.
         */
        const val DEFAULT_MANIFEST_URL =
            "https://cnb.cool/nulxiel/moxia/-/git/raw/main/update.json"

        const val GITHUB_REPO = "3546475/moxia"

        /** Tried in order; the first source that answers decides. Never mixed. */
        fun defaults(): List<UpdateSource> = listOf(
            Manifest(DEFAULT_MANIFEST_URL),
            GithubRelease(GITHUB_REPO),
        )

        /** Parses what [DataStoreManager] stored: a URL, or `github:<owner>/<repo>`. */
        fun fromStored(stored: String?): UpdateSource? {
            val value = stored?.trim().orEmpty()
            if (value.isEmpty()) return null
            if (value.startsWith("github:")) {
                val repo = value.removePrefix("github:").trim()
                return repo.takeIf { it.isNotEmpty() }?.let { GithubRelease(it) }
            }
            return value.takeIf { it.toHttpUrlOrNull() != null }?.let { Manifest(it) }
        }
    }
}
