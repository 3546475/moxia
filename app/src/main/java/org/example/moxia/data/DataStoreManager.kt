package org.example.moxia.data

import org.example.moxia.model.TopBarScrollMode

import org.example.moxia.model.NavBarScrollMode

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import org.example.moxia.model.AppearancePreferences
import org.example.moxia.model.DefaultNavDestinations
import org.example.moxia.model.EditGesture
import org.example.moxia.model.ThemeMode
import org.example.moxia.model.ColorTheme
import org.example.moxia.model.AppFont
import org.example.moxia.model.Account
import org.example.moxia.model.User
import org.example.moxia.model.toUserSnapshot

/**
 * Resolves a stored top-bar mode, including the retired three-way names: "NEVER" meant "always
 * visible", and both "ALL" and "SEARCH_ONLY" meant "hide while scrolling".
 */
internal fun topBarScrollModeOf(name: String?): TopBarScrollMode? = when (name) {
    TopBarScrollMode.ALWAYS.name, "NEVER" -> TopBarScrollMode.ALWAYS
    TopBarScrollMode.ON_SCROLL.name, "ALL", "SEARCH_ONLY" -> TopBarScrollMode.ON_SCROLL
    else -> null
}

class DataStoreManager(
    private val dataStore: DataStore<Preferences>,
    private val onAccountsChanged: () -> Unit = {}
) {

    private val gson = Gson()

    companion object {
        val APPEARANCE_THEME_MODE = stringPreferencesKey("appearance_theme_mode")
        val APPEARANCE_COLOR_THEME = stringPreferencesKey("appearance_color_theme")
        val APPEARANCE_CUSTOM_HUE = androidx.datastore.preferences.core.floatPreferencesKey("appearance_custom_hue")
        val APPEARANCE_FONT = stringPreferencesKey("appearance_font")
    val APPEARANCE_NAV_SCROLL_MODE = stringPreferencesKey("appearance_nav_scroll_mode")
    val APPEARANCE_TOP_SCROLL_MODE = stringPreferencesKey("appearance_top_scroll_mode")
    val APPEARANCE_NAV_DESTINATIONS = stringPreferencesKey("appearance_nav_destinations")
    val APPEARANCE_EDIT_GESTURE = stringPreferencesKey("appearance_edit_gesture")

        /**
         * Written by builds that still offered the Material Design style switch. A backup made by
         * such a build must restore without failing, so the key is recognised and dropped rather
         * than treated as an unknown setting.
         */
        internal const val LEGACY_APPEARANCE_THEME_STYLE = "appearance_theme_style"
        /**
         * The single switch the two per-bar switches replaced. Kept readable so preferences and
         * backups written by an older build still carry their choice over to both bars.
         */
        val APPEARANCE_BLUR_ENABLED = androidx.datastore.preferences.core.booleanPreferencesKey("appearance_blur_enabled")
        val APPEARANCE_TOP_BAR_BLUR = androidx.datastore.preferences.core.booleanPreferencesKey("appearance_top_bar_blur")
        val APPEARANCE_BOTTOM_BAR_BLUR = androidx.datastore.preferences.core.booleanPreferencesKey("appearance_bottom_bar_blur")
        val APPEARANCE_LIQUID_NAV_BAR = androidx.datastore.preferences.core.booleanPreferencesKey("appearance_liquid_nav_bar")
        val APPEARANCE_FLOATING_NAV_BAR = androidx.datastore.preferences.core.booleanPreferencesKey("appearance_floating_nav_bar")
        val ACCOUNTS_JSON = stringPreferencesKey("accounts_json")
        val SETUP_COMPLETED = androidx.datastore.preferences.core.booleanPreferencesKey("setup_completed")
        val PAGE_SIZE = intPreferencesKey("page_size")
        const val DEFAULT_PAGE_SIZE = 10
        val HEADER_SCALE = androidx.datastore.preferences.core.floatPreferencesKey("header_scale")
        const val DEFAULT_HEADER_SCALE = 1.0f
        val LINK_PREVIEW_ENABLED = androidx.datastore.preferences.core.booleanPreferencesKey("link_preview_enabled")
        const val DEFAULT_LINK_PREVIEW_ENABLED = true

        // --- Markdown rendering ("editor") settings ---
        val CODE_LINE_NUMBERS =
            androidx.datastore.preferences.core.booleanPreferencesKey("code_line_numbers")
        const val DEFAULT_CODE_LINE_NUMBERS = false
        val CODE_WRAP_LINES =
            androidx.datastore.preferences.core.booleanPreferencesKey("code_wrap_lines")
        const val DEFAULT_CODE_WRAP_LINES = false
        val PARAGRAPH_INDENT =
            androidx.datastore.preferences.core.booleanPreferencesKey("paragraph_indent")
        const val DEFAULT_PARAGRAPH_INDENT = false
        val CJK_SPACING = androidx.datastore.preferences.core.booleanPreferencesKey("cjk_spacing")
        const val DEFAULT_CJK_SPACING = false

        // --- Offline / pre-download settings ---
        val PRE_DOWNLOAD_TEXT = androidx.datastore.preferences.core.booleanPreferencesKey("pre_download_text")
        const val DEFAULT_PRE_DOWNLOAD_TEXT = true
        val PRE_DOWNLOAD_ATTACHMENTS =
            androidx.datastore.preferences.core.booleanPreferencesKey("pre_download_attachments")
        const val DEFAULT_PRE_DOWNLOAD_ATTACHMENTS = true
        val PRE_DOWNLOAD_WIFI_ONLY =
            androidx.datastore.preferences.core.booleanPreferencesKey("pre_download_wifi_only")
        const val DEFAULT_PRE_DOWNLOAD_WIFI_ONLY = true
        val PRE_DOWNLOAD_EXPLORE =
            androidx.datastore.preferences.core.booleanPreferencesKey("pre_download_explore")
        const val DEFAULT_PRE_DOWNLOAD_EXPLORE = false
        val ATTACHMENT_CACHE_MAX_MB = intPreferencesKey("attachment_cache_max_mb")
        const val DEFAULT_ATTACHMENT_CACHE_MAX_MB = 250
        val TEXT_CACHE_MAX_MB = intPreferencesKey("text_cache_max_mb")
        const val DEFAULT_TEXT_CACHE_MAX_MB = 100
        val THEME_CACHE_MAX_MB = intPreferencesKey("theme_cache_max_mb")
        const val DEFAULT_THEME_CACHE_MAX_MB = 200
        // Incremental-sync cursor for the text pre-downloader. Deliberately
        // separate from the per-account last-sync time (which SyncManager
        // updates): only the pre-downloader writes this, so pushPendingChanges() cannot
        // shrink the "what changed since the last full download" window and
        // cause updates to be skipped.
        val TEXT_SYNC_CURSOR = androidx.datastore.preferences.core.longPreferencesKey("text_sync_cursor")

        // --- Self-update state ---
        // Device-local on purpose and therefore absent from `portableKeys`: a backup restored on
        // another device must not claim that *that* install has an update waiting, and the chosen
        // source points at a release channel rather than at this user's data.
        val UPDATE_SOURCE_URL = stringPreferencesKey("update_source_url")
        val UPDATE_LAST_CHECK_AT = androidx.datastore.preferences.core.longPreferencesKey("update_last_check_at")
        val UPDATE_KNOWN_VERSION_CODE = intPreferencesKey("update_known_version_code")
        val UPDATE_KNOWN_VERSION_NAME = stringPreferencesKey("update_known_version_name")
    }

    val accounts: Flow<List<Account>> = dataStore.data
        .map { it[ACCOUNTS_JSON] }
        .distinctUntilChanged()
        .map { json ->
            if (json.isNullOrEmpty()) {
                emptyList()
            } else {
                val type = object : TypeToken<List<Account>>() {}.type
                try {
                    val list: List<Account> = gson.fromJson(json, type)
                    list
                } catch (e: Exception) {
                    emptyList()
                }
            }
        }

    val account: Flow<Account?> = accounts.map { list ->
        list.find { it.isActive }
    }

    val setupCompleted: Flow<Boolean> = dataStore.data
        .map { it[SETUP_COMPLETED] ?: false }
        .distinctUntilChanged()

    suspend fun completeSetup() {
        editPreferences { it[SETUP_COMPLETED] = true }
    }

    suspend fun saveAccounts(accounts: List<Account>) {
        editPreferences { preferences ->
            preferences[ACCOUNTS_JSON] = gson.toJson(accounts)
        }
        onAccountsChanged()
    }


    // --- Account Helpers ---

    suspend fun getAccounts(): List<Account> {

        val json = dataStore.data.map { it[ACCOUNTS_JSON] }.first()

        if (json.isNullOrEmpty()) {
            return emptyList()
        }

        val type = object : TypeToken<List<Account>>() {}.type
        val list: List<Account> = try {
            gson.fromJson(json, type)
        } catch (e: Exception) {
            emptyList()
        }

        var needsSave = false
        val sanitized = list.map { account ->
            @Suppress("SENSELESS_COMPARISON") if (account.id == null || account.hostUrl == null || account.accessToken == null) {
                needsSave = true
                @Suppress("USELESS_ELVIS") account.copy(
                    id = account.id ?: java.util.UUID.randomUUID().toString(),
                    hostUrl = account.hostUrl ?: "",
                    accessToken = account.accessToken ?: ""
                )
            } else {
                account
            }
        }

        val final = if (sanitized.isNotEmpty() && sanitized.none { it.isActive }) {
            needsSave = true
            sanitized.mapIndexed { index, account ->
                if (index == 0) account.copy(isActive = true) else account
            }
        } else {
            sanitized
        }

        if (needsSave) {
            saveAccounts(final)
        }

        return final
    }

    private suspend fun mutateAccounts(completeSetup: Boolean = false, transform: (List<Account>) -> List<Account>) {
        editPreferences { preferences ->
            val type = object : TypeToken<List<Account>>() {}.type
            val current: List<Account> = preferences[ACCOUNTS_JSON]?.let {
                gson.fromJson(it, type)
            } ?: emptyList()
            val updated = transform(current)
            preferences[ACCOUNTS_JSON] = gson.toJson(updated)
            if (completeSetup) preferences[SETUP_COMPLETED] = true
        }
        onAccountsChanged()
    }

    suspend fun addAccount(hostUrl: String, accessToken: String) {
        mutateAccounts(completeSetup = true) { current ->
            val existing = current.firstOrNull {
                it.hostUrl == hostUrl && it.accessToken == accessToken
            }
            if (existing != null) current.map { it.copy(isActive = it.id == existing.id) }
            else current.map { it.copy(isActive = false) } +
                Account(hostUrl = hostUrl, accessToken = accessToken, isActive = true)
        }
    }

    suspend fun setActiveAccount(id: String) {
        mutateAccounts { current ->
            if (current.none { it.id == id }) current
            else current.map { it.copy(isActive = it.id == id) }
        }
    }

    suspend fun updateAccountLastUsed(id: String, timestamp: Long) {
        mutateAccounts { current ->
            current.map { if (it.id == id) it.copy(lastUsed = timestamp) else it }
        }
    }

    suspend fun deleteAccount(id: String) {
        mutateAccounts { current ->
            val remaining = current.filterNot { it.id == id }
            if (remaining.isNotEmpty() && remaining.none { it.isActive }) {
                remaining.mapIndexed { index, account -> account.copy(isActive = index == 0) }
            } else remaining
        }
    }

    suspend fun updateAccount(id: String, hostUrl: String, token: String) {
        mutateAccounts { current ->
            val previous = current.find { it.id == id } ?: return@mutateAccounts current
            fun server(url: String) = url.trim().trimEnd('/').removeSuffix("/api/v1")
            if (server(previous.hostUrl) == server(hostUrl)) {
                // Token renewal retains this account's offline history and pending work.
                current.map { if (it.id == id) it.copy(hostUrl = hostUrl, accessToken = token) else it }
            } else {
                // A different server gets a new namespace. Keep the original credentials
                // and unsynced work available in the original saved account.
                current.map { if (it.id == id) it.copy(isActive = false) else it } +
                    Account(id = java.util.UUID.randomUUID().toString(), hostUrl = hostUrl,
                        accessToken = token, isActive = previous.isActive)
            }
        }
    }

    suspend fun updateAccountUser(id: String, user: User) {
        mutateAccounts { current ->
            current.map {
                if (it.id == id) it.copy(
                    user = user.toUserSnapshot(), name = user.username,
                    displayName = user.displayName, avatarUrl = user.avatarUrl,
                    email = user.email, description = user.description
                ) else it
            }
        }
    }

    suspend fun updateAccountToken(id: String, token: String) {
        mutateAccounts { current ->
            current.map { if (it.id == id) it.copy(accessToken = token) else it }
        }
    }

    val appearance: Flow<AppearancePreferences> = dataStore.data.map { preferences ->
        val hue = preferences[APPEARANCE_CUSTOM_HUE]?.takeIf { it.isFinite() && it in 0f..360f }
        val colorTheme = ColorTheme.entries.firstOrNull { it.name == preferences[APPEARANCE_COLOR_THEME] }
            ?: ColorTheme.SYSTEM
        // Two independent switches: the frost is the bar's material, the liquid glass is an
        // interaction on top of it. Either can be on without the other.
        val liquidNavBar = preferences[APPEARANCE_LIQUID_NAV_BAR] ?: false
        AppearancePreferences(
            themeMode = ThemeMode.entries.firstOrNull { it.name == preferences[APPEARANCE_THEME_MODE] }
                ?: ThemeMode.SYSTEM,
            colorTheme = if (colorTheme == ColorTheme.CUSTOM && hue == null) ColorTheme.SYSTEM else colorTheme,
            customHue = hue?.rem(360f),
            font = AppFont.entries.firstOrNull { it.name == preferences[APPEARANCE_FONT] } ?: AppFont.SYSTEM,
            navBarScrollMode = NavBarScrollMode.entries.firstOrNull {
                it.name == preferences[APPEARANCE_NAV_SCROLL_MODE]
            } ?: NavBarScrollMode.ALWAYS_VISIBLE,
            topBarScrollMode = topBarScrollModeOf(preferences[APPEARANCE_TOP_SCROLL_MODE])
                ?: TopBarScrollMode.ON_SCROLL,
            topBarBlur = preferences[APPEARANCE_TOP_BAR_BLUR]
                ?: preferences[APPEARANCE_BLUR_ENABLED] ?: false,
            bottomBarBlur = preferences[APPEARANCE_BOTTOM_BAR_BLUR]
                ?: preferences[APPEARANCE_BLUR_ENABLED] ?: true,
            liquidNavBar = liquidNavBar,
            floatingNavBar = preferences[APPEARANCE_FLOATING_NAV_BAR] ?: true,
            editGesture = EditGesture.entries.firstOrNull {
                it.name == preferences[APPEARANCE_EDIT_GESTURE]
            } ?: EditGesture.NONE,
            navDestinations = preferences[APPEARANCE_NAV_DESTINATIONS]
                ?.split(',')
                ?.filter { it.isNotBlank() }
                ?: DefaultNavDestinations
        )
    }.distinctUntilChanged()

    suspend fun saveThemeMode(mode: ThemeMode) {
        dataStore.edit { it[APPEARANCE_THEME_MODE] = mode.name }
    }

    suspend fun saveColorTheme(theme: ColorTheme, hue: Float? = null) {
        require(theme != ColorTheme.CUSTOM || (hue != null && hue.isFinite() && hue in 0f..360f))
        dataStore.edit {
            it[APPEARANCE_COLOR_THEME] = theme.name
            if (hue != null && hue.isFinite() && hue in 0f..360f) {
                it[APPEARANCE_CUSTOM_HUE] = hue % 360f
            }
        }
    }

    suspend fun saveFont(font: AppFont) {
        dataStore.edit { it[APPEARANCE_FONT] = font.name }
    }

    suspend fun saveNavBarScrollMode(mode: NavBarScrollMode) {
        editPreferences { it[APPEARANCE_NAV_SCROLL_MODE] = mode.name }
    }

    suspend fun saveTopBarScrollMode(mode: TopBarScrollMode) {
        editPreferences { it[APPEARANCE_TOP_SCROLL_MODE] = mode.name }
    }

    suspend fun saveTopBarBlur(enabled: Boolean) {
        editPreferences { it[APPEARANCE_TOP_BAR_BLUR] = enabled }
    }

    suspend fun saveBottomBarBlur(enabled: Boolean) {
        editPreferences { it[APPEARANCE_BOTTOM_BAR_BLUR] = enabled }
    }

    suspend fun saveLiquidNavBar(enabled: Boolean) {
        editPreferences { it[APPEARANCE_LIQUID_NAV_BAR] = enabled }
    }

    suspend fun saveFloatingNavBar(enabled: Boolean) {
        editPreferences { it[APPEARANCE_FLOATING_NAV_BAR] = enabled }
    }

    suspend fun saveEditGesture(gesture: EditGesture) {
        editPreferences { it[APPEARANCE_EDIT_GESTURE] = gesture.name }
    }

    /** Destinations are stored as one comma-joined string so the backup stays a single primitive. */
    suspend fun saveNavDestinations(destinations: List<String>) {
        editPreferences { preferences ->
            if (destinations.isEmpty()) {
                preferences.remove(APPEARANCE_NAV_DESTINATIONS)
            } else {
                preferences[APPEARANCE_NAV_DESTINATIONS] = destinations.joinToString(",")
            }
        }
    }

    val pageSize: Flow<Int> = dataStore.data.map { preferences ->
        preferences[PAGE_SIZE] ?: DEFAULT_PAGE_SIZE
    }

    suspend fun savePageSize(size: Int) {
        editPreferences { preferences ->
            preferences[PAGE_SIZE] = size
        }
    }

    val headerScale: Flow<Float> = dataStore.data.map { preferences ->
        preferences[HEADER_SCALE] ?: DEFAULT_HEADER_SCALE
    }

    suspend fun saveHeaderScale(scale: Float) {
        editPreferences { preferences ->
            preferences[HEADER_SCALE] = scale
        }
    }

    // --- Offline / pre-download settings ---

    val linkPreviewEnabled: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[LINK_PREVIEW_ENABLED] ?: DEFAULT_LINK_PREVIEW_ENABLED
    }

    suspend fun saveLinkPreviewEnabled(enabled: Boolean) {
        dataStore.edit { it[LINK_PREVIEW_ENABLED] = enabled }
    }

    // --- Markdown rendering ("editor") settings ---

    val codeLineNumbers: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[CODE_LINE_NUMBERS] ?: DEFAULT_CODE_LINE_NUMBERS
    }

    suspend fun saveCodeLineNumbers(enabled: Boolean) {
        editPreferences { preferences -> preferences[CODE_LINE_NUMBERS] = enabled }
    }

    val codeWrapLines: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[CODE_WRAP_LINES] ?: DEFAULT_CODE_WRAP_LINES
    }

    suspend fun saveCodeWrapLines(enabled: Boolean) {
        editPreferences { preferences -> preferences[CODE_WRAP_LINES] = enabled }
    }

    val paragraphIndent: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[PARAGRAPH_INDENT] ?: DEFAULT_PARAGRAPH_INDENT
    }

    suspend fun saveParagraphIndent(enabled: Boolean) {
        editPreferences { preferences -> preferences[PARAGRAPH_INDENT] = enabled }
    }

    val cjkSpacing: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[CJK_SPACING] ?: DEFAULT_CJK_SPACING
    }

    suspend fun saveCjkSpacing(enabled: Boolean) {
        editPreferences { preferences -> preferences[CJK_SPACING] = enabled }
    }

    val preDownloadText: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[PRE_DOWNLOAD_TEXT] ?: DEFAULT_PRE_DOWNLOAD_TEXT
    }

    suspend fun savePreDownloadText(enabled: Boolean) {
        editPreferences { preferences ->
            preferences[PRE_DOWNLOAD_TEXT] = enabled
        }
    }

    val preDownloadAttachments: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[PRE_DOWNLOAD_ATTACHMENTS] ?: DEFAULT_PRE_DOWNLOAD_ATTACHMENTS
    }

    suspend fun savePreDownloadAttachments(enabled: Boolean) {
        editPreferences { preferences ->
            preferences[PRE_DOWNLOAD_ATTACHMENTS] = enabled
        }
    }

    val preDownloadWifiOnly: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[PRE_DOWNLOAD_WIFI_ONLY] ?: DEFAULT_PRE_DOWNLOAD_WIFI_ONLY
    }

    suspend fun savePreDownloadWifiOnly(enabled: Boolean) {
        editPreferences { preferences ->
            preferences[PRE_DOWNLOAD_WIFI_ONLY] = enabled
        }
    }

    val preDownloadExplore: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[PRE_DOWNLOAD_EXPLORE] ?: DEFAULT_PRE_DOWNLOAD_EXPLORE
    }

    suspend fun savePreDownloadExplore(enabled: Boolean) {
        editPreferences { preferences ->
            preferences[PRE_DOWNLOAD_EXPLORE] = enabled
        }
    }

    val attachmentCacheMaxMb: Flow<Int> = dataStore.data.map { preferences ->
        preferences[ATTACHMENT_CACHE_MAX_MB] ?: DEFAULT_ATTACHMENT_CACHE_MAX_MB
    }

    suspend fun saveAttachmentCacheMaxMb(mb: Int) {
        editPreferences { preferences ->
            preferences[ATTACHMENT_CACHE_MAX_MB] = mb
        }
    }

    /**
     * Last successful sync time, stored per account (like the session
     * snapshot): a global key would leak one account's sync time into every
     * other account's UI.
     */
    fun lastSyncTimeKey(accountId: String): Preferences.Key<Long> =
        androidx.datastore.preferences.core.longPreferencesKey("last_sync_time_$accountId")

    fun lastSyncTime(accountId: String?): Flow<Long> {
        if (accountId == null) return flowOf(0L)
        return dataStore.data.map { preferences ->
            preferences[lastSyncTimeKey(accountId)] ?: 0L
        }
    }

    suspend fun saveLastSyncTime(accountId: String, timestamp: Long) {
        editPreferences { preferences ->
            preferences[lastSyncTimeKey(accountId)] = timestamp
        }
    }

    private fun textSyncCursorKey(accountId: String) =
        androidx.datastore.preferences.core.longPreferencesKey("text_sync_cursor_$accountId")

    // The legacy global cursor has unknown ownership and must never be inherited.
    fun textSyncCursor(accountId: String): Flow<Long> = dataStore.data.map { preferences ->
        preferences[textSyncCursorKey(accountId)] ?: 0L
    }

    suspend fun saveTextSyncCursor(accountId: String, timestamp: Long) {
        editPreferences { preferences ->
            preferences[textSyncCursorKey(accountId)] = timestamp
        }
    }

    suspend fun removeDownloadState(accountId: String) {
        editPreferences { preferences ->
            preferences.remove(textSyncCursorKey(accountId))
            preferences.remove(lastPreDownloadAtKey(accountId))
        }
    }

    /**
     * Last pre-download completion time, stored per account. Unlike the
     * in-memory cooldown in PreDownloadManager (lost on process restart),
     * this survives relaunches so a fresh start right after a completed
     * pre-download doesn't re-trigger the whole download again.
     */
    fun lastPreDownloadAtKey(accountId: String): Preferences.Key<Long> =
        androidx.datastore.preferences.core.longPreferencesKey("last_pre_download_at_$accountId")

    fun lastPreDownloadAt(accountId: String?): Flow<Long> {
        if (accountId == null) return flowOf(0L)
        return dataStore.data.map { preferences ->
            preferences[lastPreDownloadAtKey(accountId)] ?: 0L
        }
    }

    suspend fun saveLastPreDownloadAt(accountId: String, timestamp: Long) {
        editPreferences { preferences ->
            preferences[lastPreDownloadAtKey(accountId)] = timestamp
        }
    }

    // --- Per-tier cache size limits ---

    val textCacheMaxMb: Flow<Int> = dataStore.data.map { preferences ->
        preferences[TEXT_CACHE_MAX_MB] ?: DEFAULT_TEXT_CACHE_MAX_MB
    }

    suspend fun saveTextCacheMaxMb(mb: Int) {
        editPreferences { preferences ->
            preferences[TEXT_CACHE_MAX_MB] = mb
        }
    }

    val themeCacheMaxMb: Flow<Int> = dataStore.data.map { preferences ->
        preferences[THEME_CACHE_MAX_MB] ?: DEFAULT_THEME_CACHE_MAX_MB
    }

    suspend fun saveThemeCacheMaxMb(mb: Int) {
        editPreferences { preferences ->
            preferences[THEME_CACHE_MAX_MB] = mb
        }
    }

    // --- Generic per-domain snapshots ---
    // Key scheme "<domain>_snapshot_<accountId>" (the session snapshot uses
    // domain "session"): the snapshot of one account must never be restored
    // into another account's session (e.g. after an offline account switch).

    fun snapshotJsonKey(domain: String, accountId: String): Preferences.Key<String> =
        stringPreferencesKey("${domain}_snapshot_$accountId")

    fun snapshotJson(domain: String, accountId: String): Flow<String?> =
        dataStore.data.map { preferences ->
            preferences[snapshotJsonKey(domain, accountId)]
        }

    suspend fun saveSnapshotJson(domain: String, accountId: String, json: String) {
        editPreferences { preferences ->
            preferences[snapshotJsonKey(domain, accountId)] = json
        }
    }

    suspend fun removeSnapshot(domain: String, accountId: String) {
        editPreferences { preferences ->
            preferences.remove(snapshotJsonKey(domain, accountId))
        }
    }

    suspend fun removeLastSyncTime(accountId: String) {
        editPreferences { preferences ->
            preferences.remove(lastSyncTimeKey(accountId))
        }
    }

    // --- Self-update state ---
    // None of these keys appear in `portableKeys`, so they never enter a backup.

    val updateSourceUrl: Flow<String?> = dataStore.data
        .map { it[UPDATE_SOURCE_URL] }
        .distinctUntilChanged()

    suspend fun saveUpdateSourceUrl(url: String?) {
        editPreferences { preferences ->
            if (url.isNullOrBlank()) preferences.remove(UPDATE_SOURCE_URL)
            else preferences[UPDATE_SOURCE_URL] = url.trim()
        }
    }

    /** The last *successful* check; a failed one leaves it untouched so the next start retries. */
    val updateLastCheckAt: Flow<Long> = dataStore.data
        .map { it[UPDATE_LAST_CHECK_AT] ?: 0L }
        .distinctUntilChanged()

    suspend fun saveUpdateLastCheckAt(timestamp: Long) {
        editPreferences { preferences -> preferences[UPDATE_LAST_CHECK_AT] = timestamp }
    }

    /** "A newer build exists" as a persisted fact, or null when this install is current. */
    val knownUpdate: Flow<org.example.moxia.data.update.KnownUpdate?> = dataStore.data
        .map { preferences ->
            preferences[UPDATE_KNOWN_VERSION_CODE]
                ?.takeIf { it > 0 }
                ?.let { code ->
                    org.example.moxia.data.update.KnownUpdate(
                        code,
                        preferences[UPDATE_KNOWN_VERSION_NAME].orEmpty()
                    )
                }
        }
        .distinctUntilChanged()

    suspend fun saveKnownUpdate(versionCode: Int, versionName: String) {
        editPreferences { preferences ->
            preferences[UPDATE_KNOWN_VERSION_CODE] = versionCode
            preferences[UPDATE_KNOWN_VERSION_NAME] = versionName
        }
    }

    suspend fun clearKnownUpdate() {
        editPreferences { preferences ->
            preferences.remove(UPDATE_KNOWN_VERSION_CODE)
            preferences.remove(UPDATE_KNOWN_VERSION_NAME)
        }
    }

    private suspend fun editPreferences(action: suspend (androidx.datastore.preferences.core.MutablePreferences) -> Unit) =
        org.example.moxia.data.backup.BackupCoordinator.withStorageLock { dataStore.edit(action) }

    private val portableKeys: List<Preferences.Key<*>> get() = listOf(
        APPEARANCE_THEME_MODE, APPEARANCE_COLOR_THEME, APPEARANCE_CUSTOM_HUE, APPEARANCE_FONT,
        APPEARANCE_NAV_SCROLL_MODE, APPEARANCE_TOP_SCROLL_MODE,
        APPEARANCE_NAV_DESTINATIONS, APPEARANCE_BLUR_ENABLED, APPEARANCE_FLOATING_NAV_BAR,
        APPEARANCE_TOP_BAR_BLUR, APPEARANCE_BOTTOM_BAR_BLUR, APPEARANCE_LIQUID_NAV_BAR,
        PAGE_SIZE, HEADER_SCALE, LINK_PREVIEW_ENABLED, PRE_DOWNLOAD_TEXT, PRE_DOWNLOAD_ATTACHMENTS,
        PRE_DOWNLOAD_WIFI_ONLY, PRE_DOWNLOAD_EXPLORE, ATTACHMENT_CACHE_MAX_MB, TEXT_CACHE_MAX_MB, THEME_CACHE_MAX_MB,
        CODE_LINE_NUMBERS, CODE_WRAP_LINES, PARAGRAPH_INDENT, CJK_SPACING
    )

    suspend fun backupSettings(): com.google.gson.JsonObject {
        val values = dataStore.data.first().asMap().mapKeys { it.key.name }
        return com.google.gson.JsonObject().apply {
            portableKeys.forEach { key -> values[key.name]?.let { add(key.name, gson.toJsonTree(it)) } }
        }
    }

    suspend fun backupSnapshot(domain: String, accountId: String): com.google.gson.JsonObject? =
        snapshotJson(domain, accountId).first()?.let { com.google.gson.JsonParser.parseString(it).asJsonObject }

    fun validateBackupSettings(settings: com.google.gson.JsonObject) {
        require(
            settings.keySet().all { name ->
                name == LEGACY_APPEARANCE_THEME_STYLE || portableKeys.any { it.name == name }
            }
        ) { "Unknown backup setting" }
        settings.entrySet().forEach { (name, value) ->
            // Retired keys restore as a no-op: their choice is not brought back.
            if (name == LEGACY_APPEARANCE_THEME_STYLE) return@forEach
            require(value.isJsonPrimitive) { "Invalid backup setting" }
            when (name) {
                APPEARANCE_THEME_MODE.name -> org.example.moxia.model.ThemeMode.valueOf(value.asString)
                APPEARANCE_COLOR_THEME.name -> org.example.moxia.model.ColorTheme.valueOf(value.asString)
                APPEARANCE_FONT.name -> org.example.moxia.model.AppFont.valueOf(value.asString)
                APPEARANCE_NAV_SCROLL_MODE.name -> NavBarScrollMode.valueOf(value.asString)
                APPEARANCE_TOP_SCROLL_MODE.name -> require(topBarScrollModeOf(value.asString) != null) {
                    "Unknown top bar scroll mode"
                }
                APPEARANCE_CUSTOM_HUE.name, HEADER_SCALE.name -> require(value.asJsonPrimitive.isNumber && value.asFloat.isFinite())
                PAGE_SIZE.name ->
                    require(value.asJsonPrimitive.isNumber && value.asBigDecimal.stripTrailingZeros().scale() <= 0 && value.asLong in 1..Int.MAX_VALUE.toLong())
                ATTACHMENT_CACHE_MAX_MB.name, TEXT_CACHE_MAX_MB.name, THEME_CACHE_MAX_MB.name ->
                    require(value.asJsonPrimitive.isNumber && value.asBigDecimal.stripTrailingZeros().scale() <= 0 && value.asBigDecimal >= Int.MIN_VALUE.toBigDecimal() && value.asBigDecimal <= Int.MAX_VALUE.toBigDecimal())
                else -> require(value.asJsonPrimitive.isBoolean) { "Invalid boolean setting" }
            }
        }
    }

    /** One atomic preferences update; cache freshness state is deliberately never inherited. */
    suspend fun applyBackup(manifest: org.example.moxia.data.backup.BackupManifest) {
        val categories = manifest.categories
        editPreferences { prefs ->
            if (org.example.moxia.data.backup.BackupCategory.SETTINGS in categories) {
                val values = manifest.settings!!
                validateBackupSettings(values)
                portableKeys.forEach { key ->
                    prefs.remove(key)
                    val value = values.get(key.name) ?: return@forEach
                    when (key) {
                        APPEARANCE_THEME_MODE, APPEARANCE_COLOR_THEME, APPEARANCE_FONT,
            APPEARANCE_NAV_SCROLL_MODE, APPEARANCE_TOP_SCROLL_MODE, APPEARANCE_NAV_DESTINATIONS ->
                prefs[stringPreferencesKey(key.name)] = value.asString
                        APPEARANCE_CUSTOM_HUE, HEADER_SCALE -> prefs[androidx.datastore.preferences.core.floatPreferencesKey(key.name)] = value.asFloat
                        PAGE_SIZE, ATTACHMENT_CACHE_MAX_MB, TEXT_CACHE_MAX_MB, THEME_CACHE_MAX_MB -> prefs[intPreferencesKey(key.name)] = value.asInt
                        else -> prefs[androidx.datastore.preferences.core.booleanPreferencesKey(key.name)] = value.asBoolean
                    }
                }
                // The style switch of older builds is retired; drop the leftover value too.
                prefs.remove(stringPreferencesKey(LEGACY_APPEARANCE_THEME_STYLE))
            }
            manifest.accounts.forEach { data ->
                val id = data.identity.id
                if (org.example.moxia.data.backup.BackupCategory.CACHE in categories) {
                    listOf("session" to data.session, "notifications" to data.notifications).forEach { (domain, obj) ->
                        val key = snapshotJsonKey(domain, id)
                        if (obj == null) prefs.remove(key) else prefs[key] = gson.toJson(obj)
                    }
                }
                if (org.example.moxia.data.backup.BackupCategory.CACHE in categories || org.example.moxia.data.backup.BackupCategory.MEDIA in categories) {
                    prefs.remove(textSyncCursorKey(id)); prefs.remove(lastPreDownloadAtKey(id)); prefs.remove(lastSyncTimeKey(id))
                }
                if (org.example.moxia.data.backup.BackupCategory.QUEUED_EDITS in categories) {
                    prefs[stringPreferencesKey("restored_edits_$id")] = gson.toJson(data.queuedEdits)
                }
            }
            if (org.example.moxia.data.backup.BackupCategory.ACCOUNTS in categories) {
                val type = object : TypeToken<List<Account>>() {}.type
                val current: List<Account> = prefs[ACCOUNTS_JSON]?.let { gson.fromJson(it, type) } ?: emptyList()
                val restored = manifest.accounts.mapNotNull { it.account }
                val replacements = restored.associateBy { it.id }
                val combined = current.map { replacements[it.id] ?: it } + restored.filter { account -> current.none { it.id == account.id } }
                val active = restored.firstOrNull { it.isActive }?.id ?: current.firstOrNull { it.isActive }?.id ?: combined.firstOrNull()?.id
                prefs[ACCOUNTS_JSON] = gson.toJson(combined.map { it.copy(isActive = it.id == active) })
            }
            // Includes Android restoration and imports containing settings only.
            // This installation state is deliberately excluded from portableKeys.
            prefs[SETUP_COMPLETED] = true
        }
        if (org.example.moxia.data.backup.BackupCategory.ACCOUNTS in categories) onAccountsChanged()
    }

    fun restoredEdits(accountId: String): Flow<com.google.gson.JsonArray> =
        dataStore.data.map { prefs -> prefs[stringPreferencesKey("restored_edits_$accountId")]?.let { com.google.gson.JsonParser.parseString(it).asJsonArray } ?: com.google.gson.JsonArray() }

    suspend fun dismissRestoredEdit(accountId: String, editId: String) {
        editPreferences { prefs ->
            val key = stringPreferencesKey("restored_edits_$accountId")
            val edits = prefs[key]?.let { com.google.gson.JsonParser.parseString(it).asJsonArray } ?: return@editPreferences
            val remaining = com.google.gson.JsonArray().apply { edits.filter { it.asJsonObject.get("id").asString != editId }.forEach { add(it) } }
            prefs[key] = gson.toJson(remaining)
        }
    }

}
