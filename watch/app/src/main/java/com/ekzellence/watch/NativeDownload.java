package com.ekzellence.watch;
import android.content.*;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.webkit.CookieManager;
import java.net.*;
import java.io.*;
import java.security.*;
import java.security.cert.*;
import javax.net.ssl.*;
final class NativeDownload {
 static void save(MainActivity a,String url,String post,String csrf){if(!url.startsWith("https://127.0.0.1:8443/"))return;String cookies=CookieManager.getInstance().getCookie(url);new Thread(()->{Uri destination=null;try{
  byte[] certificate=new Security(a).certificate();KeyStore trust=KeyStore.getInstance(KeyStore.getDefaultType());trust.load(null);trust.setCertificateEntry("tablet",CertificateFactory.getInstance("X.509").generateCertificate(new ByteArrayInputStream(certificate)));TrustManagerFactory tm=TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());tm.init(trust);SSLContext ssl=SSLContext.getInstance("TLS");ssl.init(null,tm.getTrustManagers(),null);
  HttpsURLConnection h=(HttpsURLConnection)new URL(url).openConnection();h.setSSLSocketFactory(ssl.getSocketFactory());h.setHostnameVerifier((host,s)->host.equals("127.0.0.1"));h.setInstanceFollowRedirects(false);h.setConnectTimeout(10000);h.setReadTimeout(120000);if(cookies!=null)h.setRequestProperty("Cookie",cookies);
  try{if(post!=null){h.setRequestMethod("POST");h.setDoOutput(true);h.setRequestProperty("Content-Type","application/json");h.setRequestProperty("X-Watch-CSRF",csrf);byte[] data=post.getBytes("UTF-8");h.setFixedLengthStreamingMode(data.length);try(OutputStream o=h.getOutputStream()){o.write(data);}}
  if(h.getResponseCode()!=200)throw new IOException("Download failed. Sign in and retry.");String filename="eKZellence-"+System.currentTimeMillis()+".mp4";
  if(Build.VERSION.SDK_INT>=29){ContentValues v=new ContentValues();v.put(MediaStore.Downloads.DISPLAY_NAME,filename);v.put(MediaStore.Downloads.MIME_TYPE,"video/mp4");v.put(MediaStore.Downloads.RELATIVE_PATH,"Download/eKZellence Watch");v.put(MediaStore.Downloads.IS_PENDING,1);destination=a.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,v);if(destination==null)throw new IOException("Cannot create download");try(InputStream in=h.getInputStream();OutputStream out=a.getContentResolver().openOutputStream(destination)){if(out==null)throw new IOException("Cannot save file");copy(in,out);}ContentValues done=new ContentValues();done.put(MediaStore.Downloads.IS_PENDING,0);a.getContentResolver().update(destination,done,null,null);}
  else{File dir=a.getExternalFilesDir("Downloads");if(dir==null)throw new IOException("Storage unavailable");dir.mkdirs();try(InputStream in=h.getInputStream();OutputStream out=new FileOutputStream(new File(dir,filename))){copy(in,out);}}
  a.runOnUiThread(()->android.widget.Toast.makeText(a,"Video saved in Downloads / eKZellence Watch",android.widget.Toast.LENGTH_LONG).show());
  }finally{h.disconnect();}
 }catch(Exception e){if(destination!=null)a.getContentResolver().delete(destination,null,null);a.runOnUiThread(()->android.widget.Toast.makeText(a,"Could not save video. Check storage and sign in again.",android.widget.Toast.LENGTH_LONG).show());}},"save-video").start();}
 static void copy(InputStream in,OutputStream out)throws IOException{byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}
}
