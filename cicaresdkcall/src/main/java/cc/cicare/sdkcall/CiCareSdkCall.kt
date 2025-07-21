package cc.cicare.sdkcall

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat.startForegroundService
import cc.cicare.sdkcall.services.CiCareCallService

class CiCareSdkCall private constructor(private val context: Context) {

    companion object {
        fun init(context: Context): CiCareSdkCall {
            return CiCareSdkCall(context.applicationContext)
        }
    }

    fun showIncoming(callerName: String, callerAvatar: String, tokenCall: String, server: String, isFromPhone: Boolean) {
        val intent = Intent(context, CiCareCallService::class.java).apply {
            action = CiCareCallService.ACTION.INCOMING
            putExtra("caller_name", callerName)
            putExtra("caller_avatar", callerAvatar)
            putExtra("token", tokenCall)
            putExtra("server", server)
            putExtra("is_from_phone", isFromPhone)
        }
        context.startForegroundService(intent)
    }
}