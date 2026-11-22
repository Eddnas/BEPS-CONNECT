package com.example.ui.screens

import android.content.Intent
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.School
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.AlertStatus
import com.example.data.model.AttendanceLog
import com.example.data.model.BusRoute
import com.example.data.model.ManagementAlert
import com.example.data.model.RequestStatus
import com.example.data.model.RoleRequest
import com.example.data.model.SchoolConfig
import com.example.data.model.Student
import com.example.data.model.StudentStatus
import com.example.data.model.TransportMode
import com.example.data.model.UserProfile
import com.example.data.model.UserRole
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.StatusBadge
import com.example.ui.components.StudentAvatar
import com.example.ui.theme.BepsCrimson
import com.example.ui.theme.BepsEmerald
import com.example.ui.theme.BepsEmeraldContainer
import com.example.ui.theme.BepsGoldContainer
import com.example.util.QrAndPdfUtils
import kotlinx.coroutines.launch

@Composable
fun AdminPortalScreen(
    selectedTab: Int,
    schoolConfig: SchoolConfig?,
    students: List<Student>,
    logs: List<AttendanceLog>,
    busRoutes: List<BusRoute>,
    alerts: List<ManagementAlert>,
    allUsers: List<UserProfile>,
    roleRequests: List<RoleRequest>,
    isSubmitting: Boolean,
    onToggleDispersal: (Boolean, String, String) -> Unit,
    onSeedSampleData: () -> Unit,
    onResetDailyRoster: () -> Unit,
    onEnrollStudent: (
        studentCode: String,
        fullName: String,
        grade: String,
        parentUid: String,
        parentName: String,
        parentEmail: String,
        guardianName: String,
        transportMode: TransportMode,
        busRoute: BusRoute?
    ) -> Unit,
    onUpdateAlertStatus: (String, AlertStatus) -> Unit,
    onResolveRoleRequest: (RoleRequest, Boolean) -> Unit,
    onUpdateUserRole: (String, UserRole, String) -> Unit,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showEnrollDialog by remember { mutableStateOf(false) }
    var showResetConfirm by remember { mutableStateOf(false) }
    var roleEditTarget by remember { mutableStateOf<UserProfile?>(null) }

    if (showEnrollDialog) {
        EnrollStudentDialog(
            busRoutes = busRoutes,
            users = allUsers,
            onDismiss = { showEnrollDialog = false },
            onConfirm = { code, name, grade, pUid, pName, pEmail, guard, mode, route ->
                onEnrollStudent(code, name, grade, pUid, pName, pEmail, guard, mode, route)
                showEnrollDialog = false
            }
        )
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("Start New School Day?") },
            text = { Text("This resets all student statuses to Not Arrived and clears today's arrival/collection timestamps.") },
            confirmButton = {
                Button(
                    onClick = {
                        onResetDailyRoster()
                        showResetConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BepsCrimson)
                ) {
                    Text("Reset Roster")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) { Text("Cancel") }
            }
        )
    }

    if (roleEditTarget != null) {
        EditUserRoleDialog(
            user = roleEditTarget!!,
            onDismiss = { roleEditTarget = null },
            onConfirm = { role, grade ->
                onUpdateUserRole(roleEditTarget!!.userId, role, grade)
                roleEditTarget = null
            }
        )
    }

    when (selectedTab) {
        0 -> AdminCommandTab(
            schoolConfig = schoolConfig,
            students = students,
            logs = logs,
            alerts = alerts,
            isSubmitting = isSubmitting,
            onToggleDispersal = onToggleDispersal,
            onSeedSampleData = onSeedSampleData,
            onRequestReset = { showResetConfirm = true },
            onShowMessage = onShowMessage,
            modifier = modifier
        )
        1 -> AdminRegistryTab(
            students = students,
            busRoutes = busRoutes,
            onOpenEnroll = { showEnrollDialog = true },
            modifier = modifier
        )
        else -> AdminAlertsAndStaffTab(
            alerts = alerts,
            allUsers = allUsers,
            roleRequests = roleRequests,
            onUpdateAlertStatus = onUpdateAlertStatus,
            onResolveRoleRequest = onResolveRoleRequest,
            onEditUserRole = { roleEditTarget = it },
            modifier = modifier
        )
    }
}

