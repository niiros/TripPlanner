package com.niiros.tripplanner

import android.content.Context
import androidx.room.Room
import com.niiros.tripplanner.core.demo.FakePlaceSearchRepository
import com.niiros.tripplanner.core.demo.FakePointInfoRepository
import com.niiros.tripplanner.core.demo.FakeRouteRepository
import com.niiros.tripplanner.core.demo.FakeWeatherRepository
import com.niiros.tripplanner.core.plan.DayPlanner
import com.niiros.tripplanner.core.repo.PlaceSearchRepository
import com.niiros.tripplanner.core.repo.PointInfoRepository
import com.niiros.tripplanner.core.repo.RouteRepository
import com.niiros.tripplanner.core.repo.WeatherRepository
import com.niiros.tripplanner.core.schedule.LegPlanner
import com.niiros.tripplanner.core.schedule.ScheduleCalculator
import com.niiros.tripplanner.data.db.AppDatabase
import com.niiros.tripplanner.data.db.TripRepository
import com.niiros.tripplanner.data.location.LocationProvider
import com.niiros.tripplanner.data.net.AppSignature
import com.niiros.tripplanner.data.net.GoogleApiKeyInterceptor
import com.niiros.tripplanner.data.net.OpenMeteoApi
import com.niiros.tripplanner.data.net.OpenMeteoPointInfoRepository
import com.niiros.tripplanner.data.net.OpenMeteoWeatherRepository
import com.niiros.tripplanner.data.net.RoutesApi
import com.niiros.tripplanner.data.net.RoutesRouteRepository
import com.niiros.tripplanner.data.places.GooglePlacesSearchRepository
import com.niiros.tripplanner.data.settings.SettingsRepository
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * Manual dependency container. Without a Maps API key the app runs in demo mode: places, routes,
 * weather and zones come from deterministic fakes and the Morocco sample day.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    val clock: () -> Instant = { Instant.now() }
    fun deviceZone(): ZoneId = ZoneId.systemDefault()

    val apiKey: String = BuildConfig.MAPS_API_KEY.trim()
    val demoMode: Boolean = apiKey.isEmpty() || apiKey == PLACEHOLDER_KEY

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }

    private val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    private val openMeteo: OpenMeteoApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.open-meteo.com/")
            .client(http)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(OpenMeteoApi::class.java)
    }

    private val routesApi: RoutesApi by lazy {
        val client = http.newBuilder()
            .addInterceptor(GoogleApiKeyInterceptor(apiKey, appContext.packageName, AppSignature.sha1(appContext)))
            .build()
        Retrofit.Builder()
            .baseUrl("https://routes.googleapis.com/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(RoutesApi::class.java)
    }

    private val fakePointInfo by lazy { FakePointInfoRepository(deviceZone()) }

    val pointInfo: PointInfoRepository by lazy { if (demoMode) fakePointInfo else OpenMeteoPointInfoRepository(openMeteo) }
    val weather: WeatherRepository by lazy { if (demoMode) FakeWeatherRepository(fakePointInfo) else OpenMeteoWeatherRepository(openMeteo, clock) }
    val routes: RouteRepository by lazy { if (demoMode) FakeRouteRepository(fakePointInfo) else RoutesRouteRepository(routesApi, json) }
    val placeSearch: PlaceSearchRepository by lazy {
        if (demoMode) FakePlaceSearchRepository() else GooglePlacesSearchRepository(appContext, apiKey)
    }

    val legPlanner: LegPlanner by lazy { LegPlanner(routes, clock) }
    val dayPlanner: DayPlanner by lazy { DayPlanner(pointInfo, weather, ScheduleCalculator(legPlanner)) }

    private val database: AppDatabase by lazy {
        Room.databaseBuilder(appContext, AppDatabase::class.java, "tripplanner.db").build()
    }
    val trips: TripRepository by lazy { TripRepository(database.tripDao(), clock) }
    val settings: SettingsRepository by lazy { SettingsRepository(appContext) }
    val location: LocationProvider by lazy { LocationProvider(appContext) }

    companion object {
        const val PLACEHOLDER_KEY = "DEMO_NO_KEY"
    }
}
