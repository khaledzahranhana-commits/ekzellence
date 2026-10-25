package com.ekzellence.watch;
import android.app.*;
import android.content.*;
import android.hardware.usb.*;
import android.os.*;
import android.graphics.*;
import com.herohan.uvcapp.*;
import com.serenegiant.usb.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

public class WatchService extends Service {
 public static volatile WatchService instance;
 public Store store;Security security;WatchServer server;
 final Handler main=new Handler(Looper.getMainLooper());
 final ExecutorService io=Executors.newFixedThreadPool(2);
 final Map<String,byte[]> frames=new ConcurrentHashMap<>();
 final Map<String,Long> frameTimes=new ConcurrentHashMap<>();
 final Map<String,String> states=new ConcurrentHashMap<>();
 final Map<String,Boolean> enabled=new ConcurrentHashMap<>();
 final Map<String,RtspCamera> network=new ConcurrentHashMap<>();
 CameraHelper usb;UsbDevice usbDevice;PowerManager.WakeLock wake;
 final java.util.concurrent.atomic.AtomicBoolean snapshotBusy=new java.util.concurrent.atomic.AtomicBoolean();
 volatile boolean usbOpen,usbRecording,usbFinishing,shuttingDown;volatile long snapshotTime;File usbFile;long usbStart;long lastCleanup;long lastCloud;long lastWake;boolean cloudBusy;
 final Runnable rotate=()->{if(usb!=null&&usbRecording){usb.stopRecording();usbRecording=false;usbFinishing=true;}};
 public void onCreate(){super.onCreate();instance=this;security=new Security(this);store=new Store(this,security);
 NotificationChannel channel=new NotificationChannel("capture","Camera recording",NotificationManager.IMPORTANCE_LOW);getSystemService(NotificationManager.class).createNotificationChannel(channel);
 Intent open=new Intent(this,MainActivity.class);PendingIntent pi=PendingIntent.getActivity(this,0,open,PendingIntent.FLAG_IMMUTABLE);
 startForeground(7,new Notification.Builder(this,"capture").setContentTitle("eKZellence Watch").setContentText("Camera service active · tap to manage").setSmallIcon(android.R.drawable.presence_video_online).setContentIntent(pi).setOngoing(true).build());
 wake=((PowerManager)getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"eKZellence:Recording");wake.setReferenceCounted(false);
 try{server=new WatchServer(this);server.start();}catch(Exception e){status("system","Local server failed: "+e.getClass().getSimpleName());}
 usb=new CameraHelper();usb.setStateCallback(new ICameraHelper.StateCallback(){
 public void onAttach(UsbDevice d){if(usbDevice==null){usbDevice=d;if(isEnabled("usb"))usb.selectDevice(d);}}
 public void onDeviceOpen(UsbDevice d,boolean first){if(isEnabled("usb"))usb.openCamera();}
 public void onCameraOpen(UsbDevice d){usbOpen=true;VideoCaptureConfig cfg=usb.getVideoCaptureConfig();cfg.setAudioCaptureEnable(false).setBitRate(1000000).setVideoFrameRate(15).setIFrameInterval(2);usb.setVideoCaptureConfig(cfg);
 usb.setFrameCallback(buffer->{long now=System.currentTimeMillis();if(now-snapshotTime<500||shuttingDown)return;Size size=usb.getPreviewSize();if(size==null)return;int w=size.width,h=size.height,need=w*h*3/2;if(buffer.remaining()<need)return;if(!snapshotBusy.compareAndSet(false,true))return;byte[] nv=new byte[need];buffer.get(nv);snapshotTime=now;io.submit(()->{try{ByteArrayOutputStream b=new ByteArrayOutputStream();new YuvImage(nv,ImageFormat.NV21,w,h,null).compressToJpeg(new Rect(0,0,w,h),60,b);snapshot("usb",b.toByteArray());}catch(Exception ignored){}finally{snapshotBusy.set(false);}});},UVCCamera.PIXEL_FORMAT_NV21);
 usb.startPreview();status("usb","Live · USB");startUsbRecording();}
 public void onCameraClose(UsbDevice d){usbOpen=false;status("usb","Camera closed");}
 public void onDeviceClose(UsbDevice d){usbOpen=false;}
 public void onDetach(UsbDevice d){if(usbDevice!=null&&usbDevice.equals(d)){usbDevice=null;usbOpen=false;status("usb","Disconnected — reconnect USB camera");}}
 public void onCancel(UsbDevice d){status("usb","USB permission denied — reconnect and allow");}
 public void onError(UsbDevice d,CameraException e){status("usb","USB camera error — reconnect and retry");}
 });
 main.postDelayed(this::maintenance,1000);
 }
 void snapshot(String id,byte[] jpg){frames.put(id,jpg);frameTimes.put(id,System.currentTimeMillis());}
 void status(String id,String s){states.put(id,s);}
 boolean isEnabled(String id){return Boolean.TRUE.equals(enabled.get(id));}
 synchronized void toggle(String id,boolean on)throws Exception{boolean found=false;JSONObject camera=null;JSONArray a=store.cameras(true);for(int i=0;i<a.length();i++)if(a.getJSONObject(i).getString("id").equals(id)){camera=a.getJSONObject(i);found=true;break;}if(!found)throw new Exception("Camera not found");enabled.put(id,on);store.enabled(id,on);final JSONObject cam=camera;main.post(()->{if(id.equals("usb")){if(on){if(usbOpen)startUsbRecording();else{if(usbDevice==null){List<UsbDevice> list=usb.getDeviceList();if(list!=null&&!list.isEmpty())usbDevice=list.get(0);}if(usbDevice!=null)usb.selectDevice(usbDevice);else status(id,"Connect your USB camera");}}else{main.removeCallbacks(rotate);if(usbRecording){usbFinishing=true;usbRecording=false;usb.stopRecording();}status(id,"Stopped");}}else{RtspCamera old=network.remove(id);if(old!=null)old.stop();if(on){RtspCamera n=new RtspCamera(id,cam.optString("source"),this);network.put(id,n);new Thread(n,"rtsp-"+id).start();}else status(id,"Stopped");}});}
 void startUsbRecording(){if(shuttingDown||usb==null||!usbOpen||usbRecording||usbFinishing||!isEnabled("usb"))return;if(!store.hasRoom()){status("usb","Storage low — recording paused");return;}usbFile=store.temp("usb");final File f=usbFile;usbStart=System.currentTimeMillis();final long started=usbStart;usbRecording=true;
 usb.startRecording(new VideoCapture.OutputFileOptions.Builder(f).build(),new VideoCapture.OnVideoCaptureCallback(){
 public void onStart(){status("usb","Recording · USB · 1 Mbps");main.postDelayed(rotate,60000);}
 public void onVideoSaved(VideoCapture.OutputFileResults result){usbRecording=false;usbFinishing=true;main.removeCallbacks(rotate);long end=System.currentTimeMillis();io.submit(()->{try{store.finish("usb",f,started,end);}catch(Exception e){status("usb","Save failed — recording remains in pending");enabled.put("usb",false);store.enabled("usb",false);}finally{usbFinishing=false;main.post(()->{if(!shuttingDown)startUsbRecording();});}});}
 public void onError(int code,String message,Throwable cause){usbRecording=false;usbFinishing=false;main.removeCallbacks(rotate);status("usb","Recording error — reconnect camera and retry");enabled.put("usb",false);store.enabled("usb",false);}
 });}
 void maintenance(){if(shuttingDown)return;long now=System.currentTimeMillis();if(enabled.containsValue(true)){if(!wake.isHeld()||now-lastWake>300000){wake.acquire(600000);lastWake=now;}}else if(wake.isHeld())wake.release();if(now-lastCleanup>=86400000L){lastCleanup=now;io.submit(()->store.cleanup(System.currentTimeMillis()));}if(now-lastCloud>60000&&!cloudBusy){lastCloud=now;cloudBusy=true;io.submit(()->{try{CloudBackup.run(this);}finally{cloudBusy=false;}});}if(!store.hasRoom()&&usbRecording){main.removeCallbacks(rotate);rotate.run();}if(isEnabled("usb")&&usbOpen&&!usbRecording&&!usbFinishing)startUsbRecording();main.postDelayed(this::maintenance,5000);}
 JSONObject state()throws Exception{JSONObject j=new JSONObject();JSONArray a=store.cameras(false);for(int i=0;i<a.length();i++){JSONObject c=a.getJSONObject(i);String id=c.getString("id");c.put("enabled",isEnabled(id));c.put("status",states.getOrDefault(id,"Ready"));long time=frameTimes.getOrDefault(id,0L);c.put("lastFrame",time);c.put("live",System.currentTimeMillis()-time<5000&&isEnabled(id));}j.put("cameras",a);j.put("free",store.free());j.put("internalFree",getFilesDir().getUsableSpace());j.put("retentionDays",30);j.put("storage",store.prefs.getString("tree","").isEmpty()?"Tablet":"Selected SD folder");j.put("addresses",addresses());j.put("cloudConfigured",store.prefs.getBoolean("cloudEnabled",false));j.put("cloudStatus",states.getOrDefault("cloud","Cloud backup off"));j.put("version","0.1.0-beta");j.put("system",states.getOrDefault("system",""));return j;}
 JSONArray addresses(){JSONArray a=new JSONArray();try{Enumeration<java.net.NetworkInterface> nets=java.net.NetworkInterface.getNetworkInterfaces();while(nets.hasMoreElements()){java.net.NetworkInterface n=nets.nextElement();Enumeration<java.net.InetAddress> ips=n.getInetAddresses();while(ips.hasMoreElements()){java.net.InetAddress ip=ips.nextElement();if(ip instanceof java.net.Inet4Address&&!ip.isLoopbackAddress())a.put("https://"+ip.getHostAddress()+":8443");}}}catch(Exception ignored){}return a;}
 public int onStartCommand(Intent i,int flags,int id){return START_NOT_STICKY;}
 public android.os.IBinder onBind(Intent i){return null;}
 public void onDestroy(){shuttingDown=true;for(RtspCamera n:network.values())n.stop();main.removeCallbacksAndMessages(null);if(usb!=null){if(usbRecording)usb.stopRecording();main.postDelayed(()->usb.releaseAll(),1500);}if(server!=null)server.stop();if(wake!=null&&wake.isHeld())wake.release();instance=null;super.onDestroy();}
}
