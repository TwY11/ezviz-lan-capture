package com.face.emotion.lanprobe;

import android.app.Application;
import android.net.wifi.WifiManager;
import android.os.*;
import android.view.SurfaceHolder;
import com.ezviz.hcnetsdk.EZLoginDeviceInfo;
import com.videogo.openapi.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Serial SDK ownership; callback audio decoding has its own bounded worker. */
final class ProbeController {
    interface Ui {
        void device(String serial,String ip,int port);
        void status(String text);
        void controls(boolean initialized,boolean playing,boolean busy);
    }
    static final class Config {
        final String ip,user,password;final int port,stream;
        Config(String ip,int port,String user,String password,int stream){this.ip=ip;this.port=port;this.user=user;this.password=password;this.stream=stream;}
    }
    private static final AtomicBoolean INIT_ATTEMPTED=new AtomicBoolean();
    private static volatile boolean initialized;
    private final Application app;
    private final ProbeJournal journal;
    private final Ui ui;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ScheduledThreadPoolExecutor work=new ScheduledThreadPoolExecutor(1);
    private final AtomicLong generation=new AtomicLong();
    private EZPlayer player;
    private int login=-1;
    private boolean playing,searching,connecting,exporting,initializing,cleanupFailed;
    private long lastOsd,progressAt;
    private WifiManager.MulticastLock multicast;
    private volatile byte[] streamHeader;
    private volatile CaptureJob capture;
    ProbeController(Application app,ProbeJournal journal,Ui ui){
        this.app=app;this.journal=journal;this.ui=ui;
        work.setRemoveOnCancelPolicy(true);work.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        controls();
    }
    private boolean accepts(long id){return generation.get()==id;}
    private void status(String message){main.post(()->ui.status(message));}
    private void controls(){boolean ready=initialized,live=playing,busy=capture!=null||connecting||searching||exporting||initializing;main.post(()->ui.controls(ready,live,busy));}
    private void submit(Runnable task){if(!work.isShutdown())try{work.execute(task);}catch(RejectedExecutionException ignored){}}
    private void later(long id,long delay,Runnable task){
        if(work.isShutdown())return;
        try{work.schedule(()->{if(accepts(id))try{task.run();}catch(Exception e){fail("操作失败",e);}},Math.max(0,delay),TimeUnit.MILLISECONDS);}catch(RejectedExecutionException ignored){}
    }
    void init(String key){journal.secret(key);submit(()->{
        if(!INIT_ATTEMPTED.compareAndSet(false,true)){status(initialized?"SDK已就绪，可连接设备":"初始化已失败，请退出应用进程后重试");controls();return;}
        initializing=true;controls();status("正在初始化SDK…");long start=SystemClock.elapsedRealtime();
        try{EZOpenSDK.showSDKLog(false);EZOpenSDK.setDebugStreamEnable(false);EZOpenSDK.enableSDKWithTKToken(false);
            initialized=EZOpenSDK.initLib(app,key)&&EZOpenSDK.getInstance()!=null;
            journal.log("INIT ready="+initialized+" sdk="+EZOpenSDK.getVersion()+" ms="+(SystemClock.elapsedRealtime()-start));
            status(initialized?"SDK已就绪，请搜索或填写设备地址":"初始化失败，请查看运行信息");
        }catch(Exception e){journal.error("INIT",e);status("初始化失败，请退出应用进程后重试");}
        finally{initializing=false;controls();}
    });}
    void search(){long id=generation.incrementAndGet();submit(()->{
        cleanup();if(!accepts(id))return;
        if(!initialized){status("请先初始化SDK");controls();return;}
        if(cleanupFailed){status("释放设备失败，请重新打开应用进程");controls();return;}
        try{
            WifiManager wifi=(WifiManager)app.getSystemService(Application.WIFI_SERVICE);
            multicast=wifi.createMulticastLock("lan-capture-search");multicast.setReferenceCounted(false);multicast.acquire();
            Set<String> seen=new HashSet<>();searching=true;controls();status("正在搜索局域网设备，最多15秒…");
            boolean ok=EZHCNetDeviceSDK.getInstance().startLocalSearch(device->later(id,0,()->{
                if(searching&&seen.add(device.getDeviceSerial()))main.post(()->{if(accepts(id))ui.device(device.getDeviceSerial(),device.getLocalIp(),device.getLocalPort());});
            }));
            if(!ok)throw new IOException("设备搜索接口拒绝请求");
            later(id,15000,()->{stopSearch();controls();status(seen.isEmpty()?"未找到设备，请检查同一局域网或手动填写IP":"找到 "+seen.size()+" 台设备，选择后登录");});
        }catch(Exception e){fail("设备搜索失败",e);}
    });}
    void connect(Config config,SurfaceHolder surface){
        journal.secret(config.password);long id=generation.incrementAndGet();
        submit(()->{
            cleanup();if(!accepts(id))return;
            if(!initialized){status("请先初始化SDK");controls();return;}
            if(cleanupFailed){status("资源释放失败，请重新打开应用进程");controls();return;}
            connecting=true;controls();status("正在登录设备…");
            try{
                if(surface==null||!surface.getSurface().isValid())throw new IOException("播放区域尚未就绪");
                EZLoginDeviceInfo info=EZHCNetDeviceSDK.getInstance().loginDeviceWithUerName(config.user,config.password,config.ip,config.port);
                if(info==null||info.getLoginId()<0)throw new IOException("本地账号登录失败");
                login=info.getLoginId();if(!accepts(id)){cleanup();controls();return;}
                journal.beginLogin();streamHeader=null;
                int channel=info.getByChanNum()>0?info.getByStartChan():info.getByIPChanNum()>0?info.getByStartDChan():-1;
                if(channel<0)throw new IOException("没有可用视频通道");
                player=EZOpenSDK.getInstance().createPlayerWithUserId(login,channel,config.stream);
                if(player==null)throw new IOException("创建局域网播放器失败");
                player.setOriginDataCallback((type,data,length)->{
                    if(!accepts(id))return;
                    if(type==1&&data!=null&&length==40&&length<=data.length)streamHeader=Arrays.copyOf(data,length);
                    CaptureJob job=capture;if(job!=null)job.audio().accept(type,data,length,SystemClock.elapsedRealtime());
                });
                player.setSurfaceHold(surface);player.setPlayVerifyCode(config.password);
                player.setHandler(new Handler(Looper.getMainLooper(),message->{int what=message.what,code=message.arg1;later(id,0,()->message(id,what,code));return true;}));
                if(!player.startRealPlay())throw new IOException("启动播放失败");
                status("已登录，等待实时画面…");
                later(id,15000,()->{if(!playing)fail("播放超时",new IOException("15秒未收到播放成功消息"));});
            }catch(Exception e){if(accepts(id))fail("连接失败",e);else{cleanup();controls();}}
        });
    }
    private void message(long id,int what,int code){
        if(what==EZConstants.EZRealPlayConstants.MSG_REALPLAY_PLAY_SUCCESS&&!playing){
            playing=true;connecting=false;progressAt=SystemClock.elapsedRealtime();lastOsd=0;
            try{journal.log("REMOTE_SOUND enabled="+player.openSound());}catch(Exception e){journal.error("REMOTE_SOUND",e);}
            status("设备已连接，可以开始采集");controls();watchdog(id);
        }else if(what==EZConstants.EZRealPlayConstants.MSG_REALPLAY_PLAY_FAIL||what==EZConstants.EZRealPlayConstants.MSG_REALPLAY_PASSWORD_ERROR||what==EZConstants.EZRealPlayConstants.MSG_REALPLAY_ENCRYPT_PASSWORD_ERROR){
            fail("播放失败",new IOException("SDK消息="+what+" 错误码="+code));
        }
    }
    private void watchdog(long id){later(id,2000,()->{
        if(!playing||player==null)return;
        try{Calendar osd=player.getOSDTime();long time=osd==null?0:osd.getTimeInMillis();if(time>0&&time!=lastOsd){lastOsd=time;progressAt=SystemClock.elapsedRealtime();}}catch(Exception e){journal.log("OSD unavailable");}
        if(SystemClock.elapsedRealtime()-progressAt>=15000){fail("疑似断流",new IOException("播放时间15秒未推进，请检查摄像头后手动重连"));return;}
        watchdog(id);
    });}
    void capture(CaptureSettings settings,boolean comparison){submit(()->{
        if(!playing){status("请先登录并播放");return;}
        if(capture!=null){status("正在采集，请等待完成");return;}
        try{
            journal.storageCheck();capture=comparison?new TenSecondCapture(journal.session,streamHeader):new MediaCapture(journal.session,streamHeader,settings);
            controls();status(comparison?"10秒对照采集中，请保持前台":"正在采集："+settings.label());
            captureFrame(generation.get(),capture,0);
        }catch(Exception e){journal.error("CAPTURE_START",e);status("无法开始采集："+e.getMessage());controls();}
    });}
    private void captureFrame(long id,CaptureJob job,int slot){
        CaptureSettings settings=job.settings();
        later(id,job.start()+settings.slotMs(slot)-SystemClock.elapsedRealtime(),()->{
            if(capture!=job)return;
            if(slot>=settings.frames||SystemClock.elapsedRealtime()>=job.start()+settings.durationMs){finishCapture(true);controls();return;}
            job.frame(player,journal,slot);
            if(!accepts(id))return;
            int next=Math.min(settings.frames,settings.next(slot,SystemClock.elapsedRealtime()-job.start()));
            captureFrame(id,job,next);
        });
    }
    private void finishCapture(boolean complete){
        CaptureJob job=capture;if(job==null)return;
        status("正在处理音频并保存…");
        try{String result=job.finish(journal,complete);status(result);}
        catch(Exception e){journal.error("CAPTURE_FINISH",e);status("采集保存失败，请查看运行信息");}
        finally{capture=null;}
    }
    void stop(String reason){generation.incrementAndGet();CaptureJob job=capture;if(job!=null)job.audio().closeInput();submit(()->{cleanup();controls();status("已停止 · "+reason);});}
    private void fail(String message,Exception e){journal.error(message,e);generation.incrementAndGet();cleanup();controls();status(message+"："+e.getMessage()+"；请手动重连");}
    private void stopSearch(){
        if(searching){try{EZHCNetDeviceSDK.getInstance().stopLocalSearch();}catch(Exception e){journal.error("SEARCH_STOP",e);}searching=false;}
        if(multicast!=null){if(multicast.isHeld())multicast.release();multicast=null;}
    }
    private void cleanup(){
        finishCapture(false);streamHeader=null;cleanupFailed=false;
        if(player!=null){try{player.stopRealPlay();}catch(Exception e){cleanupFailed=true;journal.error("PLAY_STOP",e);}try{player.release();}catch(Exception e){cleanupFailed=true;journal.error("RELEASE",e);}player=null;}
        if(login>=0){try{if(!EZHCNetDeviceSDK.getInstance().logoutDeviceWithUserId(login))cleanupFailed=true;}catch(Exception e){cleanupFailed=true;journal.error("LOGOUT",e);}login=-1;}
        stopSearch();playing=false;connecting=false;
    }
    void export(android.content.ContentResolver resolver,android.net.Uri uri){generation.incrementAndGet();submit(()->{
        cleanup();exporting=true;controls();status("正在导出本次登录的采集结果…");
        try(OutputStream out=resolver.openOutputStream(uri)){if(out==null)throw new IOException("无法创建导出文件");journal.export(out);status("导出成功；下次登录成功会清理应用内旧记录");}
        catch(Exception e){journal.error("EXPORT",e);status("导出失败："+e.getMessage());}
        finally{exporting=false;controls();}
    });}
    void close(){stop("页面关闭");work.shutdown();}
}
