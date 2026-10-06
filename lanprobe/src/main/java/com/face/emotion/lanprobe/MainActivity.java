package com.face.emotion.lanprobe;

import android.app.*;
import android.content.*;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.*;
import android.widget.*;

/** Credential-free persisted settings; compact production capture controls. */
public class MainActivity extends Activity implements SurfaceHolder.Callback {
    private static final int INK=0xff17243b,MUTED=0xff66758c,BLUE=0xff2864eb,BG=0xfff3f6fb;
    private ProbeController controller;
    private TextView status,settingsLabel,connectionTitle,previewHint;
    private LinearLayout devices,connectionBody;
    private EditText key,ip,port,user,password;
    private Spinner stream;
    private Button initialize,search,connect,start,compare,configure,stop,export;
    private ProgressBar progress;
    private SurfaceView video;
    private CaptureSettings settings=CaptureSettings.DEFAULT;
    private final StringBuilder recent=new StringBuilder();
    private boolean destroyed,wasPlaying;
    private int dp(int value){return Math.round(getResources().getDisplayMetrics().density*value);}
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        try{android.content.SharedPreferences prefs=getSharedPreferences("capture_settings",MODE_PRIVATE);settings=new CaptureSettings(prefs.getInt("fps",8),prefs.getInt("rate",32000),prefs.getInt("seconds",2));}catch(IllegalArgumentException ignored){}
        LinearLayout root=column();root.setBackgroundColor(BG);root.setPadding(dp(16),dp(10),dp(16),0);
        TextView title=text("局域网采集",25,INK);title.setTypeface(null,Typeface.BOLD);root.addView(title);
        TextView subtitle=text("LOCAL CAPTURE  /  3.0",11,MUTED);subtitle.setPadding(0,dp(3),0,dp(10));root.addView(subtitle);

