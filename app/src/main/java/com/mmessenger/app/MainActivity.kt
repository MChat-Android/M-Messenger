package com.mmessenger.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.google.firebase.auth.FirebaseAuth

class MainActivity : ComponentActivity() {

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

            var selectedContact by remember {
                mutableStateOf<MatchedContact?>(null)
            }

            BackHandler(
                enabled = selectedContact != null || showContacts
            ) {
                if (selectedContact != null) {
                    selectedContact = null
                } else if (showContacts) {
                    showContacts = false
                }
            }

            MaterialTheme {
                Surface {
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

                        else -> {
                            MainScreen(
                                onContactsClick = {
                                    showContacts = true
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
