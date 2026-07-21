package com.github.catvod.spider;

import android.text.TextUtils;

import com.github.catvod.spider.merge.A.l1;
import com.github.catvod.spider.merge.A.n0;
import com.github.catvod.spider.merge.A.o0;
import com.github.catvod.spider.merge.A.r1;
import com.github.catvod.spider.merge.b.k;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.Arrays;

/**
 * Fish native crypto bridge. Loads a native library from {@link InitOrigin#e} on class
 * initialization and exposes both the native routines and the Java-side wrappers that
 * guard each call with the {@link #loaded} flag, decode obfuscated strings through
 * {@link #deobf(int, int, int)} and translate thrown errors into {@link #lastError}.
 */
public class FishCrypto {

    /** Constants used to index the {@link #sv(int)} server-value table. */
    public static final class V {
        public static final int FISH_REQ       = 0x0;
        public static final int FISH_RESP      = 0x1;
        public static final int CF_KILL        = 0x2;
        public static final int CF_KILLMSG     = 0x3;
        public static final int CF_UPDATEURL   = 0x4;
        public static final int CF_CONTENT     = 0x5;
        public static final int CF_TAG         = 0x6;
        public static final int CF_TOAST       = 0x7;
        public static final int CF_PIC         = 0x8;
        public static final int CF_GOURL       = 0x9;
        public static final int CF_PT          = 0xa;
        public static final int CF_HELPURLS    = 0xb;
        public static final int CF_ICONBASE    = 0xc;
        public static final int CF_UUID        = 0xd;
        public static final int SP_NAME        = 0xe;
        public static final int SP_KEY         = 0xf;
        public static final int SP_UUID        = 0x10;
        public static final int SP_DF          = 0x11;
        public static final int CF_POPUP       = 0x12;
        public static final int PF_ENABLED     = 0x13;
        public static final int PF_ID          = 0x14;
        public static final int PF_DAY         = 0x15;
        public static final int PF_TITLE       = 0x16;
        public static final int PF_CONTENT     = 0x17;
        public static final int PF_IMAGE       = 0x18;
        public static final int PF_IMAGE_SHA256 = 0x19;
        public static final int PF_BUTTON      = 0x1a;
        public static final int PF_PAGES       = 0x1b;
        public static final int PF_BLOCKS      = 0x1c;
        public static final int PF_TYPE        = 0x1d;
        public static final int PF_TEXT        = 0x1e;
        public static final int PF_URL         = 0x1f;
        public static final int PF_DIVIDER     = 0x20;
        public static final int PF_TITLE_COLOR = 0x21;
        public static final int PF_BUTTON_COLOR = 0x22;
        public static final int SP_POPUP_READ  = 0x23;

        private V() {
        }
    }

    public static int F = 0x0;
    public static final int GUARD_BOOT      = 0x1;
    public static final int GUARD_CONFIG    = 0x2;
    public static final int GUARD_GO_PROXY  = 0x3;
    public static final int GUARD_TRANSFER  = 0x4;
    public static final int GUARD_DANMU     = 0x5;
    public static final int GUARD_WEBDAV    = 0x6;
    public static final int GUARD_WEB_JUMP  = 0x7;

    private static volatile String[] _svCache = null;
    private static volatile String lastError = "";
    private static volatile boolean loaded;

