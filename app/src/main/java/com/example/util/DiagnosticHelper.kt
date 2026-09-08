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

        val isPersian = LocaleHelper.isPersian(context)
        val issues = mutableListOf<String>()
        if (!canScheduleExact) {
            issues.add(
                if (isPersian) "مجوز آلارم دقیق (Exact Alarm) داده نشده است - ممکن است زنگ با تاخیر مواجه شود."
                else "Exact Alarm permission not granted - alarm might trigger late."
            )
        }
        if (!isBatteryIgnored) {
            issues.add(
                if (isPersian) "بهینه‌سازی باتری (Battery Optimization) برای برنامه فعال است - ممکن است سیستم در حالت خواب (Doze) آلارم را به تعویق بیندازد."
                else "Battery Optimization is enabled - system Doze mode may postpone the alarm."
            )
        }
        if (!areNotificationsEnabled) {
            issues.add(
                if (isPersian) "اعلان‌های برنامه مسدود است - کاربر هشدار زنگ را مشاهده نخواهد کرد."
                else "App notifications are disabled - alarm heads-up banner won't show."
            )
        }
        if (!canFullScreen) {
            issues.add(
                if (isPersian) "مجوز باز شدن تمام صفحه (Full Screen Intent) فعال نیست."
                else "Full screen intent permission is not active."
            )
        }
        if (alarmVol == 0) {
            issues.add(
                if (isPersian) "میزان صدای آلارم سیستم روی صفر (بی‌صدا) تنظیم است."
                else "System alarm volume is currently muted (0)."
            )
        }

        val summaryPersian = if (issues.isEmpty()) {
            if (isPersian) "وضعیت سیستم ایده‌آل است: تمامی پیش‌نیازهای زنگ دقیق، معافیت باتری و هشدارها با موفقیت فعال هستند."
            else "System status is optimal: Exact alarms, battery bypass, and alarm alerts are all active."
        } else {
            val prefix = if (isPersian) "هشدار موانع احتمالی زنگ زدن:\n" else "Potential alarm delay warnings:\n"
            prefix + issues.joinToString("\n• ", prefix = "• ")
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
