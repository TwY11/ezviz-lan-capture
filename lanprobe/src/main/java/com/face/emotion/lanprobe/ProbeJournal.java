package com.face.emotion.lanprobe;

import android.content.Context;
import android.util.Log;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.zip.*;

final class ProbeJournal {
    interface Listener { void line(String text); }
    final File root;
    File session;
    private final Listener listener;
    private final Set<String> secrets = new HashSet<>();
    ProbeJournal(Context context, Listener listener) {
        this.listener = listener;
        root = new File(context.getFilesDir(), "evidence");
        File[] logins=root.listFiles(f->f.isDirectory()&&f.getName().startsWith("login-"));
        if(logins!=null&&logins.length>0){Arrays.sort(logins,Comparator.comparing(File::getName));session=logins[logins.length-1];}
        else {
            session = new File(root, new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date()) + "-" + UUID.randomUUID().toString().substring(0,8));
            if (!session.mkdirs()) throw new IllegalStateException("Cannot create evidence directory");
        }
        log("SESSION_START pid=" + android.os.Process.myPid() + " token_setter_calls=0");
    }
    synchronized void secret(String value) { if (value != null && !value.isEmpty()) secrets.add(value); }
    synchronized void beginLogin() throws IOException {
        File next=new File(root,"login-"+System.currentTimeMillis()+"-"+UUID.randomUUID().toString().substring(0,8));
        if(!next.mkdirs())throw new IOException("Cannot create login evidence directory");
        session=next;
        File[] old=root.listFiles();
        if(old!=null)for(File file:old)if(!file.equals(next)) {
            if(!file.getCanonicalFile().getParentFile().equals(root.getCanonicalFile()))
                throw new IOException("Unsafe evidence path");
            remove(file);
            if(file.exists())throw new IOException("Cannot delete previous login evidence");
        }
        log("LOGIN_SESSION_START previous_login_evidence_deleted=true token_setter_calls=0");
    }
    private String sanitize(String text) {
        for (String secret : secrets) text = text.replace(secret, "[REDACTED]");
        return text.replaceAll("(?i)(?:at\\.|tk\\.)[a-z0-9._-]+", "[TOKEN_REDACTED]");
    }
    synchronized void log(String text) {
        String line = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(new Date()) + " " + sanitize(text) + "\n";
        Log.i("LanProbe", line);
        listener.line(line);
    }
    synchronized void result(String item, String state, String detail) {
        log("RESULT " + item + " " + state + " " + detail);
    }
    void error(String stage, Throwable error) {
        StringWriter stack = new StringWriter(); error.printStackTrace(new PrintWriter(stack));
        String code = error instanceof com.videogo.exception.BaseException ? " code=" + ((com.videogo.exception.BaseException) error).getErrorCode() : "";
        log(stage + " ERROR" + code + " " + stack);
    }
    void storageCheck() throws IOException {
        if (root.getUsableSpace() < 32L * 1024 * 1024 || size(root) > 512L * 1024 * 1024)
            throw new IOException("空间不足：需32MB空闲且会话总量不超过512MB；请先导出，再重新登录清理");
    }
    static long size(File file) {
        if (file.isFile()) return file.length();
        long total=0; File[] children=file.listFiles();
        if(children!=null) for(File child:children) total+=size(child);
        return total;
    }
    static void remove(File file) {
        File[] children=file.listFiles(); if(children!=null) for(File child:children) remove(child);
        if(file.exists() && !file.delete()) Log.w("LanProbe","Temporary evidence cleanup failed");
    }
    synchronized void export(OutputStream target) throws IOException {
        try(ZipOutputStream zip=new ZipOutputStream(target)) { zipDirectory(zip,session,session.getName()+"/"); }
    }
    private void zipDirectory(ZipOutputStream zip,File folder,String prefix) throws IOException {
        File[] children=folder.listFiles(); if(children==null) return;
        Arrays.sort(children,Comparator.comparing(File::getName));
        for(File child:children) {
            if(child.getName().startsWith("temp-")) continue;
            if(child.isDirectory()) zipDirectory(zip,child,prefix+child.getName()+"/");
            else {
                zip.putNextEntry(new ZipEntry(prefix+child.getName()));
                try(InputStream in=new FileInputStream(child)) { byte[] b=new byte[8192]; int n; while((n=in.read(b))!=-1) zip.write(b,0,n); }
                zip.closeEntry();
            }
        }
    }
}