    private static final short[] SHORT_DATA_CRYPTO = new short[]{
        (short)0xaa4, (short)0xaac, (short)0xab1, (short)0xab5, (short)0xab8, (short)0xae1, (short)0xab2, (short)0xaae, (short)0xae1, (short)0xab1, (short)0xaa0, (short)0xab5,
        (short)0xaa9, (short)0x24a, (short)0x253, (short)0x258, (short)0x263, (short)0x25f, (short)0x253, (short)0x252, (short)0x248, (short)0x259, (short)0x252, (short)0x248,
        (short)0xc29, (short)0xc2c, (short)0xc36, (short)0xc31, (short)0x1b2, (short)0x1ca, (short)0x1b2, (short)0x1ca, (short)0x1b2, (short)0x1ca, (short)0x4fb, (short)0x4fb,
        (short)0x4fb, (short)0x1fd, (short)0x1fb, (short)0x4ff, (short)0x99b, (short)0x987, (short)0x987, (short)0x983, (short)0x6ae, (short)0x6af, (short)0x6bd, (short)0x6d6,
        (short)0x6c3, (short)0x93c, (short)0x926, (short)0x4ab, (short)0x4aa, (short)0x4b8, (short)0x4d3, (short)0x4c6, (short)0xc20, (short)0xc21, (short)0xc33, (short)0xc58,
        (short)0xc4d, (short)0x9d1, (short)0x9d0, (short)0x9cb, (short)0x9d6, (short)0x9dc, (short)0x9da, (short)0xa22, (short)0xa3e, (short)0xa33, (short)0xa2b, (short)0xa06,
        (short)0xa33, (short)0xa35, (short)0x95b, (short)0x940, (short)0x94e, (short)0x95c, (short)0x95b, (short)0xc1f, (short)0xc06, (short)0xc0c, (short)0x764, (short)0x76c,
        (short)0x756, (short)0x771, (short)0x76f, (short)0x303, (short)0x309, (short)0x305, (short)0x304, (short)0x328, (short)0x30b, (short)0x319, (short)0x30f, (short)0x605,
        (short)0x607, (short)0x610, (short)0x613, (short)0x61c, (short)0x60d, (short)0xc3d, (short)0xc30, (short)0xc39, (short)0xc25, (short)0x5cc, (short)0x4e9, (short)0x926,
        (short)0x295, (short)0x9f6, (short)0x9ee, (short)0x9f6, (short)0x245, (short)0x1fc, (short)0x1fc, (short)0x519, (short)0x567, (short)0x151, (short)0xaf4, (short)0xac6,
        (short)0xa6a, (short)0x540, (short)0x544, (short)0x553, (short)0x551, (short)0x54e, (short)0x545, (short)0x974, (short)0x96c, (short)0x974, (short)0x26a, (short)0x258,
        (short)0x1bf, (short)0x352, (short)0x32c, (short)0x5e7, (short)0x13b, (short)0x13b, (short)0x23c, (short)0x52d, (short)0x525, (short)0x538, (short)0x53c, (short)0x531,
        (short)0x568, (short)0x53b, (short)0x527, (short)0x568, (short)0x538, (short)0x529, (short)0x53c, (short)0x520
    };

    static {
        boolean success = false;
        try {
            String libPath = InitOrigin.e;
            if (libPath != null && !libPath.isEmpty()) {
                System.load(libPath);
                nReg();
                lastError = "";
                success = true;
            } else {
                lastError = deobf(0x0, 0xd, 0xac1);
            }
        } catch (Throwable t) {
            n0.i(null);
            lastError = describeLoadError(t);
        }
        loaded = success;
    }

    private FishCrypto() {
    }

    /** Decodes a string previously obfuscated by XOR-ing the {@code short[]} table with {@code key}. */
    private static String deobf(int offset, int length, int key) {
        char[] chars = new char[length];
        for (int i = 0; i < length; i++) {
            chars[i] = (char) (SHORT_DATA_CRYPTO[offset + i] ^ key);
        }
        return new String(chars);
    }

    private static native int _guardConfig(String cfg);

    private static native boolean _guardReady();

    private static native int _kChk(String key);

    private static native String _pan123ShareGetApi(String a, String b, String c, String d, int e, int f);

    private static native String _pan123UnlimitedUrl(String url);

    private static native byte[] _secAesCbcDec(String a, String b, byte[] data);

    private static native byte[] _secAesCbcEnc(String a, String b, byte[] data);

    private static native byte[] _secAesCbcEncJsonAccess(String a, String b, String c, String d, byte[] data);

    private static native String _secBubuSign(String a, String b, String c, String d, String e, String f, String g, String h, String i, String j, String k, String l);

    private static native String _secText(String text);

    private static native String _secYunDuoSign(String a, String b, String c, String d, String e, String f, String g, String h, String i, String j, String k, String l);

    private static native String _sv(int index);

    public static native byte[] aesDec(byte[] a, byte[] b, byte[] c);

