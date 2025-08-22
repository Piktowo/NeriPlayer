package moe.ouom.neriplayer.ui.screen.debug

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SettingsBackupRestore
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import moe.ouom.neriplayer.R

@Composable
fun DebugHomeScreen(
    onOpenBiliDebug: () -> Unit,
    onOpenNeteaseDebug: () -> Unit,
    onOpenSearchDebug: () -> Unit,
    onOpenLogs: () -> Unit,
    onHideDebugMode: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ListItem(
            leadingContent = {
                Icon(
                    imageVector = Icons.Outlined.BugReport,
                    contentDescription = "调试",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            },
            headlineContent = { Text("调试工具") },
            colors = ListItemDefaults.colors(
                containerColor = Color.Transparent
            ),
            supportingContent = { Text("选择要调试的平台或隐藏调试模式") },
        )

        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
            )
        ) {
            Column(Modifier.fillMaxWidth()) {
                ListItem(
                    leadingContent = {
                        Icon(
                            painter = androidx.compose.ui.res.painterResource(id = R.drawable.ic_bilibili),
                            contentDescription = "B 站",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(24.dp),
                        )
                    },
                    headlineContent = { Text("B 站接口调试") },
                    supportingContent = { Text("搜索 / 取封面简介 / 统计 / 取流 / 收藏夹") },
                    modifier = Modifier.clickable(onClick = onOpenBiliDebug),
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                )

                ListItem(
                    leadingContent = {
                        Icon(
                            painter = androidx.compose.ui.res.painterResource(id = R.drawable.ic_netease_cloud_music),
                            contentDescription = "网易云",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(24.dp),
                        )
                    },
                    headlineContent = { Text("网易云接口调试") },
                    supportingContent = { Text("账户 / 歌单 / 歌曲 URL / 歌词") },
                    modifier = Modifier.clickable(onClick = onOpenNeteaseDebug),
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                )

                ListItem(
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Outlined.Search,
                            contentDescription = "搜索",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(24.dp),
                        )
                    },
                    headlineContent = { Text("多平台搜索接口调试") },
                    supportingContent = { Text("QQ / 网易云") },
                    modifier = Modifier.clickable(onClick = onOpenSearchDebug),
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                )

                ListItem(
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Outlined.Description,
                            contentDescription = "日志",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(24.dp),
                        )
                    },
                    headlineContent = { Text("查看应用日志") },
                    supportingContent = { Text("查看、复制和导出本次运行的日志") },
                    modifier = Modifier.clickable(onClick = onOpenLogs),
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        Button(onClick = onHideDebugMode) {
            Icon(
                imageVector = Icons.Outlined.SettingsBackupRestore,
                contentDescription = "隐藏"
            )
            Spacer(Modifier.height(0.dp))
            Text("隐藏调试模式")
        }

        ListItem(
            leadingContent = {
                Icon(
                    imageVector = Icons.Outlined.Build,
                    contentDescription = "提示",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            },
            headlineContent = { Text("提示") },
            supportingContent = {
                Text("隐藏后底部栏将移除“调试”，可在设置页点击版本号 7 次再次开启")
            },
            colors = ListItemDefaults.colors(
                containerColor = Color.Transparent
            )
        )
    }
}