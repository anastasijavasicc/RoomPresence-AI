package com.example.roompresencesir1.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll

import androidx.compose.material3.Text

import androidx.compose.runtime.Composable

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import coil.compose.AsyncImage

import com.example.roompresencesir1.model.RoomPresenceUiState
import com.example.roompresencesir1.ui.components.PanelCard
import com.example.roompresencesir1.ui.components.RecordingPlayer
import com.example.roompresencesir1.ui.theme.AppMuted


@Composable
fun ActivityScreen(
    state:
    RoomPresenceUiState
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


        Text(
            text =
            "Activity",

            fontSize =
            28.sp
        )


        Text(
            text =
            "Recent Edge AI events",

            color =
            AppMuted
        )


        if (
            state.lastDetectionImage
            == null
            &&
            state.lastVideo
            == null
        ) {

            PanelCard {

                Text(
                    text =
                    "No activity recorded yet.",

                    color =
                    AppMuted
                )
            }

            return@Column
        }


        PanelCard {

            Text(
                text =
                "Latest event",

                fontSize =
                20.sp
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
                    state.audioEvent
                    != "-"
                )
                    "Triggered by ${
                        state.audioEvent
                    }"
                else
                    "Person detection",

                color =
                AppMuted
            )


            if (
                state.confidence
                > 0
            ) {

                Text(
                    text =
                    String.format(
                        "Detection confidence %.1f%%",
                        state.confidence
                                * 100
                    ),

                    color =
                    AppMuted
                )
            }
        }


        if (
            state.lastDetectionImage
            != null
        ) {

            PanelCard {

                Text(
                    text =
                    "Detection image",

                    fontSize =
                    18.sp
                )


                Spacer(
                    modifier =
                    Modifier.height(
                        12.dp
                    )
                )


                AsyncImage(
                    model =
                    state.lastDetectionImage,

                    contentDescription =
                    "Person detection",

                    modifier =
                    Modifier.fillMaxWidth(),

                    contentScale =
                    ContentScale.FillWidth
                )
            }
        }


        if (
            state.lastVideo
            != null
        ) {

            PanelCard {

                Text(
                    text =
                    "Event recording",

                    fontSize =
                    18.sp
                )


                Spacer(
                    modifier =
                    Modifier.height(
                        12.dp
                    )
                )


                RecordingPlayer(
                    url =
                    state.lastVideo
                )
            }
        }


        Spacer(
            modifier =
            Modifier.height(
                80.dp
            )
        )
    }
}