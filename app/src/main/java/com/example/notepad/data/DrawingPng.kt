package com.example.notepad.data

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import java.io.ByteArrayOutputStream
import kotlin.math.max

private const val MIN_EXPORT_SIZE_PX = 1

fun renderDrawingPng(
    strokes: List<DrawingStroke>,
    width: Int,
    height: Int,
): ByteArray {
    val bitmap = Bitmap.createBitmap(
        max(width, MIN_EXPORT_SIZE_PX),
        max(height, MIN_EXPORT_SIZE_PX),
        Bitmap.Config.ARGB_8888,
    )
    val canvas = Canvas(bitmap)
    canvas.drawColor(Color.WHITE)
    val layer = canvas.saveLayer(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat(), null)

    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    strokes.forEach { stroke ->
        val points = stroke.points
        if (points.isEmpty()) return@forEach

        paint.color = if (stroke.tool == DrawingTools.ERASER) Color.TRANSPARENT else stroke.colorArgb
        paint.strokeWidth = stroke.widthPx
        paint.xfermode = if (stroke.tool == DrawingTools.ERASER) {
            PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        } else {
            null
        }

        if (points.size == 1) {
            paint.style = Paint.Style.FILL
            canvas.drawCircle(
                points.first().x,
                points.first().y,
                stroke.widthPx / 2f,
                paint,
            )
        } else {
            paint.style = Paint.Style.STROKE
            val path = Path().apply {
                moveTo(points.first().x, points.first().y)
                points.drop(1).forEach { point ->
                    lineTo(point.x, point.y)
                }
            }
            canvas.drawPath(path, paint)
        }
    }

    paint.xfermode = null
    canvas.restoreToCount(layer)

    return ByteArrayOutputStream().use { outputStream ->
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
        outputStream.toByteArray()
    }
}
