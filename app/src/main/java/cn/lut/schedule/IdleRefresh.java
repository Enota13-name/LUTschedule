package cn.lut.schedule;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.PersistableBundle;

final class IdleRefresh {
    static final int ID=2408;
    static void operated(Context context){SharedPreferences p=context.getSharedPreferences("settings",0);long now=System.currentTimeMillis();p.edit().putLong("last_operation",now).apply();schedule(context);}
    static void schedule(Context context){SharedPreferences p=context.getSharedPreferences("settings",0);JobScheduler scheduler=(JobScheduler)context.getSystemService(Context.JOB_SCHEDULER_SERVICE);if(scheduler==null)return;
        long last=p.getLong("last_operation",0);if(!p.getBoolean("idle_refresh",true)||last==0||p.getLong("idle_consumed",0)==last){scheduler.cancel(ID);return;}
        long target=AppRules.idleTarget(last);PersistableBundle extras=new PersistableBundle();extras.putLong("cycle",last);extras.putLong("target",target);
        JobInfo job=new JobInfo.Builder(ID,new ComponentName(context,IdleRefreshService.class)).setMinimumLatency(Math.max(0,target-System.currentTimeMillis())).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPersisted(true).setExtras(extras).build();
        try{int result=scheduler.schedule(job);p.edit().putLong("idle_target",target).putBoolean("idle_scheduled",result==JobScheduler.RESULT_SUCCESS).apply();}catch(RuntimeException e){p.edit().putBoolean("idle_scheduled",false).apply();Diagnostics.record(context,"后台任务安排",e,false);}
    }
    private IdleRefresh(){}
}
