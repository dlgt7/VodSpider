package com.github.catvod.spider;

import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

/**
 * Fish 加密和配置管理工具类。
 * 提供 SO 库加载、加解密、配置管理、存储操作等功能。
 */
public class FishCrypto {

    // ========== Constants ==========
    public static int currentGuardType = 0;
    public static final int GUARD_BOOT = 1;
    public static final int GUARD_CONFIG = 2;
    public static final int GUARD_GO_PROXY = 3;
    public static final int GUARD_TRANSFER = 4;
    public static final int GUARD_DANMU = 5;
    public static final int GUARD_WEBDAV = 6;
    public static final int GUARD_WEB_JUMP = 7;

    // ========== Static fields ==========
    private static volatile String[] svCache = null;
    private static volatile String lastError = "";
    private static volatile boolean loaded;

    // ========== Config inner class ==========
    public static final class Config {
        public static volatile int spVersion = 0;
        public static volatile String[] cfgUrls = null;
        public static volatile Boolean loginRequired = null;
        public static String defKillMsg = "";
        public static String defUpdateUrl = "";
        public static String defToast = "";
        public static String defPic = "";
        public static String defGoUrl = "";
        public static String[] defHelpUrls = null;
        public static String defIconBase = "";
        public static volatile boolean initialized = false;
        public static volatile String killMsg = "";
        public static volatile String playFromName = "";
        public static volatile String toastMsg = "";
        public static volatile String picUrl = "";
        public static volatile boolean popupEnabled = false;
        public static volatile String goUrl = "";
        public static volatile String[] helpUrls = null;
        public static volatile String panIconUrl = "";
        public static volatile JSONObject panConfig = null;
        public static volatile boolean popupChecked = false;
        public static volatile String uuid = "";
        public static volatile boolean killed = false;
        public static volatile boolean forceUpdate = false;
        public static volatile String killMessage = "";
        public static volatile String tagName = "";
        public static volatile String updateUrl = "";
        public static volatile String contentUrl = "";
    }

    // ========== V inner class ==========
    public static final class V {
        // Fish request/response
        public static final int FISH_REQ = 0;
        public static final int FISH_RESP = 1;

        // Config fields
        public static final int CF_KILL = 2;
        public static final int CF_KILLMSG = 3;
        public static final int CF_UPDATEURL = 4;
        public static final int CF_CONTENT = 5;
        public static final int CF_TAG = 6;
        public static final int CF_TOAST = 7;
        public static final int CF_PIC = 8;
        public static final int CF_GOURL = 9;
        public static final int CF_PT = 10;
        public static final int CF_HELPURLS = 11;
        public static final int CF_ICONBASE = 12;
        public static final int CF_UUID = 13;

        // SharedPreferences fields
        public static final int SP_NAME = 14;
        public static final int SP_KEY = 15;
        public static final int SP_UUID = 16;
        public static final int SP_DF = 17;
        public static final int SP_POPUP_READ = 35;

        // Popup fields
        public static final int PF_ENABLED = 19;
        public static final int PF_ID = 20;
        public static final int PF_DAY = 21;
        public static final int PF_TITLE = 22;
        public static final int PF_CONTENT = 23;
        public static final int PF_IMAGE = 24;
        public static final int PF_IMAGE_SHA256 = 25;
        public static final int PF_BUTTON = 26;
        public static final int PF_PAGES = 27;
        public static final int PF_BLOCKS = 28;
        public static final int PF_TYPE = 29;
        public static final int PF_TEXT = 30;
        public static final int PF_URL = 31;
        public static final int PF_DIVIDER = 32;
        public static final int PF_TITLE_COLOR = 33;
        public static final int PF_BUTTON_COLOR = 34;
    }

    // ========== Static initializer <clinit> ==========
    static {
        boolean success = false;
        try {
            String libPath = InitOrigin.libPath;
            if (libPath != null && !libPath.isEmpty()) {
                System.load(libPath);
                nReg();
                lastError = "";
                success = true;
            } else {
                lastError = "empty so path";
            }
        } catch (Throwable t) {
            String path = null;
            reportLibPath(path);
            lastError = describeLoadError(t);
        }
        loaded = success;
    }

