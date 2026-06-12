package com.codex.suishouledger.ui.chat

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.codex.suishouledger.ServiceLocator
import com.codex.suishouledger.data.local.AccountEntity
import com.codex.suishouledger.data.local.CategoryEntity
import com.codex.suishouledger.data.local.IngestionState
import com.codex.suishouledger.data.local.LedgerEntryEntity
import com.codex.suishouledger.data.local.ReviewState
import com.codex.suishouledger.data.local.SourceType
import com.codex.suishouledger.data.local.TransactionType
import com.codex.suishouledger.data.remote.ChatMessageDto
import com.codex.suishouledger.data.remote.ChatMessageRequest
import com.codex.suishouledger.data.remote.ChatSessionResponse
import com.codex.suishouledger.data.remote.RetrofitClient
import com.google.gson.JsonElement
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.launch
import java.math.RoundingMode
import java.security.MessageDigest
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import java.util.UUID

@Composable
fun ChatScreen(
    isLoggedIn: Boolean,
    onBack: () -> Unit,
    onRequestAuth: () -> Unit
) {
    val api = remember { RetrofitClient.apiService }
    val tokenProvider = remember { ServiceLocator.authTokenProvider }
    val categories by ServiceLocator.repository.categories.collectAsState(initial = emptyList())
    val accounts by ServiceLocator.repository.accounts.collectAsState(initial = emptyList())
    val confirmedEntries by ServiceLocator.repository.confirmedEntries.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var currentSession by remember { mutableStateOf<ChatSessionResponse?>(null) }
    var messages by remember { mutableStateOf<List<ChatMessageDto>>(emptyList()) }
    var input by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var localMessageId by remember { mutableStateOf(-1L) }

    suspend fun bearerToken(): String? = tokenProvider.getBearerToken()

    suspend fun handleAuthExpired(code: Int): Boolean {
        if (code != 401 && code != 403) return false
        tokenProvider.clearExpiredAuth()
        currentSession = null
        messages = emptyList()
        error = "登录已过期，请重新登录后继续使用 AI 对话"
        return true
    }

    suspend fun createSession(title: String = "AI 记账对话"): ChatSessionResponse? {
        val token = bearerToken() ?: run {
            loading = false
            error = "请先登录后使用 AI 对话"
            return null
        }
        val response = api.createChatSession(token, mapOf("title" to title))
        return when {
            response.isSuccessful -> response.body()
            handleAuthExpired(response.code()) -> null
            else -> {
                error = "对话创建失败 (${response.code()})"
                null
            }
        }
    }

    suspend fun ensureSession(): ChatSessionResponse? {
        currentSession?.let { return it }
        val token = bearerToken() ?: run {
            loading = false
            error = "请先登录后使用 AI 对话"
            return null
        }
        val response = api.getChatSessions(token)
        if (response.isSuccessful) {
            val loaded = response.body().orEmpty()
            val session = loaded.firstOrNull() ?: createSession()
            currentSession = session
            return session
        }
        return if (handleAuthExpired(response.code())) {
            null
        } else {
            loading = false
            error = "会话加载失败 (${response.code()})"
            null
        }
    }

    suspend fun reloadMessages() {
        loading = true
        val session = ensureSession() ?: run {
            loading = false
            return
        }
        val token = bearerToken() ?: run {
            loading = false
            error = "请先登录后使用 AI 对话"
            return
        }
        val response = api.getChatMessages(token, session.id)
        if (response.isSuccessful) {
            messages = response.body().orEmpty()
            error = null
        } else if (handleAuthExpired(response.code())) {
            return
        } else {
            error = "消息加载失败 (${response.code()})"
        }
        loading = false
    }

    suspend fun startNewConversation() {
        input = ""
        error = null
        messages = emptyList()
        currentSession = null
        loading = true
        currentSession = createSession()
        loading = false
    }

    suspend fun sendMessage() {
        val content = input.trim()
        if (content.isBlank() || sending) return
        buildLocalLedgerQuestionReply(content, confirmedEntries)?.let { localReply ->
            localMessageId -= 2
            val userMessage = ChatMessageDto(
                id = localMessageId,
                role = "USER",
                content = content
            )
            val assistantMessage = ChatMessageDto(
                id = localMessageId - 1,
                role = "ASSISTANT",
                content = localReply
            )
            localMessageId -= 1
            messages = messages + userMessage + assistantMessage
            input = ""
            error = null
            return
        }
        buildImmediateDraftReply(content, localMessageId, categories, accounts)?.let { assistantMessage ->
            localMessageId -= 2
            val userMessage = ChatMessageDto(
                id = localMessageId,
                role = "USER",
                content = content
            )
            val localAssistantMessage = assistantMessage.copy(id = localMessageId - 1)
            localMessageId -= 1
            messages = messages + userMessage + localAssistantMessage
            input = ""
            error = null
            return
        }
        val session = ensureSession() ?: return
        val token = bearerToken() ?: run {
            error = "请先登录后使用 AI 对话"
            return
        }
        sending = true
        input = ""
        try {
            val response = api.sendChatMessage(
                token,
                session.id,
                ChatMessageRequest(
                    content = content,
                    ledgerContext = buildLocalLedgerContext(confirmedEntries)
                )
            )
            if (response.isSuccessful) {
                val body = response.body()
                val appended = buildList {
                    addAll(messages)
                    body?.userMessage?.let { add(it) }
                    body?.assistantMessage?.let { add(it) }
                }
                messages = if (appended.isNotEmpty()) appended else messages
                error = null
                if (body?.assistantMessage == null && body?.userMessage == null) {
                    reloadMessages()
                }
            } else if (handleAuthExpired(response.code())) {
                input = content
                return
            } else {
                input = content
                error = "发送失败 (${response.code()})"
            }
        } catch (e: Exception) {
            input = content
            error = "网络连接失败: ${e.localizedMessage ?: "未知错误"}"
        } finally {
            sending = false
        }
    }

    LaunchedEffect(isLoggedIn) {
        if (isLoggedIn) {
            reloadMessages()
        } else {
            loading = false
            currentSession = null
            messages = emptyList()
            error = null
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    Surface(color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ChatHeader(
                loading = loading,
                canRefresh = isLoggedIn,
                onBack = onBack,
                onRefresh = { scope.launch { startNewConversation() } }
            )

            if (!isLoggedIn) {
                ChatAuthCard(onRequestAuth = onRequestAuth)
            } else {
                error?.let { ErrorCard(message = it, onDismiss = { error = null }) }

                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    when {
                        loading && messages.isEmpty() -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                        messages.isEmpty() -> EmptyChatState(modifier = Modifier.align(Alignment.Center))
                        else -> LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            state = listState,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(vertical = 4.dp)
                        ) {
                            items(messages, key = { it.id }) { message ->
                                MessageBubble(
                                    message = message,
                                    categories = categories,
                                    accounts = accounts,
                                    confirmedEntries = confirmedEntries
                                )
                            }
                            if (sending) {
                                item { SendingBubble() }
                            }
                        }
                    }
                }

                ChatInputBar(
                    input = input,
                    sending = sending,
                    onInputChange = { input = it },
                    onSend = { scope.launch { sendMessage() } }
                )
            }
        }
    }
}

