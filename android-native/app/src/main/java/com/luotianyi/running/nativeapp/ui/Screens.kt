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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.text.KeyboardOptions
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.luotianyi.running.nativeapp.data.*
import com.luotianyi.running.nativeapp.R
import com.luotianyi.running.nativeapp.domain.*
import java.util.Locale
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.roundToInt

fun duration(ms:Long):String="%02d:%02d".format(ms.coerceAtLeast(0)/60000,ms.coerceAtLeast(0)/1000%60)
fun distance(m:Double)=String.format(Locale.ROOT,"%.2f km",m/1000)
fun pace(seconds:Double?):String {if(seconds==null || !seconds.isFinite() || seconds<=0)return "--";val value=seconds.roundToInt();return "%d'%02d\"".format(value/60,value%60)}
fun cadence(value:Double?)=value?.let {"${it.roundToInt()} 步/分"} ?: "--"
fun stride(value:Double?)=value?.let {String.format(Locale.ROOT,"%.2f m/步",it)} ?: "--"
@Composable fun Header(title:String,subtitle:String="") {Column(Modifier.padding(top=22.dp,bottom=4.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {Text(title,style=MaterialTheme.typography.headlineLarge,fontWeight=FontWeight.Bold);if(subtitle.isNotBlank())Text(subtitle,color=MaterialTheme.colorScheme.onSurfaceVariant)}}
@Composable fun Panel(title:String,content:@Composable ColumnScope.()->Unit) {Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(22.dp)) {Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {Text(title,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.SemiBold);content()}}}
@Composable fun Metrics(first:String,label:String,second:String,other:String) {Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {listOf(first to label,second to other).forEach {(value,title)->Card(Modifier.weight(1f),shape=RoundedCornerShape(18.dp)) {Column(Modifier.fillMaxWidth().padding(16.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(6.dp)) {Text(value,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold);Text(title,style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)}}}}}
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun HomeScreen(state:AppUiState,vm:AppViewModel,start:(RunMode)->Unit,returnToRun:()->Unit) {
    val s=state.settings
    var selectedMode by rememberSaveable(s.legacyMode) {mutableStateOf(s.legacyMode)}
    val displayedMode=if(state.busy)state.run.training.mode else selectedMode
    val lifecycleOwner=LocalLifecycleOwner.current
    val localNow by produceState(LocalDateTime.now(),lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while(isActive) {value=LocalDateTime.now();delay(30_000)}
        }
    }
    val today=localNow.toLocalDate();val zone=ZoneId.systemDefault()
    val streak=remember(state.workouts,today,zone) {RunStreaks.calculate(state.workouts,today,zone)}
    var discardRecovery by rememberSaveable {mutableStateOf(false)}
    LazyColumn(Modifier.fillMaxSize().padding(horizontal=18.dp),verticalArrangement=Arrangement.spacedBy(14.dp),contentPadding=PaddingValues(bottom=20.dp)) {
        item {HomeGreetingHeader(s.nickname,localNow.hour,streak.takeIf {state.workoutsLoaded})}
        if(state.run.training.phase in listOf(RunPhase.PREPARING,RunPhase.RUNNING,RunPhase.PAUSED))item {ActiveWorkoutCard(state,returnToRun)}
        if(state.run.recoveryError.isNotEmpty())item {Panel("恢复上次记录") {Text(state.run.recoveryError);Button(onClick=vm::retryRecovery,enabled=!state.recovering) {Text("重试恢复")};TextButton(onClick={discardRecovery=true},enabled=!state.recovering) {Text("放弃上次记录")}}}
        item {Panel("今天怎么跑？") {
            for(mode in RunMode.entries)FilterChip(selected=displayedMode==mode,onClick={
                selectedMode=mode
                if(mode!=RunMode.TIMED)vm.setLegacyMode(mode)
            },enabled=state.settingsLoaded && state.run.training.phase!=RunPhase.PREPARING && (!state.busy || (state.run.training.mode!=RunMode.TIMED && mode!=RunMode.TIMED)),modifier=Modifier.fillMaxWidth(),label={Column(Modifier.padding(vertical=6.dp)) {Text(mode.label,fontWeight=FontWeight.SemiBold);Text(mode.description,style=MaterialTheme.typography.bodySmall)}})
            if(displayedMode==RunMode.CUSTOM)IntegerSlider("固定步频 BPM",s.customBpm,60..220) {value->vm.settings {it.copy(customBpm=value)}}
            if(displayedMode==RunMode.GUIDE)StageRatios(s,vm)
            if(displayedMode==RunMode.TIMED) {
            var value by remember(s.timedMinutes) {mutableFloatStateOf(s.timedMinutes.toFloat())}
            Text("${value.roundToInt()} 分钟",color=RunOrange,style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold)
            Slider(value=value,onValueChange={value=it.roundToInt().toFloat()},onValueChangeFinished={vm.setTimedMinutes(value.roundToInt())},valueRange=5f..60f,enabled=!state.busy && state.settingsLoaded,track={sliderState->SliderDefaults.Track(sliderState=sliderState,enabled=!state.busy && state.settingsLoaded,drawStopIndicator=null)})
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {Text("5 分钟");Text("60 分钟")}
            var minutesText by remember(s.timedMinutes) {mutableStateOf(s.timedMinutes.toString())}
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                OutlinedTextField(minutesText,{minutesText=it},enabled=!state.busy,singleLine=true,label={Text("分钟（5–60）")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),modifier=Modifier.weight(1f))
                TextButton(onClick={minutesText.toIntOrNull()?.takeIf {it in 5..60}?.let(vm::setTimedMinutes) ?: vm.message("请输入 5–60 的整分钟")},enabled=!state.busy) {Text("应用")}
            }
            }
            Button(onClick={start(displayedMode)},enabled=!state.busy && !state.recovering && state.settingsLoaded && state.run.saveError.isEmpty() && state.run.recoveryError.isEmpty(),modifier=Modifier.fillMaxWidth()) {Text(if(state.busy)"本次训练正在进行" else if(displayedMode==RunMode.TIMED)"开始定时跑" else "开始跑步")}
        }}
        if(s.withWearable)item {Panel("心率") {Text("${state.hr.bpm ?: "--"} bpm",style=MaterialTheme.typography.headlineMedium,color=LuoBlue);Text(state.hr.label);Text("支持标准蓝牙心率广播，在设置页连接设备。",style=MaterialTheme.typography.bodySmall)}}
    }
    if(discardRecovery)AlertDialog(onDismissRequest={discardRecovery=false},title={Text("放弃上次未恢复的记录？")},text={Text("这会清除上次中断训练的检查点，已保存的历史不受影响。清除后无法恢复这条记录。")},confirmButton={TextButton(onClick={discardRecovery=false;vm.discardRecovery()}) {Text("确认放弃")}},dismissButton={TextButton(onClick={discardRecovery=false}) {Text("取消")}})
}
@Composable internal fun HomeGreetingHeader(nickname:String,hour:Int,streak:RunStreak?) {
    val measurer=rememberTextMeasurer()
    val titleStyle=MaterialTheme.typography.headlineMedium.copy(fontWeight=FontWeight.Bold)
    val greeting=when(hour) {
        in 5..10->"早上好"
        in 11..13->"中午好"
        in 14..17->"下午好"
        in 18..22->"晚上好"
        else->"夜深了"
    }
    Column(Modifier.fillMaxWidth().padding(top=22.dp,bottom=4.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically) {
            BoxWithConstraints(Modifier.weight(1f)) {
                val title="$greeting，$nickname"
                val available=with(LocalDensity.current) {maxWidth.roundToPx()}
                val measured=measurer.measure(AnnotatedString(title),style=titleStyle,softWrap=false,maxLines=1)
                Text(if(measured.size.width>available)"$greeting，\n$nickname" else title,style=titleStyle,maxLines=2,overflow=TextOverflow.Ellipsis)
            }
            Row(horizontalArrangement=Arrangement.spacedBy(4.dp),verticalAlignment=Alignment.CenterVertically) {
                val icon=when(streak?.iconIndex) {1->R.drawable.streak_icon1;2->R.drawable.streak_icon2;3->R.drawable.streak_icon3;4->R.drawable.streak_icon4;else->R.drawable.streak_icon0}
                Image(painterResource(icon),contentDescription=null,modifier=Modifier.size(42.dp),contentScale=ContentScale.Fit)
                Text(streak?.label ?: "连续 -- 天",color=LuoBlue,style=MaterialTheme.typography.bodyMedium.copy(fontSize=16.sp),fontWeight=FontWeight.Medium,maxLines=1)
            }
        }
        Text("今天也一起迈出每一步",color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
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
    LazyColumn(Modifier.fillMaxSize().padding(horizontal=18.dp),verticalArrangement=Arrangement.spacedBy(14.dp),contentPadding=PaddingValues(bottom=20.dp)) {
        item {Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
            TextButton(onClick=minimize) {Text("收起跑步界面")}
            Text("洛天依陪跑",color=LuoBlue,style=MaterialTheme.typography.labelLarge)
        }}
        if(t.phase==RunPhase.PREPARING)item {Panel("GPS 预热") {Text("${run.accuracy?.roundToInt()?.let {"约 $it 米"} ?: "等待信号"}，最多等待 20 秒；预热不计入运动时间。");Button(onClick={vm.runCommand("SKIP")}) {Text("跳过等待，开始训练")};TextButton(onClick={vm.runCommand("FINISH")}) {Text("取消")}}}
        item {RunHero(run.gpsMeters,t.elapsedMs,run.pace.takeIf {t.phase==RunPhase.RUNNING && run.gpsFresh})}
        if(t.totalMs>0)item {Panel(if(t.mode==RunMode.TIMED)"定时目标 ${t.totalMs/60000} 分钟" else "歌单训练进度") {Text("剩余 ${duration((t.totalMs-t.elapsedMs).coerceAtLeast(0))}",color=RunOrange,style=MaterialTheme.typography.headlineSmall);LinearProgressIndicator(progress={(t.elapsedMs.toFloat()/t.totalMs).coerceIn(0f,1f)},modifier=Modifier.fillMaxWidth());Text("本阶段剩余 ${duration(t.stageRemainingMs)}",style=MaterialTheme.typography.bodySmall)}}
        if(s.withWearable)item {HeartRateView(run.heartRate,s,t.phase==RunPhase.RUNNING)}
        item {Panel("节拍 BPM") {Text(t.bpm.toString(),style=MaterialTheme.typography.headlineMedium,color=LuoBlue);if(t.bpm!=t.targetBpm)Text("目标 ${t.targetBpm} BPM",style=MaterialTheme.typography.bodySmall)}}
        if(t.mode==RunMode.CUSTOM)item {Panel("调整固定步频") {IntegerSlider("BPM",s.customBpm,60..220) {value->vm.settings {it.copy(customBpm=value)}}}}
        item {MiniPlayer(state,vm)}
        item {Panel("运动控制") {when(t.phase) {
            RunPhase.RUNNING->Button(onClick={vm.runCommand("PAUSE")},modifier=Modifier.fillMaxWidth()) {Text("暂停运动")}
            RunPhase.PAUSED->Button(onClick={vm.runCommand("RESUME")},modifier=Modifier.fillMaxWidth()) {Text("继续运动")}
            RunPhase.IDLE,RunPhase.FINISHED->Button(onClick={start(s.legacyMode)},enabled=!run.saving && run.saveError.isEmpty(),modifier=Modifier.fillMaxWidth()) {Text("开始跑步")}
            else->{}
        };if(t.phase in listOf(RunPhase.RUNNING,RunPhase.PAUSED))OutlinedButton(onClick={confirmEnd=true;vm.runCommand("END_HINT")},modifier=Modifier.fillMaxWidth()) {Text("结束并保存记录")};if(run.saveError.isNotEmpty()) {Text(run.saveError,color=MaterialTheme.colorScheme.error);Button(onClick=vm::retrySave) {Text("重试保存记录")}}}}
        item {Text(if(t.stageIndex==0)"先活动关节，放松肩膀，逐步增加强度。" else if(t.stageIndex==3)"慢走调整呼吸，结束后做轻柔拉伸。" else "保持自然呼吸，身体不舒服时停下来。",style=MaterialTheme.typography.bodySmall)}
    }
    if(confirmEnd)AlertDialog(onDismissRequest={confirmEnd=false},title={Text("结束本次训练？")},text={Text("结束前建议慢走、调整呼吸。确认后将保存距离、步频、步幅和心率记录。")},confirmButton={TextButton(onClick={confirmEnd=false;vm.runCommand("FINISH")}) {Text("结束并保存")}},dismissButton={TextButton(onClick={confirmEnd=false}) {Text("继续训练")}})
}
@Composable private fun RunHero(meters:Double,elapsedMs:Long,livePace:Double?) {
    Surface(Modifier.fillMaxWidth().testTag("run-hero"),shape=RoundedCornerShape(26.dp),color=AppSurface) {
        Column(Modifier.background(Brush.verticalGradient(listOf(LuoBlue.copy(alpha=.16f),Color.Transparent))).padding(20.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(6.dp)) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val distanceValue=String.format(Locale.ROOT,"%.2f",meters/1000)
                val paceValue=pace(livePace)
                val fontScale=androidx.compose.ui.platform.LocalDensity.current.fontScale
                // Fit both values together so distance and pace always use the same font size.
                val sharedSize=minOf(48f,(maxWidth.value-12f)/2/(maxOf(distanceValue.length,paceValue.length)*.64f*fontScale)).coerceAtLeast(16f)
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).testTag("run-distance"),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(6.dp)) {
                        Text("距离",color=MaterialTheme.colorScheme.onSurfaceVariant)
                        HeroValue(distanceValue,sharedSize)
                        Text("km",color=LuoBlue)
                    }
                    Column(Modifier.weight(1f).testTag("run-live-pace"),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(6.dp)) {
                        Text("实时配速",color=MaterialTheme.colorScheme.onSurfaceVariant)
                        HeroValue(paceValue,sharedSize)
                        Text("/km",color=LuoBlue)
                    }
                }
            }
            HorizontalDivider(Modifier.padding(vertical=10.dp),color=LuoBlue.copy(alpha=.2f))
            Text("运动时间",color=MaterialTheme.colorScheme.onSurfaceVariant)
            Text(duration(elapsedMs),fontSize=42.sp,fontWeight=FontWeight.Bold,maxLines=1)
        }
    }
}
@Composable private fun HeroValue(value:String,size:Float) {
    Box(Modifier.fillMaxWidth().height(64.dp),contentAlignment=Alignment.Center) {
        Text(value,fontSize=size.sp,fontWeight=FontWeight.Bold,color=LuoBlue,maxLines=1)
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
