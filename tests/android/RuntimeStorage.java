package cn.lut.schedule.tests;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import org.json.JSONArray;
import org.json.JSONObject;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/** Synthetic storage regressions; no network, school account, or personal records. */
public final class RuntimeStorage {
    private RuntimeStorage() {}

    /** Invoke from RuntimeSmoke with the target app context. Returns the assertion count. */
    public static int run(Context context) throws Exception {
        int checks=0;
        Class<?> storeClass=Class.forName("cn.lut.schedule.AcademicStore");
        Constructor<?> ctor=storeClass.getDeclaredConstructor(Context.class);ctor.setAccessible(true);
        Object store=ctor.newInstance(context);
        Method save=storeClass.getDeclaredMethod("save",String.class,JSONObject.class,String.class,long.class);save.setAccessible(true);
        Method pendingMethod=storeClass.getDeclaredMethod("pending");pendingMethod.setAccessible(true);
        Method clear=storeClass.getDeclaredMethod("clear");clear.setAccessible(true);
        clear.invoke(store);
        long now=System.currentTimeMillis();
        save.invoke(store,"grades",page("baseline"),"synthetic-scope",now);
        for(int i=1;i<=150;i++)save.invoke(store,"grades",page("score-"+i),"synthetic-scope",now+i);
        JSONArray pending=(JSONArray)pendingMethod.invoke(store);
        require(pending.length()==100,"pending inbox is capped at 100 newest revisions");checks++;
        require("score-150".equals(pending.getJSONObject(99).getJSONObject("record").getString("score")),"latest valid revision is retained");checks++;
        require(!pending.getJSONObject(0).getJSONObject("record").getString("score").equals("score-1"),"oldest overflow revision is evicted");checks++;

        // The pending API also removes records older than 90 days.
        Method getDb=storeClass.getMethod("getWritableDatabase");SQLiteDatabase db=(SQLiteDatabase)getDb.invoke(store);
        db.execSQL("INSERT OR REPLACE INTO changes(id,kind,payload,stamp) VALUES(?,?,?,?)",new Object[]{"synthetic-expired","grades","{}",now-91L*24*60*60*1000});
        pending=(JSONArray)pendingMethod.invoke(store);for(int i=0;i<pending.length();i++)require(!"synthetic-expired".equals(pending.getJSONObject(i).optString("id")),"expired synthetic change removed");checks++;

        SharedPreferences prefs=context.getSharedPreferences("diagnostics",0);prefs.edit().clear().commit();
        Class<?> diagnostics=Class.forName("cn.lut.schedule.Diagnostics");Method operational=diagnostics.getDeclaredMethod("operational",Context.class,String.class,String.class);operational.setAccessible(true);
        for(int i=0;i<40;i++)operational.invoke(null,context,"synthetic network","timeout "+i);
        JSONArray operations=new JSONArray(prefs.getString("operational","[]"));require(operations.length()==24,"silent operational log is capped at 24");checks++;
        require(!prefs.getBoolean("pending",false),"silent operational log does not set a dialog marker");checks++;

        SharedPreferences seen=context.getSharedPreferences("notification_seen",0);seen.edit().clear().putBoolean("synthetic-legacy-seen",true).commit();
        Class<?> notifications=Class.forName("cn.lut.schedule.GradeNotifications");Method migrate=notifications.getDeclaredMethod("readAndMigrate",SharedPreferences.class,long.class);migrate.setAccessible(true);
        @SuppressWarnings("unchecked") java.util.Map<String,Long> history=(java.util.Map<String,Long>)migrate.invoke(null,seen,now);
        require(history.containsKey("synthetic-legacy-seen")&&seen.getLong("synthetic-legacy-seen",0)>0,"legacy boolean dedupe marker migrates to timestamp");checks++;
        Method prune=notifications.getDeclaredMethod("pruneSeen",SharedPreferences.class,java.util.Map.class,java.util.Set.class,long.class);prune.setAccessible(true);
        java.util.Set<String> unread=new java.util.HashSet<>();unread.add("synthetic-legacy-seen");
        SharedPreferences.Editor seed=seen.edit();for(int i=0;i<130;i++){history.put("synthetic-seen-"+i,now-i);seed.putLong("synthetic-seen-"+i,now-i);}seed.commit();
        prune.invoke(null,seen,history,unread,now);
        require(history.size()<=100&&seen.getAll().size()<=100&&history.containsKey("synthetic-legacy-seen"),"seen history is bounded while preserving an unread dedupe key");checks++;
        clear.invoke(store);require(seen.contains("synthetic-legacy-seen"),"store clear preserves notification markers unless the account-exit flow explicitly clears them");checks++;
        return checks;
    }

    private static JSONObject page(String score)throws Exception {
        JSONObject root=new JSONObject();root.put("kind","grades");root.put("complete",true);
        JSONArray rows=new JSONArray();JSONObject item=new JSONObject();item.put("key","synthetic-grade");item.put("name","合成记录");item.put("score",score);rows.put(item);root.put("records",rows);return root;
    }
    private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
