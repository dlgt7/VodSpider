package com.github.catvod.spider;

import android.text.TextUtils;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Filter;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Proxy;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Psmp3 评书音频 Spider
 * 
 * 支持 7 位著名评书艺术家（袁阔成、单田芳、田连元、刘兰芳、连丽如、张少佐、战战元）的分类筛选。
 * 通过 HTML 爬虫解析评书列表，使用代理处理 MP3 播放链接。
 */
public class Psmp3 extends Spider {

    // 静态正则表达式（同名不同类型字段需重命名）
    private static final Pattern LIST_PATTERN = Pattern.compile("<li class=\"[^\"]*post_list_li\"[\\s\\S]*?class=\"listtopimg[^\"]*\"[^>]*><img src=\"([^\"]+)\"[\\s\\S]*?<div class=\"fenli\"><a[^>]*>([^<]+)</a></div>[\\s\\S]*?<h2><a href=\"([^\"]+)\">([^<]+)</a></h2>", Pattern.CASE_INSENSITIVE);
    private static final Pattern PLAY_PATTERN = Pattern.compile("\\{name:\\s*\"([^\"]+)\"[^}]*url:\\s*\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern TITLE_PATTERN = Pattern.compile("<h1[^>]*>([^<]+)</h1>", Pattern.CASE_INSENSITIVE);
    private static final Pattern COVER_PATTERN = Pattern.compile("cover:\\s*\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);

    // 歌手分类数组
    private static final String[][] ARTISTS = new String[][]{
            {"ykc", "袁阔成"},
            {"stf", "单田芳"},
            {"tly", "田连元"},
            {"llf", "刘兰芳"},
            {"llr", "连丽如"},
            {"zsz", "张少佐"},
            {"tzy", "战战元"}
    };

    // 常量字符串
    private static final String HOST_URL = "https://www.psmp3.com";
    private static final String REFERER = "https://www.psmp3.com/";
    private static final String USER_AGENT = "Mozilla/5.0 (Linux; Android 12; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36";
    private static final String AUDIO_FORMAT = "audio";

    @Override
    public String homeContent(boolean filter) throws Exception {
        ArrayList<Class> classes = new ArrayList<>();
        LinkedHashMap<String, List<Filter>> filters = new LinkedHashMap<>();

        for (int i = 0; i < 7; i++) {
            String[] artist = ARTISTS[i];
            String artistId = artist[0];
            String artistName = artist[1];

            classes.add(new Class(artistId, artistName));

            if (filter) {
                ArrayList<Filter> artistFilters = new ArrayList<>();
                ArrayList<Filter.Value> values = new ArrayList<>();

                // 添加"全部"选项
                values.add(new Filter.Value("全部", artistId));

                // 根据不同歌手添加特殊分类
                if ("ykc".equals(artistId)) {
                    values.add(new Filter.Value("三国演义", "ykc-sgyy"));
                    values.add(new Filter.Value("水浒梁山", "ykc-sbls"));
                    values.add(new Filter.Value("封神演义", "ykc-fsyy"));
                } else if ("stf".equals(artistId)) {
                    values.add(new Filter.Value("隋唐演义", "stf-styy"));
                    values.add(new Filter.Value("白眉大侠", "stf-bmdx"));
                    values.add(new Filter.Value("三国演义", "stf-sgyy"));
                }

                artistFilters.add(new Filter("cateId", "分类", values));
                filters.put(artistId, artistFilters);
            }
        }

        return filter ? Result.string(classes, filters) : Result.string(classes, new ArrayList<>());
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        // 优先使用 extend 中的 cateId（过滤器选择）
        if (extend != null && !TextUtils.isEmpty(extend.get("cateId"))) {
            tid = extend.get("cateId");
        }

        // 默认分页和分类
        if (TextUtils.isEmpty(pg)) pg = "1";
        if (TextUtils.isEmpty(tid)) tid = "ykc";

        // 拼接 URL："/" + tid + "/" + pg + ".html"
        String url = new StringBuilder("/")
                .append(tid).append("/")
                .append(pg).append(".html")
                .toString();

        String html = fetchHtml(url);
        List<Vod> list = parseVideoList(html);

        int page = Integer.parseInt(pg);
        int limit = 20;
        int total = 9999;
        int count = list.isEmpty() ? page : page + 1;

        return Result.get().page(page, count, limit, total).vod(list).string();
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        String url = ids.get(0);

        // 确保 URL 以 "/" 开头且以 ".html" 结尾
        if (!url.startsWith("/")) url = "/" + url;
        if (!url.endsWith(".html")) url = url + ".html";

        String html = fetchHtml(url);

        // 提取标题
        String title = url;
        Matcher titleMatcher = TITLE_PATTERN.matcher(html);
        if (titleMatcher.find()) {
            title = titleMatcher.group(1).trim();
        }

        // 提取封面
        String cover = "";
        Matcher coverMatcher = COVER_PATTERN.matcher(html);
        if (coverMatcher.find()) {
            cover = normalizeUrl(coverMatcher.group(1));
        }

        // 解析播放列表
        StringBuilder playUrlSb = new StringBuilder();
        Matcher playMatcher = PLAY_PATTERN.matcher(html);
        int episodeCount = 0;

        while (playMatcher.find()) {
            String episodeName = playMatcher.group(1).trim();
            String episodeUrl = normalizeUrl(playMatcher.group(2).trim());

            if (TextUtils.isEmpty(episodeUrl)) continue;

            if (playUrlSb.length() > 0) {
                playUrlSb.append("#");
            }
            playUrlSb.append(episodeName).append("$").append(episodeUrl);
            episodeCount++;
        }

        // 创建 Vod 对象
        String vodId = removeHtmlSuffix(url);
        Vod vod = new Vod(vodId, title, cover, "评书随身听");
        vod.setVodPlayFrom("评书");
        vod.setVodPlayUrl(playUrlSb.toString());

        // 设置备注（共 X 段）
        if (episodeCount > 0) {
            vod.setVodRemarks("共" + episodeCount + "段");
        }

        return Result.string(vod);
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        if (TextUtils.isEmpty(id)) {
            return Result.get().url("").parse(0).string();
        }

        try {
            String playUrl = normalizeUrl(id);
            String proxyUrl = Proxy.getUrl();
            String encodedUrl = URLEncoder.encode(playUrl, "UTF-8");
            String encodedReferer = URLEncoder.encode(REFERER, "UTF-8");

            String finalUrl = new StringBuilder(proxyUrl)
                    .append("?do=mp3&url=").append(encodedUrl)
                    .append("&ref=").append(encodedReferer)
                    .toString();

            return Result.get().url(finalUrl).format(AUDIO_FORMAT).parse(0).string();
        } catch (Exception e) {
            return Result.get().url(normalizeUrl(id)).format(AUDIO_FORMAT).parse(0).string();
        }
    }

    @Override
    public String searchContent(String key, boolean quick, String pg) throws Exception {
        if (TextUtils.isEmpty(key)) {
            return Result.string(new ArrayList<>());
        }

        if (TextUtils.isEmpty(pg)) pg = "1";

        // 搜索 URL："/so/" + URLEncoder.encode(key.trim(), "UTF-8") + "_" + pg + ".html"
        String url = new StringBuilder("/so/")
                .append(URLEncoder.encode(key.trim(), "UTF-8"))
                .append("_").append(pg)
                .append(".html")
                .toString();

        String html = fetchHtml(url);
        List<Vod> list = parseVideoList(html);

        return Result.string(list);
    }

    // 私有方法：HTTP 请求
    private final String fetchHtml(String url) {
        try {
            String fullUrl = url.startsWith("http") ? url : HOST_URL.concat(url);

            HashMap<String, String> headers = new HashMap<>();
            headers.put("User-Agent", USER_AGENT);
            headers.put("Referer", REFERER);

            return OkHttp.string(fullUrl, null, headers);
        } catch (Exception e) {
            return "";
        }
    }

    // 私有方法：格式化播放次数
    private final String formatPlayCount(String count) {
        try {
            long num = Long.parseLong(count);

            if (num >= 10000) {
                return String.format(Locale.CHINA, "%.1f万播放", (double) num / 10000.0);
            } else if (num > 0) {
                return new StringBuilder().append(num).append("播放").toString();
            }
        } catch (Exception e) {
        }
        return "";
    }

    // 私有方法：URL 协议补全
    private final String normalizeUrl(String url) {
        if (TextUtils.isEmpty(url)) return "";

        if (url.startsWith("//")) return "https:".concat(url);
        if (url.startsWith("http")) return url;
        if (url.startsWith("/")) return HOST_URL.concat(url);
        return HOST_URL.concat("/").concat(url);
    }

    // 私有方法：解析视频列表
    private final List<Vod> parseVideoList(String html) {
        ArrayList<Vod> list = new ArrayList<>();

        // 使用 LIST_PATTERN 解析带封面的列表项
        Matcher listMatcher = LIST_PATTERN.matcher(html);
        while (listMatcher.find()) {
            String cover = normalizeUrl(listMatcher.group(1));
            String playCount = listMatcher.group(2);
            String vodUrl = extractVodId(listMatcher.group(3));
            String vodName = listMatcher.group(4).trim();

            if (TextUtils.isEmpty(vodUrl)) continue;

            String vodId = removeHtmlSuffix(vodUrl);
            String remark = TextUtils.isEmpty(playCount) ? formatPlayCount(playCount) : playCount.trim();

            list.add(new Vod(vodId, vodName, cover, remark));
        }

        // 如果 LIST_PATTERN 没匹配到，使用备用正则（无封面列表）
        if (list.isEmpty()) {
            Pattern fallbackPattern = Pattern.compile("<li class=\"[^\"]*post_list_li\"[\\s\\S]*?<h2><a href=\"([^\"]+)\">([^<]+)</a></h2>[\\s\\S]*?<span class=\"jzicon-jzyan\"></span>\\s*<span>(\\d+)</span>", Pattern.CASE_INSENSITIVE);
            Matcher fallbackMatcher = fallbackPattern.matcher(html);

            while (fallbackMatcher.find()) {
                String vodUrl = extractVodId(fallbackMatcher.group(1));
                String vodName = fallbackMatcher.group(2).trim();
                String playCount = fallbackMatcher.group(3);

                if (TextUtils.isEmpty(vodUrl)) continue;

                String vodId = removeHtmlSuffix(vodUrl);
                String remark = formatPlayCount(playCount);

                list.add(new Vod(vodId, vodName, "", remark));
            }
        }

        return list;
    }

    // 私有方法：提取 Vod ID（去除 host 前缀）
    private final String extractVodId(String url) {
        if (TextUtils.isEmpty(url)) return "";

        url = url.trim();

        // 如果是完整 URL，去除 host 前缀
        if (url.startsWith("http")) {
            url = url.replace(HOST_URL, "");
        }

        // 确保 URL 以 "/" 开头
        if (!url.startsWith("/")) {
            url = "/" + url;
        }

        return url;
    }

    // 私有方法：去除 .html 后缀
    private final String removeHtmlSuffix(String url) {
        if (url.endsWith(".html")) {
            return url.substring(0, url.length() - 5);
        }
        return url;
    }
}