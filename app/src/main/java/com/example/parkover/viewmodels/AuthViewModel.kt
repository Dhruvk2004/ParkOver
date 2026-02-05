package com.example.parkover.viewmodels

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.parkover.data.model.User
import com.example.parkover.data.repository.AuthRepository
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.launch

class AuthViewModel : ViewModel() {
    
    private val repository = AuthRepository()
    
    private val _authState = MutableLiveData<AuthState>()
    val authState: LiveData<AuthState> = _authState
    
    private val _currentUser = MutableLiveData<User?>()
    val currentUser: LiveData<User?> = _currentUser
    
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
