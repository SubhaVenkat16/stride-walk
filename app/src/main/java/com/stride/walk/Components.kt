package com.stride.walk

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

val Green = Color(0xFF2E9E6B)
val GreenDark = Color(0xFF1B6B47)
val GreenSoft = Color(0xFFE3F1EA)
val Orange = Color(0xFFFF8A3D)
val Red = Color(0xFFE5484D)
val Ink = Color(0xFF1C2B24)
val Muted = Color(0xFF5E7168)

@Composable
fun StrideTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Green,
            onPrimary = Color.White,
            secondary = Orange,
            background = Color(0xFFF6FBF8),
            surface = Color.White,
            onSurface = Ink
        ),
        content = content
    )
}

@Composable
fun MediaThumb(path: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val bmp by produceState<ImageBitmap?>(null, path) {
        value = withContext(Dispatchers.IO) {
            val b = if (isVideo(path)) BitmapUtil.videoThumb(path) else BitmapUtil.decode(path, 400)
            b?.asImageBitmap()
        }
    }
    Box(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFDDE6E1))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        val img = bmp
        if (img != null) {
            Image(img, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        if (isVideo(path)) {
            Text("▶", color = Color.White, fontSize = 28.sp)
        }
    }
}

/** Three buttons: take a photo, record a video, or pick from the gallery. */
@Composable
fun MediaButtons(onAdded: (List<String>) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var pending by rememberSaveable { mutableStateOf<String?>(null) }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(10)) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                val paths = withContext(Dispatchers.IO) { uris.mapNotNull { copyIn(ctx, it) } }
                if (paths.isNotEmpty()) onAdded(paths)
            }
        }
    }
    val photo = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val p = pending
        pending = null
        if (ok && p != null) onAdded(listOf(p)) else if (p != null) File(p).delete()
    }
    val video = rememberLauncherForActivityResult(ActivityResultContracts.CaptureVideo()) { ok ->
        val p = pending
        pending = null
        if (ok && p != null) onAdded(listOf(p)) else if (p != null) File(p).delete()
    }

    val pad = PaddingValues(horizontal = 4.dp)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedButton(
            onClick = {
                val f = newMediaFile(ctx, "jpg")
                pending = f.absolutePath
                photo.launch(uriFor(ctx, f))
            },
            modifier = Modifier.weight(1f),
            contentPadding = pad
        ) { Text("📷 Photo", fontSize = 13.sp) }
        OutlinedButton(
            onClick = {
                val f = newMediaFile(ctx, "mp4")
                pending = f.absolutePath
                video.launch(uriFor(ctx, f))
            },
            modifier = Modifier.weight(1f),
            contentPadding = pad
        ) { Text("🎥 Video", fontSize = 13.sp) }
        OutlinedButton(
            onClick = {
                pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
            },
            modifier = Modifier.weight(1f),
            contentPadding = pad
        ) { Text("🖼 Gallery", fontSize = 13.sp) }
    }
}

@Composable
fun GoalRing(steps: Int, goal: Int, dia: Dp = 150.dp) {
    val frac = (steps.toFloat() / goal.coerceAtLeast(1)).coerceIn(0f, 1f)
    Box(Modifier.size(dia), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val sw = 16.dp.toPx()
            val arcSize = Size(size.width - sw, size.height - sw)
            val topLeft = Offset(sw / 2, sw / 2)
            drawArc(
                color = GreenSoft, startAngle = -90f, sweepAngle = 360f, useCenter = false,
                topLeft = topLeft, size = arcSize, style = Stroke(sw, cap = StrokeCap.Round)
            )
            if (frac > 0f) {
                drawArc(
                    color = Green, startAngle = -90f, sweepAngle = 360f * frac, useCenter = false,
                    topLeft = topLeft, size = arcSize, style = Stroke(sw, cap = StrokeCap.Round)
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(fmtSteps(steps), fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Ink)
            Text("of ${fmtSteps(goal)}", fontSize = 12.sp, color = Muted)
        }
    }
}

@Composable
fun WeekChart(days: List<Pair<String, Int>>, goal: Int) {
    val maxV = maxOf(goal, days.maxOfOrNull { it.second } ?: 0, 1).toFloat()
    Column(Modifier.fillMaxWidth()) {
        Canvas(Modifier.fillMaxWidth().height(120.dp)) {
            val slot = size.width / days.size
            val bw = slot * 0.5f
            days.forEachIndexed { i, d ->
                val h = (size.height * (d.second / maxV)).coerceAtLeast(4f)
                drawRoundRect(
                    color = if (d.second >= goal) Green else Color(0xFFA5D6BC),
                    topLeft = Offset(i * slot + (slot - bw) / 2f, size.height - h),
                    size = Size(bw, h),
                    cornerRadius = CornerRadius(8f, 8f)
                )
            }
            val goalY = size.height - size.height * (goal / maxV)
            drawLine(
                color = Orange,
                start = Offset(0f, goalY),
                end = Offset(size.width, goalY),
                strokeWidth = 3f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 12f))
            )
        }
        Row(Modifier.fillMaxWidth()) {
            days.forEach {
                Text(
                    it.first, Modifier.weight(1f), textAlign = TextAlign.Center,
                    fontSize = 12.sp, color = Muted
                )
            }
        }
    }
}

@Composable
fun StatBlock(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Ink)
        Text(label, fontSize = 12.sp, color = Muted)
    }
}
