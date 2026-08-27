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
     * 每个英文键仅保留一个最通用的中文别名，避免臃肿。
     */
    public static final Map<String, String> CHINESE_KEY_MAP = new LinkedHashMap<>();

    static {
        // ===== 基础配置 =====
        CHINESE_KEY_MAP.put("主页url", "homeUrl");
        CHINESE_KEY_MAP.put("请求头", "header");
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
        CHINESE_KEY_MAP.put("筛选", "filter");

        // ===== 列表配置 =====
        CHINESE_KEY_MAP.put("数组", "list_array");
        CHINESE_KEY_MAP.put("二次截取", "list_twice");
        CHINESE_KEY_MAP.put("标题", "list_name");
        CHINESE_KEY_MAP.put("链接", "list_id");
        CHINESE_KEY_MAP.put("图片", "list_pic");
        CHINESE_KEY_MAP.put("副标题", "list_remarks");
        CHINESE_KEY_MAP.put("链接前缀", "list_prefix");
        CHINESE_KEY_MAP.put("链接后缀", "list_suffix");
        CHINESE_KEY_MAP.put("简介", "detail_content");

        // ===== 详情页配置 =====
        CHINESE_KEY_MAP.put("详情url", "detail_url");
        CHINESE_KEY_MAP.put("详情数组", "detail_array");
        CHINESE_KEY_MAP.put("详情二次截取", "detail_twice");
        CHINESE_KEY_MAP.put("导演", "detail_director");
        CHINESE_KEY_MAP.put("主演", "detail_actor");
        CHINESE_KEY_MAP.put("类型", "detail_type");
        CHINESE_KEY_MAP.put("年份", "detail_year");
        CHINESE_KEY_MAP.put("地区", "detail_area");
        CHINESE_KEY_MAP.put("状态", "detail_remarks");
        CHINESE_KEY_MAP.put("详情分隔符", "detail_label_split");
        CHINESE_KEY_MAP.put("详情合并字段", "detail_content_merge");

        // ===== 播放配置 =====
        CHINESE_KEY_MAP.put("线路数组", "from_array");
        CHINESE_KEY_MAP.put("线路标题", "from_title");
        CHINESE_KEY_MAP.put("线路二次截取", "line_twice");
        CHINESE_KEY_MAP.put("播放数组", "play_array");
        CHINESE_KEY_MAP.put("播放列表", "url_array");
        CHINESE_KEY_MAP.put("播放标题", "url_title");
        CHINESE_KEY_MAP.put("播放链接", "url_url");
        CHINESE_KEY_MAP.put("播放二次截取", "play_twice");
        CHINESE_KEY_MAP.put("播放链接前缀", "play_prefix");
        CHINESE_KEY_MAP.put("播放链接后缀", "play_suffix");
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
        CHINESE_KEY_MAP.put("搜索链接前缀", "search_prefix");

        // ===== 其他配置 =====
        CHINESE_KEY_MAP.put("过滤词", "filter_word");
        CHINESE_KEY_MAP.put("倒序", "reverse");
        CHINESE_KEY_MAP.put("免嗅", "manualVideoCheck");
        CHINESE_KEY_MAP.put("热门推荐", "hot_recommend");
        CHINESE_KEY_MAP.put("线路合并", "merge_lines");
        CHINESE_KEY_MAP.put("弹幕url", "danmuUrl");
        CHINESE_KEY_MAP.put("超时", "timeout");
        CHINESE_KEY_MAP.put("重试次数", "retry");
        CHINESE_KEY_MAP.put("自动Maccms", "auto_maccms");
        // SSRF 防护开关：allow_internal=1 放行内网地址（仅供自测）
        CHINESE_KEY_MAP.put("允许内网", "allow_internal");
        // Maccms 播放器分析模式：Anal_MacPlayer=2 时优先用正则解析脚本块
        CHINESE_KEY_MAP.put("分析MacPlayer", "Anal_MacPlayer");

        // ===== 动态域名/变量链 =====
        CHINESE_KEY_MAP.put("域名-c", "dynamic_domain");
        CHINESE_KEY_MAP.put("主页url-c", "home_url_c");
        CHINESE_KEY_MAP.put("发布页", "publish_page");
        CHINESE_KEY_MAP.put("发布站-b", "publish_station_b");
        CHINESE_KEY_MAP.put("原始网址-b", "original_url_b");
        CHINESE_KEY_MAP.put("固定直链", "fixed_link");

        // ===== 二级目录/特殊分类 =====
        CHINESE_KEY_MAP.put("二级目录", "二级目录");
        CHINESE_KEY_MAP.put("二级ID", "二级ID");
        CHINESE_KEY_MAP.put("特殊分类链接", "特殊分类链接");

        // ===== 筛选参数 =====
        CHINESE_KEY_MAP.put("排序", "排序");
        CHINESE_KEY_MAP.put("时段", "时段");
        CHINESE_KEY_MAP.put("顺序", "顺序");
        // 修复：此处曾重复 put("类型", "筛选类型")，LinkedHashMap 后写覆盖，
        // 导致详情配置中 "类型"→"detail_type" 的映射丢失（详情页类型字段提取失效）。
        // 筛选类型字段已有独立中文别名（"筛选类型名称"/"筛选类型替换词"），无需占用 "类型"。
        // 筛选数据来源（远程/EXT/内置）
        CHINESE_KEY_MAP.put("筛选数据", "filterdata");
        // 动态筛选字段名
        CHINESE_KEY_MAP.put("筛选子分类名称", "fclass_name");
        CHINESE_KEY_MAP.put("筛选子分类替换词", "fclass_value");
        CHINESE_KEY_MAP.put("筛选类型名称", "fcatelog_name");
        CHINESE_KEY_MAP.put("筛选类型替换词", "fcatelog_value");
        CHINESE_KEY_MAP.put("筛选地区名称", "farea_name");
        CHINESE_KEY_MAP.put("筛选地区替换词", "farea_value");
        CHINESE_KEY_MAP.put("筛选年份名称", "fyear_name");
        CHINESE_KEY_MAP.put("筛选年份替换词", "fyear_value");
        CHINESE_KEY_MAP.put("筛选语言名称", "flang_name");
        CHINESE_KEY_MAP.put("筛选语言替换词", "flang_value");
        CHINESE_KEY_MAP.put("筛选排序名称", "fsort_name");
        CHINESE_KEY_MAP.put("筛选排序替换词", "fsort_value");
        // 分页起始页码
        // 注意：原 "分类起始页码" 曾映射到 firstpage，但与 "首页"→firstpage 严重冲突，
        // 且 firstpage 在分类中被当作"第1页替换串"使用，与"分类起始页码"语义不符。
        // 现改为独立键 cate_firstpage，避免键冲突导致配置互相污染。
        CHINESE_KEY_MAP.put("分类起始页码", "cate_firstpage");
        CHINESE_KEY_MAP.put("分类首页", "cate_firstpage");
        CHINESE_KEY_MAP.put("搜索起始页码", "sea_firstpage");
        // 分类 JSON 模式字段
        CHINESE_KEY_MAP.put("分类截取模式", "cat_mode");
        CHINESE_KEY_MAP.put("分类JSON列表", "catjsonlist");
        CHINESE_KEY_MAP.put("分类JSON名称", "catjsonname");
        CHINESE_KEY_MAP.put("分类JSONID", "catjsonid");
        CHINESE_KEY_MAP.put("分类JSON图片", "catjsonpic");
        CHINESE_KEY_MAP.put("分类JSON状态", "catjsonstitle");
        CHINESE_KEY_MAP.put("分类片单链接前缀", "cat_prefix");
        CHINESE_KEY_MAP.put("分类片单链接后缀", "cat_suffix");
        CHINESE_KEY_MAP.put("分类副标题", "cat_subtitle");
        // 选集链接前后缀
        CHINESE_KEY_MAP.put("选集链接加前缀", "epiurl_prefix");
        CHINESE_KEY_MAP.put("选集链接加后缀", "epiurl_suffix");
        // 视频过滤排除词
        CHINESE_KEY_MAP.put("视频过滤词", "video_filter");
        // 详情字段别名
        CHINESE_KEY_MAP.put("影片名称", "vod_name");
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

            // 执行重命名：中文名 → 英文名。
            // 修复：原实现仅当英文键不存在时才复制，若英文键存在但值为空白占位
            //（"空"/"&&"/空串），中文键的实际值会被丢弃（与 javadoc 冲突策略矛盾）。
            // 现按注释语义实现：英文键缺失或其值为空白占位时，允许中文值覆盖。
            for (String[] pair : toRename) {
                String cnKey = pair[0];
                String enKey = pair[1];
                if (json.has(cnKey) && (!json.has(enKey) || isBlank(json.optString(enKey, "")))) {
                    json.put(enKey, json.get(cnKey));
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