    // ========== Constructor ==========
    public FishCrypto() {
    }

    // ========== Native method declarations ==========
    private static native int _guardConfig(String cfg);
    private static native boolean _guardReady();
    private static native int _kChk(String key);
    private static native String _pan123ShareGetApi(String shareId, String fileId, String pwd, String api, int page, int size);
    private static native String _pan123UnlimitedUrl(String url);
    private static native byte[] _secAesCbcDec(String key, String iv, byte[] input);
    private static native byte[] _secAesCbcEnc(String key, String iv, byte[] input);
    private static native byte[] _secAesCbcEncJsonAccess(String key, String iv, String path, String accessKey, byte[] input);
    private static native String _secBubuSign(String p1, String p2, String p3, String p4, String p5, String p6, String p7, String p8, String p9, String p10, String p11, String p12);
    private static native String _secText(String text);
    private static native String _secYunDuoSign(String p0, String p1, String p2, String p3, String p4, String p5, String p6, String p7, String p8, String p9, String p10, String p11);
    private static native String _sv(int index);
    public static native byte[] aesDec(byte[] key, byte[] iv, byte[] input);
    public static native byte[] aesEnc(byte[] key, byte[] iv, byte[] input);
    public static native byte[] cKey(String param1, String param2);
    public static native String[] cfgUrls();
    public static native void checkAlive();
    public static native String defCfg();
    public static native byte[] extDe(String input);
    public static native byte[] extEn(String input);
    public static native long guardLease(int guardType, String tag);
    public static native boolean guardUse(int guardType, long lease, String tag);
    public static native byte[] hmac256(byte[] key, byte[] data);
    public static native void nExit();
    public static native boolean nKilled();
    private static native void nReg();
    public static native String rPub();
    public static native long popupArm(String id, String day);
    public static native String popupAck(long id);
    public static native void popupCheck();
    public static native boolean popupRequire(String id, String day, String sha256);
    public static native void popupShown(long id);
    public static native void popupViolation(long id);
    public static native int spVer();
    public static native String ssKey();
    public static native int svN();
    public static native byte[] urlDe(String input);
    public static native byte[] urlEn(String input);

    // ========== 路径上报桩方法 ==========
    private static void reportLibPath(String path) {
        // no-op 桩方法
    }

    // ========== Public methods ==========

    public static boolean isLoaded() {
        return loaded;
    }

    public static String lastError() {
        return lastError != null ? lastError : "";
    }

    private static String describeLoadError(Throwable t) {
        String result = "";
        if (t == null) return result;
        String msg = t.getMessage();
        StringBuilder sb = new StringBuilder();
        sb.append(t.getClass().getSimpleName());
        if (msg != null && msg.length() != 0) {
            result = ": ".concat(msg);
        }
        sb.append(result);
        return sb.toString();
    }

    public static synchronized boolean guard(int guardType, String tag) {
        if (!loaded) return false;
        if (tag == null) tag = "";
        long lease = guardLease(guardType, tag);
        if (lease != 0L && guardUse(guardType, lease, tag)) {
            return true;
        }
        return false;
    }

    public static synchronized int guardConfig(String cfg) {
        if (!loaded || cfg == null || cfg.length() == 0) return -1;
        try {
            int result = _guardConfig(cfg);
            if (result != 0) wipeJavaCache();
            return result;
        } catch (Throwable t) {
            return -1;
        }
    }

    public static boolean guardReady() {
        if (!loaded) return false;
        try {
            return _guardReady();
        } catch (Throwable t) {
            return false;
        }
    }

    public static int kChk(String key) {
        if (!loaded) return 0;
        int result = _kChk(key);
        if (result != 0) wipeJavaCache();
        return result;
    }

    public static boolean killed() {
        return Config.initialized;
    }

    public static String goUrl() {
        String url = "";
        if (!TextUtils.isEmpty(url)) {
            return url;
        }
        return Config.goUrl;
    }

    public static String[] helpUrls() {
        return Config.helpUrls;
    }

