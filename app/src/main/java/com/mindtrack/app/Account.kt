package com.mindtrack.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/** Non-blocking nudge to verify the email address. Disappears once verified. */
@Composable
internal fun VerificationCard(account: AccountViewModel) {
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    if (account.emailVerified) return

    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(SurfaceElevated).padding(20.dp)
    ) {
        Text(tr("Verify your email"), color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(
            trf("We sent a link to %1\$s. Verifying helps you recover your account if you forget your password.", account.email),
            color = TextSecondary, fontSize = 12.sp, lineHeight = 18.sp
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(
                enabled = !busy,
                onClick = { scope.launch { busy = true; status = account.sendVerification(); busy = false } }
            ) { Text(tr("Resend email"), color = Lavender) }
            TextButton(
                enabled = !busy,
                onClick = { scope.launch { busy = true; status = account.checkVerified(); busy = false } }
            ) { Text(tr("I've verified"), color = Lavender) }
        }
        status?.let { Text(it, color = TextSecondary, fontSize = 12.sp) }
    }
}

/** tr("Delete account") with a confirmation dialog that asks for the password. */
@Composable
internal fun DeleteAccountSection(usesPassword: Boolean, onDelete: suspend (password: String) -> String?, onDeleteWithGoogle: suspend () -> String?) {
    var showDialog by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    TextButton(onClick = { showDialog = true }, modifier = Modifier.fillMaxWidth()) {
        Text(tr("Delete my account and data"), color = Coral)
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { if (!busy) showDialog = false },
            containerColor = SurfaceColor,
            title = { Text(tr("Delete your account?"), color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        if (usesPassword) tr("This permanently deletes your account, every check-in and every journal note, on this device and in the cloud. It cannot be undone.\n\nEnter your password to confirm.")
                        else tr("This permanently deletes your account, every check-in and every journal note, on this device and in the cloud. It cannot be undone.\n\nYou'll be asked to confirm with Google."),
                        color = TextSecondary, fontSize = 13.sp, lineHeight = 19.sp
                    )
                    if (usesPassword) {
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it; error = null },
                            label = { Text(tr("Password")) },
                            singleLine = true,
                            enabled = !busy,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    error?.let {
                        Text(it, color = Coral, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = (!usesPassword || password.isNotEmpty()) && !busy,
                    onClick = {
                        scope.launch {
                            busy = true
                            error = if (usesPassword) onDelete(password) else onDeleteWithGoogle()
                            busy = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Coral, contentColor = Background)
                ) { Text(if (busy) tr("Deleting…") else tr("Delete everything")) }
            },
            dismissButton = {
                TextButton(enabled = !busy, onClick = { showDialog = false; password = ""; error = null }) {
                    Text(tr("Cancel"), color = TextSecondary)
                }
            }
        )
    }
}
