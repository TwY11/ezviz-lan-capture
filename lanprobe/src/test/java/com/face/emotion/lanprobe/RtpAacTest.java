package com.face.emotion.lanprobe;
import org.junit.Test;
import java.io.*;
import static org.junit.Assert.*;
public class RtpAacTest {
    private byte[] packet(int seq,int timestamp){byte[] b=new byte[18];b[0]=(byte)128;b[1]=(byte)232;b[2]=(byte)(seq>>8);b[3]=(byte)seq;b[4]=(byte)(timestamp>>24);b[5]=(byte)(timestamp>>16);b[6]=(byte)(timestamp>>8);b[7]=(byte)timestamp;b[13]=16;b[15]=16;b[16]=1;b[17]=2;return b;}
    @Test public void extractsAndHandlesSequenceWrap()throws Exception{RtpAac p=new RtpAac();assertArrayEquals(new byte[]{1,2},p.extract(packet(65535,0)));p.extract(packet(0,1024));assertEquals(2,p.frames);}
    @Test(expected=IOException.class)public void rejectsGap()throws Exception{RtpAac p=new RtpAac();p.extract(packet(1,0));p.extract(packet(3,1024));}
    @Test(expected=IOException.class)public void rejectsBadAuLength()throws Exception{byte[] b=packet(1,0);b[15]=24;new RtpAac().extract(b);}
    @Test(expected=IOException.class)public void rejectsBadPadding()throws Exception{byte[] b=packet(1,0);b[0]=(byte)160;b[17]=0;new RtpAac().extract(b);}
    @Test public void removesPadding()throws Exception{byte[] b=java.util.Arrays.copyOf(packet(1,0),20);b[0]=(byte)160;b[19]=2;assertArrayEquals(new byte[]{1,2},new RtpAac().extract(b));}
    @Test public void doesNotTreatVideoAsAudio()throws Exception{byte[] b=packet(1,0);b[1]=96;assertNull(new RtpAac().extract(b));}
}
