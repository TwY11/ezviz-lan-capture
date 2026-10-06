package com.face.emotion.lanprobe;

import android.os.SystemClock;
import com.videogo.openapi.EZPlayer;
import java.io.*;
import java.security.MessageDigest;
import java.util.*;
import org.json.*;

final class TenSecondCapture implements CaptureJob {
    final File folder;
    final LiveCaptureAudio audio;
    final long start;
    private final List<String> timing=new ArrayList<>();
    private final JSONArray images=new JSONArray();
    private int attempted,valid,onTime;
    TenSecondCapture(File session,byte[] header)throws Exception {
        folder=new File(session,"av-10s-"+System.currentTimeMillis());
        if(!folder.mkdirs())throw new IOException("Cannot create capture folder");
        audio=new LiveCaptureAudio(folder,header);
        start=SystemClock.elapsedRealtime();audio.start(start);
    }
    public LiveCaptureAudio audio(){return audio;}
    public long start(){return start;}
    public CaptureSettings settings(){return new CaptureSettings(8,32000,10);}
    public void frame(EZPlayer player,ProbeJournal journal,int slot) {
        long begin=SystemClock.elapsedRealtime();attempted++;
        String name=String.format(Locale.US,"frame-%03d.jpg",slot);
        File image=new File(folder,name);
        try {
            String detail=JpegCapture.capture(player,image);
            long end=SystemClock.elapsedRealtime();valid++;if(end<=start+10000)onTime++;
            JSONObject item=new JSONObject().put("file",name).put("slot",slot).put("planned_ms",slot*125).put("begin_ms",begin-start).put("end_ms",end-start);
            images.put(item);
            timing.add(slot+","+(slot*125)+","+(begin-start)+","+(end-start)+",ok,"+detail);
        }catch(Exception e){ProbeJournal.remove(image);timing.add(slot+","+(slot*125)+","+(begin-start)+","+(SystemClock.elapsedRealtime()-start)+",failed,"+e.getClass().getSimpleName());journal.error("AV10_JPEG slot="+slot,e);}
    }
    public String finish(ProbeJournal journal,boolean complete)throws Exception {
        long collectionEnd=SystemClock.elapsedRealtime();
        String audioDetail;boolean audioOk=false;
        try{audioDetail=audio.finish(complete);audioOk=true;}catch(Exception e){audioDetail=e.getMessage();journal.error("AV10_AUDIO",e);}
        Set<String> hashes=new HashSet<>();
        for(int i=0;i<images.length();i++){
            JSONObject item=images.getJSONObject(i);MessageDigest digest=MessageDigest.getInstance("SHA-256");
            try(InputStream in=new FileInputStream(new File(folder,item.getString("file")))){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)digest.update(b,0,n);}
            StringBuilder hex=new StringBuilder();for(byte b:digest.digest())hex.append(String.format(Locale.US,"%02x",b&255));
            item.put("sha256",hex.toString());hashes.add(hex.toString());
        }
        String state=!complete?"中断":valid==80&&onTime==80&&audioOk?"通过":"失败";
        JSONObject result=new JSONObject().put("status",state).put("target_seconds",10).put("target_fps",8).put("target_frames",80)
            .put("capture_start_elapsed_ms",start).put("collection_finish_elapsed_ms",collectionEnd).put("attempted",attempted).put("valid_images",valid).put("completed_within_10s",onTime)
            .put("skipped_slots",80-attempted).put("unique_jpeg_hashes",hashes.size()).put("actual_fps_within_window",onTime/10.0)
            .put("audio_ok",audioOk).put("audio_detail",audioDetail).put("strict_av_sync_verified",false).put("images",images);
        try(PrintWriter out=new PrintWriter(new File(folder,"capture.json"),"UTF-8")){out.print(result.toString(2));if(out.checkError())throw new IOException("Manifest write failed");}
        try(PrintWriter out=new PrintWriter(new File(folder,"frames.csv"),"UTF-8")){out.println("slot,planned_ms,capture_begin_ms,capture_end_ms,state,stages");for(String line:timing)out.println(line);if(out.checkError())throw new IOException("Frame timing write failed");}
        try(PrintWriter out=new PrintWriter(new File(folder,"review.html"),"UTF-8")){
            out.print("<!doctype html><html lang='zh-CN'><meta charset='utf-8'><title>10秒音画人工对照</title><style>body{font:18px sans-serif;max-width:1100px;margin:24px auto;padding:16px}#frame{width:500px;max-width:100%;height:500px;object-fit:contain;background:#222}audio{width:100%}button{padding:10px;margin:8px}img.spec{width:100%}</style><h1>10秒音画人工对照</h1>");
            out.print("<p>请先完整解压ZIP。本页仅按抓图调用开始时间近似切图，音频0秒是首个解码样本，未验证共同媒体基准，不代表严格同步。画面来自实时截图，不是重新录制的视频。缺帧时上一张会停留，请查看下方时间。</p>");
            out.print("<p>本次结果："+state+"；有效图片 "+valid+"/80，窗口内完成 "+onTime+"；不同JPEG哈希 "+hashes.size()+"（不是不同源视频帧的严格证明）。</p>");
            out.print("<audio id='audio' controls src='audio-full-32k.wav'></audio><p><button onclick='step(-1)'>上一张</button><button onclick='step(1)'>下一张</button></p><img id='frame'><p id='caption'></p><h2>完整音频声谱图</h2><img class='spec' src='spectrogram.png'><p>原始16kHz音频上采样到32kHz，不会增加原始8kHz以上的有效频率。色标为固定幅值dBFS，不是环境声压级。</p>");
            out.print("<script>const frames="+images+";const audio=document.getElementById('audio'),img=document.getElementById('frame'),caption=document.getElementById('caption');let index=-1;function show(i){if(!frames.length){caption.textContent='没有有效图片';return;}i=Math.max(0,Math.min(frames.length-1,i));if(index!==i){index=i;img.src=frames[i].file;}let f=frames[i];caption.textContent='图片 '+(i+1)+'/'+frames.length+'，槽位 '+f.slot+'，计划 '+f.planned_ms+' ms，抓图开始/结束 '+f.begin_ms+'/'+f.end_ms+' ms；音频 '+audio.currentTime.toFixed(3)+' s';}function tick(){let t=audio.currentTime*1000,i=0;while(i+1<frames.length&&frames[i+1].begin_ms<=t)i++;show(i);}audio.addEventListener('timeupdate',tick);audio.addEventListener('seeked',tick);function step(d){audio.pause();show(index+d);}function animate(){if(!audio.paused)tick();requestAnimationFrame(animate);}show(0);animate();</script></html>");
            if(out.checkError())throw new IOException("Review write failed");
        }
        journal.result("10秒音画",state,"folder="+folder.getName()+" valid="+valid+"/80 within_window="+onTime+" unique_hashes="+hashes.size()+" collection_elapsed_ms="+(collectionEnd-start)+" postprocess_ms="+(SystemClock.elapsedRealtime()-collectionEnd)+" audio={"+audioDetail+"}；通过仅指采集完整，音画可用性需人工确认");
        return "10秒对照"+(state.equals("通过")?"完成":"："+state)+"，图片 "+valid+"/80；可导出查看声谱图与review.html";
    }
}
