package com.stride.walk

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private fun neededPermissions(): Array<String> {
    val l = mutableListOf<String>()
    if (Build.VERSION.SDK_INT >= 29) l.add(Manifest.permission.ACTIVITY_RECOGNITION)
    if (Build.VERSION.SDK_INT >= 33) l.add(Manifest.permission.POST_NOTIFICATIONS)
    return l.toTypedArray()
}

private val PERMISSION_HELP = "Allow \"Physical activity\" so Stride can count your steps."

// ---------------------------------------------------------------- root

@Composable
fun StrideRoot() {
    var tab by rememberSaveable { mutableStateOf(0) }
    var detailId by rememberSaveable { mutableStateOf<Long?>(null) }
    val summary by WalkState.summary.collectAsState()
    val walks by WalkState.walks.collectAsState()
    val s = summary
    val detail = walks.firstOrNull { it.id == detailId }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme3Bg) {
        if (s != null) {
            SummaryScreen(s) { WalkState.summary.value = null }
        } else if (detail != null) {
            DetailScreen(detail) { detailId = null }
        } else {
            Scaffold(
                containerColor = MaterialTheme3Bg,
                bottomBar = {
                    NavigationBar {
                        NavigationBarItem(tab == 0, { tab = 0 }, { Text("🚶") }, label = { Text("Walk") })
                        NavigationBarItem(tab == 1, { tab = 1 }, { Text("📊") }, label = { Text("Dashboard") })
                        NavigationBarItem(tab == 2, { tab = 2 }, { Text("🏅") }, label = { Text("Badges") })
                    }
                }
            ) { pad ->
                Box(Modifier.padding(pad).fillMaxSize()) {
                    when (tab) {
                        0 -> WalkScreen()
                        1 -> DashboardScreen { detailId = it }
                        else -> BadgesScreen()
                    }
                }
            }
        }
    }
}

private val MaterialTheme3Bg = Color(0xFFF6FBF8)

// ---------------------------------------------------------------- walk

@Composable
fun WalkScreen() {
    val ctx = LocalContext.current
    val live by WalkState.live.collectAsState()
    val media by WalkState.pendingMedia.collectAsState()
    val hasSensor by WalkState.hasStepSensor.collectAsState()
    var quick by remember { mutableStateOf(WalkRepo.quickStart) }
    var msg by remember { mutableStateOf<String?>(null) }

    val startPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (hasActivityPermission(ctx)) {
            msg = null
            WalkService.send(ctx, WalkService.ACTION_START)
        } else {
            msg = PERMISSION_HELP
        }
    }
    val quickPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (hasActivityPermission(ctx)) {
            msg = null
            WalkRepo.quickStart = true
            quick = true
            if (live.phase == Phase.IDLE) QuickStart.show(ctx)
        } else {
            quick = false
            msg = PERMISSION_HELP
        }
    }

    val km = live.steps * Walk.STRIDE_M / 1000.0
    val kcal = (live.steps * Walk.KCAL_PER_STEP).toInt()
    val status = when (live.phase) {
        Phase.IDLE -> "READY TO WALK"
        Phase.RUNNING -> "WALKING"
        Phase.PAUSED -> "PAUSED"
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Stride", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = GreenDark)
        Text(Quotes.daily(), textAlign = TextAlign.Center, color = Muted, fontSize = 14.sp)

        Card(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(status, fontSize = 13.sp, color = Green, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                Text(fmtDuration(live.elapsedMs / 1000), fontSize = 64.sp, fontWeight = FontWeight.Bold, color = Ink)
                Text(fmtSteps(live.steps), fontSize = 46.sp, fontWeight = FontWeight.Bold, color = Green)
                Text("steps", color = Muted)
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    StatBlock("%.2f".format(km), "km")
                    StatBlock("$kcal", "kcal")
                }
            }
        }

        if (live.phase == Phase.IDLE) {
            Button(
                onClick = {
                    val p = neededPermissions()
                    if (p.isEmpty()) WalkService.send(ctx, WalkService.ACTION_START) else startPerm.launch(p)
                },
                modifier = Modifier.fillMaxWidth().height(64.dp),
                shape = RoundedCornerShape(32.dp)
            ) { Text("▶  Start walk", fontSize = 20.sp) }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = {
                        val a = if (live.phase == Phase.RUNNING) WalkService.ACTION_PAUSE else WalkService.ACTION_RESUME
                        WalkService.send(ctx, a)
                    },
                    modifier = Modifier.weight(1f).height(60.dp),
                    shape = RoundedCornerShape(30.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Orange)
                ) { Text(if (live.phase == Phase.RUNNING) "⏸  Pause" else "▶  Resume", fontSize = 18.sp) }
                Button(
                    onClick = { WalkService.send(ctx, WalkService.ACTION_STOP) },
                    modifier = Modifier.weight(1f).height(60.dp),
                    shape = RoundedCornerShape(30.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Red)
                ) { Text("⏹  Stop", fontSize = 18.sp) }
            }

            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Capture this walk", fontWeight = FontWeight.Bold, color = Ink)
                    MediaButtons { WalkState.pendingMedia.value = WalkState.pendingMedia.value + it }
                    if (media.isNotEmpty()) {
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            media.forEach { MediaThumb(it, Modifier.size(72.dp)) { openMedia(ctx, it) } }
                        }
                    }
                }
            }
        }

        if (!hasSensor) {
            Text(
                "This phone has no step sensor, so steps can't be counted. Time will still be tracked.",
                color = Red, fontSize = 13.sp, textAlign = TextAlign.Center
            )
        }
        msg?.let { Text(it, color = Red, fontSize = 13.sp, textAlign = TextAlign.Center) }

        Row(
            Modifier.fillMaxWidth().padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Start button in notification bar", fontWeight = FontWeight.Medium, color = Ink)
                Text("Start a walk without opening the app", fontSize = 12.sp, color = Muted)
            }
            Switch(
                checked = quick,
                onCheckedChange = { on ->
                    if (on) {
                        val p = neededPermissions()
                        if (p.isEmpty()) {
                            WalkRepo.quickStart = true
                            quick = true
                            if (live.phase == Phase.IDLE) QuickStart.show(ctx)
                        } else {
                            quickPerm.launch(p)
                        }
                    } else {
                        WalkRepo.quickStart = false
                        quick = false
                        QuickStart.hide(ctx)
                    }
                }
            )
        }
    }
}

