package cn.lut.schedule;

import android.app.Activity;

import android.app.Dialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.animation.DecelerateInterpolator;
import android.webkit.CookieManager;
import android.webkit.SslErrorHandler;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceResponse;
import android.widget.ProgressBar;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import org.json.JSONObject;
import org.json.JSONArray;
import android.graphics.drawable.Drawable;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class MainActivity extends Activity {
    private SharedPreferences prefs;
    private SnapshotStore store;
    private ScheduleCore.Snapshot snapshot;
    private final ScheduleCore.SyncState sync=new ScheduleCore.SyncState();
    private SiteGateway gateway;
    private SyncCoordinator coordinator;
    private AcademicStore academicStore;
    private JSONArray pendingChanges=new JSONArray(),examRecords=new JSONArray();
    private LinearLayout gradeBanner;
    private final List<View> adaptiveViews=new ArrayList<>();
    private boolean foreground,userLeftApp,loginSyncQueued;
    private String officialService="";
    private int officialNavigationAttempts;
    private LinearLayout root,nav;
    private FrameLayout pages,probeHost;
    private TextView statusText;
    private StatusIcon statusIcon;
    private TimetableView timetable;
    private Bitmap background;
    private float backgroundPage;
    private int transitionAlpha;
    private android.animation.ValueAnimator backgroundAnimator;
    private boolean buildingPage;
    private ScrollView settingsScroll;
    private AppDialog errorDialog;
    private boolean dark,alive=true,returnFromBackground,loginShownThisLaunch,initialized;
    private int page=0,week=1;
    private int ink,muted,surface,base,accent,border;
    private int screenForeground=Color.BLACK;private String foregroundKey="";
    private int developerTaps;private long developerTapTime;private View developerRow;private AppDialog contactDialog;
    private String detail="尚未完成官网同步";
    private Dialog loginDialog;
    private BackgroundCrop backgroundCrop;
    private AppDialog handDialog;
    private WebView loginWeb;
    private boolean readingLoginPage;
    private boolean loginPageFailed;
    private TextView loginConnectionStatus;
    private final Handler handler=new Handler();
    private static final int IMAGE=31,IMPORT=32;
    private static final ZoneId SCHOOL_TIME=ZoneId.of("Asia/Shanghai");

    public void onCreate(Bundle state){
        super.onCreate(state);
        try {
            prefs=getSharedPreferences("settings",MODE_PRIVATE);dark=isDark();
            setTheme(AppDialog.theme(this,dark));
            store=new SnapshotStore(this);AccessMode.migrate(prefs,store);academicStore=new AcademicStore(this);loadAcademic();loadSnapshot();loadBackground();buildRoot();initialized=true;Diagnostics.clearFatal(this);if(state==null&&(getIntent().getBooleanExtra("user_launch",false)||prefs.getLong("last_operation",0)==0))IdleRefresh.operated(this);
            if(AccessMode.official(this))handler.postDelayed(this::refresh,400);if(snapshot!=null&&!prefs.contains("handedness"))handler.postDelayed(this::firstHandedness,300);
        } catch(RuntimeException | LinkageError error){Diagnostics.record(this,"课表启动",error,true);Diagnostics.showRecovery(this);}
    }
    private boolean isDark(){String mode=prefs.getString("theme","light");return mode.equals("dark") || mode.equals("system") && (getResources().getConfiguration().uiMode&Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES;}
    private void colors(){dark=isDark();ink=Color.parseColor(dark?"#F1F1F1":"#222222");muted=Color.parseColor(dark?"#B3B3B3":"#666666");surface=Color.parseColor(dark?"#252525":"#FFFFFF");base=Color.parseColor(dark?"#121212":"#F5F5F5");accent=Color.parseColor(dark?"#DEDEDE":"#333333");border=Color.parseColor(dark?"#3A3A3A":"#E0E0E0");}
    private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private TextView text(String value,float size,int color){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setIncludeFontPadding(false);if(buildingPage)adaptiveViews.add(t);return t;}
    private TextView title(String value,float size){TextView t=text(value,size,ink);t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;}
    private GradientDrawable shape(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private void clickable(View v,int fill){v.setBackground(new RippleDrawable(ColorStateList.valueOf(0x22888888),shape(fill,3),null));}
    private LinearLayout vertical(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);return v;}
    private LinearLayout row(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.HORIZONTAL);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
    private void space(LinearLayout l,int height){View v=new View(this);l.addView(v,new LinearLayout.LayoutParams(1,dp(height)));}
    private TextView action(String label,Runnable callback){TextView t=text(label,14,accent);t.setGravity(Gravity.CENTER);t.setPadding(dp(14),dp(13),dp(14),dp(13));clickable(t,Color.TRANSPARENT);t.setOnClickListener(v->{developerTaps=0;callback.run();});return t;}
    private void buildRoot(){
        colors();root=vertical();backgroundPage=tabPosition(page);root.setBackground(new Wallpaper());
        if(android.os.Build.VERSION.SDK_INT>=30){getWindow().setDecorFitsSystemWindows(false);
            root.setOnApplyWindowInsetsListener((v,insets)->{android.graphics.Insets b=insets.getInsets(WindowInsets.Type.systemBars());v.setPadding(b.left,b.top,b.right,b.bottom);return insets;});
        }
        getWindow().setStatusBarColor(base);getWindow().setNavigationBarColor(surface);
        if(android.os.Build.VERSION.SDK_INT<30)getWindow().getDecorView().setSystemUiVisibility(dark?0:View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        gradeBanner=row();root.addView(gradeBanner,new LinearLayout.LayoutParams(-1,-2));
        pages=new FrameLayout(this);root.addView(pages,new LinearLayout.LayoutParams(-1,0,1));
        nav=row();nav.setPadding(dp(12),dp(8),dp(12),dp(8));nav.setBackgroundColor(surface);root.addView(nav,new LinearLayout.LayoutParams(-1,dp(68)));
        probeHost=new FrameLayout(this);root.addView(probeHost,new LinearLayout.LayoutParams(1,1));
        gateway=new SiteGateway(this,probeHost);coordinator=new SyncCoordinator(this,probeHost,gateway);setContentView(root);renderPage(false);
        root.post(this::adaptForeground);
    }
    private void navigate(int next){developerTaps=0;if(next==page)return;int previous=tabPosition(page);float from=backgroundPage;page=next;int target=tabPosition(next);ErrorReports.breadcrumb(this,"切换页面："+new String[]{"课表","教务","设置"}[next]);renderPage(true);if(backgroundAnimator!=null)backgroundAnimator.cancel();if(animationsEnabled()){View child=pages.getChildAt(0);child.setTranslationX(dp(target>previous?10:-10));child.animate().translationX(0).setDuration(180).setInterpolator(new DecelerateInterpolator()).start();backgroundAnimator=android.animation.ValueAnimator.ofFloat(from,target);backgroundAnimator.setDuration(240);backgroundAnimator.setInterpolator(new DecelerateInterpolator());backgroundAnimator.addUpdateListener(a->{backgroundPage=(Float)a.getAnimatedValue();transitionAlpha=prefs.getString("background_layout","shared").equals("split")?0:Math.round(24*(float)Math.sin(Math.PI*a.getAnimatedFraction()));root.invalidate();});backgroundAnimator.addListener(new android.animation.AnimatorListenerAdapter(){public void onAnimationEnd(android.animation.Animator a){backgroundPage=tabPosition(page);transitionAlpha=0;root.invalidate();adaptForeground();}});backgroundAnimator.start();}else{backgroundPage=target;transitionAlpha=0;root.invalidate();adaptForeground();}}
    private void renderPage(boolean animate){
        adaptiveViews.clear();developerRow=null;pages.removeAllViews();timetable=null;statusText=null;statusIcon=null;
        root.setBackground(new Wallpaper());nav.setBackgroundColor(Color.TRANSPARENT);updateScreenForeground();refreshBanner();
        buildingPage=true;View child=page==0?home():page==1?academic():settings();buildingPage=false;ErrorReports.context(this,page,periodCount(),snapshot==null?0:snapshot.courses.size(),examRecords.length(),snapshot==null?"NONE":snapshot.source.name(),sync.status().name());pages.addView(child,new FrameLayout.LayoutParams(-1,-1));
        if(animate && animationsEnabled()){child.setAlpha(0);child.animate().alpha(1).setDuration(180).setInterpolator(new DecelerateInterpolator()).start();}
        else child.setAlpha(1);
        nav.removeAllViews();String[] labels={"课表","教务","设置"};
        int[] order=AppRules.tabOrder(prefs.getString("tab_order",null));for(int slot=0;slot<3;slot++){final int i=order[slot],next=i;LinearLayout item=vertical();item.setGravity(Gravity.CENTER);clickable(item,Color.TRANSPARENT);
            NavIcon icon=new NavIcon(i,i==page);item.addView(icon,new LinearLayout.LayoutParams(dp(23),dp(23)));space(item,5);
            TextView label=text(labels[i],12,i==page?accent:muted);label.setGravity(Gravity.CENTER);item.addView(label);item.setContentDescription(labels[i]);item.setOnClickListener(v->navigate(next));
            adaptiveViews.add(icon);adaptiveViews.add(label);if(i==page){label.setTypeface(Typeface.DEFAULT_BOLD);}
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-1,1);p.setMargins(dp(5),0,dp(5),0);nav.addView(item,p);}
        root.post(this::adaptForeground);
    }
    private boolean animationsEnabled(){try{return android.provider.Settings.Global.getFloat(getContentResolver(),android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,1)>0;}catch(Exception e){return true;}}
    private LinearLayout pageHeader(String name,String subtitle){LinearLayout header=vertical();header.setPadding(dp(22),dp(24),dp(22),dp(16));
        header.addView(text(subtitle,11,muted));space(header,7);header.addView(title(name,28));return header;}
    private View home(){
        if(snapshot==null)return entryChoices();
        LinearLayout view=vertical();
        LinearLayout tools=row();tools.setPadding(dp(16),dp(2),dp(10),dp(2));
        TextView term=text(snapshot==null?"课表尚未同步":snapshot.semester,12,ink);tools.addView(term,new LinearLayout.LayoutParams(0,dp(44),1));term.setGravity(Gravity.CENTER_VERTICAL);adaptiveViews.add(term);
        TextView list=plainAction("课程",this::showWeekCourses);tools.addView(list,new LinearLayout.LayoutParams(dp(52),dp(44)));
        statusIcon=new StatusIcon();statusIcon.setContentDescription("最近更新日期与刷新");statusIcon.setOnClickListener(v->showStatus());tools.addView(statusIcon,new LinearLayout.LayoutParams(dp(44),dp(44)));adaptiveViews.add(statusIcon);view.addView(tools);
        FrameLayout tableHost=new FrameLayout(this);LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(-1,0,1);tp.setMargins(dp(8),0,dp(8),0);view.addView(tableHost,tp);
        ScrollView tableScroll=new ScrollView(this);tableScroll.setFillViewport(true);tableScroll.setVerticalScrollBarEnabled(false);timetable=new TimetableView();tableScroll.addView(timetable,new ScrollView.LayoutParams(-1,-1));tableHost.addView(tableScroll,new FrameLayout.LayoutParams(-1,-1));
        tableHost.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{if(timetable==null||page!=0)return;int height=Math.max(b-t,dp(64+periodCount()*37+examBandHeight()));ViewGroup.LayoutParams params=timetable.getLayoutParams();if(params.height!=height){params.height=height;timetable.setLayoutParams(params);}adaptForeground();});
        LinearLayout bottom=row();bottom.setPadding(dp(16),dp(2),dp(8),dp(4));statusText=text(statusSummary(),10,muted);statusText.setMaxLines(2);adaptiveViews.add(statusText);
        LinearLayout dock=row();dock.setContentDescription("周数切换 · "+(prefs.getString("week_side","right").equals("left")?"左下角":"右下角"));
        dock.addView(plainAction("‹",()->changeWeek(-1)),new LinearLayout.LayoutParams(dp(44),dp(44)));
        TextView current=plainAction(snapshot==null?"本周":"第"+week+"周",this::showWeeks);dock.addView(current,new LinearLayout.LayoutParams(dp(56),dp(44)));
        dock.addView(plainAction("›",()->changeWeek(1)),new LinearLayout.LayoutParams(dp(44),dp(44)));dock.addView(plainAction("本周",()->{setCurrentWeek();renderPage(false);}),new LinearLayout.LayoutParams(dp(48),dp(44)));
        if(prefs.getString("week_side","right").equals("left")){bottom.addView(dock);bottom.addView(statusText,new LinearLayout.LayoutParams(0,-2,1));}else{bottom.addView(statusText,new LinearLayout.LayoutParams(0,-2,1));bottom.addView(dock);}view.addView(bottom);return view;
    }
    private View entryChoices(){
        LinearLayout view=vertical();view.setGravity(Gravity.CENTER);view.setPadding(dp(28),dp(24),dp(28),dp(24));
        TextView heading=title("开始使用课表",26);view.addView(heading);space(view,14);
        TextView note=text("登录兰州理工大学教务系统，或导入自己的课表文件。",14,ink);note.setGravity(Gravity.CENTER);note.setLineSpacing(dp(5),1);view.addView(note);space(view,30);
        view.addView(action("登录 LUT 教务系统",this::showLogin),new LinearLayout.LayoutParams(-1,dp(52)));divider(view);space(view,12);
        view.addView(action("导入课表文件",()->pick("*/*",IMPORT)),new LinearLayout.LayoutParams(-1,dp(52)));space(view,16);
        TextView format=text("导入标准 JSON 课表文件；官网服务仅为 LUT 学生提供。",12,ink);format.setGravity(Gravity.CENTER);view.addView(format);return view;
    }
    private TextView plainAction(String value,Runnable run){TextView t=text(value,15,ink);t.setGravity(Gravity.CENTER);t.setBackground(new RippleDrawable(ColorStateList.valueOf(0x22888888),shape(Color.TRANSPARENT,2),null));t.setContentDescription(value.equals("‹")?"上一周":value.equals("›")?"下一周":value);t.setOnClickListener(v->{IdleRefresh.operated(this);run.run();});adaptiveViews.add(t);return t;}
    private void showWeeks(){if(snapshot==null)return;String[] weeks=new String[availableWeeks()];for(int i=0;i<weeks.length;i++)weeks[i]="第 "+(i+1)+" 周";new AppDialog.Builder(this).setTitle("选择教学周").setSingleChoiceItems(weeks,week-1,(d,w)->{IdleRefresh.operated(this);week=w+1;d.dismiss();renderPage(false);}).setNegativeButton("关闭",null).show();}
    private int availableWeeks(){int count=snapshot==null?1:snapshot.totalWeeks;if(snapshot!=null){for(int i=0;i<examRecords.length();i++){JSONObject exam=examRecords.optJSONObject(i);if(exam==null)continue;try{int examWeek=snapshot.weekOf(LocalDate.parse(exam.optString("date")));if(examWeek>0&&examWeek<=60)count=Math.max(count,examWeek);}catch(Exception ignored){}}}return count;}
    private int periodCount(){return snapshot==null?10:snapshot.periodCount;}
    private void firstHandedness(){
        if(snapshot==null||!alive||!foreground||isFinishing()||isDestroyed()||prefs.contains("handedness")||handDialog!=null&&handDialog.isShowing())return;
        handDialog=new AppDialog.Builder(this).setTitle("用哪只手更顺手？").setCancelable(false).setItems(new String[]{"左手 · 左下角","右手 · 右下角"},(d,w)->{
            IdleRefresh.operated(this);prefs.edit().putString("handedness",w==0?"left":"right").putString("week_side",w==0?"left":"right").apply();renderPage(false);handler.postDelayed(this::refresh,250);
        }).create();handDialog.setOnDismissListener(d->handDialog=null);handDialog.show();
    }

    private String statusSummary(){if(AccessMode.IMPORT.equals(prefs.getString("entry_mode",AccessMode.NONE)))return "本地导入课表 · 不连接教务系统";switch(sync.status()){
        case UPDATING:return "正在连接教务系统，当前课表可继续查看…";
        case SUCCESS:return "✓ 教务信息已更新";
        case LOGIN_REQUIRED:return "登录已失效，请重新登录；保留已有课表";
        case OFFLINE:return snapshot==null?"网络未连接，尚无缓存课表":"网络未连接，正在显示缓存课表";
        case SERVER_ERROR:return snapshot==null?"教务系统暂时故障，尚无缓存课表":"教务系统暂时故障，正在显示缓存课表";
        case UNREACHABLE:return snapshot==null?"暂时无法连接教务系统，尚无缓存":"暂时无法连接教务系统，正在显示缓存课表";
        case ADAPTER_PENDING:return "部分信息未更新 · 显示已保存的数据";
        case PARSE_ERROR:return "读取失败，已保留原有课表";
        default:return "尚未完成本次官网同步";
    }}
    private void updateStatus(){if(statusText!=null)statusText.setText(statusSummary());if(statusIcon!=null)statusIcon.invalidate();}
    private void showStatus(){if(!AccessMode.official(this)){explain("最近更新日期",snapshot==null?"尚未登录或导入课表":("本地课表导入时间：\n"+formatTime(snapshot.fetchedAt)+"\n\n官网服务仅为 LUT 学生提供；本地导入不进行官网同步。"));return;}new AppDialog.Builder(this).setTitle("最近更新日期").setMessage("最近完整更新：\n"+formatTime(prefs.getLong("last_full_success",0))+"\n\n最近尝试：\n"+formatTime(prefs.getLong("last_attempt",0))+"\n\n课表："+formatTime(prefs.getLong("timetable_updated",snapshot!=null&&snapshot.source==ScheduleCore.Source.OFFICIAL?snapshot.fetchedAt:0))+"\n成绩："+formatTime(prefs.getLong("grades_updated",0))+"\n考试："+formatTime(prefs.getLong("exams_updated",0))+"\n\n"+detail+"\n\n对号表示本次课表、成绩、考试均完成读取和保存；失败不会清除缓存。").setPositiveButton("关闭",null).setNeutralButton("立即刷新",(d,w)->{IdleRefresh.operated(this);refresh();}).show();}

    private String formatTime(long time){return time<=0?"无":java.time.Instant.ofEpochMilli(time).atZone(SCHOOL_TIME).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))+"（北京时间）";}
    private void changeWeek(int delta){if(snapshot!=null){week=Math.max(1,Math.min(availableWeeks(),week+delta));renderPage(false);}}
    private void setCurrentWeek(){if(snapshot!=null)week=Math.max(1,Math.min(availableWeeks(),snapshot.weekOf(LocalDate.now(SCHOOL_TIME))));}
    private void loadSnapshot(){String mode=prefs.getString("entry_mode",AccessMode.NONE);snapshot=mode.equals(AccessMode.OFFICIAL)?store.load(ScheduleCore.Source.OFFICIAL):mode.equals(AccessMode.IMPORT)?store.load(ScheduleCore.Source.IMPORT):null;setCurrentWeek();}
    private void refresh(){
        if(!alive||!initialized||!AccessMode.official(this)||sync.status()==ScheduleCore.Status.UPDATING)return;ErrorReports.breadcrumb(this,"开始更新课表、成绩和考试");int ticket=sync.begin();detail="正在更新课表、成绩和考试";prefs.edit().putLong("last_attempt",System.currentTimeMillis()).apply();updateStatus();
        ConnectivityManager cm=(ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);NetworkCapabilities caps=null;try{if(cm!=null)caps=cm.getNetworkCapabilities(cm.getActiveNetwork());}catch(RuntimeException e){Diagnostics.record(this,"网络状态读取",e,false);}
        if(caps==null||!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)){sync.finish(ticket,ScheduleCore.Status.OFFLINE,null,0);detail="当前设备没有可用网络；显示本机缓存";ErrorReports.breadcrumb(this,"更新停止：设备离线，保留缓存");updateStatus();return;}
        coordinator.start((result,message)->{if(!alive||!sync.isCurrent(ticket))return;sync.finish(ticket,result,result==ScheduleCore.Status.SUCCESS?ScheduleCore.Source.OFFICIAL:null,result==ScheduleCore.Status.SUCCESS?System.currentTimeMillis():0);detail=message;loadSnapshot();loadAcademic();renderPage(false);if(result!=ScheduleCore.Status.SUCCESS&&result!=ScheduleCore.Status.LOGIN_REQUIRED)Diagnostics.event(this,"教务同步",result.name()+": "+message);if(foreground){GradeNotifications.deliver(this,pendingChanges);if(result==ScheduleCore.Status.LOGIN_REQUIRED&&!loginShownThisLaunch){loginShownThisLaunch=true;showLogin();}}});
    }
    private void loadAcademic(){if(!AccessMode.official(this)){pendingChanges=new JSONArray();examRecords=new JSONArray();return;}pendingChanges=academicStore.pending();JSONObject exams=academicStore.load("exams");examRecords=exams==null?new JSONArray():exams.optJSONArray("records");if(examRecords==null)examRecords=new JSONArray();}
    private void refreshBanner(){if(gradeBanner==null)return;gradeBanner.removeAllViews();int grades=0,exams=0;for(int i=0;i<pendingChanges.length();i++){JSONObject entry=pendingChanges.optJSONObject(i);if(entry!=null){if("grades".equals(entry.optString("kind")))grades++;else if("exams".equals(entry.optString("kind")))exams++;}}
        gradeBanner.setVisibility(grades+exams>0?View.VISIBLE:View.GONE);if(grades+exams==0)return;gradeBanner.setPadding(dp(18),dp(8),dp(12),dp(8));gradeBanner.setBackgroundColor(dark?0xED292929:0xEDF3F3F3);TextView label=text(grades>0?"新成绩 · "+grades+" 项更新":"考试安排 · "+exams+" 项更新",14,ink);gradeBanner.addView(label,new LinearLayout.LayoutParams(0,dp(36),1));label.setGravity(Gravity.CENTER_VERTICAL);TextView read=action("查看",this::showChanges);read.setBackground(new RippleDrawable(ColorStateList.valueOf(0x18888888),shape(Color.TRANSPARENT,2),null));gradeBanner.addView(read);gradeBanner.setContentDescription("教务新内容提醒");gradeBanner.setOnClickListener(v->showChanges());}
    private void showChanges(){JSONArray shown=pendingChanges;StringBuilder message=new StringBuilder();for(int i=0;i<shown.length();i++){JSONObject entry=shown.optJSONObject(i),record=entry==null?null:entry.optJSONObject("record");if(record==null)continue;message.append(record.optString("name")).append("\n");if("grades".equals(entry.optString("kind")))message.append("成绩：").append(record.optString("score"));else message.append(record.optString("date")).append(" ").append(record.optString("startTime")).append("–").append(record.optString("endTime")).append(" ").append(record.optString("room"));message.append("\n\n");}
        new AppDialog.Builder(this).setTitle("教务内容更新").setMessage(message.length()==0?"暂无未读更新":message.toString()).setPositiveButton("已读",(d,w)->{IdleRefresh.operated(this);academicStore.acknowledge(shown);loadAcademic();refreshBanner();}).setNegativeButton("稍后查看",null).show();}

    /** Native validation and atomic persistence must precede the success check icon. */
    void acceptOfficialSnapshot(int ticket,ScheduleCore.Snapshot official){
        if(!sync.isCurrent(ticket))return;
        if(official.source!=ScheduleCore.Source.OFFICIAL)throw new IllegalArgumentException("非法同步来源");
        try{store.save(official);if(sync.finish(ticket,ScheduleCore.Status.ADAPTER_PENDING,null,0)){
            verifiedOfficialSession();snapshot=official;setCurrentWeek();prefs.edit().putLong("timetable_updated",official.fetchedAt).apply();detail="课表已校验保存；成绩和考试尚需更新";renderPage(false);}}
        catch(Exception error){Diagnostics.record(this,"课表缓存保存",error,false);sync.finish(ticket,ScheduleCore.Status.PARSE_ERROR,null,0);detail="保存失败，未更新成功标记";updateStatus();}
    }
    private void showLogin(){showOfficial("官网登录",SiteGateway.HOME);}
    private void showOfficial(String service,String initialUrl){
        if(!alive||isFinishing()||isDestroyed()||!foreground)return;
        if(loginDialog!=null && loginDialog.isShowing())return;
        officialService=service;officialNavigationAttempts=0;loginSyncQueued=false;loginShownThisLaunch=true;loginPageFailed=false;loginDialog=new Dialog(this,AppDialog.theme(this,dark)){public boolean dispatchTouchEvent(MotionEvent event){if(event.getAction()==MotionEvent.ACTION_DOWN)IdleRefresh.operated(MainActivity.this);return super.dispatchTouchEvent(event);}public void onBackPressed(){if(loginWeb!=null&&loginWeb.canGoBack())loginWeb.goBack();else dismiss();}};
        loginDialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);LinearLayout body=vertical();body.setBackgroundColor(AppRules.readableSurface(base,screenForeground));
        LinearLayout header=row();header.setPadding(dp(6),0,dp(6),0);header.addView(action("‹",()->{if(loginWeb!=null&&loginWeb.canGoBack())loginWeb.goBack();else loginDialog.dismiss();}));TextView heading=title(service,14);header.addView(heading,new LinearLayout.LayoutParams(0,-2,1));header.addView(action("更多",this::officialTools));header.addView(action("关闭",()->loginDialog.dismiss()));body.addView(header,new LinearLayout.LayoutParams(-1,dp(48)));
        TextView domain=text("jwxt.lut.edu.cn",11,muted);domain.setVisibility(View.GONE);
        ProgressBar progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setMax(100);body.addView(progress,new LinearLayout.LayoutParams(-1,dp(3)));
        loginConnectionStatus=text("正在打开官网登录首页…",12,muted);loginConnectionStatus.setPadding(dp(16),dp(8),dp(16),dp(8));loginConnectionStatus.setOnClickListener(v->showWebConnection());loginConnectionStatus.setVisibility(View.GONE);body.addView(loginConnectionStatus);
        try{loginWeb=new WebView(this);SiteGateway.configure(loginWeb);}catch(RuntimeException | LinkageError error){
            Diagnostics.record(this,"官网登录组件创建",error,false);WebDiagnostics.record(this,"网页组件创建",SiteGateway.HOME,"系统网页组件暂不可用",0);if(loginWeb!=null){loginWeb.destroy();loginWeb=null;}loginDialog=null;
            toast("系统网页组件暂不可用，课表仍可查看；错误详情已保存在设置中");return;}
        body.addView(loginWeb,new LinearLayout.LayoutParams(-1,0,1));
        loginWeb.getSettings().setSupportMultipleWindows(true);
        loginWeb.setWebChromeClient(new WebChromeClient(){public void onProgressChanged(WebView v,int percent){if(v!=loginWeb)return;progress.setProgress(percent);progress.setVisibility(percent>=100?View.GONE:View.VISIBLE);}public boolean onJsAlert(WebView v,String url,String message,android.webkit.JsResult result){new AppDialog.Builder(MainActivity.this).setTitle("官网提示").setMessage(message).setPositiveButton("知道了",(d,w)->result.confirm()).setCancelable(false).show();return true;}public boolean onJsConfirm(WebView v,String url,String message,android.webkit.JsResult result){boolean[] resolved={false};AppDialog d=new AppDialog.Builder(MainActivity.this).setTitle("官网确认").setMessage(message).setPositiveButton("确认",(x,w)->{resolved[0]=true;result.confirm();}).setNegativeButton("取消",(x,w)->{resolved[0]=true;result.cancel();}).show();d.setOnDismissListener(x->{if(!resolved[0])result.cancel();});return true;}public boolean onJsPrompt(WebView v,String url,String message,String initial,android.webkit.JsPromptResult result){boolean[] resolved={false};EditText input=new EditText(MainActivity.this);input.setText(initial);AppDialog d=new AppDialog.Builder(MainActivity.this).setTitle("官网输入").setMessage(message).setView(input).setPositiveButton("确认",(x,w)->{resolved[0]=true;result.confirm(input.getText().toString());}).setNegativeButton("取消",(x,w)->{resolved[0]=true;result.cancel();}).show();d.setOnDismissListener(x->{if(!resolved[0])result.cancel();});return true;}public boolean onCreateWindow(WebView source,boolean dialog,boolean user,android.os.Message message){if(source!=loginWeb)return false;WebView child=new WebView(MainActivity.this);SiteGateway.configure(child);child.setWebViewClient(new WebViewClient(){public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){if(loginWeb!=null&&SiteGateway.allowed(r.getUrl().toString()))loginWeb.loadUrl(r.getUrl().toString());else loginConnectionFailure("网页跳转",r.getUrl().toString(),"目标地址尚未适配",0);v.post(v::destroy);return true;}});((WebView.WebViewTransport)message.obj).setWebView(child);message.sendToTarget();handler.postDelayed(()->{try{child.destroy();}catch(Exception ignored){}},10000);return true;}});
        loginWeb.setWebViewClient(new WebViewClient(){
            public void onPageStarted(WebView v,String url,android.graphics.Bitmap icon){if(v!=loginWeb)return;readingLoginPage=false;loginPageFailed=false;AcademicReader.capture(v);progress.setVisibility(View.VISIBLE);progress.setProgress(0);domain.setText(Uri.parse(url).getHost());if(loginConnectionStatus!=null){loginConnectionStatus.setVisibility(View.GONE);loginConnectionStatus.setText("正在连接官网… 点此查看连接详情");}}
            public void onPageCommitVisible(WebView v,String url){if(v==loginWeb)AcademicReader.capture(v);}
            public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest request){if(v!=loginWeb)return true;if(SiteGateway.allowed(request.getUrl().toString()))return false;
                loginConnectionFailure("认证跳转",request.getUrl().toString(),"跳转地址未适配；点此查看目标主机",0);return true;}
            public void onReceivedError(WebView v,WebResourceRequest request,WebResourceError error){if(v==loginWeb&&request.isForMainFrame()){progress.setVisibility(View.GONE);loginConnectionFailure("页面连接",request.getUrl().toString(),WebDiagnostics.networkReason(error.getErrorCode()),error.getErrorCode());}}
            public void onReceivedHttpError(WebView v,WebResourceRequest request,WebResourceResponse response){if(v==loginWeb&&request.isForMainFrame()){progress.setVisibility(View.GONE);int code=response.getStatusCode();loginConnectionFailure("网页响应",request.getUrl().toString(),code>=500?"官网暂时故障（HTTP "+code+"）":"官网拒绝访问（HTTP "+code+"）",code);}}
            public void onPageFinished(WebView v,String url){if(v!=loginWeb)return;progress.setVisibility(View.GONE);AcademicReader.capture(v);if(!loginPageFailed&&SiteGateway.allowed(url)){domain.setText(Uri.parse(url).getHost());if(loginConnectionStatus!=null)loginConnectionStatus.setText("网页已载入，请完成登录；点此查看连接详情");WebDiagnostics.record(MainActivity.this,"页面载入",url,"已收到页面，尚不代表课表同步成功",0);
                for(int delay:new int[]{300,1000,2500,5000,9000})handler.postDelayed(()->{if(alive&&loginWeb==v&&url.equals(v.getUrl())){readAcademicFromLogin();if(officialService.equals("官网登录")&&url.contains("/sys/wdkb/"))readFromLogin(false);}},delay);
            }}
            public void onReceivedSslError(WebView v,SslErrorHandler h,android.net.http.SslError error){h.cancel();if(v!=loginWeb)return;progress.setVisibility(View.GONE);loginConnectionFailure("安全连接",error.getUrl(),"官网证书验证失败，已停止加载",error.getPrimaryError());}
            public boolean onRenderProcessGone(WebView v,android.webkit.RenderProcessGoneDetail error){
                if(v!=loginWeb)return true;WebDiagnostics.record(MainActivity.this,"网页组件",v.getUrl(),"WebView 渲染进程停止",0);handler.post(()->{if(v==loginWeb&&loginDialog!=null)loginDialog.dismiss();toast("登录网页已停止运行，课表仍可查看");});return true;}
        });
        loginDialog.setContentView(body);loginDialog.setOnDismissListener(d->{if(loginWeb!=null){loginWeb.stopLoading();loginWeb.destroy();loginWeb=null;}loginDialog=null;loginConnectionStatus=null;readingLoginPage=false;if(AccessMode.official(this))handler.postDelayed(this::refresh,300);});
        Fullscreen.prepare(loginDialog,body,dark);loginDialog.show();Fullscreen.expand(loginDialog);loginWeb.loadUrl(SiteGateway.allowed(initialUrl)?initialUrl:SiteGateway.HOME);
    }
    private void officialTools(){new AppDialog.Builder(this).setTitle("官网页面").setItems(new String[]{"重新加载","官网登录首页","自动读取个人课表","连接详情"},(d,w)->{if(w==3)showWebConnection();else if(loginWeb!=null){if(w==0)loginWeb.reload();else if(w==1)loginWeb.loadUrl(SiteGateway.HOME);else{officialService="官网登录";loginWeb.loadUrl(SiteGateway.TIMETABLE);}}}).setNegativeButton("关闭",null).show();}
    private void loginConnectionFailure(String stage,String url,String reason,int code){loginPageFailed=true;readingLoginPage=false;WebDiagnostics.record(this,stage,url,reason,code);if(loginConnectionStatus!=null){loginConnectionStatus.setVisibility(View.VISIBLE);loginConnectionStatus.setText(reason+"；点此查看详情或通过“更多”重试");}}
    private void showWebConnection(){String report=Diagnostics.report(this);final String copy=report;
        new AppDialog.Builder(this).setTitle("官网连接详情").setMessage(report).setPositiveButton("关闭",null).setNeutralButton("复制摘要",(d,w)->{android.content.ClipboardManager clipboard=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);if(clipboard!=null){clipboard.setPrimaryClip(android.content.ClipData.newPlainText("官网连接摘要",copy));toast("已复制连接摘要");}}).setNegativeButton("浏览器对比",(d,w)->{try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(SiteGateway.HOME)));toast("浏览器与 App 的登录会话各自独立");}catch(RuntimeException error){toast("没有可用的浏览器");}}).show();
    }
    private void rememberCourseUrl(String url){String saved=SiteGateway.savedPageUrl(url);if(!saved.isEmpty())prefs.edit().putString("course_url",saved).apply();}
    private String configuredMonday(JSONObject parsed){String configured=prefs.getString("first_monday",""),term=prefs.getString("calendar_semester",""),actual=parsed.optString("semester");
        return !term.isEmpty()&&!actual.isEmpty()&&!term.equals(actual)?"":configured;}
    private void readFromLogin(boolean explicit){
        if(loginPageFailed){if(explicit)showWebConnection();return;}
        if(loginWeb==null || readingLoginPage || !SiteGateway.allowed(loginWeb.getUrl()))return;
        readingLoginPage=true;WebView view=loginWeb;String url=view.getUrl();
        if(explicit)toast("正在读取官网当前学期课表…");
        CourseReader.read(view,(parsed,error)->{if(!alive || view!=loginWeb || !url.equals(view.getUrl()))return;readingLoginPage=false;
            if(error!=null || parsed==null || !parsed.optBoolean("complete")){
                if(explicit){gateway.close();int failed=sync.begin();sync.finish(failed,SiteGateway.readFailure(parsed),null,0);detail="当前页面未读取到完整课表";updateStatus();String reason=parsed==null?"页面无法识别":parsed.optBoolean("login")?"请先完成官网登录":parsed.optJSONArray("errors")==null?"未识别到学期课表":parsed.optJSONArray("errors").toString();
                    new AppDialog.Builder(this).setTitle("尚未读到完整课表").setMessage(reason+"\n\n请在官网打开包含课程名称、星期、节次和周次的个人学期课表后重试。已有缓存不会被清除。").setPositiveButton("知道了",null).setNeutralButton("复制识别摘要",(d,w)->{
                        android.content.ClipboardManager clipboard=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
                        if(clipboard!=null){String summary="LUT "+BuildInfo.VERSION+" 课表识别摘要\n主机："+Uri.parse(url).getHost()+"\n原因："+reason+"\n结构："+(parsed==null?"未知":parsed.optString("structure","未知"));clipboard.setPrimaryClip(android.content.ClipData.newPlainText("课表识别摘要",summary));toast("已复制摘要，不含账号、密码或课程内容");}
                    }).show();}return;
            }
            try{if(!parsed.has("readAt"))parsed.put("readAt",System.currentTimeMillis());if(parsed.optString("pageUrl").isEmpty())parsed.put("pageUrl",url);}catch(Exception ignored){}
            if(parsed.optString("firstMonday").isEmpty() && configuredMonday(parsed).isEmpty()){
                if(explicit)chooseFirstMonday(()->{prefs.edit().putString("calendar_semester",parsed.optString("semester")).apply();saveReadPage(parsed);});else{int failed=sync.begin();sync.finish(failed,ScheduleCore.Status.PARSE_ERROR,null,0);detail="官网未返回有效学期日期，已保留原课表";updateStatus();}return;
            }
            saveReadPage(parsed);
        });
    }
    private void saveReadPage(JSONObject parsed){
        coordinator.close();int ticket=sync.begin();updateStatus();
        try{
            ScheduleCore.Snapshot official=CourseReader.decode(parsed,configuredMonday(parsed),1,parsed.optLong("readAt",System.currentTimeMillis()));
            acceptOfficialSnapshot(ticket,official);
            if(snapshot==official){if(!prefs.contains("handedness"))handler.postDelayed(this::firstHandedness,200);rememberCourseUrl(parsed.optString("pageUrl"));CookieManager.getInstance().flush();if(loginDialog!=null)loginDialog.dismiss();toast("已读取 "+official.courses.size()+" 条课程并保存");handler.postDelayed(this::refresh,350);}
        }catch(Exception error){Diagnostics.record(this,"课表校验",error,false);sync.finish(ticket,ScheduleCore.Status.PARSE_ERROR,null,0);detail="课表校验失败："+error.getMessage();updateStatus();toast(detail+"；已保留缓存");}
    }
    private void chooseFirstMonday(Runnable after){
        EditText input=new EditText(this);input.setSingleLine();input.setText(prefs.getString("first_monday",LocalDate.now(SCHOOL_TIME).with(java.time.DayOfWeek.MONDAY).toString()));input.setHint("YYYY-MM-DD");input.setTextColor(ink);
        LinearLayout field=vertical();field.addView(input);TextView validation=text("",12,0xFFE04444);validation.setVisibility(View.GONE);field.addView(validation);AppDialog dialog=new AppDialog.Builder(this).setTitle("第一教学周的周一").setMessage("优先使用官网校历。手动校正时请填写本学期第一教学周的周一，例如 2026-08-24。").setView(field).setPositiveButton("保存",null).setNegativeButton("取消",null).show();
        dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener(v->{try{LocalDate monday=LocalDate.parse(input.getText().toString().trim());if(monday.getDayOfWeek()!=java.time.DayOfWeek.MONDAY)throw new IllegalArgumentException();prefs.edit().putString("first_monday",monday.toString()).putString("calendar_semester",snapshot==null?"":snapshot.semester).apply();dialog.dismiss();if(after!=null)after.run();}catch(Exception e){validation.setText("请输入有效的周一日期，格式 YYYY-MM-DD");validation.setVisibility(View.VISIBLE);}});
    }
    private View academic(){
        LinearLayout view=vertical();view.addView(pageHeader("教务","查询与办理"));
        boolean usable=AccessMode.official(this);TextView note=text(usable?"官网服务目录 · 点击在官网打开":"这些选项仅为 LUT 学生提供。登录 LUT 教务系统后可用。",12,muted);note.setPadding(dp(22),0,dp(22),dp(16));view.addView(note);
        LinearLayout columns=row();columns.setGravity(Gravity.TOP);view.addView(columns,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout left=vertical();left.setPadding(dp(12),0,dp(10),0);LinearLayout right=vertical();right.setPadding(dp(12),0,dp(20),0);
        ScrollView leftScroll=new ScrollView(this);leftScroll.addView(left);ScrollView rightScroll=new ScrollView(this);rightScroll.addView(right);
        columns.addView(leftScroll,new LinearLayout.LayoutParams(dp(116),-1));columns.addView(rightScroll,new LinearLayout.LayoutParams(0,-1,1));
        String[] categories=AcademicCatalog.CATEGORIES;
        final TextView[] selected=new TextView[categories.length];final int[] active={Math.max(0,Math.min(categories.length-1,prefs.getInt("academic_category",0)))};
        Runnable showItems=()->{boolean previousBuild=buildingPage;buildingPage=true;adaptiveViews.removeIf(v->{android.view.ViewParent parent=v.getParent();while(parent instanceof View){if(parent==right)return true;parent=parent.getParent();}return false;});try{right.removeAllViews();right.addView(title(categories[active[0]],19));space(right,18);
            String[] items=AcademicCatalog.services(active[0]);
            if(items.length==0){right.addView(text("暂未取得此分类的服务目录",15,ink));space(right,10);TextView explanation=text("当前已确认师生服务的菜单。点击下面的入口，查看官网这一分类的内容。",13,muted);explanation.setLineSpacing(dp(4),1);right.addView(explanation);space(right,14);right.addView(action("打开官网分类",()->showOfficial(categories[active[0]],SiteGateway.HOME)));}
            else {TextView count=text(items.length+" 项服务",12,muted);right.addView(count);space(right,14);
                for(String name:items){LinearLayout item=vertical();item.setPadding(dp(2),dp(14),dp(2),dp(14));clickable(item,Color.TRANSPARENT);item.addView(title(name,14));space(item,7);TextView description=text(AcademicCatalog.description(name),12,muted);description.setLineSpacing(dp(3),1);item.addView(description);item.setContentDescription(name);item.setEnabled(usable);item.setAlpha(usable?1f:.55f);item.setOnClickListener(v->{if(usable)openAcademicService(name);});right.addView(item,new LinearLayout.LayoutParams(-1,-2));divider(right);}
            }
            rightScroll.scrollTo(0,0);
            for(int i=0;i<categories.length;i++){boolean chosen=i==active[0];clickable(selected[i],Color.TRANSPARENT);selected[i].setText(chosen?"│ "+categories[i]:categories[i]);selected[i].setTextColor(chosen?accent:muted);selected[i].setTypeface(Typeface.DEFAULT,chosen?Typeface.BOLD:Typeface.NORMAL);selected[i].setSelected(chosen);}
            }finally{buildingPage=previousBuild;root.post(this::adaptForeground);}
        };
        for(int i=0;i<categories.length;i++){final int index=i;TextView item=text(categories[i],14,accent);item.setGravity(Gravity.CENTER);item.setContentDescription("教务分类："+categories[i]);selected[i]=item;left.addView(item,new LinearLayout.LayoutParams(-1,dp(54)));space(left,10);item.setOnClickListener(v->{active[0]=index;prefs.edit().putInt("academic_category",index).apply();showItems.run();if(animationsEnabled()){right.setAlpha(0);right.animate().alpha(1).setDuration(140).start();}});}showItems.run();root.post(this::adaptForeground);return view;
    }
    private void openAcademicService(int index){openAcademicService(AcademicCatalog.SERVICE_NAMES[index]);}
    private void openAcademicService(String name){if(!AccessMode.official(this))return;if(name.equals("我的课表")){navigate(0);return;}String url=AcademicCatalog.route(prefs,name);showOfficial(name,SiteGateway.allowed(url)?url:SiteGateway.HOME);}
    private void readAcademicFromLogin(){if(loginWeb==null||loginPageFailed)return;WebView current=loginWeb;String url=current.getUrl(),kind=officialService.equals("成绩查询")?"grades":officialService.equals("我的考试安排")?"exams":"catalog";
        AcademicReader.read(current,kind,(page,error)->{if(!alive||current!=loginWeb||!url.equals(current.getUrl())||page==null||page.optBoolean("login"))return;SyncCoordinator.rememberLinks(this,page);if(page.optBoolean("authenticated")&&!loginSyncQueued){loginSyncQueued=true;verifiedOfficialSession();CookieManager.getInstance().flush();if(officialService.equals("官网登录")){coordinator.close();if(!url.contains("/sys/wdkb/")){current.loadUrl(SiteGateway.TIMETABLE);return;}}else handler.postDelayed(this::refresh,300);}
            if(page.optBoolean("complete")&&!kind.equals("catalog")){try{academicStore.save(kind,page,SyncCoordinator.scope(page),System.currentTimeMillis());prefs.edit().putLong(kind+"_updated",System.currentTimeMillis()).apply();loadAcademic();refreshBanner();if(timetable!=null)timetable.invalidate();if(foreground)GradeNotifications.deliver(this,pendingChanges);}catch(Exception e){Diagnostics.record(this,"教务页面读取",e,false);}}
            if(!officialService.equals("官网登录")&&officialNavigationAttempts<1&&page.optBoolean("portal")){officialNavigationAttempts++;String route=AcademicCatalog.route(prefs,officialService);if(SiteGateway.allowed(route))current.loadUrl(route);}
        });}

    private void verifiedOfficialSession(){prefs.edit().putString("entry_mode",AccessMode.OFFICIAL).apply();loadAcademic();IdleRefresh.schedule(this);}

    private View settings(){
        ScrollView scroll=new ScrollView(this);settingsScroll=scroll;LinearLayout view=vertical();scroll.addView(view);view.addView(pageHeader("设置","让它像你喜欢的样子"));LinearLayout content=vertical();content.setPadding(dp(20),0,dp(20),dp(24));view.addView(content);
        group(content,"外观");
        setting(content,"屏幕风格",prefs.getString("theme","light").equals("dark")?"暗色":prefs.getString("theme","light").equals("system")?"跟随系统":"亮色",()->new AppDialog.Builder(this).setTitle("屏幕风格").setItems(new String[]{"亮色","暗色","跟随系统"},(d,w)->{prefs.edit().putString("theme",new String[]{"light","dark","system"}[w]).apply();recreate();}).show());
        setting(content,"自定义背景","三页共用一张图，或同一张图裁为连续三份",()->pick("image/*",IMAGE));
        setting(content,"背景透明度",(100-prefs.getInt("veil",25))+"% · 数值越高，图片越清晰",this::chooseBackgroundOpacity);
        setting(content,"课程颜色",prefs.getString("course_colors","varied").equals("solid")?"纯色 · 自选色盘":"随机多色 · 同一课程保持同色",this::chooseCourseColors);
        setting(content,"恢复默认背景","使用纯色背景",()->{File file=new File(getFilesDir(),"background.jpg");if(file.exists()&&!file.delete()){toast("背景删除失败");return;}prefs.edit().putString("background_layout","shared").apply();loadBackground();renderPage(false);toast("已恢复默认背景");});
        group(content,"课表与操作");
        setting(content,"底栏顺序",AppRules.tabLabel(prefs.getString("tab_order",null)),this::chooseTabOrder);
        setting(content,"周数按钮位置",prefs.getString("week_side","right").equals("left")?"左下角":"右下角",()->new AppDialog.Builder(this).setTitle("周数按钮位置").setItems(new String[]{"左下角","右下角"},(d,w)->{prefs.edit().putString("week_side",w==0?"left":"right").apply();renderPage(false);}).show());
        setting(content,"考试日程","在课表对应日期显示考试时间和地点",()->explain("考试日程","同步成功后，考试会显示在课表对应日期下方，标出精确起止时间。没有学校节次时间表时，不猜测考试对应第几节。点击考试可以查看完整信息。"));
        setting(content,"学期起始日期",prefs.getString("first_monday","").isEmpty()?"优先从官网校历自动获取；可手动校正":prefs.getString("first_monday",""),()->chooseFirstMonday(()->renderPage(false)));
        group(content,"同步与提醒");
        setting(content,"官网登录","所有教务服务均在官网 WebView 中打开",this::showLogin);
        setting(content,"最近更新日期","查看各模块时间和连接状态",this::showStatus);
        setting(content,"立即刷新","打开或返回 App 时也会更新",this::refresh);
        setting(content,"新成绩通知",GradeNotifications.allowed(this)?"通知已允许 · 同时显示顶部提示":"系统通知未允许 · 顶部提示仍可查看",this::notificationPermission);
        setting(content,"静默缓存更新",prefs.getBoolean("idle_refresh",true)?"闲置 24h 后的第一个北京时间 08:00，仅一次":"已关闭",()->new AppDialog.Builder(this).setTitle("静默缓存更新").setMessage("只更新缓存，下次打开 App 才提示。连续多天未操作也只检查一次；新的操作重新计时。Android 节电或小米后台限制可能让实际执行晚于 08:00。\n\n计划时间："+formatTime(prefs.getLong("idle_target",0))+"\n上次后台检查："+formatTime(prefs.getLong("idle_checked",0))).setPositiveButton(prefs.getBoolean("idle_refresh",true)?"关闭更新":"启用更新",(d,w)->{prefs.edit().putBoolean("idle_refresh",!prefs.getBoolean("idle_refresh",true)).apply();IdleRefresh.schedule(this);renderPage(false);}).setNegativeButton("保留当前",null).show());
        group(content,"数据与隐私");
        setting(content,"导入本地课表","选择标准 JSON 文件，不计为官网同步",()->pick("*/*",IMPORT));
        setting(content,"清除数据并退出账号","清除课程、成绩、考试、未读消息和官网会话",()->new AppDialog.Builder(this).setTitle("清除数据并退出？").setMessage("保留风格与背景设置。再次登录后会建立新的成绩基线。").setPositiveButton("清除并退出",(d,w)->logout()).setNegativeButton("取消",null).show());
        group(content,"关于与排查");
        developerSetting(content);
        setting(content,"使用声明","非官方应用 · 不可商用 · 1.0.1",()->explain("使用声明","用于个人非商业学习与课表查看。应用名称：牛逼课表。开发者：Enota13。官网网页登录会话仅保存在本机，项目不自动上传教务数据或错误报告。"));
        setting(content,"项目仓库","github.com/Enota13-name/LUTschedule",()->{try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(BuildInfo.REPOSITORY)));}catch(Exception e){Diagnostics.record(this,"打开项目仓库",e,false);}});
        group(content,"错误报告");
        setting(content,"复制错误报告",Diagnostics.last(this).isEmpty()?"设备、版本、源码链接与排查指引":"已记录错误 · 复制后交给 AI 排查",this::showErrorReport);
