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

import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * JPYS 视频源爬虫实现。
 * 支持电影、电视剧、综艺、动漫的分类浏览、详情解析和播放地址获取。
 */
public class Jpys extends Spider {

    private static final String API_KEY = "cb808529bae6b6be45ecfab29a4889bc";
    private static final String DEFAULT_HOST = "https://www.hkybqufgh.com";
    private static final String CHROME_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/117.0.0.0 Safari/537.36";
    private static final String FILTERS_JSON = "{\"1\":[{\"name\":\"地区\",\"key\":\"area\",\"value\":[{\"n\":\"全部\",\"v\":\"\"},{\"n\":\"中国大陆\",\"v\":\"中国大陆\"},{\"n\":\"中国香港\",\"v\":\"中国香港\"},{\"n\":\"中国台湾\",\"v\":\"中国台湾\"},{\"n\":\"美国\",\"v\":\"美国\"},{\"n\":\"日本\",\"v\":\"日本\"},{\"n\":\"韩国\",\"v\":\"韩国\"},{\"n\":\"泰国\",\"v\":\"泰国\"},{\"n\":\"印度\",\"v\":\"印度\"},{\"n\":\"其他\",\"v\":\"其他\"}]},{\"name\":\"年份\",\"key\":\"year\",\"value\":[{\"n\":\"全部\",\"v\":\"\"},{\"n\":\"2026\",\"v\":\"2026\"},{\"n\":\"2025\",\"v\":\"2025\"},{\"n\":\"2024\",\"v\":\"2024\"},{\"n\":\"2023\",\"v\":\"2023\"},{\"n\":\"2022\",\"v\":\"2022\"},{\"n\":\"2021\",\"v\":\"2021\"},{\"n\":\"2020\",\"v\":\"2020\"},{\"n\":\"2019\",\"v\":\"2019\"},{\"n\":\"2018\",\"v\":\"2018\"},{\"n\":\"2017\",\"v\":\"2017\"},{\"n\":\"2016\",\"v\":\"2016\"},{\"n\":\"2015\",\"v\":\"2015\"},{\"n\":\"2014\",\"v\":\"2014\"},{\"n\":\"2013\",\"v\":\"2013\"},{\"n\":\"2012\",\"v\":\"2012\"},{\"n\":\"2011\",\"v\":\"2011\"},{\"n\":\"2010\",\"v\":\"2010\"},{\"n\":\"2009~2000\",\"v\":\"2009~2000\"},{\"n\":\"90年代\",\"v\":\"90年代\"},{\"n\":\"80年代\",\"v\":\"80年代\"}]}],\"2\":[{\"name\":\"地区\",\"key\":\"area\",\"value\":[{\"n\":\"全部\",\"v\":\"\"},{\"n\":\"中国大陆\",\"v\":\"中国大陆\"},{\"n\":\"中国香港\",\"v\":\"中国香港\"},{\"n\":\"中国台湾\",\"v\":\"中国台湾\"},{\"n\":\"美国\",\"v\":\"美国\"},{\"n\":\"日本\",\"v\":\"日本\"},{\"n\":\"韩国\",\"v\":\"韩国\"},{\"n\":\"泰国\",\"v\":\"泰国\"},{\"n\":\"印度\",\"v\":\"印度\"},{\"n\":\"其他\",\"v\":\"其他\"}]},{\"name\":\"年份\",\"key\":\"year\",\"value\":[{\"n\":\"全部\",\"v\":\"\"},{\"n\":\"2026\",\"v\":\"2026\"},{\"n\":\"2025\",\"v\":\"2025\"},{\"n\":\"2024\",\"v\":\"2024\"},{\"n\":\"2023\",\"v\":\"2023\"},{\"n\":\"2022\",\"v\":\"2022\"},{\"n\":\"2021\",\"v\":\"2021\"},{\"n\":\"2020\",\"v\":\"2020\"},{\"n\":\"2019\",\"v\":\"2019\"},{\"n\":\"2018\",\"v\":\"2018\"},{\"n\":\"2017\",\"v\":\"2017\"},{\"n\":\"2016\",\"v\":\"2016\"},{\"n\":\"2015\",\"v\":\"2015\"},{\"n\":\"2014\",\"v\":\"2014\"},{\"n\":\"2013\",\"v\":\"2013\"},{\"n\":\"2012\",\"v\":\"2012\"},{\"n\":\"2011\",\"v\":\"2011\"},{\"n\":\"2010\",\"v\":\"2010\"},{\"n\":\"2009~2000\",\"v\":\"2009~2000\"},{\"n\":\"90年代\",\"v\":\"90年代\"},{\"n\":\"80年代\",\"v\":\"80年代\"}]}],\"3\":[{\"name\":\"地区\",\"key\":\"area\",\"value\":[{\"n\":\"全部\",\"v\":\"\"},{\"n\":\"中国大陆\",\"v\":\"中国大陆\"},{\"n\":\"中国香港\",\"v\":\"中国香港\"},{\"n\":\"中国台湾\",\"v\":\"中国台湾\"},{\"n\":\"美国\",\"v\":\"美国\"},{\"n\":\"日本\",\"v\":\"日本\"},{\"n\":\"韩国\",\"v\":\"韩国\"},{\"n\":\"泰国\",\"v\":\"泰国\"},{\"n\":\"印度\",\"v\":\"印度\"},{\"n\":\"其他\",\"v\":\"其他\"}]},{\"name\":\"年份\",\"key\":\"year\",\"value\":[{\"n\":\"全部\",\"v\":\"\"},{\"n\":\"2026\",\"v\":\"2026\"},{\"n\":\"2025\",\"v\":\"2025\"},{\"n\":\"2024\",\"v\":\"2024\"},{\"n\":\"2023\",\"v\":\"2023\"},{\"n\":\"2022\",\"v\":\"2022\"},{\"n\":\"2021\",\"v\":\"2021\"},{\"n\":\"2020\",\"v\":\"2020\"},{\"n\":\"更早\",\"v\":\"更早\"}]}],\"4\":[{\"name\":\"地区\",\"key\":\"area\",\"value\":[{\"n\":\"全部\",\"v\":\"\"},{\"n\":\"中国大陆\",\"v\":\"中国大陆\"},{\"n\":\"美国\",\"v\":\"美国\"},{\"n\":\"日本\",\"v\":\"日本\"},{\"n\":\"其他\",\"v\":\"其他\"}]},{\"name\":\"年份\",\"key\":\"year\",\"value\":[{\"n\":\"全部\",\"v\":\"\"},{\"n\":\"2026\",\"v\":\"2026\"},{\"n\":\"2025\",\"v\":\"2025\"},{\"n\":\"2024\",\"v\":\"2024\"},{\"n\":\"2023\",\"v\":\"2023\"},{\"n\":\"2022\",\"v\":\"2022\"},{\"n\":\"2021\",\"v\":\"2021\"},{\"n\":\"2020\",\"v\":\"2020\"},{\"n\":\"2019\",\"v\":\"2019\"},{\"n\":\"2018\",\"v\":\"2018\"},{\"n\":\"2017\",\"v\":\"2017\"},{\"n\":\"2016\",\"v\":\"2016\"},{\"n\":\"2015\",\"v\":\"2015\"},{\"n\":\"2014\",\"v\":\"2014\"},{\"n\":\"2013\",\"v\":\"2013\"},{\"n\":\"2012\",\"v\":\"2012\"},{\"n\":\"2011\",\"v\":\"2011\"},{\"n\":\"2010\",\"v\":\"2010\"},{\"n\":\"2009~2000\",\"v\":\"2009~2000\"},{\"n\":\"90年代\",\"v\":\"90年代\"},{\"n\":\"80年代\",\"v\":\"80年代\"},{\"n\":\"更早\",\"v\":\"更早\"}]}]}";

