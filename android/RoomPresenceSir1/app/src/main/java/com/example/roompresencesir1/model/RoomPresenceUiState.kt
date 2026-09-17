package com.example.roompresencesir1.model

data class RoomPresenceUiState(

    val connected: Boolean =
        false,

    val systemStatus: String =
        "Connecting...",

    val monitoringEnabled: Boolean =
        true,

    val personDetected: Boolean =
        false,

    val confidence: Double =
        0.0,

    val liveSessionActive: Boolean =
        false,

    val personsDetected: Int =
        0,

    val recording: Boolean =
        false,

    val audioEvent: String =
        "-",

    val audioConfidence: Double =
        0.0,

    val lastDetectionImage: String? =
        null,

    val lastVideo: String? =
        null,

    val lastMessage: String =
        "Waiting for Raspberry Pi...",

    val detectionArchive:
    List<ArchiveItem> =
        emptyList(),

    val arduinoArchive:
    List<ArchiveItem> =
        emptyList(),

    val videoArchive:
    List<ArchiveItem> =
        emptyList(),

    val archiveLoading: Boolean =
        false
)