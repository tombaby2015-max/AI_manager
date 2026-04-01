package com.aimanager

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.collectAsState
import coil.compose.AsyncImage
import com.aimanager.data.Account
import com.aimanager.data.AiService
import com.aimanager.data.Chat
import com.aimanager.data.MainViewModel
import com.aimanager.ui.theme.AIManagerTheme

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startService(Intent(this, OverlayService::class.java))
        setContent {
            AIManagerTheme {
                MainScreen(
                    viewModel = viewModel,
                    onMinimize = { moveTaskToBack(true) }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Окно открыто — скрываем пузырь
        startService(Intent(this, OverlayService::class.java).apply {
            action = OverlayService.ACTION_HIDE
        })
    }

    override fun onPause() {
        super.onPause()
        // Окно ушло в фон — показываем пузырь
        startService(Intent(this, OverlayService::class.java).apply {
            action = OverlayService.ACTION_SHOW
        })
    }
}

// ─── Цвета ───────────────────────────────────────────────────────────────────
val BgPrimary   = Color(0xFF1A1A1E)
val BgSecondary = Color(0xFF111114)
val BgAcc       = Color(0xFF17171B)
val BgChat      = Color(0xFF141418)
val TextPrimary = Color(0xFFE0E0E0)
val TextMuted   = Color(0xFFC0C0C0)
val TextHint    = Color(0xFF888888)
val Divider     = Color(0xFF222226)
val TimerRed    = Color(0xFFE05050)
val TimerYellow = Color(0xFFE8C44A)

// ─── Утилиты ──────────────────────────────────────────────────────────────────
fun faviconUrl(baseUrl: String): String {
    val domain = Uri.parse(baseUrl).host ?: return ""
    return "https://www.google.com/s2/favicons?domain=$domain&sz=64"
}

fun formatTimer(endTimestamp: Long): Pair<String, Color> {
    val remaining = endTimestamp - System.currentTimeMillis()
    if (remaining <= 0) return Pair("", TextHint)
    val totalMin = remaining / 60000
    val hours = totalMin / 60
    val mins = totalMin % 60
    val color = if (totalMin < 30) TimerYellow else TimerRed
    val text = when {
        hours > 0 -> "${hours}ч ${mins}м"
        else -> "${mins}м"
    }
    return Pair(text, color)
}

fun openInBrowser(context: Context, url: String, browserPackage: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
        if (browserPackage.isNotEmpty()) setPackage(browserPackage)
    }
    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        // fallback без пакета
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}

// ─── Главный экран ────────────────────────────────────────────────────────────
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MainScreen(viewModel: MainViewModel, onMinimize: () -> Unit) {
    val services by viewModel.services.collectAsState()
    var showAddAiDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgPrimary)
    ) {
        TopBar(
            onCollapseAll = { viewModel.collapseAll() },
            onAddAi = { showAddAiDialog = true },
            onMinimize = onMinimize
        )
        ServiceList(
            services = services,
            viewModel = viewModel
        )
    }

    if (showAddAiDialog) {
        AddAiDialog(
            onDismiss = { showAddAiDialog = false },
            onConfirm = { name, url, iconUrl ->
                viewModel.addAiService(name, url, iconUrl)
                showAddAiDialog = false
            }
        )
    }
}

// ─── Шапка ────────────────────────────────────────────────────────────────────
@Composable
fun TopBar(onCollapseAll: () -> Unit, onAddAi: () -> Unit, onMinimize: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(BgSecondary)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onMinimize, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Свернуть", tint = Color(0xFF6699CC), modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(4.dp))
        Text(
            text = "AI Manager",
            color = TextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
        TextButton(
            onClick = onCollapseAll,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
        ) {
            Text("свернуть всё", color = TextHint, fontSize = 12.sp)
        }
        Spacer(Modifier.width(4.dp))
        IconButton(onClick = onAddAi, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Add, contentDescription = "Добавить AI", tint = TextHint)
        }
    }
    HorizontalDivider(color = Divider, thickness = 0.5.dp)
}

// ─── Список сервисов ──────────────────────────────────────────────────────────
@Composable
fun ServiceList(services: List<AiService>, viewModel: MainViewModel) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        services.forEach { ai ->
            item(key = ai.id) {
                AiRow(ai = ai, viewModel = viewModel)
                HorizontalDivider(color = Divider, thickness = 0.5.dp)
            }
            if (ai.isExpanded) {
                ai.accounts.forEach { acc ->
                    item(key = "${ai.id}_${acc.id}") {
                        AccountRow(ai = ai, account = acc, viewModel = viewModel)
                    }
                    if (acc.isExpanded) {
                        items(acc.chats, key = { "${ai.id}_${acc.id}_${it.id}" }) { chat ->
                            ChatRow(ai = ai, account = acc, chat = chat, viewModel = viewModel)
                        }
                    }
                }
            }
        }
    }
}

