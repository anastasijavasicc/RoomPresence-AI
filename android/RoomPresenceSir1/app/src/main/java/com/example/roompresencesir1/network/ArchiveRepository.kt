package com.example.roompresencesir1.network

import com.example.roompresencesir1.AppConfig
import com.example.roompresencesir1.model.ArchiveItem

import okhttp3.OkHttpClient
import okhttp3.Request

import org.json.JSONArray
import org.json.JSONObject


class ArchiveRepository {

    private val client =
        OkHttpClient()


    data class ArchiveResult(

        val detectionImages:
        List<ArchiveItem>,

        val arduinoFrames:
        List<ArchiveItem>,

        val videos:
        List<ArchiveItem>
    )


    fun loadArchive(
        onSuccess:
            (ArchiveResult) -> Unit,

        onError:
            (String) -> Unit
    ) {

        val request =
            Request.Builder()
                .url(
                    "${AppConfig.BASE_HTTP_URL}/api/archive"
                )
                .build()


        client.newCall(
            request
        ).enqueue(

            object :
                okhttp3.Callback {


                override fun onFailure(
                    call: okhttp3.Call,
                    e: java.io.IOException
                ) {

                    onError(
                        e.message
                            ?: "Archive request failed"
                    )
                }


                override fun onResponse(
                    call: okhttp3.Call,
                    response:
                    okhttp3.Response
                ) {

                    response.use {

                        if (
                            !response.isSuccessful
                        ) {

                            onError(
                                "Archive error ${response.code}"
                            )

                            return
                        }


                        val body =
                            response.body
                                ?.string()
                                ?: return


                        try {

                            val json =
                                JSONObject(
                                    body
                                )


                            val result =
                                ArchiveResult(

                                    detectionImages =
                                    parseArray(
                                        json.optJSONArray(
                                            "detection_images"
                                        )
                                    ),

                                    arduinoFrames =
                                    parseArray(
                                        json.optJSONArray(
                                            "arduino_frames"
                                        )
                                    ),

                                    videos =
                                    parseArray(
                                        json.optJSONArray(
                                            "videos"
                                        )
                                    )
                                )


                            onSuccess(
                                result
                            )

                        } catch (
                            e: Exception
                        ) {

                            onError(
                                e.message
                                    ?: "Invalid archive response"
                            )
                        }
                    }
                }
            }
        )
    }


    private fun parseArray(
        array: JSONArray?
    ): List<ArchiveItem> {

        if (
            array == null
        ) {

            return emptyList()
        }


        val result =
            mutableListOf<
                    ArchiveItem
                    >()


        for (
        index
        in 0 until array.length()
        ) {

            val item =
                array.getJSONObject(
                    index
                )


            val relativeUrl =
                item.optString(
                    "url"
                )


            result.add(

                ArchiveItem(

                    name =
                    item.optString(
                        "name"
                    ),

                    url =
                    AppConfig.BASE_HTTP_URL
                            + relativeUrl,

                    timestamp =
                    (
                            item.optDouble(
                                "timestamp",
                                0.0
                            )
                                    * 1000
                            ).toLong()
                )
            )
        }


        return result
    }
}