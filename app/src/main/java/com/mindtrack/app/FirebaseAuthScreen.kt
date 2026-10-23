package com.mindtrack.app

import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel


/** Shows [content] for the signed-in person, or the sign-in screen when nobody is signed in. */
@Composable
fun FirebaseAuthGate(
    content: @Composable (uid: String) -> Unit
) {
    val vm: AuthViewModel = viewModel()
    val uid = vm.uid
    when {
        uid == null -> AuthScreen(vm)
        vm.needsVerification -> VerifyEmailScreen(vm)
        else -> content(uid)
    }
}

@Composable
internal fun AuthScreen(vm: AuthViewModel) {
    // New people see sign-up first; "Already have an account?" switches to "Welcome back."
    var isRegistering by remember { mutableStateOf(true) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    val isLoading = vm.isLoading

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Background
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.Center
            ) {
                PixelMoon(size = 56.dp, mood = if (isRegistering) MoonMood.HAPPY else MoonMood.SLEEPY, modifier = Modifier.clearAndSetSemantics {})
                Spacer(Modifier.height(12.dp))
                Text(text = tr("MindTrack").uppercase(), color = Lavender, style = PixelLabelStyle.copy(fontSize = 13.sp))

                Spacer(Modifier.height(22.dp))

                Text(
                    text = if (isRegistering) tr("A little space just for you.") else tr("Welcome back."),
                    color = TextPrimary, fontSize = 30.sp, fontWeight = FontWeight.Medium
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    text = if (isRegistering) tr("Create your account to keep your wellness journey with you.")
                    else tr("Sign in to continue your MindTrack journey."),
                    color = TextSecondary, fontSize = 14.sp, lineHeight = 21.sp
                )

                Spacer(Modifier.height(26.dp))

                if (isRegistering) {
                    OutlinedTextField(
                        value = displayName,
                        onValueChange = { displayName = it },
                        label = { Text(tr("Your name")) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = quietFieldColors(),
                        enabled = !isLoading
                    )
                    Spacer(Modifier.height(12.dp))
                }

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text(tr("Email address")) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    shape = RoundedCornerShape(12.dp),
                    colors = quietFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading
                )

                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(tr("Password (at least 6 characters)")) },
                    singleLine = true,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        TextButton(onClick = { showPassword = !showPassword }) {
                            Text(if (showPassword) tr("Hide") else tr("Show"), color = Lavender, fontSize = 12.sp)
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = quietFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading
                )

                if (!isRegistering) {
                    TextButton(
                        onClick = { vm.sendPasswordReset(email) },
                        enabled = !isLoading,
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text(tr("Forgot password?"), color = Lavender, fontSize = 13.sp)
                    }
                }

                Spacer(Modifier.height(18.dp))

                Button(
                    onClick = {
                        if (isRegistering) vm.register(displayName, email, password)
                        else vm.signIn(email, password)
                    },
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Lavender, contentColor = Background)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.height(22.dp), color = Background, strokeWidth = 2.dp)
                    } else {
                        Text(if (isRegistering) tr("Create account") else tr("Sign in"), fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(Modifier.height(12.dp))

                val activity = androidx.compose.ui.platform.LocalContext.current as? android.app.Activity
                androidx.compose.material3.OutlinedButton(
                    onClick = { activity?.let { vm.signInWithGoogle(it) } },
                    enabled = !isLoading && activity != null,
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Border)
                ) {
                    androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.foundation.Image(
                            painter = androidx.compose.ui.res.painterResource(R.drawable.ic_google_g),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(tr("Continue with Google"), color = TextPrimary, fontWeight = FontWeight.SemiBold)
                    }
                }

                Spacer(Modifier.height(8.dp))

                TextButton(
                    onClick = {
                        isRegistering = !isRegistering
                        vm.clearMessages()
                    },
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (isRegistering) tr("Already have an account? Sign in") else tr("New to MindTrack? Create an account"),
                        color = Lavender
                    )
                }

                vm.info?.let { info ->
                    Spacer(Modifier.height(10.dp))
                    Text(text = info, color = Mint, fontSize = 13.sp, lineHeight = 19.sp)
                }

                vm.error?.let { errorMessage ->
                    Spacer(Modifier.height(10.dp))
                    Text(text = errorMessage, color = Coral, fontSize = 13.sp)
                }

                Spacer(Modifier.height(20.dp))

                Text(
                    text = tr("Your journey, your pace. Your data is protected by your account."),
                    color = TextSecondary, fontSize = 12.sp, lineHeight = 18.sp
                )
            }
        }
    }
}

/** Turns Firebase's technical errors into something a person can act on. */
internal fun friendlyAuthError(e: Exception): String = when (e) {
    is com.google.firebase.FirebaseNetworkException -> tr("Network error. Check your internet connection.")
    is com.google.firebase.auth.FirebaseAuthWeakPasswordException -> tr("Please choose a stronger password (at least 6 characters).")
    is com.google.firebase.auth.FirebaseAuthUserCollisionException -> tr("An account with this email already exists. Try signing in instead.")
    is com.google.firebase.auth.FirebaseAuthInvalidUserException,
    is com.google.firebase.auth.FirebaseAuthInvalidCredentialsException -> tr("That email and password don't match. Check them and try again, or use \"Forgot password?\".")
    is com.google.firebase.FirebaseTooManyRequestsException -> tr("Too many attempts. Please wait a few minutes and try again.")
    else -> e.localizedMessage ?: tr("Unable to authenticate. Please try again.")
}
