package com.agentos.app.ui.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agentos.app.data.db.TaskEntity
import com.agentos.app.data.db.TaskStepEntity
import com.agentos.app.data.repo.TaskRepository
import com.agentos.app.domain.engine.TaskEngine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class TasksViewModel(
    private val taskRepo: TaskRepository,
    private val engine: TaskEngine
) : ViewModel() {

    private val selectedTaskId = MutableStateFlow<String?>(null)
    val selected: StateFlow<String?> = selectedTaskId.asStateFlow()

    val tasks: StateFlow<List<TaskEntity>> = taskRepo.observeTasks()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val selectedTask: StateFlow<TaskEntity?> = selectedTaskId
        .flatMapLatest { id -> if (id == null) flowOf(null) else taskRepo.observeTask(id) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val selectedSteps: StateFlow<List<TaskStepEntity>> = selectedTaskId
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else taskRepo.observeSteps(id) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /**
     * Steps for every task currently in the list, keyed by task id. Drives the
     * "Step X/Y" chip and current-agent badge on collapsed task cards before the
     * user expands a card. Re-issued whenever the task list itself changes.
     */
    val allSteps: StateFlow<Map<String, List<TaskStepEntity>>> = taskRepo.observeTasks()
        .flatMapLatest { tasks ->
            if (tasks.isEmpty()) {
                flowOf(emptyMap())
            } else {
                val flows: List<Flow<List<TaskStepEntity>>> =
                    tasks.map { taskRepo.observeSteps(it.id) }
                combine<List<TaskStepEntity>, Map<String, List<TaskStepEntity>>>(flows) { arrays ->
                    tasks.zip(arrays.toList()).associate { (t, s) -> t.id to s }
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    fun select(taskId: String?) {
        selectedTaskId.value = taskId
    }

    /**
     * Resume a previously-interrupted or failed task. Finds the original user
     * request and conversation id, then re-launches it via the engine. The
     * engine's `resume(...)` is currently an alias for `start(...)`, which
     * creates a fresh task with a new id — the original task row stays in the
     * "Failed" section as historical record.
     */
    fun resumeTask(taskId: String) {
        viewModelScope.launch {
            val task = taskRepo.observeTask(taskId).first() ?: return@launch
            val conversationId = task.conversationId ?: ""
            engine.resume(task.userRequest, conversationId)
        }
    }
}
