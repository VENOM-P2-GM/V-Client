package dev.vclient

import android.app.Application
import dev.vclient.core.VClientCore

/** Application entry point: boots the core (single composition root). */
class VClientApp : Application() {
    override fun onCreate() {
        super.onCreate()
        VClientCore.create(this)
    }
}
