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
  } catch (x) { return out_({error:String(x && x.message || x).slice(0,400)}); }
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
function quota_(uid,kind,n){
 const l=LockService.getScriptLock();l.waitLock(15000);
 try{
  const d=Utilities.formatDate(new Date(),"UTC","yyyy-MM-dd"),p=PropertiesService.getScriptProperties(),k="quota:"+d+":"+uid,c=JSON.parse(p.getProperty(k)||"{}");
  p.getKeys().filter(x=>x.indexOf("quota:")===0 && x.indexOf(":"+d+":")<0).forEach(x=>p.deleteProperty(x));
  if(kind==="generate"){if((c.calls||0)>=10||(c.chars||0)+n>10000)throw new Error("Daily limit reached: 10 generations or 10,000 characters.");c.calls=(c.calls||0)+1;c.chars=(c.chars||0)+n;}
  else{if((c.creates||0)>=3)throw new Error("Daily voice-registration limit reached (3).");c.creates=(c.creates||0)+1;}
  p.setProperty(k,JSON.stringify(c));
 }finally{l.releaseLock();}
}
function sarvam_(method,path,payload){
 const options={method:method,headers:{"api-subscription-key":key_(),Accept:"application/json"},muteHttpExceptions:true};
 if(payload){options.contentType="application/json";options.payload=JSON.stringify(payload);}
 const r=UrlFetchApp.fetch(SARVAM_BASE+path,options),code=r.getResponseCode();
 if(code<200||code>=300){if(code===401)throw new Error("Sarvam API key rejected.");if(code===402)throw new Error("Sarvam account has insufficient credits.");if(code===403)throw new Error("Sarvam denied access or voice limit reached.");if(code===429)throw new Error("Sarvam rate limit reached.");throw new Error("Sarvam request failed (HTTP "+code+").");}
 return JSON.parse(r.getContentText());
}
function create_(uid,b){
 const name=String(b.name||"").trim(),lang=String(b.language||""),gender=String(b.gender||""),mime=String(b.mimeType||""),enc=String(b.sampleBase64||"");
 if(name.length<2||name.length>100)throw new Error("Voice name must be 2–100 characters.");
 if(!["te-IN","en-IN","hi-IN"].includes(lang))throw new Error("Unsupported sample language.");
 if(!["Male","Female","Other"].includes(gender))throw new Error("Select a valid gender.");
 if(!["audio/wav","audio/mpeg","audio/flac","audio/mp4","audio/aac"].includes(mime)||enc.length>8*1024*1024)throw new Error("Unsupported or oversized audio sample.");
 const bytes=Utilities.base64Decode(enc);if(bytes.length<1024||bytes.length>MAX_SAMPLE_BYTES)throw new Error("Sample must be 1 KB–6 MB.");
 quota_(uid,"create",0);
 const ext={"audio/wav":"wav","audio/mpeg":"mp3","audio/flac":"flac","audio/mp4":"m4a","audio/aac":"aac"};
 const form={name:name,language:lang,remove_background_noise:"true",file:Utilities.newBlob(bytes,mime,"sample."+ext[mime])};
 if(gender!=="Other")form.gender=gender.toLowerCase();
 const r=UrlFetchApp.fetch(SARVAM_BASE+"/voices/create",{method:"post",headers:{"api-subscription-key":key_(),Accept:"application/json"},payload:form,muteHttpExceptions:true});
 const c=r.getResponseCode();if(c<200||c>=300)throw new Error("Voice registration failed (HTTP "+c+").");
 const d=JSON.parse(r.getContentText()),id=d.data&&d.data.voice_id;
 if(typeof id!=="string"||!/^svc-[A-Za-z0-9_-]{3,100}$/.test(id))throw new Error("Sarvam returned an invalid voice ID.");
 return {voiceId:id};
}
function generate_(uid,b){
 const id=String(b.voiceId||""),text=String(b.text||""),lang=String(b.language||"");
 if(!/^svc-[A-Za-z0-9_-]{3,100}$/.test(id))throw new Error("Invalid saved voice ID.");
 if(!text.trim()||text.length>1000)throw new Error("Text must contain 1–1,000 characters.");
 if(!["te-IN","en-IN"].includes(lang))throw new Error("Choose Telugu or English.");
 quota_(uid,"generate",text.length);
 const boundary="----T2STUDIO"+Utilities.getUuid().replace(/-/g,"");
 const fields={voice_id:id,text:text,language_code:lang};
 let parts="";
 Object.keys(fields).forEach(k=>{parts+="--"+boundary+"\r\nContent-Disposition: form-data; name=\""+k+"\"\r\n\r\n"+fields[k]+"\r\n";});
 parts+="--"+boundary+"--\r\n";
 const r=UrlFetchApp.fetch(SARVAM_BASE+"/voices/clone",{method:"post",contentType:"multipart/form-data; boundary="+boundary,headers:{"api-subscription-key":key_(),Accept:"application/json"},payload:parts,muteHttpExceptions:true});
 const code=r.getResponseCode();if(code<200||code>=300)throw new Error("Speech generation failed (HTTP "+code+").");
 const d=JSON.parse(r.getContentText()),audio=d.audio_b64||d.audio;
 if(typeof audio!=="string"||audio.length<100||audio.length>20000000)throw new Error("Sarvam returned missing or oversized audio.");
 return {audioBase64:audio,contentType:"audio/wav"};
}
function out_(v){return ContentService.createTextOutput(JSON.stringify(v)).setMimeType(ContentService.MimeType.JSON);}
