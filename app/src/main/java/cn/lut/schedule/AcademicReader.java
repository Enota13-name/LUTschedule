package cn.lut.schedule;
import android.webkit.WebView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;

final class AcademicReader {
    interface Callback{void read(JSONObject page,Exception error);}
    static String asset(WebView view,String name)throws Exception{try(InputStream in=view.getContext().getAssets().open(name)){ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1)out.write(b,0,n);return new String(out.toByteArray(),java.nio.charset.StandardCharsets.UTF_8);}}
    static void capture(WebView view){try{view.evaluateJavascript(asset(view,"capture-academic.js"),null);}catch(Exception ignored){}}
    static void read(WebView view,String kind,Callback callback){try{String url=view.getUrl();String script=asset(view,"read-official-academic.js").replace("__LUT_KIND__",JSONObject.quote(kind));view.evaluateJavascript(script,ignored->poll(view,kind,url,android.os.SystemClock.uptimeMillis()+25000,callback));}catch(Exception e){callback.read(null,e);}}
    private static void poll(WebView view,String kind,String url,long deadline,Callback callback){try{if(url==null||!url.equals(view.getUrl())){callback.read(null,new IllegalStateException("页面已跳转"));return;}String script=asset(view,"read-academic-page.js").replace("__LUT_KIND__",JSONObject.quote(kind));view.evaluateJavascript(script,raw->{try{JSONObject page=new JSONObject(new JSONArray("["+raw+"]").getString(0));if(page.optBoolean("pending")){if(android.os.SystemClock.uptimeMillis()>=deadline){page.put("complete",false);page.put("reason","官网查询超时，保留缓存");callback.read(page,null);}else view.postDelayed(()->poll(view,kind,url,deadline,callback),250);}else callback.read(page,null);}catch(Exception e){callback.read(null,e);}});}catch(Exception e){callback.read(null,e);}}
}
