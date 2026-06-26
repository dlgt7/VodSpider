package com.github.catvod.spider.xbpq;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * XBPQConfig - XBPQ Spider 配置类
 * 
 * <p>强类型配置类，用于存储站点配置信息，支持中文配置键名。
 * 提供类型安全的配置访问和更好的可维护性。</p>
 * 
 * @version 2.0
 * @since 2024
 */
public class XBPQConfig {

    // ==================== 键别名映射 ====================
    
    /** 中文键 -> 英文键别名映射 */
    private static final Map<String, String> KEY_ALIASES = new HashMap<>();
    
    static {
        // 基础 URL 配置别名
        KEY_ALIASES.put("主页url", "homeUrl");
        KEY_ALIASES.put("首页推荐链接", "recommendUrl");
        KEY_ALIASES.put("分类url", "cateUrl");
        KEY_ALIASES.put("分类链接", "cateLink");
        KEY_ALIASES.put("分类名称", "cateName");
        KEY_ALIASES.put("分类名称替换词", "cateNameReplace");
        KEY_ALIASES.put("搜索url", "searchUrl");
        KEY_ALIASES.put("搜索链接", "searchLink");
        
        // 编码和请求头配置别名
        KEY_ALIASES.put("编码", "encoding");
        KEY_ALIASES.put("网页编码格式", "encoding");
        KEY_ALIASES.put("请求头参数", "userAgent");
        KEY_ALIASES.put("请求头", "requestHeader");
        KEY_ALIASES.put("头部集合", "requestHeader");
        KEY_ALIASES.put("图片是否需要代理", "imageProxy");
        KEY_ALIASES.put("图片代理", "imageProxy");
        KEY_ALIASES.put("备用地址", "backupUrl");
        
        // 首页配置别名
        KEY_ALIASES.put("是否开启获取首页数据", "enableHomeData");
        KEY_ALIASES.put("首页列表数组规则", "homeListRule");
        KEY_ALIASES.put("首页片单列表数组规则", "homeItemRule");
        KEY_ALIASES.put("首页片单是否Jsoup写法", "homeItemJsoup");
        KEY_ALIASES.put("首页片单标题", "homeItemTitle");
        KEY_ALIASES.put("首页片单链接", "homeItemLink");
        KEY_ALIASES.put("首页片单图片", "homeItemImage");
        KEY_ALIASES.put("首页片单副标题", "homeItemSubtitle");
        KEY_ALIASES.put("首页片单链接加前缀", "homeItemLinkPrefix");
        KEY_ALIASES.put("首页片单链接加后缀", "homeItemLinkSuffix");
        
        // 分类配置别名
        KEY_ALIASES.put("分类起始页码", "cateStartPage");
        KEY_ALIASES.put("起始页", "cateStartPage");
        KEY_ALIASES.put("分类截取模式", "cateCutMode");
        KEY_ALIASES.put("分类列表数组规则", "cateListRule");
        KEY_ALIASES.put("分类片单是否Jsoup写法", "cateItemJsoup");
        KEY_ALIASES.put("分类片单标题", "cateItemTitle");
        KEY_ALIASES.put("分类片单链接", "cateItemLink");
        KEY_ALIASES.put("分类片单图片", "cateItemImage");
        KEY_ALIASES.put("分类片单副标题", "cateItemSubtitle");
        KEY_ALIASES.put("分类片单链接加前缀", "cateItemLinkPrefix");
        KEY_ALIASES.put("分类片单链接加后缀", "cateItemLinkSuffix");
        KEY_ALIASES.put("分类数组", "cateArray");
        KEY_ALIASES.put("分类标题", "cateTitle");
        KEY_ALIASES.put("分类ID", "cateId");
        KEY_ALIASES.put("分类二次截取", "cateSecondCut");
        
        // 搜索配置别名
        KEY_ALIASES.put("搜索请求头参数", "searchHeader");
        KEY_ALIASES.put("搜索截取模式", "searchCutMode");
        KEY_ALIASES.put("搜索列表数组规则", "searchListRule");
        KEY_ALIASES.put("搜索二次截取", "searchSecondCut");
        KEY_ALIASES.put("搜索片单是否Jsoup写法", "searchItemJsoup");
        KEY_ALIASES.put("搜索数组", "searchArray");
        KEY_ALIASES.put("搜索图片", "searchImage");
        KEY_ALIASES.put("搜索片单图片", "searchImage");
        KEY_ALIASES.put("搜索标题", "searchTitle");
        KEY_ALIASES.put("搜索片单标题", "searchTitle");
        KEY_ALIASES.put("搜索副标题", "searchSubtitle");
        KEY_ALIASES.put("搜索片单链接", "searchLinkRule");
        KEY_ALIASES.put("搜索片单链接加前缀", "searchLinkPrefix");
        KEY_ALIASES.put("搜索片单链接加后缀", "searchLinkSuffix");
        KEY_ALIASES.put("POST请求数据", "postData");
        
        // 详情配置别名
        KEY_ALIASES.put("详情是否Jsoup写法", "detailJsoup");
        KEY_ALIASES.put("类型详情", "typeDetail");
        KEY_ALIASES.put("影片类型", "typeDetail");
        KEY_ALIASES.put("年代详情", "yearDetail");
        KEY_ALIASES.put("影片年代", "yearDetail");
        KEY_ALIASES.put("地区详情", "areaDetail");
        KEY_ALIASES.put("影片地区", "areaDetail");
        KEY_ALIASES.put("演员详情", "actorDetail");
        KEY_ALIASES.put("主演", "actorDetail");
        KEY_ALIASES.put("导演详情", "directorDetail");
        KEY_ALIASES.put("导演", "directorDetail");
        KEY_ALIASES.put("简介", "intro");
        KEY_ALIASES.put("简介详情", "introDetail");
        
        // 线路配置别名
        KEY_ALIASES.put("线路数组", "lineArray");
        KEY_ALIASES.put("线路列表数组规则", "lineListRule");
        KEY_ALIASES.put("线路标题", "lineTitle");
        
        // 播放配置别名
        KEY_ALIASES.put("播放数组", "playArray");
        KEY_ALIASES.put("播放列表数组规则", "playListRule");
        KEY_ALIASES.put("播放列表", "playList");
        KEY_ALIASES.put("播放二次截取", "playSecondCut");
        KEY_ALIASES.put("选集列表数组规则", "episodeListRule");
        KEY_ALIASES.put("播放标题", "playTitle");
        KEY_ALIASES.put("选集标题", "episodeTitle");
        KEY_ALIASES.put("播放链接", "playLink");
        KEY_ALIASES.put("选集链接", "episodeLink");
        KEY_ALIASES.put("选集标题链接是否Jsoup写法", "episodeJsoup");
        KEY_ALIASES.put("是否反转选集序列", "reverseEpisode");
        KEY_ALIASES.put("倒序", "reverseEpisode");
        KEY_ALIASES.put("选集链接加前缀", "episodeLinkPrefix");
        KEY_ALIASES.put("选集链接加后缀", "episodeLinkSuffix");
        KEY_ALIASES.put("链接是否直接播放", "directPlay");
        KEY_ALIASES.put("直接播放", "directPlay");
        KEY_ALIASES.put("直接播放链接加前缀", "directPlayPrefix");
        KEY_ALIASES.put("直接播放链接加后缀", "directPlaySuffix");
        KEY_ALIASES.put("直接播放直链视频请求头", "directPlayHeader");
        KEY_ALIASES.put("播放请求头", "directPlayHeader");
        
        // 嗅探配置别名
        KEY_ALIASES.put("嗅探词", "sniffWord");
        KEY_ALIASES.put("手动嗅探视频链接关键词", "sniffWord");
        KEY_ALIASES.put("强制嗅探词", "forceSniffWord");
        KEY_ALIASES.put("是否开启手动嗅探", "manualSniff");
        KEY_ALIASES.put("手动嗅探视频链接过滤词", "manualSniffFilter");
        KEY_ALIASES.put("页面代理", "pageProxy");
        
        // 其他配置别名
        KEY_ALIASES.put("分析MacPlayer", "analyzeMacPlayer");
        KEY_ALIASES.put("图片", "imageRule");
        KEY_ALIASES.put("标题", "titleRule");
        KEY_ALIASES.put("副标题", "subtitleRule");
        KEY_ALIASES.put("数组", "arrayRule");
        KEY_ALIASES.put(" 数组", "arrayRule");
        KEY_ALIASES.put("链接", "linkRule");
        KEY_ALIASES.put("链接前缀", "linkPrefix");
        KEY_ALIASES.put("链接后缀", "linkSuffix");
        KEY_ALIASES.put("规则名", "ruleName");
        KEY_ALIASES.put("规则作者", "ruleAuthor");
        KEY_ALIASES.put("筛选数据", "filterData");
        KEY_ALIASES.put("筛选", "filterData");
        KEY_ALIASES.put("分类", "cateName");
        
        // 调试配置别名
        KEY_ALIASES.put("调试", "debugMode");
        
        // 新增配置别名 - 站点信息
        KEY_ALIASES.put("站名", "siteName");
        KEY_ALIASES.put("作者", "ruleAuthor");
        
        // 新增配置别名 - 首页配置
        KEY_ALIASES.put("首页", "homePageCount");
        
        // 新增配置别名 - 过滤配置
        KEY_ALIASES.put("过滤词", "filterWord");
        KEY_ALIASES.put("滤词", "filterWord");
        
        // 新增配置别名 - 详情配置
        KEY_ALIASES.put("状态", "statusDetail");
        
        // 新增配置别名 - 播放配置
        KEY_ALIASES.put("跳转播放链接", "jumpPlayLink");
        KEY_ALIASES.put("跳转解析", "jumpParse");
        KEY_ALIASES.put("播放链接前缀", "playLinkPrefix");
        KEY_ALIASES.put("免嗅", "noSniff");
        
        // 新增配置别名 - 搜索配置
        KEY_ALIASES.put("搜索模式", "searchMode");
        KEY_ALIASES.put("搜索后缀", "searchSuffix");
        KEY_ALIASES.put("搜索请求头", "searchHeader");
        
        // 新增配置别名 - 分类配置
        KEY_ALIASES.put("分类值", "cateValue");
        
        // 新增配置别名 - 线路配置
        KEY_ALIASES.put("线路二次截取", "lineSecondCut");
        
        // 新增配置别名 - 筛选配置
        KEY_ALIASES.put("类型", "typeFilter");
        KEY_ALIASES.put("类型值", "typeValue");
        KEY_ALIASES.put("地区", "areaFilter");
        KEY_ALIASES.put("地区值", "areaValue");
        KEY_ALIASES.put("剧情", "plotFilter");
        KEY_ALIASES.put("剧情值", "plotValue");
        KEY_ALIASES.put("年份", "yearFilter");
        KEY_ALIASES.put("年份值", "yearValue");
        KEY_ALIASES.put("语言", "langFilter");
        KEY_ALIASES.put("语言值", "langValue");
        KEY_ALIASES.put("字母", "letterFilter");
        KEY_ALIASES.put("字母值", "letterValue");
        KEY_ALIASES.put("排序", "sortFilter");
        KEY_ALIASES.put("排序值", "sortValue");
    }

