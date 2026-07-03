package com.kryon.filemanager.adbshell

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import com.kryon.filemanager.MainActivity
import com.kryon.filemanager.core.SecurePreferences

class AdbNotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        when (action) {
            ACTION_PAIR_SUBMIT -> {
                val results = RemoteInput.getResultsFromIntent(intent)
                val pairingCode = results?.getCharSequence(KEY_TEXT_REPLY)?.toString()?.trim()
                
                if (!pairingCode.isNullOrEmpty()) {
                    val ip = SecurePreferences.getLastAdbIp(context)
                    val pairPort = SecurePreferences.getLastAdbPairPort(context)
                    
                    // Save the code
                    SecurePreferences.saveLastAdbPairCode(context, pairingCode)
                    
                    // Update notification to "Pairing..."
                    showNotification(context, "Pairing with code $pairingCode...", "Please wait...")
                    
                    // Start pairing process
                    AdbManager.startPairing(ip, pairPort, pairingCode) { success ->
                        if (success) {
                            showNotification(context, "Paired Successfully ✓", "Tap Connect to establish shell session.")
                        } else {
                            showNotification(context, "Pairing Failed ❌", "Make sure the pairing code is correct.")
                        }
                    }
                }
            }
            ACTION_CONNECT -> {
                val ip = SecurePreferences.getLastAdbIp(context)
                val servicePort = SecurePreferences.getLastAdbServicePort(context)
                
                showNotification(context, "Connecting to ADB...", "Attempting shell link on port $servicePort")
                
                AdbManager.startConnection(ip, servicePort) { success ->
                    if (success) {
                        showNotification(context, "Connected ✓", "ADB Shell connection active.")
                    } else {
                        showNotification(context, "Connection Failed ❌", "Tap to retry or re-pair.")
                    }
                }
            }
        }
    }

    companion object {
        const val ACTION_PAIR_SUBMIT = "com.kryon.filemanager.adbshell.ACTION_PAIR_SUBMIT"
        const val ACTION_CONNECT = "com.kryon.filemanager.adbshell.ACTION_CONNECT"
        const val KEY_TEXT_REPLY = "key_text_reply"
        const val NOTIFICATION_ID = 9001
        const val CHANNEL_ID = "adb_pairing_channel"

        fun showNotification(context: Context, title: String, content: String) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "ADB Shell Pairing",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Interactive notifications for wireless debugging pairing"
                }
                notificationManager.createNotificationChannel(channel)
            }

            // Click action to open MainActivity
            val mainIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            val mainPendingIntent = PendingIntent.getActivity(
                context,
                100,
                mainIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            // Inline reply action
            val remoteInput = RemoteInput.Builder(KEY_TEXT_REPLY)
                .setLabel("Enter 6-digit pairing code")
                .build()

            val replyPendingIntent = PendingIntent.getBroadcast(
                context,
                200,
                Intent(context, AdbNotificationReceiver::class.java).apply {
                    action = ACTION_PAIR_SUBMIT
                },
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val replyAction = NotificationCompat.Action.Builder(
                android.R.drawable.ic_menu_edit,
                "Enter Pairing Code",
                replyPendingIntent
            ).addRemoteInput(remoteInput).build()

            // Connect action
            val connectPendingIntent = PendingIntent.getBroadcast(
                context,
                300,
                Intent(context, AdbNotificationReceiver::class.java).apply {
                    action = ACTION_CONNECT
                },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val connectAction = NotificationCompat.Action.Builder(
                android.R.drawable.ic_media_play,
                "Connect",
                connectPendingIntent
            ).build()

            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_warning)
                .setContentTitle(title)
                .setContentText(content)
                .setStyle(NotificationCompat.BigTextStyle().bigText(content))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(mainPendingIntent)
                .setAutoCancel(false)
                .setOngoing(true)
                .addAction(replyAction)
                .addAction(connectAction)

            notificationManager.notify(NOTIFICATION_ID, builder.build())
        }
        
        fun cancelNotification(context: Context) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(NOTIFICATION_ID)
        }
    }
}
