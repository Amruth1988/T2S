import { getFirestore, FieldValue } from "firebase-admin/firestore";
import { initializeApp } from "firebase-admin/app";
import { getAuth } from "firebase-admin/auth";
import { HttpsError, onCall } from "firebase-functions/v2/https";
import { defineSecret, defineString } from "firebase-functions/params";
import { createHash, constants, privateDecrypt } from "node:crypto";

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
const USER_GENERATION_UNAVAILABLE = "Generation temporarily unavailable. Please try again later.";

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

export const saveCentralProviderKey = onCall({ region: REGION, enforceAppCheck: true, secrets: [PRIVATE_KEY] }, async (request) => {
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

/**
 * Fail-closed provider budget guard. Limits are configured by an authenticated T2S
 * admin in provider_limits/{provider}. The backend cannot reliably read remaining
 * provider wallet balances, so these are conservative app-side ceilings, not live
 * balance synchronization. Reservations are counted before calling the provider.
 */
export const setProviderUsageLimits = onCall({ region: REGION, enforceAppCheck: true }, async (request) => {
  const uid = requireAdmin(request);
  const provider = parseProvider(request.data?.provider);
  const enabled = request.data?.enabled;
  const dailyCharacters = request.data?.dailyCharacters;
  const monthlyCharacters = request.data?.monthlyCharacters;
  const dailyRequests = request.data?.dailyRequests;
  if (typeof enabled !== "boolean" ||
      !Number.isSafeInteger(dailyCharacters) || dailyCharacters < 0 ||
      !Number.isSafeInteger(monthlyCharacters) || monthlyCharacters < 0 ||
      !Number.isSafeInteger(dailyRequests) || dailyRequests < 0) {
    throw new HttpsError("invalid-argument", "Provide enabled and non-negative integer dailyCharacters, monthlyCharacters and dailyRequests.");
  }
  if (enabled && (dailyCharacters === 0 || monthlyCharacters === 0 || dailyRequests === 0)) {
    throw new HttpsError("invalid-argument", "All three limits must be greater than zero before enabling generation.");
  }
  await db.collection("provider_limits").doc(provider).set({
    provider, enabled, dailyCharacters, monthlyCharacters, dailyRequests,
    updatedBy: uid, updatedAt: FieldValue.serverTimestamp(),
  }, { merge: true });
  return { saved: true, provider, enabled };
});

async function reserveProviderUsage(provider: Provider, uid: string, characters: number): Promise<void> {
  const day = new Date().toISOString().slice(0, 10);
  const month = day.slice(0, 7);
  const uidHash = createHash("sha256").update(uid).digest("hex");
  const limitsRef = db.collection("provider_limits").doc(provider);
  const globalDayRef = db.collection("provider_usage").doc(`${provider}_global_${day}`);
  const globalMonthRef = db.collection("provider_usage").doc(`${provider}_global_${month}`);
  const userDayRef = db.collection("provider_usage").doc(`${provider}_user_${uidHash}_${day}`);
  const userMonthRef = db.collection("provider_usage").doc(`${provider}_user_${uidHash}_${month}`);

  // Per-account ceilings are deliberately conservative and independent of the
  // provider-wide budget. The global limits remain the final shared-wallet guard.
  const USER_DAILY_CHARACTERS = 10000;
  const USER_MONTHLY_CHARACTERS = 100000;
  const USER_DAILY_REQUESTS = 30;

  await db.runTransaction(async (tx) => {
    const [limitsSnap, globalDaySnap, globalMonthSnap, userDaySnap, userMonthSnap] = await Promise.all([
      tx.get(limitsRef), tx.get(globalDayRef), tx.get(globalMonthRef),
      tx.get(userDayRef), tx.get(userMonthRef),
    ]);
    const limits = limitsSnap.data();
    if (!limitsSnap.exists || limits?.enabled !== true) {
      throw new HttpsError("resource-exhausted", USER_GENERATION_UNAVAILABLE);
    }
    const dailyCharacters = Number(limits.dailyCharacters);
    const monthlyCharacters = Number(limits.monthlyCharacters);
    const dailyRequests = Number(limits.dailyRequests);
    if (![dailyCharacters, monthlyCharacters, dailyRequests].every(Number.isSafeInteger) ||
        dailyCharacters <= 0 || monthlyCharacters <= 0 || dailyRequests <= 0) {
      throw new HttpsError("resource-exhausted", USER_GENERATION_UNAVAILABLE);
    }

    const globalUsedToday = Number(globalDaySnap.get("characters") ?? 0);
    const globalRequestsToday = Number(globalDaySnap.get("requests") ?? 0);
    const globalUsedMonth = Number(globalMonthSnap.get("characters") ?? 0);
    const userUsedToday = Number(userDaySnap.get("characters") ?? 0);
    const userRequestsToday = Number(userDaySnap.get("requests") ?? 0);
    const userUsedMonth = Number(userMonthSnap.get("characters") ?? 0);

    if (globalUsedToday + characters > dailyCharacters ||
        globalRequestsToday + 1 > dailyRequests ||
        globalUsedMonth + characters > monthlyCharacters ||
        userUsedToday + characters > USER_DAILY_CHARACTERS ||
        userRequestsToday + 1 > USER_DAILY_REQUESTS ||
        userUsedMonth + characters > USER_MONTHLY_CHARACTERS) {
      throw new HttpsError("resource-exhausted", USER_GENERATION_UNAVAILABLE);
    }

    const now = FieldValue.serverTimestamp();
    tx.set(globalDayRef, {
      provider, day, characters: globalUsedToday + characters,
      requests: globalRequestsToday + 1, updatedAt: now,
    }, { merge: true });
    tx.set(globalMonthRef, {
      provider, month, characters: globalUsedMonth + characters,
      requests: FieldValue.increment(1), updatedAt: now,
    }, { merge: true });
    tx.set(userDayRef, {
      provider, day, uidHash, characters: userUsedToday + characters,
      requests: userRequestsToday + 1, updatedAt: now,
    }, { merge: true });
    tx.set(userMonthRef, {
      provider, month, uidHash, characters: userUsedMonth + characters,
      requests: FieldValue.increment(1), updatedAt: now,
    }, { merge: true });
  });
}

/** Per-user UTC quota for shared, billable Sarvam voice-clone requests. */
async function reserveSarvamUsage(uid: string, characters: number): Promise<void> {
  const day = new Date().toISOString().slice(0, 10);
  const uidHash = createHash("sha256").update(uid).digest("hex");
  const ref = db.collection("provider_usage").doc(`sarvam_${uidHash}_${day}`);
  await db.runTransaction(async (transaction) => {
    const snapshot = await transaction.get(ref);
    const calls = Number(snapshot.get("calls") ?? 0);
    const usedCharacters = Number(snapshot.get("characters") ?? 0);
    if (calls >= 10 || usedCharacters + characters > 10000) {
      throw new HttpsError("resource-exhausted", USER_GENERATION_UNAVAILABLE);
    }
    transaction.set(ref, {
      provider: "sarvam",
      uidHash,
      day,
      calls: calls + 1,
      characters: usedCharacters + characters,
      updatedAt: FieldValue.serverTimestamp(),
    }, { merge: true });
  });
}

/** Registers a consented voice sample using the central Sarvam credential. */
export const createCentralSarvamVoice = onCall({
  region: REGION, enforceAppCheck: true, secrets: [PRIVATE_KEY], timeoutSeconds: 120,
}, async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Sign in is required.");
  const name = request.data?.name;
  const language = request.data?.language;
  const gender = request.data?.gender;
  const sampleBase64 = request.data?.sampleBase64;
  const mimeType = request.data?.mimeType;
  if (typeof name !== "string" || name.trim().length < 2 || name.length > 100) {
    throw new HttpsError("invalid-argument", "Voice name must be 2–100 characters.");
  }
  if (!["te-IN", "en-IN", "hi-IN"].includes(language)) {
    throw new HttpsError("invalid-argument", "Unsupported voice sample language.");
  }
  if (!["Male", "Female", "Other"].includes(gender)) {
    throw new HttpsError("invalid-argument", "Select a valid voice gender.");
  }
  if (typeof sampleBase64 !== "string" || sampleBase64.length > 8_000_000 ||
      !/^[A-Za-z0-9+/]*={0,2}$/.test(sampleBase64)) {
    throw new HttpsError("invalid-argument", "Audio sample is missing or exceeds the 6 MB limit.");
  }
  const audio = Buffer.from(sampleBase64, "base64");
  if (audio.length < 1024 || audio.length > 6_000_000) {
    throw new HttpsError("invalid-argument", "Audio sample must be between 1 KB and 6 MB.");
  }
  const allowedMime = ["audio/wav", "audio/mpeg", "audio/flac", "audio/mp4", "audio/aac"];
  if (typeof mimeType !== "string" || !allowedMime.includes(mimeType)) {
    throw new HttpsError("invalid-argument", "Unsupported audio format.");
  }
  // Voice registration is a billable Sarvam operation too: reserve both the
  // provider-wide budget and the account daily call allowance before contacting Sarvam.
  await reserveProviderUsage("sarvam", request.auth.uid, 0);
  await reserveSarvamUsage(request.auth.uid, 0);
  const apiKey = await decryptProviderKey("sarvam");
  const form = new FormData();
  form.append("name", name.trim());
  form.append("language", language);
  if (gender !== "Other") form.append("gender", gender.toLowerCase());
  form.append("remove_background_noise", "true");
  form.append("file", new Blob([audio], { type: mimeType }), `sample.${mimeType === "audio/wav" ? "wav" : mimeType === "audio/mpeg" ? "mp3" : mimeType === "audio/flac" ? "flac" : mimeType === "audio/mp4" ? "m4a" : "aac"}`);
  const response = await fetch("https://api.sarvam.ai/voices/create", {
    method: "POST",
    headers: { "api-subscription-key": apiKey, "Accept": "application/json" },
    body: form,
    signal: AbortSignal.timeout(110000),
  });
  const body = await response.text();
  if (!response.ok) throw new HttpsError(response.status === 429 ? "resource-exhausted" : "unavailable", `Sarvam voice registration failed (HTTP ${response.status}).`);
  let payload: Record<string, unknown>;
  try { payload = JSON.parse(body) as Record<string, unknown>; }
  catch { throw new HttpsError("unavailable", "Sarvam returned an invalid voice response."); }
  const data = payload.data as Record<string, unknown> | undefined;
  const voiceId = typeof data?.voice_id === "string" ? data.voice_id : "";
  if (!/^svc-[A-Za-z0-9_-]{3,100}$/.test(voiceId)) {
    throw new HttpsError("unavailable", "Sarvam did not return a valid saved voice ID.");
  }
  return { voiceId };
});

/** Lists workspace voices without ever returning the provider credential. */
export const listCentralSarvamVoices = onCall({
  region: REGION, enforceAppCheck: true, secrets: [PRIVATE_KEY],
}, async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Sign in is required.");
  const apiKey = await decryptProviderKey("sarvam");
  const response = await fetch("https://api.sarvam.ai/voices", {
    method: "GET",
    headers: { "api-subscription-key": apiKey, "Accept": "application/json" },
    signal: AbortSignal.timeout(20000),
  });
  const body = await response.text();
  if (!response.ok) {
    throw new HttpsError(response.status === 429 ? "resource-exhausted" : "unavailable",
      `Sarvam voice list failed (HTTP ${response.status}).`);
  }
  try {
    return { voices: JSON.parse(body) };
  } catch {
    throw new HttpsError("unavailable", "Sarvam returned an invalid voice-list response.");
  }
});

