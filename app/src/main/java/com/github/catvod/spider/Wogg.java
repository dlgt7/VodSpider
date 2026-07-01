package com.github.catvod.spider;

import android.content.Context;
import android.text.TextUtils;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Json;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import org.json.JSONObject;

import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Wogg Spider - 网盘资源搜索站点
 * 继承自 PanWebSite（网盘站点基类），用于爬取玩偶哥等网盘资源站点
 * 
 * 主要功能：
 * 1. 支持阿里云盘、UC盘、百度盘、夸克盘等多种网盘链接
 * 2. 从 HTML 页面解析网盘分享链接和提取码
 * 3. 提供分类浏览、搜索、详情查看等功能
 */
public class Wogg extends PanWebSite {

    // ==================== 静态常量 - 正则表达式 ====================
    
    /** 阿里云盘链接匹配模式 */
    private static final Pattern ALIYUN_PATTERN = Pattern.compile(
        "(www\\.aliyundrive\\.com|www\\.alipan\\.com)/s/([^/]+)(/folder/([^/]+))?"
    );
    
    /** UC盘链接匹配模式 */
    private static final Pattern UC_PATTERN = Pattern.compile(
        "drive\\.uc\\.cn/s/([0-9a-fA-F]{8,20})(?:\\?pwd=([^&]+))?(?:\\?public=1)?(?:[#&].*)?$"
    );
    
    /** 百度盘链接匹配模式 */
    private static final Pattern BAIDU_PATTERN = Pattern.compile(
        "pan\\.baidu\\.com/(?:s/1([^?]+)\\?pwd=([^&]+)|share/init\\?surl=([^&]+)(?:&pwd=([^&]+))?)"
    );
    
    // ==================== 静态常量 - URL 和字符串 ====================
    
    /** 默认站点地址 */
    private static final String DEFAULT_SITE = "https://tvfan.xxooo.cf";
    
    /** 搜索 URL 模板 */
    private static final String SEARCH_URL_TEMPLATE = "/index.php/vodsearch/%s----------%s---.html";
    
    /** 分类 URL 模板（第一种格式） */
    private static final String CATEGORY_URL_TEMPLATE_1 = "%s/vodshow/%s-%s-%s-%s-----%s---%s.html";
    
    /** 分类 URL 模板（第二种格式，备用） */
    private static final String CATEGORY_URL_TEMPLATE_2 = "%s/index.php/vodshow/%s-%s-%s-%s-----%s---%s.html";
    
    /** 豆瓣图片 User-Agent */
    private static final String DOUBAN_USER_AGENT = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/116.0.0.0 Mobile Safari/537.36";
    
    /** 豆瓣 Referer */
    private static final String DOUBAN_REFERER = "https://www.douban.com";
    
    // ==================== 静态常量 - 分类配置 ====================
    
    /** 分类 ID 数组 */
    private static final String[] CATEGORY_IDS = {"44", "1", "2", "3", "4", "5", "6"};
    
    /** 分类名称数组 */
    private static final String[] CATEGORY_NAMES = {"臻彩视觉", "玩偶电影", "玩偶剧集", "动漫", "综艺", "音乐", "短剧"};
    
    /** 过滤器配置 JSON（用于首页） */
    private static final String FILTERS_JSON = "{\"1\":[{\"key\":\"class\",\"name\":\"剧情\",\"value\":[{\"n\":\"全部\",\"v\":\"\"},{\"n\":\"喜剧\",\"v\":\"喜剧\"},{\"n\":\"动作\",\"v\":\"动作\"},{\"n\":\"科幻\",\"v\":\"科幻\"},{\"n\":\"剧情\",\"v\":\"剧情\"}]},{\"key\":\"area\",\"name\":\"地区\",\"value\":[{\"n\":\"全部\",\"v\":\"\"},{\"n\":\"中国大陆\",\"v\":\"中国大陆\"},{\"n\":\"美国\",\"v\":\"美国\"},{\"n\":\"日本\",\"v\":\"日本\"}]},{\"key\":\"year\",\"name\":\"年份\",\"value\":[{\"n\":\"全部\",\"v\":\"\"},{\"n\":\"2026\",\"v\":\"2026\"},{\"n\":\"2025\",\"v\":\"2025\"},{\"n\":\"2024\",\"v\":\"2024\"}]},{\"key\":\"by\",\"name\":\"排序\",\"value\":[{\"n\":\"时间\",\"v\":\"time\"},{\"n\":\"人气\",\"v\":\"hits\"}]}],\"2\":[{\"key\":\"class\",\"name\":\"剧情\",\"value\":[{\"n\":\"全部\",\"v\":\"\"}]},{\"key\":\"by\",\"name\":\"排序\",\"value\":[{\"n\":\"时间\",\"v\":\"time\"}]}],\"44\":[{\"key\":\"by\",\"name\":\"排序\",\"value\":[{\"n\":\"时间\",\"v\":\"time\"}]}]}";

