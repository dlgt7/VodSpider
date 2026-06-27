package com.github.catvod.spider;

import android.content.Context;
import android.text.TextUtils;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Filter;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.net.OkHttp;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;

public class AppRJ extends Spider {

    private static final String SALT = "7gp0bnd2sr85ydii2j32pcypscoc4w6c7g5spl";

    public String siteUrl;

    public AppRJ() {
        this.siteUrl = "";
    }

    public static void addFilter(ArrayList<Filter> filters, JSONObject data, String key, String filterKey, String displayName) {
        JSONArray array = data.optJSONArray(key);
        if (array == null || array.length() <= 1) return;
        ArrayList<Filter.Value> values = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            String value = array.optString(i);
            if (value.length() > 1) {
                values.add(new Filter.Value(value, value));
            }
        }
        if (values.size() > 1) {
            filters.add(new Filter(filterKey, displayName, values));
        }
    }

    public static void putExtendParam(HashMap<String, String> params, HashMap<String, String> extend, String key) {
        if (extend.containsKey(key)) {
            params.put(key, extend.get(key));
        }
    }

    public static String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                String hex = Integer.toHexString(b & 0xff);
                if (hex.length() == 1) sb.append('0');
                sb.append(hex);
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    public static HashMap<String, String> buildCommonParams() {
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
        HashMap<String, String> params = new HashMap<>();
        params.put("timestamp", timestamp);
        String sign = md5(SALT + timestamp);
        params.put("sign", sign);
        return params;
    }

    public static Vod parseVod(JSONObject item) {
        Vod vod = new Vod();
        vod.setVodId(item.optString("vod_id"));
        vod.setVodName(item.optString("vod_name"));
        String pic = item.optString("vod_pic");
        if (TextUtils.isEmpty(pic)) pic = item.optString("vod_pic_thumb");
        vod.setVodPic(pic);
        vod.setVodRemarks(item.optString("vod_remarks"));
        return vod;
    }

    public JSONObject fetch(String path, HashMap<String, String> params) throws Exception {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("User-Agent", "okhttp-okgo/jeasonlzy");
        String url = this.siteUrl + path;
        String body = OkHttp.post(url, params, headers);
        if (TextUtils.isEmpty(body)) {
            return new JSONObject();
        }
        return new JSONObject(body);
    }

    @Override
    public void init(Context context, String extend) {
        try {
            JSONObject json = new JSONObject(extend);
            String url = json.optString("url", "");
            url = url.replaceAll("/+$", "");
            this.siteUrl = url;
        } catch (Exception e) {
        }
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        HashMap<String, String> params = buildCommonParams();
        JSONObject response = fetch("/v3/type/top_type", params);
        JSONObject data = response.optJSONObject("data");
        JSONArray list = data == null ? null : data.optJSONArray("list");
        ArrayList<Class> classes = new ArrayList<>();
        LinkedHashMap<String, List<Filter>> filters = new LinkedHashMap<>();
        if (list == null) {
            return Result.string(classes, filters);
        }
        for (int i = 0; i < list.length(); i++) {
            JSONObject item = list.optJSONObject(i);
            if (item == null) continue;
            String typeId = item.optString("type_id");
            classes.add(new Class(typeId, item.optString("type_name")));
            ArrayList<Filter> itemFilters = new ArrayList<>();
            addFilter(itemFilters, item, "extend", "class", "类型");
            addFilter(itemFilters, item, "area", "area", "地区");
            addFilter(itemFilters, item, "year", "year", "年份");
            addFilter(itemFilters, item, "lang", "lang", "语言");
            if (!itemFilters.isEmpty()) {
                filters.put(typeId, itemFilters);
            }
        }
        return Result.string(classes, filters);
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        HashMap<String, String> params = buildCommonParams();
        params.put("type_id", tid);
        params.put("limit", "12");
        if (TextUtils.isEmpty(pg)) pg = "1";
        params.put("page", pg);
        if (extend != null) {
            putExtendParam(params, extend, "area");
            putExtendParam(params, extend, "class");
            putExtendParam(params, extend, "lang");
            putExtendParam(params, extend, "year");
        }
        JSONObject response = fetch("/v3/home/type_search", params);
        JSONObject data = response.optJSONObject("data");
        JSONArray list = data == null ? null : data.optJSONArray("list");
        ArrayList<Vod> vodList = new ArrayList<>();
        if (list != null) {
            for (int i = 0; i < list.length(); i++) {
                JSONObject item = list.optJSONObject(i);
                if (item != null) vodList.add(parseVod(item));
            }
        }
        return Result.string(vodList);
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        HashMap<String, String> params = buildCommonParams();
        String vodId = ids.get(0);
        params.put("vod_id", vodId);
        JSONObject response = fetch("/v3/home/vod_details", params);
        JSONObject data = response.optJSONObject("data");
        if (data == null) {
            return Result.error("详情为空");
        }
        Vod vod = new Vod();
        vod.setVodId(vodId);
        vod.setVodName(data.optString("vod_name"));
        String pic = data.optString("vod_pic");
        if (TextUtils.isEmpty(pic)) pic = data.optString("vod_pic_thumb");
        vod.setVodPic(pic);
        vod.setVodRemarks(data.optString("vod_remarks"));
        vod.setVodContent(data.optString("vod_content"));
        vod.setVodYear(data.optString("vod_year"));
        vod.setVodActor(data.optString("vod_actor"));
        vod.setVodDirector(data.optString("vod_director"));
        vod.setTypeName(data.optString("vod_class"));
        JSONArray playList = data.optJSONArray("vod_play_list");
        ArrayList<String> playFrom = new ArrayList<>();
        ArrayList<String> playUrl = new ArrayList<>();
        if (playList != null) {
            for (int i = 0; i < playList.length(); i++) {
                JSONObject playItem = playList.optJSONObject(i);
                if (playItem == null) continue;
                String name = playItem.optString("name");
                playFrom.add(name);
                String ua = playItem.optString("ua");
                JSONArray parseUrls = playItem.optJSONArray("parse_urls");
                StringBuilder parseUrlsStr = new StringBuilder();
                if (parseUrls != null) {
                    for (int j = 0; j < parseUrls.length(); j++) {
                        parseUrlsStr.append(parseUrls.optString(j)).append("@");
                    }
                }
                JSONArray urls = playItem.optJSONArray("urls");
                ArrayList<String> urlList = new ArrayList<>();
                if (urls != null) {
                    for (int j = 0; j < urls.length(); j++) {
                        JSONObject urlItem = urls.optJSONObject(j);
                        if (urlItem == null) continue;
                        StringBuilder sb = new StringBuilder();
                        sb.append(urlItem.optString("name")).append("$");
                        sb.append(parseUrlsStr).append("|");
                        sb.append(urlItem.optString("url")).append("|");
                        sb.append(ua).append("|");
                        sb.append(data.optString(vodId)).append("|");
                        sb.append(urlItem.optString("nid"));
                        urlList.add(sb.toString());
                    }
                }
                playUrl.add(TextUtils.join("#", urlList));
            }
        }
        vod.setVodPlayFrom(TextUtils.join("$$$", playFrom));
        vod.setVodPlayUrl(TextUtils.join("$$$", playUrl));
        return Result.string(vod);
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        HashMap<String, String> params = buildCommonParams();
        params.put("keyword", key);
        params.put("limit", "12");
        params.put("page", "1");
        JSONObject response = fetch("/v3/home/search", params);
        JSONObject data = response.optJSONObject("data");
        JSONArray list = data == null ? null : data.optJSONArray("list");
        ArrayList<Vod> vodList = new ArrayList<>();
        if (list != null) {
            for (int i = 0; i < list.length(); i++) {
                JSONObject item = list.optJSONObject(i);
                if (item != null) vodList.add(parseVod(item));
            }
        }
        return Result.string(vodList);
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        String[] parts = id.split("\\|");
        if (parts.length == 5) {
            StringBuilder sb = new StringBuilder();
            sb.append(parts[0]).append("|");
            sb.append(parts[1]).append("|");
            sb.append(parts[2]).append("||");
            sb.append(parts[3]).append("|");
            sb.append(parts[4]);
            parts = sb.toString().split("\\|");
        }
        if (parts.length < 2) {
            return Result.error("播放参数错误");
        }
        String parseUrlsStr = parts[0];
        String url = parts[1];
        String ua = parts.length > 2 ? parts[2] : "";
        if (!TextUtils.isEmpty(parseUrlsStr)) {
            String[] parseUrls = parseUrlsStr.split("@");
            for (int i = 0; i < parseUrls.length; i++) {
                String parseUrl = parseUrls[i];
                if (TextUtils.isEmpty(parseUrl)) continue;
                String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
                String sign = md5(SALT + timestamp);
                StringBuilder reqUrl = new StringBuilder();
                reqUrl.append(parseUrl).append(url);
                reqUrl.append("&sign=").append(sign);
                reqUrl.append("&timestamp=").append(timestamp);
                String body = OkHttp.string(reqUrl.toString(), new HashMap<>());
                JSONObject json = new JSONObject(body);
                url = json.optString("url");
                ua = json.optString("UA", ua);
                if (!TextUtils.isEmpty(url) && url.startsWith("http")) {
                    break;
                }
            }
        }
        if (!url.startsWith("http")) {
            return Result.error("无播放地址");
        }
        HashMap<String, String> headers = new HashMap<>();
        if (!TextUtils.isEmpty(ua)) {
            headers.put("User-Agent", ua);
        }
        return Result.get().url(url).header(headers).string();
    }
}
