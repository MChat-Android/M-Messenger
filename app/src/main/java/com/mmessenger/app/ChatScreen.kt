package com.mmessenger.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import java.text.SimpleDateFormat
import java.util.Locale

private data class ChatMessage(
    val id: String,
    val senderId: String,
    val text: String,
    val createdAt: Timestamp?
)

private fun conversationId(firstUid: String, secondUid: String): String =
    listOf(firstUid, secondUid).sorted().joinToString("_")

@Composable
fun ChatScreen(
    contact: MatchedContact,
    onBack: () -> Unit
) {
    val auth = remember { FirebaseAuth.getInstance() }
    val db = remember { FirebaseFirestore.getInstance() }
    val currentUid = auth.currentUser?.uid

    val chatId = remember(currentUid, contact.uid) {
        if (currentUid == null) "" else conversationId(currentUid, contact.uid)
    }

    var messages by remember { mutableStateOf(listOf<ChatMessage>()) }
    var messageText by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }

    DisposableEffect(chatId) {
        if (chatId.isBlank()) {
            errorMessage = "Sesi login tidak ditemukan. Silakan masuk kembali."
            onDispose { }
        } else {
            val registration = db.collection("chats")
                .document(chatId)
                .collection("messages")
                .orderBy("createdAt", Query.Direction.ASCENDING)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        errorMessage = "Gagal memuat pesan: ${error.localizedMessage}"
                    } else if (snapshot != null) {
                        messages = snapshot.documents.map { document ->
                            ChatMessage(
                                id = document.id,
                                senderId = document.getString("senderId") ?: "",
                                text = document.getString("text") ?: "",
                                createdAt = document.getTimestamp("createdAt")
                            )
                        }
                    }
                }

            onDispose { registration.remove() }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF7F9FE))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) {
                Text("← Kembali")
            }

            Spacer(Modifier.width(8.dp))

            Column {
                Text(contact.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    contact.number,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
        }

        if (errorMessage.isNotBlank()) {
            Text(
                errorMessage,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(12.dp)
            )
        }

        if (messages.isEmpty() && errorMessage.isBlank()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text("Belum ada pesan. Mulai percakapan!")
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages, key = { it.id }) { message ->
                    val mine = message.senderId == currentUid

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (mine) {
                            Arrangement.End
                        } else {
                            Arrangement.Start
                        }
                    ) {
                        Column(
                            modifier = Modifier
                                .widthIn(max = 280.dp)
                                .background(
                                    if (mine) Color(0xFFDCE8FF) else Color.White,
                                    RoundedCornerShape(14.dp)
                                )
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(message.text, color = Color(0xFF17233B))

                            message.createdAt?.let { timestamp ->
                                Text(
                                    SimpleDateFormat(
                                        "HH:mm",
                                        Locale.getDefault()
                                    ).format(timestamp.toDate()),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.Gray,
                                    modifier = Modifier.align(Alignment.End)
                                )
                            }
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = messageText,
                onValueChange = { messageText = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Tulis pesan...") },
                enabled = !sending,
                maxLines = 4
            )

            Spacer(Modifier.width(8.dp))

            Button(
                enabled = messageText.isNotBlank() &&
                    !sending &&
                    currentUid != null,
                onClick = {
                    val textToSend = messageText.trim()
                    val uid = currentUid

                    if (textToSend.isNotEmpty() && uid != null) {
                        sending = true
                        errorMessage = ""

                        val chatRef = db.collection("chats").document(chatId)

                        chatRef.set(
                            mapOf(
                                "participants" to listOf(uid, contact.uid),
                                "updatedAt" to FieldValue.serverTimestamp()
                            ),
                            SetOptions.merge()
                        ).continueWithTask {
                            chatRef.collection("messages").add(
                                mapOf(
                                    "senderId" to uid,
                                    "receiverId" to contact.uid,
                                    "text" to textToSend,
                                    "createdAt" to FieldValue.serverTimestamp()
                                )
                            )
                        }.addOnSuccessListener {
                            messageText = ""
                            sending = false
                        }.addOnFailureListener { error ->
                            errorMessage =
                                "Pesan gagal dikirim: ${error.localizedMessage}"
                            sending = false
                        }
                    }
                }
            ) {
                Text(if (sending) "..." else "Kirim")
            }
        }
    }
}
