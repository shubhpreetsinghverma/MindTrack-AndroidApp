package com.mindtrack.app

import android.content.SharedPreferences

/** An in-memory SharedPreferences for fast tests that don't need an emulator. */
internal class FakePrefs : SharedPreferences {
    private val data = mutableMapOf<String, Any?>()

    override fun getAll(): MutableMap<String, *> = data.toMutableMap()
    override fun getString(key: String, defValue: String?) = data[key] as? String ?: defValue
    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String, defValues: MutableSet<String>?) = (data[key] as? Set<String>)?.toMutableSet() ?: defValues
    override fun getInt(key: String, defValue: Int) = data[key] as? Int ?: defValue
    override fun getLong(key: String, defValue: Long) = data[key] as? Long ?: defValue
    override fun getFloat(key: String, defValue: Float) = data[key] as? Float ?: defValue
    override fun getBoolean(key: String, defValue: Boolean) = data[key] as? Boolean ?: defValue
    override fun contains(key: String) = data.containsKey(key)
    override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) {}

    override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
        private val pending = mutableMapOf<String, Any?>()
        private val removed = mutableSetOf<String>()
        private var clear = false

        override fun putString(key: String, value: String?) = apply { pending[key] = value }
        override fun putStringSet(key: String, values: MutableSet<String>?) = apply { pending[key] = values?.toSet() }
        override fun putInt(key: String, value: Int) = apply { pending[key] = value }
        override fun putLong(key: String, value: Long) = apply { pending[key] = value }
        override fun putFloat(key: String, value: Float) = apply { pending[key] = value }
        override fun putBoolean(key: String, value: Boolean) = apply { pending[key] = value }
        override fun remove(key: String) = apply { removed += key }
        override fun clear() = apply { clear = true }
        override fun commit(): Boolean { apply(); return true }
        override fun apply() {
            if (clear) data.clear()
            removed.forEach { data.remove(it) }
            pending.forEach { (k, v) -> if (v == null) data.remove(k) else data[k] = v }
        }
    }
}

/** A cloud that can go offline, for testing sync. */
internal class FakeCloud : CheckInCloud {
    val docs = mutableMapOf<String, Map<String, Any>>()
    var offline = false
    var calls = 0

    override suspend fun saveCheckIn(date: String, mood: Int, stress: Int, energy: Int, journal: String, tags: List<String>) {
        calls++
        if (offline) throw java.io.IOException("offline")
        docs[date] = mapOf("date" to date, "mood" to mood, "stress" to stress, "energy" to energy, "journal" to journal, "tags" to tags)
    }

    override suspend fun deleteCheckIn(date: String) {
        calls++
        if (offline) throw java.io.IOException("offline")
        docs.remove(date)
    }

    override suspend fun loadCheckIns(limit: Long): List<Map<String, Any>> {
        if (offline) throw java.io.IOException("offline")
        return docs.values.toList()
    }
}
