package com.example.repository

import com.example.data.local.DisconnectLogDao
import com.example.data.local.DisconnectLogEntity
import kotlinx.coroutines.flow.Flow

class NetAlertRepository(private val dao: DisconnectLogDao) {

    val allLogs: Flow<List<DisconnectLogEntity>> = dao.getAllLogs()
    val totalDisconnects: Flow<Int> = dao.getTotalDisconnectsCount()

    suspend fun insertLog(log: DisconnectLogEntity): Long = dao.insertLog(log)

    suspend fun updateLog(log: DisconnectLogEntity) = dao.updateLog(log)

    suspend fun deleteLog(log: DisconnectLogEntity) = dao.deleteLog(log)

    suspend fun clearAllLogs() = dao.clearAllLogs()
}
