package com.github.catvod.crawler;

import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.util.Base64;
import android.view.Surface;
import android.view.WindowManager;

import com.github.catvod.utils.Util;
import com.github.catvod.utils.Crypto;
import com.github.catvod.js.utils.JSUtil;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.FormBody;
import okhttp3.Headers;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * SpiderApi - Spider API 接口类
 * 
 * 提供 Spider 与外部系统交互的 API 接口，包括：
 * - 日志记录功能
 * - 端口和地址获取功能
 * - 屏幕方向获取功能
 * - 多请求处理功能
 * - Web 解析功能
 * 
 * @version 1.0
 * @author CatVodSpider Team
 */
public class SpiderApi {

    // ==================== 核心方法（XBPQ.java 使用） ====================

    /**
     * 获取服务器地址
     *
     * <p>由于当前环境未集成 tvbox ControlManager，返回空字符串作为占位。
     * 如需启用，请在集成 tvbox 服务后重写此方法。</p>
     *
     * @param local 是否获取本地地址
     * @return 服务器地址字符串
     */
    public String getAddress(boolean local) {
        return "";
    }

    /**
     * 获取服务器端口
     *
     * <p>由于当前环境未集成 tvbox ControlManager，返回空字符串作为占位。
     * 如需启用，请在集成 tvbox 服务后重写此方法。</p>
     *
     * @return 端口字符串
     */
    public String getPort() {
        return "";
    }

    /**
     * 记录日志信息
     * 
     * @param msg 日志消息
     */
    public void log(String msg) {
        try {
            SpiderDebug.log(msg);
        } catch (Throwable ignored) {
        }
    }

    // ==================== 辅助方法（其他功能） ====================

    /**
     * 获取屏幕方向
     *
     * <p>由于当前环境未集成 tvbox App，无法获取当前 Activity，
     * 默认返回传感器横屏模式。如需启用，请在集成 tvbox 服务后重写此方法。</p>
     *
     * @return 屏幕方向常量值
     */
    public int getScreenOrientation() {
        return ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE;
    }

