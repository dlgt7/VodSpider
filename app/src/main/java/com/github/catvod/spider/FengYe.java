package com.github.catvod.spider;

import android.content.Context;
import android.text.TextUtils;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.net.OkHttp;

import org.json.JSONObject;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 枫叶视频 Spider
 * 支持腾讯VIP、B站VIP、优酷VIP精选及电影/电视剧/动漫/综艺/热门短剧分类
 * 自动检测可用站点并动态切换发布源
 */
public class FengYe extends Spider {

    private static final String DEFAULT_HOST = "https://www.cd-zj.com";
    private static final String DEFAULT_PUBLISH = "https://www.vip1949.com/";
    private static final String PARSE_URL = "https://fgsrg.hzqingshan.com/player/?url=";
    private static final long CACHE_DURATION = 300000L; // 5分钟缓存

    private static long lastCheckTime = 0L;
    private static final HashMap<String, String> playSourceMap = new HashMap<>();
    private static String cachedHost = "";

    private final HashMap<String, String> defaultHeaders = new HashMap<>();
    private String host = DEFAULT_HOST;
    private String publishUrl = DEFAULT_PUBLISH;

    public FengYe() {
        this.defaultHeaders.put("User-Agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36");
        this.defaultHeaders.put("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8");
        this.defaultHeaders.put("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");
        this.defaultHeaders.put("Connection", "keep-alive");
    }

    /**
     * URL规范化：补https前缀、替换HTML实体
     */
    public static String normalizeUrl(String url) {
        if (TextUtils.isEmpty(url)) return "";
        if (url.startsWith("//")) return "https:" + url;
        return url.replace("&amp;", "&");
    }

    /**
     * 解析HTML页面视频列表
     */
    public static ArrayList<Vod> parseVideoList(String html) {
        ArrayList<Vod> list = new ArrayList<>();
        LinkedHashSet<String> idSet = new LinkedHashSet<>();
        Document doc = Jsoup.parse(html);
        Elements items = doc.select("a.public-list-exp");
        Pattern idPattern = Pattern.compile("/detail/(\\d+)\\.html");

        for (Element item : items) {
            String href = item.attr("href");
            Matcher matcher = idPattern.matcher(href);
            if (!matcher.find()) continue;

            String id = matcher.group(1);
            if (!idSet.add(id)) continue; // 去重

            Element img = item.selectFirst("img");
            String title = item.attr("title");
            if (TextUtils.isEmpty(title) && img != null) {
                title = img.attr("alt");
            }

            String pic = "";
            if (img != null) {
                pic = normalizeUrl(img.attr("data-src"));
            }

            Element remarkEl = item.selectFirst(".ft2, .public-list-prb");
            String remarks = "";
            if (remarkEl != null) {
                remarks = remarkEl.text().trim();
            }

            Elements yearItems = item.select("span.public-prt");
            String year = "";
            if (!yearItems.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                for (Element yearItem : yearItems) {
                    if (sb.length() > 0) sb.append(",");
                    sb.append(yearItem.text());
                }
                year = sb.toString();
            }

            String name = title == null ? "" : title.trim();
            Vod vod = new Vod(id, name, pic, remarks);
            vod.setVodYear(year);
            list.add(vod);
        }

        return list;
    }

    /**
     * 从HashMap获取参数值或返回默认值
     */
    public static String getParamOrDefault(String key, String defaultValue, HashMap<String, String> params) {
        String value = params.get(key);
        return TextUtils.isEmpty(value) ? defaultValue : value;
    }

    /**
     * 移除URL尾部斜杠
     */
    public static String removeTrailingSlash(String url) {
        if (TextUtils.isEmpty(url)) return url;
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        return url;
    }

    /**
     * 构建请求头（添加Referer）
     */
    public HashMap<String, String> buildHeaders(String baseUrl) {
        HashMap<String, String> headers = new HashMap<>(defaultHeaders);
        String referer = removeTrailingSlash(baseUrl) + "/";
        headers.put("Referer", referer);
        return headers;
    }

