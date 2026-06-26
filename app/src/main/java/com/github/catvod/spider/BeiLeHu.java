package com.github.catvod.spider;

import android.text.TextUtils;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.net.OkHttp;
import com.github.catvod.net.OkResult;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * BeiLeHu Spider - 贝乐虎儿童视频
 */
public class BeiLeHu extends Spider {

    private static final LinkedHashMap<String, String> a = new LinkedHashMap<>();

    static {
        a.put("56", "儿歌");
        a.put("63", "故事");
        a.put("62", "少儿");
        a.put("61", "古诗");
        a.put("64", "认知");
        a.put("65", "习惯");
    }

    private static JSONArray a(int subcateId, int page, HashMap<String, String> headers) {
        try {
            JSONObject json = new JSONObject();
            json.put("age", 1);
            json.put("appver", "6.1.9");
            json.put("egvip_status", 0);
            json.put("svip_status", 0);
            json.put("vps", 60);
            json.put("subcateId", subcateId);
            json.put("p", page);

            String url = "https://vd.ubestkid.com/api/v1/bv/video";
            String body = json.toString();
            String response = OkHttp.post(url, body, headers);

            JSONObject responseJson = new JSONObject(response);
            JSONObject resultObj = responseJson.optJSONObject("result");
            if (resultObj != null) {
                return resultObj.optJSONArray("items");
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private static ArrayList<Vod> b(JSONArray items, int limit) {
        ArrayList<Vod> list = new ArrayList<>();
        if (items == null) {
            return list;
        }

        int length = items.length();
        if (limit <= 0) {
            limit = length;
        }
        limit = Math.min(length, limit);

        for (int i = 0; i < limit; i++) {
            try {
                JSONObject item = items.getJSONObject(i);
                String url = item.optString("url");
                if (TextUtils.isEmpty(url)) {
                    continue;
                }

                String title = item.optString("title", "贝乐虎");
                String image = item.optString("image");

                StringBuilder sb = new StringBuilder();
                sb.append(url);
                sb.append("@@");
                sb.append(title);
                sb.append("@@");
                sb.append(image);
                String vodId = sb.toString();

                String remark = "";
                int endInterval = item.optInt("endInterval", 0);
                if (endInterval > 0) {
                    endInterval = endInterval / 10;
                    int minutes = endInterval / 60;
                    int seconds = endInterval % 60;
                    if (seconds < 10) {
                        remark = minutes + ":0" + seconds;
                    } else {
                        remark = minutes + ":" + seconds;
                    }
                }
                if (TextUtils.isEmpty(remark)) {
                    remark = item.optString("brand", "");
                }

                list.add(new Vod(vodId, title, image, remark));
            } catch (Exception e) {
                // ignore
            }
        }
        return list;
    }

    @Override
    public String homeContent(boolean filter) {
        ArrayList<Class> classes = new ArrayList<>();
        for (Map.Entry<String, String> entry : a.entrySet()) {
            classes.add(new Class(entry.getKey(), entry.getValue()));
        }

        HashMap<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("appver", "6.1.9");

        JSONArray items = a(1, 1, headers);
        ArrayList<Vod> list = b(items, 20);

        return Result.string(classes, list);
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) {
        int page;
        try {
            page = Integer.parseInt(pg);
        } catch (Exception e) {
            page = 1;
        }

        int subcateId = Integer.parseInt(tid);

        HashMap<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("User-Agent", "Mozilla/5.0 (iPhone; CPU iPhone OS 13_2_3 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/13.0.3 Mobile/15E148 Safari/604.1");

        JSONArray items = a(subcateId, page, headers);
        ArrayList<Vod> list = b(items, 0);

        return Result.string(list);
    }

    @Override
    public String detailContent(List<String> ids) {
        String id = ids.get(0);
        String[] parts = id.split("@@");

        Vod vod = new Vod();
        vod.setVodId(id);

        String title = parts.length > 1 ? parts[1] : "贝乐虎";
        vod.setVodName(title);

        if (parts.length > 2) {
            vod.setVodPic(parts[2]);
        }

        vod.setTypeName("少儿");
        vod.setVodPlayFrom("贝乐虎");
        vod.setVodPlayUrl("播放$" + parts[0]);

        return Result.string(vod);
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) {
        String url = id;
        if (url.contains("播放")) {
            String[] parts = url.split("播放");
            url = parts[0];
        }
        if (url.contains("$")) {
            int index = url.indexOf('$');
            url = url.substring(index + 1);
        }

        HashMap<String, String> headers = new HashMap<>();
        headers.put("User-Agent", "Mozilla/5.0 (iPhone; CPU iPhone OS 13_2_3 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/13.0.3 Mobile/15E148 Safari/604.1");

        return Result.get().url(url).header(headers).string();
    }

    @Override
    public String searchContent(String key, boolean quick) {
        return Result.string(new ArrayList<>());
    }
}