package uz.callguard.app.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import uz.callguard.app.R

@SuppressLint("StaticFieldLeak", "InflateParams")
object OverlayWarningTrigger {
    private val main = Handler(Looper.getMainLooper())
    private var floatingView: View? = null
    private var manager: WindowManager? = null
    private var dismiss: Runnable? = null

    fun show(context: Context, number: String, score: Int, reasons: List<String>, category: String? = null) {
        val app = context.applicationContext
        main.post {
            hideNow()
            if (!Settings.canDrawOverlays(app)) return@post
            try {
                val wm = app.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                val view = LayoutInflater.from(app).inflate(R.layout.overlay_warning, null)
                view.findViewById<TextView>(R.id.tvNumber).text = app.getString(R.string.overlay_warning_number, number)
                val categoryText = when (category) {
                    "advertising" -> app.getString(R.string.category_advertising)
                    "fraud" -> app.getString(R.string.category_fraud)
                    "unknown" -> app.getString(R.string.category_unknown)
                    else -> category
                }
                view.findViewById<TextView>(R.id.tvDetails).text = if (categoryText != null)
                    app.getString(R.string.category_warning, categoryText) else app.getString(R.string.heuristic_warning)
                view.findViewById<View>(R.id.btnClose).setOnClickListener { hideNow() }
                val params = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                    PixelFormat.TRANSLUCENT
                ).apply { gravity = Gravity.TOP; y = (48 * app.resources.displayMetrics.density).toInt() }
                wm.addView(view, params)
                manager = wm
                floatingView = view
                dismiss = Runnable { if (floatingView === view) hideNow() }.also { main.postDelayed(it, 10_000) }
            } catch (error: Exception) {
                Log.e("CallGuard", "Cannot display caller warning", error)
                hideNow()
            }
        }
    }

    private fun hideNow() {
        dismiss?.let(main::removeCallbacks)
        dismiss = null
        floatingView?.let { view -> runCatching { manager?.removeView(view) } }
        floatingView = null
        manager = null
    }

    fun hide(context: Context) { main.post { hideNow() } }
    fun remove(windowManager: WindowManager) { main.post { hideNow() } }
}
