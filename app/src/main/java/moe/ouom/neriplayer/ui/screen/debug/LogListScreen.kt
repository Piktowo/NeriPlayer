package moe.ouom.neriplayer.ui.screen.debug

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.ouom.neriplayer.util.NPLogger
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogListScreen(
    onBack: () -> Unit,
    onLogFileClick: (String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val showClearConfirmDialog = remember { mutableStateOf(false) }

    val logFilesState = remember {
        mutableStateOf(
            NPLogger.getLogDirectory(context)?.listFiles { file ->
                file.isFile && file.name.endsWith(".txt")
            }?.sortedByDescending { it.lastModified() } ?: emptyList()
        )
    }

    if (showClearConfirmDialog.value) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog.value = false },
            title = { Text("确认清空？") },
            text = { Text("此操作将删除所有本地日志文件且无法恢复。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearConfirmDialog.value = false
                        coroutineScope.launch {
                            val directory = NPLogger.getLogDirectory(context)
                            var clearedCount = 0
                            withContext(Dispatchers.IO) {
                                directory?.listFiles { file ->
                                    file.isFile && file.name.endsWith(".txt")
                                }?.forEach {
                                    if (it.delete()) {
                                        clearedCount++
                                    }
                                }
                            }

                            logFilesState.value = emptyList()
                            snackbarHostState.showSnackbar("已清空 $clearedCount 个日志文件")
                        }
                    }
                ) {
                    Text("全部清空", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmDialog.value = false }) {
                    Text("取消")
                }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("应用日志") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (logFilesState.value.isNotEmpty()) {
                        IconButton(onClick = { showClearConfirmDialog.value = true }) {
                            Icon(Icons.Outlined.DeleteOutline, contentDescription = "清空日志")
                        }
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding)) {
            if (logFilesState.value.isEmpty()) {
                item {
                    ListItem(
                        headlineContent = { Text("没有找到日志文件") },
                        supportingContent = { Text("请确保已在设置中开启文件日志记录") }
                    )
                }
            } else {
                items(logFilesState.value) { file ->
                    ListItem(
                        headlineContent = { Text(file.name) },
                        supportingContent = { Text(formatFileMeta(file)) },
                        leadingContent = { Icon(Icons.Outlined.Description, null) },
                        modifier = Modifier.clickable { onLogFileClick(file.absolutePath) }
                    )
                }
            }
        }
    }
}

private fun formatFileMeta(file: File): String {
    val date = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(file.lastModified()))
    val size = file.length() / 1024
    return "$date - ${size}KB"
}