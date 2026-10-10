package cn.lut.schedule;

import android.webkit.WebView;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.net.URI;
import java.util.function.BooleanSupplier;

/** Guardrails and one-attempt state for the verified official local-login page. */
public final class OfficialLogin {
    private static final String HOST = "jwxt.lut.edu.cn";
    private static final String PATH = "/jwapp/sys/yjsrzfwapp/dbLogin/index.do";
    private boolean submitClaimed;

    /** Strict allowlist: HTTPS, official host, exact path, no alternate port or user info. */
    public static boolean isAllowedUrl(String value) {
        try {
            URI uri = URI.create(value);
            return "https".equalsIgnoreCase(uri.getScheme()) && HOST.equalsIgnoreCase(uri.getHost())
                    && (uri.getPort() == -1 || uri.getPort() == 443) && PATH.equals(uri.getPath())
                    && uri.getUserInfo() == null;
        } catch (Exception ignored) { return false; }
    }

    /** Call once per foreground reconnection flow; never from a background refresh. */
    public synchronized boolean claimForegroundAttempt(String currentUrl, boolean foreground) {
        if (!foreground || !isAllowedUrl(currentUrl) || submitClaimed) return false;
        submitClaimed = true;
        return true;
    }

    /** A new user-initiated foreground flow may claim one attempt after returning to the app. */
    public synchronized void beginForegroundFlow() { submitClaimed = false; }

    /** Injects credentials into only the checked top-level page's lexical scope, never a JS bridge. */
    public void fill(WebView webView, CredentialVault.Credentials credentials, boolean foreground, Callback callback) {
        fill(webView, credentials, () -> foreground, callback);
    }

    /** Authorization is checked when the posted UI work executes and immediately before JS evaluation. */
    public void fill(WebView webView, CredentialVault.Credentials credentials, BooleanSupplier authorized, Callback callback) {
        if (webView == null || credentials == null || authorized == null || callback == null) return;
        webView.post(() -> {
            String currentUrl = webView.getUrl();
            if (!isAllowedUrl(currentUrl) || !safeAuthorized(authorized)
                    || !claimForegroundAttempt(currentUrl, true)) { callback.onStatus("blocked"); return; }
            final String script;
            try {
                String source = readAsset(webView, "read-login-form.js");
                script = "(function(savedUser,savedPassword,canSubmit){\n" + source + "\n})("
                        + JSONObject.quote(credentials.username) + ","
                        + JSONObject.quote(credentials.password) + ",true);";
            } catch (Exception ignored) { callback.onStatus("manual"); return; }
            if (!isAllowedUrl(webView.getUrl()) || !safeAuthorized(authorized)) { callback.onStatus("blocked"); return; }
            webView.evaluateJavascript(script, value -> {
                String status = "manual";
                try {
                    Object decoded = value == null ? null : new org.json.JSONTokener(value).nextValue();
                    if (decoded instanceof String) {
                        JSONObject result = new JSONObject((String) decoded);
                        String candidate = result.optString("status", "manual");
                        if ("submitted".equals(candidate) || "challenge".equals(candidate)
                                || "manual".equals(candidate) || "blocked".equals(candidate)) status = candidate;
                    }
                } catch (Exception ignored) { }
                callback.onStatus(status);
            });
        });
    }

    private static boolean safeAuthorized(BooleanSupplier authorized) {
        try { return authorized.getAsBoolean(); } catch (RuntimeException ignored) { return false; }
    }

    private static String readAsset(WebView webView, String name) throws Exception {
        try (InputStream in = webView.getContext().getAssets().open(name);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096]; int count;
            while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    public interface Callback { void onStatus(String status); }

    /** The page form selector is intentionally fixed to the anonymously verified official markup. */
    public static String usernameSelector() { return "#userId[name='userId'][type='text']"; }
    public static String passwordSelector() { return "#password[name='password'][type='password']"; }
    public static String submitSelector() { return "button#loginBtn[type='button']"; }

    public OfficialLogin() { }
}
