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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Glod 视频源爬虫。
 * 提供搜索、分类列表、详情页解析及播放源获取。
 */
public class Glod extends Spider {

    private static final String TOKEN = "cb808529bae6b6be45ecfab29a4889bc";
    private static final String BASE_URL = "https://m.sdzhgt.com/";
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36";

    private HashMap<String, String> buildHeaders() {
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
        HashMap<String, String> headers = new HashMap<>();
        headers.put("User-Agent", USER_AGENT);
        headers.put("referer", BASE_URL);
        headers.put("t", timestamp);
        headers.put("token", TOKEN);
        return headers;
    }

    @Override
    public void init(Context context, String extend) throws Exception {
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        ArrayList<Class> classes = new ArrayList<>();
        classes.add(new Class("1", "电影"));
        classes.add(new Class("2", "电视剧"));
        classes.add(new Class("3", "综艺"));
        classes.add(new Class("4", "动漫"));
        return Result.string(classes, new ArrayList<>());
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        if (TextUtils.isEmpty(pg)) pg = "1";
        HashMap<String, String> params = new HashMap<>();
        params.put("type", tid);
        params.put("page", pg);
        String url = BASE_URL + "api.php/provide/vod/";
        String body = OkHttp.post(url, params, buildHeaders());
        JSONObject response = new JSONObject(body);
        JSONArray list = response.optJSONArray("list");
        ArrayList<Vod> vodList = new ArrayList<>();
        if (list != null) {
            for (int i = 0; i < list.length(); i++) {
                JSONObject item = list.optJSONObject(i);
                if (item == null) continue;
                Vod vod = new Vod();
                vod.setVodId(item.optString("vod_id"));
                vod.setVodName(item.optString("vod_name"));
                vod.setVodPic(item.optString("vod_pic"));
                vod.setVodRemarks(item.optString("vod_remarks"));
                vodList.add(vod);
            }
        }
        return Result.string(vodList);
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        String vodId = ids.get(0);
        HashMap<String, String> params = new HashMap<>();
        params.put("ids", vodId);
        String url = BASE_URL + "api.php/provide/vod/";
        String body = OkHttp.string(url, params, buildHeaders());
        JSONObject response = new JSONObject(body);
        JSONArray list = response.optJSONArray("list");
        if (list == null || list.length() == 0) {
            return Result.error("详情为空");
        }
        JSONObject item = list.optJSONObject(0);
        Vod vod = new Vod();
        vod.setVodId(vodId);
        vod.setVodName(item.optString("vod_name"));
        vod.setVodPic(item.optString("vod_pic"));
        vod.setVodRemarks(item.optString("vod_remarks"));
        vod.setVodYear(item.optString("vod_year"));
        vod.setVodArea(item.optString("vod_area"));
        vod.setVodDirector(item.optString("vod_director"));
        vod.setVodActor(item.optString("vod_actor"));
        vod.setVodContent(item.optString("vod_content"));
        vod.setTypeName(item.optString("type_name"));
        String playFrom = item.optString("vod_play_from");
        String playUrl = item.optString("vod_play_url");
        vod.setVodPlayFrom(playFrom);
        vod.setVodPlayUrl(playUrl);
        return Result.string(vod);
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        HashMap<String, String> headers = buildHeaders();
        return Result.get().url(id).header(headers).string();
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        HashMap<String, String> params = new HashMap<>();
        params.put("wd", key);
        String url = BASE_URL + "api.php/provide/vod/";
        String body = OkHttp.string(url, params, buildHeaders());
        JSONObject response = new JSONObject(body);
        JSONArray list = response.optJSONArray("list");
        ArrayList<Vod> vodList = new ArrayList<>();
        if (list != null) {
            for (int i = 0; i < list.length(); i++) {
                JSONObject item = list.optJSONObject(i);
                if (item == null) continue;
                Vod vod = new Vod();
                vod.setVodId(item.optString("vod_id"));
                vod.setVodName(item.optString("vod_name"));
                vod.setVodPic(item.optString("vod_pic"));
                vod.setVodRemarks(item.optString("vod_remarks"));
                vodList.add(vod);
            }
        }
        return Result.string(vodList);
    }
}