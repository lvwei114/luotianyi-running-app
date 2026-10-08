package com.luotianyi.running.nativeapp.ui
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.text.KeyboardOptions
import com.luotianyi.running.nativeapp.data.*
import com.luotianyi.running.nativeapp.domain.*
import java.util.Locale
import kotlin.math.roundToInt

fun duration(ms:Long):String="%02d:%02d".format(ms.coerceAtLeast(0)/60000,ms.coerceAtLeast(0)/1000%60)
fun distance(m:Double)=String.format(Locale.ROOT,"%.2f km",m/1000)
fun pace(seconds:Double?):String {if(seconds==null || !seconds.isFinite() || seconds<=0)return "--";val value=seconds.roundToInt();return "%d'%02d\"".format(value/60,value%60)}
fun cadence(value:Double?)=value?.let {"${it.roundToInt()} 步/分"} ?: "--"
fun stride(value:Double?)=value?.let {String.format(Locale.ROOT,"%.2f m/步",it)} ?: "--"
@Composable fun Header(title:String,subtitle:String="") {Column(Modifier.padding(top=22.dp,bottom=4.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {Text(title,style=MaterialTheme.typography.headlineLarge,fontWeight=FontWeight.Bold);if(subtitle.isNotBlank())Text(subtitle,color=MaterialTheme.colorScheme.onSurfaceVariant)}}
@Composable fun Panel(title:String,content:@Composable ColumnScope.()->Unit) {Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(22.dp)) {Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {Text(title,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.SemiBold);content()}}}
@Composable fun Metrics(first:String,label:String,second:String,other:String) {Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {listOf(first to label,second to other).forEach {(value,title)->Card(Modifier.weight(1f),shape=RoundedCornerShape(18.dp)) {Column(Modifier.fillMaxWidth().padding(16.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(6.dp)) {Text(value,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold);Text(title,style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)}}}}}
@Composable fun HomeScreen(state:AppUiState,vm:AppViewModel,start:(RunMode)->Unit,history:()->Unit,returnToRun:()->Unit) {
    val s=state.settings;val stats=HistoryStats.week(state.workouts,state.today)
    var discardRecovery by rememberSaveable {mutableStateOf(false)}
    LazyColumn(Modifier.fillMaxSize().padding(horizontal=18.dp),verticalArrangement=Arrangement.spacedBy(14.dp),contentPadding=PaddingValues(bottom=20.dp)) {
        item {Header("洛天依陪跑","${s.nickname}，今天也一起迈出每一步")}
        if(state.run.training.phase in listOf(RunPhase.PREPARING,RunPhase.RUNNING,RunPhase.PAUSED))item {ActiveWorkoutCard(state,returnToRun)}
        if(state.run.recoveryError.isNotEmpty())item {Panel("恢复上次记录") {Text(state.run.recoveryError);Button(onClick=vm::retryRecovery,enabled=!state.recovering) {Text("重试恢复")};TextButton(onClick={discardRecovery=true},enabled=!state.recovering) {Text("放弃上次记录")}}}
        item {Panel("今天怎么跑？") {
            for(mode in listOf(RunMode.GUIDE,RunMode.FOLLOW,RunMode.CUSTOM))FilterChip(selected=(if(state.busy)state.run.training.mode else s.legacyMode)==mode,onClick={vm.setLegacyMode(mode)},enabled=state.settingsLoaded && state.run.training.phase!=RunPhase.PREPARING && (!state.busy || state.run.training.mode!=RunMode.TIMED),modifier=Modifier.fillMaxWidth(),label={Column(Modifier.padding(vertical=6.dp)) {Text(mode.label,fontWeight=FontWeight.SemiBold);Text(mode.description,style=MaterialTheme.typography.bodySmall)}})
            if(s.legacyMode==RunMode.CUSTOM)IntegerSlider("固定步频 BPM",s.customBpm,60..220) {value->vm.settings {it.copy(customBpm=value)}}
            if(s.legacyMode==RunMode.GUIDE)StageRatios(s,vm)
            Button(onClick={start(s.legacyMode)},enabled=!state.busy && !state.recovering && state.settingsLoaded && state.run.saveError.isEmpty() && state.run.recoveryError.isEmpty(),modifier=Modifier.fillMaxWidth()) {Text(if(state.busy)"本次训练正在进行" else "开始跑步")}
        }}
        item {Panel("独立定时跑") {
            var value by remember(s.timedMinutes) {mutableFloatStateOf(s.timedMinutes.toFloat())}
            Text("${value.roundToInt()} 分钟",color=RunOrange,style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold)
            Slider(value=value,onValueChange={value=it.roundToInt().toFloat()},onValueChangeFinished={vm.setTimedMinutes(value.roundToInt())},valueRange=5f..60f,steps=54,enabled=!state.busy && state.settingsLoaded)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {Text("5 分钟");Text("60 分钟")}
            var minutesText by remember(s.timedMinutes) {mutableStateOf(s.timedMinutes.toString())}
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                OutlinedTextField(minutesText,{minutesText=it},enabled=!state.busy,singleLine=true,label={Text("分钟（5–60）")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),modifier=Modifier.weight(1f))
                TextButton(onClick={minutesText.toIntOrNull()?.takeIf {it in 5..60}?.let(vm::setTimedMinutes) ?: vm.message("请输入 5–60 的整分钟")},enabled=!state.busy) {Text("应用")}
            }
            Text("暂停不计时，到点结束。",style=MaterialTheme.typography.bodySmall)
            Button(onClick={start(RunMode.TIMED)},enabled=!state.busy && !state.recovering && state.run.saveError.isEmpty() && state.run.recoveryError.isEmpty(),modifier=Modifier.fillMaxWidth()) {Text("开始定时跑")}
        }}
        item {Panel("本周记录 · 连续 ${stats.streak} 天") {
            Text("${distance(stats.meters)} · ${stats.runs} 次 · ${duration(stats.elapsedMs)}",color=LuoBlue)
            Row(Modifier.fillMaxWidth().height(82.dp),horizontalArrangement=Arrangement.SpaceEvenly,verticalAlignment=Alignment.Bottom) {stats.daily.forEachIndexed {i,value->Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(6.dp)) {val fraction=if(stats.daily.maxOrNull()==0L)0f else value.toFloat()/stats.daily.maxOrNull()!!;Box(Modifier.width(20.dp).height((4+fraction*50).dp).background(if(value>0)LuoBlue else AppSurface, RoundedCornerShape(4.dp)));Text(listOf("一","二","三","四","五","六","日")[i],style=MaterialTheme.typography.labelSmall)}}}
            Text("平均配速 ${pace(stats.pace)}\n平均步频 ${cadence(stats.cadence)} · 步幅 ${stride(stats.stride)}",style=MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick=history,modifier=Modifier.fillMaxWidth()) {Text("跑步历史")}
        }}
        if(s.withWearable)item {Panel("心率") {Text("${state.hr.bpm ?: "--"} bpm",style=MaterialTheme.typography.headlineMedium,color=LuoBlue);Text(state.hr.label);Text("支持标准蓝牙心率广播，在设置页连接设备。",style=MaterialTheme.typography.bodySmall)}}
    }
    if(discardRecovery)AlertDialog(onDismissRequest={discardRecovery=false},title={Text("放弃上次未恢复的记录？")},text={Text("这会清除上次中断训练的检查点，已保存的历史不受影响。清除后无法恢复这条记录。")},confirmButton={TextButton(onClick={discardRecovery=false;vm.discardRecovery()}) {Text("确认放弃")}},dismissButton={TextButton(onClick={discardRecovery=false}) {Text("取消")}})
}
@Composable fun StageRatios(s:AppSettings,vm:AppViewModel) {
    var open by rememberSaveable {mutableStateOf(false)}
    TextButton(onClick={open=!open}) {Text("调整四阶段占比")}
    if(open)for(i in 0..3) {
        var value by remember(s.ratios) {mutableFloatStateOf((s.ratios[i]/s.ratios.sum()*100).toFloat().coerceIn(1f,90f))}
        Text("${listOf("热身","燃脂","冲刺","放松")[i]} ${value.roundToInt()}%",style=MaterialTheme.typography.bodySmall)
        Slider(value=value,onValueChange={value=it},onValueChangeFinished={vm.settings {current->val r=current.ratios.map {it/current.ratios.sum()*100}.toMutableList();val otherTotal=100-r[i];for(j in 0..3)r[j]=if(j==i)value.toDouble() else if(otherTotal>0)r[j]*(100-value)/otherTotal else (100-value)/3.0;current.copy(ratios=r)}},valueRange=1f..90f)
    }
}
@Composable fun IntegerSlider(label:String,value:Int,range:IntRange,change:(Int)->Unit) {
    var local by remember(value) {mutableFloatStateOf(value.toFloat())}
    Text("$label · ${local.roundToInt()}")
    Slider(value=local,onValueChange={local=it.roundToInt().toFloat()},onValueChangeFinished={change(local.roundToInt())},valueRange=range.first.toFloat()..range.last.toFloat(),steps=(range.last-range.first-1).coerceAtLeast(0))
}
@Composable fun ActiveWorkoutCard(state:AppUiState,open:()->Unit) {
    Panel(if(state.run.training.phase==RunPhase.PAUSED)"本次训练已暂停" else "本次训练正在进行") {
        Text("${distance(state.run.gpsMeters)} · ${duration(state.run.training.elapsedMs)}",color=LuoBlue)
        Button(onClick=open,modifier=Modifier.fillMaxWidth()) {Text("返回跑步界面")}
    }
}
@Composable fun RunScreen(state:AppUiState,vm:AppViewModel,start:(RunMode)->Unit,minimize:()->Unit) {
    val run=state.run;val t=run.training;val s=state.settings
    var confirmEnd by rememberSaveable {mutableStateOf(false)}
    val metrics=WorkoutMetrics.measured(t.elapsedMs,run.steps,GpsRecordChannel(run.gpsMeters,run.gpsStepMeters,run.gpsSteps))
    LazyColumn(Modifier.fillMaxSize().padding(horizontal=18.dp),verticalArrangement=Arrangement.spacedBy(14.dp),contentPadding=PaddingValues(bottom=20.dp)) {
        item {Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
            TextButton(onClick=minimize) {Text("收起跑步界面")}
            Text("洛天依陪跑",color=LuoBlue,style=MaterialTheme.typography.labelLarge)
        }}
        item {Spacer(Modifier.height(48.dp))}
        item {Header(t.mode.label,when(t.phase) {RunPhase.PREPARING->"正在预热 GPS";RunPhase.RUNNING->"${t.stageName} · ${t.bpm} BPM";RunPhase.PAUSED->"已暂停";RunPhase.FINISHED->"训练完成";else->"准备好就出发"})}
        if(t.phase==RunPhase.PREPARING)item {Panel("GPS 预热") {Text("${run.accuracy?.roundToInt()?.let {"约 $it 米"} ?: "等待信号"}，最多等待 20 秒；预热不计入运动时间。");Button(onClick={vm.runCommand("SKIP")}) {Text("跳过等待，开始训练")};TextButton(onClick={vm.runCommand("FINISH")}) {Text("取消")}}}
        item {RunHero(run.gpsMeters,t.elapsedMs)}
        if(t.totalMs>0)item {Panel(if(t.mode==RunMode.TIMED)"定时目标 ${t.totalMs/60000} 分钟" else "歌单训练进度") {Text("剩余 ${duration((t.totalMs-t.elapsedMs).coerceAtLeast(0))}",color=RunOrange,style=MaterialTheme.typography.headlineSmall);LinearProgressIndicator(progress={(t.elapsedMs.toFloat()/t.totalMs).coerceIn(0f,1f)},modifier=Modifier.fillMaxWidth());Text("本阶段剩余 ${duration(t.stageRemainingMs)}",style=MaterialTheme.typography.bodySmall)}}
        if(s.withWearable)item {HeartRateView(run.heartRate,s,t.phase==RunPhase.RUNNING)}
        item {Metrics(pace(run.pace ?: metrics.averagePaceSecondsPerKm),"配速 /km",t.bpm.toString(),if(t.bpm!=t.targetBpm)"BPM → ${t.targetBpm}" else "节拍 BPM")}
        item {Metrics(run.steps.toString(),"总步数",cadence(metrics.averageCadence),"平均步频")}
        item {Panel("定位与步幅") {Text(if(run.gpsFresh)"GPS 良好 · 约 ${run.accuracy?.roundToInt()} 米" else "等待有效 GPS 信号");Text("步幅 ${stride(metrics.averageStrideMeters)}");Text("记录距离只使用 GPS。${if(run.liveEstimatedMeters>0)"缺失路段步幅估算 ${distance(run.liveEstimatedMeters)}，仅供参考，不记入记录。" else "定位缺失路段不补算。"}",style=MaterialTheme.typography.bodySmall);if(run.status.isNotEmpty())Text(run.status,color=RunOrange)}}
        if(t.mode==RunMode.CUSTOM)item {Panel("调整固定步频") {IntegerSlider("BPM",s.customBpm,60..220) {value->vm.settings {it.copy(customBpm=value)}}}}
        item {MiniPlayer(state,vm)}
        item {Panel("运动控制") {when(t.phase) {
            RunPhase.RUNNING->Button(onClick={vm.runCommand("PAUSE")},modifier=Modifier.fillMaxWidth()) {Text("暂停运动")}
            RunPhase.PAUSED->Button(onClick={vm.runCommand("RESUME")},modifier=Modifier.fillMaxWidth()) {Text("继续运动")}
            RunPhase.IDLE,RunPhase.FINISHED->Button(onClick={start(s.legacyMode)},enabled=!run.saving && run.saveError.isEmpty(),modifier=Modifier.fillMaxWidth()) {Text("开始跑步")}
            else->{}
        };if(t.phase in listOf(RunPhase.RUNNING,RunPhase.PAUSED))OutlinedButton(onClick={confirmEnd=true},modifier=Modifier.fillMaxWidth()) {Text("结束并保存记录")};if(run.saveError.isNotEmpty()) {Text(run.saveError,color=MaterialTheme.colorScheme.error);Button(onClick=vm::retrySave) {Text("重试保存记录")}}}}
        item {Text(if(t.stageIndex==0)"先活动关节，放松肩膀，逐步增加强度。" else if(t.stageIndex==3)"慢走调整呼吸，结束后做轻柔拉伸。" else "保持自然呼吸，身体不舒服时停下来。",style=MaterialTheme.typography.bodySmall)}
    }
    if(confirmEnd)AlertDialog(onDismissRequest={confirmEnd=false},title={Text("结束本次训练？")},text={Text("将保存距离、步频、步幅和心率记录。")},confirmButton={TextButton(onClick={confirmEnd=false;vm.runCommand("FINISH")}) {Text("结束并保存")}},dismissButton={TextButton(onClick={confirmEnd=false}) {Text("继续训练")}})
}
@Composable private fun RunHero(meters:Double,elapsedMs:Long) {
    Surface(Modifier.fillMaxWidth().testTag("run-hero"),shape=RoundedCornerShape(26.dp),color=AppSurface) {
        Column(Modifier.background(Brush.verticalGradient(listOf(LuoBlue.copy(alpha=.16f),Color.Transparent))).padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(6.dp)) {
            Text("距离",color=MaterialTheme.colorScheme.onSurfaceVariant)
            Text(String.format(Locale.ROOT,"%.2f",meters/1000),fontSize=56.sp,fontWeight=FontWeight.Bold,color=LuoBlue,maxLines=1)
            Text("km",color=LuoBlue)
            HorizontalDivider(Modifier.padding(vertical=10.dp),color=LuoBlue.copy(alpha=.2f))
            Text("运动时间",color=MaterialTheme.colorScheme.onSurfaceVariant)
            Text(duration(elapsedMs),fontSize=42.sp,fontWeight=FontWeight.Bold,maxLines=1)
        }
    }
}
@Composable fun HeartRateView(hr:Int?,s:AppSettings,running:Boolean) {
    val percent=hr?.let(s::hrPercent);val color=when {percent==null->Color.Gray;percent>=90->Color(0xFFFB7185);percent>=80->RunOrange;percent>=70->LuoBlue;else->Color(0xFF7BE495)}
    val zone=when {percent==null->"等待心率";percent>=90->"极限";percent>=80->"无氧";percent>=70->"有氧";percent>=60->"燃脂";else->"热身"}
    Panel("心率 · $zone") {
        if(s.displayTier!=DisplayTier.LOW) {
            val alpha=pulseAlpha(s.displayTier==DisplayTier.HIGH && running)
            Box(Modifier.fillMaxWidth().height(142.dp),contentAlignment=Alignment.Center) {
                Canvas(Modifier.size(130.dp)) {drawArc(Color.Gray.copy(alpha=.18f),-90f,360f,false,style=Stroke(12.dp.toPx()));drawArc(color.copy(alpha=alpha),-90f,((percent ?: 0.0)/100*360).toFloat().coerceIn(0f,360f),false,style=Stroke(12.dp.toPx()))}
                Column(horizontalAlignment=Alignment.CenterHorizontally) {Text(hr?.toString() ?: "--",style=MaterialTheme.typography.headlineLarge,fontWeight=FontWeight.Bold,color=color);Text("bpm")}
            }
        } else {Text("${hr ?: "--"} bpm",style=MaterialTheme.typography.headlineLarge,color=color);LinearProgressIndicator(progress={((percent ?: 0.0)/100).toFloat().coerceIn(0f,1f)},color=color,modifier=Modifier.fillMaxWidth())}
        Text("${percent?.roundToInt()?.let {"$it%"} ?: "--"} · 最大心率 ${s.maxHr().roundToInt()}${if(s.restingHr>0)" · 心率储备法" else ""}",style=MaterialTheme.typography.bodySmall)
    }
}
@Composable private fun pulseAlpha(enabled:Boolean):Float {
    if(!enabled)return 1f
    val transition=rememberInfiniteTransition(label="hr")
    val alpha by transition.animateFloat(.94f,1f,infiniteRepeatable(tween(1000),RepeatMode.Reverse),label="pulse")
    return alpha
}
