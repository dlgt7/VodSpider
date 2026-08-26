package com.github.catvod.spider;

import android.content.Context;
import android.util.Base64;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Filter;
import com.github.catvod.bean.Result;
import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.spider.xbpq.config.CssRule;
import com.github.catvod.spider.xbpq.config.RuleConfig;
import com.github.catvod.spider.xbpq.config.StringCutRule;
import com.github.catvod.spider.xbpq.extractor.ExtractorFactory;
import com.github.catvod.spider.xbpq.extractor.RegexFieldHelper;
import com.github.catvod.spider.xbpq.model.PlaySource;
import com.github.catvod.spider.xbpq.model.VodDetail;
import com.github.catvod.spider.xbpq.model.VodItem;
import com.github.catvod.spider.xbpq.network.HttpClient;
import com.github.catvod.spider.xbpq.network.OkHttpWrapper;
import com.github.catvod.spider.xbpq.network.interceptor.CookieManager;
import com.github.catvod.spider.xbpq.network.interceptor.WafBypassInterceptor;
import com.github.catvod.spider.xbpq.parser.JsParser;
import com.github.catvod.spider.xbpq.parser.JsonParser;

import org.json.JSONArray;
import org.json.JSONObject;

import java.math.BigInteger;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * XBPQ 爬虫（模块化版）
 * <p>
 * 规则为平铺键结构（{@link RuleConfig#convertChineseKeys} 转换后），
 * 五大内容方法 + 播放解析链完整实现：
 * <ol>
 *   <li>homeContent：class_name/class_value 或 fenlei+{class} 构建分类，筛选、热门推荐</li>
 *   <li>categoryContent：class_url 模板（{cateId}/{catePg}/{class}），二次截取 + 工厂提取器</li>
 *   <li>detailContent：detail_url 模板或 homeUrl 拼接，详情字段 + 播放线路（多线/单线）</li>
 *   <li>searchContent：search_url 模板（{wd} 编码/{pg}），JSON 自动探测 + 提取器</li>
 *   <li>playerContent：直链 → force_play → jump_url 提取 → 免嗅 → 嗅探兜底</li>
 * </ol>
 * 提取器经 {@link ExtractorFactory} 创建，CSS/正则规则自动分流。
 *
 * @see RuleConfig
 * @see ExtractorFactory
 */
public class XBPQ extends Spider {

    /** Base64 解码标志（URL 安全、无换行） */
    private static final int BASE64_FLAG = Base64.URL_SAFE | Base64.NO_WRAP;

    /** 默认视频直链扩展名（video_format 未配置时使用） */
    private static final String[] DEFAULT_VIDEO_EXTS = {
            ".m3u8", ".mp4", ".flv", ".avi", ".mkv", ".rmvb", ".wmv", ".mov"
    };

    /** 默认音频扩展名（video_format 未配置时使用） */
    private static final String[] DEFAULT_AUDIO_EXTS = {".mp3", ".m4a", ".wav", ".flac", ".aac"};

    // 分页统计正则
    private static final Pattern P_PAGE_TOTAL = Pattern.compile("共(\\d+)页");
    private static final Pattern P_TOTAL_COUNT = Pattern.compile("共(\\d+)条");
    private static final Pattern P_PAGE_CURRENT = Pattern.compile("(\\d+)/(\\d+)页");

    /** 分类页单页条数（limit 与翻页兜底共用） */
    private static final int PAGE_LIMIT = 20;

    /** 从 homeUrl 提取协议+主机 */
    private static final Pattern P_HOST = Pattern.compile("(https?://[^/]+)");
    
    /** 详情兜底：<title> 标签（借鉴 Yst.extractTitle） */
    private static final Pattern P_TITLE_TAG = Pattern.compile("(?i)<title>([^<]+)</title>");
    /** 详情兜底：og:title meta（property 在前形态） */
    private static final Pattern P_OG_TITLE = Pattern.compile(
            "(?i)property\\s*=\\s*[\"']og:title[\"']\\s+content\\s*=\\s*[\"']([^\"']+)[\"']");
    /** 详情兜底：og:image meta（property 在前形态） */
    private static final Pattern P_OG_IMAGE = Pattern.compile(
            "(?i)property\\s*=\\s*[\"']og:image[\"']\\s+content\\s*=\\s*[\"']([^\"']+)[\"']");
    /** 详情兜底：og:image meta（content 在前的反序形态） */
    private static final Pattern P_OG_IMAGE_REV = Pattern.compile(
            "(?i)content\\s*=\\s*[\"']([^\"']+)[\"']\\s+property\\s*=\\s*[\"']og:image[\"']");

    /** 规则 URL 占位符（{key}）：用于 class_url 中的分类筛选占位替换 */
    private static final Pattern P_PLACEHOLDER = Pattern.compile("\\{(\\w+)\\}");
    /** {{key}} 变量引用占位符 */
    private static final Pattern P_VAR_REF = Pattern.compile("\\{\\{(\\w+)\\}\\}");
    /** [工具:xxx] 工具链标记 */
    private static final Pattern P_TOOL = Pattern.compile("\\[工具:([^\\]]+)\\]");

    /** 懒加载图片占位图特征（借鉴 Hgdj/Glod/Duboku/Kkys/Libvio/Jable 的占位图过滤思路）。
     *  注意：不收 ".gif"/"empty"/"icon." 等过宽关键词，避免误杀正常封面 */
    private static final String[] IMG_PLACEHOLDER_MARKS = {
            "data:image", "base64,", "loading.gif", "load.gif", "blank.gif",
            "pic.png", "placeholder", "lazy_loading", "default.png", "nopic",
            "favicon", "logo", "logo_placeholder", "pic-loading", "noimage"
    };

    /** 常见懒加载图片属性优先级链（借鉴 Bttwo/Mtyy/Hgdj 的 firstNonEmpty 思路） */
    private static final String[] LAZY_IMG_ATTRS = {
            "data-original", "data-src", "data-lazy-src", "data-lazyload", "data-echo", "src"
    };

    /** HTML 实体归一表（借鉴 FengYe normalizeUrl & 各爬虫对 &#58; 的处理） */
    private static final String[][] HTML_ENTITIES = {
            {"&amp;", "&"}, {"&#38;", "&"},
            {"&#58;", ":"}, {"&#47;", "/"}, {"&#63;", "?"},
            {"&#61;", "="}, {"&#38;", "&"}, {"&quot;", "\""},
            {"&#39;", "'"}, {"&lt;", "<"}, {"&gt;", ">"},
            {"&nbsp;", " "}
    };

    /** JS unescape 的 %uXXXX / %XX 解码（借鉴 Ccys 的 unescape 思路） */
    private static final Pattern P_JS_UNICODE = Pattern.compile("(?i)%u([0-9a-f]{4})");
    private static final Pattern P_JS_BYTE = Pattern.compile("(?i)%([0-9a-f]{2})");

    // ==================== 实例状态 ====================
    // volatile：五大内容方法可能被框架并发调用，规则加载后的可见性由 JMM 保证
    //（原实现每次调用都进 synchronized 方法，锁释放隐式保证可见性但有无谓锁开销）
    private String ext;
    private volatile JSONObject rule;
    private String cachedHost; // absUrl 结果缓存，避免每次重复 hostOf(getHomeUrl())
    private static HttpClient httpClient;
    private volatile boolean reverse;
    private volatile boolean mergeLines;
    private volatile boolean hotRecommend;

    // ==================== 初始化 ====================

    @Override
    public void init(Context context, String extend) throws Exception {
        super.init(context, extend);
        this.ext = extend;
        // 不在这里预初始化 rule，让 fetchRule() 懒加载规则
    }

    /**
     * 懒加载规则：extend 为 http 链接时先拉取远程规则。
     * <p>
     * 优化：原实现为 synchronized 方法，五大内容方法每次调用都进入锁。
     * 现改为双重检查锁定——规则已加载时走无锁快速路径（volatile 读），
     * 仅首次加载在临界区内完成，远程规则不会被并发重复拉取。
     */
    protected void fetchRule() {
        if (rule != null) return;  // 快速路径：规则已加载
        if (ext == null) return;
        synchronized (this) {
            if (rule != null && rule.length() > 0) return;
            try {
                String content = ext.startsWith("http") ? fetchUrl(ext, null) : ext;
                if (content == null || content.isEmpty()) {
                    SpiderDebug.log("规则内容为空: " + ext);
                    return;
                }
                rule = RuleConfig.convertChineseKeys(JsonParser.parseObject(content));
                SpiderDebug.log("[XBPQ] 规则加载成功, homeUrl=[" + RuleConfig.getRuleVal(rule, "homeUrl") + "] class_url=[" + RuleConfig.getRuleVal(rule, "class_url") + "]");
            } catch (Exception e) {
                SpiderDebug.log("规则解析失败: " + e.getMessage());
                // 不设置 rule，保持 null，允许后续重试
                return;
            }
            // Maccms 自动猜测（借鉴 XBiu.guess_rule_* 思想）：
            // 当规则内容标记 auto_maccms=1 且关键字段缺失时，注入 Maccms 默认规则
            // 使极简规则可正常工作
            if (rule != null && "1".equals(RuleConfig.getRuleVal(rule, "auto_maccms"))) {
                applyMaccmsDefaults(rule);
            }
            reverse = "1".equals(RuleConfig.getRuleVal(rule, "reverse"));
            mergeLines = "1".equals(RuleConfig.getRuleVal(rule, "merge_lines"));
            hotRecommend = "1".equals(RuleConfig.getRuleVal(rule, "hot_recommend"));
        }
    }

    /**
     * Maccms 模板系统默认规则注入（借鉴 XBiu 自动猜测思路）。
     * <p>仅在对应字段为空时填入默认值：
     * <ul>
     *   <li>homeUrl 不为空时生效</li>
     *   <li>search_url 默认：{@code <homeUrl>index.php/ajax/suggest?mid=1&wd={wd}}（如 Maccms 10）</li>
     *   <li>class_url 默认：{@code <homeUrl>index.php/vod/show/id/{cateId}/page/{catePg}}</li>
     *   <li>detail_url 默认：{@code <homeUrl>index.php/vod/detail/id/{vid}.html}</li>
     * </ul>
     * 不覆盖用户已显式配置的字段。
     */
    private void applyMaccmsDefaults(JSONObject r) {
        try {
            String home = RuleConfig.getRuleVal(r, "homeUrl");
            if (home == null || home.isEmpty()) return;
            if (!home.endsWith("/")) home = home + "/";
            if (RuleConfig.getRuleVal(r, "search_url").isEmpty()) {
                r.put("search_url", home + "index.php/ajax/suggest?mid=1&wd={wd}");
                r.put("search_mode", "0");
            }
            if (RuleConfig.getRuleVal(r, "class_url").isEmpty()) {
                r.put("class_url", home + "index.php/vod/show/id/{cateId}/page/{catePg}");
            }
            if (RuleConfig.getRuleVal(r, "detail_url").isEmpty()) {
                r.put("detail_url", home + "index.php/vod/detail/id/{vid}.html");
            }
        } catch (Exception ignored) {
        }
    }

    /** 读规则值（"空"/"&&"占位视为未配置；rule 为 null 时安全返回默认值） */
    private String getVal(String key) {
        if (rule == null) return "";
        return stripBackticks(expandVariables(RuleConfig.getRuleVal(rule, key)));
    }

    /**
     * 获取首页 URL，优先展开动态域名链（dynamic_domain → home_url_c → homeUrl）。
     * <p>支持 {{key}} 变量引用递归展开。
     */
    private String getHomeUrl() {
        String dynamicDomain = getVal("dynamic_domain");
        if (!dynamicDomain.isEmpty()) return expandVariables(dynamicDomain);
        String homeUrlC = getVal("home_url_c");
        if (!homeUrlC.isEmpty()) return expandVariables(homeUrlC);
        String homeUrl = getVal("homeUrl");
        SpiderDebug.log("[XBPQ] getHomeUrl=[" + homeUrl + "]");
        return homeUrl;
    }

    /**
     * 展开 {{key}} 变量引用，递归处理嵌套引用，最多 10 轮防止死循环。
     * <p>每轮从规则对象中读取 key 对应的值（再次展开），直到无 {{}} 或达到最大轮数。
     */
    private String expandVariables(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        boolean hasVar = value.indexOf('{') >= 0;
        if (!hasVar) {
            return applyReplaceInValue(executeTools(value));
        }
        for (int round = 0; round < 10; round++) {
            Matcher m = P_VAR_REF.matcher(value);
            if (!m.find()) break;
            StringBuilder sb = new StringBuilder();
            m.reset();
            boolean changed = false;
            while (m.find()) {
                String varKey = m.group(1);
                String varVal = RuleConfig.getRuleVal(rule, varKey);
                if (varVal != null && !varVal.isEmpty()) {
                    varVal = executeTools(varVal);
                    m.appendReplacement(sb, Matcher.quoteReplacement(varVal));
                    changed = true;
                } else {
                    m.appendReplacement(sb, Matcher.quoteReplacement(m.group(0)));
                }
            }
            m.appendTail(sb);
            if (!changed) break;
            value = sb.toString();
        }
        value = applyReplaceInValue(value);
        return executeTools(value);
    }

    /**
     * 去除字符串首尾的反引号（`），兼容部分配置习惯中使用反引号包裹 URL 的写法。
     */
    private static String stripBackticks(String value) {
        if (value == null || value.isEmpty()) return value;
        if (value.length() >= 2 && value.charAt(0) == '`' && value.charAt(value.length() - 1) == '`') {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    /**
     * 处理字符串中的 [替换:a>>b#x>>y] 语法（不限于 [工具:xxx] 内）。
     * 用于变量定义中直接使用替换语法，如 "域名-c": "{{主页url-c}}[替换:https://>>https://666.]"
     */
    private String applyReplaceInValue(String value) {
        if (value == null || value.isEmpty() || value.indexOf('[') < 0) return value;
        int ri = value.indexOf("[替换:");
        if (ri < 0) return value;
        int re = value.indexOf("]", ri);
        if (re <= ri) return value;
        String replaceContent = value.substring(ri + 4, re);
        String result = value.substring(0, ri);
        // 处理剩余的 [替换:] 部分
        for (String pair : replaceContent.split("#")) {
            int idx = pair.indexOf(">>");
            if (idx > 0) {
                result = result.replace(pair.substring(0, idx).trim(), pair.substring(idx + 2).trim());
            }
        }
        result += value.substring(re + 1);
        return result;
    }

    /**
     * 执行 [工具:xxx] 工具链。支持：
     * <ul>
     *   <li>[工具:源码] — 抓取发布页HTML并返回</li>
     *   <li>[工具:重定向] — 跟随HTTP重定向获取最终URL</li>
     *   <li>[工具:解url] — URL解码</li>
     *   <li>[工具:解b64] / [工具:Base64#解密] — Base64解码</li>
     *   <li>[工具:SHA] — SHA-256哈希</li>
     *   <li>[工具:1截取N] — 取第N个片段</li>
     *   <li>[工具:随机字符-N-唯一] — 生成随机字符串</li>
     *   <li>[工具:源码转b64#解密aes-key-iv-AES/CBC/PKCS7Padding] — AES解密</li>
     * </ul>
     */
    private String executeTools(String input) {
        if (input == null || input.isEmpty() || input.indexOf('[') < 0) return input;
        Matcher m = P_TOOL.matcher(input);
        if (!m.find()) return input;
        StringBuilder sb = new StringBuilder();
        m.reset();
        while (m.find()) {
            String toolSpec = m.group(1);
            String result = executeSingleTool(toolSpec);
            m.appendReplacement(sb, Matcher.quoteReplacement(result == null ? "" : result));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private String executeSingleTool(String spec) {
        if (spec == null || spec.isEmpty()) return "";
        String[] parts = spec.split("#", 2);
        String toolName = parts[0].trim();
        String arg = parts.length > 1 ? parts[1].trim() : "";

        try {
            switch (toolName) {
                case "源码":
                    // 从上下文获取主页URL，抓发布页
                    return fetchUrl(getHomeUrl(), buildHeaders(null));
                case "重定向":
                    return getRedirectUrl(arg);
                case "解url":
                    return java.net.URLDecoder.decode(arg, "UTF-8");
                case "urlDecode":
                    return java.net.URLDecoder.decode(arg, "UTF-8");
                case "urlEncode":
                    return java.net.URLEncoder.encode(arg, "UTF-8");
                case "解b64":
                case "Base64#解密":
                    return new String(java.util.Base64.getDecoder().decode(arg), "UTF-8");
                case "SHA":
                    java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
                    byte[] digest = md.digest(arg.getBytes("UTF-8"));
                    StringBuilder hex = new StringBuilder();
                    for (byte b : digest) hex.append(String.format("%02x", b));
                    return hex.toString();
                case "1截取": {
                    // 修复：原实现错误地split了arg（数字参数）而非输入字符串
                    // 正确语义：将输入字符串按分隔符截取第N段（从1开始计数）
                    // 格式：1截取N 或 1截取N#分隔符，默认分隔符为","
                    String[] argParts = arg.split("#", 2);
                    int n = Integer.parseInt(argParts[0].trim());
                    // 注意：此工具的"输入"来自 [工具:1截取N] 前面的文本上下文，
                    // 但由于 executeSingleTool 不接收原始输入，这里保留对arg的处理。
                    // 实际使用中，arg应为 待截取字符串#N 的格式或由调用方传入完整文本。
                    // 兼容旧调用：若arg为纯数字，返回空串（需配合上下文使用）
                    if (argParts.length < 2) {
                        SpiderDebug.log("1截取 参数格式错误，期望: N#待截取文本 或在上下文中使用");
                        return "";
                    }
                    String input = argParts[1];
                    String[] ss = input.split(",", -1);
                    // n为1-based索引，转为0-based
                    int idx = n - 1;
                    return (idx >= 0 && idx < ss.length) ? ss[idx] : "";
                }
                case "随机字符-3-唯一":
                    return randomString(3);
                case "源码转b64": {
                    // 格式：源码转b64#解密aes-key-iv-AES/CBC/PKCS7Padding
                    String decrypted = executeAesDecrypt(arg, "f5d965df75336270", "97b60394abc2fbe1");
                    return new String(java.util.Base64.getEncoder().encode(decrypted.getBytes("UTF-8")), "UTF-8");
                }
                default:
                    return "";
            }
        } catch (Exception e) {
            SpiderDebug.log("工具执行失败: " + toolName + " - " + e.getMessage());
            return "";
        }
    }

    private String executeAesDecrypt(String input, String key, String iv) throws Exception {
        javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("AES/CBC/PKCS7Padding");
        javax.crypto.spec.IvParameterSpec ivSpec = new javax.crypto.spec.IvParameterSpec(iv.getBytes("UTF-8"));
        javax.crypto.spec.SecretKeySpec keySpec = new javax.crypto.spec.SecretKeySpec(key.getBytes("UTF-8"), "AES");
        cipher.init(javax.crypto.Cipher.DECRYPT_MODE, keySpec, ivSpec);
        byte[] decoded = java.util.Base64.getDecoder().decode(input);
        byte[] decrypted = cipher.doFinal(decoded);
        return new String(decrypted, "UTF-8");
    }

    /** 不跟随重定向的专用客户端（静态复用，避免每次新建浪费连接池） */
    private static volatile okhttp3.OkHttpClient noRedirectClient;

    private static okhttp3.OkHttpClient getNoRedirectClient() {
        if (noRedirectClient == null) {
            synchronized (XBPQ.class) {
                if (noRedirectClient == null) {
                    noRedirectClient = new okhttp3.OkHttpClient.Builder()
                            .followRedirects(false)
                            .followSslRedirects(false)
                            .build();
                }
            }
        }
        return noRedirectClient;
    }

    private String getRedirectUrl(String url) {
        try {
            if (url == null || url.isEmpty()) return url;
            // 防御：[工具:重定向] 的参数来自规则配置/外部输入，可能指向内网地址
            // 统一走 SSRF 防护（allow_internal=1 可放行）
            if (isSsrfBlocked(url)) {
                SpiderDebug.log("SSRF 拦截: " + url);
                return url;
            }
            // 复用不跟随重定向的客户端实例，避免每次请求都创建新连接池
            okhttp3.Request req = new okhttp3.Request.Builder().url(url).get().build();
            okhttp3.Response resp = getNoRedirectClient().newCall(req).execute();
            try {
                String location = resp.header("Location");
                return location != null ? location : url;
            } finally {
                resp.close();
            }
        } catch (Exception e) {
            return url;
        }
    }

    /** 复用Random实例，避免每次调用都新建（ThreadLocal保证线程安全） */
    private static final ThreadLocal<java.util.Random> THREAD_LOCAL_RANDOM =
            ThreadLocal.withInitial(java.util.Random::new);

    private String randomString(int length) {
        String chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder sb = new StringBuilder(length);
        java.util.Random rand = THREAD_LOCAL_RANDOM.get();
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(rand.nextInt(chars.length())));
        }
        return sb.toString();
    }

    // ==================== 取值增强助手（借鉴 20+ 爬虫新思想，不新增字段） ====================

    /**
     * 图片 URL 净化（借鉴 nongm.fixPic / Hgdj / Glod 的占位图过滤思路）。
     * <ul>
     *   <li>HTML 实体归一（&#58;→: 等）</li>
     *   <li>过滤 loading.gif / blank.gif / base64 / placeholder 等占位图，返回 null 交由调用方回退</li>
     *   <li>相对/协议相对地址补全为主机绝对地址（复用 absUrl）</li>
     * </ul>
     * @return 干净图片地址；占位图返回 ""
     */
    private String cleanImageUrl(String url) {
        if (url == null) return "";
        url = normalizeEntity(url).trim();
        if (url.isEmpty()) return "";
        String lower = url.toLowerCase();
        for (String mark : IMG_PLACEHOLDER_MARKS) {
            if (lower.contains(mark)) return "";
        }
        return absUrl(url);
    }

    /**
     * HTML 实体归一（借鉴 FengYe.normalizeUrl 思路），处理 &amp; / &#58; 等常见实体。
     */
    private String normalizeEntity(String text) {
        if (text == null || text.indexOf('&') < 0) return text;
        String out = text;
        for (String[] pair : HTML_ENTITIES) {
            out = out.replace(pair[0], pair[1]);
        }
        return out;
    }

    /** 标题噪声后缀预编译（cleanTitle 热点：列表/搜索每条结果都调用，避免每次重编译正则） */
    private static final Pattern P_TITLE_NOISE = Pattern.compile(
            "[\\(\\[（【]\\s*(高清|HD|BD|TC|DVDrip|评分\\d+(\\.\\d+)?)\\s*[\\)\\]）】]");

    /**
     * 标题/备注清洗（借鉴 Hgdj.cleanName / Glod.cleanTitle 思路）：
     * 去除《》、首尾空格、常见的 "高清"、"评分N.N" 等质量/分数噪声后缀。
     */
    private String cleanTitle(String title) {
        if (title == null) return "";
        String out = title.trim().replace("《", "").replace("》", "");
        // 去除形如 "(高清)" / "[HD]" / "评分8.5" 等常见尾缀
        out = P_TITLE_NOISE.matcher(out).replaceAll("").trim();
        return out;
    }

    /**
     * JS unescape 解码（借鉴 Ccys.unescape 思路），处理 %uXXXX 与 %XX 转义，
     * 供 detail_array / jump_url 等文本规则中出现的 JS 转义串清洗。
     */
    private String jsUnescape(String text) {
        if (text == null || text.indexOf('%') < 0) return text;
        try {
            Matcher m = P_JS_UNICODE.matcher(text);
            StringBuffer sb = new StringBuffer();
            while (m.find()) {
                m.appendReplacement(sb, String.valueOf((char) Integer.parseInt(m.group(1), 16)));
            }
            m.appendTail(sb);
            String tmp = sb.toString();
            Matcher m2 = P_JS_BYTE.matcher(tmp);
            StringBuffer sb2 = new StringBuffer();
            while (m2.find()) {
                m2.appendReplacement(sb2, String.valueOf((char) Integer.parseInt(m2.group(1), 16)));
            }
            m2.appendTail(sb2);
            return sb2.toString();
        } catch (Exception e) {
            return text;
        }
    }

    /** 是否占位图（供外部规则回退判断） */
    private boolean isPlaceholderImage(String url) {
        if (url == null) return true;
        String lower = url.toLowerCase();
        for (String mark : IMG_PLACEHOLDER_MARKS) {
            if (lower.contains(mark)) return true;
        }
        return false;
    }

    /**
     * 图片代理转换（借鉴 XYQBiu.fixCover）。
     * 当 PicNeedProxy=1 时，将图片地址转换为 proxy://do=XBPQ 代理形式，
     * 使图片可经框架代理后展示，兼容防盗链图片。
     */
    private String fixCover(String cover, String site) {
        try {
            return "proxy://do=XBPQ&site=" + site + "&pic=" + URLEncoder.encode(cover, "UTF-8");
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        return cover;
    }

    /** PC UA */
    private static final String PC_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/110.0.0.0 Safari/537.36 Edg/110.0.1587.57";
    /** Mobile UA */
    private static final String MOBILE_UA = "Mozilla/5.0 (Linux; Android 13; Xiaomi 13 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/113.0.5672.131 Mobile Safari/537.36";
    /** iOS UA */
    private static final String IOS_UA = "Mozilla/5.0 (iPhone; CPU iPhone OS 16_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.0 Mobile/15E148 Safari/604.1";

    // ==================== 请求头 ====================

    /**
     * 构建请求头 Map
     * <p>支持三种配置形式：
     * <ol>
     *   <li>JSON 对象：{"User-Agent": "...", "Referer": "..."} → mergeJsonHeader 处理</li>
     *   <li>UA 简写："手机"/"电脑"/"苹果手机" → 替换为对应 UA 字符串</li>
     *   <li>平铺键 "User-Agent" / "Referer" → 直接 put</li>
     * </ol>
     */
    private Map<String, String> buildHeaders(String sectionKey) {
        Map<String, String> headers = new HashMap<>();
        String headerRaw = getVal("header");
        // 优先处理 UA 简写
        if (headerRaw != null && !headerRaw.isEmpty() && !headerRaw.startsWith("{")) {
            String ua = resolveUaAlias(headerRaw);
            if (ua != null) headers.put("User-Agent", ua);
        } else {
            // JSON 格式请求头
            mergeJsonHeader(headers, headerRaw);
        }
        if (sectionKey != null) {
            String sectionRaw = getVal(sectionKey);
            if (sectionRaw != null && !sectionRaw.isEmpty() && !sectionRaw.startsWith("{")) {
                String ua = resolveUaAlias(sectionRaw);
                if (ua != null) headers.put("User-Agent", ua);
            } else {
                mergeJsonHeader(headers, sectionRaw);
            }
        }
        String ua = getVal("User-Agent");
        if (ua != null && !ua.isEmpty()) headers.put("User-Agent", resolveUaAlias(ua));
        String referer = getVal("Referer");
        if (referer != null && !referer.isEmpty()) headers.put("Referer", referer);
        return headers.isEmpty() ? null : headers;
    }

    /**
     * 将 UA 简写（"手机"/"电脑"/"苹果手机"）替换为实际 UA 字符串，其他原样返回。
     */
    private String resolveUaAlias(String value) {
        if (value == null) return null;
        String v = value.trim();
        if ("手机".equals(v) || "MOBILE_UA".equals(v)) return MOBILE_UA;
        if ("电脑".equals(v) || "PC_UA".equals(v)) return PC_UA;
        if ("苹果手机".equals(v) || "IOS_UA".equals(v)) return IOS_UA;
        return v;
    }

    /** 将 JSON 字符串形式的请求头合并进 map，非法 JSON 忽略 */
    private void mergeJsonHeader(Map<String, String> headers, String json) {
        if (json == null || json.isEmpty()) return;
        JSONObject obj = JsonParser.safeParseObject(json);
        if (obj == null) return;
        JSONArray nk = obj.names();
        if (nk != null) {
            for (int i = 0; i < nk.length(); i++) {
                String key = nk.optString(i);
                if (key == null) continue;
                String value = obj.optString(key, "");
                if (!value.isEmpty()) headers.put(key, value);
            }
        }
    }

    // ==================== 网络请求 ====================

    protected String fetchUrl(String url, Map<String, String> headers) {
        return fetchUrl(url, headers, null);
    }

    protected String fetchUrl(String url, Map<String, String> headers, String timeoutKey) {
        if (url == null || url.isEmpty()) return "";
        // SSRF 防护：拦截内网/保留地址（allow_internal=1 可放行，供内网自测）
        if (isSsrfBlocked(url)) {
            SpiderDebug.log("SSRF 拦截: " + url);
            return "";
        }
        final int timeout = resolveTimeout(timeoutKey);
        return doWithRetry(url, () -> httpClient().string(url, headers, timeout));
    }

    /** 解析超时配置（默认 10 秒，可按 timeoutKey 指定规则键） */
    private int resolveTimeout(String timeoutKey) {
        int timeout = 10;
        if (rule != null) {
            String key = (timeoutKey != null && !timeoutKey.isEmpty()) ? timeoutKey : "timeout";
            String t = RuleConfig.getRuleVal(rule, key);
            if (!t.isEmpty()) {
                try { timeout = Integer.parseInt(t.trim()); } catch (NumberFormatException ignored) {}
            }
        }
        return timeout;
    }

    /** 解析重试次数（借鉴 Y360.fetchWithRetry / Gold 的容错思路）：默认 1 次（即不重试），
     *  规则可配 retry=3 启用最多 3 次重试，提升弱网/不稳定站点鲁棒性。 */
    private int resolveRetry() {
        int retry = 1;
        if (rule != null) {
            String r = RuleConfig.getRuleVal(rule, "retry");
            if (!r.isEmpty()) {
                try { retry = Math.max(1, Integer.parseInt(r.trim())); } catch (NumberFormatException ignored) {}
            }
        }
        return retry;
    }

    /** 统一重试执行：空响应/异常均计入重试，全部失败返回空串（GET/POST 共用） */
    private String doWithRetry(String url, Callable<String> action) {
        int retry = resolveRetry();
        Exception lastErr = null;
        for (int attempt = 0; attempt < retry; attempt++) {
            try {
                String body = action.call();
                if (body != null && !body.isEmpty()) return body;
                lastErr = new Exception("空响应");
            } catch (Exception e) {
                lastErr = e;
                SpiderDebug.log("请求失败(第" + (attempt + 1) + "次): " + url + " " + e.getMessage());
            }
        }
        SpiderDebug.log("请求失败: " + url + " " + (lastErr != null ? lastErr.getMessage() : ""));
        return "";
    }

    /** 是否应拦截该 URL（内网/保留地址且未显式允许内网访问） */
    private boolean isSsrfBlocked(String url) {
        if (!httpClient().isInternalUrl(url)) return false;
        return !"1".equals(getVal("allow_internal"));
    }

    /** 线程安全的单例HttpClient（双重检查锁定） */
    public static HttpClient httpClient() {
        if (httpClient == null) {
            synchronized (XBPQ.class) {
                if (httpClient == null) {
                    httpClient = new OkHttpWrapper();
                    httpClient.addInterceptor(new WafBypassInterceptor());
                    httpClient.addInterceptor(new CookieManager());
                }
            }
        }
        return httpClient;
    }

    // ==================== 首页 ====================

    @Override
    public String homeContent(boolean filter) {
        try {
            fetchRule();
            if (rule == null) return "";

            // 读取动态筛选配置（借鉴 XYQHiker / XYQBiu homeContent）
            String classNamesCfg = getVal("class_name");
            String classValuesCfg = getVal("class_value");
            String fclass_name = getVal("fclass_name");
            String fclass_value = getVal("fclass_value");
            String fcatelog_name = getVal("fcatelog_name");
            String fcatelog_value = getVal("fcatelog_value");
            String farea_name = getVal("farea_name");
            String farea_value = getVal("farea_value");
            String fyear_name = getVal("fyear_name");
            String fyear_value = getVal("fyear_value");
            String flang_name = getVal("flang_name");
            String flang_value = getVal("flang_value");
            String fsort_name = getVal("fsort_name");
            String fsort_value = getVal("fsort_value");
            if (fsort_name.isEmpty()) fsort_name = "时间&人气&评分";
            if (fsort_value.isEmpty()) fsort_value = "time&hits&score";

            // 分类列表
            List<Class> classes = new ArrayList<>();
            JSONArray rawClasses = buildClassList();
            SpiderDebug.log("[XBPQ] homeContent rawClasses.length=" + rawClasses.length() + " class_url=[" + getVal("class_url") + "]");
            for (int i = 0; i < rawClasses.length(); i++) {
                JSONObject cls = rawClasses.getJSONObject(i);
                classes.add(new Class(
                        cls.optString("type_id", ""),
                        cls.optString("type_name", "")));
            }

            // 二级目录过滤
            String twoLevelDir = getVal("二级目录");
            if (!twoLevelDir.isEmpty() && twoLevelDir.contains("|")) {
                String folders = twoLevelDir.split("\\|")[0].trim();
                String mode = twoLevelDir.contains("|") ? twoLevelDir.substring(twoLevelDir.indexOf("|") + 1) : "";
                if (mode.contains("folder")) {
                    List<Class> filtered = new ArrayList<>();
                    for (Class c : classes) {
                        boolean isFolder = false;
                        for (String folder : folders.split(",")) {
                            if (c.getTypeName().contains(folder.trim())) { isFolder = true; break; }
                        }
                        if (!isFolder) filtered.add(c);
                    }
                    classes = filtered;
                }
            }

            // 筛选处理（filterdata 远程/EXT/内置）
            JSONObject filters = null;
            if (filter) {
                String filterdata = getVal("filterdata");

                if (filterdata.startsWith("clan://") || filterdata.startsWith("http") || filterdata.startsWith("./")) {
                    // 远程加载筛选 JSON
                    try {
                        String resp = fetchUrl(filterdata, null).trim();
                        if (resp.startsWith("{") && resp.endsWith("}")) {
                            filters = new JSONObject(resp);
                        }
                    } catch (Exception e) {
                        SpiderDebug.log("filterdata 远程加载失败: " + e.getMessage());
                    }
                } else if ("EXT".equalsIgnoreCase(filterdata)) {
                    // 动态构建筛选（借鉴 XYQHiker buildFilter）
                    LinkedHashMap<String, List<Filter>> filterMap = buildFilter(
                            classValuesCfg, getVal("class_url"),
                            fclass_name, fclass_value,
                            fcatelog_name, fcatelog_value,
                            farea_name, farea_value,
                            fyear_name, fyear_value,
                            flang_name, flang_value,
                            fsort_name, fsort_value);
                    if (filterMap != null && !filterMap.isEmpty()) {
                        filters = new JSONObject(new com.google.gson.Gson().toJson(filterMap));
                    }
                } else if (!filterdata.isEmpty()) {
                    // 内置 filter JSON 对象（兼容旧写法）
                    Object raw = rule.opt("filter");
                    if (raw instanceof JSONObject) {
                        filters = (JSONObject) raw;
                    }
                } else {
                    // 无 filterdata 但配置了动态筛选字段时，也构建 EXT 筛选
                    String classUrlTpl = getVal("class_url");
                    boolean hasPlaceholders = classUrlTpl.contains("{class}") || classUrlTpl.contains("{area}")
                            || classUrlTpl.contains("{year}") || classUrlTpl.contains("{lang}")
                            || classUrlTpl.contains("{by}") || classUrlTpl.contains("{cateId}");
                    if (hasPlaceholders) {
                        LinkedHashMap<String, List<Filter>> filterMap = buildFilter(
                                classValuesCfg, classUrlTpl,
                                fclass_name, fclass_value,
                                fcatelog_name, fcatelog_value,
                                farea_name, farea_value,
                                fyear_name, fyear_value,
                                flang_name, flang_value,
                                fsort_name, fsort_value);
                        if (filterMap != null && !filterMap.isEmpty()) {
                            filters = new JSONObject(new com.google.gson.Gson().toJson(filterMap));
                        }
                    } else {
                        // 降级：尝试从 rule 中取 filter
                        Object raw = rule.opt("filter");
                        if (raw instanceof JSONObject) {
                            filters = (JSONObject) raw;
                        }
                    }
                }
            }

            // 构建结果
            if (filter && filters != null) {
                return Result.string(classes, filters).toString();
            }
            // 热门推荐：参考 XYQHiker 将 hot 列表挂入 result.list 后返回
            if (hotRecommend) {
                try {
                    JSONArray hot = fetchHotRecommend();
                    if (hot.length() > 0) {
                        JSONObject out = new JSONObject(Result.get().classes(classes).string());
                        out.put("list", hot);
                        return out.toString();
                    }
                } catch (Exception ignored) {}
            }
            return Result.get().classes(classes).string();
        } catch (Exception e) {
            SpiderDebug.log(e);
            return "";
        }
    }

    /**
     * 分类列表构建。
     * <p>兼容规则中实际出现的多种写法（字段同名但格式不同，视为同一功能，不新增键）：
     * <ol>
     *   <li>{@code 特殊分类链接="name$url#name$url"} —— name$id 用 # 串联（最常见，优先）</li>
     *   <li>{@code 分类="电影$1#剧集$2#..."} —— name$id 用 # 串联（次常见）</li>
     *   <li>{@code 分类url} 含 {class} —— 分类名即作为 type_id</li>
     *   <li>{@code 分类名称 + 分类值} 两个 & 分隔的并行数组</li>
     *   <li>{@code 分类 + 分类值}（如「电影&剧集&...」配「1&2&...」）回退写法</li>
     * </ol>
     */
    private JSONArray buildClassList() throws Exception {
        JSONArray classes = new JSONArray();
        String classUrl = getVal("class_url");
        String fenlei = getVal("fenlei");
        String specialLinks = getVal("特殊分类链接");
        String classNames = getVal("class_name");
        String classValues = getVal("class_value");

        // 0) 特殊分类链接 优先：name$url#name$url 格式，含动态域名变量 {{域名-c}} 等
        //    支持 ;;z 语法（备用首页）、[替换:xxx] 备用URL
        if (!specialLinks.isEmpty() && specialLinks.contains("$")) {
            for (String pair : specialLinks.split("#")) {
                pair = pair.trim();
                if (pair.isEmpty()) continue;
                int idx = pair.indexOf("$");
                if (idx < 0) continue;
                String name = pair.substring(0, idx).trim();
                String rawUrl = pair.substring(idx + 1).trim();
                if (name.isEmpty()) continue;
                // 处理 [替换:xxx];;z / ;;mrc* 语法：;;z/;;mrc 表示使用 [替换:...] 中的 URL 作为首页
                String url = expandVariables(rawUrl);
                int zIdx = url.indexOf(";;z");
                int mrcIdx = url.indexOf(";;mrc");
                if (zIdx >= 0 || mrcIdx >= 0) {
                    int keywordIdx = (zIdx >= 0 && mrcIdx >= 0) ? Math.min(zIdx, mrcIdx)
                                    : (zIdx >= 0 ? zIdx : mrcIdx);
                    boolean isMrc = url.startsWith(";;mrc", keywordIdx);
                    // ;;mrc* 后缀：提取关键字之后附加到 URL 的内容（如 ";;mrc*abc" 的 "*abc"）
                    String mrcSuffix = "";
                    if (isMrc && keywordIdx + 5 <= url.length()) {
                        mrcSuffix = url.substring(keywordIdx + 5);
                    }
                    // 尝试从最近的 [替换:xxx] 提取真实 URL 作为首页
                    int bracketOpen = url.lastIndexOf("[", keywordIdx);
                    int bracketClose = (bracketOpen >= 0) ? url.indexOf("]", bracketOpen) : -1;
                    if (bracketOpen >= 0 && bracketClose > bracketOpen && bracketClose < keywordIdx) {
                        String inner = url.substring(bracketOpen + 1, bracketClose);
                        if (inner.startsWith("替换:")) inner = inner.substring(3);
                        url = expandVariables(inner);
                    } else {
                        // 无 [替换:] 包裹：截断到关键字前并去掉可能残留的 "["
                        url = url.substring(0, keywordIdx).trim();
                        int lt = url.lastIndexOf("[");
                        if (lt >= 0) url = url.substring(0, lt).trim();
                    }
                    if (!mrcSuffix.isEmpty()) {
                        url = url + mrcSuffix;
                    }
                }
                addClass(classes, url, name);
            }
            return classes;
        }

        // 1) name$id#name$id 格式（最常见，约 1/3 规则使用）
        if (fenlei.contains("$")) {
            for (String pair : fenlei.split("[#&]")) {
                pair = pair.trim();
                if (pair.isEmpty()) continue;
                int idx = pair.indexOf("$");
                String name = idx >= 0 ? pair.substring(0, idx).trim() : pair;
                String id = idx >= 0 ? pair.substring(idx + 1).trim() : pair;
                if (name.isEmpty()) continue;
                addClass(classes, id, name);
            }
            return classes;
        }

        // 2) class_url 使用 {class}：分类名直接作为 type_id
        //    增强：兼容 & / , / | 多种分隔符（借鉴 Maccms 极简风格 + XBiubiu 的宽松解析）
        if (classUrl.contains("{class}") && !fenlei.isEmpty()) {
            for (String name : fenlei.split("[&,\\u007c]")) {
                name = name.trim();
                if (name.isEmpty()) continue;
                addClass(classes, name, name);
            }
            if (classes.length() > 0) return classes;
        }

        // 3) 分类名称 + 分类值（并行 & 分隔）
        if (!classNames.isEmpty() && !classValues.isEmpty()) {
            addParallel(classes, classNames, classValues);
            return classes;
        }

        // 4) 分类（名称 &）+ 分类值（id &）回退写法
        if (!fenlei.isEmpty() && !classValues.isEmpty()) {
            addParallel(classes, fenlei, classValues);
            return classes;
        }

        // 5) 兜底：分类为 & 分隔纯名称且无 分类值 时，名称同时作为 type_id
        if (!fenlei.isEmpty()) {
            for (String name : fenlei.split("&")) {
                name = name.trim();
                if (!name.isEmpty()) addClass(classes, name, name);
            }
            if (classes.length() > 0) return classes;
        }

        return classes;
    }

    private void addClass(JSONArray classes, String id, String name) {
        try {
            JSONObject item = new JSONObject();
            item.put("type_id", id);
            item.put("type_name", name);
            classes.put(item);
        } catch (Exception ignored) {
        }
    }

    private void addParallel(JSONArray classes, String names, String values) {
        String[] ns = names.split("&");
        String[] vs = values.split("&");
        for (int i = 0; i < ns.length && i < vs.length; i++) {
            String n = ns[i].trim();
            if (n.isEmpty()) continue;
            addClass(classes, vs[i].trim(), n);
        }
    }

    /** 筛选配置：filter 键为对象或 JSON 字符串 */
    private JSONObject extractFilters() {
        Object raw = rule.opt("filter");
        if (raw == null) return null;
        if (raw instanceof JSONObject) return (JSONObject) raw;
        return JsonParser.safeParseObject(String.valueOf(raw));
    }

    /**
     * 动态筛选构建（借鉴 XYQHiker.buildFilter）。
     * 遍历每个分类，根据 class_url 模板中的占位符（{class}/{area}/{year}/{lang}/{by}）
     * 生成对应的筛选组。name=="*" 时用 id 作为显示名；含 "||" 时按索引分割。
     */
    private LinkedHashMap<String, List<Filter>> buildFilter(String categoryValues, String urlTemplate,
            String fclassId, String fclassName,
            String fcatelogId, String fcatelogName,
            String fareaId, String fareaName,
            String fyearId, String fyearName,
            String flangId, String flangName,
            String fsortId, String fsortName) {
        try {
            LinkedHashMap<String, List<Filter>> result = new LinkedHashMap<>();
            String[] rawCategories = categoryValues.split("&");
            for (int i = 0; i < rawCategories.length; i++) {
                String catValue = rawCategories[i].trim().replaceAll("＆＆", "&");
                if (catValue.isEmpty()) continue;
                List<Filter> filters = new ArrayList<>();

                // class（子分类）
                if (!fclassId.isEmpty() && !fclassName.isEmpty() && urlTemplate.contains("{class}")) {
                    String displayName = fclassName.equals("*") ? fclassId : fclassName;
                    filters.add(buildFilterEntry("class", "类型", fclassId, displayName));
                }
                // area（地区）
                if (!fareaId.isEmpty() && !fareaName.isEmpty() && urlTemplate.contains("{area}")) {
                    String displayName = fareaName.equals("*") ? fareaId : fareaName;
                    filters.add(buildFilterEntry("area", "地区", fareaId, displayName));
                }
                // year（年份）
                if (!fyearId.isEmpty() && !fyearName.isEmpty() && urlTemplate.contains("{year}")) {
                    String displayName = fyearName.equals("*") ? fyearId : fyearName;
                    filters.add(buildFilterEntry("year", "年份", fyearId, displayName));
                }
                // lang（语言）
                if (!flangId.isEmpty() && !flangName.isEmpty() && urlTemplate.contains("{lang}")) {
                    String displayName = flangName.equals("*") ? flangId : flangName;
                    filters.add(buildFilterEntry("lang", "语言", flangId, displayName));
                }
                // by（排序）
                if (!fsortId.isEmpty() && !fsortName.isEmpty() && urlTemplate.contains("{by}")) {
                    String displayName = fsortName.equals("*") ? fsortId : fsortName;
                    filters.add(buildFilterEntry("by", "排序", fsortId, displayName));
                }
                // cateId（分类本身）
                if (!urlTemplate.contains("{cateId}") || !catValue.isEmpty()) {
                    // 当 URL 中有 {cateId} 时加入分类筛选
                    filters.add(buildFilterEntry("cateId", "分类", catValue, catValue));
                }

                result.put(catValue, filters);
            }
            return result;
        } catch (Exception e) {
            SpiderDebug.log("buildFilter 出错: " + e.getMessage());
            return null;
        }
    }

    /** 构建单个 Filter 条目 */
    private Filter buildFilterEntry(String key, String nameLabel, String valueVal, String nameVal) {
        try {
            List<Filter.Value> values = new ArrayList<>();
            values.add(new Filter.Value("全部", ""));
            if (!valueVal.contains("&") && !valueVal.equals("空")) {
                values.add(new Filter.Value(nameVal, valueVal.replaceAll("＆＆", "&")));
            } else if (valueVal.contains("&") && !valueVal.equals("空")) {
                String[] nameParts = nameVal.split("&");
                String[] valueParts = valueVal.split("&");
                for (int i = 0; i < nameParts.length && i < valueParts.length; i++) {
                    values.add(new Filter.Value(nameParts[i], valueParts[i].replaceAll("＆＆", "&")));
                }
            }
            return new Filter(key, nameLabel, values);
        } catch (Exception e) {
            SpiderDebug.log(e);
            return null;
        }
    }

    /** 热门推荐：抓取主页并用列表规则提取 */
    private JSONArray fetchHotRecommend() throws Exception {
        String homeUrl = getHomeUrl();
        if (homeUrl.isEmpty()) return new JSONArray();
        String body = fetchUrl(homeUrl, buildHeaders(null));
        if (body.isEmpty()) return new JSONArray();
        JSONArray videos = ExtractorFactory
                .createVideoListExtractor(CssRule.isCssRule(getVal("list_array")))
                .extract(body, rule);
        if (videos == null) return new JSONArray();
        return applyListPostProcess(videos);
    }

    // ==================== 分类页 ====================

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) {
        try {
            fetchRule();
            if (rule == null) return "";
            String url = buildCategoryUrl(tid, pg, extend);
            if (url.isEmpty()) return "";

            String body = fetchUrl(url, buildHeaders(null));
            if (body.isEmpty()) return "";

            JSONArray videos;
            String catMode = getVal("cat_mode");
            // cat_mode=0：JSON 模式（借鉴 XYQBiu categoryContent 第237-280行）
            if ("0".equals(catMode)) {
                videos = parseCatJsonMode(body, url);
            } else {
                videos = ExtractorFactory
                        .createVideoListExtractor(CssRule.isCssRule(getVal("list_array")))
                        .extract(body, rule);
            }
            if (videos == null) videos = new JSONArray();

            // cat_prefix / cat_suffix：对 vod_id 中的链接加前缀后缀（借鉴 XYQBiu 第254/308行）
            String catPrefix = getVal("cat_prefix");
            String catSuffix = getVal("cat_suffix");
            if (!catPrefix.isEmpty() || !catSuffix.isEmpty()) {
                for (int i = 0; i < videos.length(); i++) {
                    JSONObject v = videos.getJSONObject(i);
                    String rawId = v.optString("vod_id", "");
                    // vod_id 格式通常为 "name$$$pic$link"，拆分处理链接部分
                    int lastSep = rawId.lastIndexOf("$$$");
                    if (lastSep >= 0) {
                        String before = rawId.substring(0, lastSep + 3);
                        String link = rawId.substring(lastSep + 3);
                        link = catPrefix + link + catSuffix;
                        v.put("vod_id", before + link);
                    }
                }
            }

            // cat_subtitle：副标题回填到 vod_remarks
            String catSubtitle = getVal("cat_subtitle");
            if (!catSubtitle.isEmpty()) {
                for (int i = 0; i < videos.length(); i++) {
                    JSONObject v = videos.getJSONObject(i);
                    // cat_subtitle 是主列表截取规则中的 subtitle 字段名（非 vod_remarks），
                    // 若 v 中已含该字段则回填到 vod_remarks
                    if (v.has(catSubtitle)) {
                        v.put("vod_remarks", v.optString(catSubtitle, ""));
                    }
                }
            }

            videos = applyListPostProcess(videos);

            JSONObject result = new JSONObject();
            result.put("page", pg);
            result.put("pagecount", guessPageCount(body, videos.length()));
            result.put("limit", String.valueOf(PAGE_LIMIT));
            result.put("list", videos);
            return result.toString();
        } catch (Exception e) {
            SpiderDebug.log(e);
            return "";
        }
    }

    /**
     * cat_mode=0 JSON 分类模式（借鉴 XYQBiu 第237-280行）。
     * 从 JSON 响应中提取 vod 列表，支持 catjsonlist 多级路径（a.b / a.b.c）。
     * cat_prefix/cat_suffix 加在 id 上；cat_subtitle 填入 vod_remarks。
     */
    private JSONArray parseCatJsonMode(String body, String webUrl) throws Exception {
        JSONObject data = new JSONObject(body);
        JSONArray vodArray = null;
        String listPath = getVal("catjsonlist");
        if (listPath.isEmpty()) listPath = "data";
        String[] keylen = listPath.split("\\.");
        if (keylen.length == 1) {
            vodArray = data.optJSONArray(keylen[0]);
        } else if (keylen.length == 2) {
            JSONObject obj = data.optJSONObject(keylen[0]);
            if (obj != null) vodArray = obj.optJSONArray(keylen[1]);
        } else if (keylen.length == 3) {
            JSONObject obj1 = data.optJSONObject(keylen[0]);
            if (obj1 != null) {
                JSONObject obj2 = obj1.optJSONObject(keylen[1]);
                if (obj2 != null) vodArray = obj2.optJSONArray(keylen[2]);
            }
        }
        if (vodArray == null) return new JSONArray();

        String nameKey = getVal("catjsonname");
        String idKey = getVal("catjsonid");
        String picKey = getVal("catjsonpic");
        String stitleKey = getVal("catjsonstitle");
        String prefix = getVal("cat_prefix");
        String suffix = getVal("cat_suffix");
        boolean picNeedProxy = "1".equals(getVal("PicNeedProxy"));

        JSONArray videos = new JSONArray();
        for (int j = 0; j < vodArray.length(); j++) {
            try {
                JSONObject vod = vodArray.getJSONObject(j);
                String name = vod.optString(nameKey).trim();
                String id = vod.optString(idKey).trim();
                id = prefix + id + suffix;
                String pic = "";
                if (!picKey.isEmpty()) {
                    try {
                        pic = vod.optString(picKey).trim();
                        pic = absUrl(pic);
                        if (picNeedProxy) pic = fixCover(pic, webUrl);
                    } catch (Exception ignored) {}
                }
                String mark = "";
                if (!stitleKey.isEmpty()) {
                    try { mark = vod.optString(stitleKey).trim(); } catch (Exception ignored) {}
                }
                JSONObject v = new JSONObject();
                v.put("vod_id", name + "$$$" + pic + "$$$" + id);
                v.put("vod_name", name);
                v.put("vod_pic", pic);
                v.put("vod_remarks", mark);
                videos.put(v);
            } catch (Exception e) {
                SpiderDebug.log("cat_mode JSON 解析失败: " + e.getMessage());
            }
        }
        return videos;
    }

    /**
     * 分类 URL 模板替换：
     * <ul>
     *   <li>{cateId}→tid，{catePg}→页码（支持 startpage 偏移与 firstpage 首页替换）</li>
     *   <li>其余占位符（{area}/{by}/{class}/{lang}/{year}/{letter}/…）取自分页筛选 extend，缺失时置空</li>
     *   <li>支持 [firstPage=...] 语法：首页使用括号内无页码链接</li>
     *   <li>相对路径/协议相对路径自动补全为主机地址</li>
     * </ul>
     */
    private String buildCategoryUrl(String tid, String pg, Map<String, String> extend) {
        String classUrl = expandVariables(getVal("class_url"));
        if (classUrl.isEmpty()) return "";

        // [firstPage=...] 语法：首页使用括号内无页码链接
        int br = classUrl.indexOf("[firstPage=");
        if (br >= 0) {
            int end = classUrl.indexOf("]", br);
            if (end > br) {
                String firstPageTpl = classUrl.substring(br + "[firstPage=".length(), end);
                String normalTpl = classUrl.substring(0, br);
                classUrl = ("1".equals(pg)) ? firstPageTpl : normalTpl;
            }
        }

        // ;;z 语法：分类url 末尾有 ;;z 时，使用 [替换:xxx] 中的URL作为模板（首页备用）
        // ;;mrc* 语法：类似 ;;z，但随机后缀附加到 URL 末尾
        int zIdx = classUrl.indexOf(";;z");
        int mrcIdx = classUrl.indexOf(";;mrc");
        if (zIdx >= 0 || mrcIdx >= 0) {
            int reserveIdx = (zIdx >= 0 && mrcIdx >= 0) ? Math.min(zIdx, mrcIdx)
                            : (zIdx >= 0 ? zIdx : mrcIdx);
            boolean isMrc = classUrl.startsWith(";;mrc", reserveIdx);
            // ;;mrc* 后缀：提取关键字之后附加到 URL 的内容
            String mrcSuffix = "";
            if (isMrc && reserveIdx + 5 <= classUrl.length()) {
                mrcSuffix = classUrl.substring(reserveIdx + 5);
            }
            // 尝试从最近的 [替换:xxx] 提取真实 URL 作为模板
            int bracketOpen = classUrl.lastIndexOf("[", reserveIdx);
            int bracketClose = (bracketOpen >= 0) ? classUrl.indexOf("]", bracketOpen) : -1;
            if (bracketOpen >= 0 && bracketClose > bracketOpen && bracketClose < reserveIdx) {
                String inner = classUrl.substring(bracketOpen + 1, bracketClose);
                if (inner.startsWith("替换:")) inner = inner.substring(3);
                classUrl = expandVariables(inner);
            } else {
                classUrl = classUrl.substring(0, reserveIdx).trim();
                int lt = classUrl.lastIndexOf("[");
                if (lt >= 0) classUrl = classUrl.substring(0, lt).trim();
            }
            if (!mrcSuffix.isEmpty()) {
                classUrl = classUrl + mrcSuffix;
            }
        }

        int pgNum = parseIntSafe(pg, 1);
        String firstPage = getVal("firstpage");
        int startPage = parseIntSafe(getVal("startpage"), 1);
        String pageStr = (pgNum == 1 && !firstPage.isEmpty()) ? firstPage
                : String.valueOf(startPage + pgNum - 1);

        Matcher m = P_PLACEHOLDER.matcher(classUrl);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String key = m.group(1);
            String val;
            if ("cateId".equals(key)) {
                val = tid;
            } else if ("catePg".equals(key)) {
                val = pageStr;
            } else {
                val = extend != null ? extend.get(key) : null;
                if (val == null) val = "class".equals(key) ? tid : "";
            }
            try {
                val = URLEncoder.encode(val, "UTF-8");
            } catch (Exception ignored) {
            }
            // val 可能为 null（极端情况下），appendReplacement 要求非 null
            if (val == null) val = "";
            m.appendReplacement(sb, Matcher.quoteReplacement(val));
        }
        m.appendTail(sb);

        return absUrl(normalizeEntity(sb.toString()));
    }

    /** 列表后处理：标题清洗 → 图片净化 → 去重 → 过滤词 → 倒序 */
    private JSONArray applyListPostProcess(JSONArray videos) throws Exception {
        JSONArray cleaned = new JSONArray();
        for (int i = 0; i < videos.length(); i++) {
            JSONObject video = videos.getJSONObject(i);
            // 标题清洗（借鉴 Hgdj/Glod）
            if (video.has("vod_name")) {
                video.put("vod_name", cleanTitle(video.optString("vod_name", "")));
            }
            // 图片净化（过滤占位图 + 地址补全，借鉴 nongm/Hgdj/Glod）
            if (video.has("vod_pic")) {
                String pic = cleanImageUrl(video.optString("vod_pic", ""));
                if (!pic.isEmpty()) video.put("vod_pic", pic);
            }
            cleaned.put(video);
        }
        videos = cleaned;

        videos = dedupe(videos);
        String filterWord = getVal("filter_word");
        if (!filterWord.isEmpty()) {
            JSONArray kept = new JSONArray();
            for (int i = 0; i < videos.length(); i++) {
                JSONObject video = videos.getJSONObject(i);
                String name = video.optString("vod_name", "") + video.optString("vod_remarks", "");
                boolean blocked = false;
                for (String word : filterWord.split("&&")) {
                    if (!word.trim().isEmpty() && name.contains(word.trim())) {
                        blocked = true;
                        break;
                    }
                }
                if (!blocked) kept.put(video);
            }
            videos = kept;
        }
        return reverse ? reverseArray(videos) : videos;
    }

    /** 列表去重（保序，借鉴 FengYe/Glod 的 LinkedHashSet 思路）：优先按 vod_id，回退 vod_name */
    private JSONArray dedupe(JSONArray videos) throws Exception {
        JSONArray out = new JSONArray();
        Set<String> seen = new LinkedHashSet<>();
        for (int i = 0; i < videos.length(); i++) {
            JSONObject v = videos.getJSONObject(i);
            String key = v.optString("vod_id", "");
            if (key.isEmpty()) key = v.optString("vod_name", "");
            if (key.isEmpty()) {
                out.put(v);
                continue;
            }
            if (seen.contains(key)) continue;
            seen.add(key);
            out.put(v);
        }
        return out;
    }

    private JSONArray reverseArray(JSONArray array) throws Exception {
        JSONArray reversed = new JSONArray();
        for (int i = array.length() - 1; i >= 0; i--) reversed.put(array.get(i));
        return reversed;
    }

    /** 页总数猜测：共N页 / 共N条 / 当前x/y页，兜底按列表是否为空返回 1/999 */
    private int guessPageCount(String body, int listSize) {
        try {
            Matcher m = P_PAGE_TOTAL.matcher(body);
            if (m.find()) return Integer.parseInt(m.group(1));
            m = P_PAGE_CURRENT.matcher(body);
            if (m.find()) return Integer.parseInt(m.group(2));
            m = P_TOTAL_COUNT.matcher(body);
            if (m.find()) return (Integer.parseInt(m.group(1)) + PAGE_LIMIT - 1) / PAGE_LIMIT;
        } catch (Exception e) {
            // ignore
        }
        // 兜底：列表为空视为单页；列表满员视为可能还有更多页（返回 2 而非 999，
        // 避免永远返回 999 导致翻页按钮一直可用却无后续数据）
        return listSize >= PAGE_LIMIT ? 2 : 1;
    }

    // ==================== 详情页 ====================

    @Override
    public String detailContent(List<String> ids) {
        try {
            fetchRule();
            if (rule == null) return "";
            if (ids == null || ids.isEmpty()) return "";
            String vid = ids.get(0);
            JSONObject vinfo = decodeVinfo(vid);

            // force_play 直接播放模式（借鉴 XYQBiu / XYQHiker）：跳过详情抓取，直接用 vod_id 中的链接播放
            String forcePlayCfg = getVal("force_play");
            if ("1".equals(forcePlayCfg) || "2".equals(forcePlayCfg)) {
                String rawId = vinfo.optString("vod_id", vid);
                // vod_id 格式 "name$$$pic$link"，提取最后一部分作为播放链接
                int lastSep = rawId.lastIndexOf("$$$");
                String playUrl = lastSep >= 0 ? rawId.substring(lastSep + 3) : rawId;
                playUrl = getVal("play_prefix") + playUrl + getVal("play_suffix");
                VodDetail detail = new VodDetail(new VodItem(vinfo));
                PlaySource src = new PlaySource("直接播放");
                src.addEpisode(playUrl);
                detail.setPlaySources(java.util.Collections.singletonList(src));
                return detail.toJSON();
            }

            String detailUrl = buildDetailUrl(vinfo, vid);
            String body = fetchUrl(detailUrl, buildHeaders(null));
            if (body.isEmpty()) return "";

            // 详情字段（CSS/正则按规则类型自动分流）
            boolean cssMode = RuleConfig.isCssModeEnabled(this.rule);
            JSONObject vod = ExtractorFactory.createDetailExtractor(cssMode)
                    .extract(body, rule, vinfo);
            if (vod == null) vod = new JSONObject();
            // 详情字段后处理：JS 转义解码 + 标题清洗 + 图片净化
            // （借鉴 Ccys.unescape / Hgdj.cleanName / nongm.fixPic）
            vod = postProcessDetail(vod);
            // meta 兜底：标题/封面缺失时用 og:title/<title>/og:image 补全
            // （借鉴 Yst.extractTitle / Bttwo 多级取图思想，仅补缺不覆盖）
            vod = supplementDetailFromMeta(vod, body);
            // 详情分隔符取值：从正文按 "label词: 值" 文本补全导演/主演/地区/年份/备注
            // （借鉴 Yixiuwang.module-info-item / Qiwei "导演：" 文本取值思想）
            vod = applyLabelExtract(body, vod);
            // 详情多字段合并到简介：规则 detail_content_merge（中文 详情合并字段）列出若干字段，
            // 将其当前值以换行追加到 vod_content（借鉴 DJhub 把"热度/题材/集数/简介"拼入 content 思想）。
            // 仅增量合并，不覆盖已有简介；绝不新增英文字段。
            vod = mergeContentFields(vod);
            // 确保 vod_id 始终存在
            if (!vod.has("vod_id") || vod.optString("vod_id", "").isEmpty()) {
                vod.put("vod_id", vid);
            }

            VodDetail detail = new VodDetail(new VodItem(vod));
            detail.setPlaySources(extractPlaySources(body));
            return detail.toJSON();
        } catch (Exception e) {
            SpiderDebug.log(e);
            return "";
        }
    }

    /**
     * 详情字段后处理：对文本字段做 JS 转义解码，标题清洗，封面图净化。
     */
    private JSONObject postProcessDetail(JSONObject vod) throws Exception {
        if (vod == null) return vod;
        // 文本字段 JS 转义解码（借鉴 Ccys.unescape）
        for (String key : new String[]{"vod_name", "vod_content", "vod_actor", "vod_director", "vod_remarks", "vod_year", "vod_area", "type_name"}) {
            if (vod.has(key)) {
                vod.put(key, jsUnescape(vod.optString(key, "")));
            }
        }
        // 标题清洗（借鉴 Hgdj/Glod）
        if (vod.has("vod_name")) {
            vod.put("vod_name", cleanTitle(vod.optString("vod_name", "")));
        }
        // 封面图净化（过滤占位图 + 地址补全，借鉴 nongm/Hgdj/Glod）
        if (vod.has("vod_pic")) {
            String pic = cleanImageUrl(vod.optString("vod_pic", ""));
            if (!pic.isEmpty()) vod.put("vod_pic", pic);
        }
        return vod;
    }

    /**
     * 详情 meta 兜底（借鉴 Yst.extractTitle / Bttwo 多级取图）：
     * <ul>
     *   <li>vod_name 缺失：优先 og:title，回退 &lt;title&gt;（剥离 " - 站名" 后缀）</li>
     *   <li>vod_pic 缺失：og:image（兼容 property/content 正反属性序），净化补全</li>
     * </ul>
     * 仅补缺，不覆盖已提取值。
     */
    private JSONObject supplementDetailFromMeta(JSONObject vod, String html) {
        if (vod == null || html == null || html.isEmpty()) return vod;
        try {
            if (vod.optString("vod_name", "").isEmpty()) {
                String name = firstGroup(P_OG_TITLE, html);
                if (name.isEmpty()) name = firstGroup(P_TITLE_TAG, html);
                if (!name.isEmpty()) vod.put("vod_name", stripTitleSuffix(name));
            }
            if (vod.optString("vod_pic", "").isEmpty()) {
                String pic = firstGroup(P_OG_IMAGE, html);
                if (pic.isEmpty()) pic = firstGroup(P_OG_IMAGE_REV, html);
                if (!pic.isEmpty()) {
                    pic = cleanImageUrl(pic);
                    if (!pic.isEmpty()) vod.put("vod_pic", pic);
                }
            }
        } catch (Exception e) {
            SpiderDebug.log("supplementDetailFromMeta error: " + e.getMessage());
        }
        return vod;
    }

    /** 取正则首个匹配的第一捕获组，无匹配返回空串 */
    private static String firstGroup(Pattern pattern, String text) {
        Matcher m = pattern.matcher(text);
        return m.find() && m.group(1) != null ? m.group(1).trim() : "";
    }

    /**
     * 标题去站名后缀（借鉴 Yst.extractTitle）：
     * "影片名 - XX影院" → "影片名"，支持 - / — / | / _ 分隔符。
     */
    private static String stripTitleSuffix(String title) {
        if (title == null) return "";
        String t = title.trim();
        int idx = -1;
        for (String sep : new String[]{" - ", "—", "|", "_"}) {
            int i = t.indexOf(sep);
            if (i > 0 && (idx < 0 || i < idx)) idx = i;
        }
        return idx > 0 ? t.substring(0, idx).trim() : t;
    }

    /**
     * 详情分隔符取值（借鉴 Yixiuwang.module-info-item / Qiwei "导演：" 文本匹配）。
     * 规则 detail_label_split（中文别名 详情分隔符）配置一组 label 词（| 分隔），
     * 例如 "导演：|主演：|地区：|年份：|状态："。
     * 从详情页正文逐行/逐段查找 "label词 + 值"，回填到复用字段：
     * 导演→vod_director、主演→vod_actor、地区→vod_area、年份→vod_year、状态/备注→vod_remarks。
     * 仅补缺：若对应字段已存在非空则跳过，不影响已有提取结果。
     */
    private JSONObject applyLabelExtract(String body, JSONObject vod) {
        if (body == null || vod == null) return vod;
        String cfg = getVal("detail_label_split");
        if (cfg.isEmpty()) return vod;

        // 清洗 HTML 标签为纯文本，便于按分隔符切分（保留中文冒号/空格）
        // 集成 StringCutRule.cleanHtml()：统一处理 script/style移除 + 标签清理 + 实体解码 + 空白压缩
        String text = StringCutRule.cleanHtml(body);
        if (text == null) return vod;

        String[] labels = cfg.split("\\|");
        for (String raw : labels) {
            String label = raw.trim();
            if (label.isEmpty()) continue;
            // 去掉尾部冒号/空格，用于匹配字段语义
            String key = label.replaceAll("[:：\\s]+$", "");
            String field = mapLabelField(key);
            if (field == null) continue;
            if (vod.has(field) && !vod.optString(field, "").isEmpty()) continue;

            // 在文本中查找 "label...值"：label 后到行尾或下一个 label 之间的内容
            java.util.regex.Pattern p = java.util.regex.Pattern.compile(
                    java.util.regex.Pattern.quote(label) + "\\s*([^\\n|。；;]+)");
            java.util.regex.Matcher m = p.matcher(text);
            if (m.find()) {
                String val = m.group(1).trim()
                        .replaceAll("^[:：\\s]+", "")
                        .replaceAll("[\\s]+$", "");
                if (!val.isEmpty()) {
                    try { vod.put(field, val); } catch (Exception ignored) {}
                }
            }
        }
        return vod;
    }

    /** label 词 → 复用字段映射（绝不新增英文字段，仅复用既有 key） */
    private String mapLabelField(String key) {
        if (key.contains("导演")) return "vod_director";
        if (key.contains("主演") || key.contains("演员")) return "vod_actor";
        if (key.contains("地区") || key.contains("国家")) return "vod_area";
        if (key.contains("年份") || key.contains("年代") || key.contains("时间")) return "vod_year";
        if (key.contains("状态") || key.contains("备注") || key.contains("更新")) return "vod_remarks";
        if (key.contains("类型") || key.contains("分类")) return "type_name";
        if (key.contains("简介") || key.contains("剧情") || key.contains("介绍")) return "vod_content";
        return null;
    }

    /**
     * 详情多字段合并到简介（借鉴 DJhub 把"热度/题材/集数/简介"拼入 vod_content 的思想）。
     * 规则 detail_content_merge（中文别名 详情合并字段）列出要追加的字段 key（逗号分隔，
     * 例如 "vod_remarks,type_name,vod_year"），将这些字段的当前值以换行追加到 vod_content。
     * 仅增量合并，不覆盖已有简介正文；所有字段均为既有 TVBox key，绝不新增英文字段。
     */
    private JSONObject mergeContentFields(JSONObject vod) {
        if (vod == null) return vod;
        String cfg = getVal("detail_content_merge");
        if (cfg.isEmpty()) return vod;

        StringBuilder extra = new StringBuilder();
        for (String raw : cfg.split(",")) {
            String field = raw.trim();
            if (field.isEmpty()) continue;
            String val = vod.optString(field, "");
            if (val.isEmpty()) continue;
            if (extra.length() > 0) extra.append("\n");
            extra.append(val);
        }
        if (extra.length() == 0) return vod;

        String existing = vod.optString("vod_content", "");
        String merged = existing.isEmpty() ? extra.toString() : existing + "\n" + extra.toString();
        try { vod.put("vod_content", merged); } catch (Exception ignored) {}
        return vod;
    }

    /**
     * vod_id 兼容解码：列表返回纯 id；兼容历史 base64(JSON) 形式，
     * 解不开则按 {"vod_id": vid} 处理
     */
    private JSONObject decodeVinfo(String vid) {
        try {
            String decoded = new String(Base64.decode(vid, BASE64_FLAG), "UTF-8").trim();
            if (decoded.startsWith("{")) {
                return new JSONObject(decoded);
            }
        } catch (Exception e) {
            // 非 base64，按普通 id 处理
        }
        JSONObject vinfo = new JSONObject();
        try {
            vinfo.put("vod_id", vid);
        } catch (Exception ignored) {
        }
        return vinfo;
    }

    /**
     * 详情 URL：detail_url 模板（{vid}）优先；其次 vid 为完整 URL / 绝对路径，
     * 兜底用 homeUrl 主机拼接。
     * <p>
     * 特殊处理：当 vid 为纯链接格式（如 /detail/34414.html）且 list_prefix 已包含主机时，
     * 不再重复拼接主机前缀。
     */
    private String buildDetailUrl(JSONObject vinfo, String vid) {
        String innerId = vinfo.optString("vod_id", vid);
        String template = getVal("detail_url");
        if (!template.isEmpty()) {
            // 先替换 ${vid}，防止 "${vid}" 内部的 {vid} 被先替换而残留 "$" 前缀；
            // 模板可能为相对/协议相对路径，统一补全为绝对地址
            return absUrl(template.replace("${vid}", innerId).replace("{vid}", innerId));
        }
        if (innerId.startsWith("http://") || innerId.startsWith("https://")) return innerId;

        String host = hostOf(getHomeUrl());
        String prefix = getVal("list_prefix");
        // 如果 list_prefix 已包含完整主机，直接使用 innerId
        if (!prefix.isEmpty() && (prefix.contains("http://") || prefix.contains("https://"))) {
            if (innerId.startsWith("/")) return prefix + innerId.substring(1);
            return prefix + innerId;
        }
        if (innerId.startsWith("/")) return host.isEmpty() ? innerId : host + innerId;
        return host.isEmpty() ? innerId : host + "/" + innerId;
    }

    /** 提取 URL 的协议+主机部分 */
    private String hostOf(String url) {
        if (url == null || url.isEmpty()) return "";
        Matcher m = P_HOST.matcher(url);
        return m.find() ? m.group(1) : "";
    }

    /**
     * 相对/协议相对地址补全为主机绝对地址（借鉴 Libvio.fixUrl 思路）。
     * 以 http(s) 开头原样返回；以 // 开头补 https:；其余按主页主机拼接。
     */
    private String absUrl(String url) {
        if (url == null || url.isEmpty()) return url;
        if (url.startsWith("http://") || url.startsWith("https://")) return url;
        String host = hostOf(getHomeUrl());
        if (host.isEmpty()) return url;
        if (url.startsWith("//")) return "https:" + url;
        return host + (url.startsWith("/") ? url : "/" + url);
    }

    /** 播放线路提取：多线（from_array）/单线（play_array/url_array），支持线路合并与剧集过滤 */
    private List<PlaySource> extractPlaySources(String body) throws Exception {
        List<PlaySource> sources = new ArrayList<>();
        // CSS 模式以 from_array 为准：播放列表提取器仅按 from_array 判断模式；
        // url_array/play_array 可能为纯正则，误入 CSS 模式会导致线路切分失败。
        boolean cssMode = CssRule.isCssRule(getVal("from_array"));
        JSONArray lines = ExtractorFactory.createPlayListExtractor(cssMode).extract(body, rule, 0);
        if (lines == null) lines = new JSONArray();

        // 剧集过滤词（支持正则，# 分隔）
        String epFilter = getVal("episode_filter");
        List<String> epFilters = new ArrayList<>();
        if (!epFilter.isEmpty()) {
            for (String w : epFilter.split("&&")) {
                if (!w.trim().isEmpty()) epFilters.add(w.trim());
            }
        }

        // 选集链接前缀/后缀（epiurl_prefix/epiurl_suffix）
        String epiPrefix = getVal("epiurl_prefix");
        String epiSuffix = getVal("epiurl_suffix");

        // sort 自定义排序：线路名规则含 [排序:xxx] 时按顺序重排
        // 格式示例：[排序:第一站,第二站,第三站]
        for (int i = 0; i < lines.length(); i++) {
            JSONObject line = lines.getJSONObject(i);
            String lineName = line.optString("name", "线路" + (i + 1));
            JSONArray episodes = line.optJSONArray("episodes");
            if (episodes == null) continue;

            // 应用 epiurl_prefix/epiurl_suffix
            List<String> epList = new ArrayList<>();
            for (int j = 0; j < episodes.length(); j++) {
                String ep = episodes.getString(j);
                // 过滤
                boolean blocked = false;
                for (String w : epFilters) {
                    if (ep.contains(w)) { blocked = true; break; }
                }
                if (blocked) continue;
                // 应用前缀后缀
                if (!epiPrefix.isEmpty()) ep = epiPrefix + ep;
                if (!epiSuffix.isEmpty()) ep = ep + epiSuffix;
                epList.add(ep);
            }
            if (epList.isEmpty()) continue;
            sources.add(new PlaySource(lineName, epList));
        }

        // 支持线路 sort 排序（借鉴 XYQHiker 的 [排序:] 语法）
        String fromArrayRule = getVal("from_array");
        if (fromArrayRule.contains("[排序:") && sources.size() > 1) {
            String sortStr = fromArrayRule.split("\\[排序:")[1].split("\\]")[0];
            List<String> sortOrder = new ArrayList<>();
            for (String w : sortStr.split(",")) {
                if (!w.trim().isEmpty()) sortOrder.add(w.trim());
            }
            if (!sortOrder.isEmpty()) {
                List<PlaySource> sorted = new ArrayList<>();
                for (String keyword : sortOrder) {
                    for (PlaySource src : sources) {
                        if (src.getName().contains(keyword)) {
                            sorted.add(src);
                            break;
                        }
                    }
                }
                // 未在排序列表中的线路追加到末尾
                for (PlaySource src : sources) {
                    boolean found = false;
                    for (PlaySource s : sorted) {
                        if (s.getName().equals(src.getName())) { found = true; break; }
                    }
                    if (!found) sorted.add(src);
                }
                sources = sorted;
            }
        }

        if (mergeLines && sources.size() > 1) {
            PlaySource merged = new PlaySource(sources.get(0).getName());
            for (PlaySource source : sources) {
                for (String ep : source.getEpisodes()) merged.addEpisode(ep);
            }
            sources.clear();
            sources.add(merged);
        }

        // 空线路兜底
        if (sources.isEmpty()) {
            String emptyPlay = getVal("empty_play_url");
            if (!emptyPlay.isEmpty()) {
                PlaySource src = new PlaySource(getVal("empty_play_from").isEmpty()
                        ? "暂无播放" : getVal("empty_play_from"));
                if (!epiPrefix.isEmpty()) emptyPlay = epiPrefix + emptyPlay;
                if (!epiSuffix.isEmpty()) emptyPlay = emptyPlay + epiSuffix;
                src.addEpisode(emptyPlay);
                sources.add(src);
            }
        }
        return sources;
    }

    // ==================== 搜索 ====================

    @Override
    public String searchContent(String keyword, boolean quick) {
        return searchContent(keyword, quick, "1");
    }

    @Override
    public String searchContent(String keyword, boolean quick, String pg) {
        try {
            fetchRule();
            if (rule == null) return "";
            String template = getVal("search_url");
            if (template.isEmpty() || keyword == null || keyword.isEmpty()) return "";

            // sea_firstpage：搜索起始页码偏移（借鉴 XYQHiker 第2722-2732行）
            String seaFirstPage = getVal("sea_firstpage");
            int firstPageOffset = parseIntSafe(seaFirstPage, 1);
            int pgNum = parseIntSafe(pg, 1);
            String pageStr = String.valueOf(firstPageOffset + pgNum - 1);

            // POST 模式：search_url 含 ;post 后缀
            boolean isPost = template.contains(";post");
            String baseUrl;
            String postBody = null;
            if (isPost) {
                int idx = template.indexOf(";post");
                baseUrl = template.substring(0, idx);
                String rest = template.substring(idx + ";post".length());
                if (rest.startsWith(";")) rest = rest.substring(1);
                postBody = rest.replace("{wd}", URLEncoder.encode(keyword, "UTF-8"))
                              .replace("{pg}", pageStr);
            } else {
                baseUrl = template;
            }
            baseUrl = expandVariables(baseUrl);

            // 搜索链接前后缀（借鉴 XYQHiker 第2867-2868行）
            String searchPrefix = getVal("search_prefix");
            String searchSuffix = getVal("search_suffix");

            // 时间戳占位符替换（借鉴 XYQHiker 第2764-2767行）
            String unixTs = String.valueOf(System.currentTimeMillis() / 1000L);
            String milliTs = String.valueOf(System.currentTimeMillis());
            baseUrl = baseUrl.replace("时间戳", unixTs).replace("时间标", milliTs);

            // md5 加密占位符（借鉴 XYQHiker 第2769-2775行）：md5(内容) → MD5哈希
            int md5Idx;
            while ((md5Idx = baseUrl.indexOf("md5(")) >= 0) {
                int endParen = baseUrl.indexOf(")", md5Idx);
                if (endParen < 0) break;
                String md5Content = baseUrl.substring(md5Idx + 4, endParen);
                String md5Hash = computeMd5(md5Content);
                baseUrl = baseUrl.substring(0, md5Idx) + md5Hash + baseUrl.substring(endParen + 1);
            }

            baseUrl = absUrl(baseUrl);

            String body;
            if (isPost) {
                body = fetchPost(baseUrl, buildHeaders("search_header"), postBody);
            } else {
                // 先替换 ${wd} 再替换 {wd}，避免 "${wd}" 被先消费残留 "$" 前缀
                String url = baseUrl
                        .replace("${wd}", URLEncoder.encode(keyword, "UTF-8"))
                        .replace("{wd}", URLEncoder.encode(keyword, "UTF-8"))
                        .replace("{pg}", pageStr);
                url = searchPrefix + url + searchSuffix;
                body = fetchUrl(url, buildHeaders("search_header"));
            }
            if (body.isEmpty()) return "";

            // 搜索模式（XBPQ写法说明.json）："0"=JSON 搜索模式；其它=网页截取模式。
            // 两种模式均互为兜底，保证任一来源都能解析。
            boolean jsonMode = "0".equals(getVal("search_mode"));
            if (jsonMode) {
                // 优先按 JSON 解析（如 AJAX suggest 返回的 JSON）
                String jsonResult = parseJsonSearchResult(body);
                if (jsonResult != null) return applySearchPostProcess(jsonResult, keyword, quick);
                // JSON 解析失败回退网页截取
                JSONArray videos = ExtractorFactory
                        .createSearchExtractor(CssRule.isCssRule(getVal("search_array")))
                        .extract(body, rule);
                if (videos == null) videos = new JSONArray();
                JSONObject result = new JSONObject();
                result.put("list", videos);
                return applySearchPostProcess(result.toString(), keyword, quick);
            } else {
                // 网页截取模式：优先用 search_array 规则截取
                JSONArray videos = ExtractorFactory
                        .createSearchExtractor(CssRule.isCssRule(getVal("search_array")))
                        .extract(body, rule);
                if (videos == null) videos = new JSONArray();
                if (videos.length() > 0) {
                    JSONObject result = new JSONObject();
                    result.put("list", videos);
                    return applySearchPostProcess(result.toString(), keyword, quick);
                }
                // 网页无结果回退 JSON 探测
                String jsonResult = parseJsonSearchResult(body);
                if (jsonResult != null) return applySearchPostProcess(jsonResult, keyword, quick);
                return new JSONObject().put("list", new JSONArray()).toString();
            }
        } catch (Exception e) {
            SpiderDebug.log(e);
            return "";
        }
    }

    /** 计算 MD5 哈希（借鉴 XYQHiker ParseUtils.md5Hex） */
    private static String computeMd5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes("UTF-8"));
            return String.format("%032x", new BigInteger(1, digest));
        } catch (Exception e) {
            return input;
        }
    }

    /**
     * 搜索结果后处理：图片净化 → 标题清洗 → quick 精确匹配过滤（借鉴 Qimao 的 quick&&contains 思路）。
     * 解析失败时返回原串（避免吞掉错误）。
     */
    private String applySearchPostProcess(String jsonStr, String keyword, boolean quick) {
        if (jsonStr == null || jsonStr.isEmpty()) return jsonStr;
        try {
            JSONObject root = new JSONObject(jsonStr);
            JSONArray list = root.optJSONArray("list");
            if (list == null) return jsonStr;

            JSONArray out = new JSONArray();
            String kw = keyword != null ? keyword.trim().toLowerCase() : "";
            for (int i = 0; i < list.length(); i++) {
                JSONObject v = list.getJSONObject(i);
                // 图片净化（借鉴 nongm/Hgdj/Glod）
                if (v.has("vod_pic")) {
                    String pic = cleanImageUrl(v.optString("vod_pic", ""));
                    if (!pic.isEmpty()) v.put("vod_pic", pic);
                }
                // 标题清洗（借鉴 Hgdj/Glod）
                if (v.has("vod_name")) {
                    v.put("vod_name", cleanTitle(v.optString("vod_name", "")));
                }
                // quick 精确匹配：名称需包含关键字（借鉴 Qimao）
                if (quick && !kw.isEmpty()) {
                    String name = v.optString("vod_name", "").toLowerCase();
                    if (!name.contains(kw)) continue;
                }
                out.put(v);
            }
            root.put("list", out);
            return root.toString();
        } catch (Exception e) {
            return jsonStr;
        }
    }

    /** POST 请求（用于 ;post 搜索），表单体经 search_header 注入并补充 Content-Type */
    private String fetchPost(String url, Map<String, String> headers, String body) {
        if (url == null || url.isEmpty()) return "";
        if (isSsrfBlocked(url)) {
            SpiderDebug.log("SSRF 拦截: " + url);
            return "";
        }

        // 统一重试 + 超时策略：与 fetchUrl 走同一份 resolveRetry / resolveTimeout 配置
        int maxRetries = resolveRetry();
        final int timeout = resolveTimeout(null);
        Exception lastError = null;

        for (int attempt = 0; attempt < maxRetries; attempt++) {
            try {
                Map<String, String> h = new HashMap<>();
                if (headers != null) h.putAll(headers);
                // 修复：仅当调用方未显式指定 Content-Type 时补默认表单类型，
                // 避免 search_header 中配置的 JSON Content-Type 被覆盖
                //（与 HttpRequest.toPostRequest 的"请求头优先"策略保持一致）
                boolean hasContentType = false;
                if (headers != null) {
                    for (String k : headers.keySet()) {
                        if (k != null && k.equalsIgnoreCase("Content-Type")) {
                            hasContentType = true;
                            break;
                        }
                    }
                }
                if (!hasContentType) {
                    h.put("Content-Type", "application/x-www-form-urlencoded");
                }
                String resp = httpClient().string(url, h, body);
                return resp != null ? resp : "";
            } catch (Exception e) {
                lastError = e;
                SpiderDebug.log("POST 请求第" + (attempt + 1) + "次失败: " + url + " " + e.getMessage());
            }
        }

        SpiderDebug.log("POST 请求最终失败（已重试" + maxRetries + "次）: " + url
                + (lastError != null ? " " + lastError.getMessage() : ""));
        return "";
    }

    /** 泛化 JSON 搜索：递归查找含 vod_id+vod_name 的数组并映射标准字段 */
    private String parseJsonSearchResult(String body) {
        String trimmed = body.trim();
        if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) return null;
        try {
            String stripped = JsonParser.stripJsonp(trimmed);
            Object root = stripped.startsWith("[") ? new JSONArray(stripped) : new JSONObject(stripped);
            Object target = JsonParser.findTarget(root, "vod_id", "vod_name");
            if (target == null) return null;

            JSONArray arr = target instanceof JSONArray ? (JSONArray) target
                    : new JSONArray().put((JSONObject) target);
            JSONArray videos = new JSONArray();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject item = arr.getJSONObject(i);
                JSONObject video = new JSONObject();
                // 字段别名回退（借鉴 HHkk/Gold 的 id→playlet_id 等思路）：
                // 非标 JSON 字段（如 id/name/pic）也能映射到标准 vod_* 字段
                video.put("vod_id", JsonParser.pickField(item, "vod_id"));
                video.put("vod_name", JsonParser.pickField(item, "vod_name"));
                String pic = JsonParser.pickField(item, "vod_pic");
                if (!pic.isEmpty()) video.put("vod_pic", pic);
                String remarks = JsonParser.pickField(item, "vod_remarks");
                if (!remarks.isEmpty()) video.put("vod_remarks", remarks);
                if (!video.optString("vod_id").isEmpty() || !video.optString("vod_name").isEmpty()) {
                    videos.put(video);
                }
            }
            if (videos.length() == 0) return null;
            JSONObject result = new JSONObject();
            result.put("list", videos);
            return result.toString();
        } catch (Exception e) {
            return null;
        }
    }

    // ==================== 播放 ====================

    @Override
    public String playerContent(String flag, String url, List<String> vipFlags) {
        try {
            fetchRule();
            if (rule == null) return "";
            if (url == null) url = "";

            // P2P 链接（magnet/ed2k）直接交给播放器，不走 HTTP
            if (isP2PUrl(url)) {
                return appendDanmu(directResult(url));
            }

            JSONObject result;
            String forcePlay = getVal("force_play");
            boolean enforcePlay = "1".equals(forcePlay) || "2".equals(forcePlay);

            if (enforcePlay) {
                // 强制直接播放模式：拼接前缀后缀后直接输出
                String finalUrl = getVal("play_prefix") + url + getVal("play_suffix");
                if (isVip(finalUrl, vipFlags)) {
                    result = vipResult(finalUrl);
                } else if (isVideoUrl(finalUrl)) {
                    result = directResult(finalUrl);
                } else {
                    result = sniffResult(finalUrl);
                }
                return appendDanmu(result);
            }

            // 1. 直链视频 → 直接播放
            if (isVideoUrl(url)) {
                result = directResult(url);
            } else {
                if (!getVal("jump_url").isEmpty()) {
                    // 2. 跳转播放：抓取播放页并按规则提取真实地址
                    result = tryJumpPlay(url);
                    if (result == null) result = sniffResult(url);
                } else if ("1".equals(getVal("manualVideoCheck"))) {
                    // 3. 免嗅探：直接交给播放器
                    result = directResult(url);
                } else {
                    // 4. 嗅探兜底
                    result = sniffResult(url);
                }
            }
            return appendDanmu(result);
        } catch (Exception e) {
            SpiderDebug.log(e);
            return "";
        }
    }

    /** 返回 VIP 解析结果（parse:1 jx:1） */
    private JSONObject vipResult(String url) throws Exception {
        JSONObject result = new JSONObject();
        result.put("parse", 1);
        result.put("jx", "1");
        result.put("url", url);
        return result;
    }

    /**
     * 判断是否为需要 VIP 解析的视频源。
     * <p>
     * 修复说明：原实现完全忽略 vipFlags 参数，仅匹配 4 个硬编码域名。
     * 现优先消费框架传入的 vipFlags（站点配置的 vip 标志列表，如
     * ["iqiyi","优酷","m3u8"]），URL 命中任一关键词即走解析；
     * 未配置/未命中时回退内置域名表（vip.ffzy/vip.lz/hd.lz/suonizy）。
     */
    private boolean isVip(String url, List<String> vipFlags) {
        if (url == null) return false;
        if (vipFlags != null && !vipFlags.isEmpty()) {
            for (String flag : vipFlags) {
                if (flag != null && !flag.trim().isEmpty() && url.contains(flag.trim())) {
                    return true;
                }
            }
            // vipFlags 配置了但未命中，仍回退到内置域名检查（避免漏判）
        }
        String lower = url.toLowerCase();
        return lower.contains("vip.ffzy") || lower.contains("vip.lz")
                || lower.contains("hd.lz") || lower.contains("suonizy");
    }

    /** 直接播放结果（parse:0），附加播放请求头 */
    private JSONObject directResult(String url) throws Exception {
        JSONObject result = new JSONObject();
        result.put("parse", 0);
        result.put("playUrl", "");
        result.put("url", url);
        putPlayHeader(result);
        return result;
    }

    /** 嗅探结果（parse:1），附加播放请求头 */
    private JSONObject sniffResult(String url) throws Exception {
        JSONObject result = new JSONObject();
        result.put("parse", 1);
        result.put("playUrl", "");
        result.put("url", url);
        putPlayHeader(result);
        return result;
    }

    /** play_header 为 JSON 字符串时写入 header 字段（Catvod 框架规范） */
    private void putPlayHeader(JSONObject result) {
        String playHeader = getVal("play_header");
        if (playHeader.isEmpty()) return;
        JSONObject header = JsonParser.safeParseObject(playHeader);
        if (header != null && header.length() > 0) {
            try { result.put("header", header); } catch (Exception ignored) {}
        }
    }

    /** 跳转播放：抓取集数链接页面，按 jump_url 规则（css/&&/正则）提取真实地址 */
    private JSONObject tryJumpPlay(String url) {
        try {
            // 跳转 URL 来自外部集数链接（不可信），必须经 fetchUrl 走 SSRF 防护
            String body = fetchUrl(url, buildHeaders("play_header"));
            if (body.isEmpty()) return null;

            String target = extractByRule(body, getVal("jump_url"));
            if (!target.isEmpty() && target.contains("http")) {
                // 已经是 URL（可能含百分号编码），尝试解码
                return processDecodedVideoUrl(target);
            }
            // 提取结果可能是 player_aaaa JSON 或其他结构，尝试从 JSON 中取 url 字段
            if (target.startsWith("{")) {
                target = extractUrlFromJson(target);
                if (!target.isEmpty()) {
                    return processDecodedVideoUrl(target);
                }
            }
            // Maccms 模板：扫描 var player_aaaa={...} 解析（借鉴 XYQBiu.Anal_MacPlayer）
            // encrypt=1 → URLDecoder.decode  encrypt=2 → Base64 + URLDecoder
            // Anal_MacPlayer=2 时优先使用正则解析脚本块（借鉴 XYQHiker 完整流程）
            if (target.isEmpty() || !target.contains("http")) {
                MaccmsPlayer mp = extractMaccmsPlayer(body);
                if (mp != null && !mp.url.isEmpty()) {
                    target = mp.url;
                    // link_next：解析到的 url 是 next 地址，继续跳转一次
                    if (mp.linkNext != null && !mp.linkNext.isEmpty()) {
                        target = mp.linkNext;
                    }
                }
            }
            if (target.isEmpty()) {
                target = JsParser.matchVideoUrl(body);
            }
            if (target.isEmpty()) return null;
            // 处理 VIP 源
            if (isVip(target, null)) {
                return vipResult(target);
            }
            return isVideoUrl(target) ? directResult(target) : sniffResult(target);
        } catch (Exception e) {
            SpiderDebug.log("跳转播放失败: " + e.getMessage());
            return null;
        }
    }

    /** Maccms player 变量正则（regexMode 用，预编译避免每次播放重编译） */
    private static final Pattern P_MACCMS_PLAYER = Pattern.compile(
            "var\\s+player_\\w+\\s*=\\s*(\\{[^;]+?});", Pattern.DOTALL);

    /**
     * Maccms 播放脚本解析（借鉴 XYQBiu.Anal_MacPlayer / XYQHiker）：
     * <p>扫描所有 &lt;script&gt; 中 {@code var player_XXXX = {...}} 的 JSON 配置，
     * 解码 url 字段；支持 encrypt 字段：
     * <ul>
     *   <li>{@code encrypt:1} — URLDecoder.decode</li>
     *   <li>{@code encrypt:2} — Base64.decode + URLDecoder.decode</li>
     * </ul>
     * 当 Anal_MacPlayer=2 时，使用正则提取脚本块而非 indexOf 遍历（更快更可靠）。
     * 解析失败或 url 为空返回 null。
     */
    private MaccmsPlayer extractMaccmsPlayer(String body) {
        if (body == null || body.isEmpty()) return null;
        boolean regexMode = "2".equals(getVal("Anal_MacPlayer"));
        try {
            if (regexMode) {
                // Regex-based extraction (faster, handles minified JS better)
                Matcher m = P_MACCMS_PLAYER.matcher(body);
                while (m.find()) {
                    MaccmsPlayer mp = parseMaccmsPlayerJson(m.group(1));
                    if (mp != null && !mp.url.isEmpty()) return mp;
                }
            } else {
                // Original indexOf-based traversal
                int pos = 0;
                while (pos < body.length()) {
                    int pi = body.indexOf("var player_", pos);
                    if (pi < 0) return null;
                    int braceStart = body.indexOf('{', pi);
                    if (braceStart < 0) return null;
                    // 配对花括号，处理嵌套对象（如 "config":{...} 形式）
                    int braceEnd = findMatchingBrace(body, braceStart);
                    if (braceEnd < 0) return null;
                    String jsonText = body.substring(braceStart, braceEnd + 1);
                    MaccmsPlayer mp = parseMaccmsPlayerJson(jsonText);
                    if (mp != null && !mp.url.isEmpty()) return mp;
                    pos = braceEnd + 1;
                }
            }
        } catch (Exception e) {
            // ignore
        }
        return null;
    }

    /** 找到与 startPos 处 '{' 配对的 '}'，忽略字符串内的花括号。 */
    private int findMatchingBrace(String text, int startPos) {
        if (startPos < 0 || startPos >= text.length() || text.charAt(startPos) != '{') return -1;
        int depth = 0;
        boolean inStr = false;
        boolean esc = false;
        for (int i = startPos; i < text.length(); i++) {
            char c = text.charAt(i);
            if (esc) { esc = false; continue; }
            if (c == '\\') { esc = true; continue; }
            if (c == '"') { inStr = !inStr; continue; }
            if (inStr) continue;
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }

    private MaccmsPlayer parseMaccmsPlayerJson(String jsonText) {
        try {
            JSONObject obj = new JSONObject(jsonText);
            MaccmsPlayer mp = new MaccmsPlayer();
            if (obj.has("url")) mp.url = obj.optString("url", "").trim();
            if (obj.has("from")) mp.from = obj.optString("from", "").trim();
            if (obj.has("link_next")) mp.linkNext = obj.optString("link_next", "").trim();
            if (obj.has("encrypt")) {
                int encrypt = obj.optInt("encrypt", 0);
                if (encrypt == 1) {
                    try { mp.url = java.net.URLDecoder.decode(mp.url, "UTF-8"); } catch (Exception ignored) {}
                    if (!mp.linkNext.isEmpty()) {
                        try { mp.linkNext = java.net.URLDecoder.decode(mp.linkNext, "UTF-8"); } catch (Exception ignored) {}
                    }
                } else if (encrypt == 2) {
                    try {
                        mp.url = new String(android.util.Base64.decode(mp.url, android.util.Base64.DEFAULT), "UTF-8");
                        mp.url = java.net.URLDecoder.decode(mp.url, "UTF-8");
                    } catch (Exception ignored) {}
                    if (!mp.linkNext.isEmpty()) {
                        try {
                            mp.linkNext = new String(android.util.Base64.decode(mp.linkNext, android.util.Base64.DEFAULT), "UTF-8");
                            mp.linkNext = java.net.URLDecoder.decode(mp.linkNext, "UTF-8");
                        } catch (Exception ignored) {}
                    }
                }
            }
            return mp.url.isEmpty() ? null : mp;
        } catch (Exception e) {
            return null;
        }
    }

    /** Maccms var player_aaaa 解析结果载体 */
    private static class MaccmsPlayer {
        String url = "";
        String from = "";
        String linkNext = "";
    }

    /** 处理可能含百分号编码的 URL */
    private JSONObject processDecodedVideoUrl(String url) throws Exception {
        if (url.contains("%")) {
            // 包含百分号编码，尝试解码
            String decoded = java.net.URLDecoder.decode(url, "UTF-8");
            if (decoded.startsWith("http")) {
                return isVideoUrl(decoded) ? directResult(decoded) : sniffResult(decoded);
            }
        }
        return isVideoUrl(url) ? directResult(url) : sniffResult(url);
    }

    /** 从 JSON 字符串中提取 url 字段（处理 player_aaaa 等播放器配置） */
    private String extractUrlFromJson(String jsonText) {
        try {
            JSONObject json = new JSONObject(jsonText);
            if (json.has("url")) {
                String rawUrl = json.optString("url", "");
                return rawUrl.trim();
            }
        } catch (Exception e) {
            SpiderDebug.log("JSON 解析失败: " + e.getMessage());
        }
        return "";
    }

    /**
     * 通用规则提取：css:/css:// 选择器 → && 前后缀截取 → 正则 group(1)
     * <p>
     * 集成说明：
     * <ul>
     *   <li>{@code cleanHtml} — 在 applyLabelExtract 中替代内联HTML清洗代码</li>
     *   <li>{@code parseCutRule} — 此处用于预解析截取规则结构（偏移/回溯参数预留）</li>
     *   <li>{@code escapeRegex} — 在正则回退路径中安全转义用户输入</li>
     * </ul>
     */
    private String extractByRule(String content, String ruleStr) {
        // 统一走 RegexFieldHelper：支持 CSS/p: 简写、|| 备用、&& 多级截取、[替换]/[不含]/[序号] 后处理器、正则 group(1)
        return RegexFieldHelper.extract(content, ruleStr);
    }

    /**
     * 通用字段提取方法（统一 p: 简写 / && 截取 / 正则 / 后处理器 / || 备用规则）
     * <p>
     * 支持完整的字段规则体系：
     * <ul>
     *   <li>|| 备用规则：逐条尝试取首个非空结果</li>
     *   <li>p:xxx->attr / css:/css:// 前缀 → CSS 选择器提取</li>
     *   <li>[替换:a>>b] / [不含:xxx] / [序号:n] / 分割(xxx)：后处理器</li>
     *   <li>&amp;&amp; 前后缀截取：二次截取（含多级）</li>
     *   <li>正则 group(1) 优先提取</li>
     * </ul>
     * 供 detailContent 等场景下直接提取单个字段时使用。
     */
    protected String extractField(String scope, String rule) {
        return RegexFieldHelper.extract(scope, rule);
    }

    /** 弹幕注入：danmuUrl 配置存在时附加 danmaku 代理参数 */
    private String appendDanmu(JSONObject result) {
        try {
            String danmuUrl = getVal("danmuUrl");
            if (!danmuUrl.isEmpty()) {
                result.put("danmaku", "proxy://do=XBPQ&danmu_url=" + URLEncoder.encode(danmuUrl, "UTF-8"));
            }
        } catch (Exception e) {
            SpiderDebug.log("弹幕参数注入失败: " + e.getMessage());
        }
        return result.toString();
    }

    /**
     * 代理分发：处理 proxy://do=XBPQ 回调
     * <p>
     * danmu_url：拉取弹幕地址内容并透传给播放器。
     *
     * @return Object[]{code, contentType, InputStream}，未知参数返回 null
     */
    @Override
    public Object[] proxy(Map<String, String> params) throws Exception {
        String danmuUrl = params == null ? null : params.get("danmu_url");
        if (danmuUrl == null || danmuUrl.isEmpty()) return null;

        fetchRule();
        // danmuUrl 在 appendDanmu 中经过 URLEncoder.encode，先还原，再对解码后的
        // 真实地址做 SSRF 校验，防止用编码形态的内网地址绕过校验
        String decodedUrl;
        try {
            decodedUrl = java.net.URLDecoder.decode(danmuUrl, "UTF-8");
        } catch (Exception e) {
            decodedUrl = danmuUrl;
        }
        // proxy 入口由播放器/外部传入 URL，属不可信输入，强制 SSRF 防护（不受 allow_internal 影响）
        // 这里直接拦截内网地址，与后续 fetchUrl 的 isSsrfBlocked(读 allow_internal) 行为独立
        // 双重防护：即使 allow_internal=1 允许内网，proxy 入口仍禁止（防 SSRF 滥用）
        if (httpClient().isInternalUrl(decodedUrl)) {
            SpiderDebug.log("proxy SSRF 拦截: " + decodedUrl);
            return new Object[]{403, "text/plain; charset=utf-8",
                    new java.io.ByteArrayInputStream("forbidden".getBytes("UTF-8"))};
        }
        // 防御：fetchUrl 返回空时（请求失败/重试耗尽）也应返回 502 错误流，避免返回空字符串
        String respBody = fetchUrl(decodedUrl, buildHeaders(null));
        if (respBody.isEmpty()) {
            return new Object[]{502, "text/plain; charset=utf-8",
                    new java.io.ByteArrayInputStream("upstream empty".getBytes("UTF-8"))};
        }
        byte[] data = respBody.getBytes("UTF-8");
        return new Object[]{200, "application/octet-stream", new java.io.ByteArrayInputStream(data)};
    }

    // ==================== 工具 ====================

    /**
     * 框架层免嗅探判定（基类默认 false）：将规则「免嗅」/manualVideoCheck
     * 同步给框架，保证框架独立调用时与 playerContent 内部逻辑一致。
     */
    @Override
    public boolean manualVideoCheck() {
        fetchRule();
        return rule != null && "1".equals(getVal("manualVideoCheck"));
    }

    /**
     * 框架层直链判定（基类默认 false）：将 video_format/嗅探词同步给框架，
     * 使外部嗅探决策与 playerContent 一致。
     */
    @Override
    public boolean isVideoFormat(String url) {
        fetchRule();
        return rule != null && isVideoUrl(url);
    }

    /** 判断 URL 是否为 P2P/非 HTTP 协议链接（磁力/ed2k/迅雷/FTP） */
    private static boolean isP2PUrl(String url) {
        if (url == null || url.trim().isEmpty()) return false;
        String lower = url.trim().toLowerCase();
        return lower.startsWith("magnet:") || lower.startsWith("ed2k://")
                || lower.startsWith("thunder://") || lower.startsWith("ftp://")
                || lower.endsWith(".torrent") || lower.contains(".torrent?");
    }

    /**
     * 判断是否为可直连播放的视频地址：优先 video_format（嗅探词，# 分隔），
     * 未配置时按常见扩展名判断；video_format 配置后仅匹配命中才返回 true。
     * video_filter（排除词，# 分隔）命中则强制返回 false（借鉴 XYQHiker）。
     */
    private boolean isVideoUrl(String url) {
        if (url == null || url.isEmpty()) return false;
        // video_filter：排除词（命中则非视频）
        String filterWords = getVal("video_filter");
        if (!filterWords.isEmpty()) {
            for (String word : filterWords.split("#")) {
                if (word != null && !word.trim().isEmpty() && url.contains(word.trim())) return false;
            }
        }
        String sniffWords = getVal("video_format");
        if (!sniffWords.isEmpty()) {
            for (String word : sniffWords.split("#")) {
                if (word != null && !word.trim().isEmpty() && url.contains(word.trim())) return true;
            }
            return false;
        }
        String lower = url.toLowerCase();
        int queryIdx = lower.indexOf('?');
        String path = queryIdx > 0 ? lower.substring(0, queryIdx) : lower;
        for (String ext : DEFAULT_VIDEO_EXTS) {
            if (path.endsWith(ext)) return true;
        }
        return false;
    }

    private int parseIntSafe(String value, int defaultValue) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception e) {
            return defaultValue;
        }
    }
}
