package com.dhruvk.parkover.data.repository

import com.dhruvk.parkover.data.model.User
import com.dhruvk.parkover.utils.Constants
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import android.net.Uri
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class AuthRepository {
    
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
    
    val currentUser: FirebaseUser?
        get() = auth.currentUser
    
    val isLoggedIn: Boolean
        get() = currentUser != null
    
    suspend fun signUpWithEmail(email: String, password: String, name: String = ""): Result<FirebaseUser> {
        return try {
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            result.user?.let { user ->
                if (name.isNotBlank()) {
                    user.updateProfile(
                        UserProfileChangeRequest.Builder().setDisplayName(name).build()
                    ).await()
                }
                // Create user document in Firestore
                createUserInFirestore(user.uid, email, name)
                Result.success(user)
            } ?: Result.failure(Exception("User creation failed"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun signInWithEmail(email: String, password: String): Result<FirebaseUser> {
        return try {
            val result = auth.signInWithEmailAndPassword(email, password).await()
            result.user?.let { user ->
                Result.success(user)
            } ?: Result.failure(Exception("Sign in failed"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun signInWithGoogle(idToken: String): Result<FirebaseUser> {
        return try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val result = auth.signInWithCredential(credential).await()
            result.user?.let { user ->
                // Check if user exists in Firestore, if not create
                val userDoc = firestore.collection(Constants.COLLECTION_USERS)
                    .document(user.uid)
                    .get()
                    .await()
                
                if (!userDoc.exists()) {
                    createUserInFirestore(
                        uid = user.uid,
                        email = user.email ?: "",
                        name = user.displayName ?: "",
                        photoUrl = user.photoUrl?.toString()
                    )
                }
                Result.success(user)
            } ?: Result.failure(Exception("Google sign in failed"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // Debug-only shortcut: anonymous Firebase session (requires the Anonymous provider
    // to be enabled in the Firebase console)
    suspend fun signInAnonymously(): Result<FirebaseUser> {
        return try {
            val result = auth.signInAnonymously().await()
            result.user?.let { user ->
                val userDoc = firestore.collection(Constants.COLLECTION_USERS)
                    .document(user.uid)
                    .get()
                    .await()
                if (!userDoc.exists()) {
                    createUserInFirestore(uid = user.uid, email = "", name = "Dev Tester")
                }
                Result.success(user)
            } ?: Result.failure(Exception("Anonymous sign in failed"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun createUserInFirestore(
        uid: String,
        email: String,
        name: String = "",
        photoUrl: String? = null
    ) {
        val user = User(
            uid = uid,
            email = email,
            name = name,
            profilePhotoUrl = photoUrl
        )
        firestore.collection(Constants.COLLECTION_USERS)
            .document(uid)
            .set(user)
            .await()
    }
    
    suspend fun getUserData(uid: String): Result<User> {
        return try {
            val doc = firestore.collection(Constants.COLLECTION_USERS)
                .document(uid)
                .get()
                .await()
            
            doc.toObject(User::class.java)?.let { user ->
                Result.success(user)
            } ?: Result.failure(Exception("User not found"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun updateUserProfile(user: User): Result<Unit> {
        return try {
            firestore.collection(Constants.COLLECTION_USERS)
                .document(user.uid)
                .set(user)
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun sendPasswordReset(email: String): Result<Unit> {
        return try {
            auth.sendPasswordResetEmail(email).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Updates the display name in both Firebase Auth and the Firestore user document. */
    suspend fun updateDisplayName(name: String): Result<Unit> {
        val user = auth.currentUser ?: return Result.failure(Exception("Not logged in"))
        return try {
            user.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(name).build()).await()
            firestore.collection(Constants.COLLECTION_USERS)
                .document(user.uid)
                .set(mapOf("name" to name), SetOptions.merge())
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Uploads a profile photo to Firebase Storage and stores its URL on Auth + Firestore. */
    suspend fun uploadProfilePhoto(uri: Uri): Result<String> {
        val user = auth.currentUser ?: return Result.failure(Exception("Not logged in"))
        return try {
            val ref = FirebaseStorage.getInstance().reference.child("profile_photos/${user.uid}.jpg")
            ref.putFile(uri).await()
            val url = ref.downloadUrl.await().toString()
            user.updateProfile(UserProfileChangeRequest.Builder().setPhotoUri(Uri.parse(url)).build()).await()
            firestore.collection(Constants.COLLECTION_USERS)
                .document(user.uid)
                .set(mapOf("profilePhotoUrl" to url), SetOptions.merge())
                .await()
            Result.success(url)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun signOut() {
        auth.signOut()
    }
}
