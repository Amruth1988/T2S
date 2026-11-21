import { getFirestore, FieldValue } from "firebase-admin/firestore";
import { initializeApp } from "firebase-admin/app";
import { HttpsError, onCall } from "firebase-functions/v2/https";
import { defineSecret, defineString } from "firebase-functions/params";
import { constants, privateDecrypt } from "node:crypto";

initializeApp();
const db = getFirestore();
const REGION = "asia-south1";
const PRIVATE_KEY = defineSecret("T2S_PROVIDER_PRIVATE_KEY");
const PUBLIC_KEY = defineString("T2S_PROVIDER_PUBLIC_KEY");

const PROVIDERS = {
  sarvam: { name: "Sarvam AI", keyPrefix: "" },
  vakyam: { name: "Vakyam", keyPrefix: "vak_live_" },
  india: { name: "India TTS", keyPrefix: "india_live_" },
} as const;
type Provider = keyof typeof PROVIDERS;
const providerIds = Object.keys(PROVIDERS) as Provider[];
const providerRef = (id: Provider) => db.collection("provider_keys").doc(id);

function requireAdmin(request: { auth?: { uid: string; token: Record<string, unknown> } | null }): string {
  if (!request.auth) throw new HttpsError("unauthenticated", "Sign in is required.");
  if (request.auth.token.admin !== true) {
    throw new HttpsError("permission-denied", "Only T2S administrators can manage provider keys.");
  }
  return request.auth.uid;
}

function parseProvider(value: unknown): Provider {
  if (typeof value !== "string" || !providerIds.includes(value as Provider)) {
    throw new HttpsError("invalid-argument", "Unsupported provider.");
  }
  return value as Provider;
}

/** Decrypt only inside trusted Functions runtime. Private PEM is a Secret Manager secret. */
export async function decryptProviderKey(provider: Provider): Promise<string> {
  const snapshot = await providerRef(provider).get();
  if (!snapshot.exists || snapshot.get("isActive") !== true) {
    throw new HttpsError("failed-precondition", `${PROVIDERS[provider].name} is not configured or is disabled.`);
  }
  const ciphertext = snapshot.get("encryptedKey");
  if (typeof ciphertext !== "string" || !ciphertext) {
    throw new HttpsError("failed-precondition", `${PROVIDERS[provider].name} key is missing.`);
  }
  const plaintext = privateDecrypt({
    key: PRIVATE_KEY.value(),
    padding: constants.RSA_PKCS1_OAEP_PADDING,
    oaepHash: "sha256",
  }, Buffer.from(ciphertext, "base64"));
  return plaintext.toString("utf8");
}

/** Public key can be distributed safely; it cannot decrypt stored credentials. */
export const getProviderEncryptionPublicKey = onCall({ region: REGION, enforceAppCheck: true }, async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Sign in is required.");
  const publicKey = PUBLIC_KEY.value().replace(/\\n/g, "\n");
  if (!publicKey.includes("BEGIN PUBLIC KEY")) {
    throw new HttpsError("failed-precondition", "Provider encryption public key is not configured.");
  }
  return { publicKey, algorithm: "RSA-OAEP-SHA256" };
});

export const saveCentralProviderKey = onCall({ region: REGION, enforceAppCheck: true }, async (request) => {
  const uid = requireAdmin(request);
  const provider = parseProvider(request.data?.provider);
  const ciphertext = request.data?.encryptedKey;
  if (typeof ciphertext !== "string" || ciphertext.length < 100 || ciphertext.length > 8192) {
    throw new HttpsError("invalid-argument", "Submit a valid app-encrypted provider key.");
  }
  // Validate ciphertext before persisting; decryption is deliberately backend-only.
  try {
    const decoded = Buffer.from(ciphertext, "base64");
    if (decoded.length !== 256) throw new Error("Invalid RSA ciphertext length.");
    privateDecrypt({ key: PRIVATE_KEY.value(), padding: constants.RSA_PKCS1_OAEP_PADDING, oaepHash: "sha256" }, decoded);
  } catch {
    throw new HttpsError("invalid-argument", "Provider key encryption could not be verified. Refresh and try again.");
  }

  const ref = providerRef(provider);
  const existing = await ref.get();
  const now = FieldValue.serverTimestamp();
  await ref.set({
    providerId: provider,
    providerName: PROVIDERS[provider].name,
    encryptedKey: ciphertext,
    encryption: "RSA_OAEP_SHA256",
    isActive: true,
    addedBy: existing.exists ? existing.get("addedBy") : uid,
    updatedBy: uid,
    createdAt: existing.exists ? existing.get("createdAt") : now,
    updatedAt: now,
    lastRotatedAt: now,
  }, { merge: true });

  return { saved: true, provider, providerName: PROVIDERS[provider].name };
});

export const getCentralProviderStatus = onCall({ region: REGION, enforceAppCheck: true }, async (request) => {
  requireAdmin(request);
  const snapshots = await Promise.all(providerIds.map((id) => providerRef(id).get()));
  const providers: Record<string, unknown> = {};
  snapshots.forEach((snapshot, index) => {
    const id = providerIds[index];
    const data = snapshot.data();
    providers[id] = {
      providerId: id,
      providerName: PROVIDERS[id].name,
      configured: snapshot.exists && typeof data?.encryptedKey === "string",
      isActive: data?.isActive === true,
      createdAt: data?.createdAt?.toMillis?.() ?? null,
      updatedAt: data?.updatedAt?.toMillis?.() ?? null,
      lastRotatedAt: data?.lastRotatedAt?.toMillis?.() ?? null,
    };
  });
  return { providers };
});

export const setCentralProviderActive = onCall({ region: REGION, enforceAppCheck: true }, async (request) => {
  const uid = requireAdmin(request);
  const provider = parseProvider(request.data?.provider);
  const active = request.data?.isActive;
  if (typeof active !== "boolean") throw new HttpsError("invalid-argument", "isActive must be true or false.");
  const ref = providerRef(provider);
  const snapshot = await ref.get();
  if (!snapshot.exists) throw new HttpsError("not-found", "This provider has no saved key.");
  await ref.update({ isActive: active, updatedAt: FieldValue.serverTimestamp(), updatedBy: uid });
  return { updated: true, provider, isActive: active };
});
