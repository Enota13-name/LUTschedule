package cn.lut.schedule;
import android.app.*;
import android.content.*;
import android.os.*;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;
import java.lang.ref.WeakReference;

/** Local persistence first; fatal errors are recovered on the next launch. */
public final class Diagnostics extends Application {
    private static WeakReference<MainActivity> active=new WeakReference<>(null);
    private static final Handler ui=new Handler(Looper.getMainLooper());
    private static String shown="";
    public void onCreate(){super.onCreate();Thread.UncaughtExceptionHandler previous=Thread.getDefaultUncaughtExceptionHandler();Thread.setDefaultUncaughtExceptionHandler((thread,error)->{record(this,"未处理异常",error,true);if(previous!=null)previous.uncaughtException(thread,error);else{android.os.Process.killProcess(android.os.Process.myPid());System.exit(10);}});}
    static void record(Context c,String stage,Throwable error,boolean fatal){ErrorReports.record(c,stage,"本地异常，详见堆栈",error,fatal);if(!fatal)dispatch();}
    static void event(Context c,String stage,String reason){ErrorReports.record(c,stage,reason,null,false);dispatch();}
    static String last(Context c){return c.getSharedPreferences("diagnostics",0).getString("events","[]").equals("[]")?"":ErrorReports.export(c);}
    static String report(Context c){return ErrorReports.export(c);}
    static boolean needsRecovery(Context c){return c.getSharedPreferences("diagnostics",0).getBoolean("fatal",false);}
    static void clearFatal(Context c){c.getSharedPreferences("diagnostics",0).edit().putBoolean("fatal",false).apply();}
    static void attach(MainActivity a){active=new WeakReference<>(a);dispatch();}
    static void detach(MainActivity a){if(active.get()==a)active.clear();}
    private static void dispatch(){ui.removeCallbacks(show);ui.postDelayed(show,650);}
    private static final Runnable show=new Runnable(){public void run(){MainActivity a=active.get();if(a==null)return;SharedPreferences p=a.getSharedPreferences("diagnostics",0);String id=p.getString("event_id","");if(id.isEmpty()||id.equals(shown)||!p.getBoolean("pending",false))return;if(!a.diagnosticsCanShow()){ui.postDelayed(this,650);return;}shown=id;p.edit().putBoolean("pending",false).apply();a.showDiagnosticError(id);}};
    static void copy(Activity a){ClipboardManager clipboard=(ClipboardManager)a.getSystemService(Context.CLIPBOARD_SERVICE);if(clipboard!=null){clipboard.setPrimaryClip(ClipData.newPlainText("LUTschedule 错误报告",report(a)));UiNotice.show(a,"错误报告已复制，可以交给 AI 排查");}}
    static void showRecovery(Activity a){boolean dark=AppDialog.dark(a);int ink=dark?0xFFF0F0F0:0xFF222222,base=dark?0xFF121212:0xFFF7F7F7;float density=a.getResources().getDisplayMetrics().density;int pad=Math.round(24*density);ScrollView scroll=new ScrollView(a);scroll.setBackgroundColor(base);LinearLayout body=new LinearLayout(a);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(pad,pad*2,pad,pad);scroll.addView(body);TextView title=new TextView(a);title.setText("启动遇到了问题");title.setTextColor(ink);title.setTextSize(25);body.addView(title);TextView hint=new TextView(a);hint.setText("课表缓存没有清除。复制本地错误报告并交给 AI 排查；报告内附项目仓库、源码分支和堆栈。\n");hint.setTextColor(ink);hint.setTextSize(14);hint.setPadding(0,pad/2,0,pad);body.addView(hint);
        for(int i=0;i<2;i++){final boolean retry=i==1;TextView action=new TextView(a);action.setText(retry?"重新打开课表":"复制错误报告");action.setTextColor(ink);action.setTextSize(15);action.setPadding(pad/2,pad/2,pad/2,pad/2);GradientDrawable shape=new GradientDrawable();shape.setColor(dark?0xFF2B2B2B:0xFFECECEC);shape.setCornerRadius(4*density);action.setBackground(shape);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=pad/2;body.addView(action,lp);action.setOnClickListener(v->{if(!retry)copy(a);else{clearFatal(a);a.startActivity(new Intent(a,MainActivity.class));a.finish();}});}
        TextView report=new TextView(a);report.setText(report(a));report.setTextSize(12);report.setTextColor(ink);report.setTextIsSelectable(true);body.addView(report);a.setContentView(scroll);new AppDialog.Builder(a).setTitle("发生了启动错误").setMessage("错误报告已保存在本机。复制报告交给 AI，或先重新尝试打开课表。").setPositiveButton("知道了",null).show();}
}
