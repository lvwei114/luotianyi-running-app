package com.luotianyi.running.nativeapp.ui
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.luotianyi.running.nativeapp.data.*
import com.luotianyi.running.nativeapp.domain.*
import java.time.*
import java.time.format.DateTimeFormatter
fun dateTime(timestamp:Long):String=Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
@Composable fun HistoryScreen(state:AppUiState,vm:AppViewModel,returnToRun:()->Unit) {
    val week=HistoryStats.week(state.workouts,state.today)
    val total=HistoryStats.summary(state.workouts)
    LazyColumn(Modifier.fillMaxSize().padding(horizontal=18.dp),verticalArrangement=Arrangement.spacedBy(14.dp),contentPadding=PaddingValues(bottom=20.dp)) {
        item {Header("跑步记录与分析","每一步，都留下自己的进步")}
        if(state.run.training.phase in listOf(RunPhase.PREPARING,RunPhase.RUNNING,RunPhase.PAUSED))item {ActiveWorkoutCard(state,returnToRun)}
        item {Panel("累计 ${total.runs} 次训练") {
            Metrics(distance(total.meters),"距离",duration(total.elapsedMs),"运动时间")
            Text("平均配速 ${pace(total.pace)} /km",color=LuoBlue)
            Text("平均步频 ${cadence(total.cadence)} · 步幅 ${stride(total.stride)}",style=MaterialTheme.typography.bodySmall)
        }}
        item {Panel("本周分析 · 连续 ${week.streak} 天") {
            Text("${week.runs} 次 · ${distance(week.meters)} · ${duration(week.elapsedMs)}",color=LuoBlue)
            Text("平均配速 ${pace(week.pace)} /km\n平均步频 ${cadence(week.cadence)} · 步幅 ${stride(week.stride)}",style=MaterialTheme.typography.bodySmall)
            Text("每日运动时间（分钟）",style=MaterialTheme.typography.labelLarge)
            Row(Modifier.fillMaxWidth().height(116.dp),horizontalArrangement=Arrangement.SpaceEvenly,verticalAlignment=Alignment.Bottom) {
                val max=week.daily.maxOrNull()?.coerceAtLeast(1) ?: 1L
                week.daily.forEachIndexed {index,ms->Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    Text((ms/60000).toString(),style=MaterialTheme.typography.labelSmall)
                    Box(Modifier.width(20.dp).height((4+ms.toFloat()/max*64).dp).background(if(ms>0)LuoBlue else MaterialTheme.colorScheme.outline,RoundedCornerShape(4.dp)))
                    Text(listOf("一","二","三","四","五","六","日")[index],style=MaterialTheme.typography.labelSmall)
                }}
            }
        }}
        item {Text("训练记录",style=MaterialTheme.typography.titleLarge)}
        item {Text("仅记录 GPS 测得的距离；定位缺失路段不补算。",style=MaterialTheme.typography.bodySmall)}
        if(!state.workoutsLoaded)item {Text("正在加载记录…")}
        else if(state.workouts.isEmpty())item {Text("还没有跑步记录，完成训练后会自动保存。")}
        items(state.workouts,key={it.id}) {w->Card(onClick={vm.detail(w)},modifier=Modifier.fillMaxWidth()) {Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(7.dp)) {Text("${RunMode.fromStored(w.mode).label} · ${distance(w.gpsMeters)}",style=MaterialTheme.typography.titleMedium);Text(dateTime(w.startedAt));val m=w.metrics();Text("${duration(w.elapsedMillis)} · 配速 ${pace(m.averagePaceSecondsPerKm)} · ${cadence(m.averageCadence)}",style=MaterialTheme.typography.bodySmall)}}}
    }
}
@Composable fun WorkoutDetailDialog(detail:WorkoutDetail,vm:AppViewModel,close:()->Unit,deletable:Boolean,saving:Boolean=false,saveError:String="") {
    val w=detail.record;val m=w.metrics();val hr=detail.samples
    var confirmDelete by rememberSaveable {mutableStateOf(false)}
    val appState by vm.state.collectAsState()
    FullDialog(if(deletable)RunMode.fromStored(w.mode).label else "本次训练完成",close,canClose=!saving) {
        Text(dateTime(w.startedAt));if(w.timedMinutes!=null)Text("定时目标 ${w.timedMinutes} 分钟")
        Metrics(distance(w.gpsMeters),"距离",duration(w.elapsedMillis),"运动时间")
        Metrics(pace(m.averagePaceSecondsPerKm),"平均配速 /km",w.totalSteps.toString(),"总步数")
        Metrics(cadence(m.averageCadence),"平均步频",stride(m.averageStrideMeters),"步幅")
        Metrics(if(hr.isEmpty())"--" else "${hr.map {it.bpm}.average().toInt()} bpm","平均心率",hr.maxOfOrNull {it.bpm}?.let {"$it bpm"} ?: "--","最高心率")
        Text("距离仅使用 GPS；平均步频按总步数和有效运动时间计算，平均步幅仅使用 GPS 有效路段及对应步数。",style=MaterialTheme.typography.bodySmall)
        Panel("心率变化") {HeartRateChart(hr)}
        Panel("恢复建议") {Text(if(w.elapsedMillis>=45*60_000 || (hr.map {it.bpm}.average().takeIf {it.isFinite()} ?: 0.0)>160)"逐渐降低强度，慢走调整呼吸，及时补水。今天训练强度较高，留出充分恢复时间。" else "慢走调整呼吸，做轻柔拉伸，补充水分。",style=MaterialTheme.typography.bodyMedium)}
        if(!deletable) {
            Text(if(saving)"正在保存记录…" else if(saveError.isNotEmpty())saveError else "已保存至跑步历史",color=if(saveError.isEmpty())LuoBlue else MaterialTheme.colorScheme.error)
            if(saveError.isNotEmpty())Button(onClick=vm::retrySave) {Text("重试保存记录")}
            OutlinedButton(onClick={vm.savePlayed(LocalDate.now().toString())},enabled=!appState.run.playedPlaylistSaved) {Text(if(appState.run.playedPlaylistSaved)"本次歌单已保存" else "保存本次播放歌单")}
        } else TextButton(onClick={confirmDelete=true}) {Text("删除这条记录",color=MaterialTheme.colorScheme.error)}
    }
    if(confirmDelete)AlertDialog(onDismissRequest={confirmDelete=false},title={Text("删除这条记录？")},text={Text("删除后无法恢复，周统计会同步更新。")},confirmButton={TextButton(onClick={confirmDelete=false;vm.deleteRecord(w.id)}) {Text("确认删除")}},dismissButton={TextButton(onClick={confirmDelete=false}) {Text("取消")}})
}
@Composable fun HeartRateChart(samples:List<HeartRateSampleEntity>) {
    if(samples.isEmpty()) {Text("这次没有记录到心率，连接心率设备后可查看曲线。");return}
    val low=(samples.minOf {it.bpm}-10).coerceAtLeast(30);val high=(samples.maxOf {it.bpm}+10).coerceAtLeast(low+20);val end=samples.maxOf {it.elapsedMillis}.coerceAtLeast(1)
    Text("${samples.size} 个采样点 · $low–$high bpm",style=MaterialTheme.typography.bodySmall)
    Canvas(Modifier.fillMaxWidth().height(150.dp).semantics {contentDescription="心率变化，最低 ${samples.minOf {it.bpm}}，最高 ${samples.maxOf {it.bpm}}，时长 ${duration(end)}"}) {
        repeat(3) {i->val y=size.height*i/2;drawLine(Color.Gray.copy(alpha=.25f),androidx.compose.ui.geometry.Offset(0f,y),androidx.compose.ui.geometry.Offset(size.width,y))}
        val path=Path();samples.forEachIndexed {i,p->val x=p.elapsedMillis.toFloat()/end*size.width;val y=size.height-(p.bpm-low).toFloat()/(high-low)*size.height;if(i==0)path.moveTo(x,y) else path.lineTo(x,y)}
        drawPath(path,LuoBlue,style=Stroke(2.dp.toPx()));if(samples.size==1)drawCircle(LuoBlue,3.dp.toPx(),androidx.compose.ui.geometry.Offset(0f,size.height-(samples.first().bpm-low).toFloat()/(high-low)*size.height))
    }
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {Text("0:00");Text(duration(end))}
}
