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
        private val LEVEL_OFFSET_EDGE = doublePreferencesKey("level_offset_edge")
        private val LEVEL_OFFSET_FLAT = doublePreferencesKey("level_offset_flat")
        val SOUND_CALIB = doublePreferencesKey("sound_calib")
    }

    suspend fun saveLevelOffsetEdge(offset: Double) {
        context.dataStore.edit { prefs ->
            prefs[LEVEL_OFFSET_EDGE] = offset
        }
    }

    suspend fun getLevelOffsetEdge(): Double {
        val flow = context.dataStore.data.map { prefs -> prefs[LEVEL_OFFSET_EDGE] ?: 0.0 }
        return flow.first()
    }

    suspend fun saveLevelOffsetFlat(offset: Double) {
        context.dataStore.edit { prefs ->
            prefs[LEVEL_OFFSET_FLAT] = offset
        }
    }

    suspend fun getLevelOffsetFlat(): Double {
        val flow = context.dataStore.data.map { prefs -> prefs[LEVEL_OFFSET_FLAT] ?: 0.0 }
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
