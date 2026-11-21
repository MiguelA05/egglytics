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
const INACTIVE_UID = "inactive_1";
const VIEWER_UID = "viewer_1";
const STRANGER_UID = "stranger_1";

let testEnv: RulesTestEnvironment;

function basePayment(overrides: Record<string, unknown> = {}): Record<string, unknown> {
  return {
    fechaOperativa: "2026-11-18",
    createdAt: Timestamp.now(),
    syncedAt: serverTimestamp(),
    createdBy: OPERATOR_UID,
    saleId: "sale_1",
    amount: 35000,
    methodId: "cash",
    type: "payment",
    ...overrides,
  };
}

async function seedTestData(
  options: { includeStartDate?: boolean; startDate?: string } = {},
): Promise<void> {
  const { includeStartDate = true, startDate = "2026-11-01" } = options;
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    const businessDoc: Record<string, unknown> = {
      name: "Granja Familiar",
      currency: "COP",
      timezone: "America/Bogota",
      active: true,
    };
    if (includeStartDate) {
      businessDoc.startDate = startDate;
    }
    await setDoc(doc(db, `businesses/${BUSINESS_ID}`), businessDoc);

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
    await setDoc(doc(db, `businesses/${BUSINESS_ID}/paymentMethods/cash`), {
      name: "Efectivo",
      active: true,
    });
    await setDoc(doc(db, `businesses/${BUSINESS_ID}/sales/sale_1`), {
      fechaOperativa: "2026-11-18",
      createdAt: Timestamp.now(),
      syncedAt: Timestamp.now(),
      createdBy: OPERATOR_UID,
      customerName: "Cliente Base",
      lines: [{ classification: "AA", buckets: 2, eggs: 60, listPrice: 18000, unitPrice: 18000, subtotal: 36000 }],
      total: 36000,
      deliveryStatus: "pending",
    });
  });
}