/** Generates speech with the centrally stored Sarvam key; enforces a daily user quota. */
export const generateCentralSarvamClone = onCall({
  region: REGION, enforceAppCheck: true, secrets: [PRIVATE_KEY], timeoutSeconds: 120,
}, async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Sign in is required.");
  const voiceId = request.data?.voiceId;
  const text = request.data?.text;
  const language = request.data?.language;
  if (typeof voiceId !== "string" || !/^svc-[A-Za-z0-9_-]{3,100}$/.test(voiceId)) {
    throw new HttpsError("invalid-argument", "A valid saved Sarvam voice ID is required.");
  }
  if (typeof text !== "string" || !text.trim() || text.length > 1000) {
    throw new HttpsError("invalid-argument", "Text must contain 1–1,000 characters.");
  }
  if (language !== "te-IN" && language !== "en-IN") {
    throw new HttpsError("invalid-argument", "Choose Telugu or English.");
  }

  await reserveProviderUsage("sarvam", request.auth.uid, text.length);
  await reserveSarvamUsage(request.auth.uid, text.length);
  const apiKey = await decryptProviderKey("sarvam");
  const form = new FormData();
  form.append("voice_id", voiceId);
  form.append("text", text);
  form.append("language_code", language);
  const response = await fetch("https://api.sarvam.ai/voices/clone", {
    method: "POST",
    headers: { "api-subscription-key": apiKey, "Accept": "application/json" },
    body: form,
    signal: AbortSignal.timeout(110000),
  });
  const body = await response.text();
  if (!response.ok) {
    throw new HttpsError(response.status === 429 || response.status === 402 ? "resource-exhausted" : "unavailable",
      response.status === 429 || response.status === 402 ? USER_GENERATION_UNAVAILABLE : "Speech generation failed. Please try again later.");
  }
  let payload: Record<string, unknown>;
  try {
    payload = JSON.parse(body) as Record<string, unknown>;
  } catch {
    throw new HttpsError("unavailable", "Sarvam returned an invalid audio response.");
  }
  const audioBase64 = typeof payload.audio_b64 === "string" ? payload.audio_b64 :
    typeof payload.audio === "string" ? payload.audio : "";
  if (!audioBase64 || audioBase64.length > 20_000_000 || !/^[A-Za-z0-9+/]*={0,2}$/.test(audioBase64)) {
    throw new HttpsError("unavailable", "Sarvam returned missing or invalid audio data.");
  }
  return { audioBase64, contentType: "audio/wav" };
});



