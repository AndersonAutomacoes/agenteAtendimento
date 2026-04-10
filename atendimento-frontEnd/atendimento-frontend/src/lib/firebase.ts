import { getApps, initializeApp, type FirebaseApp } from "firebase/app";
import { getAuth, type Auth } from "firebase/auth";

const firebaseConfig = {
  apiKey: process.env.NEXT_PUBLIC_FIREBASE_API_KEY ?? "",
  authDomain: process.env.NEXT_PUBLIC_FIREBASE_AUTH_DOMAIN ?? "",
  projectId: process.env.NEXT_PUBLIC_FIREBASE_PROJECT_ID ?? "",
  appId: process.env.NEXT_PUBLIC_FIREBASE_APP_ID ?? "",
};

export function isFirebaseConfigured(): boolean {
  return Boolean(
    firebaseConfig.apiKey && firebaseConfig.authDomain && firebaseConfig.projectId && firebaseConfig.appId,
  );
}

let appSingleton: FirebaseApp | null = null;

export function getFirebaseApp(): FirebaseApp {
  if (!isFirebaseConfigured()) {
    throw new Error("NEXT_PUBLIC_FIREBASE_* não configurado");
  }
  if (appSingleton) return appSingleton;
  if (getApps().length > 0) {
    appSingleton = getApps()[0];
    return appSingleton;
  }
  appSingleton = initializeApp(firebaseConfig);
  return appSingleton;
}

export function getFirebaseAuth(): Auth {
  return getAuth(getFirebaseApp());
}