// ---------------------------------------------------------------- dashboard

private val dayFmt = DateTimeFormatter.ofPattern("EEE, d MMM")
private val timeFmt = DateTimeFormatter.ofPattern("h:mm a")

@Composable
fun DashboardScreen(onOpen: (Long) -> Unit) {
    val ctx = LocalContext.current
    val walks by WalkState.walks.collectAsState()
    var goal by remember { mutableIntStateOf(WalkRepo.goal) }
    var editGoal by remember { mutableStateOf(false) }

    val today = LocalDate.now()
    val byDay = walks.groupBy { it.date }
    val todaySteps = byDay[today].orEmpty().sumOf { it.steps }
    val streak = Badges.currentStreak(walks, today)
    val week = (6 downTo 0).map { off ->
        val d = today.minusDays(off.toLong())
        Pair(d.dayOfWeek.name.take(1), byDay[d].orEmpty().sumOf { it.steps })
    }
    val labels = listOf("Today", "Yesterday", "Day before yesterday")
    val older = walks.filter { it.date.isBefore(today.minusDays(2)) }.take(30)

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { Text("Your walks", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = GreenDark) }

        item {
            CardBox {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    GoalRing(todaySteps, goal)
                    Column(Modifier.padding(start = 20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Today's goal", fontWeight = FontWeight.Bold, color = Ink)
                        Text("🔥 $streak day streak", color = Ink)
                        val left = (goal - todaySteps).coerceAtLeast(0)
                        Text(
                            if (left == 0) "Goal reached! 🎉" else "${fmtSteps(left)} steps to go",
                            color = Muted, fontSize = 13.sp
                        )
                        TextButton(onClick = { editGoal = true }, contentPadding = PaddingValues(0.dp)) {
                            Text("Edit goal")
                        }
                    }
                }
            }
        }

        item {
            CardBox {
                Text("Last 7 days", fontWeight = FontWeight.Bold, color = Ink)
                Spacer(Modifier.height(8.dp))
                WeekChart(week, goal)
            }
        }

        labels.forEachIndexed { off, label ->
            val d = today.minusDays(off.toLong())
            val list = byDay[d].orEmpty()
            item(key = "day$off") {
                DayCard(label, d, list, onOpen) { openMedia(ctx, it) }
            }
        }

        if (older.isNotEmpty()) {
            item { Text("Earlier", fontWeight = FontWeight.Bold, color = Ink, fontSize = 18.sp) }
            older.forEach { w ->
                item(key = "old${w.id}") {
                    CardBox {
                        Text(w.date.format(dayFmt), color = Muted, fontSize = 12.sp)
                        WalkRow(w) { onOpen(w.id) }
                    }
                }
            }
        }
    }

    if (editGoal) {
        var txt by remember { mutableStateOf(goal.toString()) }
        AlertDialog(
            onDismissRequest = { editGoal = false },
            title = { Text("Daily step goal") },
            text = {
                OutlinedTextField(
                    value = txt,
                    onValueChange = { txt = it.filter { ch -> ch.isDigit() }.take(6) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val g = txt.toIntOrNull()?.coerceIn(500, 100000)
                    if (g != null) {
                        WalkRepo.goal = g
                        goal = g
                    }
                    editGoal = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editGoal = false }) { Text("Cancel") } }
        )
    }
}