/** Central India TTS voice catalogue; provider credentials never leave Functions. */
export const listCentralIndiaTtsVoices = onCall({
  region: REGION, enforceAppCheck: true, secrets: [PRIVATE_KEY], timeoutSeconds: 30,
}, async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Sign in is required.");
  const language = request.data?.language;
  if (!["en", "te", "hi"].includes(language)) {
    throw new HttpsError("invalid-argument", "Choose English, Telugu or Hindi.");
  }
  const apiKey = await decryptProviderKey("india");
  const response = await fetch("https://api.dharun.dev/v1/tts/voices", {
    headers: { "Accept": "application/json", "Authorization": `Bearer ${apiKey}` },
    signal: AbortSignal.timeout(20000),
  });
  const body = await response.text();
  if (!response.ok) {
    throw new HttpsError(response.status === 429 ? "resource-exhausted" : "unavailable",
      `India TTS voice catalogue failed (HTTP ${response.status}).`);
  }
  let payload: Record<string, unknown>;
  try { payload = JSON.parse(body) as Record<string, unknown>; }
  catch { throw new HttpsError("unavailable", "India TTS returned an invalid voice catalogue."); }
  const voices = Array.isArray(payload.data) ? payload.data.filter((item) => {
    if (!item || typeof item !== "object") return false;
    const voice = item as Record<string, unknown>;
    return typeof voice.language === "string" &&
      voice.language.toLowerCase().startsWith(`${language}-`) &&
      voice.non_commercial !== true;
  }) : [];
  return { voices };
});

