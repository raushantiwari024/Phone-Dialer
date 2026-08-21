package com.raushan.phone

import android.app.Application
import com.raushan.phone.telecom.CallNotificationChannels

class DialerApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // Once per process, rather than on every notification post as it used to be.
        CallNotificationChannels.ensureCreated(this)
    }
}
