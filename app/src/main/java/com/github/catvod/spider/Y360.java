package com.github.catvod.spider;

import android.text.TextUtils;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Filter;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.net.OkHttp;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * 360影视源爬虫实现
 * 使用 360kan.com API 提供搜索、分类、详情和播放服务
 */
public class Y360 extends Spider {

    // API URLs
    private static final String API_FILTER_LIST = "https://api.web.360kan.com/v1/filter/list?catid=";
    private static final String API_DETAIL = "https://api.web.360kan.com/v1/detail?cat=";
    private static final String API_RANK = "https://api.web.360kan.com/v1/rank?cat=2&callback=";
    private static final String API_SEARCH = "https://api.so.360kan.com/index?force_v=1&kw=";
    private static final String SEARCH_SUFFIX = "&from=&pageno=1&v_ap=1&tab=all";

    // Headers
    private static final String PC_UA = "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/39.0.2171.71 Safari/537.36";
    private static final String REFERER_WEB = "https://api.web.360kan.com";
    private static final String REFERER_RANK = "https://www.360kan.com/rank/dianying";
    private static final String REFERER_SEARCH_PREFIX = "https://so.360kan.com/?kw=";

    // Success markers
    private static final String SUCCESS_MARKER_DETAIL = "\"msg\":\"Success\"";
    private static final String SUCCESS_MARKER_LIST = "\"msg\":\"ok\"";

    // Constants
    private static final int MAX_RETRY = 8;
    private static final int EPISODE_BATCH_SIZE = 200;

    @Override
    public String homeContent(boolean filter) throws Exception {
        List<Class> classes = new ArrayList<>();
        classes.add(new Class("电影", "1"));
        classes.add(new Class("电视剧", "2"));
        classes.add(new Class("综艺", "3"));
        classes.add(new Class("动漫", "4"));

        // 获取推荐列表
        ArrayList<Vod> list = fetchRankList();

        if (filter) {
            HashMap<String, String> filters = new HashMap<>();
            filters.put("1", getMovieFilters());
            filters.put("2", getTvFilters());
            filters.put("3", getVarietyFilters());
            filters.put("4", getAnimeFilters());
            return Result.string(classes, list, new JSONObject(filters));
        }

        return Result.string(classes, list);
    }

