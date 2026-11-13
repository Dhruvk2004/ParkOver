package com.dhruvk.parkover.viewmodels

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dhruvk.parkover.data.model.User
import com.dhruvk.parkover.data.repository.AuthRepository
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AuthViewModel : ViewModel() {

    private val repository = AuthRepository()

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    val isLoggedIn: Boolean
        get() = repository.isLoggedIn

    fun signUpWithEmail(email: String, password: String, name: String = "") {
        _authState.value = AuthState.Loading
        viewModelScope.launch {
            val result = repository.signUpWithEmail(email, password, name.trim())
            result.fold(
                onSuccess = { user ->
                    _authState.value = AuthState.Success(user)
                },
                onFailure = { exception ->
                    _authState.value = AuthState.Error(exception.message ?: "Sign up failed")
                }
            )
        }
    }

    fun signInWithEmail(email: String, password: String) {
        _authState.value = AuthState.Loading
        viewModelScope.launch {
            val result = repository.signInWithEmail(email, password)
            result.fold(
                onSuccess = { user ->
                    _authState.value = AuthState.Success(user)
                },
                onFailure = { exception ->
                    _authState.value = AuthState.Error(exception.message ?: "Sign in failed")
                }
            )
        }
    }

    fun signInWithGoogle(idToken: String) {
        _authState.value = AuthState.Loading
        viewModelScope.launch {
            val result = repository.signInWithGoogle(idToken)
            result.fold(
                onSuccess = { user ->
                    _authState.value = AuthState.Success(user)
                },
                onFailure = { exception ->
                    _authState.value = AuthState.Error(exception.message ?: "Google sign in failed")
                }
            )
        }
    }

    // Debug builds only (see DevLoginButton): uses the DEV_LOGIN_* account from
    // local.properties when set, otherwise an anonymous Firebase session
    fun devSignIn(email: String, password: String) {
        if (email.isNotBlank() && password.isNotBlank()) {
            signInWithEmail(email, password)
            return
        }
        _authState.value = AuthState.Loading
        viewModelScope.launch {
            repository.signInAnonymously().fold(
                onSuccess = { user -> _authState.value = AuthState.Success(user) },
                onFailure = { exception ->
                    _authState.value = AuthState.Error(
                        "Dev login failed: ${exception.message}. Add DEV_LOGIN_EMAIL/DEV_LOGIN_PASSWORD " +
                            "to local.properties or enable Anonymous sign-in in Firebase."
                    )
                }
            )
        }
    }

    fun loadCurrentUser() {
        repository.currentUser?.let { firebaseUser ->
            viewModelScope.launch {
                val result = repository.getUserData(firebaseUser.uid)
                result.fold(
                    onSuccess = { user ->
                        _currentUser.value = user
                    },
                    onFailure = {
                        _currentUser.value = null
                    }
                )
            }
        }
    }

    private val _message = MutableStateFlow<String?>(null)
    /** One-off confirmation/error text for snackbars (password reset, profile updates). */
    val message: StateFlow<String?> = _message.asStateFlow()

    fun clearMessage() {
        _message.value = null
    }

    fun sendPasswordReset(email: String) {
        viewModelScope.launch {
            _message.value = repository.sendPasswordReset(email.trim()).fold(
                onSuccess = { "Reset link sent to ${email.trim()}" },
                onFailure = { "Couldn't send reset email: ${it.message}" }
            )
        }
    }

    private val _profileSaving = MutableStateFlow(false)
    val profileSaving: StateFlow<Boolean> = _profileSaving.asStateFlow()

    fun updateDisplayName(name: String) {
        viewModelScope.launch {
            _profileSaving.value = true
            repository.updateDisplayName(name.trim()).fold(
                onSuccess = {
                    _currentUser.value = _currentUser.value?.copy(name = name.trim())
                    _message.value = "Name updated"
                },
                onFailure = { _message.value = "Couldn't update name: ${it.message}" }
            )
            _profileSaving.value = false
            loadCurrentUser()
        }
    }

    fun uploadProfilePhoto(uri: Uri) {
        viewModelScope.launch {
            _profileSaving.value = true
            repository.uploadProfilePhoto(uri).fold(
                onSuccess = { url ->
                    _currentUser.value = _currentUser.value?.copy(profilePhotoUrl = url)
                    _message.value = "Photo updated"
                },
                onFailure = { _message.value = "Couldn't upload photo: ${it.message}" }
            )
            _profileSaving.value = false
        }
    }

    fun signOut() {
        repository.signOut()
        _currentUser.value = null
        _authState.value = AuthState.SignedOut
    }

    fun resetState() {
        _authState.value = AuthState.Idle
    }
}

sealed class AuthState {
    object Idle : AuthState()
    object Loading : AuthState()
    data class Success(val user: FirebaseUser) : AuthState()
    data class Error(val message: String) : AuthState()
    object SignedOut : AuthState()
}
