import { createRemoteJWKSet, decodeProtectedHeader, jwtVerify } from "jose";
import { DELETE_USAGE_SQL, RESERVE_USAGE_SQL } from "./usage-sql.js";

interface D1Result {
  success: boolean;
  meta?: { changes?: number };
}
interface D1Statement {
  bind(...values: (string | number)[]): D1Statement;
  run<T = unknown>(): Promise<D1Result & { results?: T[] }>;
}
interface D1Database {
  prepare(query: string): D1Statement;
}
interface Env {
  FIREBASE_PROJECT_ID: string;
  FIREBASE_PROJECT_NUMBER: string;
  FIREBASE_APP_ID: string;
  FIREBASE_APP_IDS?: string;
  SARVAM_GATEWAY_URL?: string;
  VAKYAM_API_KEY: string;
  INDIA_API_KEY?: string;
  USAGE_DB?: D1Database;
}

const firebaseKeys = createRemoteJWKSet(
  new URL("https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com")
);
const appCheckKeys = createRemoteJWKSet(
  new URL("https://firebaseappcheck.googleapis.com/v1/jwks")
);

function json(data: unknown, status = 200): Response {
  return new Response(JSON.stringify(data), {
    status,
    headers: { "content-type": "application/json; charset=utf-8", "cache-control": "no-store" },
  });
}

async function authenticate(request: Request, env: Env): Promise<string> {
  const token = request.headers.get("authorization")?.match(/^Bearer\s+(.+)$/i)?.[1];
  if (!token) throw new Error("Sign in to T2S Studio to use online TTS.");
  let payload: Awaited<ReturnType<typeof jwtVerify>>["payload"];
  try {
    ({ payload } = await jwtVerify(token, firebaseKeys, {
      issuer: `https://securetoken.google.com/${env.FIREBASE_PROJECT_ID}`,
      audience: env.FIREBASE_PROJECT_ID,
      algorithms: ["RS256"],
    }));
  } catch {
    throw new Error("Invalid Firebase user token.");
  }
  if (typeof payload.sub !== "string" || !payload.sub) throw new Error("Invalid Firebase user token.");

  const appCheckToken = request.headers.get("x-firebase-appcheck");
  if (!appCheckToken) throw new Error("A valid Firebase App Check token is required.");
  let appCheckSubject: unknown;
  try {
    const protectedHeader = decodeProtectedHeader(appCheckToken);
    if (protectedHeader.typ !== "JWT" || protectedHeader.alg !== "RS256") {
      throw new Error("Unsupported App Check token header.");
    }
    const verifiedAppCheck = await jwtVerify(appCheckToken, appCheckKeys, {
      issuer: `https://firebaseappcheck.googleapis.com/${env.FIREBASE_PROJECT_NUMBER}`,
      audience: `projects/${env.FIREBASE_PROJECT_NUMBER}`,
      algorithms: ["RS256"],
    });
    appCheckSubject = verifiedAppCheck.payload.sub;
  } catch {
    throw new Error("Invalid Firebase App Check token.");
  }
  const allowedAppIds = (env.FIREBASE_APP_IDS || env.FIREBASE_APP_ID).split(",").map((value) => value.trim()).filter(Boolean);
  if (typeof appCheckSubject !== "string" || !allowedAppIds.includes(appCheckSubject)) {
    throw new Error("Invalid Firebase App Check token.");
  }
  return payload.sub;
}

function extractVoiceRecords(payload: unknown, language: string): Array<Record<string, unknown>> {
  const locale = languageLocale(language).toLowerCase();
  const shortCode = language.toLowerCase();
  const asRecords = (value: unknown): Array<Record<string, unknown>> => Array.isArray(value)
    ? value.filter((item): item is Record<string, unknown> =>
        !!item && typeof item === "object" && !Array.isArray(item)
      )
    : [];

  // Providers may return a bare list, a standard { data: [...] } envelope,
  // or a grouped catalogue such as { "te-IN": [...] }. Walk known envelope
  // layers and language-keyed groups without assuming one fixed wrapper.
  const roots: unknown[] = [payload];
  if (payload && typeof payload === "object" && !Array.isArray(payload)) {
    const root = payload as Record<string, unknown>;
    roots.push(root.data, root.voices, root.results, root.items);
  }
  for (const candidate of roots) {
    const direct = asRecords(candidate);
    if (direct.length > 0) return direct;
    if (!candidate || typeof candidate !== "object" || Array.isArray(candidate)) continue;
    const object = candidate as Record<string, unknown>;
    for (const [key, value] of Object.entries(object)) {
      const normalizedKey = key.toLowerCase();
      if (normalizedKey === locale || normalizedKey === shortCode) {
        const grouped = asRecords(value);
        if (grouped.length > 0) return grouped;
      }
      if (value && typeof value === "object" && !Array.isArray(value)) {
        const nested = value as Record<string, unknown>;
        for (const [nestedKey, nestedValue] of Object.entries(nested)) {
          const normalizedNestedKey = nestedKey.toLowerCase();
          if (normalizedNestedKey === locale || normalizedNestedKey === shortCode) {
            const grouped = asRecords(nestedValue);
            if (grouped.length > 0) return grouped;
          }
        }
      }
    }
  }
  return [];
}
function languageLocale(language: string): string {
  const code = language.toLowerCase();
  if (code === "te") return "te-IN";
  if (code === "hi") return "hi-IN";
  if (code === "en") return "en-IN";
  throw new Error("Supported languages are English, Telugu and Hindi.");
}