// ─── Строка AI ────────────────────────────────────────────────────────────────
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AiRow(ai: AiService, viewModel: MainViewModel) {
    var showMenu by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showAddAccountDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(BgPrimary)
            .combinedClickable(
                onClick = { viewModel.toggleAi(ai.id) },
                onLongClick = { showMenu = true }
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (ai.isExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight,
            contentDescription = null,
            tint = TextHint,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(6.dp))
        FaviconImage(url = if (ai.iconUrl.isNotEmpty()) ai.iconUrl else faviconUrl(ai.baseUrl))
        Spacer(Modifier.width(8.dp))
        Text(
            text = ai.name,
            color = TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
    }

    if (showMenu) {
        AiContextMenu(
            onDismiss = { showMenu = false },
            onEdit = { showMenu = false; showEditDialog = true },
            onAddAccount = { showMenu = false; showAddAccountDialog = true },
            onDelete = { showMenu = false; showDeleteConfirm = true }
        )
    }
    if (showEditDialog) {
        EditAiDialog(
            ai = ai,
            onDismiss = { showEditDialog = false },
            onConfirm = { name, url, iconUrl ->
                viewModel.editAiService(ai.id, name, url, iconUrl)
                showEditDialog = false
            }
        )
    }
    if (showAddAccountDialog) {
        AddAccountDialog(
            context = context,
            onDismiss = { showAddAccountDialog = false },
            onConfirm = { name, pkg, url ->
                viewModel.addAccount(ai.id, name, pkg, url)
                showAddAccountDialog = false
            }
        )
    }
    if (showDeleteConfirm) {
        ConfirmDeleteDialog(
            text = "Удалить AI «${ai.name}» и все его аккаунты?",
            onDismiss = { showDeleteConfirm = false },
            onConfirm = { viewModel.deleteAiService(ai.id); showDeleteConfirm = false }
        )
    }
}

// ─── Строка аккаунта ──────────────────────────────────────────────────────────
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AccountRow(ai: AiService, account: Account, viewModel: MainViewModel) {
    var showMenu by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showAddChatDialog by remember { mutableStateOf(false) }
    var showTimerDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val timerPair = remember(account.timerEndTimestamp) {
        account.timerEndTimestamp?.let { formatTimer(it) }
    }

    val hasChats = account.chats.isNotEmpty()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(BgAcc)
            .combinedClickable(
                onClick = {
                    val url = account.accountUrl.ifBlank { ai.baseUrl }
                    openInBrowser(context, url, account.browserPackage)
                },
                onLongClick = { showMenu = true }
            )
            .padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Стрелка аккордеона — отдельная зона клика
        Box(
            modifier = Modifier
                .size(28.dp)
                .clickable(enabled = hasChats) {
                    viewModel.toggleAccount(ai.id, account.id)
                },
            contentAlignment = Alignment.Center
        ) {
            if (hasChats) {
                Icon(
                    imageVector = if (account.isExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight,
                    contentDescription = null,
                    tint = TextHint,
                    modifier = Modifier.size(16.dp)
                )
            } else {
                Spacer(Modifier.size(16.dp))
            }
        }
        Spacer(Modifier.width(4.dp))
        BrowserIcon(browserPackage = account.browserPackage)
        Spacer(Modifier.width(8.dp))
        Text(
            text = account.name,
            color = TextMuted,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (timerPair != null && timerPair.first.isNotEmpty()) {
            Text(
                text = timerPair.first,
                color = timerPair.second,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }

    if (showMenu) {
        AccountContextMenu(
            onDismiss = { showMenu = false },
            onOpen = {
                showMenu = false
                val url = account.accountUrl.ifBlank { ai.baseUrl }
                openInBrowser(context, url, account.browserPackage)
            },
            onAddChat = { showMenu = false; showAddChatDialog = true },
            onSetTimer = { showMenu = false; showTimerDialog = true },
            onEdit = { showMenu = false; showEditDialog = true },
            onDelete = { showMenu = false; showDeleteConfirm = true }
        )
    }
    if (showEditDialog) {
        EditAccountDialog(
            account = account,
            context = context,
            onDismiss = { showEditDialog = false },
            onConfirm = { name, pkg, url ->
                viewModel.editAccount(ai.id, account.id, name, pkg, url)
                showEditDialog = false
            }
        )
    }
    if (showAddChatDialog) {
        AddChatDialog(
            onDismiss = { showAddChatDialog = false },
            onConfirm = { name, url ->
                viewModel.addChat(ai.id, account.id, name, url)
                showAddChatDialog = false
            }
        )
    }
    if (showTimerDialog) {
        TimerDialog(
            current = account.timerEndTimestamp,
            onDismiss = { showTimerDialog = false },
            onSet = { ms -> viewModel.setTimer(ai.id, account.id, ms); showTimerDialog = false },
            onClear = { viewModel.clearTimer(ai.id, account.id); showTimerDialog = false }
        )
    }
    if (showDeleteConfirm) {
        ConfirmDeleteDialog(
            text = "Удалить аккаунт «${account.name}»?",
            onDismiss = { showDeleteConfirm = false },
            onConfirm = { viewModel.deleteAccount(ai.id, account.id); showDeleteConfirm = false }
        )
    }
}

// ─── Строка чата ──────────────────────────────────────────────────────────────
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatRow(ai: AiService, account: Account, chat: Chat, viewModel: MainViewModel) {
    var showMenu by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(BgChat)
            .combinedClickable(
                onClick = { openInBrowser(context, chat.url, account.browserPackage) },
                onLongClick = { showMenu = true }
            )
            .padding(start = 52.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(5.dp)
                .clip(CircleShape)
                .background(Color(0xFF444444))
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = chat.name,
            color = TextHint,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }

    if (showMenu) {
        ChatContextMenu(
            onDismiss = { showMenu = false },
            onOpen = { showMenu = false; openInBrowser(context, chat.url, account.browserPackage) },
            onEdit = { showMenu = false; showEditDialog = true },
            onDelete = { showMenu = false; showDeleteConfirm = true }
        )
    }
    if (showEditDialog) {
        EditChatDialog(
            chat = chat,
            onDismiss = { showEditDialog = false },
            onConfirm = { name, url ->
                viewModel.editChat(ai.id, account.id, chat.id, name, url)
                showEditDialog = false
            }
        )
    }
    if (showDeleteConfirm) {
        ConfirmDeleteDialog(
            text = "Удалить чат «${chat.name}»?",
            onDismiss = { showDeleteConfirm = false },
            onConfirm = { viewModel.deleteChat(ai.id, account.id, chat.id); showDeleteConfirm = false }
        )
    }
}

// ─── Иконка favicon ───────────────────────────────────────────────────────────
@Composable
fun FaviconImage(url: String) {
    AsyncImage(
        model = url,
        contentDescription = null,
        modifier = Modifier
            .size(20.dp)
            .clip(RoundedCornerShape(4.dp)),
        error = androidx.compose.ui.res.painterResource(android.R.drawable.ic_menu_compass)
    )
}

// ─── Иконка браузера ──────────────────────────────────────────────────────────
@Composable
fun BrowserIcon(browserPackage: String) {
    val context = LocalContext.current
    val icon = remember(browserPackage) {
        try {
            context.packageManager.getApplicationIcon(browserPackage)
        } catch (e: Exception) { null }
    }
    if (icon != null) {
        val imageBitmap = remember(icon) {
            val w = if (icon.intrinsicWidth > 0) icon.intrinsicWidth else 64
            val h = if (icon.intrinsicHeight > 0) icon.intrinsicHeight else 64
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            icon.setBounds(0, 0, canvas.width, canvas.height)
            icon.draw(canvas)
            bmp.asImageBitmap()
        }
        Image(
            bitmap = imageBitmap,
            contentDescription = null,
            modifier = Modifier.size(18.dp).clip(CircleShape)
        )
    } else {
        Box(modifier = Modifier.size(18.dp).clip(CircleShape).background(Color(0xFF2A3A4A)))
    }
}

// ─── Контекстные меню ─────────────────────────────────────────────────────────
@Composable
fun AiContextMenu(
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onAddAccount: () -> Unit,
    onDelete: () -> Unit
) {
    DropdownMenu(expanded = true, onDismissRequest = onDismiss) {
        DropdownMenuItem(text = { Text("Добавить аккаунт") }, onClick = onAddAccount)
        DropdownMenuItem(text = { Text("Редактировать") }, onClick = onEdit)
        DropdownMenuItem(text = { Text("Удалить", color = TimerRed) }, onClick = onDelete)
    }
}

@Composable
fun AccountContextMenu(
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onAddChat: () -> Unit,
    onSetTimer: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    DropdownMenu(expanded = true, onDismissRequest = onDismiss) {
        DropdownMenuItem(text = { Text("Открыть") }, onClick = onOpen)
        DropdownMenuItem(text = { Text("Добавить чат") }, onClick = onAddChat)
        DropdownMenuItem(text = { Text("Таймер") }, onClick = onSetTimer)
        DropdownMenuItem(text = { Text("Редактировать") }, onClick = onEdit)
        DropdownMenuItem(text = { Text("Удалить", color = TimerRed) }, onClick = onDelete)
    }
}

@Composable
fun ChatContextMenu(
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    DropdownMenu(expanded = true, onDismissRequest = onDismiss) {
        DropdownMenuItem(text = { Text("Открыть") }, onClick = onOpen)
        DropdownMenuItem(text = { Text("Редактировать") }, onClick = onEdit)
        DropdownMenuItem(text = { Text("Удалить", color = TimerRed) }, onClick = onDelete)
    }
}

// ─── Диалоги ──────────────────────────────────────────────────────────────────
@Composable
fun AddAiDialog(onDismiss: () -> Unit, onConfirm: (String, String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("https://") }
    var iconUrl by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Добавить AI") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Название") }, singleLine = true)
                OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text("URL сайта") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
                OutlinedTextField(value = iconUrl, onValueChange = { iconUrl = it }, label = { Text("URL иконки (необязательно)") }, singleLine = true)
            }
        },
        confirmButton = {
            val urlValid = url.trim().length > 8 && url.trim() != "https://"
            TextButton(onClick = { if (name.isNotBlank() && urlValid) onConfirm(name.trim(), url.trim(), iconUrl.trim()) }) {
                Text("Добавить")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

@Composable
fun EditAiDialog(ai: AiService, onDismiss: () -> Unit, onConfirm: (String, String, String) -> Unit) {
    var name by remember { mutableStateOf(ai.name) }
    var url by remember { mutableStateOf(ai.baseUrl) }
    var iconUrl by remember { mutableStateOf(ai.iconUrl) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Редактировать AI") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Название") }, singleLine = true)
                OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text("URL сайта") }, singleLine = true)
                OutlinedTextField(value = iconUrl, onValueChange = { iconUrl = it }, label = { Text("URL иконки") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank() && url.isNotBlank()) onConfirm(name.trim(), url.trim(), iconUrl.trim()) }) {
                Text("Сохранить")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

@Composable
fun AddAccountDialog(context: Context, onDismiss: () -> Unit, onConfirm: (String, String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var accountUrl by remember { mutableStateOf("https://") }
    var selectedPkg by remember { mutableStateOf("") }
    val browsers = remember { getInstalledBrowsers(context) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Добавить аккаунт") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Имя аккаунта") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = accountUrl, onValueChange = { accountUrl = it }, label = { Text("URL аккаунта") }, singleLine = true, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
                Text("Браузер:", color = TextMuted, fontSize = 13.sp)
                androidx.compose.foundation.lazy.LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
                    items(browsers) { (pkg, label) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedPkg = pkg }
                                .padding(vertical = 2.dp)
                        ) {
                            RadioButton(selected = selectedPkg == pkg, onClick = { selectedPkg = pkg })
                            Spacer(Modifier.width(4.dp))
                            Text(label, fontSize = 13.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isNotBlank() && selectedPkg.isNotBlank())
                    onConfirm(name.trim(), selectedPkg, accountUrl.trim())
            }) { Text("Добавить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

@Composable
fun EditAccountDialog(account: Account, context: Context, onDismiss: () -> Unit, onConfirm: (String, String, String) -> Unit) {
    var name by remember { mutableStateOf(account.name) }
    var accountUrl by remember { mutableStateOf(account.accountUrl.ifBlank { "https://" }) }
    var selectedPkg by remember { mutableStateOf(account.browserPackage) }
    val browsers = remember { getInstalledBrowsers(context) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Редактировать аккаунт") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Имя") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = accountUrl, onValueChange = { accountUrl = it }, label = { Text("URL аккаунта") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("Браузер:", color = TextMuted, fontSize = 13.sp)
                androidx.compose.foundation.lazy.LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
                    items(browsers) { (pkg, label) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedPkg = pkg }
                                .padding(vertical = 2.dp)
                        ) {
                            RadioButton(selected = selectedPkg == pkg, onClick = { selectedPkg = pkg })
                            Spacer(Modifier.width(4.dp))
                            Text(label, fontSize = 13.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isNotBlank())
                    onConfirm(name.trim(), selectedPkg, accountUrl.trim())
            }) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

@Composable
fun AddChatDialog(onDismiss: () -> Unit, onConfirm: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("https://") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Добавить чат") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Название чата") }, singleLine = true)
                OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text("URL чата") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
            }
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank() && url.isNotBlank()) onConfirm(name.trim(), url.trim()) }) {
                Text("Добавить")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

@Composable
fun EditChatDialog(chat: Chat, onDismiss: () -> Unit, onConfirm: (String, String) -> Unit) {
    var name by remember { mutableStateOf(chat.name) }
    var url by remember { mutableStateOf(chat.url) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Редактировать чат") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Название") }, singleLine = true)
                OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text("URL") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank() && url.isNotBlank()) onConfirm(name.trim(), url.trim()) }) {
                Text("Сохранить")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

@Composable
fun TimerDialog(
    current: Long?,
    onDismiss: () -> Unit,
    onSet: (Long) -> Unit,
    onClear: () -> Unit
) {
    // Режим: 0 = через сколько, 1 = до какого времени
    var mode by remember { mutableStateOf(0) }
    var hours by remember { mutableStateOf("") }
    var minutes by remember { mutableStateOf("") }
    var untilHour by remember { mutableStateOf("") }
    var untilMinute by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Таймер") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Переключатель режима
                Row(modifier = Modifier.fillMaxWidth()) {
                    listOf("Через сколько", "До времени").forEachIndexed { i, label ->
                        val selected = mode == i
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(
                                    if (selected) Color(0xFF2A3A4A) else Color(0xFF1A1A1E),
                                    RoundedCornerShape(6.dp)
                                )
                                .clickable { mode = i }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                label,
                                fontSize = 12.sp,
                                color = if (selected) TextPrimary else TextHint,
                                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal
                            )
                        }
                        if (i == 0) Spacer(Modifier.width(6.dp))
                    }
                }

                if (mode == 0) {
                    Text("Лимит снимется через:", fontSize = 12.sp, color = TextMuted)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = hours,
                            onValueChange = { hours = it.filter { c -> c.isDigit() } },
                            label = { Text("Часы") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = minutes,
                            onValueChange = { minutes = it.filter { c -> c.isDigit() } },
                            label = { Text("Минуты") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }
                } else {
                    Text("Лимит снимется в:", fontSize = 12.sp, color = TextMuted)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = untilHour,
                            onValueChange = { v ->
                                val n = v.filter { it.isDigit() }
                                if (n.isEmpty() || (n.toIntOrNull() ?: 0) <= 23) untilHour = n
                            },
                            label = { Text("Час (0-23)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        Text(":", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        OutlinedTextField(
                            value = untilMinute,
                            onValueChange = { v ->
                                val n = v.filter { it.isDigit() }
                                if (n.isEmpty() || (n.toIntOrNull() ?: 0) <= 59) untilMinute = n
                            },
                            label = { Text("Мин (0-59)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                if (current != null) {
                    TextButton(onClick = onClear) {
                        Text("Отключить таймер", color = TimerRed)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (mode == 0) {
                    val h = hours.toLongOrNull() ?: 0L
                    val m = minutes.toLongOrNull() ?: 0L
                    val ms = (h * 60 + m) * 60000L
                    if (ms > 0) onSet(ms)
                } else {
                    val h = untilHour.toIntOrNull() ?: return@TextButton
                    val m = untilMinute.toIntOrNull() ?: 0
                    val now = java.util.Calendar.getInstance()
                    val target = java.util.Calendar.getInstance().apply {
                        set(java.util.Calendar.HOUR_OF_DAY, h)
                        set(java.util.Calendar.MINUTE, m)
                        set(java.util.Calendar.SECOND, 0)
                        set(java.util.Calendar.MILLISECOND, 0)
                        // Если указанное время уже прошло сегодня — ставим на завтра
                        if (timeInMillis <= now.timeInMillis) add(java.util.Calendar.DAY_OF_MONTH, 1)
                    }
                    val ms = target.timeInMillis - now.timeInMillis
                    if (ms > 0) onSet(ms)
                }
            }) { Text("Установить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

@Composable
fun ConfirmDeleteDialog(text: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Удалить?") },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Удалить", color = TimerRed) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

// ─── Получение списка браузеров ───────────────────────────────────────────────
fun getInstalledBrowsers(context: Context): List<Pair<String, String>> {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"))
    val pm = context.packageManager
    @Suppress("DEPRECATION")
    val activities = pm.queryIntentActivities(intent, android.content.pm.PackageManager.MATCH_ALL)
    return activities.map { info ->
        val pkg = info.activityInfo.packageName
        val label = pm.getApplicationLabel(info.activityInfo.applicationInfo).toString()
        Pair(pkg, label)
    }.distinctBy { it.first }.sortedBy { it.second }
}