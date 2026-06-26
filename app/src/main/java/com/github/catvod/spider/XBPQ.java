package com.github.catvod.spider;

import android.content.Context;
import android.text.TextUtils;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Filter;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.spider.xbpq.XBPQConfig;
import com.github.catvod.spider.xbpq.XBPQHttpHelper;
import com.github.catvod.spider.xbpq.XBPQJsoupParser;
import com.github.catvod.spider.xbpq.XBPQParser;
import com.github.catvod.spider.xbpq.XBPQPlayerHandler;
import com.github.catvod.spider.xbpq.XBPQRuleApplier;
import com.github.catvod.spider.xbpq.XBPQStringExtractor;
import com.github.catvod.spider.xbpq.XBPQUtils;
import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * XBPQ Spider - 通用站点爬虫（完整实现版本）
 * 
 * 本类是一个通用的视频站点爬虫，支持多种站点配置和自定义规则。
 * 通过 JSON 配置文件可以快速适配不同的视频站点。
 * 
 * 主要功能：
 * - 支持自定义站点配置（URL、域名、分类等）
 * - 支持搜索功能
 * - 支持分类浏览
 * - 支持详情页解析
 * - 支持播放链接获取
 * 
 * 解析规则支持：
 * - 支持 Jsoup 写法（如 "img&&src"）
 * - 支持 XPath 写法（如 "data-src=\"&&\""）
 * - 支持正则表达式写法
 * - 支持包含/不包含过滤
 * - 支持替换规则
 * - 支持排序规则
 * 
 * @author CatVodSpider Team
 * @version 3.0（完整实现版本）
 */
public class XBPQ extends Spider {

    // ==================== 常量定义 ====================
    
    private static final String HTTP = "http";
    private static final String HTTPS = "https";
    private static final String AMPERSAND = "&";
    private static final String DOUBLE_AMPERSAND = "&&";
    private static final String DOUBLE_DOLLAR = "$$";
    private static final String PIPE = "|";
    private static final String COLON = ":";
    private static final String SLASH = "/";
    private static final String HASH = "#";
    private static final String DOLLAR = "$";
    private static final String UNDERSCORE = "_";
    private static final String QUESTION_MARK = "?";
    private static final String LEFT_BRACKET = "[";
    private static final String RIGHT_BRACKET = "]";
    private static final String LEFT_PAREN = "(";
    private static final String RIGHT_PAREN = ")";
    private static final String REPLACE_MARKER = "替换:";
    private static final String CONTAIN_MARKER = "包含:";
    private static final String NOT_CONTAIN_MARKER = "不包含:";
    private static final String SORT_MARKER = "排序:";
    private static final String EMPTY_MARKER = "空";
    
    // 默认 User Agent
    private static final String PC_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    private static final String MOBILE_UA = "Mozilla/5.0 (iPhone; CPU iPhone OS 16_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.0 Mobile/15E148 Safari/604.1";
    
    // 请求超时时间（毫秒）
    private static final int REQUEST_TIMEOUT_MS = 15000; // 15秒
    
    // 最大重试次数
    private static final int MAX_RETRY_COUNT = 2;
    
    // ==================== 实例字段 ====================
    
    /** XBPQ 配置对象 */
    private XBPQConfig config;
    
    /** 站点域名 */
    private String siteHost;
    
    /** 分类 URL */
    private String cateUrl;
    
    /** 搜索 URL */
    private String searchUrl;
    
    /** 编码格式 */
    private String encoding;
    
    /** User Agent */
    private String userAgent;
    
    /** 调试模式 */
    private boolean debugMode;
    
    /** 直接播放标志 */
    private boolean directPlay;
    
    /** 嗅探词 */
    private String sniffWord;
    
    /** 强制嗅探词 */
    private String forceSniffWord;
    
    /** 页面代理 */
    private String pageProxy;
    
    /** 原始配置 JSON */
    private JSONObject rawConfig;

    /** HTTP 辅助类 */
    private XBPQHttpHelper httpHelper;

    // ==================== 构造函数 ====================
    
    public XBPQ() {
        config = null;
        siteHost = "";
        cateUrl = "";
        searchUrl = "";
        encoding = "UTF-8";
        userAgent = PC_UA;
        debugMode = false;
        directPlay = false;
        sniffWord = "";
        forceSniffWord = "";
        pageProxy = "";
        rawConfig = null;
    }

    // ==================== 初始化方法 ====================
    
    @Override
    public void init(Context context, String extend) throws Exception {
        super.init(context, extend);
        
        try {
            if (extend != null && !extend.isEmpty()) {
                // 解析配置
                if (extend.startsWith(HTTP) || extend.startsWith(HTTPS)) {
                    // 从 URL 加载配置
                    String jsonStr = OkHttp.string(extend, null, null);
                    rawConfig = new JSONObject(jsonStr);
                } else if (extend.startsWith("{")) {
                    // 直接解析 JSON 字符串
                    rawConfig = new JSONObject(extend);
                } else {
                    // 尝试解析为 JSON
                    rawConfig = new JSONObject(extend);
                }
                
                // 创建配置对象
                config = new XBPQConfig(rawConfig);
                
                // 初始化站点参数
                initSiteParams();
                
                // 创建 HTTP 辅助类实例
                httpHelper = new XBPQHttpHelper(config, siteHost, debugMode);
                
                // 设置 XBPQParser 的调试模式
                XBPQParser.setDebugMode(debugMode);
                
                if (debugMode) {
                    SpiderDebug.log("[XBPQ-" + siteHost + "] 初始化成功");
                }
            }
        } catch (Exception e) {
            SpiderDebug.error("[XBPQ] init error: " + e.getMessage(), e);
        }
    }
    
