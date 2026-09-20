package com.example.bugtracker

import androidx.room.Entity
import androidx.room.PrimaryKey

object Priority {
    const val LOW = "LOW"
    const val MEDIUM = "MEDIUM"
    const val HIGH = "HIGH"
    val all = listOf(LOW, MEDIUM, HIGH)
}

object Status {
    const val OPEN = "OPEN"
    const val IN_PROGRESS = "IN_PROGRESS"
    const val CLOSED = "CLOSED"
    val all = listOf(OPEN, IN_PROGRESS, CLOSED)
}

object SyncState {
    const val PENDING = "PENDING"
    const val SYNCED = "SYNCED"
    const val CONFLICT = "CONFLICT"
    const val FAILED = "FAILED"
}

@Entity(tableName = "issues")
data class Issue(
    @PrimaryKey(autoGenerate = true) val localId: Int = 0,
    val serverId: String? = null,
    val title: String,
    val description: String,
    val priority: String = Priority.MEDIUM,
    val status: String = Status.OPEN,
    val creationDate: Long = System.currentTimeMillis(),
    val lastUpdated: Long = System.currentTimeMillis(),
    val syncStatus: String = SyncState.PENDING,
    val pendingDelete: Boolean = false,
    val isDraft: Boolean = false
)