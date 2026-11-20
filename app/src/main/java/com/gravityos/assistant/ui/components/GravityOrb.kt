package com.gravityos.assistant.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gravityos.assistant.ui.theme.GravityColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

enum class OrbState {
    IDLE,
    LISTENING,
    THINKING,
    SPEAKING
}

@Composable
fun GravityOrb(
    state: OrbState,
    modifier: Modifier = Modifier,
    size: Dp = 200.dp,
    voiceAmplitude: Float = 0f
) {
    val transition = rememberInfiniteTransition(label = "gravity_orb_motion")

    val primaryDuration = when (state) {
        OrbState.IDLE -> 8000
        OrbState.LISTENING -> 3200
        OrbState.THINKING -> 1700
        OrbState.SPEAKING -> 2400
    }

    val primaryAngle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = primaryDuration, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "primary_rotation"
    )

    val counterAngle by transition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = (primaryDuration * 1.45f).toInt(), easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "counter_rotation"
    )

    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (state == OrbState.IDLE) 2600 else 1100,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orb_pulse"
    )

    val rippleProgress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ripple_ring"
    )

    Canvas(modifier = modifier.size(size)) {
        val canvasMin = this.size.minDimension
        val center = Offset(this.size.width / 2f, this.size.height / 2f)
        val clampedAmp = voiceAmplitude.coerceIn(0f, 1f)
        val breathScale = 1f + (pulse * if (state == OrbState.IDLE) 0.025f else 0.055f) + (clampedAmp * 0.08f)
        val orbRadius = (canvasMin * 0.32f) * breathScale

        // 1. Concentric glowing sonar rings (prominent in Listening / Speaking)
        if (state == OrbState.LISTENING || state == OrbState.SPEAKING || state == OrbState.IDLE) {
            val ringAlphaMax = if (state == OrbState.IDLE) 0.16f else 0.38f
            for (i in 0..1) {
                val p = (rippleProgress + i * 0.5f) % 1f
                val ringRadius = orbRadius * (1.05f + p * 0.58f)
                val ringAlpha = (1f - p) * ringAlphaMax
                drawCircle(
                    color = GravityColors.ElectricCyan.copy(alpha = ringAlpha),
                    radius = ringRadius,
                    center = center,
                    style = Stroke(width = (1.8f * (1f - p * 0.5f)).dp.toPx())
                )
            }
        }

        // 2. Deep ambient nebula halo behind the orb
        val haloRadius = (orbRadius * 1.68f).coerceAtLeast(1f)
        drawCircle(
            brush = Brush.radialGradient(
                colorStops = arrayOf(
                    0.0f to GravityColors.PlasmaBlue.copy(alpha = 0.45f),
                    0.48f to GravityColors.ElectricCyan.copy(alpha = if (state == OrbState.LISTENING) 0.34f else 0.22f),
                    0.72f to GravityColors.NebulaViolet.copy(alpha = 0.14f),
                    1.0f to Color.Transparent
                ),
                center = center,
                radius = haloRadius
            ),
            radius = haloRadius,
            center = center
        )

        // 3. Dark-core glass sphere body
        val bodyHighlightCenter = Offset(
            x = center.x - orbRadius * 0.18f,
            y = center.y - orbRadius * 0.18f
        )
        drawCircle(
            brush = Brush.radialGradient(
                colorStops = arrayOf(
                    0.0f to Color(0xFF040916),
                    0.52f to Color(0xFF0A1B40),
                    0.82f to GravityColors.DeepCobalt.copy(alpha = 0.92f),
                    0.96f to GravityColors.ElectricCyan.copy(alpha = 0.85f),
                    1.0f to GravityColors.IceCyan.copy(alpha = 0.55f)
                ),
                center = bodyHighlightCenter,
                radius = (orbRadius * 1.18f).coerceAtLeast(1f)
            ),
            radius = orbRadius,
            center = center
        )

        // 4. Primary rotating plasma sweep ribbon
        rotate(degrees = primaryAngle, pivot = center) {
            val arcRectSize = Size(orbRadius * 1.92f, orbRadius * 1.92f)
            val arcTopLeft = Offset(
                center.x - arcRectSize.width / 2f,
                center.y - arcRectSize.height / 2f
            )
            drawArc(
                brush = Brush.sweepGradient(
                    colorStops = arrayOf(
                        0.0f to Color.Transparent,
                        0.18f to GravityColors.NebulaViolet.copy(alpha = 0.72f),
                        0.45f to GravityColors.ElectricCyan.copy(alpha = 0.95f),
                        0.58f to Color.White.copy(alpha = 0.92f),
                        0.72f to GravityColors.PlasmaBlue.copy(alpha = 0.55f),
                        1.0f to Color.Transparent
                    ),
                    center = center
                ),
                startAngle = 0f,
                sweepAngle = 310f,
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcRectSize,
                style = Stroke(width = (orbRadius * 0.16f), cap = StrokeCap.Round),
                blendMode = BlendMode.Screen
            )
        }

        // 5. Counter-rotating inner fluid wave ribbon (iconic swirling plasma silk)
        rotate(degrees = counterAngle, pivot = center) {
            val wavePath = Path()
            val segments = 36
            val baseR = orbRadius * 0.76f
            for (i in 0..segments) {
                val theta = (i.toFloat() / segments) * (2.0 * PI)
                val wobble = sin(theta * 3.0 + (pulse * PI)).toFloat() * (orbRadius * 0.14f)
                val r = baseR + wobble
                val px = center.x + (r * cos(theta)).toFloat()
                val py = center.y + (r * sin(theta)).toFloat()
                if (i == 0) wavePath.moveTo(px, py) else wavePath.lineTo(px, py)
            }
            wavePath.close()

            drawPath(
                path = wavePath,
                brush = Brush.linearGradient(
                    colors = listOf(
                        GravityColors.ElectricCyan.copy(alpha = 0.85f),
                        GravityColors.SoftViolet.copy(alpha = 0.65f),
                        Color.White.copy(alpha = 0.85f),
                        GravityColors.PlasmaBlue.copy(alpha = 0.45f)
                    ),
                    start = Offset(center.x - orbRadius, center.y - orbRadius),
                    end = Offset(center.x + orbRadius, center.y + orbRadius)
                ),
                style = Stroke(width = (orbRadius * 0.10f), cap = StrokeCap.Round),
                blendMode = BlendMode.Screen
            )
        }

        // 6. Luminous caustic plasma crescent (gives 3D glossy depth)
        rotate(degrees = primaryAngle * 0.65f, pivot = center) {
            val crescentCenter = Offset(
                center.x + orbRadius * 0.24f,
                center.y + orbRadius * 0.20f
            )
            val crescentRadius = (orbRadius * 0.72f).coerceAtLeast(1f)
            drawCircle(
                brush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0.0f to Color.White.copy(alpha = if (state == OrbState.SPEAKING) 0.55f else 0.35f),
                        0.45f to GravityColors.ElectricCyan.copy(alpha = 0.42f),
                        0.78f to GravityColors.NebulaViolet.copy(alpha = 0.20f),
                        1.0f to Color.Transparent
                    ),
                    center = crescentCenter,
                    radius = crescentRadius
                ),
                radius = crescentRadius,
                center = crescentCenter,
                blendMode = BlendMode.Screen
            )
        }

        // 7. Crisp glass outer rim highlight
        drawCircle(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.75f),
                    GravityColors.ElectricCyan.copy(alpha = 0.55f),
                    GravityColors.NebulaViolet.copy(alpha = 0.45f),
                    Color.White.copy(alpha = 0.65f)
                ),
                start = Offset(center.x - orbRadius, center.y - orbRadius),
                end = Offset(center.x + orbRadius, center.y + orbRadius)
            ),
            radius = orbRadius,
            center = center,
            style = Stroke(width = 1.6.dp.toPx())
        )
    }
}