    @Override
    public String homeVideoContent() throws Exception {
        ArrayList<Vod> list = fetchRankList();
        return Result.string(list);
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        int page = Integer.parseInt(pg);

        // 构建过滤参数
        StringBuilder urlBuilder = new StringBuilder(API_FILTER_LIST)
                .append(tid)
                .append("&pageno=")
                .append(page);

        if (extend != null) {
            if (extend.containsKey("year")) {
                urlBuilder.append("&year=").append(URLEncoder.encode(extend.get("year"), "UTF-8"));
            }
            if (extend.containsKey("area")) {
                urlBuilder.append("&area=").append(URLEncoder.encode(extend.get("area"), "UTF-8"));
            }
            if (extend.containsKey("class")) {
                urlBuilder.append("&tag=").append(URLEncoder.encode(extend.get("class"), "UTF-8"));
            }
            if (extend.containsKey("sort")) {
                urlBuilder.append("&sort=").append(extend.get("sort"));
            }
        }

        HashMap<String, String> headers = new HashMap<>();
        headers.put("User-Agent", PC_UA);
        headers.put("Referer", REFERER_WEB);

        String response = fetchWithRetry(urlBuilder.toString(), headers, SUCCESS_MARKER_LIST);
        JSONObject json = new JSONObject(response);

        if (!json.optString("msg").equals("ok")) {
            return Result.string(new ArrayList<>());
        }

        JSONArray data = json.optJSONArray("data");
        ArrayList<Vod> list = new ArrayList<>();

        if (data != null) {
            for (int i = 0; i < data.length(); i++) {
                JSONObject item = data.optJSONObject(i);
                if (item == null) continue;

                Vod vod = new Vod();
                vod.setVodId(item.optString("cat") + "@" + item.optString("id"));
                vod.setVodName(item.optString("title"));
                vod.setVodPic(item.optString("cover"));
                vod.setVodRemarks(item.optString("upinfo"));

                list.add(vod);
            }
        }

        int total = json.optInt("total", 35);
        return Result.get().vod(list).page(page, 999, 35, total).string();
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        String id = ids.get(0);
        if (TextUtils.isEmpty(id)) {
            return Result.error("数据获取失败");
        }

        // 解析ID
        String[] parts = id.split("@");
        if (parts.length < 2) {
            return Result.error("ID格式错误");
        }

        String cat = parts[0];
        String videoId = parts[1];

        // 构建详情URL
        String apiUrl = API_DETAIL + cat + "&id=" + videoId;

        HashMap<String, String> headers = new HashMap<>();
        headers.put("User-Agent", PC_UA);
        headers.put("Referer", REFERER_WEB);

        String response = fetchWithRetry(apiUrl, headers, SUCCESS_MARKER_DETAIL);
        JSONObject json = new JSONObject(response);

        if (!json.optString("msg").equals("Success")) {
            return Result.error("详情获取失败");
        }

        JSONObject data = json.optJSONObject("data");
        if (data == null) {
            return Result.error("数据解析失败");
        }

        // 构建Vod对象
        Vod vod = new Vod();
        vod.setVodId(id);
        vod.setVodName(data.optString("title"));
        vod.setVodPic(data.optString("cover"));
        vod.setTypeName(data.optString("catname"));
        vod.setVodYear(data.optString("year"));
        vod.setVodArea(data.optString("area"));
        vod.setVodActor(data.optString("actor"));
        vod.setVodDirector(data.optString("director"));
        vod.setVodContent(data.optString("description"));

        // 解析播放列表
        LinkedHashMap<String, String> playMap = new LinkedHashMap<>();
        JSONArray playList = data.optJSONArray("playlist");

        if (playList != null) {
            for (int i = 0; i < playList.length(); i++) {
                JSONObject playItem = playList.optJSONObject(i);
                if (playItem == null) continue;

                String playFrom = playItem.optString("playfrom");
                JSONArray episodes = playItem.optJSONArray("episodes");

                if (TextUtils.isEmpty(playFrom) || episodes == null) continue;

                ArrayList<String> episodeList = new ArrayList<>();
                for (int j = 0; j < episodes.length(); j++) {
                    JSONObject ep = episodes.optJSONObject(j);
                    if (ep == null) continue;

                    String epName = ep.optString("name");
                    String epUrl = ep.optString("url");
                    if (!TextUtils.isEmpty(epName) && !TextUtils.isEmpty(epUrl)) {
                        episodeList.add(epName + "$" + epUrl);
                    }
                }

                if (!episodeList.isEmpty()) {
                    playMap.put(playFrom, TextUtils.join("#", episodeList));
                }
            }
        }

        if (!playMap.isEmpty()) {
            vod.setVodPlayFrom(TextUtils.join("$$$", playMap.keySet()));
            vod.setVodPlayUrl(TextUtils.join("$$$", playMap.values()));
        }

        return Result.string(vod);
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        if (TextUtils.isEmpty(id)) {
            return Result.error("播放链接获取失败");
        }

        JSONObject result = new JSONObject();
        result.put("url", id);
        result.put("parse", 1);
        result.put("jx", 1);

        return result.toString();
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        String encodedKey = URLEncoder.encode(key, "UTF-8");

        String apiUrl = API_SEARCH + encodedKey + SEARCH_SUFFIX;

        HashMap<String, String> headers = new HashMap<>();
        headers.put("User-Agent", PC_UA);
        headers.put("Referer", REFERER_SEARCH_PREFIX + encodedKey);

        String response = fetchWithRetry(apiUrl, headers, "");
        JSONObject json = new JSONObject(response);

        JSONObject data = json.optJSONObject("data");
        if (data == null) {
            return Result.string(new ArrayList<>());
        }

        JSONArray movieList = data.optJSONArray("movie");
        ArrayList<Vod> list = new ArrayList<>();

        if (movieList != null) {
            for (int i = 0; i < movieList.length(); i++) {
                JSONObject item = movieList.optJSONObject(i);
                if (item == null) continue;

                Vod vod = new Vod();
                vod.setVodId(item.optString("cat") + "@" + item.optString("id"));
                vod.setVodName(item.optString("title"));
                vod.setVodPic(item.optString("cover"));
                vod.setVodRemarks(item.optString("upinfo"));

                list.add(vod);
            }
        }

        return Result.string(list);
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 带重试的HTTP请求
     */
    private String fetchWithRetry(String url, HashMap<String, String> headers, String successMarker) {
        for (int i = 0; i < MAX_RETRY; i++) {
            try {
                String response = OkHttp.string(url, headers);
                if (!TextUtils.isEmpty(successMarker) && !response.contains(successMarker)) {
                    Thread.sleep(500);
                    continue;
                }
                return response;
            } catch (Exception e) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ignored) {}
            }
        }
        return "";
    }

    /**
     * 获取推荐列表（排行榜）
     */
    private ArrayList<Vod> fetchRankList() throws Exception {
        String apiUrl = API_RANK + "callback";

        HashMap<String, String> headers = new HashMap<>();
        headers.put("User-Agent", PC_UA);
        headers.put("Referer", REFERER_RANK);

        String response = fetchWithRetry(apiUrl, headers, "");

        // 移除 JSONP 回调包装
        if (response.startsWith("callback(") && response.endsWith(")")) {
            response = response.substring(9, response.length() - 1);
        }

        JSONObject json = new JSONObject(response);
        JSONArray data = json.optJSONArray("data");

        ArrayList<Vod> list = new ArrayList<>();
        if (data == null) return list;

        for (int i = 0; i < data.length(); i++) {
            JSONObject item = data.optJSONObject(i);
            if (item == null) continue;

            Vod vod = new Vod();
            vod.setVodId(item.optString("cat") + "@" + item.optString("id"));
            vod.setVodName(item.optString("title"));
            vod.setVodPic(item.optString("cover"));
            vod.setVodRemarks(item.optString("upinfo"));

            list.add(vod);
        }

        return list;
    }

    /**
     * 电影过滤器
     */
    private String getMovieFilters() {
        return "{\"year\":{\"name\":\"年份\",\"value\":[" +
                "{\"n\":\"全部\",\"v\":\"\"},{\"n\":\"2024\",\"v\":\"2024\"},{\"n\":\"2023\",\"v\":\"2023\"}," +
                "{\"n\":\"2022\",\"v\":\"2022\"},{\"n\":\"2021\",\"v\":\"2021\"},{\"n\":\"2020\",\"v\":\"2020\"}," +
                "{\"n\":\"2019\",\"v\":\"2019\"},{\"n\":\"2018\",\"v\":\"2018\"},{\"n\":\"2017\",\"v\":\"2017\"}]}," +
                "\"area\":{\"name\":\"地区\",\"value\":[" +
                "{\"n\":\"全部\",\"v\":\"\"},{\"n\":\"中国\",\"v\":\"中国\"},{\"n\":\"美国\",\"v\":\"美国\"}," +
                "{\"n\":\"韩国\",\"v\":\"韩国\"},{\"n\":\"日本\",\"v\":\"日本\"},{\"n\":\"英国\",\"v\":\"英国\"}]}," +
                "\"class\":{\"name\":\"类型\",\"value\":[" +
                "{\"n\":\"全部\",\"v\":\"\"},{\"n\":\"喜剧\",\"v\":\"喜剧\"},{\"n\":\"爱情\",\"v\":\"爱情\"}," +
                "{\"n\":\"动作\",\"v\":\"动作\"},{\"n\":\"科幻\",\"v\":\"科幻\"},{\"n\":\"恐怖\",\"v\":\"恐怖\"}]}," +
                "\"sort\":{\"name\":\"排序\",\"value\":[" +
                "{\"n\":\"最新\",\"v\":\"time\"},{\"n\":\"最热\",\"v\":\"hot\"},{\"n\":\"评分\",\"v\":\"rating\"}]}}";
    }

    /**
     * 电视剧过滤器
     */
    private String getTvFilters() {
        return "{\"year\":{\"name\":\"年份\",\"value\":[" +
                "{\"n\":\"全部\",\"v\":\"\"},{\"n\":\"2024\",\"v\":\"2024\"},{\"n\":\"2023\",\"v\":\"2023\"}," +
                "{\"n\":\"2022\",\"v\":\"2022\"},{\"n\":\"2021\",\"v\":\"2021\"},{\"n\":\"2020\",\"v\":\"2020\"}]}," +
                "\"area\":{\"name\":\"地区\",\"value\":[" +
                "{\"n\":\"全部\",\"v\":\"\"},{\"n\":\"中国\",\"v\":\"中国\"},{\"n\":\"韩国\",\"v\":\"韩国\"}," +
                "{\"n\":\"美国\",\"v\":\"美国\"},{\"n\":\"日本\",\"v\":\"日本\"}]}," +
                "\"class\":{\"name\":\"类型\",\"value\":[" +
                "{\"n\":\"全部\",\"v\":\"\"},{\"n\":\"古装\",\"v\":\"古装\"},{\"n\":\"言情\",\"v\":\"言情\"}," +
                "{\"n\":\"悬疑\",\"v\":\"悬疑\"},{\"n\":\"都市\",\"v\":\"都市\"}]}," +
                "\"sort\":{\"name\":\"排序\",\"value\":[" +
                "{\"n\":\"最新\",\"v\":\"time\"},{\"n\":\"最热\",\"v\":\"hot\"},{\"n\":\"评分\",\"v\":\"rating\"}]}}";
    }

    /**
     * 综艺过滤器
     */
    private String getVarietyFilters() {
        return "{\"class\":{\"name\":\"类型\",\"value\":[" +
                "{\"n\":\"全部\",\"v\":\"\"},{\"n\":\"搞笑\",\"v\":\"搞笑\"},{\"n\":\"情感\",\"v\":\"情感\"}," +
                "{\"n\":\"访谈\",\"v\":\"访谈\"},{\"n\":\"音乐\",\"v\":\"音乐\"}]}," +
                "\"sort\":{\"name\":\"排序\",\"value\":[" +
                "{\"n\":\"最新\",\"v\":\"time\"},{\"n\":\"最热\",\"v\":\"hot\"}]}}";
    }

    /**
     * 动漫过滤器
     */
    private String getAnimeFilters() {
        return "{\"year\":{\"name\":\"年份\",\"value\":[" +
                "{\"n\":\"全部\",\"v\":\"\"},{\"n\":\"2024\",\"v\":\"2024\"},{\"n\":\"2023\",\"v\":\"2023\"}," +
                "{\"n\":\"2022\",\"v\":\"2022\"},{\"n\":\"2021\",\"v\":\"2021\"}]}," +
                "\"class\":{\"name\":\"类型\",\"value\":[" +
                "{\"n\":\"全部\",\"v\":\"\"},{\"n\":\"热血\",\"v\":\"热血\"},{\"n\":\"格斗\",\"v\":\"格斗\"}," +
                "{\"n\":\"恋爱\",\"v\":\"恋爱\"},{\"n\":\"校园\",\"v\":\"校园\"},{\"n\":\"搞笑\",\"v\":\"搞笑\"}]}," +
                "\"sort\":{\"name\":\"排序\",\"value\":[" +
                "{\"n\":\"最新\",\"v\":\"time\"},{\"n\":\"最热\",\"v\":\"hot\"},{\"n\":\"评分\",\"v\":\"rating\"}]}}";
    }
}