package com.face.emotion.lanprobe;

import java.io.*;
import java.nio.*;

final class WavWriter {
    static void write(File file,short[] samples,int rate)throws IOException {
        ByteBuffer header=ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
        header.put(new byte[]{'R','I','F','F'}).putInt(36+samples.length*2).put(new byte[]{'W','A','V','E','f','m','t',' '})
            .putInt(16).putShort((short)1).putShort((short)1).putInt(rate).putInt(rate*2).putShort((short)2).putShort((short)16)
            .put(new byte[]{'d','a','t','a'}).putInt(samples.length*2);
        File pending=new File(file.getParentFile(),"temp-"+file.getName());
        try{
            try(OutputStream out=new BufferedOutputStream(new FileOutputStream(pending))){out.write(header.array());for(short s:samples){out.write(s&255);out.write((s>>>8)&255);}}
            if(!pending.renameTo(file))throw new IOException("WAV保存失败");
        }finally{if(pending.exists())ProbeJournal.remove(pending);}
    }
}
