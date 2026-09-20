package com.example.bugtracker

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface IssueDao {

    @Query("SELECT * FROM issues WHERE pendingDelete = 0 ORDER BY creationDate DESC")
    fun getAllIssues(): Flow<List<Issue>>

    @Query("SELECT * FROM issues WHERE localId = :id")
    suspend fun getIssueById(id: Int): Issue?

    @Query("SELECT * FROM issues WHERE serverId = :serverId LIMIT 1")
    suspend fun getIssueByServerId(serverId: String): Issue?

    @Query("SELECT * FROM issues")
    suspend fun getAllOnce(): List<Issue>

    @Query("SELECT * FROM issues WHERE syncStatus != 'SYNCED'")
    suspend fun getUnsyncedIssues(): List<Issue>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(issue: Issue): Long

    @Update
    suspend fun update(issue: Issue)

    @Delete
    suspend fun delete(issue: Issue)
}