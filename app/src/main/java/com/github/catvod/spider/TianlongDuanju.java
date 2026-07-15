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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 天龙短剧源爬虫
 * 支持搜索、分类浏览、详情解析、播放地址获取
 */
public class TianlongDuanju extends Spider {

    private static final String[] API_HOSTS = {
        "https://www.tianlongduanju.com",
        "https://m.tianlongduanju.net"
    };

    private static final Pattern VIDEO_URL_PATTERN = Pattern.compile("<a[^>]+href=\"(/[\\w-]+\\.html)\"[^>]*title=\"([^\"]+)\"");
    private static final Pattern VIDEO_TITLE_PATTERN = Pattern.compile("<title>([^<]+)</title>");
    private static final Pattern M3U8_PATTERN = Pattern.compile("\"url\":\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);

    public static List<Vod> parseSearchResults(String html) {
        List<Vod> list = new ArrayList<>();
        if (TextUtils.isEmpty(html)) return list;

        Matcher matcher = VIDEO_URL_PATTERN.matcher(html);
        while (matcher.find() && list.size() < 48) {
            String url = matcher.group(1);
            String name = matcher.group(2).trim();

            Vod vod = new Vod();
            vod.setVodId(API_HOSTS[0] + "/vod" + url);
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

    public HashMap<String, String> buildHeadersWithToken(String referer) {
        HashMap<String, String> headers = buildHeaders(referer + "/");
        headers.put("X-Token", "tianlong_user_token");
        headers.put("Cookie", "tianlong_session_id=tl_2025_session_v1");
        return headers;
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        List<Class> classes = new ArrayList<>();
        classes.add(new Class("热门推荐", "热门推荐"));
        classes.add(new Class("最新上架", "最新上架"));
        classes.add(new Class("电影短片", "电影短片"));
        classes.add(new Class("都市言情", "都市言情"));
        classes.add(new Class("古装穿越", "古装穿越"));

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

        JSONObject response = fetchCategoryData(pg, keyword, keyword);
        List<Vod> list = parseVideoList(response);

        if (list.isEmpty()) {
            list = fetchDefaultVideos();
        }

        return Result.string(list);
    }

    private JSONObject fetchCategoryData(String pg, String keyword, String categoryId) {
        for (String host : API_HOSTS) {
            try {
                StringBuilder urlBuilder = new StringBuilder(host);
                urlBuilder.append("/api/video/list?page=").append(pg);

                if (!TextUtils.isEmpty(keyword)) {
                    urlBuilder.append("&keyword=").append(URLEncoder.encode(keyword, "UTF-8"));
                }

                if (!TextUtils.isEmpty(categoryId)) {
                    urlBuilder.append("&categoryId=").append(URLEncoder.encode(categoryId, "UTF-8"));
                }

                String response = OkHttp.string(urlBuilder.toString(), buildHeadersWithToken(host));
                if (!TextUtils.isEmpty(response) && response.trim().startsWith("{")) {
                    JSONObject json = new JSONObject(response);
                    if (json.has("list")) {
                        return json;
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return new JSONObject();
    }

    private List<Vod> fetchDefaultVideos() {
        for (String host : API_HOSTS) {
            try {
                String response = OkHttp.string(host + "/", buildHeaders(host + "/"));
                List<Vod> list = parseSearchResults(response);
                if (!list.isEmpty()) return list;

                response = OkHttp.string(host + "/latest.html", buildHeaders(host + "/"));
                list = parseSearchResults(response);
                if (!list.isEmpty()) return list;
            } catch (Exception ignored) {
            }
        }
        return new ArrayList<>();
    }

    private List<Vod> parseVideoList(JSONObject json) {
        List<Vod> list = new ArrayList<>();
        JSONArray array = json.optJSONArray("list");
        if (array == null) return list;

        for (int i = 0; i < array.length() && list.size() < 48; i++) {
            JSONObject item = array.optJSONObject(i);
            if (item == null) continue;

            String videoId = item.optString("id", "");
            String fullId = item.optString("vod_id", videoId);
            if (TextUtils.isEmpty(fullId)) continue;

            Vod vod = new Vod();
            vod.setVodId(API_HOSTS[0] + "/vod/" + fullId + ".html");
            vod.setVodName(item.optString("vod_name", "未命名" + fullId));
            vod.setVodPic(item.optString("vod_pic", "").replace("\\", "/"));
            vod.setVodRemarks(item.optString("vod_remarks", ""));
            list.add(vod);
        }
        return list;
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        String videoId = ids.get(0);
        // 提取视频ID
        Matcher matcher = Pattern.compile("/vod/([\\w-]+)\\.html").matcher(videoId);
        if (matcher.find()) {
            videoId = API_HOSTS[0] + "/vod/" + matcher.group(1) + ".html";
        }

        String html = OkHttp.string(videoId, buildHeaders(API_HOSTS[0] + "/"));
        if (TextUtils.isEmpty(html)) {
            try {
                html = OkHttp.string(videoId.replace(API_HOSTS[0], API_HOSTS[1]), buildHeaders(API_HOSTS[1] + "/"));
            } catch (Exception ignored) {
            }
        }

        Vod vod = new Vod();
        vod.setVodId(videoId);

        Matcher titleMatcher = VIDEO_TITLE_PATTERN.matcher(html == null ? "" : html);
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
                        episodeUrl = API_HOSTS[0] + episodeUrl;
                    }
                    String episodeName = nameMatcher.find() ? nameMatcher.group(1).trim() : ("第" + i + "集");
                    episodes.add(episodeName + "$" + episodeUrl);
                }
            }
        }

        vod.setVodPlayFrom("天龙短剧");

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
        String baseUrl = API_HOSTS[0];

        if (!id.startsWith("http")) {
            id = baseUrl + id;
        }

        String playUrl = id.replace(API_HOSTS[1], baseUrl);
        String html = OkHttp.string(playUrl, buildHeaders(playUrl));

        String result = "";
        if (!TextUtils.isEmpty(html)) {
            Matcher m3u8Matcher = M3U8_PATTERN.matcher(html);
            if (m3u8Matcher.find()) {
                Matcher urlMatcher = Pattern.compile("\"key\":\"([^\"]+)\"").matcher(m3u8Matcher.group(1));
                if (urlMatcher.find()) {
                    String videoKey = urlMatcher.group(1);
                    String apiUrl = baseUrl + "/api/play/url?key=" + URLEncoder.encode(videoKey, "UTF-8");
                    String apiResponse = OkHttp.string(apiUrl, buildHeaders(playUrl));

                    Matcher keyMatcher = Pattern.compile("\"aes_key\":\"([^\"]+)\"").matcher(apiResponse);
                    Matcher ivMatcher = Pattern.compile("\"aes_iv\":\"([^\"]+)\"").matcher(apiResponse);
                    Matcher dataMatcher = Pattern.compile("\"data\":\"([^\"]+)\"").matcher(apiResponse);

                    if (keyMatcher.find() && ivMatcher.find() && dataMatcher.find()) {
                        try {
                            JSONObject decryptResponse = new JSONObject(OkHttp.post(baseUrl + "/api/play/decrypt",
                                new HashMap<String, String>() {{
                                    put("key", videoKey);
                                    put("aes_key", keyMatcher.group(1));
                                    put("aes_iv", ivMatcher.group(1));
                                }},
                                buildHeaders(apiUrl)));

                            if ("success".equals(decryptResponse.optString("status"))) {
                                result = decryptResponse.optString("url", result);
                            }
                        } catch (Exception ignored) {
                        }
                    }
                }
            }
        }

        HashMap<String, String> headers = new HashMap<>();
        headers.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
        headers.put("Referer", playUrl);

        return Result.get().url(result).header(headers).string();
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        return searchContent(key, quick, "1");
    }

    @Override
    public String searchContent(String key, boolean quick, String pg) throws Exception {
        JSONObject response = fetchCategoryData(pg, key, "");
        return Result.string(parseVideoList(response));
    }

    @Override
    public void init(Context context, String extend) throws Exception {
        super.init(context, extend);
    }
}