package com.livora.corbett.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ───────────────────────── Auth ─────────────────────────

@Serializable
data class User(
    val id: String? = null,
    @SerialName("_id") val mongoId: String? = null,
    val firstName: String = "",
    val lastName: String = "",
    val email: String = "",
    val phone: String = "",
    val role: String = "guest",
    val department: String? = null,
) {
    val uid: String get() = id ?: mongoId ?: ""
    val fullName: String get() = "$firstName $lastName".trim()
    val isStaff: Boolean get() = role == "staff" || role == "manager" || role == "admin"
    val isManager: Boolean get() = role == "manager" || role == "admin"
}

@Serializable
data class AuthResponse(
    val accessToken: String = "",
    val refreshToken: String = "",
    val user: User = User(),
)

@Serializable data class LoginBody(val email: String, val password: String)
@Serializable
data class RegisterBody(
    val firstName: String,
    val lastName: String? = null,
    val email: String,
    val phone: String,
    val password: String,
)
@Serializable data class RefreshBody(val refreshToken: String)
@Serializable data class LogoutBody(val refreshToken: String? = null)
@Serializable data class ProfileBody(val firstName: String? = null, val lastName: String? = null, val phone: String? = null)
@Serializable data class ChangePasswordBody(val currentPassword: String, val newPassword: String)
@Serializable data class ForgotBody(val email: String, val area: String = "guest")
@Serializable data class MessageResponse(val message: String? = null)

// ───────────────────────── Settings ─────────────────────────

@Serializable
data class MealPlan(
    val code: String = "",
    val label: String = "",
    val adultPrice: Double = 0.0,
    val childPrice: Double = 0.0,
    val description: String? = null,
)

@Serializable
data class Social(val instagram: String? = null, val facebook: String? = null, val youtube: String? = null)

@Serializable
data class PublicSettings(
    val resortName: String = "Corbett The Vedant By Livora",
    val tagline: String? = null,
    val phone: String = "+91 95288 27446",
    val phoneSecondary: String = "+91 97582 00231",
    val whatsapp: String = "919758200231",
    val email: String = "Livorahospitality06@gmail.com",
    val address: String? = null,
    val mapsUrl: String = "https://maps.app.goo.gl/cEp1Z3fmkQMXzEau8",
    val mapsEmbedUrl: String? = null,
    val checkInTime: String = "14:00",
    val checkOutTime: String = "11:00",
    val cancellationHours: Int = 48,
    val mealPlans: List<MealPlan> = emptyList(),
    val social: Social? = null,
    val bookingsOpen: Boolean = true,
    val announcement: String? = null,
    val assetBaseUrl: String? = null,
)

// ───────────────────────── Rooms ─────────────────────────

@Serializable
data class Room(
    @SerialName("_id") val id: String = "",
    @Serializable(with = FlexStringSerializer::class) val roomNumber: String = "",
    val name: String = "",
    val slug: String = "",
    val type: String = "",
    @Serializable(with = FlexStringSerializer::class) val floor: String = "",
    val status: String = "available",
    val pricePerNight: Double = 0.0,
    val maxAdults: Int = 2,
    val maxChildren: Int = 0,
    val bedType: String? = null,
    val view: String? = null,
    val size: Double? = null,
    val amenities: List<String> = emptyList(),
    val highlights: List<String> = emptyList(),
    val imageUrls: List<String> = emptyList(),
    val description: String? = null,
    val isActive: Boolean = true,
    val sortOrder: Int = 0,
    val available: Boolean? = null,
)

@Serializable
data class BoardBooking(
    @SerialName("_id") val mongoId: String? = null,
    val id: String? = null,
    val bookingId: String? = null,
    val bookingRef: String = "",
    val guestName: String = "",
    val checkIn: String? = null,
    val checkOut: String? = null,
) {
    val bid: String? get() = mongoId ?: id ?: bookingId
}

@Serializable
data class BoardRoom(
    @SerialName("_id") val id: String = "",
    @Serializable(with = FlexStringSerializer::class) val roomNumber: String = "",
    val name: String = "",
    val type: String = "",
    val status: String = "available",
    val current: BoardBooking? = null,
    val arrivingToday: BoardBooking? = null,
    val departingToday: BoardBooking? = null,
)

