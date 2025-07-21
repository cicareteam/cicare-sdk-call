package cc.cicare.sdkcall.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import cc.cicare.sdkcall.notifications.ui.ScreenCallActivity
import cc.cicare.sdkcall.services.CallAction
import cc.cicare.sdkcall.services.CiCareCallService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CallNotificationManager {

    @Singleton
    @Provides
    fun provideNotificationmanagerCompat(
        @ApplicationContext context: Context
    ): NotificationManagerCompat {
        val notificationManager = NotificationManagerCompat.from(context)
        val channel = NotificationChannel(
            CiCareCallService.CALL_CHANNEL_ID,
            "CALL_CHANNEL_NAME",
            NotificationManager.IMPORTANCE_HIGH
        )
        notificationManager.createNotificationChannel(channel)
        return notificationManager
    }

    @Singleton
    @Provides
    fun incomingCallNotificationBuilder(
        @ApplicationContext context: Context,
        intent: Intent,
        callerName: String,
        callerAvatar: String
    ): NotificationCompat.Builder {
        val callerProfile = Person.Builder()
            .setUri(callerAvatar)
            .setName(callerName)
            .setImportant(true)
            .build()

        return NotificationCompat.Builder(context, CiCareCallService.CALL_CHANNEL_ID)
            .setContentIntent(screenCallIntent(context, intent))
            .setSmallIcon(CiCareCallService.INCOMING_CALL_ICON)
            .setSound(CiCareCallService.ringtoneUrl, AudioManager.STREAM_RING)
            .addPerson(callerProfile)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .setStyle(NotificationCompat.CallStyle.forIncomingCall(
                callerProfile,
                serviceCallIntent(context, intent, 1, CallAction.REJECT),
                serviceCallIntent(context, intent, 2, CallAction.ACCEPT)
            ))
    }

    @Singleton
    @Provides
    fun outgoingCallNotificationBuilder(
        @ApplicationContext context: Context,
        intent: Intent,
        calleeName: String,
        calleeAvatar: String
    ): NotificationCompat.Builder {
        val callerProfile = Person.Builder()
            .setUri(calleeAvatar)
            .setName(calleeName)
            .setImportant(true)
            .build()

        return NotificationCompat.Builder(context, CiCareCallService.CALL_CHANNEL_ID)
            .setContentIntent(screenCallIntent(context, intent))
            .setSmallIcon(CiCareCallService.OUTGOING_CALL_ICON)
            .setSound(CiCareCallService.ringtoneUrl, AudioManager.STREAM_VOICE_CALL)
            .addPerson(callerProfile)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentText("Calling $calleeName...")
            .setStyle(NotificationCompat.CallStyle.forOngoingCall(
                callerProfile,
                serviceCallIntent(context, intent, 1, CallAction.HANGUP)
            ))
    }

    @Singleton
    @Provides
    fun ongoingCallNotificationBuilder(
        @ApplicationContext context: Context,
        intent: Intent,
        calleeName: String,
        calleeAvatar: String
    ): NotificationCompat.Builder {
        val callerProfile = Person.Builder()
            .setUri(calleeAvatar)
            .setName(calleeName)
            .setImportant(true)
            .build()

        return NotificationCompat.Builder(context, CiCareCallService.CALL_CHANNEL_ID)
            .setContentIntent(screenCallIntent(context, intent))
            .setSmallIcon(CiCareCallService.ONGOING_CALL_ICON)
            .setSound(CiCareCallService.ringtoneUrl, AudioManager.STREAM_VOICE_CALL)
            .addPerson(callerProfile)
            .setWhen(System.currentTimeMillis())
            .setUsesChronometer(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setOngoing(true)
            .setStyle(NotificationCompat.CallStyle.forOngoingCall(
                callerProfile,
                serviceCallIntent(context, intent, 1, CallAction.HANGUP)
            ))
    }

    @Singleton
    @Provides
    fun missedCallNotificationBuilder(
        @ApplicationContext context: Context,
        intent: Intent,
        calleeName: String,
        calleeAvatar: String
    ): NotificationCompat.Builder {
        val callerProfile = Person.Builder()
            .setUri(calleeAvatar)
            .setName(calleeName)
            .setImportant(true)
            .build()

        return NotificationCompat.Builder(context, CiCareCallService.CALL_CHANNEL_ID)
            .setSmallIcon(CiCareCallService.MISSED_CALL_ICON)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .addPerson(callerProfile)
    }

    private fun serviceCallIntent(
        @ApplicationContext context: Context,
        intent: Intent,
        code: Int,
        callAction: CallAction
    ): PendingIntent {
        val hangupIntent = Intent(context, CiCareCallService::class.java).apply {
            action = callAction.action
            putExtras(intent)
        }

        return PendingIntent.getService(
            context, code, hangupIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_CANCEL_CURRENT
            )
    }

    private fun screenCallIntent(
        @ApplicationContext context: Context,
        intent: Intent,
    ): PendingIntent {
        Log.i("INTEN", "SCREEN")
        val hangupIntent = Intent(context, ScreenCallActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtras(intent)
        }

        return PendingIntent.getService(
            context, 0, hangupIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }
}