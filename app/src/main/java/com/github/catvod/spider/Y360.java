package com.github.catvod.spider;

import android.content.Context;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Util;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * 360kan 源爬虫实现。
 * 提供视频搜索、分类列表、详情页解析及播放地址获取。
 */
public class Y360 extends Spider {

    private static final String PC_UA = "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/39.0.2171.71 Safari/537.36";
    private static final String API_FILTER_LIST = "https://api.web.360kan.com/v1/filter/list?catid=";
    private static final String API_DETAIL = "https://api.web.360kan.com/v1/detail?cat=";
    private static final String API_RANK = "https://api.web.360kan.com/v1/rank?cat=2&callback=";
    private static final String API_SEARCH = "https://api.so.360kan.com/index?force_v=1&kw=";
    private static final String SEARCH_SUFFIX = "&from=&pageno=1&v_ap=1&tab=all";
    private static final String REFERER_WEB = "https://api.web.360kan.com";
    private static final String REFERER_RANK = "https://www.360kan.com/rank/dianying";
    private static final String REFERER_SEARCH_PREFIX = "https://so.360kan.com/?kw=";
    private static final String SUCCESS_MARKER_DETAIL = "\"msg\":\"Success\"";
    private static final String SUCCESS_MARKER_LIST = "\"msg\":\"ok\"";
    private static final int MAX_RETRY = 8;
    private static final int EPISODE_BATCH_SIZE = 200;

    /**
     * 构建视频ID，格式为 tid@@id
     */
    private static String buildVodId(String tid, String id) {
        return tid + "@@" + id;
    }

    /**
     * 清理URL，移除 &refer 和 ? 后缀
     */
    private static String cleanUrl(String url) {
        if (url == null || url.isEmpty()) return "";
        int referIdx = url.indexOf("&refer");
        if (referIdx != -1) url = url.substring(0, referIdx);
        int qIdx = url.indexOf("?");
        if (qIdx != -1) url = url.substring(0, qIdx);
        return url;
    }

    /**
     * 修正URL，确保以 http 开头
     */
    private static String fixUrl(String url) {
        if (url == null || url.isEmpty()) return "";
        if (url.startsWith("//")) return "https:" + url;
        if (url.startsWith("/")) return "https:" + url;
        if (url.startsWith("http")) return url;
        return "https:" + url;
    }

    /**
     * 将JSONArray转为逗号分隔的字符串
     */
    private static String jsonArrayToString(JSONArray arr) {
        if (arr == null) return "";
        String s = arr.toString();
        s = s.replace("\"", "").replace("[", "").replace("]", "");
        return s;
    }

