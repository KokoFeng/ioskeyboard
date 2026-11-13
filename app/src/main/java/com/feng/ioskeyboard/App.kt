package com.feng.ioskeyboard

import android.app.Application

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashReporter.install(this)
        Lang.load(this)
    }
}
