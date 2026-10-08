package com.luotianyi.running.nativeapp.ui
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.luotianyi.running.nativeapp.data.*

@Composable fun MiniPlayer(state:AppUiState,vm:AppViewModel) {
    val song=state.music.queue.getOrNull(state.music.index)
    Panel("正在播放") {
        Text(song?.title ?: "还没有音乐",fontWeight=FontWeight.SemiBold)
        if(state.music.error.isNotEmpty())Text(state.music.error,color=MaterialTheme.colorScheme.error)
        if(song!=null) {
            var dragging by remember {mutableStateOf(false)};var progress by remember {mutableFloatStateOf(0f)}
            Slider(value=if(dragging)progress else state.music.position.coerceIn(0,song.durationMillis).toFloat(),onValueChange={dragging=true;progress=it},onValueChangeFinished={vm.seek(progress.toLong());dragging=false},valueRange=0f..song.durationMillis.coerceAtLeast(1).toFloat())
            Text("${duration(state.music.position)} / ${duration(song.durationMillis)}",style=MaterialTheme.typography.bodySmall)
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly) {
            TextButton(onClick=vm::previous,enabled=song!=null) {Text("上一首")}
            Button(onClick=vm::toggleMusic,enabled=song!=null) {Text(if(state.music.playing)"暂停播放" else "播放")}
            TextButton(onClick=vm::next,enabled=song!=null) {Text("下一首")}
        }
    }
}
@Composable fun MusicScreen(state:AppUiState,vm:AppViewModel) {
    var section by rememberSaveable {mutableStateOf("local")};var importDialog by rememberSaveable {mutableStateOf(false)}
    var selectedAlbum by remember {mutableStateOf<Album?>(null)}
    LazyColumn(Modifier.fillMaxSize().padding(horizontal=18.dp),verticalArrangement=Arrangement.spacedBy(12.dp),contentPadding=PaddingValues(bottom=20.dp)) {
        item {Header("音乐","把喜欢的旋律带上路")}
        item {MiniPlayer(state,vm)}
        item {Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {listOf("order" to "顺序","shuffle" to "随机","smart" to "智能").forEach {(key,label)->FilterChip(selected=state.music.mode==key,onClick={vm.musicMode(key)},label={Text(label)})}}}
        item {Button(onClick={importDialog=true},enabled=!state.importProgress.running,modifier=Modifier.fillMaxWidth()) {Text("批量导入歌单")}}
        item {Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {listOf("local" to "本地","library" to "内置专辑","saved" to "我的歌单").forEach {(key,label)->FilterChip(selected=section==key,onClick={section=key},label={Text(label)})}}}
        when(section) {
            "local" -> {
                item {TextButton(onClick=vm::detectAll,enabled=state.music.queue.isNotEmpty()) {Text("识别未标注歌曲的 BPM")}}
                if(state.music.queue.isEmpty())item {Text("还没有播放歌单，先导入音乐吧。")}
                items(state.music.queue,key={it.id}) {song->SongRow(song,state,vm)}
                val removed=state.songs.filter {it.removedFromQueue}
                if(removed.isNotEmpty())item {Text("已移除 · 文件仍保留",style=MaterialTheme.typography.titleMedium)}
                items(removed,key={"removed:"+it.id}) {song->Card(Modifier.fillMaxWidth()) {Column(Modifier.padding(14.dp)) {Text(song.title);TextButton(onClick={vm.restore(song)}) {Text("重新添加")}}}}
            }
            "saved" -> {
                if(state.playlists.isEmpty())item {Text("还没有保存的歌单。导入或训练结束时可以创建。")}
                items(state.playlists,key={it.id}) {p->Card(onClick={vm.openPlaylist(p)},modifier=Modifier.fillMaxWidth()) {Column(Modifier.padding(16.dp)) {Text(p.name,fontWeight=FontWeight.SemiBold);Text("点击查看歌曲与播放选项",style=MaterialTheme.typography.bodySmall)}}}
            }
            "library" -> {
                item {Text("内置专辑提供曲目目录。导入对应音频后，即可选择并播放。",style=MaterialTheme.typography.bodySmall)}
                items(state.albums,key={it.name}) {album->Card(onClick={selectedAlbum=album},modifier=Modifier.fillMaxWidth()) {Column(Modifier.padding(16.dp)) {Text(album.name,fontWeight=FontWeight.SemiBold);Text("${album.songs.size} 首")}}}
            }
        }
    }
    if(importDialog)ImportMusicDialog(state,vm,{importDialog=false})
    state.playlistDetail?.let {detail->PlaylistDialog(detail,vm,{vm.closePlaylist()})}
    selectedAlbum?.let {album->
        var selected by remember(album) {mutableStateOf(setOf<String>())}
        FullDialog(album.name,{selectedAlbum=null}) {
            Text("勾选曲目，可批量加入本机已导入的歌曲。",style=MaterialTheme.typography.bodySmall)
            album.songs.forEach {s->Row(Modifier.fillMaxWidth()) {Checkbox(checked=s.title in selected,onCheckedChange={checked->selected=if(checked)selected+s.title else selected-s.title});Column(Modifier.weight(1f).padding(top=8.dp)) {Text(s.title);Text("${s.bpm} BPM · ${duration(s.durationMs)}",style=MaterialTheme.typography.bodySmall)}}}
            Button(onClick={val songs=album.songs.filter {it.title in selected}.mapNotNull {a->state.songs.singleOrNull {it.title==a.title || it.title==a.title.substringAfter(" - ")}};if(songs.isEmpty())vm.message("对应音频尚未导入，请先从本地导入") else {vm.usePlaylist(songs,true);selectedAlbum=null}},enabled=selected.isNotEmpty()) {Text("加入所选曲目")}
            TextButton(onClick={selected=album.songs.map {it.title}.toSet()}) {Text("全选")}
        }
    }
}
@Composable private fun SongRow(song:SongEntity,state:AppUiState,vm:AppViewModel) {
    Card(Modifier.fillMaxWidth()) {Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Text(song.title,fontWeight=FontWeight.SemiBold)
        Text("${song.artist} · ${duration(song.durationMillis)} · ${song.bpm?.let {"$it BPM"} ?: if(song.bpmTried)"BPM 未识别" else "未测 BPM"}",style=MaterialTheme.typography.bodySmall)
        FlowRow(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            TextButton(onClick={vm.playSong(song.id)}) {Text(if(state.music.index>=0 && state.music.queue.getOrNull(state.music.index)?.id==song.id)"当前曲目" else "播放")}
            TextButton(onClick={vm.like(song.id)}) {Text(if(song.liked)"♥ 喜欢" else "♡ 喜欢")}
            TextButton(onClick={vm.detect(song)},enabled=song.id !in state.detecting) {Text(if(song.id in state.detecting)"检测中" else "测 BPM")}
            TextButton(onClick={vm.remove(song.id)}) {Text("移除")}
        }
    }}
}
@Composable private fun ImportMusicDialog(state:AppUiState,vm:AppViewModel,close:()->Unit) {
    var name by rememberSaveable {mutableStateOf("我的跑步歌单")};var target by rememberSaveable {mutableStateOf<String?>(null)}
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) {uris->if(uris.isNotEmpty())vm.importMusic(uris,name,target)}
    FullDialog("批量导入歌单",close,canClose=!state.importProgress.running) {
        Text("导入到")
        FilterChip(selected=target==null,onClick={target=null},enabled=!state.importProgress.running,label={Text("创建新歌单")})
        state.playlists.forEach {p->FilterChip(selected=target==p.id,onClick={target=p.id},enabled=!state.importProgress.running,label={Text("追加：${p.name}")})}
        if(target==null)OutlinedTextField(value=name,onValueChange={name=it},enabled=!state.importProgress.running,label={Text("歌单名称")},singleLine=true,modifier=Modifier.fillMaxWidth())
        Button(onClick={picker.launch(arrayOf("audio/*"))},enabled=!state.importProgress.running && (name.isNotBlank() || target!=null),modifier=Modifier.fillMaxWidth()) {Text("选择多首音乐并导入")}
        val p=state.importProgress
        if(p.total>0) {
            LinearProgressIndicator(progress={p.done.toFloat()/p.total},modifier=Modifier.fillMaxWidth())
            Text("${p.done}/${p.total} · 成功 ${p.successful} 首 · 失败 ${p.failures.size} 首")
            if(p.message.isNotEmpty())Text(p.message,color=LuoBlue)
            p.failures.forEach {Text(it,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.error)}
            if(!p.running && p.message.contains("重试"))OutlinedButton(onClick=vm::retryPlaylistSave) {Text("重试保存歌单")}
        }
        Text("单首失败不会中断其余文件。音乐保存在应用目录，导入完成后只自动检测前 3 首未知 BPM。",style=MaterialTheme.typography.bodySmall)
    }
}
@Composable private fun PlaylistDialog(detail:PlaylistDetail,vm:AppViewModel,close:()->Unit) {
    var confirmDelete by rememberSaveable {mutableStateOf(false)};var name by remember(detail.playlist.id) {mutableStateOf(detail.playlist.name)}
    FullDialog(detail.playlist.name,close) {
        Text("${detail.songs.size} 首 · ${duration(detail.songs.sumOf {it.durationMillis})}")
        detail.songs.forEach {Text("${it.title}\n${it.artist} · ${duration(it.durationMillis)}",style=MaterialTheme.typography.bodyMedium)}
        Button(onClick={vm.usePlaylist(detail.songs,play=true)},enabled=detail.songs.isNotEmpty(),modifier=Modifier.fillMaxWidth()) {Text("载入并播放")}
        OutlinedButton(onClick={vm.usePlaylist(detail.songs,append=true)},enabled=detail.songs.isNotEmpty(),modifier=Modifier.fillMaxWidth()) {Text("加入播放列表")}
        OutlinedTextField(value=name,onValueChange={name=it},label={Text("歌单名称")},singleLine=true,modifier=Modifier.fillMaxWidth())
        TextButton(onClick={vm.renamePlaylist(detail.playlist.id,name)},enabled=name.isNotBlank()) {Text("保存名称")}
        TextButton(onClick={confirmDelete=true}) {Text("删除歌单",color=MaterialTheme.colorScheme.error)}
    }
    if(confirmDelete)AlertDialog(onDismissRequest={confirmDelete=false},title={Text("删除这个歌单？")},text={Text("音乐文件会保留。")},confirmButton={TextButton(onClick={confirmDelete=false;vm.deletePlaylist(detail.playlist.id)}) {Text("确认删除")}},dismissButton={TextButton(onClick={confirmDelete=false}) {Text("取消")}})
}
@Composable fun FullDialog(title:String,close:()->Unit,canClose:Boolean=true,content:@Composable ColumnScope.()->Unit) {
    Dialog(onDismissRequest={if(canClose)close()},properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxSize(),color=AppBackground) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                Row(Modifier.fillMaxWidth().padding(16.dp),horizontalArrangement=Arrangement.SpaceBetween) {Text(title,style=MaterialTheme.typography.titleLarge,modifier=Modifier.weight(1f));TextButton(onClick=close,enabled=canClose) {Text("关闭")}}
                androidx.compose.foundation.rememberScrollState().let {scroll->Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll).padding(horizontal=18.dp,vertical=12.dp),verticalArrangement=Arrangement.spacedBy(12.dp),content=content)}
            }
        }
    }
}