@Serializable data class StatusBody(
    val status: String,
    val staffNotes: String? = null,
    val estimatedMinutes: Int? = null,
    val totalAmount: Double? = null,
)

// ───────────────────────── Bookings ─────────────────────────

@Serializable
data class Extra(
    @SerialName("_id") val id: String = "",
    val description: String = "",
    val amount: Double = 0.0,
    val addedAt: String? = null,
    val source: String? = null,
)

@Serializable
data class Payment(
    @SerialName("_id") val id: String = "",
    val amount: Double = 0.0,
    val method: String = "",
    val reference: String? = null,
    val note: String? = null,
    val at: String? = null,
)

@Serializable
data class Booking(
    @SerialName("_id") val id: String = "",
    val bookingRef: String = "",
    val roomId: Ref? = null,
    @Serializable(with = FlexStringSerializer::class) val roomNumber: String = "",
    val roomName: String = "",
    val roomType: String = "",
    val guestId: Ref? = null,
    val guestName: String = "",
    val guestEmail: String = "",
    val guestPhone: String = "",
    val checkIn: String = "",
    val checkOut: String = "",
    val nights: Int = 0,
    val adults: Int = 1,
    val children: Int = 0,
    val mealPlan: String = "ep",
    val specialRequests: String? = null,
    val staffNotes: String? = null,
    val status: String = "confirmed",
    val paymentStatus: String = "pending",
    val pricePerNight: Double = 0.0,
    val roomAmount: Double = 0.0,
    val mealAmount: Double = 0.0,
    val discountAmount: Double = 0.0,
    val promoCode: String? = null,
    val extras: List<Extra> = emptyList(),
    val extrasTotal: Double = 0.0,
    val gstRate: Double = 0.12,
    val baseAmount: Double = 0.0,
    val gstAmount: Double = 0.0,
    val totalAmount: Double = 0.0,
    val payments: List<Payment> = emptyList(),
    val paidAmount: Double = 0.0,
    val balanceDue: Double = 0.0,
    val source: String = "website",
    val isBlock: Boolean = false,
    val actualCheckIn: String? = null,
    val actualCheckOut: String? = null,
    val cancelledAt: String? = null,
    val cancelReason: String? = null,
    val createdAt: String? = null,
) {
    /** Best image for this booking's room (populated in /bookings/mine and /bookings/:id). */
    val roomImage: String? get() = roomId?.imageUrls?.firstOrNull()
    val roomSlug: String get() = roomId?.slug ?: ""
    val roomKey: String get() = roomId?.id ?: ""
}

@Serializable
data class Quote(
    val nights: Int = 0,
    val pricePerNight: Double = 0.0,
    val roomAmount: Double = 0.0,
    val mealAmount: Double = 0.0,
    val discountAmount: Double = 0.0,
    val promoCode: String? = null,
    val promoMessage: String? = null,
    val total: Double = 0.0,
    val baseAmount: Double = 0.0,
    val gstRate: Double = 0.12,
    val gstAmount: Double = 0.0,
)

@Serializable data class GuestInfo(val name: String, val email: String, val phone: String)

@Serializable
data class CreateBookingBody(
    val roomId: String,
    val checkIn: String,
    val checkOut: String,
    val adults: Int,
    val children: Int,
    val mealPlan: String,
    val promoCode: String? = null,
    val specialRequests: String? = null,
    val guest: GuestInfo? = null,
    val source: String = "app",
)

@Serializable data class LookupResponse(val booking: Booking = Booking(), val cancellationHours: Int = 48)
@Serializable data class LookupCancelBody(val ref: String, val email: String, val reason: String? = null)
@Serializable data class ReasonBody(val reason: String? = null)
@Serializable
data class BookingPage(
    val bookings: List<Booking> = emptyList(),
    val total: Int = 0,
    val page: Int = 1,
    val pages: Int = 1,
)
@Serializable
data class BookingPatchBody(
    val staffNotes: String? = null,
    val specialRequests: String? = null,
    val guestPhone: String? = null,
)
@Serializable
data class PaymentBody(val amount: Double, val method: String, val reference: String? = null, val note: String? = null)
@Serializable data class ExtraBody(val description: String, val amount: Double)

