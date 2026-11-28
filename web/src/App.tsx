import { useEffect, useMemo, useState } from "react";
import {
  AudioLines, BookOpen, CircleHelp, Cloud, Download, FileAudio,
  FolderKanban, Headphones, Home, LogIn, LogOut, Menu, Mic,
  Pause, Play, Plus, Search, Settings, ShieldCheck, Sparkles, Square, Trash2,
  UserRound, Volume2, WandSparkles, X
} from "lucide-react";
import {
  createUserWithEmailAndPassword, onAuthStateChanged,
  sendPasswordResetEmail, signInWithEmailAndPassword, signInWithPopup, signOut,
  updateProfile, type User
} from "firebase/auth";
import { collection, doc, getDocs, setDoc, serverTimestamp } from "firebase/firestore";
import { auth, db, firebaseReady, googleProvider } from "./firebase";
import CloudVoices from "./CloudVoices";

type Page = "Home" | "Studio" | "Cloud Studio" | "Voice Library" | "Projects" | "Drafts" | "My Voices" | "Settings";
type Draft = { id: string; title: string; text: string; language: string; updatedAt: number };
type Project = { id: string; name: string; createdAt: number };
const pages: { name: Page; icon: typeof Home }[] = [
  { name: "Home", icon: Home }, { name: "Studio", icon: WandSparkles }, { name: "Cloud Studio", icon: Cloud },
  { name: "Voice Library", icon: Headphones }, { name: "Projects", icon: FolderKanban },
  { name: "Drafts", icon: BookOpen }, { name: "My Voices", icon: AudioLines },
  { name: "Settings", icon: Settings }
];
const samples: Record<string, string> = {
  en: "Welcome to T to S studio",
  te: "అందరికి నమస్కారం. T2S స్టూడియో కి స్వాగతం.",
  hi: "सभी को नमस्कार। T2S स्टूडियो में आपका स्वागत है।"
};
const languageNames: Record<string, string> = { en: "English", te: "Telugu", hi: "Hindi" };
const makeId = () => crypto.randomUUID();

