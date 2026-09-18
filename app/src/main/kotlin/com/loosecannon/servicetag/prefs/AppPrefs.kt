package com.loosecannon.servicetag.prefs

import android.content.Context

interface KeyValueStore {
    fun getLong(key: String): Long?
    fun putLong(key: String, value: Long)
    fun getString(key: String): String?
    fun putString(key: String, value: String)
}

class SharedPrefsStore(context: Context) : KeyValueStore {
    private val prefs = context.applicationContext.getSharedPreferences("servicetag", Context.MODE_PRIVATE)
    override fun getLong(key: String): Long? = if (prefs.contains(key)) prefs.getLong(key, 0L) else null
    override fun putLong(key: String, value: Long) { prefs.edit().putLong(key, value).apply() }
    override fun getString(key: String): String? = prefs.getString(key, null)
    override fun putString(key: String, value: String) { prefs.edit().putString(key, value).apply() }
}

enum class AppearanceMode { SYSTEM, LIGHT, DARK }

/** The few per-install facts the UI needs that are not domain data. Backed up? No — they are device-local by design. */
class AppPrefs(private val store: KeyValueStore) {
    val lastBackupAt: Long? get() = store.getLong(KEY_LAST_BACKUP)
    fun markBackupExported(now: Long) = store.putLong(KEY_LAST_BACKUP, now)
    var appearanceMode: AppearanceMode
        get() = store.getString(KEY_APPEARANCE)?.let { runCatching { AppearanceMode.valueOf(it) }.getOrNull() } ?: AppearanceMode.SYSTEM
        set(value) = store.putString(KEY_APPEARANCE, value.name)

    /** The SAF tree the owner chose for attachments. Device-local, never in a backup. */
    var attachmentTreeUri: String?
        get() = store.getString(KEY_ATTACHMENT_TREE).orNullIfBlank()
        set(value) = store.putString(KEY_ATTACHMENT_TREE, value ?: "")

    /** The set id of the last data archive restored, so a later artifacts restore can refuse (spec 7.3). */
    var lastRestoredBackupSetId: String?
        get() = store.getString(KEY_LAST_RESTORED_SET).orNullIfBlank()
        set(value) = store.putString(KEY_LAST_RESTORED_SET, value ?: "")

    private companion object {
        const val KEY_LAST_BACKUP = "last_backup_at"
        const val KEY_APPEARANCE = "appearance_mode"
        const val KEY_ATTACHMENT_TREE = "attachment_tree_uri"
        const val KEY_LAST_RESTORED_SET = "last_restored_backup_set_id"
    }
}

/** `KeyValueStore.putString` cannot store null, so clearing writes "": one state, not two. */
private fun String?.orNullIfBlank(): String? = this?.takeIf { it.isNotBlank() }
