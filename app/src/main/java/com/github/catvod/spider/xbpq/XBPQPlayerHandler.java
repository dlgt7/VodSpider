package com.github.catvod.spider.xbpq;

import android.app.Application;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.SystemClock;
import android.text.TextUtils;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.github.catvod.crawler.SpiderDebug;

import org.json.JSONObject;

import java.io.File;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * XBPQPlayerHandler - 播放处理辅助类
 * 
 * <p>负责处理所有播放相关的逻辑，包括：</p>
 * <ul>
 *   <li>WebView 视频嗅探</li>
 *   <li>磁力链接处理</li>
 *   <li>播放 URL 解析</li>
 *   <li>X5 WebView 支持</li>
 * </ul>
 * 
 * @version 1.0
 * @since 2024
 */
public class XBPQPlayerHandler {

    // ==================== 常量定义 ====================
    
    /** 播放 URL 字段名 */
    public static final String URL = "url";
    
    /** 解析标志字段名 */
    public static final String PARSE = "parse";
    
    /** 嗅探词配置字段名 */
    public static final String PARSE_WORD = "嗅探词";
    
    /** 强制嗅探词配置字段名 */
    public static final String FORCE_PARSE_WORD = "强制嗅探词";
    
    /** 页面代理字段名 */
    public static final String PAGE_PROXY = "页面代理";
    
    /** 停止解析标志 */
    static final String STOP_PARSE = "stopParse";
    
    /** 磁力链接前缀 */
    static final String MAGNET_PREFIX = "magnet:";
    
    /** M3U8 扩展名 */
    static final String M3U8_EXTENSION = ".m3u8";
    
    /** 磁力代理基础 URL */
    static final String MAGNET_PROXY_BASE_URL = "http://127.0.0.1:10079/";
    
    /** PPMAG 前缀 */
    static final String PPMAG_PREFIX = "ppmag:";
    
    /** SharedPreferences 后缀 */
    static final String PREFERENCES_SUFFIX = "_preferences";
    
    /** 全局磁力 Jar 访问标志 */
    static final String GLOBAL_MAGNET_JARACC = "global_magnet_jaracc";
    
    /** WebView 嗅探超时时间（毫秒） */
    static final int SNIFF_TIMEOUT_MS = 10000;
    
    /** WebView 嗅探检查间隔（毫秒） */
    static final int SNIFF_CHECK_INTERVAL_MS = 500;
    
    /** WebView 嗅探最大重试次数 */
    static final int SNIFF_MAX_RETRY_COUNT = 20;
    
    /** 磁力哈希正则表达式 */
    static final Pattern magHashPattern = Pattern.compile("^[a-fA-F0-9]{40}$");
    
    // ==================== 静态变量 ====================
    
    /** X5 WebView 可用标志 */
    private static boolean staticHasX5WebView = false;
    
    /** X5 WebView 检测标志 */
    private static boolean staticHasX5WebViewDetected = false;

    // ==================== 辅助方法 ====================

    /**
     * 获取应用上下文
     * 
     * <p>使用多种兜底方式获取 Context，避免返回 null 导致 NPE。</p>
     *
     * @return 应用上下文对象，如果所有方式都失败则返回 null
     */
    private static android.content.Context getContext() {
        // 方式1：尝试从 AppGlobals 获取
        try {
            android.content.Context context = (android.content.Context) Class.forName("android.app.AppGlobals")
                .getMethod("getInitialApplication")
                .invoke(null);
            if (context != null) {
                SpiderDebug.log("[XBPQ] getContext from AppGlobals success");
                return context;
            }
        } catch (Exception e) {
            SpiderDebug.error("[XBPQ] getContext AppGlobals failed", e);
        }

        // 方式2：尝试从 ActivityThread 获取
        try {
            Object activityThread = Class.forName("android.app.ActivityThread")
                .getMethod("currentActivityThread")
                .invoke(null);
            android.content.Context context = (android.content.Context) activityThread
                .getClass()
                .getMethod("getApplication")
                .invoke(activityThread);
            if (context != null) {
                SpiderDebug.log("[XBPQ] getContext from ActivityThread success");
                return context;
            }
        } catch (Exception e) {
            SpiderDebug.error("[XBPQ] getContext ActivityThread failed", e);
        }

        SpiderDebug.log("[XBPQ] getContext 所有方式都失败，返回 null");
        return null;
    }

