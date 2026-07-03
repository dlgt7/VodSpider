package com.github.catvod.spider;

import android.content.Context;
import android.text.TextUtils;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.net.OkHttp;
import com.github.catvod.spider.merge.p0.h;
import com.github.catvod.spider.merge.p0.l;
import com.github.catvod.spider.merge.r0.C0229f;

import org.json.JSONException;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * YGP Spider - 预告片视频源
 * 
 * 提供6huo.com网站的预告片视频资源抓取功能。
 * 支持分类筛选、关键词搜索、详情页解析和播放链接获取。
 * 
 * <p>主要功能:</p>
 * <ul>
 *   <li>首页推荐: 展示最新预告片列表</li>
 *   <li>分类筛选: 按类型、地区、年份、排序方式筛选</li>
 *   <li>搜索功能: 关键词搜索预告片</li>
 *   <li>详情解析: 提取视频信息、播放线路和选集列表</li>
 *   <li>播放嗅探: 返回播放页面URL供WebView嗅探</li>
 * </ul>
 * 
 * <p>字段映射:</p>
 * <ul>
 *   <li>smali 字段 a:JSONObject → Java 字段 filterConfig:JSONObject (分类筛选配置)</li>
 * </ul>
 */
public class YGP extends Spider {

    /** 网站基础URL */
    private static final String BASE_URL = "https://www.6huo.com/";

    /** PC端 User-Agent */
    private static final String PC_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/86.0.4240.198 Safari/537.36";

    /** 移动端 User-Agent */
    private static final String MOBILE_USER_AGENT = "Mozilla/5.0 (Linux; Android 13; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/116.0.0.0 Mobile Safari/537.36";