    // ==================== 基础 URL 配置 ====================
    
    /** 主页 URL */
    private String homeUrl;
    
    /** 首页推荐链接 */
    private String recommendUrl;
    
    /** 分类 URL */
    private String cateUrl;
    
    /** 分类链接 */
    private String cateLink;
    
    /** 分类名称 */
    private String cateName;
    
    /** 分类名称替换词 */
    private String cateNameReplace;
    
    /** 搜索 URL */
    private String searchUrl;
    
    /** 搜索链接 */
    private String searchLink;
    
    // ==================== 编码和请求头配置 ====================
    
    /** 网页编码格式 */
    private String encoding;
    
    /** 请求头参数 */
    private String userAgent;
    
    /** 请求头 */
    private String requestHeader;
    
    /** 图片是否需要代理 */
    private boolean imageProxy;
    
    /** 备用地址 */
    private String backupUrl;
    
    // ==================== 首页配置 ====================
    
    /** 是否开启获取首页数据 */
    private boolean enableHomeData;
    
    /** 首页列表数组规则 */
    private String homeListRule;
    
    /** 首页片单列表数组规则 */
    private String homeItemRule;
    
    /** 首页片单是否 Jsoup 写法 */
    private boolean homeItemJsoup;
    
    /** 首页片单标题 */
    private String homeItemTitle;
    
