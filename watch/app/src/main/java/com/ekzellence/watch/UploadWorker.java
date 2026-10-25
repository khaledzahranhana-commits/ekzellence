package com.ekzellence.watch;
import android.content.Context;
import androidx.work.*;
import java.util.concurrent.TimeUnit;
/** Persistent network-constrained upload work, independent of the camera service. */
public class UploadWorker extends Worker {
 public UploadWorker(Context context,WorkerParameters parameters){super(context,parameters);}
 public Result doWork(){Context c=getApplicationContext();Security security=new Security(c);Store store=new Store(c,security);if(!store.prefs.getBoolean("cloudEnabled",false))return Result.success();try{CloudBackup.run(c,store,security,message->store.prefs.edit().putString("lastUploadStatus",message).apply(),()->isStopped());return store.queueStatus().getLong("pending")>0?Result.retry():Result.success();}catch(Exception e){return Result.retry();}finally{store.close();}}
 static Constraints network(){return new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build();}
 static void enqueue(Context c){if(!c.getSharedPreferences("watch",0).getBoolean("cloudEnabled",false))return;WorkManager.getInstance(c).enqueueUniqueWork("watch-upload",ExistingWorkPolicy.KEEP,new OneTimeWorkRequest.Builder(UploadWorker.class).setConstraints(network()).setBackoffCriteria(BackoffPolicy.EXPONENTIAL,15,TimeUnit.SECONDS).build());}
 static void schedule(Context c){WorkManager.getInstance(c).enqueueUniquePeriodicWork("watch-upload-recovery",ExistingPeriodicWorkPolicy.KEEP,new PeriodicWorkRequest.Builder(UploadWorker.class,15,TimeUnit.MINUTES).setConstraints(network()).build());enqueue(c);}
}
