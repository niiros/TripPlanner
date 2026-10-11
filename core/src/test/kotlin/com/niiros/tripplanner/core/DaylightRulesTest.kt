package com.niiros.tripplanner.core

import com.niiros.tripplanner.core.daylight.DaylightFix
import com.niiros.tripplanner.core.daylight.DaylightLabel
import com.niiros.tripplanner.core.daylight.DaylightRules
import com.niiros.tripplanner.core.daylight.DaylightStatus
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DaylightRulesTest {
    private val z = fixedZmanim()
    private val buffer = minutes(30)

    @Test
    fun `arrival exactly at sunset minus buffer is OK`() {
        val arrival = at(18, 30)
        assertEquals(DaylightStatus.OK, DaylightRules.status(arrival, at(18, 45), z, buffer))
    }

    @Test
    fun `arrival one minute after sunset minus buffer is a warning`() {
        assertEquals(DaylightStatus.WARNING_NEAR_SUNSET, DaylightRules.status(at(18, 31), at(18, 40), z, buffer))
    }

    @Test
    fun `arrival exactly at sunset is still a warning, not late`() {
        assertEquals(DaylightStatus.WARNING_NEAR_SUNSET, DaylightRules.status(at(19, 0), at(19, 10), z, buffer))
    }

    @Test
    fun `arrival after sunset is late and labelled minutes after sunset`() {
        assertEquals(DaylightStatus.LATE, DaylightRules.status(at(19, 20), at(19, 50), z, buffer))
        val label = DaylightRules.label(at(19, 20), at(19, 50), z)
        assertIs<DaylightLabel.AfterSunset>(label)
        assertEquals(20, label.late.toMinutes())
    }

    @Test
    fun `stay running past sunset is a warning even when arrival is fine`() {
        assertEquals(DaylightStatus.WARNING_STAY_PAST_SUNSET, DaylightRules.status(at(17, 0), at(19, 15), z, buffer))
    }

    @Test
    fun `morning stop gets no daylight label`() {
        assertEquals(DaylightLabel.None, DaylightRules.label(at(9, 0), at(11, 30), z))
    }

    @Test
    fun `stay ending exactly at chatzot minus 60 counts as midday`() {
        assertIs<DaylightLabel.NearMidday>(DaylightRules.label(at(11, 0), at(12, 0), z))
    }

    @Test
    fun `stop overlapping chatzot gets the midday label with the chatzot time`() {
        val label = DaylightRules.label(at(12, 30), at(13, 30), z)
        assertIs<DaylightLabel.NearMidday>(label)
        assertEquals(z.chatzot, label.chatzot)
    }

    @Test
    fun `afternoon stop gets time before sunset`() {
        val label = DaylightRules.label(at(16, 15), at(17, 0), z)
        assertIs<DaylightLabel.BeforeSunset>(label)
        assertEquals(165, label.remaining.toMinutes())
    }

    @Test
    fun `late stop offers leave-earlier and shorten-dwell fixes with exact numbers`() {
        val first = stop("a", dwellMinutes = 120)
        val second = stop("b", dwellMinutes = 45)
        val late = stop("c", dwellMinutes = 30, daylight = true)
        val s1 = scheduled(0, first, at(10, 0))
        val s2 = scheduled(1, second, at(13, 0))
        val s3 = scheduled(2, late, at(19, 7))
        val sched = schedule(at(8, 0), s1, s2, s3)

        val check = DaylightRules.check(sched, s3, z, buffer, TEST_DATE)
        assertEquals(DaylightStatus.LATE, check.status)
        // Needs to arrive by 18:30 -> 37 minutes earlier. 08:00 - 37 min = 07:23 -> floored to 07:20.
        val leave = check.fixes.filterIsInstance<DaylightFix.LeaveEarlier>().single()
        assertEquals(LocalTime.of(7, 20), leave.newDeparture)
        assertEquals(40, leave.shift.toMinutes())
        // Longest earlier dwell is "a" (120 min); 37 min rounded up to 40.
        val shorten = check.fixes.filterIsInstance<DaylightFix.ShortenDwell>().single()
        assertEquals("a", shorten.stopId)
        assertEquals(40, shorten.by.toMinutes())
    }

    @Test
    fun `no shorten fix when earlier dwells are too short`() {
        val first = stop("a", dwellMinutes = 15)
        val late = stop("c", daylight = true)
        val s1 = scheduled(0, first, at(10, 0))
        val s2 = scheduled(1, late, at(19, 30))
        val check = DaylightRules.check(schedule(at(8, 0), s1, s2), s2, z, buffer, TEST_DATE)
        assertTrue(check.fixes.none { it is DaylightFix.ShortenDwell })
        assertTrue(check.fixes.any { it is DaylightFix.LeaveEarlier })
    }

    @Test
    fun `stops without the daylight flag get a label but no status`() {
        val s = stop("x")
        val ss = scheduled(0, s, at(19, 30))
        val check = DaylightRules.check(schedule(at(8, 0), ss), ss, z, buffer, TEST_DATE)
        assertNull(check.status)
        assertTrue(check.fixes.isEmpty())
    }
}
