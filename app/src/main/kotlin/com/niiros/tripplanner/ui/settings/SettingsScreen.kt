package com.niiros.tripplanner.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.niiros.tripplanner.AppContainer
import com.niiros.tripplanner.R
import com.niiros.tripplanner.core.model.TzeitMethod
import com.niiros.tripplanner.data.settings.AppSettings
import com.niiros.tripplanner.ui.common.SectionCard
import com.niiros.tripplanner.ui.common.appContainer
import com.niiros.tripplanner.ui.common.rememberTexts
import com.niiros.tripplanner.ui.theme.LocalTripColors
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val container: AppContainer) : ViewModel() {
    val settings: StateFlow<AppSettings> =
        container.settings.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())
    val demoMode: Boolean get() = container.demoMode

    fun setBuffer(minutes: Int) = viewModelScope.launch { container.settings.setSunsetBuffer(minutes) }
    fun setTzeit(method: TzeitMethod) = viewModelScope.launch { container.settings.setTzeitMethod(method) }
    fun setNotifications(enabled: Boolean) = viewModelScope.launch { container.settings.setNotificationsEnabled(enabled) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, notificationsSection: @Composable (SettingsViewModel) -> Unit = {}) {
    val container = appContainer()
    val vm: SettingsViewModel = viewModel { SettingsViewModel(container) }
    val settings by vm.settings.collectAsStateWithLifecycle()
    val t = rememberTexts()
    val c = LocalTripColors.current

    Scaffold(
        containerColor = c.background,
        topBar = {
            TopAppBar(
                title = { Text(t.s(R.string.settings_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = t.s(R.string.action_back)) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = c.background, titleContentColor = c.text),
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionCard {
                Text(t.s(R.string.settings_buffer), style = MaterialTheme.typography.titleMedium, color = c.text)
                var value by remember(settings.sunsetBufferMinutes) { mutableFloatStateOf(settings.sunsetBufferMinutes.toFloat()) }
                Text(t.s(R.string.settings_buffer_value, value.toInt()), style = MaterialTheme.typography.titleLarge, color = c.primaryAction)
                Slider(
                    value = value,
                    onValueChange = { value = (it / 5).toInt() * 5f },
                    onValueChangeFinished = { vm.setBuffer(value.toInt()) },
                    valueRange = 0f..120f,
                    steps = 23,
                )
            }
            SectionCard {
                Text(t.s(R.string.settings_tzeit), style = MaterialTheme.typography.titleMedium, color = c.text)
                TzeitMethod.entries.forEach { m ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .selectable(selected = settings.tzeitMethod == m, role = Role.RadioButton) { vm.setTzeit(m) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = settings.tzeitMethod == m, onClick = null)
                        Text(t.tzeitMethod(m), Modifier.padding(start = 8.dp), color = c.text)
                    }
                }
                Text(t.s(R.string.settings_method_note), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
            }
            notificationsSection(vm)
            SectionCard {
                Text(t.s(R.string.settings_key_status), style = MaterialTheme.typography.titleMedium, color = c.text)
                Text(t.s(if (vm.demoMode) R.string.settings_key_absent else R.string.settings_key_present), color = if (vm.demoMode) c.midday else c.ok)
                if (vm.demoMode) Text(t.s(R.string.demo_banner_text), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
            }
            SectionCard {
                Text(t.s(R.string.settings_data_sources), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
            }
        }
    }
}
