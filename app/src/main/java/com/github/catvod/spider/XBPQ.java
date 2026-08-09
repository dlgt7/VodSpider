package com.github.catvod.spider;

import android.content.Context;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;

import com.github.catvod.api.AliYun;
import com.github.catvod.bean.Class;
import com.github.catvod.bean.Filter;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.bean.XBPQ.XBPQConfig;
import com.github.catvod.bean.XBPQ.XBPQCrypto;
import com.github.catvod.bean.XBPQ.XBPQHttp;
import com.github.catvod.bean.XBPQ.XBPQParse;
import com.github.catvod.bean.XBPQ.XBPQPlayer;
import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderApi;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Util;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * XBPQ 配置驱动型影视爬虫。
 *
 * <p>通过 JSON 配置（中文/拼音/英文三套键名体系）驱动首页、分类、详情、搜索、播放全流程，
 * 支持 HTML 截取解析、XPath 节点解析、JSON/XML 搜索结果、多级跳转播放、嗅探词过滤、
 * 阿里云盘、磁力链接、直链播放及本地代理字幕探测。</p>
 *
 * <p><b>类名保留 XBPQ</b>：tvbox 生态通过 {@code "api": "csp_XBPQ"} 反射加载本类，
 * 重命名会导致全部存量配置失效。</p>
 *
 * <p>本类为对外标准 API 入口，仅保留生命周期与流程编排方法；具体实现拆分至：</p>
 * <ul>
 *   <li>{@link XBPQConfig} — 配置解析器</li>
 *   <li>{@link XBPQParse} — HTML/JSON/XML 文本截取、Jsoup 节点解析、变量插值、工具链、分页</li>
 *   <li>{@link XBPQHttp} — 请求头组装、GBK/UTF-8 源码 Fetch、POST 交互</li>
 *   <li>{@link XBPQPlayer} — 播放全流程：JS 渲染、直链拦截、BTWAF、异步验证码、嗅探/AES 解密</li>
 *   <li>{@link XBPQAliPa} — 阿里云盘与 PA（磁力/直链）专属解析分支</li>
 *   <li>{@link XBPQCrypto} — AES/CTR/CBC、Base64、SHA-1、HaB 异或解密</li>
 * </ul>
 *
 * <p>所有 {@code @Override} 方法声明 {@code throws Exception}，与 Spider 基类签名一致。</p>
 */
public class XBPQ extends Spider {

    // ==================== 常量 ====================

    /** 默认本地代理端口。 */
    private static final String DEFAULT_PORT = "9978";

    /** 图片代理默认 UA。 */
    private static final String DEFAULT_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/94.0.4606.54 Safari/537.36";

    /** 默认嗅探词。 */
    private static final String DEFAULT_SNIFF = "m3u8#.mp4#.flv#.mp3#.m4a#magnet:#ed2k:#ftp:#thunder:#push:#tvbox-xg:";

    /** 默认过滤词。 */
    private static final String DEFAULT_FILTER = "url=http#;post;#.js";

    /** host 提取正则。 */
    static final Pattern HOST_PATTERN = Pattern.compile("(https?://[^/]+)");

    /** 阿里云盘分享链接正则。 */
    static final Pattern ALIYUN_PATTERN =
            Pattern.compile("https?://www\\.(aliyundrive|alipan)\\.com/s/([^/]+)(/folder/([^/]+))?");

    /** 验证页面识别关键词（默认列表，去掉过于宽泛的"验证"避免误伤正常页面）。 */
    private static final String[] DEFAULT_VERIFY_KEYWORDS = {"人机验证", "滑动验证", "输入验证码", "安全验证", "请完成验证", "captcha", "verify"};

    /** 获取验证关键词列表（支持配置"验证关键词"自定义，用 # 分隔）。 */
    public String[] getVerifyKeywords() {
        if (config != null) {
            String custom = config.get("", "验证关键词");
            if (!custom.isEmpty()) {
                String[] parts = custom.split("#");
                List<String> list = new ArrayList<>();
                for (String p : parts) {
                    String trimmed = p.trim();
                    if (!trimmed.isEmpty()) list.add(trimmed);
                }
                if (!list.isEmpty()) return list.toArray(new String[0]);
            }
        }
        return DEFAULT_VERIFY_KEYWORDS;
    }

    /** 搜索页 form action 提取正则。 */
    private static final Pattern FORM_ACTION_PATTERN =
            Pattern.compile("<form[^>]*action=\"([^\"]+)\"");

    /** URL 提取正则（从发布页源码中提取 http/https URL）。 */
    private static final Pattern URL_EXTRACT_PATTERN =
            Pattern.compile("https?://[a-zA-Z0-9\\-_.]+(?:\\.[a-zA-Z0-9\\-_.]+)+(?:/[^\"'<>\\s]*)?");

    // ==================== sniffConfig 标志位语义化 ====================
    // sniffConfig 是一个聚合标志串，各字符含义如下：
    //   x  → 手动嗅探开关        c/y/Y/L → 缓存嗅探结果   e  → 多级跳转解析
    //   d  → 跳转深度控制(配合e)  g     → GBK 编码         J  → JS 渲染
    //   u0 → 禁用 URL 解码      点击/验证/浏览器 → 验证码处理
    /** sniffConfig 含手动嗅探标记 "x"。 */
    public boolean sniffManual() { return sniffConfig.indexOf('x') >= 0; }
    /** sniffConfig 含缓存嗅探标记 (c/y/Y/L/点击/验证/浏览器)。 */
    public boolean sniffCacheEnabled() {
        return sniffConfig.indexOf('c') >= 0 || sniffConfig.indexOf('y') >= 0
                || sniffConfig.indexOf('Y') >= 0 || sniffConfig.indexOf('L') >= 0;
    }
    /** sniffConfig 含验证码处理标记 (点击/验证/浏览器/c/y/L)。 */
    public boolean sniffVerifyEnabled() {
        return sniffConfig.indexOf("点击") >= 0 || sniffConfig.indexOf("验证") >= 0
                || sniffConfig.indexOf("浏览器") >= 0 || sniffConfig.indexOf('c') >= 0
                || sniffConfig.indexOf('y') >= 0 || sniffConfig.indexOf('L') >= 0;
    }
    /** sniffConfig 含多级跳转标记 "e"。 */
    public boolean sniffJumpEnabled() { return sniffConfig.indexOf('e') >= 0; }
    /** sniffConfig 含 GBK 编码标记 "g"。 */
    public boolean sniffGbk() { return sniffConfig.indexOf('g') >= 0; }
    /** sniffConfig 含 JS 渲染标记 "J"。 */
    public boolean sniffJsRender() { return sniffConfig.indexOf('J') >= 0; }
    /** sniffConfig 含禁用 URL 解码标记 "u0"。 */
    public boolean sniffNoUrlDecode() { return sniffConfig.contains("u0"); }

    // ==================== 静态字段 ====================

    /** 配置缓存键。 */
    public static String cacheKey = "";

