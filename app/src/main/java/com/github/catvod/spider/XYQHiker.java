package com.github.catvod.spider;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.util.Base64;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Filter;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.bean.xyqbiu.ParseUtils;
import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;
import com.github.catvod.spider.Init;
import com.github.catvod.utils.Notify;
import com.github.catvod.spider.Proxy;
import com.github.catvod.spider.PushAgent;
import com.google.gson.Gson;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Headers;
import okhttp3.Response;

/**
 * XYQHiker 影视源 Spider（XBPQ/XYQHiker 系列，XYQBiu 的后继演进版）。
 * <p>从 {@code XYQHiker.smali} 还原而来。{@code merge/xyq0208/*} 辅助类映射为标准实现：
 * <ul>
 *   <li>{@code merge/xyq0208/ĺ} → {@link org.jsoup.nodes.Element}</li>
 *   <li>{@code merge/xyq0208/ำ} → {@link org.jsoup.select.Elements}</li>
 *   <li>{@code merge/xyq0208/ތ} → OkHttp 包装</li>
 *   <li>{@code merge/xyq0208/ވ} → {@link ParseUtils}（regexExtract / urlCombine / md5Hex / escapeRegex）</li>
 * </ul>
 * 使用项目标准 Bean（{@link Vod} / {@link Result} / {@link Filter}）替代 JSONObject/JSONArray。</p>
 *
 * <p><b>注意：所有 {@code @Override} 方法均声明 {@code throws Exception}，与 Spider 基类签名一致。</b></p>
 */
public class XYQHiker extends Spider {

    // ==================== 静态字段 ====================

    /** SharedPreferences 引用（init 时初始化） */
    private static SharedPreferences prefs;

    /** 阿里盘详情解析标志（原 smali 为 static，忠实保留） */
    private static boolean aliyunFlag;

    /** 阿里盘分享链接匹配模式 */
    public static final Pattern ALIYUN_PATTERN = Pattern.compile("(https://www.(alipan|aliyundrive).com/s[^\"]+)");

    /** PC UA - Edge 浏览器 */
    public static String PC_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/110.0.0.0 Safari/537.36 Edg/110.0.1587.57";

    /** Mobile UA - 小米 13 Pro */
    public static String MOBILE_UA = "Mozilla/5.0 (Linux; Android 13; Xiaomi 13 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/113.0.5672.131 Mobile Safari/537.36";

    /** iOS UA - iPhone Safari */
    public static String IOS_UA = "Mozilla/5.0 (iPhone; CPU iPhone OS 16_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.0 Mobile/15E148 Safari/604.1";

    /** Mac UA - Safari */
    public static String MAC_UA = "Mozilla/5.0 (Macintosh; Intel Mac OS X 20_40; rv:100.0) AppleWebKit/537.75.14 (KHTML, like Gecko) Version/15.0.0 Safari/1500";

    /** 验证路径数组（用于 checkveriry / vertype） */
    private static final String[] VERIFY_PATHS = {"ajax/verify_check", "ajax.php?ac=code_check", "/verify/index.html", "?scheckAC=check"};

    /** 元素属性键数组（href/src/class/title/alt，用于 getText 等提取） */
    private static final String[] ATTR_KEYS = {"href", "src", "class", "title", "alt"};

    /** \\uXXXX 转义序列匹配模式（decodeHexChars 使用） */
    private static final Pattern UNICODE_HEX_PATTERN = Pattern.compile("(\\\\u(\\w{4}))");

    /** 占位符匹配模式 {xxx} */
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{(.*?)\\}");

    /** ed2k 链接文件名提取模式 */
    private static final Pattern ED2K_PATTERN = Pattern.compile("\\|file\\|(.*?)\\|");

    /** 磁力链接 dn 参数提取模式 */
    private static final Pattern MAGNET_PATTERN = Pattern.compile("(^|&)dn=([^&]*)(&|$)");

    // ==================== 实例字段 ====================

    /** PushAgent 实例（阿里云盘推送代理） */
    public PushAgent pushAgent;

    /** 累加 Cookie（验证码刷新后写入） */
    private String accCookie = "";

    /** Cookie 字段 */
    private String cookie = "";

    /** 编码字段 */
    private String charset = "";

    /** Referer 字段 */
    private String referer = "";

    /** 运行时请求头配置 JSON（@Headers 包装用） */
    private JSONObject configJson = new JSONObject();

    /** MacPlayerConfig 抓取正则 */
    private String macPlayerRegex = "[\\W|\\S|.]*?MacPlayerConfig.player_list[\\W|\\S|.]*?=([\\W|\\S|.]*?),MacPlayerConfig.downer_list";

    /** 站点 extend 配置原文（init 时赋值） */
    protected String extend;

    /** 站点配置 JSON（ensureSiteConfig 解析 extend 得到） */
    protected JSONObject siteConfig;

    /** OCR 识别 API 地址（配置键 OCR_API，默认 ddddocr） */
    protected String ocrApi;

    /** 调试标志（配置键 DEBUG，值为 "是" 或 "1" 时启用） */
    protected boolean debugFlag;

    // ========================================================================
    // 生命周期方法
    // ========================================================================

    /**
     * 初始化 Spider。
     * <p>解析 extend 配置，初始化 PushAgent（阿里云盘）。若 SharedPreferences 中无
     * PublicRefreshToken，则尝试从本地代理文件读取 alitoken.txt。</p>
     *
     * @param ctx    上下文
     * @param extend 站点扩展配置（JSON 字符串或 URL）
     */
    @Override
    public void init(Context ctx, String extend) throws Exception {
        super.init(ctx, extend);
        this.extend = extend;
        this.pushAgent = new PushAgent();
        String prefsName = Init.context().getPackageName() + "_preferences";
        prefs = ctx.getSharedPreferences(prefsName, 0);
        String token = prefs.getString("PublicRefreshToken", "");
        if (token.isEmpty()) {
            try {
                String proxyUrl = Proxy.getUrl().replace("/proxy", "/file/XYQTVBox/alitoken.txt");
                String fetched = OkHttp.string(proxyUrl, null).trim();
                if (fetched.length() == 32 && !fetched.isEmpty()) {
                    token = fetched;
                }
            } catch (Exception e) {
                SpiderDebug.log(e);
            }
        }
        this.pushAgent.init(ctx, token);
    }

    // ========================================================================
    // 工具方法
    // ========================================================================

    /**
     * 检查 URL 是否包含已知的伪造/重定向标记。
     *
     * @param input 待检查的 URL
     * @return 含标记返回 true，否则 false
     */
    public static boolean checkstring(String input) {
        String[] patterns = new String[]{
                "m3u8.pw/Cache",
                "from=https://banyung.pw",
                "getm3u8?url=http"
        };
        for (int i = 0; i < patterns.length; i++) {
            if (input.contains(patterns[i])) {
                return true;
            }
        }
        return false;
    }

