import { after, before, beforeEach, describe, it } from "node:test";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
  type RulesTestEnvironment,
} from "@firebase/rules-unit-testing";
import { doc, getDoc, setDoc } from "firebase/firestore";

const PROJECT_ID = "demo-egglytics-test";
let testEnv: RulesTestEnvironment;

describe("Firestore Emulator smoke test", () => {
  before(async () => {
    const rules = readFileSync(resolve(process.cwd(), "..", "firestore.rules"), "utf8");
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
  });

  after(async () => {
    await testEnv.cleanup();
  });

  it("allows authenticated read on smoke doc and denies unauthenticated read", async () => {
    await testEnv.withSecurityRulesDisabled(async (context) => {
      await setDoc(doc(context.firestore(), "smoke/ping"), { ok: true });
    });

    const authedDb = testEnv.authenticatedContext("user_1").firestore();
    await assertSucceeds(getDoc(doc(authedDb, "smoke/ping")));

    const unauthDb = testEnv.unauthenticatedContext().firestore();
    await assertFails(getDoc(doc(unauthDb, "smoke/ping")));
  });
});
