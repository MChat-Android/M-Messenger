
package com.mmessenger.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
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

// Menyamakan format nomor telepon Indonesia.
private fun normalizePhone(input: String): String? {
    var digits = input.filter { it.isDigit() }

    if (digits.startsWith("00")) {
        digits = digits.drop(2)
    }

    if (digits.startsWith("0")) {
        digits = "62" + digits.drop(1)
    } else if (digits.startsWith("8")) {
        digits = "62$digits"
    }

    if (!digits.startsWith("62")) return null
    if (digits.length !in 10..15) return null

    return digits
}

// Mencari nama berdasarkan nomor di buku kontak HP.
private fun findSavedContactName(
    context: Context,
    phone: String
): String? {
    val permissionGranted =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED

    if (!permissionGranted) return null

    val targetNumber = normalizePhone(phone) ?: return null

    return try {
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            ),
            null,
            null,
            null
        )?.use { cursor ->

            val nameIndex = cursor.getColumnIndex(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
            )

            val numberIndex = cursor.getColumnIndex(
                ContactsContract.CommonDataKinds.Phone.NUMBER
            )

            while (cursor.moveToNext()) {
                val name = cursor.getString(nameIndex) ?: ""
                val number = cursor.getString(numberIndex) ?: ""

                if (
                    normalizePhone(number) == targetNumber &&
                    name.isNotBlank()
                ) {
                    return@use name
                }
            }

            null
        }
    } catch (e: Exception) {
        null
    }
}

@Composable
fun MainScreen(
    onChatClick: (MatchedContact) -> Unit = {},
    onNewChat: () -> Unit = {},
    onContactsClick: () -> Unit = {},
    onProfileClick: () -> Unit = {}
) {
    var search by remember { mutableStateOf("") }

    var chats by remember {
        mutableStateOf(listOf<HomeChatItem>())
    }

    var loading by remember {
        mutableStateOf(true)
    }

    val db = remember {
        FirebaseFirestore.getInstance()
    }

    val context = androidx.compose.ui.platform.LocalContext.current

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

                                        val savedName =
                                            findSavedContactName(context, phone)

                                        val displayName =
                                            savedName ?: phone.ifBlank {
                                                "Pengguna M"
                                            }

                                        val photo =
                                            userDoc.getString("photoBase64")

                                        val contact = MatchedContact(
                                            name = displayName,
                                            number = phone,
                                            uid = otherUid,
                                            photoBase64 = photo
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

                                                    if (
                                                        msgError == null &&
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
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNewChat,
                containerColor = MBlue,
                contentColor = Color.White
            ) {
                Text("+", fontSize = 30.sp)
            }
        }
    ) { padding ->

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {

            // JUDUL APLIKASI TANPA IKON
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = 20.dp,
                            end = 20.dp,
                            top = 22.dp,
                            bottom = 16.dp
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "M Messenger",
                        fontSize = 27.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // KOLOM PENCARIAN
            item {
                OutlinedTextField(
                    value = search,
                    onValueChange = {
                        search = it
                    },
                    placeholder = {
                        Text(
                            "Cari percakapan...",
                            fontSize = 16.sp
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Cari"
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(40.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = 16.dp,
                            vertical = 8.dp
                        )
                )
            }

            // JARAK ANTARA PENCARIAN DAN DAFTAR PESAN
            item {
                Spacer(modifier = Modifier.height(12.dp))
            }

            when {
                loading -> {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }

                filtered.isEmpty() -> {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                if (search.isNotBlank()) {
                                    "Percakapan tidak ditemukan"
                                } else {
                                    "Belum ada percakapan"
                                }
                            )
                        }
                    }
                }

                else -> {
                    items(
                        filtered,
                        key = { it.id }
                    ) { chat ->

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onChatClick(chat.contact)
                                }
                                .padding(
                                    horizontal = 18.dp,
                                    vertical = 17.dp
                                ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {

                            // FOTO KONTAK
                            ProfileAvatar(
                                photoBase64 = chat.contact.photoBase64,
                                name = chat.contact.name,
                                size = 58.dp
                            )

                            Spacer(
                                modifier = Modifier.width(14.dp)
                            )

                            // NAMA DAN PESAN TERAKHIR
                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = chat.contact.name,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Spacer(
                                    modifier = Modifier.height(5.dp)
                                )

                                Text(
                                    text = chat.message,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Spacer(
                                modifier = Modifier.width(8.dp)
                            )

                            // WAKTU PESAN
                            Text(
                                text = chat.time,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(
                                start = 90.dp
                            ),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(
                                alpha = 0.35f
                            )
                        )
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
