package com.github.catvod.spider;

import android.content.Context;
import android.text.TextUtils;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Json;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * Douban Spider - 豆瓣影视数据源
 * 提供豆瓣影视热门列表和分类筛选功能
 *
 * <p>功能特性:</p>
 * <ul>
 *   <li>获取豆瓣实时热门影视列表 (电影/电视剧/综艺/动漫)</li>
 *   <li>支持分类筛选 (热门/榜单/筛选等多种分类)</li>
 *   <li>显示豆瓣评分作为副标题</li>
 *   <li>防盗链图片支持 (自动添加 Referer 和 User-Agent)</li>
 * </ul>
 *
 * <p>注意事项:</p>
 * <ul>
 *   <li>categoryContent 方法未实现 (原 smali 反编译失败)</li>
 *   <li>依赖 SharedPreferences 存储首页分类配置 (merge.B.a.L1 方法未实现)</li>
 *   <li>使用微信小程序 UA 和 Referer 绕过豆瓣 API 限制</li>
 * </ul>
 */
public class Douban extends Spider {

    // ==================== 常量定义 ====================

    /**
     * 豆瓣 API 基础 URL
     */
    private static final String DOUBAN_API_BASE = "http://api.douban.com/api/v2";

    /**
     * 豆瓣实时热门接口
     */
    private static final String DOUBAN_HOT_URL = DOUBAN_API_BASE + "/subject_collection/subject_real_time_hotest/items";

    /**
     * 豆瓣 API Key
     */
    private static final String DOUBAN_API_KEY = "0ac44ae016490db2204ce0a042db2916";

    /**
     * 豆瓣 Frodo API Host
     */
    private static final String DOUBAN_FRODO_HOST = "frodo.douban.com";

    /**
     * 微信小程序 Referer
     */
    private static final String WECHAT_MINI_PROGRAM_REFERER = "https://servicewechat.com/wx2f9b06c1de1ccfca/84/page-frame.html";

    /**
     * 微信小程序 User-Agent
     */
    private static final String WECHAT_MINI_PROGRAM_UA = "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/53.0.2785.143 Safari/537.36 MicroMessenger/7.0.9.501 NetType/WIFI MiniProgramEnv/Windows WindowsWechat";

    /**
     * PC User-Agent (用于图片请求)
     */
    private static final String PC_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36";

    /**
     *豆瓣 API Referer
     */
    private static final String DOUBAN_API_REFERER = "https://api.douban.com";

    /**
     * 默认分类类型 ID 列表
     */
    private static final String[] DEFAULT_CATEGORY_TYPES = {
        "hot_gaia", "tv_hot", "anime_hot", "show_hot",
        "movie", "tv", "rank_list_movie", "rank_list_tv"
    };

    /**
     * 默认分类名称列表
     */
    private static final String[] DEFAULT_CATEGORY_NAMES = {
        "热门电影", "热播剧集", "热门动漫", "热播综艺",
        "电影筛选", "电视筛选", "电影榜单", "电视剧榜单"
    };

    /**
     * SharedPreferences 配置键: 首页显示分类
     */
    private static final String CONFIG_KEY_HOME_PAGE = "homePage";

    /**
     * VOD ID 前缀: 用于标识豆瓣搜索源
     */
    private static final String VOD_ID_PREFIX = "msearch:";

    /**
     * VOD 副标题前缀: 评分
     */
    private static final String RATING_PREFIX = "评分:";

    // ==================== 字段定义 ====================

    /**
     * 扩展配置 URL
     * 用于存储外部配置 URL 或其他扩展参数
     */
    private String extendUrl;

    // ==================== 构造函数 ====================

    /**
     * 默认构造函数
     */
    public Douban() {
        this.extendUrl = "";
    }

    // ==================== Spider 标准方法实现 ====================

    /**
     * 初始化 Spider
     *
     * @param context Android Context
     * @param extend 扩展配置字符串 (存储到 extendUrl 字段)
     * @throws Exception 初始化异常
     */
    @Override
    public void init(Context context, String extend) throws Exception {
        this.extendUrl = extend != null ? extend : "";
    }

