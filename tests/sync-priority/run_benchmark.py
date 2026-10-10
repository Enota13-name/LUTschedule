from pathlib import Path
import hashlib, json, os, shutil, subprocess

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
SRC = ROOT / 'app/src/main/java/cn/lut/schedule/SyncCoordinator.java'
BUILD = (HERE / 'build').resolve()
GEN = BUILD / 'generated'

def java_tool(name):
    """Resolve JAVA_HOME first, then PATH, then this workstation's JBR fallback."""
    home=os.environ.get('JAVA_HOME')
    candidates=[]
    if home:
        candidates.extend([Path(home)/'bin'/(name+'.exe'),Path(home)/'bin'/name])
    for executable in (name, name+'.exe'):
        found=shutil.which(executable)
        if found: candidates.append(Path(found))
    fallback=Path('D:/Android/jbr/bin')
    candidates.extend([fallback/(name+'.exe'),fallback/name])
    for candidate in candidates:
        if candidate.is_file(): return str(candidate)
    raise FileNotFoundError(f'Cannot find {name}; set JAVA_HOME or add a JDK to PATH')

def replace_once(source, anchor, replacement, label):
    count=source.count(anchor)
    if count!=1: raise SystemExit(f'{label} anchor must occur exactly once; found {count}')
    return source.replace(anchor,replacement)

