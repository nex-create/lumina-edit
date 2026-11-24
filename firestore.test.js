const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_studio_123";
const BOB_UID = "bob_studio_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validProfilePayload(uid, now = new Date(Date.now() - 1000)) {
  return {
    userId: uid,
    displayName: "Alice Studio",
    email: "alice@example.com",
    autoSyncEnabled: true,
    totalBackups: 0,
    createdAt: now,
    updatedAt: now,
  };
}

function validProjectPayload(uid, now = new Date(Date.now() - 1000)) {
  return {
    userId: uid,
    title: "Golden Hour Cafe",
    sourceType: "sample",
    sampleKey: "sample_portrait",
    filterId: "creamy",
    filterIntensity: 0.85,
    exposure: 0.12,
    contrast: 0.08,
    saturation: 0.15,
    warmth: 0.25,
    tint: 0.0,
    fade: 0.1,
    vignette: 0.2,
    aspectRatio: "4:5",
    cropLeft: 0.05,
    cropTop: 0.05,
    cropRight: 0.95,
    cropBottom: 0.95,
    rotationDegrees: 0,
    straightenDegrees: 1.5,
    flipHorizontal: false,
    flipVertical: false,
    exportedCount: 0,
    deviceName: "Pixel 9 Pro",
    createdAt: now,
    updatedAt: now,
  };
}

function validPresetPayload(uid, now = new Date(Date.now() - 1000)) {
  return {
    userId: uid,
    name: "Sunset Peach",
    filterId: "peach_glow",
    filterIntensity: 0.75,
    exposure: 0.1,
    contrast: 0.05,
    saturation: 0.2,
    warmth: 0.3,
    tint: 0.1,
    fade: 0.15,
    vignette: 0.1,
    createdAt: now,
    updatedAt: now,
  };
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

test("Unauthenticated user cannot read or write profiles, projects, or presets", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("projects").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("presets").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("projects").doc("proj_1").set(validProjectPayload(ALICE_UID))
  );
});

test("Owner can create, update, list, and delete their own UserProfile, while cross-user and ghost-field writes fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set(validProfilePayload(ALICE_UID))
  );
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).get());
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      autoSyncEnabled: false,
      totalBackups: 3,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Cross-user isolation
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).update({ displayName: "Hacked" }));

  // Ghost field & immutable createdAt rejection
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      isAdmin: true,
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      createdAt: new Date(Date.now() - 200),
      updatedAt: new Date(Date.now() - 200),
    })
  );
});

test("Owner can create, list, update edits, record exports, and delete CloudPhotoProjects", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const projRef = aliceDb.collection("users").doc(ALICE_UID).collection("projects").doc("proj_1");

  // Create
  await assertSucceeds(projRef.set(validProjectPayload(ALICE_UID)));

  // List subcollection (mirrors Kotlin repository observeProjects)
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("projects").get()
  );

  // Sync edit recipe update (partial update)
  await assertSucceeds(
    projRef.update({
      filterId: "kodak_gold",
      exposure: 0.25,
      aspectRatio: "9:16",
      cropLeft: 0.1,
      cropRight: 0.85,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Attach Firebase Storage asset metadata
  await assertSucceeds(
    projRef.update({
      storagePath: `users/${ALICE_UID}/edited_photos/proj_1.jpg`,
      storageDownloadUrl: "https://firebasestorage.googleapis.com/v0/b/example.appspot.com/o/users%2Fphoto.jpg?alt=media",
      syncedPhotoSizeBytes: 512000,
      deviceName: "Pixel 9 Pro",
      updatedAt: new Date(Date.now() - 300),
    })
  );

  // Record export telemetry action
  await assertSucceeds(
    projRef.update({
      lastExportPlatform: "Instagram",
      exportedCount: 1,
      updatedAt: new Date(Date.now() - 200),
    })
  );

  // Reject invalid bounds: negative syncedPhotoSizeBytes, cropLeft >= cropRight, future timestamp, @DocumentId 'id' key, wrong sourceType
  await assertFails(
    projRef.update({
      syncedPhotoSizeBytes: -10,
      updatedAt: new Date(Date.now() - 100),
    })
  );
  await assertFails(
    projRef.update({
      cropLeft: 0.9,
      cropRight: 0.2,
      updatedAt: new Date(Date.now() - 100),
    })
  );
  await assertFails(
    projRef.update({
      updatedAt: new Date(Date.now() + 60000),
    })
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("projects").doc("proj_2").set({
      ...validProjectPayload(ALICE_UID),
      id: "proj_2",
    })
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("projects").doc("proj_3").set({
      ...validProjectPayload(ALICE_UID),
      sourceType: "unknown_source",
    })
  );

  // Cross-user access denied
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("projects").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("projects").doc("proj_1").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("projects").doc("proj_1").delete());

  // Owner delete succeeds
  await assertSucceeds(projRef.delete());
});

test("Owner can create, list, update, and delete CloudPresets with strict isolation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const presetRef = aliceDb.collection("users").doc(ALICE_UID).collection("presets").doc("preset_1");

  await assertSucceeds(presetRef.set(validPresetPayload(ALICE_UID)));
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("presets").get());
  await assertSucceeds(
    presetRef.update({
      name: "Warm Film",
      warmth: 0.45,
      updatedAt: new Date(Date.now() - 300),
    })
  );

  // Reject cross-user & invalid rotation/saturation range
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("presets").doc("preset_1").get());
  await assertFails(
    presetRef.update({
      saturation: 2.5,
      updatedAt: new Date(Date.now() - 100),
    })
  );

  await assertSucceeds(presetRef.delete());
});
