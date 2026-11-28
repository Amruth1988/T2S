import { initializeApp, getApps } from "firebase/app";
import { getAuth, GoogleAuthProvider } from "firebase/auth";
import { getFirestore } from "firebase/firestore";
import { initializeAppCheck, ReCaptchaV3Provider, type AppCheck } from "firebase/app-check";

const env = import.meta.env;
const config = {
  apiKey: env.VITE_FIREBASE_API_KEY as string | undefined,
  authDomain: env.VITE_FIREBASE_AUTH_DOMAIN as string | undefined,
  projectId: env.VITE_FIREBASE_PROJECT_ID as string | undefined,
  appId: env.VITE_FIREBASE_APP_ID as string | undefined
};
export const firebaseReady = Boolean(config.apiKey && config.authDomain && config.projectId && config.appId);
const app = firebaseReady ? (getApps()[0] ?? initializeApp(config)) : null;
export const auth = app ? getAuth(app) : null;
export const db = app ? getFirestore(app) : null;
export const googleProvider = new GoogleAuthProvider();
const appCheckSiteKey = env.VITE_FIREBASE_APPCHECK_SITE_KEY as string | undefined;
let appCheckInstance: AppCheck | null = null;
if (app && appCheckSiteKey && typeof window !== "undefined") {
  appCheckInstance = initializeAppCheck(app, {
    provider: new ReCaptchaV3Provider(appCheckSiteKey),
    isTokenAutoRefreshEnabled: true
  });
}
export const appCheck = appCheckInstance;
export const appCheckReady = Boolean(appCheckInstance);
export const ttsWorkerUrl = (env.VITE_T2S_WORKER_URL as string | undefined || "https://t2s.amruth4official.workers.dev").replace(/\/$/, "");
