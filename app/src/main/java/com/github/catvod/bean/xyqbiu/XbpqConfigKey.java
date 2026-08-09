package com.github.catvod.spider;

/**
 * XBPQ 配置键常量。
 * <p>将所有 JSON 配置键集中管理，避免硬编码字符串散落在代码各处。</p>
 */
public final class XbpqConfigKey {

    private XbpqConfigKey() {}

    // ==================== 基础配置 ====================
    public static final String HOME_URL      = "主页url";
    public static final String NAME          = "站名";
    public static final String CHARSET       = "编码";
    public static final String COOKIE        = "cookie";
    public static final String REFERER       = "referer";
    public static final String REQUEST_HEADER= "请求头";

    // ==================== 播放控制 ====================
    public static final String DIRECT_PLAY   = "直接播放";
    public static final String NO_SNIFF      = "免嗅";
    public static final String FORCE_PARSE   = "强制解析";
    public static final String SNIFF_WORDS   = "嗅探词";
    public static final String FILTER_WORDS  = "过滤词";
    public static final String JUMP_PLAY_URL = "跳转播放链接";

    // ==================== 搜索配置 ====================
    public static final String SEARCH_URL       = "搜索url";
    public static final String SEARCH_MODE      = "搜索模式";
    public static final String SEARCH_SUFFIX    = "搜索后缀";
    public static final String SEARCH_HEADERS   = "搜索请求头参数";

    // ==================== 列表配置 ====================
    public static final String ARRAY_SELECTOR       = "数组";
    public static final String TITLE_SELECTOR       = "标题";
    public static final String PIC_SELECTOR         = "图片";
    public static final String LINK_SELECTOR        = "链接";
    public static final String SUBTITLE_SELECTOR    = "副标题";
    public static final String DESC_SELECTOR        = "简介";
    public static final String ARRAY_TWICE_PRE      = "数组二次截取";
    public static final String ARRAY_TWICE_SUF      = "数组二次截取后";

    // ==================== 分类配置 ====================
    public static final String FENLEI_URL       = "分类url";
    public static final String FENLEI           = "分类";
    public static final String CATEGORY_ARRAY   = "分类数组";
    public static final String CATEGORY_TITLE   = "分类标题";
    public static final String CATEGORY_ID      = "分类ID";
    public static final String CATEGORY_TWICE_PRE   = "分类二次截取前";
    public static final String CATEGORY_TWICE_SUF   = "分类二次截取后";

    // ==================== 详情配置 ====================
    public static final String TAB_ARRAY_SELECTOR       = "线路数组";
    public static final String TAB_TITLE_SELECTOR       = "线路标题";
    public static final String PLAY_ARRAY_SELECTOR      = "播放数组";
    public static final String PLAY_LIST_SELECTOR       = "播放列表";
    public static final String PLAY_TITLE_SELECTOR      = "播放标题";
    public static final String PLAY_LINK_SELECTOR       = "播放链接";
    public static final String PLAY_TWICE_PRE           = "播放二次截取";
    public static final String PLAY_TWICE_SUF           = "播放二次截取后";
    public static final String TAB_TWICE_PRE            = "线路二次截取";
    public static final String TAB_TWICE_SUF            = "线路二次截取后";

    // ==================== 搜索列表配置 ====================
    public static final String SEARCH_ARRAY_SELECTOR    = "搜索数组";
    public static final String SEARCH_PIC_SELECTOR      = "搜索图片";
    public static final String SEARCH_TITLE_SELECTOR    = "搜索标题";
    public static final String SEARCH_LINK_SELECTOR     = "搜索链接";
    public static final String SEARCH_SUBTITLE_SELECTOR = "搜索副标题";

    // ==================== 链接修饰 ====================
    public static final String LINK_PREFIX       = "链接前缀";
    public static final String LINK_SUFFIX       = "链接后缀";
    public static final String PLAY_LINK_PREFIX  = "播放链接前缀";
    public static final String PLAY_LINK_SUFFIX  = "播放链接后缀";

    // ==================== 图片代理 ====================
    public static final String IMAGE_PROXY      = "图片代理";
    public static final String IMAGE_PROXY_REGEX    = "图片代理正则";
    public static final String IMAGE_PROXY_REPLACE  = "图片代理替换";

    // ==================== 分页 ====================
    public static final String HOME_COUNT  = "首页";
    public static final String START_PAGE  = "起始页";

    // ==================== 二次截取 ====================
    public static final String TWICE_PRE  = "二次截取";
    public static final String TWICE_SUF  = "二次截取后";

    // ==================== 高级配置 ====================
    public static final String SECOND_LEVEL_DIR    = "二级目录";
    public static final String SECOND_LEVEL_ID     = "二级ID";
    public static final String SPECIAL_CATE_LINKS  = "特殊分类链接";
    public static final String PUBLISH_PAGE        = "发布页";
    public static final String DOMAIN_CONFIG       = "域名-c";
}
