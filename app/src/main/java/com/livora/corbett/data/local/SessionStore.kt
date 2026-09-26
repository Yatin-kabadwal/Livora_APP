package com.livora.corbett.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.livora.corbett.data.api.AppJson
import com.livora.corbett.data.api.AuthResponse
import com.livora.corbett.data.api.User
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class Session(val accessToken: String, val refreshToken: String, val user: User)

/**
 * Holds the auth session. Tokens live in EncryptedSharedPreferences (falls back to private plain prefs
 * only if the Keystore is unusable on the device). Synchronous access so OkHttp interceptors can use it.
 */
@Singleton
class SessionStore @Inject constructor(@ApplicationContext private val context: Context) {

    private val prefs: SharedPreferences = createPrefs(context)
    private val _session = MutableStateFlow(load())
    val session: StateFlow<Session?> = _session.asStateFlow()

    val current: Session? get() = _session.value
    val accessToken: String? get() = _session.value?.accessToken
    val isLoggedIn: Boolean get() = _session.value != null

    @Synchronized
    fun save(auth: AuthResponse) {
        val prev = _session.value
        val user = if (auth.user.email.isBlank() && prev != null) prev.user else auth.user
        val s = Session(auth.accessToken, auth.refreshToken, user)
        prefs.edit()
            .putString(K_ACCESS, s.accessToken)
            .putString(K_REFRESH, s.refreshToken)
            .putString(K_USER, AppJson.encodeToString(User.serializer(), s.user))
            .apply()
        _session.value = s
    }

    @Synchronized
    fun updateUser(user: User) {
        val s = _session.value ?: return
        val merged = user.copy(role = if (user.role.isBlank()) s.user.role else user.role)
        prefs.edit().putString(K_USER, AppJson.encodeToString(User.serializer(), merged)).apply()
        _session.value = s.copy(user = merged)
    }

    @Synchronized
    fun clear() {
        prefs.edit().clear().apply()
        _session.value = null
    }

    private fun load(): Session? {
        return try {
            val a = prefs.getString(K_ACCESS, null)
            val r = prefs.getString(K_REFRESH, null)
            val u = prefs.getString(K_USER, null)
            if (a.isNullOrBlank() || r.isNullOrBlank() || u.isNullOrBlank()) null
            else Session(a, r, AppJson.decodeFromString(User.serializer(), u))
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        private const val FILE = "corbett_session"
        private const val K_ACCESS = "access"
        private const val K_REFRESH = "refresh"
        private const val K_USER = "user"

        private fun createPrefs(ctx: Context): SharedPreferences {
            fun encrypted(): SharedPreferences {
                val alias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
                return EncryptedSharedPreferences.create(
                    FILE,
                    alias,
                    ctx,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
                )
            }
            return try {
                encrypted()
            } catch (e: Exception) {
                // Corrupt keyset (e.g. restored device): wipe and retry once, then fall back.
                try {
                    ctx.deleteSharedPreferences(FILE)
                    encrypted()
                } catch (e2: Exception) {
                    ctx.getSharedPreferences(FILE + "_plain", Context.MODE_PRIVATE)
                }
            }
        }
    }
}
