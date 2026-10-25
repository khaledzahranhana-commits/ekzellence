package com.ekzellence.watch;
import android.app.Application;
import com.serenegiant.utils.UVCUtils;
public class WatchApp extends Application { public void onCreate(){super.onCreate();UVCUtils.init(this);UploadWorker.schedule(this);} }