@Composable
private fun ChatHeader(
    loading: Boolean,
    canRefresh: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.CenterStart)
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
        }
        Text(
            text = "AI记账助手",
            modifier = Modifier.align(Alignment.Center),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        IconButton(
            onClick = onRefresh,
            enabled = canRefresh && !loading,
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            Icon(Icons.Filled.Refresh, contentDescription = "刷新")
        }
    }
}

@Composable
private fun ChatAuthCard(onRequestAuth: () -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("请先登录后使用 AI 对话", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "登录后可以让 AI 直接生成记账草稿，并在确认后同步到首页、账单和图表。",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = onRequestAuth,
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("登录 / 注册")
            }
        }
    }
}

@Composable
private fun ChatInputBar(
    input: String,
    sending: Boolean,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        OutlinedTextField(
            value = input,
            onValueChange = onInputChange,
            modifier = Modifier.weight(1f),
            enabled = !sending,
            label = { Text("输入一句记账内容") },
            placeholder = { Text("例如：中午吃饭 35 元，支付宝") },
            maxLines = 4,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
        )
        Button(
            onClick = onSend,
            enabled = input.isNotBlank() && !sending,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.height(56.dp)
        ) {
            if (sending) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "发送")
            }
        }
    }
}

@Composable
private fun EmptyChatState(modifier: Modifier = Modifier) {
    Spacer(modifier = modifier.height(1.dp))
}

@Composable
private fun ErrorCard(message: String, onDismiss: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = message,
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    }
}

@Composable
private fun SendingBubble() {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.size(8.dp))
                Text("AI 正在回复", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun MessageBubble(
    message: ChatMessageDto,
    categories: List<CategoryEntity>,
    accounts: List<AccountEntity>,
    confirmedEntries: List<LedgerEntryEntity>
) {
    val isUser = message.role.equals("USER", ignoreCase = true)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            modifier = Modifier.widthIn(max = if (isUser) 320.dp else 360.dp),
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = if (isUser) 18.dp else 6.dp,
                bottomEnd = if (isUser) 6.dp else 18.dp
            ),
            color = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 2.dp
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                if (message.content.isNotBlank()) {
                    Text(
                        text = message.content,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (!isUser) {
                    TransactionDraftCard(
                        messageId = message.id,
                        toolCalls = message.toolCalls,
                        assistantContent = message.content,
                        categories = categories,
                        accounts = accounts,
                        confirmedEntries = confirmedEntries
                    )
                }
            }
        }
    }
}

internal data class ChatTransactionDraft(
    val clientId: String,
    val amountCents: Long,
    val transactionType: TransactionType,
    val categoryCode: String,
    val categoryName: String,
    val accountCode: String,
    val accountName: String,
    val merchant: String,
    val note: String,
    val occurredAt: Long,
    val confidence: Float
)

private fun LedgerEntryEntity.matchesAiDraft(draft: ChatTransactionDraft): Boolean {
    if (reviewState != ReviewState.CONFIRMED || sourceType != SourceType.AI_CHAT) return false
    val expectedFingerprint = "ai_chat:${draft.clientId}"
    return id == draft.clientId ||
        sourceFingerprint == expectedFingerprint ||
        (
            amountCents == draft.amountCents &&
                transactionType == draft.transactionType &&
                kotlin.math.abs(occurredAt - draft.occurredAt) < 60_000L &&
                (categoryCode.orEmpty() == draft.categoryCode || categoryNameSnapshot.orEmpty() == draft.categoryName)
            )
}

