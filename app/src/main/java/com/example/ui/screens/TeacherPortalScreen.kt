package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAlert
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.AlertSeverity
import com.example.data.model.AlertStatus
import com.example.data.model.AlertType
import com.example.data.model.ManagementAlert
import com.example.data.model.Student
import com.example.data.model.StudentStatus
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.StatusBadge
import com.example.ui.components.StudentAvatar
import com.example.ui.theme.BepsCrimson
import com.example.ui.theme.BepsCrimsonContainer
import com.example.ui.theme.BepsEmerald
import com.example.ui.theme.BepsEmeraldContainer
import com.example.ui.theme.BepsGoldContainer

@Composable
fun TeacherPortalScreen(
    selectedTab: Int,
    students: List<Student>,
    alerts: List<ManagementAlert>,
    isSubmitting: Boolean,
    onConfirmClassroom: (Student) -> Unit,
    onConfirmReadyForPickup: (Student) -> Unit,
    onRaiseAlert: (studentId: String, studentName: String, alertType: AlertType, severity: AlertSeverity, title: String, description: String) -> Unit,
    modifier: Modifier = Modifier
) {
    var alertTargetStudent by remember { mutableStateOf<Student?>(null) }
    var showRaiseAlertDialog by remember { mutableStateOf(false) }

    if (showRaiseAlertDialog) {
        RaiseAlertDialog(
            preselectedStudent = alertTargetStudent,
            students = students,
            onDismiss = {
                showRaiseAlertDialog = false
                alertTargetStudent = null
            },
            onSubmit = { sId, sName, type, sev, title, desc ->
                onRaiseAlert(sId, sName, type, sev, title, desc)
                showRaiseAlertDialog = false
                alertTargetStudent = null
            }
        )
    }

    when (selectedTab) {
        0 -> TeacherClassroomTab(
            students = students,
            isSubmitting = isSubmitting,
            onConfirmClassroom = onConfirmClassroom,
            onConfirmReadyForPickup = onConfirmReadyForPickup,
            onReportStudentAlert = {
                alertTargetStudent = it
                showRaiseAlertDialog = true
            },
            modifier = modifier
        )
        1 -> TeacherAlertsTab(
            alerts = alerts,
            onOpenNewAlert = {
                alertTargetStudent = null
                showRaiseAlertDialog = true
            },
            modifier = modifier
        )
        else -> TeacherRosterTab(
            students = students,
            modifier = modifier
        )
    }
}

@Composable
private fun TeacherClassroomTab(
    students: List<Student>,
    isSubmitting: Boolean,
    onConfirmClassroom: (Student) -> Unit,
    onConfirmReadyForPickup: (Student) -> Unit,
    onReportStudentAlert: (Student) -> Unit,
    modifier: Modifier = Modifier
) {
    val pickupRequests = students.filter { it.statusEnum() == StudentStatus.PICKUP_REQUESTED }
    val awaitingClassroom = students.filter {
        it.statusEnum() == StudentStatus.ARRIVED_AT_GATE || it.statusEnum() == StudentStatus.NOT_ARRIVED
    }
    val inClassOrReady = students.filter {
        it.statusEnum() == StudentStatus.IN_CLASSROOM || it.statusEnum() == StudentStatus.READY_FOR_PICKUP
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TeacherMetricChip("Pickup Queue", pickupRequests.size.toString(), BepsGoldContainer, Modifier.weight(1f))
                TeacherMetricChip("Gate Check-In", awaitingClassroom.size.toString(), MaterialTheme.colorScheme.surfaceVariant, Modifier.weight(1f))
                TeacherMetricChip("In Class/Ready", inClassOrReady.size.toString(), BepsEmeraldContainer, Modifier.weight(1f))
            }
        }

        item {
            Text(
                text = "Pickup Readiness Queue (${pickupRequests.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Safety Rule #3: Teacher must confirm each child is ready before Security releases them.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (pickupRequests.isEmpty()) {
            item {
                EmptyStateCard(
                    title = "No Active Pickup Requests",
                    subtitle = "When parents press REQUEST MY CHILD during dispersal, students appear here for classroom release.",
                    icon = Icons.Default.NotificationsActive
                )
            }
        } else {
            items(pickupRequests, key = { "req_${it.id}" }) { student ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            StudentAvatar(student.fullName, student.photoKey, student.statusEnum())
                            Column(modifier = Modifier.weight(1f)) {
                                Text(student.fullName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("${student.grade} • Parent: ${student.parentName}", style = MaterialTheme.typography.bodySmall)
                            }
                            StatusBadge(student.statusEnum())
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { onConfirmReadyForPickup(student) },
                                enabled = !isSubmitting,
                                colors = ButtonDefaults.buttonColors(containerColor = BepsEmerald),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("teacher_ready_${student.studentCode}")
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.size(6.dp))
                                Text("Confirm Ready")
                            }
                            OutlinedButton(
                                onClick = { onReportStudentAlert(student) },
                                modifier = Modifier.height(48.dp)
                            ) {
                                Icon(Icons.Default.Warning, contentDescription = "Flag Alert", tint = BepsCrimson)
                            }
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Classroom Attendance Check-In (${awaitingClassroom.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        items(awaitingClassroom, key = { "arr_${it.id}" }) { student ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StudentAvatar(student.fullName, student.photoKey, student.statusEnum(), size = 48.dp)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(student.fullName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text(
                            text = "${student.grade} • ${student.statusEnum().label}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(
                        onClick = { onConfirmClassroom(student) },
                        enabled = !isSubmitting,
                        colors = ButtonDefaults.buttonColors(containerColor = BepsEmerald),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("teacher_checkin_${student.studentCode}")
                    ) {
                        Text("In Class")
                    }
                }
            }
        }
    }
}

@Composable
private fun TeacherAlertsTab(
    alerts: List<ManagementAlert>,
    onOpenNewAlert: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Button(
                onClick = onOpenNewAlert,
                colors = ButtonDefaults.buttonColors(containerColor = BepsCrimson),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("raise_alert_button")
            ) {
                Icon(Icons.Default.AddAlert, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.size(8.dp))
                Text("Report Uncollected / Delay Alert to Principal")
            }
        }

        if (alerts.isEmpty()) {
            item {
                EmptyStateCard(
                    title = "No Active Classroom Alerts",
                    subtitle = "Safety Rule #6 & #7: Uncollected students remain under supervision and alerts notify management immediately.",
                    icon = Icons.Default.ReportProblem
                )
            }
        } else {
            items(alerts, key = { it.id }) { alert ->
                AlertSummaryCard(alert = alert)
            }
        }
    }
}