class ProviderRequestError extends Error {
  constructor(readonly status: number) {
    super("Generation temporarily unavailable. Please try again later.");
    this.name = "ProviderRequestError";
  }
}

async function providerError(response: Response, _provider: string): Promise<never> {
  await response.text();
  throw new ProviderRequestError(response.status);
}

const VAKYAM_PUBLIC_VOICES = [
  ["Aakash", "male"], ["Amit", "male"], ["Anand", "male"], ["Archana", "female"],
  ["Bhamini", "female"], ["Jay", "male"], ["Kavya", "female"], ["Kiran", "male"],
  ["Meena", "female"], ["Mohan", "male"], ["Neha", "female"], ["Nisha", "female"],
  ["Priya", "female"], ["Vidya", "female"], ["Vikram", "male"], ["Yash", "male"],
] as const;

function vakyamPublicVoiceFallback(language: string): Array<Record<string, string>> {
  const locale = languageLocale(language);
  return VAKYAM_PUBLIC_VOICES.map(([voice_name, gender]) => ({ voice_name, gender, language: locale }));
}

/**
 * One atomic D1 UPSERT enforces per-account daily and monthly limits. A single
 * row per UID/provider avoids split daily/monthly reservations and race windows.
 * Failed upstream calls remain charged as a conservative cost-control measure.
 */
async function reserveUsage(env: Env, uid: string, provider: "india" | "vakyam", characters: number): Promise<void> {
  if (!env.USAGE_DB) {
    throw new ProviderRequestError(503);
  }
  const uidHash = Array.from(new Uint8Array(await crypto.subtle.digest(
    "SHA-256", new TextEncoder().encode(uid)
  ))).map((byte) => byte.toString(16).padStart(2, "0")).join("");
  const now = new Date();
  const day = now.toISOString().slice(0, 10);
  const month = day.slice(0, 7);
  let result: D1Result;
  try {
    result = await env.USAGE_DB.prepare(RESERVE_USAGE_SQL)
      .bind(provider, uidHash, day, characters, month, characters, now.toISOString()).run();
  } catch {
    throw new ProviderRequestError(503);
  }
  if (!result.success) throw new ProviderRequestError(503);
  if ((result.meta?.changes ?? 0) !== 1) {
    throw new ProviderRequestError(429);
  }
}

