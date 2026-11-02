package uz.callguard.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

data class ProtectionRuleSettings(
    val blockHiddenNumbers: Boolean = false,
    val blockForeignNumbers: Boolean = false
)

enum class BlockMode {
    WARN,   // Только предупреждать (оверлей / пуш), не сбрасывать и не глушить звук
    MUTE,   // Отключить звук звонка
    REJECT  // Отклонить звонок (сбросить трубку)
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "callguard_settings")

class SettingsRepository(private val context: Context) {

    private val courierModeEndKey = longPreferencesKey("courier_mode_end_timestamp")
    val courierModeEndFlow: Flow<Long> = context.dataStore.data.map { it[courierModeEndKey] ?: 0L }

    suspend fun pauseProtection(durationMs: Long) {
        require(durationMs == CourierMode.HALF_HOUR_MS || durationMs == CourierMode.ONE_HOUR_MS)
        context.dataStore.edit { it[courierModeEndKey] = System.currentTimeMillis() + durationMs }
    }

    suspend fun resumeProtection() {
        context.dataStore.edit { it[courierModeEndKey] = 0L }
    }

    private val blockHiddenNumbersKey = booleanPreferencesKey("block_hidden_numbers")
    private val blockForeignNumbersKey = booleanPreferencesKey("block_foreign_numbers")

    val protectionRulesFlow: Flow<ProtectionRuleSettings> = context.dataStore.data.map { preferences ->
        ProtectionRuleSettings(
            blockHiddenNumbers = preferences[blockHiddenNumbersKey] ?: false,
            blockForeignNumbers = preferences[blockForeignNumbersKey] ?: false
        )
    }

    suspend fun setBlockHiddenNumbers(enabled: Boolean) {
        context.dataStore.edit { it[blockHiddenNumbersKey] = enabled }
    }

    suspend fun setBlockForeignNumbers(enabled: Boolean) {
        context.dataStore.edit { it[blockForeignNumbersKey] = enabled }
    }

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
