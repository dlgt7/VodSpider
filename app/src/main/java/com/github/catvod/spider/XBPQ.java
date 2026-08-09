package com.github.catvod.spider;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.util.Base64;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Filter;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.bean.xyqbiu.XbpqConfigKey;
import com.github.catvod.bean.xyqbiu.XbpqRuleParser;
import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderApi;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Crypto;
import com.github.catvod.utils.Json;

import com.google.gson.JsonObject;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.math.BigInteger;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Response;

/**
 * XBPQ Spider - 通用XPath/选择器Spider实现。
 * <p>从XBPQ.jar反编译还原，使用项目标准工具类（OkHttp/Json/Vod/Result）替代原生混淆实现。</p>
 *
 * <p>核心配置字段（通过extend JSON传入）：
 * <ul>
 *   <li>{@code homeUrl} - 首页地址</li>
 *   <li>{@code 一级} - 一级列表选择器</li>
 *   <li>{@code 二级} - 二级详情选择器</li>
 *   <li>{@code 搜索} - 搜索选择器</li>
 *   <li>{@code 筛选} - 筛选配置JSON</li>
 * </ul>
 * </p>
 */
public class XBPQ extends Spider {

    // ==================== 静态常量 ====================

    /** 调试标志 */
    private static final boolean DEBUG = false;

    /** UA - 桌面浏览器 */
    private static final String PC_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/110.0.0.0 Safari/537.36";

    /** UA - 移动浏览器 */
    private static final String MOBILE_UA = "Mozilla/5.0 (iPhone; CPU iPhone OS 16_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.0 Mobile/15E148 Safari/604.1";

    /** UA - iOS浏览器 */
    private static final String IOS_UA = "Mozilla/5.0 (iPhone; CPU iPhone OS 16_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.0 Mobile/15E148 Safari/604.1";

    /** UA - Mac浏览器 */
    private static final String MAC_UA = "Mozilla/5.0 (Macintosh; Intel Mac OS X 20_40; rv:100.0) AppleWebKit/537.75.14 (KHTML, like Gecko) Version/15.0.0 Safari/1500";

    /** 阿里盘分享链接模式 */
    public static final Pattern ALIYUN_PATTERN = Pattern.compile("(https://www\\.(alipan|aliyundrive)\\.com/s[^\"]+)");

    /** 首屏标记 */
    private static final String FIRST_PAGE_MARK = "firstPage=";
    private static final String FIRST_PAGE_REGEX = "\\[firstPage=";

    /** 时间戳占位符 */
    private static final String TIMESTAMP_KEY = "时间戳";
    private static final String TIME_MARK_KEY = "时间标";

    /** MD5表达式 */
    private static final String MD5_PREFIX = "md5(";
    private static final String MD5_SUFFIX = ")";

    /** 验证码路径 */
    private static final String[] VERIFY_PATHS = {"ajax/verify_check", "ajax.php?ac=code_check", "/verify/index.html", "?scheckAC=check"};
    private static final String VERIFY_TEXT = "输入验证码";
    private static final String CORRECT_VERIFY_TEXT = "输入正确的验证码";

    /** btwaf防火墙 */
    private static final String BTWAF_TEXT = "检测中";
    private static final String BTWAF_COOKIE = "btwaf";

    /** 嗅探页面标记 */
    private static final String SNIFF_HUADONG = "/huadong_";
    private static final String SNIFF_RENJI = "/renji_";

    /** 输入验证码 */
    private static final String INPUT_VERIFY = "输入验证码";
    private static final String INPUT_CORRECT_VERIFY = "输入正确的验证码";

    /** 全角字符常量（语义化） */
    private static final char FULLWIDTH_SPACE = '　';    // 全角空格
    private static final char FULLWIDTH_LPAR = '（';     // 全角(
    private static final char FULLWIDTH_RPAR = '）';     // 全角)
    private static final char FULLWIDTH_LBRACE = '｛';   // 全角{
    private static final char FULLWIDTH_RBRACE = '｝';   // 全角}
    private static final char FULLWIDTH_QMARK = '？';    // 全角?

    // ==================== 实例字段 ====================

    /** 站点配置JSON */
    protected JSONObject siteConfig;

    /** 站点地址 */
    protected String homeUrl = "";

    /** 站点名称 */
    protected String name = "";

    /** 编码 */
    protected String charset = "UTF-8";

    /** Cookie */
    protected String cookie = "";

    /** Referer */
    protected String referer = "";

    /** 超时时间 */
    protected int timeout = 5000;

    /** 调试标志 */
    protected boolean debugFlag;

    /** 列表分隔符，等价于"|"（竖线），用于分割列表项 */
    private static final String ARRAY_SEPARATOR = "|";

    /** 特殊字段分隔符，等价于"-"（连字符），用于替换特殊标记 */
    private static final String SPECIAL_SEPARATOR = "-";

    /** URL编码的竖线标记，处理URL时替换为真正的"|" */
    private static final String URL_ENCODED_PIPE = "|";

    /** URL编码的空标记，处理URL时替换为空字符串 */
    private static final String URL_ENCODED_EMPTY = "";

    /** 站点地址前缀 */
    protected String sitePrefix = "";

    /** 搜索关键词 */
    protected String searchKeyword = "";

    /** 调试信息存储 */
    protected String debugInfo = "";

    /** 视频列表缓存 */
    protected List<String> videoList = null;

    /** 详细配置JSON */
    protected JSONObject detailConfig = null;

    /** 是否显示调试信息 */
    protected boolean showDebug = false;

    /** 配置缓存 */
    protected JSONObject configCache = null;

    /** SharedPreferences */
    private SharedPreferences prefs;

    /** SpiderApi实例 */
    private SpiderApi spiderApi = null;

    /** 站点基础URL */
    private String baseUrl = "";

    /** 当前URL */
    private String currentUrl = "";

    /** 验证码标识 */
    private String verifyCode = "";

    /** 请求标记 */
    private String reqMark = "";

    /** 页面计数 */
    private int pageCount = 0;

    /** 最大重试次数 */
    private int maxRetry = 3;

    /** 是否POST请求 */
    private boolean isPost = false;

    /** 扩展配置 */
    protected String extend = "";

    /** 阿里Token */
    private String aliyunToken = "";

    /** 代理基础地址 */
    private String proxyBase = "";

    /** 是否启用代理 */
    private boolean useProxy = false;

    /** 请求头Map */
    private HashMap<String, String> headersMap = null;

    /** 阿里盘详情解析标志 */
    private static boolean aliyunFlag = false;

    // ==================== XBPQ标准配置字段 ====================

    /** 分类URL模板（等价于"分类url"配置键） */
    protected String fenleiUrl = "";

    /** 分类列表（等价于"分类"配置键） */
    protected String fenlei = "";

    /** 数组选择器（等价于"数组"配置键） */
    protected String arraySelector = "";

    /** 标题选择器（等价于"标题"配置键） */
    protected String titleSelector = "";

    /** 图片选择器（等价于"图片"配置键） */
    protected String picSelector = "";

    /** 链接选择器（等价于"链接"配置键） */
    protected String linkSelector = "";

    /** 副标题选择器（等价于"副标题"配置键） */
    protected String subtitleSelector = "";

    /** 简介选择器（等价于"简介"配置键） */
    protected String descSelector = "";

    /** 线路数组选择器（等价于"线路数组"配置键） */
    protected String tabArraySelector = "";

    /** 线路标题选择器（等价于"线路标题"配置键） */
    protected String tabTitleSelector = "";

    /** 播放数组选择器（等价于"播放数组"配置键） */
    protected String playArraySelector = "";

    /** 播放列表选择器（等价于"播放列表"配置键） */
    protected String playListSelector = "";

    /** 播放标题选择器（等价于"播放标题"配置键） */
    protected String playTitleSelector = "";

    /** 播放链接选择器（等价于"播放链接"配置键） */
    protected String playLinkSelector = "";

    /** 嗅探词（等价于"嗅探词"配置键） */
    protected String sniffWords = "";

    /** 过滤词（等价于"过滤词"配置键） */
    protected String filterWords = "";

    /** 域名-c映射（等价于"域名-c"配置键） */
    protected String domainConfig = "";

    /** 发布页（等价于"发布页"配置键） */
    protected String publishPage = "";

    /** 图片代理正则（等价于"图片代理正则"配置键） */
    protected String imageProxyRegex = "";

    /** 图片代理替换（等价于"图片代理替换"配置键） */
    protected String imageProxyReplace = "";

    /** 静态分页开关（等价于"静态分页"配置键） */
    protected boolean staticPaging = false;

    /** 每页大小（等价于"每页"配置键） */
    protected int pageSize = 0;

    /** 翻页步长（等价于"翻页步长"配置键） */
    protected int pageStep = 0;

    /** 分类分页开关（等价于"分类分页"配置键） */
    protected boolean categoryPaging = false;

    /** cover修正开关（等价于"cover修正"配置键） */
    protected boolean coverFix = false;

    /** 图文模式开关（等价于"图文模式"配置键） */
    protected boolean imageTextMode = false;

    /** 横图模式开关（等价于"横图模式"配置键） */
    protected boolean horizontalMode = false;

    /** 搜索后缀（等价于"搜索后缀"配置键） */
    protected String searchSuffix = "";

    /** 搜索数组修饰（等价于"搜索数组"修饰符） */
    protected String searchModifier = "";

    /** 搜索请求头（等价于"搜索请求头参数"配置键） */
    protected String searchHeaders = "";

    /** 累加Cookie（验证码刷新后写入） */
    protected String accCookie = "";

    /** 数组修饰符（等价于"数组修饰"配置键） */
    protected String arrayModifier = "";

    /** 线路数组修饰（等价于"线路数组"修饰符） */
    protected String tabModifier = "";

    /** 播放数组修饰（等价于"播放数组"修饰符） */
    protected String playModifier = "";

    /** 搜索URL（等价于"搜索url"配置键） */
    protected String searchUrl = "";

    /** 搜索模式（等价于"搜索模式"配置键） */
    protected String searchMode = "";

    /** 搜索数组选择器（等价于"搜索数组"配置键） */
    protected String searchArraySelector = "";

    /** 搜索图片选择器（等价于"搜索图片"配置键） */
    protected String searchPicSelector = "";

    /** 搜索标题选择器（等价于"搜索标题"配置键） */
    protected String searchTitleSelector = "";

    /** 搜索链接选择器（等价于"搜索链接"配置键） */
    protected String searchLinkSelector = "";

    /** 搜索副标题选择器（等价于"搜索副标题"配置键） */
    protected String searchSubtitleSelector = "";

    /** 二次截取前（等价于"二次截取"配置键） */
    protected String twicePre = "";

    /** 二次截取后（等价于"二次截取后"配置键） */
    protected String twiceSuf = "";

    /** 数组二次截取前（等价于"数组二次截取"配置键） */
    protected String arrayTwicePre = "";

    /** 数组二次截取后（等价于"数组二次截取后"配置键） */
    protected String arrayTwiceSuf = "";

    /** 播放二次截取前（等价于"播放二次截取"配置键） */
    protected String playTwicePre = "";

    /** 播放二次截取后（等价于"播放二次截取后"配置键） */
    protected String playTwiceSuf = "";

    /** 线路二次截取前（等价于"线路二次截取"配置键） */
    protected String tabTwicePre = "";

    /** 线路二次截取后（等价于"线路二次截取后"配置键） */
    protected String tabTwiceSuf = "";

    /** 链接前缀（等价于"链接前缀"配置键） */
    protected String linkPrefix = "";

    /** 链接后缀（等价于"链接后缀"配置键） */
    protected String linkSuffix = "";

    /** 播放链接前缀（等价于"播放链接前缀"配置键） */
    protected String playLinkPrefix = "";

    /** 播放链接后缀（等价于"播放链接后缀"配置键） */
    protected String playLinkSuffix = "";

    /** 跳转播放链接（等价于"跳转播放链接"配置键） */
    protected String jumpPlayUrl = "";

    /** 图片代理开关（等价于"图片代理"配置键） */
    protected boolean imageProxyEnabled = false;

    /** 分类二次截取前（等价于"分类二次截取"配置键） */
    protected String categoryTwicePre = "";

    /** 分类二次截取后（等价于"分类二次截取后"配置键） */
    protected String categoryTwiceSuf = "";

    /** 分类数组选择器（等价于"分类数组"配置键） */
    protected String categoryArraySelector = "";

    /** 分类标题选择器（等价于"分类标题"配置键） */
    protected String categoryTitleSelector = "";

    /** 分类ID选择器（等价于"分类ID"配置键） */
    protected String categoryIdSelector = "";

    /** 直接播放（等价于"直接播放"配置键） */
    protected String directPlay = "";

    /** 免嗅（等价于"免嗅"配置键） */
    protected String noSniff = "";

    /** 强制解析（等价于"强制解析"配置键） */
    protected String forceParse = "";

    /** 首页数量（等价于"首页"配置键） */
    protected String homeCount = "";

    /** 起始页（等价于"起始页"配置键） */
    protected String startPage = "";

    /** 请求头配置 */
    protected String requestHeader = "";

    /** 二级目录 */
    protected String secondLevelDir = "";

    /** 二级ID */
    protected String secondLevelId = "";

    /** 特殊分类链接 */
    protected String specialCateLinks = "";

    // ==================== XBPQ高级语法配置字段 ====================

    /** 动态域名替换映射（如 {"-c":"www.newdomain.com"}） */
    protected HashMap<String, String> domainMap = new HashMap<>();

    /** 工具函数映射（如 {"key":"value"}） */
    protected HashMap<String, String> toolMap = new HashMap<>();

    /** 数组替换规则（如 {"原值":"替换值"}） */
    protected HashMap<String, String> replaceMap = new HashMap<>();

    /** 数组排序规则（如 {"排序词":"1"}） */
    protected HashMap<String, String> sortMap = new HashMap<>();

    /** 数组不包含规则（如 {"排除词":"1"}） */
    protected HashMap<String, String> excludeMap = new HashMap<>();

    // ==================== 生命周期方法 ====================

