package com.agentos.app.ui.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agentos.app.data.repo.TaskRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

@OptIn(ExperimentalCoroutinesApi::class)
class TasksViewModel(private val taskRepo: TaskRepository) : ViewModel() {

    private val selectedTaskId = MutableStateFlow<String?>(null)
    val selected: StateFlow<String?> = selectedTaskId.asStateFlow()

    val tasks: StateFlow<List<com.agentos.app.data.db.TaskEntity>> = taskRepo.observeTasks()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val selectedTask: StateFlow<com.agentos.app.data.db.TaskEntity?> = selectedTaskId
        .flatMapLatest { id -> if (id == null) flowOf(null) else taskRepo.observeTask(id) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val selectedSteps: StateFlow<List<com.agentos.app.data.db.TaskStepEntity>> = selectedTaskId
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else taskRepo.observeSteps(id) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun select(taskId: String?) {
        selectedTaskId.value = taskId
    }
}
