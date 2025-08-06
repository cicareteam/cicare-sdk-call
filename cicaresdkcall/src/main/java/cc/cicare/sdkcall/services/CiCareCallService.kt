package cc.cicare.sdkcall.services

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import cc.cicare.sdkcall.event.CallStateListener
import cc.cicare.sdkcall.event.CallState
import cc.cicare.sdkcall.libs.ApiClient
import cc.cicare.sdkcall.libs.CallRequest
import cc.cicare.sdkcall.notifications.CallNotificationManager
import cc.cicare.sdkcall.notifications.ui.ScreenCallActivity
import cc.cicare.sdkcall.rtc.WebRTCEventCallback
import cc.cicare.sdkcall.rtc.WebRTCManager
import cc.cicare.sdkcall.signaling.SignalingHelper
import cc.cicare.sdkcall.signaling.SocketManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.webrtc.IceCandidate
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.SessionDescription

interface TimeTickerListener {
    fun onTimeTicketUpdate(seconds: Long)
}

class CiCareCallService: Service(), CallStateListener, WebRTCEventCallback {

    private lateinit var webRTCManager: WebRTCManager
    private lateinit var signaling: SignalingHelper
    private lateinit var eventListener: CallStateListener
    private lateinit var tickerListener: TimeTickerListener

    private var outgoingIntent: Intent? = null

    private var isFromPhone = false
    private val serviceScope = CoroutineScope(Dispatchers.IO)

    private var timerJob: Job? = null

    var callState = MutableStateFlow<String>("initializing")

