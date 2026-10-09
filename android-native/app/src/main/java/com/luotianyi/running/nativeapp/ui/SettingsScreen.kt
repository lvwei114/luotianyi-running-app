package com.luotianyi.running.nativeapp.ui
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import com.luotianyi.running.nativeapp.data.*
import kotlin.math.roundToInt

@Composable fun SettingsScreen(state:AppUiState,vm:AppViewModel,scan:()->Unit,bluetoothSettings:()->Unit) {
    val s=state.settings;var profile by rememberSaveable {mutableStateOf(false)};var backgroundDialog by rememberSaveable {mutableStateOf(false)}
    val avatarPicker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {uri->if(uri!=null)vm.avatar(uri)}
    val imagePicker=rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) {uris->vm.addBackgrounds(uris)}
    LazyColumn(Modifier.fillMaxSize().padding(horizontal=18.dp),verticalArrangement=Arrangement.spacedBy(14.dp),contentPadding=PaddingValues(bottom=20.dp)) {
        item {Header("设置")}
        item {Panel("个人信息") {
            val image=localPicture(s.avatar);if(image!=null)Image(image,"头像",Modifier.size(72.dp).clip(CircleShape),contentScale=ContentScale.Crop)
            Text("${s.nickname} · ${s.age} 岁 · ${s.height} cm · ${s.weight} kg")
            Row {TextButton(onClick={profile=true},enabled=!state.busy) {Text("编辑资料")};TextButton(onClick={avatarPicker.launch(arrayOf("image/*"))}) {Text("更换头像")}}
        }}
        item {Panel("画面效果") {DisplayTier.entries.forEach {tier->FilterChip(selected=s.displayTier==tier,onClick={vm.setDisplayTier(tier)},label={Text("${tier.label} · ${tier.description}")})};Button(onClick={backgroundDialog=true},modifier=Modifier.fillMaxWidth()) {Text("背景图库")}}}
        item {Panel("心率设备") {
            ToggleRow("有心率穿戴设备",s.withWearable,!state.busy) {enabled->vm.settings {it.copy(withWearable=enabled)};if(!enabled)vm.disconnect()}
            Text(state.hr.label)
            if(state.hr.error.isNotEmpty())Text(state.hr.error,color=MaterialTheme.colorScheme.error)
            if(s.withWearable) {
                Row {Button(onClick=scan,enabled=!state.hr.scanning) {Text(if(state.hr.scanning)"正在扫描…" else "扫描心率设备")};TextButton(onClick=vm::disconnect) {Text("断开")}}
                state.hr.devices.forEach {device->OutlinedButton(onClick={vm.connect(device.address)},modifier=Modifier.fillMaxWidth()) {Text("${device.name} · ${device.address}")}}
                TextButton(onClick=bluetoothSettings) {Text("打开系统蓝牙设置")}
                Text("仅支持标准 BLE 心率广播。若连接失败，请在手环开启心率广播并断开其他 App。",style=MaterialTheme.typography.bodySmall)
            }
        }}
        item {Panel("心率区间与提醒") {
            listOf("tanaka" to "Tanaka：208 − 0.7 × 年龄","classic" to "经典：220 − 年龄","custom" to "自定义最大心率").forEach {(key,label)->FilterChip(selected=s.hrFormula==key,onClick={vm.settings {it.copy(hrFormula=key)}},label={Text(label)})}
            if(s.hrFormula=="custom")NumberSetting("最大心率",s.customMaxHr,100..230) {value->vm.settings {it.copy(customMaxHr=value)}}
            NumberSetting("静息心率（0 表示不启用）",s.restingHr,0..120) {value->if(value==0 || value>=35)vm.settings {it.copy(restingHr=value)} else vm.message("静息心率需为 35–120，或填 0 关闭")}
            Text("最大心率 ${s.maxHr().roundToInt()}；${if(s.restingHr>0)"已启用心率储备法" else "按最大心率百分比划分区间"}",style=MaterialTheme.typography.bodySmall)
            ToggleRow("启用心率提醒与持续过高自动暂停",s.safetyEnabled) {v->vm.settings {it.copy(safetyEnabled=v)}}
            Text("90% 及以上提示放慢；持续 15 秒达到 100% 时暂停，需手动恢复。只使用最近 10 秒的有效心率，提醒不代替医疗判断。",style=MaterialTheme.typography.bodySmall)
        }}
        item {Panel("运动提醒") {
            Text("随机鼓励语频率")
            ChoiceRow(listOf("off" to "关闭","sparse" to "稀疏","standard" to "标准","dense" to "密集"),s.chatFrequency) {value->vm.settings {it.copy(chatFrequency=value)}}
            Text("补水提醒")
            ChoiceRow(listOf("0" to "关闭","15" to "15分","20" to "20分","30" to "30分"),s.hydrationMinutes.toString()) {value->vm.settings {it.copy(hydrationMinutes=value.toInt())}}
            ToggleRow("高温模式：补水间隔缩短 25%",s.highTemp) {v->vm.settings {it.copy(highTemp=v)}}
        }}
        item {Panel("音量与节拍器") {
            VolumeSlider("总音量",s.masterVolume) {v->vm.settings {it.copy(masterVolume=v)}}
            VolumeSlider("音乐",s.musicVolume) {v->vm.settings {it.copy(musicVolume=v)}}
            VolumeSlider("洛天依声音",s.voiceVolume) {v->vm.settings {it.copy(voiceVolume=v)}}
            VolumeSlider("节拍器",s.beatVolume) {v->vm.settings {it.copy(beatVolume=v)}}
            ChoiceRow(listOf("wood" to "木块","click" to "电子"),s.beatTone,vm::previewTone)
            Text("空闲时选择音色会试听两拍；训练中只更换音色。",style=MaterialTheme.typography.bodySmall)
        }}
        item {Panel("健康小知识") {
            listOf("最大心率为什么有不同公式" to "最大心率公式是人群估计值，个人可能有较大差异。Tanaka 公式为 208 − 0.7 × 年龄。参考：Tanaka et al., 2001。","训练后怎样安排恢复" to "训练后补充水分和正常饮食，根据疲劳程度安排休息，高强度训练后给身体充分恢复时间。参考：ACSM 运动测试与处方指南。","先热身，再逐步提高强度" to "动态活动关节与渐进热身，避免突然加速。参考：ACSM 运动测试与处方指南。","结束后慢走与拉伸" to "逐渐降低强度，慢走调整呼吸，再进行轻柔静态拉伸。参考：ACSM 运动测试与处方指南。","心率区间怎么看" to "60–70% 燃脂，70–80% 有氧，80–90% 无氧；最大心率计算是估计值，应结合身体感受。","补水与炎热天气" to "根据出汗、气温和个人感受补水；提醒只是辅助，不应等到提醒才处理不适。").forEach {(title,text)->var expanded by rememberSaveable(title) {mutableStateOf(false)};TextButton(onClick={expanded=!expanded}) {Text(title)};if(expanded)Text(text,style=MaterialTheme.typography.bodySmall)}
        }}
        item {Panel("关于洛天依陪跑") {
            Text("原生版作者：绿卫",style=MaterialTheme.typography.bodyMedium)
            Text("版本 0.1.6 · 原生测试版",color=RunOrange)
            Text("原项目作者：Cory\n哔哩哔哩：本人内心十分开朗\n代码协议：MIT License\nGPT-SoVITS 模型：只想干饭的麦克斯韦妖\n洛天依声音与形象版权：上海禾念信息科技有限公司",style=MaterialTheme.typography.bodySmall)
            Text("项目：github.com/w8888-hh/luotianyi-running-app\n语音模型：github.com/xdedmmc2/GPT-SoVITS-Luo-Tianyi",style=MaterialTheme.typography.bodySmall)
        }}
    }
    if(profile)ProfileDialog(s,vm,{profile=false})
    if(backgroundDialog)FullDialog("背景图库",{backgroundDialog=false}) {
        state.backgrounds.chunked(2).forEach {row->Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {row.forEach {entry->Card(onClick={vm.settings {it.copy(background=entry.id)}},modifier=Modifier.weight(1f)) {Column(Modifier.padding(8.dp)) {val image=localPicture(entry.path);if(image!=null)Image(image,null,Modifier.fillMaxWidth().height(120.dp),contentScale=ContentScale.Crop) else Box(Modifier.fillMaxWidth().height(120.dp));Text((if(s.background==entry.id)"✓ " else "")+entry.name)}}}}}
        Button(onClick={imagePicker.launch(arrayOf("image/*"))}) {Text("添加自选图片（可多选）")}
        state.backgrounds.find {it.id==s.background && it.id.startsWith("custom:")}?.let {entry->TextButton(onClick={vm.deleteBackground(entry)}) {Text("删除当前自选图",color=MaterialTheme.colorScheme.error)}}
        Text("图片会缩小保存到本机，内置图来自原项目图库。",style=MaterialTheme.typography.bodySmall)
    }
}
@Composable private fun ProfileDialog(s:AppSettings,vm:AppViewModel,close:()->Unit) {
    var name by rememberSaveable {mutableStateOf(s.nickname)};var age by rememberSaveable {mutableStateOf(s.age.toString())};var height by rememberSaveable {mutableStateOf(s.height.toString())};var weight by rememberSaveable {mutableStateOf(s.weight.toString())};var gender by rememberSaveable {mutableStateOf(s.gender)}
    FullDialog("个人资料",close) {
        OutlinedTextField(name,{name=it},label={Text("昵称")},singleLine=true,modifier=Modifier.fillMaxWidth())
        listOf("年龄" to (age to {v:String->age=v}),"身高 cm" to (height to {v:String->height=v}),"体重 kg" to (weight to {v:String->weight=v})).forEach {(label,pair)->OutlinedTextField(pair.first,pair.second,label={Text(label)},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),modifier=Modifier.fillMaxWidth())}
        ChoiceRow(listOf("female" to "女","male" to "男"),gender) {gender=it}
        Button(onClick={val a=age.toIntOrNull();val h=height.toIntOrNull();val w=weight.toIntOrNull();if(name.isBlank() || a==null || a !in 4..100 || h==null || h !in 80..230 || w==null || w !in 20..250)vm.message("请填写有效昵称、年龄 4–100、身高 80–230 和体重 20–250") else {vm.settings {it.copy(nickname=name.trim(),age=a,height=h,weight=w,gender=gender)};close()}},modifier=Modifier.fillMaxWidth()) {Text("保存资料")}
    }
}
@Composable fun ToggleRow(label:String,value:Boolean,enabled:Boolean=true,change:(Boolean)->Unit) {Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {Text(label,modifier=Modifier.weight(1f).padding(top=12.dp));Switch(value,change,enabled=enabled)}}
@Composable fun ChoiceRow(choices:List<Pair<String,String>>,value:String,change:(String)->Unit) {Column {choices.chunked(3).forEach {row->Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {row.forEach {(key,label)->FilterChip(selected=value==key,onClick={change(key)},label={Text(label)})}}}}}
@Composable private fun VolumeSlider(label:String,value:Int,change:(Int)->Unit) {Column {IntegerSlider("$label %",value,0..100,change)}}
@Composable private fun NumberSetting(label:String,value:Int,range:IntRange,change:(Int)->Unit) {var text by remember(value) {mutableStateOf(value.toString())};Row(Modifier.fillMaxWidth()) {OutlinedTextField(text,{text=it},label={Text(label)},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),singleLine=true,modifier=Modifier.weight(1f));TextButton(onClick={text.toIntOrNull()?.takeIf {it in range}?.let(change)}) {Text("应用")}}}
