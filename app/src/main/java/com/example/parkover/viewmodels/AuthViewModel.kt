package com.example.parkover.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.parkover.data.model.User
import com.example.parkover.data.repository.AuthRepository
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

    fun signUpWithEmail(email: String, password: String) {
        _authState.value = AuthState.Loading
        viewModelScope.launch {
            val result = repository.signUpWithEmail(email, password)
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