    private var metaData: Map<String, String> = hashMapOf(
        "initializing" to "Initializing...",
        "calling" to "Calling...",
        "incoming" to "Incoming Call",
        "ringing" to "Ringing",
        "connected" to "Connected",
        "ended" to "Ended",
        "answer" to "Answer",
        "decline" to "Decline",
        "mute" to "Mute",
        "unmute" to "Unmute",
        "speaker" to "Speaker",
    )


    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService(): CiCareCallService = this@CiCareCallService
    }

    object ACTION {
        const val HANGUP = "HANGUP"
        const val ACCEPT = "ACCEPT"
        const val REJECT = "REJECT"
        const val INCOMING = "INCOMING"
        const val ONGOING = "ONGOING"
        const val OUTGOING = "OUTGOING"
        const val MISSED_CALL = "MISSED_CALL"
    }

    companion object {
        //val CALL_CHANNEL_ID = "call_channel_id"
        val INCOMING_CALL_ICON = android.R.drawable.sym_call_incoming
        val ONGOING_CALL_ICON = android.R.drawable.sym_action_call
        val MISSED_CALL_ICON = android.R.drawable.sym_call_missed
        var OUTGOING_CALL_ICON = android.R.drawable.sym_call_outgoing
        var ringtoneUrl: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION) }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onCreate() {
        webRTCManager = WebRTCManager(this, this)
        signaling = SignalingHelper(webRTCManager)
        SocketManager.setCallStateListener(this)
    }

    private fun requestAudioFocus() {
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setOnAudioFocusChangeListener { /* optional */ }
            .build()
        audioManager.requestAudioFocus(focusRequest)
        Log.i("FCM", "Audio focus")
    }

    fun startCallTimer() {
        timerJob?.cancel()
        timerJob = CoroutineScope(Dispatchers.Default).launch {
            var seconds = 0L
            while (isActive) {
                delay(1000)
                seconds++
                tickerListener.onTimeTicketUpdate(seconds)
            }
        }
    }

    fun stopTimer() {
        timerJob?.cancel()
    }

    fun getCallStateFlow(): StateFlow<String> = callState

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        metaData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val extra = intent?.getSerializableExtra("meta_data", HashMap::class.java) as? HashMap<String, String>
            if (extra != null) HashMap(metaData + extra) else metaData
        } else {
            val extra = intent?.getSerializableExtra("meta_data") as? HashMap<String, String>
            if (extra != null) HashMap(metaData + extra) else metaData
        }

        when(intent?.action) {
            ACTION.INCOMING -> {
                callState.value = "incoming"
                Log.i("FCM", "Service incoming")
            }
            ACTION.ONGOING -> onOngoingCall(intent)
            ACTION.ACCEPT -> serviceScope.launch { answerCall(intent) }
            ACTION.OUTGOING -> serviceScope.launch { onOutgoingCall(intent) }
            ACTION.REJECT -> reject()
            ACTION.HANGUP -> hangup()
            "SCREEN" -> onScreen(intent)
        }
        return START_STICKY
    }

    private fun onScreen(intent: Intent) {
        startActivity(Intent(this, ScreenCallActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtras(intent)
        })
    }

    fun reject() {
        SocketManager.send("REJECT", JSONObject().apply {})
        if (::eventListener.isInitialized)
            eventListener.onCallStateChanged(CallState.ENDED)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    fun hangup() {
        SocketManager.send("REQUEST_HANGUP", JSONObject().apply {})
        if (::eventListener.isInitialized)
            eventListener.onCallStateChanged(CallState.ENDED)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    fun setMute(isMuted: Boolean) {
        webRTCManager.setMicEnabled(!isMuted)
    }

    fun setSpeaker(isSpeakerOn: Boolean) {
        webRTCManager.setAudioOutputToSpeaker(isSpeakerOn)
    }

    private suspend fun initCall(server: String, token: String) {

        webRTCManager.init()
        webRTCManager.initMic()
        SocketManager.connect(server, token)
        SocketManager.send("INIT_CALL", JSONObject().apply {
            put("is_caller", true)
            put("sdp", JSONObject().apply {
                put("type", "offer")
                put("sdp", webRTCManager.createOffer().description)
            })
        })
    }

    suspend fun answerCall(intent: Intent, fromScreen: Boolean? = false) {
        val isForeground = ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        if (fromScreen != true && !isForeground) {
            startActivity(Intent(this, ScreenCallActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                action = "ANSWER"
                putExtras(intent)
            })
        }
        val sdp: SessionDescription?
        if (this.isFromPhone)
            sdp = webRTCManager.createAnswer()
        else {
            webRTCManager.init()
            webRTCManager.initMic()
            sdp = webRTCManager.createOffer()
        }
        onCallStateChanged(CallState.CONNECTED)
        onOngoingCall(intent)
        SocketManager.send("ANSWER_CALL", JSONObject().apply {
            put("is_caller", false)
            put("sdp", JSONObject().apply {
                put("type", sdp.type.toString())
                put("sdp", sdp.description)
            })
        })
        Log.i("FCM", "ANSWER SIGNAL SENT")
    }

    @SuppressLint("MissingPermission")
    private suspend fun onOutgoingCall(intent: Intent) {
        outgoingIntent = intent
        //val callType = intent.getStringExtra("call_type") ?: "outgoing"
        val calleeId = intent.getStringExtra("callee_id") ?: ""
        val callerId = intent.getStringExtra("caller_id") ?: ""
        val calleeName = intent.getStringExtra("callee_name") ?: "unknown"
        val callerName = intent.getStringExtra("caller_name") ?: "unknown"
        val calleeAvatar = intent.getStringExtra("callee_avatar") ?: ""
        val callerAvatar = intent.getStringExtra("caller_avatar") ?: ""
        val checksum = intent.getStringExtra("checksum") ?: ""
        CallNotificationManager.provideNotificationmanagerCompat(this, "CALL_OUTGOING_CHANNEL_ID", NotificationManager.IMPORTANCE_LOW)
        val notification = CallNotificationManager.outgoingCallNotificationBuilder(
            this,
            intent,
            "CALL_OUTGOING_CHANNEL_ID",
            metaData[callState.value] ?: callState.value,
            calleeName,
            calleeAvatar
        )

        startForeground(101, notification.build())
        startActivity(Intent(this, ScreenCallActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            putExtras(intent)
        })
        try {
            val response = ApiClient.api.requestCall(
                CallRequest(
                    callerId,
                    callerName,
                    callerAvatar,
                    calleeId,
                    calleeName,
                    calleeAvatar,
                    checksum
                )
            )
            if (response.isSuccessful) {
                val apiResponse = response.body()
                apiResponse?.let {
                    eventListener.onCallStateChanged(CallState.CALLING)
                    outgoingCallStateUpdate("calling")
                    initCall(it.server, it.token)
                }
            }
        } catch (e: Exception) {
            eventListener.onCallStateChanged(CallState.ENDED)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            throw Exception(e)
        }
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    private fun outgoingCallStateUpdate(callState: String) {
        val notificationManager = CallNotificationManager.provideNotificationmanagerCompat(this, "CALL_OUTGOING_CHANNEL_ID", NotificationManager.IMPORTANCE_LOW)
        this.callState.value = callState
        val calleeName = outgoingIntent?.getStringExtra("callee_name") ?: "unknown"
        val calleeAvatar = outgoingIntent?.getStringExtra("callee_avatar") ?: ""
        val notification = CallNotificationManager.outgoingCallNotificationBuilder(
            this,
            outgoingIntent!!,
            "CALL_OUTGOING_CHANNEL_ID",
            metaData[callState] ?: callState,
            calleeName,
            calleeAvatar
        )

        notificationManager.notify(101, notification.build())
    }

    private fun onOngoingCall(intent: Intent) {
        requestAudioFocus()
        val callType = intent.getStringExtra("call_type") ?: "outgoing"
        val callerName =
            if (callType == "incoming") intent.getStringExtra("caller_name") else intent.getStringExtra("callee_name")
        val callerAvatar =
            if (callType == "incoming") intent.getStringExtra("caller_avatar") else intent.getStringExtra("callee_avatar")
        CallNotificationManager.provideNotificationmanagerCompat(this, "CALL_ONGOING_CHANNEL_ID", NotificationManager.IMPORTANCE_LOW)
        val notification = CallNotificationManager.ongoingCallNotificationBuilder(
            this,
            intent,
            "CALL_ONGOING_CHANNEL_ID",
            callerName ?: "unknown",
            callerAvatar ?: ""
        )
        eventListener.onCallStateChanged(CallState.CONNECTED)
        startForeground(101, notification.build())
    }

    fun setCallEventListener(eventListener: CallStateListener) {
        this.eventListener = eventListener
    }

    fun setTickerListener(eventListener: TimeTickerListener) {
        this.tickerListener = eventListener
    }

    override fun onDestroy() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        if (::webRTCManager.isInitialized)
            webRTCManager.close()
        SocketManager.disconnect()
        super.onDestroy()
    }

    /**
     * Callback when local SDP offer/answer has been created.
     *
     * @param sdp The session description created.
     */
    override fun onLocalSdpCreated(sdp: SessionDescription) {
        SocketManager.send("SDP_OFFER", JSONObject().apply {
            put("type", sdp.type)
            put("sdp", sdp.description)
        })
    }

    /**
     * Callback when an ICE candidate is generated.
     *
     * @param candidate The ICE candidate to be sent to remote peer.
     */
    override fun onIceCandidateGenerated(candidate: IceCandidate) {
        SocketManager.send("ICE_CANDIDATE", JSONObject().apply {
            put("sdpMid", candidate.sdpMid)
            put("sdpMLineIndex", candidate.sdpMLineIndex)
            put("candidate", candidate.sdp)
        })
    }

    /**
     * Callback when a remote media stream is received.
     *
     * @param stream The received remote media stream.
     */
    override fun onRemoteStreamReceived(stream: MediaStream) {
        if (stream.audioTracks.isNotEmpty()) {
            val remoteAudioTrack = stream.audioTracks[0]
            remoteAudioTrack.setEnabled(true)
        }
    }

    /**
     * Callback when the connection state changes.
     *
     * @param state The new connection state.
     */
    override fun onConnectionStateChanged(state: PeerConnection.PeerConnectionState) {
        if (::eventListener.isInitialized)
            eventListener.onConnectionStateChanged(state)
    }

    @SuppressLint("MissingPermission")
    override fun onCallStateChanged(callState: CallState) {
        if (::eventListener.isInitialized)
            eventListener.onCallStateChanged(callState)

        this@CiCareCallService.callState.value = callState.toString().lowercase()
        if (callState == CallState.CALLING || callState == CallState.RINGING) {
            outgoingCallStateUpdate(this@CiCareCallService.callState.value)
        }
        if (callState == CallState.CONNECTED) {
            startCallTimer()
        }
        if (callState == CallState.ENDED) {
            stopTimer()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    override fun onIceConnectionStateChanged(state: PeerConnection.IceConnectionState) {
        Log.i("ICE_STATE", state.toString())
    }


}