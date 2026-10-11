package com.niiros.tripplanner.ui.addstop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import com.niiros.tripplanner.R
import com.niiros.tripplanner.core.model.PlaceRef
import com.niiros.tripplanner.ui.common.SectionCard
import com.niiros.tripplanner.ui.common.WeatherRow
import com.niiros.tripplanner.ui.common.appContainer
import com.niiros.tripplanner.ui.common.rememberTexts
import com.niiros.tripplanner.ui.theme.LocalTripColors

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddStopSheet(
    tripId: String,
    mode: SheetMode,
    onStartChosen: (PlaceRef) -> Unit,
    onDismiss: () -> Unit,
) {
    val container = appContainer()
    val vm: AddStopViewModel = viewModel(key = "addstop-$tripId-$mode") { AddStopViewModel(tripId, mode, container) }
    val state by vm.state.collectAsStateWithLifecycle()
    val t = rememberTexts()
    val c = LocalTripColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var customDwell by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = c.background) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val title = when (mode) {
                SheetMode.Add -> R.string.add_title
                is SheetMode.Edit -> R.string.edit_title
                SheetMode.Start -> R.string.start_title
            }
            Text(t.s(title), style = MaterialTheme.typography.titleLarge, color = c.text)

            val selected = state.selected
            if (selected == null) {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = vm::onQuery,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(t.s(R.string.add_search_hint)) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                )
                if (state.searching || state.loadingPlace) LinearProgressIndicator(Modifier.fillMaxWidth())
                state.searchError?.let { Text(t.s(R.string.error_search_failed) + " " + t.error(it), color = c.late) }
                if (!state.searching && state.query.isNotBlank() && state.results.isEmpty() && state.searchError == null) {
                    Text(t.s(R.string.add_no_results), color = c.textSecondary)
                }
                state.results.forEach { s ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .clickable(role = Role.Button) { vm.pick(s) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(Icons.Filled.Place, contentDescription = null, tint = c.structure)
                        Column {
                            Text(s.primary, style = MaterialTheme.typography.bodyLarge, color = c.text)
                            s.secondary?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = c.textSecondary) }
                        }
                    }
                    HorizontalDivider(color = c.cardBorder)
                }
            } else {
                Text(selected.name, style = MaterialTheme.typography.titleMedium, color = c.text)
                selected.address?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = c.textSecondary) }
                PinPreview(selected, state.demoMode)
                TextButton(onClick = vm::clearSelection, modifier = Modifier.sizeIn(minHeight = 48.dp)) { Text(t.s(R.string.add_change_place)) }

                if (mode == SheetMode.Start) {
                    Button(
                        onClick = { onStartChosen(selected); onDismiss() },
                        colors = ButtonDefaults.buttonColors(containerColor = c.primaryAction),
                        modifier = Modifier.fillMaxWidth().sizeIn(minHeight = 52.dp),
                    ) { Text(t.s(R.string.add_confirm_start)) }
                } else {
                    Text(t.s(R.string.add_dwell), style = MaterialTheme.typography.titleSmall, color = c.text)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AddStopViewModel.DWELL_CHIPS.forEach { m ->
                            FilterChip(
                                selected = state.dwellMinutes == m,
                                onClick = { vm.setDwell(m) },
                                label = { Text(t.duration(java.time.Duration.ofMinutes(m))) },
                                modifier = Modifier.heightIn(min = 48.dp),
                            )
                        }
                        val custom = state.dwellMinutes !in AddStopViewModel.DWELL_CHIPS
                        FilterChip(
                            selected = custom,
                            onClick = { customDwell = true },
                            label = { Text(if (custom) t.duration(java.time.Duration.ofMinutes(state.dwellMinutes)) else t.s(R.string.add_dwell_custom)) },
                            modifier = Modifier.heightIn(min = 48.dp),
                        )
                    }
                    ToggleRow(t.s(R.string.add_needs_daylight), state.needsDaylight, vm::setNeedsDaylight)
                    ToggleRow(t.s(R.string.add_mincha_here), state.minchaHere, vm::setMincha)
                    ToggleRow(t.s(R.string.add_arvit_here), state.arvitHere, vm::setArvit)

                    SectionCard {
                        Text(t.s(R.string.add_preview_title), style = MaterialTheme.typography.titleSmall, color = c.text)
                        if (state.previewLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
                        state.preview?.let { p ->
                            val zone = p.scheduled.zone
                            Text(t.s(R.string.add_preview_drive, t.duration(p.scheduled.legIn.duration)), color = c.text)
                            if (!p.scheduled.legIn.hasTraffic) Text(t.s(R.string.leg_no_traffic), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                            Text(t.s(R.string.add_preview_arrival, t.time(p.scheduled.arrival, zone)), color = c.text)
                            p.point.zmanim?.let { Text(t.s(R.string.add_preview_sunset, t.time(it.sunset, zone)), color = c.primaryAction) }
                            WeatherRow(p.point)
                            p.point.elevationM?.let { Text(t.elevation(it), color = c.textSecondary) }
                        }
                    }

                    Button(
                        onClick = { vm.confirm(onDismiss) },
                        colors = ButtonDefaults.buttonColors(containerColor = c.primaryAction),
                        modifier = Modifier.fillMaxWidth().sizeIn(minHeight = 52.dp),
                    ) { Text(t.s(if (mode is SheetMode.Edit) R.string.add_confirm_edit else R.string.add_confirm)) }
                }
            }
            Box(Modifier.height(16.dp))
        }
    }

    if (customDwell) {
        var text by remember { mutableStateOf(state.dwellMinutes.toString()) }
        AlertDialog(
            onDismissRequest = { customDwell = false },
            title = { Text(t.s(R.string.add_dwell_custom_title)) },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { v -> text = v.filter { it.isDigit() }.take(4) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = { text.toLongOrNull()?.let(vm::setDwell); customDwell = false }) { Text(t.s(R.string.action_ok)) }
            },
            dismissButton = { TextButton(onClick = { customDwell = false }) { Text(t.s(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(role = Role.Switch) { onChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = LocalTripColors.current.text)
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun PinPreview(place: PlaceRef, demoMode: Boolean) {
    val t = rememberTexts()
    val c = LocalTripColors.current
    val description = t.s(R.string.add_map_description, place.name)
    if (demoMode) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(120.dp)
                .background(c.card, MaterialTheme.shapes.medium)
                .semantics { contentDescription = description },
            contentAlignment = Alignment.Center,
        ) {
            Text(t.s(R.string.demo_map_placeholder), color = c.textSecondary)
        }
        return
    }
    val latLng = LatLng(place.point.lat, place.point.lng)
    val camera = rememberCameraPositionState { position = CameraPosition.fromLatLngZoom(latLng, 13f) }
    val marker = remember(latLng) { MarkerState(position = latLng) }
    LaunchedEffect(latLng) { camera.position = CameraPosition.fromLatLngZoom(latLng, 13f) }
    GoogleMap(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .semantics { contentDescription = description },
        cameraPositionState = camera,
        uiSettings = MapUiSettings(zoomControlsEnabled = false, mapToolbarEnabled = false, myLocationButtonEnabled = false),
    ) {
        Marker(state = marker, title = place.name)
    }
}