    /**
     * 带重试的HTTP GET请求
     * @param url 请求URL
     * @param referer Referer头
     * @param successMarker 成功标识，响应中包含此字符串则立即返回；为空则首次响应即返回
     */
    private static String fetchWithRetry(String url, String referer, String successMarker) {
        try {
            HashMap<String, String> headers = new HashMap<>();
            headers.put("User-Agent", PC_UA);
            if (referer != null && !referer.isEmpty()) {
                headers.put("Referer", referer);
            }
            for (int i = 0; i < MAX_RETRY; i++) {
                String response = OkHttp.string(url, headers);
                if (successMarker == null || successMarker.isEmpty() || response.contains(successMarker)) {
                    return response;
                }
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            return "";
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 从剧集JSONArray构建剧集列表
     * 每集格式：集标题$url
     */
    private static void buildEpisodes(ArrayList<String> list, JSONArray arr) {
        if (arr == null) return;
        for (int i = 0; i < arr.length(); i++) {
            try {
                JSONObject item = arr.getJSONObject(i);
                String num = item.optString("playlink_num", "");
                String name = item.optString("name", "");
                String period = item.optString("period", "");
                String url = cleanUrl(item.optString("url", ""));

                if (Util.isNotEmpty(period)) {
                    name = period + " " + name;
                }
                if (Util.isNotEmpty(num)) {
                    name = "第" + num + "集" + name;
                }
                if (name == null || name.isEmpty()) {
                    name = "第" + (i + 1) + "集";
                }
                list.add(name + "$" + url);
            } catch (Exception e) {
                // skip invalid item
            }
        }
    }

    /**
     * 获取首页推荐视频列表（电影排行）
     */
    private ArrayList<Vod> getHomeVideoList() {
        ArrayList<Vod> list = new ArrayList<>();
        try {
            String response = fetchWithRetry(API_RANK, REFERER_RANK, "");
            JSONObject json = new JSONObject(response);
            JSONArray data = json.optJSONArray("data");
            if (data == null) return list;
            for (int i = 0; i < data.length(); i++) {
                try {
                    JSONObject item = data.getJSONObject(i);
                    String id = buildVodId("1", item.optString("ent_id", ""));
                    String title = item.optString("title", "");
                    String pic = fixUrl(item.optString("cover", ""));
                    String remark = jsonArrayToString(item.optJSONArray("moviecat"));
                    list.add(new Vod(id, title, pic, remark));
                } catch (Exception e) {
                    // skip invalid item
                }
            }
        } catch (Exception e) {
            // skip on error
        }
        return list;
    }

    @Override
    public void init(Context context, String extend) throws Exception {
        super.init(context, extend);
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        List<Class> classes = Arrays.asList(
                new Class("1", "电影"),
                new Class("2", "电视剧"),
                new Class("3", "综艺"),
                new Class("4", "动漫")
        );
        ArrayList<Vod> list = getHomeVideoList();
        return Result.string(classes, list);
    }

    @Override
    public String homeVideoContent() throws Exception {
        return Result.string(getHomeVideoList());
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        ArrayList<Vod> list = new ArrayList<>();
        try {
            String by = "ranklatest";
            String cat = "";
            String year = "";
            String area = "";
            String actor = "";
            if (extend != null) {
                if (extend.containsKey("by")) by = extend.get("by");
                if (extend.containsKey("class")) cat = extend.get("class");
                if (extend.containsKey("year")) year = extend.get("year");
                if (extend.containsKey("area")) area = extend.get("area");
                if (extend.containsKey("actor")) actor = extend.get("actor");
            }

            StringBuilder urlBuilder = new StringBuilder(API_FILTER_LIST);
            urlBuilder.append(tid);
            urlBuilder.append("&rank=").append(by);
            urlBuilder.append("&cat=").append(cat);
            urlBuilder.append("&year=").append(year);
            urlBuilder.append("&area=").append(area);
            urlBuilder.append("&act=").append(actor);
            urlBuilder.append("&size=35");
            int page = Integer.parseInt(pg);
            if (page > 1) {
                urlBuilder.append("&pageno=").append(pg);
            }
            urlBuilder.append("&callback=");

            String response = fetchWithRetry(urlBuilder.toString(), REFERER_WEB, SUCCESS_MARKER_LIST);
            JSONObject json = new JSONObject(response);
            JSONObject data = json.optJSONObject("data");
            if (data == null) return Result.get().vod(list).page(page, 9999, 35, list.size()).string();
            JSONArray movies = data.optJSONArray("movies");
            if (movies == null) return Result.get().vod(list).page(page, 9999, 35, list.size()).string();

            for (int i = 0; i < movies.length(); i++) {
                try {
                    JSONObject item = movies.getJSONObject(i);
                    String title = item.optString("title", "");
                    if (title.isEmpty() || title.equals("儿大女成人")
                            || title.contains("detailReferer")
                            || title.contains("闭幕式")
                            || title.contains("赏析")) {
                        continue;
                    }
                    String id = buildVodId(tid, item.optString("ent_id", ""));
                    String pic = fixUrl(item.optString("cdncover", ""));

                    String category = jsonArrayToString(item.optJSONArray("moviecat"));
                    String upinfo = item.optString("upinfo", "");
                    String total = item.optString("total", "");
                    String tag = item.optString("tag", "");

                    String remark;
                    if (Util.isNotEmpty(upinfo) && !"-1".equals(upinfo)) {
                        remark = "更新至" + upinfo + "集";
                    } else if (Util.isNotEmpty(total) && !"-1".equals(total)) {
                        remark = "已完结";
                    } else if (Util.isNotEmpty(tag)) {
                        remark = tag;
                    } else {
                        remark = category;
                    }

                    list.add(new Vod(id, title, pic, remark));
                } catch (Exception e) {
                    // skip invalid item
                }
            }
            return Result.get().vod(list).page(page, 9999, 35, list.size()).string();
        } catch (Exception e) {
            return Result.get().vod(list).page(pg, 9999, 35, list.size()).string();
        }
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        try {
            String id = ids.get(0);
            String cat;
            String entId;

            // 解析ID，格式为 tid@@ent_id
            if (id.contains("@@")) {
                String[] parts = id.split("@@", 2);
                cat = parts[0];
                entId = parts[1];
            } else {
                return Result.error("详情为空");
            }

            // 构建详情URL并请求
            String url = API_DETAIL + cat + "&id=" + entId + "&callback=";
            String response = fetchWithRetry(url, REFERER_WEB, SUCCESS_MARKER_DETAIL);
            JSONObject json = new JSONObject(response);
            JSONObject data = json.optJSONObject("data");
            if (data == null) return Result.error("详情为空");

            // 构建Vod信息
            Vod vod = new Vod();
            vod.setVodId(id);
            vod.setVodName(data.optString("title", ""));
            vod.setVodPic(fixUrl(data.optString("cdncover", "")));
            vod.setVodYear(data.optString("pubdate", ""));
            vod.setVodDirector(data.optString("director", ""));
            vod.setVodContent(data.optString("description", ""));
            vod.setVodActor(data.optString("actor", ""));
            vod.setVodArea(data.optString("area", ""));
            vod.setVodTag(data.optString("tag", ""));
            vod.setTypeName(data.optString("moviecategory", ""));

            // 构建播放源映射
            LinkedHashMap<String, String> playMap = new LinkedHashMap<>();
            JSONArray playlinkSites = data.optJSONArray("playlink_sites");

            // 优先从 allepidetail 构建（支持200集分批）
            JSONObject allepidetail = data.optJSONObject("allepidetail");
            if (allepidetail != null && playlinkSites != null) {
                for (int i = 0; i < playlinkSites.length(); i++) {
                    try {
                        String site = playlinkSites.getString(i);
                        JSONArray eps = allepidetail.optJSONArray(site);
                        if (eps == null || eps.length() == 0) continue;

                        int total = eps.length();
                        int batchCount = (total + EPISODE_BATCH_SIZE - 1) / EPISODE_BATCH_SIZE;
                        for (int b = 0; b < batchCount; b++) {
                            int start = b * EPISODE_BATCH_SIZE;
                            int end = Math.min(start + EPISODE_BATCH_SIZE, total);
                            JSONArray batch = new JSONArray();
                            for (int j = start; j < end; j++) {
                                batch.put(eps.get(j));
                            }
                            ArrayList<String> epList = new ArrayList<>();
                            buildEpisodes(epList, batch);
                            String key = batchCount > 1 ? site + " " + (b + 1) : site;
                            playMap.put(key, Util.join("#", epList));
                        }
                    } catch (Exception e) {
                        // skip invalid site
                    }
                }
            }

            // 若无 allepidetail，从 defaultepisode 构建
            if (playMap.isEmpty()) {
                JSONObject defaultepisode = data.optJSONObject("defaultepisode");
                if (defaultepisode != null && playlinkSites != null) {
                    for (int i = 0; i < playlinkSites.length(); i++) {
                        try {
                            String site = playlinkSites.getString(i);
                            JSONArray eps = defaultepisode.optJSONArray(site);
                            if (eps == null || eps.length() == 0) continue;
                            ArrayList<String> epList = new ArrayList<>();
                            buildEpisodes(epList, eps);
                            playMap.put(site, Util.join("#", epList));
                        } catch (Exception e) {
                            // skip invalid site
                        }
                    }
                }
            }

            // 设置播放源
            ArrayList<String> playFromList = new ArrayList<>(playMap.keySet());
            ArrayList<String> playUrlList = new ArrayList<>(playMap.values());
            vod.setVodPlayFrom(Util.join("$$$", playFromList));
            vod.setVodPlayUrl(Util.join("$$$", playUrlList));

            return Result.string(vod);
        } catch (Exception e) {
            return Result.error("详情为空");
        }
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        if (id == null || id.isEmpty()) {
            return Result.error("播放地址为空");
        }
        JSONObject result = new JSONObject();
        result.put("url", id);
        result.put("parse", 1);
        result.put("jx", 1);
        return result.toString();
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        ArrayList<Vod> list = new ArrayList<>();
        try {
            String encodedKey = URLEncoder.encode(key, "UTF-8");
            String url = API_SEARCH + encodedKey + SEARCH_SUFFIX;
            String referer = REFERER_SEARCH_PREFIX + encodedKey;
            String response = fetchWithRetry(url, referer, "");
            JSONObject json = new JSONObject(response);
            JSONObject data = json.optJSONObject("data");
            if (data == null) return Result.string(list);
            JSONObject longData = data.optJSONObject("longData");
            if (longData == null) return Result.string(list);
            JSONArray rows = longData.optJSONArray("rows");
            if (rows == null) return Result.string(list);

            for (int i = 0; i < rows.length(); i++) {
                try {
                    JSONObject item = rows.getJSONObject(i);
                    String catId = item.optString("cat_id", "");
                    String enId = item.optString("en_id", "");
                    String title = item.optString("titleTxt", "");
                    String cover = fixUrl(item.optString("cover", ""));

                    String remark = "";
                    JSONObject coverInfo = item.optJSONObject("coverInfo");
                    if (coverInfo != null) {
                        remark = coverInfo.optString("txt", "");
                        if (remark.isEmpty()) {
                            remark = coverInfo.optString("quality", "");
                        }
                    }

                    list.add(new Vod(buildVodId(catId, enId), title, cover, remark));
                } catch (Exception e) {
                    // skip invalid item
                }
            }
        } catch (Exception e) {
            // skip on error
        }
        return Result.string(list);
    }
}
