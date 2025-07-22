package cc.cicare.sdkcall

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import cc.cicare.sdkcall.libs.ApiClient
import cc.cicare.sdkcall.libs.CallRequest
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
        android.Manifest.permission.FOREGROUND_SERVICE_PHONE_CALL,
        android.Manifest.permission.FOREGROUND_SERVICE_MICROPHONE
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
        val intent = Intent(context, CiCareCallService::class.java).apply {
            action = CiCareCallService.ACTION.INCOMING
            putExtra("call_type", "incoming")
            putExtra("caller_name", callerName)
            putExtra("caller_avatar", callerAvatar)
            putExtra("meta_data", meta)
            putExtra("token", tokenCall)
            putExtra("server", server)
            putExtra("is_from_phone", isFromPhone)
        }
        context.startForegroundService(intent)
    }

    suspend fun makeCall(callerId: String,
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