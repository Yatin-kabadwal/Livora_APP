package com.livora.corbett.data.realtime

import android.os.Handler
import android.os.Looper
import com.livora.corbett.BuildConfig
import com.livora.corbett.data.local.SessionStore
import io.socket.client.IO
import io.socket.client.Socket
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class RealtimeEvent(val name: String, val payload: String?)

/**
 * Socket.IO client. Connected only while the app is in the foreground (see MainActivity) and signed in.
 * Every screen must still refetch on resume: the socket is a best-effort accelerator.
 */
@Singleton
class SocketManager @Inject constructor(private val session: SessionStore) {

    private val handler = Handler(Looper.getMainLooper())
    private var socket: Socket? = null
    private var wanted = false
    private var retryCount = 0

    private val _events = MutableSharedFlow<RealtimeEvent>(extraBufferCapacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val events: SharedFlow<RealtimeEvent> = _events.asSharedFlow()

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private val eventNames = listOf(
        "booking:new", "booking:update",
        "message:new", "message:update",
        "roomservice:new", "roomservice:update",
        "housekeeping:update",
    )

    @Synchronized
    fun start() {
        wanted = true
        if (socket != null) return
        val token = session.accessToken ?: return
        connect(token)
    }

    @Synchronized
    fun stop() {
        wanted = false
        handler.removeCallbacksAndMessages(null)
        teardown()
    }

    private fun teardown() {
        try {
            socket?.off()
            socket?.disconnect()
            socket?.close()
        } catch (e: Exception) {
            // ignore
        }
        socket = null
        _connected.value = false
    }

    private fun connect(token: String) {
        try {
            val opts = IO.Options().apply {
                auth = mapOf("token" to token)
                reconnection = false // we manage retries so each attempt uses the freshest token
                timeout = 15_000
            }
            val s = IO.socket(BuildConfig.SOCKET_URL, opts)
            s.on(Socket.EVENT_CONNECT) {
                retryCount = 0
                _connected.value = true
            }
            s.on(Socket.EVENT_DISCONNECT) {
                _connected.value = false
                scheduleRetry()
            }
            s.on(Socket.EVENT_CONNECT_ERROR) {
                _connected.value = false
                scheduleRetry()
            }
            eventNames.forEach { name ->
                s.on(name) { args ->
                    _events.tryEmit(RealtimeEvent(name, args.firstOrNull()?.toString()))
                }
            }
            socket = s
            s.connect()
        } catch (e: Exception) {
            socket = null
            scheduleRetry()
        }
    }

    @Synchronized
    private fun scheduleRetry() {
        if (!wanted) return
        handler.removeCallbacksAndMessages(null)
        retryCount++
        val delayMs = (3_000L * retryCount).coerceAtMost(60_000L)
        handler.postDelayed({
            synchronized(this) {
                if (!wanted) return@postDelayed
                teardown()
                val token = session.accessToken ?: return@postDelayed
                connect(token)
            }
        }, delayMs)
    }
}
