package cn.lut.schedule;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.SystemClock;
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

/** Sequential read-only sync. UI callbacks and session ownership are confined to the main thread. */
final class SyncCoordinator {
    interface Callback { void finished(ScheduleCore.Status status, String detail); }
    interface Events {
        void timetableReady(ScheduleCore.Snapshot snapshot);
        void moduleUpdated(String module, boolean success, String detail);
        void finished(ScheduleCore.Status status, String detail);
    }

    private static final long ROUND_BUDGET_MS = 120_000L;
    private static SyncCoordinator sessionOwner;
    private static boolean sessionOwnerForeground;
    private final Context context;
    private final FrameLayout host;
    private final SiteGateway course;
    private final SharedPreferences prefs;
    private final Handler handler = new Handler();
    private WebView web;
    private int generation, stage, document;
    private boolean finished, clicked, reading, foreground;
    private boolean timetableOk, gradesOk, examsOk;
    private long roundDeadline;
    private StringBuilder details;
    private Events events;
    private ScheduleCore.Status failure;
    private static final String[] READ_ONLY = {"体测成绩管理", "课程查询", "学分收费管理", "学业完成查询", "成绩认定"};

    SyncCoordinator(Context context, FrameLayout host, SiteGateway course) {
        this.context = context;
        this.host = host;
        this.course = course;
        prefs = context.getSharedPreferences("settings", 0);
    }

    /** Compatibility entry point for callers which only consume the final result. */
    void start(Callback callback) {
        start(new Events() {
            public void timetableReady(ScheduleCore.Snapshot snapshot) {}
            public void moduleUpdated(String module, boolean success, String detail) {}
            public void finished(ScheduleCore.Status status, String detail) { callback.finished(status, detail); }
        }, false);
    }

    boolean start(Events listener, boolean foregroundRequest) {
        close();
        if (!AccessMode.official(context)) {
            listener.finished(ScheduleCore.Status.IDLE, "本地课表，不连接教务系统");
            return false;
        }
        SyncCoordinator previous = sessionOwner;
        if (previous != null && previous != this) {
            if (!foregroundRequest && sessionOwnerForeground) {
                return false;
            }
            previous.cancelForPreemption();
        }
        sessionOwner = this;
        sessionOwnerForeground = foregroundRequest;
        foreground = foregroundRequest;
        finished = false;
        events = listener;
        details = new StringBuilder();
        failure = ScheduleCore.Status.ADAPTER_PENDING;
        timetableOk = gradesOk = examsOk = false;
        roundDeadline = SystemClock.uptimeMillis() + ROUND_BUDGET_MS;
        final int ticket = ++generation;
        prefs.edit().putLong("last_attempt", System.currentTimeMillis()).apply();
        handler.postDelayed(() -> {
            if (isCurrent(ticket)) {
                appendDetail("整轮同步达到 120 秒时间预算");
                finish(ticket, ScheduleCore.Status.UNREACHABLE, "整轮同步达到 120 秒时间预算；已保留已保存数据");
            }
        }, ROUND_BUDGET_MS);
        course.start((status, message, page) -> {
            if (!isCurrent(ticket)) return;
            if (!authorized(ticket)) return;
            if (status == ScheduleCore.Status.SUCCESS && page != null) {
                try (SnapshotStore cache = new SnapshotStore(context)) {
                    String monday = prefs.getString("first_monday", "");
                    String term = prefs.getString("calendar_semester", "");
                    if (!term.isEmpty() && !page.optString("semester").isEmpty() && !term.equals(page.optString("semester"))) monday = "";
                    ScheduleCore.Snapshot snapshot = CourseReader.decode(page, monday, 1, System.currentTimeMillis());
                    if (snapshot == null || snapshot.courses == null || snapshot.courses.isEmpty()) throw new IllegalStateException("课表校验无有效课程");
                    cache.save(snapshot);
                    timetableOk = true;
                    prefs.edit().putLong("timetable_updated", snapshot.fetchedAt).apply();
                    if (isCurrent(ticket)) emitTimetable(snapshot);
                } catch (Exception e) {
                    failure = ScheduleCore.Status.PARSE_ERROR;
                    appendDetail("课表：校验或保存失败，保留旧缓存");
                    Diagnostics.record(context, "课表校验与保存", e, false);
                    finish(ticket, failure, details.toString());
                    return;
                }
            } else {
                failure = status;
                appendDetail("课表：" + message);
            }
            if (status == ScheduleCore.Status.LOGIN_REQUIRED) {
                finish(ticket, status, details.toString());
                return;
            }
            if (status == ScheduleCore.Status.UNREACHABLE || status == ScheduleCore.Status.SERVER_ERROR) {
                finish(ticket, status, details.toString());
                return;
            }
            stage = 0;
            openModule(ticket);
        });
        return true;
    }

