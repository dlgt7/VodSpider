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
import java.util.Iterator;
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
     * 从URL查询字符串中提取指定key的值
     */
    private static String extractValue(String content, String key) {
        String prefix = key + "=";
        int idx = content.indexOf(prefix);
        if (idx == -1) return "";
        int start = idx + prefix.length();
        int end = content.indexOf("&", start);
        if (end == -1) end = content.length();
        return content.substring(start, end);
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
            int page = pg.matches("\\d+") ? Integer.parseInt(pg) : 1;
            return Result.get().vod(list).page(page, 9999, 35, list.size()).string();
        }
    }

    /**
     * 构建详情页分批请求URL
     * @param baseUrl 基础URL（API_DETAIL + cat + &id= + entId）
     * @param site 线路名称
     * @param start 起始集数（0表示不分批）
     * @param end 结束集数（0表示不分批）
     */
    private static String buildDetailUrl(String baseUrl, String site, int start, int end) {
        StringBuilder sb = new StringBuilder(baseUrl);
        sb.append("&site=").append(site);
        if (start > 0) {
            sb.append("&start=").append(start);
            sb.append("&end=").append(end);
        }
        sb.append("&callback=");
        return sb.toString();
    }

    /**
     * 从分批请求响应中获取指定site的剧集JSONArray
     * 响应格式：data.allepidetail.siteName 或 data.defaultepisode.siteName
     */
    private static JSONArray fetchSiteEpisodes(String url, String referer, String site, String dataKey) {
        try {
            String resp = fetchWithRetry(url, referer, SUCCESS_MARKER_DETAIL);
            JSONObject json = new JSONObject(resp);
            JSONObject data = json.optJSONObject("data");
            if (data == null) return null;
            JSONObject container = data.optJSONObject(dataKey);
            if (container == null) return null;
            return container.optJSONArray(site);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        try {
            String id = ids.get(0);
            String cat;
            String entId;

            // 解析ID，支持 tid@@ent_id 和 JSON detailUrl 两种格式
            if (id.isEmpty()) {
                cat = "1";
                entId = "";
            } else if (id.contains("@@")) {
                String[] parts = id.split("@@", 2);
                cat = parts[0];
                entId = parts[1];
            } else {
                try {
                    JSONObject idJson = new JSONObject(id);
                    String detailUrl = idJson.optString("detailUrl", "");
                    if (!detailUrl.isEmpty()) {
                        cat = extractValue(detailUrl, "cat");
                        entId = extractValue(detailUrl, "id");
                    } else {
                        cat = "1";
                        entId = id;
                    }
                } catch (Exception e) {
                    cat = "1";
                    entId = id;
                }
            }

            // 构建详情基础URL并请求
            String baseUrl = API_DETAIL + cat + "&id=" + entId;
            String detailUrl = baseUrl + "&callback=";
            String response = fetchWithRetry(detailUrl, REFERER_WEB, SUCCESS_MARKER_DETAIL);
            JSONObject json = new JSONObject(response);
            JSONObject data = json.optJSONObject("data");
            if (data == null) return Result.error("详情为空");

            // 构建Vod信息（注意：moviecategory/area/director/actor 是 JSONArray）
            Vod vod = new Vod();
            vod.setVodId(id);
            vod.setVodName(data.optString("title", ""));
            vod.setVodPic(fixUrl(data.optString("cdncover", "")));
            vod.setTypeName(jsonArrayToString(data.optJSONArray("moviecategory")));
            vod.setVodYear(data.optString("pubdate", ""));
            vod.setVodArea(jsonArrayToString(data.optJSONArray("area")));
            vod.setVodActor(jsonArrayToString(data.optJSONArray("actor")));
            vod.setVodDirector(jsonArrayToString(data.optJSONArray("director")));
            vod.setVodContent(data.optString("description", ""));

            // 构建播放源映射
            LinkedHashMap<String, String> playMap = new LinkedHashMap<>();
            JSONArray playlinkSites = data.optJSONArray("playlink_sites");
            JSONObject playlinksdetail = data.optJSONObject("playlinksdetail");

            // 路径1：allepidetail - 全量剧集（需多次HTTP请求分批获取）
            if (data.has("allepidetail") && playlinkSites != null) {
                for (int i = 0; i < playlinkSites.length(); i++) {
                    try {
                        String site = playlinkSites.getString(i);

                        // 先请求获取总集数
                        String siteUrl = buildDetailUrl(baseUrl, site, 0, 0);
                        String siteResp = fetchWithRetry(siteUrl, REFERER_WEB, SUCCESS_MARKER_DETAIL);
                        JSONObject siteJson = new JSONObject(siteResp);
                        JSONObject siteData = siteJson.optJSONObject("data");
                        if (siteData == null) continue;

                        JSONObject sitePlsd = siteData.optJSONObject("playlinksdetail");
                        JSONObject allupinfo = sitePlsd != null ? sitePlsd.optJSONObject("allupinfo") : null;
                        int totalCount = 0;
                        if (allupinfo != null && allupinfo.has(site)) {
                            totalCount = Integer.parseInt(String.valueOf(allupinfo.get(site)));
                        }

                        if (totalCount <= 0) continue;

                        // 分批获取剧集（每批200集）
                        int batchIdx = 0;
                        for (int start = 0; start < totalCount; start += EPISODE_BATCH_SIZE) {
                            int end = Math.min(start + EPISODE_BATCH_SIZE, totalCount);
                            batchIdx++;
                            String batchUrl = buildDetailUrl(baseUrl, site, start, end);
                            JSONArray eps = fetchSiteEpisodes(batchUrl, REFERER_WEB, site, "allepidetail");
                            if (eps == null || eps.length() == 0) continue;

                            ArrayList<String> epList = new ArrayList<>();
                            buildEpisodes(epList, eps);
                            if (epList.isEmpty()) continue;

                            String key = (totalCount > EPISODE_BATCH_SIZE) ? site + " " + batchIdx : site;
                            playMap.put(key, Util.join("#", epList));
                        }
                    } catch (Exception e) {
                        // skip invalid site
                    }
                }
            }

            // 路径2：defaultepisode - 默认剧集（需HTTP请求获取）
            if (playMap.isEmpty() && data.has("defaultepisode") && playlinkSites != null) {
                for (int i = 0; i < playlinkSites.length(); i++) {
                    try {
                        String site = playlinkSites.getString(i);

                        // 综艺（tid==3）且有tag时，按年份遍历获取
                        if ("3".equals(cat) && data.has("tag")) {
                            JSONObject tagObj = data.optJSONObject("tag");
                            if (tagObj != null) {
                                Iterator<String> keys = tagObj.keys();
                                while (keys.hasNext()) {
                                    try {
                                        String year = keys.next();
                                        String yearUrl = baseUrl + "&year=" + year + "&callback=";
                                        JSONArray eps = fetchSiteEpisodes(yearUrl, REFERER_WEB, site, "defaultepisode");
                                        if (eps == null || eps.length() == 0) continue;
                                        ArrayList<String> epList = new ArrayList<>();
                                        buildEpisodes(epList, eps);
                                        if (!epList.isEmpty()) {
                                            playMap.put(site, Util.join("#", epList));
                                        }
                                    } catch (Exception e) {
                                        // skip invalid year
                                    }
                                }
                            }
                        } else {
                            // 非综艺：请求获取该site的defaultepisode
                            String siteUrl = buildDetailUrl(baseUrl, site, 0, 0);
                            String siteResp = fetchWithRetry(siteUrl, REFERER_WEB, SUCCESS_MARKER_DETAIL);
                            JSONObject siteJson = new JSONObject(siteResp);
                            JSONObject siteData = siteJson.optJSONObject("data");
                            if (siteData == null) continue;

                            JSONObject defaultEp = siteData.optJSONObject("defaultepisode");
                            if (defaultEp == null || !defaultEp.has(site)) continue;

                            int totalCount = 0;
                            try {
                                totalCount = Integer.parseInt(String.valueOf(defaultEp.get(site)));
                            } catch (Exception ignored) {}

                            if (totalCount <= 0) {
                                // 无集数信息，直接取数据
                                JSONArray eps = siteData.optJSONObject("defaultepisode") != null
                                        ? siteData.optJSONObject("defaultepisode").optJSONArray(site) : null;
                                if (eps == null || eps.length() == 0) continue;
                                ArrayList<String> epList = new ArrayList<>();
                                buildEpisodes(epList, eps);
                                if (!epList.isEmpty()) {
                                    playMap.put(site, Util.join("#", epList));
                                }
                            } else {
                                // 分批获取
                                int batchIdx = 0;
                                for (int start = 0; start < totalCount; start += EPISODE_BATCH_SIZE) {
                                    int end = Math.min(start + EPISODE_BATCH_SIZE, totalCount);
                                    batchIdx++;
                                    String batchUrl = buildDetailUrl(baseUrl, site, start, end);
                                    JSONArray eps = fetchSiteEpisodes(batchUrl, REFERER_WEB, site, "defaultepisode");
                                    if (eps == null || eps.length() == 0) continue;
                                    ArrayList<String> epList = new ArrayList<>();
                                    buildEpisodes(epList, eps);
                                    if (epList.isEmpty()) continue;
                                    String key = (totalCount > EPISODE_BATCH_SIZE) ? site + " " + batchIdx : site;
                                    playMap.put(key, Util.join("#", epList));
                                }
                            }
                        }
                    } catch (Exception e) {
                        // skip invalid site
                    }
                }
            }

            // 路径3：playlinksdetail - 从每个site取default_url构建单条播放
            if (playMap.isEmpty() && playlinksdetail != null && playlinkSites != null) {
                for (int i = 0; i < playlinkSites.length(); i++) {
                    try {
                        String site = playlinkSites.getString(i);
                        JSONObject siteObj = playlinksdetail.optJSONObject(site);
                        if (siteObj == null) continue;
                        String defaultUrl = cleanUrl(siteObj.optString("default_url", ""));
                        if (defaultUrl.isEmpty()) continue;
                        playMap.put(site, "正片$" + defaultUrl);
                    } catch (Exception e) {
                        // skip invalid site
                    }
                }
            }

            // 设置播放源
            if (!playMap.isEmpty()) {
                ArrayList<String> playFromList = new ArrayList<>(playMap.keySet());
                ArrayList<String> playUrlList = new ArrayList<>(playMap.values());
                vod.setVodPlayFrom(Util.join("$$$", playFromList));
                vod.setVodPlayUrl(Util.join("$$$", playUrlList));
            }

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
