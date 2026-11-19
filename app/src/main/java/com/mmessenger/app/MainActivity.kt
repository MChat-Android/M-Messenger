
package com.mmessenger.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging

class MainActivity : ComponentActivity() {

    private var notificationUidState: MutableState<String?>? = null

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        notificationUidState?.value = intent.getStringExtra("openChatWith")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            var showHome by rememberSaveable {
                mutableStateOf(
                    FirebaseAuth.getInstance().currentUser != null
                )
            }

            var showContacts by rememberSaveable {
                mutableStateOf(false)
            }

            var showProfile by rememberSaveable {
                mutableStateOf(false)
            }

            var selectedContact by remember {
                mutableStateOf<MatchedContact?>(null)
            }

            val notificationUid = remember {
                mutableStateOf(intent.getStringExtra("openChatWith"))
            }
            notificationUidState = notificationUid

            LaunchedEffect(showHome) {
                val user = FirebaseAuth.getInstance().currentUser

                if (showHome && user != null) {

                    if (Build.VERSION.SDK_INT >= 33 &&
                        checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED
                    ) {
                        requestPermissions(
                            arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                            1001
                        )
                    }

                    val userDoc = FirebaseFirestore.getInstance()
                        .collection("users")
                        .document(user.uid)

                    val profile = hashMapOf(
                        "uid" to user.uid,
                        "email" to (user.email ?: ""),
                        "timestamp" to FieldValue.serverTimestamp()
                    )

                    userDoc.set(profile, SetOptions.merge())
                        .addOnSuccessListener {
                            FirebaseMessaging.getInstance().token
                                .addOnSuccessListener { token ->
                                    userDoc.set(
                                        mapOf("fcmToken" to token),
                                        SetOptions.merge()
                                    )
                                }
                        }
                }
            }

            LaunchedEffect(notificationUid.value, showHome) {
                val targetUid = notificationUid.value
                val user = FirebaseAuth.getInstance().currentUser

                if (!targetUid.isNullOrBlank() &&
                    showHome &&
                    user != null
                ) {
                    FirebaseFirestore.getInstance()
                        .collection("users")
                        .document(targetUid)
                        .get()
                        .addOnSuccessListener { document ->
                            val phone = document.getString("phoneNumber") ?: ""
                            val username = document.getString("name") ?: ""
                            val email = document.getString("email") ?: ""

                            selectedContact = MatchedContact(
                                name = username.ifBlank {
                                    phone.ifBlank {
                                        email.ifBlank { "Pengguna M" }
                                    }
                                },
                                number = phone,
                                uid = targetUid
                            )

                            showContacts = false
                            showProfile = false
                            notificationUid.value = null
                        }
                        .addOnFailureListener { error ->
                            Toast.makeText(
                                this@MainActivity,
                                "Gagal membuka percakapan: ${error.localizedMessage}",
                                Toast.LENGTH_LONG
                            ).show()

                            notificationUid.value = null
                        }
                }
            }

            BackHandler(
                enabled = selectedContact != null ||
                    showContacts || showProfile
            ) {
                when {
                    selectedContact != null -> {
                        selectedContact = null
                    }

                    showContacts -> {
                        showContacts = false
                    }

                    showProfile -> {
                        showProfile = false
                    }
                }
            }

            MaterialTheme {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(
                            showHome,
                            selectedContact,
                            showContacts,
                            showProfile
                        ) {
                            var totalDrag = 0f

                            detectHorizontalDragGestures(
                                onHorizontalDrag = { change, dragAmount ->
                                    totalDrag += dragAmount
                                    change.consume()
                                },
                                onDragEnd = {
                                    if (showHome && selectedContact == null) {
                                        if (totalDrag < -80f) {
                                            when {
                                                !showContacts && !showProfile -> {
                                                    showContacts = true
                                                    showProfile = false
                                                }

                                                showContacts -> {
                                                    showContacts = false
                                                    showProfile = true
                                                }
                                            }
                                        } else if (totalDrag > 80f) {
                                            when {
                                                showProfile -> {
                                                    showProfile = false
                                                    showContacts = true
                                                }

                                                showContacts -> {
                                                    showContacts = false
                                                    showProfile = false
                                                }
                                            }
                                        }
                                    }

                                    totalDrag = 0f
                                },
                                onDragCancel = {
                                    totalDrag = 0f
                                }
                            )
                        }
                ) {
                    when {
                        !showHome -> {
                            AuthScreen(
                                onContinueAsGuest = {
                                    showHome = true
                                }
                            )
                        }

                        selectedContact != null -> {
                            ChatScreen(
                                contact = selectedContact!!,
                                onBack = {
                                    selectedContact = null
                                }
                            )
                        }

                        showContacts -> {
                            ContactsScreen(
                                onBack = {
                                    showContacts = false
                                },
                                onContactClick = { contact ->
                                    selectedContact = contact
                                }
                            )
                        }

                        showProfile -> {
                            ProfileScreen(
                                onBack = {
                                    showProfile = false
                                },
                                onLoggedOut = {
                                    showProfile = false
                                    showHome = false
                                }
                            )
                        }

                        else -> {
                            MainScreen(
                                onChatClick = { contact ->
                                    selectedContact = contact
                                },
                                onContactsClick = {
                                    showContacts = true
                                },
                                onProfileClick = {
                                    showProfile = true
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