    boolean isRunning() { return !finished && events != null && sessionOwner == this; }

    private boolean isCurrent(int ticket) {
        return ticket == generation && !finished && sessionOwner == this;
    }

    private String kind() { return stage == 0 ? "grades" : stage == 1 ? "exams" : "service:" + service(); }
    private String service() { return stage == 0 ? "成绩查询" : stage == 1 ? "我的考试安排" : READ_ONLY[stage - 2]; }

    private void openModule(int ticket) {
        if (!authorized(ticket)) return;
        destroyWeb();
        clicked = reading = false;
        int currentStage = stage;
        String moduleName = service();
        try {
            web = new WebView(context);
            SiteGateway.configure(web);
            host.addView(web, new FrameLayout.LayoutParams(context.getResources().getDisplayMetrics().widthPixels, context.getResources().getDisplayMetrics().heightPixels));
        } catch (RuntimeException | LinkageError error) {
            Diagnostics.record(context, "教务网页组件创建", error, false);
            recordExternal("教务模块", SiteGateway.HOME, "系统网页组件暂不可用", 0);
            moduleFailed(ticket, ScheduleCore.Status.UNREACHABLE, "系统网页组件暂不可用", true);
            return;
        }
        WebView current = web;
        String route = AcademicCatalog.route(prefs, moduleName);
        current.setWebViewClient(new WebViewClient() {
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest request) {
                if (v != web) return true;
                String url = request.getUrl().toString();
                if (SiteGateway.allowed(url)) return false;
                recordExternal("认证跳转", url, "认证地址尚未适配", 0);
                moduleFailed(ticket, ScheduleCore.Status.ADAPTER_PENDING, "认证地址尚未适配", true);
                return true;
            }
            public void onPageStarted(WebView v, String url, android.graphics.Bitmap icon) { if (v != web) return; document++; reading = false; AcademicReader.capture(v); }
            public void onPageCommitVisible(WebView v, String url) { AcademicReader.capture(v); }
            public void onPageFinished(WebView v, String url) { if (v == web && isCurrent(ticket)) { AcademicReader.capture(v); poll(ticket, document, 0); } }
            public void onReceivedSslError(WebView v, SslErrorHandler h, android.net.http.SslError error) {
                h.cancel(); if (v == web) { recordExternal("教务模块", v.getUrl(), "HTTPS 握手失败", error.getPrimaryError()); moduleFailed(ticket, ScheduleCore.Status.UNREACHABLE, "安全连接验证失败", true); }
            }
            public void onReceivedError(WebView v, WebResourceRequest r, WebResourceError error) {
                if (v == web && r.isForMainFrame()) { int code = error.getErrorCode(); String reason = WebDiagnostics.networkReason(code); recordExternal(moduleName, r.getUrl().toString(), reason, code); moduleFailed(ticket, ScheduleCore.Status.UNREACHABLE, reason, true); }
            }
            public void onReceivedHttpError(WebView v, WebResourceRequest r, WebResourceResponse response) {
                if (v == web && r.isForMainFrame()) { int code = response.getStatusCode(); ScheduleCore.Status status = code == 401 ? ScheduleCore.Status.LOGIN_REQUIRED : code >= 500 ? ScheduleCore.Status.SERVER_ERROR : ScheduleCore.Status.UNREACHABLE; String reason = "HTTP " + code; recordExternal(moduleName, r.getUrl().toString(), reason, code); moduleFailed(ticket, status, reason, code == 401 || code == 429); }
            }
            public boolean onRenderProcessGone(WebView v, android.webkit.RenderProcessGoneDetail error) {
                if (v == web) { recordExternal(moduleName, v.getUrl(), "网页组件停止运行", 0); moduleFailed(ticket, ScheduleCore.Status.UNREACHABLE, "网页组件停止运行", true); }
                return true;
            }
        });
        handler.postDelayed(() -> {
            if (isCurrent(ticket) && stage == currentStage && web == current) {
                recordExternal(moduleName, current.getUrl(), "读取超时，保留缓存", 0);
                moduleFailed(ticket, ScheduleCore.Status.UNREACHABLE, "读取超时，保留缓存", false);
            }
        }, 35_000L);
        current.loadUrl(SiteGateway.savedPageUrl(route).isEmpty() ? SiteGateway.HOME : route);
    }

    private void poll(int ticket, int doc, int attempt) {
        if (!authorized(ticket) || doc != document || web == null || reading) return;
        reading = true;
        WebView current = web;
        String module = kind();
        AcademicReader.read(current, module, (page, error) -> {
            if (!authorized(ticket) || current != web || doc != document) return;
            reading = false;
            if (error != null) {
                Diagnostics.record(context, "教务页面读取", error, false);
                moduleFailed(ticket, ScheduleCore.Status.PARSE_ERROR, "页面读取失败，保留缓存", false);
                return;
            }
            if (page != null) {
                rememberLinks(context, page);
                if (page.optBoolean("login")) {
                    failure = ScheduleCore.Status.LOGIN_REQUIRED;
                    appendDetail(service() + "：登录已失效");
                    emitModule(module, false, "登录状态待重新确认");
                    finish(ticket, ScheduleCore.Status.LOGIN_REQUIRED, details.toString());
                    return;
                }
                if (page.optBoolean("complete")) {
                    try (AcademicStore data = new AcademicStore(context)) {
                        data.save(module, page, scope(page), System.currentTimeMillis());
                        if (stage == 0) gradesOk = true; else if (stage == 1) examsOk = true;
                        prefs.edit().putLong(module + "_updated", System.currentTimeMillis()).apply();
                        emitModule(module, true, "已校验并保存");
                        next(ticket);
                        return;
                    } catch (Exception saveError) {
                        Diagnostics.record(context, "教务数据校验与保存", saveError, false);
                        moduleFailed(ticket, ScheduleCore.Status.PARSE_ERROR, "结果校验或保存失败，保留缓存", false);
                        return;
                    }
                }
                if (!clicked && page.optBoolean("portal")) {
                    String route = AcademicCatalog.route(prefs, service());
                    if (SiteGateway.allowed(route) && !route.equals(current.getUrl())) { clicked = true; current.loadUrl(route); return; }
                    clicked = true;
                    clickService(current, service());
                }
            }
            if (attempt < 10 && SystemClock.uptimeMillis() + 700 < roundDeadline) {
                handler.postDelayed(() -> poll(ticket, doc, attempt + 1), 700);
            } else {
                moduleFailed(ticket, ScheduleCore.Status.ADAPTER_PENDING, page == null ? "读取失败" : page.optString("reason", "尚未识别页面"), false);
            }
        });
    }

    static void clickService(WebView view, String name) {
        String script = "(function(n){var a=document.querySelectorAll('a,button,[role=button],[onclick],[class*=app]');for(var i=0;i<a.length;i++){var t=(a[i].innerText||a[i].title||'').replace(/\\s+/g,'').trim();if(t===n){a[i].click();return true;}}return false;})('" + name.replace("'", "") + "')";
        view.evaluateJavascript(script, null);
    }

    static void rememberLinks(Context context, JSONObject page) {
        JSONArray links = page.optJSONArray("links");
        if (links == null) return;
        SharedPreferences.Editor edit = context.getSharedPreferences("settings", 0).edit();
        for (int i = 0; i < links.length(); i++) {
            JSONObject link = links.optJSONObject(i);
            if (link == null) continue;
            String title = link.optString("title").replaceAll("\\s+", ""), url = SiteGateway.savedPageUrl(link.optString("url"));
            if (url.isEmpty()) continue;
            for (String[] group : AcademicCatalog.GROUPS) for (String label : group) if (title.equals(label.replaceAll("\\s+", ""))) edit.putString("service_" + label, url);
        }
        edit.apply();
    }

    static String scope(JSONObject page) {
        String account = page.optString("account");
        String value = account.isEmpty() ? "session:" + String.valueOf(CookieManager.getInstance().getCookie(SiteGateway.HOME)) : "account:" + account;
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder();
            for (byte b : bytes) out.append(String.format(Locale.ROOT, "%02x", b & 255));
            return out.toString();
        } catch (Exception e) { throw new IllegalStateException(e); }
    }

    private void moduleFailed(int ticket, ScheduleCore.Status status, String reason, boolean terminateRound) {
        if (!isCurrent(ticket)) return;
        failure = status;
        appendDetail(service() + "：" + reason);
        emitModule(kind(), false, reason);
        if (terminateRound || status == ScheduleCore.Status.LOGIN_REQUIRED || SystemClock.uptimeMillis() >= roundDeadline) finish(ticket, status, details.toString());
        else next(ticket);
    }

    private void next(int ticket) {
        if (!isCurrent(ticket)) return;
        stage++;
        while (stage >= 2 && stage < READ_ONLY.length + 2 && AcademicCatalog.route(prefs, service()).isEmpty()) {
            String name = service();
            appendDetail(name + "：尚未发现官网链接");
            emitModule("service:" + name, false, "尚未发现官网链接");
            stage++;
        }
        if (stage >= READ_ONLY.length + 2) finish(ticket, null, null);
        else if (SystemClock.uptimeMillis() >= roundDeadline) finish(ticket, ScheduleCore.Status.UNREACHABLE, "整轮同步达到 120 秒时间预算；已保留已保存数据");
        else openModule(ticket);
    }

    private boolean authorized(int ticket) {
        if (!isCurrent(ticket)) return false;
        if (AccessMode.official(context)) return true;
        finish(ticket, ScheduleCore.Status.IDLE, "已切换为本地课表，停止官网读取");
        return false;
    }

    private void finish(int ticket, ScheduleCore.Status override, String overrideDetail) {
        if (ticket != generation || finished || sessionOwner != this) return;
        if (override == null && SystemClock.uptimeMillis() >= roundDeadline) {
            override = ScheduleCore.Status.UNREACHABLE;
            overrideDetail = "整轮同步达到 120 秒时间预算；已保留已保存数据";
        }
        finished = true;
        boolean coreOk = timetableOk && gradesOk && examsOk;
        long time = System.currentTimeMillis();
        if (coreOk) prefs.edit().putLong("last_full_success", time).apply();
        ScheduleCore.Status result = override != null ? override : coreOk ? ScheduleCore.Status.SUCCESS : failure;
        String message = overrideDetail != null ? overrideDetail : coreOk ? "课表、成绩和考试已校验并保存" : details.toString();
        prefs.edit().putString("sync_detail", message).apply();
        Events listener = events;
        events = null;
        handler.removeCallbacksAndMessages(null);
        destroyWeb();
        course.close();
        releaseSession();
        if (listener != null) {
            try { listener.finished(result, message); }
            catch (RuntimeException callbackError) { Diagnostics.record(context, "同步完成回调", callbackError, false); }
        }
    }

    private void cancelForPreemption() {
        if (finished || sessionOwner != this) return;
        int ticket = generation;
        Events listener = events;
        finished = true;
        events = null;
        handler.removeCallbacksAndMessages(null);
        course.close();
        destroyWeb();
        releaseSession();
        if (listener != null) {
            try { listener.finished(ScheduleCore.Status.IDLE, "前台同步抢占，后台或旧采集已取消"); }
            catch (RuntimeException callbackError) { Diagnostics.record(context, "同步抢占回调", callbackError, false); }
        }
    }

    private void appendDetail(String line) {
        if (details == null) details = new StringBuilder();
        if (details.length() > 0) details.append('\n');
        details.append(line);
    }

    private void recordExternal(String stageName, String url, String reason, int code) {
        WebDiagnostics.record(context, stageName, url, reason, code);
    }

    private void emitTimetable(ScheduleCore.Snapshot snapshot) {
        Events listener = events;
        if (listener != null) try { listener.timetableReady(snapshot); }
        catch (RuntimeException callbackError) { Diagnostics.record(context, "课表就绪回调", callbackError, false); }
    }

    private void emitModule(String module, boolean success, String detail) {
        Events listener = events;
        if (listener != null) try { listener.moduleUpdated(module, success, detail); }
        catch (RuntimeException callbackError) { Diagnostics.record(context, "教务模块回调", callbackError, false); }
    }

    private void releaseSession() {
        if (sessionOwner == this) { sessionOwner = null; sessionOwnerForeground = false; }
    }

    private void destroyWeb() {
        if (web != null) {
            WebView old = web;
            web = null;
            try { old.stopLoading(); host.removeView(old); old.destroy(); } catch (RuntimeException ignored) {}
        }
    }

    void close() {
        generation++;
        finished = true;
        events = null;
        handler.removeCallbacksAndMessages(null);
        course.close();
        destroyWeb();
        releaseSession();
    }
}
