import {portal} from './portal.mjs';
const baseHeaders={'Cache-Control':'no-store','X-Content-Type-Options':'nosniff','X-Frame-Options':'DENY','Referrer-Policy':'no-referrer'};
const enc=new TextEncoder();
const b64=b=>btoa(String.fromCharCode(...new Uint8Array(b))).replace(/\+/g,'-').replace(/\//g,'_').replace(/=+$/,'');
const un64=s=>Uint8Array.from(atob(s.replace(/-/g,'+').replace(/_/g,'/')+'='.repeat((4-s.length%4)%4)),c=>c.charCodeAt(0));
const random=()=>b64(crypto.getRandomValues(new Uint8Array(24)));
const result=(j,status=200,extra={})=>new Response(JSON.stringify(j),{status,headers:{...baseHeaders,'Content-Type':'application/json',...extra}});
async function equal(a,b){const [x,y]=await Promise.all([a,b].map(v=>crypto.subtle.digest('SHA-256',enc.encode(v))));const u=new Uint8Array(x),v=new Uint8Array(y);let d=0;for(let i=0;i<u.length;i++)d|=u[i]^v[i];return d===0;}
export async function verifyPassword(password,record){try{if(typeof password!=='string'||password.length>200)return false;const [count,salt,hash]=record.split('$');if(Number(count)!==100000)return false;const key=await crypto.subtle.importKey('raw',enc.encode(password),'PBKDF2',false,['deriveBits']);const bits=await crypto.subtle.deriveBits({name:'PBKDF2',salt:un64(salt),iterations:100000,hash:'SHA-256'},key,256);return equal(b64(bits),hash);}catch{return false;}}
async function hmac(env,payload){const k=await crypto.subtle.importKey('raw',enc.encode(env.SESSION_SECRET),{name:'HMAC',hash:'SHA-256'},false,['sign']);return b64(await crypto.subtle.sign('HMAC',k,enc.encode(payload)));}
async function credentialVersion(env){return b64(await crypto.subtle.digest('SHA-256',enc.encode(env.VIEW_PASSWORD_RECORD||'')));}
async function session(request,env){if(!env.SESSION_SECRET||env.SESSION_SECRET.length<32)return null;const raw=(request.headers.get('Cookie')||'').split(';').map(v=>v.trim()).find(v=>v.startsWith('watch_cloud='))?.slice(12);try{const [body,sig]=raw.split('.');if(!await equal(await hmac(env,body),sig))return null;const data=JSON.parse(new TextDecoder().decode(un64(body)));if(data.exp<Date.now()||data.aud!=='ekzellence-cloud'||data.version!==await credentialVersion(env))return null;return data;}catch{return null;}}
async function body(request){if(!request.headers.get('Content-Type')?.startsWith('application/json'))throw Error('JSON required');const origin=request.headers.get('Origin');if(origin&&origin!==new URL(request.url).origin)throw Error('Invalid origin');const text=await request.text();if(text.length>4096)throw Error('Request too large');return JSON.parse(text);}
async function gate(request,env,mode,success=false){if(!env.SECURITY_GATE)throw Error('Security gate not configured');const ip=request.headers.get('CF-Connecting-IP')||'unknown';const key=b64(await crypto.subtle.digest('SHA-256',enc.encode(mode+':'+ip)));const stub=env.SECURITY_GATE.get(env.SECURITY_GATE.idFromName('watch-auth'));const r=await stub.fetch('https://internal/attempt',{method:'POST',body:JSON.stringify({key,success})});return r.status===200;}
function ready(env){return !!(env.VIEW_PASSWORD_RECORD&&env.DELETE_PIN_RECORD&&env.SESSION_SECRET?.length>=32&&env.SECURITY_GATE);}
function validCamera(id){return /^(usb|[a-f0-9-]{36})$/.test(id);}
export default {
 async fetch(request,env){try{
 const url=new URL(request.url),path=url.pathname,method=request.method;
 if(method==='GET'&&path==='/'){const nonce=random();return new Response(portal.replaceAll('__NONCE__',nonce),{headers:{...baseHeaders,'Content-Type':'text/html; charset=utf-8','Content-Security-Policy':`default-src 'self'; script-src 'nonce-${nonce}'; style-src 'unsafe-inline'; img-src 'self' blob:; media-src 'self' blob:; connect-src 'self'; frame-ancestors 'none'; base-uri 'none'`}});}
 if(path==='/api/login'&&method==='POST'){if(!ready(env))return result({error:'Cloud access is not configured'},503);const data=await body(request);if(!await gate(request,env,'login'))return result({error:'Too many attempts. Wait one minute.'},429);if(!await verifyPassword(data.password,env.VIEW_PASSWORD_RECORD))return result({error:'Incorrect cloud password'},401);await gate(request,env,'login',true);const csrf=random(),record={exp:Date.now()+3600000,aud:'ekzellence-cloud',csrf,version:await credentialVersion(env)};const payload=b64(enc.encode(JSON.stringify(record)));return result({csrf},200,{'Set-Cookie':`watch_cloud=${payload}.${await hmac(env,payload)}; Path=/; Secure; HttpOnly; SameSite=Strict; Max-Age=3600`});}
 const live=path.match(/^\/live\/(usb|[a-f0-9-]{36})$/);
 if(live&&method==='POST'){
 if(!env.UPLOAD_TOKEN||env.UPLOAD_TOKEN.length<24||!await equal(request.headers.get('Authorization')||'','Bearer '+env.UPLOAD_TOKEN))return result({error:'Upload authorization required'},401);
 const length=Number(request.headers.get('Content-Length')),time=Number(request.headers.get('X-Frame-Time'));if(!Number.isSafeInteger(length)||length<4||length>524288||!Number.isFinite(time)||Math.abs(time-Date.now())>10000)return result({error:'Invalid live frame'},400);
 const frame=await request.arrayBuffer();if(frame.byteLength!==length||new Uint8Array(frame)[0]!==255||new Uint8Array(frame)[1]!==216)return result({error:'Invalid JPEG'},400);
 const target=env.LIVE_FRAMES.get(env.LIVE_FRAMES.idFromName(live[1]));return target.fetch('https://internal/frame',{method:'POST',body:frame});
 }
 const match=path.match(/^\/recordings\/(usb|[a-f0-9-]{36})\/(\d{13})\.mp4$/);
 if(match&&method==='PUT'){
  if(!env.UPLOAD_TOKEN||env.UPLOAD_TOKEN.length<24||!await equal(request.headers.get('Authorization')||'','Bearer '+env.UPLOAD_TOKEN))return result({error:'Upload authorization required'},401);
  const key=match[1]+'/'+match[2]+'.mp4',start=Number(match[2]),end=Number(request.headers.get('X-Recording-End')),size=Number(request.headers.get('Content-Length')),sha=request.headers.get('X-Recording-SHA256')||'';
  if(!/^[a-f0-9]{64}$/.test(sha)||!Number.isSafeInteger(end)||end<start||end>start+3600000||end>Date.now()+300000||!Number.isSafeInteger(size)||size<1||size>250*1024*1024)return result({error:'Invalid recording metadata'},400);
  const existing=await env.RECORDINGS.head(key);if(existing){if(existing.size!==size||existing.customMetadata?.sha256!==sha)return result({error:'Existing recording is immutable'},409);return result({key,size,sha256:sha});}
  const hash=Uint8Array.from(sha.match(/../g),v=>parseInt(v,16));let cameraName;try{cameraName=decodeURIComponent(request.headers.get('X-Camera-Name')||match[1]).slice(0,60);}catch{cameraName=match[1];}
  const object=await env.RECORDINGS.put(key,request.body,{onlyIf:{etagDoesNotMatch:'*'},sha256:hash.buffer,httpMetadata:{contentType:'video/mp4'},customMetadata:{camera:match[1],cameraName,start:String(start),end:String(end),sha256:sha}});
  if(!object)return result({error:'Concurrent upload; retry safely'},409);return result({key,size,sha256:sha},201);
 }
 const viewer=await session(request,env);if(!viewer)return result({error:'Cloud sign-in required'},401);
 if(live&&method==='GET'){const target=env.LIVE_FRAMES.get(env.LIVE_FRAMES.idFromName(live[1]));const response=await target.fetch('https://internal/frame');return new Response(response.body,{status:response.status,headers:{...baseHeaders,'Content-Type':response.status===200?'image/jpeg':'text/plain','X-Frame-Time':response.headers.get('X-Frame-Time')||''}});}
 if(method==='GET'&&path==='/api/session')return result({csrf:viewer.csrf});
 if(method==='POST'||method==='DELETE'){if(request.headers.get('X-Watch-CSRF')!==viewer.csrf)return result({error:'Sign in again'},403);}
 if(method==='POST'&&path==='/api/logout'){await body(request);return result({ok:true},200,{'Set-Cookie':'watch_cloud=; Path=/; Secure; HttpOnly; SameSite=Strict; Max-Age=0'});}
 if(method==='GET'&&path==='/api/recordings'){
  const camera=url.searchParams.get('camera')||'';if(camera&&!validCamera(camera))return result({error:'Invalid camera'},400);const since=Number(url.searchParams.get('since')||0),until=Number(url.searchParams.get('until')||Number.MAX_SAFE_INTEGER);const page=await env.RECORDINGS.list({limit:500,prefix:camera?camera+'/':'',cursor:url.searchParams.get('cursor')||undefined,include:['customMetadata']});const recordings=page.objects.filter(o=>o.customMetadata&&Number(o.customMetadata.end)>=since&&Number(o.customMetadata.start)<=until).map(o=>({key:o.key,camera:o.customMetadata.camera,name:o.customMetadata.cameraName||o.customMetadata.camera,start:Number(o.customMetadata.start),end:Number(o.customMetadata.end),size:o.size})).sort((a,b)=>b.start-a.start);return result({recordings,cursor:page.truncated?page.cursor:null});
 }
 if(match&&(method==='GET'||method==='HEAD')){
  const key=match[1]+'/'+match[2]+'.mp4',head=await env.RECORDINGS.head(key);if(!head)return result({error:'Recording not found'},404);const h={...baseHeaders,'Content-Type':'video/mp4','Accept-Ranges':'bytes','Content-Length':String(head.size)};if(url.searchParams.get('download')==='1')h['Content-Disposition']=`attachment; filename="${match[1]}-${match[2]}.mp4"`;
  if(method==='HEAD')return new Response(null,{headers:h});let range;const raw=request.headers.get('Range');if(raw){const m=raw.match(/^bytes=(\d*)-(\d*)$/);if(!m)return new Response(null,{status:416,headers:{...baseHeaders,'Content-Range':`bytes */${head.size}`}});let a,b;if(!m[1]){const n=Number(m[2]);if(n<1)return result({error:'Invalid range'},416);a=Math.max(0,head.size-n);b=head.size-1;}else{a=Number(m[1]);b=m[2]?Math.min(head.size-1,Number(m[2])):head.size-1;}if(a>=head.size||a>b)return new Response(null,{status:416,headers:{...baseHeaders,'Content-Range':`bytes */${head.size}`}});range={offset:a,length:b-a+1};h['Content-Range']=`bytes ${a}-${b}/${head.size}`;h['Content-Length']=String(range.length);}
  const object=await env.RECORDINGS.get(key,range?{range}:undefined);return new Response(object.body,{status:range?206:200,headers:h});
 }
 if(match&&method==='DELETE'){
  const data=await body(request);if(!await gate(request,env,'delete'))return result({error:'Too many deletion attempts. Wait one minute.'},429);if(!await verifyPassword(data.pin,env.DELETE_PIN_RECORD))return result({error:'Incorrect deletion PIN'},403);await gate(request,env,'delete',true);await env.RECORDINGS.delete(match[1]+'/'+match[2]+'.mp4');return result({ok:true});
 }
 return result({error:'Not found'},404);
 }catch(e){return result({error:'Cloud request could not be completed'},503);}},
 async scheduled(event,env,ctx){const cutoff=Date.now()-30*86400000;let cursor;do{const page=await env.RECORDINGS.list({limit:1000,cursor,include:['customMetadata']});const keys=page.objects.filter(o=>Number(o.customMetadata?.end)>0&&Number(o.customMetadata.end)<cutoff).map(o=>o.key);if(keys.length)await env.RECORDINGS.delete(keys);cursor=page.truncated?page.cursor:undefined;}while(cursor);}
};
/** Persisted attempt counters survive Worker restarts; inaccessible as a public endpoint. */
export class SecurityGate {
 constructor(state,env){this.state=state;}
 async fetch(request){const {key,success}=await request.json();if(!/^[a-zA-Z0-9_-]{43}$/.test(key))return new Response(null,{status:400});const now=Date.now();const allowed=await this.state.storage.transaction(async tx=>{let value=await tx.get(key);if(!value||value.until<=now)value={count:0,until:now+60000};if(value.count>=5)return false;if(success)value.count=0;else value.count++;await tx.put(key,value);return true;});if(!await this.state.storage.getAlarm())await this.state.storage.setAlarm(now+120000);return new Response(null,{status:allowed?200:429});}
 async alarm(){const now=Date.now();let cursor;do{const rows=await this.state.storage.list({startAfter:cursor,limit:500});if(!rows.size)break;for(const [key,value]of rows){if(value.until<=now)await this.state.storage.delete(key);cursor=key;}if(rows.size<500)break;}while(cursor);}
}

/** Latest live JPEG only; transient memory, never the permanent recording archive. */
export class LiveFrames {
 constructor(state,env){this.frame=null;this.at=0;}
 async fetch(request){if(request.method==='POST'){const data=await request.arrayBuffer();if(data.byteLength>524288)return new Response(null,{status:413});this.frame=data;this.at=Date.now();return new Response(null,{status:204});}if(!this.frame||Date.now()-this.at>5000)return new Response('Live offline',{status:404});return new Response(this.frame,{headers:{'Content-Type':'image/jpeg','Cache-Control':'no-store','X-Frame-Time':String(this.at)}});}
}
