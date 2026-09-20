package com.example.bugtracker

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class IssueViewModel(
    application: Application,
    private val saved: SavedStateHandle
) : AndroidViewModel(application) {

    private val app: Application = application
    private val repo = IssueRepository.getInstance(app)

    val issues: StateFlow<List<Issue>> =
        repo.issues.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Form state lives in SavedStateHandle so it survives rotation and process death.
    val formOpen: StateFlow<Boolean> = saved.getStateFlow(KEY_FORM_OPEN, false)
    val editingId: StateFlow<Int> = saved.getStateFlow(KEY_EDITING_ID, -1)
    val editingDraft: StateFlow<Boolean> = saved.getStateFlow(KEY_EDITING_DRAFT, false)
    val title: StateFlow<String> = saved.getStateFlow(KEY_TITLE, "")
    val description: StateFlow<String> = saved.getStateFlow(KEY_DESCRIPTION, "")
    val priority: StateFlow<String> = saved.getStateFlow(KEY_PRIORITY, Priority.MEDIUM)
    val status: StateFlow<String> = saved.getStateFlow(KEY_STATUS, Status.OPEN)

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing

    init {
        // Send anything waiting and pull the latest list when the app opens.
        SyncWorker.schedule(app)
    }

    fun newIssue() {
        clearForm()
        saved[KEY_FORM_OPEN] = true
    }

    fun edit(issue: Issue) {
        saved[KEY_EDITING_ID] = issue.localId
        saved[KEY_EDITING_DRAFT] = issue.isDraft
        saved[KEY_TITLE] = issue.title
        saved[KEY_DESCRIPTION] = issue.description
        saved[KEY_PRIORITY] = issue.priority
        saved[KEY_STATUS] = issue.status
        saved[KEY_FORM_OPEN] = true
    }

    fun closeForm() {
        clearForm()
        saved[KEY_FORM_OPEN] = false
    }

    private fun clearForm() {
        saved[KEY_EDITING_ID] = -1
        saved[KEY_EDITING_DRAFT] = false
        saved[KEY_TITLE] = ""
        saved[KEY_DESCRIPTION] = ""
        saved[KEY_PRIORITY] = Priority.MEDIUM
        saved[KEY_STATUS] = Status.OPEN
    }

    fun onTitleChange(value: String) {
        saved[KEY_TITLE] = value
    }

    fun onDescriptionChange(value: String) {
        saved[KEY_DESCRIPTION] = value
    }

    fun onPriorityChange(value: String) {
        saved[KEY_PRIORITY] = value
    }

    fun onStatusChange(value: String) {
        saved[KEY_STATUS] = value
    }

    fun submit(asDraft: Boolean) {
        val cleanTitle = title.value.trim()
        if (cleanTitle.isEmpty()) {
            _message.value = "Please type a title first."
            return
        }
        val desc = description.value
        val prio = priority.value
        val stat = status.value
        val id = editingId.value

        viewModelScope.launch {
            if (id == -1) {
                repo.createIssue(cleanTitle, desc, prio, stat, asDraft)
            } else {
                val existing = repo.getIssue(id)
                if (existing != null) {
                    repo.updateIssue(existing, cleanTitle, desc, prio, stat, asDraft)
                } else {
                    repo.createIssue(cleanTitle, desc, prio, stat, asDraft)
                }
            }
            closeForm()
            _message.value = when {
                asDraft -> "Draft saved on this phone."
                isNetworkAvailable(app) -> "Saved. Syncing now."
                else -> "Saved on this phone. It will sync when you are online."
            }
        }
    }

    fun delete(issue: Issue) {
        viewModelScope.launch {
            repo.deleteIssue(issue)
            _message.value = "Issue deleted."
        }
    }

    fun syncNow() {
        if (_syncing.value) return
        viewModelScope.launch {
            if (!isNetworkAvailable(app)) {
                SyncWorker.schedule(app)
                _message.value = "No internet. Your work is saved on this phone and will sync later."
                return@launch
            }
            _syncing.value = true
            val ok = try {
                repo.syncAll()
            } finally {
                _syncing.value = false
            }
            if (ok) {
                _message.value = "Sync complete."
            } else {
                SyncWorker.schedule(app)
                _message.value = "Sync failed. The app will retry by itself."
            }
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    private companion object {
        const val KEY_FORM_OPEN = "formOpen"
        const val KEY_EDITING_ID = "editingId"
        const val KEY_EDITING_DRAFT = "editingDraft"
        const val KEY_TITLE = "title"
        const val KEY_DESCRIPTION = "description"
        const val KEY_PRIORITY = "priority"
        const val KEY_STATUS = "status"
    }
}