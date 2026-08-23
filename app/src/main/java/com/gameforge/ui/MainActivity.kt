package com.gameforge.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gameforge.data.LlmProvider
import com.gameforge.engine.GeckoEngine
import com.gameforge.llm.GameGenerationPipeline
import com.gameforge.llm.LlmManager
import com.gameforge.ui.theme.GameForgeTheme
import kotlinx.coroutines.delay

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
    val coroutineScope = rememberCoroutineScope()
    val activeGames by viewModel.activeGames.collectAsState()
    val currentGame by viewModel.currentGame.collectAsState()
    val gameState by viewModel.gameState.collectAsState()
    val isGenerating by viewModel.isGenerating.collectAsState()
    val isChatVisible by viewModel.isChatVisible.collectAsState()
    val chatMessages by viewModel.chatMessages.collectAsState()
    val error by viewModel.error.collectAsState()
    val generationState by viewModel.generationState.collectAsState()
    val activeProvider by viewModel.activeProvider.collectAsState()

    var isDeleteMode by remember { mutableStateOf(false) }
    var showNewGameDialog by remember { mutableStateOf(false) }
    var chatFraction by remember { mutableFloatStateOf(0.7f) }
    var chatInput by remember { mutableStateOf("") }

    // 기본 프로바이더 설정
    LaunchedEffect(Unit) {
        if (activeProvider == null) {
            LlmManager.DEFAULT_PROVIDERS.firstOrNull { it.isEnabled }?.let {
                viewModel.setActiveProvider(it)
            }
        }
    }

    // 엔진 초기화
    LaunchedEffect(Unit) {
        engine.initialize()
    }

    // 에러 자동 클리어
    LaunchedEffect(error) {
        if (error != null) {
            delay(5000)
            viewModel.clearError()
        }
    }

    when {
        // 게임 생성 중
        isGenerating -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        when (generationState) {
                            is GameGenerationPipeline.GenerationState.Generating ->
                                (generationState as GameGenerationPipeline.GenerationState.Generating).progress
                            is GameGenerationPipeline.GenerationState.Loading ->
                                (generationState as GameGenerationPipeline.GenerationState.Loading).progress
                            is GameGenerationPipeline.GenerationState.Validating ->
                                (generationState as GameGenerationPipeline.GenerationState.Validating).progress
                            else -> "게임 생성 중…"
                        },
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }

        // 게임 플레이 화면
        currentGame != null -> {
            Box(modifier = Modifier.fillMaxSize()) {
                GamePlayScreen(
                    game = currentGame!!,
                    engine = engine,
                    gameState = gameState,
                    onBack = { viewModel.deselectGame() },
                    onOpenChat = { viewModel.toggleChat() },
                    onSave = {
                        coroutineScope.launch {
                            viewModel.saveCurrentState(source = "manual", label = "수동 저장")
                        }
                    },
                    onRestart = {
                        // TODO: 게임 재시작 (초기 상태 복원)
                    },
                    onDelete = {
                        viewModel.deleteGame(currentGame!!.id)
                        viewModel.deselectGame()
                    },
                )

                // 채팅 오버레이
                if (isChatVisible) {
                    DraggableChatOverlay(
                        messages = chatMessages,
                        inputText = chatInput,
                        onInputTextChange = { chatInput = it },
                        onSend = {
                            if (chatInput.isNotBlank()) {
                                viewModel.sendChatMessage(chatInput, engine)
                                chatInput = ""
                            }
                        },
                        isGenerating = isGenerating,
                        fraction = chatFraction,
                        onFractionChange = { chatFraction = it },
                        onClose = { viewModel.toggleChat() },
                    )
                }
            }
        }

        // 대시보드
        else -> {
            DashboardScreen(
                games = activeGames,
                onNewGame = { showNewGameDialog = true },
                onSelectGame = { game ->
                    viewModel.selectGame(game, engine)
                },
                onDeleteGame = { id -> viewModel.deleteGame(id) },
                isDeleteMode = isDeleteMode,
                onToggleDeleteMode = { isDeleteMode = !isDeleteMode },
            )
        }
    }

    // 새 게임 다이얼로그
    if (showNewGameDialog) {
        NewGameDialog(
            providers = LlmManager.DEFAULT_PROVIDERS,
            onDismiss = { showNewGameDialog = false },
            onGenerate = { provider, genreHint ->
                showNewGameDialog = false
                viewModel.setActiveProvider(provider)
                viewModel.generateNewGame(engine, genreHint)
            },
        )
    }

    // 에러 스낵바
    error?.let { msg ->
        Snackbar(
            modifier = Modifier.padding(16.dp),
            action = { TextButton(onClick = { viewModel.clearError() }) { Text("닫기") } },
        ) {
            Text(msg)
        }
    }
}
