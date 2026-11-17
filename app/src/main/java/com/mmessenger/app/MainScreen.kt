
package com.messenger.app

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

private val MBlue = Color(0xFF246BFD)

data class ChatItem(
    val name: String,
    val message: String,
    val time: String,
    val unread: Int = 0,
    val online: Boolean = false
)

@Composable
fun MainScreen(
    onChatClick: (ChatItem) -> Unit = {},
    onNewChat: () -> Unit = {},
    onContactsClick: () -> Unit = {},
    onProfileClick: () -> Unit = {}
) {
    var search by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf("Chat") }

    val chats = listOf(
        ChatItem("Andi", "Halo, apa kabar?", "10:42", 2, true),
        ChatItem("Budi", "Sampai nanti!", "09:35", 0, true),
        ChatItem("Siti", "Terima kasih 😊", "Kemarin"),
        ChatItem("Rizky", "Oke, siap!", "Kemarin", 1),
        ChatItem("Dina", "Selamat pagi", "Senin")
    )

    val filtered = chats.filter {
        it.name.contains(search, ignoreCase = true)
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
            NavigationBar(
                containerColor = Color.White
            ) {
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

            LazyColumn {
                items(filtered) { chat ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onChatClick(chat) }
                            .padding(horizontal = 16.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box {
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
                                    chat.name.first().toString(),
                                    color = MBlue,
                                    fontSize = 21.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            if (chat.online) {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .background(
                                            Color(0xFF16A34A),
                                            CircleShape
                                        )
                                        .align(Alignment.BottomEnd)
                                )
                            }
                        }

                        Spacer(Modifier.width(13.dp))

                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                chat.name,
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

                        Column(
                            horizontalAlignment = Alignment.End
                        ) {
                            Text(
                                chat.time,
                                fontSize = 11.sp,
                                color = Color.Gray
                            )

                            if (chat.unread > 0) {
                                Spacer(Modifier.height(5.dp))
                                Box(
                                    modifier = Modifier
                                        .background(
                                            MBlue,
                                            CircleShape
                                        )
                                        .padding(
                                            horizontal = 7.dp,
                                            vertical = 3.dp
                                        )
                                ) {
                                    Text(
                                        chat.unread.toString(),
                                        color = Color.White,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(
                        color = Color(0xFFF0F0F0)
                    )
                }
            }
        }
    }
}
