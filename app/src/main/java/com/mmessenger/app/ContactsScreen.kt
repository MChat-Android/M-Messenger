package com.mmessenger.app

import android.Manifest
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PhoneContact(
    val name: String,
    val number: String
)

@Composable
fun ContactsScreen(onBack: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var contacts by remember { mutableStateOf(listOf<PhoneContact>()) }
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_CONTACTS
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    var loading by remember { mutableStateOf(false) }

    val permissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            hasPermission = granted
        }

    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            loading = true

            scope.launch {
                contacts = withContext(Dispatchers.IO) {
                    val result = mutableListOf<PhoneContact>()

                    val cursor = context.contentResolver.query(
                        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                        arrayOf(
                            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                            ContactsContract.CommonDataKinds.Phone.NUMBER
                        ),
                        null,
                        null,
                        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
                    )

                    cursor?.use {
                        val nameIndex = it.getColumnIndex(
                            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
                        )
                        val numberIndex = it.getColumnIndex(
                            ContactsContract.CommonDataKinds.Phone.NUMBER
                        )

                        while (it.moveToNext()) {
                            val name = it.getString(nameIndex) ?: ""
                            val number = it.getString(numberIndex) ?: ""

                            if (name.isNotBlank() && number.isNotBlank()) {
                                result.add(PhoneContact(name, number))
                            }
                        }
                    }

                    result.distinctBy { it.number }
                }

                loading = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        TextButton(onClick = onBack) {
            Text("← Kembali")
        }

        Text(
            "Kontak",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(Modifier.height(16.dp))

        when {
            !hasPermission -> {
                Text("Izinkan M Messenger mengakses kontak HP untuk menemukan teman.")

                Button(
                    onClick = {
                        permissionLauncher.launch(
                            Manifest.permission.READ_CONTACTS
                        )
                    }
                ) {
                    Text("Izinkan akses kontak")
                }
            }

            loading -> {
                CircularProgressIndicator()
            }

            contacts.isEmpty() -> {
                Text("Belum ada kontak yang ditemukan.")
            }

            else -> {
                Text("${contacts.size} nomor kontak ditemukan")

                LazyColumn {
                    items(contacts) { contact ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp)
                        ) {
                            Text(
                                contact.name,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                contact.number,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
