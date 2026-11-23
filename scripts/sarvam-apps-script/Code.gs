const SARVAM_BASE = "https://api.sarvam.ai";
const MAX_SAMPLE_BYTES = 6 * 1024 * 1024;
function doGet() { return out_({ok:true, service:"T2S Sarvam Gateway", version:1}); }
function doPost(e) {
  try {
    const b = JSON.parse(e && e.postData ? e.postData.contents : "{}");
    const u = auth_(b.idToken), action = String(b.action || "");
    if (action === "list") return out_({voices: sarvam_("get", "/voices")});
    if (action === "create") return out_(create_(u.uid, b));
    if (action === "generate") return out_(generate_(u.uid, b));
    throw new Error("Unsupported action.");
  } catch (x) { return out_({error:publicError_(x)}); }
}
function publicError_(x){
 const message=String(x && x.message || x);
 const safePrefixes=[
  "Sign in to T2S again.","Gateway setup incomplete: Firebase verification key missing.",
  "Firebase sign-in expired or invalid. Sign in again.","T2S account is not allowed.",
  "Unsupported action.","Unsupported sample language.","Voice name must be",
  "Invalid gender value.","Unsupported or oversized audio sample.","Sample must be",
  "Generation temporarily unavailable.","Sarvam returned an invalid voice ID.",
  "Invalid saved voice ID.","Text must contain","Choose Telugu or English.",
  "Sarvam returned missing or oversized audio."
 ];
 return safePrefixes.some(prefix=>message.startsWith(prefix))
  ? message.slice(0,180)
  : "Voice service temporarily unavailable. Please try again later.";
}
function auth_(token) {
  if (typeof token !== "string" || token.length < 100) throw new Error("Sign in to T2S again.");
  const p=PropertiesService.getScriptProperties(), k=p.getProperty("FIREBASE_WEB_API_KEY");
  if (!k) throw new Error("Gateway setup incomplete: Firebase verification key missing.");
  const r=UrlFetchApp.fetch("https://identitytoolkit.googleapis.com/v1/accounts:lookup?key="+encodeURIComponent(k),{method:"post",contentType:"application/json",payload:JSON.stringify({idToken:token}),muteHttpExceptions:true});
  if(r.getResponseCode()!==200) throw new Error("Firebase sign-in expired or invalid. Sign in again.");
  const a=JSON.parse(r.getContentText()).users;
  if(!a || !a[0] || !a[0].localId || a[0].disabled) throw new Error("T2S account is not allowed.");
  return {uid:a[0].localId};
}
function key_(){const k=PropertiesService.getScriptProperties().getProperty("SARVAM_API_KEY");if(!k||k.length<8)throw new Error("Gateway setup incomplete: Sarvam key missing.");return k;}
// Reserve allowance atomically before contacting Sarvam so concurrent requests
// cannot all pass a check against the same remaining allowance.
// Reservations are timestamped so an Apps Script timeout cannot strand allowance
// for the rest of the day. All reservation mutations are protected by one script lock.
function quotaReserve_(uid,kind,n){
 const l=LockService.getScriptLock();l.waitLock(15000);
 try{
  const day=Utilities.formatDate(new Date(),"UTC","yyyy-MM-dd"),p=PropertiesService.getScriptProperties(),key="quota:v3:"+day+":"+uid,c=JSON.parse(p.getProperty(key)||"{}");
  const now=Date.now(),pending=(c.pending||[]).filter(x=>x&&now-Number(x.at||0)<10*60*1000);
  const reservedCalls=pending.filter(x=>x.kind==="generate").length;
  const reservedChars=pending.filter(x=>x.kind==="generate").reduce((sum,x)=>sum+Number(x.chars||0),0);
  const reservedCreates=pending.filter(x=>x.kind==="create").length;
  if(kind==="generate"){
   if((c.calls||0)+reservedCalls>=10||(c.chars||0)+reservedChars+n>10000)throw new Error("Generation temporarily unavailable. Please try again later.");
  }else if((c.creates||0)+reservedCreates>=3)throw new Error("Generation temporarily unavailable. Please try again later.");
  const id=Utilities.getUuid();
  pending.push({id:id,at:now,kind:kind,chars:n});
  c.pending=pending;p.setProperty(key,JSON.stringify(c));
  return {key:key,id:id,kind:kind,chars:n};
 }finally{l.releaseLock();}
}
function quotaFinish_(reservation,success){
 const l=LockService.getScriptLock();l.waitLock(15000);
 try{
  const p=PropertiesService.getScriptProperties(),c=JSON.parse(p.getProperty(reservation.key)||"{}");
  const pending=c.pending||[],index=pending.findIndex(x=>x.id===reservation.id);
  if(index<0)return;
  pending.splice(index,1);c.pending=pending;
  if(success&&reservation.kind==="generate"){
   c.calls=(c.calls||0)+1;c.chars=(c.chars||0)+reservation.chars;
  }else if(success)c.creates=(c.creates||0)+1;
  p.setProperty(reservation.key,JSON.stringify(c));
 }finally{l.releaseLock();}
}
// Owner-only recovery helper: run manually in Apps Script editor with the user's Firebase UID.
function adminResetVoiceRegistrationQuota(uid){
 if(!uid||typeof uid!=="string")throw new Error("Provide the Firebase UID.");
 const d=Utilities.formatDate(new Date(),"UTC","yyyy-MM-dd"),p=PropertiesService.getScriptProperties(),k="quota:v3:"+d+":"+uid,c=JSON.parse(p.getProperty(k)||"{}");
 c.creates=0;p.setProperty(k,JSON.stringify(c));return "Today's voice-registration allowance reset for the supplied UID.";
}
function sarvam_(method,path,payload){
 const options={method:method,headers:{"api-subscription-key":key_(),Accept:"application/json"},muteHttpExceptions:true};
 if(payload){options.contentType="application/json";options.payload=JSON.stringify(payload);}
 const r=UrlFetchApp.fetch(SARVAM_BASE+path,options),code=r.getResponseCode();
 if(code<200||code>=300)throw new Error("Generation temporarily unavailable. Please try again later.");
 return JSON.parse(r.getContentText());
}
function create_(uid,b){
 const name=String(b.name||"").trim(),lang=String(b.language||""),gender=String(b.gender||"").trim().toLowerCase(),mime=String(b.mimeType||""),enc=String(b.sampleBase64||"");
 if(name.length<2||name.length>100)throw new Error("Voice name must be 2–100 characters.");
 if(!["te-IN","en-IN","hi-IN"].includes(lang))throw new Error("Unsupported sample language.");
 // Sarvam gender is optional and only accepts male/female. Legacy local profiles
 // may contain Other/Unknown or an empty value; omit those instead of rejecting.
 if(gender && !["male","female","other","unknown"].includes(gender))throw new Error("Invalid gender value.");
 if(!["audio/wav","audio/mpeg","audio/flac","audio/aac","audio/opus","audio/mulaw","audio/alaw","audio/linear16"].includes(mime)||enc.length>8*1024*1024)throw new Error("Unsupported or oversized audio sample. Convert the recording to WAV, MP3, FLAC or AAC.");
 const bytes=Utilities.base64Decode(enc);if(bytes.length<1024||bytes.length>MAX_SAMPLE_BYTES)throw new Error("Sample must be 1 KB–6 MB.");
 const reservation=quotaReserve_(uid,"create",0);
 try{
  const ext={"audio/wav":"wav","audio/mpeg":"mp3","audio/flac":"flac","audio/aac":"aac","audio/opus":"opus","audio/mulaw":"mulaw","audio/alaw":"alaw","audio/linear16":"pcm"};
  const form={name:name,language:lang,remove_background_noise:"true",file:Utilities.newBlob(bytes,mime,"sample."+ext[mime])};
  // Sarvam marks gender optional; omit it rather than infer or force a value.
  const r=UrlFetchApp.fetch(SARVAM_BASE+"/voices/create",{method:"post",headers:{"api-subscription-key":key_(),Accept:"application/json"},payload:form,muteHttpExceptions:true});
  const code=r.getResponseCode();if(code<200||code>=300)throw new Error("Generation temporarily unavailable. Please try again later.");
  const d=JSON.parse(r.getContentText()),id=d.data&&d.data.voice_id;
  if(typeof id!=="string"||!/^svc-[A-Za-z0-9_-]{3,100}$/.test(id))throw new Error("Sarvam returned an invalid voice ID.");
  quotaFinish_(reservation,true);
  return {voiceId:id};
 }catch(error){quotaFinish_(reservation,false);throw error;}
}
function generate_(uid,b){
 const id=String(b.voiceId||""),text=String(b.text||""),lang=String(b.language||"");
 if(!/^svc-[A-Za-z0-9_-]{3,100}$/.test(id))throw new Error("Invalid saved voice ID.");
 if(!text.trim()||text.length>1000)throw new Error("Text must contain 1–1,000 characters.");
 if(!["te-IN","en-IN"].includes(lang))throw new Error("Choose Telugu or English.");
 const reservation=quotaReserve_(uid,"generate",text.length);
 try{
  const boundary="----T2STUDIO"+Utilities.getUuid().replace(/-/g,"");
  const fields={voice_id:id,text:text,language_code:lang};
  let parts="";
  Object.keys(fields).forEach(k=>{parts+="--"+boundary+"\r\nContent-Disposition: form-data; name=\""+k+"\"\r\n\r\n"+fields[k]+"\r\n";});
  parts+="--"+boundary+"--\r\n";
  const r=UrlFetchApp.fetch(SARVAM_BASE+"/voices/clone",{method:"post",contentType:"multipart/form-data; boundary="+boundary,headers:{"api-subscription-key":key_(),Accept:"application/json"},payload:parts,muteHttpExceptions:true});
  const code=r.getResponseCode();if(code<200||code>=300)throw new Error("Generation temporarily unavailable. Please try again later.");
  const d=JSON.parse(r.getContentText()),audio=d.audio_b64||d.audio;
  if(typeof audio!=="string"||audio.length<100||audio.length>20000000)throw new Error("Sarvam returned missing or oversized audio.");
  quotaFinish_(reservation,true);
  return {audioBase64:audio,contentType:"audio/wav"};
 }catch(error){quotaFinish_(reservation,false);throw error;}
}
function out_(v){return ContentService.createTextOutput(JSON.stringify(v)).setMimeType(ContentService.MimeType.JSON);}
