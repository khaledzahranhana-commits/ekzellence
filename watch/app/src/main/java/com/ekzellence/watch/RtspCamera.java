package com.ekzellence.watch;
import android.media.*;
import android.graphics.*;
import android.util.Base64;
import java.io.*;
import java.net.*;
import java.nio.*;
import java.security.*;
import java.util.*;
import java.util.regex.*;

/** Video-only RTSP/RTP over TCP. H.264 single NAL, STAP-A and FU-A. */
public final class RtspCamera implements Runnable {
 final String id,source;final WatchService owner;
 volatile boolean running=true;volatile Socket socket;
 InputStream in;OutputStream out;String base,session="",auth="",user="",pass="";int seq=1;
 MediaCodec decoder;MediaMuxer mux;int track;File file;long segmentStart,rtpOrigin=-1,lastPts,lastSnapshot,lastKeepalive;int width=1280,height=720;byte[] sps,pps;
 ByteArrayOutputStream access=new ByteArrayOutputStream(),fragment;
 long frameTs=-1;int lastSeq=-1;boolean frameBad;
 RtspCamera(String id,String source,WatchService owner){this.id=id;this.source=source;this.owner=owner;}
 public void stop(){running=false;try{if(socket!=null)socket.close();}catch(Exception ignored){}}
 public void run(){while(running){try{connect();readVideo();}catch(Exception e){if(running)owner.status(id,"Reconnecting: "+e.getClass().getSimpleName());}finally{closeMedia();try{if(socket!=null)socket.close();}catch(Exception ignored){}}if(running)try{Thread.sleep(3000);}catch(InterruptedException e){break;}}}
 void connect()throws Exception{
 URI u=new URI(source);if(u.getHost()==null)throw new IOException("Invalid RTSP address");if(u.getRawUserInfo()!=null){String[] a=u.getRawUserInfo().split(":",2);user=URLDecoder.decode(a[0],"UTF-8");pass=a.length>1?URLDecoder.decode(a[1],"UTF-8"):"";}base=new URI("rtsp",null,u.getHost(),u.getPort(),u.getPath(),u.getQuery(),null).toString();auth="";session="";seq=1;rtpOrigin=-1;lastSeq=-1;frameTs=-1;access.reset();fragment=null;sps=null;pps=null;
 socket=new Socket();socket.connect(new InetSocketAddress(u.getHost(),u.getPort()<0?554:u.getPort()),8000);socket.setSoTimeout(15000);in=new BufferedInputStream(socket.getInputStream());out=socket.getOutputStream();
 Response d=request("DESCRIBE",base,"Accept: application/sdp\r\n",true);String sdp=new String(d.body,"UTF-8");boolean video=false;String control="";int payload=-1;
 for(String line:sdp.split("\r?\n")){if(line.startsWith("m=")){if(video)break;video=line.startsWith("m=video");if(video){String[] m=line.split(" +");if(m.length>3)payload=Integer.parseInt(m[3]);}}if(!video)continue;
 if(line.startsWith("a=rtpmap:")){if(!line.toUpperCase(Locale.ROOT).contains("H264/90000"))throw new IOException("Select an H.264 camera stream");}
 if(line.startsWith("a=control:"))control=line.substring(10).trim();
 if(line.contains("sprop-parameter-sets=")){String v=line.split("sprop-parameter-sets=",2)[1].split("[;\\s]",2)[0];String[] sets=v.split(",");if(sets.length>=2){sps=Base64.decode(sets[0],0);pps=Base64.decode(sets[1],0);}}
 }
 if(payload<0)throw new IOException("No video stream");String contentBase=d.headers.getOrDefault("content-base",base.endsWith("/")?base:base+"/");String target=control.startsWith("rtsp://")?control:(control.equals("*")?base:URI.create(contentBase).resolve(control).toString());
 Response setup=request("SETUP",target,"Transport: RTP/AVP/TCP;unicast;interleaved=0-1\r\n",true);session=setup.headers.getOrDefault("session","").split(";",2)[0];if(session.isEmpty())throw new IOException("No RTSP session");request("PLAY",base,"Range: npt=0.000-\r\n",true);lastKeepalive=System.currentTimeMillis();owner.status(id,"Live · H.264 / TCP");
 }
 static class Response{int code;Map<String,String> headers=new HashMap<>();byte[] body;}
 String line()throws Exception{ByteArrayOutputStream b=new ByteArrayOutputStream();int c;while((c=in.read())!=-1){if(c=='\n')break;if(c!='\r')b.write(c);if(b.size()>16384)throw new IOException("RTSP header too large");}if(c==-1)throw new EOFException();return b.toString("UTF-8");}
 Response response(String first)throws Exception{Response r=new Response();r.code=Integer.parseInt(first.split(" ")[1]);while(true){String l=line();if(l.isEmpty())break;int p=l.indexOf(':');if(p>0)r.headers.put(l.substring(0,p).toLowerCase(Locale.ROOT),l.substring(p+1).trim());}int n=Integer.parseInt(r.headers.getOrDefault("content-length","0"));if(n<0||n>1048576)throw new IOException("SDP too large");r.body=readN(n);return r;}
 byte[] readN(int n)throws Exception{byte[] b=new byte[n];int p=0;while(p<n){int a=in.read(b,p,n-p);if(a<0)throw new EOFException();p+=a;}return b;}
 Response request(String method,String url,String extra,boolean retry)throws Exception{send(method,url,extra);Response r=response(line());if(r.code==401&&retry&&!user.isEmpty()){String challenge=r.headers.getOrDefault("www-authenticate","");auth=authorization(challenge,method,url);return request(method,url,extra,false);}if(r.code<200||r.code>=300)throw new IOException("RTSP status "+r.code);return r;}
 void send(String method,String url,String extra)throws Exception{String a=auth;if(auth.startsWith("Digest ")){a=authorization(authChallenge,method,url);}String text=method+" "+url+" RTSP/1.0\r\nCSeq: "+(seq++)+"\r\nUser-Agent: eKZellenceWatch/0.1\r\n"+(session.isEmpty()?"":"Session: "+session+"\r\n")+(a.isEmpty()?"":"Authorization: "+a+"\r\n")+extra+"\r\n";out.write(text.getBytes("UTF-8"));out.flush();}
 String authChallenge="";int nc;
 String authorization(String challenge,String method,String url)throws Exception{if(challenge.startsWith("Basic"))return "Basic "+Base64.encodeToString((user+":"+pass).getBytes("UTF-8"),2);authChallenge=challenge;Map<String,String> m=new HashMap<>();Matcher mm=Pattern.compile("(\\w+)=(?:\"([^\"]*)\"|([^, ]+))").matcher(challenge);while(mm.find())m.put(mm.group(1),mm.group(2)!=null?mm.group(2):mm.group(3));String realm=m.getOrDefault("realm",""),nonce=m.getOrDefault("nonce",""),cn=UUID.randomUUID().toString().replace("-",""),n=String.format(Locale.US,"%08x",++nc);String h1=md5(user+":"+realm+":"+pass);if(m.getOrDefault("algorithm","MD5").equalsIgnoreCase("MD5-sess"))h1=md5(h1+":"+nonce+":"+cn);boolean q=m.getOrDefault("qop","").contains("auth");String answer=md5(h1+":"+nonce+":"+(q?n+":"+cn+":auth:":"")+md5(method+":"+url));return "Digest username=\""+user.replace("\"","")+"\", realm=\""+realm+"\", nonce=\""+nonce+"\", uri=\""+url+"\", response=\""+answer+"\""+(q?", qop=auth, nc="+n+", cnonce=\""+cn+"\"":"")+(m.containsKey("opaque")?", opaque=\""+m.get("opaque")+"\"":"");}
 static String md5(String s)throws Exception{byte[] b=MessageDigest.getInstance("MD5").digest(s.getBytes("UTF-8"));StringBuilder r=new StringBuilder();for(byte x:b)r.append(String.format("%02x",x&255));return r.toString();}
 void readVideo()throws Exception{while(running){int first=in.read();if(first<0)throw new EOFException();if(first=='$'){int ch=in.read(),a=in.read(),b=in.read();if(b<0)throw new EOFException();byte[] data=readN((a<<8)|b);if(ch==0)rtp(data);}else{String rest=line();response((char)first+rest);}if(System.currentTimeMillis()-lastKeepalive>20000){send("OPTIONS",base,"");lastKeepalive=System.currentTimeMillis();}}}
 void rtp(byte[] p)throws Exception{if(p.length<12||(p[0]&0xc0)!=0x80)return;int off=12+(p[0]&15)*4;if((p[0]&16)!=0){if(off+4>p.length)return;off+=4+(((p[off+2]&255)<<8)|(p[off+3]&255))*4;}int end=p.length;if((p[0]&32)!=0)end-=p[p.length-1]&255;if(off>=end)return;int sequence=((p[2]&255)<<8)|(p[3]&255);long ts=((long)(p[4]&255)<<24)|((long)(p[5]&255)<<16)|((p[6]&255)<<8)|(p[7]&255);
 if(frameTs!=ts){if(access.size()>0&&!frameBad)frame(access.toByteArray(),frameTs);access.reset();fragment=null;frameTs=ts;frameBad=false;}if(lastSeq!=-1&&sequence!=((lastSeq+1)&65535))frameBad=true;lastSeq=sequence;
 int type=p[off]&31;if(type>=1&&type<=23)nal(Arrays.copyOfRange(p,off,end));else if(type==24){int x=off+1;while(x+2<=end){int n=((p[x]&255)<<8)|(p[x+1]&255);x+=2;if(n==0||x+n>end){frameBad=true;break;}nal(Arrays.copyOfRange(p,x,x+n));x+=n;}}else if(type==28&&off+2<end){int h=p[off+1]&255;if((h&128)!=0){fragment=new ByteArrayOutputStream();fragment.write((p[off]&0xe0)|(h&31));}if(fragment!=null){fragment.write(p,off+2,end-off-2);if(fragment.size()>4*1024*1024)throw new IOException("NAL too large");if((h&64)!=0){nal(fragment.toByteArray());fragment=null;}}else frameBad=true;}
 if(access.size()>8*1024*1024)throw new IOException("Frame too large");if((p[1]&128)!=0){if(access.size()>0&&!frameBad)frame(access.toByteArray(),frameTs);access.reset();fragment=null;}}
 void nal(byte[] n)throws Exception{if((n[0]&31)==7)sps=n;if((n[0]&31)==8)pps=n;access.write(new byte[]{0,0,0,1});access.write(n);}
 void frame(byte[] data,long ts)throws Exception{if(sps==null||pps==null)return;boolean key=false;for(int i=0;i+4<data.length;i++)if(data[i]==0&&data[i+1]==0&&data[i+2]==0&&data[i+3]==1&&(data[i+4]&31)==5)key=true;
 if(rtpOrigin<0)rtpOrigin=ts;long pts=((ts-rtpOrigin)&0xffffffffL)*1000000L/90000;lastPts=pts;
 if(decoder==null&&key){try{int[] dim=Sps.dimensions(sps);width=dim[0];height=dim[1];MediaFormat f=format();f.setInteger(MediaFormat.KEY_COLOR_FORMAT,MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible);decoder=MediaCodec.createDecoderByType("video/avc");decoder.configure(f,null,null,0);decoder.start();}catch(Exception e){if(decoder!=null)decoder.release();decoder=null;owner.status(id,"Recording available; live decoder unavailable");}}
 if(decoder!=null){try{int input=decoder.dequeueInputBuffer(0);if(input>=0){ByteBuffer b=decoder.getInputBuffer(input);if(b!=null&&b.capacity()>=data.length){b.clear();b.put(data);decoder.queueInputBuffer(input,0,data.length,pts,0);}else decoder.queueInputBuffer(input,0,0,pts,0);}MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();int o;while((o=decoder.dequeueOutputBuffer(info,0))>=0){try{if(System.currentTimeMillis()-lastSnapshot>450){Image im=decoder.getOutputImage(o);if(im!=null){try{owner.snapshot(id,jpeg(im));lastSnapshot=System.currentTimeMillis();}finally{im.close();}}}}finally{decoder.releaseOutputBuffer(o,false);}}}catch(Exception e){try{decoder.stop();decoder.release();}catch(Exception ignored){}decoder=null;}}
 if(!owner.isEnabled(id)||!owner.store.hasRoom()){finishSegment();if(!owner.store.hasRoom())owner.status(id,"Storage low — recording paused");return;}
 if(mux!=null&&key&&System.currentTimeMillis()-segmentStart>=10000)finishSegment();
 if(mux==null&&key){file=owner.store.temp(id);mux=new MediaMuxer(file.getAbsolutePath(),MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);track=mux.addTrack(format());mux.start();segmentStart=System.currentTimeMillis();segmentPts=pts;owner.status(id,"Recording · H.264");}
 if(mux!=null){MediaCodec.BufferInfo bi=new MediaCodec.BufferInfo();bi.set(0,data.length,pts-segmentPts,key?MediaCodec.BUFFER_FLAG_KEY_FRAME:0);mux.writeSampleData(track,ByteBuffer.wrap(data),bi);}}
 long segmentPts;
 MediaFormat format(){MediaFormat f=MediaFormat.createVideoFormat("video/avc",width,height);f.setByteBuffer("csd-0",ByteBuffer.wrap(prefix(sps)));f.setByteBuffer("csd-1",ByteBuffer.wrap(prefix(pps)));return f;}
 static byte[] prefix(byte[] n){byte[] r=new byte[n.length+4];r[3]=1;System.arraycopy(n,0,r,4,n.length);return r;}
 static byte[] jpeg(Image im)throws Exception{Rect c=im.getCropRect();int w=c.width()&~1,h=c.height()&~1;byte[] nv=new byte[w*h*3/2];Image.Plane[] p=im.getPlanes();for(int k=0;k<3;k++){ByteBuffer b=p[k].getBuffer();int shift=k==0?0:1,rows=h>>shift,cols=w>>shift;int base=k==0?0:w*h+(k==1?1:0);for(int y=0;y<rows;y++)for(int x=0;x<cols;x++){int src=(y+(c.top>>shift))*p[k].getRowStride()+(x+(c.left>>shift))*p[k].getPixelStride();nv[base+(k==0?y*w+x:y*w+x*2)]=b.get(src);}}ByteArrayOutputStream out=new ByteArrayOutputStream();new YuvImage(nv,ImageFormat.NV21,w,h,null).compressToJpeg(new Rect(0,0,w,h),55,out);return out.toByteArray();}
 void finishSegment(){if(mux==null)return;MediaMuxer m=mux;mux=null;try{m.stop();m.release();File completed=file;long started=segmentStart,ended=System.currentTimeMillis();owner.io.submit(()->{try{owner.store.finish(id,completed,started,ended);}catch(Exception e){owner.enabled.put(id,false);owner.store.enabled(id,false);owner.status(id,"Save failed — recording paused; check storage");}});}catch(Exception e){try{m.release();}catch(Exception ignored){}owner.enabled.put(id,false);owner.store.enabled(id,false);owner.status(id,"Save failed — recording paused; check storage");}}
 void closeMedia(){finishSegment();if(decoder!=null){try{decoder.stop();decoder.release();}catch(Exception ignored){}decoder=null;}}
}
