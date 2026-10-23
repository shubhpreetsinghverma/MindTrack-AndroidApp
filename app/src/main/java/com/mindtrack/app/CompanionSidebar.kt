package com.mindtrack.app

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/** The companion chat with a sidebar of earlier chats, like the one in Claude. */
@Composable
internal fun CompanionScreen(
    vm: CompanionViewModel,
    records: List<CheckInRecord>,
    streak: Int,
    onOpenExercises: () -> Unit = {}
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = SurfaceColor) {
                ChatHistoryPanel(
                    vm = vm,
                    onPicked = { scope.launch { drawerState.close() } }
                )
            }
        }
    ) {
        CompanionChat(
            vm = vm, records = records, streak = streak, onOpenExercises = onOpenExercises,
            onOpenSidebar = { scope.launch { drawerState.open() } }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ChatHistoryPanel(vm: CompanionViewModel, onPicked: () -> Unit) {
    var toDelete by remember { mutableStateOf<ConversationSummary?>(null) }
    var confirmAll by remember { mutableStateOf(false) }
    val now = System.currentTimeMillis()

    Column(Modifier.fillMaxHeight().statusBarsPadding().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 18.dp)) {
        Text(tr("Chats"), color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(14.dp))
        Button(
            onClick = { vm.newChat(); onPicked() },
            enabled = !vm.isSending,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Lavender, contentColor = Background)
        ) { Text(tr("New chat"), fontWeight = FontWeight.Bold) }
        Spacer(Modifier.height(14.dp))

        if (vm.conversations.isEmpty()) {
            Text(
                tr("No saved chats yet. Your conversations will appear here."),
                color = TextSecondary, fontSize = 13.sp, lineHeight = 19.sp,
                modifier = Modifier.weight(1f).padding(top = 6.dp)
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(bottom = 8.dp)
            ) {
                items(vm.conversations, key = { it.id }) { chat ->
                    val selected = chat.id == vm.currentId
                    val ago = daysAgo(chat.lastActivity, now)
                    val agoText = when (ago) { 0 -> tr("Today"); 1 -> tr("Yesterday"); else -> trf("%1\$d days ago", ago) }
                    val left = daysUntilDeleted(chat.lastActivity, now)
                    val leftText = when (left) { 0 -> tr("Deletes today"); 1 -> tr("Deletes tomorrow"); else -> trf("Deletes in %1\$d days", left) }
                    Column(
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (selected) SurfaceSelected else SurfaceElevated)
                            .combinedClickable(
                                enabled = !vm.isSending || selected,
                                role = Role.Button,
                                onClickLabel = tr("Open chat"),
                                onLongClickLabel = tr("Delete this chat"),
                                onClick = { vm.openConversation(chat.id); onPicked() },
                                onLongClick = { toDelete = chat }
                            )
                            .padding(14.dp)
                    ) {
                        Text(
                            if (chat.title == "New chat") tr("New chat") else chat.title,
                            color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                            maxLines = 2, overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(trf("%1\$s  ·  %2\$s", agoText, leftText), color = TextSecondary, fontSize = 11.sp)
                    }
                }
            }
        }

        Text(
            tr("Chats are kept on this device only and deleted automatically 7 days after your last message. Press and hold a chat to delete it."),
            color = TextSecondary, fontSize = 11.sp, lineHeight = 16.sp, modifier = Modifier.padding(vertical = 8.dp)
        )
        if (vm.conversations.isNotEmpty()) {
            TextButton(onClick = { confirmAll = true }, modifier = Modifier.fillMaxWidth()) {
                Text(tr("Delete all chats"), color = Coral)
            }
        }
    }

    toDelete?.let { chat ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            containerColor = SurfaceColor,
            title = { Text(tr("Delete this chat?"), color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    tr("This chat will be deleted from this device. Your check-ins are not affected."),
                    color = TextSecondary, fontSize = 13.sp, lineHeight = 19.sp
                )
            },
            confirmButton = { TextButton(onClick = { vm.deleteConversation(chat.id); toDelete = null }) { Text(tr("Delete"), color = Coral) } },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text(tr("Cancel"), color = TextSecondary) } }
        )
    }
    if (confirmAll) {
        AlertDialog(
            onDismissRequest = { confirmAll = false },
            containerColor = SurfaceColor,
            title = { Text(tr("Delete all chats?"), color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    tr("This deletes every chat with the companion from this device. Your check-ins are not affected."),
                    color = TextSecondary, fontSize = 13.sp, lineHeight = 19.sp
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.deleteAllConversations(); confirmAll = false; onPicked() }) { Text(tr("Delete"), color = Coral) }
            },
            dismissButton = { TextButton(onClick = { confirmAll = false }) { Text(tr("Cancel"), color = TextSecondary) } }
        )
    }
}
