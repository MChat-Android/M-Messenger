
package com.mmessenger.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

private val AuthBlue = Color(0xFF246BFD)
private fun normalizePhoneNumber(input: String): String? {
    var digits = input.filter { it.isDigit() }

    if (digits.startsWith("0")) {
        digits = "62" + digits.drop(1)
    } else if (digits.startsWith("8")) {
        digits = "62" + digits
    }

    if (!digits.startsWith("62")) {
        return null
    }

    if (digits.length !in 10..15) {
        return null
    }

    return "+$digits"
}
@Composable
fun AuthScreen(onContinueAsGuest: () -> Unit) {
    var isRegister by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    val auth = remember { FirebaseAuth.getInstance() }
    val db = remember { FirebaseFirestore.getInstance() }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFFF7F9FE)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .background(AuthBlue, RoundedCornerShape(20.dp))
                    .padding(horizontal = 25.dp, vertical = 12.dp)
            ) {
                Text(
                    "M",
                    color = Color.White,
                    fontSize = 38.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(18.dp))

            Text(
                "M Messenger",
                fontSize = 27.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                if (isRegister) "Buat akun baru"
                else "Selamat datang kembali",
                color = Color.Gray
            )

            Spacer(Modifier.height(25.dp))

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                singleLine = true,
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email
                )
            )

            if (isRegister) {
                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Nomor telepon") },
                    singleLine = true,
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Phone
                    )
                )
            }

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Kata sandi") },
                singleLine = true,
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = PasswordVisualTransformation()
            )

            if (isRegister) {
                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    label = { Text("Ulangi kata sandi") },
                    singleLine = true,
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation()
                )
            }

            Spacer(Modifier.height(18.dp))

            Button(
                onClick = {
                    message = ""

                    if (email.isBlank() || !email.contains("@")) {
                        message = "Masukkan alamat email yang benar."
                        return@Button
                    }

                    if (password.length < 6) {
                        message = "Kata sandi minimal 6 karakter."
                        return@Button
                    }

                    if (isRegister) {
                        if (phone.isBlank()) {
                            message = "Nomor telepon wajib diisi."
                            return@Button
                        }

                        if (password != confirmPassword) {
                            message = "Konfirmasi kata sandi tidak cocok."
                            return@Button
                        }

                        isLoading = true

                        auth.createUserWithEmailAndPassword(
                            email.trim(),
                            password
                        ).addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                val user = auth.currentUser

                                if (user == null) {
                                    isLoading = false
                                    message = "Akun dibuat, tetapi sesi login tidak ditemukan."
                                    return@addOnCompleteListener
                                }

                                val profile = hashMapOf(
                                    "uid" to user.uid,
                                    "email" to email.trim(),
                                    "phoneNumber" to phone.trim(),
                                    "createdAt" to FieldValue.serverTimestamp()
                                )

                                db.collection("users")
                                    .document(user.uid)
                                    .set(profile)
                                    .addOnSuccessListener {
                                        isLoading = false
                                        onContinueAsGuest()
                                    }
                                    .addOnFailureListener { error ->
                                        isLoading = false
                                        message = "Akun berhasil dibuat, tetapi profil gagal disimpan: ${error.localizedMessage}"
                                    }
                            } else {
                                isLoading = false
                                message = task.exception?.localizedMessage
                                    ?: "Pendaftaran gagal."
                            }
                        }
                    } else {
                        isLoading = true

                        auth.signInWithEmailAndPassword(
                            email.trim(),
                            password
                        ).addOnCompleteListener { task ->
                            isLoading = false

                            if (task.isSuccessful) {
                                onContinueAsGuest()
                            } else {
                                message = task.exception?.localizedMessage
                                    ?: "Login gagal."
                            }
                        }
                    }
                },
                enabled = !isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AuthBlue
                )
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(if (isRegister) "Daftar" else "Masuk")
                }
            }

            if (message.isNotEmpty()) {
                Text(
                    message,
                    color = Color.Red,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 10.dp)
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (isRegister) "Sudah punya akun?"
                    else "Belum punya akun?"
                )

                TextButton(
                    enabled = !isLoading,
                    onClick = {
                        isRegister = !isRegister
                        message = ""
                    }
                ) {
                    Text(if (isRegister) "Masuk" else "Daftar")
                }
            }

            TextButton(
                enabled = !isLoading,
                onClick = onContinueAsGuest
            ) {
                Text("Lihat aplikasi tanpa akun")
            }
        }
    }
}
