package com.livora.corbett.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.appDataStore: DataStore<Preferences> by preferencesDataStore(name = "corbett_prefs")

/** Non-secret preferences (DataStore). */
@Singleton
class AppPrefs @Inject constructor(@ApplicationContext private val context: Context) {
    private val store get() = context.appDataStore

    private val kOnboarding = booleanPreferencesKey("onboarding_seen")
    private val kTheme = stringPreferencesKey("theme_mode")
    private val kNotif = booleanPreferencesKey("notifications_enabled")
    private val kThreads = stringSetPreferencesKey("thread_tokens")
    private val kStaffPrompt = booleanPreferencesKey("staff_alert_prompt_shown")
    private val kLastEmail = stringPreferencesKey("last_email")

    val onboardingSeen: Flow<Boolean> = store.data.map { it[kOnboarding] ?: false }
    /** "dark" (default), "light" or "system". */
    val themeMode: Flow<String> = store.data.map { it[kTheme] ?: "dark" }
    val notificationsEnabled: Flow<Boolean> = store.data.map { it[kNotif] ?: true }
    val threadTokens: Flow<Set<String>> = store.data.map { it[kThreads] ?: emptySet() }
    val staffPromptShown: Flow<Boolean> = store.data.map { it[kStaffPrompt] ?: false }
    val lastEmail: Flow<String> = store.data.map { it[kLastEmail] ?: "" }

    suspend fun setOnboardingSeen() { store.edit { it[kOnboarding] = true } }
    suspend fun setThemeMode(mode: String) { store.edit { it[kTheme] = mode } }
    suspend fun setNotificationsEnabled(on: Boolean) { store.edit { it[kNotif] = on } }
    suspend fun setStaffPromptShown() { store.edit { it[kStaffPrompt] = true } }
    suspend fun setLastEmail(email: String) { store.edit { it[kLastEmail] = email } }
    suspend fun addThreadToken(token: String) {
        store.edit { it[kThreads] = (it[kThreads] ?: emptySet()) + token }
    }
    suspend fun removeThreadToken(token: String) {
        store.edit { it[kThreads] = (it[kThreads] ?: emptySet()) - token }
    }
}
