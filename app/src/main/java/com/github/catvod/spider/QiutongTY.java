package com.github.catvod.spider;

import android.text.TextUtils;
import android.util.Base64;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.net.OkHttp;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author CatVod
 * @date 2024-10-06
 */
public class QiutongTY extends Spider {

    private static String optString(JSONObject obj, String key) {
        if (obj != null && obj.has(key)) {
            String value = obj.optString(key);
            if (!"null".equals(value)) {
                return value;
            }
        }
        return "";
    }

    @Override
    public String homeContent(boolean filter) {
        List<Class> classes = new ArrayList<>();
        classes.add(new Class("-1", "全部"));
        classes.add(new Class("1", "足球"));
        classes.add(new Class("2", "篮球"));
        return Result.string(classes, new ArrayList<>());
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) {
        String cateId = "-1".equals(tid) ? "" : tid;
        String url = "https://aapi2.xbncs.com/api/room/page?roomType=&navId=" +
            cateId + "&roomId=&word=&page=" + pg + "&pageSize=30&channelId=3&platform=1";

        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36");

        try {
            String response = OkHttp.string(url, null, headers);
            JSONObject object = new JSONObject(response);
            JSONObject data = object.optJSONObject("data");

            if (data == null) {
                return Result.get()
                    .page(Integer.parseInt(pg), 9999, 30, 999999)
                    .vod(new ArrayList<>())
                    .string();
            }

            JSONArray list = data.optJSONArray("list");
            List<Vod> videos = new ArrayList<>();

            if (list != null) {
                for (int i = 0; i < list.length(); i++) {
                    JSONObject item = list.getJSONObject(i);
                    String roomId = optString(item, "roomId");
                    String title = optString(item, "title");
                    String cover = optString(item, "cover");
                    String navName = optString(item, "navName");
                    videos.add(new Vod(roomId, title, cover, navName));
                }
            }

            return Result.get()
                .page(Integer.parseInt(pg), 9999, 30, 999999)
                .vod(videos)
                .string();
        } catch (Exception e) {
            return Result.get()
                .page(Integer.parseInt(pg), 9999, 30, 999999)
                .vod(new ArrayList<>())
                .string();
        }
    }

    @Override
    public String detailContent(List<String> ids) {
        String url = String.format(
            "https://aapi2.xbncs.com/api/room/info?roomId=%s&channelId=3&platform=1",
            ids.get(0)
        );

        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36");

        try {
            String response = OkHttp.string(url, null, headers);
            JSONObject object = new JSONObject(response);
            JSONObject data = object.optJSONObject("data");

            if (data == null) {
                return Result.error("暂无播放数据");
            }

            String vodId = ids.get(0);
            String title = optString(data, "title");
            String cover = optString(data, "cover");
            Vod vod = new Vod(vodId, title, cover);

            String description = optString(data, "description");
            vod.setVodContent(description);

            String nickName = optString(data, "nickName");
            vod.setTypeName(nickName);

            List<String> playUrls = new ArrayList<>();
            String pushUrl = optString(data, "pushUrl");
            String pullUrl = optString(data, "pullUrl");

            if (!TextUtils.isEmpty(pushUrl)) {
                String encodedPushUrl = Base64.encodeToString(
                    pushUrl.getBytes(StandardCharsets.UTF_8),
                    Base64.NO_WRAP
                );
                playUrls.add("flv$" + encodedPushUrl);
            }

            if (!TextUtils.isEmpty(pullUrl)) {
                String encodedPullUrl = Base64.encodeToString(
                    pullUrl.getBytes(StandardCharsets.UTF_8),
                    Base64.NO_WRAP
                );
                playUrls.add("m3u8$" + encodedPullUrl);
            }

            if (playUrls.isEmpty()) {
                return Result.error("暂无播放数据");
            }

            vod.setVodPlayFrom("球通");
            vod.setVodPlayUrl(TextUtils.join("#", playUrls));

            return Result.string(vod);
        } catch (Exception e) {
            return Result.error("暂无播放数据");
        }
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) {
        try {
            String decodedUrl = new String(
                Base64.decode(id, Base64.NO_WRAP),
                StandardCharsets.UTF_8
            );
            return Result.get()
                .url(decodedUrl)
                .parse(0)
                .string();
        } catch (Exception e) {
            return Result.error("解析失败");
        }
    }
}