package com.example.roompresencesir1.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable


private val EdgeAiColorScheme =
    darkColorScheme(

        primary =
        AppPrimary,

        background =
        AppBackground,

        surface =
        AppSurface,

        onPrimary =
        AppText,

        onBackground =
        AppText,

        onSurface =
        AppText,

        error =
        AppDanger
    )


@Composable
fun RoomPresenceSir1Theme(
    content:
    @Composable () -> Unit
) {

    MaterialTheme(
        colorScheme =
        EdgeAiColorScheme,

        content =
        content
    )
}