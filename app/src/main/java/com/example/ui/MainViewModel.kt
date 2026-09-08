package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.AlarmRepository
import com.example.model.AlarmItem
import com.example.model.AlarmLog
import com.example.model.LogLevel
import com.example.scheduler.AlarmScheduler
import com.example.util.DiagnosticHelper
import com.example.util.SystemStatusReport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: AlarmRepository
    val alarms: StateFlow<List<AlarmItem>>
    val logs: StateFlow<List<AlarmLog>>

    private val _systemReport = MutableStateFlow(DiagnosticHelper.checkSystemStatus(application))
    val systemReport: StateFlow<SystemStatusReport> = _systemReport.asStateFlow()

    init {
        val db = AppDatabase.getDatabase(application)
        repository = AlarmRepository(db.alarmDao(), db.alarmLogDao())

        alarms = repository.allAlarms.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        logs = repository.allLogs.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        refreshSystemReport()
    }

    fun refreshSystemReport() {
        val report = DiagnosticHelper.checkSystemStatus(getApplication())
        _systemReport.value = report
    }

    fun saveAlarm(alarm: AlarmItem) {
        viewModelScope.launch {
            val id = if (alarm.id == 0L) {
                repository.insertAlarm(alarm)
            } else {
                repository.updateAlarm(alarm)
                alarm.id
            }

            val updated = alarm.copy(id = id)
            if (updated.isEnabled) {
                AlarmScheduler.scheduleAlarm(getApplication(), updated)
            } else {
                AlarmScheduler.cancelAlarm(getApplication(), updated)
            }
        }
    }

    fun toggleAlarm(alarm: AlarmItem, isEnabled: Boolean) {
        viewModelScope.launch {
            val updated = alarm.copy(isEnabled = isEnabled)
            repository.updateAlarm(updated)

            if (isEnabled) {
                AlarmScheduler.scheduleAlarm(getApplication(), updated)
            } else {
                AlarmScheduler.cancelAlarm(getApplication(), updated)
            }
        }
    }

    fun deleteAlarm(alarm: AlarmItem) {
        viewModelScope.launch {
            AlarmScheduler.cancelAlarm(getApplication(), alarm)
            repository.deleteAlarm(alarm)
        }
    }

    fun clearLogs() {
        viewModelScope.launch {
            repository.clearAllLogs()
        }
    }

    fun runSystemAudit() {
        viewModelScope.launch {
            val report = DiagnosticHelper.checkSystemStatus(getApplication())
            _systemReport.value = report

            val level = if (report.canScheduleExactAlarms && report.isBatteryOptimizationIgnored && report.areNotificationsEnabled) {
                LogLevel.SUCCESS
            } else {
                LogLevel.WARNING
            }

            val isPersian = com.example.util.LocaleHelper.isPersian(getApplication())
            val log = AlarmLog(
                alarmId = 0L,
                alarmLabel = if (isPersian) "بررسی سلامت سیستم" else "System Health Audit",
                timestamp = System.currentTimeMillis(),
                eventType = "SYSTEM_AUDIT",
                level = level,
                message = report.summaryPersian,
                technicalReason = report.technicalDetails
            )
            repository.insertLog(log)
        }
    }

    fun createTestAlarmInSeconds(seconds: Int = 10) {
        viewModelScope.launch {
            val isPersian = com.example.util.LocaleHelper.isPersian(getApplication())
            val cal = Calendar.getInstance().apply {
                add(Calendar.SECOND, seconds)
            }
            val testAlarm = AlarmItem(
                hour = cal.get(Calendar.HOUR_OF_DAY),
                minute = cal.get(Calendar.MINUTE),
                label = if (isPersian) "تست زنگ دقیق ($seconds ثانیه دیگر)" else "Test Alarm (in $seconds sec)",
                isEnabled = true,
                daysOfWeek = emptyList(),
                volume = 80,
                isGradualVolume = true,
                gradualDurationSeconds = 15,
                isSmartMuteEnabled = true,
                vibrate = false
            )
            val id = repository.insertAlarm(testAlarm)
            AlarmScheduler.scheduleAlarm(getApplication(), testAlarm.copy(id = id))
        }
    }
}
