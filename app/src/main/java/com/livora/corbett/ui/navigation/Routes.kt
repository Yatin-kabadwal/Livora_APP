package com.livora.corbett.ui.navigation

import android.net.Uri

object Routes {
    const val SPLASH = "splash"
    const val ONBOARDING = "onboarding"
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val FORGOT = "forgot"
    const val GUEST = "guest"
    const val STAFF = "staff"

    const val ROOM = "room/{slug}"
    fun room(slug: String) = "room/${Uri.encode(slug)}"

    const val BOOKING = "booking/{slug}"
    fun booking(slug: String) = "booking/${Uri.encode(slug)}"

    const val CONFIRMATION = "confirmation"

    const val STAY = "stay?id={id}&ref={ref}&email={email}"
    /** Only non-blank arguments are put in the URL (empty query values do not match the route pattern reliably). */
    fun stay(id: String = "", ref: String = "", email: String = ""): String {
        val q = listOf("id" to id, "ref" to ref, "email" to email)
            .filter { it.second.isNotBlank() }
            .joinToString("&") { "${it.first}=${Uri.encode(it.second)}" }
        return if (q.isEmpty()) "stay" else "stay?$q"
    }

    const val ROOM_SERVICE = "roomservice/{bookingId}"
    fun roomService(bookingId: String) = "roomservice/${Uri.encode(bookingId)}"

    const val COMPOSE = "compose"
    const val CHAT = "chat/{token}"
    fun chat(token: String) = "chat/${Uri.encode(token)}"

    const val DINING = "dining"
    const val PROFILE = "profile"

    const val S_BOOKING = "sbooking/{id}"
    fun sBooking(id: String) = "sbooking/${Uri.encode(id)}"
    const val S_WALKIN = "walkin"
    const val S_THREAD = "sthread/{id}"
    fun sThread(id: String) = "sthread/${Uri.encode(id)}"
}
