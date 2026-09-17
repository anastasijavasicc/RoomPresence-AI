package com.example.roompresencesir1.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll

import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text

import androidx.compose.runtime.Composable

import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import com.example.roompresencesir1.model.RoomPresenceUiState
import com.example.roompresencesir1.ui.components.Metric
import com.example.roompresencesir1.ui.components.PanelCard
import com.example.roompresencesir1.ui.components.StatusPill
import com.example.roompresencesir1.ui.theme.AppDanger
import com.example.roompresencesir1.ui.theme.AppMuted
import com.example.roompresencesir1.ui.theme.AppSuccess
import com.example.roompresencesir1.ui.theme.AppSurfaceLight


@Composable
fun HomeScreen(
    state: RoomPresenceUiState,
    onMonitoringChanged:
        (Boolean) -> Unit,
    onAlarm:
        () -> Unit
) {

    Column(
        modifier =
        Modifier
            .fillMaxSize()
            .verticalScroll(
                rememberScrollState()
            )
            .padding(
                20.dp
            ),

        verticalArrangement =
        Arrangement.spacedBy(
            18.dp
        )
    ) {


        Row(
            modifier =
            Modifier.fillMaxWidth(),

            horizontalArrangement =
            Arrangement.SpaceBetween,

            verticalAlignment =
            Alignment.CenterVertically
        ) {

            Column {

                Text(
                    text =
                    "Edge AI",

                    fontSize =
                    30.sp
                )

                Text(
                    text =
                    "Room Monitor",

                    color =
                    AppMuted,

                    fontSize =
                    16.sp
                )
            }


            StatusPill(
                text =
                if (
                    state.connected
                )
                    "● ONLINE"
                else
                    "● OFFLINE",

                color =
                if (
                    state.connected
                )
                    AppSuccess
                else
                    AppDanger
            )
        }


        // ====================================================
        // MONITORING
        // ====================================================

        PanelCard {

            Text(
                text =
                "Monitoring",

                fontSize =
                21.sp
            )


            Spacer(
                modifier =
                Modifier.height(
                    5.dp
                )
            )


            Text(
                text =
                if (
                    state.monitoringEnabled
                )
                    "Room protection is active"
                else
                    "Monitoring is paused",

                color =
                AppMuted
            )


            Spacer(
                modifier =
                Modifier.height(
                    18.dp
                )
            )


            Row(
                modifier =
                Modifier.fillMaxWidth(),

                horizontalArrangement =
                Arrangement.spacedBy(
                    10.dp
                )
            ) {


                Button(
                    onClick = {

                        onMonitoringChanged(
                            true
                        )
                    },

                    modifier =
                    Modifier.weight(
                        1f
                    ),

                    colors =
                    ButtonDefaults
                        .buttonColors(
                            containerColor =
                            if (
                                state.monitoringEnabled
                            )
                                AppSuccess
                            else
                                AppSurfaceLight
                        )
                ) {

                    Text(
                        text =
                        "MONITORING ON"
                    )
                }


                Button(
                    onClick = {

                        onMonitoringChanged(
                            false
                        )
                    },

                    modifier =
                    Modifier.weight(
                        1f
                    ),

                    colors =
                    ButtonDefaults
                        .buttonColors(
                            containerColor =
                            if (
                                !state.monitoringEnabled
                            )
                                AppDanger
                            else
                                AppSurfaceLight
                        )
                ) {

                    Text(
                        text =
                        "MONITORING OFF"
                    )
                }
            }
        }


        // ====================================================
        // ROOM STATUS
        // ====================================================

        Box(
            modifier =
            Modifier
                .fillMaxWidth()
                .background(

                    if (
                        state.personDetected
                    )
                        AppDanger.copy(
                            alpha =
                            0.13f
                        )
                    else
                        AppSurfaceLight,

                    RoundedCornerShape(
                        24.dp
                    )
                )
                .padding(
                    24.dp
                )
        ) {

            Column(
                modifier =
                Modifier.fillMaxWidth(),

                horizontalAlignment =
                Alignment.CenterHorizontally
            ) {


                Text(
                    text =
                    if (
                        state.personDetected
                    )
                        "!"
                    else
                        "✓",

                    color =
                    if (
                        state.personDetected
                    )
                        AppDanger
                    else
                        AppSuccess,

                    fontSize =
                    42.sp
                )


                Spacer(
                    modifier =
                    Modifier.height(
                        8.dp
                    )
                )


                Text(
                    text =
                    if (
                        state.personDetected
                    )
                        "Person detected"
                    else
                        "Room is secure",

                    fontSize =
                    24.sp
                )


                Spacer(
                    modifier =
                    Modifier.height(
                        6.dp
                    )
                )


                Text(
                    text =
                    if (
                        state.personDetected
                    )
                        "${state.personsDetected.coerceAtLeast(1)} person currently detected"
                    else
                        "No active security event",

                    color =
                    AppMuted
                )
            }
        }


        // ====================================================
        // LATEST ACTIVITY
        // ====================================================

        PanelCard {

            Text(
                text =
                "Latest activity",

                fontSize =
                19.sp
            )


            Spacer(
                modifier =
                Modifier.height(
                    18.dp
                )
            )


            Row(
                modifier =
                Modifier.fillMaxWidth(),

                horizontalArrangement =
                Arrangement.spacedBy(
                    25.dp
                )
            ) {


                Metric(
                    label =
                    "Audio trigger",

                    value =
                    if (
                        state.audioEvent
                        != "-"
                    )
                        "${state.audioEvent.replaceFirstChar { it.uppercase() }} ${
                            String.format(
                                "%.0f%%",
                                state.audioConfidence
                                        * 100
                            )
                        }"
                    else
                        "None",

                    modifier =
                    Modifier.weight(
                        1f
                    )
                )


                Metric(
                    label =
                    "AI confidence",

                    value =
                    if (
                        state.confidence
                        > 0
                    )
                        String.format(
                            "%.0f%%",
                            state.confidence
                                    * 100
                        )
                    else
                        "—",

                    modifier =
                    Modifier.weight(
                        1f
                    )
                )
            }
        }


        Text(
            text =
            "Emergency",

            color =
            AppMuted,

            fontSize =
            14.sp
        )


        Button(
            onClick =
            onAlarm,

            modifier =
            Modifier
                .fillMaxWidth()
                .height(
                    58.dp
                ),

            shape =
            RoundedCornerShape(
                18.dp
            ),

            colors =
            ButtonDefaults
                .buttonColors(
                    containerColor =
                    AppDanger
                )
        ) {

            Text(
                text =
                "ACTIVATE ALARM",

                color =
                Color.White,

                fontSize =
                16.sp
            )
        }


        Spacer(
            modifier =
            Modifier.height(
                80.dp
            )
        )
    }
}