package com.example.service

import android.app.KeyguardManager
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.AlarmApplication
import com.example.R
import com.example.data.AppDatabase
import com.example.model.AlarmItem
import com.example.model.AlarmLog
import com.example.model.LogLevel
import com.example.receiver.AlarmReceiver
import com.example.scheduler.AlarmScheduler
import com.example.util.LocaleHelper
import com.example.ui.ringing.AlarmRingingActivity
import com.example.util.DiagnosticHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AlarmService : Service() {

    companion object {
        const val NOTIFICATION_ID = 2001

        private val _currentAlarmState = MutableStateFlow<AlarmRingingState?>(null)
        val currentAlarmState: StateFlow<AlarmRingingState?> = _currentAlarmState.asStateFlow()

        @Volatile
        private var instance: AlarmService? = null

        fun isCurrentlyMuted(): Boolean {
            return instance?.isMuted == true
        }

        fun muteImmediately(context: Context) {
            val service = instance
            if (service != null) {
                // Instantly and synchronously silence audio & vibration on the current thread
                service.silenceAudioImmediately()
                service.handlePowerButtonMute()
            } else {
                val intent = Intent(context, AlarmService::class.java).apply {
                    action = AlarmReceiver.ACTION_MUTE_ALARM
                }
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        ContextCompat.startForegroundService(context, intent)
                    } else {
                        context.startService(intent)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    data class AlarmRingingState(
        val alarm: AlarmItem,
        val isMuted: Boolean,
        val muteRemainingSeconds: Int,
        val currentVolumePercent: Int
    )

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var wakeLock: PowerManager.WakeLock? = null
    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var currentAlarm: AlarmItem? = null

    private var gradualVolumeJob: Job? = null
    private var smartMuteJob: Job? = null

    private var isMuted = false
    private var remainingMuteSeconds = 60
    private var currentVolumePercent = 0
    private var isScreenReceiverRegistered = false

    private val screenAndUnlockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    // User pressed hardware Power button!
                    handlePowerButtonMute()
                }
                Intent.ACTION_USER_PRESENT -> {
                    // User unlocked phone!
                    handleDeviceUnlocked()
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "AlarmClock:AlarmServiceWakeLock"
        ).apply {
            setReferenceCounted(false)
            acquire(10 * 60 * 1000L) // 10 minutes max
        }

        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        registerReceiver(screenAndUnlockReceiver, filter)
        isScreenReceiverRegistered = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: return START_NOT_STICKY
        val alarmId = intent.getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, -1L)
        val scheduledTime = intent.getLongExtra(AlarmScheduler.EXTRA_SCHEDULED_TIME, 0L)

        when (action) {
            AlarmReceiver.ACTION_ALARM_TRIGGER -> {
                startRinging(alarmId, scheduledTime)
            }
            AlarmReceiver.ACTION_MUTE_ALARM -> {
                handlePowerButtonMute()
            }
            AlarmReceiver.ACTION_DISMISS_ALARM -> {
                handleDismissRequest()
            }
            AlarmReceiver.ACTION_SNOOZE_ALARM -> {
                handleSnooze()
            }
        }

        return START_STICKY
    }

    private fun startRinging(alarmId: Long, scheduledTime: Long) {
        serviceScope.launch(Dispatchers.IO) {
            val isPersian = LocaleHelper.isPersian(applicationContext)
            val defaultAlarmLabel = if (isPersian) "آلارم" else "Alarm"
            val db = AppDatabase.getDatabase(applicationContext)
            val alarm = if (alarmId > 0) db.alarmDao().getAlarmById(alarmId) else null
            val resolvedAlarm = alarm ?: AlarmItem(
                id = if (alarmId > 0) alarmId else 1L,
                hour = 0,
                minute = 0,
                label = defaultAlarmLabel,
                volume = 85,
                isGradualVolume = true,
                isSmartMuteEnabled = true,
                vibrate = false
            )

            currentAlarm = resolvedAlarm
            val actualTime = System.currentTimeMillis()
            val delayMs = if (scheduledTime > 0) actualTime - scheduledTime else 0L

            // Log trigger event with exact delay evaluation
            val report = DiagnosticHelper.checkSystemStatus(applicationContext)
            val isSevereDelay = delayMs > 5000L
            val delaySeconds = delayMs / 1000.0
            val logLabel = resolvedAlarm.label.ifBlank { defaultAlarmLabel }

            val logMsg = if (scheduledTime > 0) {
                if (isSevereDelay) {
                    if (isPersian) {
                        "هشدار: آلارم «$logLabel» با تاخیر %.1f ثانیه‌ای به صدا درآمد! دلایل احتمالی: محدودیت باتری یا خواب عمیق سیستم.".format(delaySeconds)
                    } else {
                        "Warning: Alarm \"$logLabel\" rang with %.1f sec delay! Possible reasons: battery optimization or deep sleep.".format(delaySeconds)
                    }
                } else {
                    if (isPersian) {
                        "آلارم «$logLabel» با موفقیت و در زمان دقیق به صدا درآمد. (اختلاف زمان: ${delayMs} میلی‌ثانیه)"
                    } else {
                        "Alarm \"$logLabel\" triggered accurately on time. (Delta: ${delayMs} ms)"
                    }
                }
            } else {
                if (isPersian) {
                    "آلارم «$logLabel» به صدا درآمد."
                } else {
                    "Alarm \"$logLabel\" started ringing."
                }
            }

            val log = AlarmLog(
                alarmId = resolvedAlarm.id,
                alarmLabel = logLabel,
                timestamp = actualTime,
                eventType = if (isSevereDelay) "DELAY_WARNING" else "TRIGGERED",
                scheduledTime = scheduledTime,
                actualTime = actualTime,
                delayMs = delayMs,
                level = if (isSevereDelay) LogLevel.WARNING else LogLevel.SUCCESS,
                message = logMsg,
                technicalReason = report.technicalDetails
            )
            db.alarmLogDao().insertLog(log)

            // If one-time alarm, disable it in DB
            if (resolvedAlarm.daysOfWeek.isEmpty()) {
                db.alarmDao().setAlarmEnabled(resolvedAlarm.id, false)
            } else {
                // Schedule next occurrence
                AlarmScheduler.scheduleAlarm(applicationContext, resolvedAlarm)
            }

            launch(Dispatchers.Main) {
                initAudioAndVibration(resolvedAlarm)
                val notification = buildForegroundNotification(resolvedAlarm, false, 0)
                startForeground(NOTIFICATION_ID, notification)
                updateState()
            }
        }
    }

    private fun initAudioAndVibration(alarm: AlarmItem) {
        val targetVolumePercent = alarm.volume.coerceIn(10, 100)
        val targetVolumeFraction = targetVolumePercent / 100f

        try {
            val ringtoneUri = if (alarm.ringtoneUri.isNotEmpty()) {
                Uri.parse(alarm.ringtoneUri)
            } else {
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            }

            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(applicationContext, ringtoneUri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                isLooping = true
            }

            if (alarm.isGradualVolume) {
                // Crescendo: start gently at 20% and increase noticeably every 10 seconds
                val startVolume = 0.20f.coerceAtMost(targetVolumeFraction)
                mediaPlayer?.setVolume(startVolume, startVolume)
                currentVolumePercent = (startVolume * 100).toInt()
                mediaPlayer?.prepare()
                mediaPlayer?.start()

                startGradualVolumeRamp(targetVolumeFraction, startVolume)
            } else {
                mediaPlayer?.setVolume(targetVolumeFraction, targetVolumeFraction)
                currentVolumePercent = targetVolumePercent
                mediaPlayer?.prepare()
                mediaPlayer?.start()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (alarm.vibrate) {
            startVibration()
        }
    }

    private fun startGradualVolumeRamp(targetVolumeFraction: Float, startVolume: Float) {
        gradualVolumeJob?.cancel()
        gradualVolumeJob = serviceScope.launch {
            val stepTimeMs = 10_000L // Increase volume every 10 seconds
            val stepIncrement = 0.20f // Noticeable increase of 20% per step

            var currentVol = startVolume
            while (isActive && !isMuted && currentVol < targetVolumeFraction) {
                delay(stepTimeMs)
                if (isMuted) break
                currentVol = (currentVol + stepIncrement).coerceAtMost(targetVolumeFraction)
                mediaPlayer?.setVolume(currentVol, currentVol)
                currentVolumePercent = (currentVol * 100).toInt()
                updateState()
            }
            if (!isMuted && currentVol >= targetVolumeFraction) {
                mediaPlayer?.setVolume(targetVolumeFraction, targetVolumeFraction)
                currentVolumePercent = (targetVolumeFraction * 100).toInt()
                updateState()
            }
        }
    }

    private fun startVibration() {
        val pattern = longArrayOf(0, 700, 400, 700, 400)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val effect = VibrationEffect.createWaveform(pattern, 0)
            vibrator?.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(pattern, 0)
        }
    }

    private fun stopVibration() {
        vibrator?.cancel()
    }

    /**
     * Synchronously and immediately silences audio and vibration.
     * Can be called from any thread without waiting for coroutines.
     */
    fun silenceAudioImmediately() {
        isMuted = true
        gradualVolumeJob?.cancel()
        try {
            mediaPlayer?.setVolume(0f, 0f)
            mediaPlayer?.pause()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        try {
            vibrator?.cancel()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Requirement 6:
     * When user presses Power button / Screen Off, mute alarm sound immediately to avoid disturbing others.
     * Starts a 60-second timer during which the user must unlock phone to permanently dismiss.
     * If 60 seconds expire and phone has not been unlocked, resumes sound at full volume!
     */
    private fun handlePowerButtonMute() {
        silenceAudioImmediately()

        val alarm = currentAlarm ?: return
        if (!alarm.isSmartMuteEnabled) return

        if (smartMuteJob?.isActive == true && remainingMuteSeconds > 0) {
            return // already in mute countdown
        }

        remainingMuteSeconds = 60
        updateState()

        // Post silent notification with mute message once (no vibration, no sound alerts, NO fullScreenIntent)
        val notification = buildForegroundNotification(alarm, isMuted = true, remainingSeconds = 60)
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        nm.notify(NOTIFICATION_ID, notification)

        val isPersian = java.util.Locale.getDefault().language == "fa"
        // Log mute event
        serviceScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(applicationContext)
            val log = AlarmLog(
                alarmId = alarm.id,
                alarmLabel = alarm.label,
                timestamp = System.currentTimeMillis(),
                eventType = "MUTED_POWER_BUTTON",
                level = LogLevel.WARNING,
                message = if (isPersian) "آلارم با دکمه پاور بی‌صدا شد. کاربر ۱ دقیقه فرصت دارد قفل گوشی را باز کند."
                          else "Alarm silenced with power button. User has 1 minute to unlock device.",
                technicalReason = "Power button / Screen off detected. Audio silenced immediately. 60s countdown engaged."
            )
            db.alarmLogDao().insertLog(log)
        }

        // 60-second countdown without any recurring vibration or alert sounds
        smartMuteJob?.cancel()
        smartMuteJob = serviceScope.launch {
            for (sec in 60 downTo 1) {
                remainingMuteSeconds = sec
                updateState()
                delay(1000L)
            }

            // If we reached here, 60 seconds expired without unlock/dismiss!
            resumeLoudAlarmAfterTimeout()
        }
    }

    private fun resumeLoudAlarmAfterTimeout() {
        val alarm = currentAlarm ?: return
        isMuted = false
        remainingMuteSeconds = 0

        val targetVolumePercent = alarm.volume.coerceIn(10, 100)
        val targetFraction = targetVolumePercent / 100f
        try {
            if (alarm.isGradualVolume) {
                // User requested: reset gradual volume increase starting from low volume and ramp up every 10 seconds
                val startVolume = 0.20f.coerceAtMost(targetFraction)
                mediaPlayer?.setVolume(startVolume, startVolume)
                mediaPlayer?.start()
                currentVolumePercent = (startVolume * 100).toInt()
                startGradualVolumeRamp(targetFraction, startVolume)
            } else {
                mediaPlayer?.setVolume(targetFraction, targetFraction)
                mediaPlayer?.start()
                currentVolumePercent = targetVolumePercent
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (alarm.vibrate) {
            startVibration()
        }

        // Re-wake screen
        wakeLock?.acquire(3 * 60 * 1000L)

        // Launch full-screen ringing activity again
        val ringingIntent = Intent(applicationContext, AlarmRingingActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarm.id)
        }
        startActivity(ringingIntent)

        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        nm.notify(NOTIFICATION_ID, buildForegroundNotification(alarm, false, 0))
        updateState()

        val isPersian = java.util.Locale.getDefault().language == "fa"
        // Log resume event
        serviceScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(applicationContext)
            val log = AlarmLog(
                alarmId = alarm.id,
                alarmLabel = alarm.label,
                timestamp = System.currentTimeMillis(),
                eventType = "RESUMED_UNANSWERED",
                level = LogLevel.ERROR,
                message = if (isPersian) "مهلت ۱ دقیقه‌ای پایان یافت اما قفل گوشی باز نشد! آلارم مجدداً به صدا درآمد."
                          else "1-minute mute expired without device unlock. Alarm resumed loudly.",
                technicalReason = "60-second mute countdown elapsed without lockscreen dismissal."
            )
            db.alarmLogDao().insertLog(log)
        }
    }

    private fun handleDeviceUnlocked() {
        val alarm = currentAlarm ?: return
        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        val isPersian = java.util.Locale.getDefault().language == "fa"

        if (keyguardManager.isDeviceSecure) {
            // Device is configured with PIN/Pattern/Password/Biometrics.
            // Require user to actually unlock using their credential!
            if (!keyguardManager.isDeviceLocked) {
                val reason = if (isPersian) "قفل امنیتی گوشی (پین/پترن/اثر انگشت) باز شد و آلارم با موفقیت قطع گردید."
                             else "Device unlocked securely. Alarm dismissed."
                dismissAlarmSuccessfully(reason)
            }
        } else {
            // Unsecured keyguard (swipe or none)
            if (!keyguardManager.isKeyguardLocked) {
                val reason = if (isPersian) "قفل صفحه باز شد و آلارم متوقف شد."
                             else "Keyguard dismissed. Alarm stopped."
                dismissAlarmSuccessfully(reason)
            }
        }
    }

    private fun handleDismissRequest() {
        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        val isPersian = java.util.Locale.getDefault().language == "fa"

        if (keyguardManager.isDeviceSecure && keyguardManager.isDeviceLocked) {
            // Secure device is currently locked. Trigger authentication challenge!
            val ringingIntent = Intent(applicationContext, AlarmRingingActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                putExtra("REQUEST_UNLOCK_CHALLENGE", true)
            }
            startActivity(ringingIntent)
        } else if (keyguardManager.isKeyguardLocked) {
            val ringingIntent = Intent(applicationContext, AlarmRingingActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            }
            startActivity(ringingIntent)
        } else {
            val msg = if (isPersian) "آلارم توسط کاربر با باز بودن قفل گوشی به طور کامل قطع شد."
                      else "Alarm dismissed with phone unlocked."
            dismissAlarmSuccessfully(msg)
        }
    }

    private fun handleSnooze() {
        val alarm = currentAlarm ?: return
        serviceScope.launch(Dispatchers.IO) {
            val isPersian = LocaleHelper.isPersian(applicationContext)
            val db = AppDatabase.getDatabase(applicationContext)
            val alarmLabel = alarm.label.ifBlank { if (isPersian) "آلارم" else "Alarm" }
            val log = AlarmLog(
                alarmId = alarm.id,
                alarmLabel = alarmLabel,
                timestamp = System.currentTimeMillis(),
                eventType = "SNOOZED",
                level = LogLevel.INFO,
                message = if (isPersian) "آلارم «$alarmLabel» برای ۵ دقیقه به تعویق افتاد (Snooze)."
                          else "Alarm \"$alarmLabel\" snoozed for 5 minutes."
            )
            db.alarmLogDao().insertLog(log)

            // Reschedule in 5 minutes
            val snoozeAlarm = alarm.copy(
                hour = ((System.currentTimeMillis() + 5 * 60 * 1000L) / (1000 * 60 * 60) % 24).toInt(),
                minute = ((System.currentTimeMillis() + 5 * 60 * 1000L) / (1000 * 60) % 60).toInt()
            )
            AlarmScheduler.scheduleAlarm(applicationContext, snoozeAlarm)
        }

        stopAlarmAndCleanup()
    }

    fun dismissAlarmSuccessfully(reason: String) {
        val alarm = currentAlarm
        serviceScope.launch(Dispatchers.IO) {
            if (alarm != null) {
                val db = AppDatabase.getDatabase(applicationContext)
                val log = AlarmLog(
                    alarmId = alarm.id,
                    alarmLabel = alarm.label,
                    timestamp = System.currentTimeMillis(),
                    eventType = "DISMISSED_UNLOCKED",
                    level = LogLevel.SUCCESS,
                    message = reason,
                    technicalReason = "Keyguard is unlocked. Permanent dismissal confirmed."
                )
                db.alarmLogDao().insertLog(log)
            }
        }
        stopAlarmAndCleanup()
    }

    private fun stopAlarmAndCleanup() {
        smartMuteJob?.cancel()
        gradualVolumeJob?.cancel()
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
        stopVibration()
        wakeLock?.let {
            if (it.isHeld) it.release()
        }

        if (isScreenReceiverRegistered) {
            try {
                unregisterReceiver(screenAndUnlockReceiver)
                isScreenReceiverRegistered = false
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        _currentAlarmState.value = null
        if (instance == this) {
            instance = null
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun updateState() {
        val alarm = currentAlarm ?: return
        _currentAlarmState.value = AlarmRingingState(
            alarm = alarm,
            isMuted = isMuted,
            muteRemainingSeconds = remainingMuteSeconds,
            currentVolumePercent = currentVolumePercent
        )
    }

    private fun buildForegroundNotification(
        alarm: AlarmItem,
        isMuted: Boolean,
        remainingSeconds: Int
    ): Notification {
        val fullScreenIntent = Intent(this, AlarmRingingActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarm.id)
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this,
            0,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action: Mute (Power button simulation in notification)
        val muteIntent = Intent(this, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_MUTE_ALARM
            putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarm.id)
        }
        val mutePendingIntent = PendingIntent.getBroadcast(
            this,
            1,
            muteIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action: Dismiss
        val dismissIntent = Intent(this, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_DISMISS_ALARM
            putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarm.id)
        }
        val dismissPendingIntent = PendingIntent.getBroadcast(
            this,
            2,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action: Snooze
        val snoozeIntent = Intent(this, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_SNOOZE_ALARM
            putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarm.id)
        }
        val snoozePendingIntent = PendingIntent.getBroadcast(
            this,
            3,
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (isMuted) {
            getString(R.string.notif_muted_title, remainingSeconds)
        } else {
            getString(R.string.notif_ringing_title, alarm.label)
        }

        val content = if (isMuted) {
            getString(R.string.notif_muted_content)
        } else {
            getString(R.string.notif_ringing_content)
        }

        val builder = NotificationCompat.Builder(this, AlarmApplication.ALARM_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(content)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(fullScreenPendingIntent)

        if (isMuted) {
            builder.setPriority(NotificationCompat.PRIORITY_DEFAULT)
            builder.setCategory(NotificationCompat.CATEGORY_STATUS)
            builder.setSilent(true)
            builder.setOnlyAlertOnce(true)
            // CRITICAL: DO NOT setFullScreenIntent when muted, so screen is allowed to turn off and stay off!
            builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, getString(R.string.dismiss_with_unlock), dismissPendingIntent)
        } else {
            builder.setPriority(NotificationCompat.PRIORITY_MAX)
            builder.setCategory(NotificationCompat.CATEGORY_ALARM)
            builder.setVibrate(longArrayOf(0))
            builder.setFullScreenIntent(fullScreenPendingIntent, true)
            builder.addAction(android.R.drawable.ic_lock_silent_mode, getString(R.string.ringing_smart_mute_title), mutePendingIntent)
            builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, getString(R.string.dismiss), dismissPendingIntent)
            builder.addAction(android.R.drawable.ic_popup_sync, getString(R.string.snooze), snoozePendingIntent)
        }

        return builder.build()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAlarmAndCleanup()
    }
}
