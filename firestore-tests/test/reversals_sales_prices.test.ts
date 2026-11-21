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
  writeBatch,
} from "firebase/firestore";

const PROJECT_ID = "demo-egglytics-test";
const BUSINESS_ID = "egglytics_default";
const ADMIN_UID = "admin_1";
const OPERATOR_UID = "operator_1";
const VIEWER_UID = "viewer_1";
const STRANGER_UID = "stranger_1";

const ALLOWED_REVERSAL_TARGETS = [
  "openingBalances",
  "harvests",
  "sales",
  "payments",
  "saleReturns",
  "eggOutflows",
  "feedPurchases",
  "feedDepletions",
  "feedLosses",
  "expenses",
  "withdrawals",
] as const;

const DISALLOWED_REVERSAL_TARGETS = [
  "reversals",
  "counts",
  "closings",
  "prices",
  "customers",
  "members",
] as const;

let testEnv: RulesTestEnvironment;

function basePrice(overrides: Record<string, unknown> = {}): Record<string, unknown> {
  return {
    classification: "AA",
    pricePerBucket: 18000,
    validFrom: "2026-11-18",
    createdAt: Timestamp.now(),
    createdBy: ADMIN_UID,
    syncedAt: serverTimestamp(),
    ...overrides,
  };
}

function baseSale(overrides: Record<string, unknown> = {}): Record<string, unknown> {
  const createdAt = Timestamp.now();
  return {
    fechaOperativa: "2026-11-18",
    createdAt,
    syncedAt: serverTimestamp(),
    createdBy: OPERATOR_UID,
    lines: [{ classification: "AA", buckets: 2, eggs: 60, listPrice: 18000, unitPrice: 18000, subtotal: 36000 }],
    total: 36000,
    deliveryStatus: "pending",
    ...overrides,
  };
}

function baseReversal(overrides: Record<string, unknown> = {}): Record<string, unknown> {
  return {
    fechaOperativa: "2026-11-18",
    createdAt: Timestamp.now(),
    syncedAt: serverTimestamp(),
    createdBy: ADMIN_UID,
    reason: "Error de registro",
    targetCollection: "payments",
    targetId: "target_doc_1",
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
    await setDoc(doc(db, `businesses/${BUSINESS_ID}/members/${VIEWER_UID}`), {
      name: "Solo Lectura",
      email: "viewer@example.com",
      role: "viewer",
      active: true,
      createdAt: Timestamp.now(),
    });

    await setDoc(doc(db, `businesses/${BUSINESS_ID}/prices/price_seed_1`), {
      classification: "AA",
      pricePerBucket: 18000,
      validFrom: "2026-11-01",
      createdAt: Timestamp.now(),
      createdBy: ADMIN_UID,
      syncedAt: Timestamp.now(),
    });

    await setDoc(doc(db, `businesses/${BUSINESS_ID}/sales/sale_pending_1`), {
      fechaOperativa: "2026-11-18",
      createdAt: Timestamp.now(),
      syncedAt: Timestamp.now(),
      createdBy: OPERATOR_UID,
      lines: [{ classification: "AA", buckets: 1, eggs: 30, listPrice: 18000, unitPrice: 18000, subtotal: 18000 }],
      total: 18000,
      deliveryStatus: "pending",
    });

    const deliveredTs = Timestamp.now();
    await setDoc(doc(db, `businesses/${BUSINESS_ID}/sales/sale_delivered_1`), {
      fechaOperativa: "2026-11-18",
      createdAt: deliveredTs,
      syncedAt: deliveredTs,
      createdBy: OPERATOR_UID,
      lines: [{ classification: "AA", buckets: 1, eggs: 30, listPrice: 18000, unitPrice: 18000, subtotal: 18000 }],
      total: 18000,
      deliveryStatus: "delivered",
      deliveredAt: deliveredTs,
      deliveredBy: OPERATOR_UID,
    });

    for (const coll of ALLOWED_REVERSAL_TARGETS) {
      await setDoc(doc(db, `businesses/${BUSINESS_ID}/${coll}/target_doc_1`), {
        seeded: true,
      });
    }
    for (const coll of DISALLOWED_REVERSAL_TARGETS) {
      await setDoc(doc(db, `businesses/${BUSINESS_ID}/${coll}/target_doc_1`), {
        seeded: true,
      });
    }

    await setDoc(doc(db, `businesses/${BUSINESS_ID}/saleReturns/sr_joint_1`), {
      seeded: true,
    });
    await setDoc(doc(db, `businesses/${BUSINESS_ID}/payments/pay_refund_joint_1`), {
      seeded: true,
    });
    await setDoc(doc(db, `businesses/${BUSINESS_ID}/reversals/rev_seed_1`), {
      fechaOperativa: "2026-11-18",
      createdAt: Timestamp.now(),
      syncedAt: Timestamp.now(),
      createdBy: ADMIN_UID,
      reason: "Seed",
      targetCollection: "payments",
      targetId: "target_doc_1",
    });
  });
}

