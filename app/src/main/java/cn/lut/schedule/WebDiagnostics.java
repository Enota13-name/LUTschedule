package cn.lut.schedule;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Build;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/** Connection metadata only: never copies cookies, page contents or URL parameters. */
final class WebDiagnostics {
    static String provider(){try{PackageInfo p=WebView.getCurrentWebViewPackage();return p==null?"未找到系统网页组件":p.packageName+" / "+p.versionName;}catch(RuntimeException|LinkageError e){return "无法读取组件版本";}}
    static String networkReason(int code){switch(code){
        case WebViewClient.ERROR_HOST_LOOKUP:return "域名解析失败";
        case WebViewClient.ERROR_CONNECT:return "无法建立连接";
        case WebViewClient.ERROR_TIMEOUT:return "连接超时";
        case WebViewClient.ERROR_FAILED_SSL_HANDSHAKE:return "HTTPS 握手失败";
        case WebViewClient.ERROR_REDIRECT_LOOP:return "登录跳转循环";
        case WebViewClient.ERROR_UNSUPPORTED_SCHEME:return "网页使用了未支持的地址类型";
        default:return "网页加载失败";
    }}
    static String record(Context c,String stage,String url,String reason,int code){
        String origin="未知";try{Uri u=Uri.parse(url==null?"":url);if(u.getHost()!=null)origin=u.getScheme()+"://"+u.getHost()+(u.getPort()==-1?"":":"+u.getPort());}catch(RuntimeException ignored){}
        String report="LUT 官网连接元数据\n阶段："+ReportRedactor.clean(stage)+"\n目标主机："+origin+"\n原因："+ReportRedactor.clean(reason)+"\n错误代码："+code+"\nWebView："+provider()+"\nAndroid："+Build.VERSION.RELEASE+" / API "+Build.VERSION.SDK_INT+"\n记录时间："+java.time.Instant.now().atZone(java.time.ZoneId.of("Asia/Shanghai"))+"\n不含账号、密码、Cookie、网址参数或页面内容。";
        c.getSharedPreferences("diagnostics",Context.MODE_PRIVATE).edit().putString("web",report).apply();if(!stage.equals("页面载入"))Diagnostics.event(c,stage,reason+" (code="+code+", host="+origin+")");return report;
    }
    static String last(Context c){return c.getSharedPreferences("diagnostics",Context.MODE_PRIVATE).getString("web","");}
}
