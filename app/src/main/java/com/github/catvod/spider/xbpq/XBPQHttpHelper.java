package com.github.catvod.spider.xbpq;

import android.text.TextUtils;

import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;

import java.util.HashMap;
import java.util.Map;

/**
 * XBPQHttpHelper - 网络请求辅助类
 * 
 * <p>负责处理所有网络请求相关的逻辑，包括：</p>
 * <ul>
 *   <li>GET/POST 请求</li>
 *   <li>请求头构建</li>
 *   <li>重试机制</li>
 *   <li>编码处理</li>
 * </ul>
 * 
 * @version 1.0
 * @since 2024
 */
public class XBPQHttpHelper {

    // ==================== 常量定义 ====================
    
    /** 最大重试次数 */
    private static final int MAX_RETRY_COUNT = 2;
    
    /** 重试等待时间（毫秒） */
    private static final int RETRY_WAIT_MS = 1000;
    
    /** 分隔符：多个关键词分隔 */
    private static final String HASH = "#";
    
    /** 分隔符：键值分隔 */
    private static final String DOLLAR = "$";
    
    /** POST 模式分隔符 */
    private static final String POST_SEP = ";post;";
    
    /** POST 模式分隔符（无参数） */
    private static final String POST_SEP_SIMPLE = ";post";
    
    /** 反向截取后缀 */
    private static final String RC_SUFFIX = ";;rc";
    
    /** 多次反向截取后缀 */
    private static final String MRC_SUFFIX = ";;mrc";
    
    // ==================== 成员变量 ====================
    
    /** 配置对象 */
    private final XBPQConfig config;
    
    /** 站点域名 */
    private final String siteHost;
    
    /** 调试模式 */
    private final boolean debugMode;
    
    /** User Agent */
    private final String userAgent;
    
    /** 编码格式 */
    private final String encoding;
    
    // ==================== 构造函数 ====================
    
    /**
     * 构造函数
     * 
     * @param config 配置对象
     * @param siteHost 站点域名
     * @param debugMode 调试模式
     */
    public XBPQHttpHelper(XBPQConfig config, String siteHost, boolean debugMode) {
        this.config = config;
        this.siteHost = siteHost != null ? siteHost : "";
        this.debugMode = debugMode;
        this.userAgent = config.getUserAgent();
        this.encoding = config.getEncoding();
    }
    
    // ==================== 公共方法 ====================
    
    /**
     * 获取页面内容（GET 请求）
     * 
     * @param url 页面 URL
     * @return 页面内容
     */
    public String fetchPage(String url) {
        return fetchPage(url, false, null);
    }
    
