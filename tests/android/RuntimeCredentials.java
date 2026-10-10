package cn.lut.schedule.tests;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.KeyStore;
import java.util.Arrays;

/** Synthetic credential-vault and login-guard checks. Never uses a school account. */
public final class RuntimeCredentials {
    private RuntimeCredentials() { }

    /** Invoked from RuntimeSmoke using only reflection, so this fixture is not packaged in the app. */
    public static int run(Context context) throws Exception {
        int checks = 0;
        Class<?> vaultClass = Class.forName("cn.lut.schedule.CredentialVault");
        Constructor<?> vaultCtor = vaultClass.getConstructor(Context.class);
        Object vault = vaultCtor.newInstance(context);
        Method save = vaultClass.getMethod("save", String.class, String.class);
        Method load = vaultClass.getMethod("load");
        Method has = vaultClass.getMethod("hasCredentials");
        Method delete = vaultClass.getMethod("delete");
        File encryptedFile = new File(context.getFilesDir(), "credentials.vault");
        File tempFile = new File(context.getFilesDir(), "credentials.vault.tmp");

        require((Boolean) delete.invoke(vault), "initial cleanup is confirmed"); checks++;
        require(!(Boolean) save.invoke(vault, "", "synthetic-password"), "empty username rejected"); checks++;
        require(!(Boolean) save.invoke(vault, "synthetic-user", ""), "empty password rejected"); checks++;
        require(!(Boolean) save.invoke(vault, repeat('u', 201), "synthetic-password"), "username byte limit enforced"); checks++;
        require(!(Boolean) save.invoke(vault, "synthetic-user", repeat('p', 1025)), "password byte limit enforced"); checks++;
        require(!encryptedFile.exists(), "rejected credentials do not create vault data"); checks++;

        String account1 = "fixture-account-alpha", password1 = "fixture-password-alpha";
        require((Boolean) save.invoke(vault, account1, password1), "synthetic credentials saved"); checks++;
        require((Boolean) has.invoke(vault) && encryptedFile.isFile(), "vault presence reflects encrypted private file"); checks++;
        byte[] firstCipher = Files.readAllBytes(encryptedFile.toPath());
        require(!contains(firstCipher, account1.getBytes(StandardCharsets.UTF_8))
                && !contains(firstCipher, password1.getBytes(StandardCharsets.UTF_8)), "vault ciphertext contains no plaintext fixture"); checks++;
        require(aliasExists(), "Keystore key exists while vault is saved"); checks++;
        Object first = load.invoke(vault);
        require(account1.equals(secret(first, "username")) && password1.equals(secret(first, "password")), "vault decrypts saved credentials"); checks++;

        String account2 = "fixture-account-beta", password2 = "fixture-password-beta";
        require((Boolean) save.invoke(vault, account2, password2), "replacement credentials saved"); checks++;
        byte[] secondCipher = Files.readAllBytes(encryptedFile.toPath());
        require(!Arrays.equals(firstCipher, secondCipher), "replacement atomically changes randomized ciphertext"); checks++;
        require(!contains(secondCipher, account1.getBytes(StandardCharsets.UTF_8))
                && !contains(secondCipher, password1.getBytes(StandardCharsets.UTF_8))
                && !contains(secondCipher, account2.getBytes(StandardCharsets.UTF_8))
                && !contains(secondCipher, password2.getBytes(StandardCharsets.UTF_8)), "replacement file remains ciphertext only"); checks++;
        Object second = load.invoke(vault);
        require(account2.equals(secret(second, "username")) && password2.equals(secret(second, "password")), "load returns replacement credentials"); checks++;
        require(!tempFile.exists(), "atomic replacement leaves no temporary file"); checks++;
        Arrays.fill(firstCipher, (byte) 0); Arrays.fill(secondCipher, (byte) 0);

        require((Boolean) save.invoke(vault, account2, password2), "synthetic vault prepared for authentication-tag failure"); checks++;
        byte[] corrupt = Files.readAllBytes(encryptedFile.toPath()); corrupt[corrupt.length - 1] ^= 1;
        Files.write(encryptedFile.toPath(), corrupt); Arrays.fill(corrupt, (byte) 0);
        require(load.invoke(vault) == null, "tampered ciphertext fails authentication"); checks++;
        require(!(Boolean) has.invoke(vault) && !(Boolean) aliasExists(), "tamper recovery removes ciphertext and key"); checks++;
        require((Boolean) delete.invoke(vault), "delete confirms success after tamper recovery"); checks++;

        try (FileOutputStream out = new FileOutputStream(encryptedFile)) { out.write(new byte[17 * 1024]); }
        require(load.invoke(vault) == null, "oversized ciphertext fails closed"); checks++;
        require(!(Boolean) has.invoke(vault), "invalid ciphertext recovery removes vault file"); checks++;
        require(!(Boolean) aliasExists(), "invalid vault recovery removes unusable key"); checks++;
        require((Boolean) delete.invoke(vault), "delete succeeds after recovery"); checks++;

        require((Boolean) save.invoke(vault, account1, password1), "vault can be recreated after recovery"); checks++;
        require((Boolean) delete.invoke(vault), "delete confirms success"); checks++;
        require(!(Boolean) has.invoke(vault) && !encryptedFile.exists() && !tempFile.exists(), "delete removes ciphertext and temporary file"); checks++;
        require(!aliasExists(), "delete removes the Keystore key"); checks++;
        require(load.invoke(vault) == null, "load after delete returns no credentials"); checks++;

        checks += testLoginGuard();
        return checks;
    }

