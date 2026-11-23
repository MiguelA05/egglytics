import { describe, it, before, after, beforeEach } from "node:test";
import assert from "node:assert/strict";
import {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
  type RulesTestEnvironment,
} from "@firebase/rules-unit-testing";
import { readFileSync } from "node:fs";
import { doc, setDoc, updateDoc, deleteDoc, writeBatch, Timestamp, serverTimestamp } from "firebase/firestore";

const PROJECT_ID = "demo-egglytics-counts-rules";
const BUSINESS_ID = "egglytics_default";
const OPERATOR_UID = "operator_1";
const ADMIN_UID = "admin_1";

let testEnv: RulesTestEnvironment;

function baseMove(uid: string) {
  return {
    fechaOperativa: "2026-11-23",
    createdAt: Timestamp.now(),
    syncedAt: serverTimestamp(),
    createdBy: uid,
  };
}

describe("OpeningBalances, counts, closings and reversals rules", () => {
  before(async () => {
    testEnv = await initializeTestEnvironment({
      projectId: PROJECT_ID,
      firestore: {
        host: "127.0.0.1",
        port: 8080,
        rules: readFileSync("../firestore.rules", "utf8"),
      },
    });
  });

  after(async () => {
    await testEnv.cleanup();
  });

  beforeEach(async () => {
    await testEnv.clearFirestore();
    await testEnv.withSecurityRulesDisabled(async (ctx) => {
      const db = ctx.firestore();
      await setDoc(doc(db, `businesses/${BUSINESS_ID}`), {
        name: "Huevos Frescos",
        currency: "COP",
        timezone: "America/Bogota",
        startDate: "2026-11-01",
        active: true,
      });
      await setDoc(doc(db, `businesses/${BUSINESS_ID}/members/${OPERATOR_UID}`), {
        name: "Operador",
        email: "op@example.com",
        role: "operator",
        active: true,
        createdAt: Timestamp.now(),
      });
      await setDoc(doc(db, `businesses/${BUSINESS_ID}/members/${ADMIN_UID}`), {
        name: "Admin",
        email: "admin@example.com",
        role: "admin",
        active: true,
        createdAt: Timestamp.now(),
      });
    });
  });

  it("allows only admin to create openingBalances and enforces immutability", async () => {
    const opDb = testEnv.authenticatedContext(OPERATOR_UID).firestore();
    const adminDb = testEnv.authenticatedContext(ADMIN_UID).firestore();

    await assertFails(
      setDoc(doc(opDb, `businesses/${BUSINESS_ID}/openingBalances/ob_op`), {
        ...baseMove(OPERATOR_UID),
        kind: "cash",
        key: "cash",
        quantity: 100000,
      }),
    );

    const obRef = doc(adminDb, `businesses/${BUSINESS_ID}/openingBalances/ob_admin`);
    await assertSucceeds(
      setDoc(obRef, {
        ...baseMove(ADMIN_UID),
        kind: "cash",
        key: "cash",
        quantity: 100000,
      }),
    );
    await assertFails(updateDoc(obRef, { quantity: 200000 }));
    await assertFails(deleteDoc(obRef));
  });

  it("allows atomic closing WriteBatch (counts + closing) and denies reversing counts or closings", async () => {
    const opDb = testEnv.authenticatedContext(OPERATOR_UID).firestore();
    const adminDb = testEnv.authenticatedContext(ADMIN_UID).firestore();

    const batch = writeBatch(opDb);
    batch.set(doc(opDb, `businesses/${BUSINESS_ID}/counts/c_cash_1`), {
      ...baseMove(OPERATOR_UID),
      kind: "cash",
      items: {
        cash: { expected: 100000, physical: 95000, difference: -5000, reason: "Faltante caja" },
      },
      isClosing: true,
    });
    batch.set(doc(opDb, `businesses/${BUSINESS_ID}/closings/cl_1`), {
      ...baseMove(OPERATOR_UID),
      summary: { totalCash: 95000 },
      countIds: ["c_cash_1"],
    });
    await assertSucceeds(batch.commit());

    await assertFails(
      setDoc(doc(adminDb, `businesses/${BUSINESS_ID}/reversals/rev_count`), {
        ...baseMove(ADMIN_UID),
        targetCollection: "counts",
        targetId: "c_cash_1",
        reason: "No permitido",
      }),
    );
    await assertFails(
      setDoc(doc(adminDb, `businesses/${BUSINESS_ID}/reversals/rev_closing`), {
        ...baseMove(ADMIN_UID),
        targetCollection: "closings",
        targetId: "cl_1",
        reason: "No permitido",
      }),
    );
  });
});
