package com.example.liveflash

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object MonitorState {
    private val _status = MutableStateFlow("尚未开始监控")
    val status = _status.asStateFlow()

    private val _lastText = MutableStateFlow("识别结果将在这里显示")
    val lastText = _lastText.asStateFlow()

    private val _matches = MutableStateFlow(0)
    val matches = _matches.asStateFlow()

    fun updateStatus(value: String) { _status.value = value }
    fun updateText(value: String) { _lastText.value = value.take(1200) }
    fun incrementMatch() { _matches.value += 1 }
    fun reset() { _matches.value = 0; _lastText.value = "等待识别" }
}
