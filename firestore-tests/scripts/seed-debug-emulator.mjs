import { initializeTestEnvironment } from "@firebase/rules-unit-testing";
import { Timestamp, doc, setDoc } from "firebase/firestore";

const PROJECT_ID = "demo-egglytics-test";
const BUSINESS_ID = "egglytics_default";
const AUTH_EMULATOR = "http://127.0.0.1:9099";

async function createAuthUser(email, password, displayName) {
  const res = await fetch(
    `${AUTH_EMULATOR}/identitytoolkit.googleapis.com/v1/accounts:signUp?key=demo-api-key`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ email, password, displayName, returnSecureToken: true }),
    },
  );
  const body = await res.json();
  if (!res.ok) {
    throw new Error(`No se pudo crear usuario ${email}: ${JSON.stringify(body)}`);
  }
  return body.localId;
}

async function main() {
  const operatorUid = await createAuthUser("operador@example.com", "egglytics123", "Madre (Operador)");
  const strangerUid = await createAuthUser("sinmembresia@example.com", "egglytics123", "Sin Membresia");

  const env = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: { host: "127.0.0.1", port: 8080 },
  });

  try {
    await env.withSecurityRulesDisabled(async (context) => {
      const db = context.firestore();
      await setDoc(doc(db, `businesses/${BUSINESS_ID}`), {
        name: "Granja Familiar",
        currency: "COP",
        timezone: "America/Bogota",
        startDate: "2026-11-01",
        active: true,
      });
      await setDoc(doc(db, `businesses/${BUSINESS_ID}/members/${operatorUid}`), {
        name: "Madre",
        email: "operador@example.com",
        role: "operator",
        active: true,
        createdAt: Timestamp.now(),
      });
      await setDoc(doc(db, `businesses/${BUSINESS_ID}/prices/price_aa_1`), {
        classification: "AA",
        pricePerBucket: 18500,
        validFrom: "2026-11-01",
        createdAt: Timestamp.now(),
        syncedAt: Timestamp.now(),
        createdBy: operatorUid,
        note: "Precio inicial de prueba",
      });
      await setDoc(doc(db, `businesses/${BUSINESS_ID}/paymentMethods/cash`), {
        name: "Efectivo",
        active: true,
      });
      await setDoc(doc(db, `businesses/${BUSINESS_ID}/paymentMethods/nequi`), {
        name: "Nequi",
        active: true,
      });
      await setDoc(doc(db, `businesses/${BUSINESS_ID}/expenseCategories/fuel`), {
        name: "Combustible",
        active: true,
      });
      await setDoc(doc(db, `businesses/${BUSINESS_ID}/expenseCategories/packaging`), {
        name: "Empaques",
        active: true,
      });
      await setDoc(doc(db, `businesses/${BUSINESS_ID}/expenseCategories/medicine`), {
        name: "Medicamentos",
        active: true,
      });
      await setDoc(doc(db, `businesses/${BUSINESS_ID}/expenseCategories/maintenance`), {
        name: "Mantenimiento",
        active: true,
      });
      await setDoc(doc(db, `businesses/${BUSINESS_ID}/expenseCategories/other`), {
        name: "Otros",
        active: true,
      });
    });
  } finally {
    await env.cleanup();
  }

  console.log("Datos locales sembrados en Auth + Firestore Emulator:");
  console.log(`  Negocio: businesses/${BUSINESS_ID}`);
  console.log(`  Operador activo: operador@example.com / egglytics123 (uid=${operatorUid})`);
  console.log(`  Sin membresía:   sinmembresia@example.com / egglytics123 (uid=${strangerUid})`);
  console.log("  Precio vigente:  AA = 18500 COP (validFrom 2026-11-01)");
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