STUBS = {
'android/content/Context.java': '''package android.content; public class Context { private final SharedPreferences p=new SharedPreferences(); public SharedPreferences getSharedPreferences(String n,int m){return p;} public android.content.res.Resources getResources(){return new android.content.res.Resources();} }''',
'android/content/SharedPreferences.java': '''package android.content; import java.util.*; public class SharedPreferences { Map<String,Object> m=new HashMap<>(); public String getString(String k,String d){return (String)m.getOrDefault(k,d);} public long getLong(String k,long d){return (long)m.getOrDefault(k,d);} public Editor edit(){return new Editor(this);} public static class Editor{SharedPreferences p;Map<String,Object> x=new HashMap<>();Editor(SharedPreferences p){this.p=p;}public Editor putLong(String k,long v){x.put(k,v);return this;}public Editor putString(String k,String v){x.put(k,v);return this;}public void apply(){p.m.putAll(x);}} }''',
'android/content/res/Resources.java': '''package android.content.res; public class Resources {public android.util.DisplayMetrics getDisplayMetrics(){return new android.util.DisplayMetrics();}}''',
'android/util/DisplayMetrics.java': '''package android.util; public class DisplayMetrics {public int widthPixels=400,heightPixels=800;}''',
'android/os/Handler.java': '''package android.os; public class Handler {public void postDelayed(Runnable r,long d){cn.lut.schedule.Clock.add(d,r);}public void removeCallbacksAndMessages(Object o){cn.lut.schedule.Clock.clear();}}''',
'android/graphics/Bitmap.java': '''package android.graphics; public class Bitmap {}''',
'android/net/Uri.java': '''package android.net; public class Uri {String s;Uri(String s){this.s=s;}public static Uri parse(String s){return new Uri(s);}public String toString(){return s;}}''',
'android/net/http/SslError.java': '''package android.net.http; public class SslError {}''',
'android/webkit/WebResourceRequest.java': '''package android.webkit; public class WebResourceRequest {public android.net.Uri getUrl(){return android.net.Uri.parse("https://jwxt.lut.edu.cn/test");}public boolean isForMainFrame(){return true;}}''',
'android/webkit/WebResourceError.java': '''package android.webkit; public class WebResourceError {}''',
'android/webkit/WebResourceResponse.java': '''package android.webkit; public class WebResourceResponse {public int getStatusCode(){return 500;}}''',
'android/webkit/SslErrorHandler.java': '''package android.webkit; public class SslErrorHandler {public void cancel(){}}''',
'android/webkit/RenderProcessGoneDetail.java': '''package android.webkit; public class RenderProcessGoneDetail {}''',
'android/webkit/WebViewClient.java': '''package android.webkit; public class WebViewClient {public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){return false;}public void onPageStarted(WebView v,String u,android.graphics.Bitmap b){}public void onPageCommitVisible(WebView v,String u){}public void onPageFinished(WebView v,String u){}public void onReceivedSslError(WebView v,SslErrorHandler h,android.net.http.SslError e){}public void onReceivedError(WebView v,WebResourceRequest r,WebResourceError e){}public void onReceivedHttpError(WebView v,WebResourceRequest r,WebResourceResponse e){}public boolean onRenderProcessGone(WebView v,RenderProcessGoneDetail d){return false;}}''',
'android/webkit/WebView.java': '''package android.webkit; public class WebView {WebViewClient c;String url;public WebView(android.content.Context x){}public void setWebViewClient(WebViewClient c){this.c=c;}public void loadUrl(String u){url=u;if(c!=null)c.onPageFinished(this,u);}public String getUrl(){return url;}public void evaluateJavascript(String s,Object c){}public void stopLoading(){}public void destroy(){}}''',
'android/webkit/CookieManager.java': '''package android.webkit; public class CookieManager {private static final CookieManager I=new CookieManager();public static CookieManager getInstance(){return I;}public String getCookie(String s){return "synthetic";}}''',
'android/widget/FrameLayout.java': '''package android.widget; public class FrameLayout {public FrameLayout(android.content.Context c){}public static class LayoutParams {public LayoutParams(int w,int h){}}public void addView(android.webkit.WebView v,LayoutParams p){}public void removeView(android.webkit.WebView v){}}''',
'org/json/JSONObject.java': '''package org.json; import java.util.*; public class JSONObject {Map<String,Object> m=new HashMap<>();public JSONObject(){}public JSONObject put(String k,Object v){m.put(k,v);return this;}public String optString(String k){return optString(k,"");}public String optString(String k,String d){Object v=m.get(k);return v==null?d:String.valueOf(v);}public boolean optBoolean(String k){return Boolean.TRUE.equals(m.get(k));}public int optInt(String k){Object v=m.get(k);return v instanceof Number?((Number)v).intValue():0;}public long optLong(String k){Object v=m.get(k);return v instanceof Number?((Number)v).longValue():0;}public boolean has(String k){return m.containsKey(k);}public JSONArray optJSONArray(String k){Object v=m.get(k);return v instanceof JSONArray?(JSONArray)v:null;}public JSONObject optJSONObject(String k){Object v=m.get(k);return v instanceof JSONObject?(JSONObject)v:null;}public String getString(String k){return optString(k);}}''',
'org/json/JSONArray.java': '''package org.json; import java.util.*; public class JSONArray {List<Object> a=new ArrayList<>();public int length(){return a.size();}public JSONObject optJSONObject(int i){return i>=0&&i<a.size()&&a.get(i) instanceof JSONObject?(JSONObject)a.get(i):null;}public String optString(int i){return i>=0&&i<a.size()?String.valueOf(a.get(i)):"";}public JSONArray put(Object o){a.add(o);return this;}}''',
'cn/lut/schedule/Clock.java': '''package cn.lut.schedule; import java.util.*; public class Clock {static long now;static PriorityQueue<E> q=new PriorityQueue<>();static class E implements Comparable<E>{long t;Runnable r;E(long t,Runnable r){this.t=t;this.r=r;}public int compareTo(E x){return Long.compare(t,x.t);}}public static void add(long d,Runnable r){q.add(new E(now+d,r));}public static void clear(){q.clear();}static void reset(){now=0;q.clear();}static void run(){int n=0;while(!q.isEmpty()&&n++<10000){E e=q.remove();now=e.t;e.r.run();}}}''',
'cn/lut/schedule/ScheduleCore.java': '''package cn.lut.schedule; public class ScheduleCore {public enum Status{IDLE,UPDATING,SUCCESS,LOGIN_REQUIRED,OFFLINE,UNREACHABLE,SERVER_ERROR,ADAPTER_PENDING,PARSE_ERROR}public enum Source{OFFICIAL,IMPORT}public static class Snapshot{public long fetchedAt=Clock.now;}}''',
'cn/lut/schedule/AccessMode.java': '''package cn.lut.schedule; public class AccessMode {public static boolean official(android.content.Context c){return true;}}''',
'cn/lut/schedule/SiteGateway.java': '''package cn.lut.schedule; public class SiteGateway {static final String HOME="https://jwxt.lut.edu.cn/";interface Callback{void finished(ScheduleCore.Status s,String m,org.json.JSONObject p);} Callback cb;int generation;public void start(Callback c){cb=c;int t=++generation;Clock.add(1000,()->{if(t==generation)c.finished(ScheduleCore.Status.SUCCESS,"course",new org.json.JSONObject().put("semester","synthetic"));});}public void close(){generation++;}public static void configure(android.webkit.WebView v){}public static boolean allowed(String s){return true;}public static String savedPageUrl(String s){return s;}}''',
'cn/lut/schedule/CourseReader.java': '''package cn.lut.schedule; public class CourseReader {public static ScheduleCore.Snapshot decode(org.json.JSONObject p,String m,int w,long t){ScheduleCore.Snapshot s=new ScheduleCore.Snapshot();s.fetchedAt=Clock.now;return s;}interface Callback{void done(org.json.JSONObject p,Throwable e);}}''',
'cn/lut/schedule/AcademicReader.java': '''package cn.lut.schedule; public class AcademicReader {interface Callback{void done(org.json.JSONObject p,Throwable e);}public static void capture(android.webkit.WebView v){}public static void read(android.webkit.WebView v,String module,Callback cb){long delay=module.equals("grades")?3000:module.equals("exams")?4000:2000;Clock.add(delay,()->{org.json.JSONObject p=new org.json.JSONObject().put("complete",true).put("kind",module).put("records",new org.json.JSONArray()).put("verifiedEmpty",true);cb.done(p,null);});}}''',
'cn/lut/schedule/AcademicStore.java': '''package cn.lut.schedule; public class AcademicStore implements AutoCloseable {static String failFor="";public AcademicStore(android.content.Context c){}public void save(String m,org.json.JSONObject p,String s,long t)throws Exception{if(failFor.equals(m))throw new Exception("synthetic save failure");}public void close(){}}''',
'cn/lut/schedule/SnapshotStore.java': '''package cn.lut.schedule; public class SnapshotStore implements AutoCloseable {static boolean fail;public SnapshotStore(android.content.Context c){}public void save(ScheduleCore.Snapshot s)throws Exception{if(fail)throw new Exception("synthetic snapshot failure");Harness.savedAt=Clock.now;}public void close(){}}''',
'cn/lut/schedule/AcademicCatalog.java': '''package cn.lut.schedule; public class AcademicCatalog {static String[][] GROUPS={{"体测成绩管理","课程查询","学分收费管理","学业完成查询","成绩认定"}};static String route(android.content.SharedPreferences p,String n){return "https://jwxt.lut.edu.cn/"+n;}}''',
'cn/lut/schedule/Harness.java': '''package cn.lut.schedule; public class Harness {static long savedAt=-1,readyAt=-1,finishedAt=-1;static ScheduleCore.Status finalStatus;static void reset(){Clock.reset();savedAt=readyAt=finishedAt=-1;finalStatus=null;SnapshotStore.fail=false;AcademicStore.failFor="";}static void run(boolean prototype,String fail,boolean saveFail,String scenario){reset();AcademicStore.failFor=fail;SnapshotStore.fail=saveFail;android.content.Context ctx=new android.content.Context();android.widget.FrameLayout host=new android.widget.FrameLayout(ctx);SiteGateway gateway=new SiteGateway();SyncCoordinator co=new SyncCoordinator(ctx,host,gateway);SyncCoordinator.Callback cb=(s,d)->{finishedAt=Clock.now;finalStatus=s;};if(prototype){co.start(cb,s->{readyAt=Clock.now;});}else co.start(cb);Clock.run();print(scenario);}static void stale(boolean prototype){reset();android.content.Context ctx=new android.content.Context();android.widget.FrameLayout host=new android.widget.FrameLayout(ctx);SiteGateway gateway=new SiteGateway();SyncCoordinator co=new SyncCoordinator(ctx,host,gateway);co.start((s,d)->finishedAt=Clock.now,s->readyAt=Clock.now);co.close();gateway.cb.finished(ScheduleCore.Status.SUCCESS,"late",new org.json.JSONObject().put("semester","synthetic"));print("cancelled_old_ticket");}static void print(String n){System.out.println("scenario="+n+", saved="+savedAt+", ready="+readyAt+", finished="+finishedAt+", status="+finalStatus);}public static void main(String[]a){if(a[0].equals("old")){run(false,"",false,"normal");return;}run(true,"",false,"normal");run(true,"exams",false,"later_module_failure");run(true,"",true,"timetable_save_failure");stale(true);}}'''
}