    // ==================== 构造函数 ====================
    
    public Wogg() {
        super();
    }

    // ==================== 静态工具方法 ====================
    
    /**
     * 从 Element 中尝试多个属性名，返回第一个非空值
     * 
     * @param element Jsoup Element 对象
     * @param attrNames 属性名数组（按优先级顺序）
     * @return 第一个非空的属性值，或空字符串
     */
    private static String getFirstNonEmptyAttr(Element element, String... attrNames) {
        if (element == null || attrNames == null) return "";
        for (String attrName : attrNames) {
            String value = element.attr(attrName);
            if (!TextUtils.isEmpty(value)) {
                return value;
            }
        }
        return "";
    }
    
    /**
     * 为豆瓣图片 URL 添加特殊请求头
     * 豆瓣图片需要特定的 User-Agent 和 Referer 才能访问
     * 
     * @param url 图片 URL
     * @return 添加了请求头信息的 URL（格式：url@Headers={...}）
     */
    private static String addDoubanHeaders(String url) {
        if (TextUtils.isEmpty(url) || !url.contains("douban")) {
            return url;
        }
        try {
            JSONObject headers = new JSONObject();
            headers.put("User-Agent", DOUBAN_USER_AGENT);
            headers.put("Referer", DOUBAN_REFERER);
            return url + "@Headers=" + headers.toString();
        } catch (Exception e) {
            return url;
        }
    }
    
    /**
     * 从 HashMap 中获取指定 key 的值，不存在则返回空字符串
     * 
     * @param key 键名
     * @param map HashMap 对象
     * @return 对应的值，或空字符串
     */
    private static String getFromHashMap(String key, HashMap<String, String> map) {
        if (map == null) return "";
        String value = map.get(key);
        return value == null ? "" : value;
    }
    
    /**
     * 处理图片 URL - 提取 http 开头的部分并添加豆瓣 headers
     * 
     * @param picUrl 原始图片 URL
     * @return 处理后的图片 URL
     */
    private static String processImageUrl(String picUrl) {
        if (TextUtils.isEmpty(picUrl)) return "";
        int httpIndex = picUrl.lastIndexOf("http");
        if (httpIndex > 0) {
            picUrl = picUrl.substring(httpIndex);
            picUrl = addDoubanHeaders(picUrl);
        }
        return picUrl;
    }
    
    /**
     * 处理提取码格式 - 将"提取码：xxx"转换为"?pwd=xxx"
     * 
     * @param url 包含提取码的 URL
     * @return 处理后的 URL
     */
    private static String processExtractCode(String url) {
        if (url == null) return "";
        Pattern pattern = Pattern.compile("提取码[\\s:：]*");
        Matcher matcher = pattern.matcher(url);
        return matcher.replaceAll("?pwd=");
    }
    
    /**
     * 判断字符串是否包含网盘链接
     * 
     * @param text 待检测字符串
     * @return 是否包含网盘链接
     */
    private static boolean containsPanLink(String text) {
        if (TextUtils.isEmpty(text)) return false;
        Matcher aliMatcher = ALIYUN_PATTERN.matcher(text);
        Matcher ucMatcher = UC_PATTERN.matcher(text);
        Matcher baiduMatcher = BAIDU_PATTERN.matcher(text);
        // 还需要检查 merge/a/H.a（阿里云盘完整链接模式）
        return aliMatcher.find() || ucMatcher.find() || baiduMatcher.find();
    }
    
    /**
     * 判断链接是否为阿里云盘链接
     * 
     * @param url URL 字符串
     * @return 是否为阿里云盘链接
     */
    private static boolean isAliyunLink(String url) {
        return url.contains("aliyundrive") || url.contains("alipan");
    }

    // ==================== HTML 解析方法 ====================
    
