package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.model.AlarmLog
import kotlinx.coroutines.flow.Flow

@Dao
interface AlarmLogDao {
    @Query("SELECT * FROM alarm_logs ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<AlarmLog>>

    @Query("SELECT * FROM alarm_logs WHERE alarmId = :alarmId ORDER BY timestamp DESC")
    fun getLogsForAlarm(alarmId: Long): Flow<List<AlarmLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: AlarmLog): Long

    @Query("DELETE FROM alarm_logs WHERE id = :id")
    suspend fun deleteLogById(id: Long)

    @Query("DELETE FROM alarm_logs")
    suspend fun clearAllLogs()

    @Query("SELECT COUNT(*) FROM alarm_logs")
    suspend fun getLogCount(): Int
}
