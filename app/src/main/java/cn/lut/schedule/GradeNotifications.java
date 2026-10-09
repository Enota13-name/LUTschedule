package cn.lut.schedule;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;

final class GradeNotifications {
    static final String CHANNEL="new_grades";
    static boolean allowed(Context c){NotificationManager manager=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);if(manager==null||!manager.areNotificationsEnabled()||android.os.Build.VERSION.SDK_INT>=33&&c.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)return false;NotificationChannel channel=manager.getNotificationChannel(CHANNEL);return channel==null||channel.getImportance()!=NotificationManager.IMPORTANCE_NONE;}
    /** Called only when the app is visible, including changes queued by a silent background run. */
    static void deliver(Context context,JSONArray inbox){if(!allowed(context))return;NotificationManager manager=(NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);NotificationChannel channel=new NotificationChannel(CHANNEL,"新成绩提醒",NotificationManager.IMPORTANCE_DEFAULT);channel.setDescription("打开 App 更新后发现新成绩时提醒；静默后台任务不会发通知");manager.createNotificationChannel(channel);
        NotificationChannel actual=manager.getNotificationChannel(CHANNEL);if(actual!=null&&actual.getImportance()==NotificationManager.IMPORTANCE_NONE)return;
        SharedPreferences seen=context.getSharedPreferences("notification_seen",0);int count=0;java.util.List<String> ids=new java.util.ArrayList<>();
        for(int i=0;i<inbox.length();i++){JSONObject e=inbox.optJSONObject(i);if(e!=null&&"grades".equals(e.optString("kind"))&&!seen.getBoolean(e.optString("id"),false)){count++;ids.add(e.optString("id"));}}
        if(count==0)return;Intent open=new Intent(context,BootstrapActivity.class);open.putExtra("show_changes",true);open.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pending=PendingIntent.getActivity(context,41,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification notification=new Notification.Builder(context,CHANNEL).setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("有 "+count+" 项新成绩").setContentText("点击打开课表，查看本次成绩更新").setContentIntent(pending).setAutoCancel(true).setVisibility(Notification.VISIBILITY_PRIVATE).setCategory(Notification.CATEGORY_STATUS).build();
        try{manager.notify(41,notification);SharedPreferences.Editor edit=seen.edit();for(String id:ids)edit.putBoolean(id,true);edit.apply();}catch(SecurityException ignored){}
    }
    private GradeNotifications(){}
}
