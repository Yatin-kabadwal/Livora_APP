@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.livora.corbett.ui.staff.housekeeping

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.livora.corbett.data.api.ChecklistItem
import com.livora.corbett.data.api.HkTask
import com.livora.corbett.data.api.Room
import com.livora.corbett.data.api.TaskPatchBody
import com.livora.corbett.data.realtime.SocketManager
import com.livora.corbett.data.repo.OpsRepository
import com.livora.corbett.ui.components.AppTextField
import com.livora.corbett.ui.components.EmptyState
import com.livora.corbett.ui.components.GhostButton
import com.livora.corbett.ui.components.GlassCard
import com.livora.corbett.ui.components.GradientButton
import com.livora.corbett.ui.components.InlineError
import com.livora.corbett.ui.components.ListSkeleton
import com.livora.corbett.ui.components.LoadBox
import com.livora.corbett.ui.components.LocalSnackbar
import com.livora.corbett.ui.components.SelectChip
import com.livora.corbett.ui.components.StatusPill
import com.livora.corbett.ui.components.statusColor
import com.livora.corbett.ui.components.staggerIn
import com.livora.corbett.ui.theme.Brand
import com.livora.corbett.util.ApiResult
import com.livora.corbett.util.Fmt
import com.livora.corbett.util.Load
import com.livora.corbett.util.Poller
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HkUi(
    val tasks: Load<List<HkTask>> = Load.Loading,
    val rooms: List<Room> = emptyList(),
    val refreshing: Boolean = false,
    val createOpen: Boolean = false,
    val creating: Boolean = false,
    val createError: String? = null,
    val busyId: String? = null,
)

@HiltViewModel
class HousekeepingViewModel @Inject constructor(
    private val ops: OpsRepository,
    private val socket: SocketManager,
) : ViewModel() {
    private val _ui = MutableStateFlow(HkUi())
    val ui: StateFlow<HkUi> = _ui.asStateFlow()
    private val _msgs = Channel<String>(Channel.BUFFERED)
    val messages = _msgs.receiveAsFlow()
    private val poller = Poller(viewModelScope, 45_000) { load(silent = true) }

    init {
        refresh(initial = true)
        viewModelScope.launch {
            val r = ops.allRooms()
            if (r is ApiResult.Success) _ui.update { it.copy(rooms = r.data) }
        }
        viewModelScope.launch { socket.events.collect { if (it.name == "housekeeping:update") load(silent = true) } }
    }

    fun startPolling() = poller.start()
    fun stopPolling() = poller.stop()

    fun refresh(initial: Boolean = false) {
        if (initial) _ui.update { it.copy(tasks = Load.Loading) } else _ui.update { it.copy(refreshing = true) }
        viewModelScope.launch { load(silent = false) }
    }

    private suspend fun load(silent: Boolean) {
        when (val r = ops.tasks("pending,in_progress,completed")) {
            is ApiResult.Success -> _ui.update { it.copy(tasks = Load.Ready(r.data), refreshing = false) }
            is ApiResult.Failure -> _ui.update {
                it.copy(tasks = if (silent && it.tasks is Load.Ready) it.tasks else Load.Failed(r.message), refreshing = false)
            }
        }
    }

    fun setStatus(t: HkTask, status: String) {
        _ui.update { it.copy(busyId = t.id) }
        viewModelScope.launch {
            when (val r = ops.patchTask(t.id, TaskPatchBody(status = status))) {
                is ApiResult.Success -> _msgs.trySend("Room ${t.room}: ${Fmt.titleCase(status)}")
                is ApiResult.Failure -> _msgs.trySend(r.message)
            }
            _ui.update { it.copy(busyId = null) }
            load(silent = true)
        }
    }

    fun toggleItem(t: HkTask, index: Int) {
        val items = t.checklistItems.mapIndexed { i, c -> if (i == index) c.copy(checked = !c.checked) else c }
        // optimistic
        _ui.update { st ->
            val cur = (st.tasks as? Load.Ready)?.data ?: return@update st
            st.copy(tasks = Load.Ready(cur.map { if (it.id == t.id) it.copy(checklistItems = items) else it }))
        }
        viewModelScope.launch {
            val r = ops.patchTask(t.id, TaskPatchBody(checklistItems = items))
            if (r is ApiResult.Failure) {
                _msgs.trySend(r.message)
                load(silent = true)
            }
        }
    }

    fun openCreate(open: Boolean) = _ui.update { it.copy(createOpen = open, createError = null) }

    fun create(roomId: String, type: String, priority: String, notes: String) {
        _ui.update { it.copy(creating = true, createError = null) }
        viewModelScope.launch {
            when (val r = ops.createTask(roomId, type, priority, notes)) {
                is ApiResult.Success -> {
                    _ui.update { it.copy(creating = false, createOpen = false) }
                    _msgs.trySend("Task created.")
                    load(silent = true)
                }
                is ApiResult.Failure -> _ui.update { it.copy(creating = false, createError = r.message) }
            }
        }
    }
}

