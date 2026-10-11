package com.niiros.tripplanner.core.clothing

import com.niiros.tripplanner.core.weather.HourPoint
import com.niiros.tripplanner.core.weather.WeatherRules
import java.time.Instant
import java.time.ZoneId
import kotlin.math.roundToInt

/** Weather hours a person actually spends at one place (a stop's stay, or the start at departure). */
data class StayWeather(
    val placeName: String,
    val zone: ZoneId,
    val elevationM: Int?,
    val hours: List<HourPoint>,
)

enum class ClothingItem { LAYERS, WARM_LAYER, COAT_HAT_GLOVES, LIGHT_CLOTHING_WATER, WINDBREAKER, RAIN_JACKET, HAT_SUNSCREEN, WATERPROOF_SHOES }

/** [singlePlace] is set when exactly one place triggered the rule, so the UI can name it. */
data class ClothingAdvice(val item: ClothingItem, val singlePlace: String?)

data class Extreme(val placeName: String, val time: Instant, val zone: ZoneId, val feelsLikeC: Int)

data class HighPoint(val placeName: String, val elevationM: Int)

data class ClothingSummary(
    val min: Extreme,
    val max: Extreme,
    val spread: Int,
    /** Set when the day's elevation range exceeds [ClothingRules.ELEVATION_RANGE_M]. */
    val highest: HighPoint?,
    val items: List<ClothingAdvice>,
)

object ClothingRules {
    const val LAYERS_SPREAD_C = 10
    const val WARM_LAYER_BELOW_C = 12.0
    const val COAT_BELOW_C = 5.0
    const val LIGHT_CLOTHING_ABOVE_C = 28.0
    const val WINDBREAKER_ABOVE_KMH = 30.0
    const val RAIN_JACKET_PROBABILITY = 40
    const val SUN_PROTECTION_UV = 6.0
    const val ELEVATION_RANGE_M = 800

    fun summarize(stays: List<StayWeather>): ClothingSummary? {
        val withHours = stays.filter { it.hours.isNotEmpty() }
        if (withHours.isEmpty()) return null

        data class Sample(val stay: StayWeather, val hour: HourPoint)
        val samples = withHours.flatMap { s -> s.hours.map { Sample(s, it) } }
        val minSample = samples.minBy { it.hour.feelsLikeC }
        val maxSample = samples.maxBy { it.hour.feelsLikeC }
        val minC = minSample.hour.feelsLikeC.roundToInt()
        val maxC = maxSample.hour.feelsLikeC.roundToInt()
        val spread = maxC - minC

        val elevations = stays.mapNotNull { s -> s.elevationM?.let { s to it } }
        val highest = if (elevations.size >= 2) {
            val hi = elevations.maxBy { it.second }
            val lo = elevations.minBy { it.second }
            if (hi.second - lo.second > ELEVATION_RANGE_M) HighPoint(hi.first.placeName, hi.second) else null
        } else {
            null
        }

        fun placesWhere(predicate: (HourPoint) -> Boolean): List<String> =
            withHours.filter { s -> s.hours.any(predicate) }.map { it.placeName }.distinct()

        val items = ArrayList<ClothingAdvice>()
        fun add(item: ClothingItem, places: List<String>?) {
            if (places == null) items += ClothingAdvice(item, null)
            else if (places.isNotEmpty()) items += ClothingAdvice(item, places.singleOrNull())
        }

        if (spread >= LAYERS_SPREAD_C) add(ClothingItem.LAYERS, null)
        val coat = placesWhere { it.feelsLikeC < COAT_BELOW_C }
        if (coat.isNotEmpty()) {
            add(ClothingItem.COAT_HAT_GLOVES, coat)
        } else {
            add(ClothingItem.WARM_LAYER, placesWhere { it.feelsLikeC < WARM_LAYER_BELOW_C })
        }
        add(ClothingItem.LIGHT_CLOTHING_WATER, placesWhere { it.feelsLikeC > LIGHT_CLOTHING_ABOVE_C })
        add(ClothingItem.WINDBREAKER, placesWhere { (it.windKmh ?: 0.0) > WINDBREAKER_ABOVE_KMH })
        add(ClothingItem.RAIN_JACKET, placesWhere { (it.precipitationProbability ?: 0) >= RAIN_JACKET_PROBABILITY })
        add(ClothingItem.HAT_SUNSCREEN, placesWhere { (it.uvIndex ?: 0.0) >= SUN_PROTECTION_UV })
        add(ClothingItem.WATERPROOF_SHOES, placesWhere { WeatherRules.isSnow(it.weatherCode) })

        return ClothingSummary(
            min = Extreme(minSample.stay.placeName, minSample.hour.time, minSample.stay.zone, minC),
            max = Extreme(maxSample.stay.placeName, maxSample.hour.time, maxSample.stay.zone, maxC),
            spread = spread,
            highest = highest,
            items = items,
        )
    }
}
