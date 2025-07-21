package cc.cicare.sdkcall.services

import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Binder
import android.os.IBinder
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import cc.cicare.sdkcall.event.CallEventListener
import cc.cicare.sdkcall.event.CallState
import cc.cicare.sdkcall.notifications.CallNotificationManager
import cc.cicare.sdkcall.notifications.ui.ScreenCallActivity
import cc.cicare.sdkcall.rtc.WebRTCEventCallback
import cc.cicare.sdkcall.rtc.WebRTCManager
import cc.cicare.sdkcall.signaling.SocketManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.webrtc.IceCandidate
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.SessionDescription
import javax.inject.Singleton

enum class CallAction(val action: String) {
    HANGUP("HANGUP"),
    ACCEPT("ACCEPT"),
    REJECT("REJECT"),
    INCOMING("INCOMING"),
    ONGOING("ONGOING"),
    MISSED_CALL("MISSED_CALL");
}

@Singleton
class CiCareCallService: Service(), CallEventListener, WebRTCEventCallback {

    private lateinit var webRTCManager: WebRTCManager
    private lateinit var socketManager: SocketManager
    private lateinit var eventListener: CallEventListener

    private var isFromPhone = false
    private val serviceScope = CoroutineScope(Dispatchers.IO)


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
        val CALL_CHANNEL_ID = "call_channel_id"
        val INCOMING_CALL_ICON = android.R.drawable.sym_call_incoming
        val ONGOING_CALL_ICON = android.R.drawable.sym_action_call
        val MISSED_CALL_ICON = android.R.drawable.sym_call_missed
        var OUTGOING_CALL_ICON = android.R.drawable.sym_call_outgoing
        var ringtoneUrl: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE) }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onCreate() {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setOnAudioFocusChangeListener { /* optional */ }
            .build()
        audioManager.requestAudioFocus(focusRequest)
        webRTCManager = WebRTCManager(this, this)
        socketManager = SocketManager(this, webRTCManager)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when(intent?.action) {
            ACTION.INCOMING -> onIncomingCall(intent)
            ACTION.ONGOING -> onOngoingCall(intent)
            ACTION.ACCEPT -> serviceScope.launch { answerCall(intent) }
            ACTION.OUTGOING -> serviceScope.launch { onOutgoingCall(intent) }
            ACTION.REJECT -> reject()
            ACTION.HANGUP -> hangup()
        }
        return START_STICKY
    }

    fun reject() {
        socketManager.send("REJECT", JSONObject().apply {})
        if (::eventListener.isInitialized)
            eventListener.onCallStateChanged(CallState.ENDED)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    fun hangup() {
        socketManager.send("REQUEST_HANGUP", JSONObject().apply {})
        if (::eventListener.isInitialized)
            eventListener.onCallStateChanged(CallState.ENDED)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun initReceive(server: String, token: String, isFromPhone: Boolean?) {
        socketManager.connect(server, token)
        socketManager.send("RINGING_CALL", JSONObject().apply {})

        this.isFromPhone = isFromPhone ?: false
    }

    private suspend fun initCall(token: String, server: String) {
        webRTCManager.init()
        webRTCManager.initMic()
        socketManager.connect(server, token)
        socketManager.send("INIT_CALL", JSONObject().apply {
            put("is_caller", true)
            put("sdp", JSONObject().apply {
                put("type", "offer")
                put("sdp", webRTCManager.createOffer().description)
            })
        })
    }

    suspend fun answerCall(intent: Intent, fromScreen: Boolean? = false) {
        if (fromScreen != true) {
            startActivity(Intent(this, ScreenCallActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
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
        socketManager.send("ANSWER_CALL", JSONObject().apply {
            put("is_caller", false)
            put("sdp", JSONObject().apply {
                put("type", sdp.type.toString())
                put("sdp", sdp.description)
            })
        })
    }

    private suspend fun onOutgoingCall(intent: Intent) {
        val calleeName = intent.getStringExtra("callee_name") ?: "unknown"
        val calleeAvatar = intent.getStringExtra("callee_avatar") ?: ""
        val token = intent.getStringExtra("token") ?: return
        val server = intent.getStringExtra("server") ?: return
        CallNotificationManager.provideNotificationmanagerCompat(this)
        val notification = CallNotificationManager.incomingCallNotificationBuilder(
            this,
            intent,
            calleeName,
            calleeAvatar
        )
        initCall(server, token)

        startForeground(101, notification.build())
        val isForeground = ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        if (isForeground) {
            startActivity(Intent(this, ScreenCallActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                putExtras(intent)
            })
        }
    }

    private fun onIncomingCall(intent: Intent) {
        val callerName = intent.getStringExtra("caller_name") ?: "unknown"
        val callerAvatar = intent.getStringExtra("caller_avatar") ?: ""
        val token = intent.getStringExtra("token") ?: return
        val server = intent.getStringExtra("server") ?: return
        isFromPhone = intent.getBooleanExtra("from_phone", false)
        CallNotificationManager.provideNotificationmanagerCompat(this)
        val notification = CallNotificationManager.incomingCallNotificationBuilder(
            this,
            intent,
            callerName,
            callerAvatar
        )
//        if (isFromPhone) {
//            webRTCManager.init()
//            webRTCManager.initMic()
//        }

        initReceive(server, token, isFromPhone)

        startForeground(101, notification.build())
        val isForeground = ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        if (isForeground) {
            startActivity(Intent(this, ScreenCallActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                putExtras(intent)
            })
        }
    }

    private fun onOngoingCall(intent: Intent) {
        val callerName = intent.getStringExtra("caller_name") ?: intent.getStringExtra("callee_name")
        val callerAvatar = intent.getStringExtra("caller_avatar") ?: intent.getStringExtra("callee_avatar")
        CallNotificationManager.provideNotificationmanagerCompat(this)
        val notification = CallNotificationManager.ongoingCallNotificationBuilder(
            this,
            intent,
            callerName ?: "unknown",
            callerAvatar ?: ""
        )
        startForeground(101, notification.build())
    }

    fun setCallEventListener(eventListener: CallEventListener) {
        this.eventListener = eventListener
    }

    override fun onDestroy() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    /**
     * Callback when local SDP offer/answer has been created.
     *
     * @param sdp The session description created.
     */
    override fun onLocalSdpCreated(sdp: SessionDescription) {
        socketManager.send("SDP_OFFER", JSONObject().apply {
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
        socketManager.send("ICE_CANDIDATE", JSONObject().apply {
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

    override fun onCallStateChanged(callState: CallState) {
        if (::eventListener.isInitialized)
            eventListener.onCallStateChanged(callState)
    }

    override fun onIceConnectionStateChanged(state: PeerConnection.IceConnectionState) {
        Log.i("ICE_STATE", state.toString())
    }


}