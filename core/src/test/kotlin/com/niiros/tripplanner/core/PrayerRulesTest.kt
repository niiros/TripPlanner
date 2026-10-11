package com.niiros.tripplanner.core

import com.niiros.tripplanner.core.prayer.MinchaProblem
import com.niiros.tripplanner.core.prayer.PrayerRules
import com.niiros.tripplanner.core.prayer.StopZmanim
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PrayerRulesTest {
    private val z = fixedZmanim()

    private fun sz(index: Int, id: String, from: Pair<Int, Int>, to: Pair<Int, Int>, mincha: Boolean = false, arvit: Boolean = false) =
        StopZmanim(scheduled(index, stop(id, mincha = mincha, arvit = arvit), at(from.first, from.second), at(to.first, to.second)), z)

    @Test
    fun `no stop overlapping the mincha window raises an alert with sunset deadline`() {
        val plan = PrayerRules.plan(listOf(sz(0, "a", 9 to 0, 10 to 0), sz(1, "b", 11 to 0, 13 to 0)))
        assertTrue(plan.minchaStopIds.isEmpty())
        assertEquals(z.sunset, plan.minchaMissingDeadline)
    }

    @Test
    fun `suggests the last stop overlapping the window, preferring after mincha ketana`() {
        val plan = PrayerRules.plan(
            listOf(
                sz(0, "a", 13 to 45, 15 to 0),
                sz(1, "b", 16 to 45, 17 to 30),
                sz(2, "c", 19 to 30, 20 to 0), // after sunset: outside the window
            ),
        )
        assertEquals(listOf("b"), plan.minchaStopIds)
        assertTrue(plan.minchaSuggested)
        assertNull(plan.minchaMissingDeadline)
    }

    @Test
    fun `falls back to the last overlapping stop when none is after ketana`() {
        val plan = PrayerRules.plan(listOf(sz(0, "a", 13 to 45, 14 to 15), sz(1, "b", 14 to 30, 15 to 0)))
        assertEquals(listOf("b"), plan.minchaStopIds)
    }

    @Test
    fun `manual mincha toggle overrides the suggestion and is flagged when outside the window`() {
        val plan = PrayerRules.plan(listOf(sz(0, "a", 10 to 0, 11 to 0, mincha = true), sz(1, "b", 16 to 45, 17 to 30)))
        assertEquals(listOf("a"), plan.minchaStopIds)
        assertEquals(false, plan.minchaSuggested)
        assertEquals(MinchaProblem.ENDS_BEFORE_GEDOLA, plan.minchaProblems["a"])
    }

    @Test
    fun `manual mincha after sunset is flagged`() {
        val plan = PrayerRules.plan(listOf(sz(0, "a", 19 to 10, 19 to 40, mincha = true)))
        assertEquals(MinchaProblem.STARTS_AFTER_SUNSET, plan.minchaProblems["a"])
    }

    @Test
    fun `arvit is suggested at the first stop whose stay extends past tzeit`() {
        val plan = PrayerRules.plan(listOf(sz(0, "a", 17 to 0, 18 to 0), sz(1, "b", 19 to 0, 20 to 0), sz(2, "c", 20 to 30, 21 to 0)))
        assertEquals(listOf("b"), plan.arvitStopIds)
        assertTrue(plan.arvitSuggested)
    }

    @Test
    fun `when no stay passes tzeit, the tzeit time is noted`() {
        val plan = PrayerRules.plan(listOf(sz(0, "a", 15 to 0, 16 to 0)))
        assertTrue(plan.arvitStopIds.isEmpty())
        assertEquals(z.tzeit, plan.arvitTzeitNote)
    }

    @Test
    fun `manual arvit overrides suggestion`() {
        val plan = PrayerRules.plan(listOf(sz(0, "a", 15 to 0, 16 to 0, arvit = true), sz(1, "b", 19 to 0, 20 to 0)))
        assertEquals(listOf("a"), plan.arvitStopIds)
        assertEquals(false, plan.arvitSuggested)
    }
}