    /**
     * 从搜索结果页面解析 Vod 列表
     * 
     * @param doc Jsoup Document 对象
     * @return Vod 列表
     */
    private static ArrayList<Vod> parseSearchResult(Document doc) {
        ArrayList<Vod> list = new ArrayList<>();
        if (doc == null) return list;
        
        Elements items = doc.select(".module-search-item");
        for (Element item : items) {
            // 查找链接元素（优先级：.video-serial > a）
            Element linkEl = item.selectFirst(".video-serial");
            if (linkEl == null) linkEl = item.selectFirst("a");
            if (linkEl == null) continue;
            
            // 查找图片元素
            Element imgEl = item.selectFirst(".module-item-pic > img");
            String picUrl = "";
            if (imgEl != null) {
                picUrl = getFirstNonEmptyAttr(imgEl, "data-src", "src");
                picUrl = processImageUrl(picUrl);
            }
            
            // 查找标签元素
            Element tagEl = item.selectFirst(".video-tag-icon");
            String remark = "";
            if (tagEl != null) {
                remark = tagEl.text();
            }
            
            // 构建 Vod 对象
            String vodId = linkEl.attr("href");
            String vodName = linkEl.attr("title");
            Vod vod = new Vod(vodId, vodName, picUrl, remark);
            list.add(vod);
        }
        return list;
    }
    
    /**
     * 从分类内容页面解析 Vod 列表（跳过"臻彩"分类）
     * 
     * @param doc Jsoup Document 对象
     * @return Vod 列表
     */
    private static ArrayList<Vod> parseCategoryResult(Document doc) {
        ArrayList<Vod> list = new ArrayList<>();
        if (doc == null) return list;
        
        Elements items = doc.select(".module-item");
        for (Element item : items) {
            // 查找链接元素（多种选择器）
            Element linkEl = item.selectFirst(".module-item-titlebox > a");
            if (linkEl == null) linkEl = item.selectFirst(".video-name a");
            if (linkEl == null) linkEl = item.selectFirst("a");
            if (linkEl == null) continue;
            
            // 获取标题（优先 title 属性，否则用 text）
            String vodName;
            if (linkEl.hasAttr("title")) {
                vodName = linkEl.attr("title");
            } else {
                vodName = linkEl.text().trim();
            }
            
            // 跳过"臻彩"分类（特殊处理）
            if ("臻彩".equals(vodName)) continue;
            
            // 查找图片元素
            Element imgEl = item.selectFirst(".module-item-pic > img");
            if (imgEl == null) imgEl = item.selectFirst("img");
            String picUrl = "";
            if (imgEl != null) {
                picUrl = getFirstNonEmptyAttr(imgEl, "data-src", "src");
                picUrl = processImageUrl(picUrl);
            }
            
            // 查找备注元素
            Element remarkEl = item.selectFirst(".module-item-text");
            String remark = "";
            if (remarkEl != null) {
                remark = remarkEl.text();
            }
            
            // 构建 Vod 对象
            String vodId = linkEl.attr("href");
            Vod vod = new Vod(vodId, vodName, picUrl, remark);
            list.add(vod);
        }
        return list;
    }
    
    /**
     * 从详情页面解析网盘链接列表
     * 
     * @param doc Jsoup Document 对象
     * @return 网盘链接列表（已去重和排序）
     */
    private ArrayList<String> parsePanLinksFromDetail(Document doc) {
        ArrayList<String> primaryLinks = new ArrayList<>();  // 阿里云盘/UC盘链接
        ArrayList<String> secondaryLinks = new ArrayList<>(); // 其他网盘链接
        
        if (doc == null) return primaryLinks;
        
        Elements linkElements = doc.select(".module-row-text");
        for (Element el : linkElements) {
            String linkText = el.attr("data-clipboard-text").trim();
            if (TextUtils.isEmpty(linkText)) continue;
            
            // 判断是否包含网盘链接
            if (!containsPanLink(linkText)) continue;
            
            // 处理提取码格式
            linkText = processExtractCode(linkText);
            
            // 简单验证链接格式
            if (TextUtils.isEmpty(linkText)) continue;
            
            // 判断链接类型
            Matcher aliMatcher = ALIYUN_PATTERN.matcher(linkText);
            Matcher ucMatcher = UC_PATTERN.matcher(linkText);
            
            if (aliMatcher.find() || ucMatcher.find()) {
                primaryLinks.add(linkText);
            } else {
                Matcher baiduMatcher = BAIDU_PATTERN.matcher(linkText);
                if (baiduMatcher.find()) {
                    secondaryLinks.add(linkText);
                }
            }
        }
        
        // 合并列表（优先阿里云盘/UC盘）
        if (primaryLinks.size() > 1) {
            Collections.reverse(primaryLinks);
        }
        ArrayList<String> result = new ArrayList<>(secondaryLinks);
        result.addAll(primaryLinks);
        
        // 去重
        deduplicateLinks(result);
        
        return result;
    }
    
