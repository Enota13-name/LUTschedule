package cn.lut.schedule.tests;

import android.app.Activity;
import android.app.Dialog;
import android.app.Instrumentation;
import android.content.DialogInterface;
import android.os.Handler;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.LocalDate;

/** Synthetic MainActivity event and optional-credential UI checks. Never invokes refresh or a school account. */
public final class RuntimeUpdateUi {
    private final Instrumentation test;
    private int checks;
    private static final Class<?>[] NONE = new Class<?>[0];

    private RuntimeUpdateUi(Instrumentation instrumentation) { test = instrumentation; }

    /** Invoked by the runtime test harness after MainActivity has started. */
    public static String run(Instrumentation instrumentation, Activity activity) throws Exception {
        int assertions = new RuntimeUpdateUi(instrumentation).execute(activity);
        return "PASS: " + assertions + " assertions";
    }

    private int execute(Activity activity) throws Exception {
        Object coordinator = field(activity, "coordinator");
        uiCall(coordinator, "close", NONE);
        Handler handler = (Handler) field(activity, "handler");
        handler.removeCallbacksAndMessages(null);
        uiSet(activity, "foreground", true);

        syntheticUpdateCallbacks(activity);
        credentialNoticeAndEditor(activity);
        return checks;
    }

    private void syntheticUpdateCallbacks(Activity activity) throws Exception {
        Class<?> sourceClass = Class.forName("cn.lut.schedule.ScheduleCore$Source");
        Object official = Enum.valueOf((Class) sourceClass, "OFFICIAL");
        JSONObject courseJson = syntheticTimetable();
        Object snapshot = call(Class.forName("cn.lut.schedule.SnapshotStore"), "decode",
                new Class<?>[]{JSONObject.class, sourceClass}, courseJson, official);
        Object snapshotStore = field(activity, "store");
        call(snapshotStore, "save", new Class<?>[]{snapshot.getClass()}, snapshot);

        activity.getSharedPreferences("settings", 0).edit().putString("entry_mode", "official")
                .putBoolean("credential_intro_v1", true).commit();
        uiCall(activity, "loadSnapshot", NONE);
        uiCall(activity, "loadAcademic", NONE);
        uiCall(activity, "renderPage", new Class<?>[]{boolean.class}, false);

        Object sync = field(activity, "sync");
        int request = nextRequest(activity);
        int ticket = (Integer) uiCall(sync, "begin", NONE);
        uiSet(activity, "courseReadyThisRun", false);
        Object events = syncEvents(activity, request, ticket);
        uiCall(events, "timetableReady", new Class<?>[]{snapshot.getClass()}, snapshot);
        test.waitForIdleSync();
        require(((TextView) field(activity, "statusText")).getText().toString().contains("✓ 课表已更新"),
                "verified saved timetable immediately shows the home success check");
        require(uiCall(sync, "status", NONE).toString().equals("SUCCESS"),
                "timetable-ready event marks only the validated official ticket successful");
        require(field(activity, "snapshot") == snapshot, "ready callback installs the supplied saved snapshot");

        JSONObject examPage = syntheticExamPage(snapshot);
        Object academicStore = field(activity, "academicStore");
        call(academicStore, "save", new Class<?>[]{String.class, JSONObject.class, String.class, long.class},
                "exams", examPage, "runtime-update-ui-synthetic", System.currentTimeMillis());
        uiCall(events, "moduleUpdated", new Class<?>[]{String.class, boolean.class, String.class},
                "exams", true, "synthetic module saved");
        test.waitForIdleSync();
        require(((JSONArray) field(activity, "examRecords")).length() == 1,
                "saved exam records are reloaded after the independent module event");
        require((Integer) call(activity, "examBandHeight", NONE) > 0,
                "saved exam immediately contributes a visible timetable exam band");

        uiCall(events, "moduleUpdated", new Class<?>[]{String.class, boolean.class, String.class},
                "grades", false, "synthetic later module failure");
        uiCall(events, "finished", new Class<?>[]{Class.forName("cn.lut.schedule.ScheduleCore$Status"), String.class},
                enumValue("cn.lut.schedule.ScheduleCore$Status", "ADAPTER_PENDING"), "synthetic later failure");
        test.waitForIdleSync();
        require(uiCall(sync, "status", NONE).toString().equals("SUCCESS"),
                "later module failure does not revoke timetable success");
        require(((TextView) field(activity, "statusText")).getText().toString().contains("✓ 课表已更新"),
                "later module failure leaves the home success check visible");
        require(field(activity, "completeStatus").toString().equals("ADAPTER_PENDING"),
                "whole-round status separately records the later module failure");

        request = nextRequest(activity);
        ticket = (Integer) uiCall(sync, "begin", NONE);
        uiSet(activity, "courseReadyThisRun", false);
        events = syncEvents(activity, request, ticket);
        uiCall(events, "timetableReady", new Class<?>[]{snapshot.getClass()}, snapshot);
        uiCall(events, "moduleUpdated", new Class<?>[]{String.class, boolean.class, String.class},
                "exams", true, "synthetic complete round");
        uiCall(events, "finished", new Class<?>[]{Class.forName("cn.lut.schedule.ScheduleCore$Status"), String.class},
                enumValue("cn.lut.schedule.ScheduleCore$Status", "SUCCESS"), "synthetic complete round");
        require(field(activity, "completeStatus").toString().equals("SUCCESS"),
                "independent final event records a completed whole round");
        require(uiCall(sync, "status", NONE).toString().equals("SUCCESS"),
                "successful whole round retains the timetable success state");

        Object originalSnapshot = field(activity, "snapshot");
        Object changed = changedSnapshot(courseJson, sourceClass, official);
        Object staleEvents = syncEvents(activity, request, ticket);
        uiSet(activity, "syncRequestId", request + 1);
        uiCall(staleEvents, "timetableReady", new Class<?>[]{changed.getClass()}, changed);
        uiCall(staleEvents, "finished", new Class<?>[]{Class.forName("cn.lut.schedule.ScheduleCore$Status"), String.class},
                enumValue("cn.lut.schedule.ScheduleCore$Status", "LOGIN_REQUIRED"), "stale synthetic login result");
        require(field(activity, "snapshot") == originalSnapshot,
                "stale request cannot replace the currently displayed snapshot");
        require(field(activity, "loginDialog") == null,
                "stale request cannot open the login window");

        uiSet(activity, "syncRequestId", request + 2);
        int lifecycleTicket = (Integer) uiCall(sync, "begin", NONE);
        Object lifecycleEvents = syncEvents(activity, request + 2, lifecycleTicket);
        invokeLifecycleStop(activity);
        activity.getSharedPreferences("settings",0).edit().putString("entry_mode","import").commit();
        runMain(()->{test.callActivityOnStart(activity);test.callActivityOnResume(activity);});
        activity.getSharedPreferences("settings",0).edit().putString("entry_mode","official").commit();
        handlerCleanup(activity);
        uiSet(activity, "foreground", true);
        uiSet(activity, "returnFromBackground", false);
        uiCall(lifecycleEvents, "timetableReady", new Class<?>[]{snapshot.getClass()}, changed);
        uiCall(lifecycleEvents, "finished", new Class<?>[]{Class.forName("cn.lut.schedule.ScheduleCore$Status"), String.class},
                enumValue("cn.lut.schedule.ScheduleCore$Status", "LOGIN_REQUIRED"), "cancelled synthetic login result");
        require(field(activity, "snapshot") == originalSnapshot,
                "onStop cancellation prevents old events replacing the current snapshot");
        require(field(activity, "loginDialog") == null,
                "onStop cancellation prevents old events opening the login window");

        uiSet(activity, "syncRequestId", request + 3);
        activity.getSharedPreferences("settings", 0).edit().putString("entry_mode", "import").commit();
        Object importEvents = syncEvents(activity, request + 3, lifecycleTicket);
        uiCall(importEvents, "timetableReady", new Class<?>[]{snapshot.getClass()}, changed);
        uiCall(importEvents, "finished", new Class<?>[]{Class.forName("cn.lut.schedule.ScheduleCore$Status"), String.class},
                enumValue("cn.lut.schedule.ScheduleCore$Status", "LOGIN_REQUIRED"), "synthetic import-mode result");
        require(field(activity, "snapshot") == originalSnapshot,
                "import mode rejects late official events without replacing its selected snapshot");
        require(field(activity, "loginDialog") == null,
                "import mode rejects late official login requests");
        activity.getSharedPreferences("settings", 0).edit().putString("entry_mode", "official").commit();
    }

