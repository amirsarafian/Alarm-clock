package com.example.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.example.MainActivity
import com.example.data.AppDatabase
import com.example.model.AlarmItem
import com.example.model.AlarmLog
import com.example.model.LogLevel
import com.example.receiver.AlarmReceiver
import com.example.util.DiagnosticHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object AlarmScheduler {

    const val EXTRA_ALARM_ID = "extra_alarm_id"
    const val EXTRA_SCHEDULED_TIME = "extra_scheduled_time"

    fun scheduleAlarm(context: Context, alarm: AlarmItem) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val triggerTime = calculateNextTriggerTime(alarm)

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_ALARM_TRIGGER
            putExtra(EXTRA_ALARM_ID, alarm.id)
            putExtra(EXTRA_SCHEDULED_TIME, triggerTime)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alarm.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // PendingIntent to show when user taps the alarm clock icon in system UI
        val showIntent = Intent(context, MainActivity::class.java)
        val showPendingIntent = PendingIntent.getActivity(
            context,
            alarm.id.toInt() + 100000,
            showIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val clockInfo = AlarmManager.AlarmClockInfo(triggerTime, showPendingIntent)
        alarmManager.setAlarmClock(clockInfo, pendingIntent)

        // Log the scheduling event
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getDatabase(context)
                val report = DiagnosticHelper.checkSystemStatus(context)
                val sdf = SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault())
                val formattedTime = sdf.format(Date(triggerTime))

                val msg = "آلارم برای زمان دقیق $formattedTime تنظیم شد. (سیستم ساعت دقیق AlarmClockInfo اختصاص داده شد)"
                val log = AlarmLog(
                    alarmId = alarm.id,
                    alarmLabel = alarm.label,
                    timestamp = System.currentTimeMillis(),
                    eventType = "SCHEDULED",
                    scheduledTime = triggerTime,
                    actualTime = 0L,
                    delayMs = 0L,
                    level = if (report.canScheduleExactAlarms) LogLevel.INFO else LogLevel.WARNING,
                    message = msg,
                    technicalReason = report.technicalDetails
                )
                db.alarmLogDao().insertLog(log)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun cancelAlarm(context: Context, alarm: AlarmItem) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_ALARM_TRIGGER
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alarm.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getDatabase(context)
                val log = AlarmLog(
                    alarmId = alarm.id,
                    alarmLabel = alarm.label,
                    timestamp = System.currentTimeMillis(),
                    eventType = "CANCELED",
                    level = LogLevel.INFO,
                    message = "آلارم «${alarm.label}» توسط کاربر لغو یا غیرفعال شد."
                )
                db.alarmLogDao().insertLog(log)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun calculateNextTriggerTime(alarm: AlarmItem): Long {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, alarm.hour)
            set(Calendar.MINUTE, alarm.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        if (alarm.daysOfWeek.isEmpty()) {
            // One-time alarm
            if (target.timeInMillis <= now.timeInMillis) {
                target.add(Calendar.DAY_OF_YEAR, 1)
            }
            return target.timeInMillis
        } else {
            // Repeating alarm for specific days
            // Calendar day of week: Sunday=1, Monday=2, Tuesday=3, Wednesday=4, Thursday=5, Friday=6, Saturday=7
            // Our model: 1=Mon, 2=Tue, 3=Wed, 4=Thu, 5=Fri, 6=Sat, 7=Sun
            for (offset in 0..7) {
                val check = (target.clone() as Calendar).apply {
                    add(Calendar.DAY_OF_YEAR, offset)
                }
                if (check.timeInMillis > now.timeInMillis) {
                    val calDay = check.get(Calendar.DAY_OF_WEEK)
                    val ourDay = when (calDay) {
                        Calendar.MONDAY -> 1
                        Calendar.TUESDAY -> 2
                        Calendar.WEDNESDAY -> 3
                        Calendar.THURSDAY -> 4
                        Calendar.FRIDAY -> 5
                        Calendar.SATURDAY -> 6
                        Calendar.SUNDAY -> 7
                        else -> 1
                    }
                    if (alarm.daysOfWeek.contains(ourDay)) {
                        return check.timeInMillis
                    }
                }
            }
            // Fallback
            if (target.timeInMillis <= now.timeInMillis) {
                target.add(Calendar.DAY_OF_YEAR, 1)
            }
            return target.timeInMillis
        }
    }
}
