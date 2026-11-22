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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.AttendanceLog
import com.example.data.model.BusRoute
import com.example.data.model.BusStatus
import com.example.data.model.LogEventType
import com.example.data.model.SchoolConfig
import com.example.data.model.Student
import com.example.data.model.StudentStatus
import com.example.ui.components.DispersalWindowBanner
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.QrPassDialog
import com.example.ui.components.StatusBadge
import com.example.ui.components.StudentAvatar
import com.example.ui.components.TimelineStepBar
import com.example.ui.theme.BepsCrimson
import com.example.ui.theme.BepsEmerald
import com.example.ui.theme.BepsEmeraldContainer
import com.example.ui.theme.BepsGoldContainer
import com.example.ui.theme.BepsTealContainer
import com.example.ui.theme.BepsTealInfo

@Composable
fun ParentPortalScreen(
    selectedTab: Int,
    students: List<Student>,
    logs: List<AttendanceLog>,
    busRoutes: List<BusRoute>,
    schoolConfig: SchoolConfig?,
    isAdminUser: Boolean,
    isSubmitting: Boolean,
    onRequestPickup: (Student) -> Unit,
    onAddGuardian: (Student, String) -> Unit,
    onSeedSampleData: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedStudentId by remember(students) { mutableStateOf(students.firstOrNull()?.id.orEmpty()) }
    val activeStudent = students.firstOrNull { it.id == selectedStudentId } ?: students.firstOrNull()
    var qrStudent by remember { mutableStateOf<Student?>(null) }
    var guardianDialogStudent by remember { mutableStateOf<Student?>(null) }

    if (qrStudent != null) {
        QrPassDialog(student = qrStudent!!, onDismiss = { qrStudent = null })
    }

    if (guardianDialogStudent != null) {
        AddGuardianDialog(
            student = guardianDialogStudent!!,
            onDismiss = { guardianDialogStudent = null },
            onConfirm = { entry ->
                onAddGuardian(guardianDialogStudent!!, entry)
                guardianDialogStudent = null
            }
        )
    }

    when (selectedTab) {
        0 -> ParentDashboardTab(
            students = students,
            activeStudent = activeStudent,
            schoolConfig = schoolConfig,
            isAdminUser = isAdminUser,
            isSubmitting = isSubmitting,
            onSelectStudent = { selectedStudentId = it.id },
            onShowQr = { qrStudent = it },
            onRequestPickup = onRequestPickup,
            onSeedSampleData = onSeedSampleData,
            modifier = modifier
        )
        1 -> ParentNotificationsTab(
            logs = logs,
            modifier = modifier
        )
        else -> ParentBusAndGuardiansTab(
            students = students,
            busRoutes = busRoutes,
            onAddGuardianClick = { guardianDialogStudent = it },
            modifier = modifier
        )
    }
}