    /**
     * 检查内容是否包含验证码路径标记。
     *
     * @param input 待检查的 HTML 内容
     * @return 含验证路径返回 true，否则 false
     */
    public static boolean checkveriry(String input) {
        for (String path : VERIFY_PATHS) {
            if (input.contains(path)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 返回内容中匹配的验证路径。
     *
     * @param input 待检查的 HTML 内容
     * @return 匹配的验证路径，无匹配返回空串
     */
    public static String vertype(String input) {
        for (String path : VERIFY_PATHS) {
            if (input.contains(path)) {
                return path;
            }
        }
        return "";
    }

    /**
     * 将字符串列表拼接为单个字符串。
     *
     * @param list 字符串列表
     * @param sep 分隔符
     * @return 拼接结果，空列表返回空串
     */
    public static String listToString(List<String> list, String sep) {
        StringBuilder sb = new StringBuilder();
        if (list == null || list.size() <= 0) {
            return "";
        }
        if (list.size() <= 1) {
            return list.get(0);
        }
        sb.append(list.get(0));
        for (int i = 1; i < list.size(); i++) {
            sb.append(sep);
            sb.append(list.get(i));
        }
        return sb.toString();
    }

    /**
     * 按规则从 Jsoup Element 提取文本。
     * <p>规则格式: [mode][!remove1!remove2...]
     * <ul>
     *   <li>先按 "\\.js:" 分割，若多段则取第一段</li>
     *   <li>再按 "!" 分割，首段为模式，其余为待删除子串</li>
     *   <li>模式: Text→text(), B64Dec→Base64 解码 text(), Html→html(),
     *       含 "Attr"→attr(去 Attr 后的 key), 默认→attr(key)</li>
     *   <li>提取后: 若规则整体不为 "Html" 则将 \n 替换为空格，
     *       再循环删除所有 "!" 分隔的后续段</li>
     * </ul>
     * 异常时记录日志并返回 null。</p>
     *
     * @param element Jsoup 元素
     * @param rule    提取规则
     * @return 提取的文本，异常返回 null
     */
    private static String extractByMode(Element element, String rule) {
        if (element == null || rule == null || rule.isEmpty()) {
            return null;
        }
        try {
            String[] jsParts = rule.split("\\.js:");
            if (jsParts.length > 1) {
                rule = jsParts[0];
            }
            String[] parts = rule.split("!");
            String space = " ";
            String newline = "\n";
            String b64Dec = "B64Dec";
            String text = "Text";
            String empty = "";
            String attr = "Attr";
            String html = "Html";
            String result;
            if (parts.length > 1) {
                String mode = parts[0];
                if (mode.equals(text)) {
                    result = element.text();
                } else if (b64Dec.equals(mode)) {
                    result = new String(Base64.decode(element.text(), Base64.DEFAULT));
                } else if (html.equals(mode)) {
                    result = element.html();
                } else if (mode.contains(attr)) {
                    result = element.attr(mode.replace(attr, empty));
                } else {
                    result = element.attr(parts[0]);
                }
                if (!html.equals(rule)) {
                    result = result.replaceAll(newline, space);
                }
                for (int i = 1; i < parts.length; i++) {
                    result = result.replace(parts[i], empty);
                }
            } else {
                if (rule.equals(text)) {
                    result = element.text();
                } else if (b64Dec.equals(rule)) {
                    result = new String(Base64.decode(element.text(), Base64.DEFAULT));
                } else if (html.equals(rule)) {
                    result = element.html();
                } else if (rule.contains(attr)) {
                    result = element.attr(rule.replace(attr, empty));
                } else {
                    result = element.attr(rule);
                }
                if (!html.equals(rule)) {
                    result = result.replaceAll(newline, space);
                }
            }
            return result;
        } catch (Exception e) {
            SpiderDebug.log(e);
            return null;
        }
    }

    /**
     * 按规则从 Element 提取文本，支持 "||" 回退。
     * <p>"*" 规则返回 "null"。规则含 "||" 时按顺序尝试各段，返回首个非空结果。</p>
     *
     * @param element Jsoup 元素
     * @param rule    提取规则
     * @return 提取的文本
     */
    public static String getText(Element element, String rule) {
        if ("*".equals(rule)) {
            return "null";
        }
        String[] parts = rule.split("\\|\\|");
        if (parts.length > 1) {
            for (int i = 0; i < parts.length; i++) {
                String result = null;
                try {
                    result = extractByMode(element, parts[i]);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                if (!TextUtils.isEmpty(result)) {
                    return result;
                }
            }
        }
        return extractByMode(element, rule);
    }

    /**
     * 按 "&&" 导航并提取文本。
     * <p>规则按 "&&" 分割：除最后一段外都是 getTrueElement 导航步骤，
     * 最后一段通过 getText 在导航后的元素上提取。</p>
     *
     * @param element 起始元素
     * @param rule    "&&" 分隔的规则
     * @return 提取的文本
     */
    private static String navigateAndExtract(Element element, String rule) {
        String[] parts = rule.split("&&");
        if (parts.length != 1) {
            element = getTrueElement(parts[0], element);
            if (element == null) {
                return "";
            }
        }
        for (int i = 1; i < parts.length - 1; i++) {
            element = getTrueElement(parts[i], element);
            if (element == null) {
                return "";
            }
        }
        return getText(element, parts[parts.length - 1]);
    }

    /**
     * 解析规则分段并拼接结果。
     * <p>按 sep 分割规则；引号段（'...' / "..."）按字面量取值（\n 转义还原），
     * 其他段通过 navigateAndExtract 解析。结果无分隔符拼接。</p>
     *
     * @param element 起始元素
     * @param rule    规则字符串
     * @param sep     分段分隔符
     * @return 拼接后的文本
     */
    private static String resolveRuleSegments(Element element, String rule, String sep) {
        String[] parts = rule.split(sep);
        List<String> list = new ArrayList<>();
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i].trim();
            if ((part.startsWith("'") && part.endsWith("'")) || (part.startsWith("\"") && part.endsWith("\""))) {
                part = part.substring(1, part.length() - 1).replace("\\n", "\n");
                list.add(part);
            } else {
                list.add(navigateAndExtract(element, part));
            }
        }
        return listToString(list, "");
    }

    /**
     * 按规则从 Element 提取文本（支持 "+" / "＋" 分段拼接）。
     *
     * @param element Jsoup 元素
     * @param rule    提取规则
     * @return 提取的文本，空规则返回 ""
     */
    public static String getTextByRule(Element element, String rule) {
        if (rule == null || rule.length() == 0 || "*".equals(rule)) {
            return "";
        }
        if (rule.contains(".js:") || rule.contains("＋")) {
            return resolveRuleSegments(element, rule, "＋");
        }
        return resolveRuleSegments(element, rule, "\\+");
    }

    /**
     * 按规则导航到真实元素。
     * <p>支持 "--" 链式提取、"||" 回退、",index" 索引选择、默认 select。</p>
     *
     * @param rule    导航规则
     * @param element 起始元素
     * @return 导航后的元素
     */
    public static Element getTrueElement(String rule, Element element) {
        if (element == null) {
            return null;
        }
        if (rule.startsWith("Text") || rule.startsWith("Attr")) {
            return element;
        }
        for (String key : ATTR_KEYS) {
            if (key.equals(rule)) {
                return element;
            }
        }
        // "--" chained extraction: each subsequent element's outerHtml is removed from the previous text
        String[] dashParts = rule.split("--");
        if (dashParts.length > 1) {
            Element current = getTrueElement(dashParts[0], element);
            if (current == null) {
                return null;
            }
            String text = current.outerHtml();
            for (int i = 1; i < dashParts.length; i++) {
                Element next = getTrueElement(dashParts[i], current);
                if (next == null) {
                    break;
                }
                String nextText = next.outerHtml();
                text = text.replace(nextText, "");
                current = Jsoup.parse(text);
            }
            return current;
        }
        // "||" fallback: return first non-null result among alternatives
        String[] orParts = rule.split("\\|\\|");
        if (orParts.length > 1) {
            for (int i = 0; i < orParts.length; i++) {
                Element result = null;
                try {
                    result = getTrueElement(orParts[i], element);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                if (result != null) {
                    return result;
                }
            }
        }
        // ",index" select by index (negative = from end)
        String[] commaParts = rule.split(",");
        if (commaParts.length > 1) {
            int idx = Integer.parseInt(commaParts[1]);
            Elements selected = element.select(commaParts[0]);
            if (selected.isEmpty()) return null;
            if (idx < 0) {
                int actualIdx = selected.size() + idx;
                if (actualIdx < 0) return null;
                return selected.get(actualIdx);
            }
            if (idx >= selected.size()) return null;
            return selected.get(idx);
        }
        // default: select first matching element
        Element first = null;
        try {
            first = element.select(rule).first();
        } catch (Exception e) {
            // 无效 CSS 选择器（如 "-p"），返回 null 让上层降级处理
        }
        if (first != null) {
            return first;
        }
        // Fallback: if no descendant matches, check if the element itself matches.
        // This handles cases like rule="a" applied to an <a> element (when the
        // array rule already selected the target elements directly).
        if (isSelfMatch(element, rule)) {
            return element;
        }
        return null;
    }

    /**
     * 判断元素自身是否匹配 CSS 选择器（仅支持常见简单选择器：标签名、.class、#id）。
     * <p>Jsoup 的 {@code element.select(rule)} 只搜索后代元素，不包含元素自身。
     * 本方法用于在后代搜索失败时，检查元素自身是否匹配，以支持
     * "选集列表数组规则=a" + "选集标题=a&&Text" 这类规则嵌套场景。</p>
     *
     * @param element 待检查的元素
     * @param rule    CSS 选择器字符串
     * @return true 表示元素自身匹配该选择器
     */
    private static boolean isSelfMatch(Element element, String rule) {
        if (rule == null || rule.isEmpty() || element == null) {
            return false;
        }
        try {
            String trimmed = rule.trim();
            // 标签名匹配：rule = "a", "li", "div" 等
            if (trimmed.equals(element.nodeName())) {
                return true;
            }
            // class 选择器：rule = ".v_list", ".play_list" 等
            if (trimmed.startsWith(".") && trimmed.length() > 1) {
                return element.hasClass(trimmed.substring(1));
            }
            // id 选择器：rule = "#play_list_sort" 等
            if (trimmed.startsWith("#") && trimmed.length() > 1) {
                return trimmed.substring(1).equals(element.id());
            }
        } catch (Exception e) {
            // ignore
        }
        return false;
    }

    /**
     * 元素选择器（支持范围切片）。
     * <p>支持两种选择模式：</p>
     * <ul>
     *   <li><b>简单模式</b>：rule 不含逗号，直接调用 {@code element.select(rule)}</li>
     *   <li><b>范围模式</b>：rule 格式为 {@code "cssSelector,start:end"}，
     *       先用 cssSelector 选择全部元素，再取索引 [start, end) 范围内的子集。
     *       end 为负数时从末尾计算。end 超出 size 时截断为 size。</li>
     * </ul>
     *
     * @param element 源元素
     * @param rule    选择规则字符串
     * @return 选择的元素集合
     */
    private static Elements selectByRule(Element element, String rule) {
        String[] parts = rule.split(",");
        if (parts.length > 1) {
            // 范围模式：parts[0] = CSS 选择器，parts[1] = "start:end"
            String[] rangeParts = parts[1].split(":", -1);
            int start = 0;
            if (!TextUtils.isEmpty(rangeParts[0])) {
                try {
                    start = Integer.parseInt(rangeParts[0]);
                } catch (NumberFormatException e) {
                    e.printStackTrace();
                }
            }
            int end = 0;
            if (!TextUtils.isEmpty(rangeParts[1])) {
                try {
                    end = Integer.parseInt(rangeParts[1]);
                } catch (NumberFormatException e) {
                    e.printStackTrace();
                }
            }
            Elements selected = element.select(parts[0]);
            int size = selected.size();
            if (size == 0) return new Elements();
            if (start < 0) {
                start = Math.max(0, size + start);
            }
            if (start >= size) return new Elements();
            if (end > size) {
                end = size;
            }
            if (end <= 0) {
                end = end + size;
            }
            if (end <= start) return new Elements();
            Elements result = new Elements();
            for (int i = start; i < end; i++) {
                result.add(selected.get(i));
            }
            return result;
        }
        // 简单模式：直接选择
        return element.select(rule);
    }

    /**
     * 按规则选择元素列表，支持 "||" 多规则合并。
     *
     * @param element 源元素
     * @param rule    选择规则（可含 "||" 分隔多个规则）
     * @return 合并后的元素集合
     */
    public static Elements selectElements(Element element, String rule) {
        String[] parts = rule.split("\\|\\|");
        Elements result = new Elements();
        for (int i = 0; i < parts.length; i++) {
            try {
                result.addAll(selectByRule(element, parts[i]));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return result;
    }

    /**
     * 将字符串中每个字符转换为 ASCII 码（或 ASCII+1）拼接。
     *
     * @param input 输入字符串
     * @param mode  "djs" → 原始 ASCII；其他 → ASCII+1
     * @return 转换后的数字字符串
     */
    public static String string2Hex(String input, String mode) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i <= input.length() - 1; i++) {
            String codeStr = String.valueOf((int) input.charAt(i));
            if (mode.equals("djs")) {
                sb.append(codeStr);
            } else {
                sb.append(Integer.parseInt(codeStr) + 1);
            }
        }
        return sb.toString();
    }

    /**
     * 解码 \\uXXXX 转义序列为字面字符。
     *
     * @param text 含 \\uXXXX 转义的文本
     * @return 解码后的文本
     */
    private static String decodeHexChars(String text) {
        Matcher matcher = UNICODE_HEX_PATTERN.matcher(text);
        while (matcher.find()) {
            String fullMatch = matcher.group(1);
            String hex = matcher.group(2);
            char ch = (char) Integer.parseInt(hex, 16);
            text = text.replace(fullMatch, String.valueOf(ch));
        }
        return text;
    }

    /**
     * 构建降序年份范围字符串（当前年份至当前年份-20，'&' 分隔）。
     *
     * @return 年份范围字符串，如 "2026&2025&...&2006"
     */
    private static String buildYearRange() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy");
        int currentYear = Integer.parseInt(sdf.format(new Date()));
        int endYear = currentYear - 20;
        StringBuilder sb = new StringBuilder();
        for (int y = currentYear; y >= endYear; y--) {
            if (sb.length() > 0) {
                sb.append("&");
            }
            sb.append(String.valueOf(y));
        }
        return sb.toString();
    }

    // ========================================================================
    // HTTP 方法
    // ========================================================================

    /**
     * GET 请求并捕获响应头。
     * <p>返回响应体字符串，将响应头填入 responseHeaders。</p>
     *
     * @param url             请求 URL
     * @param headers         请求头
     * @param responseHeaders 响应头输出 Map
     * @return 响应体字符串，异常返回 null
     */
    private static String fetchWithHeaders(String url, Map<String, String> headers, Map<String, List<String>> responseHeaders) {
        Response response = null;
        try {
            response = OkHttp.newCall(url, headers);
            Headers respHeaders = response.headers();
            for (String name : respHeaders.names()) {
                responseHeaders.put(name, respHeaders.values(name));
            }
            return response.body().string();
        } catch (Exception e) {
            SpiderDebug.log(e);
            return null;
        } finally {
            if (response != null) response.close();
        }
    }

    /**
     * GET 请求获取内容。
     * <p>clan:// URL 会重写为本地代理 /file/ 端点。响应体 \r|\n 被去除。</p>
     *
     * @param url     请求 URL
     * @param charset 字符编码
     * @param headers 请求头
     * @return 响应内容字符串
     */
    protected String fetchGet(String url, String charset, Map<String, String> headers) {
        try {
            SpiderDebug.log(url);
            if (url.startsWith("clan://")) {
                String proxyBase = Proxy.getUrl().replace("/proxy", "/file/");
                url = url.replace("clan://", proxyBase);
                return OkHttp.string(url, null);
            }
            return new String(OkHttp.bytes(url, headers, 30000), charset).replaceAll("\r|\n", "");
        } catch (Throwable e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * POST 表单请求。
     * <p>响应体 \r|\n 被去除。charset 参数保留用于签名一致性。</p>
     *
     * @param url     请求 URL
     * @param params  表单参数
     * @param charset 字符编码（保留签名）
     * @param headers 请求头
     * @return 响应内容字符串
     */
    protected String fetchPostForm(String url, Map<String, String> params, String charset, Map<String, String> headers) {
        try {
            SpiderDebug.log(url);
            String resp;
            if (charset != null && !charset.equalsIgnoreCase("UTF-8") && !charset.equalsIgnoreCase("UTF8")
                    && params != null && !params.isEmpty()) {
                // 非 UTF-8 编码（如 gb2312/gbk）：手动构造 URL-encoded body，
                // 因为 OkHttp FormBody.Builder 默认用 UTF-8，会导致中文关键词乱码
                StringBuilder body = new StringBuilder();
                for (Map.Entry<String, String> entry : params.entrySet()) {
                    if (body.length() > 0) body.append("&");
                    body.append(URLEncoder.encode(entry.getKey(), charset));
                    body.append("=");
                    body.append(URLEncoder.encode(entry.getValue(), charset));
                }
                // Content-Type 不带 charset 参数（与浏览器行为一致，服务器根据页面编码解码 body）
                okhttp3.MediaType mediaType = okhttp3.MediaType.parse("application/x-www-form-urlencoded");
                okhttp3.RequestBody requestBody = okhttp3.RequestBody.create(mediaType, body.toString());
                okhttp3.Request.Builder builder = new okhttp3.Request.Builder().url(url);
                if (headers != null) {
                    for (String key : headers.keySet()) {
                        // 跳过已有的 Content-Type，用 RequestBody 的 MediaType 代替
                        if (!key.equalsIgnoreCase("Content-Type")) {
                            builder.addHeader(key, headers.get(key));
                        }
                    }
                }
                builder.post(requestBody);
                try (okhttp3.Response response = OkHttp.client().newCall(builder.build()).execute()) {
                    byte[] bytes = response.body().bytes();
                    resp = new String(bytes, charset);
                    SpiderDebug.log("fetchPostForm response code=" + response.code() + " length=" + resp.length());
                }
            } else {
                resp = OkHttp.post(url, params, headers);
            }
            return resp.replaceAll("\r|\n", "");
        } catch (Throwable e) {
            e.printStackTrace();
            SpiderDebug.log("fetchPostForm error: " + e.toString());
            return null;
        }
    }

    /**
     * POST JSON 请求。
     * <p>响应体 \r|\n 被去除。charset 参数保留用于签名一致性。</p>
     *
     * @param url      请求 URL
     * @param jsonBody JSON 请求体
     * @param charset  字符编码（保留签名）
     * @param headers  请求头
     * @return 响应内容字符串
     */
    protected String fetchPostJson(String url, String jsonBody, String charset, Map<String, String> headers) {
        try {
            SpiderDebug.log(url);
            String resp = OkHttp.post(url, jsonBody, headers);
            return resp.replaceAll("\r|\n", "");
        } catch (Throwable e) {
            e.printStackTrace();
            return null;
        }
    }

    // ========================================================================
    // 验证处理方法
    // ========================================================================

    /**
     * 处理 /huadong_ 和 /renji_ 验证页的响应后处理。
     * <p>若 HTML 含上述标记，则解析验证脚本，计算 verify token
     * (key + md5(string2Hex(value, mode)))，通过 GET 提交验证，保存 cookie，
     * 然后重新请求原始 URL。返回清理后的响应 (\r|\n 去除) 或 3 次重试后 null。</p>
     *
     * @param url  原始请求 URL
     * @param html 响应 HTML
     * @param mode "show" 或其他（决定请求头构建方式）
     * @return 清理后的响应，或 null
     */
    private String handleSniffVerify(String url, String html, String mode) {
        HashMap<String, String> headers;
        if (mode.equals("show")) {
            headers = buildShowHeaders(url);
        } else {
            headers = buildPostHeaders(url);
        }
        String empty = "";
        String newlineRegex = "\r|\n";
        String verifyTitle = "验证</title>";
        String renjiMarker = "/renji_";
        for (int retry = 0; retry < 3; retry++) {
            String response;
            if (html.contains("/huadong_") || html.contains(renjiMarker)) {
                // 解析 HTML, 提取验证脚本
                Document doc = Jsoup.parse(html);
                String scriptSrc = getTextByRule(doc, "body&&script&&src");
                String scriptUrl = ParseUtils.urlCombine(url, scriptSrc);
                String scriptContent = fetchGet(scriptUrl, charset, headers);
                // 提取验证参数
                String key = ParseUtils.regexExtract(scriptContent, "key=\"", "\"").get(0);
                String value = ParseUtils.regexExtract(scriptContent, "value=\"", "\"").get(0);
                String a20be899val = ParseUtils.regexExtract(scriptContent, "c.get(\"/a20be899", "\"").get(0);
                // 构建 verify 路径: /a20be899{a20be899val}{key}&value={md5}
                StringBuilder sb = new StringBuilder();
                sb.append("/a20be899");
                sb.append(a20be899val);
                sb.append(key);
                sb.append("&value=");
                String hexMode = html.contains(renjiMarker) ? "djs" : "hd";
                String hexValue = string2Hex(value, hexMode);
                String md5Value = ParseUtils.md5Hex(hexValue);
                sb.append(md5Value);
                String verifyUrl = ParseUtils.urlCombine(url, sb.toString());
                // 提交验证 (GET, 捕获响应头)
                Map<String, List<String>> respHeaders = new HashMap<>();
                fetchWithHeaders(verifyUrl, headers, respHeaders);
                // 提取 set-cookie
                for (Map.Entry<String, List<String>> entry : respHeaders.entrySet()) {
                    if (entry.getKey().equalsIgnoreCase("set-cookie")) {
                        accCookie = TextUtils.join(";", entry.getValue());
                    }
                }
                // 重新请求原始 URL
                response = fetchGet(url, charset, headers);
            } else {
                response = html;
            }
            // 共享检查: 若不含验证标题则返回, 否则重试
            if (!response.contains(verifyTitle)) {
                return response.replaceAll(newlineRegex, empty);
            }
        }
        return null;
    }

    /**
     * 处理 btwaf (宝塔 WAF) 检测页的响应后处理。
     * <p>若响应含 "检测中" 和 "btwaf"，则提取 btwaf token，带 token 重新请求，
     * 保存 cookie，必要时回退到 GET。返回清理后的响应 (\r|\n 去除) 或 3 次重试后 null。</p>
     *
     * @param url  原始请求 URL
     * @param body 响应内容
     * @param mode "show" 或其他（决定请求头构建方式）
     * @return 清理后的响应，或 null
     */
    private String handleBtwafVerify(String url, String body, String mode) {
        HashMap<String, String> headers;
        if (mode.equals("show")) {
            headers = buildShowHeaders(url);
        } else {
            headers = buildPostHeaders(url);
        }
        String empty = "";
        String newlineRegex = "\r|\n";
        String detectTitle = "<title>检测中</title>";
        String response = body;
        for (int retry = 0; retry < 3; retry++) {
            if (response.contains("检测中") && response.contains("btwaf")) {
                // 提取 btwaf token
                String btwafValue = ParseUtils.regexExtract(response, "btwaf=", "\"").get(0);
                // 构建 btwaf URL
                String btwafUrl = new StringBuilder()
                        .append(url)
                        .append(url.contains("?") ? "&btwaf=" : "?btwaf=")
                        .append(btwafValue)
                        .toString();
                // GET 请求 (捕获响应头)
                Map<String, List<String>> respHeaders = new HashMap<>();
                response = fetchWithHeaders(btwafUrl, headers, respHeaders);
                // 提取 set-cookie
                for (Map.Entry<String, List<String>> entry : respHeaders.entrySet()) {
                    if (entry.getKey().equalsIgnoreCase("set-cookie")) {
                        accCookie = TextUtils.join(";", entry.getValue());
                    }
                }
                // 若仍检测中, 回退到 GET
                if (response.contains(detectTitle)) {
                    response = fetchGet(url, charset, headers);
                }
            }
            // 检查是否仍含检测页标题
            if (!response.contains(detectTitle)) {
                return response.replaceAll(newlineRegex, empty);
            }
        }
        return null;
    }

    /**
     * 验证码/人机验证处理：获取验证码图片 → OCR 识别 → 提交验证 → 重试。
     * <p>'body' 参数实为 mode ("show"/其他)，'method' 为验证类型路径
     * (含 "/verify" / "scheckAC" / 其他)，'headers' 为 POST 表单参数 Map
     * (非 null 时用 POST 重新请求，null 时用 GET)。</p>
     * <p>验证类型分流：
     * <ul>
     *   <li>/verify: 验证码图片 /index.php/verify/index.html, 提交 verify_check</li>
     *   <li>scheckAC: 验证码图片 /include/vdimgck.php, 提交 search.php?scheckAC=check</li>
     *   <li>其他: 验证码图片 /inc/common/code.php?a={method}&s=, 提交 code_check</li>
     * </ul>
     * OCR 结果通过 fetchPostJson 发送到 ocrApi。最多重试 4 次。
     * 成功返回结果，失败返回最近一次响应（初始为 ""）。</p>
     *
     * @param url     原始请求 URL
     * @param headers POST 表单参数 Map（null 时用 GET 重新请求）
     * @param body    实为 mode（"show"/其他）
     * @param method  验证类型路径
     * @return 验证后的响应内容
     */
    private String handleCaptchaVerify(String url, Map<String, String> headers, String body, String method) {
        // 构建请求头 (含 X-Requested-With)
        HashMap<String, String> reqHeaders;
        if (body.equals("show")) {
            reqHeaders = buildShowHeaders(url);
        } else {
            reqHeaders = buildPostHeaders(url);
        }
        reqHeaders.put("X-Requested-With", "XMLHttpRequest");
        String xRequestedWith = "X-Requested-With";
        String lastResponse = "";
        for (int retry = 0; retry < 4; retry++) {
            try {
                // 构建验证码图片 URL (按验证类型分流)
                String captchaUrl;
                if (method.contains("/verify")) {
                    captchaUrl = ParseUtils.urlCombine(url, "/index.php/verify/index.html") + "?" + Math.random();
                } else if (method.contains("scheckAC")) {
                    captchaUrl = ParseUtils.urlCombine(url, "/include/vdimgck.php") + "?get=" + new Date();
                } else {
                    captchaUrl = ParseUtils.urlCombine(url, "/inc/common/code.php?a=" + method + "&s=") + Math.random();
                }
                // 构建验证码请求头 (重新构建, 不含 X-Requested-With)
                HashMap<String, String> captchaHeaders;
                if (body.equals("show")) {
                    captchaHeaders = buildShowHeaders(url);
                } else {
                    captchaHeaders = buildPostHeaders(url);
                }
                // 获取验证码图片
                Response captchaResponse = OkHttp.newCall(captchaUrl, captchaHeaders);
                String base64Image;
                try {
                    byte[] captchaBytes = captchaResponse.body().bytes();
                    base64Image = Base64.encodeToString(captchaBytes, Base64.NO_WRAP);
                } finally {
                    captchaResponse.close();
                }
                // 发送到 OCR API
                HashMap<String, String> ocrHeaders = new HashMap<>();
                ocrHeaders.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/105.0.0.0 Safari/537.36");
                ocrHeaders.put("Content-Type", "text/plain; charset=utf-8");
                String ocrResult = fetchPostJson(ocrApi, base64Image, charset, ocrHeaders);
                // 提交验证
                String verifyResponse;
                if (method.contains("/verify")) {
                    String checkUrl = ParseUtils.urlCombine(url, "/index.php/ajax/verify_check?type=" + method + "&verify=" + ocrResult);
                    verifyResponse = fetchPostForm(checkUrl, null, charset, reqHeaders);
                } else if (method.contains("scheckAC")) {
                    reqHeaders.remove(xRequestedWith);
                    String searchUrl = "/search.php?scheckAC=check&page=&searchtype=&order=&tid=&area=&year=&letter=&yuyan=&state=&money=&ver=&jq=";
                    HashMap<String, String> params = new HashMap<>();
                    params.put("validate", ocrResult);
                    params.put("searchword", "");
                    verifyResponse = fetchPostForm(ParseUtils.urlCombine(url, searchUrl), params, charset, reqHeaders);
                } else {
                    String checkUrl = ParseUtils.urlCombine(url, "/inc/ajax.php?ac=code_check&type=" + method + "&code=" + ocrResult);
                    verifyResponse = fetchGet(checkUrl, charset, reqHeaders);
                }
                // 检查验证结果
                if (method.contains("scheckAC")) {
                    // scheckAC 路径
                    if (verifyResponse.contains("验证码不正确")) {
                        // 验证码错误, lastResponse 不更新, 重试
                        continue;
                    }
                    // 验证码正确, 重新请求原始 URL
                    String refetched;
                    if (headers != null) {
                        refetched = fetchPostForm(url, headers, charset, reqHeaders);
                    } else {
                        refetched = fetchGet(url, charset, reqHeaders);
                    }
                    if (!refetched.contains("输入正确的验证码") && !checkveriry(refetched)) {
                        return refetched;
                    }
                    lastResponse = refetched;
                } else {
                    // 非 scheckAC 路径: 解析 JSON
                    JSONObject json = new JSONObject(verifyResponse);
                    if (json.getString("msg").equals("ok")) {
                        reqHeaders.remove(xRequestedWith);
                        String refetched;
                        if (headers != null) {
                            refetched = fetchPostForm(url, headers, charset, reqHeaders);
                        } else {
                            refetched = fetchGet(url, charset, reqHeaders);
                        }
                        // 频繁操作限制
                        if (refetched.contains("不要频繁操作，搜索时间间隔为")) {
                            TimeUnit.SECONDS.sleep(6);
                            if (headers != null) {
                                refetched = fetchPostForm(url, headers, charset, reqHeaders);
                            } else {
                                refetched = fetchGet(url, charset, reqHeaders);
                            }
                        }
                        if (!refetched.contains("输入验证码") && !checkveriry(refetched)) {
                            return refetched;
                        }
                        lastResponse = refetched;
                    } else {
                        // msg != "ok", lastResponse 不更新, 重试
                        continue;
                    }
                }
            } catch (Exception e) {
                SpiderDebug.log(e);
                if (debugFlag) {
                    Notify.show("ocr验证出错：" + e.toString());
                }
            }
        }
        return lastResponse;
    }

    // ========================================================================
    // 请求头构建方法
    // ========================================================================

    /**
     * 构建 show 模式请求头。
     * <p>读取 "请求头参数" 配置项（空则回退 "Headers"），解析 key$value#key$value 格式，
     * 支持 UA 简写替换（PC_UA/电脑/MOBILE_UA/手机/IOS_UA/苹果手机/MAC_UA/苹果电脑）、
     * Cookie 追加（accCookie + charset 字段）、user-agent 同步写入 configJson、
     * referer=WebView 跳过等。配置不含 "$" 时走单 header 模式。</p>
     *
     * @param url 请求 URL（用于 referer 等）
     * @return 构建好的请求头 Map
     */
    protected HashMap<String, String> buildShowHeaders(String url) {
        HashMap<String, String> headers = new HashMap<>();
        try {
            // 解析配置键：优先 "请求头参数"，空则回退 "Headers"
            String configKey;
            if (this.getConfig("请求头参数").isEmpty()) {
                configKey = "Headers";
            } else {
                configKey = "请求头参数";
            }
            String uaConfig = this.getConfig(configKey, "").trim();

            if (uaConfig.contains("$")) {
                // ===== 多 header 模式：按 '#' 分隔，每条按 '$' 拆 key/value =====
                String[] entries = uaConfig.split("#");
                for (String entry : entries) {
                    String[] kv = entry.split("\\$");
                    if (kv.length < 2) continue;
                    String key = kv[0];
                    String value = kv[1];

                    // --- UA 别名替换 ---
                    if (value.equals("PC_UA") || value.equals("电脑")) {
                        value = PC_UA;
                    } else if (value.equals("MOBILE_UA") || value.equals("手机")) {
                        value = MOBILE_UA;
                    } else if (value.equals("IOS_UA") || value.equals("苹果手机")) {
                        value = IOS_UA;
                    } else if (value.equals("MAC_UA") || value.equals("苹果电脑")) {
                        value = MAC_UA;
                    }

                    // --- Cookie 追加：accCookie 非空且 key 为 cookie 时追加 ---
                    if (!this.accCookie.isEmpty() && key.equalsIgnoreCase("cookie")) {
                        StringBuilder sb = new StringBuilder();
                        sb.append(value);
                        sb.append(";").append(this.accCookie);
                        value = sb.toString();
                    }

                    // --- user-agent 同步写入 configJson ---
                    if (key.equalsIgnoreCase("user-agent")) {
                        this.configJson.put("user-agent", value);
                    }

                    // --- referer/WebView 处理：value 为 "WebView" 时跳过（不写入 headers） ---
                    if (!value.equalsIgnoreCase("WebView")) {
                        headers.put(key, value);
                    }
                }

                // --- 循环后 Cookie 补充：accCookie 非空且长度>1，配置无 Cookie$/cookie$ ---
                if (!this.accCookie.isEmpty() && this.accCookie.length() > 1) {
                    if (!uaConfig.contains("Cookie$") && !uaConfig.contains("cookie$")) {
                        headers.put("Cookie", this.accCookie);
                    }
                }
            } else {
                // ===== 单 header 模式 =====
                if (uaConfig.isEmpty()) {
                    uaConfig = "okhttp/3.12.11";
                } else {
                    // UA 别名替换
                    if (uaConfig.equals("PC_UA") || uaConfig.equals("电脑")) {
                        uaConfig = PC_UA;
                    } else if (uaConfig.equals("MOBILE_UA") || uaConfig.equals("手机")) {
                        uaConfig = MOBILE_UA;
                    } else if (uaConfig.equals("IOS_UA") || uaConfig.equals("苹果手机")) {
                        uaConfig = IOS_UA;
                    } else if (uaConfig.equals("MAC_UA") || uaConfig.equals("苹果电脑")) {
                        uaConfig = MAC_UA;
                    }
                }

                // --- Cookie 补充（无 Cookie$/cookie$ 检查，忠实还原 smali）---
                if (!this.accCookie.isEmpty() && this.accCookie.length() > 1) {
                    headers.put("Cookie", this.accCookie);
                }

                headers.put("User-Agent", uaConfig);
                this.configJson.put("user-agent", uaConfig);
            }
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        return headers;
    }

    /**
     * 构建 POST 模式请求头。
     * <p>读取 "搜索请求头参数" 配置项（空则回退 "SHeaders"），逻辑类似 buildShowHeaders，
     * Cookie 追加仅使用 accCookie 字段。配置为空且不含 "$" 时回退调用
     * buildShowHeaders(url)。</p>
     *
     * @param url 请求 URL
     * @return 构建好的请求头 Map
     */
    protected HashMap<String, String> buildPostHeaders(String url) {
        HashMap<String, String> headers = new HashMap<>();
        try {
            // 解析配置键：优先 "搜索请求头参数"，空则回退 "SHeaders"
            String configKey;
            if (this.getConfig("搜索请求头参数").isEmpty()) {
                configKey = "SHeaders";
            } else {
                configKey = "搜索请求头参数";
            }
            String uaConfig = this.getConfig(configKey, "").trim();

            if (uaConfig.contains("$")) {
                // ===== 多 header 模式 =====
                String[] entries = uaConfig.split("#");
                for (String entry : entries) {
                    String[] kv = entry.split("\\$");
                    if (kv.length < 2) continue;
                    String key = kv[0];
                    String value = kv[1];

                    // --- UA 别名替换 ---
                    if (value.equals("PC_UA") || value.equals("电脑")) {
                        value = PC_UA;
                    } else if (value.equals("MOBILE_UA") || value.equals("手机")) {
                        value = MOBILE_UA;
                    } else if (value.equals("IOS_UA") || value.equals("苹果手机")) {
                        value = IOS_UA;
                    } else if (value.equals("MAC_UA") || value.equals("苹果电脑")) {
                        value = MAC_UA;
                    }

                    // --- Cookie 追加：accCookie 非空且 key 为 cookie 时追加 ---
                    if (!this.accCookie.isEmpty() && key.equalsIgnoreCase("cookie")) {
                        StringBuilder sb = new StringBuilder();
                        sb.append(value);
                        sb.append(";").append(this.accCookie);
                        value = sb.toString();
                    }

                    // --- user-agent 同步写入 configJson ---
                    if (key.equalsIgnoreCase("user-agent")) {
                        this.configJson.put("user-agent", value);
                    }

                    // --- referer/WebView 处理：value 为 "WebView" 时跳过（不写入 headers） ---
                    if (!value.equalsIgnoreCase("WebView")) {
                        headers.put(key, value);
                    }
                }

                // --- 循环后 Cookie 补充：accCookie 非空且长度>1，配置无 Cookie$/cookie$ ---
                if (!this.accCookie.isEmpty() && this.accCookie.length() > 1) {
                    if (!uaConfig.contains("Cookie$") && !uaConfig.contains("cookie$")) {
                        headers.put("Cookie", this.accCookie);
                    }
                }
            } else {
                // ===== 单 header 模式 =====
                if (uaConfig.isEmpty()) {
                    // 配置为空时回退到 buildShowHeaders（忠实还原 smali）
                    return buildShowHeaders(url);
                }

                // UA 别名替换
                if (uaConfig.equals("PC_UA") || uaConfig.equals("电脑")) {
                    uaConfig = PC_UA;
                } else if (uaConfig.equals("MOBILE_UA") || uaConfig.equals("手机")) {
                    uaConfig = MOBILE_UA;
                } else if (uaConfig.equals("IOS_UA") || uaConfig.equals("苹果手机")) {
                    uaConfig = IOS_UA;
                } else if (uaConfig.equals("MAC_UA") || uaConfig.equals("苹果电脑")) {
                    uaConfig = MAC_UA;
                }

                // --- Cookie 补充（无 Cookie$/cookie$ 检查，忠实还原 smali）---
                if (!this.accCookie.isEmpty() && this.accCookie.length() > 1) {
                    headers.put("Cookie", this.accCookie);
                }

                headers.put("User-Agent", uaConfig);
                this.configJson.put("user-agent", uaConfig);
            }
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        return headers;
    }

    // ========================================================================
    // 配置方法
    // ========================================================================

    /**
     * 读取站点配置项，空值/"空"/"&&" 时返回默认值。
     *
     * @param key 配置键
     * @param def 默认值
     * @return 配置值或默认值
     */
    private String getConfig(String key, String def) {
        String value = this.siteConfig.optString(key);
        if (!value.isEmpty() && !value.equals("空") && !value.equals("&&")) {
            return value;
        }
        return def;
    }

    /**
     * 读取站点配置项（无默认值，空时返回 ""）。
     *
     * @param key 配置键
     * @return 配置值，空时返回 ""
     */
    private String getConfigNoDef(String key) {
        return getConfig(key, "");
    }

    /**
     * 懒加载解析 extend 为 siteConfig。
     * <p>http(s) URL → 拉取 JSON；否则直接解析 extend 字符串。
     * 读取 OCR_API → ocrApi；读取 DEBUG 配置 → debugFlag（"是" 或 "1"）。</p>
     */
    protected void ensureSiteConfig() {
        if (this.siteConfig != null) return;
        if (this.extend == null) return;
        try {
            String configStr;
            if (this.extend.startsWith("http")) {
                configStr = OkHttp.string(this.extend, null);
            } else {
                configStr = this.extend;
            }
            // 去除行首 // 注释（忽略前导空格/制表符），不影响 JSON 字符串值中的 http://
            configStr = configStr.replaceAll("(?m)^[ \\t]*//[^\\n]*$", "");
            this.siteConfig = new JSONObject(configStr);
            // 默认 OCR 接口为 repl.co 免费服务，生产环境建议通过 OCR_API 配置项覆盖为自建 API
            this.ocrApi = getConfig("OCR_API", "https://ddddocr--lineagett.repl.co/ocr/b64/text");
            String debugCfg = getConfig("DEBUG", "0");
            this.debugFlag = debugCfg.equals("是") || debugCfg.equals("1");
        } catch (JSONException e) {
            // smali 原为空 catch，此处增加日志辅助排查（不影响功能行为）
            SpiderDebug.log(e);
            if (this.debugFlag) {
                Notify.show("站点配置解析失败：" + e.toString());
            }
        }
    }

    /**
     * 将 referer 写入 configJson 并构建 url + "@Headers=" + configJson 的包装 URL。
     *
     * @param url     原始 URL
     * @param referer referer 值
     * @param flag    布尔标志（未使用，保留签名）
     * @return 包装后的 URL
     */
    protected String wrapHeaders(String url, String referer, boolean flag) {
        try {
            this.configJson.put("referer", referer);
            return url + "@Headers=" + this.configJson.toString();
        } catch (Exception e) {
            SpiderDebug.log(e);
            return url;
        }
    }

    // ========================================================================
    // 视频格式检测方法
    // ========================================================================

    /**
     * 判断 URL 是否为可播放的视频格式。
     * <p>读取 "手动嗅探视频链接关键词" 和 "手动嗅探视频链接过滤词" 配置。
     * URL 含 =http 或 .html 且不含已知标记时视为非视频。</p>
     *
     * @param url 待检查的 URL
     * @return 是视频格式返回 true，否则 false
     */
    @Override
    public boolean isVideoFormat(String url) throws Exception {
        ensureSiteConfig();
        String formatKey = getConfig("手动嗅探视频链接关键词").isEmpty() ? "VideoFormat" : "手动嗅探视频链接关键词";
        String filterKey = getConfig("手动嗅探视频链接过滤词").isEmpty() ? "VideoFilter" : "手动嗅探视频链接过滤词";
        String[] formats = getConfig(formatKey, ".m3u8#.mp4#.flv#video/tos#.mp3#.m4a").toLowerCase().split("#");
        String[] filters = getConfig(filterKey, "=http#.html").toLowerCase().split("#");
        String lower = url.toLowerCase();
        // URLs containing =http or .html are treated as non-video unless checkstring matches
        if ((lower.contains("=http") || lower.contains(".html")) && !checkstring(lower)) {
            return false;
        }
        for (String fmt : formats) {
            if (lower.contains(fmt)) {
                for (String flt : filters) {
                    if (lower.contains(flt) && !checkstring(lower)) {
                        return false;
                    }
                }
                return true;
            }
        }
        return false;
    }

    /**
     * 判断是否开启手动嗅探。
     * <p>读取 "是否开启手动嗅探" 配置，值为 "1" 或 "是" 时返回 true。</p>
     *
     * @return 开启手动嗅探返回 true，否则 false
     */
    @Override
    public boolean manualVideoCheck() throws Exception {
        ensureSiteConfig();
        String key = getConfig("是否开启手动嗅探").isEmpty() ? "ManualSniffer" : "是否开启手动嗅探";
        String val = getConfig(key);
        return val.equals("1") || val.equals("是");
    }

    // ========================================================================
    // 辅助工具方法（原 smali merge/xyq0208/* 还原）
    // ========================================================================

    /** 单参数 getConfig 便捷方法，等价于 getConfig(key, "")。 */
    private String getConfig(String key) {
        return getConfig(key, "");
    }

    /** 构建年份范围字符串（当前年份倒推 20 年），别名 getProxyBase。 */
    private static String getProxyBase() {
        return buildYearRange();
    }

    /**
     * HTML 实体/标签清理（原 smali 方法 މ/decodeUnicode）。
     * <p>将 &nbsp; 替换为空格，移除 HTML 实体、标签、括号，压缩空白。
     * 注意：smali 方法名为 decodeUnicode 但实际执行的是 HTML 清理。</p>
     */
    private String cleanText(String text) {
        if (text == null) return "";
        text = text.replaceAll("\\&nbsp;", " ");
        text = text.replaceAll("\\&[a-zA-Z]{1,10};", "");
        text = text.replaceAll("<[^>]*>", "");
        text = text.replaceAll("[(/>)<]", "");
        text = text.replaceAll("\\s{2,}", "");
        return text;
    }

    /** cleanText 别名。 */
    private String cleanHtml(String text) {
        return cleanText(text);
    }

    /**
     * 解码 HTML 内容（原 smali 方法 ԩ/decodeContent）。
     * <p>解码 \\uXXXX 转义序列，等价于 {@link #decodeHexChars(String)}。</p>
     */
    private static String decodeHtml(String html) {
        if (html == null) return "";
        return decodeHexChars(html);
    }

    /**
     * 应用 [替换:old1##new1||old2##new2] 替换规则（原 smali 方法 ވ.ރ）。
     * <p>从 rule 中提取 [替换:...] 内容，按 || 分割为多条规则，
     * 每条按 ## 分割为 old/new 对，执行字符串替换。
     * 无 ## 时移除匹配文本。</p>
     */
    private static String applyReplace(String source, String rule) {
        if (source == null || source.isEmpty()) return source;
        try {
            ArrayList<String> replaceParts = ParseUtils.regexExtract(rule, "[替换:", "]");
            if (replaceParts.isEmpty()) return source;
            String replaceCfg = replaceParts.get(0);
            if (replaceCfg.isEmpty()) return source;
            String[] rules = replaceCfg.split("\\|\\|");
            for (String r : rules) {
                String[] kv = r.split("##");
                if (kv.length < 2) {
                    source = source.replace(kv[0], "");
                } else {
                    source = source.replace(kv[0], kv[1]);
                }
            }
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        return source;
    }

    /** 判断 URL 是否为绝对 URL（以 http:// 或 https:// 开头）。 */
    private static boolean isAbsoluteUrl(String url) {
        if (url == null) return false;
        return url.startsWith("http://") || url.startsWith("https://")
                || url.startsWith("magnet:") || url.startsWith("ed2k://")
                || url.startsWith("thunder://") || url.startsWith("ftp://");
    }

    /**
     * 处理图片 URL（原 smali 方法 ԯ/resolveImageUrl）。
     * <p>needProxy 为 true 时通过本地代理加载图片。</p>
     */
    private String processPicUrl(String picUrl, String pageUrl, boolean needProxy) {
        if (picUrl == null || picUrl.isEmpty()) return picUrl;
        if (needProxy) {
            try {
                String proxyBase = Proxy.getUrl().replace("/proxy", "/image/");
                if (picUrl.startsWith("http")) {
                    return proxyBase + picUrl;
                }
                return ParseUtils.urlCombine(pageUrl, picUrl);
            } catch (Exception e) {
                SpiderDebug.log(e);
            }
        }
        return picUrl;
    }

    /**
     * 嗅探真实视频 URL（原 smali 方法 ވ.ކ）。
     * <p>抓取页面 HTML，解析 video/source 标签，返回首个非空 src。</p>
     */
    private static String fetchAndExtract(String url) {
        try {
            String html = OkHttp.string(url, new HashMap<>());
            Document doc = Jsoup.parse(html);
            Elements videos = doc.select("video source");
            for (Element v : videos) {
                String src = v.attr("src");
                if (!src.isEmpty()) return src;
            }
            return url;
        } catch (Exception e) {
            SpiderDebug.log(e);
            return url;
        }
    }

    /** 判断 URL 是否为可直接播放的视频 URL（含 .m3u8/.mp4 等）。 */
    private static boolean isPlayableVideoUrl(String url) {
        if (url == null || url.isEmpty()) return false;
        String lower = url.toLowerCase();
        return lower.endsWith(".m3u8") || lower.endsWith(".mp4") || lower.endsWith(".flv")
                || lower.contains(".m3u8?") || lower.contains(".mp4?")
                || lower.endsWith(".mkv") || lower.endsWith(".avi");
    }

    /** 判断 URL 是否为视频 URL（含常见视频扩展名）。 */
    private static boolean isVideoUrl(String url) {
        if (url == null || url.isEmpty()) return false;
        String lower = url.toLowerCase();
        String[] exts = {".m3u8", ".mp4", ".flv", ".mkv", ".avi", ".mov", ".wmv", ".ts", ".webm"};
        for (String ext : exts) {
            if (lower.contains(ext)) return true;
        }
        return false;
    }

    /**
     * 判断 URL 是否为 P2P/非 HTTP 协议链接（磁力/ed2k/迅雷/FTP）。
     * <p>此类链接不能通过 HTTP 解析器抓取，需以 parse(0) 直接交给支持 P2P 的播放器处理，
     * 否则 HTTP 客户端会因无法建立连接而超时。</p>
     */
    private static boolean isP2PUrl(String url) {
        if (url == null || url.trim().isEmpty()) return false;
        String lower = url.trim().toLowerCase();
        return lower.startsWith("magnet:")
                || lower.startsWith("ed2k://")
                || lower.startsWith("thunder://")
                || lower.startsWith("ftp://")
                || lower.endsWith(".torrent")
                || lower.contains(".torrent?");
    }

    // ========================================================================
    // 筛选构建方法
    // ========================================================================

    /**
     * 构建单个筛选组 Filter 对象。
     * <p>根据 key/name/nameVal/valueVal 构建 Filter bean。
     * nameVal 为显示标签（n），valueVal 为筛选值（v）。
     * 非 "by" 类型且 valueVal 非 "空" 时添加 "全部" 选项；
     * "by" 类型且 valueVal 非 "空" 时添加 "默认" 选项。
     * valueVal 含 "&" 时按 "&" 分割为多值。</p>
     *
     * @param key      筛选键（如 "cateId" / "class" / "area" / "year" / "lang" / "by"）
     * @param name     筛选显示名（如 "分类" / "类型" / "地区"）
     * @param nameVal  显示标签（n），"&" 分隔多值
     * @param valueVal 筛选值（v），"&" 分隔多值
     * @return 构建好的 Filter 对象
     */
    private Filter buildFilterEntry(String key, String name, String nameVal, String valueVal) {
        try {
            List<Filter.Value> values = new ArrayList<>();
            if (!key.equals("by") && !valueVal.equals("空")) {
                values.add(new Filter.Value("全部", ""));
            } else if (key.equals("by") && !valueVal.equals("空")) {
                values.add(new Filter.Value("默认", ""));
            }
            if (valueVal.contains("&") && !valueVal.equals("空")) {
                String[] nameParts = nameVal.split("&");
                String[] valueParts = valueVal.split("&");
                for (int i = 0; i < nameParts.length && i < valueParts.length; i++) {
                    values.add(new Filter.Value(nameParts[i], valueParts[i].replaceAll("＆＆", "&")));
                }
            } else if (!valueVal.equals("空")) {
                values.add(new Filter.Value(nameVal, valueVal));
            }
            return new Filter(key, name, values);
        } catch (Exception e) {
            SpiderDebug.log(e);
            if (debugFlag) {
                Notify.show("筛选getRType部分出错：" + e.toString());
            }
            return null;
        }
    }

    /**
     * 为各分类构建筛选配置。
     * <p>遍历分类列表，为每个分类构建 cateId/class/area/year/lang/by 筛选组。
     * 每个筛选类型：name=="*" 时用 id 作为名称；name 和 id 均含 "||" 时按分类索引分割。
     * 返回 LinkedHashMap：键为分类名，值为该分类的筛选组列表。</p>
     *
     * @param categoryNames 分类名称（"&" 分隔，"＆＆" 转义为 "&"）
     * @param urlTemplate   URL 模板（含 {cateId}/{class}/{area}/{year}/{lang}/{by} 占位符）
     * @param cateId       分类显示名（名称，→n）
     * @param cateName     分类筛选值（替换词，→v）
     * @param classId      类型显示名（名称，→n）
     * @param className    类型筛选值（替换词，→v）
     * @param areaId       地区显示名（名称，→n）
     * @param areaName     地区筛选值（替换词，→v）
     * @param yearId       年份显示名（名称，→n）
     * @param yearName     年份筛选值（替换词，→v）
     * @param langId       语言显示名（名称，→n）
     * @param langName     语言筛选值（替换词，→v）
     * @param byId         排序显示名（名称，→n）
     * @param byName       排序筛选值（替换词，→v）
     * @return 各分类的筛选配置 Map
     */
    private LinkedHashMap<String, List<Filter>> buildFilter(String categoryNames, String urlTemplate,
            String cateId, String cateName,
            String classId, String className,
            String areaId, String areaName,
            String yearId, String yearName,
            String langId, String langName,
            String byId, String byName) {
        try {
            LinkedHashMap<String, List<Filter>> result = new LinkedHashMap<>();
            String sep = "&";
            // Split category names by "&", replacing ＆＆ with &
            String[] rawCategories = categoryNames.split(sep);
            ArrayList<String> categoryList = new ArrayList<>();
            for (String rawCat : rawCategories) {
                categoryList.add(rawCat.replaceAll("＆＆", sep));
            }

            for (int i = 0; i < categoryList.size(); i++) {
                List<Filter> filters = new ArrayList<>();
                try {
                    // --- cateId (分类) filter ---
                    if (!cateId.isEmpty() && !cateName.isEmpty() && urlTemplate.contains("{cateId}")) {
                        String displayName = cateName.equals("*") ? cateId : cateName;
                        if (displayName.contains("||") && cateId.contains("||")) {
                            String[] ids = cateId.split("\\|\\|");
                            String[] names = displayName.split("\\|\\|");
                            if (i < names.length && !names[i].equals("空")) {
                                filters.add(buildFilterEntry("cateId", "分类", ids[i], names[i]));
                            }
                        } else {
                            filters.add(buildFilterEntry("cateId", "分类", cateId, displayName));
                        }
                    }

                    // --- class (类型) filter ---
                    if (!classId.isEmpty() && !className.isEmpty() && urlTemplate.contains("{class}")) {
                        String displayName = className.equals("*") ? classId : className;
                        if (displayName.contains("||") && classId.contains("||")) {
                            String[] ids = classId.split("\\|\\|");
                            String[] names = displayName.split("\\|\\|");
                            if (i < names.length && !names[i].equals("空")) {
                                filters.add(buildFilterEntry("class", "类型", ids[i], names[i]));
                            }
                        } else {
                            filters.add(buildFilterEntry("class", "类型", classId, displayName));
                        }
                    }

                    // --- area (地区) filter ---
                    if (!areaId.isEmpty() && !areaName.isEmpty() && urlTemplate.contains("{area}")) {
                        String displayName = areaName.equals("*") ? areaId : areaName;
                        if (displayName.contains("||") && areaId.contains("||")) {
                            String[] ids = areaId.split("\\|\\|");
                            String[] names = displayName.split("\\|\\|");
                            if (i < names.length && !names[i].equals("空")) {
                                filters.add(buildFilterEntry("area", "地区", ids[i], names[i]));
                            }
                        } else {
                            filters.add(buildFilterEntry("area", "地区", areaId, displayName));
                        }
                    }

                    // --- year (年份) filter ---
                    if (!yearId.isEmpty() && !yearName.isEmpty() && urlTemplate.contains("{year}")) {
                        String displayName = yearName.equals("*") ? yearId : yearName;
                        if (displayName.contains("||") && yearId.contains("||")) {
                            String[] ids = yearId.split("\\|\\|");
                            String[] names = displayName.split("\\|\\|");
                            if (i < names.length && !names[i].equals("空")) {
                                filters.add(buildFilterEntry("year", "年份", ids[i], names[i]));
                            }
                        } else {
                            filters.add(buildFilterEntry("year", "年份", yearId, displayName));
                        }
                    }

                    // --- lang (语言) filter ---
                    if (!langId.isEmpty() && !langName.isEmpty() && urlTemplate.contains("{lang}")) {
                        String displayName = langName.equals("*") ? langId : langName;
                        if (displayName.contains("||") && langId.contains("||")) {
                            String[] ids = langId.split("\\|\\|");
                            String[] names = displayName.split("\\|\\|");
                            if (i < names.length && !names[i].equals("空")) {
                                filters.add(buildFilterEntry("lang", "语言", ids[i], names[i]));
                            }
                        } else {
                            filters.add(buildFilterEntry("lang", "语言", langId, displayName));
                        }
                    }

                    // --- by (排序) filter ---
                    if (!byId.isEmpty() && !byName.isEmpty() && urlTemplate.contains("{by}")) {
                        String displayName = byName.equals("*") ? byId : byName;
                        if (displayName.contains("||") && byId.contains("||")) {
                            String[] ids = byId.split("\\|\\|");
                            String[] names = displayName.split("\\|\\|");
                            if (i < names.length && !names[i].equals("空")) {
                                filters.add(buildFilterEntry("by", "排序", ids[i], names[i]));
                            }
                        } else {
                            filters.add(buildFilterEntry("by", "排序", byId, displayName));
                        }
                    }

                    // Put filters list into result with category name as key
                    result.put(categoryList.get(i), filters);
                } catch (Exception e) {
                    SpiderDebug.log(e);
                    if (debugFlag) {
                        Notify.show("buildFilter详细筛选生成出错：" + e.toString());
                    }
                }
            }
            return result;
        } catch (Exception e) {
            SpiderDebug.log(e);
            if (debugFlag) {
                Notify.show("buildFilter全局出错：" + e.toString());
            }
            return null;
        }
    }

// === METHOD: homeContent (smali L17282-L18287) ===
@Override
public String homeContent(boolean filter) throws Exception {
    String empty = "";
    List<Class> classes = new ArrayList<>();
    JSONObject filters = null;
    String classValue = "", classUrl = "";
    String fclassName = "", fclassValue = "", fcatelogName = "", fcatelogValue = "";
    String fareaName = "", fareaValue = "", fyearName = "", fyearValue = "";
    String flangName = "", flangValue = "", fsortName = "", fsortValue = "";
    try {
        ensureSiteConfig();

        String className = getConfig("分类名称");
        if (className.isEmpty()) className = getConfig("class_name");
        classValue = getConfig("分类名称替换词");
        if (classValue.isEmpty()) classValue = getConfig("class_value");
        classUrl = getConfig("分类链接");
        if (classUrl.isEmpty()) classUrl = getConfig("class_url");
        fclassName = getConfig("筛选子分类名称");
        if (fclassName.isEmpty()) fclassName = getConfig("fclass_name");
        fclassValue = getConfig("筛选子分类替换词");
        if (fclassValue.isEmpty()) fclassValue = getConfig("fclass_value");
        fcatelogName = getConfig("筛选类型名称");
        if (fcatelogName.isEmpty()) fcatelogName = getConfig("fcatelog_name");
        fcatelogValue = getConfig("筛选类型替换词");
        if (fcatelogValue.isEmpty()) fcatelogValue = getConfig("fcatelog_value");
        fareaName = getConfig("筛选地区名称");
        if (fareaName.isEmpty()) fareaName = getConfig("farea_name");
        fareaValue = getConfig("筛选地区替换词");
        if (fareaValue.isEmpty()) fareaValue = getConfig("farea_value");
        fyearName = getConfig("筛选年份名称");
        if (fyearName.isEmpty()) fyearName = getConfig("fyear_name", getProxyBase());
        fyearValue = getConfig("筛选年份替换词");
        if (fyearValue.isEmpty()) fyearValue = getConfig("fyear_value", "*");
        flangName = getConfig("筛选语言名称");
        if (flangName.isEmpty()) flangName = getConfig("flang_name");
        flangValue = getConfig("筛选语言替换词");
        if (flangValue.isEmpty()) flangValue = getConfig("flang_value");
        fsortName = getConfig("筛选排序名称");
        if (fsortName.isEmpty()) fsortName = getConfig("fsort_name", "时间&人气&评分");
        fsortValue = getConfig("筛选排序替换词");
        if (fsortValue.isEmpty()) fsortValue = getConfig("fsort_value", "time&hits&score");

        String[] classNameParts = className.split("&");
        String[] classValueParts = classValue.split("&");
        for (int i = 0; i < classNameParts.length; i++) {
            Class cls = new Class(classValueParts[i].replaceAll("＆＆", "&"), classNameParts[i]);
            classes.add(cls);
        }
    } catch (Exception e) {
        SpiderDebug.log(e);
        if (debugFlag) {
            Notify.show("homeContent全局出错：" + e.toString());
        }
        return empty;
    }

    try {
        String filterdata = getConfig("筛选数据");
        String filterdataKey = filterdata.isEmpty() ? "filterdata" : "筛选数据";
        if (filterdata.isEmpty()) filterdata = getConfig("filterdata");
        try {
            // smali 原始调用，疑似触发网络栈初始化/权限检查，结果未使用，忠实保留
            java.net.InetAddress.getLocalHost();
        } catch (Exception ignored) {
        }
        if (filterdata.startsWith("clan://") || filterdata.startsWith("http") || filterdata.startsWith("./")) {
            String resp = OkHttp.string(filterdata, null).trim();
            if (resp.startsWith("{") && resp.endsWith("}")) {
                filters = new JSONObject(resp);
            }
        } else if (filterdata.equalsIgnoreCase("EXT")) {
            // 使用 Gson 序列化 buildFilter 结果，避免 org.json JSONObject.wrap() 对自定义
            // bean（com.github.catvod.bean.Filter）返回 null 导致筛选数据丢失
            LinkedHashMap<String, List<Filter>> filterMap = buildFilter(classValue, classUrl, fclassName, fclassValue, fcatelogName, fcatelogValue,
                    fareaName, fareaValue, fyearName, fyearValue, flangName, flangValue, fsortName, fsortValue);
            if (filterMap != null) {
                filters = new JSONObject(new Gson().toJson(filterMap));
            }
        } else {
            filters = this.siteConfig.optJSONObject(filterdataKey);
        }
    } catch (Exception e) {
        SpiderDebug.log(e);
        if (debugFlag) {
            Notify.show("homeContent筛选部分出错：" + e.toString());
        }
    }

    try {
        if (filter && filters != null) {
            return Result.string(classes, filters);
        }
        return Result.get().classes(classes).string();
    } catch (Exception e) {
        SpiderDebug.log(e);
        if (debugFlag) {
            Notify.show("homeContent全局出错：" + e.toString());
        }
        return empty;
    }
}

// === METHOD: homeVideoContent (smali L18288-L21164) ===
@Override
public String homeVideoContent() throws Exception {
    final String AMP = "&";
    final String ONE = "1";
    final String YES = "是";
    final String EMPTY = "";
    final String MD5_PREFIX = "md5(";
    final String MD5_SUFFIX = ")";
    final String PG_URL = "PG_URL";
    final String LIST_KEY = "list";
    final String SEMI_POST = ";post";
    final String REGEX_Q = "\\?";
    final String FULLWIDTH_Q = "？？";
    final String QMARK = "?";
    final String SEMI = ";";
    final String HTTP = "http";
    final String AMP_AMP = "&&";

    String subtitleKey = "首页片单副标题";
    if (getConfig(subtitleKey).isEmpty()) subtitleKey = "home_subtitle";
    String picKey = "首页片单图片";
    if (getConfig(picKey).isEmpty()) picKey = "home_pic";
    String urlKey = "首页片单链接";
    if (getConfig(urlKey).isEmpty()) urlKey = "home_url";
    String titleKey = "首页片单标题";
    if (getConfig(titleKey).isEmpty()) titleKey = "home_title";
    String homeJsoupKey = "首页片单是否Jsoup写法";
    if (getConfig(homeJsoupKey).isEmpty()) homeJsoupKey = "home_is_jsoup";
    String epiArrKey = "首页片单列表数组规则";
    if (getConfig(epiArrKey).isEmpty()) epiArrKey = "hmepi_arr_rule";
    String arrKey = "首页列表数组规则";
    if (getConfig(arrKey).isEmpty()) arrKey = "home_arr_rule";
    String rcmedKey = "首页推荐链接";
    if (getConfig(rcmedKey).isEmpty()) rcmedKey = "rcmed_url";
    String proxyKey = "图片是否需要代理";
    if (getConfig(proxyKey).isEmpty()) proxyKey = "PicNeedProxy";
    String homeContentKey = "是否开启获取首页数据";
    if (getConfig(homeContentKey).isEmpty()) homeContentKey = "homeContent";
    String prefixKey = "首页片单链接加前缀";
    if (getConfig(prefixKey).isEmpty()) prefixKey = "home_prefix";
    String suffixKey = "首页片单链接加后缀";
    if (getConfig(suffixKey).isEmpty()) suffixKey = "home_suffix";
    String classValueKey = "分类名称替换词";
    if (getConfig(classValueKey).isEmpty()) classValueKey = "class_value";

    List<Vod> list = new ArrayList<>();
    String emptyResult = EMPTY;

    try {
        ensureSiteConfig();

        String homeContentCfg = getConfig(homeContentKey);
        String rcmedUrlCfg = getConfig(rcmedKey);
        String epiArrCfg = getConfig(epiArrKey);

        boolean homeContentEnabled = homeContentCfg.equals(ONE) || homeContentCfg.equals(YES);
        boolean bothSet = !rcmedUrlCfg.isEmpty() && !epiArrCfg.isEmpty();
        boolean bothEmpty = rcmedUrlCfg.isEmpty() && epiArrCfg.isEmpty();

        if (!bothSet && homeContentEnabled) {
            String classValue = getConfig(classValueKey, EMPTY);
            String[] cateArr = classValue.split(AMP);
            for (int i = 0; i < cateArr.length; i++) {
                if (list.size() >= 20) break;
                String tid = cateArr[i].replaceAll("＆＆", AMP);
                HashMap<String, String> ext = new HashMap<>();
                String cateResultStr = parseCategoryList(tid, ONE, false, ext);
                if (cateResultStr != null && !cateResultStr.isEmpty()) {
                    try {
                        JSONObject cateJson = new JSONObject(cateResultStr);
                        JSONArray arr = cateJson.optJSONArray(LIST_KEY);
                        if (arr != null) {
                            for (int j = 0; j < arr.length() && j < 5; j++) {
                                list.add(Vod.objectFrom(arr.getJSONObject(j).toString()));
                            }
                        }
                    } catch (Exception ce) {
                        SpiderDebug.log(ce);
                    }
                }
            }
        }

        if (bothEmpty) {
            return Result.string(list);
        }
        if (!homeContentEnabled) {
            return Result.string(list);
        }

        String charsetCfgKey = "网页编码格式";
        if (getConfig(charsetCfgKey).isEmpty()) charsetCfgKey = "Coding_format";
        this.charset = getConfig(charsetCfgKey, "UTF-8");

        String catJsoupKey = "分类片单是否Jsoup写法";
        if (getConfig(catJsoupKey).isEmpty()) catJsoupKey = "cat_is_jsoup";
        String catTitleKey = "分类片单标题";
        if (getConfig(catTitleKey).isEmpty()) catTitleKey = "cat_title";
        String catUrlKey = "分类片单链接";
        if (getConfig(catUrlKey).isEmpty()) catUrlKey = "cat_url";
        String catPicKey = "分类片单图片";
        if (getConfig(catPicKey).isEmpty()) catPicKey = "cat_pic";
        String catSubtitleKey = "分类片单副标题";
        if (getConfig(catSubtitleKey).isEmpty()) catSubtitleKey = "cat_subtitle";
        String catPrefixKey = "分类片单链接加前缀";
        if (getConfig(catPrefixKey).isEmpty()) catPrefixKey = "cat_prefix";
        String catSuffixKey = "分类片单链接加后缀";
        if (getConfig(catSuffixKey).isEmpty()) catSuffixKey = "cat_suffix";

        boolean homeIsJsoup = getConfig(homeJsoupKey, ONE).equals(ONE) || getConfig(homeJsoupKey, YES).equals(YES);
        boolean picNeedProxy = getConfig(proxyKey, ONE).equals(ONE) || getConfig(proxyKey, YES).equals(YES);
        boolean catIsJsoup = getConfig(catJsoupKey, ONE).equals(ONE) || getConfig(catJsoupKey, YES).equals(YES);

        String pageUrl = getConfig(rcmedKey);
        String tsSeconds = String.valueOf(System.currentTimeMillis() / 1000L);
        String tsMillis = String.valueOf(System.currentTimeMillis());
        pageUrl = pageUrl.replace("时间戳", tsSeconds);
        pageUrl = pageUrl.replace("时间标", tsMillis);

        if (pageUrl.contains(MD5_PREFIX)) {
            ArrayList<String> md5Parts = ParseUtils.regexExtract(pageUrl, MD5_PREFIX, MD5_SUFFIX);
            if (!md5Parts.isEmpty()) {
                String md5Content = md5Parts.get(0);
                String md5Full = MD5_PREFIX + md5Content + MD5_SUFFIX;
                String encoded = ParseUtils.md5Hex(md5Content);
                pageUrl = pageUrl.replace(md5Full, encoded);
            }
        }

        String html;
        if (pageUrl.contains(SEMI_POST)) {
            String postUrl = pageUrl.split(REGEX_Q)[0].replaceAll(FULLWIDTH_Q, QMARK).trim();
            String postBody = pageUrl.split(REGEX_Q)[1].split(SEMI)[0].replaceAll(FULLWIDTH_Q, QMARK).trim();

            if (postBody.isEmpty()) {
                Map<String, String> headers = buildShowHeaders(postUrl);
                html = fetchPostForm(postUrl, null, this.charset, headers);
            } else if (postBody.startsWith("{") && postBody.endsWith("}")) {
                String jsonBody = new JSONObject(postBody).toString();
                Map<String, String> headers = buildShowHeaders(postUrl);
                html = fetchPostJson(postUrl, jsonBody, this.charset, headers);
            } else {
                LinkedHashMap<String, String> formData = new LinkedHashMap<>();
                String[] pairs = postBody.split(AMP);
                for (String pair : pairs) {
                    int eqIdx = pair.indexOf("=");
                    if (eqIdx >= 0) {
                        formData.put(pair.substring(0, eqIdx), pair.substring(eqIdx + 1));
                    }
                }
                Map<String, String> headers = buildShowHeaders(postUrl);
                html = fetchPostForm(postUrl, formData, this.charset, headers);
            }
        } else {
            Map<String, String> headers = buildShowHeaders(pageUrl);
            html = fetchGet(pageUrl, this.charset, headers);

            if (html != null && html.contains("检测中") && html.contains("btwaf")) {
                html = handleBtwafVerify(pageUrl, html, "show");
            }
            if (html != null && (html.contains("/huadong_") || html.contains("/renji_"))) {
                html = handleSniffVerify(pageUrl, html, "show");
            }
        }

        String decodedHtml = decodeHtml(html);
        org.jsoup.nodes.Document doc = org.jsoup.Jsoup.parse(decodedHtml);

        String prefixRule = getConfig(prefixKey);
        if (prefixRule.isEmpty() && homeIsJsoup == catIsJsoup) {
            prefixRule = getConfig(catPrefixKey);
        }
        if (prefixRule.contains(AMP_AMP)) {
            prefixRule = getTextByRule(doc, prefixRule);
            prefixRule = prefixRule.replace(PG_URL, pageUrl);
        }
        if (prefixRule.contains(PG_URL)) {
            prefixRule = prefixRule.replace(PG_URL, pageUrl);
            prefixRule = prefixRule.replaceAll("'", EMPTY);
        }
        final String prefixText = prefixRule;

        String suffixRule = getConfig(suffixKey);
        if (suffixRule.isEmpty() && homeIsJsoup == catIsJsoup) {
            suffixRule = getConfig(catSuffixKey);
        }
        if (suffixRule.contains(AMP_AMP)) {
            suffixRule = getTextByRule(doc, suffixRule);
        }
        final String suffixText = suffixRule;

        String arrRule = getConfig(arrKey);
        org.jsoup.nodes.Element listNode = doc;
        String[] arrParts = arrRule.split(AMP_AMP);
        listNode = getTrueElement(arrParts[0], listNode);
        for (int i = 1; i < arrParts.length - 1; i++) {
            // 容错：中间规则不匹配时跳过，保持上一个非空元素继续搜索
            org.jsoup.nodes.Element next = getTrueElement(arrParts[i], listNode);
            if (next != null) {
                listNode = next;
            }
        }
        org.jsoup.select.Elements outerElements = selectElements(listNode, arrParts[arrParts.length - 1]);

        for (int i = 0; i < outerElements.size(); i++) {
            org.jsoup.nodes.Element outerElem = outerElements.get(i);

            String epiRule = getConfig(epiArrKey);
            org.jsoup.select.Elements innerElements;
            if (epiRule.contains(AMP_AMP)) {
                String[] epiParts = epiRule.split(AMP_AMP);
                org.jsoup.nodes.Element innerNode = getTrueElement(epiParts[0], outerElem);
                for (int j = 1; j < epiParts.length - 1; j++) {
                    innerNode = getTrueElement(epiParts[j], innerNode);
                }
                innerElements = selectElements(innerNode, epiParts[epiParts.length - 1]);
            } else {
                innerElements = selectElements(outerElem, epiRule);
            }

            String defaultSubtitle = EMPTY;
            for (int j = 0; j < innerElements.size(); j++) {
                try {
                    org.jsoup.nodes.Element item = innerElements.get(j);

                    String titleRule = getConfig(titleKey);
                    if (titleRule.isEmpty() && homeIsJsoup == catIsJsoup) {
                        titleRule = getConfig(catTitleKey);
                    }
                    String title;
                    if (homeIsJsoup) {
                        title = getTextByRule(item, titleRule);
                    } else {
                        String[] titleParts = titleRule.split(AMP_AMP);
                        title = cleanHtml(ParseUtils.regexExtract(item.toString(), titleParts[0], titleParts[1]).get(0));
                    }

                    String picRule = getConfig(picKey);
                    if (picRule.isEmpty() && homeIsJsoup == catIsJsoup) {
                        picRule = getConfig(catPicKey);
                    }
                    String pic = defaultSubtitle;
                    boolean picReady = false;
                    if (!picRule.isEmpty()) {
                        if (picRule.startsWith(HTTP)) {
                            pic = picRule;
                            picReady = true;
                        } else {
                            try {
                                if (homeIsJsoup) {
                                    pic = getTextByRule(item, picRule).trim();
                                } else {
                                    String[] picParts = picRule.split(AMP_AMP);
                                    pic = ParseUtils.regexExtract(item.toString(), picParts[0], picParts[1]).get(0);
                                }
                                picReady = true;
                            } catch (Exception picEx) {
                                SpiderDebug.log(picEx);
                                pic = defaultSubtitle;
                            }
                            if (picReady && pic.contains("url(")) {
                                try {
                                    String urlContent = pic.replaceAll("\\&quot;", EMPTY);
                                    String[] urlParts = urlContent.split("url\\(");
                                    if (urlParts.length > 1 && urlParts[1].contains(")")) {
                                        pic = urlParts[1].split("\\)")[0].replaceAll("['\"]", EMPTY);
                                    }
                                } catch (Exception urlEx) {
                                    SpiderDebug.log(urlEx);
                                    picReady = false;
                                }
                            }
                            if (picReady) {
                                try {
                                    pic = ParseUtils.urlCombine(pageUrl, pic);
                                } catch (Exception ucEx) {
                                    SpiderDebug.log(ucEx);
                                    picReady = false;
                                }
                            }
                        }
                        if (picReady && picNeedProxy) {
                            try {
                                pic = processPicUrl(pic, pageUrl, picNeedProxy);
                            } catch (Exception proxyEx) {
                                SpiderDebug.log(proxyEx);
                            }
                        }
                    }

                    String subtitleRule = getConfig(subtitleKey);
                    if (subtitleRule.isEmpty() && homeIsJsoup == catIsJsoup) {
                        subtitleRule = getConfig(catSubtitleKey);
                    }
                    String subtitle;
                    if (!subtitleRule.isEmpty()) {
                        if (homeIsJsoup) {
                            try {
                                subtitle = getTextByRule(item, subtitleRule);
                            } catch (Exception subEx) {
                                SpiderDebug.log(subEx);
                                subtitle = defaultSubtitle;
                            }
                        } else {
                            try {
                                String[] subParts = subtitleRule.split(AMP_AMP);
                                subtitle = cleanHtml(ParseUtils.regexExtract(item.toString(), subParts[0], subParts[1]).get(0));
                            } catch (Exception subEx) {
                                SpiderDebug.log(subEx);
                                subtitle = defaultSubtitle;
                            }
                        }
                    } else {
                        subtitle = defaultSubtitle;
                    }

                    String urlRule = getConfig(urlKey);
                    if (urlRule.isEmpty() && homeIsJsoup == catIsJsoup) {
                        urlRule = getConfig(catUrlKey);
                    }
                    String extractedUrl;
                    if (homeIsJsoup) {
                        String baseRule = urlRule.split("\\[替换:")[0];
                        extractedUrl = getTextByRule(item, baseRule);
                    } else {
                        String[] urlParts = urlRule.split("\\[替换:");
                        String baseRule = urlParts[0];
                        String[] baseParts = baseRule.split(AMP_AMP);
                        extractedUrl = ParseUtils.regexExtract(item.toString(), baseParts[0], baseParts[1]).get(0);
                    }

                    if (urlRule.contains("[替换")) {
                        extractedUrl = applyReplace(extractedUrl, urlRule);
                    }

                    String finalUrl = new StringBuilder()
                            .append(prefixText)
                            .append(extractedUrl)
                            .append(suffixText)
                            .toString();

                    if (finalUrl.contains("'input'")) {
                        finalUrl = finalUrl.replaceAll("'input'", extractedUrl);
                    }

                    String vodId = new StringBuilder()
                            .append(title)
                            .append("$$$")
                            .append(pic)
                            .append("$$$")
                            .append(finalUrl)
                            .toString();
                    Vod vod = new Vod(vodId, title, pic, subtitle);
                    list.add(vod);
                } catch (Exception itemEx) {
                    SpiderDebug.log(itemEx);
                    if (debugFlag) {
                        Notify.show("主页历遍列表出错：" + itemEx.toString());
                    }
                    continue;
                }
            }
        }

        return Result.string(list);

    } catch (Exception e) {
        SpiderDebug.log(e);
        if (debugFlag) {
            Notify.show("主页全局出错：" + e.toString());
        }
        return emptyResult;
    }
}

// === METHOD: categoryContent (smali L12879-L12915) ===
@Override
public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
    String result = parseCategoryList(tid, pg, filter, extend);
    if (result != null) {
        return result;
    }
    return "";
}

// === METHOD: parseCategoryList (smali L1058-L5106) ===
private String parseCategoryList(String tid, String pg, boolean filter, HashMap<String, String> extend) {
    final String ZERO = "0";
    final String ONE = "1";
    final String YES = "是";
    final String NO = "否";
    final String CATE_PG = "{catePg}";
    final String MD5_PREFIX = "md5(";
    final String MD5_SUFFIX = ")";
    final String FIRST_PAGE_MARK = "firstPage=";
    final String FIRST_PAGE_REGEX = "\\[firstPage=";
    final String BRACKET_CLOSE = "\\]";
    final String BRACE_CLOSE = "}";
    final String BRACE_OPEN = "{";
    final String FULLWIDTH_BRACE_OPEN = "｛";
    final String FULLWIDTH_BRACE_CLOSE = "｝";
    final String SEMI_POST = ";post";
    final String DOUBLE_QMARK = "？？";
    final String QMARK = "?";
    final String SEMI = ";";
    final String AMP = "&";
    final String EQ = "=";
    final String AND_AND = "&&";
    final String DOUBLE_DOLLAR = "$$$";
    final String INPUT_PLACEHOLDER = "'input'";
    final String SLASH = "/";
    final String HTTP = "http";
    final String PG_URL = "PG_URL";
    final String SINGLE_QUOTE = "'";
    final String REPLACE_PREFIX = "[替换";
    final String REPLACE_REGEX = "\\[替换:";
    final String URL_FUNC = "url(";
    final String URL_FUNC_REGEX = "url\\(";
    final String CLOSE_PAREN_REGEX = "\\)";
    final String QUOTE_REGEX = "['\"]";
    final String HTML_ENTITY_QUOT = "\\&quot;";
    final String SNIFF_HUADONG = "/huadong_";
    final String SNIFF_RENJI = "/renji_";
    final String VERIFY_TEXT = "输入验证码";
    final String DETECT_TEXT = "检测中";
    final String BTWAF_TEXT = "btwaf";
    final String SHOW = "show";
    final String TIMESTAMP_KEY = "时间戳";
    final String TIME_MARK_KEY = "时间标";
    final int MAX_INT = 0x7fffffff;

    List<Vod> videos = new ArrayList<>();
    String pageStr = ZERO;
    String responseHtml = null;

    try {
        ensureSiteConfig();

        String charsetKey = getConfig("网页编码格式").isEmpty() ? "Coding_format" : "网页编码格式";
        String firstPageKey = getConfig("分类起始页码").isEmpty() ? "firstpage" : "分类起始页码";
        this.charset = getConfig(charsetKey, "UTF-8");

        String firstPageCfg = getConfig(firstPageKey, ONE);
        String firstPageStr = String.valueOf(Integer.parseInt(firstPageCfg));
        if (firstPageStr.equals(ZERO)) {
            pageStr = String.valueOf(Integer.parseInt(pg) - 1);
        } else {
            pageStr = String.valueOf(Integer.parseInt(pg) - 1 + Integer.parseInt(getConfig(firstPageKey, ONE)));
        }

        String classUrlKey = getConfig("分类链接").isEmpty() ? "class_url" : "分类链接";
        String cateUrl = getConfig(classUrlKey);

        String pageThreshold = cateUrl.contains(CATE_PG) ? ZERO : ONE;

        if (cateUrl.contains(FIRST_PAGE_MARK)) {
            boolean pageIsZero = pageStr.equals(ZERO);
            boolean firstPageIsZero = firstPageStr.equals(ZERO);
            boolean pageIsOne = pageStr.equals(ONE);
            boolean firstPageIsOne = firstPageStr.equals(ONE);
            if (pageIsZero && firstPageIsZero) {
                cateUrl = cateUrl.split(FIRST_PAGE_REGEX)[1].split(BRACKET_CLOSE)[0];
            } else if (pageIsOne && firstPageIsOne) {
                cateUrl = cateUrl.split(FIRST_PAGE_REGEX)[1].split(BRACKET_CLOSE)[0];
            } else {
                cateUrl = cateUrl.split(FIRST_PAGE_REGEX)[0];
            }
        }

        if (filter && extend != null && extend.size() > 0) {
            for (String key : extend.keySet()) {
                String value = extend.get(key);
                if (value.length() > 0) {
                    String placeholder = BRACE_OPEN + key + BRACE_CLOSE;
                    cateUrl = cateUrl.replace(placeholder, value);
                }
            }
        }

        String url = cateUrl.replaceAll("\\{cateId\\}", tid);

        if (!url.contains(CATE_PG) && !getConfig(classUrlKey).contains(CATE_PG)) {
            int pageInt = Integer.parseInt(pageStr);
            int thresholdInt = Integer.parseInt(pageThreshold);
            if (pageStr.equals(ZERO)) {
                if (pageInt >= thresholdInt) return null;
            } else {
                if (pageInt > thresholdInt) return null;
            }
        }

        url = url.replaceAll("\\{catePg\\}", pageStr);

        // 先收集所有占位符匹配，再统一替换，避免遍历中修改 url 导致混乱
        java.util.regex.Matcher placeholderMatcher = PLACEHOLDER_PATTERN.matcher(url);
        java.util.List<String[]> placeholderMatches = new java.util.ArrayList<>();
        while (placeholderMatcher.find()) {
            String fullMatch = placeholderMatcher.group(0);
            String keyName = fullMatch.replace(BRACE_OPEN, "").replace(BRACE_CLOSE, "");
            placeholderMatches.add(new String[]{fullMatch, keyName});
        }
        for (String[] match : placeholderMatches) {
            url = url.replace(match[0], "");
            url = url.replace(SLASH + match[1] + SLASH, "");
        }

        url = url.replaceAll(TIMESTAMP_KEY, String.valueOf(System.currentTimeMillis() / 1000));
        url = url.replaceAll(TIME_MARK_KEY, String.valueOf(System.currentTimeMillis()));

        if (url.contains(MD5_PREFIX)) {
            ArrayList<String> md5Contents = ParseUtils.regexExtract(url, MD5_PREFIX, MD5_SUFFIX);
            if (!md5Contents.isEmpty()) {
                String md5Content = md5Contents.get(0);
                if (!md5Content.isEmpty()) {
                    String md5Expr = MD5_PREFIX + md5Content + MD5_SUFFIX;
                    String md5Hash = ParseUtils.md5Hex(md5Content);
                    url = url.replace(md5Expr, md5Hash);
                }
            }
        }

        url = url.replaceAll(FULLWIDTH_BRACE_OPEN, BRACE_OPEN);
        url = url.replaceAll(FULLWIDTH_BRACE_CLOSE, BRACE_CLOSE);

        if (url.contains(SEMI_POST)) {
            String baseUrl = url.split("\\?")[0];
            baseUrl = baseUrl.replaceAll(DOUBLE_QMARK, QMARK).trim();
            String queryString = url.split("\\?")[1].split(SEMI)[0];
            queryString = queryString.replaceAll(DOUBLE_QMARK, QMARK).trim();

            if (!queryString.isEmpty()) {
                if (queryString.startsWith(BRACE_OPEN) && queryString.endsWith(BRACE_CLOSE)) {
                    String jsonBody = new JSONObject(queryString).toString();
                    HashMap<String, String> headers = buildShowHeaders(baseUrl);
                    responseHtml = fetchPostJson(baseUrl, jsonBody, this.charset, headers);
                } else {
                    LinkedHashMap<String, String> postData = new LinkedHashMap<>();
                    String[] params = queryString.split(AMP);
                    for (String param : params) {
                        int eqIdx = param.indexOf(EQ);
                        if (eqIdx < 0) continue;
                        String key = param.substring(0, eqIdx);
                        String val = param.substring(eqIdx + 1);
                        postData.put(key, val);
                    }
                    HashMap<String, String> headers = buildShowHeaders(baseUrl);
                    responseHtml = fetchPostForm(baseUrl, postData, this.charset, headers);
                    if (responseHtml != null && responseHtml.contains(VERIFY_TEXT) && checkveriry(responseHtml)) {
                        String verifyType = vertype(responseHtml);
                        responseHtml = handleCaptchaVerify(baseUrl, postData, this.charset, verifyType);
                    }
                }
            } else {
                HashMap<String, String> headers = buildShowHeaders(baseUrl);
                responseHtml = fetchPostForm(baseUrl, null, this.charset, headers);
            }
        } else {
            HashMap<String, String> headers = buildShowHeaders(url);
            responseHtml = fetchGet(url, this.charset, headers);
            if (responseHtml != null && responseHtml.contains(DETECT_TEXT) && responseHtml.contains(BTWAF_TEXT)) {
                responseHtml = handleBtwafVerify(url, responseHtml, SHOW);
            }
            if (responseHtml != null && (responseHtml.contains(SNIFF_HUADONG) || responseHtml.contains(SNIFF_RENJI))) {
                responseHtml = handleSniffVerify(url, responseHtml, SHOW);
            }
            if (responseHtml != null && responseHtml.contains(VERIFY_TEXT) && checkveriry(responseHtml)) {
                String verifyType = vertype(responseHtml);
                responseHtml = handleCaptchaVerify(url, null, this.charset, verifyType);
            }
        }

        if (responseHtml == null) return null;
        responseHtml = decodeHtml(responseHtml);

        String catModeKey = getConfig("分类截取模式").isEmpty() ? "cat_mode" : "分类截取模式";
        String catIsJsoupKey = getConfig("分类片单是否Jsoup写法").isEmpty() ? "cat_is_jsoup" : "分类片单是否Jsoup写法";
        String catjsonTwiceKey = getConfig("分类Json数据二次截取").isEmpty() ? "catjson_twice" : "分类Json数据二次截取";
        String picNeedProxyKey = getConfig("图片是否需要代理").isEmpty() ? "PicNeedProxy" : "图片是否需要代理";

        String catModeVal = getConfig(catModeKey);
        boolean isJsonMode = catModeVal.equals(ZERO) || catModeVal.equals(NO);

        String catIsJsoupVal = getConfig(catIsJsoupKey, ONE);
        boolean catIsJsoup = catIsJsoupVal.equals(ONE) || catIsJsoupVal.equals(YES);

        String picNeedProxyVal = getConfig(picNeedProxyKey);
        boolean picNeedProxy = picNeedProxyVal.equals(ONE) || picNeedProxyVal.equals(YES);

        String catArrRuleKey = getConfig("分类列表数组规则").isEmpty() ? "cat_arr_rule" : "分类列表数组规则";
        String catTitleKey = getConfig("分类片单标题").isEmpty() ? "cat_title" : "分类片单标题";
        String catUrlKey = getConfig("分类片单链接").isEmpty() ? "cat_url" : "分类片单链接";
        String catPicKey = getConfig("分类片单图片").isEmpty() ? "cat_pic" : "分类片单图片";
        String catSubtitleKey = getConfig("分类片单副标题").isEmpty() ? "cat_subtitle" : "分类片单副标题";
        String catPrefixKey = getConfig("分类片单链接加前缀").isEmpty() ? "cat_prefix" : "分类片单链接加前缀";
        String catSuffixKey = getConfig("分类片单链接加后缀").isEmpty() ? "cat_suffix" : "分类片单链接加后缀";

        if (isJsonMode) {
            String catjsonTwiceVal = getConfig(catjsonTwiceKey);
            if (!catjsonTwiceVal.isEmpty() && catjsonTwiceVal.contains(AND_AND)) {
                String[] twiceParts = catjsonTwiceVal.split(AND_AND);
                ArrayList<String> twiceMatches = ParseUtils.regexExtract(responseHtml, twiceParts[0], twiceParts[1]);
                if (!twiceMatches.isEmpty() && !twiceMatches.get(0).isEmpty()) {
                    responseHtml = twiceMatches.get(0);
                }
            }

            JSONObject jsonObject = new JSONObject(responseHtml);
            String jsonPathStr = getConfig(catArrRuleKey, "data");
            String[] jsonPath = jsonPathStr.split("\\.");
            JSONArray items;
            if (jsonPath.length == 1) {
                items = jsonObject.getJSONArray(jsonPath[0]);
            } else if (jsonPath.length == 2) {
                items = jsonObject.getJSONObject(jsonPath[0]).getJSONArray(jsonPath[1]);
            } else if (jsonPath.length == 3) {
                items = jsonObject.getJSONObject(jsonPath[0]).getJSONObject(jsonPath[1]).getJSONArray(jsonPath[2]);
            } else if (jsonPath.length == 4) {
                items = jsonObject.getJSONObject(jsonPath[0]).getJSONObject(jsonPath[1]).getJSONObject(jsonPath[2]).getJSONArray(jsonPath[3]);
            } else {
                items = null;
            }

            if (items != null) {
                for (int i = 0; i < items.length(); i++) {
                    try {
                        JSONObject item = items.getJSONObject(i);
                        String titleNameConfig = getConfig(catTitleKey);
                        String title = item.optString(titleNameConfig).trim();
                        String urlConfig = getConfig(catUrlKey);
                        String urlValue = item.optString(urlConfig).trim();
                        String prefix = getConfig(catPrefixKey, "");
                        String suffix = getConfig(catSuffixKey, "");
                        String vodId = prefix + urlValue + suffix;
                        if (vodId.contains(INPUT_PLACEHOLDER)) {
                            vodId = vodId.replaceAll(INPUT_PLACEHOLDER, urlValue);
                        }
                        String pic = "";
                        String picConfig = getConfig(catPicKey);
                        if (!picConfig.isEmpty()) {
                            if (picConfig.startsWith(HTTP)) {
                                pic = picConfig;
                            } else {
                                pic = item.optString(picConfig).trim();
                            }
                            pic = ParseUtils.urlCombine(url, pic);
                            pic = processPicUrl(pic, url, picNeedProxy);
                        }
                        String subtitleConfig = getConfig(catSubtitleKey);
                        String remarks = item.optString(subtitleConfig).trim();
                        Vod vod = new Vod(title + DOUBLE_DOLLAR + pic + DOUBLE_DOLLAR + vodId, title, pic, remarks);
                        videos.add(vod);
                    } catch (Exception e) {
                        SpiderDebug.log(e);
                        if (debugFlag) {
                            Notify.show("分类解析json出错：" + e.toString());
                        }
                    }
                }
            }
        } else {
            String homeIsJsoupKey = getConfig("首页片单是否Jsoup写法").isEmpty() ? "home_is_jsoup" : "首页片单是否Jsoup写法";
            String homeTitleKey = getConfig("首页片单标题").isEmpty() ? "home_title" : "首页片单标题";
            String homeUrlKey = getConfig("首页片单链接").isEmpty() ? "home_url" : "首页片单链接";
            String homePicKey = getConfig("首页片单图片").isEmpty() ? "home_pic" : "首页片单图片";
            String homeSubtitleKey = getConfig("首页片单副标题").isEmpty() ? "home_subtitle" : "首页片单副标题";
            String homePrefixKey = getConfig("首页片单链接加前缀").isEmpty() ? "home_prefix" : "首页片单链接加前缀";
            String homeSuffixKey = getConfig("首页片单链接加后缀").isEmpty() ? "home_suffix" : "首页片单链接加后缀";

            String homeIsJsoupVal = getConfig(homeIsJsoupKey, YES);
            boolean homeIsJsoup = homeIsJsoupVal.equals(YES) || getConfig(homeIsJsoupKey, ONE).equals(ONE);

            org.jsoup.nodes.Document doc = org.jsoup.Jsoup.parse(responseHtml);

            // 使用 分类列表数组规则 查找列表元素（与 homeVideoContent 一致）
            String arrRule = getConfig(catArrRuleKey);
            if (arrRule.isEmpty() && catIsJsoup == homeIsJsoup) {
                arrRule = getConfig("首页列表数组规则");
                if (arrRule.isEmpty()) arrRule = getConfig("home_arr_rule");
            }

            org.jsoup.nodes.Element baseElement = doc;
            String[] arrRuleParts = arrRule.split(AND_AND);
            baseElement = getTrueElement(arrRuleParts[0], doc);
            for (int j = 1; j < arrRuleParts.length - 1; j++) {
                // 容错：中间规则不匹配时跳过，保持上一个非空元素继续搜索
                org.jsoup.nodes.Element next = getTrueElement(arrRuleParts[j], baseElement);
                if (next != null) {
                    baseElement = next;
                }
            }
            String lastSelector = arrRuleParts[arrRuleParts.length - 1];
            org.jsoup.select.Elements elements = selectElements(baseElement, lastSelector);

            for (int i = 0; i < elements.size(); i++) {
                try {
                    org.jsoup.nodes.Element itemElement = elements.get(i);

                    String titleCfg = getConfig(catTitleKey);
                    if (titleCfg.isEmpty() && catIsJsoup == homeIsJsoup) {
                        titleCfg = getConfig(homeTitleKey);
                    }
                    String title;
                    if (catIsJsoup) {
                        title = getTextByRule(itemElement, titleCfg).trim();
                    } else {
                        String outerHtml = itemElement.outerHtml();
                        String[] titleParts = titleCfg.split(AND_AND);
                        ArrayList<String> titleMatches = ParseUtils.regexExtract(outerHtml, titleParts[0], titleParts[1]);
                        title = cleanHtml(titleMatches.get(0));
                    }

                    String urlCfg = getConfig(catUrlKey);
                    if (urlCfg.isEmpty() && catIsJsoup == homeIsJsoup) {
                        urlCfg = getConfig(homeUrlKey);
                    }
                    String urlValue;
                    if (catIsJsoup) {
                        urlValue = getTextByRule(itemElement, urlCfg).trim();
                    } else {
                        String outerHtml = itemElement.outerHtml();
                        String[] urlParts = urlCfg.split(AND_AND);
                        ArrayList<String> urlMatches = ParseUtils.regexExtract(outerHtml, urlParts[0], urlParts[1]);
                        urlValue = cleanHtml(urlMatches.get(0));
                    }
                    if (urlValue.contains(PG_URL)) {
                        urlValue = urlValue.replace(PG_URL, url).replaceAll(SINGLE_QUOTE, "");
                    }
                    String prefix = getConfig(catPrefixKey, "");
                    String suffix = getConfig(catSuffixKey, "");
                    String vodId = prefix + urlValue + suffix;
                    if (vodId.contains(INPUT_PLACEHOLDER)) {
                        vodId = vodId.replaceAll(INPUT_PLACEHOLDER, urlValue);
                    }

                    String pic = "";
                    String picCfg = getConfig(catPicKey);
                    if (picCfg.isEmpty() && catIsJsoup == homeIsJsoup) {
                        picCfg = getConfig(homePicKey);
                    }
                    if (!picCfg.isEmpty()) {
                        if (catIsJsoup) {
                            pic = getTextByRule(itemElement, picCfg).trim();
                        } else {
                            String outerHtml = itemElement.outerHtml();
                            String[] picParts = picCfg.split(AND_AND);
                            ArrayList<String> picMatches = ParseUtils.regexExtract(outerHtml, picParts[0], picParts[1]);
                            pic = picMatches.get(0);
                        }
                        if (pic.contains(URL_FUNC)) {
                            pic = pic.replaceAll(HTML_ENTITY_QUOT, "");
                            String[] urlFuncParts = pic.split(URL_FUNC_REGEX);
                            if (urlFuncParts.length > 1) {
                                String afterUrl = urlFuncParts[1];
                                if (afterUrl.contains(SLASH)) {
                                    pic = afterUrl.split(CLOSE_PAREN_REGEX)[0].replaceAll(QUOTE_REGEX, "");
                                }
                            }
                        }
                        pic = ParseUtils.urlCombine(url, pic);
                        pic = processPicUrl(pic, url, picNeedProxy);
                    }

                    String subtitleCfg = getConfig(catSubtitleKey);
                    if (subtitleCfg.isEmpty() && catIsJsoup == homeIsJsoup) {
                        subtitleCfg = getConfig(homeSubtitleKey);
                    }
                    String remarks = "";
                    if (!subtitleCfg.isEmpty()) {
                        if (catIsJsoup) {
                            remarks = getTextByRule(itemElement, subtitleCfg).trim();
                        } else {
                            String outerHtml = itemElement.outerHtml();
                            String[] subtitleParts = subtitleCfg.split(AND_AND);
                            ArrayList<String> subtitleMatches = ParseUtils.regexExtract(outerHtml, subtitleParts[0], subtitleParts[1]);
                            remarks = cleanHtml(subtitleMatches.get(0));
                        }
                        if (subtitleCfg.contains(REPLACE_PREFIX)) {
                            remarks = applyReplace(remarks, subtitleCfg);
                        }
                    }

                    Vod vod = new Vod(title + DOUBLE_DOLLAR + pic + DOUBLE_DOLLAR + vodId, title, pic, remarks);
                    videos.add(vod);
                } catch (Exception e) {
                    SpiderDebug.log(e);
                    if (debugFlag) {
                        Notify.show("分类解析html出错：" + e.toString());
                    }
                }
            }
        }

        if (videos.size() < 1) {
            return null;
        }
        return Result.string(Integer.parseInt(pageStr), MAX_INT, videos.size(), MAX_INT, videos);
    } catch (Exception e) {
        SpiderDebug.log(e);
        if (debugFlag) {
            Notify.show("分类网页全局出错：" + e.toString());
        }
        return null;
    }
}

// === METHOD: searchContent (smali L25662-L25678) ===
@Override
public String searchContent(String wd, boolean filter) throws Exception {
    return parseSearchContent(wd, "1");
}

// === METHOD: searchContent overload (smali L25679-L25689) ===
@Override
public String searchContent(String wd, boolean filter, String pg) throws Exception {
    return parseSearchContent(wd, pg);
}

// === METHOD: parseSearchContent (smali L9194-L12713) ===
@SuppressWarnings("CharsetObjectCanBeUsed")
private String parseSearchContent(String keyword, String page) {
    final String WD_REGEX = "\\{wd\\}";
    final String SEARCH_PG = "{SearchPg}";
    final String SEARCH_PG_REGEX = "\\{SearchPg\\}";
    final String ZERO = "0";
    final String ONE = "1";
    final String EMPTY = "";
    final String DOUBLE_DOLLAR = "$$$";
    final String INPUT_PLACEHOLDER = "'input'";
    final String HTTP = "http";
    final String AMP = "&";
    final String EQUALS = "=";
    final String SEMICOLON = ";";
    final String POST_SUFFIX = ";post";
    final String MD5_PREFIX = "md5(";
    final String RPAREN = ")";
    final String LBRACKET = "\\[firstPage=";
    final String RBRACKET = "\\]";
    final String FIRST_PAGE_EQ = "firstPage=";
    final String FW_LBRACE = "｛";
    final String FW_RBRACE = "｝";
    final String LBRACE = "{";
    final String RBRACE = "}";
    final String DOT_REGEX = "\\.";
    final String AMP_AMP = "&&";
    final String PG_URL = "PG_URL";
    final String SINGLE_QUOTE = "'";
    final String URL_FUNC = "url(";
    final String URL_FUNC_REGEX = "url\\(";
    final String QUOT_ENTITY = "\\&quot;";
    final String RPAREN_REGEX = "\\)";
    final String QUOTES_REGEX = "['\"]";
    final String REPLACE_PREFIX = "\\[替换:";
    final String REPLACE_MARKER = "[替换";
    final String INPUT_VERIFY = "输入验证码";
    final String INPUT_CORRECT_VERIFY = "输入正确的验证码";
    final String RATE_LIMIT = "不要频繁操作，搜索时间间隔为";
    final String DETECTING = "检测中";
    final String BTWAF = "btwaf";
    final String HUADONG = "/huadong_";
    final String RENJI = "/renji_";
    final String SEARCH_MODE_STR = "search";
    final String YES = "是";
    final String NO = "否";
    final String TS_PLACEHOLDER = "时间戳";
    final String TS_MARK = "时间标";
    final String LIST_KEY = "list";
    final String NAME_DEFAULT = "name";
    final String ID_DEFAULT = "id";
    final String PIC_DEFAULT = "pic";

    try {
        ensureSiteConfig();

        String charsetKey = getConfig("网页编码格式").isEmpty() ? "Coding_format" : "网页编码格式";
        String searchUrlKey = getConfig("搜索链接").isEmpty() ? "search_url" : "搜索链接";
        String postDataKey = getConfig("POST请求数据").isEmpty() ? "sea_PtBody" : "POST请求数据";
        String firstPageKey = getConfig("搜索起始页码").isEmpty() ? "sea_firstpage" : "搜索起始页码";

        this.charset = getConfig(charsetKey, "UTF-8");

        String firstPageStr = String.valueOf(Integer.parseInt(getConfig(firstPageKey, ONE)));
        String pageStr;
        if (firstPageStr.equals(ZERO)) {
            pageStr = String.valueOf(Integer.parseInt(page) - 1);
        } else {
            pageStr = String.valueOf(Integer.parseInt(page) - 1 + Integer.parseInt(getConfig(firstPageKey, ONE)));
        }

        String searchUrl = getConfig(searchUrlKey);

        boolean hasSearchPg = searchUrl.contains(SEARCH_PG);
        String pageThreshold = hasSearchPg ? ZERO : ONE;

        if (!hasSearchPg) {
            int pageInt = Integer.parseInt(pageStr);
            int thresholdInt = Integer.parseInt(pageThreshold);
            if (pageStr.equals(ZERO)) {
                if (pageInt >= thresholdInt) return null;
            } else {
                if (pageInt > thresholdInt) return null;
            }
        }

        if (searchUrl.contains(FIRST_PAGE_EQ)) {
            if ((pageStr.equals(ZERO) && firstPageStr.equals(ZERO))
                    || (pageStr.equals(ONE) && firstPageStr.equals(ONE))) {
                searchUrl = searchUrl.split(LBRACKET)[1].split(RBRACKET)[0];
            } else {
                searchUrl = searchUrl.split(LBRACKET)[0];
            }
        }

        String encodedKey = URLEncoder.encode(keyword, this.charset);
        String url = searchUrl.replaceAll(WD_REGEX, encodedKey);
        url = url.replaceAll(SEARCH_PG_REGEX, pageStr);

        String baseUrl = url.split(SEMICOLON)[0];

        String unixTs = String.valueOf(System.currentTimeMillis() / 1000L);
        String milliTs = String.valueOf(System.currentTimeMillis());
        baseUrl = baseUrl.replaceAll(TS_PLACEHOLDER, unixTs);
        baseUrl = baseUrl.replaceAll(TS_MARK, milliTs);

        if (baseUrl.contains(MD5_PREFIX)) {
            ArrayList<String> md5Contents = ParseUtils.regexExtract(baseUrl, MD5_PREFIX, RPAREN);
            String md5Content = md5Contents.get(0);
            String md5Expr = MD5_PREFIX + md5Content + RPAREN;
            String md5Hash = ParseUtils.md5Hex(md5Content);
            baseUrl = baseUrl.replace(md5Expr, md5Hash);
        }

        boolean isPost = url.contains(POST_SUFFIX);
        String response;

        if (isPost) {
            String postBody = getConfig(postDataKey);
            postBody = postBody.replaceAll(WD_REGEX, keyword);
            postBody = postBody.replaceAll(SEARCH_PG_REGEX, pageStr);
            postBody = postBody.trim();
            postBody = postBody.replaceAll(FW_LBRACE, LBRACE);
            postBody = postBody.replaceAll(FW_RBRACE, RBRACE);

            if (postBody.isEmpty()) {
                HashMap<String, String> headers = buildShowHeaders(baseUrl);
                response = fetchPostForm(baseUrl, null, this.charset, headers);
            } else if (postBody.startsWith(LBRACE) && postBody.endsWith(RBRACE)) {
                String jsonBody = new JSONObject(postBody).toString();
                HashMap<String, String> headers = buildShowHeaders(baseUrl);
                response = fetchPostJson(baseUrl, jsonBody, this.charset, headers);
            } else {
                LinkedHashMap<String, String> params = new LinkedHashMap<>();
                String[] pairs = postBody.split(AMP);
                for (String pair : pairs) {
                    int eqIdx = pair.indexOf(EQUALS);
                    if (eqIdx < 0) continue;
                    String formKey = pair.substring(0, eqIdx);
                    String formVal = pair.substring(eqIdx + 1);
                    params.put(formKey, formVal);
                }
                HashMap<String, String> headers = buildShowHeaders(baseUrl);
                response = fetchPostForm(baseUrl, params, this.charset, headers);

                if (response != null && (response.contains(INPUT_VERIFY) || response.contains(INPUT_CORRECT_VERIFY))) {
                    if (checkveriry(response)) {
                        String verifyType = vertype(response);
                        response = handleCaptchaVerify(baseUrl, params, SEARCH_MODE_STR, verifyType);
                    }
                }

                if (response != null && response.contains(RATE_LIMIT)) {
                    TimeUnit.SECONDS.sleep(6);
                    headers = buildShowHeaders(baseUrl);
                    response = fetchPostForm(baseUrl, params, this.charset, headers);
                }
            }
        } else {
            HashMap<String, String> headers = buildShowHeaders(baseUrl);
            response = fetchGet(baseUrl, this.charset, headers);

            if (response != null && response.contains(DETECTING) && response.contains(BTWAF)) {
                response = handleBtwafVerify(baseUrl, response, SEARCH_MODE_STR);
            }

            if (response != null && (response.contains(HUADONG) || response.contains(RENJI))) {
                response = handleSniffVerify(baseUrl, response, SEARCH_MODE_STR);
            }

            if (response != null && (response.contains(INPUT_VERIFY) || response.contains(INPUT_CORRECT_VERIFY))) {
                if (checkveriry(response)) {
                    String verifyType = vertype(response);
                    response = handleCaptchaVerify(baseUrl, null, SEARCH_MODE_STR, verifyType);
                }
            }

            if (response != null && response.contains(RATE_LIMIT)) {
                TimeUnit.SECONDS.sleep(6);
                headers = buildShowHeaders(baseUrl);
                response = fetchGet(baseUrl, this.charset, headers);
            }
        }

        response = decodeHtml(response);

        String searchModeKey = getConfig("搜索截取模式").isEmpty() ? "search_mode" : "搜索截取模式";
        String searchModeVal = getConfig(searchModeKey);
        boolean jsonMode = searchModeVal.equals(ZERO) || searchModeVal.equals(NO);

        String isJsoupKey = getConfig("搜索片单是否Jsoup写法").isEmpty() ? "sea_is_jsoup" : "搜索片单是否Jsoup写法";
        String isJsoupVal = getConfig(isJsoupKey, ONE);
        boolean isJsoup = isJsoupVal.equals(ONE) || getConfig(isJsoupKey, YES).equals(YES);

        String picProxyKey = getConfig("图片是否需要代理").isEmpty() ? "PicNeedProxy" : "图片是否需要代理";
        String picProxyVal = getConfig(picProxyKey);
        boolean picNeedProxy = picProxyVal.equals(ONE) || picProxyVal.equals(YES);

        String jsonTwiceKey = getConfig("搜索Json数据二次截取").isEmpty() ? "seajson_twice" : "搜索Json数据二次截取";

        String listArrKey = getConfig("搜索列表数组规则").isEmpty() ? "sea_arr_rule" : "搜索列表数组规则";
        String picKey = getConfig("搜索片单图片").isEmpty() ? "sea_pic" : "搜索片单图片";
        String titleKey = getConfig("搜索片单标题").isEmpty() ? "sea_title" : "搜索片单标题";
        String urlKey = getConfig("搜索片单链接").isEmpty() ? "sea_url" : "搜索片单链接";
        String subtitleKey = getConfig("搜索片单副标题").isEmpty() ? "sea_subtitle" : "搜索片单副标题";
        String prefixKey = getConfig("搜索片单链接加前缀").isEmpty() ? "search_prefix" : "搜索片单链接加前缀";
        String suffixKey = getConfig("搜索片单链接加后缀").isEmpty() ? "search_suffix" : "搜索片单链接加后缀";

        List<Vod> listArray = new ArrayList<>();

        String jsonTwice = getConfig(jsonTwiceKey);
        if (!jsonTwice.isEmpty() && jsonTwice.contains(AMP_AMP)) {
            String[] twiceParts = jsonTwice.split(AMP_AMP);
            String twicePre = twiceParts[0];
            String twiceSuf = twiceParts[1];
            ArrayList<String> twiceResult = ParseUtils.regexExtract(response, twicePre, twiceSuf);
            response = twiceResult.get(0);
        }

        if (jsonMode) {
            JSONObject jsonObj = new JSONObject(response);
            String listRule = getConfig(listArrKey, LIST_KEY);
            String[] pathParts = listRule.split(DOT_REGEX);

            JSONArray items;
            if (pathParts.length == 1) {
                items = jsonObj.getJSONArray(pathParts[0]);
            } else if (pathParts.length == 2) {
                items = jsonObj.getJSONObject(pathParts[0]).getJSONArray(pathParts[1]);
            } else if (pathParts.length == 3) {
                items = jsonObj.getJSONObject(pathParts[0])
                        .getJSONObject(pathParts[1]).getJSONArray(pathParts[2]);
            } else if (pathParts.length == 4) {
                items = jsonObj.getJSONObject(pathParts[0])
                        .getJSONObject(pathParts[1])
                        .getJSONObject(pathParts[2]).getJSONArray(pathParts[3]);
            } else {
                items = null;
            }

            if (items != null) {
                String pic = EMPTY;
                for (int i = 0; i < items.length(); i++) {
                    try {
                        JSONObject item = items.getJSONObject(i);

                        String name = item.optString(getConfig(titleKey, NAME_DEFAULT)).trim();
                        String id = item.optString(getConfig(urlKey, ID_DEFAULT)).trim();
                        String vodUrl = getConfig(prefixKey, EMPTY) + id + getConfig(suffixKey, EMPTY);

                        if (vodUrl.contains(INPUT_PLACEHOLDER)) {
                            vodUrl = vodUrl.replaceAll(INPUT_PLACEHOLDER, id);
                        }

                        String picConfig = getConfig(picKey);
                        if (!picConfig.isEmpty()) {
                            if (picConfig.startsWith(HTTP)) {
                                pic = picConfig;
                            } else {
                                pic = item.optString(getConfig(picKey, PIC_DEFAULT)).trim();
                                pic = ParseUtils.urlCombine(baseUrl, pic);
                            }
                            if (picNeedProxy) {
                                pic = processPicUrl(pic, baseUrl, picNeedProxy);
                            }
                        }

                        String subtitle = item.optString(getConfig(subtitleKey)).trim();

                        if (name.contains(keyword)) {
                            Vod vodObj = new Vod(name + DOUBLE_DOLLAR + pic + DOUBLE_DOLLAR + vodUrl, name, pic, subtitle);
                            listArray.add(vodObj);
                        }
                    } catch (Exception e) {
                        SpiderDebug.log(e);
                        if (debugFlag) {
                            Notify.show("搜索解析json区域出错：" + e.toString());
                        }
                    }
                }
            }
        } else {
            org.jsoup.nodes.Document doc = org.jsoup.Jsoup.parse(response);

            String prefix = EMPTY;
            String prefixConfig = getConfig(prefixKey);
            if (prefixConfig.contains(AMP_AMP)) {
                prefix = getTextByRule(doc, prefixConfig);
                prefix = prefix.replace(PG_URL, baseUrl);
            } else {
                prefix = prefixConfig;
            }
            if (prefix.contains(PG_URL)) {
                prefix = prefix.replace(PG_URL, baseUrl);
                prefix = prefix.replaceAll(SINGLE_QUOTE, EMPTY);
            }

            String suffix = EMPTY;
            String suffixConfig = getConfig(suffixKey);
            if (suffixConfig.contains(AMP_AMP)) {
                suffix = getTextByRule(doc, suffixConfig);
            } else {
                suffix = suffixConfig;
            }

            String listRule = getConfig(listArrKey);
            String[] ruleParts = listRule.split(AMP_AMP);
            org.jsoup.nodes.Element element = getTrueElement(ruleParts[0], doc);
            for (int j = 1; j < ruleParts.length - 1; j++) {
                // 容错：中间规则不匹配时跳过，保持上一个非空元素继续搜索
                org.jsoup.nodes.Element next = getTrueElement(ruleParts[j], element);
                if (next != null) {
                    element = next;
                }
            }
            String lastRule = ruleParts[ruleParts.length - 1];
            org.jsoup.select.Elements elements = selectElements(element, lastRule);

            String pic = EMPTY;
            for (int i = 0; i < elements.size(); i++) {
                try {
                    org.jsoup.nodes.Element item = elements.get(i);

                    String titleRule = getConfig(titleKey);
                    String name;
                    if (isJsoup) {
                        name = getTextByRule(item, titleRule).trim();
                    } else {
                        String html = item.outerHtml();
                        String[] titleParts = titleRule.split(AMP_AMP);
                        ArrayList<String> titleMatches = ParseUtils.regexExtract(html, titleParts[0], titleParts[1]);
                        name = cleanHtml(titleMatches.get(0));
                    }

                    String picConfig = getConfig(picKey);
                    if (!picConfig.isEmpty()) {
                        if (picConfig.startsWith(HTTP)) {
                            pic = picConfig;
                        } else {
                            if (isJsoup) {
                                pic = getTextByRule(item, picConfig).trim();
                            } else {
                                String html = item.outerHtml();
                                String[] picParts = picConfig.split(AMP_AMP);
                                ArrayList<String> picMatches = ParseUtils.regexExtract(html, picParts[0], picParts[1]);
                                pic = picMatches.get(0);
                            }
                            pic = ParseUtils.urlCombine(baseUrl, pic);
                        }

                        if (pic.contains(URL_FUNC)) {
                            pic = pic.replaceAll(QUOT_ENTITY, EMPTY);
                            String[] urlParts = pic.split(URL_FUNC_REGEX);
                            if (urlParts.length > 1) {
                                String afterUrl = urlParts[1];
                                if (afterUrl.contains(RPAREN)) {
                                    pic = afterUrl.split(RPAREN_REGEX)[0];
                                    pic = pic.replaceAll(QUOTES_REGEX, EMPTY);
                                }
                            }
                        }

                        if (picNeedProxy) {
                            pic = processPicUrl(pic, baseUrl, picNeedProxy);
                        }
                    }

                    String subtitle = EMPTY;
                    String subtitleRule = getConfig(subtitleKey);
                    if (!subtitleRule.isEmpty()) {
                        if (isJsoup) {
                            subtitle = getTextByRule(item, subtitleRule).trim();
                        } else {
                            String html = item.outerHtml();
                            String[] subParts = subtitleRule.split(AMP_AMP);
                            ArrayList<String> subMatches = ParseUtils.regexExtract(html, subParts[0], subParts[1]);
                            subtitle = cleanHtml(subMatches.get(0));
                        }
                        if (subtitleRule.contains(REPLACE_PREFIX)) {
                            subtitle = applyReplace(subtitle, subtitleRule);
                        }
                    }

                    String urlRule = getConfig(urlKey);
                    String urlRulePart = urlRule.split(REPLACE_PREFIX)[0];
                    String urlValue;
                    if (isJsoup) {
                        urlValue = getTextByRule(item, urlRulePart).trim();
                    } else {
                        String html = item.outerHtml();
                        String[] urlRuleParts = urlRulePart.split(AMP_AMP);
                        ArrayList<String> urlMatches = ParseUtils.regexExtract(html, urlRuleParts[0], urlRuleParts[1]);
                        urlValue = cleanHtml(urlMatches.get(0));
                    }

                    if (urlValue.contains(PG_URL)) {
                        urlValue = urlValue.replace(PG_URL, baseUrl).replaceAll(SINGLE_QUOTE, EMPTY);
                    }

                    String fullUrlRule = getConfig(urlKey);
                    if (fullUrlRule.contains(REPLACE_MARKER)) {
                        urlValue = applyReplace(urlValue, fullUrlRule);
                    }

                    String fullUrl = prefix + urlValue + suffix;

                    if (fullUrl.contains(INPUT_PLACEHOLDER)) {
                        fullUrl = fullUrl.replaceAll(INPUT_PLACEHOLDER, urlValue);
                    }

                    if (name.contains(keyword)) {
                        Vod vodObj = new Vod(name + DOUBLE_DOLLAR + pic + DOUBLE_DOLLAR + fullUrl, name, pic, subtitle);
                        listArray.add(vodObj);
                    }
                } catch (Exception e) {
                    SpiderDebug.log(e);
                    if (debugFlag) {
                        Notify.show("搜索解析html区域出错：" + e.toString());
                    }
                }
            }
        }

        return Result.string(listArray);

    } catch (Exception e) {
        SpiderDebug.log(e);
        if (debugFlag) {
            Notify.show("搜索全局出错：" + e.toString());
        }
        return EMPTY;
    }
}

// === METHOD: detailContent (smali L12916-L17281) ===
@Override
public String detailContent(List<String> ids) throws Exception {
    String emptyStr = "";
    String sepAmp = "&&";
    String sepDollar = "$";
    String sepDoubleDollar = "$$$";
    String sepHash = "#";

    try {
        ensureSiteConfig();

        String charsetKey = getConfig("网页编码格式").isEmpty() ? "Coding_format" : "网页编码格式";
        charset = getConfig(charsetKey, "UTF-8");

        String[] parts = ids.get(0).split("\\$\\$\\$");
        if (parts.length < 3) {
            throw new Exception("ids 格式不标准，期望 vodName$vodPic$url 分隔符 $$$");
        }
        String url = parts[2].trim();
        String vodName = parts[0];
        String vodId = parts[0];

        // 相对 URL 补全：如果 url 不以 http:// 或 https:// 开头（且非 P2P 链接），
        // 从配置的"首页推荐链接"/"搜索链接"中提取站点 host 补全为绝对 URL。
        // 这解决了 prefix 为空导致 vod_id 为相对路径、detailContent 无法请求详情页的问题。
        if (!url.startsWith("http://") && !url.startsWith("https://") && !isP2PUrl(url) && !url.contains(";post")) {
            String siteUrl = getConfig("首页推荐链接");
            if (siteUrl.isEmpty()) {
                siteUrl = getConfig("搜索链接");
            }
            if (!siteUrl.isEmpty()) {
                String tempUrl = siteUrl.split("\\{")[0].split("\\?")[0];
                if (tempUrl.startsWith("https://")) {
                    String host = tempUrl.substring(8).split("/")[0];
                    url = "https://" + host + (url.startsWith("/") ? url : "/" + url);
                } else if (tempUrl.startsWith("http://")) {
                    String host = tempUrl.substring(7).split("/")[0];
                    url = "http://" + host + (url.startsWith("/") ? url : "/" + url);
                }
            }
        }

        if (ALIYUN_PATTERN.matcher(url).find()) {
            aliyunFlag = true;
            ArrayList<String> aliyunList = new ArrayList<>();
            aliyunList.add(url.trim());
            String aliyunResult = pushAgent.detailContent(aliyunList);
            if (aliyunResult.length() > 0) {
                JSONObject aliyunJson = new JSONObject(aliyunResult);
                JSONArray aliyunListArr = aliyunJson.optJSONArray("list");
                JSONObject aliyunVod = aliyunListArr.getJSONObject(0);
                aliyunVod.put("vod_id", ids.get(0));
                return aliyunJson.toString();
            }
            return aliyunResult;
        }

        String forcePlayKey = getConfig("链接是否直接播放").isEmpty() ? "force_play" : "链接是否直接播放";
        String listArrKey = getConfig("播放列表数组规则").isEmpty() ? "list_arr_rule" : "播放列表数组规则";
        String epiArrKey = getConfig("选集列表数组规则").isEmpty() ? "epi_arr_rule" : "选集列表数组规则";
        String epiReverseKey = getConfig("是否反转选集序列").isEmpty() ? "epi_reverse" : "是否反转选集序列";

        boolean forcePlay = getConfig(forcePlayKey).equals("1") || getConfig(forcePlayKey).equals("是");

        ArrayList<String> playUrlList = new ArrayList<>();
        ArrayList<String> playFromList = new ArrayList<>();

        if (forcePlay) {
            playFromList.add(vodId);
            playUrlList.add(new StringBuilder().append(vodId).append(sepDollar).append(url).toString());

            String playFrom = TextUtils.join(sepDoubleDollar, playFromList);
            String playUrl = TextUtils.join(sepDoubleDollar, playUrlList);
            Vod vod = new Vod(ids.get(0), vodName, emptyStr);
            vod.setVodPlayFrom(playFrom);
            vod.setVodPlayUrl(playUrl);
            return Result.string(vod);
        }

        String html;
        if (url.contains(";post")) {
            String postUrl = url.split("\\?")[0].replaceAll("？？", "?").trim();
            String postDataStr = url.split("\\?")[1].split(";")[0].replaceAll("？？", "?").trim();

            if (!postDataStr.isEmpty()) {
                if (postDataStr.startsWith("{") && postDataStr.endsWith("}")) {
                    JSONObject postJson = new JSONObject(postDataStr);
                    String postBody = postJson.toString();
                    html = fetchPostJson(postUrl, postBody, charset, buildShowHeaders(postUrl));
                } else {
                    LinkedHashMap<String, String> postParams = new LinkedHashMap<>();
                    String[] pairs = postDataStr.split("&");
                    for (String pair : pairs) {
                        int eqIdx = pair.indexOf("=");
                        if (eqIdx < 0) continue;
                        String key = pair.substring(0, eqIdx);
                        String val = pair.substring(eqIdx + 1);
                        postParams.put(key, val);
                    }
                    html = fetchPostForm(postUrl, postParams, charset, buildShowHeaders(postUrl));
                }
            } else {
                html = fetchPostForm(postUrl, null, charset, buildShowHeaders(postUrl));
            }
        } else {
            html = fetchGet(url, charset, buildShowHeaders(url));

            String verifyType = "show";
            if (html != null && html.contains("检测中") && html.contains("btwaf")) {
                html = handleBtwafVerify(url, html, verifyType);
            }
            if (html != null && (html.contains("/huadong_") || html.contains("/renji_"))) {
                html = handleSniffVerify(url, html, verifyType);
            }
            if (html != null && html.contains("输入验证码") && checkveriry(html)) {
                String vType = vertype(html);
                html = handleCaptchaVerify(url, null, verifyType, vType);
            }
        }

        html = decodeHtml(html);
        org.jsoup.nodes.Document doc = org.jsoup.Jsoup.parse(html);

        String epiurlPrefixKey = getConfig("选集链接加前缀").isEmpty() ? "epiurl_prefix" : "选集链接加前缀";
        String epiurlPrefix = getConfig(epiurlPrefixKey);

        String epiurlSuffixKey = getConfig("选集链接加后缀").isEmpty() ? "epiurl_suffix" : "选集链接加后缀";
        String epiurlSuffix = getConfig(epiurlSuffixKey);

        String epiJsoupKey = getConfig("选集标题链接是否Jsoup写法").isEmpty() ? "epi_is_jsoup" : "选集标题链接是否Jsoup写法";
        // 默认 jsoup 写法（与 cat_is_jsoup/home_is_jsoup 一致，注释约定 "1为jsoup写法(默认)"）
        String epiJsoupRule = getConfig(epiJsoupKey, "1");

        String epiTitleKey = getConfig("选集标题").isEmpty() ? "epi_title" : "选集标题";
        String epiTitleRule = getConfig(epiTitleKey);

        String epiUrlKey = getConfig("选集链接").isEmpty() ? "epi_url" : "选集链接";
        String epiUrlRule = getConfig(epiUrlKey);

        boolean epiIsJsoup = epiJsoupRule.equals("1") || epiJsoupRule.equals("是");

        String prefixVal = epiurlPrefix;
        if (prefixVal.contains("&&")) {
            prefixVal = getTextByRule(doc, prefixVal).replace("PG_URL", url);
        }
        if (prefixVal.contains("PG_URL")) {
            prefixVal = prefixVal.replace("PG_URL", url).replaceAll("'", emptyStr);
        }

        String suffixVal = epiurlSuffix;
        if (suffixVal.contains("&&")) {
            suffixVal = getTextByRule(doc, suffixVal);
        }

        String listRule = getConfig(listArrKey);
        String[] ruleParts = listRule.split("&&");
        org.jsoup.select.Elements playlistElems;
        if (ruleParts.length == 1) {
            // 单段规则：直接在 doc 中搜索所有匹配元素
            playlistElems = selectElements(doc, ruleParts[0]);
        } else {
            // 多段规则：先导航到容器，再在容器内搜索
            org.jsoup.nodes.Element containerElem = getTrueElement(ruleParts[0], doc);
            for (int ri = 1; ri < ruleParts.length - 1; ri++) {
                // 容错：如果中间规则不匹配（如 .content 容器不存在），跳过该规则，
                // 保持上一个非空元素作为容器继续搜索，避免整个线路解析失败。
                org.jsoup.nodes.Element next = getTrueElement(ruleParts[ri], containerElem);
                if (next != null) {
                    containerElem = next;
                }
            }
            playlistElems = selectElements(containerElem, ruleParts[ruleParts.length - 1]);
        }

        // 选集数组规则：用于在每个线路容器内选取选集元素
        String epiArrRule = getConfig(epiArrKey);
        boolean isJsoupMode = epiIsJsoup;
        boolean shouldReverse = getConfig(epiReverseKey).equals("1") || getConfig(epiReverseKey).equals("是");

        // 外层循环：遍历每条线路（list_arr_rule 选出的播放列表容器）
        for (int pli = 0; pli < playlistElems.size(); pli++) {
            org.jsoup.nodes.Element playlistElem = playlistElems.get(pli);

            // 内层选集定位：用 epi_arr_rule 在当前线路容器内选集
            org.jsoup.select.Elements episodeElems;
            if (epiArrRule != null && !epiArrRule.isEmpty() && epiArrRule.contains("&&")) {
                String[] epiRuleParts = epiArrRule.split("&&");
                org.jsoup.nodes.Element epiNode = getTrueElement(epiRuleParts[0], playlistElem);
                for (int eri = 1; eri < epiRuleParts.length - 1; eri++) {
                    // 容错：中间规则不匹配时跳过，保持上一个非空元素继续搜索
                    org.jsoup.nodes.Element next = getTrueElement(epiRuleParts[eri], epiNode);
                    if (next != null) {
                        epiNode = next;
                    }
                }
                episodeElems = selectElements(epiNode, epiRuleParts[epiRuleParts.length - 1]);
            } else if (epiArrRule != null && !epiArrRule.isEmpty()) {
                episodeElems = selectElements(playlistElem, epiArrRule);
            } else {
                // epi_arr_rule 未配置时将线路容器自身作为单集
                episodeElems = new org.jsoup.select.Elements();
                episodeElems.add(playlistElem);
            }

            ArrayList<String> episodeUrls = new ArrayList<>();

            // 内层循环：遍历每个选集
            for (int ei = 0; ei < episodeElems.size(); ei++) {
                org.jsoup.nodes.Element episodeElem = episodeElems.get(ei);

                String epiTitle;
                if (isJsoupMode) {
                    epiTitle = getTextByRule(episodeElem, epiTitleRule);
                } else {
                    String elemHtml = episodeElem.outerHtml();
                    String[] titleRuleParts = epiTitleRule.split("&&");
                    String pre = titleRuleParts[0];
                    String suf = titleRuleParts.length > 1 ? titleRuleParts[1] : "";
                    ArrayList<String> titleMatches = ParseUtils.regexExtract(elemHtml, pre, suf);
                    epiTitle = cleanHtml(titleMatches.get(0));
                }

                // 选集标题后处理：如果标题本身是 ed2k/magnet 链接，从中提取文件名作为标题
                // （原 smali 14420-14513 行逻辑，仅对标题做提取，链接保留完整 ed2k/magnet 地址）
                if (epiTitle != null && epiTitle.startsWith("ed2k:")) {
                    String decoded = URLDecoder.decode(epiTitle);
                    Matcher titleEd2kMatcher = ED2K_PATTERN.matcher(decoded);
                    if (titleEd2kMatcher.find()) {
                        epiTitle = titleEd2kMatcher.group(1);
                    }
                }
                if (epiTitle != null && epiTitle.startsWith("magnet:")) {
                    String decoded = URLDecoder.decode(epiTitle);
                    Matcher titleMagnetMatcher = MAGNET_PATTERN.matcher(decoded);
                    if (titleMagnetMatcher.find()) {
                        epiTitle = titleMagnetMatcher.group(2);
                    }
                }

                // 选集标题 $http 处理：若标题含 "$http" 且后半段以 http 开头，取前半段作为标题（原 smali 14517-14561 行）
                if (epiTitle != null && epiTitle.contains("$http")) {
                    String[] titleSplit = epiTitle.split("\\$");
                    if (titleSplit.length > 1 && titleSplit[1].startsWith("http")) {
                        epiTitle = titleSplit[0];
                    }
                }

                String epiUrl;
                String rawUrlRule = getConfig(epiUrlKey);
                String urlRule = rawUrlRule.split("\\[保留页链\\]")[0];
                urlRule = urlRule.split("\\[替换:")[0];

                if (isJsoupMode) {
                    epiUrl = getTextByRule(episodeElem, urlRule);
                } else {
                    String elemHtml = episodeElem.outerHtml();
                    String[] urlRuleParts = urlRule.split("&&");
                    String pre = urlRuleParts[0];
                    String suf = urlRuleParts.length > 1 ? urlRuleParts[1] : "";
                    ArrayList<String> urlMatches = ParseUtils.regexExtract(elemHtml, pre, suf);
                    epiUrl = urlMatches.get(0);
                }

                if (rawUrlRule.contains("[替换")) {
                    epiUrl = applyReplace(epiUrl, rawUrlRule);
                }

                if (epiUrl.contains("$http")) {
                    String[] urlSplit = epiUrl.split("\\$");
                    if (urlSplit.length > 1 && urlSplit[1].startsWith("http")) {
                        epiUrl = urlSplit[1];
                    }
                }

                String fullUrl;
                if (isAbsoluteUrl(epiUrl)) {
                    fullUrl = epiUrl;
                } else {
                    fullUrl = new StringBuilder().append(prefixVal).append(epiUrl).append(suffixVal).toString();
                }

                if (fullUrl.contains("'input'")) {
                    fullUrl = fullUrl.replaceAll("'input'", epiUrl);
                }

                String episode = new StringBuilder().append(epiTitle).append(sepDollar).append(fullUrl).toString();
                episodeUrls.add(episode);
            }

            // 选集遍历完成后统一翻转并拼接为单条线路
            if (shouldReverse) {
                Collections.reverse(episodeUrls);
            }
            playUrlList.add(TextUtils.join(sepHash, episodeUrls));
        }

        if (playUrlList.size() >= 1) {
            if (playUrlList.size() == 1 && playUrlList.get(0).trim().equals(sepDollar)) {
                if (getConfig(epiUrlKey).contains("[保留页链]")) {
                    playUrlList.clear();
                    playUrlList.add(new StringBuilder().append(parts[0]).append(sepDollar).append(parts[2]).toString());
                }
            }
        }

        String tabArrKey = getConfig("线路列表数组规则").isEmpty() ? "tab_arr_rule" : "线路列表数组规则";
        String tabArrRule = getConfig(tabArrKey);

        String tabTitleKey = getConfig("线路标题").isEmpty() ? "tab_title" : "线路标题";
        String tabTitleRule = getConfig(tabTitleKey);

        if (!tabTitleRule.isEmpty() && !tabArrRule.isEmpty()) {
            String[] tabRuleParts = tabArrRule.split("&&");
            org.jsoup.select.Elements tabElems;
            if (tabRuleParts.length == 1) {
                // 单段规则：直接在 doc 中搜索所有匹配元素
                tabElems = selectElements(doc, tabRuleParts[0]);
            } else {
                // 多段规则：先导航到容器，再在容器内搜索
                org.jsoup.nodes.Element tabContainer = getTrueElement(tabRuleParts[0], doc);
                for (int ti = 1; ti < tabRuleParts.length - 1; ti++) {
                    // 容错：中间规则不匹配时跳过，保持上一个非空元素继续搜索
                    org.jsoup.nodes.Element next = getTrueElement(tabRuleParts[ti], tabContainer);
                    if (next != null) {
                        tabContainer = next;
                    }
                }
                tabElems = selectElements(tabContainer, tabRuleParts[tabRuleParts.length - 1]);
            }

            for (int ti = 0; ti < playUrlList.size(); ti++) {
                try {
                    org.jsoup.nodes.Element tabElem = tabElems.get(ti);
                    String rawTabRule = tabTitleRule;
                    String cleanTabRule = rawTabRule.split("\\[排序:")[0].split("\\[替换:")[0].split("\\[不包含:")[0];
                    String tabName = getTextByRule(tabElem, cleanTabRule);

                    for (int di = 0; di < playFromList.size(); di++) {
                        if (tabName.equals(playFromList.get(di))) {
                            tabName = new StringBuilder().append(tabName).append(sepDollar).append(ti + 1).toString();
                            break;
                        }
                    }
                    playFromList.add(tabName);
                } catch (Exception e) {
                    SpiderDebug.log(e);
                    if (debugFlag) {
                        Notify.show("详情获取线路出错：" + e.toString());
                    }
                }
            }
        } else {
            for (int ti = 0; ti < playUrlList.size(); ti++) {
                playFromList.add(new StringBuilder().append("线路列表").append(ti + 1).toString());
            }
        }

        String[] ruleMarkers = {tabTitleRule, "[不包含:", "[排序:"};
        for (int mi = 0; mi < 3; mi++) {
            String marker = ruleMarkers[mi];
            String tabRuleStr = tabTitleRule;

            if (mi == 0 && tabRuleStr.contains("[不包含:") && marker.contains("[不包含:")) {
                String excludeStr = tabRuleStr.split("\\[不包含:")[1].split("\\]")[0];
                String[] excludeWords = excludeStr.split(",");
                for (String word : excludeWords) {
                    for (int ti = playFromList.size() - 1; ti >= 0; ti--) {
                        if (playFromList.get(ti).contains(word) && playFromList.size() > 1) {
                            playFromList.remove(ti);
                            playUrlList.remove(ti);
                        }
                    }
                }
            }

            if (mi == 1 && tabRuleStr.contains("[排序:") && marker.contains("[排序:")) {
                String sortStr = tabRuleStr.split("\\[排序:")[1].split("\\]")[0];
                String[] sortWords = sortStr.split(",");
                ArrayList<String> sortedFrom = new ArrayList<>();
                ArrayList<String> sortedUrl = new ArrayList<>();
                for (String word : sortWords) {
                    for (int ti = 0; ti < playFromList.size(); ti++) {
                        if (playFromList.get(ti).contains(word)) {
                            sortedFrom.add(playFromList.get(ti));
                            sortedUrl.add(playUrlList.get(ti));
                            playFromList.remove(ti);
                            playUrlList.remove(ti);
                            ti--;
                        }
                    }
                }
                playUrlList.addAll(0, sortedUrl);
                playFromList.addAll(0, sortedFrom);
            }

            if (mi == 2 && tabRuleStr.contains("[替换:") && marker.contains("[替换:")) {
                for (int ti = 0; ti < playFromList.size(); ti++) {
                    String replaced = applyReplace(playFromList.get(ti), tabRuleStr);
                    playFromList.set(ti, replaced);
                }
            }
        }

        String projCoverKey = getConfig("封面详情").isEmpty() ? "proj_cover" : "封面详情";
        String projCateKey = getConfig("类型详情").isEmpty() ? "proj_cate" : "类型详情";
        String projYearKey = getConfig("年代详情").isEmpty() ? "proj_year" : "年代详情";
        String projAreaKey = getConfig("地区详情").isEmpty() ? "proj_area" : "地区详情";
        String projActorKey = getConfig("演员详情").isEmpty() ? "proj_actor" : "演员详情";
        String projPlotKey = getConfig("简介详情").isEmpty() ? "proj_plot" : "简介详情";
        String projJsoupKey = getConfig("详情是否Jsoup写法").isEmpty() ? "proj_is_jsoup" : "详情是否Jsoup写法";

        String projJsoupVal = getConfig(projJsoupKey);
        boolean detailIsJsoup = projJsoupVal.equals("1") || projJsoupVal.equals("是");

        String vodPic = emptyStr;
        String coverRule = getConfig(projCoverKey);
        if (!coverRule.isEmpty()) {
            try {
                if (detailIsJsoup) {
                    vodPic = getTextByRule(doc, coverRule);
                } else {
                    String[] coverParts = coverRule.split("&&");
                    String pre = coverParts[0];
                    String suf = coverParts.length > 1 ? coverParts[1] : "";
                    ArrayList<String> coverMatches = ParseUtils.regexExtract(doc.outerHtml(), pre, suf);
                    vodPic = cleanHtml(coverMatches.get(0));
                }

                if (vodPic.contains("url(")) {
                    vodPic = vodPic.replaceAll("\\&quot;", emptyStr);
                    String[] urlParts = vodPic.split("url\\(");
                    if (urlParts.length > 1 && urlParts[1].contains(")")) {
                        vodPic = urlParts[1].split("\\)")[0].replaceAll("['\"]", emptyStr);
                    }
                }

                vodPic = ParseUtils.urlCombine(url, vodPic);
            } catch (Exception e) {
                SpiderDebug.log(e);
            }
        }

        String typeName = emptyStr;
        String cateRule = getConfig(projCateKey);
        if (!cateRule.isEmpty()) {
            try {
                if (detailIsJsoup) {
                    typeName = getTextByRule(doc, cateRule);
                } else {
                    String[] cateParts = cateRule.split("&&");
                    String pre = cateParts[0];
                    String suf = cateParts.length > 1 ? cateParts[1] : "";
                    ArrayList<String> cateMatches = ParseUtils.regexExtract(doc.outerHtml(), pre, suf);
                    typeName = cleanHtml(cateMatches.get(0));
                }
            } catch (Exception e) {
                SpiderDebug.log(e);
            }
        }

        String vodYear = emptyStr;
        if (!getConfig(projYearKey).isEmpty()) {
            try {
                String yearRule = getConfig(projYearKey);
                if (detailIsJsoup) {
                    vodYear = getTextByRule(doc, yearRule);
                } else {
                    String[] yearParts = yearRule.split("&&");
                    String pre = yearParts[0];
                    String suf = yearParts.length > 1 ? yearParts[1] : "";
                    ArrayList<String> yearMatches = ParseUtils.regexExtract(doc.outerHtml(), pre, suf);
                    vodYear = cleanHtml(yearMatches.get(0));
                }
            } catch (Exception e) {
                SpiderDebug.log(e);
            }
        }

        String vodArea = emptyStr;
        if (!getConfig(projAreaKey).isEmpty()) {
            try {
                String areaRule = getConfig(projAreaKey);
                if (detailIsJsoup) {
                    vodArea = getTextByRule(doc, areaRule);
                } else {
                    String[] areaParts = areaRule.split("&&");
                    String pre = areaParts[0];
                    String suf = areaParts.length > 1 ? areaParts[1] : "";
                    ArrayList<String> areaMatches = ParseUtils.regexExtract(doc.outerHtml(), pre, suf);
                    vodArea = cleanHtml(areaMatches.get(0));
                }
            } catch (Exception e) {
                SpiderDebug.log(e);
            }
        }

        String vodActor = emptyStr;
        if (!getConfig(projActorKey).isEmpty()) {
            try {
                String actorRule = getConfig(projActorKey);
                if (detailIsJsoup) {
                    vodActor = getTextByRule(doc, actorRule);
                } else {
                    String[] actorParts = actorRule.split("&&");
                    String pre = actorParts[0];
                    String suf = actorParts.length > 1 ? actorParts[1] : "";
                    ArrayList<String> actorMatches = ParseUtils.regexExtract(doc.outerHtml(), pre, suf);
                    vodActor = cleanHtml(actorMatches.get(0));
                    vodActor = vodActor.replaceAll("\\&nbsp;", " ");
                    vodActor = vodActor.replaceAll("\\&[a-zA-Z]{1,10};", emptyStr);
                    vodActor = vodActor.replaceAll("<[^>]*>", emptyStr);
                    vodActor = vodActor.replaceAll("\\s{2,}", emptyStr);
                }
            } catch (Exception e) {
                SpiderDebug.log(e);
            }
        }

        String vodContent = emptyStr;
        if (!getConfig(projPlotKey).isEmpty()) {
            try {
                String plotRule = getConfig(projPlotKey);
                if (detailIsJsoup) {
                    vodContent = getTextByRule(doc, plotRule);
                } else {
                    String[] plotParts = plotRule.split("&&");
                    String pre = plotParts[0];
                    String suf = plotParts.length > 1 ? plotParts[1] : "";
                    ArrayList<String> plotMatches = ParseUtils.regexExtract(doc.outerHtml(), pre, suf);
                    vodContent = cleanHtml(plotMatches.get(0));
                }
            } catch (Exception e) {
                SpiderDebug.log(e);
            }
        }

        String playFrom = TextUtils.join(sepDoubleDollar, playFromList);
        String playUrl = TextUtils.join(sepDoubleDollar, playUrlList);

        Vod vod = new Vod(ids.get(0), vodName, vodPic);
        vod.setTypeName(typeName);
        vod.setVodYear(vodYear);
        vod.setVodArea(vodArea);
        vod.setVodActor(vodActor);
        vod.setVodContent(vodContent);
        vod.setVodPlayFrom(playFrom);
        vod.setVodPlayUrl(playUrl);
        return Result.string(vod);

    } catch (Exception e) {
        SpiderDebug.log(e);
        if (debugFlag) {
            Notify.show("详情全局出错：" + e.toString());
        }
        return emptyStr;
    }
}

// === METHOD: playerContent (smali L21597-L25661) ===
@Override
public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
    String emptyStr = "";
    String resultStr = emptyStr;
    try {
        if (flag.contains("Ali转码") || flag.contains("Open原画") || flag.contains("Open转码")) {
            return this.pushAgent.playerContent(flag, id, vipFlags);
        }

        // P2P 协议链接（magnet/ed2k/thunder）不能走 HTTP 解析器，否则会连接超时。
        // 直接以 parse(0)+playUrl="" 返回（与 PushAgent 磁力处理格式一致），
        // 交给支持 P2P 的播放器（如迅雷内核）处理。
        // 必须显式设置 playUrl=""，否则部分播放器会使用默认解析地址尝试 HTTP 解析 magnet → 超时。
        if (isP2PUrl(id)) {
            JSONObject p2pResult = new JSONObject();
            p2pResult.put("parse", 0);
            p2pResult.put("playUrl", "");
            p2pResult.put("url", id);
            return p2pResult.toString();
        }

        ensureSiteConfig();
        String codingKey = getConfig("网页编码格式", emptyStr).isEmpty() ? "Coding_format" : "网页编码格式";
        this.charset = getConfig(codingKey, "UTF-8");

        String analMacPlayerKey = getConfig("分析MacPlayer", emptyStr).isEmpty() ? "Anal_MacPlayer" : "分析MacPlayer";

        JSONObject headersJson = new JSONObject();
        String headersKey = getConfig("请求头参数", emptyStr).isEmpty() ? "Headers" : "请求头参数";
        String uaStr = getConfig(headersKey, emptyStr).trim();

        String pcUaKey = "PC_UA";
        String mobileUaKey = "MOBILE_UA";
        String iosUaKey = "IOS_UA";
        String macUaKey = "MAC_UA";
        String refererKey = "referer";
        String cookieKey = "cookie";
        String hashSep = "#";
        String oneStr = "1";
        String twoStr = "2";

        if (uaStr.contains("$")) {
            String[] uaParts = uaStr.split(hashSep);
            for (String uaPart : uaParts) {
                String[] kv = uaPart.split("\\$", 2);
                if (kv.length < 2) continue;
                String headerKey = kv[0];
                String headerValue = kv[1];

                String uaValue;
                if (headerValue.equals(pcUaKey) || headerValue.equals("电脑")) {
                    uaValue = PC_UA;
                } else if (headerValue.equals(mobileUaKey) || headerValue.equals("手机")) {
                    uaValue = MOBILE_UA;
                } else if (headerValue.equals(iosUaKey) || headerValue.equals("苹果手机")) {
                    uaValue = IOS_UA;
                } else if (headerValue.equals(macUaKey) || headerValue.equals("苹果电脑")) {
                    uaValue = MAC_UA;
                } else {
                    uaValue = headerValue;
                }

                if (headerKey.equalsIgnoreCase(refererKey) && uaValue.equalsIgnoreCase("WebView")) {
                    uaValue = id;
                }

                if (!this.accCookie.isEmpty() && headerKey.equalsIgnoreCase(cookieKey)) {
                    uaValue = uaValue + ";" + this.accCookie;
                }

                headersJson.put(headerKey, uaValue);
            }

            if (!uaStr.toLowerCase().contains("referer")) {
                if (getConfig(analMacPlayerKey).equals(twoStr)) {
                    headersJson.put("Referer", id);
                }
            }

            if (!this.accCookie.isEmpty() && this.accCookie.length() > 1
                    && !uaStr.contains("Cookie$") && !uaStr.contains("cookie$")) {
                headersJson.put("Cookie", this.accCookie);
            }
        } else {
            if (uaStr.isEmpty()) {
                uaStr = "okhttp/3.12.11";
            } else if (uaStr.equals(pcUaKey) || uaStr.equals("电脑")) {
                uaStr = PC_UA;
            } else if (uaStr.equals(mobileUaKey) || uaStr.equals("手机")) {
                uaStr = MOBILE_UA;
            } else if (uaStr.equals(iosUaKey) || uaStr.equals("苹果手机")) {
                uaStr = IOS_UA;
            } else if (uaStr.equals(macUaKey) || uaStr.equals("苹果电脑")) {
                uaStr = MAC_UA;
            }

            if (!this.accCookie.isEmpty() && this.accCookie.length() > 1) {
                headersJson.put("Cookie", this.accCookie);
            }

            if (getConfig(analMacPlayerKey).equals(twoStr)) {
                headersJson.put("Referer", id);
            }

            headersJson.put("User-Agent", uaStr);
        }

        // Build headersMap from headersJson for Result.header()
        Map<String, String> headersMap = new LinkedHashMap<>();
        java.util.Iterator<String> headerKeys = headersJson.keys();
        while (headerKeys.hasNext()) {
            String hk = headerKeys.next();
            headersMap.put(hk, headersJson.optString(hk));
        }

        String forcePlayKey = getConfig("链接是否直接播放", emptyStr).isEmpty() ? "force_play" : "链接是否直接播放";
        String playPrefixKey = getConfig("直接播放链接加前缀", emptyStr).isEmpty() ? "play_prefix" : "直接播放链接加前缀";
        String playSuffixKey = getConfig("直接播放链接加后缀", emptyStr).isEmpty() ? "play_suffix" : "直接播放链接加后缀";
        String playHeaderKey = getConfig("直接播放直链视频请求头", emptyStr).isEmpty() ? "play_header" : "直接播放直链视频请求头";

        boolean forcePlay = getConfig(forcePlayKey).equals(oneStr) || getConfig(forcePlayKey).equals(twoStr);

        if (forcePlay) {
            String newUrl = getConfig(playPrefixKey, emptyStr) + id + getConfig(playSuffixKey, emptyStr);

            Map<String, String> headerMap;
            if (!getConfig(playHeaderKey, emptyStr).isEmpty()) {
                headerMap = new LinkedHashMap<>();
                JSONObject headerObj = this.configJson.optJSONObject(playHeaderKey);
                if (headerObj != null) {
                    java.util.Iterator<String> hKeys = headerObj.keys();
                    while (hKeys.hasNext()) {
                        String hk = hKeys.next();
                        headerMap.put(hk, headerObj.optString(hk));
                    }
                } else {
                    String[] headerParts = getConfig(playHeaderKey).split(hashSep);
                    for (String part : headerParts) {
                        String[] kv = part.split("\\$", 2);
                        if (kv.length < 2) continue;
                        headerMap.put(kv[0], " " + kv[1]);
                    }
                }
            } else {
                headerMap = headersMap;
            }

            if ((newUrl.contains("vip.ffzy") || newUrl.contains("vip.lz")
                    || newUrl.contains("hd.lz") || newUrl.contains("suonizy"))
                    && newUrl.contains("/share/")) {
                newUrl = newUrl.replaceAll("#isVideo=true#", emptyStr);
                newUrl = fetchAndExtract(newUrl);
            }

            if (newUrl.contains("#isVideo=true#") || isPlayableVideoUrl(newUrl)) {
                if (newUrl.contains("#isVideo=true#")) {
                    newUrl = newUrl.replaceAll("#isVideo=true#", emptyStr);
                }
                return Result.get().url(newUrl).parse(0).header(headerMap).string();
            }

            if (isVideoUrl(newUrl)) {
                return Result.get().url(newUrl).parse(1).jx().header(headerMap).string();
            }

            return Result.get().url(newUrl).parse(1).header(headerMap).string();
        }

        String analMacVal = getConfig(analMacPlayerKey);
        boolean analMac = analMacVal.equals(oneStr) || analMacVal.equals(twoStr) || analMacVal.equals("是");

        String playerUrl = null;
        String from = null;
        String linkNext = null;
        boolean hasKey = false;
        boolean hasTm = false;
        Integer idInt = null;
        Integer nidInt = null;
        String vodPicThumb = null;
        String vodTitle = null;
        String vodTitleName = null;

        String parseUrl = null;
        boolean containsJump = false;
        boolean containsNext = false;
        boolean containsTitleAndThumb = false;
        boolean containsNid = false;

        if (analMac && !forcePlay) {
            try {
                String html = fetchGet(id, this.charset, buildShowHeaders(id));

                if (html != null && html.contains("检测中") && html.contains("btwaf")) {
                    html = handleBtwafVerify(id, html, "show");
                }

                if (html != null && (html.contains("/huadong_") || html.contains("/renji_"))) {
                    html = handleSniffVerify(id, html, "show");
                }

                if (html != null && html.contains("输入验证码") && checkveriry(html)) {
                    String verifyType = vertype(html);
                    html = handleCaptchaVerify(id, null, "show", verifyType);
                }

                org.jsoup.nodes.Document doc = org.jsoup.Jsoup.parse(html);
                org.jsoup.select.Elements scripts = doc.select("script");

                for (int i = 0; i < scripts.size(); i++) {
                    org.jsoup.nodes.Element scriptEl = scripts.get(i);
                    String scriptText = scriptEl.data().trim();
                    if (!scriptText.startsWith("var player_")) {
                        continue;
                    }

                    int jsonStart = scriptText.indexOf('{');
                    int jsonEnd = scriptText.lastIndexOf('}') + 1;
                    String jsonStr = scriptText.substring(jsonStart, jsonEnd);
                    JSONObject playerJson = new JSONObject(jsonStr);

                    playerUrl = playerJson.getString("url");
                    from = playerJson.getString("from");
                    linkNext = playerJson.getString("link_next");
                    hasKey = playerJson.has("key");
                    hasTm = playerJson.has("tm");
                    idInt = playerJson.has("id") ? playerJson.getInt("id") : null;
                    vodPicThumb = playerJson.has("vod_pic_thumb") ? playerJson.getString("vod_pic_thumb") : null;

                    if (playerJson.has("vod_title") && playerJson.has("vod_title_name")) {
                        vodTitle = playerJson.getString("vod_title");
                        vodTitleName = playerJson.getString("vod_title_name");
                    }

                    nidInt = playerJson.has("nid") ? playerJson.getInt("nid") : null;

                    if (playerJson.has("encrypt")) {
                        int encrypt = playerJson.getInt("encrypt");
                        if (encrypt == 1) {
                            playerUrl = URLDecoder.decode(playerUrl);
                        } else if (encrypt == 2) {
                            playerUrl = URLDecoder.decode(new String(android.util.Base64.decode(playerUrl, 0)));
                        }
                    }
                    break;
                }

                if (playerUrl != null
                        && (playerUrl.contains("vip.ffzy") || playerUrl.contains("vip.lz")
                            || playerUrl.contains("hd.lz") || playerUrl.contains("suonizy"))
                        && playerUrl.contains("/share/")) {
                    playerUrl = fetchAndExtract(playerUrl);
                }

                if (this.debugFlag && playerUrl != null) {
                    Notify.show("视频链接：" + playerUrl);
                }

                if (playerUrl != null && (hasTm || hasKey)) {
                    if (isPlayableVideoUrl(playerUrl)) {
                        return Result.get().url(playerUrl).parse(0).header(headersMap).string();
                    } else {
                        return Result.get().url(id).parse(1).header(headersMap).string();
                    }
                }
            } catch (Exception e) {
                SpiderDebug.log(e);
                if (this.debugFlag) {
                    Notify.show("分析var plays部分出错：" + e.toString());
                }
            }
        }

        if (analMac && !forcePlay) {
            try {
                String dateStr = new SimpleDateFormat("yyyyMMdd").format(new Date()).toString();
                String configUrl = ParseUtils.urlCombine(id, "/static/js/playerconfig.js?t=" + dateStr);
                String configContent = fetchGet(configUrl, this.charset, buildShowHeaders(id));

                Pattern pattern = Pattern.compile(this.macPlayerRegex);
                Matcher matcher = pattern.matcher(configContent);

                JSONObject playerConfigJson = null;
                if (matcher.find()) {
                    playerConfigJson = new JSONObject(matcher.group(1));
                }

                if (playerConfigJson != null && from != null && playerConfigJson.has(from)) {
                    JSONObject playerConfig = playerConfigJson.getJSONObject(from);
                    String ps = playerConfig.getString("ps");

                    if (ps.equals(oneStr)) {
                        String parseField = playerConfig.getString("parse");
                        if (!parseField.isEmpty()) {
                            String parseJsUrl = ParseUtils.urlCombine(id, "/static/player/parse.js");
                            String parseJsContent = fetchGet(parseJsUrl, this.charset, buildShowHeaders(id));
                            containsNext = parseJsContent.contains("&next=");
                        }
                    } else {
                        String playerJsUrl = ParseUtils.urlCombine(id, "/static/player/" + from + ".js");
                        String playerJsContent = fetchGet(playerJsUrl, this.charset, buildShowHeaders(id));

                        containsJump = playerJsContent.contains("&jump=");
                        containsNext = playerJsContent.contains("&next=");
                        containsTitleAndThumb = playerJsContent.contains("&title=") && playerJsContent.contains("humb=");
                        containsNid = playerJsContent.contains("&nid=");

                        if (playerJsContent.contains("src=\"http")) {
                            ArrayList<String> matches = ParseUtils.regexExtract(playerJsContent, "src=\"", "\"");
                            if (matches != null && !matches.isEmpty()) {
                                parseUrl = matches.get(0).split("'")[0];
                            }
                        } else if (playerJsContent.contains("src=\"'+")) {
                            ArrayList<String> matches = ParseUtils.regexExtract(playerJsContent, "+'", "\"");
                            if (matches != null && !matches.isEmpty()) {
                                parseUrl = matches.get(0).split("'")[0];
                            }
                        }

                        if (parseUrl != null && !parseUrl.isEmpty()) {
                            parseUrl = ParseUtils.urlCombine(id, parseUrl);
                        }
                    }
                }
            } catch (Exception e) {
                SpiderDebug.log(e);
                if (this.debugFlag) {
                    Notify.show("分析playerconfig区域出错：" + e.toString());
                }
            }
        }

        if (playerUrl != null) {
            if (isVideoUrl(playerUrl) && (getConfig(analMacPlayerKey).equals(oneStr) || getConfig(analMacPlayerKey).equals("是"))) {
                try {
                    return Result.get().url(playerUrl).parse(1).jx().string();
                } catch (Exception e) {
                    SpiderDebug.log(e);
                    if (this.debugFlag) {
                        Notify.show("Mac分析2播放区域出错：" + e.toString());
                    }
                }
            }

            boolean isPlayable = isPlayableVideoUrl(playerUrl);
            boolean isZxzj = playerUrl.contains("/zxzj_");

            if (isPlayable || isZxzj) {
                if (isZxzj) {
                    try {
                        String zxzjHtml = fetchGet(playerUrl, this.charset, buildShowHeaders(id));
                        ArrayList<String> urlMatches = ParseUtils.regexExtract(zxzjHtml, "var url = '", "'");
                        if (urlMatches != null && !urlMatches.isEmpty()) {
                            String matchedUrl = urlMatches.get(0);
                            String reversed = new StringBuffer(matchedUrl).reverse().toString();
                            char[] chars = reversed.toCharArray();

                            StringBuilder hexStr = new StringBuilder();
                            for (int i = 0; i < chars.length; i += 2) {
                                hexStr.append("\\u00");
                                hexStr.append(chars[i]);
                                if (i + 1 < chars.length) {
                                    hexStr.append(chars[i + 1]);
                                }
                            }

                            String decoded = decodeHexChars(hexStr.toString());
                            int len = decoded.length();
                            int mid = (int) Math.ceil((len - 6) / 2.0);
                            String result = decoded.substring(0, mid - 1) + decoded.substring(mid + 6);

                            return Result.get().url(result).parse(0).header(headersMap).string();
                        }
                    } catch (Exception e) {
                        SpiderDebug.log(e);
                        if (this.debugFlag) {
                            Notify.show("直链视频与zxzj部分出错：" + e.toString());
                        }
                    }
                } else {
                    try {
                        return Result.get().url(playerUrl).parse(0).header(headersMap).string();
                    } catch (Exception e) {
                        SpiderDebug.log(e);
                        if (this.debugFlag) {
                            Notify.show("Mac分析2播放区域出错：" + e.toString());
                        }
                    }
                }
            } else {
                if (parseUrl != null && getConfig(analMacPlayerKey).equals(twoStr)) {
                    try {
                        String finalUrl = playerUrl;
                        boolean hasLinkNext = linkNext != null && !linkNext.isEmpty();

                        if (hasLinkNext) {
                            if (containsTitleAndThumb) {
                                StringBuilder sb = new StringBuilder();
                                sb.append(playerUrl);
                                sb.append("&jump=");
                                sb.append(linkNext != null ? linkNext : "");
                                sb.append("&title=");
                                sb.append(vodTitle != null ? vodTitle : "");
                                sb.append("+");
                                sb.append(vodTitleName != null ? vodTitleName : "");
                                sb.append("&thumb=");
                                sb.append(vodPicThumb != null ? vodPicThumb : "");
                                sb.append("&id=");
                                sb.append(nidInt != null ? nidInt : "");
                                sb.append("&nid=");
                                sb.append(idInt != null ? idInt : "");
                                finalUrl = sb.toString();
                            } else if (containsJump) {
                                StringBuilder sb = new StringBuilder();
                                sb.append(playerUrl);
                                sb.append("&jump=");
                                sb.append(linkNext != null ? linkNext : "");
                                finalUrl = sb.toString();
                            } else if (containsNid) {
                                StringBuilder sb = new StringBuilder();
                                sb.append(playerUrl);
                                sb.append("&next=");
                                sb.append(linkNext != null ? linkNext : "");
                                sb.append("&id=");
                                sb.append(nidInt != null ? nidInt : "");
                                sb.append("&nid=");
                                sb.append(idInt != null ? idInt : "");
                                sb.append("&from=");
                                sb.append(from != null ? from : "");
                                finalUrl = sb.toString();
                            } else if (!playerUrl.contains("&next=") && containsNext) {
                                StringBuilder sb = new StringBuilder();
                                sb.append(playerUrl);
                                sb.append("&next=");
                                sb.append(linkNext != null ? linkNext : "");
                                finalUrl = sb.toString();
                            }
                        }

                        return Result.get().url(parseUrl + finalUrl).parse(1).header(headersMap).string();
                    } catch (Exception e) {
                        SpiderDebug.log(e);
                        if (this.debugFlag) {
                            Notify.show("Mac分析2播放区域出错：" + e.toString());
                        }
                    }
                }
            }
        }

        return Result.get().url(id).parse(1).header(headersMap).string();

    } catch (Exception e) {
        SpiderDebug.log(e);
        if (this.debugFlag) {
            Notify.show("播放类全局区域出错：" + e.toString());
        }
        return resultStr;
    }
}

}

