package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface VpnServerDao {
    @Query("SELECT * FROM vpn_servers ORDER BY score DESC")
    fun getAllServers(): Flow<List<VpnServerEntity>>

    @Query("SELECT * FROM vpn_servers ORDER BY score DESC")
    suspend fun getAllServersSnapshot(): List<VpnServerEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(servers: List<VpnServerEntity>)

    @Query("DELETE FROM vpn_servers")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM vpn_servers")
    fun getServerCount(): Flow<Int>

    @Query("SELECT * FROM vpn_servers WHERE id = :id LIMIT 1")
    suspend fun getServerById(id: String): VpnServerEntity?
}
