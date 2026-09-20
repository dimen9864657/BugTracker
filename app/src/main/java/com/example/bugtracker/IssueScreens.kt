package com.example.bugtracker

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun BugTrackerScreen(vm: IssueViewModel) {
    val issues by vm.issues.collectAsState()
    val formOpen by vm.formOpen.collectAsState()
    val message by vm.message.collectAsState()
    val syncing by vm.syncing.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        val text = message
        if (text != null) {
            snackbarHostState.showSnackbar(text)
            vm.clearMessage()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .imePadding()
    ) {
        if (formOpen) {
            FormScreen(vm)
        } else {
            ListScreen(issues, syncing, vm)
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
fun ListScreen(issues: List<Issue>, syncing: Boolean, vm: IssueViewModel) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Bug Tracker",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.weight(1f)
            )
            if (syncing) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
            }
            OutlinedButton(onClick = { vm.syncNow() }) {
                Text("Sync")
            }
        }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { vm.newIssue() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("+ New issue")
        }
        Spacer(Modifier.height(8.dp))

        if (issues.isEmpty()) {
            Text("No issues yet. Tap + New issue to add one.")
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(issues, key = { it.localId }) { issue ->
                    IssueCard(
                        issue = issue,
                        onClick = { vm.edit(issue) },
                        onDelete = { vm.delete(issue) }
                    )
                }
            }
        }
    }
}

@Composable
fun IssueCard(issue: Issue, onClick: () -> Unit, onDelete: () -> Unit) {
    val isProblem = issue.syncStatus == SyncState.FAILED || issue.syncStatus == SyncState.CONFLICT

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = issue.title, style = MaterialTheme.typography.titleMedium)
            if (issue.description.isNotBlank()) {
                Text(text = issue.description, maxLines = 2)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Priority: ${issue.priority}   Status: ${issue.status.replace("_", " ")}",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = "Created: ${formatDate(issue.creationDate)}",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = syncLabel(issue),
                style = MaterialTheme.typography.bodySmall,
                color = if (isProblem) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.primary
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDelete) {
                    Text("Delete")
                }
            }
        }
    }
}

@Composable
fun FormScreen(vm: IssueViewModel) {
    val priority by vm.priority.collectAsState()
    val status by vm.status.collectAsState()
    val editingId by vm.editingId.collectAsState()
    val editingDraft by vm.editingDraft.collectAsState()

    // Start from the saved values so rotation keeps what the user typed.
    var title by remember { mutableStateOf(vm.title.value) }
    var description by remember { mutableStateOf(vm.description.value) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = if (editingId == -1) "New issue" else "Edit issue",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = title,
            onValueChange = {
                title = it
                vm.onTitleChange(it)
            },
            label = { Text("Title") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = description,
            onValueChange = {
                description = it
                vm.onDescriptionChange(it)
            },
            label = { Text("Description") },
            minLines = 3,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))

        ChoiceGroup("Priority", Priority.all, priority) { vm.onPriorityChange(it) }
        Spacer(Modifier.height(8.dp))
        ChoiceGroup("Status", Status.all, status) { vm.onStatusChange(it) }
        Spacer(Modifier.height(16.dp))

        Button(
            onClick = { vm.submit(false) },
            enabled = title.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Submit")
        }

        if (editingId == -1 || editingDraft) {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { vm.submit(true) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save draft")
            }
        }

        Spacer(Modifier.height(8.dp))
        TextButton(
            onClick = { vm.closeForm() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Cancel")
        }
    }
}

@Composable
fun ChoiceGroup(
    label: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit
) {
    Text(text = label, style = MaterialTheme.typography.titleSmall)
    options.forEach { option ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onSelect(option) },
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(selected = option == selected, onClick = { onSelect(option) })
            Text(option.replace("_", " "))
        }
    }
}

private fun syncLabel(issue: Issue): String = when {
    issue.isDraft -> "Draft (not sent yet)"
    issue.syncStatus == SyncState.SYNCED -> "Synced"
    issue.syncStatus == SyncState.FAILED -> "Warning: sync failed. Tap Sync to retry."
    issue.syncStatus == SyncState.CONFLICT -> "Conflict. Tap Sync to retry."
    else -> "Waiting to sync"
}

private fun formatDate(millis: Long): String =
    SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(millis))