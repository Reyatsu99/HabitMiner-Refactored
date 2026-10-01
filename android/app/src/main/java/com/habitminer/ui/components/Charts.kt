package com.habitminer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import java.util.concurrent.TimeUnit

@Suppress("FunctionName")
@Composable
fun TopAppMiniChart(
    appUsages: Map<String, Long>,
    modifier: Modifier = Modifier,
) {
    if (appUsages.isEmpty()) return

    val topApps = appUsages.entries.sortedByDescending { it.value }.take(5)
    val maxDuration = topApps.maxOfOrNull { it.value } ?: 1L

    Column(modifier = modifier.fillMaxWidth()) {
        topApps.forEach { (appName, duration) ->
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = appName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(0.35f),
                )

                val minutes = TimeUnit.MILLISECONDS.toMinutes(duration)
                val timeString =
                    if (minutes >= 60) {
                        val h = minutes / 60
                        val m = minutes % 60
                        if (m > 0) "${h}h ${m}m" else "${h}h"
                    } else {
                        "${minutes}m"
                    }

                Text(
                    text = timeString,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.width(60.dp),
                )

                val fraction = (duration.toFloat() / maxDuration.toFloat()).coerceIn(0f, 1f)
                val animatedFraction by androidx.compose.animation.core.animateFloatAsState(
                    targetValue = fraction,
                    animationSpec = androidx.compose.animation.core.tween(durationMillis = 1000, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                    label = "fraction"
                )

                Box(
                    modifier =
                        Modifier
                            .weight(0.5f)
                            .height(12.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)),
                ) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth(fraction = animatedFraction)
                                .height(12.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.primary),
                    )
                }
            }
        }
    }
}
