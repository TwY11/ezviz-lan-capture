package com.face.emotion.lanprobe;

final class CaptureSettings {
    static final int[] RATES={8000,16000,22050,24000,32000,44100,48000};
    static final CaptureSettings DEFAULT=new CaptureSettings(8,32000,2);
    final int fps,rate,seconds,frames;
    final long durationMs;
    CaptureSettings(int fps,int rate,int seconds){
        if(fps<1||fps>30)throw new IllegalArgumentException("帧率范围为1～30帧/秒");
        if(seconds<1||seconds>60)throw new IllegalArgumentException("采集时长范围为1～60秒");
        boolean supported=false;for(int value:RATES)if(rate==value)supported=true;
        if(!supported)throw new IllegalArgumentException("不支持的音频采样率");
        if(fps*seconds>600)throw new IllegalArgumentException("每段最多600张图片，请降低帧率或时长");
        this.fps=fps;this.rate=rate;this.seconds=seconds;frames=fps*seconds;durationMs=seconds*1000L;
    }
    long slotMs(int slot){return (slot*1000L+fps-1)/fps;}
    int next(int slot,long elapsed){return Math.max(slot+1,(int)((Math.max(0,elapsed-1)*fps+1000)/1000));}
    String label(){return fps+" 帧/秒 · "+(rate/1000.0)+" kHz · "+seconds+" 秒 · "+frames+" 张";}
}
