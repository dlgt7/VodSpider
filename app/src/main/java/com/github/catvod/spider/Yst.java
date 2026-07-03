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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Yst Spider - 北京时间视频源
 * JSONP API型 Spider，处理 JSONP 格式响应
 */
public class Yst extends Spider {

    // DEX 同名不同类型字段重命名
    private static final Pattern JSONP_PATTERN = Pattern.compile("^[^(]+\\((.*)\\)\\s*;?\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern TITLE_PATTERN = Pattern.compile("<title>([^<]+)</title>", Pattern.CASE_INSENSITIVE);

    // 年份映射数组（2025-2018 对应 list_id）
    // 使用简单的内部类替代 merge/g/e
    private static final YearListId[] YEAR_LIST_ID_MAP = new YearListId[]{
            new YearListId("2025", "btv_08da67cea600bf3c78973427bfaba12d_s0_2025"),
            new YearListId("2024", "btv_08da67cea600bf3c78973427bfaba12d_s0_2024"),
            new YearListId("2023", "btv_08da67cea600bf3c78973427bfaba12d_s0_2023"),
            new YearListId("2022", "btv_08da67cea600bf3c78973427bfaba12d_s0_2022"),
            new YearListId("2021", "btv_08da67cea600bf3c78973427bfaba12d_s0_2021"),
            new YearListId("2020", "btv_08da67cea600bf3c78973427bfaba12d_s0_2020"),
            new YearListId("2019", "btv_08da67cea600bf3c78973427bfaba12d_s0_2019"),
            new YearListId("2018", "btv_08da67cea600bf3c78973427bfaba12d_s0_2018")
    };

    // 内部类：年份与 list_id 映射（替代 merge/g/e）
    private static class YearListId {
        final String year;  // 对应 merge/g/e.a
        final String listId; // 对应 merge/g/e.b

        YearListId(String year, String listId) {
            this.year = year;
            this.listId = listId;
        }
    }

    public Yst() {
        super();
    }

    // merge/Z/d.t 等价实现：构建 headers HashMap
    private static HashMap<String, String> buildHeaders() {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("User-Agent", "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 Chrome/120.0.0.0 Mobile Safari/537.36");
        headers.put("Referer", "https://www.btime.com/");
        return headers;
    }

    // merge/Z/d.z 等价实现：拼接 URL
    private static String concatUrl(String base, String suffix) {
        return base + suffix;
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        ArrayList<Class> classes = new ArrayList<>();
        for (int i = 0; i < YEAR_LIST_ID_MAP.length; i++) {
            YearListId item = YEAR_LIST_ID_MAP[i];
            // tid 格式：listId, type_id: "2-{index}-H", type_name: year
            String typeId = "2-" + i + "-H";
            classes.add(new Class(item.listId, item.year, typeId));
        }
        // merge/Z/d.o 等价：返回首页分类列表
        return Result.string(classes, new ArrayList<>());
    }

    @Override
    public String homeVideoContent() throws Exception {
        YearListId first = YEAR_LIST_ID_MAP[0];
        ArrayList<Vod> list = fetchCategoryContent(first.listId, "1");
        return Result.string(list);
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        String listId = resolveListId(tid);
        ArrayList<Vod> list = fetchCategoryContent(listId, pg);
        return Result.string(list);
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        String vodId = ids.get(0);

        // 提取真正的 ID（如果是完整 URL 则提取最后一部分）
        String realId = extractRealId(vodId);

        // 1. 拉取详情 HTML 页
        String detailUrl = vodId.startsWith("http") ? vodId : concatUrl("https://item.btime.com/", realId);
        String html = OkHttp.string(detailUrl, buildHeaders());

        // 2. 拉取 JSONP 播放信息
        String jsonpUrl = concatUrl("https://app.api.btime.com/video/play?callback=jQuery1&id=", realId);
        String jsonpResp = OkHttp.string(jsonpUrl, buildHeaders());
        String jsonStr = extractJsonp(jsonpResp);

        // 3. 提取标题（从 HTML）
        String title = extractTitle(html);

        // 4. 提取封面（从 HTML）
        String cover = extractCover(html);

        // 5. 提取简介（从 JSON）
        String summary = extractSummary(jsonStr);

        // 6. 提取播放列表
        ArrayList<String> playList = parsePlayList(jsonStr);

        // 7. 构建 Vod 对象
        Vod vod = new Vod();
        vod.setVodId(vodId);
        vod.setVodName(title);
        vod.setVodPic(cover);
        vod.setVodContent(summary);
        vod.setVodPlayFrom("默认");
        vod.setVodPlayUrl(TextUtils.join("#", playList));

        return Result.string(vod);
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        String playUrl = cleanUrl(id);

        // 如果是 JSONP URL，则拉取并解析
        if (playUrl.contains("app.api.btime.com/video/play")) {
            String jsonpResp = OkHttp.string(playUrl, buildHeaders());
            String jsonStr = extractJsonp(jsonpResp);
            ArrayList<String> playList = parsePlayList(jsonStr);
            if (!playList.isEmpty()) {
                // 取第一个播放地址，去掉前缀 "xxx$"
                String first = playList.get(0);
                int dollarIdx = first.indexOf('$');
                if (dollarIdx >= 0) {
                    playUrl = first.substring(dollarIdx + 1);
                }
            }
        }

        HashMap<String, String> headers = buildHeaders();
        headers.put("Referer", "https://www.btime.com/");

        return Result.get().url(playUrl).header(headers).string();
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        return searchContent(key, quick, "1");
    }

    @Override
    public String searchContent(String key, boolean quick, String pg) throws Exception {
        String keyword = key == null ? "" : key.trim();
        if (TextUtils.isEmpty(keyword)) {
            return Result.string(new ArrayList<>());
        }

        // 解析页码
        int page;
        try {
            page = Math.max(1, Integer.parseInt(pg));
        } catch (Exception e) {
            page = 1;
        }

        LinkedHashMap<String, Vod> resultMap = new LinkedHashMap<>();
        int maxResults = 40;

        // 从指定页开始，遍历年份列表
        int startIndex = Math.min(YEAR_LIST_ID_MAP.length - 1, (page - 1) * 2);
        for (int i = startIndex; i < YEAR_LIST_ID_MAP.length && resultMap.size() < maxResults; i++) {
            YearListId yearItem = YEAR_LIST_ID_MAP[i];
            // 每个年份最多拉取 8 页
            for (int refresh = 1; refresh <= 8 && resultMap.size() < maxResults; refresh++) {
                ArrayList<Vod> pageList = fetchCategoryContent(yearItem.listId, String.valueOf(refresh));
                for (Vod vod : pageList) {
                    if (resultMap.size() >= maxResults) break;
                    if (!TextUtils.isEmpty(vod.getVodName()) && vod.getVodName().contains(keyword)) {
                        // 用 URL 作为 key 去重
                        String uniqueKey = TextUtils.isEmpty(vod.getVodId()) ? vod.getVodName() : vod.getVodId();
                        resultMap.put(uniqueKey, vod);
                    }
                }
            }
        }

        ArrayList<Vod> result = new ArrayList<>(resultMap.values());
        return Result.string(result);
    }

    // ========== 私有辅助方法 ==========

    /**
     * 解析 tid 获取 list_id
     * tid 可能是：
     * 1. 空 -> 使用第一个年份
     * 2. "btv_xxx" -> 直接使用
     * 3. "2-{index}-H" -> 解析 index 使用对应年份
     * 4. "{index}" -> 直接用索引
     * 5. 年份字符串 "2025" -> 查找对应 list_id
     */
    private String resolveListId(String tid) {
        if (TextUtils.isEmpty(tid)) {
            return YEAR_LIST_ID_MAP[0].listId;
        }

        tid = tid.trim();

        // 如果已经是 btv_xxx 格式，直接返回
        if (tid.startsWith("btv_")) {
            return tid;
        }

        // 尝试解析 "2-{index}-H" 格式
        Pattern pattern2 = Pattern.compile("^([23])-(\\d+)(?:-H)?$");
        Matcher matcher = pattern2.matcher(tid);
        if (matcher.matches()) {
            int index = Integer.parseInt(matcher.group(2));
            if (index >= 0 && index < YEAR_LIST_ID_MAP.length) {
                return YEAR_LIST_ID_MAP[index].listId;
            }
        }

        // 尝试纯数字索引
        if (tid.matches("\\d+")) {
            int index = Integer.parseInt(tid);
            if (index >= 0 && index < YEAR_LIST_ID_MAP.length) {
                return YEAR_LIST_ID_MAP[index].listId;
            }
        }

        // 查找年份匹配或 list_id 匹配
        for (YearListId item : YEAR_LIST_ID_MAP) {
            if (tid.equals(item.listId) || tid.equals(item.year)) {
                return item.listId;
            }
        }

        // 尝试年份模式 "20\\d{2}"
        if (tid.matches("20\\d{2}")) {
            for (YearListId item : YEAR_LIST_ID_MAP) {
                if (item.year.equals(tid) || item.listId.endsWith("_s0_" + tid)) {
                    return item.listId;
                }
            }
        }

        // 默认返回第一个
        return YEAR_LIST_ID_MAP[0].listId;
    }

    /**
     * 拉取分类内容列表
     */
    private ArrayList<Vod> fetchCategoryContent(String listId, String pg) throws Exception {
        String refresh = TextUtils.isEmpty(pg) ? "1" : pg;
        StringBuilder urlBuilder = new StringBuilder("https://pc.api.btime.com/btimeweb/infoFlow?list_id=");
        urlBuilder.append(URLEncoder.encode(listId, "UTF-8"));
        urlBuilder.append("&refresh=");
        urlBuilder.append(URLEncoder.encode(refresh, "UTF-8"));
        urlBuilder.append("&count=20&expands=pageinfo");

        String jsonStr = OkHttp.string(urlBuilder.toString(), buildHeaders());
        JSONObject root = new JSONObject(jsonStr);

        ArrayList<Vod> result = new ArrayList<>();
        JSONObject data = root.optJSONObject("data");
        if (data == null) {
            return result;
        }

        JSONArray list = data.optJSONArray("list");
        if (list == null) {
            return result;
        }

        for (int i = 0; i < list.length(); i++) {
            JSONObject item = list.optJSONObject(i);
            if (item == null) continue;
            Vod vod = buildVodFromJson(item);
            if (vod != null) {
                result.add(vod);
            }
        }

        return result;
    }

    /**
     * 从 JSON 构建 Vod 对象
     */
    private Vod buildVodFromJson(JSONObject json) {
        // 优先提取 URL
        String url = cleanUrl(json.optString("url"));
        if (TextUtils.isEmpty(url)) {
            String gid = json.optString("gid");
            if (!TextUtils.isEmpty(gid)) {
                url = concatUrl("https://item.btime.com/", gid);
            }
        }
        if (TextUtils.isEmpty(url)) {
            return null;
        }

        // 提取标题
        JSONObject dataObj = json.optJSONObject("data");
        String title;
        if (dataObj != null) {
            title = dataObj.optString("title");
        } else {
            title = json.optString("title");
        }

        // 提取封面
        String cover = "";
        if (dataObj != null) {
            JSONArray covers = dataObj.optJSONArray("covers");
            if (covers != null && covers.length() > 0) {
                cover = cleanUrl(covers.optString(0));
            }
        }

        // 提取时长作为备注
        String duration = "";
        if (dataObj != null) {
            duration = dataObj.optString("duration");
        }

        // 如果标题为空，用 URL 代替
        if (TextUtils.isEmpty(title)) {
            title = url;
        }

        return new Vod(url, title, cover, duration);
    }

    /**
     * 提取真正的 ID（从完整 URL）
     */
    private String extractRealId(String vodId) {
        if (TextUtils.isEmpty(vodId)) {
            return "";
        }

        String id = vodId.trim();
        if (id.contains("item.btime.com/")) {
            int lastSlash = id.lastIndexOf('/');
            if (lastSlash >= 0) {
                id = id.substring(lastSlash + 1);
            }
        }

        // 去掉查询参数
        id = id.replaceAll("[?#].*", "");
        return id;
    }

    /**
     * 从 JSONP 响应提取 JSON 内容
     */
    private String extractJsonp(String jsonpResp) {
        if (jsonpResp == null) {
            return "";
        }
        Matcher matcher = JSONP_PATTERN.matcher(jsonpResp.trim());
        if (matcher.find()) {
            return matcher.group(1);
        }
        return jsonpResp;
    }

    /**
     * 从 HTML 提取标题
     */
    private String extractTitle(String html) {
        if (TextUtils.isEmpty(html)) {
            return "";
        }

        Matcher matcher = TITLE_PATTERN.matcher(html);
        if (!matcher.find()) {
            return "";
        }

        String title = matcher.group(1)
                .replaceAll("\\s+", " ")
                .trim();

        // 去掉 " - xxx" 后缀
        int dashIdx = title.indexOf('-');
        if (dashIdx > 0) {
            title = title.substring(0, dashIdx).trim();
        }

        // 去掉 " _ xxx" 后缀
        int underscoreIdx = title.indexOf('_');
        if (underscoreIdx > 0) {
            title = title.substring(0, underscoreIdx).trim();
        }

        return TextUtils.isEmpty(title) ? "养生堂" : title;
    }

    /**
     * 从 HTML 提取封面
     */
    private String extractCover(String html) {
        if (TextUtils.isEmpty(html)) {
            return "";
        }

        // 尝试 property="og:image"
        Pattern ogImagePattern = Pattern.compile("property=\"og:image\"\\s+content=\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);
        Matcher matcher = ogImagePattern.matcher(html);
        if (matcher.find()) {
            return cleanUrl(matcher.group(1));
        }

        // 回退 video_covers 字段
        Pattern coversPattern = Pattern.compile("video_covers\"\\s*:\\s*\"([^\"]+)");
        matcher = coversPattern.matcher(html);
        if (matcher.find()) {
            return cleanUrl(matcher.group(1));
        }

        return "";
    }

    /**
     * 从 JSON 提取简介
     */
    private String extractSummary(String jsonStr) {
        if (TextUtils.isEmpty(jsonStr)) {
            return "";
        }

        try {
            JSONObject root = new JSONObject(jsonStr);
            JSONObject data = root.optJSONObject("data");
            if (data != null) {
                String summary = data.optString("summary");
                if (!TextUtils.isEmpty(summary)) {
                    return summary;
                }
            }
        } catch (Exception e) {
            // ignore
        }

        return "";
    }

    /**
     * 解析播放列表
     */
    private ArrayList<String> parsePlayList(String jsonStr) {
        ArrayList<String> result = new ArrayList<>();
        if (TextUtils.isEmpty(jsonStr)) {
            return result;
        }

        try {
            JSONObject root = new JSONObject(jsonStr);
            JSONObject data = root.optJSONObject("data");
            if (data == null) {
                return result;
            }

            // 优先 video_streams
            JSONArray videoStreams = data.optJSONArray("video_streams");
            addPlayItems(result, videoStreams);

            // 如果为空，尝试 audio_streams
            if (result.isEmpty()) {
                JSONArray audioStreams = data.optJSONArray("audio_streams");
                addPlayItems(result, audioStreams);
            }
        } catch (Exception e) {
            // ignore
        }

        return result;
    }

    /**
     * 添加播放项到列表
     */
    private void addPlayItems(ArrayList<String> list, JSONArray array) {
        if (array == null) {
            return;
        }

        for (int i = 0; i < array.length(); i++) {
            Object item = array.opt(i);
            if (item instanceof JSONArray) {
                JSONArray nested = (JSONArray) item;
                for (int j = 0; j < nested.length(); j++) {
                    JSONObject obj = nested.optJSONObject(j);
                    addSinglePlayItem(list, obj);
                }
            } else if (item instanceof JSONObject) {
                JSONObject obj = (JSONObject) item;
                addSinglePlayItem(list, obj);
            }
        }
    }

    /**
     * 添加单个播放项
     */
    private void addSinglePlayItem(ArrayList<String> list, JSONObject obj) {
        if (obj == null) {
            return;
        }

        String streamUrl = cleanUrl(obj.optString("stream_url"));
        if (TextUtils.isEmpty(streamUrl)) {
            return;
        }

        // 提取备注
        String remark = obj.optString("stream_vbt");
        if (TextUtils.isEmpty(remark)) {
            remark = obj.optString("duration");
        }
        if (TextUtils.isEmpty(remark)) {
            remark = "正片";
        }

        // 格式：remark$url
        list.add(remark + "$" + streamUrl);
    }

    /**
     * 清理 URL（替换 \/ 为 /，trim）
     */
    private String cleanUrl(String url) {
        if (url == null) {
            return "";
        }
        return url.replace("\\/", "/").trim();
    }
}