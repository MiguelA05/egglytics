import { after, before, beforeEach, describe, it } from "node:test";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
  type RulesTestEnvironment,
} from "@firebase/rules-unit-testing";
import {
  Timestamp,
  deleteDoc,
  doc,
  getDoc,
  serverTimestamp,
  setDoc,
  updateDoc,
} from "firebase/firestore";

const PROJECT_ID = "demo-egglytics-test";
const BUSINESS_ID = "egglytics_default";
const ADMIN_UID = "admin_1";
const OPERATOR_UID = "operator_1";
const INACTIVE_UID = "inactive_1";
const VIEWER_UID = "viewer_1";
const STRANGER_UID = "stranger_1";

let testEnv: RulesTestEnvironment;

function baseHarvest(overrides: Record<string, unknown> = {}): Record<string, unknown> {
  return {
    fechaOperativa: "2026-11-18",
    createdAt: Timestamp.now(),
    syncedAt: serverTimestamp(),
    createdBy: OPERATOR_UID,
    graded: { AA: 100, A: 20 },
    discarded: 5,
    ...overrides,
  };
}

async function seedTestData(): Promise<void> {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    await setDoc(doc(db, `businesses/${BUSINESS_ID}`), {
      name: "Granja Familiar",
      currency: "COP",
      timezone: "America/Bogota",
      startDate: "2026-11-01",
      active: true,
    });
    await setDoc(doc(db, `businesses/${BUSINESS_ID}/members/${ADMIN_UID}`), {
      name: "Desarrollador",
      email: "admin@example.com",
      role: "admin",
      active: true,
      createdAt: Timestamp.now(),
    });
    await setDoc(doc(db, `businesses/${BUSINESS_ID}/members/${OPERATOR_UID}`), {
      name: "Madre",
      email: "operador@example.com",
      role: "operator",
      active: true,
      createdAt: Timestamp.now(),
    });
    await setDoc(doc(db, `businesses/${BUSINESS_ID}/members/${INACTIVE_UID}`), {
      name: "Exmiembro",
      email: "inactivo@example.com",
      role: "operator",
      active: false,
      createdAt: Timestamp.now(),
    });
    await setDoc(doc(db, `businesses/${BUSINESS_ID}/members/${VIEWER_UID}`), {
      name: "Solo Lectura",
      email: "viewer@example.com",
      role: "viewer",
      active: true,
      createdAt: Timestamp.now(),
    });
    await setDoc(doc(db, `businesses/${BUSINESS_ID}/harvests/harvest_seed_1`), {
      fechaOperativa: "2026-11-18",
      createdAt: Timestamp.now(),
      syncedAt: Timestamp.now(),
      createdBy: OPERATOR_UID,
      graded: { AA: 60 },
      discarded: 2,
    });
  });
}

