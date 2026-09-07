package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.data.AppDatabase
import com.example.model.AlarmLog
import com.example.model.LogLevel
import com.example.scheduler.AlarmScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == Intent.ACTION_TIME_CHANGED ||
            action == Intent.ACTION_TIMEZONE_CHANGED
        ) {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val db = AppDatabase.getDatabase(context)
                    val enabledAlarms = db.alarmDao().getEnabledAlarmsSync()

                    for (alarm in enabledAlarms) {
                        AlarmScheduler.scheduleAlarm(context, alarm)
                    }

                    val log = AlarmLog(
                        alarmId = 0L,
                        alarmLabel = "سیستم",
                        timestamp = System.currentTimeMillis(),
                        eventType = "REBOOT_RESTORED",
                        level = LogLevel.SUCCESS,
                        message = "دستگاه راه‌اندازی مجدد شد؛ تعداد ${enabledAlarms.size} آلارم فعال مجدداً با بالاترین دقت زمان‌بندی شدند.",
                        technicalReason = "Broadcast action: $action"
                    )
                    db.alarmLogDao().insertLog(log)
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