    private void credentialNoticeAndEditor(Activity activity) throws Exception {
        android.content.SharedPreferences prefs = activity.getSharedPreferences("settings", 0);
        prefs.edit().remove("credential_intro_v1").putBoolean("saved_login_enabled", false).commit();
        uiCall(activity, "offerCredentialNotice", NONE);
        Object intro = field(activity, "credentialIntro");
        require("cn.lut.schedule.AppDialog".equals(intro.getClass().getName()) && ((Dialog) intro).isShowing(),
                "first-use or upgrade credential notice uses the app-owned dialog");
        String notice = text((ViewGroup) ((Dialog) intro).getWindow().getDecorView());
        require(notice.contains("仅在你授权后用于官网登录") && notice.contains("App 需要联网访问教务系统"),
                "credential notice explains authorization and network requirement");
        require(notice.contains("本地加密不能保证绝无泄露") && !notice.contains("绝不会泄露"),
                "credential notice does not promise zero leakage");
        require(prefs.getBoolean("credential_intro_v1", false), "notice is marked shown exactly once");
        clickDialogButton((Dialog) intro, DialogInterface.BUTTON_NEGATIVE);
        uiCall(activity, "offerCredentialNotice", NONE);
        require(field(activity, "credentialIntro") == null,
                "dismissed introductory notice is not offered repeatedly");

        uiCall(activity, "editSavedLogin", NONE);
        Object editor = field(activity, "credentialDialog");
        require("cn.lut.schedule.AppDialog".equals(editor.getClass().getName()) && ((Dialog) editor).isShowing(),
                "credential editor is an app-owned dialog");
        require((((Dialog) editor).getWindow().getAttributes().flags & WindowManager.LayoutParams.FLAG_SECURE) != 0,
                "credential editor protects its window with FLAG_SECURE");
        java.util.List<EditText> inputs = new java.util.ArrayList<>();
        collectInputs(((Dialog) editor).getWindow().getDecorView(), inputs);
        require(inputs.size() == 2, "credential editor provides separate account and password fields");
        for (EditText input : inputs) {
            require(!input.isSaveEnabled(), "credential field disables saved-instance plaintext state");
            require(input.getImportantForAutofill() == View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS,
                    "credential field opts out of platform autofill persistence");
        }
        String editorText = text((ViewGroup) ((Dialog) editor).getWindow().getDecorView());
        require(editorText.contains("保存并允许前台重登录") && editorText.contains("取消"),
                "editor offers explicit opt-in and cancellation without auto-enabling");
        require(!prefs.getBoolean("saved_login_enabled", false), "opening editor does not enable saved login");
        clickDialogButton((Dialog) editor, DialogInterface.BUTTON_NEGATIVE);
        require(field(activity, "credentialDialog") == null && !prefs.getBoolean("saved_login_enabled", false),
                "cancel closes editor without enabling saved login");

        Object vault = field(activity, "credentialVault");
        call(vault, "delete", NONE);
        boolean seeded = (Boolean) call(vault, "save", new Class<?>[]{String.class, String.class},
                "synthetic-runtime-user", "synthetic-runtime-password");
        require(seeded, "synthetic-only encrypted fixture is available to inspect the delete entry");
        prefs.edit().putBoolean("saved_login_enabled", true).commit();
        dismissIfShowing(activity, "handDialog");
        dismissIfShowing(activity, "errorDialog");
        test.waitForIdleSync();
        uiCall(activity, "showSavedLogin", NONE);
        Dialog options=(Dialog)field(activity,"credentialOptions");
        require(options!=null&&options.isShowing(),"saved-login management uses a visible app-owned dialog");
        TextView deleteAction=(TextView)call(options,"getButton",new Class<?>[]{int.class},DialogInterface.BUTTON_NEGATIVE);
        require(deleteAction!=null&&deleteAction.getText().toString().equals("删除保存信息"),
                "saved-login settings expose an explicit delete action");
        TextView editAction=(TextView)call(options,"getButton",new Class<?>[]{int.class},DialogInterface.BUTTON_NEUTRAL);
        require(editAction!=null&&editAction.getText().toString().equals("更改保存信息"),
                "saved-login settings expose an edit action");
        boolean[] deletedFromUi={false};runMain(()->deletedFromUi[0]=deleteAction.performClick());
        require(deletedFromUi[0], "delete entry is an actionable app-dialog control");
        long deadline = android.os.SystemClock.uptimeMillis() + 3000;
        while ((Boolean) call(vault, "hasCredentials", NONE)
                && android.os.SystemClock.uptimeMillis() < deadline) {
            Thread.sleep(50);
            test.waitForIdleSync();
        }
        require(!(Boolean) call(vault, "hasCredentials", NONE), "synthetic saved credentials are deleted through the settings action");
        require(!prefs.getBoolean("saved_login_enabled", false), "delete action disables saved-login use");
    }