    public static String pic() {
        return Config.picUrl;
    }

    public static String toast() {
        return Config.toastMsg;
    }

    public static String panIcon() {
        return Config.panIconUrl;
    }

    public static JSONObject panCfg() {
        ensureConfigLoaded();
        return Config.panConfig;
    }

    public static int spVersion() {
        return Config.spVersion;
    }

    public static void loadRemote(String url) {
        String result = fetchRemoteConfig(url);
        if (result != null) {
            storeRemoteConfig(-1, result);
        }
    }

    public static synchronized boolean tryReload(String path) {
        if (loaded) return true;
        if (path != null && !path.isEmpty()) {
            try {
                System.load(path);
                nReg();
                loaded = true;
                lastError = "";
                return true;
            } catch (Throwable t) {
                reportLibPath(path);
                lastError = describeLoadError(t);
                return false;
            }
        }
        lastError = "empty so path";
        return false;
    }

    public static void noticeInit() {
        if (Config.initialized) return;
        if (!isLoaded()) return;
        try {
            Config.spVersion = spVer();
            Config.cfgUrls = cfgUrls();

            int[] warmupIds = new int[]{
                    0xe, 0xf, 0x10, 0x0, 0x1, 0x2, 0x3, 0x4,
                    0x5, 0x6, 0x7, 0x8, 0x9, 0xa, 0xb, 0xc,
                    0xd, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17,
                    0x18, 0x19, 0x1a, 0x1b, 0x1c, 0x1d, 0x1e,
                    0x1f, 0x20, 0x21, 0x22, 0x23
            };
            warmup(warmupIds);

            JSONObject def = new JSONObject(defCfg());
            String empty = "";

            Config.defKillMsg = def.optString("notice", empty);
            Config.defUpdateUrl = def.optString("playTag", empty);
            Config.defToast = def.optString("toast", empty);
            Config.defPic = def.optString("pic", empty);
            Config.defGoUrl = def.optString("goUrl", empty);
            Config.defIconBase = def.optString("iconBase", empty);
            def.optString("prefix", empty);

            String ptKey = "help";
            JSONArray ptArr = def.optJSONArray(ptKey);
            if (ptArr != null && ptArr.length() > 0) {
                Config.defHelpUrls = new String[ptArr.length()];
                for (int idx = 0; idx < ptArr.length(); idx++) {
                    Config.defHelpUrls[idx] = ptArr.getString(idx);
                }
            }

            Config.killMsg = Config.defKillMsg;
            Config.playFromName = Config.defUpdateUrl;
            Config.toastMsg = Config.defToast;
            Config.picUrl = Config.defPic;
            Config.goUrl = Config.defGoUrl;
            Config.helpUrls = Config.defHelpUrls;
            Config.panIconUrl = Config.defIconBase;
            Config.initialized = true;
        } catch (Exception e) {
            // ignore
        }
    }

    public static void noticeLoad() {
        int unused = Config.spVersion;
        InitOrigin.execute(new NoticeLoadRunnable(4));
    }

    public static void warmup(int... ids) {
        if (!loaded) return;
        int n = svN();
        if (n <= 0) return;

        if (svCache == null) {
            synchronized (FishCrypto.class) {
                if (svCache == null) {
                    svCache = new String[n];
                }
            }
        }

        for (int id : ids) {
            if (id >= 0 && id < n) {
                sv(id);
            }
        }
    }

    public static void warmupAll() {
        if (!loaded) return;
        int n = svN();
        if (n <= 0) return;

        if (svCache == null) {
            synchronized (FishCrypto.class) {
                if (svCache == null) {
                    svCache = new String[n];
                }
            }
        }

        for (int i = 0; i < n; i++) {
            sv(i);
        }
    }

    public static String sv(int index) {
        String[] cache = svCache;
        if (cache != null && index >= 0 && index < cache.length && cache[index] != null) {
            return cache[index];
        }

        String result = "";
        if (loaded) {
            String nativeResult = _sv(index);
            if (nativeResult != null) result = nativeResult;
        }

        if (cache != null && index >= 0 && index < cache.length) {
            cache[index] = result;
        }
        return result;
    }

