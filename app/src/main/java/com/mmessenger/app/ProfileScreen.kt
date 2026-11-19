
package com.mmessenger.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.io.ByteArrayOutputStream

@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    onLoggedOut: () -> Unit
) {
    val auth = FirebaseAuth.getInstance()
    val db = FirebaseFirestore.getInstance()
    val user = auth.currentUser
    val context = LocalContext.current

    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf(user?.email ?: "") }
    var editing by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var savingPhoto by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var photoBase64 by remember { mutableStateOf<String?>(null) }
    var photoBitmap by remember { mutableStateOf<Bitmap?>(null) }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val original = context.contentResolver
                    .openInputStream(uri)
                    ?.use { BitmapFactory.decodeStream(it) }

                if (original != null) {
                    val ratio = minOf(
                        512f / original.width,
                        512f / original.height,
                        1f
                    )

                    val resized = Bitmap.createScaledBitmap(
                        original,
                        (original.width * ratio).toInt().coerceAtLeast(1),
                        (original.height * ratio).toInt().coerceAtLeast(1),
                        true
                    )

                    val output = ByteArrayOutputStream()
                    resized.compress(
                        Bitmap.CompressFormat.JPEG,
                        65,
                        output
                    )

                    photoBase64 = Base64.encodeToString(
                        output.toByteArray(),
                        Base64.NO_WRAP
                    )
                    photoBitmap = resized
                    message = "Foto siap disimpan"
                } else {
                    message = "Foto tidak dapat dibaca"
                }
            } catch (e: Exception) {
                message = "Gagal memproses foto"
            }
        }
    }

    LaunchedEffect(user?.uid) {
        if (user != null) {
            db.collection("users")
                .document(user.uid)
                .get()
                .addOnSuccessListener { doc ->
                    name = doc.getString("name") ?: ""
                    phone = doc.getString("phoneNumber") ?: ""
                    email = doc.getString("email") ?: email

                    photoBase64 = doc.getString("photoBase64")

                    try {
                        photoBase64?.let {
                            val bytes = Base64.decode(it, Base64.DEFAULT)
                            photoBitmap = BitmapFactory.decodeByteArray(
                                bytes, 0, bytes.size
                            )
                        }
                    } catch (_: Exception) {
                        photoBitmap = null
                    }

                    loading = false
                }
                .addOnFailureListener {
                    loading = false
                    message = "Gagal mengambil data profil"
                }
        } else {
            loading = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF7F9FE))
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) {
                Text("← Kembali")
            }

            Text(
                "Profil Saya",
                fontSize = 23.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.height(25.dp))

        Box(
            modifier = Modifier
                .size(120.dp)
                .background(Color(0xFFEAF1FF), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (photoBitmap != null) {
                Image(
                    bitmap = photoBitmap!!.asImageBitmap(),
                    contentDescription = "Foto profil",
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Text(
                    name.firstOrNull()?.uppercase() ?: "M",
                    fontSize = 42.sp,
                    color = Color(0xFF246BFD),
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        if (user != null) {
            OutlinedButton(
                onClick = {
                    imagePicker.launch("image/*")
                },
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text("Pilih Foto dari Galeri")
            }

            if (photoBase64 != null) {
                Button(
                    onClick = {
                        savingPhoto = true

                        db.collection("users")
                            .document(user.uid)
                            .update("photoBase64", photoBase64)
                            .addOnSuccessListener {
                                savingPhoto = false
                                message = "Foto profil berhasil disimpan"
                            }
                            .addOnFailureListener {
                                savingPhoto = false
                                message = "Gagal menyimpan foto"
                            }
                    },
                    enabled = !savingPhoto,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    Text(
                        if (savingPhoto) "Menyimpan..."
                        else "Simpan Foto Profil"
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        if (user == null) {
            Text(
                "Mode Tamu",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Text("Anda belum masuk menggunakan akun M.")
        } else if (loading) {
            CircularProgressIndicator()
        } else {
            Text(
                "Informasi Akun",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(15.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nama pengguna") },
                enabled = editing,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(10.dp))

            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it },
                label = { Text("Nomor telepon") },
                enabled = editing,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(10.dp))

            OutlinedTextField(
                value = email,
                onValueChange = {},
                label = { Text("Email akun") },
                enabled = false,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(20.dp))

            Button(
                onClick = {
                    if (editing) {
                        if (name.isBlank() || phone.isBlank()) {
                            message = "Nama dan nomor telepon wajib diisi"
                        } else {
                            loading = true

                            db.collection("users")
                                .document(user.uid)
                                .update(
                                    mapOf(
                                        "name" to name.trim(),
                                        "phoneNumber" to phone.trim()
                                    )
                                )
                                .addOnSuccessListener {
                                    loading = false
                                    editing = false
                                    message = "Profil berhasil disimpan"
                                }
                                .addOnFailureListener {
                                    loading = false
                                    message = "Gagal menyimpan profil"
                                }
                        }
                    } else {
                        editing = true
                        message = ""
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF246BFD)
                )
            ) {
                Text(if (editing) "Simpan Profil" else "Edit Profil")
            }
        }

        if (message.isNotBlank()) {
            Text(
                message,
                color = Color(0xFF246BFD),
                modifier = Modifier.padding(10.dp)
            )
        }

        Spacer(Modifier.weight(1f))

        if (user != null) {
            OutlinedButton(
                onClick = {
                    auth.signOut()
                    onLoggedOut()
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color.Red
                )
            ) {
                Text("Keluar dari Akun")
            }
        }
    }
}
