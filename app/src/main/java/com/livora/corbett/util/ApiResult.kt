package com.livora.corbett.util

import com.livora.corbett.data.api.AppJson
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlin.coroutines.cancellation.CancellationException

sealed interface ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>
    data class Failure(val message: String, val code: Int? = null, val isNetwork: Boolean = false) : ApiResult<Nothing>
}

inline fun <T, R> ApiResult<T>.map(transform: (T) -> R): ApiResult<R> = when (this) {
    is ApiResult.Success -> ApiResult.Success(transform(data))
    is ApiResult.Failure -> this
}

inline fun <T> ApiResult<T>.onSuccess(block: (T) -> Unit): ApiResult<T> {
    if (this is ApiResult.Success) block(data)
    return this
}

inline fun <T> ApiResult<T>.onFailure(block: (ApiResult.Failure) -> Unit): ApiResult<T> {
    if (this is ApiResult.Failure) block(this)
    return this
}

val <T> ApiResult<T>.dataOrNull: T? get() = (this as? ApiResult.Success)?.data

/** Wraps a network call so it never throws (except coroutine cancellation). */
suspend fun <T> safeApi(block: suspend () -> T): ApiResult<T> = try {
    ApiResult.Success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: HttpException) {
    ApiResult.Failure(httpMessage(e), e.code())
} catch (e: SocketTimeoutException) {
    ApiResult.Failure(
        "The server is taking a while to respond. It may be waking up, please try again in a few seconds.",
        isNetwork = true,
    )
} catch (e: UnknownHostException) {
    ApiResult.Failure("No internet connection. Please check your network and try again.", isNetwork = true)
} catch (e: IOException) {
    ApiResult.Failure("Can't reach the server right now. Please check your connection and try again.", isNetwork = true)
} catch (e: SerializationException) {
    ApiResult.Failure("We received an unexpected response from the server. Please try again.")
} catch (e: IllegalArgumentException) {
    ApiResult.Failure("We received an unexpected response from the server. Please try again.")
} catch (e: Exception) {
    ApiResult.Failure(e.message ?: "Something went wrong. Please try again.")
}

private fun httpMessage(e: HttpException): String {
    val raw = try {
        e.response()?.errorBody()?.string()
    } catch (x: Exception) {
        null
    }
    if (!raw.isNullOrBlank()) {
        try {
            val obj: JsonObject = AppJson.parseToJsonElement(raw).jsonObject
            val msg = (obj["message"] as? JsonPrimitive)?.contentOrNull
                ?: (obj["error"] as? JsonPrimitive)?.contentOrNull
            if (!msg.isNullOrBlank()) return msg
        } catch (x: Exception) {
            // fall through to generic
        }
    }
    return when (e.code()) {
        401 -> "Your session has expired. Please sign in again."
        403 -> "You don't have permission to do that."
        404 -> "We couldn't find that."
        409 -> "That's no longer available."
        429 -> "Too many attempts. Please wait a moment and try again."
        in 500..599 -> "The server had a problem. Please try again shortly."
        else -> "Request failed (${e.code()})."
    }
}

/** Simple UI load state. */
sealed interface Load<out T> {
    data object Loading : Load<Nothing>
    data class Ready<T>(val data: T) : Load<T>
    data class Failed(val message: String) : Load<Nothing>
}

val <T> Load<T>.readyOrNull: T? get() = (this as? Load.Ready)?.data
