package com.example.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.AlertSeverity
import com.example.data.model.AlertStatus
import com.example.data.model.AlertType
import com.example.data.model.AttendanceLog
import com.example.data.model.BusRoute
import com.example.data.model.BusStatus
import com.example.data.model.ManagementAlert
import com.example.data.model.RoleRequest
import com.example.data.model.SchoolConfig
import com.example.data.model.Student
import com.example.data.model.TransportMode
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.data.model.UserRole
import com.example.data.repository.BepsRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class BepsViewModel(
    private val repository: BepsRepository,
    val currentUserId: String,
    val currentUserName: String,
    val currentUserEmail: String,
    val isBootstrappedAdmin: Boolean
) : ViewModel() {

    private val _selectedWorkspaceRole = MutableStateFlow<UserRole?>(null)
    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    private val _isSubmitting = MutableStateFlow(false)
    val isSubmitting: StateFlow<Boolean> = _isSubmitting.asStateFlow()

    val userProfileState: StateFlow<UiState<UserProfile?>> =
        repository.observeUserProfile(currentUserId)
            .toUiState("Failed to load profile")
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

    val schoolConfigState: StateFlow<UiState<SchoolConfig?>> =
        repository.observeSchoolConfig()
            .toUiState("Failed to load school configuration")
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

    val effectiveRole: StateFlow<UserRole> =
        userProfileState.map { state ->
            val assigned = (state as? UiState.Success)?.data?.roleEnum()
                ?: if (isBootstrappedAdmin) UserRole.ADMIN else UserRole.PARENT
            if (assigned == UserRole.ADMIN || isBootstrappedAdmin) {
                _selectedWorkspaceRole.value ?: UserRole.ADMIN
            } else {
                assigned
            }
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            if (isBootstrappedAdmin) UserRole.ADMIN else UserRole.PARENT
        )

    private val assignedRoleFlow: Flow<UserRole> = userProfileState.map { state ->
        (state as? UiState.Success)?.data?.roleEnum()
            ?: if (isBootstrappedAdmin) UserRole.ADMIN else UserRole.PARENT
    }

    val studentsState: StateFlow<UiState<List<Student>>> =
        assignedRoleFlow.flatMapLatest { role ->
            repository.observeStudents(currentUserId, role, isBootstrappedAdmin)
        }
            .toUiState("Failed to load students")
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

    val logsState: StateFlow<UiState<List<AttendanceLog>>> =
        assignedRoleFlow.flatMapLatest { role ->
            repository.observeAttendanceLogs(currentUserId, role, isBootstrappedAdmin)
        }
            .toUiState("Failed to load activity notifications")
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

    val busRoutesState: StateFlow<UiState<List<BusRoute>>> =
        assignedRoleFlow.flatMapLatest { role ->
            repository.observeBusRoutes(currentUserId, role, isBootstrappedAdmin)
        }
            .toUiState("Failed to load school bus routes")
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

    val alertsState: StateFlow<UiState<List<ManagementAlert>>> =
        assignedRoleFlow.flatMapLatest { role ->
            repository.observeManagementAlerts(currentUserId, role, isBootstrappedAdmin)
        }
            .toUiState("Failed to load management alerts")
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

    val allUsersState: StateFlow<UiState<List<UserProfile>>> =
        repository.observeAllUsers(isBootstrappedAdmin)
            .toUiState("Failed to load staff directory")
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

    val roleRequestsState: StateFlow<UiState<List<RoleRequest>>> =
        repository.observeRoleRequests(isBootstrappedAdmin)
            .toUiState("Failed to load role requests")
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

    init {
        viewModelScope.launch {
            repository.ensureUserProfile(currentUserName, currentUserEmail)
                .onFailure { e ->
                    _snackbarMessage.value = e.localizedMessage ?: "Failed to initialize profile"
                }
        }
    }

    fun selectWorkspaceRole(role: UserRole) {
        _selectedWorkspaceRole.value = role
    }

    fun clearSnackbar() {
        _snackbarMessage.value = null
    }

    fun showMessage(message: String) {
        _snackbarMessage.value = message
    }

    fun initializeSampleCampusData() {
        runMutation("Campus roster initialized in Firestore.") {
            repository.initializeSampleCampusData(currentUserId, currentUserName, currentUserEmail)
        }
    }

    fun toggleDispersalWindow(open: Boolean, windowLabel: String, notice: String) {
        val statusWord = if (open) "OPENED" else "LOCKED"
        runMutation("Campus dispersal window $statusWord.") {
            repository.toggleDispersalWindow(open, windowLabel, notice, currentUserId, currentUserName)
        }
    }

    fun requestPickup(student: Student, dispersalOpen: Boolean) {
        if (!dispersalOpen) {
            _snackbarMessage.value = "Safety Rule: Pickup requests are locked until the school opens the dispersal period."
            return
        }
        if (student.statusEnum().name != "IN_CLASSROOM") {
            _snackbarMessage.value = "Pickup can only be requested once the teacher confirms classroom attendance."
            return
        }
        runMutation("Pickup request sent for ${student.fullName}.") {
            repository.requestStudentPickup(student, currentUserId, currentUserName, effectiveRole.value)
        }
    }

    fun addAuthorizedGuardian(student: Student, guardianEntry: String) {
        runMutation("Authorized collector added for ${student.fullName}.") {
            repository.addAuthorizedGuardian(student, guardianEntry, currentUserName)
        }
    }

    fun recordGateArrival(student: Student) {
        runMutation("Recorded gate arrival for ${student.fullName}.") {
            repository.recordGateArrival(student, currentUserName, effectiveRole.value)
        }
    }

    fun confirmClassroomCheckIn(student: Student) {
        runMutation("Confirmed ${student.fullName} in classroom.") {
            repository.confirmClassroomCheckIn(student, currentUserName, effectiveRole.value)
        }
    }

    fun confirmReadyForPickup(student: Student) {
        runMutation("${student.fullName} marked ready for collection.") {
            repository.confirmReadyForPickup(student, currentUserName, effectiveRole.value)
        }
    }

    fun confirmHandover(student: Student, collectorName: String) {
        runMutation("Verified handover completed for ${student.fullName}.") {
            repository.confirmHandover(student, collectorName, currentUserName, effectiveRole.value)
        }
    }

    fun rejectPickupRequest(student: Student, reason: String) {
        runMutation("Pickup held & Principal alert logged for ${student.fullName}.") {
            repository.rejectPickupRequest(student, reason, currentUserName, effectiveRole.value)
        }
    }

    fun confirmBusBoarding(student: Student, busRoute: BusRoute?) {
        runMutation("Confirmed bus boarding for ${student.fullName}.") {
            repository.confirmBusBoarding(student, busRoute, currentUserName, effectiveRole.value)
        }
    }

    fun updateBusRouteStatus(route: BusRoute, newStatus: BusStatus, checkpoint: String) {
        runMutation("${route.routeNumber} updated to ${newStatus.label}.") {
            repository.updateBusRouteStatus(route, newStatus, checkpoint)
        }
    }

    fun raiseManagementAlert(
        studentId: String,
        studentName: String,
        alertType: AlertType,
        severity: AlertSeverity,
        title: String,
        description: String
    ) {
        runMutation("Management alert dispatched to Principal.") {
            repository.raiseManagementAlert(
                studentId,
                studentName,
                alertType,
                severity,
                title,
                description,
                currentUserName,
                effectiveRole.value
            )
        }
    }

    fun updateAlertStatus(alertId: String, newStatus: AlertStatus) {
        runMutation("Alert marked ${newStatus.label}.") {
            repository.updateAlertStatus(alertId, newStatus)
        }
    }

    fun enrollStudent(
        studentCode: String,
        fullName: String,
        grade: String,
        parentUid: String,
        parentName: String,
        parentEmail: String,
        guardianName: String,
        transportMode: TransportMode,
        busRoute: BusRoute?
    ) {
        runMutation("Enrolled $fullName ($studentCode).") {
            repository.enrollStudent(
                studentCode,
                fullName,
                grade,
                parentUid,
                parentName,
                parentEmail,
                guardianName,
                transportMode,
                busRoute,
                currentUserName
            )
        }
    }

    fun resetDailyRoster() {
        val students = (studentsState.value as? UiState.Success)?.data.orEmpty()
        val routes = (busRoutesState.value as? UiState.Success)?.data.orEmpty()
        runMutation("Daily campus roster reset for new school day.") {
            repository.resetDailyRoster(students, routes, currentUserName)
        }
    }

    fun submitRoleRequest(requestedRole: UserRole, note: String) {
        runMutation("Role request submitted to BEPS Principal.") {
            repository.submitRoleRequest(currentUserName, currentUserEmail, requestedRole, note)
        }
    }

    fun resolveRoleRequest(request: RoleRequest, approve: Boolean) {
        val students = (studentsState.value as? UiState.Success)?.data.orEmpty()
        val routes = (busRoutesState.value as? UiState.Success)?.data.orEmpty()
        val verb = if (approve) "approved" else "declined"
        runMutation("Staff request $verb for ${request.displayName}.") {
            repository.resolveRoleRequest(request, approve, students, routes)
        }
    }

    fun updateUserRole(targetUid: String, newRole: UserRole, assignedGrade: String) {
        val students = (studentsState.value as? UiState.Success)?.data.orEmpty()
        val routes = (busRoutesState.value as? UiState.Success)?.data.orEmpty()
        runMutation("Updated staff role to ${newRole.label}.") {
            repository.updateUserRole(targetUid, newRole, assignedGrade, students, routes)
        }
    }

    private fun runMutation(successMsg: String, block: suspend () -> Result<Unit>) {
        viewModelScope.launch {
            _isSubmitting.value = true
            val result = block()
            _isSubmitting.value = false
            result.onSuccess {
                _snackbarMessage.value = successMsg
            }.onFailure { error ->
                _snackbarMessage.value = error.localizedMessage ?: "Operation failed"
            }
        }
    }

    private fun <T> Flow<T>.toUiState(fallbackMessage: String): Flow<UiState<T>> =
        map<T, UiState<T>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, fallbackMessage, rootError)
                emit(UiState.Error(rootError.message ?: fallbackMessage))
            }

    private companion object {
        const val TAG = "BepsViewModel"
        const val STOP_TIMEOUT_MS = 5000L
    }
}
