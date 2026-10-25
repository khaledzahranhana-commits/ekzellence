package com.ekzellence.watch;
import android.content.Context;
import android.net.Uri;
import android.media.*;
import java.io.*;
import java.nio.*;
public final class Clipper {
 /** Keyframe-aligned, video-only export. Source recording is never modified. */
 static File clip(Context c,Uri uri,long fromUs,long toUs)throws Exception{
 if(fromUs<0||toUs<=fromUs||toUs-fromUs>600000000L)throw new IOException("Choose a clip between 1 second and 10 minutes");
 File dir=new File(c.getCacheDir(),"exports");dir.mkdirs();File f=File.createTempFile("clip-",".mp4",dir);MediaExtractor ex=new MediaExtractor();MediaMuxer mux=null;boolean started=false,ok=false;
 try{ex.setDataSource(c,uri,null);int input=-1;for(int i=0;i<ex.getTrackCount();i++)if(ex.getTrackFormat(i).getString(MediaFormat.KEY_MIME).startsWith("video/")){input=i;break;}if(input<0)throw new IOException("No video track");MediaFormat fmt=ex.getTrackFormat(input);ex.selectTrack(input);ex.seekTo(fromUs,MediaExtractor.SEEK_TO_PREVIOUS_SYNC);if(ex.getSampleTime()<0||ex.getSampleTime()>=toUs)throw new IOException("Time outside recording");mux=new MediaMuxer(f.getPath(),MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);int output=mux.addTrack(fmt);mux.start();started=true;ByteBuffer data=ByteBuffer.allocate(8*1024*1024);MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();long origin=ex.getSampleTime();int samples=0;while(ex.getSampleTime()>=0&&ex.getSampleTime()<toUs){data.clear();int size=ex.readSampleData(data,0);if(size<0)break;info.set(0,size,ex.getSampleTime()-origin,(ex.getSampleFlags() & MediaExtractor.SAMPLE_FLAG_SYNC)!=0 ? MediaCodec.BUFFER_FLAG_KEY_FRAME : 0);mux.writeSampleData(output,data,info);samples++;ex.advance();}if(samples==0)throw new IOException("Empty clip");mux.stop();started=false;ok=true;return f;}finally{ex.release();if(mux!=null){if(started)try{mux.stop();}catch(Exception ignored){}mux.release();}if(!ok)f.delete();}
 }
}
