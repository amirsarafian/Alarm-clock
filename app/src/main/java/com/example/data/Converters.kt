package com.example.data

import androidx.room.TypeConverter
import com.example.model.LogLevel

class Converters {
    @TypeConverter
    fun fromIntList(list: List<Int>?): String {
        return list?.joinToString(",") ?: ""
    }

    @TypeConverter
    fun toIntList(data: String?): List<Int> {
        if (data.isNullOrEmpty()) return emptyList()
        return data.split(",").mapNotNull { it.trim().toIntOrNull() }
    }

    @TypeConverter
    fun fromLogLevel(level: LogLevel): String {
        return level.name
    }

    @TypeConverter
    fun toLogLevel(data: String?): LogLevel {
        return try {
            if (data != null) LogLevel.valueOf(data) else LogLevel.INFO
        } catch (e: Exception) {
            LogLevel.INFO
        }
    }
}
