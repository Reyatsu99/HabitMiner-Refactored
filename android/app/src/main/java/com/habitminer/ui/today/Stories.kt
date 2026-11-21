@file:Suppress("ktlint:standard:function-naming")

package com.habitminer.ui.today

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.habitminer.analytics.PatternAction
import com.habitminer.analytics.StorySlide
import com.habitminer.analytics.StoryTone
import com.habitminer.ui.design.GuardRequest
import com.habitminer.ui.design.LocalAppGuards
import com.habitminer.ui.design.ScreenPadding

/** One bubble in the row at the top of Today. */
data class StoryBubble(
    val id: String,
    val label: String,
    val icon: ImageVector,
    val tone: StoryTone,
    /** Watched already (today, or this week for the Weekly Story): the ring goes grey. */
    val seen: Boolean,
    /** Slides to show; empty for one that opens a screen of its own (the Weekly Story). */
    val slides: List<StorySlide> = emptyList(),
)

/** Remembers which stories you've watched: today's and last night's per day, the week's per week. */
object StorySeen {
    private fun prefs(context: Context) = context.getSharedPreferences("stories", Context.MODE_PRIVATE)

    fun seen(
        context: Context,
        id: String,
        stamp: String,
    ): Boolean = runCatching { prefs(context).getString(id, null) == stamp }.getOrDefault(false)

    fun mark(
        context: Context,
        id: String,
        stamp: String,
    ) = runCatching { prefs(context).edit().putString(id, stamp).apply() }
}

/** Two gradient stops for a tone, the same in light and dark (stories are always vivid). */
fun storyColors(tone: StoryTone): Pair<Color, Color> =
    when (tone) {
        StoryTone.SCREEN -> Color(0xFFD10A45) to Color(0xFFFF6A93)
        StoryTone.SLEEP -> Color(0xFF3730A3) to Color(0xFF8B5CF6)
        StoryTone.GOOD -> Color(0xFF15803D) to Color(0xFF4ADE80)
        StoryTone.NOTIFY -> Color(0xFF7E22CE) to Color(0xFFD946EF)
        StoryTone.CHECK -> Color(0xFF0E7490) to Color(0xFF22D3EE)
        StoryTone.CALM -> Color(0xFFC2410C) to Color(0xFFF59E0B)
    }

/**
 * The row of story bubbles at the top of Today, like Instagram's: a coloured ring until you've
 * watched one, grey after.
 */
@Composable
fun StoryStrip(
    bubbles: List<StoryBubble>,
    onOpen: (StoryBubble) -> Unit,
) {
    if (bubbles.isEmpty()) return
    LazyRow(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = ScreenPadding),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
    ) {
        items(bubbles.size) { i ->
            val b = bubbles[i]
            val (a, c) = storyColors(b.tone)
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(72.dp).clickable(onClickLabel = "Open ${b.label}") { onOpen(b) }) {
                val ring =
                    if (b.seen) {
                        Brush.linearGradient(listOf(MaterialTheme.colorScheme.outlineVariant, MaterialTheme.colorScheme.outlineVariant))
                    } else {
                        Brush.linearGradient(listOf(a, c))
                    }
                Box(
                    Modifier.size(66.dp).border(3.dp, ring, CircleShape).padding(5.dp).clip(CircleShape)
                        .background(if (b.seen) Brush.linearGradient(listOf(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.colorScheme.surfaceContainerHigh)) else Brush.linearGradient(listOf(a, c))),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(b.icon, contentDescription = null, tint = if (b.seen) MaterialTheme.colorScheme.onSurfaceVariant else Color.White, modifier = Modifier.size(28.dp))
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    b.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (b.seen) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
            }
        }
    }
}

/** Full-screen story: tap the right side for next, the left for back. */
@Composable
fun StoryViewer(
    slides: List<StorySlide>,
    onClose: () -> Unit,
    onOpenSleep: () -> Unit,
) {
    if (slides.isEmpty()) return
    var index by remember { mutableIntStateOf(0) }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        StoryPage(
            slide = slides[index],
            index = index,
            count = slides.size,
            onNext = { if (index < slides.lastIndex) index++ else onClose() },
            onBack = { if (index > 0) index-- },
            onClose = onClose,
            onOpenSleep = onOpenSleep,
        )
    }
}

