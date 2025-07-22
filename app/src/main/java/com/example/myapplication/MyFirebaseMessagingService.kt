package com.example.myapplication

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        Log.i("FCM", token)
//
//        if (userId != null && userId > 0) {
//            CoroutineScope(Dispatchers.IO).launch {
//                try {
//                    val response = ApiClient.api.saveToken(TokenSaveRequest(userId, token))
//                    if (response.isSuccessful) {
//                        Log.d("FCM", "Token saved")
//                    } else {
//                        Log.e("FCM", "Token failed: ${response.code()}")
//                    }
//                } catch (e: Exception) {
//                    Log.e("FCM", "Error saving token: ${e.message}")
//                }
//            }
//        } else {
//            Log.w("FCM", "User ID not found, cannot save token.")
//        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val callerName = data["caller_name"] ?: "Unknown"
        val callerId = data["caller_id"] ?: ""
        val callerAvatar = data["caller_avatar"] ?: ""
        val tokenAnswer = data["token_receive"] ?: return
        val fromPhone = data["from_phone"] ?: "false"
        val server = data["server"] ?: return

        Log.i("FCM", "notifikasi masuk $data")

    }
}