    /**
     * 获取页面内容（完整版本）
     * 
     * <p>支持 GET/POST 请求、超时控制、重试机制。</p>
     * 
     * @param url 页面 URL
     * @param isPost 是否为 POST 请求
     * @param postData POST 请求数据
     * @return 页面内容
     */
    public String fetchPage(String url, boolean isPost, String postData) {
        if (TextUtils.isEmpty(url)) {
            SpiderDebug.log("[XBPQ-" + siteHost + "] fetchPage: URL为空");
            return "";
        }
        
        if (debugMode) {
            SpiderDebug.log("[XBPQ-" + siteHost + "] fetchPage 开始: url=" + url + ", isPost=" + isPost);
        }
        
        // 检查是否为 POST 模式（URL 格式：网址;post;键1=值1&键2=值2 或 网址;post）
        if (url.contains(POST_SEP)) {
            String[] parts = url.split(POST_SEP);
            if (parts.length >= 2) {
                url = parts[0];
                postData = parts[1];
                isPost = true;
                if (debugMode) {
                    SpiderDebug.log("[XBPQ-" + siteHost + "] fetchPage: 检测到POST模式(带参数), url=" + url + ", data=" + postData);
                }
            }
        } else if (url.contains(POST_SEP_SIMPLE)) {
            // POST 模式（无参数，使用配置中的 POST 数据）
            String[] parts = url.split(POST_SEP_SIMPLE);
            url = parts[0];
            isPost = true;
            // 使用配置中的 POST 数据
            if (TextUtils.isEmpty(postData)) {
                postData = config.getPostData();
            }
            if (debugMode) {
                SpiderDebug.log("[XBPQ-" + siteHost + "] fetchPage: 检测到POST模式(无参数), url=" + url + ", data=" + postData);
            }
        }
        
        // 处理特殊 URL 后缀（;;rc、;;mrc 及其他后缀）
        // ;;rc - 反向截取（从后往前截取）
        // ;;mrc - 多次反向截取（多次从后往前截取）
        // ;;mrcRAD、;;rcRAD 等 - 包含额外指令的后缀，忽略未知部分
        boolean useReverseCut = false;
        boolean useMultiReverseCut = false;
        
        // 检查是否包含 ;; 分隔符
        if (url.contains(";;")) {
            // 提取 ;; 后的后缀部分
            int suffixIndex = url.indexOf(";;");
            String suffixPart = url.substring(suffixIndex);
            url = url.substring(0, suffixIndex);
            
            // 解析后缀指令
            if (suffixPart.contains("mrc")) {
                useMultiReverseCut = true;
                if (debugMode) {
                    SpiderDebug.log("[XBPQ-" + siteHost + "] fetchPage: 检测到多次反向截取模式(;;mrc)");
                }
            }
            if (suffixPart.contains("rc") && !suffixPart.contains("mrc")) {
                useReverseCut = true;
                if (debugMode) {
                    SpiderDebug.log("[XBPQ-" + siteHost + "] fetchPage: 检测到反向截取模式(;;rc)");
                }
            }
            // RAD 等其他后缀指令暂时忽略
            if (suffixPart.contains("RAD") && debugMode) {
                SpiderDebug.log("[XBPQ-" + siteHost + "] fetchPage: 检测到RAD后缀(暂时忽略)");
            }
        }
        
        Map<String, String> headers = buildHeaders();
        
        // 重试机制
        for (int retry = 0; retry <= MAX_RETRY_COUNT; retry++) {
            try {
                String html = doRequest(url, isPost, postData, headers);
                
                // 检查结果
                if (!TextUtils.isEmpty(html)) {
                    // 处理编码
                    if (!"UTF-8".equalsIgnoreCase(encoding) && !"utf-8".equalsIgnoreCase(encoding)) {
                        SpiderDebug.log("[XBPQ-" + siteHost + "] fetchPage: 使用非UTF-8编码: " + encoding);
                    }
                    
                    // 处理反向截取
                    if (useReverseCut || useMultiReverseCut) {
                        int beforeLen = html.length();
                        html = applyReverseCut(html, useMultiReverseCut);
                        if (debugMode) {
                            String preview = html.length() > 80 ? html.substring(html.length() - 80) : html;
                            preview = preview.replaceAll("\\s+", " ").trim();
                            SpiderDebug.log("[XBPQ-" + siteHost + "] fetchPage: 应用反向截取, " +
                                    "前=" + beforeLen + " -> 后=" + html.length() + ", 尾部预览=[" + preview + "]");
                        }
                    }
                    
                    if (debugMode) {
                        SpiderDebug.log("[XBPQ-" + siteHost + "] fetchPage 成功: url=" + url + ", 长度=" + html.length());
                    }
                    
                    return html;
                }
                
                // 结果为空，重试
                if (retry < MAX_RETRY_COUNT) {
                    SpiderDebug.log("[XBPQ-" + siteHost + "] fetchPage: 结果为空，正在重试 (" + (retry + 1) + "/" + MAX_RETRY_COUNT + ")");
                    Thread.sleep(RETRY_WAIT_MS);
                }
                
            } catch (Exception e) {
                SpiderDebug.error("[XBPQ-" + siteHost + "] fetchPage error: url=" + url + ", " + e.getMessage(), e);
                
                // 重试
                if (retry < MAX_RETRY_COUNT) {
                    SpiderDebug.log("[XBPQ-" + siteHost + "] fetchPage: 正在重试 (" + (retry + 1) + "/" + MAX_RETRY_COUNT + ")");
                    try {
                        Thread.sleep(RETRY_WAIT_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        SpiderDebug.log("[XBPQ-" + siteHost + "] fetchPage: 重试等待被中断");
                    }
                }
            }
        }
        
        SpiderDebug.log("[XBPQ-" + siteHost + "] fetchPage 失败: 已重试 " + (MAX_RETRY_COUNT + 1) + " 次, url=" + url);
        return "";
    }
    
    /**
     * 应用反向截取
     * 
     * <p>;;rc - 反向截取：从后往前截取，去除最后一个匹配项之后的内容</p>
     * <p>;;mrc - 多次反向截取：多次从后往前截取，去除所有匹配项之后的内容</p>
     * 
     * @param html HTML 内容
     * @param multi 是否多次截取
     * @return 截取后的内容
     */
    private String applyReverseCut(String html, boolean multi) {
        if (TextUtils.isEmpty(html)) {
            return html;
        }

        // 反向截取常用标记（从结构性标签到常见脚本片段，覆盖大多数"从后往前截取"规则）
        // 顺序大致从最末尾的标签往前；multi=true 时依次清理所有标记的尾部内容，
        // multi=false 时只处理第一个能匹配到的标记。
        String[] markers = {"</body>", "</html>", "</div>", "</script>", "function", "var ", "window."};

        if (multi) {
            // 多次反向截取：从后往前逐步清理每个标记的尾部无关内容
            for (String marker : markers) {
                int lastIndex = html.lastIndexOf(marker);
                if (lastIndex > 0) {
                    html = html.substring(0, lastIndex + marker.length());
                }
            }
        } else {
            // 单次反向截取：只处理最后一个能匹配到的标记
            for (String marker : markers) {
                int lastIndex = html.lastIndexOf(marker);
                if (lastIndex > 0) {
                    html = html.substring(0, lastIndex + marker.length());
                    break;
                }
            }
        }

        return html.trim();
    }
    
    /**
     * 构建请求头
     * 
     * @return 请求头 Map
     */
    public Map<String, String> buildHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", userAgent);
        headers.put("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
        headers.put("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");
        headers.put("Accept-Encoding", "gzip, deflate");
        headers.put("Connection", "keep-alive");
        
        if (!TextUtils.isEmpty(siteHost)) {
            headers.put("Referer", siteHost);
        }
        
        // 添加自定义请求头
        String customHeader = config.getRequestHeader();
        if (!TextUtils.isEmpty(customHeader)) {
            addCustomHeaders(headers, customHeader);
        }
        
        return headers;
    }
    
    /**
     * 构建播放请求头
     * 
     * @return 播放请求头 Map
     */
    public Map<String, String> buildPlayHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", userAgent);
        
        if (!TextUtils.isEmpty(siteHost)) {
            headers.put("Referer", siteHost);
        }
        
        // 添加自定义播放请求头
        String playHeader = config.getDirectPlayHeader();
        if (!TextUtils.isEmpty(playHeader)) {
            addCustomHeaders(headers, playHeader);
        }
        
        return headers;
    }
    
