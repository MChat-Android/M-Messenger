
package com.mmessenger.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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

@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    onLoggedOut: () -> Unit
) {
    val auth = FirebaseAuth.getInstance()
    val db = FirebaseFirestore.getInstance()
    val user = auth.currentUser

    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf(user?.email ?: "") }
    var editing by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var message by remember { mutableStateOf("") }

    LaunchedEffect(user?.uid) {
        if (user != null) {
            db.collection("users")
                .document(user.uid)
                .get()
                .addOnSuccessListener { doc ->
                    name = doc.getString("name") ?: ""
                    phone = doc.getString("phoneNumber") ?: ""
                    email = doc.getString("email") ?: email
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

        Spacer(Modifier.height(30.dp))

        Box(
            modifier = Modifier
                .size(100.dp)
                .background(Color(0xFFEAF1FF), CircleShape)
                .align(Alignment.CenterHorizontally),
            contentAlignment = Alignment.Center
        ) {
            Text(
                name.firstOrNull()?.uppercase() ?: "M",
                fontSize = 42.sp,
                color = Color(0xFF246BFD),
                fontWeight = FontWeight.Bold
            )
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
                                .document(user!!.uid)
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
