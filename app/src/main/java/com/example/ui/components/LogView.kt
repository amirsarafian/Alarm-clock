package com.example.ui.components

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AlarmLog
import com.example.model.LogLevel
import com.example.util.DiagnosticHelper
import com.example.util.SystemStatusReport

@Composable
fun LogView(
    logs: List<AlarmLog>,
    systemReport: SystemStatusReport,
    onRunAudit: () -> Unit,
    onClearLogs: () -> Unit,
    onTestAlarm: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedFilter by remember { mutableStateOf("ALL") }

    val filteredLogs = remember(logs, selectedFilter) {
        when (selectedFilter) {
            "WARNINGS" -> logs.filter { it.level == LogLevel.WARNING || it.level == LogLevel.ERROR }
            "DISMISS" -> logs.filter { it.eventType.contains("DISMISSED") }
            "MUTE" -> logs.filter { it.eventType.contains("MUTE") }
            else -> logs
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // System Diagnostics Status Card (Requirement 1 & 2)
        item {
            SystemHealthCard(
                report = systemReport,
                onRunAudit = onRunAudit,
                onFixExactAlarm = {
                    try {
                        context.startActivity(DiagnosticHelper.openExactAlarmSettingsIntent(context))
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                },
                onFixBattery = {
                    try {
                        context.startActivity(DiagnosticHelper.openBatteryOptimizationSettingsIntent(context))
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                },
                onFixNotification = {
                    try {
                        context.startActivity(DiagnosticHelper.openAppNotificationSettingsIntent(context))
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            )
        }

        // Action Buttons Row: Test Alarm & Clear Logs
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onTestAlarm,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("test_alarm_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "تست زنگ (۱۰ ثانیه)", fontSize = 12.sp)
                }

                OutlinedButton(
                    onClick = onClearLogs,
                    modifier = Modifier.testTag("clear_logs_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteSweep,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "پاکسازی", fontSize = 12.sp)
                }
            }
        }

        // Filter chips
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedFilter == "ALL",
                    onClick = { selectedFilter = "ALL" },
                    label = { Text("همه (${logs.size})", fontSize = 11.sp) }
                )
                FilterChip(
                    selected = selectedFilter == "WARNINGS",
                    onClick = { selectedFilter = "WARNINGS" },
                    label = { Text("خطاها و تاخیرها", fontSize = 11.sp) }
                )
                FilterChip(
                    selected = selectedFilter == "DISMISS",
                    onClick = { selectedFilter = "DISMISS" },
                    label = { Text("قطع شدن", fontSize = 11.sp) }
                )
                FilterChip(
                    selected = selectedFilter == "MUTE",
                    onClick = { selectedFilter = "MUTE" },
                    label = { Text("سکوت هوشمند", fontSize = 11.sp) }
                )
            }
        }

        // Empty state
        if (filteredLogs.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "هنوز لاگی ثبت نشده است.\nرویدادهای زنگ، قطع شدن، سکوت و تست سلامت در اینجا نمایش داده می‌شوند.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        lineHeight = 20.sp
                    )
                }
            }
        }

        // Logs items
        items(filteredLogs, key = { it.id }) { log ->
            LogItemCard(log = log)
        }
    }
}

