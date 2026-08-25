package com.github.catvod.spider.xbpq.config;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.github.catvod.crawler.SpiderDebug;

/**
 * XBPQ规则配置管理类
 * <p>
 * 负责规则配置的加载与中文键名到英文键名的转换。
 * XBPQ 规则为平铺结构（非嵌套），本表即 com.github.catvod.spider.XBPQ 的
 * 键名标准，别名冲突时保留非空值。
 *
 * @author CatVodSpider Team
 * @version 2.1
 */
public class RuleConfig {

    /**
     * 中文键名到英文键名的映射表（XBPQ 键名标准）
     */
    public static final Map<String, String> CHINESE_KEY_MAP = new LinkedHashMap<>();

    static {
        // ===== 基础配置 =====
        CHINESE_KEY_MAP.put("主页url", "homeUrl");
        CHINESE_KEY_MAP.put("请求头", "header");
        CHINESE_KEY_MAP.put("头部集合", "header");
        CHINESE_KEY_MAP.put("编码", "encoding");
        CHINESE_KEY_MAP.put("起始页", "startpage");
        CHINESE_KEY_MAP.put("首页", "firstpage");
        CHINESE_KEY_MAP.put("UserAgent", "User-Agent");
        CHINESE_KEY_MAP.put("Referer", "Referer");

        // ===== 分类配置 =====
        CHINESE_KEY_MAP.put("分类url", "class_url");
        CHINESE_KEY_MAP.put("分类", "fenlei");
        CHINESE_KEY_MAP.put("分类名称", "class_name");
        CHINESE_KEY_MAP.put("分类值", "class_value");
        CHINESE_KEY_MAP.put("分类二次截取", "cat_twice");
        CHINESE_KEY_MAP.put("分类数组", "cat_array");
        CHINESE_KEY_MAP.put("分类标题", "cat_title");
        CHINESE_KEY_MAP.put("分类ID", "cat_id");
        CHINESE_KEY_MAP.put("分类筛选", "filter");
        CHINESE_KEY_MAP.put("筛选", "filter");

        // ===== 列表配置 =====
        CHINESE_KEY_MAP.put("数组", "list_array");
        CHINESE_KEY_MAP.put("列表数组", "list_array");
        CHINESE_KEY_MAP.put("二次截取", "list_twice");
        CHINESE_KEY_MAP.put("列表二次截取", "list_twice");
        CHINESE_KEY_MAP.put("标题", "list_name");
        CHINESE_KEY_MAP.put("列表标题", "list_name");
        CHINESE_KEY_MAP.put("链接", "list_id");
        CHINESE_KEY_MAP.put("列表链接", "list_id");
        CHINESE_KEY_MAP.put("图片", "list_pic");
        CHINESE_KEY_MAP.put("列表图片", "list_pic");
        CHINESE_KEY_MAP.put("副标题", "list_remarks");
        CHINESE_KEY_MAP.put("列表副标题", "list_remarks");
        CHINESE_KEY_MAP.put("链接前缀", "list_prefix");
        CHINESE_KEY_MAP.put("列表链接前缀", "list_prefix");
        CHINESE_KEY_MAP.put("链接后缀", "list_suffix");
        CHINESE_KEY_MAP.put("列表链接加后缀", "list_suffix");
        CHINESE_KEY_MAP.put("简介", "detail_content");

        // ===== 详情页配置 =====
        CHINESE_KEY_MAP.put("详情url", "detail_url");
        CHINESE_KEY_MAP.put("详情数组", "detail_array");
        CHINESE_KEY_MAP.put("详情二次截取", "detail_twice");
        CHINESE_KEY_MAP.put("导演", "detail_director");
        CHINESE_KEY_MAP.put("主演", "detail_actor");
        CHINESE_KEY_MAP.put("演员", "detail_actor");
        CHINESE_KEY_MAP.put("影片类型", "detail_type");
        CHINESE_KEY_MAP.put("类型", "detail_type");
        CHINESE_KEY_MAP.put("影片年代", "detail_year");
        CHINESE_KEY_MAP.put("年份", "detail_year");
        CHINESE_KEY_MAP.put("年代", "detail_year");
        CHINESE_KEY_MAP.put("影片地区", "detail_area");
        CHINESE_KEY_MAP.put("地区", "detail_area");
        CHINESE_KEY_MAP.put("状态", "detail_remarks");
        CHINESE_KEY_MAP.put("备注", "detail_remarks");
        CHINESE_KEY_MAP.put("剧情", "detail_content");
        CHINESE_KEY_MAP.put("内容", "detail_content");
        CHINESE_KEY_MAP.put("详情分隔符", "detail_label_split");
        CHINESE_KEY_MAP.put("详情合并字段", "detail_content_merge");

        // ===== 播放配置 =====
        CHINESE_KEY_MAP.put("线路数组", "from_array");
        CHINESE_KEY_MAP.put("线路标题", "from_title");
        CHINESE_KEY_MAP.put("线路二次截取", "line_twice");
        CHINESE_KEY_MAP.put("播放数组", "play_array");
        CHINESE_KEY_MAP.put("播放列表", "url_array");
        CHINESE_KEY_MAP.put("集数数组", "url_array");
        CHINESE_KEY_MAP.put("播放标题", "url_title");
        CHINESE_KEY_MAP.put("集数标题", "url_title");
        CHINESE_KEY_MAP.put("播放链接", "url_url");
        CHINESE_KEY_MAP.put("集数链接", "url_url");
        CHINESE_KEY_MAP.put("播放二次截取", "play_twice");
        CHINESE_KEY_MAP.put("播放链接前缀", "play_prefix");
        CHINESE_KEY_MAP.put("播放链接后缀", "play_suffix");
        CHINESE_KEY_MAP.put("跳转播放链接", "jump_url");
        CHINESE_KEY_MAP.put("跳转播放", "jump_url");
        CHINESE_KEY_MAP.put("直接播放", "force_play");
        CHINESE_KEY_MAP.put("播放请求头", "play_header");
        CHINESE_KEY_MAP.put("嗅探词", "video_format");
        CHINESE_KEY_MAP.put("剧集过滤", "episode_filter");
        CHINESE_KEY_MAP.put("空播放兜底", "empty_play_url");
        CHINESE_KEY_MAP.put("空播放线路名", "empty_play_from");

        // ===== 搜索配置 =====
        CHINESE_KEY_MAP.put("搜索url", "search_url");
        CHINESE_KEY_MAP.put("搜索模式", "search_mode");
        CHINESE_KEY_MAP.put("搜索数组", "search_array");
        CHINESE_KEY_MAP.put("搜索标题", "search_name");
        CHINESE_KEY_MAP.put("搜索图片", "search_pic");
        CHINESE_KEY_MAP.put("搜索链接", "search_id");
        CHINESE_KEY_MAP.put("搜索副标题", "search_remarks");
        CHINESE_KEY_MAP.put("搜索简介", "search_content");
        CHINESE_KEY_MAP.put("搜索二次截取", "search_twice");
        CHINESE_KEY_MAP.put("搜索请求头", "search_header");
        CHINESE_KEY_MAP.put("搜索后缀", "search_suffix");
        // 搜索链接后缀 与 搜索后缀 功能相同（见 XBPQ使用说明 4.6：搜索后缀=搜索链接后缀），
        // 映射至同一英文键，不新增字段。
        CHINESE_KEY_MAP.put("搜索链接后缀", "search_suffix");
        CHINESE_KEY_MAP.put("搜索链接前缀", "search_prefix");

        // ===== 其他配置 =====
        CHINESE_KEY_MAP.put("图片代理前缀", "baseEncodeUrl");
        CHINESE_KEY_MAP.put("图片代理", "PicNeedProxy");
        CHINESE_KEY_MAP.put("代理密钥", "secretKey");
        CHINESE_KEY_MAP.put("过滤词", "filter_word");
        CHINESE_KEY_MAP.put("倒序", "reverse");
        CHINESE_KEY_MAP.put("倒序播放", "reverse");
        CHINESE_KEY_MAP.put("免嗅", "manualVideoCheck");
        CHINESE_KEY_MAP.put("热门推荐", "hot_recommend");
        CHINESE_KEY_MAP.put("列表显示", "list_display");
        CHINESE_KEY_MAP.put("线路合并", "merge_lines");
        CHINESE_KEY_MAP.put("播放图片", "play_image");
        CHINESE_KEY_MAP.put("弹幕url", "danmuUrl");
        CHINESE_KEY_MAP.put("站名", "siteName");
        CHINESE_KEY_MAP.put("超时", "timeout");
        CHINESE_KEY_MAP.put("重试", "retry");
        CHINESE_KEY_MAP.put("重试次数", "retry");
    }

