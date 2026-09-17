package com.example.roompresencesir1.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

import androidx.compose.material3.Button
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

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


@Composable
fun HistoryScreen(
    state:
    RoomPresenceUiState,

    onRefresh:
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
            Arrangement.SpaceBetween
        ) {

            Column {

                Text(
                    text =
                    "History",

                    fontSize =
                    28.sp
                )

                Text(
                    text =
                    "Saved camera events",

                    color =
                    AppMuted
                )
            }


            Button(
                onClick =
                onRefresh
            ) {

                Text(
                    text =
                    "Refresh"
                )
            }
        }


        // ====================================================
        // DETECTION IMAGES
        // ====================================================

        Text(
            text =
            "AI detections",

            fontSize =
            20.sp
        )


        if (
            state.detectionArchive.isEmpty()
        ) {

            PanelCard {

                Text(
                    text =
                    "No saved detection images.",

                    color =
                    AppMuted
                )
            }

        } else {

            state.detectionArchive
                .forEach {
                        item ->


                    PanelCard {

                        Text(
                            text =
                            formatArchiveTime(
                                item.timestamp
                            ),

                            color =
                            AppMuted,

                            fontSize =
                            12.sp
                        )


                        Spacer(
                            modifier =
                            Modifier.height(
                                10.dp
                            )
                        )


                        AsyncImage(
                            model =
                            item.url,

                            contentDescription =
                            "Detection image",

                            modifier =
                            Modifier.fillMaxWidth(),

                            contentScale =
                            ContentScale.FillWidth
                        )
                    }
                }
        }


        // ====================================================
        // RECORDED VIDEOS
        // ====================================================

        Text(
            text =
            "Recorded clips",

            fontSize =
            20.sp
        )


        if (
            state.videoArchive.isEmpty()
        ) {

            PanelCard {

                Text(
                    text =
                    "No recorded clips.",

                    color =
                    AppMuted
                )
            }

        } else {

            state.videoArchive
                .forEach {
                        item ->


                    PanelCard {

                        Text(
                            text =
                            formatArchiveTime(
                                item.timestamp
                            ),

                            color =
                            AppMuted,

                            fontSize =
                            12.sp
                        )


                        Spacer(
                            modifier =
                            Modifier.height(
                                10.dp
                            )
                        )


                        RecordingPlayer(
                            url =
                            item.url
                        )
                    }
                }
        }


        // ====================================================
        // RAW ARDUINO IMAGES
        // ====================================================

        Text(
            text =
            "Arduino camera frames",

            fontSize =
            20.sp
        )


        if (
            state.arduinoArchive.isEmpty()
        ) {

            PanelCard {

                Text(
                    text =
                    "No Arduino frames.",

                    color =
                    AppMuted
                )
            }

        } else {

            state.arduinoArchive
                .forEach {
                        item ->


                    PanelCard {

                        Text(
                            text =
                            formatArchiveTime(
                                item.timestamp
                            ),

                            color =
                            AppMuted,

                            fontSize =
                            12.sp
                        )


                        Spacer(
                            modifier =
                            Modifier.height(
                                10.dp
                            )
                        )


                        AsyncImage(
                            model =
                            item.url,

                            contentDescription =
                            "Arduino frame",

                            modifier =
                            Modifier.fillMaxWidth(),

                            contentScale =
                            ContentScale.FillWidth
                        )
                    }
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


private fun formatArchiveTime(
    timestamp: Long
): String {

    if (
        timestamp <= 0
    ) {

        return ""
    }


    val formatter =
        SimpleDateFormat(
            "dd.MM.yyyy  HH:mm:ss",
            Locale.getDefault()
        )


    return formatter.format(
        Date(
            timestamp
        )
    )
}