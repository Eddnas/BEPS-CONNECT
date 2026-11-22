package com.example.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import com.example.data.model.BusRoute
import com.example.data.model.BusStatus
import com.example.data.model.Student
import com.example.data.model.StudentStatus
import com.example.data.model.TransportMode
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.StatusBadge
import com.example.ui.components.StudentAvatar
import com.example.ui.theme.BepsCrimson
import com.example.ui.theme.BepsEmerald
import com.example.ui.theme.BepsEmeraldContainer
import com.example.ui.theme.BepsGoldContainer

@Composable
fun SecurityPortalScreen(
    selectedTab: Int,
    students: List<Student>,
    busRoutes: List<BusRoute>,
    isSubmitting: Boolean,
    onRecordArrival: (Student) -> Unit,
    onConfirmHandover: (Student, String) -> Unit,
    onRejectPickup: (Student, String) -> Unit,
    onConfirmBusBoarding: (Student, BusRoute?) -> Unit,
    onUpdateBusStatus: (BusRoute, BusStatus, String) -> Unit,
    modifier: Modifier = Modifier
) {
    var activeStudent by remember { mutableStateOf<Student?>(null) }
    var handoverTarget by remember { mutableStateOf<Student?>(null) }
    var rejectTarget by remember { mutableStateOf<Student?>(null) }

    val refreshedActive = activeStudent?.let { sel -> students.firstOrNull { it.id == sel.id } ?: sel }

    if (handoverTarget != null) {
        HandoverVerificationDialog(
            student = handoverTarget!!,
            onDismiss = { handoverTarget = null },
            onConfirm = { collector ->
                onConfirmHandover(handoverTarget!!, collector)
                handoverTarget = null
            }
        )
    }

    if (rejectTarget != null) {
        RejectPickupDialog(
            student = rejectTarget!!,
            onDismiss = { rejectTarget = null },
            onConfirm = { reason ->
                onRejectPickup(rejectTarget!!, reason)
                rejectTarget = null
            }
        )
    }

    when (selectedTab) {
        0 -> SecurityGateScanTab(
            students = students,
            busRoutes = busRoutes,
            selectedStudent = refreshedActive,
            isSubmitting = isSubmitting,
            onSelectStudent = { activeStudent = it },
            onRecordArrival = onRecordArrival,
            onOpenHandover = { handoverTarget = it },
            onOpenReject = { rejectTarget = it },
            onConfirmBusBoarding = onConfirmBusBoarding,
            modifier = modifier
        )
        1 -> SecurityHandoverQueueTab(
            students = students,
            onOpenHandover = { handoverTarget = it },
            onOpenReject = { rejectTarget = it },
            modifier = modifier
        )
        else -> SecurityBusBayTab(
            students = students,
            busRoutes = busRoutes,
            isSubmitting = isSubmitting,
            onConfirmBusBoarding = onConfirmBusBoarding,
            onUpdateBusStatus = onUpdateBusStatus,
            modifier = modifier
        )
    }
}

