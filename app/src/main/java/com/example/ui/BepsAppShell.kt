package com.example.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Class
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.auth.signOut
import com.example.data.model.UiState
import com.example.data.model.UserRole
import com.example.ui.components.BepsCrestBadge
import com.example.ui.components.EmptyStateCard
import com.example.ui.screens.AdminPortalScreen
import com.example.ui.screens.ParentPortalScreen
import com.example.ui.screens.SecurityPortalScreen
import com.example.ui.screens.TeacherPortalScreen
import com.example.ui.theme.BepsCrimson
import com.example.ui.theme.BepsCrimsonContainer
import com.example.ui.theme.BepsEmerald
import com.example.ui.theme.BepsEmeraldContainer
import com.example.viewmodel.BepsViewModel

private data class NavTabItem(
    val label: String,
    val icon: ImageVector
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BepsAppShell(
    viewModel: BepsViewModel
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val snackbarHostState = remember { SnackbarHostState() }

    val userProfileState by viewModel.userProfileState.collectAsStateWithLifecycle()
    val schoolConfigState by viewModel.schoolConfigState.collectAsStateWithLifecycle()
    val effectiveRole by viewModel.effectiveRole.collectAsStateWithLifecycle()
    val studentsState by viewModel.studentsState.collectAsStateWithLifecycle()
    val logsState by viewModel.logsState.collectAsStateWithLifecycle()
    val busRoutesState by viewModel.busRoutesState.collectAsStateWithLifecycle()
    val alertsState by viewModel.alertsState.collectAsStateWithLifecycle()
    val allUsersState by viewModel.allUsersState.collectAsStateWithLifecycle()
    val roleRequestsState by viewModel.roleRequestsState.collectAsStateWithLifecycle()
    val snackbarMessage by viewModel.snackbarMessage.collectAsStateWithLifecycle()
    val isSubmitting by viewModel.isSubmitting.collectAsStateWithLifecycle()

    var selectedTab by rememberSaveable(effectiveRole) { mutableIntStateOf(0) }
    var showAccountDialog by remember { mutableStateOf(false) }
    var showRoleRequestDialog by remember { mutableStateOf(false) }

    LaunchedEffect(snackbarMessage) {
        val msg = snackbarMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearSnackbar()
        }
    }

    val assignedProfile = (userProfileState as? UiState.Success)?.data
    val canSwitchRoles = viewModel.isBootstrappedAdmin || assignedProfile?.roleEnum() == UserRole.ADMIN
    val schoolConfig = (schoolConfigState as? UiState.Success)?.data
    val students = (studentsState as? UiState.Success)?.data.orEmpty()
    val logs = (logsState as? UiState.Success)?.data.orEmpty()
    val busRoutes = (busRoutesState as? UiState.Success)?.data.orEmpty()
    val alerts = (alertsState as? UiState.Success)?.data.orEmpty()
    val allUsers = (allUsersState as? UiState.Success)?.data.orEmpty()
    val roleRequests = (roleRequestsState as? UiState.Success)?.data.orEmpty()

    val tabs = remember(effectiveRole) {
        when (effectiveRole) {
            UserRole.PARENT -> listOf(
                NavTabItem("My Child", Icons.Default.FamilyRestroom),
                NavTabItem("Alerts", Icons.Default.Notifications),
                NavTabItem("Bus & IDs", Icons.Default.DirectionsBus)
            )
            UserRole.TEACHER -> listOf(
                NavTabItem("Classroom", Icons.Default.Class),
                NavTabItem("Incidents", Icons.Default.ReportProblem),
                NavTabItem("Roster", Icons.AutoMirrored.Filled.ListAlt)
            )
            UserRole.SECURITY -> listOf(
                NavTabItem("Gate Scan", Icons.Default.QrCodeScanner),
                NavTabItem("Handover", Icons.Default.VerifiedUser),
                NavTabItem("Bus Bay", Icons.Default.DirectionsBus)
            )
            UserRole.ADMIN -> listOf(
                NavTabItem("Command", Icons.Default.Dashboard),
                NavTabItem("Registry", Icons.AutoMirrored.Filled.ListAlt),
                NavTabItem("Staff & Alerts", Icons.Default.AdminPanelSettings)
            )
        }
    }

    if (showAccountDialog) {
        AlertDialog(
            onDismissRequest = { showAccountDialog = false },
            title = { Text("BEPS Connect Account") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = assignedProfile?.displayName ?: viewModel.currentUserName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = viewModel.currentUserEmail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Surface(color = BepsEmeraldContainer, shape = MaterialTheme.shapes.small) {
                        Text(
                            text = "Assigned Role: ${assignedProfile?.roleEnum()?.label ?: if (canSwitchRoles) "Admin" else "Parent"}",
                            style = MaterialTheme.typography.labelMedium,
                            color = BepsEmerald,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                    if (!canSwitchRoles) {
                        OutlinedButton(
                            onClick = {
                                showAccountDialog = false
                                showRoleRequestDialog = true
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Badge, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.size(6.dp))
                            Text("Request Teacher / Security Access")
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showAccountDialog = false
                        signOut(credentialManager, scope)
                    },
                    modifier = Modifier.testTag("sign_out_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.size(6.dp))
                    Text(stringResource(id = R.string.sign_out))
                }
            },
            dismissButton = {
                TextButton(onClick = { showAccountDialog = false }) { Text("Close") }
            }
        )
    }

    if (showRoleRequestDialog) {
        RoleRequestDialog(
            onDismiss = { showRoleRequestDialog = false },
            onSubmit = { role, note ->
                viewModel.submitRoleRequest(role, note)
                showRoleRequestDialog = false
            }
        )
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            contentWindowInsets = WindowInsets.statusBars,
            topBar = {
                Column {
                    TopAppBar(
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        title = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                BepsCrestBadge(size = 38.dp)
                                Column {
                                    Text(
                                        text = "BEPS Connect",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Ras Al Khaimah • ${effectiveRole.label} Portal",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        },
                        actions = {
                            val dispersalOpen = schoolConfig?.dispersalOpen == true
                            Surface(
                                color = if (dispersalOpen) BepsEmeraldContainer else BepsCrimsonContainer,
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier.padding(end = 6.dp)
                            ) {
                                Text(
                                    text = if (dispersalOpen) "OPEN" else "LOCKED",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (dispersalOpen) BepsEmerald else BepsCrimson,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                            IconButton(
                                onClick = { showAccountDialog = true },
                                modifier = Modifier.testTag("account_menu_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AccountCircle,
                                    contentDescription = "Account and Role Settings",
                                    tint = BepsEmerald
                                )
                            }
                        }
                    )

                    if (canSwitchRoles) {
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            tonalElevation = 1.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(UserRole.entries) { role ->
                                    val selected = effectiveRole == role
                                    FilterChip(
                                        selected = selected,
                                        onClick = { viewModel.selectWorkspaceRole(role) },
                                        label = { Text(role.label) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = BepsEmerald,
                                            selectedLabelColor = Color.White
                                        ),
                                        modifier = Modifier.testTag("role_chip_${role.name.lowercase()}")
                                    )
                                }
                            }
                        }
                    }
                }
            },
            bottomBar = {
                if (!useRail) {
                    NavigationBar(windowInsets = WindowInsets.navigationBars) {
                        tabs.forEachIndexed { idx, item ->
                            NavigationBarItem(
                                selected = selectedTab == idx,
                                onClick = { selectedTab = idx },
                                icon = { Icon(item.icon, contentDescription = item.label) },
                                label = { Text(item.label) },
                                modifier = Modifier.testTag("nav_tab_$idx")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail) {
                    NavigationRail(modifier = Modifier.fillMaxHeight()) {
                        Spacer(modifier = Modifier.size(12.dp))
                        tabs.forEachIndexed { idx, item ->
                            NavigationRailItem(
                                selected = selectedTab == idx,
                                onClick = { selectedTab = idx },
                                icon = { Icon(item.icon, contentDescription = item.label) },
                                label = { Text(item.label) },
                                modifier = Modifier.testTag("nav_rail_$idx")
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Box(modifier = Modifier.widthIn(max = 760.dp).fillMaxSize()) {
                        when {
                            studentsState is UiState.Loading -> {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(color = BepsEmerald)
                                }
                            }
                            studentsState is UiState.Error -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    EmptyStateCard(
                                        title = "Connection Error",
                                        subtitle = (studentsState as UiState.Error).message,
                                        icon = Icons.Default.ReportProblem
                                    )
                                }
                            }
                            else -> {
                                when (effectiveRole) {
                                    UserRole.PARENT -> ParentPortalScreen(
                                        selectedTab = selectedTab,
                                        students = students,
                                        logs = logs,
                                        busRoutes = busRoutes,
                                        schoolConfig = schoolConfig,
                                        isAdminUser = canSwitchRoles,
                                        isSubmitting = isSubmitting,
                                        onRequestPickup = { student ->
                                            viewModel.requestPickup(student, schoolConfig?.dispersalOpen == true)
                                        },
                                        onAddGuardian = { student, entry ->
                                            viewModel.addAuthorizedGuardian(student, entry)
                                        },
                                        onSeedSampleData = { viewModel.initializeSampleCampusData() }
                                    )
                                    UserRole.TEACHER -> TeacherPortalScreen(
                                        selectedTab = selectedTab,
                                        students = students,
                                        alerts = alerts,
                                        isSubmitting = isSubmitting,
                                        onConfirmClassroom = { viewModel.confirmClassroomCheckIn(it) },
                                        onConfirmReadyForPickup = { viewModel.confirmReadyForPickup(it) },
                                        onRaiseAlert = { sId, sName, type, sev, title, desc ->
                                            viewModel.raiseManagementAlert(sId, sName, type, sev, title, desc)
                                        }
                                    )
                                    UserRole.SECURITY -> SecurityPortalScreen(
                                        selectedTab = selectedTab,
                                        students = students,
                                        busRoutes = busRoutes,
                                        isSubmitting = isSubmitting,
                                        onRecordArrival = { viewModel.recordGateArrival(it) },
                                        onConfirmHandover = { s, collector -> viewModel.confirmHandover(s, collector) },
                                        onRejectPickup = { s, reason -> viewModel.rejectPickupRequest(s, reason) },
                                        onConfirmBusBoarding = { s, route -> viewModel.confirmBusBoarding(s, route) },
                                        onUpdateBusStatus = { route, status, checkpoint ->
                                            viewModel.updateBusRouteStatus(route, status, checkpoint)
                                        }
                                    )
                                    UserRole.ADMIN -> AdminPortalScreen(
                                        selectedTab = selectedTab,
                                        schoolConfig = schoolConfig,
                                        students = students,
                                        logs = logs,
                                        busRoutes = busRoutes,
                                        alerts = alerts,
                                        allUsers = allUsers,
                                        roleRequests = roleRequests,
                                        isSubmitting = isSubmitting,
                                        onToggleDispersal = { open, window, notice ->
                                            viewModel.toggleDispersalWindow(open, window, notice)
                                        },
                                        onSeedSampleData = { viewModel.initializeSampleCampusData() },
                                        onResetDailyRoster = { viewModel.resetDailyRoster() },
                                        onEnrollStudent = { code, name, grade, pUid, pName, pEmail, guard, mode, route ->
                                            viewModel.enrollStudent(code, name, grade, pUid, pName, pEmail, guard, mode, route)
                                        },
                                        onUpdateAlertStatus = { id, status -> viewModel.updateAlertStatus(id, status) },
                                        onResolveRoleRequest = { req, approve -> viewModel.resolveRoleRequest(req, approve) },
                                        onUpdateUserRole = { uid, role, grade -> viewModel.updateUserRole(uid, role, grade) },
                                        onShowMessage = { viewModel.showMessage(it) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RoleRequestDialog(
    onDismiss: () -> Unit,
    onSubmit: (UserRole, String) -> Unit
) {
    var selectedRole by remember { mutableStateOf(UserRole.TEACHER) }
    var note by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Request BEPS Staff Role") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Select the staff role for Principal approval:", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(UserRole.TEACHER, UserRole.SECURITY).forEach { role ->
                        FilterChip(
                            selected = selectedRole == role,
                            onClick = { selectedRole = role },
                            label = { Text(role.label) }
                        )
                    }
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Grade / Gate Assignment Note") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSubmit(selectedRole, note) }) {
                Text("Submit Request")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
