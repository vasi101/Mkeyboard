package com.mobilekeyb.bluetooth

import android.app.*
import android.content.Intent
import android.os.IBinder
import com.mobilekeyb.MainActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Owns HID independently of the activity and remains active when the screen is locked. */
class HidConnectionService : Service() {
    companion object {
        private val activeManager = MutableStateFlow<BluetoothHidManager?>(null)
        val manager = activeManager.asStateFlow()
    }
    override fun onCreate() {
        super.onCreate()
        if (android.os.Build.VERSION.SDK_INT >= 31 &&
            checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            stopSelf()
            return
        }
        val notifications = getSystemService(NotificationManager::class.java)
        notifications.createNotificationChannel(NotificationChannel("connection", "Bluetooth connection", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop = PendingIntent.getService(this, 1, Intent(this, HidConnectionService::class.java).setAction("STOP"), PendingIntent.FLAG_IMMUTABLE)
        startForeground(1, Notification.Builder(this, "connection")
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle("Mobile Keyb connection active")
            .setContentText("Keeps Bluetooth HID ready and reconnects to your computer.")
            .setContentIntent(open).setOngoing(true)
            .addAction(Notification.Action.Builder(null, "Stop", stop).build()).build())
        activeManager.value = BluetoothHidManager(this)
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") {
            activeManager.value?.disconnect()
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() {
        activeManager.value?.close()
        activeManager.value = null
        super.onDestroy()
    }
}