    public static native byte[] aesEnc(byte[] a, byte[] b, byte[] c);

    public static k applyContent(k content) {
        return r1.applyContent(content);
    }

    public static String applyContent(String content) {
        if (r1.isKilled()) {
            return content;
        }
        r1.refresh();
        return r1.p(content, r1.l);
    }

    public static String applyContentR(String content) {
        String keyUrl = deobf(0xd, 0xb, 0x23c);
        String jsonArrayKey = deobf(0x18, 0x4, 0xc45);
        if (TextUtils.isEmpty(content) || r1.isKilled()) {
            return content;
        }
        r1.refresh();
        if (TextUtils.isEmpty(r1.l)) {
            return content;
        }
        try {
            JSONObject json = new JSONObject(content);
            if (json.has(jsonArrayKey)) {
                JSONArray array = json.getJSONArray(jsonArrayKey);
                if (array.length() > 0) {
                    JSONObject first = array.getJSONObject(0);
                    String replaced = r1.p(first.optString(keyUrl, ""), r1.l);
                    first.put(keyUrl, replaced);
                }
            }
            content = json.toString();
        } catch (Exception ignored) {
        }
        return content;
    }

    public static k applyNotice(k notice) {
        return r1.applyNotice(notice);
    }

    public static k applyPlayFrom(k playFrom) {
        return r1.applyPlayFrom(playFrom);
    }

    public static String applyPlayFrom(String playFrom) {
        if (r1.isKilled()) {
            return playFrom;
        }
        r1.refresh();
        if (TextUtils.isEmpty(r1.m) || TextUtils.isEmpty(playFrom)) {
            return playFrom;
        }
        String separator = deobf(0x1c, 0x6, 0x1ee);
        String[] parts = playFrom.split(separator, -1);
        parts[0] = r1.m;
        String joiner = deobf(0x22, 0x3, 0x4df);
        return TextUtils.join(joiner, parts);
    }

    public static native byte[] cKey(String a, String b);

    public static native String[] cfgUrls();

    public static native void checkAlive();

    public static String decExt(String value) {
        boolean canDecode = true;
        if (TextUtils.isEmpty(value)) {
            return value;
        }
        if (isLoaded()) {
            checkAlive();
        }
        value = value.trim();
        String prefixA = deobf(0x25, 0x2, 0x186);
        if (value.startsWith(prefixA)) {
            return value;
        }
        String prefixB = deobf(0x27, 0x4, 0x9f3);
        if (value.startsWith(prefixB)) {
            return value;
        }
        int length = value.length();
        if (length >= 0x18 && length % 4 == 0) {
            for (int i = 0; i < length; i++) {
                char c = value.charAt(i);
                boolean isUpper = c >= 'A' && c <= 'Z';
                boolean isLower = c >= 'a' && c <= 'z';
                boolean isDigit = c >= '0' && c <= '9';
                boolean isBase64 = c == '+' || c == '/' || c == '=';
                if (!isUpper && !isLower && !isDigit && !isBase64) {
                    canDecode = false;
                    break;
                }
            }
        } else {
            canDecode = false;
        }
        if (!isLoaded()) {
            return canDecode ? value : value;
        }
        try {
            byte[] decoded = extDe(value);
            if (decoded == null) {
                return canDecode ? value : value;
            }
            String charset = deobf(0x2c, 0x5, 0x6fb);
            return new String(decoded, charset);
        } catch (Exception ignored) {
            return canDecode ? value : value;
        }
    }

    public static native String defCfg();

    private static String describeLoadError(Throwable t) {
        if (t == null) {
            return "";
        }
        String message = t.getMessage();
        StringBuilder sb = new StringBuilder();
        sb.append(t.getClass().getSimpleName());
        if (message != null && message.length() != 0) {
            String separator = deobf(0x31, 0x2, 0x906);
            sb.append(separator.concat(message));
        }
        return sb.toString();
    }

    public static String encExt(String value) {
        if (!isLoaded()) {
            return "";
        }
        try {
            byte[] encoded = extEn(value);
            if (encoded == null) {
                return "";
            }
            String charset = deobf(0x33, 0x5, 0x4fe);
            return new String(encoded, charset);
        } catch (Exception ignored) {
            return "";
        }
    }

