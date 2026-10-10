package cn.lut.schedule;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.Key;
import java.security.KeyStore;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Stores optional credentials encrypted at rest with an Android Keystore key. */
public final class CredentialVault {
    private static final String KEY_ALIAS = "lut.schedule.credentials.v1";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int TAG_BITS = 128;
    private static final int MAGIC = 0x4c555456; // LUTV
    private static final int MAX_USERNAME_BYTES = 200;
    private static final int MAX_PASSWORD_BYTES = 1024;
    private static final int MAX_VAULT_BYTES = 16 * 1024;
    private static final Object LOCK = new Object();
    private final File file;

    public CredentialVault(Context context) {
        Context app = context.getApplicationContext();
        this.file = new File(app.getFilesDir(), "credentials.vault");
    }

    /** Returns false only when saving fails; secrets are never logged or persisted in preferences. */
    public boolean save(String username, String password) {
        synchronized (LOCK) { return saveLocked(username, password); }
    }

    private boolean saveLocked(String username, String password) {
        if (username == null || password == null || username.isEmpty() || password.isEmpty()) return false;
        byte[] plain = null, u = null, p = null;
        File temp = new File(file.getParentFile(), file.getName() + ".tmp");
        try {
            u = username.getBytes("UTF-8"); p = password.getBytes("UTF-8");
            if (u.length == 0 || u.length > MAX_USERNAME_BYTES || p.length == 0 || p.length > MAX_PASSWORD_BYTES) return false;
            plain = new byte[8 + u.length + p.length];
            putInt(plain, 0, MAGIC); putInt(plain, 4, u.length);
            System.arraycopy(u, 0, plain, 8, u.length);
            System.arraycopy(p, 0, plain, 8 + u.length, p.length);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey());
            byte[] iv = cipher.getIV(), encrypted = cipher.doFinal(plain);
            try (FileOutputStream out = new FileOutputStream(temp)) {
                out.write(iv.length); out.write(iv); out.write(encrypted); out.getFD().sync();
            }
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (Exception e) {
            temp.delete();
            recoverInvalidKey(e);
            return false;
        } finally {
            if (plain != null) Arrays.fill(plain, (byte) 0);
            if (u != null) Arrays.fill(u, (byte) 0);
            if (p != null) Arrays.fill(p, (byte) 0);
        }
    }

    /** Reads and decrypts credentials, or returns null when none are saved/usable. */
    public Credentials load() {
        synchronized (LOCK) { return loadLocked(); }
    }

    private Credentials loadLocked() {
        if (!file.isFile()) return null;
        byte[] blob = null, plain = null;
        try {
            long size = file.length();
            if (size < 1 + 12 + 16 || size > MAX_VAULT_BYTES) throw new IllegalStateException("vault size invalid");
            blob = Files.readAllBytes(file.toPath());
            if (blob.length < 1 + 12 + 16 || blob.length > MAX_VAULT_BYTES) throw new IllegalStateException("vault data invalid");
            int ivLength = blob[0] & 255;
            if (ivLength < 12 || ivLength > 16 || blob.length <= 1 + ivLength) throw new IllegalStateException("vault data invalid");
            byte[] iv = Arrays.copyOfRange(blob, 1, 1 + ivLength);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, getExistingKey(), new GCMParameterSpec(TAG_BITS, iv));
            plain = cipher.doFinal(blob, 1 + ivLength, blob.length - 1 - ivLength);
            if (plain.length < 8 || getInt(plain, 0) != MAGIC) throw new IllegalStateException("vault payload invalid");
            int n = getInt(plain, 4);
            if (n <= 0 || n > MAX_USERNAME_BYTES || n > plain.length - 8
                    || plain.length - 8 - n <= 0 || plain.length - 8 - n > MAX_PASSWORD_BYTES) throw new IllegalStateException("vault payload invalid");
            String username = new String(plain, 8, n, "UTF-8");
            String password = new String(plain, 8 + n, plain.length - 8 - n, "UTF-8");
            return new Credentials(username, password);
        } catch (Exception e) {
            delete(); // Includes Keystore key invalidation and corrupt ciphertext recovery.
            return null;
        } finally {
            if (plain != null) Arrays.fill(plain, (byte) 0);
            if (blob != null) Arrays.fill(blob, (byte) 0);
        }
    }

    public boolean hasCredentials() { synchronized (LOCK) { return file.isFile(); } }

    /** Removes ciphertext and the Keystore key immediately; false means deletion could not be verified. */
    public boolean delete() {
        synchronized (LOCK) { return deleteLocked(); }
    }

    private boolean deleteLocked() {
        File temp = new File(file.getParentFile(), file.getName() + ".tmp");
        if (file.exists() && !file.delete()) return false;
        if (temp.exists() && !temp.delete()) return false;
        try {
            KeyStore ks = KeyStore.getInstance("AndroidKeyStore"); ks.load(null);
            if (ks.containsAlias(KEY_ALIAS)) ks.deleteEntry(KEY_ALIAS);
            ks.load(null);
            return !file.exists() && !temp.exists() && !ks.containsAlias(KEY_ALIAS);
        } catch (Exception e) { return false; }
    }

    private static SecretKey getOrCreateKey() throws Exception {
        Key existing = getKey(false);
        if (existing instanceof SecretKey) return (SecretKey) existing;
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true).build());
        return generator.generateKey();
    }

    private static SecretKey getExistingKey() throws Exception {
        Key key = getKey(true);
        if (!(key instanceof SecretKey)) throw new IllegalStateException("vault key missing");
        return (SecretKey) key;
    }

    private static Key getKey(boolean required) throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore"); ks.load(null);
        Key key = ks.getKey(KEY_ALIAS, null);
        if (required && key == null) throw new IllegalStateException("vault key missing");
        return key;
    }

    private void recoverInvalidKey(Exception e) {
        String name = e.getClass().getName();
        if (name.contains("KeyPermanentlyInvalidated") || name.contains("UnrecoverableKey")) delete();
    }
    private static void putInt(byte[] b, int at, int v) { b[at]=(byte)(v>>>24); b[at+1]=(byte)(v>>>16); b[at+2]=(byte)(v>>>8); b[at+3]=(byte)v; }
    private static int getInt(byte[] b, int at) { return ((b[at]&255)<<24)|((b[at+1]&255)<<16)|((b[at+2]&255)<<8)|(b[at+3]&255); }

    public static final class Credentials {
        public final String username;
        public final String password;
        Credentials(String username, String password) { this.username=username; this.password=password; }
    }
}