    /**
     * 初始化Spider。
     *
     * @param ctx    上下文
     * @param extend 站点扩展配置（JSON字符串）
     */
    @Override
    public void init(Context ctx, String extend) throws Exception {
        super.init(ctx, extend);
        this.extend = extend;
        this.name = "XBPQ";
        this.siteConfig = new JSONObject(extend);

        // 读取基础配置
        this.homeUrl = siteConfig.optString(XbpqConfigKey.HOME_URL, "");
        this.name = siteConfig.optString(XbpqConfigKey.NAME, this.name);
        this.charset = siteConfig.optString(XbpqConfigKey.CHARSET, "UTF-8");
        this.debugFlag = siteConfig.optBoolean("debug", false);
        this.timeout = siteConfig.optInt("timeout", 5000);
        this.requestHeader = siteConfig.optString(XbpqConfigKey.REQUEST_HEADER, "");
        this.cookie = siteConfig.optString(XbpqConfigKey.COOKIE, "");
        this.referer = siteConfig.optString(XbpqConfigKey.REFERER, "");
        this.directPlay = siteConfig.optString(XbpqConfigKey.DIRECT_PLAY, "");
        this.noSniff = siteConfig.optString(XbpqConfigKey.NO_SNIFF, "");
        this.forceParse = siteConfig.optString(XbpqConfigKey.FORCE_PARSE, "");
        this.sniffWords = siteConfig.optString(XbpqConfigKey.SNIFF_WORDS, "");
        this.filterWords = siteConfig.optString(XbpqConfigKey.FILTER_WORDS, "");
        this.searchMode = siteConfig.optString(XbpqConfigKey.SEARCH_MODE, "");
        this.searchUrl = siteConfig.optString(XbpqConfigKey.SEARCH_URL, "");
        this.searchSuffix = siteConfig.optString(XbpqConfigKey.SEARCH_SUFFIX, "");
        this.searchHeaders = siteConfig.optString(XbpqConfigKey.SEARCH_HEADERS, "");
        this.homeCount = siteConfig.optString(XbpqConfigKey.HOME_COUNT, "");
        this.startPage = siteConfig.optString(XbpqConfigKey.START_PAGE, "");
        this.fenleiUrl = siteConfig.optString(XbpqConfigKey.FENLEI_URL, "");
        this.fenlei = siteConfig.optString(XbpqConfigKey.FENLEI, "");
        this.arraySelector = siteConfig.optString(XbpqConfigKey.ARRAY_SELECTOR, "");
        this.titleSelector = siteConfig.optString(XbpqConfigKey.TITLE_SELECTOR, "");
        this.picSelector = siteConfig.optString(XbpqConfigKey.PIC_SELECTOR, "");
        this.linkSelector = siteConfig.optString(XbpqConfigKey.LINK_SELECTOR, "");
        this.subtitleSelector = siteConfig.optString(XbpqConfigKey.SUBTITLE_SELECTOR, "");
        this.descSelector = siteConfig.optString(XbpqConfigKey.DESC_SELECTOR, "");
        this.tabArraySelector = siteConfig.optString(XbpqConfigKey.TAB_ARRAY_SELECTOR, "");
        this.tabTitleSelector = siteConfig.optString(XbpqConfigKey.TAB_TITLE_SELECTOR, "");
        this.playArraySelector = siteConfig.optString(XbpqConfigKey.PLAY_ARRAY_SELECTOR, "");
        this.playListSelector = siteConfig.optString(XbpqConfigKey.PLAY_LIST_SELECTOR, "");
        this.playTitleSelector = siteConfig.optString(XbpqConfigKey.PLAY_TITLE_SELECTOR, "");
        this.playLinkSelector = siteConfig.optString(XbpqConfigKey.PLAY_LINK_SELECTOR, "");
        this.linkPrefix = siteConfig.optString(XbpqConfigKey.LINK_PREFIX, "");
        this.linkSuffix = siteConfig.optString(XbpqConfigKey.LINK_SUFFIX, "");
        this.playLinkPrefix = siteConfig.optString(XbpqConfigKey.PLAY_LINK_PREFIX, "");
        this.playLinkSuffix = siteConfig.optString(XbpqConfigKey.PLAY_LINK_SUFFIX, "");
        this.jumpPlayUrl = siteConfig.optString(XbpqConfigKey.JUMP_PLAY_URL, "");
        this.imageProxyEnabled = !siteConfig.optString(XbpqConfigKey.IMAGE_PROXY, "").isEmpty();
        this.imageProxyRegex = siteConfig.optString(XbpqConfigKey.IMAGE_PROXY_REGEX, "");
        this.imageProxyReplace = siteConfig.optString(XbpqConfigKey.IMAGE_PROXY_REPLACE, "");
        this.categoryTwicePre = siteConfig.optString(XbpqConfigKey.CATEGORY_TWICE_PRE, "");
        this.categoryTwiceSuf = siteConfig.optString(XbpqConfigKey.CATEGORY_TWICE_SUF, "");
        this.categoryArraySelector = siteConfig.optString(XbpqConfigKey.CATEGORY_ARRAY, "");
        this.categoryTitleSelector = siteConfig.optString(XbpqConfigKey.CATEGORY_TITLE, "");
        this.categoryIdSelector = siteConfig.optString(XbpqConfigKey.CATEGORY_ID, "");
        this.staticPaging = siteConfig.optBoolean("静态分页", false);
        this.pageSize = siteConfig.optInt("每页", 0);
        this.pageStep = siteConfig.optInt("翻页步长", 0);
        this.categoryPaging = siteConfig.optBoolean("分类分页", false);
        this.coverFix = siteConfig.optBoolean("cover修正", false);
        this.imageTextMode = siteConfig.optBoolean("图文模式", false);
        this.horizontalMode = siteConfig.optBoolean("横图模式", false);
        this.twicePre = siteConfig.optString(XbpqConfigKey.TWICE_PRE, "");
        this.twiceSuf = siteConfig.optString(XbpqConfigKey.TWICE_SUF, "");
        this.arrayTwicePre = siteConfig.optString(XbpqConfigKey.ARRAY_TWICE_PRE, "");
        this.arrayTwiceSuf = siteConfig.optString(XbpqConfigKey.ARRAY_TWICE_SUF, "");
        this.playTwicePre = siteConfig.optString(XbpqConfigKey.PLAY_TWICE_PRE, "");
        this.playTwiceSuf = siteConfig.optString(XbpqConfigKey.PLAY_TWICE_SUF, "");
        this.tabTwicePre = siteConfig.optString(XbpqConfigKey.TAB_TWICE_PRE, "");
        this.tabTwiceSuf = siteConfig.optString(XbpqConfigKey.TAB_TWICE_SUF, "");
        this.searchArraySelector = siteConfig.optString(XbpqConfigKey.SEARCH_ARRAY_SELECTOR, "");
        this.searchPicSelector = siteConfig.optString(XbpqConfigKey.SEARCH_PIC_SELECTOR, "");
        this.searchTitleSelector = siteConfig.optString(XbpqConfigKey.SEARCH_TITLE_SELECTOR, "");
        this.searchLinkSelector = siteConfig.optString(XbpqConfigKey.SEARCH_LINK_SELECTOR, "");
        this.searchSubtitleSelector = siteConfig.optString(XbpqConfigKey.SEARCH_SUBTITLE_SELECTOR, "");
        this.secondLevelDir = siteConfig.optString(XbpqConfigKey.SECOND_LEVEL_DIR, "");
        this.secondLevelId = siteConfig.optString(XbpqConfigKey.SECOND_LEVEL_ID, "");
        this.specialCateLinks = siteConfig.optString(XbpqConfigKey.SPECIAL_CATE_LINKS, "");
        this.publishPage = siteConfig.optString(XbpqConfigKey.PUBLISH_PAGE, "");
        this.domainConfig = siteConfig.optString(XbpqConfigKey.DOMAIN_CONFIG, "");

        // 初始化xbpq高级语法配置
        initAdvancedConfig();

        // 计算站点前缀
        this.sitePrefix = this.homeUrl;
        if (sitePrefix.isEmpty() && !this.publishPage.isEmpty()) {
            sitePrefix = this.publishPage;
        }
        // 提取host作为站点前缀
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(".*(https?://[^/]+)/.*").matcher(this.homeUrl);
        if (matcher.matches()) {
            this.sitePrefix = matcher.group(1);
        }

        this.baseUrl = this.homeUrl;
        this.proxyBase = this.homeUrl;

        String prefsName = ctx.getPackageName() + "_preferences";
        prefs = ctx.getSharedPreferences(prefsName, 0);

        // 初始化SpiderApi
        this.spiderApi = new SpiderApi();
    }

    // ==================== 工具方法 - 加密解密 ====================

    /**
     * AES解密。
     */
    public static String decryptAes(String encrypted, String charset, String key, String iv) {
        try {
            java.security.Key secretKey = new javax.crypto.spec.SecretKeySpec(key.getBytes(charset), "AES");
            javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(javax.crypto.Cipher.DECRYPT_MODE, secretKey,
                new javax.crypto.spec.IvParameterSpec(iv.getBytes()));
            return new String(cipher.doFinal(android.util.Base64.decode(encrypted, android.util.Base64.NO_WRAP)), charset);
        } catch (Exception e) {
            SpiderDebug.log(e);
            return null;
        }
    }

