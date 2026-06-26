package com.github.catvod.spider;

import android.content.Context;
import android.text.TextUtils;
import android.util.Base64;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Filter;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Util;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import okhttp3.Response;

public class Hxq extends Spider {

    private static final String[][] QUALITIES = {
            {"蓝光HDR", "11"},
            {"蓝光1080P", "10"},
            {"高清720P", "2"},
            {"标清480P", "1"}
    };

    private HxqSession session;
    private final HashMap<String, String> tokenCache = new HashMap<>();
    private String host = "https://hxqapi.hiyun.tv";
    private final HashMap<String, Long> expireCache = new HashMap<>();

    public static String[] a(String p0) {
        if (TextUtils.isEmpty(p0)) {
            return new String[0];
        }
        String v0;
        if (p0.contains("%")) {
            v0 = Util.decode(p0);
        } else {
            v0 = p0;
        }
        if (v0.startsWith("http://") || v0.startsWith("https://")) {
            return new String[]{v0};
        }
        byte[] bytes = Base64.decode(p0, 8);
        String decoded = new String(bytes, StandardCharsets.UTF_8);
        return decoded.split("\\|\\|\\|");
    }

    public static String e(String p0) {
        if (TextUtils.isEmpty(p0)) {
            return p0;
        }
        if (!p0.contains("51touxiang.com")) {
            return p0;
        }
        if (p0.contains("voldn") || p0.contains("hwcpicc") || !p0.startsWith("http://")) {
            int idx = p0.indexOf("://");
            if (idx < 0) {
                return p0;
            }
            int slash = p0.indexOf('/', idx + 3);
            if (slash < 0) {
                return p0;
            }
            return "http://piccc.cdn.51touxiang.com" + p0.substring(slash);
        }
        return p0;
    }

    public static String f(JSONObject p0) {
        Object image = p0.opt("image");
        if (image instanceof JSONObject) {
            JSONObject img = (JSONObject) image;
            String url = img.optString("url", "");
            if (url.isEmpty()) {
                url = img.optString("thumb", "");
            }
            return url;
        }
        if (image instanceof String) {
            return (String) image;
        }
        String poster = p0.optString("poster", "");
        if (poster.isEmpty()) {
            poster = p0.optString("thumb", "");
        }
        return poster;
    }

    public static ArrayList<Vod> g(JSONArray p0, int p1) {
        ArrayList<Vod> list = new ArrayList<>();
        if (p0 == null) {
            return list;
        }
        int limit = (p1 > 0) ? Math.min(p1, p0.length()) : p0.length();
        for (int i = 0; i < limit; i++) {
            JSONObject item = p0.optJSONObject(i);
            if (item == null) {
                continue;
            }
            String remarks = item.optString("conerMemo", "");
            if (remarks.isEmpty()) {
                remarks = item.optString("detailMemo", "");
            }
            if (remarks.isEmpty()) {
                remarks = item.optString("shorthand", "");
            }
            String sid = String.valueOf(item.opt("sid"));
            String name = item.optString("name", "");
            String pic = f(item);
            list.add(new Vod(sid, name, pic, remarks));
        }
        return list;
    }

    public static String h(String p0, String p1, String p2, String p3) {
        String url;
        if (p3.startsWith("http")) {
            url = p3;
        } else if (p3.startsWith("/")) {
            url = p0 + p3;
        } else {
            url = p0 + p1 + "/" + p3;
        }
        if (!TextUtils.isEmpty(p2)) {
            StringBuilder sb = new StringBuilder(url);
            String sep = url.contains("?") ? "&" : "?";
            sb.append(sep).append(p2);
            url = sb.toString();
        }
        return url;
    }

