package com.livora.corbett.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.time.LocalDate
import java.time.ZoneOffset

/** Small wrappers around common external intents. All are failure-safe (no crash if no app handles them). */
object Intents {

    private fun Context.launch(intent: Intent, failure: String): Boolean {
        return try {
            if (this !is android.app.Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, failure, Toast.LENGTH_SHORT).show()
            false
        } catch (e: SecurityException) {
            Toast.makeText(this, failure, Toast.LENGTH_SHORT).show()
            false
        }
    }

    private fun digits(phone: String) = phone.filter { it.isDigit() || it == '+' }

    fun call(ctx: Context, phone: String) {
        ctx.launch(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + digits(phone))), "No phone app found")
    }

    fun whatsapp(ctx: Context, number: String, text: String? = null) {
        val n = number.filter { it.isDigit() }
        val withCountry = if (n.length == 10) "91$n" else n
        var url = "https://wa.me/$withCountry"
        if (!text.isNullOrBlank()) url += "?text=" + Uri.encode(text)
        ctx.launch(Intent(Intent.ACTION_VIEW, Uri.parse(url)), "Couldn't open WhatsApp")
    }

    fun openUrl(ctx: Context, url: String) {
        ctx.launch(Intent(Intent.ACTION_VIEW, Uri.parse(url)), "Couldn't open the link")
    }

    fun email(ctx: Context, to: String, subject: String = "") {
        val i = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$to")).putExtra(Intent.EXTRA_SUBJECT, subject)
        ctx.launch(i, "No email app found")
    }

    fun share(ctx: Context, text: String, title: String = "Share") {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        ctx.launch(Intent.createChooser(send, title), "Nothing to share with")
    }

    fun addToCalendar(ctx: Context, title: String, checkIn: LocalDate, checkOut: LocalDate, location: String, description: String) {
        val begin = checkIn.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val end = checkOut.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val i = Intent(Intent.ACTION_INSERT)
            .setData(CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin)
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, end)
            .putExtra(CalendarContract.Events.ALL_DAY, true)
            .putExtra(CalendarContract.Events.TITLE, title)
            .putExtra(CalendarContract.Events.EVENT_LOCATION, location)
            .putExtra(CalendarContract.Events.DESCRIPTION, description)
        ctx.launch(i, "No calendar app found")
    }

    fun openPdf(ctx: Context, file: File) {
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", file)
        val i = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/pdf")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.launch(i, "No PDF viewer installed. Install one to view the invoice.")
    }

    fun findActivity(ctx: Context): android.app.Activity? {
        var c: Context? = ctx
        while (c is ContextWrapper) {
            if (c is android.app.Activity) return c
            c = c.baseContext
        }
        return null
    }
}