    /**
     * 初始化站点参数
     */
    private void initSiteParams() {
        try {
            // 获取主页 URL
            String homeUrl = config.getHomeUrl();
            if (!homeUrl.isEmpty()) {
                if (homeUrl.startsWith(HTTP) || homeUrl.startsWith(HTTPS)) {
                    siteHost = extractHost(homeUrl);
                }
            }
            
            // 获取分类 URL
            cateUrl = config.getCateUrl();
            if (!cateUrl.isEmpty() && (cateUrl.startsWith(HTTP) || cateUrl.startsWith(HTTPS))) {
                siteHost = extractHost(cateUrl);
            }
            
            // 获取搜索 URL
            searchUrl = config.getSearchUrl();
            
            // 获取编码
            encoding = config.getEncoding();
            
            // 获取 User Agent
            String ua = config.getUserAgent();
            if ("手机".equals(ua) || "mobile".equalsIgnoreCase(ua)) {
                userAgent = MOBILE_UA;
            } else {
                userAgent = PC_UA;
            }
            
            // 获取调试模式
            debugMode = rawConfig != null && 
                ("1".equals(rawConfig.optString("调试", "0")) || 
                 "true".equals(rawConfig.optString("调试", "false")));
            
            // 获取直接播放标志
            directPlay = config.isDirectPlay();
            
            // 获取嗅探词
            sniffWord = config.getSniffWord();
            forceSniffWord = config.getForceSniffWord();
            
            // 获取页面代理
            pageProxy = config.getPageProxy();
            
        } catch (Exception e) {
            SpiderDebug.error("[XBPQ] initSiteParams error: " + e.getMessage(), e);
        }
    }
    
    /**
     * 从 URL 中提取域名
     */
    private String extractHost(String url) {
        try {
            if (url == null || url.isEmpty()) return "";
            
            // 移除协议前缀
            String temp = url;
            if (temp.startsWith(HTTPS + COLON + SLASH + SLASH)) {
                temp = temp.substring(8);
            } else if (temp.startsWith(HTTP + COLON + SLASH + SLASH)) {
                temp = temp.substring(7);
            }
            
            // 提取域名部分
            int slashIndex = temp.indexOf(SLASH);
            if (slashIndex > 0) {
                return (url.startsWith(HTTPS) ? HTTPS : HTTP) + COLON + SLASH + SLASH + temp.substring(0, slashIndex);
            }
            
            return (url.startsWith(HTTPS) ? HTTPS : HTTP) + COLON + SLASH + SLASH + temp;
        } catch (Exception e) {
            return url;
        }
    }

    // ==================== 核心方法实现 ====================
    
    /**
     * 获取首页内容
     */
    @Override
    public String homeContent(boolean filter) {
        try {
            List<Class> classes = new ArrayList<>();
            LinkedHashMap<String, List<Filter>> filters = new LinkedHashMap<>();
            
            // 解析分类
            parseCategories(classes);
            
            // 获取首页推荐数据
            List<Vod> list = new ArrayList<>();
            if (config.isEnableHomeData() && !siteHost.isEmpty()) {
                String homeUrl = config.getHomeUrl();
                if (homeUrl.isEmpty()) {
                    homeUrl = siteHost;
                }
                
                String html = httpHelper.fetchPage(homeUrl);
                if (!html.isEmpty()) {
                    list = parseHomeList(html);
                }
            }
            
            // 解析筛选数据
            if (filter && rawConfig != null) {
                parseFilters(filters);
            }
            
            return Result.string(classes, list, filters);
        } catch (Exception e) {
            SpiderDebug.error("[XBPQ] homeContent error: " + e.getMessage(), e);
            return Result.error("首页内容获取失败");
        }
    }
    
