package com.ekzellence.watch;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.net.*;
import java.io.*;
final class CloudLive {
 private static final ExecutorService senders=Executors.newFixedThreadPool(2);
 private static final ConcurrentHashMap<String,AtomicBoolean> busy=new ConcurrentHashMap<>();
 static void offer(WatchService service,String camera,byte[] jpeg){if(service.shuttingDown||!service.store.prefs.getBoolean("cloudEnabled",false)||jpeg.length>524288)return;AtomicBoolean active=busy.computeIfAbsent(camera,k->new AtomicBoolean());if(!active.compareAndSet(false,true))return;final long capture=System.currentTimeMillis();senders.submit(()->{HttpURLConnection h=null;try{String root=service.store.prefs.getString("cloudUrl","");if(root.isEmpty()||System.currentTimeMillis()-capture>2000)return;String token=service.security.decrypt(service.store.prefs.getString("cloudToken",""));h=(HttpURLConnection)new URL(root+"/live/"+camera).openConnection();h.setInstanceFollowRedirects(false);h.setConnectTimeout(3000);h.setReadTimeout(3000);h.setRequestMethod("POST");h.setDoOutput(true);h.setRequestProperty("Authorization","Bearer "+token);h.setRequestProperty("Content-Type","image/jpeg");h.setRequestProperty("X-Frame-Time",""+capture);h.setFixedLengthStreamingMode(jpeg.length);try(OutputStream out=h.getOutputStream()){out.write(jpeg);}if(h.getResponseCode()==204)service.status("cloudLive","Live preview connected");}catch(Exception e){service.status("cloudLive","Live unavailable — check internet");}finally{if(h!=null)h.disconnect();active.set(false);}});}
}
