package com.example.roompresencesir1.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

import com.example.roompresencesir1.AppConfig


object NotificationHelper {


    fun createChannel(
        context: Context
    ) {

        if (
            Build.VERSION.SDK_INT
            >= Build.VERSION_CODES.O
        ) {

            val channel =
                NotificationChannel(

                    AppConfig.NOTIFICATION_CHANNEL,

                    "Security alerts",

                    NotificationManager
                        .IMPORTANCE_HIGH
                )


            channel.description =
                "Alerts when a person is detected"


            val manager =
                context.getSystemService(
                    NotificationManager::class.java
                )


            manager.createNotificationChannel(
                channel
            )
        }
    }


    fun showPersonDetected(
        context: Context,
        confidence: Double
    ) {

        if (
            Build.VERSION.SDK_INT
            >= Build.VERSION_CODES.TIRAMISU
            &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            )
            !=
            PackageManager.PERMISSION_GRANTED
        ) {

            return
        }


        val percent =
            confidence * 100


        val notification =
            NotificationCompat.Builder(

                context,

                AppConfig.NOTIFICATION_CHANNEL
            )
                .setSmallIcon(
                    android.R.drawable.ic_dialog_alert
                )
                .setContentTitle(
                    "Person detected"
                )
                .setContentText(
                    "Activity detected • "
                            + String.format(
                        "%.0f%% confidence",
                        percent
                    )
                )
                .setPriority(
                    NotificationCompat.PRIORITY_HIGH
                )
                .setAutoCancel(
                    true
                )
                .build()


        val manager =
            context.getSystemService(
                Context.NOTIFICATION_SERVICE
            ) as NotificationManager


        manager.notify(
            1001,
            notification
        )
    }
}