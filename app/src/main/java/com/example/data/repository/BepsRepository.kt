package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.firebase.OperationType
import com.example.data.firebase.handleFirestoreError
import com.example.data.model.AlertSeverity
import com.example.data.model.AlertStatus
import com.example.data.model.AlertType
import com.example.data.model.AttendanceLog
import com.example.data.model.BusRoute
import com.example.data.model.BusStatus
import com.example.data.model.LogEventType
import com.example.data.model.ManagementAlert
import com.example.data.model.RequestStatus
import com.example.data.model.RoleRequest
import com.example.data.model.SchoolConfig
import com.example.data.model.Student
import com.example.data.model.StudentStatus
import com.example.data.model.TransportMode
import com.example.data.model.UserProfile
import com.example.data.model.UserRole
import com.example.util.QrAndPdfUtils
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class BepsRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")

    private fun isCurrentUserBootstrappedAdmin(): Boolean {
        val user = auth.currentUser ?: return false
        return user.isEmailVerified && user.email == BOOTSTRAPPED_ADMIN_EMAIL
    }

    suspend fun ensureUserProfile(displayName: String, email: String): Result<UserProfile> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "users/$uid"
        return try {
            val docRef = db.collection("users").document(uid)
            val snap = docRef.get().await()
            if (snap.exists()) {
                val existing = snap.toObject(UserProfile::class.java, ServerTimestampBehavior.ESTIMATE)
                if (existing != null) return Result.success(existing)
            }
            val safeName = displayName.trim().ifBlank { "BEPS Member" }.take(100)
            val safeEmail = email.trim().ifBlank { "member@beps.ae" }.take(160)
            val assignedRole = if (isCurrentUserBootstrappedAdmin()) UserRole.ADMIN.name else UserRole.PARENT.name
            val payload = mapOf(
                "userId" to uid,
                "displayName" to safeName,
                "email" to safeEmail,
                "role" to assignedRole,
                "assignedGrade" to if (assignedRole == UserRole.ADMIN.name) "Campus Wide" else "",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            if (isCurrentUserBootstrappedAdmin()) {
                ensureDefaultSchoolConfig(uid, safeName)
            }
            Result.success(
                UserProfile(
                    id = uid,
                    userId = uid,
                    displayName = safeName,
                    email = safeEmail,
                    role = assignedRole
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    private suspend fun ensureDefaultSchoolConfig(actorUid: String, actorName: String) {
        val configRef = db.collection("school_config").document("main")
        val snap = configRef.get().await()
        if (!snap.exists()) {
            configRef.set(
                mapOf(
                    "schoolName" to "Bangladesh English Private School",
                    "campusLocation" to "Ras Al Khaimah, UAE",
                    "dispersalOpen" to false,
                    "dispersalWindowLabel" to "01:00 PM - 02:15 PM",
                    "activeNotice" to "Morning gate active. Parent pickup requests unlock during authorized dispersal.",
                    "updatedByUid" to actorUid,
                    "updatedByName" to actorName.take(100),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }
    }

    fun observeUserProfile(uid: String): Flow<UserProfile?> = flow {
        if (uid.isBlank()) {
            emit(null)
            return@flow
        }
        val path = "users/$uid"
        emitAll(
            db.collection("users").document(uid)
                .snapshots()
                .map { it.toObject(UserProfile::class.java, ServerTimestampBehavior.ESTIMATE) }
                .catch { e ->
                    logStreamErrorIfActive(e, uid, OperationType.GET, path)
                    throw e
                }
        )
    }

    fun observeSchoolConfig(): Flow<SchoolConfig?> = flow {
        val uid = requireUserId()
        val path = "school_config/main"
        emitAll(
            db.collection("school_config").document("main")
                .snapshots()
                .map { it.toObject(SchoolConfig::class.java, ServerTimestampBehavior.ESTIMATE) }
                .catch { e ->
                    logStreamErrorIfActive(e, uid, OperationType.GET, path)
                    throw e
                }
        )
    }

    fun observeStudents(uid: String, role: UserRole, isBootstrappedAdmin: Boolean): Flow<List<Student>> = flow {
        if (uid.isBlank()) {
            emit(emptyList())
            return@flow
        }
        val query = when {
            isBootstrappedAdmin -> db.collection("students")
            role == UserRole.PARENT -> db.collection("students").whereEqualTo("parentUid", uid)
            else -> db.collection("students").whereArrayContains("authorizedStaffUids", uid)
        }
        emitAll(
            query.snapshots()
                .map { snap ->
                    snap.toObjects(Student::class.java, ServerTimestampBehavior.ESTIMATE)
                        .sortedBy { it.studentCode }
                }
                .catch { e ->
                    logStreamErrorIfActive(e, uid, OperationType.LIST, "students")
                    throw e
                }
        )
    }

    fun observeAttendanceLogs(uid: String, role: UserRole, isBootstrappedAdmin: Boolean): Flow<List<AttendanceLog>> = flow {
        if (uid.isBlank()) {
            emit(emptyList())
            return@flow
        }
        val query = when {
            isBootstrappedAdmin -> db.collection("attendance_logs")
            role == UserRole.PARENT -> db.collection("attendance_logs").whereEqualTo("parentUid", uid)
            else -> db.collection("attendance_logs").whereArrayContains("authorizedStaffUids", uid)
        }
        emitAll(
            query.snapshots()
                .map { snap ->
                    snap.toObjects(AttendanceLog::class.java, ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { e ->
                    logStreamErrorIfActive(e, uid, OperationType.LIST, "attendance_logs")
                    throw e
                }
        )
    }

    fun observeBusRoutes(uid: String, role: UserRole, isBootstrappedAdmin: Boolean): Flow<List<BusRoute>> = flow {
        if (uid.isBlank()) {
            emit(emptyList())
            return@flow
        }
        val query = when {
            isBootstrappedAdmin -> db.collection("bus_routes")
            role == UserRole.PARENT -> db.collection("bus_routes").whereArrayContains("assignedParentUids", uid)
            else -> db.collection("bus_routes").whereArrayContains("authorizedStaffUids", uid)
        }
        emitAll(
            query.snapshots()
                .map { snap ->
                    snap.toObjects(BusRoute::class.java, ServerTimestampBehavior.ESTIMATE)
                        .sortedBy { it.routeNumber }
                }
                .catch { e ->
                    logStreamErrorIfActive(e, uid, OperationType.LIST, "bus_routes")
                    throw e
                }
        )
    }

    fun observeManagementAlerts(uid: String, role: UserRole, isBootstrappedAdmin: Boolean): Flow<List<ManagementAlert>> {
        if (uid.isBlank() || (role == UserRole.PARENT && !isBootstrappedAdmin)) {
            return flowOf(emptyList())
        }
        val query = if (isBootstrappedAdmin) {
            db.collection("management_alerts")
        } else {
            db.collection("management_alerts").whereArrayContains("authorizedStaffUids", uid)
        }
        return query.snapshots()
            .map { snap ->
                snap.toObjects(ManagementAlert::class.java, ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt?.seconds ?: 0L }
            }
            .catch { e ->
                logStreamErrorIfActive(e, uid, OperationType.LIST, "management_alerts")
                throw e
            }
    }

    fun observeAllUsers(isBootstrappedAdmin: Boolean): Flow<List<UserProfile>> {
        if (!isBootstrappedAdmin) return flowOf(emptyList())
        val uid = auth.currentUser?.uid ?: return flowOf(emptyList())
        return db.collection("users")
            .snapshots()
            .map { snap ->
                snap.toObjects(UserProfile::class.java, ServerTimestampBehavior.ESTIMATE)
                    .sortedBy { it.displayName }
            }
            .catch { e ->
                logStreamErrorIfActive(e, uid, OperationType.LIST, "users")
                throw e
            }
    }

    fun observeRoleRequests(isBootstrappedAdmin: Boolean): Flow<List<RoleRequest>> {
        if (!isBootstrappedAdmin) return flowOf(emptyList())
        val uid = auth.currentUser?.uid ?: return flowOf(emptyList())
        return db.collection("role_requests")
            .snapshots()
            .map { snap ->
                snap.toObjects(RoleRequest::class.java, ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt?.seconds ?: 0L }
            }
            .catch { e ->
                logStreamErrorIfActive(e, uid, OperationType.LIST, "role_requests")
                throw e
            }
    }

    suspend fun initializeSampleCampusData(
        actorUid: String,
        actorName: String,
        actorEmail: String
    ): Result<Unit> {
        val path = "students"
        return try {
            val uid = requireUserId()
            check(uid == actorUid)
            val safeName = actorName.trim().ifBlank { "BEPS Principal" }.take(100)
            val safeEmail = actorEmail.trim().ifBlank { "principal@beps.ae" }.take(160)
            val dateKey = QrAndPdfUtils.currentDateKey()
            val batch = db.batch()

            val configRef = db.collection("school_config").document("main")
            batch.set(
                configRef,
                mapOf(
                    "schoolName" to "Bangladesh English Private School",
                    "campusLocation" to "Ras Al Khaimah, UAE",
                    "dispersalOpen" to true,
                    "dispersalWindowLabel" to "01:00 PM - 02:15 PM",
                    "activeNotice" to "Afternoon dispersal window is open. Gate 1 & Bus Bay B are staffed.",
                    "updatedByUid" to uid,
                    "updatedByName" to safeName,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )

            val bus4Ref = db.collection("bus_routes").document("bus_04")
            batch.set(
                bus4Ref,
                mapOf(
                    "routeNumber" to "BUS-04",
                    "routeName" to "Al Nakheel & Corniche",
                    "driverName" to "Karim Uddin",
                    "attendantName" to "Ms. Salma Begum",
                    "status" to BusStatus.AT_SCHOOL.name,
                    "boardedCount" to 0L,
                    "capacity" to 32L,
                    "lastCheckpoint" to "BEPS Main Bus Bay",
                    "assignedParentUids" to listOf(uid),
                    "authorizedStaffUids" to listOf(uid),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )

            val bus7Ref = db.collection("bus_routes").document("bus_07")
            batch.set(
                bus7Ref,
                mapOf(
                    "routeNumber" to "BUS-07",
                    "routeName" to "Al Hamra & Jazirat",
                    "driverName" to "Rashid Ali",
                    "attendantName" to "Ms. Farhana",
                    "status" to BusStatus.AT_SCHOOL.name,
                    "boardedCount" to 0L,
                    "capacity" to 28L,
                    "lastCheckpoint" to "BEPS South Gate Bay",
                    "assignedParentUids" to listOf(uid),
                    "authorizedStaffUids" to listOf(uid),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )

            data class SeedStudent(
                val docId: String,
                val code: String,
                val name: String,
                val grade: String,
                val photoKey: String,
                val busId: String,
                val busName: String,
                val mode: TransportMode,
                val status: StudentStatus,
                val arrivalTime: String
            )

            val seeds = listOf(
                SeedStudent(
                    "stu_aisha", "BEPS-2026-0101", "Aisha Rahman", "Grade 3 - A",
                    "avatar_girl_1", "", "Private Guardian Pickup",
                    TransportMode.PRIVATE_PICKUP, StudentStatus.IN_CLASSROOM, "07:25 AM"
                ),
                SeedStudent(
                    "stu_rayhan", "BEPS-2026-0102", "Rayhan Chowdhury", "Grade 5 - B",
                    "avatar_boy_1", "bus_04", "Bus 04 - Al Nakheel",
                    TransportMode.SCHOOL_BUS, StudentStatus.IN_CLASSROOM, "07:28 AM"
                ),
                SeedStudent(
                    "stu_zara", "BEPS-2026-0103", "Zara Islam", "Grade 2 - A",
                    "avatar_girl_2", "", "Private Guardian Pickup",
                    TransportMode.PRIVATE_PICKUP, StudentStatus.ARRIVED_AT_GATE, "07:34 AM"
                ),
                SeedStudent(
                    "stu_omar", "BEPS-2026-0104", "Omar Hossain", "Grade 6 - A",
                    "avatar_boy_2", "bus_07", "Bus 07 - Al Hamra",
                    TransportMode.SCHOOL_BUS, StudentStatus.NOT_ARRIVED, ""
                )
            )

            seeds.forEach { s ->
                val sRef = db.collection("students").document(s.docId)
                batch.set(
                    sRef,
                    mapOf(
                        "studentCode" to s.code,
                        "fullName" to s.name,
                        "grade" to s.grade,
                        "parentUid" to uid,
                        "parentName" to safeName,
                        "parentEmail" to safeEmail,
                        "authorizedStaffUids" to listOf(uid),
                        "authorizedGuardians" to listOf(
                            "$safeName (Primary Parent)",
                            "Nadia Rahman (Authorized Mother • RAK ID 784-1990-22)"
                        ),
                        "photoKey" to s.photoKey,
                        "busRouteId" to s.busId,
                        "busRouteName" to s.busName,
                        "transportMode" to s.mode.name,
                        "status" to s.status.name,
                        "arrivalTimeLabel" to s.arrivalTime,
                        "collectionTimeLabel" to "",
                        "lastUpdatedByUid" to uid,
                        "lastUpdatedByName" to safeName,
                        "verifiedCollectorName" to "",
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )

                if (s.arrivalTime.isNotBlank()) {
                    val logRef = db.collection("attendance_logs").document("seed_arr_${s.docId}")
                    batch.set(
                        logRef,
                        mapOf(
                            "studentId" to s.docId,
                            "studentCode" to s.code,
                            "studentName" to s.name,
                            "grade" to s.grade,
                            "parentUid" to uid,
                            "authorizedStaffUids" to listOf(uid),
                            "eventType" to LogEventType.ARRIVAL.name,
                            "title" to "Your child has arrived!",
                            "message" to "Dear Parent, ${s.name} safely arrived at Bangladesh English Private School at ${s.arrivalTime}.",
                            "actorUid" to uid,
                            "actorName" to safeName,
                            "actorRole" to UserRole.SECURITY.name,
                            "collectorName" to "",
                            "timeLabel" to s.arrivalTime,
                            "dateKey" to dateKey,
                            "createdAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
            }

            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun toggleDispersalWindow(
        open: Boolean,
        windowLabel: String,
        notice: String,
        actorUid: String,
        actorName: String
    ): Result<Unit> {
        val path = "school_config/main"
        return try {
            val uid = requireUserId()
            check(uid == actorUid)
            val cleanWindow = windowLabel.trim().ifBlank { "01:00 PM - 02:15 PM" }.take(80)
            val cleanNotice = notice.trim().take(300)
            val docRef = db.collection("school_config").document("main")
            val exists = docRef.get().await().exists()
            if (exists) {
                docRef.update(
                    mapOf(
                        "dispersalOpen" to open,
                        "dispersalWindowLabel" to cleanWindow,
                        "activeNotice" to cleanNotice,
                        "updatedByUid" to uid,
                        "updatedByName" to actorName.trim().ifBlank { "Principal" }.take(100),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.set(
                    mapOf(
                        "schoolName" to "Bangladesh English Private School",
                        "campusLocation" to "Ras Al Khaimah, UAE",
                        "dispersalOpen" to open,
                        "dispersalWindowLabel" to cleanWindow,
                        "activeNotice" to cleanNotice,
                        "updatedByUid" to uid,
                        "updatedByName" to actorName.trim().ifBlank { "Principal" }.take(100),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun requestStudentPickup(
        student: Student,
        actorUid: String,
        actorName: String,
        actorRole: UserRole
    ): Result<Unit> {
        val path = "students/${student.id}"
        return try {
            val uid = requireUserId()
            require(student.id.isNotBlank()) { "Student ID cannot be blank" }
            val timeNow = QrAndPdfUtils.currentTimeLabel()
            val batch = db.batch()
            val studentRef = db.collection("students").document(student.id)
            batch.update(
                studentRef,
                mapOf(
                    "status" to StudentStatus.PICKUP_REQUESTED.name,
                    "lastUpdatedByUid" to uid,
                    "lastUpdatedByName" to actorName.trim().ifBlank { "BEPS Parent" }.take(100),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            val logRef = db.collection("attendance_logs").document()
            batch.set(
                logRef,
                buildLogMap(
                    student = student,
                    eventType = LogEventType.PICKUP_REQUESTED,
                    title = "Your pickup request has been received!",
                    message = "Pickup requested for ${student.fullName}. Please proceed to the designated pickup zone and await teacher & security verification.",
                    actorUid = uid,
                    actorName = actorName,
                    actorRole = actorRole,
                    collectorName = "",
                    timeLabel = timeNow
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun addAuthorizedGuardian(
        student: Student,
        guardianEntry: String,
        actorName: String
    ): Result<Unit> {
        val path = "students/${student.id}"
        return try {
            val uid = requireUserId()
            val clean = guardianEntry.trim().take(135)
            require(clean.length >= 3) { "Guardian details must be at least 3 characters." }
            require(student.authorizedGuardians.size < 10) { "Maximum of 10 authorized guardians reached." }
            val updatedList = (student.authorizedGuardians + clean).distinct().take(10)
            db.collection("students").document(student.id).update(
                mapOf(
                    "authorizedGuardians" to updatedList,
                    "lastUpdatedByUid" to uid,
                    "lastUpdatedByName" to actorName.trim().ifBlank { "Parent" }.take(100),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun recordGateArrival(
        student: Student,
        actorName: String,
        actorRole: UserRole
    ): Result<Unit> {
        val path = "students/${student.id}"
        return try {
            val uid = requireUserId()
            val timeNow = QrAndPdfUtils.currentTimeLabel()
            val batch = db.batch()
            batch.update(
                db.collection("students").document(student.id),
                mapOf(
                    "status" to StudentStatus.ARRIVED_AT_GATE.name,
                    "arrivalTimeLabel" to timeNow,
                    "lastUpdatedByUid" to uid,
                    "lastUpdatedByName" to actorName.trim().ifBlank { "Security Officer" }.take(100),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.set(
                db.collection("attendance_logs").document(),
                buildLogMap(
                    student = student,
                    eventType = LogEventType.ARRIVAL,
                    title = "Your child has arrived!",
                    message = "Dear Parent, ${student.fullName} safely arrived at Bangladesh English Private School at $timeNow. Status: Checked in at gate.",
                    actorUid = uid,
                    actorName = actorName,
                    actorRole = actorRole,
                    collectorName = "",
                    timeLabel = timeNow
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun confirmClassroomCheckIn(
        student: Student,
        actorName: String,
        actorRole: UserRole
    ): Result<Unit> {
        val path = "students/${student.id}"
        return try {
            val uid = requireUserId()
            val timeNow = QrAndPdfUtils.currentTimeLabel()
            val batch = db.batch()
            val updates = mutableMapOf<String, Any>(
                "status" to StudentStatus.IN_CLASSROOM.name,
                "lastUpdatedByUid" to uid,
                "lastUpdatedByName" to actorName.trim().ifBlank { "Class Teacher" }.take(100),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (student.arrivalTimeLabel.isBlank()) {
                updates["arrivalTimeLabel"] = timeNow
            }
            batch.update(db.collection("students").document(student.id), updates)
            batch.set(
                db.collection("attendance_logs").document(),
                buildLogMap(
                    student = student,
                    eventType = LogEventType.CLASSROOM_CHECKIN,
                    title = "Classroom Attendance Confirmed",
                    message = "${student.fullName} has safely entered ${student.grade} classroom.",
                    actorUid = uid,
                    actorName = actorName,
                    actorRole = actorRole,
                    collectorName = "",
                    timeLabel = timeNow
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun confirmReadyForPickup(
        student: Student,
        actorName: String,
        actorRole: UserRole
    ): Result<Unit> {
        val path = "students/${student.id}"
        return try {
            val uid = requireUserId()
            val timeNow = QrAndPdfUtils.currentTimeLabel()
            val batch = db.batch()
            batch.update(
                db.collection("students").document(student.id),
                mapOf(
                    "status" to StudentStatus.READY_FOR_PICKUP.name,
                    "lastUpdatedByUid" to uid,
                    "lastUpdatedByName" to actorName.trim().ifBlank { "Class Teacher" }.take(100),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.set(
                db.collection("attendance_logs").document(),
                buildLogMap(
                    student = student,
                    eventType = LogEventType.READY_FOR_PICKUP,
                    title = "Student Escorted to Pickup Zone",
                    message = "${student.fullName} is ready for verified guardian collection at the gate.",
                    actorUid = uid,
                    actorName = actorName,
                    actorRole = actorRole,
                    collectorName = "",
                    timeLabel = timeNow
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun confirmHandover(
        student: Student,
        collectorName: String,
        actorName: String,
        actorRole: UserRole
    ): Result<Unit> {
        val path = "students/${student.id}"
        return try {
            val uid = requireUserId()
            val cleanCollector = collectorName.trim().take(140)
            require(cleanCollector.length >= 2) { "Authorized collector name must be verified before handover." }
            val timeNow = QrAndPdfUtils.currentTimeLabel()
            val batch = db.batch()
            batch.update(
                db.collection("students").document(student.id),
                mapOf(
                    "status" to StudentStatus.COLLECTED.name,
                    "collectionTimeLabel" to timeNow,
                    "verifiedCollectorName" to cleanCollector,
                    "lastUpdatedByUid" to uid,
                    "lastUpdatedByName" to actorName.trim().ifBlank { "Security Officer" }.take(100),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.set(
                db.collection("attendance_logs").document(),
                buildLogMap(
                    student = student,
                    eventType = LogEventType.HANDOVER_COMPLETED,
                    title = "Your child has been collected!",
                    message = "${student.fullName} was safely handed over to $cleanCollector at $timeNow (Verified by $actorName).",
                    actorUid = uid,
                    actorName = actorName,
                    actorRole = actorRole,
                    collectorName = cleanCollector,
                    timeLabel = timeNow
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun rejectPickupRequest(
        student: Student,
        reason: String,
        actorName: String,
        actorRole: UserRole
    ): Result<Unit> {
        val path = "students/${student.id}"
        return try {
            val uid = requireUserId()
            val cleanReason = reason.trim().ifBlank { "Collector ID verification failed." }.take(350)
            val timeNow = QrAndPdfUtils.currentTimeLabel()
            val batch = db.batch()
            batch.update(
                db.collection("students").document(student.id),
                mapOf(
                    "status" to StudentStatus.IN_CLASSROOM.name,
                    "lastUpdatedByUid" to uid,
                    "lastUpdatedByName" to actorName.trim().ifBlank { "Security Officer" }.take(100),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.set(
                db.collection("attendance_logs").document(),
                buildLogMap(
                    student = student,
                    eventType = LogEventType.PICKUP_REJECTED,
                    title = "Pickup Verification Held",
                    message = "Student remains under school supervision. Reason: $cleanReason",
                    actorUid = uid,
                    actorName = actorName,
                    actorRole = actorRole,
                    collectorName = "",
                    timeLabel = timeNow
                )
            )
            batch.set(
                db.collection("management_alerts").document(),
                mapOf(
                    "studentId" to student.id,
                    "studentName" to student.fullName,
                    "alertType" to AlertType.REJECTED_PICKUP.name,
                    "severity" to AlertSeverity.HIGH.name,
                    "title" to "Rejected Pickup: ${student.fullName}",
                    "description" to cleanReason,
                    "reportedByUid" to uid,
                    "reportedByName" to actorName.trim().ifBlank { "Staff" }.take(100),
                    "reportedByRole" to actorRole.name,
                    "status" to AlertStatus.OPEN.name,
                    "authorizedStaffUids" to student.authorizedStaffUids.ifEmpty { listOf(uid) }.take(30),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun confirmBusBoarding(
        student: Student,
        busRoute: BusRoute?,
        actorName: String,
        actorRole: UserRole
    ): Result<Unit> {
        val path = "students/${student.id}"
        return try {
            val uid = requireUserId()
            val timeNow = QrAndPdfUtils.currentTimeLabel()
            val batch = db.batch()
            batch.update(
                db.collection("students").document(student.id),
                mapOf(
                    "status" to StudentStatus.BOARDED_BUS.name,
                    "collectionTimeLabel" to timeNow,
                    "lastUpdatedByUid" to uid,
                    "lastUpdatedByName" to actorName.trim().ifBlank { "Bus Monitor" }.take(100),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            if (busRoute != null && busRoute.id.isNotBlank()) {
                batch.update(
                    db.collection("bus_routes").document(busRoute.id),
                    mapOf(
                        "boardedCount" to FieldValue.increment(1L),
                        "status" to BusStatus.BOARDING.name,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            batch.set(
                db.collection("attendance_logs").document(),
                buildLogMap(
                    student = student,
                    eventType = LogEventType.BUS_BOARDED,
                    title = "Boarded ${student.busRouteName.ifBlank { "School Bus" }}",
                    message = "${student.fullName} boarded the school bus at $timeNow (Verified by $actorName).",
                    actorUid = uid,
                    actorName = actorName,
                    actorRole = actorRole,
                    collectorName = student.busRouteName,
                    timeLabel = timeNow
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateBusRouteStatus(
        route: BusRoute,
        newStatus: BusStatus,
        checkpoint: String
    ): Result<Unit> {
        val path = "bus_routes/${route.id}"
        return try {
            requireUserId()
            db.collection("bus_routes").document(route.id).update(
                mapOf(
                    "status" to newStatus.name,
                    "lastCheckpoint" to checkpoint.trim().take(120),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun raiseManagementAlert(
        studentId: String,
        studentName: String,
        alertType: AlertType,
        severity: AlertSeverity,
        title: String,
        description: String,
        actorName: String,
        actorRole: UserRole
    ): Result<Unit> {
        val path = "management_alerts"
        return try {
            val uid = requireUserId()
            val cleanTitle = title.trim().take(120)
            val cleanDesc = description.trim().take(500)
            require(cleanTitle.length >= 3 && cleanDesc.length >= 3) {
                "Alert title and description must be at least 3 characters."
            }
            db.collection("management_alerts").document().set(
                mapOf(
                    "studentId" to studentId.take(128),
                    "studentName" to studentName.trim().take(100),
                    "alertType" to alertType.name,
                    "severity" to severity.name,
                    "title" to cleanTitle,
                    "description" to cleanDesc,
                    "reportedByUid" to uid,
                    "reportedByName" to actorName.trim().ifBlank { "Staff" }.take(100),
                    "reportedByRole" to actorRole.name,
                    "status" to AlertStatus.OPEN.name,
                    "authorizedStaffUids" to listOf(uid),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateAlertStatus(alertId: String, newStatus: AlertStatus): Result<Unit> {
        val path = "management_alerts/$alertId"
        return try {
            requireUserId()
            db.collection("management_alerts").document(alertId).update(
                mapOf(
                    "status" to newStatus.name,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun enrollStudent(
        studentCode: String,
        fullName: String,
        grade: String,
        parentUid: String,
        parentName: String,
        parentEmail: String,
        guardianName: String,
        transportMode: TransportMode,
        busRoute: BusRoute?,
        actorName: String
    ): Result<Unit> {
        val path = "students"
        return try {
            val uid = requireUserId()
            val cleanCode = studentCode.trim().uppercase().take(40)
            val cleanName = fullName.trim().take(100)
            val cleanGrade = grade.trim().take(40)
            require(cleanCode.length >= 3 && cleanName.length >= 2 && cleanGrade.length >= 2) {
                "Please enter valid student code, full name, and grade."
            }
            val effectiveParentUid = parentUid.trim().ifBlank { uid }
            val effectiveParentName = parentName.trim().ifBlank { actorName }.take(100)
            val effectiveParentEmail = parentEmail.trim().ifBlank { "parent@beps.ae" }.take(160)
            val guardians = listOfNotNull(
                "$effectiveParentName (Parent)",
                guardianName.trim().takeIf { it.isNotBlank() }?.take(140)
            )
            db.collection("students").document().set(
                mapOf(
                    "studentCode" to cleanCode,
                    "fullName" to cleanName,
                    "grade" to cleanGrade,
                    "parentUid" to effectiveParentUid,
                    "parentName" to effectiveParentName,
                    "parentEmail" to effectiveParentEmail,
                    "authorizedStaffUids" to listOf(uid),
                    "authorizedGuardians" to guardians,
                    "photoKey" to "avatar_girl_1",
                    "busRouteId" to (busRoute?.id ?: ""),
                    "busRouteName" to (busRoute?.let { "${it.routeNumber} - ${it.routeName}" } ?: "Private Guardian Pickup"),
                    "transportMode" to transportMode.name,
                    "status" to StudentStatus.NOT_ARRIVED.name,
                    "arrivalTimeLabel" to "",
                    "collectionTimeLabel" to "",
                    "lastUpdatedByUid" to uid,
                    "lastUpdatedByName" to actorName.trim().ifBlank { "Admin" }.take(100),
                    "verifiedCollectorName" to "",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun resetDailyRoster(
        students: List<Student>,
        busRoutes: List<BusRoute>,
        actorName: String
    ): Result<Unit> {
        val path = "students"
        return try {
            val uid = requireUserId()
            val batch = db.batch()
            students.forEach { student ->
                if (student.id.isNotBlank()) {
                    batch.update(
                        db.collection("students").document(student.id),
                        mapOf(
                            "status" to StudentStatus.NOT_ARRIVED.name,
                            "arrivalTimeLabel" to "",
                            "collectionTimeLabel" to "",
                            "verifiedCollectorName" to "",
                            "lastUpdatedByUid" to uid,
                            "lastUpdatedByName" to actorName.trim().ifBlank { "Admin" }.take(100),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
            }
            busRoutes.forEach { route ->
                if (route.id.isNotBlank()) {
                    batch.update(
                        db.collection("bus_routes").document(route.id),
                        mapOf(
                            "status" to BusStatus.AT_SCHOOL.name,
                            "boardedCount" to 0L,
                            "lastCheckpoint" to "BEPS Main Bus Bay",
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun submitRoleRequest(
        displayName: String,
        email: String,
        requestedRole: UserRole,
        note: String
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "role_requests/$uid"
        return try {
            require(requestedRole == UserRole.TEACHER || requestedRole == UserRole.SECURITY) {
                "Only Teacher or Security staff roles can be requested."
            }
            db.collection("role_requests").document(uid).set(
                mapOf(
                    "userId" to uid,
                    "displayName" to displayName.trim().ifBlank { "BEPS Staff" }.take(100),
                    "email" to email.trim().ifBlank { "staff@beps.ae" }.take(160),
                    "requestedRole" to requestedRole.name,
                    "note" to note.trim().take(250),
                    "status" to RequestStatus.PENDING.name,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun resolveRoleRequest(
        request: RoleRequest,
        approve: Boolean,
        students: List<Student>,
        busRoutes: List<BusRoute>
    ): Result<Unit> {
        val path = "role_requests/${request.userId}"
        return try {
            requireUserId()
            val batch = db.batch()
            val reqRef = db.collection("role_requests").document(request.userId)
            batch.update(
                reqRef,
                mapOf(
                    "status" to if (approve) RequestStatus.APPROVED.name else RequestStatus.DECLINED.name,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            if (approve) {
                val userRef = db.collection("users").document(request.userId)
                batch.update(
                    userRef,
                    mapOf(
                        "role" to request.requestedRole,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                students.forEach { s ->
                    if (s.id.isNotBlank()) {
                        batch.update(
                            db.collection("students").document(s.id),
                            mapOf("authorizedStaffUids" to FieldValue.arrayUnion(request.userId))
                        )
                    }
                }
                busRoutes.forEach { b ->
                    if (b.id.isNotBlank()) {
                        batch.update(
                            db.collection("bus_routes").document(b.id),
                            mapOf("authorizedStaffUids" to FieldValue.arrayUnion(request.userId))
                        )
                    }
                }
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateUserRole(
        targetUid: String,
        newRole: UserRole,
        assignedGrade: String,
        students: List<Student>,
        busRoutes: List<BusRoute>
    ): Result<Unit> {
        val path = "users/$targetUid"
        return try {
            requireUserId()
            val batch = db.batch()
            batch.update(
                db.collection("users").document(targetUid),
                mapOf(
                    "role" to newRole.name,
                    "assignedGrade" to assignedGrade.trim().take(60),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            if (newRole != UserRole.PARENT) {
                students.forEach { s ->
                    if (s.id.isNotBlank()) {
                        batch.update(
                            db.collection("students").document(s.id),
                            mapOf("authorizedStaffUids" to FieldValue.arrayUnion(targetUid))
                        )
                    }
                }
                busRoutes.forEach { b ->
                    if (b.id.isNotBlank()) {
                        batch.update(
                            db.collection("bus_routes").document(b.id),
                            mapOf("authorizedStaffUids" to FieldValue.arrayUnion(targetUid))
                        )
                    }
                }
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    private fun buildLogMap(
        student: Student,
        eventType: LogEventType,
        title: String,
        message: String,
        actorUid: String,
        actorName: String,
        actorRole: UserRole,
        collectorName: String,
        timeLabel: String
    ): Map<String, Any> = mapOf(
        "studentId" to student.id,
        "studentCode" to student.studentCode,
        "studentName" to student.fullName,
        "grade" to student.grade,
        "parentUid" to student.parentUid,
        "authorizedStaffUids" to student.authorizedStaffUids.ifEmpty { listOf(actorUid) }.take(30),
        "eventType" to eventType.name,
        "title" to title.take(120),
        "message" to message.take(400),
        "actorUid" to actorUid,
        "actorName" to actorName.trim().ifBlank { "BEPS Staff" }.take(100),
        "actorRole" to actorRole.name,
        "collectorName" to collectorName.trim().take(140),
        "timeLabel" to timeLabel.take(40),
        "dateKey" to QrAndPdfUtils.currentDateKey(),
        "createdAt" to FieldValue.serverTimestamp()
    )

    private fun logStreamErrorIfActive(
        error: Throwable,
        expectedUid: String,
        operationType: OperationType,
        path: String
    ) {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) return
        val ex = firestoreError ?: (error as? Exception) ?: return
        if (auth.currentUser?.uid == expectedUid) {
            handleFirestoreError(ex, operationType, path)
        }
    }

    companion object {
        const val BOOTSTRAPPED_ADMIN_EMAIL = "edwardnana056@gmail.com"
    }
}
