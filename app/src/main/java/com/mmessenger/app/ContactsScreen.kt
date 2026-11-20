package com.mmessenger.app

import android.Manifest
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class PhoneContact(
    val name: String,
    val number: String
)

data class MatchedContact(
    val name: String,
    val number: String,
    val uid: String,
    val photoBase64: String? = null
)

private fun normalizeContactPhone(input: String): String? {
    var digits = input.filter { it.isDigit() }

    if (digits.startsWith("00")) {
        digits = digits.drop(2)
    }

    if (digits.startsWith("0")) {
        digits = "62" + digits.drop(1)
    } else if (digits.startsWith("8")) {
        digits = "62" + digits
    }

    if (!digits.startsWith("62")) return null
    if (digits.length !in 10..15) return null

    return "+$digits"
}

@Composable
fun ContactsScreen(
    onBack: () -> Unit = {},
    onContactClick: (MatchedContact) -> Unit = {}
) {
    val context = LocalContext.current
    val db = remember { FirebaseFirestore.getInstance() }
    val auth = remember { FirebaseAuth.getInstance() }

    var contacts by remember {
        mutableStateOf(listOf<MatchedContact>())
    }

    var loading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_CONTACTS
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            hasPermission = granted
            if (!granted) {
                errorMessage = "Izin kontak belum diberikan."
            }
        }

    // Otomatis meminta izin saat menu Kontak dibuka.
    LaunchedEffect(Unit) {
        if (!hasPermission) {
            permissionLauncher.launch(
                Manifest.permission.READ_CONTACTS
            )
        }
    }

    // Setelah izin diberikan, kontak langsung disinkronkan.
    LaunchedEffect(hasPermission) {
        if (!hasPermission) return@LaunchedEffect

        val currentUser = auth.currentUser

        if (currentUser == null) {
            errorMessage = "Silakan login untuk mencari teman M."
            return@LaunchedEffect
        }

        loading = true
        errorMessage = ""

        try {
            val phoneContacts = mutableListOf<PhoneContact>()

            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                ),
                null,
                null,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
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

                    if (name.isNotBlank() && number.isNotBlank()) {
                        phoneContacts.add(
                            PhoneContact(name, number)
                        )
                    }
                }
            }

            val normalizedContacts = phoneContacts.mapNotNull { contact ->
                val normalized = normalizeContactPhone(contact.number)

                if (normalized != null) {
                    Triple(contact.name, contact.number, normalized)
                } else {
                    null
                }
            }.distinctBy { it.third }

            val phoneNumbers = normalizedContacts.map { it.third }

            val matchedUsers =
                mutableMapOf<String, Pair<String, String?>>()

            phoneNumbers.chunked(10).forEach { batch ->

                val queryTasks = batch.map { phone ->
                    db.collection("users")
                        .whereEqualTo("phoneNumber", phone)
                        .get()
                }

                val results: List<QuerySnapshot> =
                    withContext(Dispatchers.IO) {
                        Tasks.await(
                            Tasks.whenAllSuccess<QuerySnapshot>(queryTasks)
                        )
                    }

                results.forEach { snapshot ->
                    snapshot.documents.forEach { document ->
                        val phone = document.getString("phoneNumber")
                        val uid = document.id
                        val photo = document.getString("photoBase64")

                        if (phone != null && uid != currentUser.uid) {
                            matchedUsers[phone] = uid to photo
                        }
                    }
                }
            }

            contacts = normalizedContacts.mapNotNull { contact ->
                val matchedUser = matchedUsers[contact.third]

                if (matchedUser != null) {
                    MatchedContact(
                        name = contact.first,
                        number = contact.second,
                        uid = matchedUser.first,
                        photoBase64 = matchedUser.second
                    )
                } else {
                    null
                }
            }

        } catch (e: Exception) {
            errorMessage =
                "Gagal mencari kontak: ${e.localizedMessage}"
        } finally {
            loading = false
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        item {
            TextButton(onClick = onBack) {
                Text("← Kembali")
            }
        }

        item {
            Text(
                "Temukan Teman M",
                style = MaterialTheme.typography.headlineMedium
            )

            Spacer(Modifier.height(16.dp))
        }

        when {
            !hasPermission -> {
                item {
                    Text(
                        "M Messenger memerlukan izin kontak " +
                            "untuk menemukan teman yang menggunakan M."
                    )

                    Spacer(Modifier.height(12.dp))

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
            }

            loading -> {
                item {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(8.dp))
                    Text("Menyinkronkan kontak...")
                }
            }

            errorMessage.isNotEmpty() -> {
                item {
                    Text(errorMessage)
                }
            }

            contacts.isEmpty() -> {
                item {
                    Text(
                        "Belum ada kontak yang menggunakan M Messenger."
                    )
                }
            }

            else -> {
                item {
                    Text(
                        "${contacts.size} teman menggunakan M Messenger"
                    )

                    Spacer(Modifier.height(12.dp))
                }

                items(contacts) { contact ->
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onContactClick(contact)
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment =
                                androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            ProfileAvatar(
                                photoBase64 = contact.photoBase64,
                                name = contact.name,
                                size = 52.dp
                            )

                            Spacer(Modifier.width(12.dp))

                            Column {
                                Text(
                                    contact.name,
                                    style = MaterialTheme.typography.titleMedium
                                )

                                Text(contact.number)
                            }
                        }

                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
