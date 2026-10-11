package com.niiros.tripplanner.ui.trips

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.niiros.tripplanner.R
import com.niiros.tripplanner.ui.common.Banner
import com.niiros.tripplanner.ui.common.BannerKind
import com.niiros.tripplanner.ui.common.SectionCard
import com.niiros.tripplanner.ui.common.appContainer
import com.niiros.tripplanner.ui.common.rememberTexts
import com.niiros.tripplanner.ui.theme.LocalTripColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripListScreen(onOpen: (String, Boolean) -> Unit, onSettings: () -> Unit) {
    val container = appContainer()
    val vm: TripListViewModel = viewModel { TripListViewModel(container) }
    val trips by vm.trips.collectAsStateWithLifecycle()
    val t = rememberTexts()
    val c = LocalTripColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var menuOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<Pair<String, String>?>(null) }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val text = withContext(Dispatchers.IO) {
                    runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } }.getOrNull()
                }
                if (text == null) {
                    snackbar.showSnackbar(t.s(R.string.import_failed))
                } else {
                    vm.import(text) { id ->
                        scope.launch { snackbar.showSnackbar(t.s(if (id != null) R.string.import_done else R.string.import_failed)) }
                    }
                }
            }
        }
    }

    Scaffold(
        containerColor = c.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(t.s(R.string.trips_title)) },
                actions = {
                    IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, contentDescription = t.s(R.string.trips_settings)) }
                    Box {
                        IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, contentDescription = t.s(R.string.action_more)) }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(text = { Text(t.s(R.string.trips_load_sample)) }, onClick = {
                                menuOpen = false
                                vm.loadSample { id -> onOpen(id, false) }
                            })
                            DropdownMenuItem(text = { Text(t.s(R.string.trips_import)) }, onClick = {
                                menuOpen = false
                                importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
                            })
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = c.background, titleContentColor = c.text),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { vm.createTrip({ d -> t.s(R.string.trips_default_name, d) }) { id -> onOpen(id, true) } },
                containerColor = c.primaryAction,
                contentColor = androidx.compose.ui.graphics.Color.White,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(t.s(R.string.trips_new)) },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (vm.demoMode) {
                item { Banner(t.s(R.string.demo_banner_text), BannerKind.INFO, title = t.s(R.string.demo_banner_title)) }
            }
            val list = trips
            if (list != null && list.isEmpty()) {
                item { Text(t.s(R.string.trips_empty), color = c.textSecondary, style = MaterialTheme.typography.bodyLarge) }
            }
            items(list.orEmpty(), key = { it.id }) { trip ->
                SectionCard(Modifier.clickable(role = Role.Button) { onOpen(trip.id, false) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(trip.name, style = MaterialTheme.typography.titleMedium, color = c.text)
                            val subtitle = trip.startName?.let { t.s(R.string.trips_item_subtitle, t.date(trip.date), it) }
                                ?: t.s(R.string.trips_item_no_start, t.date(trip.date))
                            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                        }
                        var itemMenu by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { itemMenu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = t.s(R.string.action_more)) }
                            DropdownMenu(expanded = itemMenu, onDismissRequest = { itemMenu = false }) {
                                DropdownMenuItem(text = { Text(t.s(R.string.trips_open)) }, onClick = { itemMenu = false; onOpen(trip.id, false) })
                                DropdownMenuItem(text = { Text(t.s(R.string.trips_duplicate)) }, onClick = {
                                    itemMenu = false
                                    vm.duplicate(trip.id, t.s(R.string.trips_duplicate_suffix))
                                })
                                DropdownMenuItem(text = { Text(t.s(R.string.action_delete)) }, onClick = {
                                    itemMenu = false
                                    confirmDelete = trip.id to trip.name
                                })
                            }
                        }
                    }
                }
            }
        }
    }

    confirmDelete?.let { (id, name) ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text(t.s(R.string.trips_delete_confirm_title)) },
            text = { Text(t.s(R.string.trips_delete_confirm_text, name)) },
            confirmButton = { TextButton(onClick = { vm.delete(id); confirmDelete = null }) { Text(t.s(R.string.action_delete)) } },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text(t.s(R.string.action_cancel)) } },
        )
    }
}
