package com.clipforge.ai.core.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.clipforge.ai.core.utils.Constants
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

val Context.userDataStore: DataStore<Preferences> by preferencesDataStore(name = Constants.PREFS_NAME)

data class UserPrefs(
    val isPro: Boolean = false,
    val dailyExportCount: Int = 0,
    val lastExportDate: String = "",
    val defaultQuality: String = "720p",
    val notificationsEnabled: Boolean = true
)

class UserPreferencesManager(private val context: Context) {

    private val IS_PRO               = booleanPreferencesKey(Constants.KEY_IS_PRO)
    private val DAILY_EXPORT_COUNT   = intPreferencesKey(Constants.KEY_DAILY_EXPORT_COUNT)
    private val LAST_EXPORT_DATE     = stringPreferencesKey(Constants.KEY_LAST_EXPORT_DATE)
    private val DEFAULT_QUALITY      = stringPreferencesKey("default_quality")
    private val NOTIFICATIONS        = booleanPreferencesKey("notifications_enabled")

    val userPrefs: Flow<UserPrefs> = context.userDataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { prefs ->
            UserPrefs(
                isPro               = prefs[IS_PRO]             ?: false,
                dailyExportCount    = prefs[DAILY_EXPORT_COUNT] ?: 0,
                lastExportDate      = prefs[LAST_EXPORT_DATE]   ?: "",
                defaultQuality      = prefs[DEFAULT_QUALITY]    ?: "720p",
                notificationsEnabled = prefs[NOTIFICATIONS]     ?: true
            )
        }

    suspend fun setIsPro(isPro: Boolean) {
        context.userDataStore.edit { it[IS_PRO] = isPro }
    }

    suspend fun incrementDailyExport(todayDate: String) {
        context.userDataStore.edit { prefs ->
            val lastDate = prefs[LAST_EXPORT_DATE] ?: ""
            val count    = if (lastDate == todayDate) (prefs[DAILY_EXPORT_COUNT] ?: 0) else 0
            prefs[DAILY_EXPORT_COUNT] = count + 1
            prefs[LAST_EXPORT_DATE]   = todayDate
        }
    }

    suspend fun setDefaultQuality(quality: String) {
        context.userDataStore.edit { it[DEFAULT_QUALITY] = quality }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.userDataStore.edit { it[NOTIFICATIONS] = enabled }
    }

    suspend fun canExportToday(todayDate: String, isPro: Boolean): Boolean {
        if (isPro) return true
        var count = 0
        var lastDate = ""
        context.userDataStore.data.catch { emit(emptyPreferences()) }.collect { prefs ->
            lastDate = prefs[LAST_EXPORT_DATE] ?: ""
            count    = prefs[DAILY_EXPORT_COUNT] ?: 0
        }
        return lastDate != todayDate || count < com.clipforge.ai.core.ads.AdConfig.FREE_DAILY_EXPORT_LIMIT
    }
}
