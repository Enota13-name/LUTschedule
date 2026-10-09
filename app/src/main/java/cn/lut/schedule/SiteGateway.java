package cn.lut.schedule;

import android.content.Context;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Handler;
import android.webkit.CookieManager;
import android.webkit.SslErrorHandler;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import org.json.JSONArray;
import org.json.JSONObject;

/** Reuse the official session and read the previously captured timetable page. */
final class SiteGateway {
    static final String HOME="https://jwxt.lut.edu.cn/";
    static final String TIMETABLE="https://jwxt.lut.edu.cn/jwapp/sys/wdkb/*default/index.do?EMAP_LANG=zh#/xskcb";
    static ScheduleCore.Status readFailure(JSONObject page){return page!=null&&page.optBoolean("login")?ScheduleCore.Status.LOGIN_REQUIRED:page!=null&&page.optInt("http")>=500?ScheduleCore.Status.SERVER_ERROR:page!=null&&page.optBoolean("networkError")?ScheduleCore.Status.UNREACHABLE:ScheduleCore.Status.PARSE_ERROR;}
    interface Callback { void finished(ScheduleCore.Status status,String detail,JSONObject page); }
    private final Context activity;
    private final FrameLayout host;
    private final Handler handler=new Handler();
    private WebView probe;
    private int generation,documentGeneration;
    private boolean done,navigated;
    SiteGateway(Context activity,FrameLayout host){this.activity=activity;this.host=host;}
    static boolean allowed(String url){
        if(url==null)return false;Uri u=Uri.parse(url);String h=u.getHost();
        return "https".equalsIgnoreCase(u.getScheme()) && h!=null && (h.equalsIgnoreCase("lut.edu.cn") || h.toLowerCase(java.util.Locale.ROOT).endsWith(".lut.edu.cn"))
            && (u.getPort()==-1 || u.getPort()==443) && u.getUserInfo()==null;
    }
    static String savedPageUrl(String url){
        if(!allowed(url))return "";
        if(url.matches("(?i).*[?&#;](ticket|token|access_token|code|session|jsessionid|password|pwd|auth)=.*"))return "";
        return url;
    }
    static void configure(WebView view){
        view.getSettings().setJavaScriptEnabled(true);view.getSettings().setDomStorageEnabled(true);
        view.getSettings().setJavaScriptCanOpenWindowsAutomatically(true);
        view.getSettings().setUseWideViewPort(true);view.getSettings().setLoadWithOverviewMode(true);
        view.getSettings().setBuiltInZoomControls(true);view.getSettings().setDisplayZoomControls(false);
        view.getSettings().setAllowFileAccess(false);view.getSettings().setAllowContentAccess(false);
        view.getSettings().setMixedContentMode(android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        view.getSettings().setCacheMode(android.webkit.WebSettings.LOAD_NO_CACHE);
        CookieManager.getInstance().setAcceptCookie(true);
    }
    void start(Callback callback){
        close();done=false;navigated=false;final int ticket=generation;
        try{probe=new WebView(activity);configure(probe);}catch(RuntimeException | LinkageError error){
            Diagnostics.record(activity,"同步网页组件创建",error,false);finish(ticket,callback,ScheduleCore.Status.UNREACHABLE,"系统网页组件暂不可用，已保留本地课表",null);return;}
        android.util.DisplayMetrics metrics=activity.getResources().getDisplayMetrics();
        host.addView(probe,new FrameLayout.LayoutParams(metrics.widthPixels,metrics.heightPixels));
        handler.postDelayed(()->finish(ticket,callback,ScheduleCore.Status.UNREACHABLE,"连接或读取超时，保留缓存；尚不能判断是否为教务系统故障",null),35000);
        probe.setWebViewClient(new WebViewClient(){
            public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){if(allowed(r.getUrl().toString()))return false;
                finish(ticket,callback,ScheduleCore.Status.ADAPTER_PENDING,"认证跳转到了尚未支持的域名",null);return true;}
            public void onPageStarted(WebView v,String url,android.graphics.Bitmap icon){documentGeneration++;}
            public void onReceivedSslError(WebView v,SslErrorHandler h,SslError e){h.cancel();finish(ticket,callback,ScheduleCore.Status.UNREACHABLE,"官网安全连接验证失败",null);}
            public boolean onRenderProcessGone(WebView v,android.webkit.RenderProcessGoneDetail error){finish(ticket,callback,ScheduleCore.Status.UNREACHABLE,"同步网页已停止运行，已保留本地课表",null);return true;}
            public void onReceivedError(WebView v,WebResourceRequest r,WebResourceError e){if(r.isForMainFrame())finish(ticket,callback,ScheduleCore.Status.UNREACHABLE,"暂时无法连接教务系统（网络错误 "+e.getErrorCode()+"）",null);}
            public void onReceivedHttpError(WebView v,WebResourceRequest r,WebResourceResponse response){if(r.isForMainFrame()){int code=response.getStatusCode();
                finish(ticket,callback,code>=500?ScheduleCore.Status.SERVER_ERROR:ScheduleCore.Status.UNREACHABLE,
                    code>=500?"教务系统暂时故障（HTTP "+code+"）":"官网拒绝了当前请求（HTTP "+code+"）",null);}}
            public void onPageFinished(WebView v,String url){if(ticket==generation&&!done&&allowed(url))readPage(ticket,documentGeneration,callback,0);}
        });
        probe.loadUrl(TIMETABLE);
    }
    private void readPage(int ticket,int document,Callback cb,int attempt){
        if(ticket!=generation || document!=documentGeneration || done || probe==null)return;
        CourseReader.read(probe,(page,error)->{
            if(ticket!=generation || document!=documentGeneration || done || probe==null)return;
            if(error!=null){finish(ticket,cb,ScheduleCore.Status.PARSE_ERROR,"课表读取失败，保留缓存",null);return;}
            if(page.optBoolean("login")){finish(ticket,cb,ScheduleCore.Status.LOGIN_REQUIRED,"请在官网完成登录，成功后将自动读取课表",null);return;}
            if(page.optBoolean("complete")){
                try{if(!page.has("readAt"))page.put("readAt",System.currentTimeMillis());if(page.optString("pageUrl").isEmpty())page.put("pageUrl",probe.getUrl());}catch(Exception ignored){}
                CookieManager.getInstance().flush();finish(ticket,cb,ScheduleCore.Status.SUCCESS,"已读取官网课表，正在校验并保存",page);return;
            }
            if("emap".equals(page.optString("adapter"))){
                finish(ticket,cb,readFailure(page),"课表接口读取未完成，保留缓存；请在官网登录窗口重试读取",page);return;
            }
            if(!navigated){JSONArray links=page.optJSONArray("links");if(links!=null)for(int i=0;i<links.length();i++){JSONObject link=links.optJSONObject(i);if(link==null)continue;
                String title=link.optString("title"),url=link.optString("url");
                if(title.matches(".*(个人课表|我的课表|学生课表|学期课表|课表查询).*")){if(allowed(url)&&!url.equals(probe.getUrl())){navigated=true;probe.loadUrl(url);return;}}
            }}
            if(attempt<9){handler.postDelayed(()->readPage(ticket,document,cb,attempt+1),800);return;}
            finish(ticket,cb,ScheduleCore.Status.ADAPTER_PENDING,"未识别到完整学期课表，已保留缓存；可在官网窗口的更多菜单重试采集",page);
        });
    }
    private void finish(int ticket,Callback cb,ScheduleCore.Status status,String detail,JSONObject page){
        if(ticket!=generation || done)return;done=true;
        if(status==ScheduleCore.Status.UNREACHABLE||status==ScheduleCore.Status.SERVER_ERROR||status==ScheduleCore.Status.ADAPTER_PENDING)WebDiagnostics.record(activity,"后台更新连接",probe==null?TIMETABLE:probe.getUrl(),detail,page==null?0:page.optInt("http"));
        close();cb.finished(status,detail,page);
    }
    void close(){
        generation++;documentGeneration++;handler.removeCallbacksAndMessages(null);
        if(probe!=null){WebView old=probe;probe=null;try{old.stopLoading();host.removeView(old);old.destroy();}catch(RuntimeException ignored){}}done=true;
    }
}