        FrameLayout preview=new FrameLayout(this);preview.setBackground(round(0xff111d31,16));
        video=new SurfaceView(this);video.getHolder().addCallback(this);
        preview.addView(video,new FrameLayout.LayoutParams(-1,-1));
        previewHint=text("连接设备后显示实时画面",14,Color.WHITE);previewHint.setGravity(Gravity.CENTER);previewHint.setBackgroundColor(0xff111d31);
        preview.addView(previewHint,new FrameLayout.LayoutParams(-1,-1));
        int previewHeight=getResources().getConfiguration().screenHeightDp<700?140:185;
        root.addView(preview,new LinearLayout.LayoutParams(-1,dp(previewHeight)));
        status=text("初始化SDK后，连接同一局域网的摄像头",13,INK);status.setPadding(dp(4),dp(10),dp(4),dp(8));status.setMinHeight(dp(52));status.setMaxLines(4);root.addView(status);
        progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setIndeterminate(true);progress.setIndeterminateTintList(ColorStateList.valueOf(BLUE));progress.setVisibility(View.INVISIBLE);root.addView(progress,new LinearLayout.LayoutParams(-1,dp(3)));

        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);LinearLayout content=column();content.setPadding(0,dp(8),0,dp(16));scroll.addView(content);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));

        LinearLayout captureCard=card(content);
        TextView captureTitle=text("音视频采集",18,INK);captureTitle.setTypeface(null,Typeface.BOLD);captureCard.addView(captureTitle);
        settingsLabel=text("",13,MUTED);settingsLabel.setPadding(0,dp(8),0,dp(4));captureCard.addView(settingsLabel);refreshSettings();
        configure=button(captureCard,"采集设置",false,this::showSettings);
        start=button(captureCard,"开始采集",true,()->controller.capture(settings,false));
        TextView mediaHint=text("只保存帧图片与重采样后的WAV音频。",12,MUTED);mediaHint.setPadding(0,dp(3),0,dp(7));captureCard.addView(mediaHint);
        compare=button(captureCard,"采集10秒画面＋音频",false,()->controller.capture(new CaptureSettings(8,32000,10),true));
        captureCard.addView(text("固定8帧/秒 · 32kHz；保留声谱图与人工对照页面。",12,MUTED));

        LinearLayout actions=card(content);
        LinearLayout row=new LinearLayout(this);actions.addView(row);
        stop=button(row,"停止",false,()->controller.stop("用户停止"));
        export=button(row,"导出本次登录ZIP",false,this::export);
        actions.addView(text("新登录成功会删除旧会话；离开页面会停止采集。",12,MUTED));

        LinearLayout connection=card(content);
        connectionTitle=text("设备连接  ▾",17,INK);connectionTitle.setTypeface(null,Typeface.BOLD);connectionTitle.setPadding(0,dp(8),0,dp(8));connection.addView(connectionTitle);
        connectionBody=column();connection.addView(connectionBody);
        connectionTitle.setOnClickListener(v->setConnectionExpanded(connectionBody.getVisibility()!=View.VISIBLE));
        key=field(connectionBody,"AppKey · 仅本次运行使用","",true);
        initialize=button(connectionBody,"初始化SDK",true,()->{String value=key.getText().toString().trim();if(value.isEmpty()){key.setError("请输入真实AppKey");return;}hideKeyboard();controller.init(value);});
        search=button(connectionBody,"搜索局域网设备",false,()->{devices.removeAllViews();hideKeyboard();controller.search();});
        devices=column();connectionBody.addView(devices);
        ip=field(connectionBody,"设备IP","",false);
        port=field(connectionBody,"服务端口","8000",false);port.setInputType(InputType.TYPE_CLASS_NUMBER);
        user=field(connectionBody,"本地用户名","admin",false);
        password=field(connectionBody,"设备密码 / 验证码 · 不保存","",true);
        connectionBody.addView(text("视频码流",12,MUTED));
        stream=new Spinner(this);stream.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"主码流","子码流"}));connectionBody.addView(stream);
        connect=button(connectionBody,"登录并播放 / 重新连接",true,this::connect);
        connectionBody.addView(text("无需AccessToken；不使用手机摄像头或麦克风。",12,MUTED));
        button(content,"查看运行信息",false,this::showRecent);

        ProbeJournal journal=new ProbeJournal(this,line->runOnUiThread(()->{if(!destroyed){recent.append(line);if(recent.length()>16000)recent.delete(0,recent.length()-12000);}}));
        controller=new ProbeController(getApplication(),journal,new ProbeController.Ui(){
            public void device(String serial,String address,int servicePort){
                if(destroyed)return;
                button(devices,serial+" · "+address,false,()->{ip.setText(address);port.setText(String.valueOf(servicePort));});
                if(ip.getText().toString().isEmpty()){ip.setText(address);port.setText(String.valueOf(servicePort));}
            }
            public void status(String value){if(!destroyed)status.setText(value);}
            public void controls(boolean ready,boolean playing,boolean busy){
                if(destroyed)return;
                initialize.setEnabled(!ready&&!busy);key.setEnabled(!ready&&!busy);
                search.setEnabled(ready&&!busy);connect.setEnabled(ready&&!busy);
                start.setEnabled(playing&&!busy);compare.setEnabled(playing&&!busy);configure.setEnabled(!busy);
                stop.setEnabled(playing||busy);export.setEnabled(!busy);
                for(EditText field:new EditText[]{ip,port,user,password})field.setEnabled(!busy);
                stream.setEnabled(!busy);progress.setVisibility(busy?View.VISIBLE:View.INVISIBLE);
                previewHint.setVisibility(playing?View.GONE:View.VISIBLE);
                if(playing&&!wasPlaying)setConnectionExpanded(false);
                wasPlaying=playing;
            }
        });
        setContentView(root);
    }
    private LinearLayout column(){LinearLayout layout=new LinearLayout(this);layout.setOrientation(LinearLayout.VERTICAL);return layout;}
    private GradientDrawable round(int color,int radius){GradientDrawable shape=new GradientDrawable();shape.setColor(color);shape.setCornerRadius(dp(radius));return shape;}
    private TextView text(String value,int size,int color){TextView view=new TextView(this);view.setText(value);view.setTextSize(size);view.setTextColor(color);return view;}
    private LinearLayout card(LinearLayout parent){LinearLayout card=column();card.setPadding(dp(14),dp(12),dp(14),dp(12));card.setBackground(round(Color.WHITE,16));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(12);parent.addView(card,p);return card;}
    private EditText field(LinearLayout parent,String label,String value,boolean secret){
        TextView caption=text(label,12,MUTED);caption.setPadding(0,dp(10),0,dp(3));parent.addView(caption);
        EditText edit=new EditText(this);edit.setText(value);edit.setSingleLine();edit.setSaveEnabled(false);edit.setTextSize(15);edit.setTextColor(INK);
        edit.setInputType(InputType.TYPE_CLASS_TEXT|(secret?InputType.TYPE_TEXT_VARIATION_PASSWORD:InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS));
        edit.setBackgroundTintList(ColorStateList.valueOf(0xffaebcd2));parent.addView(edit,new LinearLayout.LayoutParams(-1,dp(48)));return edit;
    }
    private Button button(LinearLayout parent,String title,boolean primary,Runnable action){
        Button view=new Button(this);view.setText(title);view.setTextSize(14);view.setAllCaps(false);view.setMinHeight(dp(46));view.setMinimumHeight(dp(46));
        view.setTextColor(new ColorStateList(new int[][]{new int[]{-android.R.attr.state_enabled},new int[]{}},new int[]{0xff8a97ab,primary?Color.WHITE:BLUE}));
        view.setBackground(round(primary?BLUE:0xffedf3ff,10));
        view.setBackgroundTintList(new ColorStateList(new int[][]{new int[]{-android.R.attr.state_enabled},new int[]{}},new int[]{0xffe6ebf3,primary?BLUE:0xffedf3ff}));
        LinearLayout.LayoutParams p=parent.getOrientation()==LinearLayout.HORIZONTAL?new LinearLayout.LayoutParams(0,dp(48),1):new LinearLayout.LayoutParams(-1,dp(48));p.topMargin=dp(7);p.bottomMargin=dp(7);if(parent.getOrientation()==LinearLayout.HORIZONTAL)p.setMargins(dp(3),dp(3),dp(3),dp(8));
        parent.addView(view,p);view.setOnClickListener(v->action.run());return view;
    }
    private void refreshSettings(){settingsLabel.setText(settings.label());}
    private void setConnectionExpanded(boolean expanded){connectionBody.setVisibility(expanded?View.VISIBLE:View.GONE);connectionTitle.setText(expanded?"设备连接  ▾":"设备连接  ▸");}
    private void showSettings(){
        LinearLayout form=column();form.setPadding(dp(22),dp(4),dp(22),dp(8));
        EditText fps=field(form,"目标帧率 · 1～30帧/秒",String.valueOf(settings.fps),false);fps.setInputType(InputType.TYPE_CLASS_NUMBER);
        form.addView(text("输出音频采样率",12,MUTED));Spinner rate=new Spinner(this);String[] labels=new String[CaptureSettings.RATES.length];int selected=0;
        for(int i=0;i<labels.length;i++){labels[i]=(CaptureSettings.RATES[i]/1000.0)+" kHz";if(CaptureSettings.RATES[i]==settings.rate)selected=i;}
        rate.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,labels));rate.setSelection(selected);form.addView(rate);
        EditText duration=field(form,"单次采集时长 · 1～60秒",String.valueOf(settings.seconds),false);duration.setInputType(InputType.TYPE_CLASS_NUMBER);
        TextView note=text("每段最多600张。高帧率不保证达标；未达到目标会明确提示。设置仅影响普通采集。",12,MUTED);form.addView(note);
        ScrollView scroll=new ScrollView(this);scroll.addView(form);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("采集设置").setView(scroll).setNegativeButton("取消",null).setPositiveButton("保存",null).create();
        dialog.setOnShowListener(ignored->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            try{
                settings=new CaptureSettings(Integer.parseInt(fps.getText().toString()),CaptureSettings.RATES[rate.getSelectedItemPosition()],Integer.parseInt(duration.getText().toString()));
                getSharedPreferences("capture_settings",MODE_PRIVATE).edit().putInt("fps",settings.fps).putInt("rate",settings.rate).putInt("seconds",settings.seconds).apply();
                refreshSettings();dialog.dismiss();
            }catch(IllegalArgumentException e){note.setText(e instanceof NumberFormatException?"请输入完整的整数参数":e.getMessage());note.setTextColor(0xffb42318);}
        }));dialog.show();
    }
    private void hideKeyboard(){View view=getCurrentFocus();if(view!=null)((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(view.getWindowToken(),0);}
    private void connect(){
        try{
            int number=Integer.parseInt(port.getText().toString());if(number<1||number>65535)throw new IllegalArgumentException("端口范围为1～65535");
            String address=ip.getText().toString().trim(),name=user.getText().toString().trim(),pass=password.getText().toString();
            if(address.isEmpty()||name.isEmpty()||pass.isEmpty())throw new IllegalArgumentException("请填写设备IP、用户名和密码");
            hideKeyboard();controller.connect(new ProbeController.Config(address,number,name,pass,stream.getSelectedItemPosition()+1),video.getHolder());
        }catch(IllegalArgumentException e){status.setText(e instanceof NumberFormatException?"端口必须为整数":e.getMessage());}
    }
    private void export(){
        controller.stop("准备导出");
        Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/zip");
        intent.putExtra(Intent.EXTRA_TITLE,"lan-capture-"+System.currentTimeMillis()+".zip");startActivityForResult(intent,1);
    }
    private void showRecent(){
        TextView log=text(recent.length()==0?"暂无运行信息":recent.toString(),12,INK);log.setTextIsSelectable(true);log.setPadding(dp(16),dp(12),dp(16),dp(12));
        ScrollView scroll=new ScrollView(this);scroll.addView(log);
        new AlertDialog.Builder(this).setTitle("运行信息 · 不保存为文件").setView(scroll).setPositiveButton("关闭",null).show();
    }
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==1&&result==RESULT_OK&&data!=null&&data.getData()!=null)controller.export(getContentResolver(),data.getData());}
    @Override protected void onStop(){super.onStop();if(controller!=null)controller.stop("页面离开前台");}
    @Override protected void onDestroy(){destroyed=true;if(controller!=null)controller.close();super.onDestroy();}
    @Override public void surfaceCreated(SurfaceHolder holder){}
    @Override public void surfaceChanged(SurfaceHolder holder,int format,int width,int height){}
    @Override public void surfaceDestroyed(SurfaceHolder holder){if(controller!=null)controller.stop("播放区域已关闭");}
}
