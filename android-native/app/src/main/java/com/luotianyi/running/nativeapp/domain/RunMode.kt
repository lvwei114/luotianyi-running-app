package com.luotianyi.running.nativeapp.domain

enum class RunMode(val label: String, val description: String) {
    GUIDE("跟天依跑", "按歌单时长分配四阶段"),
    FOLLOW("天依跟你跑", "跟随心率调整节奏"),
    CUSTOM("自定义 BPM", "按你设定的固定节奏"),
    TIMED("定时跑", "独立选择 5–60 分钟");

    companion object {
        fun fromStored(value: String?) = entries.firstOrNull { it.name == value } ?: GUIDE
    }
}
