package cn.lut.schedule;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import org.json.JSONArray;
import org.json.JSONObject;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

final class SnapshotStore extends SQLiteOpenHelper {
    SnapshotStore(Context c){super(c,"schedule.db",null,1);}
    public void onCreate(SQLiteDatabase db){db.execSQL("CREATE TABLE snapshots (source TEXT PRIMARY KEY, payload TEXT NOT NULL)");}
    public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion){throw new IllegalStateException("需要显式数据库迁移");}
    ScheduleCore.Snapshot load(ScheduleCore.Source source) {
        try(Cursor c=getReadableDatabase().query("snapshots",new String[]{"payload"},"source=?",new String[]{source.name()},null,null,null)){
            if(c.moveToFirst()) return decode(new JSONObject(c.getString(0)),source);
        }catch(Exception ignored){} // A corrupt snapshot is never interpreted as an empty valid timetable.
        return null;
    }
    void save(ScheduleCore.Snapshot snapshot) throws Exception {
        String payload=encode(snapshot).toString();
        decode(new JSONObject(payload),snapshot.source); // Validate before beginning the atomic replacement.
        SQLiteDatabase db=getWritableDatabase(); db.beginTransaction();
        try {ContentValues values=new ContentValues(); values.put("source",snapshot.source.name()); values.put("payload",payload);
            if(db.insertWithOnConflict("snapshots",null,values,SQLiteDatabase.CONFLICT_REPLACE)==-1)throw new IllegalStateException("课表写入失败");
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
    void clear(){getWritableDatabase().delete("snapshots",null,null);}
    static ScheduleCore.Snapshot decode(JSONObject o,ScheduleCore.Source forcedSource) throws Exception {
        if(o.getInt("schemaVersion")!=1) throw new IllegalArgumentException("不支持的课表格式版本");
        JSONArray array=o.getJSONArray("courses"); List<ScheduleCore.Course> courses=new ArrayList<>();
        for(int i=0;i<array.length();i++){
            JSONObject item=array.getJSONObject(i); JSONArray rawWeeks=item.getJSONArray("weeks"); List<Integer> weeks=new ArrayList<>();
            for(int j=0;j<rawWeeks.length();j++) weeks.add(rawWeeks.getInt(j));
            courses.add(new ScheduleCore.Course(item.getString("name"),item.optString("teacher"),item.optString("room"),
                item.getInt("day"),item.getInt("start"),item.getInt("end"),weeks,item.optInt("rawStart",0),item.optInt("rawEnd",0),item.optInt("periodsPerRow",1)));
        }
        return new ScheduleCore.Snapshot(o.getString("semester"),LocalDate.parse(o.getString("firstMonday")),o.getInt("totalWeeks"),
            forcedSource,o.optLong("fetchedAt",0),courses,o.optInt("periodCount",0));
    }
    static JSONObject encode(ScheduleCore.Snapshot s)throws Exception{
        JSONObject o=new JSONObject(); o.put("schemaVersion",1);o.put("semester",s.semester);o.put("firstMonday",s.firstMonday.toString());
        o.put("totalWeeks",s.totalWeeks);o.put("source",s.source.name());o.put("fetchedAt",s.fetchedAt);
        o.put("periodCount",s.periodCount);
        JSONArray courses=new JSONArray();for(ScheduleCore.Course c:s.courses){JSONObject item=new JSONObject();
            item.put("name",c.name);item.put("teacher",c.teacher);item.put("room",c.room);item.put("day",c.day);item.put("start",c.start);item.put("end",c.end);
            item.put("weeks",new JSONArray(c.weeks));item.put("rawStart",c.rawStart);item.put("rawEnd",c.rawEnd);item.put("periodsPerRow",c.periodsPerRow); courses.put(item);}
        o.put("courses",courses);return o;
    }
}
