package com.example.notepad.data

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import java.io.ByteArrayOutputStream
import kotlin.math.ceil
import kotlin.math.hypot
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

        paint.strokeWidth = stroke.widthPx
        if (stroke.tool == DrawingTools.ERASER) {
            drawEraserStroke(canvas, paint, stroke)
            return@forEach
        }

        paint.color = stroke.colorArgb
        paint.xfermode = null
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

private fun drawEraserStroke(
    canvas: Canvas,
    paint: Paint,
    stroke: DrawingStroke,
) {
    paint.color = Color.TRANSPARENT
    paint.style = Paint.Style.FILL
    paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    sampleStrokeCenters(stroke).forEach { point ->
        val half = stroke.widthPx / 2f
        canvas.drawRect(
            point.x - half,
            point.y - half,
            point.x + half,
            point.y + half,
            paint,
        )
    }
}

private fun sampleStrokeCenters(stroke: DrawingStroke): List<DrawingPoint> {
    val points = stroke.points
    if (points.isEmpty()) return emptyList()
    if (points.size == 1) return listOf(points.first())

    val stepPx = max(stroke.widthPx / 3f, 1f)
    return buildList {
        points.zipWithNext().forEach { (start, end) ->
            val dx = end.x - start.x
            val dy = end.y - start.y
            val distance = hypot(dx.toDouble(), dy.toDouble()).toFloat()
            val steps = max(1, ceil(distance / stepPx).toInt())
            for (step in 0..steps) {
                val fraction = step / steps.toFloat()
                add(
                    DrawingPoint(
                        x = start.x + dx * fraction,
                        y = start.y + dy * fraction,
                    ),
                )
            }
        }
    }
}