    /** 默认分类筛选配置JSON */
    private static final String DEFAULT_FILTER_CONFIG = "{\"movlist/\":[" +
        "{\"key\":\"1\",\"name\":\"类型\",\"value\":[" +
        "{\"n\":\"全部\",\"v\":\"\"},{\"n\":\"喜剧\",\"v\":\"喜剧\"},{\"n\":\"爱情\",\"v\":\"爱情\"}," +
        "{\"n\":\"恐怖\",\"v\":\"恐怖\"},{\"n\":\"动作\",\"v\":\"动作\"},{\"n\":\"科幻\",\"v\":\"科幻\"}," +
        "{\"n\":\"剧情\",\"v\":\"剧情\"},{\"n\":\"战争\",\"v\":\"战争\"},{\"n\":\"犯罪\",\"v\":\"犯罪\"}," +
        "{\"n\":\"灾难\",\"v\":\"灾难\"},{\"n\":\"奇幻\",\"v\":\"奇幻\"},{\"n\":\"悬疑\",\"v\":\"悬疑\"}," +
        "{\"n\":\"惊悚\",\"v\":\"惊悚\"},{\"n\":\"冒险\",\"v\":\"冒险\"}" +
        "]},{" +
        "\"key\":\"0\",\"name\":\"地区\",\"value\":[" +
        "{\"n\":\"全部\",\"v\":\"\"},{\"n\":\"大陆\",\"v\":\"大陆\"},{\"n\":\"香港\",\"v\":\"香港\"}," +
        "{\"n\":\"台湾\",\"v\":\"台湾\"},{\"n\":\"美国\",\"v\":\"美国\"},{\"n\":\"法国\",\"v\":\"法国\"}," +
        "{\"n\":\"英国\",\"v\":\"英国\"},{\"n\":\"日本\",\"v\":\"日本\"},{\"n\":\"韩国\",\"v\":\"韩国\"}," +
        "{\"n\":\"德国\",\"v\":\"德国\"},{\"n\":\"泰国\",\"v\":\"泰国\"},{\"n\":\"印度\",\"v\":\"印度\"}," +
        "{\"n\":\"其他\",\"v\":\"其他\"}" +
        "]},{" +
        "\"key\":\"2\",\"name\":\"年份\",\"value\":[" +
        "{\"n\":\"全部\",\"v\":\"\"},{\"v\":\"2026\",\"n\":\"2026\"},{\"v\":\"2025\",\"n\":\"2025\"}," +
        "{\"n\":\"2024\",\"v\":\"2024\"},{\"n\":\"2023\",\"v\":\"2023\"},{\"n\":\"2022\",\"v\":\"2022\"}," +
        "{\"n\":\"2021\",\"v\":\"2021\"},{\"n\":\"2020\",\"v\":\"2020\"},{\"n\":\"2019\",\"v\":\"2019\"}," +
        "{\"n\":\"2018\",\"v\":\"2018\"},{\"n\":\"2017\",\"v\":\"2017\"},{\"n\":\"2016\",\"v\":\"2016\"}," +
        "{\"n\":\"2015\",\"v\":\"2015\"},{\"n\":\"2014\",\"v\":\"2014\"},{\"n\":\"2013\",\"v\":\"2013\"}," +
        "{\"n\":\"2012\",\"v\":\"2012\"},{\"n\":\"2011\",\"v\":\"2011\"},{\"n\":\"2010\",\"v\":\"2010\"}," +
        "{\"n\":\"2009\",\"v\":\"2009\"},{\"n\":\"2008\",\"v\":\"2008\"},{\"n\":\"2007\",\"v\":\"2007\"}," +
        "{\"n\":\"2006\",\"v\":\"2006\"},{\"n\":\"2005\",\"v\":\"2005\"},{\"n\":\"2004\",\"v\":\"2004\"}," +
        "{\"n\":\"2003\",\"v\":\"2003\"},{\"n\":\"2002\",\"v\":\"2002\"},{\"n\":\"2001\",\"v\":\"2001\"}," +
        "{\"n\":\"2000\",\"v\":\"2000\"},{\"n\":\"1999\",\"v\":\"1999\"},{\"n\":\"1998\",\"v\":\"1998\"}," +
        "{\"n\":\"1980\",\"v\":\"1980\"}" +
        "]},{" +
        "\"key\":\"3\",\"name\":\"排序\",\"value\":[" +
        "{\"n\":\"最近更新\",\"v\":\"\"},{\"n\":\"热门\",\"v\":\"hot\"},{\"n\":\"上映时间\",\"v\":\"pubtime\"}" +
        "]}" +
        "]}";

    /**
     * 分类筛选配置
     * 包含类型、地区、年份、排序等筛选维度
     */
    private JSONObject filterConfig;

