package cn.lut.schedule;

import android.app.job.JobService;
import android.app.job.JobParameters;
import android.content.SharedPreferences;
import android.widget.FrameLayout;

/** Cache-only scheduled work; foreground sync can preempt and finish this job quietly. */
public final class IdleRefreshService extends JobService {
    private SyncCoordinator coordinator;
    private JobParameters activeParams;

    public boolean onStartJob(JobParameters params) {
        SharedPreferences p = getSharedPreferences("settings", 0);
        long cycle = params.getExtras().getLong("cycle", 0), target = params.getExtras().getLong("target", 0);
        if (!AccessMode.official(this) || !p.getBoolean("idle_refresh", true) || cycle == 0 || cycle != p.getLong("last_operation", 0) || p.getLong("idle_consumed", 0) == cycle) return false;
        if (target != AppRules.idleTarget(cycle) || System.currentTimeMillis() < target) { IdleRefresh.schedule(this); return false; }
        p.edit().putLong("idle_consumed", cycle).putLong("idle_checked", System.currentTimeMillis()).apply();
        try {
            FrameLayout host = new FrameLayout(this);
            coordinator = new SyncCoordinator(this, host, new SiteGateway(this, host));
            activeParams = params;
            SyncCoordinator.Events events = new SyncCoordinator.Events() {
                public void timetableReady(ScheduleCore.Snapshot snapshot) {}
                public void moduleUpdated(String module, boolean success, String detail) {}
                public void finished(ScheduleCore.Status status, String detail) { finishActiveJob(params); }
            };
            boolean started = coordinator.start(events, false);
            if (!started) { coordinator.close(); coordinator = null; activeParams = null; return false; }
            return true;
        } catch (RuntimeException | LinkageError e) {
            Diagnostics.record(this, "静默缓存更新", e, false);
            if (coordinator != null) { coordinator.close(); coordinator = null; }
            activeParams = null;
            return false;
        }
    }

    private void finishActiveJob(JobParameters params) {
        if (activeParams != params) return;
        if (coordinator != null) { coordinator.close(); coordinator = null; }
        activeParams = null;
        jobFinished(params, false);
    }

    public boolean onStopJob(JobParameters params) {
        if (activeParams == params) {
            activeParams = null;
            if (coordinator != null) { coordinator.close(); coordinator = null; }
        }
        return false;
    }

    public void onDestroy() {
        activeParams = null;
        if (coordinator != null) { coordinator.close(); coordinator = null; }
        super.onDestroy();
    }
}