    private static final HashMap<String, Boolean> vodNameCache = new HashMap<>();

    private String apiHost = DEFAULT_HOST;
    private String deviceId = "";
    private String apiKey = API_KEY;

    public Jpys() {
        this.deviceId = generateUUID();
    }

    private boolean checkDomainValid(String url) {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setInstanceFollowRedirects(true);
            conn.setRequestMethod("HEAD");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            int code = conn.getResponseCode();
            boolean valid = code >= 200 && code <= 399;
            try {
                conn.disconnect();
                return valid;
            } catch (Exception e) {
                return valid;
            }
        } catch (Exception e) {
            return false;
        }
    }

    private static String generateUUID() {
        return java.util.UUID.randomUUID().toString();
    }

    private static String sha1(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] digest = md.digest(input.getBytes());
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

    private HashMap<String, String> buildHeaders(String sign, String timestamp) {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("sign", sign);
        headers.put("T", timestamp);
        headers.put("Deviceid", deviceId);
        return headers;
    }

    private String buildSign(String params) {
        // 注意: 原 merge/C1994z.m4721a() 方法行为未知,这里使用简化实现
        // 实际可能需要对参数进行排序、拼接等处理
        return sha1(params);
    }

    private String xorDecrypt(byte[] key, byte[] data) {
        // XOR 解密实现 (对应原 C1820a.m4153b())
        byte[] result = new byte[data.length];
        for (int i = 0; i < data.length; i++) {
            result[i] = (byte) (data[i] ^ key[i % key.length]);
        }
        return new String(result);
    }

    @Override
    public void init(Context context, String extend) throws Exception {
        if (TextUtils.isEmpty(extend)) return;
        String[] domains = extend.split(",");
        for (String domain : domains) {
            if (checkDomainValid(domain)) {
                this.apiHost = domain.trim();
                break;
            }
        }
        this.apiKey = API_KEY;
        this.deviceId = generateUUID();
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        ArrayList<Class> classes = new ArrayList<>();
        ArrayList<Vod> videos = new ArrayList<>();

        // 添加分类 (第一个分类ID通过XOR解密生成)
        byte[] xorKey = new byte[]{0};
        byte[] encryptedId = new byte[]{49, -128, -107, -84, 109, 92, 73, 53};
        String decryptedId = xorDecrypt(xorKey, encryptedId);
        classes.add(new Class(decryptedId, "电影"));
        classes.add(new Class("2", "电视剧"));
        classes.add(new Class("4", "动漫"));
        classes.add(new Class("3", "综艺"));

        // 获取热门搜索推荐
        String timestamp = String.valueOf(System.currentTimeMillis());
        String signParams = String.format("key=%s&t=%s", apiKey, timestamp);
        String sign = buildSign(signParams);
        HashMap<String, String> headers = buildHeaders(sign, timestamp);

        String url = apiHost + "/api/mw-movie/anonymous/home/hotSearch";
        String response = OkHttp.string(url, null, headers);
        JSONObject json = new JSONObject(response);
        JSONArray hotList = json.getJSONArray("data");

        for (int i = 0; i < hotList.length(); i++) {
            JSONObject item = hotList.getJSONObject(i);
            videos.add(new Vod(
                item.getString("vodId"),
                item.getString("vodName"),
                item.getString("vodPic"),
                item.getString("vodVersion")
            ));
        }

        JSONObject filters = new JSONObject(FILTERS_JSON);
        return Result.string(classes, videos, filters);
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        ArrayList<Vod> videos = new ArrayList<>();

        // 处理筛选参数
        String by = extend.get("by");
        if (by == null || by.equals("全部")) by = "";
        String year = extend.get("year");
        if (year == null || year.equals("全部")) year = "";
        String area = extend.get("area");
        if (area == null || area.equals("全部")) area = "";

        // 构建签名
        String timestamp = String.valueOf(System.currentTimeMillis());
        String signParams = String.format("area=%s&pageNum=%s&type1=%s&year=%s&key=%s&t=%s",
            area, pg, tid, year, apiKey, timestamp);
        // 注意: 原 C1994z.m4721a() 可能需要对参数排序,这里简化处理
        String sign = buildSign(signParams);

        HashMap<String, String> headers = buildHeaders(sign, timestamp);
        String url = String.format(apiHost + "/api/mw-movie/anonymous/video/list?type1=%s&pageNum=%s&area=%s&year=%s",
            tid, pg, area, year);

        String response = OkHttp.string(url, null, headers);
        JSONObject json = new JSONObject(response);
        JSONArray list = json.getJSONObject("data").getJSONArray("list");

        for (int i = 0; i < list.length(); i++) {
            JSONObject item = list.getJSONObject(i);
            String vodName = item.getString("vodName");
            // 去重处理
            if (vodNameCache.isEmpty() || !vodNameCache.containsKey(vodName)) {
                videos.add(new Vod(
                    item.getString("vodId"),
                    vodName,
                    item.getString("vodPic"),
                    item.getString("vodVersion")
                ));
            }
        }

        return Result.string(videos);
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        // 注意: 原 detailContent 实现过于复杂,这里基于API模式推断实现
        if (ids == null || ids.isEmpty()) return Result.string(new Vod());

        String vodId = ids.get(0);
        ArrayList<Vod> videos = new ArrayList<>();

        // 获取详情信息
        String timestamp = String.valueOf(System.currentTimeMillis());
        String signParams = String.format("id=%s&key=%s&t=%s", vodId, apiKey, timestamp);
        String sign = buildSign(signParams);
        HashMap<String, String> headers = buildHeaders(sign, timestamp);

        String url = apiHost + "/api/mw-movie/anonymous/video/detail?id=" + vodId;
        String response = OkHttp.string(url, null, headers);

        JSONObject json = new JSONObject(response);
        JSONObject data = json.getJSONObject("data");

        Vod vod = new Vod();
        vod.setVodId(vodId);
        vod.setVodName(data.optString("vodName"));
        vod.setVodPic(data.optString("vodPic"));
        vod.setVodYear(data.optString("vodYear"));
        vod.setVodArea(data.optString("vodArea"));
        vod.setVodActor(data.optString("vodActor"));
        vod.setVodDirector(data.optString("vodDirector"));
        vod.setVodContent(data.optString("vodContent"));

        // 解析播放列表
        JSONArray episodes = data.optJSONArray("episodes");
        if (episodes != null) {
            StringBuilder playFrom = new StringBuilder();
            StringBuilder playUrl = new StringBuilder();

            for (int i = 0; i < episodes.length(); i++) {
                JSONObject ep = episodes.getJSONObject(i);
                String epName = ep.optString("name");
                String epId = ep.optString("id");
                String epNid = ep.optString("nid");

                if (i > 0) {
                    playFrom.append("#");
                    playUrl.append("#");
                }
                playFrom.append(epName);
                playUrl.append(epName + "$" + epId + "@" + epNid);
            }

            vod.setVodPlayFrom(playFrom.toString());
            vod.setVodPlayUrl(playUrl.toString());
        }

        videos.add(vod);
        return Result.string(videos);
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        try {
            String[] parts = id.split("@");
            if (parts.length < 2) return Result.error("播放地址格式错误");

            String epId = parts[0];
            String epNid = parts[1];

            String timestamp = String.valueOf(System.currentTimeMillis());
            String signParams = String.format("id=%s&nid=%s&key=%s&t=%s", epId, epNid, apiKey, timestamp);
            String sign = buildSign(signParams);
            HashMap<String, String> headers = buildHeaders(sign, timestamp);

            String url = apiHost + "/api/mw-movie/anonymous/v2/video/episode/url?id=" + epId + "&nid=" + epNid;
            String response = OkHttp.string(url, null, headers);

            JSONObject json = new JSONObject(response);
            String playUrl = json.getJSONObject("data").getJSONArray("list").getJSONObject(0).getString("url");

            // 构建请求头
            JSONObject headerJson = new JSONObject();
            headerJson.put("User-Agent", CHROME_UA);
            headerJson.put("Origin", apiHost);
            headerJson.put("Referer", apiHost);

            JSONObject result = new JSONObject();
            result.put("url", playUrl);
            result.put("parse", 0);
            result.put("playUrl", "");
            result.put("header", headerJson.toString());

            // 注意: 原 C2246a.addDanmaku() 弹幕处理功能,这里简化实现
            return result.toString();
        } catch (Exception e) {
            return Result.error("播放链接解析失败");
        }
    }

    @Override
    public String searchContent(String keyword, boolean quick) throws Exception {
        ArrayList<Vod> videos = new ArrayList<>();

        String timestamp = String.valueOf(System.currentTimeMillis());
        String signParams = String.format("keyword=%s&pageNum=1&pageSize=8&key=%s&t=%s",
            keyword, apiKey, timestamp);
        String sign = buildSign(signParams);
        HashMap<String, String> headers = buildHeaders(sign, timestamp);

        String url = apiHost + "/api/mw-movie/anonymous/video/searchByWord?keyword=" +
            URLEncoder.encode(keyword) + "&pageNum=1&pageSize=8";
        String response = OkHttp.string(url, null, headers);

        JSONObject json = new JSONObject(response);
        JSONArray list = json.getJSONObject("data").getJSONObject("result").getJSONArray("list");

        for (int i = 0; i < list.length(); i++) {
            JSONObject item = list.getJSONObject(i);
            String vodClass = item.getString("vodClass");
            // 过滤伦理类内容
            if (!vodClass.equals("伦理")) {
                videos.add(new Vod(
                    item.getString("vodId"),
                    item.getString("vodName"),
                    item.getString("vodPic"),
                    item.getString("vodRemarks")
                ));
            }
        }

        return Result.string(videos);
    }
}