const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ADMIN_UID = "admin_edward";
const ADMIN_EMAIL = "edwardnana056@gmail.com";
const PARENT_UID = "parent_tariq";
const OTHER_PARENT_UID = "parent_other";
const TEACHER_UID = "teacher_fatima";
const SECURITY_UID = "security_hassan";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function pastDate(msOffset = 5000) {
  return new Date(Date.now() - msOffset);
}

function adminContext() {
  return testEnv.authenticatedContext(ADMIN_UID, {
    email: ADMIN_EMAIL,
    email_verified: true,
  });
}

async function seedUsersAndConfig({ dispersalOpen = false } = {}) {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    const t = pastDate(20000);
    await db.collection("users").doc(ADMIN_UID).set({
      userId: ADMIN_UID,
      displayName: "Principal Edward",
      email: ADMIN_EMAIL,
      phone: "+971 50 111 2233",
      role: "ADMIN",
      assignedGrade: "Campus Wide",
      createdAt: t,
      updatedAt: t,
    });
    await db.collection("users").doc(PARENT_UID).set({
      userId: PARENT_UID,
      displayName: "Tariq Rahman",
      email: "tariq@example.com",
      role: "PARENT",
      createdAt: t,
      updatedAt: t,
    });
    await db.collection("users").doc(TEACHER_UID).set({
      userId: TEACHER_UID,
      displayName: "Ms. Fatima",
      email: "fatima@beps.ae",
      role: "TEACHER",
      assignedGrade: "Grade 3 - A",
      createdAt: t,
      updatedAt: t,
    });
    await db.collection("users").doc(SECURITY_UID).set({
      userId: SECURITY_UID,
      displayName: "Officer Hassan",
      email: "hassan@beps.ae",
      role: "SECURITY",
      assignedGrade: "Gate 1",
      createdAt: t,
      updatedAt: t,
    });
    await db.collection("school_config").doc("main").set({
      schoolName: "Bangladesh English Private School",
      campusLocation: "Ras Al Khaimah, UAE",
      dispersalOpen,
      dispersalWindowLabel: "01:00 PM - 02:15 PM",
      activeNotice: "Regular school day schedule",
      updatedByUid: ADMIN_UID,
      updatedByName: "Principal Edward",
      createdAt: t,
      updatedAt: t,
    });
    await db.collection("students").doc("stu_aisha").set({
      studentCode: "BEPS-2026-0101",
      fullName: "Aisha Rahman",
      grade: "Grade 3 - A",
      parentUid: PARENT_UID,
      parentName: "Tariq Rahman",
      parentEmail: "tariq@example.com",
      authorizedStaffUids: [TEACHER_UID, SECURITY_UID, ADMIN_UID],
      authorizedGuardians: ["Tariq Rahman (Father)", "Nadia Rahman (Mother)"],
      photoKey: "avatar_girl_1",
      busRouteId: "bus_04",
      busRouteName: "Bus 04 - Al Nakheel",
      transportMode: "PRIVATE_PICKUP",
      status: "IN_CLASSROOM",
      arrivalTimeLabel: "07:25 AM",
      collectionTimeLabel: "",
      lastUpdatedByUid: TEACHER_UID,
      lastUpdatedByName: "Ms. Fatima",
      verifiedCollectorName: "",
      createdAt: t,
      updatedAt: t,
    });
    await db.collection("bus_routes").doc("bus_04").set({
      routeNumber: "BUS-04",
      routeName: "Al Nakheel & Corniche",
      driverName: "Driver Karim",
      attendantName: "Ms. Salma",
      status: "AT_SCHOOL",
      boardedCount: 0,
      capacity: 32,
      lastCheckpoint: "BEPS Main Bus Bay",
      assignedParentUids: [PARENT_UID],
      authorizedStaffUids: [TEACHER_UID, SECURITY_UID, ADMIN_UID],
      createdAt: t,
      updatedAt: t,
    });
  });
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated users are denied across all collections", async () => {
  await seedUsersAndConfig({ dispersalOpen: true });
  const unauth = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauth.collection("school_config").doc("main").get());
  await assertFails(unauth.collection("students").get());
  await assertFails(unauth.collection("users").doc(PARENT_UID).get());
  await assertFails(unauth.collection("management_alerts").get());
});

