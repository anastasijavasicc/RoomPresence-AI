package com.example.roompresencesir1.network

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener


class RoomPresenceWebSocketClient(
    private val url: String,
    private val listener: Listener
) {

    interface Listener {

        fun onConnected()

        fun onMessage(
            message: String
        )

        fun onDisconnected()

        fun onError(
            message: String
        )
    }


    private val client =
        OkHttpClient()

    private var webSocket:
            WebSocket? = null


    fun connect() {

        val request =
            Request.Builder()
                .url(
                    url
                )
                .build()


        webSocket =
            client.newWebSocket(

                request,

                object :
                    WebSocketListener() {


                    override fun onOpen(
                        webSocket: WebSocket,
                        response: Response
                    ) {

                        listener.onConnected()

                        webSocket.send(
                            "STATUS"
                        )
                    }


                    override fun onMessage(
                        webSocket: WebSocket,
                        text: String
                    ) {

                        listener.onMessage(
                            text
                        )
                    }


                    override fun onClosed(
                        webSocket: WebSocket,
                        code: Int,
                        reason: String
                    ) {

                        listener.onDisconnected()
                    }


                    override fun onFailure(
                        webSocket: WebSocket,
                        t: Throwable,
                        response: Response?
                    ) {

                        listener.onError(
                            t.message
                                ?: "Connection error"
                        )
                    }
                }
            )
    }


    fun send(
        command: String
    ) {

        webSocket?.send(
            command
        )
    }


    fun disconnect() {

        webSocket?.close(
            1000,
            "Application closed"
        )

        client
            .dispatcher
            .executorService
            .shutdown()
    }
}