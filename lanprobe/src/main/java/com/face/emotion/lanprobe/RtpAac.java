package com.face.emotion.lanprobe;

import java.io.*;
import java.util.Arrays;

/** Strict profile verified on the user's camera; not a universal dynamic-PT mapping. */
final class RtpAac {
    private int seq=-1; private long timestamp,ssrc; int frames;
    byte[] extract(byte[] b)throws IOException {
        if(b.length<12 || (b[0]&255)>>6!=2)throw new IOException("Invalid RTP header");
        if((b[1]&127)!=104)return null;
        int start=12+4*(b[0]&15),end=b.length;
        if(start>end)throw new IOException("Invalid CSRC list");
        if((b[0]&16)!=0){if(start+4>end)throw new IOException("Invalid extension");start+=4+4*u16(b,start+2);}
        if(start>end)throw new IOException("Extension overflow");
        if((b[0]&32)!=0){int pad=b[end-1]&255;if(pad==0||pad>end-start)throw new IOException("Invalid padding");end-=pad;}
        if(end-start<4||u16(b,start)!=16)throw new IOException("Unsupported AAC AU header");
        int au=u16(b,start+2);if((au&7)!=0 || (au>>3)!=end-start-4)throw new IOException("AAC AU size/index mismatch");
        int next=u16(b,2);long ts=u32(b,4),source=u32(b,8);
        if(seq>=0 && (((next-seq)&65535)!=1 || ((ts-timestamp)&0xffffffffL)!=1024 || source!=ssrc))throw new IOException("Audio RTP gap/reorder/timestamp/source change");
        seq=next;timestamp=ts;ssrc=source;frames++;
        return Arrays.copyOfRange(b,start+4,end);
    }
    static void adts(OutputStream out,byte[] frame)throws IOException {
        int n=frame.length+7;if(n>=8192)throw new IOException("AAC frame too large");
        out.write(new byte[]{(byte)255,(byte)241,(byte)96,(byte)(64|(n>>11)),(byte)(n>>3),(byte)(((n&7)<<5)|31),(byte)252});out.write(frame);
    }
    private static int u16(byte[] b,int p){return ((b[p]&255)<<8)|(b[p+1]&255);}
    private static long u32(byte[] b,int p){return ((long)u16(b,p)<<16)|u16(b,p+2);}
}