test("Anti-privilege-escalation: user cannot self-assign staff/admin role or spoof admin email", async () => {
  const userDb = testEnv.authenticatedContext("new_user_1", {
    email: "newuser@example.com",
    email_verified: true,
  }).firestore();
  const t = pastDate();

  // Cannot create profile with TEACHER/ADMIN role
  await assertFails(
    userDb.collection("users").doc("new_user_1").set({
      userId: "new_user_1",
      displayName: "Sneaky User",
      email: "newuser@example.com",
      role: "ADMIN",
      createdAt: t,
      updatedAt: t,
    })
  );

  // Can create profile as PARENT
  await assertSucceeds(
    userDb.collection("users").doc("new_user_1").set({
      userId: "new_user_1",
      displayName: "Parent User",
      email: "newuser@example.com",
      role: "PARENT",
      createdAt: t,
      updatedAt: t,
    })
  );

  // Cannot escalate own role via update
  await assertFails(
    userDb.collection("users").doc("new_user_1").update({
      role: "SECURITY",
      updatedAt: pastDate(1000),
    })
  );

  // Email spoofing without email_verified == true must fail admin operations
  const spoofedAdminDb = testEnv.authenticatedContext("spoof_uid", {
    email: ADMIN_EMAIL,
    email_verified: false,
  }).firestore();
  await assertFails(spoofedAdminDb.collection("students").get());
});

test("Safety Rule #1: Parent cannot request pickup when dispersalOpen is false, succeeds when true", async () => {
  await seedUsersAndConfig({ dispersalOpen: false });
  const parentDb = testEnv.authenticatedContext(PARENT_UID).firestore();

  // Blocked while dispersal window is closed
  await assertFails(
    parentDb.collection("students").doc("stu_aisha").update({
      status: "PICKUP_REQUESTED",
      lastUpdatedByUid: PARENT_UID,
      lastUpdatedByName: "Tariq Rahman",
      updatedAt: pastDate(1000),
    })
  );

  // Admin opens dispersal window
  const adminDb = adminContext().firestore();
  await assertSucceeds(
    adminDb.collection("school_config").doc("main").update({
      dispersalOpen: true,
      updatedByUid: ADMIN_UID,
      updatedByName: "Principal Edward",
      updatedAt: pastDate(1000),
    })
  );

  // Now parent pickup request succeeds
  await assertSucceeds(
    parentDb.collection("students").doc("stu_aisha").update({
      status: "PICKUP_REQUESTED",
      lastUpdatedByUid: PARENT_UID,
      lastUpdatedByName: "Tariq Rahman",
      updatedAt: pastDate(500),
    })
  );

  // Parent creates matching PICKUP_REQUESTED audit log
  await assertSucceeds(
    parentDb.collection("attendance_logs").doc("log_pickup_1").set({
      studentId: "stu_aisha",
      studentCode: "BEPS-2026-0101",
      studentName: "Aisha Rahman",
      grade: "Grade 3 - A",
      parentUid: PARENT_UID,
      authorizedStaffUids: [TEACHER_UID, SECURITY_UID, ADMIN_UID],
      eventType: "PICKUP_REQUESTED",
      title: "Pickup Requested",
      message: "Parent requested pickup at Gate 1.",
      actorUid: PARENT_UID,
      actorName: "Tariq Rahman",
      actorRole: "PARENT",
      collectorName: "",
      timeLabel: "01:10 PM",
      dateKey: "2026-11-21",
      createdAt: pastDate(500),
    })
  );

  // Parent CANNOT directly mark child READY_FOR_PICKUP or COLLECTED (Safety Rule #2 & #3)
  await assertFails(
    parentDb.collection("students").doc("stu_aisha").update({
      status: "COLLECTED",
      collectionTimeLabel: "01:15 PM",
      verifiedCollectorName: "Tariq Rahman",
      lastUpdatedByUid: PARENT_UID,
      lastUpdatedByName: "Tariq Rahman",
      updatedAt: pastDate(300),
    })
  );
});

test("Safety Rules #3, #4, #5: Teacher readiness and Security verified handover workflow", async () => {
  await seedUsersAndConfig({ dispersalOpen: true });
  const parentDb = testEnv.authenticatedContext(PARENT_UID).firestore();
  const teacherDb = testEnv.authenticatedContext(TEACHER_UID).firestore();
  const securityDb = testEnv.authenticatedContext(SECURITY_UID).firestore();

  // Parent requests pickup
  await assertSucceeds(
    parentDb.collection("students").doc("stu_aisha").update({
      status: "PICKUP_REQUESTED",
      lastUpdatedByUid: PARENT_UID,
      lastUpdatedByName: "Tariq Rahman",
      updatedAt: pastDate(2000),
    })
  );

  // Security cannot skip teacher readiness check (PICKUP_REQUESTED -> COLLECTED must fail)
  await assertFails(
    securityDb.collection("students").doc("stu_aisha").update({
      status: "COLLECTED",
      collectionTimeLabel: "01:15 PM",
      verifiedCollectorName: "Tariq Rahman (Father)",
      lastUpdatedByUid: SECURITY_UID,
      lastUpdatedByName: "Officer Hassan",
      updatedAt: pastDate(1500),
    })
  );

  // Teacher marks student READY_FOR_PICKUP
  await assertSucceeds(
    teacherDb.collection("students").doc("stu_aisha").update({
      status: "READY_FOR_PICKUP",
      lastUpdatedByUid: TEACHER_UID,
      lastUpdatedByName: "Ms. Fatima",
      updatedAt: pastDate(1000),
    })
  );

  // Security cannot complete handover without verifiedCollectorName
  await assertFails(
    securityDb.collection("students").doc("stu_aisha").update({
      status: "COLLECTED",
      collectionTimeLabel: "01:15 PM",
      verifiedCollectorName: "",
      lastUpdatedByUid: SECURITY_UID,
      lastUpdatedByName: "Officer Hassan",
      updatedAt: pastDate(800),
    })
  );

  // Security completes verified handover
  await assertSucceeds(
    securityDb.collection("students").doc("stu_aisha").update({
      status: "COLLECTED",
      collectionTimeLabel: "01:15 PM",
      verifiedCollectorName: "Tariq Rahman (Father)",
      lastUpdatedByUid: SECURITY_UID,
      lastUpdatedByName: "Officer Hassan",
      updatedAt: pastDate(500),
    })
  );
});

