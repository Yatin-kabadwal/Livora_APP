package com.livora.corbett

import android.app.Application
import com.livora.corbett.util.Notifier
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class CorbettApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifier.createChannels(this)
    }
}