describe("Task 3a-2: Firestore minimal rules (businesses, members, payments, validMovement)", () => {
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

  it("1a/1b (AC-32): active member reads business, members and payments; unauthenticated denied", async () => {
    await testEnv.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), `businesses/${BUSINESS_ID}/payments/p_seed`), basePayment());
    });

    const opDb = testEnv.authenticatedContext(OPERATOR_UID).firestore();
    await assertSucceeds(getDoc(doc(opDb, `businesses/${BUSINESS_ID}`)));
    await assertSucceeds(getDoc(doc(opDb, `businesses/${BUSINESS_ID}/members/${ADMIN_UID}`)));
    await assertSucceeds(getDoc(doc(opDb, `businesses/${BUSINESS_ID}/payments/p_seed`)));

    const unauthDb = testEnv.unauthenticatedContext().firestore();
    await assertFails(getDoc(doc(unauthDb, `businesses/${BUSINESS_ID}`)));
    await assertFails(getDoc(doc(unauthDb, `businesses/${BUSINESS_ID}/members/${ADMIN_UID}`)));
    await assertFails(getDoc(doc(unauthDb, `businesses/${BUSINESS_ID}/payments/p_seed`)));
  });

  it("1c (AC-32): authenticated user without member doc cannot read (twin: active operator reads)", async () => {
    const opDb = testEnv.authenticatedContext(OPERATOR_UID).firestore();
    await assertSucceeds(getDoc(doc(opDb, `businesses/${BUSINESS_ID}`)));

    const strangerDb = testEnv.authenticatedContext(STRANGER_UID).firestore();
    await assertFails(getDoc(doc(strangerDb, `businesses/${BUSINESS_ID}`)));
  });

  it("1c-write (AC-32): authenticated user without member doc cannot write payment (twin: active operator writes)", async () => {
    const opDb = testEnv.authenticatedContext(OPERATOR_UID).firestore();
    await assertSucceeds(setDoc(doc(opDb, `businesses/${BUSINESS_ID}/payments/p_op_ok`), basePayment()));

    const strangerDb = testEnv.authenticatedContext(STRANGER_UID).firestore();
    await assertFails(
      setDoc(
        doc(strangerDb, `businesses/${BUSINESS_ID}/payments/p_stranger`),
        basePayment({ createdBy: STRANGER_UID }),
      ),
    );
  });

  it("1d (AC-32): inactive member cannot read (twin: active operator reads)", async () => {
    const opDb = testEnv.authenticatedContext(OPERATOR_UID).firestore();
    await assertSucceeds(getDoc(doc(opDb, `businesses/${BUSINESS_ID}`)));

    const inactiveDb = testEnv.authenticatedContext(INACTIVE_UID).firestore();
    await assertFails(getDoc(doc(inactiveDb, `businesses/${BUSINESS_ID}`)));
  });

  it("1d-write (AC-32): inactive member cannot write payment (twin: active operator writes)", async () => {
    const opDb = testEnv.authenticatedContext(OPERATOR_UID).firestore();
    await assertSucceeds(setDoc(doc(opDb, `businesses/${BUSINESS_ID}/payments/p_op_ok2`), basePayment()));

    const inactiveDb = testEnv.authenticatedContext(INACTIVE_UID).firestore();
    await assertFails(
      setDoc(
        doc(inactiveDb, `businesses/${BUSINESS_ID}/payments/p_inactive`),
        basePayment({ createdBy: INACTIVE_UID }),
      ),
    );
  });

  it("2a (AC-32, AC-65): both operator_1 and admin_1 can create base payment", async () => {
    const opDb = testEnv.authenticatedContext(OPERATOR_UID).firestore();
    const adminDb = testEnv.authenticatedContext(ADMIN_UID).firestore();

    await assertSucceeds(
      setDoc(
        doc(opDb, `businesses/${BUSINESS_ID}/payments/p_op`),
        basePayment({ createdBy: OPERATOR_UID }),
      ),
    );
    await assertSucceeds(
      setDoc(
        doc(adminDb, `businesses/${BUSINESS_ID}/payments/p_admin`),
        basePayment({ createdBy: ADMIN_UID }),
      ),
    );
  });

  it("2b (AC-33 partial): NOBODY (neither admin nor operator) can write businesses or members from client", async () => {
    // allow write: if false en businesses/{b} y sin reglas de escritura en members/{uid}; no existe gemelo de aceptacion.
    const adminDb = testEnv.authenticatedContext(ADMIN_UID).firestore();
    const opDb = testEnv.authenticatedContext(OPERATOR_UID).firestore();

    await assertFails(updateDoc(doc(adminDb, `businesses/${BUSINESS_ID}`), { name: "Otro" }));
    await assertFails(updateDoc(doc(opDb, `businesses/${BUSINESS_ID}`), { name: "Otro" }));
    await assertFails(
      updateDoc(doc(adminDb, `businesses/${BUSINESS_ID}/members/${OPERATOR_UID}`), { role: "admin" }),
    );
    await assertFails(
      updateDoc(doc(opDb, `businesses/${BUSINESS_ID}/members/${OPERATOR_UID}`), { role: "admin" }),
    );
  });

  it("3a (AC-34 partial): payments are immutable (update and delete denied)", async () => {
    const opDb = testEnv.authenticatedContext(OPERATOR_UID).firestore();
    const adminDb = testEnv.authenticatedContext(ADMIN_UID).firestore();

    await assertSucceeds(setDoc(doc(opDb, `businesses/${BUSINESS_ID}/payments/p1`), basePayment()));
    await assertFails(updateDoc(doc(adminDb, `businesses/${BUSINESS_ID}/payments/p1`), { note: "Editado" }));
    await assertFails(deleteDoc(doc(adminDb, `businesses/${BUSINESS_ID}/payments/p1`)));
  });

  it("4a/4b (AC-35): allows createdBy == auth.uid and denies createdBy != auth.uid", async () => {
    const opDb = testEnv.authenticatedContext(OPERATOR_UID).firestore();

    await assertSucceeds(setDoc(doc(opDb, `businesses/${BUSINESS_ID}/payments/p_ok`), basePayment()));
    await assertFails(
      setDoc(
        doc(opDb, `businesses/${BUSINESS_ID}/payments/p_spoof`),
        basePayment({ createdBy: ADMIN_UID }),
      ),
    );
  });

  it("5a/5b (AC-52 partial): allows fechaOperativa >= startDate and denies < startDate", async () => {
    const opDb = testEnv.authenticatedContext(OPERATOR_UID).firestore();

    await assertSucceeds(
      setDoc(
        doc(opDb, `businesses/${BUSINESS_ID}/payments/p_start`),
        basePayment({ fechaOperativa: "2026-11-01" }),
      ),
    );
    await assertFails(
      setDoc(
        doc(opDb, `businesses/${BUSINESS_ID}/payments/p_before`),
        basePayment({ fechaOperativa: "2026-10-31" }),
      ),
    );
  });

  it("5c: denies payment when business document has no startDate field (evaluation error)", async () => {
    await testEnv.clearFirestore();
    await seedTestData({ includeStartDate: false });

    const opDb = testEnv.authenticatedContext(OPERATOR_UID).firestore();
    await assertFails(setDoc(doc(opDb, `businesses/${BUSINESS_ID}/payments/p_no_start`), basePayment()));
  });

  it("6a/6b/6c (AC-57, R-22): enforces createdAt <= request.time + 5m and syncedAt == serverTimestamp", async () => {
    const opDb = testEnv.authenticatedContext(OPERATOR_UID).firestore();
    const nowMs = Date.now();

    await assertSucceeds(
      setDoc(
        doc(opDb, `businesses/${BUSINESS_ID}/payments/p_plus2m`),
        basePayment({ createdAt: Timestamp.fromMillis(nowMs + 2 * 60 * 1000) }),
      ),
    );
    await assertFails(
      setDoc(
        doc(opDb, `businesses/${BUSINESS_ID}/payments/p_plus10m`),
        basePayment({ createdAt: Timestamp.fromMillis(nowMs + 10 * 60 * 1000) }),
      ),
    );

    await assertSucceeds(setDoc(doc(opDb, `businesses/${BUSINESS_ID}/payments/p_good_sync`), basePayment()));
    await assertFails(
      setDoc(
        doc(opDb, `businesses/${BUSINESS_ID}/payments/p_bad_sync`),
        basePayment({ syncedAt: Timestamp.fromMillis(nowMs - 86_400_000) }),
      ),
    );
  });

  it("7a/7b/7c (AC-57): validDate accepts boundary dates and 2026-02-29, rejects impossible dates", async () => {
    await testEnv.clearFirestore();
    await seedTestData({ startDate: "2026-01-01" });
    const opDb = testEnv.authenticatedContext(OPERATOR_UID).firestore();

    for (const validDate of ["2026-11-30", "2026-12-31", "2028-02-29"]) {
      await assertSucceeds(
        setDoc(
          doc(opDb, `businesses/${BUSINESS_ID}/payments/p_v_${validDate}`),
          basePayment({ fechaOperativa: validDate }),
        ),
      );
    }

    for (const badDate of ["2026-13-01", "2026-04-31", "2026-02-30"]) {
      await assertFails(
        setDoc(
          doc(opDb, `businesses/${BUSINESS_ID}/payments/p_b_${badDate}`),
          basePayment({ fechaOperativa: badDate }),
        ),
      );
    }

    // Limitación conocida (§11.1): el calendario exacto, incluido el 29 de febrero en años no bisiestos,
    // se valida solo en la app (:domain/OperationalDate). Si se endurece la regex de validDate,
    // actualiza ESPECIFICACION.md y esta prueba.
    await assertSucceeds(
      setDoc(
        doc(opDb, `businesses/${BUSINESS_ID}/payments/p_2026_02_29`),
        basePayment({ fechaOperativa: "2026-02-29" }),
      ),
    );
  });

  it("8a-8d (AC-65 payment): validates link exclusivity, amount, methodId, type and closed fields", async () => {
    const opDb = testEnv.authenticatedContext(OPERATOR_UID).firestore();

    await assertSucceeds(setDoc(doc(opDb, `businesses/${BUSINESS_ID}/payments/p_sale`), basePayment()));

    const byOpening = basePayment({ openingDebtId: "od_1" });
    delete byOpening.saleId;
    await assertSucceeds(setDoc(doc(opDb, `businesses/${BUSINESS_ID}/payments/p_od`), byOpening));

    await assertFails(
      setDoc(
        doc(opDb, `businesses/${BUSINESS_ID}/payments/p_both`),
        basePayment({ openingDebtId: "od_1" }),
      ),
    );

    const noLink = basePayment();
    delete noLink.saleId;
    await assertFails(setDoc(doc(opDb, `businesses/${BUSINESS_ID}/payments/p_nolink`), noLink));

    for (const badAmount of [0, -100, 12.5]) {
      await assertFails(
        setDoc(
          doc(opDb, `businesses/${BUSINESS_ID}/payments/p_amt_${badAmount}`),
          basePayment({ amount: badAmount }),
        ),
      );
    }

    const noMethod = basePayment();
    delete noMethod.methodId;
    await assertFails(setDoc(doc(opDb, `businesses/${BUSINESS_ID}/payments/p_nomethod`), noMethod));
    await assertFails(
      setDoc(
        doc(opDb, `businesses/${BUSINESS_ID}/payments/p_emptymethod`),
        basePayment({ methodId: "" }),
      ),
    );

    await assertFails(
      setDoc(
        doc(opDb, `businesses/${BUSINESS_ID}/payments/p_badtype`),
        basePayment({ type: "credit" }),
      ),
    );

    await assertFails(
      setDoc(
        doc(opDb, `businesses/${BUSINESS_ID}/payments/p_cust`),
        basePayment({ customerId: "cust_1" }),
      ),
    );
    await assertFails(
      setDoc(
        doc(opDb, `businesses/${BUSINESS_ID}/payments/p_sr`),
        basePayment({ saleReturnId: "sr_1" }),
      ),
    );
    await assertFails(
      setDoc(
        doc(opDb, `businesses/${BUSINESS_ID}/payments/p_reason`),
        basePayment({ reason: "extra" }),
      ),
    );
  });

  it("9a-9d (AC-65 refund): requires saleReturnId existing after batch and saleId (not openingDebtId)", async () => {
    const opDb = testEnv.authenticatedContext(OPERATOR_UID).firestore();

    const okBatch = writeBatch(opDb);
    okBatch.set(doc(opDb, `businesses/${BUSINESS_ID}/saleReturns/sr_1`), {
      fechaOperativa: "2026-11-18",
      createdAt: Timestamp.now(),
      syncedAt: serverTimestamp(),
      createdBy: OPERATOR_UID,
      saleId: "sale_1",
      classification: "AA",
      buckets: 1,
      creditAmount: 18000,
      restock: true,
      reason: "Devolucion cliente",
    });
    okBatch.set(
      doc(opDb, `businesses/${BUSINESS_ID}/payments/p_refund_ok`),
      basePayment({ type: "refund", saleReturnId: "sr_1", amount: 18000 }),
    );
    await assertSucceeds(okBatch.commit());

    await assertFails(
      setDoc(
        doc(opDb, `businesses/${BUSINESS_ID}/payments/p_refund_no_sr`),
        basePayment({ type: "refund", amount: 18000 }),
      ),
    );

    await assertFails(
      setDoc(
        doc(opDb, `businesses/${BUSINESS_ID}/payments/p_refund_missing_sr`),
        basePayment({ type: "refund", saleReturnId: "sr_does_not_exist", amount: 18000 }),
      ),
    );

    const badLinkBatch = writeBatch(opDb);
    badLinkBatch.set(doc(opDb, `businesses/${BUSINESS_ID}/saleReturns/sr_2`), {
      fechaOperativa: "2026-11-18",
      createdAt: Timestamp.now(),
      syncedAt: serverTimestamp(),
      createdBy: OPERATOR_UID,
      saleId: "sale_1",
      classification: "AA",
      buckets: 1,
      creditAmount: 18000,
      restock: true,
      reason: "Devolucion cliente",
    });
    const refundWithOd = basePayment({
      type: "refund",
      openingDebtId: "od_1",
      saleReturnId: "sr_2",
      amount: 18000,
    });
    delete refundWithOd.saleId;
    badLinkBatch.set(doc(opDb, `businesses/${BUSINESS_ID}/payments/p_refund_od`), refundWithOd);
    await assertFails(badLinkBatch.commit());
  });

  it("10a-10d (AC-65, AC-33 overpayment_refund): admin only, requires non-empty reason, forbids saleReturnId", async () => {
    const adminDb = testEnv.authenticatedContext(ADMIN_UID).firestore();
    const opDb = testEnv.authenticatedContext(OPERATOR_UID).firestore();

    await assertSucceeds(
      setDoc(
        doc(adminDb, `businesses/${BUSINESS_ID}/payments/p_opr_sale`),
        basePayment({
          createdBy: ADMIN_UID,
          type: "overpayment_refund",
          reason: "Correccion sobrepago",
          amount: 5000,
        }),
      ),
    );

    const oprOd = basePayment({
      createdBy: ADMIN_UID,
      openingDebtId: "od_1",
      type: "overpayment_refund",
      reason: "Correccion sobrepago apertura",
      amount: 5000,
    });
    delete oprOd.saleId;
    await assertSucceeds(setDoc(doc(adminDb, `businesses/${BUSINESS_ID}/payments/p_opr_od`), oprOd));

    await assertFails(
      setDoc(
        doc(opDb, `businesses/${BUSINESS_ID}/payments/p_opr_op`),
        basePayment({
          createdBy: OPERATOR_UID,
          type: "overpayment_refund",
          reason: "Intento operador",
          amount: 5000,
        }),
      ),
    );

    await assertFails(
      setDoc(
        doc(adminDb, `businesses/${BUSINESS_ID}/payments/p_opr_no_reason`),
        basePayment({
          createdBy: ADMIN_UID,
          type: "overpayment_refund",
          amount: 5000,
        }),
      ),
    );

    await assertFails(
      setDoc(
        doc(adminDb, `businesses/${BUSINESS_ID}/payments/p_opr_empty_reason`),
        basePayment({
          createdBy: ADMIN_UID,
          type: "overpayment_refund",
          reason: "",
          amount: 5000,
        }),
      ),
    );

    await assertFails(
      setDoc(
        doc(adminDb, `businesses/${BUSINESS_ID}/payments/p_opr_with_sr`),
        basePayment({
          createdBy: ADMIN_UID,
          type: "overpayment_refund",
          reason: "Correccion",
          saleReturnId: "sr_1",
          amount: 5000,
        }),
      ),
    );
  });

  it("11 (RF-29, AC-33): active member with role viewer can read business but cannot create payment (twin: operator creates)", async () => {
    const viewerDb = testEnv.authenticatedContext(VIEWER_UID).firestore();
    const operatorDb = testEnv.authenticatedContext(OPERATOR_UID).firestore();

    await assertSucceeds(getDoc(doc(viewerDb, `businesses/${BUSINESS_ID}`)));
    await assertFails(
      setDoc(
        doc(viewerDb, `businesses/${BUSINESS_ID}/payments/p_viewer_denied`),
        basePayment({ createdBy: VIEWER_UID }),
      ),
    );
    await assertSucceeds(
      setDoc(
        doc(operatorDb, `businesses/${BUSINESS_ID}/payments/p_operator_allowed`),
        basePayment({ createdBy: OPERATOR_UID }),
      ),
    );
  });

  it("12 (AC-32, R-07): active member reads paymentMethods (stranger/unauth/write denied); rejected sale+payment WriteBatch writes neither doc", async () => {
    const operatorDb = testEnv.authenticatedContext(OPERATOR_UID).firestore();
    const strangerDb = testEnv.authenticatedContext(STRANGER_UID).firestore();
    const unauthDb = testEnv.unauthenticatedContext().firestore();

    await assertSucceeds(getDoc(doc(operatorDb, `businesses/${BUSINESS_ID}/paymentMethods/cash`)));
    await assertFails(getDoc(doc(strangerDb, `businesses/${BUSINESS_ID}/paymentMethods/cash`)));
    await assertFails(getDoc(doc(unauthDb, `businesses/${BUSINESS_ID}/paymentMethods/cash`)));
    await assertFails(
      setDoc(doc(operatorDb, `businesses/${BUSINESS_ID}/paymentMethods/card`), {
        name: "Tarjeta",
        active: true,
      }),
    );

    const badBatch = writeBatch(operatorDb);
    badBatch.set(doc(operatorDb, `businesses/${BUSINESS_ID}/sales/s_batch_fail`), {
      fechaOperativa: "2026-11-18",
      createdAt: Timestamp.now(),
      syncedAt: serverTimestamp(),
      createdBy: OPERATOR_UID,
      lines: [{ classification: "AA", buckets: 2, eggs: 60, listPrice: 18500, unitPrice: 18500, subtotal: 37000 }],
      total: 37000,
      deliveryStatus: "pending",
    });
    badBatch.set(
      doc(operatorDb, `businesses/${BUSINESS_ID}/payments/p_batch_fail`),
      basePayment({ saleId: "s_batch_fail", amount: 0 }),
    );
    await assertFails(badBatch.commit());

    await testEnv.withSecurityRulesDisabled(async (ctx) => {
      const adminDb = ctx.firestore();
      const saleSnap = await getDoc(doc(adminDb, `businesses/${BUSINESS_ID}/sales/s_batch_fail`));
      const paySnap = await getDoc(doc(adminDb, `businesses/${BUSINESS_ID}/payments/p_batch_fail`));
      if (saleSnap.exists() || paySnap.exists()) {
        throw new Error("Rejected WriteBatch must not persist sale or payment");
      }
    });
  });
});