    public static String encUrl(String value) {
        if (!isLoaded()) {
            return "";
        }
        try {
            byte[] encoded = urlEn(value);
            if (encoded == null) {
                return "";
            }
            String charset = deobf(0x38, 0x5, 0xc75);
            return new String(encoded, charset);
        } catch (Exception ignored) {
            return "";
        }
    }

    public static native byte[] extDe(String value);

    public static native byte[] extEn(String value);

    public static String goUrl() {
        String url = "";
        if (TextUtils.isEmpty(url)) {
            url = r1.q;
        }
        return url;
    }

    public static synchronized boolean guard(int guardId, String payload) {
        if (!loaded) {
            return false;
        }
        if (payload == null) {
            payload = "";
        }
        try {
            long lease = guardLease(guardId, payload);
            if (lease != 0L && guardUse(guardId, lease, payload)) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    public static synchronized int guardConfig(String cfg) {
        if (!loaded || cfg == null || cfg.length() == 0) {
            return -1;
        }
        try {
            int result = _guardConfig(cfg);
            if (result != 0) {
                wipeJavaCache();
            }
            return result;
        } catch (Throwable ignored) {
            return -1;
        }
    }

    public static native long guardLease(int guardId, String payload);

    public static boolean guardReady() {
        if (!loaded) {
            return false;
        }
        try {
            return _guardReady();
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static native boolean guardUse(int guardId, long lease, String payload);

    public static String[] helpUrls() {
        return r1.r;
    }

    public static native byte[] hmac256(byte[] a, byte[] b);

    public static boolean isLoaded() {
        return loaded;
    }

    public static int kChk(String key) {
        if (!loaded) {
            return 0;
        }
        int result = _kChk(key);
        if (result != 0) {
            wipeJavaCache();
        }
        return result;
    }

    public static boolean killed() {
        return r1.isKilled();
    }

    public static String lastError() {
        return lastError == null ? "" : lastError;
    }

    public static void loadRemote(String url) {
        String data = r1.fetchUrl(url);
        if (data != null) {
            r1.loadData(-1, data);
        }
    }

    public static native void nExit();

    public static native boolean nKilled();

    private static native void nReg();

    public static void noticeInit() {
        if (r1.k) {
            return;
        }
        if (!isLoaded()) {
            return;
        }
        try {
            r1.a = spVer();
            r1.b = cfgUrls();
            int[] warmupIds = {0xe, 0xf, 0x10, 0x0, 0x1, 0x2, 0x3, 0x4, 0x5, 0x6, 0x7, 0x8, 0x9, 0xa, 0xb, 0xc, 0xd, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17, 0x18, 0x19, 0x1a, 0x1b, 0x1c, 0x1d, 0x1e, 0x1f, 0x20, 0x21, 0x22, 0x23};
            warmup(warmupIds);
            JSONObject cfg = new JSONObject(defCfg());
            String empty = "";
            r1.d = cfg.optString(deobf(0x3d, 0x6, 0x9bf), empty);
            r1.e = cfg.optString(deobf(0x43, 0x7, 0xa52), empty);
            r1.f = cfg.optString(deobf(0x4a, 0x5, 0x92f), empty);
            r1.g = cfg.optString(deobf(0x4f, 0x3, 0xc6f), empty);
            r1.h = cfg.optString(deobf(0x52, 0x5, 0x703), empty);
            r1.j = cfg.optString(deobf(0x57, 0x8, 0x36a), empty);
            cfg.optString(deobf(0x5f, 0x6, 0x675), empty);
            JSONArray arr = cfg.optJSONArray(deobf(0x65, 0x4, 0xc55));
            if (arr != null && arr.length() > 0) {
                r1.i = new String[arr.length()];
                for (int i = 0; i < arr.length(); i++) {
                    r1.i[i] = arr.getString(i);
                }
            }
            r1.l = r1.d;
            r1.m = r1.e;
            r1.n = r1.f;
            r1.o = r1.g;
            r1.q = r1.h;
            r1.r = r1.i;
            r1.s = r1.j;
            r1.k = true;
        } catch (Exception ignored) {
        }
    }

    public static void noticeLoad() {
        InitOrigin.execute(new o0(4));
    }

    public static String pan123ShareGetApi(String a, String b, String c, String d, int e, int f) {
        if (!loaded) {
            return "";
        }
        try {
            String result = _pan123ShareGetApi(a, b, c, d, e, f);
            return result == null ? "" : result;
        } catch (Throwable ignored) {
            return "";
        }
    }

    public static String pan123UnlimitedUrl(String url) {
        if (!loaded || url == null || url.length() == 0) {
            return "";
        }
        try {
            String result = _pan123UnlimitedUrl(url);
            return result == null ? "" : result;
        } catch (Throwable ignored) {
            return "";
        }
    }

    public static JSONObject panCfg() {
        r1.refresh();
        return r1.t;
    }

    public static String panIcon() {
        return r1.s;
    }

    public static String pic() {
        return r1.o;
    }

    public static native String popupAck(long popupId);

    public static native long popupArm(String a, String b);

    public static native void popupCheck();

    public static native boolean popupRequire(String a, String b, String c);

    public static native void popupShown(long popupId);

    public static native void popupViolation(long popupId);

    public static native String rPub();

    public static byte[] secAesCbcDec(String a, String b, byte[] data) {
        if (!loaded || data == null || data.length == 0) {
            return null;
        }
        try {
            return _secAesCbcDec(a, b, data);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static byte[] secAesCbcEnc(String a, String b, byte[] data) {
        if (!loaded || data == null || data.length == 0) {
            return null;
        }
        try {
            return _secAesCbcEnc(a, b, data);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static byte[] secAesCbcEncJsonAccess(String a, String b, String c, String d, byte[] data) {
        if (!loaded || data == null || data.length == 0) {
            return null;
        }
        try {
            return _secAesCbcEncJsonAccess(a, b, c, d, data);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static String secBubuSign(String a, String b, String c, String d, String e, String f, String g, String h, String i, String j, String k, String l) {
        if (!loaded) {
            return "";
        }
        try {
            String result = _secBubuSign(a, b, c, d, e, f, g, h, i, j, k, l);
            return result == null ? "" : result;
        } catch (Throwable ignored) {
            return "";
        }
    }

    public static String secText(String text) {
        if (!loaded || text == null || text.length() == 0) {
            return "";
        }
        try {
            String result = _secText(text);
            return result == null ? "" : result;
        } catch (Throwable ignored) {
            return "";
        }
    }

    public static String secYunDuoSign(String a, String b, String c, String d, String e, String f, String g, String h, String i, String j, String k, String l) {
        if (!loaded) {
            return "";
        }
        try {
            String result = _secYunDuoSign(a, b, c, d, e, f, g, h, i, j, k, l);
            return result == null ? "" : result;
        } catch (Throwable ignored) {
            return "";
        }
    }

    public static native int spVer();

    public static int spVersion() {
        return r1.a;
    }

    public static boolean ssDel(String name) {
        try {
            File file = l1.c(name);
            if (file != null && file.exists() && file.delete()) {
                return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    public static String ssExport() {
        StringBuilder sb = new StringBuilder(deobf(0x69, 0x1, 0x5b7));
        File folder = l1.d();
        String emptyMark = deobf(0x6a, 0x1, 0x494);
        if (folder == null) {
            sb.append(emptyMark);
            return l1.b(sb.toString());
        }
        File[] files = folder.listFiles();
        if (files != null) {
            boolean first = true;
            for (File file : files) {
                if (file.isFile() && file.length() > 0) {
                    String name = file.getName();
                    String decoded = l1.f(name);
                    if (decoded.isEmpty()) {
                        continue;
                    }
                    if (!first) {
                        sb.append(deobf(0x6b, 0x1, 0x90a));
                    }
                    sb.append(deobf(0x6c, 0x1, 0x2b7));
                    sb.append(name);
                    sb.append(deobf(0x6d, 0x3, 0x9d4));
                    String replaced = decoded;
                    replaced = replaced.replace(deobf(0x70, 0x1, 0x219), deobf(0x71, 0x2, 0x1a0));
                    replaced = replaced.replace(deobf(0x73, 0x2, 0x545), deobf(0x75, 0x1, 0x15b));
                    replaced = replaced.replace(deobf(0x76, 0x2, 0xaa8), deobf(0x70, 0x1, 0x219));
                    sb.append(replaced);
                    sb.append(deobf(0x6b, 0x1, 0x90a));
                    first = false;
                }
            }
        }
        sb.append(emptyMark);
        return l1.b(sb.toString());
    }

    public static File ssFile(String name) {
        return l1.c(name);
    }

    public static File ssFolder() {
        return l1.d();
    }

    public static boolean ssImport(String payload) {
        try {
            String data = l1.a(payload);
            if (data.isEmpty()) {
                return false;
            }
            String beginMark = deobf(0x78, 0x1, 0xa11);
            if (!data.startsWith(beginMark)) {
                return false;
            }
            data = data.substring(1, data.length() - 1);
            String lineSep = deobf(0x79, 0x6, 0x56c);
            String[] lines = data.split(lineSep);
            for (String line : lines) {
                String keySep = deobf(0x7f, 0x3, 0x956);
                int sepIndex = line.indexOf(keySep);
                if (sepIndex > 0) {
                    String name = line.substring(0, sepIndex);
                    String content = line.substring(sepIndex + 3, line.length() - 1);
                    content = content.replace(deobf(0x82, 0x2, 0x236), deobf(0x84, 0x1, 0x1b5));
                    content = content.replace(deobf(0x85, 0x2, 0x30e), deobf(0x87, 0x1, 0x5c5));
                    content = content.replace(deobf(0x88, 0x2, 0x167), deobf(0x8a, 0x1, 0x260));
                    l1.g(name, content);
                }
            }
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    public static native String ssKey();

    public static String ssRead(String name) {
        return l1.f(name);
    }

    public static void ssWrite(String name, String content) {
        l1.g(name, content);
    }

    public static String sv(int index) {
        String[] cache = _svCache;
        if (cache != null && index >= 0 && index < cache.length && cache[index] != null) {
            return cache[index];
        }
        String value = "";
        if (loaded) {
            String nativeValue = _sv(index);
            if (nativeValue != null) {
                value = nativeValue;
            }
        }
        if (cache != null && index >= 0 && index < cache.length) {
            cache[index] = value;
        }
        return value;
    }

    public static native int svN();

    public static String toast() {
        return r1.n;
    }

    public static synchronized boolean tryReload(String libPath) {
        if (loaded) {
            return true;
        }
        if (libPath == null || libPath.isEmpty()) {
            lastError = deobf(0x8b, 0xd, 0x548);
            return false;
        }
        try {
            System.load(libPath);
            nReg();
            loaded = true;
            lastError = "";
            return true;
        } catch (Throwable t) {
            n0.i(libPath);
            lastError = describeLoadError(t);
            return false;
        }
    }

    public static native byte[] urlDe(String value);

    public static native byte[] urlEn(String value);

    public static void warmup(int... indices) {
        if (!loaded) {
            return;
        }
        int total = svN();
        if (total <= 0) {
            return;
        }
        if (_svCache == null) {
            synchronized (FishCrypto.class) {
                if (_svCache == null) {
                    _svCache = new String[total];
                }
            }
        }
        for (int index : indices) {
            if (index >= 0 && index < total) {
                sv(index);
            }
        }
    }

    public static void warmupAll() {
        if (!loaded) {
            return;
        }
        int total = svN();
        if (total <= 0) {
            return;
        }
        if (_svCache == null) {
            synchronized (FishCrypto.class) {
                if (_svCache == null) {
                    _svCache = new String[total];
                }
            }
        }
        for (int i = 0; i < total; i++) {
            sv(i);
        }
    }

    public static void wipeJavaCache() {
        String[] cache = _svCache;
        if (cache != null) {
            Arrays.fill(cache, null);
        }
        _svCache = null;
    }
}