    /**
     * 判断字符串是否全为数字
     * 
     * @param str 待判断的字符串
     * @return true表示全为数字，false表示包含非数字字符或为空
     */
    private static boolean isNumeric(String str) {
        if (str == null || str.isEmpty()) {
            return false;
        }
        for (int i = 0; i < str.length(); i++) {
            if (!Character.isDigit(str.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * 获取HTML文档对象
     * 使用PC端User-Agent请求指定URL并解析为HTML文档
     * 
     * @param url 请求URL
     * @return HTML文档对象 (merge.p0.h)
     */
    private h fetchHtmlDocument(String url) {
        HashMap<String, String> headers = com.github.catvod.spider.merge.Z.d.t(
            "User-Agent", PC_USER_AGENT,
            "Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8"
        );
        headers.put("Accept-Language", "zh-CN,zh;q=0.9");
        return com.github.catvod.spider.merge.B.a.N3(OkHttp.string(url, headers));
    }

    /**
     * 解析视频列表
     * 从HTML文档中提取预告片视频信息列表
     * 
     * @param document HTML文档对象
     * @return 视频列表 (ArrayList<Vod>)
     */
    private ArrayList<Vod> parseVideoList(h document) {
        ArrayList<Vod> videoList = new ArrayList<>();
        
        // 检查是否无结果
        if (document.Y().contains("没有找到您想要的结果哦")) {
            return videoList;
        }

        // 查找视频列表元素
        C0229f videoElements = document.U("div.inner-2col-main div.movlist > ul li > a");
        
        // 如果主选择器未找到，尝试备用选择器
        if (videoElements.isEmpty()) {
            l largestList = null;
            int maxSize = 0;
            
            // 查找最大的视频列表容器
            for (l listContainer : document.U("div.inner-2col-main div.movlist, div.movlist")) {
                C0229f items = listContainer.U("li[data-index] > a");
                if (items.isEmpty()) {
                    items = listContainer.U("li > a");
                }
                if (items.size() > maxSize) {
                    maxSize = items.size();
                    largestList = listContainer;
                }
            }
            
            // 使用最大容器的元素列表
            if (largestList != null) {
                C0229f items = largestList.U("li[data-index] > a");
                if (items.isEmpty()) {
                    items = largestList.U("li > a");
                }
                videoElements = items;
            } else {
                // 最后尝试全局选择器
                videoElements = document.U("div.inner-2col-main li[data-index] > a");
            }
        }

        // 解析每个视频元素
        for (l videoElement : videoElements) {
            String videoId = videoElement.c("href");
            
            // 提取标题
            l titleElement = videoElement.V("span.item-title");
            String title = titleElement != null ? titleElement.c("title") : videoElement.Y();
            if (title == null || title.isEmpty()) {
                title = titleElement != null ? titleElement.Y() : videoElement.c("title");
            }
            
            // 提取图片URL
            l imageElement = videoElement.V("img");
            String imageUrl = imageElement != null ? imageElement.c("src") : "";
            
            // 提取发布时间
            l timeElement = videoElement.V("span.item-pubtime");
            String publishTime = timeElement != null ? timeElement.Y() : "";
            if (publishTime.isEmpty()) {
                C0229f spanElements = videoElement.U("span");
                if (spanElements.size() > 1) {
                    publishTime = spanElements.get(spanElements.size() - 1).Y();
                }
            }
            
            // 添加到视频列表
            videoList.add(new Vod(videoId, title, buildVideoUrl(imageUrl), publishTime));
        }
        
        return videoList;
    }

    /**
     * 构建视频图片URL
     * 为图片URL添加请求头信息，用于防盗链处理
     * 
     * @param imageUrl 原始图片URL
     * @return 添加请求头信息的完整URL字符串
     * @throws JSONException JSON构建异常
     */
    private String buildVideoUrl(String imageUrl) throws JSONException {
        if (imageUrl == null || imageUrl.isEmpty()) {
            return imageUrl;
        }
        
        // 补全URL
        if (!imageUrl.startsWith("http")) {
            imageUrl = BASE_URL + imageUrl.replaceFirst("^/+", "");
        }
        
        // 构建请求头JSON
        JSONObject headerJson = new JSONObject();
        headerJson.put("User-Agent", MOBILE_USER_AGENT);
        headerJson.put("Referer", "https://www.douban.com");
        
        // 添加@Headers标记
        StringBuilder result = com.github.catvod.spider.merge.Z.d.r(imageUrl, "@Headers=");
        result.append(headerJson.toString());
        
        return result.toString();
    }

    /**
     * 使用正则表达式提取文本
     * 从源文本中提取匹配正则表达式的第一个分组内容
     * 
     * @param pattern 正则表达式Pattern对象
     * @param text 源文本
     * @return 提取的文本，如果未匹配则返回空字符串
     */
    private String extractPattern(Pattern pattern, String text) {
        if (pattern == null || text == null) {
            return text == null ? "" : text;
        }
        
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1).trim() : "";
    }

    /**
     * 初始化Spider
     * 加载分类筛选配置
     * 
     * @param context Android Context对象
     */
    @Override
    public void init(Context context) {
        this.filterConfig = new JSONObject(DEFAULT_FILTER_CONFIG);
    }

    /**
     * 获取首页内容
     * 
     * @param filter 是否返回筛选配置
     * @return 首页内容JSON字符串
     * @throws Exception 异常
     */
    @Override
    public String homeContent(boolean filter) throws Exception {
        ArrayList<Class> classes = new ArrayList<>();
        classes.add(new Class("movlist/", "肥猫陪你看预告"));
        
        ArrayList<Vod> videoList = parseVideoList(fetchHtmlDocument(BASE_URL));
        
        return filter ? Result.string(classes, videoList, this.filterConfig) : Result.string(classes, videoList);
    }

    /**
     * 获取分类内容列表
     * 
     * @param tid 分类ID (如 "movlist/")
     * @param pg 页码
     * @param filter 是否启用筛选
     * @param extend 筛选参数HashMap
     * @return 分类内容JSON字符串
     * @throws Exception 异常
     */
    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        // 构建筛选参数数组 [地区, 类型, 年份, 排序, 页码]
        String[] filterParams = new String[5];
        filterParams[0] = "";  // 地区 (key=0)
        filterParams[1] = "";  // 类型 (key=1)
        filterParams[2] = "";  // 年份 (key=2)
        filterParams[3] = "";  // 排序 (key=3)
        filterParams[4] = pg;  // 页码
        
        // 处理筛选参数
        if (extend != null) {
            for (String key : extend.keySet()) {
                String value = extend.get(key);
                if (value == null) {
                    value = "";
                }
                filterParams[Integer.parseInt(key)] = URLEncoder.encode(value, "UTF-8");
            }
        }
        
        // 构建分类URL
        String categoryUrl = BASE_URL + tid + TextUtils.join("_", filterParams);
        
        // 获取HTML文档
        h document = fetchHtmlDocument(categoryUrl);
        
        // 解析视频列表
        ArrayList<Vod> videoList = parseVideoList(document);
        
        // 解析分页信息
        int currentPage = Integer.parseInt(pg);
        int maxPage = currentPage;
        
        C0229f pageLinks = document.U("p.page-nav a");
        if (!pageLinks.isEmpty()) {
            // 查找最大页码
            for (l pageLink : pageLinks) {
                String pageText = pageLink.Y().trim();
                if (isNumeric(pageText)) {
                    int pageNum = Integer.parseInt(pageText);
                    if (pageNum > maxPage) {
                        maxPage = pageNum;
                    }
                }
            }
            
            // 获取当前页码
            l currentPageElement = document.V("p.page-nav a.current");
            if (currentPageElement != null && isNumeric(currentPageElement.Y())) {
                currentPage = Integer.parseInt(currentPageElement.Y());
            }
            
            // 如果没有找到最大页码，则使用当前页码
            if (maxPage <= 0) {
                maxPage = currentPage;
            }
        }
        
        // 计算总数量
        int totalCount = maxPage <= 1 ? videoList.size() : maxPage * 30;
        
        return Result.string(currentPage, maxPage, 30, totalCount, videoList);
    }

    /**
     * 获取详情内容
     * 
     * @param ids 视频ID列表
     * @return 详情内容JSON字符串
     * @throws Exception 异常
     */
    @Override
    public String detailContent(List<String> ids) throws Exception {
        // 构建详情URL
        String detailUrl = BASE_URL + ids.get(0).replaceFirst("^/+", "");
        
        // 获取HTML文档
        h document = fetchHtmlDocument(detailUrl);
        
        // 提取视频信息
        l imageElement = document.V("div.movie-title-mpic > a > img, div.movie-title-mpic img");
        String imageUrl = imageElement != null ? imageElement.c("src") : "";
        
        String title = document.U("h1.movie-name").h();
        
        // 提取地区和类型
        String region = "";
        String type = "";
        for (l linkElement : document.U("div.movie-title-detail a")) {
            String linkHref = linkElement.c("href");
            if (linkHref.contains("country")) {
                region = linkElement.Y();
            }
            if (linkHref.contains("movietype")) {
                StringBuilder typeBuilder = com.github.catvod.spider.merge.Z.d.q(type);
                typeBuilder.append(linkElement.Y());
                typeBuilder.append("/");
                type = typeBuilder.toString();
            }
        }
        
        // 提取详情文本
        String detailText = document.U("div.movie-title-detail p").h();
        
        // 使用正则提取导演、年份、主演、简介
        String director = extractPattern(Pattern.compile("导演：(.+)主演"), detailText);
        String year = extractPattern(Pattern.compile("上映：(\\w+)"), detailText);
        String actor = extractPattern(Pattern.compile("主演：(.+)剧情"), detailText);
        String description = extractPattern(Pattern.compile("剧情：(.+)\\(详细\\)"), detailText);
        
        // 解析播放线路和选集
        ArrayList<String> playFromList = new ArrayList<>();
        ArrayList<String> playUrlList = new ArrayList<>();
        
        C0229f playTables = document.U("#tabwrapper-all > table.tlist, #tabwrapper-all > .tlist");
        if (playTables.isEmpty()) {
            playTables = document.U("#tabwrapper-all table.tlist");
        }
        
        for (l playTable : playTables) {
            l headerElement = playTable.V("th");
            String routeName = headerElement != null ? headerElement.Y() : "预告";
            if (routeName.isEmpty()) {
                routeName = "预告";
            }
            
            ArrayList<String> episodeList = new ArrayList<>();
            
            for (l episodeLink : playTable.U("td a.tlist-bbs-tdtitle")) {
                String episodeUrl = episodeLink.c("href");
                if (!episodeUrl.contains("/bbs/")) {
                    episodeList.add(episodeLink.Y() + "$" + episodeUrl);
                }
            }
            
            if (!episodeList.isEmpty()) {
                playFromList.add(routeName);
                playUrlList.add(episodeList.size() > 1 ? TextUtils.join("#", episodeList) : episodeList.get(0));
            }
        }
        
        // 如果没有播放信息，添加默认预告
        if (playFromList.isEmpty()) {
            playFromList.add("暂无预告");
            playUrlList.add("暂无预告$www");
        }
        
        // 构建Vod对象
        Vod vod = new Vod(ids.get(0), title, buildVideoUrl(imageUrl), "");
        vod.setTypeName(type);
        vod.setVodYear(year);
        vod.setVodArea(region);
        vod.setVodActor(actor);
        vod.setVodDirector(director);
        vod.setVodContent(description);
        vod.setVodPlayFrom(TextUtils.join("$$$", playFromList));
        vod.setVodPlayUrl(TextUtils.join("$$$", playUrlList));
        
        return Result.string(vod);
    }

    /**
     * 获取播放内容
     * 返回播放页面URL供WebView嗅探视频真实地址
     * 
     * @param flag 播放标识
     * @param id 视频ID (播放链接)
     * @param list flags参数列表
     * @return 播放内容JSON字符串
     * @throws Exception 异常
     */
    @Override
    public String playerContent(String flag, String id, List<String> list) throws Exception {
        String playUrl = BASE_URL + id.replaceFirst("^/+", "");
        return Result.get().parse(1).url(playUrl).string();
    }

    /**
     * 搜索内容
     * 
     * @param keyword 搜索关键词
     * @param quick 是否快速搜索
     * @return 搜索结果JSON字符串
     * @throws Exception 异常
     */
    @Override
    public String searchContent(String keyword, boolean quick) throws Exception {
        String searchUrl = BASE_URL + "?keyword=" + URLEncoder.encode(keyword, "UTF-8") + "&view=search";
        ArrayList<Vod> searchResult = parseVideoList(fetchHtmlDocument(searchUrl));
        return Result.string(searchResult);
    }
}