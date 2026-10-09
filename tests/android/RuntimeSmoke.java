package cn.lut.schedule.tests;
import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.view.ViewGroup;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.io.File;
import java.io.FileOutputStream;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import org.json.JSONObject;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.lang.reflect.Proxy;

public final class RuntimeSmoke extends Instrumentation {
    private boolean academicOnly,designOnly,v017,v018,ui018;
    public void onCreate(Bundle args){super.onCreate(args);ui018=args!=null&&"v018-ui".equals(args.getString("suite"));academicOnly=args!=null&&"academic".equals(args.getString("suite"));v018=args!=null&&"v018".equals(args.getString("suite"));v017=v018||args!=null&&"v017".equals(args.getString("suite"));designOnly=ui018||v017||args!=null&&"v016".equals(args.getString("suite"));start();}
    private Object field(Activity a,String name)throws Exception{Field f=a.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(a);}
    public void onStart(){Bundle result=new Bundle();try{
        getTargetContext().getSharedPreferences("settings",0).edit().putString("theme","light").commit();
        if(designOnly){new File(getTargetContext().getFilesDir(),"background.jpg").delete();getTargetContext().getSharedPreferences("diagnostics",0).edit().clear().commit();getTargetContext().getSharedPreferences("settings",0).edit().putString("background_layout","shared").putInt("veil",25).putString("course_colors","varied").putString("week_side","right").putInt("academic_category",0).commit();getTargetContext().getSharedPreferences("settings",0).edit().putString("handedness","right").putBoolean("demo",false).putBoolean("idle_refresh",true).commit();getTargetContext().getSharedPreferences("notification_seen",0).edit().clear().commit();((android.app.NotificationManager)getTargetContext().getSystemService(android.content.Context.NOTIFICATION_SERVICE)).cancelAll();}
        if(academicOnly)getTargetContext().getSharedPreferences("settings",0).edit().putBoolean("demo",false).putInt("academic_category",0).commit();
        Intent launch=new Intent();launch.setClassName("cn.lut.schedule","cn.lut.schedule.MainActivity");launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Activity activity=startActivitySync(launch);waitForIdleSync();
        if(field(activity,"pages")==null)throw new AssertionError("No native timetable page: "+getTargetContext().getSharedPreferences("diagnostics",0).getString("last",""));
        if(containsText((ViewGroup)field(activity,"pages"),"这一周的课") || containsText((ViewGroup)field(activity,"pages"),"课间 · LUT"))throw new AssertionError("Removed home headings still visible");
        Thread.sleep(1500);waitForIdleSync();
        if(ui018){result.putString("result",new RuntimeV018(this).run(activity));finish(Activity.RESULT_OK,result);return;}if(designOnly){String extra=v017?new RuntimeV017(this).run(activity)+"\n":"";result.putString("result",extra+(v018?new RuntimeV018(this).run(activity)+"\n":"")+new RuntimeV016(this).run(activity));finish(Activity.RESULT_OK,result);return;}
        if(academicOnly){academicChecks(activity);result.putString("result","PASS: academic menu visible outside demo; four official categories and 14 teacher/student services; native text-only details and unconfirmed categories; selected category persistence; my timetable navigation; dark recreation; cached data unchanged");finish(Activity.RESULT_OK,result);return;}
        readerChecks(activity);emapChecks(activity);loginConnectionChecks(activity);
        final ViewGroup nav=(ViewGroup)field(activity,"nav");
        runOnMainSync(()->nav.getChildAt(1).performClick());waitForIdleSync();
        runOnMainSync(()->nav.getChildAt(2).performClick());waitForIdleSync();
        runOnMainSync(()->nav.getChildAt(0).performClick());waitForIdleSync();
        Method demo=activity.getClass().getDeclaredMethod("enableDemo",boolean.class);demo.setAccessible(true);
        runOnMainSync(()->{try{demo.invoke(activity,true);}catch(Exception e){throw new RuntimeException(e);}});waitForIdleSync();
        if(field(activity,"snapshot")==null)throw new AssertionError("Demo failed to load");
        capture("demo-light.png");
        ActivityMonitor monitor=new ActivityMonitor("cn.lut.schedule.MainActivity",null,false);addMonitor(monitor);
        runOnMainSync(()->{activity.getSharedPreferences("settings",0).edit().putString("theme","dark").commit();activity.recreate();});
        Activity recreated=waitForMonitorWithTimeout(monitor,8000);removeMonitor(monitor);waitForIdleSync();
        if(recreated==null || field(recreated,"pages")==null || !Boolean.TRUE.equals(field(recreated,"dark")))throw new AssertionError("Dark-theme recreation failed");
        capture("demo-dark.png");
        result.putString("result","PASS: login connection error UI, provider/version and sanitized diagnostics; main-frame load/HTTP/blocked-host callbacks; subresource and stale callback isolation; failed pages skip reader; EMAP and DOM fixtures; SQLite/cache preservation; startup, tabs, removed headings, dark recreation");finish(Activity.RESULT_OK,result);
    }catch(Throwable error){result.putString("error",android.util.Log.getStackTraceString(error));finish(Activity.RESULT_CANCELED,result);}}
    private void capture(String name)throws Exception{waitForIdleSync();Thread.sleep(250);android.graphics.Bitmap bitmap=getUiAutomation().takeScreenshot();
        if(bitmap==null)throw new AssertionError("Screenshot unavailable");try(FileOutputStream out=new FileOutputStream(new File(getTargetContext().getExternalFilesDir(null),name))){bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();}
    private boolean containsText(ViewGroup group,String value){for(int i=0;i<group.getChildCount();i++){android.view.View v=group.getChildAt(i);if(v instanceof android.widget.TextView&&value.equals(((android.widget.TextView)v).getText().toString()))return true;if(v instanceof ViewGroup&&containsText((ViewGroup)v,value))return true;}return false;}
    private JSONObject readFixture(Activity activity,String html)throws Exception{return readFixture(activity,html,"https://jwxt.lut.edu.cn/test-fixture");}
    private JSONObject readFixture(Activity activity,String html,String fixtureUrl)throws Exception{
        Class<?> reader=Class.forName("cn.lut.schedule.CourseReader"),callback=Class.forName("cn.lut.schedule.CourseReader$Callback");
        Method read=reader.getDeclaredMethod("read",WebView.class,callback);read.setAccessible(true);
        CountDownLatch latch=new CountDownLatch(1);final JSONObject[] answer={null};final Throwable[] problem={null};final WebView[] web={null};
        Object proxy=Proxy.newProxyInstance(callback.getClassLoader(),new Class<?>[]{callback},(p,m,a)->{if(m.getName().equals("result")){answer[0]=(JSONObject)a[0];problem[0]=(Throwable)a[1];latch.countDown();}return null;});
        runOnMainSync(()->{web[0]=new WebView(activity);web[0].getSettings().setJavaScriptEnabled(true);
            ((ViewGroup)activity.getWindow().getDecorView()).addView(web[0],new ViewGroup.LayoutParams(1,1));
            web[0].setWebViewClient(new WebViewClient(){public void onPageFinished(WebView v,String url){try{read.invoke(null,v,proxy);}catch(Exception e){problem[0]=e;latch.countDown();}}});
            web[0].loadDataWithBaseURL(fixtureUrl,html,"text/html","UTF-8",null);
        });
        if(!latch.await(25,TimeUnit.SECONDS))throw new AssertionError("Reader fixture timed out");
        runOnMainSync(()->{((ViewGroup)web[0].getParent()).removeView(web[0]);web[0].destroy();});
        if(problem[0]!=null)throw new AssertionError(problem[0]);return answer[0];
    }
    private void readerChecks(Activity activity)throws Exception{
        String json="{\"semester\":\"测试学期\",\"firstMonday\":\"2026-09-07\",\"totalWeeks\":20,\"courses\":[{\"name\":\"测试数学\",\"day\":1,\"rawStart\":1,\"rawEnd\":2,\"weeks\":[1,3,5]},{\"name\":\"测试晚课\",\"day\":7,\"rawStart\":11,\"rawEnd\":12,\"weeks\":[2,4,6]}]}";
        JSONObject structured=readFixture(activity,"<html><body><script type='application/json'>"+json+"</script></body></html>");
        if(!structured.optBoolean("complete")||structured.getJSONArray("courses").length()!=2)throw new AssertionError("Structured course fixture failed: "+structured);
        String header="<table><tr><th>课程名称</th><th>星期</th><th>节次</th><th>周次</th><th>教室</th><th>教师</th></tr>";
        String good="<tr><td>列表测试课</td><td>星期一</td><td>1-2节</td><td>1-16周（单）</td><td>A101</td><td>测试教师</td></tr>";
        JSONObject list=readFixture(activity,header+good+"</table>");
        if(!list.optBoolean("complete")||list.getJSONArray("courses").getJSONObject(0).getJSONArray("weeks").length()!=8)throw new AssertionError("List/odd-week fixture failed: "+list);
        JSONObject partial=readFixture(activity,header+good+"<tr><td>缺周次课程</td><td>周二</td><td>3-4节</td><td></td><td></td><td></td></tr></table>");
        if(partial.optBoolean("complete"))throw new AssertionError("Partial data accepted as complete");
        JSONObject paged=readFixture(activity,header+good+"</table><div class='pagination'>下一页</div>");
        if(paged.optBoolean("complete"))throw new AssertionError("Paginated page accepted as entire term");
        JSONObject login=readFixture(activity,"<input type='password'><script type='application/json'>"+json+"</script>");
        if(!login.optBoolean("login")||login.optBoolean("complete"))throw new AssertionError("Login form mistaken for timetable");
        String grid="<table><tr><th>节次</th><th>周一</th><th>周二</th><th>周三</th><th>周四</th><th>周五</th><th>周六</th><th>周日</th></tr><tr><td>1</td><td rowspan='2'>大学物理<br>1-16周（双）<br>教室：A101<br>教师：测试教师</td><td></td><td></td><td></td><td></td><td></td><td></td></tr><tr><td>2</td><td></td><td></td><td></td><td></td><td></td><td></td></tr></table>";
        JSONObject gridResult=readFixture(activity,grid);
        if(!gridResult.optBoolean("complete")||gridResult.getJSONArray("courses").getJSONObject(0).getInt("rawEnd")!=2)throw new AssertionError("Rowspan grid fixture failed: "+gridResult);
        JSONObject missingEnd=readFixture(activity,grid.replace("<tr><td>2</td>","<tr><td>未知</td>"));
        if(missingEnd.optBoolean("complete"))throw new AssertionError("Merged course silently truncated");
        Method save=activity.getClass().getDeclaredMethod("saveReadPage",JSONObject.class);save.setAccessible(true);
        runOnMainSync(()->{try{save.invoke(activity,structured);}catch(Exception e){throw new RuntimeException(e);}});waitForIdleSync();Object saved=field(activity,"snapshot");
        Field courses=saved.getClass().getDeclaredField("courses");courses.setAccessible(true);java.util.List<?> normalized=(java.util.List<?>)courses.get(saved);
        Object late=normalized.get(1);Field start=late.getClass().getDeclaredField("start");start.setAccessible(true);
        if(start.getInt(late)!=11)throw new AssertionError("Twelfth period dropped rather than retained");
        Object store=field(activity,"store");Class<?> source=Class.forName("cn.lut.schedule.ScheduleCore$Source");Object official=java.lang.Enum.valueOf((Class)source,"OFFICIAL");
        Method load=store.getClass().getDeclaredMethod("load",source);load.setAccessible(true);if(load.invoke(store,official)==null)throw new AssertionError("Official snapshot not persisted");
        runOnMainSync(()->{try{save.invoke(activity,partial);}catch(Exception e){throw new RuntimeException(e);}});waitForIdleSync();
        if(field(activity,"snapshot")!=saved)throw new AssertionError("Failed read replaced cached snapshot");
    }
    private JSONObject emapFixture(Activity activity,String mode)throws Exception{
        String script="window.fetch=function(url,options){var p=null,status=200;"+
            "if(options.method!=='POST'||options.credentials!=='same-origin')throw Error('Fixture: session/verb mismatch');"+
            "if(url.endsWith('/dqxnxq.do')){p={code:'0',datas:{dqxnxq:{extParams:{code:1},rows:[{DM:'2026-2027-1',MC:'接口测试学期'}]}}};if('"+mode+"'==='login')status=401;if('"+mode+"'==='server')status=503;}"+
            "else if(url.endsWith('/xskcb.do')){var q=new URLSearchParams(options.body);if(q.get('XNXQDM')!=='2026-2027-1'||q.get('pageSize')!=='1000')throw Error('Fixture: term/all rows mismatch');"+
            "var rows=[{KCM:'接口数学',SKJS:'测试教师',JASMC:'A101',SKXQ:1,KSJC:1,JSJC:2,SKZC:'10101',XNXQDM:'2026-2027-1'},{KCM:'接口晚课',SKXQ:7,KSJC:11,JSJC:12,SKZC:'010101',XNXQDM:'2026-2027-1'}];"+
            "if('"+mode+"'==='wrongTerm')rows[0].XNXQDM='2025-2026-1';if('"+mode+"'==='missing')rows[0].SKZC=null;"+
            "p={code:'0',datas:{xskcb:{extParams:{code:1},totalSize:'"+mode+"'==='partial'?3:2,pageSize:1000,rows:rows}}};if('"+mode+"'==='error')p.datas.xskcb.extParams.code=0;}"+
            "else if(url.endsWith('/cxjcs.do')){var q=new URLSearchParams(options.body);if(q.get('XN')!=='2026-2027'||q.get('XQ')!=='1')throw Error('Fixture: calendar mismatch');p={code:'0',datas:{cxjcs:{extParams:{code:1},rows:[{XQKSRQ:'2026-09-07'}]}}};if('"+mode+"'==='noCalendar')status=404;}"+
            "else throw Error('Fixture: unexpected endpoint');return Promise.resolve(new Response(JSON.stringify(p),{status:status}));};";
        return readFixture(activity,"<html><body>动态课表<div class='pagination'>网页周视图分页</div><script>"+script+"</script></body></html>","https://jwxt.lut.edu.cn/jwapp/sys/wdkb/*default/index.do?EMAP_LANG=zh#/xskcb");
    }
    private void emapChecks(Activity activity)throws Exception{
        Class<?> gateway=Class.forName("cn.lut.schedule.SiteGateway");Field route=gateway.getDeclaredField("TIMETABLE");route.setAccessible(true);
        if(!"https://jwxt.lut.edu.cn/jwapp/sys/wdkb/*default/index.do?EMAP_LANG=zh#/xskcb".equals(route.get(null)))throw new AssertionError("Timetable route/hash lost");
        JSONObject good=emapFixture(activity,"good");
        if(!good.optBoolean("complete")||!"emap".equals(good.optString("adapter"))||!"2026-09-07".equals(good.optString("firstMonday"))||good.getJSONArray("courses").getJSONObject(0).getJSONArray("weeks").length()!=3)throw new AssertionError("EMAP fixtures failed: "+good);
        if(!"测试教师".equals(good.getJSONArray("courses").getJSONObject(0).optString("teacher")))throw new AssertionError("EMAP SKJS field missed");
        for(String mode:new String[]{"partial","wrongTerm","missing","error"}){JSONObject bad=emapFixture(activity,mode);if(bad.optBoolean("complete"))throw new AssertionError("EMAP accepted "+mode+": "+bad);}
        JSONObject login=emapFixture(activity,"login");if(!login.optBoolean("login")||login.optBoolean("complete"))throw new AssertionError("EMAP login status missed");
        JSONObject server=emapFixture(activity,"server");if(server.optInt("http")!=503||server.optBoolean("complete"))throw new AssertionError("EMAP server failure missed");
        Method failure=gateway.getDeclaredMethod("readFailure",JSONObject.class);failure.setAccessible(true);
        if(!"SERVER_ERROR".equals(failure.invoke(null,server).toString())||!"LOGIN_REQUIRED".equals(failure.invoke(null,login).toString()))throw new AssertionError("Native failure status classification incorrect");
        JSONObject noCalendar=emapFixture(activity,"noCalendar");if(!noCalendar.optBoolean("complete")||!noCalendar.optString("firstMonday").isEmpty())throw new AssertionError("Calendar failure guessed a date");
        Method save=activity.getClass().getDeclaredMethod("saveReadPage",JSONObject.class);save.setAccessible(true);
        runOnMainSync(()->{try{save.invoke(activity,good);}catch(Exception e){throw new RuntimeException(e);}});waitForIdleSync();
        Object cached=field(activity,"snapshot");JSONObject partial=emapFixture(activity,"partial");
        runOnMainSync(()->{try{save.invoke(activity,partial);}catch(Exception e){throw new RuntimeException(e);}});waitForIdleSync();
        if(field(activity,"snapshot")!=cached)throw new AssertionError("EMAP failure changed cache");
    }
    private android.webkit.WebResourceRequest request(String url,boolean main){return (android.webkit.WebResourceRequest)Proxy.newProxyInstance(android.webkit.WebResourceRequest.class.getClassLoader(),new Class<?>[]{android.webkit.WebResourceRequest.class},(p,m,a)->{
        switch(m.getName()){case "getUrl":return android.net.Uri.parse(url);case "isForMainFrame":return main;case "isRedirect":case "hasGesture":return false;case "getMethod":return "GET";case "getRequestHeaders":return java.util.Collections.emptyMap();default:return null;}
    });}
    private android.webkit.WebResourceError errorFixture(Activity activity)throws Exception{
        CountDownLatch latch=new CountDownLatch(1);final WebView[] browser={null};final android.webkit.WebResourceError[] observed={null};
        runOnMainSync(()->{browser[0]=new WebView(activity);((ViewGroup)activity.getWindow().getDecorView()).addView(browser[0],new ViewGroup.LayoutParams(1,1));browser[0].setWebViewClient(new WebViewClient(){public void onReceivedError(WebView v,android.webkit.WebResourceRequest r,android.webkit.WebResourceError e){if(r.isForMainFrame()){observed[0]=e;latch.countDown();}}});browser[0].loadUrl("file:///lut-missing-test-fixture/nonexistent.html");});
        boolean ready=latch.await(6,TimeUnit.SECONDS);runOnMainSync(()->{((ViewGroup)browser[0].getParent()).removeView(browser[0]);browser[0].destroy();});
        if(!ready||observed[0]==null)throw new AssertionError("No framework load error for fixture");return observed[0];
    }
    private void loginConnectionChecks(Activity activity)throws Exception{
        Object cached=field(activity,"snapshot");Method open=activity.getClass().getDeclaredMethod("showLogin");open.setAccessible(true);
        runOnMainSync(()->{try{open.invoke(activity);}catch(Exception e){throw new RuntimeException(e);}});waitForIdleSync();
        WebView web=(WebView)field(activity,"loginWeb");if(web==null)throw new AssertionError("Login WebView missing");
        runOnMainSync(()->{web.stopLoading();web.loadDataWithBaseURL("https://jwxt.lut.edu.cn/","<html><body><input type='password'>模拟官网登录页</body></html>","text/html","UTF-8",null);});
        Thread.sleep(1000);waitForIdleSync();final android.webkit.WebViewClient[] clients={null};runOnMainSync(()->clients[0]=web.getWebViewClient());android.webkit.WebViewClient client=clients[0];
        android.webkit.WebResourceError tls=errorFixture(activity);
        runOnMainSync(()->{client.onReceivedError(web,request("https://jwxt.lut.edu.cn/private-student-path?ticket=fixture-secret",true),tls);client.onPageFinished(web,"https://jwxt.lut.edu.cn/");});waitForIdleSync();
        if(!Boolean.TRUE.equals(field(activity,"loginPageFailed")))throw new AssertionError("Failed page treated as ready");
        String report=activity.getSharedPreferences("diagnostics",0).getString("web","");
        if(!report.contains("错误代码："+tls.getErrorCode())||!report.contains("WebView：")||report.contains("fixture-secret")||report.contains("private-student-path"))throw new AssertionError("Connection report missing metadata or contains sensitive URL");
        if(!((android.widget.TextView)field(activity,"loginConnectionStatus")).getText().toString().contains("点此复制详情"))throw new AssertionError("Connection error not visible");
        capture("login-connection.png");
        runOnMainSync(()->client.onPageStarted(web,"https://jwxt.lut.edu.cn/",null));
        runOnMainSync(()->client.onReceivedError(web,request("https://res.lut.edu.cn/image.png",false),tls));waitForIdleSync();
        if(Boolean.TRUE.equals(field(activity,"loginPageFailed")))throw new AssertionError("Subresource failure hides whole login page");
        android.webkit.WebResourceResponse unavailable=new android.webkit.WebResourceResponse("text/html","UTF-8",503,"Service unavailable",java.util.Collections.emptyMap(),new java.io.ByteArrayInputStream(new byte[0]));
        runOnMainSync(()->client.onReceivedHttpError(web,request("https://jwxt.lut.edu.cn/",true),unavailable));waitForIdleSync();
        if(!activity.getSharedPreferences("diagnostics",0).getString("web","").contains("HTTP 503"))throw new AssertionError("HTTP error missed");
        runOnMainSync(()->client.shouldOverrideUrlLoading(web,request("https://auth.example.test/login?ticket=fixture-secret",true)));waitForIdleSync();
        if(!activity.getSharedPreferences("diagnostics",0).getString("web","").contains("auth.example.test"))throw new AssertionError("Blocked auth host not visible");
        if(android.webkit.WebView.getCurrentWebViewPackage()==null)throw new AssertionError("System WebView provider missing in test device");
        runOnMainSync(()->{try{((android.app.Dialog)field(activity,"loginDialog")).dismiss();}catch(Exception e){throw new RuntimeException(e);}});waitForIdleSync();
        String before=activity.getSharedPreferences("diagnostics",0).getString("web","");runOnMainSync(()->client.onReceivedError(web,request("https://jwxt.lut.edu.cn/",true),tls));
        if(!before.equals(activity.getSharedPreferences("diagnostics",0).getString("web","")))throw new AssertionError("Stale closed WebView callback changed report");
        if(field(activity,"snapshot")!=cached)throw new AssertionError("Login failure changed cached timetable");
    }
    private android.view.View described(android.view.View view,String value){if(value.equals(String.valueOf(view.getContentDescription())))return view;if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){android.view.View hit=described(group.getChildAt(i),value);if(hit!=null)return hit;}}return null;}
    private boolean containsWebView(android.view.View view){if(view instanceof WebView)return true;if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)if(containsWebView(group.getChildAt(i)))return true;}return false;}
    private void scrollViews(android.view.View view,java.util.List<android.widget.ScrollView> out){if(view instanceof android.widget.ScrollView)out.add((android.widget.ScrollView)view);if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)scrollViews(group.getChildAt(i),out);}}
    private void stopTestNetwork(Activity activity)throws Exception{Object gateway=field(activity,"gateway");Method close=gateway.getClass().getDeclaredMethod("close");close.setAccessible(true);runOnMainSync(()->{try{close.invoke(gateway);((android.os.Handler)field(activity,"handler")).removeCallbacksAndMessages(null);android.app.Dialog login=(android.app.Dialog)field(activity,"loginDialog");if(login!=null)login.dismiss();}catch(Exception e){throw new RuntimeException(e);}});}
    private void academicChecks(Activity activity)throws Exception{
        stopTestNetwork(activity);Object cached=field(activity,"snapshot");ViewGroup nav=(ViewGroup)field(activity,"nav");runOnMainSync(()->nav.getChildAt(1).performClick());waitForIdleSync();
        ViewGroup content=(ViewGroup)field(activity,"pages");
        if(!containsText(content,"14 项服务")||!containsText(content,"成绩查询")||containsText(content,"等待官网菜单接入")||containsWebView(content))throw new AssertionError("Native official menu missing outside demo");
        if(activity.getSharedPreferences("settings",0).getBoolean("demo",false))throw new AssertionError("Academic page changed schedule into demo mode");
        capture("academic-light.png");
        android.view.View grades=described(content,"成绩查询");runOnMainSync(()->grades.performClick());waitForIdleSync();Thread.sleep(200);
        android.view.accessibility.AccessibilityNodeInfo dialog=getUiAutomation().getRootInActiveWindow();
        if(dialog==null||dialog.findAccessibilityNodeInfosByText("该服务的个人数据暂未接入").isEmpty())throw new AssertionError("Service detail pretends live data exists");
        java.util.List<android.view.accessibility.AccessibilityNodeInfo> buttons=dialog.findAccessibilityNodeInfosByText("返回目录");if(buttons.isEmpty())throw new AssertionError("No service return button");buttons.get(0).performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK);waitForIdleSync();
        for(String category:new String[]{"学籍管理","创新创业","实践教学"}){android.view.View categoryView=described(content,"教务分类："+category);if(categoryView==null)throw new AssertionError("Official category absent: "+category);runOnMainSync(()->categoryView.performClick());waitForIdleSync();if(!containsText(content,"暂未取得此分类的服务目录")||containsText(content,"成绩查询"))throw new AssertionError("Unseen category contains invented services");}
        runOnMainSync(()->nav.getChildAt(2).performClick());waitForIdleSync();runOnMainSync(()->nav.getChildAt(1).performClick());waitForIdleSync();
        content=(ViewGroup)field(activity,"pages");android.view.View persisted=described(content,"教务分类：实践教学");if(persisted==null||!persisted.isSelected())throw new AssertionError("Category selection lost during navigation");
        android.view.View teacher=described(content,"教务分类：师生服务");runOnMainSync(()->teacher.performClick());waitForIdleSync();
        java.util.List<android.widget.ScrollView> scrolls=new java.util.ArrayList<>();scrollViews(content,scrolls);if(scrolls.size()!=2)throw new AssertionError("Academic page not two independent text columns");runOnMainSync(()->scrolls.get(1).fullScroll(android.view.View.FOCUS_DOWN));waitForIdleSync();capture("academic-bottom.png");
        android.view.View timetable=described(content,"我的课表");if(timetable==null)throw new AssertionError("My timetable service missing");runOnMainSync(()->timetable.performClick());waitForIdleSync();if(((Integer)field(activity,"page"))!=0||field(activity,"snapshot")!=cached)throw new AssertionError("My timetable does not return to own cached timetable");
        runOnMainSync(()->nav.getChildAt(1).performClick());waitForIdleSync();ActivityMonitor monitor=new ActivityMonitor("cn.lut.schedule.MainActivity",null,false);addMonitor(monitor);
        runOnMainSync(()->{activity.getSharedPreferences("settings",0).edit().putString("theme","dark").commit();activity.recreate();});Activity recreated=waitForMonitorWithTimeout(monitor,8000);removeMonitor(monitor);waitForIdleSync();if(recreated==null)throw new AssertionError("Theme recreation failed");stopTestNetwork(recreated);
        ViewGroup recreatedNav=(ViewGroup)field(recreated,"nav");runOnMainSync(()->recreatedNav.getChildAt(1).performClick());waitForIdleSync();if(!containsText((ViewGroup)field(recreated,"pages"),"14 项服务")||!Boolean.TRUE.equals(field(recreated,"dark")))throw new AssertionError("Menu missing after dark recreation");capture("academic-dark.png");
    }
}
