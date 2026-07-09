package com.github.catvod.spider;

import android.content.Context;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.net.OkHttp;

import org.json.JSONArray;
import org.json.JSONObject;

import java.math.BigInteger;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Glod 视频源爬虫。
 * 基于 m.sdzhgt.com API，提供搜索、分类列表、详情页解析、播放源获取及弹幕支持。
 */
public class Glod extends Spider {

    private static final String TOKEN = "cb808529bae6b6be45ecfab29a4889bc";

    /** 弹幕缓存，key=缓存键（vodId/nid），value=[danmuUrl, nid] */
    private static final Map<String, String[]> danmuCache = new ConcurrentHashMap<>();

    /** User-Agent 请求头值 */
    private String userAgent;
    /** 签名令牌 */
    private String token;
    /** 备用视频 URL */
    private String fallbackUrl;
    /** 站点根 URL */
    private String hostUrl;

    public Glod() {
        this.token = TOKEN;
    }

    /** 构建基础请求头（User-Agent + referer） */
    private Map<String, String> buildBaseHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", this.userAgent);
        headers.put("referer", this.hostUrl);
        return headers;
    }

    /** 构建签名请求头，包含 User-Agent、referer、t（时间戳）、sign（签名） */
    private Map<String, String> buildHeaders(String p1) {
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
        String tParam = "&t=";

        String signStr;
        if (p1 == null || p1.isEmpty()) {
            signStr = "key=" + this.token + tParam + timestamp;
        } else {
            signStr = p1 + "&key=" + this.token + tParam + timestamp;
        }
        String sign = sha1(signStr);

        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", this.userAgent);
        headers.put("referer", this.hostUrl);
        headers.put("t", timestamp);
        headers.put("sign", sign);
        return headers;
    }

    /** SHA-1 哈希，返回 40 字符十六进制字符串 */
    private static String sha1(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] digest = md.digest(input.getBytes("UTF-8"));
            String hex = new BigInteger(1, digest).toString(16);
            while (hex.length() < 40) hex = "0" + hex;
            return hex;
        } catch (Exception e) {
            return "";
        }
    }

    /** MD5 哈希，返回 32 字符十六进制字符串 */
    private static String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes("UTF-8"));
            String hex = new BigInteger(1, digest).toString(16);
            while (hex.length() < 32) hex = "0" + hex;
            return hex;
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    public void init(Context context, String extend) throws Exception {
        this.hostUrl = "https://m.sdzhgt.com/";
        this.userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36";
        this.fallbackUrl = "https://sf1-cdn-tos.huoshanstatic.com/obj/media-fe/xgplayer_doc_video/mp4/xgplayer-demo-720p.mp4";
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        ArrayList<Class> classes = new ArrayList<>();
        classes.add(new Class("1", "电影"));
        classes.add(new Class("2", "电视剧"));
        classes.add(new Class("3", "综艺"));
        classes.add(new Class("4", "动漫"));

        ArrayList<Vod> vodList = new ArrayList<>();
        JSONObject extObj = new JSONObject();
        try {
            String url = this.hostUrl + "/api/mw-movie/anonymous/home/hotSearch";
            String json = OkHttp.string(url, buildBaseHeaders());
            JSONObject response = new JSONObject(json);
            JSONObject data = response.optJSONObject("data");
            if (data != null) {
                JSONArray hotList = data.optJSONArray("typeId1");
                if (hotList == null) hotList = data.optJSONArray("hotSearch");
                if (hotList != null) {
                    for (int i = 0; i < hotList.length(); i++) {
                        JSONObject item = hotList.optJSONObject(i);
                        if (item == null) continue;
                        int typeId = item.optInt("typeId1", 0);
                        String vodNameKey = (typeId == 1) ? "vodName" : "vodVersion";
                        String vodPicKey = (typeId == 1) ? "vodPic" : "vodRemarks";
                        String name = item.optString("vodName");
                        String pic = item.optString("vodPic");
                        String remarks = item.optString("vodRemarks", "");
                        String vodId = item.optString("vodId", "");
                        if (name.isEmpty()) name = item.optString("vodVersion", "");
                        if (pic.isEmpty()) pic = item.optString("vodRemarks", "");
                        vodList.add(new Vod(vodId, name, pic, remarks));
                    }
                }
            }
        } catch (Exception e) {
            // 首页列表获取失败时忽略，仅返回分类
        }
        return Result.string(classes, vodList, extObj);
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        // 提取筛选参数
        String type = extend != null && extend.containsKey("type") ? extend.get("type") : "";
        String cls = extend != null && extend.containsKey("class") ? extend.get("class") : "";
        String area = extend != null && extend.containsKey("area") ? extend.get("area") : "";
        String year = extend != null && extend.containsKey("year") ? extend.get("year") : "";
        String lang = extend != null && extend.containsKey("lang") ? extend.get("lang") : "";
        String by = extend != null && extend.containsKey("by") ? extend.get("by") : "";

        // 构建请求 URL
        StringBuilder urlBuilder = new StringBuilder();
        urlBuilder.append(this.hostUrl);
        urlBuilder.append("/vod/show/id/");
        urlBuilder.append(tid);
        urlBuilder.append(type);
        urlBuilder.append(cls);
        urlBuilder.append(area);
        urlBuilder.append(year);
        urlBuilder.append(lang);
        urlBuilder.append(by);
        urlBuilder.append("/page/");
        urlBuilder.append(pg);

        ArrayList<Vod> vodList = new ArrayList<>();
        try {
            String url = urlBuilder.toString();
            String json = OkHttp.string(url, buildBaseHeaders());

            // 处理 JSON 响应：替换 \" 为 " 并解析
            String cleaned = json.replace("\\\"", "\"").replace("\\\\", "\\");
            int listStart = cleaned.indexOf("\"list\":[");
            if (listStart != -1) {
                // 通过方括号深度追踪提取 list 数组
                int start = listStart + 8; // skip "list":[
                int depth = 0;
                int arrayStart = -1;
                for (int i = start; i < cleaned.length(); i++) {
                    char c = cleaned.charAt(i);
                    if (c == '[') {
                        if (depth == 0) arrayStart = i;
                        depth++;
                    } else if (c == ']') {
                        depth--;
                        if (depth == 0) {
                            String listJson = cleaned.substring(arrayStart, i + 1);
                            JSONArray listArray = new JSONArray(listJson);
                            for (int j = 0; j < listArray.length(); j++) {
                                JSONObject item = listArray.optJSONObject(j);
                                if (item == null) continue;
                                int typeId = item.optInt("typeId1", 0);
                                String nameKey = (typeId == 1) ? "vodName" : "vodVersion";
                                String picKey = (typeId == 1) ? "vodPic" : "vodRemarks";
                                String name = item.optString(nameKey);
                                String pic = item.optString(picKey);
                                String remarks = item.optString("vodRemarks", "");
                                String vodId = item.optString("vodId", "");
                                if (name.isEmpty()) name = item.optString("vodVersion", "");
                                if (pic.isEmpty()) pic = item.optString("vodRemarks", "");
                                vodList.add(new Vod(vodId, name, pic, remarks));
                            }
                            break;
                        }
                    }
                }
            }
        } catch (Exception e) {
            // 解析异常时返回空列表
        }
        return Result.string(vodList);
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        danmuCache.clear();
        String vodId = ids.get(0);

        try {
            // 请求详情 API
            String url = this.hostUrl + "/api/mw-movie/anonymous/video/detail?id=" + vodId;
            String signParams = "id=" + vodId;
            String json = OkHttp.string(url, buildHeaders(signParams));

            JSONObject response = new JSONObject(json);
            JSONObject data = response.optJSONObject("data");

            Vod vod = new Vod();
            vod.setTypeName(data != null ? data.optString("typeName", "") : "");
            vod.setVodId(vodId);
            vod.setVodName(data != null ? data.optString("vodName", "") : "");
            vod.setVodRemarks(data != null ? data.optString("vodRemarks", "") : "");
            vod.setVodYear(data != null ? data.optString("vodYear", "") : "");
            vod.setVodArea(data != null ? data.optString("vodArea", "") : "");
            vod.setVodActor(data != null ? data.optString("vodActor", "") : "");
            vod.setVodDirector(data != null ? data.optString("vodDirector", "") : "");
            vod.setVodContent(data != null ? data.optString("vodContent", "") : "");

            // 处理剧集列表和弹幕缓存
            if (data != null) {
                JSONArray episodeList = data.optJSONArray("episodeList");
                if (episodeList != null && episodeList.length() > 0) {
                    StringBuilder playUrl = new StringBuilder();
                    StringBuilder danmuStr = new StringBuilder();
                    for (int i = 0; i < episodeList.length(); i++) {
                        JSONObject ep = episodeList.optJSONObject(i);
                        if (ep == null) continue;
                        String epName = ep.optString("name", "");
                        String nid = ep.optString("nid", "");
                        String cacheKey = vodId + "/" + nid;
                        String danmuUrl = epName;
                        // 拼接剧集：epName$nid#epName$nid#...
                        if (playUrl.length() > 0) {
                            playUrl.append("#");
                            danmuStr.append("#");
                        }
                        playUrl.append(epName).append("$").append(nid);
                        danmuStr.append(epName).append("$").append(nid);
                        danmuCache.put(cacheKey, new String[]{danmuUrl, nid});
                    }
                    vod.setVodPlayFrom("默认");
                    vod.setVodPlayUrl(playUrl.toString());
                }
            }
            return Result.string(vod);
        } catch (Exception e) {
            return Result.string(new Vod());
        }
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        try {
            // 从弹幕缓存获取数据
            String[] danmuData = danmuCache.get(id);
            String danmuUrl = "";
            String nid = "";

            if (danmuData != null && danmuData.length == 2) {
                danmuUrl = danmuData[0];
                nid = danmuData[1];
            }

            // 按 "/" 分隔 id，提取 vodId 和 epId
            String[] parts = id.split("/");
            if (parts.length < 2) {
                return Result.get().url(this.fallbackUrl).string();
            }
            String vodId = parts[0];
            String epId = parts[1];

            // 构建播放 URL
            String playUrl = "id=" + vodId + "&nid=" + epId;
            String fullUrl = this.hostUrl + "/api/mw-movie/anonymous/v2/video/episode/url?" + playUrl;
            String json = OkHttp.string(fullUrl, buildHeaders(playUrl));

            JSONObject response = new JSONObject(json);
            JSONObject dataObj = response.optJSONObject("data");
            JSONArray listArray = null;
            String videoUrl = "";
            if (dataObj != null) {
                listArray = dataObj.optJSONArray("list");
                if (listArray != null && listArray.length() > 0) {
                    JSONObject firstItem = listArray.optJSONObject(0);
                    videoUrl = firstItem != null ? firstItem.optString("url", "") : "";
                }
            }

            // 构建返回结果
            Map<String, String> headers = new HashMap<>();
            headers.put("User-Agent", this.userAgent);

            String finalUrl;
            if (videoUrl.isEmpty() && (danmuUrl == null || danmuUrl.isEmpty())) {
                finalUrl = "";
            } else {
                try {
                    finalUrl = OkHttp.string(videoUrl.isEmpty() ? danmuUrl : videoUrl, headers);
                } catch (Exception e) {
                    finalUrl = videoUrl.isEmpty() ? danmuUrl : videoUrl;
                }
            }

            JSONObject result = new JSONObject();
            result.put("parse", 0);
            result.put("header", new JSONObject(headers));
            result.put("danmaku", danmuUrl);
            result.put("url", finalUrl);
            return result.toString();
        } catch (Exception e) {
            return Result.get().url(this.fallbackUrl).string();
        }
    }

    @Override
    public Object[] proxy(Map<String, String> params) throws Exception {
        if (params != null) {
            String doValue = params.get("do");
            String danmuValue = params.get("danmu");
            if (doValue != null && doValue.equals(danmuValue)) {
                try {
                    return new Object[]{params};
                } catch (Exception e) {
                    // ignored
                }
            }
        }
        return null;
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        return searchContent(key, quick, "1");
    }

    @Override
    public String searchContent(String key, boolean quick, String pg) throws Exception {
        if (pg == null || pg.isEmpty()) pg = "1";

        ArrayList<Vod> vodList = new ArrayList<>();
        try {
            // 构建搜索 URL
            String searchUrl = this.hostUrl + "/api/mw-movie/anonymous/video/searchByWord?"
                    + "keyword=" + URLEncoder.encode(key, "UTF-8")
                    + "&pageNum=" + pg
                    + "&pageSize=12";

            String signUrl = this.hostUrl + "/api/mw-movie/anonymous/video/searchByWord?"
                    + "keyword=" + key + "&pageNum=" + pg + "&pageSize=12";

            String json = OkHttp.string(searchUrl, buildHeaders(signUrl));

            JSONObject response = new JSONObject(json);
            JSONObject data = response.optJSONObject("data");
            if (data != null) {
                JSONArray list = data.optJSONArray("list");
                if (list == null) list = data.optJSONArray("result");
                if (list != null) {
                    for (int i = 0; i < list.length(); i++) {
                        JSONObject item = list.optJSONObject(i);
                        if (item == null) continue;
                        int typeId = item.optInt("typeId1", 0);
                        String nameKey = (typeId == 1) ? "vodName" : "vodVersion";
                        String picKey = (typeId == 1) ? "vodPic" : "vodRemarks";
                        String name = item.optString(nameKey);
                        String pic = item.optString(picKey);
                        String remarks = item.optString("vodRemarks", "");
                        String vodId = item.optString("vodId", "");
                        if (name.isEmpty()) name = item.optString("vodVersion", "");
                        if (pic.isEmpty()) pic = item.optString("vodRemarks", "");
                        vodList.add(new Vod(vodId, name, pic, remarks));
                    }
                }
            }
        } catch (Exception e) {
            // 搜索异常时返回空列表
        }
        return Result.string(vodList);
    }
}