@Composable
private fun ParentDashboardTab(
    students: List<Student>,
    activeStudent: Student?,
    schoolConfig: SchoolConfig?,
    isAdminUser: Boolean,
    isSubmitting: Boolean,
    onSelectStudent: (Student) -> Unit,
    onShowQr: (Student) -> Unit,
    onRequestPickup: (Student) -> Unit,
    onSeedSampleData: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dispersalOpen = schoolConfig?.dispersalOpen == true

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            DispersalWindowBanner(config = schoolConfig)
        }

        if (students.isEmpty()) {
            item {
                EmptyStateCard(
                    title = "No Linked Students Yet",
                    subtitle = if (isAdminUser) {
                        "Initialize the BEPS campus roster to test student arrival, classroom check-in, pickup commands, and QR gate handover."
                    } else {
                        "Ask the BEPS school administrator to link your child's student profile to your Google account email."
                    },
                    icon = Icons.Default.FamilyRestroom,
                    actionLabel = if (isAdminUser) "Initialize BEPS Sample Roster" else null,
                    onAction = if (isAdminUser) onSeedSampleData else null
                )
            }
            return@LazyColumn
        }

        if (students.size > 1) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "My Children (${students.size})",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(students, key = { it.id }) { child ->
                            FilterChip(
                                selected = child.id == activeStudent?.id,
                                onClick = { onSelectStudent(child) },
                                label = { Text("${child.fullName} • ${child.grade}") }
                            )
                        }
                    }
                }
            }
        }

        if (activeStudent != null) {
            item {
                val status = activeStudent.statusEnum()
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            StudentAvatar(
                                fullName = activeStudent.fullName,
                                photoKey = activeStudent.photoKey,
                                status = status,
                                size = 64.dp
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = activeStudent.fullName,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${activeStudent.grade} • ${activeStudent.studentCode}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                StatusBadge(status = status)
                            }
                        }

                        TimelineStepBar(status = status)

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "Today's Arrival",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = activeStudent.arrivalTimeLabel.ifBlank { "Pending Gate Scan" },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "Collection / Bus",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = activeStudent.collectionTimeLabel.ifBlank {
                                        activeStudent.transportModeEnum().label
                                    },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        if (activeStudent.verifiedCollectorName.isNotBlank()) {
                            Surface(
                                color = BepsEmeraldContainer,
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        Icons.Default.VerifiedUser,
                                        contentDescription = null,
                                        tint = BepsEmerald,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "Verified collector: ${activeStudent.verifiedCollectorName}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = BepsEmerald,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }

                        OutlinedButton(
                            onClick = { onShowQr(activeStudent) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("show_qr_pass_button")
                        ) {
                            Icon(Icons.Default.QrCode2, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.size(8.dp))
                            Text("Show Student QR Gate Pass")
                        }
                    }
                }
            }

            item {
                val status = activeStudent.statusEnum()
                val canRequestPickup = dispersalOpen && status == StudentStatus.IN_CLASSROOM && !isSubmitting
                val helperReason = when {
                    !dispersalOpen -> "Safety Control: Parent pickup commands are locked until BEPS opens the dispersal period."
                    status == StudentStatus.NOT_ARRIVED -> "Your child has not checked in at school yet."
                    status == StudentStatus.ARRIVED_AT_GATE -> "Awaiting classroom teacher attendance confirmation."
                    status == StudentStatus.PICKUP_REQUESTED -> "Pickup request received. Waiting for teacher to prepare your child."
                    status == StudentStatus.READY_FOR_PICKUP -> "Your child is ready at the pickup zone. Present your ID to Security."
                    status == StudentStatus.COLLECTED -> "Handover completed safely at ${activeStudent.collectionTimeLabel}."
                    status == StudentStatus.BOARDED_BUS -> "Student has safely boarded ${activeStudent.busRouteName}."
                    else -> "Press below to notify the classroom teacher and gate security."
                }

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
                            text = "Dispersal Command",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = helperReason,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Button(
                            onClick = { onRequestPickup(activeStudent) },
                            enabled = canRequestPickup,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = BepsEmerald,
                                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                            ),
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("request_pickup_button")
                        ) {
                            Icon(
                                imageVector = if (dispersalOpen) Icons.Default.NotificationsActive else Icons.Default.Lock,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.size(8.dp))
                            Text(
                                text = when (status) {
                                    StudentStatus.PICKUP_REQUESTED -> "PICKUP REQUEST SENT"
                                    StudentStatus.READY_FOR_PICKUP -> "READY AT PICKUP ZONE"
                                    StudentStatus.COLLECTED -> "SAFELY COLLECTED"
                                    StudentStatus.BOARDED_BUS -> "BOARDED SCHOOL BUS"
                                    else -> stringResource(id = R.string.request_my_child)
                                },
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Text(
                            text = "Safety rule: A pickup request is not automatic release. Teacher confirmation and Security guardian verification are mandatory.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ParentNotificationsTab(
    logs: List<AttendanceLog>,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "BEPS School Notifications",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Real-time arrival, pickup request, and verified collection updates.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (logs.isEmpty()) {
            item {
                EmptyStateCard(
                    title = "No Notifications Yet",
                    subtitle = "Arrival alerts, pickup confirmations, and collection receipts will appear here in real time.",
                    icon = Icons.Default.Notifications
                )
            }
            return@LazyColumn
        }

        items(logs, key = { it.id }) { log ->
            val event = log.eventTypeEnum()
            val (badgeBg, badgeFg) = when (event) {
                LogEventType.ARRIVAL, LogEventType.CLASSROOM_CHECKIN -> BepsEmeraldContainer to BepsEmerald
                LogEventType.PICKUP_REQUESTED, LogEventType.READY_FOR_PICKUP -> BepsGoldContainer to MaterialTheme.colorScheme.onSecondaryContainer
                LogEventType.HANDOVER_COMPLETED -> BepsEmeraldContainer to BepsEmerald
                LogEventType.BUS_BOARDED -> BepsTealContainer to BepsTealInfo
                LogEventType.PICKUP_REJECTED -> MaterialTheme.colorScheme.errorContainer to BepsCrimson
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
                                text = "BEPS • ${event.label.uppercase()}",
                                style = MaterialTheme.typography.labelSmall,
                                color = badgeFg,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                Icons.Default.Schedule,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = log.timeLabel,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Text(
                        text = log.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = log.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${log.studentName} (${log.grade}) • Staff: ${log.actorName}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ParentBusAndGuardiansTab(
    students: List<Student>,
    busRoutes: List<BusRoute>,
    onAddGuardianClick: (Student) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                text = "School Bus Monitoring",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }

        if (busRoutes.isEmpty()) {
            item {
                EmptyStateCard(
                    title = "No Bus Routes Assigned",
                    subtitle = "Private guardian pickup is active, or bus routes will appear once assigned by BEPS Transport.",
                    icon = Icons.Default.DirectionsBus
                )
            }
        } else {
            items(busRoutes, key = { it.id }) { route ->
                val busStatus = route.statusEnum()
                val progress = (route.boardedCount.toFloat() / route.capacity.coerceAtLeast(1L).toFloat()).coerceIn(0f, 1f)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
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
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.DirectionsBus, contentDescription = null, tint = BepsEmerald)
                                Text(
                                    text = "${route.routeNumber} • ${route.routeName}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Surface(
                                color = if (busStatus == BusStatus.DEPARTED) BepsGoldContainer else BepsEmeraldContainer,
                                shape = MaterialTheme.shapes.small
                            ) {
                                Text(
                                    text = busStatus.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                        Text(
                            text = "Driver: ${route.driverName} • Attendant: ${route.attendantName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Checkpoint: ${route.lastCheckpoint}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth(),
                            color = BepsEmerald
                        )
                        Text(
                            text = "Boarded: ${route.boardedCount} / ${route.capacity} students",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Authorized Guardians & Collectors",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Security verifies collectors against this approved list before releasing any student.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        items(students, key = { "guard_${it.id}" }) { student ->
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
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = student.fullName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = student.grade,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        OutlinedButton(
                            onClick = { onAddGuardianClick(student) },
                            modifier = Modifier.testTag("add_guardian_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.size(4.dp))
                            Text("Add")
                        }
                    }

                    student.authorizedGuardians.forEach { guardian ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = BepsEmerald,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = guardian,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AddGuardianDialog(
    student: Student,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var guardianName by remember { mutableStateOf("") }
    var relationship by remember { mutableStateOf("") }
    var emiratesIdTail by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Authorized Collector") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "For ${student.fullName} (${student.grade})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = guardianName,
                    onValueChange = { guardianName = it },
                    label = { Text("Full Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = relationship,
                    onValueChange = { relationship = it },
                    label = { Text("Relationship (e.g. Uncle, Driver)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = emiratesIdTail,
                    onValueChange = { emiratesIdTail = it },
                    label = { Text("Emirates ID (Last 4 digits)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val rel = relationship.trim().ifBlank { "Guardian" }
                    val idSuffix = emiratesIdTail.trim().takeIf { it.isNotBlank() }?.let { " • ID *$it" } ?: ""
                    onConfirm("${guardianName.trim()} ($rel$idSuffix)")
                },
                enabled = guardianName.trim().length >= 2
            ) {
                Text("Authorize")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