    // ==================== 播放内容解析 ====================
    
    /**
     * 播放内容解析
     * 
     * @param jsonStr JSON 字符串
     * @param ext 扩展配置
     * @return 解析后的 JSON 字符串
     */
    public static String playerContent(String jsonStr, JSONObject ext) {
        List<String> matchList = null;
        List<String> forceMatchList = null;
        String pageProxyUrl = "";
        String forceSniffWord = "";

        try {
            if (ext != null) {
                String sniffWord = ext.optString(PARSE_WORD, "");
                forceSniffWord = ext.optString(FORCE_PARSE_WORD, "");

                SpiderDebug.log("[XBPQ] playerContent sniffWord:" + sniffWord + " forceSniffWord:" + forceSniffWord);

                if (!TextUtils.isEmpty(sniffWord)) {
                    matchList = Arrays.asList(sniffWord.split("#"));
                }

                if (!TextUtils.isEmpty(forceSniffWord)) {
                    forceMatchList = Arrays.asList(forceSniffWord.split("#"));
                }

                pageProxyUrl = ext.optString(PAGE_PROXY, "");
                SpiderDebug.log("[XBPQ] playerContent pageProxyUrl:" + pageProxyUrl);
            }

            JSONObject json = new JSONObject(jsonStr);
            String url = json.getString(URL);

            // 处理磁力链接
            if (url.startsWith(MAGNET_PREFIX)) {
                url = handleMagnetLink(url);
                json.put(URL, url);
            } else if (url.contains(M3U8_EXTENSION)) {
                url = getProxyDownloadUrl(url, pageProxyUrl);
                json.put(URL, url);
            } else {
                int parse = json.getInt(PARSE);
                if (parse == 1) {
                    url = sniffVideoUrl(url, forceMatchList, matchList);
                    json.put(URL, url);
                }
            }

            return json.toString();
        } catch (Exception e) {
            SpiderDebug.error("[XBPQ] playerContent error: " + e.getMessage(), e);
            return jsonStr;
        }
    }
    
    // ==================== 磁力链接处理 ====================
    
    /**
     * 处理磁力链接
     * 
     * @param url 磁力链接
     * @return 处理后的 URL
     */
    private static String handleMagnetLink(String url) {
        try {
            android.content.Context context = getContext();
            if (context == null) {
                SpiderDebug.log("[XBPQ] handleMagnetLink: Context 为 null，跳过磁力链接处理");
                return url;
            }
            
            // 安全获取 Application
            Application application = context instanceof Application
                ? (Application) context
                : (Application) context.getApplicationContext();
            
            if (application == null) {
                SpiderDebug.log("[XBPQ] handleMagnetLink: Application 为 null，跳过磁力链接处理");
                return url;
            }
            
            String prefName = application.getPackageName() + PREFERENCES_SUFFIX;
            SharedPreferences preferences = application.getSharedPreferences(prefName, 0);
            boolean magnetJarAccess = preferences.getBoolean(GLOBAL_MAGNET_JARACC, false);

            if (magnetJarAccess) {
                Uri uri = Uri.parse(url);
                File file = new File(uri.getPath());
                Matcher hashMatcher = magHashPattern.matcher(file.getName());

                if (hashMatcher.matches()) {
                    String proxyUrl = MAGNET_PROXY_BASE_URL +
                        URLEncoder.encode(PPMAG_PREFIX + url.substring(7));
                    SpiderDebug.log("[XBPQ] magnet url: " + proxyUrl);
                    return proxyUrl;
                }
            }
        } catch (Exception e) {
            SpiderDebug.error("[XBPQ] handleMagnetLink error: " + e.getMessage(), e);
        }

        return url;
    }
    
