import "server-only";

import * as admin from "firebase-admin";
import { readFileSync } from "node:fs";

/**
 * Servidor: credenciais via {@link process.env.FIREBASE_SERVICE_ACCOUNT_JSON}
 * (JSON da service account como string, ex. Vercel) ou {@link process.env.GOOGLE_APPLICATION_CREDENTIALS}
 * apontando para ficheiro local em desenvolvimento.
 */
export function getFirestoreAdmin(): admin.firestore.Firestore {
  try {
    if (!admin.apps.length) {
      const raw = process.env.FIREBASE_SERVICE_ACCOUNT_JSON?.trim();
      if (raw) {
        const cred = JSON.parse(raw) as admin.ServiceAccount;
        admin.initializeApp({
          credential: admin.credential.cert(cred),
        });
      } else {
        const fromPath = process.env.FIREBASE_SERVICE_ACCOUNT_JSON_PATH?.trim();
        if (fromPath) {
          const fileJson = readFileSync(fromPath, "utf8");
          const cred = JSON.parse(fileJson) as admin.ServiceAccount;
          admin.initializeApp({
            credential: admin.credential.cert(cred),
          });
        } else {
          admin.initializeApp({
            credential: admin.credential.applicationDefault(),
          });
        }
      }
    }
    return admin.firestore();
  } catch {
    throw new Error("FIREBASE_ADMIN_NOT_CONFIGURED");
  }
}
