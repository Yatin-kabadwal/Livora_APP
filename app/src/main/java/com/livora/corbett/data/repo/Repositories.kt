package com.livora.corbett.data.repo

import com.livora.corbett.data.api.*
import com.livora.corbett.data.local.AppPrefs
import com.livora.corbett.data.local.Session
import com.livora.corbett.data.local.SessionStore
import com.livora.corbett.util.ApiResult
import com.livora.corbett.util.safeApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val api: ApiService,
    private val store: SessionStore,
    private val prefs: AppPrefs,
) {
    val session: StateFlow<Session?> get() = store.session

    suspend fun login(email: String, password: String): ApiResult<User> = safeApi {
        val r = api.login(LoginBody(email.trim(), password))
        store.save(r)
        prefs.setLastEmail(email.trim())
        r.user
    }

    suspend fun register(first: String, last: String, email: String, phone: String, password: String): ApiResult<User> = safeApi {
        val r = api.register(RegisterBody(first.trim(), last.trim().ifBlank { null }, email.trim(), phone.trim(), password))
        store.save(r)
        r.user
    }

    suspend fun forgotPassword(email: String): ApiResult<String> = safeApi {
        api.forgotPassword(ForgotBody(email.trim(), "guest")).message
            ?: "If that email is registered, a reset link is on its way."
    }

    suspend fun refreshMe(): ApiResult<User> = safeApi {
        val u = api.me()
        store.updateUser(u)
        u
    }

    suspend fun updateProfile(first: String, last: String, phone: String): ApiResult<User> = safeApi {
        val u = api.updateProfile(ProfileBody(first.trim(), last.trim(), phone.trim()))
        store.updateUser(u)
        u
    }

    suspend fun changePassword(current: String, next: String): ApiResult<String> = safeApi {
        api.changePassword(ChangePasswordBody(current, next)).message ?: "Password updated."
    }

    suspend fun logout() {
        val rt = store.current?.refreshToken
        safeApi { api.logout(LogoutBody(rt)) }
        store.clear()
    }
}

@Singleton
class ResortRepository @Inject constructor(
    private val api: ApiService,
    private val json: Json,
) {
    suspend fun rooms(type: String?, adults: Int?, children: Int?, checkIn: String?, checkOut: String?): ApiResult<List<Room>> = safeApi {
        json.decodeList<Room>(api.rooms(type, adults, children, checkIn, checkOut))
            .filter { it.isActive }
            .sortedBy { it.sortOrder }
    }

    suspend fun room(idOrSlug: String): ApiResult<Room> = safeApi { api.room(idOrSlug) }

    suspend fun menu(): ApiResult<List<MenuItem>> = safeApi {
        json.decodeList<MenuItem>(api.menu()).filter { it.isAvailable }.sortedBy { it.sortOrder }
    }

    suspend fun promotions(): ApiResult<List<Promotion>> = safeApi { json.decodeList<Promotion>(api.promotions()) }
    suspend fun reviews(): ApiResult<List<Review>> = safeApi { json.decodeList<Review>(api.reviews()) }

    suspend fun quote(roomId: String, checkIn: String, checkOut: String, adults: Int, children: Int, mealPlan: String, promo: String?): ApiResult<Quote> =
        safeApi { api.quote(roomId, checkIn, checkOut, adults, children, mealPlan, promo?.takeIf { it.isNotBlank() }) }

    suspend fun validatePromo(code: String, nights: Int, amount: Double, roomType: String?): ApiResult<PromoValidation> =
        safeApi { api.validatePromo(PromoValidateBody(code.trim(), nights, amount, roomType)) }

    suspend fun postReview(ref: String, email: String, rating: Int, title: String, comment: String): ApiResult<Unit> = safeApi {
        api.postReview(ReviewBody(ref, email, rating, title.ifBlank { null }, comment.trim()))
        Unit
    }
}

@Singleton
class BookingRepository @Inject constructor(
    private val api: ApiService,
    private val json: Json,
) {
    suspend fun create(body: CreateBookingBody): ApiResult<Booking> = safeApi { api.createBooking(body) }
    suspend fun lookup(ref: String, email: String): ApiResult<LookupResponse> = safeApi { api.lookup(ref.trim(), email.trim()) }

    suspend fun lookupCancel(ref: String, email: String, reason: String?): ApiResult<Unit> = safeApi {
        api.lookupCancel(LookupCancelBody(ref.trim(), email.trim(), reason?.ifBlank { null }))
        Unit
    }

    suspend fun mine(): ApiResult<List<Booking>> = safeApi { json.decodeList<Booking>(api.myBookings()) }
    suspend fun get(id: String): ApiResult<Booking> = safeApi { api.booking(id) }

    suspend fun cancel(id: String, reason: String?): ApiResult<Booking> = safeApi {
        api.cancelBooking(id, ReasonBody(reason?.ifBlank { null }))
        api.booking(id)
    }

    /** Streams the invoice PDF (needs the Bearer header, so it goes through the authed client) into [dir]. */
    suspend fun downloadInvoice(id: String, ref: String, dir: File): ApiResult<File> = safeApi {
        withContext(Dispatchers.IO) {
            dir.mkdirs()
            val out = File(dir, "Invoice-" + ref.replace(Regex("[^A-Za-z0-9_-]"), "_") + ".pdf")
            api.invoice(id).use { body ->
                body.byteStream().use { input -> out.outputStream().use { input.copyTo(it) } }
            }
            out
        }
    }

    // ── staff ──
    suspend fun list(status: String?, search: String?, view: String?, page: Int, limit: Int = 25): ApiResult<BookingPage> =
        safeApi { api.bookings(status, null, search?.takeIf { it.isNotBlank() }, view, page, limit) }

    /** Resolves a booking id from a reference when the API row didn't include one. */
    suspend fun idForRef(ref: String): String? {
        val r = safeApi { api.bookings(null, null, ref, null, 1, 5) }
        return (r as? ApiResult.Success)?.data?.bookings?.firstOrNull { it.bookingRef == ref }?.id
            ?: (r as? ApiResult.Success)?.data?.bookings?.firstOrNull()?.id
    }

    suspend fun setStatus(id: String, status: String, notes: String? = null): ApiResult<Booking> = safeApi {
        api.bookingStatus(id, StatusBody(status, staffNotes = notes))
        api.booking(id)
    }

    suspend fun patch(id: String, staffNotes: String?, specialRequests: String?): ApiResult<Booking> = safeApi {
        api.patchBooking(id, BookingPatchBody(staffNotes = staffNotes, specialRequests = specialRequests))
        api.booking(id)
    }

    suspend fun addPayment(id: String, amount: Double, method: String, reference: String?, note: String?): ApiResult<Booking> = safeApi {
        api.addPayment(id, PaymentBody(amount, method, reference?.ifBlank { null }, note?.ifBlank { null }))
        api.booking(id)
    }

    suspend fun addExtra(id: String, description: String, amount: Double): ApiResult<Booking> = safeApi {
        api.addExtra(id, ExtraBody(description.trim(), amount))
        api.booking(id)
    }
}

