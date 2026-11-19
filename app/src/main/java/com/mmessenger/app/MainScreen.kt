
package com.mmessenger.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val MBlue = Color(0xFF246BFD)

private data class HomeChatItem(
    val id: String,
    val contact: MatchedContact,
    val message: String,
    val time: String,
    val sortTime: Long
)

@Composable
fun MainScreen(
    onChatClick: (MatchedContact) -> Unit = {},
    onNewChat: () -> Unit = {},
    onContactsClick: () -> Unit = {},
    onProfileClick: () -> Unit = {}
) {
    var search by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf("Chat") }
    var chats by remember { mutableStateOf(listOf<HomeChatItem>()) }
    var loading by remember { mutableStateOf(true) }

    val db = remember { FirebaseFirestore.getInstance() }
    val currentUid = FirebaseAuth.getInstance().currentUser?.uid

    DisposableEffect(currentUid) {
        if (currentUid.isNullOrBlank()) {
            loading = false
            onDispose { }
        } else {
            val messageListeners =
                mutableMapOf<String, ListenerRegistration>()

            val registration = db.collection("chats")
                .whereArrayContains("participants", currentUid)
                .addSnapshotListener { snapshot, error ->

                    if (error != null || snapshot == null) {
                        loading = false
                    } else {
                        val activeIds = snapshot.documents.map {
                            it.id
                        }.toSet()

                        messageListeners.keys
                            .filter { it !in activeIds }
                            .toList()
                            .forEach { id ->
                                messageListeners.remove(id)?.remove()
                                chats = chats.filter { it.id != id }
                            }

                        snapshot.documents.forEach { chatDoc ->
                            val chatId = chatDoc.id

                            val participants =
                                chatDoc.get("participants") as? List<*>
                                    ?: emptyList<Any>()

                            val otherUid = participants
                                .filterIsInstance<String>()
                                .firstOrNull { it != currentUid }
                                ?: return@forEach

                            if (!messageListeners.containsKey(chatId)) {

                                db.collection("users")
                                    .document(otherUid)
                                    .get()
                                    .addOnSuccessListener { userDoc ->

                                        val phone =
                                            userDoc.getString("phoneNumber") ?: ""

                                        val email =
                                            userDoc.getString("email") ?: ""

                                        val contact = MatchedContact(
                                            name = phone.ifBlank {
                                                email.ifBlank {
                                                    "Pengguna M"
                                                }
                                            },
                                            number = phone,
                                            uid = otherUid
                                        )

                                        val latestMessageQuery =
                                            db.collection("chats")
                                                .document(chatId)
                                                .collection("messages")
                                                .orderBy(
                                                    "createdAt",
                                                    Query.Direction.DESCENDING
                                                )
                                                .limit(1)

                                        val messageListener =
                                            latestMessageQuery
                                                .addSnapshotListener { messages, msgError ->

                                                    if (msgError == null &&
                                                        messages != null
                                                    ) {
                                                        val document =
                                                            messages.documents.firstOrNull()

                                                        if (document != null) {
                                                            val text =
                                                                document.getString("text")
                                                                    ?: ""

                                                            val timestamp =
                                                                document.getTimestamp("createdAt")

                                                            val timeValue =
                                                                timestamp?.toDate()?.time
                                                                    ?: 0L

                                                            val timeText =
                                                                timestamp?.toDate()?.let {
                                                                    formatChatTime(it)
                                                                } ?: ""

                                                            val item = HomeChatItem(
                                                                id = chatId,
                                                                contact = contact,
                                                                message = text,
                                                                time = timeText,
                                                                sortTime = timeValue
                                                            )

                                                            chats = (
                                                                chats.filter {
                                                                    it.id != chatId
                                                                } + item
                                                            ).sortedByDescending {
                                                                it.sortTime
                                                            }
                                                        }
                                                    }

                                                    loading = false
                                                }

                                        messageListeners[chatId] =
                                            messageListener
                                    }
                            }
                        }

                        loading = false
                    }
                }

            onDispose {
                registration.remove()
                messageListeners.values.forEach {
                    it.remove()
                }
                messageListeners.clear()
            }
        }
    }

    val filtered = chats.filter {
        it.contact.name.contains(search, true) ||
        it.contact.number.contains(search, true)
    }

    Scaffold(
        containerColor = Color.White,
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNewChat,
                containerColor = MBlue,
                contentColor = Color.White
            ) {
                Text("+", fontSize = 30.sp)
            }
        },
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                listOf("Chat", "Kontak", "Profil").forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = {
                            selectedTab = tab
                            when (tab) {
                                "Kontak" -> onContactsClick()
                                "Profil" -> onProfileClick()
                            }
                        },
                        icon = {
                            Text(
                                when (tab) {
                                    "Chat" -> "●"
                                    "Kontak" -> "♙"
                                    else -> "●"
                                },
                                fontSize = 20.sp
                            )
                        },
                        label = { Text(tab) }
                    )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color.White)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFEAF1FF))
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "M",
                    fontSize = 34.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MBlue
                )

                Spacer(Modifier.width(10.dp))

                Column {
                    Text(
                        "Messenger",
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF17233B)
                    )
                    Text(
                        "Stay Connected",
                        color = Color.Gray,
                        fontSize = 12.sp
                    )
                }

                Spacer(Modifier.weight(1f))

                Text("●", color = Color(0xFF16A34A))
                Spacer(Modifier.width(5.dp))
                Text("Online", color = Color.DarkGray)
            }

            Text(
                "Pesan",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(20.dp)
            )

            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                placeholder = { Text("Cari percakapan...") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )

            Text(
                "Pesan terbaru",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                modifier = Modifier.padding(16.dp)
            )

            when {
                loading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                filtered.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (search.isNotBlank())
                                "Percakapan tidak ditemukan"
                            else
                                "Belum ada percakapan"
                        )
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.weight(1f)
                    ) {
                        items(filtered, key = { it.id }) { chat ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onChatClick(chat.contact)
                                    }
                                    .padding(
                                        horizontal = 16.dp,
                                        vertical = 13.dp
                                    ),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(50.dp)
                                        .background(
                                            Color(0xFFEAF1FF),
                                            CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        chat.contact.name
                                            .firstOrNull()
                                            ?.uppercase() ?: "M",
                                        color = MBlue,
                                        fontSize = 21.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Spacer(Modifier.width(13.dp))

                                Column(
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        chat.contact.name,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )

                                    Text(
                                        chat.message,
                                        color = Color.Gray,
                                        fontSize = 13.sp,
                                        maxLines = 1
                                    )
                                }

                                Text(
                                    chat.time,
                                    fontSize = 11.sp,
                                    color = Color.Gray
                                )
                            }

                            HorizontalDivider(
                                color = Color(0xFFF0F0F0)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatChatTime(date: Date): String {
    val now = Date()

    val sameDay =
        SimpleDateFormat("yyyyMMdd", Locale.getDefault())
            .format(date) ==
        SimpleDateFormat("yyyyMMdd", Locale.getDefault())
            .format(now)

    return if (sameDay) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
    } else {
        SimpleDateFormat("dd/MM", Locale.getDefault()).format(date)
    }
}