    /** 首页片单链接 */
    private String homeItemLink;
    
    /** 首页片单图片 */
    private String homeItemImage;
    
    /** 首页片单副标题 */
    private String homeItemSubtitle;
    
    /** 首页片单链接加前缀 */
    private String homeItemLinkPrefix;
    
    /** 首页片单链接加后缀 */
    private String homeItemLinkSuffix;
    
    // ==================== 分类配置 ====================
    
    /** 分类起始页码 */
    private int cateStartPage;
    
    /** 分类截取模式 */
    private int cateCutMode;
    
    /** 分类列表数组规则 */
    private String cateListRule;
    
    /** 分类片单是否 Jsoup 写法 */
    private boolean cateItemJsoup;
    
    /** 分类片单标题 */
    private String cateItemTitle;
    
    /** 分类片单链接 */
    private String cateItemLink;
    
    /** 分类片单图片 */
    private String cateItemImage;
    
    /** 分类片单副标题 */
    private String cateItemSubtitle;
    
    /** 分类片单链接加前缀 */
    private String cateItemLinkPrefix;
    
    /** 分类片单链接加后缀 */
    private String cateItemLinkSuffix;
    
    /** 分类数组 */
    private String cateArray;
    
    /** 分类标题 */
    private String cateTitle;
    
    /** 分类 ID */
    private String cateId;
    
    /** 分类二次截取 */
    private String cateSecondCut;
    
    // ==================== 搜索配置 ====================
    
    /** 搜索请求头参数 */
    private String searchHeader;
    
    /** 搜索截取模式 */
    private int searchCutMode;
    
    /** 搜索列表数组规则 */
    private String searchListRule;
    
    /** 搜索二次截取 */
    private String searchSecondCut;
    
    /** 搜索片单是否 Jsoup 写法 */
    private boolean searchItemJsoup;
    
    /** 搜索数组 */
    private String searchArray;
    
    /** 搜索图片 */
    private String searchImage;
    
    /** 搜索标题 */
    private String searchTitle;
    
    /** 搜索副标题 */
    private String searchSubtitle;
    
    /** 搜索链接 */
    private String searchLinkRule;
    
    /** 搜索片单链接加前缀 */
    private String searchLinkPrefix;
    
    /** 搜索片单链接加后缀 */
    private String searchLinkSuffix;
    
    /** POST 请求数据 */
    private String postData;
    
    // ==================== 详情配置 ====================
    
    /** 详情是否 Jsoup 写法 */
    private boolean detailJsoup;
    
    /** 类型详情 */
    private String typeDetail;
    
    /** 年代详情 */
    private String yearDetail;
    
    /** 地区详情 */
    private String areaDetail;
    
    /** 演员详情 */
    private String actorDetail;
    
    /** 导演详情 */
    private String directorDetail;
    
    /** 简介 */
    private String intro;
    
    /** 简介详情 */
    private String introDetail;
    
    // ==================== 线路配置 ====================
    
    /** 线路数组 */
    private String lineArray;
    
    /** 线路列表数组规则 */
    private String lineListRule;
    
    /** 线路标题 */
    private String lineTitle;
    
    // ==================== 播放配置 ====================
    
    /** 播放数组 */
    private String playArray;
    
    /** 播放列表数组规则 */
    private String playListRule;
    
    /** 播放列表 */
    private String playList;
    
    /** 播放二次截取 */
    private String playSecondCut;
    
    /** 选集列表数组规则 */
    private String episodeListRule;
    
    /** 播放标题 */
    private String playTitle;
    
    /** 选集标题 */
    private String episodeTitle;
    
    /** 播放链接 */
    private String playLink;
    
    /** 选集链接 */
    private String episodeLink;
    
    /** 选集标题链接是否 Jsoup 写法 */
    private boolean episodeJsoup;
    
    /** 是否反转选集序列 */
    private boolean reverseEpisode;
    
    /** 选集链接加前缀 */
    private String episodeLinkPrefix;
    
    /** 选集链接加后缀 */
    private String episodeLinkSuffix;
    
    /** 链接是否直接播放 */
    private boolean directPlay;
    
    /** 直接播放链接加前缀 */
    private String directPlayPrefix;
    
    /** 直接播放链接加后缀 */
    private String directPlaySuffix;
    
    /** 直接播放直链视频请求头 */
    private String directPlayHeader;
    
    // ==================== 嗅探配置 ====================
    
    /** 嗅探词 */
    private String sniffWord;
    
