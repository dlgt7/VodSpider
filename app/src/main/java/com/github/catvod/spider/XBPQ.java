package com.github.catvod.spider;

import android.content.Context;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Pair;

import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.utils.Util;
import com.github.catvod.net.OkHttp;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class XBPQ extends Spider {

    protected String ext = null;
    public JSONObject rule = null;
    // 默认的视频类型
    private ArrayList<String> videoFormatList = new ArrayList<>(Arrays.asList(".m3u8", ".mp4", ".mpeg", ".flv", ".mkv"));
    // 倒序开关
    private boolean reverseOrder = false;

    // 中文字段名映射到英文key
    private static final HashMap<String, String> CHINESE_KEY_MAP = new HashMap<>();
    static {
        // 基础配置
        CHINESE_KEY_MAP.put("主页url", "homeUrl");
        CHINESE_KEY_MAP.put("请求头", "header");
        CHINESE_KEY_MAP.put("编码", "encoding");
        CHINESE_KEY_MAP.put("起始页", "firstpage");
        CHINESE_KEY_MAP.put("首页", "firstpage");
        CHINESE_KEY_MAP.put("UserAgent", "User-Agent");
        CHINESE_KEY_MAP.put("Referer", "Referer");
        // 分类相关
        CHINESE_KEY_MAP.put("分类url", "class_url");
        CHINESE_KEY_MAP.put("分类", "fenlei");
        CHINESE_KEY_MAP.put("分类名称", "class_name");
        CHINESE_KEY_MAP.put("分类值", "class_value");
        CHINESE_KEY_MAP.put("分类二次截取", "cat_twice");
        CHINESE_KEY_MAP.put("分类数组", "cat_array");
        CHINESE_KEY_MAP.put("分类标题", "cat_title");
        CHINESE_KEY_MAP.put("分类ID", "cat_id");
        CHINESE_KEY_MAP.put("分类筛选", "filter");
        CHINESE_KEY_MAP.put("排序", "sort_type");
        CHINESE_KEY_MAP.put("排序值", "sort_value");
        // 列表 / 数组
        CHINESE_KEY_MAP.put("数组", "list_array");
        CHINESE_KEY_MAP.put("二次截取", "list_twice");
        CHINESE_KEY_MAP.put("图片", "list_pic");
        CHINESE_KEY_MAP.put("标题", "list_name");
        CHINESE_KEY_MAP.put("链接", "list_id");
        CHINESE_KEY_MAP.put("副标题", "list_remarks");
        CHINESE_KEY_MAP.put("简介", "detail_content");
        CHINESE_KEY_MAP.put("导演", "detail_director");
        CHINESE_KEY_MAP.put("主演", "detail_actor");
        CHINESE_KEY_MAP.put("影片类型", "detail_type");
        CHINESE_KEY_MAP.put("影片年代", "detail_year");
        CHINESE_KEY_MAP.put("影片地区", "detail_area");
        CHINESE_KEY_MAP.put("状态", "detail_remarks");
        // 播放相关
        CHINESE_KEY_MAP.put("线路数组", "from_array");
        CHINESE_KEY_MAP.put("线路标题", "from_title");
        CHINESE_KEY_MAP.put("播放数组", "play_array");
        CHINESE_KEY_MAP.put("播放列表", "url_array");
        CHINESE_KEY_MAP.put("播放标题", "url_title");
        CHINESE_KEY_MAP.put("播放链接", "url_url");
        CHINESE_KEY_MAP.put("跳转播放链接", "jump_url");
        CHINESE_KEY_MAP.put("直接播放", "force_play");
        CHINESE_KEY_MAP.put("播放请求头", "play_header");
        CHINESE_KEY_MAP.put("嗅探词", "video_format");
        // 搜索
        CHINESE_KEY_MAP.put("搜索url", "search_url");
        CHINESE_KEY_MAP.put("搜索模式", "search_mode");
        CHINESE_KEY_MAP.put("搜索数组", "search_array");
        CHINESE_KEY_MAP.put("搜索标题", "search_name");
        CHINESE_KEY_MAP.put("搜索图片", "search_pic");
        CHINESE_KEY_MAP.put("搜索链接", "search_id");
        CHINESE_KEY_MAP.put("搜索副标题", "search_remarks");
        CHINESE_KEY_MAP.put("搜索二次截取", "search_twice");
        CHINESE_KEY_MAP.put("搜索请求头", "search_header");
        CHINESE_KEY_MAP.put("搜索后缀", "search_suffix");
        CHINESE_KEY_MAP.put("线路二次截取", "line_second_cut");
        CHINESE_KEY_MAP.put("多线二次截取", "multi_line_twice");
        CHINESE_KEY_MAP.put("多线数组", "multi_line_array");
        CHINESE_KEY_MAP.put("多线链接", "multi_line_url");
        CHINESE_KEY_MAP.put("多线链接前缀", "multi_line_prefix");
        CHINESE_KEY_MAP.put("多线链接后缀", "multi_line_suffix");
        // 其他
        CHINESE_KEY_MAP.put("图片代理", "PicNeedProxy");
        CHINESE_KEY_MAP.put("过滤词", "filter_word");
        CHINESE_KEY_MAP.put("倒序", "reverse");
        CHINESE_KEY_MAP.put("免嗅", "manualVideoCheck");
    }


    protected final int base64Flag = Base64.DEFAULT | Base64.URL_SAFE | Base64.NO_WRAP;
    // 一定是正确的分类名称，用来帮助定位分类列表，猜cateManual
    protected final ArrayList<String> cateManuals = new ArrayList<>(Arrays.asList("电影", "剧集", "电视剧", "连续剧", "综艺", "动漫"));
    // 无效的分类名，用来过滤cateManual
    protected final ArrayList<String> invalidCateNames = new ArrayList<>(Arrays.asList("更多","下载", "首页", "资讯", "留言", "导航", "专题", "短视频", "热榜", "排行", "追剧","更新","APP", "直播", "label", "Netflix", "最新", "最近更新"));
    // 详情页 影片信息相关字段，猜详情页信息时用
    protected final ArrayList<String> detailItemNames = new ArrayList<>(Arrays.asList("导演", "主演", "演员", "地区", "类型", "年份", "年代"));
    protected final ArrayList<String> detailItemKeys = new ArrayList<>(Arrays.asList("vod_director", "vod_actor", "vod_actor", "vod_area", "type_name", "vod_year", "vod_year"));
    protected String splitFlag = ""; // 分段标志,猜cateManual时用

    // html标签查找时用到的辅助类
    protected class HtmlMatchInfo {
        public String group0; // 正则表达式匹配到的字符串
        /**
         * 一般用来放href中的内容
         */
        public String group1; //
        public String group2; //
        public String diff;   // 两个匹配结果比较group1得到的不同部分
        public int startPos;    // 正则匹配到的起始位置
        public int endPos;      // 正则匹配到的结束位置
        public ArrayList<Integer> uploads;  // 祖先结节的索引
        public int matchedUpNodePos = -1;   // 与其他HtmlMatchInfo最匹配的祖先节点位置
        public int diffStartIndex;   // 不同那部分数据的开始位置
        public int diffEndIndex; // 不同那部分数据的结束位置

        public void init(Matcher m) {
            this.group0 = m.group(0);
            if (m.groupCount() > 0)
                this.group1 = m.group(1);
            if (m.groupCount() > 1)
                this.group2 = m.group(2);
            this.startPos = m.start(0);
            this.endPos = m.end(0);
        }

        // 通过比较两个group1的不同部分，不同部分的内容以splitFlag中的字符为开始或结束位置
        public boolean findDiffStr(HtmlMatchInfo rhs, String splitFlag) {
            int len = Math.min(group1.length(), rhs.group1.length());
            // 找不同字符的开始位置
            for (int i =0; i < len; ++i){
                char a = group1.charAt(i);
                char b = rhs.group1.charAt(i);
                if(a== b &&  splitFlag.indexOf (a) != -1) {
                    diffStartIndex = i+1;
                    rhs.diffStartIndex = i+1;
                }
                if(a != b) break;
            }

            // 找不同字符的结束位置
            diffEndIndex = group1.length();
            rhs.diffEndIndex = rhs.group1.length();
            for (int i =1; i < len; ++i){
                char a = group1.charAt(group1.length()-i);
                char b = rhs.group1.charAt(rhs.group1.length()-i);
                if(a== b && splitFlag.indexOf (a) != -1) {
                    diffEndIndex = group1.length()-i;
                    rhs.diffEndIndex = rhs.group1.length()-i;
                }
                if(a != b) break;
            }
            if(this.diff == null || this.diff.isEmpty() && diffStartIndex < diffEndIndex) {
                diff = group1.substring(diffStartIndex, diffEndIndex);
            }else{
                if( diffEndIndex < diffStartIndex || !diff.equals(group1.substring(diffStartIndex, diffEndIndex))){
                    return false;
                }
            }
            if(rhs.diffStartIndex < rhs.diffEndIndex) {
                rhs.diff = rhs.group1.substring(rhs.diffStartIndex, rhs.diffEndIndex);
            }
            return true;
        }

        // 判断 rhs 与当前对象是有相同的祖先节点
        boolean hasSameUpNode(HtmlMatchInfo rhs) {
            if (rhs.uploads.size() != this.uploads.size()) return false;
            for (int i = 0; i < uploads.size(); ++i) {
                if (uploads.get(i).intValue() != rhs.uploads.get(i).intValue()) continue;
                if (matchedUpNodePos == -1 || uploads.get(i).intValue() == matchedUpNodePos) {
                    matchedUpNodePos = uploads.get(i).intValue();
                    rhs.matchedUpNodePos = uploads.get(i).intValue();
                    return true;
                }
                return false;
            }
            return false;
        }
    }

    public void init(Context context, String extend) throws Exception {
        super.init(context, extend);
        this.ext = extend;
    }

    // 将JSON中的中文字段名转换为英文key
    protected JSONObject convertChineseKeys(JSONObject json) {
        try {
            // 先收集要重命名的键，避免边遍历边修改导致跳键
            java.util.ArrayList<String> toRename = new java.util.ArrayList<>();
            Iterator<String> keys = json.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                if (CHINESE_KEY_MAP.containsKey(key)) {
                    toRename.add(key);
                }
            }
            for (String key : toRename) {
                String enKey = CHINESE_KEY_MAP.get(key);
                Object val = json.get(key);
                json.remove(key);
                json.put(enKey, val);
            }
            // 递归处理嵌套的JSONObject和JSONArray
            java.util.ArrayList<String> allKeys = new java.util.ArrayList<>();
            keys = json.keys();
            while (keys.hasNext()) allKeys.add(keys.next());
            for (String key : allKeys) {
                Object val = json.get(key);
                if (val instanceof JSONObject) {
                    json.put(key, convertChineseKeys((JSONObject) val));
                } else if (val instanceof JSONArray) {
                    JSONArray arr = (JSONArray) val;
                    for (int i = 0; i < arr.length(); i++) {
                        if (arr.get(i) instanceof JSONObject) {
                            arr.put(i, convertChineseKeys((JSONObject) arr.get(i)));
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return json;
    }

    // 获取规则值，处理"空"和"&&"特殊情况（来自123.txt）
    protected String getRuleVal(String key, String def) {
        if (rule == null) return def;
        String v = rule.optString(key, "");
        if (v.isEmpty() || "空".equals(v) || "&&".equals(v)) return def;
        return v;
    }

    protected String getRuleVal(String key) {
        return getRuleVal(key, "");
    }

    // ==================== 初始化 ====================
    protected void fetchRule() {
        if (rule == null) {
            if (ext != null) {
                try {
                    JSONObject rawRule;
                    if (ext.startsWith("http")) {
                        if(ext.indexOf("{cateId}") != -1 || ext.indexOf("{catePg}") !=-1){
                            rule = new JSONObject();
                            rule.put("homeUrl", ext);
                        }else{
                            String json = OkHttp.string(ext, null);
                            rawRule = new JSONObject(json);
                            rule = convertChineseKeys(rawRule);
                        }
                    } else {
                        rawRule = new JSONObject(ext);
                        rule = convertChineseKeys(rawRule);
                    }

                    // 兼容旧字段：list.url → class_url
                    if (!rule.has("class_url") && rule.has("list") && rule.getJSONObject("list").has("url")) {
                        rule.put("class_url", rule.getJSONObject("list").getString("url"));
                    }

                    // 默认播放嗅探词（来自123.txt）
                    if (rule.has("video_format")) {
                        String vf = rule.optString("video_format", "");
                        if (!vf.isEmpty()) {
                            videoFormatList.clear();
                            for (String f : vf.split("#")) {
                                if (!f.trim().isEmpty()) videoFormatList.add(f.trim());
                            }
                        }
                    }

                    if (!rule.has("list")) {
                        rule.put("list", new JSONObject());
                    }
                    JSONObject list = rule.getJSONObject("list");
                    // 初始化homeUrl,list.url
                    String homeUrl= rule.getString("homeUrl");
                    if(homeUrl.indexOf("{cateId}") != -1){
                        URL r = new URL(homeUrl);
                        String path =  r.getPath();
                        // 更新解析出来的homeUrl
                        rule.put("homeUrl", homeUrl.substring(0, homeUrl.indexOf(path)));
                        if(!list.has("url")){
                            list.put("url", homeUrl);
                        }
                    }
                    // class_url 未写入 list.url 时兜底（来自XBiubiu/XYQBiu的class_url字段）
                    if (!list.has("url")) {
                        String classUrl = rule.optString("class_url", "");
                        if (!classUrl.isEmpty()) {
                            list.put("url", classUrl);
                        }
                    }
                    // 初始化截断标志
                    String listUrl = list.getString("url");
                    if(listUrl.indexOf("/") !=-1) splitFlag+='/';
                    if(listUrl.indexOf(".") !=-1) splitFlag+='.';
                    if(listUrl.indexOf("-") !=-1) splitFlag+='-';

                    if (!rule.has("detail")) {
                        rule.put("detail", new JSONObject());
                    }

                    if (!rule.has("playlist")) {
                        rule.put("playlist", new JSONObject());
                    }

                    // 如果没有search，且没有配置任何搜索字段，则生成默认的suggest搜索
                    boolean hasFlatSearch = !getRuleVal("search_url").isEmpty()
                            || !getRuleVal("search_array").isEmpty()
                            || !getRuleVal("search_name").isEmpty()
                            || !getRuleVal("search_pic").isEmpty()
                            || !getRuleVal("search_id").isEmpty();
                    // 兼容 search 直接写字符串格式：{"search": "/vodsearch/{wd}---.html"}
                    Object searchObjRaw = rule.opt("search");
                    boolean hasSearchStrFormat = searchObjRaw instanceof String && !((String) searchObjRaw).isEmpty();
                    if (!rule.has("search") && !hasFlatSearch && !hasSearchStrFormat) {
                       String url = addHttpPrefix("index.php/ajax/suggest?mid=1&wd=阿凡达");
                        try {
                            JSONObject result = new JSONObject(OkHttp.string(url, getHeaders(url)));
                            JSONObject search = new JSONObject();
                            search.put("vod_id", "id");
                            search.put("vod_name", "name");
                            search.put("vod_pic", "pic");
                            search.put("url", addHttpPrefix("index.php/ajax/suggest?mid=1&wd={wd}"));
                            rule.put("search", search);
                        }
                        catch (Exception e){}
                    }
                    // 将字符串格式的 search 转换为 JSONObject 格式
                    if (hasSearchStrFormat) {
                        String searchUrlStr = (String) searchObjRaw;
                        JSONObject searchJson = new JSONObject();
                        searchJson.put("url", addHttpPrefix(searchUrlStr));
                        rule.put("search", searchJson);
                    }

                    // 有扁平搜索字段时，强制用配置覆盖 suggest
                    if (hasFlatSearch) {
                        if (!rule.has("search")) {
                            rule.put("search", new JSONObject());
                        }
                        JSONObject searchObj = rule.getJSONObject("search");
                        String searchUrlFlat = getRuleVal("search_url");
                        if (!searchUrlFlat.isEmpty()) {
                            searchObj.put("url", searchUrlFlat);
                        }
                        // 强制覆盖 vod_* 字段（不判断 has，让配置优先）
                        String[][] flatSearchFields = {
                                {"search_name", "vod_name"},
                                {"search_pic", "vod_pic"},
                                {"search_id", "vod_id"},
                                {"search_remarks", "vod_remarks"}
                        };
                        for (String[] pair : flatSearchFields) {
                            String val = getRuleVal(pair[0]);
                            if (!val.isEmpty()) {
                                JSONArray lb = stringCutToLookback(applyOrSelector(val));
                                if (lb != null) searchObj.put(pair[1], lb);
                            }
                        }
                    }

                    // 部分网站的播放页上直接就有 播放地址，基本上就是一样的格式，可以尝试在playerContent中直接拿直链
                    if (!rule.has("play")) {
                        JSONObject play = new JSONObject();
                        JSONArray region = new JSONArray();
                        region.put("var player_aaaa=");
                        region.put(0);

                        JSONArray vod_url = new JSONArray();
                        vod_url.put("\"url\":\"");
                        vod_url.put("\"");
                        play.put("region", region);
                        play.put("vod_url", vod_url);
                        rule.put("play", play);
                    }

                    // play字段中可以填写播放连接的关键字用来帮助识别嗅探结果，
                    // 一般奇葩的网站会用到
                    if (rule.has("play")) { // 自定义嗅探关键字
                        JSONObject play = rule.getJSONObject("play");
                        JSONArray keywords = play.optJSONArray("keywords");
                        if (keywords != null) {
                            videoFormatList.clear();
                            for (int i = 0; i < keywords.length(); ++i) {
                                videoFormatList.add(keywords.getString(i));
                            }
                        }
                    }

                    // 扁平字段注入：list_name/list_pic/list_id/list_remarks → list.vod_name/vod_pic/vod_id/vod_remarks
                    String[][] flatListFields = {
                        {"list_name", "vod_name"},
                        {"list_pic", "vod_pic"},
                        {"list_id", "vod_id"},
                        {"list_remarks", "vod_remarks"}
                    };
                    for (String[] pair : flatListFields) {
                        String val = getRuleVal(pair[0]);
                        if (!val.isEmpty() && !list.has(pair[1])) {
                            JSONArray lb = stringCutToLookback(applyOrSelector(val));
                            if (lb != null) list.put(pair[1], lb);
                        }
                    }
                    // 搜索侧同理
                    JSONObject search = rule.optJSONObject("search");
                    if (search != null) {
                        String[][] flatSearchFields = {
                            {"search_name", "vod_name"},
                            {"search_pic", "vod_pic"},
                            {"search_id", "vod_id"},
                            {"search_remarks", "vod_remarks"}
                        };
                        for (String[] pair : flatSearchFields) {
                            String val = getRuleVal(pair[0]);
                            if (!val.isEmpty() && !search.has(pair[1])) {
                                JSONArray lb = stringCutToLookback(applyOrSelector(val));
                                if (lb != null) search.put(pair[1], lb);
                            }
                        }
                    }

                    // 有扁平搜索字段时，确保 search 对象存在（必须在 applyStringCutRules 之前）
                    if (!rule.has("search")) {
                        boolean hasSearchField = !getRuleVal("search_url").isEmpty()
                                || !getRuleVal("search_array").isEmpty()
                                || !getRuleVal("search_name").isEmpty()
                                || !getRuleVal("search_pic").isEmpty()
                                || !getRuleVal("search_id").isEmpty();
                        if (hasSearchField) {
                            JSONObject searchObj = new JSONObject();
                            String searchUrlFlat = getRuleVal("search_url");
                            if (!searchUrlFlat.isEmpty()) {
                                searchObj.put("url", searchUrlFlat);
                            }
                            rule.put("search", searchObj);
                        }
                    }

                    // 应用字符串截取格式：list_array/search_array/play_array/from_array
                    applyStringCutRules(list, "list_array");
                    applyStringCutRules(rule.optJSONObject("search"), "search_array");
                    applyStringCutRules(rule.optJSONObject("playlist"), "play_array");
                    applyStringCutRules(rule.optJSONObject("playlist"), "from_array");
                    applyStringCutRules(rule.optJSONObject("detail"), "detail_array");

                    // playlist 扁平字段注入
                    JSONObject playlist = rule.getJSONObject("playlist");
                    // url_url / url_array → vod_play_url（单集链接规则）
                    String urlUrl = getRuleVal("url_url");
                    if (!urlUrl.isEmpty() && !playlist.has("vod_play_url")) {
                        JSONArray lb = stringCutToLookback(applyOrSelector(urlUrl));
                        if (lb != null) playlist.put("vod_play_url", lb);
                    }
                    if (!playlist.has("vod_play_url")) {
                        String urlArray = getRuleVal("url_array");
                        if (!urlArray.isEmpty()) {
                            JSONArray lb = stringCutToLookback(applyOrSelector(urlArray));
                            if (lb != null) playlist.put("vod_play_url", lb);
                        }
                    }
                    // url_title → vod_play_url_title（集标题）
                    String urlTitle = getRuleVal("url_title");
                    if (!urlTitle.isEmpty() && !playlist.has("vod_play_url_title")) {
                        JSONArray lb = stringCutToLookback(applyOrSelector(urlTitle));
                        if (lb != null) playlist.put("vod_play_url_title", lb);
                    }
                    // play_array 只用于 findVodPlayUrl 内部按 ul 分线路，不写入 region
                    // （region 若写成 hl-sort-list&&</ul> 会只截到第一段，丢失后续线路）

                    // 线路二次截取和多线字段处理
                    String lineSecondCut = getRuleVal("line_second_cut");
                    String multiLineTwice = getRuleVal("multi_line_twice");
                    String multiLineArray = getRuleVal("multi_line_array");
                    String multiLineUrl = getRuleVal("multi_line_url");
                    String multiLinePrefix = getRuleVal("multi_line_prefix");
                    String multiLineSuffix = getRuleVal("multi_line_suffix");

                    // 倒序开关（参考 XBPQ202608150244.java）
                    reverseOrder = "1".equals(getRuleVal("reverse"));

                    // 猜cateManual：用户已显式配置分类时跳过猜测，避免多余网络请求
                    JSONObject cateManual = rule.optJSONObject("cateManual");
                    String body = "";
                    boolean hasExplicitCate = !getRuleVal("fenlei").isEmpty()
                            || (!getRuleVal("class_name").isEmpty() && !getRuleVal("class_value").isEmpty());
                    if (cateManual == null && !hasExplicitCate) {
                        // 重建 cateManaul规则
                        body = this.fetchUrl(rule.getString("homeUrl"), rule.optJSONObject("header"));
                        if(body.length() > 32*1024) { body = body.substring(0, 32 * 1024); }
                        cateManual = this.guess_rule_cateManual(body);
                        if(cateManual != null){
                            rule.put("cateManual", cateManual);
                        }
                    }

                    // 猜list.vod_id
                    if (!list.has("vod_id")) {
                        if(body.isEmpty()){
                            body = this.fetchUrl(rule.getString("homeUrl"), rule.optJSONObject("header"));
                            if(body.length() > 32*1024) { body = body.substring(0, 32 * 1024); }
                        }
                        JSONArray listvodid = this.guess_rule_vod_id(body);
                        list.put("vod_id", listvodid);
                    }

                    // 如果没有json搜索接口，那么尝试在主页上找search 的接口 url
                    if (!rule.has("search")) {
                        if(body.isEmpty()){
                            body = this.fetchUrl(rule.getString("homeUrl"), rule.optJSONObject("header"));
                            if(body.length() > 32*1024) { body = body.substring(0, 32 * 1024); }
                        }
                        String url =  this.guess_rule_search_url(body);
                        if(!url.isEmpty()){
                            JSONObject searchRule = new JSONObject();
                            searchRule.put("url", url);
                            rule.put("search", searchRule);
                        }
                    }

                    SpiderDebug.log(String.format("默认rule: %s", rule.toString()));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public String addHttpPrefix(String url) {
        try {
            if (url.isEmpty()) return "";
            if (url.startsWith("http")) return url;
            String result = rule.getString("homeUrl");
            if (result.endsWith("/")) {
                result = result.substring(0, result.length() - 1);
            }
            if (url.startsWith("/")) {
                result += url;
            } else {
                result += "/" + url;
            }
            return result;
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return url;
    }

    protected HashMap<String, String> getHeaders(String url) {
        HashMap<String, String> headers = new HashMap<>();
        try {
            if (rule.has("header")) {
                Object headerObj = rule.get("header");
                if (headerObj instanceof JSONObject) {
                    JSONObject header = (JSONObject) headerObj;
                    Iterator<String> iter = header.keys();
                    while (iter.hasNext()) {
                        String key = iter.next();
                        headers.put(key, header.getString(key));
                    }
                } else if (headerObj instanceof String) {
                    // 兼容字符串格式："Key1$Value1#Key2$Value2"
                    JSONObject hdr = parseHeader((String) headerObj);
                    Iterator<String> iter = hdr.keys();
                    while (iter.hasNext()) {
                        String key = iter.next();
                        headers.put(key, hdr.getString(key));
                    }
                }
            }
            // 展开 headers 中的 UA 占位符（来自 header 字段的 User-Agent$MOBILE_UA）
            String uaVal = headers.get("User-Agent");
            if ("PC_UA".equals(uaVal)) {
                headers.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/94.0.4606.54 Safari/537.36");
            } else if ("MOBILE_UA".equals(uaVal)) {
                headers.put("User-Agent", "Mozilla/5.0 (Linux; Android 11; Mi 10 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/88.0.4324.152 Mobile Safari/537.36");
            }
            // 支持 User-Agent 和 Referer 直接配置（来自XYQBiu）
            String ua = rule.optString("User-Agent", "");
            if (!ua.isEmpty()) {
                if ("PC_UA".equals(ua)) {
                    headers.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/94.0.4606.54 Safari/537.36");
                } else if ("MOBILE_UA".equals(ua)) {
                    headers.put("User-Agent", "Mozilla/5.0 (Linux; Android 11; Mi 10 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/88.0.4324.152 Mobile Safari/537.36");
                } else {
                    headers.put("User-Agent", ua);
                }
            }
            String referer = rule.optString("Referer", "");
            if (!referer.isEmpty() && referer.startsWith("http")) {
                headers.put("Referer", referer);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        if (!headers.containsKey("User-Agent")) {
            headers.put("User-Agent", Util.CHROME);
        }
        return headers;
    }

    // 解析 header 字符串为 JSONObject（格式: "Key1$Value1#Key2$Value2"）
    protected JSONObject parseHeader(String headerStr) {
        try {
            JSONObject hdr = new JSONObject();
            if (headerStr.startsWith("{")) {
                return new JSONObject(headerStr);
            }
            String[] pairs = headerStr.split("#");
            for (String pair : pairs) {
                String[] kv = pair.split("\\$", 2);
                if (kv.length >= 2) {
                    hdr.put(kv[0].trim(), kv[1].trim());
                }
            }
            return hdr;
        } catch (JSONException e) {
            SpiderDebug.log(e);
        }
        return new JSONObject();
    }

    // 提取子内容（来自XBiubiu/XYQBiu）
    protected ArrayList<String> subContent(String content, String startFlag, String endFlag) {
        ArrayList<String> result = new ArrayList<>();
        if (startFlag.isEmpty() && endFlag.isEmpty()) {
            result.add(content);
            return result;
        }
        try {
            String escapedStart = escapeExprSpecialWord(startFlag);
            String escapedEnd = escapeExprSpecialWord(endFlag);
            // endFlag 为空时，只匹配 startFlag 之后的内容（不含 startFlag 本身）
            Pattern pattern = Pattern.compile(escapedStart + "(.*?)" + (escapedEnd.isEmpty() ? "$" : escapedEnd));
            Matcher matcher = pattern.matcher(content);
            while (matcher.find()) {
                result.add(matcher.group(1).trim());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (result.isEmpty()) result.add("");
        return result;
    }

    // ==================== 字符串截取格式支持 ====================

    /**
     * 应用 || 条件选择器：按顺序返回第一个非空结果
     * 格式："默认--空||首页--module-items\">&&class=\"content\""
     */
    protected String applyOrSelector(String data) {
        if (data == null || !data.contains("||")) return data;
        String[] parts = data.split("\\|\\|");
        for (String part : parts) {
            if (part.isEmpty()) continue;
            String resolved = part.trim();
            // 去掉 key-- 前缀（如 "默认--"、"首页--"）
            int doubleDash = resolved.indexOf("--");
            if (doubleDash > 0) {
                resolved = resolved.substring(doubleDash + 2);
            }
            if (!resolved.isEmpty()) return resolved;
        }
        return data;
    }

    /**
     * 应用二次截取规则
     * 格式："前缀&&后缀"
     */
    protected String applySecondCut(String content, String cutRule) {
        if (content == null || content.isEmpty() || cutRule == null || cutRule.isEmpty()) return content;
        // 先处理后处理器标记 [替换:a>>b]，替换掉后再截取
        cutRule = applyPostProcessors(cutRule);
        String[] parts = cutRule.split("&&");
        if (parts.length == 0) return content;
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
        return content.substring(start).trim();
    }

    /**
     * 应用后处理器：[替换:a>>b] [包含:关键词] [不包含:关键词]
     * 替换掉标记但执行对应操作
     */
    protected String applyPostProcessors(String str) {
        if (str == null || str.isEmpty()) return str;
        Pattern procPattern = Pattern.compile("\\[(替换|包含|不包含):([^\\]]+)\\]");
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
            }
            m.appendReplacement(sb, "");
        }
        m.appendTail(sb);
        return sb.toString();
    }

     /**
      * 将字符串截取规则（前缀&&后缀）转换为 lookback JSONArray
      * lookback 格式：[前缀, 后缀, 左偏移, 右偏移, 回看层级]
      */
     protected JSONArray stringCutToLookback(String rule) {
         if (rule == null || rule.isEmpty()) return null;
         String cutRule = applyPostProcessors(rule);
         String[] parts = cutRule.split("&&");
         JSONArray lookback = new JSONArray();
         if (parts.length >= 1) {
             lookback.put(parts[0].trim()); // 前缀
         } else {
             lookback.put("");
         }
         if (parts.length >= 2 && !parts[1].trim().isEmpty()) {
             lookback.put(parts[1].trim()); // 后缀
         } else {
             lookback.put(0);
         }
         lookback.put(0); // 左偏移
         lookback.put(0); // 右偏移
         lookback.put(1); // 回看层级
         return lookback;
     }

    /**
     * 将字符串截取规则转换为嵌套 JSONObject 中的 lookback 规则
     * 用于 list_array、search_array、play_array、from_array、detail_array 等字段
     */
    protected void applyStringCutRules(JSONObject target, String ruleKey) {
        if (target == null) return;
        String ruleVal = getRuleVal(ruleKey);
        if (ruleVal.isEmpty()) return;
        // 应用 || 条件选择器，去掉 key-- 前缀
        String processed = applyOrSelector(ruleVal);
        if (processed.isEmpty()) return;
        // 转换为 lookback JSONArray 并存入 target
        JSONArray lookback = stringCutToLookback(processed);
        if (lookback != null) {
            String fieldName = ruleKey.replace("_array", "");
            try {
                if (!target.has(fieldName)) {
                    target.put(fieldName, lookback);
                }
            } catch (JSONException e) {
                SpiderDebug.log(e);
            }
        }
    }

    public static String escapeExprSpecialWord(String keyword) {
        if (!keyword.isEmpty()) {
            String[] fbsArr = {"\\", "$", "(", ")", "*", "+", ".", "[", "]", "?", "^", "{", "}", "|"};
            for (String key : fbsArr) {
                if (keyword.contains(key)) {
                    keyword = keyword.replace(key, "\\" + key);
                }
            }
        }
        return keyword;
    }

    public static class HtmlNodeHlper{
        // 非正常配对的html标签，进行html层级查找时要用到
        protected static ArrayList<String> notPairedTag = new ArrayList<>(Arrays.asList("img", "br", "meta", "!--")); // <!---->为注释
        // 判断当前html标签是否为正常的标签
        public static boolean isPairedHtmlTag(String str, int startPos) {
            String tmp = str.substring(startPos, Math.min(str.length(), startPos + 10));
            for (String p : notPairedTag) {
                if (tmp.indexOf(p) != -1) {  // 找到了
                    // 找 > 如果匹配了 />  则认为是配对的
                    for (int i = startPos + 1; i < str.length(); ++i) {
                        String sm = str.substring(i);
                        if (str.charAt(i) == '>') {
                            if (str.charAt(i - 1) == '/') {
                                return true;
                            } else {
                                return false;
                            }
                        }
                    }
                    return false;
                }
            }
            return true;
        }

        // 查找当前标签的html代码 pos必须是标签的开始位置 <
        public static String nodeString(String str, int pos) {
            if (pos < 0 || pos >= str.length() || str.charAt(pos) != '<') return str;
            int isRightNode = 0;
            for (int i = pos; i < str.length() - 1; ++i) {
//            String sm = str.substring(i, i + 400);
                switch (str.charAt(i)) {
                    // 遇到 / 那么这个位置有可能是xml的结束标识,这种情况下再遇到<则不是当前节点的上级节点
                    case '/': {
                        if (str.charAt(i + 1) == '>') { // "/>" 认为是标签的结束位置
                            isRightNode--;
                        } else if (str.charAt(i - 1) == '<') { // "</" 认为是标签的结束位置
                            isRightNode--;
                        }
                        break;
                    }
                    case '>': {
                        if (isRightNode == 0) {
                            return str.substring(pos, i + 1);
                        }
                        break;
                    }
                    case '<': {
                        if (str.charAt(i + 1) != '/' && isPairedHtmlTag(str, i)) { // 不是 "</" 则认为是html标签的开始位置
                            ++isRightNode;
                        }
                        break;
                    }
                    default:
                        break;
                }

            }
            return str.substring(pos);
        }

        // lookback 回溯层级， 一般能找到共同的祖先结节就可以了
        public static ArrayList<Integer> findUpNodes(String str, int pos, int lookback) {
            ArrayList<Integer> nodes = new ArrayList<>();
            ArrayList<String> urls = new ArrayList<>();
            if (pos == -1) return nodes;
            int isUpNode = 0;
            for (int i = pos; i >= 0; --i) {
                switch (str.charAt(i)) {
                    // 遇到 / 那么这个位置有可能是xml的结束标识,这种情况下再遇到<则不是当前节点的上级节点
                    case '/': {
                        if (str.charAt(i + 1) == '>') {
                            isUpNode++;
//                        SpiderDebug.log(String.format("not xml %s", str.substring(i, i + 20)));
                        } else if (str.charAt(i - 1) == '<') {
                            isUpNode++;
                            --i;
//                        SpiderDebug.log(String.format("not xml %s", str.substring(i, i + 20)));
                        }
                        break;
                    }
                    case '<': {
                        if (isUpNode == 0) {
//                        SpiderDebug.log(String.format("find up node %d %s", i, str.substring(i, i + 30)));
                            urls.add(String.format("%5d", i));
                            nodes.add(i);
                        } else if (isPairedHtmlTag(str, i)) {
                            isUpNode--;
                            if (isUpNode < 0) isUpNode = 0;
//                        SpiderDebug.log(String.format("%s", str.substring(i, i + 30)));
                        }

                        break;
                    }
                    default:
                        break;
                }
                if (nodes.size() >= lookback) {
                    break;
                }
            }
            return nodes;
        }
        // 获取当前节点的所有子节点
        public static ArrayList<String> getChildNodes(String str) {
            ArrayList<String> arr = new ArrayList<>();
            int pos = 0;
            if (pos < 0 || pos >= str.length() || str.charAt(pos) != '<') return arr;
            ++pos;
            while (pos > -1 && pos < str.length()) {
                pos = str.indexOf('<', pos);
                String p = nodeString(str, pos);
                if (p.isEmpty()) {
                    break;
                }
                arr.add(p);
                pos += p.length();
            }
            return arr;
        }

        // 移除字符串的html标签
        public static String trimHtmlString(String str, String r) {
            String ret = str.replace("\r\n", "")
                    .replace("\n", "")
                    .replaceAll("<.+?>", r)
                    // .replace(" ", "")
                    .replaceAll("\\s+", " ")
                    .replace("&nbsp;", "")
                    .replace("&emsp;", "")
                    .trim();
            return ret;
        }

        public static String trimHtmlString(String str) {
            return trimHtmlString(str, "");
        }

    }

    public static class Utils{
        // 查找列表块的起始位置，取最靠近共同祖先节点的位置
        public static int findBlockPos(ArrayList<Integer> a, ArrayList<Integer> b) {
            int len = a.size() > b.size() ? b.size() : a.size();
            if(len ==1 ) return b.get(0);
            for (int i = 0; i < len; ++i) {
                if (a.get(i).intValue() == b.get(i).intValue()) {
                    return b.get(i - 1);
                }
            }
            return b.get(len - 1);
        }

        // 查找两个字符串之间的子串
        // keys 字段说明
        // 0 prefix 1 suffix 2 找到子串后左边index的偏移量 3 找到子串后右边index的偏移量
        public static String findSubString(String str, int startPos, JSONArray keys, String defaultVal) {
            try {
                if (keys == null) return defaultVal;
                String prefix = keys.getString(0);
                String suffix = keys.getString(1);
                int offsetl = 0;    // 左边的偏移量
                int offsetr = 0;    // 右边的偏移量
                if (keys.length() > 2) {
                    offsetl = keys.getInt(2);
                }
                if (keys.length() > 3) {
                    offsetr = keys.getInt(3);
                }
                int a = str.indexOf(prefix, startPos) + prefix.length();
                if (a < prefix.length()) return defaultVal;
                int b = str.indexOf(suffix, a);
                if (b < a) return defaultVal;
                return HtmlNodeHlper.trimHtmlString(str.substring(a + offsetl, b + offsetr));
            } catch (JSONException e) {
                e.printStackTrace();
            }
            return defaultVal;
        }

        public static String findSubString(String str, int startPos, JSONArray keys) {
            return findSubString(str, startPos, keys, "");
        }

        // 获取回看层数
        public static int getLookbackCount(JSONArray keys) {
            try {
                if (keys != null && keys.length() > 4) return keys.getInt(4);
            } catch (Exception e) {
                //e.printStackTrace();
            }
            return 0;
        }

        // 遍历JSONObect中的JSONArray查找回看的层数可用的规则
        public static JSONArray getLookbackArray(JSONObject obj) {
            try {
                // 优先顺序：list > search > vod_id > 其他
                String[] priorityKeys = {"list", "search", "vod_id"};
                for (String key : priorityKeys) {
                    if (obj.has(key)) {
                        Object val = obj.get(key);
                        if (val instanceof JSONArray && getLookbackCount((JSONArray) val) > 0) {
                            return (JSONArray) val;
                        }
                    }
                }
                // 兜底：遍历所有 JSONArray 字段
                Iterator iter = obj.keys();
                while (iter.hasNext()) {
                    String key = (String) iter.next();
                    if ("list".equals(key) || "search".equals(key) || "vod_id".equals(key)) continue;
                    Object val = obj.get(key);
                    if (val instanceof JSONArray) {
                        int c = getLookbackCount((JSONArray) val);
                        if (c > 0) return (JSONArray) val;
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        }

        // 统计子串个数
        public static int getSubStringCount(String str, String sub){
            int pos =0;
            int count =0;
            while (pos < str.length()){
                pos = str.indexOf(sub, pos);
                if(pos == -1) break;
                pos += sub.length();
                ++count;
            }
            return  count;
        }

        // 获取指定区间的字符串
        public static String getRegion(String str, JSONObject obj) {
            try {
                if (obj == null) return str;
                JSONArray region = obj.optJSONArray("region");
                if (region == null) return str;
                String prefix = region.getString(0);
                int a = str.indexOf(prefix);
                if (a == -1) return str;
                int b = str.length();
                if (region.length() > 1) {
                    b = str.indexOf(region.getString(1), a + prefix.length());
                    if (b == -1) b = str.length();
                }
                return str.substring(a, b);
            } catch (JSONException e) {
                e.printStackTrace();
            }
            return str;
        }

    }

    // 猜测分类列表的html区间代码
    protected String guessCateManualHtmlString(String body) {
        String regx = String.format("<a.+?href=\"(.+?)\".*?<", TextUtils.join("|", cateManuals));
        Pattern pattern = Pattern.compile(regx);
        Matcher m = pattern.matcher(body);
        ArrayList<HtmlMatchInfo> list = new ArrayList<>();
        int mcount = 0;
        while (m.find()   ) {
            ++mcount;
            if(mcount >30 && !list.isEmpty()){
                break;
            }
            HtmlMatchInfo cate = new HtmlMatchInfo();
            cate.init(m);
            cate.group2 = HtmlNodeHlper.trimHtmlString(HtmlNodeHlper.nodeString(body, cate.startPos));
            if (cate.group2.isEmpty()) continue;
            boolean bOk = false;
            for (String v : cateManuals){
                if(cate.group2.indexOf(v) !=-1) {
                    bOk = true;
                    break;
                }
            }
            if(!bOk) continue;
            cate.uploads = HtmlNodeHlper.findUpNodes(body, cate.startPos, 3);
            if (!list.isEmpty()) {
                boolean b = list.get(0).hasSameUpNode(cate);
                if (!b) { // 当前找到的info和list中的匹配
                    if (list.size() > 1) { // 如果list中的数据大于1 则认为找到了类型列表
                        return HtmlNodeHlper.nodeString(body, list.get(0).matchedUpNodePos);
                    }
                    list.clear();
                }
            }
            list.add(cate);
        }
        if (list.size() > 1) { // 如果list中的数据大于1 则认为找到了类型列表
            return HtmlNodeHlper.nodeString(body, list.get(0).matchedUpNodePos);
        } else {
            return "";
        }
    }

    // 从html代码中猜测分类名和分类ID cateManual规则
    protected JSONObject guess_rule_cateManual(String body) {
        try {
            String str = this.guessCateManualHtmlString(body);
            if (str.isEmpty()) return new JSONObject();

            String regx = String.format("<a.+?href=\"(.+?)\".*?[\"|>](\\s*?\\S+?\\s*?)(\"|<)", TextUtils.join("|", cateManuals));
            Pattern pattern = Pattern.compile(regx, Pattern.CASE_INSENSITIVE);
            Matcher m = pattern.matcher(str);
            ArrayList<HtmlMatchInfo> list = new ArrayList<>();
            while (m.find()) {
                // HtmlMatchInfo 字段映射
                // HtmlMatchInfo.group1 -> href
                // HtmlMatchInfo.group2 -> name
                // HtmlMatchInfo.diff  -> id  分类ID
                HtmlMatchInfo cate = new HtmlMatchInfo();
                cate.init(m);
                if (cate.group1.length() < 5) continue;
                cate.group2 = HtmlNodeHlper.trimHtmlString(HtmlNodeHlper.nodeString(str, cate.startPos));
                if(cate.group2.isEmpty()) continue;
                // 判断是否为正常的分类名
                boolean validCateName = true;
                for (int j = 0; j < invalidCateNames.size(); ++j) {
                    if (cate.group2.indexOf(invalidCateNames.get(j)) != -1) {
                        SpiderDebug.log(String.format("排除无效分类：%s --> %s", cate.group1, cate.group2));
                        validCateName = false;
                        break;
                    }
                }
                if(!validCateName) continue;

                if (!list.isEmpty()) {
                    if (!list.get(0).findDiffStr(cate, splitFlag)) {
                        SpiderDebug.log(String.format("排除可能无效的分类 %s <--> %s", cate.group1, cate.group2));
                        continue;
                    }
                }
                list.add(cate);
            }

            ArrayList<Integer> baseInfoIndexs = new ArrayList<>();
            // 找到最可能是正确的导航item
            for (int i =0; i < list.size(); ++i){
                list.get(i).diff = null;
                for (String v : cateManuals){
                    if(list.get(i).group2.indexOf(v) !=-1) {
                        baseInfoIndexs.add(i);
                        break;
                    }
                }
            }

            // 以找到的导航item为基准重建分类ID
            int baseInfoIndex=0;
            for (int i =1; i < baseInfoIndexs.size(); ++i){
                baseInfoIndex=baseInfoIndexs.get(0).intValue();
                list.get(baseInfoIndex).findDiffStr(list.get(baseInfoIndexs.get(i).intValue()), splitFlag);
            }

            JSONObject cateManual = new JSONObject();
            for (int i = 0; i < list.size(); ++i) {
                if(list.get(i).diff == null || list.get(i).diff.isEmpty()) {
                    if(!list.get(baseInfoIndex).findDiffStr(list.get(i), splitFlag)){
                        SpiderDebug.log(String.format("排除可能无效的分类 : %s", list.get(i).group0));
                        continue;
                    }
                }

                boolean validCateName = true;
                String name = list.get(i).group2;
                String id = list.get(i).diff;
                if (id == null || id.isEmpty()) continue;
                if (name == null || name.isEmpty()) continue;
                for (int k =0; k <id.length(); ++k){
                    if(splitFlag.indexOf(id.charAt(k)) != -1){
                        SpiderDebug.log(String.format("跳过无效的分类ID :%s", id));
                        continue;
                    }
                }
                for (int j = 0; j < invalidCateNames.size(); ++j) {
                    if (name.indexOf(invalidCateNames.get(j)) != -1) {
                        validCateName = false;
                        break;
                    }
                }
                if (validCateName && !cateManual.has(name)) {
                    cateManual.put(name, id);
                }
            }
            rule.put("cateManual", cateManual);
            return cateManual;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new JSONObject();
    }

    // 猜测搜索页的url规则
    protected String guess_rule_search_url(String body){
        // 找搜索页url的逻辑，不处理其他情情况
        // 1. 找包含 name input标签
        // 2. 找到后往上找三层，找action="" 如果有的话就找到了
        String regex = "<input.+?name=\"(.+?)\"";
        Pattern pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
        Matcher m = pattern.matcher(body);
        if(m.find()){
            String str = m.group(0);
            String wd = m.group(1);
            for (int i =1; i < 4; ++i){
                ArrayList<Integer> arr =  HtmlNodeHlper.findUpNodes(body,m.start(0), i);
                String r = HtmlNodeHlper.nodeString(body, arr.get(arr.size()-1));
                String regex2 = "action=\"(.+?)\"";
                Pattern pattern2 = Pattern.compile(regex2, Pattern.CASE_INSENSITIVE);
                Matcher m2 = pattern2.matcher(r);
                if(m2.find()){
                    String url = m2.group(1);
                    char ch = url.indexOf('?') ==-1 ? '?' : '&';
                    url = addHttpPrefix(url +   ch + wd + "={wd}");
                    return url;
                }

            }
        }
        return "";
    }
    // 猜测列表数据的 vod_id 规则
    public JSONArray guess_rule_vod_id(String body) {
        try {
            String regx = "<a.+?href=\"(.+?)\"";
            Pattern pattern = Pattern.compile(regx);
            Matcher m = pattern.matcher(body);
            HashMap<String, JSONArray> founds = new HashMap<>();
            ArrayList<HtmlMatchInfo> list = new ArrayList<>();
            while (m.find()) {
                HtmlMatchInfo cate = new HtmlMatchInfo();
                cate.init(m);
                cate.uploads = HtmlNodeHlper.findUpNodes(body, cate.startPos, 4);
//                String ms = this.findNodeString(body, cate.uploads.get(cate.uploads.size()-1));
                if (!list.isEmpty()) {

                    if(cate.group1.equals( list.get(list.size()-1).group1)) continue;
                    boolean b = list.get(list.size()-1).hasSameUpNode(cate);
                    if (!b) { // 当前找到的info和list中的匹配
                        if (list.size() > 1) {
                            HtmlMatchInfo info = list.get(0);
                            info.findDiffStr(list.get(1), splitFlag);
                            int id = 0;
                            boolean isNumberID = false;
                            try { id = Integer.valueOf(info.diff).intValue(); isNumberID = true; }catch (Exception e){}

                            if(id > 100 ){ // cateID一般都是小于100的
                                String url = (info.group1.replace(list.get(0).diff, "{vid}"));
                                JSONArray arr = new JSONArray();
                                String prefix = url.substring(0, url.indexOf("{vid}"));
                                String suffix = url.substring(prefix.length() + "{vid}".length());
                                int lookback = info.uploads.indexOf(info.matchedUpNodePos) - 1;
                                if (lookback < 1) lookback = 1;
                                arr.put(prefix);
                                arr.put(suffix);
                                arr.put(0);
                                arr.put(0);
                                arr.put(lookback);
                                arr.put(list.size());

                                if (!founds.containsKey(url)) {
                                    founds.put(url, arr);
                                } else {
                                    int nlen = founds.get(url).getInt(5) + list.size();
                                    arr.put(5, nlen);
                                    founds.put(url, arr);
                                    if(nlen >= 30){
                                        list.clear();
                                        break;
                                    }
                                }
                            }

                        }
                        list.clear();
                    }
                }
                list.add(cate);
                if(list.size()>30){
                    break;
                }
            }


            if (list.size() > 5 || (list.size()>1 && founds.isEmpty())) { // 如果list中的数据大于1 则认为找到了类型列表
                HtmlMatchInfo info = list.get(0);
                info.findDiffStr(list.get(1), splitFlag);
                int id = 0;
                boolean isNumberID = false;
                try { id = Integer.valueOf(info.diff).intValue(); isNumberID = true; }catch (Exception e){}

                if(id > 100 ){ // cateID一般都是小于100的

                    String url = (info.group1.replace(list.get(0).diff, "{vid}"));
                    JSONArray arr = new JSONArray();
                    String prefix = url.substring(0, url.indexOf("{vid}"));
                    String suffix = url.substring(prefix.length() + "{vid}".length());
                    int lookback = info.uploads.indexOf(info.matchedUpNodePos) - 1;
                    if (lookback < 1) lookback = 1;
                    arr.put(prefix);
                    arr.put(suffix);
                    arr.put(0);
                    arr.put(0);
                    arr.put(lookback);
                    arr.put(list.size());

                    if (!founds.containsKey(url)) {
                        founds.put(url, arr);
                    } else {
                        int nlen = founds.get(url).getInt(5) + list.size();
                        arr.put(5, nlen);
                        founds.put(url, arr);
                    }
                }
            }


            JSONArray c = null;
            for (String key : founds.keySet()) {
                JSONArray v = founds.get(key);
                if(c == null || c.getInt(5) < v.getInt(5)) c = v;
            }
            return  c;

        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    // 猜播放列表
    public JSONArray guess_rule_vod_play_url(String str, String vid) {
        String regex = "href=\"(/.+?)\"";
        Pattern pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
        Matcher m = pattern.matcher(str);
        HtmlMatchInfo info = new HtmlMatchInfo();
        ArrayList<String> vec = new ArrayList<>();
        boolean p0__ = false;
        while (m.find()){
            String sb = m.group(1);
            // 太长的url认为是错误的播放地址
            if(sb.length() > 100) continue;
            if(sb.indexOf(vid) == -1) continue;
            // 如果当前url的长度比上一个url的长度短也认为是无效的播放地址（带上了vod_id一般短一点的可能是详情页的地址）
            // 一般来讲 999-1-1.html 这种格式是播放页的地址，不排除这种地址
            boolean is__html = (sb.indexOf(vid+"-") != -1);
            if(!is__html  && vec.size() > 0 && vec.get(vec.size()-1).length() > sb.length()) continue;
            if(is__html && !p0__ ){ // 找到了准确度最高的播放连接格式，如果检查到列表头不是这种格式的话，清空列表从头开始
                vec.clear();
            }
            // 如果列表里面装了标准的连接格式，不标准的就不要了
            if(p0__ && !is__html) continue;

            info.init(m);
            if(vec.isEmpty()) p0__ = is__html;
            vec.add(m.group(1));
            if(vec.size() > 10  && vec.get(vec.size()-2).length() == sb.length()) {
                break;
            }
        }
        if(info.group0 != null){
            for (int i =1;i < 4; ++i){
                ArrayList<Integer> nodes = HtmlNodeHlper.findUpNodes(str, info.startPos, i);
                int startPos = nodes.get(nodes.size()-1).intValue();
                //String smd =  str.substring(startPos, startPos+10);
                String smd =  HtmlNodeHlper.nodeString(str, startPos);

                if(smd.indexOf("<ul") ==0 || smd.indexOf("<div") ==0  || i == 3){ // 最多退三层，找不到就算了
                    // found 播放列表的 根节节点
                    String prefix = info.group1.substring(0, info.group1.indexOf(vid));
                    String suffix = "\"";
                    JSONArray arr = new JSONArray();
                    arr.put(prefix);
                    arr.put(suffix);
                    arr.put(0 - prefix.length());
                    arr.put(0);
                    arr.put(i);
                    return arr;
                }
            }
        }
        return null;
    }

    //
    public String guess_value_vod_name(String nd, int startPos) {
        try {
            JSONArray vec = new JSONArray();
            vec.put("alt=\"");
            vec.put("\"");
            String val = Utils.findSubString(nd, startPos, vec);

            if (val.isEmpty()) {
                vec.put(0, "\" title=\"");
                val = Utils.findSubString(nd, startPos, vec);
            }
            if (val.isEmpty()) { // 如果没有通过title找到视频名，则取整个node的文本内容,取出现次数最多的项

                String all = HtmlNodeHlper.trimHtmlString(nd, "!!!!");
                String[] words = all.split("!!!!");
                HashMap<String, Integer> map = new HashMap<String, Integer>();

                for (int i = 0; i < words.length; ++i) {
                    words[i] =words[i].trim();
                    if (!words[i].isEmpty() && words[i].indexOf("更新") ==-1) {

                        int c = 1;
                        if (map.containsKey(words[i])) {
                            c = 1 + map.get(words[i]).intValue();
                            ;//[words[i]]
                        }
                        map.put(words[i], Integer.valueOf(c));
                    }
                }
                String s = "";
                int c = 0;
                for (String key : map.keySet()) {
                    int v = map.get(key).intValue();
                    if (v > c) {
                        c = v;
                        s = key;
                    }
                }
                val = s;
            }
            return val.replace("在线", "")
                    .replace("立即", "")
                    .replace("观看", "")
                    .replace("点播", "")
                    .replace("影片", "")
                    .replace("信息", "")
                    .replace("播放", "")
                    .trim();
            //return  val;
        } catch (Exception e) {

        }
        return "";
    }

    public String guess_value_vod_remarks(String nd, int startPos, String vod_name) {
        try {
            String all = HtmlNodeHlper.trimHtmlString(nd, "!!!!");
            String[] words = all.split("!!!!");
            String val = "";
            for (int i = 0; i < words.length; ++i) {
                String wd = words[i].trim();
                if (!wd.isEmpty() && wd.indexOf(vod_name) == -1) {
                    String dot = (!val.isEmpty()) ? "," : "";// val += ",";
                    String tmp = val + dot + wd;
                    if (tmp.length() > 20) {
                        break;
                    }
                    val = tmp;
                }
            }
            return val;
        } catch (Exception e) {

        }
        return "";

    }

    public String guess_value_vod_pic(String nd, int startPos) {
        try {
            JSONArray vec = new JSONArray();
            vec.put("data-original=\"");
            vec.put("\"");
            String val = Utils.findSubString(nd, startPos, vec);
            if (val.isEmpty()) {
                vec.put(0, "data-src=\"");
                val = Utils.findSubString(nd, startPos, vec);
            }
            if (val.isEmpty()) {
                vec.put(0, "src=\"");
                val = Utils.findSubString(nd, startPos, vec);
            }
            if (val.isEmpty()) {
                vec.put(0, "data-bg=\"");
                val = Utils.findSubString(nd, startPos, vec);
            }
            if (val.isEmpty()) {
                //TODO: 直接在nd中找个.jpg .png 之类的当图片
            }
            return addHttpPrefix(val);
        } catch (Exception e) {

        }
        return "";
    }

    @Override
    public boolean isVideoFormat(String url) {
        url = url.toLowerCase();
        if (url.contains("=http") || url.contains("=https") || url.contains("=https%3a%2f") || url.contains("=http%3a%2f")) {
            return false;
        }
        for (String format : videoFormatList) {
            if (url.contains(format)) {
                return true;
            }
        }
        return false;
    }

    // 让当前爬虫自己判断是否为可播放的地址
    @Override
    public boolean manualVideoCheck() throws Exception {
        return true;
    }


    @Override
    public String homeContent(boolean z) throws Exception {
        try {
            fetchRule();

            JSONObject result = new JSONObject();
            JSONArray classes = new JSONArray();
            // 用户显式配置了 fenlei，则不使用猜测的 cateManual（避免猜测结果优先于用户配置）
            JSONObject cateManual = rule.optJSONObject("cateManual");
            String fenleiExplicit = rule.optString("fenlei", "");
            if (!fenleiExplicit.isEmpty()) {
                cateManual = null;
            }

            // 应用 cat_twice 分类二次截取（支持 || 条件选择器 + key--前缀 + [替换] 后处理器）
            String catTwice = getRuleVal("cat_twice");
            if (!catTwice.isEmpty() && cateManual == null) {
                // 对 body 进行二次截取后再解析分类
                String body = fetchUrl(rule.getString("homeUrl"), rule.optJSONObject("header"));
                if(body.length() > 32*1024) { body = body.substring(0, 32 * 1024); }
                body = applySecondCut(body, applyOrSelector(catTwice));
                cateManual = this.guess_rule_cateManual(body);
                if(cateManual != null){
                    rule.put("cateManual", cateManual);
                }
            }
            // 支持 class_name/class_value 格式（来自XYQBiu）
            String classNames = rule.optString("class_name", "");
            String classValues = rule.optString("class_value", "");
            if (!classNames.isEmpty() && !classValues.isEmpty()) {
                String[] names = classNames.split("&");
                String[] values = classValues.split("&");
                int len = Math.min(names.length, values.length);
                for (int i = 0; i < len; i++) {
                    JSONObject jsonObject = new JSONObject();
                    jsonObject.put("type_name", names[i]);
                    jsonObject.put("type_id", values[i].replace("＆＆", "&"));
                    classes.put(jsonObject);
                }
            } else if (cateManual != null && cateManual.length() > 0) {
                // 原有逻辑
                Iterator<String> keys = cateManual.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    JSONObject jsonObject = new JSONObject();
                    jsonObject.put("type_name", key);
                    jsonObject.put("type_id", cateManual.getString(key));
                    classes.put(jsonObject);
                }
            } else if (!getRuleVal("cat_array").isEmpty() && !getRuleVal("cat_title").isEmpty() && !getRuleVal("cat_id").isEmpty()) {
                // 支持 cat_array/cat_title/cat_id 格式（来自XYQHiker/XYQBiu）
                String catArrayRule = getRuleVal("cat_array");
                String catTitleRule = getRuleVal("cat_title");
                String catIdRule = getRuleVal("cat_id");
                if (!catArrayRule.isEmpty() && !catTitleRule.isEmpty() && !catIdRule.isEmpty()) {
                    String body = fetchUrl(rule.getString("homeUrl"), rule.optJSONObject("header"));
                    if(body.length() > 32*1024) { body = body.substring(0, 32 * 1024); }
                    // 先应用二次截取（catTwice 已在上方定义）
                    if (!catTwice.isEmpty()) {
                        body = applySecondCut(body, applyOrSelector(catTwice));
                    }
                    // 用 cat_array 截取列表
                    String cutArray = applyOrSelector(catArrayRule);
                    String[] arrayParts = cutArray.split("&&");
                    String arrayStart = arrayParts.length > 0 ? arrayParts[0] : "";
                    String arrayEnd = arrayParts.length > 1 ? arrayParts[1] : "";
                    if (!arrayStart.isEmpty()) {
                        int startIdx = body.indexOf(arrayStart);
                        if (startIdx >= 0) {
                            body = body.substring(startIdx + arrayStart.length());
                        }
                    }
                    if (!arrayEnd.isEmpty()) {
                        int endIdx = body.indexOf(arrayEnd);
                        if (endIdx >= 0) {
                            body = body.substring(0, endIdx);
                        }
                    }
                    // 根据 cat_title/cat_id 格式提取分类
                    String[] titleParts = catTitleRule.split("&&");
                    String[] idParts = catIdRule.split("&&");
                    String[] items = body.split("(?s)");
                    // 按 item 分隔符拆分（通常是 && 的后缀）
                    java.util.ArrayList<String> itemStrs = new java.util.ArrayList<>();
                    int pos = 0;
                    String sep = arrayEnd.isEmpty() ? "" : arrayEnd;
                    if (!sep.isEmpty()) {
                        int lastSep = body.lastIndexOf(sep);
                        if (lastSep >= 0) {
                            body = body.substring(0, lastSep + sep.length());
                        }
                    }
                    // 重新按 && 分割
                    if (body.contains(arrayEnd)) {
                        int idx = 0;
                        while (idx < body.length()) {
                            int next = body.indexOf(arrayEnd, idx);
                            if (next < 0) break;
                            String item = body.substring(idx, next + arrayEnd.length());
                            itemStrs.add(item);
                            idx = next + arrayEnd.length();
                        }
                    } else {
                        itemStrs.add(body);
                    }
                    for (String itemStr : itemStrs) {
                        try {
                            // 提取标题
                            String title;
                            if (titleParts.length >= 2) {
                                int tStart = itemStr.indexOf(titleParts[0]);
                                if (tStart < 0) continue;
                                tStart += titleParts[0].length();
                                int tEnd = itemStr.indexOf(titleParts[1], tStart);
                                title = tEnd > 0 ? itemStr.substring(tStart, tEnd).trim() : itemStr.substring(tStart).trim();
                            } else {
                                title = itemStr.trim();
                            }
                            // 提取ID
                            String id;
                            if (idParts.length >= 2) {
                                int iStart = itemStr.indexOf(idParts[0]);
                                if (iStart < 0) iStart = 0;
                                iStart += idParts[0].length();
                                int iEnd = itemStr.indexOf(idParts[1], iStart);
                                id = iEnd > 0 ? itemStr.substring(iStart, iEnd).trim() : itemStr.substring(iStart).trim();
                            } else {
                                id = itemStr.trim();
                            }
                            if (!title.isEmpty()) {
                                JSONObject jsonObject = new JSONObject();
                                jsonObject.put("type_name", title);
                                jsonObject.put("type_id", id);
                                classes.put(jsonObject);
                            }
                        } catch (Exception e) {
                            SpiderDebug.log(e);
                        }
                    }
                }
            // 支持 fenlei 格式（来自XBiubiu），格式: "名称$id#名称2$id2"
            } else {
                String fenlei = rule.optString("fenlei", "");
                if (!fenlei.isEmpty()) {
                    String[] items = fenlei.split("#");
                    for (String item : items) {
                        String[] info = item.split("\\$");
                        if (info.length >= 2) {
                            JSONObject jsonObject = new JSONObject();
                            jsonObject.put("type_name", info[0]);
                            jsonObject.put("type_id", info[1]);
                            classes.put(jsonObject);
                        }
                    }
                }
            }

            // 兜底：如果 classes 为空且有 fenlei 字段（单值分类名），尝试从 class_url 提取 cateId
            if (classes.length() == 0 && !rule.optString("fenlei", "").isEmpty()) {
                String classUrl = rule.optString("class_url", "");
                String cateId = "";
                if (classUrl.contains("tid=")) {
                    int start = classUrl.indexOf("tid=") + 4;
                    int end = classUrl.indexOf("&", start);
                    cateId = end > start ? classUrl.substring(start, end) : classUrl.substring(start);
                } else if (classUrl.contains("{cateId}")) {
                    cateId = "1";
                } else if (classUrl.contains("?")) {
                    cateId = "1";
                }
                if (!cateId.isEmpty()) {
                    JSONObject jsonObject = new JSONObject();
                    jsonObject.put("type_name", rule.optString("fenlei", ""));
                    jsonObject.put("type_id", cateId);
                    classes.put(jsonObject);
                }
            }

            result.put("class", classes);
            if (z && rule.has("filter")) {
                result.put("filters", rule.getJSONObject("filter"));
            }
            // 支持 filterdata 字段（来自XYQBiu）
            if (z && rule.has("filterdata")) {
                Object filterdata = rule.get("filterdata");
                if (filterdata instanceof JSONObject) {
                    result.put("filters", (JSONObject) filterdata);
                } else if (filterdata instanceof String) {
                    String furl = (String) filterdata;
                    if (furl.startsWith("http")) {
                        try {
                            String fjson = OkHttp.string(furl, null);
                            result.put("filters", new JSONObject(fjson));
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
            // 构建排序筛选（来自XBiubiu），格式: "排序名1&排序名2" → "排序值1&排序值2"
            if (z && !getRuleVal("sort_type").isEmpty() && !getRuleVal("sort_value").isEmpty()) {
                String sortNames = getRuleVal("sort_type");
                String sortValues = getRuleVal("sort_value");
                String[] names = sortNames.split("&");
                String[] values = sortValues.split("&");
                // 检查 class_url 中是否有 {by} 占位符
                String classUrl = rule.optString("class_url", "");
                if (classUrl.contains("{by}")) {
                    JSONObject filter = new JSONObject();
                    JSONObject byItem = new JSONObject();
                    byItem.put("key", "by");
                    JSONArray listArr = new JSONArray();
                    int len = Math.min(names.length, values.length);
                    for (int i = 0; i < len; i++) {
                        JSONObject opt = new JSONObject();
                        opt.put("n", names[i].trim());
                        opt.put("v", values[i].trim());
                        listArr.put(opt);
                    }
                    byItem.put("value", listArr);
                    filter.put("by", byItem);
                    result.put("filters", filter);
                }
            }
            return result.toString();
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        return "";
    }

    // 首页视频推荐（来自XBiubiu/XYQBiu）
    @Override
    public String homeVideoContent() {
        try {
            fetchRule();
            String homeVal = getRuleVal("firstpage");
            if (homeVal.isEmpty()) {
                homeVal = "20";          // 没配置就默认拉 20 条
            }

            int maxVideos = 20;
            List<String> preferCates = new ArrayList<>();
            Map<String, Integer> cateLimit = new HashMap<>();

            // 支持完整「首页」语法：韩剧$20#泰剧$15#日剧$10
            if (homeVal.contains("$") || homeVal.contains("#")) {
                String[] items = homeVal.split("#");
                for (String item : items) {
                    item = item.trim();
                    if (item.isEmpty()) continue;
                    String[] kv = item.split("\\$");
                    String name = kv[0].trim();
                    preferCates.add(name);
                    int limit = 20;
                    if (kv.length > 1) {
                        try {
                            limit = Integer.parseInt(kv[1].trim());
                        } catch (Exception ignore) {}
                    }
                    cateLimit.put(name, limit);
                    maxVideos = Math.max(maxVideos, limit);
                }
            } else {
                try {
                    maxVideos = Integer.parseInt(homeVal.trim());
                } catch (NumberFormatException e) {
                    maxVideos = 20;
                }
            }

            JSONObject homeObj = new JSONObject(homeContent(true));
            JSONArray classes = homeObj.optJSONArray("class");
            if (classes == null || classes.length() == 0) {
                return "";
            }

            // 建立 名称 → type_id 映射
            Map<String, String> name2id = new LinkedHashMap<>();
            for (int i = 0; i < classes.length(); i++) {
                JSONObject cls = classes.getJSONObject(i);
                name2id.put(cls.optString("type_name"), cls.optString("type_id"));
            }

            int count = 0;
            JSONArray allVideos = new JSONArray();
            Set<String> usedIds = new HashSet<>();

            // 1. 优先按「首页」指定的分类拉取（韩剧 → 泰剧 → 日剧）
            for (String cateName : preferCates) {
                if (count >= maxVideos) break;
                String tid = name2id.get(cateName);
                if (tid == null || tid.isEmpty()) continue;

                int thisLimit = cateLimit.getOrDefault(cateName, 20);
                pullCategoryVideos(tid, thisLimit, allVideos, usedIds);
                count = allVideos.length();
            }

            // 2. 如果还不够，再按 class 顺序补全（跳过已经拉过的）
            if (count < maxVideos) {
                for (int i = 0; i < classes.length() && count < maxVideos; i++) {
                    JSONObject cls = classes.getJSONObject(i);
                    String name = cls.optString("type_name");
                    if (preferCates.contains(name)) continue;   // 已经优先拉过了
                    String tid = cls.optString("type_id");
                    pullCategoryVideos(tid, maxVideos - count, allVideos, usedIds);
                    count = allVideos.length();
                }
            }

            // 倒序（如果配置了）
            if (reverseOrder) {
                JSONArray reversed = new JSONArray();
                for (int i = allVideos.length() - 1; i >= 0; i--) {
                    reversed.put(allVideos.get(i));
                }
                allVideos = reversed;
            }

            JSONObject result = new JSONObject();
            result.put("list", allVideos);
            return result.toString();
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        return "";
    }

    /** 拉取单个分类视频（带去重 + 数量限制） */
    private void pullCategoryVideos(String tid, int limit, JSONArray allVideos, Set<String> usedIds) {
        try {
            String content = categoryContent(tid, "1", false, new HashMap<>());
            if (content == null || content.isEmpty()) return;

            JSONObject data = new JSONObject(content);
            JSONArray list = data.optJSONArray("list");
            if (list == null) return;

            int thisCount = 0;
            for (int j = 0; j < list.length() && thisCount < limit; j++) {
                JSONObject v = list.getJSONObject(j);
                String vid = v.optString("vod_id");
                if (vid.isEmpty() || usedIds.contains(vid)) continue;
                usedIds.add(vid);
                allVideos.put(v);
                thisCount++;
            }
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
    }

    // from xpath 加入过滤条件
    protected String categoryUrl(String tid, String pg, boolean filter, HashMap<String, String> extend) {
        try {
            JSONObject list = this.rule.getJSONObject("list");
            String cateUrl = list.optString(pg, "");
            if(cateUrl.isEmpty())
                cateUrl = list.getString("url");
            // 处理 ;; 模式后缀（如 /search.php?...;;mrcRA）
            if (cateUrl.contains(";;")) {
                int semiIdx = cateUrl.indexOf(";;");
                cateUrl = cateUrl.substring(0, semiIdx).trim();
            }
            if (filter && extend != null && extend.size() > 0) {
                for (Iterator<String> it = extend.keySet().iterator(); it.hasNext(); ) {
                    String key = it.next();
                    String value = extend.get(key);
                    if (value.length() > 0) {
                        cateUrl = cateUrl.replace("{" + key + "}", URLEncoder.encode(value));
                    }
                }
            }
            cateUrl = cateUrl.replace("{cateId}", tid).replace("{catePg}", pg);
            Matcher m = Pattern.compile("\\{(.*?)\\}").matcher(cateUrl);
            while (m.find()) {
                String n = m.group(0).replace("{", "").replace("}", "");
                cateUrl = cateUrl.replace(m.group(0), "").replace("/" + n + "/", "");
            }
            return cateUrl;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "";
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        try {
            JSONObject list = this.rule.getJSONObject("list");
            String url = categoryUrl(tid, pg, filter, extend);
            String body = fetchUrl(url, list.optJSONObject("header"));
            String str = Utils.getRegion(body, list);
            // 应用 list_twice 二次截取（支持 || 条件选择器 + key--前缀 + [替换] 后处理器）
            String listTwice = getRuleVal("list_twice");
            if (!listTwice.isEmpty()) {
                str = applySecondCut(str, applyOrSelector(listTwice));
            }
            JSONArray videos = new JSONArray();
            JSONArray lookback = Utils.getLookbackArray(list);
            if (lookback != null) lookback = new JSONArray(lookback.toString());
            Set<String> set = new HashSet<String>();
            int pos = 0;
            ArrayList<Integer> urlnodes = null;
            int lookup = -1;
            while (lookback != null) {
                pos = str.indexOf(lookback.getString(0), pos);
                if (pos == -1) break;

                ArrayList<Integer> arr = null;
                int blockPos = 0;
                String nd ="";
                do {
                    arr = HtmlNodeHlper.findUpNodes(str, pos - 1, lookback.getInt(4));
                    if (urlnodes == null) {
                        urlnodes = arr;
                        blockPos = arr.get(arr.size() - 1);
                    } else {
                        blockPos = Utils.findBlockPos(urlnodes, arr);
                    }
                    nd = HtmlNodeHlper.nodeString(str, blockPos);

                    // 检查是否回看层数过多，如果回看导数过多会导致加载不出来数据或一页只加载一条数据，需要进行修正
                    if(lookup < 0){
                        int count = Utils.getSubStringCount(nd, lookback.getString(0));
                        if(count > 3 && lookback.getInt(4)>1){
                            lookback.put(4, lookback.getInt(4)-1);
                            urlnodes = null;
                            blockPos=0;
                            nd="";
                            SpiderDebug.log(String.format("找到过多的url匹配项(%d)，降低匹配层级为%d", count, lookback.getInt(4)));
                        }else if (count > 1 && lookback.getInt(4) > 1) {
                            // 新增：一个 block 中出现多次前缀（不止1个视频），强制降层级
                            lookback.put(4, Math.max(1, lookback.getInt(4)-1));
                            urlnodes = null;
                            blockPos = 0;
                            nd = "";
                            SpiderDebug.log(String.format("检测到多条目(%d)，强制降低lookback到%d", count, lookback.getInt(4)));
                        }else if(lookup == -1){
                            String pic = guess_value_vod_pic(nd,0); //尝试找一下图片，如果没找到的话增加一级
                            String vName = guess_value_vod_name(nd,0);
                            if(pic.isEmpty()||vName.isEmpty()){
                                lookback.put(4, lookback.getInt(4)+1);
                                urlnodes = null;
                                blockPos=0;
                                nd="";
                                lookup = -2; // 只退一次
                                SpiderDebug.log(String.format("当前层级未找到(%s)，增加匹配层级为%d",  pic.isEmpty()? "图片": "标题",  lookback.getInt(4)));
                            }else{
                                // 即使找到了图片/标题，如果 block 内含多条 URL 也不要接受高层级
                                int multiCount = Utils.getSubStringCount(nd, lookback.getString(0));
                                if(multiCount > 1 && lookback.getInt(4) > 1){
                                    lookback.put(4, Math.max(1, lookback.getInt(4)-1));
                                    urlnodes = null;
                                    blockPos = 0;
                                    nd = "";
                                    SpiderDebug.log(String.format("block内含多条目(%d)，拒绝接受，降低lookback到%d", multiCount, lookback.getInt(4)));
                                }else{
                                    lookup = lookback.getInt(4);
                                }
                            }
                        }else{
                            lookup = lookback.getInt(4);
                        }
                    }
                }while (lookup < 0 );


                pos = blockPos + nd.length();
                blockPos = 0;
                String vod_id = Utils.findSubString(nd, blockPos, list.getJSONArray("vod_id"));
                if (!set.contains(vod_id)) { // 排除重复数据
                    // filter_word：列表过滤词（包含即跳过）
                    String filterWord = getRuleVal("filter_word");
                    if (!filterWord.isEmpty()) {
                        String vodName = Utils.findSubString(nd, blockPos, list.optJSONArray("vod_name"));
                        boolean containsFilter = false;
                        for (String word : filterWord.split(",")) {
                            String trimmed = word.trim();
                            if (!trimmed.isEmpty() && (vod_id.contains(trimmed) || vodName.contains(trimmed))) {
                                containsFilter = true;
                                break;
                            }
                        }
                        if (containsFilter) continue;
                    }
                    set.add(vod_id);
                    JSONObject v = new JSONObject();
                    v.put("vod_id", vod_id);
                    v.put("vod_name", Utils.findSubString(nd, blockPos, list.optJSONArray("vod_name")));

                    if (v.getString("vod_name").isEmpty()) {
                        v.put("vod_name", guess_value_vod_name(nd, 0));
                    }

                    v.put("vod_pic", addHttpPrefix(Utils.findSubString(nd, blockPos, list.optJSONArray("vod_pic"))));

                    if (v.getString("vod_pic").isEmpty()) {
                        v.put("vod_pic", guess_value_vod_pic(nd, 0));
                    }

                    if (getRuleVal("PicNeedProxy").equals("1")) {
                        String pic = v.getString("vod_pic");
                        if (!pic.isEmpty()) {
                            v.put("vod_pic", fixCover(pic, url));
                        }
                    }
                    v.put("vod_remarks", Utils.findSubString(nd, blockPos, list.optJSONArray("vod_remarks")));

                    // 随便整点remark
                    if (v.getString("vod_remarks").isEmpty()) {
                        String vod_name = v.getString("vod_name");
                        v.put("vod_remarks", guess_value_vod_remarks(nd, 0, vod_name));
                    }
                    v.put("vod_id", Base64.encodeToString(v.toString().getBytes(StandardCharsets.UTF_8), base64Flag));
                    videos.put(v);
                }
            }

            // 倒序
            if (reverseOrder) {
                JSONArray reversed = new JSONArray();
                for (int i = videos.length() - 1; i >= 0; i--) {
                    reversed.put(videos.get(i));
                }
                videos = reversed;
            }

            JSONObject result = new JSONObject();
            result.put("page", pg);
            result.put("pagecount", Integer.MAX_VALUE);
            result.put("limit", Math.max(90, videos.length()));
            result.put("total", Integer.MAX_VALUE);
            result.put("list", videos);
            return result.toString();
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    // 生成播放的名称
    public ArrayList<String> makeVodPlayFrom(int sz) {
        ArrayList<String> vec = new ArrayList<String>();
        for (int i = 1; i <= sz; ++i) {
            vec.add("播放列表" + i);
        }
        return vec;
    }

    // 查找播放列表名
    public ArrayList<String> findVodPlayFrom(String str, int sz) {
        try {
            ArrayList<Integer> urlnodes = null;
            JSONObject playlist = this.rule.getJSONObject("playlist");
            if (!playlist.has("vod_play_from")) {
                // 尝试从 from_array 的字符串截取规则中提取线路名
                String fromArray = getRuleVal("from_array");
                String lineSecondCut = getRuleVal("line_second_cut");
                if (!fromArray.isEmpty()) {
                    // 应用线路二次截取（line_second_cut）
                    if (!lineSecondCut.isEmpty()) {
                        str = applySecondCut(str, applyOrSelector(lineSecondCut));
                    }
                    String processed = applyOrSelector(fromArray);
                    String cutRule = applyPostProcessors(processed);
                    String[] parts = cutRule.split("&&");
                    if (parts.length >= 2) {
                        String start = parts[0].trim();
                        String end = parts.length > 1 ? parts[1].trim() : "";
                        int linePos = 0;
                        ArrayList<String> lines = new ArrayList<>();
                        while (lines.size() < sz) {
                            int startPos = str.indexOf(start, linePos);
                            if (startPos < 0) break;
                            int startIndex = startPos + start.length();
                            int endIndex;
                            if (end.isEmpty()) {
                                // endFlag 为空时，匹配到下一个同层级 start（单条提取），而非截到字符串末尾
                                int nextStart = str.indexOf(start, startIndex);
                                endIndex = nextStart >= 0 ? nextStart : str.length();
                            } else {
                                endIndex = str.indexOf(end, startIndex);
                                if (endIndex < 0) break;
                            }
                            lines.add(str.substring(startIndex, endIndex).trim());
                            linePos = endIndex + (end.isEmpty() ? 0 : end.length());
                        }
                        if (!lines.isEmpty()) return new ArrayList<>(lines);
                    }
                }
                return makeVodPlayFrom(sz);
            }
            ArrayList<Pair<Integer, String>> vod_play_from = new ArrayList<Pair<Integer, String>>();
            JSONArray rule_vod_play_from = playlist.getJSONArray("vod_play_from");
            for (int i = 0; i < rule_vod_play_from.length(); ++i) {
                String s = rule_vod_play_from.get(i).getClass().getSimpleName();
                String key = "";
                String alias = "";
                if (s.equals("String")) {
                    key = alias = rule_vod_play_from.getString(i);
                } else if (s.equals("JSONArray")) {
                    JSONArray item = rule_vod_play_from.getJSONArray(i);
                    key = alias = item.getString(0);
                    if (item.length() > 1) {
                        alias = item.getString(1);
                    }
                } else {
                    return makeVodPlayFrom(sz);
                }

                int pos = str.indexOf(key);
                if (pos == -1) continue;
                vod_play_from.add(new Pair<>(pos, alias));
            }
            // 找到的名称与实际需要的数量不匹配，返回默认的名称
            if (vod_play_from.size() != sz) {
                return makeVodPlayFrom(sz);
            }
            // 排序
            Collections.sort(vod_play_from, new Comparator<Pair<Integer, String>>() {
                @Override
                public int compare(Pair<Integer, String> a, Pair<Integer, String> b) {
                    return a.first.intValue() - b.first.intValue();
                }
            });

            ArrayList<String> vec = new ArrayList<String>();
            for (int i = 0; i < vod_play_from.size(); ++i) {
                vec.add(vod_play_from.get(i).second);
            }
            return vec;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return makeVodPlayFrom(sz);
    }

    // 查找播放列表
    public ArrayList<String> findVodPlayUrl(String str) {
        ArrayList<String> tmp_vod_play_url = new ArrayList<String>();
        ArrayList<String> vod_play_url = new ArrayList<String>();
        try {
            int pos = 0;
            ArrayList<Integer> urlnodes = null;
            JSONObject playlist = this.rule.getJSONObject("playlist");
            int sort = playlist.optInt("sort", 0);  // 如果这个值是0。表示要倒序播放列表
            HashMap<String, Integer> map = new HashMap<String, Integer>();
            Set<Integer> rmset = new HashSet<Integer>();
//            String tmp = "";
            ArrayList<String> tmp = new ArrayList<String>();

            JSONArray lookback = Utils.getLookbackArray(playlist);
            JSONArray rule_vod_play_url = playlist.optJSONArray("vod_play_url");
            String prefix = (rule_vod_play_url != null && rule_vod_play_url.length() > 0)
                    ? rule_vod_play_url.getString(0) : "";

            // 处理多线模式（PPT等特殊站点）
            String multiLineTwiceVal = getRuleVal("multi_line_twice");
            String multiLineArrayVal = getRuleVal("multi_line_array");
            String multiLineUrlVal = getRuleVal("multi_line_url");
            String multiLinePrefixVal = getRuleVal("multi_line_prefix");
            String multiLineSuffixVal = getRuleVal("multi_line_suffix");
            if (!multiLineArrayVal.isEmpty() && !multiLineUrlVal.isEmpty()) {
                String processedBody = str;
                if (!multiLineTwiceVal.isEmpty()) {
                    processedBody = applySecondCut(str, applyOrSelector(multiLineTwiceVal));
                }
                String processedArray = applyOrSelector(multiLineArrayVal);
                String processedUrl = applyOrSelector(multiLineUrlVal);
                String[] arrayParts = processedArray.split("&&");
                String[] urlParts = processedUrl.split("&&");
                if (arrayParts.length >= 2 && urlParts.length >= 2) {
                    String arrayStart = arrayParts[0].trim();
                    String arrayEnd = arrayParts[1].trim();
                    String urlStart = urlParts[0].trim();
                    String urlEnd = urlParts[1].trim();
                    int linePos = 0;
                    ArrayList<String> lines = new ArrayList<>();
                    while (lines.size() < 10) {
                        int aStart = processedBody.indexOf(arrayStart, linePos);
                        if (aStart < 0) break;
                        int aEndIdx = aStart + arrayStart.length();
                        int aEnd = processedBody.indexOf(arrayEnd, aEndIdx);
                        if (aEnd < 0) break;
                        String lineContent = processedBody.substring(aEndIdx, aEnd);
                        int uStart = lineContent.indexOf(urlStart);
                        if (uStart < 0) { linePos = aEnd + arrayEnd.length(); continue; }
                        String afterUrlStart = lineContent.substring(uStart + urlStart.length());
                        int uEnd = afterUrlStart.indexOf(urlEnd);
                        if (uEnd < 0) { linePos = aEnd + arrayEnd.length(); continue; }
                        String url = multiLinePrefixVal + afterUrlStart.substring(0, uEnd) + multiLineSuffixVal;
                        lines.add(url);
                        linePos = aEnd + arrayEnd.length();
                    }
                    if (!lines.isEmpty()) {
                        vod_play_url.add(TextUtils.join("#", lines));
                        return vod_play_url;
                    }
                }
            }

            // 按 play_array 分块解析各线路选集（通用方案，支持 hl-sort-list 等）
            String playArrayRule = getRuleVal("play_array");
            String urlUrlRule = getRuleVal("url_url");
            if (!playArrayRule.isEmpty() && !urlUrlRule.isEmpty()
                    && playArrayRule.contains("&&") && urlUrlRule.contains("&&")) {
                String[] pa = applyPostProcessors(applyOrSelector(playArrayRule)).split("&&", 2);
                String[] ua = applyPostProcessors(applyOrSelector(urlUrlRule)).split("&&", 2);
                String listStart = pa[0].trim();
                String listEnd = pa.length > 1 ? pa[1].trim() : "</ul>";
                String hrefStart = ua[0].trim();
                String hrefEnd = ua.length > 1 ? ua[1].trim() : "\"";

                String titleStart = ">";
                String titleEnd = "<";
                String urlTitleRule = getRuleVal("url_title");
                if (!urlTitleRule.isEmpty() && urlTitleRule.contains("&&")) {
                    String[] ta = applyPostProcessors(applyOrSelector(urlTitleRule)).split("&&", 2);
                    titleStart = ta[0];
                    titleEnd = ta.length > 1 ? ta[1] : "<";
                }

                int listPos = 0;
                int blockCount = 0;
                while (true) {
                    int ls = str.indexOf(listStart, listPos);
                    if (ls < 0) break;
                    int le = str.indexOf(listEnd, ls + listStart.length());
                    if (le < 0) break;
                    String block = str.substring(ls, le);
                    listPos = le + listEnd.length();
                    blockCount++;

                    ArrayList<String> eps = new ArrayList<>();
                    int hp = 0;
                    while (true) {
                        int hs = block.indexOf(hrefStart, hp);
                        if (hs < 0) break;
                        int he0 = hs + hrefStart.length();
                        int he = block.indexOf(hrefEnd, he0);
                        if (he < 0) break;
                        String href = block.substring(he0, he).trim();
                        hp = he + hrefEnd.length();
                        if (!href.contains("/play/") && !href.contains("vodplay")) continue;

                        String title = "";
                        int ts = block.indexOf(titleStart, he);
                        if (ts >= 0 && ts < he + 120) {
                            int te = block.indexOf(titleEnd, ts + titleStart.length());
                            if (te > ts) title = cleanHtml(block.substring(ts + titleStart.length(), te));
                        }
                        if (title.contains("展开全部")) continue;
                        if (title.isEmpty()) title = "第" + (eps.size() + 1) + "集";
                        eps.add(title + "$" + addHttpPrefix(href));
                    }
                    if (!eps.isEmpty()) {
                        if (sort != 0) Collections.reverse(eps);
                        tmp_vod_play_url.add(TextUtils.join("#", eps));
                    }
                }
                if (!tmp_vod_play_url.isEmpty()) {
                    SpiderDebug.log("playArray: blocks=" + blockCount + " episodes=" + tmp_vod_play_url.size());
                    for (int i = 0; i < tmp_vod_play_url.size(); ++i) {
                        if (!rmset.contains(i)) {
                            vod_play_url.add(tmp_vod_play_url.get(i));
                        }
                    }
                    return vod_play_url;
                } else {
                    SpiderDebug.log("playArray: blocks=" + blockCount + " tmp_vod_play_url empty");
                }
            }

            for (int i = 0; i < tmp_vod_play_url.size(); ++i) {
                if (!rmset.contains(i)) {
                    vod_play_url.add(tmp_vod_play_url.get(i));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return vod_play_url;
    }

    // 猜测详情数据的html区间
    protected String guessDetailContentRegion(String body) {
        String regx = String.format(">\\s*?(%s)|(%s)", TextUtils.join("|", detailItemNames), TextUtils.join("：|", detailItemNames));
        Pattern pattern = Pattern.compile(regx, Pattern.CASE_INSENSITIVE);
        Matcher m = pattern.matcher(body);
        ArrayList<HtmlMatchInfo> list = new ArrayList<>();
        while (m.find()) {
            HtmlMatchInfo cate = new HtmlMatchInfo();
            cate.init(m);
            cate.uploads = HtmlNodeHlper.findUpNodes(body, cate.startPos, 5);
            if (!list.isEmpty()) {
                boolean b = list.get(0).hasSameUpNode(cate);
                if (!b) { // 当前找到的info和list中的匹配
                    if (list.size() > 1) {
                        boolean found = false;
                        for (int i = 0; i < list.size(); ++i) {
                            if (list.get(i).group0.indexOf("导演") != -1) {
                                found = true;
                            }
                        }
                        if (found) {
                            return HtmlNodeHlper.nodeString(body, list.get(0).matchedUpNodePos);
                        } else {
                            list.clear();
                        }
                    }
                    list.clear();
                }
            }
            list.add(cate);
        }
        if (list.size() > 1) { // 如果list中的数据大于1 则认为找到了类型列表
            return HtmlNodeHlper.nodeString(body, list.get(0).matchedUpNodePos);
        } else {
            return "";
        }
    }

    @Override
    public String detailContent(List<String> ids) {
        try {
            fetchRule();
            JSONObject vinfo = new JSONObject(new String(Base64.decode(ids.get(0), base64Flag), "UTF-8"));

            JSONObject detail = rule.optJSONObject("detail");
            if (detail == null) return "";
            // 生成详情页 URL
            String vid = vinfo.optString("vod_id", "");
            String url;
            if (detail.has("url")) {
                // 规则里显式写了详情 url
                url = detail.getString("url").replace("{vid}", vid);
            } else if (vid.startsWith("http://") || vid.startsWith("https://") || vid.startsWith("/")) {
                // XYQ 风格：链接字段已是完整路径或 URL
                url = addHttpPrefix(vid);
            } else {
                // 原生 XBPQ：list.vod_id 是 [前缀, 后缀] 模板
                JSONObject list = rule.getJSONObject("list");
                JSONArray tmp = list.getJSONArray("vod_id");
                url = addHttpPrefix(tmp.getString(0) + vid + tmp.getString(1));
            }
            String body = fetchUrl(url, detail.optJSONObject("header"));
            String str = Utils.getRegion(body, detail);
            int startPos = 0;

            String nodeString = "";
            // 圈定 详情数据的范围
            JSONArray lookback = Utils.getLookbackArray(detail);
            if (lookback != null) {
                int pos = str.indexOf(lookback.getString(0), 0);
                if (pos != -1) {
                    ArrayList<Integer> arr = HtmlNodeHlper.findUpNodes(str, pos - 1, lookback.getInt(4));
                    if (arr.size() > 0) {
                        startPos = arr.get(arr.size() - 1);
                        nodeString = HtmlNodeHlper.nodeString(str, startPos); // 精确详情数据的范围
                    }
                }
            }
            // 没有指定详情数据范围则猜一个出来
            if (nodeString.isEmpty()) {
                nodeString = this.guessDetailContentRegion(body);
            }

            if (nodeString.length() != str.length()) {
                str = nodeString;
                startPos = 0;
            }
            ///////////////////////////////////////////////////////////////////////////////////////

            JSONObject vod = new JSONObject();
            vod.put("vod_id", ids.get(0));
            vod.put("vod_name", Utils.findSubString(str, startPos, detail.optJSONArray("vod_name")));
            vod.put("vod_pic", addHttpPrefix(Utils.findSubString(str, startPos, detail.optJSONArray("vod_pic"))));
            vod.put("type_name", Utils.findSubString(str, startPos, detail.optJSONArray("type_name")));
            vod.put("vod_year", Utils.findSubString(str, startPos, detail.optJSONArray("vod_year")));
            vod.put("vod_area", Utils.findSubString(str, startPos, detail.optJSONArray("vod_area")));
            vod.put("vod_remarks", Utils.findSubString(str, startPos, detail.optJSONArray("vod_remarks")));
            vod.put("vod_actor", Utils.findSubString(str, startPos, detail.optJSONArray("vod_actor")));
            vod.put("vod_director", Utils.findSubString(str, startPos, detail.optJSONArray("vod_director")));
            vod.put("vod_content", Utils.findSubString(str, startPos, detail.optJSONArray("vod_content")));


            ////////////////////////////////////////////////////////////////////////////////////////
            if (vod.getString("vod_name").isEmpty()) {
                vod.put("vod_name", vinfo.optString("vod_name", ""));
            }
            // 从页面中猜个视频名称出来
            if (vod.getString("vod_name").isEmpty()) {
                vod.put("vod_name", guess_value_vod_name(str, startPos));
            }

            ////////////////////////////////////////////////////////////////////////////////////////
            if (vod.getString("vod_pic").isEmpty()) {
                vod.put("vod_pic", vinfo.optString("vod_pic", ""));
            }
            if (vod.getString("vod_pic").isEmpty()) {
                vod.put("vod_pic", guess_value_vod_pic(str, startPos));
            }
            if (getRuleVal("PicNeedProxy").equals("1")) {
                String pic = vod.getString("vod_pic");
                if (!pic.isEmpty()) {
                    vod.put("vod_pic", fixCover(pic, url));
                }
            }

            ////////////////////////////////////////////////////////////////////////////////////////
            if (lookback != null && lookback.length() > 1) {
                JSONArray key = new JSONArray();
                String name = lookback.getString(0);
                String skey = lookback.getString(0);

                ArrayList<String> detailItems = new ArrayList<>(Arrays.asList("导演", "演员", "类型", "年份"));
                for (String p : detailItems) {
                    if (name.indexOf(p) != -1) {
                        skey = p;
                        break;
                    }
                }
                key.put(name);
                key.put(lookback.getString(1));
                if (vod.getString("vod_director").isEmpty()) {
                    key.put(0, name.replace(skey, "导演"));
                    vod.put("vod_director", Utils.findSubString(str, startPos, key));
                }
                if (vod.getString("vod_actor").isEmpty()) {
                    key.put(0, name.replace(skey, "主演"));
                    vod.put("vod_actor", Utils.findSubString(str, startPos, key));
                }

                if (vod.getString("vod_content").isEmpty()) {
                    String all = HtmlNodeHlper.trimHtmlString(str, "!!!!");
                    String[] words = all.split("!!!!");
                    String v = "";
                    for (int i = 0; i < words.length; ++i) {
                        if (words[i].length() > v.length()) {
                            v = words[i];
                        }
                    }
                    vod.put("vod_content", HtmlNodeHlper.trimHtmlString(v));
                }

            } else { // 猜一下详情数据
                if (vod.getString("vod_director").isEmpty()) {
                    ArrayList<String> arr = HtmlNodeHlper.getChildNodes(nodeString);
                    String content = "";
                    String f = TextUtils.join("|", detailItemNames);
                    String regex = String.format("%s",f);
                    Pattern pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
                    for (int i = 0; i < arr.size(); ++i) {
                        String p = HtmlNodeHlper.trimHtmlString(arr.get(i), " ").replace("：", "");
                        if (p.length() > content.length()) { content = p; }
                        String[] all = p.split(regex);
                        // split出来的可能存在空字符串，去除掉
                        ArrayList<String> items = new ArrayList<>();
                        for(String c: all){
                            if(c.isEmpty()) continue;
                            items.add(c);
                        }
                        Matcher m = pattern.matcher(p);
                        int index = 0;
                        while (m.find() && index < items.size()){
                            String s = m.group(0);
                            for (int j = 0; j < detailItemNames.size(); ++j) {
                                String name = detailItemNames.get(j);
                                String key = detailItemKeys.get(j);
                                if (s.indexOf(name) != -1) {
                                    if (vod.getString(key).isEmpty()) {
                                        vod.put(key, items.get(index).trim());
                                    }
                                    break;
                                }
                            }
                            ++index;
                        }
                    }

                    if (vod.getString("vod_content").isEmpty()) {
                        vod.put("vod_content", content);
                    }
                }
            }


            playlistContent(ids, vod, body);// 获取播放列表


            JSONObject result = new JSONObject();
            JSONArray list = new JSONArray();
            list.put(vod);
            result.put("list", list);
            return result.toString();
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }

    }

    // 播放页 str 为 detailContent 函数中http返回值
    protected void playlistContent(List<String> ids, JSONObject vod, String body) {
        try {
            fetchRule();
            JSONObject vinfo = new JSONObject(new String(Base64.decode(ids.get(0), base64Flag), "UTF-8"));

            JSONObject playlist = rule.optJSONObject("playlist");
            if (playlist == null) return;
            if (playlist.has("url")) {
                String detailUrl = rule.getJSONObject("detail").optString("url");
                String playListUrl = playlist.getString("url");
                if(!detailUrl.equals(playListUrl)){
                    String url = playlist.getString("url").replace("{vid}", vinfo.getString("vod_id"));
                    body = fetchUrl(url, playlist.optJSONObject("header"));
                }
            }
            String str = Utils.getRegion(body, playlist);

            ArrayList<String> vod_play_url = null;
            if (!playlist.has("vod_play_url")) {
                // 猜vod_play_url的查找规则
                JSONArray vod_play_url_rule = this.guess_rule_vod_play_url(str, vinfo.getString("vod_id"));
                if(vod_play_url_rule != null){
                    playlist.put("vod_play_url", vod_play_url_rule);
                }
            }
            vod_play_url = this.findVodPlayUrl(str);
            ArrayList<String> vod_play_from = this.findVodPlayFrom(str, vod_play_url.size());

            // 如果有说明播放源的名称，且规则里配置了 vod_play_from，才做别名排序
            if (playlist.has("vod_play_from") && vod_play_url != null && !vod_play_url.isEmpty()) {
                String f1 = TextUtils.join("$$$", vod_play_from);
                String f2 = TextUtils.join("$$$", makeVodPlayFrom(vod_play_url.size()));

                if (!f1.equals(f2)) {
                    ArrayList<String> urls = new ArrayList<>();
                    ArrayList<String> froms = new ArrayList<>();

                    JSONArray rule_vod_play_from = playlist.getJSONArray("vod_play_from");
                    for (int i = 0; i < rule_vod_play_from.length(); ++i) {
                        String s = rule_vod_play_from.get(i).getClass().getSimpleName();
                        String alias = "";
                        if (s.equals("String")) {
                            alias = rule_vod_play_from.getString(i);
                        } else if (s.equals("JSONArray")) {
                            JSONArray item = rule_vod_play_from.getJSONArray(i);
                            alias = item.getString(0);
                            if (item.length() > 1) {
                                alias = item.getString(1);
                            }
                        }

                        for (int j = 0; j < vod_play_from.size(); ++j) {
                            if (vod_play_from.get(j).equals(alias)) {
                                urls.add(vod_play_url.get(j));
                                froms.add(vod_play_from.get(j));
                            }
                        }
                    }
                    if (!urls.isEmpty()) {
                        vod_play_url = urls;
                        vod_play_from = froms;
                    }
                }
            }
            vod.put("vod_play_url", TextUtils.join("$$$", vod_play_url));
            vod.put("vod_play_from", TextUtils.join("$$$", vod_play_from));
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    // 尝试从播放页中找播放url
    protected String parsePlayUrl(String str, String str2, List<String> list) {
        try {
            JSONObject play = rule.optJSONObject("play");
            if (play == null) {
                return "";
            }
            String tmp = fetchUrl(str2, play.optJSONObject("header"));
            String body = Utils.getRegion(tmp, play);
            int startPos = 0;
            JSONArray lookback = Utils.getLookbackArray(play);
            if (lookback != null) {
                int pos = body.indexOf(lookback.getString(0), 0);
                if (pos != -1) {
                    ArrayList<Integer> arr = HtmlNodeHlper.findUpNodes(body, pos - 1, lookback.getInt(4));
                    if (arr.size() > 0) {
                        startPos = arr.get(arr.size() - 1);
                    } else {
                        startPos = pos;
                    }
                }
            }

            String vod_url = Utils.findSubString(body, startPos, play.optJSONArray("vod_url"));
            vod_url = vod_url.replace("\\/", "/");
            if (vod_url.isEmpty() || !isVideoFormat(vod_url)) return "";
            JSONObject result = new JSONObject();
            result.put("parse", 0);
            result.put("playUrl", "");
            result.put("url", vod_url);
            return result.toString();

        } catch (Exception e) {
            e.printStackTrace();
        }

        return "";
    }

    @Override
    public String playerContent(String str, String str2, List<String> list) throws Exception {
        try {
            fetchRule();
            String webUrl = str2;

            // 支持 force_play 直接播放模式（来自XYQBiu）
            String forcePlay = rule.optString("force_play", "0");
            if (forcePlay.equals("1") || forcePlay.equals("2")) {
                JSONObject result = new JSONObject();
                webUrl = rule.optString("play_prefix", "") + webUrl + rule.optString("play_suffix", "");
                // 支持 play_header 请求头（来自XYQBiu）
                String playHeader = rule.optString("play_header", "");
                if (!playHeader.isEmpty()) {
                    try {
                        if (playHeader.startsWith("{")) {
                            result.put("header", playHeader);
                        } else {
                            JSONObject hdr = new JSONObject();
                            String[] usera = playHeader.split("#");
                            for (String user : usera) {
                                String[] head = user.split("\\$");
                                hdr.put(head[0], " " + head[1]);
                            }
                            result.put("header", hdr.toString());
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                if (webUrl.contains("#isVideo=true#")) {
                    webUrl = webUrl.replaceAll("#isVideo=true#", "");
                }
                if (Util.isVideoFormat(webUrl)) {
                    result.put("parse", 0);
                    result.put("playUrl", "");
                } else if (Util.isVip(webUrl)) {
                    result.put("parse", 1);
                    result.put("jx", "1");
                    result.put("url", webUrl);
                    return result.toString();
                } else {
                    result.put("parse", 1);
                    result.put("playUrl", "");
                }
                result.put("url", webUrl);
                return result.toString();
            }

            // 支持直接播放（来自XBiubiu/XYQBiu）
            if (rule.optString("direct", "0").equals("1")) {
                JSONObject result = new JSONObject();
                result.put("parse", 0);
                result.put("playUrl", "");
                result.put("url", str2);
                return result.toString();
            }

            // 支持 MacPlayer 播放器解析（来自XYQBiu）
            if (rule.optString("Anal_MacPlayer", "0").equals("1")) {
                try {
                    String html = fetchUrl(webUrl, null);
                    Pattern scriptPattern = Pattern.compile("var player_\\w+\\s*=\\s*(\\{.+?\\});");
                    Matcher scriptMatcher = scriptPattern.matcher(html);
                    if (scriptMatcher.find()) {
                        String jsonStr = scriptMatcher.group(1);
                        JSONObject player = new JSONObject(jsonStr);
                        String videoUrlTmp = player.getString("url");
                        if (player.has("encrypt")) {
                            int encrypt = player.getInt("encrypt");
                            if (encrypt == 1) {
                                videoUrlTmp = java.net.URLDecoder.decode(videoUrlTmp, "UTF-8");
                            } else if (encrypt == 2) {
                                videoUrlTmp = new String(Base64.decode(videoUrlTmp, Base64.DEFAULT), "UTF-8");
                                videoUrlTmp = java.net.URLDecoder.decode(videoUrlTmp, "UTF-8");
                            }
                        }
                        if (Util.isVip(videoUrlTmp)) {
                            JSONObject result = new JSONObject();
                            result.put("parse", 1);
                            result.put("jx", "1");
                            result.put("url", videoUrlTmp);
                            return result.toString();
                        } else if (Util.isVideoFormat(videoUrlTmp)) {
                            JSONObject result = new JSONObject();
                            result.put("parse", 0);
                            result.put("playUrl", "");
                            result.put("url", videoUrlTmp);
                            return result.toString();
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            // 先判断是否可以拿到直链
            String ret = parsePlayUrl(str, str2, list);
            if (!ret.isEmpty()) return ret;

            // 支持跳转播放链接（来自XBPQ例子格式）
            String jumpUrl = rule.optString("jump_url", "");
            if (!jumpUrl.isEmpty()) {
                try {
                    String html = fetchUrl(webUrl, null);
                    jumpUrl = applyPostProcessors(jumpUrl);
                    String[] jparts = jumpUrl.split("&&", 2);
                    String startFlag = jparts[0];
                    String endFlag = jparts.length > 1 ? jparts[1] : "";
                    String parsedUrl = "";
                    // 支持 * 通配：var player_*"url":"&&" → 用正则匹配
                    if (startFlag.contains("*")) {
                        Pattern p = Pattern.compile(
                            "var player_\\w+\\s*=\\s*\\{[^}]*?\"url\"\\s*:\\s*\"([^\"]+)\"");
                        Matcher m = p.matcher(html);
                        if (m.find()) parsedUrl = m.group(1);
                    } else {
                        ArrayList<String> results = subContent(html, startFlag, endFlag);
                        if (!results.isEmpty()) parsedUrl = results.get(0);
                    }
                    // 尝试处理 encrypt（MacCMS 常见）
                    try {
                        Pattern ep = Pattern.compile("\"encrypt\"\\s*:\\s*(\\d+)");
                        Matcher em = ep.matcher(html);
                        if (em.find()) {
                            int encrypt = Integer.parseInt(em.group(1));
                            if (encrypt == 1) {
                                parsedUrl = java.net.URLDecoder.decode(parsedUrl, "UTF-8");
                            } else if (encrypt == 2) {
                                parsedUrl = new String(Base64.decode(parsedUrl, Base64.DEFAULT), "UTF-8");
                                parsedUrl = java.net.URLDecoder.decode(parsedUrl, "UTF-8");
                            }
                        }
                    } catch (Exception ignored) {}
                    parsedUrl = parsedUrl.replace("\\/", "/");
                    if (!parsedUrl.isEmpty()) {
                        if (Util.isVideoFormat(parsedUrl) || isVideoFormat(parsedUrl)) {
                            JSONObject result = new JSONObject();
                            result.put("parse", 0);
                            result.put("playUrl", "");
                            result.put("url", parsedUrl);
                            return result.toString();
                        }
                        if (Util.isVip(parsedUrl)) {
                            JSONObject result = new JSONObject();
                            result.put("parse", 1);
                            result.put("jx", "1");
                            result.put("url", parsedUrl);
                            return result.toString();
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            // 直接将网页地址返回回去进行嗅探
            JSONObject result = new JSONObject();
            result.put("parse", 1);
            result.put("playUrl", "");
            result.put("url", str2);
            return result.toString();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "";
    }

    protected Object parseJsonSearchResult(Object obj) {
        try {
            if (obj == null) return null;
            JSONObject search = rule.optJSONObject("search");
            if (search == null) return null;
            String key_vod_id = search.getString("vod_id");
            String key_vod_name = search.getString("vod_name");
            String type = obj.getClass().getSimpleName();
            if (type.equals("JSONObject")) {
                JSONObject object = (JSONObject) obj;
                if (object.has(key_vod_id) && object.has(key_vod_name)) return object;
                for (Iterator<String> iter = object.keys(); iter.hasNext(); ) {
                    String k = iter.next();
                    Object r = parseJsonSearchResult(object.get(k));
                    if (r != null) {
                        return r;
                    }
                }
            } else if (type.equals("JSONArray")) {
                JSONArray array = (JSONArray) obj;
                for (int i = 0; i < array.length(); ++i) {
                    if (parseJsonSearchResult(array.get(i)) != null) {
                        return array;
                    }
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    protected String parseSearchResult(String body) {
        try {
            JSONObject obj = new JSONObject(body);
            Object info = parseJsonSearchResult(obj);
            if (info == null) return "";
            JSONArray arr = new JSONArray();
            if (info.getClass().getSimpleName().equals("JSONObject")) {
                arr.put(info);
            } else {
                arr = (JSONArray) info;
            }
            JSONObject search = rule.optJSONObject("search");
            JSONArray videos = new JSONArray();
            for (int i = 0; i < arr.length(); ++i) {
                JSONObject v = new JSONObject();
                JSONObject o = arr.getJSONObject(i);
                if (search.has("vod_id") && o.has(search.getString("vod_id"))) {
                    v.put("vod_id", o.get(search.getString("vod_id")).toString());
                } else {
                    continue;
                }
                if (search.has("vod_name") && o.has(search.getString("vod_name"))) {
                    v.put("vod_name", o.get(search.getString("vod_name")).toString());
                } else {
                    v.put("vod_name", "未知");
                }

                if (search.has("vod_pic") && o.has(search.getString("vod_pic"))) {
                    v.put("vod_pic", o.get(search.getString("vod_pic")).toString());
                } else {
                    v.put("vod_pic", "");
                }

                if (search.has("vod_remarks") && o.has(search.getString("vod_remarks"))) {
                    v.put("vod_remarks", o.get(search.getString("vod_remarks")).toString());
                } else {
                    v.put("vod_remarks", "");
                }
                v.put("vod_id", Base64.encodeToString(v.toString().getBytes(StandardCharsets.UTF_8), base64Flag));
                videos.put(v);
            }
            JSONObject result = new JSONObject();
            result.put("list", videos);
            return result.toString();

        } catch (Exception e) {
//            e.printStackTrace();
        }
        return "";
    }

    @Override
    public String searchContent(String wd, boolean z) {
        try {
            fetchRule();
            // 支持搜索url的两种格式：search.url（嵌套）和 search_url（扁平）
            JSONObject search = rule.optJSONObject("search");
            String searchUrlFlat = rule.optString("search_url", "");
            if ((search == null || !search.has("url")) && searchUrlFlat.isEmpty()) return "";

            String str = "";
            String url = "";
            // 扁平 search_url 优先于 search.url（suggest）
            if (!searchUrlFlat.isEmpty()) {
                url = addHttpPrefix(
                        searchUrlFlat
                                .replace("{wd}", URLEncoder.encode(wd, "UTF-8"))
                                .replace("{pg}", "1")
                );
                // 应用 search_suffix 后缀
                String searchSuffix = getRuleVal("search_suffix");
                if (!searchSuffix.isEmpty()) {
                    url = url + searchSuffix;
                }
                String searchHeader = getRuleVal("search_header");
                JSONObject searchHeaders = null;
                if (!searchHeader.isEmpty()) {
                    searchHeaders = parseHeader(searchHeader);
                }
                str = fetchUrl(url, searchHeaders);
            } else if (search != null && search.has("url")) {
                url = search.getString("url")
                        .replace("{wd}", wd)
                        .replace("{pg}", "1");
                url = addHttpPrefix(url);
                // 应用 search_suffix 后缀
                String searchSuffix = getRuleVal("search_suffix");
                if (!searchSuffix.isEmpty()) {
                    url = url + searchSuffix;
                }
                // 使用 search_header 请求头
                String searchHeader = getRuleVal("search_header");
                JSONObject searchHeaders = null;
                if (!searchHeader.isEmpty()) {
                    searchHeaders = parseHeader(searchHeader);
                } else if (search != null) {
                    searchHeaders = search.optJSONObject("header");
                }
                str = fetchUrl(url, searchHeaders);
            }
            str = Utils.getRegion(str, search);
            // 应用 search_twice 二次截取（支持 || 条件选择器 + key--前缀 + [替换] 后处理器）
            String searchTwice = getRuleVal("search_twice");
            if (!searchTwice.isEmpty()) {
                str = applySecondCut(str, applyOrSelector(searchTwice));
            }
            // 搜索模式 1：直接返回原始内容（适用于 JSON API 搜索）
            String searchMode = getRuleVal("search_mode", "0");
            if ("1".equals(searchMode)) {
                return str;
            }
            // 先当JSON解析试试
            String r = parseSearchResult(str);
            if (r != null && !r.isEmpty()) {
                return r;
            }

            // search 没有 vod_id 规则时，从 list 继承
            if (!search.has("vod_id")) {
                JSONObject list = rule.getJSONObject("list");
                search.put("vod_id", list.getJSONArray("vod_id"));
            }
            // 如果是 suggest 模式（vod_id 是数字映射），覆盖为列表的 vod_id 规则
            // 保证点击搜索结果能正确进入详情页
            if (search.has("vod_id") && "id".equals(search.optString("vod_id", ""))) {
                JSONObject list = rule.getJSONObject("list");
                search.put("vod_id", list.getJSONArray("vod_id"));
            }

            JSONArray videos = new JSONArray();
            Set<String> set = new HashSet<String>();
            int pos = 0;
            ArrayList<Integer> urlnodes = null;

            JSONArray lookback = search.optJSONArray("search");
            if (lookback == null || Utils.getLookbackCount(lookback) <= 0) {
                lookback = Utils.getLookbackArray(search);
            }

            int lookup = -1;
            while (lookback != null) {
                pos = str.indexOf(lookback.getString(0), pos);
                if (pos == -1) break;

                ArrayList<Integer> arr = null;
                int blockPos = 0;
                String nd ="";
                do {
                    arr = HtmlNodeHlper.findUpNodes(str, pos - 1, lookback.getInt(4));
                    if (urlnodes == null) {
                        urlnodes = arr;
                        blockPos = arr.get(arr.size() - 1);
                    } else {
                        blockPos = Utils.findBlockPos(urlnodes, arr);
                    }
                    nd = HtmlNodeHlper.nodeString(str, blockPos);
                    // 检查是否回看层数过多，如果回看导数过多会导致加载不出来数据或一页只加载一条数据，需要进行修正
                    if(lookup < 0){
                        int count = Utils.getSubStringCount(nd, lookback.getString(0));
                        if(count > 3 && lookback.getInt(4)>1){
                            lookback.put(4, lookback.getInt(4)-1);
                            urlnodes = null;
                            blockPos=0;
                            nd="";
                            SpiderDebug.log(String.format("找到过多的url匹配项(%d)，降低匹配层级为%d", count, lookback.get(4)));
                        }else if (count > 1 && lookback.getInt(4) > 1) {
                            // 新增：一个 block 中出现多次前缀（不止1个视频），强制降层级
                            lookback.put(4, Math.max(1, lookback.getInt(4)-1));
                            urlnodes = null;
                            blockPos = 0;
                            nd = "";
                            SpiderDebug.log(String.format("检测到多条目(%d)，强制降低lookback到%d", count, lookback.get(4)));
                        }
                        else if(lookup == -1){
                            String pic = guess_value_vod_pic(nd,0); //尝试找一下图片，如果没找到的话增加一级
                            String vName = guess_value_vod_name(nd,0);
                            if(pic.isEmpty()||vName.isEmpty()){
                                lookback.put(4, lookback.getInt(4)+1);
                                urlnodes = null;
                                blockPos=0;
                                nd="";
                                lookup = -2; // 只退一次
                                SpiderDebug.log(String.format("当前层级未找到(%s)，增加匹配层级为%d",  pic.isEmpty()? "图片": "标题",  lookback.getInt(4)));
                            }else{
                                lookup = lookback.getInt(4);
                            }
                        }else{
                            lookup = lookback.getInt(4);
                        }
                    }
                }while (lookup < 0);

                pos = blockPos + nd.length();
                blockPos = 0;
                String vod_id = Utils.findSubString(nd, blockPos, search.getJSONArray("vod_id"));
                if (!set.contains(vod_id)) {
                    // filter_word：搜索结果过滤词（包含即跳过）
                    String filterWord = getRuleVal("filter_word");
                    if (!filterWord.isEmpty()) {
                        boolean containsFilter = false;
                        // 先提取搜索标题用于过滤
                        String searchName = Utils.findSubString(nd, blockPos, search.optJSONArray("vod_name"));
                        for (String word : filterWord.split(",")) {
                            String trimmed = word.trim();
                            if (!trimmed.isEmpty() && (vod_id.contains(trimmed) || searchName.contains(trimmed))) {
                                containsFilter = true;
                                break;
                            }
                        }
                        if (containsFilter) continue;
                    }
                    set.add(vod_id);
                    JSONObject v = new JSONObject();
                    v.put("vod_id", vod_id);
                    v.put("vod_name", Utils.findSubString(nd, blockPos, search.optJSONArray("vod_name")));
                    v.put("vod_pic", addHttpPrefix(Utils.findSubString(nd, blockPos, search.optJSONArray("vod_pic"))));

                    if (v.getString("vod_pic").isEmpty()) {
                        v.put("vod_pic", guess_value_vod_pic(nd, 0));
                    }
                    if (getRuleVal("PicNeedProxy").equals("1")) {
                        String pic = v.getString("vod_pic");
                        if (!pic.isEmpty()) {
                            v.put("vod_pic", fixCover(pic, url));
                        }
                    }
                    v.put("vod_remarks", Utils.findSubString(nd, blockPos, search.optJSONArray("vod_remarks")));

                    if (v.getString("vod_name").isEmpty()) {
                        v.put("vod_name", guess_value_vod_name(nd, 0));
                    }

                    if (v.getString("vod_pic").isEmpty()) {
                        v.put("vod_pic", guess_value_vod_pic(nd, 0));
                    }
                    // 随便整点remark
                    if (v.getString("vod_remarks").isEmpty()) {
                        String vod_name = v.getString("vod_name");
                        v.put("vod_remarks", guess_value_vod_remarks(nd, 0, vod_name));
                    }
                    v.put("vod_id", Base64.encodeToString(v.toString().getBytes(StandardCharsets.UTF_8), base64Flag));
                    videos.put(v);
                }
            }

            // 倒序
            if (reverseOrder) {
                JSONArray reversed = new JSONArray();
                for (int i = videos.length() - 1; i >= 0; i--) {
                    reversed.put(videos.get(i));
                }
                videos = reversed;
            }

            JSONObject result = new JSONObject();
            result.put("list", videos);
            return result.toString();
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    protected String fetchUrl(String url, JSONObject h) {
        String html = OkHttp.string(url, getHeaders(url));
        html = this.jumpbtwaf(url, html, h);
        return html.replaceAll("<!--.+?-->", "").replace("\r\n","").replace("\n","");  // 移除注释
    }

    // Unicode转中文（来自XBiubiu/XYQBiu）
    protected String convertUnicodeToCh(String str) {
        if (str == null || !str.contains("\\u")) return str;
        Pattern pattern = Pattern.compile("(\\\\u(\\w{4}))");
        Matcher matcher = pattern.matcher(str);
        while (matcher.find()) {
            String unicodeNum = matcher.group(2);
            char c = (char) Integer.parseInt(unicodeNum, 16);
            str = str.replace(matcher.group(1), String.valueOf(c));
        }
        return str;
    }

    // 统一获取页面内容（来自123.txt）
    protected String fetch(String webUrl) {
        String html = OkHttp.string(webUrl, getHeaders(webUrl));
        html = jumpbtwaf(webUrl, html);
        html = convertUnicodeToCh(html);
        return html.replaceAll("<!--.+?-->", "").replace("\r\n", "").replace("\n", "");
    }

    // extractField / cleanHtml 工具方法（来自123.txt）
    protected String extractField(String block, String rule) {
        if (rule == null || rule.isEmpty()) return "";
        if (rule.contains("&&")) {
            String[] se = rule.split("&&", 2);
            ArrayList<String> r = subContent(block, se[0], se[1]);
            return r.isEmpty() ? "" : cleanHtml(r.get(0));
        }
        return cleanHtml(rule);
    }

    protected String cleanHtml(String s) {
        if (s == null) return "";
        return s.replaceAll("<[^>]+>", "")
                .replaceAll("\\&[a-zA-Z]{1,10};", "")
                .replaceAll("[(/>)<]", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    // POST请求（来自123.txt）
    private String fetchPost(String webUrl) {
        try {
            String postUrl = webUrl.split("\\?")[0].replace("？？", "?");
            String body = webUrl.contains("?") ? webUrl.split("\\?")[1].split(";")[0] : "";
            if (body.startsWith("{")) {
                return convertUnicodeToCh(OkHttp.post(postUrl, body, getHeaders(postUrl)));
            } else {
                LinkedHashMap<String, String> params = new LinkedHashMap<>();
                for (String p : body.split("&")) {
                    int idx = p.indexOf("=");
                    if (idx > 0) params.put(p.substring(0, idx), p.substring(idx + 1));
                }
                return convertUnicodeToCh(OkHttp.post(postUrl, params, getHeaders(postUrl)));
            }
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        return "";
    }

    // POST请求搜索（来自XYQBiu）
    protected String postSearch(String wd, boolean z) {
        try {
            JSONObject search = rule.optJSONObject("search");
            if (search == null) return "";
            String url = search.getString("url");
            JSONObject params = search.optJSONObject("post");
            if (params == null) {
                params = search.optJSONObject("postBody");
            }
            if (params == null) return "";
            HashMap<String, String> reqpayload = new HashMap<>();
            Iterator<String> iter = params.keys();
            while (iter.hasNext()) {
                String key = iter.next();
                String value = params.getString(key).replace("{wd}", wd);
                reqpayload.put(key, value);
            }
            HashMap<String, String> header = getHeaders(url);
            header.put("content-type", "application/x-www-form-urlencoded");
            return convertUnicodeToCh(OkHttp.post(url, reqpayload, header));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "";
    }

    protected String jumpbtwaf(String webUrl, String html) {
        try {
            if (!rule.optBoolean("btwaf", false)) return html;
            for (int i = 0; i < 3; i++) {
                if (html.contains("检测中") && html.contains("btwaf")) {
                    JSONArray keys = new JSONArray();
                    keys.put("btwaf=");
                    keys.put("\"");
                    String btwaf = Utils.findSubString(html, 0, keys);
                    String bturl = webUrl + "?btwaf=" + btwaf;
                    Map<String, String> headers = getHeaders(webUrl);
                    okhttp3.Response response = OkHttp.newCall(bturl, headers);
                    for (String name : response.headers().names()) {
                        if ("set-cookie".equalsIgnoreCase(name)) {
                            String cookie = TextUtils.join(";", response.headers(name));
                            if (!rule.has("header")) rule.put("header", new JSONObject());
                            rule.getJSONObject("header").put("cookie", cookie);
                            break;
                        }
                    }
                    html = OkHttp.string(webUrl, getHeaders(webUrl));
                }
                if (!html.contains("检测中") && !html.contains("btwaf")) break;
            }
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        return html;
    }

    protected String jumpbtwaf(String webUrl, String html, JSONObject h) {
        try {
            // 没有配置btwaf不执行下面的代码
            if (!rule.optBoolean("btwaf", false)) {
                return html;
            }

            if (html.contains("检测中") && html.contains("跳转中") && html.contains("btwaf")) {
                JSONArray keys = new JSONArray();
                keys.put("btwaf=");
                keys.put("\"");
                String btwaf = Utils.findSubString(html, 0, keys);
                String bturl = webUrl + "?btwaf=" + btwaf;

                okhttp3.Response response = OkHttp.newCall(bturl, getHeaders(webUrl));
                for (String name : response.headers().names()) {
                    if ("set-cookie".equalsIgnoreCase(name)) {
                        String btcookie = TextUtils.join(";", response.headers(name));
                        if (!rule.has("header")) {
                            rule.put("header", new JSONObject());
                        }
                        rule.getJSONObject("header").put("cookie", btcookie);
                        break;
                    }
                }
                html = fetchUrl(webUrl, h);
            }
            if (!html.contains("检测中") && !html.contains("btwaf")) {
                return html;
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        return html;
    }

    // 图片代理（来自XYQBiu）
    protected String fixCover(String cover, String site) {
        try {
            return "proxy://do=XBPQ&site=" + URLEncoder.encode(site, "UTF-8") + "&pic=" + URLEncoder.encode(cover, "UTF-8");
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        return cover;
    }

    private static HashMap<String, String> XBPQPicHeader = null;

    public static Object[] loadPic(Map<String, String> prmap) {
        try {
            String site = java.net.URLDecoder.decode(prmap.get("site"), "UTF-8");
            String pic = java.net.URLDecoder.decode(prmap.get("pic"), "UTF-8");

            if (XBPQPicHeader == null) {
                XBPQPicHeader = new HashMap<>();
                XBPQPicHeader.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/94.0.4606.54 Safari/537.36");
                XBPQPicHeader.put("referer", site);
            }
            Object[] result = OkHttp.proxy(pic, XBPQPicHeader);
            if (result != null && ((Integer) result[0]) == 200) {
                java.io.ByteArrayInputStream stream = new java.io.ByteArrayInputStream((byte[]) result[2]);
                Object[] proxyResult = new Object[3];
                proxyResult[0] = 200;
                proxyResult[1] = (String) result[1];
                proxyResult[2] = stream;
                return proxyResult;
            }
        } catch (Throwable th) {
            th.printStackTrace();
        }
        return null;
    }

}

