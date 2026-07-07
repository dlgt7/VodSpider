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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Jpys 视频爬虫实现。
 * 支持电影、电视剧、综艺、动漫分类的视频搜索、详情解析和播放地址获取。
 * API 使用 SHA1 签名验证，支持域名动态切换。
 */
public class Jpys extends Spider {

    private static final Map<String, Boolean> cachedItems = new HashMap<>();

    private String host = "https://www.hkybqufgh.com";
    private String uuid = "";
    private String apiKey = "cb808529bae6b6be45ecfab29a4889bc";

    /**
     * 检查域名是否可用（HTTP HEAD 请求验证）。
     * @param url 域名 URL
     * @return 是否可用（200-399 状态码）
     */
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

    /**
     * 生成随机 UUID。
     * @return UUID 字符串
     */
    public static String getUUID() {
        return UUID.randomUUID().toString();
    }

    /**
     * MD5 哈希计算（32位小写，前补零）。
     * @param input 输入字符串
     * @return MD5 哈希值
     */
    public static String md5(String input) {
        try {
            byte[] digest = MessageDigest.getInstance("MD5").digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(new java.math.BigInteger(1, digest).toString(16));
            while (sb.length() < 32) {
                sb.insert(0, "0");
            }
            return sb.toString().toLowerCase();
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * SHA-1 哈希计算（双层签名链：先 MD5 再 SHA-1）。
     * @param input 输入字符串
     * @return SHA-1 哈希值（小写十六进制）
     */
    public static String sha1(String input) {
        try {
            // 第一层：MD5 哈希
            String md5Hash = md5(input);
            
            // 第二层：SHA-1 哈希
            byte[] digest = MessageDigest.getInstance("SHA-1").digest(md5Hash.getBytes(StandardCharsets.UTF_8));
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

    @Override
    public void init(Context context, String extend) throws Exception {
        super.init(context, extend);
        if (TextUtils.isEmpty(extend)) return;
        
        // 验证并选择可用域名
        String[] urls = extend.split(",");
        for (String url : urls) {
            if (checkDomainValid(url)) {
                this.host = url.trim();
                break;
            }
        }
        
        this.uuid = getUUID();
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        ArrayList<Class> classes = new ArrayList<>();
        ArrayList<Vod> list = new ArrayList<>();
        LinkedHashMap<String, List<Filter>> filters = new LinkedHashMap<>();
        
        // 分类列表
        classes.add(new Class("1", "电影"));
        classes.add(new Class("2", "电视剧"));
        classes.add(new Class("4", "动漫"));
        classes.add(new Class("3", "综艺"));
        
        // 过滤器配置
        JSONObject filterConfig = new JSONObject("{\"1\":[{\"name\":\"地区\",\"key\":\"area\",\"value\":[{\"n\":\"全部\",\"v\":\"\"},{\"n\":\"中国大陆\",\"v\":\"中国大陆\"},{\"n\":\"中国香港\",\"v\":\"中国香港\"},{\"n\":\"中国台湾\",\"v\":\"中国台湾\"},{\"n\":\"美国\",\"v\":\"美国\"},{\"n\":\"日本\",\"v\":\"日本\"},{\"n\":\"韩国\",\"v\":\"韩国\"},{\"n\":\"泰国\",\"v\":\"泰国\"},{\"n\":\"印度\",\"v\":\"印度\"},{\"n\":\"其他\",\"v\":\"其他\"}]},{\"name\":\"年份\",\"key\":\"year\",\"value\":[{\"n\":\"全部\",\"v\":\"\"},{\"n\":\"2026\",\"v\":\"2026\"},{\"n\":\"2025\",\"v\":\"2025\"},{\"n\":\"2024\",\"v\":\"2024\"},{\"n\":\"2023\",\"v\":\"2023\"},{\"n\":\"2022\",\"v\":\"2022\"},{\"n\":\"2021\",\"v\":\"2021\"},{\"n\":\"2020\",\"v\":\"2020\"},{\"n\":\"2019\",\"v\":\"2019\"},{\"n\":\"2018\",\"v\":\"2018\"},{\"n\":\"2017\",\"v\":\"2017\"},{\"n\":\"2016\",\"v\":\"2016\"},{\"n\":\"2015\",\"v\":\"2015\"},{\"n\":\"2014\",\"v\":\"2014\"},{\"n\":\"2013\",\"v\":\"2013\"},{\"n\":\"2012\",\"v\":\"2012\"},{\"n\":\"2011\",\"v\":\"2011\"},{\"n\":\"2010\",\"v\":\"2010\"},{\"n\":\"2009~2000\",\"v\":\"2009~2000\"},{\"n\":\"90年代\",\"v\":\"90年代\"},{\"n\":\"80年代\",\"v\":\"80年代\"}]}],\"2\":[{\"name\":\"地区\",\"key\":\"area\",\"value\":[{\"n\":\"全部\",\"v\":\"\"},{\"n\":\"中国大陆\",\"v\":\"中国大陆\"},{\"n\":\"中国香港\",\"v\":\"中国香港\"},{\"n\":\"中国台湾\",\"v\":\"中国台湾\"},{\"n\":\"美国\",\"v\":\"美国\"},{\"n\":\"日本\",\"v\":\"日本\"},{\"n\":\"韩国\",\"v\":\"韩国\"},{\"n\":\"泰国\",\"v\":\"泰国\"},{\"n\":\"印度\",\"v\":\"印度\"},{\"n\":\"其他\",\"v\":\"其他\"}]},{\"name\":\"年份\",\"key\":\"year\",\"value\":[{\"n\":\"全部\",\"v\":\"\"},{\"n\":\"2026\",\"v\":\"2026\"},{\"n\":\"2025\",\"v\":\"2025\"},{\"n\":\"2024\",\"v\":\"2024\"},{\"n\":\"2023\",\"v\":\"2023\"},{\"n\":\"2022\",\"v\":\"2022\"},{\"n\":\"2021\",\"v\":\"2021\"},{\"n\":\"2020\",\"v\":\"2020\"},{\"n\":\"2019\",\"v\":\"2019\"},{\"n\":\"2018\",\"v\":\"2018\"},{\"n\":\"2017\",\"v\":\"2017\"},{\"n\":\"2016\",\"v\":\"2016\"},{\"n\":\"2015\",\"v\":\"2015\"},{\"n\":\"2014\",\"v\":\"2014\"},{\"n\":\"2013\",\"v\":\"2013\"},{\"n\":\"2012\",\"v\":\"2012\"},{\"n\":\"2011\",\"v\":\"2011\"},{\"n\":\"2010\",\"v\":\"2010\"},{\"n\":\"2009~2000\",\"v\":\"2009~2000\"},{\"n\":\"90年代\",\"v\":\"90年代\"},{\"n\":\"80年代\",\"v\":\"80年代\"}]}],\"3\":[{\"name\":\"地区\",\"key\":\"area\",\"value\":[{\"n\":\"全部\",\"v\":\"\"},{\"n\":\"中国大陆\",\"v\":\"中国大陆\"},{\"n\":\"中国香港\",\"v\":\"中国香港\"},{\"n\":\"中国台湾\",\"v\":\"中国台湾\"},{\"n\":\"美国\",\"v\":\"美国\"},{\"n\":\"日本\",\"v\":\"日本\"},{\"n\":\"韩国\",\"v\":\"韩国\"},{\"n\":\"泰国\",\"v\":\"泰国\"},{\"n\":\"印度\",\"v\":\"印度\"},{\"n\":\"其他\",\"v\":\"其他\"}]},{\"name\":\"年份\",\"key\":\"year\",\"value\":[{\"n\":\"全部\",\"v\":\"\"},{\"n\":\"2026\",\"v\":\"2026\"},{\"n\":\"2025\",\"v\":\"2025\"},{\"n\":\"2024\",\"v\":\"2024\"},{\"n\":\"2023\",\"v\":\"2023\"},{\"n\":\"2022\",\"v\":\"2022\"},{\"n\":\"2021\",\"v\":\"2021\"},{\"n\":\"2020\",\"v\":\"2020\"},{\"n\":\"更早\",\"v\":\"更早\"}]}],\"4\":[{\"name\":\"地区\",\"key\":\"area\",\"value\":[{\"n\":\"全部\",\"v\":\"\"},{\"n\":\"中国大陆\",\"v\":\"中国大陆\"},{\"n\":\"美国\",\"v\":\"美国\"},{\"n\":\"日本\",\"v\":\"日本\"},{\"n\":\"其他\",\"v\":\"其他\"}]},{\"name\":\"年份\",\"key\":\"year\",\"value\":[{\"n\":\"全部\",\"v\":\"\"},{\"n\":\"2026\",\"v\":\"2026\"},{\"n\":\"2025\",\"v\":\"2025\"},{\"n\":\"2024\",\"v\":\"2024\"},{\"n\":\"2023\",\"v\":\"2023\"},{\"n\":\"2022\",\"v\":\"2022\"},{\"n\":\"2021\",\"v\":\"2021\"},{\"n\":\"2020\",\"v\":\"2020\"},{\"n\":\"2019\",\"v\":\"2019\"},{\"n\":\"2018\",\"v\":\"2018\"},{\"n\":\"2017\",\"v\":\"2017\"},{\"n\":\"2016\",\"v\":\"2016\"},{\"n\":\"2015\",\"v\":\"2015\"},{\"n\":\"2014\",\"v\":\"2014\"},{\"n\":\"2013\",\"v\":\"2013\"},{\"n\":\"2012\",\"v\":\"2012\"},{\"n\":\"2011\",\"v\":\"2011\"},{\"n\":\"2010\",\"v\":\"2010\"},{\"n\":\"2009~2000\",\"v\":\"2009~2000\"},{\"n\":\"90年代\",\"v\":\"90年代\"},{\"n\":\"80年代\",\"v\":\"80年代\"},{\"n\":\"更早\",\"v\":\"更早\"}]}]}");
        
        // 解析过滤器
        for (String typeId : new String[]{"1", "2", "3", "4"}) {
            ArrayList<Filter> typeFilters = new ArrayList<>();
            JSONArray filterArray = filterConfig.optJSONArray(typeId);
            if (filterArray != null) {
                for (int i = 0; i < filterArray.length(); i++) {
                    JSONObject filterObj = filterArray.getJSONObject(i);
                    String name = filterObj.getString("name");
                    String key = filterObj.getString("key");
                    JSONArray valueArray = filterObj.getJSONArray("value");
                    ArrayList<Filter.Value> values = new ArrayList<>();
                    for (int j = 0; j < valueArray.length(); j++) {
                        JSONObject valueObj = valueArray.getJSONObject(j);
                        values.add(new Filter.Value(valueObj.getString("n"), valueObj.getString("v")));
                    }
                    typeFilters.add(new Filter(key, name, values));
                }
            }
            filters.put(typeId, typeFilters);
        }
        
        // 热门搜索列表
        String timestamp = String.valueOf(System.currentTimeMillis());
        String sign = sha1(String.format("key=%s&t=%s", apiKey, timestamp));
        
        HashMap<String, String> headers = new HashMap<>();
        headers.put("sign", sign);
        headers.put("T", timestamp);
        headers.put("Deviceid", "Deviceid");
        
        String response = OkHttp.string(host + "/api/mw-movie/anonymous/home/hotSearch", headers);
        JSONArray hotList = new JSONObject(response).getJSONArray("data");
        
        for (int i = 0; i < hotList.length(); i++) {
            JSONObject item = hotList.getJSONObject(i);
            list.add(new Vod(
                item.getString("vodId"),
                item.getString("vodName"),
                item.getString("vodPic"),
                item.getString("vodVersion")
            ));
        }
        
        return Result.string(classes, list, filters);
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        ArrayList<Vod> list = new ArrayList<>();
        
        // 处理扩展参数
        String by = extend.containsKey("by") ? extend.get("by") : "";
        String year = extend.containsKey("year") ? extend.get("year") : "";
        String area = extend.containsKey("area") ? extend.get("area") : "";
        
        if ("全部".equals(by)) by = "";
        if ("全部".equals(year)) year = "";
        if ("全部".equals(area)) area = "";
        
        // 签名计算
        String timestamp = String.valueOf(System.currentTimeMillis());
        String signData = String.format("area=%s&pageNum=%s&type1=%s&year=%s&key=%s&t=%s", 
            area, pg, tid, year, apiKey, timestamp);
        String sign = sha1(signData);
        
        HashMap<String, String> headers = new HashMap<>();
        headers.put("sign", sign);
        headers.put("T", timestamp);
        headers.put("Deviceid", "Deviceid");
        
        // API 请求
        String url = String.format(host + "/api/mw-movie/anonymous/video/list?type1=%s&pageNum=%s&area=%s&year=%s", 
            tid, pg, area, year);
        String response = OkHttp.string(url, headers);
        
        JSONObject data = new JSONObject(response).getJSONObject("data");
        JSONArray videoList = data.getJSONArray("list");
        
        for (int i = 0; i < videoList.length(); i++) {
            JSONObject item = videoList.getJSONObject(i);
            String name = item.getString("vodName");
            
            // 去重逻辑
            if (cachedItems.isEmpty() || !cachedItems.containsKey(name)) {
                list.add(new Vod(
                    item.getString("vodId"),
                    name,
                    item.getString("vodPic"),
                    item.getString("vodVersion")
                ));
            }
        }
        
        // 注意：原 API 未提供分页元数据，此处使用默认值
        int page = Integer.parseInt(pg);
        return Result.get().page(page, page, 20, 0).vod(list).string();
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        String vodId = ids.get(0);
        
        // 签名计算
        String timestamp = String.valueOf(System.currentTimeMillis());
        String signData = "id=" + vodId + String.format("&key=%s&t=%s", apiKey, timestamp);
        String sign = sha1(signData);
        
        HashMap<String, String> headers = new HashMap<>();
        headers.put("sign", sign);
        headers.put("T", timestamp);
        headers.put("Deviceid", "Deviceid");
        
        // API 请求
        String url = host + "/api/mw-movie/anonymous/v2/video/detail?id=" + vodId;
        String response = OkHttp.string(url, headers);
        
        // 调试日志：打印API响应
        SpiderDebug.log("Jpys detailContent API响应: " + response);
        
        // 解析响应
        JSONObject jsonResponse = new JSONObject(response);
        
        // 检查响应码
        if (jsonResponse.optInt("code") != 200) {
            SpiderDebug.log("Jpys detailContent API错误: " + jsonResponse.optString("msg"));
            return Result.string(new Vod());
        }
        
        JSONObject data = jsonResponse.getJSONObject("data");
        
        // 构建 Vod 对象
        Vod vod = new Vod();
        vod.setVodId(vodId);
        vod.setVodName(data.optString("vodName"));
        vod.setVodPic(data.optString("vodPic"));
        vod.setVodRemarks(data.optString("vodRemarks"));
        vod.setVodContent(data.optString("vodContent"));
        vod.setVodYear(data.optString("vodYear"));
        vod.setVodActor(data.optString("vodActor"));
        vod.setVodDirector(data.optString("vodDirector"));
        
        // 播放列表解析
        JSONArray episodeListArray = data.optJSONArray("episodeList");
        ArrayList<String> playFrom = new ArrayList<>();
        ArrayList<String> playUrl = new ArrayList<>();
        
        SpiderDebug.log("Jpys episodeList字段存在: " + (episodeListArray != null));
        SpiderDebug.log("Jpys episodeList长度: " + (episodeListArray != null ? episodeListArray.length() : 0));
        
        if (episodeListArray != null && episodeListArray.length() > 0) {
            playFrom.add("默认线路");
            
            ArrayList<String> episodeUrls = new ArrayList<>();
            for (int i = 0; i < episodeListArray.length(); i++) {
                JSONObject ep = episodeListArray.getJSONObject(i);
                StringBuilder sb = new StringBuilder();
                sb.append(ep.optString("name")).append("$");
                sb.append(vodId).append("@");
                sb.append(ep.optString("nid"));
                episodeUrls.add(sb.toString());
                
                // 调试日志：打印每集信息
                SpiderDebug.log("Jpys 剧集" + i + ": name=" + ep.optString("name") + ", nid=" + ep.optString("nid"));
            }
            playUrl.add(TextUtils.join("#", episodeUrls));
        }
        
        vod.setVodPlayFrom(TextUtils.join("$$$", playFrom));
        vod.setVodPlayUrl(TextUtils.join("$$$", playUrl));
        
        // 调试日志：打印最终播放列表
        SpiderDebug.log("Jpys vodPlayFrom: " + vod.getVodPlayFrom());
        SpiderDebug.log("Jpys vodPlayUrl: " + vod.getVodPlayUrl());
        
        return Result.string(vod);
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        try {
            // 解析视频 ID 和节点 ID
            String[] parts = id.split("@");
            String videoId = parts[0];
            String nodeId = parts[1];
            
            // 签名计算
            String timestamp = String.valueOf(System.currentTimeMillis());
            String signData = String.format("id=%s&nid=%s&key=%s&t=%s", 
                videoId, nodeId, apiKey, timestamp);
            String sign = sha1(signData);
            
            HashMap<String, String> headers = new HashMap<>();
            headers.put("sign", sign);
            headers.put("T", timestamp);
            headers.put("Deviceid", "Deviceid");
            
            // API 请求
            String url = host + "/api/mw-movie/anonymous/v2/video/episode/url?id=" + videoId + "&nid=" + nodeId;
            String response = OkHttp.string(url, headers);
            
            JSONObject data = new JSONObject(response).getJSONObject("data");
            JSONArray playList = data.getJSONArray("list");
            String playUrl = playList.getJSONObject(0).getString("url");
            
            // 构建播放响应
            JSONObject result = new JSONObject();
            result.put("url", playUrl);
            result.put("parse", "0");
            result.put("playUrl", "");
            
            JSONObject playHeader = new JSONObject();
            playHeader.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/117.0.0.0 Safari/537.36");
            playHeader.put("Origin", host);
            playHeader.put("Referer", host);
            result.put("header", playHeader.toString());
            
            return result.toString();
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        ArrayList<Vod> list = new ArrayList<>();
        
        // 签名计算（注意：keyword 使用原始字符串，不编码）
        String timestamp = String.valueOf(System.currentTimeMillis());
        String signData = "keyword=" + key + "&pageNum=1&pageSize=8" + 
            String.format("&key=%s&t=%s", apiKey, timestamp);
        String sign = sha1(signData);
        
        HashMap<String, String> headers = new HashMap<>();
        headers.put("sign", sign);
        headers.put("T", timestamp);
        headers.put("Deviceid", "Deviceid");
        
        // API 请求（URL 中 keyword 需要 URLEncoder）
        String url = host + "/api/mw-movie/anonymous/video/searchByWord?keyword=" + 
            URLEncoder.encode(key, StandardCharsets.UTF_8.name()) + "&pageNum=1&pageSize=8";
        String response = OkHttp.string(url, headers);
        
        JSONObject data = new JSONObject(response).getJSONObject("data").getJSONObject("result");
        JSONArray searchList = data.getJSONArray("list");
        
        for (int i = 0; i < searchList.length(); i++) {
            JSONObject item = searchList.getJSONObject(i);
            String vodClass = item.getString("vodClass");
            
            // 过滤伦理类内容
            if (!"伦理".equals(vodClass)) {
                list.add(new Vod(
                    item.getString("vodId"),
                    item.getString("vodName"),
                    item.getString("vodPic"),
                    item.getString("vodRemarks")
                ));
            }
        }
        
        return Result.get().page(1, 1, 20, 0).vod(list).string();
    }
}