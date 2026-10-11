package com.niiros.tripplanner.core

import com.niiros.tripplanner.core.clothing.ClothingItem
import com.niiros.tripplanner.core.clothing.ClothingRules
import com.niiros.tripplanner.core.clothing.StayWeather
import com.niiros.tripplanner.core.util.TtlCache
import com.niiros.tripplanner.core.weather.HourPoint
import com.niiros.tripplanner.core.weather.HourlyForecast
import com.niiros.tripplanner.core.weather.WeatherCondition
import com.niiros.tripplanner.core.weather.WeatherIcon
import com.niiros.tripplanner.core.weather.WeatherRules
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WeatherAndClothingTest {

    private fun hour(h: Int, temp: Double, apparent: Double = temp, code: Int = 0, wind: Double = 10.0, rain: Int = 0, uv: Double = 2.0) =
        HourPoint(at(h), temp, apparent, rain, code, wind, uv)

    @Test
    fun `wmo codes map to conditions and icons`() {
        assertEquals(WeatherCondition.THUNDERSTORM, WeatherRules.condition(95))
        assertEquals(WeatherIcon.STORM, WeatherRules.icon(WeatherRules.condition(99), 5.0, false))
        assertEquals(WeatherIcon.SNOW, WeatherRules.icon(WeatherRules.condition(73), 5.0, false))
        assertEquals(WeatherIcon.RAIN, WeatherRules.icon(WeatherRules.condition(81), 5.0, false))
        assertEquals(WeatherIcon.FOG, WeatherRules.icon(WeatherRules.condition(45), 5.0, false))
        assertEquals(WeatherIcon.CLOUDY, WeatherRules.icon(WeatherRules.condition(3), 5.0, false))
        assertEquals(WeatherIcon.PARTLY_CLOUDY, WeatherRules.icon(WeatherRules.condition(2), 5.0, true))
        assertEquals(WeatherIcon.WIND, WeatherRules.icon(WeatherRules.condition(0), 45.0, false))
        assertEquals(WeatherIcon.CLEAR_NIGHT, WeatherRules.icon(WeatherRules.condition(0), 5.0, true))
        assertEquals(WeatherIcon.CLEAR_DAY, WeatherRules.icon(WeatherRules.condition(1), 5.0, false))
    }

    @Test
    fun `arrival after sunset uses the night icon`() {
        val f = HourlyForecast(listOf(hour(19, 20.0), hour(20, 18.0)), TEST_ZONE, 500.0)
        val w = assertNotNull(WeatherRules.stopWeather(f, at(19, 40), at(7, 0), at(19, 0)))
        assertEquals(WeatherIcon.CLEAR_NIGHT, w.icon)
        assertTrue(w.isNight)
    }

    @Test
    fun `extras appear only above their thresholds`() {
        val mild = HourlyForecast(listOf(hour(12, 20.0, apparent = 18.0, wind = 20.0, rain = 20, uv = 5.0)), TEST_ZONE, null)
        val w1 = WeatherRules.stopWeather(mild, at(12, 10), null, null)!!
        assertNull(w1.feelsLikeC)
        assertNull(w1.windKmh)
        assertNull(w1.precipitationProbability)
        assertEquals(false, w1.highUv)

        val harsh = HourlyForecast(listOf(hour(12, 20.0, apparent = 16.0, wind = 40.0, rain = 30, uv = 6.0)), TEST_ZONE, null)
        val w2 = WeatherRules.stopWeather(harsh, at(12, 10), null, null)!!
        assertEquals(16, w2.feelsLikeC)
        assertEquals(40, w2.windKmh)
        assertEquals(30, w2.precipitationProbability)
        assertTrue(w2.highUv)
    }

    @Test
    fun `no forecast hour near arrival means no weather`() {
        val f = HourlyForecast(listOf(hour(6, 10.0)), TEST_ZONE, null)
        assertNull(WeatherRules.stopWeather(f, at(12, 0), null, null))
    }

    @Test
    fun `elevation rounds to nearest 10 meters and missing stays missing`() {
        assertEquals(2260, WeatherRules.roundElevation(2257.4))
        assertEquals(470, WeatherRules.roundElevation(466.0))
        assertNull(WeatherRules.roundElevation(null))
        assertNull(WeatherRules.roundElevation(Double.NaN))
    }

    @Test
    fun `large temperature spread recommends layers and names the extremes and the high point`() {
        val stays = listOf(
            StayWeather("מרקש", TEST_ZONE, 470, listOf(hour(9, 22.0, apparent = 23.0, uv = 3.0))),
            StayWeather("טישקה", TEST_ZONE, 2260, listOf(hour(11, 12.0, apparent = 9.0, wind = 38.0, rain = 45, uv = 8.0))),
            StayWeather("ורזזאת", TEST_ZONE, 1140, listOf(hour(15, 28.0, apparent = 29.4))),
        )
        val s = assertNotNull(ClothingRules.summarize(stays))
        assertEquals(9, s.min.feelsLikeC)
        assertEquals("טישקה", s.min.placeName)
        assertEquals(29, s.max.feelsLikeC)
        assertEquals("ורזזאת", s.max.placeName)
        assertEquals(20, s.spread)
        assertEquals("טישקה", s.highest?.placeName)
        assertEquals(2260, s.highest?.elevationM)

        val items = s.items.associateBy { it.item }
        assertNotNull(items[ClothingItem.LAYERS])
        assertEquals("טישקה", items[ClothingItem.WARM_LAYER]?.singlePlace)
        assertEquals("ורזזאת", items[ClothingItem.LIGHT_CLOTHING_WATER]?.singlePlace)
        assertEquals("טישקה", items[ClothingItem.WINDBREAKER]?.singlePlace)
        assertEquals("טישקה", items[ClothingItem.RAIN_JACKET]?.singlePlace)
        assertEquals("טישקה", items[ClothingItem.HAT_SUNSCREEN]?.singlePlace)
        assertNull(items[ClothingItem.COAT_HAT_GLOVES])
        assertNull(items[ClothingItem.WATERPROOF_SHOES])
    }

    @Test
    fun `freezing snowy stop asks for coat and waterproof shoes, small spread no layers`() {
        val stays = listOf(
            StayWeather("A", TEST_ZONE, 1800, listOf(hour(10, 2.0, apparent = 0.0, code = 73))),
            StayWeather("B", TEST_ZONE, 1900, listOf(hour(12, 4.0, apparent = 3.0))),
        )
        val s = ClothingRules.summarize(stays)!!
        val items = s.items.map { it.item }
        assertTrue(ClothingItem.COAT_HAT_GLOVES in items)
        assertTrue(ClothingItem.WARM_LAYER !in items)
        assertTrue(ClothingItem.WATERPROOF_SHOES in items)
        assertTrue(ClothingItem.LAYERS !in items)
        assertNull(s.highest)
        // Rule triggered by both stops: no single place named.
        assertNull(s.items.first { it.item == ClothingItem.COAT_HAT_GLOVES }.singlePlace)
    }

    @Test
    fun `no weather at all gives no clothing card`() {
        assertNull(ClothingRules.summarize(listOf(StayWeather("A", TEST_ZONE, 100, emptyList()))))
    }

    @Test
    fun `ttl cache expires entries after one hour`() {
        var now = Instant.parse("2026-10-20T08:00:00Z")
        val cache = TtlCache<String, Int>(Duration.ofHours(1)) { now }
        cache.put("k", 1)
        now = now.plus(Duration.ofMinutes(59))
        assertEquals(1, cache.get("k"))
        now = now.plus(Duration.ofMinutes(1))
        assertNull(cache.get("k"))
    }
}
