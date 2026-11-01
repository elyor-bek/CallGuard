package uz.callguard.app.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.core.graphics.toColorInt
import uz.callguard.app.R

@SuppressLint("StaticFieldLeak", "InflateParams")
object OverlayWarningTrigger {

    private var floatingView: View? = null

    fun show(context: Context, number: String, score: Int, reasons: List<String>) {
        if (!Settings.canDrawOverlays(context)) {
            Log.e("CallGuard", "Нет разрешения SYSTEM_ALERT_WINDOW для показа оверлея")
            return
        }

        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        remove(windowManager)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP
            x = 0
            y = 100
        }

        val view = LayoutInflater.from(context).inflate(R.layout.overlay_warning, null)

        val backgroundColor = when {
            score >= 60 -> "#D32F2F".toColorInt()
            score >= 30 -> "#F57C00".toColorInt()
            else -> "#388E3C".toColorInt()
        }
        view.setBackgroundColor(backgroundColor)

        val formattedNumber = context.getString(R.string.overlay_warning_number, number)
        val formattedDetails = context.getString(
            R.string.overlay_risk_details,
            score,
            reasons.joinToString(", ")
        )

        view.findViewById<TextView>(R.id.tvNumber)?.text = formattedNumber
        view.findViewById<TextView>(R.id.tvDetails)?.text = formattedDetails

        view.findViewById<View>(R.id.btnClose)?.setOnClickListener {
            remove(windowManager)
        }

        try {
            windowManager.addView(view, params)
            floatingView = view
        } catch (e: Exception) {
            Log.e("CallGuard", "Не удалось показать оверлей", e)
        }
    }

    fun remove(windowManager: WindowManager) {
        floatingView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                Log.e("CallGuard", "Ошибка при удалении оверлея", e)
            }
            floatingView = null
        }
    }

    fun hide(context: Context) {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        remove(windowManager)
    }
}
