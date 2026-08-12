package com.github.catvod.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.TypedValue;
import android.webkit.WebSettings;
import android.webkit.WebView;

import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.spider.Init;
import com.github.catvod.net.OkHttp;

import org.json.JSONObject;

import java.net.URLEncoder;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * JavaScript key 获取工具（用于 WebView 验证码）
 */
final class HttpWebView {

    /**
     * 创建并配置 WebView（必须在主线程调用）
     * 自动将调用切换到主线程，避免在异步线程创建 WebView 导致崩溃
     */
    public static WebView createWebView(Context context) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            return buildWebView(context);
        }
        // 非主线程：提交到主线程执行，带超时防止永久阻塞
        Handler mainHandler = new Handler(Looper.getMainLooper());
        final WebView[] result = {null};
        final CountDownLatch latch = new CountDownLatch(1);
        final boolean[] isTimedOut = {false};
        boolean posted = mainHandler.post(() -> {
            if (isTimedOut[0]) return;
            try {
                result[0] = buildWebView(context);
            } catch (Exception e) {
                SpiderDebug.log(e);
            } finally {
                latch.countDown();
            }
        });
        if (!posted) return null;
        try {
            if (!latch.await(3, TimeUnit.SECONDS)) {
                isTimedOut[0] = true;
                SpiderDebug.log("HttpWebView.createWebView 主线程等待超时");
                return null;
            }
        } catch (InterruptedException e) {
            isTimedOut[0] = true;
            Thread.currentThread().interrupt();
            return null;
        }
        return result[0];
    }

    private static WebView buildWebView(Context context) {
        Context appContext = context.getApplicationContext() != null ? context.getApplicationContext() : context;
        WebView webView = new WebView(appContext);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setTextZoom(100);
        webView.setHorizontalScrollBarEnabled(false);
        return webView;
    }
}

/**
 * 显示尺寸转换工具
 */
final class DisplayUtils {

    /**
     * 将 DP 转换为 PX（像素）
     */
    public static int dpToPx(int dp) {
        Context context = Init.context();
        if (context == null) return dp;
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp,
                context.getResources().getDisplayMetrics());
    }

    /**
     * 将 PX 转换为 DP
     */
    public static int pxToDp(int px) {
        Context context = Init.context();
        if (context == null) return px;
        return (int) (px / context.getResources().getDisplayMetrics().density);
    }
}

/**
 * 通用滑块验证工具类
 * 支持多种验证类型：滑动验证、点选验证等
 * 支持外部验证服务（ddddocr等）
 * 支持Cookie缓存管理
 */
public class SliderVerifyUtils {

    // ==================== 常量定义 ====================

    /**
     * 验证流程专属IO超时（毫秒）：防止锁内网络挂起阻塞整个爬虫池
     */
    private static final long VERIFY_IO_TIMEOUT_MS = 5000;

    /**
     * 默认缓存TTL：30分钟
     */
    private static final long DEFAULT_CACHE_TTL = 30 * 60 * 1000;
    
    /**
     * 默认SharedPreferences文件名
     */
    private static final String DEFAULT_PREFS_NAME = "slider_verify_cache";
    
    /**
     * 默认缓存键
     */
    private static final String DEFAULT_CACHE_KEY = "verify_cookie";
    
    /**
     * PC User-Agent
     */
    private static final String PC_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    /**
     * 移动端 User-Agent
     */
    private static final String MOBILE_UA = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1";
    
    // ==================== 验证类型 ====================
    
    /**
     * 验证类型枚举
     */
    public enum VerifyType {
        SLIDER("slider", "滑动验证"),
        CLICK("click", "点选验证"),
        SLIDER_TVB("tvb_huadong", "TVB滑动验证"),
        UNKNOWN("unknown", "未知类型");
        
        private final String code;
        private final String desc;
        
        VerifyType(String code, String desc) {
            this.code = code;
            this.desc = desc;
        }
        
        public String getCode() {
            return code;
        }
        
        public String getDesc() {
            return desc;
        }
        
        public static VerifyType fromCode(String code) {
            for (VerifyType type : values()) {
                if (type.code.equals(code)) {
                    return type;
                }
            }
            return UNKNOWN;
        }
    }
    
    // ==================== 实例字段 ====================
    