/** Central Vakyam voice catalogue. */
export const listCentralVakyamVoices = onCall({
  region: REGION, enforceAppCheck: true, secrets: [PRIVATE_KEY], timeoutSeconds: 30,
}, async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Sign in is required.");
  const language = request.data?.language;
  if (!["en", "te", "hi"].includes(language)) {
    throw new HttpsError("invalid-argument", "Choose English, Telugu or Hindi.");
  }
  const apiKey = await decryptProviderKey("vakyam");
  const response = await fetch("https://api.vakyam.ai/v1/voices?group_by=language", {
    headers: { "Accept": "application/json", "Authorization": `Bearer ${apiKey}` },
    signal: AbortSignal.timeout(20000),
  });
  const body = await response.text();
  if (!response.ok) {
    throw new HttpsError(response.status === 429 ? "resource-exhausted" : "unavailable",
      `Vakyam voice catalogue failed (HTTP ${response.status}).`);
  }
  let payload: Record<string, unknown>;
  try { payload = JSON.parse(body) as Record<string, unknown>; }
  catch { throw new HttpsError("unavailable", "Vakyam returned an invalid voice catalogue."); }
  const locale = language === "te" ? "te-IN" : language === "hi" ? "hi-IN" : "en-IN";
  const data = payload.data && typeof payload.data === "object" ? payload.data as Record<string, unknown> : payload;
  const voices = Array.isArray(data[locale]) ? data[locale] : [];
  return { voices };
});

