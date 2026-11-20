
package com.mmessenger.app

import android.Manifest
import android.content.pm.PackageManager
import android.view.SurfaceView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration

@Composable
fun CallScreen(
    callId: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    var call by remember { mutableStateOf<MCall?>(null) }
    var callStatus by remember { mutableStateOf("Memuat panggilan...") }
    var loading by remember { mutableStateOf(false) }
    var joined by remember { mutableStateOf(false) }
    var muted by remember { mutableStateOf(false) }
    var cameraEnabled by remember { mutableStateOf(true) }
    var speakerEnabled by remember { mutableStateOf(true) }
    var remoteUid by remember { mutableIntStateOf(-1) }
    var pendingConnect by remember { mutableStateOf(false) }

    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid

    DisposableEffect(callId) {
        val registration: ListenerRegistration =
            CallManager.listenCall(callId) { result ->
                call = result

                callStatus = when (result?.status) {
                    "ringing" -> "Memanggil..."
                    "accepted" -> "Panggilan diterima"
                    "rejected" -> "Panggilan ditolak"
                    "ended" -> "Panggilan berakhir"
                    else -> "Menunggu..."
                }
            }

        onDispose {
            registration.remove()
        }
    }

    val permissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->

            val granted = permissions.values.all { it }

            if (granted) {
                pendingConnect = true
            } else {
                callStatus = "Izin mikrofon atau kamera ditolak."
            }
        }

    LaunchedEffect(pendingConnect, call?.channelName) {

        if (!pendingConnect) return@LaunchedEffect

        val channel = call?.channelName ?: return@LaunchedEffect

        pendingConnect = false
        loading = true
        callStatus = "Mengambil token Agora..."

        CallManager.requestAgoraToken(channel) { result ->

            result.fold(
                onSuccess = { credentials ->

                    AgoraManager.initialize(
                        context,
                        credentials.appId
                    ) { initResult ->

                        initResult.fold(
                            onSuccess = {

                                AgoraManager.setRemoteCallbacks(
                                    onJoined = { uid ->
                                        remoteUid = uid
                                        callStatus = "Terhubung"
                                    },
                                    onOffline = {
                                        remoteUid = -1
                                        callStatus = "Lawan bicara terputus"
                                    },
                                    onAgoraError = { error ->
                                        callStatus = "Agora error: $error"
                                    }
                                )

                                AgoraManager.joinCall(
                                    credentials,
                                    call?.callType == "video"
                                ) { joinResult ->

                                    loading = false

                                    joinResult.fold(
                                        onSuccess = {
                                            joined = true
                                            callStatus = "Menghubungkan..."
                                        },
                                        onFailure = {
                                            callStatus =
                                                it.message ?: "Gagal bergabung."
                                        }
                                    )
                                }
                            },
                            onFailure = {
                                loading = false
                                callStatus =
                                    it.message ?: "Agora gagal diinisialisasi."
                            }
                        )
                    }
                },
                onFailure = {
                    loading = false
                    callStatus =
                        it.message ?: "Gagal mengambil token."
                }
            )
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            if (joined) {
                AgoraManager.leaveCall()
            }
        }
    }

    val isVideo = call?.callType == "video"
    val isReceiver = currentUserId == call?.receiverId

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF101820))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {

        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (isVideo) "Panggilan Video" else "Panggilan Suara",
                color = Color.White,
                fontSize = 24.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = callStatus,
                color = Color.LightGray,
                fontSize = 16.sp
            )
        }

        if (isVideo && joined) {

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                AndroidView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    factory = {
                        SurfaceView(it).also { view ->
                            AgoraManager.setupLocalVideo(view)
                        }
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))

                if (remoteUid != -1) {
                    AndroidView(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        factory = {
                            SurfaceView(it).also { view ->
                                AgoraManager.setupRemoteVideo(
                                    remoteUid,
                                    view
                                )
                            }
                        }
                    )
                } else {
                    Text(
                        "Menunggu video lawan bicara...",
                        color = Color.White
                    )
                }
            }

        } else {
            Text(
                text = "M",
                color = Color.White,
                fontSize = 90.sp
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            if (
                call?.status == "ringing" &&
                isReceiver
            ) {
                Button(
                    onClick = {
                        loading = true

                        CallManager.acceptCall(callId) { result ->
                            loading = false

                            result.onFailure {
                                callStatus =
                                    it.message ?: "Gagal menerima panggilan."
                            }
                        }
                    },
                    enabled = !loading
                ) {
                    Text("Terima Panggilan")
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        CallManager.rejectCall(callId) {
                            onBack()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Red
                    )
                ) {
                    Text("Tolak")
                }
            }

            if (
                call?.status == "accepted" &&
                !joined
            ) {
                Button(
                    onClick = {
                        val permissions = mutableListOf(
                            Manifest.permission.RECORD_AUDIO
                        )

                        if (isVideo) {
                            permissions.add(Manifest.permission.CAMERA)
                        }

                        val missing = permissions.filter {
                            ContextCompat.checkSelfPermission(
                                context,
                                it
                            ) != PackageManager.PERMISSION_GRANTED
                        }

                        if (missing.isEmpty()) {
                            pendingConnect = true
                        } else {
                            permissionLauncher.launch(
                                missing.toTypedArray()
                            )
                        }
                    },
                    enabled = !loading
                ) {
                    Text(
                        if (loading) "Menghubungkan..." else "Hubungkan"
                    )
                }
            }

            if (joined) {

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    Button(
                        onClick = {
                            muted = !muted
                            AgoraManager.muteMicrophone(muted)
                        },
                        shape = CircleShape
                    ) {
                        Text(if (muted) "Mic OFF" else "Mic ON")
                    }

                    if (isVideo) {
                        Button(
                            onClick = {
                                cameraEnabled = !cameraEnabled
                                AgoraManager.enableCamera(cameraEnabled)
                            },
                            shape = CircleShape
                        ) {
                            Text(
                                if (cameraEnabled) "Cam ON" else "Cam OFF"
                            )
                        }
                    }

                    Button(
                        onClick = {
                            speakerEnabled = !speakerEnabled
                            AgoraManager.setSpeakerphone(speakerEnabled)
                        },
                        shape = CircleShape
                    ) {
                        Text("Speaker")
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = {
                    AgoraManager.leaveCall()

                    CallManager.endCall(callId) {
                        onBack()
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Red
                )
            ) {
                Text("Akhiri Panggilan")
            }

            TextButton(onClick = onBack) {
                Text("Kembali", color = Color.White)
            }
        }
    }
}
