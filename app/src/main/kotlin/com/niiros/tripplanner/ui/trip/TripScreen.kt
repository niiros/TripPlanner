package com.niiros.tripplanner.ui.trip

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.niiros.tripplanner.R
import com.niiros.tripplanner.core.model.ServiceError
import com.niiros.tripplanner.ui.addstop.AddStopSheet
import com.niiros.tripplanner.ui.addstop.SheetMode
import com.niiros.tripplanner.ui.common.Banner
import com.niiros.tripplanner.ui.common.BannerKind
import com.niiros.tripplanner.ui.common.Intents
import com.niiros.tripplanner.ui.common.appContainer
import com.niiros.tripplanner.ui.common.rememberTexts
import com.niiros.tripplanner.ui.theme.LocalTripColors
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import java.time.Instant
import java.time.ZoneOffset

private const val STOP_KEY_PREFIX = "stop:"

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TripScreen(tripId: String, isNew: Boolean, onBack: () -> Unit) {
    val container = appContainer()
    val vm: TripViewModel = viewModel(key = "trip-$tripId") { TripViewModel(tripId, isNew, container) }
    val state by vm.state.collectAsStateWithLifecycle()
    val t = rememberTexts()
    val c = LocalTripColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var sheet by remember { mutableStateOf<SheetMode?>(null) }
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<Pair<String, String>?>(null) }

    LaunchedEffect(vm) { vm.messages.collect { snackbar.showSnackbar(it) } }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.any { it }) {
            vm.useCurrentLocation(t.s(R.string.trip_my_location))
        } else {
            vm.onLocationDenied()
            sheet = SheetMode.Start
        }
    }
    fun requestLocation() {
        permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }

    // A brand-new trip starts from the current location (or a search if permission is denied).
    var askedForStart by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.trip?.start, state.trip) {
        val trip = state.trip
        if (trip != null && trip.start == null && !askedForStart) {
            askedForStart = true
            requestLocation()
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            scope.launch {
                val json = vm.exportJson() ?: return@launch
                context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray(Charsets.UTF_8)) }
            }
        }
    }

    val plan = state.plan
    val trip = state.trip

    Scaffold(
        containerColor = c.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(trip?.name ?: "", maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = t.s(R.string.action_back)) }
                },
                actions = {
                    IconButton(onClick = { vm.shareText(t)?.let { Intents.shareText(context, it) } }) {
                        Icon(Icons.Filled.Share, contentDescription = t.s(R.string.trip_share))
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, contentDescription = t.s(R.string.action_more)) }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(text = { Text(t.s(R.string.trip_export)) }, onClick = {
                                menuOpen = false
                                exportLauncher.launch((trip?.name ?: "trip").replace(Regex("[\\\\/:*?\"<>|]"), "_") + ".json")
                            })
                            DropdownMenuItem(text = { Text(t.s(R.string.trip_rename)) }, onClick = { menuOpen = false; showRename = true })
                            DropdownMenuItem(
                                text = { Text(t.s(R.string.action_retry)) },
                                leadingIcon = { Icon(Icons.Filled.Refresh, contentDescription = null) },
                                onClick = { menuOpen = false; vm.retry() },
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = c.background, titleContentColor = c.text),
            )
        },
    ) { padding ->
        if (trip == null) {
            LinearProgressIndicator(Modifier.fillMaxWidth().padding(padding))
            return@Scaffold
        }

        // Local order while dragging; committed to the database when the drag ends.
        val order = remember { mutableStateListOf<String>() }
        val tripOrder = trip.stops.map { it.id }
        var dragging by remember { mutableStateOf(false) }
        if (!dragging && order.toList() != tripOrder) {
            order.clear(); order.addAll(tripOrder)
        }
        val listState = rememberLazyListState()
        val reorderState = rememberReorderableLazyListState(listState) { from, to ->
            val fromId = (from.key as? String)?.removePrefix(STOP_KEY_PREFIX)
            val toId = (to.key as? String)?.takeIf { it.startsWith(STOP_KEY_PREFIX) }?.removePrefix(STOP_KEY_PREFIX)
            if (fromId != null && toId != null) {
                val i = order.indexOf(fromId); val j = order.indexOf(toId)
                if (i >= 0 && j >= 0) order.add(j, order.removeAt(i))
            }
        }
        val stopPlans = plan?.stops?.associateBy { it.scheduled.stop.id }.orEmpty()

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "progress") {
                if (state.calculating) LinearProgressIndicator(Modifier.fillMaxWidth()) else Box(Modifier.padding(0.dp))
            }
            if (state.demoMode) {
                item(key = "demo") { Banner(t.s(R.string.demo_banner_text), BannerKind.INFO, title = t.s(R.string.demo_banner_title)) }
            }
            item(key = "header") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { showDate = true }, modifier = Modifier.sizeIn(minHeight = 48.dp)) {
                            Icon(Icons.Filled.DateRange, contentDescription = null)
                            Text(" " + t.date(trip.date))
                        }
                        OutlinedButton(onClick = { showTime = true }, modifier = Modifier.sizeIn(minHeight = 48.dp)) {
                            Text(t.s(R.string.trip_departure) + " " + t.time(trip.departureTime))
                        }
                        Button(
                            onClick = { vm.leftNow(t) },
                            enabled = plan != null && trip.start != null,
                            colors = ButtonDefaults.buttonColors(containerColor = c.structure),
                            modifier = Modifier.sizeIn(minHeight = 48.dp),
                        ) { Text(t.s(R.string.trip_left_now)) }
                    }
                    plan?.let { Text(t.s(R.string.trip_zone, t.zoneName(it.schedule.startZone)), style = MaterialTheme.typography.bodyMedium, color = c.textSecondary) }
                }
            }
            plan?.let { p ->
                val alerts = buildList {
                    state.locationProblem?.let { add(t.s(it) to BannerKind.WARNING) }
                    if (p.zoneUncertain) add(t.s(R.string.error_zone_uncertain) to BannerKind.WARNING)
                    if (p.multiZone) add(t.s(R.string.trip_zone_multi) to BannerKind.WARNING)
                    p.errors.forEach { e -> add(t.error(e) to if (e == ServiceError.NO_ROUTE || e == ServiceError.OFFLINE) BannerKind.WARNING else BannerKind.ERROR) }
                    if (p.stops.any { it.point.weatherState == com.niiros.tripplanner.core.plan.WeatherState.FAILED } && ServiceError.OFFLINE !in p.errors) {
                        add(t.s(R.string.error_weather_unavailable) to BannerKind.WARNING)
                    }
                    p.prayer.minchaMissingDeadline?.let { d ->
                        val zone = p.stops.firstOrNull { it.scheduled.stop.id == p.prayer.minchaDeadlineZoneStopId }?.scheduled?.zone ?: p.summaryZone
                        add(t.s(R.string.prayer_mincha_missing, t.time(d, zone)) to BannerKind.WARNING)
                    }
                }.distinct()
                alerts.forEachIndexed { i, (text, kind) -> item(key = "alert-$i") { Banner(text, kind) } }

                item(key = "zmanim") {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        ZmanimCard(p.summaryZmanim, p.summaryPlaceName, p.summaryZone)
                        p.prayer.arvitTzeitNote?.let {
                            Text(t.s(R.string.prayer_arvit_tzeit_note, t.time(it, p.summaryZone)), style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
                        }
                    }
                }
                item(key = "clothing") { ClothingCard(p.clothing) }
                item(key = "daylight") { DaylightBar(p) }
            }
            item(key = "start") {
                StartCard(
                    point = plan?.start,
                    departure = plan?.schedule?.departure ?: Instant.now(),
                    zone = plan?.schedule?.startZone ?: java.time.ZoneId.systemDefault(),
                    onChangeStart = { sheet = SheetMode.Start },
                    onUseLocation = { requestLocation() },
                )
            }
            order.forEachIndexed { index, stopId ->
                val sp = stopPlans[stopId] ?: return@forEachIndexed
                item(key = STOP_KEY_PREFIX + stopId) {
                    ReorderableItem(reorderState, key = STOP_KEY_PREFIX + stopId) { _ ->
                        Column {
                            LegRow(sp.scheduled.legIn)
                            StopCard(
                                sp = sp,
                                prayer = plan!!.prayer,
                                startZone = plan.schedule.startZone,
                                isFirst = index == 0,
                                isLast = index == order.lastIndex,
                                onEdit = { sheet = SheetMode.Edit(stopId) },
                                onDelete = { confirmDelete = stopId to sp.point.place.name },
                                onMove = { delta -> vm.moveStop(stopId, delta) },
                                onFix = vm::applyFix,
                                onNavigate = { point, _, waze -> Intents.navigate(context, point, waze) },
                                dragHandle = {
                                    IconButton(
                                        onClick = {},
                                        modifier = Modifier.draggableHandle(
                                            onDragStarted = { dragging = true },
                                            onDragStopped = { dragging = false; vm.reorder(order.toList()) },
                                        ),
                                    ) {
                                        Icon(painterResource(R.drawable.ic_drag_handle), contentDescription = t.s(R.string.stop_drag))
                                    }
                                },
                            )
                        }
                    }
                }
            }
            item(key = "add") {
                Button(
                    onClick = { sheet = SheetMode.Add },
                    enabled = trip.start != null,
                    colors = ButtonDefaults.buttonColors(containerColor = c.primaryAction),
                    modifier = Modifier.fillMaxWidth().sizeIn(minHeight = 52.dp),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text(" " + t.s(R.string.trip_add_stop), style = MaterialTheme.typography.titleMedium)
                }
            }
            plan?.let { p -> if (p.stops.isNotEmpty()) item(key = "footer") { FooterTotals(p) } }
        }
    }

    sheet?.let { mode ->
        AddStopSheet(
            tripId = tripId,
            mode = mode,
            onStartChosen = { place -> vm.setStart(place) },
            onDismiss = { sheet = null },
        )
    }

    if (showDate && trip != null) {
        val dateState = rememberDatePickerState(
            initialSelectedDateMillis = trip.date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    dateState.selectedDateMillis?.let { vm.setDate(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    showDate = false
                }) { Text(t.s(R.string.action_ok)) }
            },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text(t.s(R.string.action_cancel)) } },
        ) { DatePicker(state = dateState) }
    }

    if (showTime && trip != null) {
        val timeState = rememberTimePickerState(trip.departureTime.hour, trip.departureTime.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { showTime = false },
            title = { Text(t.s(R.string.trip_departure)) },
            text = { TimePicker(state = timeState) },
            confirmButton = {
                TextButton(onClick = {
                    vm.setDepartureTime(java.time.LocalTime.of(timeState.hour, timeState.minute))
                    showTime = false
                }) { Text(t.s(R.string.action_ok)) }
            },
            dismissButton = { TextButton(onClick = { showTime = false }) { Text(t.s(R.string.action_cancel)) } },
        )
    }

    if (showRename && trip != null) {
        var name by remember { mutableStateOf(trip.name) }
        AlertDialog(
            onDismissRequest = { showRename = false },
            title = { Text(t.s(R.string.trip_rename)) },
            text = { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(t.s(R.string.trip_name_label)) }, singleLine = true) },
            confirmButton = { TextButton(onClick = { vm.rename(name); showRename = false }) { Text(t.s(R.string.action_save)) } },
            dismissButton = { TextButton(onClick = { showRename = false }) { Text(t.s(R.string.action_cancel)) } },
        )
    }

    confirmDelete?.let { (id, name) ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text(t.s(R.string.stop_delete_confirm, name)) },
            confirmButton = { TextButton(onClick = { vm.deleteStop(id); confirmDelete = null }) { Text(t.s(R.string.action_delete)) } },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text(t.s(R.string.action_cancel)) } },
        )
    }
}