function validateOnlineTtsInput(data: Record<string, unknown>, maxCharacters: number) {
  const text = data.text;
  const voice = data.voice;
  const language = data.language;
  const speed = data.speed;
  const format = data.format;
  if (typeof text !== "string" || !text.trim() || text.length > maxCharacters) {
    throw new HttpsError("invalid-argument", `Text must contain 1–${maxCharacters} characters.`);
  }
  if (typeof voice !== "string" || !voice.trim() || voice.length > 160) {
    throw new HttpsError("invalid-argument", "Choose a valid voice.");
  }
  if (typeof language !== "string" || !["en", "te", "hi"].includes(language)) {
    throw new HttpsError("invalid-argument", "Choose English, Telugu or Hindi.");
  }
  const numericSpeed = typeof speed === "number" && Number.isFinite(speed) ? speed : 1;
  if (numericSpeed < 0.5 || numericSpeed > 2) {
    throw new HttpsError("invalid-argument", "Speech speed must be between 0.5 and 2.");
  }
  const safeFormat = typeof format === "string" && ["mp3", "wav", "ogg"].includes(format.toLowerCase())
    ? format.toLowerCase() : "mp3";
  return { text, voice, language, speed: numericSpeed, format: safeFormat };
}

/** Generate India TTS audio through the centrally stored India API key. */
export const generateCentralIndiaTts = onCall({
  region: REGION, enforceAppCheck: true, secrets: [PRIVATE_KEY], timeoutSeconds: 120,
}, async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Sign in is required.");
  const input = validateOnlineTtsInput(request.data ?? {}, 3000);
  await reserveProviderUsage("india", request.auth.uid, input.text.length);
  const apiKey = await decryptProviderKey("india");
  const response = await fetch("https://api.dharun.dev/v1/tts", {
    method: "POST",
    headers: {
      "Authorization": `Bearer ${apiKey}`,
      "Content-Type": "application/json",
      "Accept": input.format === "mp3" ? "audio/mpeg" : input.format === "wav" ? "audio/wav" : "audio/ogg",
    },
    body: JSON.stringify({ text: input.text, voice: input.voice, speed: input.speed, format: input.format }),
    signal: AbortSignal.timeout(110000),
  });
  if (!response.ok) {
    const detail = await response.text();
    throw new HttpsError(response.status === 429 || response.status === 402 ? "resource-exhausted" : "unavailable",
      response.status === 429 || response.status === 402 ? USER_GENERATION_UNAVAILABLE : "Speech generation failed. Please try again later.");
  }
  const bytes = Buffer.from(await response.arrayBuffer());
  if (bytes.length < 1024 || bytes.length > 7_000_000) {
    throw new HttpsError("unavailable", "India TTS returned empty or oversized audio.");
  }
  return { audioBase64: bytes.toString("base64"), contentType: response.headers.get("content-type") ?? "audio/mpeg" };
});