    private final String siteUrl;
    private final SharedPreferences sharedPreferences;
    private final String cacheKey;
    private final long cacheTTL;
    private final HashMap<String, String> defaultHeaders;
    
    private volatile String verifyCookie = "";
    private volatile long verifiedAt = 0;
    private volatile boolean isVerifying = false; // 标记是否有线程正在执行验证，防止并发击穿
    private final ReentrantLock verifyLock = new ReentrantLock();
    private String ddddOcrApi = "";
    private String jsKeyUrl = ""; // JS验证码key接口地址，为空则不主动获取
    private VerifyType verifyType = VerifyType.SLIDER_TVB;
    private boolean enableCache = true;
    private WebView captchaWebView; // 验证码弹窗专用 WebView，需手动销毁
    
    // ==================== 构造方法 ====================
    
    /**
     * 简单构造方法（使用默认配置）
     * @param siteUrl 站点URL
     */
    public SliderVerifyUtils(String siteUrl) {
        this(siteUrl, null, null);
    }
    
    /**
     * 完整构造方法
     * @param siteUrl 站点URL
     * @param context Android Context（用于缓存，可为null）
     * @param extend 扩展配置（JSON字符串或ddddocr API地址）
     */
    public SliderVerifyUtils(String siteUrl, Context context, String extend) {
        this(siteUrl, context, extend, DEFAULT_PREFS_NAME, DEFAULT_CACHE_KEY, DEFAULT_CACHE_TTL);
    }
    
    /**
     * 完全自定义构造方法
     * @param siteUrl 站点URL
     * @param context Android Context
     * @param extend 扩展配置
     * @param prefsName SharedPreferences文件名
     * @param cacheKey 缓存键名
     * @param cacheTTL 缓存TTL（毫秒）
     */
    public SliderVerifyUtils(String siteUrl, Context context, String extend, 
                            String prefsName, String cacheKey, long cacheTTL) {
        // 规范化站点URL（去除尾部斜杠）
        this.siteUrl = siteUrl.endsWith("/") ? siteUrl.substring(0, siteUrl.length() - 1) : siteUrl;
        
        // 初始化SharedPreferences
        if (context != null) {
            this.sharedPreferences = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE);
        } else {
            this.sharedPreferences = null;
        }
        
        this.cacheKey = cacheKey;
        this.cacheTTL = cacheTTL;
        
        // 初始化默认请求头
        this.defaultHeaders = new HashMap<>();
        defaultHeaders.put("User-Agent", PC_UA);
        
        // 解析扩展配置
        parseExtendConfig(extend);
        