    public static Object applyContent(Object k) {
        return applyContentObject(k);
    }

    public static String applyContent(String content) {
        if (isInitialized()) return content;
        ensureConfigLoaded();
        return encodeWithKey(content, Config.killMsg);
    }

    public static String applyContentR(String content) {
        int unused = Config.spVersion;
        String keyName = "vod_content";
        String tagKey = "list";
        String result = content;

        if (TextUtils.isEmpty(content)) return result;
        if (isInitialized()) return result;
        ensureConfigLoaded();
        if (TextUtils.isEmpty(Config.killMsg)) return result;

        try {
            JSONObject json = new JSONObject(content);
            if (json.has(tagKey)) {
                JSONArray arr = json.getJSONArray(tagKey);
                if (arr.length() > 0) {
                    JSONObject first = arr.getJSONObject(0);
                    String val = first.optString(keyName, "");
                    String encoded = encodeWithKey(val, Config.killMsg);
                    first.put(keyName, encoded);
                }
            }
            result = json.toString();
        } catch (Exception e) {
            // ignore
        }
        return result;
    }

    public static Object applyNotice(Object k) {
        return applyNoticeObject(k);
    }

    public static Object applyPlayFrom(Object k) {
        return applyPlayFromObject(k);
    }

    public static String applyPlayFrom(String playFrom) {
        String result = playFrom;
        if (isInitialized()) return result;

        ensureConfigLoaded();
        if (TextUtils.isEmpty(Config.playFromName) || TextUtils.isEmpty(playFrom)) return result;

        String sep = "\\$\\$\\$";
        String[] parts = playFrom.split(sep, -1);
        parts[0] = Config.playFromName;
        String joiner = "$$$";
        result = TextUtils.join(joiner, parts);

        return result;
    }

    public static String encExt(String content) {
        int unused = Config.spVersion;
        String result = "";
        if (!isLoaded()) return result;
        try {
            byte[] encrypted = extEn(content);
            if (encrypted == null) return result;
            String charset = "UTF-8";
            result = new String(encrypted, charset);
        } catch (Throwable t) {
            // ignore
        }
        return result;
    }

    public static String encUrl(String content) {
        int unused = Config.spVersion;
        String result = "";
        if (!isLoaded()) return result;
        try {
            byte[] encrypted = urlEn(content);
            if (encrypted == null) return result;
            String charset = "UTF-8";
            result = new String(encrypted, charset);
        } catch (Throwable t) {
            // ignore
        }
        return result;
    }

