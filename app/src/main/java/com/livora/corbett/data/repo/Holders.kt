package com.livora.corbett.data.repo

import com.livora.corbett.data.api.ApiService
import com.livora.corbett.data.api.Booking
import com.livora.corbett.data.api.PublicSettings
import com.livora.corbett.util.ApiResult
import com.livora.corbett.util.safeApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** Live public settings (with hard-coded fallbacks from the API contract when offline). */
@Singleton
class ResortConfig @Inject constructor(private val api: ApiService) {
    private val _settings = MutableStateFlow(PublicSettings())
    val settings: StateFlow<PublicSettings> = _settings.asStateFlow()
    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    suspend fun refresh(): ApiResult<PublicSettings> {
        val r = safeApi { api.publicSettings() }
        if (r is ApiResult.Success) {
            _settings.value = r.data
            _loaded.value = true
        }
        return r
    }
}

data class Criteria(
    val checkIn: LocalDate? = null,
    val checkOut: LocalDate? = null,
    val adults: Int = 2,
    val children: Int = 0,
) {
    val hasDates: Boolean get() = checkIn != null && checkOut != null && checkOut.isAfter(checkIn)
}

/** Shared "where/when" selection used by Home, Rooms, Room detail and the booking flow. */
@Singleton
class SearchState @Inject constructor() {
    private val _criteria = MutableStateFlow(Criteria())
    val criteria: StateFlow<Criteria> = _criteria.asStateFlow()

    fun update(transform: (Criteria) -> Criteria) {
        _criteria.value = transform(_criteria.value)
    }

    fun setDates(inDate: LocalDate?, outDate: LocalDate?) = update { it.copy(checkIn = inDate, checkOut = outDate) }
    fun setGuests(adults: Int, children: Int) = update { it.copy(adults = adults, children = children) }
}

/** Passes the just-created booking to the confirmation screen (anonymous guests cannot re-fetch it by id). */
@Singleton
class LastBooking @Inject constructor() {
    private val _booking = MutableStateFlow<Booking?>(null)
    val booking: StateFlow<Booking?> = _booking.asStateFlow()
    fun set(b: Booking?) { _booking.value = b }
}
