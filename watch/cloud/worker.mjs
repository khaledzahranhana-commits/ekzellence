/** Private Cloudflare R2 backup API. No browser public access or public bucket. */
const headers={'Cache-Control':'no-store','X-Content-Type-Options':'nosniff'};
async function allowed(request,env){
 const supplied=request.headers.get('Authorization')||'';
 if(!env.UPLOAD_TOKEN||env.UPLOAD_TOKEN.length<24)return false;
 const a=new Uint8Array(await crypto.subtle.digest('SHA-256',new TextEncoder().encode(supplied)));
 const b=new Uint8Array(await crypto.subtle.digest('SHA-256',new TextEncoder().encode('Bearer '+env.UPLOAD_TOKEN)));
 let diff=0;for(let i=0;i<a.length;i++)diff|=a[i]^b[i];return diff===0;
}
export default {
 async fetch(request,env){
  if(!await allowed(request,env))return new Response('Unauthorized',{status:401,headers});
  const url=new URL(request.url),match=url.pathname.match(/^\/recordings\/(usb|[a-f0-9-]{36})\/(\d{13})\.mp4$/);
  if(!match)return new Response('Not found',{status:404,headers});
  const key=match[1]+'/'+match[2]+'.mp4';
  if(request.method==='PUT'){
   const start=Number(match[2]),end=Number(request.headers.get('X-Recording-End')),size=Number(request.headers.get('Content-Length'));
   if(!Number.isSafeInteger(end)||end<start||end>start+3600000||!Number.isSafeInteger(size)||size<1||size>250*1024*1024)return new Response('Invalid recording metadata or size',{status:400,headers});
   // R2 is private. Existing keys overwrite idempotently if an upload is retried.
   await env.RECORDINGS.put(key,request.body,{httpMetadata:{contentType:'video/mp4'},customMetadata:{camera:match[1],start:String(start),end:String(end)}});
   return new Response(null,{status:204,headers});
  }
  if(request.method==='GET'){
   const object=await env.RECORDINGS.get(key);if(!object)return new Response('Not found',{status:404,headers});
   return new Response(object.body,{headers:{...headers,'Content-Type':'video/mp4','Content-Disposition':'attachment; filename="'+match[2]+'.mp4"'}});
  }
  return new Response('Method not allowed',{status:405,headers});
 },
 async scheduled(event,env,ctx){
  const cutoff=Date.now()-30*86400000;let cursor;
  do{const page=await env.RECORDINGS.list({limit:1000,cursor,include:['customMetadata']});const keys=page.objects.filter(o=>Number(o.customMetadata?.end)>0&&Number(o.customMetadata.end)<cutoff).map(o=>o.key);if(keys.length)await env.RECORDINGS.delete(keys);cursor=page.truncated?page.cursor:undefined;}while(cursor);
 }
};
