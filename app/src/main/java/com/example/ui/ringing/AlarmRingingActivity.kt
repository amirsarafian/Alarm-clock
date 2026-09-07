package com.example.ui.ringing

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.receiver.AlarmReceiver
import com.example.service.AlarmService
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AlarmRingingActivity : ComponentActivity() {

    private val confirmCredentialLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            // Secure credential (PIN, pattern, password, or biometric) succeeded!
            sendDismissBroadcast()
            finish()
        } else {
            Toast.makeText(
                this,
                getString(R.string.auth_required_toast),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setupLockscreenWake()

        if (intent?.getBooleanExtra("REQUEST_UNLOCK_CHALLENGE", false) == true) {
            attemptDismissWithUnlock()
        }

        setContent {
            MyApplicationTheme(darkTheme = true) {
                val state by AlarmService.currentAlarmState.collectAsStateWithLifecycle()

                // If service stopped, close ringing screen
                LaunchedEffect(state) {
                    if (state == null) {
                        finish()
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF0F111A)
                ) {
                    RingingScreenContent(
                        state = state,
                        onMuteClick = {
                            val muteIntent = Intent(this@AlarmRingingActivity, AlarmReceiver::class.java).apply {
                                action = AlarmReceiver.ACTION_MUTE_ALARM
                            }
                            sendBroadcast(muteIntent)
                        },
                        onSnoozeClick = {
                            val snoozeIntent = Intent(this@AlarmRingingActivity, AlarmReceiver::class.java).apply {
                                action = AlarmReceiver.ACTION_SNOOZE_ALARM
                            }
                            sendBroadcast(snoozeIntent)
                            finish()
                        },
                        onDismissClick = {
                            attemptDismissWithUnlock()
                        }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra("REQUEST_UNLOCK_CHALLENGE", false)) {
            attemptDismissWithUnlock()
        }
    }

    private fun setupLockscreenWake() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // Intercept volume buttons to trigger smart mute
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            val muteIntent = Intent(this, AlarmReceiver::class.java).apply {
                action = AlarmReceiver.ACTION_MUTE_ALARM
            }
            sendBroadcast(muteIntent)
            Toast.makeText(this, getString(R.string.smart_muted_banner), Toast.LENGTH_SHORT).show()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun attemptDismissWithUnlock() {
        val km = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (km.isDeviceSecure) {
            // User configured secure PIN, pattern, password, or biometrics.
            // If device is locked, launch the secure credential challenge!
            if (km.isDeviceLocked) {
                val authIntent = km.createConfirmDeviceCredentialIntent(
                    getString(R.string.auth_prompt_title),
                    getString(R.string.auth_prompt_desc)
                )
                if (authIntent != null) {
                    confirmCredentialLauncher.launch(authIntent)
                    return
                }
            }
        }

        // If not locked or device doesn't have secure lock, check keyguard
        if (km.isKeyguardLocked) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                km.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
                    override fun onDismissSucceeded() {
                        super.onDismissSucceeded()
                        sendDismissBroadcast()
                        finish()
                    }

                    override fun onDismissCancelled() {
                        super.onDismissCancelled()
                        Toast.makeText(
                            this@AlarmRingingActivity,
                            getString(R.string.auth_required_toast),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                })
            } else {
                sendDismissBroadcast()
                finish()
            }
        } else {
            // Already fully unlocked
            sendDismissBroadcast()
            finish()
        }
    }

    private fun sendDismissBroadcast() {
        val dismissIntent = Intent(this, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_DISMISS_ALARM
        }
        sendBroadcast(dismissIntent)
    }
}

@Composable
fun RingingScreenContent(
    state: AlarmService.AlarmRingingState?,
    onMuteClick: () -> Unit,
    onSnoozeClick: () -> Unit,
    onDismissClick: () -> Unit
) {
    var currentTimeString by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        while (true) {
            val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
            currentTimeString = sdf.format(Date())
            delay(1000L)
        }
    }

    val isMuted = state?.isMuted == true
    val remainingSeconds = state?.muteRemainingSeconds ?: 60
    val volumePercent = state?.currentVolumePercent ?: 0
    val isPersian = Locale.getDefault().language == "fa"
    val defaultLabel = stringResource(R.string.app_name)
    val alarmLabel = state?.alarm?.label?.ifEmpty { defaultLabel } ?: defaultLabel

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isMuted) 1f else 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top Section: App info & Status
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Alarm,
                    contentDescription = null,
                    tint = if (isMuted) Color(0xFFFFB74D) else Color(0xFF00E5FF),
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isMuted) stringResource(R.string.ringing_smart_mute_title) else stringResource(R.string.ringing_active_title),
                    color = if (isMuted) Color(0xFFFFB74D) else Color(0xFF80D8FF),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Big Clock Display
            Text(
                text = currentTimeString.ifEmpty { "00:00" },
                fontSize = 76.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = (-2).sp,
                color = Color.White
            )

            Text(
                text = alarmLabel,
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFFE0E0E0),
                modifier = Modifier.padding(top = 4.dp)
            )

            // Volume Indicator (Crescendo feedback)
            if (!isMuted) {
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.VolumeUp,
                        contentDescription = null,
                        tint = Color(0xFF00E5FF),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isPersian) "صدای زنگ: $volumePercent%" else "Volume: $volumePercent%",
                        color = Color(0xFFB0BEC5),
                        fontSize = 13.sp
                    )
                }
            }
        }

        // Center Pulsing Graphic / Smart Mute Status Card
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
        ) {
            if (isMuted) {
                // Smart Mute Countdown Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("smart_mute_banner_card"),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF261D12)
                    ),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = Brush.horizontalGradient(
                            listOf(Color(0xFFFF9800), Color(0xFFFF5722))
                        )
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.VolumeMute,
                            contentDescription = null,
                            tint = Color(0xFFFFB74D),
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.ringing_smart_mute_title),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFFCC80)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.ringing_muted_banner_text, remainingSeconds),
                            fontSize = 13.sp,
                            color = Color(0xFFFFE0B2),
                            textAlign = TextAlign.Center,
                            lineHeight = 20.sp
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = if (isPersian) "$remainingSeconds ثانیه" else "$remainingSeconds s",
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFF9800)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { remainingSeconds / 60f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = Color(0xFFFF9800),
                            trackColor = Color(0xFF422E1A)
                        )
                    }
                }
            } else {
                // Ringing Bell with dynamic pulse
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(170.dp)
                        .scale(pulseScale)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                listOf(
                                    Color(0x3300E5FF),
                                    Color(0x1000E5FF),
                                    Color.Transparent
                                )
                            )
                        )
                        .border(2.dp, Color(0x6600E5FF), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.NotificationsActive,
                        contentDescription = null,
                        tint = Color(0xFF00E5FF),
                        modifier = Modifier.size(70.dp)
                    )
                }
            }
        }

        // Bottom Section: Actions
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Option to mute with Power button or direct button
            if (!isMuted) {
                OutlinedButton(
                    onClick = onMuteClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("mute_button"),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color(0xFFFFCC80)
                    ),
                    border = ButtonDefaults.outlinedButtonBorder.copy(
                        brush = Brush.horizontalGradient(
                            listOf(Color(0xFFFFA726), Color(0xFFFF7043))
                        )
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.VolumeMute,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isPersian) "بی‌صدا کردن موقت (دکمه پاور)" else "Mute temporarily (Power button)",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Primary Dismiss Button: Requires unlocking device
            Button(
                onClick = onDismissClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .testTag("dismiss_button"),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF00C853)
                )
            ) {
                Icon(
                    imageVector = Icons.Default.LockOpen,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(horizontalAlignment = Alignment.Start) {
                    Text(
                        text = stringResource(R.string.dismiss),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = stringResource(R.string.dismiss_with_unlock),
                        fontSize = 11.sp,
                        color = Color(0xFFE8F5E9)
                    )
                }
            }

            // Secondary Snooze Button
            OutlinedButton(
                onClick = onSnoozeClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("snooze_button"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color(0xFF90CAF9)
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Snooze,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.snooze),
                    fontSize = 13.sp
                )
            }
        }
    }
}
