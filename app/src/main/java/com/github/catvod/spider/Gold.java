package com.github.catvod.spider;

import android.content.Context;
import android.text.TextUtils;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Filter;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Crypto;
import com.github.catvod.utils.Util;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * 金牌影视 Spider (Gold)
 * 站点：https://www.jiabaide.cn（支持多域名自动测速）
 * 基于 API + MD5/SHA1 签名验证
 * ext 配置：{"site":"https://www.jiabaide.cn,域名2,域名3"}
 */
public class Gold extends Spider {

    private static final String DEFAULT_HOST = "https://www.jiabaide.cn";
    private static final String SIGN_KEY = "cb808529bae6b6be45ecfab29a4889bc";
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; ) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.6478.61 Chrome/126.0.6478.61 Not/A)Brand/8 Safari/537.36";

    private String host;

    @Override
    public void init(Context context, String extend) throws Exception {
        super.init(context);
        List<String> hosts = new ArrayList<>();
        if (!TextUtils.isEmpty(extend)) {
            extend = extend.trim();
            // 支持 JSON 格式 {"site":"url1,url2"}
            if (extend.startsWith("{")) {
                try {
                    JSONObject json = new JSONObject(extend);
                    String site = json.optString("site", "");
                    if (!TextUtils.isEmpty(site)) {
                        hosts = parseHosts(site);
                    }
                } catch (Exception ignored) {
                }
            } else if (extend.contains(",")) {
                hosts = parseHosts(extend);
            } else if (extend.startsWith("http")) {
                hosts.add(extend);
            }
        }
        if (hosts.isEmpty()) hosts.add(DEFAULT_HOST);
        host = selectFastestHost(hosts);
    }

    private List<String> parseHosts(String site) {
        List<String> hosts = new ArrayList<>();
        for (String h : site.split(",")) {
            h = h.trim();
            if (!TextUtils.isEmpty(h)) {
                if (!h.startsWith("http")) h = "https://" + h;
                hosts.add(h);
            }
        }
        return hosts;
    }

    /** 多域名测速，选择最快的（并发 HEAD 请求） */
    private String selectFastestHost(List<String> hosts) {
        if (hosts.size() <= 1) return hosts.isEmpty() ? DEFAULT_HOST : hosts.get(0);
        ExecutorService executor = Executors.newFixedThreadPool(hosts.size());
        try {
            Map<String, Future<Long>> futures = new LinkedHashMap<>();
            for (final String h : hosts) {
                futures.put(h, executor.submit(new Callable<Long>() {
                    @Override
                    public Long call() {
                        long start = System.currentTimeMillis();
                        try {
                            OkHttp.string(h, getBaseHeaders());
                            return System.currentTimeMillis() - start;
                        } catch (Exception e) {
                            return Long.MAX_VALUE;
                        }
                    }
                }));
            }
            String best = hosts.get(0);
            long minDelay = Long.MAX_VALUE;
            for (Map.Entry<String, Future<Long>> entry : futures.entrySet()) {
                try {
                    long delay = entry.getValue().get();
                    if (delay < minDelay) {
                        minDelay = delay;
                        best = entry.getKey();
                    }
                } catch (Exception ignored) {
                }
            }
            return best;
        } finally {
            executor.shutdown();
        }
    }

    private Map<String, String> getBaseHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", USER_AGENT);
        headers.put("Accept", "application/json, text/plain, */*");
        return headers;
    }

    /**
     * 构建带签名的请求头
     * 算法：sign = SHA1(MD5(queryString))，queryString 包含请求参数 + key + t
     */
    private Map<String, String> getHeaders(Map<String, String> params) {
        if (params == null) params = new LinkedHashMap<>();
        // 复制 params 避免修改原 map（使用 LinkedHashMap 保持顺序）
        Map<String, String> signParams = new LinkedHashMap<>(params);
        String t = String.valueOf(System.currentTimeMillis());
        signParams.put("key", SIGN_KEY);
        signParams.put("t", t);

        String queryString = buildQueryString(signParams);
        String md5 = Crypto.md5(queryString);
        String sign = Crypto.sha1(md5);
        String deviceid = UUID.randomUUID().toString();

        Map<String, String> headers = getBaseHeaders();
        headers.put("sign", sign);
        headers.put("t", t);
        headers.put("deviceid", deviceid);
        return headers;
    }

    /** 构建查询字符串 k=v&k=v（保持插入顺序） */
    private String buildQueryString(Map<String, String> params) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (sb.length() > 0) sb.append("&");
            sb.append(entry.getKey()).append("=").append(entry.getValue());
        }
        return sb.toString();
    }

    /** GET 请求并返回 JSON */
    private JSONObject getJson(String path, Map<String, String> params) throws Exception {
        String url = host + path;
        if (params != null && !params.isEmpty()) {
            url += "?" + buildQueryString(params);
        }
        Map<String, String> headers = getHeaders(params);
        String resp = OkHttp.string(url, headers);
        return new JSONObject(resp);
    }

    /** 转换 API 字段名为 vod_ 前缀（vodId → vod_id, typeName → type_name） */
    private String convertField(String field) {
        field = field.toLowerCase();
        if (field.startsWith("vod") && field.length() > 3) {
            field = field.replace("vod", "vod_");
        }
        if (field.startsWith("type") && field.length() > 4) {
            field = field.replace("type", "type_");
        }
        return field;
    }

    /** 从 JSON 数组构建 Vod 列表（字段名转换） */
    private List<Vod> getVod(JSONArray array) {
        List<Vod> list = new ArrayList<>();
        if (array == null) return list;
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item == null) continue;
            Vod vod = new Vod();
            vod.setVodId(item.optString("vodId"));
            vod.setVodName(item.optString("vodName"));
            vod.setVodPic(item.optString("vodPic"));
            vod.setVodRemarks(item.optString("vodRemarks"));
            vod.setVodYear(item.optString("vodYear"));
            vod.setVodArea(item.optString("vodArea"));
            vod.setVodActor(item.optString("vodActor"));
            vod.setVodDirector(item.optString("vodDirector"));
            vod.setVodContent(item.optString("vodContent"));
            vod.setTypeName(item.optString("typeName"));
            // 保留 episodelist 供 detailContent 使用（通过 setVodTag 临时存储）
            JSONArray eps = item.optJSONArray("episodeList");
            if (eps == null) eps = item.optJSONArray("episodelist");
            if (eps != null) {
                vod.setVodTag(eps.toString());
            }
            list.add(vod);
        }
        return list;
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        List<Class> classes = new ArrayList<>();
        LinkedHashMap<String, List<Filter>> filters = new LinkedHashMap<>();

        try {
            JSONObject cdata = getJson("/api/mw-movie/anonymous/get/filer/type", null);
            JSONArray classArray = cdata.optJSONArray("data");
            if (classArray != null) {
                for (int i = 0; i < classArray.length(); i++) {
                    JSONObject k = classArray.optJSONObject(i);
                    if (k == null) continue;
                    classes.add(new Class(String.valueOf(k.optInt("typeId")), k.optString("typeName")));
                }
            }

            JSONObject fdata = getJson("/api/mw-movie/anonymous/v1/get/filer/list", null);
            JSONObject filterData = fdata.optJSONObject("data");
            if (filterData != null) {
                JSONArray sortValues = new JSONArray();
                sortValues.put(new JSONObject("{\"n\":\"最近更新\",\"v\":\"2\"}"));
                sortValues.put(new JSONObject("{\"n\":\"人气高低\",\"v\":\"3\"}"));
                sortValues.put(new JSONObject("{\"n\":\"评分高低\",\"v\":\"4\"}"));

                for (java.util.Iterator<String> it = filterData.keys(); it.hasNext(); ) {
                    String tid = it.next();
                    JSONObject d = filterData.optJSONObject(tid);
                    if (d == null) continue;
                    List<Filter> filterList = new ArrayList<>();

                    // 类型
                    List<Filter.Value> typeValues = new ArrayList<>();
                    JSONArray typeList = d.optJSONArray("typeList");
                    if (typeList != null) {
                        for (int i = 0; i < typeList.length(); i++) {
                            JSONObject item = typeList.optJSONObject(i);
                            if (item != null) typeValues.add(new Filter.Value(item.optString("itemText"), item.optString("itemValue")));
                        }
                    }
                    if (!typeValues.isEmpty()) filterList.add(new Filter("type", "类型", typeValues));

                    // 剧情
                    JSONArray plotList = d.optJSONArray("plotList");
                    if (plotList != null && plotList.length() > 0) {
                        List<Filter.Value> plotValues = new ArrayList<>();
                        for (int i = 0; i < plotList.length(); i++) {
                            JSONObject item = plotList.optJSONObject(i);
                            if (item != null) plotValues.add(new Filter.Value(item.optString("itemText"), item.optString("itemText")));
                        }
                        if (!plotValues.isEmpty()) filterList.add(new Filter("v_class", "剧情", plotValues));
                    }

                    // 地区
                    JSONArray districtList = d.optJSONArray("districtList");
                    if (districtList != null) {
                        List<Filter.Value> areaValues = new ArrayList<>();
                        for (int i = 0; i < districtList.length(); i++) {
                            JSONObject item = districtList.optJSONObject(i);
                            if (item != null) areaValues.add(new Filter.Value(item.optString("itemText"), item.optString("itemText")));
                        }
                        if (!areaValues.isEmpty()) filterList.add(new Filter("area", "地区", areaValues));
                    }

                    // 年份
                    JSONArray yearList = d.optJSONArray("yearList");
                    if (yearList != null) {
                        List<Filter.Value> yearValues = new ArrayList<>();
                        for (int i = 0; i < yearList.length(); i++) {
                            JSONObject item = yearList.optJSONObject(i);
                            if (item != null) yearValues.add(new Filter.Value(item.optString("itemText"), item.optString("itemText")));
                        }
                        if (!yearValues.isEmpty()) filterList.add(new Filter("year", "年份", yearValues));
                    }

                    // 语言
                    JSONArray languageList = d.optJSONArray("languageList");
                    if (languageList != null) {
                        List<Filter.Value> langValues = new ArrayList<>();
                        for (int i = 0; i < languageList.length(); i++) {
                            JSONObject item = languageList.optJSONObject(i);
                            if (item != null) langValues.add(new Filter.Value(item.optString("itemText"), item.optString("itemText")));
                        }
                        if (!langValues.isEmpty()) filterList.add(new Filter("lang", "语言", langValues));
                    }

                    // 排序
                    List<Filter.Value> sortVals = new ArrayList<>();
                    sortVals.add(new Filter.Value("最近更新", "2"));
                    sortVals.add(new Filter.Value("人气高低", "3"));
                    sortVals.add(new Filter.Value("评分高低", "4"));
                    // tid==1 时移除"最近更新"
                    if ("1".equals(tid)) {
                        sortVals.remove(0);
                    }
                    filterList.add(new Filter("sort", "排序", sortVals));

                    filters.put(tid, filterList);
                }
            }
        } catch (Exception e) {
            SpiderDebug.log(e);
        }

        return Result.string(classes, new ArrayList<>(), filters);
    }

    @Override
    public String homeVideoContent() throws Exception {
        List<Vod> list = new ArrayList<>();
        try {
            JSONObject data1 = getJson("/api/mw-movie/anonymous/v1/home/all/list", null);
            JSONObject d1 = data1.optJSONObject("data");
            if (d1 != null) {
                for (java.util.Iterator<String> it = d1.keys(); it.hasNext(); ) {
                    JSONObject group = d1.optJSONObject(it.next());
                    if (group == null) continue;
                    JSONArray arr = group.optJSONArray("list");
                    if (arr != null) list.addAll(getVod(arr));
                }
            }
            JSONObject data2 = getJson("/api/mw-movie/anonymous/home/hotSearch", null);
            JSONArray hotArr = data2.optJSONArray("data");
            if (hotArr != null) list.addAll(getVod(hotArr));
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        return Result.string(list);
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        if (TextUtils.isEmpty(pg)) pg = "1";
        Map<String, String> params = new LinkedHashMap<>();
        params.put("area", extend != null ? extend.getOrDefault("area", "") : "");
        params.put("filterStatus", "1");
        params.put("lang", extend != null ? extend.getOrDefault("lang", "") : "");
        params.put("pageNum", pg);
        params.put("pageSize", "30");
        params.put("sort", extend != null && !TextUtils.isEmpty(extend.get("sort")) ? extend.get("sort") : "1");
        params.put("sortBy", "1");
        params.put("type", extend != null ? extend.getOrDefault("type", "") : "");
        params.put("type1", tid);
        params.put("v_class", extend != null ? extend.getOrDefault("v_class", "") : "");
        params.put("year", extend != null ? extend.getOrDefault("year", "") : "");

        List<Vod> list = new ArrayList<>();
        try {
            JSONObject data = getJson("/api/mw-movie/anonymous/video/list", params);
            JSONObject d = data.optJSONObject("data");
            if (d != null) {
                list = getVod(d.optJSONArray("list"));
            }
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        int page = Util.toInt(pg, 1);
        return Result.get().page(page, 9999, 90, 999999).vod(list).string();
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        String id = ids.get(0);
        List<Vod> list = new ArrayList<>();
        try {
            Map<String, String> params = new LinkedHashMap<>();
            params.put("id", id);
            JSONObject data = getJson("/api/mw-movie/anonymous/video/detail", params);
            JSONObject d = data.optJSONObject("data");
            if (d == null) return Result.string(list);

            List<Vod> vods = getVod(new JSONArray().put(d));
            if (vods.isEmpty()) return Result.string(list);
            Vod vod = vods.get(0);

            // 构建播放列表（API 返回 episodeList 驼峰命名）
            List<String> playUrls = new ArrayList<>();
            JSONArray episodes = d.optJSONArray("episodeList");
            if (episodes == null) episodes = d.optJSONArray("episodelist");
            if (episodes == null) {
                // 大小写不敏感搜索
                for (java.util.Iterator<String> it = d.keys(); it.hasNext(); ) {
                    String k = it.next();
                    if (k.equalsIgnoreCase("episodelist")) {
                        episodes = d.optJSONArray(k);
                        break;
                    }
                }
            }
            if (episodes != null) {
                for (int i = 0; i < episodes.length(); i++) {
                    JSONObject ep = episodes.optJSONObject(i);
                    if (ep == null) continue;
                    String name = episodes.length() > 1 ? ep.optString("name") : vod.getVodName();
                    playUrls.add(name + "$" + id + "@@" + ep.optString("nid"));
                }
            }
            vod.setVodPlayFrom("金牌");
            vod.setVodPlayUrl(TextUtils.join("#", playUrls));
            vod.setVodTag(""); // 清理临时存储
            list.add(vod);
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        return Result.string(list);
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        return searchContent(key, quick, "1");
    }

    @Override
    public String searchContent(String key, boolean quick, String pg) throws Exception {
        if (TextUtils.isEmpty(pg)) pg = "1";
        Map<String, String> params = new LinkedHashMap<>();
        params.put("keyword", key);
        params.put("pageNum", pg);
        params.put("pageSize", "8");
        params.put("sourceCode", "1");

        List<Vod> list = new ArrayList<>();
        try {
            JSONObject data = getJson("/api/mw-movie/anonymous/video/searchByWord", params);
            JSONObject d = data.optJSONObject("data");
            if (d != null) {
                JSONObject result = d.optJSONObject("result");
                if (result != null) {
                    list = getVod(result.optJSONArray("list"));
                }
            }
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        return Result.string(list);
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        try {
            String[] ids = id.split("@@");
            if (ids.length < 2) {
                return Result.get().parse(0).url(id).string();
            }
            Map<String, String> params = new LinkedHashMap<>();
            params.put("clientType", "1");
            params.put("id", ids[0]);
            params.put("nid", ids[1]);

            JSONObject data = getJson("/api/mw-movie/anonymous/v2/video/episode/url", params);
            JSONObject d = data.optJSONObject("data");
            List<String> urlList = new ArrayList<>();
            if (d != null) {
                JSONArray list = d.optJSONArray("list");
                if (list != null) {
                    for (int i = 0; i < list.length(); i++) {
                        JSONObject item = list.optJSONObject(i);
                        if (item == null) continue;
                        urlList.add(item.optString("resolutionName"));
                        urlList.add(item.optString("url"));
                    }
                }
            }

            Map<String, String> headers = new HashMap<>();
            headers.put("User-Agent", USER_AGENT);
            headers.put("Origin", host);
            headers.put("Referer", host + "/");

            if (urlList.isEmpty()) {
                return Result.get().parse(0).url(id).header(headers).string();
            }
            return Result.get().parse(0).url(urlList).header(headers).string();
        } catch (Exception e) {
            SpiderDebug.log(e);
            return Result.get().parse(0).url(id).string();
        }
    }
}
