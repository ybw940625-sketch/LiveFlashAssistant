package com.example.liveflash

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private const val JD_URL = "https://item.jd.com/100327520619.html"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Alerting.createChannel(this)
        setContent {
            MaterialTheme {
                val context = LocalContext.current
                Column(
                    modifier = Modifier.fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text("京东抢购辅助", style = MaterialTheme.typography.headlineMedium)
                    Text("目标：雷鸟眼镜 Bilibili 联名款")
                    Text("自动触发时间：每天上午 10:00（北京时间）")

                    Button(
                        onClick = {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(JD_URL))
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("打开京东目标商品")
                    }

                    OutlinedButton(
                        onClick = {
                            context.startActivity(
                                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("开启京东辅助功能权限")
                    }

                    Text("使用方法：开启辅助权限后，打开京东目标商品页面，并保持页面在前台。")
                    Text("每天北京时间上午10点尝试识别购买入口。订单提交和付款由你本人确认。")
                    Text(
                        "注意：定时点击功能仍需实机验证，不能保证抢购成功。",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}
