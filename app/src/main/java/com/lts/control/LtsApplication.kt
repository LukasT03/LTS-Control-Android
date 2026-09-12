package com.lts.control

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.lts.control.core.notifications.LocalNotificationManager
import com.lts.control.core.repository.DeviceRepository

class LtsApplication : Application(), Application.ActivityLifecycleCallbacks {

    private var activeActivityCount = 0

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(this)
        LocalNotificationManager(this).createChannels()
        DeviceRepository.getInstance(this).initialize()
    }

    override fun onActivityStarted(activity: Activity) {
        activeActivityCount++
        LocalNotificationManager.isAppInForeground = activeActivityCount > 0
    }

    override fun onActivityStopped(activity: Activity) {
        activeActivityCount--
        LocalNotificationManager.isAppInForeground = activeActivityCount > 0
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityResumed(activity: Activity) {}
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}
}
