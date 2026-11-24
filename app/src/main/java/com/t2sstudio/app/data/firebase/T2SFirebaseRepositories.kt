package com.t2sstudio.app.data.firebase

import android.os.Handler
import android.os.Looper
import com.t2sstudio.app.T2SScriptDraft
import java.util.concurrent.atomic.AtomicBoolean

import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import java.util.Date

/**
 * Firebase Authentication entry points for email/password and Google credentials.
 * Google UI/credential acquisition is handled by the Android UI layer; pass the
 * resulting AuthCredential to signInWithGoogleCredential.
 */
class T2SAuthRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    val currentUser: FirebaseUser?
        get() = auth.currentUser

    fun registerWithEmail(
        email: String,
        password: String,
        onComplete: (Result<FirebaseUser>) -> Unit
    ) {
        auth.createUserWithEmailAndPassword(email.trim(), password)
            .addOnSuccessListener { result ->
                val user = result.user
                if (user == null) onComplete(Result.failure(IllegalStateException("Firebase returned no user.")))
                else saveUserProfile(user, onComplete)
            }
            .addOnFailureListener { onComplete(Result.failure(it)) }
    }

    fun signInWithEmail(
        email: String,
        password: String,
        onComplete: (Result<FirebaseUser>) -> Unit
    ) {
        auth.signInWithEmailAndPassword(email.trim(), password)
            .addOnSuccessListener { result ->
                val user = result.user
                if (user == null) onComplete(Result.failure(IllegalStateException("Firebase returned no user.")))
                else saveUserProfile(user, onComplete)
            }
            .addOnFailureListener { onComplete(Result.failure(it)) }
    }

    fun signInWithGoogleCredential(
        credential: AuthCredential,
        onComplete: (Result<FirebaseUser>) -> Unit
    ) {
        auth.signInWithCredential(credential)
            .addOnSuccessListener { result ->
                val user = result.user
                if (user == null) onComplete(Result.failure(IllegalStateException("Firebase returned no user.")))
                else saveUserProfile(user, onComplete)
            }
            .addOnFailureListener { onComplete(Result.failure(it)) }
    }

    fun sendPasswordReset(
        email: String,
        onComplete: (Result<Unit>) -> Unit
    ) {
        auth.sendPasswordResetEmail(email.trim())
            .addOnSuccessListener { onComplete(Result.success(Unit)) }
            .addOnFailureListener { onComplete(Result.failure(it)) }
    }

    fun signOut() = auth.signOut()

    private fun saveUserProfile(
        user: FirebaseUser,
        onComplete: (Result<FirebaseUser>) -> Unit
    ) {
        val profile = mapOf(
            "uid" to user.uid,
            "displayName" to user.displayName,
            "email" to user.email,
            "photoUrl" to user.photoUrl?.toString(),
            "accountStatus" to "active",
            "schemaVersion" to 1,
            "updatedAt" to FieldValue.serverTimestamp(),
            "lastSignInAt" to FieldValue.serverTimestamp()
        )
        firestore.collection("users").document(user.uid)
            .set(profile + ("createdAt" to Date(user.metadata?.creationTimestamp ?: System.currentTimeMillis())), SetOptions.merge())
            .addOnSuccessListener { onComplete(Result.success(user)) }
            // Authentication must remain successful even if the optional profile
            // metadata write is temporarily blocked by rules or connectivity.
            .addOnFailureListener { onComplete(Result.success(user)) }
    }
}

/**
 * Metadata-only cloud repository. Audio bytes and original voice recordings
 * remain on-device; this class stores project/script descriptors and settings.
 */