    /** 图片代理字节缓存（UA+referer → byte[]），LRU 策略限 100 条，synchronized 保证线程安全。 */
    private static final int PIC_CACHE_MAX = 100;
    private static final Map<String, byte[]> picCache = new LinkedHashMap<String, byte[]>(PIC_CACHE_MAX, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, byte[]> eldest) {
            return size() > PIC_CACHE_MAX;
        }
    };

    // ==================== 实例字段（public 供 bean.XBPQ 包内模块跨包访问） ====================

    /** 站点根 URL。 */
    public String hostUrl = "";
    /** cover/url 拼接前缀。 */
    public String baseUrl = "";
    /** 分类 url 模板。 */
    public String categoryUrlTemplate;
    /** 嗅探词 + 模式标记聚合串。 */
    public String sniffConfig = "";
    /** 过滤词。 */
    public String filterWords = "";
    /** 协议强制 https/http。 */
    public String forceProtocol = "";
    /** 列表倒序开关。 */
    public boolean reverseEpisodes = false;
    /** 分类列表。 */
    public ArrayList<String> categoryList;
    /** 图片代理 URL 模板。 */
    public String imageProxyUrl;
    /** 二级截取末位（默认 3）。 */
    public int secondaryCutEnd = 3;
    /** 手动嗅探默认开关（默认 true）。 */
    public boolean manualCheck = true;
    /** debug 开关。 */
    public boolean debug = false;
    /** 图文模式开关。 */
    public boolean imageTextMode = false;
    /** 静态分页开关。 */
    public boolean staticPaging = false;
    /** 每页分页大小。 */
    public int pageSize;
    /** cover 修正开关。 */
    public boolean coverFix = false;
    /** 二级截取开关。 */
    public boolean secondaryCutEnabled = false;
    /** 图片代理开关。 */
    public boolean imageProxyEnabled = false;
    /** 翻页步长。 */
    public int pageStep = 0;
    /** 分类分页开关。 */
    public boolean categoryPaging = false;
    /** 强制小写嗅探。 */
    public boolean lowerCaseSniff = false;
    /** 二级截取起始。 */
    public int secondaryCutStart = 0;
    /** 二级截取 CSS 选择器（配置后优先用 Jsoup 精确选段，比数字下标更稳）。 */
    public String secondaryCutSelector = "";
    /** 二级截取前置字符串（"播放区域"前截取，配合 secondaryCutSuf 使用）。 */
    public String secondaryCutPre = "";
    /** 二级截取后置字符串（"播放区域"后截取，配合 secondaryCutPre 使用）。 */
    public String secondaryCutSuf = "";
    /** 图片代理替换规则。 */
    public String imageProxyReplace = "";
    /** 图片代理正则。 */
    public String imageProxyRegex = "";
    /** Jsoup 解析模式开关（开启后选择器失败时回退到 Jsoup CSS 选择器）。 */
    public boolean jsoupMode = false;
    /** tid → 分类名映射（用于 || 多段选择器按分类名选段）。 */
    public final Map<String, String> tidToName = new HashMap<>();

    // ===== 运行时依赖 =====

    /** 原始配置 JSON。 */
    public XBPQConfig config;
    /** Context。 */
    protected Context context;
    /** 请求头缓存（volatile 保证多线程可见性）。 */
    public volatile Map<String, String> headerCache;
    /** SpiderApi 回调（默认实例，log() 内部委托 SpiderDebug；框架注入时覆盖）。 */
    public SpiderApi spiderApi = new SpiderApi();
    /** 本地代理端口。 */
    protected String port = DEFAULT_PORT;
    /** 当前方法名标记（日志用）。 */
    private String currentMethod = "";
    /** 横图模式开关。 */
    private boolean horizontalMode = false;
    /** Map 容量上限（LRU 淘汰，防止长时间运行内存无限增长）。 */
    private static final int MAP_MAX_SIZE = 200;

    /**
     * 验证状态 Map（按 URL 隔离，LRU 淘汰上限 200 条）。
     * 值含义：不存在=未验证 / "0"=跳过 / "1"=已验证
     */
    public final Map<String, String> verifyStateMap = Collections.synchronizedMap(
            new LinkedHashMap<String, String>(64, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
                    return size() > MAP_MAX_SIZE;
                }
            });
    /**
     * 嗅探结果缓存 Map（按 URL 隔离，LRU 淘汰上限 200 条）。
     * key = 播放页 URL，value = 嗅探到的真实播放地址
     */
    public final Map<String, String> sniffResultMap = Collections.synchronizedMap(
            new LinkedHashMap<String, String>(64, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
                    return size() > MAP_MAX_SIZE;
                }
            });
    /** 播放模式："1"=cookie模式，"cookie"=显示cookie。 */
    public String playMode = "";
    /** Cookie 字符串（来自"登录"配置）。 */
    public String cookieStr = "";
    /** 嗅探结果持久化缓存前缀（SharedPreferences 用，含站点标识）。 */
    public String sniffCachePrefix = "";
    /** 后缀解码模板（含工具链，用于播放 URL 解码）。 */
    public String suffixDecode = "";
    /** 播放列表二次截取前置（播放数组提取前再截取一次）。 */
    public String playArrTwice = "";
    /** 线路列表二次截取前置。 */
    public String lineArrTwice = "";
    /** 动态分类数组前缀（""=标准模式，非空=从页面动态提取分类列表）。 */
    public String catArrayPre = "";
    /** 动态分类标题选择器。 */
    public String catTitleSel = "";
    /** 动态分类ID选择器。 */
    public String catIdSel = "";
    /** 搜索请求头配置（格式同请求头，Key$Value 用 # 分隔）。 */
    public String searchReqHeader = "";
    /** 播放请求头配置。 */
    public String playReqHeader = "";
    /** 免嗅开关（"1"=跳过嗅探，直接播放；""=正常嗅探）。 */
    public boolean skipSniff = false;
    /** 直接播放开关（"1"=播放链接直接输出，不做嗅探/跳转等处理）。 */
    public boolean directPlay = false;
    /** 变量缓存（避免重复计算，配置重载时清空）。ConcurrentHashMap 保证多线程搜索/解析并发安全。 */
    public final Map<String, String> varCache = new java.util.concurrent.ConcurrentHashMap<>();

    // ==================== 构造函数 ====================

    public XBPQ() {
        super();
    }

    // ==================== 生命周期方法 ====================

    @Override
    public void init(Context context) throws Exception {
        super.init(context);
    }

    @Override
    public void init(Context context, String extend) throws Exception {
        super.init(context, extend);
        // 兜底设置本地代理端口（initApi 可能未被框架调用）
        if (Init.getLocalProxyPort() == null || Init.getLocalProxyPort().isEmpty()) {
            Init.setLocalProxyPort(this.port);
        }
        // 日志：输出 extend 原始值（截断防止过长）
        if (spiderApi != null) {
            String extLog = extend == null ? "null" : (extend.length() > 200 ? extend.substring(0, 200) + "..." : extend);
            spiderApi.log("init extend原始值: " + extLog);
        }
        // 清理 extend 参数：去除 BOM、首尾空格和反引号
        // TVBox 配置中 ext 值常被反引号包裹，有时还带 BOM 头，不清理会导致 startsWith 判断失败
        if (extend != null) {
            // 移除 BOM（\uFEFF）和零宽字符
            extend = extend.replace("\uFEFF", "").trim();
            while (extend.startsWith("`") && extend.endsWith("`") && extend.length() > 1) {
                extend = extend.substring(1, extend.length() - 1).trim();
            }
            if (spiderApi != null) spiderApi.log("init extend清理后: " + (extend.length() > 200 ? extend.substring(0, 200) + "..." : extend));
        }
        // 仅当 extend 不是 JSON 配置或 URL 时才作为阿里云盘 refresh_token
        // extend 可能是完整 JSON 站点配置、URL、或纯 token 字符串
        if (extend != null && !extend.isEmpty()
                && !extend.startsWith("{") && !extend.startsWith("http")
                && !extend.contains(":") && !extend.contains(",")) {
            AliYun.get().setRefreshToken(extend);
        }
        this.context = context;
        if (extend == null || extend.isEmpty()) return;
        try {
            JSONObject json;
            if (extend.startsWith("http")) {
                if (extend.contains("{cateId}")) {
                    json = new JSONObject();
                    json.put("分类url", extend);
                } else {
                    String content = OkHttp.string(extend, XBPQHttp.buildHeaders(this));
                    json = new JSONObject(content);
                }
            } else if (extend.startsWith("{")) {
                json = new JSONObject(removeJsonComments(extend));
            } else {
                json = new JSONObject();
                String safe = extend.replace("\\,", "逗号");
                for (String pair : safe.split(",")) {
                    int colonIndex = pair.indexOf(":");
                    if (colonIndex <= 0) continue;
                    String configKey = pair.substring(0, colonIndex).trim();
                    String configValue = pair.substring(colonIndex + 1).trim().replace("逗号", ",");
                    json.put(configKey, configValue);
                }
            }
            this.config = new XBPQConfig(json);
            fillFields();
            if (spiderApi != null) {
                spiderApi.log("init 成功: 分类数=" + (categoryList != null ? categoryList.size() : 0)
                        + ", baseUrl=" + baseUrl + ", 分类url=" + categoryUrlTemplate);
            }
        } catch (Exception e) {
            SpiderDebug.log(e);
            if (debug) Init.show("请检配置ext");
            if (spiderApi != null) spiderApi.log("请检配置ext-->" + e);
        }
    }

    /**
     * 从配置读取所有运行时字段。
     */
    private void fillFields() {
        if (config == null) return;
        // 配置重载时清空请求头缓存和验证状态，避免旧缓存污染新配置
        headerCache = null;
        verifyStateMap.clear();
        sniffResultMap.clear();
        varCache.clear();

        // ===== 第一阶段：纯配置读取（无网络 I/O，绝不抛异常） =====
        // 先读分类列表和所有字段配置，确保即使域名发现失败也能返回分类标签
        categoryUrlTemplate = cleanUrl(config.get("", "分类url", "分类链接", "fenlei", "分类链接模板"));
        sniffConfig = config.get("", "嗅探词", "VideoFormat");
        filterWords = config.get("", "过滤词", "VideoFilter");
        forceProtocol = config.get("", "强制解析");
        reverseEpisodes = config.getBool("", "倒序", "epi_reverse", "是否反转选集序列");
        // 分类列表解析：兼容四种配置写法（标准XBPQ/名+值/道长XYQ/仅名称）
        categoryList = parseCategoryList();
        // 构建 tid → 分类名映射（用于 || 多段选择器按分类名选段）
        tidToName.clear();
        if (categoryList != null) {
            for (String item : categoryList) {
                String[] parts = item.split("\\$");
                if (parts.length >= 2) {
                    tidToName.put(parts[1], parts[0]);
                } else if (!item.isEmpty()) {
                    // 仅名称时，name 同时作 tid
                    tidToName.put(item, item);
                }
            }
        }
        // 特殊分类链接：name$url，tid=name
        String specialCatLink = config.get("", "特殊分类链接");
        if (!specialCatLink.isEmpty()) {
            for (String link : specialCatLink.split("#")) {
                String[] parts = link.split("\\$");
                if (parts.length >= 2) {
                    tidToName.put(parts[0], parts[0]);
                }
            }
        }
        // 图片代理四件套：URL/正则/替换/开关 全量读取
        imageProxyUrl = config.get("", "图片代理", "pic_proxy");
        imageProxyRegex = config.get("", "图片代理正则", "pic_regex", "imageProxyRegex");
        imageProxyReplace = config.get("", "图片代理替换", "pic_replace", "imageProxyReplace");
        // 图片代理开关：优先读"图片代理开关"/"图片代理启用"显式开关，
        // 未配置显式开关时以 imageProxyUrl 非空为准（避免误触发，与 chaifen.txt 严格推导逻辑一致）
        boolean hasExplicitProxySwitch = config.has("图片代理开关") || config.has("图片代理启用");
        if (hasExplicitProxySwitch) {
            imageProxyEnabled = config.getBool("", "图片代理开关", "图片代理启用");
        } else {
            imageProxyEnabled = !imageProxyUrl.isEmpty();
        }
        secondaryCutEnd = config.getInt(3, "二级截取末位");
        secondaryCutStart = config.getInt(0, "二级截取起始");
        secondaryCutEnabled = config.getBool("", "二级截取");
        // 二级截取精确选段：CSS 选择器优先，其次前后字符串截取，最后回退数字下标
        secondaryCutSelector = config.get("", "二级截取选择器", "二级截取节点");
        secondaryCutPre = config.get("", "二级截取前", "二级截取前缀");
        secondaryCutSuf = config.get("", "二级截取后", "二级截取后缀");
        staticPaging = config.getBool("", "静态分页");
        pageSize = config.getInt(0, "每页", "limit");
        pageStep = config.getInt(0, "翻页步长");
        categoryPaging = config.getBool("", "分类分页");
        coverFix = config.getBool("", "cover修正");
        lowerCaseSniff = config.getBool("", "强制小写嗅探");
        debug = config.getBool("", "debug");
        imageTextMode = config.getBool("", "图文模式");
        jsoupMode = config.getBool("", "jsoup解析", "JsoupMode");
        // 横图模式开关（在 homeContent/categoryContent 解析 Vod 列表后应用 vod.setVodPicStyle("rect")）
        horizontalMode = config.getBool("", "横图", "横图模式");
        String manual = config.get("", "手动嗅探", "ManualSniffer");
        if (!manual.isEmpty()) {
            manualCheck = "1".equals(manual) || sniffManual();
        }
        playMode = config.get("", "播放模式", "playMode");
        cookieStr = config.get("", "登录", "cookie", "Cookie");
        sniffCachePrefix = cacheKey + "_sniff_";
        // 后缀解码：播放 URL 解码模板（含工具链，interpolate 时触发 fetch+decode）
        suffixDecode = config.get("", "后缀解码");
        playArrTwice = config.get("", "播放二次截取", "play_arr_twice");
        lineArrTwice = config.get("", "线路二次截取", "line_arr_twice");
        catArrayPre = config.get("", "分类数组", "cat_arr_pre");
        catTitleSel = config.get("", "分类标题", "cat_title_sel");
        catIdSel = config.get("", "分类ID", "cat_id_sel");
        searchReqHeader = config.get("", "搜索请求头", "search_req_header");
        playReqHeader = config.get("", "播放请求头", "play_req_header");
        skipSniff = "1".equals(config.get("", "免嗅", "skipSniff"));
        directPlay = "1".equals(config.get("", "直接播放", "directPlay"));

        // ===== 第二阶段：域名发现链（网络 I/O，独立 try-catch，失败不影响分类显示） =====
        try {
            discoverDomain();
        } catch (Exception e) {
            SpiderDebug.log("fillFields 域名发现失败: " + e.getMessage());
            if (spiderApi != null) spiderApi.log("fillFields 域名发现失败: " + e.getMessage());
        }
    }

    /**
     * 域名发现链：按优先级依次尝试主页url、主页url-c、域名-c、发布页、固定直链、境外永久、镜像源、回家的路。
     * 发现到域名后提取 hostUrl 和 baseUrl。
     * 本方法可能执行网络 I/O（fetchHtml/interpolate），调用方需 try-catch。
     */
    private void discoverDomain() {
        // 1. 主页url（直接配置的站点根 URL，清理反引号和首尾空格）
        String homeUrl = cleanUrl(config.get("", "主页url"));

        // 2. 主页url-c（带工具链的动态域名发现，如 [工具:源码#...] 提取发布页中的真实域名）
        String homeUrlC = config.get("", "主页url-c");
        if (!homeUrlC.isEmpty()) {
            homeUrlC = XBPQParse.interpolate(this, homeUrlC);
            if (!homeUrlC.isEmpty()) {
                homeUrl = homeUrlC;
            }
        }

        // 3. 域名-c（基于 {{主页url-c}} 变量做二次变换，如 [替换:https://>>https://666.]）
        String domainC = config.get("", "域名-c");
        if (!domainC.isEmpty()) {
            domainC = XBPQParse.interpolate(this, domainC);
            if (!domainC.isEmpty()) {
                homeUrl = domainC;
            }
        }

        // 4. 发布页（独立配置的发布页 URL，fetch 后从中提取域名）
        if (homeUrl.isEmpty()) {
            String publishPage = config.get("", "发布页");
            if (!publishPage.isEmpty()) {
                if (!publishPage.startsWith("http")) {
                    // 发布页本身可能是域名，补全协议
                    publishPage = "https://" + publishPage;
                }
                String publishHtml = XBPQHttp.fetchHtml(this, publishPage);
                if (publishHtml != null && !publishHtml.isEmpty()) {
                    // 从发布页源码中提取第一个 http(s) URL 作为主页
                    Matcher urlMatcher = URL_EXTRACT_PATTERN.matcher(publishHtml);
                    if (urlMatcher.find()) {
                        homeUrl = urlMatcher.group(1);
                    }
                }
            }
        }

        // 5. 固定直链：主页 url 为空时作为兜底域名
        if (homeUrl.isEmpty()) homeUrl = XBPQParse.interpolate(this, config.get("", "固定直链"));

        // 6. 境外永久：逗号分隔的备用域名列表，取第一个
        if (homeUrl.isEmpty()) {
            String overseas = config.get("", "境外永久");
            if (!overseas.isEmpty()) {
                String[] domains = overseas.split(",");
                for (String d : domains) {
                    if (d.trim().startsWith("http")) { homeUrl = d.trim(); break; }
                }
            }
        }

        // 7. 镜像源：逗号分隔的镜像域名列表
        if (homeUrl.isEmpty()) {
            String mirror = config.get("", "镜像源");
            if (!mirror.isEmpty()) {
                String[] domains = mirror.split(",");
                for (String d : domains) {
                    if (d.trim().startsWith("http")) { homeUrl = d.trim(); break; }
                }
            }
        }

        // 8. 回家的路：备用域名
        if (homeUrl.isEmpty()) {
            String goHome = config.get("", "回家的路");
            if (!goHome.isEmpty() && goHome.startsWith("http")) {
                homeUrl = goHome;
            }
        }

        Matcher hostMatcher = HOST_PATTERN.matcher(homeUrl);
        if (hostMatcher.find()) {
            hostUrl = hostMatcher.group(1);
            baseUrl = hostUrl;
        }
    }

    // ==================== 分类列表解析 ====================

    /**
     * 移除 JSON 字符串中的 // 注释和 # 注释（行内注释）。
     * XBPQ 配置中允许使用 // 和 # 写注释，但标准 JSON 不支持，解析前需清理。
     * 注意：不处理 JSON 值内部的 // 或 # 字符（如 URL 中含 //）。
     */
    public static String removeJsonComments(String json) {
        if (json == null) return "";
        StringBuilder sb = new StringBuilder();
        boolean inString = false;
        char prev = 0;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (inString) {
                sb.append(c);
                if (c == '\\' && prev == '\\') {
                    // 双反斜杠：转义序列，下一个字符不改变状态
                } else if (c == '"') {
                    inString = false;
                }
                prev = c;
            } else {
                if (c == '"') {
                    inString = true;
                    sb.append(c);
                } else if (c == '/' && i + 1 < json.length() && json.charAt(i + 1) == '/') {
                    // 行注释：跳过到行末
                    while (i < json.length() && json.charAt(i) != '\n') i++;
                } else if (c == '#') {
                    // 行注释（YAML风格）：跳过到行末
                    while (i < json.length() && json.charAt(i) != '\n') i++;
                } else {
                    sb.append(c);
                }
                prev = c;
            }
        }
        return sb.toString();
    }

    /**
     * 清理配置中读取的 URL 字符串：去除首尾空格和反引号。
     * XBPQ 配置中 URL 常被反引号包裹（如 `` `https://example.com` ``），
     * 不清理会导致 URL 验证失败和 HTTP 请求异常。
     * @param url 原始 URL 字符串
     * @return 清理后的 URL 字符串
     */
    public static String cleanUrl(String url) {
        if (url == null) return "";
        url = url.trim();
        // 去除首尾反引号（可能有多层）
        while (url.startsWith("`") && url.endsWith("`") && url.length() > 1) {
            url = url.substring(1, url.length() - 1).trim();
        }
        return url;
    }

    /**
     * 安全地将 Vod 转换为 JSONObject。
     * 先尝试解析 Gson 序列化串（vod.toString），失败则手工写入核心字段，
     * 避免特殊字符（如控制字符、非法转义）导致整条列表丢弃。
     * @param vod 视频对象
     * @return 至少包含 vod_id/vod_name/vod_pic/vod_remarks 的 JSONObject
     */
    public JSONObject vodToJson(Vod vod) {
        if (vod == null) return new JSONObject();
        try {
            return new JSONObject(vod.toString());
        } catch (Exception e) {
            // Gson 串含特殊字符解析失败，手工写入核心字段兜底
            JSONObject json = new JSONObject();
            try {
                json.put("vod_id", vod.getVodId());
                json.put("vod_name", vod.getVodName());
                json.put("vod_pic", vod.getVodPic());
                json.put("vod_remarks", vod.getVodRemarks());
            } catch (Exception ignored) {
            }
            return json;
        }
    }

    /**
     * 解析分类列表，兼容四种配置写法：
     * <ol>
     *   <li>标准 XBPQ："分类": "电影$1#电视剧$2"（name$value，# 分隔）</li>
     *   <li>名+值："分类": "电影&电视剧" + "分类值": "1&2"（& 分隔，平行数组）</li>
     *   <li>道长 XYQ："分类名称": "电影&电视剧" + "分类名称替换词": "1&2"</li>
     *   <li>仅名称："分类": "每日#每周"（name 同时作 tid，# 分隔）</li>
     * </ol>
     * 分隔符 # / & 都可；无 $ 时用名称作 tid。
     * @return 分类项列表，每项格式 "name$tid" 或 "name"（仅名称时）
     */
    private ArrayList<String> parseCategoryList() {
        ArrayList<String> result = new ArrayList<>();
        if (config == null) return result;
        // 动态分类数组：先从页面提取分类列表（优先于静态配置）
        if (!catArrayPre.isEmpty()) {
            try {
                String homeUrl = !baseUrl.isEmpty() ? baseUrl : hostUrl;
                if (homeUrl.isEmpty()) homeUrl = config.get("", "主页url", "主页");
                if (homeUrl.isEmpty()) homeUrl = cleanUrl(config.get("", "分类url", "分类链接", "fenlei"));
                if (!homeUrl.isEmpty() && !homeUrl.startsWith("http"))
                    homeUrl = baseUrl + (homeUrl.startsWith("/") ? "" : "/") + homeUrl;
                if (!homeUrl.isEmpty() && homeUrl.startsWith("http")) {
                    String homeHtml = XBPQHttp.fetchHtml(this, homeUrl);
                    if (homeHtml != null && !homeHtml.isEmpty()) {
                        String arrPre = catArrayPre.contains("&&") ? catArrayPre.split("&&")[0] : catArrayPre;
                        String arrSuf = catArrayPre.contains("&&")
                                ? catArrayPre.split("&&", 2)[1] : "</";
                        List<String> items = XBPQParse.extractAll(this, homeHtml, arrPre, arrSuf);
                        String titleSel = catTitleSel.isEmpty() ? ">&&<" : catTitleSel;
                        String idSel = catIdSel.isEmpty() ? "href=\"&&\"" : catIdSel;
                        for (String item : items) {
                            String name = XBPQParse.pick(this, item, titleSel);
                            String id = XBPQParse.pick(this, item, idSel);
                            if (!name.isEmpty()) result.add(name + "$" + id);
                        }
                        if (!result.isEmpty()) return result;
                    }
                }
            } catch (Exception ignored) {}
        }
        // 优先读"分类名称"（道长 XYQ），其次读"分类"（标准 XBPQ / 名+值 / 仅名称）
        String names = config.get("", "分类名称", "分类");
        if (names.isEmpty()) return result;
        // 道长 XYQ："分类名称替换词" 或 标准名+值："分类值"
        String values = config.get("", "分类名称替换词", "分类值");
        if (!values.isEmpty()) {
            // 名+值平行数组模式：names 和 values 一一对应，合并为 "name$value"
            result = mergeNameValueCategories(names, values);
        } else {
            // 单字段模式：标准 XBPQ（name$value）或仅名称
            // 同时支持 # 和 & 作为分隔符
            String[] items = names.split("[#&]");
            for (String item : items) {
                if (item == null || item.isEmpty()) continue;
                result.add(item.trim());
            }
        }
        return result;
    }

    /**
     * 合并平行分类名称和值数组。
     * @param names  分类名称串（& 或 # 分隔）
     * @param values 分类值串（& 或 # 分隔，与 names 一一对应）
     * @return "name$value" 列表
     */
    private ArrayList<String> mergeNameValueCategories(String names, String values) {
        ArrayList<String> result = new ArrayList<>();
        if (names == null || values == null) return result;
        String[] nameArr = names.split("[#&]");
        String[] valueArr = values.split("[#&]");
        for (int i = 0; i < nameArr.length && i < valueArr.length; i++) {
            String name = nameArr[i].trim();
            String value = valueArr[i].trim();
            if (name.isEmpty()) continue;
            result.add(name + "$" + value);
        }
        return result;
    }

    /**
     * 注入 SpiderApi（框架可选调用；Spider 基类未声明此方法，故不加 @Override）。
     * 默认 spiderApi 实例已能通过 SpiderDebug 输出日志，此方法用于框架注入真实实例。
     */
    public void initApi(SpiderApi api) {
        if (api == null) return;
        this.spiderApi = api;
        this.port = api.getPort();
        if (this.port == null || this.port.isEmpty()) this.port = DEFAULT_PORT;
        Init.setLocalProxyPort(this.port);
        api.log("Id版端口：" + port);
    }

    // ==================== 首页 ====================

    @Override
    public String homeContent(boolean filter) throws Exception {
        // 分类列表在 try 块外构建，确保即使 fetchCategory 异常也能返回分类标签
        List<Class> classes = buildClasses();
        // 日志：输出分类列表详情（type_id 和 type_name），确认 Gson 序列化前数据正确
        if (spiderApi != null) {
            StringBuilder clsLog = new StringBuilder("homeContent 分类列表(").append(classes.size()).append("):\n");
            for (Class c : classes) {
                clsLog.append("  type_id=").append(c.getTypeId())
                      .append(", type_name=").append(c.getTypeName()).append("\n");
            }
            spiderApi.log(clsLog.toString());
        }
        try {
            currentMethod = "首页";
            if (config == null) {
                if (spiderApi != null) spiderApi.log("homeContent 失败：config 未初始化（init 未执行或 extend 解析失败）");
                String json = Result.string(classes, Collections.<Vod>emptyList(), new LinkedHashMap<>());
                if (spiderApi != null) spiderApi.log("homeContent 返回(config=null): " + json);
                return json;
            }

            // 域名兜底：fillFields 域名发现失败时，从"主页url"重新提取
            if (baseUrl == null || baseUrl.isEmpty()) {
                String homeUrl = cleanUrl(config.get("", "主页url"));
                if (!homeUrl.isEmpty()) {
                    Matcher hostMatcher = HOST_PATTERN.matcher(homeUrl);
                    if (hostMatcher.find()) {
                        hostUrl = hostMatcher.group(1);
                        baseUrl = hostUrl;
                        if (spiderApi != null) spiderApi.log("homeContent 域名兜底提取: " + baseUrl);
                    }
                }
            }
            // 分类 URL 模板兜底：fillFields 未读到时重新读取
            if (categoryUrlTemplate == null || categoryUrlTemplate.isEmpty()) {
                categoryUrlTemplate = cleanUrl(config.get("", "分类url", "分类链接", "fenlei"));
            }

            if (spiderApi != null) {
                spiderApi.log("homeContent: baseUrl=" + baseUrl + ", categoryUrlTemplate=" + categoryUrlTemplate);
            }

            // 首页列表：取第一个分类的内容作为首页推荐
            String homeTid = classes.isEmpty() ? "1" : classes.get(0).getTypeId();
            JSONObject result = fetchCategory(homeTid, "1", filter, null);
            List<Vod> list = XBPQParse.parseVodList(result);
            if (spiderApi != null) spiderApi.log("homeContent: fetchCategory 返回 " + (list != null ? list.size() : 0) + " 条结果");
            // 横图模式：为 Vod 列表设置 rect 样式标记，驱动前端 16:9 横图渲染
            if (horizontalMode && list != null) {
                for (Vod vod : list) {
                    vod.setStyle(Vod.Style.rect());
                }
            }

            LinkedHashMap<String, List<Filter>> filters = filter ? XBPQParse.buildFilters(this) : new LinkedHashMap<>();
            String json = Result.string(classes, list, filters);
            if (spiderApi != null) spiderApi.log("homeContent 返回JSON(" + json.length() + "字符): " + (json.length() > 500 ? json.substring(0, 500) + "..." : json));
            return json;
        } catch (Exception e) {
            SpiderDebug.log(e);
            if (spiderApi != null) spiderApi.log("homeContent 异常: " + e.getMessage());
            // 异常时保留已构建的分类列表，返回空 Vod 列表（不丢弃分类标签）
            String json = Result.string(classes, Collections.<Vod>emptyList(), new LinkedHashMap<>());
            if (spiderApi != null) spiderApi.log("homeContent 异常返回: " + json);
            return json;
        }
    }

    /**
     * 构建分类列表（List<Class>）。
     * 从 categoryList 配置项解析，避免 type_id 重复（TVBox 前端按 type_id 去重，重复会导致分类标签消失）。
     * 若配置中无分类，返回仅含"全部"的默认列表。
     */
    private List<Class> buildClasses() {
        List<Class> classes = new ArrayList<>();
        Set<String> usedTypeIds = new HashSet<>();
        if (categoryList != null && !categoryList.isEmpty()) {
            for (String item : categoryList) {
                if (item == null || item.isEmpty()) continue;
                if (item.contains("clan://")) continue;
                String[] parts = item.split("\\$");
                String tid, tname;
                if (parts.length >= 2) {
                    tid = parts[1].trim();
                    tname = parts[0].trim();
                } else {
                    tid = item.trim();
                    tname = item.trim();
                }
                if (tid.isEmpty() || tname.isEmpty()) continue;
                // 跳过重复 type_id（TVBox 前端按 type_id 去重）
                if (usedTypeIds.contains(tid)) continue;
                usedTypeIds.add(tid);
                classes.add(new Class(tid, tname));
            }
        }
        // 无分类配置时添加默认"全部"
        if (classes.isEmpty()) {
            classes.add(new Class("1", "全部"));
        }
        return classes;
    }

    @Override
    public String homeVideoContent() throws Exception {
        try {
            if (config == null) {
                if (spiderApi != null) spiderApi.log("homeVideoContent 失败：config 未初始化");
                return Result.string(Collections.<Vod>emptyList());
            }
            // 首页列表：取第一个分类的内容作为首页推荐
            List<Class> classes = buildClasses();
            String homeTid = classes.isEmpty() ? "1" : classes.get(0).getTypeId();
            JSONObject result = fetchCategory(homeTid, "1", false, null);
            List<Vod> list = XBPQParse.parseVodList(result);
            return Result.string(list);
        } catch (Exception e) {
            SpiderDebug.log(e);
            return Result.string(Collections.<Vod>emptyList());
        }
    }

    // ==================== 分类 ====================

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        try {
            if (spiderApi != null) spiderApi.log("categoryContent(tid=" + tid + ", pg=" + pg + ", filter=" + filter + ")");
            currentMethod = "分类";
            if (config == null) {
                if (spiderApi != null) spiderApi.log("categoryContent 失败：config 未初始化");
                return "";
            }
            JSONObject result = fetchCategory(tid, pg, filter, extend);
            String resultJson = result == null ? "" : result.toString();
            // 始终返回结果 JSON（即使列表为空 {"list":[]} 也需返回，否则前端无法显示分类页结构）
            return resultJson;
        } catch (Exception e) {
            SpiderDebug.log(e);
            return "";
        }
    }

    /**
     * 分类核心流程：URL 模板填充 → HTTP 请求 → 列表解析 → 分页信息组装。
     */
    private JSONObject fetchCategory(String tid, String pg, boolean filter, HashMap<String, String> extend) {
        JSONObject result = new JSONObject();
        try {
            int page = XBPQParse.parseInt(pg, 1);
            // 分类分页关闭时仅返回第一页
            if (!categoryPaging && page > 1) {
                result.put("list", new JSONArray());
                result.put("page", page);
                result.put("pagecount", 1);
                return result;
            }
            // 起始页：部分站点从 0 开始分页，起始页配置调整 URL 中的页码
            int startPage = config.getInt(1, "起始页", "qishiye", "firstpage");
            int urlPage = page - 1 + startPage;
            // 翻页步长：部分站点页码按步长递增（如 10, 20, 30）
            if (pageStep > 0) urlPage = (page - 1) * pageStep + startPage;
            // 静态分页：始终使用第一页
            if (staticPaging) urlPage = 1;

            // 特殊分类链接覆盖：name$url 格式，匹配当前分类名时使用专属 URL 模板
            String currentName = tidToName.get(tid);
            String specialCatLink = config.get("", "特殊分类链接");
            String url = null;
            if (!specialCatLink.isEmpty() && currentName != null) {
                for (String link : specialCatLink.split("#")) {
                    String[] parts = link.split("\\$", 2);
                    if (parts.length >= 2 && parts[0].equals(currentName)) {
                        url = XBPQParse.interpolate(this, parts[1]);
                        break;
                    }
                }
            }

            if (url == null || url.isEmpty()) {
                url = categoryUrlTemplate;
                if (url == null || url.isEmpty()) url = cleanUrl(config.get("", "分类url", "分类链接", "fenlei"));
                // 变量插值：展开 {{域名-c}} 等变量引用
                url = XBPQParse.interpolate(this, url);
            }
            if (url == null || url.isEmpty()) {
                result.put("list", new JSONArray());
                return result;
            }
            url = url.replace("{cateId}", tid).replace("{catePg}", String.valueOf(urlPage))
                     .replace("{cateid}", tid).replace("{catepg}", String.valueOf(urlPage));
            // 筛选占位符替换：从 extend 读取用户选择，未选时用空串（否则 URL 带字面量 {class} 等导致请求失败）
            url = url.replace("{class}", extend != null && extend.containsKey("class") ? XBPQParse.safeGet(extend, "class") : "");
            url = url.replace("{year}", extend != null && extend.containsKey("year") ? XBPQParse.safeGet(extend, "year") : "");
            url = url.replace("{area}", extend != null && extend.containsKey("area") ? XBPQParse.safeGet(extend, "area") : "");
            url = url.replace("{by}", extend != null && extend.containsKey("by") ? XBPQParse.safeGet(extend, "by") : "");
            url = url.replace("{letter}", extend != null && extend.containsKey("letter") ? XBPQParse.safeGet(extend, "letter") : "");
            url = url.replace("{lang}", extend != null && extend.containsKey("lang") ? XBPQParse.safeGet(extend, "lang") : "");

            // 二级目录：匹配分类名时追加后缀（格式：name1,name2,...|suffix）
            String secondaryDir = config.get("", "二级目录");
            if (!secondaryDir.isEmpty() && secondaryDir.contains("|") && currentName != null) {
                String[] dirParts = secondaryDir.split("\\|", 2);
                String[] dirNames = dirParts[0].split(",");
                for (String dirName : dirNames) {
                    if (dirName.trim().equals(currentName)) {
                        String dirSuffix = dirParts[1];
                        // 替换筛选占位符
                        String classVal = extend != null && extend.containsKey("class") ? XBPQParse.safeGet(extend, "class") : "0";
                        String areaVal = extend != null && extend.containsKey("area") ? XBPQParse.safeGet(extend, "area") : "0";
                        String byVal = extend != null && extend.containsKey("by") ? XBPQParse.safeGet(extend, "by") : "H";
                        dirSuffix = dirSuffix.replace("{class}", classVal)
                                .replace("{area}", areaVal)
                                .replace("{by}", byVal);
                        url = url + dirSuffix;
                        break;
                    }
                }
            }
            // 列表后缀：URL 末尾追加后缀
            String listSuffix = config.get("", "列表后缀", "houzhui");
            if (!listSuffix.isEmpty()) url = url + listSuffix;
            // URL 备用地址：url1[url2];;flags 格式，url1 失败时尝试 url2
            // ;;flags 控制后缀（z/g/c等）保留在主 URL 上，由 fetchHtml 内部解析处理
            String backupUrl = "";
            int bracketIdx = url.indexOf('[');
            if (bracketIdx > 0) {
                int closeIdx = url.indexOf(']', bracketIdx);
                if (closeIdx > bracketIdx) {
                    backupUrl = url.substring(bracketIdx + 1, closeIdx);
                    // 主 URL = ] 之前部分 + ] 之后部分（;;flags 保留）
                    url = url.substring(0, bracketIdx) + url.substring(closeIdx + 1);
                }
            }
            if (!url.startsWith("http")) {
                url = baseUrl + (url.startsWith("/") ? "" : "/") + url;
            }
            String html;
            // ;post 标记仅移除标记本身，保留 ;;flags 后缀供 fetchHtml/fetchPost 处理
            if (url.contains(";post")) {
                html = XBPQHttp.fetchPost(this, url.replace(";post", ""));
            } else {
                html = XBPQHttp.fetchHtml(this, url);
            }
            // 首次请求失败时尝试备用 URL（备用 URL 也经 fetchHtml 处理 ;;flags）
            if ((html == null || html.isEmpty()) && !backupUrl.isEmpty()) {
                backupUrl = backupUrl.replace("{cateId}", tid).replace("{catePg}", String.valueOf(urlPage));
                if (!backupUrl.startsWith("http")) {
                    backupUrl = baseUrl + (backupUrl.startsWith("/") ? "" : "/") + backupUrl;
                }
                html = XBPQHttp.fetchHtml(this, backupUrl);
            }
            if (html == null || html.isEmpty()) {
                result.put("list", new JSONArray());
                return result;
            }

            html = html.replaceAll("class=\"pages\"[^>]*>.*?</div>", "")
                    .replace("热门电", "").replace("感兴趣", "").replace("热播影", "");

            // 二次截取：支持组合格式 "pre&&suf"（单键）和分离格式 "二次截取"+"二次截取后"
            String twiceRaw = config.get("", "二次截取", "jiequqian", "cat_twice_pre");
            String twicePre, twiceSuf;
            if (twiceRaw.contains("&&")) {
                // 组合格式：pre&&suf
                String[] twiceParts = twiceRaw.split("&&");
                twicePre = twiceParts[0];
                twiceSuf = twiceParts.length > 1 ? twiceParts[1] : "";
            } else {
                twicePre = twiceRaw;
                twiceSuf = config.get("", "二次截取后", "jiequhou", "cat_twice_suf");
            }
            if (!twicePre.isEmpty() && !twiceSuf.isEmpty()) {
                String[] cutPrefixes = twicePre.split("##");
                String[] cutSuffixes = twiceSuf.split("##");
                for (int i = 0; i < cutPrefixes.length && i < cutSuffixes.length; i++) {
                    html = XBPQParse.extractBetween(html, cutPrefixes[i], cutSuffixes[i]);
                }
            }

            // 数组二次截取：在数组提取前再进行一次截取
            String arrTwiceRaw = config.get("", "数组二次截取", "arr_twice_pre");
            if (!arrTwiceRaw.isEmpty() && arrTwiceRaw.contains("&&")) {
                String[] arrTwiceParts = arrTwiceRaw.split("&&");
                String arrTwicePre = arrTwiceParts[0];
                String arrTwiceSuf = arrTwiceParts.length > 1 ? arrTwiceParts[1] : "";
                if (!arrTwicePre.isEmpty() && !arrTwiceSuf.isEmpty()) {
                    html = XBPQParse.extractBetween(html, arrTwicePre, arrTwiceSuf);
                }
            }

            // 数组：支持组合格式 "pre&&suf"（单键）和分离格式 "数组"+"数组后"，支持 || 多段选择器
            // 分类选择器为空时回退到搜索选择器（很多配置不单独定义分类选择器）
            String arrRaw = XBPQParse.resolveMultiSection(this, config.get("", "数组", "jiequshuzuqian", "cateVodNode", "cat_arr_pre"), tid);
            String arrPre, arrSuf;
            if (arrRaw.contains("&&")) {
                String[] arrParts = arrRaw.split("&&");
                arrPre = arrParts[0];
                arrSuf = arrParts.length > 1 ? arrParts[1] : "";
            } else {
                arrPre = arrRaw;
                arrSuf = config.get("", "数组后", "jiequshuzuhou", "cat_arr_suf");
            }
            // 分类数组为空时回退到搜索数组
            if (arrPre.isEmpty()) {
                String seaArrRaw = config.get("", "搜索数组", "ssjiequshuzuqian", "sea_arr_pre");
                if (seaArrRaw.contains("&&")) {
                    String[] seaArrParts = seaArrRaw.split("&&");
                    arrPre = seaArrParts[0];
                    arrSuf = seaArrParts.length > 1 ? seaArrParts[1] : (arrSuf.isEmpty() ? "" : arrSuf);
                } else if (!seaArrRaw.isEmpty()) {
                    arrPre = seaArrRaw;
                    if (arrSuf.isEmpty()) arrSuf = config.get("", "搜索截取数组", "ssjiequshuzuhou", "sea_arr_suf");
                }
            }
            if (arrPre.isEmpty()) arrPre = "<li";
            if (arrSuf.isEmpty()) arrSuf = "</li>";

            // 字段选择器：分类为空时回退到搜索选择器，都为空时用默认值
            String titleSel = XBPQParse.resolveMultiSection(this, config.get("", "标题", "biaotiqian", "catjsonname", "cat_title"), tid);
            if (titleSel.isEmpty()) titleSel = XBPQParse.resolveMultiSection(this, config.get("", "搜索标题", "ssbiaotiqian", "sea_title"), tid);
            if (titleSel.isEmpty()) titleSel = ">&&<";

            String picSel = XBPQParse.resolveMultiSection(this, config.get("", "图片", "tupianqian", "catjsonpic", "cat_pic"), tid);
            if (picSel.isEmpty()) picSel = XBPQParse.resolveMultiSection(this, config.get("", "搜索图片", "sstupianqian", "sea_pic"), tid);
            if (picSel.isEmpty()) picSel = "data-original=\"&&\"";

            String linkSel = XBPQParse.resolveMultiSection(this, config.get("", "链接", "lianjieqian", "catjsonid", "cat_url"), tid);
            if (linkSel.isEmpty()) linkSel = XBPQParse.resolveMultiSection(this, config.get("", "搜索链接", "搜索前", "sea_url"), tid);
            if (linkSel.isEmpty()) linkSel = "href=\"&&\"";

            String subSel = XBPQParse.resolveMultiSection(this, config.get("", "副标题", "fubiaotiqian", "catjsonstitle", "cat_subtitle"), tid);
            if (subSel.isEmpty()) subSel = XBPQParse.resolveMultiSection(this, config.get("", "搜索副标题", "ssfubiaotiqian", "sea_subtitle"), tid);

            String linkPre = config.get("", "链接前缀", "ljqianzhui", "cat_prefix");
            if (linkPre.isEmpty()) linkPre = config.get("", "搜索链接前缀", "ssljqianzhui");
            String linkSuf = config.get("", "链接后缀", "ljhouzhui", "cat_suffix");
            if (linkSuf.isEmpty()) linkSuf = config.get("", "搜索链接后缀", "ssljhouzhui");

            List<String> items = XBPQParse.extractAll(this, html, arrPre, arrSuf);
            if (items.isEmpty() && spiderApi != null) {
                spiderApi.log("分类列表为空：数组前缀[" + arrPre + "] 数组后缀[" + arrSuf + "] 未匹配到内容");
            } else if (debug && spiderApi != null) {
                spiderApi.log("分类列表命中：" + items.size() + " 项（数组[" + arrPre + "&&" + arrSuf + "]）");
            }
            JSONArray list = new JSONArray();
            int missCount = 0;
            for (String item : items) {
                try {
                    String title = XBPQParse.pick(this, item, titleSel);
                    String pic = XBPQParse.pick(this, item, picSel);
                    String link = XBPQParse.pick(this, item, linkSel);
                    String sub = XBPQParse.pick(this, item, subSel);
                    // debug 模式下统计字段未命中次数
                    if (debug && title.isEmpty() && link.isEmpty()) missCount++;
                    if (title.isEmpty() && link.isEmpty()) continue;
                    if (!linkPre.isEmpty() && !link.startsWith("http")) link = linkPre + link;
                    if (!linkSuf.isEmpty()) link = link + linkSuf;
                    if (!link.startsWith("http") && !link.startsWith("//")) {
                        link = baseUrl + (link.startsWith("/") ? "" : "/") + link;
                    }
                    // 封面修正：coverFix 开启时补全图片 URL 协议和路径
                    if (coverFix && !pic.isEmpty() && !pic.startsWith("http") && !pic.startsWith("//")) {
                        pic = baseUrl + (pic.startsWith("/") ? "" : "/") + pic;
                    }
                    Vod vod = new Vod(link, title, pic, sub);
                    // 图文模式：设置标记供前端识别
                    if (imageTextMode) vod.setVodTag("图文");
                    // 横图模式：设置 rect 样式标记，驱动前端 16:9 横图渲染
                    if (horizontalMode) vod.setStyle(Vod.Style.rect());
                    if (tid != null && tid.contains("shortVideo$")) {
                        vod.setVodId(link + "$$$" + link + "$$$" + title);
                    }
                    JSONObject vodJson = vodToJson(vod);
                    list.put(vodJson);
                } catch (Exception ignored) {
                }
            }
            // debug 模式下输出选择器未命中统计
            if (debug && spiderApi != null && missCount > 0) {
                spiderApi.log("分类字段未命中：" + missCount + "/" + items.size()
                        + " 项标题和链接均为空（标题[" + titleSel + "] 链接[" + linkSel + "]）");
            }

            int pageCount = XBPQParse.parsePageCount(html);
            result.put("page", page);
            result.put("pagecount", pageCount);
            result.put("limit", pageSize > 0 ? pageSize : list.length());
            // total 优先从页面提取真实总数，提取失败时用页数×每页条数估算
            int total = XBPQParse.parseTotalCount(html);
            result.put("total", total > 0 ? total : pageCount * list.length());
            result.put("list", list);
        } catch (Exception e) {
            SpiderDebug.log(e);
            try {
                result.put("list", new JSONArray());
            } catch (Exception ignored) {
            }
        }
        return result;
    }

    // ==================== 详情 ====================

    @Override
    public String detailContent(List<String> ids) throws Exception {
        try {
            if (spiderApi != null) spiderApi.log("detailContent(ids=" + ids + ")");
            currentMethod = "详情";

            String id = ids.get(0);
            String[] parts = id.split("\\$\\$\\$");

            // 阿里云盘分享链接
            if (id.contains("aliyundrive") || id.contains("alipan")) {
                return XBPQAliPa.detailAliContent(this, ids);
            }

            // 磁力链接 / 本地代理地址 → PA 详情解析
            if (id.startsWith("magnet:") || id.contains("127.0.0.1:9978")) {
                if (parts.length == 1) return XBPQAliPa.detailPaContent(this, ids);
            }

            // 二级网站域名 / 是否站外链接域名 → PA 详情解析
            if (baseUrl.contains("二级网站域名") || baseUrl.contains("是否站外链接域名")) {
                if (parts.length == 1) return XBPQAliPa.detailPaContent(this, ids);
            }

            if (config == null) {
                if (spiderApi != null) spiderApi.log("detailContent 失败：config 未初始化");
                return "";
            }

            // ID 格式：tid$$$url$$$name[$$$playerMode]
            String tid = parts.length > 0 ? parts[0] : id;
            String detailUrl = parts.length > 1 ? parts[1] : "";
            String name = parts.length > 2 ? parts[2] : "";

            // 直播/特殊播放模式检测：parts[3] 为 "播放器" 或以 "Json:" 开头
            boolean liveMode = false;
            if (parts.length > 3) {
                String mode = parts[3];
                if ("播放器".equals(mode) || mode.startsWith("Json:")) {
                    liveMode = true;
                }
            }

            // 详情页 URL 构建
            // "详情页$$$网页" 组合键：config 同时指定详情页和网页配置时优先使用 parts[1]
            boolean hasCombinedKey = sniffConfig.contains("详情页$$$网页");
            if (detailUrl.isEmpty() || hasCombinedKey) {
                String configDetail = config.get("", "详情页", "网页");
                if (!configDetail.isEmpty()) detailUrl = configDetail;
            }
            if (detailUrl.isEmpty() && !tid.isEmpty()) {
                if (tid.startsWith("http")) detailUrl = tid;
                else if (tid.startsWith("/")) detailUrl = baseUrl + tid;
                else detailUrl = baseUrl + "/" + tid;
            }
            if (detailUrl.isEmpty()) return "";

            // URL 清洗
            detailUrl = detailUrl.replace("手机端网站域名", "电脑端网站域名");
            if (detailUrl.startsWith("/") && !detailUrl.startsWith("//")) {
                detailUrl = baseUrl + detailUrl;
            }

            // XPath 模式：URL 以 \\ 开头时走节点解析
            if (detailUrl.startsWith("\\\\") && !secondaryCutEnabled && !liveMode) {
                return xpDetailContent(ids, detailUrl);
            }

            // 获取详情页源码（;post 标记仅移除标记本身，;;flags 后缀保留供 fetchHtml/fetchPost 处理）
            String html;
            if (detailUrl.contains(";post")) {
                html = XBPQHttp.fetchPost(this, detailUrl.replace(";post", ""));
            } else {
                html = XBPQHttp.fetchHtml(this, detailUrl);
            }
            if (html == null || html.isEmpty()) {
                if (spiderApi != null) spiderApi.log("详情页源码为空：" + detailUrl);
                return "";
            }

            // 跳转详情：config 含"跳转详情"时，从首页源码提取二级 URL 再请求
            String jumpDetailSel = config != null ? config.get("", "跳转详情") : "";
            if (!jumpDetailSel.isEmpty()) {
                String jumpUrl = XBPQParse.pick(this, html, jumpDetailSel);
                if (!jumpUrl.isEmpty()) {
                    if (!jumpUrl.startsWith("http") && !jumpUrl.startsWith("//")) {
                        jumpUrl = baseUrl + (jumpUrl.startsWith("/") ? "" : "/") + jumpUrl;
                    }
                    if (spiderApi != null) spiderApi.log("请求跳转详情源码，webUrl--> " + jumpUrl);
                    String jumpHtml;
                    if (jumpUrl.contains(";post")) {
                        jumpHtml = XBPQHttp.fetchPost(this, jumpUrl.replace(";post", ""));
                    } else {
                        jumpHtml = XBPQHttp.fetchHtml(this, jumpUrl);
                    }
                    if (jumpHtml != null && !jumpHtml.isEmpty()) {
                        html = jumpHtml;
                    }
                }
            }

            // 阿里云盘响应检测：HTTP 源码中包含阿里云盘分享链接时委托阿里详情解析
            Matcher aliMatcher = ALIYUN_PATTERN.matcher(html);
            if (aliMatcher.find()) {
                String aliUrl = aliMatcher.group(0).replace("\\", "");
                return XBPQAliPa.detailAliContent(this, Arrays.asList(aliUrl));
            }

            // 构建详情 Vod
            Vod vod = new Vod();
            // vod_id 仅存 tid，cookie 已作为实例字段 cookieStr 在 playerContent 中可用
            // 不再将 cookie 追加到 vod_id，避免日志/分享时泄露
            vod.setVodId(tid);
            vod.setVodName(name.isEmpty() ? XBPQParse.extractField(this, html, "标题", "biaoti", "name", "dtName") : name);
            vod.setVodPic(XBPQParse.extractField(this, html, "图片", "tupian", "pic", "dtImg"));
            vod.setTypeName(XBPQParse.extractField(this, html, "分类", "类型", "fenlei", "leixing", "dtCate"));
            vod.setVodYear(XBPQParse.extractField(this, html, "年份", "nianfen", "dtYear"));
            vod.setVodArea(XBPQParse.extractField(this, html, "地区", "diqu", "dtArea"));
            vod.setVodRemarks(XBPQParse.extractField(this, html, "状态", "zhuangtai", "dtMark"));
            vod.setVodDirector(XBPQParse.extractField(this, html, "导演", "daoyan", "dtDirector"));
            vod.setVodActor(XBPQParse.extractField(this, html, "主演", "zhuyan", "dtActor"));
            vod.setVodContent(XBPQParse.extractField(this, html, "简介", "jianjie", "dtDesc"));

            // 二级截取：精确选段优先，回退到数字下标分段
            // 优先级：CSS 选择器 > 前后字符串截取 > <ul/<div 数字下标分段
            if (secondaryCutEnabled) {
                String cutHtml = XBPQParse.secondaryCutHtml(this, html);
                if (!cutHtml.isEmpty()) {
                    html = cutHtml;
                } else if (spiderApi != null && debug) {
                    spiderApi.log("二级截取未命中，保留原文");
                }
            }

            // 播放列表解析
            String playFrom = XBPQParse.parsePlayFrom(this, html);
            String playUrl = XBPQParse.parsePlayUrl(this, html, tid);

            if (playFrom.isEmpty()) playFrom = "默认线路";
            if (playUrl.isEmpty()) {
                playUrl = XBPQParse.parseDefaultEpisodes(this, html, tid);
            }
            if (playUrl.isEmpty() && spiderApi != null) {
                spiderApi.log("播放列表为空，详情URL=" + detailUrl);
            }

            // 线路合并：值为"不分线路"时将所有线路合并为一条
            if (config != null) {
                String lineMerge = config.get("", "线路合并");
                if ("不分线路".equals(lineMerge) && !playUrl.isEmpty()) {
                    playUrl = playUrl.replaceAll("\\$\\$\\$", "#");
                    playFrom = "合并线路";
                }
            }

            // 直播模式：多线路时分别命名为"直播源1/2/..."，单线路用"直播列表"
            if (liveMode) {
                if (playUrl.contains("$$$")) {
                    String[] lines = playUrl.split("\\$\\$\\$");
                    StringBuilder playFromBuilder = new StringBuilder();
                    for (int i = 0; i < lines.length; i++) {
                        if (i > 0) playFromBuilder.append("$$$");
                        playFromBuilder.append("直播源").append(i + 1);
                    }
                    playFrom = playFromBuilder.toString();
                } else {
                    playFrom = "直播列表";
                }
            }

            vod.setVodPlayFrom(playFrom);
            vod.setVodPlayUrl(playUrl);

            // 倒序处理
            if (reverseEpisodes && !playUrl.isEmpty()) {
                vod.setVodPlayUrl(XBPQParse.reverseEpisodesInUrl(playUrl));
            }

            List<Vod> list = new ArrayList<>();
            list.add(vod);
            return Result.string(list);
        } catch (Exception e) {
            SpiderDebug.log(e);
            if (spiderApi != null) spiderApi.log("detailContent 异常：" + e.getMessage());
            return "";
        }
    }

    // ==================== 播放 ====================

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        try {
            if (spiderApi != null) spiderApi.log("playerContent(flag=" + flag + ", id=" + id + ")");
            currentMethod = "播放";

            // 阶段 1：PA flag 委托
            if (">超清<".equals(flag) || ">2K<".equals(flag) || ">原画<".equals(flag)) {
                return XBPQAliPa.playerPaContent(this, flag, id, vipFlags);
            }

            if (config == null) {
                if (spiderApi != null) spiderApi.log("playerContent 失败：config 未初始化");
                return "";
            }

            // 阶段 2：URL 前处理（路径补全、xp 协议、;post 请求）
            String playUrl = XBPQPlayer.preprocessPlayUrl(this, id);

            // 直接播放模式："直接播放"=1 时跳过所有嗅探/跳转，直接返回播放地址
            if (directPlay && playUrl.startsWith("http")) {
                return Result.get().parse(0).url(playUrl).header(XBPQHttp.buildHeaderMap(this)).string();
            }

            // 阶段 3：JS 渲染（sniffConfig 含 J）
            String jsResult = XBPQPlayer.handleJsRender(this, playUrl);
            if (jsResult != null) return jsResult;

            // 阶段 4：直链判断
            String directResult = XBPQPlayer.handleDirectLink(this, playUrl);
            if (directResult != null) return directResult;

            // 阶段 5：btwaf 防护处理
            playUrl = XBPQPlayer.handleBtwafProtection(this, playUrl);

            // 阶段 6：验证码异步处理
            XBPQPlayer.handleVerificationAsync(this, playUrl);

            // 阶段 7：免嗅处理（skipSniff=1 时跳过）
            if (!skipSniff) {
                String sniffResult = XBPQPlayer.handleSniffing(this, playUrl);
                if (sniffResult != null) return sniffResult;
            }

            // 阶段 8：加密播放解密
            String decryptResult = XBPQPlayer.handleDecryption(this, playUrl);
            if (decryptResult != null) return decryptResult;

            // 阶段 9：多级跳转解析
            String jumpResult = XBPQPlayer.handleJumpResolution(this, playUrl);
            if (jumpResult != null) return jumpResult;

            // 阶段 10：最终组装
            return XBPQPlayer.buildFinalResult(this, playUrl, id);
        } catch (Exception e) {
            SpiderDebug.log(e);
            if (spiderApi != null) spiderApi.log("playerContent 异常：" + e.getMessage());
            return "";
        }
    }

    // ==================== 搜索 ====================

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        try {
            if (spiderApi != null) spiderApi.log("searchContent(key=" + key + ", quick=" + quick + ")");
            currentMethod = "搜索";
            if (config == null) {
                if (spiderApi != null) spiderApi.log("searchContent 失败：config 未初始化");
                return "";
            }

            JSONObject result = searchOnce("", key, quick);

            // 特殊分类检测（config 已在方法入口校验非空）
            String specialCategory = config.get("", "特殊分类");
            String specialCategoryUrl = config.get("", "特殊分类url");
            String specialCategoryLink = config.get("", "特殊分类链接");
            if (!specialCategory.isEmpty() && key.contains(specialCategory)) {
                if (!specialCategoryUrl.isEmpty()) {
                    String specialUrl = specialCategoryUrl.replace("{wd}", key);
                    if (!specialUrl.startsWith("http")) {
                        specialUrl = baseUrl + (specialUrl.startsWith("/") ? "" : "/") + specialUrl;
                    }
                    JSONObject specialResult = searchOnce(specialUrl, key, quick);
                    if (specialResult != null && specialResult.optJSONArray("list") != null
                            && specialResult.getJSONArray("list").length() > 0) {
                        return specialResult.toString();
                    }
                }
                if (!specialCategoryLink.isEmpty()) {
                    JSONArray list = new JSONArray();
                    String[] links = specialCategoryLink.split("#");
                    for (String link : links) {
                        if (link.isEmpty()) continue;
                        String[] parts = link.split("\\$");
                        if (parts.length >= 2) {
                            Vod vod = new Vod(parts[1], parts[0], "", "");
                            list.put(vodToJson(vod));
                        }
                    }
                    if (list.length() > 0) {
                        return new JSONObject().put("list", list).toString();
                    }
                }
            }

            if (result != null && result.optJSONArray("list") != null && result.getJSONArray("list").length() >= 1) {
                return result.toString();
            }

            // 多站点搜索：sniffConfig 按 ";" 分割，结果按 vod_name 去重
            if (!sniffConfig.isEmpty() && sniffConfig.contains(";")) {
                JSONArray allResults = new JSONArray();
                Set<String> seenNames = new HashSet<>();
                for (String site : sniffConfig.split(";")) {
                    if (site == null || !site.startsWith("h")) continue;
                    JSONObject siteResult = searchOnce(site, key, quick);
                    if (siteResult != null && siteResult.optJSONArray("list") != null) {
                        JSONArray list = siteResult.getJSONArray("list");
                        for (int i = 0; i < list.length(); i++) {
                            try {
                                JSONObject item = list.getJSONObject(i);
                                String name = item.optString("vod_name", "");
                                // 按 vod_name 去重，空名始终保留
                                if (name.isEmpty() || seenNames.add(name)) {
                                    allResults.put(item);
                                }
                            } catch (Exception ignored) {
                            }
                        }
                    }
                }
                if (allResults.length() > 0) {
                    return new JSONObject().put("list", allResults).toString();
                }
            }

            // 兜底：拼接 host 搜索首页
            if (!baseUrl.isEmpty()) {
                result = searchOnce(baseUrl + "/;;搜首页", key, quick);
                if (result != null) return result.toString();
            }

            return new JSONObject().put("list", new JSONArray()).toString();
        } catch (Exception e) {
            SpiderDebug.log(e);
            if (spiderApi != null) spiderApi.log("searchContent 异常：" + e.getMessage());
            return "";
        }
    }

    /**
     * 搜索核心：支持 HTML / JSON / XML 三种模式。
     */
    private JSONObject searchOnce(String url, String key, boolean quick) {
        JSONObject result = new JSONObject();
        try {
            String searchUrl = cleanUrl(config.get("", "搜索url", "搜索链接", "搜索前", "search_url", "searchUrl", "sousuoqian"));
            String searchSuf = config.get("", "搜索后缀", "sousuohouzhui");
            // 变量插值：展开 {{域名-c}} 等变量引用
            searchUrl = XBPQParse.interpolate(this, searchUrl);
            if (searchUrl.isEmpty()) {
                if (url.isEmpty()) url = baseUrl;
                String homeHtml = XBPQHttp.fetchHtml(this, url);
                if (homeHtml != null) {
                    Matcher formMatcher = FORM_ACTION_PATTERN.matcher(homeHtml);
                    if (formMatcher.find()) {
                        String action = formMatcher.group(1);
                        if (!action.startsWith("http")) action = baseUrl + (action.startsWith("/") ? "" : "/") + action;
                        searchUrl = action + "?{wd}";
                    }
                }
            }
            if (searchUrl.isEmpty()) {
                result.put("list", new JSONArray());
                return result;
            }

            String fullUrl = searchUrl.replace("{wd}", key).replace("{pg}", "1");
            // 搜索后缀：URL 末尾追加后缀（智能处理已有 query 的情况）
            if (!searchSuf.isEmpty()) {
                if (fullUrl.contains("?") && searchSuf.startsWith("?")) {
                    // 已有 query 且后缀也以 ? 开头，去掉重复 ?
                    fullUrl = fullUrl + "&" + searchSuf.substring(1);
                } else if (fullUrl.contains("?") && !searchSuf.startsWith("&") && !searchSuf.startsWith("#")) {
                    // 已有 query 但后缀不以 & 开头，补 &
                    fullUrl = fullUrl + "&" + searchSuf;
                } else {
                    fullUrl = fullUrl + searchSuf;
                }
            }
            if (!fullUrl.startsWith("http")) {
                fullUrl = baseUrl + (fullUrl.startsWith("/") ? "" : "/") + fullUrl;
            }

            String html;
            Map<String, String> extraSearchHeaders = XBPQHttp.parseExtraHeaders(this, searchReqHeader);
            if (fullUrl.contains(";post")) {
                html = XBPQHttp.fetchPost(this, fullUrl.replace(";post", ""));
            } else if (!extraSearchHeaders.isEmpty()) {
                html = XBPQHttp.fetchHtmlWithExtra(this, fullUrl, extraSearchHeaders);
            } else {
                html = XBPQHttp.fetchHtml(this, fullUrl);
            }
            if (html == null || html.isEmpty()) {
                result.put("list", new JSONArray());
                return result;
            }

            // 搜索模式：强制指定解析模式
            // "1"/"" → HTML 截取（默认），"json" → JSON 解析，"xml" → XML/RSS 解析
            String searchMode = config.get("", "搜索模式", "ssmoshi");

            // JSON 模式：仅明确指定 "json" 时进入（避免 HTML 以 { 开头被误判）
            if ("json".equals(searchMode)) {
                return parseJsonSearch(html);
            }

            // XML 模式（RSS）
            if ("xml".equals(searchMode) || fullUrl.contains("rss.xml")
                    || html.trim().startsWith("<?xml") || html.trim().startsWith("<rss")) {
                return parseXmlSearch(html);
            }

            // 二次截取：支持组合格式 "pre&&suf"（单键）和分离格式
            String twiceRaw = config.get("", "搜索二次截取", "ssjiequqian", "sea_twice_pre");
            String twicePre, twiceSuf;
            if (twiceRaw.contains("&&")) {
                String[] twiceParts = twiceRaw.split("&&");
                twicePre = twiceParts[0];
                twiceSuf = twiceParts.length > 1 ? twiceParts[1] : "";
            } else {
                twicePre = twiceRaw;
                twiceSuf = config.get("", "搜索二次截取后", "ssjiequhou", "sea_twice_suf");
            }
            if (!twicePre.isEmpty() && !twiceSuf.isEmpty()) {
                html = XBPQParse.extractBetween(html, twicePre, twiceSuf);
            }

            // 数组截取：支持组合格式 "pre&&suf"（单键）和分离格式
            // 搜索数组为空时回退到常规数组选择器（很多配置不单独定义搜索数组）
            String arrRaw = config.get("", "搜索数组", "ssjiequshuzuqian", "sea_arr_pre");
            if (arrRaw.isEmpty()) {
                arrRaw = config.get("", "数组", "jiequshuzuqian", "cateVodNode", "cat_arr_pre");
            }
            String arrPre, arrSuf;
            if (arrRaw.contains("&&")) {
                String[] arrParts = arrRaw.split("&&");
                arrPre = arrParts[0];
                arrSuf = arrParts.length > 1 ? arrParts[1] : "";
            } else {
                arrPre = arrRaw;
                arrSuf = config.get("", "搜索截取数组", "ssjiequshuzuhou", "sea_arr_suf");
                if (arrSuf.isEmpty()) arrSuf = config.get("", "数组后", "jiequshuzuhou", "cat_arr_suf");
            }
            if (arrPre.isEmpty()) arrPre = "<a";
            if (arrSuf.isEmpty()) arrSuf = "</a>";

            // 字段选择器：搜索专用为空时回退到常规选择器（默认值用选择器模式，不会与键名冲突）
            String titleSel = config.get("", "搜索标题", "ssbiaotiqian", "sea_title");
            if (titleSel.isEmpty()) titleSel = config.get("title=\"&&\"", "标题", "biaotiqian", "catjsonname", "cat_title");
            String picSel = config.get("", "搜索图片", "sstupianqian", "sea_pic");
            if (picSel.isEmpty()) picSel = config.get("data-original=\"&&\"", "图片", "tupianqian", "catjsonpic", "cat_pic");
            String linkSel = config.get("", "搜索链接", "搜索前", "sea_url");
            if (linkSel.isEmpty()) linkSel = config.get("href=\"&&\"", "链接", "lianjieqian", "catjsonid", "cat_url");
            String subSel = config.get("", "搜索副标题", "ssfubiaotiqian", "sea_subtitle");
            if (subSel.isEmpty()) subSel = config.get("", "副标题", "fubiaotiqian", "catjsonstitle", "cat_subtitle");

            String linkPre = config.get("", "搜索链接前缀", "ssljqianzhui");
            if (linkPre.isEmpty()) linkPre = config.get("", "链接前缀", "ljqianzhui", "cat_prefix");
            String linkSuf = config.get("", "搜索链接后缀", "ssljhouzhui");
            if (linkSuf.isEmpty()) linkSuf = config.get("", "链接后缀", "ljhouzhui", "cat_suffix");

            List<String> items = XBPQParse.extractAll(this, html, arrPre, arrSuf);
            if (items.isEmpty() && spiderApi != null) {
                spiderApi.log("搜索列表为空：数组前缀[" + arrPre + "] 数组后缀[" + arrSuf + "] 未匹配到内容");
            } else if (debug && spiderApi != null) {
                spiderApi.log("搜索列表命中：" + items.size() + " 项（数组[" + arrPre + "&&" + arrSuf + "]）");
            }
            JSONArray list = new JSONArray();
            for (String item : items) {
                try {
                    String title = XBPQParse.pick(this, item, titleSel);
                    String pic = XBPQParse.pick(this, item, picSel);
                    String link = XBPQParse.pick(this, item, linkSel);
                    String sub = XBPQParse.pick(this, item, subSel);
                    if (title.isEmpty()) continue;
                    if (!linkPre.isEmpty() && !link.startsWith("http")) link = linkPre + link;
                    if (!linkSuf.isEmpty()) link = link + linkSuf;
                    if (!link.startsWith("http") && !link.startsWith("//")) {
                        link = baseUrl + (link.startsWith("/") ? "" : "/") + link;
                    }
                    Vod vod = new Vod(link, title, pic, sub);
                    list.put(vodToJson(vod));
                } catch (Exception ignored) {
                }
            }
            result.put("list", list);
        } catch (Exception e) {
            SpiderDebug.log(e);
            try {
                result.put("list", new JSONArray());
            } catch (Exception ignored) {
            }
        }
        return result;
    }

    /** JSON 模式搜索结果解析。 */
    private JSONObject parseJsonSearch(String html) {
        JSONObject result = new JSONObject();
        try {
            JSONObject json = new JSONObject(html);
            JSONArray searchList = json.optJSONArray("list");
            if (searchList == null)
                searchList = json.optJSONObject("data") != null ? json.getJSONObject("data").optJSONArray("list") : null;
            if (searchList == null) {
                result.put("list", new JSONArray());
                return result;
            }
            String nameKey = config.get("name", "搜索标题", "jsname", "jsonname");
            String idKey = config.get("id", "搜索链接", "jsid", "jsonid");
            String picKey = config.get("pic", "搜索图片", "jspic", "jsonpic");
            JSONArray list = new JSONArray();
            for (int i = 0; i < searchList.length(); i++) {
                try {
                    JSONObject item = searchList.getJSONObject(i);
                    String name = item.optString(nameKey.isEmpty() ? "name" : nameKey);
                    String link = item.optString(idKey.isEmpty() ? "id" : idKey);
                    String pic = item.optString(picKey.isEmpty() ? "pic" : picKey);
                    if (name.isEmpty()) continue;
                    Vod vod = new Vod(link, name, pic, "");
                    list.put(vodToJson(vod));
                } catch (Exception ignored) {
                }
            }
            result.put("list", list);
        } catch (Exception e) {
            SpiderDebug.log(e);
            try {
                result.put("list", new JSONArray());
            } catch (Exception ignored) {
            }
        }
        return result;
    }

    /** XML 模式搜索结果解析（RSS 格式：item/title/link/pic/pubDate）。 */
    private JSONObject parseXmlSearch(String html) {
        JSONObject result = new JSONObject();
        try {
            String itemSel = config.get("<item>&&</item>", "搜索数组", "sea_arr_pre");
            String titleSel = config.get("<title>&&</title>", "搜索标题", "sea_title");
            String linkSel = config.get("<link>&&</link>", "搜索链接", "sea_url");
            String picSel = config.get("<pic>&&</pic>", "搜索图片", "sea_pic");
            String subSel = config.get("<pubDate>&&</pubDate>", "搜索副标题", "sea_subtitle");
            List<String> items = XBPQParse.extractAll(this, html, itemSel.split("&&")[0], itemSel.split("&&")[1]);
            JSONArray list = new JSONArray();
            for (String item : items) {
                try {
                    String title = XBPQParse.pick(this, item, titleSel);
                    String link = XBPQParse.pick(this, item, linkSel);
                    String pic = XBPQParse.pick(this, item, picSel);
                    String sub = XBPQParse.pick(this, item, subSel);
                    if (title.isEmpty()) continue;
                    if (!link.startsWith("http") && !link.startsWith("//")) {
                        link = baseUrl + (link.startsWith("/") ? "" : "/") + link;
                    }
                    Vod vod = new Vod(link, title, pic, sub);
                    list.put(vodToJson(vod));
                } catch (Exception ignored) {
                }
            }
            result.put("list", list);
        } catch (Exception e) {
            SpiderDebug.log(e);
            try {
                result.put("list", new JSONArray());
            } catch (Exception ignored) {
            }
        }
        return result;
    }

    // ==================== 嗅探 / isVideoFormat ====================

    @Override
    public boolean isVideoFormat(String url) throws Exception {
        if (url == null || url.isEmpty()) return false;
        if (config == null) return false;
        if (lowerCaseSniff) url = url.toLowerCase();
        if (!url.startsWith("http") && !url.startsWith("magnet")) return false;
        String sniff = config.get(DEFAULT_SNIFF, "嗅探词", "VideoFormat");
        String filter = config.get(DEFAULT_FILTER, "过滤词", "VideoFilter");
        String[] sniffWords = sniff.split("#");
        String[] filterWords = filter.split("#");
        for (String sniffWord : sniffWords) {
            if (sniffWord.isEmpty() || !url.contains(sniffWord)) continue;
            boolean filtered = false;
            for (String filterWord : filterWords) {
                if (!filterWord.isEmpty() && url.contains(filterWord)) {
                    filtered = true;
                    break;
                }
            }
            if (!filtered) return true;
        }
        return false;
    }

    @Override
    public boolean manualVideoCheck() throws Exception {
        String manualValue = config.get("", "嗅探词", "ManualSniffer");
        if (manualValue.isEmpty()) {
            manualValue = config.get("", "手动嗅探", "ManualSniffer");
            return "1".equals(manualValue) || sniffManual();
        }
        return true;
    }

    // ==================== 代理 / 图片 ====================

    /**
     * 代理接口（public，供框架反射调用，方法名与 smali 一致为 mProxy）。
     */
    public Object[] mProxy(Map<String, String> params) throws Exception {
        return proxy(params);
    }

    @Override
    public Object[] proxy(Map<String, String> params) throws Exception {
        String type = params.get("type");
        if (type == null) return null;
        if ("pic".equals(type)) {
            String pic = params.get("pic");
            if (pic != null && !imageProxyRegex.isEmpty() && !imageProxyReplace.isEmpty()) {
                try {
                    pic = pic.replaceAll(imageProxyRegex, imageProxyReplace);
                    params.put("pic", pic);
                } catch (Exception ignored) {
                }
            }
            return loadPic(params);
        }
        // 阿里云盘字幕 / 视频代理
        if ("sub".equals(type)) return AliYun.get().proxySub(params);
        if ("video".equals(type)) return AliYun.get().proxyVideo(params);
        // 阿里云盘 token 类型：返回授权令牌 JSON
        if ("token".equals(type)) {
            try {
                String auth = AliYun.get().getHeader().get("authorization");
                if (auth == null || auth.isEmpty()) auth = "";
                String json = new JSONObject().put("token", auth).toString();
                return new Object[]{200, "application/json", new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8))};
            } catch (Exception e) {
                SpiderDebug.log(e);
                return null;
            }
        }
        return null;
    }

    /** 图片代理单图最大字节数（5MB，防止恶意超大图撑爆内存）。 */
    private static final int PIC_MAX_BYTES = 5 * 1024 * 1024;

    /** 图片 Content-Type 白名单（按 URL 扩展名匹配）。 */
    private static final List<String> PIC_EXTENSIONS = Arrays.asList(
            ".jpg", ".jpeg", ".png", ".gif", ".webp", ".bmp", ".svg", ".ico");

    /**
     * 图片代理：从 params 获取 site/pic → 构建请求头 → OkHttp 请求 → 返回 byte[]。
     * synchronized 保证静态缓存线程安全。
     * 安全限制：URL 扩展名白名单（非图片扩展名直接拒绝）、单图 ≤ 5MB、下载后魔数校验。
     */
    public static Object[] loadPic(Map<String, String> params) {
        if (params == null) return null;
        String site = params.get("site");
        String pic = params.get("pic");
        if (pic == null || pic.isEmpty()) return null;
        // URL 扩展名白名单校验：有扩展名但非图片类型 → 直接拒绝（省带宽）；无扩展名 → 放行由魔数兜底
        String lowerPic = pic.toLowerCase().split("[?#]")[0];
        if (lowerPic.matches(".*\\.[a-z0-9]{2,5}$")) {
            boolean validExt = false;
            for (String ext : PIC_EXTENSIONS) {
                if (lowerPic.endsWith(ext)) {
                    validExt = true;
                    break;
                }
            }
            if (!validExt) return new Object[]{500, "text/plain", null};
        }
        String url = pic.startsWith("http") ? pic : (site != null ? site + pic : pic);
        String cacheKey = site + "|" + pic;
        synchronized (picCache) {
            byte[] cached = picCache.get(cacheKey);
            if (cached != null) {
                return new Object[]{200, "image/jpeg", new ByteArrayInputStream(cached)};
            }
        }
        try {
            Map<String, String> headers = new HashMap<>();
            headers.put("User-Agent", DEFAULT_UA);
            if (site != null && !site.isEmpty()) headers.put("Referer", site);
            byte[] data = OkHttp.bytes(url, headers);
            // 安全校验：空数据 / 超大图 / 非图片魔数 → 拒绝
            if (data == null || data.length == 0 || data.length > PIC_MAX_BYTES) {
                return new Object[]{500, "text/plain", null};
            }
            // 魔数校验：检测前几个字节判断是否为图片（JPEG/PNG/GIF/WebP/BMP）
            if (!isImageMagicBytes(data)) {
                return new Object[]{500, "text/plain", null};
            }
            synchronized (picCache) {
                picCache.put(cacheKey, data);
            }
            return new Object[]{200, "image/jpeg", new ByteArrayInputStream(data)};
        } catch (Exception e) {
            SpiderDebug.log(e);
            return new Object[]{500, "text/plain", null};
        }
    }

    /**
     * 图片魔数校验：通过前几个字节判断是否为常见图片格式。
     * JPEG: FF D8 FF | PNG: 89 50 4E 47 | GIF: 47 49 46 38
     * WebP: 52 49 46 46 ... 57 45 42 50 | BMP: 42 4D
     */
    private static boolean isImageMagicBytes(byte[] data) {
        if (data == null || data.length < 4) return false;
        // JPEG: FF D8 FF
        if ((data[0] & 0xFF) == 0xFF && (data[1] & 0xFF) == 0xD8 && (data[2] & 0xFF) == 0xFF) return true;
        // PNG: 89 50 4E 47
        if ((data[0] & 0xFF) == 0x89 && data[1] == 0x50 && data[2] == 0x4E && data[3] == 0x47) return true;
        // GIF: 47 49 46 38 (GIF8)
        if (data[0] == 0x47 && data[1] == 0x49 && data[2] == 0x46 && data[3] == 0x38) return true;
        // BMP: 42 4D (BM)
        if (data[0] == 0x42 && data[1] == 0x4D) return true;
        // WebP: RIFF....WEBP (12 bytes)
        if (data.length >= 12 && data[0] == 0x52 && data[1] == 0x49 && data[2] == 0x46 && data[3] == 0x46
                && data[8] == 0x57 && data[9] == 0x45 && data[10] == 0x42 && data[11] == 0x50) return true;
        return false;
    }

    /**
     * AES-CBC 解密（public，供框架反射调用）。
     *
     * <p>解密算法：hex字符串 → byte[] → 与 KEY="wxEesU" 循环 XOR → AES-CBC 解密 → UTF-8 字符串。</p>
     *
     * @param encrypted  Base64 编码的密文
     * @param charset    字符集（默认 "UTF-8"）
     * @param key        密钥字符串
     * @param iv         初始化向量
     * @return 解密后的明文，失败返回 null
     */
    public String decrypt(String encrypted, String charset, String key, String iv) {
        try {
            // 密钥处理：hex → byte[] → XOR with "wxEesU"
            byte[] keyBytes = XBPQCrypto.decryptHex(key).getBytes(charset);
            javax.crypto.spec.SecretKeySpec secretKey =
                    new javax.crypto.spec.SecretKeySpec(keyBytes, "AES");

            // 解密算法：AES/CBC/PKCS5Padding
            javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("AES/CBC/PKCS5Padding");
            javax.crypto.spec.IvParameterSpec ivSpec =
                    new javax.crypto.spec.IvParameterSpec(iv.getBytes(charset));
            cipher.init(javax.crypto.Cipher.DECRYPT_MODE, secretKey, ivSpec);

            // Base64 解码 + 解密
            byte[] decoded = android.util.Base64.decode(encrypted, android.util.Base64.DEFAULT);
            byte[] decrypted = cipher.doFinal(decoded);
            return new String(decrypted, charset);
        } catch (Exception e) {
            if (spiderApi != null) {
                android.util.Log.e("XBPQ", "decrypt error: " + e.getMessage());
            }
            return null;
        }
    }

    /**
     * AES-CBC 加密（public，供框架反射调用）。
     *
     * <p>加密算法：明文 → UTF-8 byte[] → AES-CBC 加密 → Base64 编码。</p>
     *
     * @param content 明文内容
     * @param charset 字符集（默认 "UTF-8"）
     * @param key     密钥字符串
     * @param iv      初始化向量
     * @return Base64 编码的密文，失败返回 null
     */
    public String encrypt(String content, String charset, String key, String iv) {
        try {
            javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("AES/CBC/PKCS5Padding");
            javax.crypto.spec.SecretKeySpec secretKey =
                    new javax.crypto.spec.SecretKeySpec(key.getBytes(charset), "AES");
            javax.crypto.spec.IvParameterSpec ivSpec =
                    new javax.crypto.spec.IvParameterSpec(iv.getBytes(charset));
            cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, secretKey, ivSpec);
            byte[] encrypted = cipher.doFinal(content.getBytes(charset));
            return android.util.Base64.encodeToString(encrypted, android.util.Base64.DEFAULT);
        } catch (Exception e) {
            if (spiderApi != null) {
                android.util.Log.e("XBPQ", "encrypt error: " + e.getMessage());
            }
            return null;
        }
    }

    /**
     * 获取 Token（public，供框架反射调用）。
     * 实际实现与 encrypt 相同。
     */
    public String getToken(String content, String charset, String key, String iv) {
        return encrypt(content, charset, key, iv);
    }

    // ==================== XPath 详情解析 ====================

    /**
     * XPath 模式详情解析（public，供框架反射调用）。
     */
    public String xpDetailContent(List<String> ids) throws Exception {
        if (ids == null || ids.isEmpty()) return "";
        String id = ids.get(0);
        String[] parts = id.split("\\$\\$\\$");
        String detailUrl = parts.length > 1 ? parts[1] : "";
        if (detailUrl.isEmpty()) {
            if (!parts[0].startsWith("http")) {
                detailUrl = baseUrl + "/" + parts[0];
            } else {
                detailUrl = parts[0];
            }
        }
        return xpDetailContent(ids, detailUrl);
    }

    /**
     * XPath 模式详情解析（内部实现）。
     *
     * <p>当详情页 URL 以 {@code \\} 开头时启用，使用 XPath 配置键
     * （dtNode/dtCate/dtArea/dtYear/dtMark/dtDirector/dtActor/dtDesc 等）
     * 从 HTML 中提取详情字段和播放列表。</p>
     *
     * @param ids  详情 ID 列表
     * @param url  详情页 URL（已清洗）
     * @return 详情 JSON 字符串
     */
    private String xpDetailContent(List<String> ids, String url) throws Exception {
        try {
            String html = XBPQHttp.fetchHtml(this, url);
            if (html == null || html.isEmpty()) return "";

            // 详情节点：先定位详情容器再提取字段
            String detailNode = config.get("", "详情节点", "dtNode");
            if (!detailNode.isEmpty() && detailNode.contains("&&")) {
                String[] nodeParts = detailNode.split("&&");
                html = XBPQParse.extractBetween(html, nodeParts[0],
                        nodeParts.length >= 2 ? nodeParts[1] : "</");
            }

            Vod vod = new Vod();
            vod.setVodId(ids.get(0).split("\\$\\$\\$")[0]);
            vod.setVodName(XBPQParse.extractField(this, html, "标题", "name"));
            vod.setVodPic(XBPQParse.extractField(this, html, "图片", "pic"));
            vod.setTypeName(XBPQParse.extractField(this, html, "类型", "dtCate"));
            vod.setVodYear(XBPQParse.extractField(this, html, "年份", "dtYear"));
            vod.setVodArea(XBPQParse.extractField(this, html, "地区", "dtArea"));
            vod.setVodRemarks(XBPQParse.extractField(this, html, "状态", "dtMark"));
            vod.setVodDirector(XBPQParse.extractField(this, html, "导演", "dtDirector"));
            vod.setVodActor(XBPQParse.extractField(this, html, "主演", "dtActor"));
            vod.setVodContent(XBPQParse.extractField(this, html, "简介", "dtDesc"));

            String fromNode = config.get("", "线路节点", "dtFromNode");
            String fromName = config.get("", "线路名", "dtFromName");
            String urlNode = config.get("", "播放节点", "dtUrlNode");
            String urlSubNode = config.get("//a", "播放子节点", "dtUrlSubNode");
            String urlName = config.get("/text()", "播放标题", "dtUrlName");
            String urlId = config.get("/@href", "播放链接", "dtUrlId");

            String playFrom;
            String playUrl;
            if (!fromNode.isEmpty()) {
                List<String> tabs = XBPQParse.extractAll(this, html, fromNode, "</");
                List<String> names = new ArrayList<>();
                List<String> epGroups = new ArrayList<>();
                for (String tab : tabs) {
                    String name = XBPQParse.pick(this, tab, fromName.isEmpty() ? ">&&<" : fromName);
                    if (name.isEmpty()) name = "线路" + (names.size() + 1);
                    names.add(name);
                    List<String> nodes = XBPQParse.extractAll(this, tab, urlSubNode, "</");
                    List<String> eps = new ArrayList<>();
                    for (String node : nodes) {
                        String title = XBPQParse.pick(this, node, urlName);
                        String link = XBPQParse.pick(this, node, urlId);
                        if (title.isEmpty() && link.isEmpty()) continue;
                        eps.add(title + "$" + link);
                    }
                    epGroups.add(TextUtils.join("#", eps));
                }
                playFrom = TextUtils.join("$$$", names);
                playUrl = TextUtils.join("$$$", epGroups);
            } else {
                playFrom = "默认线路";
                List<String> nodes = XBPQParse.extractAll(this, html, urlSubNode, "</");
                List<String> eps = new ArrayList<>();
                for (String node : nodes) {
                    String title = XBPQParse.pick(this, node, urlName);
                    String link = XBPQParse.pick(this, node, urlId);
                    if (title.isEmpty() && link.isEmpty()) continue;
                    eps.add(title + "$" + link);
                }
                playUrl = TextUtils.join("#", eps);
            }
            if (reverseEpisodes && !playUrl.isEmpty()) {
                playUrl = XBPQParse.reverseEpisodesInUrl(playUrl);
            }
            vod.setVodPlayFrom(playFrom);
            vod.setVodPlayUrl(playUrl);

            List<Vod> list = new ArrayList<>();
            list.add(vod);
            return Result.string(list);
        } catch (Exception e) {
            SpiderDebug.log(e);
            return "";
        }
    }
}
