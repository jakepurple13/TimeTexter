package com.programmersbox.timetexter

import android.app.Application
import com.google.android.material.color.DynamicColors

class TimeTexterApp : Application() {

    override fun onCreate() {
        super.onCreate()
        DynamicColors.applyToActivitiesIfAvailable(this)

    }

}