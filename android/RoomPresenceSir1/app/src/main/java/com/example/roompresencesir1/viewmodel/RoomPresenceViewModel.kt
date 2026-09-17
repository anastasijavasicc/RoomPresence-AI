package com.example.roompresencesir1.viewmodel

import android.os.Handler
import android.os.Looper

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel

import com.example.roompresencesir1.AppConfig
import com.example.roompresencesir1.model.RoomPresenceUiState
import com.example.roompresencesir1.network.ArchiveRepository
import com.example.roompresencesir1.network.RoomPresenceWebSocketClient

import org.json.JSONObject


class RoomPresenceViewModel :
    ViewModel(),
    RoomPresenceWebSocketClient.Listener {


    private val mainHandler =
        Handler(
            Looper.getMainLooper()
        )


    private val _uiState =
        mutableStateOf(
            RoomPresenceUiState()
        )


    val uiState:
            State<RoomPresenceUiState>
        get() = _uiState


    var onPersonDetected:
            ((Double) -> Unit)? =
        null


    private var reconnectEnabled =
        true


    private val socketClient =
        RoomPresenceWebSocketClient(
            AppConfig.WEBSOCKET_URL,
            this
        )


    private val archiveRepository =
        ArchiveRepository()


    init {

        socketClient.connect()

        refreshArchive()
    }


    // ========================================================
    // CONNECTION
    // ========================================================

    override fun onConnected() {

        updateOnMain {

            it.copy(
                connected =
                true,

                systemStatus =
                "Monitoring",

                lastMessage =
                "Connected to Raspberry Pi"
            )
        }
    }


    override fun onDisconnected() {

        updateOnMain {

            it.copy(
                connected =
                false,

                systemStatus =
                "Offline",

                lastMessage =
                "Connection lost"
            )
        }

        reconnect()
    }


    override fun onError(
        message: String
    ) {

        updateOnMain {

            it.copy(
                connected =
                false,

                systemStatus =
                "Connection error",

                lastMessage =
                message
            )
        }

        reconnect()
    }


    private fun reconnect() {

        if (
            !reconnectEnabled
        ) {
            return
        }


        mainHandler.postDelayed(
            {

                if (
                    reconnectEnabled
                    &&
                    !_uiState
                        .value
                        .connected
                ) {

                    socketClient.connect()
                }

            },
            3000
        )
    }


    // ========================================================
    // WEBSOCKET MESSAGE
    // ========================================================

    override fun onMessage(
        message: String
    ) {

        try {

            val json =
                JSONObject(
                    message
                )


            when (
                json.optString(
                    "type"
                )
            ) {


                "CONNECTED" -> {

                    updateOnMain {

                        it.copy(
                            connected =
                            true,

                            systemStatus =
                            "Monitoring"
                        )
                    }
                }


                "STATUS" -> {

                    val data =
                        json.optJSONObject(
                            "data"
                        )
                            ?: return


                    updateOnMain {

                        val image =
                            data.optString(
                                "detection_image",
                                ""
                            )


                        val video =
                            data.optString(
                                "last_video",
                                ""
                            )


                        it.copy(

                            connected =
                            true,

                            systemStatus =
                            data.optString(
                                "state",
                                "Monitoring"
                            ),

                            personDetected =
                            data.optBoolean(
                                "person_detected",
                                false
                            ),

                            confidence =
                            data.optDouble(
                                "person_confidence",
                                0.0
                            ),

                            liveSessionActive =
                            data.optBoolean(
                                "live_session_active",
                                false
                            ),

                            personsDetected =
                            data.optInt(
                                "persons_detected",
                                0
                            ),

                            recording =
                            data.optBoolean(
                                "video_recording",
                                false
                            ),

                            audioEvent =
                            data.optString(
                                "audio_event",
                                "-"
                            ),

                            audioConfidence =
                            data.optDouble(
                                "audio_confidence",
                                0.0
                            ),

                            lastDetectionImage =
                            convertUrl(
                                image,
                                it.lastDetectionImage
                            ),

                            lastVideo =
                            convertUrl(
                                video,
                                it.lastVideo
                            )
                        )
                    }
                }


                // ============================================
                // AUDIO EVENT - NOVO
                // ============================================

                "AUDIO_EVENT" -> {

                    val event =
                        json.optString(
                            "event",
                            "-"
                        )


                    val confidence =
                        json.optDouble(
                            "confidence",
                            0.0
                        )


                    updateOnMain {

                        it.copy(
                            audioEvent =
                            event,

                            audioConfidence =
                            confidence,

                            lastMessage =
                            "Audio trigger: $event"
                        )
                    }
                }


                "PERSON_DETECTED" -> {

                    val confidence =
                        json.optDouble(
                            "confidence",
                            0.0
                        )


                    val image =
                        json.optString(
                            "image",
                            ""
                        )


                    updateOnMain {

                        it.copy(

                            personDetected =
                            true,

                            confidence =
                            confidence,

                            personsDetected =
                            json.optInt(
                                "persons",
                                1
                            ),

                            systemStatus =
                            "Person detected",

                            lastDetectionImage =
                            convertUrl(
                                image,
                                it.lastDetectionImage
                            ),

                            lastMessage =
                            "Person detected"
                        )
                    }


                    mainHandler.post {

                        onPersonDetected?.invoke(
                            confidence
                        )
                    }


                    refreshArchive()
                }


                "NO_PERSON" -> {

                    updateOnMain {

                        it.copy(
                            personDetected =
                            false,

                            confidence =
                            0.0,

                            personsDetected =
                            0,

                            systemStatus =
                            "Monitoring"
                        )
                    }
                }


                "LIVE_STARTED" -> {

                    updateOnMain {

                        it.copy(

                            liveSessionActive =
                            true,

                            recording =
                            true,

                            systemStatus =
                            "Live monitoring"
                        )
                    }
                }


                "PERSON_COUNT" -> {

                    updateOnMain {

                        it.copy(
                            personsDetected =
                            json.optInt(
                                "persons",
                                0
                            )
                        )
                    }
                }


                "LIVE_ENDED" -> {

                    val video =
                        json.optString(
                            "video",
                            ""
                        )


                    updateOnMain {

                        it.copy(

                            liveSessionActive =
                            false,

                            recording =
                            false,

                            personsDetected =
                            0,

                            systemStatus =
                            "Monitoring",

                            lastVideo =
                            convertUrl(
                                video,
                                it.lastVideo
                            ),

                            lastMessage =
                            "Event recording saved"
                        )
                    }


                    refreshArchive()
                }


                "MONITORING_ON" -> {

                    updateOnMain {

                        it.copy(
                            monitoringEnabled =
                            true,

                            systemStatus =
                            "Monitoring"
                        )
                    }
                }


                "MONITORING_OFF" -> {

                    updateOnMain {

                        it.copy(
                            monitoringEnabled =
                            false,

                            systemStatus =
                            "Monitoring paused"
                        )
                    }
                }


                "ALARM_ACTIVATED" -> {

                    updateOnMain {

                        it.copy(
                            lastMessage =
                            "Alarm activated"
                        )
                    }
                }


                "ARDUINO_DISCONNECTED" -> {

                    updateOnMain {

                        it.copy(
                            systemStatus =
                            "Arduino disconnected"
                        )
                    }
                }


                "VISION_ERROR",
                "LIVE_ERROR" -> {

                    updateOnMain {

                        it.copy(
                            systemStatus =
                            "System error",

                            lastMessage =
                            json.optString(
                                "message",
                                "System error"
                            )
                        )
                    }
                }
            }

        } catch (
            e: Exception
        ) {

            updateOnMain {

                it.copy(
                    lastMessage =
                    "Invalid server response"
                )
            }
        }
    }


    // ========================================================
    // ARCHIVE
    // ========================================================

    fun refreshArchive() {

        updateState {

            it.copy(
                archiveLoading =
                true
            )
        }


        archiveRepository.loadArchive(

            onSuccess = {
                    archive ->

                updateOnMain {

                    it.copy(
                        detectionArchive =
                        archive
                            .detectionImages,

                        arduinoArchive =
                        archive
                            .arduinoFrames,

                        videoArchive =
                        archive
                            .videos,

                        archiveLoading =
                        false
                    )
                }
            },

            onError = {

                updateOnMain {

                    it.copy(
                        archiveLoading =
                        false
                    )
                }
            }
        )
    }


    // ========================================================
    // COMMANDS
    // ========================================================

    fun activateAlarm() {

        socketClient.send(
            "ACTIVATE_ALARM"
        )
    }


    fun setMonitoring(
        enabled: Boolean
    ) {

        updateState {

            it.copy(
                monitoringEnabled =
                enabled
            )
        }


        socketClient.send(

            if (
                enabled
            )
                "MONITORING_ON"
            else
                "MONITORING_OFF"
        )
    }


    // ========================================================
    // HELPERS
    // ========================================================

    private fun convertUrl(
        value: String,
        oldValue: String?
    ): String? {

        if (
            value.isBlank()
            ||
            value == "null"
        ) {

            return oldValue
        }


        return if (
            value.startsWith(
                "http"
            )
        ) {

            value

        } else {

            AppConfig.BASE_HTTP_URL + value
        }
    }


    private fun updateOnMain(
        block:
            (RoomPresenceUiState)
        -> RoomPresenceUiState
    ) {

        mainHandler.post {

            updateState(
                block
            )
        }
    }


    private fun updateState(
        block:
            (RoomPresenceUiState)
        -> RoomPresenceUiState
    ) {

        _uiState.value =
            block(
                _uiState.value
            )
    }


    override fun onCleared() {

        reconnectEnabled =
            false

        socketClient.disconnect()

        super.onCleared()
    }
}