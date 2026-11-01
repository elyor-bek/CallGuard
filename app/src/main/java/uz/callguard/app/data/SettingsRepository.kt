package uz.callguard.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

enum class BlockMode {
    WARN,   // Только предупреждать (оверлей / пуш), не сбрасывать и не глушить звук
    MUTE,   // Отключить звук звонка
    REJECT  // Отклонить звонок (сбросить трубку)
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "callguard_settings")

class SettingsRepository(private val context: Context) {

    private val blockModeKey = stringPreferencesKey("block_mode")

    val blockModeFlow: Flow<BlockMode> = context.dataStore.data
        .map { preferences ->
            val modeName = preferences[blockModeKey] ?: BlockMode.WARN.name
            try {
                BlockMode.valueOf(modeName)
            } catch (_: Exception) {
                BlockMode.WARN
            }
        }

    suspend fun getBlockMode(): BlockMode {
        return blockModeFlow.first()
    }

    suspend fun setBlockMode(mode: BlockMode) {
        context.dataStore.edit { preferences ->
            preferences[blockModeKey] = mode.name
        }
    }
}
