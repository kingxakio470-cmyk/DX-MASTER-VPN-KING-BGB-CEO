package com.dxmaster.vpnking

import android.content.Context
import android.graphics.*
import android.view.View
import kotlin.math.min

class MicView(c: Context) : View(c) {
    var c1 = 0xFF00F0FF.toInt()
    var c2 = 0xFFFF2BD6.toInt()
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private var t = 0f

    init { setLayerType(LAYER_TYPE_SOFTWARE, null) }

    override fun onDraw(cv: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val cx = w / 2; val cy = h * 0.42f
        val on = MicEngine.running
        val lv = MicEngine.level
        t += 1.5f
        val a = if (on) c1 else 0xFF4A5068.toInt()
        val b = if (on) c2 else 0xFF353A50.toInt()

        p.style = Paint.Style.STROKE; p.strokeCap = Paint.Cap.ROUND
        val r0 = min(w, h) * 0.38f
        p.strokeWidth = 3f; p.color = a; p.setShadowLayer(16f, 0f, 0f, a)
        cv.drawCircle(cx, cy, r0, p)
        p.strokeWidth = 6f; p.color = b; p.setShadowLayer(20f, 0f, 0f, b)
        val rr = RectF(cx - r0 - 16, cy - r0 - 16, cx + r0 + 16, cy + r0 + 16)
        cv.drawArc(rr, t, 40f + lv * 260f, false, p)
        cv.drawArc(rr, t + 180f, 40f + lv * 260f, false, p)

        val hw = w * 0.12f
        val head = RectF(cx - hw, cy - h * 0.2f, cx + hw, cy + h * 0.02f)
        p.clearShadowLayer(); p.style = Paint.Style.FILL; p.color = 0xFF0B1024.toInt()
        cv.drawRoundRect(head, hw, hw, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = 4f; p.color = a; p.setShadowLayer(14f, 0f, 0f, a)
        cv.drawRoundRect(head, hw, hw, p)
        p.strokeWidth = 2f
        for (i in 1..6) {
            val y = head.top + head.height() * i / 7f
            cv.drawLine(head.left + hw * 0.4f, y, head.right - hw * 0.4f, y, p)
        }
        p.strokeWidth = 4f; p.color = b; p.setShadowLayer(14f, 0f, 0f, b)
        cv.drawArc(RectF(cx - w * 0.17f, cy - h * 0.1f, cx + w * 0.17f, cy + h * 0.12f), 0f, 180f, false, p)
        cv.drawLine(cx, cy + h * 0.12f, cx, cy + h * 0.2f, p)
        cv.drawLine(cx - w * 0.12f, cy + h * 0.2f, cx + w * 0.12f, cy + h * 0.2f, p)

        p.style = Paint.Style.FILL; p.textAlign = Paint.Align.CENTER
        p.typeface = Typeface.DEFAULT_BOLD; p.letterSpacing = 0.1f
        p.color = a; p.setShadowLayer(18f, 0f, 0f, a)
        p.textSize = w * 0.058f
        cv.drawText("DX MASTER VPN KING", cx, h * 0.86f, p)
        p.textSize = w * 0.035f; p.color = b; p.setShadowLayer(10f, 0f, 0f, b)
        cv.drawText(if (on) "● LIVE - TAP TO STOP" else "TAP TO START", cx, h * 0.94f, p)
        p.letterSpacing = 0f
        postInvalidateOnAnimation()
    }
}
