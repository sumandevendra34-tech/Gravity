package com.gravityos.assistant.overlay

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import com.gravityos.assistant.MainActivity
import com.gravityos.assistant.ui.components.OrbState
import com.gravityos.assistant.voice.GravityVoiceHub
import com.gravityos.assistant.voice.GravityVoiceService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.abs

class FloatingOrbService : Service() {

    companion object {
        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

        fun start(context: Context): Boolean {
            if (!Settings.canDrawOverlays(context)) return false
            context.startService(Intent(context, FloatingOrbService::class.java))
            return true
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, FloatingOrbService::class.java))
        }
    }

    private var windowManager: WindowManager? = null
    private var orbView: FloatingOrbCanvasView? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private val uiScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onBind(intent: Intent?): IBinder? = null

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate() {
        super.onCreate()
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val density = resources.displayMetrics.density
        val orbSizePx = (68 * density).toInt()

        val params = WindowManager.LayoutParams(
            orbSizePx,
            orbSizePx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = resources.displayMetrics.widthPixels - orbSizePx - (14 * density).toInt()
            y = (resources.displayMetrics.heightPixels * 0.42f).toInt()
        }
        layoutParams = params

        val view = FloatingOrbCanvasView(this)
        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop
        var initialX = 0
        var initialY = 0
        var touchStartX = 0f
        var touchStartY = 0f
        var downTime = 0L
        var moved = false
        var longPressTriggered = false

        val longPressRunnable = Runnable {
            if (!moved) {
                longPressTriggered = true
                openMainApp()
            }
        }

        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    touchStartX = event.rawX
                    touchStartY = event.rawY
                    downTime = System.currentTimeMillis()
                    moved = false
                    longPressTriggered = false
                    v.postDelayed(longPressRunnable, 550)
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - touchStartX).toInt()
                    val dy = (event.rawY - touchStartY).toInt()
                    if (abs(dx) > touchSlop || abs(dy) > touchSlop) {
                        moved = true
                        v.removeCallbacks(longPressRunnable)
                    }
                    if (moved) {
                        params.x = initialX + dx
                        params.y = initialY + dy
                        try {
                            windowManager?.updateViewLayout(v, params)
                        } catch (_: Throwable) {
                        }
                    }
                    true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.removeCallbacks(longPressRunnable)
                    if (!moved && !longPressTriggered && event.action == MotionEvent.ACTION_UP) {
                        val elapsed = System.currentTimeMillis() - downTime
                        if (elapsed < 550) {
                            onOrbSingleTap()
                        }
                    } else if (moved) {
                        snapToNearestEdge(v, params, orbSizePx, density)
                    }
                    true
                }

                else -> false
            }
        }

        try {
            windowManager?.addView(view, params)
            orbView = view
            _isRunning.value = true
            observeVoiceState(view)
        } catch (_: Throwable) {
            _isRunning.value = false
            stopSelf()
        }
    }

    private fun observeVoiceState(view: FloatingOrbCanvasView) {
        uiScope.launch {
            GravityVoiceHub.state.collect { state ->
                view.updateState(state.orbState, state.voiceAmplitude)
            }
        }
    }

    private fun onOrbSingleTap() {
        val current = GravityVoiceHub.state.value
        if (current.orbState == OrbState.SPEAKING) {
            GravityVoiceHub.interruptSpeech()
            return
        }
        if (current.isWakeServiceRunning) {
            GravityVoiceService.triggerListenNow(applicationContext)
        } else {
            GravityVoiceHub.triggerSingleShotListen(applicationContext)
        }
    }

    private fun openMainApp() {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(intent)
    }

    private fun snapToNearestEdge(
        view: View,
        params: WindowManager.LayoutParams,
        orbSizePx: Int,
        density: Float
    ) {
        val screenW = resources.displayMetrics.widthPixels
        val margin = (10 * density).toInt()
        params.x = if (params.x + orbSizePx / 2 < screenW / 2) margin else screenW - orbSizePx - margin
        try {
            windowManager?.updateViewLayout(view, params)
        } catch (_: Throwable) {
        }
    }

    override fun onDestroy() {
        uiScope.cancel()
        orbView?.stopAnimation()
        try {
            orbView?.let { windowManager?.removeView(it) }
        } catch (_: Throwable) {
        }
        orbView = null
        _isRunning.value = false
        super.onDestroy()
    }
}