        // 加载缓存
        loadCache();
    }
    
    // ==================== 配置方法 ====================
    
    /**
     * 解析扩展配置
     */
    private void parseExtendConfig(String extend) {
        if (TextUtils.isEmpty(extend)) return;

        try {
            if (extend.startsWith("{")) {
                JSONObject json = new JSONObject(extend);

                // ddddocr API地址
                if (json.has("ddddocr_api")) {
                    ddddOcrApi = json.getString("ddddocr_api").replaceAll("/$", "");
                    SpiderDebug.log("设置ddddocr API: " + ddddOcrApi);
                }

                // 验证类型（明确处理）
                if (json.has("verify_type")) {
                    String typeCode = json.getString("verify_type");
                    verifyType = VerifyType.fromCode(typeCode);
                    SpiderDebug.log("设置验证类型: " + verifyType.getDesc());
                }

                // 是否启用缓存（明确处理，默认true）
                if (json.has("enable_cache")) {
                    enableCache = json.optBoolean("enable_cache", true);
                    SpiderDebug.log("启用缓存: " + enableCache);
                }

                // 自定义User-Agent
                if (json.has("user_agent")) {
                    defaultHeaders.put("User-Agent", json.getString("user_agent"));
                }

                // JS验证码key接口地址
                if (json.has("js_key_url")) {
                    jsKeyUrl = json.getString("js_key_url").replaceAll("/$", "");
                    SpiderDebug.log("设置js_key_url: " + jsKeyUrl);
                }

            } else {
                // 直接作为 ddddocr API 地址（必须 http(s) 开头才采纳）
                if (extend.startsWith("http://") || extend.startsWith("https://")) {
                    ddddOcrApi = extend.replaceAll("/$", "");
                    SpiderDebug.log("设置ddddocr API: " + ddddOcrApi);
                } else {
                    SpiderDebug.log("略过非法的扩展打码配置: " + extend);
                }
            }
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
    }
    
    /**
     * 设置验证类型
     */
    public void setVerifyType(VerifyType type) {
        this.verifyType = type;
    }
    
    /**
     * 设置ddddocr API地址
     */
    public void setDdddOcrApi(String api) {
        this.ddddOcrApi = api != null ? api.replaceAll("/$", "") : "";
    }
    
    /**
     * 设置是否启用缓存
     */
    public void setEnableCache(boolean enable) {
        this.enableCache = enable;
    }
    
    /**
     * 添加自定义请求头
     */
    public void addHeader(String key, String value) {
        defaultHeaders.put(key, value);
    }
    
    // ==================== 缓存管理 ====================
    
    /**
     * 加载缓存
     */
    private void loadCache() {
        if (!enableCache) return;
        
        // 先检查内存缓存
        if (!TextUtils.isEmpty(verifyCookie) && System.currentTimeMillis() - verifiedAt < cacheTTL) {
            return;
        }
        
        if (sharedPreferences == null) return;
        
        try {
            String cached = sharedPreferences.getString(cacheKey, "");
            if (TextUtils.isEmpty(cached)) return;

            int sepIdx = cached.indexOf('\u0001');
            if (sepIdx > 0) {
                String cookie = cached.substring(0, sepIdx);
                long timestamp = Long.parseLong(cached.substring(sepIdx + 1));

                if (!TextUtils.isEmpty(cookie) && System.currentTimeMillis() - timestamp < cacheTTL) {
                    verifyCookie = cookie;
                    verifiedAt = timestamp;
                }
            }
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
    }
    
    /**
     * 保存缓存
     */
    private void saveCache() {
        verifiedAt = System.currentTimeMillis();

        if (!enableCache || sharedPreferences == null) return;

        try {
            // 使用 commit() 替代 apply()，确保高并发下磁盘落盘数据的绝对可见性
            sharedPreferences.edit()
                    .putString(cacheKey, verifyCookie + '\u0001' + verifiedAt)
                    .commit();
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
    }

    /**
     * 清除缓存
     */
    public void clearCache() {
        verifyCookie = "";
        verifiedAt = 0;

        if (sharedPreferences != null) {
            try {
                sharedPreferences.edit().remove(cacheKey).commit();
            } catch (Exception e) {
                SpiderDebug.log(e);
            }
        }
    }
    
    /**
     * 设置验证码弹窗 WebView（调用方负责生命周期管理）
     */
    public void setCaptchaWebView(WebView webView) {
        destroyWebView();
        this.captchaWebView = webView;
    }

    /**
     * 销毁验证码弹窗 WebView，释放内存（防止 OOM）
     */
    /**
     * 销毁验证码弹窗 WebView，断开父联并释放内存（防止 OOM 与内存泄漏）
     */
    public void destroyWebView() {
        if (captchaWebView != null) {
            try {
                // 1. 安全从父布局移除，切断 View 树引用
                if (captchaWebView.getParent() != null) {
                    ((android.view.ViewGroup) captchaWebView.getParent()).removeView(captchaWebView);
                }
                // 2. 清除历史与缓存
                captchaWebView.clearHistory();
                captchaWebView.clearCache(true);
                captchaWebView.loadUrl("about:blank");
                // 3. 彻底销毁
                captchaWebView.destroy();
            } catch (Exception e) {
                SpiderDebug.log(e);
            }
            captchaWebView = null;
        }
    }

    /**
     * 获取当前验证Cookie
     */
    public String getVerifyCookie() {
        return verifyCookie;
    }
    
    // ==================== 核心验证方法 ====================
    
    /**
     * 检测是否为验证页面
     */
    public boolean isVerifyPage(String html) {
        if (TextUtils.isEmpty(html)) return false;
        
        // 精确特征：huadong JS、验证关键词或验证码脚本
        return html.contains("huadong_") ||
               html.contains("yanzheng_huadong.php") ||
               html.contains("滑动验证") || html.contains("滑块验证") ||
               html.contains("人机验证") || html.contains("安全验证") ||
               html.contains("captcha") || html.contains("click_captcha");
    }
    
    /**
     * 检测验证类型（更智能）
     */
    public VerifyType detectVerifyType(String html) {
        if (TextUtils.isEmpty(html)) return VerifyType.UNKNOWN;

        // 优先检测特定类型
        if (html.contains("huadong_") || html.contains("yanzheng_huadong.php")) {
            return VerifyType.SLIDER_TVB;
        }

        // 检测点选验证（优先级高于滑动）
        if (html.contains("点选验证") || html.contains("点击验证") || html.contains("click_captcha")) {
            return VerifyType.CLICK;
        }

        // 检测滑动验证
        if (html.contains("滑动验证") || html.contains("滑块验证") || html.contains("slider") || html.contains("captcha")) {
            return VerifyType.SLIDER;
        }

        return VerifyType.SLIDER; // 默认滑动验证
    }
    
    /**
     * 带验证的请求（简单版本）
     */
    public String requestWithVerify(String url) {
        return requestWithVerify(url, null);
    }
    
    /**
     * 带验证的请求（完整版本）
     * 使用双重检查锁定：先无锁发请求，仅在检测到验证页面时才加锁执行验证，避免阻塞正常请求。
     */
    public String requestWithVerify(String url, Map<String, String> extraHeaders) {
        // 第一阶段：无锁发送请求，提高并发性能
        HashMap<String, String> headers = new HashMap<>(defaultHeaders);
        if (extraHeaders != null) {
            headers.putAll(extraHeaders);
        }
        if (!TextUtils.isEmpty(verifyCookie)) {
            headers.put("Cookie", verifyCookie);
        }
        String content = OkHttp.string(url, headers);

        // 无锁快速路径：不需要验证则直接返回
        if (!isVerifyPage(content)) {
            return content;
        }

        // 锁外前置判定：若有线程正在锁内打码，短暂自旋等待其完成，避免拿旧 Cookie 返回验证页给业务层
        int spinRetries = 5;
        while (isVerifying && spinRetries > 0) {
            try {
                TimeUnit.MILLISECONDS.sleep(300);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            spinRetries--;
            // 若打码线程已完成且 Cookie 有效，直接用新 Cookie 快速通关
            if (!isVerifying && !TextUtils.isEmpty(verifyCookie)) {
                headers.put("Cookie", verifyCookie);
                content = OkHttp.string(url, headers);
                if (!isVerifyPage(content)) {
                    return content;
                }
            }
        }

        // 第二阶段：检测到验证页面，加锁执行验证（原子操作）
        verifyLock.lock();
        try {
            // 双重检查：其他线程可能已完成验证，缓存了有效 Cookie
            if (!TextUtils.isEmpty(verifyCookie) && System.currentTimeMillis() - verifiedAt < cacheTTL) {
                headers.put("Cookie", verifyCookie);
                content = OkHttp.string(url, headers);
                if (!isVerifyPage(content)) {
                    return content;
                }
            } else {
                // 加载最新缓存
                loadCache();
                // 补一层检查：若从磁盘捞到的是有效 Cookie，直接重试，避免重复验证
                if (!TextUtils.isEmpty(verifyCookie) && System.currentTimeMillis() - verifiedAt < cacheTTL) {
                    headers.put("Cookie", verifyCookie);
                    content = OkHttp.string(url, headers);
                    if (!isVerifyPage(content)) {
                        return content;
                    }
                }
            }

            // 🔥 双重检查彻底失败，确定需要打码，再对外宣示状态
            isVerifying = true;

            VerifyType detectedType = detectVerifyType(content);
            SpiderDebug.log("检测到验证页面，类型：" + detectedType.getDesc());

            boolean success = passVerify(content, detectedType);

            if (success) {
                saveCache();

                // 重试请求
                headers.put("Cookie", verifyCookie);
                content = OkHttp.string(url, headers);

                // 再次检测（防止验证失败）
                if (isVerifyPage(content)) {
                    clearCache();
                    SpiderDebug.log("验证后仍为验证页面，已清除缓存");
                }
            } else {
                clearCache();
                SpiderDebug.log("验证失败，已清除缓存");
            }

            return content;

        } catch (Exception e) {
            SpiderDebug.log(e);
            return "";
        } finally {
            isVerifying = false; // 确保标志位一定被复位，防止死锁
            verifyLock.unlock();
        }
    }
    
    /**
     * 执行验证（根据类型自动选择）
     */
    private boolean passVerify(String html, VerifyType type) {
        // 优先尝试外部验证服务
        if (tryExternalVerify(html, type)) {
            SpiderDebug.log("外部验证服务成功");
            return true;
        }
        
        // 根据类型执行本地验证
        switch (type) {
            case SLIDER_TVB:
                return passTvbSliderVerify(html);
            case SLIDER:
                return passSliderVerify(html);
            case CLICK:
                return passClickVerify(html);
            default:
                return false;
        }
    }
    
    // ==================== TVB滑动验证 ====================

    /**
     * TVB滑动验证（针对huadong_*.js）
     * 企业级重构：彻底规避隐式 NPE 及 OkHttp 物理连接泄漏
     */
    private boolean passTvbSliderVerify(String html) {
        try {
            // 提取huadong_*.js脚本路径
            Pattern scriptPattern = Pattern.compile("src=[\"']([^\"']*huadong_[^\"']+\\.js\\?id=\\d+)[\"']");
            Matcher scriptMatcher = scriptPattern.matcher(html);
            if (!scriptMatcher.find()) {
                SpiderDebug.log("未找到huadong脚本路径");
                return false;
            }

            String scriptPath = scriptMatcher.group(1);
            String scriptUrl = scriptPath.startsWith("http") ? scriptPath : siteUrl + scriptPath;

            // 全程使用局部变量，验证成功后才刷入全局，避免半成品状态污染其他线程
            String localCookie = this.verifyCookie;

            // ================== 交互 1：获取 JS 脚本内容 ==================
            HashMap<String, String> jsHeaders = new HashMap<>(defaultHeaders);
            if (!TextUtils.isEmpty(localCookie)) {
                jsHeaders.put("Cookie", localCookie);
            }
            jsHeaders.put("Referer", siteUrl + "/");

            String jsContent = "";
            Response jsResponse = executeRequest(scriptUrl, jsHeaders);
            if (jsResponse != null) {
                try {
                    if (jsResponse.isSuccessful()) {
                        // 嵌套管辖 Body 流，防物理连接泄漏
                        try (ResponseBody body = jsResponse.body()) {
                            if (body != null) {
                                jsContent = body.string();
                            }
                        }
                        List<String> setCookies = jsResponse.headers("Set-Cookie");
                        if (setCookies != null) {
                            for (String cookie : setCookies) {
                                if (!TextUtils.isEmpty(cookie)) {
                                    localCookie = mergeCookie(localCookie, cookie);
                                }
                            }
                        }
                    } else {
                        SpiderDebug.log("获取JS脚本响应码异常: " + jsResponse.code());
                        return false;
                    }
                } finally {
                    try { jsResponse.close(); } catch (Exception ignored) {}
                }
            } else {
                SpiderDebug.log("获取JS脚本返回 Response 为空");
                return false;
            }

            if (TextUtils.isEmpty(jsContent)) {
                SpiderDebug.log("JS脚本内容为空");
                return false;
            }

            // 提取验证参数
            String key = extractByRegex(jsContent, "key\\s*=\\s*[\"']([^\"']+)[\"']");
            String value = extractByRegex(jsContent, "value\\s*=\\s*[\"']([^\"']+)[\"']");
            String verifyPath = extractVerifyPath(jsContent);

            if (TextUtils.isEmpty(key) || TextUtils.isEmpty(value) || TextUtils.isEmpty(verifyPath)) {
                SpiderDebug.log("提取验证参数失败");
                return false;
            }

            // 构建验证URL
            String verifyUrl = buildVerifyUrl(verifyPath, key, value);

            // ================== 交互 2：执行滑块验证冲刺 ==================
            HashMap<String, String> verifyHeaders = new HashMap<>(defaultHeaders);
            if (!TextUtils.isEmpty(localCookie)) {
                verifyHeaders.put("Cookie", localCookie);
            }
            verifyHeaders.put("Referer", siteUrl + "/");

            boolean success = false;
            Response verifyResponse = executeRequest(verifyUrl, verifyHeaders);
            if (verifyResponse != null) {
                try {
                    success = verifyResponse.isSuccessful();
                    // 🔥 核心修正：必须管辖 ResponseBody，否则底层 Socket 物理连接永远无法归还连接池
                    try (ResponseBody body = verifyResponse.body()) {
                        if (success) {
                            List<String> verifyCookies = verifyResponse.headers("Set-Cookie");
                            if (verifyCookies != null) {
                                for (String cookie : verifyCookies) {
                                    if (!TextUtils.isEmpty(cookie)) {
                                        localCookie = mergeCookie(localCookie, cookie);
                                    }
                                }
                                this.verifyCookie = localCookie;
                            }
                        }
                    }
                } finally {
                    try { verifyResponse.close(); } catch (Exception ignored) {}
                }
            } else {
                SpiderDebug.log("验证请求返回 Response 为空");
                return false;
            }

            SpiderDebug.log("TVB滑块验证" + (success ? "成功" : "失败"));
            return success;

        } catch (Exception e) {
            SpiderDebug.log(e);
            return false;
        }
    }
    
    /**
     * 提取验证路径
     */
    private String extractVerifyPath(String jsContent) {
        // 尝试多种正则模式
        String path = extractByRegex(jsContent, "c\\.get\\([\"']([^\"']*yanzheng_huadong\\.php\\?[^\"']*)[\"']");
        if (!TextUtils.isEmpty(path)) return path;
        
        path = extractByRegex(jsContent, "([\\w_\\/.-]*yanzheng_huadong\\.php\\?type=[^\"']+)&key=");
        if (!TextUtils.isEmpty(path)) return path;
        
        path = extractByRegex(jsContent, "yanzheng_huadong\\.php[^\"']*");
        if (!TextUtils.isEmpty(path)) return path;
        
        return "";
    }
    
    /**
     * 构建验证URL
     */
    private String buildVerifyUrl(String verifyPath, String key, String value) throws Exception {
        String encodedKey = URLEncoder.encode(key, "UTF-8");
        String md5Value = md5(asciiPlusOne(value));
        String baseUrl = verifyPath.startsWith("http") ? verifyPath : siteUrl + verifyPath;
        String separator = baseUrl.contains("?") ? "&" : "?";
        return baseUrl + separator + "key=" + encodedKey + "&value=" + md5Value;
    }
    
    // ==================== 通用滑动验证 ====================

    /**
     * 通用滑动验证
     */
    private boolean passSliderVerify(String html) {
        throw new UnsupportedOperationException("通用滑动验证暂未实现，请通过 extend 配置指定 verify_type 为 tvb");
    }

    // ==================== 点选验证 ====================

    /**
     * 点选验证
     */
    private boolean passClickVerify(String html) {
        throw new UnsupportedOperationException("点选验证暂未实现");
    }
    
    // ==================== 外部验证服务 ====================
    
    /**
     * 尝试使用外部验证服务
     */
    private boolean tryExternalVerify(String html, VerifyType type) {
        if (TextUtils.isEmpty(ddddOcrApi)) return false;
        
        try {
            // 构建请求体
            JSONObject requestBody = new JSONObject();
            requestBody.put("url", siteUrl);
            requestBody.put("html", html != null ? html : "");
            requestBody.put("cookie", verifyCookie);
            requestBody.put("type", type.getCode());
            
            // 构建请求头
            HashMap<String, String> apiHeaders = new HashMap<>();
            apiHeaders.put("Content-Type", "application/json");
            apiHeaders.put("User-Agent", defaultHeaders.get("User-Agent"));
            apiHeaders.put("Referer", siteUrl + "/");
            
            // 发送请求（使用短超时，防止外部打码服务挂起时锁住线程）
            String apiUrl = ddddOcrApi + "/verify";
            String response = executeRequestBody(apiUrl, apiHeaders);
            if (TextUtils.isEmpty(response) || !response.trim().startsWith("{")) {
                SpiderDebug.log("外部验证服务返回非合法JSON数据或超时");
                return false;
            }

            // 解析响应
            JSONObject data = new JSONObject(response);
            String cookie = extractCookieFromResponse(data);
            
            if (!TextUtils.isEmpty(cookie)) {
                // 全程局部变量，确认成功后一次性刷入全局
                String[] cookies = cookie.split(";\\s*");
                String tempCookie = this.verifyCookie;
                for (String c : cookies) {
                    if (!TextUtils.isEmpty(c)) {
                        tempCookie = mergeCookies(tempCookie, c);
                    }
                }
                this.verifyCookie = tempCookie;
                SpiderDebug.log("外部验证服务成功，已更新cookie");
                return true;
            }
            
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        
        return false;
    }
    
    /**
     * 从响应中提取cookie
     */
    private String extractCookieFromResponse(JSONObject data) {
        try {
            // 尝试多种字段名
            if (data.has("cookie") && !data.isNull("cookie")) {
                return data.getString("cookie");
            }
            
            if (data.has("cookies") && !data.isNull("cookies")) {
                return data.getString("cookies");
            }
            
            // 尝试嵌套的data对象
            if (data.has("data")) {
                JSONObject inner = data.optJSONObject("data");
                if (inner != null) {
                    if (inner.has("cookie")) {
                        return inner.getString("cookie");
                    }
                    if (inner.has("cookies")) {
                        return inner.getString("cookies");
                    }
                }
            }
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        
        return null;
    }
    
    // ==================== 辅助方法 ====================
    
    /**
     * 执行HTTP请求并返回Response对象（验证流程专用，携带短超时防止锁内IO挂起）
     */
    private Response executeRequest(String url, Map<String, String> headers) {
        try {
            return OkHttp.newCall(url, headers, VERIFY_IO_TIMEOUT_MS);
        } catch (Exception e) {
            SpiderDebug.log(e);
            return null;
        }
    }

    /**
     * 执行HTTP请求并返回响应体字符串（带短超时，用于外部打码服务调用）
     */
    private String executeRequestBody(String url, Map<String, String> headers) {
        // 1. 锁外安全获取 Response，不直接放入 try(...) 防 null.close() NPE
        Response response = null;
        try {
            response = OkHttp.newCall(url, headers, VERIFY_IO_TIMEOUT_MS);
            if (response == null || !response.isSuccessful()) return null;
            // 2. 精准嵌套管辖 Body 物理流，防死锁和连接池干涸
            try (ResponseBody body = response.body()) {
                return body != null ? body.string() : null;
            }
        } catch (Exception e) {
            SpiderDebug.log(e);
            return null;
        } finally {
            if (response != null) {
                try { response.close(); } catch (Exception ignored) {}
            }
        }
    }
    
    /**
     * 正则提取
     */
    private String extractByRegex(String text, String regex) {
        if (TextUtils.isEmpty(text)) return "";
        Matcher m = Pattern.compile(regex).matcher(text);
        return m.find() ? m.group(1) : "";
    }
    
    /**
     * MD5加密
     */
    private String md5(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] bytes = md.digest((text == null ? "" : text).getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b & 0xff));
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }
    
    /**
     * 每个字符 ASCII 码 +1 后拼接为十进制字符串
     */
    private String asciiPlusOne(String str) {
        if (TextUtils.isEmpty(str)) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < str.length(); i++) {
            sb.append((int) str.charAt(i) + 1);
        }
        return sb.toString();
    }
    
    /**
     * 合并Cookie（复用静态方法）
     */
    private String mergeCookie(String oldCookie, String setCookie) {
        return mergeCookies(oldCookie, setCookie);
    }
    
    // ==================== 静态工具方法 ====================
    
    /**
     * 静态方法：快速检测是否为验证页面
     */
    public static boolean isSliderVerifyPage(String html) {
        if (TextUtils.isEmpty(html)) return false;
        return html.contains("滑动验证") ||
               html.contains("滑块验证") ||
               html.contains("huadong_") ||
               html.contains("yanzheng_huadong.php") ||
               html.contains("click_captcha");
    }
    
    /**
     * 静态方法：快速合并Cookie
     * 完整解析两个分号分隔的 Cookie 字符串，保留所有合法 Name=Value 对
     */
    public static String mergeCookies(String oldCookie, String newCookie) {
        HashMap<String, String> jar = new HashMap<>();

        if (!TextUtils.isEmpty(oldCookie)) {
            for (String c : oldCookie.split(";")) {
                parseCookiePair(c.trim(), jar);
            }
        }

        if (!TextUtils.isEmpty(newCookie)) {
            for (String c : newCookie.split(";")) {
                parseCookiePair(c.trim(), jar);
            }
        }

        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : jar.entrySet()) {
            if (sb.length() > 0) sb.append("; ");
            // 无值的 Flag（如 HttpOnly）不加 '=' 号，避免输出脏数据
            if (TextUtils.isEmpty(entry.getValue())) {
                sb.append(entry.getKey());
            } else {
                sb.append(entry.getKey()).append("=").append(entry.getValue());
            }
        }
        return sb.toString();
    }

    private static void parseCookiePair(String pair, HashMap<String, String> jar) {
        if (TextUtils.isEmpty(pair)) return;
        int idx = pair.indexOf('=');
        String name;
        String value;
        if (idx >= 0) {
            name = pair.substring(0, idx).trim();
            value = pair.substring(idx + 1).trim();
        } else {
            // 允许无 '=' 的标记（如 HttpOnly 独立标头），以空字符串为值写入
            name = pair.trim();
            value = "";
        }
        if (!TextUtils.isEmpty(name)
                && !name.equalsIgnoreCase("Path")
                && !name.equalsIgnoreCase("Domain")
                && !name.equalsIgnoreCase("Expires")
                && !name.equalsIgnoreCase("Max-Age")
                && !name.equalsIgnoreCase("HttpOnly")
                && !name.equalsIgnoreCase("Secure")
                && !name.equalsIgnoreCase("SameSite")) {
            jar.put(name, value);
        }
    }
    
    /**
     * 静态方法：快速MD5
     */
    public static String quickMd5(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] bytes = md.digest((text == null ? "" : text).getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b & 0xff));
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }
    
    /**
     * 获取PC User-Agent
     */
    public static String getPcUserAgent() {
        return PC_UA;
    }
    
    /**
     * 获取移动端 User-Agent
     */
    public static String getMobileUserAgent() {
        return MOBILE_UA;
    }

    /**
     * 设置JS验证码key接口地址（如 http://127.0.0.1/key）
     */
    public void setJsKeyUrl(String url) {
        this.jsKeyUrl = url != null ? url.replaceAll("/$", "") : "";
    }

    /**
     * 获取 JS 验证码 Key（使用实例持有的 jsKeyUrl）
     */
    public String getJsKey() {
        return getJsKey(this.jsKeyUrl);
    }

    /**
     * 获取 JS 验证码 Key
     * 通过 OkHttp 请求配置的 /key 接口获取，失败时返回空字符串
     * jsKeyUrl 为空时返回空字符串（避免无意义请求）
     */
    /**
     * 获取 JS 验证码 Key
     * 修复版：彻底切断隐式 null.close() 导致的 NPE 闪退
     */
    public static String getJsKey(String jsKeyUrl) {
        if (TextUtils.isEmpty(jsKeyUrl)) return "";
        okhttp3.Response response = null;
        try {
            response = OkHttp.newCall(jsKeyUrl, null, VERIFY_IO_TIMEOUT_MS);
            if (response == null || !response.isSuccessful()) return "";
            try (okhttp3.ResponseBody body = response.body()) {
                String bodyStr = body != null ? body.string() : "";
                return TextUtils.isEmpty(bodyStr) ? "" : bodyStr.trim();
            }
        } catch (Exception e) {
            SpiderDebug.log(e);
            return "";
        } finally {
            if (response != null) {
                try { response.close(); } catch (Exception ignored) {}
            }
        }
    }

    /**
     * 解密混淆字符串（对应 QJr.decrypt 功能）
     * Hex+XOR 解密，密钥: lywkxC
     * @param hexStr 形如 "191710111133" 或 "1a2b3c" 的十六进制加密串（大小写不敏感）
     * @return 解密后的明文
     */
    public static String decryptHex(String hexStr) {
        if (hexStr == null || hexStr.length() % 2 != 0) return "";
        final String KEY = "lywkxC";
        final String HEX = "0123456789abcdef";
        String lowerHex = hexStr.toLowerCase();
        int len = lowerHex.length();
        byte[] b = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            int hi = HEX.indexOf(lowerHex.charAt(i));
            int lo = HEX.indexOf(lowerHex.charAt(i + 1));
            if (hi < 0 || lo < 0) return ""; // 非法字符直接返回空，避免返回垃圾数据
            b[i / 2] = (byte) ((hi << 4) | lo);
        }
        for (int i = 0; i < b.length; i++) {
            b[i] = (byte) (b[i] ^ KEY.charAt(i % KEY.length()));
        }
        return new String(b);
    }
}