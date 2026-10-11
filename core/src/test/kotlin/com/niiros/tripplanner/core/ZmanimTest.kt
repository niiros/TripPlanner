package com.niiros.tripplanner.core

import com.niiros.tripplanner.core.model.GeoPoint
import com.niiros.tripplanner.core.model.TzeitMethod
import com.niiros.tripplanner.core.zmanim.ZmanimCalculator
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ZmanimTest {
    private val calc = ZmanimCalculator()
    private val jerusalem = GeoPoint(31.7767, 35.2345)
    private val zone = ZoneId.of("Asia/Jerusalem")
    private val date = LocalDate.of(2026, 10, 20)

    @Test
    fun `zmanim are ordered and chatzot is the midpoint of the day`() {
        val z = assertNotNull(calc.compute(jerusalem, date, zone, TzeitMethod.DEGREES_8_5))
        val ordered = listOf(z.sunrise, z.chatzot, z.minchaGedola, z.minchaKetana, z.plagHamincha, z.sunset, z.tzeit!!)
        assertEquals(ordered.sorted(), ordered)
        val mid = z.sunrise.plus(Duration.between(z.sunrise, z.sunset).dividedBy(2))
        assertTrue(abs(Duration.between(mid, z.chatzot).seconds) < 90, "chatzot $z")
        // Mincha gedola is half a sha'ah zmanit after chatzot (Gra).
        val shaah = Duration.between(z.sunrise, z.sunset).dividedBy(12)
        assertTrue(abs(Duration.between(z.chatzot.plus(shaah.dividedBy(2)), z.minchaGedola).seconds) < 90)
    }

    @Test
    fun `jerusalem sunset in late october is plausible`() {
        val z = assertNotNull(calc.compute(jerusalem, date, zone, TzeitMethod.DEGREES_8_5))
        val local = z.sunset.atZone(zone).toLocalTime()
        // Sea-level sunset in Jerusalem on 20 Oct (still on IDT, DST ends 25 Oct) is about 18:00.
        assertTrue(local.isAfter(LocalTime.of(17, 45)) && local.isBefore(LocalTime.of(18, 15)), "sunset $local")
    }

    @Test
    fun `tzeit methods differ as expected`() {
        val deg = calc.compute(jerusalem, date, zone, TzeitMethod.DEGREES_8_5)!!
        val fixed = calc.compute(jerusalem, date, zone, TzeitMethod.FIXED_18_MIN)!!
        val rt = calc.compute(jerusalem, date, zone, TzeitMethod.RABBEINU_TAM_72)!!
        assertEquals(deg.sunset.plus(Duration.ofMinutes(18)), fixed.tzeit)
        assertTrue(Duration.between(rt.sunset, rt.tzeit).toMinutes() in 71..72)
        val degMinutes = Duration.between(deg.sunset, deg.tzeit).toMinutes()
        assertTrue(degMinutes in 25..45, "8.5 degrees tzeit is $degMinutes min after sunset")
    }

    @Test
    fun `zmanim follow the stop location, not the device zone`() {
        val marrakech = calc.compute(GeoPoint(31.63, -7.99), date, ZoneId.of("Africa/Casablanca"), TzeitMethod.DEGREES_8_5)!!
        val ouarzazate = calc.compute(GeoPoint(30.92, -6.90), date, ZoneId.of("Africa/Casablanca"), TzeitMethod.DEGREES_8_5)!!
        // Ouarzazate is ~1 degree east: sunset a few minutes earlier.
        assertTrue(ouarzazate.sunset.isBefore(marrakech.sunset))
    }
}
