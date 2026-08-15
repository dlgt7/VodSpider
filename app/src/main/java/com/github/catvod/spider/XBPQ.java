package com.github.catvod.spider;

import android.content.Context;
import android.text.TextUtils;

import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Crypto;
import com.github.catvod.utils.MathEval;
import com.github.catvod.utils.Util;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.ByteArrayInputStream;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.Inflater;

/**
 * XBPQ - 信必飘扩展解析器
 *
 * 功能覆盖：首页 / 分类列表 / 详情页 / 搜索 / 播放 / 代理请求 / AES加解密
 *
 * 配置驱动，支持两种方式初始化：
 *   1. extend 为 HTTP URL  → 从远程拉取 JSON 配置
 *   2. extend 为 JSON 字符串 → 直接解析
 *
 * 支持的解析模式：
 *   - CSS 选择器（Jsoup）
 *   - JSON 路径（直接字段读取）
 *   - Base64 / Hex / GZIP 编码内容内联
 */
public class XBPQ extends Spider {

    // ==================== 静态字段（全局共享） ====================
    private static boolean debug = false;
    private static String defaultUa = "";
    private static Context context;
    private static String extendConfig = null;
    private static String apiType = null;
    private static String singletonKey = "";
    private static XBPQ singleton = new XBPQ();
    private static String secretKey = "";
    private static String baseEncodeUrl = "";
    private static String imgFlag = "";
    private static String playHeaderCache = ""; // 播放请求头缓存（对应 Smali 静态字段 ا۪ۦ，由 remoteVideoUrlProcess 更新）
    // SpiderApi 相关（对应 Smali 静态字段 ا۪ۭ/اۣ۫/ا۫ۥ 与实例字段 اۥۭ）
    private static com.github.catvod.crawler.SpiderApi spiderApiRef = null; // SpiderApi 引用（Smali ا۪ۭ）
    private static String apiPort = "";      // SpiderApi 端口（Smali اۣ۫，同时写入 Init.port）
    private static String apiAddress = "";   // SpiderApi 服务地址（Smali ا۫ۥ，来自 getAddress(false)）
    private static String unusedField = "";
    private static Map<String, String> headerMap = new HashMap<>();
    private static JSONObject configJson = null;
    private static String configStr = "";
    private static String cacheJson = "";

    // ==================== 实例字段 - 基础配置 ====================
    private boolean isDebug = false;
    private int screenOrientation = -1; // 屏幕方向（Smali 实例字段 اۥۭ，来自 SpiderApi.getScreenOrientation()）
    private String homeUrl = "";
    private String className = "";
    private String classUrl = "";
    private String url = "";
    private boolean isJson = false;
    private int pageSize = 40;
    private String page = "";
    private String searchUrl = "search";
    private String title = "";
    private String pic = "";
    private String id = "";
    private String desc = "";
    private String content = "";
    private String parseUrl = "";
    private String parseHeader = "";
    private String parseUa = "";
    private String parseReferer = "";
    private int mode = 0;
    private String timeout = "5";
    private String referer = "";
    private String ua = "";
    private JSONArray playUrl = null;
    private JSONObject playerJson = new JSONObject();
    private JSONObject js = null;
    private JSONObject jx = null;
    private JSONObject ext = null;
    private boolean autoPlay = true;
    private String playFrom = "";
    private String playUrlRule = "";
    private String playUa = "";
    private String playJsonPath = "";
    private String playTitle = "";
    private String playLink = "";
    private String lineArray = "";
    private String lineTitle = "";
    private String playArray = "";
    private String playListTitle = "";
    private String playLinkUrl = "";
    private String playPrefix = "";
    private String searchArray = "";
    private String searchTitle = "";
    private String searchLink = "";
    private String searchPic = "";
    private String searchDesc = "";
    private String searchSuffix = "";
    private String arraySecondCut = "";
    private String playSecondCut = "";
    private String lineSecondCut = "";
    private String searchSecondCut = "";
    private boolean directPlay = false;
    private boolean noSniff = false;
    private boolean reverseOrder = false;
    private int searchMode = 0;
    private String startPage = "1";
    private int firstPage = 1;
    private String redirectUrl = "";
    private String filterList = "";
    private Map<String, String> variableMap = new HashMap<>();

    // ==================== 实例字段 - 元数据与高级配置 ====================
    private String siteName = "";       // 站名
    private String author = "";         // 作者
    private String charset = "";        // 编码
    private String sniffWords = "";     // 嗅探词
    private String filterWords = "";    // 过滤词
    private String releasePage = "";    // 发布页
    private String dynamicDomain = "";  // 域名-c（动态域名）
    private String originalUrlBak = ""; // 原始网址-b
    private String releaseSiteBak = ""; // 发布站-b
    private String overseasPermanent = ""; // 境外永久
    private String domainSuffix = "";   // 后缀
    private String urlPrefix = "";      // 前缀
    private String fixedDirectUrl = ""; // 固定直链
    private String backupPath = "";     // 回家的路
    private String keyExtractRule = ""; // key截取
    private String aesKey = "";         // key (AES)
    private String aesIv = "";          // iv
    private String secondaryCut = "";   // 二次截取（通用）
    private String secondClassDir = ""; // 二级目录
    private String secondClassId = "";  // 二级ID
    private String specialClassUrl = ""; // 特殊分类链接
    private String playRequestHeader = ""; // 播放请求头
    private String searchRequestHeader = ""; // 搜索请求头
    private String playSubtitle = "";   // 播放副标题
    private boolean forcePlay = false;  // 强制直连播放标记（Smali 实例字段 إۧ，由 id 段标记/magnet 等前缀触发）
    private String activeCate = "";     // 详情 id 中 activecate= 参数值（Smali 实例字段 اۣۣۧ）
    private String parseSourceBlacklist = ""; // 解析源码黑名单（# 分隔，Smali 122344）
    private String filmType = "";       // 影片类型
    private String filmYear = "";       // 影片年代
    private String filmArea = "";       // 影片地区
    private String filmStatus = "";     // 状态
    private String director = "";       // 导演
    private String actor = "";          // 主演
    private String filterSwitch = "";   // 筛选开关
    private String typeFilter = "";     // 类型筛选
    private String sortType = "";       // 排序
    private String sortOrder = "";      // 顺序
    private String classValue = "";     // 分类值
    private boolean decodeHtmlEnabled = false; // 解码html开关
    private String copyUrl = "";        // 复制链接选择器（用于 detailContent）
    private String encodeHtml = "";     // 编码html内容
    private String encodeHtmlUrl = "";  // 编码html请求的URL模板
    private String liveUrl = "";        // 直播地址
    private String liveCmsUrl = "";     // CMS直播源
    private String jsonPlay = "";       // 播放JSON路径
    private String vod_pic_ensure = ""; // 确保封面URL
    private String detailUrl = "";      // 详情页规则
    private String descUrl = "";        // 简介页规则
    private String picUrl = "";         // 图片URL模板
    private String titleUrl = "";       // 标题URL模板
    private String idUrl = "";          // ID URL模板
    private String contentUrl = "";     // 内容URL模板
    private String parseType = "j";     // 解析类型（j/css/rule）
    private String rulePlayFrom = "";   // 线路来源
    private String rulePlayUrl = "";    // 播放URL规则
    private String ruleTitle = "";      // 播放标题规则
    private String ruleUrl = "";        // 播放链接规则
    private String rulePre = "";        // 前缀规则
    private String rulePost = "";       // 后缀规则
    private String ruleCover = "";      // 封面规则
    private String ruleContentType = ""; // 内容类型
    private String rulePlayState = "";  // 播放状态
    private String rulePlayYear = "";   // 播放年代
    private String rulePlayArea = "";   // 播放地区
    private String rulePlayActor = "";  // 主演规则
    private String rulePlayDirector = ""; // 导演规则
    private String rulePlayDesc = "";   // 简介规则
    private String rulePlayNote = "";   // 备注规则
    private String ruleListArray = "";  // 列表数组规则
    private String ruleListTitle = "";  // 列表标题规则
    private String ruleListId = "";     // 列表ID规则
    private String ruleListPic = "";    // 列表图片规则
    private String ruleListDesc = "";   // 列表简介规则
    private String ruleDetailArray = ""; // 详情数组规则
    private String ruleDetailTitle = ""; // 详情标题规则
    private String ruleDetailId = "";   // 详情ID规则
    private String ruleDetailPic = "";  // 详情图片规则
    private String ruleDetailDesc = ""; // 详情简介规则
    private String ruleDetailContent = ""; // 详情内容规则
    private String ruleSearchArray = ""; // 搜索数组规则
    private String ruleSearchTitle = ""; // 搜索标题规则
    private String ruleSearchId = "";   // 搜索ID规则
    private String ruleSearchPic = "";  // 搜索图片规则
    private String ruleSearchDesc = ""; // 搜索简介规则
    private boolean enableCache = true; // 是否启用缓存
    private int cacheTime = 3600;       // 缓存时间（秒）
    private String cacheKeyPrefix = ""; // 缓存键前缀
    private String resultKey = "";      // 结果键
    private String defaultClass = "";   // 默认分类
    private String defaultVodId = "";   // 默认视频ID
    private String homeContentKey = ""; // 首页内容键
    private String categoryContentKey = ""; // 分类内容键
    private String searchContentKey = ""; // 搜索内容键

    // ==================== 实例字段 - 分类页独立规则（XBPQ.json 新增） ====================
    private String classSecondCut = ""; // 分类二次截取（从主页 html 中单独截取分类区域）
    private String classArray = "";     // 分类数组选择器（独立于数组规则）
    private String classTitle = "";     // 分类标题选择器
    private String classId = "";        // 分类ID选择器
    private String redirectParse = "";  // 跳转解析（解析器URL）
    private String wallpaper = "";      // 壁纸URL
    private String homeLogo = "";       // 首页Logo URL

    // ==================== 实例字段 - 列表/首页 JSON 规则 ====================
    // [新增]
    private String listStr = "";         // 列表 JSON 路径
    private String headJson = "";        // 头部 JSON 路径
    private String bodyJson = "";        // 主体 JSON 路径
    private String addJson = "";         // 追加 JSON 路径
    private String exp = "";             // 表达式
    private boolean decodeHtml = false;  // HTML 解码标志
    private String decodeHtmlUrl = "";   // 解码 HTML 的 URL

    // ==================== 实例字段 - 分页与列表规则 ====================
    private String pageUrl = "";           // 分页URL模板
    private String pageCount = "";         // 页数公式
    private String pageFlag = "";          // 分页标识
    private String pageFrom = "1";         // 分页起始页
    private String pageTo = "";            // 分页结束页
    private String pageCountRule = "";     // 页数提取规则
    private String listRule = "";          // 列表选择器（首页/分类通用）
    private String contentRule = "";       // 内容规则
    private String descRule = "";          // 简介规则
    private String picRule = "";           // 图片规则
    private String titleRule = "";         // 标题规则
    private String idRule = "";            // ID规则
    private String pageSizeRule = "";
    private int maxResultSize = 200;
    private String errorCodes = "";
    private String failCodes = "";
    private String successCodes = "";
    private boolean enableSniffer = true;
    private String snifferUrl = "";
    private String sniffPattern = "";
    private boolean isQuickSearch = false;
    private String searchClass = "";
    private String searchResult = "";
    private int searchIndex = 0;
    private int searchCount = 0;
    private String parseJsonPath = "";
    private String[] parseJsonPathList = null;
    private int[] parseIndexList = null;
    private boolean isFilter = true;
    private int filterMode = -1;

    // ==================== 实例字段 - 首页缓存 ====================
    private JSONObject homeJson = null;
    private String homeJsonStr = "";
    private JSONObject homeJsonObj = null;
    private JSONObject homeJsonArr = null;
    private boolean isHome = false;
    private boolean isDetail = false;
    private JSONArray homeVideoArr = null;
    private JSONArray searchResultArr = null;
    private JSONObject resultJson = new JSONObject();
    private String resultJsonStr = "";
    private List<Integer> resultIndexList = new ArrayList<>();
    private final Random random = new Random();
    private String resultStr = "";
    private String detailContentStr = "";
    private String detailContentObj = "";
    private String playerResult = "";
    private String searchId = "";
    private String playerSource = "";
    private boolean isSearch = false;
    private String extendText = ""; // 原始 extend 配置串（Smali 实例字段 اۣ۟，用于手动嗅探 x 标记判断）
    private String colorStr = "";   // 图标颜色（Smali 实例字段 اۦۨ，1~2 位数字或 a-b 色段）
    private final ArrayList<Integer> colorPool = new ArrayList<>(); // 随机颜色号池（Smali اۦ۪）
    private String iconColor = "";  // 收藏图标色后缀（Smali 实例字段 اۭ，拼在 vod_style 之后）
    private int lastResponseCode = 200;   // 最近一次请求响应码（Smali 实例字段 ا۟ۨ，错误弹窗"访问失败: 码"使用）
    private boolean requestFailed = false; // 请求失败标志（Smali 实例字段 اۨ۫，触发 Init.show + 消息弹窗）
    private String failMessage = "";       // 请求失败消息（Smali 实例字段 اۭۨ）

    // ==================== 初始化 ====================

    @Override
    public void init(Context context) throws Exception {
        super.init(context);
    }

    @Override
    public void init(Context context, String extend) throws Exception {
        super.init(context);
        if (context != null) {
            XBPQ.context = context;
        }
        this.extendText = extend == null ? "" : extend;
        if (extend == null || extend.isEmpty()) return;
        try {
            String configStr = extend;
            if (extend.trim().startsWith("http")) {
                configStr = OkHttp.string(extend);
            }
            parseConfig(configStr);
        } catch (Exception e) {
            // key:value 格式兼容：非 JSON 时尝试解析
            try {
                JSONObject kvConfig = parseKeyValueConfig(configStr);
                if (kvConfig != null) {
                    parseConfig(kvConfig.toString());
                }
            } catch (Exception e2) {
                SpiderDebug.log("XBPQ init error: " + e.getMessage());
            }
        }
    }

    public void init(Context context, String extend, String type, Integer flag) throws Exception {
        init(context, extend);
    }

    // ==================== 配置解析 ====================

    /**
     * 解析 JSON 配置文件，设置所有运行时字段
     * 对应配置 Key：homeUrl, className, classUrl, url, searchUrl, title, pic,
     *               id, desc, content, parseUrl, parseHeader, parseUa, parseReferer,
     *               code, charset, timeout, mode, referer, playUrl, playerJson,
     *               js, jx, ext, isJson 等
     */
    private void parseConfig(String jsonStr) throws JSONException {
        if (jsonStr == null || jsonStr.isEmpty()) return;
        JSONObject config = new JSONObject(jsonStr);
        // ===== 元数据 =====
        siteName = config.optString("站名", "");
        author = config.optString("作者", "");
        charset = config.optString("编码", "");
        // ===== 基础配置（中文Key优先，英文fallback） =====
        homeUrl = config.optString("主页url", config.optString("homeUrl", homeUrl));
        className = config.optString("分类", config.optString("className", className));
        classUrl = config.optString("分类url", config.optString("classUrl", classUrl));
        url = config.optString("url", url);
        searchUrl = config.optString("搜索url", config.optString("searchUrl", searchUrl));
        title = config.optString("标题", config.optString("title", title));
        pic = config.optString("图片", config.optString("pic", pic));
        id = config.optString("链接", config.optString("id", id));
        desc = config.optString("简介", config.optString("desc", desc));
        content = config.optString("内容", config.optString("content", content));
        parseUrl = config.optString("解析url", config.optString("parseUrl", parseUrl));
        parseHeader = config.optString("解析请求头", config.optString("parseHeader", parseHeader));
        parseUa = config.optString("解析UA", config.optString("parseUa", parseUa));
        parseReferer = config.optString("解析Referer", config.optString("parseReferer", parseReferer));
        timeout = config.optString("timeout", timeout);
        mode = config.optInt("mode", mode);
        referer = config.optString("referer", referer);
        ua = config.optString("请求头", config.optString("ua", ua));
        isJson = config.optBoolean("isJson", isJson);
        // ===== 分类相关（中文Key优先） =====
        filterSwitch = config.optString("筛选", config.optString("filterSwitch", filterSwitch));
        typeFilter = config.optString("类型", config.optString("typeFilter", typeFilter));
        sortType = config.optString("排序", config.optString("sortType", sortType));
        sortOrder = config.optString("顺序", config.optString("sortOrder", sortOrder));
        classValue = config.optString("分类值", config.optString("classValue", classValue));
        // ===== 数组/选择器规则 =====
        arraySecondCut = config.optString("数组二次截取", config.optString("arraySecondCut",
                config.optString("数组", "")));
        // 兼容：如果只配了"数组"而没有"数组二次截取"，将"数组"值赋给 arraySecondCut（部分用法）
        String arrayRule = config.optString("数组", "");
        if (!arrayRule.isEmpty() && arraySecondCut.isEmpty()) {
            arraySecondCut = arrayRule;
        }
        // ===== 播放相关（中文Key优先） =====
        playJsonPath = config.optString("playJsonPath", playJsonPath);
        playTitle = config.optString("播放标题", config.optString("playTitle", playTitle));
        playLink = config.optString("播放链接", config.optString("playLink", playLink));
        lineArray = config.optString("线路数组", config.optString("lineArray", lineArray));
        lineTitle = config.optString("线路标题", config.optString("lineTitle", lineTitle));
        playArray = config.optString("播放数组", config.optString("playArray", playArray));
        playListTitle = config.optString("播放标题", config.optString("playListTitle", playListTitle));
        playLinkUrl = config.optString("播放链接", config.optString("playLinkUrl", playLinkUrl));
        playPrefix = config.optString("播放链接前缀", config.optString("playPrefix", playPrefix));
        searchArray = config.optString("搜索数组", config.optString("searchArray", searchArray));
        searchTitle = config.optString("搜索标题", config.optString("searchTitle", searchTitle));
        searchLink = config.optString("搜索链接", config.optString("searchLink", searchLink));
        searchPic = config.optString("搜索图片", config.optString("searchPic", searchPic));
        searchDesc = config.optString("搜索副标题", config.optString("searchDesc", searchDesc));
        searchSuffix = config.optString("搜索后缀", config.optString("searchSuffix", searchSuffix));
        playSecondCut = config.optString("播放二次截取", config.optString("playSecondCut", playSecondCut));
        lineSecondCut = config.optString("线路二次截取", config.optString("lineSecondCut", lineSecondCut));
        searchSecondCut = config.optString("搜索二次截取", config.optString("searchSecondCut", searchSecondCut));
        parseSourceBlacklist = config.optString("解析源码黑名单", config.optString("parseSourceBlacklist", parseSourceBlacklist));
        // ===== 布尔开关（中文Key优先） =====
        directPlay = "1".equals(config.optString("直接播放", String.valueOf(directPlay)))
                || config.optBoolean("directPlay", directPlay);
        noSniff = "1".equals(config.optString("免嗅", String.valueOf(noSniff)))
                || config.optBoolean("noSniff", noSniff);
        reverseOrder = "1".equals(config.optString("倒序", String.valueOf(reverseOrder)))
                || config.optBoolean("reverseOrder", reverseOrder);
        decodeHtmlEnabled = config.optBoolean("解码html", decodeHtmlEnabled);
        // ===== 搜索/分页 =====
        searchMode = config.optInt("搜索模式", config.optInt("searchMode", searchMode));
        startPage = config.optString("起始页", config.optString("startPage", startPage));
        firstPage = config.optInt("首页", config.optInt("firstPage", firstPage));
        redirectUrl = config.optString("跳转播放链接", config.optString("redirectUrl", redirectUrl));
        pageUrl = config.optString("分页url", config.optString("pageUrl", pageUrl));
        pageCount = config.optString("页数", config.optString("pageCount", pageCount));
        pageFlag = config.optString("分页标识", config.optString("pageFlag", pageFlag));
        pageFrom = config.optString("起始页码", config.optString("pageFrom", pageFrom));
        pageTo = config.optString("结束页码", config.optString("pageTo", pageTo));
        pageCountRule = config.optString("页数规则", config.optString("pageCountRule", pageCountRule));
        // ===== 列表规则 =====
        listRule = config.optString("列表数组", config.optString("listRule", listRule));
        contentRule = config.optString("内容", config.optString("contentRule", contentRule));
        descRule = config.optString("简介", config.optString("descRule", descRule));
        picRule = config.optString("图片", config.optString("picRule", picRule));
        titleRule = config.optString("标题", config.optString("titleRule", titleRule));
        idRule = config.optString("链接", config.optString("idRule", idRule));
        // ===== 高级配置 =====
        pageSizeRule = config.optString("页数规则", config.optString("pageSizeRule", pageSizeRule));
        maxResultSize = config.optInt("最大结果", config.optInt("maxResultSize", maxResultSize));
        errorCodes = config.optString("错误码", config.optString("errorCodes", errorCodes));
        failCodes = config.optString("失败码", config.optString("failCodes", failCodes));
        successCodes = config.optString("成功码", config.optString("successCodes", successCodes));
        isQuickSearch = config.optBoolean("快速搜索", config.optBoolean("isQuickSearch", isQuickSearch));
        searchClass = config.optString("搜索分类", config.optString("searchClass", searchClass));
        searchResult = config.optString("搜索结果", config.optString("searchResult", searchResult));
        searchIndex = config.optInt("搜索索引", config.optInt("searchIndex", searchIndex));
        searchCount = config.optInt("搜索计数", config.optInt("searchCount", searchCount));
        parseJsonPath = config.optString("解析路径", config.optString("parseJsonPath", parseJsonPath));
        // ===== 列表JSON规则 =====
        listStr = config.optString("列表路径", config.optString("listStr", listStr));
        headJson = config.optString("头部json", config.optString("headJson", headJson));
        bodyJson = config.optString("主体json", config.optString("bodyJson", bodyJson));
        addJson = config.optString("追加json", config.optString("addJson", addJson));
        exp = config.optString("表达式", config.optString("exp", exp));
        // ===== HTML处理 =====
        copyUrl = config.optString("复制链接", config.optString("copyUrl", copyUrl));
        encodeHtmlUrl = config.optString("编码html", config.optString("encodeHtmlUrl", encodeHtmlUrl));
        decodeHtml = config.optBoolean("解码html", decodeHtml);
        decodeHtmlUrl = config.optString("解码链接", config.optString("decodeHtmlUrl", decodeHtmlUrl));
        encodeHtml = config.optString("编码html内容", config.optString("encodeHtml", encodeHtml));
        // ===== 详情扩展字段 =====
        filmType = config.optString("影片类型", config.optString("filmType", filmType));
        filmYear = config.optString("影片年代", config.optString("filmYear", filmYear));
        filmArea = config.optString("影片地区", config.optString("filmArea", filmArea));
        filmStatus = config.optString("状态", config.optString("filmStatus", filmStatus));
        director = config.optString("导演", config.optString("director", director));
        actor = config.optString("主演", config.optString("actor", actor));
        // ===== 高级功能 =====
        sniffWords = config.optString("嗅探词", config.optString("sniffWords", sniffWords));
        filterWords = config.optString("过滤词", config.optString("filterWords", filterWords));
        releasePage = config.optString("发布页", config.optString("releasePage", releasePage));
        dynamicDomain = config.optString("域名-c", config.optString("dynamicDomain", dynamicDomain));
        originalUrlBak = config.optString("原始网址-b", config.optString("originalUrlBak", originalUrlBak));
        releaseSiteBak = config.optString("发布站-b", config.optString("releaseSiteBak", releaseSiteBak));
        overseasPermanent = config.optString("境外永久", config.optString("overseasPermanent", overseasPermanent));
        domainSuffix = config.optString("后缀", config.optString("domainSuffix", domainSuffix));
        urlPrefix = config.optString("前缀", config.optString("前缀", urlPrefix));
        fixedDirectUrl = config.optString("固定直链", config.optString("fixedDirectUrl", fixedDirectUrl));
        backupPath = config.optString("回家的路", config.optString("backupPath", backupPath));
        keyExtractRule = config.optString("key截取", config.optString("keyExtractRule", keyExtractRule));
        aesKey = config.optString("key", config.optString("aesKey", aesKey));
        aesIv = config.optString("iv", config.optString("aesIv", aesIv));
        secondaryCut = config.optString("二次截取", config.optString("secondaryCut", secondaryCut));
        secondClassDir = config.optString("二级目录", config.optString("secondClassDir", secondClassDir));
        secondClassId = config.optString("二级ID", config.optString("secondClassId", secondClassId));
        specialClassUrl = config.optString("特殊分类链接", config.optString("specialClassUrl", specialClassUrl));
        playRequestHeader = config.optString("播放请求头", config.optString("playRequestHeader", playRequestHeader));
        searchRequestHeader = config.optString("搜索请求头", config.optString("searchRequestHeader", searchRequestHeader));
        playSubtitle = config.optString("播放副标题", config.optString("playSubtitle", playSubtitle));
        // ===== playUrl JSON数组 =====
        playUrl = config.optJSONArray("playUrl");
        playerJson = config.optJSONObject("playerJson");
        js = config.optJSONObject("js");
        jx = config.optJSONObject("jx");
        ext = config.optJSONObject("ext");

        // 解析筛选条件
        parseFilterConfig(config);

        // ===== [新增] 初始化 variableMap =====
        variableMap.clear();
        variableMap.put("主页url", homeUrl);
        variableMap.put("站名", siteName);
        variableMap.put("作者", author);
        variableMap.put("分类", className);
        variableMap.put("分类url", classUrl);
        variableMap.put("搜索url", searchUrl);
        variableMap.put("二次截取", secondaryCut);

        // XBPQ.json 新增字段：分类页独立规则、壁纸、首页Logo
        classSecondCut = config.optString("分类二次截取", config.optString("classSecondCut", classSecondCut));
        classArray = config.optString("分类数组", config.optString("classArray", classArray));
        classTitle = config.optString("分类标题", config.optString("classTitle", classTitle));
        classId = config.optString("分类ID", config.optString("classId", classId));
        redirectParse = config.optString("跳转解析", config.optString("redirectParse", redirectParse));
        wallpaper = config.optString("wallpaper", wallpaper);
        homeLogo = config.optString("homeLogo", homeLogo);

        // ===== [新增] 直播相关配置 =====
        liveUrl = config.optString("直播url", config.optString("liveUrl", liveUrl));
        liveCmsUrl = config.optString("cms直播源", config.optString("liveCmsUrl", liveCmsUrl));
        jsonPlay = config.optString("播放json路径", config.optString("jsonPlay", jsonPlay));

        // ===== [新增] 封面/详情/简介URL模板 =====
        vod_pic_ensure = config.optString("确保封面", config.optString("vod_pic_ensure", vod_pic_ensure));
        detailUrl = config.optString("详情页规则", config.optString("detailUrl", detailUrl));
        descUrl = config.optString("简介页规则", config.optString("descUrl", descUrl));
        picUrl = config.optString("图片url", config.optString("picUrl", picUrl));
        titleUrl = config.optString("标题url", config.optString("titleUrl", titleUrl));
        idUrl = config.optString("链接url", config.optString("idUrl", idUrl));
        contentUrl = config.optString("内容url", config.optString("contentUrl", contentUrl));

        // ===== [新增] 解析类型 =====
        parseType = config.optString("解析类型", config.optString("parseType", parseType));
        if (parseType.isEmpty()) parseType = "j"; // 默认JSON解析

        // ===== [新增] 播放规则（rule格式）=====
        rulePlayFrom = config.optString("线路来源规则", config.optString("rulePlayFrom", rulePlayFrom));
        rulePlayUrl = config.optString("播放URL规则", config.optString("rulePlayUrl", rulePlayUrl));
        ruleTitle = config.optString("播放标题规则", config.optString("ruleTitle", ruleTitle));
        ruleUrl = config.optString("播放链接规则", config.optString("ruleUrl", ruleUrl));
        rulePre = config.optString("播放前缀规则", config.optString("rulePre", rulePre));
        rulePost = config.optString("播放后缀规则", config.optString("rulePost", rulePost));
        ruleCover = config.optString("播放封面规则", config.optString("ruleCover", ruleCover));

        // ===== [新增] 详情字段规则 =====
        ruleContentType = config.optString("内容类型规则", config.optString("ruleContentType", ruleContentType));
        rulePlayState = config.optString("状态规则", config.optString("rulePlayState", rulePlayState));
        rulePlayYear = config.optString("年代规则", config.optString("rulePlayYear", rulePlayYear));
        rulePlayArea = config.optString("地区规则", config.optString("rulePlayArea", rulePlayArea));
        rulePlayActor = config.optString("主演规则", config.optString("rulePlayActor", rulePlayActor));
        rulePlayDirector = config.optString("导演规则", config.optString("rulePlayDirector", rulePlayDirector));
        rulePlayDesc = config.optString("简介规则", config.optString("rulePlayDesc", rulePlayDesc));
        rulePlayNote = config.optString("备注规则", config.optString("rulePlayNote", rulePlayNote));

        // ===== [新增] 列表规则 =====
        ruleListArray = config.optString("列表数组规则", config.optString("ruleListArray", ruleListArray));
        ruleListTitle = config.optString("列表标题规则", config.optString("ruleListTitle", ruleListTitle));
        ruleListId = config.optString("列表ID规则", config.optString("ruleListId", ruleListId));
        ruleListPic = config.optString("列表图片规则", config.optString("ruleListPic", ruleListPic));
        ruleListDesc = config.optString("列表简介规则", config.optString("ruleListDesc", ruleListDesc));

        // ===== [新增] 详情页规则 =====
        ruleDetailArray = config.optString("详情数组规则", config.optString("ruleDetailArray", ruleDetailArray));
        ruleDetailTitle = config.optString("详情标题规则", config.optString("ruleDetailTitle", ruleDetailTitle));
        ruleDetailId = config.optString("详情ID规则", config.optString("ruleDetailId", ruleDetailId));
        ruleDetailPic = config.optString("详情图片规则", config.optString("ruleDetailPic", ruleDetailPic));
        ruleDetailDesc = config.optString("详情简介规则", config.optString("ruleDetailDesc", ruleDetailDesc));
        ruleDetailContent = config.optString("详情内容规则", config.optString("ruleDetailContent", ruleDetailContent));

        // ===== [新增] 搜索规则 =====
        ruleSearchArray = config.optString("搜索数组规则", config.optString("ruleSearchArray", ruleSearchArray));
        ruleSearchTitle = config.optString("搜索标题规则", config.optString("ruleSearchTitle", ruleSearchTitle));
        ruleSearchId = config.optString("搜索ID规则", config.optString("ruleSearchId", ruleSearchId));
        ruleSearchPic = config.optString("搜索图片规则", config.optString("ruleSearchPic", ruleSearchPic));
        ruleSearchDesc = config.optString("搜索简介规则", config.optString("ruleSearchDesc", ruleSearchDesc));

        // ===== [新增] 缓存相关 =====
        enableCache = config.optBoolean("启用缓存", config.optBoolean("enableCache", enableCache));
        cacheTime = config.optInt("缓存时间", config.optInt("cacheTime", cacheTime));
        cacheKeyPrefix = config.optString("缓存键前缀", config.optString("cacheKeyPrefix", cacheKeyPrefix));
        resultKey = config.optString("结果键", config.optString("resultKey", resultKey));
        defaultClass = config.optString("默认分类", config.optString("defaultClass", defaultClass));
        defaultVodId = config.optString("默认视频ID", config.optString("defaultVodId", defaultVodId));
        homeContentKey = config.optString("首页内容键", config.optString("homeContentKey", homeContentKey));
        categoryContentKey = config.optString("分类内容键", config.optString("categoryContentKey", categoryContentKey));
        searchContentKey = config.optString("搜索内容键", config.optString("searchContentKey", searchContentKey));

        // 将 ">>" 分隔符统一替换为 "|"，便于后续 split
        if (!className.contains("|")) {
            className = className.replace(">>", "|");
        }
        if (!classUrl.contains("|")) {
            classUrl = classUrl.replace(">>", "|");
        }

        // ===== [新增] 静态字段赋值（用于 fixCover 等工具方法）=====
        baseEncodeUrl = config.optString("编码URL前缀", config.optString("baseEncodeUrl", baseEncodeUrl));
        imgFlag = config.optString("图片标志", config.optString("imgFlag", imgFlag));
        // 保存配置对象供其他方法使用
        configJson = config;
        // 应用已保存的偏好菜单选择状态（Smali اۧۦ：SSTop/offTempFilter 等开关生效）
        applyPrefMenu();
    }