describe("Task 3b-1: Firestore rules (prices, sales, reversals)", () => {
  before(async () => {
    const rulesPath = process.env.RULES_FILE
      ? resolve(process.env.RULES_FILE)
      : resolve(process.cwd(), "..", "firestore.rules");
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

  // --- BLOQUE A: prices ---
  it("P1 (AC-33, R-04): only admin can create price (operator and viewer denied)", async () => {
    const adminDb = testEnv.authenticatedContext(ADMIN_UID).firestore();
    const operatorDb = testEnv.authenticatedContext(OPERATOR_UID).firestore();
    const viewerDb = testEnv.authenticatedContext(VIEWER_UID).firestore();

    await assertFails(
      setDoc(
        doc(operatorDb, `businesses/${BUSINESS_ID}/prices/p_op`),
        basePrice({ createdBy: OPERATOR_UID }),
      ),
    );
    await assertFails(
      setDoc(
        doc(viewerDb, `businesses/${BUSINESS_ID}/prices/p_view`),
        basePrice({ createdBy: VIEWER_UID }),
      ),
    );
    await assertSucceeds(
      setDoc(
        doc(adminDb, `businesses/${BUSINESS_ID}/prices/p_admin`),
        basePrice({ createdBy: ADMIN_UID }),
      ),
    );
  });

  it("P2 (AC-64): allows validFrom < startDate on price and rejects invalid calendar date", async () => {
    const adminDb = testEnv.authenticatedContext(ADMIN_UID).firestore();

    await assertSucceeds(
      setDoc(
        doc(adminDb, `businesses/${BUSINESS_ID}/prices/p_before_start`),
        basePrice({ validFrom: "2026-01-01" }),
      ),
    );
    await assertFails(
      setDoc(
        doc(adminDb, `businesses/${BUSINESS_ID}/prices/p_bad_feb30`),
        basePrice({ validFrom: "2026-02-30" }),
      ),
    );
    await assertFails(
      setDoc(
        doc(adminDb, `businesses/${BUSINESS_ID}/prices/p_bad_m13`),
        basePrice({ validFrom: "2026-13-01" }),
      ),
    );
  });

  it("P3 (AC-64, R-22): enforces createdBy == auth.uid, createdAt <= +5m, syncedAt == serverTimestamp", async () => {
    const adminDb = testEnv.authenticatedContext(ADMIN_UID).firestore();
    const futureTs = Timestamp.fromMillis(Date.now() + 10 * 60 * 1000);
    const pastTs = Timestamp.fromMillis(Date.now() - 60 * 60 * 1000);

    await assertFails(
      setDoc(
        doc(adminDb, `businesses/${BUSINESS_ID}/prices/p_other_uid`),
        basePrice({ createdBy: OPERATOR_UID }),
      ),
    );
    await assertFails(
      setDoc(
        doc(adminDb, `businesses/${BUSINESS_ID}/prices/p_future`),
        basePrice({ createdAt: futureTs }),
      ),
    );
    await assertFails(
      setDoc(
        doc(adminDb, `businesses/${BUSINESS_ID}/prices/p_fixed_synced`),
        basePrice({ syncedAt: pastTs }),
      ),
    );
    await assertSucceeds(
      setDoc(
        doc(adminDb, `businesses/${BUSINESS_ID}/prices/p_valid_ts`),
        basePrice(),
      ),
    );
  });

  it("P4 (R-04): validates price classification, positive int pricePerBucket and closed fields", async () => {
    const adminDb = testEnv.authenticatedContext(ADMIN_UID).firestore();

    await assertFails(
      setDoc(
        doc(adminDb, `businesses/${BUSINESS_ID}/prices/p_zero`),
        basePrice({ pricePerBucket: 0 }),
      ),
    );
    await assertFails(
      setDoc(
        doc(adminDb, `businesses/${BUSINESS_ID}/prices/p_decimal`),
        basePrice({ pricePerBucket: 12.5 }),
      ),
    );
    await assertFails(
      setDoc(
        doc(adminDb, `businesses/${BUSINESS_ID}/prices/p_bad_class`),
        basePrice({ classification: "XL" }),
      ),
    );
    await assertFails(
      setDoc(
        doc(adminDb, `businesses/${BUSINESS_ID}/prices/p_extra`),
        basePrice({ extraField: "nope" }),
      ),
    );
    await assertSucceeds(
      setDoc(
        doc(adminDb, `businesses/${BUSINESS_ID}/prices/p_valid_note`),
        basePrice({ note: "Precio nuevo" }),
      ),
    );
  });

  it("P5 (AC-32, AC-34, AC-58): prices are immutable (update/delete denied) and readable only by active members", async () => {
    const adminDb = testEnv.authenticatedContext(ADMIN_UID).firestore();
    const operatorDb = testEnv.authenticatedContext(OPERATOR_UID).firestore();
    const viewerDb = testEnv.authenticatedContext(VIEWER_UID).firestore();
    const strangerDb = testEnv.authenticatedContext(STRANGER_UID).firestore();
    const unauthDb = testEnv.unauthenticatedContext().firestore();

    await assertSucceeds(getDoc(doc(operatorDb, `businesses/${BUSINESS_ID}/prices/price_seed_1`)));
    await assertSucceeds(getDoc(doc(viewerDb, `businesses/${BUSINESS_ID}/prices/price_seed_1`)));
    await assertFails(getDoc(doc(strangerDb, `businesses/${BUSINESS_ID}/prices/price_seed_1`)));
    await assertFails(getDoc(doc(unauthDb, `businesses/${BUSINESS_ID}/prices/price_seed_1`)));
    await assertFails(
      updateDoc(doc(adminDb, `businesses/${BUSINESS_ID}/prices/price_seed_1`), { pricePerBucket: 19000 }),
    );
    await assertFails(deleteDoc(doc(adminDb, `businesses/${BUSINESS_ID}/prices/price_seed_1`)));
  });

  // --- BLOQUE B: sales ---
  it("S1 (AC-33): viewer cannot create sale (twin: operator creates)", async () => {
    const viewerDb = testEnv.authenticatedContext(VIEWER_UID).firestore();
    const operatorDb = testEnv.authenticatedContext(OPERATOR_UID).firestore();

    await assertFails(
      setDoc(
        doc(viewerDb, `businesses/${BUSINESS_ID}/sales/s_viewer`),
        baseSale({ createdBy: VIEWER_UID }),
      ),
    );
    await assertSucceeds(
      setDoc(
        doc(operatorDb, `businesses/${BUSINESS_ID}/sales/s_operator`),
        baseSale({ createdBy: OPERATOR_UID }),
      ),
    );
  });

  it("S2 (AC-52): sale enforces fechaOperativa >= startDate", async () => {
    const operatorDb = testEnv.authenticatedContext(OPERATOR_UID).firestore();

    await assertFails(
      setDoc(
        doc(operatorDb, `businesses/${BUSINESS_ID}/sales/s_before_start`),
        baseSale({ fechaOperativa: "2026-10-31" }),
      ),
    );
    await assertSucceeds(
      setDoc(
        doc(operatorDb, `businesses/${BUSINESS_ID}/sales/s_on_start`),
        baseSale({ fechaOperativa: "2026-11-01" }),
      ),
    );
  });

  it("S3 (AC-59, R-05): validates pending vs delivered fields on sale create", async () => {
    const operatorDb = testEnv.authenticatedContext(OPERATOR_UID).firestore();
    const ts = Timestamp.now();

    await assertFails(
      setDoc(
        doc(operatorDb, `businesses/${BUSINESS_ID}/sales/s_pend_with_deliv`),
        baseSale({ deliveryStatus: "pending", deliveredAt: ts }),
      ),
    );
    await assertFails(
      setDoc(
        doc(operatorDb, `businesses/${BUSINESS_ID}/sales/s_pend_with_by`),
        baseSale({ deliveryStatus: "pending", deliveredBy: OPERATOR_UID }),
      ),
    );
    await assertFails(
      setDoc(
        doc(operatorDb, `businesses/${BUSINESS_ID}/sales/s_deliv_diff_ts`),
        baseSale({
          createdAt: ts,
          deliveryStatus: "delivered",
          deliveredAt: Timestamp.fromMillis(ts.toMillis() - 1000),
          deliveredBy: OPERATOR_UID,
        }),
      ),
    );
    await assertFails(
      setDoc(
        doc(operatorDb, `businesses/${BUSINESS_ID}/sales/s_deliv_other_by`),
        baseSale({
          createdAt: ts,
          deliveryStatus: "delivered",
          deliveredAt: ts,
          deliveredBy: ADMIN_UID,
        }),
      ),
    );
    await assertSucceeds(
      setDoc(
        doc(operatorDb, `businesses/${BUSINESS_ID}/sales/s_deliv_ok`),
        baseSale({
          createdAt: ts,
          deliveryStatus: "delivered",
          deliveredAt: ts,
          deliveredBy: OPERATOR_UID,
        }),
      ),
    );
  });

  it("S4 (R-05): sale requires non-empty lines and positive integer total", async () => {
    const operatorDb = testEnv.authenticatedContext(OPERATOR_UID).firestore();

    await assertFails(
      setDoc(
        doc(operatorDb, `businesses/${BUSINESS_ID}/sales/s_empty_lines`),
        baseSale({ lines: [] }),
      ),
    );
    await assertFails(
      setDoc(
        doc(operatorDb, `businesses/${BUSINESS_ID}/sales/s_zero_total`),
        baseSale({ total: 0 }),
      ),
    );
    await assertSucceeds(
      setDoc(
        doc(operatorDb, `businesses/${BUSINESS_ID}/sales/s_valid`),
        baseSale(),
      ),
    );
  });

  it("S5 (AC-33, AC-59): only operator/admin can mark pending -> delivered (viewer and already-delivered denied)", async () => {
    const viewerDb = testEnv.authenticatedContext(VIEWER_UID).firestore();
    const operatorDb = testEnv.authenticatedContext(OPERATOR_UID).firestore();
    const now = Timestamp.now();

    await assertFails(
      updateDoc(doc(viewerDb, `businesses/${BUSINESS_ID}/sales/sale_pending_1`), {
        deliveryStatus: "delivered",
        deliveredAt: now,
        deliveredBy: VIEWER_UID,
      }),
    );
    await assertFails(
      updateDoc(doc(operatorDb, `businesses/${BUSINESS_ID}/sales/sale_delivered_1`), {
        deliveryStatus: "delivered",
        deliveredAt: now,
        deliveredBy: OPERATOR_UID,
      }),
    );
    await assertSucceeds(
      updateDoc(doc(operatorDb, `businesses/${BUSINESS_ID}/sales/sale_pending_1`), {
        deliveryStatus: "delivered",
        deliveredAt: now,
        deliveredBy: OPERATOR_UID,
      }),
    );
  });

  it("S6 (AC-59): sale delivery update rejects invalid deliveredBy, future deliveredAt, or edits to total/lines/fechaOperativa (one by one with twin)", async () => {
    const operatorDb = testEnv.authenticatedContext(OPERATOR_UID).firestore();
    const now = Timestamp.now();
    const futureTs = Timestamp.fromMillis(Date.now() + 10 * 60 * 1000);
    const saleRef = doc(operatorDb, `businesses/${BUSINESS_ID}/sales/sale_pending_1`);

    await assertFails(
      updateDoc(saleRef, {
        deliveryStatus: "delivered",
        deliveredAt: now,
        deliveredBy: ADMIN_UID,
      }),
    );
    await assertFails(
      updateDoc(saleRef, {
        deliveryStatus: "delivered",
        deliveredAt: futureTs,
        deliveredBy: OPERATOR_UID,
      }),
    );
    await assertFails(
      updateDoc(saleRef, {
        deliveryStatus: "delivered",
        deliveredAt: now,
        deliveredBy: OPERATOR_UID,
        total: 1,
      }),
    );
    await assertFails(
      updateDoc(saleRef, {
        deliveryStatus: "delivered",
        deliveredAt: now,
        deliveredBy: OPERATOR_UID,
        lines: [],
      }),
    );
    await assertFails(
      updateDoc(saleRef, {
        deliveryStatus: "delivered",
        deliveredAt: now,
        deliveredBy: OPERATOR_UID,
        fechaOperativa: "2026-11-19",
      }),
    );
    await assertSucceeds(
      updateDoc(saleRef, {
        deliveryStatus: "delivered",
        deliveredAt: now,
        deliveredBy: OPERATOR_UID,
      }),
    );
  });

  it("S7 (AC-34): sales cannot be deleted", async () => {
    const adminDb = testEnv.authenticatedContext(ADMIN_UID).firestore();
    await assertFails(deleteDoc(doc(adminDb, `businesses/${BUSINESS_ID}/sales/sale_pending_1`)));
  });

  it("S8 (AC-59): sale create rejects extra unknown fields (twin: without extra field and with optional fields succeeds)", async () => {
    const operatorDb = testEnv.authenticatedContext(OPERATOR_UID).firestore();
    const strangerDb = testEnv.authenticatedContext(STRANGER_UID).firestore();
    const unauthDb = testEnv.unauthenticatedContext().firestore();
    const ts = Timestamp.now();

    await assertSucceeds(getDoc(doc(operatorDb, `businesses/${BUSINESS_ID}/sales/sale_pending_1`)));
    await assertFails(getDoc(doc(strangerDb, `businesses/${BUSINESS_ID}/sales/sale_pending_1`)));
    await assertFails(getDoc(doc(unauthDb, `businesses/${BUSINESS_ID}/sales/sale_pending_1`)));

    await assertFails(
      setDoc(
        doc(operatorDb, `businesses/${BUSINESS_ID}/sales/s_extra_field`),
        baseSale({ foo: 1 }),
      ),
    );
    await assertSucceeds(
      setDoc(
        doc(operatorDb, `businesses/${BUSINESS_ID}/sales/s_no_extra_field`),
        baseSale({ customerId: "cli_1", customerName: "Tienda La Esquina", note: "Entregar temprano" }),
      ),
    );
    await assertSucceeds(
      setDoc(
        doc(operatorDb, `businesses/${BUSINESS_ID}/sales/s_deliv_all_fields`),
        baseSale({
          createdAt: ts,
          customerId: "cli_1",
          customerName: "Tienda La Esquina",
          note: "Entregada en tienda",
          deliveryStatus: "delivered",
          deliveredAt: ts,
          deliveredBy: OPERATOR_UID,
        }),
      ),
    );
  });

  // --- BLOQUE C: reversals ---
  it("R1 (AC-33, R-18): only admin can create reversal (operator and viewer denied)", async () => {
    const adminDb = testEnv.authenticatedContext(ADMIN_UID).firestore();
    const operatorDb = testEnv.authenticatedContext(OPERATOR_UID).firestore();
    const viewerDb = testEnv.authenticatedContext(VIEWER_UID).firestore();

    await assertFails(
      setDoc(
        doc(operatorDb, `businesses/${BUSINESS_ID}/reversals/rev_op`),
        baseReversal({ createdBy: OPERATOR_UID }),
      ),
    );
    await assertFails(
      setDoc(
        doc(viewerDb, `businesses/${BUSINESS_ID}/reversals/rev_view`),
        baseReversal({ createdBy: VIEWER_UID }),
      ),
    );
    await assertSucceeds(
      setDoc(
        doc(adminDb, `businesses/${BUSINESS_ID}/reversals/rev_admin`),
        baseReversal({ createdBy: ADMIN_UID }),
      ),
    );
  });

  it("R2 (AC-52, R-18): reversal requires non-empty reason and fechaOperativa >= startDate", async () => {
    const adminDb = testEnv.authenticatedContext(ADMIN_UID).firestore();

    await assertFails(
      setDoc(
        doc(adminDb, `businesses/${BUSINESS_ID}/reversals/rev_empty_reason`),
        baseReversal({ reason: "" }),
      ),
    );
    await assertFails(
      setDoc(
        doc(adminDb, `businesses/${BUSINESS_ID}/reversals/rev_before_start`),
        baseReversal({ fechaOperativa: "2026-10-31" }),
      ),
    );
    await assertSucceeds(
      setDoc(
        doc(adminDb, `businesses/${BUSINESS_ID}/reversals/rev_valid`),
        baseReversal(),
      ),
    );
  });

  it("R3 (AC-58, R-18): table test for ALL allowed and disallowed targetCollection values", async () => {
    const adminDb = testEnv.authenticatedContext(ADMIN_UID).firestore();

    for (const coll of ALLOWED_REVERSAL_TARGETS) {
      await assertSucceeds(
        setDoc(
          doc(adminDb, `businesses/${BUSINESS_ID}/reversals/rev_ok_${coll}`),
          baseReversal({ targetCollection: coll, targetId: "target_doc_1" }),
        ),
      );
    }

    for (const coll of DISALLOWED_REVERSAL_TARGETS) {
      await assertFails(
        setDoc(
          doc(adminDb, `businesses/${BUSINESS_ID}/reversals/rev_bad_${coll}`),
          baseReversal({ targetCollection: coll, targetId: "target_doc_1" }),
        ),
      );
    }
  });

  it("R4 (AC-58): reversal requires existing targetId and rejects targetId containing '/'", async () => {
    const adminDb = testEnv.authenticatedContext(ADMIN_UID).firestore();

    await assertFails(
      setDoc(
        doc(adminDb, `businesses/${BUSINESS_ID}/reversals/rev_missing`),
        baseReversal({ targetCollection: "payments", targetId: "does_not_exist" }),
      ),
    );
    await assertFails(
      setDoc(
        doc(adminDb, `businesses/${BUSINESS_ID}/reversals/rev_slash`),
        baseReversal({ targetCollection: "payments", targetId: "a/b" }),
      ),
    );
    await assertSucceeds(
      setDoc(
        doc(adminDb, `businesses/${BUSINESS_ID}/reversals/rev_exists`),
        baseReversal({ targetCollection: "payments", targetId: "target_doc_1" }),
      ),
    );
  });

  it("R5 (AC-58, R-19): joint cancellation WriteBatch (saleReturn + refund) succeeds when both exist and fails atomically if one target is missing", async () => {
    const adminDb = testEnv.authenticatedContext(ADMIN_UID).firestore();

    const badBatch = writeBatch(adminDb);
    badBatch.set(
      doc(adminDb, `businesses/${BUSINESS_ID}/reversals/rev_joint_bad_sr`),
      baseReversal({ targetCollection: "saleReturns", targetId: "sr_joint_1" }),
    );
    badBatch.set(
      doc(adminDb, `businesses/${BUSINESS_ID}/reversals/rev_joint_bad_pay`),
      baseReversal({ targetCollection: "payments", targetId: "missing_refund" }),
    );
    await assertFails(badBatch.commit());

    const goodBatch = writeBatch(adminDb);
    goodBatch.set(
      doc(adminDb, `businesses/${BUSINESS_ID}/reversals/rev_joint_ok_sr`),
      baseReversal({ targetCollection: "saleReturns", targetId: "sr_joint_1" }),
    );
    goodBatch.set(
      doc(adminDb, `businesses/${BUSINESS_ID}/reversals/rev_joint_ok_pay`),
      baseReversal({ targetCollection: "payments", targetId: "pay_refund_joint_1" }),
    );
    await assertSucceeds(goodBatch.commit());
  });

  it("R6 (AC-34): reversals are immutable (update and delete denied)", async () => {
    const adminDb = testEnv.authenticatedContext(ADMIN_UID).firestore();

    await assertFails(
      updateDoc(doc(adminDb, `businesses/${BUSINESS_ID}/reversals/rev_seed_1`), { reason: "Editado" }),
    );
    await assertFails(deleteDoc(doc(adminDb, `businesses/${BUSINESS_ID}/reversals/rev_seed_1`)));
  });
});
