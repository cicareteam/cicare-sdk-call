package cc.cicare.sdkcall.notifications.ui

import android.annotation.SuppressLint
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlinx.coroutines.*
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import cc.cicare.sdkcall.event.CallEventListener
import cc.cicare.sdkcall.event.CallState
import cc.cicare.sdkcall.notifications.ui.model.CallViewModel
import cc.cicare.sdkcall.services.CiCareCallService
import cc.cicare.sdkcall.services.TimeTickerListener
import coil.compose.AsyncImage
import org.webrtc.PeerConnection
import kotlin.collections.HashMap

class ScreenCallActivity : ComponentActivity(), CallEventListener, TimeTickerListener {

    private var callService: CiCareCallService? = null
    private var bound: Boolean = false
    private var eventListener: CallEventListener = this
    private var tickerListener: TimeTickerListener = this
    //private var callDurationJob: Job? = null
    //private var callSeconds = 0

    private var timeTicker by mutableStateOf(0L)
    private val viewModel by viewModels<CallViewModel>()

    //private var callStatusRaw by mutableStateOf("initializing")
    private var isMicMuted by mutableStateOf(false)
    private var isSpeakerOn by mutableStateOf(false)

    private var metaData: HashMap<*, *> = hashMapOf(
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

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            callService = (binder as CiCareCallService.LocalBinder).getService()
            bound = true
            // Observe StateFlow
            lifecycleScope.launchWhenStarted {
                callService?.getCallStateFlow()?.collect {
                    viewModel.updateState(it)
                }
            }
            //Log.i("CALLSCREEN", callStatusRaw)
            callService?.setCallEventListener(eventListener)
            callService?.setTickerListener(tickerListener)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            bound = false
            callService = null
        }
    }

    override fun onStart() {
        super.onStart()
        Intent(this, CiCareCallService::class.java).also {
            bindService(it, connection, Context.BIND_AUTO_CREATE)
        }
    }

    override fun onStop() {
        super.onStop()
        if (bound) {
            unbindService(connection)
            bound = false
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        when(intent?.action) {
            CiCareCallService.ACTION.ACCEPT -> lifecycleScope.launch { answer() }
        }
        lifecycleScope.launchWhenStarted {
            callService?.getCallStateFlow()?.collect {
                viewModel.updateState(it)
            }
        }
        callService?.setCallEventListener(eventListener)
        // handle update state or extras here
    }

    @SuppressLint("ServiceCast")
    private fun requestAudioFocus() {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setOnAudioFocusChangeListener { /* optional */ }
            .build()
        audioManager.requestAudioFocus(focusRequest)
    }

    private fun hasAllRequiredPermissions(context: Context): Boolean {
        val micPermission = ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO)
        val fgsMicPermission = if (Build.VERSION.SDK_INT >= 34) {
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.FOREGROUND_SERVICE_MICROPHONE)
        } else PackageManager.PERMISSION_GRANTED

        return micPermission == PackageManager.PERMISSION_GRANTED &&
                fgsMicPermission == PackageManager.PERMISSION_GRANTED
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (hasAllRequiredPermissions(this)) {
            requestAudioFocus()
        }

        if (intent.action == "INCOMING") {
            val serviceIntent = Intent(this, CiCareCallService::class.java).apply {
                action = CiCareCallService.ACTION.INCOMING
                putExtras(intent) // semua data dari FCM diteruskan
            }
            ContextCompat.startForegroundService(this, serviceIntent)
        }

        val callerName = intent.getStringExtra("caller_name") ?: "Unknown"
        val callerAvatar = intent.getStringExtra("caller_avatar") ?: ""
        val calleeName = intent.getStringExtra("callee_name") ?: "unknown"
        val calleeAvatar = intent.getStringExtra("callee_avatar") ?: ""
        val callType = intent.getStringExtra("call_type") ?: "outgoing"

        metaData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val extra = intent.getSerializableExtra("meta_data", HashMap::class.java) as? HashMap<String, String>
            if (extra != null) HashMap(metaData + extra) else metaData
        } else {
            val extra = intent.getSerializableExtra("meta_data") as? HashMap<String, String>
            if (extra != null) HashMap(metaData + extra) else metaData
        }
        Log.i("CALLSCREEN ACTIOn", intent.action.toString())
        when(intent.action) {
            CiCareCallService.ACTION.ACCEPT -> lifecycleScope.launch { answer() }
        }
        enableEdgeToEdge()
        setContent {
            val callStatusRaw by viewModel.callStatusRaw.collectAsState()
            CallScreen(
                if (callType == "incoming") callerName else calleeName,
                callTimer = if
                        (callStatusRaw == "connected") formatElapsedTime(timeTicker)
                        else metaData[callStatusRaw] ?: callStatusRaw,
                callStatusRaw = callStatusRaw,
                if (callType == "incoming") callerAvatar else calleeAvatar,
                isMicMuted,
                isSpeakerOn,
                metaData = metaData.mapKeys { it.key.toString() }.mapValues { it.value.toString() },
                onMuteClick = {
                    isMicMuted = !isMicMuted
                    callService?.setMute(isMicMuted)
                },
                onSpeakerClick = {
                    isSpeakerOn = !isSpeakerOn
                    callService?.setSpeaker(isSpeakerOn)
                },
                onAnswerCallClick = {
                    lifecycleScope.launch {
                        answer()
                    }
                },
                onEndCallClick = {
                    if (callStatusRaw !="connected") {
                        callService?.reject()
                    } else {
                        hangup()
                    }
                    finish()
                },
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        //callDurationJob?.cancel()
        callService?.stopSelf()
    }


    override fun onConnectionStateChanged(state: PeerConnection.PeerConnectionState) {

    }

    override fun onTimeTicketUpdate(seconds: Long) {
        timeTicker = seconds
    }

    override fun onCallStateChanged(callState: CallState) {
        Log.i("HELLO", "RUN TIMER")
        //callStatusRaw = callState.toString().lowercase()
        /*when (callState) {
            CallState.RINGING -> callStatus = "ringing"
            CallState.CONNECTED -> callStatus = "connected"
            CallState.ENDED -> {
                callStatus = callState.name.lowercase()
                finish()
            }
            else -> callStatus = callStatusRaw
        }*/

        /*if (callState == CallState.CONNECTED) {
            Log.i("SCREEN", "RUN TIMER")
            // Start timer
            callSeconds = 0
            callDurationJob?.cancel()
            callDurationJob = CoroutineScope(Dispatchers.Main).launch {
                while (isActive) {
                    delay(1000)
                    callSeconds++
                    callTimer = formatTime(callSeconds)
                }
            }
        } else*/ if (callState == CallState.ENDED) {
            finish()
        }
    }

    private suspend fun answer() {
        callService?.answerCall(intent, true)
    }

    private fun hangup() {
        callService?.hangup()
        finish()
    }

    @SuppressLint("DefaultLocale")
    private fun formatElapsedTime(seconds: Long): String {
        val minutes = seconds / 60
        val secs = seconds % 60
        return String.format("%02d:%02d", minutes, secs)
    }

//    private fun _e(key: String, hashMap: Map<*, *>): Any {
//        return hashMap[key] ?: key
//    }

}

