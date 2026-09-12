package com.example.platinumcleaner.service

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.example.platinumcleaner.domain.cleaning.InteractiveQueue
import com.example.platinumcleaner.domain.verification.VerificationEngine
import com.example.platinumcleaner.platform.cleaning.InteractiveCleanManager
import com.example.platinumcleaner.util.OverlayPermissionHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * FloatingAssistantService — Floating Assistant Bar over Android Settings (Opsi 1 / Sprint V10).
 *
 * Menampilkan kartu mengambang elegan di atas layar Pengaturan Android untuk memandu
 * pengguna dan menyediakan tombol navigasi cepat [Lewati] dan [Lanjut ke Aplikasi Berikutnya ➔].
 */
class FloatingAssistantService : Service() {

    companion object {
        private const val TAG = "FloatingAssistantSvc"

        fun start(context: Context) {
            if (OverlayPermissionHelper.canDrawOverlays(context)) {
                val intent = Intent(context, FloatingAssistantService::class.java)
                try {
                    context.startService(intent)
                } catch (e: Exception) {
                    Log.e(TAG, "Gagal memulai FloatingAssistantService: ${e.message}")
                }
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, FloatingAssistantService::class.java)
            try {
                context.stopService(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Gagal menghentikan FloatingAssistantService: ${e.message}")
            }
        }
    }

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())

    // UI elements
    private var tvTitle: TextView? = null
    private var tvSubtitle: TextView? = null
    private var btnSkip: Button? = null
    private var btnNext: Button? = null
    private var btnClose: TextView? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        if (!OverlayPermissionHelper.canDrawOverlays(this)) {
            Log.w(TAG, "Overlay permission not granted, stopping service")
            stopSelf()
            return
        }

        setupFloatingView()
        observeQueue()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupFloatingView() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = dpToPx(36) // Elevasi di atas navigation bar
        }

        val rootLayout = createFloatingCardLayout(params)
        floatingView = rootLayout

        try {
            windowManager?.addView(rootLayout, params)
            Log.d(TAG, "Floating Assistant Bar berhasil ditambahkan ke WindowManager")
        } catch (e: Exception) {
            Log.e(TAG, "Gagal menambahkan Floating View: ${e.message}")
            stopSelf()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createFloatingCardLayout(params: WindowManager.LayoutParams): View {
        val root = FrameLayout(this).apply {
            setPadding(dpToPx(16), 0, dpToPx(16), dpToPx(12))
        }

        // Card Container: Dark Charcoal Quiet Luxury background (#1E1E22) with subtle 1dp border (#333338)
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(18), dpToPx(14), dpToPx(18), dpToPx(14))

            val bg = GradientDrawable().apply {
                setColor(Color.parseColor("#1C1C1E")) // Quiet Luxury Deep Charcoal
                cornerRadius = dpToPx(20).toFloat()
                setStroke(dpToPx(1), Color.parseColor("#3A3A3C"))
            }
            background = bg
            elevation = dpToPx(8).toFloat()
        }

        // Header Row (Drag handle / icon + Title + Close button)
        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        tvTitle = TextView(this).apply {
            text = "🧹 Asisten Pembersihan"
            setTextColor(Color.parseColor("#FFFFFF"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        btnClose = TextView(this).apply {
            text = "✕"
            setTextColor(Color.parseColor("#8E8E93"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setPadding(dpToPx(8), dpToPx(4), dpToPx(4), dpToPx(4))
            setOnClickListener {
                InteractiveCleanManager.stopSession(applicationContext)
                stopSelf()
            }
        }

        headerRow.addView(tvTitle)
        headerRow.addView(btnClose)
        card.addView(headerRow)

        // Subtitle / Guidance
        tvSubtitle = TextView(this).apply {
            text = "Ketuk 'Penyimpanan' lalu 'Hapus Memori'"
            setTextColor(Color.parseColor("#EBEBF5"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(0, dpToPx(4), 0, dpToPx(12))
        }
        card.addView(tvSubtitle)

        // Action Buttons Row: [ Lewati ] and [ Lanjut ke [Next] ➔ ]
        val buttonRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        btnSkip = Button(this).apply {
            text = "Lewati"
            setTextColor(Color.parseColor("#A1A1A6"))
            setBackgroundColor(Color.TRANSPARENT)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            transformationMethod = null
            setOnClickListener {
                val next = InteractiveCleanManager.skipCurrent(applicationContext)
                if (next == null) stopSelf()
            }
        }

        val spacer = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
        }

        btnNext = Button(this).apply {
            text = "Lanjut ➔"
            setTextColor(Color.parseColor("#1C1C1E"))
            val btnBg = GradientDrawable().apply {
                setColor(Color.parseColor("#FFFFFF")) // Pure White Accent Button
                cornerRadius = dpToPx(14).toFloat()
            }
            background = btnBg
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.DEFAULT_BOLD
            transformationMethod = null
            setPadding(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(8))
            setOnClickListener {
                val next = InteractiveCleanManager.advanceToNext(applicationContext)
                if (next == null) stopSelf()
            }
        }

        buttonRow.addView(btnSkip)
        buttonRow.addView(spacer)
        buttonRow.addView(btnNext)
        card.addView(buttonRow)

        // Draggable gesture support so user can move it if it blocks Samsung's bottom toolbar
        var initialY = 0
        var initialTouchY = 0f

        card.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialY = params.y
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaY = (initialTouchY - event.rawY).toInt()
                    params.y = (initialY + deltaY).coerceIn(dpToPx(16), dpToPx(400))
                    windowManager?.updateViewLayout(root, params)
                    true
                }
                else -> false
            }
        }

        root.addView(card)
        return root
    }

    private fun observeQueue() {
        serviceScope.launch {
            InteractiveCleanManager.activeQueue.collectLatest { queue ->
                if (queue == null || queue.isCompleted || queue.isStopped) {
                    stopSelf()
                    return@collectLatest
                }
                updateUiForQueue(queue)
            }
        }
    }

    private fun updateUiForQueue(queue: InteractiveQueue) {
        val target = queue.currentTarget ?: return
        val currentNumber = queue.currentIndex + 1
        val total = queue.totalTargets
        val cacheStr = VerificationEngine.formatBytes(target.cacheBytesBefore)

        tvTitle?.text = "🧹 $currentNumber/$total: ${target.appLabel} ($cacheStr)"

        val next = queue.nextTarget
        if (next != null) {
            btnNext?.text = "Lanjut ke ${next.appLabel.take(10)} ➔"
            btnSkip?.visibility = View.VISIBLE
        } else {
            btnNext?.text = "Selesai ➔"
            btnSkip?.visibility = View.GONE
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        floatingView?.let { view ->
            try {
                windowManager?.removeView(view)
            } catch (e: Exception) {
                Log.e(TAG, "Error saat menghapus floating view: ${e.message}")
            }
        }
        floatingView = null
        Log.d(TAG, "FloatingAssistantService destroyed")
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }
}
