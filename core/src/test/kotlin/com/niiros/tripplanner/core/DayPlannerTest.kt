package com.niiros.tripplanner.core

import com.niiros.tripplanner.core.demo.DemoData
import com.niiros.tripplanner.core.demo.FakePointInfoRepository
import com.niiros.tripplanner.core.demo.FakeRouteRepository
import com.niiros.tripplanner.core.demo.FakeWeatherRepository
import com.niiros.tripplanner.core.daylight.DaylightLabel
import com.niiros.tripplanner.core.daylight.DaylightStatus
import com.niiros.tripplanner.core.model.GeoPoint
import com.niiros.tripplanner.core.model.PlannerSettings
import com.niiros.tripplanner.core.model.ServiceError
import com.niiros.tripplanner.core.model.Trip
import com.niiros.tripplanner.core.plan.DayPlanner
import com.niiros.tripplanner.core.plan.WeatherState
import com.niiros.tripplanner.core.repo.PointInfo
import com.niiros.tripplanner.core.repo.WeatherRepository
import com.niiros.tripplanner.core.repo.WeatherResult
import com.niiros.tripplanner.core.schedule.LegPlanner
import com.niiros.tripplanner.core.schedule.ScheduleCalculator
import kotlinx.coroutines.test.runTest
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DayPlannerTest {
    private val israel = ZoneId.of("Asia/Jerusalem")
    private val now = at(5, 0, date = TEST_DATE.minusDays(1))

    private fun demoPlanner(weather: WeatherRepository? = null): DayPlanner {
        val info = FakePointInfoRepository(israel)
        return DayPlanner(info, weather ?: FakeWeatherRepository(info), ScheduleCalculator(LegPlanner(FakeRouteRepository(info), { now })))
    }

    @Test
    fun `morocco demo day plans end to end with mountain colder than valleys`() = runTest {
        val trip = DemoData.moroccoTrip("demo", TEST_DATE)
        val plan = demoPlanner().plan(trip, PlannerSettings(), now, israel)

        assertEquals(DemoData.MOROCCO, plan.schedule.startZone)
        assertEquals(4, plan.stops.size)
        assertTrue(plan.errors.isEmpty())
        assertEquals(2260, plan.stops[0].point.elevationM)
        val pass = plan.stops[0].point.weather!!
        val valley = plan.stops[2].point.weather!!
        assertTrue(pass.temperatureC < valley.temperatureC - 8, "pass ${pass.temperatureC} valley ${valley.temperatureC}")
        assertEquals("נווה המדבר פינט", plan.summaryPlaceName)
        assertNotNull(plan.summaryZmanim)
        val clothing = assertNotNull(plan.clothing)
        assertTrue(clothing.spread >= 10)
        assertEquals("מעבר טיזי נ'טישקה", clothing.highest?.placeName)
        // Times are shown in Morocco time even though the fallback (device) zone is Israel.
        assertEquals(LocalTime.of(9, 30), plan.schedule.departure.atZone(plan.schedule.startZone).toLocalTime())
    }

    @Test
    fun `forecast unavailable keeps everything else working`() = runTest {
        val trip = DemoData.moroccoTrip("demo", TEST_DATE)
        val plan = demoPlanner(NoWeather()).plan(trip, PlannerSettings(), now, israel)
        assertTrue(plan.stops.all { it.point.weatherState == WeatherState.NOT_YET && it.point.weather == null })
        assertNull(plan.clothing?.items?.firstOrNull())
        assertNotNull(plan.summaryZmanim)
        assertTrue(plan.stops.all { it.daylight != null })
        assertTrue(plan.errors.isEmpty())
    }

    @Test
    fun `weather request failure is reported and stops show failed state`() = runTest {
        val trip = DemoData.moroccoTrip("demo", TEST_DATE)
        val plan = demoPlanner(NoWeather(WeatherResult.Failed(ServiceError.OFFLINE))).plan(trip, PlannerSettings(), now, israel)
        assertTrue(ServiceError.OFFLINE in plan.errors)
        assertTrue(plan.stops.all { it.point.weatherState == WeatherState.FAILED })
    }

    @Test
    fun `elevation missing for one stop omits only that stop's elevation`() = runTest {
        val start = place("start", 31.6, -8.0)
        val a = stop("a", lat = 31.3, lng = -7.4)
        val b = stop("b", lat = 31.0, lng = -7.1)
        val infos = MapPointInfo(
            mapOf(
                start.point to PointInfo(TEST_ZONE, 470.0),
                a.place.point to PointInfo(TEST_ZONE, null),
                b.place.point to PointInfo(TEST_ZONE, 1253.0),
            ),
        )
        val planner = DayPlanner(infos, NoWeather(), ScheduleCalculator(LegPlanner(FixedRoutes(), { now })))
        val trip = Trip("t", "x", TEST_DATE, LocalTime.of(8, 0), start, listOf(a, b))
        val plan = planner.plan(trip, PlannerSettings(), now, israel)
        assertEquals(470, plan.start?.elevationM)
        assertNull(plan.stops[0].point.elevationM)
        assertEquals(1250, plan.stops[1].point.elevationM)
    }

    @Test
    fun `unknown start zone falls back to the device zone and is flagged`() = runTest {
        val planner = DayPlanner(MapPointInfo(emptyMap()), NoWeather(), ScheduleCalculator(LegPlanner(FixedRoutes(), { now })))
        val trip = Trip("t", "x", TEST_DATE, LocalTime.of(8, 0), place("s", 10.0, 10.0), listOf(stop("a", lat = 10.1, lng = 10.0)))
        val plan = planner.plan(trip, PlannerSettings(), now, israel)
        assertTrue(plan.zoneUncertain)
        assertEquals(israel, plan.schedule.startZone)
    }

    @Test
    fun `demo day flags the late oasis stop and the midday pass`() = runTest {
        val trip = DemoData.moroccoTrip("demo", LocalDate.of(2026, 10, 20))
        val plan = demoPlanner().plan(trip, PlannerSettings(), now, israel)
        val labels = plan.stops.map { it.daylight?.label }
        assertTrue(labels[0] is DaylightLabel.NearMidday || labels[0] is DaylightLabel.None, "pass label ${labels[0]}")
        val oasis = plan.stops.last().daylight!!
        assertTrue(oasis.status != DaylightStatus.OK, "oasis status ${oasis.status} at ${plan.stops.last().scheduled.arrival.atZone(DemoData.MOROCCO)}")
        assertTrue(oasis.fixes.isNotEmpty())
    }

    @Test
    fun `resolved zones are returned for caching`() = runTest {
        val start = place("start", 31.6, -8.0)
        val a = stop("a", lat = 31.3, lng = -7.4)
        val infos = MapPointInfo(mapOf(start.point to PointInfo(TEST_ZONE, 470.0), a.place.point to PointInfo(TEST_ZONE, 2000.0)))
        val planner = DayPlanner(infos, NoWeather(), ScheduleCalculator(LegPlanner(FixedRoutes(), { now })))
        val plan = planner.plan(Trip("t", "x", TEST_DATE, LocalTime.of(8, 0), start, listOf(a)), PlannerSettings(), now, israel)
        assertEquals(TEST_ZONE, plan.resolvedPointInfo[DayPlanner.START_KEY]?.zone)
        assertEquals(2000.0, plan.resolvedPointInfo["a"]?.elevationM)
        assertEquals(GeoPoint(31.3, -7.4), plan.stops[0].scheduled.stop.place.point)
    }
}
