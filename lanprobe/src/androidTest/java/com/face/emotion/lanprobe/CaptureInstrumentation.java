package com.face.emotion.lanprobe;

import android.app.Instrumentation;
import android.os.*;
import java.io.*;
import java.nio.*;
import java.util.*;
import java.util.zip.*;

/** Deterministic AAC tone fixture; does not log in, read or delete user sessions. */
public final class CaptureInstrumentation extends Instrumentation {
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){
        Bundle result=new Bundle();File sandbox=new File(getTargetContext().getCacheDir(),"capture-validation-"+System.currentTimeMillis());
        try{
            if(!sandbox.mkdirs())throw new IOException("Cannot create isolated test directory");
            List<byte[]> units=new ArrayList<>();
            try(DataInputStream input=new DataInputStream(getContext().getAssets().open("test-tone.aac"))){
                while(input.available()>0){byte[] h=new byte[7];input.readFully(h);if((h[0]&255)!=255||(h[1]&0xf6)!=0xf0)throw new IOException("ADTS sync");int size=((h[3]&3)<<11)|((h[4]&255)<<3)|((h[5]&255)>>5);byte[] au=new byte[size-7];input.readFully(au);units.add(au);}
            }
            if(units.size()<157)throw new IOException("Short tone fixture");
            StringBuilder metrics=new StringBuilder();
            for(int rate:CaptureSettings.RATES){
                File folder=new File(sandbox,"rate-"+rate);folder.mkdir();LiveCaptureAudio audio=new LiveCaptureAudio(folder,header(),new CaptureSettings(8,rate,2),false);
                feed(audio,units,31,2000);audio.finish(true);
                File[] files=folder.listFiles();if(files==null||files.length!=1||!files[0].getName().equals("audio-"+rate+"hz.wav"))throw new IOException("Normal output retained extra files");
                int samples=verifyWav(files[0],rate);if(samples!=(int)(31L*1024*rate/16000))throw new IOException("Wrong sample count");
                metrics.append(rate).append("Hz=").append(samples).append(" samples; ");
            }
            File comparison=new File(sandbox,"comparison");comparison.mkdir();
            ProbeJournal journal=journal(sandbox);
            TenSecondCapture ten=new TenSecondCapture(comparison,header());feed(ten.audio(),units,156,10000);ten.finish(journal,true);
            for(String name:new String[]{"audio.aac","audio-full-32k.wav","audio-original-16k.wav","spectrogram.png","review.html","capture.json","frames.csv","audio-packets.csv"})if(!new File(ten.folder,name).isFile())throw new IOException("10s comparison missing "+name);
            verifyWav(new File(ten.folder,"audio-full-32k.wav"),32000);
            File mediaRoot=new File(sandbox,"normal-job");mediaRoot.mkdir();
            MediaCapture media=new MediaCapture(mediaRoot,header(),CaptureSettings.DEFAULT);feed(media.audio(),units,31,2000);String incomplete=media.finish(journal,true);
            if(!incomplete.contains("不完整"))throw new IOException("Zero-frame run not flagged");
            File[] partial=mediaRoot.listFiles();if(partial==null||partial.length!=1||!partial[0].getName().startsWith("incomplete-"))throw new IOException("Missing partial marker");
            File empty=new File(sandbox,"empty");empty.mkdir();LiveCaptureAudio none=new LiveCaptureAudio(empty,header(),CaptureSettings.DEFAULT,false);none.start(SystemClock.elapsedRealtime());
            boolean rejected=false;try{none.finish(false);}catch(IOException expected){rejected=true;}
            if(!rejected||empty.listFiles().length!=0)throw new IOException("No-audio created output or passed");
            File gap=new File(sandbox,"gap");gap.mkdir();LiveCaptureAudio broken=new LiveCaptureAudio(gap,header(),CaptureSettings.DEFAULT,false);long now=SystemClock.elapsedRealtime();broken.start(now);
            byte[] first=packet(units.get(0),0),third=packet(units.get(2),2);broken.accept(2,first,first.length,now);broken.accept(2,third,third.length,now+1);
            rejected=false;try{broken.finish(false);}catch(IOException expected){rejected=true;}if(!rejected||gap.listFiles().length!=0)throw new IOException("Discontinuous audio accepted");
            File abort=new File(sandbox,"abort");abort.mkdir();LiveCaptureAudio interrupted=new LiveCaptureAudio(abort,header(),CaptureSettings.DEFAULT,false);feed(interrupted,units,4,256);interrupted.finish(false);verifyWav(new File(abort,"audio-32000hz.wav"),32000);
            journal.beginLogin();File previous=journal.session;new File(previous,"old.jpg").createNewFile();journal.beginLogin();new File(journal.session,"new.jpg").createNewFile();journal.log("memory only");
            if(previous.exists()||journal.session.listFiles().length!=1)throw new IOException("Session cleanup/log retention");
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();journal.export(bytes);int count=0;
            try(ZipInputStream zip=new ZipInputStream(new ByteArrayInputStream(bytes.toByteArray()))){ZipEntry entry;while((entry=zip.getNextEntry())!=null){if(!entry.getName().equals(journal.session.getName()+"/new.jpg"))throw new IOException("Unexpected export entry");count++;}}
            if(count!=1)throw new IOException("ZIP count");
            result.putString("rates",metrics.toString());result.putString("result","PASS: 7 PCM output rates, media-only retention, 10s diagnostic outputs, incomplete marking, empty/gap rejection, interruption, login rotation and ZIP isolation. No live camera/JPEG test.");
        }catch(Throwable error){result.putString("error",error.toString());}
        finally{ProbeJournal.remove(sandbox);finish(result.containsKey("error")?1:0,result);}
    }
    private ProbeJournal journal(File sandbox){final File root=new File(sandbox,"journal");return new ProbeJournal(new android.content.ContextWrapper(getTargetContext()){@Override public File getFilesDir(){return root;}},text->{});}
    private static byte[] header(){byte[] b=new byte[40];b[0]='I';b[1]='M';b[2]='K';b[3]='H';b[16]=(byte)128;b[17]=62;return b;}
    private static byte[] packet(byte[] au,int seq){byte[] b=new byte[16+au.length];b[0]=(byte)128;b[1]=(byte)104;b[2]=(byte)(seq>>8);b[3]=(byte)seq;long time=seq*1024L;for(int i=7;i>=4;i--){b[i]=(byte)time;time>>>=8;}b[8]=0x55;b[9]=0x66;b[10]=0x77;b[11]=(byte)0x88;b[13]=16;int bits=au.length<<3;b[14]=(byte)(bits>>8);b[15]=(byte)bits;System.arraycopy(au,0,b,16,au.length);return b;}
    private static void feed(LiveCaptureAudio audio,List<byte[]> units,int count,long duration){long start=SystemClock.elapsedRealtime();audio.start(start);for(int i=0;i<count;i++){long delay=start+i*64L-SystemClock.elapsedRealtime();if(delay>0)SystemClock.sleep(delay);byte[] p=packet(units.get(i),i);audio.accept(2,p,p.length,SystemClock.elapsedRealtime());}long delay=start+duration-SystemClock.elapsedRealtime();if(delay>0)SystemClock.sleep(delay);}
    private static int verifyWav(File file,int rate)throws IOException{byte[] header=new byte[44];try(DataInputStream in=new DataInputStream(new FileInputStream(file))){in.readFully(header);}ByteBuffer b=ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);if(b.getInt(24)!=rate||b.getShort(22)!=1||b.getShort(34)!=16||b.getInt(40)!=file.length()-44)throw new IOException("Invalid WAV header");return b.getInt(40)/2;}
}
