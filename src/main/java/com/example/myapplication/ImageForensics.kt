package com.example.myapplication

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Lightweight, offline texture check.
 * It looks for tiles whose high-frequency energy is very different from the
 * rest of the image (pasted or re-rendered areas). It is a hint, not proof.
 */
object ImageForensics {

    private const val TILE = 32

    fun analyze(bitmap: Bitmap): ImageForensicsResult {
        val w = bitmap.width
        val h = bitmap.height
        val lowRes = w < 400 || h < 400

        if (w < TILE * 3 || h < TILE * 3) {
            return ImageForensicsResult(w, h, lowRes, false, emptyList())
        }

        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        val luma = FloatArray(w * h)
        for (i in pixels.indices) {
            val p = pixels[i]
            luma[i] = 0.299f * ((p shr 16) and 0xFF) +
                    0.587f * ((p shr 8) and 0xFF) +
                    0.114f * (p and 0xFF)
        }

        val tilesX = (w + TILE - 1) / TILE
        val tilesY = (h + TILE - 1) / TILE
        val total = tilesX * tilesY

        val sum = DoubleArray(total)
        val sumSq = DoubleArray(total)
        val lap = DoubleArray(total)
        val count = IntArray(total)

        for (y in 1 until h - 1) {
            val row = y * w
            val tileRow = (y / TILE) * tilesX
            for (x in 1 until w - 1) {
                val i = row + x
                val c = luma[i]
                val l = abs(4f * c - luma[i - 1] - luma[i + 1] - luma[i - w] - luma[i + w])
                val t = tileRow + x / TILE
                sum[t] += c.toDouble()
                sumSq[t] += (c * c).toDouble()
                lap[t] += l.toDouble()
                count[t]++
            }
        }

        val flat = mutableListOf<Int>()
        val textured = mutableListOf<Int>()
        val energy = DoubleArray(total)

        for (t in 0 until total) {
            if (count[t] < TILE * TILE / 2) continue
            val mean = sum[t] / count[t]
            val std = sqrt((sumSq[t] / count[t] - mean * mean).coerceAtLeast(0.0))
            energy[t] = lap[t] / count[t]
            when {
                std < 6.0 -> flat.add(t)
                std >= 8.0 -> textured.add(t)
            }
        }

        val flatMedian = median(flat.map { energy[it] })
        val photoLike = flat.size >= 8 && flatMedian > 1.2

        val candidates = mutableListOf<Pair<Int, Double>>()
        candidates += outliers(flat, energy, minZ = 6.0, ratio = 3.0, minAbs = 1.5, madFloor = 0.3)
        if (!photoLike) {
            candidates += outliers(textured, energy, minZ = 6.0, ratio = 2.0, minAbs = 0.0, madFloor = 2.0)
        }

        val regions = candidates
            .sortedByDescending { it.second }
            .take(3)
            .map { (t, _) ->
                val tx = t % tilesX
                val ty = t / tilesX
                ImageForensicsRegion(
                    left = (tx * TILE).toFloat() / w,
                    top = (ty * TILE).toFloat() / h,
                    right = minOf((tx + 1) * TILE, w).toFloat() / w,
                    bottom = minOf((ty + 1) * TILE, h).toFloat() / h
                )
            }

        return ImageForensicsResult(w, h, lowRes, photoLike, regions)
    }

    private fun outliers(
        ids: List<Int>,
        energy: DoubleArray,
        minZ: Double,
        ratio: Double,
        minAbs: Double,
        madFloor: Double
    ): List<Pair<Int, Double>> {
        if (ids.size < 8) return emptyList()

        val values = ids.map { energy[it] }
        val med = median(values)
        val mad = median(values.map { abs(it - med) }).coerceAtLeast(madFloor)

        return ids.mapNotNull { t ->
            val e = energy[t]
            val z = (e - med) / (1.4826 * mad)
            if (z > minZ && e > med * ratio && e > minAbs) t to z else null
        }
    }

    private fun median(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        val sorted = values.sorted()
        return sorted[sorted.size / 2]
    }
}