private val taskTypes = listOf(
    "checkout_clean" to "Checkout clean",
    "daily_clean" to "Daily clean",
    "deep_clean" to "Deep clean",
    "turndown" to "Turndown",
    "maintenance" to "Maintenance",
)
private val priorities = listOf("low" to "Low", "normal" to "Normal", "high" to "High", "urgent" to "Urgent")

@Composable
fun HousekeepingScreen(vm: HousekeepingViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val snack = LocalSnackbar.current

    LifecycleResumeEffect(Unit) {
        vm.startPolling()
        onPauseOrDispose { vm.stopPolling() }
    }
    LaunchedEffect(vm) { vm.messages.collect { snack.showSnackbar(it) } }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Text("Housekeeping", style = MaterialTheme.typography.displaySmall, modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 8.dp))
            PullToRefreshBox(isRefreshing = ui.refreshing, onRefresh = { vm.refresh() }, modifier = Modifier.weight(1f)) {
                LoadBox(ui.tasks, onRetry = { vm.refresh(initial = true) }, skeleton = { ListSkeleton(4, 130.dp) }) { tasks ->
                    if (tasks.isEmpty()) {
                        EmptyState(Icons.Filled.CleaningServices, "All clear", "No housekeeping tasks right now. Tasks are created automatically at check-out.")
                    } else {
                        val inProgress = tasks.filter { it.status == "in_progress" }
                        val pending = tasks.filter { it.status == "pending" }
                        val done = tasks.filter { it.status == "completed" || it.status == "skipped" }.take(8)
                        LazyColumn(
                            Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            section("In progress", inProgress, ui.busyId, vm)
                            section("Pending", pending, ui.busyId, vm)
                            section("Completed", done, ui.busyId, vm)
                        }
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { vm.openCreate(true) },
            icon = { Icon(Icons.Filled.Add, null) },
            text = { Text("New task") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        )
    }
    if (ui.createOpen) CreateTaskSheet(ui, vm)
}

private fun androidx.compose.foundation.lazy.LazyListScope.section(title: String, list: List<HkTask>, busyId: String?, vm: HousekeepingViewModel) {
    if (list.isEmpty()) return
    item(key = "h-$title") { Text("$title (${list.size})", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 4.dp)) }
    items(list.size, key = { list[it].id.ifBlank { "$title-$it" } }) { i ->
        TaskCard(list[i], busyId == list[i].id, vm, Modifier.staggerIn(i))
    }
}

@Composable
private fun TaskCard(t: HkTask, busy: Boolean, vm: HousekeepingViewModel, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val urgent = t.priority == "urgent" || t.priority == "high"
    GlassCard(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Room ${t.room.ifBlank { "?" }}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(Fmt.titleCase(t.taskType), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                StatusPill(Fmt.titleCase(t.status), statusColor(t.status))
                if (t.priority.isNotBlank()) StatusPill(Fmt.titleCase(t.priority), if (urgent) Brand.danger else cs.onSurfaceVariant)
            }
        }
        t.assignedTo?.displayName?.takeIf { it.isNotBlank() }?.let { Text("Assigned to $it", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant) }
        t.notes?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp)) }
        if (t.checklistItems.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            val enabled = t.status != "completed" && t.status != "skipped"
            t.checklistItems.forEachIndexed { i, c ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = c.checked, onCheckedChange = { vm.toggleItem(t, i) }, enabled = enabled)
                    Text(c.item, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        if (t.status == "pending" || t.status == "in_progress") {
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (t.status == "pending") {
                    GradientButton("Start", { vm.setStatus(t, "in_progress") }, Modifier.weight(1f), loading = busy)
                } else {
                    GradientButton("Complete", { vm.setStatus(t, "completed") }, Modifier.weight(1f), loading = busy)
                }
                GhostButton("Skip", { vm.setStatus(t, "skipped") }, Modifier.width(96.dp), enabled = !busy)
            }
        }
    }
}

@Composable
private fun CreateTaskSheet(ui: HkUi, vm: HousekeepingViewModel) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var roomId by remember { mutableStateOf<String?>(null) }
    var type by remember { mutableStateOf("daily_clean") }
    var priority by remember { mutableStateOf("normal") }
    var notes by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }
    ModalBottomSheet(onDismissRequest = { vm.openCreate(false) }, sheetState = state, containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("New housekeeping task", style = MaterialTheme.typography.headlineSmall)
            Text("Room", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ui.rooms.forEach { r -> SelectChip(r.roomNumber.ifBlank { r.name }, roomId == r.id, { roomId = r.id; localError = null }) }
            }
            Text("Type", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { taskTypes.forEach { (c, l) -> SelectChip(l, type == c, { type = c }) } }
            Text("Priority", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { priorities.forEach { (c, l) -> SelectChip(l, priority == c, { priority = c }) } }
            AppTextField(notes, { notes = it }, "Notes (optional)", singleLine = false, minLines = 2)
            InlineError(localError ?: ui.createError)
            GradientButton(
                "Create task",
                { val r = roomId; if (r == null) localError = "Choose a room." else vm.create(r, type, priority, notes) },
                Modifier.fillMaxWidth(), loading = ui.creating,
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}