    /**
     * 对网盘链接列表去重（基于链接中的 share_id）
     * 
     * @param links 链接列表
     */
    private void deduplicateLinks(List<String> links) {
        if (links == null || links.size() < 2) return;
        HashSet<String> seen = new HashSet<>();
        for (int i = 0; i < links.size(); i++) {
            String link = links.get(i);
            // 提取 share_id 作为唯一标识
            String shareId = extractShareId(link);
            if (seen.contains(shareId)) {
                links.remove(i);
                i--;
            } else {
                seen.add(shareId);
            }
        }
    }
    
    /**
     * 从网盘链接中提取 share_id
     * 
     * @param link 网盘链接
     * @return share_id 字符串
     */
    private String extractShareId(String link) {
        if (TextUtils.isEmpty(link)) return "";
        Matcher matcher;
        // 阿里云盘
        matcher = ALIYUN_PATTERN.matcher(link);
        if (matcher.find()) return matcher.group(2);
        // UC盘
        matcher = UC_PATTERN.matcher(link);
        if (matcher.find()) return matcher.group(1);
        // 百度盘
        matcher = BAIDU_PATTERN.matcher(link);
        if (matcher.find()) {
            String surl = matcher.group(1);
            return surl != null ? surl : matcher.group(3);
        }
        return link; // 无法提取时返回原始链接
    }

    // ==================== Spider 标准方法 ====================
    
    /**
     * 初始化 Spider
     * 
     * 功能：
     * 1. 解析配置中的站点 URL（支持字符串或数组）
     * 2. 对多个站点进行 HEAD 请求检测，选择第一个可用的站点
     * 3. 调用父类 init 方法传递 token
     * 
     * @param context Android Context
     * @param extend JSON 配置字符串（包含 site 和 token）
     */
    @Override
    public void init(Context context, String extend) throws Exception {
        // 调用 merge/A/a.Z0() 初始化（简化：直接调用父类方法）
        super.init(context, "");
        
        // 解析配置
        JsonObject config = Json.safeObject(extend);
        if (config == null || !config.has("site")) {
            return;
        }
        
        JsonElement siteElement = config.get("site");
        String siteUrl = null;
        
        // 处理 site 配置（可能是字符串或数组）
        if (siteElement.isJsonArray()) {
            JsonArray sites = siteElement.getAsJsonArray();
            // 遍历站点列表，检测可用性
            for (int i = 0; i < sites.size(); i++) {
                String site = sites.get(i).getAsString();
                site = normalizeUrl(site);
                
                // HEAD 请求检测站点可用性
                if (checkSiteAvailable(site)) {
                    siteUrl = site;
                    break;
                }
            }
            // 如果所有站点都不可用，使用第一个
            if (siteUrl == null && sites.size() > 0) {
                siteUrl = normalizeUrl(sites.get(0).getAsString());
            }
        } else {
            siteUrl = normalizeUrl(siteElement.getAsString());
        }
        
        // 更新站点 URL
        if (!TextUtils.isEmpty(siteUrl)) {
            this.siteUrl = siteUrl;
        }
        
        // 获取 token 并调用父类 init
        String token = config.has("token") ? config.get("token").getAsString() : "";
        super.init(context, token);
    }
    
