package com.github.catvod.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;

import org.json.JSONObject;

import java.net.URLEncoder;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Response;

/**
 * 通用滑块验证工具类
 * 支持多种验证类型：滑动验证、点选验证等
 * 支持外部验证服务（ddddocr等）
 * 支持Cookie缓存管理
 */
public class SliderVerifyUtils {

    // ==================== 常量定义 ====================
    
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
    private static final String PC_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36";
    
    /**
     * 移动端 User-Agent
     */
    private static final String MOBILE_UA = "Mozilla/5.0 (iPhone; CPU iPhone OS 14_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/14.0 Mobile/15E148 Safari/604.1";
    
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
    
    private String verifyCookie = "";
    private long verifiedAt = 0;
    private String ddddOcrApi = "";
    private VerifyType verifyType = VerifyType.SLIDER_TVB;
    private boolean enableCache = true;
    
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

            } else {
                // 直接作为ddddocr API地址
                ddddOcrApi = extend.replaceAll("/$", "");
                SpiderDebug.log("设置ddddocr API: " + ddddOcrApi);
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
            
            String[] parts = cached.split("\\|");
            if (parts.length == 2) {
                String cookie = parts[0];
                long timestamp = Long.parseLong(parts[1]);
                
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
            sharedPreferences.edit()
                    .putString(cacheKey, verifyCookie + "|" + verifiedAt)
                    .apply();
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
                sharedPreferences.edit().remove(cacheKey).apply();
            } catch (Exception e) {
                SpiderDebug.log(e);
            }
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
        
        // 常见验证页面特征
        return html.contains("滑动验证") || 
               html.contains("滑块验证") ||
               html.contains("人机验证") ||
               html.contains("安全验证") ||
               html.contains("huadong_") || 
               html.contains("yanzheng_huadong.php") ||
               html.contains("captcha") ||
               html.contains("verify") && html.contains("slider");
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
     */
    public String requestWithVerify(String url, Map<String, String> extraHeaders) {
        try {
            // 加载缓存
            loadCache();

            // 构建请求头
            HashMap<String, String> headers = new HashMap<>(defaultHeaders);
            if (extraHeaders != null) {
                headers.putAll(extraHeaders);
            }
            if (!TextUtils.isEmpty(verifyCookie)) {
                headers.put("Cookie", verifyCookie);
            }

            // 发送请求
            String content = OkHttp.string(url, headers);

            // 检查请求结果
            if (TextUtils.isEmpty(content)) {
                SpiderDebug.log("请求返回空内容: " + url);
            }

            // 检测验证页面
            if (isVerifyPage(content)) {
                VerifyType detectedType = detectVerifyType(content);
                SpiderDebug.log("检测到验证页面，类型：" + detectedType.getDesc());

                // 执行验证
                boolean success = passVerify(content, detectedType);

                if (success) {
                    saveCache();

                    // 重试请求
                    headers.put("Cookie", verifyCookie);
                    content = OkHttp.string(url, headers);

                    // 检查重试结果
                    if (TextUtils.isEmpty(content)) {
                        SpiderDebug.log("验证后重试返回空内容: " + url);
                    }

                    // 再次检测（防止验证失败）
                    if (isVerifyPage(content)) {
                        clearCache();
                        SpiderDebug.log("验证后仍为验证页面，已清除缓存");
                    }
                } else {
                    clearCache();
                    SpiderDebug.log("验证失败，已清除缓存");
                }
            }

            return content;

        } catch (Exception e) {
            SpiderDebug.log(e);
            return "";
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
     * 使用 try-with-resources 确保 Response 正确关闭
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

            // 获取脚本内容（使用 try-with-resources）
            HashMap<String, String> jsHeaders = new HashMap<>(defaultHeaders);
            if (!TextUtils.isEmpty(verifyCookie)) {
                jsHeaders.put("Cookie", verifyCookie);
            }
            jsHeaders.put("Referer", siteUrl + "/");

            String jsContent;
            try (Response jsResponse = executeRequest(scriptUrl, jsHeaders)) {
                if (jsResponse == null || !jsResponse.isSuccessful()) {
                    SpiderDebug.log("获取JS脚本失败");
                    return false;
                }

                jsContent = jsResponse.body().string();
                String setCookie = jsResponse.header("Set-Cookie");
                if (!TextUtils.isEmpty(setCookie)) {
                    verifyCookie = mergeCookie(verifyCookie, setCookie);
                }
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

            // 执行验证（使用 try-with-resources）
            HashMap<String, String> verifyHeaders = new HashMap<>(defaultHeaders);
            if (!TextUtils.isEmpty(verifyCookie)) {
                verifyHeaders.put("Cookie", verifyCookie);
            }
            verifyHeaders.put("Referer", siteUrl + "/");

            boolean success;
            try (Response verifyResponse = executeRequest(verifyUrl, verifyHeaders)) {
                if (verifyResponse == null) {
                    SpiderDebug.log("验证请求失败");
                    return false;
                }

                String verifySetCookie = verifyResponse.header("Set-Cookie");
                if (!TextUtils.isEmpty(verifySetCookie)) {
                    verifyCookie = mergeCookie(verifyCookie, verifySetCookie);
                }

                success = verifyResponse.isSuccessful();
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
        String md5Value = md5(stringToHex(value));
        
        if (verifyPath.contains("&key=")) {
            return siteUrl + verifyPath + encodedKey + "&value=" + md5Value;
        } else {
            return siteUrl + verifyPath + "&key=" + encodedKey + "&value=" + md5Value;
        }
    }
    
    // ==================== 通用滑动验证 ====================

    /**
     * 通用滑动验证
     * TODO: 可以扩展支持其他类型的滑动验证（如极验、网易易盾等）
     */
    private boolean passSliderVerify(String html) {
        SpiderDebug.log("通用滑动验证暂未实现");
        return false;
    }

    // ==================== 点选验证 ====================

    /**
     * 点选验证
     * TODO: 可以扩展支持点选验证（如字体验证、图标验证等）
     */
    private boolean passClickVerify(String html) {
        SpiderDebug.log("点选验证暂未实现");
        return false;
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
            
            // 发送请求
            String apiUrl = ddddOcrApi + "/verify";
            String response = OkHttp.post(apiUrl, requestBody.toString(), apiHeaders);
            
            if (TextUtils.isEmpty(response)) {
                SpiderDebug.log("外部验证服务响应为空");
                return false;
            }
            
            // 解析响应
            JSONObject data = new JSONObject(response);
            String cookie = extractCookieFromResponse(data);
            
            if (!TextUtils.isEmpty(cookie)) {
                // 合并cookie
                String[] cookies = cookie.split(";\\s*");
                for (String c : cookies) {
                    if (!TextUtils.isEmpty(c)) {
                        verifyCookie = mergeCookie(verifyCookie, c);
                    }
                }
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
     * 执行HTTP请求并返回Response对象（带超时保护）
     */
    private Response executeRequest(String url, Map<String, String> headers) {
        try {
            okhttp3.Request.Builder requestBuilder = new okhttp3.Request.Builder()
                    .url(url);

            for (Map.Entry<String, String> entry : headers.entrySet()) {
                requestBuilder.addHeader(entry.getKey(), entry.getValue());
            }

            return OkHttp.client()
                    .newBuilder()
                    .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                    .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                    .build()
                    .newCall(requestBuilder.build())
                    .execute();
        } catch (Exception e) {
            SpiderDebug.log(e);
            return null;
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
     * 字符串转十六进制编码（每个字符ASCII码+1）
     */
    private String stringToHex(String str) {
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
        return !TextUtils.isEmpty(html) && 
               (html.contains("滑动验证") || 
                html.contains("滑块验证") ||
                html.contains("huadong_") || 
                html.contains("yanzheng_huadong.php"));
    }
    
    /**
     * 静态方法：快速合并Cookie
     */
    public static String mergeCookies(String oldCookie, String newCookie) {
        HashMap<String, String> jar = new HashMap<>();
        
        if (!TextUtils.isEmpty(oldCookie)) {
            for (String c : oldCookie.split(";")) {
                String trimmed = c.trim();
                if (!TextUtils.isEmpty(trimmed)) {
                    int idx = trimmed.indexOf('=');
                    if (idx > 0) {
                        jar.put(trimmed.substring(0, idx), trimmed.substring(idx + 1));
                    }
                }
            }
        }
        
        if (!TextUtils.isEmpty(newCookie)) {
            String first = newCookie.split(";")[0];
            int idx = first.indexOf('=');
            if (idx > 0) {
                jar.put(first.substring(0, idx), first.substring(idx + 1));
            }
        }
        
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : jar.entrySet()) {
            if (sb.length() > 0) sb.append("; ");
            sb.append(entry.getKey()).append("=").append(entry.getValue());
        }
        return sb.toString();
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
}