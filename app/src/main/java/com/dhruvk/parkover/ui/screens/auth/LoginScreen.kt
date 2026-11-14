package com.dhruvk.parkover.ui.screens.auth

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dhruvk.parkover.R
import com.dhruvk.parkover.ui.components.DevLoginButton
import com.dhruvk.parkover.ui.components.ParkTextField
import com.dhruvk.parkover.ui.components.PrimaryButton
import com.dhruvk.parkover.ui.components.SecondaryButton
import com.dhruvk.parkover.ui.theme.DividerColor
import com.dhruvk.parkover.ui.theme.ErrorRed
import com.dhruvk.parkover.ui.theme.Purple
import com.dhruvk.parkover.ui.theme.PurpleDark
import com.dhruvk.parkover.ui.theme.TextPrimary
import com.dhruvk.parkover.ui.theme.TextSecondary
import com.dhruvk.parkover.ui.theme.TextTertiary
import com.dhruvk.parkover.viewmodels.AuthState
import com.dhruvk.parkover.viewmodels.AuthViewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException

@Composable
fun LoginScreen(
    viewModel: AuthViewModel,
    onLoginSuccess: () -> Unit,
    onNavigateToSignUp: () -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val authState = viewModel.authState.collectAsStateWithLifecycle().value
    val message = viewModel.message.collectAsStateWithLifecycle().value
    val snackbar = remember { SnackbarHostState() }

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var emailError by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val isLoading = authState is AuthState.Loading

    // Don't carry an error over from Sign up
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
                errorMessage = "Google sign in failed: ${e.message}"
            }
        }
    }

    LaunchedEffect(authState) {
        when (authState) {
            is AuthState.Success -> onLoginSuccess()
            is AuthState.Error -> errorMessage = mapAuthError(authState.message)
            else -> {}
        }
    }

    LaunchedEffect(message) {
        if (message != null) {
            snackbar.showSnackbar(message)
            viewModel.clearMessage()
        }
    }

    fun submit() {
        emailError = null
        passwordError = null
        errorMessage = null
        when {
            email.isBlank() -> emailError = "Enter your email"
            !android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches() -> emailError = "Enter a valid email, like name@example.com"
            password.isBlank() -> passwordError = "Enter your password"
            password.length < 6 -> passwordError = "Passwords are at least 6 characters"
            else -> {
                focusManager.clearFocus()
                viewModel.signInWithEmail(email.trim(), password)
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
            Spacer(Modifier.height(36.dp))
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Brush.linearGradient(listOf(Purple, PurpleDark))),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier.size(96.dp)
                )
            }
            Column {
                Text("Log in to ParkOver", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Welcome back. Your bookings are waiting.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextSecondary
                )
            }

            Spacer(Modifier.height(4.dp))

            ParkTextField(
                value = email,
                onValueChange = { email = it; emailError = null; errorMessage = null },
                label = "Email",
                placeholder = "name@example.com",
                error = emailError,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next)
            )
            ParkTextField(
                value = password,
                onValueChange = { password = it; passwordError = null; errorMessage = null },
                label = "Password",
                error = passwordError,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
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

            Text(
                text = "Forgot password?",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = Purple,
                modifier = Modifier
                    .align(Alignment.End)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        if (android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()) {
                            viewModel.sendPasswordReset(email.trim())
                        } else {
                            emailError = "Enter your email first, then tap Forgot password"
                        }
                    }
                    .padding(4.dp)
            )

            errorMessage?.let {
                Text(it, color = ErrorRed, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.fillMaxWidth())
            }

            PrimaryButton(text = "Log in", onClick = ::submit, loading = isLoading)

            Row(verticalAlignment = Alignment.CenterVertically) {
                HorizontalDivider(modifier = Modifier.weight(1f), color = DividerColor)
                Text("  or  ", style = MaterialTheme.typography.bodySmall, color = TextTertiary)
                HorizontalDivider(modifier = Modifier.weight(1f), color = DividerColor)
            }

            SecondaryButton(
                text = "Continue with Google",
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

            Spacer(Modifier.height(24.dp))

            Text(
                text = buildAnnotatedString {
                    append("New to ParkOver? ")
                    withStyle(SpanStyle(color = Purple, fontWeight = FontWeight.SemiBold)) { append("Create account") }
                },
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onNavigateToSignUp)
                    .padding(vertical = 12.dp)
            )
        }

        DevLoginButton(
            onDevLogin = { devEmail, devPassword ->
                errorMessage = null
                viewModel.devSignIn(devEmail, devPassword)
            },
            enabled = !isLoading,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 12.dp, end = 16.dp)
        )

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
        )
    }
}

private fun mapAuthError(rawMessage: String): String {
    if (rawMessage.startsWith("Dev login")) return rawMessage
    val lower = rawMessage.lowercase()
    return when {
        "credential" in lower || "password" in lower || "invalid" in lower ->
            "The email or password is incorrect"
        "no user" in lower || "user not found" in lower ->
            "No account found with this email"
        "too many" in lower || "blocked" in lower ->
            "Too many attempts. Please try again later"
        "network" in lower || "timeout" in lower ->
            "Network error. Please check your connection"
        "disabled" in lower ->
            "This account has been disabled"
        "email already" in lower || "already in use" in lower ->
            "An account with this email already exists"
        else -> "Something went wrong. Please try again"
    }
}