@Singleton
class MessageRepository @Inject constructor(
    private val api: ApiService,
    private val json: Json,
    private val prefs: AppPrefs,
) {
    suspend fun create(body: CreateMessageBody): ApiResult<CreateMessageResponse> = safeApi {
        val r = api.createMessage(body)
        if (r.token.isNotBlank()) prefs.addThreadToken(r.token)
        r
    }

    suspend fun thread(token: String): ApiResult<MsgThread> = safeApi { api.thread(token).let { if (it.token.isNullOrBlank()) it.copy(token = token) else it } }
    suspend fun reply(token: String, text: String): ApiResult<Unit> = safeApi { api.replyThread(token, ReplyBody(text.trim())); Unit }

    /** Account threads (when signed in) merged with threads started anonymously on this device. */
    suspend fun myThreads(loggedIn: Boolean, localTokens: Set<String>): ApiResult<List<MsgThread>> = safeApi {
        val out = LinkedHashMap<String, MsgThread>()
        if (loggedIn) {
            json.decodeList<MsgThread>(api.myThreads()).forEach { t ->
                val key = t.token ?: t.id
                out[key] = t
            }
        }
        for (tok in localTokens) {
            if (out.containsKey(tok)) continue
            val r = safeApi { api.thread(tok) }
            if (r is ApiResult.Success) out[tok] = r.data.copy(token = tok)
        }
        out.values.sortedByDescending { it.lastMessageAt ?: "" }
    }

    // ── staff ──
    suspend fun inbox(status: String, search: String?, page: Int): ApiResult<ThreadPage> =
        safeApi { api.inbox(status, search?.takeIf { it.isNotBlank() }, page, 30) }

    suspend fun unread(): ApiResult<Int> = safeApi { api.unreadCount().unread }
    suspend fun staffThread(id: String): ApiResult<MsgThread> = safeApi { api.staffThread(id) }
    suspend fun staffReply(id: String, text: String): ApiResult<Unit> = safeApi { api.staffReply(id, ReplyBody(text.trim())); Unit }
    suspend fun setStatus(id: String, status: String): ApiResult<Unit> = safeApi { api.staffThreadStatus(id, StatusBody(status)); Unit }
}

@Singleton
class OpsRepository @Inject constructor(
    private val api: ApiService,
    private val json: Json,
) {
    suspend fun dashboard(): ApiResult<Dashboard> = safeApi { api.dashboard(null) }
    suspend fun board(): ApiResult<List<BoardRoom>> = safeApi { json.decodeList<BoardRoom>(api.roomBoard()) }
    suspend fun setRoomStatus(roomId: String, status: String): ApiResult<Unit> = safeApi { api.setRoomStatus(roomId, StatusBody(status)); Unit }
    suspend fun allRooms(): ApiResult<List<Room>> = safeApi {
        json.decodeList<Room>(api.rooms(null, null, null, null, null)).filter { it.isActive }.sortedBy { it.sortOrder }
    }

    suspend fun tasks(status: String?): ApiResult<List<HkTask>> = safeApi { json.decodeList<HkTask>(api.housekeeping(status)) }
    suspend fun createTask(roomId: String, type: String, priority: String, notes: String): ApiResult<Unit> = safeApi {
        api.createTask(CreateTaskBody(roomId, type, priority.ifBlank { null }, notes.ifBlank { null }))
        Unit
    }
    suspend fun patchTask(id: String, body: TaskPatchBody): ApiResult<Unit> = safeApi { api.patchTask(id, body); Unit }

    suspend fun roomService(status: String?): ApiResult<List<RoomServiceReq>> = safeApi { api.roomService(status).requests }
    suspend fun createRoomService(body: CreateRoomServiceBody): ApiResult<Unit> = safeApi { api.createRoomService(body); Unit }
    suspend fun roomServiceStatus(id: String, status: String, eta: Int?, amount: Double?, notes: String?): ApiResult<Unit> = safeApi {
        api.roomServiceStatus(id, StatusBody(status, staffNotes = notes?.ifBlank { null }, estimatedMinutes = eta, totalAmount = amount)); Unit
    }
    suspend fun cancelRoomService(id: String): ApiResult<Unit> = safeApi { api.roomServiceCancel(id, ReasonBody()); Unit }
}
