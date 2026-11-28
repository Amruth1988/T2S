import { useEffect, useRef, useState } from "react";
import { AudioLines, Cloud, Download, Mic, Pause, Play, RefreshCw, ShieldCheck, Square, WandSparkles } from "lucide-react";
import type { User } from "firebase/auth";
import { generateOnlineSpeech, generateSarvamSpeech, listOnlineVoices, listSarvamVoices, registerSarvamVoice, type OnlineProvider, type Voice, type SarvamVoice } from "./tts";

type Props = { user: User | null; onMessage: (message: string) => void };
const locale = (code: string) => code === "te" ? "te-IN" : code === "hi" ? "hi-IN" : "en-IN";
const greeting: Record<string, string> = { en: "Welcome to T to S studio", te: "అందరికి నమస్కారం. T2S స్టూడియో కి స్వాగతం.", hi: "सभी को नमस्कार। T2S स्टूडियो में आपका स्वागत है।" };

export default function CloudVoices({ user, onMessage }: Props) {
  const [language, setLanguage] = useState("te");
  const [provider, setProvider] = useState<OnlineProvider>("Vakyam");
  const [voices, setVoices] = useState<Voice[]>([]);
  const [myVoices, setMyVoices] = useState<SarvamVoice[]>([]);
  const [voiceId, setVoiceId] = useState("");
  const [sarvamId, setSarvamId] = useState("");
  const [text, setText] = useState(greeting.te);
  const [speed, setSpeed] = useState(1);
  const [format, setFormat] = useState<"mp3"|"wav">("mp3");
  const [loading, setLoading] = useState(false);
  const [generating, setGenerating] = useState(false);
  const [audioUrl, setAudioUrl] = useState("");
  const [recording, setRecording] = useState(false);
  const [sample, setSample] = useState<Blob|null>(null);
  const [sampleUrl, setSampleUrl] = useState("");
  const [name, setName] = useState("");
  const [gender, setGender] = useState("Male");
  const recorder = useRef<MediaRecorder|null>(null);
  const stream = useRef<MediaStream|null>(null);
  const chunks = useRef<BlobPart[]>([]);

  useEffect(() => {
    setText(greeting[language]);
    setVoiceId("");
  }, [language]);

  useEffect(() => () => {
    if (audioUrl) URL.revokeObjectURL(audioUrl);
    if (sampleUrl) URL.revokeObjectURL(sampleUrl);
    stream.current?.getTracks().forEach(track => track.stop());
    if (recorder.current?.state === "recording") recorder.current.stop();
  }, [audioUrl, sampleUrl]);

  async function loadCatalogue() {
    if (!user) { onMessage("Sign in to load secure online voices."); return; }
    setLoading(true);
    try {
      const found = await listOnlineVoices(provider, language);
      setVoices(found);
      setVoiceId(found[0]?.id || "");
      onMessage(`${found.length} ${provider} voices loaded.`);
    } catch (error) { onMessage(error instanceof Error ? error.message : "Could not load the online voice catalogue."); }
    finally { setLoading(false); }
  }
  async function loadMyVoices() {
    if (!user) { onMessage("Sign in to load your Sarvam voice profiles."); return; }
    setLoading(true);
    try {
      const found = await listSarvamVoices();
      setMyVoices(found);
      setSarvamId(found[0]?.id || "");
      onMessage(`${found.length} registered Sarvam voices loaded.`);
    } catch (error) { onMessage(error instanceof Error ? error.message : "Could not load Sarvam voices."); }
    finally { setLoading(false); }
  }
  async function generate() {
    if (!user) { onMessage("Sign in to generate cloud speech."); return; }
    if (!text.trim()) { onMessage("Enter text before generating speech."); return; }
    setGenerating(true);
    try {
      const blob = sarvamId
        ? await generateSarvamSpeech({ voiceId: sarvamId, text, language: locale(language) })
        : await generateOnlineSpeech({ provider, text, voice: voiceId, language, speed, format });
      if (blob.size < 1024) throw new Error("The voice provider returned an empty audio file.");
      const url = URL.createObjectURL(blob);
      setAudioUrl(previous => { if (previous) URL.revokeObjectURL(previous); return url; });
      onMessage("Speech generated. You can listen or download it below.");
    } catch (error) { onMessage(error instanceof Error ? error.message : "Speech generation failed."); }
    finally { setGenerating(false); }
  }
  async function startRecording() {
    try {
      const media = await navigator.mediaDevices.getUserMedia({ audio: true });
      stream.current = media;
      const type = MediaRecorder.isTypeSupported("audio/webm;codecs=opus") ? "audio/webm;codecs=opus" : "audio/webm";
      const active = new MediaRecorder(media, { mimeType: type });
      chunks.current = [];
      active.ondataavailable = event => { if (event.data.size) chunks.current.push(event.data); };
      active.onstop = () => {
        const blob = new Blob(chunks.current, { type: active.mimeType || "audio/webm" });
        setSample(blob);
        setSampleUrl(previous => { if (previous) URL.revokeObjectURL(previous); return URL.createObjectURL(blob); });
        media.getTracks().forEach(track => track.stop());
        onMessage("Voice sample recorded locally. Press Register only when you want to upload it.");
      };
      active.start();
      recorder.current = active;
      setRecording(true);
    } catch { onMessage("Microphone permission is unavailable. Allow microphone access and try again."); }
  }
  async function registerVoice() {
    if (!user) { onMessage("Sign in before registering a Sarvam voice."); return; }
    if (!sample) { onMessage("Record and stop a voice sample first."); return; }
    if (!name.trim()) { onMessage("Enter a name for this voice profile."); return; }
    setGenerating(true);
    try {
      const id = await registerSarvamVoice({ name, language: locale(language), gender, audio: sample });
      setName("");
      onMessage(`Sarvam voice registered successfully: ${id}`);
      await loadMyVoices();
    } catch (error) { onMessage(error instanceof Error ? error.message : "Voice registration failed."); }
    finally { setGenerating(false); }
  }

  return <div className="page-stack">
    <div className="page-title"><div><span className="eyebrow">ONLINE + PERSONAL VOICES</span><h1>Cloud Voice Studio</h1><p>Use the existing T2S voice services with secure account verification.</p></div><span className="status-pill"><span/> Authenticated services</span></div>
    <section className="panel cloud-panel">
      <div className="cloud-panel-heading"><div className="content-icon"><Cloud/></div><div><h3>Online voice catalogue</h3><p>India API and Vakyam voices for English, Telugu and Hindi.</p></div></div>
      <div className="catalogue-tools">
        <label>Provider<select value={provider} onChange={e=>{setProvider(e.target.value as OnlineProvider);setVoices([]);setVoiceId("");}}><option>Vakyam</option><option>India API</option></select></label>
        <label>Language<select value={language} onChange={e=>setLanguage(e.target.value)}><option value="en">English</option><option value="te">Telugu</option><option value="hi">Hindi</option></select></label>
        <button className="outline" onClick={loadCatalogue} disabled={loading}><RefreshCw size={15}/> {loading?"Loading…":"Load voices"}</button>
      </div>
      <label>Online voice<select value={voiceId} onChange={e=>{setVoiceId(e.target.value);setSarvamId("");}}><option value="">Choose an online voice</option>{voices.map(v=><option key={v.id} value={v.id}>{v.name} · {v.gender} · {v.language}</option>)}</select></label>
      {voices.length>0&&<div className="notice"><ShieldCheck size={16}/>{voices.length} catalogue entries loaded from {provider}. Provider voice availability may vary by language.</div>}
    </section>
    <section className="panel cloud-panel">
      <div className="cloud-panel-heading"><div className="content-icon"><WandSparkles/></div><div><h3>Generate speech</h3><p>Generate a real provider audio file, then play or download it.</p></div></div>
      <label>Script<textarea rows={7} value={text} maxLength={3000} onChange={e=>setText(e.target.value)} placeholder="Type your script…"/></label>
      <div className="catalogue-tools">
        <label>Speed <strong>{speed.toFixed(1)}×</strong><input type="range" min="0.5" max="2" step="0.1" value={speed} onChange={e=>setSpeed(Number(e.target.value))}/></label>
        <label>Output format<select value={format} onChange={e=>setFormat(e.target.value as "mp3"|"wav")}><option value="mp3">MP3</option><option value="wav">WAV</option></select></label>
        <button className="primary" onClick={generate} disabled={generating||(!voiceId&&!sarvamId)}>{generating?"Working…":"Generate speech"}</button>
      </div>
      {audioUrl&&<div className="generated-audio"><audio controls src={audioUrl}/><a className="outline" href={audioUrl} download={`t2s-speech.${sarvamId?"wav":format}`}><Download size={16}/> Download audio</a></div>}
    </section>
    <section className="panel cloud-panel">
      <div className="cloud-panel-heading"><div className="content-icon"><AudioLines/></div><div><h3>My Sarvam voices</h3><p>Register your own sample and reuse the returned Sarvam voice ID.</p></div></div>
      <div className="catalogue-tools"><label>Registered voice<select value={sarvamId} onChange={e=>{setSarvamId(e.target.value);setVoiceId("");}}><option value="">Use online catalogue voice instead</option>{myVoices.map(v=><option key={v.id} value={v.id}>{v.name} · {v.language} · {v.gender}</option>)}</select></label><button className="outline" onClick={loadMyVoices} disabled={loading}>{loading?"Loading…":"Load my voices"}</button></div>
      <div className="record-actions">{recording?<button className="primary" onClick={()=>{recorder.current?.stop();setRecording(false);}}><Square size={16}/> Stop recording</button>:<button className="outline" onClick={startRecording}><Mic size={16}/> Record my voice</button>}{sampleUrl&&<audio controls src={sampleUrl}/>}</div>
      <div className="sarvam-register">
        <label>Voice name<input value={name} onChange={e=>setName(e.target.value)} placeholder="My Telugu voice"/></label>
        <div className="catalogue-tools"><label>Gender<select value={gender} onChange={e=>setGender(e.target.value)}><option>Male</option><option>Female</option><option>Other</option></select></label><label>Sample language<select value={language} onChange={e=>setLanguage(e.target.value)}><option value="en">English</option><option value="te">Telugu</option><option value="hi">Hindi</option></select></label></div>
        <button className="primary" onClick={registerVoice} disabled={!sample||generating}>{generating?"Registering…":"Register voice with Sarvam"}</button>
        <p className="notice"><ShieldCheck size={16}/> Your recording remains in this browser until you press Register. Registration sends the sample to the existing T2S Sarvam gateway for processing.</p>
      </div>
    </section>
    {!user&&<div className="notice"><ShieldCheck size={16}/> Sign in first. Cloud voice features require Firebase Authentication and App Check.</div>}
  </div>;
}