    /**
     * 获取HTML内容
     */
    public String fetchHtml(String path) {
        try {
            String url = path;
            if (!url.startsWith("http")) {
                StringBuilder sb = new StringBuilder(host);
                if (!path.startsWith("/")) sb.append("/");
                sb.append(path);
                url = sb.toString();
            }

            HashMap<String, String> headers = buildHeaders(host);
            return OkHttp.string(url, headers);
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 检查站点可用性
     */
    public boolean checkSiteAvailable(String testUrl) {
        testUrl = removeTrailingSlash(testUrl);
        String[] testPaths = {
                testUrl + "/",
                testUrl + "/cupfox-list/1--------1---.html"
        };

        for (String path : testPaths) {
            try {
                HashMap<String, String> headers = buildHeaders(testUrl);
                String html = OkHttp.string(path, headers);
                // 检查响应内容是否包含视频列表特征
                if (!TextUtils.isEmpty(html) && (html.contains("public-list-exp") || html.contains("/detail/"))) {
                    return true;
                }
            } catch (Exception e) {
                // 继续检查下一个路径
            }
        }

        return false;
    }

    @Override
    public void init(Context context, String extend) {
        this.publishUrl = DEFAULT_PUBLISH;

        if (!TextUtils.isEmpty(extend)) {
            String url = extend.trim();
            if (!url.startsWith("http")) {
                try {
                    JSONObject json = new JSONObject(url);
                    String publish = json.optString("publish");
                    if (TextUtils.isEmpty(publish)) {
                        publish = json.optString("url");
                    }
                    if (!TextUtils.isEmpty(publish)) {
                        url = publish.trim();
                    }
                } catch (Exception e) {
                    // 解析失败，使用原始值
                }
            }

            if (url.startsWith("http")) {
                this.publishUrl = url;
            }
        }

        long now = System.currentTimeMillis();
        if (!TextUtils.isEmpty(cachedHost) && (now - lastCheckTime) < CACHE_DURATION) {
            this.host = cachedHost;
            return;
        }

        // 检测可用站点
        try {
            HashMap<String, String> headers = buildHeaders(DEFAULT_HOST);
            String html = OkHttp.string(this.publishUrl, headers);
            if (!TextUtils.isEmpty(html)) {
                ArrayList<String> urlList = new ArrayList<>();
                Pattern urlPattern = Pattern.compile("url\\s*:\\s*\"(https?://[^\"]+)\"");
                Matcher matcher = urlPattern.matcher(html);
                while (matcher.find()) {
                    String foundUrl = removeTrailingSlash(matcher.group(1));
                    if (!TextUtils.isEmpty(foundUrl) && !urlList.contains(foundUrl)) {
                        urlList.add(foundUrl);
                    }
                }

                if (!urlList.isEmpty()) {
                    for (String testUrl : urlList) {
                        if (checkSiteAvailable(testUrl)) {
                            this.host = testUrl;
                            break;
                        }
                    }
                }

                // 解析播放源配置并填充 playSourceMap
                playSourceMap.clear();
                Pattern playSourcePattern = Pattern.compile("\"(\\w+)\"\\s*:\\s*\"(https?://[^\"]+)\"");
                Matcher playMatcher = playSourcePattern.matcher(html);
                while (playMatcher.find()) {
                    playSourceMap.put(playMatcher.group(1), playMatcher.group(2));
                }
                // 兜底：如果解析失败，使用内置默认值
                if (playSourceMap.isEmpty()) {
                    playSourceMap.put("default", "https://fgsrg.hzqingshan.com/api/");
                }
            }
        } catch (Exception e) {
            // 使用默认站点
        }

        cachedHost = this.host;
        lastCheckTime = now;
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        ArrayList<Class> classes = new ArrayList<>();
        classes.add(new Class("/label/qq", "腾讯VIP精选"));
        classes.add(new Class("/label/bli", "B站VIP精选"));
        classes.add(new Class("/label/youku", "优酷VIP精选"));
        classes.add(new Class("2", "电视剧"));
        classes.add(new Class("1", "电影"));
        classes.add(new Class("4", "动漫"));
        classes.add(new Class("3", "综艺"));
        classes.add(new Class("5", "热门短剧"));

        if (!filter) {
            return Result.string(classes, new ArrayList<>());
        }

        // 过滤器JSON配置
        String filterJson = "{\"2\":[{\"key\":\"class\",\"name\":\"类型\",\"value\":[{\"n\":\"全部\",\"v\":\"2\"},{\"n\":\"国产剧\",\"v\":\"13\"},{\"n\":\"日韩剧\",\"v\":\"15\"},{\"n\":\"海外剧\",\"v\":\"16\"}]},{\"key\":\"area\",\"name\":\"地区\",\"value\":[{\"n\":\"全部\",\"v\":\"\"},{\"n\":\"大陆\",\"v\":\"大陆\"},{\"n\":\"香港\",\"v\":\"香港\"},{\"n\":\"台湾\",\"v\":\"台湾\"},{\"n\":\"美国\",\"v\":\"美国\"},{\"n\":\"韩国\",\"v\":\"韩国\"},{\"n\":\"日本\",\"v\":\"日本\"}]},{\"key\":\"year\",\"name\":\"年份\",\"value\":[{\"n\":\"全部\",\"v\":\"\"},{\"n\":\"2026\",\"v\":\"2026\"},{\"n\":\"2025\",\"v\":\"2025\"},{\"n\":\"2024\",\"v\":\"2024\"},{\"n\":\"2023\",\"v\":\"2023\"},{\"n\":\"2022\",\"v\":\"2022\"},{\"n\":\"2021\",\"v\":\"2021\"},{\"n\":\"2020\",\"v\":\"2020\"}]},{\"key\":\"lang\",\"name\":\"语言\",\"value\":[{\"n\":\"全部\",\"v\":\"\"},{\"n\":\"国语\",\"v\":\"国语\"},{\"n\":\"英语\",\"v\":\"英语\"},{\"n\":\"粤语\",\"v\":\"粤语\"}]},{\"key\":\"letter\",\"name\":\"字母\",\"value\":[{\"n\":\"全部\",\"v\":\"\"},{\"n\":\"A\",\"v\":\"A\"},{\"n\":\"B\",\"v\":\"B\"},{\"n\":\"C\",\"v\":\"C\"},{\"n\":\"D\",\"v\":\"D\"}]},{\"key\":\"sort\",\"name\":\"排序\",\"value\":[{\"n\":\"时间\",\"v\":\"time\"},{\"n\":\"人气\",\"v\":\"hits\"},{\"n\":\"评分\",\"v\":\"score\"}]}],\"1\":[{\"key\":\"class\",\"name\":\"类型\",\"value\":[{\"n\":\"全部\",\"v\":\"1\"},{\"n\":\"动作片\",\"v\":\"6\"},{\"n\":\"喜剧片\",\"v\":\"7\"},{\"n\":\"恐怖片\",\"v\":\"8\"},{\"n\":\"科幻片\",\"v\":\"9\"},{\"n\":\"爱情片\",\"v\":\"10\"},{\"n\":\"剧情片\",\"v\":\"11\"},{\"n\":\"战争片\",\"v\":\"12\"}]},{\"key\":\"area\",\"name\":\"地区\",\"value\":[{\"n\":\"全部\",\"v\":\"\"},{\"n\":\"大陆\",\"v\":\"大陆\"},{\"n\":\"香港\",\"v\":\"香港\"},{\"n\":\"台湾\",\"v\":\"台湾\"},{\"n\":\"美国\",\"v\":\"美国\"},{\"n\":\"韩国\",\"v\":\"韩国\"},{\"n\":\"日本\",\"v\":\"日本\"}]},{\"key\":\"year\",\"name\":\"年份\",\"value\":[{\"n\":\"全部\",\"v\":\"\"},{\"n\":\"2026\",\"v\":\"2026\"},{\"n\":\"2025\",\"v\":\"2025\"},{\"n\":\"2024\",\"v\":\"2024\"},{\"n\":\"2023\",\"v\":\"2023\"},{\"n\":\"2022\",\"v\":\"2022\"},{\"n\":\"2021\",\"v\":\"2021\"},{\"n\":\"2020\",\"v\":\"2020\"}]},{\"key\":\"lang\",\"name\":\"语言\",\"value\":[{\"n\":\"全部\",\"v\":\"\"},{\"n\":\"国语\",\"v\":\"国语\"},{\"n\":\"英语\",\"v\":\"英语\"},{\"n\":\"粤语\",\"v\":\"粤语\"}]},{\"key\":\"letter\",\"name\":\"字母\",\"value\":[{\"n\":\"全部\",\"v\":\"\"},{\"n\":\"A\",\"v\":\"A\"},{\"n\":\"B\",\"v\":\"B\"},{\"n\":\"C\",\"v\":\"C\"},{\"n\":\"D\",\"v\":\"D\"}]},{\"key\":\"sort\",\"name\":\"排序\",\"value\":[{\"n\":\"时间\",\"v\":\"time\"},{\"n\":\"人气\",\"v\":\"hits\"},{\"n\":\"评分\",\"v\":\"score\"}]}],\"4\":[{\"key\":\"class\",\"name\":\"类型\",\"value\":[{\"n\":\"全部\",\"v\":\"4\"},{\"n\":\"国产动漫\",\"v\":\"25\"},{\"n\":\"日韩动漫\",\"v\":\"26\"}]},{\"key\":\"year\",\"name\":\"年份\",\"value\":[{\"n\":\"全部\",\"v\":\"\"},{\"n\":\"2026\",\"v\":\"2026\"},{\"n\":\"2025\",\"v\":\"2025\"},{\"n\":\"2024\",\"v\":\"2024\"}]},{\"key\":\"sort\",\"name\":\"排序\",\"value\":[{\"n\":\"时间\",\"v\":\"time\"},{\"n\":\"人气\",\"v\":\"hits\"},{\"n\":\"评分\",\"v\":\"score\"}]}],\"3\":[{\"key\":\"class\",\"name\":\"类型\",\"value\":[{\"n\":\"全部\",\"v\":\"3\"},{\"n\":\"大陆综艺\",\"v\":\"21\"},{\"n\":\"日韩综艺\",\"v\":\"22\"}]},{\"key\":\"year\",\"name\":\"年份\",\"value\":[{\"n\":\"全部\",\"v\":\"\"},{\"n\":\"2026\",\"v\":\"2026\"},{\"n\":\"2025\",\"v\":\"2025\"},{\"n\":\"2024\",\"v\":\"2024\"}]},{\"key\":\"sort\",\"name\":\"排序\",\"value\":[{\"n\":\"时间\",\"v\":\"time\"},{\"n\":\"人气\",\"v\":\"hits\"},{\"n\":\"评分\",\"v\":\"score\"}]}]}";

        JSONObject filters;
        try {
            filters = new JSONObject(filterJson);
        } catch (Exception e) {
            filters = new JSONObject();
        }

        return Result.string(classes, new ArrayList<>(), filters);
    }

    @Override
    public String homeVideoContent() throws Exception {
        String html = fetchHtml("/");
        ArrayList<Vod> list = parseVideoList(html);
        return Result.string(list);
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        // 标签分类（VIP精选）
        if (tid.startsWith("/label")) {
            String url = tid + "/page/" + pg + ".html";
            String html = fetchHtml(url);
            ArrayList<Vod> list = parseVideoList(html);

            int page;
            try {
                page = Integer.parseInt(pg);
            } catch (Exception e) {
                page = 1;
            }

            int total = list.size() < 24 ? page : page + 2;
            return Result.string(page, total, 24, total * 24, list);
        }

        // 构建筛选参数
        HashMap<String, String> params = new HashMap<>();
        if (extend != null) {
            for (Map.Entry<String, String> entry : extend.entrySet()) {
                if (!TextUtils.isEmpty(entry.getValue())) {
                    params.put(entry.getKey(), entry.getValue());
                }
            }
        }

        String tidValue = getParamOrDefault("tid", tid, params);
        String classValue = getParamOrDefault("class", tidValue, params);
        String area = getParamOrDefault("area", "", params);
        String genre = getParamOrDefault("genre", "", params);
        String year = getParamOrDefault("year", "", params);
        String lang = getParamOrDefault("lang", "", params);
        String letter = getParamOrDefault("letter", "", params);
        String sort = getParamOrDefault("sort", "", params);

        // 无筛选条件时的简化URL
        if (TextUtils.isEmpty(area) && TextUtils.isEmpty(genre) && TextUtils.isEmpty(year)
                && TextUtils.isEmpty(lang) && TextUtils.isEmpty(letter) && TextUtils.isEmpty(sort)) {
            String url = "/cupfox-list/" + tidValue + "--------" + pg + "---.html";
            String html = fetchHtml(url);
            ArrayList<Vod> list = parseVideoList(html);

            int page;
            try {
                page = Integer.parseInt(pg);
            } catch (Exception e) {
                page = 1;
            }

            // 解析总页数
            Document doc = Jsoup.parse(html);
            Elements pageLinks = doc.select("a.page-link");
            int totalPage = page;
            for (Element link : pageLinks) {
                if ("尾页".equals(link.text())) {
                    Pattern pagePattern = Pattern.compile("---(\\d+)---");
                    Matcher matcher = pagePattern.matcher(link.attr("href"));
                    if (matcher.find()) {
                        totalPage = Integer.parseInt(matcher.group(1));
                        break;
                    }
                }
            }

            if (list.isEmpty()) totalPage = 0;
            return Result.string(page, totalPage, 36, 9999, list);
        }

        // 构建完整筛选URL（补入页码 pg）
        StringBuilder urlBuilder = new StringBuilder("/cupfox-list/");
        urlBuilder.append(tidValue).append("-");
        urlBuilder.append(area).append("-");
        urlBuilder.append(sort).append("-");
        urlBuilder.append(genre).append("-");
        urlBuilder.append(lang).append("-");
        urlBuilder.append(letter).append("-");
        urlBuilder.append(year).append("---").append(pg).append("---.html");

        String html = fetchHtml(urlBuilder.toString());
        ArrayList<Vod> list = parseVideoList(html);

        int page;
        try {
            page = Integer.parseInt(pg);
        } catch (Exception e) {
            page = 1;
        }

        return Result.string(page, 1, 36, 9999, list);
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        if (ids == null || ids.isEmpty()) {
            return Result.error("详情参数为空");
        }

        String vodId = ids.get(0);
        if (vodId.contains(",")) {
            vodId = vodId.substring(0, vodId.indexOf(","));
        }
        vodId = vodId.trim();

        if (TextUtils.isEmpty(vodId)) {
            return Result.error("影片ID为空");
        }

        String url = "/detail/" + vodId + ".html";
        String html = fetchHtml(url);
        Document doc = Jsoup.parse(html);

        Vod vod = new Vod();
        vod.setVodId(vodId);

        Element titleEl = doc.selectFirst("h3.slide-info-title");
        vod.setVodName(titleEl == null ? "" : titleEl.text().trim());

        Element picEl = doc.selectFirst("img.lazy");
        vod.setVodPic(picEl == null ? "" : normalizeUrl(picEl.attr("data-src")));

        // 解析导演和演员
        Elements infoItems = doc.select(".slide-info");
        String director = "";
        String actor = "";
        for (Element item : infoItems) {
            String text = item.text().trim();
            if (text.startsWith("导演：")) {
                director = text.replace("导演：", "").trim();
            } else if (text.startsWith("演员：")) {
                actor = text.replace("演员：", "").trim();
            }
        }
        vod.setVodDirector(director);
        vod.setVodActor(actor);

        Element contentEl = doc.selectFirst("#height_limit");
        vod.setVodContent(contentEl == null ? "" : contentEl.text().trim());

        // 解析播放线路和剧集
        ArrayList<String> playFrom = new ArrayList<>();
        Elements tabItems = doc.select(".anthology-tab a.swiper-slide");
        for (Element tab : tabItems) {
            String name = tab.text().trim();
            if (!TextUtils.isEmpty(name)) {
                playFrom.add(name);
            }
        }

        ArrayList<String> playUrlList = new ArrayList<>();
        Elements listBoxes = doc.select(".anthology-list-box");
        Pattern playPattern = Pattern.compile("/play/(.*?)\\.html");

        for (int i = 0; i < listBoxes.size(); i++) {
            ArrayList<String> episodes = new ArrayList<>();
            Element listBox = listBoxes.get(i);
            Elements links = listBox.select("li a");

            for (Element link : links) {
                String href = link.attr("href");
                Matcher matcher = playPattern.matcher(href);
                if (!matcher.find()) continue;

                String epName = link.text().trim();
                if (TextUtils.isEmpty(epName)) epName = "正片";

                String playId = matcher.group(1);
                episodes.add(epName + "$" + vodId + "-" + playId);
            }

            if (!episodes.isEmpty() && i < playFrom.size()) {
                // 倒序排列剧集
                ArrayList<String> reversed = new ArrayList<>();
                for (int j = episodes.size() - 1; j >= 0; j--) {
                    reversed.add(episodes.get(j));
                }
                playUrlList.add(TextUtils.join("#", reversed));
            }
        }

        // 匹配线路名称
        ArrayList<String> matchedPlayFrom = new ArrayList<>();
        for (int i = 0; i < playFrom.size() && i < playUrlList.size(); i++) {
            matchedPlayFrom.add(playFrom.get(i));
        }

        vod.setVodPlayFrom(TextUtils.join("$$$", matchedPlayFrom));
        vod.setVodPlayUrl(TextUtils.join("$$$", playUrlList));

        return Result.string(vod);
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        if (TextUtils.isEmpty(id)) {
            return Result.error("播放ID为空");
        }

        id = id.trim();
        if (id.contains("$")) {
            id = id.substring(id.lastIndexOf("$") + 1);
        }

        if (TextUtils.isEmpty(id)) {
            return Result.error("播放ID为空");
        }

        String playUrl;
        if (id.startsWith("http")) {
            playUrl = id;
        } else {
            playUrl = host + "/play/" + id + ".html";
        }

        String html = fetchHtml(playUrl);
        if (TextUtils.isEmpty(html)) {
            return Result.get().url(playUrl).parse(1).header(defaultHeaders).string();
        }

        // 提取player_aaaa配置
        Pattern playerPattern = Pattern.compile("player_aaaa=(.*?)</script>", Pattern.DOTALL);
        Matcher matcher = playerPattern.matcher(html);
        if (!matcher.find()) {
            return Result.get().url(playUrl).parse(1).header(defaultHeaders).string();
        }

        try {
            JSONObject playerJson = new JSONObject(matcher.group(1));
            String videoUrl = playerJson.optString("url");
            String from = playerJson.optString("from");

            if (TextUtils.isEmpty(videoUrl)) {
                return Result.error("无播放地址");
            }

            // 直链视频（m3u8/mp4）
            if (videoUrl.startsWith("http") && (videoUrl.contains(".m3u8") || videoUrl.contains(".mp4"))) {
                return Result.get().url(videoUrl).parse(0).header(defaultHeaders).string();
            }

            // 尝试解析播放源
            String parseApi = playSourceMap.get(from);
            if (!TextUtils.isEmpty(parseApi)) {
                try {
                    HashMap<String, String> parseHeaders = new HashMap<>();
                    parseHeaders.put("User-Agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36");
                    parseHeaders.put("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8");
                    parseHeaders.put("accept-language", "zh-CN,zh;q=0.9");
                    parseHeaders.put("cache-control", "no-cache");
                    parseHeaders.put("pragma", "no-cache");
                    parseHeaders.put("referer", "https://www.ht10010.com/");

                    String parseReqUrl = PARSE_URL + URLEncoder.encode(videoUrl, "UTF-8");
                    String parseHtml = OkHttp.string(parseReqUrl, parseHeaders);

                    Pattern tokenPattern = Pattern.compile("data-te=\"(.*?)\"");
                    Matcher tokenMatcher = tokenPattern.matcher(parseHtml);
                    if (tokenMatcher.find()) {
                        HashMap<String, String> postParams = new HashMap<>();
                        postParams.put("url", videoUrl);
                        postParams.put("token", tokenMatcher.group(1));

                        String result = OkHttp.post(parseApi, postParams, parseHeaders);
                        JSONObject resultJson = new JSONObject(result);
                        if (resultJson.optInt("code") == 200) {
                            String finalUrl = resultJson.optString("url");
                            if (!TextUtils.isEmpty(finalUrl)) {
                                return Result.get().url(finalUrl).parse(0).header(defaultHeaders).string();
                            }
                        }
                    }
                } catch (Exception e) {
                    // 解析失败，回退到嗅探
                }
            }

            return Result.get().url(playUrl).parse(1).header(defaultHeaders).string();

        } catch (Exception e) {
            return Result.get().url(playUrl).parse(1).header(defaultHeaders).string();
        }
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        return searchContent(key, quick, "1");
    }

    @Override
    public String searchContent(String key, boolean quick, String pg) throws Exception {
        String keyword = key == null ? "" : key.trim();
        String url = "/cupfox-search/" + URLEncoder.encode(keyword, "UTF-8") + "----------" + pg + "---.html";
        String html = fetchHtml(url);

        ArrayList<Vod> list = new ArrayList<>();
        LinkedHashSet<String> idSet = new LinkedHashSet<>();
        Document doc = Jsoup.parse(html);
        Elements items = doc.select("a.public-list-exp");
        Pattern idPattern = Pattern.compile("/detail/(\\d+)\\.html");

        for (Element item : items) {
            String href = item.attr("href");
            Matcher matcher = idPattern.matcher(href);
            if (!matcher.find()) continue;

            String id = matcher.group(1);
            if (!idSet.add(id)) continue;

            Element img = item.selectFirst("img");
            String pic = "";
            if (img != null) {
                pic = normalizeUrl(img.attr("data-src"));
            }

            // 尝试获取标题
            Element titleEl = doc.selectFirst("a.thumb-txt[href=\"/detail/" + id + ".html\"]");
            String title;
            if (titleEl != null) {
                title = titleEl.text().trim();
            } else if (img != null) {
                title = img.attr("alt").trim();
            } else {
                title = "";
            }

            Element remarkEl = item.selectFirst(".public-list-prb, .ft2");
            String remarks = remarkEl == null ? "" : remarkEl.text().trim();

            Vod vod = new Vod(id, title, pic, remarks);
            list.add(vod);
        }

        int page;
        try {
            page = Integer.parseInt(pg);
        } catch (Exception e) {
            page = 1;
        }

        return Result.string(page, 1, 36, list.size(), list);
    }
}