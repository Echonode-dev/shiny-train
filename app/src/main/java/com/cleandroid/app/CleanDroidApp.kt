package com.cleandroid.app

import android.app.Application
import com.cleandroid.app.di.AppContainer

class CleanDroidApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