    /**
     * 将JSON中的中文字段名转换为英文Key
     * 支持递归处理嵌套对象和数组。
     * <p>
     * 冲突策略：多个中文别名映射同一英文键时，保留非空值（已有值为空则允许覆盖）。
     *
     * @param json 原始JSON对象
     * @return 转换后的JSON对象
     */
    public static JSONObject convertChineseKeys(JSONObject json) {
        if (json == null) return new JSONObject();

        try {
            // 收集要重命名的键（避免边遍历边修改）
            List<String[]> toRename = new ArrayList<>();
            Iterator<String> keys = json.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                String enKey = CHINESE_KEY_MAP.get(key);
                if (enKey != null) {
                    toRename.add(new String[]{key, enKey});
                }
            }

            // 执行重命名（非空优先）
            for (String[] pair : toRename) {
                String cnKey = pair[0];
                String enKey = pair[1];
                Object val = json.get(cnKey);
                json.remove(cnKey);
                if (!json.has(enKey) || isBlank(json.optString(enKey))) {
                    json.put(enKey, val);
                }
            }

            // 递归处理嵌套对象
            List<String> allKeys = new ArrayList<>();
            keys = json.keys();
            while (keys.hasNext()) allKeys.add(keys.next());

            for (String key : allKeys) {
                Object val = json.get(key);
                if (val instanceof JSONObject) {
                    json.put(key, convertChineseKeys((JSONObject) val));
                } else if (val instanceof org.json.JSONArray) {
                    org.json.JSONArray arr = (org.json.JSONArray) val;
                    for (int i = 0; i < arr.length(); i++) {
                        if (arr.get(i) instanceof JSONObject) {
                            arr.put(i, convertChineseKeys((JSONObject) arr.get(i)));
                        }
                    }
                }
            }
        } catch (Exception e) {
            SpiderDebug.log("convertChineseKeys error: " + e.getMessage());
        }

