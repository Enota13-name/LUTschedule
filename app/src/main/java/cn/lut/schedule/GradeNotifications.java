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
    static final int MAX_SEEN=100;
    static final long SEEN_MAX_AGE=90L*24*60*60*1000;
    static boolean allowed(Context c){NotificationManager manager=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);if(manager==null||!manager.areNotificationsEnabled()||android.os.Build.VERSION.SDK_INT>=33&&c.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)return false;NotificationChannel channel=manager.getNotificationChannel(CHANNEL);return channel==null||channel.getImportance()!=NotificationManager.IMPORTANCE_NONE;}
    /** Called only when the app is visible, including changes queued by a silent background run. */
    static void deliver(Context context,JSONArray inbox){
        SharedPreferences seen=context.getSharedPreferences("notification_seen",0);long now=System.currentTimeMillis();java.util.Map<String,Long> history=readAndMigrate(seen,now);int count=0;java.util.List<String> ids=new java.util.ArrayList<>();java.util.Set<String> unread=new java.util.HashSet<>();
        for(int i=0;i<inbox.length();i++){JSONObject e=inbox.optJSONObject(i);if(e!=null&&"grades".equals(e.optString("kind"))){String id=e.optString("id");if(!id.isEmpty()){unread.add(id);if(!history.containsKey(id)){count++;ids.add(id);}}}}
        pruneSeen(seen,history,unread,now);
        if(count==0||!allowed(context))return;NotificationManager manager=(NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);if(manager==null)return;NotificationChannel channel=new NotificationChannel(CHANNEL,"新成绩提醒",NotificationManager.IMPORTANCE_DEFAULT);channel.setDescription("打开 App 更新后发现新成绩时提醒；静默后台任务不会发通知");manager.createNotificationChannel(channel);
        NotificationChannel actual=manager.getNotificationChannel(CHANNEL);if(actual!=null&&actual.getImportance()==NotificationManager.IMPORTANCE_NONE)return;
        Intent open=new Intent(context,BootstrapActivity.class);open.putExtra("show_changes",true);open.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pending=PendingIntent.getActivity(context,41,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification notification=new Notification.Builder(context,CHANNEL).setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("有 "+count+" 项新成绩").setContentText("点击打开课表，查看本次成绩更新").setContentIntent(pending).setAutoCancel(true).setVisibility(Notification.VISIBILITY_PRIVATE).setCategory(Notification.CATEGORY_STATUS).build();
        try{manager.notify(41,notification);SharedPreferences.Editor edit=seen.edit();for(String id:ids)edit.putLong(id,now);edit.apply();history.putAll(asMap(ids,now));pruneSeen(seen,history,unread,now);}catch(SecurityException ignored){}
    }
    private static java.util.Map<String,Long> readAndMigrate(SharedPreferences prefs,long now){java.util.Map<String,Long> values=new java.util.HashMap<>();SharedPreferences.Editor edit=prefs.edit();boolean changed=false;for(java.util.Map.Entry<String,?> entry:prefs.getAll().entrySet()){Object value=entry.getValue();if(value instanceof Long){long stamp=(Long)value;if(stamp>0)values.put(entry.getKey(),stamp);else{edit.remove(entry.getKey());changed=true;}}else if(value instanceof Boolean){if((Boolean)value){values.put(entry.getKey(),now);edit.putLong(entry.getKey(),now);}else edit.remove(entry.getKey());changed=true;}else{edit.remove(entry.getKey());changed=true;}}if(changed)edit.apply();return values;}
    private static java.util.Map<String,Long> asMap(java.util.List<String> ids,long stamp){java.util.Map<String,Long> out=new java.util.HashMap<>();for(String id:ids)out.put(id,stamp);return out;}
    private static void pruneSeen(SharedPreferences prefs,java.util.Map<String,Long> history,java.util.Set<String> unread,long now){java.util.List<java.util.Map.Entry<String,Long>> candidates=new java.util.ArrayList<>();java.util.List<String> expired=new java.util.ArrayList<>();SharedPreferences.Editor edit=prefs.edit();for(java.util.Map.Entry<String,Long> item:history.entrySet()){if(item.getValue()<now-SEEN_MAX_AGE&&!unread.contains(item.getKey()))expired.add(item.getKey());else if(!unread.contains(item.getKey()))candidates.add(item);}for(String id:expired){edit.remove(id);history.remove(id);}candidates.sort(java.util.Map.Entry.comparingByValue());int excess=history.size()-MAX_SEEN;for(int i=0;i<candidates.size()&&excess>0;i++,excess--){String id=candidates.get(i).getKey();edit.remove(id);history.remove(id);}edit.apply();}
    private GradeNotifications(){}
}
