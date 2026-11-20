/**
 * Script de medición de límites de lotes en Firestore Emulator (Tarea 4).
 *
 * IMPORTANTE: Este script depende del texto exacto de ../../firestore.rules
 * del commit be8a80b. Tras aplicar la variante B a firestore.rules, los reemplazos
 * abortarán con un error explícito (comportamiento esperado, no es un fallo).
 * Si se vuelve a ejecutar en el futuro, habrá que actualizar los anclajes aquí.
 */
import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
  type RulesTestEnvironment,
} from "@firebase/rules-unit-testing";
import { Timestamp, doc, serverTimestamp, setDoc, writeBatch } from "firebase/firestore";

const PROJECT_ID = "demo-egglytics-test";
const BUSINESS_ID = "egglytics_default";
const OPERATOR_UID = "operator_1";
const STRANGER_UID = "stranger_1";
const MAX_BATCH_TEST = 30;

interface VariantSpec {
  id: "A" | "B" | "C" | "D";
  label: string;
  rules: string;
  theoreticalCallsPerPayment: number;
  theoreticalMaxPayments: number;
  theoreticalRefundCalls: number;
  theoreticalClosingCalls: number;
  useClaims: boolean;
}

function buildRuleVariants(): VariantSpec[] {
  const baseRules = readFileSync(
    resolve(import.meta.dirname, "../../firestore.rules"),
    "utf8",
  ).replace(/\r\n/g, "\n");

  // Reglas temporales de counts y closings solo dentro de la medición (Tarea 4)
  const saleReturnsAnchor = `      // PROVISIONAL: sustituir en la Tarea 3b
      match /saleReturns/{id} {
        allow read: if isActive(b);
        allow create: if validMovement(b);
      }`;
  const withClosingCollections = `${saleReturnsAnchor}

      // TEMPORAL SOLO MEDICION TAREA 4
      match /counts/{id} {
        allow read: if isActive(b);
        allow create: if validMovement(b);
      }

      // TEMPORAL SOLO MEDICION TAREA 4
      match /closings/{id} {
        allow read: if isActive(b);
        allow create: if validMovement(b);
      }`;

  const rulesA = baseRules.replace(saleReturnsAnchor, withClosingCollections);
  if (rulesA === baseRules) {
    throw new Error("Falló la construcción de Variante A: no se encontró el bloque saleReturns.");
  }

  const originalMemberBlock = `    function memberData(b) {
      return get(/databases/$(database)/documents/businesses/$(b)/members/$(request.auth.uid)).data;
    }
    function businessData(b) {
      return get(/databases/$(database)/documents/businesses/$(b)).data;
    }
    function isActive(b) {
      return request.auth != null
        && exists(/databases/$(database)/documents/businesses/$(b)/members/$(request.auth.uid))
        && memberData(b).active == true;
    }
    function isAdmin(b) { return isActive(b) && memberData(b).role == 'admin'; }
    function isOperator(b) { return isActive(b) && memberData(b).role in ['admin', 'operator']; }`;

  const singleGetMemberBlock = `    function memberDoc(b) {
      return get(/databases/$(database)/documents/businesses/$(b)/members/$(request.auth.uid));
    }
    function businessData(b) {
      return get(/databases/$(database)/documents/businesses/$(b)).data;
    }
    function isActive(b) {
      let m = memberDoc(b);
      return request.auth != null && m != null && m.data.active == true;
    }
    function isAdmin(b) {
      let m = memberDoc(b);
      return request.auth != null && m != null && m.data.active == true && m.data.role == 'admin';
    }
    function isOperator(b) {
      let m = memberDoc(b);
      return request.auth != null && m != null && m.data.active == true && m.data.role in ['admin', 'operator'];
    }`;

  const rulesB = rulesA.replace(originalMemberBlock, singleGetMemberBlock);
  if (rulesB === rulesA) {
    throw new Error("Falló la construcción de Variante B: no se encontró el bloque de funciones de miembro.");
  }

  const startDateLine = "\n        && d.fechaOperativa >= businessData(b).startDate";
  const rulesC = rulesB.replace(startDateLine, "");
  if (rulesC === rulesB) {
    throw new Error("Falló la construcción de Variante C: no se encontró la línea de startDate.");
  }

  const claimsMemberBlock = `    function businessData(b) {
      return get(/databases/$(database)/documents/businesses/$(b)).data;
    }
    function isActive(b) {
      return request.auth != null && request.auth.token.role in ['admin', 'operator'];
    }
    function isAdmin(b) {
      return request.auth != null && request.auth.token.role == 'admin';
    }
    function isOperator(b) {
      return request.auth != null && request.auth.token.role in ['admin', 'operator'];
    }`;

  const rulesD = rulesB.replace(singleGetMemberBlock, claimsMemberBlock);
  if (rulesD === rulesB || rulesD === rulesA || rulesD === rulesC) {
    throw new Error("Falló la construcción de Variante D: no es distinta de A, B y C.");
  }

  if (new Set([rulesA, rulesB, rulesC, rulesD]).size !== 4) {
    throw new Error("Las 4 variantes de reglas deben ser distintas entre sí.");
  }

  return [
    {
      id: "A",
      label: "Actuales (exists + 2 get miembro + get startDate)",
      rules: rulesA,
      theoreticalCallsPerPayment: 4,
      theoreticalMaxPayments: 5,
      theoreticalRefundCalls: 9,
      theoreticalClosingCalls: 16,
      useClaims: false,
    },
    {
      id: "B",
      label: "1 get miembro (sin exists) + get startDate",
      rules: rulesB,
      theoreticalCallsPerPayment: 2,
      theoreticalMaxPayments: 10,
      theoreticalRefundCalls: 5,
      theoreticalClosingCalls: 8,
      useClaims: false,
    },
    {
      id: "C",
      label: "1 get miembro (sin exists) y sin startDate",
      rules: rulesC,
      theoreticalCallsPerPayment: 1,
      theoreticalMaxPayments: 20,
      theoreticalRefundCalls: 3,
      theoreticalClosingCalls: 4,
      useClaims: false,
    },
    {
      id: "D",
      label: "Claims en token (sin leer members) + get startDate",
      rules: rulesD,
      theoreticalCallsPerPayment: 1,
      theoreticalMaxPayments: 20,
      theoreticalRefundCalls: 3,
      theoreticalClosingCalls: 4,
      useClaims: true,
    },
  ];
}

