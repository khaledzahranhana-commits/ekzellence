package com.ekzellence.watch;
import android.app.*;
import android.os.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.*;
import android.net.http.SslError;
import android.webkit.*;
import android.widget.*;
import java.security.MessageDigest;
public class MainActivity extends Activity {
 WebView web;Handler main=new Handler();
 public void onCreate(Bundle b){super.onCreate(b);if(checkSelfPermission(android.Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED){requestPermissions(Build.VERSION.SDK_INT>=33?new String[]{android.Manifest.permission.CAMERA,android.Manifest.permission.POST_NOTIFICATIONS}:new String[]{android.Manifest.permission.CAMERA},44);}else launch();}
 public void onRequestPermissionsResult(int r,String[] p,int[] g){super.onRequestPermissionsResult(r,p,g);if(r==44&&g.length>0&&g[0]==PackageManager.PERMISSION_GRANTED)launch();else new AlertDialog.Builder(this).setMessage("Camera permission is required to connect the USB camera. Enable it in App settings.").setPositiveButton("Settings",(d,w)->startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName())))).show();}
 void launch(){startForegroundService(new Intent(this,WatchService.class));web=new WebView(this);setContentView(web);web.getSettings().setJavaScriptEnabled(true);web.getSettings().setDomStorageEnabled(true);web.getSettings().setAllowFileAccess(false);web.getSettings().setAllowContentAccess(false);web.addJavascriptInterface(new Bridge(),"Tablet");web.setDownloadListener((url,agent,disposition,mime,length)->NativeDownload.save(this,url,null,null));web.setWebViewClient(new WebViewClient(){
 public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){return !r.getUrl().toString().startsWith("https://127.0.0.1:8443/");}
 public void onReceivedSslError(WebView v,SslErrorHandler h,SslError e){try{byte[] cert=android.net.http.SslCertificate.saveState(e.getCertificate()).getByteArray("x509-certificate");if(e.getUrl().startsWith("https://127.0.0.1:8443/")&&MessageDigest.isEqual(cert,new Security(MainActivity.this).certificate())){h.proceed();return;}}catch(Exception ignored){}h.cancel();}
 });waitForServer(0);}
 void waitForServer(int attempt){if(WatchService.instance!=null&&WatchService.instance.server!=null){web.loadUrl("https://127.0.0.1:8443/");}else if(attempt<40)main.postDelayed(()->waitForServer(attempt+1),250);else Toast.makeText(this,"Server failed to start. Reopen the app.",Toast.LENGTH_LONG).show();}
 class Bridge{
 @JavascriptInterface public void exportClip(long id,double from,double to,String csrf){try{String body=new org.json.JSONObject().put("id",id).put("from",from).put("to",to).toString();NativeDownload.save(MainActivity.this,"https://127.0.0.1:8443/clip",body,csrf);}catch(Exception ignored){}}
 @JavascriptInterface public void chooseStorage(){runOnUiThread(()->{WatchService s=WatchService.instance;if(s!=null&&s.enabled.containsValue(true)){Toast.makeText(MainActivity.this,"Stop all cameras before changing storage",Toast.LENGTH_LONG).show();return;}Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);startActivityForResult(i,51);});}
 @JavascriptInterface public void internalStorage(){runOnUiThread(()->{WatchService s=WatchService.instance;if(s!=null&&!s.enabled.containsValue(true))s.store.prefs.edit().remove("tree").apply();else Toast.makeText(MainActivity.this,"Stop all cameras before changing storage",Toast.LENGTH_LONG).show();});}
 }
 public void onActivityResult(int req,int result,Intent data){super.onActivityResult(req,result,data);if(req==51&&result==RESULT_OK&&data!=null){try{Uri u=data.getData();getContentResolver().takePersistableUriPermission(u,Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);WatchService.instance.store.prefs.edit().putString("tree",u.toString()).commit();Toast.makeText(this,"Recording folder selected",Toast.LENGTH_SHORT).show();}catch(Exception e){Toast.makeText(this,"Folder access was not granted",Toast.LENGTH_LONG).show();}}}
 public void onBackPressed(){if(web!=null)web.evaluateJavascript("window.goBack ? goBack() : null",null);else super.onBackPressed();}
 public void onDestroy(){if(web!=null){web.removeJavascriptInterface("Tablet");web.destroy();}super.onDestroy();}
}
