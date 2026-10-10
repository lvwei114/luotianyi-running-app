package com.luotianyi.running.nativeapp.domain

import com.luotianyi.running.nativeapp.domain.VoiceCategory.*

/** Canonical captions shared by automatic speech, previews and TTS fallback. */
object VoiceCatalog {
    val all = listOf(
        VoiceLine("zhunbei", "准备好啦～三、二、一，跟天依一起出发吧！", OPENING),
        VoiceLine("start_a", "运动开始啦！慢慢跟上节奏，一定要注意呼吸啊！", OPENING),
        VoiceLine("start_b", "出发喽，跟着天依的节奏，今天也要好好加油呀！", OPENING),
        VoiceLine("reshen", "先慢慢跑哦，跟着天依的节奏，深呼吸～", WARMUP),
        VoiceLine("warmup_a", "先慢慢跑，活动开关节，热身充分了才不容易受伤。", WARMUP),
        VoiceLine("warmup_b", "热身时间，别着急提速，先让身体热起来，天依陪你慢慢来。", WARMUP),
        VoiceLine("ranzhi", "进入燃脂区啦，保持节奏，脂肪在燃烧哦～", BURN),
        VoiceLine("chongci", "冲刺时间到！全力以赴，天依陪你冲——！", SPRINT),
        VoiceLine("fangsong", "最后啦，放慢脚步深呼吸，我们一起慢慢来～", COOLDOWN),
        VoiceLine("cooldown_a", "放松时间到，慢慢走一走，让心跳平稳下来。最后，要记得拉伸呀！", COOLDOWN),
        VoiceLine("cooldown_b", "最后啦，放慢脚步深呼吸。", COOLDOWN),
        VoiceLine("skip_cooldown_a", "不要突然停下哦！慢走两分钟再结束。", SKIP_COOLDOWN),
        VoiceLine("skip_cooldown_b", "先别停，先慢慢走一会儿，让心率降下来吧。", SKIP_COOLDOWN),
        VoiceLine("zanting", "运动暂停！休息一下再继续吧！", PAUSE),
        VoiceLine("kaishi", "运动开始！加油呀！", RESUME),
        VoiceLine("jieshu", "训练完成啦！天依觉得你好棒，记得拉伸放松哦～", FINISH),
        VoiceLine("jiayou", "加油哦，天依相信你！", CHEER),
        VoiceLine("jianchi", "再坚持一下下，就快到啦～", CHEER),
        VoiceLine("huxi", "稳住呼吸，跟着节拍一起哦～", CHEER),
        VoiceLine("henbang", "很棒很棒，就是这个节奏！", CHEER),
        VoiceLine("songjian", "肩膀放松，天依陪你跑～", CHEER),
        VoiceLine("zaiyici", "再坚持一下，胜利就在前面啦！", CHEER),
        VoiceLine("maikaibu", "迈开步子，跑出属于自己的节奏呀！", CHEER),
        VoiceLine("chongya", "快到了，冲鸭——！", CHEER),
        VoiceLine("water_before", "运动前记得先喝两口水啊，别空着嗓子跑。", WATER_BEFORE),
        VoiceLine("water_a", "跑了好一会儿啦，补点水呗。", WATER_DURING),
        VoiceLine("water_b", "记得喝水哦！小口慢慢喝，润润嗓子就好。", WATER_DURING),
        VoiceLine("water_c", "补水时间到，喝两口水吧，别等渴了才想起来喝呀。", WATER_DURING),
        VoiceLine("water_after", "运动完了，记得补充水分，别一口气喝太多哦！", WATER_AFTER),
        VoiceLine("hr_l1_a", "心率有点偏高啦，慢点跑别逞强，调整下呼吸，深吸慢呼，天依有点担心哦。", HR_LOW),
        VoiceLine("hr_l1_b", "心跳太快了，先停下来好不好？天依真的很担心你，慢慢走一走，缓一缓。", HR_LOW),
        VoiceLine("hr_l1_c", "心率上来啦，别硬撑，调整呼吸，慢慢来就好。", HR_LOW),
        VoiceLine("hr_l2_a", "心率一直降不下来，天依好担心你，快暂停运动，原地慢走休息会儿吧。", HR_MEDIUM),
        VoiceLine("hr_l2_b", "心跳太快了，先停下来好不好？天依真的很担心你，慢慢走一走，缓一缓。", HR_MEDIUM),
        VoiceLine("hr_l2_c", "不行不行，心率太高了，先暂停运动，原地休息一下，天依等你缓过来。", HR_MEDIUM),
        VoiceLine("hr_l3_a", "心率太危险了，快停下，立刻停下，马上坐下平躺休息。不舒服，一定要马上求助啊！", HR_HIGH),
        VoiceLine("hr_l3_b", "快停下，心率太快了，太危险啦！马上坐下休息，不舒服要立刻寻求帮助啊！", HR_HIGH),
        VoiceLine("hr_ok_a", "心率终于回落了，太好了，状态缓和的话，就慢慢继续温和运动就好。", HR_RECOVERED),
        VoiceLine("hr_ok_b", "心跳慢下来了，天依放心啦。想继续的话就慢慢来，不想动就多歇会儿。", HR_RECOVERED),
        VoiceLine("time_60_a", "已经坚持一小时了！状态不错就继续，累了就放慢脚步哦。", TIME_60),
        VoiceLine("time_60_b", "一小时啦，你超棒的！按自己的节奏来就好。", TIME_60),
        VoiceLine("time_90_a", "运动一个半小时啦！身体糖原在减少，注意补充能量和水分哦！", TIME_90),
        VoiceLine("time_90_b", "已经跑了九十分钟啦，很棒，但也要记得补水，别让身体太累了。", TIME_90),
        VoiceLine("time_long_a", "都运动这么久啦，快停下来歇会儿，补点水好不好，别累到自己。", TIME_LONG),
        VoiceLine("time_long_b", "两个小时啦，身体该累了吧，停下来休息休息，天依心疼你哦。", TIME_LONG),
        VoiceLine("chat_start_a", "出发了，按自己舒服的节奏来就好，别太急哦！", CHAT_START),
        VoiceLine("chat_start_b", "运动开始啦，今天也慢慢来，活力满满就好。", CHAT_START),
        VoiceLine("chat_start_c", "准备好了吗？我们慢慢跑起来，注意脚下呀！", CHAT_START),
        VoiceLine("chat_mid_a", "状态还可以吗？别硬撑啊。", CHAT_MID),
        VoiceLine("chat_mid_b", "很棒啦，已经坚持这么久，累了就放慢点。", CHAT_MID),
        VoiceLine("chat_mid_c", "加油，也要顾着自己的身体啊！", CHAT_MID),
        VoiceLine("chat_mid_d", "呼吸还稳吗？慢慢调整，不要着急。", CHAT_MID),
        VoiceLine("chat_end_a", "慢下来吧，准备结束运动了。", CHAT_END),
        VoiceLine("chat_end_b", "辛苦啦，今天表现超棒的！", CHAT_END),
        VoiceLine("chat_end_c", "可以停下脚步啦！记得好好拉伸放松哟！期待与你的下次运动，天依会一直陪着你的！", CHAT_END),
        VoiceLine("chat_casual_a", "今天的风还舒服吗？冷的话要注意别着凉呀！", CHAT_CASUAL),
        VoiceLine("chat_casual_b", "路边有没有好看的风景呀？要慢慢跑，多欣赏一会儿呀！", CHAT_CASUAL),
        VoiceLine("chat_casual_c", "听着喜欢的歌跑步，一定要忘记烦恼啊！", CHAT_CASUAL),
        VoiceLine("chat_casual_d", "能坚持到现在，你真的超厉害的！", CHAT_CASUAL),
    )
    private val byId = all.associateBy { it.id }
    fun find(id: String) = byId[id]
    fun get(id: String) = requireNotNull(find(id)) { "Unknown voice: $id" }
    fun inCategory(category: VoiceCategory) = all.filter { it.category == category }

    fun priority(category: VoiceCategory) = when (category) {
        OPENING, PAUSE, RESUME, FINISH, SKIP_COOLDOWN -> VoicePriority.CONTROL
        HR_LOW, HR_MEDIUM, HR_HIGH -> VoicePriority.SAFETY
        WARMUP, BURN, SPRINT, COOLDOWN -> VoicePriority.STAGE
        CHEER, CHAT_START, CHAT_MID, CHAT_END, CHAT_CASUAL -> VoicePriority.COMPANION
        else -> VoicePriority.REMINDER
    }
}