/** Generate Vakyam Raaga speech through the centrally stored Vakyam key. */
export const generateCentralVakyamTts = onCall({
  region: REGION, enforceAppCheck: true, secrets: [PRIVATE_KEY], timeoutSeconds: 120,
}, async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Sign in is required.");
  const input = validateOnlineTtsInput(request.data ?? {}, 3000);
  await reserveProviderUsage("vakyam", request.auth.uid, input.text.length);
  const apiKey = await decryptProviderKey("vakyam");
  const locale = input.language === "te" ? "te-IN" : input.language === "hi" ? "hi-IN" : "en-IN";
  const response = await fetch("https://api.vakyam.ai/v1/tts/generate", {
    method: "POST",
    headers: {
      "Authorization": `Bearer ${apiKey}`,
      "Content-Type": "application/json",
      "Accept": "application/json",
    },
    body: JSON.stringify({
      text: input.text, model_id: "raaga-v1", voice: input.voice,
      language: locale, output_format: input.format === "wav" ? "wav" : "mp3", speed: input.speed,
    }),
    signal: AbortSignal.timeout(110000),
  });
  const body = await response.text();
  if (!response.ok) {
    throw new HttpsError(response.status === 429 || response.status === 402 ? "resource-exhausted" : "unavailable",
      response.status === 429 || response.status === 402 ? USER_GENERATION_UNAVAILABLE : "Speech generation failed. Please try again later.");
  }
  let payload: Record<string, unknown>;
  try { payload = JSON.parse(body) as Record<string, unknown>; }
  catch { throw new HttpsError("unavailable", "Vakyam returned an invalid audio response."); }
  const data = payload.data && typeof payload.data === "object" ? payload.data as Record<string, unknown> : {};
  const audioBase64 = typeof payload.audio === "string" ? payload.audio :
    typeof payload.audio_base64 === "string" ? payload.audio_base64 :
    typeof data.audio === "string" ? data.audio : "";
  if (!audioBase64 || audioBase64.length > 9_000_000 || !/^[A-Za-z0-9+/]*={0,2}$/.test(audioBase64)) {
    throw new HttpsError("unavailable", "Vakyam returned missing or invalid audio data.");
  }
  return { audioBase64, contentType: input.format === "wav" ? "audio/wav" : "audio/mpeg" };
});


/**
 * Permanently deletes the caller's T2S account and all data below users/{uid}.
 * A recent sign-in is required so a stolen long-lived ID token cannot erase an
 * account. Firestore recursiveDelete removes every nested user collection.
 */
export const deleteT2SAccount = onCall({
  region: REGION,
  enforceAppCheck: true,
  timeoutSeconds: 540,
  memory: "512MiB",
}, async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Sign in before deleting your account.");
  const uid = request.auth.uid;
  const authTime = Number(request.auth.token.auth_time ?? 0);
  const nowSeconds = Math.floor(Date.now() / 1000);
  if (!Number.isFinite(authTime) || authTime <= 0 ||
      authTime > nowSeconds + 60 || nowSeconds - authTime > 300) {
    throw new HttpsError(
      "failed-precondition",
      "For your security, sign in again and retry account deletion."
    );
  }

  const uidHash = createHash("sha256").update(uid).digest("hex");
  const userRef = db.collection("users").doc(uid);

  // Remove all UID-owned Firestore data, including nested projects, scripts,
  // voiceProfiles and any future subcollections below the user document.
  await db.recursiveDelete(userRef);

  // Remove hashed quota ledgers without exposing the original Firebase UID.
  const usage = await db.collection("provider_usage").where("uidHash", "==", uidHash).get();
  for (let offset = 0; offset < usage.docs.length; offset += 400) {
    const batch = db.batch();
    usage.docs.slice(offset, offset + 400).forEach((document) => batch.delete(document.ref));
    await batch.commit();
  }

  // Delete the Firebase Authentication identity only after its data cleanup succeeds.
  await getAuth().deleteUser(uid);
  return { deleted: true };
});
