package com.mobileclaw.ui.aipage

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat

/** Creates a pinned launcher shortcut for an AI page. */
object ShortcutHelper {

    fun pinShortcut(context: Context, def: AiPageDef): Boolean {
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(context)) return false

        val icon = createPageIcon(def.icon)
        val intent = AiPageActivity.intent(context, def.id).apply {
            action = Intent.ACTION_VIEW
        }

        val shortcut = ShortcutInfoCompat.Builder(context, "aipage_${def.id}")
            .setShortLabel(def.title.take(25))
            .setLongLabel(def.title)
            .setIcon(IconCompat.createWithBitmap(icon))
            .setIntent(intent)
            .build()

        return runCatching {
            ShortcutManagerCompat.requestPinShortcut(context, shortcut, null)
            true
        }.getOrDefault(false)
    }

    private fun createPageIcon(iconKey: String): Bitmap {
        val size = 192
        val bm = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bm)

        // Rounded background
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#111111")
        }
        val rect = RectF(0f, 0f, size.toFloat(), size.toFloat())
        canvas.drawRoundRect(rect, 44f, 44f, bgPaint)

        val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#C7F43A")
            strokeWidth = 12f
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val whitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            strokeWidth = 10f
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }

        val key = iconKey.trim().lowercase()
        if (key.contains("chat") || key.contains("message") || key == "💬") {
            canvas.drawRoundRect(RectF(48f, 54f, 144f, 126f), 24f, 24f, whitePaint)
            canvas.drawLine(78f, 126f, 64f, 148f, whitePaint)
        } else {
            canvas.drawRoundRect(RectF(58f, 42f, 134f, 150f), 12f, 12f, whitePaint)
            canvas.drawLine(76f, 74f, 116f, 74f, accentPaint)
            canvas.drawLine(76f, 100f, 116f, 100f, whitePaint)
            canvas.drawLine(76f, 126f, 104f, 126f, whitePaint)
        }

        return bm
    }
}
