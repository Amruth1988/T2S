import { createRemoteJWKSet, decodeProtectedHeader, jwtVerify } from "jose";

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
  const token = request.headers.get("authorization")?.match(/^Bearer\\s+(.+)$/i)?.[1];
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
  if (typeof appCheckSubject !== "string" || appCheckSubject !== env.FIREBASE_APP_ID) {
    throw new Error("Invalid Firebase App Check token.");
  }
  return payload.sub;
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
  const result = await env.USAGE_DB.prepare(`
    INSERT INTO user_provider_usage
      (provider, uid_hash, day, daily_characters, daily_requests, month, monthly_characters, updated_at)
    VALUES (?, ?, ?, ?, 1, ?, ?, ?)
    ON CONFLICT(provider, uid_hash) DO UPDATE SET
      day = excluded.day,
      daily_characters = CASE
        WHEN user_provider_usage.day = excluded.day
          THEN user_provider_usage.daily_characters + excluded.daily_characters
        ELSE excluded.daily_characters END,
      daily_requests = CASE
        WHEN user_provider_usage.day = excluded.day
          THEN user_provider_usage.daily_requests + 1
        ELSE 1 END,
      month = excluded.month,
      monthly_characters = CASE
        WHEN user_provider_usage.month = excluded.month
          THEN user_provider_usage.monthly_characters + excluded.monthly_characters
        ELSE excluded.monthly_characters END,
      updated_at = excluded.updated_at
    WHERE
      (user_provider_usage.day != excluded.day OR
        (user_provider_usage.daily_characters + excluded.daily_characters <= 10000
          AND user_provider_usage.daily_requests + 1 <= 30))
      AND
      (user_provider_usage.month != excluded.month OR
        user_provider_usage.monthly_characters + excluded.monthly_characters <= 100000)
  `).bind(provider, uidHash, day, characters, month, characters, now.toISOString()).run();
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
    });
  }

  const uid = await authenticate(request, env);

  if (url.pathname === "/v1/india/voices" && request.method === "GET") {
    const response = await fetch("https://api.dharun.dev/v1/tts/voices", {
      headers: env.INDIA_API_KEY ? { authorization: `Bearer ${env.INDIA_API_KEY}` } : {},
    });
    if (!response.ok) return providerError(response, "India TTS");
    const body = await response.json() as { data?: Array<Record<string, unknown>> };
    const language = url.searchParams.get("language") || "te";
    const voices = (body.data || []).filter(v =>
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
    const response = await fetch("https://api.vakyam.ai/v1/voices?group_by=language", {
      headers: { authorization: `Bearer ${env.VAKYAM_API_KEY}`, accept: "application/json" },
    });
    if (!response.ok) return providerError(response, "Vakyam");
    const root = await response.json() as Record<string, unknown> & { data?: Record<string, unknown> };
    const locale = languageLocale(url.searchParams.get("language") || "te");
    const grouped = root.data && typeof root.data === "object" ? root.data : root;
    const data = grouped[locale];
    const voices = Array.isArray(data)
      ? data.filter((item): item is Record<string, unknown> =>
          !!item && typeof item === "object" &&
          typeof (item as Record<string, unknown>).voice_name === "string")
      : [];
    return json({ voices });
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
  "access-control-allow-methods": "GET, POST, OPTIONS",
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
      if (error instanceof ProviderRequestError) {
        const status = error.status === 429 ? 429 : error.status === 503 ? 503 : error.status === 402 ? 402 : 502;
        return json({ error: "Generation temporarily unavailable. Please try again later." }, status);
      }
      const message = error instanceof Error ? error.message : "Generation temporarily unavailable. Please try again later.";
      const status = message.startsWith("Sign in") || message.startsWith("Invalid Firebase") || message.startsWith("A valid Firebase App Check") ? 401 : 502;
      const response = json(
        { error: status === 401 ? message : "Generation temporarily unavailable. Please try again later." },
        status
      );
      const headers = new Headers(response.headers);
      for (const [key, value] of Object.entries(corsHeaders)) headers.set(key, value);
      return new Response(response.body, { status: response.status, headers });
    }
  },
};
