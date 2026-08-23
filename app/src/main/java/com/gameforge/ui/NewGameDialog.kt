package com.gameforge.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.gameforge.data.LlmProvider
import com.gameforge.llm.LlmManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewGameDialog(
    providers: List<LlmProvider>,
    onDismiss: () -> Unit,
    onGenerate: (LlmProvider, String?) -> Unit,
) {
    var selectedProvider by remember { mutableStateOf(providers.firstOrNull { it.isEnabled }) }
    var genreHint by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }

    val genreOptions = listOf(
        "퍼즐" to "🧩",
        "아케이드" to "👾",
        "전략" to "♟️",
        "액션" to "⚔️",
        "리듬" to "🎵",
        "어드벤처" to "🗺️",
        "시뮬레이션" to "🏙️",
        "카드" to "🃏",
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("새 게임 만들기", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // LLM 프로바이더 선택
                Text("AI 모델", style = MaterialTheme.typography.labelMedium)
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded },
                ) {
                    OutlinedTextField(
                        value = selectedProvider?.name ?: "선택하세요",
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                    ) {
                        providers.filter { it.isEnabled }.forEach { provider ->
                            DropdownMenuItem(
                                text = { Text("${provider.name} (${provider.model})") },
                                onClick = {
                                    selectedProvider = provider
                                    expanded = false
                                },
                            )
                        }
                    }
                }

                // 장르 힌트 (선택)
                Text("장르 힌트 (선택)", style = MaterialTheme.typography.labelMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(genreOptions.size) { index ->
                        val (genre, emoji) = genreOptions[index]
                        FilterChip(
                            selected = genreHint == genre,
                            onClick = {
                                genreHint = if (genreHint == genre) "" else genre
                            },
                            label = { Text("$emoji $genre") },
                        )
                    }
                }

                OutlinedTextField(
                    value = genreHint,
                    onValueChange = { genreHint = it },
                    label = { Text("또는 직접 입력…") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (selectedProvider != null) {
                        onGenerate(selectedProvider!!, genreHint.ifBlank { null })
                    }
                },
                enabled = selectedProvider != null,
            ) {
                Text("생성")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소")
            }
        },
    )
}