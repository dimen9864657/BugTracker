package com.example.bugtracker

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException

fun isNetworkAvailable(context: Context): Boolean {
    val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val network = manager.activeNetwork ?: return false
    val capabilities = manager.getNetworkCapabilities(network) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}

class IssueRepository private constructor(private val appContext: Context) {

    private val dao: IssueDao = AppDatabase.getInstance(appContext).issueDao()
    private val api: IssueApi get() = ApiClient.api
    private val syncMutex = Mutex()

    val issues: Flow<List<Issue>> = dao.getAllIssues()

    suspend fun getIssue(localId: Int): Issue? = dao.getIssueById(localId)

    // ---------- Local first CRUD ----------

    suspend fun createIssue(
        title: String,
        description: String,
        priority: String,
        status: String,
        asDraft: Boolean
    ) {
        val now = System.currentTimeMillis()
        dao.insert(
            Issue(
                title = title.trim(),
                description = description.trim(),
                priority = priority,
                status = status,
                creationDate = now,
                lastUpdated = now,
                syncStatus = SyncState.PENDING,
                isDraft = asDraft
            )
        )
        if (!asDraft) SyncWorker.schedule(appContext)
    }

    suspend fun updateIssue(
        existing: Issue,
        title: String,
        description: String,
        priority: String,
        status: String,
        asDraft: Boolean
    ) {
        dao.update(
            existing.copy(
                title = title.trim(),
                description = description.trim(),
                priority = priority,
                status = status,
                lastUpdated = System.currentTimeMillis(),
                syncStatus = SyncState.PENDING,
                isDraft = asDraft
            )
        )
        if (!asDraft) SyncWorker.schedule(appContext)
    }

    suspend fun deleteIssue(issue: Issue) {
        val current = dao.getIssueById(issue.localId) ?: return
        if (current.serverId == null) {
            // Never reached the server, so just remove it here.
            dao.delete(current)
        } else {
            // Keep it hidden until the DELETE request succeeds.
            dao.update(
                current.copy(
                    pendingDelete = true,
                    isDraft = false,
                    syncStatus = SyncState.PENDING,
                    lastUpdated = System.currentTimeMillis()
                )
            )
            SyncWorker.schedule(appContext)
        }
    }

    // ---------- Two way sync ----------

    // Returns true when everything was sent and received without problems.
    suspend fun syncAll(): Boolean = syncMutex.withLock {
        var allGood = true

        val pending = dao.getUnsyncedIssues().filter { !it.isDraft }
        for (issue in pending) {
            if (!pushOne(issue)) allGood = false
        }

        val pulled = try {
            pullRemote()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }

        allGood && pulled
    }

    suspend fun markUnsyncedAsFailed() {
        dao.getUnsyncedIssues()
            .filter { !it.isDraft && it.syncStatus == SyncState.PENDING }
            .forEach { dao.update(it.copy(syncStatus = SyncState.FAILED)) }
    }

    private suspend fun pushOne(issue: Issue): Boolean {
        return try {
            when {
                issue.pendingDelete -> pushDelete(issue)
                issue.serverId == null -> pushCreate(issue)
                else -> pushUpdate(issue)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
    }

    private suspend fun pushCreate(issue: Issue): Boolean {
        val response = api.createIssue(issue.toDto())
        val newId = response.body()?.id
        if (response.isSuccessful && newId != null) {
            markSynced(issue, newId)
            return true
        }
        return false
    }

    private suspend fun pushUpdate(issue: Issue): Boolean {
        val serverId = issue.serverId ?: return false
        val response = api.updateIssue(serverId, issue.toDto())
        return when {
            response.isSuccessful -> {
                markSynced(issue, serverId)
                true
            }
            response.code() == 404 -> pushCreate(issue.copy(serverId = null))
            response.code() == 409 -> resolveConflict(issue, serverId)
            else -> false
        }
    }

    private suspend fun pushDelete(issue: Issue): Boolean {
        val serverId = issue.serverId
        if (serverId == null) {
            dao.delete(issue)
            return true
        }
        val response = api.deleteIssue(serverId)
        if (response.isSuccessful || response.code() == 404) {
            dao.delete(issue)
            return true
        }
        return false
    }

    // 409 means the server copy changed too. The newer lastUpdated wins.
    private suspend fun resolveConflict(issue: Issue, serverId: String): Boolean {
        val listResponse = api.getIssues()
        val remote = listResponse.body()?.firstOrNull { it.id == serverId }
        if (!listResponse.isSuccessful || remote == null) return false

        val remoteTime = remote.lastUpdated ?: 0L
        if (remoteTime > issue.lastUpdated) {
            dao.insert(remote.toIssue(issue.localId))
            return true
        }

        val retry = api.updateIssue(serverId, issue.toDto())
        if (retry.isSuccessful) {
            markSynced(issue, serverId)
            return true
        }
        dao.getIssueById(issue.localId)?.let {
            dao.update(it.copy(syncStatus = SyncState.CONFLICT))
        }
        return false
    }

    // If the user edited the issue while it was uploading, keep it PENDING.
    private suspend fun markSynced(original: Issue, serverId: String) {
        val current = dao.getIssueById(original.localId)
        if (current == null) {
            try {
                api.deleteIssue(serverId)
            } catch (e: IOException) {
                // Nothing more to do here.
            }
            return
        }
        val editedMeanwhile = current.lastUpdated != original.lastUpdated
        dao.update(
            current.copy(
                serverId = serverId,
                syncStatus = if (editedMeanwhile) SyncState.PENDING else SyncState.SYNCED
            )
        )
    }

    private suspend fun pullRemote(): Boolean {
        val response = api.getIssues()
        val remote: List<IssueDto> = when {
            response.isSuccessful -> response.body() ?: emptyList()
            response.code() == 404 -> emptyList()
            else -> return false
        }

        val remoteIds = HashSet<String>()
        for (dto in remote) {
            val id = dto.id ?: continue
            remoteIds.add(id)
            if (dto.title.isNullOrBlank()) continue

            val existing = dao.getIssueByServerId(id)
            when {
                existing == null -> dao.insert(dto.toIssue(0))
                existing.pendingDelete -> {
                    // Our delete will be sent, so leave it alone.
                }
                existing.syncStatus == SyncState.SYNCED ||
                        (dto.lastUpdated ?: 0L) > existing.lastUpdated ->
                    dao.insert(dto.toIssue(existing.localId))
            }
        }

        // Anything the server no longer has is removed here too.
        for (local in dao.getAllOnce()) {
            val serverId = local.serverId
            if (serverId != null &&
                local.syncStatus == SyncState.SYNCED &&
                serverId !in remoteIds
            ) {
                dao.delete(local)
            }
        }
        return true
    }

    companion object {
        @Volatile
        private var INSTANCE: IssueRepository? = null

        fun getInstance(context: Context): IssueRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: IssueRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}