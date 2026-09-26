package com.livora.corbett.data.api

import com.livora.corbett.data.local.SessionStore
import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import java.io.IOException

/** Adds the Bearer token (when signed in) to every request. */
class AuthInterceptor(private val session: SessionStore) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val req = chain.request()
        val builder = req.newBuilder().header("Accept", "application/json")
        val token = session.accessToken
        val path = req.url.encodedPath
        val isAuthEntry = path.endsWith("/auth/login") || path.endsWith("/auth/register") ||
            path.endsWith("/auth/refresh") || path.endsWith("/auth/forgot-password") || path.endsWith("/auth/reset-password")
        if (token != null && !isAuthEntry && req.header("Authorization") == null) {
            builder.header("Authorization", "Bearer $token")
        }
        return chain.proceed(builder.build())
    }
}

/**
 * Single-flight refresh with ROTATING refresh tokens: only one thread refreshes at a time; the
 * others see the already-rotated access token and simply retry. Both new tokens are stored.
 * If the refresh token is rejected, the session is cleared (UI navigates to login).
 */
class TokenAuthenticator(
    private val session: SessionStore,
    private val authApi: AuthApi,
) : Authenticator {

    private val lock = Any()

    override fun authenticate(route: Route?, response: Response): Request? {
        val sentAuth = response.request.header("Authorization") ?: return null // not an authed call (e.g. wrong password)
        if (responseCount(response) >= 3) return null
        val sentToken = sentAuth.removePrefix("Bearer ").trim()

        return synchronized(lock) {
            val current = session.current
            if (current == null) {
                null
            } else if (current.accessToken != sentToken) {
                // Someone else already refreshed while we waited for the lock.
                retryWith(response.request, current.accessToken)
            } else {
                try {
                    val r = authApi.refresh(RefreshBody(current.refreshToken)).execute()
                    val body = r.body()
                    if (r.isSuccessful && body != null && body.accessToken.isNotBlank()) {
                        session.save(body)
                        retryWith(response.request, body.accessToken)
                    } else {
                        if (r.code() in 400..403) session.clear()
                        null
                    }
                } catch (e: IOException) {
                    null // offline: keep the session, the call simply fails
                }
            }
        }
    }

    private fun retryWith(request: Request, token: String): Request =
        request.newBuilder().header("Authorization", "Bearer $token").build()

    private fun responseCount(response: Response): Int {
        var r: Response? = response
        var n = 1
        while (r?.priorResponse != null) {
            n++
            r = r.priorResponse
        }
        return n
    }
}
