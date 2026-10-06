package com.face.emotion.lanprobe;

import org.junit.Test;
import static org.junit.Assert.*;

public class CaptureSignalTest {
    @Test public void allOutputRatesPreserveDurationAndDc(){
        short[] input=new short[16000];java.util.Arrays.fill(input,(short)1234);
        for(int rate:CaptureSettings.RATES){short[] result=CaptureSignal.resample(input,16000,rate);assertEquals(rate,result.length);for(short sample:result)assertEquals(1234,sample);}
    }
    @Test public void downsamplingRejectsAboveNyquistTone(){
        short[] input=new short[8192];for(int i=0;i<input.length;i++)input[i]=(short)Math.round(16000*Math.sin(2*Math.PI*6000*i/16000));
        short[] output=CaptureSignal.resample(input,16000,8000);double sum=0;for(int i=100;i<output.length-100;i++)sum+=(double)output[i]*output[i];
        assertTrue(Math.sqrt(sum/(output.length-200))<20);
    }
    @Test public void downsamplingKeepsSpeechBandTone(){
        short[] input=new short[8192];for(int i=0;i<input.length;i++)input[i]=(short)Math.round(16000*Math.sin(2*Math.PI*1000*i/16000));
        short[] output=CaptureSignal.resample(input,16000,8000);double[] db=CaptureSignal.spectrum(output,1024,1024);
        assertEquals(-6.2266,db[128],.15);
    }
    @Test public void preservesDurationAndConstantWithoutSilentPadding(){
        short[] input=new short[160000];java.util.Arrays.fill(input,(short)1234);
        short[] output=CaptureSignal.resample(input,16000,32000);
        assertEquals(320000,output.length);for(short value:output)assertEquals(1234,value);
    }
    @Test public void retainsShortAudioRatherThanExpandingToTenSeconds(){
        assertEquals(2000,CaptureSignal.resample(new short[1000],16000,32000).length);
        assertEquals(0,CaptureSignal.resample(new short[0],16000,32000).length);
    }
    @Test public void upsamplingPreservesInputSamplePhase(){
        short[] result=CaptureSignal.resample(new short[]{100,300,500},16000,32000);
        assertEquals(100,result[0]);assertEquals(300,result[2]);assertEquals(500,result[4]);
    }
    @Test public void upsamplingSuppressesFalseHighFrequencyImage(){
        short[] source=new short[2048];for(int i=0;i<source.length;i++)source[i]=(short)Math.round(16000*Math.sin(2*Math.PI*4000*i/16000));
        double[] db=CaptureSignal.spectrum(CaptureSignal.resample(source,16000,32000),1024,1024);
        assertTrue(db[128]>-7);assertTrue(db[384]<-60);
    }
    @Test public void spectrogramPlacesOneKhzToneAtCorrectBin(){
        short[] wave=new short[1024];for(int i=0;i<wave.length;i++)wave[i]=(short)Math.round(16384*Math.sin(2*Math.PI*1000*i/32000));
        double[] db=CaptureSignal.spectrum(wave,0,1024);int peak=0;for(int i=1;i<db.length;i++)if(db[i]>db[peak])peak=i;
        assertEquals(32,peak);assertEquals(-6.0206,db[peak],.02);
    }
    @Test public void silenceIsFiniteAndBelowColorFloor(){
        for(double value:CaptureSignal.spectrum(new short[1024],0,1024)){assertTrue(Double.isFinite(value));assertTrue(value<=-90);}
    }
}
