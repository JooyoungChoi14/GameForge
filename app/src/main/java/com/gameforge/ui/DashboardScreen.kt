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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    games: List<GameEntry>,
    onNewGame: () -> Unit,
    onSelectGame: (GameEntry) -> Unit,
    onDeleteGame: (String) -> Unit,
    isDeleteMode: Boolean,
    onToggleDeleteMode: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🎮 GameForge") },
                actions = {
                    IconButton(onClick = onToggleDeleteMode) {
                        Icon(
                            if (isDeleteMode) Icons.Default.Close else Icons.Default.Delete,
                            contentDescription = if (isDeleteMode) "삭제 모드 종료" else "삭제 모드"
                        )
                    }
                    IconButton(onClick = onNewGame) {
                        Icon(Icons.Default.Add, contentDescription = "새 게임")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
        }
    ) { padding ->
        if (games.isEmpty()) {
            // 빈 상태
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "아직 게임이 없습니다",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "+ 버튼을 눌러 새 게임을 만드세요",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding),
                contentPadding = PaddingValues(16.dp),
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