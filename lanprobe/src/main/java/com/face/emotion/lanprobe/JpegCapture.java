package com.face.emotion.lanprobe;

import android.graphics.BitmapFactory;
import com.videogo.openapi.EZPlayer;
import java.io.*;

/** Direct SDK JPEG output; no bitmap decode/re-encode on the capture path. */
final class JpegCapture {
    static String capture(EZPlayer player,File file)throws IOException {
        long start=System.nanoTime();
        if(file.getParentFile().getUsableSpace()<32L*1024*1024)throw new IOException("存储空间不足");
        long before=System.nanoTime();int code=player.capturePicture(file.getAbsolutePath(),0);long saved=System.nanoTime();
        if(code!=0)throw new IOException("SDK capture-to-file error="+code);
        try(InputStream in=new FileInputStream(file)){if(in.read()!=255||in.read()!=216)throw new IOException("Not JPEG");}
        BitmapFactory.Options opts=new BitmapFactory.Options();opts.inJustDecodeBounds=true;
        BitmapFactory.decodeFile(file.getAbsolutePath(),opts);
        if(opts.outWidth<=0||opts.outHeight<=0)throw new IOException("Invalid JPEG dimensions");
        long end=System.nanoTime();
        return "sdk_ms="+(saved-before)/1e6+" validation_ms="+(end-saved)/1e6+" total_ms="+(end-start)/1e6+" bytes="+file.length();
    }
}
