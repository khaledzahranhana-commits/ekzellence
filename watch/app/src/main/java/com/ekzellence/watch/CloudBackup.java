package com.ekzellence.watch;
import android.net.*;
import android.database.Cursor;
import android.content.ContentValues;
import java.net.*;
import java.io.*;
/** Opt-in only. Uploads finalized files to the private R2 Worker; Wi-Fi only. */
final class CloudBackup {
 static void run(WatchService s){if(!s.store.prefs.getBoolean("cloudEnabled",false))return;ConnectivityManager cm=s.getSystemService(ConnectivityManager.class);NetworkCapabilities caps=cm.getNetworkCapabilities(cm.getActiveNetwork());if(caps==null||!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)){s.status("cloud","Waiting for Wi-Fi");return;}String root=s.store.prefs.getString("cloudUrl","");if(root.isEmpty())return;
 try(Cursor c=s.store.getReadableDatabase().rawQuery("SELECT id,camera,uri,start,end,size FROM recordings WHERE cloud=0 ORDER BY start LIMIT 2",null)){while(c.moveToNext()){long id=c.getLong(0);String token=s.security.decrypt(s.store.prefs.getString("cloudToken",""));HttpURLConnection h=(HttpURLConnection)new URL(root+"/recordings/"+c.getString(1)+"/"+c.getLong(3)+".mp4").openConnection();try{h.setInstanceFollowRedirects(false);h.setConnectTimeout(10000);h.setReadTimeout(120000);h.setRequestMethod("PUT");h.setDoOutput(true);h.setRequestProperty("Authorization","Bearer "+token);h.setRequestProperty("Content-Type","video/mp4");h.setRequestProperty("X-Recording-End",""+c.getLong(4));h.setFixedLengthStreamingMode(c.getLong(5));Uri uri=Uri.parse(c.getString(2));try(InputStream in=uri.getScheme().equals("file")?new FileInputStream(uri.getPath()):s.getContentResolver().openInputStream(uri);OutputStream out=h.getOutputStream()){if(in==null)throw new IOException();byte[] buf=new byte[65536];int n;while((n=in.read(buf))>=0)out.write(buf,0,n);}if(h.getResponseCode()<200||h.getResponseCode()>=300)throw new IOException("Upload refused: "+h.getResponseCode());ContentValues v=new ContentValues();v.put("cloud",1);s.store.getWritableDatabase().update("recordings",v,"id=?",new String[]{""+id});s.status("cloud","Backup up to date");}finally{h.disconnect();}}}catch(Exception e){s.status("cloud","Backup pending — check cloud settings and internet");}}
}