@Composable
private fun SecurityGateScanTab(
    students: List<Student>,
    busRoutes: List<BusRoute>,
    selectedStudent: Student?,
    isSubmitting: Boolean,
    onSelectStudent: (Student) -> Unit,
    onRecordArrival: (Student) -> Unit,
    onOpenHandover: (Student) -> Unit,
    onOpenReject: (Student) -> Unit,
    onConfirmBusBoarding: (Student, BusRoute?) -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var scanNotice by remember { mutableStateOf<String?>(null) }

    val matchingStudents = remember(students, searchQuery) {
        val q = searchQuery.trim().lowercase()
        if (q.isBlank()) students
        else students.filter {
            it.studentCode.lowercase().contains(q) ||
                it.fullName.lowercase().contains(q) ||
                it.grade.lowercase().contains(q)
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            CameraQrScannerBox(
                onQrScanned = { raw ->
                    val code = raw.removePrefix("BEPS:STUDENT:").trim()
                    val found = students.firstOrNull {
                        it.studentCode.equals(code, ignoreCase = true) ||
                            it.qrPayload().equals(raw, ignoreCase = true)
                    }
                    if (found != null) {
                        scanNotice = "Verified badge: ${found.fullName} (${found.studentCode})"
                        onSelectStudent(found)
                    } else {
                        scanNotice = "Unrecognized QR code: $raw"
                    }
                }
            )
        }

        if (scanNotice != null) {
            item {
                Surface(
                    color = BepsEmeraldContainer,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = scanNotice.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = BepsEmerald,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }

        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                placeholder = { Text("Search Student ID (e.g. BEPS-2026-0101) or Name") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("security_search_input")
            )
        }

        if (selectedStudent != null) {
            item {
                SecurityStudentActionCard(
                    student = selectedStudent,
                    busRoute = busRoutes.firstOrNull { it.id == selectedStudent.busRouteId },
                    isSubmitting = isSubmitting,
                    onRecordArrival = { onRecordArrival(selectedStudent) },
                    onOpenHandover = { onOpenHandover(selectedStudent) },
                    onOpenReject = { onOpenReject(selectedStudent) },
                    onConfirmBusBoarding = { route -> onConfirmBusBoarding(selectedStudent, route) }
                )
            }
        }

        item {
            Text(
                text = "Tap a Student to Process Gate Check-In / Exit",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        items(matchingStudents, key = { "sec_${it.id}" }) { student ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectStudent(student) },
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(
                    containerColor = if (selectedStudent?.id == student.id) BepsEmeraldContainer.copy(alpha = 0.45f)
                    else MaterialTheme.colorScheme.surface
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StudentAvatar(student.fullName, student.photoKey, student.statusEnum(), size = 44.dp)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(student.fullName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text("${student.studentCode} • ${student.grade}", style = MaterialTheme.typography.labelSmall)
                    }
                    StatusBadge(student.statusEnum())
                }
            }
        }
    }
}

@Composable
private fun SecurityStudentActionCard(
    student: Student,
    busRoute: BusRoute?,
    isSubmitting: Boolean,
    onRecordArrival: () -> Unit,
    onOpenHandover: () -> Unit,
    onOpenReject: () -> Unit,
    onConfirmBusBoarding: (BusRoute?) -> Unit
) {
    val status = student.statusEnum()
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StudentAvatar(student.fullName, student.photoKey, status, size = 56.dp)
                Column(modifier = Modifier.weight(1f)) {
                    Text(student.fullName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("${student.grade} • ${student.studentCode}", style = MaterialTheme.typography.labelMedium)
                }
                StatusBadge(status)
            }

            Text(
                text = "Authorized Collectors: ${student.authorizedGuardians.joinToString(" | ").ifBlank { "None listed" }}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            HorizontalDivider()

            when (status) {
                StudentStatus.NOT_ARRIVED -> {
                    Button(
                        onClick = onRecordArrival,
                        enabled = !isSubmitting,
                        colors = ButtonDefaults.buttonColors(containerColor = BepsEmerald),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("record_arrival_button")
                    ) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = null)
                        Spacer(modifier = Modifier.size(8.dp))
                        Text("Record Gate Arrival")
                    }
                }
                StudentStatus.READY_FOR_PICKUP -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onOpenHandover,
                            enabled = !isSubmitting,
                            colors = ButtonDefaults.buttonColors(containerColor = BepsEmerald),
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .testTag("verify_handover_button")
                        ) {
                            Icon(Icons.Default.VerifiedUser, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.size(6.dp))
                            Text("Verify & Handover")
                        }
                        OutlinedButton(
                            onClick = onOpenReject,
                            enabled = !isSubmitting,
                            modifier = Modifier.height(50.dp)
                        ) {
                            Icon(Icons.Default.Block, contentDescription = "Reject", tint = BepsCrimson)
                        }
                    }
                }
                StudentStatus.PICKUP_REQUESTED -> {
                    Text(
                        text = "Safety Rule #3: Awaiting classroom teacher confirmation before physical release.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = BepsCrimson,
                        fontWeight = FontWeight.SemiBold
                    )
                    OutlinedButton(
                        onClick = onOpenReject,
                        enabled = !isSubmitting,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Hold & Notify Principal", color = BepsCrimson)
                    }
                }
                StudentStatus.IN_CLASSROOM -> {
                    if (student.transportModeEnum() == TransportMode.SCHOOL_BUS) {
                        Button(
                            onClick = { onConfirmBusBoarding(busRoute) },
                            enabled = !isSubmitting,
                            colors = ButtonDefaults.buttonColors(containerColor = BepsEmerald),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                        ) {
                            Icon(Icons.Default.DirectionsBus, contentDescription = null)
                            Spacer(modifier = Modifier.size(8.dp))
                            Text("Confirm Bus Boarding (${student.busRouteName})")
                        }
                    } else {
                        Text(
                            text = "Student is safely in ${student.grade}. Awaiting parent pickup command during dispersal.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                StudentStatus.ARRIVED_AT_GATE -> {
                    Text(
                        text = "Checked in at ${student.arrivalTimeLabel}. Awaiting classroom teacher confirmation.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                StudentStatus.COLLECTED -> {
                    Text(
                        text = "Collected at ${student.collectionTimeLabel} by ${student.verifiedCollectorName}.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = BepsEmerald,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                StudentStatus.BOARDED_BUS -> {
                    Text(
                        text = "Boarded ${student.busRouteName} at ${student.collectionTimeLabel}.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = BepsEmerald,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun SecurityHandoverQueueTab(
    students: List<Student>,
    onOpenHandover: (Student) -> Unit,
    onOpenReject: (Student) -> Unit,
    modifier: Modifier = Modifier
) {
    val readyQueue = students.filter { it.statusEnum() == StudentStatus.READY_FOR_PICKUP }
    val pendingTeacher = students.filter { it.statusEnum() == StudentStatus.PICKUP_REQUESTED }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "Verified Handover Queue (${readyQueue.size})",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Safety Rules #4 & #5: Verify collector ID against authorized guardians before confirming release.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (readyQueue.isEmpty()) {
            item {
                EmptyStateCard(
                    title = "No Students Waiting at Gate",
                    subtitle = if (pendingTeacher.isNotEmpty()) {
                        "${pendingTeacher.size} pickup request(s) are awaiting teacher readiness confirmation."
                    } else {
                        "Students confirmed ready by their teachers will appear here for guardian verification."
                    },
                    icon = Icons.Default.Security
                )
            }
        } else {
            items(readyQueue, key = { "ready_${it.id}" }) { student ->
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
                                Text("${student.grade} • ${student.studentCode}", style = MaterialTheme.typography.labelSmall)
                            }
                            StatusBadge(student.statusEnum())
                        }
                        Text(
                            text = "Approved Collectors: ${student.authorizedGuardians.joinToString(", ")}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { onOpenHandover(student) },
                                colors = ButtonDefaults.buttonColors(containerColor = BepsEmerald),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.size(6.dp))
                                Text("Confirm Handover")
                            }
                            OutlinedButton(
                                onClick = { onOpenReject(student) },
                                modifier = Modifier.height(48.dp)
                            ) {
                                Text("Reject", color = BepsCrimson)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SecurityBusBayTab(
    students: List<Student>,
    busRoutes: List<BusRoute>,
    isSubmitting: Boolean,
    onConfirmBusBoarding: (Student, BusRoute?) -> Unit,
    onUpdateBusStatus: (BusRoute, BusStatus, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val busStudents = students.filter {
        it.transportModeEnum() == TransportMode.SCHOOL_BUS &&
            (it.statusEnum() == StudentStatus.IN_CLASSROOM || it.statusEnum() == StudentStatus.READY_FOR_PICKUP)
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "Bus Bay Boarding & Dispatch",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }

        items(busRoutes, key = { "sec_bus_${it.id}" }) { route ->
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
                            Text("${route.routeNumber} • ${route.routeName}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("Boarded ${route.boardedCount}/${route.capacity} • ${route.lastCheckpoint}", style = MaterialTheme.typography.bodySmall)
                        }
                        Surface(
                            color = if (route.statusEnum() == BusStatus.DEPARTED) BepsGoldContainer else BepsEmeraldContainer,
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(
                                text = route.statusEnum().label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(BusStatus.BOARDING, BusStatus.DEPARTED, BusStatus.COMPLETED).forEach { nextStatus ->
                            FilterChip(
                                selected = route.statusEnum() == nextStatus,
                                onClick = {
                                    val checkpoint = when (nextStatus) {
                                        BusStatus.BOARDING -> "BEPS Bus Bay Boarding"
                                        BusStatus.DEPARTED -> "Departed BEPS RAK Campus"
                                        BusStatus.COMPLETED -> "Final Drop-off Completed"
                                        BusStatus.AT_SCHOOL -> "BEPS Main Bus Bay"
                                    }
                                    onUpdateBusStatus(route, nextStatus, checkpoint)
                                },
                                label = { Text(nextStatus.label) }
                            )
                        }
                    }
                }
            }
        }

        if (busStudents.isNotEmpty()) {
            item {
                Text(
                    text = "Students Awaiting Bus Boarding (${busStudents.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            items(busStudents, key = { "bus_stu_${it.id}" }) { student ->
                val route = busRoutes.firstOrNull { it.id == student.busRouteId }
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
                        StudentAvatar(student.fullName, student.photoKey, student.statusEnum(), size = 44.dp)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(student.fullName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(student.busRouteName, style = MaterialTheme.typography.bodySmall)
                        }
                        Button(
                            onClick = { onConfirmBusBoarding(student, route) },
                            enabled = !isSubmitting,
                            colors = ButtonDefaults.buttonColors(containerColor = BepsEmerald),
                            modifier = Modifier.height(48.dp)
                        ) {
                            Text("Board Bus")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HandoverVerificationDialog(
    student: Student,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val guardians = student.authorizedGuardians.ifEmpty { listOf("${student.parentName} (Parent)") }
    var selectedGuardian by remember { mutableStateOf(guardians.first()) }
    var customCollector by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Verify Authorized Collector") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Releasing ${student.fullName} (${student.grade}). Select verified guardian or enter ID details:",
                    style = MaterialTheme.typography.bodySmall
                )
                guardians.forEach { entry ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedGuardian = entry
                                customCollector = ""
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedGuardian == entry && customCollector.isBlank(),
                            onClick = {
                                selectedGuardian = entry
                                customCollector = ""
                            }
                        )
                        Text(entry, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                OutlinedTextField(
                    value = customCollector,
                    onValueChange = { customCollector = it },
                    label = { Text("Or enter verified ID / collector name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val chosen = customCollector.trim().ifBlank { selectedGuardian }
                    onConfirm(chosen)
                },
                colors = ButtonDefaults.buttonColors(containerColor = BepsEmerald)
            ) {
                Text("Confirm Handover")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun RejectPickupDialog(
    student: Student,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var reason by remember { mutableStateOf("Collector not listed on authorized guardian profile") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Hold Student & Alert Principal") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Safety Rule #6 & #7: ${student.fullName} remains under school supervision and management receives an immediate alert.",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Reason for holding pickup") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(reason) },
                enabled = reason.trim().length >= 3,
                colors = ButtonDefaults.buttonColors(containerColor = BepsCrimson)
            ) {
                Text("Hold & Send Alert")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
