package com.gameforge.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gameforge.data.GameEntry

@Composable
fun DashboardScreen(
    games: List<GameEntry>,
    onNewGame: () -> Unit,
    onSelectGame: (GameEntry) -> Unit,
    onDeleteGame: (String) -> Unit,
    isDeleteMode: Boolean,
    onToggleDeleteMode: () -> Unit,
    onOpenSettings: () -> Unit = {},
) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (games.isEmpty()) {
            // 빈 상태 — 세 개 버튼을 중앙에 배치
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "🎮 GameForge",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "아직 게임이 없습니다",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(32.dp))
                Button(
                    onClick = onNewGame,
                    modifier = Modifier.fillMaxWidth(0.6f),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("새 게임 만들기")
                }
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.fillMaxWidth(0.6f),
                ) {
                    Icon(Icons.Default.Settings, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("프로바이더 설정")
                }
            }
        } else {
            // 게임 목록 — 상단에 액션 버튼 행
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "설정")
                    }
                    if (isDeleteMode) {
                        Button(onClick = onToggleDeleteMode) {
                            Icon(Icons.Default.Close, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("완료")
                        }
                    } else {
                        OutlinedButton(onClick = onToggleDeleteMode) {
                            Icon(Icons.Default.Delete, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("삭제")
                        }
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Button(onClick = onNewGame) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("새 게임")
                    }
                }

                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(games, key = { it.id }) { game ->
                        GameCard(
                            game = game,
                            onClick = { onSelectGame(game) },
                            onDelete = { onDeleteGame(game.id) },
                            isDeleteMode = isDeleteMode,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GameCard(
    game: GameEntry,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    isDeleteMode: Boolean,
) {
    val elapsedMs = System.currentTimeMillis() - game.lastPlayedAt
    val timeAgo = when {
        elapsedMs < 60_000 -> "방금 전"
        elapsedMs < 3_600_000 -> "${elapsedMs / 60_000}분 전"
        elapsedMs < 86_400_000 -> "${elapsedMs / 3_600_000}시간 전"
        else -> "${elapsedMs / 86_400_000}일 전"
    }

    Card(
        onClick = if (isDeleteMode) onDelete else onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isDeleteMode)
                MaterialTheme.colorScheme.errorContainer
            else
                MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = game.emoji,
                fontSize = 32.sp,
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = game.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "$timeAgo · ${"★".repeat(game.difficulty)}${"☆".repeat(5 - game.difficulty)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (isDeleteMode) {
                Icon(
                    Icons.Default.DeleteForever,
                    contentDescription = "삭제",
                    tint = MaterialTheme.colorScheme.error,
                )
            } else {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = "플레이",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}