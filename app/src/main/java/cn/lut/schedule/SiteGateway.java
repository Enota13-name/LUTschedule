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
    static final String HOME = "https://jwxt.lut.edu.cn/";
    static final String TIMETABLE = "https://jwxt.lut.edu.cn/jwapp/sys/wdkb/*default/index.do?EMAP_LANG=zh#/xskcb";
    interface Callback { void finished(ScheduleCore.Status status, String detail, JSONObject page); }

    private final Context activity;
    private final FrameLayout host;
    private final Handler handler = new Handler();
    private WebView probe;
    private int generation, documentGeneration;
    private boolean done, navigated;

    SiteGateway(Context activity, FrameLayout host) { this.activity = activity; this.host = host; }

    static ScheduleCore.Status readFailure(JSONObject page) {
        if (page == null) return ScheduleCore.Status.PARSE_ERROR;
        int http = page.optInt("http");
        if (http == 403 || http == 429) return ScheduleCore.Status.UNREACHABLE;
        if (http == 401 || page.optBoolean("login")) return ScheduleCore.Status.LOGIN_REQUIRED;
        if (http >= 500) return ScheduleCore.Status.SERVER_ERROR;
        if (page.optBoolean("networkError")) return ScheduleCore.Status.UNREACHABLE;
        return ScheduleCore.Status.PARSE_ERROR;
    }

    static boolean allowed(String url) {
        if (url == null) return false;
        Uri u = Uri.parse(url);
        String h = u.getHost();
        return "https".equalsIgnoreCase(u.getScheme()) && h != null && (h.equalsIgnoreCase("lut.edu.cn") || h.toLowerCase(java.util.Locale.ROOT).endsWith(".lut.edu.cn"))
                && (u.getPort() == -1 || u.getPort() == 443) && u.getUserInfo() == null;
    }

    static String savedPageUrl(String url) {
        if (!allowed(url)) return "";
        if (url.matches("(?i).*[?&#;](ticket|token|access_token|code|session|jsessionid|password|pwd|auth)=.*")) return "";
        return url;
    }

    static void configure(WebView view) {
        view.getSettings().setJavaScriptEnabled(true); view.getSettings().setDomStorageEnabled(true);
        view.getSettings().setJavaScriptCanOpenWindowsAutomatically(true);
        view.getSettings().setUseWideViewPort(true); view.getSettings().setLoadWithOverviewMode(true);
        view.getSettings().setBuiltInZoomControls(true); view.getSettings().setDisplayZoomControls(false);
        view.getSettings().setAllowFileAccess(false); view.getSettings().setAllowContentAccess(false);
        view.getSettings().setMixedContentMode(android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        view.getSettings().setCacheMode(android.webkit.WebSettings.LOAD_NO_CACHE);
        CookieManager.getInstance().setAcceptCookie(true);
    }

    void start(Callback callback) {
        close(); done = false; navigated = false; final int ticket = generation;
        try { probe = new WebView(activity); configure(probe); }
        catch (RuntimeException | LinkageError error) {
            Diagnostics.record(activity, "同步网页组件创建", error, false);
            finish(ticket, callback, ScheduleCore.Status.UNREACHABLE, "系统网页组件暂不可用，已保留本地课表", metadata("webview", 0, 0, "系统网页组件暂不可用"));
            return;
        }
        android.util.DisplayMetrics metrics = activity.getResources().getDisplayMetrics();
        host.addView(probe, new FrameLayout.LayoutParams(metrics.widthPixels, metrics.heightPixels));
        handler.postDelayed(() -> finish(ticket, callback, ScheduleCore.Status.UNREACHABLE,
                "课表读取超过 35 秒，保留缓存；尚不能判断是否为教务系统故障", metadata("timetable", 0, 0, "读取超时")), 35_000L);
        probe.setWebViewClient(new WebViewClient() {
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                if (allowed(r.getUrl().toString())) return false;
                finish(ticket, callback, ScheduleCore.Status.ADAPTER_PENDING, "认证跳转到了尚未支持的域名", metadata("redirect", 0, 0, "认证跳转到了尚未支持的域名"));
                return true;
            }
            public void onPageStarted(WebView v, String url, android.graphics.Bitmap icon) { documentGeneration++; }
            public void onReceivedSslError(WebView v, SslErrorHandler h, SslError e) {
                h.cancel(); finish(ticket, callback, ScheduleCore.Status.UNREACHABLE, "官网安全连接验证失败", metadata("tls", e.getPrimaryError(), 0, "HTTPS 握手失败"));
            }
            public boolean onRenderProcessGone(WebView v, android.webkit.RenderProcessGoneDetail error) {
                finish(ticket, callback, ScheduleCore.Status.UNREACHABLE, "同步网页已停止运行，已保留本地课表", metadata("webview", 0, 0, "网页渲染进程停止")); return true;
            }
            public void onReceivedError(WebView v, WebResourceRequest r, WebResourceError e) {
                if (r.isForMainFrame()) {
                    int code = e.getErrorCode(); String reason = WebDiagnostics.networkReason(code);
                    finish(ticket, callback, ScheduleCore.Status.UNREACHABLE, "暂时无法连接教务系统（网络错误 " + code + "）", metadata("timetable", 0, code, reason));
                }
            }
            public void onReceivedHttpError(WebView v, WebResourceRequest r, WebResourceResponse response) {
                if (r.isForMainFrame()) {
                    int code = response.getStatusCode();
                    ScheduleCore.Status status = code >= 500 ? ScheduleCore.Status.SERVER_ERROR : ScheduleCore.Status.UNREACHABLE;
                    finish(ticket, callback, status, code >= 500 ? "教务系统暂时故障（HTTP " + code + "）" : "官网拒绝了当前请求（HTTP " + code + "）", metadata("timetable", code, 0, "主框架 HTTP " + code));
                }
            }
            public void onPageFinished(WebView v, String url) { if (ticket == generation && !done && allowed(url)) readPage(ticket, documentGeneration, callback, 0); }
        });
        probe.loadUrl(TIMETABLE);
    }

    private JSONObject metadata(String stage, int http, int networkCode, String reason) {
        JSONObject page = new JSONObject();
        try { page.put("stage", stage); page.put("http", http); page.put("networkCode", networkCode); page.put("networkError", networkCode != 0); page.put("reason", reason); }
        catch (Exception ignored) {}
        return page;
    }

    private JSONObject addStage(JSONObject page, String stage) {
        if (page == null) page = new JSONObject();
        try { if (!page.has("stage")) page.put("stage", stage); }
        catch (Exception ignored) {}
        return page;
    }

    private void readPage(int ticket, int document, Callback cb, int attempt) {
        if (ticket != generation || document != documentGeneration || done || probe == null) return;
        CourseReader.read(probe, (page, error) -> {
            if (ticket != generation || document != documentGeneration || done || probe == null) return;
            if (error != null) { finish(ticket, cb, ScheduleCore.Status.PARSE_ERROR, "课表读取失败，保留缓存", metadata("course-reader", 0, 0, "页面解析失败")); return; }
            page = addStage(page, "timetable");
            if (page.optInt("http") >= 400) {
                ScheduleCore.Status status = readFailure(page);
                finish(ticket, cb, status, "课表接口请求失败（HTTP " + page.optInt("http") + "），保留缓存", page);
                return;
            }
            if (page.optBoolean("login")) {
                finish(ticket, cb, ScheduleCore.Status.LOGIN_REQUIRED, "请在官网完成登录，成功后将自动读取课表", page); return;
            }
            if (page.optBoolean("complete")) {
                try {
                    if (!page.has("readAt")) page.put("readAt", System.currentTimeMillis());
                    if (page.optString("pageUrl").isEmpty()) page.put("pageUrl", probe.getUrl());
                } catch (Exception ignored) {}
                CookieManager.getInstance().flush(); finish(ticket, cb, ScheduleCore.Status.SUCCESS, "已读取官网课表，正在校验并保存", page); return;
            }
            if ("emap".equals(page.optString("adapter"))) {
                ScheduleCore.Status status = readFailure(page);
                finish(ticket, cb, status, "课表接口读取未完成，保留缓存；请在官网登录窗口重试读取", page); return;
            }
            if (!navigated) {
                JSONArray links = page.optJSONArray("links");
                if (links != null) for (int i = 0; i < links.length(); i++) {
                    JSONObject link = links.optJSONObject(i); if (link == null) continue;
                    String title = link.optString("title"), url = link.optString("url");
                    if (title.matches(".*(个人课表|我的课表|学生课表|学期课表|课表查询).*")) {
                        if (allowed(url) && !url.equals(probe.getUrl())) { navigated = true; probe.loadUrl(url); return; }
                    }
                }
            }
            if (attempt < 9) { handler.postDelayed(() -> readPage(ticket, document, cb, attempt + 1), 800); return; }
            finish(ticket, cb, ScheduleCore.Status.ADAPTER_PENDING, "未识别到完整学期课表，已保留缓存；可在官网窗口的更多菜单重试采集", addStage(page, "course-parse"));
        });
    }

    private void finish(int ticket, Callback cb, ScheduleCore.Status status, String detail, JSONObject page) {
        if (ticket != generation || done) return;
        done = true;
        JSONObject data = addStage(page, "timetable");
        int http = data.optInt("http"), networkCode = data.optInt("networkCode");
        if (status == ScheduleCore.Status.UNREACHABLE || status == ScheduleCore.Status.SERVER_ERROR || status == ScheduleCore.Status.ADAPTER_PENDING)
            WebDiagnostics.record(activity, "课表-" + data.optString("stage", "timetable"), probe == null ? TIMETABLE : probe.getUrl(), detail, http != 0 ? http : networkCode);
        close();
        cb.finished(status, detail, data);
    }

    void close() {
        generation++; documentGeneration++; handler.removeCallbacksAndMessages(null);
        if (probe != null) {
            WebView old = probe; probe = null;
            try { old.stopLoading(); host.removeView(old); old.destroy(); } catch (RuntimeException ignored) {}
        }
        done = true;
    }
}
