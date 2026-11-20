
package com.mmessenger.app

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private const val CALL_NOTIFICATION_WEB_APP_URL =
    "https://script.google.com/macros/s/AKfycbyaTSSa9dRaQB3NNxKskFvh9LlBFNkkya5xWWcvyPsYfHCSAMT-_WP0UGFd0kbUI4yz/exec"

private const val AGORA_TOKEN_WORKER_URL =
    "https://m-messenger-agora-token.muhamad24-soleh.workers.dev/rtc-token"

data class MCall(
    val id: String,
    val callerId: String,
    val receiverId: String,
    val channelName: String,
    val callType: String,
    val status: String,
    val createdAt: Timestamp? = null
)

data class AgoraCredentials(
    val appId: String,
    val channelName: String,
    val userAccount: String,
    val token: String,
    val expiresIn: Long = 3600
)

object CallManager {

    private val db = FirebaseFirestore.getInstance()
    private val mainHandler = Handler(Looper.getMainLooper())

    private fun deliverResult(action: () -> Unit) {
        mainHandler.post(action)
    }

    // =====================================
    // MEMBUAT PANGGILAN
    // =====================================

    fun createCall(
        callerId: String,
        receiverId: String,
        callType: String,
        callback: (Result<String>) -> Unit
    ) {
        if (callerId.isBlank() || receiverId.isBlank()) {
            callback(Result.failure(Exception("Identitas pengguna tidak valid.")))
            return
        }

        if (callerId == receiverId) {
            callback(Result.failure(Exception("Tidak dapat menelepon diri sendiri.")))
            return
        }

        val ref = db.collection("calls").document()
        val channel = "mcall_${ref.id}"

        val data = hashMapOf(
            "callerId" to callerId,
            "receiverId" to receiverId,
            "participants" to listOf(callerId, receiverId),
            "channelName" to channel,
            "callType" to callType,
            "status" to "ringing",
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )

        ref.set(data)
            .addOnSuccessListener {
                sendIncomingCallNotification(ref.id)
                callback(Result.success(ref.id))
            }
            .addOnFailureListener {
                callback(Result.failure(it))
            }
    }

    // =====================================
    // MENGAMBIL TOKEN AGORA DARI CLOUDFLARE
    // =====================================

