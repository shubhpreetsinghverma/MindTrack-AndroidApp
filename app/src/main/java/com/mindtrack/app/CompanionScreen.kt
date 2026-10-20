package com.mindtrack.app

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
internal fun CompanionScreen(
    vm: CompanionViewModel,
    records: List<CheckInRecord>,
    streak: Int,
    onOpenExercises: () -> Unit = {}
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
            Box(Modifier.weight(1f)) {
                ScreenHeader(
                    eyebrow = tr("A SPACE WITHOUT JUDGEMENT"),
                    title = tr("Your companion"),
                    subtitle = tr("Sometimes it helps to put things into words.")
                )
            }
            if (messages.size > 1) {
                TextButton(onClick = { vm.newChat() }, enabled = !vm.isSending) {
                    Text(tr("New chat"), color = if (vm.isSending) TextSecondary else Lavender, fontSize = 12.sp)
                }
            }
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

        OutlinedTextField(
            value = message,
            onValueChange = { if (it.length <= 2000) message = it },
            modifier = Modifier.fillMaxWidth(),
            enabled = !vm.isSending && !outOfMessages,
            placeholder = { Text(tr("What's on your mind?"), color = TextSecondary) },
            shape = RoundedCornerShape(18.dp),
            maxLines = 4,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
        )

        Spacer(Modifier.height(9.dp))

        Button(
            onClick = {
                val text = message.trim()
                if (text.isNotEmpty() && !vm.isSending) {
                    message = ""
                    vm.send(text)
                }
            },
            enabled = message.isNotBlank() && !vm.isSending && !outOfMessages,
            modifier = Modifier.fillMaxWidth().height(51.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Lavender, contentColor = Background)
        ) {
            Text(if (vm.isSending) tr("Thinking…") else tr("Send message  →"), fontWeight = FontWeight.Bold)
        }

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
        Text(tr(chat.sender), color = Lavender, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)

        Spacer(Modifier.height(5.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (chat.isUser) Arrangement.End else Arrangement.Start
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.88f)
                    .clip(
                        RoundedCornerShape(
                            topStart = 19.dp,
                            topEnd = 19.dp,
                            bottomStart = if (chat.isUser) 19.dp else 5.dp,
                            bottomEnd = if (chat.isUser) 5.dp else 19.dp
                        )
                    )
                    .background(if (chat.isUser) SurfaceSelected else SurfaceColor)
                    .padding(15.dp)
            ) {
                if (chat.isUser) {
                    Text(chat.text, color = TextPrimary, fontSize = 14.sp, lineHeight = 21.sp)
                } else {
                    val rendered = remember(chat.text) { markdownToAnnotated(chat.text, Lavender) }
                    Text(rendered, color = TextPrimary, fontSize = 14.sp, lineHeight = 21.sp)
                }
            }
        }
    }
}
