package com.stride.walk

import android.app.Application

class StrideApp : Application() {
    override fun onCreate() {
        super.onCreate()
        WalkRepo.init(this)
        Notif.ensureChannels(this)
    }
}
