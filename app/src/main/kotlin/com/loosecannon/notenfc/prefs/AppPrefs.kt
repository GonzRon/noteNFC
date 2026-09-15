package com.loosecannon.notenfc.prefs

import android.content.Context

interface KeyValueStore {
    fun getLong(key: String): Long?
    fun putLong(key: String, value: Long)
    fun getString(key: String): String?
    fun putString(key: String, value: String)
}

class SharedPrefsStore(context: Context) : KeyValueStore {
    private val prefs = context.applicationContext.getSharedPreferences("notenfc", Context.MODE_PRIVATE)
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
    private companion object {
        const val KEY_LAST_BACKUP = "last_backup_at"
        const val KEY_APPEARANCE = "appearance_mode"
    }
}