@Composable
fun CardBox(content: @Composable () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(Modifier.padding(16.dp)) { content() }
    }
}

@Composable
fun DayCard(
    label: String,
    date: LocalDate,
    walks: List<Walk>,
    onOpen: (Long) -> Unit,
    onMedia: (String) -> Unit
) {
    CardBox {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Ink, modifier = Modifier.weight(1f))
            Text(date.format(dayFmt), color = Muted, fontSize = 12.sp)
        }
        if (walks.isEmpty()) {
            Text("No walks yet", color = Muted, modifier = Modifier.padding(top = 8.dp))
        } else {
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                StatBlock(fmtSteps(walks.sumOf { it.steps }), "steps")
                StatBlock(fmtMinutes(walks.sumOf { it.durationSec }), "time")
                StatBlock("%.2f".format(walks.sumOf { it.distanceKm }), "km")
            }
            val allMedia = walks.flatMap { it.media }
            if (allMedia.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    allMedia.forEach { MediaThumb(it, Modifier.size(64.dp)) { onMedia(it) } }
                }
            }
            Spacer(Modifier.height(6.dp))
            walks.forEach { WalkRow(it) { onOpen(it.id) } }
        }
    }
}

@Composable
fun WalkRow(w: Walk, onClick: () -> Unit) {
    val time = Instant.ofEpochMilli(w.startedAt).atZone(ZoneId.systemDefault()).toLocalTime().format(timeFmt)
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(time, fontWeight = FontWeight.Medium, color = Ink)
            Text(fmtMinutes(w.durationSec), fontSize = 12.sp, color = Muted)
        }
        if (w.media.isNotEmpty()) Text("📎${w.media.size}  ", color = Muted, fontSize = 13.sp)
        Text("${fmtSteps(w.steps)} steps  ›", color = Green, fontWeight = FontWeight.Bold)
    }
}

// ---------------------------------------------------------------- badges