    private JSONObject syntheticExamPage(Object snapshot) throws Exception {
        LocalDate monday = (LocalDate) field(snapshot, "firstMonday");
        LocalDate date = monday.plusWeeks(5).plusDays(2);
        JSONArray records = new JSONArray().put(new JSONObject().put("key", "synthetic-exam-1")
                .put("name", "合成考试").put("date", date.toString()).put("startTime", "09:00")
                .put("endTime", "10:30").put("room", "测试房间"));
        return new JSONObject().put("kind", "exams").put("complete", true).put("records", records);
    }

    private JSONObject syntheticTimetable() throws Exception {
        JSONArray weeks = new JSONArray();
        for (int i = 1; i <= 20; i++) weeks.put(i);
        JSONArray courses = new JSONArray().put(new JSONObject().put("name", "合成验证课程")
                .put("teacher", "合成教师").put("room", "测试房间").put("day", 1)
                .put("start", 1).put("end", 2).put("rawStart", 1).put("rawEnd", 2)
                .put("periodsPerRow", 1).put("weeks", weeks));
        LocalDate monday = LocalDate.now(java.time.ZoneId.of("Asia/Shanghai"))
                .with(java.time.DayOfWeek.MONDAY).minusWeeks(5);
        return new JSONObject().put("schemaVersion", 1).put("semester", "合成课表 · 非学校数据")
                .put("firstMonday", monday.toString()).put("totalWeeks", 20).put("periodCount", 10)
                .put("fetchedAt", System.currentTimeMillis()).put("courses", courses);
    }