test("Cross-user isolation & client query alignment on students, bus_routes, and alerts", async () => {
  await seedUsersAndConfig({ dispersalOpen: true });
  const parentDb = testEnv.authenticatedContext(PARENT_UID).firestore();
  const otherParentDb = testEnv.authenticatedContext(OTHER_PARENT_UID).firestore();
  const teacherDb = testEnv.authenticatedContext(TEACHER_UID).firestore();
  const adminDb = adminContext().firestore();

  // Parent filtered query succeeds; unfiltered query fails
  await assertSucceeds(parentDb.collection("students").where("parentUid", "==", PARENT_UID).get());
  await assertFails(parentDb.collection("students").get());

  // Other parent cannot read Aisha's record
  await assertFails(otherParentDb.collection("students").doc("stu_aisha").get());
  await assertFails(otherParentDb.collection("bus_routes").doc("bus_04").get());

  // Parent can query assigned bus route; teacher queries via authorizedStaffUids
  await assertSucceeds(parentDb.collection("bus_routes").where("assignedParentUids", "array-contains", PARENT_UID).get());
  await assertSucceeds(teacherDb.collection("students").where("authorizedStaffUids", "array-contains", TEACHER_UID).get());

  // Admin can query all students and create/resolve management alerts
  await assertSucceeds(adminDb.collection("students").get());
  await assertSucceeds(
    teacherDb.collection("management_alerts").doc("alert_1").set({
      studentId: "stu_aisha",
      studentName: "Aisha Rahman",
      alertType: "UNCOLLECTED_STUDENT",
      severity: "HIGH",
      title: "Late Pickup Alert",
      description: "Student awaiting guardian past 2:15 PM.",
      reportedByUid: TEACHER_UID,
      reportedByName: "Ms. Fatima",
      reportedByRole: "TEACHER",
      status: "OPEN",
      authorizedStaffUids: [TEACHER_UID, SECURITY_UID, ADMIN_UID],
      createdAt: pastDate(1000),
      updatedAt: pastDate(1000),
    })
  );

  // Teacher cannot resolve alert; Admin can resolve, and terminal RESOLVED locks further updates
  await assertFails(
    teacherDb.collection("management_alerts").doc("alert_1").update({
      status: "RESOLVED",
      updatedAt: pastDate(500),
    })
  );
  await assertSucceeds(
    adminDb.collection("management_alerts").doc("alert_1").update({
      status: "RESOLVED",
      updatedAt: pastDate(400),
    })
  );
  await assertFails(
    adminDb.collection("management_alerts").doc("alert_1").update({
      status: "OPEN",
      updatedAt: pastDate(200),
    })
  );
});

test("Shadow update & DocumentId 'id' poisoning are rejected", async () => {
  await seedUsersAndConfig({ dispersalOpen: true });
  const adminDb = adminContext().firestore();
  const t = pastDate();

  // Reject creating student with forbidden 'id' field in body
  await assertFails(
    adminDb.collection("students").doc("stu_bad").set({
      id: "stu_bad",
      studentCode: "BEPS-9999",
      fullName: "Bad Payload",
      grade: "Grade 1",
      parentUid: PARENT_UID,
      parentName: "Tariq",
      parentEmail: "tariq@example.com",
      authorizedStaffUids: [ADMIN_UID],
      authorizedGuardians: [],
      photoKey: "avatar_boy_1",
      busRouteId: "",
      busRouteName: "Private",
      transportMode: "PRIVATE_PICKUP",
      status: "NOT_ARRIVED",
      arrivalTimeLabel: "",
      collectionTimeLabel: "",
      lastUpdatedByUid: ADMIN_UID,
      lastUpdatedByName: "Principal Edward",
      verifiedCollectorName: "",
      createdAt: t,
      updatedAt: t,
    })
  );

  // Reject ghost field in update
  await assertFails(
    adminDb.collection("students").doc("stu_aisha").update({
      ghostField: "hack",
      updatedAt: pastDate(100),
    })
  );
});
