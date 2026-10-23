package com.mindtrack.app

import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val RESEND_COOLDOWN_SECONDS = 30

/**
 * Shown right after sign-up (and on sign-in) until the email address is confirmed. The app stays closed
 * behind it; it opens by itself as soon as the link has been tapped.
 */
@Composable
internal fun VerifyEmailScreen(vm: AuthViewModel) {
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var cooldown by remember { mutableIntStateOf(RESEND_COOLDOWN_SECONDS) }   // the sign-up already sent one

    // Check quietly when the person comes back from their mail app, and every few seconds while here.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) scope.launch { vm.recheckVerification(quiet = true) }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(4_000)
            vm.recheckVerification(quiet = true)
        }
    }
    LaunchedEffect(cooldown) {
        if (cooldown > 0) { delay(1_000); cooldown-- }
    }

    VerifyEmailContent(
        email = vm.email,
        status = status,
        busy = busy,
        resendCooldown = cooldown,
        onCheck = { scope.launch { busy = true; status = vm.recheckVerification(); busy = false } },
        onResend = {
            scope.launch {
                busy = true
                status = vm.resendVerification()
                cooldown = RESEND_COOLDOWN_SECONDS
                busy = false
            }
        },
        onSignOut = { vm.signOutUnverified() }
    )
}

@Composable
internal fun VerifyEmailContent(
    email: String,
    status: String?,
    busy: Boolean,
    resendCooldown: Int,
    onCheck: () -> Unit,
    onResend: () -> Unit,
    onSignOut: () -> Unit
) {
    Surface(modifier = Modifier.fillMaxSize(), color = Background) {
        Box(
            modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.Center
            ) {
                PixelMoon(size = 56.dp, mood = MoonMood.SLEEPY, modifier = Modifier.clearAndSetSemantics {})
                Spacer(Modifier.height(18.dp))
                Text(tr("Check your email"), color = TextPrimary, fontSize = 30.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(10.dp))
                Text(
                    trf("We sent a verification link to %1\$s. Tap the link in that email, then come back here.", email),
                    color = TextSecondary, fontSize = 15.sp, lineHeight = 22.sp
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    tr("MindTrack opens as soon as your address is confirmed. Can't see it? Check your spam folder."),
                    color = TextSecondary, fontSize = 13.sp, lineHeight = 20.sp
                )
                Spacer(Modifier.height(26.dp))

                Button(
                    onClick = onCheck,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Lavender, contentColor = Background)
                ) { Text(tr("I've verified"), fontWeight = FontWeight.Bold) }

                Spacer(Modifier.height(6.dp))
                TextButton(onClick = onResend, enabled = !busy && resendCooldown <= 0, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        if (resendCooldown > 0) trf("Resend email (%1\$d)", resendCooldown) else tr("Resend email"),
                        color = if (resendCooldown > 0) TextSecondary else Lavender
                    )
                }
                status?.let {
                    Text(it, color = TextSecondary, fontSize = 13.sp, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
                }
                TextButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) {
                    Text(tr("Use a different account"), color = TextSecondary)
                }
            }
        }
    }
}
