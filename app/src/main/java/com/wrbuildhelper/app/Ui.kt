package com.wrbuildhelper.app

import android.animation.ValueAnimator
import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat

class Palette(
    val bg: Int, val card: Int, val tx: Int, val mu: Int,
    val ac: Int, val bd: Int, val rd: Int, val gn: Int
) {
    companion object {
        private fun h(v: Long): Int = v.toInt()
        val LIGHT = Palette(h(0xFFF4F5F8), h(0xFFFFFFFF), h(0xFF14161C), h(0xFF666677), h(0xFF2F6BFF), h(0xFFDDE1EA), h(0xFFE5484D), h(0xFF18A058))
        val DARK = Palette(h(0xFF0E1118), h(0xFF181C26), h(0xFFEEF0F5), h(0xFF98A0B3), h(0xFF5B8CFF), h(0xFF2A3040), h(0xFFFF6B70), h(0xFF3ECF86))
        val OVERLAY = Palette(h(0xF20E1118), h(0xFF181C26), h(0xFFEEF0F5), h(0xFF98A0B3), h(0xFF5B8CFF), h(0xFF2A3040), h(0xFFFF6B70), h(0xFF3ECF86))

        fun of(ctx: Context): Palette {
            val night = ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            return if (night == Configuration.UI_MODE_NIGHT_YES) DARK else LIGHT
        }
    }
}

object Ui {
    fun dp(ctx: Context, v: Float): Int = (v * ctx.resources.displayMetrics.density + 0.5f).toInt()

    fun rounded(color: Int, radius: Float, stroke: Int = 0, strokeW: Int = 0, dashed: Boolean = false): GradientDrawable {
        val g = GradientDrawable()
        g.shape = GradientDrawable.RECTANGLE
        g.setColor(color)
        g.cornerRadius = radius
        if (strokeW > 0) {
            if (dashed) g.setStroke(strokeW, stroke, strokeW * 4f, strokeW * 3f) else g.setStroke(strokeW, stroke)
        }
        return g
    }

    fun text(ctx: Context, s: CharSequence, sp: Float, color: Int, bold: Boolean = false): TextView {
        val t = TextView(ctx)
        t.text = s
        t.textSize = sp
        t.setTextColor(color)
        if (bold) t.setTypeface(t.typeface, Typeface.BOLD)
        return t
    }

    fun icon(ctx: Context, res: Int, tint: Int): Drawable {
        val d = ContextCompat.getDrawable(ctx, res)!!.mutate()
        d.setTint(tint)
        return d
    }

    fun pressable(v: View) {
        v.setOnTouchListener { view, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> view.animate().scaleX(0.95f).scaleY(0.95f).setDuration(90).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> view.animate().scaleX(1f).scaleY(1f).setDuration(140).start()
            }
            false
        }
    }

    fun chip(ctx: Context, pal: Palette, label: String, on: Boolean, small: Boolean = false, onClick: () -> Unit): TextView {
        val t = text(ctx, label, if (small) 12f else 13f, if (on) 0xFFFFFFFF.toInt() else pal.tx)
        val ph = dp(ctx, if (small) 9f else 12f)
        val pv = dp(ctx, if (small) 5f else 7f)
        t.setPadding(ph, pv, ph, pv)
        t.background = if (on) rounded(pal.ac, dp(ctx, 20f).toFloat(), pal.ac, dp(ctx, 1f))
        else rounded(pal.card, dp(ctx, 20f).toFloat(), pal.bd, dp(ctx, 1f))
        t.setOnClickListener { onClick() }
        pressable(t)
        return t
    }

    fun card(ctx: Context, pal: Palette): LinearLayout {
        val l = LinearLayout(ctx)
        l.orientation = LinearLayout.VERTICAL
        l.setPadding(dp(ctx, 14f), dp(ctx, 14f), dp(ctx, 14f), dp(ctx, 14f))
        l.background = rounded(pal.card, dp(ctx, 14f).toFloat(), pal.bd, dp(ctx, 1f))
        return l
    }

    fun lp(w: Int = ViewGroup.LayoutParams.MATCH_PARENT, h: Int = ViewGroup.LayoutParams.WRAP_CONTENT): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(w, h)

    fun fadeIn(v: View, delay: Long = 0) {
        v.alpha = 0f
        v.translationY = dp(v.context, 10f).toFloat()
        v.animate().alpha(1f).translationY(0f).setStartDelay(delay).setDuration(240).start()
    }
}

/** Layout que quebra linha automaticamente (para os "chips"). */
class FlowLayout(ctx: Context, private val hGap: Int, private val vGap: Int) : ViewGroup(ctx) {
    override fun onMeasure(wSpec: Int, hSpec: Int) {
        val maxW = MeasureSpec.getSize(wSpec) - paddingLeft - paddingRight
        var x = 0
        var y = 0
        var rowH = 0
        for (i in 0 until childCount) {
            val c = getChildAt(i)
            if (c.visibility == GONE) continue
            c.measure(MeasureSpec.makeMeasureSpec(maxW, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
            val w = c.measuredWidth
            val h = c.measuredHeight
            if (x > 0 && x + w > maxW) {
                x = 0
                y += rowH + vGap
                rowH = 0
            }
            x += w + hGap
            rowH = maxOf(rowH, h)
        }
        val total = y + rowH + paddingTop + paddingBottom
        setMeasuredDimension(resolveSize(MeasureSpec.getSize(wSpec), wSpec), resolveSize(total, hSpec))
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val maxW = r - l - paddingLeft - paddingRight
        var x = 0
        var y = 0
        var rowH = 0
        for (i in 0 until childCount) {
            val c = getChildAt(i)
            if (c.visibility == GONE) continue
            val w = c.measuredWidth
            val h = c.measuredHeight
            if (x > 0 && x + w > maxW) {
                x = 0
                y += rowH + vGap
                rowH = 0
            }
            val left = paddingLeft + x
            val top = paddingTop + y
            c.layout(left, top, left + w, top + h)
            x += w + hGap
            rowH = maxOf(rowH, h)
        }
    }
}

/** Barra animada de duas cores. */
class BarView(ctx: Context, private val bgc: Int, private val fgc: Int, private val frac: Float) : View(ctx) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private var prog = 0f
    private val anim: ValueAnimator = ValueAnimator.ofFloat(0f, 1f)

    init {
        anim.duration = 550
        anim.interpolator = DecelerateInterpolator()
        anim.addUpdateListener {
            prog = it.animatedValue as Float
            invalidate()
        }
        anim.start()
    }

    override fun onDetachedFromWindow() {
        anim.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val r = h / 2f
        path.reset()
        path.addRoundRect(RectF(0f, 0f, w, h), r, r, Path.Direction.CW)
        canvas.save()
        canvas.clipPath(path)
        paint.color = bgc
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.color = fgc
        canvas.drawRect(0f, 0f, w * frac.coerceIn(0f, 1f) * prog, h, paint)
        canvas.restore()
    }
}
