package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.scheduler.AlarmScheduler
import com.example.service.AlarmService

class AlarmReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_ALARM_TRIGGER = "com.aistudio.alarmclock.ACTION_ALARM_TRIGGER"
        const val ACTION_DISMISS_ALARM = "com.aistudio.alarmclock.ACTION_DISMISS_ALARM"
        const val ACTION_SNOOZE_ALARM = "com.aistudio.alarmclock.ACTION_SNOOZE_ALARM"
        const val ACTION_MUTE_ALARM = "com.aistudio.alarmclock.ACTION_MUTE_ALARM"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val alarmId = intent.getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, -1L)
        val scheduledTime = intent.getLongExtra(AlarmScheduler.EXTRA_SCHEDULED_TIME, 0L)

        val serviceIntent = Intent(context, AlarmService::class.java).apply {
            this.action = action
            putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId)
            putExtra(AlarmScheduler.EXTRA_SCHEDULED_TIME, scheduledTime)
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(context, serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
