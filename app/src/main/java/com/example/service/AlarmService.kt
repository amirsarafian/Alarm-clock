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
import com.example.AlarmApplication
import com.example.data.AppDatabase
import com.example.model.AlarmItem
import com.example.model.AlarmLog
import com.example.model.LogLevel
import com.example.receiver.AlarmReceiver
import com.example.scheduler.AlarmScheduler
import com.example.ui.ringing.AlarmRingingActivity
import com.example.util.DiagnosticHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AlarmService : Service() {

    companion object {
        const val NOTIFICATION_ID = 2001

        private val _currentAlarmState = MutableStateFlow<AlarmRingingState?>(null)
        val currentAlarmState: StateFlow<AlarmRingingState?> = _currentAlarmState.asStateFlow()
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
            val db = AppDatabase.getDatabase(applicationContext)
            val alarm = if (alarmId > 0) db.alarmDao().getAlarmById(alarmId) else null
            val resolvedAlarm = alarm ?: AlarmItem(
                id = if (alarmId > 0) alarmId else 1L,
                hour = 0,
                minute = 0,
                label = "آلارم بیدارباش",
                volume = 85,
                isGradualVolume = true,
                isSmartMuteEnabled = true
            )

            currentAlarm = resolvedAlarm
            val actualTime = System.currentTimeMillis()
            val delayMs = if (scheduledTime > 0) actualTime - scheduledTime else 0L

            // Log trigger event with exact delay evaluation
            val report = DiagnosticHelper.checkSystemStatus(applicationContext)
            val isSevereDelay = delayMs > 5000L
            val delaySeconds = delayMs / 1000.0

            val logMsg = if (scheduledTime > 0) {
                if (isSevereDelay) {
                    "هشدار: آلارم «${resolvedAlarm.label}» با تاخیر %.1f ثانیه‌ای به صدا درآمد! دلایل احتمالی: محدودیت باتری یا خواب عمیق سیستم.".format(delaySeconds)
                } else {
                    "آلارم «${resolvedAlarm.label}» با موفقیت و در زمان دقیق به صدا درآمد. (اختلاف زمان: ${delayMs} میلی‌ثانیه)"
                }
            } else {
                "آلارم «${resolvedAlarm.label}» به صدا درآمد."
            }

            val log = AlarmLog(
                alarmId = resolvedAlarm.id,
                alarmLabel = resolvedAlarm.label,
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
                // Crescendo: start very soft (5%) and ramp up smoothly
                val startVolume = 0.05f
                mediaPlayer?.setVolume(startVolume, startVolume)
                currentVolumePercent = 5
                mediaPlayer?.prepare()
                mediaPlayer?.start()

                startGradualVolumeRamp(targetVolumeFraction, alarm.gradualDurationSeconds)
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

    private fun startGradualVolumeRamp(targetVolumeFraction: Float, durationSec: Int) {
        gradualVolumeJob?.cancel()
        gradualVolumeJob = serviceScope.launch {
            val totalSteps = durationSec.coerceAtLeast(5)
            val stepTimeMs = 1000L
            val volumeIncrement = (targetVolumeFraction - 0.05f) / totalSteps

            var currentVol = 0.05f
            for (step in 1..totalSteps) {
                delay(stepTimeMs)
                if (isMuted) break // muted
                currentVol = (currentVol + volumeIncrement).coerceAtMost(targetVolumeFraction)
                mediaPlayer?.setVolume(currentVol, currentVol)
                currentVolumePercent = (currentVol * 100).toInt()
                updateState()
            }
            if (!isMuted) {
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
     * Requirement 6:
     * When user presses Power button / Screen Off, mute alarm sound immediately to avoid disturbing others.
     * Starts a 60-second timer during which the user must unlock phone to permanently dismiss.
     * If 60 seconds expire and phone has not been unlocked, resumes sound at full volume!
     */
    private fun handlePowerButtonMute() {
        val alarm = currentAlarm ?: return
        if (!alarm.isSmartMuteEnabled) return

        if (isMuted) return // already in mute countdown

        isMuted = true
        gradualVolumeJob?.cancel()

        // Silence audio & vibration immediately
        try {
            mediaPlayer?.setVolume(0f, 0f)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        stopVibration()

        remainingMuteSeconds = 60
        updateState()

        // Update foreground notification with mute warning
        val notification = buildForegroundNotification(alarm, isMuted = true, remainingSeconds = 60)
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        nm.notify(NOTIFICATION_ID, notification)

        // Log mute event
        serviceScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(applicationContext)
            val log = AlarmLog(
                alarmId = alarm.id,
                alarmLabel = alarm.label,
                timestamp = System.currentTimeMillis(),
                eventType = "MUTED_POWER_BUTTON",
                level = LogLevel.WARNING,
                message = "آلارم موقتاً با دکمه پاور/خاموشی صفحه بی‌صدا شد. کاربر ۶۰ ثانیه مهلت دارد قفل گوشی را باز کند و آلارم را قطع کند.",
                technicalReason = "ACTION_SCREEN_OFF detected. Smart mute 60s countdown engaged."
            )
            db.alarmLogDao().insertLog(log)
        }

        // 60-second countdown
        smartMuteJob?.cancel()
        smartMuteJob = serviceScope.launch {
            for (sec in 60 downTo 1) {
                remainingMuteSeconds = sec
                updateState()
                if (sec % 5 == 0) {
                    val notif = buildForegroundNotification(alarm, isMuted = true, remainingSeconds = sec)
                    nm.notify(NOTIFICATION_ID, notif)
                }
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

        // Resume at full target volume!
        val targetVolumePercent = alarm.volume.coerceIn(10, 100)
        val targetFraction = targetVolumePercent / 100f
        try {
            mediaPlayer?.setVolume(targetFraction, targetFraction)
            currentVolumePercent = targetVolumePercent
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

        // Log resume event
        serviceScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(applicationContext)
            val log = AlarmLog(
                alarmId = alarm.id,
                alarmLabel = alarm.label,
                timestamp = System.currentTimeMillis(),
                eventType = "RESUMED_UNANSWERED",
                level = LogLevel.ERROR,
                message = "مهلت ۱ دقیقه‌ای پایان یافت اما قفل گوشی باز نشد! آلارم با صدای کامل مجدداً به صدا درآمد تا کاربر بیدار شود.",
                technicalReason = "60-second mute countdown elapsed without lockscreen dismissal."
            )
            db.alarmLogDao().insertLog(log)
        }
    }

    private fun handleDeviceUnlocked() {
        val alarm = currentAlarm ?: return
        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        val isLocked = keyguardManager.isKeyguardLocked

        if (!isLocked) {
            // Unlocked! Dismiss alarm permanently
            dismissAlarmSuccessfully("قفل گوشی باز شد و آلارم با موفقیت متوقف شد.")
        }
    }

    private fun handleDismissRequest() {
        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (keyguardManager.isKeyguardLocked) {
            // Still locked, open full screen so user unlocks
            val ringingIntent = Intent(applicationContext, AlarmRingingActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            }
            startActivity(ringingIntent)
        } else {
            dismissAlarmSuccessfully("آلارم توسط کاربر با باز بودن قفل گوشی به طور کامل قطع شد.")
        }
    }

    private fun handleSnooze() {
        val alarm = currentAlarm ?: return
        serviceScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(applicationContext)
            val log = AlarmLog(
                alarmId = alarm.id,
                alarmLabel = alarm.label,
                timestamp = System.currentTimeMillis(),
                eventType = "SNOOZED",
                level = LogLevel.INFO,
                message = "آلارم «${alarm.label}» برای ۵ دقیقه به تعویق افتاد (Snooze)."
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
            "آلارم بی‌صدا شد ($remainingSeconds ثانیه باقی‌مانده)"
        } else {
            "آلارم در حال زنگ زدن: ${alarm.label}"
        }

        val content = if (isMuted) {
            "تا ۱ دقیقه قفل گوشی را باز کرده و قطع کنید؛ در غیر این صورت دوباره با صدای بلند زنگ می‌زند."
        } else {
            "برای بی‌صدا کردن موقت دکمه پاور را بفشارید. برای قطع دائم قفل گوشی را باز کنید."
        }

        val builder = NotificationCompat.Builder(this, AlarmApplication.ALARM_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setContentIntent(fullScreenPendingIntent)

        if (isMuted) {
            builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "قطع کامل (باز کردن قفل)", dismissPendingIntent)
        } else {
            builder.addAction(android.R.drawable.ic_lock_silent_mode, "بی‌صدا کردن (۱ دقیقه)", mutePendingIntent)
            builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "قطع آلارم", dismissPendingIntent)
            builder.addAction(android.R.drawable.ic_popup_sync, "تعویق (۵ دقیقه)", snoozePendingIntent)
        }

        return builder.build()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAlarmAndCleanup()
    }
}
