package com.face.emotion.lanprobe;

import android.os.SystemClock;
import com.videogo.openapi.EZPlayer;
import java.io.*;
import java.util.Locale;

/** Normal capture retains only JPEG and resampled WAV. No raw stream, trace, HTML or manifest. */
final class MediaCapture implements CaptureJob {
    final File folder;
    private final CaptureSettings settings;
    private final LiveCaptureAudio audio;
    private final long start;
    private int valid,onTime;
    MediaCapture(File session,byte[] header,CaptureSettings settings)throws Exception {
        this.settings=settings;
        folder=new File(session,"capture-"+System.currentTimeMillis()+"-"+settings.seconds+"s-"+settings.fps+"fps");
        if(!folder.mkdirs())throw new IOException("无法创建采集目录");
        try{audio=new LiveCaptureAudio(folder,header,settings,false);}
        catch(Exception e){ProbeJournal.remove(folder);throw e;}
        start=SystemClock.elapsedRealtime();audio.start(start);
    }
    public LiveCaptureAudio audio(){return audio;}
    public long start(){return start;}
    public CaptureSettings settings(){return settings;}
    public void frame(EZPlayer player,ProbeJournal journal,int slot){
        File image=new File(folder,String.format(Locale.US,"frame-%03d.jpg",slot));
        try{JpegCapture.capture(player,image);valid++;if(SystemClock.elapsedRealtime()<=start+settings.durationMs)onTime++;}
        catch(Exception e){ProbeJournal.remove(image);journal.error("CAPTURE_FRAME slot="+slot,e);}
    }
    public String finish(ProbeJournal journal,boolean complete)throws Exception {
        boolean audioOk=false;String detail;
        try{detail=audio.finish(complete);audioOk=true;}catch(Exception e){detail=e.getMessage();journal.error("CAPTURE_AUDIO",e);}
        boolean ok=complete&&audioOk&&onTime==settings.frames;
        if(!ok){
            File incomplete=new File(folder.getParentFile(),"incomplete-"+folder.getName());
            if(!folder.renameTo(incomplete))throw new IOException("采集不完整，目录标记失败："+folder.getName());
        }
        String result=(ok?"采集完成":complete?"采集不完整":"采集已中断")+"：图片 "+valid+"/"+settings.frames+"，音频"+(audioOk?"已保存":"未通过检查")+(ok?"":"；部分文件标记为 incomplete");
        journal.log(result+" audio={"+detail+"}");return result;
    }
}
