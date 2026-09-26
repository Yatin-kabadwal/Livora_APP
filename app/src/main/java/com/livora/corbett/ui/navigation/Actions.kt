package com.livora.corbett.ui.navigation

/** Navigation callbacks handed to the guest tab screens (keeps screens free of NavController). */
class GuestActions(
    val openRoom: (slug: String) -> Unit,
    val openStay: (id: String) -> Unit,
    val openLookup: (ref: String, email: String) -> Unit,
    val openChat: (token: String) -> Unit,
    val compose: () -> Unit,
    val dining: () -> Unit,
    val login: () -> Unit,
    val roomService: (bookingId: String) -> Unit,
)

/** Navigation callbacks for the staff shell. */
class StaffActions(
    val openBooking: (id: String) -> Unit,
    val openThread: (id: String) -> Unit,
    val walkIn: () -> Unit,
    val profile: () -> Unit,
)
