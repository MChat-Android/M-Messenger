
package com.mmessenger.app

import android.content.Context
import android.util.Log
import io.agora.rtc2.ChannelMediaOptions
import io.agora.rtc2.Constants
import io.agora.rtc2.IRtcEngineEventHandler
import io.agora.rtc2.RtcEngine
import io.agora.rtc2.RtcEngineConfig
import io.agora.rtc2.video.VideoCanvas

object AgoraManager {

    private const val TAG = "MAgoraManager"

    private var rtcEngine: RtcEngine? = null
    private var currentChannel: String? = null

    private var onRemoteUserJoined: ((Int) -> Unit)? = null
    private var onRemoteUserOffline: ((Int) -> Unit)? = null
    private var onError: ((Int) -> Unit)? = null

    private val eventHandler = object : IRtcEngineEventHandler() {

        override fun onJoinChannelSuccess(
            channel: String?,
            uid: Int,
            elapsed: Int
        ) {
            Log.d(TAG, "Berhasil bergabung: $channel")
        }

        override fun onUserJoined(
            uid: Int,
            elapsed: Int
        ) {
            Log.d(TAG, "Pengguna bergabung: $uid")
            onRemoteUserJoined?.invoke(uid)
        }

        override fun onUserOffline(
            uid: Int,
            reason: Int
        ) {
            Log.d(TAG, "Pengguna keluar: $uid")
            onRemoteUserOffline?.invoke(uid)
        }

        override fun onError(err: Int) {
            Log.e(TAG, "Agora error: $err")
            onError?.invoke(err)
        }
    }

    fun initialize(
        context: Context,
        appId: String,
        callback: (Result<Unit>) -> Unit
    ) {
        try {
            if (appId.isBlank()) {
                callback(
                    Result.failure(
                        Exception("Agora App ID kosong.")
                    )
                )
                return
            }

            if (rtcEngine != null) {
                callback(Result.success(Unit))
                return
            }

            val config = RtcEngineConfig().apply {
                mContext = context.applicationContext
                mAppId = appId
                mEventHandler = eventHandler
            }

            rtcEngine = RtcEngine.create(config)

            callback(Result.success(Unit))

        } catch (error: Exception) {
            Log.e(TAG, "Gagal menginisialisasi Agora.", error)
            callback(Result.failure(error))
        }
    }

    fun joinCall(
        credentials: AgoraCredentials,
        isVideoCall: Boolean,
        callback: (Result<Unit>) -> Unit
    ) {
        try {
            val engine = rtcEngine
                ?: throw Exception("Agora belum diinisialisasi.")

            if (credentials.token.isBlank()) {
                throw Exception("Token Agora kosong.")
            }

            if (credentials.channelName.isBlank()) {
                throw Exception("Channel Agora kosong.")
            }

            if (credentials.userAccount.isBlank()) {
                throw Exception("User Account kosong.")
            }

            if (isVideoCall) {
                engine.enableVideo()
                engine.startPreview()
            } else {
                engine.disableVideo()
            }

            engine.enableAudio()

            val options = ChannelMediaOptions().apply {
                channelProfile =
                    Constants.CHANNEL_PROFILE_COMMUNICATION

                clientRoleType =
                    Constants.CLIENT_ROLE_BROADCASTER

                publishMicrophoneTrack = true
                publishCameraTrack = isVideoCall

                autoSubscribeAudio = true
                autoSubscribeVideo = isVideoCall
            }

            val result = engine.joinChannelWithUserAccount(
                credentials.token,
                credentials.channelName,
                credentials.userAccount,
                options
            )

            if (result != 0) {
                throw Exception(
                    "Gagal bergabung ke channel. Kode: $result"
                )
            }

            currentChannel = credentials.channelName

            callback(Result.success(Unit))

        } catch (error: Exception) {
            Log.e(TAG, "Gagal memulai panggilan.", error)
            callback(Result.failure(error))
        }
    }

    fun setupLocalVideo(view: android.view.SurfaceView) {
        rtcEngine?.setupLocalVideo(
            VideoCanvas(
                view,
                Constants.RENDER_MODE_HIDDEN,
                0
            )
        )
    }

    fun setupRemoteVideo(
        uid: Int,
        view: android.view.SurfaceView
    ) {
        rtcEngine?.setupRemoteVideo(
            VideoCanvas(
                view,
                Constants.RENDER_MODE_HIDDEN,
                uid
            )
        )
    }

    fun setRemoteCallbacks(
        onJoined: (Int) -> Unit,
        onOffline: (Int) -> Unit,
        onAgoraError: (Int) -> Unit
    ) {
        onRemoteUserJoined = onJoined
        onRemoteUserOffline = onOffline
        onError = onAgoraError
    }

    fun muteMicrophone(muted: Boolean) {
        rtcEngine?.muteLocalAudioStream(muted)
    }

    fun enableCamera(enabled: Boolean) {
        if (enabled) {
            rtcEngine?.enableVideo()
            rtcEngine?.startPreview()
        } else {
            rtcEngine?.stopPreview()
            rtcEngine?.disableVideo()
        }
    }

    fun switchCamera() {
        rtcEngine?.switchCamera()
    }

    fun setSpeakerphone(enabled: Boolean) {
        rtcEngine?.setEnableSpeakerphone(enabled)
    }

    fun leaveCall() {
        try {
            rtcEngine?.stopPreview()
            rtcEngine?.leaveChannel()
            currentChannel = null
        } catch (error: Exception) {
            Log.e(TAG, "Gagal keluar dari channel.", error)
        }
    }

    fun release() {
        try {
            leaveCall()

            rtcEngine?.let {
                RtcEngine.destroy()
            }

            rtcEngine = null

            onRemoteUserJoined = null
            onRemoteUserOffline = null
            onError = null

        } catch (error: Exception) {
            Log.e(TAG, "Gagal melepaskan Agora.", error)
        }
    }
}