    /** 强制嗅探词 */
    private String forceSniffWord;
    
    /** 是否开启手动嗅探 */
    private boolean manualSniff;
    
    /** 手动嗅探视频链接关键词 */
    private String manualSniffKeyword;
    
    /** 手动嗅探视频链接过滤词 */
    private String manualSniffFilter;
    
    /** 页面代理 */
    private String pageProxy;
    
    // ==================== 其他配置 ====================
    
    /** 起始页 */
    private int startPage;
    
    /** 倒序 */
    private boolean reverseOrder;
    
    /** 分析 MacPlayer */
    private boolean analyzeMacPlayer;
    
    /** 图片 */
    private String imageRule;
    
    /** 标题 */
    private String titleRule;
    
    /** 副标题 */
    private String subtitleRule;
    
    /** 数组 */
    private String arrayRule;
    
    /** 链接 */
    private String linkRule;
    
    /** 链接前缀 */
    private String linkPrefix;
    
    /** 链接后缀 */
    private String linkSuffix;
    
    /** 规则名 */
    private String ruleName;
    
    /** 规则作者 */
    private String ruleAuthor;
    
    /** 筛选数据 */
    private JSONObject filterData;
    
    /** 原始 JSON 配置 */
    private JSONObject raw;
    
    /** 规则映射 */
    private Map<String, String> rules;
    
    // ==================== 新增配置字段 ====================
    
    /** 站点名称 */
    private String siteName;
    
    /** 首页数据数量 */
    private int homePageCount;
    
    /** 过滤词 */
    private String filterWord;
    
    /** 状态详情 */
    private String statusDetail;
    
    /** 跳转播放链接 */
    private String jumpPlayLink;
    
    /** 跳转解析 */
    private String jumpParse;
    
    /** 播放链接前缀 */
    private String playLinkPrefix;
    
    /** 免嗅探 */
    private boolean noSniff;
    
    /** 搜索模式 */
    private int searchMode;
    
    /** 搜索后缀 */
    private String searchSuffix;
    
    /** 分类值 */
    private String cateValue;
    
    /** 线路二次截取 */
    private String lineSecondCut;
    
    /** 类型筛选 */
    private String typeFilter;
    
    /** 类型筛选值 */
    private String typeValue;
    
    /** 地区筛选 */
    private String areaFilter;
    
    /** 地区筛选值 */
    private String areaValue;
    
    /** 剧情筛选 */
    private String plotFilter;
    
    /** 剧情筛选值 */
    private String plotValue;
    
    /** 年份筛选 */
    private String yearFilter;
    
    /** 年份筛选值 */
    private String yearValue;
    
    /** 语言筛选 */
    private String langFilter;
    
    /** 语言筛选值 */
    private String langValue;
    
    /** 字母筛选 */
    private String letterFilter;
    
    /** 字母筛选值 */
    private String letterValue;
    
    /** 排序筛选 */
    private String sortFilter;
    
    /** 排序筛选值 */
    private String sortValue;
    
    // ==================== 构造函数 ====================
    
    public XBPQConfig() {
        this.rules = new HashMap<>();
        this.cateStartPage = 1;
        this.startPage = 1;
        this.encoding = "UTF-8";
        this.userAgent = "PC_UA";
    }
    
    public XBPQConfig(JSONObject json) {
        this();
        this.raw = json;
        parseFromJson(json);
    }
    
    // ==================== 解析方法 ====================
    
