package com.github.catvod.spider;

import android.text.TextUtils;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Filter;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.net.OkHttp;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class WexTangDou extends Spider {

    private static final String API_HOST = "https://aa.tangdou.com:12308/tv_api.php";
    private static final String SHARE_MAIN_URL = "https://api-h5.tangdou.com/sample/share/main?vid=";
    private static final String SHARE_RECOMMEND_URL = "https://api-h5.tangdou.com/sample/share/recommend?page_num=";
    private static final String PIC_HOST = "http://bimg.tangdou.com";
    private static final String PIC_SUFFIX = "!s640";
    private static final String REFERER = "https://www.tangdoucdn.com/";
    private static final String UA = "Mozilla/5.0 (Linux; Android 7.1.2; ASUS_I003DD) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Mobile Safari/537.36";
    private static final String UUID = "62689604d8ca446c83b4540d6119c8b2";
    private static final String VERSION = "7.1.2";

    private static final HashMap<String, List<Vod>> CACHE = new HashMap<>();

    private static final String[][] CATEGORIES = new String[][]{
            {"1471610743", "弹跳舞"}, {"1471610739", "步子舞"}, {"1471610742", "双人舞"}, {"1471610738", "健身舞"},
            {"1471610748", "流行舞"}, {"1471610757", "鬼步舞"}, {"1471610758", "水兵舞"}, {"1471610746", "民族风"}
    };

    public static void a(JSONObject data, String key, ArrayList<String> cdnList, ArrayList<String> urlList) {
        if (data == null) return;
        JSONArray arr = data.optJSONArray(key);
        if (arr == null) return;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject item = arr.optJSONObject(i);
            if (item == null) continue;
            String url = item.optString("url");
            if (TextUtils.isEmpty(url)) continue;
            String cdn = item.optString("cdn_source", key);
            String define = item.optString("define");
            if ("3".equals(define)) cdn = cdn + "1080";
            cdnList.add(cdn);
            urlList.add(url);
        }
    }

    public static String f(JSONObject data, String key) {
        if (data == null) return "";
        String[] keys = new String[]{key, "hd", "sd"};
        for (String k : keys) {
            JSONArray arr = data.optJSONArray(k);
            if (arr == null) continue;
            for (int j = 0; j < arr.length(); j++) {
                JSONObject item = arr.optJSONObject(j);
                String url = item.optString("url");
                if (!TextUtils.isEmpty(url)) return url;
            }
        }
        return "";
    }

    public final Map<String, String> b() {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("User-Agent", UA);
        headers.put("Referer", REFERER);
        return headers;
    }

    public final ArrayList<Vod> c(JSONArray arr) {
        ArrayList<Vod> list = new ArrayList<>();
        if (arr == null) return list;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject item = arr.optJSONObject(i);
            if (item == null) continue;
            String vid = String.valueOf(item.optLong("vid", 0L));
            String vodId = vid;
            if ("0".equals(vid)) vodId = item.optString("id");
            list.add(new Vod(vodId, item.optString("title"), e(item)));
        }
        return list;
    }

    public final ArrayList<Vod> d(JSONArray arr) {
        ArrayList<Vod> list = new ArrayList<>();
        if (arr == null) return list;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject item = arr.optJSONObject(i);
            if (item == null) continue;
            String vid = item.optString("vid");
            String id = item.optString("id", vid);
            if (TextUtils.isEmpty(id)) continue;
            String name = item.optString("name");
            list.add(new Vod(id, item.optString("title", name), e(item)));
        }
        return list;
    }

    public final String e(JSONObject item) {
        String[] keys = new String[]{"big_pic", "pic", "cover", "thumbnail", "image"};
        for (String key : keys) {
            String img = item.optString(key);
            if (TextUtils.isEmpty(img)) continue;
            if (img.startsWith("http")) {
                if (img.contains("!")) return img;
                return img.concat(PIC_SUFFIX);
            } else {
                return PIC_HOST + img + PIC_SUFFIX;
            }
        }
        return "";
    }

    public final JSONObject g(String ac, HashMap<String, String> params) {
        try {
            HashMap<String, String> all = new HashMap<>();
            all.put("mod", "tv");
            all.put("ac", ac);
            all.put("uuid", UUID);
            all.put("version", VERSION);
            all.put("client", "3");
            all.put("channel_id", "16");
            all.put("time", String.valueOf(System.currentTimeMillis()));
            all.putAll(params);
            StringBuilder sb = new StringBuilder(API_HOST);
            sb.append("?");
            for (Map.Entry<String, String> entry : all.entrySet()) {
                if (sb.charAt(sb.length() - 1) != '?') sb.append('&');
                sb.append(entry.getKey());
                sb.append('=');
                sb.append(entry.getValue());
            }
            return new JSONObject(OkHttp.string(sb.toString(), null, b()));
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        ArrayList<Class> classes = new ArrayList<>();
        classes.add(new Class("1471610743", "广场舞+舞蹈"));
        classes.add(new Class("11", "名师"));
        classes.add(new Class("2", "健身"));
        classes.add(new Class("20", "乐艺厅"));
        classes.add(new Class("18", "热榜"));
        classes.add(new Class("5", "女神必备"));
        LinkedHashMap<String, List<Filter>> filtersMap = new LinkedHashMap<>();
        ArrayList<Filter.Value> values = new ArrayList<>();
        for (String[] pair : CATEGORIES) {
            values.add(new Filter.Value(pair[1], pair[0]));
        }
        ArrayList<Filter> filters = new ArrayList<>();
        filters.add(new Filter("cateId", "类型", values));
        filtersMap.put("1471610743", filters);
        return Result.string(classes, filtersMap);
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        String key = tid;
        if (extend != null) {
            String cateId = (String) extend.get("cateId");
            if (!TextUtils.isEmpty(cateId)) key = cateId;
        }
        int page = Integer.parseInt(pg);
        if (page > 1) {
            List<Vod> cached = CACHE.get(key);
            if (cached == null) {
                return Result.get().page(page, page, 0, 0).vod(new ArrayList<>()).string();
            } else {
                return Result.get().page(page, page, 0, cached.size()).vod(new ArrayList<>()).string();
            }
        }
        ArrayList<Vod> items = new ArrayList<>();
        HashMap<String, String> params = new HashMap<>();
        params.put("pid", key);
        params.put("page", "1");
        JSONObject indexJson = g("index", params);
        JSONArray datas = indexJson.optJSONArray("datas");
        ArrayList<Vod> firstBatch = d(datas);
        items.addAll(firstBatch);
        for (Vod item : firstBatch) {
            for (int pageNum = 1; pageNum <= 3; pageNum++) {
                try {
                    String url = SHARE_RECOMMEND_URL + pageNum + "&vid=" + item.getVodId();
                    JSONObject recJson = new JSONObject(OkHttp.string(url, null, b()));
                    JSONArray recArr = recJson.optJSONArray("data");
                    ArrayList<Vod> recList = c(recArr);
                    for (Vod rec : recList) {
                        boolean found = false;
                        for (Vod existing : items) {
                            if (existing.getVodId().equals(rec.getVodId())) {
                                found = true;
                                break;
                            }
                        }
                        if (!found) items.add(rec);
                    }
                } catch (Exception e) {
                }
            }
            if (items.size() >= 100) break;
        }
        CACHE.put(key, items);
        return Result.get().page(1, 1, items.size(), items.size()).vod(items).string();
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        String vid = ids.get(0);
        JSONObject json = new JSONObject(OkHttp.string(SHARE_MAIN_URL + vid, null, b()));
        JSONObject data = json.optJSONObject("data");
        String name = data != null ? data.optString("title", "") : vid;
        String content = data != null ? data.optString("title", "") : "";
        String pic = data != null ? e(data) : "";
        Vod vod = new Vod(vid, name, pic);
        vod.setVodContent(content);
        ArrayList<String> playFromList = new ArrayList<>();
        ArrayList<String> playUrlList = new ArrayList<>();
        HashMap<String, String> params = new HashMap<>();
        params.put("vid", vid);
        JSONObject mp4Json = g("mp4", params);
        JSONObject datas = mp4Json.optJSONObject("datas");
        a(datas, "hd", playFromList, playUrlList);
        a(datas, "sd", playFromList, playUrlList);
        if (playFromList.isEmpty() && data != null) {
            String playUrl = data.optString("play_url");
            playUrl = data.optString("video_url", playUrl);
            if (!TextUtils.isEmpty(playUrl)) {
                playFromList.add("分享");
                playUrlList.add(playUrl);
            }
        }
        vod.setVodPlayFrom(TextUtils.join("$$$", playFromList));
        vod.setVodPlayUrl(TextUtils.join("$$$", playUrlList));
        return Result.string(vod);
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("User-Agent", UA);
        if (id.startsWith("http")) {
            return Result.get().url(id).parse(0).header(headers).string();
        }
        HashMap<String, String> params = new HashMap<>();
        params.put("vid", id);
        JSONObject mp4Json = g("mp4", params);
        JSONObject datas = mp4Json.optJSONObject("datas");
        String url = f(datas, flag);
        if (TextUtils.isEmpty(url)) url = f(datas, "hd");
        return Result.get().url(url).parse(0).header(headers).string();
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        ArrayList<Vod> results = new ArrayList<>();
        HashMap<String, String> params = new HashMap<>();
        params.put("pid", "1471610743");
        params.put("page", "1");
        JSONObject indexJson = g("index", params);
        JSONArray datas = indexJson.optJSONArray("datas");
        ArrayList<Vod> firstBatch = d(datas);
        results.addAll(firstBatch);
        ArrayList<Vod> snapshot = new ArrayList<>(results);
        for (Vod item : snapshot) {
            for (int pageNum = 1; pageNum <= 3; pageNum++) {
                try {
                    String url = SHARE_RECOMMEND_URL + pageNum + "&vid=" + item.getVodId();
                    JSONObject recJson = new JSONObject(OkHttp.string(url, null, b()));
                    JSONArray recArr = recJson.optJSONArray("data");
                    ArrayList<Vod> recList = c(recArr);
                    for (Vod rec : recList) {
                        boolean found = false;
                        for (Vod existing : results) {
                            if (existing.getVodId().equals(rec.getVodId())) {
                                found = true;
                                break;
                            }
                        }
                        if (!found) results.add(rec);
                    }
                } catch (Exception e) {
                    break;
                }
            }
        }
        ArrayList<Vod> vodList = new ArrayList<>();
        for (Vod m : results) {
            if (m.getVodName() == null || !m.getVodName().contains(key)) continue;
            boolean found = false;
            for (Vod existing : vodList) {
                if (m.getVodId().equals(existing.getVodId())) {
                    found = true;
                    break;
                }
            }
            if (!found) vodList.add(m);
            if (vodList.size() >= 40) break;
        }
        return Result.string(vodList);
    }
}
