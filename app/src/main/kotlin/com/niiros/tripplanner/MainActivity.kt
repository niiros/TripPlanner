package com.niiros.tripplanner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.niiros.tripplanner.ui.settings.SettingsScreen
import com.niiros.tripplanner.ui.theme.TripPlannerTheme
import com.niiros.tripplanner.ui.trip.TripScreen
import com.niiros.tripplanner.ui.trips.TripListScreen
import kotlinx.serialization.Serializable

@Serializable
object TripsRoute

@Serializable
data class TripRoute(val id: String, val isNew: Boolean = false)

@Serializable
object SettingsRoute

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            TripPlannerTheme {
                val nav = rememberNavController()
                NavHost(navController = nav, startDestination = TripsRoute) {
                    composable<TripsRoute> {
                        TripListScreen(
                            onOpen = { id, isNew -> nav.navigate(TripRoute(id, isNew)) },
                            onSettings = { nav.navigate(SettingsRoute) },
                        )
                    }
                    composable<TripRoute> { entry ->
                        val route = entry.toRoute<TripRoute>()
                        TripScreen(tripId = route.id, isNew = route.isNew, onBack = { nav.popBackStack() })
                    }
                    composable<SettingsRoute> {
                        SettingsScreen(onBack = { nav.popBackStack() })
                    }
                }
            }
        }
    }
}
