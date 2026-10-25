package com.ekzellence.watch;
import java.io.ByteArrayOutputStream;
final class Sps {
 byte[] b;int bit;
 Sps(byte[] input){ByteArrayOutputStream o=new ByteArrayOutputStream();for(int i=1;i<input.length;i++){if(i>2&&input[i]==3&&input[i-1]==0&&input[i-2]==0)continue;o.write(input[i]);}b=o.toByteArray();}
 int read(int n){int x=0;for(int i=0;i<n;i++){if(bit>=b.length*8)throw new IllegalArgumentException("Invalid SPS");x=x*2+((b[bit/8]>>(7-bit%8))&1);bit++;}return x;}
 int ue(){int n=0;while(read(1)==0){if(++n>24)throw new IllegalArgumentException("Invalid SPS");}return (1<<n)-1+(n==0?0:read(n));}
 int se(){int x=ue();return (x%2==0)?-x/2:(x+1)/2;}
 void scaling(int n){int last=8,next=8;for(int i=0;i<n;i++){if(next!=0)next=(last+se()+256)%256;last=next==0?last:next;}}
 static int[] dimensions(byte[] n){Sps s=new Sps(n);int profile=s.read(8);s.read(8);s.read(8);s.ue();int chroma=1,separate=0;if(profile==100||profile==110||profile==122||profile==244||profile==44||profile==83||profile==86||profile==118||profile==128||profile==138||profile==139||profile==134||profile==135){chroma=s.ue();if(chroma==3)separate=s.read(1);s.ue();s.ue();s.read(1);if(s.read(1)==1)for(int i=0;i<(chroma!=3?8:12);i++)if(s.read(1)==1)s.scaling(i<6?16:64);}s.ue();int poc=s.ue();if(poc==0)s.ue();else if(poc==1){s.read(1);s.se();s.se();int k=s.ue();for(int i=0;i<k;i++)s.se();}s.ue();s.read(1);int w=(s.ue()+1)*16,h=s.ue()+1,frame=s.read(1);if(frame==0)s.read(1);s.read(1);h*=16*(2-frame);if(s.read(1)==1){int l=s.ue(),r=s.ue(),t=s.ue(),d=s.ue();int type=separate==1?0:chroma;int sx=(type==1||type==2)?2:1,sy=(type==1?2:1)*(2-frame);w-=(l+r)*sx;h-=(t+d)*sy;}if(w<16||h<16||w>4096||h>2160)throw new IllegalArgumentException("Unsupported dimensions");return new int[]{w,h};}
}