    private void parseFromJson(JSONObject json) {
        if (json == null) return;
        
        // 基础 URL 配置
        homeUrl = getString(json, "", "主页url", "首页推荐链接", "homeUrl");
        recommendUrl = getString(json, "", "首页推荐链接");
        cateUrl = getString(json, "", "分类url", "分类链接", "cateUrl");
        cateLink = getString(json, "", "分类链接");
        cateName = getString(json, "", "分类名称", "分类");
        cateNameReplace = getString(json, "", "分类名称替换词");
        searchUrl = getString(json, "", "搜索url", "搜索链接", "searchUrl");
        searchLink = getString(json, "", "搜索链接");

        // 编码和请求头配置
        encoding = getString(json, "UTF-8", "编码", "网页编码格式");
        userAgent = getString(json, "PC_UA", "请求头参数", "请求头");
        requestHeader = getString(json, "", "请求头");
        imageProxy = getBoolean(json, false, "图片是否需要代理", "图片代理");
        backupUrl = getString(json, "", "备用地址");
        
        // 首页配置
        enableHomeData = getBoolean(json, "是否开启获取首页数据", true);
        homeListRule = getString(json, "", "首页列表数组规则");
        homeItemRule = getString(json, "", "首页片单列表数组规则");
        homeItemJsoup = getBoolean(json, "首页片单是否Jsoup写法", true);
        homeItemTitle = getString(json, "", "首页片单标题");
        homeItemLink = getString(json, "", "首页片单链接");
        homeItemImage = getString(json, "", "首页片单图片");
        homeItemSubtitle = getString(json, "", "首页片单副标题");
        homeItemLinkPrefix = getString(json, "", "首页片单链接加前缀");
        homeItemLinkSuffix = getString(json, "", "首页片单链接加后缀");
        
        // 分类配置
        cateStartPage = getInt(json, 1, "分类起始页码", "起始页");
        cateCutMode = getInt(json, "分类截取模式", 1);
        cateListRule = getString(json, "", "分类列表数组规则");
        cateItemJsoup = getBoolean(json, "分类片单是否Jsoup写法", true);
        cateItemTitle = getString(json, "", "分类片单标题");
        cateItemLink = getString(json, "", "分类片单链接");
        cateItemImage = getString(json, "", "分类片单图片");
        cateItemSubtitle = getString(json, "", "分类片单副标题");
        cateItemLinkPrefix = getString(json, "", "分类片单链接加前缀");
        cateItemLinkSuffix = getString(json, "", "分类片单链接加后缀");
        cateArray = getString(json, "", "分类数组");
        cateTitle = getString(json, "", "分类标题");
        cateId = getString(json, "", "分类ID");
        cateSecondCut = getString(json, "", "分类二次截取");
        
        // 搜索配置
        searchHeader = getString(json, "", "搜索请求头参数", "搜索请求头");
        searchCutMode = getInt(json, "搜索截取模式", 1);
        searchListRule = getString(json, "", "搜索列表数组规则");
        searchSecondCut = getString(json, "", "搜索二次截取");
        searchItemJsoup = getBoolean(json, "搜索片单是否Jsoup写法", true);
        searchArray = getString(json, "", "搜索数组");
        searchImage = getString(json, "", "搜索图片", "搜索片单图片");
        searchTitle = getString(json, "", "搜索标题", "搜索片单标题");
        searchSubtitle = getString(json, "", "搜索副标题");
        searchLinkRule = getString(json, "", "搜索链接", "搜索片单链接");
        searchLinkPrefix = getString(json, "", "搜索片单链接加前缀");
        searchLinkSuffix = getString(json, "", "搜索片单链接加后缀");
        postData = getString(json, "", "POST请求数据");
        
        // 详情配置
        detailJsoup = getBoolean(json, "详情是否Jsoup写法", false);
        typeDetail = getString(json, "", "类型详情");
        yearDetail = getString(json, "", "年代详情");
        areaDetail = getString(json, "", "地区详情");
        actorDetail = getString(json, "", "演员详情");
        directorDetail = getString(json, "", "导演详情");
        intro = getString(json, "", "简介");
        introDetail = getString(json, "", "简介详情");

        // 线路配置
        lineArray = getString(json, "", "线路数组");
        lineListRule = getString(json, "", "线路列表数组规则");
        lineTitle = getString(json, "", "线路标题");

        // 播放配置
        playArray = getString(json, "", "播放数组");
        playListRule = getString(json, "", "播放列表数组规则");
        playList = getString(json, "", "播放列表");
        playSecondCut = getString(json, "", "播放二次截取");
        episodeListRule = getString(json, "", "选集列表数组规则");
        playTitle = getString(json, "", "播放标题");
        episodeTitle = getString(json, "", "选集标题");
        playLink = getString(json, "", "播放链接");
        episodeLink = getString(json, "", "选集链接");
        episodeJsoup = getBoolean(json, "选集标题链接是否Jsoup写法", true);
        reverseEpisode = getBoolean(json, false, "是否反转选集序列", "倒序");
        episodeLinkPrefix = getString(json, "", "选集链接加前缀");
        episodeLinkSuffix = getString(json, "", "选集链接加后缀");
        directPlay = getBoolean(json, "链接是否直接播放", false);
        directPlayPrefix = getString(json, "", "直接播放链接加前缀");
        directPlaySuffix = getString(json, "", "直接播放链接加后缀");
        directPlayHeader = getString(json, "", "直接播放直链视频请求头");
        
        // 嗅探配置
        sniffWord = getString(json, "", "嗅探词", "手动嗅探视频链接关键词");
        forceSniffWord = getString(json, "", "强制嗅探词");
        manualSniff = getBoolean(json, "是否开启手动嗅探", false);
        manualSniffKeyword = getString(json, "", "手动嗅探视频链接关键词");
        manualSniffFilter = getString(json, "", "手动嗅探视频链接过滤词");
        pageProxy = getString(json, "", "页面代理");

        // 其他配置
        reverseOrder = getBoolean(json, "倒序", false);
        analyzeMacPlayer = getBoolean(json, "分析MacPlayer", false);
        imageRule = getString(json, "", "图片");
        titleRule = getString(json, "", "标题");
        subtitleRule = getString(json, "", "副标题");
        arrayRule = getString(json, "", "数组", " 数组");
        linkRule = getString(json, "", "链接");
        linkPrefix = getString(json, "", "链接前缀");
        linkSuffix = getString(json, "", "链接后缀");
        ruleName = getString(json, "", "规则名");
        ruleAuthor = getString(json, "", "规则作者");
        filterData = json.optJSONObject("筛选数据");

        // 新增配置解析
        siteName = getString(json, "", "站名");
        homePageCount = getInt(json, "首页", 0);
        filterWord = getString(json, "", "过滤词", "滤词");
        statusDetail = getString(json, "", "状态");
        jumpPlayLink = getString(json, "", "跳转播放链接");
        jumpParse = getString(json, "", "跳转解析");
        playLinkPrefix = getString(json, "", "播放链接前缀");
        noSniff = getBoolean(json, "免嗅", false);
        searchMode = getInt(json, "搜索模式", 0);
        searchSuffix = getString(json, "", "搜索后缀");
        cateValue = getString(json, "", "分类值");
        lineSecondCut = getString(json, "", "线路二次截取");

        // 筛选配置解析
        typeFilter = getString(json, "", "类型");
        typeValue = getString(json, "", "类型值");
        areaFilter = getString(json, "", "地区");
        areaValue = getString(json, "", "地区值");
        plotFilter = getString(json, "", "剧情");
        plotValue = getString(json, "", "剧情值");
        yearFilter = getString(json, "", "年份");
        yearValue = getString(json, "", "年份值");
        langFilter = getString(json, "", "语言");
        langValue = getString(json, "", "语言值");
        letterFilter = getString(json, "", "字母");
        letterValue = getString(json, "", "字母值");
        sortFilter = getString(json, "", "排序");
        sortValue = getString(json, "", "排序值");
        
        // 解析所有规则到映射
        java.util.Iterator<String> keyIter = json.keys();
        while (keyIter.hasNext()) {
            String key = keyIter.next();
            String value = json.optString(key, "");
            if (value != null && !value.isEmpty()) {
                rules.put(key, value);
            }
        }
    }
    
