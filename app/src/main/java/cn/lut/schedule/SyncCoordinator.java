package cn.lut.schedule;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceError;
import android.webkit.WebResourceResponse;
import android.webkit.SslErrorHandler;
import android.webkit.CookieManager;
import android.widget.FrameLayout;
import org.json.JSONArray;
import org.json.JSONObject;
import java.security.MessageDigest;
import java.util.Locale;

/** Sequential, read-only sync. Each valid module persists independently; the check requires all three. */
final class SyncCoordinator {
    interface Callback{void finished(ScheduleCore.Status status,String detail);}
    private final Context context;private final FrameLayout host;private final SiteGateway course;private final SharedPreferences prefs;
    private final Handler handler=new Handler();private WebView web;private int generation,stage,document;private boolean finished,clicked,reading;
    private boolean timetableOk,gradesOk,examsOk;private StringBuilder details;private Callback callback;private ScheduleCore.Status failure;
    private static final String[] READ_ONLY={"体测成绩管理","课程查询","学分收费管理","学业完成查询","成绩认定"};
    SyncCoordinator(Context context,FrameLayout host,SiteGateway course){this.context=context;this.host=host;this.course=course;prefs=context.getSharedPreferences("settings",0);}
    void start(Callback cb){close();if(!AccessMode.official(context)){cb.finished(ScheduleCore.Status.IDLE,"本地课表，不连接教务系统");return;}finished=false;callback=cb;details=new StringBuilder();failure=ScheduleCore.Status.ADAPTER_PENDING;timetableOk=gradesOk=examsOk=false;int ticket=generation;
        prefs.edit().putLong("last_attempt",System.currentTimeMillis()).apply();
        course.start((status,message,page)->{if(ticket!=generation||finished)return;
            if(!authorized(ticket))return;if(status==ScheduleCore.Status.SUCCESS&&page!=null){try(SnapshotStore cache=new SnapshotStore(context)){
                String monday=prefs.getString("first_monday","");String term=prefs.getString("calendar_semester","");if(!term.isEmpty()&&!page.optString("semester").isEmpty()&&!term.equals(page.optString("semester")))monday="";
                ScheduleCore.Snapshot snapshot=CourseReader.decode(page,monday,1,System.currentTimeMillis());cache.save(snapshot);timetableOk=true;prefs.edit().putLong("timetable_updated",snapshot.fetchedAt).apply();
            }catch(Exception e){details.append("课表：校验未完成，保留缓存\n");}}
            else{failure=status;details.append("课表：").append(message).append('\n');}
            if(status==ScheduleCore.Status.LOGIN_REQUIRED){finish(ticket);return;}stage=0;openModule(ticket);
        });
    }
    private String kind(){return stage==0?"grades":stage==1?"exams":"service:"+service();}
    private String service(){return stage==0?"成绩查询":stage==1?"我的考试安排":READ_ONLY[stage-2];}
    private void openModule(int ticket){if(!authorized(ticket))return;destroyWeb();clicked=reading=false;int currentStage=stage;
        try{web=new WebView(context);SiteGateway.configure(web);host.addView(web,new FrameLayout.LayoutParams(context.getResources().getDisplayMetrics().widthPixels,context.getResources().getDisplayMetrics().heightPixels));}
        catch(RuntimeException|LinkageError e){moduleFailed(ticket,"系统网页组件不可用");return;}
        WebView current=web;String route=AcademicCatalog.route(prefs,service());
        current.setWebViewClient(new WebViewClient(){
            public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest request){if(v!=web)return true;if(SiteGateway.allowed(request.getUrl().toString()))return false;moduleFailed(ticket,"认证地址尚未适配");return true;}
            public void onPageStarted(WebView v,String url,android.graphics.Bitmap icon){if(v!=web)return;document++;reading=false;AcademicReader.capture(v);}
            public void onPageCommitVisible(WebView v,String url){AcademicReader.capture(v);}
            public void onPageFinished(WebView v,String url){if(v==web&&ticket==generation&&!finished){AcademicReader.capture(v);poll(ticket,document,0);}}
            public void onReceivedSslError(WebView v,SslErrorHandler h,android.net.http.SslError e){h.cancel();if(v==web)moduleFailed(ticket,"安全连接验证失败");}
            public void onReceivedError(WebView v,WebResourceRequest r,WebResourceError e){if(v==web&&r.isForMainFrame()){failure=ScheduleCore.Status.UNREACHABLE;moduleFailed(ticket,"无法连接官网");}}
            public void onReceivedHttpError(WebView v,WebResourceRequest r,WebResourceResponse response){if(v==web&&r.isForMainFrame()){failure=response.getStatusCode()>=500?ScheduleCore.Status.SERVER_ERROR:ScheduleCore.Status.UNREACHABLE;moduleFailed(ticket,"HTTP "+response.getStatusCode());}}
            public boolean onRenderProcessGone(WebView v,android.webkit.RenderProcessGoneDetail error){if(v==web)moduleFailed(ticket,"网页组件停止运行");return true;}
        });
        handler.postDelayed(()->{if(ticket==generation&&stage==currentStage&&web==current)moduleFailed(ticket,"读取超时，保留缓存");},35000);
        current.loadUrl(SiteGateway.savedPageUrl(route).isEmpty()?SiteGateway.HOME:route);
    }
    private void poll(int ticket,int doc,int attempt){if(!authorized(ticket)||doc!=document||web==null||reading)return;reading=true;WebView current=web;String module=kind();
        AcademicReader.read(current,module,(page,error)->{if(!authorized(ticket)||current!=web||doc!=document)return;reading=false;
            if(page!=null){rememberLinks(context,page);if(page.optBoolean("login")){failure=ScheduleCore.Status.LOGIN_REQUIRED;details.append(service()).append("：登录已失效\n");finish(ticket);return;}
                if(page.optBoolean("complete")){try(AcademicStore data=new AcademicStore(context)){data.save(module,page,scope(page),System.currentTimeMillis());if(stage==0)gradesOk=true;else if(stage==1)examsOk=true;prefs.edit().putLong(module+"_updated",System.currentTimeMillis()).apply();next(ticket);return;}catch(Exception e){moduleFailed(ticket,"结果校验失败，保留缓存");return;}}
                if(!clicked&&page.optBoolean("portal")){String route=AcademicCatalog.route(prefs,service());if(SiteGateway.allowed(route)&&!route.equals(current.getUrl())){clicked=true;current.loadUrl(route);return;}
                    clicked=true;clickService(current,service());}
            }
            if(attempt<10)handler.postDelayed(()->poll(ticket,doc,attempt+1),700);else moduleFailed(ticket,page==null?"读取失败":page.optString("reason","尚未识别页面"));
        });
    }
    static void clickService(WebView view,String name){String script="(function(n){var a=document.querySelectorAll('a,button,[role=button],[onclick],[class*=app]');for(var i=0;i<a.length;i++){var t=(a[i].innerText||a[i].title||'').replace(/\\s+/g,'').trim();if(t===n){a[i].click();return true;}}return false;})('"+name.replace("'","")+"')";view.evaluateJavascript(script,null);}
    static void rememberLinks(Context context,JSONObject page){JSONArray links=page.optJSONArray("links");if(links==null)return;SharedPreferences.Editor edit=context.getSharedPreferences("settings",0).edit();
        for(int i=0;i<links.length();i++){JSONObject link=links.optJSONObject(i);if(link==null)continue;String title=link.optString("title").replaceAll("\\s+",""),url=SiteGateway.savedPageUrl(link.optString("url"));if(url.isEmpty())continue;for(String[] group:AcademicCatalog.GROUPS)for(String label:group)if(title.equals(label.replaceAll("\\s+","")))edit.putString("service_"+label,url);}
        edit.apply();
    }
    static String scope(JSONObject page){String account=page.optString("account");String value=account.isEmpty()?"session:"+String.valueOf(CookieManager.getInstance().getCookie(SiteGateway.HOME)):"account:"+account;
        try{byte[] bytes=MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));StringBuilder out=new StringBuilder();for(byte b:bytes)out.append(String.format(Locale.ROOT,"%02x",b&255));return out.toString();}catch(Exception e){throw new IllegalStateException(e);}}
    private void moduleFailed(int ticket,String reason){if(ticket!=generation||finished)return;details.append(service()).append("：").append(reason).append('\n');next(ticket);}
    private void next(int ticket){if(ticket!=generation||finished)return;stage++;while(stage>=2&&stage<READ_ONLY.length+2&&AcademicCatalog.route(prefs,service()).isEmpty()){details.append(service()).append("：尚未发现官网链接\n");stage++;}if(stage>=READ_ONLY.length+2)finish(ticket);else openModule(ticket);}
    private boolean authorized(int ticket){if(ticket!=generation||finished)return false;if(AccessMode.official(context))return true;Callback cb=callback;close();if(cb!=null)cb.finished(ScheduleCore.Status.IDLE,"已切换为本地课表，停止官网读取");return false;}
    private void finish(int ticket){if(!authorized(ticket))return;finished=true;boolean ok=timetableOk&&gradesOk&&examsOk;long time=System.currentTimeMillis();if(ok)prefs.edit().putLong("last_full_success",time).apply();String message=ok?"课表、成绩和考试已校验并保存":details.toString();prefs.edit().putString("sync_detail",message).apply();Callback cb=callback;destroyWeb();handler.removeCallbacksAndMessages(null);if(cb!=null)cb.finished(ok?ScheduleCore.Status.SUCCESS:failure,message);}
    private void destroyWeb(){if(web!=null){WebView old=web;web=null;try{old.stopLoading();host.removeView(old);old.destroy();}catch(RuntimeException ignored){}}}
    void close(){generation++;finished=true;callback=null;handler.removeCallbacksAndMessages(null);course.close();destroyWeb();}
}