class T2SFirestoreRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    private fun userCollection(uid: String, collection: String) =
        firestore.collection("users").document(uid).collection(collection)

    fun saveProject(
        uid: String,
        projectId: String,
        name: String,
        createdAt: Long? = null,
        onComplete: (Result<Unit>) -> Unit
    ) {
        val data = mutableMapOf<String, Any>(
            "name" to name.trim(),
            "updatedAt" to FieldValue.serverTimestamp(),
            "isArchived" to false
        )
        createdAt?.let { data["createdAt"] = Date(it) }
        userCollection(uid, "projects").document(projectId)
            .set(data, SetOptions.merge())
            .addOnSuccessListener { onComplete(Result.success(Unit)) }
            .addOnFailureListener { onComplete(Result.failure(it)) }
    }

    fun loadProjects(
        uid: String,
        onComplete: (Result<List<com.t2sstudio.app.T2SProject>>) -> Unit
    ) {
        if (uid.isBlank()) {
            onComplete(Result.failure(IllegalStateException("Sign in to load your cloud projects.")))
            return
        }
        userCollection(uid, "projects").get()
            .addOnSuccessListener { snapshot ->
                val projects = snapshot.documents
                    .filter { it.getBoolean("isArchived") != true }
                    .mapNotNull { document ->
                        val name = document.getString("name")?.trim().orEmpty()
                        if (name.isBlank()) return@mapNotNull null
                        val createdAt = document.getTimestamp("createdAt")?.toDate()?.time
                            ?: document.getDate("createdAt")?.time
                            ?: 0L
                        com.t2sstudio.app.T2SProject(
                            id = document.id,
                            name = name,
                            createdAt = createdAt
                        )
                    }
                    .sortedByDescending { it.createdAt }
                onComplete(Result.success(projects))
            }
            .addOnFailureListener { onComplete(Result.failure(it)) }
    }

    fun archiveProject(
        uid: String,
        projectId: String,
        onComplete: (Result<Unit>) -> Unit
    ) {
        userCollection(uid, "projects").document(projectId)
            .set(
                mapOf("isArchived" to true, "updatedAt" to FieldValue.serverTimestamp()),
                SetOptions.merge()
            )
            .addOnSuccessListener { onComplete(Result.success(Unit)) }
            .addOnFailureListener { onComplete(Result.failure(it)) }
    }

    fun saveScriptMetadata(
        uid: String,
        scriptId: String,
        projectId: String?,
        title: String,
        content: String,
        language: String,
        onComplete: (Result<Unit>) -> Unit
    ) {
        val data = mapOf(
            "projectId" to projectId,
            "title" to title,
            "content" to content,
            "language" to language,
            "updatedAt" to FieldValue.serverTimestamp(),
            "isDeleted" to false
        )
        userCollection(uid, "scripts").document(scriptId)
            .set(data, SetOptions.merge())
            .addOnSuccessListener { onComplete(Result.success(Unit)) }
            .addOnFailureListener { onComplete(Result.failure(it)) }
    }

    fun loadDrafts(
        uid: String,
        onComplete: (Result<List<T2SScriptDraft>>) -> Unit
    ) {
        if (uid.isBlank()) {
            onComplete(Result.failure(IllegalStateException("Please sign in to load your Firebase drafts.")))
            return
        }
        userCollection(uid, "scripts")
            .whereEqualTo("isDraft", true)
            .get()
            .addOnSuccessListener { snapshot ->
                val drafts = snapshot.documents.filter { it.getBoolean("isDeleted") != true }.mapNotNull { document ->
                    val content = document.getString("content").orEmpty()
                    if (content.isBlank()) return@mapNotNull null
                    val projectId = document.getString("projectId").orEmpty()
                    val updatedAt = document.getTimestamp("updatedAt")?.toDate()?.time
                        ?: document.getDate("updatedAt")?.time
                        ?: 0L
                    T2SScriptDraft(
                        id = document.id,
                        title = document.getString("title") ?: "Untitled draft",
                        content = content,
                        language = document.getString("language") ?: "English",
                        projectId = projectId,
                        projectName = if (projectId.isBlank()) "Unassigned" else "Project",
                        updatedAt = updatedAt
                    )
                }.sortedByDescending { it.updatedAt }
                onComplete(Result.success(drafts))
            }
            .addOnFailureListener { onComplete(Result.failure(it)) }
    }

    fun saveDraftMetadata(
        uid: String,
        draftId: String,
        projectId: String?,
        title: String,
        content: String,
        language: String,
        onComplete: (Result<Unit>) -> Unit
    ) {
        require(uid.isNotBlank()) { "A signed-in Firebase user is required to save a draft." }
        require(draftId.isNotBlank()) { "Draft ID cannot be empty." }
        require(title.isNotBlank()) { "Draft title cannot be empty." }
        require(content.isNotBlank()) { "Draft content cannot be empty." }
        require(content.length <= 180_000) { "Draft is too large to save. Please shorten it and try again." }

        val mainHandler = Handler(Looper.getMainLooper())
        val completed = AtomicBoolean(false)
        fun finish(result: Result<Unit>) {
            if (completed.compareAndSet(false, true)) {
                mainHandler.removeCallbacksAndMessages(draftId)
                mainHandler.post { onComplete(result) }
            }
        }
        val timeout = Runnable {
            finish(Result.failure(
                java.util.concurrent.TimeoutException(
                    "Firebase draft save timed out. Check your internet connection and try again."
                )
            ))
        }
        mainHandler.postAtTime(timeout, draftId, android.os.SystemClock.uptimeMillis() + 20_000L)

        try {
            val data = mapOf(
                "projectId" to projectId,
                "title" to title,
                "content" to content,
                "language" to language,
                "isDraft" to true,
                "isDeleted" to false,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            userCollection(uid, "scripts").document(draftId)
                .set(data, SetOptions.merge())
                .addOnSuccessListener { finish(Result.success(Unit)) }
                .addOnFailureListener { error ->
                    val firestoreCode = (error as? com.google.firebase.firestore.FirebaseFirestoreException)
                        ?.code?.name
                    val detail = error.localizedMessage ?: error.javaClass.simpleName
                    finish(Result.failure(
                        IllegalStateException(
                            "Firestore draft write failed${firestoreCode?.let { " [$it]" } ?: ""}: $detail",
                            error
                        )
                    ))
                }
        } catch (error: Exception) {
            finish(Result.failure(error))
        }
    }

    fun loadVoiceMetadata(
        uid: String,
        onComplete: (Result<List<com.t2sstudio.app.SavedUserVoice>>) -> Unit
    ) {
        if (uid.isBlank()) {
            onComplete(Result.failure(IllegalStateException("Sign in to load your saved voices.")))
            return
        }
        userCollection(uid, "voiceProfiles").get()
            .addOnSuccessListener { snapshot ->
                val voices = snapshot.documents.mapNotNull { document ->
                    val name = document.getString("name")?.trim().orEmpty()
                    if (name.isBlank()) return@mapNotNull null
                    com.t2sstudio.app.SavedUserVoice(
                        id = document.id,
                        name = name,
                        localSamplePath = null,
                        language = document.getString("language") ?: "en",
                        gender = document.getString("gender") ?: "Unknown",
                        sarvamVoiceId = document.getString("sarvamVoiceId").orEmpty(),
                        firebaseSynced = true
                    )
                }.sortedBy { it.name.lowercase() }
                onComplete(Result.success(voices))
            }
            .addOnFailureListener { onComplete(Result.failure(it)) }
    }

    fun saveVoiceMetadata(
        uid: String,
        voiceId: String,
        name: String,
        gender: String,
        language: String,
        sarvamVoiceId: String,
        onComplete: (Result<Unit>) -> Unit
    ) {
        val data = mapOf(
            "name" to name.trim(),
            "gender" to gender,
            "language" to language,
            "sarvamVoiceId" to sarvamVoiceId,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        userCollection(uid, "voiceProfiles").document(voiceId)
            .set(data, SetOptions.merge())
            .addOnSuccessListener { onComplete(Result.success(Unit)) }
            .addOnFailureListener { onComplete(Result.failure(it)) }
    }

    fun archiveScript(
        uid: String,
        scriptId: String,
        onComplete: (Result<Unit>) -> Unit
    ) {
        userCollection(uid, "scripts").document(scriptId)
            .set(
                mapOf("isDeleted" to true, "updatedAt" to FieldValue.serverTimestamp()),
                SetOptions.merge()
            )
            .addOnSuccessListener { onComplete(Result.success(Unit)) }
            .addOnFailureListener { onComplete(Result.failure(it)) }
    }

    fun loadUserProfile(uid: String, onComplete: (Result<Map<String, String>>) -> Unit) {
        if (uid.isBlank()) {
            onComplete(Result.failure(IllegalStateException("Sign in to load your profile.")))
            return
        }
        firestore.collection("users").document(uid).get()
            .addOnSuccessListener { snapshot ->
                val profile = listOf("firstName", "lastName", "mobile", "city", "gender")
                    .associateWith { snapshot.getString(it).orEmpty() }
                onComplete(Result.success(profile))
            }
            .addOnFailureListener { onComplete(Result.failure(it)) }
    }

    fun saveUserProfile(
        uid: String,
        firstName: String,
        lastName: String,
        mobile: String,
        city: String,
        gender: String,
        onComplete: (Result<Unit>) -> Unit
    ) {
        if (uid.isBlank()) {
            onComplete(Result.failure(IllegalStateException("Sign in to save your profile.")))
            return
        }
        val data = mapOf(
            "uid" to uid,
            "firstName" to firstName.trim(),
            "lastName" to lastName.trim(),
            "mobile" to mobile.trim(),
            "city" to city.trim(),
            "gender" to gender,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        firestore.collection("users").document(uid).set(data, SetOptions.merge())
            .addOnSuccessListener { onComplete(Result.success(Unit)) }
            .addOnFailureListener { onComplete(Result.failure(it)) }
    }

    fun savePreferences(
        uid: String,
        preferences: Map<String, Any>,
        onComplete: (Result<Unit>) -> Unit
    ) {
        userCollection(uid, "settings").document("preferences")
            .set(preferences + ("updatedAt" to FieldValue.serverTimestamp()), SetOptions.merge())
            .addOnSuccessListener { onComplete(Result.success(Unit)) }
            .addOnFailureListener { onComplete(Result.failure(it)) }
    }
}