    /**
     * 构建搜索请求头
     * 
     * @return 搜索请求头 Map
     */
    public Map<String, String> buildSearchHeaders() {
        Map<String, String> headers = buildHeaders();
        
        // 添加搜索特定请求头
        String searchHeader = config.getSearchHeader();
        if (!TextUtils.isEmpty(searchHeader)) {
            addCustomHeaders(headers, searchHeader);
        }
        
        return headers;
    }
    
    // ==================== 私有方法 ====================
    
    /**
     * 执行请求
     * 
     * @param url URL
     * @param isPost 是否为 POST
     * @param postData POST 数据
     * @param headers 请求头
     * @return 响应内容
     */
    private String doRequest(String url, boolean isPost, String postData, Map<String, String> headers) throws Exception {
        if (isPost && !TextUtils.isEmpty(postData)) {
            // POST 请求
            if (debugMode) {
                SpiderDebug.log("[XBPQ-" + siteHost + "] fetchPage: 发送POST请求, url=" + url);
            }
            return OkHttp.post(url, postData, headers);
        } else {
            // GET 请求
            if (debugMode) {
                SpiderDebug.log("[XBPQ-" + siteHost + "] fetchPage: 发送GET请求, url=" + url);
            }
            return OkHttp.string(url, null, headers);
        }
    }
    
    /**
     * 添加自定义请求头
     * 
     * @param headers 请求头 Map
     * @param customHeader 自定义请求头字符串（格式：key1$value1#key2$value2）
     */
    private void addCustomHeaders(Map<String, String> headers, String customHeader) {
        if (TextUtils.isEmpty(customHeader)) return;
        
        String[] parts = customHeader.split(HASH);
        for (String part : parts) {
            String[] kv = part.split(DOLLAR);
            if (kv.length == 2) {
                headers.put(kv[0].trim(), kv[1].trim());
            }
        }
    }
}