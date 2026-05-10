package com.example.notepad.data

import org.json.JSONArray
import org.json.JSONObject

data class DrawingPoint(
    val x: Float,
    val y: Float,
)

data class DrawingStroke(
    val points: List<DrawingPoint>,
)

object DrawingJson {
    fun encode(strokes: List<DrawingStroke>): String {
        val strokeArray = JSONArray()
        strokes.forEach { stroke ->
            val pointArray = JSONArray()
            stroke.points.forEach { point ->
                pointArray.put(
                    JSONObject()
                        .put("x", point.x.toDouble())
                        .put("y", point.y.toDouble()),
                )
            }
            strokeArray.put(JSONObject().put("points", pointArray))
        }
        return strokeArray.toString()
    }

    fun decode(json: String?): List<DrawingStroke> {
        if (json.isNullOrBlank()) return emptyList()

        return runCatching {
            val strokeArray = JSONArray(json)
            buildList {
                for (strokeIndex in 0 until strokeArray.length()) {
                    val strokeObject = strokeArray.optJSONObject(strokeIndex) ?: continue
                    val pointArray = strokeObject.optJSONArray("points") ?: continue
                    val points = buildList {
                        for (pointIndex in 0 until pointArray.length()) {
                            val pointObject = pointArray.optJSONObject(pointIndex) ?: continue
                            add(
                                DrawingPoint(
                                    x = pointObject.optDouble("x").toFloat(),
                                    y = pointObject.optDouble("y").toFloat(),
                                ),
                            )
                        }
                    }
                    if (points.isNotEmpty()) add(DrawingStroke(points))
                }
            }
        }.getOrDefault(emptyList())
    }
}
