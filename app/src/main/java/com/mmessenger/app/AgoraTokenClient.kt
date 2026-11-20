
package com.mmessenger.app

import android.os.Handler
import android.os.Looper
import com.google.firebase.auth.FirebaseAuth
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class AgoraTokenResponse(
    val appId: String,
    val channelName: String,
    val userAccount: String,
    val token: String
)

object AgoraTokenClient {

    private const val TOKEN_URL =
        "https://m-messenger-agora-token.muhamad24-soleh.workers.dev/rtc-token"

    fun requestToken(
        channelName: String,
        callback: (Result<AgoraTokenResponse>) -> Unit
    ) {
        val user = FirebaseAuth.getInstance().currentUser

        if (user == null) {
            callback(
                Result.failure(
                    Exception("Silakan login terlebih dahulu.")
                )
            )
            return
        }

        user.getIdToken(false)
            .addOnSuccessListener { tokenResult ->

                val idToken = tokenResult.token

                if (idToken.isNullOrBlank()) {
                    callback(
                        Result.failure(
                            Exception("Firebase ID Token tidak tersedia.")
                        )
                    )
                    return@addOnSuccessListener
                }

                Thread {
                    var connection: HttpURLConnection? = null

                    try {
                        connection =
                            URL(TOKEN_URL).openConnection()
                                as HttpURLConnection

                        connection.requestMethod = "POST"
                        connection.doOutput = true
                        connection.connectTimeout = 20000
                        connection.readTimeout = 20000

                        connection.setRequestProperty(
                            "Authorization",
                            "Bearer $idToken"
                        )

                        connection.setRequestProperty(
                            "Content-Type",
                            "application/json; charset=utf-8"
                        )

                        val body = JSONObject()
                            .put("channelName", channelName)
                            .toString()

                        connection.outputStream.use {
                            it.write(body.toByteArray(Charsets.UTF_8))
                        }

                        val status = connection.responseCode

                        val stream =
                            if (status in 200..299) {
                                connection.inputStream
                            } else {
                                connection.errorStream
                            }

                        val response = stream
                            ?.bufferedReader()
                            ?.use { it.readText() }
                            .orEmpty()

                        if (status !in 200..299) {
                            val message = try {
                                JSONObject(response)
                                    .optString("error", "HTTP $status")
                            } catch (_: Exception) {
                                "HTTP $status"
                            }

                            throw Exception(message)
                        }

                        val json = JSONObject(response)

                        val result = AgoraTokenResponse(
                            appId = json.getString("appId"),
                            channelName = json.getString("channelName"),
                            userAccount = json.getString("userAccount"),
                            token = json.getString("token")
                        )

                        Handler(Looper.getMainLooper()).post {
                            callback(Result.success(result))
                        }

                    } catch (error: Exception) {
                        Handler(Looper.getMainLooper()).post {
                            callback(
                                Result.failure(error)
                            )
                        }
                    } finally {
                        connection?.disconnect()
                    }
                }.start()
            }
            .addOnFailureListener { error ->
                callback(Result.failure(error))
            }
    }
}
