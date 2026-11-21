package com.example.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.domain.AppLanguage
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "crafterp_prefs")

class SessionPreferences(private val context: Context) {
    private val keyUserId = stringPreferencesKey("logged_in_user_id")
    private val keyLanguage = stringPreferencesKey("app_language")
    private val keyTheme = stringPreferencesKey("theme_mode")

    val loggedInUserId: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[keyUserId]?.takeIf { it.isNotBlank() }
    }

    val language: Flow<AppLanguage> = context.dataStore.data.map { prefs ->
        AppLanguage.fromCode(prefs[keyLanguage] ?: "en")
    }

    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { prefs ->
        ThemeMode.entries.firstOrNull { it.name == prefs[keyTheme] } ?: ThemeMode.SYSTEM
    }

    suspend fun setLoggedInUserId(userId: String?) {
        context.dataStore.edit { prefs ->
            if (userId.isNullOrBlank()) prefs.remove(keyUserId)
            else prefs[keyUserId] = userId
        }
    }

    suspend fun setLanguage(language: AppLanguage) {
        context.dataStore.edit { prefs ->
            prefs[keyLanguage] = language.code
        }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { prefs ->
            prefs[keyTheme] = mode.name
        }
    }
}