@Composable
fun SystemHealthCard(
    report: SystemStatusReport,
    onRunAudit: () -> Unit,
    onFixExactAlarm: () -> Unit,
    onFixBattery: () -> Unit,
    onFixNotification: () -> Unit
) {
    val isAllGood = report.canScheduleExactAlarms && report.isBatteryOptimizationIgnored && report.areNotificationsEnabled

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("system_health_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isAllGood) {
                Color(0xFF1B2E1E)
            } else {
                Color(0xFF2E1B1B)
            }
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isAllGood) Icons.Default.HealthAndSafety else Icons.Default.WarningAmber,
                        contentDescription = null,
                        tint = if (isAllGood) Color(0xFF81C784) else Color(0xFFFF8A80),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isAllGood) "وضعیت سیستم: آماده و بدون مانع" else "نیازمند تنظیم برای دقت ۱۰۰٪",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = if (isAllGood) Color(0xFFC8E6C9) else Color(0xFFFFCDD2)
                    )
                }

                IconButton(onClick = onRunAudit) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "بررسی مجدد",
                        tint = if (isAllGood) Color(0xFF81C784) else Color(0xFFFF8A80)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Diagnostic checklist
            DiagnosticRow(
                title = "مجوز آلارم دقیق (Exact Alarm)",
                isOk = report.canScheduleExactAlarms,
                actionLabel = "فعال‌سازی",
                onFix = onFixExactAlarm
            )
            DiagnosticRow(
                title = "معافیت از محدودیت باتری (Doze Bypass)",
                isOk = report.isBatteryOptimizationIgnored,
                actionLabel = "رفع محدودیت",
                onFix = onFixBattery
            )
            DiagnosticRow(
                title = "اعلان‌ها و هشدار تمام‌صفحه",
                isOk = report.areNotificationsEnabled,
                actionLabel = "فعال‌سازی",
                onFix = onFixNotification
            )

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = report.summaryPersian,
                fontSize = 12.sp,
                color = if (isAllGood) Color(0xFFA5D6A7) else Color(0xFFEF9A9A),
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
private fun DiagnosticRow(
    title: String,
    isOk: Boolean,
    actionLabel: String,
    onFix: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = if (isOk) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                contentDescription = null,
                tint = if (isOk) Color(0xFF81C784) else Color(0xFFFF8A80),
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.9f)
            )
        }

        if (!isOk) {
            OutlinedButton(
                onClick = onFix,
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                modifier = Modifier.height(28.dp)
            ) {
                Text(text = actionLabel, fontSize = 10.sp, color = Color(0xFFFF8A80))
            }
        }
    }
}

@Composable
fun LogItemCard(log: AlarmLog) {
    var isExpanded by remember { mutableStateOf(false) }

    val (badgeColor, badgeText) = when (log.eventType) {
        "TRIGGERED" -> Color(0xFF00E676) to "زنگ سر وقت"
        "DELAY_WARNING" -> Color(0xFFFF5252) to "هشدار تاخیر"
        "MUTED_POWER_BUTTON" -> Color(0xFFFFAB00) to "سکوت دکمه پاور"
        "DISMISSED_UNLOCKED" -> Color(0xFF00E5FF) to "قطع با بازگشایی قفل"
        "RESUMED_UNANSWERED" -> Color(0xFFFF1744) to "زنگ مجدد (عدم بازگشایی)"
        "SNOOZED" -> Color(0xFF90CAF9) to "تعویق (Snooze)"
        "SCHEDULED" -> Color(0xFFB388FF) to "زمان‌بندی دقیق"
        "SYSTEM_AUDIT" -> Color(0xFF69F0AE) to "بررسی سیستم"
        "REBOOT_RESTORED" -> Color(0xFF40C4FF) to "بازیابی پس از ریبوت"
        else -> Color(0xFFB0BEC5) to log.eventType
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { isExpanded = !isExpanded }
            .testTag("log_card_${log.id}"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Event badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(badgeColor.copy(alpha = 0.2f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = badgeText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = badgeColor
                    )
                }

                // Timestamp
                Text(
                    text = log.getFormattedDate(),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = log.message,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 19.sp
            )

            // Delay metric if applicable
            if (log.delayMs != 0L) {
                Spacer(modifier = Modifier.height(4.dp))
                val isDelayed = log.delayMs > 3000L
                Text(
                    text = "میزان تاخیر سیستمی: ${log.delayMs} میلی‌ثانیه" +
                            if (isDelayed) " (بیش از حد نرمال؛ علت احتمالی: محدودیت باتری یا خواب عمیق)" else " (دقت ایده‌آل)",
                    fontSize = 11.sp,
                    color = if (isDelayed) Color(0xFFFF5252) else Color(0xFF00E676)
                )
            }

            // Expandable technical diagnostic
            if (log.technicalReason.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Text(
                        text = if (isExpanded) "بستن جزییات فنی" else "مشاهده دلایل فنی",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }

                AnimatedVisibility(visible = isExpanded) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(10.dp)
                    ) {
                        Text(
                            text = log.technicalReason,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }
    }
}
