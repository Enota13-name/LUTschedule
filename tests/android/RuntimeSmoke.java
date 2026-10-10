package cn.lut.schedule.tests;
import android.app.*;
import android.content.*;
import android.os.Bundle;
import java.io.*;

/** Formal release suite: synthetic data only, no school account. */
public final class RuntimeSmoke extends Instrumentation {
    private String phase="full";
    public void onCreate(Bundle args){super.onCreate(args);if(args!=null)phase=args.getString("phase","full");start();}
    public void onStart(){Thread watchdog=new Thread(()->{try{Thread.sleep(90000);for(java.util.Map.Entry<Thread,StackTraceElement[]> entry:Thread.getAllStackTraces().entrySet())if(entry.getKey().getName().contains("Instr")||entry.getKey().getName().equals("main")){StringBuilder stack=new StringBuilder(entry.getKey().getName());for(StackTraceElement frame:entry.getValue())stack.append("\n").append(frame);android.util.Log.e("LutRuntimeTest",stack.toString());}}catch(InterruptedException done){}});watchdog.setDaemon(true);watchdog.start();Bundle result=new Bundle();try{
        Context c=getTargetContext();c.deleteDatabase("schedule.db");c.deleteDatabase("academic.db");new File(c.getFilesDir(),"background.jpg").delete();
        c.getSharedPreferences("settings",0).edit().clear().putBoolean("credential_intro_v1",true).putBoolean("demo",true).putString("developer","obsolete value").commit();c.getSharedPreferences("diagnostics",0).edit().clear().commit();c.getSharedPreferences("notification_seen",0).edit().clear().commit();
        ((NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE)).cancelAll();
        Intent intent=new Intent();intent.setClassName("cn.lut.schedule","cn.lut.schedule.MainActivity");intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);Activity a=startActivitySync(intent);waitForIdleSync();Thread.sleep(400);
        String current="",protocol="",regression="";
        if(phase.equals("full")||phase.equals("design"))current=new RuntimeSourceUi(this).run(a);
        if(phase.equals("full")||phase.equals("protocol"))protocol=new RuntimeOfficialProtocol(this).run(a);
        if(phase.equals("full")||phase.equals("regression"))regression=new RuntimeRegression(this).run(a);
        if(phase.equals("update"))regression="PASS: storage="+RuntimeStorage.run(c)+", credentials="+RuntimeCredentials.run(c)+" assertions; updateUi="+RuntimeUpdateUi.run(this,a);
        if(phase.equals("session-write")||phase.equals("session-read"))regression=RuntimeSession.run(this,phase.equals("session-write"));
        result.putString("result",current+"\n"+protocol+"\n"+regression);watchdog.interrupt();finish(Activity.RESULT_OK,result);
    }catch(Throwable error){StringWriter out=new StringWriter();error.printStackTrace(new PrintWriter(out));result.putString("result","FAIL: "+out);finish(Activity.RESULT_CANCELED,result);}}
}
