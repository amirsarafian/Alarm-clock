package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.LockClock
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AlarmItem

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AlarmEditDialog(
    alarm: AlarmItem?,
    onDismiss: () -> Unit,
    onSave: (AlarmItem) -> Unit
) {
    val initialHour = alarm?.hour ?: 7
    val initialMinute = alarm?.minute ?: 30

    val timePickerState = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = initialMinute,
        is24Hour = true
    )

    var label by remember { mutableStateOf(alarm?.label ?: "صبح بخیر") }
    var volume by remember { mutableIntStateOf(alarm?.volume ?: 85) }
    var isGradualVolume by remember { mutableStateOf(alarm?.isGradualVolume ?: true) }
    var isSmartMuteEnabled by remember { mutableStateOf(alarm?.isSmartMuteEnabled ?: true) }
    var vibrate by remember { mutableStateOf(alarm?.vibrate ?: true) }

    val selectedDays = remember {
        mutableStateListOf<Int>().apply {
            if (alarm != null) {
                addAll(alarm.daysOfWeek)
            }
        }
    }

    // Days mapping: 6=شنبه, 7=یکشنبه, 1=دوشنبه, 2=سه‌شنبه, 3=چهارشنبه, 4=پنج‌شنبه, 5=جمعه
    val daysList = listOf(
        6 to "شنبه",
        7 to "۱شنبه",
        1 to "۲شنبه",
        2 to "۳شنبه",
        3 to "۴شنبه",
        4 to "۵شنبه",
        5 to "جمعه"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (alarm == null) "افزودن آلارم جدید" else "ویرایش آلارم",
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Time Picker
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    TimePicker(
                        state = timePickerState,
                        modifier = Modifier.testTag("time_picker")
                    )
                }

                // Label
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("عنوان آلارم") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("alarm_label_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                // Days of week
                Text(
                    text = "روزهای تکرار (خالی = یک‌بار):",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    daysList.forEach { (dayCode, dayName) ->
                        val isSelected = selectedDays.contains(dayCode)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .clickable {
                                    if (isSelected) selectedDays.remove(dayCode)
                                    else selectedDays.add(dayCode)
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = dayName,
                                fontSize = 12.sp,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Requirement 5: Dedicated volume for each alarm
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.VolumeUp,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "میزان صدای اختصاصی این آلارم:",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Text(
                                text = "$volume%",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Slider(
                            value = volume.toFloat(),
                            onValueChange = { volume = it.toInt() },
                            valueRange = 10f..100f,
                            steps = 18,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("alarm_volume_slider")
                        )
                    }
                }

                // Requirement 4: Gradual Volume Increase (Crescendo)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.GraphicEq,
                                contentDescription = null,
                                tint = Color(0xFF00B0FF),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "افزایش تدریجی صدا (Crescendo)",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "شروع با صدای ملایم و افزایش پیوسته در ۳۰ ثانیه",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = isGradualVolume,
                            onCheckedChange = { isGradualVolume = it },
                            modifier = Modifier.testTag("crescendo_switch")
                        )
                    }
                }

                // Requirement 6: Smart Mute & Unlock-to-dismiss mode
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.LockClock,
                                contentDescription = null,
                                tint = Color(0xFFFF9100),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "سکوت با پاور + الزام بازگشایی قفل",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "امکان سایلنت با پاور، اما تا ۱ دقیقه باید قفل گوشی باز شود وگرنه دوباره زنگ می‌زند.",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = isSmartMuteEnabled,
                            onCheckedChange = { isSmartMuteEnabled = it },
                            modifier = Modifier.testTag("smart_mute_switch")
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val newAlarm = AlarmItem(
                        id = alarm?.id ?: 0L,
                        hour = timePickerState.hour,
                        minute = timePickerState.minute,
                        label = label.ifBlank { "آلارم" },
                        isEnabled = true,
                        daysOfWeek = selectedDays.toList(),
                        volume = volume,
                        isGradualVolume = isGradualVolume,
                        gradualDurationSeconds = 30,
                        isSmartMuteEnabled = isSmartMuteEnabled,
                        vibrate = vibrate
                    )
                    onSave(newAlarm)
                },
                modifier = Modifier.testTag("save_alarm_button")
            ) {
                Text("ذخیره آلارم")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("cancel_alarm_button")
            ) {
                Text("انصراف")
            }
        }
    )
}