    /**
     * 解析 key:value 格式的配置文件（非 JSON）
     * 支持格式：key1:value1,key2:value2,... （用 \, 转义逗号）
     */
    private JSONObject parseKeyValueConfig(String raw) throws JSONException {
        if (raw == null || raw.trim().isEmpty()) return new JSONObject();
        try {
            // 先尝试作为 JSON 解析（快速路径）
            if (raw.trim().startsWith("{")) {
                return new JSONObject(raw);
            }
            JSONObject result = new JSONObject();
            // 处理转义逗号 \, → 临时标记
            String processed = raw.replace("\\,", "\u0000");
            String[] pairs = processed.split(",");
            for (String pair : pairs) {
                pair = pair.trim();
                if (pair.isEmpty()) continue;
                int colonIdx = pair.indexOf(':');
                if (colonIdx < 0) continue;
                String key = pair.substring(0, colonIdx).trim().replace("\u0000", ",");
                String value = pair.substring(colonIdx + 1).trim().replace("\u0000", ",");
                result.put(key, value);
            }
            return result;
        } catch (Exception e) {
            SpiderDebug.log("parseKeyValueConfig error: " + e.getMessage());
            return new JSONObject();
        }
    }

    /**
     * 解析筛选条件配置
     * 支持格式：
     *   "filters": ["剧情:剧情:喜剧|爱情|剧情", "地区:地区:大陆|香港|台湾"]
     *   "filters": {"key":{"name":"key","value":[{"n":"v1"},{"n":"v2"}]}}
     */
    private void parseFilterConfig(JSONObject config) {
        try {
            Object filtersObj = config.opt("filters");
            if (filtersObj instanceof JSONArray) {
                JSONArray arr = (JSONArray) filtersObj;
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < arr.length(); i++) {
                    if (i > 0) sb.append("\n");
                    String item = arr.getString(i);
                    if (!item.contains(":") && !item.contains(",")) continue;
                    sb.append(item);
                }
                filterList = sb.toString();
            } else if (filtersObj instanceof JSONObject) {
                JSONObject jsonFilters = (JSONObject) filtersObj;
                Iterator<String> keys = jsonFilters.keys();
                StringBuilder sb = new StringBuilder();
                while (keys.hasNext()) {
                    String key = keys.next();
                    Object val = jsonFilters.get(key);
                    String name = key;
                    StringBuilder vals = new StringBuilder();
                    if (val instanceof JSONObject) {
                        JSONObject vObj = (JSONObject) val;
                        name = vObj.optString("name", key);
                        JSONArray vArr = vObj.optJSONArray("value");
                        if (vArr != null) {
                            for (int i = 0; i < vArr.length(); i++) {
                                JSONObject vo = vArr.optJSONObject(i);
                                String vn = vo != null ? vo.optString("n", "") : "";
                                if (i > 0) vals.append("|");
                                vals.append(vn);
                            }
                        }
                    }
                    sb.append(key).append(":").append(name).append(":").append(vals);
                    sb.append("\n");
                }
                filterList = sb.toString().trim();
            }
        } catch (Exception e) {
            SpiderDebug.log("parseFilterConfig error: " + e.getMessage());
        }
    }

    /**
     * [新增] 将 typeFilter/sortType 格式字符串解析为筛选条件加入 filterListJson
     * 格式："名称$值#名称$值"（用 # 分隔，$ 分隔 name/value），"||" 分隔分组
     */
    private void appendTypeFilter(JSONArray filterListJson, String defaultKey, String raw) throws JSONException {
        // 按 || 拆分组，每组用 # 分隔各项
        String[] groups = raw.split("\\|\\|");
        for (String group : groups) {
            if (group.trim().isEmpty()) continue;
            JSONObject filterObj = new JSONObject();
            filterObj.put("key", defaultKey);
            JSONArray vals = new JSONArray();
            for (String item : group.split("#")) {
                item = item.trim();
                if (item.isEmpty()) continue;
                String displayName = item;
                String value = "";
                int dollarIdx = item.indexOf('$');
                if (dollarIdx >= 0) {
                    displayName = item.substring(0, dollarIdx).trim();
                    value = item.substring(dollarIdx + 1).trim();
                }
                if (vals.length() > 0) vals.put("|");
                vals.put(displayName + "$" + value);
            }
            filterObj.put("name", defaultKey);
            filterObj.put("value", vals);
            filterListJson.put(filterObj);
        }
    }

    // ==================== 核心字符串处理器 ====================

    /**
     * 核心字符串处理器
     * 支持：Base64 解码、Hex 解码、GZIP 解压、CSS/JSONPath 选择器提取
     *
     * @param selector CSS 选择器或 JSONPath
     * @param data     待处理数据，以 "]" 分隔多个 segment
     * @param debug    是否输出调试日志
     * @return 处理结果
     */
    private String processString(String selector, String data, boolean debug) {
        if (selector == null) return "";
        if (data == null || data.isEmpty()) return selector;

        // 先解析变量引用 {{key}}
        data = resolveVariables(data);
        // 应用 || 条件选择器（按分类动态选择）
        data = applyOrSelector(data);
        // 应用后处理器 [替换:...] [包含:...] [不包含:...]
        data = applyPostProcessors(data);

        StringBuilder result = new StringBuilder();
        String[] segments = data.split("\\]");

        for (String segment : segments) {
            if (segment.isEmpty()) continue;
            try {
                // Base64 解码：以 "[" 开头
                if (segment.startsWith("[")) {
                    String decoded = new String(Base64.getDecoder().decode(segment.substring(1)), StandardCharsets.UTF_8);
                    result.append(decoded);
                    continue;
                }
                // Hex 解码：纯十六进制且长度为偶数
                if (segment.matches("^[0-9a-fA-F]+$") && segment.length() % 2 == 0) {
                    result.append(hexDecode(segment));
                    continue;
                }
                // GZIP 解压：以 "gzip:" 开头
                if (segment.startsWith("gzip:")) {
                    byte[] compressed = Base64.getDecoder().decode(segment.substring(5));
                    Inflater inflater = new Inflater();
                    inflater.setInput(compressed);
                    byte[] output = new byte[compressed.length * 2];
                    int decompressedLen = inflater.inflate(output);
                    result.append(new String(output, 0, decompressedLen));
                    continue;
                }
                // CSS/JSONPath 选择器提取（含 CSS 简写语法 p:tag->attr）
                if (!selector.isEmpty() && !selector.startsWith("#")) {
                    String extracted = extractBySelector(segment, selector);
                    if (!extracted.isEmpty()) {
                        result.append(extracted);
                        continue;
                    }
                }
                // 默认：直接追加原文
                result.append(segment);
            } catch (Exception e) {
                result.append(segment);
            }
        }
        return result.toString();
    }

    // ==================== 高级解析工具方法 ====================

    /**
     * 变量替换：将 {{key}} 替换为 variableMap 中的值，同时执行 [工具:...] 处理链
     */
    private String resolveVariables(String str) {
        if (str == null || str.isEmpty()) return str;
        // 先注入系统变量
        variableMap.put("主页url", homeUrl);
        variableMap.put("站名", siteName);
        variableMap.put("作者", author);
        // 递归替换 {{变量}}，直到稳定
        Pattern pattern = Pattern.compile("\\{\\{([^}]+)\\}\\}");
        for (int iter = 0; iter < 10; iter++) {
            Matcher matcher = pattern.matcher(str);
            if (!matcher.find()) break;
            StringBuffer sb = new StringBuffer();
            matcher.reset();
            while (matcher.find()) {
                String key = matcher.group(1).trim();
                String val = resolveToolChain(variableMap.getOrDefault(key, ""));
                matcher.appendReplacement(sb, Matcher.quoteReplacement(val));
            }
            matcher.appendTail(sb);
            str = sb.toString();
        }
        return str;
    }

    /**
     * 执行 [工具:xxx] 处理链，支持多级串联（# 分隔）
     * 格式示例：
     *   [工具:SHA]
     *   [工具:源码#截取:key:"&&"]
     *   [工具:源码#解密AES-{{key}}-{{iv}}-AES/CBC/PKCS7Padding]
     *   [工具:随机字符-3-唯一]
     *   [工具:1截取16]
     *   [工具:源码转b64]
     */
    private String resolveToolChain(String input) {
        if (input == null || !input.contains("[工具:")) return input;
        Pattern toolPattern = Pattern.compile("\\[工具:([^\\]]+)\\]");
        Matcher m = toolPattern.matcher(input);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String chain = m.group(1); // 如 "SHA" 或 "源码#截取:key:\"&&\""
            String result = processToolChainStep(input, chain);
            m.appendReplacement(sb, Matcher.quoteReplacement(result));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /**
     * 处理单个 [工具:xxx] 步骤（链式调用）
     * 支持 # 分隔多级操作
     */
    private String processToolChainStep(String input, String step) {
        String[] ops = step.split("#");
        String current = input;
        for (String op : ops) {
            op = op.trim();
            if (op.isEmpty()) continue;
            current = applyToolOp(current, op);
            if (current == null) return "";
        }
        return current;
    }

    /**
     * 应用单个工具操作
     */
    private String applyToolOp(String input, String op) {
        // [工具:SHA] 或 [工具:SHA256] → SHA-1/SHA-256 哈希
        if ("SHA".equals(op)) {
            return Crypto.sha1(input);
        }
        if ("SHA256".equals(op) || op.startsWith("SHA256")) {
            try {
                java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
                byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder();
                for (byte b : digest) {
                    sb.append(String.format("%02x", b));
                }
                return sb.toString();
            } catch (Exception e) {
                return input;
            }
        }
        // [工具:MD5] → MD5 哈希
        if ("MD5".equals(op) || op.startsWith("MD5")) {
            try {
                java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
                byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder();
                for (byte b : digest) {
                    sb.append(String.format("%02x", b));
                }
                return sb.toString();
            } catch (Exception e) {
                return input;
            }
        }
        // [工具:随机字符-N-唯一] 或 [工具:随机字符-N]
        if (op.startsWith("随机字符")) {
            int charLength = 8;
            if (op.contains("-")) {
                try { charLength = Integer.parseInt(op.split("-")[1].trim()); } catch (Exception ignored) {}
            }
            return Crypto.randomKey(charLength);
        }
        // [工具:1截取N] 或 [工具:截取N] → 取前 N 个字符
        if (op.matches("^\\d+截取\\d*$") || op.matches("^截取\\d+$")) {
            int start = 0, end = input.length();
            String[] parts = op.split("截取");
            try {
                if (!parts[0].isEmpty()) start = Integer.parseInt(parts[0].trim());
                if (parts.length > 1 && !parts[1].isEmpty()) end = start + Integer.parseInt(parts[1].trim());
            } catch (Exception ignored) {}
            end = Math.min(end, input.length());
            return input.substring(Math.min(start, input.length()), end);
        }
        // [工具:源码] 或 [工具:源码#截取:pattern] 或 [工具:源码转b64]
        if (op.startsWith("源码")) {
            // 源码模式：input 本身就是源内容
            String src = input;
            if (op.contains("#截取:")) {
                // 从 src 中截取匹配的内容
                int idx = op.indexOf("#截取:");
                String rule = op.substring(idx + 5);
                return applySourceExtract(src, rule);
            }
            if ("源码转b64".equals(op.trim()) || op.endsWith("转b64")) {
                return Util.base64Encode(src);
            }
            return src;
        }
        // [工具:URL编码] 或 [工具:URLEncode]
        if ("URL编码".equals(op) || "URLEncode".equals(op) || op.startsWith("URL编码")) {
            try {
                return URLEncoder.encode(input, "UTF-8");
            } catch (Exception e) {
                return input;
            }
        }
        // [工具:URL解码] 或 [工具:URLDecode]
        if ("URL解码".equals(op) || "URLDecode".equals(op) || op.startsWith("URL解码")) {
            try {
                return URLDecoder.decode(input, "UTF-8");
            } catch (Exception e) {
                return input;
            }
        }
        // [工具:Base64编码] 或 [工具:Base64Encode]
        if ("Base64编码".equals(op) || "Base64Encode".equals(op) || op.startsWith("Base64编码")) {
            return Util.base64Encode(input);
        }
        // [工具:Base64解码] 或 [工具:Base64Decode]
        if ("Base64解码".equals(op) || "Base64Decode".equals(op) || op.startsWith("Base64解码")) {
            return Util.base64Decode(input);
        }
        // [工具:解密AES-key-iv-模式] 或 [工具:解密AES-key-iv]
        if (op.startsWith("解密")) {
            // AES-CBC/PKCS7Padding 解密
            // 格式：解密AES-<base64密文>-<key>-<iv>-AES/CBC/PKCS7Padding
            // 或简化：解密-<key>-<iv>（input 本身是密文）
            String mode = "";
            String key = "", iv = "";
            if (op.contains("AES-")) {
                String[] segs = op.split("AES-");
                // segs[0] = "解密", segs[1] = 密文, 后续 = key-iv-模式
                if (segs.length >= 2) {
                    // 密文可能包含 {{变量}}，需先替换
                    String cipherText = resolveVariables(segs[1]);
                    if (segs.length >= 3) {
                        String rest = resolveVariables(segs[2]);
                        int lastDash = rest.lastIndexOf('-');
                        if (lastDash > 0) {
                            iv = rest.substring(0, lastDash);
                            // 检查是否有模式后缀
                            if (rest.length() > lastDash + 1) {
                                String suffix = rest.substring(lastDash + 1);
                                if (suffix.contains("/")) { mode = suffix; }
                                else { key = rest; }
                            } else { key = rest; }
                        } else { key = rest; }
                    }
                    if (mode.isEmpty() || mode.equals("PKCS7Padding")) mode = "PKCS5Padding";
                    try {
                        return Crypto.CBC(cipherText, key, iv);
                    } catch (Exception e) {
                        SpiderDebug.log("AES decrypt error: " + e.getMessage());
                        return "";
                    }
                }
            }
            // 简化模式：解密-<key>-<iv>，input 作为密文
            String[] parts = op.split("-");
            if (parts.length >= 3) {
                key = parts[1];
                iv = parts[2];
                try {
                    return Crypto.CBC(input, key, iv);
                } catch (Exception e) {
                    SpiderDebug.log("AES decrypt error: " + e.getMessage());
                    return "";
                }
            }
            return input;
        }
        // [工具:加密AES-key-iv-模式]
        if (op.startsWith("加密")) {
            String[] parts = op.split("-");
            if (parts.length >= 3) {
                String key = parts[1];
                String iv = parts[2];
                try {
                    return Crypto.aesEncrypt(input, key, iv);
                } catch (Exception e) {
                    SpiderDebug.log("AES encrypt error: " + e.getMessage());
                    return "";
                }
            }
            return input;
        }
        return input;
    }

    /**
     * 从源内容中按规则截取（用于 [工具:源码#截取:pattern]）
     * pattern 格式："key:\"&&\"" 表示取 key:"值" 中的值
     */
    private String applySourceExtract(String src, String rule) {
        if (src == null || src.isEmpty()) return "";
        // 尝试简单文本截取 key:"&&" 模式
        int startIdx = rule.indexOf(":\"");
        if (startIdx >= 0) {
            String prefix = rule.substring(0, startIdx + 2); // 如 key:"
            // 用 simple indexOf 截取
            int si = src.indexOf(prefix);
            if (si >= 0) {
                si += prefix.length();
                int ei = src.indexOf("\"", si);
                if (ei > si) return src.substring(si, ei);
            }
        }
        // 回退到通用正则截取
        try {
            java.util.regex.Pattern p = java.util.regex.Pattern.compile(rule);
            java.util.regex.Matcher m = p.matcher(src);
            if (m.find()) return m.group(1);
        } catch (Exception ignored) {}
        return src;
    }

    /**
     * 应用 || 条件选择器：根据当前分类/动作动态选取配置
     * 格式："selector1||selector2||selector3"
     * 按顺序返回第一个非空结果
     */
    private String applyOrSelector(String data) {
        if (data == null || !data.contains("||")) return data;
        String[] parts = data.split("\\|\\|");
        for (String part : parts) {
            if (part.isEmpty()) continue;
            String resolved = resolveVariables(part.trim());
            if (!resolved.isEmpty()) return resolved;
        }
        return data;
    }

    /**
     * 应用后处理器：[替换:a>>b] [包含:关键词] [不包含:关键词] [排序:a>>b]
     */
    private String applyPostProcessors(String str) {
        if (str == null || str.isEmpty()) return str;
        Pattern procPattern = Pattern.compile("\\[(替换|包含|不包含|排序):([^\\]]+)\\]");
        Matcher m = procPattern.matcher(str);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String type = m.group(1);
            String param = m.group(2);
            if ("替换".equals(type)) {
                String[] kv = param.split(">>");
                if (kv.length == 2) {
                    str = str.replace(kv[0].trim(), kv[1].trim());
                }
            } else if ("包含".equals(type)) {
                str = str.contains(param.trim()) ? str : "";
            } else if ("不包含".equals(type)) {
                str = str.contains(param.trim()) ? "" : str;
            } else if ("排序".equals(type)) {
                List<String> sorted = sortList(splitByDelimiter(str));
                StringBuilder sortedSb = new StringBuilder();
                for (int j = 0; j < sorted.size(); j++) {
                    if (j > 0) sortedSb.append("|");
                    sortedSb.append(sorted.get(j));
                }
                str = sortedSb.toString();
            }
            m.appendReplacement(sb, "");
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /**
     * 按管道符分割字符串并返回列表
     */
    private List<String> splitByDelimiter(String str) {
        if (str == null || str.isEmpty()) return new ArrayList<>();
        return Arrays.asList(str.split("\\|"));
    }

    /**
     * 应用二次截取规则
     * 格式："前缀&&后缀"，可带 [[index]] 指定返回第 N 块
     * 对应 Smali 中 ۧۦۤ 方法（index 参数）
     */
    private String applySecondCut(String content, String cutRule, int index) {
        if (content == null || content.isEmpty() || cutRule == null || cutRule.isEmpty()) return content;
        String[] parts = cutRule.split("&&");
        if (parts.length == 0) return content;
        // 处理 [[N]] 索引标记
        int blockIndex = 0;
        if (parts.length > 0 && parts[0].contains("[[")) {
            try {
                blockIndex = Integer.parseInt(parts[0].replaceAll("\\[\\[|\\]\\]", ""));
                parts[0] = "";
            } catch (NumberFormatException ignored) {}
        }
        int start = 0;
        if (!parts[0].isEmpty()) {
            start = content.indexOf(parts[0]);
            if (start < 0) return content;
            start += parts[0].length();
        }
        if (parts.length >= 2 && !parts[1].isEmpty()) {
            int end = content.indexOf(parts[1], start);
            if (end < 0) return content.substring(start);
            return content.substring(start, end).trim();
        }
        String result = content.substring(start).trim();
        // 按 && 再切割，取第 blockIndex 块
        if (blockIndex > 0) {
            String[] blocks = result.split("&&");
            if (blockIndex < blocks.length) return blocks[blockIndex].trim();
            return result;
        }
        return result;
    }

    /**
     * 应用二次截取规则（无索引版本，兼容旧调用）
     */
    private String applySecondCut(String content, String cutRule) {
        return applySecondCut(content, cutRule, 0);
    }

    /**
     * CSS 简写语法解析：p:div[class*="xxx"]->text  或  p:div->attr
     * 返回 Jsoup CSS 选择器字符串
     */
    private String parseCssShortSyntax(String selector) {
        if (selector == null || !selector.startsWith("p:")) return selector;
        String cssExpr = selector.substring(2); // 去掉 "p:"
        if (!cssExpr.contains("->")) return cssExpr;
        String[] parts = cssExpr.split("->");
        String tagPart = parts[0].trim();
        String attrPart = parts.length > 1 ? parts[1].trim() : "";
        // tagPart 可能是 "div.class" 或 "div[attr*=val]"
        if (attrPart.equals("text")) {
            return tagPart; // Jsoup select 直接返回文本
        } else if (attrPart.equals("href") || attrPart.equals("src")) {
            return tagPart + "[" + attrPart + "]";
        } else if (!attrPart.isEmpty()) {
            return tagPart;
        }
        return tagPart;
    }

    /**
     * 从配置中提取单条过滤条件，支持 [替换:x>>y] 后处理器
     */
    private String processFilterItem(String item) {
        if (item == null) return "";
        // 移除后处理器标记，但执行替换
        return applyPostProcessors(item);
    }

    // ==================== 工具方法 ====================

    /**
     * 十六进制字符串解码
     */
    private String hexDecode(String hex) {
        if (hex == null || hex.length() % 2 != 0) return "";
        byte[] bytes = new byte[hex.length() / 2];
        for (int i = 0; i < hex.length(); i += 2) {
            bytes[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                                 | Character.digit(hex.charAt(i + 1), 16));
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /**
     * [补充] JSON 转义字符解码（对应 Smali 中的 أۧۦ 方法）
     * 将反斜杠uXXXX格式的 Unicode 转义序列转换为实际字符
     * 并移除多余的换行符和反斜杠
     */
    private static String hexEscapeDecode(String input) {
        if (input == null || input.isEmpty()) return input;
        try {
            // 匹配反斜杠uXXXX 格式的 Unicode 转义序列
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\\\u([0-9A-Fa-f]{4})");
            java.util.regex.Matcher matcher = pattern.matcher(input);
            StringBuilder sb = new StringBuilder();
            while (matcher.find()) {
                String hex = matcher.group(1);
                char c = (char) Integer.parseInt(hex, 16);
                matcher.appendReplacement(sb, Matcher.quoteReplacement(String.valueOf(c)));
            }
            matcher.appendTail(sb);
            String result = sb.toString();
            // 替换 \r\n 为空（移除多余换行）
            result = result.replace("\r\n", "");
            // 移除多余的连续反斜杠（非引号前的）
            result = result.replaceAll("\\\\\\+(?!\")", "");
            return result.trim();
        } catch (Exception e) {
            SpiderDebug.log("hexEscapeDecode error: " + e.getMessage());
            return input;
        }
    }

    /**
     * [补充] 远程视频 URL 处理（对应 Smali 中的 أۧۧ 方法）
     * 检测播放 URL 中的 "请求头(...)" 标记，提取请求头定义存入缓存，
     * 经 getRV 处理后更新缓存，并从 URL 中移除该标记
     */
    private static String remoteVideoUrlProcess(String input) {
        if (input == null || input.isEmpty()) return input;
        try {
            // 检测 "请求头(" 标记（Smali 解密后的真实标记文本）
            if (input.contains("请求头(")) {
                // 提取标记内的请求头定义（正则 ".*请求头\((.*?)\).*"，对应 Smali 解密常量）
                java.util.regex.Matcher m = java.util.regex.Pattern
                        .compile(".*请求头\\((.*?)\\).*", java.util.regex.Pattern.DOTALL)
                        .matcher(input);
                String headerDefine = m.find() ? m.group(1) : "";
                // 存入播放请求头缓存
                playHeaderCache = headerDefine;
                // 经 getRV 处理（远程取值）后更新缓存
                String rv = getRV(headerDefine);
                if (rv != null && !rv.isEmpty()) {
                    playHeaderCache = rv;
                }
                // 移除 URL 中的 "请求头(...)" 标记
                input = input.replaceAll("请求头\\(.*?\\)", "");
            }
            return input;
        } catch (Exception e) {
            SpiderDebug.log("remoteVideoUrlProcess error: " + e.getMessage());
            return input;
        }
    }

    /**
     * [补充] 十六进制转义字符解码（对应 Smali 中的 ا۪۟ 方法）
     * 将反斜杠x## 或反斜杠u#### 格式的十六进制转义转换为十进制数值字符串
     */
    private static String hexEscapeToDecimal(String input) {
        if (input == null || input.isEmpty()) return input;
        try {
            // 匹配反斜杠x## 或反斜杠u#### 格式（解密后为十六进制转义串相关模式）
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\\\([xX][0-9A-Fa-f]{2}|u[0-9A-Fa-f]{4})");
            java.util.regex.Matcher matcher = pattern.matcher(input);
            StringBuffer sb = new StringBuffer();
            while (matcher.find()) {
                String escaped = matcher.group(1);
                String hexPart = escaped.substring(1); // 去掉 \ 前缀
                long value = Long.parseLong(hexPart, 16);
                matcher.appendReplacement(sb, Matcher.quoteReplacement(String.valueOf(value)));
            }
            matcher.appendTail(sb);
            return sb.toString();
        } catch (Exception e) {
            SpiderDebug.log("hexEscapeToDecimal error: " + e.getMessage());
            return input;
        }
    }

    /**
     * [补充] URL 编码处理（对应 Smali 中的 اۣۭ 方法）
     * 对包含特定标记的行进行 URL 编码，用于处理加密的播放地址
     */
    private static String urlEncodeProcess(String lines, String defaultUrl) {
        if (lines == null || lines.isEmpty()) return lines;
        try {
            StringBuilder result = new StringBuilder();
            String[] sep = {"اۦۥ", "اۥ"}; // 分隔符
            java.io.StringReader sr = new java.io.StringReader(lines);
            java.io.BufferedReader br = new java.io.BufferedReader(sr);
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                // 跳过以特定标记开头的行
                if (line.startsWith("اۦۨ")) continue;
                // 检查是否包含需要编码的标记
                if (line.contains("أۦا۟ۨا۟۟")) {
                    StringBuilder sb = new StringBuilder();
                    sb.append(line);
                    sb.append("إۨإۣاۣۥاۦاۧۨۥاۧۨۧا۟ۨا۟۟اۧۨۥاۧۨۧ");
                    sb.append(defaultUrl);
                    sb.append("اۥۣاۣۧا۟ۥاۨۨاۦ");
                    String encoded = sb.toString();
                    try {
                        encoded = java.net.URLEncoder.encode(encoded, "UTF-8");
                    } catch (Exception e) {
                        SpiderDebug.log("urlEncodeProcess encode error: " + e.getMessage());
                    }
                    result.append(encoded).append("\n");
                } else {
                    result.append(line).append("\n");
                }
            }
            br.close();
            return result.toString().trim();
        } catch (Exception e) {
            SpiderDebug.log("urlEncodeProcess error: " + e.getMessage());
            return lines;
        }
    }

    /**
     * [补充] 视频 URL 验证（对应 Smali 中的 أۣ۟ 方法）
     * 验证 URL 是否为有效的视频地址（非错误页、非空）
     */
    private boolean isVideoUrl(String url) {
        if (url == null || url.isEmpty()) return false;
        // 检查是否包含错误标记（解密后为 "اۥۣ" 或 "اۥۣأۧ"）
        String errorPrefix = "اۥۣ";
        String errorFull = "اۥۣأۧ";
        if (url.indexOf(errorPrefix) >= 0 && url.indexOf(errorFull) < 0) {
            return false;
        }
        return url.length() > 1;
    }

    /**
     * 通过 CSS 选择器提取单个文本（第一个匹配元素）
     */
    private String extractBySelector(String html, String selector) {
        if (Util.isEmpty(html) || Util.isEmpty(selector)) return "";
        try {
            // 支持 && 二次截取（对应 Smali 中 selector 含 "&&" 时的文本截取模式）
            if (selector.contains("&&")) {
                return applySecondCut(html, selector);
            }
            Document doc = Jsoup.parse(html);
            Elements elements = doc.select(selector);
            if (!elements.isEmpty()) {
                return elements.first().text().trim();
            }
        } catch (Exception e) {
            SpiderDebug.log("extractBySelector error: " + e.getMessage());
        }
        return "";
    }

    /**
     * 通过 CSS 选择器提取所有匹配文本，返回列表
     */
    private List<String> extractBySelectorAll(String html, String selector) {
        List<String> list = new ArrayList<>();
        if (Util.isEmpty(html) || Util.isEmpty(selector)) return list;
        try {
            // 支持 && 二次截取
            if (selector.contains("&&")) {
                String cutResult = applySecondCut(html, selector);
                if (!cutResult.isEmpty()) list.add(cutResult);
                return list;
            }
            Document doc = Jsoup.parse(html);
            Elements elements = doc.select(selector);
            for (Element el : elements) {
                list.add(el.text().trim());
            }
        } catch (Exception e) {
            SpiderDebug.log("extractBySelectorAll error: " + e.getMessage());
        }
        return list;
    }

    /**
     * 解析分类页（使用独立的 classArray/classTitle/classId 规则）
     * 用于 XBPQ.json 中 "分类数组"/"分类标题"/"分类ID" 格式
     */
    private String parseCategoryFromSeparateRules(String html, String arrayRule, String titleRule, String idRule) {
        try {
            JSONArray categories = new JSONArray();
            List<String> titles = extractBySelectorAll(html, parseCssShortSyntax(titleRule));
            List<String> ids = extractBySelectorAll(html, parseCssShortSyntax(idRule));
            // 如果数组规则非空，先按数组截取再提取
            String extractHtml = html;
            if (!arrayRule.isEmpty()) {
                extractHtml = applySecondCut(html, arrayRule);
            }
            List<String> titles2 = extractBySelectorAll(extractHtml, parseCssShortSyntax(titleRule));
            List<String> ids2 = extractBySelectorAll(extractHtml, parseCssShortSyntax(idRule));
            // 优先使用截取后的结果，若为空则回退到原 html
            if (titles2.size() > titles.size()) {
                titles = titles2;
                ids = ids2;
            }
            int count = Math.min(titles.size(), ids.size());
            for (int i = 0; i < count; i++) {
                JSONObject cls = new JSONObject();
                cls.put("type_id", ids.get(i).trim());
                cls.put("type_name", titles.get(i).trim());
                categories.put(cls);
            }
            // 确保首页分类存在
            JSONObject first = new JSONObject();
            first.put("type_id", "");
            first.put("type_name", "首页");
            if (categories.length() == 0) {
                categories.put(first);
            } else if (!categories.getJSONObject(0).getString("type_name").equals("首页")) {
                categories.put(0, first);
            }
            JSONObject result = new JSONObject();
            result.put("class", categories);
            return result.toString();
        } catch (Exception e) {
            SpiderDebug.log("parseCategoryFromSeparateRules error: " + e.getMessage());
            return "{\"class\":[]}";
        }
    }

    /**
     * 清理字符串中的干扰标记 [.*?] 和 ￥.*?￥
     */
    public static String clan(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.replaceAll("\\[.*?\\]", "").replaceAll("￥.*?￥", "");
    }

    /**
     * 获取页面内容（静态工具方法）
     */
    public static String getBL(String path) {
        singleton.lazyInit();
        if (singleton.homeUrl.isEmpty()) return "";
        String fullUrl = path.startsWith("http") ? path : Util.repairUrl(singleton.homeUrl, path);
        try {
            return OkHttp.string(fullUrl, singleton.buildHeaders());
        } catch (Exception e) {
            SpiderDebug.log("getBL error: " + e.getMessage());
            return "";
        }
    }

    /**
     * 获取配置组合字符串
     */
    public static String getCom() {
        return singleton.homeUrl;
    }

    /**
     * 获取 Android Context
     */
    public static Context getContext() {
        return context != null ? context.getApplicationContext() : Init.context();
    }

    /**
     * 获取请求结果
     */
    public static String getRV(String str) {
        singleton.lazyInit();
        if (singleton.homeUrl.isEmpty()) return "";
        return singleton.processString("", str, false);
    }

    /**
     * 获取 XBPQ 单例
     */
    public static XBPQ getXbpq() {
        return singleton;
    }

    /**
     * 存储 SpiderApi 引用及派生信息
     * 对应 Smali 私有方法 ا۪(SpiderApi)：
     *   spiderApiRef = api;
     *   apiPort = api.getPort(); Init.port = apiPort;
     *   screenOrientation = api.getScreenOrientation();
     *   apiAddress = api.getAddress(false);
     */
    private void storeSpiderApi(com.github.catvod.crawler.SpiderApi api) {
        spiderApiRef = api;
        apiPort = api.getPort();
        Init.port = apiPort;
        screenOrientation = api.getScreenOrientation();
        apiAddress = api.getAddress(false);
    }

    /**
     * 初始化 SpiderApi 接口
     * 对应 Smali 中 initApi(SpiderApi) 方法
     */
    public void initApi(com.github.catvod.crawler.SpiderApi spiderApi) {
        storeSpiderApi(spiderApi);
    }

    /**
     * 初始化 SpiderApi 接口并解析配置
     * 对应 Smali 中 initApi(SpiderApi, String) 方法：
     *   try { configJson = new JSONObject(extend); storeSpiderApi(api); return true; }
     *   catch { return false; }
     *
     * @return true 表示初始化成功
     */
    public boolean initApi(com.github.catvod.crawler.SpiderApi spiderApi, String extend) {
        try {
            configJson = new JSONObject(extend);
            storeSpiderApi(spiderApi);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 代理请求入口（委托给 Proxy.proxy）
     * 对应 Smali 中 mProxy(Map) 方法
     */
    public Object[] mProxy(Map<String, String> map) {
        try {
            return com.github.catvod.spider.Proxy.proxy(map);
        } catch (Exception e) {
            SpiderDebug.log("mProxy error: " + e.getMessage());
            return null;
        }
    }

    /**
     * 检查错误码是否在失败列表中
     */
    public static boolean isFail(int code) {
        singleton.lazyInit();
        if (singleton.failCodes.isEmpty()) return false;
        String codeStr = String.valueOf(code);
        for (String failCode : singleton.failCodes.split(",")) {
            if (codeStr.equals(failCode.trim())) return true;
        }
        return false;
    }

    /**
     * 单参数字符串切割（委托给三参数版本）
     */
    public static String jsCut(String str) {
        return jsCut("", str, "");
    }

    /**
     * 双参数字符串切割
     */
    public static String jsCut(String str, String str2) {
        return jsCut(str, str2, "");
    }

    /**
     * 三参数字符串切割：从 data 中用 before/after 截取中间部分
     */
    public static String jsCut(String before, String data, String after) {
        singleton.lazyInit();
        if (singleton.homeUrl.isEmpty()) return "";
        if (before.isEmpty()) before = singleton.parseJsonPath;
        int start = before.isEmpty() ? 0 : data.indexOf(before);
        if (start < 0) return "";
        start += before.length();
        if (!after.isEmpty()) {
            int end = data.indexOf(after, start);
            if (end < 0) return "";
            return data.substring(start, end).trim();
        }
        return data.substring(start).trim();
    }

    /**
     * JSON 数组转 XML 格式（弹幕兼容）
     */
    public static String jsonArray2xml(String str) {
        if (str == null || str.isEmpty()) return "";
        try {
            JSONArray array = new JSONObject(str).optJSONArray("list");
            if (array == null) return "";
            StringBuilder sb = new StringBuilder();
            sb.append("<?xml version=\"1.0\" encoding=\"utf-8\"?><i>\n");
            for (int i = 0; i < array.length(); i++) {
                JSONArray item = array.getJSONArray(i);
                if (item.length() < 5) continue;
                sb.append("<d p=\"").append(item.getString(0))
                  .append(",").append(item.getInt(1))
                  .append(",").append(item.getInt(2))
                  .append(",").append(item.getInt(3))
                  .append(",").append(item.getInt(4))
                  .append(",0,").append(item.getInt(6))
                  .append("\">").append(item.getString(5)).append("</d>\n");
            }
            sb.append("</i>");
            return sb.toString();
        } catch (Exception e) {
            SpiderDebug.log("jsonArray2xml error: " + e.getMessage());
            return "";
        }
    }

    /**
     * 加载弹幕数据
     * 返回 Object[]{timeout, contentType, inputStream}
     */
    public static Object[] loadDanmu(Map<String, String> map) {
        try {
            String danmuUrl = map.get("danmuUrl");
            if (danmuUrl == null || danmuUrl.isEmpty()) return null;
            danmuUrl = java.net.URLDecoder.decode(danmuUrl, "UTF-8");
            Map<String, String> headers = new LinkedHashMap<>();
            headers.put("User-Agent", defaultUa.isEmpty() ? Util.randomUA() : defaultUa);
            String resp = OkHttp.string(danmuUrl, headers);
            String xml = jsonArray2xml(resp);
            if (xml.isEmpty()) xml = resp;
            return new Object[]{200, "application/xml", new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))};
        } catch (Exception e) {
            SpiderDebug.log("loadDanmu error: " + e.getMessage());
            return null;
        }
    }

    /**
     * 解析 M3U8 直播流
     * 返回 Object[]{statusCode, contentType, inputStream}
     */
    public static Object[] loadM3u8(Map<String, String> map) {
        try {
            String m3u8Url = map.get("url");
            String baseUrl = map.get("base");
            if (m3u8Url == null || m3u8Url.isEmpty()) return null;
            m3u8Url = java.net.URLDecoder.decode(m3u8Url, "UTF-8");
            String content = OkHttp.string(m3u8Url, null);
            if (content == null || content.isEmpty()) return null;
            // 处理相对路径
            if (!content.contains("#EXTINF")) {
                return new Object[]{200, "application/vnd.apple.mpegurl",
                        new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8))};
            }
            StringBuilder result = new StringBuilder();
            String[] lines = content.split("\n");
            String resolvedBase = baseUrl != null ? baseUrl : Util.extractDomain(m3u8Url) + "/";
            for (String line : lines) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    result.append(line).append("\n");
                } else if (!line.startsWith("http")) {
                    result.append(Util.repairUrl(resolvedBase, line)).append("\n");
                } else {
                    result.append(line).append("\n");
                }
            }
            return new Object[]{200, "application/vnd.apple.mpegurl",
                    new ByteArrayInputStream(result.toString().getBytes(StandardCharsets.UTF_8))};
        } catch (Exception e) {
            SpiderDebug.log("loadM3u8 error: " + e.getMessage());
            return null;
        }
    }

    /**
     * 加载图片资源
     * 返回 Object[]{statusCode, contentType, inputStream}
     */
    public static Object[] loadPic(Map<String, String> map) {
        try {
            String picUrl = map.get("url");
            String referer = map.get("referer");
            if (picUrl == null || picUrl.isEmpty()) return null;
            picUrl = java.net.URLDecoder.decode(picUrl, "UTF-8");
            Map<String, String> headers = new LinkedHashMap<>();
            if (!referer.isEmpty()) headers.put("Referer", referer);
            byte[] data = OkHttp.bytes(picUrl, headers);
            String contentType = "image/jpeg";
            if (picUrl.toLowerCase().endsWith(".png")) contentType = "image/png";
            else if (picUrl.toLowerCase().endsWith(".gif")) contentType = "image/gif";
            else if (picUrl.toLowerCase().endsWith(".webp")) contentType = "image/webp";
            return new Object[]{200, contentType, data};
        } catch (Exception e) {
            SpiderDebug.log("loadPic error: " + e.getMessage());
            return null;
        }
    }

    /**
     * 日志输出（受 debug 开关控制）
     */
    public static void log(String str) {
        log(debug, str);
    }

    /**
     * 条件日志输出
     */
    public static void log(boolean enabled, String str) {
        if (!enabled) return;
        SpiderDebug.log(str);
    }

    /**
     * 渲染嗅探：判断 URL 是否为有效视频源
     *
     * @param url      目标 URL
     * @param pattern  嗅探模式（逗号分隔的关键字）
     * @return true 表示匹配
     */
    public static boolean renderSniff(String url, String pattern) {
        if (url == null || url.isEmpty() || pattern == null || pattern.isEmpty()) return false;
        String[] patterns = pattern.split(",");
        String lowerUrl = url.toLowerCase();
        for (String p : patterns) {
            p = p.trim();
            if (p.isEmpty()) continue;
            if (lowerUrl.contains(p)) return true;
        }
        // 检查视频格式
        if (Util.isVideoFormat(url)) return true;
        return false;
    }

    /**
     * 设置页面缓存
     */
    public static void setBL(String path, String content) {
        if (singleton == null) return;
        singleton.homeUrl = content;
    }

    /**
     * 字符串处理工具
     */
    public static String strPro(String str, String str2, int i) {
        if (singleton == null) return "";
        return singleton.processString(str, str2, false);
    }

    /**
     * 通用工具方法（2参数版）
     */
    public static String tool(String str, String str2) {
        return tool(str, str2, true);
    }

    /**
     * 通用工具方法（3参数版，带条件处理）
     */
    public static String tool(String str, String str2, boolean condition) {
        if (!condition) return str;
        return str2;
    }

    // ==================== [新增] 新增工具方法 ====================

    /**
     * 修复视频封面URL，处理编码和特殊标记
     */
    // [新增]
    public static String fixCover(String str, String str2) {
        if (str == null || str.isEmpty()) return "";
        int idx = str.indexOf("$$");
        if (idx > 0) str = str.substring(0, idx);
        // str2 可用于额外处理（如添加 imgFlag），此处兼容 Smali 接口
        // 处理 imgFlag 和 secretKey 编码
        if (!XBPQ.imgFlag.isEmpty() && !XBPQ.secretKey.isEmpty()) {
            try {
                String encoded = java.net.URLEncoder.encode(str, "UTF-8");
                return XBPQ.imgFlag + encoded;
            } catch (Exception e) {
                return str;
            }
        }
        return str;
    }

    /**
     * 多规则字符串提取
     * 支持多个选择器按优先级依次尝试，返回第一个非空结果
     */
    // [新增]
    private String multiRuleExtract(String html, String... selectors) {
        if (html == null || html.isEmpty()) return "";
        for (String selector : selectors) {
            if (selector == null || selector.isEmpty()) continue;
            // 先尝试 CSS 选择器
            String cssResult = extractBySelector(html, parseCssShortSyntax(selector));
            if (!cssResult.isEmpty()) return cssResult;
            // 再尝试 JSON 路径
            if (selector.contains(".")) {
                try {
                    JSONObject obj = new JSONObject(html);
                    String[] parts = selector.split("\\.");
                    for (String part : parts) {
                        obj = obj.optJSONObject(part);
                        if (obj == null) break;
                    }
                    if (obj != null) {
                        String val = obj.optString("value", obj.optString("data", obj.toString()));
                        if (!val.isEmpty()) return val;
                    }
                } catch (Exception ignored) {}
            }
        }
        return "";
    }

    /**
     * 双规则字符串提取
     * 先按 before/after 截取，再从结果中提取指定规则
     */
    // [新增]
    private String twoRuleExtract(String html, String beforeRule, String afterRule, String extractRule) {
        if (html == null || html.isEmpty()) return "";
        // 第一步：二次截取
        String cutResult = applySecondCut(html, beforeRule + "&&" + afterRule);
        // 第二步：用 extractRule 提取
        return singleRuleExtract(cutResult, extractRule);
    }

    /**
     * 三规则字符串提取
     * 先按 rule1/rule2 截取，再从结果中按 rule3 提取
     */
    // [新增]
    private String threeRuleExtract(String html, String rule1, String rule2, String rule3) {
        if (html == null || html.isEmpty()) return "";
        // 第一步：按 rule1/rule2 二次截取
        String cutResult = applySecondCut(html, rule1 + "&&" + rule2);
        // 第二步：按 rule3 提取
        return singleRuleExtract(cutResult, rule3);
    }

    /**
     * 六规则字符串提取
     * 支持两级二次截取：先用 rule1/rule2 截取大段，再从结果中用 rule3/rule4 截取小段，最后用 rule5/rule6 提取
     */
    // [新增]
    private String sixRuleExtract(String html, String rule1, String rule2,
                                  String rule3, String rule4,
                                  String rule5, String rule6) {
        if (html == null || html.isEmpty()) return "";
        // 第一级截取
        String level1 = applySecondCut(html, rule1 + "&&" + rule2);
        // 第二级截取
        String level2 = applySecondCut(level1, rule3 + "&&" + rule4);
        // 第三级提取
        return singleRuleExtract(level2, rule5 + "&&" + rule6);
    }

    /**
     * [新增] 对 HTML 内容执行解码处理（如 URL decode、JS 反转义等）
     */
    // [新增]
    private String decodeHtmlContent(String html) {
        if (html == null || html.isEmpty()) return html;
        try {
            // URL 解码
            String decoded = java.net.URLDecoder.decode(html, "UTF-8");
            if (!decoded.equals(html)) return decoded;
            // 常见 HTML 实体解码
            decoded = decoded.replace("&amp;", "&")
                    .replace("&lt;", "<")
                    .replace("&gt;", ">")
                    .replace("&quot;", "\"")
                    .replace("&#39;", "'")
                    .replace("&#x27;", "'");
            return decoded;
        } catch (Exception e) {
            SpiderDebug.log("decodeHtmlContent error: " + e.getMessage());
            return html;
        }
    }

    /**
     * [新增] 对内容执行编码处理（编码成 HTML 后再请求）
     */
    // [新增]
    private String encodeHtmlContent(String content) {
        if (content == null || content.isEmpty()) return content;
        try {
            // URL 编码
            String encoded = URLEncoder.encode(content, "UTF-8");
            // 如果配置了 encodeHtml，对其进行处理
            if (!encodeHtml.isEmpty()) {
                encoded = processString(encodeHtml, encoded, isDebug);
            }
            return encoded;
        } catch (Exception e) {
            SpiderDebug.log("encodeHtmlContent error: " + e.getMessage());
            return content;
        }
    }

    /**
     * [新增] 判断字符串是否为合法的 JSON 数组（兼容处理）
     */
    // [新增]
    public static boolean isJsonArrayEqual(String str) {
        if (str == null || str.isEmpty()) return false;
        String trimmed = str.trim();
        return trimmed.startsWith("[") && trimmed.endsWith("]");
    }

    /**
     * 单规则字符串提取
     */
    // [新增]
    private String singleRuleExtract(String html, String rule) {
        if (html == null || html.isEmpty() || rule == null || rule.isEmpty()) return "";
        // 尝试 CSS 选择器
        String cssResult = extractBySelector(html, parseCssShortSyntax(rule));
        if (!cssResult.isEmpty()) return cssResult;
        // 尝试二次截取
        if (rule.contains("&&")) {
            return applySecondCut(html, rule);
        }
        // 尝试正则
        try {
            java.util.regex.Pattern p = java.util.regex.Pattern.compile(rule);
            java.util.regex.Matcher m = p.matcher(html);
            if (m.find()) return m.group(1);
        } catch (Exception ignored) {}
        return "";
    }

    /**
     * 统计 JSONObject 的键数量（私有方法）
     */
    private int countJsonKeys(JSONObject obj) {
        if (obj == null) return 0;
        try {
            return obj.keys().hasNext() ? obj.keySet().size() : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * 检查字符串是否为空（私有方法）
     */
    private boolean isStrEmpty(String str) {
        return str == null || str.trim().isEmpty();
    }

    /**
     * 将字符串保存到 SharedPreferences（私有方法）
     */
    private void saveToPrefs(String key, String value) {
        try {
            android.preference.PreferenceManager.getDefaultSharedPreferences(getContext())
                    .edit().putString("xbpq_" + key, value).apply();
        } catch (Exception e) {
            SpiderDebug.log("saveToPrefs error: " + e.getMessage());
        }
    }

    /**
     * 字符串处理：移除 HTML 标签、去除多余空白，返回干净文本（私有方法）
     */
    private String cleanHtmlText(String str) {
        if (str == null || str.isEmpty()) return "";
        // 移除 HTML 标签
        String result = str.replaceAll("<[^>]+>", "");
        // 解码 HTML 实体
        result = result.replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&apos;", "'");
        // 压缩空白
        result = result.replaceAll("\\s+", " ").trim();
        return result;
    }

    /**
     * 处理 URL 并提取内容（多规则选择器 + URL 拼接）
     */
    private String extractUrlContent(String str) {
        if (str == null || str.isEmpty()) return "";
        try {
            // 1. 如果配置了 encodeHtmlUrl，先请求编码后的页面再处理
            String html = str;
            if (!encodeHtmlUrl.isEmpty()) {
                String encoded = fetchContent(encodeHtmlUrl);
                if (!encoded.isEmpty() && !encodeHtml.isEmpty()) {
                    html = processString(encodeHtml, encoded, isDebug);
                }
            }
            // 2. 多规则选择器提取（调用 multiRuleExtract）
            String url = multiRuleExtract(html, str);
            // 3. 拼接基础 URL
            if (!url.startsWith("http")) {
                url = Util.repairUrl(homeUrl, url);
            }
            return url;
        } catch (Exception e) {
            SpiderDebug.log("extractUrlContent error: " + e.getMessage());
            return str;
        }
    }

    /**
     * 验证 URL 格式有效性（私有方法）
     */
    private boolean isValidUrl(String str) {
        if (str == null || str.isEmpty()) return false;
        try {
            java.net.URL url = new java.net.URL(str);
            String protocol = url.getProtocol();
            return "http".equalsIgnoreCase(protocol) || "https".equalsIgnoreCase(protocol)
                    || "file".equalsIgnoreCase(protocol) || "m3u8".equalsIgnoreCase(protocol);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 多规则字符串提取（别名方法）
     * 支持多个选择器按优先级依次尝试，返回第一个非空结果
     */
    private String multiExtract(String html, String... selectors) {
        return multiRuleExtract(html, selectors);
    }

    /**
     * 单规则字符串提取（别名方法）
     */
    private String singleExtract(String html, String rule) {
        return singleRuleExtract(html, rule);
    }

    /**
     * 双规则字符串提取（别名方法）
     */
    private String twoExtract(String html, String beforeRule, String afterRule, String extractRule) {
        return twoRuleExtract(html, beforeRule, afterRule, extractRule);
    }

    /**
     * 三规则字符串提取（别名方法）
     */
    private String threeExtract(String html, String rule1, String rule2, String rule3) {
        return threeRuleExtract(html, rule1, rule2, rule3);
    }

    /**
     * 六规则字符串提取（别名方法）
     */
    private String sixExtract(String html, String rule1, String rule2,
                        String rule3, String rule4,
                        String rule5, String rule6) {
        return sixRuleExtract(html, rule1, rule2, rule3, rule4, rule5, rule6);
    }

    /**
     * [新增] 获取配置信息
     * 返回当前配置的关键字段
     */
    public String config() {
        try {
            JSONObject cfg = new JSONObject();
            cfg.put("homeUrl", homeUrl);
            cfg.put("siteName", siteName);
            cfg.put("className", className);
            cfg.put("classUrl", classUrl);
            cfg.put("searchUrl", searchUrl);
            cfg.put("parseUrl", parseUrl);
            cfg.put("isJson", isJson);
            cfg.put("mode", mode);
            cfg.put("ua", ua);
            cfg.put("referer", referer);
            return cfg.toString();
        } catch (Exception e) {
            SpiderDebug.log("config error: " + e.getMessage());
            return "{}";
        }
    }

    /**
     * [新增] 获取缓存内容
     * 支持操作：get/set/clear，格式为 JSON {"op":"set","key":"xxx","value":"yyy"}
     */
    public String cache(String op, String key, String value) {
        try {
            if ("set".equals(op) && key != null) {
                android.preference.PreferenceManager.getDefaultSharedPreferences(getContext())
                        .edit().putString(singletonKey + "_" + key, value).apply();
                return "{\"ok\":true}";
            }
            if ("get".equals(op) && key != null) {
                String result = android.preference.PreferenceManager.getDefaultSharedPreferences(getContext())
                        .getString(singletonKey + "_" + key, "");
                return "{\"value\":\"" + result.replace("\"", "\\\"") + "\"}";
            }
            if ("clear".equals(op)) {
                android.preference.PreferenceManager.getDefaultSharedPreferences(getContext())
                        .edit().remove(singletonKey + "_category").apply();
                android.preference.PreferenceManager.getDefaultSharedPreferences(getContext())
                        .edit().remove(singletonKey + "_home").apply();
                android.preference.PreferenceManager.getDefaultSharedPreferences(getContext())
                        .edit().remove(singletonKey + "_search").apply();
                android.preference.PreferenceManager.getDefaultSharedPreferences(getContext())
                        .edit().remove(singletonKey + "_detail").apply();
                android.preference.PreferenceManager.getDefaultSharedPreferences(getContext())
                        .edit().remove(singletonKey + "_play").apply();
                return "{\"ok\":true}";
            }
        } catch (Exception e) {
            SpiderDebug.log("cache error: " + e.getMessage());
        }
        return "{}";
    }

    /**
     * [新增] 获取直播内容
     * 返回 JSON：{"list":[...]}
     */
    @Override
    public String liveContent(String url) throws Exception {
        if (url.isEmpty() && liveUrl.isEmpty()) return "{\"list\":[]}";
        return loadLiveContent(url);
    }

    /**
     * 解析二级目录和ID（用于分类页的独立规则）
     */
    // [新增]
    private void parseSecondClass(String html, com.github.catvod.bean.Vod vod) {
        try {
            if (!secondClassDir.isEmpty()) {
                String dir = extractBySelector(html, parseCssShortSyntax(secondClassDir));
                if (!dir.isEmpty()) vod.setVodArea(dir);
            }
            if (!secondClassId.isEmpty()) {
                String sid = extractBySelector(html, parseCssShortSyntax(secondClassId));
                if (!sid.isEmpty()) vod.setVodId(sid);
            }
        } catch (Exception e) {
            SpiderDebug.log("parseSecondClass error: " + e.getMessage());
        }
    }

    /**
     * 获取并处理编码后的HTML内容
     */
    // [新增]
    private String fetchEncodedContent(String baseUrl) {
        try {
            String html = fetchContent(baseUrl);
            if (html.isEmpty()) return "";
            // 如果配置了解码开关，尝试解码
            if (decodeHtml && !decodeHtmlUrl.isEmpty()) {
                String decoded = fetchContent(decodeHtmlUrl.replace("{url}", html));
                if (!decoded.isEmpty()) return decoded;
            }
            // 如果配置了编码后处理
            if (!encodeHtml.isEmpty()) {
                return processString(encodeHtml, html, isDebug);
            }
            return html;
        } catch (Exception e) {
            SpiderDebug.log("fetchEncodedContent error: " + e.getMessage());
            return "";
        }
    }

    /**
     * 解析首页视频数组（支持独立数组规则）
     */
    // [新增]
    private List<com.github.catvod.bean.Vod> parseHomeVodListFromJson(String html) {
        try {
            if (html.isEmpty()) return new ArrayList<>();
            String extracted = html;
            // 应用数组二次截取
            if (!arraySecondCut.isEmpty()) {
                extracted = applySecondCut(extracted, arraySecondCut);
            }
            // 如果有独立首页视频数组规则
            if (!listStr.isEmpty()) {
                // 尝试从 JSON 中提取
                if (isJsonMode(extracted)) {
                    JSONObject obj = new JSONObject(extracted);
                    JSONArray arr = findJsonArray(obj, listStr);
                    if (arr != null && arr.length() > 0) {
                        return parseJsonArrayToVodList(arr);
                    }
                }
            }
            // 兜底：用 title 选择器提取
            return parseHomeVodList(extracted);
        } catch (Exception e) {
            SpiderDebug.log("parseHomeVodListFromJson error: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * 递归在 JSON 对象中查找指定路径的数组
     */
    // [新增]
    private JSONArray findJsonArray(JSONObject obj, String path) {
        if (obj == null || path == null) return null;
        String[] parts = path.split("[/.]");
        for (String part : parts) {
            if (part.isEmpty()) continue;
            if (obj.has(part)) {
                Object val = obj.opt(part);
                if (val instanceof JSONArray) return (JSONArray) val;
                if (val instanceof JSONObject) obj = (JSONObject) val;
                else return null;
            } else {
                // 尝试所有子节点
                Iterator<String> keys = obj.keys();
                while (keys.hasNext()) {
                    String entryKey = keys.next();
                    Object entryValue = obj.opt(entryKey);
                    if (entryValue instanceof JSONObject) {
                        JSONArray result = findJsonArray((JSONObject) entryValue, part);
                        if (result != null) return result;
                    }
                }
                return null;
            }
        }
        return null;
    }

    /**
     * 将 JSONArray 转换为 Vod 列表
     */
    // [新增]
    private List<com.github.catvod.bean.Vod> parseJsonArrayToVodList(JSONArray array) {
        List<com.github.catvod.bean.Vod> list = new ArrayList<>();
        try {
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.getJSONObject(i);
                com.github.catvod.bean.Vod vod = new com.github.catvod.bean.Vod();
                vod.setVodId(item.optString("id", item.optString("vod_id", "")));
                vod.setVodName(item.optString("name", item.optString("vod_name", "")));
                vod.setVodPic(item.optString("pic", item.optString("vod_pic", "")));
                vod.setVodRemarks(item.optString("remarks", ""));
                vod.setVodContent(item.optString("content", ""));
                list.add(vod);
            }
        } catch (Exception e) {
            SpiderDebug.log("parseJsonArrayToVodList error: " + e.getMessage());
        }
        return list;
    }

    /**
     * 获取搜索结果缓存
     */
    // [新增]
    public String getSearchResult() {
        return searchResult;
    }

    /**
     * 设置搜索结果缓存
     */
    // [新增]
    public void setSearchResult(String result) {
        this.searchResult = result;
    }

    /**
     * 获取搜索标题
     */
    // [新增]
    public String getSearchTitle() {
        return searchTitle;
    }

    /**
     * 处理动态域名替换
     */
    // [新增]
    private String processDynamicDomain(String url) {
        if (dynamicDomain == null || dynamicDomain.isEmpty()) return url;
        // 替换域名
        try {
            java.net.URL base = new java.net.URL(homeUrl);
            java.net.URL dyn = new java.net.URL(dynamicDomain);
            String path = base.getPath();
            String query = base.getQuery();
            String result = dyn.getProtocol() + "://" + dyn.getHost();
            if (dyn.getPort() > 0) result += ":" + dyn.getPort();
            result += path;
            if (!query.isEmpty()) result += "?" + query;
            return result;
        } catch (Exception e) {
            return url;
        }
    }

    // ==================== 网络请求辅助 ====================

    /**
     * 构建默认请求头
     * 支持"请求头"字段（User-Agent$xxx#Referer$yyy 格式）和"播放请求头"、"搜索请求头"
     */
    private Map<String, String> buildHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", ua.isEmpty() ? Util.randomUA() : ua);
        headers.put("Accept", "*/*");
        headers.put("Accept-Language", "zh-CN,zh;q=0.9");
        if (!referer.isEmpty()) headers.put("Referer", referer);
        // 解析"请求头"字段（支持 User-Agent$...#Referer$... 格式）
        parseCustomHeaders(headers, ua);
        return headers;
    }

    /**
     * 构建播放请求头（支持"播放请求头"字段）
     */
    private Map<String, String> buildPlayHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", parseUa.isEmpty() ? Util.randomUA() : parseUa);
        if (!parseReferer.isEmpty()) headers.put("Referer", parseReferer);
        if (!parseHeader.isEmpty()) headers.put("Content-Type", parseHeader);
        // 同时支持"播放请求头"字段
        parseCustomHeaders(headers, playRequestHeader);
        // 合并播放 URL 中 "请求头(...)" 标记提取的动态缓存（对应 Smali 静态字段 ا۪ۦ）
        if (!playHeaderCache.isEmpty()) {
            parseCustomHeaders(headers, playHeaderCache);
        }
        return headers;
    }

    /**
     * 构建搜索请求头（支持"搜索请求头"字段）
     */
    private Map<String, String> buildSearchHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", ua.isEmpty() ? Util.randomUA() : ua);
        parseCustomHeaders(headers, searchRequestHeader);
        return headers;
    }

    /**
     * 解析自定义请求头字符串
     * 支持格式：Key$Value#Key2$Value2 或 Key$Value@Key2$Value2
     */
    private void parseCustomHeaders(Map<String, String> headers, String headerStr) {
        if (headerStr == null || headerStr.isEmpty()) return;
        // 统一将 @ 替换为 #，作为对分隔符
        String normalized = headerStr.replace('@', '#');
        String[] pairs = normalized.split("#");
        for (String pair : pairs) {
            pair = pair.trim();
            if (pair.isEmpty()) continue;
            int idx = pair.indexOf('$');
            if (idx <= 0) continue;
            String key = pair.substring(0, idx).trim();
            String value = pair.substring(idx + 1).trim();
            if (!key.isEmpty() && !value.isEmpty()) {
                headers.put(key, value);
            }
        }
    }

    /**
     * 拼接完整 URL
     */
    private String buildUrl(String base, String relative) {
        if (relative == null || relative.isEmpty()) return base;
        if (relative.startsWith("http")) return relative;
        return Util.repairUrl(base, relative);
    }

    /**
     * 获取网页内容
     */
    private String fetchContent(String targetUrl) {
        try {
            // 应用变量模板替换（支持 {{主页url}}、{{域名-c}} 等）
            String resolvedUrl = resolveVariables(targetUrl);
            Map<String, String> headers = buildHeaders();
            String body;
            if (mode == 1) {
                body = OkHttp.post(resolvedUrl, "", headers);
            } else {
                body = OkHttp.string(resolvedUrl, headers);
            }
            // 请求成功：清除失败标志（Smali getMovieList 每次请求前重置 اۨ۫/اۭۨ）
            requestFailed = false;
            failMessage = "";
            lastResponseCode = 200;
            return body;
        } catch (Exception e) {
            SpiderDebug.log("fetchContent error: " + e.getMessage());
            // 请求失败：记录错误状态（Smali اۨ۫/اۭۨ，供 Init.show 与消息弹窗使用）
            requestFailed = true;
            failMessage = e.getMessage() == null ? "请求异常" : e.getMessage();
            lastResponseCode = 0;
            return "";
        }
    }

    /**
     * 判断是否为 JSON 模式（HTML 以 { 或 [ 开头）
     */
    private boolean isJsonMode(String html) {
        if (html == null) return false;
        String trimmed = html.trim();
        return trimmed.startsWith("{") || trimmed.startsWith("[");
    }

    /**
     * 多 URL 详情获取（对应 Smali multiReq اۭۥ，92444-92669）：
     * 输入含 $$$ 时按段并行抓取（每段 10s 超时），结果顺序拼接；
     * 拼接结果以 ||| 开头则去掉前缀；单段/无分隔时直接抓取。
     */
    private String fetchDetailMulti(String urls) {
        try {
            if (urls == null || urls.indexOf("$$$") < 1) return fetchContent(urls);
            java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newCachedThreadPool();
            List<java.util.concurrent.Future<String>> futures = new ArrayList<>();
            for (String u : urls.split("\\$\\$\\$")) {
                futures.add(pool.submit(() -> fetchContent(u)));
            }
            StringBuilder sb = new StringBuilder();
            for (java.util.concurrent.Future<String> f : futures) {
                try {
                    sb.append(f.get(10, java.util.concurrent.TimeUnit.SECONDS));
                } catch (Exception ignored) {
                }
            }
            pool.shutdown();
            String result = sb.toString();
            if (result.startsWith("|||")) result = result.substring(3);
            return result;
        } catch (Exception e) {
            SpiderDebug.log("multiReq()错误！-->" + e.getMessage());
            return "";
        }
    }

    // ==================== 业务方法 ====================

    /**
     * 获取首页内容
     * 返回 JSON：{"class":[...],"filters":[...],"list":[...]}
     */
    @Override
    public String homeContent(boolean filter) throws Exception {
        try {
            if (homeJson != null) return homeJson.toString();
            String html = fetchContent(homeUrl);
            // Smali homeContent 117045-117139：响应码非 200 或内容含"网站维护中"
            // → 返回消息弹窗 tab（msgbox），而非空列表
            if (requestFailed || html.isEmpty() || html.contains("网站维护中")) {
                return new JSONObject().put("list",
                        msgTabBuild(html, "访问失败: " + lastResponseCode)).toString();
            }

            JSONObject result = new JSONObject();

            // 提取分类（支持二次截取）
            JSONArray classList = new JSONArray();
            String rawClassData = html;
            if (!arraySecondCut.isEmpty()) {
                rawClassData = applySecondCut(html, arraySecondCut);
            }

            if (!className.isEmpty() && !classUrl.isEmpty()) {
                List<String> names = extractBySelectorAll(rawClassData, parseCssShortSyntax(className));
                List<String> urls = extractBySelectorAll(rawClassData, parseCssShortSyntax(classUrl));
                int count = Math.min(names.size(), urls.size());
                for (int i = 0; i < count; i++) {
                    JSONObject cls = new JSONObject();
                    String rawUrl = urls.get(i).trim();
                    // [新增] 正确处理 classUrl 为空的情况（用索引代替）
                    if (rawUrl.isEmpty() || rawUrl.equals("/")) {
                        rawUrl = String.valueOf(i);
                    } else {
                        // 去除主页URL前缀
                        rawUrl = rawUrl.replace(homeUrl, "").replace("http://", "").replace("https://", "");
                        rawUrl = rawUrl.startsWith("/") ? rawUrl.substring(1) : rawUrl;
                    }
                    cls.put("type_id", rawUrl);
                    cls.put("type_name", names.get(i).trim());
                    classList.put(cls);
                }
            } else if (!className.isEmpty()) {
                // 支持两种分隔符：| 或 >（传统格式）和 &（API 格式）
                String nameSep = className.contains("&") ? "&" : "[|>]";
                String urlSep = classUrl.contains("&") ? "&" : "[|>]";
                String[] nameArr = className.split(nameSep);
                // 分类值（classValue）支持 & 分隔，与 className 一一对应
                String[] urlArr;
                if (!classValue.isEmpty() && classValue.contains("&")) {
                    urlArr = classValue.split("&");
                } else if (!classUrl.isEmpty()) {
                    urlArr = classUrl.split(urlSep);
                } else {
                    urlArr = nameArr;
                }
                for (int i = 0; i < nameArr.length; i++) {
                    JSONObject cls = new JSONObject();
                    String tid = (i == 0) ? ""
                            : (urlArr.length > i && !urlArr[i].isEmpty()) ? urlArr[i] : nameArr[i].trim();
                    cls.put("type_id", tid);
                    cls.put("type_name", nameArr[i].trim());
                    classList.put(cls);
                }
            }

            // 倒序
            if (reverseOrder && classList.length() > 1) {
                JSONArray reversed = new JSONArray();
                for (int i = classList.length() - 1; i >= 0; i--) {
                    reversed.put(classList.get(i));
                }
                classList = reversed;
            }

            // 动态分类 Tab 注入（女神分类/女优分类/热搜分类/首页二级）
            if (gsCfg != null) {
                if ("1".equals(gsCfg.optString("女神二级"))) {
                    JSONObject女神Tab = new JSONObject();
                    女神Tab.put("type_id", "女神");
                    女神Tab.put("type_name", "女神分类");
                    classList.put(女神Tab);
                }
                if ("1".equals(gsCfg.optString("女优二级"))) {
                    JSONObject女优Tab = new JSONObject();
                    女优Tab.put("type_id", "女优");
                    女优Tab.put("type_name", "女优分类");
                    classList.put(女优Tab);
                }
                if ("1".equals(gsCfg.optString("热搜二级"))) {
                    JSONObject热搜Tab = new JSONObject();
                    热搜Tab.put("type_id", "热搜");
                    热搜Tab.put("type_name", "热搜分类");
                    classList.put(热搜Tab);
                }
            }

            // 源内功能 tab 注入（Smali homeContent 59230-59250：SSTop 开启时置顶，否则追加）
            JSONObject gsCfg2 = configJson == null ? new JSONObject() : configJson;
            classList = insertActionTabs(classList, gsCfg2.optBoolean("SSTop"));

            result.put("class", classList);

            // 提取筛选条件
            JSONArray filterListJson = new JSONArray();
            // [新增] 将 typeFilter 和 sortType 作为固定筛选条件加入（支持 "名称$值#名称$值" 格式）
            if (filter && !typeFilter.isEmpty()) {
                appendTypeFilter(filterListJson, "类型", typeFilter);
            }
            if (filter && !sortType.isEmpty()) {
                appendTypeFilter(filterListJson, "排序", sortType);
            }
            // 来自 filters 配置的筛选条件
            if (filter && filterList != null && !filterList.isEmpty()) {
                for (String f : filterList.split("\n")) {
                    f = f.trim();
                    if (f.isEmpty()) continue;
                    String[] parts = f.split(":");
                    JSONObject filterObj = new JSONObject();
                    filterObj.put("key", parts.length > 0 ? processFilterItem(parts[0].trim()) : "");
                    filterObj.put("name", parts.length > 1 ? processFilterItem(parts[1].trim()) : "");
                    JSONArray vals = new JSONArray();
                    if (parts.length > 2) {
                        for (String v : processFilterItem(parts[2]).split("\\|")) {
                            vals.put(v.trim());
                        }
                    }
                    filterObj.put("value", vals);
                    filterListJson.put(filterObj);
                }
            }
            if (filterListJson.length() > 0) {
                result.put("filters", filterListJson);
            }

            // 提取视频列表
            if (!title.isEmpty()) {
                List<com.github.catvod.bean.Vod> vodList;
                // [新增] 支持 listStr 独立数组规则
                if (!listStr.isEmpty()) {
                    vodList = parseHomeVodListFromJson(html);
                } else {
                    vodList = parseHomeVodList(rawClassData);
                }
                if (reverseOrder) {
                    java.util.Collections.reverse(vodList);
                }
                // firstPage 限制首页视频数量（当 firstPage > 0 且小于默认限制时生效）
                if (firstPage > 0 && firstPage < vodList.size()) {
                    vodList = vodList.subList(0, firstPage);
                }
                result.put("list", toVodJsonArray(vodList));
            }

            homeJson = result;
            return result.toString();
        } catch (Exception e) {
            SpiderDebug.log("homeContent error: " + e.getMessage());
            return "{\"class\":[],\"list\":[]}";
        }
    }

    /**
     * 获取首页视频内容（不带筛选）
     */
    @Override
    public String homeVideoContent() {
        try {
            return homeContent(homeJson == null);
        } catch (Exception e) {
            return "{}";
        }
    }

    /**
     * 获取分类页面内容
     */
    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        // ===== 源内分类特殊处理（Smali categoryContent 95676-96547 还原） =====
        // 1. 收藏夹分类：读取收藏列表（Smali 95676-95891）
        if ("收藏夹".equals(tid)) {
            if (!"1".equals(pg)) return "";
            try {
                String saved = readPref(singletonKey + "_collect");
                if (playerJson == null || saved == null || saved.length() <= 10) return "";
                JSONArray arr = new JSONArray(saved);
                if (arr.length() <= 0) return "";
                String custom = extend == null ? null : extend.get("custom");
                if (custom != null && custom.length() > 0) {
                    // 收藏夹内过滤：vod_name 包含关键词
                    JSONArray result = new JSONArray();
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject item = arr.getJSONObject(i);
                        if (item.optString("vod_name").indexOf(custom) >= 0) {
                            result.put(item);
                        }
                    }
                    return new JSONObject().put("list", result).toString();
                }
                return new JSONObject().put("list", arr).toString();
            } catch (Exception e) {
                SpiderDebug.log("collect category error: " + e.getMessage());
                return "";
            }
        }
        String categoryResult = "";
        // 2. extend custom 参数：纯数字→页码偏移，其他→执行搜索（Smali 96440-96507）
        String custom = extend == null ? null : extend.get("custom");
        if (custom != null && custom.length() > 0) {
            if (custom.matches("\\d+")) {
                try {
                    pg = String.valueOf(Integer.parseInt(custom) + Integer.parseInt(pg) - 1);
                } catch (NumberFormatException ignored) {
                }
            } else {
                categoryResult = search(custom, pg, false);
            }
        }
        // 3. 源内搜索 tid："源内搜索:关键词"（Smali 96520-96547，前缀长度 5）
        if (categoryResult.length() < 1 && tid != null && tid.startsWith("源内搜索:")) {
            categoryResult = search(tid.substring(5), pg, false);
        }
        if (categoryResult.length() >= 1) return categoryResult;

        // local 特殊处理：从缓存读取
        if ("local".equals(tid)) {
            try {
                String cached = android.preference.PreferenceManager.getDefaultSharedPreferences(getContext())
                        .getString(singletonKey + "_category", "");
                if (!cached.isEmpty()) {
                    JSONArray filtered = new JSONArray(cached);
                    String filterKey = extend != null ? extend.get("key") : null;
                    if (filterKey != null && !filterKey.isEmpty()) {
                        JSONArray result = new JSONArray();
                        for (int i = 0; i < filtered.length(); i++) {
                            JSONObject obj = filtered.getJSONObject(i);
                            String val = obj.optString(filterKey, "");
                            if (val.contains(extend.getOrDefault("value", ""))) {
                                result.put(obj);
                            }
                        }
                        return new JSONObject().put("list", result).toString();
                    }
                    return new JSONObject().put("list", filtered).toString();
                }
            } catch (Exception ignored) {}
            return "";
        }

        // 修正起始页
        int pageNum;
        try {
            pageNum = Integer.parseInt(pg);
            if (pageNum <= 0) pageNum = firstPage;
        } catch (NumberFormatException e) {
            pageNum = firstPage;
        }

        String classPath = resolveVariables(classUrl)
                .replace("{cateId}", tid)
                .replace("{catePg}", String.valueOf(pageNum))
                .replace("{cateid}", tid)
                .replace("{catepg}", String.valueOf(pageNum));
        // 解析 classUrl 中的 ;;模式 后缀（如 /music/id-{cateId}-{catePg}.html;;mrcRAD）
        // ;;后为模式标识，仅影响请求方式（暂保留，不影响实际请求逻辑）
        String modeSuffix = "";
        if (classPath.contains(";;")) {
            int semiIdx = classPath.indexOf(";;");
            modeSuffix = classPath.substring(semiIdx + 2).trim();
            classPath = classPath.substring(0, semiIdx).trim();
        }
        String html = (!encodeHtmlUrl.isEmpty()) ? fetchEncodedContent(buildUrl(homeUrl, classPath)) : fetchContent(buildUrl(homeUrl, classPath));
        // Smali أۦ 24556-24582 / getMovieList 62618-62653：请求失败 → Init.show 提示 + 消息弹窗 tab
        if (requestFailed) {
            Init.show(failMessage);
            return new JSONObject().put("list", msgTabBuild(html, failMessage)).toString();
        }
        // Smali أۥ۪ 58405-58435：内容含"网站维护中" → 消息弹窗 tab（网站维护中！）
        if (html.contains("网站维护中")) {
            return new JSONObject().put("list", msgTabBuild(html, "网站维护中！")).toString();
        }
        // Smali getMovieList 68307-68359：JSON 模式无内容 → 消息弹窗 tab（无法访问:码）
        if (html.isEmpty()) {
            return new JSONObject().put("list",
                    msgTabBuild(html, "无法访问:" + lastResponseCode)).toString();
        }

        // XBPQ.json 新增：分类二次截取 + 分类独立数组/标题/ID 规则
        if (!classSecondCut.isEmpty()) {
            html = applySecondCut(html, classSecondCut);
        }
        if (!classArray.isEmpty() && !classTitle.isEmpty() && !classId.isEmpty()) {
            return parseCategoryFromSeparateRules(html, classArray, classTitle, classId);
        }

        // [新增] 处理 extend 筛选参数（key/value 过滤）
        if (extend != null && !extend.isEmpty()) {
            String filterKey = extend.getOrDefault("key", "");
            String filterValue = extend.getOrDefault("value", "");
            if (!filterKey.isEmpty() && !filterValue.isEmpty()) {
                // 将筛选参数拼入 classPath（部分站点通过 URL 参数传递筛选条件）
                if (classPath.contains("?")) {
                    classPath = classPath + "&" + filterKey + "=" + filterValue;
                } else {
                    classPath = classPath + "?" + filterKey + "=" + filterValue;
                }
                // 重新请求
                html = (!encodeHtmlUrl.isEmpty()) ? fetchEncodedContent(buildUrl(homeUrl, classPath)) : fetchContent(buildUrl(homeUrl, classPath));
                if (html.isEmpty()) {
                    return new JSONObject().put("list",
                            msgTabBuild(html, "无法访问:" + lastResponseCode)).toString();
                }
                if (!classSecondCut.isEmpty()) {
                    html = applySecondCut(html, classSecondCut);
                }
            }
        }

        // 应用数组二次截取
        if (!arraySecondCut.isEmpty()) {
            html = applySecondCut(html, arraySecondCut);
        }

        List<com.github.catvod.bean.Vod> vodList;
        if (isJsonMode(html)) {
            vodList = parseJsonVodList(html);
        } else {
            vodList = parseHomeVodList(html);
        }

        // [新增] 使用 listRule 独立列表选择器（若配置则优先）
        if (vodList.isEmpty() && !listRule.isEmpty()) {
            String cssListRule = parseCssShortSyntax(listRule);
            vodList = parseHomeVodListFromSelector(html, cssListRule);
        }
        // [新增] 使用 ruleList* 系列规则解析列表（若配置则优先）
        if (vodList.isEmpty() && (!ruleListArray.isEmpty() || !ruleListTitle.isEmpty())) {
            vodList = parseListByRule(html);
        }
        // Smali أۦ 24380/24433 三级回退兜底：站点规则解析失败时依次按 <li&&</li、<div&&</div 标签抓取
        if (vodList.isEmpty() && !isJsonMode(html)) {
            vodList = parseHomeVodListFromSelector(html, "li");
        }
        if (vodList.isEmpty() && !isJsonMode(html)) {
            vodList = parseHomeVodListFromSelector(html, "div");
        }

        // [新增] 分页：从响应中提取总页数（pageCountRule 或 pageCount 公式）
        int totalPage = -1;
        if (!pageCountRule.isEmpty() && isJsonMode(html)) {
            totalPage = extractTotalPage(html);
        } else if (!pageCount.isEmpty()) {
            try {
                totalPage = Integer.parseInt(pageCount);
            } catch (NumberFormatException ignored) {}
        }

        // 倒序
        if (reverseOrder) {
            java.util.Collections.reverse(vodList);
        }

        // [新增] 构建分页信息
        JSONObject respObj = new JSONObject();
        JSONArray list = new JSONArray();
        for (com.github.catvod.bean.Vod vod : vodList) {
            list.put(toVodJson(vod));
        }
        respObj.put("list", list);
        if (totalPage > 0) {
            respObj.put("page", pageNum);
            respObj.put("pagecount", totalPage);
            respObj.put("limit", pageSize);
            respObj.put("total", vodList.size());
        }
        return respObj.toString();
    }

    /**
     * 获取视频详情内容（支持线路数组、播放数组、二次截取等完整解析）
     */
    @Override
    public String detailContent(List<String> ids) throws Exception {
        if (ids == null || ids.isEmpty()) return "[]";
        // [补充 Smali 97059-97098] json搜索 标记剥离（json 模式搜索结果携带）
        String rawId = ids.get(0);
        if (rawId.contains("json搜索")) {
            rawId = rawId.replace("json搜索", "");
            ids.set(0, rawId);
        }
        // [补充 Smali 97120-97256] $$$ 多段 id 解析与强制播放标记
        String[] segs = rawId.split("\\$\\$\\$");
        forcePlay = (segs.length > 3 && ("playDirect".equals(segs[3]) || segs[3].startsWith("shortVideo")))
                || extendText.contains("z"); // extendText 含 z → 直接播放配置生效
        // URL 段优先级：segs[2] > segs[1] > segs[0]（Smali 97280-97450）
        String idUrl = segs.length > 2 ? segs[2] : (segs.length > 1 ? segs[1] : segs[0]);
        if (idUrl.startsWith("/") && !idUrl.startsWith("//")) idUrl = homeUrl + idUrl;
        // [补充 Smali 97453-97513] activecate= 参数剥离
        int acIdx = idUrl.indexOf("activecate=");
        if (acIdx > 0) {
            if (!idUrl.endsWith("=")) {
                String[] acParts = idUrl.split("[\\?&]activecate=");
                if (acParts.length > 1) activeCate = acParts[1];
            }
            idUrl = idUrl.substring(0, acIdx);
        }
        // [补充 Smali 97519-97576] magnet/push/file 前缀直连
        if (!forcePlay && (idUrl.startsWith("magnet:") || idUrl.startsWith("push://") || idUrl.startsWith("file"))) {
            forcePlay = true;
        }
        String url = buildUrl(homeUrl, idUrl);
        // [补充 Smali 92444 multiReq] 多 URL（$$$ 分隔）并行获取拼接
        String html = (!encodeHtmlUrl.isEmpty()) ? fetchEncodedContent(url) : fetchDetailMulti(url);
        com.github.catvod.bean.Vod vod = new com.github.catvod.bean.Vod();
        vod.setVodId(rawId);

        if (isJsonMode(html)) {
            JSONObject obj = new JSONObject(html);
            vod.setVodName(obj.optString("name", obj.optString("vod_name", "")));
            vod.setVodPic(obj.optString("pic", obj.optString("vod_pic", "")));
            vod.setVodContent(obj.optString("content", ""));
            vod.setVodRemarks(obj.optString("remarks", ""));
            // JSON 模式也支持线路/播放解析
            parseJsonPlaySources(obj, vod);
        } else {
            Document doc = Jsoup.parse(html);
            if (!title.isEmpty()) {
                Element el = doc.selectFirst(title.contains(":") ? title : "h1,h2,h3");
                if (el != null) vod.setVodName(el.text().trim());
            }
            if (!pic.isEmpty()) {
                Element el = doc.selectFirst(pic);
                if (el != null) {
                    String picUrl = extractPicAttr(el);
                    // [补充] fixCover 调用（对应 Smali detailContent 中图片修复逻辑）
                    vod.setVodPic(fixCover(picUrl, homeUrl));
                }
            }
            if (!content.isEmpty()) {
                Element el = doc.selectFirst(content);
                if (el != null) vod.setVodContent(el.text().trim());
            }
            if (!desc.isEmpty()) {
                Element el = doc.selectFirst(desc);
                if (el != null) vod.setVodRemarks(el.text().trim());
            }
            // 详情扩展字段：影片类型/年代/地区/状态/导演/主演（支持 CSS 选择器或文本截取）
            parseDetailField(doc, filmType, vod::setTypeName);
            parseDetailField(doc, filmYear, vod::setVodYear);
            parseDetailField(doc, filmArea, vod::setVodArea);
            parseDetailField(doc, filmStatus, v -> vod.setVodRemarks(
                    v + (vod.getVodRemarks() != null ? " " + vod.getVodRemarks() : "")));
            parseDetailField(doc, director, vod::setVodDirector);
            parseDetailField(doc, actor, vod::setVodActor);
            // 解析线路和播放源（支持二次截取）
            parseHtmlPlaySources(html, doc, vod);
            // [新增] 支持 copyUrl 复制链接：将详情页中的链接替换为配置的值
            if (!copyUrl.isEmpty()) {
                try {
                    String copiedId = extractBySelector(doc.html(), parseCssShortSyntax(copyUrl));
                    if (!copiedId.isEmpty()) {
                        vod.setVodId(copiedId.trim());
                    }
                } catch (Exception ignored) {}
            }
        }

        return new JSONArray().put(toVodJson(vod)).toString();
    }

    /**
     * 解析详情页扩展字段（影片类型/年代/地区/状态/导演/主演/vod_play_url）
     * 支持 CSS 选择器格式（如 "导　　演&&<br>"）和纯文本截取格式
     */
    private void parseDetailField(Document doc, String selector, java.util.function.Consumer<String> setter) {
        if (selector == null || selector.isEmpty()) return;
        try {
            String text = "";
            // 尝试 CSS 选择器
            if (selector.contains("&&")) {
                // 文本截取格式：前缀&&后缀
                text = applySecondCut(doc.html(), selector);
            }
            // 尝试 Jsoup CSS 选择器
            if (text.isEmpty() && !selector.contains("&&")) {
                Element el = doc.selectFirst(selector);
                if (el != null) text = el.text().trim();
            }
            if (!text.isEmpty()) setter.accept(text);
        } catch (Exception ignored) {}
    }

    /**
     * [补充] fixCover - 修复封面图 URL（对应 Smali 中 fixCover 方法）
     * 处理相对路径、URL 编码、特殊协议等
     */
    private String fixCover(String picUrl, String referer) {
        if (picUrl == null || picUrl.isEmpty()) return "";
        try {
            // 处理 base64 数据 URI
            if (picUrl.startsWith("data:image")) {
                return picUrl;
            }
            // 处理相对路径
            if (picUrl.startsWith("//")) {
                return "https:" + picUrl;
            }
            if (picUrl.startsWith("/")) {
                return homeUrl + picUrl;
            }
            if (!picUrl.startsWith("http")) {
                return Util.repairUrl(homeUrl, picUrl);
            }
            return picUrl;
        } catch (Exception e) {
            SpiderDebug.log("fixCover error: " + e.getMessage());
            return picUrl;
        }
    }

    /**
     * 解析 HTML 详情页的线路和播放源
     * 支持：lineArray/lineTitle、playArray/playListTitle/playLinkUrl
     * 支持：lineSecondCut、playSecondCut
     */
    private void parseHtmlPlaySources(String rawHtml, Document doc, com.github.catvod.bean.Vod vod) {
        try {
            String playHtml = rawHtml;
            // 线路二次截取
            if (!lineSecondCut.isEmpty()) {
                playHtml = applySecondCut(playHtml, lineSecondCut);
            }

            // 解析线路数组
            JSONObject lineMap = new JSONObject();
            if (!lineArray.isEmpty()) {
                String cssLineArr = parseCssShortSyntax(lineArray);
                Elements lineEls = doc.select(cssLineArr);
                if (lineEls.isEmpty()) {
                    // 文本截取模式（如 "module-tab-item&&</div>"）
                    String lineBlock = applySecondCut(playHtml, lineArray.split("&&")[0] + "&&" + (lineArray.contains("&&") ? lineArray.split("&&")[1] : ""));
                    if (!lineBlock.isEmpty()) {
                        lineEls = Jsoup.parse(lineBlock).select(lineArray.contains("&&") ? lineArray.split("&&")[0] : "div");
                    }
                }
                // 提取线路标题
                List<String> lineNames = new ArrayList<>();
                if (!lineTitle.isEmpty()) {
                    String cssLineTitle = parseCssShortSyntax(lineTitle);
                    for (Element el : lineEls) {
                        Elements titleEls = el.select(cssLineTitle);
                        lineNames.add(titleEls.isEmpty() ? el.text().trim() : titleEls.first().text().trim());
                    }
                } else {
                    for (int i = 0; i < lineEls.size(); i++) {
                        lineNames.add("线路" + (i + 1));
                    }
                }

                // 解析每个线路的播放列表
                for (int i = 0; i < lineEls.size(); i++) {
                    String lineName = lineNames.size() > i ? lineNames.get(i) : "线路" + (i + 1);
                    Element lineEl = lineEls.get(i);
                    // 在当前线路块内查找播放列表
                    String epHtml = lineEl.outerHtml();
                    // 播放二次截取
                    if (!playSecondCut.isEmpty()) {
                        epHtml = applySecondCut(epHtml, playSecondCut);
                    }
                    // 解析播放链接
                    List<String> episodes = parsePlayEpisodes(epHtml, lineName);
                    if (!episodes.isEmpty()) {
                        lineMap.put(lineName, Util.join("#", episodes));
                    }
                }
            }

            // 兜底：如果 lineArray 未配置，从整页解析
            if (lineMap.length() == 0) {
                parseDefaultPlaySources(rawHtml, vod);
            } else {
                dedupeLineNames(lineMap);
                List<String> playFrom = new ArrayList<>();
                List<String> playLists = new ArrayList<>();
                Iterator<String> lineIt = lineMap.keys();
                while (lineIt.hasNext()) {
                    String lineKey = lineIt.next();
                    playFrom.add(lineKey);
                    playLists.add(lineMap.optString(lineKey));
                }
                vod.setVodPlayFrom(Util.join("$$$", playFrom));
                vod.setVodPlayUrl(Util.join("$$$", playLists));
            }
        } catch (Exception e) {
            SpiderDebug.log("parseHtmlPlaySources error: " + e.getMessage());
        }
    }

    /**
     * 同名线路去重：重名线路追加 _1/_2 数字后缀（对应 Smali أۦۧ 方法）
     * 依赖插入顺序，需在 LinkedHashMap 语义下操作 JSONObject
     */
    private void dedupeLineNames(JSONObject lineMap) throws JSONException {
        java.util.List<String> names = new ArrayList<>();
        Iterator<String> it = lineMap.keys();
        while (it.hasNext()) names.add(it.next());
        for (int i = 0; i < names.size(); i++) {
            String base = names.get(i);
            int suffix = 1;
            for (int j = i + 1; j < names.size(); j++) {
                if (names.get(j).equals(base)) {
                    Object episodes = lineMap.remove(names.get(j));
                    lineMap.put(base + "_" + suffix, episodes);
                    names.set(j, base + "_" + suffix);
                    suffix++;
                }
            }
        }
    }

    /**
     * 兜底解析：无 lineArray 配置时使用默认播放源解析
     */
    private void parseDefaultPlaySources(String html, com.github.catvod.bean.Vod vod) {
        try {
            Document doc = Jsoup.parse(html);
            List<String> episodes = parseEpisodeUrls(html, "默认");
            if (!episodes.isEmpty()) {
                vod.setVodPlayFrom("默认源");
                vod.setVodPlayUrl(Util.join("#", episodes));
            }
        } catch (Exception e) {
            SpiderDebug.log("parseDefaultPlaySources error: " + e.getMessage());
        }
    }

    /**
     * 解析播放链接列表（支持播放数组、播放标题、播放链接、播放链接前缀）
     */
    private List<String> parsePlayEpisodes(String html, String sourceName) {
        List<String> episodes = new ArrayList<>();
        if (Util.isEmpty(html)) return episodes;

        String cssPlayArr = parseCssShortSyntax(playArray);
        Document doc = Jsoup.parse(html);

        if (!playArray.isEmpty()) {
            Elements playEls = doc.select(cssPlayArr);
            for (Element el : playEls) {
                String epText = "";
                String epUrl = "";
                if (!playListTitle.isEmpty()) {
                    String cssTitle = parseCssShortSyntax(playListTitle);
                    Element titleEl = el.selectFirst(cssTitle);
                    epText = titleEl != null ? titleEl.text().trim() : el.text().trim();
                } else {
                    epText = el.text().trim();
                }
                if (!playLinkUrl.isEmpty()) {
                    String cssLink = parseCssShortSyntax(playLinkUrl);
                    Element linkEl = el.selectFirst(cssLink);
                    epUrl = linkEl != null ? linkEl.attr("href") : "";
                } else {
                    epUrl = el.attr("href");
                }
                if (epUrl.isEmpty()) continue;
                // 应用播放链接前缀
                if (!playPrefix.isEmpty()) {
                    epUrl = playPrefix + epUrl;
                }
                epUrl = buildUrl(homeUrl, epUrl);
                if (epText.isEmpty()) epText = "第" + (episodes.size() + 1) + "集";
                episodes.add(epText + "$" + epUrl);
            }
        } else {
            // 默认：从所有 <a> 标签解析
            Elements links = doc.select("a[href]");
            for (Element link : links) {
                String href = link.attr("href");
                String text = link.text().trim();
                if (href.isEmpty()) continue;
                if (href.startsWith("#") || href.startsWith("javascript:")) continue;
                if (!playUrlRule.isEmpty() && !href.toLowerCase().matches(".*" + playUrlRule + ".*")) continue;
                String fullUrl = buildUrl(homeUrl, href);
                if (!playPrefix.isEmpty()) fullUrl = playPrefix + fullUrl;
                if (text.isEmpty()) text = "播放" + (episodes.size() + 1);
                episodes.add(text + "$" + fullUrl);
            }
        }
        return episodes;
    }

    /**
     * 解析 JSON 模式的播放源
     */
    private void parseJsonPlaySources(JSONObject obj, com.github.catvod.bean.Vod vod) throws JSONException {
        // 如果配置了 playUrl 数组（每个元素是一个播放源 URL），逐条请求解析
        if (playUrl != null && playUrl.length() > 0) {
            JSONObject lineMap = new JSONObject();
            for (int i = 0; i < playUrl.length(); i++) {
                String sourceName = playUrl.getString(i);
                String sourceUrl = buildUrl(homeUrl, sourceName);
                try {
                    String sourceContent = fetchContent(sourceUrl);
                    List<String> episodes = parseEpisodeUrls(sourceContent, sourceName);
                    if (!episodes.isEmpty()) {
                        lineMap.put(sourceName, Util.join("#", episodes));
                    }
                } catch (Exception e) {
                    SpiderDebug.log("parseJsonPlaySources source error: " + sourceName + " - " + e.getMessage());
                }
            }
            if (lineMap.length() > 0) {
                List<String> playFrom = new ArrayList<>();
                List<String> playLists = new ArrayList<>();
                Iterator<String> lineIt = lineMap.keys();
                while (lineIt.hasNext()) {
                    String lineKey = lineIt.next();
                    playFrom.add(lineKey);
                    playLists.add(lineMap.optString(lineKey));
                }
                vod.setVodPlayFrom(Util.join("$$$", playFrom));
                vod.setVodPlayUrl(Util.join("$$$", playLists));
                return;
            }
        }
        // 尝试从 playJsonPath 中提取
        if (!playJsonPath.isEmpty()) {
            String[] paths = playJsonPath.split("\\|");
            for (String path : paths) {
                JSONObject sub = obj;
                for (String part : path.split("\\.")) {
                    sub = sub.optJSONObject(part);
                    if (sub == null) break;
                }
                if (sub != null) {
                    // 尝试提取播放数据
                    vod.setVodPlayFrom(sub.optString("playFrom", vod.getVodPlayFrom()));
                    vod.setVodPlayUrl(sub.optString("playUrl", vod.getVodPlayUrl()));
                    break;
                }
            }
        }
        // 兜底
        if (vod.getVodPlayFrom().isEmpty()) {
            vod.setVodPlayFrom("默认源");
            vod.setVodPlayUrl(obj.optString("playUrl", ""));
        }
    }

    /**
     * [新增] 使用 rule* 系列规则解析播放源（替代 lineArray/playArray 方式）
     * 支持：rulePlayFrom/rulePlayUrl/ruleTitle/ruleUrl/rulePre/rulePost/ruleCover
     */
    private void parsePlaySourcesByRule(com.github.catvod.bean.Vod vod, Document doc) {
        try {
            // rulePlayFrom: 线路来源选择器，解析出各线路名称
            if (!rulePlayFrom.isEmpty()) {
                List<String> fromNames = extractBySelectorAll(doc.html(), parseCssShortSyntax(rulePlayFrom));
                if (!fromNames.isEmpty()) {
                    StringBuilder fromBuilder = new StringBuilder();
                    StringBuilder urlBuilder = new StringBuilder();
                    // 线路去重：跳过"名称+地址"完全相同的重复线路（同名线路会导致播放器索引错乱）
                    Set<String> seenRoutes = new HashSet<>();
                    for (int i = 0; i < fromNames.size(); i++) {
                        String fromName = fromNames.get(i).trim();
                        // rulePlayUrl: 播放URL选择器，每个线路对应一个URL
                        if (!rulePlayUrl.isEmpty()) {
                            List<String> urls = extractBySelectorAll(doc.html(), parseCssShortSyntax(rulePlayUrl));
                            String playUrl = i < urls.size() ? urls.get(i).trim() : "";
                            // 应用前缀/后缀
                            if (!rulePre.isEmpty()) playUrl = resolveVariables(rulePre) + playUrl;
                            if (!rulePost.isEmpty()) playUrl = playUrl + resolveVariables(rulePost);
                            String routeKey = fromName + "@" + playUrl;
                            if (!seenRoutes.add(routeKey)) continue;
                            if (fromBuilder.length() > 0) fromBuilder.append("$$$");
                            fromBuilder.append(fromName);
                            if (urlBuilder.length() > 0) urlBuilder.append("$$$");
                            urlBuilder.append(playUrl);
                        }
                    }
                    if (fromBuilder.length() > 0) {
                        vod.setVodPlayFrom(fromBuilder.toString());
                        vod.setVodPlayUrl(urlBuilder.toString());
                    }
                }
            }
            // ruleTitle/ruleUrl: 直接解析播放列表（单线路）
            if (!ruleTitle.isEmpty() && !ruleUrl.isEmpty()) {
                List<String> titles = extractBySelectorAll(doc.html(), parseCssShortSyntax(ruleTitle));
                List<String> urls = extractBySelectorAll(doc.html(), parseCssShortSyntax(ruleUrl));
                if (!titles.isEmpty() && !urls.isEmpty()) {
                    StringBuilder epBuilder = new StringBuilder();
                    int count = Math.min(titles.size(), urls.size());
                    for (int i = 0; i < count; i++) {
                        String title = titles.get(i).trim();
                        String url = urls.get(i).trim();
                        if (!rulePre.isEmpty()) url = resolveVariables(rulePre) + url;
                        if (!rulePost.isEmpty()) url = url + resolveVariables(rulePost);
                        if (epBuilder.length() > 0) epBuilder.append("#");
                        epBuilder.append(title).append("$").append(url);
                    }
                    if (epBuilder.length() > 0) {
                        if (vod.getVodPlayFrom().isEmpty()) {
                            vod.setVodPlayFrom("默认");
                        }
                        String existingUrl = vod.getVodPlayUrl();
                        if (existingUrl.isEmpty()) {
                            vod.setVodPlayUrl(epBuilder.toString());
                        } else {
                            vod.setVodPlayUrl(existingUrl + "$$$" + epBuilder.toString());
                        }
                    }
                }
            }
        } catch (Exception e) {
            SpiderDebug.log("parsePlaySourcesByRule error: " + e.getMessage());
        }
    }

    /**
     * [新增] 使用 rule* 系列规则解析详情字段
     * 支持：ruleContentType/rulePlayState/rulePlayYear/rulePlayArea/rulePlayActor/rulePlayDirector/rulePlayDesc/rulePlayNote
     */
    private void parseDetailFieldsByRule(Document doc, com.github.catvod.bean.Vod vod) {
        try {
            if (!ruleContentType.isEmpty()) {
                String type = extractBySelector(doc.html(), parseCssShortSyntax(ruleContentType));
                if (!type.isEmpty()) vod.setTypeName(type.trim());
            }
            if (!rulePlayState.isEmpty()) {
                String state = extractBySelector(doc.html(), parseCssShortSyntax(rulePlayState));
                if (!state.isEmpty()) vod.setVodRemarks(state.trim());
            }
            if (!rulePlayYear.isEmpty()) {
                String year = extractBySelector(doc.html(), parseCssShortSyntax(rulePlayYear));
                if (!year.isEmpty()) vod.setVodYear(year.trim());
            }
            if (!rulePlayArea.isEmpty()) {
                String area = extractBySelector(doc.html(), parseCssShortSyntax(rulePlayArea));
                if (!area.isEmpty()) vod.setVodArea(area.trim());
            }
            if (!rulePlayActor.isEmpty()) {
                String actor = extractBySelector(doc.html(), parseCssShortSyntax(rulePlayActor));
                if (!actor.isEmpty()) vod.setVodActor(actor.trim());
            }
            if (!rulePlayDirector.isEmpty()) {
                String director = extractBySelector(doc.html(), parseCssShortSyntax(rulePlayDirector));
                if (!director.isEmpty()) vod.setVodDirector(director.trim());
            }
            if (!rulePlayDesc.isEmpty()) {
                String desc = extractBySelector(doc.html(), parseCssShortSyntax(rulePlayDesc));
                if (!desc.isEmpty()) {
                    if (vod.getVodContent() != null && !vod.getVodContent().isEmpty()) {
                        vod.setVodContent(vod.getVodContent() + "\n" + desc.trim());
                    } else {
                        vod.setVodContent(desc.trim());
                    }
                }
            }
            if (!rulePlayNote.isEmpty()) {
                String note = extractBySelector(doc.html(), parseCssShortSyntax(rulePlayNote));
                if (!note.isEmpty()) vod.setVodRemarks(note.trim());
            }
        } catch (Exception e) {
            SpiderDebug.log("parseDetailFieldsByRule error: " + e.getMessage());
        }
    }

    /**
     * [新增] 使用 rule* 系列规则解析列表页视频条目
     * 支持：ruleListArray/ruleListTitle/ruleListId/ruleListPic/ruleListDesc
     */
    private List<com.github.catvod.bean.Vod> parseListByRule(String html) {
        List<com.github.catvod.bean.Vod> list = new ArrayList<>();
        if (Util.isEmpty(html)) return list;
        try {
            // 如果配置了列表数组规则，先按数组截取
            String extractHtml = html;
            if (!ruleListArray.isEmpty()) {
                String arrResult = extractBySelector(html, parseCssShortSyntax(ruleListArray));
                if (!arrResult.isEmpty()) extractHtml = arrResult;
            }
            // 提取各字段
            List<String> titles = !ruleListTitle.isEmpty() ? extractBySelectorAll(extractHtml, parseCssShortSyntax(ruleListTitle)) : new ArrayList<>();
            List<String> ids = !ruleListId.isEmpty() ? extractBySelectorAll(extractHtml, parseCssShortSyntax(ruleListId)) : new ArrayList<>();
            List<String> pics = !ruleListPic.isEmpty() ? extractBySelectorAll(extractHtml, parseCssShortSyntax(ruleListPic)) : new ArrayList<>();
            List<String> descs = !ruleListDesc.isEmpty() ? extractBySelectorAll(extractHtml, parseCssShortSyntax(ruleListDesc)) : new ArrayList<>();
            int count = Math.max(Math.max(titles.size(), ids.size()), Math.max(pics.size(), descs.size()));
            for (int i = 0; i < count; i++) {
                com.github.catvod.bean.Vod vod = new com.github.catvod.bean.Vod();
                vod.setVodName(i < titles.size() ? titles.get(i).trim() : "");
                vod.setVodId(i < ids.size() ? ids.get(i).trim() : "");
                vod.setVodPic(i < pics.size() ? pics.get(i).trim() : "");
                vod.setVodRemarks(i < descs.size() ? descs.get(i).trim() : "");
                list.add(vod);
            }
        } catch (Exception e) {
            SpiderDebug.log("parseListByRule error: " + e.getMessage());
        }
        return list;
    }

    /**
     * [新增] 使用 rule* 系列规则解析详情页字段
     * 支持：ruleDetailArray/ruleDetailTitle/ruleDetailId/ruleDetailPic/ruleDetailDesc/ruleDetailContent
     */
    private void parseDetailByRule(Document doc, com.github.catvod.bean.Vod vod) {
        try {
            String html = doc.html();
            // 先按详情数组截取
            if (!ruleDetailArray.isEmpty()) {
                String arrResult = extractBySelector(html, parseCssShortSyntax(ruleDetailArray));
                if (!arrResult.isEmpty()) html = arrResult;
            }
            if (!ruleDetailTitle.isEmpty()) {
                String title = extractBySelector(html, parseCssShortSyntax(ruleDetailTitle));
                if (!title.isEmpty()) vod.setVodName(title.trim());
            }
            if (!ruleDetailId.isEmpty()) {
                String id = extractBySelector(html, parseCssShortSyntax(ruleDetailId));
                if (!id.isEmpty()) vod.setVodId(id.trim());
            }
            if (!ruleDetailPic.isEmpty()) {
                String pic = extractBySelector(html, parseCssShortSyntax(ruleDetailPic));
                if (!pic.isEmpty()) vod.setVodPic(pic.trim());
            }
            if (!ruleDetailDesc.isEmpty()) {
                String desc = extractBySelector(html, parseCssShortSyntax(ruleDetailDesc));
                if (!desc.isEmpty()) vod.setVodRemarks(desc.trim());
            }
            if (!ruleDetailContent.isEmpty()) {
                String content = extractBySelector(html, parseCssShortSyntax(ruleDetailContent));
                if (!content.isEmpty()) vod.setVodContent(content.trim());
            }
        } catch (Exception e) {
            SpiderDebug.log("parseDetailByRule error: " + e.getMessage());
        }
    }

    /**
     * [新增] 使用 rule* 系列规则解析搜索结果
     * 支持：ruleSearchArray/ruleSearchTitle/ruleSearchId/ruleSearchPic/ruleSearchDesc
     */
    private List<com.github.catvod.bean.Vod> parseSearchByRule(String html) {
        List<com.github.catvod.bean.Vod> list = new ArrayList<>();
        if (Util.isEmpty(html)) return list;
        try {
            String extractHtml = html;
            if (!ruleSearchArray.isEmpty()) {
                String arrResult = extractBySelector(html, parseCssShortSyntax(ruleSearchArray));
                if (!arrResult.isEmpty()) extractHtml = arrResult;
            }
            List<String> titles = !ruleSearchTitle.isEmpty() ? extractBySelectorAll(extractHtml, parseCssShortSyntax(ruleSearchTitle)) : new ArrayList<>();
            List<String> ids = !ruleSearchId.isEmpty() ? extractBySelectorAll(extractHtml, parseCssShortSyntax(ruleSearchId)) : new ArrayList<>();
            List<String> pics = !ruleSearchPic.isEmpty() ? extractBySelectorAll(extractHtml, parseCssShortSyntax(ruleSearchPic)) : new ArrayList<>();
            List<String> descs = !ruleSearchDesc.isEmpty() ? extractBySelectorAll(extractHtml, parseCssShortSyntax(ruleSearchDesc)) : new ArrayList<>();
            int count = Math.max(Math.max(titles.size(), ids.size()), Math.max(pics.size(), descs.size()));
            for (int i = 0; i < count; i++) {
                com.github.catvod.bean.Vod vod = new com.github.catvod.bean.Vod();
                vod.setVodName(i < titles.size() ? titles.get(i).trim() : "");
                vod.setVodId(i < ids.size() ? ids.get(i).trim() : "");
                vod.setVodPic(i < pics.size() ? pics.get(i).trim() : "");
                vod.setVodRemarks(i < descs.size() ? descs.get(i).trim() : "");
                list.add(vod);
            }
        } catch (Exception e) {
            SpiderDebug.log("parseSearchByRule error: " + e.getMessage());
        }
        return list;
    }

    /**
     * 搜索视频内容
     */
    @Override
    public String searchContent(String keyword, boolean quick) throws Exception {
        lazyInit();
        return search(keyword, "", quick);
    }

    @Override
    public String searchContent(String keyword, boolean quick, String pg) throws Exception {
        return search(keyword, pg, quick);
    }

    /**
     * 搜索核心方法（支持搜索数组、搜索标题、搜索链接、搜索图片等独立规则）
     */
    public String search(String keyword, String pg, boolean quick) throws Exception {
        String searchPath = resolveVariables(searchUrl)
                .replace("{wd}", java.net.URLEncoder.encode(keyword, "UTF-8"))
                .replace("{pg}", pg);
        // 搜索后缀
        if (!searchSuffix.isEmpty()) {
            searchPath = searchPath + searchSuffix;
        }
        String html = (!encodeHtmlUrl.isEmpty()) ? fetchEncodedContent(buildUrl(homeUrl, searchPath)) : fetchContent(buildUrl(homeUrl, searchPath));
        if (html.isEmpty()) return "[]";

        // [新增] 搜索二次截取（多级链式）
        if (!searchSecondCut.isEmpty()) {
            html = applySecondCut(html, searchSecondCut);
        }
        // 通用二次截取
        if (!secondaryCut.isEmpty() && !secondaryCut.equals(searchSecondCut)) {
            html = applySecondCut(html, secondaryCut);
        }

        // 搜索模式 1：直接返回原始内容（适用于 JSON API 搜索）
        if (searchMode == 1) {
            if (isJsonMode(html)) {
                return html;
            }
            return "[]";
        }

        // [新增] 支持 searchClass 筛选分类
        if (!searchClass.isEmpty()) {
            html = applySecondCut(html, searchClass + "&&");
        }

        List<com.github.catvod.bean.Vod> vodList;
        if (isJsonMode(html)) {
            vodList = parseJsonVodList(html);
        } else if (!searchArray.isEmpty()) {
            // 使用独立的搜索选择器规则
            vodList = parseSearchVodList(html);
        } else if (!ruleSearchArray.isEmpty() || !ruleSearchTitle.isEmpty()) {
            // [新增] 使用 ruleSearch* 系列规则解析搜索结果
            vodList = parseSearchByRule(html);
        } else {
            vodList = parseHomeVodList(html);
        }

        // [新增] xml搜索分支：当配置包含 xml搜索=1 时，重新用 XML 解析器解析
        if (configJson != null && "1".equals(configJson.optString("xml搜索"))) {
            List<com.github.catvod.bean.Vod> xmlList = parseXmlVodList(html);
            if (!xmlList.isEmpty()) vodList = xmlList;
        }

        // [新增] 特殊分类链接多源合并（Smali：读取特殊分类链接配置，并行搜索后合并）
        if (!specialClassUrl.isEmpty()) {
            String[] extraUrls = specialClassUrl.split("\\r?\\n");
            java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(Math.min(extraUrls.length, 4));
            java.util.concurrent.List<java.util.concurrent.Future<List<com.github.catvod.bean.Vod>>> futures = new java.util.ArrayList<>();
            for (String extraUrl : extraUrls) {
                final String eu = extraUrl.trim();
                if (eu.isEmpty()) continue;
                futures.add(pool.submit(() -> {
                    try {
                        String extraHtml = fetchContent(buildUrl(homeUrl, eu.replace("{wd}", java.net.URLEncoder.encode(keyword, "UTF-8"))));
                        if (extraHtml.isEmpty()) return new ArrayList<com.github.catvod.bean.Vod>();
                        if (isJsonMode(extraHtml)) return parseJsonVodList(extraHtml);
                        if (!searchArray.isEmpty()) return parseSearchVodList(extraHtml);
                        if (!ruleSearchArray.isEmpty() || !ruleSearchTitle.isEmpty()) return parseSearchByRule(extraHtml);
                        return parseHomeVodList(extraHtml);
                    } catch (Exception e) {
                        SpiderDebug.log("特殊分类搜索失败: " + e.getMessage());
                        return new ArrayList<com.github.catvod.bean.Vod>();
                    }
                }));
            }
            for (java.util.concurrent.Future<List<com.github.catvod.bean.Vod>> f : futures) {
                try { vodList.addAll(f.get(10, java.util.concurrent.TimeUnit.SECONDS)); } catch (Exception ignored) {}
            }
            pool.shutdown();
        }

        // [新增] 支持 searchIndex 过滤：只保留 index 位置的条目
        if (searchIndex >= 0 && searchIndex < vodList.size()) {
            List<com.github.catvod.bean.Vod> filtered = new ArrayList<>();
            filtered.add(vodList.get(searchIndex));
            vodList = filtered;
        }

        // [新增] 缓存搜索结果（用于 homeContent 中展示搜索结果）
        searchResultArr = toVodJsonArray(vodList);
        searchResult = searchResultArr.toString();

        if (reverseOrder) {
            java.util.Collections.reverse(vodList);
        }

        JSONArray list = new JSONArray();
        for (com.github.catvod.bean.Vod vod : vodList) {
            list.put(toVodJson(vod));
        }
        return list.toString();
    }

    /**
     * 使用独立搜索规则解析搜索结果
     */
    private List<com.github.catvod.bean.Vod> parseSearchVodList(String html) {
        List<com.github.catvod.bean.Vod> list = new ArrayList<>();
        if (Util.isEmpty(html)) return list;
        Document doc = Jsoup.parse(html);
        String cssArr = parseCssShortSyntax(searchArray);
        Elements items = doc.select(cssArr);
        for (Element el : items) {
            com.github.catvod.bean.Vod vod = new com.github.catvod.bean.Vod();
            // 搜索标题
            if (!searchTitle.isEmpty()) {
                String cssTitle = parseCssShortSyntax(searchTitle);
                Element titleEl = el.selectFirst(cssTitle);
                vod.setVodName(titleEl != null ? titleEl.text().trim() : el.text().trim());
            } else {
                vod.setVodName(el.text().trim());
            }
            // 搜索链接
            if (!searchLink.isEmpty()) {
                String cssLink = parseCssShortSyntax(searchLink);
                Element linkEl = el.selectFirst(cssLink);
                vod.setVodId(linkEl != null ? linkEl.attr("href") : el.attr("href"));
            } else {
                vod.setVodId(el.attr("href"));
            }
            // 搜索图片
            if (!searchPic.isEmpty()) {
                String cssPic = parseCssShortSyntax(searchPic);
                Element picEl = el.selectFirst(cssPic);
                vod.setVodPic(picEl != null ? picEl.attr("src") : "");
            }
            // 搜索副标题
            if (!searchDesc.isEmpty()) {
                String cssDesc = parseCssShortSyntax(searchDesc);
                Element descEl = el.selectFirst(cssDesc);
                vod.setVodRemarks(descEl != null ? descEl.text().trim() : "");
            }
            list.add(vod);
        }
        return list;
    }

    /**
     * XML/HTML 搜索解析（对应 Smali xml搜索=1 分支）
     * 复用 searchArray/searchTitle/searchLink/searchPic/searchDesc 等选择器规则
     */
    private List<com.github.catvod.bean.Vod> parseXmlVodList(String html) {
        List<com.github.catvod.bean.Vod> list = new ArrayList<>();
        if (Util.isEmpty(html)) return list;
        try {
            Document doc = Jsoup.parse(html);
            String cssArr = parseCssShortSyntax(searchArray.isEmpty() ? "li,a" : searchArray);
            Elements items = doc.select(cssArr);
            for (Element el : items) {
                com.github.catvod.bean.Vod vod = new com.github.catvod.bean.Vod();
                if (!searchTitle.isEmpty()) {
                    Element titleEl = el.selectFirst(parseCssShortSyntax(searchTitle));
                    vod.setVodName(titleEl != null ? titleEl.text().trim() : el.text().trim());
                } else {
                    vod.setVodName(el.text().trim());
                }
                if (!searchLink.isEmpty()) {
                    Element linkEl = el.selectFirst(parseCssShortSyntax(searchLink));
                    vod.setVodId(linkEl != null ? linkEl.attr("href").trim() : "");
                }
                if (!searchPic.isEmpty()) {
                    Element picEl = el.selectFirst(parseCssShortSyntax(searchPic));
                    vod.setVodPic(picEl != null ? picEl.attr("src").trim() : "");
                }
                if (!searchDesc.isEmpty()) {
                    Element descEl = el.selectFirst(parseCssShortSyntax(searchDesc));
                    vod.setVodRemarks(descEl != null ? descEl.text().trim() : "");
                }
                if (!vod.getVodId().isEmpty() && !vod.getVodName().isEmpty()) {
                    list.add(vod);
                }
            }
        } catch (Exception e) {
            SpiderDebug.log("parseXmlVodList error: " + e.getMessage());
        }
        return list;
    }

    /**
     * 内联字幕参数标记：id 形如 "videoUrl?zimu=subtitleUrl"（对应 Smali 中 [?&]zimu= 的拆分）
     */
    private static final Pattern SUBTITLE_PARAM_PATTERN = Pattern.compile("[?&]zimu=");
    /**
     * 内联解析接口参数标记：id 形如 "videoUrl?activate=parseApi"（对应 Smali 中 [?&]activate= 的拆分）
     */
    private static final Pattern ACTIVATE_PARAM_PATTERN = Pattern.compile("[?&]activate=");

    /**
     * 解析播放链接完整链路：
     * 1. 内联参数提取（?zimu= 字幕 / ?activate= 解析接口覆盖）
     * 2. 解析接口处理（activate 优先于全局 parseUrl，支持 {url} 占位）
     * 3. 跳转播放链接（redirectUrl + redirectParse）
     * 4. 远程视频 URL 处理（[videourl=...] 标记）
     * 5. 结果构建：直链(parse=0) / 嗅探(parse=1) / 直接播放，并注入字幕(subs)
     */
    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        SpiderDebug.log("XBPQ playerContent flag=" + flag + ", id=" + id + ", vipFlags=" + vipFlags);
        String playId = id == null ? "" : id;
        String subTitleUrl = "";
        String activateParseUrl = "";

        // ===== 1. 内联播放参数提取（对应 Smali 中 zimu/activate 拆分逻辑）=====
        Matcher zimuMatcher = SUBTITLE_PARAM_PATTERN.matcher(playId);
        if (zimuMatcher.find()) {
            String tail = playId.substring(zimuMatcher.end());
            int nextParam = tail.indexOf('&');
            subTitleUrl = nextParam >= 0 ? tail.substring(0, nextParam) : tail;
            playId = playId.substring(0, zimuMatcher.start());
        }
        Matcher activateMatcher = ACTIVATE_PARAM_PATTERN.matcher(playId);
        if (activateMatcher.find()) {
            String tail = playId.substring(activateMatcher.end());
            int nextParam = tail.indexOf('&');
            activateParseUrl = nextParam >= 0 ? tail.substring(0, nextParam) : tail;
            playId = playId.substring(0, activateMatcher.start());
        }

        // ===== 1.5 [补充 Smali 121893-122203] json播放：短 id 且无 url= 标记时从页面 player json 提取直链 =====
        if (playId.length() <= 10 && !playId.contains("url=") && !playId.contains("Url=")) {
            try {
                String pageUrl = buildUrl(homeUrl, playId);
                String pageHtml = fetchContent(pageUrl);
                // Smali 121931 规则：<script>*var player_&&</script> 截取播放器脚本块
                String block = applySecondCut(pageHtml, "<script>*var player_&&</script>");
                if (block.length() > 50) {
                    int b0 = block.indexOf('{');
                    int b1 = block.lastIndexOf('}');
                    if (b0 >= 0 && b1 > b0) {
                        JSONObject playJson = new JSONObject(block.substring(b0, b1 + 1));
                        String jsonUrl = playJson.optString("url", "");
                        // Smali 121995-122148：encrypt 字段解码（条件：extendText 含 u 且不含 u0）
                        if (playJson.has("encrypt") && jsonUrl.length() > 0) {
                            int enc = playJson.getInt("encrypt");
                            boolean decodeAble = extendText.contains("u") && !extendText.contains("u0");
                            if (enc == 1) {
                                if (decodeAble) jsonUrl = java.net.URLDecoder.decode(jsonUrl, "UTF-8");
                            } else if (enc == 2) {
                                String decoded = new String(android.util.Base64.decode(jsonUrl, 0), "UTF-8");
                                jsonUrl = decodeAble ? java.net.URLDecoder.decode(decoded, "UTF-8") : decoded;
                            }
                        }
                        if (jsonUrl.length() > 6) {
                            SpiderDebug.log("免嗅获得直链--> " + jsonUrl);
                            playId = jsonUrl;
                        }
                    }
                }
            } catch (Exception e) {
                SpiderDebug.log("json播放 error: " + e.getMessage());
            }
        }

        // ===== 2. 解析接口处理（activate 优先于全局 parseUrl）=====
        String effectiveParseUrl = !activateParseUrl.isEmpty() ? activateParseUrl : parseUrl;
        boolean isDirectVideo = Util.isVideoFormat(playId);
        if (!effectiveParseUrl.isEmpty() && !isDirectVideo) {
            try {
                String parseApi = effectiveParseUrl.contains("{url}")
                        ? effectiveParseUrl.replace("{url}", java.net.URLEncoder.encode(playId, "UTF-8"))
                        : effectiveParseUrl + java.net.URLEncoder.encode(playId, "UTF-8");
                String parseHtml = fetchContent(parseApi);
                String parsedUrl = extractPlayUrl(parseHtml);
                if (!parsedUrl.isEmpty() && Util.isVideoFormat(parsedUrl)) {
                    playId = parsedUrl;
                    isDirectVideo = true;
                }
            } catch (Exception e) {
                SpiderDebug.log("playerContent parse error: " + e.getMessage());
            }
        }

        // ===== 2.5 [补充 Smali 122205-122700] vipFlags 多解析源尝试 =====
        // id 非直链且带 vipFlags 时：逐个解析源构建候选页，黑名单（解析源码黑名单，# 分隔）过滤后
        // 以 "url"*"&&" / 'url'*'&&' 规则抓取解析结果，取第一条有效直链
        if (vipFlags != null && !vipFlags.isEmpty() && !isDirectVideo && !Util.isVideoFormat(playId)) {
            for (int vi = 0; vi < vipFlags.size(); vi++) {
                try {
                    String vFlag = vipFlags.get(vi);
                    String cand;
                    if (vi == 0) {
                        cand = playId;
                    } else {
                        try {
                            String[] sp = playId.split("[uU]rl=");
                            cand = vFlag + (sp.length > 1 ? sp[1] : playId);
                        } catch (Exception e2) {
                            cand = vFlag + playId;
                        }
                    }
                    String candUrl = buildUrl(homeUrl, cand);
                    // 黑名单过滤：命中则跳过该源
                    boolean blackHit = false;
                    if (!parseSourceBlacklist.isEmpty()) {
                        for (String word : parseSourceBlacklist.split("#")) {
                            if (!word.isEmpty() && candUrl.contains(word)) {
                                blackHit = true;
                                break;
                            }
                        }
                    }
                    if (blackHit) continue;
                    SpiderDebug.log("开始解析\n解析源码--> " + candUrl);
                    String parsePage = fetchContent(candUrl);
                    String picked = "";
                    // 规则1："url"*"&&" 截取；失败则 'url'*'&&'（Smali 122468/122525）
                    String r1 = applyWildcardCut(parsePage, "\"url\"", "&&");
                    if (r1 != null && r1.trim().length() >= 6) {
                        picked = r1.trim();
                    } else {
                        String r2 = applyWildcardCut(parsePage, "'url'", "&&");
                        if (r2 != null && r2.trim().length() >= 6) picked = r2.trim();
                    }
                    if (picked.length() > 6 && Util.isVideoFormat(picked)) {
                        SpiderDebug.log("免嗅获得直链--> " + picked);
                        playId = picked;
                        isDirectVideo = true;
                        break;
                    }
                } catch (Exception e) {
                    SpiderDebug.log("vipFlags 源尝试失败: " + e.getMessage());
                }
            }
        }

        // ===== 3. 跳转播放链接（redirectUrl）=====
        if (!redirectUrl.isEmpty() && !isDirectVideo) {
            String redirectHtml = fetchContent(playId);
            if (!redirectHtml.isEmpty()) {
                String redirected = applySecondCut(redirectHtml, redirectUrl);
                if (!redirected.isEmpty()) {
                    playId = redirected;
                    isDirectVideo = Util.isVideoFormat(playId);
                    // 如果有跳转解析器，先过解析器提取真实URL
                    if (!redirectParse.isEmpty() && !isDirectVideo) {
                        String afterParse = resolveVariables(redirectParse);
                        if (afterParse.contains("j:")) {
                            playId = extractPlayUrl(afterParse.replace("j:", "") + ";url=" + playId);
                        } else if (!afterParse.isEmpty()) {
                            playId = afterParse.replace("{url}", playId);
                        }
                        isDirectVideo = Util.isVideoFormat(playId);
                    }
                }
            }
        }

        // ===== 3.5 云盘/磁力链接直接透传（Smali：aliyundrive/alipan/quark/uc/magnet 不走解析）=====
        if (playId.contains("quark.cn") || playId.contains("夸克视频")) {
            SpiderDebug.log("quarkDrive url:" + playId);
            com.github.catvod.bean.Result qResult = com.github.catvod.bean.Result.get();
            qResult.url(playId);
            return qResult.string();
        }
        if (playId.contains("uczyzy") || playId.contains("uc.cn")) {
            SpiderDebug.log("ucDrive url:" + playId);
            com.github.catvod.bean.Result uResult = com.github.catvod.bean.Result.get();
            uResult.url(playId);
            return uResult.string();
        }
        if (playId.startsWith("magnet:")) {
            SpiderDebug.log("magnet url:" + playId);
            com.github.catvod.bean.Result mResult = com.github.catvod.bean.Result.get();
            mResult.url(playId);
            return mResult.string();
        }

        // ===== 4. 视频 URL 有效性验证 + 远程视频 URL 处理 =====
        if (!isVideoUrl(playId)) {
            SpiderDebug.log("playerContent: invalid video URL: " + playId);
            com.github.catvod.bean.Result errorResult = com.github.catvod.bean.Result.get();
            errorResult.error("无效的视频地址");
            return errorResult.string();
        }
        playId = remoteVideoUrlProcess(playId);

        com.github.catvod.bean.Result result = com.github.catvod.bean.Result.get();

        // ===== 5. 字幕注入（内联 zimu 参数优先；=lrc 标记时下载并转 SRT，对应 Smali 124862-125010）=====
        if (!subTitleUrl.isEmpty()) {
            try {
                com.github.catvod.bean.Sub sub = com.github.catvod.bean.Sub.create();
                if (subTitleUrl.contains("=lrc")) {
                    // [补充 Smali 124862-124937] LRC 字幕：下载内容 → lrcToSrt → data URL 内嵌
                    String lrcContent = fetchContent(buildUrl(homeUrl, subTitleUrl));
                    String srtText = lrcContent.isEmpty() ? "" : lrcToSrt(lrcContent);
                    if (!srtText.isEmpty()) {
                        String dataUrl = "data:application/x-subrip;base64,"
                                + android.util.Base64.encodeToString(srtText.getBytes("UTF-8"), android.util.Base64.NO_WRAP);
                        sub.url(dataUrl);
                        sub.name((flag == null || flag.isEmpty() ? "subtitle" : flag) + ".srt");
                        sub.format("application/x-subrip");
                    } else {
                        sub.url(subTitleUrl);
                        sub.name("subtitle");
                        sub.format("application/x-subrip");
                    }
                } else {
                    sub.url(subTitleUrl);
                    sub.name("subtitle");
                    String lowerSub = subTitleUrl.toLowerCase();
                    if (lowerSub.contains(".vtt")) {
                        sub.format("vtt");
                    } else if (lowerSub.contains(".ass")) {
                        sub.format("ass");
                    } else {
                        sub.format("srt");
                    }
                }
                List<com.github.catvod.bean.Sub> subList = new ArrayList<>();
                subList.add(sub);
                result.subs(subList);
            } catch (Exception e) {
                SpiderDebug.log("playerContent subtitle error: " + e.getMessage());
            }
        }

        // ===== 6. 直接播放：直接返回 URL，不经过播放器 =====
        if (directPlay) {
            result.url(playId);
            return result.string();
        }

        // ===== 7. 构建播放结果：直链播放(parse=0) 或 嗅探模式(parse=1) =====
        result.url(playId);
        result.header(buildPlayHeaders());

        if (Util.isVideoFormat(playId)) {
            // 直链视频：不需要 webview 解析
            result.parse(0);
        } else if (!noSniff) {
            // 非直链（网页播放页）：交给播放器 webview 嗅探
            result.parse(1);
        }

        return result.string();
    }

    /**
     * 通配截取（对应 Smali 规则 "url"*"&&" / 'url'*'&&'，122468/122525）：
     * 从 content 中找到 start 标记后，取到 end 标记（&&）之间的内容；
     * 首个引号闭合处截断（提取 "url":"value" 的 value）。
     */
    private String applyWildcardCut(String content, String startMark, String endMark) {
        try {
            if (content == null || content.isEmpty()) return null;
            int s = content.indexOf(startMark);
            if (s < 0) return null;
            s += startMark.length();
            int e = content.indexOf(endMark, s);
            if (e < 0) e = content.length();
            String seg = content.substring(s, e);
            // 取引号内的值（"url":"xxx" 或 'url':'xxx'）
            java.util.regex.Matcher vm = java.util.regex.Pattern.compile("['\"]([^'\"]+)['\"]").matcher(seg);
            return vm.find() ? vm.group(1) : seg;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * LRC 字幕转 SRT（对应 Smali 124913 SpiderApi.lrcToSrt）：
     * 解析 [mm:ss.xx] 时间标签（支持一行多标签），按时间排序后逐行生成 SRT 段，
     * 结束时间取下一行起始时间（末行 +3s）。
     */
    private String lrcToSrt(String lrc) {
        try {
            java.util.regex.Pattern tp = java.util.regex.Pattern.compile("\\[(\\d{1,2}):(\\d{1,2})(?:[.:](\\d{1,3}))?]");
            List<long[]> times = new ArrayList<>();
            List<String> lines = new ArrayList<>();
            for (String line : lrc.split("\\r?\\n")) {
                java.util.regex.Matcher m = tp.matcher(line);
                List<Long> ts = new ArrayList<>();
                while (m.find()) {
                    long ms = Long.parseLong(m.group(1)) * 60000L + Long.parseLong(m.group(2)) * 1000L;
                    if (m.group(3) != null) {
                        String frac = (m.group(3) + "00").substring(0, 3);
                        ms += Long.parseLong(frac);
                    }
                    ts.add(ms);
                }
                String text = tp.matcher(line).replaceAll("").trim();
                if (ts.isEmpty() || text.isEmpty()) continue;
                for (long t : ts) {
                    times.add(new long[]{t});
                    lines.add(text);
                }
            }
            // 按时间稳定排序（索引配对）
            Integer[] idx = new Integer[lines.size()];
            for (int i = 0; i < idx.length; i++) idx[i] = i;
            final List<long[]> ft = times;
            java.util.Arrays.sort(idx, (a, b) -> Long.compare(ft.get(a)[0], ft.get(b)[0]));
            StringBuilder sb = new StringBuilder();
            for (int n = 0; n < idx.length; n++) {
                long start = times.get(idx[n])[0];
                long end = n + 1 < idx.length ? times.get(idx[n + 1])[0] : start + 3000L;
                sb.append(n + 1).append('\n');
                sb.append(fmtSrtTime(start)).append(" --> ").append(fmtSrtTime(end)).append('\n');
                sb.append(lines.get(idx[n])).append("\n\n");
            }
            return sb.toString();
        } catch (Exception e) {
            SpiderDebug.log("lrcToSrt error: " + e.getMessage());
            return "";
        }
    }

    /** 毫秒 → SRT 时间戳 HH:MM:SS,mmm */
    private String fmtSrtTime(long ms) {
        return String.format("%02d:%02d:%02d,%03d", ms / 3600000, (ms / 60000) % 60, (ms / 1000) % 60, ms % 1000);
    }

    /**
     * 读取 SharedPreferences 偏好（Smali أۣۧ，对应 Init.اۣ SharedPreferences）
     */
    private String readPref(String key) {
        try {
            return Init.getString(key, "");
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 保存 SharedPreferences 偏好（Smali أ۟ۥ）
     */
    private void savePref(String key, String value) {
        try {
            Init.put(key, value == null ? "" : value);
        } catch (Exception ignored) {
        }
    }

    /**
     * 消息/指令构建（Smali أۣۥ）
     * "refresh" → 列表刷新指令；"moreSet" → 更多设置表单；其余返回空串
     */
    private String msgBuild(String s) {
        try {
            if ("refresh".equals(s)) {
                JSONObject act = new JSONObject();
                act.put("actionId", "__refresh_list__");
                JSONObject r = new JSONObject();
                r.put("action", act);
                return r.toString();
            }
            if ("moreSet".equals(s)) {
                return moreSetForm();
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    /**
     * 构建消息弹窗 tab（Smali اۧۥ(String,String)，68591-68800 完整还原）
     * 用于请求失败/网站维护等场景，以列表项形式弹出友情提示：
     * [{vod_name: 名称, vod_id: {actionId:"消息弹窗", type:"msgbox", title:"友情提示",
     *   htmlMsg: 内容>10字?内容:名称}.toString(), vod_pic:"clan://assets/tab.png?bgcolor=随机色", vod_tag:"action"}]
     *
     * @param msgText    弹窗正文（长度大于 10 时作为 htmlMsg 展示）
     * @param actionName tab 标题（正文过短时兜底作为 htmlMsg）
     */
    private JSONArray msgTabBuild(String msgText, String actionName) {
        JSONArray arr = new JSONArray();
        try {
            JSONObject act = new JSONObject();
            act.put("actionId", "消息弹窗");
            act.put("type", "msgbox");
            act.put("title", "友情提示");
            act.put("htmlMsg", msgText != null && msgText.length() > 10 ? msgText : actionName);
            JSONObject item = new JSONObject();
            item.put("vod_name", actionName);
            item.put("vod_id", act.toString());
            item.put("vod_pic", "clan://assets/tab.png?bgcolor=" + randomColor());
            item.put("vod_tag", "action");
            arr.put(item);
        } catch (Exception ignored) {
        }
        return arr;
    }

    /**
     * 保存搜索词到缓存（Smali أ۟ۧ）
     * 缓存格式："wd:=wd,wd2:=wd2,..."，条目数上限取配置 searchWdNum（默认 6）
     */
    private void saveWd(String wd) {
        JSONObject cfg = configJson;
        if (cfg == null || cfg.optBoolean("offSearchCache")) return;
        if (wd == null || wd.length() <= 0) return;
        String cache = readPref("searchWdCache");
        String numStr = cfg.optString("searchWdNum", "");
        int num = 6;
        if (numStr.length() > 0 && numStr.matches("\\d+")) {
            num = Integer.parseInt(numStr);
        }
        String tag = wd + ":=" + wd;
        if (cache.length() > 0 && num > 1) {
            if (cache.indexOf(tag) < 0) {
                String[] parts = cache.split(",");
                if (parts.length < num) {
                    cache = tag + "," + cache;
                } else {
                    cache = tag + "," + cache.substring(0, cache.lastIndexOf(","));
                }
            }
        } else {
            cache = tag;
        }
        if (num > 0) {
            savePref("searchWdCache", cache);
        } else {
            savePref("searchWdCache", "");
        }
    }

    /**
     * 获取随机颜色号（Smali ا۪۟）
     * colorStr 为 1~2 位数字且未开随机且不含 "-" 时返回固定色；否则从 0~99 池中不重复抽取
     */
    private String randomColor() {
        if (colorStr != null && colorStr.length() > 0 && colorStr.length() < 3
                && configJson != null && !configJson.optBoolean("colorRandom")
                && colorStr.indexOf("-") < 0) {
            return colorStr;
        }
        if (colorPool.isEmpty()) {
            for (int i = 0; i < 100; i++) colorPool.add(i);
        }
        int pick = colorPool.remove(random.nextInt(colorPool.size()));
        return String.valueOf(pick);
    }

    /**
     * 构建偏好设置默认菜单（Smali اۭۦ，92273-93155）
     * 每项 {name, action, selected}；SSTop 与 moreSet 默认开启
     */
    private JSONArray buildPrefMenu() {
        JSONArray menu = new JSONArray();
        try {
            menu.put(new JSONObject().put("name", "置顶搜索和设置").put("action", "SSTop").put("selected", true));
            menu.put(new JSONObject().put("name", "随机图标背景色").put("action", "colorRandom").put("selected", false));
            menu.put(new JSONObject().put("name", "始终显示收藏夹").put("action", "favoritesShow").put("selected", false));
            menu.put(new JSONObject().put("name", "不屏蔽伦理筛选").put("action", "sexFilter").put("selected", false));
            menu.put(new JSONObject().put("name", "关闭搜索记录").put("action", "offSearchCache").put("selected", false));
            menu.put(new JSONObject().put("name", "关闭源内过滤").put("action", "offTempFilter").put("selected", false));
            menu.put(new JSONObject().put("name", "允许自动换源").put("action", "switchSource").put("selected", false));
            menu.put(new JSONObject().put("name", "打开调试模式").put("action", "openDebug").put("selected", false));
            menu.put(new JSONObject().put("name", "查看更多设置").put("action", "moreSet").put("selected", true));
        } catch (Exception ignored) {
        }
        return menu;
    }

    /**
     * 获取偏好菜单（Smali ا۟ۥ，45060-45224）
     * 从 globalSeting 偏好读取用户已保存的选择状态（JSONArray），按 action 键与默认菜单合并
     */
    private JSONArray getPrefMenu() {
        try {
            String saved = readPref("globalSeting");
            JSONArray savedArr = null;
            if (saved != null && saved.length() > 0) {
                try {
                    savedArr = new JSONArray(saved);
                } catch (Exception ignored) {
                }
            }
            JSONArray defaults = buildPrefMenu();
            if (savedArr == null) return defaults;
            JSONArray merged = new JSONArray();
            for (int i = 0; i < defaults.length(); i++) {
                JSONObject item = defaults.getJSONObject(i);
                for (int j = 0; j < savedArr.length(); j++) {
                    JSONObject sv = savedArr.getJSONObject(j);
                    if (item.optString("action").equals(sv.optString("action"))) {
                        item.put("selected", sv.optBoolean("selected"));
                        break;
                    }
                }
                merged.put(item);
            }
            return merged;
        } catch (Exception e) {
            return buildPrefMenu();
        }
    }

    /**
     * 应用偏好菜单选择状态到 configJson（Smali اۧۦ，68802）
     * 将菜单各项 {action, selected} 展开为 configJson[action] = selected 布尔键值
     */
    private void applyPrefMenu() {
        try {
            JSONArray menu = getPrefMenu();
            for (int i = 0; i < menu.length(); i++) {
                JSONObject item = menu.getJSONObject(i);
                if (configJson == null) configJson = new JSONObject();
                configJson.put(item.optString("action"), item.optBoolean("selected"));
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * 插入"源内搜索/偏好设置"action tab（Smali اۨ۟，70028-70630）
     * top=true 置顶插入，false 追加到末尾；需 playerJson 配置启用
     */
    private JSONArray insertActionTabs(JSONArray classes, boolean top) {
        if (playerJson == null) return classes;
        try {
            JSONObject cfg = configJson == null ? new JSONObject() : configJson;
            // 偏好设置 tab（option 菜单 + type 2 + width 800）
            JSONObject setAct = new JSONObject();
            setAct.put("actionId", "偏好设置");
            setAct.put("title", "偏好设置");
            setAct.put("type", 2);
            setAct.put("width", 800);
            setAct.put("option", getPrefMenu());
            JSONObject setTab = new JSONObject();
            setTab.put("vod_name", "偏好设置");
            setTab.put("vod_id", "偏好设置");
            setTab.put("vod_pic", "clan://assets/set.png?bgcolor=" + randomColor());
            setTab.put("vod_tag", "action");
            setTab.put("action", setAct);
            // 源内搜索 tab（输入框 + 搜索历史）
            JSONObject input = new JSONObject();
            input.put("id", "wd");
            input.put("tip", "请输入搜索内容");
            input.put("value", "");
            JSONObject searchAct = new JSONObject();
            searchAct.put("actionId", "源内搜索");
            searchAct.put("title", "源内搜索");
            searchAct.put("type", 1);
            searchAct.put("input", new JSONArray().put(input));
            if (!cfg.optBoolean("offSearchCache")) {
                String wdCache = readPref("searchWdCache");
                if (wdCache != null && wdCache.length() > 0) {
                    searchAct.put("selectData", wdCache);
                }
            }
            JSONObject searchTab = new JSONObject();
            searchTab.put("vod_name", "源内搜索");
            searchTab.put("vod_id", "源内搜索");
            searchTab.put("vod_pic", "clan://assets/search.png?bgcolor=" + randomColor());
            searchTab.put("vod_tag", "action");
            searchTab.put("action", searchAct);
            // 收藏夹入口（Smali ا۟ۧ "#收藏夹$收藏夹"：favoritesShow 开启或已有收藏时显示）
            JSONObject collectTab = null;
            String collectSaved = readPref(singletonKey + "_collect");
            if (cfg.optBoolean("favoritesShow")
                    || (collectSaved != null && collectSaved.length() > 10)) {
                collectTab = new JSONObject();
                collectTab.put("vod_name", "收藏夹");
                collectTab.put("vod_id", "收藏夹");
                collectTab.put("vod_pic", "clan://assets/folder.png?bgcolor=" + randomColor());
                collectTab.put("vod_tag", "folder");
            }
            JSONArray result = new JSONArray();
            if (top) {
                result.put(searchTab);
                result.put(setTab);
                if (collectTab != null) result.put(collectTab);
                for (int i = 0; i < classes.length(); i++) result.put(classes.get(i));
            } else {
                for (int i = 0; i < classes.length(); i++) result.put(classes.get(i));
                if (collectTab != null) result.put(collectTab);
                result.put(searchTab);
                result.put(setTab);
            }
            return result;
        } catch (Exception e) {
            SpiderDebug.log("insertActionTabs error: " + e.getMessage());
            return classes;
        }
    }

    /**
     * 收藏管理（Smali اۣۣ(String, boolean)）
     * param 格式："名称$id"（$$$ 已转义为 ###）；add=true 添加，false 取消
     */
    private String collectManage(String param, boolean add) {
        String searchKey = "搜索二级";
        try {
            String saved = readPref(singletonKey + "_collect");
            JSONArray arr;
            if (saved != null && saved.length() > 10) {
                arr = new JSONArray(saved);
            } else {
                arr = new JSONArray();
            }
            param = param == null ? "" : param.replace("$$$", "###");
            if (add && param.indexOf("$") < 0) {
                return "无效影片，收藏失败！";
            }
            String[] segs = param.split("\\$");
            String name = segs.length > 0 ? segs[0] : "";
            String id = segs.length > 1 ? segs[1] : "";
            id = id.replace("###", "$$$");

            if (add) {
                JSONObject item = new JSONObject();
                String arrStr = arr.toString().replace("\"", "");
                if (arrStr.indexOf(id) >= 0) {
                    return "收藏夹中已经存在此片！";
                }
                if (id.indexOf("$$$") > 0) {
                    id = id.split("\\$\\$\\$")[1];
                }
                if (name.indexOf("vod_name") >= 0) {
                    name = name.replaceAll(".*更多\\[(.*?)\\].*", "$1");
                }
                item.put("vod_name", name);
                item.put("vod_id", id);
                if (id.indexOf("$$$") >= 0 || id.endsWith("二级") || id.indexOf("二级&") > 0) {
                    item.put("vod_style", "[CFS][AN:源内收藏,源内过滤]" + iconColor);
                    item.put("vod_tag", "folder");
                }
                String remarks = "目录";
                if (id.indexOf(searchKey) > 0) {
                    remarks = "搜索";
                }
                item.put("vod_remarks", remarks);
                String pic;
                if (playerJson != null) {
                    if (id.indexOf(searchKey) > 0) {
                        pic = "clan://assets/search.png?bgcolor=" + randomColor();
                    } else {
                        pic = "clan://assets/folder.png?bgcolor=" + randomColor();
                    }
                } else {
                    pic = "https://img1.baidu.com/it/u=2186574415,3687980247&fm=253&fmt=auto&app=138&f=PNG?w=300&h=300";
                }
                item.put("vod_pic", pic);
                arr.put(item);
            } else {
                for (int i = 0; i < arr.length(); i++) {
                    String vid = arr.getJSONObject(i).optString("vod_id");
                    if (vid != null && vid.length() >= 1 && vid.equals(id)) {
                        arr.remove(i);
                        savePref(singletonKey + "_collect", arr.toString());
                        return msgBuild("refresh");
                    }
                }
            }
            savePref(singletonKey + "_collect", arr.toString());
            if (add) {
                return "收藏成功，刷新后查看！";
            }
            return "取消收藏失败！";
        } catch (Exception e) {
            SpiderDebug.log("collectManage error: " + e.getMessage());
            return add ? "收藏失败！" : "取消收藏失败！";
        }
    }

    /**
     * 收藏排序（Smali أۭ۟）
     * command：前 移 ⇦ / 后 移 ⇨ / 置 顶 ⇧ / 置 底 ⇩；param 格式同 collectManage
     */
    private String sortCollect(String command, String param) {
        try {
            JSONArray arr = new JSONArray(readPref(singletonKey + "_collect"));
            param = param == null ? "" : param.replace("$$$", "###");
            String id = param.split("\\$")[1].replace("###", "$$$");
            int idx = -1;
            for (int i = 0; i < arr.length(); i++) {
                if (arr.getJSONObject(i).optString("vod_id").equals(id)) {
                    idx = i;
                    break;
                }
            }
            if (idx == -1) return "未找到对应的vod_id";
            if ("前 移 ⇦".equals(command)) {
                if (idx == 0) return "已在最前！";
                JSONObject prev = arr.getJSONObject(idx - 1);
                JSONObject cur = arr.getJSONObject(idx);
                arr.put(idx - 1, cur);
                arr.put(idx, prev);
            } else if ("后 移 ⇨".equals(command)) {
                if (idx == arr.length() - 1) return "已在最后！";
                JSONObject next = arr.getJSONObject(idx + 1);
                JSONObject cur = arr.getJSONObject(idx);
                arr.put(idx + 1, cur);
                arr.put(idx, next);
            } else if ("置 顶 ⇧".equals(command)) {
                if (idx == 0) return "已经到顶！";
                JSONObject item = arr.getJSONObject(idx);
                arr.remove(idx);
                JSONArray newArr = new JSONArray();
                newArr.put(item);
                for (int i = 0; i < arr.length(); i++) {
                    newArr.put(arr.get(i));
                }
                arr = newArr;
            } else if ("置 底 ⇩".equals(command)) {
                if (idx == arr.length() - 1) return "已经到底！";
                JSONObject item = arr.getJSONObject(idx);
                arr.remove(idx);
                arr.put(item);
            } else {
                return "无效的指令";
            }
            savePref(singletonKey + "_collect", arr.toString());
            return msgBuild("refresh");
        } catch (Exception e) {
            SpiderDebug.log("sortCollect error: " + e.getMessage());
            return "收藏排序时发生异常";
        }
    }

    /**
     * 标题过滤表单（Smali أۣۧ）
     * 返回 multiInput 表单：标题包含 / 标题不含 / 播放标题包含 / 播放标题不含
     */
    private String titleFiltForm(String param) {
        try {
            String kw = (param == null ? "" : param).split("\\$")[0];
            JSONObject cfg = configJson == null ? new JSONObject() : configJson;
            String titleNoHas = cfg.optString("titleNoHas", "");
            String formTitle = "偏好设置中可关闭过滤";
            if (!cfg.optBoolean("offTempFilter")) {
                if (titleNoHas.length() >= 1) {
                    kw = titleNoHas + "#" + kw;
                }
            } else {
                formTitle = "已关闭过滤，偏好设置中可开启";
                kw = titleNoHas;
            }
            JSONObject act = new JSONObject();
            act.put("actionId", "标题过滤");
            act.put("type", "multiInput");
            act.put("title", formTitle);
            act.put("width", 480);
            JSONArray inputs = new JSONArray();
            inputs.put(filterInput("标题包含:", "titleHas", cfg.optString("titleHas", "")));
            inputs.put(filterInput("标题不含:", "titleNoHas", kw));
            inputs.put(filterInput("播放标题包含:", "playTitleHas", cfg.optString("playTitleHas", "")));
            inputs.put(filterInput("播放标题不含:", "playTitleNoHas", cfg.optString("playTitleNoHas", "")));
            act.put("input", inputs);
            JSONObject r = new JSONObject();
            r.put("action", act);
            return r.toString();
        } catch (Exception e) {
            SpiderDebug.log("titleFiltForm error: " + e.getMessage());
            return "";
        }
    }

    /**
     * 构建单个过滤输入项（name/id/tip/value）
     */
    private JSONObject filterInput(String name, String id, String value) throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("name", name);
        obj.put("id", id);
        obj.put("tip", "多个过滤词用#分隔");
        obj.put("value", value == null ? "" : value);
        return obj;
    }

    /**
     * 偏好设置处理（Smali أ۪۟）
     * moreSet=false 保存 globalSeting，true 保存 globalMoreSeting；返回刷新指令或更多设置表单
     */
    private String prefsForm(String param, boolean moreSet) {
        log(debug, "action_value--> " + param);
        try {
            String key = moreSet ? "globalMoreSeting" : "globalSeting";
            if (!readPref(key).equals(param)) {
                savePref(key, param);
            }
            // 应用菜单选择状态到 configJson（SSTop/offTempFilter 等开关立即生效）
            applyPrefMenu();
            JSONObject cfg = configJson == null ? new JSONObject() : configJson;
            if (!moreSet && cfg.optBoolean("moreSet")) {
                return msgBuild("moreSet");
            }
            reloadAll();
            return msgBuild("refresh");
        } catch (Exception e) {
            SpiderDebug.log("prefsForm error: " + e.getMessage());
            return "";
        }
    }

    /**
     * 偏好热更新（Smali أ۟۫）：将 param(JSON) 的键值合并进 configJson
     */
    private void reloadPrefsHead(String param) {
        try {
            JSONObject patch = new JSONObject(param);
            java.util.Iterator<String> it = patch.keys();
            while (it.hasNext()) {
                String key = it.next();
                if (configJson == null) configJson = new JSONObject();
                configJson.put(key, patch.optString(key));
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * 配置完整重载（Smali ا۪ۨ）：合并 globalSeting/globalMoreSeting 偏好并重应用关键开关
     */
    private void reloadAll() {
        try {
            // 偏好覆盖：globalSeting / globalMoreSeting 的 JSON 键值合并进 configJson
            for (String key : new String[]{"globalSeting", "globalMoreSeting"}) {
                String saved = readPref(key);
                if (saved != null && saved.length() > 2) {
                    reloadPrefsHead(saved);
                }
            }
            JSONObject cfg = configJson == null ? new JSONObject() : configJson;
            // 临时过滤配置 _tempFilter
            String tempFilter = readPref(singletonKey + "_tempFilter");
            if (tempFilter != null && tempFilter.length() > 2) {
                reloadPrefsHead(tempFilter);
                cfg = configJson == null ? new JSONObject() : configJson;
            }
            // 关键开关重应用
            if (cfg.has("openDebug")) {
                debug = "1".equals(cfg.optString("openDebug")) || cfg.optBoolean("openDebug");
            }
            colorStr = cfg.optString("color", "");
        } catch (Exception e) {
            SpiderDebug.log("reloadAll error: " + e.getMessage());
        }
    }

    /**
     * 更多设置表单（Smali اۭۣ）
     * selectData 为 GZIP(base64) 解压后的颜色/选项配置
     */
    private String moreSetForm() {
        String title = "更多设置";
        try {
            JSONObject act = new JSONObject();
            act.put("actionId", title);
            act.put("type", "multiInput");
            act.put("title", title);
            act.put("width", 400);
            act.put("input", buildMoreSetInputs());
            act.put("selectData",
                    "香槟黄:=3,浅蓝色:=4,淡紫色:=8,浅青色:=10,棕灰色:=33,嫩草绿:=34,鲜橙色:=76,玫瑰色:=77,珍珠粉:=90"
                            + "$$$0,10,20,40$$$0,5,10,15,20$$$5,10,15,20");
            act.put("ungzip", "");
            JSONObject r = new JSONObject();
            r.put("action", act);
            return r.toString();
        } catch (Exception e) {
            SpiderDebug.log("moreSetForm error: " + e.getMessage());
            return "";
        }
    }

    /**
     * 构建更多设置输入项（Smali أۣۧ）：图标颜色 / 搜索词数量 / 嗅探超时等
     */
    private JSONArray buildMoreSetInputs() throws JSONException {
        JSONObject cfg = configJson == null ? new JSONObject() : configJson;
        JSONArray inputs = new JSONArray();
        JSONObject colorInput = new JSONObject();
        colorInput.put("name", "图标颜色:");
        colorInput.put("id", "color");
        colorInput.put("tip", "0~99数字或色段");
        colorInput.put("value", cfg.optString("color", ""));
        inputs.put(colorInput);
        JSONObject wdNumInput = new JSONObject();
        wdNumInput.put("name", "搜索词数量:");
        wdNumInput.put("id", "searchWdNum");
        wdNumInput.put("tip", "历史搜索词保存个数");
        wdNumInput.put("value", cfg.optString("searchWdNum", ""));
        inputs.put(wdNumInput);
        JSONObject randomInput = new JSONObject();
        randomInput.put("name", "随机图标色:");
        randomInput.put("id", "colorRandom");
        randomInput.put("tip", "开启后每次刷新随机换色");
        randomInput.put("value", cfg.optString("colorRandom", ""));
        inputs.put(randomInput);
        return inputs;
    }

    /**
     * 代理请求入口（action 方法）
     * 支持命令：decode / encode / base64decode / base64encode / proxy / encrypt / decrypt / cache / live
     * 及源内 UI 命令：源内搜索 / 源内收藏 / 源内过滤 / 标题过滤 / 重置偏好 / 清空收藏 / 取消收藏 / 排序 / 偏好设置 / 更多设置
     */
    public String action(String command, String param) {
        try {
            if ("decode".equals(command)) {
                return Util.decode(param);
            }
            if ("encode".equals(command)) {
                return Util.encode(param);
            }
            if ("base64decode".equals(command)) {
                return Util.base64Decode(param);
            }
            if ("base64encode".equals(command)) {
                return Util.base64Encode(param);
            }
            if ("proxy".equals(command)) {
                return fetchContent(param);
            }
            if ("encrypt".equals(command)) {
                return encrypt(param, "UTF-8", "", "");
            }
            if ("decrypt".equals(command)) {
                return decrypt(param, "UTF-8", "", "");
            }
            if ("live".equals(command)) {
                // 直播源解析
                return loadLiveContent(param);
            }
            if ("cache".equals(command)) {
                // 缓存管理：set / get / clear / keys
                if (param == null) return "{}";
                JSONObject cmd = new JSONObject(param);
                String op = cmd.optString("op", "");
                String key = cmd.optString("key", "");
                if ("set".equals(op) && !key.isEmpty()) {
                    String value = cmd.optString("value", "");
                    android.preference.PreferenceManager.getDefaultSharedPreferences(getContext())
                            .edit().putString(singletonKey + "_" + key, value).apply();
                    return "{\"ok\":true}";
                }
                if ("get".equals(op) && !key.isEmpty()) {
                    String value = android.preference.PreferenceManager.getDefaultSharedPreferences(getContext())
                            .getString(singletonKey + "_" + key, "");
                    return "{\"value\":\"" + value.replace("\"", "\\\"") + "\"}";
                }
                if ("clear".equals(op)) {
                    // 清除所有以 singletonKey_ 开头的缓存
                    android.preference.PreferenceManager.getDefaultSharedPreferences(getContext())
                            .edit().remove(singletonKey + "_category").apply();
                    android.preference.PreferenceManager.getDefaultSharedPreferences(getContext())
                            .edit().remove(singletonKey + "_home").apply();
                    android.preference.PreferenceManager.getDefaultSharedPreferences(getContext())
                            .edit().remove(singletonKey + "_search").apply();
                    android.preference.PreferenceManager.getDefaultSharedPreferences(getContext())
                            .edit().remove(singletonKey + "_detail").apply();
                    android.preference.PreferenceManager.getDefaultSharedPreferences(getContext())
                            .edit().remove(singletonKey + "_play").apply();
                    return "{\"ok\":true}";
                }
                if ("keys".equals(op)) {
                    // 返回所有缓存键
                    java.util.Map<String, ?> allEntries = android.preference.PreferenceManager.getDefaultSharedPreferences(getContext())
                            .getAll();
                    JSONArray keys = new JSONArray();
                    for (String entryKey : allEntries.keySet()) {
                        if (entryKey.startsWith(singletonKey + "_")) {
                            keys.put(entryKey.substring(singletonKey.length() + 1));
                        }
                    }
                    return "{\"keys\":" + keys + "}";
                }
            }
            // ===== 源内 UI 命令（Smali action 95058-95648 完整还原） =====
            if ("源内搜索".equals(command)) {
                JSONObject j = param == null ? new JSONObject() : new JSONObject(param);
                String wd = j.optString("wd", "");
                if (wd.length() > 0) {
                    saveWd(wd);
                    JSONObject act = new JSONObject();
                    act.put("actionId", "__self_search__");
                    act.put("name", "搜索: " + wd);
                    act.put("tid", "源内搜索:" + wd);
                    act.put("flag", "[CFS][AN:源内收藏,源内过滤]" + iconColor);
                    JSONObject r = new JSONObject();
                    r.put("action", act);
                    return r.toString();
                }
            }
            if ("源内收藏".equals(command)) {
                return collectManage(param, true);
            }
            if ("源内过滤".equals(command)) {
                return titleFiltForm(param);
            }
            if ("标题过滤".equals(command)) {
                savePref(singletonKey + "_tempFilter", param);
                reloadPrefsHead(param);
                // Smali 95374-95382：返回 msgBuild("refresh") 触发列表刷新（"refresh" 指令，非偏好键名）
                return msgBuild("refresh");
            }
            if ("重置偏好".equals(command)) {
                savePref("globalSeting", "");
                savePref("globalMoreSeting", "");
                return "重置成功，下次生效";
            }
            if ("清空收藏".equals(command)) {
                savePref(singletonKey + "_collect", "");
                return msgBuild("refresh");
            }
            if ("取消收藏".equals(command)) {
                return collectManage(param, false);
            }
            if ("后 移 ⇨".equals(command) || "前 移 ⇦".equals(command)
                    || "置 顶 ⇧".equals(command) || "置 底 ⇩".equals(command)) {
                return sortCollect(command, param);
            }
            if ("偏好设置".equals(command)) {
                return prefsForm(param, false);
            }
            if ("更多设置".equals(command)) {
                return prefsForm(param, true);
            }
        } catch (Exception e) {
            SpiderDebug.log("action error: " + e.getMessage());
        }
        return null;
    }

    /**
     * 加载直播内容
     * 返回 JSON：{"list":[...]}
     */
    private String loadLiveContent(String param) {
        try {
            String liveUrlStr = (!liveUrl.isEmpty()) ? liveUrl : param;
            if (liveUrlStr.isEmpty()) return "{\"list\":[]}";
            // 如果 liveUrl 是 JSON 格式，直接解析
            if (liveUrlStr.trim().startsWith("{") || liveUrlStr.trim().startsWith("[")) {
                return parseJsonLiveList(liveUrlStr);
            }
            // 否则作为 URL 请求
            String html = fetchContent(liveUrlStr);
            if (html.isEmpty()) return "{\"list\":[]}";
            // 尝试按行分割（每行一个直播源）
            return parseLineLiveList(html);
        } catch (Exception e) {
            SpiderDebug.log("loadLiveContent error: " + e.getMessage());
            return "{\"list\":[]}";
        }
    }

    /**
     * 解析 JSON 格式的直播源列表
     */
    private String parseJsonLiveList(String jsonStr) {
        try {
            JSONArray array;
            if (jsonStr.trim().startsWith("[")) {
                array = new JSONArray(jsonStr);
            } else {
                JSONObject obj = new JSONObject(jsonStr);
                array = obj.optJSONArray("list") != null ? obj.getJSONArray("list")
                        : obj.optJSONArray("data") != null ? obj.getJSONArray("data")
                        : new JSONArray();
            }
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.getJSONObject(i);
                String name = item.optString("name", item.optString("title", ""));
                String url = item.optString("url", item.optString("link", ""));
                if (name.isEmpty() || url.isEmpty()) continue;
                JSONObject live = new JSONObject();
                live.put("name", name);
                live.put("url", url);
                return live.toString();
            }
            return "{}";
        } catch (Exception e) {
            SpiderDebug.log("parseJsonLiveList error: " + e.getMessage());
            return "{\"list\":[]}";
        }
    }

    /**
     * 解析行格式直播源列表
     * 格式：名称$URL 或 名称#URL
     */
    private String parseLineLiveList(String content) {
        try {
            String[] lines = content.split("\n");
            for (String line : lines) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String separator = line.contains("$") ? "$" : "#";
                int idx = line.indexOf(separator);
                if (idx <= 0) continue;
                String name = line.substring(0, idx).trim();
                String url = line.substring(idx + 1).trim();
                if (name.isEmpty() || url.isEmpty()) continue;
                JSONObject live = new JSONObject();
                live.put("name", name);
                live.put("url", url);
                return live.toString();
            }
            return "{}";
        } catch (Exception e) {
            SpiderDebug.log("parseLineLiveList error: " + e.getMessage());
            return "{\"list\":[]}";
        }
    }

    // ==================== AES 加解密 ====================

    /**
     * AES-CBC 模式解密
     */
    public String decrypt(String src, String encoding, String key, String iv) {
        try {
            if (src == null || src.isEmpty()) return "";
            return Crypto.CBC(src, key != null ? key : "", iv != null ? iv : "");
        } catch (Exception e) {
            SpiderDebug.log("decrypt error: " + e.getMessage());
            return src;
        }
    }

    /**
     * AES-CBC 模式加密
     */
    public String encrypt(String src, String encoding, String key, String iv) {
        try {
            if (src == null || src.isEmpty()) return "";
            return Crypto.aesEncrypt(src, key != null ? key : "", iv != null ? iv : "");
        } catch (Exception e) {
            SpiderDebug.log("encrypt error: " + e.getMessage());
            return "";
        }
    }

    // ==================== 扩展结果处理 ====================

    /**
     * 处理扩展结果（分类 / 详情 / 播放）
     * 对应 extResult 方法
     */
    public String extResult(String input, String type, String value) {
        try {
            if (input == null || input.isEmpty()) return "";
            JSONObject obj = new JSONObject(input);
            String vodId = obj.optString("vod_id", "");
            String classNameVal = obj.optString("class", "");
            String pg = obj.optString("pg", "");
            String flag = obj.optString("flag", "");
            String playUrlVal = obj.optString("play_url", "");
            boolean filter = obj.optBoolean("filter", false);

            HashMap<String, String> map = new HashMap<>();
            JSONObject extObj = obj.optJSONObject("extend");
            if (extObj != null) {
                Iterator<String> extKeys = extObj.keys();
                while (extKeys.hasNext()) {
                    String key = extKeys.next();
                    map.put(key, extObj.optString(key));
                }
            }

            if (!classNameVal.isEmpty() && !pg.isEmpty()) {
                return categoryContent(classNameVal, pg, filter, map);
            }
            if (!vodId.isEmpty()) {
                ArrayList<String> ids = new ArrayList<>();
                ids.add(vodId);
                return detailContent(ids);
            }
            if (!playUrlVal.isEmpty()) {
                return playerContent(flag, playUrlVal, new ArrayList<>());
            }
        } catch (Exception e) {
            SpiderDebug.log("extResult error: " + e.getMessage());
        }
        return "";
    }

    // ==================== Token 获取 ====================

    /**
     * 从网络获取 Token
     */
    public String getToken(String tokenUrl, String header, String key, String extend) {
        try {
            if (tokenUrl == null || tokenUrl.isEmpty()) return "";
            String resp = OkHttp.string(tokenUrl, headerMap);
            if (resp == null || resp.isEmpty()) return "";
            JSONObject json = new JSONObject(resp);
            return json.optString(key, "");
        } catch (Exception e) {
            SpiderDebug.log("getToken error: " + e.getMessage());
            return "";
        }
    }

    // ==================== 视频格式检测 ====================

    /**
     * 判断 URL 是否为可播放的视频格式（对应 Smali isVideoFormat 118237）
     */
    public boolean isVideoFormat(String url) {
        if (url == null || url.isEmpty()) return false;
        // 内置 base64 m3u8 数据流直接放行
        if (url.startsWith("data:application/vnd.apple.mpegurl;base64,")) return true;
        // 本地代理 m3u8 请求直接放行
        if (url.startsWith("http://127.0.0.1:") && url.indexOf("/proxy.m3u8?") > 0) return true;
        // 手动嗅探关闭时，走通用格式检测（Smali أۥ.اۥ）
        if (!manualVideoCheck()) return Util.isVideoFormat(url);
        // 手动嗅探开启：协议白名单 + 嗅探词/过滤词匹配
        String lowerUrl = url.toLowerCase();
        if (!lowerUrl.startsWith("http") && !lowerUrl.startsWith("magnet")) return false;
        JSONObject cfg = configJson == null ? new JSONObject() : configJson;
        String sniffStr = cfg.optString("嗅探词", cfg.optString("VideoFormat",
                "m3u8#.mp4#.flv#.mp3#.m4a#magnet:#ed2k:#ftp:#thunder:#push:#tvbox-xg:"));
        String filterStr = cfg.optString("过滤词", cfg.optString("VideoFilter",
                "url=#=http#Url=#;post;#.js#114.mp4"));
        String[] sniffWords = sniffStr.split("#");
        String[] filterWords = filterStr.split("#");
        for (String word : sniffWords) {
            if (word.length() < 1) continue;
            if (lowerUrl.indexOf(word) < 0) continue;
            // 命中嗅探词后逐个检查过滤词，含过滤词则判定非视频
            for (String fw : filterWords) {
                if (fw.length() < 1) continue;
                if (lowerUrl.indexOf(fw) >= 0) return false;
            }
            return true;
        }
        return false;
    }

    /**
     * 手动嗅探开关（对应 Smali manualVideoCheck 118530）
     */
    public boolean manualVideoCheck() {
        JSONObject cfg = configJson == null ? new JSONObject() : configJson;
        // 配置了嗅探词（或过滤词）即视为开启
        if (!cfg.optString("嗅探词", cfg.optString("过滤词", "")).isEmpty()) return true;
        // 显式开关：手动嗅探/ManualSniffer == 1
        if ("1".equals(cfg.optString("手动嗅探", cfg.optString("ManualSniffer", "")))) return true;
        // extend 中含 x 标记时开启（Smali 实例字段 اۣ۟）
        return extendText.indexOf("x") >= 0;
    }

    /**
     * 数学表达式求值
     */
    public double mathEval(String expression) {
        if (expression == null || expression.isEmpty()) return 0;
        try {
            return MathEval.evaluate(expression);
        } catch (Exception e) {
            SpiderDebug.log("mathEval error: " + e.getMessage());
            return 0;
        }
    }

    // ==================== 懒加载初始化 ====================

    /**
     * 懒加载：从远程获取配置内容
     */
    private void lazyInit() {
        if (extendConfig != null && !extendConfig.isEmpty()
                && (singleton.homeUrl.isEmpty() || singleton.homeJson == null)) {
            try {
                String remoteConfig = OkHttp.string(extendConfig);
                if (!remoteConfig.isEmpty()) {
                    parseConfig(remoteConfig);
                }
            } catch (Exception e) {
                SpiderDebug.log("lazyInit error: " + e.getMessage());
            }
        }
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 从 HTML 中解析首页/列表页的视频 Vod 对象列表
     */
    private List<com.github.catvod.bean.Vod> parseHomeVodList(String html) {
        return parseHomeVodListFromSelector(html, null);
    }

    /**
     * [新增] 使用自定义选择器解析视频列表（支持 listRule 独立规则）
     */
    private List<com.github.catvod.bean.Vod> parseHomeVodListFromSelector(String html, String cssSelector) {
        List<com.github.catvod.bean.Vod> list = new ArrayList<>();
        if (Util.isEmpty(html)) return list;
        Document doc = Jsoup.parse(html);
        // 如果有独立 listRule，用它作为列表容器选择器；否则回退到 title/id/pic 规则
        String listSel = cssSelector != null && !cssSelector.isEmpty() ? cssSelector : "a";
        Elements items = doc.select(listSel);
        for (Element el : items) {
            com.github.catvod.bean.Vod vod = new com.github.catvod.bean.Vod();
            // 优先使用独立 idRule/titleRule/picRule/descRule
            if (!idRule.isEmpty()) {
                Element idEl = el.selectFirst(parseCssShortSyntax(idRule));
                vod.setVodId(idEl != null ? idEl.attr("href") : el.attr("href"));
            } else if (!title.isEmpty() && !id.isEmpty()) {
                vod.setVodId(extractId(el));
            } else {
                vod.setVodId(el.attr("href"));
            }
            if (!titleRule.isEmpty()) {
                Element tEl = el.selectFirst(parseCssShortSyntax(titleRule));
                vod.setVodName(tEl != null ? tEl.text().trim() : el.text().trim());
            } else if (!title.isEmpty()) {
                vod.setVodName(extractTitle(el));
            } else {
                vod.setVodName(el.text().trim());
            }
            if (!picRule.isEmpty()) {
                Element pEl = el.selectFirst(parseCssShortSyntax(picRule));
                vod.setVodPic(pEl != null ? extractPicAttr(pEl) : "");
            } else if (!pic.isEmpty()) {
                vod.setVodPic(extractPic(el));
            }
            if (!descRule.isEmpty()) {
                Element dEl = el.selectFirst(parseCssShortSyntax(descRule));
                vod.setVodRemarks(dEl != null ? dEl.text().trim() : "");
            } else if (!desc.isEmpty()) {
                vod.setVodRemarks(extractDesc(el));
            }
            list.add(vod);
        }
        return list;
    }

    /**
     * [新增] 从 JSON 响应中提取总页数
     */
    private int extractTotalPage(String jsonStr) {
        try {
            JSONObject obj = new JSONObject(jsonStr);
            // 常见路径
            Integer pageCount = obj.optInt("pagecount", -1);
            if (pageCount <= 0) pageCount = obj.optInt("total_page", -1);
            if (pageCount <= 0) pageCount = obj.optInt(" totalPages", -1);
            if (pageCount <= 0) {
                JSONObject pageInfo = obj.optJSONObject("page");
                if (pageInfo != null) pageCount = pageInfo.optInt("pagecount", -1);
            }
            if (pageCount <= 0) {
                JSONObject data = obj.optJSONObject("data");
                if (data != null) {
                    pageCount = data.optInt("pagecount", -1);
                    if (pageCount <= 0) pageCount = data.optInt("total_page", -1);
                }
            }
            return pageCount > 0 ? pageCount : -1;
        } catch (Exception e) {
            return -1;
        }
    }

    /**
     * 从 JSON 字符串中解析视频列表
     */
    private List<com.github.catvod.bean.Vod> parseJsonVodList(String jsonStr) {
        List<com.github.catvod.bean.Vod> list = new ArrayList<>();
        try {
            JSONArray array;
            if (jsonStr.trim().startsWith("[")) {
                array = new JSONArray(jsonStr);
            } else {
                JSONObject obj = new JSONObject(jsonStr);
                array = obj.optJSONArray("list") != null ? obj.getJSONArray("list")
                        : obj.optJSONArray("data") != null ? obj.getJSONArray("data")
                        : new JSONArray();
            }
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.getJSONObject(i);
                com.github.catvod.bean.Vod vod = new com.github.catvod.bean.Vod();
                vod.setVodId(item.optString("id", item.optString("vod_id", "")));
                vod.setVodName(item.optString("name", item.optString("vod_name", "")));
                vod.setVodPic(item.optString("pic", item.optString("vod_pic", "")));
                vod.setVodRemarks(item.optString("remarks", ""));
                vod.setVodContent(item.optString("content", ""));
                list.add(vod);
            }
        } catch (Exception e) {
            SpiderDebug.log("parseJsonVodList error: " + e.getMessage());
        }
        return list;
    }

    /**
     * 解析剧集链接（从 HTML 中提取 <a> 标签）
     */
    private ArrayList<String> parseEpisodeUrls(String content, String sourceName) {
        ArrayList<String> episodes = new ArrayList<>();
        if (Util.isEmpty(content)) return episodes;
        Document doc = Jsoup.parse(content);
        Elements links = doc.select("a[href]");
        for (Element link : links) {
            String href = link.attr("href");
            String text = link.text().trim();
            if (href.isEmpty()) continue;
            // 跳过锚点链接和无意义链接
            if (href.startsWith("#") || href.startsWith("javascript:")) continue;
            String fullUrl = buildUrl(homeUrl, href);
            // 过滤掉非视频链接（除非配置了 playUrlRule）
            if (!playUrlRule.isEmpty()) {
                if (!fullUrl.toLowerCase().matches(".*" + playUrlRule + ".*")) continue;
            }
            episodes.add(text + "$" + fullUrl);
        }
        return episodes;
    }

    /**
     * 从解析页 HTML 中提取真实播放 URL
     */
    private String extractPlayUrl(String html) {
        if (Util.isEmpty(html)) return "";
        // 尝试正则匹配视频直链
        Pattern p = Pattern.compile("(https?://[^\\s\"']+\\.(?:m3u8|mp4|flv|avi|mkv|rm|wmv|ts)[^\\s\"']*)");
        Matcher m = p.matcher(html);
        if (m.find()) return m.group(1);
        // 尝试提取 src 属性
        p = Pattern.compile("src[\"\\s=]+([^\"\\s]+)");
        m = p.matcher(html);
        if (m.find()) {
            String url = m.group(1).replaceAll("[\"' ]", "");
            if (url.startsWith("http")) return url;
        }
        return html;
    }

    /**
     * 从 Element 中提取图片 URL（支持多种属性名）
     */
    private String extractPicAttr(Element el) {
        String attr = pic.contains(":") ? pic.split(":")[1] : "src";
        String val = el.attr(attr);
        if (val.isEmpty()) {
            // 尝试 data-original / data-src 等常见属性
            val = el.attr("data-original");
            if (val.isEmpty()) val = el.attr("data-src");
            if (val.isEmpty()) val = el.attr("src");
        }
        return val.trim();
    }

    /**
     * 提取视频 ID（支持多种选择器策略）
     */
    private String extractId(Element el) {
        if (!id.isEmpty()) {
            try {
                Elements items = el.select(id);
                if (!items.isEmpty()) return items.first().text();
            } catch (Exception ignored) {}
        }
        String href = el.attr("href");
        // 尝试从 href 中提取数字 ID
        Pattern p = Pattern.compile("(\\d+)");
        Matcher m = p.matcher(href);
        if (m.find()) return m.group(1);
        return href;
    }

    /**
     * 提取标题
     */
    private String extractTitle(Element el) {
        if (!title.isEmpty()) {
            try {
                Elements items = el.select(title);
                if (!items.isEmpty()) return items.first().text();
            } catch (Exception ignored) {}
        }
        return el.attr("title");
    }

    /**
     * 提取图片
     */
    private String extractPic(Element el) {
        if (!pic.isEmpty()) {
            try {
                Elements items = el.select(pic);
                if (!items.isEmpty()) return items.first().attr("src");
            } catch (Exception ignored) {}
        }
        return el.attr("data-src");
    }

    /**
     * 提取备注/描述（与 extractTitle/extractPic 同构，使用 desc 配置选择器）
     */
    private String extractDesc(Element el) {
        if (!desc.isEmpty()) {
            try {
                Elements items = el.select(desc);
                if (!items.isEmpty()) return items.first().text();
            } catch (Exception ignored) {}
        }
        return el.text();
    }

    /**
     * 将 Vod 对象转为 JSONObject（用于 JSON 输出）
     */
    private JSONObject toVodJson(com.github.catvod.bean.Vod vod) {
        JSONObject json = new JSONObject();
        try {
            json.put("vod_id", vod.getVodId());
            json.put("vod_name", vod.getVodName());
            json.put("vod_pic", vod.getVodPic());
            json.put("vod_remarks", vod.getVodRemarks());
            json.put("vod_content", vod.getVodContent());
            // 详情扩展字段：类型/年代/地区/导演/主演（与 parseDetailField 设置的字段一一对应）
            if (vod.getTypeName() != null && !vod.getTypeName().isEmpty()) json.put("type_name", vod.getTypeName());
            if (vod.getVodYear() != null && !vod.getVodYear().isEmpty()) json.put("vod_year", vod.getVodYear());
            if (vod.getVodArea() != null && !vod.getVodArea().isEmpty()) json.put("vod_area", vod.getVodArea());
            if (vod.getVodDirector() != null && !vod.getVodDirector().isEmpty()) json.put("vod_director", vod.getVodDirector());
            if (vod.getVodActor() != null && !vod.getVodActor().isEmpty()) json.put("vod_actor", vod.getVodActor());
            if (vod.getVodPlayFrom() != null && !vod.getVodPlayFrom().isEmpty()) {
                json.put("vod_play_from", vod.getVodPlayFrom());
            }
            if (vod.getVodPlayUrl() != null && !vod.getVodPlayUrl().isEmpty()) {
                json.put("vod_play_url", vod.getVodPlayUrl());
            }
        } catch (JSONException e) {
            SpiderDebug.log("toVodJson error: " + e.getMessage());
        }
        return json;
    }

    /**
     * 将 Vod 列表转为 JSONArray
     */
    private JSONArray toVodJsonArray(List<com.github.catvod.bean.Vod> list) {
        JSONArray array = new JSONArray();
        for (com.github.catvod.bean.Vod vod : list) {
            array.put(toVodJson(vod));
        }
        return array;
    }

    /**
     * 列表排序：按数字前缀降序排列
     */
    public List<String> sortList(List<String> list) {
        if (list.size() < 2) return list;
        for (int i = 0; i < list.size() - 1; i++) {
            for (int j = 0; j < list.size() - i - 1; j++) {
                int numA = extractLeadingNumber(list.get(j));
                int numB = extractLeadingNumber(list.get(j + 1));
                if (numA < numB) {
                    String temp = list.get(j);
                    list.set(j, list.get(j + 1));
                    list.set(j + 1, temp);
                }
            }
        }
        return list;
    }

    /**
     * 从字符串开头提取数字
     */
    private int extractLeadingNumber(String str) {
        if (str == null || str.isEmpty()) return 0;
        StringBuilder sb = new StringBuilder();
        for (char c : str.toCharArray()) {
            if (Character.isDigit(c)) sb.append(c);
            else break;
        }
        try {
            return Integer.parseInt(sb.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * URL 规范化工具方法（本类扩展，非父类覆写）
     */
    protected String uRl(String url) {
        if (url == null) return "";
        return url.trim();
    }

    @Override
    public void destroy() {
        // 清理资源
    }
}
