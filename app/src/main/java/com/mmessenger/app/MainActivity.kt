package com.mmessenger.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            var showHome by remember {
                mutableStateOf(false)
            }

            var showContacts by remember {
                mutableStateOf(false)
            }

            var selectedContact by remember {
                mutableStateOf<MatchedContact?>(null)
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
