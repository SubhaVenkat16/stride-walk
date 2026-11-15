package com.stride.walk

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.graphics.ColorUtils
import java.io.File
import java.io.FileOutputStream
import java.time.format.DateTimeFormatter

/** Draws the shareable "I walked X steps" card as a picture. */
object ShareCard {
    const val THEME_COUNT = 4
    private const val W = 1080
    private const val H = 1350

    private val themes = arrayOf(
        intArrayOf(0xFFFFB36B.toInt(), 0xFFFF5C7A.toInt()), // sunrise
        intArrayOf(0xFF6DD5FA.toInt(), 0xFF2B6CB0.toInt()), // ocean sky
        intArrayOf(0xFF9BE08F.toInt(), 0xFF1F7A4D.toInt()), // forest
        intArrayOf(0xFFB39DDB.toInt(), 0xFF2E3A8C.toInt())  // twilight
    )

    fun render(walk: Walk, themeIdx: Int, photoPath: String?, badge: String?): Bitmap {
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val th = themes[Math.floorMod(themeIdx, themes.size)]
        val photo = photoPath?.let { BitmapUtil.decode(it, 1600) }

        if (photo != null) {
            drawCropped(c, photo)
            p.shader = LinearGradient(
                0f, 0f, 0f, H.toFloat(),
                Color.argb(70, 0, 0, 0), Color.argb(200, 0, 0, 0), Shader.TileMode.CLAMP
            )
            c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), p)
            p.shader = null
        } else {
            drawScene(c, p, th)
        }

        val shadow = Color.argb(110, 0, 0, 0)
        val t = Paint(Paint.ANTI_ALIAS_FLAG)
        t.color = Color.WHITE
        t.textAlign = Paint.Align.CENTER
        t.setShadowLayer(10f, 0f, 4f, shadow)

        // Header
        t.typeface = Typeface.DEFAULT_BOLD
        t.textSize = 46f
        t.letterSpacing = 0.2f
        c.drawText("🚶 STRIDE", W / 2f, 130f, t)
        t.letterSpacing = 0f
        t.typeface = Typeface.DEFAULT
        t.textSize = 36f
        t.alpha = 220
        c.drawText(walk.date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM")), W / 2f, 190f, t)
        t.alpha = 255

        // Big step count
        t.typeface = Typeface.DEFAULT_BOLD
        t.textSize = 230f
        c.drawText(fmtSteps(walk.steps), W / 2f, 470f, t)
        t.typeface = Typeface.DEFAULT
        t.textSize = 56f
        t.letterSpacing = 0.3f
        c.drawText("STEPS", W / 2f, 550f, t)
        t.letterSpacing = 0f

        // Stats row
        val stats = listOf(
            Pair(fmtDuration(walk.durationSec), "TIME"),
            Pair("%.2f km".format(walk.distanceKm), "DISTANCE"),
            Pair("${walk.calories}", "KCAL")
        )
        stats.forEachIndexed { i, s ->
            val x = W * (0.2f + 0.3f * i)
            t.typeface = Typeface.DEFAULT_BOLD
            t.textSize = 62f
            c.drawText(s.first, x, 680f, t)
            t.typeface = Typeface.DEFAULT
            t.textSize = 30f
            t.alpha = 210
            c.drawText(s.second, x, 725f, t)
            t.alpha = 255
        }

        // Quote
        val quote = Quotes.pick(walk.id)
        val tp = TextPaint(Paint.ANTI_ALIAS_FLAG)
        tp.color = Color.WHITE
        tp.textSize = 56f
        tp.typeface = Typeface.create(Typeface.SERIF, Typeface.ITALIC)
        tp.setShadowLayer(10f, 0f, 4f, shadow)
        val layout = StaticLayout.Builder.obtain("“$quote”", 0, quote.length + 2, tp, 860)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setLineSpacing(0f, 1.15f)
            .build()
        c.save()
        c.translate((W - 860) / 2f, 840f)
        layout.draw(c)
        c.restore()

        // Badge pill
        if (badge != null) {
            val bp = Paint(Paint.ANTI_ALIAS_FLAG)
            bp.color = Color.argb(60, 255, 255, 255)
            c.drawRoundRect(RectF(160f, 1110f, W - 160f, 1190f), 40f, 40f, bp)
            t.textSize = 38f
            t.typeface = Typeface.DEFAULT_BOLD
            c.drawText("🏅 Badge unlocked: $badge", W / 2f, 1163f, t)
        }

        // Footer
        t.typeface = Typeface.DEFAULT
        t.textSize = 36f
        t.alpha = 230
        c.drawText("#WalkWithStride", W / 2f, H - 60f, t)
        return bmp
    }

    private fun drawCropped(c: Canvas, b: Bitmap) {
        val s = maxOf(W.toFloat() / b.width, H.toFloat() / b.height)
        val dw = b.width * s
        val dh = b.height * s
        val dst = RectF((W - dw) / 2f, (H - dh) / 2f, (W + dw) / 2f, (H + dh) / 2f)
        c.drawBitmap(b, null, dst, Paint(Paint.FILTER_BITMAP_FLAG))
    }

    private fun drawScene(c: Canvas, p: Paint, th: IntArray) {
        p.shader = LinearGradient(0f, 0f, 0f, H.toFloat(), th[0], th[1], Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), p)
        p.shader = null

        // Sun glow
        p.color = Color.argb(70, 255, 255, 255)
        c.drawCircle(W * 0.78f, H * 0.14f, 150f, p)
        p.color = Color.argb(150, 255, 255, 255)
        c.drawCircle(W * 0.78f, H * 0.14f, 90f, p)

        // Rolling hills
        fun hill(color: Int, baseY: Float, c1: Float, c2: Float, endY: Float) {
            val path = Path()
            path.moveTo(0f, baseY)
            path.cubicTo(W * 0.3f, c1, W * 0.65f, c2, W.toFloat(), endY)
            path.lineTo(W.toFloat(), H.toFloat())
            path.lineTo(0f, H.toFloat())
            path.close()
            p.color = color
            c.drawPath(path, p)
        }
        hill(ColorUtils.blendARGB(th[1], Color.BLACK, 0.20f), H * 0.80f, H * 0.70f, H * 0.90f, H * 0.78f)
        hill(ColorUtils.blendARGB(th[1], Color.BLACK, 0.40f), H * 0.88f, H * 0.82f, H * 0.95f, H * 0.86f)

        // Little trees
        val treeColor = ColorUtils.blendARGB(th[1], Color.BLACK, 0.60f)
        p.color = treeColor
        listOf(110f to 0.915f, 200f to 0.93f, 930f to 0.92f, 1010f to 0.935f).forEach { (x, yr) ->
            val y = H * yr
            val tree = Path()
            tree.moveTo(x, y - 110f)
            tree.lineTo(x - 38f, y)
            tree.lineTo(x + 38f, y)
            tree.close()
            c.drawPath(tree, p)
            c.drawRect(x - 6f, y, x + 6f, y + 22f, p)
        }
    }

    fun share(ctx: Context, walk: Walk, themeIdx: Int, photoPath: String?, badge: String?) {
        val bmp = render(walk, themeIdx, photoPath, badge)
        val dir = File(ctx.cacheDir, "share")
        dir.mkdirs()
        val file = File(dir, "stride_${walk.id}.png")
        FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = uriFor(ctx, file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, "I walked ${fmtSteps(walk.steps)} steps with Stride! #WalkWithStride")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(Intent.createChooser(send, "Share your walk"))
    }
}
