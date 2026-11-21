package com.jeal.switchdriver

import android.app.Application

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Store.init(this)
        ShizukuBridge.init(this)
        Policy.seedOnce(this)
    }
}
