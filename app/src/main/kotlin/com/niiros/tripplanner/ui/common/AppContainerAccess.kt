package com.niiros.tripplanner.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.niiros.tripplanner.AppContainer
import com.niiros.tripplanner.TripPlannerApp

@Composable
fun appContainer(): AppContainer = (LocalContext.current.applicationContext as TripPlannerApp).container