@Composable
fun CallScreen(
    callerName: String,
    callTimer: Any,
    callStatusRaw: String,
    avatarUrl: String,
    isMicMuted: Boolean,
    isSpeakerOn: Boolean,
    metaData: Map<String, String>,
    onMuteClick: () -> Unit,
    onSpeakerClick: () -> Unit,
    onAnswerCallClick: () -> Unit,
    onEndCallClick: () -> Unit
) {
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(Color(0xFF999999)),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(modifier = Modifier.height(20.dp))
                Text(text = callerName, style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.height(5.dp))
                Text(
                    text = callTimer as String,
                    style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(100.dp))
            }

            // Avatar
            CallAvatar(avatarUrl)

            Spacer(modifier = Modifier.height(10.dp))

            if (callStatusRaw.lowercase() == "incoming") {
                Row(
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 15.dp)
                ) {
                    RoundIconButton(
                        icon = Icons.Default.CallEnd,
                        label = metaData["decline"] ?: "Reject",
                        onClick = onEndCallClick,
                        backgroundColor = Color.Red,
                        iconTint = Color.White
                    )

                    RoundIconButton(
                        icon = Icons.Default.Person,
                        label = metaData["answer"] ?: "Answer",
                        onClick = onAnswerCallClick,
                        backgroundColor = Color.Green,
                        iconTint = Color.White
                    )
                }
            } else {
                Row(
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    modifier = Modifier.fillMaxWidth()
                        .padding(bottom = 15.dp)
                ) {
                    RoundIconButton(
                        icon = Icons.Default.MicOff,
                        label = metaData["mute"] ?: "Mute",
                        onClick = onMuteClick,
                        backgroundColor = if (isMicMuted) Color.White else Color.LightGray,
                        iconTint = if (isMicMuted) Color.Red else Color.White
                    )
                    RoundIconButton(
                        icon = Icons.AutoMirrored.Filled.VolumeUp,
                        label = metaData["speaker"] ?: "Speaker",
                        onClick = onSpeakerClick,
                        backgroundColor = if (isSpeakerOn) Color.White else Color.LightGray,
                        iconTint = if (isSpeakerOn) Color.Black else Color.Black
                    )
                    RoundIconButton(
                        icon = Icons.Default.CallEnd,
                        label = metaData["end"] ?: "End",
                        onClick = onEndCallClick,
                        backgroundColor = Color.Red,
                        iconTint = Color.White
                    )
                }
            }
        }
    }
}

@Composable
fun CallAvatar(imageUrl: String?) {
    if (imageUrl.isNullOrBlank()) {
        // Tampilkan icon orang jika URL kosong
        Box(
            modifier = Modifier
                .size(160.dp)
                .clip(CircleShape)
                .background(Color.LightGray),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = "Default Avatar",
                tint = Color.White,
                modifier = Modifier.size(80.dp)
            )
        }
    } else {
        // Tampilkan gambar dari URL
        AsyncImage(
            model = imageUrl,
            contentDescription = "Caller Avatar",
            modifier = Modifier
                .size(160.dp)
                .clip(CircleShape)
                .border(2.dp, Color.Gray, CircleShape)
        )
    }
}


@Composable
fun RoundIconButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    backgroundColor: Color = Color.LightGray,
    iconTint: Color = Color.Black
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(backgroundColor)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = label, tint = iconTint)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = label, style = MaterialTheme.typography.bodySmall)
    }
}
//
//@Composable
//@Preview
//fun DefaultPreview() {
//    var metaData: HashMap<*, *> = hashMapOf(
//        "initializing" to "Initializing",
//        "ringing" to "Ringing",
//        "connected" to "Connected",
//        "ended" to "Ended",
//        "answer" to "Answer",
//        "decline" to "Decline",
//        "mute" to "Mute",
//        "unmute" to "Unmute",
//        "speaker" to "Speaker",
//        "phone_speaker" to "Phone Speaker",
//    )
//    CallScreen(
//        "callerName",
//        "Connected",
//        "connected",
//        "",
//        true,
//        true,
//        metaData.mapKeys { it.key.toString() }.mapValues { it.value.toString() },
//        onMuteClick = {},
//        onEndCallClick = {},
//        onAnswerCallClick = {},
//        onSpeakerClick = {}
//    )
//}
