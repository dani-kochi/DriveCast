package dev.dani.drivecast.data.model

import dev.dani.drivecast.domain.model.LatLng
import kotlin.math.pow

/**
 * Decoder for the Google encoded polyline algorithm.
 *
 * OSRM can emit either `polyline` (precision 5) or `polyline6` (precision 6) geometries, so the
 * precision is a parameter rather than a hard-coded 1e5 factor.
 */
object PolylineDecoder {

    const val PRECISION_5 = 5
    const val PRECISION_6 = 6

    fun decode(encoded: String, precision: Int = PRECISION_5): List<LatLng> = buildList {
        var index = 0
        var lat = 0
        var lng = 0
        val factor = 10.0.pow(precision)

        while (index < encoded.length) {
            val dLat = decodeSignedValue(encoded, index) ?: break
            index = dLat.nextIndex
            lat += dLat.value

            val dLng = decodeSignedValue(encoded, index) ?: break
            index = dLng.nextIndex
            lng += dLng.value

            add(LatLng(lat / factor, lng / factor))
        }
    }

    private class Chunk(val value: Int, val nextIndex: Int)

    private fun decodeSignedValue(encoded: String, start: Int): Chunk? {
        var index = start
        var shift = 0
        var result = 0
        var byte: Int
        do {
            if (index >= encoded.length) return null
            byte = encoded[index++].code - 63
            result = result or ((byte and 0x1f) shl shift)
            shift += 5
        } while (byte >= 0x20)
        val value = if (result and 1 != 0) (result shr 1).inv() else result shr 1
        return Chunk(value, index)
    }
}
