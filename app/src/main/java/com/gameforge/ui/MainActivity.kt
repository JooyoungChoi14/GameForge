package com.gameforge.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gameforge.data.GameEntry
import com.gameforge.engine.GeckoEngine
import com.gameforge.ui.theme.GameForgeTheme

class MainActivity : ComponentActivity() {
    private lateinit var engine: GeckoEngine

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        engine = GeckoEngine(this)

        setContent {
            GameForgeTheme {
                GameForgeApp(
                    engine = engine,
                )
            }
        }
    }

    override fun onPause() {
        super.onPause()
        engine.pauseGame()
    }

    override fun onResume() {
        super.onResume()
        engine.resumeGame()
    }

    override fun onDestroy() {
        super.onDestroy()
        engine.shutdown()
    }
}

@Composable
fun GameForgeApp(engine: GeckoEngine) {
    val viewModel: GameViewModel = viewModel()
    val activeGames by viewModel.activeGames.collectAsState()
    val currentGame by viewModel.currentGame.collectAsState()
    val gameState by viewModel.gameState.collectAsState()
    val isGenerating by viewModel.isGenerating.collectAsState()
    val chatMessages by viewModel.chatMessages.collectAsState()
    val error by viewModel.error.collectAsState()

    var isDeleteMode by remember { mutableStateOf(false) }
    var showChat by remember { mutableStateOf(false) }
    var chatFraction by remember { mutableFloatStateOf(0.7f) }
    var chatInput by remember { mutableStateOf("") }

    // 엔진 초기화
    LaunchedEffect(Unit) {
        engine.initialize()
    }

    when {
        currentGame != null -> {
            // 게임 플레이 화면
            Box(modifier = Modifier.fillMaxSize()) {
                GamePlayScreen(
                    game = currentGame!!,
                    engine = engine,
                    gameState = gameState,
                    onBack = {
                        viewModel.deselectGame()
                    },
                    onOpenChat = { showChat = true },
                    onSave = {
                        // TODO: 수동 저장
                    },
                    onRestart = {
                        // TODO: 게임 재시작
                    },
                    onDelete = {
                        viewModel.deleteGame(currentGame!!.id)
                        viewModel.deselectGame()
                    },
                )

                // 채팅 오버레이
                if (showChat) {
                    DraggableChatOverlay(
                        messages = chatMessages,
                        inputText = chatInput,
                        onInputTextChange = { chatInput = it },
                        onSend = {
                            if (chatInput.isNotBlank()) {
                                // TODO: LLM 채팅 전송
                                chatInput = ""
                            }
                        },
                        isGenerating = isGenerating,
                        fraction = chatFraction,
                        onFractionChange = { chatFraction = it },
                        onClose = { showChat = false },
                    )
                }
            }

            // 에러 표시
            error?.let { msg ->
                Snackbar(
                    modifier = Modifier.padding(16.dp),
                ) {
                    Text(msg)
                }
            }
        }

        else -> {
            // 대시보드
            DashboardScreen(
                games = activeGames,
                onNewGame = {
                    // TODO: 새 게임 생성 플로우
                },
                onSelectGame = { game ->
                    viewModel.selectGame(game)
                    // TODO: 게임 로드 + 엔진에 HTML 주입
                },
                onDeleteGame = { id ->
                    viewModel.deleteGame(id)
                },
                isDeleteMode = isDeleteMode,
                onToggleDeleteMode = { isDeleteMode = !isDeleteMode },
            )
        }
    }
}