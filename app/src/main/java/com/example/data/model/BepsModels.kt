package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class UserRole(val label: String) {
    PARENT("Parent"),
    TEACHER("Teacher"),
    SECURITY("Security"),
    ADMIN("Admin")
}

enum class StudentStatus(val label: String, val stepIndex: Int) {
    NOT_ARRIVED("Not Arrived", 0),
    ARRIVED_AT_GATE("Arrived at Gate", 1),
    IN_CLASSROOM("In Classroom", 2),
    PICKUP_REQUESTED("Pickup Requested", 3),
    READY_FOR_PICKUP("Ready for Pickup", 4),
    COLLECTED("Collected", 5),
    BOARDED_BUS("Boarded Bus", 5)
}

enum class TransportMode(val label: String) {
    PRIVATE_PICKUP("Private Guardian Pickup"),
    SCHOOL_BUS("School Bus")
}

enum class LogEventType(val label: String) {
    ARRIVAL("Arrival"),
    CLASSROOM_CHECKIN("Classroom"),
    PICKUP_REQUESTED("Dispersal"),
    READY_FOR_PICKUP("Ready"),
    HANDOVER_COMPLETED("Completed"),
    BUS_BOARDED("Bus Boarding"),
    PICKUP_REJECTED("Rejected")
}

enum class BusStatus(val label: String) {
    AT_SCHOOL("At School Bay"),
    BOARDING("Boarding Now"),
    DEPARTED("Departed Campus"),
    COMPLETED("Route Completed")
}

enum class AlertType(val label: String) {
    DELAY("Delay"),
    REJECTED_PICKUP("Rejected Pickup"),
    UNCOLLECTED_STUDENT("Uncollected Student"),
    INCIDENT("Safety Incident")
}

enum class AlertSeverity(val label: String) {
    HIGH("High"),
    MEDIUM("Medium"),
    LOW("Low")
}

enum class AlertStatus(val label: String) {
    OPEN("Open"),
    ACKNOWLEDGED("Acknowledged"),
    RESOLVED("Resolved")
}

enum class RequestStatus(val label: String) {
    PENDING("Pending"),
    APPROVED("Approved"),
    DECLINED("Declined")
}

inline fun <reified T : Enum<T>> safeEnumValue(raw: String?, default: T): T {
    if (raw.isNullOrBlank()) return default
    return enumValues<T>().firstOrNull { it.name == raw } ?: default
}

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val email: String = "",
    val phone: String = "",
    val role: String = UserRole.PARENT.name,
    val assignedGrade: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun roleEnum(): UserRole = safeEnumValue(role, UserRole.PARENT)
}

@Keep
@IgnoreExtraProperties
data class SchoolConfig(
    @DocumentId val id: String = "",
    val schoolName: String = "Bangladesh English Private School",
    val campusLocation: String = "Ras Al Khaimah, UAE",
    val dispersalOpen: Boolean = false,
    val dispersalWindowLabel: String = "01:00 PM - 02:15 PM",
    val activeNotice: String = "Morning arrival gate open. Dispersal requests unlock at 01:00 PM.",
    val updatedByUid: String = "",
    val updatedByName: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Student(
    @DocumentId val id: String = "",
    val studentCode: String = "",
    val fullName: String = "",
    val grade: String = "",
    val parentUid: String = "",
    val parentName: String = "",
    val parentEmail: String = "",
    val authorizedStaffUids: List<String> = emptyList(),
    val authorizedGuardians: List<String> = emptyList(),
    val photoKey: String = "avatar_girl_1",
    val busRouteId: String = "",
    val busRouteName: String = "",
    val transportMode: String = TransportMode.PRIVATE_PICKUP.name,
    val status: String = StudentStatus.NOT_ARRIVED.name,
    val arrivalTimeLabel: String = "",
    val collectionTimeLabel: String = "",
    val lastUpdatedByUid: String = "",
    val lastUpdatedByName: String = "",
    val verifiedCollectorName: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun statusEnum(): StudentStatus = safeEnumValue(status, StudentStatus.NOT_ARRIVED)
    fun transportModeEnum(): TransportMode = safeEnumValue(transportMode, TransportMode.PRIVATE_PICKUP)
    fun qrPayload(): String = "BEPS:STUDENT:$studentCode"
}

@Keep
@IgnoreExtraProperties
data class AttendanceLog(
    @DocumentId val id: String = "",
    val studentId: String = "",
    val studentCode: String = "",
    val studentName: String = "",
    val grade: String = "",
    val parentUid: String = "",
    val authorizedStaffUids: List<String> = emptyList(),
    val eventType: String = LogEventType.ARRIVAL.name,
    val title: String = "",
    val message: String = "",
    val actorUid: String = "",
    val actorName: String = "",
    val actorRole: String = UserRole.SECURITY.name,
    val collectorName: String = "",
    val timeLabel: String = "",
    val dateKey: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
) {
    fun eventTypeEnum(): LogEventType = safeEnumValue(eventType, LogEventType.ARRIVAL)
}

@Keep
@IgnoreExtraProperties
data class BusRoute(
    @DocumentId val id: String = "",
    val routeNumber: String = "",
    val routeName: String = "",
    val driverName: String = "",
    val attendantName: String = "",
    val status: String = BusStatus.AT_SCHOOL.name,
    val boardedCount: Long = 0L,
    val capacity: Long = 32L,
    val lastCheckpoint: String = "",
    val assignedParentUids: List<String> = emptyList(),
    val authorizedStaffUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun statusEnum(): BusStatus = safeEnumValue(status, BusStatus.AT_SCHOOL)
}

@Keep
@IgnoreExtraProperties
data class ManagementAlert(
    @DocumentId val id: String = "",
    val studentId: String = "",
    val studentName: String = "",
    val alertType: String = AlertType.INCIDENT.name,
    val severity: String = AlertSeverity.MEDIUM.name,
    val title: String = "",
    val description: String = "",
    val reportedByUid: String = "",
    val reportedByName: String = "",
    val reportedByRole: String = UserRole.TEACHER.name,
    val status: String = AlertStatus.OPEN.name,
    val authorizedStaffUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun alertTypeEnum(): AlertType = safeEnumValue(alertType, AlertType.INCIDENT)
    fun severityEnum(): AlertSeverity = safeEnumValue(severity, AlertSeverity.MEDIUM)
    fun statusEnum(): AlertStatus = safeEnumValue(status, AlertStatus.OPEN)
}

@Keep
@IgnoreExtraProperties
data class RoleRequest(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val email: String = "",
    val requestedRole: String = UserRole.TEACHER.name,
    val note: String = "",
    val status: String = RequestStatus.PENDING.name,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun requestedRoleEnum(): UserRole = safeEnumValue(requestedRole, UserRole.TEACHER)
    fun statusEnum(): RequestStatus = safeEnumValue(status, RequestStatus.PENDING)
}
