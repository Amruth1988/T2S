package com.t2sstudio.app

import android.content.Context
import android.os.Environment
import java.io.File
import java.security.MessageDigest

/** Removes only local files and preferences owned by the Firebase UID being deleted. */
object AccountDataCleanup {
    private fun accountKey(uid: String): String = MessageDigest.getInstance("SHA-256")
        .digest(uid.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    fun clear(context: Context, uid: String) {
        require(uid.isNotBlank()) { "A Firebase UID is required for account cleanup." }
        val key = accountKey(uid)

        check(
            context.getSharedPreferences("t2s_projects_$key", Context.MODE_PRIVATE)
                .edit().clear().commit()
        ) { "Could not clear this account's local project index." }
        check(
            context.getSharedPreferences("t2s_audio_history_$key", Context.MODE_PRIVATE)
                .edit().clear().commit()
        ) { "Could not clear this account's local audio history." }

        val musicRoot = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: context.filesDir
        listOf(
            File(musicRoot, "T2SStudio/accounts/$key"),
            File(context.filesDir, "accounts/$key/voices"),
            File(context.noBackupFilesDir, "accounts/$key/voices")
        ).forEach { directory ->
            if (directory.exists()) {
                check(directory.deleteRecursively() && !directory.exists()) {
                    "Could not completely remove account-owned files."
                }
            }
        }
    }
}
