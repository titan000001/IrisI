package com.example.camera

internal object RawSensorGeometry {
    fun select(supported: List<Pair<Int, Int>>, native: List<Pair<Int, Int>>,
               requested: Pair<Int, Int>): Pair<Int, Int>? =
        supported.filter { it in native && it.first > 0 && it.second > 0 }
            .minByOrNull { kotlin.math.abs(it.first.toLong() - requested.first) +
                kotlin.math.abs(it.second.toLong() - requested.second) }
}
