package com.mobileclaw.perception

import kotlin.math.roundToInt

/**
 * Keeps the latest phone screenshot coordinate contract used by vision tools.
 * The model sees scaled screenshot pixels; actions must map those pixels back
 * to the physical display coordinate space used by Accessibility gestures.
 */
object PhoneScreenState {
    @Volatile
    private var latest: ScreenCoordinateSpace? = null

    fun update(space: ScreenCoordinateSpace) {
        latest = space
    }

    fun latest(): ScreenCoordinateSpace? = latest

    fun mapPoint(
        x: Float,
        y: Float,
        inputWidth: Float? = null,
        inputHeight: Float? = null,
    ): MappedPoint {
        val space = latest
        val fromW = inputWidth?.takeIf { it > 0f } ?: space?.imageWidth?.toFloat()
        val fromH = inputHeight?.takeIf { it > 0f } ?: space?.imageHeight?.toFloat()
        val toW = space?.screenWidth?.toFloat()
        val toH = space?.screenHeight?.toFloat()
        if (fromW == null || fromH == null || toW == null || toH == null) {
            return MappedPoint(x, y, false, "no latest screenshot coordinate space")
        }
        val mappedX = (x * toW / fromW).coerceIn(0f, toW)
        val mappedY = (y * toH / fromH).coerceIn(0f, toH)
        return MappedPoint(
            x = mappedX,
            y = mappedY,
            converted = fromW.roundToInt() != toW.roundToInt() || fromH.roundToInt() != toH.roundToInt(),
            note = "${fromW.roundToInt()}x${fromH.roundToInt()} -> ${toW.roundToInt()}x${toH.roundToInt()}",
        )
    }

    fun describe(): String {
        val s = latest ?: return "No screenshot coordinate space recorded yet."
        return "Image coordinate space: ${s.imageWidth}x${s.imageHeight}; device screen: ${s.screenWidth}x${s.screenHeight}. " +
            "Use the image coordinates from the latest phone observation. tap/scroll/long_click will map them to device pixels."
    }
}

data class ScreenCoordinateSpace(
    val imageWidth: Int,
    val imageHeight: Int,
    val screenWidth: Int,
    val screenHeight: Int,
)

data class MappedPoint(
    val x: Float,
    val y: Float,
    val converted: Boolean,
    val note: String,
)