async function seedBase(env: RulesTestEnvironment): Promise<void> {
  await env.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    await setDoc(doc(db, `businesses/${BUSINESS_ID}`), {
      name: "Granja Familiar",
      currency: "COP",
      timezone: "America/Bogota",
      startDate: "2026-11-01",
      active: true,
    });
    await setDoc(doc(db, `businesses/${BUSINESS_ID}/members/${OPERATOR_UID}`), {
      name: "Madre",
      email: "operador@example.com",
      role: "operator",
      active: true,
      createdAt: Timestamp.now(),
    });
  });
}

function makePaymentData(saleId: string): Record<string, unknown> {
  return {
    fechaOperativa: "2026-11-18",
    createdAt: Timestamp.now(),
    syncedAt: serverTimestamp(),
    createdBy: OPERATOR_UID,
    saleId,
    amount: 10000,
    methodId: "cash",
    type: "payment",
  };
}

function makeMovementBase(): Record<string, unknown> {
  return {
    fechaOperativa: "2026-11-18",
    createdAt: Timestamp.now(),
    syncedAt: serverTimestamp(),
    createdBy: OPERATOR_UID,
  };
}

async function measureVariant(spec: VariantSpec) {
  let env: RulesTestEnvironment | undefined;
  try {
    env = await initializeTestEnvironment({
      projectId: PROJECT_ID,
      firestore: {
        rules: spec.rules,
        host: "127.0.0.1",
        port: 8080,
      },
    });
    await env.clearFirestore();
    await seedBase(env);

    // 1. Verificar denegaciones equivalentes a 1c-write
    if (spec.useClaims) {
      const noClaimDb = env.authenticatedContext(STRANGER_UID).firestore();
      await assertFails(
        setDoc(doc(noClaimDb, `businesses/${BUSINESS_ID}/payments/deny_noclaim`), {
          ...makePaymentData("sale_1"),
          createdBy: STRANGER_UID,
        }),
      );
      const wrongRoleDb = env.authenticatedContext(STRANGER_UID, { role: "viewer" }).firestore();
      await assertFails(
        setDoc(doc(wrongRoleDb, `businesses/${BUSINESS_ID}/payments/deny_wrongrole`), {
          ...makePaymentData("sale_1"),
          createdBy: STRANGER_UID,
        }),
      );
    } else {
      const strangerDb = env.authenticatedContext(STRANGER_UID).firestore();
      await assertFails(
        setDoc(doc(strangerDb, `businesses/${BUSINESS_ID}/payments/deny_nomember`), {
          ...makePaymentData("sale_1"),
          createdBy: STRANGER_UID,
        }),
      );
    }

    const opDb = spec.useClaims
      ? env.authenticatedContext(OPERATOR_UID, { role: "operator" }).firestore()
      : env.authenticatedContext(OPERATOR_UID).firestore();

    // 2. Barrido de WriteBatch de N pagos (1..30)
    let maxPassed = 0;
    let firstFailed: number | null = null;
    let failReason = "";

    for (let n = 1; n <= MAX_BATCH_TEST; n++) {
      const batch = writeBatch(opDb);
      for (let i = 1; i <= n; i++) {
        const ref = doc(opDb, `businesses/${BUSINESS_ID}/payments/batch_${n}_pay_${i}`);
        batch.set(ref, makePaymentData(`sale_${i}`));
      }
      try {
        await batch.commit();
        maxPassed = n;
      } catch (err: unknown) {
        const rawMsg = err instanceof Error ? err.message : String(err);
        if (n === 1) {
          throw new Error(`Variante ${spec.id} rota: no es un límite (falló N=1): ${rawMsg.slice(0, 300)}`);
        }
        firstFailed = n;
        failReason = rawMsg.slice(0, 300);
        break;
      }
    }

    // 3. Medición devolución con refund (1 saleReturns + 1 payment refund)
    const refundBatch = writeBatch(opDb);
    const srRef = doc(opDb, `businesses/${BUSINESS_ID}/saleReturns/sr_meas_1`);
    refundBatch.set(srRef, { ...makeMovementBase(), saleId: "sale_1" });
    const refundPayRef = doc(opDb, `businesses/${BUSINESS_ID}/payments/pay_refund_meas_1`);
    refundBatch.set(refundPayRef, {
      ...makeMovementBase(),
      saleId: "sale_1",
      saleReturnId: "sr_meas_1",
      amount: 5000,
      methodId: "cash",
      type: "refund",
    });
    await assertSucceeds(refundBatch.commit());

    // 4. Medición cierre (3 counts + 1 closings)
    const closingBatch = writeBatch(opDb);
    for (const kind of ["eggs", "feed", "cash"]) {
      closingBatch.set(
        doc(opDb, `businesses/${BUSINESS_ID}/counts/count_${kind}_1`),
        { ...makeMovementBase(), kind, items: [] },
      );
    }
    closingBatch.set(
      doc(opDb, `businesses/${BUSINESS_ID}/closings/closing_1`),
      { ...makeMovementBase() },
    );
    await assertSucceeds(closingBatch.commit());

    return {
      id: spec.id,
      label: spec.label,
      theoreticalCallsPerPayment: spec.theoreticalCallsPerPayment,
      theoreticalMaxPayments: spec.theoreticalMaxPayments,
      theoreticalRefundCalls: spec.theoreticalRefundCalls,
      theoreticalClosingCalls: spec.theoreticalClosingCalls,
      emulatorMaxPassed: maxPassed,
      emulatorFirstFailed: firstFailed,
      failReason,
      refundBatch: `no falla en el emulador; el recuento de llamadas es aritmético (${spec.id}=${spec.theoreticalRefundCalls} en el peor caso)`,
      closingBatch: `no falla en el emulador; el recuento de llamadas es aritmético (${spec.id}=${spec.theoreticalClosingCalls} en el peor caso)`,
      denialCheck: "denegado",
    };
  } finally {
    if (env) {
      await env.cleanup();
    }
  }
}

async function main(): Promise<void> {
  const variants = buildRuleVariants();
  const results = [];
  for (const v of variants) {
    results.push(await measureVariant(v));
  }

  console.log("\n=== RESULTADOS MEDICION TAREA 4 ===");
  for (const r of results) {
    const emuStr =
      r.emulatorFirstFailed === null
        ? `>=${r.emulatorMaxPassed} (no falló hasta ${MAX_BATCH_TEST})`
        : `${r.emulatorMaxPassed} (falló en ${r.emulatorFirstFailed}: ${r.failReason})`;
    console.log(
      `[${r.id}] ${r.label}\n` +
        `  Teórico (doc): ${r.theoreticalCallsPerPayment} llamadas/pago, máx ${r.theoreticalMaxPayments} pagos/lote\n` +
        `  Emulador: ${emuStr}\n` +
        `  1c-write equiv: ${r.denialCheck}\n` +
        `  refundBatch: ${r.refundBatch}\n` +
        `  closingBatch: ${r.closingBatch}`,
    );
  }
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
