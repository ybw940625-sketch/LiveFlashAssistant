package com.example.liveflash

data class FlashConfig(
    val roomUrl: String = "",
    val targetWords: String = "",
    val expectedPrice: String = "",
    val alertCooldownSeconds: Int = 8
) {
    fun keywords(): List<String> = targetWords
        .split(',', '，', '\n')
        .map { it.trim() }
        .filter { it.length >= 2 }
}