    /**
     * 获取首页内容
     *
     * <p>处理流程:</p>
     * <ul>
     *   <li>从 SharedPreferences 读取首页分类配置 (TODO: merge.B.a.L1 方法未实现)</li>
     *   <li>构建分类列表 (热门电影/热播剧集/热门动漫等)</li>
     *   <li>请求豆瓣实时热门 API</li>
     *   <li>解析影视列表并返回</li>
     * </ul>
     *
     * @param filter 是否启用过滤
     * @return JSON 格式的首页内容字符串
     * @throws Exception 请求或解析异常
     */
    @Override
    public String homeContent(boolean filter) throws Exception {
        // TODO: merge.B.a.c1() 方法未实现 (疑似配置初始化)
        // TODO: merge.B.a.L1("homePage", "") 方法未实现 (应从 SP 读取首页分类配置)
        String homePageConfig = ""; // 暂用空字符串替代

        // 解析首页分类配置 (逗号分隔的分类名称)
        HashSet<String> enabledCategories = new HashSet<>();
        if (!TextUtils.isEmpty(homePageConfig)) {
            enabledCategories.addAll(Arrays.asList(homePageConfig.split(",")));
        }

        // 构建分类列表
        ArrayList<Class> classes = new ArrayList<>();
        List<String> typeIds = Arrays.asList(DEFAULT_CATEGORY_TYPES);
        List<String> typeNames = Arrays.asList(DEFAULT_CATEGORY_NAMES);

        for (int i = 0; i < typeIds.size(); i++) {
            String typeName = typeNames.get(i);
            // 如果配置为空或包含该分类,则添加到列表
            if (enabledCategories.isEmpty() || enabledCategories.contains(typeName)) {
                classes.add(new Class(typeIds.get(i), typeName));
            }
        }

        // 构建热门 API URL (带 API Key)
        String hotUrl = DOUBAN_HOT_URL + "?apikey=" + DOUBAN_API_KEY;

        // 请求豆瓣 API
        String response = OkHttp.string(hotUrl, buildHeaders());

        // 解析响应 JSON
        JSONObject responseJson = new JSONObject(response);
        JSONArray itemsArray = responseJson.optJSONArray("subject_collection_items");

        // 解析影视列表
        ArrayList<Vod> vodList = parseVodList(itemsArray);

        // 构建返回结果
        // 如果 filter=true 且 extendUrl 不为空,则尝试请求 extendUrl 并解析为 filters
        JsonElement filters = filter && !TextUtils.isEmpty(this.extendUrl)
            ? Json.parse(OkHttp.string(this.extendUrl))
            : null;

        return filters != null
            ? Result.string(classes, vodList, filters)
            : Result.string(classes, vodList);
    }

    /**
     * 获取分类内容 (未实现)
     *
     * <p>状态: 方法未实现，抛出 UnsupportedOperationException</p>
     * <p>原因: 原 smali 代码反编译失败 (524 个指令单元被跳过)</p>
     *
     * @param tid 分类 ID
     * @param pg 页码
     * @param filter 是否启用过滤
     * @param extend 扩展参数
     * @return JSON 格式的分类内容字符串
     * @throws Exception 始终抛出 UnsupportedOperationException
     */
    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        // 原始 smali 代码反编译失败,方法体包含 524 个指令单元被跳过
        // JADX WARN 提示:
        // - Can't fix incorrect switch cases order
        // - Failed to find 'out' block for switch
        // - Failed to restore switch over string
        // - Removed duplicated region
        throw new UnsupportedOperationException("Method not decompiled: com.github.catvod.spider.Douban.categoryContent(String, String, boolean, HashMap):String");
    }

    // ==================== 辅助方法 ====================

    /**
     * 构建豆瓣 API 请求头
     *
     * <p>请求头包含:</p>
     * <ul>
     *   <li>Host: frodo.douban.com</li>
     *   <li>Connection: Keep-Alive</li>
     *   <li>Referer: 微信小程序页面</li>
     *   <li>User-Agent: 微信小程序 UA (绕过 API 限制)</li>
     * </ul>
     *
     * @return 包含豆瓣 API 必需请求头的 Map
     */
    private Map<String, String> buildHeaders() {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("Host", DOUBAN_FRODO_HOST);
        headers.put("Connection", "Keep-Alive");
        headers.put("Referer", WECHAT_MINI_PROGRAM_REFERER);
        headers.put("User-Agent", WECHAT_MINI_PROGRAM_UA);
        return headers;
    }

    /**
     * 解析豆瓣影视列表 JSON 数组
     *
     * <p>解析字段:</p>
     * <ul>
     *   <li>VOD ID: "msearch:" +豆瓣 ID</li>
     *   <li>标题: title 字段</li>
     *   <li>图片: pic.normal 字段 +防盗链后缀</li>
     *   <li>副标题: "评分:" + rating.value</li>
     * </ul>
     *
     * @param jsonArray豆瓣返回的影视列表 JSON 数组
     * @return 解析后的 Vod 对象列表
     * @throws JSONException JSON 解析异常
     */
    private static ArrayList<Vod> parseVodList(JSONArray jsonArray) throws JSONException {
        ArrayList<Vod> vodList = new ArrayList<>();

        for (int i = 0; i < jsonArray.length(); i++) {
            JSONObject itemJson = jsonArray.getJSONObject(i);

            // 构建 VOD ID (豆瓣 ID 加前缀)
            String vodId = VOD_ID_PREFIX + itemJson.optString("id");

            // 提取标题
            String vodName = itemJson.optString("title");

            // 提取图片 URL (带防盗链后缀)
            String vodPic;
            try {
                String normalPic = itemJson.getJSONObject("pic").optString("normal");
                // 添加 Referer 和 User-Agent 后缀防盗链
                vodPic = normalPic
                    + "@Referer=" + DOUBAN_API_REFERER
                    + "@User-Agent=" + PC_USER_AGENT;
            } catch (Exception e) {
                vodPic = "";
            }

            // 提取评分作为副标题
            String vodRemarks;
            try {
                String ratingValue = itemJson.getJSONObject("rating").optString("value");
                vodRemarks = RATING_PREFIX + ratingValue;
            } catch (Exception e) {
                vodRemarks = "";
            }
            vodList.add(new Vod(vodId, vodName, vodPic, vodRemarks));
        }

        return vodList;
    }
}