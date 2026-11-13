package com.dhruvk.parkover.ui.screens.auth

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dhruvk.parkover.R
import com.dhruvk.parkover.ui.components.DevLoginButton
import com.dhruvk.parkover.ui.components.ParkTextField
import com.dhruvk.parkover.ui.components.PrimaryButton
import com.dhruvk.parkover.ui.components.SecondaryButton
import com.dhruvk.parkover.ui.components.SquareIconButton
import com.dhruvk.parkover.ui.theme.DividerColor
import com.dhruvk.parkover.ui.theme.ErrorRed
import com.dhruvk.parkover.ui.theme.SuccessGreen
import com.dhruvk.parkover.ui.theme.TextPrimary
import com.dhruvk.parkover.ui.theme.TextSecondary
import com.dhruvk.parkover.ui.theme.TextTertiary
import com.dhruvk.parkover.ui.theme.WarningOrange
import com.dhruvk.parkover.viewmodels.AuthState
import com.dhruvk.parkover.viewmodels.AuthViewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException

@Composable
fun SignUpScreen(
    viewModel: AuthViewModel,
    onSignUpSuccess: () -> Unit,
    onNavigateToLogin: () -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val authState = viewModel.authState.collectAsStateWithLifecycle().value

    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var nameError by remember { mutableStateOf<String?>(null) }
    var emailError by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var confirmPasswordError by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val isLoading = authState is AuthState.Loading

    LaunchedEffect(Unit) { viewModel.resetState() }

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                account.idToken?.let { token -> viewModel.signInWithGoogle(token) }
            } catch (e: ApiException) {
                errorMessage = "Google sign up failed: ${e.message}"
            }
        }
    }

    LaunchedEffect(authState) {
        when (authState) {
            is AuthState.Success -> onSignUpSuccess()
            is AuthState.Error -> errorMessage = mapSignUpAuthError(authState.message)
            else -> {}
        }
    }

    fun submit() {
        nameError = null; emailError = null; passwordError = null; confirmPasswordError = null; errorMessage = null
        when {
            name.trim().length < 2 -> nameError = "Enter your name (at least 2 letters)"
            email.isBlank() -> emailError = "Enter your email"
            !android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches() -> emailError = "Enter a valid email, like name@example.com"
            password.length < 6 -> passwordError = "Use at least 6 characters"
            confirmPassword != password -> confirmPasswordError = "Passwords don't match"
            else -> {
                focusManager.clearFocus()
                viewModel.signUpWithEmail(email.trim(), password, name)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Spacer(Modifier.height(8.dp))
            SquareIconButton(
                icon = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "Back to log in",
                onClick = onNavigateToLogin
            )
            Column {
                Text("Create your account", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
                Spacer(Modifier.height(4.dp))
                Text("Takes 30 seconds. No card needed.", style = MaterialTheme.typography.bodyLarge, color = TextSecondary)
            }

            ParkTextField(
                value = name,
                onValueChange = { name = it; nameError = null },
                label = "Full name",
                error = nameError,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next)
            )
            ParkTextField(
                value = email,
                onValueChange = { email = it; emailError = null; errorMessage = null },
                label = "Email",
                placeholder = "name@example.com",
                error = emailError,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next)
            )
            Column {
                ParkTextField(
                    value = password,
                    onValueChange = { password = it; passwordError = null },
                    label = "Password",
                    error = passwordError,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                    trailing = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                if (passwordVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                contentDescription = if (passwordVisible) "Hide password" else "Show password",
                                tint = TextSecondary
                            )
                        }
                    }
                )
                if (password.isNotEmpty() && passwordError == null) {
                    Spacer(Modifier.height(8.dp))
                    PasswordStrength(password)
                }
            }
            ParkTextField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it; confirmPasswordError = null },
                label = "Confirm password",
                error = confirmPasswordError,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() })
            )

            errorMessage?.let {
                Text(it, color = ErrorRed, style = MaterialTheme.typography.bodyMedium)
            }

            PrimaryButton(text = "Create account", onClick = ::submit, loading = isLoading)

            SecondaryButton(
                text = "Sign up with Google",
                onClick = {
                    val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                        .requestIdToken(context.getString(R.string.default_web_client_id))
                        .requestEmail()
                        .build()
                    googleSignInLauncher.launch(GoogleSignIn.getClient(context, gso).signInIntent)
                },
                enabled = !isLoading,
                leading = {
                    Icon(
                        painter = painterResource(R.drawable.google),
                        contentDescription = null,
                        tint = Color.Unspecified,
                        modifier = Modifier.size(20.dp)
                    )
                }
            )

            Text(
                text = "By continuing you agree to the Terms and Privacy Policy",
                style = MaterialTheme.typography.bodySmall,
                color = TextTertiary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp)
            )
        }

        DevLoginButton(
            onDevLogin = { devEmail, devPassword -> viewModel.devSignIn(devEmail, devPassword) },
            enabled = !isLoading,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 12.dp, end = 16.dp)
        )
    }
}

@Composable
private fun PasswordStrength(password: String) {
    val score = listOf(
        password.length >= 8,
        password.any { it.isDigit() },
        password.any { it.isUpperCase() } && password.any { it.isLowerCase() },
        password.any { !it.isLetterOrDigit() }
    ).count { it }
    val (label, color) = when {
        score <= 1 -> "Weak password" to ErrorRed
        score == 2 -> "Okay password" to WarningOrange
        score == 3 -> "Good password" to SuccessGreen
        else -> "Strong password" to SuccessGreen
    }
    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
            repeat(4) { index ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (index < score) color else DividerColor)
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = color, fontWeight = FontWeight.Medium)
    }
}

private fun mapSignUpAuthError(rawMessage: String): String {
    if (rawMessage.startsWith("Dev login")) return rawMessage
    val lower = rawMessage.lowercase()
    return when {
        "email already" in lower || "already in use" in lower ->
            "An account with this email already exists. Log in instead."
        "weak password" in lower ->
            "Password is too weak. Use at least 6 characters"
        "invalid email" in lower || "badly formatted" in lower ->
            "Enter a valid email address"
        "network" in lower || "timeout" in lower ->
            "Network error. Check your connection and try again"
        "too many" in lower || "blocked" in lower ->
            "Too many attempts. Try again in a few minutes"
        else -> "Something went wrong. Please try again"
    }
}
