
package com.mmessenger.app

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.FieldValue

data class MCall(
    val id: String,
    val callerId: String,
    val receiverId: String,
    val channelName: String,
    val callType: String,
    val status: String,
    val createdAt: Timestamp? = null
)

object CallManager {

    private val db = FirebaseFirestore.getInstance()

    fun createCall(
        callerId: String,
        receiverId: String,
        callType: String,
        callback: (Result<String>) -> Unit
    ) {
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
                callback(Result.success(ref.id))
            }
            .addOnFailureListener {
                callback(Result.failure(it))
            }
    }

    fun acceptCall(
        callId: String,
        callback: (Result<Unit>) -> Unit
    ) {
        updateStatus(
            callId,
            "accepted",
            "acceptedAt",
            callback
        )
    }

    fun rejectCall(
        callId: String,
        callback: (Result<Unit>) -> Unit = {}
    ) {
        updateStatus(
            callId,
            "rejected",
            "endedAt",
            callback
        )
    }

    fun endCall(
        callId: String,
        callback: (Result<Unit>) -> Unit = {}
    ) {
        updateStatus(
            callId,
            "ended",
            "endedAt",
            callback
        )
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

    fun listenCall(
        callId: String,
        callback: (MCall?) -> Unit
    ): ListenerRegistration {
        return db.collection("calls")
            .document(callId)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null || !snapshot.exists()) {
                    callback(null)
                    return@addSnapshotListener
                }

                callback(
                    MCall(
                        id = snapshot.id,
                        callerId = snapshot.getString("callerId") ?: "",
                        receiverId = snapshot.getString("receiverId") ?: "",
                        channelName = snapshot.getString("channelName") ?: "",
                        callType = snapshot.getString("callType") ?: "audio",
                        status = snapshot.getString("status") ?: "",
                        createdAt = snapshot.getTimestamp("createdAt")
                    )
                )
            }
    }

    fun listenIncomingCalls(
        userId: String,
        callback: (MCall) -> Unit
    ): ListenerRegistration {
        return db.collection("calls")
            .whereArrayContains("participants", userId)
            .addSnapshotListener { snapshot, error ->

                if (error != null || snapshot == null) return@addSnapshotListener

                snapshot.documents.forEach { document ->

                    val receiverId =
                        document.getString("receiverId") ?: ""

                    val status =
                        document.getString("status") ?: ""

                    if (receiverId == userId && status == "ringing") {
                        callback(
                            MCall(
                                id = document.id,
                                callerId = document.getString("callerId") ?: "",
                                receiverId = receiverId,
                                channelName = document.getString("channelName") ?: "",
                                callType = document.getString("callType") ?: "audio",
                                status = status,
                                createdAt = document.getTimestamp("createdAt")
                            )
                        )
                    }
                }
            }
    }
}