    public static Object[] i(int code, String body) {
        return new Object[]{
                Integer.valueOf(code),
                "text/plain",
                new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8))
        };
    }

    public static String j(String p0, String p1, String p2, String p3) {
        int start = p0.indexOf("URI=\"");
        if (start < 0) {
            return p0;
        }
        start += 5;
        int end = p0.indexOf('"', start);
        if (end < 0) {
            return p0;
        }
        String uri = p0.substring(start, end);
        StringBuilder sb = new StringBuilder();
        sb.append(p0.substring(0, start));
        sb.append(h(p1, p2, p3, uri));
        sb.append(p0.substring(end));
        return sb.toString();
    }

    public static Object[] proxy(Map<String, String> params) {
        String url = params.get("url");
        if (TextUtils.isEmpty(url)) {
            return i(0x190, "Missing url");
        }
        HashMap<String, String> headers = new HashMap<>();
        headers.put("User-Agent", "tdc.8260");

        String raw = params.get("raw");
        if ("1".equals(raw)) {
            String[] urls = a(url);
            if (urls.length == 0) {
                return i(0x190, "Missing url");
            }
            String realUrl = e(urls[0].trim());
            if (TextUtils.isEmpty(realUrl)) {
                return i(0x190, "Bad url");
            }
            headers.put("Referer", realUrl);
            try {
                Response resp = OkHttp.newCall(realUrl, headers);
                byte[] bytes = resp.body().bytes();
                resp.close();
                String contentType = realUrl.contains(".m3u8")
                        ? "application/vnd.apple.mpegurl" : "video/mp2t";
                return new Object[]{
                        Integer.valueOf(0xc8),
                        contentType,
                        new ByteArrayInputStream(bytes)
                };
            } catch (Exception e) {
                return i(0x1f6, "Fetch failed");
            }
        }

        String[] urls = a(url);
        int fmt;
        try {
            fmt = Integer.parseInt(params.get("fmt"));
        } catch (Exception e) {
            fmt = 2;
        }
        int[] durations = new int[urls.length];
        String dur = params.get("dur");
        if (!TextUtils.isEmpty(dur)) {
            String[] parts = dur.split(",");
            for (int i = 0; i < Math.min(parts.length, durations.length); i++) {
                try {
                    durations[i] = Integer.parseInt(parts[i].trim());
                } catch (Exception e) {
                }
            }
        }

        String contentType = "application/x-mpegURL";
        String versionLine = "#EXT-X-VERSION:3\n";
        String endList = "#EXT-X-ENDLIST\n";

        if (fmt == 1) {
            StringBuilder sb = new StringBuilder("#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-PLAYLIST-TYPE:VOD\n");
            int maxDur = 0xa;
            int count = 0;
            boolean first = true;
            for (int i = 0; i < urls.length; i++) {
                String u = e(urls[i].trim());
                if (u.isEmpty() || !u.startsWith("http")) {
                    continue;
                }
                int d = durations[i];
                if (d <= 0) {
                    d = 0x258;
                }
                if (d > maxDur) {
                    maxDur = d;
                }
                if (!first) {
                    sb.append("#EXT-X-DISCONTINUITY\n");
                }
                sb.append("#EXTINF:").append(d).append(".000,\n").append(u).append("\n");
                count++;
                first = false;
            }
            if (count == 0) {
                return i(0x1f6, "No valid MP4 URLs");
            }
            String result = sb.toString().replace(versionLine,
                    "#EXT-X-VERSION:3\n#EXT-X-TARGETDURATION:" + maxDur + "\n#EXT-X-MEDIA-SEQUENCE:0\n")
                    + endList;
            return new Object[]{
                    Integer.valueOf(0xc8),
                    contentType,
                    new ByteArrayInputStream(result.getBytes(StandardCharsets.UTF_8))
            };
        }

        StringBuilder sb = new StringBuilder("#EXTM3U\n#EXT-X-VERSION:3\n");
        int maxDur = 0xa;
        boolean allFailed = true;
        boolean first = true;
        for (int i = 0; i < urls.length; i++) {
            String u = e(urls[i].trim());
            if (u.isEmpty()) {
                continue;
            }
            headers.put("Referer", u);
            String content = OkHttp.string(u, headers);
            if (TextUtils.isEmpty(content)) {
                continue;
            }

            String baseHost = null;
            String basePath = "";
            String baseQuery = "";
            if (content.contains("#EXT-X-STREAM-INF")) {
                try {
                    URL urlObj = new URL(u);
                    baseHost = urlObj.getProtocol() + "://" + urlObj.getHost();
                    baseQuery = urlObj.getQuery();
                    if (baseQuery == null) {
                        baseQuery = "";
                    }
                    String path = urlObj.getPath();
                    if (path != null && path.contains("/")) {
                        basePath = path.substring(0, path.lastIndexOf('/'));
                    }
                    for (String line : content.split("\\r?\\n")) {
                        String trimmed = line.trim();
                        if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                            continue;
                        }
                        String newUrl = h(baseHost, basePath, baseQuery, trimmed);
                        content = OkHttp.string(newUrl, headers);
                        break;
                    }
                } catch (Exception e) {
                    continue;
                }
            }

            try {
                URL urlObj = new URL(u);
                baseHost = urlObj.getProtocol() + "://" + urlObj.getHost();
                baseQuery = urlObj.getQuery();
                if (baseQuery == null) {
                    baseQuery = "";
                }
                String path = urlObj.getPath();
                if (path != null && path.contains("/")) {
                    basePath = path.substring(0, path.lastIndexOf('/'));
                }
            } catch (Exception e) {
                continue;
            }

            if (!first) {
                sb.append("#EXT-X-DISCONTINUITY\n");
            }
            first = false;
            allFailed = false;

            for (String line : content.split("\\r?\\n")) {
                String trimmed = line.trim();
                String resolved;
                if (trimmed.startsWith("#EXT-X-TARGETDURATION:")) {
                    try {
                        int d = Integer.parseInt(trimmed.substring(21).trim());
                        if (d > maxDur) {
                            maxDur = d;
                        }
                    } catch (Exception e) {
                    }
                    continue;
                }
                if (trimmed.startsWith("#EXTM3U")
                        || trimmed.startsWith("#EXT-X-VERSION")
                        || trimmed.startsWith("#EXT-X-MEDIA-SEQUENCE")
                        || trimmed.startsWith("#EXT-X-ENDLIST")) {
                    continue;
                }
                if ((trimmed.startsWith("#EXT-X-KEY:") || trimmed.startsWith("#EXT-X-MAP:"))
                        && trimmed.contains("URI=\"")) {
                    resolved = j(trimmed, baseHost, basePath, baseQuery);
                } else if (trimmed.startsWith("#")) {
                    resolved = trimmed;
                } else if (trimmed.isEmpty()) {
                    continue;
                } else {
                    resolved = h(baseHost, basePath, baseQuery, trimmed);
                }
                sb.append(resolved).append("\n");
            }
        }
        if (allFailed) {
            return i(0x1f6, "All m3u8 segments failed");
        }
        String result = sb.toString().replace(versionLine,
                "#EXT-X-VERSION:3\n#EXT-X-TARGETDURATION:" + maxDur + "\n#EXT-X-MEDIA-SEQUENCE:0\n")
                + endList;
        return new Object[]{
                Integer.valueOf(0xc8),
                contentType,
                new ByteArrayInputStream(result.getBytes(StandardCharsets.UTF_8))
        };
    }

    public String b(String pid) {
        try {
            JSONObject json = new JSONObject();
            json.put("pid", pid);
            json.put("refer", "");
            JSONObject result = c(json, "/api/series2/episode/detail");
            if (result == null) {
                return "";
            }
            JSONObject playItem = result.optJSONObject("playItem");
            if (playItem == null) {
                return "";
            }
            Object sources = playItem.opt("sources");
            JSONArray arr;
            if (sources instanceof JSONArray) {
                arr = (JSONArray) sources;
            } else if (sources instanceof String) {
                try {
                    arr = new JSONArray((String) sources);
                } catch (Exception e) {
                    arr = null;
                }
            } else {
                arr = null;
            }
            if (arr == null || arr.length() == 0) {
                return "";
            }
            return arr.getJSONObject(0).optString("scid", "");
        } catch (Exception e) {
            return "";
        }
    }

    public JSONObject c(JSONObject json, String path) throws Exception {
        JSONObject result = d(json, path);
        if (result == null) {
            return null;
        }
        return HxqCrypto.decryptResponseData(result, session.uid);
    }

    public JSONObject d(JSONObject json, String path) throws Exception {
        String query;
        if (json.length() == 0) {
            query = "";
        } else {
            StringBuilder sb = new StringBuilder();
            java.util.Iterator<String> keys = json.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                if (sb.length() > 0) {
                    sb.append("&");
                }
                sb.append(Util.encode(key));
                sb.append("=");
                String val = String.valueOf(json.opt(key));
                sb.append(Util.encode(val));
            }
            query = sb.toString();
        }
        String url = host + path + (query.isEmpty() ? "" : "?" + query);
        Map<String, String> headers = HxqCrypto.buildHeaders(session, System.currentTimeMillis());
        String body = OkHttp.string(url, headers);
        if (TextUtils.isEmpty(body)) {
            return null;
        }
        JSONObject resp = new JSONObject(body);
        if (resp.optInt("rescode", -1) == 0) {
            return resp;
        }
        return null;
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        String stype;
        String cid;
        if (tid.contains("_")) {
            String[] parts = tid.split("_", 2);
            stype = parts[0];
            cid = parts[1];
        } else {
            stype = tid;
            cid = "";
        }
        if (extend != null && extend.containsKey("cid")) {
            cid = extend.get("cid");
        }
        JSONObject json = new JSONObject();
        json.put("stype", stype);
        json.put("page", pg);
        if (!cid.isEmpty() && !"-1".equals(cid)) {
            json.put("cid", cid);
        }
        if (extend != null) {
            if (extend.containsKey("sort")) {
                json.put("sort", extend.get("sort"));
            }
            if (extend.containsKey("year")) {
                json.put("year", extend.get("year"));
            }
        }
        JSONObject resp = c(json, "/api/series2/arrange/cate");
        if (resp == null) {
            return Result.error("分类加载失败");
        }
        JSONArray seriesList = resp.optJSONArray("seriesList");
        ArrayList<Vod> list = g(seriesList, 0);
        int page;
        try {
            page = Integer.parseInt(pg);
        } catch (Exception e) {
            page = 1;
        }
        int more = resp.optInt("more", 0);
        int pageCount = (more > 0) ? page + 1 : page;
        int limit = Math.max(list.size(), 1);
        int total = limit * pageCount;
        return Result.get().vod(list).page(page, pageCount, limit, total).string();
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        String sid = ids.get(0);
        JSONObject json = new JSONObject();
        json.put("sid", sid);
        JSONObject resp = c(json, "/api/series2/detail/normal");
        if (resp == null) {
            return Result.error("详情加载失败");
        }
        JSONObject series = resp.optJSONObject("series");
        JSONArray playItems = resp.optJSONArray("playItems");
        String firstPid = "";
        String firstScid = "";
        if (playItems != null && playItems.length() > 0) {
            firstPid = playItems.getJSONObject(0).optString("pid", "");
            if (!firstPid.isEmpty()) {
                firstScid = b(firstPid);
            }
        }
        ArrayList<String> playFrom = new ArrayList<>();
        ArrayList<String> playUrl = new ArrayList<>();
        for (int q = 0; q < 4; q++) {
            String[] quality = QUALITIES[q];
            StringBuilder urlBuilder = new StringBuilder();
            if (playItems != null) {
                for (int i = 0; i < playItems.length(); i++) {
                    JSONObject item = playItems.getJSONObject(i);
                    String pid = item.optString("pid", "");
                    if (pid.isEmpty()) {
                        continue;
                    }
                    String scid;
                    if (pid.equals(firstPid)) {
                        scid = firstScid;
                    } else {
                        scid = "";
                    }
                    String title = item.optString("title", "");
                    if (title.isEmpty()) {
                        title = "第" + (i + 1) + "集";
                    }
                    if (urlBuilder.length() > 0) {
                        urlBuilder.append("#");
                    }
                    urlBuilder.append(title).append("$").append(sid);
                    urlBuilder.append("|||").append(pid);
                    urlBuilder.append("|||").append(scid);
                    urlBuilder.append("|||").append(quality[1]);
                }
            }
            playFrom.add(quality[0]);
            String urlStr = urlBuilder.toString();
            String display;
            if (urlStr.isEmpty()) {
                display = "";
            } else {
                StringBuilder dispBuilder = new StringBuilder();
                String[] entries = urlStr.split("#");
                for (int i = 0; i < entries.length; i++) {
                    String entry = entries[i].trim();
                    if (entry.isEmpty()) {
                        continue;
                    }
                    if (dispBuilder.length() > 0) {
                        dispBuilder.append("#");
                    }
                    dispBuilder.append("第").append(i + 1).append("集 ").append(entry);
                }
                display = dispBuilder.toString();
            }
            playUrl.add(display);
        }
        Vod vod = new Vod(sid, series.optString("name", ""), f(series), "");
        int category = series.optInt("category", 0);
        String typeName;
        if (category == 1) {
            typeName = "韩剧";
        } else if (category == 2) {
            typeName = "综艺";
        } else if (category == 3) {
            typeName = "电影";
        } else {
            typeName = "";
        }
        vod.setTypeName(typeName);
        vod.setVodArea("韩国");
        vod.setVodContent(series.optString("shorthand", ""));
        int count = series.optInt("count", 0);
        if (count > 0) {
            boolean isFinished = series.optBoolean("isFinished", false);
            if (isFinished) {
                vod.setVodRemarks(count + "集全");
            } else {
                vod.setVodRemarks("更新至" + count + "集");
            }
        }
        vod.setVodPlayFrom(TextUtils.join("$$$", playFrom));
        vod.setVodPlayUrl(TextUtils.join("$$$", playUrl));
        return Result.string(vod);
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        JSONObject json = new JSONObject();
        json.put("stype", "1");
        json.put("page", "1");
        JSONObject resp = c(json, "/api/series2/arrange/cate");
        if (resp == null) {
            return Result.error("首页加载失败");
        }
        ArrayList<Class> classes = new ArrayList<>();
        LinkedHashMap<String, List<Filter>> filters = new LinkedHashMap<>();
        JSONArray sorts = resp.optJSONArray("sorts");
        JSONArray years = resp.optJSONArray("years");
        JSONArray groups = resp.optJSONArray("groups");
        if (groups != null) {
            for (int i = 0; i < groups.length(); i++) {
                JSONObject group = groups.getJSONObject(i);
                String stype = String.valueOf(group.opt("stype"));
                String name = group.optString("name", "");
                classes.add(new Class(stype, name));
                if (!filter) {
                    continue;
                }
                ArrayList<Filter> groupFilters = new ArrayList<>();
                JSONArray cates = group.optJSONArray("cates");
                if (cates != null && cates.length() > 0) {
                    ArrayList<Filter.Value> values = new ArrayList<>();
                    for (int j = 0; j < cates.length(); j++) {
                        JSONObject cate = cates.optJSONObject(j);
                        if (cate == null) {
                            continue;
                        }
                        String cateName = cate.optString("name", "");
                        if (cateName.isEmpty()) {
                            continue;
                        }
                        if ("伦理".equals(cateName)) {
                            continue;
                        }
                        values.add(new Filter.Value(cateName, String.valueOf(cate.opt("value"))));
                    }
                    if (!values.isEmpty()) {
                        groupFilters.add(new Filter("cid", "类型", values));
                    }
                }
                ArrayList<Filter> sortFilters = new ArrayList<>();
                if (sorts != null && sorts.length() > 0) {
                    ArrayList<Filter.Value> sortValues = new ArrayList<>();
                    for (int j = 0; j < sorts.length(); j++) {
                        JSONObject sort = sorts.optJSONObject(j);
                        if (sort == null) {
                            continue;
                        }
                        sortValues.add(new Filter.Value(sort.optString("name"), sort.optString("value")));
                    }
                    if (!sortValues.isEmpty()) {
                        sortFilters.add(new Filter("sort", "排序", sortValues));
                    }
                }
                if (years != null && years.length() > 0) {
                    ArrayList<Filter.Value> yearValues = new ArrayList<>();
                    for (int j = 0; j < years.length(); j++) {
                        JSONObject year = years.optJSONObject(j);
                        if (year == null) {
                            continue;
                        }
                        yearValues.add(new Filter.Value(year.optString("name"), year.optString("value")));
                    }
                    if (!yearValues.isEmpty()) {
                        sortFilters.add(new Filter("year", "年份", yearValues));
                    }
                }
                groupFilters.addAll(sortFilters);
                if (!groupFilters.isEmpty()) {
                    filters.put(stype, groupFilters);
                }
            }
        }
        JSONArray seriesList = resp.optJSONArray("seriesList");
        return Result.string(classes, g(seriesList, 0x14), filters);
    }

    @Override
    public void init(Context context, String extend) {
        try {
            if (extend != null) {
                String trimmed = extend.trim();
                if (trimmed.startsWith("{")) {
                    JSONObject json = new JSONObject(trimmed);
                    host = json.optString("api", "https://hxqapi.hiyun.tv");
                } else if (trimmed.startsWith("http")) {
                    host = trimmed;
                }
            }
            if (host.endsWith("/")) {
                host = host.substring(0, host.length() - 1);
            }
            session = HxqCrypto.createSession();
            d(new JSONObject(), "/api/common/configs");
        } catch (Exception e) {
        }
    }

    @Override
    public String playerContent(String flag, String id, List<String> headers) throws Exception {
        if (TextUtils.isEmpty(id) || !id.contains("|||")) {
            return Result.error("播放参数错误");
        }
        String[] parts = id.split("\\|\\|\\|", -1);
        String pid = (parts.length > 1) ? parts[1] : "";
        String scid = (parts.length > 2) ? parts[2] : "";
        String quality = (parts.length > 3) ? parts[3] : "10";
        if (TextUtils.isEmpty(pid)) {
            return Result.error("缺少 pid");
        }
        if (TextUtils.isEmpty(scid)) {
            scid = b(pid);
        }
        if (TextUtils.isEmpty(scid)) {
            return Result.error("缺少 scid");
        }

        String cachedToken = tokenCache.get(pid);
        Long cachedExpire = expireCache.get(pid);
        String token;
        if (!TextUtils.isEmpty(cachedToken) && cachedExpire != null
                && System.currentTimeMillis() < cachedExpire) {
            token = cachedToken;
        } else {
            token = fetchRewardToken(pid);
        }

        if (TextUtils.isEmpty(token)) {
            return Result.error("获取激励 token 失败(需 aps/Guard)");
        }

        long ts = System.currentTimeMillis() / 1000;
        String re = TextUtils.isEmpty(token) ? "1" : "0";
        String sign = HxqCrypto.buildRslvQuerySignGuard(session, pid, scid, quality, token, ts, re);

        StringBuilder urlBuilder = new StringBuilder(host);
        urlBuilder.append("/api/series/rslvV4?version=6.8&uuid=");
        urlBuilder.append(Util.encode(session.devId));
        urlBuilder.append("&t=").append(ts);
        urlBuilder.append("&sq=").append(Util.encode(quality));
        urlBuilder.append("&scid=").append(Util.encode(scid));
        urlBuilder.append("&re=").append(re);
        urlBuilder.append("&pid=").append(Util.encode(pid));
        urlBuilder.append("&dt=android");
        if (!TextUtils.isEmpty(token)) {
            urlBuilder.append("&ttk=").append(Util.encode(token));
        }
        urlBuilder.append("&sign=").append(sign);

        Map<String, String> reqHeaders = HxqCrypto.buildHeaders(session, System.currentTimeMillis());
        String body = OkHttp.string(urlBuilder.toString(), reqHeaders);
        JSONObject resp = null;
        if (!TextUtils.isEmpty(body)) {
            resp = new JSONObject(body);
        }
        if (resp == null || resp.optInt("rescode", -1) != 0) {
            String rescode = (resp == null) ? "null" : String.valueOf(resp.optInt("rescode"));
            return Result.error("解析失败(rescode=" + rescode + ")");
        }
        JSONArray datas = resp.optJSONArray("datas");
        if (datas == null || datas.length() == 0) {
            return Result.error("无播放数据");
        }
        int format = resp.optInt("format", 2);
        ArrayList<String> urlList = new ArrayList<>();
        ArrayList<Integer> durList = new ArrayList<>();
        for (int i = 0; i < datas.length(); i++) {
            JSONObject data = datas.getJSONObject(i);
            String seg = data.optString("url", "");
            if (seg.isEmpty()) {
                continue;
            }
            String decoded = HxqCrypto.decodePlaySegment(seg, session.uid);
            decoded = e(decoded);
            if (decoded.isEmpty()) {
                continue;
            }
            urlList.add(decoded);
            durList.add(Integer.valueOf(data.optInt("duration", 0)));
        }
        if (urlList.isEmpty()) {
            return Result.error("播放地址为空(分片解密失败)");
        }

        String firstUrl = urlList.get(0);
        HashMap<String, String> playHeaders = new HashMap<>();
        playHeaders.put("User-Agent", "tdc.8260");
        if (!TextUtils.isEmpty(firstUrl)) {
            playHeaders.put("Referer", firstUrl);
        }

        String proxyUrl = Proxy.getUrl();
        if (proxyUrl.contains(":-1")) {
            return Result.get().parse(0).url(firstUrl).header(playHeaders).string();
        }
        if (format == 1 && urlList.size() == 1) {
            return Result.get().parse(0).url(firstUrl).header(playHeaders).string();
        }

        String joined = TextUtils.join("|||", urlList);
        String encoded = Base64.encodeToString(joined.getBytes(StandardCharsets.UTF_8), 0xb);
        StringBuilder durSb = new StringBuilder();
        for (int i = 0; i < durList.size(); i++) {
            if (i > 0) {
                durSb.append(",");
            }
            durSb.append(durList.get(i));
        }
        String finalUrl = proxyUrl + "?do=hxq&url=" + encoded + "&fmt=" + format + "&dur=" + durSb;
        Result result = Result.get().parse(0).url(finalUrl).header(playHeaders);
        if (format == 2) {
            result.m3u8();
        }
        return result.string();
    }

    private String fetchRewardToken(String pid) throws Exception {
        Map<String, String> headers = HxqCrypto.buildHeaders(session, System.currentTimeMillis());
        String rewardUrl = host + "/api/carp/reward/v2?scene=ad_series_play";
        String body = OkHttp.string(rewardUrl, headers);
        if (TextUtils.isEmpty(body)) {
            return "";
        }
        JSONObject resp = new JSONObject(body);
        if (resp.optInt("rescode", -1) != 0) {
            return "";
        }
        String traceId = resp.optString("traceId", "");
        if (TextUtils.isEmpty(traceId)) {
            JSONObject decrypted = HxqCrypto.decryptResponseData(resp, session.uid);
            if (decrypted != null) {
                traceId = decrypted.optString("traceId", "");
            }
        }
        if (TextUtils.isEmpty(traceId)) {
            return "";
        }
        long ts = resp.optLong("ts", System.currentTimeMillis());
        String rewardBody = HxqCrypto.buildRewardBody(pid, traceId, "ad_series_play", ts);
        HashMap<String, String> postHeaders = new HashMap<>(headers);
        postHeaders.put("Content-Type", "application/json");
        postHeaders.put("aps", HxqCrypto.buildRewardAps(rewardBody));
        String postUrl = host + "/api/carp/reward/rp/v2";
        String postResp = OkHttp.post(postUrl, rewardBody, postHeaders);
        if (TextUtils.isEmpty(postResp)) {
            return "";
        }
        JSONObject postJson = new JSONObject(postResp);
        if (postJson.optInt("rescode", -1) != 0) {
            return "";
        }
        Object dataObj = postJson.opt("data");
        JSONObject dataJson = null;
        if (dataObj instanceof String) {
            dataJson = HxqCrypto.decryptResponseData(postJson, session.uid);
        }
        if (dataJson == null) {
            return "";
        }
        JSONObject tokenInfo = dataJson.optJSONObject("rewardTokenInfo");
        if (tokenInfo == null) {
            return "";
        }
        String token = tokenInfo.optString("token", "");
        if (TextUtils.isEmpty(token)) {
            return "";
        }
        // expireSec: 优先 expireTime，其次 expires，最后默认 0x5460
        int expires = tokenInfo.optInt("expires", 0x5460);
        int expireSec = tokenInfo.optInt("expireTime", expires);
        tokenCache.put(pid, token);
        long expireTime = System.currentTimeMillis() + expireSec * 1000L - 0xea60;
        expireCache.put(pid, expireTime);
        return token;
    }

    public Object[] proxyLocal(Map<String, String> params) {
        return proxy(params);
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        JSONObject json = new JSONObject();
        json.put("srefer", "search");
        json.put("type", "0");
        json.put("page", "1");
        json.put("k", key);
        JSONObject resp = c(json, "/api/search/s4");
        if (resp == null) {
            return Result.error("搜索无结果");
        }
        JSONArray seriesList = resp.optJSONArray("seriesList");
        return Result.string(g(seriesList, 0));
    }
}