private class FloatingOrbCanvasView(context: Context) : View(context) {

    private var angle = 0f
    private var orbState = OrbState.IDLE
    private var amplitude = 0f

    private val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val swirlPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val arcRect = RectF()

    private val animator = ValueAnimator.ofFloat(0f, 360f).apply {
        duration = 5000L
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            angle = it.animatedValue as Float
            invalidate()
        }
    }

    init {
        animator.start()
    }

    fun updateState(newState: OrbState, newAmplitude: Float) {
        if (orbState != newState) {
            orbState = newState
            animator.duration = when (newState) {
                OrbState.IDLE -> 5500L
                OrbState.LISTENING -> 2200L
                OrbState.THINKING -> 1400L
                OrbState.SPEAKING -> 1900L
            }
        }
        amplitude = newAmplitude.coerceIn(0f, 1f)
        invalidate()
    }

    fun stopAnimation() {
        animator.cancel()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val minDim = minOf(width, height).toFloat()
        val pulseBoost = if (orbState == OrbState.IDLE) 0f else (0.05f + amplitude * 0.08f)
        val radius = minDim * (0.34f + pulseBoost)

        // Outer ambient halo
        val haloRadius = (radius * 1.45f).coerceAtLeast(1f)
        haloPaint.shader = RadialGradient(
            cx,
            cy,
            haloRadius,
            intArrayOf(
                Color.argb(if (orbState == OrbState.LISTENING) 150 else 95, 56, 189, 248),
                Color.argb(55, 124, 58, 237),
                Color.TRANSPARENT
            ),
            floatArrayOf(0.4f, 0.75f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, haloRadius, haloPaint)

        // Dark-core glowing sphere
        bodyPaint.shader = RadialGradient(
            cx - radius * 0.18f,
            cy - radius * 0.18f,
            (radius * 1.15f).coerceAtLeast(1f),
            intArrayOf(
                Color.parseColor("#040A18"),
                Color.parseColor("#0B1E47"),
                Color.parseColor("#1D4ED8"),
                Color.parseColor("#60A5FA")
            ),
            floatArrayOf(0f, 0.52f, 0.84f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, radius, bodyPaint)

        // Swirling plasma energy ribbon
        canvas.save()
        canvas.rotate(angle, cx, cy)
        swirlPaint.strokeWidth = radius * 0.22f
        swirlPaint.shader = SweepGradient(
            cx,
            cy,
            intArrayOf(
                Color.TRANSPARENT,
                Color.argb(200, 124, 58, 237),
                Color.argb(245, 56, 189, 248),
                Color.WHITE,
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, 0.25f, 0.55f, 0.72f, 1f)
        )
        val arcR = radius * 0.86f
        arcRect.set(cx - arcR, cy - arcR, cx + arcR, cy + arcR)
        canvas.drawArc(arcRect, 0f, 290f, false, swirlPaint)
        canvas.restore()

        // Crisp outer cyan/white rim
        rimPaint.strokeWidth = resources.displayMetrics.density * 1.4f
        rimPaint.color = when (orbState) {
            OrbState.LISTENING -> Color.parseColor("#7DD3FC")
            OrbState.THINKING -> Color.parseColor("#A78BFA")
            OrbState.SPEAKING -> Color.WHITE
            OrbState.IDLE -> Color.argb(190, 96, 165, 250)
        }
        canvas.drawCircle(cx, cy, radius, rimPaint)
    }
}
