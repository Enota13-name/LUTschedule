package cn.lut.schedule;

import android.webkit.WebView;
import android.os.SystemClock;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

final class CourseReader {
    interface Callback { void result(JSONObject page,Exception error); }
    private static String script,bootstrap;
    private static String asset(WebView view,String name)throws Exception{
        try(InputStream in=view.getContext().getAssets().open(name)){ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[4096];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);return new String(out.toByteArray(),java.nio.charset.StandardCharsets.UTF_8);}
    }
    static void read(WebView view,Callback cb){
        try{
            if(script==null)script=asset(view,"read-course-page.js");
            if(bootstrap==null)bootstrap=asset(view,"read-emap-course.js");
            String expected=view.getUrl();long deadline=SystemClock.uptimeMillis()+22000;
            view.evaluateJavascript(bootstrap,ignored->poll(view,expected,deadline,cb));
        }catch(Exception e){cb.result(null,e);}
    }
    private static void poll(WebView view,String expected,long deadline,Callback cb){
        try{
            if(expected==null || !expected.equals(view.getUrl())){cb.result(null,new IllegalStateException("页面已跳转，忽略旧读取"));return;}
            view.evaluateJavascript(script,raw->{
                JSONObject page;
                try{page=new JSONObject(new JSONArray("["+raw+"]").getString(0));}
                catch(Exception error){cb.result(null,error);return;}
                if(!page.optBoolean("pending")){cb.result(page,null);return;}
                if(SystemClock.uptimeMillis()>=deadline){try{page.put("pending",false);page.put("complete",false);page.put("networkError",true);page.put("errors",new JSONArray().put("课表接口读取超时，已保留缓存"));}catch(Exception ignored){}cb.result(page,null);return;}
                view.postDelayed(()->poll(view,expected,deadline,cb),300);
            });
        }catch(Exception error){cb.result(null,error);}
    }
    static ScheduleCore.Snapshot decode(JSONObject page,String configuredMonday,int periodsPerRow,long readAt)throws Exception{
        if(!page.optBoolean("complete") || page.optBoolean("login"))throw new IllegalArgumentException("尚未读到完整学期课表");
        String first=page.optString("firstMonday");if(first.isEmpty())first=configuredMonday;
        if(first==null || first.isEmpty())throw new IllegalArgumentException("需要设置第一教学周的周一日期");
        JSONArray array=page.getJSONArray("courses");if(array.length()==0 || array.length()>500)throw new IllegalArgumentException("没有可验证的课程记录；不覆盖缓存");
        List<ScheduleCore.Course> courses=new ArrayList<>();int maxWeek=1;
        for(int i=0;i<array.length();i++){JSONObject item=array.getJSONObject(i);JSONArray sourceWeeks=item.getJSONArray("weeks");List<Integer> weeks=new ArrayList<>();
            for(int j=0;j<sourceWeeks.length();j++){int week=sourceWeeks.getInt(j);weeks.add(week);maxWeek=Math.max(maxWeek,week);}
            int rawStart=item.getInt("rawStart"),rawEnd=item.getInt("rawEnd"),start=(rawStart-1)/periodsPerRow+1,end=(rawEnd-1)/periodsPerRow+1;
            courses.add(new ScheduleCore.Course(item.getString("name"),item.optString("teacher"),item.optString("room"),item.getInt("day"),start,end,weeks,rawStart,rawEnd,periodsPerRow));
        }
        return new ScheduleCore.Snapshot(page.optString("semester").isEmpty()?"官网当前学期":page.getString("semester"),LocalDate.parse(first),Math.max(page.optInt("totalWeeks",0),maxWeek),ScheduleCore.Source.OFFICIAL,readAt,courses,page.optInt("periodCount",0));
    }
}