def write(rel, content):
    p=GEN/rel; p.parent.mkdir(parents=True,exist_ok=True); p.write_text(content,encoding='utf-8')

def main():
    here=HERE.resolve()
    if BUILD.parent!=here or BUILD.name!='build':
        raise SystemExit(f'Refusing to clean unverified build path: {BUILD}')
    if BUILD.exists(): shutil.rmtree(BUILD)
    BUILD.mkdir(parents=True)
    for rel, content in STUBS.items(): write(rel,content)
    prod=SRC.read_text(encoding='utf-8')
    write('cn/lut/schedule/SyncCoordinator.java',prod)
    proto=replace_once(prod,'interface Callback{void finished(ScheduleCore.Status status,String detail);}', 'interface Callback{void finished(ScheduleCore.Status status,String detail);}\n    interface TimetableReady{void ready(ScheduleCore.Snapshot snapshot);}','callback interface')
    proto=replace_once(proto,'void start(Callback cb){close();', 'void start(Callback cb){start(cb,snapshot->{});}\n    void start(Callback cb,TimetableReady ready){close();','start method')
    needle='cache.save(snapshot);timetableOk=true;prefs.edit().putLong("timetable_updated",snapshot.fetchedAt).apply();'
    proto=replace_once(proto,needle,needle+'ready.ready(snapshot);','post-save callback')
    write('prototype/cn/lut/schedule/SyncCoordinator.java',proto)
    # Compile each variant independently against the same stubs.
    results=[]
    for name, coord in [('old',GEN/'cn/lut/schedule/SyncCoordinator.java'),('prototype',GEN/'prototype/cn/lut/schedule/SyncCoordinator.java')]:
        out=GEN/'classes'/name; out.mkdir(parents=True,exist_ok=True)
        sources=[str(p) for p in GEN.rglob('*.java') if 'prototype' not in p.parts and p.name!='SyncCoordinator.java']+[str(coord)]
        # Select the coordinator and reset harness callbacks to match its signature.
        hs=GEN/'cn/lut/schedule/Harness.java'; text=STUBS['cn/lut/schedule/Harness.java']
        if name=='old':
            text='''package cn.lut.schedule; public class Harness {static long savedAt=-1,finishedAt=-1;static ScheduleCore.Status finalStatus;public static void main(String[]a){Clock.reset();android.content.Context ctx=new android.content.Context();SyncCoordinator co=new SyncCoordinator(ctx,new android.widget.FrameLayout(ctx),new SiteGateway());co.start((s,d)->{finishedAt=Clock.now;finalStatus=s;});Clock.run();System.out.println("scenario=normal, saved="+savedAt+", ready=-1, finished="+finishedAt+", status="+finalStatus);}}'''
        hs.write_text(text,encoding='utf-8')
        cmd=[java_tool('javac'),'-encoding','UTF-8','-d',str(out)]+sources
        subprocess.run(cmd,check=True,timeout=30)
        run=subprocess.run([java_tool('java'),'-cp',str(out),'cn.lut.schedule.Harness',name],capture_output=True,text=True,check=True,timeout=30)
        rows=[]
        for line in run.stdout.splitlines():
            row={}
            for part in line.split(','):
                k,v=part.strip().split('=',1)
                try: row[k]=int(v)
                except ValueError: row[k]=v
            rows.append(row)
        results.extend((name,row) for row in rows)
    out=[]
    for name,row in results:
        if name=='old': out.append({'scenario':'normal','variant':'production','timetable_saved_ms':row['saved'],'ready_callback_ms':None,'final_callback_ms':row['finished'],'post_save_wait_ms':row['finished']-row['saved'],'status':row['status']})
        else:
            scenario=row['scenario']
            out.append({'scenario':scenario,'variant':'isolated_prototype','timetable_saved_ms':row['saved'] if row['saved']>=0 else None,'ready_callback_ms':row['ready'] if row['ready']>=0 else None,'final_callback_ms':row['finished'] if row['finished']>=0 else None,'post_save_wait_ms':row['finished']-row['saved'] if row['saved']>=0 else None,'status':row['status'],'ready_survived_later_failure':scenario=='later_module_failure' and row['ready']>=0})
    checks={
      'old_waits_for_all_modules':out[0]['timetable_saved_ms'] is not None and out[0]['final_callback_ms']>out[0]['timetable_saved_ms'] and out[0]['ready_callback_ms'] is None,
      'prototype_ready_after_persist':out[1]['ready_callback_ms']==out[1]['timetable_saved_ms'],
      'prototype_preserves_total_end_time':out[1]['final_callback_ms']==out[0]['final_callback_ms'],
      'later_failure_does_not_revoke_ready':out[2]['ready_survived_later_failure'] and out[2]['status']!='SUCCESS',
      'save_failure_emits_no_ready':out[3]['timetable_saved_ms'] is None and out[3]['ready_callback_ms'] is None,
      'cancelled_ticket_emits_no_callback':out[4]['ready_callback_ms'] is None and out[4]['final_callback_ms'] is None,
    }
    if not all(checks.values()): raise SystemExit('synthetic contract failed: '+json.dumps(checks))
    result={'scope':'synthetic harness only; no live network, no production changes, and no Android UI frame measurement','production_source_sha256':hashlib.sha256(SRC.read_bytes()).hexdigest(),'dependencies_ms':{'timetable':1000,'grades':3000,'exams':4000,'each_auxiliary_module':2000},'checks':checks,'results':out}
    (BUILD/'results.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print(json.dumps(result,ensure_ascii=False,indent=2))

if __name__=='__main__': main()
