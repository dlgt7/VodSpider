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

import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * JPYS视频源爬虫实现，支持搜索、分类、详情、播放功能。
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
            conn.disconnect();
            return valid;
        } catch (Exception e) {
            return false;
        }
    }

    private static String generateUUID() {
        return java.util.UUID.randomUUID().toString();
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
        ArrayList<Vod> videos = new ArrayList<>();
        ArrayList<Class> classes = new ArrayList<>();

        // 添加分类列表（电影、电视剧、动漫、综艺）
        classes.add(new Class(xorDecrypt(new byte[]{0}, new byte[]{49, -128, -107, -84, 109, 92, 73, 53}), "电影"));
        classes.add(new Class("2", "电视剧"));
        classes.add(new Class("4", "动漫"));
        classes.add(new Class("3", "综艺"));

        // 创建过滤器JSON
        JSONObject filtersJson = new JSONObject(FILTERS_JSON);

        // 构建请求参数
        String timestamp = String.valueOf(System.currentTimeMillis());
        String sign = md5(String.format("key=%s&t=%s", apiKey, timestamp));

        HashMap<String, String> headers = new HashMap<>();
        headers.put("sign", sign);
        headers.put("T", timestamp);
        headers.put("Deviceid", deviceId);

        // 发送请求并解析响应
        JSONArray hotSearchArray = new JSONObject(OkHttp.string(apiHost + "/api/mw-movie/anonymous/home/hotSearch", null, headers)).getJSONArray("data");

        for (int i = 0; i < hotSearchArray.length(); i++) {
            JSONObject item = hotSearchArray.getJSONObject(i);
            videos.add(new Vod(
                item.getString("vodId"),
                item.getString("vodName"),
                item.getString("vodPic"),
                item.getString("vodVersion")
            ));
        }

        return Result.string(classes, videos, filtersJson);
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        ArrayList<Vod> videos = new ArrayList<>();

        // 处理筛选参数
        String area = extend.containsKey("area") ? extend.get("area") : "";
        String year = extend.containsKey("year") ? extend.get("year") : "";
        if ("全部".equals(area)) area = "";
        if ("全部".equals(year)) year = "";

        // 构建签名
        String timestamp = String.valueOf(System.currentTimeMillis());
        String sign = md5(String.format("area=%s&pageNum=%s&type1=%s&year=%s&key=%s&t=%s",
            area, pg, tid, year, apiKey, timestamp));

        HashMap<String, String> headers = new HashMap<>();
        headers.put("sign", sign);
        headers.put("T", timestamp);
        headers.put("Deviceid", deviceId);

        String url = String.format(apiHost + "/api/mw-movie/anonymous/video/list?type1=%s&pageNum=%s&area=%s&year=%s",
            tid, pg, area, year);

        String response = OkHttp.string(url, null, headers);
        JSONObject json = new JSONObject(response);
        JSONArray list = json.getJSONObject("data").getJSONArray("list");

        for (int i = 0; i < list.length(); i++) {
            JSONObject item = list.getJSONObject(i);
            String vodName = item.getString("vodName");
            if (!vodNameCache.containsKey(vodName)) {
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
        if (ids == null || ids.isEmpty()) return Result.string(new Vod());

        String vodId = ids.get(0);
        ArrayList<Vod> videos = new ArrayList<>();

        // 获取详情信息
        String timestamp = String.valueOf(System.currentTimeMillis());
        String sign = md5(String.format("id=%s&key=%s&t=%s", vodId, apiKey, timestamp));

        HashMap<String, String> headers = new HashMap<>();
        headers.put("sign", sign);
        headers.put("T", timestamp);
        headers.put("Deviceid", deviceId);

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
        String[] parts = id.split("@");
        if (parts.length < 2) return Result.error("播放地址格式错误");

        String epId = parts[0];
        String epNid = parts[1];

        // 构建签名和请求头
        String timestamp = String.valueOf(System.currentTimeMillis());
        String sign = md5(String.format("id=%s&nid=%s&key=%s&t=%s", epId, epNid, apiKey, timestamp));

        HashMap<String, String> headers = new HashMap<>();
        headers.put("sign", sign);
        headers.put("T", timestamp);
        headers.put("Deviceid", deviceId);

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

        return result.toString();
    }

    @Override
    public String searchContent(String keyword, boolean quick) throws Exception {
        ArrayList<Vod> videos = new ArrayList<>();

        // 构建签名和请求头
        String timestamp = String.valueOf(System.currentTimeMillis());
        String sign = md5(String.format("keyword=%s&pageNum=1&pageSize=8&key=%s&t=%s",
            keyword, apiKey, timestamp));

        HashMap<String, String> headers = new HashMap<>();
        headers.put("sign", sign);
        headers.put("T", timestamp);
        headers.put("Deviceid", deviceId);

        String url = apiHost + "/api/mw-movie/anonymous/video/searchByWord?keyword=" +
            URLEncoder.encode(keyword) + "&pageNum=1&pageSize=8";
        String response = OkHttp.string(url, null, headers);

        JSONObject json = new JSONObject(response);
        JSONArray list = json.getJSONObject("data").getJSONObject("result").getJSONArray("list");

        for (int i = 0; i < list.length(); i++) {
            JSONObject item = list.getJSONObject(i);
            String vodClass = item.getString("vodClass");
            // 过滤伦理类内容
            if (!"伦理".equals(vodClass)) {
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

    /**
     * MD5签名方法（返回32位小写十六进制字符串，不足32位前面补0）
     */
    private String md5(String str) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(str.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder(new BigInteger(1, digest).toString(16));
            while (sb.length() < 32) {
                sb.insert(0, "0");
            }
            return sb.toString().toLowerCase();
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * XOR解密方法（循环使用密钥对数据进行解密）
     */
    private String xorDecrypt(byte[] data, byte[] key) {
        int dataLength = data.length;
        int keyLength = key.length;
        int dataIndex = 0;
        int keyIndex = 0;
        while (dataIndex < dataLength) {
            if (keyIndex >= keyLength) {
                keyIndex = 0;
            }
            data[dataIndex] = (byte) (data[dataIndex] ^ key[keyIndex]);
            dataIndex++;
            keyIndex++;
        }
        return new String(data);
    }
}