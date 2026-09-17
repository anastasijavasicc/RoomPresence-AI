package com.example.roompresencesir1

object AppConfig {

    const val RASPBERRY_IP =
        "172.20.10.2"

    const val BASE_HTTP_URL =
        "http://$RASPBERRY_IP:5000"

    const val WEBSOCKET_URL =
        "ws://$RASPBERRY_IP:5000/ws"

    const val HLS_URL =
        "$BASE_HTTP_URL/static/hls/live.m3u8"

    const val NOTIFICATION_CHANNEL =
        "room_presence_alerts"
}