package com.personal.gravity

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class GravityOrbView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    enum class OrbState { PASSIVE, AWAKE, EXECUTING }

    private data class OrbitalParticle(
        var angle: Float,
        var distanceFactor: Float,
        var speed: Float,
        var size: Float,
        var alpha: Int
    )

    private val particles = List(38) {
        OrbitalParticle(
            angle = Random.nextFloat() * 360f,
            distanceFactor = 0.55f + Random.nextFloat() * 0.85f,
            speed = 0.3f + Random.nextFloat() * 0.85f,
            size = 2f + Random.nextFloat() * 4f,
            alpha = 95 + Random.nextInt(160)
        )
    }

    private val corePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val wavePath = Path()

    private var phase = 0f
    private var smoothedEnergy = 0.12f
    private var currentState = OrbState.PASSIVE

    fun updateAudioLevel(rmsdB: Float) {
        val target = ((rmsdB + 2f) / 11f).coerceIn(0.08f, 1.35f)
        smoothedEnergy += (target - smoothedEnergy) * 0.28f
    }

    fun setOrbState(state: OrbState) {
        currentState = state
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val minDim = width.coerceAtMost(height).toFloat()
        if (minDim <= 0f) return
        val baseRadius = minDim * 0.24f

        val speedMultiplier = when (currentState) {
            OrbState.AWAKE -> 2.5f
            OrbState.EXECUTING -> 3.4f
            OrbState.PASSIVE -> 0.85f
        }
        phase += 0.045f * speedMultiplier

        val primaryColor = when (currentState) {
            OrbState.AWAKE -> Color.parseColor("#00F0FF")
            OrbState.EXECUTING -> Color.parseColor("#10B981")
            OrbState.PASSIVE -> Color.parseColor("#38BDF8")
        }
        val secondaryColor = when (currentState) {
            OrbState.AWAKE -> Color.parseColor("#818CF8")
            OrbState.EXECUTING -> Color.parseColor("#34D399")
            OrbState.PASSIVE -> Color.parseColor("#334155")
        }

        // Only draw particles if view is large enough (skip for tiny overlay)
        if (minDim > 180f) {
            for (p in particles) {
                p.angle = (p.angle + p.speed * speedMultiplier) % 360f
                val rad = Math.toRadians(p.angle.toDouble())
                val orbitR = baseRadius * p.distanceFactor * (1f + smoothedEnergy * 0.14f)
                val px = cx + orbitR * cos(rad).toFloat()
                val py = cy + orbitR * sin(rad).toFloat()
                particlePaint.color = primaryColor
                particlePaint.alpha = p.alpha
                canvas.drawCircle(px, py, p.size, particlePaint)
            }
        }

        // Gyroscopic Elliptical Orbit
        canvas.save()
        canvas.rotate(phase * 18f, cx, cy)
        ringPaint.strokeWidth = (minDim * 0.007f).coerceAtLeast(1.4f)
        ringPaint.color = secondaryColor
        ringPaint.alpha = 130
        val oval = RectF(
            cx - baseRadius * 1.42f,
            cy - baseRadius * 0.82f,
            cx + baseRadius * 1.42f,
            cy + baseRadius * 0.82f
        )
        canvas.drawOval(oval, ringPaint)
        canvas.restore()

        // 3 Harmonic Gravitational Wave Rings
        for (ring in 1..3) {
            wavePath.reset()
            val ringRadius = baseRadius * (0.85f + ring * 0.24f)
            ringPaint.strokeWidth = ((minDim * 0.013f) / ring).coerceAtLeast(1.3f)
            ringPaint.color = if (ring % 2 == 1) primaryColor else secondaryColor
            ringPaint.alpha = (230 / ring).coerceIn(60, 240)

            val harmonics = 3 + ring * 2
            for (deg in 0..360 step 5) {
                val rad = Math.toRadians(deg.toDouble())
                val wave = sin(rad * harmonics + phase * ring) *
                        cos(rad * (harmonics - 2) - phase) *
                        (baseRadius * 0.20f * smoothedEnergy)
                val r = ringRadius + wave.toFloat()
                val x = cx + r * cos(rad).toFloat()
                val y = cy + r * sin(rad).toFloat()
                if (deg == 0) wavePath.moveTo(x, y) else wavePath.lineTo(x, y)
            }
            wavePath.close()
            canvas.drawPath(wavePath, ringPaint)
        }

        // Singularity Core
        val coreRadius = baseRadius * (0.62f + smoothedEnergy * 0.18f)
        corePaint.shader = RadialGradient(
            cx, cy, coreRadius * 1.6f,
            intArrayOf(
                Color.parseColor("#FFFFFF"),
                primaryColor,
                secondaryColor,
                Color.TRANSPARENT
            ),
            floatArrayOf(0.08f, 0.42f, 0.75f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, coreRadius * 1.6f, corePaint)

        postInvalidateOnAnimation()
    }
}
