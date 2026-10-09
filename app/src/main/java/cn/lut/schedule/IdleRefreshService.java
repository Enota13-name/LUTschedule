package cn.lut.schedule;
import android.app.job.JobService;
import android.app.job.JobParameters;
import android.content.SharedPreferences;
import android.widget.FrameLayout;

/** Cache only: this service never creates notifications, dialogs, sound or vibration. */
public final class IdleRefreshService extends JobService {
    private SyncCoordinator coordinator;
    public boolean onStartJob(JobParameters params){SharedPreferences p=getSharedPreferences("settings",0);long cycle=params.getExtras().getLong("cycle",0),target=params.getExtras().getLong("target",0);
        if(!p.getBoolean("idle_refresh",true)||cycle==0||cycle!=p.getLong("last_operation",0)||p.getLong("idle_consumed",0)==cycle)return false;
        if(target!=AppRules.idleTarget(cycle)||System.currentTimeMillis()<target){IdleRefresh.schedule(this);return false;}
        p.edit().putLong("idle_consumed",cycle).putLong("idle_checked",System.currentTimeMillis()).apply();
        try{FrameLayout host=new FrameLayout(this);coordinator=new SyncCoordinator(this,host,new SiteGateway(this,host));coordinator.start((status,detail)->{if(coordinator==null)return;coordinator.close();coordinator=null;jobFinished(params,false);});return true;}
        catch(RuntimeException|LinkageError e){Diagnostics.record(this,"静默缓存更新",e,false);if(coordinator!=null){coordinator.close();coordinator=null;}return false;}
    }
    public boolean onStopJob(JobParameters params){if(coordinator!=null){coordinator.close();coordinator=null;}return false;}
    public void onDestroy(){if(coordinator!=null)coordinator.close();super.onDestroy();}
}