    // ==================== 辅助方法 ====================
    
    /**
     * 从 JSONObject 获取字符串值（支持多键 fallback 和默认值）
     *
     * <p>按顺序遍历 keys，返回第一个存在的键对应的值；如果所有键都不存在，返回 defaultValue。</p>
     *
     * @param json JSON 对象
     * @param defaultValue 默认值（当所有键都不存在时返回）
     * @param keys 键名列表（按优先级顺序）
     * @return 字符串值
     */
    private String getString(JSONObject json, String defaultValue, String... keys) {
        if (json == null || keys == null || keys.length == 0) {
            return defaultValue;
        }

        for (String key : keys) {
            if (json.has(key)) {
                return json.optString(key, defaultValue);
            }
        }

        return defaultValue;
    }
    
    /**
     * 从 JSONObject 获取整数值（带默认值，支持多键 fallback）
     *
     * @param json JSON 对象
     * @param defaultValue 默认值
     * @param keys 键名列表
     * @return 整数值
     */
    private int getInt(JSONObject json, int defaultValue, String... keys) {
        if (json == null || keys == null || keys.length == 0) {
            return defaultValue;
        }
        for (String key : keys) {
            if (json.has(key)) {
                return json.optInt(key, defaultValue);
            }
        }
        return defaultValue;
    }
    
    /**
     * 从 JSONObject 获取整数值（带默认值）
     * 
     * @param json JSON 对象
     * @param key 键名
     * @param defaultValue 默认值
     * @return 整数值
     */
    private int getInt(JSONObject json, String key, int defaultValue) {
        if (json == null || key == null) {
            return defaultValue;
        }
        return json.has(key) ? json.optInt(key, defaultValue) : defaultValue;
    }
    
    /**
     * 从 JSONObject 获取布尔值（带默认值，支持多键 fallback）
     *
     * @param json JSON 对象
     * @param defaultValue 默认值
     * @param keys 键名列表
     * @return 布尔值
     */
    private boolean getBoolean(JSONObject json, boolean defaultValue, String... keys) {
        if (json == null || keys == null || keys.length == 0) {
            return defaultValue;
        }
        for (String key : keys) {
            if (json.has(key)) {
                String value = json.optString(key, defaultValue ? "1" : "0");
                return "1".equals(value) || "true".equalsIgnoreCase(value);
            }
        }
        return defaultValue;
    }
    
    /**
     * 从 JSONObject 获取布尔值（带默认值）
     * 
     * @param json JSON 对象
     * @param key 键名
     * @param defaultValue 默认值
     * @return 布尔值
     */
    private boolean getBoolean(JSONObject json, String key, boolean defaultValue) {
        if (json == null || key == null) {
            return defaultValue;
        }
        if (json.has(key)) {
            String value = json.optString(key, defaultValue ? "1" : "0");
            return "1".equals(value) || "true".equalsIgnoreCase(value);
        }
        return defaultValue;
    }
    
    /**
     * 获取配置值（支持多键 fallback）
     */
    public String getValue(String... keys) {
        for (String key : keys) {
            // 先尝试直接获取
            if (rules.containsKey(key)) {
                return rules.get(key);
            }
            // 再尝试通过别名获取
            String aliasKey = KEY_ALIASES.get(key);
            if (aliasKey != null && rules.containsKey(aliasKey)) {
                return rules.get(aliasKey);
            }
        }
        return "";
    }
    
    /**
     * 获取配置值（带默认值，支持别名）
     * 
     * <p>优化版：复用 getValue 方法，减少重复代码。</p>
     * 
     * @param key 配置键名（支持中文键）
     * @param defaultValue 默认值
     * @return 配置值字符串
     */
    public String getString(String key, String defaultValue) {
        String value = getValue(key);
        return value.isEmpty() ? defaultValue : value;
    }
    
    /**
     * 获取配置值（带默认值，支持别名）
     * 
     * @param key 配置键名（支持中文键）
     * @param defaultValue 默认值
     * @return 配置值整数
     */
    public int getInt(String key, int defaultValue) {
        String value = getString(key, null);
        if (value != null && !value.isEmpty()) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }
    
    /**
     * 获取配置值（带默认值，支持别名）
     * 
     * @param key 配置键名（支持中文键）
     * @param defaultValue 默认值
     * @return 配置值布尔
     */
    public boolean getBoolean(String key, boolean defaultValue) {
        String value = getString(key, null);
        if (value != null && !value.isEmpty()) {
            return "1".equals(value) || "true".equals(value);
        }
        return defaultValue;
    }
    
