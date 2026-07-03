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

import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * WenCai Spider - 文才影视 JSON API 爬虫
 * <p>
 * 提供电影、电视剧、动漫、综艺的视频检索和播放功能
 * 使用 MD5 + SHA-1 双层签名验证请求
 * 支持多线路测试和动态 URL 切换
 * </p>
 */
public class WenCai extends Spider {

    private static final String DEFAULT_HOST = "https://www.tjrongze.com";
    private static final String KEY = "cb808529bae6b6be45ecfab29a4889bc";
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/117.0.0.0 Safari/537.36";
    private static final String PLAY_FROM = "文才";

    private String host;

    public WenCai() {
        this.host = DEFAULT_HOST;
    }

    /**
     * 从 JSONObject 创建 Vod 对象
     *
     * @param remarkKey 用于获取备注的字段名（如 "vodVersion" 或 "vodRemarks"）
     * @param item      JSON 对象包含视频信息
     * @return Vod 对象
     */
    private static Vod createVod(String remarkKey, JSONObject item) {
        Vod vod = new Vod();
        vod.setVodId(item.optString("vodId"));
        vod.setVodName(item.optString("vodName"));
        vod.setVodPic(item.optString("vodPic"));
        String remark = item.optString(remarkKey);
        if (TextUtils.isEmpty(remark)) {
            remark = item.optString("vodRemarks");
        }
        vod.setVodRemarks(remark);
        return vod;
    }

    /**
     * 生成 MD5 + SHA-1 签名
     *
     * @param body 签名输入字符串
     * @return SHA-1 哈希值（小写十六进制）
     */
    private static String md5sha1Sign(String body) {
        try {
            // 第一层：MD5
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            byte[] md5Bytes = md5.digest(body.getBytes(StandardCharsets.UTF_8));
            StringBuilder md5Hex = new StringBuilder();
            for (byte b : md5Bytes) {
                String hex = Integer.toHexString(b & 0xff);
                if (hex.length() == 1) md5Hex.append('0');
                md5Hex.append(hex);
            }
            String md5Str = md5Hex.toString();

            // 第二层：SHA-1
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            byte[] sha1Bytes = sha1.digest(md5Str.getBytes(StandardCharsets.UTF_8));
            StringBuilder sha1Hex = new StringBuilder();
            for (byte b : sha1Bytes) {
                String hex = Integer.toHexString(b & 0xff);
                if (hex.length() == 1) sha1Hex.append('0');
                sha1Hex.append(hex);
            }
            return sha1Hex.toString();
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 发送 API 请求
     *
     * @param path        API 路径
     * @param bodyTemplate 请求体模板（含 {key} 和 {t} 占位符）
     * @return JSONObject 响应数据
     */
    private JSONObject requestAPI(String path, String bodyTemplate) throws Exception {
        // 构造完整 URL
        String url = new StringBuilder(host).append(path).toString();

        // 构造签名
        long timestamp = System.currentTimeMillis();
        String timestampStr = String.valueOf(timestamp);
        String body = bodyTemplate.replace("{key}", KEY).replace("{t}", timestampStr);
        String sign = md5sha1Sign(body);

        // 构造请求头
        HashMap<String, String> headers = new HashMap<>();
        headers.put("sign", sign);
        headers.put("T", timestampStr);
        headers.put("Deviceid", "Deviceid");
        headers.put("User-Agent", USER_AGENT);

        // 发送请求
        String response = OkHttp.string(url, headers);
        if (TextUtils.isEmpty(response)) {
            return new JSONObject();
        }
        return new JSONObject(response);
    }

    /**
     * 获取热搜列表
     *
     * @return 热搜视频列表
     */
    private ArrayList<Vod> getHotSearchList() throws Exception {
        JSONObject response = requestAPI("/api/mw-movie/anonymous/home/hotSearch", "key={key}&t={t}");
        JSONArray array = response.optJSONArray("data");
        ArrayList<Vod> list = new ArrayList<>();
        if (array == null) return list;

        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item == null) continue;
            list.add(createVod("vodVersion", item));
        }
        return list;
    }

    @Override
    public void init(Context context, String extend) throws Exception {
        if (TextUtils.isEmpty(extend)) return;

        String[] urls = extend.split(",");
        for (String url : urls) {
            url = url.trim();
            if (url.isEmpty()) continue;

            // 补协议前缀
            if (!url.startsWith("http")) {
                url = "https://" + url;
            }

            // 测试 URL 可用性
            boolean available = false;
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setInstanceFollowRedirects(true);
                conn.setRequestMethod("HEAD");
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);
                int code = conn.getResponseCode();
                conn.disconnect();
                available = code >= 200 && code <= 399;
            } catch (Exception ignored) {
            }