/** One slide, full screen. */
@Composable
fun StoryPage(
    slide: StorySlide,
    index: Int,
    count: Int,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onClose: () -> Unit,
    onOpenSleep: () -> Unit = {},
) {
    val (a, c) = storyColors(slide.tone)
    var showNumbers by remember(slide.id) { mutableStateOf(false) }
    Box(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(a, c)))
            .pointerInput(slide.id) {
                detectTapGestures { o -> if (o.x < size.width / 3f) onBack() else onNext() }
            },
    ) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 24.dp, vertical = 12.dp)) {
            // Progress: one segment per slide.
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                repeat(count) { i ->
                    Box(Modifier.weight(1f).height(3.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = if (i <= index) 0.95f else 0.35f)))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text(slide.kicker, style = MaterialTheme.typography.titleSmall, color = Color.White.copy(alpha = 0.9f), modifier = Modifier.weight(1f))
                IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, contentDescription = "Close", tint = Color.White) }
            }
            Spacer(Modifier.height(56.dp))
            Text(slide.headline, style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold), color = Color.White)
            Spacer(Modifier.height(18.dp))
            Text(slide.meaning, style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.92f))
            slide.tip?.let {
                Spacer(Modifier.height(22.dp))
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = 0.16f)).padding(16.dp)) {
                    Column {
                        Text("Try this", style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.85f))
                        Spacer(Modifier.height(4.dp))
                        Text(it, style = MaterialTheme.typography.bodyLarge, color = Color.White)
                        StoryAction(slide, onOpenSleep)
                    }
                }
            }
            slide.numbers?.let { n ->
                Spacer(Modifier.height(8.dp))
                if (showNumbers) {
                    Text(n, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.8f))
                } else {
                    TextButton(onClick = { showNumbers = true }, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 0.dp)) {
                        Text("See the numbers", color = Color.White.copy(alpha = 0.85f))
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Text(
                if (index < count - 1) "Tap for the next one" else "Tap to finish",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** The one-tap action behind a slide, if it has one. */
@Composable
private fun StoryAction(
    slide: StorySlide,
    onOpenSleep: () -> Unit,
) {
    val p = slide.pattern ?: return
    val guards = LocalAppGuards.current
    val context = LocalContext.current
    var done by remember(slide.id) { mutableStateOf(false) }
    val listed = guards != null && p.packageName != null && p.packageName in guards.useLess
    val label: String
    val run: () -> Unit
    when (p.action) {
        PatternAction.PAUSE_APPS_AT_NIGHT -> {
            if (guards == null || p.apps.isEmpty()) return
            if (p.apps.all { it.first in guards.useLess && guards.pauseFrom[it.first] != null }) {
                done = true
            }
            label = "Pause ${com.habitminer.analytics.PatternFinder.appNames(p.apps)} after 23:00 on weeknights"
            run = { p.apps.forEach { (pkg, _) -> guards.onGuard(GuardRequest(pkg, true, p.nightFromMinute ?: 23 * 60, true, p.title)) } }
        }
        PatternAction.PAUSE_APP_AT_NIGHT -> {
            val pkg = p.packageName ?: return
            if (guards == null) return
            if (listed) done = true
            label = "Pause ${p.appName} after ${com.habitminer.analytics.Format.clockFromMinutes(p.nightFromMinute ?: 23 * 60)}"
            run = { guards.onGuard(GuardRequest(pkg, true, p.nightFromMinute ?: 23 * 60, reason = p.title)) }
        }
        PatternAction.PAUSE_APP -> {
            val pkg = p.packageName ?: return
            if (guards == null) return
            if (listed) done = true
            label = "Pause before ${p.appName}"
            run = { guards.onGuard(GuardRequest(pkg, true, reason = p.title)) }
        }
        PatternAction.APP_NOTIFICATIONS -> {
            val pkg = p.packageName ?: return
            label = "Open ${p.appName}'s notification settings"
            run = {
                runCatching {
                    context.startActivity(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            }
        }
        PatternAction.OPEN_SLEEP -> {
            label = "See your nights"
            run = onOpenSleep
        }
        PatternAction.NONE -> return
    }
    Spacer(Modifier.height(12.dp))
    if (done) {
        Text("Done. It's on, and Goals will show whether it's working.", style = MaterialTheme.typography.titleSmall, color = Color.White)
    } else {
        Button(
            onClick = {
                run()
                if (p.action != PatternAction.APP_NOTIFICATIONS && p.action != PatternAction.OPEN_SLEEP) done = true
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF151518)),
        ) { Text(label) }
    }
}

/** Icons for the three kinds of bubbles. */
object StoryIcons {
    val today: ImageVector get() = Icons.Rounded.AutoAwesome
    val night: ImageVector get() = Icons.Rounded.Bedtime
    val week: ImageVector get() = Icons.Rounded.AutoStories
}