@Composable
fun BadgesScreen() {
    val walks by WalkState.walks.collectAsState()
    val unlocked = Badges.unlocked(walks, WalkRepo.goal)
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Text("Badges", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = GreenDark) }
        item { Text("${unlocked.size} of ${Badges.all.size} unlocked", color = Muted) }
        Badges.all.forEach { b ->
            val on = b.id in unlocked
            item(key = b.id) {
                CardBox {
                    Row(Modifier.alpha(if (on) 1f else 0.4f), verticalAlignment = Alignment.CenterVertically) {
                        Text(b.emoji, fontSize = 40.sp)
                        Column(Modifier.padding(start = 16.dp).weight(1f)) {
                            Text(b.title, fontWeight = FontWeight.Bold, color = Ink)
                            Text(b.desc, fontSize = 13.sp, color = Muted)
                        }
                        Text(if (on) "✓" else "🔒", fontSize = 20.sp, color = Green)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- share panel

@Composable
fun SharePanel(walk: Walk, badge: String?) {
    val ctx = LocalContext.current
    var theme by rememberSaveable { mutableStateOf((walk.id % ShareCard.THEME_COUNT).toInt()) }
    var usePhoto by rememberSaveable { mutableStateOf(false) }
    val firstPhoto = walk.media.firstOrNull { !isVideo(it) }
    val key = listOf(walk.id, theme, usePhoto, firstPhoto, walk.steps, badge)
    val card by produceState<ImageBitmap?>(null, key) {
        value = withContext(Dispatchers.Default) {
            ShareCard.render(walk, theme, if (usePhoto) firstPhoto else null, badge).asImageBitmap()
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        val img = card
        if (img != null) {
            Image(
                img, null,
                Modifier.fillMaxWidth().aspectRatio(1080f / 1350f).clip(RoundedCornerShape(20.dp)),
                contentScale = ContentScale.Fit
            )
        } else {
            Box(
                Modifier.fillMaxWidth().aspectRatio(1080f / 1350f)
                    .clip(RoundedCornerShape(20.dp)).background(GreenSoft)
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { theme = (theme + 1) % ShareCard.THEME_COUNT },
                modifier = Modifier.weight(1f)
            ) { Text("🎨 Change style") }
            if (firstPhoto != null) {
                OutlinedButton(onClick = { usePhoto = !usePhoto }, modifier = Modifier.weight(1f)) {
                    Text(if (usePhoto) "🌄 Use scene" else "📷 Use my photo")
                }
            }
        }
        Button(
            onClick = { ShareCard.share(ctx, walk, theme, if (usePhoto) firstPhoto else null, badge) },
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(27.dp)
        ) { Text("Share on social media", fontSize = 16.sp) }
    }
}

// ---------------------------------------------------------------- summary & detail

@Composable
fun SummaryScreen(summary: Summary, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val walks by WalkState.walks.collectAsState()
    val walk = walks.firstOrNull { it.id == summary.walk.id } ?: summary.walk
    BackHandler { onDone() }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Walk complete! 🎉", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = GreenDark)
        CardBox {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                StatBlock(fmtSteps(walk.steps), "steps")
                StatBlock(fmtDuration(walk.durationSec), "time")
                StatBlock("%.2f".format(walk.distanceKm), "km")
                StatBlock("${walk.calories}", "kcal")
            }
        }
        summary.newBadges.forEach {
            CardBox {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(it.emoji, fontSize = 36.sp)
                    Column(Modifier.padding(start = 14.dp)) {
                        Text("Badge unlocked!", color = Orange, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Text(it.title, fontWeight = FontWeight.Bold, color = Ink)
                        Text(it.desc, color = Muted, fontSize = 12.sp)
                    }
                }
            }
        }
        SharePanel(walk, summary.newBadges.firstOrNull()?.title)

        CardBox {
            Text("Add photos or videos from this walk", fontWeight = FontWeight.Bold, color = Ink)
            Spacer(Modifier.height(8.dp))
            MediaButtons { WalkRepo.update(walk.copy(media = walk.media + it)) }
            MediaStrip(walk.media) { openMedia(ctx, it) }
        }
        Button(
            onClick = onDone,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(27.dp)
        ) { Text("Done", fontSize = 16.sp) }
    }
}

@Composable
private fun MediaStrip(media: List<String>, onOpen: (String) -> Unit) {
    if (media.isEmpty()) return
    Spacer(Modifier.height(10.dp))
    media.chunked(3).forEach { row ->
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { MediaThumb(it, Modifier.weight(1f).aspectRatio(1f)) { onOpen(it) } }
            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@Composable
fun DetailScreen(walk: Walk, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }
    BackHandler { onBack() }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack, contentPadding = PaddingValues(0.dp)) { Text("‹ Back") }
        }
        Text(walk.date.format(dayFmt), fontSize = 26.sp, fontWeight = FontWeight.Bold, color = GreenDark)
        CardBox {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                StatBlock(fmtSteps(walk.steps), "steps")
                StatBlock(fmtDuration(walk.durationSec), "time")
                StatBlock("%.2f".format(walk.distanceKm), "km")
                StatBlock("${walk.calories}", "kcal")
            }
        }
        CardBox {
            Text("Photos and videos", fontWeight = FontWeight.Bold, color = Ink)
            Spacer(Modifier.height(8.dp))
            MediaButtons { WalkRepo.update(walk.copy(media = walk.media + it)) }
            MediaStrip(walk.media) { openMedia(ctx, it) }
        }
        SharePanel(walk, null)
        TextButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
            Text("Delete this walk", color = Red)
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this walk?") },
            text = { Text("Its photos and videos will be removed too.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    WalkRepo.delete(walk.id)
                    onBack()
                }) { Text("Delete", color = Red) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
        )
    }
}
