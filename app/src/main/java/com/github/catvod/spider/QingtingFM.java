package com.github.catvod.spider;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.net.OkHttp;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class QingtingFM extends Spider {

    private static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36";
    private static final String REFERER = "https://www.qtfm.cn";
    private static final String POST_URL = "https://webbff.qtfm.cn/www";
    private static final String DETAIL_URL = "https://webapi.qtfm.cn/api/pc/radio/";
    private static final String LIVE_URL = "https://lhttp-hw.qtfm.cn/live/";
    private static final String LIVE_SUFFIX = "/64k.mp3";

    public final Map<String, String> buildHeader() {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("User-Agent", UA);
        headers.put("Referer", REFERER);
        return headers;
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        String key = tid;
        if (extend != null) {
            Object cateId = extend.get("cateId");
            if (cateId != null) {
                key = (String) extend.get("cateId");
            }
        }
        String body = "{\"query\":\"{\\n    radioPage(cid:" + key + ", page:" + pg + "){\\n      contents\\n    }\\n  }\"}";
        try {
            String resp = OkHttp.post(POST_URL, body, buildHeader());
            JSONObject json = new JSONObject(resp);
            JSONArray items = json.getJSONObject("data").getJSONObject("radioPage").getJSONObject("contents").getJSONArray("items");
            ArrayList<Vod> list = new ArrayList<>();
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.getJSONObject(i);
                String pic = item.optString("imgUrl");
                if (!pic.contains("https")) {
                    pic = "https:" + pic;
                }
                list.add(new Vod(item.optString("id"), item.optString("title"), pic, item.optString("desc")));
            }
            int page = Integer.parseInt(pg);
            int limit = 12;
            int total = 9999;
            int nextPage = (list.size() < limit) ? page : page + 1;
            return Result.get().page(page, nextPage, limit, total).vod(list).string();
        } catch (Exception e) {
            return Result.string(new ArrayList<>());
        }
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        String id = ids.get(0);
        String url = DETAIL_URL + id;
        JSONObject json = new JSONObject(OkHttp.string(url, null, buildHeader()));
        JSONObject album = json.getJSONObject("album");
        Vod vod = new Vod(id, album.getString("title"), album.getString("cover"));
        vod.setVodContent(album.getString("description"));
        vod.setVodPlayFrom("蜻蜓FM");
        vod.setVodPlayUrl(album.getString("title") + "$" + album.getString("id"));
        return Result.string(vod);
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        ArrayList<Class> classes = new ArrayList<>();
        List<String> ids = Arrays.asList(
                "217", "99", "3", "5", "7", "83", "19", "31", "44", "59",
                "69", "85", "111", "129", "139", "151", "169", "187", "202", "239",
                "254", "257", "259", "281", "291", "316", "327", "351", "357", "308",
                "342", "433", "442", "429", "439", "432", "441", "430", "431", "440",
                "438", "435", "436", "434"
        );
        List<String> names = Arrays.asList(
                "广东", "浙江", "北京", "天津", "河北", "上海", "山西", "内蒙古", "辽宁", "吉林",
                "黑龙江", "江苏", "安徽", "福建", "江西", "山东", "河南", "湖北", "湖南", "广西",
                "海南", "重庆", "四川", "贵州", "云南", "陕西", "甘肃", "宁夏", "新疆", "西藏",
                "青海", "资讯", "音乐", "交通", "经济", "文艺", "都市", "体育", "双语", "综合",
                "生活", "旅游", "曲艺", "方言"
        );
        for (int i = 0; i < ids.size(); i++) {
            classes.add(new Class(ids.get(i), names.get(i)));
        }
        return Result.string(classes, new ArrayList<>());
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        String url = LIVE_URL + id + LIVE_SUFFIX;
        return Result.get().url(url).header(buildHeader()).string();
    }

    @Override
    public String searchContent(String key, boolean quick, String pg) throws Exception {
        String body = "{\"query\":\"{\\n        searchResultsPage(keyword:\\\"" + key + "\\\", page:" + pg + ", include:\\\"channel_live\\\" ) {\\n          tdk,\\n          searchData,\\n          numFound\\n        }\\n      }\"}";
        String resp = OkHttp.post(POST_URL, body, buildHeader());
        JSONObject json = new JSONObject(resp);
        JSONArray items = json.getJSONObject("data").getJSONObject("searchResultsPage").getJSONArray("searchData");
        ArrayList<Vod> list = new ArrayList<>();
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.getJSONObject(i);
            list.add(new Vod(item.optString("id"), item.optString("title"), item.optString("cover"), item.optString("description")));
        }
        return Result.string(list);
    }
}
