package com.example.liveflash

import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.util.DisplayMetrics
import android.view.WindowManager
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import java.util.concurrent.atomic.AtomicBoolean

class ScreenMonitorService : Service() {
    companion object {
        const val ACTION_START = "com.example.liveflash.START"
        const val ACTION_STOP = "com.example.liveflash.STOP"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        const val EXTRA_KEYWORDS = "keywords"
        const val EXTRA_COOLDOWN = "cooldown"
        private const val NOTIFICATION_ID = 1001
    }

    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var worker: HandlerThread? = null
    private var handler: Handler? = null
    private val busy = AtomicBoolean(false)
    private var lastAlertAt = 0L
    private var keywords: List<String> = emptyList()
    private var cooldownMs = 8000L
    private val recognizer by lazy {
        TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopCapture()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START -> startCapture(intent)
        }
        return START_NOT_STICKY
    }

    private fun startCapture(intent: Intent) {
        if (projection != null) return

        keywords = intent.getStringArrayListExtra(EXTRA_KEYWORDS) ?: emptyList()
        cooldownMs = (intent.getIntExtra(EXTRA_COOLDOWN, 8).coerceIn(2, 120) * 1000L)

        Alerting.createChannel(this)
        startForeground(NOTIFICATION_ID, buildNotification("正在准备屏幕识别"))

        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
        val data = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_RESULT_DATA)
        }

        if (data == null) {
            MonitorState.updateStatus("屏幕授权数据缺失")
            stopSelf()
            return
        }

        try {
            val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            projection = manager.getMediaProjection(resultCode, data)
            projection?.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    stopCapture()
                    stopSelf()
                }
            }, Handler(mainLooper))

            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            (getSystemService(WINDOW_SERVICE) as WindowManager).defaultDisplay.getRealMetrics(metrics)

            val width = metrics.widthPixels
            val height = metrics.heightPixels
            val density = metrics.densityDpi

            worker = HandlerThread("FlashOCR").also { it.start() }
            handler = Handler(worker!!.looper)
            reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
            reader!!.setOnImageAvailableListener({ imageReader ->
                val image = imageReader.acquireLatestImage() ?: return@setOnImageAvailableListener
                processImage(image)
            }, handler)

            virtualDisplay = projection!!.createVirtualDisplay(
                "LiveFlashOCR",
                width,
                height,
                density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader!!.surface,
                null,
                handler
            )

            MonitorState.updateStatus("正在识别屏幕文字；不会保存截图")
        } catch (e: Exception) {
            MonitorState.updateStatus("启动失败：" + (e.message ?: "未知错误"))
            stopCapture()
            stopSelf()
        }
    }

    private fun processImage(image: Image) {
        if (!busy.compareAndSet(false, true)) {
            image.close()
            return
        }

        try {
            val plane = image.planes[0]
            val buffer = plane.buffer
            val pixelStride = plane.pixelStride
            val rowStride = plane.rowStride
            val rowPadding = rowStride - pixelStride * image.width
            val paddedWidth = image.width + rowPadding / pixelStride
            val padded = Bitmap.createBitmap(
                paddedWidth, image.height, Bitmap.Config.ARGB_8888
            )
            padded.copyPixelsFromBuffer(buffer)
            val bitmap = Bitmap.createBitmap(padded, 0, 0, image.width, image.height)
            padded.recycle()
            image.close()

            val input = InputImage.fromBitmap(bitmap, 0)
            recognizer.process(input)
                .addOnSuccessListener { result ->
                    val text = result.text
                    // Avoid processing or surfacing payment/checkout screens.
                    val sensitive = listOf(
                        "支付密码", "银行卡号", "安全码", "验证码",
                        "确认付款", "立即付款", "付款方式", "收银台"
                    ).any { text.contains(it) }

                    if (sensitive) {
                        // Do not expose or retain text from payment/checkout screens.
                        MonitorState.updateStatus("检测到支付/结算页面，已暂停内容匹配")
                        return@addOnSuccessListener
                    }

                    MonitorState.updateText(text.ifBlank { "未识别到文字" })
                    val match = keywords.firstOrNull { it.isNotBlank() && text.contains(it, true) }
                    val now = System.currentTimeMillis()
                    if (match != null && now - lastAlertAt >= cooldownMs) {
                        lastAlertAt = now
                        MonitorState.incrementMatch()
                        MonitorState.updateStatus("发现疑似目标关键词：$match，请人工核对商品")
                        Alerting.beep()
                        Alerting.notifyMatch(this, "匹配关键词：$match。请核对商品、规格和价格。")
                    }
                }
                .addOnFailureListener {
                    MonitorState.updateStatus("OCR 识别暂时失败")
                }
                .addOnCompleteListener {
                    bitmap.recycle()
                    busy.set(false)
                }
        } catch (e: Exception) {
            runCatching { image.close() }
            busy.set(false)
        }
    }

    private fun buildNotification(message: String): Notification {
        return Notification.Builder(this, Alerting.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle("直播秒杀助手正在运行")
            .setContentText(message)
            .setOngoing(true)
            .build()
    }

    private fun stopCapture() {
        runCatching { reader?.setOnImageAvailableListener(null, null) }
        runCatching { virtualDisplay?.release() }
        virtualDisplay = null
        runCatching { reader?.close() }
        reader = null
        runCatching { projection?.stop() }
        projection = null
        runCatching { worker?.quitSafely() }
        worker = null
        handler = null
        MonitorState.updateStatus("监控已停止")
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    override fun onDestroy() {
        stopCapture()
        runCatching { recognizer.close() }
        super.onDestroy()
    }
}