@Composable
private fun TeacherRosterTab(
    students: List<Student>,
    modifier: Modifier = Modifier
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(students, query) {
        val q = query.trim().lowercase()
        if (q.isBlank()) students
        else students.filter {
            it.fullName.lowercase().contains(q) ||
                it.grade.lowercase().contains(q) ||
                it.studentCode.lowercase().contains(q)
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                placeholder = { Text("Search student name, grade, or code") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (filtered.isEmpty()) {
            item {
                EmptyStateCard(
                    title = "No Matching Students",
                    subtitle = "Try another grade or student ID.",
                    icon = Icons.Default.School
                )
            }
        } else {
            items(filtered, key = { "roster_${it.id}" }) { student ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        StudentAvatar(student.fullName, student.photoKey, student.statusEnum(), size = 46.dp)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(student.fullName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text("${student.grade} • ${student.studentCode}", style = MaterialTheme.typography.labelSmall)
                        }
                        StatusBadge(student.statusEnum())
                    }
                }
            }
        }
    }
}

@Composable
private fun TeacherMetricChip(
    label: String,
    value: String,
    containerColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = containerColor
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
fun AlertSummaryCard(
    alert: ManagementAlert,
    actionContent: (@Composable () -> Unit)? = null
) {
    val status = alert.statusEnum()
    val badgeBg = when (status) {
        AlertStatus.OPEN -> BepsCrimsonContainer
        AlertStatus.ACKNOWLEDGED -> BepsGoldContainer
        AlertStatus.RESOLVED -> BepsEmeraldContainer
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(color = badgeBg, shape = MaterialTheme.shapes.small) {
                    Text(
                        text = "${alert.alertTypeEnum().label} • ${status.label}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                Text(
                    text = alert.severityEnum().label,
                    style = MaterialTheme.typography.labelSmall,
                    color = BepsCrimson,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(alert.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(alert.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text = "Reported by ${alert.reportedByName} (${alert.reportedByRole})",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (actionContent != null) {
                actionContent()
            }
        }
    }
}

@Composable
fun RaiseAlertDialog(
    preselectedStudent: Student?,
    students: List<Student>,
    onDismiss: () -> Unit,
    onSubmit: (studentId: String, studentName: String, alertType: AlertType, severity: AlertSeverity, title: String, description: String) -> Unit
) {
    var selectedType by remember { mutableStateOf(AlertType.UNCOLLECTED_STUDENT) }
    var selectedSeverity by remember { mutableStateOf(AlertSeverity.HIGH) }
    var title by remember {
        mutableStateOf(
            if (preselectedStudent != null) "Uncollected / Delay: ${preselectedStudent.fullName}"
            else ""
        )
    }
    var description by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Dispatch Management Alert") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Alert Type", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(AlertType.UNCOLLECTED_STUDENT, AlertType.DELAY, AlertType.INCIDENT).forEach { type ->
                        FilterChip(
                            selected = selectedType == type,
                            onClick = { selectedType = type },
                            label = { Text(type.label) }
                        )
                    }
                }
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Alert Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Incident / Supervision Details") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val sId = preselectedStudent?.id ?: students.firstOrNull()?.id.orEmpty()
                    val sName = preselectedStudent?.fullName ?: "Campus General"
                    onSubmit(sId, sName, selectedType, selectedSeverity, title, description)
                },
                enabled = title.trim().length >= 3 && description.trim().length >= 3,
                colors = ButtonDefaults.buttonColors(containerColor = BepsCrimson)
            ) {
                Text("Send Alert")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
