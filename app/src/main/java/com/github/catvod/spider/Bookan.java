package com.github.catvod.spider;

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
import java.util.Map;

public class Bookan extends Spider {

    private static final String UA = "Mozilla/5.0 (Linux; Android 12; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36";
    private static final String LIST_URL = "https://api.bookan.com.cn/voice/book/list?instance_id=25304&page=";
    private static final String UNITS_URL = "https://api.bookan.com.cn/voice/album/units?album_id=";
    private static final String SEARCH_URL = "https://es.bookan.com.cn/api/v3/voice/book?instanceId=25304&keyword=";
    private static final String PLAY_FROM = "博看听书";

    private static final String[] CATEGORIES = new String[]{
            "少年读物", "儿童文学", "国学经典", "文艺少年", "育儿心经",
            "心理哲学", "青春励志", "历史小说", "故事会", "音乐戏剧", "相声评书"
    };
    private static final String[] CATEGORY_IDS = new String[]{
            "1305", "1304", "1320", "1306", "1309",
            "1310", "1307", "1312", "1303", "1317", "1319"
    };

    public final Map<String, String> getHeaders() {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("User-Agent", UA);
        return headers;
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        ArrayList<Class> classes = new ArrayList<>();
        for (int i = 0; i < 11; i++) {
            classes.add(new Class(CATEGORY_IDS[i], CATEGORIES[i]));
        }
        ArrayList<Vod> list = new ArrayList<>();
        return Result.string(classes, list);
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        if (TextUtils.isEmpty(pg)) pg = "1";
        try {
            String url = LIST_URL + pg + "&category_id=" + tid + "&num=24";
            JSONObject response = new JSONObject(OkHttp.string(url, null, getHeaders()));
            if (response.optInt("code", -1) != 0) {
                return Result.string(new ArrayList<>());
            }
            JSONObject data = response.getJSONObject("data");
            JSONArray list = data.getJSONArray("list");
            ArrayList<Vod> vodList = new ArrayList<>();
            for (int i = 0; i < list.length(); i++) {
                JSONObject item = list.getJSONObject(i);
                String author = item.optJSONObject("extra").optString("author");
                String id = String.valueOf(item.optInt("id"));
                String name = item.optString("name");
                String cover = item.optString("cover");
                vodList.add(new Vod(id, name, cover, author));
            }
            int page = Integer.parseInt(pg);
            int lastPage = data.optInt("last_page", page);
            return Result.get().page(page, lastPage, 24, lastPage * 24).vod(vodList).string();
        } catch (Exception e) {
            return Result.string(new ArrayList<>());
        }
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        String id = ids.get(0);
        StringBuilder playUrls = new StringBuilder();
        String vodName = id;
        String vodRemarks = "";
        int page = 1;
        int lastPage = 1;
        while (true) {
            String url = UNITS_URL + id + "&page=" + page + "&num=200&order=1";
            JSONObject response = new JSONObject(OkHttp.string(url, null, getHeaders()));
            if (response.optInt("code", -1) != 0) break;
            JSONObject data = response.getJSONObject("data");
            lastPage = data.optInt("last_page", 1);
            JSONArray list = data.getJSONArray("list");
            if (page == 1 && list.length() > 0) {
                vodRemarks = "更新于 " + list.getJSONObject(0).optString("updated_at");
            }
            for (int i = 0; i < list.length(); i++) {
                JSONObject item = list.getJSONObject(i);
                if (playUrls.length() > 0) playUrls.append("#");
                playUrls.append(item.optString("title")).append("$").append(item.optString("file"));
            }
            page++;
            if (page > lastPage) break;
        }
        Vod vod = new Vod(id, vodName, "", vodRemarks);
        vod.setVodPlayFrom(PLAY_FROM);
        vod.setVodPlayUrl(playUrls.toString());
        return Result.string(vod);
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        if (TextUtils.isEmpty(id)) {
            return Result.get().url("").parse(0).string();
        }
        return Result.get().url(id).header(getHeaders()).parse(0).string();
    }

    @Override
    public String searchContent(String key, boolean quick, String pg) throws Exception {
        if (TextUtils.isEmpty(key)) {
            return Result.string(new ArrayList<>());
        }
        if (TextUtils.isEmpty(pg)) pg = "1";
        String url = SEARCH_URL + URLEncoder.encode(key.trim(), "UTF-8") + "&pageNum=" + pg + "&limitNum=20";
        JSONObject response = new JSONObject(OkHttp.string(url, null, getHeaders()));
        if (response.optInt("code", -1) != 0) {
            return Result.string(new ArrayList<>());
        }
        JSONObject data = response.getJSONObject("data");
        JSONArray list = data.getJSONArray("list");
        ArrayList<Vod> vodList = new ArrayList<>();
        for (int i = 0; i < list.length(); i++) {
            JSONObject item = list.getJSONObject(i);
            String id = String.valueOf(item.optInt("id"));
            String name = item.optString("name");
            String cover = item.optString("cover");
            String author = item.optJSONObject("extra").optString("author");
            vodList.add(new Vod(id, name, cover, author));
        }
        return Result.string(vodList);
    }
}