    /**
     * 获取分类列表
     * 
     * <p>支持两种分类配置格式，按优先级处理：</p>
     * 
     * <h3>优先级 1：cateName + cateNameReplace 格式（&分隔符）</h3>
     * <pre>
     * cateName: "电影&电视剧&综艺"
     * cateNameReplace: "1&2&3"
     * 结果: [("电影", "1"), ("电视剧", "2"), ("综艺", "3")]
     * </pre>
     * 
     * <h3>优先级 2：cateName 格式（#和$分隔符）</h3>
     * <pre>
     * cateName: "电影$1#电视剧$2#综艺$3"
     * 结果: [("电影", "1"), ("电视剧", "2"), ("综艺", "3")]
     * </pre>
     * 
     * <p><b>注意：</b>如果同时配置了 cateName 和 cateNameReplace，
     * 则优先使用第一种格式（&分隔符）。</p>
     * 
     * @return 分类列表，每个元素为 [分类名称, 分类ID]
     */
    public List<String[]> getCategoryList() {
        List<String[]> list = new ArrayList<>();
        
        // 优先级 1：使用 cateName + cateNameReplace（&分隔符格式）
        if (cateName != null && !cateName.isEmpty() && cateNameReplace != null && !cateNameReplace.isEmpty()) {
            String[] names = cateName.split("&");
            String[] ids = cateNameReplace.split("&");
            for (int i = 0; i < names.length && i < ids.length; i++) {
                list.add(new String[]{names[i], ids[i]});
            }
        } 
        // 优先级 2：使用 cateName（#和$分隔符格式）
        else if (cateName != null && !cateName.isEmpty()) {
            String[] items = cateName.split("#");
            for (String item : items) {
                String[] parts = item.split("\\$");
                if (parts.length >= 2) {
                    list.add(new String[]{parts[0], parts[1]});
                }
            }
        }
        
        return list;
    }
    
    // ==================== Getter 方法 ====================
    
    public String getHomeUrl() { return homeUrl != null ? homeUrl : ""; }
    public String getCateUrl() { return cateUrl != null ? cateUrl : ""; }
    public String getSearchUrl() { return searchUrl != null ? searchUrl : ""; }
    public String getEncoding() { return encoding != null ? encoding : "UTF-8"; }
    public String getUserAgent() { return userAgent != null ? userAgent : "PC_UA"; }
    public String getSniffWord() { return sniffWord != null ? sniffWord : ""; }
    public String getForceSniffWord() { return forceSniffWord != null ? forceSniffWord : ""; }
    public String getPlayArray() { return playArray != null ? playArray : ""; }
    public String getPlayList() { return playList != null ? playList : ""; }
    public String getPlaySecondCut() { return playSecondCut != null ? playSecondCut : ""; }
    public String getPlayTitle() { return playTitle != null ? playTitle : ""; }
    public String getPlayLink() { return playLink != null ? playLink : ""; }
    public String getLineArray() { return lineArray != null ? lineArray : ""; }
    public String getLineTitle() { return lineTitle != null ? lineTitle : ""; }
    public String getImageRule() { return imageRule != null ? imageRule : ""; }
    public String getTitleRule() { return titleRule != null ? titleRule : ""; }
    public String getSubtitleRule() { return subtitleRule != null ? subtitleRule : ""; }
    public String getArrayRule() { return arrayRule != null ? arrayRule : ""; }
    public String getIntro() { return intro != null ? intro : ""; }
    public String getSearchArray() { return searchArray != null ? searchArray : ""; }
    public String getSearchImage() { return searchImage != null ? searchImage : ""; }
    public String getSearchTitle() { return searchTitle != null ? searchTitle : ""; }
    public String getSearchLinkRule() { return searchLinkRule != null ? searchLinkRule : ""; }
    public String getCateArray() { return cateArray != null ? cateArray : ""; }
    public String getCateTitle() { return cateTitle != null ? cateTitle : ""; }
    public String getCateId() { return cateId != null ? cateId : ""; }
    public String getCateSecondCut() { return cateSecondCut != null ? cateSecondCut : ""; }
    public int getCateStartPage() { return cateStartPage; }
    public int getStartPage() { return startPage; }
    public boolean isReverseOrder() { return reverseOrder; }
    public boolean isReverseEpisode() { return reverseEpisode; }
    public boolean isDirectPlay() { return directPlay; }
    public boolean isManualSniff() { return manualSniff; }
    public String getManualSniffKeyword() { return manualSniffKeyword != null ? manualSniffKeyword : ""; }
    public String getManualSniffFilter() { return manualSniffFilter != null ? manualSniffFilter : ""; }
    public String getPageProxy() { return pageProxy != null ? pageProxy : ""; }
    public String getEpisodeLinkPrefix() { return episodeLinkPrefix != null ? episodeLinkPrefix : ""; }
    public String getEpisodeLinkSuffix() { return episodeLinkSuffix != null ? episodeLinkSuffix : ""; }
    public String getDirectPlayPrefix() { return directPlayPrefix != null ? directPlayPrefix : ""; }
    public String getDirectPlaySuffix() { return directPlaySuffix != null ? directPlaySuffix : ""; }
    public String getDirectPlayHeader() { return directPlayHeader != null ? directPlayHeader : ""; }
    public JSONObject getRaw() { return raw; }
    public Map<String, String> getRules() { return rules; }
    public String getPostData() { return postData != null ? postData : ""; }
    public String getSearchHeader() { return searchHeader != null ? searchHeader : ""; }
    public String getSearchSubtitle() { return searchSubtitle != null ? searchSubtitle : ""; }
    public String getSearchLinkPrefix() { return searchLinkPrefix != null ? searchLinkPrefix : ""; }
    public String getSearchLinkSuffix() { return searchLinkSuffix != null ? searchLinkSuffix : ""; }
    public String getCateItemTitle() { return cateItemTitle != null ? cateItemTitle : ""; }
    public String getCateItemLink() { return cateItemLink != null ? cateItemLink : ""; }
    public String getCateItemImage() { return cateItemImage != null ? cateItemImage : ""; }
    public String getCateItemSubtitle() { return cateItemSubtitle != null ? cateItemSubtitle : ""; }
    public String getCateItemLinkPrefix() { return cateItemLinkPrefix != null ? cateItemLinkPrefix : ""; }
    public String getCateItemLinkSuffix() { return cateItemLinkSuffix != null ? cateItemLinkSuffix : ""; }
    public String getCateListRule() { return cateListRule != null ? cateListRule : ""; }
    public boolean isCateItemJsoup() { return cateItemJsoup; }
    public String getHomeItemTitle() { return homeItemTitle != null ? homeItemTitle : ""; }
    public String getHomeItemLink() { return homeItemLink != null ? homeItemLink : ""; }
    public String getHomeItemImage() { return homeItemImage != null ? homeItemImage : ""; }
    public String getHomeItemSubtitle() { return homeItemSubtitle != null ? homeItemSubtitle : ""; }
    public String getHomeItemLinkPrefix() { return homeItemLinkPrefix != null ? homeItemLinkPrefix : ""; }
    public String getHomeItemLinkSuffix() { return homeItemLinkSuffix != null ? homeItemLinkSuffix : ""; }
    public String getHomeListRule() { return homeListRule != null ? homeListRule : ""; }
    public String getHomeItemRule() { return homeItemRule != null ? homeItemRule : ""; }
    public boolean isHomeItemJsoup() { return homeItemJsoup; }
    public boolean isEnableHomeData() { return enableHomeData; }
    public String getRecommendUrl() { return recommendUrl != null ? recommendUrl : ""; }
    public String getCateLink() { return cateLink != null ? cateLink : ""; }
    public String getCateName() { return cateName != null ? cateName : ""; }
    public String getCateNameReplace() { return cateNameReplace != null ? cateNameReplace : ""; }
    public String getSearchLink() { return searchLink != null ? searchLink : ""; }
    public String getRequestHeader() { return requestHeader != null ? requestHeader : ""; }
    public boolean isImageProxy() { return imageProxy; }
    public String getBackupUrl() { return backupUrl != null ? backupUrl : ""; }
    public int getCateCutMode() { return cateCutMode; }
    public int getSearchCutMode() { return searchCutMode; }
    public String getSearchListRule() { return searchListRule != null ? searchListRule : ""; }
    public String getSearchSecondCut() { return searchSecondCut != null ? searchSecondCut : ""; }
    public boolean isSearchItemJsoup() { return searchItemJsoup; }
    public boolean isDetailJsoup() { return detailJsoup; }
    public String getTypeDetail() { return typeDetail != null ? typeDetail : ""; }
    public String getYearDetail() { return yearDetail != null ? yearDetail : ""; }
    public String getAreaDetail() { return areaDetail != null ? areaDetail : ""; }
    public String getActorDetail() { return actorDetail != null ? actorDetail : ""; }
    public String getDirectorDetail() { return directorDetail != null ? directorDetail : ""; }
    public String getIntroDetail() { return introDetail != null ? introDetail : ""; }
    public String getLineListRule() { return lineListRule != null ? lineListRule : ""; }
    public String getPlayListRule() { return playListRule != null ? playListRule : ""; }
    public String getEpisodeListRule() { return episodeListRule != null ? episodeListRule : ""; }
    public String getEpisodeTitle() { return episodeTitle != null ? episodeTitle : ""; }
    public String getEpisodeLink() { return episodeLink != null ? episodeLink : ""; }
    public boolean isEpisodeJsoup() { return episodeJsoup; }
    public boolean isAnalyzeMacPlayer() { return analyzeMacPlayer; }
    public String getLinkRule() { return linkRule != null ? linkRule : ""; }
    public String getLinkPrefix() { return linkPrefix != null ? linkPrefix : ""; }
    public String getLinkSuffix() { return linkSuffix != null ? linkSuffix : ""; }
    public String getRuleName() { return ruleName != null ? ruleName : ""; }
    public String getRuleAuthor() { return ruleAuthor != null ? ruleAuthor : ""; }
    public JSONObject getFilterData() { return filterData; }
    