@Composable
private fun AdminCommandTab(
    schoolConfig: SchoolConfig?,
    students: List<Student>,
    logs: List<AttendanceLog>,
    alerts: List<ManagementAlert>,
    isSubmitting: Boolean,
    onToggleDispersal: (Boolean, String, String) -> Unit,
    onSeedSampleData: () -> Unit,
    onRequestReset: () -> Unit,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var dispersalOpen by remember(schoolConfig) { mutableStateOf(schoolConfig?.dispersalOpen == true) }
    var windowLabel by remember(schoolConfig) {
        mutableStateOf(schoolConfig?.dispersalWindowLabel?.ifBlank { "01:00 PM - 02:15 PM" } ?: "01:00 PM - 02:15 PM")
    }
    var notice by remember(schoolConfig) {
        mutableStateOf(schoolConfig?.activeNotice ?: "Afternoon dispersal window active.")
    }

    val arrivedCount = students.count { it.statusEnum() != StudentStatus.NOT_ARRIVED }
    val readyOrRequested = students.count {
        it.statusEnum() == StudentStatus.PICKUP_REQUESTED || it.statusEnum() == StudentStatus.READY_FOR_PICKUP
    }
    val collectedCount = students.count {
        it.statusEnum() == StudentStatus.COLLECTED || it.statusEnum() == StudentStatus.BOARDED_BUS
    }
    val openAlerts = alerts.count { it.statusEnum() != AlertStatus.RESOLVED }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Campus Dispersal Gate Control",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Safety Rule #1: Controls whether parents can submit pickup requests.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = dispersalOpen,
                            onCheckedChange = {
                                dispersalOpen = it
                                onToggleDispersal(it, windowLabel, notice)
                            },
                            modifier = Modifier.testTag("toggle_dispersal_switch")
                        )
                    }

                    OutlinedTextField(
                        value = windowLabel,
                        onValueChange = { windowLabel = it },
                        label = { Text("Dispersal Hours (UAE)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = notice,
                        onValueChange = { notice = it },
                        label = { Text("Live Campus Notice") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = { onToggleDispersal(dispersalOpen, windowLabel, notice) },
                        enabled = !isSubmitting,
                        colors = ButtonDefaults.buttonColors(containerColor = BepsEmerald),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Text("Broadcast Campus Schedule")
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AdminKpiBox("Arrived", "$arrivedCount/${students.size}", BepsEmeraldContainer, Modifier.weight(1f))
                AdminKpiBox("Active Queue", "$readyOrRequested", BepsGoldContainer, Modifier.weight(1f))
                AdminKpiBox("Released", "$collectedCount", BepsEmeraldContainer, Modifier.weight(1f))
                AdminKpiBox("Open Alerts", "$openAlerts", MaterialTheme.colorScheme.errorContainer, Modifier.weight(1f))
            }
        }

        if (students.isEmpty()) {
            item {
                EmptyStateCard(
                    title = "Campus Roster Empty",
                    subtitle = "Seed sample BEPS students, school buses, and morning arrival records into Firestore to test the full arrival-to-handover workflow.",
                    icon = Icons.Default.AdminPanelSettings,
                    actionLabel = "Initialize BEPS Sample Roster",
                    onAction = onSeedSampleData
                )
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Operations & Daily Reporting",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Button(
                        onClick = {
                            scope.launch {
                                val res = QrAndPdfUtils.exportDailyPdfReport(context, students, logs, alerts)
                                res.onSuccess { uri ->
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "application/pdf"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(
                                        Intent.createChooser(shareIntent, "Share BEPS Daily PDF Report")
                                    )
                                    onShowMessage("Daily PDF report generated (${students.size} students, ${logs.size} logs).")
                                }.onFailure { err ->
                                    onShowMessage(err.localizedMessage ?: "PDF export failed")
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = BepsEmerald),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("export_pdf_button")
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null)
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(stringResource(id = R.string.export_daily_pdf))
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onSeedSampleData,
                            enabled = !isSubmitting,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.size(4.dp))
                            Text("Seed Roster")
                        }
                        OutlinedButton(
                            onClick = onRequestReset,
                            enabled = !isSubmitting && students.isNotEmpty(),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                        ) {
                            Icon(Icons.Default.RestartAlt, contentDescription = null, tint = BepsCrimson, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.size(4.dp))
                            Text("New Day", color = BepsCrimson)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminRegistryTab(
    students: List<Student>,
    busRoutes: List<BusRoute>,
    onOpenEnroll: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Button(
                onClick = onOpenEnroll,
                colors = ButtonDefaults.buttonColors(containerColor = BepsEmerald),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("enroll_student_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.size(8.dp))
                Text("Enroll New BEPS Student")
            }
        }

        item {
            Text(
                text = "Enrolled Students (${students.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        items(students, key = { "adm_stu_${it.id}" }) { student ->
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
                        Text("${student.studentCode} • ${student.grade}", style = MaterialTheme.typography.labelSmall)
                        Text("Parent: ${student.parentName} • ${student.busRouteName}", style = MaterialTheme.typography.bodySmall)
                    }
                    StatusBadge(student.statusEnum())
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "School Bus Fleet (${busRoutes.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        items(busRoutes, key = { "adm_bus_${it.id}" }) { route ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${route.routeNumber} • ${route.routeName}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text("Driver: ${route.driverName} • Attendant: ${route.attendantName}", style = MaterialTheme.typography.bodySmall)
                    Text("Status: ${route.statusEnum().label} • Boarded: ${route.boardedCount}/${route.capacity}", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun AdminAlertsAndStaffTab(
    alerts: List<ManagementAlert>,
    allUsers: List<UserProfile>,
    roleRequests: List<RoleRequest>,
    onUpdateAlertStatus: (String, AlertStatus) -> Unit,
    onResolveRoleRequest: (RoleRequest, Boolean) -> Unit,
    onEditUserRole: (UserProfile) -> Unit,
    modifier: Modifier = Modifier
) {
    val pendingRequests = roleRequests.filter { it.statusEnum() == RequestStatus.PENDING }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "Management Incident Alerts (${alerts.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        if (alerts.isEmpty()) {
            item {
                EmptyStateCard(
                    title = "All Clear — Zero Open Alerts",
                    subtitle = "Delays, rejected pickup attempts, and uncollected student alerts appear here.",
                    icon = Icons.Default.CheckCircle
                )
            }
        } else {
            items(alerts, key = { "adm_al_${it.id}" }) { alert ->
                AlertSummaryCard(alert = alert) {
                    if (alert.statusEnum() != AlertStatus.RESOLVED) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (alert.statusEnum() == AlertStatus.OPEN) {
                                OutlinedButton(
                                    onClick = { onUpdateAlertStatus(alert.id, AlertStatus.ACKNOWLEDGED) },
                                    modifier = Modifier.height(44.dp)
                                ) {
                                    Text("Acknowledge")
                                }
                            }
                            Button(
                                onClick = { onUpdateAlertStatus(alert.id, AlertStatus.RESOLVED) },
                                colors = ButtonDefaults.buttonColors(containerColor = BepsEmerald),
                                modifier = Modifier.height(44.dp)
                            ) {
                                Text("Resolve")
                            }
                        }
                    }
                }
            }
        }

        if (pendingRequests.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Pending Staff Role Requests (${pendingRequests.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            items(pendingRequests, key = { "req_role_${it.id}" }) { req ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("${req.displayName} (${req.email})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text("Requested Role: ${req.requestedRoleEnum().label} • ${req.note}", style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { onResolveRoleRequest(req, true) },
                                colors = ButtonDefaults.buttonColors(containerColor = BepsEmerald)
                            ) { Text("Approve") }
                            OutlinedButton(onClick = { onResolveRoleRequest(req, false) }) {
                                Text("Decline", color = BepsCrimson)
                            }
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Registered Staff & Parent Directory (${allUsers.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        items(allUsers, key = { "usr_${it.id}" }) { user ->
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
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(user.displayName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text(user.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = "${user.roleEnum().label}${user.assignedGrade.takeIf { it.isNotBlank() }?.let { " • $it" } ?: ""}",
                            style = MaterialTheme.typography.labelSmall,
                            color = BepsEmerald
                        )
                    }
                    OutlinedButton(onClick = { onEditUserRole(user) }) {
                        Text("Role")
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminKpiBox(
    label: String,
    value: String,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Surface(modifier = modifier, shape = MaterialTheme.shapes.medium, color = color) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun EnrollStudentDialog(
    busRoutes: List<BusRoute>,
    users: List<UserProfile>,
    onDismiss: () -> Unit,
    onConfirm: (
        studentCode: String,
        fullName: String,
        grade: String,
        parentUid: String,
        parentName: String,
        parentEmail: String,
        guardianName: String,
        transportMode: TransportMode,
        busRoute: BusRoute?
    ) -> Unit
) {
    var code by remember { mutableStateOf("BEPS-2026-0105") }
    var fullName by remember { mutableStateOf("") }
    var grade by remember { mutableStateOf("Grade 4 - A") }
    var parentEmail by remember { mutableStateOf("") }
    var guardianName by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf(TransportMode.PRIVATE_PICKUP) }
    var selectedRoute by remember { mutableStateOf(busRoutes.firstOrNull()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Enroll BEPS Student") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = code, onValueChange = { code = it }, label = { Text("Student ID Code") }, singleLine = true)
                OutlinedTextField(value = fullName, onValueChange = { fullName = it }, label = { Text("Student Full Name") }, singleLine = true)
                OutlinedTextField(value = grade, onValueChange = { grade = it }, label = { Text("Grade & Section") }, singleLine = true)
                OutlinedTextField(value = parentEmail, onValueChange = { parentEmail = it }, label = { Text("Parent Email (optional)") }, singleLine = true)
                OutlinedTextField(value = guardianName, onValueChange = { guardianName = it }, label = { Text("Authorized Collector") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TransportMode.entries.forEach { m ->
                        FilterChip(
                            selected = mode == m,
                            onClick = { mode = m },
                            label = { Text(if (m == TransportMode.SCHOOL_BUS) "School Bus" else "Private Pickup") }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val matchedParent = users.firstOrNull { it.email.equals(parentEmail.trim(), ignoreCase = true) }
                    onConfirm(
                        code,
                        fullName,
                        grade,
                        matchedParent?.userId.orEmpty(),
                        matchedParent?.displayName ?: "BEPS Parent",
                        matchedParent?.email ?: parentEmail,
                        guardianName,
                        mode,
                        if (mode == TransportMode.SCHOOL_BUS) selectedRoute else null
                    )
                },
                enabled = code.trim().length >= 3 && fullName.trim().length >= 2 && grade.trim().length >= 2
            ) {
                Text("Enroll")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun EditUserRoleDialog(
    user: UserProfile,
    onDismiss: () -> Unit,
    onConfirm: (UserRole, String) -> Unit
) {
    var selectedRole by remember { mutableStateOf(user.roleEnum()) }
    var assignedGrade by remember { mutableStateOf(user.assignedGrade) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Assign Staff Role") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("${user.displayName} (${user.email})", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    UserRole.entries.forEach { role ->
                        FilterChip(
                            selected = selectedRole == role,
                            onClick = { selectedRole = role },
                            label = { Text(role.label) }
                        )
                    }
                }
                OutlinedTextField(
                    value = assignedGrade,
                    onValueChange = { assignedGrade = it },
                    label = { Text("Assigned Grade / Gate") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(selectedRole, assignedGrade) }) {
                Text("Save Role")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
