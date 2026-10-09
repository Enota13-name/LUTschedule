package cn.lut.schedule.tests;
import android.app.*;
import android.content.*;
import android.os.Bundle;
import java.io.*;

/** Current entry; historical implementations remain in their release branches. */
public final class RuntimeSmoke extends Instrumentation {
    private String phase="full";
    public void onCreate(Bundle args){super.onCreate(args);if(args!=null)phase=args.getString("phase","full");start();}
    public void onStart(){Bundle result=new Bundle();try{
        Context c=getTargetContext();c.deleteDatabase("schedule.db");c.deleteDatabase("academic.db");new File(c.getFilesDir(),"background.jpg").delete();
        c.getSharedPreferences("settings",0).edit().clear().putBoolean("demo",true).putString("developer","obsolete value").commit();c.getSharedPreferences("diagnostics",0).edit().clear().commit();c.getSharedPreferences("notification_seen",0).edit().clear().commit();
        ((NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE)).cancelAll();
        Intent intent=new Intent();intent.setClassName("cn.lut.schedule","cn.lut.schedule.MainActivity");intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);Activity a=startActivitySync(intent);waitForIdleSync();Thread.sleep(400);
        String current="",protocol="",regression="";
        if(phase.equals("full")||phase.equals("design"))current=new RuntimeV019(this).run(a);
        if(phase.equals("full")||phase.equals("protocol"))protocol=new RuntimeV017(this).run(a);
        if(phase.equals("full")||phase.equals("regression"))regression=new RuntimeV016(this).run(a);
        result.putString("result",current+"\n"+protocol+"\n"+regression);finish(Activity.RESULT_OK,result);
    }catch(Throwable error){StringWriter out=new StringWriter();error.printStackTrace(new PrintWriter(out));result.putString("result","FAIL: "+out);finish(Activity.RESULT_CANCELED,result);}}
}
