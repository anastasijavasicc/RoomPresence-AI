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

import androidx.compose.foundation.shape.RoundedCornerShape

import androidx.compose.material3.Text

import androidx.compose.runtime.Composable

import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import com.example.roompresencesir1.AppConfig
import com.example.roompresencesir1.model.RoomPresenceUiState
import com.example.roompresencesir1.ui.components.EdgeVideoPlayer
import com.example.roompresencesir1.ui.components.Metric
import com.example.roompresencesir1.ui.components.PanelCard
import com.example.roompresencesir1.ui.components.StatusPill
import com.example.roompresencesir1.ui.theme.AppDanger
import com.example.roompresencesir1.ui.theme.AppMuted
import com.example.roompresencesir1.ui.theme.AppSurfaceLight


@Composable
fun LiveScreen(
    state:
    RoomPresenceUiState
) {

    Column(
        modifier =
        Modifier
            .fillMaxSize()
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
                    "Live monitoring",

                    fontSize =
                    28.sp
                )

                Text(
                    text =
                    "Edge AI • Raspberry Pi",

                    color =
                    AppMuted
                )
            }


            if (
                state.liveSessionActive
            ) {

                StatusPill(
                    text =
                    "● LIVE",

                    color =
                    AppDanger
                )
            }
        }


        if (
            state.liveSessionActive
        ) {

            Box(
                modifier =
                Modifier
                    .fillMaxWidth()
                    .background(
                        AppSurfaceLight,
                        RoundedCornerShape(
                            24.dp
                        )
                    )
                    .padding(
                        5.dp
                    )
            ) {

                EdgeVideoPlayer(
                    url =
                    AppConfig.HLS_URL
                )
            }


            PanelCard {

                Row(
                    modifier =
                    Modifier.fillMaxWidth(),

                    horizontalArrangement =
                    Arrangement.SpaceBetween
                ) {

                    Metric(
                        label =
                        "Persons",

                        value =
                        state.personsDetected
                            .toString()
                    )


                    Metric(
                        label =
                        "Recording",

                        value =
                        if (
                            state.recording
                        )
                            "Active"
                        else
                            "Off"
                    )


                    Metric(
                        label =
                        "AI",

                        value =
                        "LiteRT"
                    )
                }
            }

        }

        else {

            Box(
                modifier =
                Modifier
                    .fillMaxWidth()
                    .height(
                        360.dp
                    )
                    .background(
                        AppSurfaceLight,
                        RoundedCornerShape(
                            26.dp
                        )
                    ),

                contentAlignment =
                Alignment.Center
            ) {

                Column(
                    horizontalAlignment =
                    Alignment.CenterHorizontally
                ) {

                    Text(
                        text =
                        "◉",

                        fontSize =
                        50.sp,

                        color =
                        AppMuted
                    )


                    Spacer(
                        modifier =
                        Modifier.height(
                            15.dp
                        )
                    )


                    Text(
                        text =
                        "No active event",

                        fontSize =
                        22.sp
                    )


                    Spacer(
                        modifier =
                        Modifier.height(
                            8.dp
                        )
                    )


                    Text(
                        text =
                        "Live video starts automatically\nwhen Edge AI confirms a person.",

                        color =
                        AppMuted
                    )
                }
            }
        }
    }
}