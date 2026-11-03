package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DisconnectLogDao {
    @Query("SELECT * FROM disconnect_logs ORDER BY startTime DESC")
    fun getAllLogs(): Flow<List<DisconnectLogEntity>>

    @Query("SELECT * FROM disconnect_logs WHERE roomId = :roomId ORDER BY startTime DESC")
    fun getLogsForRoom(roomId: String): Flow<List<DisconnectLogEntity>>

    @Query("SELECT * FROM disconnect_logs WHERE endTime IS NULL ORDER BY startTime DESC LIMIT 1")
    suspend fun getActiveUnresolvedLog(): DisconnectLogEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: DisconnectLogEntity): Long

    @Update
    suspend fun updateLog(log: DisconnectLogEntity)

    @Delete
    suspend fun deleteLog(log: DisconnectLogEntity)

    @Query("DELETE FROM disconnect_logs")
    suspend fun clearAllLogs()

    @Query("SELECT COUNT(*) FROM disconnect_logs")
    fun getTotalDisconnectsCount(): Flow<Int>
}
