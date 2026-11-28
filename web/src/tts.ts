import { getToken } from "firebase/app-check";
import { auth, appCheck, appCheckReady, ttsWorkerUrl } from "./firebase";

export type OnlineProvider = "India API" | "Vakyam";
export type Voice = { id: string; name: string; language: string; gender: string; provider: string; licence?: string; attribution?: string };
export type SarvamVoice = { id: string; name: string; language: string; gender: string };

async function secureRequest(path: string, init: RequestInit = {}) {
  if (!auth?.currentUser) throw new Error("Sign in to T2S Studio to use cloud voices.");
  if (!appCheck || !appCheckReady) throw new Error("Secure web voice access is not configured. Add the Firebase App Check site key and register this web app with App Check.");
  const [idToken, appCheckToken] = await Promise.all([
    auth.currentUser.getIdToken(),
    getToken(appCheck, false)
  ]);
  const headers = new Headers(init.headers);
  headers.set("Authorization", `Bearer ${idToken}`);
  headers.set("X-Firebase-AppCheck", appCheckToken.token);
  if (init.body && !headers.has("Content-Type")) headers.set("Content-Type", "application/json");
  const response = await fetch(`${ttsWorkerUrl}${path}`, { ...init, headers });
  const payload = await response.json().catch(() => ({})) as Record<string, unknown>;
  if (!response.ok) throw new Error(String(payload.error || `Voice service request failed (HTTP ${response.status}).`));
  return payload;
}

export async function listOnlineVoices(provider: OnlineProvider, language: string): Promise<Voice[]> {
  const route = provider === "Vakyam" ? "vakyam" : "india";
  const result = await secureRequest(`/v1/${route}/voices?language=${encodeURIComponent(language)}`);
  const items = Array.isArray(result.voices) ? result.voices as Record<string, unknown>[] : [];
  return items.map((item, index) => ({
    id: String(item.id || item.voice_id || item.voice_name || item.name || item.speaker || index),
    name: String(item.name || item.voice_name || item.speaker || item.id || `Voice ${index + 1}`),
    language: String(item.language || (language === "te" ? "te-IN" : language === "hi" ? "hi-IN" : "en-IN")),
    gender: String(item.gender || "Unknown"),
    provider,
    licence: String(item.licence || ""),
    attribution: String(item.attribution || "")
  }));
}

export async function generateOnlineSpeech(input: {
  provider: OnlineProvider; text: string; voice: string; language: string; speed: number; format: "mp3" | "wav";
}): Promise<Blob> {
  const route = input.provider === "Vakyam" ? "vakyam" : "india";
  const result = await secureRequest(`/v1/${route}/tts`, {
    method: "POST",
    body: JSON.stringify({ text: input.text, voice: input.voice, language: input.language, speed: input.speed, format: input.format })
  });
  const encoded = String(result.audioBase64 || "");
  if (!encoded) throw new Error("The provider returned an empty audio file.");
  const binary = atob(encoded);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
  const blob = new Blob([bytes], { type: String(result.contentType || (input.format === "wav" ? "audio/wav" : "audio/mpeg")) });
  if (blob.size < 1024) throw new Error("The provider returned an invalid audio file.");
  return blob;
}

export async function listSarvamVoices(): Promise<SarvamVoice[]> {
  const result = await secureRequest("/v1/sarvam/voices", { method: "GET" });
  const items = Array.isArray(result.voices) ? result.voices as Record<string, unknown>[] : [];
  return items.map(item => ({
    id: String(item.voiceId || item.voice_id || item.id || ""),
    name: String(item.name || item.voiceName || item.voice_name || "My Sarvam voice"),
    language: String(item.language || "en-IN"),
    gender: String(item.gender || "Unknown")
  })).filter(item => item.id.startsWith("svc-"));
}

export async function registerSarvamVoice(input: {
  name: string; language: string; gender: string; audio: Blob;
}): Promise<string> {
  if (input.audio.size < 1024 || input.audio.size > 6 * 1024 * 1024) throw new Error("Voice sample must be between 1 KB and 6 MB.");
  const base64 = await new Promise<string>((resolve, reject) => {
    const reader = new FileReader();
    reader.onerror = () => reject(new Error("Could not read the voice sample."));
    reader.onload = () => resolve(String(reader.result).split(",")[1] || "");
    reader.readAsDataURL(input.audio);
  });
  const result = await secureRequest("/v1/sarvam/voices", {
    method: "POST",
    body: JSON.stringify({
      name: input.name.trim(), language: input.language, gender: input.gender,
      mimeType: input.audio.type || "audio/webm", sampleBase64: base64
    })
  });
  const id = String(result.voiceId || result.voice_id || "");
  if (!id.startsWith("svc-")) throw new Error("Sarvam did not return a registered voice ID.");
  return id;
}

export async function generateSarvamSpeech(input: { voiceId: string; text: string; language: string }): Promise<Blob> {
  const result = await secureRequest("/v1/sarvam/tts", { method: "POST", body: JSON.stringify(input) });
  const encoded = String(result.audioBase64 || "");
  if (!encoded) throw new Error("Sarvam returned no audio data.");
  const binary = atob(encoded);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
  const blob = new Blob([bytes], { type: "audio/wav" });
  if (blob.size < 1024) throw new Error("Sarvam returned invalid audio.");
  return blob;
}
