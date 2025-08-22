package moe.ouom.neriplayer.activity

import android.Manifest
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toDrawable
import androidx.core.graphics.toColorInt
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import moe.ouom.neriplayer.core.player.PlayerEvent
import moe.ouom.neriplayer.core.player.PlayerManager
import moe.ouom.neriplayer.data.SettingsRepository
import moe.ouom.neriplayer.ui.NeriApp
import moe.ouom.neriplayer.util.HapticButton
import moe.ouom.neriplayer.util.HapticTextButton
import moe.ouom.neriplayer.util.NPLogger

private enum class AppStage { Loading, Disclaimer, Main }

class MainActivity : ComponentActivity() {
    private val settingsRepository by lazy { SettingsRepository(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {

            val repo = SettingsRepository(this)
            val devModeEnabled by repo.devModeEnabledFlow.collectAsState(initial = false)
            NPLogger.init(context = this, enableFileLogging = devModeEnabled)

            val dynamicColor by settingsRepository.dynamicColorFlow.collectAsState(initial = true)
            val forceDark by settingsRepository.forceDarkFlow.collectAsState(initial = false)
            val followSystemDark by settingsRepository.followSystemDarkFlow.collectAsState(initial = true)
            val disclaimerAcceptedNullable by settingsRepository.disclaimerAcceptedFlow.collectAsState(initial = null)

            val systemDark = isSystemInDarkTheme()
            val useDark = remember(forceDark, followSystemDark, systemDark) {
                when {
                    forceDark -> true
                    followSystemDark -> systemDark
                    else -> false
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val launcher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) {  }
                LaunchedEffect(Unit) {
                    launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }

            NeriTheme(useDark = useDark, useDynamic = dynamicColor) {
                SideEffect {
                    applyWindowBackground(useDark)
                    val controller = WindowInsetsControllerCompat(window, window.decorView)
                    controller.isAppearanceLightStatusBars = !useDark
                    controller.isAppearanceLightNavigationBars = !useDark
                }

                var playedEntrance by rememberSaveable { mutableStateOf(false) }
                LaunchedEffect(Unit) { playedEntrance = true }

                val stage = when (disclaimerAcceptedNullable) {
                    null -> AppStage.Loading
                    true -> AppStage.Main
                    false -> AppStage.Disclaimer
                }

                AnimatedContent(
                    targetState = stage,
                    transitionSpec = {
                        val enter = slideInVertically(
                            animationSpec = tween(durationMillis = 550, easing = FastOutSlowInEasing),
                            initialOffsetY = { fullHeight ->
                                if (playedEntrance) fullHeight / 8 else 0
                            }
                        ) + fadeIn(animationSpec = tween(350, delayMillis = if (playedEntrance) 50 else 0))

                        val exit = slideOutVertically(
                            animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
                            targetOffsetY = { -it / 12 }
                        ) + fadeOut(animationSpec = tween(250))

                        enter togetherWith exit using SizeTransform(clip = false)
                    },
                    label = "AppStageTransition"
                ) { current ->
                    when (current) {
                        AppStage.Loading -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .statusBarsPadding()
                                    .navigationBarsPadding()
                            )
                        }
                        AppStage.Disclaimer -> {
                            val scope = rememberCoroutineScope()
                            DisclaimerScreen(
                                onAgree = { scope.launch { settingsRepository.setDisclaimerAccepted(true) } }
                            )
                        }
                        AppStage.Main -> {

                            var showDialog by remember { mutableStateOf(false) }
                            var dialogMessage by remember { mutableStateOf("") }
                            val lifecycleOwner = LocalLifecycleOwner.current

                            LaunchedEffect(lifecycleOwner.lifecycle) {
                                lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                                    PlayerManager.playerEventFlow.collect { event ->
                                        when (event) {
                                            is PlayerEvent.ShowLoginPrompt -> {
                                                dialogMessage = event.message
                                                showDialog = true
                                            }

                                            is PlayerEvent.ShowError -> {
                                                dialogMessage = event.message
                                                showDialog = true
                                            }
                                        }
                                    }
                                }
                            }

                            if (showDialog) {
                                AlertDialog(
                                    onDismissRequest = { showDialog = false },
                                    title = { Text("提示") },
                                    text = { Text(dialogMessage) },
                                    confirmButton = {
                                        HapticTextButton(onClick = { showDialog = false }) {
                                            Text("确定")
                                        }
                                    }
                                )
                            }

                            NeriApp(
                                onIsDarkChanged = { isDark ->

                                    applyWindowBackground(isDark)
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun applyWindowBackground(isDark: Boolean) {
        val bgColor = if (isDark) "#121212".toColorInt() else Color.WHITE
        window.setBackgroundDrawable(bgColor.toDrawable())
        @Suppress("DEPRECATION")
        run {
            window.statusBarColor = Color.TRANSPARENT
            window.navigationBarColor = Color.TRANSPARENT
        }
    }
}

@Composable
fun NeriTheme(
    useDark: Boolean,
    useDynamic: Boolean,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        useDynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (useDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        else -> {
            if (useDark) darkColorScheme() else lightColorScheme()
        }
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content
    )
}

@Composable
fun DisclaimerScreen(onAgree: () -> Unit) {
    var countdown by remember { mutableIntStateOf(5) }
    LaunchedEffect(Unit) { while (countdown > 0) { delay(1000); countdown-- } }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "免责声明与隐私说明",
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(12.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    SectionTitle("1. 软件性质说明")
                    BodyText(
                        "本软件（音理音理!）为跨平台音视频播放工具，仅用于整合并播放来自多个第三方在线平台的公开内容。" +
                                "本软件本身不上传、分发或修改任何音视频文件；不在服务器侧存储任何用户内容或第三方内容。" +
                                "为便于离线播放，本软件提供“仅保存至本地设备”的下载功能，下载过程不经过开发者服务器，亦不会进行任何云端同步或备份。"
                    )

                    SectionTitle("2. 内容来源与版权归属")
                    Bullets(
                        listOf(
                            "所有可播放内容均由用户自行从第三方平台访问，版权及相关权益归原作者或平台所有；",
                            "用户使用时应遵守所在国家/地区的法律法规；",
                            "软件支持将第三方平台的音视频内容下载至用户本地设备，但不会上传、同步或存储至任何服务器；",
                            "不得以任何方式对第三方内容实施侵权。"
                        )
                    )

                    SectionTitle("3. 使用者责任")
                    Bullets(
                        listOf(
                            "用户应自行确认访问与下载内容的合法来源与使用权限；",
                            "开发者不承诺第三方平台内容的合法性或可用性；",
                            "因用户行为（包括下载、分享、传播等）引发的法律风险由用户自行负责。"
                        )
                    )

                    SectionTitle("4. 第三方服务与政策")
                    Bullets(
                        listOf(
                            "本软件部分功能依赖第三方平台接口与服务；",
                            "开发者不就第三方服务的稳定性、兼容性、时效性作出任何保证；",
                            "用户应自行查阅并遵守各平台的使用条款与隐私政策。"
                        )
                    )

                    SectionTitle("5. 隐私说明（无数据收集）")
                    Bullets(
                        listOf(
                            "本软件不收集任何个人身份信息；",
                            "本软件不收集任何设备信息；",
                            "本软件不进行行为跟踪、分析或用户画像；",
                            "播放记录、搜索记录等仅保留在本地；",
                            "当用户发起下载时，文件仅保存至本地存储，不会回传开发者或任何第三方；",
                            "不接入第三方统计、崩溃分析或广告SDK；",
                            "第三方平台访问日志由该平台依据其隐私政策处理；",
                            "本隐私说明更新日期：2025-08-09。"
                        )
                    )

                    SectionTitle("6. 免责与责任限制")
                    Bullets(
                        listOf(
                            "本软件按“现状”提供，不对稳定性等作任何担保；",
                            "在法律允许范围内，开发者不对因使用（包括下载行为）造成的损失负责；",
                            "即便已被告知可能发生此类损失，上述限制仍适用。"
                        )
                    )

                    SectionTitle("7. 合规与地区差异")
                    BodyText("用户应确保自身使用与下载行为符合所在法域的法律规定。")

                    SectionTitle("8. 条款更新")
                    BodyText("开发者可不时更新本免责声明与隐私说明，继续使用即视为接受更新。")

                    SectionTitle("9. 同意条款")
                    EmphasisText("使用本软件即表示您已阅读、理解并同意本页面全部内容，若不同意请卸载并停止使用。")
                }

                Spacer(Modifier.height(16.dp))

                HapticButton(
                    onClick = { onAgree() },
                    enabled = countdown == 0,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (countdown == 0) "我已知晓并同意" else "请阅读（${countdown}s）",
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        modifier = Modifier.padding(top = 6.dp)
    )
}
@Composable private fun BodyText(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Justify)
}
@Composable private fun EmphasisText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
        color = MaterialTheme.colorScheme.error,
        textAlign = TextAlign.Justify
    )
}
@Composable private fun Bullets(items: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach { item ->
            Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
                Text("• ", style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = item,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Justify,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}