describe("Harvests rules (§8.3, §11.1)", () => {
  before(async () => {
    const rulesPath = resolve(process.cwd(), "..", "firestore.rules");
    const rules = readFileSync(rulesPath, "utf8");
    testEnv = await initializeTestEnvironment({
      projectId: PROJECT_ID,
      firestore: {
        host: "127.0.0.1",
        port: 8080,
        rules,
      },
    });
  });

  beforeEach(async () => {
    await testEnv.clearFirestore();
    await seedTestData();
  });

  after(async () => {
    await testEnv.cleanup();
  });

  it("H1 (AC-32, AC-33): active operator/admin can create and read harvest; viewer, inactive, stranger and unauth cannot write", async () => {
    const operatorDb = testEnv.authenticatedContext(OPERATOR_UID).firestore();
    const adminDb = testEnv.authenticatedContext(ADMIN_UID).firestore();
    const viewerDb = testEnv.authenticatedContext(VIEWER_UID).firestore();
    const inactiveDb = testEnv.authenticatedContext(INACTIVE_UID).firestore();
    const strangerDb = testEnv.authenticatedContext(STRANGER_UID).firestore();
    const unauthDb = testEnv.unauthenticatedContext().firestore();

    await assertSucceeds(getDoc(doc(operatorDb, `businesses/${BUSINESS_ID}/harvests/harvest_seed_1`)));
    await assertSucceeds(getDoc(doc(viewerDb, `businesses/${BUSINESS_ID}/harvests/harvest_seed_1`)));
    await assertFails(getDoc(doc(strangerDb, `businesses/${BUSINESS_ID}/harvests/harvest_seed_1`)));
    await assertFails(getDoc(doc(unauthDb, `businesses/${BUSINESS_ID}/harvests/harvest_seed_1`)));

    await assertSucceeds(
      setDoc(
        doc(operatorDb, `businesses/${BUSINESS_ID}/harvests/h_op`),
        baseHarvest({ createdBy: OPERATOR_UID, note: "Recolección mañana" }),
      ),
    );
    await assertSucceeds(
      setDoc(
        doc(adminDb, `businesses/${BUSINESS_ID}/harvests/h_admin`),
        baseHarvest({ createdBy: ADMIN_UID }),
      ),
    );
    await assertFails(
      setDoc(
        doc(viewerDb, `businesses/${BUSINESS_ID}/harvests/h_viewer`),
        baseHarvest({ createdBy: VIEWER_UID }),
      ),
    );
    await assertFails(
      setDoc(
        doc(inactiveDb, `businesses/${BUSINESS_ID}/harvests/h_inactive`),
        baseHarvest({ createdBy: INACTIVE_UID }),
      ),
    );
    await assertFails(
      setDoc(
        doc(strangerDb, `businesses/${BUSINESS_ID}/harvests/h_stranger`),
        baseHarvest({ createdBy: STRANGER_UID }),
      ),
    );
    await assertFails(
      setDoc(
        doc(unauthDb, `businesses/${BUSINESS_ID}/harvests/h_unauth`),
        baseHarvest(),
      ),
    );
  });

  it("H2 (AC-35, AC-52, AC-57): enforces validMovement (createdBy, startDate, createdAt <= +5m, syncedAt == serverTimestamp)", async () => {
    const operatorDb = testEnv.authenticatedContext(OPERATOR_UID).firestore();
    const futureTs = Timestamp.fromMillis(Date.now() + 10 * 60 * 1000);
    const pastTs = Timestamp.fromMillis(Date.now() - 60 * 60 * 1000);

    await assertFails(
      setDoc(
        doc(operatorDb, `businesses/${BUSINESS_ID}/harvests/h_bad_uid`),
        baseHarvest({ createdBy: ADMIN_UID }),
      ),
    );
    await assertFails(
      setDoc(
        doc(operatorDb, `businesses/${BUSINESS_ID}/harvests/h_before_start`),
        baseHarvest({ fechaOperativa: "2026-10-31" }),
      ),
    );
    await assertFails(
      setDoc(
        doc(operatorDb, `businesses/${BUSINESS_ID}/harvests/h_future_ts`),
        baseHarvest({ createdAt: futureTs }),
      ),
    );
    await assertFails(
      setDoc(
        doc(operatorDb, `businesses/${BUSINESS_ID}/harvests/h_fixed_sync`),
        baseHarvest({ syncedAt: pastTs }),
      ),
    );
    await assertSucceeds(
      setDoc(
        doc(operatorDb, `businesses/${BUSINESS_ID}/harvests/h_ok`),
        baseHarvest(),
      ),
    );
  });

  it("H3 (§8.3): validates graded map keys/non-negative ints, discarded non-negative int, and closed fields", async () => {
    const operatorDb = testEnv.authenticatedContext(OPERATOR_UID).firestore();

    await assertFails(
      setDoc(
        doc(operatorDb, `businesses/${BUSINESS_ID}/harvests/h_bad_class`),
        baseHarvest({ graded: { XL: 10 } }),
      ),
    );
    await assertFails(
      setDoc(
        doc(operatorDb, `businesses/${BUSINESS_ID}/harvests/h_neg_graded`),
        baseHarvest({ graded: { AA: -1 } }),
      ),
    );
    await assertFails(
      setDoc(
        doc(operatorDb, `businesses/${BUSINESS_ID}/harvests/h_dec_graded`),
        baseHarvest({ graded: { AA: 10.5 } }),
      ),
    );
    await assertFails(
      setDoc(
        doc(operatorDb, `businesses/${BUSINESS_ID}/harvests/h_neg_discarded`),
        baseHarvest({ discarded: -1 }),
      ),
    );
    await assertFails(
      setDoc(
        doc(operatorDb, `businesses/${BUSINESS_ID}/harvests/h_extra_field`),
        baseHarvest({ extra: 1 }),
      ),
    );
    await assertSucceeds(
      setDoc(
        doc(operatorDb, `businesses/${BUSINESS_ID}/harvests/h_all_zero`),
        baseHarvest({ graded: { B: 0, A: 0, AA: 0, AAA: 0, JUMBO: 0 }, discarded: 0 }),
      ),
    );
  });

  it("H4 (AC-34): harvests are immutable (update and delete denied)", async () => {
    const adminDb = testEnv.authenticatedContext(ADMIN_UID).firestore();

    await assertFails(
      updateDoc(doc(adminDb, `businesses/${BUSINESS_ID}/harvests/harvest_seed_1`), { discarded: 0 }),
    );
    await assertFails(deleteDoc(doc(adminDb, `businesses/${BUSINESS_ID}/harvests/harvest_seed_1`)));
  });
});