async function handle(request: Request, env: Env): Promise<Response> {
  const url = new URL(request.url);
  if (request.method === "OPTIONS") {
    return new Response(null, { status: 204, headers: corsHeaders });
  }
  if (url.pathname === "/health" && request.method === "GET") {
    return json({
      ok: true,
      service: "t2s-tts-worker",
      providers: { vakyam: Boolean(env.VAKYAM_API_KEY), india: Boolean(env.INDIA_API_KEY) },
      usageQuota: Boolean(env.USAGE_DB),
      vakyamPublicVoiceCount: VAKYAM_PUBLIC_VOICES.length,
    });
  }

  const uid = await authenticate(request, env);

  // Account deletion calls this route before Firebase Auth is removed so the
  // hashed per-account quota ledger is erased from D1 as well as Firestore.
  if (url.pathname === "/v1/account/usage" && request.method === "DELETE") {
    if (!env.USAGE_DB) return json({ deleted: 0 });
    const uidHash = Array.from(new Uint8Array(await crypto.subtle.digest(
      "SHA-256", new TextEncoder().encode(uid)
    ))).map((byte) => byte.toString(16).padStart(2, "0")).join("");
    let result: D1Result;
    try {
      result = await env.USAGE_DB.prepare(DELETE_USAGE_SQL).bind(uidHash).run();
    } catch {
      throw new ProviderRequestError(503);
    }
    if (!result.success) throw new ProviderRequestError(503);
    return json({ deleted: result.meta?.changes ?? 0 });
  }

  if (url.pathname.startsWith("/v1/sarvam/")) {
    const gateway = env.SARVAM_GATEWAY_URL || "https://script.google.com/macros/s/AKfycbzreX70ong9CQwtGMZ4tcSSLLY3iZ28MFL0ny6L6pitmRLUq6duHCJXiYZ_O2Do8wPP/exec";
    const idToken = request.headers.get("authorization")?.replace(/^Bearer\\s+/i, "");
    if (!idToken) return json({ error: "Sign in to T2S before using Sarvam voices." }, 401);
    let action: string;
    let data: Record<string, unknown> = {};
    if (url.pathname === "/v1/sarvam/voices" && request.method === "GET") action = "list";
    else if (url.pathname === "/v1/sarvam/voices" && request.method === "POST") {
      action = "create";
      data = await request.json() as Record<string, unknown>;
      const name = typeof data.name === "string" ? data.name.trim() : "";
      const sampleBase64 = typeof data.sampleBase64 === "string" ? data.sampleBase64 : "";
      if (!name || name.length > 60 || !sampleBase64 || sampleBase64.length > 8_500_000) {
        return json({ error: "Provide a voice name and a valid audio sample (maximum 6 MB)." }, 400);
      }
      if (!["en-IN", "te-IN", "hi-IN"].includes(String(data.language))) return json({ error: "Unsupported voice language." }, 400);
      if (!["Male", "Female", "Other"].includes(String(data.gender))) return json({ error: "Choose a valid voice gender." }, 400);
    } else if (url.pathname === "/v1/sarvam/tts" && request.method === "POST") {
      action = "generate";
      data = await request.json() as Record<string, unknown>;
      if (typeof data.voiceId !== "string" || !data.voiceId.startsWith("svc-")) return json({ error: "Choose a registered Sarvam voice." }, 400);
      if (typeof data.text !== "string" || !data.text.trim() || data.text.length > 1000) return json({ error: "Text must contain 1–1,000 characters." }, 400);
      if (!["en-IN", "te-IN", "hi-IN"].includes(String(data.language))) return json({ error: "Unsupported voice language." }, 400);
    } else return json({ error: "Route not found." }, 404);

    const payload = { action, idToken, ...data };
    const upstream = await fetch(gateway, {
      method: "POST",
      headers: { "content-type": "application/json", accept: "application/json" },
      body: JSON.stringify(payload),
      redirect: "follow",
    });
    const body = await upstream.text();
    if (!upstream.ok) return json({ error: "Sarvam service request failed. Please try again." }, 502);
    let result: unknown;
    try { result = JSON.parse(body); } catch { return json({ error: "Sarvam service returned an invalid response." }, 502); }
    if (result && typeof result === "object" && "error" in result) {
      return json({ error: String((result as { error: unknown }).error || "Sarvam request failed.") }, 502);
    }
    return json(result);
  }

  if (url.pathname === "/v1/india/voices" && request.method === "GET") {
    const response = await fetch("https://api.dharun.dev/v1/tts/voices", {
      headers: env.INDIA_API_KEY ? { authorization: `Bearer ${env.INDIA_API_KEY}` } : {},
    });
    if (!response.ok) return providerError(response, "India TTS");
    const body: unknown = await response.json();
    const language = url.searchParams.get("language") || "te";
    const records = extractVoiceRecords(body, language);
    if (records.length === 0) {
      return json({ error: "India TTS returned an empty or unsupported voice catalogue response." }, 502);
    }
    const voices = records.filter(v =>
      String(v.language || "").split("-")[0].toLowerCase() === language.toLowerCase() &&
      v.non_commercial !== true
    );
    return json({ voices });
  }

  if (url.pathname === "/v1/india/tts" && request.method === "POST") {
    const input = await request.json() as { text?: string; voice?: string; language?: string; speed?: number; format?: string };
    const text = input.text?.trim() || "";
    if (!text || text.length > 3000) return json({ error: "Text must contain 1–3,000 characters." }, 400);
    const voice = input.voice?.trim() || languageLocale(input.language || "en");
    const format = ["mp3", "wav", "ogg"].includes(input.format || "") ? input.format! : "mp3";
    await reserveUsage(env, uid, "india", text.length);
    const query = new URLSearchParams({ text, voice, format, speed: String(Math.min(2, Math.max(0.5, input.speed || 1))) });
    const headers: Record<string, string> = { accept: format === "mp3" ? "audio/mpeg" : format === "wav" ? "audio/wav" : "audio/ogg" };
    if (env.INDIA_API_KEY) headers.authorization = `Bearer ${env.INDIA_API_KEY}`;
    const response = await fetch(`https://api.dharun.dev/v1/tts?${query}`, { headers });
    if (!response.ok) return providerError(response, "India TTS");
    const bytes = new Uint8Array(await response.arrayBuffer());
    if (bytes.byteLength < 1024 || bytes.byteLength > 8_000_000) {
      return json({ error: "India TTS returned an invalid or oversized audio file." }, 502);
    }
    let binary = "";
    for (let i = 0; i < bytes.length; i += 0x8000) binary += String.fromCharCode(...bytes.subarray(i, i + 0x8000));
    return json({ audioBase64: btoa(binary), contentType: response.headers.get("content-type") || "audio/mpeg" });
  }

  if (url.pathname === "/v1/vakyam/voices" && request.method === "GET") {
    const language = url.searchParams.get("language") || "te";
    const publicRoster = vakyamPublicVoiceFallback(language);
    try {
      const response = await fetch("https://api.vakyam.ai/v1/voices?group_by=language", {
        headers: { authorization: `Bearer ${env.VAKYAM_API_KEY}`, accept: "application/json" },
      });
      // Catalogue discovery is independent of speech generation. Always keep
      // the documented public voices selectable if the provider catalogue is
      // blocked, rate-limited, unavailable, or returns an unsupported response.
      if (!response.ok) {
        return json({
          voices: publicRoster,
          source: "documented-public-roster-provider-unavailable",
          providerStatus: response.status,
        });
      }
      const root: unknown = await response.json();
      const records = extractVoiceRecords(root, language);
      const providerVoices = records.filter(item =>
        typeof item.voice_name === "string" || typeof item.name === "string"
      );
      // Seed with all 16 documented public presets, then enrich/override matching
      // names with provider metadata when the live catalogue is available.
      const merged = new Map<string, Record<string, unknown>>();
      for (const voice of publicRoster) merged.set(voice.voice_name, voice);
      for (const voice of providerVoices) {
        const name = String(voice.voice_name || voice.name || "").trim();
        if (name) merged.set(name, { ...merged.get(name), ...voice });
      }
      return json({ voices: Array.from(merged.values()), source: "public-roster-plus-provider" });
    } catch {
      // Network or JSON parsing failures must not hide the public roster.
      return json({ voices: publicRoster, source: "documented-public-roster-provider-unavailable" });
    }
  }

  if (url.pathname === "/v1/vakyam/tts" && request.method === "POST") {
    const input = await request.json() as { text?: string; voice?: string; language?: string; speed?: number; format?: string };
    const text = input.text?.trim() || "";
    if (!text || text.length > 3000) return json({ error: "Text must contain 1–3,000 characters." }, 400);
    if (!input.voice?.trim()) return json({ error: "Choose a Vakyam voice." }, 400);
    const locale = languageLocale(input.language || "en");
    const format = input.format === "wav" ? "wav" : "mp3";
    await reserveUsage(env, uid, "vakyam", text.length);
    const response = await fetch("https://api.vakyam.ai/v1/tts/generate", {
      method: "POST",
      headers: { authorization: `Bearer ${env.VAKYAM_API_KEY}`, "content-type": "application/json", accept: "application/json" },
      body: JSON.stringify({
        text, model_id: "raaga-v1", voice: input.voice, language: locale,
        output_format: format, speed: Math.min(2, Math.max(0.5, input.speed || 1)),
      }),
    });
    if (!response.ok) return providerError(response, "Vakyam");
    const root = await response.json() as { audio?: string; audio_base64?: string; data?: { audio?: string } };
    const audioBase64 = root.audio || root.audio_base64 || root.data?.audio;
    if (!audioBase64) return json({ error: "Vakyam returned no audio data." }, 502);
    return json({ audioBase64, contentType: format === "wav" ? "audio/wav" : "audio/mpeg" });
  }

  return json({ error: "Route not found." }, 404);
}

