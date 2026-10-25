package com.ekzellence.watch;
public final class Retention {
 public static final long WINDOW=30L*24*60*60*1000;
 public static long cutoff(long now){return now-WINDOW;}
 public static boolean expired(long end,long now){return end<cutoff(now);}
 public static long estimatedBytes(long bitsPerSecond,int cameras,int days){return bitsPerSecond*cameras*days*86400L/8;}
}
