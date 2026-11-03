package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.model.ConnectionStatus
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.GreenOnline
import com.example.ui.theme.RedOffline

@Composable
fun PulseRadarAnimation(
    status: ConnectionStatus,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse_radar")

    val isAlert = status == ConnectionStatus.DISCONNECTED_ALERT || status == ConnectionStatus.LOCATION_CHANGED_ALERT

    val pulse1 by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isAlert) 800 else 2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse1"
    )

    val pulse2 by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isAlert) 800 else 2200, delayMillis = 600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse2"
    )

    val color = when (status) {
        ConnectionStatus.ONLINE_MONITORING -> GreenOnline
        ConnectionStatus.DISCONNECTED_ALERT -> RedOffline
        ConnectionStatus.LOCATION_CHANGED_ALERT -> AmberWarning
        ConnectionStatus.MUTED_ALERT -> RedOffline.copy(alpha = 0.7f)
        ConnectionStatus.CONNECTING -> AmberWarning
        ConnectionStatus.RECONNECTING -> AmberWarning
        ConnectionStatus.IDLE -> CyanAccent
    }

    Box(
        modifier = modifier.size(160.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = this.center
            val maxRadius = size.minDimension / 2f

            if (status != ConnectionStatus.IDLE) {
                // Ring 1
                drawCircle(
                    color = color.copy(alpha = (1f - pulse1).coerceIn(0f, 0.7f)),
                    radius = maxRadius * pulse1,
                    center = center,
                    style = Stroke(width = 3.dp.toPx())
                )

                // Ring 2
                drawCircle(
                    color = color.copy(alpha = (1f - pulse2).coerceIn(0f, 0.5f)),
                    radius = maxRadius * pulse2,
                    center = center,
                    style = Stroke(width = 2.dp.toPx())
                )
            }

            // Central solid indicator circle
            drawCircle(
                color = color.copy(alpha = 0.25f),
                radius = maxRadius * 0.45f,
                center = center
            )
            drawCircle(
                color = color,
                radius = maxRadius * 0.32f,
                center = center
            )
        }

        Icon(
            imageVector = when (status) {
                ConnectionStatus.DISCONNECTED_ALERT, ConnectionStatus.MUTED_ALERT -> Icons.Default.Warning
                ConnectionStatus.LOCATION_CHANGED_ALERT -> Icons.Default.MyLocation
                ConnectionStatus.ONLINE_MONITORING -> Icons.Default.Sensors
                else -> Icons.Default.NotificationsActive
            },
            contentDescription = "وضعیت ضربان",
            tint = Color.White,
            modifier = Modifier.size(28.dp)
        )
    }
}
