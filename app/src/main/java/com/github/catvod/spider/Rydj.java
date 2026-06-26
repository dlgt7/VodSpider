package com.github.catvod.spider;

import android.text.TextUtils;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Json;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Rydj extends Spider {

    private static final String BASE_URL = "https://xifan-api-cn.youlishipin.com";
    private static final Map<String, String> HEADERS = new HashMap<String, String>() {{
        put("User-Agent", "Mozilla/5.0 (Linux; Android 8.0; DUK-AL20 Build/HUAWEIDUK-AL20; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/57.0.2987.132 MQQBrowser/6.2 TBS/044353 Mobile Safari/537.36 MicroMessenger/6.7.3.1360(0x26070333) NetType/WIFI Language/zh_CN Process/tools");
    }};

    private static final Pattern TAG_PATTERN = Pattern.compile("<tag>|</tag>");

    private static final String SESSION = "eyJpbmZvIjp7InVpZCI6IiIsInJ0IjoiMTc0MDY2ODk4NiIsInVuIjoiT1BHX2U5ODQ4NTgzZmM4ZjQzZTJhZjc5ZTcxNjRmZTE5Y2JjIiwiZnQiOiIxNzQwNjY4OTg2In19";
    private static final String FEEDS_SESSION = "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJ1dHlwIjowLCJidWlkIjoxNjM0MDU3ODE4OTgxNDk5OTA0LCJhdWQiOiJkcmFtYSIsInZlciI6MiwicmF0IjoxNzQwNjY4OTg2LCJ1bm0iOiJPUEdfZTk4NDg1ODNmYzhmNDNlMmFmNzllNzE2NGZlMTljYmMiLCJpZCI6ImVhZGE1NmEyZWEzYTE0YmMwMzE3ZDc2ZmVjODJjNzc3IiwiZXhwIjoxNzQxMjczNzg2LCJkYyI6ImJqaHQifQ.IwuI0gK077RF4G10JRxgxx4GCG502vR8Z0W9EV4kd-c";

    private final Gson gson = new Gson();

    private long getTimestamp() {
        return System.currentTimeMillis() / 1000;
    }

    private String buildUrl(String path, Map<String, String> params) {
        StringBuilder sb = new StringBuilder(BASE_URL + path);
        if (params != null && !params.isEmpty()) {
            sb.append("?");
            for (Map.Entry<String, String> entry : params.entrySet()) {
                sb.append(entry.getKey()).append("=").append(entry.getValue()).append("&");
            }
            sb.deleteCharAt(sb.length() - 1);
        }
        return sb.toString();
    }

    private JsonObject fetchResult(String url) {
        String body = OkHttp.string(url, HEADERS);
        if (TextUtils.isEmpty(body)) return new JsonObject();
        try {
            JsonElement element = Json.parse(body);
            if (element.isJsonObject() && element.getAsJsonObject().has("result")) {
                return element.getAsJsonObject().getAsJsonObject("result");
            }
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        return new JsonObject();
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        List<Vod> videos = new ArrayList<>();
        List<Class> classes = new ArrayList<>();

        Map<String, String> params = new HashMap<>();
        params.put("reqType", "duanjuCategory");
        params.put("version", "2001001");
        params.put("androidVersionCode", "28");

        JsonObject data = fetchResult(buildUrl("/xifan/drama/portalPage", params));
        JsonArray elements = data.getAsJsonArray("elements");
        if (elements != null && elements.size() > 0) {
            JsonArray contents = elements.get(0).getAsJsonObject().getAsJsonArray("contents");
            if (contents != null) {
                for (JsonElement item : contents) {
                    JsonObject vo = item.getAsJsonObject().getAsJsonObject("categoryItemVo");
                    if (vo == null || vo.has("subCategories")) continue;

                    String oppo = vo.get("oppoCategory").getAsString();
                    String cid = vo.get("categoryId").getAsString();
                    String typeId = oppo + "@" + cid;

                    classes.add(new Class(typeId, oppo));
                }
            }
        }

        return Result.string(classes, videos);
    }

    @Override
    public String homeVideoContent() throws Exception {
        return categoryContent("", "1", false, null);
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        List<Vod> videos = new ArrayList<>();

        int page = Integer.parseInt(pg);
        int offset = (page - 1) * 30;
        long ts = getTimestamp();

        Map<String, String> params = new HashMap<>();
        params.put("reqType", "aggregationPage");
        params.put("offset", String.valueOf(offset));
        params.put("quickEngineVersion", "-1");
        params.put("scene", "");
        params.put("categoryVersion", "1");
        params.put("density", "1.5");
        params.put("pageID", "page_theater");
        params.put("version", "2001001");
        params.put("androidVersionCode", "28");
        params.put("requestId", ts + "d4aa487d53e646c2");
        params.put("appId", "drama");
        params.put("teenMode", "false");
        params.put("userBaseMode", "false");
        params.put("session", SESSION);
        params.put("feedssession", FEEDS_SESSION);

        if (!TextUtils.isEmpty(tid)) {
            String[] parts = tid.split("@");
            params.put("categoryId", parts[1]);
            params.put("categoryNames", parts[0]);
        } else {
            params.put("categoryNames", "");
            params.put("categoryId", "");
        }

        JsonObject data = fetchResult(buildUrl("/xifan/drama/portalPage", params));
        JsonArray elements = data.getAsJsonArray("elements");

        if (elements != null) {
            for (JsonElement elem : elements) {
                JsonArray contents = elem.getAsJsonObject().getAsJsonArray("contents");
                if (contents == null) continue;
                for (JsonElement item : contents) {
                    JsonObject duanju = item.getAsJsonObject().getAsJsonObject("duanjuVo");
                    if (duanju == null) continue;

                    String name = duanju.get("title").getAsString();
                    String id = duanju.get("duanjuId").getAsString();
                    String source = duanju.get("source").getAsString();
                    String pic = duanju.get("coverImageUrl").getAsString();

                    videos.add(new Vod(id + "#" + source, name, pic, "热播推荐"));
                }
            }
        }

        return Result.get().page(page, 9999, 90, 999999).vod(videos).string();
    }

    @Override
    public String detailContent(List<String> ids) {
        try {
            String id = ids.get(0);
            String[] parts = id.split("#");
            long ts = getTimestamp();

            Map<String, String> params = new HashMap<>();
            params.put("duanjuId", parts[0]);
            params.put("source", parts[1]);
            params.put("openFrom", "homescreen");
            params.put("type", "");
            params.put("pageID", "page_inner_flow");
            params.put("density", "1.5");
            params.put("version", "2001001");
            params.put("androidVersionCode", "28");
            params.put("requestId", ts + "aa498144140ef297");
            params.put("appId", "drama");
            params.put("teenMode", "false");
            params.put("userBaseMode", "false");
            params.put("session", SESSION);
            params.put("feedssession", FEEDS_SESSION);

            JsonObject data = fetchResult(buildUrl("/xifan/drama/getDuanjuInfo", params));

            String name = data.has("title") ? data.get("title").getAsString() : id;
            String desc = data.has("desc") ? data.get("desc").getAsString() : "暂无简介";
            JsonArray episodes = data.getAsJsonArray("episodeList");

            StringBuilder playUrl = new StringBuilder();
            if (episodes != null) {
                for (JsonElement ep : episodes) {
                    JsonObject obj = ep.getAsJsonObject();
                    String epName = obj.get("index").getAsString();
                    String url = obj.get("playUrl").getAsString();
                    playUrl.append(epName).append("$").append(url).append("#");
                }
                if (playUrl.length() > 0) {
                    playUrl.deleteCharAt(playUrl.length() - 1);
                }
            }

            Vod vod = new Vod();
            vod.setVodId(id);
            vod.setVodName(name);
            vod.setVodContent("剧情简介：" + desc);
            vod.setVodPlayFrom("如意短剧");
            vod.setVodPlayUrl(playUrl.toString());

            return Result.string(vod);
        } catch (Exception e) {
            return Result.error("获取详情失败").toString();
        }
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) {
        return Result.get().url(id).parse(0).header(HEADERS).string();
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        return searchContent(key, quick, "1");
    }

    @Override
    public String searchContent(String key, boolean quick, String pg) throws Exception {
        List<Vod> videos = new ArrayList<>();

        int page = Integer.parseInt(pg);
        long ts = getTimestamp();

        Map<String, String> params = new HashMap<>();
        params.put("keyword", key);
        params.put("pageIndex", String.valueOf(page));
        params.put("version", "2001001");
        params.put("androidVersionCode", "28");
        params.put("requestId", ts + "ea3a14bc0317d76f");
        params.put("appId", "drama");
        params.put("teenMode", "false");
        params.put("userBaseMode", "false");
        params.put("session", SESSION);
        params.put("feedssession", FEEDS_SESSION);

        JsonObject data = fetchResult(buildUrl("/xifan/search/getSearchList", params));
        JsonArray elements = data.getAsJsonArray("elements");

        if (elements != null) {
            for (JsonElement elem : elements) {
                JsonArray contents = elem.getAsJsonObject().getAsJsonArray("contents");
                if (contents == null) continue;
                for (JsonElement item : contents) {
                    JsonObject duanju = item.getAsJsonObject().getAsJsonObject("duanjuVo");
                    if (duanju == null) continue;

                    String name = duanju.get("title").getAsString();
                    Matcher matcher = TAG_PATTERN.matcher(name);
                    name = matcher.replaceAll("");

                    String id = duanju.get("duanjuId").getAsString();
                    String source = duanju.get("source").getAsString();
                    String pic = duanju.get("coverImageUrl").getAsString();

                    videos.add(new Vod(id + "#" + source, name, pic, "搜索结果"));
                }
            }
        }

        return Result.get().page(page, 9999, 90, 999999).vod(videos).string();
    }
}
