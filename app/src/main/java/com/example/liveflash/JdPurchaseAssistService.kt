
package com.example.liveflash

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.os.Handler
import android.os.Looper
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicBoolean

class JdPurchaseAssistService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private val attempted = AtomicBoolean(false)

    private val targetTime = LocalDateTime.of(2026, 11, 13, 10, 0)
        .atZone(ZoneId.of("Asia/Shanghai"))
        .toInstant()
        .toEpochMilli()

    override fun onServiceConnected() {
        super.onServiceConnected()

        val delay = (targetTime - System.currentTimeMillis()).coerceAtLeast(0L)

        handler.postDelayed({
            inspectAndClick()
        }, delay)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (attempted.get()) return
        if (System.currentTimeMillis() < targetTime) return

        if (event?.packageName?.toString() != "com.jingdong.app.mall") return

        inspectAndClick()
    }

    private fun inspectAndClick() {
        if (attempted.get()) return
        if (System.currentTimeMillis() < targetTime) return

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
            attempted.set(true)
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

        if (target != null && attempted.compareAndSet(false, true)) {
            target.performAction(
                AccessibilityNodeInfo.ACTION_CLICK
            )
        }
    }

    private fun collectText(
        node: AccessibilityNodeInfo
    ): List<String> {
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

    override fun onInterrupt() {
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