    // ==================== 新增 Getter 方法 ====================
    
    public String getSiteName() { return siteName != null ? siteName : ""; }
    public int getHomePageCount() { return homePageCount; }
    public String getFilterWord() { return filterWord != null ? filterWord : ""; }
    public String getStatusDetail() { return statusDetail != null ? statusDetail : ""; }
    public String getJumpPlayLink() { return jumpPlayLink != null ? jumpPlayLink : ""; }
    public String getJumpParse() { return jumpParse != null ? jumpParse : ""; }
    public String getPlayLinkPrefix() { return playLinkPrefix != null ? playLinkPrefix : ""; }
    public boolean isNoSniff() { return noSniff; }
    public int getSearchMode() { return searchMode; }
    public String getSearchSuffix() { return searchSuffix != null ? searchSuffix : ""; }
    public String getCateValue() { return cateValue != null ? cateValue : ""; }
    public String getLineSecondCut() { return lineSecondCut != null ? lineSecondCut : ""; }
    
    // 筛选配置 Getter 方法
    public String getTypeFilter() { return typeFilter != null ? typeFilter : ""; }
    public String getTypeValue() { return typeValue != null ? typeValue : ""; }
    public String getAreaFilter() { return areaFilter != null ? areaFilter : ""; }
    public String getAreaValue() { return areaValue != null ? areaValue : ""; }
    public String getPlotFilter() { return plotFilter != null ? plotFilter : ""; }
    public String getPlotValue() { return plotValue != null ? plotValue : ""; }
    public String getYearFilter() { return yearFilter != null ? yearFilter : ""; }
    public String getYearValue() { return yearValue != null ? yearValue : ""; }
    public String getLangFilter() { return langFilter != null ? langFilter : ""; }
    public String getLangValue() { return langValue != null ? langValue : ""; }
    public String getLetterFilter() { return letterFilter != null ? letterFilter : ""; }
    public String getLetterValue() { return letterValue != null ? letterValue : ""; }
    public String getSortFilter() { return sortFilter != null ? sortFilter : ""; }
    public String getSortValue() { return sortValue != null ? sortValue : ""; }
}