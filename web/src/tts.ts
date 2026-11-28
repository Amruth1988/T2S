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

async function normalizeVoiceSample(source: Blob): Promise<Blob> {
  const AudioContextClass = window.AudioContext;
  if (!AudioContextClass) throw new Error("This browser cannot prepare the recording for Sarvam voice registration.");
  const context = new AudioContextClass();
  try {
    const decoded = await context.decodeAudioData(await source.arrayBuffer());
    const sampleRate = decoded.sampleRate;
    const frames = decoded.length;
    const pcm = new Int16Array(frames);
    for (let i = 0; i < frames; i++) {
      let sample = 0;
      for (let channel = 0; channel < decoded.numberOfChannels; channel++) sample += decoded.getChannelData(channel)[i] || 0;
      sample /= decoded.numberOfChannels;
      pcm[i] = Math.max(-1, Math.min(1, sample)) * (sample < 0 ? 32768 : 32767);
    }
    const bytes = new ArrayBuffer(44 + pcm.byteLength);
    const view = new DataView(bytes);
    const writeText = (offset: number, value: string) => { for (let i = 0; i < value.length; i++) view.setUint8(offset + i, value.charCodeAt(i)); };
    writeText(0, "RIFF"); view.setUint32(4, 36 + pcm.byteLength, true); writeText(8, "WAVE");
    writeText(12, "fmt "); view.setUint32(16, 16, true); view.setUint16(20, 1, true);
    view.setUint16(22, 1, true); view.setUint32(24, sampleRate, true);
    view.setUint32(28, sampleRate * 2, true); view.setUint16(32, 2, true);
    view.setUint16(34, 16, true); writeText(36, "data"); view.setUint32(40, pcm.byteLength, true);
    new Int16Array(bytes, 44).set(pcm);
    return new Blob([bytes], { type: "audio/wav" });
  } finally { await context.close(); }
}

export async function registerSarvamVoice(input: {
  name: string; language: string; gender: string; audio: Blob;
}): Promise<string> {
  if (input.audio.size < 1024) throw new Error("Voice sample is too short or empty.");
  const wav = await normalizeVoiceSample(input.audio);
  if (wav.size > 6 * 1024 * 1024) throw new Error("The prepared WAV sample exceeds Sarvam 6 MB limit. Record a shorter sample.");
  const base64 = await new Promise<string>((resolve, reject) => {
    const reader = new FileReader();
    reader.onerror = () => reject(new Error("Could not read the voice sample."));
    reader.onload = () => resolve(String(reader.result).split(",")[1] || "");
    reader.readAsDataURL(wav);
  });
  const result = await secureRequest("/v1/sarvam/voices", {
    method: "POST",
    body: JSON.stringify({
      name: input.name.trim(), language: input.language, gender: input.gender,
      mimeType: "audio/wav", sampleBase64: base64
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
