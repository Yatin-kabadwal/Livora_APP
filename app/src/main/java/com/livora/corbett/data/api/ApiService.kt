package com.livora.corbett.data.api

import kotlinx.serialization.json.JsonElement
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

/** Plain (authenticator-free) endpoint used by the token refresher. */
interface AuthApi {
    @POST("auth/refresh")
    fun refresh(@Body body: RefreshBody): Call<AuthResponse>
}

interface ApiService {
    // ── auth ──
    @POST("auth/login") suspend fun login(@Body body: LoginBody): AuthResponse
    @POST("auth/register") suspend fun register(@Body body: RegisterBody): AuthResponse
    @POST("auth/logout") suspend fun logout(@Body body: LogoutBody): JsonElement
    @GET("auth/me") suspend fun me(): User
    @PUT("auth/profile") suspend fun updateProfile(@Body body: ProfileBody): User
    @PUT("auth/change-password") suspend fun changePassword(@Body body: ChangePasswordBody): MessageResponse
    @POST("auth/forgot-password") suspend fun forgotPassword(@Body body: ForgotBody): MessageResponse

    // ── settings / content ──
    @GET("settings/public") suspend fun publicSettings(): PublicSettings
    @GET("menu") suspend fun menu(): JsonElement
    @GET("promotions") suspend fun promotions(): JsonElement
    @POST("promotions/validate") suspend fun validatePromo(@Body body: PromoValidateBody): PromoValidation
    @GET("reviews") suspend fun reviews(): JsonElement
    @POST("reviews") suspend fun postReview(@Body body: ReviewBody): JsonElement

    // ── rooms ──
    @GET("rooms")
    suspend fun rooms(
        @Query("type") type: String?,
        @Query("adults") adults: Int?,
        @Query("children") children: Int?,
        @Query("checkIn") checkIn: String?,
        @Query("checkOut") checkOut: String?,
    ): JsonElement

    @GET("rooms/{id}") suspend fun room(@Path("id") id: String): Room
    @GET("rooms/board") suspend fun roomBoard(): JsonElement
    @PATCH("rooms/{id}/status") suspend fun setRoomStatus(@Path("id") id: String, @Body body: StatusBody): JsonElement

    // ── bookings ──
    @GET("bookings/quote")
    suspend fun quote(
        @Query("roomId") roomId: String,
        @Query("checkIn") checkIn: String,
        @Query("checkOut") checkOut: String,
        @Query("adults") adults: Int,
        @Query("children") children: Int,
        @Query("mealPlan") mealPlan: String,
        @Query("promoCode") promoCode: String?,
    ): Quote

    @POST("bookings") suspend fun createBooking(@Body body: CreateBookingBody): Booking
    @GET("bookings/lookup") suspend fun lookup(@Query("ref") ref: String, @Query("email") email: String): LookupResponse
    @POST("bookings/lookup/cancel") suspend fun lookupCancel(@Body body: LookupCancelBody): JsonElement
    @GET("bookings/mine") suspend fun myBookings(): JsonElement

    @GET("bookings")
    suspend fun bookings(
        @Query("status") status: String?,
        @Query("paymentStatus") paymentStatus: String?,
        @Query("search") search: String?,
        @Query("view") view: String?,
        @Query("page") page: Int?,
        @Query("limit") limit: Int?,
    ): BookingPage

    @GET("bookings/{id}") suspend fun booking(@Path("id") id: String): Booking
    @Streaming @GET("bookings/{id}/invoice") suspend fun invoice(@Path("id") id: String): ResponseBody
    @POST("bookings/{id}/cancel") suspend fun cancelBooking(@Path("id") id: String, @Body body: ReasonBody): JsonElement
    @PATCH("bookings/{id}/status") suspend fun bookingStatus(@Path("id") id: String, @Body body: StatusBody): JsonElement
    @PATCH("bookings/{id}") suspend fun patchBooking(@Path("id") id: String, @Body body: BookingPatchBody): JsonElement
    @POST("bookings/{id}/payments") suspend fun addPayment(@Path("id") id: String, @Body body: PaymentBody): JsonElement
    @POST("bookings/{id}/extras") suspend fun addExtra(@Path("id") id: String, @Body body: ExtraBody): JsonElement

    // ── messages ──
    @POST("messages") suspend fun createMessage(@Body body: CreateMessageBody): CreateMessageResponse
    @GET("messages/thread/{token}") suspend fun thread(@Path("token") token: String): MsgThread
    @POST("messages/thread/{token}/reply") suspend fun replyThread(@Path("token") token: String, @Body body: ReplyBody): JsonElement
    @GET("messages/mine") suspend fun myThreads(): JsonElement

    @GET("messages")
    suspend fun inbox(
        @Query("status") status: String?,
        @Query("search") search: String?,
        @Query("page") page: Int?,
        @Query("limit") limit: Int?,
    ): ThreadPage

    @GET("messages/unread-count") suspend fun unreadCount(): UnreadCount
    @GET("messages/{id}") suspend fun staffThread(@Path("id") id: String): MsgThread
    @POST("messages/{id}/reply") suspend fun staffReply(@Path("id") id: String, @Body body: ReplyBody): JsonElement
    @PATCH("messages/{id}/status") suspend fun staffThreadStatus(@Path("id") id: String, @Body body: StatusBody): JsonElement

    // ── room service ──
    @GET("roomservice") suspend fun roomService(@Query("status") status: String?): RoomServicePage
    @POST("roomservice") suspend fun createRoomService(@Body body: CreateRoomServiceBody): JsonElement
    @PATCH("roomservice/{id}/status") suspend fun roomServiceStatus(@Path("id") id: String, @Body body: StatusBody): JsonElement
    @POST("roomservice/{id}/cancel") suspend fun roomServiceCancel(@Path("id") id: String, @Body body: ReasonBody): JsonElement

    // ── housekeeping ──
    @GET("housekeeping") suspend fun housekeeping(@Query("status") status: String?): JsonElement
    @POST("housekeeping") suspend fun createTask(@Body body: CreateTaskBody): JsonElement
    @PATCH("housekeeping/{id}") suspend fun patchTask(@Path("id") id: String, @Body body: TaskPatchBody): JsonElement

    // ── analytics ──
    @GET("analytics/dashboard") suspend fun dashboard(@Query("date") date: String?): Dashboard
}
