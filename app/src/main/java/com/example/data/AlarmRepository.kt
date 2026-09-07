package com.example.data

import com.example.model.AlarmItem
import com.example.model.AlarmLog
import kotlinx.coroutines.flow.Flow

class AlarmRepository(
    private val alarmDao: AlarmDao,
    private val alarmLogDao: AlarmLogDao
) {
    val allAlarms: Flow<List<AlarmItem>> = alarmDao.getAllAlarms()
    val allLogs: Flow<List<AlarmLog>> = alarmLogDao.getAllLogs()

    suspend fun getAlarmById(id: Long): AlarmItem? = alarmDao.getAlarmById(id)

    suspend fun getEnabledAlarmsSync(): List<AlarmItem> = alarmDao.getEnabledAlarmsSync()

    suspend fun insertAlarm(alarm: AlarmItem): Long = alarmDao.insertAlarm(alarm)

    suspend fun updateAlarm(alarm: AlarmItem) = alarmDao.updateAlarm(alarm)

    suspend fun deleteAlarm(alarm: AlarmItem) = alarmDao.deleteAlarm(alarm)

    suspend fun deleteAlarmById(id: Long) = alarmDao.deleteAlarmById(id)

    suspend fun setAlarmEnabled(id: Long, isEnabled: Boolean) = alarmDao.setAlarmEnabled(id, isEnabled)

    suspend fun insertLog(log: AlarmLog): Long = alarmLogDao.insertLog(log)

    suspend fun clearAllLogs() = alarmLogDao.clearAllLogs()

    suspend fun deleteLogById(id: Long) = alarmLogDao.deleteLogById(id)
}
