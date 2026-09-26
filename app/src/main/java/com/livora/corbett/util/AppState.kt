package com.livora.corbett.util

/** Process-wide foreground flag (set by MainActivity onStart/onStop). */
object AppState {
    @Volatile
    var isForeground: Boolean = false
}