    fun requestAgoraToken(
        channelName: String,
        callback: (Result<AgoraCredentials>) -> Unit
    ) {
        if (channelName.isBlank()) {
            callback(
                Result.failure(Exception("Nama channel tidak valid."))
            )
            return
        }

        val user = FirebaseAuth.getInstance().currentUser

        if (user == null) {
            callback(
                Result.failure(Exception("Pengguna belum login."))
            )
            return
        }

        user.getIdToken(false)
            .addOnSuccessListener { tokenResult ->

                val firebaseToken = tokenResult.token

                if (firebaseToken.isNullOrBlank()) {
                    callback(
                        Result.failure(
                            Exception("Firebase ID Token kosong.")
                        )
                    )
                    return@addOnSuccessListener
                }

                Thread {
                    var connection: HttpURLConnection? = null

                    try {
                        connection =
                            URL(AGORA_TOKEN_WORKER_URL)
                                .openConnection() as HttpURLConnection

                        connection.requestMethod = "POST"
                        connection.doOutput = true
                        connection.connectTimeout = 20000
                        connection.readTimeout = 20000

                        connection.setRequestProperty(
                            "Content-Type",
                            "application/json; charset=utf-8"
                        )

                        connection.setRequestProperty(
                            "Authorization",
                            "Bearer $firebaseToken"
                        )

                        val requestBody = JSONObject()
                            .put("channelName", channelName)
                            .toString()

                        connection.outputStream.use {
                            it.write(
                                requestBody.toByteArray(Charsets.UTF_8)
                            )
                        }

                        val statusCode = connection.responseCode

                        val responseStream =
                            if (statusCode in 200..299) {
                                connection.inputStream
                            } else {
                                connection.errorStream
                            }

                        val responseText =
                            responseStream?.bufferedReader()?.use {
                                it.readText()
                            }.orEmpty()

                        val json = JSONObject(responseText)

                        if (statusCode !in 200..299) {
                            throw Exception(
                                json.optString(
                                    "error",
                                    "Gagal mengambil token Agora. HTTP $statusCode"
                                )
                            )
                        }

                        val appId = json.optString("appId")
                        val returnedChannel =
                            json.optString("channelName")
                        val userAccount =
                            json.optString("userAccount")
                        val agoraToken =
                            json.optString("token")

                        if (
                            appId.isBlank() ||
                            returnedChannel != channelName ||
                            userAccount.isBlank() ||
                            agoraToken.isBlank()
                        ) {
                            throw Exception(
                                "Respons token Agora tidak lengkap atau tidak sesuai."
                            )
                        }

                        val credentials = AgoraCredentials(
                            appId = appId,
                            channelName = returnedChannel,
                            userAccount = userAccount,
                            token = agoraToken,
                            expiresIn = json.optLong("expiresIn", 3600)
                        )

                        Log.d(
                            "MAgora",
                            "Token Agora berhasil diperoleh."
                        )

                        deliverResult {
                            callback(Result.success(credentials))
                        }

                    } catch (error: Exception) {

                        Log.e(
                            "MAgora",
                            "Gagal mengambil token Agora.",
                            error
                        )

                        deliverResult {
                            callback(Result.failure(error))
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

    // =====================================
    // NOTIFIKASI PANGGILAN MASUK
    // =====================================

    private fun sendIncomingCallNotification(callId: String) {

        val user = FirebaseAuth.getInstance().currentUser

        if (user == null) {
            Log.e("MCallNotification", "Pengguna belum login.")
            return
        }

        user.getIdToken(false)
            .addOnSuccessListener { tokenResult ->

                val idToken = tokenResult.token

                if (idToken.isNullOrBlank()) return@addOnSuccessListener

                Thread {
                    var connection: HttpURLConnection? = null

                    try {
                        connection =
                            URL(CALL_NOTIFICATION_WEB_APP_URL)
                                .openConnection() as HttpURLConnection

                        connection.requestMethod = "POST"
                        connection.doOutput = true
                        connection.connectTimeout = 20000
                        connection.readTimeout = 20000
                        connection.instanceFollowRedirects = true

                        connection.setRequestProperty(
                            "Content-Type",
                            "application/json; charset=utf-8"
                        )

                        val body = JSONObject()
                            .put("type", "incoming_call")
                            .put("idToken", idToken)
                            .put("callId", callId)
                            .toString()

                        connection.outputStream.use {
                            it.write(body.toByteArray(Charsets.UTF_8))
                        }

                        val statusCode = connection.responseCode

                        val responseStream =
                            if (statusCode in 200..299) {
                                connection.inputStream
                            } else {
                                connection.errorStream
                            }

                        val responseText =
                            responseStream?.bufferedReader()?.use {
                                it.readText()
                            }.orEmpty()

                        val responseJson = try {
                            JSONObject(responseText)
                        } catch (_: Exception) {
                            JSONObject()
                        }

                        if (
                            statusCode !in 200..299 ||
                            !responseJson.optBoolean("success", false)
                        ) {
                            Log.e(
                                "MCallNotification",
                                "Notifikasi gagal: $responseText"
                            )
                        }

                    } catch (error: Exception) {
                        Log.e(
                            "MCallNotification",
                            "Kesalahan mengirim notifikasi.",
                            error
                        )
                    } finally {
                        connection?.disconnect()
                    }
                }.start()
            }
            .addOnFailureListener {
                Log.e(
                    "MCallNotification",
                    "Gagal mengambil Firebase ID Token.",
                    it
                )
            }
    }

    // =====================================
    // MENERIMA PANGGILAN
    // =====================================

    fun acceptCall(
        callId: String,
        callback: (Result<Unit>) -> Unit
    ) {
        updateStatus(callId, "accepted", "acceptedAt", callback)
    }

    // =====================================
    // MENOLAK PANGGILAN
    // =====================================

    fun rejectCall(
        callId: String,
        callback: (Result<Unit>) -> Unit = {}
    ) {
        updateStatus(callId, "rejected", "endedAt", callback)
    }

    // =====================================
    // MENGAKHIRI PANGGILAN
    // =====================================

    fun endCall(
        callId: String,
        callback: (Result<Unit>) -> Unit = {}
    ) {
        updateStatus(callId, "ended", "endedAt", callback)
    }

    private fun updateStatus(
        callId: String,
        status: String,
        timeField: String,
        callback: (Result<Unit>) -> Unit
    ) {
        db.collection("calls")
            .document(callId)
            .update(
                mapOf(
                    "status" to status,
                    timeField to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            .addOnSuccessListener {
                callback(Result.success(Unit))
            }
            .addOnFailureListener {
                callback(Result.failure(it))
            }
    }

    // =====================================
    // MEMANTAU STATUS PANGGILAN
    // =====================================

    fun listenCall(
        callId: String,
        callback: (MCall?) -> Unit
    ): ListenerRegistration {

        return db.collection("calls")
            .document(callId)
            .addSnapshotListener { snapshot, error ->

                if (
                    error != null ||
                    snapshot == null ||
                    !snapshot.exists()
                ) {
                    callback(null)
                    return@addSnapshotListener
                }

                callback(
                    MCall(
                        id = snapshot.id,
                        callerId =
                            snapshot.getString("callerId") ?: "",
                        receiverId =
                            snapshot.getString("receiverId") ?: "",
                        channelName =
                            snapshot.getString("channelName") ?: "",
                        callType =
                            snapshot.getString("callType") ?: "audio",
                        status =
                            snapshot.getString("status") ?: "",
                        createdAt =
                            snapshot.getTimestamp("createdAt")
                    )
                )
            }
    }

    // =====================================
    // MENDETEKSI PANGGILAN MASUK
    // =====================================

    fun listenIncomingCalls(
        userId: String,
        callback: (MCall) -> Unit
    ): ListenerRegistration {

        return db.collection("calls")
            .whereArrayContains("participants", userId)
            .addSnapshotListener { snapshot, error ->

                if (
                    error != null ||
                    snapshot == null
                ) {
                    return@addSnapshotListener
                }

                snapshot.documents.forEach { document ->

                    val receiverId =
                        document.getString("receiverId") ?: ""

                    val status =
                        document.getString("status") ?: ""

                    if (
                        receiverId == userId &&
                        status == "ringing"
                    ) {
                        callback(
                            MCall(
                                id = document.id,
                                callerId =
                                    document.getString("callerId") ?: "",
                                receiverId = receiverId,
                                channelName =
                                    document.getString("channelName") ?: "",
                                callType =
                                    document.getString("callType") ?: "audio",
                                status = status,
                                createdAt =
                                    document.getTimestamp("createdAt")
                            )
                        )
                    }
                }
            }
    }
}
