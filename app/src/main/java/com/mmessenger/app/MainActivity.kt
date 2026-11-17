
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

                        showContacts -> {
                            ContactsScreen(
                                onBack = {
                                    showContacts = false
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
