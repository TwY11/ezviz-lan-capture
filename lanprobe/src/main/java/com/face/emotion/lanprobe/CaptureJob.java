package com.face.emotion.lanprobe;

import com.videogo.openapi.EZPlayer;

interface CaptureJob {
    LiveCaptureAudio audio();
    long start();
    CaptureSettings settings();
    void frame(EZPlayer player,ProbeJournal journal,int slot);
    String finish(ProbeJournal journal,boolean complete)throws Exception;
}
