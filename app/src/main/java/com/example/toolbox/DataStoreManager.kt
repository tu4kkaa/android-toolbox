// DataStoreManager.kt
package com.example.toolbox

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "toolbox_prefs")

class DataStoreManager(private val context: Context) {
    companion object {
        val LEVEL_OFFSET = doublePreferencesKey("level_offset")
        val SOUND_CALIB = doublePreferencesKey("sound_calib")
    }

    suspend fun saveLevelOffset(offset: Double) {
        context.dataStore.edit { prefs ->
            prefs[LEVEL_OFFSET] = offset
        }
    }

    suspend fun getLevelOffset(): Double {
        val flow = context.dataStore.data.map { prefs -> prefs[LEVEL_OFFSET] ?: 0.0 }
        return flow.first()
    }

    suspend fun saveSoundCalib(offset: Double) {
        context.dataStore.edit { prefs ->
            prefs[SOUND_CALIB] = offset
        }
    }

    suspend fun getSoundCalib(): Double {
        val flow = context.dataStore.data.map { prefs -> prefs[SOUND_CALIB] ?: 0.0 }
        return flow.first()
    }
}
