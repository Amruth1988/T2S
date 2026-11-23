package com.t2sstudio.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class T2SProject(
    val id: String,
    val name: String,
    val createdAt: Long
)

object ProjectStore {
    private fun legacyPrefsName(uid: String) = "t2s_projects_${uid.hashCode().toUInt().toString(16)}"

    private fun prefsName(uid: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
            .digest(uid.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return "t2s_projects_$digest"
    }

    private fun migrateLegacyIfNeeded(context: Context, uid: String, targetName: String) {
        val target = context.getSharedPreferences(targetName, Context.MODE_PRIVATE)
        if (target.contains(KEY_ITEMS)) return
        val legacyValue = context.getSharedPreferences(legacyPrefsName(uid), Context.MODE_PRIVATE)
            .getString(KEY_ITEMS, null) ?: return
        // Copy, do not delete: a hash collision in the legacy namespace must not
        // cause another account's old project list to be destroyed.
        target.edit().putString(KEY_ITEMS, legacyValue).commit()
    }

    private const val KEY_ITEMS = "items"

    fun load(context: Context, uid: String?): List<T2SProject> {
        if (uid.isNullOrBlank()) return emptyList()
        val name = prefsName(uid)
        migrateLegacyIfNeeded(context, uid, name)
        val raw = context.getSharedPreferences(name, Context.MODE_PRIVATE)
            .getString(KEY_ITEMS, "[]") ?: "[]"
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                val id = item.optString("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val name = item.optString("name").trim().takeIf { it.isNotBlank() } ?: return@mapNotNull null
                T2SProject(id, name, item.optLong("createdAt"))
            }.distinctBy { it.id }.sortedByDescending { it.createdAt }
        }.getOrDefault(emptyList())
    }

    fun save(context: Context, uid: String?, projects: List<T2SProject>) {
        if (uid.isNullOrBlank()) return
        val name = prefsName(uid)
        migrateLegacyIfNeeded(context, uid, name)
        val array = JSONArray()
        projects.forEach { project ->
            array.put(JSONObject().apply {
                put("id", project.id)
                put("name", project.name)
                put("createdAt", project.createdAt)
            })
        }
        context.getSharedPreferences(name, Context.MODE_PRIVATE)
            .edit().putString(KEY_ITEMS, array.toString()).apply()
    }

    fun create(name: String): T2SProject {
        val clean = name.trim()
        require(clean.isNotEmpty()) { "Enter a project name." }
        return T2SProject(UUID.randomUUID().toString(), clean, System.currentTimeMillis())
    }
}
