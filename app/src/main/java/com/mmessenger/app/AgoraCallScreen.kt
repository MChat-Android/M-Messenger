
package com.mmessenger.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.view.SurfaceView
import android.widget.FrameLayout
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.firebase.firestore.ListenerRegistration
import io.agora.rtc2.Constants
import io.agora.rtc2.IRtcEngineEventHandler
import io.agora.rtc2.RtcEngine
import io.agora.rtc2.video.VideoCanvas

@Composable
fun AgoraCallScreen(
    callId: String,
    channelName: String,
    callType: String,
    onCallEnded: () -> Unit
) {
    val context = LocalContext.current
    val isVideo = callType == "video"
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    var engine by remember { mutableStateOf<RtcEngine?>(null) }
    var joined by remember { mutableStateOf(false) }
    var remoteJoined by remember { mutableStateOf(false) }
    var muted by remember { mutableStateOf(false) }
    var speaker by remember { mutableStateOf(true) }
    var cameraEnabled by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf("") }
    var ending by remember { mutableStateOf(false) }

    var localContainer by remember {
        mutableStateOf<FrameLayout?>(null)
    }

    var remoteContainer by remember {
        mutableStateOf<FrameLayout?>(null)
    }

    val requiredPermissions = remember(isVideo) {
        if (isVideo) {
            arrayOf(
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.CAMERA
            )
        } else {
            arrayOf(Manifest.permission.RECORD_AUDIO)
        }
    }

    fun permissionsGranted(): Boolean {
        return requiredPermissions.all {
            ContextCompat.checkSelfPermission(
                context,
                it
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    var permissionReady by remember {
        mutableStateOf(permissionsGranted())
    }

    val permissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { result ->
            permissionReady =
                requiredPermissions.all { result[it] == true }

            if (!permissionReady) {
                errorMessage = "Izin mikrofon atau kamera belum diberikan."
            }
        }

    val callListener: ListenerRegistration? = remember(callId) {
        null
    }

    DisposableEffect(callId) {
        val listener = CallManager.listenCall(callId) { call ->
            if (call == null) return@listenCall

            if (
                call.status == "rejected" ||
                call.status == "ended"
            ) {
                mainHandler.post {
                    if (!ending) {
                        ending = true
                        onCallEnded()
                    }
                }
            }
        }

        onDispose {
            listener.remove()
        }
    }

    LaunchedEffect(Unit) {
        if (!permissionReady) {
            permissionLauncher.launch(requiredPermissions)
        }
    }

    LaunchedEffect(permissionReady) {
        if (!permissionReady || engine != null) return@LaunchedEffect

        AgoraTokenClient.requestToken(channelName) { result ->
            result.onSuccess { tokenData ->

                try {
                    val handler = object : IRtcEngineEventHandler() {

                        override fun onJoinChannelSuccess(
                            channel: String?,
                            uid: Int,
                            elapsed: Int
                        ) {
                            mainHandler.post {
                                joined = true
                            }
                        }

                        override fun onUserJoined(
                            uid: Int,
                            elapsed: Int
                        ) {
                            mainHandler.post {
                                remoteJoined = true

                                if (isVideo) {
                                    val container = remoteContainer

                                    if (container != null) {
                                        container.removeAllViews()

                                        val surface =
                                            SurfaceView(context)

                                        container.addView(surface)

                                        engine?.setupRemoteVideo(
                                            VideoCanvas(
                                                surface,
                                                Constants.RENDER_MODE_HIDDEN,
                                                uid
                                            )
                                        )
                                    }
                                }
                            }
                        }

                        override fun onUserOffline(
                            uid: Int,
                            reason: Int
                        ) {
                            mainHandler.post {
                                remoteJoined = false
                                remoteContainer?.removeAllViews()
                            }
                        }

                        override fun onError(err: Int) {
                            mainHandler.post {
                                errorMessage =
                                    "Agora mengalami kesalahan: $err"
                            }
                        }
                    }

                    val rtc = RtcEngine.create(
                        context,
                        tokenData.appId,
                        handler
                    )

                    engine = rtc

                    rtc.setChannelProfile(
                        Constants.CHANNEL_PROFILE_COMMUNICATION
                    )

                    rtc.enableAudio()
                    rtc.setEnableSpeakerphone(true)

                    if (isVideo) {
                        rtc.enableVideo()

                        localContainer?.let { container ->
                            container.removeAllViews()

                            val surface = SurfaceView(context)

                            container.addView(surface)

                            rtc.setupLocalVideo(
                                VideoCanvas(
                                    surface,
                                    Constants.RENDER_MODE_HIDDEN,
                                    0
                                )
                            )
                        }

                        rtc.startPreview()
                    }

                    val joinResult =
                        rtc.joinChannelWithUserAccount(
                            tokenData.token,
                            tokenData.channelName,
                            tokenData.userAccount
                        )

                    if (joinResult != 0) {
                        errorMessage =
                            "Gagal bergabung ke panggilan. Kode: $joinResult"
                    }

                } catch (e: Exception) {
                    errorMessage =
                        e.message ?: "Gagal memulai panggilan."
                }
            }

            result.onFailure {
                errorMessage =
                    it.message ?: "Gagal mengambil token panggilan."
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                engine?.stopPreview()
                engine?.leaveChannel()
                RtcEngine.destroy()
                engine = null
            } catch (_: Exception) {
            }
        }
    }

    fun endCurrentCall() {
        if (ending) return

        ending = true

        CallManager.endCall(callId) {
            mainHandler.post {
                onCallEnded()
            }
        }
    }

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
                style = MaterialTheme.typography.headlineSmall
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = when {
                    !errorMessage.isBlank() -> errorMessage
                    joined && remoteJoined -> "Terhubung"
                    joined -> "Menunggu lawan bicara..."
                    else -> "Menghubungkan..."
                },
                color = Color.LightGray
            )
        }

        if (isVideo) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AndroidView(
                    factory = { ctx ->
                        FrameLayout(ctx).also {
                            remoteContainer = it
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(Color.Black)
                )

                Spacer(modifier = Modifier.height(12.dp))

                AndroidView(
                    factory = { ctx ->
                        FrameLayout(ctx).also {
                            localContainer = it
                        }
                    },
                    modifier = Modifier
                        .width(120.dp)
                        .height(170.dp)
                        .background(Color.DarkGray)
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .size(180.dp)
                    .background(Color(0xFF263746), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "M",
                    color = Color.White,
                    style = MaterialTheme.typography.displayLarge
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalButton(
                    onClick = {
                        muted = !muted
                        engine?.muteLocalAudioStream(muted)
                    }
                ) {
                    Text(if (muted) "Mic Mati" else "Mikrofon")
                }

                if (isVideo) {
                    FilledTonalButton(
                        onClick = {
                            cameraEnabled = !cameraEnabled
                            engine?.muteLocalVideoStream(!cameraEnabled)
                        }
                    ) {
                        Text(if (cameraEnabled) "Kamera" else "Kamera Mati")
                    }
                }

                FilledTonalButton(
                    onClick = {
                        speaker = !speaker
                        engine?.setEnableSpeakerphone(speaker)
                    }
                ) {
                    Text(if (speaker) "Speaker" else "Earpiece")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = { endCurrentCall() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Red
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(55.dp)
            ) {
                Text("AKHIRI PANGGILAN")
            }
        }
    }
}
