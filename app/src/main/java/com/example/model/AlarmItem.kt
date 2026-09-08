package com.example.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "alarms")
data class AlarmItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val hour: Int,
    val minute: Int,
    val label: String = "",
    val isEnabled: Boolean = true,
    // Days of week (1 = Monday, ..., 7 = Sunday). Empty list means one-time alarm.
    val daysOfWeek: List<Int> = emptyList(),
    // Custom volume percentage: 10 to 100
    val volume: Int = 85,
    // Gradual volume increase (Crescendo)
    val isGradualVolume: Boolean = true,
    val gradualDurationSeconds: Int = 30,
    // Smart Mute via power button: silences sound immediately, but requires unlocking phone within 1 minute
    val isSmartMuteEnabled: Boolean = true,
    val vibrate: Boolean = false,
    val ringtoneUri: String = ""
) {
    fun getFormattedTime(): String {
        val h = String.format("%02d", hour)
        val m = String.format("%02d", minute)
        return "$h:$m"
    }

    fun getDaysDescription(isPersian: Boolean = java.util.Locale.getDefault().language == "fa"): String {
        if (daysOfWeek.isEmpty()) return if (isPersian) "یک‌بار" else "Once"
        if (daysOfWeek.size == 7) return if (isPersian) "هر روز" else "Every day"
        if (daysOfWeek.containsAll(listOf(6, 7)) && daysOfWeek.size == 2) {
            return if (isPersian) "آخر هفته" else "Weekends"
        }
        val faNames = mapOf(6 to "ش", 7 to "ی", 1 to "د", 2 to "س", 3 to "چ", 4 to "پ", 5 to "ج")
        val enNames = mapOf(1 to "Mon", 2 to "Tue", 3 to "Wed", 4 to "Thu", 5 to "Fri", 6 to "Sat", 7 to "Sun")
        return if (isPersian) {
            daysOfWeek.sorted().joinToString("، ") { faNames[it] ?: "$it" }
        } else {
            daysOfWeek.sorted().joinToString(", ") { enNames[it] ?: "$it" }
        }
    }
}
