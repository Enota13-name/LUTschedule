package cn.lut.schedule;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import org.json.JSONArray;
import org.json.JSONObject;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.Set;

/** Validated module snapshots and durable, deduplicated change inbox. */
final class AcademicStore extends SQLiteOpenHelper {
    static final int MAX_PENDING=100;
    static final long CHANGE_MAX_AGE=90L*24*60*60*1000;
    AcademicStore(Context context){super(context,"academic.db",null,1);}
    public void onCreate(SQLiteDatabase db){db.execSQL("CREATE TABLE modules (name TEXT PRIMARY KEY,payload TEXT NOT NULL,stamp INTEGER NOT NULL,scope TEXT NOT NULL)");db.execSQL("CREATE TABLE changes (id TEXT PRIMARY KEY,kind TEXT NOT NULL,payload TEXT NOT NULL,stamp INTEGER NOT NULL)");}
    public void onUpgrade(SQLiteDatabase db,int old,int next){throw new IllegalStateException("缺少数据迁移");}
    JSONObject load(String module){try(Cursor c=getReadableDatabase().query("modules",new String[]{"payload"},"name=?",new String[]{module},null,null,null)){if(c.moveToFirst())return new JSONObject(c.getString(0));}catch(Exception ignored){}return null;}
    static void validate(String module,JSONObject page)throws Exception{
        if(!page.optBoolean("complete") || page.optBoolean("login") || !module.equals(page.optString("kind")))throw new IllegalArgumentException("未确认完整的"+module+"查询结果");
        JSONArray rows=page.getJSONArray("records");if(rows.length()>2000)throw new IllegalArgumentException("记录过多");Set<String> keys=new HashSet<>();
        for(int i=0;i<rows.length();i++){JSONObject item=rows.getJSONObject(i);String key=item.getString("key"),name=item.getString("name");if(key.isEmpty()||key.length()>500||name.trim().isEmpty()||name.length()>200||!keys.add(key))throw new IllegalArgumentException("记录标识重复或无效");
            if(module.equals("grades")){String score=item.getString("score");if(score.length()>60)throw new IllegalArgumentException("成绩格式无效");}
            if(module.equals("exams")){LocalDate.parse(item.getString("date"));LocalTime start=LocalTime.parse(item.getString("startTime")),end=LocalTime.parse(item.getString("endTime"));if(!end.isAfter(start))throw new IllegalArgumentException("考试结束时间无效");}
        }
        if(rows.length()==0&&!page.optBoolean("verifiedEmpty"))throw new IllegalArgumentException("不能把无法识别的数据当作空记录");
    }
    /** First complete read establishes a baseline; a later newly published grade enters the inbox. */
    void save(String module,JSONObject page,String scope,long time)throws Exception{
        validate(module,page);if(scope==null||scope.isEmpty()||time<=0)throw new IllegalArgumentException("缺少会话范围或读取时间");
        JSONObject old=load(module);String previousScope="";long stamp=0;
        try(Cursor c=getReadableDatabase().query("modules",new String[]{"scope","stamp"},"name=?",new String[]{module},null,null,null)){if(c.moveToFirst()){previousScope=c.getString(0);stamp=c.getLong(1);}}
        if(scope.equals(previousScope)&&time<stamp)return;
        JSONArray changes=new JSONArray();
        if(old!=null&&scope.equals(previousScope)){
            JSONObject previous=new JSONObject();JSONArray before=old.getJSONArray("records");for(int i=0;i<before.length();i++){JSONObject item=before.getJSONObject(i);previous.put(item.getString("key"),item);}
            JSONArray now=page.getJSONArray("records");for(int i=0;i<now.length();i++){JSONObject item=now.getJSONObject(i),was=previous.optJSONObject(item.getString("key"));
                boolean changed=module.equals("grades")?published(item.optString("score"))&&(was==null||!item.optString("score").equals(was.optString("score"))):module.equals("exams")&&(was==null||!item.toString().equals(was.toString()));
                if(changed)changes.put(item);
            }
        }
        JSONObject saved=new JSONObject(page.toString());saved.put("readAt",time);saved.put("scope",scope);
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{
            if(!previousScope.isEmpty()&&!scope.equals(previousScope))db.delete("changes",null,null);
            ContentValues data=new ContentValues();data.put("name",module);data.put("payload",saved.toString());data.put("stamp",time);data.put("scope",scope);if(db.insertWithOnConflict("modules",null,data,SQLiteDatabase.CONFLICT_REPLACE)==-1)throw new IllegalStateException("缓存写入失败");
            for(int i=0;i<changes.length();i++){JSONObject item=changes.getJSONObject(i);ContentValues v=new ContentValues();v.put("id",scope+":"+module+":"+item.getString("key")+":"+revision(module,item));v.put("kind",module);v.put("payload",item.toString());v.put("stamp",time);if(db.insertWithOnConflict("changes",null,v,SQLiteDatabase.CONFLICT_IGNORE)==-1){} }
            pruneChanges(db,time);
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
    private static boolean published(String score){return !score.trim().isEmpty()&&!score.matches("(?i)(--?|暂无.*|未发布|未录入|待.*|null)");}
    private static String revision(String module,JSONObject item){return module.equals("grades")?item.optString("score"):item.optString("date")+"/"+item.optString("startTime")+"/"+item.optString("endTime")+"/"+item.optString("room");}
    private static void pruneChanges(SQLiteDatabase db,long now){
        db.delete("changes","stamp<?",new String[]{Long.toString(now-CHANGE_MAX_AGE)});
        db.execSQL("DELETE FROM changes WHERE id NOT IN (SELECT id FROM changes ORDER BY stamp DESC,id DESC LIMIT "+MAX_PENDING+")");
    }
    JSONArray pending(){JSONArray out=new JSONArray();SQLiteDatabase db=getWritableDatabase();long now=System.currentTimeMillis();db.beginTransaction();try{pruneChanges(db,now);try(Cursor c=db.query("changes",new String[]{"id","kind","payload"},null,null,null,null,"stamp ASC,id ASC",Integer.toString(MAX_PENDING))){while(c.moveToNext()){JSONObject entry=new JSONObject();entry.put("id",c.getString(0));entry.put("kind",c.getString(1));entry.put("record",new JSONObject(c.getString(2)));out.put(entry);}}db.setTransactionSuccessful();}catch(Exception ignored){}finally{db.endTransaction();}return out;}
    void acknowledge(JSONArray entries){SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{for(int i=0;i<entries.length();i++){JSONObject e=entries.optJSONObject(i);if(e!=null)db.delete("changes","id=?",new String[]{e.optString("id")});}db.setTransactionSuccessful();}finally{db.endTransaction();}}
    void clear(){getWritableDatabase().delete("modules",null,null);getWritableDatabase().delete("changes",null,null);}
}
