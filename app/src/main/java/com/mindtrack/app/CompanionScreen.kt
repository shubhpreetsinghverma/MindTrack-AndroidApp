package com.mindtrack.app

import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.scrollBy
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun CompanionChat(
    vm: CompanionViewModel,
    records: List<CheckInRecord>,
    streak: Int,
    onOpenExercises: () -> Unit = {},
    onOpenSidebar: () -> Unit = {}
) {
    var message by remember { mutableStateOf("") }
    val messages = vm.messages
    val listState = rememberLazyListState()

    LaunchedEffect(records, streak) { vm.updateContext(records, streak) }
    LaunchedEffect(Unit) { vm.refreshQuota() }
    val outOfMessages = vm.remaining <= 0

    // Follow the newest text, but stop following as soon as the person scrolls up to read.
    var following by remember { mutableStateOf(true) }
    LaunchedEffect(listState) {
        snapshotFlow { listState.lastScrolledBackward to listState.canScrollForward }.collect { (back, canForward) ->
            if (back) following = false
            if (!canForward) following = true
        }
    }
    val lastLength = messages.lastOrNull()?.text?.length ?: 0
    LaunchedEffect(messages.size) {
        following = true
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }
    LaunchedEffect(lastLength) {
        if (following && messages.isNotEmpty()) listState.scrollBy(100_000f)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .padding(horizontal = 18.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.SpaceBetween) {
            // Three short lines: the chat history lives behind this, like the sidebar in Claude.
            Box(
                Modifier.padding(top = 4.dp, end = 12.dp).size(42.dp).clip(RoundedCornerShape(14.dp)).background(SurfaceElevated)
                    .semantics { contentDescription = tr("Chat history") }
                    .clickable(role = Role.Button, onClick = onOpenSidebar),
                contentAlignment = Alignment.Center
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp), horizontalAlignment = Alignment.Start) {
                    Box(Modifier.size(width = 18.dp, height = 2.dp).clip(CircleShape).background(Lavender))
                    Box(Modifier.size(width = 12.dp, height = 2.dp).clip(CircleShape).background(Lavender))
                    Box(Modifier.size(width = 18.dp, height = 2.dp).clip(CircleShape).background(Lavender))
                }
            }
            Box(Modifier.weight(1f)) {
                ScreenHeader(
                    eyebrow = tr("A SPACE WITHOUT JUDGEMENT"),
                    title = tr("Your companion"),
                    subtitle = tr("Sometimes it helps to put things into words.")
                )
            }
            PixelMoon(
                size = 34.dp,
                mood = if (vm.isSending) MoonMood.HAPPY else MoonMood.SLEEPY,
                modifier = Modifier.padding(top = 6.dp).clearAndSetSemantics {}
            )
        }

        Spacer(Modifier.height(15.dp))

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(15.dp),
            contentPadding = PaddingValues(bottom = 14.dp)
        ) {
            itemsIndexed(messages) { index, chat ->
                // The reply that is still arriving is shown through the typing line instead of an empty bubble.
                if (chat.text.isNotEmpty() || !vm.isSending || index != messages.lastIndex) ChatBubble(chat)
            }

            if (vm.isSending) {
                item {
                    Text(
                        if (messages.lastOrNull()?.text.isNullOrEmpty()) tr("MindTrack is listening…") else tr("MindTrack is writing…"),
                        color = Lavender,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }
        }

        vm.errorMessage?.let { error ->
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(error, color = Coral, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.weight(1f))
                if (vm.failedMessage != null) {
                    TextButton(onClick = { vm.retry() }) { Text(tr("Retry"), color = Lavender, fontWeight = FontWeight.SemiBold) }
                }
            }
        }

        if (outOfMessages) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(SurfaceElevated).padding(16.dp)
            ) {
                Text(tr("That's everything for today"), color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(
                    trf("The companion's free messages are used up and refresh at about %1\$s. You can still check in, write in your journal, or look at your Insights in the meantime.", vm.resetLabel),
                    color = TextSecondary, fontSize = 12.sp, lineHeight = 18.sp
                )
                TextButton(onClick = onOpenExercises) { Text(tr("Try a breathing exercise  →"), color = Lavender) }
            }
            Spacer(Modifier.height(12.dp))
        } else {
            Text(
                trf("About %1\$d messages left today · refreshes around %2\$s", vm.remaining, vm.resetLabel),
                color = if (vm.remaining <= 3) Lavender else TextSecondary,
                fontSize = 11.sp,
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }

        val canSend = message.isNotBlank() && !vm.isSending && !outOfMessages
        val sendNow = {
            val text = message.trim()
            if (text.isNotEmpty() && !vm.isSending) {
                message = ""
                vm.send(text)
            }
        }
        OutlinedTextField(
            value = message,
            onValueChange = { if (it.length <= 2000) message = it },
            modifier = Modifier.fillMaxWidth(),
            enabled = !vm.isSending && !outOfMessages,
            placeholder = { Text(tr("What's on your mind?"), color = TextSecondary) },
            shape = RoundedCornerShape(14.dp),
            colors = quietFieldColors(),
            maxLines = 4,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            trailingIcon = {
                Box(
                    Modifier
                        .padding(end = 8.dp)
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (canSend) Lavender else SurfaceElevated)
                        .semantics { contentDescription = tr("Send message") }
                        .clickable(enabled = canSend, role = Role.Button, onClick = sendNow),
                    contentAlignment = Alignment.Center
                ) {
                    Text("↑", color = if (canSend) Background else TextSecondary, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clearAndSetSemantics {})
                }
            }
        )

        Spacer(Modifier.height(8.dp))

        Text(
            tr("An AI wellbeing companion, not a person and not a substitute for professional care."),
            color = TextSecondary,
            fontSize = 10.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
        )
    }
}

@Composable
internal fun ChatBubble(chat: ChatMessage) {
    Column(
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        horizontalAlignment = if (chat.isUser) Alignment.End else Alignment.Start
    ) {
        Text(tr(chat.sender).uppercase(), color = TextSecondary, style = PixelLabelStyle)

        Spacer(Modifier.height(5.dp))

        if (chat.isUser) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.84f)
                    .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 5.dp))
                    .background(TextPrimary)
                    .padding(horizontal = 15.dp, vertical = 12.dp)
            ) {
                Text(chat.text, color = Background, fontSize = 14.sp, lineHeight = 21.sp)
            }
        } else {
            val rendered = remember(chat.text) { markdownToAnnotated(chat.text, Lavender) }
            Text(rendered, color = TextPrimary, fontSize = 15.sp, lineHeight = 23.sp, modifier = Modifier.fillMaxWidth(0.94f))
        }
    }
}
