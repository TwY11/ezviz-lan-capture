package com.face.emotion.lanprobe;

import android.graphics.*;
import java.io.*;
import java.util.Locale;

final class CaptureSpectrogram {
    static void save(short[] pcm,File file)throws IOException {
        final int rate=32000,n=1024,hop=256,left=110,top=120,width=1280,height=512;
        if(pcm.length<n)throw new IOException("Insufficient audio for spectrogram");
        int columns=1+(pcm.length-n)/hop;
        Bitmap heat=Bitmap.createBitmap(columns,513,Bitmap.Config.ARGB_8888);
        Bitmap canvas=Bitmap.createBitmap(1500,820,Bitmap.Config.ARGB_8888);
        try {
            for(int x=0;x<columns;x++) {
                double[] db=CaptureSignal.spectrum(pcm,x*hop,n);
                for(int f=0;f<db.length;f++) {
                    float level=(float)Math.max(0,Math.min(1,(db[f]+90)/90));
                    heat.setPixel(x,512-f,Color.HSVToColor(new float[]{240*(1-level),0.95f,0.12f+0.88f*level}));
                }
            }
            Canvas c=new Canvas(canvas);c.drawColor(Color.WHITE);Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setColor(Color.BLACK);p.setTextSize(26);
            c.drawText("Remote audio spectrogram | mono PCM16 / 32 kHz",left,42,p);
            p.setTextSize(20);c.drawText("Hann 1024 (32 ms), hop 256 (8 ms), amplitude dBFS [-90, 0]",left,76,p);
            double seconds=pcm.length/(double)rate;
            c.drawRect(left,top,left+width,top+height,p);
            // STFT columns are centered on their windows; uncovered edges stay black, not fabricated audio.
            float first=(float)(n/2.0/pcm.length*width),last=(float)(((columns-1)*hop+n/2.0)/pcm.length*width);
            c.drawBitmap(heat,null,new RectF(left+first,top,left+last,top+height),null);
            for(int k=0;k<=16;k+=2){float y=top+height-k/16f*height;c.drawText(k+" kHz",12,y+7,p);}
            for(int s=0;s<=Math.floor(seconds);s++){float x=left+(float)(s/seconds*width);c.drawLine(x,top+height,x,top+height+8,p);c.drawText(s+" s",x-10,top+height+34,p);}
            c.drawText(String.format(Locale.US,"Actual decoded duration: %.3f s. Source: 16 kHz AAC (useful bandwidth <= 8 kHz).",seconds),left,710,p);
            c.drawText("Time 0 = first decoded audio sample, NOT a verified video/media common origin.",left,749,p);
            for(int x=0;x<300;x++){float v=x/299f;p.setColor(Color.HSVToColor(new float[]{240*(1-v),.95f,.12f+.88f*v}));c.drawLine(left+x,770,left+x,790,p);}
            p.setColor(Color.BLACK);c.drawText("-90 dBFS                          0 dBFS",left+320,790,p);
            try(OutputStream out=new FileOutputStream(file)){if(!canvas.compress(Bitmap.CompressFormat.PNG,100,out))throw new IOException("PNG encoding failed");}
        }finally{heat.recycle();canvas.recycle();}
    }
}
