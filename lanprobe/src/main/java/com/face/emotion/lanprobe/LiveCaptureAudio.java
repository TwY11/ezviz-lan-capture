package com.face.emotion.lanprobe;

import android.media.*;
import android.os.SystemClock;
import java.io.*;
import java.nio.*;
import java.util.Arrays;
import java.util.concurrent.*;

/** Callback only copies audio packets. One dedicated thread owns the AAC decoder and PCM output. */
final class LiveCaptureAudio {
    private static final class Packet {
        final byte[] data; final long arrival;
        Packet(byte[] b,long t){data=b;arrival=t;}
    }
    private final ArrayBlockingQueue<Packet> queue=new ArrayBlockingQueue<>(256);
    private final CountDownLatch ready=new CountDownLatch(1);
    private final Thread worker;
    private final File folder;
    private final CaptureSettings settings;
    private final boolean diagnostics;
    private final ByteArrayOutputStream pcm=new ByteArrayOutputStream();
    private volatile boolean closed;
    private volatile long start=-1;
    private volatile String error="";
    private long firstArrival=-1,lastArrival=-1;
    private int frames,outputBlocks,dropped;
    private boolean eos;
    LiveCaptureAudio(File folder,byte[] header)throws Exception {
        this(folder,header,new CaptureSettings(8,32000,10),true);
    }
    LiveCaptureAudio(File folder,byte[] header,CaptureSettings settings,boolean diagnostics)throws Exception {
        if(header==null||header.length!=40||header[0]!='I'||header[1]!='M'||header[2]!='K'||header[3]!='H'||(header[16]&255)!=128||(header[17]&255)!=62||header[18]!=0||header[19]!=0)
            throw new IOException("未取得已验证的IMKH/16kHz流头；请重新登录后再采集");
        this.folder=folder;this.settings=settings;this.diagnostics=diagnostics;
        worker=new Thread(this::decode,"lanprobe-live-aac");worker.start();
        if(!ready.await(5,TimeUnit.SECONDS)){closed=true;worker.join(5000);throw new IOException("AAC decoder initialization timeout");}
        if(!error.isEmpty())throw new IOException(error);
    }
    void start(long time){start=time;}
    synchronized void accept(int type,byte[] data,int length,long now) {
        long begin=start;
        if(closed||begin<0||now<begin||now>=begin+settings.durationMs||!error.isEmpty())return;
        if(type!=2||data==null||length<12||length>data.length||(data[1]&127)!=104)return;
        if(length>8192||!queue.offer(new Packet(Arrays.copyOf(data,length),now))){dropped++;error="Audio queue overflow / oversized packet";}
    }
    private void decode() {
        MediaCodec codec=null;
        try(OutputStream aac=diagnostics?new BufferedOutputStream(new FileOutputStream(new File(folder,"audio.aac"))):discard();
            PrintWriter timing=new PrintWriter(diagnostics?new FileOutputStream(new File(folder,"audio-packets.csv")):discard())) {
            timing.println("packet,arrival_from_capture_start_ms,rtp_timestamp,audio_relative_pts_us,aac_bytes");
            MediaFormat format=MediaFormat.createAudioFormat("audio/mp4a-latm",16000,1);
            format.setByteBuffer("csd-0",ByteBuffer.wrap(new byte[]{0x14,0x08})); // AAC-LC / 16 kHz / mono, verified camera profile.
            codec=MediaCodec.createDecoderByType("audio/mp4a-latm");codec.configure(format,null,null,0);codec.start();ready.countDown();
            RtpAac parser=new RtpAac();
            while(!closed||!queue.isEmpty()) {
                Packet packet=queue.poll(20,TimeUnit.MILLISECONDS);
                if(packet!=null) {
                    byte[] au=parser.extract(packet.data);if(au==null)continue;
                    if(frames>=settings.durationMs/64+64)throw new IOException("Unexpected excess audio duration");
                    RtpAac.adts(aac,au);
                    long ts=0;for(int i=4;i<8;i++)ts=(ts<<8)|(packet.data[i]&255);
                    timing.println(frames+","+(packet.arrival-start)+","+ts+","+(frames*64000L)+","+au.length);
                    if(firstArrival<0)firstArrival=packet.arrival;lastArrival=packet.arrival;
                    input(codec,au,frames*64000L,false);frames++;
                }
                drain(codec,0);
            }
            input(codec,new byte[0],frames*64000L,true);
            long deadline=SystemClock.elapsedRealtime()+3000;
            while(!eos&&SystemClock.elapsedRealtime()<deadline)drain(codec,10000);
            if(!eos)throw new IOException("AAC decoder EOS timeout");
            if(timing.checkError())throw new IOException("Audio timing write failed");
        }catch(Exception e){error=e.getClass().getSimpleName()+": "+e.getMessage();}
        finally{ready.countDown();if(codec!=null){try{codec.stop();}catch(Exception ignored){}codec.release();}}
    }
    private static OutputStream discard(){return new OutputStream(){public void write(int b){} @Override public void write(byte[] b,int offset,int count){}};}
    void closeInput(){synchronized(this){closed=true;}}
    private void input(MediaCodec codec,byte[] au,long pts,boolean last)throws Exception {
        long deadline=SystemClock.elapsedRealtime()+2000;
        while(SystemClock.elapsedRealtime()<deadline) {
            int index=codec.dequeueInputBuffer(10000);
            if(index>=0){ByteBuffer b=codec.getInputBuffer(index);if(b==null||b.capacity()<au.length)throw new IOException("Invalid AAC input buffer");b.clear();b.put(au);codec.queueInputBuffer(index,0,au.length,pts,last?MediaCodec.BUFFER_FLAG_END_OF_STREAM:0);return;}
            drain(codec,0);
        }
        throw new IOException("AAC input timeout");
    }
    private void drain(MediaCodec codec,long wait)throws Exception {
        MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();
        for(int count=0;count<512;count++) {
            int index=codec.dequeueOutputBuffer(info,wait);wait=0;
            if(index==MediaCodec.INFO_TRY_AGAIN_LATER)return;
            if(index==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED){
                MediaFormat f=codec.getOutputFormat();
                if(f.getInteger(MediaFormat.KEY_SAMPLE_RATE)!=16000||f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)!=1||(f.containsKey(MediaFormat.KEY_PCM_ENCODING)&&f.getInteger(MediaFormat.KEY_PCM_ENCODING)!=AudioFormat.ENCODING_PCM_16BIT))throw new IOException("Unexpected decoded PCM format: "+f);
            }else if(index>=0){
                try{
                    if(info.size>0){if(pcm.size()+info.size>2*1024*1024)throw new IOException("PCM size limit");ByteBuffer b=codec.getOutputBuffer(index);if(b==null)throw new IOException("No PCM buffer");b.position(info.offset);b.limit(info.offset+info.size);byte[] bytes=new byte[info.size];b.get(bytes);pcm.write(bytes);outputBlocks++;}
                    if((info.flags&MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0)eos=true;
                }finally{codec.releaseOutputBuffer(index,false);}
            }
        }
    }
    String finish(boolean complete)throws Exception {
        closeInput();
        worker.join(7000);
        if(worker.isAlive())throw new IOException("Audio worker still active; do not reconnect until it stops");
        byte[] data=pcm.toByteArray();if(data.length%2!=0)throw new IOException("Invalid PCM byte count");
        short[] source=new short[data.length/2];ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(source);
        String detail="frames="+frames+" decoded_blocks="+outputBlocks+" samples_16k="+source.length+" duration_ms="+(source.length/16.0)+" first_arrival_ms="+(firstArrival<0?-1:firstArrival-start)+" last_arrival_ms="+(lastArrival<0?-1:lastArrival-start)+" dropped="+dropped+" eos="+eos+" padding_samples=0 error="+error;
        if(diagnostics&&source.length>0){
            WavWriter.write(new File(folder,"audio-original-16k.wav"),source,16000);
            short[] resampled=CaptureSignal.resample(source,16000,32000);
            WavWriter.write(new File(folder,"audio-full-32k.wav"),resampled,32000);
            if(resampled.length>=1024)CaptureSpectrogram.save(resampled,new File(folder,"spectrogram.png"));
        }
        if(diagnostics)try(PrintWriter out=new PrintWriter(new File(folder,"audio-result.txt"),"UTF-8")){out.println(detail);out.println("Arrival time is not capture time. Audio zero is the first decoded sample; common video epoch is NOT verified.");}
        if(!error.isEmpty()||dropped!=0||!eos||source.length==0)throw new IOException(detail);
        if(complete&&Math.abs(source.length/16.0-settings.durationMs)>250)throw new IOException("Audio duration outside configured window +/-250ms; "+detail);
        if(!diagnostics)WavWriter.write(new File(folder,"audio-"+settings.rate+"hz.wav"),CaptureSignal.resample(source,16000,settings.rate),settings.rate);
        return detail;
    }
}