        return json;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty() || "空".equals(value) || "&&".equals(value);
    }

    /**
     * 获取规则值（处理空值占位："空"、单独的"&&"视为未配置）
     *
     * @param rule         规则对象
     * @param key          键名
     * @param defaultValue 默认值
     * @return 规则值，空值返回默认值
     */
    public static String getRuleVal(JSONObject rule, String key, String defaultValue) {
        if (rule == null) return defaultValue;
        String value = rule.optString(key, "");
        if (isBlank(value)) {
            return defaultValue;
        }
        return value;
    }

    public static String getRuleVal(JSONObject rule, String key) {
        return getRuleVal(rule, key, "");
    }

    /**
     * 是否在详情提取规则中开启了 CSS 模式。
     * <p>仅扫描详情提取器实际消费的键（含详情标题/图片沿用的 list_name/list_pic），
     * 键名与 CHINESE_KEY_MAP 产出的英文键严格对齐；不扫 search_url（URL模板）
     * 与播放线路键（播放列表提取器自行按 from_array/url_array 判定），
     * 避免线路 CSS 规则误触发详情 CSS 模式。
     */
    public static boolean isCssModeEnabled(JSONObject rule) {
        Set<String> extractKeys = new HashSet<>(Arrays.asList(
                "list_name", "list_pic",
                "detail_array", "detail_twice", "detail_content", "detail_director",
                "detail_actor", "detail_type", "detail_year", "detail_area", "detail_remarks"
        ));
        for (String key : extractKeys) {
            String val = rule.optString(key, "");
            if (val.isEmpty()) continue;
            if (CssRule.isCssRule((String) val)) return true;
        }
        return false;
    }
}