    private Object changedSnapshot(JSONObject fixture, Class<?> sourceClass, Object official) throws Exception {
        JSONObject changed = new JSONObject(fixture.toString()).put("semester", "合成旧请求课表");
        return call(Class.forName("cn.lut.schedule.SnapshotStore"), "decode",
                new Class<?>[]{JSONObject.class, sourceClass}, changed, official);
    }

    private Object syncEvents(Activity activity, int request, int ticket) throws Exception {
        Class<?> events = Class.forName("cn.lut.schedule.SyncCoordinator$Events");
        return call(activity, "syncEvents", new Class<?>[]{int.class, int.class}, request, ticket);
    }

    private int nextRequest(Activity activity) throws Exception {
        int request = (Integer) field(activity, "syncRequestId") + 1;
        set(activity, "syncRequestId", request);
        return request;
    }

    private void invokeLifecycleStop(Activity activity) throws Exception {
        Method stop = activity.getClass().getDeclaredMethod("onStop");
        stop.setAccessible(true);
        runMain(() -> { try { stop.invoke(activity); } catch (Exception e) { throw new RuntimeException(e); } });
    }

    private void handlerCleanup(Activity activity) throws Exception {
        ((Handler) field(activity, "handler")).removeCallbacksAndMessages(null);
        call(field(activity, "coordinator"), "close", NONE);
    }

    private void dismissIfShowing(Activity activity, String name) throws Exception {
        Object value = field(activity, name);
        if (value instanceof Dialog && ((Dialog) value).isShowing())
            runMain(((Dialog) value)::dismiss);
    }

    private Object enumValue(String name, String value) throws Exception {
        Class<?> type = Class.forName(name);
        return Enum.valueOf((Class) type, value);
    }

    private static Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void set(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Object call(Object target, String name, Class<?>[] types, Object... args) throws Exception {
        Class<?> owner = target instanceof Class ? (Class<?>) target : target.getClass();
        Method method = owner.getDeclaredMethod(name, types);
        method.setAccessible(true);
        return method.invoke(target instanceof Class ? null : target, args);
    }

    private Object uiCall(Object target, String name, Class<?>[] types, Object... args) throws Exception {
        Object[] result = new Object[1];
        Throwable[] error = new Throwable[1];
        runMain(() -> { try { result[0] = call(target, name, types, args); } catch (Throwable e) { error[0] = e; } });
        if (error[0] != null) throw new AssertionError(error[0]);
        return result[0];
    }

    private void uiSet(Object target, String name, Object value) throws Exception {
        runMain(() -> { try { set(target, name, value); } catch (Exception e) { throw new RuntimeException(e); } });
    }

    private void clickDialogButton(Dialog dialog, int which) throws Exception {
        View button = (View) call(dialog, "getButton", new Class<?>[]{int.class}, which);
        require(button != null, "expected dialog action exists: " + which);
        runMain(button::performClick);
        test.waitForIdleSync();
    }

    private void collectInputs(View view, java.util.List<EditText> out) {
        if (view instanceof EditText) out.add((EditText) view);
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++)
            collectInputs(((ViewGroup) view).getChildAt(i), out);
    }

    private String text(ViewGroup view) {
        StringBuilder out = new StringBuilder();
        collectText(view, out);
        return out.toString();
    }

    private void collectText(View view, StringBuilder out) {
        if (view instanceof TextView) out.append(((TextView) view).getText()).append('\n');
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++)
            collectText(((ViewGroup) view).getChildAt(i), out);
    }

    private void runMain(Runnable task) {
        test.runOnMainSync(task);
        test.waitForIdleSync();
    }

    private void require(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