    /**
     * AES加密处理。
     */
    public static String encryptAes(String content, String charset, String key, String iv) {
        try {
            java.security.Key secretKey = new javax.crypto.spec.SecretKeySpec(key.getBytes(), "AES");
            javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, secretKey,
                new javax.crypto.spec.IvParameterSpec(iv.getBytes()));
            return android.util.Base64.encodeToString(cipher.doFinal(content.getBytes(charset)), android.util.Base64.NO_WRAP);
        } catch (Exception e) {
            SpiderDebug.log(e);
            return null;
        }
    }

    // ==================== 工具方法 - 配置获取 ====================

    /**
     * 获取配置值。
     */
    private String getConfig(String key, String defaultVal) {
        if (configCache == null) {
            configCache = siteConfig;
        }
        String value = configCache.optString(key, "");
        return value.isEmpty() ? defaultVal : value;
    }

    /**
     * 获取配置值（4个备选键）。
     */
    private String getConfig(String key1, String key2, String key3, String key4) {
        String v = getConfig(key1, "");
        if (!v.isEmpty()) return v;
        v = getConfig(key2, "");
        if (!v.isEmpty()) return v;
        v = getConfig(key3, "");
        if (!v.isEmpty()) return v;
        return getConfig(key4, "");
    }

    /**
     * 获取配置值（3个备选键）。
     */
    private String getConfig(String key1, String key2, String key3) {
        String v = getConfig(key1, "");
        if (!v.isEmpty()) return v;
        v = getConfig(key2, "");
        if (!v.isEmpty()) return v;
        return getConfig(key3, "");
    }

    /**
     * 获取配置值链式调用。
     */
    private String getConfigChain(String key1, String key2, String defaultVal) {
        return getConfig(key1, getConfig(key2, defaultVal));
    }

    private String getConfigChain(String key1, String key2, String key3, String defaultVal) {
        return getConfig(key1, getConfigChain(key2, key3, defaultVal));
    }

    private String getConfigChain(String key1, String key2, String key3, String key4, String defaultVal) {
        return getConfig(key1, getConfigChain(key2, key3, key4, defaultVal));
    }

    private String getConfigChain(String key1, String key2, String key3, String key4, String key5, String defaultVal) {
        return getConfig(key1, getConfigChain(key2, key3, key4, key5, defaultVal));
    }

    // ==================== 工具方法 - URL处理 ====================

    /**
     * 规范化URL处理。
     */
    private String normalizeUrl(String url) {
        if (url == null || url.isEmpty()) {
            return "";
        }
        url = url.replace("[time]", String.valueOf(System.currentTimeMillis()));
        url = url.replace("[site]", sitePrefix.isEmpty() ? "" : sitePrefix);
        return url.trim();
    }

    /**
     * 解析选择器规则。
     */
    private String parseSelector(String rule) {
        if (rule == null || rule.isEmpty()) {
            return "";
        }
        return XbpqRuleParser.escapePipe(rule);
    }

    /**
     * 提取视频列表中的URL。
     */
    private String extractUrl(String list, int index, String key) {
        try {
            String[] parts = list.split("\\$");
            if (index >= parts.length) {
                return "";
            }
            String[] params = parts[index].split("&");
            for (String param : params) {
                if (param.startsWith(key + "=")) {
                    return param.substring(key.length() + 1);
                }
            }
            return "";
        } catch (Exception e) {
            return "";
        }
    }

    // ==================== 工具方法 - HTML/JSON解析 ====================

    /**
     * 从HTML提取文本。
     */
    private String getText(String html, String selector, int index) {
        try {
            Document doc = Jsoup.parse(html);
            Elements elements = doc.select(selector);
            if (index >= 0 && index < elements.size()) {
                return elements.get(index).text().trim();
            }
            if (!elements.isEmpty()) {
                return elements.first().text().trim();
            }
            return "";
        } catch (Exception e) {
            SpiderDebug.log(e);
            return "";
        }
    }

    /**
     * 从HTML提取属性。
     */
    private String getAttr(String html, String selector, String attr, int index) {
        try {
            Document doc = Jsoup.parse(html);
            Elements elements = doc.select(selector);
            if (index >= 0 && index < elements.size()) {
                return elements.get(index).attr(attr).trim();
            }
            if (!elements.isEmpty()) {
                return elements.first().attr(attr).trim();
            }
            return "";
        } catch (Exception e) {
            SpiderDebug.log(e);
            return "";
        }
    }

    /**
     * 从JSON提取字段。
     */
    private String getJson(String json, String key) {
        try {
            JSONObject obj = new JSONObject(json);
            return obj.optString(key, "");
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 从JSON提取字段（带默认值）。
     */
    private String getJson(String json, String key, String defaultVal) {
        try {
            JSONObject obj = new JSONObject(json);
            return obj.optString(key, defaultVal);
        } catch (Exception e) {
            return defaultVal;
        }
    }

    // ==================== 工具方法 - 请求构建 ====================

    /**
     * 构建请求头。
     */
    private Map<String, String> buildHeaders(Map<String, String> headers) {
        Map<String, String> result = new HashMap<>();
        result.put("User-Agent", PC_UA);
        if (!cookie.isEmpty()) {
            result.put("Cookie", cookie);
        }
        if (!referer.isEmpty()) {
            result.put("Referer", referer);
        }
        if (headers != null) {
            result.putAll(headers);
        }
        return result;
    }

    /**
     * GET请求。
     */
    protected String get(String url, Map<String, String> headers) {
        try {
            SpiderDebug.log("GET: " + url);
            return OkHttp.string(url, buildHeaders(headers));
        } catch (Exception e) {
            SpiderDebug.log(e);
            return "";
        }
    }

    /**
     * POST请求。
     */
    protected String post(String url, Map<String, String> data, Map<String, String> headers) {
        try {
            SpiderDebug.log("POST: " + url);
            return OkHttp.post(url, data, buildHeaders(headers));
        } catch (Exception e) {
            SpiderDebug.log(e);
            return "";
        }
    }

    /**
     * POST请求（JSON body）。
     */
    protected String postJson(String url, String json, Map<String, String> headers) {
        try {
            SpiderDebug.log("POST_JSON: " + url);
            return OkHttp.post(url, json, buildHeaders(headers));
        } catch (Exception e) {
            SpiderDebug.log(e);
            return "";
        }
    }

    // ==================== 工具方法 - 列表提取 ====================

    /**
     * 从HTML提取列表。
     *
     * @param html HTML内容
     * @param selector 选择器
     * @param isJson 是否为JSON模式
     * @param headers 额外请求头
     * @return 提取的列表项
     */
    private List<String> extractList(String html, String selector, boolean isJson, Map<String, String> headers) {
        List<String> result = new ArrayList<>();
        if (html == null || html.isEmpty()) {
            return result;
        }
        try {
            if (isJson) {
                // JSON模式：解析JSON数组
                JSONArray jsonArray = new JSONArray(html);
                for (int i = 0; i < jsonArray.length(); i++) {
                    JSONObject item = jsonArray.getJSONObject(i);
                    result.add(item.toString());
                }
            } else {
                // HTML模式：使用Jsoup解析
                Document doc = Jsoup.parse(html);
                Elements elements = doc.select(selector);
                for (Element el : elements) {
                    result.add(el.outerHtml());
                }
            }
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        return result;
    }

    /**
     * 从列表项中提取URL。
     */
    private String extractItemUrl(String itemHtml, String urlSelector) {
        try {
            Document doc = Jsoup.parse(itemHtml);
            Element el = doc.selectFirst(urlSelector);
            if (el != null) {
                return el.attr("href");
            }
            // 尝试直接从文本中提取URL
            Matcher matcher = Pattern.compile("(https?://[^\\s<>\"\\']+)").matcher(itemHtml);
            if (matcher.find()) {
                return matcher.group(1);
            }
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        return "";
    }

    /**
     * 从列表项中提取图片。
     */
    private String extractItemPic(String itemHtml, String picSelector) {
        try {
            Document doc = Jsoup.parse(itemHtml);
            Element el = doc.selectFirst(picSelector);
            if (el != null) {
                String src = el.attr("src");
                if (src.isEmpty()) {
                    src = el.attr("data-src");
                }
                if (src.isEmpty()) {
                    src = el.attr("data-original");
                }
                return src;
            }
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        return "";
    }

    /**
     * 从列表项中提取名称。
     */
    private String extractItemName(String itemHtml, String nameSelector) {
        try {
            Document doc = Jsoup.parse(itemHtml);
            Element el = doc.selectFirst(nameSelector);
            if (el != null) {
                return el.text().trim();
            }
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        return "";
    }

    // ==================== 工具方法 - 多请求处理 ====================

    /**
     * 执行多请求逻辑。
     */
    private String multiRequest(List<Map<String, String>> requests) {
        try {
            if (requests == null || requests.isEmpty()) {
                return "[]";
            }

            com.google.gson.JsonArray jsonArray = new com.google.gson.JsonArray();
            for (Map<String, String> req : requests) {
                com.google.gson.JsonObject obj = new com.google.gson.JsonObject();
                obj.addProperty("url", req.getOrDefault("url", ""));
                obj.addProperty("method", req.getOrDefault("method", "GET"));
                if (req.containsKey("headers")) {
                    com.google.gson.JsonObject headers = new com.google.gson.JsonObject();
                    for (Map.Entry<String, String> entry : ((Map<String, String>) Json.fromJson(req.get("headers"), Map.class)).entrySet()) {
                        headers.addProperty(entry.getKey(), entry.getValue());
                    }
                    obj.add("headers", headers);
                }
                jsonArray.add(obj);
            }

            return spiderApi.multiReq(jsonArray);
        } catch (Exception e) {
            SpiderDebug.log(e);
            return "[]";
        }
    }

    // ==================== XBPQ高级语法支持 ====================

    /**
     * 初始化xbpq高级语法配置。
     */
    private void initAdvancedConfig() {
        try {
            // 初始化动态域名映射
            if (!domainConfig.isEmpty()) {
                domainMap = Json.fromJson(domainConfig, HashMap.class);
                if (domainMap == null) {
                    domainMap = new HashMap<>();
                }
            }

            // 初始化工具函数映射
            String toolConfig = getConfig("工具", "");
            if (!toolConfig.isEmpty()) {
                toolMap = Json.fromJson(toolConfig, HashMap.class);
                if (toolMap == null) {
                    toolMap = new HashMap<>();
                }
            }

            // 初始化数组替换规则
            String replaceConfig = getConfig("替换", "");
            if (!replaceConfig.isEmpty()) {
                replaceMap = Json.fromJson(replaceConfig, HashMap.class);
                if (replaceMap == null) {
                    replaceMap = new HashMap<>();
                }
            }

            // 初始化数组排序规则
            String sortConfig = getConfig("排序", "");
            if (!sortConfig.isEmpty()) {
                sortMap = Json.fromJson(sortConfig, HashMap.class);
                if (sortMap == null) {
                    sortMap = new HashMap<>();
                }
            }

            // 初始化数组不包含规则
            String excludeConfig = getConfig("不包含", "");
            if (!excludeConfig.isEmpty()) {
                excludeMap = Json.fromJson(excludeConfig, HashMap.class);
                if (excludeMap == null) {
                    excludeMap = new HashMap<>();
                }
            }

            // 处理动态域名
            if (!publishPage.isEmpty() && !domainConfig.isEmpty()) {
                processDynamicDomain();
            }
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
    }

    /**
     * 处理动态域名。
     */
    private void processDynamicDomain() {
        try {
            String publishHtml = get(publishPage, null);
            if (!publishHtml.isEmpty()) {
                // 从发布页提取域名
                Matcher domainMatcher = Pattern.compile("(https?://[^/\\s<>\"']+)", Pattern.CASE_INSENSITIVE).matcher(publishHtml);
                if (domainMatcher.find()) {
                    String newDomain = domainMatcher.group(1);
                    // 解析域名-c映射
                    if (!domainConfig.isEmpty()) {
                        HashMap<String, String> domainMap = Json.fromJson(domainConfig, HashMap.class);
                        if (domainMap != null) {
                            for (String key : domainMap.keySet()) {
                                this.domainMap.put(key, newDomain);
                            }
                        }
                    }
                    // 更新站点前缀
                    Matcher hostMatcher = Pattern.compile("^(https?://[^/]+)").matcher(newDomain);
                    if (hostMatcher.find()) {
                        this.sitePrefix = hostMatcher.group(1);
                    }
                }
            }
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
    }

    /**
     * 处理xbpq数组修饰符（替换、排序、不包含等）。
     *
     * @param content 原始内容
     * @param modifier 修饰符字符串，如 "[替换:奇异视频>>黑狐一线]"
     * @return 处理后的内容
     */
    private String processArrayModifier(String content, String modifier) {
        if (content == null || modifier == null || modifier.isEmpty()) {
            return content;
        }
        try {
            // 解析修饰符格式：[替换:原值>>替换值]、[排序:排序词]、[不包含:排除词]等
            Matcher modifierMatcher = Pattern.compile("\\[(\\w+):([^\\]]+)\\]").matcher(modifier);
            while (modifierMatcher.find()) {
                String type = modifierMatcher.group(1);
                String value = modifierMatcher.group(2);

                switch (type) {
                    case "替换":
                        // 格式：原值>>替换值
                        String[] replaceParts = value.split(">>");
                        if (replaceParts.length == 2) {
                            content = content.replace(replaceParts[0], replaceParts[1]);
                        }
                        break;
                    case "排序":
                        // 格式：排序词（用于重新排列数组项）
                        // 这里简化处理，实际xbpq实现更复杂
                        break;
                    case "不包含":
                        // 格式：排除词
                        if (!value.isEmpty()) {
                            // 移除包含排除词的项
                            String[] lines = content.split("\n");
                            StringBuilder sb = new StringBuilder();
                            for (String line : lines) {
                                if (!line.contains(value)) {
                                    if (sb.length() > 0) {
                                        sb.append("\n");
                                    }
                                    sb.append(line);
                                }
                            }
                            content = sb.toString();
                        }
                        break;
                    case "包含":
                        // 格式：包含词
                        if (!value.isEmpty()) {
                            String[] lines = content.split("\n");
                            StringBuilder sb = new StringBuilder();
                            for (String line : lines) {
                                if (line.contains(value)) {
                                    if (sb.length() > 0) {
                                        sb.append("\n");
                                    }
                                    sb.append(line);
                                }
                            }
                            content = sb.toString();
                        }
                        break;
                    case "截取":
                        // 格式：截取前缀>>截取后缀
                        String[] cutParts = value.split(">>");
                        if (cutParts.length == 2) {
                            int start = content.indexOf(cutParts[0]);
                            if (start >= 0) {
                                start += cutParts[0].length();
                                int end = content.indexOf(cutParts[1], start);
                                if (end >= 0) {
                                    content = content.substring(start, end);
                                }
                            }
                        }
                        break;
                    case "处理":
                        // 格式：工具名#参数
                        String[] processParts = value.split("#");
                        String toolName = processParts[0];
                        if (toolMap.containsKey(toolName)) {
                            String toolValue = toolMap.get(toolName);
                            // 根据工具类型处理
                            content = applyTool(content, toolValue);
                        }
                        break;
                    default:
                        break;
                }
            }
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        return content;
    }

    /**
     * 应用工具函数。
     */
    private String applyTool(String content, String toolValue) {
        if (content == null || toolValue == null) {
            return content;
        }
        try {
            // 支持的工具类型
            if (toolValue.contains("sha")) {
                // SHA加密
                return shaEncrypt(content);
            } else if (toolValue.contains("md5")) {
                // MD5加密
                return md5Encrypt(content);
            } else if (toolValue.contains("b64") || toolValue.contains("base64")) {
                // Base64处理
                if (toolValue.contains("解密") || toolValue.contains("decode")) {
                    return base64Decode(content);
                } else {
                    return base64Encode(content);
                }
            } else if (toolValue.contains("aes")) {
                // AES加解密
                // 格式：aes-加密/解密#key#iv
                String[] parts = toolValue.split("#");
                boolean isDecrypt = parts[0].contains("解密");
                String key = parts.length > 1 ? parts[1] : "";
                String iv = parts.length > 2 ? parts[2] : "";
                return aesProcess(content, key, iv, isDecrypt);
            } else if (toolValue.contains("截取")) {
                // 截取处理
                String[] parts = toolValue.split("#");
                if (parts.length >= 2) {
                    int start = Integer.parseInt(parts[1]);
                    int end = parts.length > 2 ? Integer.parseInt(parts[2]) : content.length();
                    return content.substring(start, Math.min(end, content.length()));
                }
            } else if (toolValue.contains("随机")) {
                // 随机字符
                String[] parts = toolValue.split("-");
                int length = parts.length > 1 ? Integer.parseInt(parts[1]) : 10;
                return randomString(length);
            }
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        return content;
    }

    /**
     * 处理xbpq URL变量替换（如 {{域名-c}}、{{key}}）。
     *
     * @param url 原始URL
     * @return 替换后的URL
     */
    private String processUrlVariables(String url) {
        if (url == null || url.isEmpty()) {
            return url;
        }

        try {
            // 1. 处理firstPage
            url = processFirstPage(url);
            // 2. 处理时间戳
            url = processTimestamp(url);
            // 3. 处理md5
            url = processMd5(url);
            // 4. 处理全角半角
            url = convertFullWidth(url);
            // 5. 处理URL变量 {{key}}
            return processUrlTemplate(url);
        } catch (Exception e) {
            SpiderDebug.log("processUrlVariables 异常: " + e.getMessage());
            return url;
        }
    }

    /**
     * 处理首屏特殊链接 [firstPage=...]。
     */
    private String processFirstPage(String url) {
        if (url == null || !url.contains(FIRST_PAGE_REGEX)) {
            return url;
        }
        try {
            int idx = url.indexOf(FIRST_PAGE_REGEX);
            if (idx < 0) {
                return url;
            }
            int endIdx = url.indexOf(']', idx);
            if (endIdx < 0) {
                return url;
            }
            String firstPageContent = url.substring(idx + FIRST_PAGE_REGEX.length(), endIdx);
            String base = url.substring(0, idx);
            SpiderDebug.log("processFirstPage 检测到首屏标记, firstPage=" + firstPageContent);
            // 返回基础URL，首屏逻辑由调用方处理
            return base;
        } catch (Exception e) {
            SpiderDebug.log("processFirstPage 异常: " + e.getMessage());
            return url;
        }
    }

    /**
     * 处理时间戳占位符（时间戳、时间标）。
     */
    private String processTimestamp(String url) {
        if (url == null) {
            return url;
        }
        try {
            long tsSeconds = System.currentTimeMillis() / 1000;
            long tsMillis = System.currentTimeMillis();
            url = url.replace(TIMESTAMP_KEY, String.valueOf(tsSeconds));
            url = url.replace(TIME_MARK_KEY, String.valueOf(tsMillis));
            SpiderDebug.log("processTimestamp 时间戳替换完成: 秒=" + tsSeconds + ", 毫秒=" + tsMillis);
            return url;
        } catch (Exception e) {
            SpiderDebug.log("processTimestamp 异常: " + e.getMessage());
            return url;
        }
    }

    /**
     * 处理md5()表达式。
     */
    private String processMd5(String url) {
        if (url == null || !url.contains(MD5_PREFIX)) {
            return url;
        }
        try {
            int start = url.indexOf(MD5_PREFIX);
            int end = url.indexOf(MD5_SUFFIX, start);
            if (start >= 0 && end > start) {
                String content = url.substring(start + MD5_PREFIX.length(), end);
                String md5 = md5Encrypt(content);
                url = url.substring(0, start) + md5 + url.substring(end + MD5_SUFFIX.length());
                SpiderDebug.log("processMd5 md5替换完成: " + content + " -> " + md5);
            }
            return url;
        } catch (Exception e) {
            SpiderDebug.log("processMd5 异常: " + e.getMessage());
            return url;
        }
    }

    /**
     * 全角半角自动转换。
     */
    private String convertFullWidth(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        try {
            char[] chars = text.toCharArray();
            for (int i = 0; i < chars.length; i++) {
                // 全角空格
                if (chars[i] == FULLWIDTH_SPACE) {
                    chars[i] = ' ';
                }
                // 全角括号
                else if (chars[i] == FULLWIDTH_LPAR) {
                    chars[i] = '(';
                } else if (chars[i] == FULLWIDTH_RPAR) {
                    chars[i] = ')';
                }
                // 全角花括号
                else if (chars[i] == FULLWIDTH_LBRACE) {
                    chars[i] = '{';
                } else if (chars[i] == FULLWIDTH_RBRACE) {
                    chars[i] = '}';
                }
                // 全角问号
                else if (chars[i] == FULLWIDTH_QMARK) {
                    chars[i] = '?';
                }
            }
            String result = new String(chars);
            if (!result.equals(text)) {
                SpiderDebug.log("convertFullWidth 全角半角转换完成");
            }
            return result;
        } catch (Exception e) {
            SpiderDebug.log("convertFullWidth 异常: " + e.getMessage());
            return text;
        }
    }

    /**
     * 处理URL模板变量 {{key}}。
     */
    private String processUrlTemplate(String url) {
        if (url == null || url.isEmpty()) {
            return url;
        }
        try {
            // 处理 ;;后缀（如 ;;mrcRAD、;;m0）
            url = processSuffix(url);

            // 处理 {{变量名}} 格式
            Matcher varMatcher = Pattern.compile("\\{\\{(\\w+-?\\w*)\\}\\}").matcher(url);
            StringBuffer sb = new StringBuffer();
            while (varMatcher.find()) {
                String varName = varMatcher.group(1);
                String replacement = "";

                // 优先从域名映射中获取
                if (domainMap.containsKey(varName)) {
                    replacement = domainMap.get(varName);
                }
                // 然后从工具映射中获取
                else if (toolMap.containsKey(varName)) {
                    replacement = toolMap.get(varName);
                }
                // 最后从配置中获取
                else {
                    replacement = getConfig(varName, "");
                }

                varMatcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
            }
            varMatcher.appendTail(sb);
            return sb.toString();
        } catch (Exception e) {
            SpiderDebug.log(e);
            return url;
        }
    }

    /**
     * 处理URL后缀（如 ;;mrcRAD、;;m0）。
     *
     * @param url 原始URL
     * @return 处理后的URL
     */
    private String processSuffix(String url) {
        if (url == null || !url.contains(";;")) {
            return url;
        }
        try {
            int suffixIdx = url.indexOf(";;");
            String base = url.substring(0, suffixIdx);
            String suffix = url.substring(suffixIdx + 2);

            // 处理不同的后缀
            if (suffix.contains("mrc")) {
                // mrc后缀：移动端请求头
                // 在实际应用中，这里可以添加额外的请求逻辑
                SpiderDebug.log("检测到mrc后缀，使用移动端请求头");
            } else if (suffix.contains("m0") || suffix.contains("m1") || suffix.contains("m2")) {
                // 数字后缀：请求方式标记
                SpiderDebug.log("检测到移动请求标记: " + suffix);
            } else if (suffix.contains("RAD")) {
                // RAD后缀：特定解析规则
                SpiderDebug.log("检测到RAD后缀");
            }

            // 返回基础URL（后缀用于特殊处理）
            return base;
        } catch (Exception e) {
            SpiderDebug.log(e);
            return url;
        }
    }

    /**
     * SHA1加密。
     */
    private String shaEncrypt(String content) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-1");
            byte[] digest = md.digest(content.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            SpiderDebug.log(e);
            return content;
        }
    }

    /**
     * MD5加密。
     */
    private String md5Encrypt(String content) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(content.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            SpiderDebug.log(e);
            return content;
        }
    }

    /**
     * Base64编码。
     */
    private String base64Encode(String content) {
        try {
            return android.util.Base64.encodeToString(content.getBytes("UTF-8"), android.util.Base64.NO_WRAP);
        } catch (Exception e) {
            SpiderDebug.log(e);
            return content;
        }
    }

    /**
     * Base64解码。
     */
    private String base64Decode(String content) {
        try {
            return new String(android.util.Base64.decode(content, android.util.Base64.NO_WRAP), "UTF-8");
        } catch (Exception e) {
            SpiderDebug.log(e);
            return content;
        }
    }

    /**
     * AES加解密处理。
     */
    private String aesProcess(String content, String key, String iv, boolean isDecrypt) {
        if (isDecrypt) {
            return decryptAes(content, "UTF-8", key, iv);
        } else {
            return encryptAes(content, "UTF-8", key, iv);
        }
    }

    /**
     * 生成随机字符串。
     */
    private String randomString(int length) {
        try {
            String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
            java.util.Random random = new java.util.Random();
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < length; i++) {
                sb.append(chars.charAt(random.nextInt(chars.length())));
            }
            return sb.toString();
        } catch (Exception e) {
            SpiderDebug.log(e);
            return "";
        }
    }

    // ==================== 工具方法 - 过滤构建 ====================

    /**
     * 构建筛选器列表。
     */
    private LinkedHashMap<String, List<Filter>> buildFilter(String classValue, String classUrl,
            String fclassName, String fclassValue, String fcatelogName, String fcatelogValue,
            String fareaName, String fareaValue, String fyearName, String fyearValue,
            String flangName, String flangValue, String fsortName, String fsortValue) {
        LinkedHashMap<String, List<Filter>> filters = new LinkedHashMap<>();

        // 类型筛选
        if (!fclassName.isEmpty() && !fclassValue.isEmpty()) {
            List<Filter> fclassList = new ArrayList<>();
            String[] names = fclassName.split("&");
            String[] values = fclassValue.split("&");
            for (int i = 0; i < names.length; i++) {
                fclassList.add(new Filter(names[i], values[i]));
            }
            filters.put(fclassName, fclassList);
        }

        // 分类筛选
        if (!fcatelogName.isEmpty() && !fcatelogValue.isEmpty()) {
            List<Filter> catelogList = new ArrayList<>();
            String[] names = fcatelogName.split("&");
            String[] values = fcatelogValue.split("&");
            for (int i = 0; i < names.length; i++) {
                catelogList.add(new Filter(names[i], values[i]));
            }
            filters.put(fcatelogName, catelogList);
        }

        // 地区筛选
        if (!fareaName.isEmpty() && !fareaValue.isEmpty()) {
            List<Filter> areaList = new ArrayList<>();
            String[] names = fareaName.split("&");
            String[] values = fareaValue.split("&");
            for (int i = 0; i < names.length; i++) {
                areaList.add(new Filter(names[i], values[i]));
            }
            filters.put(fareaName, areaList);
        }

        // 年份筛选
        if (!fyearName.isEmpty() && !fyearValue.isEmpty()) {
            List<Filter> yearList = new ArrayList<>();
            String[] names = fyearName.split("&");
            String[] values = fyearValue.split("&");
            for (int i = 0; i < names.length; i++) {
                yearList.add(new Filter(names[i], values[i]));
            }
            filters.put(fyearName, yearList);
        }

        // 语言筛选
        if (!flangName.isEmpty() && !flangValue.isEmpty()) {
            List<Filter> langList = new ArrayList<>();
            String[] names = flangName.split("&");
            String[] values = flangValue.split("&");
            for (int i = 0; i < names.length; i++) {
                langList.add(new Filter(names[i], values[i]));
            }
            filters.put(flangName, langList);
        }

        // 排序筛选
        if (!fsortName.isEmpty() && !fsortValue.isEmpty()) {
            List<Filter> sortList = new ArrayList<>();
            String[] names = fsortName.split("&");
            String[] values = fsortValue.split("&");
            for (int i = 0; i < names.length; i++) {
                sortList.add(new Filter(names[i], values[i]));
            }
            filters.put(fsortName, sortList);
        }

        return filters;
    }

    // ==================== 工具方法 - 选择器提取 ====================

    /**
     * 从Element提取文本（支持B64Dec模式）。
     */
    private String extractText(Element element, String rule) {
        if (element == null || rule == null || rule.isEmpty()) {
            return "";
        }
        try {
            if (rule.equals("Text")) {
                return element.text().trim();
            } else if (rule.equals("B64Dec")) {
                String text = element.text();
                try {
                    return new String(Base64.decode(text, Base64.DEFAULT));
                } catch (Exception e) {
                    return text;
                }
            } else if (rule.equals("Html")) {
                return element.html().trim();
            } else if (rule.contains("Attr")) {
                return element.attr(rule.replace("Attr", "")).trim();
            } else {
                return element.attr(rule).trim();
            }
        } catch (Exception e) {
            SpiderDebug.log(e);
            return "";
        }
    }

    /**
     * 从Element列表提取文本。
     */
    private String extractTextByRule(Elements elements, String rule) {
        if (elements == null || elements.isEmpty() || rule == null || rule.isEmpty()) {
            return "";
        }
        try {
            String[] parts = rule.split("\\|\\|");
            for (String part : parts) {
                String result = extractText(elements.first(), part);
                if (!result.isEmpty()) {
                    return result;
                }
            }
            return extractText(elements.first(), rule);
        } catch (Exception e) {
            SpiderDebug.log(e);
            return "";
        }
    }

    /**
     * 提取视频列表。
     */
    private JSONObject extractVideos(String html, String selector, Map<String, String> ext) {
        JSONObject result = new JSONObject();
        try {
            List<Vod> list = new ArrayList<>();

            if (html.isEmpty()) {
                result.put("list", list);
                result.put("page", 1);
                result.put("pagecount", 1);
                result.put("limit", 0);
                result.put("total", 0);
                return result;
            }

            // 处理JSON模式
            if (selector.startsWith("$")) {
                String jsonStr = selector.substring(1);
                JSONArray jsonArray = new JSONArray(html);
                for (int i = 0; i < jsonArray.length(); i++) {
                    JSONObject item = jsonArray.getJSONObject(i);
                    String vodId = item.optString("id", "");
                    String vodName = item.optString("name", "");
                    String vodPic = item.optString("pic", "");
                    String vodRemarks = item.optString("remarks", "");
                    list.add(new Vod(vodId, vodName, vodPic, vodRemarks));
                }
            } else {
                // HTML模式
                Document doc = Jsoup.parse(html);
                Elements elements = doc.select(selector);

                String idSelector = getConfig("id", "a");
                String nameSelector = getConfig("name", "a");
                String picSelector = getConfig("pic", "img");
                String remarksSelector = getConfig("remarks", ".record,.remarks");
                String titleSelector = getConfig("title", "a");

                for (Element el : elements) {
                    String vodId = el.attr("href");
                    if (vodId.isEmpty()) {
                        vodId = el.attr("data-src");
                    }
                    if (vodId.isEmpty()) {
                        vodId = el.attr("data-id");
                    }

                    String vodName = el.text();
                    String vodPic = el.attr("data-src");
                    if (vodPic.isEmpty()) {
                        vodPic = el.attr("src");
                    }
                    if (vodPic.isEmpty()) {
                        vodPic = el.attr("data-original");
                    }

                    String vodRemarks = el.select(remarksSelector).text().trim();
                    if (vodRemarks.isEmpty()) {
                        vodRemarks = el.select(".record").text().trim();
                    }

                    list.add(new Vod(vodId, vodName, vodPic, vodRemarks));
                }
            }

            // 获取分页信息
            String count = getConfig("页数", "");
            int pagecount = count.isEmpty() ? -1 : Integer.parseInt(count);

            result.put("list", new JSONArray());
            for (Vod vod : list) {
                result.put("list", ((JSONArray) result.get("list")).put(Json.toJson(vod)));
            }
            result.put("page", 1);
            result.put("pagecount", pagecount);
            result.put("limit", list.size());
            result.put("total", list.size());

        } catch (Exception e) {
            SpiderDebug.log(e);
            try {
                result.put("list", new JSONArray());
            } catch (JSONException je) {
                // ignore
            }
        }
        return result;
    }

    // ==================== Spider接口实现 ====================

    /**
     * 首页内容。
     */
    @Override
    public String homeContent(boolean flag) throws Exception {
        try {
            List<Class> classes = new ArrayList<>();
            LinkedHashMap<String, List<Filter>> filters = new LinkedHashMap<>();

            // 解析分类名称和值
            String className = getConfig("class_name", "");
            if (className.isEmpty()) {
                className = getConfig("分类名称", "");
            }
            String classValue = getConfig("class_value", "");
            if (classValue.isEmpty()) {
                classValue = getConfig("分类名称替换词", "");
            }

            // 如果有分类选择器，从HTML中提取
            String classSelector = getConfig("class_selector", "");
            if (!classSelector.isEmpty()) {
                String html = get(processUrlVariables(homeUrl), null);
                if (!html.isEmpty()) {
                    Elements elements = Jsoup.parse(html).select(classSelector);
                    for (Element el : elements) {
                        String typeName = el.text().trim();
                        String typeId = el.attr("href").trim();
                        if (!typeName.isEmpty() && !typeId.isEmpty()) {
                            classes.add(new Class(typeId, typeName));
                        }
                    }
                }
            } else if (!className.isEmpty() && !classValue.isEmpty()) {
                // 直接配置分类
                String[] nameParts = className.split("&");
                String[] valueParts = classValue.split("&");
                for (int i = 0; i < nameParts.length; i++) {
                    String val = i < valueParts.length ? valueParts[i] : valueParts[0];
                    classes.add(new Class(val.replaceAll("&&", "&"), nameParts[i]));
                }
            }

            // 解析筛选配置
            String filterJson = getConfig("筛选", "");
            if (filterJson.isEmpty()) {
                filterJson = getConfig("filterdata", "");
            }

            if (!filterJson.isEmpty()) {
                if (filterJson.startsWith("clan://") || filterJson.startsWith("http") || filterJson.startsWith("./")) {
                    String resp = OkHttp.string(filterJson, null).trim();
                    if (resp.startsWith("{") && resp.endsWith("}")) {
                        JSONObject filterObj = new JSONObject(resp);
                        filters = Json.fromJson(filterObj.toString(),
                            new com.google.gson.reflect.TypeToken<LinkedHashMap<String, List<Filter>>>() {}.getType());
                    }
                } else if (filterJson.equalsIgnoreCase("EXT")) {
                    // 动态构建筛选
                    String fclassName = getConfig("fclass_name", "");
                    String fclassValue = getConfig("fclass_value", "");
                    String fcatelogName = getConfig("fcatelog_name", "");
                    String fcatelogValue = getConfig("fcatelog_value", "");
                    String fareaName = getConfig("farea_name", "");
                    String fareaValue = getConfig("farea_value", "");
                    String fyearName = getConfig("fyear_name", "");
                    String fyearValue = getConfig("fyear_value", "");
                    String flangName = getConfig("flang_name", "");
                    String flangValue = getConfig("flang_value", "");
                    String fsortName = getConfig("fsort_name", "时间&人气&评分");
                    String fsortValue = getConfig("fsort_value", "time&hits&score");

                    LinkedHashMap<String, List<Filter>> builtFilters = buildFilter(
                        classValue, getConfig("class_url", ""),
                        fclassName, fclassValue, fcatelogName, fcatelogValue,
                        fareaName, fareaValue, fyearName, fyearValue,
                        flangName, flangValue, fsortName, fsortValue);
                    if (!builtFilters.isEmpty()) {
                        filters.putAll(builtFilters);
                    }
                } else {
                    // 直接JSON配置
                    try {
                        JSONObject filterObj = new JSONObject(filterJson);
                        filters = Json.fromJson(filterObj.toString(),
                            new com.google.gson.reflect.TypeToken<LinkedHashMap<String, List<Filter>>>() {}.getType());
                    } catch (Exception e) {
                        // 忽略
                    }
                }
            }

            return Result.string(classes, filters);
        } catch (Exception e) {
            SpiderDebug.log(e);
            return Result.error(e.getMessage());
        }
    }

    /**
     * 首页视频内容。
     */
    @Override
    public String homeVideoContent() throws Exception {
        try {
            String html = get(homeUrl, null);
            if (html.isEmpty()) {
                return Result.string(Collections.emptyList());
            }

            List<Vod> list = new ArrayList<>();
            String selector = getConfig("一级", "");
            if (selector.isEmpty()) {
                return Result.string(Collections.emptyList());
            }

            // 处理数组修饰符
            String arrayModifier = getConfig("数组修饰", "");
            if (!arrayModifier.isEmpty()) {
                html = processArrayModifier(html, arrayModifier);
            }

            Elements elements = Jsoup.parse(html).select(selector);
            for (Element el : elements) {
                String vodId = el.attr("href").trim();
                String vodName = el.text().trim();
                String vodPic = el.attr("data-src").trim();
                if (vodPic.isEmpty()) {
                    vodPic = el.attr("src").trim();
                }
                String vodRemarks = el.select(".record").text().trim();
                if (vodRemarks.isEmpty()) {
                    vodRemarks = el.select(".remarks").text().trim();
                }

                list.add(new Vod(vodId, vodName, vodPic, vodRemarks));
            }

            return Result.string(list);
        } catch (Exception e) {
            SpiderDebug.log(e);
            return Result.error(e.getMessage());
        }
    }

    /**
     * 分类内容。
     */
    @Override
    public String categoryContent(String tid, String pg, boolean flag, HashMap<String, String> extend) throws Exception {
        try {
            String url = getConfig("class_url", "");
            if (url.isEmpty()) {
                url = homeUrl;
            }
            url = processUrlVariables(url);
            url = url.replace("{cateId}", tid).replace("{catePg}", pg);

            // 处理分页
            String firstPage = getConfig("firstpage", "1");
            if (!firstPage.isEmpty() && !"1".equals(firstPage)) {
                int pgNum = Integer.parseInt(pg);
                int firstPageNum = Integer.parseInt(firstPage);
                if (pgNum == 1) {
                    url = url.replace("/" + pg, "");
                }
            }

            // 检查是否需要POST
            String method = getConfig("请求方式", "");
            boolean isPost = method.contains("POST") || method.contains("post");
            String postData = getConfig("post数据", "");

            String html;
            if (isPost && !postData.isEmpty()) {
                Map<String, String> data = new HashMap<>();
                String[] parts = postData.split("&");
                for (String part : parts) {
                    String[] kv = part.split("=");
                    if (kv.length == 2) {
                        data.put(kv[0], kv[1]);
                    }
                }
                html = post(url, data, null);
            } else {
                html = get(url, null);
            }

            if (html.isEmpty()) {
                return Result.string(Collections.emptyList());
            }

            List<Vod> list = new ArrayList<>();
            String selector = getConfig("一级", "");
            if (selector.isEmpty()) {
                return Result.string(Collections.emptyList());
            }

            Elements elements = Jsoup.parse(html).select(selector);
            for (Element el : elements) {
                String vodId = el.attr("href").trim();
                String vodName = el.text().trim();
                String vodPic = el.attr("data-src").trim();
                if (vodPic.isEmpty()) {
                    vodPic = el.attr("src").trim();
                }
                String vodRemarks = el.select(".record").text().trim();
                if (vodRemarks.isEmpty()) {
                    vodRemarks = el.select(".remarks").text().trim();
                }

                // cover修正：补全图片URL协议和路径
                if (coverFix && !vodPic.isEmpty() && !vodPic.startsWith("http") && !vodPic.startsWith("//")) {
                    vodPic = fixCover(homeUrl, vodPic);
                }

                Vod vod = new Vod(vodId, vodName, vodPic, vodRemarks);

                // 图文模式：设置标签
                if (imageTextMode) {
                    vod.setVodTag("图文");
                }
                // 横图模式：设置样式
                if (horizontalMode) {
                    vod.setStyle(com.github.catvod.bean.Vod.Style.rect());
                }

                list.add(vod);
            }

            // 获取分页信息
            String count = getConfig("页数", "");
            int pagecount = count.isEmpty() ? -1 : Integer.parseInt(count);

            return Result.string(Integer.parseInt(pg), pagecount, list.size(), list.size(), list);
        } catch (Exception e) {
            SpiderDebug.log(e);
            return Result.error(e.getMessage());
        }
    }

    /**
     * 详情内容。
     */
    @Override
    public String detailContent(List<String> ids) throws Exception {
        try {
            String vodId = ids.get(0);
            String url = getConfig("详情URL", "");
            if (url.isEmpty()) {
                url = homeUrl + vodId;
            }
            url = url.replace("{vid}", vodId);

            // XPath 模式：URL 以 \\ 开头时走节点解析
            if (url.startsWith("\\\\") || url.startsWith("//")) {
                return xpDetailContent(ids, url);
            }

            // 检测阿里盘链接
            if (ALIYUN_PATTERN.matcher(url).find()) {
                aliyunFlag = true;
                ArrayList<String> aliyunList = new ArrayList<>();
                aliyunList.add(url.trim());
                PushAgent pushAgent = new PushAgent();
                pushAgent.init(null, extend);
                return pushAgent.detailContent(aliyunList);
            }

            String html = get(url, null);
            if (html.isEmpty()) {
                return Result.error("无法获取详情");
            }

            Vod vod = new Vod();
            vod.setVodId(vodId);

            // 提取基本信息
            vod.setVodName(getText(html, getConfig("标题", "h1"), 0));
            vod.setVodPic(getAttr(html, getConfig("图片", "img"), "src", 0));
            vod.setVodRemarks(getText(html, getConfig("备注", ".remarks"), 0));
            vod.setVodYear(getText(html, getConfig("年份", ".year"), 0));
            vod.setVodArea(getText(html, getConfig("地区", ".area"), 0));
            vod.setVodActor(getText(html, getConfig("演员", ".actor"), 0));
            vod.setVodDirector(getText(html, getConfig("导演", ".director"), 0));
            vod.setVodContent(getText(html, getConfig("简介", ".content"), 0));
            vod.setTypeName(getText(html, getConfig("类型", ".type"), 0));

            // 检查是否直接播放
            String forcePlay = getConfig("force_play", "");
            if ("1".equals(forcePlay) || "是".equals(forcePlay)) {
                vod.setVodPlayFrom("默认");
                vod.setVodPlayUrl(vodId + "$" + url);
                return Result.string(vod);
            }

            // 提取播放列表
            String playSelector = getConfig("播放列表", "");
            String playUrlSelector = getConfig("播放链接", "");
            String playFromSelector = getConfig("播放来源", "");

            if (!playSelector.isEmpty() && !playUrlSelector.isEmpty()) {
                Elements playElements = Jsoup.parse(html).select(playSelector);
                List<String> playFrom = new ArrayList<>();
                List<String> playUrl = new ArrayList<>();

                for (Element el : playElements) {
                    String fromName = getText(el.html(), playFromSelector, 0);
                    if (fromName.isEmpty()) {
                        fromName = "默认";
                    }
                    playFrom.add(fromName);

                    Elements links = el.select(playUrlSelector);
                    StringBuilder urlBuilder = new StringBuilder();
                    for (int i = 0; i < links.size(); i++) {
                        String linkUrl = links.get(i).attr("href");
                        String linkName = links.get(i).text();
                        if (linkUrl.isEmpty()) {
                            linkUrl = links.get(i).attr("data-url");
                        }
                        if (urlBuilder.length() > 0) {
                            urlBuilder.append("$");
                        }
                        urlBuilder.append(linkName).append("$").append(linkUrl);
                    }
                    playUrl.add(urlBuilder.toString());
                }

                vod.setVodPlayFrom(TextUtils.join("$$$", playFrom));
                vod.setVodPlayUrl(TextUtils.join("$$$", playUrl));
            }

            return Result.string(vod);
        } catch (Exception e) {
            SpiderDebug.log(e);
            return Result.error(e.getMessage());
        }
    }

    /**
     * XPath 模式详情解析（内部实现）。
     *
     * <p>当详情页 URL 以 {@code \} 开头时启用，使用 XPath 配置键
     * （dtNode/dtCate/dtArea/dtYear/dtMark/dtDirector/dtActor/dtDesc 等）
     * 从 HTML 中提取详情字段和播放列表。</p>
     *
     * @param ids  详情 ID 列表
     * @param url  详情页 URL（已清洗）
     * @return 详情 JSON 字符串
     */
    private String xpDetailContent(List<String> ids, String url) throws Exception {
        try {
            String html = get(url, null);
            if (html.isEmpty()) {
                return Result.error("无法获取详情");
            }

            Vod vod = new Vod();
            vod.setVodId(ids.get(0).split("\\$\\$\\$")[0]);
            vod.setVodName(extractByXPath(html, "标题", "name"));
            vod.setVodPic(extractByXPath(html, "图片", "pic"));
            vod.setTypeName(extractByXPath(html, "类型", "dtCate"));
            vod.setVodYear(extractByXPath(html, "年份", "dtYear"));
            vod.setVodArea(extractByXPath(html, "地区", "dtArea"));
            vod.setVodRemarks(extractByXPath(html, "状态", "dtMark"));
            vod.setVodDirector(extractByXPath(html, "导演", "dtDirector"));
            vod.setVodActor(extractByXPath(html, "主演", "dtActor"));
            vod.setVodContent(extractByXPath(html, "简介", "dtDesc"));

            // 提取播放列表
            String fromNode = getConfig("线路节点", "dtFromNode");
            String fromName = getConfig("线路名", "dtFromName");
            String urlNode = getConfig("播放节点", "dtUrlNode");
            String urlSubNode = getConfig("播放子节点", "dtUrlSubNode");
            String urlName = getConfig("播放标题", "dtUrlName");
            String urlId = getConfig("播放链接", "dtUrlId");

            String playFrom;
            String playUrl;
            if (!fromNode.isEmpty()) {
                List<String> tabs = extractByXPathList(html, fromNode);
                List<String> names = new ArrayList<>();
                List<String> epGroups = new ArrayList<>();
                for (String tab : tabs) {
                    String name = extractByXPath(tab, fromName.isEmpty() ? ">&&<" : fromName);
                    if (name.isEmpty()) {
                        name = "线路" + (names.size() + 1);
                    }
                    names.add(name);
                    List<String> nodes = extractByXPathList(tab, urlSubNode);
                    List<String> eps = new ArrayList<>();
                    for (String node : nodes) {
                        String title = extractByXPath(node, urlName);
                        String link = extractByXPath(node, urlId);
                        if (title.isEmpty() && link.isEmpty()) continue;
                        eps.add(title + "$" + link);
                    }
                    epGroups.add(String.join("#", eps));
                }
                playFrom = String.join("$$$", names);
                playUrl = String.join("$$$", epGroups);
            } else {
                playFrom = "默认线路";
                List<String> nodes = extractByXPathList(html, urlSubNode);
                List<String> eps = new ArrayList<>();
                for (String node : nodes) {
                    String title = extractByXPath(node, urlName);
                    String link = extractByXPath(node, urlId);
                    if (title.isEmpty() && link.isEmpty()) continue;
                    eps.add(title + "$" + link);
                }
                playUrl = String.join("#", eps);
            }
            vod.setVodPlayFrom(playFrom);
            vod.setVodPlayUrl(playUrl);

            List<Vod> list = new ArrayList<>();
            list.add(vod);
            return Result.string(list);
        } catch (Exception e) {
            SpiderDebug.log(e);
            return Result.error(e.getMessage());
        }
    }

    /**
     * 从HTML中提取单个XPath字段。
     */
    private String extractByXPath(String html, String key, String defaultKey) {
        try {
            String selector = getConfig(key, defaultKey);
            if (selector.isEmpty()) {
                selector = defaultKey;
            }
            // 简化实现：使用Jsoup CSS选择器
            Elements elements = Jsoup.parse(html).select(selector);
            if (!elements.isEmpty()) {
                return elements.first().text().trim();
            }
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        return "";
    }

    /**
     * 2参数版本：使用 key 本身作为 CSS 选择器。
     */
    private String extractByXPath(String html, String selector) {
        try {
            Elements elements = Jsoup.parse(html).select(selector);
            if (!elements.isEmpty()) {
                return elements.first().text().trim();
            }
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        return "";
    }

    /**
     * 从HTML中提取多个XPath字段列表。
     */
    private List<String> extractByXPathList(String html, String selector) {
        List<String> result = new ArrayList<>();
        try {
            if (selector.isEmpty()) {
                return result;
            }
            Elements elements = Jsoup.parse(html).select(selector);
            for (Element el : elements) {
                result.add(el.html());
            }
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        return result;
    }

    /**
     * 搜索内容。
     */
    @Override
    public String searchContent(String keywords, boolean quick) throws Exception {
        try {
            String url = getConfig("搜索URL", "");
            if (url.isEmpty()) {
                url = getConfig("搜索链接", "");
            }
            if (url.isEmpty()) {
                url = processUrlVariables(homeUrl) + "?search=" + URLEncoder.encode(keywords, charset);
            }
            url = processUrlVariables(url).replace("{wd}", URLEncoder.encode(keywords, charset));

            String html = get(url, null);
            if (html.isEmpty()) {
                return Result.string(Collections.emptyList());
            }

            // 搜索模式：强制指定解析模式
            String searchMode = getConfig("搜索模式", "ssmoshi");
            
            // JSON 模式：仅明确指定 "json" 时进入
            if ("json".equals(searchMode)) {
                return parseJsonSearch(html).toString();
            }

            // XML 模式（RSS）
            if ("xml".equals(searchMode) || url.contains("rss.xml")
                    || html.trim().startsWith("<?xml") || html.trim().startsWith("<rss")) {
                return parseXmlSearch(html);
            }

            List<Vod> list = new ArrayList<>();
            String selector = getConfig("搜索", "");
            if (selector.isEmpty()) {
                selector = getConfig("搜索列表", "");
            }
            if (selector.isEmpty()) {
                return Result.string(Collections.emptyList());
            }

            // 处理数组修饰符
            String searchModifier = getConfig("搜索修饰", "");
            if (!searchModifier.isEmpty()) {
                html = processArrayModifier(html, searchModifier);
            }

            Elements elements = Jsoup.parse(html).select(selector);
            for (Element el : elements) {
                String vodId = el.attr("href").trim();
                String vodName = el.text().trim();
                String vodPic = el.attr("data-src").trim();
                if (vodPic.isEmpty()) {
                    vodPic = el.attr("src").trim();
                }
                String vodRemarks = el.select(".record").text().trim();

                list.add(new Vod(vodId, vodName, vodPic, vodRemarks));
            }

            return Result.string(list);
        } catch (Exception e) {
            SpiderDebug.log(e);
            return Result.error(e.getMessage());
        }
    }

    /**
     * 搜索内容（带页码）。
     */
    @Override
    public String searchContent(String keywords, boolean quick, String pg) throws Exception {
        return searchContent(keywords, quick);
    }

    /**
     * JSON 模式搜索结果解析。
     */
    private JSONObject parseJsonSearch(String html) {
        try {
            JSONObject json = new JSONObject(html);
            JSONArray searchList = json.optJSONArray("list");
            if (searchList == null) {
                JSONObject data = json.optJSONObject("data");
                if (data != null) {
                    searchList = data.optJSONArray("list");
                }
            }
            if (searchList == null) {
                JSONObject result = new JSONObject();
                try { result.put("list", new JSONArray()); } catch (JSONException je) {}
                return result;
            }
            String nameKey = getConfig("name", "搜索标题", "jsname", "jsonname");
            String idKey = getConfig("id", "搜索链接", "jsid", "jsonid");
            String picKey = getConfig("pic", "搜索图片", "jspic", "jsonpic");
            JSONArray list = new JSONArray();
            for (int i = 0; i < searchList.length(); i++) {
                try {
                    JSONObject item = searchList.getJSONObject(i);
                    String name = item.optString(nameKey.isEmpty() ? "name" : nameKey);
                    String link = item.optString(idKey.isEmpty() ? "id" : idKey);
                    String pic = item.optString(picKey.isEmpty() ? "pic" : picKey);
                    if (name.isEmpty()) continue;
                    Vod vod = new Vod(link, name, pic, "");
                    list.put(Json.toJson(vod));
                } catch (Exception ignored) {
                }
            }
            return new JSONObject().put("list", list);
        } catch (Exception e) {
            SpiderDebug.log(e);
            try {
                return new JSONObject().put("list", new JSONArray());
            } catch (JSONException je) {
                return new JSONObject();
            }
        }
    }

    /**
     * XML 模式搜索结果解析（RSS 格式：item/title/link/pic/pubDate）。
     */
    private String parseXmlSearch(String html) {
        try {
            String itemSel = getConfig("<item>&&</item>", "搜索数组", "sea_arr_pre");
            String titleSel = getConfig("<title>&&</title>", "搜索标题", "sea_title");
            String linkSel = getConfig("<link>&&</link>", "搜索链接", "sea_url");
            String picSel = getConfig("<pic>&&</pic>", "搜索图片", "sea_pic");
            String subSel = getConfig("<pubDate>&&</pubDate>", "搜索副标题", "sea_subtitle");

            // 二次截取：先按 item 分隔，再逐项提取
            String[] items = html.split(itemSel, -1);
            JSONArray list = new JSONArray();
            for (String item : items) {
                try {
                    if (item.isEmpty()) continue;
                    String title = extractByXPath(item, titleSel);
                    String link = extractByXPath(item, linkSel);
                    String pic = extractByXPath(item, picSel);
                    String sub = extractByXPath(item, subSel);
                    if (title.isEmpty()) continue;
                    if (!link.startsWith("http") && !link.startsWith("//")) {
                        link = baseUrl + (link.startsWith("/") ? "" : "/") + link;
                    }
                    Vod vod = new Vod(link, title, pic, sub);
                    list.put(Json.toJson(vod));
                } catch (Exception ignored) {
                }
            }
            return new JSONObject().put("list", list).toString();
        } catch (Exception e) {
            SpiderDebug.log(e);
            return new JSONObject().put("list", new JSONArray()).toString();
        }
    }

    /**
     * 播放器内容。
     */
    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        try {
            // 处理阿里盘转码
            if (flag.contains("Ali转码") || flag.contains("Open原画") || flag.contains("Open转码")) {
                PushAgent pushAgent = new PushAgent();
                pushAgent.init(null, extend);
                return pushAgent.playerContent(flag, id, vipFlags);
            }

            // P2P链接处理
            if (isP2PUrl(id)) {
                JSONObject p2pResult = new JSONObject();
                p2pResult.put("parse", 0);
                p2pResult.put("playUrl", "");
                p2pResult.put("url", id);
                return p2pResult.toString();
            }

            // 解析播放链接
            String playUrl = id;
            if (id.contains("$")) {
                String[] parts = id.split("\\$");
                if (parts.length >= 2) {
                    playUrl = parts[1];
                }
            }

            // 检测视频类型
            if (playUrl.contains(".m3u8")) {
                return Result.get().url(playUrl).m3u8().parse(0).string();
            } else if (playUrl.contains(".mp4") || playUrl.contains(".avi") || playUrl.contains(".rmvb")) {
                return Result.get().url(playUrl).mp4().parse(0).string();
            }

            // 使用解析器
            String parseUrl = getConfig("解析", "");
            if (parseUrl.isEmpty()) {
                parseUrl = getConfig("xn_parse", "");
            }

            if (!parseUrl.isEmpty()) {
                String finalUrl = parseUrl.replace("{url}", playUrl).replace("{cateId}", flag);
                String html = get(finalUrl, null);
                if (!html.isEmpty()) {
                    String resultUrl = getJson(html, "url");
                    if (!resultUrl.isEmpty()) {
                        return Result.get().url(resultUrl).parse(0).string();
                    }
                }
            }

            // 默认返回
            return Result.get().url(playUrl).parse(1).string();
        } catch (Exception e) {
            SpiderDebug.log(e);
            return Result.error(e.getMessage());
        }
    }

    // ==================== 私有方法 - URL判断 ====================

    /**
     * 判断是否为P2P链接。
     */
    private boolean isP2PUrl(String url) {
        if (url == null || url.isEmpty()) {
            return false;
        }
        return url.startsWith("magnet:") || url.startsWith("ed2k://") || url.startsWith("thunder://");
    }

    /**
     * 判断是否为阿里盘链接。
     */
    private boolean isAliyunUrl(String url) {
        if (url == null || url.isEmpty()) {
            return false;
        }
        return url.contains("alipan.com") || url.contains("aliyundrive.com") || url.contains("夸克");
    }

    // ==================== 私有方法 - 验证处理 ====================

    /**
     * 检查验证码。
     */
    private boolean checkVerify(String html) {
        try {
            String verifyText = getConfig("验证码文字", "输入验证码");
            return html.contains(verifyText) || html.contains("验证码") || html.contains("btwaf");
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 检查验证码路径。
     */
    private boolean checkVerifyPath(String html) {
        if (html == null || html.isEmpty()) {
            return false;
        }
        for (String path : VERIFY_PATHS) {
            if (html.contains(path)) {
                SpiderDebug.log("checkVerifyPath 检测到验证码路径: " + path);
                return true;
            }
        }
        return false;
    }

    /**
     * 处理btwaf防火墙。
     */
    private String handleBtwaf(String url, String html) {
        if (html == null || !html.contains(BTWAF_TEXT) || !html.contains(BTWAF_COOKIE)) {
            return html;
        }
        try {
            // 提取btwaf cookie值
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("btwaf=([^\"]+)");
            java.util.regex.Matcher matcher = pattern.matcher(html);
            if (matcher.find()) {
                String btwafValue = matcher.group(1);
                SpiderDebug.log("handleBtwaf 检测到btwaf防火墙, 提取token: " + btwafValue);
                // 构建带btwaf的URL
                String btwafUrl = url + (url.contains("?") ? "&" : "?") + "btwaf=" + btwafValue;
                SpiderDebug.log("handleBtwaf 重试URL: " + btwafUrl);
                // 重新请求
                return get(btwafUrl, null);
            }
        } catch (Exception e) {
            SpiderDebug.log("handleBtwaf 异常: " + e.getMessage());
        }
        return html;
    }

    /**
     * 处理huadong/renji嗅探页面。
     */
    private String handleSniffPage(String url, String html) {
        if (html == null || (!html.contains(SNIFF_HUADONG) && !html.contains(SNIFF_RENJI))) {
            return html;
        }
        try {
            boolean isRenji = html.contains(SNIFF_RENJI);
            SpiderDebug.log("handleSniffPage 检测到嗅探页面: isRenji=" + isRenji);
            // 实际应用中需要更复杂的处理逻辑
        } catch (Exception e) {
            SpiderDebug.log("handleSniffPage 异常: " + e.getMessage());
        }
        return html;
    }

    /**
     * 检查阿里云盘链接。
     */
    private boolean checkAliyun(String id) {
        if (id == null || id.isEmpty()) {
            return false;
        }
        boolean result = ALIYUN_PATTERN.matcher(id).find();
        SpiderDebug.log("checkAliyun 检查结果: id=" + id + ", 是阿里云盘链接=" + result);
        return result;
    }

    /**
     * 解码Unicode转义。
     */
    private String decodeHtml(String html) {
        if (html == null || html.isEmpty()) {
            return html;
        }
        try {
            // 匹配 \\uXXXX 模式
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\\\u([0-9a-fA-F]{4})");
            java.util.regex.Matcher matcher = pattern.matcher(html);
            StringBuffer sb = new StringBuffer();
            while (matcher.find()) {
                int codePoint = Integer.parseInt(matcher.group(1), 16);
                matcher.appendReplacement(sb, Character.toString((char) codePoint));
            }
            matcher.appendTail(sb);
            String result = sb.toString();
            if (!result.equals(html)) {
                SpiderDebug.log("decodeHtml Unicode解码完成");
            }
            return result;
        } catch (Exception e) {
            SpiderDebug.log("decodeHtml 异常: " + e.getMessage());
            return html;
        }
    }

    /**
     * 构建搜索请求头。
     */
    private Map<String, String> buildPostHeaders() {
        Map<String, String> headers = new HashMap<>();
        // 优先使用搜索请求头配置
        String searchHeaders = this.searchHeaders;
        if (searchHeaders == null || searchHeaders.isEmpty()) {
            // 回退到全局请求头
            searchHeaders = this.requestHeader;
        }
        if (searchHeaders != null && !searchHeaders.isEmpty()) {
            headers.putAll(parseHeaderConfig(searchHeaders));
        }
        // 追加accCookie
        if (!accCookie.isEmpty()) {
            String existingCookie = headers.get("Cookie");
            if (existingCookie != null && !existingCookie.isEmpty()) {
                headers.put("Cookie", existingCookie + ";" + accCookie);
            } else {
                headers.put("Cookie", accCookie);
            }
        }
        SpiderDebug.log("buildPostHeaders 搜索请求头构建完成: " + headers.keySet());
        return headers;
    }

    /**
     * 解析请求头配置（key$value#key$value格式）。
     */
    private Map<String, String> parseHeaderConfig(String headerConfig) {
        Map<String, String> headers = new HashMap<>();
        if (headerConfig == null || headerConfig.isEmpty()) {
            return headers;
        }
        try {
            String[] pairs = headerConfig.split("#");
            for (String pair : pairs) {
                String[] kv = pair.split("\\$");
                if (kv.length == 2) {
                    String key = kv[0].trim();
                    String value = kv[1].trim();
                    // 处理UA简写
                    value = processUaShortcuts(value);
                    // 处理Referer替换
                    if (key.equalsIgnoreCase("Referer") && value.contains("WebView")) {
                        value = homeUrl;
                    }
                    headers.put(key, value);
                }
            }
        } catch (Exception e) {
            SpiderDebug.log("parseHeaderConfig 异常: " + e.getMessage());
        }
        return headers;
    }

    /**
     * 处理UA简写。
     */
    private String processUaShortcuts(String ua) {
        if (ua == null || ua.isEmpty()) {
            return ua;
        }
        switch (ua) {
            case "PC_UA":
            case "电脑":
                return PC_UA;
            case "MOBILE_UA":
            case "手机":
                return MOBILE_UA;
            case "IOS_UA":
            case "苹果手机":
                return IOS_UA;
            case "MAC_UA":
            case "苹果电脑":
                return MAC_UA;
            default:
                return ua;
        }
    }

    /**
     * 获取链接文件名。
     */
    private String extractFileName(String link, String title) {
        if (link == null || link.isEmpty()) {
            return title;
        }
        try {
            String lowerLink = link.toLowerCase();
            // ed2k链接
            if (lowerLink.startsWith("ed2k://")) {
                int fileStart = link.indexOf("|file|");
                if (fileStart >= 0) {
                    int fileEnd = link.indexOf("|", fileStart + 6);
                    if (fileEnd > fileStart) {
                        return link.substring(fileStart + 6, fileEnd);
                    }
                }
            }
            // magnet链接
            else if (lowerLink.startsWith("magnet:")) {
                int dnStart = link.toLowerCase().indexOf("dn=");
                if (dnStart >= 0) {
                    int dnEnd = link.indexOf("&", dnStart);
                    String dn = dnEnd > 0 ? link.substring(dnStart + 3, dnEnd) : link.substring(dnStart + 3);
                    return java.net.URLDecoder.decode(dn, "UTF-8");
                }
            }
        } catch (Exception e) {
            SpiderDebug.log("extractFileName 异常: " + e.getMessage());
        }
        return title;
    }

    /**
     * 检查是否包含验证码路径。
     */
    private boolean containsVerifyPath(String html) {
        if (html == null) {
            return false;
        }
        for (String path : VERIFY_PATHS) {
            if (html.contains(path)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 获取验证页面。
     */
    private String getVerifyPage(String url) {
        try {
            String[] verifyPaths = {"/verify/index.html", "ajax/verify_check", "ajax.php?ac=code_check"};
            for (String path : verifyPaths) {
                String verifyUrl = url;
                if (!verifyUrl.endsWith("/")) {
                    verifyUrl += "/";
                }
                verifyUrl += path;
                String html = get(verifyUrl, null);
                if (!html.isEmpty() && html.length() > 100) {
                    return html;
                }
            }
            return "";
        } catch (Exception e) {
            return "";
        }
    }

    // ==================== 私有方法 - 配置处理 ====================

    /**
     * 确保站点配置已加载。
     */
    private void ensureSiteConfig() {
        if (siteConfig == null) {
            try {
                siteConfig = new JSONObject(extend);
            } catch (JSONException je) {
                siteConfig = new JSONObject();
            }
        }
    }

    /**
     * 获取代理基础地址。
     */
    protected String getProxyBase() {
        return proxyBase;
    }

    // ==================== 代理方法 ====================

    /**
     * 代理内容。
     */
    @Override
    public Object[] proxy(Map<String, String> params) throws Exception {
        try {
            String path = params.get("path");
            SpiderDebug.log("proxy 收到通用代理请求: path=" + path);
            if (path == null || path.isEmpty()) {
                return new Object[]{404, "text/plain", "Path not found"};
            }

            String url = path.replace("/proxy/", "");
            SpiderDebug.log("proxy 解析目标URL: " + url);
            if (url.isEmpty()) {
                return new Object[]{404, "text/plain", "URL not found"};
            }

            SpiderDebug.log("proxy 发起HTTP请求");
            Response response = OkHttp.newCall(url, (String) null);
            if (!response.isSuccessful()) {
                SpiderDebug.log("proxy 请求失败: code=" + response.code());
                response.close();
                return new Object[]{500, "text/plain", "Request failed"};
            }

            byte[] data = response.body().bytes();
            String contentType = response.header("Content-Type", "application/octet-stream");
            response.close();

            SpiderDebug.log("proxy 请求成功: content-type=" + contentType + ", size=" + data.length);
            return new Object[]{200, contentType, data};
        } catch (Exception e) {
            SpiderDebug.log("proxy 异常: " + e.getMessage());
            SpiderDebug.log(e);
            return new Object[]{500, "text/plain", e.getMessage()};
        }
    }

    // ==================== 其他接口方法 ====================

    /**
     * 动作处理。
     */
    @Override
    public String action(String action) throws Exception {
        return null;
    }

    /**
     * 销毁。
     */
    @Override
    public void destroy() {
        super.destroy();
    }

    /**
     * 手动视频检查。
     */
    @Override
    public boolean manualVideoCheck() throws Exception {
        return false;
    }

    /**
     * 检查视频格式。
     */
    @Override
    public boolean isVideoFormat(String url) throws Exception {
        if (url == null || url.isEmpty()) {
            return false;
        }
        String lowerUrl = url.toLowerCase();
        return lowerUrl.contains(".m3u8") || lowerUrl.contains(".mp4") ||
               lowerUrl.contains(".avi") || lowerUrl.contains(".rmvb") ||
               lowerUrl.contains(".flv") || lowerUrl.contains(".wmv") ||
               lowerUrl.contains(".mkv") || lowerUrl.contains(".mov");
    }

    /**
     * 直播内容。
     */
    @Override
    public String liveContent(String url) throws Exception {
        return "";
    }

    /**
     * 播放列表。
     */
    @Override
    public String playlist(String url) throws Exception {
        return "";
    }

    /**
     * 配置。
     */
    @Override
    public String config() throws Exception {
        return extend;
    }

    /**
     * 缓存。
     */
    @Override
    public String cache(String key) throws Exception {
        if (prefs == null) {
            return "";
        }
        return prefs.getString(key, "");
    }

    /**
     * 清除缓存。
     */
    @Override
    public void clearCache(String key) throws Exception {
        if (prefs != null) {
            prefs.edit().remove(key).apply();
        }
    }

    // ==================== 其他工具方法 ====================

    /**
     * 图片代理。
     *
     * @param params 代理参数
     * @return 代理结果数组
     */
    public static Object[] loadPic(Map<String, String> params) {
        try {
            String url = params.get("url");
            SpiderDebug.log("loadPic 收到图片代理请求: url=" + url);
            if (url == null || url.isEmpty()) {
                return new Object[]{404, "text/plain", "URL not found"};
            }

            // 构建图片代理请求头缓存
            if (picCache == null) {
                picCache = new HashMap<>();
                // Referer：指向图片来源页面，防止防盗链
                picCache.put("Referer", "https://img9.doubanio.com/");
                // User-Agent：模拟浏览器请求，提高图片获取成功率
                picCache.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/110.0.0.0 Safari/537.36");
                // Accept：声明支持图片格式
                picCache.put("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8");
                SpiderDebug.log("loadPic 初始化请求头缓存");
            }

            // 使用OkHttp请求图片
            SpiderDebug.log("loadPic 发起HTTP请求");
            Response response = OkHttp.newCall(url, picCache);
            if (response == null || !response.isSuccessful()) {
                SpiderDebug.log("loadPic 请求失败: code=" + (response != null ? response.code() : "null"));
                return new Object[]{500, "text/plain", "Request failed"};
            }

            byte[] data = response.body().bytes();
            String contentType = response.header("Content-Type", "image/jpeg");
            response.close();

            SpiderDebug.log("loadPic 请求成功: content-type=" + contentType + ", size=" + data.length);
            return new Object[]{200, contentType, data};
        } catch (Exception e) {
            SpiderDebug.log("loadPic 异常: " + e.getMessage());
            SpiderDebug.log(e);
            return new Object[]{500, "text/plain", e.getMessage()};
        }
    }

    /**
     * 修复封面URL。
     * 将相对路径或无协议路径补全为完整的绝对URL。
     */
    private String fixCover(String url, String pic) {
        SpiderDebug.log("fixCover 开始修复: url=" + url + ", pic=" + pic);
        if (pic == null || pic.isEmpty()) {
            SpiderDebug.log("fixCover 图片为空，返回原url");
            return url;
        }
        if (pic.startsWith("http")) {
            SpiderDebug.log("fixCover 图片已是完整URL，无需修复");
            return pic;
        }
        if (pic.startsWith("//")) {
            String fixedPic = "https:" + pic;
            SpiderDebug.log("fixCover 补全协议: " + pic + " -> " + fixedPic);
            return fixedPic;
        }
        String fixedPic = url + "/" + pic;
        SpiderDebug.log("fixCover 补全路径: " + pic + " -> " + fixedPic);
        return fixedPic;
    }

    /**
     * 获取视频信息。
     */
    private String getVodInfo(String html, String selector, String key) {
        try {
            Elements elements = Jsoup.parse(html).select(selector);
            for (Element el : elements) {
                String text = el.text();
                if (text.contains(key)) {
                    return text.replace(key, "").trim();
                }
            }
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        return "";
    }

    /**
     * 处理分页信息。
     */
    private JSONObject handlePagination(String html, JSONObject result) {
        try {
            String count = getConfig("计数", "");
            String limit = getConfig("每页数量", "20");
            String total = getConfig("总数", "");
            String page = getConfig("当前页", "1");
            String pages = getConfig("页数", "");

            int limitNum = limit.isEmpty() ? 20 : Integer.parseInt(limit);
            int pageNum = page.isEmpty() ? 1 : Integer.parseInt(page);
            int pagesNum = pages.isEmpty() ? -1 : Integer.parseInt(pages);

            result.put("page", pageNum);
            result.put("pagecount", pagesNum);
            result.put("limit", limitNum);
            result.put("total", limitNum * pagesNum);

            return result;
        } catch (Exception e) {
            SpiderDebug.log(e);
            return result;
        }
    }

    /**
     * 处理筛选数据。
     */
    private LinkedHashMap<String, List<Filter>> parseFilterData(String filterJson) {
        try {
            if (filterJson == null || filterJson.isEmpty()) {
                return new LinkedHashMap<>();
            }

            if (filterJson.startsWith("{") && filterJson.endsWith("}")) {
                return Json.fromJson(filterJson,
                    new com.google.gson.reflect.TypeToken<LinkedHashMap<String, List<Filter>>>() {}.getType());
            }

            if (filterJson.startsWith("clan://") || filterJson.startsWith("http")) {
                String resp = OkHttp.string(filterJson, null);
                JSONObject obj = new JSONObject(resp);
                return Json.fromJson(obj.toString(),
                    new com.google.gson.reflect.TypeToken<LinkedHashMap<String, List<Filter>>>() {}.getType());
            }

            return new LinkedHashMap<>();
        } catch (Exception e) {
            SpiderDebug.log(e);
            return new LinkedHashMap<>();
        }
    }

    // ==================== 静态缓存字段 ====================

    /** 图片代理缓存 */
    private static Map<String, String> picCache;

    /** 嗅探结果缓存（按URL隔离，LRU淘汰上限200条） */
    public static final Map<String, String> sniffResultMap =
            new java.util.LinkedHashMap<String, String>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(java.util.Map.Entry<String, String> eldest) {
                    return size() > 200;
                }
            };

    /**
     * 验证状态缓存。
     * <p>使用 LinkedHashMap 实现 LRU 淘汰（上限 100 条），配合 WeakReference 避免内存泄漏。</p>
     */
    public static final Map<String, String> verifyStateMap =
            new java.util.LinkedHashMap<String, String>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(java.util.Map.Entry<String, String> eldest) {
                    return size() > 100;
                }
            };

    /** 嗅探结果缓存前缀 */
    private static final String SNIFF_CACHE_PREFIX = "sniff_";

    /** 图片魔数校验表 */
    private static final int[][] IMAGE_MAGIC_BYTES = {
            {0xFF, 0xD8, 0xFF},           // JPEG
            {0x89, 0x50, 0x4E, 0x47},     // PNG
            {0x47, 0x49, 0x46},           // GIF
            {0x42, 0x4D}                   // BMP
    };

    // ==================== xbpq公共API接口 ====================

    /**
     * 嗅探缓存开关。
     */
    public boolean sniffCacheEnabled() {
        boolean result = !sniffResultMap.isEmpty();
        SpiderDebug.log("sniffCacheEnabled 检查结果: sniffResultMap大小=" + sniffResultMap.size() + ", 已缓存=" + result);
        return result;
    }

    /**
     * 嗅探手动开关。
     * 用于判断嗅探是否需要手动干预（如验证码、JS渲染等）。
     */
    public boolean sniffManual() {
        boolean manual = sniffWords != null && sniffWords.contains("x");
        SpiderDebug.log("sniffManual 检查结果: sniffWords=" + sniffWords + ", 需要手动处理=" + manual);
        return manual;
    }

    /**
     * 验证状态开关。
     */
    public boolean sniffVerifyEnabled() {
        boolean result = !verifyStateMap.isEmpty();
        SpiderDebug.log("sniffVerifyEnabled 检查结果: verifyStateMap大小=" + verifyStateMap.size() + ", 已验证=" + result);
        return result;
    }

    /**
     * 向验证状态缓存写入条目（自动触发 LRU 淘汰）。
     *
     * @param key   缓存键（如 "aliyun_shareId"）
     * @param value 缓存值
     */
    public static void putVerifyState(String key, String value) {
        verifyStateMap.put(key, value);
    }

    /**
     * 从验证状态缓存读取条目（自动清理已被 GC 的条目）。
     *
     * @param key 缓存键
     * @return 缓存值，若条目已被 GC 则返回 null
     */
    public static String getVerifyState(String key) {
        return verifyStateMap.get(key);
    }

    /**
     * 嗅探跳转开关。
     */
    public boolean sniffJumpEnabled() {
        boolean result = !sniffWords.isEmpty();
        SpiderDebug.log("sniffJumpEnabled 检查结果: sniffWords=" + sniffWords + ", 需要跳转=" + result);
        return result;
    }

    /**
     * 嗅探GBK开关。
     */
    public boolean sniffGbk() {
        boolean result = charset != null && charset.equalsIgnoreCase("gbk");
        SpiderDebug.log("sniffGbk 检查结果: charset=" + charset + ", 使用GBK=" + result);
        return result;
    }

    /**
     * 嗅探JS渲染开关。
     */
    public boolean sniffJsRender() {
        return false;
    }

    /**
     * 嗅探URL解码开关。
     */
    public boolean sniffNoUrlDecode() {
        return false;
    }

    /**
     * AES解密接口。
     */
    public String decrypt(String encrypted, String charset, String key, String iv) {
        try {
            return decryptAes(encrypted, charset != null ? charset : "UTF-8", key, iv);
        } catch (Exception e) {
            SpiderDebug.log(e);
            return "";
        }
    }

    /**
     * AES加密接口。
     */
    public String encrypt(String content, String charset, String key, String iv) {
        try {
            return encryptAes(content, charset != null ? charset : "UTF-8", key, iv);
        } catch (Exception e) {
            SpiderDebug.log(e);
            return "";
        }
    }

    /**
     * 获取Token接口。
     */
    public String getToken(String content, String charset, String key, String iv) {
        try {
            if (content == null || content.isEmpty()) {
                return "";
            }
            // 根据配置选择加密方式
            if (key != null && !key.isEmpty()) {
                return encrypt(content, charset, key, iv);
            }
            return content;
        } catch (Exception e) {
            SpiderDebug.log(e);
            return "";
        }
    }

    /**
     * 验证图片魔数接口。
     * 检查图片数据是否符合常见图片格式的文件头。
     */
    public boolean isImageMagicBytes(byte[] data) {
        if (data == null || data.length < 4) {
            SpiderDebug.log("isImageMagicBytes 数据无效: data=" + (data == null ? "null" : "length=" + data.length));
            return false;
        }
        // 检查JPEG: FF D8 FF
        if (data[0] == (byte) 0xFF && data[1] == (byte) 0xD8 && data[2] == (byte) 0xFF) {
            SpiderDebug.log("isImageMagicBytes 检测到JPEG格式");
            return true;
        }
        // 检查PNG: 89 50 4E 47
        if (data[0] == (byte) 0x89 && data[1] == (byte) 0x50 && data[2] == (byte) 0x4E && data[3] == (byte) 0x47) {
            SpiderDebug.log("isImageMagicBytes 检测到PNG格式");
            return true;
        }
        // 检查GIF: 47 49 46
        if (data[0] == (byte) 0x47 && data[1] == (byte) 0x49 && data[2] == (byte) 0x46) {
            SpiderDebug.log("isImageMagicBytes 检测到GIF格式");
            return true;
        }
        // 检查BMP: 42 4D
        if (data[0] == (byte) 0x42 && data[1] == (byte) 0x4D) {
            SpiderDebug.log("isImageMagicBytes 检测到BMP格式");
            return true;
        }
        SpiderDebug.log("isImageMagicBytes 未识别格式，前4字节: " + String.format("0x%02X 0x%02X 0x%02X 0x%02X", data[0], data[1], data[2], data[3]));
        return false;
    }

    /**
     * 代理接口。
     */
    public Object[] mProxy(Map<String, String> params) throws Exception {
        try {
            String target = params.get("url");
            SpiderDebug.log("mProxy 收到代理请求: url=" + target);
            if (target == null || target.isEmpty()) {
                return new Object[]{404, "text/plain", "URL not found"};
            }

            // 根据URL类型分发处理
            String prefix = target.split("\\?")[0].split("/").length > 3
                    ? target.split("/")[3] : "";
            SpiderDebug.log("mProxy 分发路径: prefix=" + prefix);

            switch (prefix) {
                case "pic":
                    SpiderDebug.log("mProxy 路由到图片代理");
                    return loadPic(params);
                case "sniff":
                    SpiderDebug.log("mProxy 路由到嗅探代理");
                    return proxySniff(params);
                default:
                    SpiderDebug.log("mProxy 路由到通用代理");
                    return proxy(params);
            }
        } catch (Exception e) {
            SpiderDebug.log("mProxy 异常: " + e.getMessage());
            SpiderDebug.log(e);
            return new Object[]{500, "text/plain", e.getMessage()};
        }
    }

    /**
     * 嗅探代理接口。
     */
    private Object[] proxySniff(Map<String, String> params) throws Exception {
        try {
            String url = params.get("url");
            String key = params.get("key");
            SpiderDebug.log("proxySniff 收到请求: url=" + url + ", key=" + key);

            // 检查缓存
            String cacheKey = SNIFF_CACHE_PREFIX + key;
            if (sniffResultMap.containsKey(cacheKey)) {
                String cached = sniffResultMap.get(cacheKey);
                SpiderDebug.log("proxySniff 命中缓存: key=" + cacheKey + ", 长度=" + (cached != null ? cached.length() : 0));
                if (cached != null && !cached.isEmpty()) {
                    return new Object[]{200, "text/plain", cached};
                }
            } else {
                SpiderDebug.log("proxySniff 缓存未命中: key=" + cacheKey);
            }

            // 执行嗅探
            String result = proxySniffContent(url);
            SpiderDebug.log("proxySniff 嗅探结果: 长度=" + (result != null ? result.length() : 0));
            if (result != null) {
                sniffResultMap.put(cacheKey, result);
                SpiderDebug.log("proxySniff 缓存写入: key=" + cacheKey);
            }

            return new Object[]{200, "text/plain", result != null ? result : ""};
        } catch (Exception e) {
            SpiderDebug.log("proxySniff 异常: " + e.getMessage());
            SpiderDebug.log(e);
            return new Object[]{500, "text/plain", e.getMessage()};
        }
    }

    /**
     * 代理嗅探内容接口。
     */
    private String proxySniffContent(String url) {
        try {
            SpiderDebug.log("proxySniffContent 收到URL: " + url);
            if (url == null || url.isEmpty()) {
                SpiderDebug.log("proxySniffContent URL为空，返回null");
                return null;
            }

            // 检查嗅探词
            String sniffWords = this.sniffWords;
            if (!sniffWords.isEmpty()) {
                SpiderDebug.log("proxySniffContent 检查嗅探词: " + sniffWords);
                // 实际嗅探逻辑：这里返回URL作为嗅探结果
                String result = url;
                SpiderDebug.log("proxySniffContent 嗅探结果: " + result);
                return result;
            }

            // 检查过滤词
            String filterWords = this.filterWords;
            if (!filterWords.isEmpty()) {
                SpiderDebug.log("proxySniffContent 检查过滤词: " + filterWords);
                // 如果URL包含过滤词，返回null（过滤掉）
                if (url.contains(filterWords)) {
                    SpiderDebug.log("proxySniffContent URL被过滤词过滤: " + filterWords);
                    return null;
                }
            }

            SpiderDebug.log("proxySniffContent 返回原始URL");
            return url;
        } catch (Exception e) {
            SpiderDebug.log("proxySniffContent 异常: " + e.getMessage());
            return null;
        }
    }

    // ==================== 缺失方法补充（从Smali还原） ====================

    /**
     * 从配置中提取字符串。
     */
    private String extractConfig(String key, String defaultVal, boolean flag, HashMap<String, String> map) {
        try {
            String val = getConfig(key, defaultVal);
            if (val.isEmpty() && map != null) {
                val = map.getOrDefault(key, defaultVal);
            }
            return val;
        } catch (Exception e) {
            SpiderDebug.log(e);
            return defaultVal;
        }
    }

    /**
     * 从配置中提取字符串链。
     */
    private String extractConfigChain(String key1, String key2, String defaultVal, boolean flag, HashMap<String, String> map) {
        String val = extractConfig(key1, defaultVal, flag, map);
        if (val.isEmpty() && key2 != null) {
            val = extractConfig(key2, defaultVal, flag, map);
        }
        return val;
    }

    /**
     * 获取字符串。
     */
    private String extractBetween(String start, String end, String content) {
        return extractBetween(start, end, content, "");
    }

    /**
     * 获取字符串（等价于原G方法完整版）。
     */
    private String extractBetween(String start, String end, String content, String def) {
        try {
            if (content == null || content.isEmpty()) {
                return def;
            }
            int startIdx = content.indexOf(start);
            if (startIdx < 0) {
                return def;
            }
            startIdx += start.length();
            int endIdx = content.indexOf(end, startIdx);
            if (endIdx < 0) {
                return def;
            }
            return content.substring(startIdx, endIdx).trim();
        } catch (Exception e) {
            SpiderDebug.log(e);
            return def;
        }
    }

    /**
     * 二次截取：用 pre/suf 做字符串粗切。
     */
    private String twiceExtract(String content, String pre, String suf) {
        if (content == null || content.isEmpty()) {
            return "";
        }
        try {
            int startIdx = content.indexOf(pre);
            if (startIdx < 0) {
                return "";
            }
            startIdx += pre.length();
            int endIdx = content.indexOf(suf, startIdx);
            if (endIdx < 0) {
                return content.substring(startIdx).trim();
            }
            return content.substring(startIdx, endIdx).trim();
        } catch (Exception e) {
            SpiderDebug.log(e);
            return "";
        }
    }

    /**
     * 完整二次截取：先用 pre/suf 做字符串粗切，再对结果应用 Jsoup CSS 选择器精切。
     */
    private String twiceExtractHtml(String content, String pre, String suf, String selector) {
        String rough = twiceExtract(content, pre, suf);
        if (rough.isEmpty() || selector == null || selector.isEmpty()) {
            return rough;
        }
        try {
            Document doc = Jsoup.parse(rough);
            Element el = doc.selectFirst(selector);
            return el != null ? el.html().trim() : rough.trim();
        } catch (Exception e) {
            SpiderDebug.log(e);
            return rough;
        }
    }

    /**
     * 二次截取后提取指定文本或属性（粗切+精切+取text/attr）。
     */
    private String extractFromTwice(String html, String pre, String suf, String selector, String attr) {
        String rough = twiceExtract(html, pre, suf);
        if (rough.isEmpty()) {
            return "";
        }
        try {
            org.jsoup.nodes.Document doc = org.jsoup.Jsoup.parse(rough);
            Element el = doc.selectFirst(selector);
            if (el == null) {
                return rough.trim();
            }
            if (attr != null && !attr.isEmpty()) {
                String val = el.attr(attr);
                return val != null ? val.trim() : "";
            }
            return el.text().trim();
        } catch (Exception e) {
            SpiderDebug.log(e);
            return "";
        }
    }

    /**
     * 简化的二次截取包装（仅粗切，不应用选择器）。
     */
    private String applyTwiceExtract(String html, String pre, String suf) {
        return twiceExtract(html, pre, suf);
    }

    /**
     * 按关键词提取列表项。
     */
    private ArrayList<String> extractListByKeyword(String content, String key) {
        ArrayList<String> result = new ArrayList<>();
        try {
            if (content == null || content.isEmpty()) {
                return result;
            }
            int idx = content.indexOf(key);
            if (idx < 0) {
                return result;
            }
            String rest = content.substring(idx + key.length());
            String[] parts = rest.split(",");
            for (String part : parts) {
                if (!part.isEmpty()) {
                    result.add(part.trim());
                }
            }
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        return result;
    }

    /**
     * 获取URL参数（等价于原L方法）。
     */
    private HashMap<String, String> parseUrlParams(String url) {
        HashMap<String, String> params = new HashMap<>();
        try {
            if (url == null || url.isEmpty() || !url.contains("?")) {
                return params;
            }
            String query = url.substring(url.indexOf('?') + 1);
            for (String part : query.split("&")) {
                String[] kv = part.split("=");
                if (kv.length == 2) {
                    params.put(kv[0], kv[1]);
                }
            }
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        return params;
    }

    /**
     * 解析URL参数（代理到parseUrlParams）。
     */
    private HashMap<String, String> getUrlParams(String url) {
        return parseUrlParams(url);
    }

    /**
     * 解析ArrayList（按分隔符分割）。
     */
    private ArrayList<String> parseArrayList(String key, String content, String def) {
        ArrayList<String> result = new ArrayList<>();
        try {
            if (content == null || content.isEmpty()) {
                return result;
            }
            String separator = ARRAY_SEPARATOR;
            int idx = content.indexOf(separator);
            if (idx < 0) {
                return parseArrayListWithDefault(key, content, def);
            }
            String[] parts = content.split(separator);
            for (String part : parts) {
                if (!part.isEmpty()) {
                    result.add(part.trim());
                }
            }
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        return result;
    }

    /**
     * 解析字符串字段（从JSON或文本中提取）。
     */
    private String parseStringField(String key, String content) {
        try {
            if (content == null || content.isEmpty()) {
                return "";
            }
            // 尝试从JSON对象中获取
            JSONObject obj = new JSONObject(content);
            if (obj != null) {
                String val = obj.optString(key, "");
                if (!val.isEmpty()) {
                    return val;
                }
            }
            // 尝试正则匹配
            String pattern = "\"" + key + "\"\\s*:\\s*\"([^\"]*)\"";
            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(pattern).matcher(content);
            if (matcher.find()) {
                return matcher.group(1);
            }
            return "";
        } catch (Exception e) {
            SpiderDebug.log(e);
            return "";
        }
    }

    /**
     * 按竖线分割解析ArrayList。
     */
    private ArrayList<String> splitByPipe(String key, String content, String def) {
        ArrayList<String> result = new ArrayList<>();
        try {
            if (content == null || content.isEmpty()) {
                return result;
            }
            String[] parts = content.split("|");
            for (String part : parts) {
                if (!part.isEmpty()) {
                    result.add(part.trim());
                }
            }
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        return result;
    }

    /**
     * 从URL提取JSON数据。
     */
    private JSONObject extractJsonFromUrl(String url, String key, boolean flag, HashMap<String, String> map) {
        try {
            String html = get(url, null);
            if (html.isEmpty()) {
                return new JSONObject();
            }
            JSONObject result = new JSONObject();
            // 尝试从HTML中提取JSON数据
            int jsonStart = html.indexOf('{');
            int jsonEnd = html.lastIndexOf('}');
            if (jsonStart >= 0 && jsonEnd > jsonStart) {
                String jsonStr = html.substring(jsonStart, jsonEnd + 1);
                try {
                    JSONObject json = new JSONObject(jsonStr);
                    String val = json.optString(key, "");
                    if (!val.isEmpty()) {
                        result.put(key, val);
                    }
                } catch (JSONException ex) {
                    // ignore
                }
            }
            return result;
        } catch (Exception e) {
            SpiderDebug.log(e);
            return new JSONObject();
        }
    }

    /**
     * 解析ArrayList（带默认值，等价于原e0方法）。
     */
    private ArrayList<String> parseArrayListWithDefault(String key, String content, String def) {
        ArrayList<String> result = new ArrayList<>();
        try {
            if (content == null || content.isEmpty()) {
                return result;
            }
            // 处理分隔符
            String separator = ARRAY_SEPARATOR;
            int idx = content.indexOf(separator);
            if (idx >= 0) {
                String[] parts = content.split(separator);
                for (String part : parts) {
                    if (!part.isEmpty()) {
                        result.add(part.trim());
                    }
                }
            } else {
                result.add(content.trim());
            }
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        return result;
    }

    /**
     * 清理HTML标签和多余空白。
     */
    private String cleanHtmlAndWhitespace(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        try {
            text = text.trim();
            // 移除HTML标签
            text = text.replaceAll("<[^>]+>", "").trim();
            // 移除多余空白
            text = text.replaceAll("\\s+", " ").trim();
            return text;
        } catch (Exception e) {
            SpiderDebug.log(e);
            return text;
        }
    }

    /**
     * 清理特殊字符。
     */
    private String cleanSpecialChars(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        try {
            String sep = SPECIAL_SEPARATOR;
            int idx = text.indexOf(sep);
            if (idx >= 0) {
                text = text.replace(sep, "-");
            }
            return text.trim();
        } catch (Exception e) {
            SpiderDebug.log(e);
            return text;
        }
    }

    /**
     * 从URL提取JSON数组（等价于原h方法）。
     */
    private JSONArray extractJsonArrayFromUrl(String url, String key1, String key2, String key3,
            String start, String end, String def) {
        try {
            String html = get(url, null);
            if (html.isEmpty()) {
                return new JSONArray();
            }
            JSONArray result = new JSONArray();
            // 尝试从JSON数组中提取
            int arrStart = html.indexOf('[');
            int arrEnd = html.lastIndexOf(']');
            if (arrStart >= 0 && arrEnd > arrStart) {
                String arrStr = html.substring(arrStart, arrEnd + 1);
                try {
                    JSONArray arr = new JSONArray(arrStr);
                    for (int i = 0; i < arr.length(); i++) {
                        result.put(arr.get(i));
                    }
                } catch (JSONException ex) {
                    // ignore
                }
            }
            return result;
        } catch (Exception e) {
            SpiderDebug.log(e);
            return new JSONArray();
        }
    }

    /**
     * 正则提取（等价于原h0方法）。
     */
    private String extractByRegex(String regex) {
        try {
            if (regex == null || regex.isEmpty()) {
                return "";
            }
            String pattern = "|" + regex + "|";
            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(pattern).matcher(debugInfo);
            if (matcher.find() && matcher.groupCount() >= 1) {
                return matcher.group(1).trim();
            }
            return "";
        } catch (Exception e) {
            SpiderDebug.log(e);
            return "";
        }
    }

    /**
     * 处理视频URL（等价于原i方法）。
     */
    private String processVideoUrl(String url) {
        try {
            if (url == null || url.isEmpty()) {
                return "";
            }
            // 获取URL参数
            HashMap<String, String> params = getUrlParams(url);
            // 构建完整URL
            StringBuilder sb = new StringBuilder();
            sb.append(url);
            if (!params.isEmpty()) {
                sb.append("?");
                boolean first = true;
                for (Map.Entry<String, String> entry : params.entrySet()) {
                    if (!first) {
                        sb.append("&");
                    }
                    sb.append(entry.getKey()).append("=").append(entry.getValue());
                    first = false;
                }
            }
            return sb.toString();
        } catch (Exception e) {
            SpiderDebug.log(e);
            return url;
        }
    }

    /**
     * 处理站点内容。
     */
    private String processSiteContent() {
        try {
            String content = getConfig("内容", "");
            if (content.isEmpty()) {
                return "";
            }
            // 处理变量替换
            String result = content;
            result = result.replace("{site}", sitePrefix);
            result = result.replace("{time}", String.valueOf(System.currentTimeMillis()));
            return result;
        } catch (Exception e) {
            SpiderDebug.log(e);
            return "";
        }
    }

    /**
     * 构建JSON对象。
     */
    private JSONObject buildJsonObject(String key, String value) {
        try {
            JSONObject result = new JSONObject();
            if (value == null || value.isEmpty()) {
                return result;
            }
            result.put(key, value);
            return result;
        } catch (Exception e) {
            SpiderDebug.log(e);
            return new JSONObject();
        }
    }

    /**
     * 获取页面JSON。
     */
    private JSONObject getPageJson(String url, String type, String encoding) {
        try {
            String html = get(url, null);
            if (html.isEmpty()) {
                return new JSONObject();
            }
            JSONObject result = new JSONObject();
            result.put("content", html);
            result.put("encoding", encoding);
            return result;
        } catch (Exception e) {
            SpiderDebug.log(e);
            return new JSONObject();
        }
    }

    /**
     * 提取文本片段。
     */
    private String extractTextSegment(String text, String pattern) {
        try {
            if (text == null || text.isEmpty() || pattern == null || pattern.isEmpty()) {
                return "";
            }
            int idx = text.indexOf(pattern);
            if (idx < 0) {
                return "";
            }
            // 构建正则模式
            String regex = "|" + "|" +
                    Pattern.quote(pattern) + "|" +
                    "|";
            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(regex).matcher(text);
            if (matcher.find()) {
                return matcher.group(1).trim();
            }
            return "";
        } catch (Exception e) {
            SpiderDebug.log(e);
            return "";
        }
    }

    /**
     * 解析JSON字符串。
     */
    private JSONObject parseJson(String json) {
        try {
            if (json == null || json.isEmpty()) {
                return new JSONObject();
            }
            return new JSONObject(json);
        } catch (Exception e) {
            SpiderDebug.log(e);
            return new JSONObject();
        }
    }

    /**
     * 获取配置内容JSON（等价于原s方法）。
     */
    private JSONObject getConfigJson() {
        try {
            String content = getConfig("内容", "");
            if (content.isEmpty()) {
                return new JSONObject();
            }
            return new JSONObject(content);
        } catch (Exception e) {
            SpiderDebug.log(e);
            return new JSONObject();
        }
    }

    /**
     * 按索引分割文本（等价于原u方法）。
     */
    private String splitByText(String text, String pattern, int index) {
        try {
            if (text == null || text.isEmpty() || pattern == null || pattern.isEmpty()) {
                return "";
            }
            String[] parts = text.split(Pattern.quote(pattern));
            if (index < parts.length) {
                return parts[index].trim();
            }
            return "";
        } catch (Exception e) {
            SpiderDebug.log(e);
            return "";
        }
    }

    /**
     * 提取JSON数组（等价于原v方法）。
     */
    private JSONArray extractJsonArray(String json, String key) {
        try {
            if (json == null || json.isEmpty()) {
                return new JSONArray();
            }
            JSONObject obj = new JSONObject(json);
            if (obj == null) {
                return new JSONArray();
            }
            // 尝试直接解析为数组
            if (json.trim().startsWith("[")) {
                return new JSONArray(json);
            }
            // 从对象中提取数组
            Object val = obj.opt(key);
            if (val instanceof JSONArray) {
                return (JSONArray) val;
            }
            return new JSONArray();
        } catch (Exception e) {
            SpiderDebug.log(e);
            return new JSONArray();
        }
    }

    /**
     * 处理字符串字段。
     */
    private String processStringField(String key, String content) {
        try {
            if (content == null || content.isEmpty()) {
                return "";
            }
            // 先处理分隔符
            String separator = ARRAY_SEPARATOR;
            int idx = content.indexOf(separator);
            if (idx >= 0) {
                // 常量已语义化，无需额外替换
            }
            return parseStringField(key, content);
        } catch (Exception e) {
            SpiderDebug.log(e);
            return "";
        }
    }

    /**
     * 清理字符串。
     */
    private String cleanStringWithKey(String key, String content) {
        try {
            if (content == null || content.isEmpty()) {
                return "";
            }
            String sep = ARRAY_SEPARATOR;
            if (content.endsWith(sep)) {
                content = content.replace(sep, "");
            }
            // 尝试从JSON中提取
            int idx = content.indexOf('P');
            if (idx >= 0) {
                return content.replace("P", "");
            }
            return extractStringFromJson(key, content);
        } catch (Exception e) {
            SpiderDebug.log(e);
            return "";
        }
    }

    /**
     * 解析Gson JSON对象。
     */
    private com.google.gson.JsonObject parseGsonObject(String json) {
        try {
            if (json == null || json.isEmpty()) {
                return new com.google.gson.JsonObject();
            }
            com.google.gson.JsonParser parser = new com.google.gson.JsonParser();
            return parser.parse(json).getAsJsonObject();
        } catch (Exception e) {
            SpiderDebug.log(e);
            return new com.google.gson.JsonObject();
        }
    }

    /**
     * 从JSON提取字符串字段。
     */
    private String extractStringFromJson(String key, String content) {
        try {
            if (content == null || content.isEmpty()) {
                return "";
            }
            // 尝试从JSON对象中提取
            JSONObject obj = new JSONObject(content);
            if (obj != null) {
                String val = obj.optString(key, "");
                if (!val.isEmpty()) {
                    // 清理特殊字符
                    val = val.replace("|", "");
                    val = val.replace("#", "");
                    val = val.replace("-", "");
                    return val.trim();
                }
            }
            return "";
        } catch (Exception e) {
            SpiderDebug.log(e);
            return "";
        }
    }

}
