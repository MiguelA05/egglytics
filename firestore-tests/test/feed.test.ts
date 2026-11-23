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

const PROJECT_ID = "demo-egglytics-feed-rules";
const BUSINESS_ID = "egglytics_default";
const OPERATOR_UID = "operator_1";

let testEnv: RulesTestEnvironment;

function baseMove(uid: string) {
  return {
    fechaOperativa: "2026-11-23",
    createdAt: Timestamp.now(),
    syncedAt: serverTimestamp(),
    createdBy: uid,
  };
}

describe("Feed rules (feedPurchases, feedDepletions, feedLosses)", () => {
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
    });
  });

  it("allows valid feedPurchase, feedDepletion and feedLoss by active operator and enforces immutability", async () => {
    const db = testEnv.authenticatedContext(OPERATOR_UID).firestore();

    const pRef = doc(db, `businesses/${BUSINESS_ID}/feedPurchases/fp_1`);
    await assertSucceeds(
      setDoc(pRef, {
        ...baseMove(OPERATOR_UID),
        ingredient: "concentrate",
        bags: 3,
        totalKg: 120,
        totalCost: 240000,
        methodId: "cash",
      }),
    );
    await assertFails(updateDoc(pRef, { totalCost: 1000 }));
    await assertFails(deleteDoc(pRef));

    await assertSucceeds(
      setDoc(doc(db, `businesses/${BUSINESS_ID}/feedDepletions/fd_1`), {
        ...baseMove(OPERATOR_UID),
        ingredient: "corn_bran",
        bags: 1,
        kg: 40,
      }),
    );

    await assertSucceeds(
      setDoc(doc(db, `businesses/${BUSINESS_ID}/feedLosses/fl_1`), {
        ...baseMove(OPERATOR_UID),
        ingredient: "concentrate",
        kg: 5,
        type: "loss",
        reason: "Humedad",
      }),
    );
  });

  it("rejects mismatched kg, missing reason or unknown fields", async () => {
    const db = testEnv.authenticatedContext(OPERATOR_UID).firestore();

    await assertFails(
      setDoc(doc(db, `businesses/${BUSINESS_ID}/feedPurchases/fp_bad_kg`), {
        ...baseMove(OPERATOR_UID),
        ingredient: "concentrate",
        bags: 2,
        totalKg: 50,
        totalCost: 100000,
        methodId: "cash",
      }),
    );

    await assertFails(
      setDoc(doc(db, `businesses/${BUSINESS_ID}/feedLosses/fl_no_reason`), {
        ...baseMove(OPERATOR_UID),
        ingredient: "concentrate",
        kg: 5,
        type: "loss",
        reason: "",
      }),
    );
  });
});
