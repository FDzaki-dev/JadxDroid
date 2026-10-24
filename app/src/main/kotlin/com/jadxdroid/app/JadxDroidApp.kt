package com.jadxdroid.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.jadxdroid.app.log.CrashLogger
import com.jadxdroid.app.work.DecompileWorker

class JadxDroidApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // jadx's FileUtils creates its temp directory in a static initialiser, so the property
        // must point at the app cache before any jadx class is loaded.
        System.setProperty("java.io.tmpdir", cacheDir.absolutePath)
        CrashLogger.install(this)
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            DecompileWorker.CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}
