import { describe, it, before, after, beforeEach } from "node:test";
import assert from "node:assert/strict";
import {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
  type RulesTestEnvironment,
} from "@firebase/rules-unit-testing";
import { readFileSync } from "node:fs";
import { doc, setDoc, updateDoc, deleteDoc, Timestamp, serverTimestamp } from "firebase/firestore";

const PROJECT_ID = "demo-egglytics-expenses-rules";
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

describe("Expenses, withdrawals and expenseCategories rules", () => {
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

  it("allows admin to write expenseCategories and denies operator", async () => {
    const opDb = testEnv.authenticatedContext(OPERATOR_UID).firestore();
    const adminDb = testEnv.authenticatedContext(ADMIN_UID).firestore();

    await assertFails(
      setDoc(doc(opDb, `businesses/${BUSINESS_ID}/expenseCategories/fuel`), {
        name: "Combustible",
        active: true,
      }),
    );
    await assertSucceeds(
      setDoc(doc(adminDb, `businesses/${BUSINESS_ID}/expenseCategories/fuel`), {
        name: "Combustible",
        active: true,
      }),
    );
  });

  it("allows active operator to create immutable expense and withdrawal, and rejects invalid amount", async () => {
    const opDb = testEnv.authenticatedContext(OPERATOR_UID).firestore();

    const expRef = doc(opDb, `businesses/${BUSINESS_ID}/expenses/exp_1`);
    await assertSucceeds(
      setDoc(expRef, {
        ...baseMove(OPERATOR_UID),
        categoryId: "fuel",
        amount: 25000,
        methodId: "cash",
        description: "Gasolina moto",
      }),
    );
    await assertFails(updateDoc(expRef, { amount: 1000 }));
    await assertFails(deleteDoc(expRef));

    const wRef = doc(opDb, `businesses/${BUSINESS_ID}/withdrawals/w_1`);
    await assertSucceeds(
      setDoc(wRef, {
        ...baseMove(OPERATOR_UID),
        amount: 50000,
        methodId: "cash",
        note: "Retiro hogar",
      }),
    );
    await assertFails(updateDoc(wRef, { amount: 1000 }));
    await assertFails(deleteDoc(wRef));

    await assertFails(
      setDoc(doc(opDb, `businesses/${BUSINESS_ID}/expenses/exp_zero`), {
        ...baseMove(OPERATOR_UID),
        categoryId: "fuel",
        amount: 0,
        methodId: "cash",
      }),
    );
  });
});