    // ==================== 视频嗅探 ====================
    
    /**
     * 嗅探视频 URL
     * 
     * @param url 原始 URL
     * @param forceMatchList 强制匹配列表
     * @param matchList 匹配列表
     * @return 嗅探到的 URL
     */
    private static String sniffVideoUrl(String url, List<String> forceMatchList, List<String> matchList) {
        HashMap<String, Object> parseResult = new HashMap<>();
        ArrayList<Object> webViewSet = new ArrayList<>();
        HashMap<String, Object> parseFlags = new HashMap<>();

        getMediaUrl(url, parseResult, forceMatchList, matchList, false, webViewSet, parseFlags);

        long startTime = System.currentTimeMillis();
        int retryCount = 0;
        
        while (retryCount < SNIFF_MAX_RETRY_COUNT && parseResult.isEmpty()) {
            long elapsedTime = System.currentTimeMillis() - startTime;
            if (elapsedTime >= SNIFF_TIMEOUT_MS) {
                SpiderDebug.log("[XBPQ] sniff timeout after " + elapsedTime + "ms");
                break;
            }
            
            SystemClock.sleep(SNIFF_CHECK_INTERVAL_MS);
            retryCount++;
        }

        parseFlags.put(STOP_PARSE, "1");

        String resultUrl = url;
        if (!parseResult.isEmpty() && parseResult.containsKey(URL)) {
            resultUrl = (String) parseResult.get(URL);
            SpiderDebug.log("[XBPQ] sniffed URL: " + resultUrl);
        } else {
            SpiderDebug.log("[XBPQ] sniff failed or timeout");
        }

        destroyView(webViewSet);
        
        return resultUrl;
    }
    
    /**
     * 获取代理下载 URL
     * 
     * @param url 原始 URL
     * @param pageProxyUrl 页面代理 URL
     * @return 代理 URL
     */
    private static String getProxyDownloadUrl(String url, String pageProxyUrl) {
        if (TextUtils.isEmpty(pageProxyUrl)) {
            return url;
        }
        // 这里可以添加代理逻辑
        return url;
    }
    
    // ==================== WebView 管理 ====================
    
    /**
     * 获取媒体链接（使用 WebView 嗅探）
     * 
     * <p>必须在主线程创建和操作 WebView，避免内存泄漏。</p>
     */
    public static void getMediaUrl(String url, HashMap<String, Object> parseResult,
                                   List<String> forceMatchList, List<String> matchList,
                                   boolean isPc, ArrayList<Object> webViewSet,
                                   HashMap<String, Object> parseFlags) {
        // 必须在主线程创建 WebView
        runOnUiThread(() -> createStandardWebView(url, parseResult, parseFlags, webViewSet, isPc));
    }
    
    /**
     * 销毁 WebView 实例
     * 
     * <p>必须在主线程销毁 WebView，避免内存泄漏。</p>
     */
    public static void destroyView(ArrayList<Object> webViewSet) {
        if (webViewSet == null || webViewSet.isEmpty()) return;

        // 必须在主线程销毁 WebView
        runOnUiThread(() -> destroyStandardWebView(webViewSet));

        webViewSet.clear();
    }
    
    /**
     * 在主线程执行操作
     */
    private static void runOnUiThread(Runnable runnable) {
        if (runnable == null) return;

        try {
            android.content.Context context = getContext();
            if (context instanceof android.app.Activity) {
                ((android.app.Activity) context).runOnUiThread(runnable);
            } else {
                new android.os.Handler(android.os.Looper.getMainLooper()).post(runnable);
            }
        } catch (Exception e) {
            SpiderDebug.error("[XBPQ] runOnUiThread error: " + e.getMessage(), e);
            try {
                new android.os.Handler(android.os.Looper.getMainLooper()).post(runnable);
            } catch (Exception ex) {
                SpiderDebug.error("[XBPQ] runOnUiThread fallback error: " + ex.getMessage(), ex);
            }
        }
    }
    
