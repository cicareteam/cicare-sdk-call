package cc.cicare.sdkcall.notifications.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cc.cicare.sdkcall.event.CallEventListener
import cc.cicare.sdkcall.event.CallState
import cc.cicare.sdkcall.services.CiCareCallService
import coil.compose.AsyncImage
import org.webrtc.PeerConnection
import java.util.HashMap

class ScreenCallActivity : ComponentActivity(), CallEventListener {

    private var callService: CiCareCallService? = null
    private var bound: Boolean = false
    private var eventListener: CallEventListener = this

    private var callStatus: String = "ringing"

    private var metaData: HashMap<*, *> = hashMapOf(
        "initializing" to "Initializing",
        "ringing" to "Ringing",
        "connected" to "Connected",
        "rejected" to "Rejected",
        "hangup" to "Hangup",
        "answer" to "Answer",
        "reject" to "Reject",
        "mute" to "Mute",
        "unmute" to "Unmute",
        "speaker" to "Speaker",
        "phone_speaker" to "Phone Speaker",
    )

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            callService = (binder as CiCareCallService.LocalBinder).getService()
            bound = true
            callService?.setCallEventListener(eventListener)
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val callerName = intent.getStringExtra("caller_name") ?: "Unknown"
        val callerAvatar = intent.getStringExtra("caller_avatar") ?: ""

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            metaData = intent.getSerializableExtra("meta_data", HashMap::class.java) ?: metaData
        else
            metaData = (intent.getSerializableExtra("meta_data") ?: metaData) as HashMap<*, *>

        enableEdgeToEdge()
        setContent {
            CallScreen(
                callerName,
                _e(callStatus, metaData) as String,
                callerAvatar,
                onMuteClick = {},
                onEndCallClick = {},
                onSpeakerClick = {}
            )
        }
    }

    override fun onConnectionStateChanged(state: PeerConnection.PeerConnectionState) {

    }

    override fun onCallStateChanged(callState: CallState) {

    }

    private fun _e(key: String, hashMap: HashMap<*, *>): Any? {
        return hashMap[key]
    }

}

@Composable
fun CallScreen(
    calleeName: String,
    callStatus: String,
    avatarUrl: String, // Avatar bisa dari drawable
    onMuteClick: () -> Unit,
    onSpeakerClick: () -> Unit,
    onEndCallClick: () -> Unit
) {
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {


            // Penelpon
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(modifier = Modifier.height(20.dp))
                Text(text = calleeName, style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.height(5.dp))
                Text(text = callStatus, style = MaterialTheme.typography.bodyMedium)
            }

            // Avatar
            CallAvatar(avatarUrl)

            // Tombol bawah
            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier.fillMaxWidth()
                    .padding(bottom = 15.dp)
            ) {
                RoundIconButton(icon = Icons.Default.MicOff, label = "Mute", onClick = onMuteClick)
                RoundIconButton(icon = Icons.Default.VolumeUp, label = "Speaker", onClick = onSpeakerClick)
                RoundIconButton(
                    icon = Icons.Default.CallEnd,
                    label = "End",
                    onClick = onEndCallClick,
                    backgroundColor = Color.Red,
                    iconTint = Color.White
                )
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

@Composable
@Preview
fun defaultPreview() {
    CallScreen(
        "callerName",
        "Ringing",
        "",
        onMuteClick = {},
        onEndCallClick = {},
        onSpeakerClick = {}
    )
}