@Composable
private fun TransactionDraftCard(
    messageId: Long,
    toolCalls: String?,
    assistantContent: String?,
    categories: List<CategoryEntity>,
    accounts: List<AccountEntity>,
    confirmedEntries: List<LedgerEntryEntity>
) {
    val draft = remember(messageId, toolCalls, assistantContent, categories, accounts) {
        parseTransactionDraft(messageId, toolCalls, assistantContent, categories, accounts)
    } ?: return
    val alreadyConfirmed = remember(draft.clientId, draft.amountCents, draft.occurredAt, confirmedEntries) {
        confirmedEntries.any { it.matchesAiDraft(draft) }
    }

    val scope = rememberCoroutineScope()
    var saving by remember(messageId, toolCalls, assistantContent) { mutableStateOf(false) }
    var dismissed by remember(messageId, toolCalls, assistantContent) { mutableStateOf(false) }
    var confirmed by remember(messageId, toolCalls, assistantContent) { mutableStateOf(false) }
    var statusText by remember(messageId, toolCalls, assistantContent) { mutableStateOf<String?>(null) }
    val isConfirmed = confirmed || alreadyConfirmed

    if (dismissed) {
        Card(
            modifier = Modifier.padding(top = 10.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text(
                text = "已取消这条草稿",
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    Card(
        modifier = Modifier.padding(top = 10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("待确认草稿", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                if (isConfirmed) {
                    Surface(shape = RoundedCornerShape(999.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                        Text(
                            text = "已入账",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
            Text(
                text = draft.categoryName.ifBlank { "未分类" },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = formatMoney(draft.amountCents),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = formatDraftDate(draft.occurredAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            statusText?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (it.contains("失败")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = { dismissed = true },
                    enabled = !saving && !isConfirmed
                ) {
                    Text("取消")
                }
                Button(
                    onClick = {
                        scope.launch {
                            saving = true
                            statusText = null
                            val result = try {
                                val entry = draft.toLedgerEntry()
                                ServiceLocator.repository.confirmAiChatDraft(entry)
                                true
                            } catch (_: Throwable) {
                                false
                            }
                            confirmed = result
                            statusText = if (result) {
                                "已加入正式账本"
                            } else {
                                "入账失败，请重试"
                            }
                            saving = false
                        }
                    },
                    enabled = !saving && !isConfirmed,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        when {
                            saving -> "处理中..."
                            isConfirmed -> "已入账"
                            else -> "确认入账"
                        }
                    )
                }
            }
        }
    }
}

private fun parseTransactionDraft(
    messageId: Long,
    toolCalls: String?,
    assistantContent: String?,
    categories: List<CategoryEntity>,
    accounts: List<AccountEntity>
): ChatTransactionDraft? {
    return parseTransactionDraftFromToolCalls(messageId, toolCalls, categories, accounts)
        ?: parseTransactionDraftFromContent(messageId, assistantContent, categories, accounts)
}

private fun buildImmediateDraftReply(
    content: String,
    nextMessageId: Long,
    categories: List<CategoryEntity>,
    accounts: List<AccountEntity>
): ChatMessageDto? {
    if (!looksLikeBookkeepingInput(content)) return null
    val draft = parseTransactionDraftFromContent(nextMessageId - 1, content, categories, accounts) ?: return null
    val toolCalls = draft.toPendingDraftJson()
    return ChatMessageDto(
        id = nextMessageId - 1,
        role = "ASSISTANT",
        content = "我先为你生成一张待确认草稿，请核对后入账。",
        toolCalls = toolCalls
    )
}

internal fun looksLikeBookkeepingInput(text: String): Boolean {
    val normalized = text.trim()
    if (normalized.isBlank()) return false
    val hasIntent = listOf(
        "记一笔",
        "记账",
        "帮我记",
        "帮我补",
        "补一笔",
        "补记",
        "入账",
        "花了",
        "买了",
        "支付",
        "付款",
        "消费",
        "支出",
        "收入",
        "收了",
        "报销",
        "退款"
    )
        .any { normalized.contains(it) }
    return hasIntent && extractAmountText(normalized) != null
}

private fun ChatTransactionDraft.toPendingDraftJson(): String {
    val payload = JsonObject().apply {
        addProperty("status", "PENDING_CONFIRMATION")
        addProperty("tool", "add_transaction_draft")
        addProperty("clientId", clientId)
        addProperty("amountCents", amountCents)
        addProperty("amountYuan", "%.2f".format(Locale.US, amountCents / 100.0))
        addProperty("transactionType", transactionType.name)
        addProperty("categoryCode", categoryCode)
        addProperty("categoryName", categoryName)
        addProperty("accountCode", accountCode)
        addProperty("accountName", accountName)
        addProperty("merchant", merchant)
        addProperty("note", note)
        addProperty("sourceType", SourceType.AI_CHAT.name)
        addProperty("reviewState", ReviewState.DRAFT.name)
        addProperty("occurredAt", occurredAt)
        addProperty("confidence", confidence)
    }
    return payload.toString()
}

private fun parseTransactionDraftFromToolCalls(
    messageId: Long,
    toolCalls: String?,
    categories: List<CategoryEntity>,
    accounts: List<AccountEntity>
): ChatTransactionDraft? {
    if (toolCalls.isNullOrBlank()) return null
    return runCatching {
        val root = JsonParser.parseString(toolCalls)
        when {
            root.isJsonArray -> root.asJsonArray
                .mapNotNull { element -> element.asJsonObjectOrNull()?.let { parseTransactionDraftToolCall(messageId, it, categories, accounts) } }
                .firstOrNull()

            root.isJsonObject -> parseTransactionDraftObject(messageId, root.asJsonObject, categories, accounts)
            else -> null
        }
    }.getOrNull()
}

private fun parseTransactionDraftToolCall(
    messageId: Long,
    json: JsonObject,
    categories: List<CategoryEntity>,
    accounts: List<AccountEntity>
): ChatTransactionDraft? {
    val function = json.objectValue("function")
    val argumentsElement = when {
        function != null && function.has("arguments") -> function.get("arguments")
        json.has("arguments") -> json.get("arguments")
        else -> null
    }
    val arguments = when {
        argumentsElement == null || argumentsElement.isJsonNull -> json
        argumentsElement.isJsonObject -> argumentsElement.asJsonObject
        argumentsElement.isJsonPrimitive -> runCatching {
            JsonParser.parseString(argumentsElement.asString).asJsonObject
        }.getOrNull()
        else -> null
    } ?: json
    val argumentText = argumentsElement?.takeIf { it.isJsonPrimitive }?.asStringOrNull()
    return parseTransactionDraftPayload(messageId, arguments, categories, accounts)
        ?: arguments.stringValue("content", "text", "description")
            ?.let { parseTransactionDraftFromContent(messageId, it, categories, accounts) }
        ?: argumentText?.let { parseTransactionDraftFromContent(messageId, it, categories, accounts) }
}

private fun parseTransactionDraftObject(
    messageId: Long,
    json: JsonObject,
    categories: List<CategoryEntity>,
    accounts: List<AccountEntity>
): ChatTransactionDraft? {
    return parseTransactionDraftPayload(messageId, json, categories, accounts)
        ?: json.arrayValue("tool_calls", "toolCalls")?.mapNotNull { element ->
            element.asJsonObjectOrNull()?.let { parseTransactionDraftToolCall(messageId, it, categories, accounts) }
        }?.firstOrNull()
        ?: parseTransactionDraftToolCall(messageId, json, categories, accounts)
}

private fun parseTransactionDraftPayload(
    messageId: Long,
    json: JsonObject,
    categories: List<CategoryEntity>,
    accounts: List<AccountEntity>
): ChatTransactionDraft? {
    val payload = json.objectValue("draft", "transaction", "payload", "data", "arguments") ?: json
    val amountCents = payload.longValue("amountCents", "amount_cents")
        ?: payload.stringValue("amountCents", "amount_cents")?.toWholeCentsOrNull()
        ?: payload.stringValue("amount", "amountYuan", "amount_yuan")
            ?.toPositiveCentsOrNull()
        ?: return null
    val transactionType = when {
        payload.stringValue("transactionType", "transaction_type", "type")?.contains("income", ignoreCase = true) == true ||
            payload.stringValue("transactionType", "transaction_type", "type")?.contains("收入") == true -> TransactionType.INCOME
        payload.stringValue("transactionType", "transaction_type", "type")?.contains("transfer", ignoreCase = true) == true ||
            payload.stringValue("transactionType", "transaction_type", "type")?.contains("转账") == true -> TransactionType.TRANSFER
        else -> TransactionType.EXPENSE
    }
    val categoryInput = payload.stringValue("categoryCode", "category_code", "categoryName", "category_name", "category").orEmpty()
    val category = resolveCategory(categoryInput, categories, transactionType)
    val accountInput = payload.stringValue("accountCode", "account_code", "accountName", "account_name", "account").orEmpty()
    val account = resolveAccount(accountInput, accounts)
    val clientId = payload.stringValue("clientId", "client_id").orEmpty().ifBlank {
        stableDraftId("tool:$messageId:${payload.toString()}")
    }
    val confidence = payload.floatValue("confidence", "score", "confidenceScore") ?: 0f
    val merchant = payload.stringValue("merchant", "merchantName", "merchant_name").orEmpty()
    val note = payload.stringValue("note", "content", "description").orEmpty()
    val occurredAt = parsePayloadOccurredAt(payload)
        ?: parseDraftDateText(note)
        ?: System.currentTimeMillis()
    return ChatTransactionDraft(
        clientId = clientId,
        amountCents = amountCents,
        transactionType = transactionType,
        categoryCode = category.first,
        categoryName = category.second,
        accountCode = account.first,
        accountName = account.second,
        merchant = merchant,
        note = note,
        occurredAt = occurredAt,
        confidence = confidence
    )
}

internal fun parseTransactionDraftFromContent(
    messageId: Long,
    content: String?,
    categories: List<CategoryEntity>,
    accounts: List<AccountEntity>
): ChatTransactionDraft? {
    val text = content?.trim().orEmpty()
    if (text.isBlank()) return null
    parseJsonDraftFromText(messageId, text, categories, accounts)?.let { return it }

    val amountText = extractLabeledValue(text, listOf("金额", "金额(元)", "金额（元）", "消费金额"))
        ?: extractAmountText(text)
    val amountCents = amountText?.toPositiveCentsOrNull() ?: return null

    val transactionTypeText = extractLabeledValue(text, listOf("类型", "收支", "交易类型")).orEmpty()
    val transactionType = when {
        transactionTypeText.contains("收入") || transactionTypeText.contains("income", ignoreCase = true) -> TransactionType.INCOME
        transactionTypeText.contains("转账") || transactionTypeText.contains("transfer", ignoreCase = true) -> TransactionType.TRANSFER
        else -> TransactionType.EXPENSE
    }

    val categoryText = extractStructuredValue(text, listOf("分类", "类别", "品类"))
        ?: inferCategoryInput(text)
    val category = resolveCategory(categoryText, categories, transactionType)
    val accountText = extractStructuredValue(text, listOf("账户", "账号", "账本"))
        ?: inferAccountInput(text)
    val account = resolveAccount(accountText, accounts)
    val occurredAt = extractStructuredValue(text, listOf("时间", "日期"))
        ?.let { parseDraftDateText(it) }
        ?: parseDraftDateText(extractLikelyDateText(text) ?: text)
        ?: System.currentTimeMillis()

    return ChatTransactionDraft(
        clientId = stableDraftId("content:$messageId:$text"),
        amountCents = amountCents,
        transactionType = transactionType,
        categoryCode = category.first,
        categoryName = category.second,
        accountCode = account.first,
        accountName = account.second,
        merchant = inferMerchant(text),
        note = text,
        occurredAt = occurredAt,
        confidence = 0.6f
    )
}

private fun extractLikelyDateText(text: String): String? {
    val fullDate = Regex("""\d{4}[年\-/\.]\d{1,2}[月\-/\.]\d{1,2}日?号?(?:\s+\d{1,2}:\d{2}(?::\d{2})?)?""")
        .find(text)
        ?.value
    if (!fullDate.isNullOrBlank()) return fullDate
    return Regex("""(?<!\d)\d{1,2}[月\-/\.]\d{1,2}日?号?(?:\s+\d{1,2}:\d{2}(?::\d{2})?)?""")
        .find(text)
        ?.value
        ?: Regex("""(?<!\d)\d{1,2}[日号](?:\s*(?:早上|上午|中午|下午|晚上|今晚|凌晨|傍晚)?\s*\d{1,2}:\d{2}(?::\d{2})?)?""")
            .find(text)
            ?.value
        ?: Regex("""(?:上周|这周|本周|下周)?[一二三四五六日天](?:\s*(?:早上|上午|中午|下午|晚上|今晚|凌晨|傍晚)?\s*\d{1,2}:\d{2}(?::\d{2})?)?""")
            .find(text)
            ?.value
}

internal fun extractAmountText(text: String): String? {
    Regex("""(?:[¥￥]\s*)?([0-9]+(?:\.[0-9]{1,2})?)\s*(?:元|块|块钱|rmb|RMB)""")
        .find(text)
        ?.groupValues
        ?.getOrNull(1)
        ?.let { return it }
    Regex("""[¥￥]\s*([0-9]+(?:\.[0-9]{1,2})?)""")
        .find(text)
        ?.groupValues
        ?.getOrNull(1)
        ?.let { return it }

    val dateRanges = buildList {
        Regex("""\d{4}[年\-/\.]\d{1,2}[月\-/\.]\d{1,2}日?号?""")
            .findAll(text)
            .forEach { add(it.range) }
        Regex("""(?<!\d)\d{1,2}[月\-/\.]\d{1,2}日?号?""")
            .findAll(text)
            .forEach { add(it.range) }
    }
    return Regex("""(?<![A-Za-z0-9.])(\d+(?:\.\d{1,2})?)(?![A-Za-z0-9.])""")
        .findAll(text)
        .mapNotNull { match ->
            val value = match.groupValues.getOrNull(1).orEmpty()
            val number = value.toBigDecimalOrNull() ?: return@mapNotNull null
            val overlapsDate = dateRanges.any { range ->
                match.range.first <= range.last && match.range.last >= range.first
            }
            when {
                overlapsDate -> null
                number <= java.math.BigDecimal.ZERO -> null
                else -> value
            }
        }
        .lastOrNull()
}

private fun parseJsonDraftFromText(
    messageId: Long,
    text: String,
    categories: List<CategoryEntity>,
    accounts: List<AccountEntity>
): ChatTransactionDraft? {
    val candidate = when {
        text.startsWith("{") && text.endsWith("}") -> text
        text.contains("PENDING_CONFIRMATION") -> {
            val start = text.indexOf('{')
            val end = text.lastIndexOf('}')
            if (start >= 0 && end > start) text.substring(start, end + 1) else null
        }
        else -> null
    } ?: return null
    return runCatching {
        JsonParser.parseString(candidate).asJsonObject
    }.getOrNull()?.let { parseTransactionDraftObject(messageId, it, categories, accounts) }
}

private fun resolveCategory(
    input: String,
    categories: List<CategoryEntity>,
    transactionType: TransactionType
): Pair<String, String> {
    val normalized = input.trim()
    val matched = (if (normalized.isBlank()) null else categories.firstOrNull { category ->
        val candidate = normalized.lowercase()
        category.isIncome == (transactionType == TransactionType.INCOME) && (
            category.code.equals(candidate, ignoreCase = true) ||
            category.name == normalized ||
            category.name.contains(normalized) ||
            normalized.contains(category.name)
            )
    })
    return when {
        matched != null -> matched.code to matched.name
        transactionType == TransactionType.INCOME -> "other_income" to "其他收入"
        else -> "other" to "其他"
    }
}

private fun resolveAccount(input: String, accounts: List<AccountEntity>): Pair<String, String> {
    val normalized = input.trim()
    val matched = (if (normalized.isBlank()) null else accounts.firstOrNull { account ->
        val candidate = normalized.lowercase()
        account.code.equals(candidate, ignoreCase = true) ||
            account.name == normalized ||
            account.name.contains(normalized) ||
            normalized.contains(account.name)
    }) ?: when {
        normalized.contains("微信") -> accounts.firstOrNull { it.code == "wechat" }
        normalized.contains("支付宝") -> accounts.firstOrNull { it.code == "alipay" }
        normalized.contains("银行卡") || normalized.contains("银行") -> accounts.firstOrNull { it.code == "bank" }
        normalized.contains("现金") -> accounts.firstOrNull { it.code == "cash" }
        else -> null
    }
    return when {
        matched != null -> matched.code to matched.name
        else -> "cash" to "现金"
    }
}

internal fun inferCategoryInput(text: String): String {
    return when {
        text.contains("话费") || text.contains("手机费") || text.contains("流量费") || text.contains("流量包") ||
            text.contains("通信费") || text.contains("通讯费") || text.contains("宽带") || text.contains("网费") -> "通讯"
        text.contains("水费") || text.contains("电费") || text.contains("燃气") || text.contains("物业费") ||
            text.contains("生活缴费") -> "生活缴费"
        text.contains("房租") || text.contains("租金") || text.contains("房贷") -> "居住"
        text.contains("机票") || text.contains("火车票") || text.contains("高铁") || text.contains("酒店") -> "旅行"
        text.contains("咖啡") || text.contains("奶茶") || text.contains("茶饮") || text.contains("瑞幸") || text.contains("星巴克") -> "咖啡茶饮"
        text.contains("吃饭") || text.contains("午饭") || text.contains("晚饭") || text.contains("早餐") || text.contains("餐") -> "餐饮"
        text.contains("外卖") || text.contains("美团") || text.contains("饿了么") -> "外卖"
        text.contains("打车") || text.contains("滴滴") || text.contains("出租车") -> "打车"
        text.contains("地铁") || text.contains("公交") || text.contains("交通") -> "交通"
        text.contains("购物") || text.contains("淘宝") || text.contains("京东") || text.contains("拼多多") -> "购物"
        text.contains("工资") || text.contains("薪资") -> "工资"
        text.contains("退款") || text.contains("返现") || text.contains("报销") -> "退款返现"
        else -> ""
    }
}

private fun inferAccountInput(text: String): String {
    return when {
        text.contains("微信") -> "微信"
        text.contains("支付宝") -> "支付宝"
        text.contains("银行卡") || text.contains("银行") -> "银行卡"
        text.contains("现金") -> "现金"
        else -> ""
    }
}

private fun inferMerchant(text: String): String {
    return listOf("美团", "饿了么", "滴滴", "淘宝", "京东", "拼多多", "星巴克", "瑞幸")
        .firstOrNull { text.contains(it) }
        .orEmpty()
}

private fun extractStructuredValue(content: String, labels: List<String>): String? {
    return extractTableValue(content, labels) ?: extractLabeledValue(content, labels)
}

private fun extractTableValue(content: String, labels: List<String>): String? {
    val labelSet = labels.toSet()
    return content.lineSequence()
        .mapNotNull { line ->
            val trimmed = line.trim()
            if (!trimmed.startsWith("|") || !trimmed.endsWith("|")) return@mapNotNull null
            val cells = trimmed
                .trim('|')
                .split('|')
                .map { it.cleanMarkdownCell() }
                .filter { it.isNotBlank() }
            if (cells.size < 2) return@mapNotNull null
            val label = cells.first().removeSuffix("：").removeSuffix(":").trim()
            if (label !in labelSet) return@mapNotNull null
            cells.drop(1).firstOrNull { it.isNotBlank() }
        }
        .firstOrNull()
}

private fun extractLabeledValue(content: String, labels: List<String>): String? {
    val escapedLabels = labels.joinToString("|") { Regex.escape(it) }
    val regex = Regex("""(?:^|\n)\s*[-*•]?\s*(?:\*\*)?(?:$escapedLabels)(?:\*\*)?\s*[:：]\s*([^\n\r]+)""")
    return regex.find(content)?.groupValues?.getOrNull(1)?.cleanMarkdownCell()?.takeIf { it.isNotBlank() }
}

private fun String.cleanMarkdownCell(): String {
    return trim()
        .replace("**", "")
        .replace("__", "")
        .replace("`", "")
        .replace(Regex("""^[\p{So}\p{Sk}\s]+"""), "")
        .replace(Regex("""[\p{So}\p{Sk}\s]+$"""), "")
        .trim()
}

private fun JsonObject.stringValue(vararg keys: String): String? {
    for (key in keys) {
        val element = get(key) ?: continue
        if (element.isJsonNull || !element.isJsonPrimitive) continue
        val value = runCatching { element.asString }.getOrNull()?.trim()
        if (!value.isNullOrBlank()) return value
    }
    return null
}

private fun JsonObject.objectValue(vararg keys: String): JsonObject? {
    for (key in keys) {
        val element = get(key) ?: continue
        val value = when {
            element.isJsonObject -> element.asJsonObject
            element.isJsonPrimitive -> runCatching {
                JsonParser.parseString(element.asString).asJsonObject
            }.getOrNull()
            else -> null
        }
        if (value != null) return value
    }
    return null
}

private fun JsonObject.arrayValue(vararg keys: String): List<JsonElement>? {
    for (key in keys) {
        val element = get(key) ?: continue
        val array = when {
            element.isJsonArray -> element.asJsonArray
            element.isJsonPrimitive -> runCatching {
                JsonParser.parseString(element.asString).asJsonArray
            }.getOrNull()
            else -> null
        }
        if (array != null) return array.toList()
    }
    return null
}

private fun JsonElement.asJsonObjectOrNull(): JsonObject? {
    return runCatching {
        when {
            isJsonObject -> asJsonObject
            isJsonPrimitive -> JsonParser.parseString(asString).asJsonObject
            else -> null
        }
    }.getOrNull()
}

private fun JsonElement.asStringOrNull(): String? {
    return runCatching { asString }.getOrNull()?.takeIf { it.isNotBlank() }
}

private fun JsonObject.longValue(vararg keys: String): Long? {
    for (key in keys) {
        val element = get(key) ?: continue
        if (element.isJsonNull || !element.isJsonPrimitive) continue
        val primitive = element.asJsonPrimitive
        val value = when {
            primitive.isNumber -> runCatching { primitive.asLong }.getOrNull()
            primitive.isString -> primitive.asString.toLongOrNull()
            else -> null
        }
        if (value != null) return value
    }
    return null
}

private fun JsonObject.floatValue(vararg keys: String): Float? {
    for (key in keys) {
        val element = get(key) ?: continue
        if (element.isJsonNull || !element.isJsonPrimitive) continue
        val primitive = element.asJsonPrimitive
        val value = when {
            primitive.isNumber -> runCatching { primitive.asFloat }.getOrNull()
            primitive.isString -> primitive.asString.toFloatOrNull()
            else -> null
        }
        if (value != null) return value
    }
    return null
}

private fun Long.normalizeEpochMillis(): Long {
    return if (this in 1L..9_999_999_999L) this * 1000L else this
}

private fun parsePayloadOccurredAt(payload: JsonObject): Long? {
    return payload.longValue(
        "occurredAt",
        "occurred_at",
        "occurredAtMillis",
        "occurred_at_millis",
        "timestamp",
        "transactionTimestamp",
        "transaction_timestamp"
    )?.normalizeEpochMillis()
        ?: payload.stringValue(
            "date",
            "occurredDate",
            "occurred_date",
            "occurredAt",
            "occurred_at",
            "occurredAtText",
            "occurred_at_text",
            "occurredTime",
            "occurred_time",
            "transactionTime",
            "transaction_time",
            "time",
            "datetime",
            "dateTime",
            "date_time"
        )?.let { parseDraftDateText(it) }
}

private fun String.toPositiveCentsOrNull(): Long? {
    return trim()
        .replace("元", "")
        .replace("￥", "")
        .replace("¥", "")
        .replace("RMB", "", ignoreCase = true)
        .replace("rmb", "", ignoreCase = true)
        .replace(",", "")
        .toBigDecimalOrNull()
        ?.takeIf { it > java.math.BigDecimal.ZERO }
        ?.movePointRight(2)
        ?.setScale(0, RoundingMode.HALF_UP)
        ?.toLong()
}

private fun String.toWholeCentsOrNull(): Long? {
    return trim()
        .replace(",", "")
        .toLongOrNull()
        ?.takeIf { it > 0L }
}

internal fun parseDraftDateText(text: String): Long? {
    val trimmed = text.trim()
    if (trimmed.isBlank()) return null
    trimmed.toLongOrNull()?.let { return it.normalizeEpochMillis() }

    runCatching { return Instant.parse(trimmed).toEpochMilli() }
    runCatching { return OffsetDateTime.parse(trimmed).toInstant().toEpochMilli() }

    val zone = ZoneId.systemDefault()
    val normalized = trimmed
        .replace('年', '-')
        .replace('月', '-')
        .replace("日", " ")
        .replace("号", " ")
        .replace('/', '-')
        .replace('.', '-')
        .trim()
    val localDateTimePatterns = listOf(
        "yyyy-M-d H:mm:ss",
        "yyyy-M-d H:mm",
        "yyyy-M-d'T'H:mm:ss",
        "yyyy-M-d'T'H:mm"
    )
    for (pattern in localDateTimePatterns) {
        runCatching {
            return LocalDateTime.parse(normalized, DateTimeFormatter.ofPattern(pattern))
                .atZone(zone)
                .toInstant()
                .toEpochMilli()
        }
    }
    runCatching {
        return LocalDate.parse(normalized.substringBefore(' '), DateTimeFormatter.ofPattern("yyyy-M-d"))
            .atStartOfDay(zone)
            .toInstant()
            .toEpochMilli()
    }
    val ymd = Regex("""(\d{4})[-/.年](\d{1,2})[-/.月](\d{1,2})(?:[日号]?\s*(\d{1,2}):(\d{2})(?::(\d{2}))?)?""")
        .find(trimmed)
    if (ymd != null) {
        val year = ymd.groupValues[1].toIntOrNull() ?: return null
        val month = ymd.groupValues[2].toIntOrNull() ?: return null
        val day = ymd.groupValues[3].toIntOrNull() ?: return null
        val hour = ymd.groupValues.getOrNull(4)?.toIntOrNull() ?: 0
        val minute = ymd.groupValues.getOrNull(5)?.toIntOrNull() ?: 0
        val second = ymd.groupValues.getOrNull(6)?.toIntOrNull() ?: 0
        return runCatching {
            java.time.LocalDateTime.of(year, month, day, hour, minute, second)
                .atZone(java.time.ZoneId.systemDefault())
                .toInstant()
            .toEpochMilli()
        }.getOrNull()
    }
    val monthDay = Regex("""(?<!\d)(\d{1,2})[-/.月](\d{1,2})(?:[日号]?\s*(\d{1,2}):(\d{2})(?::(\d{2}))?)?""")
        .find(trimmed)
    if (monthDay != null) {
        val currentDate = LocalDate.now(zone)
        val month = monthDay.groupValues[1].toIntOrNull() ?: return null
        val day = monthDay.groupValues[2].toIntOrNull() ?: return null
        val hour = monthDay.groupValues.getOrNull(3)?.toIntOrNull() ?: 0
        val minute = monthDay.groupValues.getOrNull(4)?.toIntOrNull() ?: 0
        val second = monthDay.groupValues.getOrNull(5)?.toIntOrNull() ?: 0
        return runCatching {
            LocalDateTime.of(currentDate.year, month, day, hour, minute, second)
                .atZone(zone)
                .toInstant()
                .toEpochMilli()
        }.getOrNull()
    }
    val dayOnly = Regex("""(?<!\d)(\d{1,2})[日号](?:\s*(早上|上午|中午|下午|晚上|今晚|凌晨|傍晚)?\s*(\d{1,2}):(\d{2})(?::(\d{2}))?)?""")
        .find(trimmed)
    if (dayOnly != null) {
        val currentDate = LocalDate.now(zone)
        val day = dayOnly.groupValues[1].toIntOrNull() ?: return null
        val time = resolveChineseTimeOfDay(
            period = dayOnly.groupValues.getOrNull(2).orEmpty(),
            hourText = dayOnly.groupValues.getOrNull(3).orEmpty(),
            minuteText = dayOnly.groupValues.getOrNull(4).orEmpty(),
            secondText = dayOnly.groupValues.getOrNull(5).orEmpty()
        )
        return runCatching {
            LocalDateTime.of(currentDate.year, currentDate.monthValue, day, time.hour, time.minute, time.second)
                .atZone(zone)
                .toInstant()
                .toEpochMilli()
        }.getOrNull()
    }
    val weekdayDate = parseChineseWeekdayDate(trimmed, zone)
    if (weekdayDate != null) {
        val time = parseChineseTime(trimmed)
        return LocalDateTime.of(weekdayDate, time).atZone(zone).toInstant().toEpochMilli()
    }
    val modernRelativeDate = when {
        trimmed.contains("前天") -> LocalDate.now(zone).minusDays(2)
        trimmed.contains("昨天") || trimmed.contains("昨晚") || trimmed.contains("昨日") -> LocalDate.now(zone).minusDays(1)
        trimmed.contains("今天") || trimmed.contains("今日") || trimmed.contains("今晚") -> LocalDate.now(zone)
        else -> null
    }
    if (modernRelativeDate != null) {
        val timeMatch = Regex("""(\d{1,2}):(\d{2})(?::(\d{2}))?""").find(trimmed)
        val time = if (timeMatch != null) {
            resolveChineseTimeOfDay(
                period = Regex("""早上|上午|中午|下午|晚上|今晚|凌晨|傍晚""").find(trimmed)?.value.orEmpty(),
                hourText = timeMatch.groupValues[1],
                minuteText = timeMatch.groupValues[2],
                secondText = timeMatch.groupValues.getOrNull(3).orEmpty()
            )
        } else {
            defaultChineseTimeOfDay(trimmed)
        }
        return LocalDateTime.of(modernRelativeDate, time).atZone(zone).toInstant().toEpochMilli()
    }
    val relativeDate = when {
        trimmed.contains("前天") -> LocalDate.now(zone).minusDays(2)
        trimmed.contains("昨天") || trimmed.contains("昨晚") || trimmed.contains("昨日") -> LocalDate.now(zone).minusDays(1)
        trimmed.contains("今天") || trimmed.contains("今日") || trimmed.contains("今晚") -> LocalDate.now(zone)
        else -> null
    }
    if (relativeDate != null) {
        val timeMatch = Regex("""(\d{1,2}):(\d{2})(?::(\d{2}))?""").find(trimmed)
        val time = if (timeMatch != null) {
            resolveChineseTimeOfDay(
                period = Regex("""早上|上午|中午|下午|晚上|今晚|凌晨|傍晚""").find(trimmed)?.value.orEmpty(),
                hourText = timeMatch.groupValues[1],
                minuteText = timeMatch.groupValues[2],
                secondText = timeMatch.groupValues.getOrNull(3).orEmpty()
            )
        } else {
            defaultChineseTimeOfDay(trimmed)
        }
        return LocalDateTime.of(relativeDate, time).atZone(zone).toInstant().toEpochMilli()
    }
    return null
}

private fun parseChineseWeekdayDate(text: String, zone: ZoneId): LocalDate? {
    val match = Regex("""(上周|这周|本周|下周)?(?:周|星期|礼拜)?([一二三四五六日天])""").find(text) ?: return null
    val weekPrefix = match.groupValues.getOrNull(1).orEmpty()
    val targetDay = when (match.groupValues.getOrNull(2)) {
        "一" -> 1
        "二" -> 2
        "三" -> 3
        "四" -> 4
        "五" -> 5
        "六" -> 6
        "日", "天" -> 7
        else -> return null
    }
    val today = LocalDate.now(zone)
    val monday = today.minusDays((today.dayOfWeek.value - 1).toLong())
    val offsetWeeks = when (weekPrefix) {
        "上周" -> -1L
        "下周" -> 1L
        else -> 0L
    }
    return monday.plusWeeks(offsetWeeks).plusDays((targetDay - 1).toLong())
}

private fun parseChineseTime(text: String): LocalTime {
    val period = Regex("""早上|上午|中午|下午|晚上|今晚|凌晨|傍晚""").find(text)?.value.orEmpty()
    val match = Regex("""(\d{1,2}):(\d{2})(?::(\d{2}))?""").find(text)
    return if (match == null) {
        defaultChineseTimeOfDay(text)
    } else {
        resolveChineseTimeOfDay(
            period = period,
            hourText = match.groupValues[1],
            minuteText = match.groupValues[2],
            secondText = match.groupValues.getOrNull(3).orEmpty()
        )
    }
}

private fun defaultChineseTimeOfDay(text: String): LocalTime {
    return when {
        text.contains("凌晨") -> LocalTime.of(1, 0)
        text.contains("早上") || text.contains("上午") -> LocalTime.of(8, 0)
        text.contains("中午") -> LocalTime.of(12, 0)
        text.contains("下午") -> LocalTime.of(15, 0)
        text.contains("晚上") || text.contains("今晚") || text.contains("傍晚") -> LocalTime.of(20, 0)
        else -> LocalTime.MIDNIGHT
    }
}

private fun resolveChineseTimeOfDay(
    period: String,
    hourText: String,
    minuteText: String,
    secondText: String
): LocalTime {
    var hour = hourText.toIntOrNull()?.coerceIn(0, 23) ?: 0
    val minute = minuteText.toIntOrNull()?.coerceIn(0, 59) ?: 0
    val second = secondText.toIntOrNull()?.coerceIn(0, 59) ?: 0
    if ((period == "下午" || period == "晚上" || period == "今晚" || period == "傍晚") && hour in 1..11) {
        hour += 12
    }
    if (period == "中午" && hour in 1..10) {
        hour += 12
    }
    if (period == "凌晨" && hour == 12) {
        hour = 0
    }
    return LocalTime.of(hour, minute, second)
}

private fun stableDraftId(seed: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
        .digest(seed.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        .take(16)
    return "ai-$digest"
}

private fun formatDraftDate(timeMillis: Long): String {
    return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date(timeMillis))
}

private fun formatMoney(cents: Long): String {
    return NumberFormat.getCurrencyInstance(Locale.CHINA).format(cents / 100.0)
}

private fun buildLocalLedgerQuestionReply(content: String, entries: List<LedgerEntryEntity>): String? {
    if (!looksLikeLocalLedgerQuestion(content)) return null
    val range = inferLocalLedgerRange(content)
    val zone = ZoneId.systemDefault()
    val startMillis = range.first.atStartOfDay(zone).toInstant().toEpochMilli()
    val endExclusiveMillis = range.second.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val confirmed = entries.filter {
        it.reviewState == ReviewState.CONFIRMED &&
            it.occurredAt >= startMillis &&
            it.occurredAt < endExclusiveMillis
    }
    val expenseCents = confirmed
        .filter { it.transactionType == TransactionType.EXPENSE }
        .sumOf { it.amountCents }
    val incomeCents = confirmed
        .filter { it.transactionType == TransactionType.INCOME }
        .sumOf { it.amountCents }
    val count = confirmed.count { it.transactionType == TransactionType.EXPENSE || it.transactionType == TransactionType.INCOME }
    val rangeText = formatLocalLedgerRange(range.first, range.second)
    val expenseText = formatPlainMoney(expenseCents)
    val incomeText = formatPlainMoney(incomeCents)
    return if (count == 0) {
        "${rangeText}已确认入账的消费为 ${expenseText} 元，目前本地账本里没有符合条件的已确认记录。"
    } else {
        "${rangeText}已确认支出 ${expenseText} 元，收入 ${incomeText} 元，共 ${count} 笔。统计口径与首页、账单和图表一致，只包含已确认入账记录。"
    }
}

private fun looksLikeLocalLedgerQuestion(text: String): Boolean {
    val normalized = text.trim().lowercase(Locale.ROOT)
    if (normalized.isBlank()) return false
    val hasLedgerWord = listOf("消费", "花了", "支出", "收入", "账单", "花多少", "用了多少")
        .any { normalized.contains(it) }
    val hasQuestionWord = listOf("多少", "合计", "总", "这个月", "本月", "今天", "今日", "昨天", "昨日")
        .any { normalized.contains(it) }
    return hasLedgerWord && hasQuestionWord
}

private fun inferLocalLedgerRange(text: String): Pair<LocalDate, LocalDate> {
    val today = LocalDate.now(ZoneId.systemDefault())
    return when {
        text.contains("昨天") || text.contains("昨日") -> today.minusDays(1) to today.minusDays(1)
        text.contains("今天") || text.contains("今日") -> today to today
        text.contains("上个月") || text.contains("上月") -> {
            val lastMonth = today.minusMonths(1)
            lastMonth.withDayOfMonth(1) to lastMonth.withDayOfMonth(lastMonth.lengthOfMonth())
        }
        else -> today.withDayOfMonth(1) to today
    }
}

private fun formatLocalLedgerRange(start: LocalDate, end: LocalDate): String {
    return if (start == end) {
        "${start.format(DateTimeFormatter.ISO_LOCAL_DATE)} "
    } else {
        "${start.format(DateTimeFormatter.ISO_LOCAL_DATE)} 至 ${end.format(DateTimeFormatter.ISO_LOCAL_DATE)} "
    }
}

private fun formatPlainMoney(cents: Long): String {
    return "%.2f".format(Locale.US, cents / 100.0)
}

private fun buildLocalLedgerContext(entries: List<LedgerEntryEntity>): String {
    val confirmed = entries.filter { it.reviewState == ReviewState.CONFIRMED }
    val currentMonth = SimpleDateFormat("yyyy-MM", Locale.CHINA).format(Date())
    val recentIds = confirmed
        .sortedByDescending { it.occurredAt }
        .take(300)
        .map { it.id }
        .toSet()
    val sorted = confirmed
        .filter { entry ->
            entry.id in recentIds ||
                SimpleDateFormat("yyyy-MM", Locale.CHINA).format(Date(entry.occurredAt)) == currentMonth
        }
        .sortedByDescending { it.occurredAt }
    val root = JsonObject().apply {
        addProperty("source", "android_local_room")
        addProperty("generatedAt", System.currentTimeMillis())
        addProperty("officialReviewState", ReviewState.CONFIRMED.name)
        addProperty("currentMonth", currentMonth)
        addProperty("count", sorted.size)
    }
    val array = JsonArray()
    sorted.forEach { entry ->
        array.add(JsonObject().apply {
            addProperty("id", entry.id)
            addProperty("reviewState", entry.reviewState.name)
            addProperty("transactionType", entry.transactionType.name)
            addProperty("amountCents", entry.amountCents)
            addProperty("amountYuan", "%.2f".format(Locale.US, entry.amountCents / 100.0))
            addProperty("merchant", entry.merchant)
            addProperty("categoryCode", entry.categoryCode.orEmpty())
            addProperty("categoryName", entry.categoryNameSnapshot.orEmpty())
            addProperty("accountCode", entry.accountCode)
            addProperty("accountName", entry.accountNameSnapshot)
            addProperty("note", entry.note)
            addProperty("occurredAt", entry.occurredAt)
            addProperty("date", formatDraftDate(entry.occurredAt).substringBefore(' '))
            addProperty("sourceType", entry.sourceType.name)
        })
    }
    root.add("entries", array)
    return root.toString()
}

private fun ChatTransactionDraft.toLedgerEntry(): LedgerEntryEntity {
    val now = System.currentTimeMillis()
    return LedgerEntryEntity(
        id = clientId.ifBlank { "ai-${UUID.randomUUID()}" },
        reviewState = ReviewState.DRAFT,
        ingestionState = IngestionState.RAW,
        transactionType = transactionType,
        sourceType = SourceType.AI_CHAT,
        sourceFingerprint = "ai_chat:$clientId",
        sourcePackage = "ai-chat",
        sourceAppName = "AI 对话",
        occurredAt = occurredAt.takeIf { it > 0L } ?: now,
        amountCents = amountCents,
        merchant = merchant,
        categoryCode = categoryCode.ifBlank { null },
        categoryNameSnapshot = categoryName,
        accountCode = accountCode.ifBlank { "cash" },
        accountNameSnapshot = accountName.ifBlank { "现金" },
        note = note,
        rawText = "",
        confidence = confidence,
        needsReview = true,
        createdAt = now,
        updatedAt = now
    )
}
