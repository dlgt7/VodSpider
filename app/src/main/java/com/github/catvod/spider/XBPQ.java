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

    // 分页统计正则（放宽空白与大小写，覆盖更多站点文案）
    private static final Pattern P_PAGE_TOTAL = Pattern.compile("共\\s*(\\d+)\\s*页");
    private static final Pattern P_TOTAL_COUNT = Pattern.compile("共\\s*(\\d+)\\s*条");
    private static final Pattern P_PAGE_CURRENT = Pattern.compile("(\\d+)\\s*/\\s*(\\d+)\\s*页");
    /** 英文分页文案：Page 1 of 20 / page 1/20 */
    private static final Pattern P_PAGE_EN = Pattern.compile("(?i)\\bpage\\s*\\d*\\s*(?:of|/)\\s*(\\d+)");
    /** JSON/JS 字段形态：pagecount / page_count / total_pages / totalPages */
    private static final Pattern P_PAGE_FIELD = Pattern.compile(
            "(?i)(?:pagecount|page_count|total_?pages?)\\s*[\"':=]\\s*\"?(\\d+)");

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
    /**
     * {{key}} 变量引用占位符。
     * <p>键名必须放宽到任意非 {@code {}} 字符：XBPQ 规则中变量多为中文且带 -c 后缀
     * （如 {{域名-c}}、{{主页url-c}}、{{搜索url-c}}），{@code \w} 只匹配 [a-zA-Z_0-9]，
     * 既不匹配中文字符也不匹配 '-'，会导致动态域名链整体失效。
     */
    private static final Pattern P_VAR_REF = Pattern.compile("\\{\\{([^{}]+)\\}\\}");
    /** [工具:xxx] 工具链标记 */
    private static final Pattern P_TOOL = Pattern.compile("\\[工具:([^\\]]+)\\]");

    /** 懒加载图片占位图特征（借鉴 Hgdj/Glod/Duboku/Kkys/Libvio/Jable 的占位图过滤思路）。
     *  注意：不收 ".gif"/"empty"/"icon." 等过宽关键词，避免误杀正常封面 */
    private static final String[] IMG_PLACEHOLDER_MARKS = {
            "data:image", "base64,", "loading.gif", "load.gif", "blank.gif",
            "pic.png", "placeholder", "lazy_loading", "default.png", "nopic",
            "favicon", "logo", "logo_placeholder", "pic-loading", "noimage"
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
                // ext 双格式支持（与原版 XBPQ 一致）：
                // 1) 远程规则：{"ext": "https://...json"}（gh-proxy 等镜像同样按 http 处理）
                // 2) 内联规则：{"ext": {...}} 或 {"ext": "{\"搜索url\":...}"} —— 宿主把
                //    JSON 对象序列化成字符串传入，直接解析，中文键经 convertChineseKeys 映射
                String content = ext.startsWith("http") ? fetchUrl(ext, null) : ext;
                if (content != null) {
                    content = content.trim();
                    // 去 UTF-8 BOM：远程文件/Windows 编辑器常带 BOM，JSONTokener 会解析失败
                    if (content.startsWith("\uFEFF")) content = content.substring(1).trim();
                }
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
            applyResponseCharset(RuleConfig.getRuleVal(rule, "charset"));
        }
    }

    /**
     * 应用规则的页面编码（规则键 "编码"/charset，如 GBK、GB2312）。
     * <p>原先 XBPQ 完全忽略该配置，响应体一律按 UTF-8 解码，
     * GBK 站点的标题/简介全部是乱码。</p>
     */
    private void applyResponseCharset(String charset) {
        if (charset == null || charset.isEmpty()) return;
        String normalized = charset.trim();
        if (normalized.isEmpty() || "UTF-8".equalsIgnoreCase(normalized)
                || "utf8".equalsIgnoreCase(normalized)) return;
        try {
            HttpClient client = httpClient();
            if (client instanceof OkHttpWrapper) {
                // httpClient 是静态共享实例（多站点复用），字符集必须按本站 host
                // 隔离设置——原先设为全局默认，后加载 "编码" 的站点会覆盖先加载
                // 站点的设置，导致 GBK/UTF-8 混部环境下部分站点整站乱码
                String host = "";
                try {
                    String home = getHomeUrl();
                    if (!home.isEmpty()) host = new java.net.URL(home).getHost();
                } catch (Exception ignored) {
                }
                if (host.isEmpty()) {
                    try {
                        String home = RuleConfig.getRuleVal(rule, "homeUrl");
                        if (!home.isEmpty()) host = new java.net.URL(home).getHost();
                    } catch (Exception ignored) {
                    }
                }
                ((OkHttpWrapper) client).setResponseCharsetForHost(host, normalized);
            }
        } catch (Exception e) {
            SpiderDebug.log("设置页面编码失败: " + normalized);
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
        // 修复：原实现只处理第一个 [替换:] 标记，变量链串联多个标记时
        // 后续标记原样残留在结果里。现循环处理，每个标记作用于其之前的累积文本。
        StringBuilder sb = new StringBuilder(value);
        for (int guard = 0; guard < 10; guard++) {
            int ri = sb.indexOf("[替换:");
            if (ri < 0) break;
            int re = sb.indexOf("]", ri);
            if (re < ri) break;
            String replaceContent = sb.substring(ri + 4, re);
            String result = sb.substring(0, ri);
            // `\#` 为转义的字面量 #（与 StringCutRule 后处理器语义一致）
            for (String pair : replaceContent.split("(?<!\\\\)#")) {
                int idx = pair.indexOf(">>");
                if (idx > 0) {
                    String from = pair.substring(0, idx).trim().replace("\\#", "#");
                    String to = pair.substring(idx + 2).trim().replace("\\#", "#");
                    result = result.replace(from, to);
                }
            }
            sb = new StringBuilder(result).append(sb.substring(re + 1));
        }
        return sb.toString();
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
    /** 工具链跨函数递归深度上限（防 [工具:源码] ↔ getHomeUrl ↔ expandVariables 循环配置栈溢出） */
    private static final int MAX_TOOL_DEPTH = 4;
    /** ThreadLocal 深度计数器（int[] 装箱避免每次调用新建 Integer） */
    private static final ThreadLocal<int[]> TOOL_DEPTH = ThreadLocal.withInitial(() -> new int[]{0});

    private String executeTools(String input) {
        if (input == null || input.isEmpty() || input.indexOf('[') < 0) return input;
        int[] depth = TOOL_DEPTH.get();
        if (depth[0] >= MAX_TOOL_DEPTH) {
            SpiderDebug.log("工具链递归超过 " + MAX_TOOL_DEPTH + " 层，中止展开（规则可能存在循环引用）");
            return input;
        }
        depth[0]++;
        try {
            Matcher m = P_TOOL.matcher(input);
            if (!m.find()) return input;
            StringBuilder sb = new StringBuilder();
            m.reset();
            while (m.find()) {
                String toolSpec = m.group(1);
                // 将工具前面的累积文本作为 prefix 传入，使 [工具:1截取N] 能截取上下文文本
                String result = executeSingleTool(toolSpec, sb.toString());
                m.appendReplacement(sb, Matcher.quoteReplacement(result == null ? "" : result));
            }
            m.appendTail(sb);
            return sb.toString();
        } finally {
            depth[0]--;
        }
    }

    private String executeSingleTool(String spec, String prefix) {
        if (spec == null || spec.isEmpty()) return "";
        String[] parts = spec.split("#", 2);
        String toolName = parts[0].trim();
        String arg = parts.length > 1 ? parts[1].trim() : "";

        try {
            // [工具:随机字符-N-唯一]：N 支持任意位数（原实现只注册了写死的 -3-，
            // 配 "随机字符-6-唯一" 会落入 default 返回空串）
            if (toolName.startsWith("随机字符-") && toolName.endsWith("-唯一")) {
                String nStr = toolName.substring("随机字符-".length(),
                        toolName.length() - "-唯一".length()).trim();
                int len = parseIntSafe(nStr, 0);
                if (len > 0 && len <= 64) return randomString(len);
                return "";
            }
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
                    // 语义：截取第 N 段（1-based）。
                    // 写法一（从上下文截取）：文本在工具之前，如 "a,b,c[工具:1截取2]" → "b"
                    //   此时 arg 仅含 N（可选 #分隔符），输入取自 prefix。
                    // 写法二（显式文本）：arg 为 "N#待截取文本"，直接截取 arg 文本（兼容旧配置）。
                    // 分隔符默认 ","，可用 "N#sep#文本" 指定（第二个 # 后的全部作为文本）。
                    int n;
                    String srcText;
                    String[] argParts = arg.split("#", 3);
                    n = Integer.parseInt(argParts[0].trim());
                    if (argParts.length >= 3) {
                        // 显式写法：N#sep#文本
                        String sep = argParts[1];
                        srcText = argParts[2];
                    } else if (argParts.length == 2) {
                        // 仅 "N#文本"：文本中不含自定义分隔符，用默认 ","
                        srcText = argParts[1];
                    } else {
                        // 仅 "N"：从工具前的上下文 prefix 截取
                        srcText = prefix == null ? "" : prefix;
                    }
                    if (srcText.isEmpty()) return "";
                    String sep = (argParts.length >= 3) ? argParts[1] : ",";
                    String[] ss = srcText.split(java.util.regex.Pattern.quote(sep), -1);
                    int idx = n - 1;
                    return (idx >= 0 && idx < ss.length) ? ss[idx] : "";
                }
                case "随机字符-3-唯一":
                    return randomString(3);
                case "源码转b64": {
                    // 格式：源码转b64#解密aes-<key>-<iv>-<alg>
                    // 修复：原实现把整个 arg（"解密aes-key-iv-AES/CBC/..."）当作 Base64 密文去解密，
                    // 必然抛异常返回空；且规则里配置的 key/iv 从未被使用。
                    // 现解析 arg 中的 key/iv（缺省用内置值），密文取自工具标记之前的上下文文本。
                    String key = "f5d965df75336270";
                    String iv = "97b60394abc2fbe1";
                    String cipherText = prefix == null ? "" : prefix;
                    if (arg.startsWith("解密aes")) {
                        String cfg = arg.substring("解密aes".length());
                        if (cfg.startsWith("-")) cfg = cfg.substring(1);
                        String[] seg = cfg.split("-", 3);
                        if (seg.length >= 2) {
                            if (!seg[0].trim().isEmpty()) key = seg[0].trim();
                            if (!seg[1].trim().isEmpty()) iv = seg[1].trim();
                        }
                    } else if (!arg.isEmpty()) {
                        // 兼容旧写法：arg 直接就是密文
                        cipherText = arg;
                    }
                    if (cipherText.isEmpty()) return "";
                    String decrypted = executeAesDecrypt(cipherText, key, iv);
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
                if (location == null) return url;
                if (location.startsWith("http://") || location.startsWith("https://")) return location;
                // 修复：相对 Location（如 "/jump?to=xxx"）原样返回会导致后续
                // 拼接/请求失败，现按请求地址补全为绝对 URL
                try {
                    return new java.net.URL(new java.net.URL(url), location).toString();
                } catch (Exception e2) {
                    return location;
                }
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
        // 通用请求头（JSON / UA 简写 / Key$Value#... 短语法）
        applyHeaderValue(headers, getVal("header"));
        // 修复：真实规则里 "头部集合"（xBPQ 4 条）与 "User"（如 "User-Agent:Dart/2.14 (dart:io)"）
        // 都是请求头的另一种写法，原先被完全忽略，导致这些站点被反爬拦截。
        applyHeaderValue(headers, getVal("头部集合"));
        applyHeaderValue(headers, getVal("User"));
        if (sectionKey != null) {
            applyHeaderValue(headers, getVal(sectionKey));
        }
        String ua = getVal("User-Agent");
        if (ua != null && !ua.isEmpty()) headers.put("User-Agent", resolveUaAlias(ua));
        String referer = getVal("Referer");
        if (referer != null && !referer.isEmpty()) headers.put("Referer", referer);
        return headers.isEmpty() ? null : headers;
    }

    /**
     * 解析单个请求头配置项并合并进 map，自动识别三种写法：
     * <ol>
     *   <li>JSON 对象：{"User-Agent":"..."}</li>
     *   <li>短语法：{@code User-Agent$xxx#Referer$yyy} 或 {@code User-Agent:xxx}</li>
     *   <li>UA 简写："手机"/"电脑"/"苹果手机" 或原始 UA 字符串</li>
     * </ol>
     */
    private void applyHeaderValue(Map<String, String> headers, String raw) {
        if (raw == null) return;
        String v = raw.trim();
        if (v.isEmpty()) return;
        if (v.startsWith("{")) {
            mergeJsonHeader(headers, v);
            return;
        }
        if (parseHeaderShortSyntax(v, headers)) return;
        // 兜底：当作 UA（简写或完整 UA 串）
        String ua = resolveUaAlias(v);
        if (ua != null) headers.put("User-Agent", ua);
    }

    /**
     * 解析 {@code Key$Value#Key$Value} 与 {@code Key:Value} 两种短语法。
     *
     * @return true 表示已按短语法解析（无论是否解析出条目）
     */
    private static boolean parseHeaderShortSyntax(String value, Map<String, String> out) {
        // 形式1：Key$Value#Key$Value（头部集合 / 播放请求头 的常用写法）
        if (value.indexOf('$') > 0) {
            boolean hit = false;
            for (String seg : value.split("#")) {
                int d = seg.indexOf('$');
                if (d > 0 && d < seg.length() - 1) {
                    String name = seg.substring(0, d).trim();
                    if (isHeaderNameLike(name)) {
                        out.put(name, seg.substring(d + 1).trim());
                        hit = true;
                    }
                }
            }
            return hit;
        }
        // 形式2：Key:Value / Key：Value（单行）
        int c = value.indexOf(':');
        int cf = value.indexOf('：');
        int sep = (c < 0) ? cf : (cf < 0 ? c : Math.min(c, cf));
        if (sep > 0 && sep < value.length() - 1) {
            String name = value.substring(0, sep).trim();
            if (isHeaderNameLike(name)) {
                out.put(name, value.substring(sep + 1).trim());
                return true;
            }
        }
        return false;
    }

    /** 是否为合法的 HTTP 头名形态（仅字母数字、-、_），用于区分 "Key:Value" 与整段 UA 串 */
    private static boolean isHeaderNameLike(String name) {
        if (name == null || name.isEmpty() || name.length() > 64) return false;
        for (int i = 0; i < name.length(); i++) {
            char ch = name.charAt(i);
            if (!(Character.isLetterOrDigit(ch) || ch == '-' || ch == '_')) return false;
        }
        return true;
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
                try {
                    int v = Integer.parseInt(t.trim());
                    // 修复：规格中「超时」单位为毫秒（写法说明示例 "10000"），
                    // 而网络层按秒使用——原先 "10000" 会变成 10000 秒（约 2.8 小时），
                    // 弱网下请求看似永久挂起。≥1000 视为毫秒换算成秒，<1000 视为已配秒数；
                    // 并钳制到 [1,120] 秒防极端配置。
                    if (v >= 1000) v = v / 1000;
                    timeout = Math.max(1, Math.min(120, v));
                } catch (NumberFormatException ignored) {}
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
            // 原版 XBPQ 动态筛选写法（旺旺影视等）：类型/类型值、地区/地区值、
            // 年份/年份值、排序/排序值 与 筛选XX名称/替换词（f*_name/f*_value）同功能，
            // 此处等价回填，不新增键。含 "&&" 的值是详情页截取规则（如 "类型" 被用作
            // detail_type），不是筛选列表，必须排除。
            String[] pairFallbacks = {
                    "类型", "类型值", "fcatelog_name", "fcatelog_value",
                    "地区", "地区值", "farea_name", "farea_value",
                    "年份", "年份值", "fyear_name", "fyear_value",
                    "排序", "排序值", "fsort_name", "fsort_value"
            };
            for (int i = 0; i < pairFallbacks.length; i += 4) {
                if (getVal(pairFallbacks[i + 2]).isEmpty() && getVal(pairFallbacks[i + 3]).isEmpty()) {
                    String n = RuleConfig.getRuleVal(rule, pairFallbacks[i]);
                    String v = RuleConfig.getRuleVal(rule, pairFallbacks[i + 1]);
                    if (!n.isEmpty() && !v.isEmpty() && !n.contains("&&") && !v.contains("&&")) {
                        rule.put(pairFallbacks[i + 2], n);
                        rule.put(pairFallbacks[i + 3], v);
                        if (pairFallbacks[i + 2].equals("fcatelog_name")) { fcatelog_name = n; fcatelog_value = v; }
                        else if (pairFallbacks[i + 2].equals("farea_name")) { farea_name = n; farea_value = v; }
                        else if (pairFallbacks[i + 2].equals("fyear_name")) { fyear_name = n; fyear_value = v; }
                        else if (pairFallbacks[i + 2].equals("fsort_name")) { fsort_name = n; fsort_value = v; }
                    }
                }
            }
            // 修复：原先无条件注入 Maccms 排序默认值，导致任何含 {by} 占位符的规则
            // 都会凭空生成一个 by 筛选组，并进一步顶掉规则内联配置的 筛选 JSON。
            // 现仅在用户显式配置了任一 f*_name/f*_value 时（即选择 EXT 动态筛选）才注入。
            boolean hasDynamicFilterCfg = !(fclass_name + fclass_value + fcatelog_name + fcatelog_value
                    + farea_name + farea_value + fyear_name + fyear_value
                    + flang_name + flang_value).isEmpty();
            if (fsort_name.isEmpty() && hasDynamicFilterCfg) fsort_name = "时间&人气&评分";
            if (fsort_value.isEmpty() && hasDynamicFilterCfg) fsort_value = "time&hits&score";

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

            // 动态筛选的分类值：优先 分类值(class_value)；未配置时用实际分类的 type_id
            //（旺旺影视等仅配 分类 "电影$1#..." 的规则，筛选组须按这些 id 逐分类生成）
            String categoryValues = classValuesCfg;
            if (categoryValues.isEmpty() && !classes.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                for (Class c : classes) {
                    if (sb.length() > 0) sb.append("&");
                    sb.append(c.getTypeId());
                }
                categoryValues = sb.toString();
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
                            categoryValues, getVal("class_url"),
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
                    // 修复：原实现只要 URL 模板含占位符就走动态构建，会直接用生成的
                    // 筛选顶掉规则中内联配置的 筛选（filter）JSON，导致配置好的
                    // 剧情/地区/年份等筛选全部丢失。
                    // 现改为：内联 筛选 优先，缺失时才动态构建。
                    Object raw = rule.opt("filter");
                    if (raw instanceof JSONObject && ((JSONObject) raw).length() > 0) {
                        filters = (JSONObject) raw;
                    } else if (hasPlaceholders) {
                        LinkedHashMap<String, List<Filter>> filterMap = buildFilter(
                                categoryValues, classUrlTpl,
                                fclass_name, fclass_value,
                                fcatelog_name, fcatelog_value,
                                farea_name, farea_value,
                                fyear_name, fyear_value,
                                flang_name, flang_value,
                                fsort_name, fsort_value);
                        if (filterMap != null && !filterMap.isEmpty()) {
                            filters = new JSONObject(new com.google.gson.Gson().toJson(filterMap));
                        }
                    }
                }
            }

            // 构建结果。
            // 修复：原实现在「带筛选」分支提前 return，热门推荐在配置了筛选的站点上永不展示。
            // 现统一先产出基础 JSON，再挂上 hot 列表。
            String baseJson = (filter && filters != null)
                    ? Result.string(classes, filters)
                    : Result.get().classes(classes).string();
            if (hotRecommend) {
                try {
                    JSONArray hot = fetchHotRecommend();
                    if (hot.length() > 0) {
                        JSONObject out = new JSONObject(baseJson);
                        out.put("list", hot);
                        return out.toString();
                    }
                } catch (Exception ignored) {}
            }
            return baseJson;
        } catch (Exception e) {
            SpiderDebug.log(e);
            return "";
        }
    }

    /**
     * 首页推荐视频。
     * <p>抓取主页并按列表规则提取，条数上限取自「首页」/firstpage —— 参考实现
     * （XBPQ优化前 homeVideoContent 第4076-4089行、XBPQ 第18次 第2865-2868行）中
     * 该键的含义就是<b>首页视频数量上限</b>（真实规则取值形如 120 / 200），
     * 而非分类分页的页码替换串，两者不可混用。</p>
     */
    @Override
    public String homeVideoContent() {
        try {
            fetchRule();
            if (rule == null) return "";
            JSONArray hot = fetchHotRecommend();
            return new JSONObject().put("list", hot).toString();
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
     *   <li>{@code 分类数组+分类标题+分类ID} —— 从主页动态抓取分类（LiteApple/BTT 写法）</li>
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

        // 0.5) 分类数组/分类标题/分类ID：从主页动态抓取分类（LiteApple/BTT 写法）
        String catArray = getVal("cat_array");
        if (!catArray.isEmpty()) {
            JSONArray dynamic = buildDynamicCategories(catArray);
            if (dynamic.length() > 0) return dynamic;
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

    /**
     * 分类数组/分类标题/分类ID 动态抓取分类（写法说明 §二，LiteApple/BTT 写法）。
     * <p>抓主页（可先按 分类二次截取 cat_twice 截取区域），按 分类数组 切块，
     * 逐块提取 分类标题/分类ID。分类ID 提取出的相对路径（如 /vodlist/6.html）
     * 补全为绝对 URL 直接作为 type_id，走 buildCategoryUrl 的"tid 即链接"分支。</p>
     */
    private JSONArray buildDynamicCategories(String catArray) {
        JSONArray classes = new JSONArray();
        try {
            String homeUrl = getHomeUrl();
            if (homeUrl.isEmpty()) return classes;
            String body = fetchUrl(homeUrl, buildHeaders(null));
            if (body.isEmpty()) return classes;
            String catTwice = getVal("cat_twice");
            if (!catTwice.isEmpty()) {
                if (CssRule.isCssRule(catTwice)) {
                    String cut = CssRule.cutRegion(body, catTwice);
                    if (!cut.isEmpty()) body = cut;
                } else {
                    body = StringCutRule.applySecondCut(body, catTwice);
                }
            }
            // 未配置字段时的默认规则（与写法说明默认值一致）；
            // ">&&</a>" 兜底取锚点文本（末对截取缺失后缀时贪婪取到块尾）
            String titleRule = getVal("cat_title");
            if (titleRule.isEmpty()) titleRule = "title=\"&&\"||alt=\"&&\"||>&&</a>";
            String idRule = getVal("cat_id");
            if (idRule.isEmpty()) idRule = "href=\"&&\"";
            for (String item : RegexFieldHelper.splitItems(body, catArray)) {
                String id = RegexFieldHelper.extract(item, idRule).trim();
                if (id.isEmpty()) continue;
                String name = RegexFieldHelper.extract(item, titleRule).trim();
                if (id.startsWith("/")) id = absUrl(id);
                addClass(classes, id, name.isEmpty() ? id : name);
            }
        } catch (Exception e) {
            SpiderDebug.log("分类数组动态抓取失败: " + e.getMessage());
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
            String fclassName, String fclassValue,
            String fcatelogName, String fcatelogValue,
            String fareaName, String fareaValue,
            String fyearName, String fyearValue,
            String flangName, String flangValue,
            String fsortName, String fsortValue) {
        try {
            LinkedHashMap<String, List<Filter>> result = new LinkedHashMap<>();
            String[] rawCategories = categoryValues.split("&");
            for (int i = 0; i < rawCategories.length; i++) {
                String catValue = rawCategories[i].trim().replaceAll("＆＆", "&");
                if (catValue.isEmpty()) continue;
                List<Filter> filters = new ArrayList<>();

                // class（子分类 / 类型）
                // 修复：fcatelog（筛选类型）原先完全未参与构建，配置后静默失效。
                // 现 fclass 优先，fclass 未配置时回退 fcatelog，兼容 XYQHiker 写法。
                if (urlTemplate.contains("{class}")) {
                    if (!fclassName.isEmpty() && !fclassValue.isEmpty()) {
                        addFilterEntryByIndex(filters, "class", "类型", fclassName, fclassValue, i);
                    } else if (!fcatelogName.isEmpty() && !fcatelogValue.isEmpty()) {
                        addFilterEntryByIndex(filters, "class", "类型", fcatelogName, fcatelogValue, i);
                    }
                }
                // area（地区）
                if (urlTemplate.contains("{area}")) {
                    addFilterEntryByIndex(filters, "area", "地区", fareaName, fareaValue, i);
                }
                // year（年份）
                if (urlTemplate.contains("{year}")) {
                    addFilterEntryByIndex(filters, "year", "年份", fyearName, fyearValue, i);
                }
                // lang（语言）
                if (urlTemplate.contains("{lang}")) {
                    addFilterEntryByIndex(filters, "lang", "语言", flangName, flangValue, i);
                }
                // by（排序）
                if (urlTemplate.contains("{by}")) {
                    addFilterEntryByIndex(filters, "by", "排序", fsortName, fsortValue, i);
                }
                // cateId（分类本身）
                // 修复：原条件为 "!contains({cateId}) || !isEmpty"，逻辑写反——
                // 模板不含 {cateId} 时仍生成无用的分类筛选，模板含 {cateId} 且分类值为空时反而不生成。
                if (urlTemplate.contains("{cateId}") && !catValue.isEmpty()) {
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

    /**
     * 构建单个 Filter 条目。
     * <p>参数顺序与 XYQHiker 对齐：第 3 个参数为显示名（→Filter.Value.n，对应 *_name），
     * 第 4 个参数为 URL 替换词（→Filter.Value.v，对应 *_value）。
     * 修复：原实现的第 3/4 参语义被倒置，导致界面显示"替换词"、URL 拼接"名称"，筛选完全错位。
     */
    private Filter buildFilterEntry(String key, String nameLabel, String nameVal, String valueVal) {
        try {
            if (nameVal == null) nameVal = "";
            if (valueVal == null) valueVal = "";
            List<Filter.Value> values = new ArrayList<>();
            if ("by".equals(key)) {
                values.add(new Filter.Value("默认", ""));
            } else {
                values.add(new Filter.Value("全部", ""));
            }
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

    /**
     * 按分类索引生成筛选条目。
     * <p>{@code nameCfg} 为「名称」（→Filter.Value.n），{@code valueCfg} 为「替换词」（→Filter.Value.v）。
     * 替换词为 "*" 时以名称兼作替换词；两者均含 "||" 时按分类索引取对应分段
     *（XBPQ 写法说明中声明的 "||" 分组写法，原实现遗漏）。
     */
    private void addFilterEntryByIndex(List<Filter> filters, String key, String label,
                                       String nameCfg, String valueCfg, int index) {
        if (nameCfg == null || valueCfg == null || nameCfg.isEmpty() || valueCfg.isEmpty()) return;
        // 替换词为 "*" 时，名称同时充当替换词
        String effectiveValue = "*".equals(valueCfg.trim()) ? nameCfg : valueCfg;

        if (nameCfg.contains("||") && effectiveValue.contains("||")) {
            String[] names = nameCfg.split("\\|\\|");
            String[] values = effectiveValue.split("\\|\\|");
            if (index < names.length && index < values.length && !names[index].equals("空")) {
                Filter f = buildFilterEntry(key, label, names[index].trim(), values[index].trim());
                if (f != null) filters.add(f);
            }
            return;
        }
        Filter f = buildFilterEntry(key, label, nameCfg.trim(), effectiveValue.trim());
        if (f != null) filters.add(f);
    }

    /** 热门推荐：抓取主页并用列表规则提取 */
    private JSONArray fetchHotRecommend() throws Exception {
        String homeUrl = getHomeUrl();
        if (homeUrl.isEmpty()) return new JSONArray();
        String body = fetchUrl(homeUrl, buildHeaders(null));
        if (body.isEmpty()) return new JSONArray();
        JSONArray videos = extractVideoListByWeb(body);
        if (videos == null) return new JSONArray();
        videos = applyListPostProcess(videos);
        // 「首页」/firstpage = 首页视频条数上限（参考 XBPQ优化前 第4084-4089行）。
        // 仅当纯数字且 > 1 时截断：1 是规则模板的默认值（表示"首页即第 1 页"），
        // 按它截断会把首页砍成 1 条；非数字（区块配置串等写法）同样不限制。
        int maxVideos = parseIntSafe(getVal("firstpage"), -1);
        if (maxVideos > 1 && videos.length() > maxVideos) {
            JSONArray trimmed = new JSONArray();
            for (int i = 0; i < maxVideos; i++) trimmed.put(videos.get(i));
            videos = trimmed;
        }
        return videos;
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
                // 兜底：响应非 JSON / catjsonlist 路径取不到数组时回退网页截取。
                // 否则站点改版返回 HTML 会让分类永久空白（异常被外层 catch 吞成 ""）。
                if (videos == null || videos.length() == 0) {
                    SpiderDebug.log("[XBPQ] cat_mode=0 JSON 解析无结果，回退网页截取模式");
                    videos = extractVideoListByWeb(body);
                }
            } else {
                videos = extractVideoListByWeb(body);
            }
            if (videos == null) videos = new JSONArray();

            // cat_prefix / cat_suffix：对 vod_id 中的链接加前缀后缀（借鉴 XYQBiu 第254/308行）
            // 修复一：cat_mode=0 时 parseCatJsonMode 已经拼接过一次前后缀，
            //        这里再拼一次会造成双重前缀（链接被破坏），故 JSON 模式下跳过。
            // 修复二：原实现要求 vod_id 必须含 "$$$" 才处理，而网页截取模式下 vod_id
            //        就是裸链接（不含 "$$$"），导致 cat_prefix/cat_suffix 在最常用的
            //        网页截取模式下完全失效。现对两种情况都生效。
            String catPrefix = getVal("cat_prefix");
            String catSuffix = getVal("cat_suffix");
            if (!"0".equals(catMode) && (!catPrefix.isEmpty() || !catSuffix.isEmpty())) {
                for (int i = 0; i < videos.length(); i++) {
                    JSONObject v = videos.getJSONObject(i);
                    String rawId = v.optString("vod_id", "");
                    if (rawId.isEmpty()) continue;
                    // vod_id 格式通常为 "name$$$pic$$$link"，只需处理最后的链接部分
                    int lastSep = rawId.lastIndexOf("$$$");
                    String before = lastSep >= 0 ? rawId.substring(0, lastSep + 3) : "";
                    String link = lastSep >= 0 ? rawId.substring(lastSep + 3) : rawId;
                    v.put("vod_id", before + catPrefix + link + catSuffix);
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

            int pgNum = parseIntSafe(pg, 1);
            // 只计算一次：每次都要对整页 HTML 跑 5 个正则，重复调用纯属浪费
            int pageCount = guessPageCount(body, videos.length(), pgNum);
            JSONObject result = new JSONObject();
            // 分页四件套与框架 Result 约定对齐：全部数值型（Integer）；
            // total 与 pagecount 同口径（pagecount × limit），列表为空时 total=0
            result.put("page", pgNum);
            result.put("pagecount", pageCount);
            result.put("limit", PAGE_LIMIT);
            result.put("total", videos.length() > 0 ? pageCount * PAGE_LIMIT : 0);
            result.put("list", videos);
            return result.toString();
        } catch (Exception e) {
            SpiderDebug.log(e);
            return "";
        }
    }

    /**
     * 网页截取模式提取视频列表（CSS 规则自动分流）。
     * <p>规则未配 数组（list_array）但配了 搜索数组（search_array）时，
     * 分类页回退用搜索规则提取——foxjun 等站点分类页与搜索页结构完全同构
     * （实测均为 class="media" 块），原版 XBPQ 即依赖该回退。</p>
     */
    private JSONArray extractVideoListByWeb(String body) throws Exception {
        String listArray = getVal("list_array");
        JSONObject ruleForExtract = rule;
        if (listArray.isEmpty()) {
            String searchArray = getVal("search_array");
            if (searchArray.isEmpty()) return new JSONArray();
            // 同功能字段替换：搜索数组/搜索标题/搜索图片/搜索链接/搜索副标题 → 列表字段
            ruleForExtract = new JSONObject(rule.toString());
            copyIfEmpty(ruleForExtract, "search_array", "list_array");
            copyIfEmpty(ruleForExtract, "search_name", "list_name");
            copyIfEmpty(ruleForExtract, "search_pic", "list_pic");
            copyIfEmpty(ruleForExtract, "search_id", "list_id");
            copyIfEmpty(ruleForExtract, "search_remarks", "list_remarks");
            copyIfEmpty(ruleForExtract, "search_twice", "list_twice");
            copyIfEmpty(ruleForExtract, "search_prefix", "list_prefix");
            copyIfEmpty(ruleForExtract, "search_suffix", "list_suffix");
            listArray = searchArray;
        }
        return ExtractorFactory
                .createVideoListExtractor(CssRule.isCssRule(listArray))
                .extract(body, ruleForExtract);
    }

    /** 目标键为空时从源键复制值（同功能字段替换用） */
    private static void copyIfEmpty(JSONObject ruleObj, String fromKey, String toKey) {
        String to = ruleObj.optString(toKey, "");
        if (!to.isEmpty()) return;
        String from = ruleObj.optString(fromKey, "");
        if (!from.isEmpty()) ruleObj.put(toKey, from);
    }

    /**
     * cat_mode=0 JSON 分类模式（借鉴 XYQBiu 第237-280行）。
     * 从 JSON 响应中提取 vod 列表，支持 catjsonlist 多级路径（a.b / a.b.c）。
     * cat_prefix/cat_suffix 加在 id 上；cat_subtitle 填入 vod_remarks。
     * <p>响应体非 JSON 时返回空数组，由 categoryContent 回退网页截取模式。</p>
     */
    private JSONArray parseCatJsonMode(String body, String webUrl) throws Exception {
        JSONObject data;
        try {
            data = new JSONObject(body.trim());
        } catch (Exception e) {
            SpiderDebug.log("[XBPQ] cat_mode=0 响应非 JSON: " + e.getMessage());
            return new JSONArray();
        }
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

        // 未显式配置时回退标准字段名，并经 JsonParser.pickField 走别名表
        //（原实现用空 key 直接 optString("")，导致所有条目 name/id 全为空）
        String nameKey = getVal("catjsonname");
        if (nameKey.isEmpty()) nameKey = "vod_name";
        String idKey = getVal("catjsonid");
        if (idKey.isEmpty()) idKey = "vod_id";
        String picKey = getVal("catjsonpic");
        String stitleKey = getVal("catjsonstitle");
        String prefix = getVal("cat_prefix");
        String suffix = getVal("cat_suffix");
        boolean picNeedProxy = "1".equals(getVal("PicNeedProxy"));

        JSONArray videos = new JSONArray();
        for (int j = 0; j < vodArray.length(); j++) {
            try {
                JSONObject vod = vodArray.getJSONObject(j);
                String name = JsonParser.pickField(vod, nameKey).trim();
                String id = JsonParser.pickField(vod, idKey).trim();
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
     *   <li>{cateId}→tid，{catePg}→站点页码（{@code startPage + pg - 1}），
     *       支持 cate_firstpage（分类起始页码）作为第 1 页替换串</li>
     *   <li>其余占位符（{area}/{by}/{class}/{lang}/{year}/{letter}/…）取自分页筛选 extend，缺失时置空</li>
     *   <li>支持 [firstPage=...] 语法：站点第 1 页使用括号内无页码链接</li>
     *   <li>支持末尾裸 [...] 作为首页备用链接（如 ".../{catePg}/[首页URL]"）</li>
     *   <li>";;" 及其后内容为模式标识（;;z / ;;mrc*），一律剥离，不参与请求</li>
     *   <li>相对路径/协议相对路径自动补全为主机地址</li>
     * </ul>
     */
    private String buildCategoryUrl(String tid, String pg, Map<String, String> extend) {
        // 特殊分类链接构建的分类 type_id 是完整 URL（buildClassList 分支 0），
        // 直接作为请求地址；硬套 class_url 模板会把 URL 塞进 {cateId} 产生乱链，
        // class_url 为空时则直接返回空白——特殊分类功能整体不可用
        if (tid != null && (tid.startsWith("http://") || tid.startsWith("https://"))) {
            return tid;
        }
        // getVal 内部已完成 {{变量}} 展开与 [工具:...] 执行，无需再次 expandVariables
        String classUrl = getVal("class_url");
        if (classUrl.isEmpty()) return "";

        // 1) ";;" 之后为模式标识（;;z / ;;mrcRAD / ;;mrcRAz …），整段剥离。
        //    参考实现（XBPQ 第18次 buildCategoryPath）明确：";;后为模式标识，不影响请求"。
        //    原实现把 ";;mrc" 之后的标识当后缀拼回 URL（"...&jq=;;mrcRA" → "...&jq=RA"，
        //    查询参数被污染），并用方括号内的首页 URL 无条件替换整条模板
        //    （".../{catePg}/[首页URL];;mrcRAz" → 翻页永远停在首页）。
        int semi = classUrl.indexOf(";;");
        if (semi >= 0) classUrl = classUrl.substring(0, semi).trim();

        // 2) 页码计算：站点页码 = startPage + pg - 1（与 XBPQ优化前.shiftStartPage 一致）
        int pgNum = parseIntSafe(pg, 1);
        int startPage = parseIntSafe(getVal("startpage"), 1);
        if (startPage < 0) startPage = 0;
        int sitePage = startPage + pgNum - 1;
        // cate_firstpage（分类起始页码 / 分类首页）是"第 1 页的替换串"。
        // 绝不能用 首页/firstpage 顶替：参考实现中它是【首页视频条数上限】
        //（真实规则取值形如 120 / 200），填进 {catePg} 会生成 ".../120" 之类的错误地址。
        String cateFirstPage = getVal("cate_firstpage");
        String pageStr = (pgNum == 1 && !cateFirstPage.isEmpty())
                ? cateFirstPage : String.valueOf(sitePage);
        // 仅当请求的就是站点第 1 页（0 或 1 起始）时才启用"无页码"首页模板
        boolean isSiteFirstPage = pgNum == 1 && startPage <= 1;

        // 3) [firstPage=...] 语法
        int br = classUrl.indexOf("[firstPage=");
        if (br >= 0) {
            int end = classUrl.indexOf("]", br);
            if (end > br) {
                String firstPageTpl = classUrl.substring(br + "[firstPage=".length(), end);
                String normalTpl = classUrl.substring(0, br);
                classUrl = isSiteFirstPage ? firstPageTpl : normalTpl;
            }
        } else {
            // 4) 末尾裸 [...]（不含 firstPage=）作为首页备用链接，仅在站点第 1 页时启用
            int bOpen = classUrl.lastIndexOf('[');
            if (bOpen >= 0) {
                int bClose = classUrl.indexOf(']', bOpen);
                if (bClose > bOpen && classUrl.substring(bClose + 1).trim().isEmpty()) {
                    String inner = classUrl.substring(bOpen + 1, bClose).trim();
                    if (inner.startsWith("替换:")) inner = inner.substring(3).trim();
                    String head = classUrl.substring(0, bOpen).trim();
                    classUrl = (isSiteFirstPage && !inner.isEmpty())
                            ? expandVariables(inner) : head;
                }
            }
        }

        Matcher m = P_PLACEHOLDER.matcher(classUrl);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            // 键名按小写归一化，兼容规格白名单（XBPQ使用说明 §5.4）声明的变体：
            // {cateid}/{catepg}/{pg}/{page}/{cateIdEn}。此前只有大小写敏感的
            // {cateId}/{catePg} 被识别，小写变体落入 extend 分支取空被替换成空串，
            // 所有分页生成同一 URL，翻页静默失效。
            String norm = m.group(1).toLowerCase();
            String val;
            if ("cateid".equals(norm) || "cateiden".equals(norm)) {
                // buildFilter 会生成 cateId 筛选组，用户选中时 extend["cateId"] 优先
                //（XYQHiker 语义）；未选中回退 tid。
                String fromExtend = extend != null ? extend.get(m.group(1)) : null;
                if (fromExtend == null || fromExtend.isEmpty()) fromExtend = tid;
                // {cateIdEn}（XYQHiker 语义）= 整体 URL 编码的分类 ID；
                // {cateId} 做最小化编码（保留 / ? = & 等结构字符），与筛选占位符策略一致
                val = "cateiden".equals(norm) ? urlEncodeAll(fromExtend) : encodeUrlValue(fromExtend);
            } else if ("catepg".equals(norm) || "page".equals(norm) || "pg".equals(norm)) {
                // 页码/首页替换串属于站点 URL 路径结构，禁止整体 URL 编码
                // （否则 firstpage=?page=1 会被编码成 %3Fpage%3D1 破坏分页）
                val = pageStr;
            } else {
                String key = m.group(1);
                val = extend != null ? extend.get(key) : null;
                if (val == null) {
                    // {class} 回退为 tid 仅限"分类名即ID"的模板（不含 {cateId}）；
                    // Maccms 横杠模板（含 {cateId}，如 /vodshow/{cateId}---{class}-----{catePg}---{year}/）
                    // 中 {class} 是类型筛选槽位，回退 tid 会把分类 ID 污染进类型槽
                    val = "class".equals(key) && !classUrl.contains("{cateId}") ? tid : "";
                }
                // 筛选类占位符（来自用户输入的 extend 参数）做最小化编码：
                // 仅转义非 ASCII 与空格，保留 / ? = & : 等 URL 结构字符。
                // 修复：全量 URLEncoder.encode 会把 "/class/喜剧" 变成
                // "%2Fclass%2F%E5%96%9C%E5%89%A7"，破坏站点路径结构使筛选失效。
                val = encodeUrlValue(val);
            }
            // val 可能为 null（极端情况下），appendReplacement 要求非 null
            if (val == null) val = "";
            m.appendReplacement(sb, Matcher.quoteReplacement(val));
        }
        m.appendTail(sb);

        // 未赋值的路径型占位符会留下空路径段（"/{area}/{by}/" → "///"），需折叠；
        // 但 Maccms 横杠式（"/vodshow/1--------{catePg}---"）不含斜杠，不受影响
        return absUrl(normalizeEntity(collapseEmptyPathSegments(sb.toString())));
    }

    /**
     * 折叠因占位符取空而产生的重复斜杠（{@code "/{area}/{by}/" → "///" → "/"}）。
     * <p>仅处理 path 部分（{@code ?} / {@code #} 之前），并保留 scheme 的 {@code http://}。
     * 参考 XYQHiker：删除占位符后会再删一次 {@code /key/}。</p>
     */
    private static String collapseEmptyPathSegments(String url) {
        if (url == null || url.indexOf("//") < 0) return url;
        int cut = url.length();
        int q = url.indexOf('?');
        int h = url.indexOf('#');
        if (q >= 0) cut = q;
        if (h >= 0 && h < cut) cut = h;
        String path = url.substring(0, cut);
        StringBuilder b = new StringBuilder(path.length());
        for (int i = 0; i < path.length(); i++) {
            char c = path.charAt(i);
            // 连续斜杠：丢弃后一个，但 scheme 后的 "//" 必须保留
            if (c == '/' && b.length() > 0 && b.charAt(b.length() - 1) == '/'
                    && !(b.length() >= 2 && b.charAt(b.length() - 2) == ':')) {
                continue;
            }
            b.append(c);
        }
        return b.toString() + url.substring(cut);
    }

    /** 列表后处理：标题清洗 → 图片净化 → 去重 → 过滤词 → 倒序 */
    private JSONArray applyListPostProcess(JSONArray videos) throws Exception {
        return applyCommonListPostProcess(videos);
    }

    /**
     * 列表通用后处理：标题清洗 → 图片净化 → 去重 → 过滤词 → 倒序。
     * <p>分类页、首页热门推荐、搜索结果三处共用，保证「过滤词 / 倒序 / 去重」行为一致
     *（参考实现 XBPQ优化前 在列表与搜索两处都做了倒序与 shouldFilter）。</p>
     */
    private JSONArray applyCommonListPostProcess(JSONArray videos) throws Exception {
        if (videos == null) return new JSONArray();
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
                if (!pic.isEmpty()) {
                    video.put("vod_pic", pic);
                } else {
                    // 占位图/无效图：移除该字段，避免展示垃圾图片
                    try { video.remove("vod_pic"); } catch (Exception ignored) {}
                }
            }
            cleaned.put(video);
        }

        videos = dedupe(cleaned);
        videos = filterByWords(videos);
        return reverse ? reverseArray(videos) : videos;
    }

    /**
     * 过滤词过滤。
     * <p>分隔符为中英文逗号 {@code ,} / {@code ，}（参考实现 XBPQ优化前 的
     * {@code filterWord.split("[,，]")}）。</p>
     * <p>仅比对 vod_name / vod_remarks：vod_id 是 "name$$$pic$$$link" 整串或
     * 绝对 URL，任何出现在链接里的词（http/com/html/域名片段）都会把整页结果
     * 误杀，故不再参与匹配。过滤词面向的是标题/副标题层面的垃圾内容。</p>
     */
    private JSONArray filterByWords(JSONArray videos) throws Exception {
        String filterWord = getVal("filter_word");
        if (filterWord.isEmpty()) return videos;
        List<String> words = new ArrayList<>();
        for (String w : filterWord.split("[,，]")) {
            if (!w.trim().isEmpty()) words.add(w.trim());
        }
        if (words.isEmpty()) return videos;

        JSONArray kept = new JSONArray();
        for (int i = 0; i < videos.length(); i++) {
            JSONObject video = videos.getJSONObject(i);
            String name = video.optString("vod_name", "");
            String remarks = video.optString("vod_remarks", "");
            boolean blocked = false;
            for (String word : words) {
                if (name.contains(word) || remarks.contains(word)) {
                    blocked = true;
                    break;
                }
            }
            if (!blocked) kept.put(video);
        }
        return kept;
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

    /**
     * 页总数猜测：共N页 / 共N条 / 当前x/y页 / Page x of y / pagecount 字段。
     * <p><b>兜底与当前页挂钩</b>：列表为空视为末页（{@code pagecount = pgNum}，框架自然停止翻页）；
     * 列表非空则假定还有下一页（{@code pagecount = pgNum + 1}）。
     * 修复：原兜底恒返回 1 或 2，用户翻到第 3 页时 {@code page(3) > pagecount(2)}，
     * TVBox 判定无下一页，翻页在第 2 页卡死。</p>
     */
    private int guessPageCount(String body, int listSize, int pgNum) {
        if (pgNum < 1) pgNum = 1;
        if (body != null && !body.isEmpty()) {
            try {
                Matcher m = P_PAGE_TOTAL.matcher(body);
                if (m.find()) return Math.max(1, Integer.parseInt(m.group(1)));
                m = P_PAGE_CURRENT.matcher(body);
                if (m.find()) return Math.max(1, Integer.parseInt(m.group(2)));
                m = P_PAGE_EN.matcher(body);
                if (m.find()) return Math.max(1, Integer.parseInt(m.group(1)));
                m = P_PAGE_FIELD.matcher(body);
                if (m.find()) return Math.max(1, Integer.parseInt(m.group(1)));
                m = P_TOTAL_COUNT.matcher(body);
                if (m.find()) {
                    int total = Integer.parseInt(m.group(1));
                    return Math.max(1, (total + PAGE_LIMIT - 1) / PAGE_LIMIT);
                }
            } catch (Exception ignored) {
                // 解析失败走兜底
            }
        }
        return listSize <= 0 ? pgNum : pgNum + 1;
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

            // force_play 直接播放模式（借鉴 XYQBiu / XYQHiker）：
            // "1"=直接播放分类列表中的链接——跳过详情抓取，用 vod_id 中的链接作为唯一集数；
            // "2"=详情选集链接直接播放——仍走正常详情提取拿到选集列表，
            //     由 playerContent 对每个选集链接直连播放（不嗅探/不跳转）。
            // 修复：原实现把 "1"/"2" 同等处理，配置 "2" 的站点选集列表被压成
            // 一个列表链接，详情页真实的剧集列表全部丢失。
            String forcePlayCfg = getVal("force_play");
            if ("1".equals(forcePlayCfg)) {
                String rawId = vinfo.optString("vod_id", vid);
                // vod_id 格式 "name$$$pic$$$link"，提取最后一部分作为播放链接
                int lastSep = rawId.lastIndexOf("$$$");
                String playUrl = lastSep >= 0 ? rawId.substring(lastSep + 3) : rawId;
                // 修复：play_prefix/play_suffix 统一只在 playerContent 中拼接一次。
                // 原先此处与 playerContent 各拼一次，强制播放模式下链接会被加上双重前缀后缀。
                VodDetail detail = new VodDetail(new VodItem(vinfo));
                PlaySource src = new PlaySource("直接播放");
                // 集串需带 "标题$" 前缀，否则 TVBox 按 "$" 分割时把整个链接当集名
                src.addEpisode("播放$" + playUrl);
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
            // 播放图片兜底（写法说明 §六）：规则 "播放图片"/play_image 指定的
            // 固定封面，在 og:image 等所有来源都取不到时使用
            if (vod.optString("vod_pic", "").isEmpty()) {
                String fallbackPic = getVal("play_image");
                if (!fallbackPic.isEmpty()) {
                    String pic = cleanImageUrl(fallbackPic);
                    if (!pic.isEmpty()) vod.put("vod_pic", pic);
                }
            }
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
            if (!pic.isEmpty()) {
                vod.put("vod_pic", pic);
            } else {
                try { vod.remove("vod_pic"); } catch (Exception ignored) {}
            }
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

            // 修复：cleanHtml 已把换行压缩为单空格，原值正则 [^\n|。；;]+ 的
            // "到行尾"截断失效，会把后续所有 "主演：xxx 类型：xxx" 整行吞进当前字段。
            // 现改为：值结束于下一个任一 label 词首次出现处或分隔符 |。；;。
            int searchFrom = 0;
            while (true) {
                int li = text.indexOf(label, searchFrom);
                if (li < 0) break;
                int valStart = li + label.length();
                while (valStart < text.length()
                        && (text.charAt(valStart) == ':' || text.charAt(valStart) == '：'
                            || Character.isWhitespace(text.charAt(valStart)))) {
                    valStart++;
                }
                // 值终点 = 下一个 label 出现位置与各分隔符位置中的最小者
                int end = text.length();
                for (String other : labels) {
                    String o = other.trim();
                    if (o.isEmpty()) continue;
                    int oi = text.indexOf(o, valStart);
                    if (oi >= 0 && oi < end) end = oi;
                }
                String stops = "|。；;";
                for (int s = 0; s < stops.length(); s++) {
                    int si = text.indexOf(String.valueOf(stops.charAt(s)), valStart);
                    if (si >= 0 && si < end) end = si;
                }
                String val = text.substring(valStart, end).trim();
                if (!val.isEmpty()) {
                    try { vod.put(field, val); } catch (Exception ignored) {}
                    break;
                }
                // 值为空（如 label 后紧跟下一个 label），继续找下一处出现
                searchFrom = li + label.length();
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
        if (vid == null) vid = "";
        try {
            // 1) 加密规则：vid 整体为 base64(JSON)，解码后形如 {"vod_id":...,"vod_name":...}
            String decoded = new String(Base64.decode(vid, BASE64_FLAG), "UTF-8").trim();
            if (decoded.startsWith("{")) {
                return new JSONObject(decoded);
            }
        } catch (Exception ignored) {
            // 非 base64，继续按明文格式解析
        }
        // 2) 标准 XBPQ 格式：name$$$pic$$$link（$$$ 分隔，明文）
        //    link 可能是详情相对路径/绝对URL，需正确拆分供 buildDetailUrl 使用
        JSONObject vinfo = new JSONObject();
        try {
            String realId = vid;
            String name = "";
            String pic = "";
            int firstSep = vid.indexOf("$$$");
            if (firstSep >= 0) {
                name = vid.substring(0, firstSep);
                String rest = vid.substring(firstSep + 3);
                int secondSep = rest.indexOf("$$$");
                if (secondSep >= 0) {
                    pic = rest.substring(0, secondSep);
                    realId = rest.substring(secondSep + 3);
                } else {
                    realId = rest;
                }
            }
            if (!name.isEmpty()) vinfo.put("vod_name", name);
            if (!pic.isEmpty()) vinfo.put("vod_pic", pic);
            // 只保留真实 id/link 作为 vod_id，避免 buildDetailUrl 把 $$$ 垃圾拼进 URL
            vinfo.put("vod_id", realId);
        } catch (Exception ignored) {
            try { vinfo.put("vod_id", vid); } catch (Exception ignore2) {}
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

    /** 整体 URL 编码（{cateIdEn} 用，XYQHiker 语义），失败回退原文 */
    private static String urlEncodeAll(String value) {
        if (value == null || value.isEmpty()) return value == null ? "" : value;
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (Exception e) {
            return value;
        }
    }

    /**
     * URL 最小化编码：仅对非 ASCII 字符与空格做百分号编码，保留 {@code / ? = & : - _ . ~}
     * 等 URL 结构字符。
     * <p>筛选占位符的值来自规则自身的配置（如 {@code /class/喜剧}），本身就是 URL 片段，
     * 若整体 {@link URLEncoder#encode} 会把路径分隔符 {@code /} 也编码掉，导致 404。</p>
     */
    private static String encodeUrlValue(String value) {
        if (value == null || value.isEmpty()) return value;
        StringBuilder sb = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < 0x80 && c != ' ' && c != '"' && c != '<' && c != '>' && c != '#') {
                sb.append(c);
                continue;
            }
            try {
                for (byte b : String.valueOf(c).getBytes("UTF-8")) {
                    sb.append('%').append(String.format("%02X", b & 0xFF));
                }
            } catch (Exception e) {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /** 播放线路提取：多线（from_array）/单线（play_array/url_array），支持线路合并与剧集过滤 */
    private List<PlaySource> extractPlaySources(String body) throws Exception {
        List<PlaySource> sources = new ArrayList<>();

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

        // "[排序:xxx]" 是排序指令而非切分规则，必须先从 from_array 剥离，
        // 否则 splitItems 会拿 "</div>[排序:A,B]" 去 HTML 里匹配，导致线路切分不出结果。
        String fromArrayRaw = getVal("from_array");
        int sortMark = fromArrayRaw.indexOf("[排序:");
        String fromArrayClean = sortMark >= 0 ? fromArrayRaw.substring(0, sortMark) : fromArrayRaw;

        // 修复：真实规则（低端影视 / 4K影院 等）使用 "线路链接"（from_url）——
        // 每个线路块指向一个独立的子页面（分页线路），必须先抓取子页面再提取剧集。
        // 原先该配置被完全忽略，这类站点只能拿到空线路。
        String fromUrlRule = getVal("from_url");
        if (!fromUrlRule.isEmpty() && !fromArrayClean.isEmpty()) {
            List<PlaySource> byUrl = extractPlaySourcesByLineUrl(
                    body, fromArrayClean, fromUrlRule, epiPrefix, epiSuffix, epFilters);
            if (!byUrl.isEmpty()) return applyLineSortAndMerge(byUrl);
        }

        // CSS 模式以 from_array 为准：播放列表提取器仅按 from_array 判断模式；
        // url_array/play_array 可能为纯正则，误入 CSS 模式会导致线路切分失败。
        JSONObject ruleForExtract = rule;
        if (sortMark >= 0) {
            ruleForExtract = new JSONObject(rule.toString());
            ruleForExtract.put("from_array", fromArrayClean);
        }
        boolean cssMode = CssRule.isCssRule(fromArrayClean);
        JSONArray lines = ExtractorFactory.createPlayListExtractor(cssMode).extract(body, ruleForExtract, 0);
        if (lines == null) lines = new JSONArray();

        // sort 自定义排序：线路名规则含 [排序:xxx] 时按顺序重排
        // 格式示例：[排序:第一站,第二站,第三站]
        for (int i = 0; i < lines.length(); i++) {
            JSONObject line = lines.getJSONObject(i);
            String lineName = line.optString("name", "线路" + (i + 1));
            JSONArray episodes = line.optJSONArray("episodes");
            if (episodes == null) continue;
            List<String> epList = toEpisodeList(episodes, epiPrefix, epiSuffix, epFilters);
            if (epList.isEmpty()) continue;
            sources.add(new PlaySource(lineName, epList));
        }

        sources = applyLineSortAndMerge(sources);

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

    /**
     * 剧集数组 → 条目列表：过滤词过滤 + 选集链接前后缀。
     * <p>剧集条目格式为 {@code "标题$链接"}，前后缀只能作用于 {@code $} 之后的链接部分；
     * 若直接作用于整条，{@code $} 分隔符会被污染，TVBox 会把 "前缀标题" 当集名、
     * "链接后缀" 当播放地址，导致整条线路不可播。</p>
     * <p>修复：相对链接（如 stui/skr 站点的 {@code /play/xx-1-1.html}）原先原样输出，
     * 播放器无法加载，现统一补全主机；magnet:/thunder:/ed2k: 等协议链接原样保留。</p>
     */
    private List<String> toEpisodeList(JSONArray episodes, String epiPrefix,
                                       String epiSuffix, List<String> epFilters) {
        List<String> epList = new ArrayList<>();
        if (episodes == null) return epList;
        for (int j = 0; j < episodes.length(); j++) {
            String ep = episodes.optString(j, "");
            if (ep.isEmpty()) continue;
            boolean blocked = false;
            for (String w : epFilters) {
                if (ep.contains(w)) { blocked = true; break; }
            }
            if (blocked) continue;
            epList.add(applyEpiAffix(absolveEpisodeLink(ep), epiPrefix, epiSuffix));
        }
        return epList;
    }

    /** 剧集条目 "标题$链接" 的链接部分补全主机（P2P 协议链接原样保留） */
    private String absolveEpisodeLink(String ep) {
        if (ep == null) return ep;
        int sep = ep.indexOf('$');
        if (sep < 0) return absolveEpisodeUrl(ep);
        return ep.substring(0, sep + 1) + absolveEpisodeUrl(ep.substring(sep + 1));
    }

    /** 带协议头的链接（magnet: / thunder: / ed2k: / ftp: 等）不参与主机补全 */
    private static final Pattern P_HAS_SCHEME = Pattern.compile("^[a-zA-Z][a-zA-Z0-9+.-]*:");

    private String absolveEpisodeUrl(String link) {
        if (link == null || link.isEmpty()) return link;
        if (link.startsWith("http://") || link.startsWith("https://")) return link;
        if (P_HAS_SCHEME.matcher(link).find()) return link;
        return absUrl(link);
    }

    /** 给剧集条目的链接部分加前后缀 */
    private static String applyEpiAffix(String ep, String prefix, String suffix) {
        if ((prefix == null || prefix.isEmpty()) && (suffix == null || suffix.isEmpty())) return ep;
        String p = prefix == null ? "" : prefix;
        String s = suffix == null ? "" : suffix;
        int sep = ep.indexOf('$');
        if (sep >= 0) {
            return ep.substring(0, sep + 1) + p + ep.substring(sep + 1) + s;
        }
        return p + ep + s;
    }

    /** 线路排序（[排序:xxx] 语法）+ 线路合并（merge_lines）+ 倒序（reverse） */
    private List<PlaySource> applyLineSortAndMerge(List<PlaySource> sources) {
        List<PlaySource> result = sources;

        // 倒序播放（写法说明：「倒序 "1"=倒序（集数/线路）」）：
        // 1) 每条线路的集数倒序；2) 未配置 [排序:] 时线路顺序也倒序。
        // [排序:xxx] 显式排序在下方执行、优先级更高，会覆盖线路顺序。
        // 常规 from_array 与 线路链接 from_url 两条提取路径都经此方法，倒序统一在此生效。
        if (reverse) {
            for (PlaySource src : result) {
                java.util.Collections.reverse(src.getEpisodes());
            }
            if (result.size() > 1 && !getVal("from_array").contains("[排序:")) {
                java.util.Collections.reverse(result);
            }
        }

        // 支持线路 sort 排序（借鉴 XYQHiker 的 [排序:] 语法）
        String fromArrayRule = getVal("from_array");
        if (fromArrayRule.contains("[排序:") && result.size() > 1) {
            try {
                String sortStr = fromArrayRule.split("\\[排序:")[1].split("\\]")[0];
                List<String> sortOrder = new ArrayList<>();
                for (String w : sortStr.split(",")) {
                    if (!w.trim().isEmpty()) sortOrder.add(w.trim());
                }
                if (!sortOrder.isEmpty()) {
                    List<PlaySource> sorted = new ArrayList<>();
                    for (String keyword : sortOrder) {
                        for (PlaySource src : result) {
                            if (src.getName().contains(keyword)) {
                                sorted.add(src);
                                break;
                            }
                        }
                    }
                    // 未在排序列表中的线路追加到末尾
                    for (PlaySource src : result) {
                        boolean found = false;
                        for (PlaySource s : sorted) {
                            if (s.getName().equals(src.getName())) { found = true; break; }
                        }
                        if (!found) sorted.add(src);
                    }
                    result = sorted;
                }
            } catch (Exception ignored) {
            }
        }

        if (mergeLines && result.size() > 1) {
            PlaySource merged = new PlaySource(result.get(0).getName());
            for (PlaySource source : result) {
                for (String ep : source.getEpisodes()) merged.addEpisode(ep);
            }
            result = new ArrayList<>();
            result.add(merged);
        }
        return result;
    }

    /**
     * 按 "线路链接"（from_url）提取线路：每个线路块指向一个独立子页面，
     * 需先抓取子页面再用 play_array/url_array 提取剧集。
     * <p>典型场景：低端影视的分页线路、4K影院 的分集线路。</p>
     */
    private List<PlaySource> extractPlaySourcesByLineUrl(String body, String fromArrayClean,
            String fromUrlRule, String epiPrefix, String epiSuffix, List<String> epFilters) {
        List<PlaySource> sources = new ArrayList<>();
        List<String> allEpisodes = new ArrayList<>();
        String firstTitle = "";

        try {
            String content = body;
            String lineTwice = getVal("line_twice");
            // 修复：CSS 形态的线路二次截取原先被静默忽略（仅非 CSS 走 applySecondCut），
            // 现统一分流：CSS 走 cutRegion，其余走字符串截取
            if (!lineTwice.isEmpty()) {
                if (CssRule.isCssRule(lineTwice)) {
                    String cut = CssRule.cutRegion(content, lineTwice);
                    if (!cut.isEmpty()) content = cut;
                } else {
                    content = StringCutRule.applySecondCut(content, lineTwice);
                }
            }
            String titleRule = getVal("from_title");
            // 修复：from_array 为 CSS 规则时 splitItems 会把选择器当前后缀截取串
            // 误处理，导致线路切分失败；CSS 规则改走 Jsoup 选择切块
            List<String> blocks;
            if (CssRule.isCssRule(fromArrayClean)) {
                blocks = new ArrayList<>();
                try {
                    org.jsoup.nodes.Document doc = org.jsoup.Jsoup.parse(content);
                    for (org.jsoup.nodes.Element el
                            : CssRule.selectWithAnd(doc, CssRule.stripPrefix(fromArrayClean))) {
                        blocks.add(el.outerHtml());
                    }
                } catch (Exception e2) {
                    SpiderDebug.log("线路数组 CSS 切分失败: " + e2.getMessage());
                }
            } else {
                blocks = RegexFieldHelper.splitItems(content, fromArrayClean);
            }

            for (int i = 0; i < blocks.size(); i++) {
                String block = blocks.get(i);
                String url = RegexFieldHelper.extract(block, fromUrlRule);
                if (url.isEmpty()) continue;
                // 多线链接前缀/后缀（使用说明 §4.7）：加在提取出的线路链接上
                String mlPrefix = getVal("multi_line_prefix");
                String mlSuffix = getVal("multi_line_suffix");
                if (!mlPrefix.isEmpty()) url = mlPrefix + url;
                if (!mlSuffix.isEmpty()) url = url + mlSuffix;
                url = absUrl(url);
                if (isSsrfBlocked(url)) {
                    SpiderDebug.log("线路链接 SSRF 拦截: " + url);
                    continue;
                }
                String sub = fetchUrl(url, buildHeaders("play_header"));
                if (sub.isEmpty()) continue;

                String title = titleRule.isEmpty() ? "" : RegexFieldHelper.extract(block, titleRule);
                if (title.isEmpty()) title = "线路" + (i + 1);
                if (firstTitle.isEmpty()) firstTitle = title;

                List<String> eps = extractEpisodesFromPage(sub, epiPrefix, epiSuffix, epFilters);
                if (eps.isEmpty()) continue;

                if (mergeLines) {
                    allEpisodes.addAll(eps);
                } else {
                    sources.add(new PlaySource(title, eps));
                }
            }
        } catch (Exception e) {
            SpiderDebug.log("线路链接模式提取失败: " + e.getMessage());
        }

        if (mergeLines && !allEpisodes.isEmpty()) {
            sources.clear();
            sources.add(new PlaySource(firstTitle.isEmpty() ? "播放" : firstTitle, allEpisodes));
        }
        return sources;
    }

    /**
     * 在单个页面内按单线模式提取剧集（复用播放列表提取器）。
     * <p>通过移除 from_array / from_title / line_twice / from_url，
     * 使提取器走单线分支，避免子页面内容被再次按线路切分。</p>
     */
    private List<String> extractEpisodesFromPage(String page, String epiPrefix,
            String epiSuffix, List<String> epFilters) throws Exception {
        List<String> out = new ArrayList<>();
        JSONObject subRule = new JSONObject(rule.toString());
        subRule.remove("from_array");
        subRule.remove("from_title");
        subRule.remove("line_twice");
        subRule.remove("from_url");

        JSONArray lines = ExtractorFactory.createPlayListExtractor(false).extract(page, subRule, 0);
        if (lines == null) return out;
        for (int i = 0; i < lines.length(); i++) {
            JSONObject line = lines.getJSONObject(i);
            out.addAll(toEpisodeList(line.optJSONArray("episodes"), epiPrefix, epiSuffix, epFilters));
        }
        return out;
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
                postBody = rest;
            } else {
                baseUrl = template;
            }

            // 搜索链接前后缀（借鉴 XYQHiker 第2867-2868行）
            // 注意：这两个值是【搜索结果详情页链接】的前后缀（如 前缀 "/voddetail/"、
            // 后缀 "-1-1.html"），不是搜索页面 URL 的前后缀，不能拼到下面的 url 上。
            String searchPrefix = getVal("search_prefix");
            String searchSuffix = getVal("search_suffix");

            // 修复：URLEncoder 把空格编码成 '+'，而 {wd} 常常落在 URL 路径段里
            //（如 /search/wd/{wd}/），'+' 在路径中不会被还原为空格，导致搜不到结果。
            // 统一换成 %20 —— 在查询串中 %20 与 '+' 等价，在路径中也正确。
            String encWd = URLEncoder.encode(keyword, "UTF-8").replace("+", "%20");
            // 表单体走标准 application/x-www-form-urlencoded，'+' 表示空格才是正确的
            String encWdForm = URLEncoder.encode(keyword, "UTF-8");

            baseUrl = expandVariables(baseUrl);
            // 修复：原实现只对 POST body 替换 {wd}/{pg}，';post' 之前的 URL 段完全不替换。
            // 而 XBPQ写法说明 给出的示例正是 "http://xxx.com/search/{wd};post"，
            // 请求地址里会残留字面量 "{wd}"，必然 404。
            // 变量展开必须在占位符替换之前：{{变量}} 可能展开出 {wd}/{pg} 模板。
            baseUrl = applySearchPlaceholders(baseUrl, encWd, pageStr);
            if (postBody != null) {
                postBody = applySearchPlaceholders(expandVariables(postBody), encWdForm, pageStr);
                // 修复：POST 表单体原先不做 时间戳/md5(...) 替换，
                // "wd={wd}&time=时间戳&sign=md5({wd})" 形态的签名接口会原样发出字面量
                postBody = applySearchTransforms(postBody);
            }

            baseUrl = applySearchTransforms(baseUrl);

            baseUrl = absUrl(baseUrl);

            // 注意：search_prefix / search_suffix 是【结果详情页链接】的前后缀，
            // 绝不能拼到搜索请求 URL 末尾（真实规则里形如 "搜索后缀":" /voddetail/"，
            // 拼上去会把请求地址变成 ".../search?wd=xx/voddetail/"，导致 30 余个站点搜索失败）。
            // 前后缀由结果解析阶段作用于每条结果的详情页链接。
            String body;
            if (isPost) {
                body = fetchPost(baseUrl, buildHeaders("search_header"), postBody);
            } else {
                body = fetchUrl(baseUrl, buildHeaders("search_header"));
            }
            if (body.isEmpty()) return "";

            // 搜索模式（XBPQ写法说明.json）："0"=JSON 搜索模式；其它=网页截取模式。
            // 两种模式均互为兜底，保证任一来源都能解析。
            boolean jsonMode = "0".equals(getVal("search_mode"));
            JSONArray videos = null;
            if (jsonMode) {
                // 优先按 JSON 解析（如 AJAX suggest 返回的 JSON）
                videos = parseJsonSearchArray(body, searchPrefix, searchSuffix);
            }
            if (videos == null || videos.length() == 0) {
                // 网页截取模式
                videos = ExtractorFactory
                        .createSearchExtractor(CssRule.isCssRule(getVal("search_array")))
                        .extract(body, rule);
            }
            if (videos == null || videos.length() == 0) {
                // 网页无结果时再回退 JSON 探测（非 JSON 模式的兜底路径）
                videos = parseJsonSearchArray(body, searchPrefix, searchSuffix);
            }
            if (videos == null) videos = new JSONArray();

            int pageCount = guessPageCount(body, videos.length(), pgNum);
            JSONObject result = new JSONObject();
            result.put("list", videos);
            // 分页四件套与 categoryContent 保持一致：全部数值型（Integer）
            result.put("page", pgNum);
            result.put("pagecount", pageCount);
            result.put("limit", PAGE_LIMIT);
            result.put("total", videos.length() > 0 ? pageCount * PAGE_LIMIT : 0);
            return applySearchPostProcess(result.toString(), keyword, quick);
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
     * 搜索请求的动态变换（借鉴 XYQHiker 第2764-2775行），GET URL 与 POST 表单体共用：
     * <ul>
     *   <li>{@code 时间戳} → Unix 秒级时间戳；{@code 时间标} → 毫秒级时间戳</li>
     *   <li>{@code md5(内容)} → 内容的 MD5 哈希（循环处理，嵌套括号不支持）</li>
     * </ul>
     */
    private static String applySearchTransforms(String text) {
        if (text == null || text.isEmpty()) return text;
        String unixTs = String.valueOf(System.currentTimeMillis() / 1000L);
        String milliTs = String.valueOf(System.currentTimeMillis());
        text = text.replace("时间戳", unixTs).replace("时间标", milliTs);
        int md5Idx;
        while ((md5Idx = text.indexOf("md5(")) >= 0) {
            int endParen = text.indexOf(")", md5Idx);
            if (endParen < 0) break;
            String md5Content = text.substring(md5Idx + 4, endParen);
            text = text.substring(0, md5Idx) + computeMd5(md5Content) + text.substring(endParen + 1);
        }
        return text;
    }

    /**
     * 替换搜索 URL / 表单体中的 {@code ${wd}} / <code>{wd}</code> / <code>{pg}</code> / <code>{page}</code> 占位符。
     * <p>先替换 {@code ${wd}} 再替换 <code>{wd}</code>，避免 "${wd}" 被先消费残留 "$" 前缀；
     * 页码支持 {@code {pg}} 与规格白名单里的 {@code {page}} 两种写法。</p>
     */
    private static String applySearchPlaceholders(String text, String encWd, String pageStr) {
        if (text == null || text.isEmpty()) return text;
        return text.replace("${wd}", encWd)
                .replace("{wd}", encWd)
                .replace("{pg}", pageStr)
                .replace("{page}", pageStr);
    }

    /**
     * 搜索结果后处理：链接补全 → 标题清洗 → 图片净化 → 去重 → 过滤词 → 倒序
     * → quick 精确匹配过滤（借鉴 Qimao 的 quick&&contains 思路）。
     * <p>去重 / 过滤词 / 倒序复用列表的通用后处理 {@link #applyCommonListPostProcess}，
     * 保证「过滤词」「倒序」在搜索页与分类页行为一致 —— 参考实现
     * （XBPQ优化前 第6609/6684行）在搜索结果处同样执行 reverseArray 与 shouldFilter。</p>
     * 解析失败时返回原串（避免吞掉错误）。
     */
    private String applySearchPostProcess(String jsonStr, String keyword, boolean quick) {
        if (jsonStr == null || jsonStr.isEmpty()) return jsonStr;
        try {
            JSONObject root = new JSONObject(jsonStr);
            JSONArray list = root.optJSONArray("list");
            if (list == null) return jsonStr;

            for (int i = 0; i < list.length(); i++) {
                JSONObject v = list.getJSONObject(i);
                // 详情页链接补全：搜索结果里的链接常是相对路径（如 /voddetail/123），
                // 不补全会导致点进详情时把相对路径当 tid 使用而抓不到内容。
                if (v.has("vod_id")) {
                    String id = v.optString("vod_id", "");
                    if (!id.isEmpty() && !id.startsWith("http")) {
                        String abs = absUrl(id);
                        if (!abs.isEmpty()) v.put("vod_id", abs);
                    }
                }
            }

            // 标题清洗 / 图片净化 / 去重 / 过滤词 / 倒序
            JSONArray processed = applyCommonListPostProcess(list);

            // quick 精确匹配：名称需包含关键字（借鉴 Qimao）
            String kw = keyword == null ? "" : keyword.trim().toLowerCase();
            JSONArray out = processed;
            if (quick && !kw.isEmpty()) {
                out = new JSONArray();
                for (int i = 0; i < processed.length(); i++) {
                    JSONObject v = processed.getJSONObject(i);
                    String name = v.optString("vod_name", "").trim().toLowerCase();
                    // 名称为空时回退比 vod_id，避免漏掉只有链接没有标题的结果
                    if (name.isEmpty()) name = v.optString("vod_id", "").trim().toLowerCase();
                    if (name.contains(kw)) out.put(v);
                }
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
                String resp = httpClient().string(url, h, body, timeout);
                // 与 doWithRetry 保持一致：空响应同样视为失败并重试，
                // 否则弱网/偶发空包会让搜索直接返回空结果
                if (resp != null && !resp.isEmpty()) return resp;
                lastError = new Exception("空响应");
            } catch (Exception e) {
                lastError = e;
                SpiderDebug.log("POST 请求第" + (attempt + 1) + "次失败: " + url + " " + e.getMessage());
            }
        }

        SpiderDebug.log("POST 请求最终失败（已重试" + maxRetries + "次）: " + url
                + (lastError != null ? " " + lastError.getMessage() : ""));
        return "";
    }

    /**
     * 泛化 JSON 搜索：递归查找含 vod_id+vod_name 的数组并映射标准字段。
     *
     * @return 视频数组；响应非 JSON / 未找到目标 / 结果为空时返回 {@code null}，交由调用方兜底
     */
    private JSONArray parseJsonSearchArray(String body, String linkPrefix, String linkSuffix) {
        if (body == null) return null;
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
                // 数组元素可能是标量/嵌套数组，强转会抛异常并中断整次解析
                Object elem = arr.opt(i);
                if (!(elem instanceof JSONObject)) continue;
                JSONObject item = (JSONObject) elem;
                JSONObject video = new JSONObject();
                // 字段别名回退（借鉴 HHkk/Gold 的 id→playlet_id 等思路）：
                // 非标 JSON 字段（如 id/name/pic）也能映射到标准 vod_* 字段
                String id = JsonParser.pickField(item, "vod_id");
                // 与网页截取模式保持一致：详情页链接需套用 搜索链接前缀/搜索链接后缀
                if (!id.isEmpty()) id = linkPrefix + id + linkSuffix;
                video.put("vod_id", id);
                video.put("vod_name", JsonParser.pickField(item, "vod_name"));
                String pic = JsonParser.pickField(item, "vod_pic");
                if (!pic.isEmpty()) video.put("vod_pic", pic);
                String remarks = JsonParser.pickField(item, "vod_remarks");
                if (!remarks.isEmpty()) video.put("vod_remarks", remarks);
                if (!video.optString("vod_id").isEmpty() || !video.optString("vod_name").isEmpty()) {
                    videos.put(video);
                }
            }
            return videos.length() == 0 ? null : videos;
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
                // 强制直接播放模式：拼接前缀后缀后直接输出。
                // 修复：force_play=2 的剧集来自详情提取器，而提取器已按规则拼过
                // play_prefix/play_suffix——此处再无条件拼接会造成双重前缀/后缀
                //（URL 损坏无法播放）。现做幂等检查：已带前缀/后缀则不重复拼。
                String prefix = getVal("play_prefix");
                String suffix = getVal("play_suffix");
                String finalUrl = url;
                if (!prefix.isEmpty() && !finalUrl.startsWith(prefix)) finalUrl = prefix + finalUrl;
                if (!suffix.isEmpty() && !finalUrl.endsWith(suffix)) finalUrl = finalUrl + suffix;
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
                    // 修复：link_next 是"下一集播放页链接"（如 /play/xxx-1-2.html），
                    // 原实现无条件用它覆盖已解出的视频地址 target，导致 Maccms
                    // 播放页（encrypt=1 的 player_aaaa）永远解析不出真实 m3u8，
                    // 播放退化为对播放页 URL 的嗅探。现只使用 mp.url。
                    target = mp.url;
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
                // 修复：正则要求 "};"/结尾分号且无法配对嵌套对象——
                // 热剧TV网等站点 player_aaaa 以 "}</script>" 结尾（无分号）且内含
                // vod_data 嵌套对象，正则路径必然失败。失败时回落括号配对扫描。
                MaccmsPlayer byScan = extractMaccmsPlayerByBraceScan(body);
                if (byScan != null) return byScan;
            } else {
                // Original indexOf-based traversal（括号配对，处理嵌套对象）
                MaccmsPlayer byScan = extractMaccmsPlayerByBraceScan(body);
                if (byScan != null) return byScan;
            }
        } catch (Exception e) {
            // ignore
        }
        return null;
    }

    /**
     * Maccms 播放脚本括号配对扫描（indexOf 路径独立入口）。
     * 供 regexMode 失败时回落使用（正则无法配对嵌套对象、且部分站点
     * 的 player_aaaa 以 "&lt;/script&gt;" 结尾无分号）。
     */
    private MaccmsPlayer extractMaccmsPlayerByBraceScan(String body) {
        try {
            int pos = 0;
            while (pos < body.length()) {
                int pi = body.indexOf("var player_", pos);
                if (pi < 0) return null;
                int braceStart = body.indexOf('{', pi);
                if (braceStart < 0) return null;
                int braceEnd = findMatchingBrace(body, braceStart);
                if (braceEnd < 0) return null;
                MaccmsPlayer mp = parseMaccmsPlayerJson(body.substring(braceStart, braceEnd + 1));
                if (mp != null && !mp.url.isEmpty()) return mp;
                pos = braceEnd + 1;
            }
        } catch (Exception ignored) {
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
        if (params == null || params.isEmpty()) return null;

        String danmuUrl = params.get("danmu_url");
        if (danmuUrl != null && !danmuUrl.isEmpty()) return proxyDanmu(danmuUrl);

        // PicNeedProxy=1 时 fixCover 会生成 proxy://do=XBPQ&site=..&pic=.. ，
        // 原先 proxy() 只处理 danmu_url，图片代理请求会落到 null（框架按失败处理），
        // 导致所有封面图加载不出来。此处补齐二进制回源分支。
        String pic = params.get("pic");
        if (pic != null && !pic.isEmpty()) {
            String site = params.get("site");
            return proxyBinary(pic, site, guessImageContentType(pic));
        }

        String m3u8 = params.get("m3u8");
        if (m3u8 != null && !m3u8.isEmpty()) {
            return proxyText(m3u8, "application/vnd.apple.mpegurl");
        }
        return null;
    }

    /** 弹幕回源（文本） */
    private Object[] proxyDanmu(String danmuUrl) throws Exception {
        fetchRule();
        // danmuUrl 在 appendDanmu 中经过 URLEncoder.encode，先还原，再对解码后的
        // 真实地址做 SSRF 校验，防止用编码形态的内网地址绕过校验
        String decodedUrl = decodeParam(danmuUrl);
        // proxy 入口由播放器/外部传入 URL，属不可信输入，强制 SSRF 防护（不受 allow_internal 影响）
        // 这里直接拦截内网地址，与后续 fetchUrl 的 isSsrfBlocked(读 allow_internal) 行为独立
        // 双重防护：即使 allow_internal=1 允许内网，proxy 入口仍禁止（防 SSRF 滥用）
        // 原始值与解码值逐一校验，防止用编码形态的内网地址绕过
        if (httpClient().isInternalUrl(decodedUrl) || httpClient().isInternalUrl(danmuUrl)) {
            SpiderDebug.log("proxy SSRF 拦截: " + decodedUrl);
            return textResponse(403, "forbidden");
        }
        // 防御：fetchUrl 返回空时（请求失败/重试耗尽）也应返回 502 错误流，避免返回空字符串
        String respBody = fetchUrl(decodedUrl, buildHeaders(null));
        if (respBody.isEmpty()) return textResponse(502, "upstream empty");
        return new Object[]{200, "application/octet-stream",
                new java.io.ByteArrayInputStream(respBody.getBytes("UTF-8"))};
    }

    /** 二进制回源（图片等）：按字节透传，避免 String 中转破坏二进制内容 */
    private Object[] proxyBinary(String encodedUrl, String referer, String contentType) throws Exception {
        fetchRule();
        String url = decodeParam(encodedUrl);
        if (httpClient().isInternalUrl(url)) {
            SpiderDebug.log("proxy SSRF 拦截: " + url);
            return textResponse(403, "forbidden");
        }
        Map<String, String> headers = buildHeaders(null);
        if (referer != null && !referer.isEmpty() && httpClient() != null) {
            if (headers == null) headers = new HashMap<>();
            headers.put("Referer", referer);
        }
        byte[] data = httpClient().bytes(url, headers);
        if (data == null || data.length == 0) return textResponse(502, "upstream empty");
        return new Object[]{200, contentType, new java.io.ByteArrayInputStream(data)};
    }

    /** 文本回源（M3U8 等） */
    private Object[] proxyText(String encodedUrl, String contentType) throws Exception {
        fetchRule();
        String url = decodeParam(encodedUrl);
        if (httpClient().isInternalUrl(url)) {
            SpiderDebug.log("proxy SSRF 拦截: " + url);
            return textResponse(403, "forbidden");
        }
        String body = fetchUrl(url, buildHeaders(null));
        if (body.isEmpty()) return textResponse(502, "upstream empty");
        return new Object[]{200, contentType, new java.io.ByteArrayInputStream(body.getBytes("UTF-8"))};
    }

    /**
     * 还原 proxy 参数中的 URL。
     * <p>不同宿主行为不一致：Android {@code Uri.getQueryParameter} 取值时会解码，
     * 部分宿主则原样传入。原先无条件 {@code URLDecoder.decode} 会造成二次解码，
     * 把 URL 中合法的 {@code %XX}（如中文路径）破坏掉。
     * 现仅在值仍呈百分号编码形态时才解码。</p>
     */
    private static String decodeParam(String value) {
        if (value == null) return "";
        if (looksPercentEncoded(value)) {
            try {
                String decoded = java.net.URLDecoder.decode(value, "UTF-8");
                if (!decoded.isEmpty()) return decoded;
            } catch (Exception ignored) {
            }
        }
        return value;
    }

    /** 是否呈百分号编码形态（存在 % + 两位十六进制） */
    private static boolean looksPercentEncoded(String s) {
        int idx = s.indexOf('%');
        while (idx >= 0 && idx + 2 < s.length()) {
            if (isHexChar(s.charAt(idx + 1)) && isHexChar(s.charAt(idx + 2))) return true;
            idx = s.indexOf('%', idx + 1);
        }
        return false;
    }

    private static boolean isHexChar(char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }

    private static Object[] textResponse(int code, String message) {
        try {
            return new Object[]{code, "text/plain; charset=utf-8",
                    new java.io.ByteArrayInputStream(message.getBytes("UTF-8"))};
        } catch (Exception e) {
            return new Object[]{code, "text/plain; charset=utf-8",
                    new java.io.ByteArrayInputStream(new byte[0])};
        }
    }

    /** 按扩展名猜测图片 Content-Type，无法识别时回退通用二进制流 */
    private static String guessImageContentType(String url) {
        String lower = url == null ? "" : url.toLowerCase();
        int q = lower.indexOf('?');
        if (q > 0) lower = lower.substring(0, q);
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".bmp")) return "image/bmp";
        if (lower.endsWith(".svg")) return "image/svg+xml";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        return "application/octet-stream";
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