// ───────────────────────── Messages ─────────────────────────

@Serializable
data class Msg(
    @SerialName("_id") val id: String = "",
    val sender: String = "guest",
    val body: String = "",
    val staffName: String? = null,
    val at: String? = null,
)

@Serializable
data class MsgThread(
    @SerialName("_id") val id: String = "",
    val ref: String = "",
    val token: String? = null,
    val name: String = "",
    val email: String = "",
    val phone: String? = null,
    val subject: String = "",
    val topic: String = "general",
    val status: String = "new",
    @Serializable(with = FlexBoolSerializer::class) val unreadByStaff: Boolean = false,
    @Serializable(with = FlexBoolSerializer::class) val unreadByGuest: Boolean = false,
    val lastMessageAt: String? = null,
    val messages: List<Msg> = emptyList(),
    val preview: String? = null,
    val count: Int? = null,
)

@Serializable
data class ThreadPage(
    val threads: List<MsgThread> = emptyList(),
    val total: Int = 0,
    val unread: Int = 0,
    val page: Int = 1,
    val pages: Int = 1,
)

@Serializable
data class CreateMessageBody(
    val name: String,
    val email: String,
    val phone: String? = null,
    val subject: String? = null,
    val topic: String? = null,
    val message: String,
    val source: String = "app",
    val website: String = "",
)

@Serializable
data class CreateMessageResponse(val ref: String = "", val token: String = "", val message: String? = null)
@Serializable data class ReplyBody(val message: String)
@Serializable data class UnreadCount(val unread: Int = 0)

// ───────────────────────── Menu / reviews / promos ─────────────────────────

@Serializable
data class MenuItem(
    @SerialName("_id") val id: String = "",
    val name: String = "",
    val description: String? = null,
    val category: String = "",
    val price: Double = 0.0,
    val isVeg: Boolean = true,
    val imageUrl: String? = null,
    val isAvailable: Boolean = true,
    val isSignature: Boolean = false,
    val sortOrder: Int = 0,
)

@Serializable
data class Review(
    @SerialName("_id") val id: String = "",
    val name: String = "",
    val location: String? = null,
    val rating: Int = 5,
    val title: String? = null,
    val comment: String = "",
    val createdAt: String? = null,
)

@Serializable
data class ReviewBody(
    val ref: String,
    val email: String,
    val rating: Int,
    val title: String? = null,
    val comment: String,
    val location: String? = null,
)

@Serializable
data class Promotion(
    @SerialName("_id") val id: String = "",
    val code: String = "",
    val name: String = "",
    val description: String? = null,
    val discountType: String = "percent",
    val discountValue: Double = 0.0,
    val minNights: Int? = null,
    val minAmount: Double? = null,
    val maxDiscountAmount: Double? = null,
    val validTo: String? = null,
)

@Serializable
data class PromoValidateBody(val code: String, val nights: Int, val amount: Double, val roomType: String? = null)

@Serializable
data class PromoValidation(
    val valid: Boolean = false,
    val message: String? = null,
    val code: String? = null,
    val name: String? = null,
    val discountAmount: Double? = null,
)

// ───────────────────────── Room service ─────────────────────────

@Serializable
data class RsItem(
    val menuItemId: Ref? = null,
    val name: String = "",
    val qty: Int = 1,
    val price: Double = 0.0,
)

@Serializable
data class RoomServiceReq(
    @SerialName("_id") val id: String = "",
    val bookingId: Ref? = null,
    @Serializable(with = FlexStringSerializer::class) val roomNumber: String = "",
    val guestName: String = "",
    val category: String = "other",
    val title: String = "",
    val items: List<RsItem> = emptyList(),
    val guestNotes: String? = null,
    val staffNotes: String? = null,
    val status: String = "pending",
    @Serializable(with = FlexStringSerializer::class) val priority: String = "",
    val totalAmount: Double = 0.0,
    val estimatedMinutes: Int? = null,
    val createdAt: String? = null,
)

