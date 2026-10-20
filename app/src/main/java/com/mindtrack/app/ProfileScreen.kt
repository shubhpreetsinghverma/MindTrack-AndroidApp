package com.mindtrack.app

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import com.google.firebase.Firebase
import com.google.firebase.appcheck.appCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.initialize
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

// ------------------------------------------------------------
// PROFILE
// ------------------------------------------------------------


@Composable
internal fun ProfileScreen(
    records: List<CheckInRecord>,
    checkedInToday: Boolean,
    streak: Int = 0,
    totalCheckIns: Int = 0,
    onHistoryClick: () -> Unit = {},
    onInsightsClick: () -> Unit,
    account: AccountViewModel,
    memoryCount: Int = 0,
    memorySince: Long? = null,
    onClearMemory: () -> Unit = {},
    onPrivacyClick: () -> Unit = {},
    onDeleteAccount: suspend (String) -> String? = { null },
    onDeleteAccountWithGoogle: suspend () -> String? = { null },
    onSignOut: () -> Unit
) {
    var showNameDialog by remember { mutableStateOf(false) }

    val email = account.email.ifBlank { tr("No email available") }
    val displayName = account.displayName

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Background),
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = 18.dp,
            bottom = 30.dp
        ),
        verticalArrangement = Arrangement.spacedBy(17.dp)
    ) {
        item {
            ScreenHeader(
                eyebrow = tr("YOUR PERSONAL SPACE"),
                title = tr("Your profile"),
                subtitle = tr("Your journey, at your own pace.")
            )
        }

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(26.dp))
                    .background(SurfaceColor)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(84.dp)
                        .clip(CircleShape)
                        .background(SurfaceSelected),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "♡",
                        color = Lavender,
                        fontSize = 43.sp
                    )
                }

                Spacer(Modifier.height(15.dp))

                Text(
                    displayName,
                    color = TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(6.dp))

                Text(
                    email,
                    color = TextSecondary,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(14.dp))

                Text(
                    tr("✓  Signed in with Firebase"),
                    color = Mint,
                    fontSize = 12.sp
                )

                TextButton(onClick = { showNameDialog = true }) {
                    Text(tr("Edit name"), color = Lavender, fontSize = 13.sp)
                }
            }
        }

        item {
            Column(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(SurfaceColor).padding(20.dp)
            ) {
                Text(tr("Your activity"), color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(5.dp))
                Text(tr("Small steps count. Here is your recent activity."), color = TextSecondary, fontSize = 12.sp)
                Spacer(Modifier.height(15.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ProfileStatCard("$totalCheckIns", tr("Total check-ins"), Modifier.weight(1f))
                    ProfileStatCard("$streak", tr("Day streak"), Modifier.weight(1f))
                    ProfileStatCard(if (checkedInToday) tr("Done") else tr("Not yet"), tr("Today"), Modifier.weight(1f))
                }
                Spacer(Modifier.height(13.dp))
                TextButton(onClick = onInsightsClick) {
                    Text(tr("View mood insights  →"), color = Lavender)
                }
                TextButton(onClick = onHistoryClick) {
                    Text(tr("Browse check-in history  →"), color = Lavender)
                }
            }
        }

        item { VerificationCard(account) }

        item { ReminderCard() }

        item { AppearanceCard() }

        item { CompanionMemoryCard(memoryCount, memorySince, onClearMemory) }

        item { AppLockCard() }

        item { CrashReportsCard() }

        item {
            Column(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(SurfaceColor).padding(20.dp)
            ) {
                Text(tr("Account details"), color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(12.dp))
                Text(tr("SIGN-IN METHOD"), color = TextSecondary, fontSize = 10.sp, letterSpacing = 1.2.sp)
                Spacer(Modifier.height(4.dp))
                Text(tr("Firebase authentication"), color = TextPrimary, fontSize = 14.sp)
                Spacer(Modifier.height(12.dp))
                Text(tr("ACCOUNT EMAIL"), color = TextSecondary, fontSize = 10.sp, letterSpacing = 1.2.sp)
                Spacer(Modifier.height(4.dp))
                Text(email, color = TextPrimary, fontSize = 14.sp)
                Spacer(Modifier.height(12.dp))
                Text(tr("Your reflections are personal. Share only what feels comfortable."), color = TextSecondary, fontSize = 12.sp, lineHeight = 18.sp)
            }
        }

        item { AppInfoCard() }

        item {
            if (showNameDialog) {
                EditNameDialog(
                    current = displayName,
                    onDismiss = { showNameDialog = false },
                    onSave = { name ->
                        val error = account.updateDisplayName(name)
                        if (error == null) showNameDialog = false
                        error
                    }
                )
            }
            Spacer(Modifier.height(2.dp))

            Button(
                onClick = onSignOut,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = SurfaceElevated,
                    contentColor = Coral
                )
            ) {
                Text(
                    tr("Sign out"),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        item {
            TextButton(onClick = onPrivacyClick, modifier = Modifier.fillMaxWidth()) {
                Text(tr("Privacy policy"), color = Lavender)
            }
            DeleteAccountSection(usesPassword = account.usesPassword, onDelete = onDeleteAccount, onDeleteWithGoogle = onDeleteAccountWithGoogle)
        }
    }
}

@Composable
private fun AppInfoCard() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "—"
    }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(SurfaceColor).padding(20.dp)) {
        Text(tr("About this app"), color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))
        Text(tr("VERSION"), color = TextSecondary, fontSize = 10.sp, letterSpacing = 1.2.sp)
        Spacer(Modifier.height(4.dp))
        Text("MindTrack $version", color = TextPrimary, fontSize = 14.sp)
        Spacer(Modifier.height(12.dp))
        Text(
            tr("MindTrack is a wellbeing companion. It is not a medical service, and it can't contact emergency services."),
            color = TextSecondary, fontSize = 12.sp, lineHeight = 18.sp
        )
        TextButton(onClick = {
            val intent = android.content.Intent(android.content.Intent.ACTION_SENDTO).apply {
                data = android.net.Uri.parse("mailto:")
                putExtra(android.content.Intent.EXTRA_SUBJECT, "MindTrack feedback (v$version)")
            }
            runCatching { context.startActivity(intent) }
        }) { Text(tr("Send feedback  →"), color = Lavender) }
    }
}

@Composable
private fun EditNameDialog(current: String, onDismiss: () -> Unit, onSave: suspend (String) -> String?) {
    var name by remember { mutableStateOf(current) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    androidx.compose.material3.AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        containerColor = SurfaceColor,
        title = { Text(tr("Your name"), color = TextPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                androidx.compose.material3.OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 100) { name = it; error = null } },
                    singleLine = true,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                )
                error?.let { Text(it, color = Coral, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp)) }
            }
        },
        confirmButton = {
            Button(
                enabled = name.isNotBlank() && !busy,
                onClick = {
                    scope.launch {
                        busy = true
                        error = onSave(name)
                        busy = false
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Lavender, contentColor = Background)
            ) { Text(if (busy) tr("Saving…") else tr("Save")) }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text(tr("Cancel"), color = TextSecondary) } }
    )
}