    /**
     * 创建标准 WebView
     */
    private static void createStandardWebView(String url, HashMap<String, Object> parseResult,
                                               HashMap<String, Object> parseFlags,
                                               ArrayList<Object> webViewSet, boolean isPc) {
        // 检查 Context 是否为空，避免 NPE
        android.content.Context context = getContext();
        if (context == null) {
            SpiderDebug.log("[XBPQ] createStandardWebView: Context 为 null，无法创建 WebView");
            return;
        }
        WebView webView = new WebView(context);
        webViewSet.add(webView);
        
        configureStandardWebView(webView, isPc);
        setStandardWebViewClient(webView, parseResult, parseFlags);
        
        webView.loadUrl(url);
    }
    
    /**
     * 配置标准 WebView
     */
    private static void configureStandardWebView(WebView webView, boolean isPc) {
        android.webkit.WebSettings settings = webView.getSettings();
        
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setDatabaseEnabled(true);
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setCacheMode(android.webkit.WebSettings.LOAD_NO_CACHE);
        
        if (isPc) {
            settings.setUserAgentString("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
        } else {
            settings.setUserAgentString("Mozilla/5.0 (iPhone; CPU iPhone OS 16_0 like Mac OS X)");
        }
    }
    
    /**
     * 设置标准 WebView Client
     */
    private static void setStandardWebViewClient(WebView webView, 
                                                  HashMap<String, Object> parseResult,
                                                  HashMap<String, Object> parseFlags) {
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                view.loadUrl(url);
                return true;
            }
            
            @Override
            public void onLoadResource(WebView view, String url) {
                handleVideoSniff(url, parseResult, parseFlags);
            }
            
            @Override
            public void onReceivedSslError(WebView view, android.webkit.SslErrorHandler handler,
                                           android.net.http.SslError error) {
                // 嗅探场景下许多视频站点使用自签名/过期证书，忽略 SSL 错误以保证嗅探可用。
                // 注意：此处理仅用于 WebView 嗅探，不影响 OkHttp 的请求安全策略。
                handler.proceed();
            }
        });
    }
    
    /**
     * 处理视频嗅探
     */
    private static void handleVideoSniff(String url, HashMap<String, Object> parseResult,
                                          HashMap<String, Object> parseFlags) {
        if (parseFlags.containsKey(STOP_PARSE)) return;

        // 判断是否为视频格式（使用 XBPQUtils 统一实现）
        if (XBPQUtils.isVideoFormat(url)) {
            parseResult.put(URL, url);
            SpiderDebug.log("[XBPQ] sniffed video URL: " + url);
        }
    }

    /**
     * 销毁标准 WebView
     */
    private static void destroyStandardWebView(ArrayList<Object> webViewSet) {
        for (Object webView : webViewSet) {
            safeDestroy(webView);
        }
    }

    /**
     * 安全销毁 WebView
     */
    private static void safeDestroy(Object webView) {
        if (webView == null) return;

        try {
            if (webView instanceof WebView) {
                WebView w = (WebView) webView;
                w.stopLoading();
                w.loadUrl("about:blank");
                w.clearHistory();
                w.clearCache(true);
                w.onPause();
                w.removeAllViews();
                w.destroyDrawingCache();
                w.destroy();
            }
        } catch (Exception e) {
            SpiderDebug.error("[XBPQ] safeDestroy error: " + e.getMessage(), e);
        }
    }

    /**
     * 检测是否有 X5 WebView
     *
     * <p>当前项目未集成腾讯 X5 SDK，始终返回 false。
     * 如需启用 X5 内核，请引入 com.tencent.smtt 依赖并恢复相关 X5 方法。</p>
     */
    public static boolean hasX5WebView() {
        return false;
    }
}