    /**
     * 检测站点可用性（HEAD 请求）
     * 
     * @param siteUrl 站点 URL
     * @return 是否可用（响应码 200-299）
     */
    private boolean checkSiteAvailable(String siteUrl) {
        try {
            URL url = new URL(siteUrl + "/");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setInstanceFollowRedirects(true);
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setRequestMethod("HEAD");
            
            // 设置 User-Agent
            Map<String, String> headers = getHeaders();
            if (headers.containsKey("User-Agent")) {
                conn.setRequestProperty("User-Agent", headers.get("User-Agent"));
            }
            
            int code = conn.getResponseCode();
            conn.disconnect();
            return code >= 200 && code < 300;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * URL 格式化 - 去除末尾多余的斜杠
     * 
     * @param url 原始 URL
     * @return 格式化后的 URL
     */
    private String normalizeUrl(String url) {
        if (TextUtils.isEmpty(url)) return this.siteUrl;
        return url.trim().replaceAll("/+$", "");
    }
    
    /**
     * 首页内容 - 返回分类列表和推荐内容
     * 
     * @param filter 是否返回过滤器
     * @return JSON 字符串（包含 classes、filters、vod 列表）
     */
    @Override
    public String homeContent(boolean filter) throws Exception {
        ArrayList<Class> classes = new ArrayList<>();
        
        // 构建 7 个固定分类
        for (int i = 0; i < CATEGORY_IDS.length; i++) {
            classes.add(new Class(CATEGORY_IDS[i], CATEGORY_NAMES[i]));
        }
        
        // 拉取首页 HTML
        String html = OkHttp.string(this.siteUrl, getHeaders());
        Document doc = Jsoup.parse(html);
        ArrayList<Vod> vodList = parseCategoryResult(doc);
        
        // 截取前 12 个推荐项
        int limit = Math.min(vodList.size(), 12);
        if (limit > 0) {
            vodList = new ArrayList<>(vodList.subList(0, limit));
        }
        
        // 构建返回结果
        try {
            JSONObject filtersObj = new JSONObject(FILTERS_JSON);
            return Result.get()
                .classes(classes)
                .vod(vodList)
                .filters(filtersObj)
                .string();
        } catch (Exception e) {
            return Result.get()
                .classes(classes)
                .vod(vodList)
                .string();
        }
    }
    
    /**
     * 分类内容 - 根据分类 ID 和过滤条件查询
     * 
     * @param tid 分类 ID
     * @param pg 页码
     * @param filter 是否应用过滤器
     * @param extend 过滤条件 HashMap
     * @return JSON 字符串（包含 vod 列表和分页信息）
     */
    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        // 获取 cateId（优先从 extend 中获取）
        String cateId = tid;
        if (extend != null && extend.containsKey("cateId")) {
            cateId = extend.get("cateId");
        }
        
        // 获取过滤参数
        String area = getFromHashMap("area", extend);
        String year = getFromHashMap("year", extend);
        String sortBy = getFromHashMap("by", extend);
        String classType = getFromHashMap("class", extend);
        
        // 构建分类 URL（第一种格式）
        String url = String.format(CATEGORY_URL_TEMPLATE_1,
            this.siteUrl, cateId, area, classType, year, pg, sortBy);
        
        // 拉取 HTML
        String html = OkHttp.string(url, getHeaders());
        Document doc = Jsoup.parse(html);
        
        // 检查是否有内容，若无内容则尝试第二种 URL 格式
        Elements items = doc.select(".module-item");
        if (items.isEmpty()) {
            url = String.format(CATEGORY_URL_TEMPLATE_2,
                this.siteUrl, cateId, area, classType, year, pg, sortBy);
            html = OkHttp.string(url, getHeaders());
            doc = Jsoup.parse(html);
        }
        
        // 解析 Vod 列表
        ArrayList<Vod> vodList = parseCategoryResult(doc);
        
        // 构建分页结果
        int page;
        try {
            page = Integer.parseInt(pg);
        } catch (Exception e) {
            page = 1;
        }
        int count = vodList.size();
        int total = vodList.size();
        
        return Result.get()
            .page(page, 1, count, total)
            .vod(vodList)
            .string();
    }
    
    /**
     * 详情内容 - 解析影片详情和网盘链接列表
     * 
     * @param ids 影片 ID 列表（ID 可能是完整 URL 或相对路径）
     * @return JSON 字符串（包含 Vod 对象和 playUrl 列表）
     */
    @Override
    public String detailContent(List<String> ids) throws Exception {
        String vodId = ids.get(0);
        
        // 构建详情页 URL
        String detailUrl;
        if (vodId.startsWith("http")) {
            detailUrl = vodId;
        } else {
            detailUrl = this.siteUrl + vodId;
        }
        
        // 拉取详情页 HTML
        String html = OkHttp.string(detailUrl, getHeaders());
        Document doc = Jsoup.parse(html);
        
        // 解析网盘链接列表
        ArrayList<String> panLinks = parsePanLinksFromDetail(doc);
        
        // 如果没有找到网盘链接，调用父类方法（可能走阿里云盘专用解析）
        if (panLinks.isEmpty()) {
            return super.detailContent(ids);
        }
        
        // 构建 Vod 对象
        Vod vod = new Vod();
        vod.setVodId(vodId);
        
        // 解析标题
        Element titleEl = doc.selectFirst(".video-info-header > .page-title");
        if (titleEl == null) titleEl = doc.selectFirst("h1");
        vod.setVodName(titleEl != null ? titleEl.text() : "");
        
        // 解析封面图片
        Element imgEl = doc.selectFirst(".module-item-pic img");
        if (imgEl != null) {
            String picUrl = getFirstNonEmptyAttr(imgEl, "data-src", "src");
            picUrl = processImageUrl(picUrl);
            vod.setVodPic(picUrl);
        }
        
        // 解析其他信息
        // 简介
        Element introEl = doc.selectFirst(".video-info-content");
        if (introEl != null) {
            vod.setVodContent(introEl.text());
        }
        
        // 演员
        Element actorEl = doc.selectFirst(".video-info-actor");
        if (actorEl != null) {
            vod.setVodActor(actorEl.text());
        }
        
        // 导演
        Element directorEl = doc.selectFirst(".video-info-director");
        if (directorEl != null) {
            vod.setVodDirector(directorEl.text());
        }
        
        // 备注
        Element remarkEl = doc.selectFirst(".video-info-remarks");
        if (remarkEl != null) {
            vod.setVodRemarks(remarkEl.text());
        }
        
        // 年份
        Element yearEl = doc.selectFirst(".video-info-year");
        if (yearEl != null) {
            vod.setVodYear(yearEl.text());
        }
        
        // 地区
        Element areaEl = doc.selectFirst(".video-info-area");
        if (areaEl != null) {
            vod.setVodArea(areaEl.text());
        }
        
        // 构建播放列表（每个网盘链接作为一个播放源）
        ArrayList<String> playFrom = new ArrayList<>();
        ArrayList<String> playUrl = new ArrayList<>();
        
        for (int i = 0; i < panLinks.size(); i++) {
            String link = panLinks.get(i);
            // 播放源名称
            String sourceName = isAliyunLink(link) ? "阿里云盘" : "网盘";
            playFrom.add(sourceName + "_" + (i + 1));
            
            // 播放 URL（网盘链接）
            playUrl.add(link);
        }
        
        vod.setVodPlayFrom(TextUtils.join("$$$", playFrom));
        vod.setVodPlayUrl(TextUtils.join("$$$", playUrl));
        
        return Result.string(vod);
    }
    
    /**
     * 搜索内容（不带分页） - 调用内部搜索方法
     * 
     * @param key 搜索关键词
     * @param quick 是否快速搜索
     * @return JSON 字符串（包含 vod 列表）
     */
    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        return searchContentInternal(key, "1");
    }
    
    /**
     * 搜索内容（带分页） - 调用内部搜索方法
     * 
     * @param key 搜索关键词
     * @param quick 是否快速搜索
     * @param pg 页码
     * @return JSON 字符串（包含 vod 列表）
     */
    @Override
    public String searchContent(String key, boolean quick, String pg) throws Exception {
        return searchContentInternal(key, pg);
    }
    
    /**
     * 内部搜索方法 - 构建搜索 URL 并解析结果
     * 
     * @param key 搜索关键词（需 URL 编码）
     * @param pg 页码
     * @return JSON 字符串（包含 vod 列表）
     */
    private String searchContentInternal(String key, String pg) throws Exception {
        try {
            // URL 编码关键词
            String encodedKey = URLEncoder.encode(key, "UTF-8");
            
            // 构建搜索 URL
            String url = this.siteUrl + String.format(SEARCH_URL_TEMPLATE, encodedKey, pg);
            
            // 拉取 HTML
            String html = OkHttp.string(url, getHeaders());
            Document doc = Jsoup.parse(html);
            
            // 解析结果
            ArrayList<Vod> vodList = parseSearchResult(doc);
            
            return Result.string(vodList);
        } catch (Exception e) {
            return "";
        }
    }
}