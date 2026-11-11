package com.example.liveflash

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.os.Handler
import android.os.Looper
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class JdPurchaseAssistService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private val zone = ZoneId.of("Asia/Shanghai")
    private var attemptedDate: LocalDate? = null

    private val dailyCheck = object : Runnable {
        override fun run() {
            inspectAndClick()
            scheduleNextDay()
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()

        // 如果今天已经过了10点，立即检查当前页面
        inspectAndClick()

        // 安排下一次每天上午10点的检查
        scheduleNextDay()
    }

    private fun scheduleNextDay() {
        handler.removeCallbacks(dailyCheck)

        val now = ZonedDateTime.now(zone)
        var next = now.toLocalDate().atTime(10, 0).atZone(zone)

        if (!next.isAfter(now)) {
            next = next.plusDays(1)
        }

        val delay = java.time.Duration.between(now, next).toMillis()
        handler.postDelayed(dailyCheck, delay)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.packageName?.toString() != "com.jingdong.app.mall") return
        inspectAndClick()
    }

    private fun inspectAndClick() {
        val now = ZonedDateTime.now(zone)
        val today = now.toLocalDate()

        if (now.toLocalTime().isBefore(LocalTime.of(10, 0))) return
        if (attemptedDate == today) return

        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString() != "com.jingdong.app.mall") return

        val pageText = collectText(root).joinToString(" ")

        // 必须确认是雷鸟商品页面
        if (!pageText.contains("雷鸟")) return

        // 到订单确认或付款页面立即停止
        val stopWords = listOf(
            "提交订单",
            "确认订单",
            "收银台",
            "立即付款",
            "支付密码"
        )

        if (stopWords.any { pageText.contains(it) }) {
            attemptedDate = today
            return
        }

        // 只识别明确的购买入口
        val buttons = listOf(
            "立即抢购",
            "马上抢购",
            "立即购买",
            "去抢购"
        )

        val target = findClickable(root, buttons)

        if (target != null) {
            val success = target.performAction(
                AccessibilityNodeInfo.ACTION_CLICK
            )
            if (success) {
                attemptedDate = today
            }
        }
    }

    private fun collectText(node: AccessibilityNodeInfo): List<String> {
        val result = mutableListOf<String>()

        node.text?.toString()?.let { result.add(it) }
        node.contentDescription?.toString()?.let { result.add(it) }

        for (i in 0 until node.childCount) {
            node.getChild(i)?.let {
                result.addAll(collectText(it))
            }
        }

        return result
    }

    private fun findClickable(
        node: AccessibilityNodeInfo,
        labels: List<String>
    ): AccessibilityNodeInfo? {
        val text = node.text?.toString().orEmpty()
        val desc = node.contentDescription?.toString().orEmpty()

        if (labels.any { text.contains(it) || desc.contains(it) }) {
            if (node.isClickable) return node

            var parent = node.parent
            while (parent != null) {
                if (parent.isClickable) return parent
                parent = parent.parent
            }
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findClickable(child, labels)
            if (found != null) return found
        }

        return null
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
