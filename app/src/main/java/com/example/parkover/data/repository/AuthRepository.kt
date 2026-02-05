package com.example.parkover.data.repository

import com.example.parkover.data.model.User
import com.example.parkover.utils.Constants
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class AuthRepository {
    
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
    
    val currentUser: FirebaseUser?
        get() = auth.currentUser
    
    val isLoggedIn: Boolean
        get() = currentUser != null
    
    suspend fun signUpWithEmail(email: String, password: String): Result<FirebaseUser> {
        return try {
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            result.user?.let { user ->
                // Create user document in Firestore
                createUserInFirestore(user.uid, email)
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
    
    fun signOut() {
        auth.signOut()
    }
}
