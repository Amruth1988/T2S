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
    private const val PREFS = "t2s_projects"
    private const val KEY_ITEMS = "items"

    fun load(context: Context): List<T2SProject> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
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

    fun save(context: Context, projects: List<T2SProject>) {
        val array = JSONArray()
        projects.forEach { project ->
            array.put(JSONObject().apply {
                put("id", project.id)
                put("name", project.name)
                put("createdAt", project.createdAt)
            })
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_ITEMS, array.toString()).apply()
    }

    fun create(name: String): T2SProject {
        val clean = name.trim()
        require(clean.isNotEmpty()) { "Enter a project name." }
        return T2SProject(UUID.randomUUID().toString(), clean, System.currentTimeMillis())
    }
}