    private static int testLoginGuard() throws Exception {
        Class<?> loginClass = Class.forName("cn.lut.schedule.OfficialLogin");
        Method allowed = loginClass.getMethod("isAllowedUrl", String.class);
        require((Boolean) allowed.invoke(null, "https://jwxt.lut.edu.cn/jwapp/sys/yjsrzfwapp/dbLogin/index.do"), "official HTTPS login URL allowed");
        require(!(Boolean) allowed.invoke(null, "http://jwxt.lut.edu.cn/jwapp/sys/yjsrzfwapp/dbLogin/index.do"), "HTTP login URL blocked");
        require(!(Boolean) allowed.invoke(null, "https://attacker.example/jwapp/sys/yjsrzfwapp/dbLogin/index.do"), "foreign host blocked");
        require(!(Boolean) allowed.invoke(null, "https://jwxt.lut.edu.cn.evil.example/jwapp/sys/yjsrzfwapp/dbLogin/index.do"), "lookalike host blocked");
        require(!(Boolean) allowed.invoke(null, "https://jwxt.lut.edu.cn:444/jwapp/sys/yjsrzfwapp/dbLogin/index.do"), "nonstandard port blocked");
        require(!(Boolean) allowed.invoke(null, "https://user@jwxt.lut.edu.cn/jwapp/sys/yjsrzfwapp/dbLogin/index.do"), "URL user-info blocked");
        require(!(Boolean) allowed.invoke(null, "https://jwxt.lut.edu.cn/jwapp/sys/other/index.do"), "other official path blocked");

        Object guard = loginClass.getConstructor().newInstance();
        Method begin = loginClass.getMethod("beginForegroundFlow");
        Method claim = loginClass.getMethod("claimForegroundAttempt", String.class, boolean.class);
        String url = "https://jwxt.lut.edu.cn/jwapp/sys/yjsrzfwapp/dbLogin/index.do";
        require(!(Boolean) claim.invoke(guard, url, false), "background login attempt blocked");
        require((Boolean) claim.invoke(guard, url, true), "foreground flow claims one attempt");
        require(!(Boolean) claim.invoke(guard, url, true), "duplicate attempt in same flow blocked");
        begin.invoke(guard);
        require((Boolean) claim.invoke(guard, url, true), "new explicitly begun flow may claim once");
        return 11;
    }

    private static boolean aliasExists() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore"); store.load(null);
        return store.containsAlias("lut.schedule.credentials.v1");
    }

    private static String secret(Object credentials, String name) throws Exception {
        if (credentials == null) return null;
        Field field = credentials.getClass().getField(name);
        return (String) field.get(credentials);
    }

    private static boolean contains(byte[] value, byte[] target) {
        outer: for (int i = 0; i <= value.length - target.length; i++) {
            for (int j = 0; j < target.length; j++) if (value[i + j] != target[j]) continue outer;
            return true;
        }
        return false;
    }

    private static String repeat(char value, int count) {
        char[] chars = new char[count]; Arrays.fill(chars, value); return new String(chars);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
