package com.gameforge.ui

import androidx.compose.animation.*
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.gameforge.data.ChatMessage

/**
 * DraggableChatOverlay — DevCompanion 패턴 재사용.
 * 하단에서 드래그하여 열리는 채팅 패널.
 */
@Composable
fun DraggableChatOverlay(
    messages: List<ChatMessage>,
    inputText: String,
    onInputTextChange: (String) -> Unit,
    onSend: () -> Unit,
    isGenerating: Boolean,
    fraction: Float = 0.7f,
    onFractionChange: (Float) -> Unit,
    onClose: () -> Unit,
) {
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val currentFraction = (fraction + dragOffset).coerceIn(0.15f, 0.95f)

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val maxHeight = constraints.maxHeight.toFloat()

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(currentFraction)
                .align(Alignment.BottomCenter)
                .pointerInput(Unit) {
                    detectVerticalDragGestures { _, dragAmount ->
                        dragOffset += -dragAmount / maxHeight
                        val newFraction = (fraction + dragOffset).coerceIn(0.15f, 0.95f)
                        onFractionChange(newFraction)
                        dragOffset = 0f // reset after applying
                    }
                },
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp,
                shape = MaterialTheme.shapes.large.copy(
                    bottomStart = CornerSize(0.dp),
                    bottomEnd = CornerSize(0.dp),
                ),
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // 드래그 핸들 + 헤더
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // 드래그 핸들
                        Box(
                            modifier = Modifier
                                .width(32.dp)
                                .height(4.dp)
                                .align(Alignment.CenterVertically),
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            "💬 게임 채팅",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        IconButton(onClick = onClose) {
                            Icon(Icons.Default.Close, contentDescription = "닫기")
                        }
                    }

                    HorizontalDivider()

                    // 메시지 목록
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(messages, key = { it.id }) { message ->
                            ChatBubble(message = message)
                        }
                    }

                    // 입력 영역
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = inputText,
                            onValueChange = onInputTextChange,
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("게임에 대해 이야기하세요…") },
                            maxLines = 3,
                            enabled = !isGenerating,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = onSend,
                            enabled = inputText.isNotBlank() && !isGenerating,
                        ) {
                            if (isGenerating) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                Icon(Icons.Default.Send, contentDescription = "전송")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatBubble(message: ChatMessage) {
    val isUser = message.role == "user"
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            color = if (isUser)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.secondaryContainer,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.widthIn(max = 280.dp),
        ) {
            Text(
                text = message.content,
                modifier = Modifier.padding(10.dp),
                style = MaterialTheme.typography.bodySmall,
                color = if (isUser)
                    MaterialTheme.colorScheme.onPrimaryContainer
                else
                    MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}