export default function App() {
  const [user, setUser] = useState<User | null>(null);
  const [page, setPage] = useState<Page>("Home");
  const [language, setLanguage] = useState("en");
  const [text, setText] = useState("");
  const [speed, setSpeed] = useState(1);
  const [pitch, setPitch] = useState(1);
  const [voices, setVoices] = useState<SpeechSynthesisVoice[]>([]);
  const [voiceName, setVoiceName] = useState("");
  const [playing, setPlaying] = useState(false);
  const [query, setQuery] = useState("");
  const [drafts, setDrafts] = useState<Draft[]>([]);
  const [projects, setProjects] = useState<Project[]>([]);
  const [recording, setRecording] = useState(false);
  const [recordUrl, setRecordUrl] = useState("");
  const [recordName, setRecordName] = useState("My voice recording");
  const [message, setMessage] = useState("");
  const [authOpen, setAuthOpen] = useState(false);
  const [authMode, setAuthMode] = useState<"login" | "register">("login");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [displayName, setDisplayName] = useState("");
  const [mobileNav, setMobileNav] = useState(false);
  const [tab, setTab] = useState("Installed");
  const [mediaRecorder, setMediaRecorder] = useState<MediaRecorder | null>(null);

  useEffect(() => {
    if (!auth) return;
    return onAuthStateChanged(auth, setUser);
  }, []);

  useEffect(() => {
    const load = () => setVoices(window.speechSynthesis?.getVoices() ?? []);
    load();
    window.speechSynthesis?.addEventListener("voiceschanged", load);
    return () => window.speechSynthesis?.removeEventListener("voiceschanged", load);
  }, []);

  useEffect(() => {
    const key = user?.uid ?? "guest";
    setDrafts(JSON.parse(localStorage.getItem("t2s-drafts-" + key) || "[]") as Draft[]);
    setProjects(JSON.parse(localStorage.getItem("t2s-projects-" + key) || "[]") as Project[]);
  }, [user?.uid]);

  useEffect(() => {
    if (!message) return;
    const timer = window.setTimeout(() => setMessage(""), 4200);
    return () => window.clearTimeout(timer);
  }, [message]);

  const availableVoices = useMemo(() => voices.filter(v => {
    const langPrefix = language === "en" ? "en" : language;
    return v.lang.toLowerCase().startsWith(langPrefix);
  }), [voices, language]);
  const selectedVoice = availableVoices.find(v => v.name === voiceName) ?? availableVoices[0];
  const localKey = user?.uid ?? "guest";

  function persistDrafts(next: Draft[]) {
    setDrafts(next);
    localStorage.setItem("t2s-drafts-" + localKey, JSON.stringify(next));
  }
  function persistProjects(next: Project[]) {
    setProjects(next);
    localStorage.setItem("t2s-projects-" + localKey, JSON.stringify(next));
  }
  function speak(value = text) {
    if (!value.trim()) { setMessage("Enter some text to preview your speech."); return; }
    if (!("speechSynthesis" in window)) { setMessage("Speech playback is not supported by this browser."); return; }
    window.speechSynthesis.cancel();
    const utterance = new SpeechSynthesisUtterance(value);
    utterance.lang = language === "en" ? "en-IN" : language === "te" ? "te-IN" : "hi-IN";
    if (selectedVoice) utterance.voice = selectedVoice;
    utterance.rate = speed;
    utterance.pitch = pitch;
    utterance.onstart = () => setPlaying(true);
    utterance.onend = () => setPlaying(false);
    utterance.onerror = () => { setPlaying(false); setMessage("This browser voice could not speak the selected language."); };
    window.speechSynthesis.speak(utterance);
  }
  function stopSpeech() { window.speechSynthesis?.cancel(); setPlaying(false); }
  function saveDraft() {
    if (!text.trim()) { setMessage("Write text before saving a draft."); return; }
    const draft: Draft = { id: makeId(), title: text.trim().split("\n")[0].slice(0, 64) || "Untitled draft", text, language, updatedAt: Date.now() };
    persistDrafts([draft, ...drafts]);
    setMessage("Draft saved on this browser.");
  }
  function createProject() {
    const name = window.prompt("Project name");
    if (!name?.trim()) return;
    const project = { id: makeId(), name: name.trim(), createdAt: Date.now() };
    persistProjects([project, ...projects]);
    setMessage("Project created.");
  }
  async function syncDrafts() {
    const firestore = db;
    if (!user || !firestore) { setMessage("Sign in to enable cloud sync after Firebase web setup."); return; }
    await Promise.all(drafts.map(d => setDoc(doc(firestore, "users", user.uid, "scripts", d.id), {
      title: d.title, content: d.text, language: languageNames[d.language], isDraft: true,
      isDeleted: false, updatedAt: serverTimestamp()
    }, { merge: true })));
    setMessage("Draft metadata synced to your T2S account.");
  }
  async function loadCloudDrafts() {
    const firestore = db;
    if (!user || !firestore) return;
    const snap = await getDocs(collection(firestore, "users", user.uid, "scripts"));
    const cloud = snap.docs.filter(d => d.data().isDraft && !d.data().isDeleted).map(d => {
      const v = d.data();
      return { id: d.id, title: String(v.title || "Untitled"), text: String(v.content || ""), language: v.language === "Telugu" ? "te" : v.language === "Hindi" ? "hi" : "en", updatedAt: v.updatedAt?.toMillis?.() ?? Date.now() };
    });
    const merged = [...cloud, ...drafts].filter((d, i, a) => a.findIndex(x => x.id === d.id) === i).sort((a,b) => b.updatedAt-a.updatedAt);
    persistDrafts(merged);
    setMessage("Cloud drafts loaded.");
  }
  async function authenticate(event: React.FormEvent) {
    event.preventDefault();
    if (!auth) { setMessage("Firebase web sign-in is not configured yet."); return; }
    try {
      if (authMode === "register") {
        const result = await createUserWithEmailAndPassword(auth, email.trim(), password);
        if (displayName.trim()) await updateProfile(result.user, { displayName: displayName.trim() });
      } else await signInWithEmailAndPassword(auth, email.trim(), password);
      setAuthOpen(false); setPassword(""); setMessage("Welcome to T2S Studio.");
    } catch (e) { setMessage(e instanceof Error ? e.message : "Sign-in failed."); }
  }
  async function googleSignIn() {
    if (!auth) { setMessage("Firebase web sign-in is not configured yet."); return; }
    try { await signInWithPopup(auth, googleProvider); setAuthOpen(false); setMessage("Signed in with Google."); }
    catch (e) { setMessage(e instanceof Error ? e.message : "Google sign-in failed."); }
  }
  async function resetPassword() {
    if (!auth || !email.trim()) { setMessage("Enter your email address first."); return; }
    try { await sendPasswordResetEmail(auth, email.trim()); setMessage("Password reset email sent."); }
    catch (e) { setMessage(e instanceof Error ? e.message : "Could not send reset email."); }
  }
  async function beginRecording() {
    if (!navigator.mediaDevices?.getUserMedia) { setMessage("Microphone recording requires a supported browser and HTTPS."); return; }
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      const recorder = new MediaRecorder(stream);
      const chunks: BlobPart[] = [];
      recorder.ondataavailable = e => { if (e.data.size) chunks.push(e.data); };
      recorder.onstop = () => {
        const blob = new Blob(chunks, { type: recorder.mimeType || "audio/webm" });
        if (recordUrl) URL.revokeObjectURL(recordUrl);
        setRecordUrl(URL.createObjectURL(blob));
        stream.getTracks().forEach(t => t.stop());
        setMessage("Recording ready for local preview. It has not been uploaded.");
      };
      recorder.start(); setMediaRecorder(recorder); setRecording(true);
    } catch { setMessage("Microphone permission was denied or unavailable."); }
  }
  function endRecording() { mediaRecorder?.stop(); setRecording(false); }
  function downloadRecording() {
    if (!recordUrl) return;
    const a = document.createElement("a"); a.href = recordUrl; a.download = "t2s-voice-recording.webm"; a.click();
  }

  const content = () => {
    if (page === "Cloud Studio") return <CloudVoices user={user} onMessage={setMessage}/>;
    if (page === "Home") return <div className="page-stack">
      <section className="hero"><div><span className="eyebrow">YOUR PERSONAL SPEECH WORKSPACE</span><h1>Give your words<br/><em>a voice.</em></h1><p>Create speech, explore voices and keep every script organized in one studio.</p><button className="primary" onClick={() => setPage("Studio")}><WandSparkles size={17}/> Open Studio</button></div><div className="hero-art"><div className="orb"><AudioLines size={76}/></div><span className="floating-chip chip-one">English · Telugu · Hindi</span><span className="floating-chip chip-two"><Volume2 size={15}/> Voice studio</span></div></section>
      <div className="section-heading"><div><span className="eyebrow">WORKSPACE</span><h2>Pick up where you left off</h2></div><button className="text-button" onClick={() => setPage("Projects")}>All projects ↗</button></div>
      <div className="stat-grid"><button className="stat-card" onClick={() => setPage("Projects")}><FolderKanban/><strong>{projects.length}</strong><span>Projects</span></button><button className="stat-card" onClick={() => setPage("Drafts")}><BookOpen/><strong>{drafts.length}</strong><span>Saved drafts</span></button><button className="stat-card" onClick={() => setPage("Voice Library")}><Headphones/><strong>{availableVoices.length}</strong><span>Browser voices</span></button></div>
      <section className="welcome-card"><div className="welcome-icon"><Sparkles/></div><div><h3>Start with a sample</h3><p>Try a greeting in your preferred language and listen with voices installed in this browser.</p></div><button className="outline" onClick={() => { setLanguage("te"); setText(samples.te); setPage("Studio"); }}>Try Telugu sample</button></section>
    </div>;
    if (page === "Studio") return <div className="page-stack">
      <div className="page-title"><div><span className="eyebrow">CREATE</span><h1>Speech Studio</h1><p>Write, tune and preview your speech.</p></div><span className="status-pill"><span/> Browser voice mode</span></div>
      <div className="studio-grid"><section className="panel editor-panel"><div className="panel-top"><h3>Your script</h3><span>{text.length.toLocaleString()} characters</span></div><div className="control-row"><label>Language<select value={language} onChange={e => {setLanguage(e.target.value);setText("");setVoiceName("");}}><option value="en">English</option><option value="te">Telugu</option><option value="hi">Hindi</option></select></label><button className="text-button" onClick={() => setText(samples[language])}>Insert sample text</button></div><textarea value={text} onChange={e => setText(e.target.value)} placeholder="Type or paste your script here…" rows={11}/><div className="editor-footer"><button className="subtle" onClick={saveDraft}><Plus size={16}/> Save draft</button><span>Autosave is not enabled</span></div></section>
      <section className="panel settings-panel"><h3>Voice settings</h3><label>Installed voice<select value={selectedVoice?.name ?? ""} onChange={e => setVoiceName(e.target.value)}><option value="">Browser default</option>{availableVoices.map(v => <option key={v.voiceURI} value={v.name}>{v.name} · {v.lang}</option>)}</select></label><div className="range-label"><span>Speed</span><b>{speed.toFixed(1)}×</b></div><input type="range" min="0.5" max="2" step="0.1" value={speed} onChange={e => setSpeed(Number(e.target.value))}/><div className="range-label"><span>Pitch</span><b>{pitch.toFixed(1)}</b></div><input type="range" min="0.5" max="2" step="0.1" value={pitch} onChange={e => setPitch(Number(e.target.value))}/><div className="voice-note"><ShieldCheck size={17}/><span>Browser voices run on your device. Voice availability depends on your operating system and browser.</span></div></section></div>
      <section className="player-bar"><div className="player-symbol"><AudioLines/></div><div className="player-copy"><strong>Speech preview</strong><span>{selectedVoice?.name ?? "Default browser voice"} · {languageNames[language]}</span></div><div className="player-actions"><button className="outline" onClick={saveDraft}><Plus size={16}/> Save</button>{playing ? <button className="primary" onClick={stopSpeech}><Pause size={16}/> Stop</button> : <button className="primary" onClick={() => speak()}><Play size={16}/> Preview speech</button>}</div></section>
      <p className="notice"><CircleHelp size={16}/> Browser speech preview does not create an MP3/WAV file. Online provider generation and export will be enabled after secure web authentication and provider setup are completed.</p>
    </div>;
    if (page === "Voice Library") return <div className="page-stack"><div className="page-title"><div><span className="eyebrow">DISCOVER</span><h1>Voice Library</h1><p>Choose a browser-installed voice or open the authenticated cloud catalogue.</p></div></div><div className="tabs"><button className={tab==="Installed"?"active":""} onClick={()=>setTab("Installed")}>Installed</button><button className={tab==="Online"?"active":""} onClick={()=>setPage("Cloud Studio")}>Online + Sarvam</button></div>{tab==="Installed"?<><div className="searchbox"><Search size={17}/><input value={query} onChange={e=>setQuery(e.target.value)} placeholder="Search installed voices…"/></div><div className="voice-list">{availableVoices.filter(v=>v.name.toLowerCase().includes(query.toLowerCase())).map(v=><div className="voice-row" key={v.voiceURI}><div className="voice-avatar"><Volume2/></div><div className="voice-info"><strong>{v.name}</strong><span>{v.lang} · Installed in browser</span></div><button className="icon-button" aria-label="Preview voice" onClick={()=>{setVoiceName(v.name);speak(samples[language]);}}><Play size={17}/></button></div>)}{availableVoices.length===0&&<div className="empty"><Headphones/><h3>No installed {languageNames[language]} voices found</h3><p>Try another language or install a voice pack in your device settings.</p></div>}</div></>:<section className="panel cloud-panel"><div className="cloud-panel-heading"><div className="content-icon"><Cloud/></div><div><h3>Online and personal voices</h3><p>India API, Vakyam, Sarvam registered voices and cloud speech generation.</p></div></div><button className="primary" onClick={()=>setPage("Cloud Studio")}><Headphones size={16}/> Open Cloud Voice Studio</button></section>}</div>;
    if (page === "Projects") return <div className="page-stack"><div className="page-title"><div><span className="eyebrow">ORGANIZE</span><h1>Projects</h1><p>Keep scripts grouped by project.</p></div><button className="primary" onClick={createProject}><Plus size={17}/> New project</button></div>{projects.length?<div className="card-grid">{projects.map(p=><article className="content-card" key={p.id}><div className="content-icon"><FolderKanban/></div><h3>{p.name}</h3><span>Created {new Date(p.createdAt).toLocaleDateString()}</span><button className="text-button" onClick={()=>setPage("Drafts")}>View drafts →</button></article>)}</div>:<div className="empty"><FolderKanban/><h3>Your projects start here</h3><p>Create a project to organize your scripts.</p><button className="primary" onClick={createProject}><Plus size={16}/> Create project</button></div>}</div>;
    if (page === "Drafts") return <div className="page-stack"><div className="page-title"><div><span className="eyebrow">YOUR WRITING</span><h1>Drafts</h1><p>Continue a script whenever inspiration strikes.</p></div><button className="primary" onClick={()=>setPage("Studio")}><Plus size={17}/> New draft</button></div>{user&&<div className="sync-row"><span><Cloud size={16}/> Drafts are stored in this browser.</span><button className="outline" onClick={loadCloudDrafts}>Load cloud drafts</button><button className="outline" onClick={syncDrafts}>Sync to account</button></div>}{drafts.length?<div className="draft-list">{drafts.map(d=><article className="draft-row" key={d.id}><div className="content-icon"><FileAudio/></div><div className="draft-info"><strong>{d.title}</strong><span>{languageNames[d.language]} · {new Date(d.updatedAt).toLocaleString()}</span><p>{d.text.slice(0,150)}</p></div><button className="outline" onClick={()=>{setText(d.text);setLanguage(d.language);setPage("Studio");}}>Open</button><button className="icon-button danger" aria-label="Delete draft" onClick={()=>persistDrafts(drafts.filter(x=>x.id!==d.id))}><Trash2 size={17}/></button></article>)}</div>:<div className="empty"><BookOpen/><h3>No drafts yet</h3><p>Write a script in Speech Studio and save it here.</p></div>}</div>;
    if (page === "My Voices") return <div className="page-stack"><div className="page-title"><div><span className="eyebrow">YOUR VOICE PROFILE</span><h1>My Voices</h1><p>Record a sample and keep a local copy.</p></div></div><section className="panel record-panel"><div className="record-art"><Mic size={34}/></div><div><h3>Record a voice sample</h3><p>Allow microphone access, record your sample, then preview or download it. Nothing is uploaded automatically.</p><label>Recording name<input value={recordName} onChange={e=>setRecordName(e.target.value)}/></label><div className="record-actions">{recording?<button className="primary" onClick={endRecording}><Square size={16}/> Stop recording</button>:<button className="primary" onClick={beginRecording}><Mic size={16}/> Start recording</button>}{recordUrl&&<><audio controls src={recordUrl}/><button className="outline" onClick={downloadRecording}><Download size={16}/> Download sample</button></>}</div></div></section><div className="notice"><ShieldCheck size={16}/> Voice sample stays in this browser session unless you choose to download it. Cloud voice registration is not active in this web build yet.</div></div>;
    return <div className="page-stack"><div className="page-title"><div><span className="eyebrow">PREFERENCES</span><h1>Settings</h1><p>Account and browser preferences.</p></div></div><section className="panel settings-card"><h3>Account</h3>{user?<><div className="account-line"><div className="user-avatar"><UserRound/></div><div><strong>{user.displayName||"T2S Studio user"}</strong><span>{user.email}</span></div></div><button className="outline" onClick={()=>auth&&signOut(auth)}><LogOut size={16}/> Sign out</button></>:<><p>Sign in to connect your T2S account. Firebase web configuration is required.</p><button className="primary" onClick={()=>setAuthOpen(true)}><LogIn size={16}/> Sign in</button></>}<div className="divider"/><h3>Browser storage</h3><p>Drafts and project names are currently saved in this browser's local storage. They do not automatically appear on your Android device.</p><button className="outline danger-outline" onClick={()=>{if(confirm("Delete this browser's local drafts and projects?")){persistDrafts([]);persistProjects([]);setMessage("Local workspace cleared.");}}}><Trash2 size={16}/> Clear local workspace</button></section></div>;
  };

  return <div className="app-shell">
    <aside className={"sidebar "+(mobileNav?"open":"")}><div className="brand"><div className="brand-mark"><AudioLines/></div><div><strong>T2S <span>Studio</span></strong><small>TEXT TO SPEECH</small></div><button className="mobile-close" onClick={()=>setMobileNav(false)}><X/></button></div><div className="workspace-label">WORKSPACE</div><nav>{pages.map(item=>{const Icon=item.icon;return <button key={item.name} className={page===item.name?"nav-item active":"nav-item"} onClick={()=>{setPage(item.name);setMobileNav(false);}}><Icon size={18}/>{item.name}{item.name==="Drafts"&&drafts.length>0&&<small>{drafts.length}</small>}</button>;})}</nav><div className="sidebar-bottom"><div className="secure-note"><ShieldCheck size={17}/><span>Your voice. Your workspace.<br/>Your control.</span></div><div className="profile-mini">{user?.photoURL?<img src={user.photoURL} alt=""/>:<div className="user-avatar"><UserRound size={17}/></div>}<div><strong>{user?.displayName||user?.email?.split("@")[0]||"Guest workspace"}</strong><span>{user?"Signed in":"Local mode"}</span></div><button className="icon-button" title={user?"Sign out":"Sign in"} onClick={()=>user?auth&&signOut(auth):setAuthOpen(true)}>{user?<LogOut size={16}/>:<LogIn size={16}/>}</button></div></div></aside>
    {mobileNav&&<button className="scrim" aria-label="Close navigation" onClick={()=>setMobileNav(false)}/>}
    <main className="main-area"><header className="topbar"><button className="mobile-menu" onClick={()=>setMobileNav(true)}><Menu/></button><div className="breadcrumb">T2S Studio <span>/</span> {page}</div><div className="top-actions"><span className="free-tag">FREE WORKSPACE</span>{user?<button className="account-button" onClick={()=>setPage("Settings")}><UserRound size={17}/>{user.displayName||user.email}</button>:<button className="outline sign-in" onClick={()=>setAuthOpen(true)}><LogIn size={16}/> Sign in</button>}</div></header><div className="page-container">{content()}</div><footer>© 2026 T2S Studio <span>Made for words that matter.</span></footer></main>
    {message&&<div className="toast">{message}</div>}
    {authOpen&&<div className="modal-backdrop" onClick={()=>setAuthOpen(false)}><section className="auth-modal" onClick={e=>e.stopPropagation()}><button className="modal-close" onClick={()=>setAuthOpen(false)}><X/></button><div className="brand-mark large"><AudioLines/></div><span className="eyebrow">WELCOME TO T2S</span><h2>{authMode==="login"?"Sign in to your studio":"Create your account"}</h2><p>Sync your writing workspace across supported T2S experiences.</p><form onSubmit={authenticate}>{authMode==="register"&&<label>Your name<input value={displayName} onChange={e=>setDisplayName(e.target.value)} required/></label>}<label>Email address<input type="email" value={email} onChange={e=>setEmail(e.target.value)} required/></label><label>Password<input type="password" minLength={6} value={password} onChange={e=>setPassword(e.target.value)} required/></label><button className="primary full" type="submit">{authMode==="login"?"Sign in":"Create account"}</button></form><button className="google-button" onClick={googleSignIn}><span>G</span> Continue with Google</button><button className="text-button reset-link" onClick={resetPassword}>Forgot password?</button><div className="auth-switch">{authMode==="login"?"New to T2S Studio?":"Already have an account?"} <button onClick={()=>setAuthMode(authMode==="login"?"register":"login")}>{authMode==="login"?"Create account":"Sign in"}</button></div>{!firebaseReady&&<div className="config-warning">Account sign-in is waiting for the Firebase Web App configuration. Local studio features are available now.</div>}</section></div>}
  </div>;
}
