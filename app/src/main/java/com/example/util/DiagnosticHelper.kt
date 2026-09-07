package com.example.util

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

data class SystemStatusReport(
    val canScheduleExactAlarms: Boolean,
    val isBatteryOptimizationIgnored: Boolean,
    val areNotificationsEnabled: Boolean,
    val canUseFullScreenIntent: Boolean,
    val alarmVolumeLevel: Int,
    val maxAlarmVolume: Int,
    val ringerMode: Int,
    val summaryPersian: String,
    val technicalDetails: String
)

object DiagnosticHelper {

    fun checkSystemStatus(context: Context): SystemStatusReport {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val notificationManager = NotificationManagerCompat.from(context)

        // 1. Exact Alarm permission (Android 12+)
        val canScheduleExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }

        // 2. Battery optimization status
        val isBatteryIgnored = powerManager.isIgnoringBatteryOptimizations(context.packageName)

        // 3. Notification permission
        val areNotificationsEnabled = notificationManager.areNotificationsEnabled()

        // 4. Full screen intent (Android 14+)
        val canFullScreen = if (Build.VERSION.SDK_INT >= 34) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.canUseFullScreenIntent()
        } else {
            true
        }

        // 5. Audio volume
        val alarmVol = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
        val maxAlarmVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        val ringerMode = audioManager.ringerMode

        val issues = mutableListOf<String>()
        if (!canScheduleExact) {
            issues.add("مجوز آلارم دقیق (Exact Alarm) داده نشده است - ممکن است زنگ با تاخیر مواجه شود.")
        }
        if (!isBatteryIgnored) {
            issues.add("بهینه‌سازی باتری (Battery Optimization) برای برنامه فعال است - ممکن است سیستم در حالت خواب (Doze) آلارم را به تعویق بیندازد.")
        }
        if (!areNotificationsEnabled) {
            issues.add("اعلان‌های برنامه مسدود است - کاربر هشدار زنگ را مشاهده نخواهد کرد.")
        }
        if (!canFullScreen) {
            issues.add("مجوز باز شدن تمام صفحه (Full Screen Intent) فعال نیست.")
        }
        if (alarmVol == 0) {
            issues.add("میزان صدای آلارم سیستم روی صفر (بی‌صدا) تنظیم است.")
        }

        val summaryPersian = if (issues.isEmpty()) {
            "وضعیت سیستم ایده‌آل است: تمامی پیش‌نیازهای زنگ دقیق، معافیت باتری و هشدارها با موفقیت فعال هستند."
        } else {
            "هشدار موانع احتمالی زنگ زدن:\n" + issues.joinToString("\n• ", prefix = "• ")
        }

        val technicalDetails = buildString {
            append("ExactAlarm=$canScheduleExact; ")
            append("BatteryOptIgnored=$isBatteryIgnored; ")
            append("NotificationsEnabled=$areNotificationsEnabled; ")
            append("FullScreenIntent=$canFullScreen; ")
            append("AlarmVolume=$alarmVol/$maxAlarmVol; ")
            append("RingerMode=$ringerMode; ")
            append("AndroidApi=${Build.VERSION.SDK_INT}; ")
            append("Manufacturer=${Build.MANUFACTURER}; ")
            append("Model=${Build.MODEL}")
        }

        return SystemStatusReport(
            canScheduleExactAlarms = canScheduleExact,
            isBatteryOptimizationIgnored = isBatteryIgnored,
            areNotificationsEnabled = areNotificationsEnabled,
            canUseFullScreenIntent = canFullScreen,
            alarmVolumeLevel = alarmVol,
            maxAlarmVolume = maxAlarmVol,
            ringerMode = ringerMode,
            summaryPersian = summaryPersian,
            technicalDetails = technicalDetails
        )
    }

    fun openExactAlarmSettingsIntent(context: Context): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                data = Uri.parse("package:${context.packageName}")
            }
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
        }
    }

    fun openBatteryOptimizationSettingsIntent(context: Context): Intent {
        return Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
    }

    fun openAppNotificationSettingsIntent(context: Context): Intent {
        return Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        }
    }
}