const corsHeaders = {
  "access-control-allow-origin": "*",
  "access-control-allow-methods": "GET, POST, DELETE, OPTIONS",
  "access-control-allow-headers": "authorization, content-type, x-firebase-appcheck",
};

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    try {
      const response = await handle(request, env);
      const headers = new Headers(response.headers);
      for (const [key, value] of Object.entries(corsHeaders)) headers.set(key, value);
      return new Response(response.body, { status: response.status, headers });
    } catch (error) {
      let response: Response;
      if (error instanceof ProviderRequestError) {
        const status = error.status === 429 ? 429 : error.status === 503 ? 503 : error.status === 402 ? 402 : 502;
        response = json({ error: "Generation temporarily unavailable. Please try again later." }, status);
      } else {
        const message = error instanceof Error ? error.message : "Generation temporarily unavailable. Please try again later.";
        const status = message.startsWith("Sign in") || message.startsWith("Invalid Firebase") || message.startsWith("A valid Firebase App Check") ? 401 : 502;
        response = json(
          { error: status === 401 ? message : "Generation temporarily unavailable. Please try again later." },
          status
        );
      }
      const headers = new Headers(response.headers);
      for (const [key, value] of Object.entries(corsHeaders)) headers.set(key, value);
      return new Response(response.body, { status: response.status, headers });
    }
  },
};