@Serializable
data class RoomServicePage(
    val requests: List<RoomServiceReq> = emptyList(),
    val total: Int = 0,
    val page: Int = 1,
    val pages: Int = 1,
)

@Serializable data class RsItemBody(val menuItemId: String, val qty: Int)

@Serializable
data class CreateRoomServiceBody(
    val bookingId: String,
    val category: String,
    val title: String,
    val guestNotes: String? = null,
    val priority: String? = null,
    val items: List<RsItemBody>? = null,
)

// ───────────────────────── Housekeeping ─────────────────────────

@Serializable
data class ChecklistItem(val item: String = "", val checked: Boolean = false)

@Serializable
data class HkTask(
    @SerialName("_id") val id: String = "",
    val roomId: Ref? = null,
    @Serializable(with = FlexStringSerializer::class) val roomNumber: String = "",
    val taskType: String = "daily_clean",
    val status: String = "pending",
    @Serializable(with = FlexStringSerializer::class) val priority: String = "",
    val assignedTo: Ref? = null,
    val notes: String? = null,
    val scheduledFor: String? = null,
    val checklistItems: List<ChecklistItem> = emptyList(),
) {
    val room: String get() = roomNumber.ifBlank { roomId?.roomNumber ?: "" }.ifBlank { roomId?.name ?: "" }
}

@Serializable
data class CreateTaskBody(
    val roomId: String,
    val taskType: String,
    val priority: String? = null,
    val notes: String? = null,
)

@Serializable
data class TaskPatchBody(
    val status: String? = null,
    val notes: String? = null,
    val priority: String? = null,
    val checklistItems: List<ChecklistItem>? = null,
)

// ───────────────────────── Analytics ─────────────────────────

@Serializable
data class RevenueBlock(
    val roomRevenue: Double = 0.0,
    val fbRevenue: Double = 0.0,
    val totalRevenue: Double = 0.0,
    val byMethod: Map<String, Double> = emptyMap(),
    val expenseTotal: Double = 0.0,
    val netProfit: Double = 0.0,
)

@Serializable
data class Occupancy(
    val total: Int = 0,
    val occupiedTonight: Int = 0,
    val rate: Double = 0.0,
    val inHouse: Int = 0,
)

@Serializable
data class DashStay(
    @SerialName("_id") val mongoId: String? = null,
    val id: String? = null,
    val bookingId: String? = null,
    val bookingRef: String = "",
    val guestName: String = "",
    @Serializable(with = FlexStringSerializer::class) val roomNumber: String = "",
    val roomName: String = "",
    val adults: Int = 0,
    val children: Int = 0,
    val balanceDue: Double = 0.0,
    val guestPhone: String = "",
    val status: String? = null,
) {
    val bid: String? get() = mongoId ?: id ?: bookingId
}

@Serializable
data class DashUpcoming(
    val bookingRef: String = "",
    val guestName: String = "",
    val roomName: String = "",
    val checkIn: String? = null,
    val checkOut: String? = null,
    val totalAmount: Double = 0.0,
)

@Serializable data class PendingPayments(val total: Double = 0.0, val count: Int = 0)

@Serializable
data class Counters(
    val unreadMessages: Int = 0,
    val pendingService: Int = 0,
    val pendingHousekeeping: Int = 0,
    val newBookings24h: Int = 0,
)

@Serializable
data class Dashboard(
    val date: String? = null,
    val today: RevenueBlock = RevenueBlock(),
    val monthToDate: RevenueBlock = RevenueBlock(),
    val occupancy: Occupancy = Occupancy(),
    val arrivals: List<DashStay> = emptyList(),
    val departures: List<DashStay> = emptyList(),
    val upcoming: List<DashUpcoming> = emptyList(),
    val pendingPayments: PendingPayments = PendingPayments(),
    val counters: Counters = Counters(),
    val roomsByStatus: Map<String, Int> = emptyMap(),
)
