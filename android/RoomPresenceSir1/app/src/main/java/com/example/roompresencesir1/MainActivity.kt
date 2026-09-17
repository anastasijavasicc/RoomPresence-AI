package com.example.roompresencesir1

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels

import androidx.core.content.ContextCompat

import com.example.roompresencesir1.notifications.NotificationHelper
import com.example.roompresencesir1.ui.RoomPresenceApp
import com.example.roompresencesir1.ui.theme.RoomPresenceSir1Theme
import com.example.roompresencesir1.viewmodel.RoomPresenceViewModel


class MainActivity :
    ComponentActivity() {


    private val viewModel:
            RoomPresenceViewModel
            by viewModels()


    private val notificationPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts
                .RequestPermission()
        ) {
        }


    override fun onCreate(
        savedInstanceState: Bundle?
    ) {

        super.onCreate(
            savedInstanceState
        )


        NotificationHelper.createChannel(
            this
        )


        requestNotificationPermission()


        viewModel.onPersonDetected =
            { confidence ->

                NotificationHelper
                    .showPersonDetected(
                        this,
                        confidence
                    )
            }


        setContent {

            RoomPresenceSir1Theme {

                val state =
                    viewModel
                        .uiState
                        .value



                RoomPresenceApp(
                    state = state,

                    onMonitoringChanged = {
                        viewModel
                            .setMonitoring(it)
                    },

                    onAlarm = {
                        viewModel.activateAlarm()
                    },

                    onRefreshHistory = {
                        viewModel.refreshArchive()
                    }
                )
            }
        }
    }


    private fun requestNotificationPermission() {

        if (
            Build.VERSION.SDK_INT
            >= Build.VERSION_CODES.TIRAMISU
        ) {

            if (
                ContextCompat
                    .checkSelfPermission(
                        this,
                        Manifest.permission
                            .POST_NOTIFICATIONS
                    )
                !=
                PackageManager
                    .PERMISSION_GRANTED
            ) {

                notificationPermissionLauncher
                    .launch(
                        Manifest.permission
                            .POST_NOTIFICATIONS
                    )
            }
        }
    }


    override fun onDestroy() {

        viewModel.onPersonDetected =
            null

        super.onDestroy()
    }
}