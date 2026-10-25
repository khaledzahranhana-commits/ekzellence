package com.ekzellence.watch;
import java.util.*;
public class CoreTest {
 public static void main(String[] args)throws Exception{
  long now=1792890000000L;
  check(Retention.expired(now-Retention.WINDOW-1,now),"older than 30 days must expire");
  check(!Retention.expired(now-Retention.WINDOW,now),"boundary must remain");
  check(!Retention.expired(now-1,now),"recent recording must remain");
  check(Retention.estimatedBytes(1000000,1,30)==324000000000L,"storage estimate");
  check(Retention.estimatedBytes(1000000,2,30)==648000000000L,"multi-camera estimate");
  byte[] sample=java.nio.file.Files.readAllBytes(java.nio.file.Path.of(args[0]));int found=0;
  for(int i=0;i+5<sample.length;i++)if(sample[i]==0&&sample[i+1]==0&&sample[i+2]==0&&sample[i+3]==1&&(sample[i+4]&31)==7){int end=i+5;while(end+3<sample.length&&!(sample[end]==0&&sample[end+1]==0&&(sample[end+2]==1||(sample[end+2]==0&&sample[end+3]==1))))end++;int[] d=Sps.dimensions(Arrays.copyOfRange(sample,i+4,end));check(d[0]==640&&d[1]==360,"SPS dimensions and cropping");found++;break;}
  check(found==1,"SPS present in real H.264 fixture");
  try{Sps.dimensions(new byte[]{0x67});throw new AssertionError("truncated SPS accepted");}catch(IllegalArgumentException expected){}
  System.out.println("Core: 30-day retention boundary, multi-camera storage estimate, real H.264 SPS decoding and malformed SPS passed.");
 }
 static void check(boolean b,String why){if(!b)throw new AssertionError(why);}
}
