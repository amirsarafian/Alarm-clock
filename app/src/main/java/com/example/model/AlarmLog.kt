package com.example.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class LogLevel {
    INFO,
    WARNING,
    ERROR,
    SUCCESS
}

@Entity(tableName = "alarm_logs")
data class AlarmLog(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val alarmId: Long,
    val alarmLabel: String,
    val timestamp: Long = System.currentTimeMillis(),
    val eventType: String, // e.g. "TRIGGERED", "MUTED_POWER_BUTTON", "DISMISSED_UNLOCKED", "SNOOZED", "RESUMED_UNANSWERED", "SCHEDULED", "MISSED_OR_DELAYED", "SYSTEM_AUDIT"
    val scheduledTime: Long = 0L,
    val actualTime: Long = 0L,
    val delayMs: Long = 0L,
    val level: LogLevel = LogLevel.INFO,
    val message: String,
    val technicalReason: String = ""
) {
    fun getFormattedDate(): String {
        val sdf = SimpleDateFormat("HH:mm:ss - yyyy/MM/dd", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    fun getFormattedTime(): String {
        val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
}
