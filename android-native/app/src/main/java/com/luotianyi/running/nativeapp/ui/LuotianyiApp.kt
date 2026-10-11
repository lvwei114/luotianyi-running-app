package com.luotianyi.running.nativeapp.ui
import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DirectionsRun
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.painterResource
import com.luotianyi.running.nativeapp.R
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.luotianyi.running.nativeapp.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.graphics.BitmapFactory
import java.io.File

enum class AppTab(val label:String) { HOME("首页"),RUN("记录"),MUSIC("音乐"),SETTINGS("设置") }
@Composable fun localPicture(path:String):ImageBitmap? {
    val context=LocalContext.current
    val bitmap by produceState<ImageBitmap?>(null,path) {value=withContext(Dispatchers.IO) {
        runCatching {
            val options=BitmapFactory.Options().apply {inSampleSize=2}
            if(path.startsWith("asset:"))context.assets.open(path.removePrefix("asset:")).use {BitmapFactory.decodeStream(it,null,options)?.asImageBitmap()}
            else if(path.isNotEmpty())BitmapFactory.decodeFile(path,options)?.asImageBitmap() else null
        }.getOrNull()
    }}
    return bitmap
}
@Composable fun LuotianyiApp(vm:AppViewModel) {
    val state by vm.state.collectAsStateWithLifecycle();val context=LocalContext.current
    var tabName by rememberSaveable {mutableStateOf(AppTab.HOME.name)};val tab=AppTab.valueOf(tabName)
    var workout by rememberSaveable {mutableStateOf(false)};var summary by rememberSaveable {mutableStateOf(false)}
    var openedSession by rememberSaveable {mutableStateOf<String?>(null)}
    val active=state.run.training.phase in listOf(RunPhase.PREPARING,RunPhase.RUNNING,RunPhase.PAUSED)
    var pendingMode by rememberSaveable {mutableStateOf<String?>(null)}
    val snackbar=remember {SnackbarHostState()};val holder=rememberSaveableStateHolder()
    val trainingPermissions=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {granted->
        val mode=pendingMode?.let(RunMode::fromStored);pendingMode=null
        if(granted[Manifest.permission.ACCESS_FINE_LOCATION]!=true)vm.message("未允许精确 GPS，仍可训练；距离仅在取得 GPS 后记录")
        if(mode!=null)vm.start(mode)
    }
    val blePermissions=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {vm.scan()}
    fun start(mode:RunMode) {
        pendingMode=mode.name
        val permissions=mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION)
        if(Build.VERSION.SDK_INT>=29)permissions.add(Manifest.permission.ACTIVITY_RECOGNITION)
        if(Build.VERSION.SDK_INT>=33)permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        trainingPermissions.launch(permissions.toTypedArray())
    }
    fun scan() {blePermissions.launch(if(Build.VERSION.SDK_INT>=31)arrayOf(Manifest.permission.BLUETOOTH_SCAN,Manifest.permission.BLUETOOTH_CONNECT) else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION))}
    LaunchedEffect(vm) {vm.messages.collect {snackbar.showSnackbar(it)}}
    // Open only after the service accepts a new session. Minimizing survives rotation.
    LaunchedEffect(active,state.run.sessionId) {
        if(active && openedSession!=state.run.sessionId) {openedSession=state.run.sessionId;workout=true}
        if(!active)workout=false
    }
    LaunchedEffect(state.run.completed?.id) {if(state.run.completed!=null) {summary=true;workout=false;tabName=AppTab.RUN.name}}
    BackHandler(workout || tab!=AppTab.HOME) {if(workout)workout=false else tabName=AppTab.HOME.name}
    val background=state.backgrounds.find {it.id==state.settings.background}?.path.orEmpty()
    val image=localPicture(background)
    Box(Modifier.fillMaxSize()) {
        if(image!=null)Image(image,null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Color(0xFF10111A).copy(alpha=if(image==null)1f else .82f)))
        RunningAtmosphere(state.settings.displayTier,state.run.training.phase==RunPhase.RUNNING)
        Scaffold(containerColor=Color.Transparent,contentColor=MaterialTheme.colorScheme.onBackground,snackbarHost={SnackbarHost(snackbar)},bottomBar={
            if(!workout)NavigationBar(containerColor=AppSurface.copy(alpha=.96f)) {AppTab.entries.forEach {entry->
                val icon=when(entry) {AppTab.HOME->Icons.Outlined.Home;AppTab.RUN->Icons.AutoMirrored.Outlined.DirectionsRun;AppTab.MUSIC->Icons.Outlined.LibraryMusic;AppTab.SETTINGS->Icons.Outlined.Settings}
                NavigationBarItem(selected=tab==entry,onClick={tabName=entry.name},icon={Icon(icon,null)},label={Text(entry.label)})
            }}
        }) {padding->Box(Modifier.fillMaxSize().padding(padding)) {
            val screen=if(workout)"WORKOUT" else tabName
            AnimatedContent(targetState=screen,transitionSpec={
                val ms=if(state.settings.displayTier==com.luotianyi.running.nativeapp.data.DisplayTier.LOW)0 else 220
                fadeIn(tween(ms)) togetherWith fadeOut(tween(ms))
            },label="page") {target->holder.SaveableStateProvider(target) {
                if(target=="WORKOUT")Column(Modifier.fillMaxSize()) {
                    if(state.subtitle.isNotBlank())Box(Modifier.fillMaxWidth().padding(vertical=8.dp),contentAlignment=Alignment.TopCenter) {
                        TianyiToast(state.subtitle,state.settings.displayTier)
                    }
                    Box(Modifier.weight(1f)) {RunScreen(state,vm,::start,{workout=false})}
                } else when(AppTab.valueOf(target)) {
                    AppTab.HOME->HomeScreen(state,vm,::start,{workout=true})
                    AppTab.RUN->HistoryScreen(state,vm,{workout=true})
                    AppTab.MUSIC->MusicScreen(state,vm)
                    AppTab.SETTINGS->SettingsScreen(state,vm,::scan,{context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))})
                }
            }}
            if(!workout)TianyiToast(state.subtitle,state.settings.displayTier,Modifier.align(Alignment.TopCenter).padding(top=64.dp))
        }}
    }
    state.detail?.let {WorkoutDetailDialog(it,vm,{vm.closeDetail()},true)}
    if(summary && state.run.completed!=null) {
        val detail=WorkoutDetail(state.run.completed!!,state.run.samples)
        WorkoutDetailDialog(detail,vm,{summary=false;vm.dismissSummary()},false,state.run.saving,state.run.saveError)
    }
    state.streakMilestone?.takeIf {
        state.settingsLoaded && state.settings.disclaimerAccepted && !state.busy && !state.recovering &&
            state.run.completed==null && state.detail==null && state.error==null && state.run.alert.isEmpty()
    }?.let {StreakMilestoneDialog(it.days,vm::dismissStreakMilestone)}
    if(state.run.alert.isNotEmpty())AlertDialog(onDismissRequest=vm::clearAlert,title={Text("运动提醒")},text={Text(state.run.alert)},confirmButton={TextButton(onClick=vm::clearAlert) {Text("知道了")}})
    state.error?.let {error->AlertDialog(onDismissRequest=vm::dismissError,title={Text("操作未完成")},text={Text(error)},confirmButton={TextButton(onClick=vm::dismissError) {Text("知道了")}})}
    if(state.settingsLoaded && !state.settings.disclaimerAccepted)AlertDialog(onDismissRequest={},title={Text("开始前请了解")},text={Text("运动数据和个人信息保存在本机。心率与 GPS 会有误差，心率提醒不能代替医疗判断。身体不舒服时请停止运动。\n\n洛天依陪跑使用现有本地语音；记录距离只取 GPS，定位缺失路段不补算。")},confirmButton={Button(onClick={vm.settings {it.copy(disclaimerAccepted=true)}}) {Text("我已了解")}})
}

@Composable internal fun StreakMilestoneDialog(days:Int,close:()->Unit) {
    AlertDialog(onDismissRequest=close,
        title={Text("坚持的你，真棒！")},
        text={Column(Modifier.fillMaxWidth(),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(18.dp)) {
            Image(painterResource(R.drawable.streak_icon5),contentDescription=null,modifier=Modifier.size(200.dp),contentScale=ContentScale.Fit)
            Text("你已经坚持${days}天了！天依觉得你好棒！")
        }},
        confirmButton={Box(Modifier.fillMaxWidth(),contentAlignment=Alignment.Center) {
            Button(onClick=close,modifier=Modifier.widthIn(min=180.dp).heightIn(min=56.dp)) {
                Text("继续加油",fontSize=18.sp,fontWeight=FontWeight.SemiBold)
            }
        }})
}
