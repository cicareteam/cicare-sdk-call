package cc.cicare.sdkcall

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import cc.cicare.sdkcall.notifications.CallNotificationManager
import cc.cicare.sdkcall.notifications.ui.ScreenCallActivity
import cc.cicare.sdkcall.services.CiCareCallService

class CiCareSdkCall private constructor(private val context: Context, private val activity: Activity?) {

    companion object {
        fun init(context: Context): CiCareSdkCall {
            return CiCareSdkCall(context.applicationContext, null)
        }
        fun initActivity(context: Context, activity: Activity?): CiCareSdkCall {
            return CiCareSdkCall(context.applicationContext, activity)
        }
    }

    private val requiredPermissions = arrayOf(
        android.Manifest.permission.RECORD_AUDIO,
        android.Manifest.permission.FOREGROUND_SERVICE,
        android.Manifest.permission.POST_NOTIFICATIONS,
        android.Manifest.permission.FOREGROUND_SERVICE_MICROPHONE,
        android.Manifest.permission.FOREGROUND_SERVICE_PHONE_CALL
    )

    @RequiresApi(Build.VERSION_CODES.S)
    fun checkAndRequestPermissions() {
        if (requiredPermissions.any {
                ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
            }) {
            if (activity != null) {
                ActivityCompat.requestPermissions(activity, requiredPermissions, 1001)
            }
        }
    }

    private fun hasAllRequiredPermissions(context: Context): Boolean {
        return requiredPermissions.any {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    fun showIncoming(callerId: String,
                     callerName: String,
                     callerAvatar: String,
                     calleeId: String,
                     calleeName: String,
                     calleeAvatar: String,
                     checkSum: String,
                     metaData: Map<String, String> = emptyMap(),
                     tokenCall: String, server: String, isFromPhone: Boolean) {
        val meta: HashMap<String, String> = HashMap(metaData)
        val intent = Intent(context, ScreenCallActivity::class.java).apply {
            action = "INCOMING"
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("call_type", "incoming")
            putExtra("caller_name", callerName)
            putExtra("caller_avatar", callerAvatar)
            putExtra("meta_data", meta)
            putExtra("token", tokenCall)
            putExtra("server", server)
            putExtra("is_from_phone", isFromPhone)
        }
        /*val intent = Intent(context, CiCareCallService::class.java).apply {
            action = CiCareCallService.ACTION.INCOMING
            putExtra("call_type", "incoming")
            putExtra("caller_name", callerName)
            putExtra("caller_avatar", callerAvatar)
            putExtra("meta_data", meta)
            putExtra("token", tokenCall)
            putExtra("server", server)
            putExtra("is_from_phone", isFromPhone)
        }
        context.startForegroundService(intent)*/
        context.startForegroundService(intent)
        if (hasAllRequiredPermissions(context)) {
            val notificationManager = CallNotificationManager.provideNotificationmanagerCompat(
                context, "INCOMING_CALL_CHANNEL",
                NotificationManagerCompat.IMPORTANCE_MAX
            )
            val notify = CallNotificationManager.incomingCallNotificationBuilder(
                context,
                intent,
                "INCOMING_CALL_CHANNEL",
                callerName,
                callerAvatar
            )
            notificationManager.notify(101, notify.build())
        }
    }

    fun makeCall(callerId: String,
                         callerName: String,
                         callerAvatar: String,
                         calleeId: String,
                         calleeName: String,
                         calleeAvatar: String,
                         checkSum: String,
                         metaData: Map<String, String> = emptyMap()) {

                    val intent = Intent(context, CiCareCallService::class.java).apply {
                        action = CiCareCallService.ACTION.OUTGOING
                        putExtra("call_type", "outgoing")
                        putExtra("callee_id", calleeId)
                        putExtra("callee_name", calleeName)
                        putExtra("callee_avatar", calleeAvatar)
                        putExtra("caller_id", callerId)
                        putExtra("caller_name", callerName)
                        putExtra("caller_avatar", callerAvatar)
                        putExtra("checksum", checkSum)
                        putExtra("meta_data", HashMap(metaData))
                    }
                    context.startForegroundService(intent)

    }
}