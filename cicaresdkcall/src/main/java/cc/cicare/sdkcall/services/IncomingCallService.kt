package cc.cicare.sdkcall.services

import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import cc.cicare.sdkcall.event.CallState
import cc.cicare.sdkcall.notifications.CallNotificationManager
import cc.cicare.sdkcall.notifications.ui.ScreenCallActivity
import cc.cicare.sdkcall.services.CiCareCallService.ACTION
import cc.cicare.sdkcall.signaling.SocketManager
import org.json.JSONObject
import kotlin.collections.plus

class IncomingCallService : Service() {

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
    private var isFromPhone = false

    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService(): IncomingCallService = this@IncomingCallService
    }

    override fun onBind(p0: Intent?): IBinder? {
        return binder
    }

    override fun onCreate() {
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        metaData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val extra = intent?.getSerializableExtra("meta_data", HashMap::class.java) as? HashMap<String, String>
            if (extra != null) HashMap(metaData + extra) else metaData
        } else {
            val extra = intent?.getSerializableExtra("meta_data") as? HashMap<String, String>
            if (extra != null) HashMap(metaData + extra) else metaData
        }
        Log.i("FCM", intent?.action.toString())
        when (intent?.action) {
            ACTION.INCOMING -> onIncomingCall(intent)
            ACTION.REJECT -> reject()
        }
        return START_STICKY
    }


    private fun onIncomingCall(intent: Intent) {
        val callerName = intent.getStringExtra("caller_name") ?: "unknown"
        val callerAvatar = intent.getStringExtra("caller_avatar") ?: ""
        val token = intent.getStringExtra("token") ?: return
        val server = intent.getStringExtra("server") ?: return
        isFromPhone = intent.getBooleanExtra("from_phone", false)
        CallNotificationManager.provideNotificationmanagerCompat(
            this, "CALL_INCOMING_CHANNEL_ID", NotificationManager.IMPORTANCE_MAX)
        val notification = CallNotificationManager.incomingCallNotificationBuilder(
            this,
            intent,
            "CALL_INCOMING_CHANNEL_ID",
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
                action = "INCOMING"
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                putExtras(intent)
            })
        }
    }

    fun reject() {
        SocketManager.send("REJECT", JSONObject().apply {})
        val isForeground = ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        if (isForeground) {
            startActivity(Intent(this, ScreenCallActivity::class.java).apply {
                action = "REJECT"
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            })
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun initReceive(server: String, token: String, isFromPhone: Boolean?) {
        SocketManager.connect(server, token)
        SocketManager.send("RINGING_CALL", JSONObject().apply {})

        this.isFromPhone = isFromPhone ?: false
    }

}
