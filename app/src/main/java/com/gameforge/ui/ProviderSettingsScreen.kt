package com.gameforge.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.gameforge.data.LlmProvider
import com.gameforge.llm.LlmManager

/**
 * 프로바이더 설정 화면.
 * API 키, Base URL, 모델명을 편집 가능하게.
 * Room DB에 영속화, 저장 시 LlmManager 클라이언트 캐시 무효화.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderSettingsScreen(
    providers: List<LlmProvider>,
    onUpdateProvider: (LlmProvider) -> Unit,
    onBack: () -> Unit,
) {
    var selectedProvider by remember { mutableStateOf(providers.firstOrNull { it.isEnabled }) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("⚙️ 프로바이더 설정") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "뒤로")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
        }
    ) { padding ->
        if (providers.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text("프로바이더가 없습니다", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // 프로바이더 선택 탭
                ScrollableTabRow(
                    selectedTabIndex = providers.indexOf(selectedProvider).coerceAtLeast(0),
                ) {
                    providers.forEach { provider ->
                        Tab(
                            selected = provider.id == selectedProvider?.id,
                            onClick = { selectedProvider = provider },
                            text = { Text(provider.name) },
                        )
                    }
                }

                selectedProvider?.let { provider ->
                    ProviderEditor(
                        provider = provider,
                        onSave = { updated ->
                            onUpdateProvider(updated)
                            selectedProvider = updated
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ProviderEditor(
    provider: LlmProvider,
    onSave: (LlmProvider) -> Unit,
) {
    var name by remember(provider.id) { mutableStateOf(provider.name) }
    var baseUrl by remember(provider.id) { mutableStateOf(provider.baseUrl) }
    var model by remember(provider.id) { mutableStateOf(provider.model) }
    var apiKey by remember(provider.id) { mutableStateOf(provider.apiKey ?: "") }
    var isEnabled by remember(provider.id) { mutableStateOf(provider.isEnabled) }
    var showApiKey by remember(provider.id) { mutableStateOf(false) }
    var hasUnsavedChanges by remember(provider.id) { mutableStateOf(false) }

    // 프로바이더별 기본값
    val defaults = LlmManager.DEFAULT_PROVIDERS.find { it.id == provider.id }
    val isOllama = provider.id == "ollama"

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // 활성화 토글
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("활성화", style = MaterialTheme.typography.titleSmall)
            Switch(
                checked = isEnabled,
                onCheckedChange = {
                    isEnabled = it
                    hasUnsavedChanges = true
                },
            )
        }

        HorizontalDivider()

        // 이름
        OutlinedTextField(
            value = name,
            onValueChange = {
                name = it
                hasUnsavedChanges = true
            },
            label = { Text("이름") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        // Base URL
        OutlinedTextField(
            value = baseUrl,
            onValueChange = {
                baseUrl = it
                hasUnsavedChanges = true
            },
            label = { Text("Base URL") },
            placeholder = { Text(defaults?.baseUrl ?: "https://api.example.com") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            trailingIcon = {
                if (baseUrl != (defaults?.baseUrl ?: "")) {
                    IconButton(onClick = {
                        baseUrl = defaults?.baseUrl ?: ""
                        hasUnsavedChanges = true
                    }) {
                        Icon(Icons.Default.RestartAlt, contentDescription = "기본값 복원")
                    }
                }
            },
        )

        // 모델명
        OutlinedTextField(
            value = model,
            onValueChange = {
                model = it
                hasUnsavedChanges = true
            },
            label = { Text("모델") },
            placeholder = { Text(defaults?.model ?: "model-name") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            trailingIcon = {
                if (model != (defaults?.model ?: "")) {
                    IconButton(onClick = {
                        model = defaults?.model ?: ""
                        hasUnsavedChanges = true
                    }) {
                        Icon(Icons.Default.RestartAlt, contentDescription = "기본값 복원")
                    }
                }
            },
        )

        // API 키 (Ollama는 불필요)
        if (!isOllama) {
            OutlinedTextField(
                value = apiKey,
                onValueChange = {
                    apiKey = it
                    hasUnsavedChanges = true
                },
                label = { Text("API 키") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = if (showApiKey) {
                    androidx.compose.ui.text.input.VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                trailingIcon = {
                    Row {
                        IconButton(onClick = { showApiKey = !showApiKey }) {
                            Icon(
                                if (showApiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (showApiKey) "숨기기" else "보기",
                            )
                        }
                    }
                },
            )
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                ),
            ) {
                Text(
                    "Ollama Cloud은 API 키가 필요하지 않습니다",
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 저장 버튼
        Button(
            onClick = {
                onSave(
                    provider.copy(
                        name = name,
                        baseUrl = baseUrl,
                        model = model,
                        apiKey = apiKey.ifBlank { null },
                        isEnabled = isEnabled,
                    )
                )
                hasUnsavedChanges = false
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = hasUnsavedChanges && baseUrl.isNotBlank() && model.isNotBlank(),
        ) {
            Icon(Icons.Default.Save, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("저장")
        }

        // 변경 안내
        if (!hasUnsavedChanges && provider.id == selectedProvider?.id) {
            Text(
                "변경사항이 없습니다",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            )
        }
    }
}
