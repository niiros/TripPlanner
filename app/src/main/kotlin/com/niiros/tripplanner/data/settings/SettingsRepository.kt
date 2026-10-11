package com.niiros.tripplanner.data.settings

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.niiros.tripplanner.core.model.PlannerSettings
import com.niiros.tripplanner.core.model.TzeitMethod
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Duration

data class AppSettings(
    val sunsetBufferMinutes: Int = DEFAULT_BUFFER_MINUTES,
    val tzeitMethod: TzeitMethod = TzeitMethod.DEGREES_8_5,
    val notificationsEnabled: Boolean = false,
) {
    val planner: PlannerSettings get() = PlannerSettings(Duration.ofMinutes(sunsetBufferMinutes.toLong()), tzeitMethod)

    companion object {
        const val DEFAULT_BUFFER_MINUTES = 30
    }
}

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {
    private object Keys {
        val buffer = intPreferencesKey("sunset_buffer_minutes")
        val tzeit = stringPreferencesKey("tzeit_method")
        val notifications = booleanPreferencesKey("notifications_enabled")
        val sampleSeeded = booleanPreferencesKey("sample_seeded")
    }

    /** True once the demo sample day was inserted automatically, so it is not re-added after deletion. */
    suspend fun markSampleSeeded(): Boolean {
        var already = false
        context.dataStore.edit {
            already = it[Keys.sampleSeeded] == true
            it[Keys.sampleSeeded] = true
        }
        return already
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { it.toSettings() }

    private fun Preferences.toSettings() = AppSettings(
        sunsetBufferMinutes = this[Keys.buffer] ?: AppSettings.DEFAULT_BUFFER_MINUTES,
        tzeitMethod = this[Keys.tzeit]?.let { name -> TzeitMethod.entries.firstOrNull { it.name == name } } ?: TzeitMethod.DEGREES_8_5,
        notificationsEnabled = this[Keys.notifications] ?: false,
    )

    suspend fun setSunsetBuffer(minutes: Int) {
        context.dataStore.edit { it[Keys.buffer] = minutes.coerceIn(0, 180) }
    }

    suspend fun setTzeitMethod(method: TzeitMethod) {
        context.dataStore.edit { it[Keys.tzeit] = method.name }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.notifications] = enabled }
    }
}
