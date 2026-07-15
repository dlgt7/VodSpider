package com.github.catvod.spider;

import android.content.Context;
import android.text.TextUtils;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.net.OkHttp;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Andy 短剧源爬虫
 * 支持搜索、分类浏览、详情解析、播放地址获取
 */
public class AndyDuanju extends Spider {

    private static final String SITE_URL = "https://www.andy666.com";

    private static final Pattern VIDEO_URL_PATTERN = Pattern.compile("<a[^>]+href=\"(/[\\w-]+\\.html)\"[^>]*title=\"([^\"]+)\"");
    private static final Pattern M3U8_PATTERN = Pattern.compile("(https?://[^\"'\\s]+\\.m3u8[^\"'\\s]*)", Pattern.CASE_INSENSITIVE);

    public static String fixUrl(String url) {
        if (TextUtils.isEmpty(url)) return "";
        if (url.startsWith("http")) return url;
        return (url.startsWith("/") ? SITE_URL : SITE_URL + "/") + url;
    }

    public static List<Vod> parseSearchResults(String html) {
        List<Vod> list = new ArrayList<>();
        if (TextUtils.isEmpty(html)) return list;

        Matcher matcher = VIDEO_URL_PATTERN.matcher(html);
        while (matcher.find() && list.size() < 48) {
            String url = matcher.group(1);
            String name = matcher.group(2).trim();

            Vod vod = new Vod();
            vod.setVodId(SITE_URL + "/vod" + url);
            vod.setVodName(name);
            vod.setVodPic("");
            vod.setVodRemarks("");
            list.add(vod);
        }
        return list;
    }

    public HashMap<String, String> buildHeaders(String referer) {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
        headers.put("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
        headers.put("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");
        headers.put("Accept-Encoding", "gzip, deflate");
        headers.put("Connection", "keep-alive");
        if (!TextUtils.isEmpty(referer)) {
            headers.put("Referer", referer);
        }
        return headers;
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        List<Class> classes = new ArrayList<>();
        classes.add(new Class("热门推荐", "热门推荐"));
        classes.add(new Class("最新上架", "最新上架"));
        classes.add(new Class("电影短片", "电影短片"));
        classes.add(new Class("都市言情", "都市言情"));

        return Result.string(classes, new ArrayList<>());
    }

    @Override
    public String homeVideoContent() throws Exception {
        return categoryContent("热门推荐", "1", false, new HashMap<>());
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        String keyword = "";
        if (tid != null) {
            tid = tid.trim();
            if (!TextUtils.isEmpty(tid) && !"全部".equalsIgnoreCase(tid) && !"热门推荐".equals(tid)) {
                keyword = tid;
            }
        }

        String url = SITE_URL + "/api/video/list?keyword=" + URLEncoder.encode(keyword, "UTF-8");
        String response = OkHttp.string(url, buildHeaders(SITE_URL + "/"));

        List<Vod> list = new ArrayList<>();
        if (!TextUtils.isEmpty(response)) {
            JSONObject json = new JSONObject(response);
            JSONArray array = json.optJSONArray("list");
            if (array != null) {
                for (int i = 0; i < array.length() && list.size() < 48; i++) {
                    JSONObject item = array.optJSONObject(i);
                    if (item == null) continue;

                    String videoId = item.optString("id", "");
                    if (TextUtils.isEmpty(videoId)) continue;

                    Vod vod = new Vod();
                    vod.setVodId(SITE_URL + "/vod/" + videoId + ".html");
                    vod.setVodName(item.optString("name", "未命名" + videoId));
                    vod.setVodPic(fixUrl(item.optString("pic", "")));
                    vod.setVodRemarks(item.optString("remarks", ""));
                    list.add(vod);
                }
            }
        }

        if (list.isEmpty()) {
            String html = OkHttp.string(SITE_URL + "/latest.html", buildHeaders(SITE_URL + "/"));
            list = parseSearchResults(html);
        }

        return Result.string(list);
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        String videoId = ids.get(0);
        // 提取视频ID
        Matcher matcher = Pattern.compile("/vod/([\\w-]+)\\.html").matcher(videoId);
        if (matcher.find()) {
            videoId = SITE_URL + "/vod/" + matcher.group(1) + ".html";
        }

        String html = OkHttp.string(videoId, buildHeaders(SITE_URL + "/"));

        Vod vod = new Vod();
        vod.setVodId(videoId);

        Matcher titleMatcher = Pattern.compile("<title>([^<]+)</title>").matcher(html == null ? "" : html);
        vod.setVodName(titleMatcher.find() ? titleMatcher.group(1).trim() : "未命名视频");
        vod.setVodDirector("");

        List<String> episodes = new ArrayList<>();
        if (!TextUtils.isEmpty(html)) {
            String[] parts = html.split("<div class=\"episode-list\">");
            for (int i = 1; i < parts.length; i++) {
                String part = parts[i].split("</div>", 2)[0];
                Matcher urlMatcher = Pattern.compile("href=\"([^\"]+)\"").matcher(part);
                Matcher nameMatcher = Pattern.compile(">([^<]+)</a>").matcher(part);

                if (urlMatcher.find()) {
                    String episodeUrl = urlMatcher.group(1);
                    if (!episodeUrl.startsWith("http")) {
                        episodeUrl = SITE_URL + episodeUrl;
                    }
                    String episodeName = nameMatcher.find() ? nameMatcher.group(1).trim() : ("第" + i + "集");
                    episodes.add(episodeName + "$" + episodeUrl);
                }
            }
        }

        vod.setVodPlayFrom("Andy短剧");

        StringBuilder episodeBuilder = new StringBuilder();
        for (int i = 0; i < episodes.size(); i++) {
            if (i > 0) episodeBuilder.append('#');
            episodeBuilder.append(episodes.get(i));
        }
        vod.setVodPlayUrl(episodeBuilder.toString());

        return Result.string(vod);
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        if (!id.startsWith("http")) {
            id = SITE_URL + id;
        }

        String html = OkHttp.string(id, buildHeaders(SITE_URL + "/"));
        String playUrl = "";

        if (!TextUtils.isEmpty(html)) {
            Matcher m3u8Matcher = M3U8_PATTERN.matcher(html);
            if (m3u8Matcher.find()) {
                playUrl = m3u8Matcher.group(1);
            } else {
                Matcher directMatcher = Pattern.compile("\"url\":\"([^\"]+)\"").matcher(html);
                if (directMatcher.find()) {
                    playUrl = directMatcher.group(1);
                }
            }
        }

        HashMap<String, String> headers = new HashMap<>();
        headers.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
        headers.put("Referer", id);

        return Result.get().url(playUrl).header(headers).string();
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        return searchContent(key, quick, "1");
    }

    @Override
    public String searchContent(String key, boolean quick, String pg) throws Exception {
        String url = SITE_URL + "/search.php?keyword=" + URLEncoder.encode(key, "UTF-8");
        String html = OkHttp.string(url, buildHeaders(SITE_URL + "/"));
        return Result.string(parseSearchResults(html));
    }

    @Override
    public void init(Context context, String extend) throws Exception {
        super.init(context, extend);
    }
}