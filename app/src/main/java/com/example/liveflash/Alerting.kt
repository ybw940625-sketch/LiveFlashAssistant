package com.example.liveflash

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build

object Alerting {
    const val CHANNEL_ID = "flash_match_alerts"

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "目标商品提醒",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "识别到你设置的目标商品时提醒"
                enableVibration(true)
            }
            context.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    fun beep() {
        runCatching {
            ToneGenerator(AudioManager.STREAM_NOTIFICATION, 85)
                .startTone(ToneGenerator.TONE_PROP_BEEP, 350)
        }
    }

    fun notifyMatch(context: Context, text: String) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val notification = android.app.Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("发现疑似目标商品")
            .setContentText(text.take(100))
            .setAutoCancel(true)
            .build()
        manager.notify(2002, notification)
    }
}