return scroll;
    }
    private void group(LinearLayout host,String name){space(host,18);TextView label=text(name,12,muted);label.setPadding(dp(4),0,0,dp(10));host.addView(label);}
    private void explain(String name,String message){new AppDialog.Builder(this).setTitle(name).setMessage(message).setPositiveButton("知道了",null).show();}
    private void notificationPermission(){new AppDialog.Builder(this).setTitle("新成绩通知权限").setMessage("POST_NOTIFICATIONS 用于发现新成绩后发送 Android 消息。顶部提示不依赖这项权限。\n\n后台 08:00 更新只保存缓存，下次打开 App 才提醒。通知不在锁屏直接展示课程成绩。"+(GradeNotifications.allowed(this)?"\n\n当前已允许通知。":"\n\n当前系统通知未允许。" )).setPositiveButton(GradeNotifications.allowed(this)?"通知系统设置":"允许通知",(d,w)->{IdleRefresh.operated(this);if(android.os.Build.VERSION.SDK_INT>=33&&checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED&&!prefs.getBoolean("notification_requested",false))requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS},71);else{Intent i=new Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS);i.putExtra(android.provider.Settings.EXTRA_APP_PACKAGE,getPackageName());try{startActivity(i);}catch(Exception e){openSystemSettings();}}}).setNegativeButton("暂不开启",null).show();}
    public void onRequestPermissionsResult(int code,String[] permissions,int[] results){super.onRequestPermissionsResult(code,permissions,results);if(code==71){prefs.edit().putBoolean("notification_requested",true).apply();if(foreground)GradeNotifications.deliver(this,pendingChanges);renderPage(false);}}
    private void openSystemSettings(){try{startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName())));}catch(Exception e){toast("无法打开系统设置");}}
    private int tabPosition(int id){int[] order=AppRules.tabOrder(prefs.getString("tab_order",null));for(int slot=0;slot<3;slot++)if(order[slot]==id)return slot;return 1;}
    private void applyTabOrder(String order){if(backgroundAnimator!=null)backgroundAnimator.cancel();prefs.edit().putString("tab_order",order).apply();backgroundPage=tabPosition(page);transitionAlpha=0;renderPage(false);root.invalidate();}
    private AppDialog chooseTabOrder(){String[] labels=new String[AppRules.TAB_ORDERS.length];int selected=0;String stored=prefs.getString("tab_order",AppRules.TAB_ORDERS[0]);for(int i=0;i<labels.length;i++){labels[i]=AppRules.tabLabel(AppRules.TAB_ORDERS[i]);if(AppRules.TAB_ORDERS[i].equals(stored))selected=i;}return new AppDialog.Builder(this).setTitle("底栏顺序").setSingleChoiceItems(labels,selected,(d,w)->{d.dismiss();applyTabOrder(AppRules.TAB_ORDERS[w]);}).setNegativeButton("关闭",null).show();}
    private void chooseCourseColors(){new AppDialog.Builder(this).setTitle("课程配色").setItems(new String[]{"随机多色 · 课程颜色保持稳定","纯色 · 从色盘选择"},(d,w)->{if(w==0){prefs.edit().putString("course_colors","varied").apply();renderPage(false);}else chooseSolidColor();}).show();}
    private AppDialog chooseSolidColor(){
        LinearLayout body=vertical(),top=row();body.setPadding(0,dp(8),0,dp(8));ColorWheel wheel=new ColorWheel(prefs.getInt("solid_color",0xFFC9DDD1));top.addView(wheel,new LinearLayout.LayoutParams(0,dp(190),1));TextView example=text("高等数学\n1–2节\nA201",13,ink);example.setTypeface(Typeface.DEFAULT_BOLD);example.setGravity(Gravity.TOP);example.setPadding(dp(10),dp(14),dp(8),dp(8));LinearLayout.LayoutParams sample=new LinearLayout.LayoutParams(dp(86),dp(122));sample.leftMargin=dp(12);top.addView(example,sample);body.addView(top);
        SeekBar brightness=new SeekBar(this);brightness.setMax(100);brightness.setProgress(Math.round(wheel.value*100));body.addView(text("明度",12,muted));body.addView(brightness);EditText hex=new EditText(this);hex.setSingleLine();hex.setTextColor(ink);hex.setHint("例如 #C9DDD1");hex.setText(String.format(java.util.Locale.ROOT,"#%06X",wheel.color&0xFFFFFF));body.addView(hex);TextView validation=text("",12,0xFFE04444);validation.setVisibility(View.GONE);body.addView(validation);
        Runnable preview=()->{example.setBackground(shape(AppRules.readableSurface(wheel.color,screenForeground),3));example.setTextColor(screenForeground);};preview.run();wheel.changed=()->{hex.setText(String.format(java.util.Locale.ROOT,"#%06X",wheel.color&0xFFFFFF));preview.run();};brightness.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}public void onProgressChanged(SeekBar s,int value,boolean user){if(user){wheel.value=Math.max(.1f,value/100f);wheel.recolor();}}});
        hex.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int n,int a){}public void onTextChanged(CharSequence s,int start,int before,int count){try{int color=Color.parseColor(s.toString().trim())|0xFF000000;example.setBackground(shape(AppRules.readableSurface(color,screenForeground),3));example.setTextColor(screenForeground);}catch(Exception ignored){}}public void afterTextChanged(android.text.Editable e){}});
        AppDialog dialog=new AppDialog.Builder(this).setTitle("课程纯色与预览").setView(body).setPositiveButton("保存",null).setNegativeButton("取消",null).show();dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener(v->{try{int color=Color.parseColor(hex.getText().toString().trim());prefs.edit().putString("course_colors","solid").putInt("solid_color",color|0xFF000000).apply();dialog.dismiss();renderPage(false);}catch(Exception e){validation.setText("请输入有效颜色，例如 #C9DDD1");validation.setVisibility(View.VISIBLE);}});return dialog;
    }
    private void chooseBackgroundOpacity(){LinearLayout body=vertical();TextView value=text("",14,ink);SeekBar seek=new SeekBar(this);seek.setMax(100);seek.setProgress(100-prefs.getInt("veil",25));Runnable label=()->value.setText(seek.getProgress()+"% · 数值越高，图片越清晰");label.run();body.addView(value);body.addView(seek);seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}public void onProgressChanged(SeekBar s,int n,boolean user){label.run();}});new AppDialog.Builder(this).setTitle("背景透明度").setView(body).setPositiveButton("保存",(d,w)->{prefs.edit().putInt("veil",100-seek.getProgress()).apply();renderPage(false);}).setNegativeButton("取消",null).show();}
    private void showErrorReport(){String report=Diagnostics.report(this);new AppDialog.Builder(this).setTitle("本地错误报告").setMessage(report).setPositiveButton("复制错误报告",(d,w)->Diagnostics.copy(this)).setNegativeButton("关闭",null).show();}
    boolean diagnosticsCanShow(){return alive&&initialized&&foreground&&!isFinishing()&&!isDestroyed()&&(handDialog==null||!handDialog.isShowing())&&(errorDialog==null||!errorDialog.isShowing())&&(backgroundCrop==null||!backgroundCrop.isShowing());}
    void showDiagnosticError(String id){errorDialog=new AppDialog.Builder(this).setTitle("发生了错误").setMessage("错误编号："+id+"\n错误报告已保存在本机，有效课表缓存会继续显示。\n\n请到设置页面最底部复制错误报告，并交给 AI 排查。报告包含项目仓库与对应源码分支。").setPositiveButton("去设置",(d,w)->{if(loginDialog!=null)loginDialog.dismiss();navigate(2);if(settingsScroll!=null)settingsScroll.post(()->settingsScroll.fullScroll(View.FOCUS_DOWN));}).setNegativeButton("稍后处理",null).show();errorDialog.setOnDismissListener(d->errorDialog=null);}
    private void divider(LinearLayout host){View line=new View(this);line.setBackgroundColor(dark?0x55444444:0x55888888);host.addView(line,new LinearLayout.LayoutParams(-1,dp(1)));}
    private void setting(LinearLayout host,String name,String summary,Runnable callback){LinearLayout item=vertical();item.setPadding(dp(2),dp(14),dp(2),dp(14));clickable(item,Color.TRANSPARENT);item.addView(title(name,15));space(item,6);item.addView(text(summary,12,muted));item.setContentDescription(name);item.setOnClickListener(v->{developerTaps=0;IdleRefresh.operated(this);ErrorReports.breadcrumb(this,"设置："+name);callback.run();});host.addView(item,new LinearLayout.LayoutParams(-1,-2));divider(host);}
    private void pick(String mime,int request){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType(mime);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);try{startActivityForResult(i,request);}catch(Exception e){Diagnostics.record(this,"系统文件选择器",e,false);toast("没有可用的文件选择器");}}
    private void developerSetting(LinearLayout host){
        LinearLayout item=vertical();developerRow=item;item.setPadding(dp(2),dp(14),dp(2),dp(14));item.addView(title("开发者",15));space(item,6);item.addView(text(BuildInfo.DEVELOPER,12,ink));space(item,4);item.addView(text("app图标作者：他不想署名",12,muted));
        item.setContentDescription("开发者 Enota13");item.setSoundEffectsEnabled(false);item.setHapticFeedbackEnabled(false);item.setOnClickListener(v->developerTap());host.addView(item,new LinearLayout.LayoutParams(-1,-2));divider(host);
    }
    private void developerTap(){
        long now=android.os.SystemClock.uptimeMillis();if(now-developerTapTime>3000)developerTaps=0;developerTapTime=now;
        if(++developerTaps<5)return;developerTaps=0;android.content.ClipboardManager clipboard=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
        if(clipboard==null){Diagnostics.event(this,"开发者邮箱复制","系统剪贴板暂不可用");return;}
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("开发者邮箱",BuildInfo.CONTACT_EMAIL));
        contactDialog=new AppDialog.Builder(this).setTitle("开发者联系").setMessage("你已经复制了开发者的邮箱，畅所欲言").setPositiveButton("知道了",null).show();contactDialog.setOnDismissListener(d->contactDialog=null);
    }
    void importSchedule(byte[] bytes)throws Exception{
        ScheduleCore.Snapshot imported=SnapshotStore.decode(new JSONObject(new String(bytes,java.nio.charset.StandardCharsets.UTF_8)),ScheduleCore.Source.IMPORT);
        ScheduleCore.Snapshot stamped=new ScheduleCore.Snapshot(imported.semester,imported.firstMonday,imported.totalWeeks,ScheduleCore.Source.IMPORT,System.currentTimeMillis(),imported.courses,imported.periodCount);
        store.save(stamped);coordinator.close();int ticket=sync.begin();sync.finish(ticket,ScheduleCore.Status.IDLE,null,0);
        prefs.edit().putString("entry_mode",AccessMode.IMPORT).apply();IdleRefresh.schedule(this);loadAcademic();loadSnapshot();renderPage(false);
        if(snapshot!=null&&!prefs.contains("handedness"))handler.postDelayed(this::firstHandedness,150);
    }
    private void logout(){coordinator.close();int ticket=sync.begin();sync.finish(ticket,ScheduleCore.Status.LOGIN_REQUIRED,null,0);
        store.clear();academicStore.clear();getSharedPreferences("notification_seen",0).edit().clear().apply();SharedPreferences.Editor logoutEdit=prefs.edit().putString("entry_mode",AccessMode.NONE).remove("course_url").remove("first_monday").remove("calendar_semester").remove("last_full_success").remove("grades_updated").remove("exams_updated").remove("timetable_updated");for(String[] group:AcademicCatalog.GROUPS)for(String label:group)logoutEdit.remove("service_"+label);logoutEdit.apply();IdleRefresh.schedule(this);loadAcademic();snapshot=null;CookieManager.getInstance().removeAllCookies(value->CookieManager.getInstance().flush());
        android.webkit.WebStorage.getInstance().deleteAllData();detail="已清除会话与本地课表";renderPage(false);toast("已退出账号");}
    protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(result!=RESULT_OK || data==null || data.getData()==null)return;
        Uri uri=data.getData();try{
            byte[] bytes;try(InputStream input=getContentResolver().openInputStream(uri)){bytes=readBounded(input,request==IMAGE?20*1024*1024:1024*1024);}
            if(request==IMPORT){importSchedule(bytes);toast("已导入本地课表；教务选项仅为 LUT 学生提供");
            }else if(request==IMAGE){showBackgroundCrop(bytes);}
        }catch(Exception|OutOfMemoryError e){Diagnostics.record(this,request==IMAGE?"背景图片读取":"课表文件导入",e,false);toast("操作失败，错误报告已保存；原数据未改变");}
    }
    private void showBackgroundCrop(byte[] bytes)throws Exception{ErrorReports.breadcrumb(this,"选择背景图片，进入裁剪预览");if(backgroundCrop!=null)backgroundCrop.dismiss();float aspect=root.getHeight()>0?root.getWidth()/(float)root.getHeight():getResources().getDisplayMetrics().widthPixels/(float)getResources().getDisplayMetrics().heightPixels;backgroundCrop=new BackgroundCrop(this,bytes,aspect,dark,prefs.getInt("veil",25),()->{loadBackground();renderPage(false);toast("背景已按预览保存到本机");});backgroundCrop.show();}
    private byte[] readBounded(InputStream input,int limit)throws Exception{if(input==null)throw new IllegalArgumentException("文件无法打开");ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int n;while((n=input.read(buffer))!=-1){if(out.size()+n>limit)throw new IllegalArgumentException("文件过大");out.write(buffer,0,n);}return out.toByteArray();}
    private void loadBackground(){foregroundKey="";if(background!=null){background.recycle();background=null;}File file=new File(getFilesDir(),"background.jpg");if(file.exists()){background=BitmapFactory.decodeFile(file.getAbsolutePath());try{String mode=new android.media.ExifInterface(file.getAbsolutePath()).getAttribute(android.media.ExifInterface.TAG_USER_COMMENT);if("LUTBACKGROUND:split".equals(mode)||"LUTBACKGROUND:shared".equals(mode))prefs.edit().putString("background_layout",mode.endsWith("split")?"split":"shared").apply();}catch(Exception ignored){}}}
    private void toast(String message){UiNotice.show(this,message);}
    public void onConfigurationChanged(Configuration c){super.onConfigurationChanged(c);if(isDark()!=dark)recreate();else renderPage(false);}
    public boolean dispatchTouchEvent(MotionEvent event){if(initialized&&event.getAction()==MotionEvent.ACTION_DOWN){IdleRefresh.operated(this);android.graphics.Rect area=new android.graphics.Rect();if(developerRow==null||!developerRow.getGlobalVisibleRect(area)||!area.contains((int)event.getRawX(),(int)event.getRawY()))developerTaps=0;}return super.dispatchTouchEvent(event);}
    protected void onUserLeaveHint(){userLeftApp=true;super.onUserLeaveHint();}
    protected void onStop(){foreground=false;Diagnostics.detach(this);super.onStop();returnFromBackground=true;}
    protected void onResume(){super.onResume();foreground=true;if(initialized){Diagnostics.attach(this);if(snapshot!=null&&!prefs.contains("handedness"))handler.postDelayed(this::firstHandedness,150);if(userLeftApp){userLeftApp=false;IdleRefresh.operated(this);}loadAcademic();refreshBanner();GradeNotifications.deliver(this,pendingChanges);if(returnFromBackground){returnFromBackground=false;handler.post(this::refresh);}}}
    protected void onDestroy(){alive=false;Diagnostics.detach(this);if(backgroundAnimator!=null)backgroundAnimator.cancel();if(errorDialog!=null)errorDialog.dismiss();if(contactDialog!=null)contactDialog.dismiss();handler.removeCallbacksAndMessages(null);if(coordinator!=null)coordinator.close();if(gateway!=null)gateway.close();if(loginDialog!=null)loginDialog.dismiss();if(backgroundCrop!=null)backgroundCrop.dismiss();if(handDialog!=null)handDialog.dismiss();if(store!=null)store.close();if(academicStore!=null)academicStore.close();if(background!=null){background.recycle();background=null;}super.onDestroy();}

    private RectF wallpaperRect(){int w=root.getWidth(),h=root.getHeight();if(background==null||background.isRecycled())return new RectF(0,0,w,h);boolean split=prefs.getString("background_layout","shared").equals("split");float target=split?w*3f:w,scale=Math.max(target/background.getWidth(),h/(float)background.getHeight());float bw=background.getWidth()*scale,bh=background.getHeight()*scale,left=(target-bw)/2-(split?backgroundPage*w:0);return new RectF(left,(h-bh)/2,left+bw,(h+bh)/2);}
    private int veilAlpha(){return Math.min(255,Math.max(0,prefs.getInt("veil",25)*255/100));}
    int screenForeground(){return screenForeground;}
    private int buttonColor(View view){return screenForeground;}
    private void updateScreenForeground(){
        if(root==null)return;String key=root.getWidth()+":"+root.getHeight()+":"+veilAlpha()+":"+dark+":"+prefs.getString("background_layout","shared");
        if(key.equals(foregroundKey))return;
        int dominant=base;
        if(background!=null&&!background.isRecycled()&&root.getWidth()>0&&root.getHeight()>0){
            // Sample each displayed panorama part equally; navigation never changes the chosen foreground.
            int parts=prefs.getString("background_layout","shared").equals("split")?3:1;
            int[] samples=new int[parts*32*64];float previous=backgroundPage;
            try{for(int part=0;part<parts;part++){backgroundPage=part;RectF r=wallpaperRect();float scale=r.width()/background.getWidth();
                for(int y=0;y<64;y++)for(int x=0;x<32;x++){int bx=Math.max(0,Math.min(background.getWidth()-1,(int)((root.getWidth()*(x+.5f)/32-r.left)/scale))),by=Math.max(0,Math.min(background.getHeight()-1,(int)((root.getHeight()*(y+.5f)/64-r.top)/scale)));samples[part*32*64+y*32+x]=AppRules.composite(base,background.getPixel(bx,by),veilAlpha());}
            }}finally{backgroundPage=previous;}
            dominant=AppRules.dominant(samples);
        }
        screenForeground=AppRules.foreground(dominant);ink=muted=accent=screenForeground;foregroundKey=key;
    }
    private void adaptText(View view){if(view instanceof TextView){TextView t=(TextView)view;t.setTextColor(screenForeground);t.setShadowLayer(dp(1.2f),0,0,screenForeground==Color.WHITE?0xAA000000:0xAAFFFFFF);}if(view instanceof ViewGroup){ViewGroup g=(ViewGroup)view;for(int i=0;i<g.getChildCount();i++)adaptText(g.getChildAt(i));}}
    private void adaptForeground(){if(!alive||root==null)return;updateScreenForeground();adaptText(root);for(View v:adaptiveViews)if(!(v instanceof TextView))v.invalidate();if(gradeBanner!=null)gradeBanner.setBackgroundColor(AppRules.readableSurface(dark?0xFF292929:0xFFF3F3F3,screenForeground));if(timetable!=null)timetable.invalidate();
        if(android.os.Build.VERSION.SDK_INT>=30){android.view.WindowInsetsController controller=root.getWindowInsetsController();if(controller!=null){int flags=android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;controller.setSystemBarsAppearance(screenForeground==Color.WHITE?0:flags,flags);}}
        getWindow().setStatusBarColor(Color.TRANSPARENT);getWindow().setNavigationBarColor(Color.TRANSPARENT);
    }
    private final class Wallpaper extends Drawable {private final Paint paint=new Paint(3);public void draw(Canvas canvas){canvas.drawColor(base);if(background!=null&&!background.isRecycled()){canvas.drawBitmap(background,null,wallpaperRect(),paint);canvas.drawColor(Color.argb(veilAlpha(),Color.red(base),Color.green(base),Color.blue(base)));if(transitionAlpha>0)canvas.drawColor(Color.argb(transitionAlpha,Color.red(base),Color.green(base),Color.blue(base)));}}public void setAlpha(int alpha){}public void setColorFilter(ColorFilter filter){}public int getOpacity(){return PixelFormat.OPAQUE;}}
    private final class ColorWheel extends View {final Paint paint=new Paint(3);float hue,saturation=.3f,value=.85f;int color;Runnable changed;ColorWheel(int initial){super(MainActivity.this);float[] hsv=new float[3];Color.colorToHSV(initial,hsv);hue=hsv[0];saturation=hsv[1];value=hsv[2];color=initial;setContentDescription("课程纯色色盘，可触摸选择颜色或输入十六进制颜色");}void recolor(){color=Color.HSVToColor(new float[]{hue,saturation,value});invalidate();if(changed!=null)changed.run();}protected void onDraw(Canvas c){float x=getWidth()/2f,y=getHeight()/2f,r=Math.min(x,y)-dp(10);int[] colors={Color.RED,Color.YELLOW,Color.GREEN,Color.CYAN,Color.BLUE,Color.MAGENTA,Color.RED};paint.setShader(new android.graphics.SweepGradient(x,y,colors,null));c.drawCircle(x,y,r,paint);paint.setShader(new android.graphics.RadialGradient(x,y,r,Color.WHITE,Color.TRANSPARENT,android.graphics.Shader.TileMode.CLAMP));c.drawCircle(x,y,r,paint);paint.setShader(null);paint.setColor(Color.WHITE);paint.setStrokeWidth(dp(2));paint.setStyle(Paint.Style.STROKE);double a=Math.toRadians(hue);c.drawCircle(x+(float)Math.cos(a)*r*saturation,y+(float)Math.sin(a)*r*saturation,dp(7),paint);paint.setStyle(Paint.Style.FILL);}public boolean onTouchEvent(MotionEvent e){if(e.getAction()==MotionEvent.ACTION_DOWN||e.getAction()==MotionEvent.ACTION_MOVE){float dx=e.getX()-getWidth()/2f,dy=e.getY()-getHeight()/2f,r=Math.min(getWidth(),getHeight())/2f-dp(10);hue=(float)((Math.toDegrees(Math.atan2(dy,dx))+360)%360);saturation=Math.min(1,(float)Math.hypot(dx,dy)/r);recolor();return true;}if(e.getAction()==MotionEvent.ACTION_UP){performClick();return true;}return true;}public boolean performClick(){super.performClick();return true;}}

    private final class StatusIcon extends View {
        private final Paint p=new Paint(3);StatusIcon(){super(MainActivity.this);setFocusable(true);clickable(this,Color.TRANSPARENT);}
        protected void onDraw(Canvas c){super.onDraw(c);float x=getWidth()/2f,y=getHeight()/2f;p.setStrokeWidth(dp(1.8f));p.setStyle(Paint.Style.STROKE);
            if(!AccessMode.official(MainActivity.this)){p.setColor(screenForeground);c.drawCircle(x,y,dp(10),p);c.drawLine(x,y-dp(2),x,y+dp(5),p);p.setStyle(Paint.Style.FILL);c.drawCircle(x,y-dp(5),dp(1),p);return;}boolean ok=sync.status()==ScheduleCore.Status.SUCCESS;p.setColor(ok?buttonColor(this):sync.status()==ScheduleCore.Status.UPDATING?buttonColor(this):0xFFE54444);if(sync.status()!=ScheduleCore.Status.UPDATING)c.drawCircle(x,y,dp(10),p);
            if(ok){c.drawLine(x-dp(5),y,x-dp(1),y+dp(4),p);c.drawLine(x-dp(1),y+dp(4),x+dp(6),y-dp(5),p);}
            else if(sync.status()==ScheduleCore.Status.UPDATING){float angle=animationsEnabled()?(android.os.SystemClock.uptimeMillis()%1200)*.3f:-90;c.drawArc(x-dp(10),y-dp(10),x+dp(10),y+dp(10),angle,270,false,p);if(animationsEnabled())postInvalidateDelayed(32);}
            else {c.drawLine(x,y-dp(5),x,y+dp(1),p);p.setStyle(Paint.Style.FILL);c.drawCircle(x,y+dp(5),dp(1),p);}
        }
    }
    private final class NavIcon extends View {
        private final int type;private final boolean chosen;private final Paint p=new Paint(3);NavIcon(int type,boolean chosen){super(MainActivity.this);this.type=type;this.chosen=chosen;}
        protected void onDraw(Canvas canvas){float scale=getWidth()/24f;canvas.save();canvas.scale(scale,scale);p.setColor(buttonColor(this));p.setStrokeWidth(1.6f);p.setStyle(Paint.Style.STROKE);
            if(type==0){canvas.drawRoundRect(3,4,21,21,3,3,p);canvas.drawLine(3,9,21,9,p);canvas.drawLine(8,2,8,6,p);canvas.drawLine(16,2,16,6,p);canvas.drawLine(7,13,17,13,p);canvas.drawLine(7,17,13,17,p);}
            else if(type==1){canvas.drawRoundRect(4,3,20,21,2,2,p);canvas.drawLine(8,8,16,8,p);canvas.drawLine(8,12,16,12,p);canvas.drawLine(8,16,14,16,p);}
            else {canvas.drawCircle(12,12,7,p);canvas.drawCircle(12,12,2.5f,p);for(int i=0;i<8;i++){double a=i*Math.PI/4;canvas.drawLine(12+(float)Math.cos(a)*8,12+(float)Math.sin(a)*8,12+(float)Math.cos(a)*10,12+(float)Math.sin(a)*10,p);}}canvas.restore();}
    }
    private JSONArray visibleExams(){return examRecords;}
    private int examBandHeight(){if(snapshot==null)return 0;int max=0;LocalDate monday=snapshot.mondayOf(week);JSONArray exams=visibleExams();for(int day=0;day<7;day++){int count=0;for(int i=0;i<exams.length();i++){JSONObject exam=exams.optJSONObject(i);if(exam!=null&&monday.plusDays(day).toString().equals(exam.optString("date")))count++;}max=Math.max(max,count);}return max*72;}
    private void showExam(JSONObject exam){new AppDialog.Builder(this).setTitle("考试 · "+exam.optString("name")).setMessage(exam.optString("date")+"\n"+exam.optString("startTime")+"–"+exam.optString("endTime")+"\n地点："+exam.optString("room")+"\n\n"+"来自最近保存的官网考试安排；以官网为准").setPositiveButton("知道了",null).show();}
    private final class TimetableView extends View {
        private final Paint p=new Paint(3);private final List<RectF> hitRects=new ArrayList<>(),examRects=new ArrayList<>();private final List<ScheduleCore.Course> hitCourses=new ArrayList<>();private final List<JSONObject> hitExams=new ArrayList<>();
        TimetableView(){super(MainActivity.this);setContentDescription("周课表，显示官网实际小节、日期与星期；考试显示在对应日期下并标出具体时间。课程详情可通过上方课程按钮查看。");}
        private void drawText(Canvas c,String value,float x,float y,float size,int color,boolean bold){p.setStyle(Paint.Style.FILL);p.setColor(color);p.setTextSize(dp(size));p.setTypeface(bold?Typeface.DEFAULT_BOLD:Typeface.DEFAULT);c.drawText(value,x,y,p);}
        private void wrapped(Canvas c,String value,RectF rect,float y,int color,int limit){
            int line=0;for(String part:value.split("\n",-1)){int offset=0;
                while(offset<part.length()&&line<limit&&y<rect.bottom-dp(1)){
                    p.setTextSize(dp(10));p.setTypeface(line==0?Typeface.DEFAULT_BOLD:Typeface.DEFAULT);
                    int chars=Math.max(1,p.breakText(part,offset,part.length(),true,Math.max(dp(4),rect.width()-dp(8)),null));
                    drawText(c,part.substring(offset,Math.min(part.length(),offset+chars)),rect.left+dp(4),y,10,color,line==0);offset+=chars;y+=dp(13);line++;
                }
                if(line>=limit||y>=rect.bottom-dp(1))break;
            }
        }
        protected void onDraw(Canvas c){super.onDraw(c);hitRects.clear();hitCourses.clear();examRects.clear();hitExams.clear();float w=getWidth(),h=getHeight();if(w<=0||h<=0)return;float gutter=dp(24),header=dp(64),band=dp(examBandHeight()),cw=(w-gutter)/7,rh=Math.max(dp(25),(h-header-band)/periodCount());LocalDate monday=snapshot==null?LocalDate.now(SCHOOL_TIME).with(java.time.DayOfWeek.MONDAY):snapshot.mondayOf(week);int[] origin=new int[2],at=new int[2];root.getLocationInWindow(origin);getLocationInWindow(at);
            {
            String[] days={"一","二","三","四","五","六","日"};int color=screenForeground;for(int day=0;day<7;day++){LocalDate date=monday.plusDays(day);float x=gutter+cw*day+dp(4);boolean today=date.equals(LocalDate.now(SCHOOL_TIME));if(today){p.setColor(color);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(1.6f));c.drawRoundRect(new RectF(gutter+cw*day+dp(1),dp(1),gutter+cw*(day+1)-dp(1),header-dp(5)),dp(2),dp(2),p);p.setStyle(Paint.Style.FILL);c.drawRect(gutter+cw*day+dp(2),header-dp(9),gutter+cw*(day+1)-dp(2),header-dp(5),p);}p.setShadowLayer(dp(1),0,0,color==Color.WHITE?0x99000000:0x99FFFFFF);drawText(c,date.format(DateTimeFormatter.ofPattern("M.dd")),x,dp(18),11,color,today);drawText(c,"周"+days[day],x,dp(35),12,color,today);if(today)drawText(c,"今天",x,dp(49),10,color,true);p.clearShadowLayer();}
            }
            JSONArray exams=visibleExams();int[] stacks=new int[7];for(int i=0;i<exams.length();i++){JSONObject exam=exams.optJSONObject(i);if(exam==null)continue;try{LocalDate date=LocalDate.parse(exam.optString("date"));long index=java.time.temporal.ChronoUnit.DAYS.between(monday,date);if(index<0||index>6)continue;int day=(int)index;float x=gutter+cw*day,y=header+dp(72)*stacks[day]++;RectF rect=new RectF(x+dp(1),y+dp(2),x+cw-dp(1),y+dp(70));p.setStyle(Paint.Style.FILL);p.setColor(AppRules.readableSurface(dark?0xFF664338:0xFFF7DACB,screenForeground));c.drawRoundRect(rect,dp(3),dp(3),p);c.save();c.clipRect(rect);wrapped(c,"考试\n"+exam.optString("startTime")+"\n–"+exam.optString("endTime")+"\n"+exam.optString("name"),rect,rect.top+dp(13),screenForeground,5);c.restore();examRects.add(rect);hitExams.add(exam);}catch(Exception ignored){}}
            for(int i=0;i<periodCount();i++){float y=header+band+i*rh+rh/2+dp(4);int color=screenForeground;p.setShadowLayer(dp(1),0,0,color==Color.WHITE?0xAA000000:0xAAFFFFFF);drawText(c,Integer.toString(i+1),dp(5),y,11,color,false);p.clearShadowLayer();}
            if(snapshot==null)return;int[] light={0xFFD6E6D3,0xFFF0DFBF,0xFFCEE1EF,0xFFE4D7EF,0xFFCBE7E0};int[] night={0xFF30473C,0xFF624632,0xFF30495F,0xFF4C3B65,0xFF603B46};for(ScheduleCore.Placement placement:ScheduleCore.layout(snapshot.courses,week)){ScheduleCore.Course course=placement.course;float slot=cw/placement.lanes;int start=course.rawStart>0?course.rawStart:course.start,end=course.rawEnd>0?course.rawEnd:course.end;float x=gutter+(course.day-1)*cw+placement.lane*slot,y=header+band+(start-1)*rh;RectF rect=new RectF(x+dp(1.5f),y+dp(3),x+slot-dp(1.5f),header+band+end*rh-dp(3));int index=(course.name.hashCode()&0x7fffffff)%light.length;int fill=prefs.getString("course_colors","varied").equals("solid")?prefs.getInt("solid_color",0xFFC9DDD1):dark?night[index]:light[index];int color=screenForeground;fill=AppRules.readableSurface(fill,color);p.setStyle(Paint.Style.FILL);p.setColor(fill);c.drawRoundRect(rect,dp(3),dp(3),p);hitRects.add(rect);hitCourses.add(course);c.save();c.clipRect(rect);AppRules.Location location=AppRules.location(course.room);int limit=Math.max(1,(int)((rect.height()-dp(20))/dp(13)));wrapped(c,course.name+(location.building.isEmpty()?"":"\n"+location.building),rect,rect.top+dp(13),color,limit);p.setTextSize(dp(10));float size=10;while(size>7&&p.measureText(location.room)>rect.width()-dp(8)){size-=.5f;p.setTextSize(dp(size));}String room=android.text.TextUtils.ellipsize(location.room,new android.text.TextPaint(p),Math.max(dp(4),rect.width()-dp(8)),android.text.TextUtils.TruncateAt.END).toString();drawText(c,room,rect.left+dp(4),rect.bottom-dp(5),size,color,true);if(placement.conflict)drawText(c,"!",rect.right-dp(6),rect.top+dp(10),8,color,true);c.restore();}
        }
        private float downX,downY;public boolean onTouchEvent(MotionEvent e){if(e.getAction()==MotionEvent.ACTION_DOWN){downX=e.getX();downY=e.getY();return true;}if(e.getAction()==MotionEvent.ACTION_MOVE&&Math.abs(e.getX()-downX)>dp(25)&&Math.abs(e.getX()-downX)>Math.abs(e.getY()-downY))getParent().requestDisallowInterceptTouchEvent(true);if(e.getAction()==MotionEvent.ACTION_UP){float dx=e.getX()-downX,dy=e.getY()-downY;if(Math.abs(dx)>dp(60)&&Math.abs(dx)>Math.abs(dy)){changeWeek(dx<0?1:-1);return true;}if(Math.abs(dx)<dp(15)&&Math.abs(dy)<dp(15)){performClick();for(int i=examRects.size()-1;i>=0;i--)if(examRects.get(i).contains(e.getX(),e.getY())){showExam(hitExams.get(i));return true;}for(int i=hitRects.size()-1;i>=0;i--)if(hitRects.get(i).contains(e.getX(),e.getY())){showCourse(hitCourses.get(i));return true;}}return true;}return true;}public boolean performClick(){super.performClick();return true;}
    }

    private void showCourse(ScheduleCore.Course course){new AppDialog.Builder(this).setTitle(course.name).setMessage("星期："+new String[]{"","一","二","三","四","五","六","日"}[course.day]+"\n官网小节："+(course.rawStart>0?course.rawStart:course.start)+"–"+(course.rawEnd>0?course.rawEnd:course.end)+"\n地点："+course.room+"\n教师："+course.teacher+"\n周次："+course.weeks+"\n\n"+(snapshot.source==ScheduleCore.Source.IMPORT?"来自本地导入文件":"以最新官网数据为准")).setPositiveButton("知道了",null).show();}
    private void showWeekCourses(){
        if(snapshot==null){toast("还没有可查看的课表");return;}
        List<ScheduleCore.Course> selected=new ArrayList<>();List<String> labels=new ArrayList<>();
        for(ScheduleCore.Course c:snapshot.courses)if(c.appears(week)){selected.add(c);labels.add("周"+new String[]{"","一","二","三","四","五","六","日"}[c.day]+" · "+(c.rawStart>0?c.rawStart:c.start)+"–"+(c.rawEnd>0?c.rawEnd:c.end)+"节  "+c.name);}
        if(selected.isEmpty()){toast("当前周没有已安排的课程");return;}
        new AppDialog.Builder(this).setTitle("第 "+week+" 周课程").setItems(labels.toArray(new String[0]),(d,w)->showCourse(selected.get(w))).setNegativeButton("关闭",null).show();
    }
}