            // 如果可用则设置为新 host
            if (available) {
                this.host = url.replaceAll("/+$", "");
                return;
            }
        }
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        ArrayList<Class> classes = new ArrayList<>();
        classes.add(new Class("1", "电影"));
        classes.add(new Class("2", "电视剧"));
        classes.add(new Class("4", "动漫"));
        classes.add(new Class("3", "综艺"));

        // 过滤器配置
        String filtersJson = "{\"1\":[{\"name\":\"地区\",\"key\":\"area\",\"value\":[{\"n\":\"全部\",\"v\":\"\"},{\"n\":\"中国大陆\",\"v\":\"中国大陆\"},{\"n\":\"中国香港\",\"v\":\"中国香港\"},{\"n\":\"中国台湾\",\"v\":\"中国台湾\"},{\"n\":\"美国\",\"v\":\"美国\"},{\"n\":\"日本\",\"v\":\"日本\"},{\"n\":\"韩国\",\"v\":\"韩国\"},{\"n\":\"泰国\",\"v\":\"泰国\"},{\"n\":\"印度\",\"v\":\"印度\"},{\"n\":\"其他\",\"v\":\"其他\"}]},{\"name\":\"年份\",\"key\":\"year\",\"value\":[{\"n\":\"全部\",\"v\":\"\"},{\"n\":\"2026\",\"v\":\"2026\"},{\"n\":\"2025\",\"v\":\"2025\"},{\"n\":\"2024\",\"v\":\"2024\"},{\"n\":\"2023\",\"v\":\"2023\"},{\"n\":\"2022\",\"v\":\"2022\"},{\"n\":\"2021\",\"v\":\"2021\"},{\"n\":\"2020\",\"v\":\"2020\"},{\"n\":\"2019\",\"v\":\"2019\"},{\"n\":\"2018\",\"v\":\"2018\"},{\"n\":\"2017\",\"v\":\"2017\"},{\"n\":\"2016\",\"v\":\"2016\"},{\"n\":\"2015\",\"v\":\"2015\"},{\"n\":\"2014\",\"v\":\"2014\"},{\"n\":\"2013\",\"v\":\"2013\"},{\"n\":\"2012\",\"v\":\"2012\"},{\"n\":\"2011\",\"v\":\"2011\"},{\"n\":\"2010\",\"v\":\"2010\"},{\"n\":\"2009~2000\",\"v\":\"2009~2000\"},{\"n\":\"90年代\",\"v\":\"90年代\"},{\"n\":\"80年代\",\"v\":\"80年代\"}]}],\"2\":[{\"name\":\"地区\",\"key\":\"area\",\"value\":[{\"n\":\"全部\",\"v\":\"\"},{\"n\":\"中国大陆\",\"v\":\"中国大陆\"},{\"n\":\"中国香港\",\"v\":\"中国香港\"},{\"n\":\"中国台湾\",\"v\":\"中国台湾\"},{\"n\":\"美国\",\"v\":\"美国\"},{\"n\":\"日本\",\"v\":\"日本\"},{\"n\":\"韩国\",\"v\":\"韩国\"},{\"n\":\"泰国\",\"v\":\"泰国\"},{\"n\":\"印度\",\"v\":\"印度\"},{\"n\":\"其他\",\"v\":\"其他\"}]},{\"name\":\"年份\",\"key\":\"year\",\"value\":[{\"n\":\"全部\",\"v\":\"\"},{\"n\":\"2026\",\"v\":\"2026\"},{\"n\":\"2025\",\"v\":\"2025\"},{\"n\":\"2024\",\"v\":\"2024\"},{\"n\":\"2023\",\"v\":\"2023\"},{\"n\":\"2022\",\"v\":\"2022\"},{\"n\":\"2021\",\"v\":\"2021\"},{\"n\":\"2020\",\"v\":\"2020\"},{\"n\":\"2019\",\"v\":\"2019\"},{\"n\":\"2018\",\"v\":\"2018\"},{\"n\":\"2017\",\"v\":\"2017\"},{\"n\":\"2016\",\"v\":\"2016\"},{\"n\":\"2015\",\"v\":\"2015\"},{\"n\":\"2014\",\"v\":\"2014\"},{\"n\":\"2013\",\"v\":\"2013\"},{\"n\":\"2012\",\"v\":\"2012\"},{\"n\":\"2011\",\"v\":\"2011\"},{\"n\":\"2010\",\"v\":\"2010\"},{\"n\":\"2009~2000\",\"v\":\"2009~2000\"},{\"n\":\"90年代\",\"v\":\"90年代\"},{\"n\":\"80年代\",\"v\":\"80年代\"}]}],\"3\":[{\"name\":\"地区\",\"key\":\"area\",\"value\":[{\"n\":\"全部\",\"v\":\"\"},{\"n\":\"中国大陆\",\"v\":\"中国大陆\"},{\"n\":\"中国香港\",\"v\":\"中国香港\"},{\"n\":\"中国台湾\",\"v\":\"中国台湾\"},{\"n\":\"美国\",\"v\":\"美国\"},{\"n\":\"日本\",\"v\":\"日本\"},{\"n\":\"韩国\",\"v\":\"韩国\"},{\"n\":\"泰国\",\"v\":\"泰国\"},{\"n\":\"印度\",\"v\":\"印度\"},{\"n\":\"其他\",\"v\":\"其他\"}]},{\"name\":\"年份\",\"key\":\"year\",\"value\":[{\"n\":\"全部\",\"v\":\"\"},{\"n\":\"2026\",\"v\":\"2026\"},{\"n\":\"2025\",\"v\":\"2025\"},{\"n\":\"2024\",\"v\":\"2024\"},{\"n\":\"2023\",\"v\":\"2023\"},{\"n\":\"2022\",\"v\":\"2022\"},{\"n\":\"2021\",\"v\":\"2021\"},{\"n\":\"2020\",\"v\":\"2020\"},{\"n\":\"更早\",\"v\":\"更早\"}]}],\"4\":[{\"name\":\"地区\",\"key\":\"area\",\"value\":[{\"n\":\"全部\",\"v\":\"\"},{\"n\":\"中国大陆\",\"v\":\"中国大陆\"},{\"n\":\"美国\",\"v\":\"美国\"},{\"n\":\"日本\",\"v\":\"日本\"},{\"n\":\"其他\",\"v\":\"其他\"}]},{\"name\":\"年份\",\"key\":\"year\",\"value\":[{\"n\":\"全部\",\"v\":\"\"},{\"n\":\"2026\",\"v\":\"2026\"},{\"n\":\"2025\",\"v\":\"2025\"},{\"n\":\"2024\",\"v\":\"2024\"},{\"n\":\"2023\",\"v\":\"2023\"},{\"n\":\"2022\",\"v\":\"2022\"},{\"n\":\"2021\",\"v\":\"2021\"},{\"n\":\"2020\",\"v\":\"2020\"},{\"n\":\"2019\",\"v\":\"2019\"},{\"n\":\"2018\",\"v\":\"2018\"},{\"n\":\"2017\",\"v\":\"2017\"},{\"n\":\"2016\",\"v\":\"2016\"},{\"n\":\"2015\",\"v\":\"2015\"},{\"n\":\"2014\",\"v\":\"2014\"},{\"n\":\"2013\",\"v\":\"2013\"},{\"n\":\"2012\",\"v\":\"2012\"},{\"n\":\"2011\",\"v\":\"2011\"},{\"n\":\"2010\",\"v\":\"2010\"},{\"n\":\"2009~2000\",\"v\":\"2009~2000\"},{\"n\":\"90年代\",\"v\":\"90年代\"},{\"n\":\"80年代\",\"v\":\"80年代\"},{\"n\":\"更早\",\"v\":\"更早\"}]}]}";
        JSONObject filters = new JSONObject(filtersJson);

        ArrayList<Vod> list = getHotSearchList();
        return Result.string(classes, list, filters);
    }

    @Override
    public String homeVideoContent() throws Exception {
        ArrayList<Vod> list = getHotSearchList();
        return Result.string(list);
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        // 处理地区参数
        String area = "";
        if (extend != null) {
            area = extend.get("area");
        }
        if (TextUtils.isEmpty(area) || "全部".equals(area)) {
            area = "";
        }

        // 处理年份参数
        String year = "";
        if (extend != null) {
            year = extend.get("year");
        }
        if (!TextUtils.isEmpty(year) && !"全部".equals(year)) {
            area = year;  // 注意：smali 中这里的逻辑比较奇怪，需要忠实还原
        }

        // 处理分页参数
        if (TextUtils.isEmpty(pg)) {
            pg = "1";
        }

        // URL 编码参数
        String encodedArea = URLEncoder.encode(area, "UTF-8");
        String encodedYear = URLEncoder.encode(year, "UTF-8");

        // 构造 URL 和请求体
        String url = String.format("/api/mw-movie/anonymous/video/list?type1=%s&pageNum=%s&area=%s&year=%s",
                tid, pg, encodedArea, encodedYear);
        String body = String.format("area=%s&pageNum=%s&type1=%s&year=%s&key={key}&t={t}",
                area, pg, tid, year);

        JSONObject response = requestAPI(url, body);
        JSONObject data = response.optJSONObject("data");
        JSONArray array = (data != null) ? data.optJSONArray("list") : null;

        ArrayList<Vod> list = new ArrayList<>();
        if (array != null) {
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);
                if (item == null) continue;
                list.add(createVod("vodVersion", item));
            }
        }

        return Result.string(list);
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        String vodId = ids.get(0);

        String url = "/api/mw-movie/anonymous/video/detail?id=" + vodId;
        String body = "id=" + vodId + "&key={key}&t={t}";

        JSONObject response = requestAPI(url, body);
        JSONObject data = response.optJSONObject("data");
        if (data == null) {
            return Result.error("详情为空");
        }

        Vod vod = new Vod();
        vod.setVodId(vodId);
        vod.setVodName(data.optString("vodName"));
        vod.setVodPic(data.optString("vodPic"));
        vod.setVodYear(data.optString("vodYear"));
        vod.setVodArea(data.optString("vodArea"));
        vod.setVodActor(data.optString("vodActor"));
        vod.setVodDirector(data.optString("vodDirector"));
        vod.setVodRemarks(data.optString("vodRemarks"));
        vod.setTypeName(data.optString("vodClass"));

        String content = data.optString("vodBlurb");
        if (TextUtils.isEmpty(content)) {
            content = data.optString("vodContent");
        }
        vod.setVodContent(content);

        // 处理播放列表
        JSONArray episodeList = data.optJSONArray("episodeList");
        ArrayList<String> playUrls = new ArrayList<>();
        if (episodeList != null) {
            for (int i = 0; i < episodeList.length(); i++) {
                JSONObject episode = episodeList.optJSONObject(i);
                if (episode == null) continue;
                String name = episode.optString("name");
                String nid = episode.optString("nid");
                playUrls.add(name + "$" + vodId + "@" + nid);
            }
        }

        vod.setVodPlayFrom(PLAY_FROM);
        vod.setVodPlayUrl(TextUtils.join("#", playUrls));

        return Result.string(vod);
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        // 解析播放参数
        if (id.contains("$")) {
            int lastDollarIndex = id.lastIndexOf('$');
            id = id.substring(lastDollarIndex + 1);
        }

        String[] parts = id.split("@");
        if (parts.length < 2) {
            return Result.error("播放参数错误");
        }

        String videoId = parts[0];
        String nid = parts[1];

        // 构造 URL 和请求体
        String url = "/api/mw-movie/anonymous/v2/video/episode/url?clientType=3&id=" + videoId + "&nid=" + nid;
        String body = "clientType=3&id=" + videoId + "&nid=" + nid + "&key={key}&t={t}";

        JSONObject response = requestAPI(url, body);
        JSONObject data = response.optJSONObject("data");
        JSONArray array = (data != null) ? data.optJSONArray("list") : null;

        if (array == null || array.length() == 0) {
            return Result.error("无播放地址");
        }

        JSONObject firstEpisode = array.getJSONObject(0);
        String playUrl = firstEpisode.optString("url");

        if (TextUtils.isEmpty(playUrl)) {
            return Result.error("无效播放地址");
        }

        // 检查 URL 是否以 http/https 开头
        if (!playUrl.startsWith("http://") && !playUrl.startsWith("https://")) {
            return Result.error("无效播放地址");
        }

        // 构造播放请求头
        HashMap<String, String> headers = new HashMap<>();
        headers.put("User-Agent", USER_AGENT);
        headers.put("Origin", host);
        headers.put("Referer", host);

        return Result.get().url(playUrl).parse(0).header(headers).string();
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        return searchContent(key, quick, "1");
    }

    @Override
    public String searchContent(String key, boolean quick, String pg) throws Exception {
        if (TextUtils.isEmpty(key)) {
            return Result.string(new ArrayList<>());
        }

        key = key.trim();
        String encodedKey = URLEncoder.encode(key, "UTF-8");

        String url = "/api/mw-movie/anonymous/video/searchByWord?keyword=" + encodedKey + "&pageNum=1&pageSize=8";
        String body = "keyword=" + key + "&pageNum=1&pageSize=8&key={key}&t={t}";

        JSONObject response = requestAPI(url, body);
        JSONObject data = response.optJSONObject("data");
        JSONObject result = (data != null) ? data.optJSONObject("result") : null;
        JSONArray array = (result != null) ? result.optJSONArray("list") : null;

        ArrayList<Vod> list = new ArrayList<>();
        if (array != null) {
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);
                if (item == null) continue;

                // 过滤掉"伦理"类型
                String vodClass = item.optString("vodClass");
                if ("伦理".equals(vodClass)) continue;

                list.add(createVod("vodRemarks", item));
            }
        }

        return Result.string(list);
    }
}