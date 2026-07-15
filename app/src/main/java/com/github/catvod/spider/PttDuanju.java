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

/**
 * PTT 短剧源爬虫
 * 支持搜索、分类浏览、详情解析、播放地址获取
 */
public class PttDuanju extends Spider {

    private String baseUrl;
    private String userAgent;
    private String homePath;

    public static JSONArray parseVideoListJson(String jsonStr) {
        if (TextUtils.isEmpty(jsonStr)) return new JSONArray();
        String list = PttDuanjuNative.parseList(jsonStr);
        if (TextUtils.isEmpty(list)) return new JSONArray();
        
        try {
            return new JSONArray(list);
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    public void ensureInitialized() {
        if (TextUtils.isEmpty(baseUrl)) {
            baseUrl = PttDuanjuNative.siteBase();
            userAgent = PttDuanjuNative.userAgent();
            homePath = PttDuanjuNative.homePath();
        }
    }

    public String fixUrlWithProxy(String url) {
        if (TextUtils.isEmpty(url)) return "";

        if (!url.startsWith("http")) {
            url = (url.startsWith("/") ? baseUrl : baseUrl + "/") + url;
        }

        try {
            return "http://127.0.0.1:9978/?url=" + URLEncoder.encode(url, "UTF-8") +
                   "&referer=" + URLEncoder.encode(baseUrl + "/", "UTF-8");
        } catch (Exception e) {
            return url;
        }
    }

    public HashMap<String, String> buildHeaders(String referer) {
        ensureInitialized();
        HashMap<String, String> headers = new HashMap<>();
        headers.put("User-Agent", userAgent);
        if (!TextUtils.isEmpty(referer)) {
            headers.put("Referer", referer);
        }
        return headers;
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        ensureInitialized();

        List<Class> classes = new ArrayList<>();
        classes.add(new Class("全部短剧", "全部短剧"));
        classes.add(new Class("热门推荐", "热门推荐"));
        classes.add(new Class("最新上架", "最新上架"));
        classes.add(new Class("都市言情", "都市言情"));
        classes.add(new Class("古装穿越", "古装穿越"));
        classes.add(new Class("悬疑推理", "悬疑推理"));
        classes.add(new Class("甜宠萌宝", "甜宠萌宝"));
        classes.add(new Class("逆袭重生", "逆袭重生"));
        classes.add(new Class("霸总豪门", "霸总豪门"));

        return Result.string(classes, new ArrayList<>());
    }

    @Override
    public String homeVideoContent() throws Exception {
        ensureInitialized();

        String url = baseUrl + homePath;
        String response = OkHttp.string(url, buildHeaders(baseUrl));

        List<Vod> list = new ArrayList<>();
        JSONArray array = parseVideoListJson(response);
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item == null) continue;

            Vod vod = new Vod();
            vod.setVodId(item.optString("vod_id", ""));
            vod.setVodName(item.optString("vod_name", ""));
            vod.setVodPic(fixUrlWithProxy(item.optString("vod_pic", "")));
            vod.setVodRemarks(item.optString("vod_remarks", ""));
            list.add(vod);
        }

        return Result.string(list);
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        ensureInitialized();

        String url = baseUrl + PttDuanjuNative.categoryPath(tid, pg);
        String referer = baseUrl + homePath;
        String response = OkHttp.string(url, buildHeaders(referer));

        List<Vod> list = new ArrayList<>();
        JSONArray array = parseVideoListJson(response);
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item == null) continue;

            Vod vod = new Vod();
            vod.setVodId(item.optString("vod_id", ""));
            vod.setVodName(item.optString("vod_name", ""));
            vod.setVodPic(fixUrlWithProxy(item.optString("vod_pic", "")));
            vod.setVodRemarks(item.optString("vod_remarks", ""));
            list.add(vod);
        }

        return Result.string(list);
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        ensureInitialized();

        String videoId = ids.get(0);
        String referer = baseUrl + homePath;
        String html = OkHttp.string(videoId, buildHeaders(referer));

        String detail = PttDuanjuNative.parseDetail(videoId, html);
        JSONObject detailJson = TextUtils.isEmpty(detail) ? new JSONObject() : new JSONObject(detail);

        Vod vod = new Vod();
        vod.setVodId(videoId);
        vod.setVodName(detailJson.optString("vod_name", ""));
        vod.setVodPic(fixUrlWithProxy(detailJson.optString("vod_pic", "")));
        vod.setVodDirector("");
        vod.setVodPlayFrom(detailJson.optString("vod_play_from", "PTT短剧"));
        vod.setVodPlayUrl(detailJson.optString("vod_play_url", ""));

        return Result.string(vod);
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        ensureInitialized();

        String referer = id.contains("/") ? id.substring(0, id.lastIndexOf('/')) : baseUrl;
        String html = OkHttp.string(id, buildHeaders(referer));

        String playUrl = PttDuanjuNative.parsePlayMedia(html);

        HashMap<String, String> headers = new HashMap<>();
        headers.put("User-Agent", userAgent);
        headers.put("Referer", id);
        headers.put("Origin", baseUrl);
        headers.put("Cookie", "ptt");

        return Result.get().url(playUrl).header(headers).string();
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        return searchContent(key, quick, "1");
    }

    @Override
    public String searchContent(String key, boolean quick, String pg) throws Exception {
        ensureInitialized();

        String url = baseUrl + PttDuanjuNative.searchPath(key, pg);
        String referer = baseUrl + homePath;
        String response = OkHttp.string(url, buildHeaders(referer));

        List<Vod> list = new ArrayList<>();
        JSONArray array = parseVideoListJson(response);
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item == null) continue;

            Vod vod = new Vod();
            vod.setVodId(item.optString("vod_id", ""));
            vod.setVodName(item.optString("vod_name", ""));
            vod.setVodPic(fixUrlWithProxy(item.optString("vod_pic", "")));
            vod.setVodRemarks(item.optString("vod_remarks", ""));
            list.add(vod);
        }

        return Result.string(list);
    }

    @Override
    public void init(Context context, String extend) throws Exception {
        super.init(context, extend);
        ensureInitialized();
    }
}