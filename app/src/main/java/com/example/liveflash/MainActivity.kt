package com.example.liveflash

import android.Manifest
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Alerting.createChannel(this)
        setContent { MaterialTheme { FlashAssistantApp() } }
    }
}

@Composable
fun FlashAssistantApp() {
    val context = LocalContext.current
    var roomUrl by remember { mutableStateOf("") }
    var keywords by remember { mutableStateOf("目标商品,商品名称") }
    var cooldown by remember { mutableStateOf("8") }
    val status by MonitorState.status.collectAsState()
    val lastText by MonitorState.lastText.collectAsState()
    val matches by MonitorState.matches.collectAsState()

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    val projectionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK && result.data != null) {
            val list = keywords.split(',', '，', '\n')
                .map { it.trim() }.filter { it.length >= 2 }
            val service = Intent(context, ScreenMonitorService::class.java).apply {
                action = ScreenMonitorService.ACTION_START
                putExtra(ScreenMonitorService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(ScreenMonitorService.EXTRA_RESULT_DATA, result.data)
                putStringArrayListExtra(
                    ScreenMonitorService.EXTRA_KEYWORDS,
                    ArrayList(list)
                )
                putExtra(
                    ScreenMonitorService.EXTRA_COOLDOWN,
                    cooldown.toIntOrNull()?.coerceIn(2, 120) ?: 8
                )
            }
            ContextCompat.startForegroundService(context, service)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("直播秒杀助手 V2.0", style = MaterialTheme.typography.headlineMedium)
        Text("iQOO Z9 Turbo+ · Android 16 · 本机 OCR")

        OutlinedTextField(
            value = roomUrl,
            onValueChange = { roomUrl = it },
            label = { Text("抖音直播间分享链接（可选）") },
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = {
                val value = roomUrl.trim()
                if (value.startsWith("https://") || value.startsWith("http://")) {
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(value)))
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("打开直播间") }

        OutlinedTextField(
            value = keywords,
            onValueChange = { keywords = it },
            label = { Text("目标商品关键词（逗号分隔）") },
            supportingText = { Text("例如：品牌名,商品名称,型号；至少填写一个准确关键词") },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = cooldown,
            onValueChange = { cooldown = it.filter(Char::isDigit) },
            label = { Text("提醒冷却时间（秒，2–120）") },
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = {
                if (Build.VERSION.SDK_INT >= 33) {
                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                val manager = context.getSystemService(
                    android.content.Context.MEDIA_PROJECTION_SERVICE
                ) as MediaProjectionManager
                projectionLauncher.launch(manager.createScreenCaptureIntent())
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("授权并开始屏幕识别") }

        OutlinedButton(
            onClick = {
                context.startService(
                    Intent(context, ScreenMonitorService::class.java).apply {
                        action = ScreenMonitorService.ACTION_STOP
                    }
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("停止识别") }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("运行状态", style = MaterialTheme.typography.titleMedium)
                Text(status)
                Text("关键词提醒次数：$matches")
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("最近识别文字", style = MaterialTheme.typography.titleMedium)
                Text(lastText, style = MaterialTheme.typography.bodySmall)
            }
        }

        Text(
            "使用说明：先打开直播间，再返回本应用设置关键词并授权屏幕捕获，随后切回直播间。" +
                "识别内容仅在内存中处理，不保存截图或识别文本。识别结果可能误报，购买前请人工核对。" +
                "检测到支付/结算关键词时会暂停匹配。应用不会自动点击购买、提交订单或发起支付。",
            style = MaterialTheme.typography.bodySmall
        )
    }
}