    /**
     * 获取分类内容
     */
    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) {
        try {
            List<Vod> list = new ArrayList<>();
            
            // 构建分类 URL
            String url = buildCateUrl(tid, pg, extend);
            if (url.isEmpty()) {
                return Result.string(list);
            }
            
            // 获取页面内容
            String html = httpHelper.fetchPage(url);
            if (html.isEmpty()) {
                return Result.string(list);
            }
            
            // 解析视频列表
            list = parseCateList(html, tid);
            
            // 构建返回结果
            int page = XBPQUtils.parseInt(pg, 1);
            return Result.get().vod(list).page(page, 1, list.size(), list.size()).string();
        } catch (Exception e) {
            SpiderDebug.error("[XBPQ] categoryContent error: " + e.getMessage(), e);
            return Result.error("分类内容获取失败");
        }
    }
    
    /**
     * 获取详情内容
     */
    @Override
    public String detailContent(List<String> ids) {
        try {
            if (ids == null || ids.isEmpty()) {
                return Result.error("无效的ID");
            }
            
            String id = ids.get(0);
            List<Vod> list = new ArrayList<>();
            
            // 构建详情 URL
            String url = buildDetailUrl(id);
            if (url.isEmpty()) {
                url = id;
                if (!url.startsWith(HTTP) && !url.startsWith(HTTPS)) {
                    url = XBPQUtils.joinUrl(siteHost, url);
                }
            }
            
            // 获取页面内容
            String html = httpHelper.fetchPage(url);
            if (html.isEmpty()) {
                return Result.error("详情页面获取失败");
            }
            
            // 解析详情
            Vod vod = parseDetail(html, id);
            if (vod != null) {
                list.add(vod);
            }
            
            return Result.string(list);
        } catch (Exception e) {
            SpiderDebug.error("[XBPQ] detailContent error: " + e.getMessage(), e);
            return Result.error("详情内容获取失败");
        }
    }
    
    /**
     * 搜索内容
     */
    @Override
    public String searchContent(String key, boolean quick) {
        try {
            List<Vod> list = new ArrayList<>();
            
            // 构建搜索 URL
            String url = buildSearchUrl(key);
            if (url.isEmpty()) {
                return Result.string(list);
            }
            
            // 获取页面内容
            String html = httpHelper.fetchPage(url);
            if (html.isEmpty()) {
                return Result.string(list);
            }
            
            // 解析搜索结果
            list = parseSearchList(html);
            
            return Result.string(list);
        } catch (Exception e) {
            SpiderDebug.error("[XBPQ] searchContent error: " + e.getMessage(), e);
            return Result.error("搜索失败");
        }
    }
    
    /**
     * 播放内容 - 委托给 XBPQPlayerHandler 处理
     */
    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) {
        try {
            // 处理播放链接
            String url = id;
            
            // 添加域名前缀
            if (!url.startsWith(HTTP) && !url.startsWith(HTTPS) && !siteHost.isEmpty()) {
                url = XBPQUtils.joinUrl(siteHost, url);
            }
            
            // 添加前缀后缀
            String prefix = config.getEpisodeLinkPrefix();
            String suffix = config.getEpisodeLinkSuffix();
            if (!prefix.isEmpty()) {
                url = XBPQUtils.joinUrl(prefix, url);
            }
            if (!suffix.isEmpty()) {
                url = XBPQUtils.joinUrl(url, suffix);
            }
            
            // 判断是否直接播放
            if (directPlay || XBPQUtils.isVideoFormat(url)) {
                return Result.get()
                    .url(url)
                    .header(httpHelper.buildPlayHeaders())
                    .string();
            }
            
            // 构造 JSON 字符串
            JSONObject json = new JSONObject();
            json.put("url", url);
            json.put("parse", 1);
            String jsonStr = json.toString();
            
            // 构造扩展配置
            JSONObject ext = new JSONObject();
            if (!sniffWord.isEmpty()) {
                ext.put(XBPQPlayerHandler.PARSE_WORD, sniffWord);
            }
            if (!forceSniffWord.isEmpty()) {
                ext.put(XBPQPlayerHandler.FORCE_PARSE_WORD, forceSniffWord);
            }
            if (!pageProxy.isEmpty()) {
                ext.put(XBPQPlayerHandler.PAGE_PROXY, pageProxy);
            }
            
            // 委托给 XBPQPlayerHandler
            String result = XBPQPlayerHandler.playerContent(jsonStr, ext);
            
            // 解析结果
            JSONObject resultJson = new JSONObject(result);
            String playUrl = resultJson.optString("url", url);
            int parseFlag = resultJson.optInt("parse", 1);
            
            return Result.get()
                .url(playUrl)
                .parse(parseFlag)
                .header(httpHelper.buildPlayHeaders())
                .string();
        } catch (Exception e) {
            SpiderDebug.error("[XBPQ] playerContent error: " + e.getMessage(), e);
            return Result.error("播放链接获取失败");
        }
    }

    // ==================== URL 构建方法 ====================
    
    /**
     * 构建分类 URL
     */
    private String buildCateUrl(String tid, String pg, HashMap<String, String> extend) {
        try {
            String url = cateUrl;
            if (url.isEmpty()) {
                url = config.getCateUrl();
            }
            
            if (url.isEmpty()) {
                return "";
            }
            
            // 替换分类 ID
            url = url.replace("{cateId}", tid);
            
            // 替换页码（支持多种格式）
            url = url.replace("{catePg}", pg);
            url = url.replace("{pg}", pg);
            int pgNum = XBPQUtils.parseInt(pg, 1);
            url = url.replace("{page}", String.valueOf(pgNum));
            
            // 处理第一页特殊情况
            // 格式: 基础URL[第一页URL] 或 基础URL[]
            if (url.contains(LEFT_BRACKET) && url.contains(RIGHT_BRACKET)) {
                int start = url.indexOf(LEFT_BRACKET);
                int end = url.indexOf(RIGHT_BRACKET);
                if (start >= 0 && end > start) {
                    String contentInBracket = url.substring(start + 1, end);
                    String urlBeforeBracket = url.substring(0, start);
                    
                    if (pgNum == 1) {
                        // 第一页：如果括号内有内容，使用括号内的 URL；否则使用括号前的 URL
                        if (!contentInBracket.isEmpty()) {
                            url = contentInBracket;
                        } else {
                            url = urlBeforeBracket;
                        }
                    } else {
                        // 非第一页：使用括号前的 URL
                        url = urlBeforeBracket;
                    }
                }
            }
            
            // 替换扩展参数
            if (extend != null) {
                for (Map.Entry<String, String> entry : extend.entrySet()) {
                    String key = entry.getKey();
                    String value = entry.getValue();
                    url = url.replace("{" + key + "}", value);
                }
            }
            
            // 处理未替换的参数
            url = url.replace("{area}", "");
            url = url.replace("{class}", "");
            url = url.replace("{year}", "");
            url = url.replace("{by}", "");
            url = url.replace("{lang}", "");
            url = url.replace("{letter}", "");
            
            // 添加域名前缀
            if (!url.startsWith(HTTP) && !url.startsWith(HTTPS)) {
                url = XBPQUtils.joinUrl(siteHost, url);
            }
            
            return url;
        } catch (Exception e) {
            SpiderDebug.error("[XBPQ] buildCateUrl error: " + e.getMessage(), e);
            return "";
        }
    }
    
    /**
     * 构建搜索 URL
     */
    private String buildSearchUrl(String key) {
        try {
            String url = searchUrl;
            if (url.isEmpty()) {
                url = config.getSearchUrl();
            }
            
            if (url.isEmpty()) {
                // 使用默认搜索 URL
                if (!siteHost.isEmpty()) {
                    url = XBPQUtils.joinUrl(siteHost, "/search?wd=" + XBPQUtils.encodeUrl(key));
                }
                return url;
            }
            
            // 替换搜索关键词（在 POST 参数中也替换）
            url = url.replace("{wd}", XBPQUtils.encodeUrl(key));
            url = url.replace("{keyword}", XBPQUtils.encodeUrl(key));
            url = url.replace("{key}", XBPQUtils.encodeUrl(key));
            url = url.replace("{searchKey}", XBPQUtils.encodeUrl(key));
            url = url.replace("{pg}", "1");
            
            // POST 请求格式保持不变，让 XBPQHttpHelper 处理
            // 支持两种 POST 格式：
            // 1. url;post;params - 带参数的 POST 请求，params 为 POST 数据
            // 2. url;post        - 无参数的 POST 请求，使用配置中的 postData
            // XBPQHttpHelper.fetchPage 会自动识别并处理这两种格式
            
            // 添加域名前缀（仅对非 POST 格式的 URL）
            if (!url.contains(";post;") && !url.contains(";post") && !url.startsWith(HTTP) && !url.startsWith(HTTPS)) {
                url = XBPQUtils.joinUrl(siteHost, url);
            }
            
            return url;
        } catch (Exception e) {
            SpiderDebug.error("[XBPQ] buildSearchUrl error: " + e.getMessage(), e);
            return "";
        }
    }
    
    /**
     * 构建详情 URL
     */
    private String buildDetailUrl(String id) {
        try {
            if (id.startsWith(HTTP) || id.startsWith(HTTPS)) {
                return id;
            }
            
            // 添加域名前缀
            if (!siteHost.isEmpty()) {
                return XBPQUtils.joinUrl(siteHost, id);
            }
            
            return id;
        } catch (Exception e) {
            return id;
        }
    }

    // ==================== 解析方法 ====================
    
    /**
     * 解析分类列表
     */
    private void parseCategories(List<Class> classes) {
        try {
            // 从配置中获取分类
            String cateName = config.getCateName();
            String cateNameReplace = config.getCateNameReplace();
            String cateValue = config.getCateValue();  // 新增：分类值
            
            // 优先使用分类值模式（格式：分类名称用&分隔，分类值用&分隔）
            if (!cateName.isEmpty() && !cateValue.isEmpty()) {
                String[] names = cateName.split(AMPERSAND);
                String[] ids = cateValue.split(AMPERSAND);
                for (int i = 0; i < names.length && i < ids.length; i++) {
                    classes.add(new Class(ids[i], names[i]));
                }
            } else if (!cateName.isEmpty() && !cateNameReplace.isEmpty()) {
                // 海阔模式（格式同上）
                String[] names = cateName.split(AMPERSAND);
                String[] ids = cateNameReplace.split(AMPERSAND);
                for (int i = 0; i < names.length && i < ids.length; i++) {
                    classes.add(new Class(ids[i], names[i]));
                }
            } else if (!cateName.isEmpty()) {
                // 使用配置的分类（格式：名称$ID#名称$ID）
                String[] names = cateName.split(HASH);
                for (String name : names) {
                    String[] parts = name.split(DOLLAR);
                    if (parts.length >= 2) {
                        classes.add(new Class(parts[1], parts[0]));
                    } else if (parts.length == 1) {
                        classes.add(new Class(parts[0], parts[0]));
                    }
                }
            } else if (rawConfig != null) {
                // 从网页解析分类
                String cateUrl = config.getCateUrl();
                if (!cateUrl.isEmpty()) {
                    String html = httpHelper.fetchPage(siteHost);
                    if (!html.isEmpty()) {
                        parseCategoriesFromHtml(html, classes);
                    }
                }
            }
        } catch (Exception e) {
            SpiderDebug.error("[XBPQ] parseCategories error: " + e.getMessage(), e);
        }
    }
    
    /**
     * 从 HTML 解析分类
     */
    private void parseCategoriesFromHtml(String html, List<Class> classes) {
        try {
            String cateArray = config.getCateArray();
            String cateTitle = config.getCateTitle();
            String cateId = config.getCateId();
            
            if (cateArray.isEmpty()) return;
            
            // 二次截取 - 委托给 XBPQParser
            String content = XBPQParser.parseHtml(html, rawConfig.optString("分类二次截取", ""));
            
            // 解析数组
            List<String> items = parseArray(content, cateArray);
            
            for (String item : items) {
                String title = parseField(item, cateTitle);
                String id = parseField(item, cateId);
                
                if (!title.isEmpty() && !id.isEmpty()) {
                    classes.add(new Class(id, title));
                }
            }
        } catch (Exception e) {
            SpiderDebug.error("[XBPQ] parseCategoriesFromHtml error: " + e.getMessage(), e);
        }
    }
    
    /**
     * 解析筛选数据
     */
    private void parseFilters(LinkedHashMap<String, List<Filter>> filters) {
        try {
            // 解析类型筛选
            parseFilterByConfig(filters, "类型", "类型值", "class");
            
            // 解析剧情筛选
            parseFilterByConfig(filters, "剧情", "剧情值", "class");
            
            // 解析地区筛选
            parseFilterByConfig(filters, "地区", "地区值", "area");
            
            // 解析年份筛选
            parseFilterByConfig(filters, "年份", "年份值", "year");
            
            // 解析排序筛选
            parseFilterByConfig(filters, "排序", "", "by");
            
            // 解析语言筛选
            parseFilterByConfig(filters, "语言", "语言值", "lang");
            
            // 解析字母筛选
            parseFilterByConfig(filters, "字母", "字母值", "letter");
        } catch (Exception e) {
            SpiderDebug.error("[XBPQ] parseFilters error: " + e.getMessage(), e);
        }
    }
    
    /**
     * 根据配置解析筛选
     */
    private void parseFilterByConfig(LinkedHashMap<String, List<Filter>> filters, 
                                      String nameKey, String valueKey, String filterKey) {
        try {
            if (rawConfig == null) return;
            
            String names = rawConfig.optString(nameKey, "");
            String values = rawConfig.optString(valueKey, "");

            if (names.isEmpty()) return;

            List<Filter> filterList = new ArrayList<>();

            // 处理 [替换:...] 格式
            // 严格判断：必须以 "[替换:" 开头且以 "]" 结尾，才视为整段替换格式
            // 这样可避免混合格式（如 "电影$movie#[替换:tv>>电视剧]"）被误判，
            // 混合格式会落入下面的普通解析分支。
            boolean isReplaceFormat = names.startsWith(LEFT_BRACKET + REPLACE_MARKER)
                    && names.endsWith(RIGHT_BRACKET)
                    && names.length() > (LEFT_BRACKET + REPLACE_MARKER + RIGHT_BRACKET).length();

            if (isReplaceFormat) {
                // 格式: [替换:原值1>>新值1#原值2>>新值2]
                // 提取 "[替换:" 与最后一个 "]" 之间的内容，避免内部出现 "]" 时截断
                int replaceStart = (LEFT_BRACKET + REPLACE_MARKER).length();
                int replaceEnd = names.lastIndexOf(RIGHT_BRACKET);
                String replaceRules = names.substring(replaceStart, replaceEnd);
                String[] rules = replaceRules.split(HASH);
                for (String rule : rules) {
                    String trimmed = rule.trim();
                    if (trimmed.isEmpty()) continue;
                    String[] parts = trimmed.split(">>");
                    if (parts.length >= 2) {
                        // 原值作为 filter value，新值作为显示名称
                        filterList.add(new Filter(parts[0], parts[1]));
                    } else {
                        // 没有替换规则，直接使用
                        filterList.add(new Filter(parts[0], parts[0]));
                    }
                }
            }

            if (filterList.isEmpty()) {
                // 普通格式: 名称$值#名称$值 或 名称&名称（配合值配置）
                // 也作为 [替换:...] 解析失败时的兜底
                String[] nameArr = names.split(HASH);
                String[] valueArr = !values.isEmpty() ? values.split(AMPERSAND) : new String[0];

                for (int i = 0; i < nameArr.length; i++) {
                    String[] parts = nameArr[i].split(DOLLAR);
                    String name = parts[0];
                    String value = parts.length >= 2 ? parts[1] :
                        (valueArr.length > i ? valueArr[i] : name);

                    filterList.add(new Filter(value, name));
                }
            }
            
            if (!filterList.isEmpty()) {
                filters.put(filterKey, filterList);
            }
        } catch (Exception e) {
            SpiderDebug.error("[XBPQ] parseFilterByConfig error: " + e.getMessage(), e);
        }
    }
    
    /**
     * 解析首页列表
     */
    private List<Vod> parseHomeList(String html) {
        List<Vod> list = new ArrayList<>();
        try {
            String listRule = config.getHomeListRule();
            String itemRule = config.getHomeItemRule();
            
            if (listRule.isEmpty() && itemRule.isEmpty()) {
                // 使用默认解析
                list = parseCateList(html, "");
                return list;
            }
            
            // 二次截取 - 委托给 XBPQParser
            String content = XBPQParser.parseHtml(html, rawConfig.optString("二次截取", ""));
            
            // 解析列表
            List<String> items = parseArray(content, listRule.isEmpty() ? itemRule : listRule);
            
            for (String item : items) {
                Vod vod = parseVodItem(item, "首页");
                if (vod != null) {
                    list.add(vod);
                }
            }
        } catch (Exception e) {
            SpiderDebug.error("[XBPQ] parseHomeList error: " + e.getMessage(), e);
        }
        return list;
    }
    
    /**
     * 解析分类列表
     */
    private List<Vod> parseCateList(String html, String tid) {
        List<Vod> list = new ArrayList<>();
        try {
            // 二次截取 - 委托给 XBPQParser
            String content = XBPQParser.parseHtml(html, rawConfig.optString("二次截取", ""));
            
            // 指定截取 - 委托给 XBPQParser
            content = XBPQParser.parseWithCategory(content, tid, tid);
            
            // 解析数组
            String arrayRule = config.getValue("数组", "分类列表数组规则");
            List<String> items = parseArray(content, arrayRule);
            
            for (String item : items) {
                Vod vod = parseVodItem(item, tid);
                if (vod != null) {
                    list.add(vod);
                }
            }
            
            // 倒序处理
            if (config.isReverseOrder()) {
                Collections.reverse(list);
            }
        } catch (Exception e) {
            SpiderDebug.error("[XBPQ] parseCateList error: " + e.getMessage(), e);
        }
        return list;
    }
    
    /**
     * 解析搜索列表
     */
    private List<Vod> parseSearchList(String html) {
        List<Vod> list = new ArrayList<>();
        try {
            // 二次截取 - 委托给 XBPQParser
            String searchSecondCut = config.getSearchSecondCut();
            String content = !searchSecondCut.isEmpty() ? XBPQParser.parseHtml(html, searchSecondCut) : html;
            
            // 解析数组
            String arrayRule = config.getSearchArray();
            if (arrayRule.isEmpty()) {
                arrayRule = config.getValue("数组");
            }
            List<String> items = parseArray(content, arrayRule);
            
            for (String item : items) {
                Vod vod = parseSearchVodItem(item);
                if (vod != null) {
                    list.add(vod);
                }
            }
        } catch (Exception e) {
            SpiderDebug.error("[XBPQ] parseSearchList error: " + e.getMessage(), e);
        }
        return list;
    }
    
    /**
     * 解析视频项
     */
    private Vod parseVodItem(String item, String tid) {
        try {
            String titleRule = config.getCateItemTitle();
            String linkRule = config.getCateItemLink();
            String imageRule = config.getCateItemImage();
            String subtitleRule = config.getCateItemSubtitle();
            
            // 如果分类配置为空，使用默认配置
            if (titleRule.isEmpty()) {
                titleRule = config.getValue("标题");
            }
            if (linkRule.isEmpty()) {
                linkRule = config.getValue("链接");
            }
            if (imageRule.isEmpty()) {
                imageRule = config.getValue("图片");
            }
            if (subtitleRule.isEmpty()) {
                subtitleRule = config.getValue("副标题");
            }
            
            // 指定截取 - 委托给 XBPQParser
            item = XBPQParser.parseWithCategory(item, tid, tid);
            
            String title = parseField(item, titleRule);
            String link = parseField(item, linkRule);
            String image = parseField(item, imageRule);
            String subtitle = parseField(item, subtitleRule);
            
            // 处理链接前缀后缀
            String linkPrefix = config.getCateItemLinkPrefix();
            String linkSuffix = config.getCateItemLinkSuffix();
            if (linkPrefix.isEmpty()) {
                linkPrefix = config.getValue("链接前缀");
            }
            if (linkSuffix.isEmpty()) {
                linkSuffix = config.getValue("链接后缀");
            }
            
            if (!linkPrefix.isEmpty()) {
                link = XBPQUtils.joinUrl(linkPrefix, link);
            }
            if (!linkSuffix.isEmpty()) {
                link = XBPQUtils.joinUrl(link, linkSuffix);
            }
            
            // 处理图片链接
            if (!image.isEmpty() && !image.startsWith(HTTP) && !image.startsWith(HTTPS)) {
                image = XBPQUtils.joinUrl(siteHost, image);
            }
            
            if (!title.isEmpty() && !link.isEmpty()) {
                return new Vod(link, title, image, subtitle);
            }
        } catch (Exception e) {
            SpiderDebug.error("[XBPQ] parseVodItem error: " + e.getMessage(), e);
        }
        return null;
    }
    
    /**
     * 解析搜索视频项
     */
    private Vod parseSearchVodItem(String item) {
        try {
            String titleRule = config.getSearchTitle();
            String linkRule = config.getSearchLinkRule();
            String imageRule = config.getSearchImage();
            String subtitleRule = config.getSearchSubtitle();
            
            // 如果搜索配置为空，使用默认配置
            if (titleRule.isEmpty()) {
                titleRule = config.getValue("标题");
            }
            if (linkRule.isEmpty()) {
                linkRule = config.getValue("链接");
            }
            if (imageRule.isEmpty()) {
                imageRule = config.getValue("图片");
            }
            if (subtitleRule.isEmpty()) {
                subtitleRule = config.getValue("副标题");
            }
            
            String title = parseField(item, titleRule);
            String link = parseField(item, linkRule);
            String image = parseField(item, imageRule);
            String subtitle = parseField(item, subtitleRule);
            
            // 处理链接前缀后缀
            String linkPrefix = config.getSearchLinkPrefix();
            String linkSuffix = config.getSearchLinkSuffix();
            if (linkPrefix.isEmpty()) {
                linkPrefix = config.getValue("搜索链接前缀", "链接前缀");
            }
            if (linkSuffix.isEmpty()) {
                linkSuffix = config.getValue("搜索链接后缀", "链接后缀");
            }
            
            // 搜索后缀（新增）- 添加到链接末尾的固定字符串
            String searchSuffix = config.getSearchSuffix();
            
            if (!linkPrefix.isEmpty()) {
                link = XBPQUtils.joinUrl(linkPrefix, link);
            }
            if (!linkSuffix.isEmpty()) {
                link = XBPQUtils.joinUrl(link, linkSuffix);
            }
            if (!searchSuffix.isEmpty()) {
                // 如果 searchSuffix 已自带连接符（?、&、/），直接拼接；
                // 否则根据 link 是否已含查询参数选择 ? 或 & 作为连接符，
                // 避免出现 link?a=1?page=2 这种重复问号的非法 URL。
                if (searchSuffix.startsWith(QUESTION_MARK) || searchSuffix.startsWith(AMPERSAND) || searchSuffix.startsWith(SLASH)) {
                    link = link + searchSuffix;
                } else {
                    String connector = link.contains(QUESTION_MARK) ? AMPERSAND : QUESTION_MARK;
                    link = link + connector + searchSuffix;
                }
            }
            
            // 处理图片链接
            if (!image.isEmpty() && !image.startsWith(HTTP) && !image.startsWith(HTTPS)) {
                image = XBPQUtils.joinUrl(siteHost, image);
            }
            
            if (!title.isEmpty() && !link.isEmpty()) {
                return new Vod(link, title, image, subtitle);
            }
        } catch (Exception e) {
            SpiderDebug.error("[XBPQ] parseSearchVodItem error: " + e.getMessage(), e);
        }
        return null;
    }
    
    /**
     * 解析详情
     */
    private Vod parseDetail(String html, String id) {
        try {
            Vod vod = new Vod();
            vod.setVodId(id);
            
            // 解析标题
            String titleRule = config.getValue("标题");
            String title = parseField(html, titleRule);
            if (title.isEmpty()) {
                title = id;
            }
            vod.setVodName(title);
            
            // 解析图片
            String imageRule = config.getValue("图片");
            String image = parseField(html, imageRule);
            if (!image.isEmpty() && !image.startsWith(HTTP) && !image.startsWith(HTTPS)) {
                image = XBPQUtils.joinUrl(siteHost, image);
            }
            vod.setVodPic(image);
            
            // 解析类型
            String typeRule = config.getTypeDetail();
            if (!typeRule.isEmpty()) {
                String type = parseField(html, typeRule);
                vod.setTypeName(type);
            }
            
            // 解析年代
            String yearRule = config.getYearDetail();
            if (!yearRule.isEmpty()) {
                String year = parseField(html, yearRule);
                vod.setVodYear(year);
            }
            
            // 解析地区
            String areaRule = config.getAreaDetail();
            if (!areaRule.isEmpty()) {
                String area = parseField(html, areaRule);
                vod.setVodArea(area);
            }
            
            // 解析演员
            String actorRule = config.getActorDetail();
            if (!actorRule.isEmpty()) {
                String actor = parseField(html, actorRule);
                vod.setVodActor(actor);
            }
            
            // 解析导演
            String directorRule = config.getDirectorDetail();
            if (!directorRule.isEmpty()) {
                String director = parseField(html, directorRule);
                vod.setVodDirector(director);
            }
            
            // 解析简介
            String introRule = config.getIntroDetail();
            if (!introRule.isEmpty()) {
                String intro = parseField(html, introRule);
                vod.setVodContent(intro);
            }
            
            // 解析状态（新增）
            String statusRule = config.getStatusDetail();
            if (!statusRule.isEmpty()) {
                String status = parseField(html, statusRule);
                // 状态信息可以添加到简介中或单独处理
                if (!status.isEmpty() && vod.getVodContent().isEmpty()) {
                    vod.setVodContent("状态: " + status);
                }
            }
            
            // 解析线路和播放列表
            parsePlayInfo(html, vod);
            
            return vod;
        } catch (Exception e) {
            SpiderDebug.error("[XBPQ] parseDetail error: " + e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * 解析播放信息
     */
    private void parsePlayInfo(String html, Vod vod) {
        try {
            List<String> playFromList = new ArrayList<>();
            List<String> playUrlList = new ArrayList<>();
            
            // 二次截取 - 委托给 XBPQParser
            String playSecondCut = config.getPlaySecondCut();
            String content = !playSecondCut.isEmpty() ? XBPQParser.parseHtml(html, playSecondCut) : html;
            
            // 解析线路
            String lineArray = config.getLineArray();
            String lineTitle = config.getLineTitle();
            
            // 线路二次截取（新增）
            String lineSecondCut = config.getLineSecondCut();
            String lineContent = content;
            if (!lineSecondCut.isEmpty()) {
                lineContent = XBPQParser.parseHtml(content, lineSecondCut);
            }
            
            if (!lineArray.isEmpty()) {
                // 解析线路数组
                List<String> lines = parseArray(lineContent, lineArray);
                
                for (String line : lines) {
                    String title = parseField(line, lineTitle);
                    
                    // 处理替换 - 委托给 XBPQParser
                    title = XBPQParser.applyReplace(title, lineArray);
                    
                    if (!title.isEmpty()) {
                        playFromList.add(title);
                    }
                }
            }
            
            // 解析播放列表
            String playArray = config.getPlayArray();
            String playListRule = config.getPlayListRule();
            String episodeListRule = config.getEpisodeListRule();
            String playTitle = config.getPlayTitle();
            String episodeTitle = config.getEpisodeTitle();
            String playLink = config.getPlayLink();
            String episodeLink = config.getEpisodeLink();
            
            // 播放链接前缀（新增）
            String playLinkPrefix = config.getPlayLinkPrefix();
            
            if (!playArray.isEmpty()) {
                // 解析播放数组
                List<String> playArrays = parseArray(content, playArray);
                
                for (String playItem : playArrays) {
                    // 解析选集列表
                    List<String> episodes = parseArray(playItem, episodeListRule.isEmpty() ? playListRule : episodeListRule);
                    
                    List<String> episodeUrls = new ArrayList<>();
                    for (String episode : episodes) {
                        String title = parseField(episode, episodeTitle.isEmpty() ? playTitle : episodeTitle);
                        String url = parseField(episode, episodeLink.isEmpty() ? playLink : episodeLink);
                        
                        // 处理播放链接前缀（新增）
                        if (!playLinkPrefix.isEmpty()) {
                            url = XBPQUtils.joinUrl(playLinkPrefix, url);
                        }
                        
                        // 处理链接前缀后缀
                        String prefix = config.getEpisodeLinkPrefix();
                        String suffix = config.getEpisodeLinkSuffix();
                        if (!prefix.isEmpty()) {
                            url = XBPQUtils.joinUrl(prefix, url);
                        }
                        if (!suffix.isEmpty()) {
                            url = XBPQUtils.joinUrl(url, suffix);
                        }
                        
                        // 处理替换 - 委托给 XBPQParser
                        title = XBPQParser.applyReplace(title, episodeListRule.isEmpty() ? playListRule : episodeListRule);
                        url = XBPQParser.applyReplace(url, episodeListRule.isEmpty() ? playListRule : episodeListRule);
                        
                        if (!title.isEmpty() && !url.isEmpty()) {
                            episodeUrls.add(title + DOLLAR + url);
                        } else if (!url.isEmpty()) {
                            episodeUrls.add(url);
                        }
                    }
                    
                    // 倒序处理
                    if (config.isReverseEpisode()) {
                        Collections.reverse(episodeUrls);
                    }
                    
                    if (!episodeUrls.isEmpty()) {
                        playUrlList.add(TextUtils.join(HASH, episodeUrls));
                    }
                }
            }
            
            // 设置播放信息
            if (!playFromList.isEmpty()) {
                vod.setVodPlayFrom(TextUtils.join(DOUBLE_DOLLAR, playFromList));
            }
            if (!playUrlList.isEmpty()) {
                vod.setVodPlayUrl(TextUtils.join(DOUBLE_DOLLAR, playUrlList));
            }
            
            // 如果没有解析到线路，但有播放列表，设置默认线路
            if (playFromList.isEmpty() && !playUrlList.isEmpty()) {
                vod.setVodPlayFrom("默认");
            }
        } catch (Exception e) {
            SpiderDebug.error("[XBPQ] parsePlayInfo error: " + e.getMessage(), e);
        }
    }

    // ==================== 核心解析工具方法 ====================
    
    /**
     * 解析数组 - 委托给 XBPQParser
     */
    private List<String> parseArray(String content, String rule) {
        if (TextUtils.isEmpty(content) || TextUtils.isEmpty(rule)) {
            if (debugMode) {
                SpiderDebug.log("[XBPQ-" + siteHost + "] parseArray: 参数为空 - content长度=" + (content == null ? "null" : content.length()) + ", rule=" + rule);
            }
            return new ArrayList<>();
        }

        try {
            if (debugMode) {
                SpiderDebug.log("[XBPQ-" + siteHost + "] parseArray 开始: rule=" + rule);
            }

            List<String> result = XBPQParser.parseArray(content, rule);

            if (debugMode) {
                SpiderDebug.log("[XBPQ-" + siteHost + "] parseArray 完成: 结果数量=" + result.size());
            }

            return result;
        } catch (Exception e) {
            SpiderDebug.error("[XBPQ-" + siteHost + "] parseArray error: " + e.getMessage(), e);
            return new ArrayList<>();
        }
    }
    
    /**
     * 解析字段 - 委托给 XBPQParser
     * 
     * 注意：XBPQParser.parseHtml 会自动判断解析模式（Jsoup 或字符串截取），
     * 不需要手动指定 isJsoup 参数。
     */
    private String parseField(String content, String rule) {
        if (TextUtils.isEmpty(content) || TextUtils.isEmpty(rule)) {
            if (debugMode) {
                SpiderDebug.log("[XBPQ-" + siteHost + "] parseField: 参数为空 - content长度=" + (content == null ? "null" : content.length()) + ", rule=" + rule);
            }
            return "";
        }

        try {
            if (debugMode) {
                SpiderDebug.log("[XBPQ-" + siteHost + "] parseField 开始: rule=" + rule);
            }

            String result = XBPQParser.parseHtml(content, rule);

            if (debugMode) {
                SpiderDebug.log("[XBPQ-" + siteHost + "] parseField 完成: 结果=" + (result.length() > 50 ? result.substring(0, 50) + "..." : result));
            }

            return result;
        } catch (Exception e) {
            SpiderDebug.error("[XBPQ-" + siteHost + "] parseField error: " + e.getMessage(), e);
            return "";
        }
    }
}