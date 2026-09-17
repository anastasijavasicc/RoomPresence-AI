package com.example.roompresencesir1.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding

import androidx.compose.foundation.shape.RoundedCornerShape

import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember

import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

import com.example.roompresencesir1.ui.theme.AppBorder
import com.example.roompresencesir1.ui.theme.AppMuted
import com.example.roompresencesir1.ui.theme.AppSurface


@Composable
fun PanelCard(
    modifier: Modifier =
        Modifier,

    content:
    @Composable () -> Unit
) {

    Card(
        modifier =
        modifier.fillMaxWidth(),

        shape =
        RoundedCornerShape(
            22.dp
        ),

        colors =
        CardDefaults
            .cardColors(
                containerColor =
                AppSurface
            )
    ) {

        Column(
            modifier =
            Modifier.padding(
                20.dp
            )
        ) {

            content()
        }
    }
}


@Composable
fun StatusPill(
    text: String,
    color: Color
) {

    Box(
        modifier =
        Modifier
            .background(
                color.copy(
                    alpha =
                    0.15f
                ),
                RoundedCornerShape(
                    50.dp
                )
            )
            .border(
                width =
                1.dp,
                color =
                color.copy(
                    alpha =
                    0.4f
                ),
                shape =
                RoundedCornerShape(
                    50.dp
                )
            )
            .padding(
                horizontal =
                12.dp,
                vertical =
                7.dp
            )
    ) {

        Text(
            text =
            text,

            color =
            color,

            fontSize =
            12.sp
        )
    }
}


@Composable
fun Metric(
    label: String,
    value: String,
    modifier: Modifier =
        Modifier
) {

    Column(
        modifier =
        modifier
    ) {

        Text(
            text =
            label,

            color =
            AppMuted,

            fontSize =
            12.sp
        )

        Spacer(
            modifier =
            Modifier.height(
                5.dp
            )
        )

        Text(
            text =
            value,

            style =
            MaterialTheme
                .typography
                .titleMedium
        )
    }
}


@Composable
fun EdgeVideoPlayer(
    url: String,
    modifier: Modifier =
        Modifier
) {

    val context =
        LocalContext.current


    val player =
        remember(
            url
        ) {

            ExoPlayer.Builder(
                context
            )
                .build()
                .apply {

                    setMediaItem(
                        MediaItem.fromUri(
                            url
                        )
                    )

                    prepare()

                    playWhenReady =
                        true
                }
        }


    DisposableEffect(
        player
    ) {

        onDispose {

            player.release()
        }
    }


    AndroidView(
        modifier =
        modifier
            .fillMaxWidth()
            .height(
                260.dp
            ),

        factory = {

            PlayerView(
                it
            ).apply {

                this.player =
                    player

                useController =
                    false

                keepScreenOn =
                    true
            }
        }
    )
}


@Composable
fun RecordingPlayer(
    url: String
) {

    val context =
        LocalContext.current


    val player =
        remember(
            url
        ) {

            ExoPlayer.Builder(
                context
            )
                .build()
                .apply {

                    setMediaItem(
                        MediaItem.fromUri(
                            url
                        )
                    )

                    prepare()
                }
        }


    DisposableEffect(
        player
    ) {

        onDispose {

            player.release()
        }
    }


    AndroidView(
        modifier =
        Modifier
            .fillMaxWidth()
            .height(
                220.dp
            ),

        factory = {

            PlayerView(
                it
            ).apply {

                this.player =
                    player

                useController =
                    true
            }
        }
    )
}