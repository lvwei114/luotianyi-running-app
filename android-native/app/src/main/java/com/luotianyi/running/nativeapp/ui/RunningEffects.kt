package com.luotianyi.running.nativeapp.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.luotianyi.running.nativeapp.data.DisplayTier
import kotlin.math.sin

/** Same fixed, non-interactive top caption as the original project's voice toast. */
@Composable fun TianyiToast(text:String,tier:DisplayTier,modifier:Modifier=Modifier) {
    var lastText by remember {mutableStateOf(text)}
    LaunchedEffect(text) {if(text.isNotBlank())lastText=text}
    AnimatedVisibility(visible=text.isNotBlank(),modifier=modifier.fillMaxWidth(.88f),
        enter=fadeIn(tween(if(tier==DisplayTier.LOW)0 else 300)),
        exit=fadeOut(tween(if(tier==DisplayTier.LOW)0 else 300))) {
        Surface(shape=RoundedCornerShape(24.dp),color=Color.Black.copy(alpha=.72f),
            modifier=Modifier.testTag("tianyi-toast").semantics {liveRegion=LiveRegionMode.Polite}) {
            Text("天依：${text.ifBlank {lastText}}",Modifier.padding(horizontal=16.dp,vertical=10.dp),
                color=LuoBlue,style=MaterialTheme.typography.bodyMedium,textAlign=TextAlign.Center,
                maxLines=3,overflow=TextOverflow.Ellipsis)
        }
    }
}

/** Native equivalents of the original aurora float and particle rise animations. */
@Composable fun RunningAtmosphere(tier:DisplayTier,running:Boolean) {
    if(tier==DisplayTier.LOW)return
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val animated=tier==DisplayTier.HIGH && running && lifecycle.isAtLeast(Lifecycle.State.RESUMED)
    val movement:State<Float>
    if(animated) {
        val transition=rememberInfiniteTransition(label="aurora")
        movement=transition.animateFloat(0f,1f,infiniteRepeatable(tween(10000,easing=LinearEasing)),label="float-rise")
    } else movement=remember {mutableFloatStateOf(0f)}
    Canvas(Modifier.fillMaxSize()) {
        val phase=movement.value
        val drift=sin(phase*2*Math.PI).toFloat()*24.dp.toPx()
        val colors=listOf(RunOrange,LuoBlue,RunOrange)
        val centers=listOf(Offset(size.width*.05f+drift,size.height*.12f+drift),
            Offset(size.width*.95f-drift,size.height*.35f+drift),
            Offset(size.width*.85f+drift,size.height*.86f-drift))
        centers.forEachIndexed {index,center->
            val radius=size.width*(if(index==1).55f else .8f)
            drawCircle(Brush.radialGradient(listOf(colors[index].copy(alpha=if(tier==DisplayTier.HIGH).13f else .06f),Color.Transparent),center,radius),radius,center)
        }
        if(animated)repeat(12) {index->
            val progress=(phase*1.6667f+index/12f)%1f
            val x=size.width*((index*37%100)/100f)
            val y=size.height*(1f-progress)
            val alpha=sin(progress*Math.PI).toFloat()*.5f
            drawCircle((if(index%2==0)LuoBlue else RunOrange).copy(alpha=alpha),((index%3)+1).dp.toPx(),Offset(x,y))
        }
    }
}
