import { KeyManagementServiceClient } from "@google-cloud/kms";
import { getFirestore, FieldValue } from "firebase-admin/firestore";
import { initializeApp } from "firebase-admin/app";
import { HttpsError, onCall } from "firebase-functions/v2/https";

initializeApp();
const db = getFirestore();
const kms = new KeyManagementServiceClient();
const REGION = "asia-south1";
const KMS_KEY_NAME = process.env.T2S_KMS_KEY_NAME ?? "";

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

function requireKmsKey(): string {
  if (!KMS_KEY_NAME) {
    throw new HttpsError("failed-precondition", "Server encryption is not configured. Set T2S_KMS_KEY_NAME before deployment.");
  }
  return KMS_KEY_NAME;
}

/** Encrypts with Cloud KMS. Plaintext is never written to Firestore or logs. */
async function encryptProviderKey(apiKey: string): Promise<{ ciphertext: string; keyVersion: string }> {
  const [result] = await kms.encrypt({
    name: requireKmsKey(),
    plaintext: Buffer.from(apiKey, "utf8"),
  });
  if (!result.ciphertext) throw new Error("KMS returned no ciphertext.");
  return {
    ciphertext: Buffer.from(result.ciphertext as Uint8Array).toString("base64"),
    keyVersion: result.name ?? KMS_KEY_NAME,
  };
}

/** Backend-only helper for provider proxy functions. Never expose this via a callable. */
export async function decryptProviderKey(provider: Provider): Promise<string> {
  const snapshot = await providerRef(provider).get();
  if (!snapshot.exists || snapshot.get("isActive") !== true) {
    throw new HttpsError("failed-precondition", `${PROVIDERS[provider].name} is not configured or is disabled.`);
  }
  const ciphertext = snapshot.get("encryptedKey");
  if (typeof ciphertext !== "string" || !ciphertext) {
    throw new HttpsError("failed-precondition", `${PROVIDERS[provider].name} key is missing.`);
  }
  const [result] = await kms.decrypt({
    name: requireKmsKey(),
    ciphertext: Buffer.from(ciphertext, "base64"),
  });
  if (!result.plaintext) throw new Error("KMS returned no plaintext.");
  return Buffer.from(result.plaintext as Uint8Array).toString("utf8");
}

export const saveCentralProviderKey = onCall({ region: REGION, enforceAppCheck: true }, async (request) => {
  const uid = requireAdmin(request);
  const provider = parseProvider(request.data?.provider);
  const apiKey = request.data?.apiKey;
  if (typeof apiKey !== "string" || apiKey.trim().length < 8 || apiKey.length > 4096) {
    throw new HttpsError("invalid-argument", "Enter a valid provider key.");
  }
  const cleanKey = apiKey.trim();
  const prefix = PROVIDERS[provider].keyPrefix;
  if (prefix && !cleanKey.startsWith(prefix)) {
    throw new HttpsError("invalid-argument", `The ${PROVIDERS[provider].name} key format is invalid.`);
  }

  let encrypted: { ciphertext: string; keyVersion: string };
  try {
    encrypted = await encryptProviderKey(cleanKey);
  } catch (error) {
    console.error("Provider key encryption failed", { provider, adminUid: uid });
    if (error instanceof HttpsError) throw error;
    throw new HttpsError("failed-precondition", "Could not encrypt the provider key. Check Cloud KMS configuration and permissions.");
  }

  const ref = providerRef(provider);
  const existing = await ref.get();
  const now = FieldValue.serverTimestamp();
  await ref.set({
    providerId: provider,
    providerName: PROVIDERS[provider].name,
    encryptedKey: encrypted.ciphertext,
    encryption: "CLOUD_KMS",
    kmsKeyVersion: encrypted.keyVersion,
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
