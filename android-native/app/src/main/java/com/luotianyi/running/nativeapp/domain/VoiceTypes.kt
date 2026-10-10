package com.luotianyi.running.nativeapp.domain

enum class VoiceCategory(val label: String) {
    OPENING("准备与开场"), WARMUP("热身"), BURN("燃脂"), SPRINT("冲刺"),
    COOLDOWN("放松"), SKIP_COOLDOWN("跳过放松"), PAUSE("暂停"), RESUME("继续"), FINISH("训练结束"),
    CHEER("随机鼓励"), WATER_BEFORE("运动前补水"), WATER_DURING("运动中补水"), WATER_AFTER("运动后补水"),
    HR_LOW("一级心率提醒"), HR_MEDIUM("二级心率提醒"), HR_HIGH("三级心率提醒"), HR_RECOVERED("心率恢复"),
    TIME_60("60 分钟提醒"), TIME_90("90 分钟提醒"), TIME_LONG("长时间运动提醒"),
    CHAT_START("开场聊天"), CHAT_MID("途中聊天"), CHAT_END("结束聊天"), CHAT_CASUAL("日常闲聊")
}

data class VoiceLine(val id: String, val text: String, val category: VoiceCategory)
enum class VoicePriority { COMPANION, REMINDER, STAGE, CONTROL, SAFETY }
data class VoiceRequest(val line: VoiceLine, val priority: VoicePriority,
    val expiresAt: Long = Long.MAX_VALUE, val allowQueue: Boolean = false)