    public static String decExt(String content) {
        int unused = Config.spVersion;
        String emptyCheck = "{}";
        boolean isBase64 = true;
        String result = emptyCheck;

        if (TextUtils.isEmpty(content)) return result;

        if (isLoaded()) checkAlive();

        content = content.trim();
        String prefix1 = "{";

        if (!content.startsWith(prefix1)) {
            String prefix2 = "http";
            if (!content.startsWith(prefix2)) {
                // Validate base64
                if (content.length() >= 24 && content.length() % 4 == 0) {
                    for (int i = 0; i < content.length(); i++) {
                        char c = content.charAt(i);
                        if (!((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') ||
                                (c >= '0' && c <= '9') || c == '+' || c == '/' || c == '=')) {
                            isBase64 = false;
                            break;
                        }
                    }
                } else {
                    isBase64 = false;
                }

                if (!isLoaded()) {
                    if (isBase64) return result;
                    return content;
                }

                try {
                    byte[] decoded = extDe(content);
                    if (decoded == null) {
                        if (isBase64) return result;
                        return content;
                    }
                    String charset = "UTF-8";
                    result = new String(decoded, charset);
                    return result;
                } catch (Throwable t) {
                    if (isBase64) return result;
                    return content;
                }
            }
        }

        return content;
    }

    public static byte[] secAesCbcDec(String key, String iv, byte[] input) {
        if (loaded && input != null && input.length != 0) {
            try {
                return _secAesCbcDec(key, iv, input);
            } catch (Throwable t) {
                // ignore
            }
        }
        return null;
    }

    public static byte[] secAesCbcEnc(String key, String iv, byte[] input) {
        if (loaded && input != null && input.length != 0) {
            try {
                return _secAesCbcEnc(key, iv, input);
            } catch (Throwable t) {
                // ignore
            }
        }
        return null;
    }

    public static byte[] secAesCbcEncJsonAccess(String key, String iv, String path, String accessKey, byte[] input) {
        if (loaded && input != null && input.length != 0) {
            try {
                return _secAesCbcEncJsonAccess(key, iv, path, accessKey, input);
            } catch (Throwable t) {
                // ignore
            }
        }
        return null;
    }

    public static String secBubuSign(String p1, String p2, String p3, String p4, String p5, String p6, String p7, String p8, String p9, String p10, String p11, String p12) {
        String result = "";
        if (!loaded) return result;
        try {
            String nativeResult = _secBubuSign(p1, p2, p3, p4, p5, p6, p7, p8, p9, p10, p11, p12);
            if (nativeResult != null) result = nativeResult;
        } catch (Throwable t) {
            // ignore
        }
        return result;
    }

    public static String secText(String text) {
        String result = "";
        if (loaded && text != null && text.length() != 0) {
            try {
                String nativeResult = _secText(text);
                if (nativeResult != null) result = nativeResult;
            } catch (Throwable t) {
                // ignore
            }
        }
        return result;
    }

    public static String secYunDuoSign(String p0, String p1, String p2, String p3, String p4, String p5, String p6, String p7, String p8, String p9, String p10, String p11) {
        String result = "";
        if (!loaded) return result;
        try {
            String nativeResult = _secYunDuoSign(p0, p1, p2, p3, p4, p5, p6, p7, p8, p9, p10, p11);
            if (nativeResult != null) result = nativeResult;
        } catch (Throwable t) {
            // ignore
        }
        return result;
    }

    public static String pan123ShareGetApi(String shareId, String fileId, String pwd, String api, int page, int size) {
        String result = "";
        if (!loaded) return result;
        try {
            String nativeResult = _pan123ShareGetApi(shareId, fileId, pwd, api, page, size);
            if (nativeResult != null) result = nativeResult;
        } catch (Throwable t) {
            // ignore
        }
        return result;
    }

    public static String pan123UnlimitedUrl(String url) {
        String result = "";
        if (loaded && url != null && url.length() != 0) {
            try {
                String nativeResult = _pan123UnlimitedUrl(url);
                if (nativeResult != null) result = nativeResult;
            } catch (Throwable t) {
                // ignore
            }
        }
        return result;
    }

    public static boolean ssDel(String name) {
        boolean result = false;
        try {
            File file = getFileByName(name);
            if (file != null && file.exists() && file.delete()) {
                result = true;
            }
        } catch (Exception e) {
            // ignore
        }
        return result;
    }

    public static String ssExport() {
        StringBuilder sb = new StringBuilder("{");
        File folder = getStorageFolder();
        String closingBracket = "}";

        if (folder == null) {
            sb.append(closingBracket);
            return encodeExportData(sb.toString());
        }

        File[] files = folder.listFiles();
        if (files != null) {
            String newline = ",";
            String quote = "\"";
            String colonQuote = "\":\"";
            String backslash = "\\";
            String slash = "\\\\";
            String escapedQuote = "\\\"";
            String escapedNewline = "\n";
            String literalNewline = "\\n";

            boolean first = true;
            for (File f : files) {
                if (!f.isFile() || f.length() <= 0) continue;
                String name = f.getName();
                String data = readFileContent(name);
                if (data.isEmpty()) continue;

                if (!first) sb.append(newline);
                sb.append(quote);
                sb.append(name);
                sb.append(colonQuote);
                data = data.replace(backslash, slash);
                data = data.replace(quote, escapedQuote);
                data = data.replace(escapedNewline, literalNewline);
                sb.append(data);
                sb.append(quote);
                first = false;
            }
        }

        sb.append(closingBracket);
        return encodeExportData(sb.toString());
    }

    public static File ssFile(String name) {
        return getFileByName(name);
    }

    public static File ssFolder() {
        return getStorageFolder();
    }

    public static boolean ssImport(String data) {
        boolean result = false;
        try {
            String decoded = decodeImportData(data);
            if (decoded.isEmpty()) return result;

            String bracketStart = "{";
            if (!decoded.startsWith(bracketStart)) return result;

            decoded = decoded.substring(1, decoded.length() - 1);
            String entrySep = ",(?=\\\")";
            String[] entries = decoded.split(entrySep);

            for (String entry : entries) {
                try {
                    String colonQuote = "\":\"";
                    int sepIdx = entry.indexOf(colonQuote);
                    if (sepIdx <= 0) continue;

                    String name = entry.substring(1, sepIdx);
                    int valStart = sepIdx + 3;
                    int valEnd = entry.length() - 1;
                    String val = entry.substring(valStart, valEnd);

                    String slash = "\\n";
                    String backslash = "\n";
                    val = val.replace(slash, backslash);

                    String entrySep2 = "\\\"";
                    String newline = "\"";
                    val = val.replace(entrySep2, newline);

                    String literalNewline = "\\\\";
                    String escapedNewline = "\\";
                    val = val.replace(literalNewline, escapedNewline);

                    writeFileContent(name, val);
                } catch (Exception e) {
                    // continue
                }
            }
            result = true;
        } catch (Exception e) {
            // ignore
        }
        return result;
    }

    public static String ssRead(String name) {
        return readFileContent(name);
    }

    public static void ssWrite(String name, String data) {
        writeFileContent(name, data);
    }

    public static void wipeJavaCache() {
        String[] cache = svCache;
        if (cache != null) {
            for (int i = 0; i < cache.length; i++) {
                cache[i] = null;
            }
        }
        svCache = null;
    }

    // ========== r1 桩方法 ==========

    private static boolean isInitialized() {
        return Config.initialized;
    }

    private static void ensureConfigLoaded() {
        // 确保配置已加载
    }

    private static Object applyContentObject(Object obj) {
        // 应用内容对象
        return obj;
    }

    private static Object applyNoticeObject(Object obj) {
        // 应用通知对象
        return obj;
    }

    private static Object applyPlayFromObject(Object obj) {
        // 应用播放来源对象
        return obj;
    }

    private static String encodeWithKey(String content, String key) {
        // 使用密钥编码内容
        return content;
    }

    private static void storeRemoteConfig(int type, String url) {
        // 存储远程配置
    }

    private static String fetchRemoteConfig(String url) {
        // 获取远程配置
        return null;
    }

    // ========== 文件存储桩方法 ==========

    private static File getFileByName(String name) {
        // 根据名称获取文件
        File folder = getStorageFolder();
        if (folder == null) return null;
        return new File(folder, name);
    }

    private static File getStorageFolder() {
        // 获取存储目录
        return null;
    }

    private static String readFileContent(String name) {
        // 读取文件内容
        File file = getFileByName(name);
        if (file == null || !file.exists()) return "";
        try {
            FileInputStream fis = new FileInputStream(file);
            byte[] data = new byte[(int) file.length()];
            fis.read(data);
            fis.close();
            return new String(data, "UTF-8");
        } catch (Exception e) {
            return "";
        }
    }

    private static void writeFileContent(String name, String content) {
        // 写入文件内容
        File file = getFileByName(name);
        if (file == null) return;
        try {
            FileOutputStream fos = new FileOutputStream(file);
            fos.write(content.getBytes("UTF-8"));
            fos.close();
        } catch (Exception e) {
            // ignore
        }
    }

    private static String decodeImportData(String data) {
        // 解码导入数据
        return data;
    }

    private static String encodeExportData(String data) {
        // 编码导出数据
        return data;
    }

    // ========== Inner Runnable for noticeLoad ==========

    private static final class NoticeLoadRunnable implements Runnable {
        private final int type;

        NoticeLoadRunnable(int type) {
            this.type = type;
        }

        @Override
        public void run() {
            noticeInit();
        }
    }
}
