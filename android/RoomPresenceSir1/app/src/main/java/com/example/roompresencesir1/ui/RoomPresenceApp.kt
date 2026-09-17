package com.example.roompresencesir1.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding

import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

import androidx.compose.ui.Modifier

import com.example.roompresencesir1.model.RoomPresenceUiState
import com.example.roompresencesir1.ui.screens.ActivityScreen
import com.example.roompresencesir1.ui.screens.HistoryScreen
import com.example.roompresencesir1.ui.screens.HomeScreen
import com.example.roompresencesir1.ui.screens.LiveScreen

import com.example.roompresencesir1.ui.theme.AppBackground
import com.example.roompresencesir1.ui.theme.AppMuted
import com.example.roompresencesir1.ui.theme.AppPrimary
import com.example.roompresencesir1.ui.theme.AppSurface


private enum class AppTab {

    HOME,
    LIVE,
    ACTIVITY,
    HISTORY
}


@Composable
fun RoomPresenceApp(
    state:
    RoomPresenceUiState,

    onMonitoringChanged:
        (Boolean) -> Unit,

    onAlarm:
        () -> Unit,

    onRefreshHistory:
        () -> Unit
) {

    var selectedTab by
    rememberSaveable {

        mutableStateOf(
            AppTab.HOME
        )
    }


    Scaffold(
        containerColor =
        AppBackground,

        bottomBar = {

            NavigationBar(
                containerColor =
                AppSurface
            ) {


                NavigationBarItem(
                    selected =
                    selectedTab
                            == AppTab.HOME,

                    onClick = {

                        selectedTab =
                            AppTab.HOME
                    },

                    icon = {

                        Text(
                            text =
                            "⌂",

                            color =
                            if (
                                selectedTab
                                == AppTab.HOME
                            )
                                AppPrimary
                            else
                                AppMuted
                        )
                    },

                    label = {

                        Text(
                            "Home"
                        )
                    }
                )


                NavigationBarItem(
                    selected =
                    selectedTab
                            == AppTab.LIVE,

                    onClick = {

                        selectedTab =
                            AppTab.LIVE
                    },

                    icon = {

                        Text(
                            text =
                            "●",

                            color =
                            if (
                                selectedTab
                                == AppTab.LIVE
                            )
                                AppPrimary
                            else
                                AppMuted
                        )
                    },

                    label = {

                        Text(
                            "Live"
                        )
                    }
                )


                NavigationBarItem(
                    selected =
                    selectedTab
                            == AppTab.ACTIVITY,

                    onClick = {

                        selectedTab =
                            AppTab.ACTIVITY
                    },

                    icon = {

                        Text(
                            text =
                            "◉",

                            color =
                            if (
                                selectedTab
                                == AppTab.ACTIVITY
                            )
                                AppPrimary
                            else
                                AppMuted
                        )
                    },

                    label = {

                        Text(
                            "Activity"
                        )
                    }
                )


                NavigationBarItem(
                    selected =
                    selectedTab
                            == AppTab.HISTORY,

                    onClick = {

                        selectedTab =
                            AppTab.HISTORY

                        onRefreshHistory()
                    },

                    icon = {

                        Text(
                            text =
                            "◷",

                            color =
                            if (
                                selectedTab
                                == AppTab.HISTORY
                            )
                                AppPrimary
                            else
                                AppMuted
                        )
                    },

                    label = {

                        Text(
                            "History"
                        )
                    }
                )
            }
        }
    ) {
            paddingValues ->


        Box(
            modifier =
            Modifier.padding(
                paddingValues
            )
        ) {


            when (
                selectedTab
            ) {

                AppTab.HOME -> {

                    HomeScreen(
                        state =
                        state,

                        onMonitoringChanged =
                        onMonitoringChanged,

                        onAlarm =
                        onAlarm
                    )
                }


                AppTab.LIVE -> {

                    LiveScreen(
                        state =
                        state
                    )
                }


                AppTab.ACTIVITY -> {

                    ActivityScreen(
                        state =
                        state
                    )
                }


                AppTab.HISTORY -> {

                    HistoryScreen(
                        state =
                        state,

                        onRefresh =
                        onRefreshHistory
                    )
                }
            }
        }
    }
}