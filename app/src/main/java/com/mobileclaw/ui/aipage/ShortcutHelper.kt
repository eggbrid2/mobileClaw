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

        val icon = createEmojiIcon(context, def.icon)
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

    private fun createEmojiIcon(context: Context, emoji: String): Bitmap {
        val size = 192
        val bm = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bm)

        // Rounded background
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#1A73E8")
        }
        val rect = RectF(0f, 0f, size.toFloat(), size.toFloat())
        canvas.drawRoundRect(rect, 44f, 44f, bgPaint)

        // Emoji text
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 96f
            textAlign = Paint.Align.CENTER
        }
        val textBounds = android.graphics.Rect()
        textPaint.getTextBounds(emoji, 0, emoji.length, textBounds)
        val x = size / 2f
        val y = size / 2f - textBounds.exactCenterY()
        canvas.drawText(emoji, x, y, textPaint)

        return bm
    }
}