    /**
     * 执行多请求处理
     * 
     * <p>优化版本，增加了超时控制和更好的异常处理。</p>
     * 
     * @param array 请求配置数组
     * @return 请求结果 JSON 字符串
     */
    public String multiReq(JsonArray array) {
        try {
            if (array == null || array.size() == 0) return "[]";
            
            // 动态调整线程池大小（最大12个线程）
            int threadCount = Math.min(array.size(), 12);
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            
            java.util.ArrayList<Future<String>> futures = new java.util.ArrayList<>();
            
            for (JsonElement element : array) {
                if (!element.isJsonObject()) continue;
                JsonObject obj = element.getAsJsonObject();
                futures.add(executor.submit(() -> executeRequest(obj)));
            }
            
            JsonArray result = new JsonArray();
            
            // 等待所有请求完成（带超时）
            for (Future<String> future : futures) {
                try {
                    // 设置超时时间为30秒
                    String response = future.get(30, java.util.concurrent.TimeUnit.SECONDS);
                    result.add(convertToResult(response));
                } catch (java.util.concurrent.TimeoutException e) {
                    SpiderDebug.log("multiReq timeout: " + e.getMessage());
                    result.add(new JsonPrimitive(""));
                    future.cancel(true);
                } catch (Exception e) {
                    SpiderDebug.log("multiReq error: " + e.getMessage());
                    result.add(new JsonPrimitive(""));
                }
            }
            
            executor.shutdown();
            
            // 等待线程池完全关闭
            try {
                if (!executor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
            
            return result.toString();
        } catch (Throwable th) {
            SpiderDebug.log("multiReq fatal error: " + th.getMessage());
            return "[]";
        }
    }

    /**
     * Web 解析功能
     * 
     * @param url 待解析的 URL
     * @param flag 解析标志
     * @return 解析后的代理 URL
     */
    public String webParse(String url, String flag) {
        try {
            if (url == null || url.isEmpty()) {
                SpiderDebug.log("webParse: URL is empty");
                return "";
            }
            String encoded = Base64.encodeToString(url.getBytes("UTF-8"), Base64.DEFAULT | Base64.URL_SAFE | Base64.NO_WRAP);
            return "proxy://go=SuperParse&flag=" + (flag == null ? "" : flag) + "&url=" + encoded;
        } catch (Throwable th) {
            SpiderDebug.log("webParse error (url=" + url + ", flag=" + flag + "): " + th.getMessage());
            return "";
        }
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 执行单个请求
     * 
     * @param requestConfig 请求配置对象
     * @return 请求响应字符串
     */
    private static String executeRequest(JsonObject requestConfig) {
        try {
            String url = getStringValue(requestConfig, "url");
            if (url.isEmpty()) {
                SpiderDebug.log("executeRequest: URL is empty");
                return "";
            }
            String method = getStringValue(requestConfig, "method");
            Headers headers = buildHeaders(requestConfig.get("headers"));
            Request.Builder builder = new Request.Builder().url(url).headers(headers);
            if ("POST".equalsIgnoreCase(method)) builder.post(buildRequestBody(requestConfig));
            OkHttpClient client = com.github.catvod.net.OkHttp.client();
            try (Response response = client.newCall(builder.build()).execute()) {
                return response.body() != null ? response.body().string() : "";
            }
        } catch (Throwable th) {
            SpiderDebug.log("executeRequest error: " + th.getMessage());
            return "";
        }
    }

    /**
     * 将文本转换为 JSON 结果
     * 
     * @param text 待转换的文本
     * @return JSON 元素
     */
    private static JsonElement convertToResult(String text) {
        if (text == null) return new JsonPrimitive("");
        try {
            String trim = text.trim();
            if (trim.startsWith("{") || trim.startsWith("[")) {
                return JsonParser.parseString(trim);
            }
        } catch (Throwable ignored) {
        }
        return new JsonPrimitive(text);
    }

    /**
     * 构建请求体
     * 
     * @param requestConfig 请求配置对象
     * @return OkHttp 请求体
     */
    private static RequestBody buildRequestBody(JsonObject requestConfig) {
        JsonElement data = requestConfig.get("data");
        if (data == null || data.isJsonNull()) return RequestBody.create(null, "");
        String postType = getStringValue(requestConfig, "postType");
        if ("form".equalsIgnoreCase(postType) && data.isJsonObject()) {
            FormBody.Builder builder = new FormBody.Builder();
            for (Map.Entry<String, JsonElement> entry : data.getAsJsonObject().entrySet()) {
                builder.add(entry.getKey(), entry.getValue().getAsString());
            }
            return builder.build();
        }
        return RequestBody.create(null, data.isJsonPrimitive() ? data.getAsString() : data.toString());
    }

    /**
     * 构建请求头
     * 
     * @param headersElement 请求头 JSON 元素
     * @return OkHttp 请求头对象
     */
    private static Headers buildHeaders(JsonElement headersElement) {
        try {
            if (headersElement == null || headersElement.isJsonNull() || !headersElement.isJsonObject()) return new Headers.Builder().build();
            HashMap<String, String> headersMap = new HashMap<>();
            for (Map.Entry<String, JsonElement> entry : headersElement.getAsJsonObject().entrySet()) {
                headersMap.put(entry.getKey(), entry.getValue().getAsString());
            }
            return Headers.of(headersMap);
        } catch (Throwable th) {
            return new Headers.Builder().build();
        }
    }

    /**
     * 从 JSON 对象中获取字符串值
     * 
     * @param jsonObject JSON 对象
     * @param key 键名
     * @return 字符串值
     */
    private static String getStringValue(JsonObject jsonObject, String key) {
        JsonElement element = jsonObject.get(key);
        return element == null || element.isJsonNull() ? "" : element.getAsString();
    }

    // ==================== 辅助方法（Spider 常用工具） ====================

    /**
     * HTML 解析方法
     * 
     * <p>使用 Jsoup 解析 HTML 内容，支持 CSS 选择器语法。</p>
     * <p>规则语法示例：</p>
     * <ul>
     *   <li>".class" - 选择 class 元素</li>
     *   <li>"#id" - 选择 id 元素</li>
     *   <li>"tag" - 选择标签元素</li>
     *   <li>"Text" - 获取文本内容</li>
     *   <li>"Html" - 获取 HTML 内容</li>
     *   <li>"href" - 获取 href 属性</li>
     *   <li>"src" - 获取 src 属性</li>
     * </ul>
     * 
     * @param html HTML 内容字符串
     * @param rule 解析规则（CSS 选择器）
     * @return 解析结果字符串
     */
    public String htmlParse(String html, String rule) {
        if (html == null || html.isEmpty() || rule == null || rule.isEmpty()) return "";
        try {
            Document doc = Jsoup.parse(html);
            String[] rules = rule.split("&&");
            Element element = doc;
            
            for (String r : rules) {
                r = r.trim();
                if (r.startsWith("body&&")) {
                    element = doc.body();
                    r = r.substring(6);
                }
                
                if (r.startsWith(".")) {
                    element = element.selectFirst(r);
                } else if (r.startsWith("#")) {
                    element = element.selectFirst(r);
                } else if (r.startsWith("Text")) {
                    return element != null ? element.text() : "";
                } else if (r.startsWith("Html")) {
                    return element != null ? element.html() : "";
                } else if (r.startsWith("href")) {
                    return element != null ? element.attr("href") : "";
                } else if (r.startsWith("src")) {
                    return element != null ? element.attr("src") : "";
                } else if (r.startsWith("data-")) {
                    return element != null ? element.attr(r) : "";
                } else if (r.startsWith("style")) {
                    return element != null ? element.attr("style") : "";
                } else if (r.startsWith("title")) {
                    return element != null ? element.attr("title") : "";
                } else if (r.startsWith("alt")) {
                    return element != null ? element.attr("alt") : "";
                } else if (r.startsWith("class")) {
                    return element != null ? element.attr("class") : "";
                } else if (r.startsWith("id")) {
                    return element != null ? element.attr("id") : "";
                } else {
                    element = element.selectFirst(r);
                }
                
                if (element == null) return "";
            }
            
            return element != null ? element.text() : "";
        } catch (Throwable th) {
            SpiderDebug.log("htmlParse error: " + th.getMessage());
            return "";
        }
    }

    /**
     * 正则匹配方法
     * 
     * <p>使用正则表达式从文本中提取匹配内容。</p>
     * 
     * @param text 待匹配的文本
     * @param pattern 正则表达式模式
     * @return 匹配到的第一个结果，如果正则包含分组则返回第一个分组内容
     */
    public String regexMatch(String text, String pattern) {
        if (text == null || text.isEmpty() || pattern == null || pattern.isEmpty()) return "";
        try {
            Pattern p = Pattern.compile(pattern);
            Matcher m = p.matcher(text);
            if (m.find()) {
                return m.groupCount() > 0 ? m.group(1) : m.group(0);
            }
        } catch (Throwable th) {
            SpiderDebug.log("regexMatch error: " + th.getMessage());
        }
        return "";
    }

    /**
     * URL 拼接方法
     * 
     * <p>将相对 URL 拼接到基础 URL 上，自动处理路径分隔符。</p>
     * 
     * @param base 基础 URL
     * @param relative 相对 URL 或路径
     * @return 拼接后的完整 URL
     */
    public String urlJoin(String base, String relative) {
        if (base == null || base.isEmpty()) return relative != null ? relative : "";
        if (relative == null || relative.isEmpty()) return base;
        try {
            // 如果相对路径已经是完整 URL，直接返回
            if (relative.startsWith("http://") || relative.startsWith("https://")) {
                return relative;
            }
            // 处理以 // 开头的相对路径
            if (relative.startsWith("//")) {
                String protocol = base.startsWith("https://") ? "https:" : "http:";
                return protocol + relative;
            }
            // 使用 URL 类进行标准拼接
            URL baseUrl = new URL(base);
            URL resultUrl = new URL(baseUrl, relative);
            return resultUrl.toString();
        } catch (Throwable th) {
            // 简单拼接作为后备方案
            String separator = base.endsWith("/") || relative.startsWith("/") ? "" : "/";
            return base + separator + relative;
        }
    }

    /**
     * URL 编码方法
     * 
     * <p>将文本进行 URL 编码，使用 UTF-8 编码格式。</p>
     * 
     * @param text 待编码的文本
     * @return URL 编码后的字符串
     */
    public String urlEncode(String text) {
        if (text == null || text.isEmpty()) return "";
        try {
            return Util.encode(text);
        } catch (Throwable th) {
            SpiderDebug.log("urlEncode error: " + th.getMessage());
            return text;
        }
    }

    /**
     * URL 解码方法
     * 
     * <p>将 URL 编码的文本进行解码，使用 UTF-8 编码格式。</p>
     * 
     * @param text URL 编码的文本
     * @return 解码后的字符串
     */
    public String urlDecode(String text) {
        if (text == null || text.isEmpty()) return "";
        try {
            return Util.decode(text);
        } catch (Throwable th) {
            SpiderDebug.log("urlDecode error: " + th.getMessage());
            return text;
        }
    }

    /**
     * Base64 编码方法
     * 
     * <p>将文本进行 Base64 编码，使用 UTF-8 编码格式。</p>
     * 
     * @param text 待编码的文本
     * @return Base64 编码后的字符串
     */
    public String base64Encode(String text) {
        if (text == null || text.isEmpty()) return "";
        try {
            return Crypto.base64Encode(text);
        } catch (Throwable th) {
            SpiderDebug.log("base64Encode error: " + th.getMessage());
            return "";
        }
    }

    /**
     * Base64 解码方法
     * 
     * <p>将 Base64 编码的文本进行解码，使用 UTF-8 编码格式。</p>
     * 
     * @param text Base64 编码的文本
     * @return 解码后的字符串
     */
    public String base64Decode(String text) {
        if (text == null || text.isEmpty()) return "";
        try {
            return Crypto.base64Decode(text);
        } catch (Throwable th) {
            SpiderDebug.log("base64Decode error: " + th.getMessage());
            return "";
        }
    }

    /**
     * MD5 加密方法
     * 
     * <p>将文本进行 MD5 加密，返回 32 位小写十六进制字符串。</p>
     * 
     * @param text 待加密的文本
     * @return MD5 加密后的字符串（32 位小写）
     */
    public String md5(String text) {
        if (text == null || text.isEmpty()) return "";
        try {
            return Crypto.md5(text);
        } catch (Throwable th) {
            SpiderDebug.log("md5 error: " + th.getMessage());
            return "";
        }
    }

    /**
     * 获取时间戳方法
     * 
     * <p>获取当前系统时间的时间戳（毫秒级）。</p>
     * 
     * @return 当前时间戳（毫秒）
     */
    public long timestamp() {
        return System.currentTimeMillis();
    }

    /**
     * 线程休眠方法
     * 
     * <p>使当前线程休眠指定的毫秒数。</p>
     * 
     * @param millis 休眠时间（毫秒）
     */
    public void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            SpiderDebug.log("sleep interrupted: " + e.getMessage());
        }
    }
}
