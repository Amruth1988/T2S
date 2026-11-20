package com.t2sstudio.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class T2SScriptDraft(val id:String,val title:String,val content:String,val language:String,val projectId:String,val projectName:String,val updatedAt:Long)

object ScriptDraftStore {
    private const val PREFS = "t2s_script_drafts"
    fun load(context:Context):List<T2SScriptDraft> {
        val raw=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getString("items","[]") ?: "[]"
        return runCatching {
            val array=JSONArray(raw)
            (0 until array.length()).mapNotNull { i ->
                val item=array.optJSONObject(i) ?: return@mapNotNull null
                val id=item.optString("id")
                val content=item.optString("content")
                if(id.isBlank() || content.isBlank()) null else T2SScriptDraft(
                    id,item.optString("title","Untitled draft"),content,
                    item.optString("language","English"),item.optString("projectId",""),
                    item.optString("projectName","Unassigned"),item.optLong("updatedAt")
                )
            }.distinctBy { it.id }.sortedByDescending { it.updatedAt }
        }.getOrDefault(emptyList())
    }
    fun save(context:Context,drafts:List<T2SScriptDraft>) {
        val array=JSONArray()
        drafts.forEach { d -> array.put(JSONObject().apply {
            put("id",d.id); put("title",d.title); put("content",d.content)
            put("language",d.language); put("projectId",d.projectId)
            put("projectName",d.projectName); put("updatedAt",d.updatedAt)
        }) }
        context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putString("items",array.toString()).apply()
    }
    fun create(content:String,language:String,projectId:String,projectName:String):T2SScriptDraft {
        val clean=content.trim()
        require(clean.isNotEmpty()) { "Write some text before saving a draft." }
        val title=clean.lineSequence().firstOrNull().orEmpty().take(64).ifBlank { "Untitled draft" }
        return T2SScriptDraft(UUID.randomUUID().toString(),title,clean,language,projectId,projectName,System.currentTimeMillis())
    }
}
