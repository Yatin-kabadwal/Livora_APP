package com.livora.corbett.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.livora.corbett.data.api.ApiService
import com.livora.corbett.data.local.AppPrefs
import com.livora.corbett.data.local.SessionStore
import com.livora.corbett.util.ApiResult
import com.livora.corbett.util.AppState
import com.livora.corbett.util.Notifier
import com.livora.corbett.util.safeApi
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

@EntryPoint
@InstallIn(SingletonComponent::class)
interface PollEntryPoint {
    fun api(): ApiService
    fun session(): SessionStore
    fun prefs(): AppPrefs
}

/**
 * Best-effort background polling for staff (no FCM): every ~15 min checks unread guest messages and
 * pending room-service requests and posts a local notification when the numbers grow.
 */
class StaffPollWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val ep = EntryPointAccessors.fromApplication(applicationContext, PollEntryPoint::class.java)
        val session = ep.session().current ?: return Result.success()
        if (!session.user.isStaff) return Result.success()
        if (!ep.prefs().notificationsEnabled.first()) return Result.success()

        val api = ep.api()
        val unread = (safeApi { api.unreadCount().unread } as? ApiResult.Success)?.data
        val pending = (safeApi { api.roomService("pending").total } as? ApiResult.Success)?.data

        val sp = applicationContext.getSharedPreferences("poll_state", Context.MODE_PRIVATE)
        val lastUnread = sp.getInt("unread", 0)
        val lastPending = sp.getInt("pending", 0)

        if (!AppState.isForeground) {
            if (unread != null && unread > lastUnread) {
                Notifier.show(applicationContext, 1001, "New guest message", "$unread unread message(s) in the inbox.")
            }
            if (pending != null && pending > lastPending) {
                Notifier.show(applicationContext, 1002, "New room service request", "$pending request(s) waiting to be accepted.")
            }
        }
        sp.edit().apply {
            if (unread != null) putInt("unread", unread)
            if (pending != null) putInt("pending", pending)
        }.apply()
        return Result.success()
    }

    companion object {
        private const val NAME = "staff_poll"

        fun schedule(ctx: Context) {
            val req = PeriodicWorkRequestBuilder<StaffPollWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.UPDATE, req)
        }

        fun cancel(ctx: Context) {
            WorkManager.getInstance(ctx).cancelUniqueWork(NAME)
        }
    }
}
