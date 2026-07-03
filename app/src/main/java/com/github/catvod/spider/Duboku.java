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

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

/**
 * 独播库 Spider - 加密签名型视频源
 * 使用 Base64 + Random 生成 API 签名，支持电影/电视剧/综艺/动漫等分类
 */
public class Duboku extends Spider {

    private static final String DEFAULT_HOST = "https://api.dbokutv.com";
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64)";
    private static final String REFERER = "https://www.duboku.tv/";
    private static final String PLAYER_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/118.0.0.0 Safari/537.36";
    private static final String PLAYER_ORIGIN = "https://w.duboku.io";
    private static final String PLAYER_REFERER = "https://w.duboku.io/";

    private String host;

    public Duboku() {
        this.host = DEFAULT_HOST;
    }

    /**
     * 生成 API 签名参数
     * 算法：Base64(交错拼接(时间戳, 随机字符串)) + Random 生成 sign/token
     */
    private static String generateSignParams() {
        long timestamp = System.currentTimeMillis() / 1000;

        // 用时间戳作为种子创建 Random
        Random random = new Random(timestamp);

        // 生成随机数（范围 0x2faf0800 ~ 0x2faf0801，即 800000000 ~ 800000001）
        int randNum = random.nextInt(0x2faf0800) % 0x2faf0801;

        // 拼接两个字符串：100000000 + randNum 和 900000000 - randNum
        String str1 = String.valueOf(0x5f5e100 + randNum); // 100000000 + randNum
        String str2 = String.valueOf(0x35a4e900 - randNum); // 900000000 - randNum

        // 交错拼接时间戳字符串和随机字符串
        String timestampStr = String.valueOf(timestamp);
        StringBuilder interleaved = new StringBuilder();
        int minLen = Math.min(str1.length(), timestampStr.length());

        for (int i = 0; i < minLen; i++) {
            interleaved.append(str1.charAt(i));
            interleaved.append(timestampStr.charAt(i));
        }

        // 添加剩余部分
        if (str1.length() > minLen) {
            interleaved.append(str1.substring(minLen));
        }
        if (timestampStr.length() > minLen) {
            interleaved.append(timestampStr.substring(minLen));
        }

        // Base64 编码（NO_WRAP = 2）
        String base64Str = Base64.getEncoder().encodeToString(interleaved.toString().getBytes(StandardCharsets.UTF_8));

        // 替换 "=" 为 "."
        String ssid = base64Str.replace("=", ".");

        // 生成 sign（60 位随机字符串）
        String signCharset = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
        String sign = generateRandomString(60, signCharset, timestamp + 60);

        // 生成 token（38 位随机字符串）
        String tokenCharset = "XYZ0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVW";
        String token = generateRandomString(38, tokenCharset, timestamp + 38);

        // 返回完整查询参数
        return "?sign=" + sign + "&ssid=" + ssid + "&token=" + token;
    }

    /**
     * 生成随机字符串
     */
    private static String generateRandomString(int length, String charset, long seed) {
        Random random = new Random(System.currentTimeMillis() / 1000 + seed);
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < length; i++) {
            int index = random.nextInt(charset.length());
            sb.append(charset.charAt(index));
        }

        return sb.toString();
    }

    /**
     * 解码字符串（分段反转 + Base64 解码）
     */
    private static String decodeString(String input) {
        if (TextUtils.isEmpty(input)) {
            return "";
        }

        try {
            StringBuilder reversed = new StringBuilder();
            int pos = 0;

            // 每 10 字符分段反转
            while (pos < input.length()) {
                int end = Math.min(pos + 10, input.length());
                StringBuilder segment = new StringBuilder(input.substring(pos, end));
                reversed.append(segment.reverse());
                pos = end;
            }

            // 替换 "." 为 "="
            String base64Str = reversed.toString().replace(".", "=");

            // Base64 解码
            byte[] decoded = Base64.getDecoder().decode(base64Str);
            return new String(decoded, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return input;
        }
    }

    /**
     * 请求 API
     */
    private String fetchApi(String path) throws Exception {
        String url = host + path + generateSignParams();

        HashMap<String, String> headers = new HashMap<>();
        headers.put("User-Agent", USER_AGENT);
        headers.put("Connection", "Keep-Alive");
        headers.put("Referer", REFERER);

        return OkHttp.string(url, headers);
    }

    /**
     * 解析 Vod 列表
     */
    private ArrayList<Vod> parseVodList(JSONArray array) throws Exception {
        ArrayList<Vod> list = new ArrayList<>();

        if (array == null) {
            return list;
        }

        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item == null) continue;

            String id = decodeString(item.optString("DId"));
            if (TextUtils.isEmpty(id)) {
                id = item.optString("DId");
            }

            String name = item.optString("Name");
            String pic = decodeString(item.optString("TnId"));
            if (TextUtils.isEmpty(pic)) {
                pic = item.optString("TnId");
            }

            String remark = item.optString("Tag");
            if (TextUtils.isEmpty(remark) && item.has("Rating")) {
                remark = item.optDouble("Rating") + "分";
            }

            if (!TextUtils.isEmpty(id) && !TextUtils.isEmpty(name)) {
                list.add(new Vod(id, name, pic, remark));
            }
        }

        return list;
    }

    @Override
    public void init(Context context, String extend) throws Exception {
        host = DEFAULT_HOST;

        if (TextUtils.isEmpty(extend)) {
            return;
        }

        String config = extend.trim();

        // 尝试解析 JSON 配置
        if (!config.startsWith("http")) {
            try {
                JSONObject json = new JSONObject(config);
                config = json.optString("api", json.optString("site", config));
            } catch (Exception ignored) {
            }
        }

        // 移除末尾斜杠
        while (config.endsWith("/")) {
            config = config.substring(0, config.length() - 1);
        }

        // 更新 host
        if (config.startsWith("http")) {
            host = config;
        }
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        ArrayList<Class> classes = new ArrayList<>();
        classes.add(new Class("1", "电影"));
        classes.add(new Class("2", "电视剧"));
        classes.add(new Class("3", "综艺"));
        classes.add(new Class("4", "动漫"));
        classes.add(new Class("21", "短剧"));
        classes.add(new Class("20", "港剧"));
        classes.add(new Class("13", "陆剧"));
        classes.add(new Class("15", "日韩剧"));
        classes.add(new Class("14", "台泰剧"));

        ArrayList<Vod> list = new ArrayList<>();
        JSONArray homeArray = new JSONArray(fetchApi("/home"));

        for (int i = 0; i < homeArray.length(); i++) {
            JSONObject item = homeArray.optJSONObject(i);
            if (item != null) {
                JSONArray vodArray = item.optJSONArray("VodList");
                list.addAll(parseVodList(vodArray));
            }
        }

        if (filter) {
            JSONObject filters = new JSONObject(FILTER_JSON);
            return Result.string(classes, list, filters);
        } else {
            return Result.string(classes, list);
        }
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        // 从 extend 中获取 cateId（可能覆盖 tid）
        if (extend != null && !TextUtils.isEmpty(extend.get("cateId"))) {
            tid = extend.get("cateId");
        }

        String area = extend != null ? extend.get("area") : "";
        String year = extend != null ? extend.get("year") : "";
        String by = extend != null ? extend.get("by") : "";
        String classType = extend != null ? extend.get("class") : "";
        String lang = extend != null ? extend.get("lang") : "";

        if (!TextUtils.isEmpty(area)) area = area.trim();
        if (!TextUtils.isEmpty(year)) year = year.trim();
        if (!TextUtils.isEmpty(by)) by = by.trim();
        if (!TextUtils.isEmpty(classType)) classType = classType.trim();
        if (!TextUtils.isEmpty(lang)) lang = lang.trim();

        if (TextUtils.isEmpty(pg)) {
            pg = "1";
        }

        // 构建 URL 路径
        StringBuilder pathBuilder = new StringBuilder("/vodshow/");
        pathBuilder.append(tid).append("-");
        pathBuilder.append(area).append("-");
        pathBuilder.append(by).append("-");
        pathBuilder.append(classType).append("-");
        pathBuilder.append(lang).append("----");
        pathBuilder.append(pg).append("---");
        pathBuilder.append(year);

        JSONObject response = new JSONObject(fetchApi(pathBuilder.toString()));
        JSONArray vodArray = response.optJSONArray("VodList");
        ArrayList<Vod> list = parseVodList(vodArray);

        int currentPage = Integer.parseInt(pg);
        int nextPage = currentPage + 1;
        int total = Integer.MAX_VALUE;

        return Result.string(currentPage, nextPage, list.size(), total, list);
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        String vodId = ids.get(0);
        JSONObject response = new JSONObject(fetchApi(vodId));

        Vod vod = new Vod();
        vod.setVodId(vodId);
        vod.setVodName(response.optString("Name"));

        String pic = decodeString(response.optString("TnId"));
        vod.setVodPic(pic);

        vod.setVodYear(response.optString("ReleaseYear"));
        vod.setVodDirector(response.optString("Director"));

        JSONArray actorArray = response.optJSONArray("Actor");
        String actor = actorArray != null ? actorArray.toString() : "";
        vod.setVodActor(actor);

        vod.setVodContent(response.optString("Description"));

        String playFrom = response.optString("Name");
        JSONArray playlistArray = response.optJSONArray("Playlist");

        ArrayList<String> playUrls = new ArrayList<>();
        if (playlistArray != null) {
            for (int i = 0; i < playlistArray.length(); i++) {
                JSONObject episode = playlistArray.optJSONObject(i);
                if (episode == null) continue;

                String episodeName = episode.optString("EpisodeName");
                if (TextUtils.isEmpty(episodeName)) {
                    episodeName = "正片";
                }

                String videoId = decodeString(episode.optString("VId"));
                if (TextUtils.isEmpty(videoId)) {
                    videoId = episode.optString("VId");
                }

                StringBuilder playUrl = new StringBuilder();
                playUrl.append(episodeName).append("$");
                playUrl.append(videoId).append("|");
                playUrl.append(episodeName).append("|");
                playUrl.append(playFrom);

                playUrls.add(playUrl.toString());
            }
        }

        vod.setVodPlayFrom("独播库");
        vod.setVodPlayUrl(TextUtils.join("#", playUrls));

        return Result.string(vod);
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        // 解析播放参数
        String videoId = id;

        // 处理 "|" 分隔符（取第一段）
        if (videoId.contains("|")) {
            String[] parts = videoId.split("\\|", -1);
            videoId = parts[0];
        }

        // 处理 "$" 分隔符（取最后一段）
        if (videoId.contains("$")) {
            int lastDollar = videoId.lastIndexOf('$');
            videoId = videoId.substring(lastDollar + 1);
        }

        // 获取播放 URL
        JSONObject response = new JSONObject(fetchApi(videoId));
        String playUrl = decodeString(response.optString("HId"));

        if (TextUtils.isEmpty(playUrl)) {
            playUrl = response.optString("HId");
        }

        // 构建播放结果
        Result result = Result.get().url(playUrl);

        HashMap<String, String> headers = new HashMap<>();
        headers.put("User-Agent", PLAYER_USER_AGENT);
        headers.put("origin", PLAYER_ORIGIN);
        headers.put("referer", PLAYER_REFERER);

        result.header(headers);

        // 判断是否需要解析（非 .m3u8 需要解析）
        boolean needParse = !playUrl.contains(".m3u8");
        result.parse(needParse ? 1 : 0);

        return result.string();
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        StringBuilder pathBuilder = new StringBuilder("/vodsearch");
        pathBuilder.append(generateSignParams());
        pathBuilder.append("&wd=");
        pathBuilder.append(java.net.URLEncoder.encode(key, "UTF-8"));

        String url = host + pathBuilder.toString();

        HashMap<String, String> headers = new HashMap<>();
        headers.put("User-Agent", USER_AGENT);
        headers.put("Connection", "Keep-Alive");
        headers.put("Referer", REFERER);

        String response = OkHttp.string(url, headers);
        JSONArray searchArray = new JSONArray(response);
        ArrayList<Vod> list = parseVodList(searchArray);

        return Result.string(list);
    }

    // 过滤器 JSON（从 smali 行 1598 提取）
    private static final String FILTER_JSON = "{\"1\":[{\"key\":\"class\",\"name\":\"类型\",\"value\":[{\"n\":\"喜剧\",\"v\":\"喜剧\"},{\"n\":\"爱情\",\"v\":\"爱情\"},{\"n\":\"恐怖\",\"v\":\"恐怖\"},{\"n\":\"动作\",\"v\":\"动作\"},{\"n\":\"科幻\",\"v\":\"科幻\"},{\"n\":\"剧情\",\"v\":\"剧情\"},{\"n\":\"悬疑\",\"v\":\"悬疑\"},{\"n\":\"惊悚\",\"v\":\"惊悚\"},{\"n\":\"古装\",\"v\":\"古装\"}]},{\"key\":\"area\",\"name\":\"地区\",\"value\":[{\"n\":\"大陆\",\"v\":\"大陆\"},{\"n\":\"香港\",\"v\":\"香港\"},{\"n\":\"台湾\",\"v\":\"台湾\"},{\"n\":\"韩国\",\"v\":\"韩国\"},{\"n\":\"日本\",\"v\":\"日本\"},{\"n\":\"泰国\",\"v\":\"泰国\"}]},{\"key\":\"year\",\"name\":\"年份\",\"value\":[{\"n\":\"2025\",\"v\":\"2025\"},{\"n\":\"2024\",\"v\":\"2024\"},{\"n\":\"2023\",\"v\":\"2023\"},{\"n\":\"2022\",\"v\":\"2022\"},{\"n\":\"2021\",\"v\":\"2021\"},{\"n\":\"2020\",\"v\":\"2020\"}]},{\"key\":\"lang\",\"name\":\"语言\",\"value\":[{\"n\":\"国语\",\"v\":\"国语\"},{\"n\":\"粤语\",\"v\":\"粤语\"},{\"n\":\"韩语\",\"v\":\"韩语\"},{\"n\":\"英语\",\"v\":\"英语\"},{\"n\":\"日语\",\"v\":\"日语\"}]},{\"key\":\"by\",\"name\":\"排序\",\"value\":[{\"n\":\"时间\",\"v\":\"\"},{\"n\":\"人气\",\"v\":\"人气\"},{\"n\":\"评分\",\"v\":\"评分\"}]}],\"2\":[{\"key\":\"class\",\"name\":\"类型\",\"value\":[{\"n\":\"喜剧\",\"v\":\"喜剧\"},{\"n\":\"爱情\",\"v\":\"爱情\"},{\"n\":\"恐怖\",\"v\":\"恐怖\"},{\"n\":\"动作\",\"v\":\"动作\"},{\"n\":\"科幻\",\"v\":\"科幻\"},{\"n\":\"剧情\",\"v\":\"剧情\"},{\"n\":\"悬疑\",\"v\":\"悬疑\"},{\"n\":\"惊悚\",\"v\":\"惊悚\"},{\"n\":\"古装\",\"v\":\"古装\"}]},{\"key\":\"area\",\"name\":\"地区\",\"value\":[{\"n\":\"大陆\",\"v\":\"大陆\"},{\"n\":\"香港\",\"v\":\"香港\"},{\"n\":\"台湾\",\"v\":\"台湾\"},{\"n\":\"韩国\",\"v\":\"韩国\"},{\"n\":\"日本\",\"v\":\"日本\"},{\"n\":\"泰国\",\"v\":\"泰国\"}]},{\"key\":\"year\",\"name\":\"年份\",\"value\":[{\"n\":\"2025\",\"v\":\"2025\"},{\"n\":\"2024\",\"v\":\"2024\"},{\"n\":\"2023\",\"v\":\"2023\"},{\"n\":\"2022\",\"v\":\"2022\"},{\"n\":\"2021\",\"v\":\"2021\"},{\"n\":\"2020\",\"v\":\"2020\"}]},{\"key\":\"lang\",\"name\":\"语言\",\"value\":[{\"n\":\"国语\",\"v\":\"国语\"},{\"n\":\"粤语\",\"v\":\"粤语\"},{\"n\":\"韩语\",\"v\":\"韩语\"},{\"n\":\"英语\",\"v\":\"英语\"},{\"n\":\"日语\",\"v\":\"日语\"}]},{\"key\":\"by\",\"name\":\"排序\",\"value\":[{\"n\":\"时间\",\"v\":\"\"},{\"n\":\"人气\",\"v\":\"人气\"},{\"n\":\"评分\",\"v\":\"评分\"}]}],\"3\":[{\"key\":\"class\",\"name\":\"类型\",\"value\":[{\"n\":\"喜剧\",\"v\":\"喜剧\"},{\"n\":\"爱情\",\"v\":\"爱情\"},{\"n\":\"恐怖\",\"v\":\"恐怖\"},{\"n\":\"动作\",\"v\":\"动作\"},{\"n\":\"科幻\",\"v\":\"科幻\"},{\"n\":\"剧情\",\"v\":\"剧情\"},{\"n\":\"悬疑\",\"v\":\"悬疑\"},{\"n\":\"惊悚\",\"v\":\"惊悚\"},{\"n\":\"古装\",\"v\":\"古装\"}]},{\"key\":\"area\",\"name\":\"地区\",\"value\":[{\"n\":\"大陆\",\"v\":\"大陆\"},{\"n\":\"香港\",\"v\":\"香港\"},{\"n\":\"台湾\",\"v\":\"台湾\"},{\"n\":\"韩国\",\"v\":\"韩国\"},{\"n\":\"日本\",\"v\":\"日本\"},{\"n\":\"泰国\",\"v\":\"泰国\"}]},{\"key\":\"year\",\"name\":\"年份\",\"value\":[{\"n\":\"2025\",\"v\":\"2025\"},{\"n\":\"2024\",\"v\":\"2024\"},{\"n\":\"2023\",\"v\":\"2023\"},{\"n\":\"2022\",\"v\":\"2022\"},{\"n\":\"2021\",\"v\":\"2021\"},{\"n\":\"2020\",\"v\":\"2020\"}]},{\"key\":\"lang\",\"name\":\"语言\",\"value\":[{\"n\":\"国语\",\"v\":\"国语\"},{\"n\":\"粤语\",\"v\":\"粤语\"},{\"n\":\"韩语\",\"v\":\"韩语\"},{\"n\":\"英语\",\"v\":\"英语\"},{\"n\":\"日语\",\"v\":\"日语\"}]},{\"key\":\"by\",\"name\":\"排序\",\"value\":[{\"n\":\"时间\",\"v\":\"\"},{\"n\":\"人气\",\"v\":\"人气\"},{\"n\":\"评分\",\"v\":\"评分\"}]}],\"4\":[{\"key\":\"class\",\"name\":\"类型\",\"value\":[{\"n\":\"喜剧\",\"v\":\"喜剧\"},{\"n\":\"爱情\",\"v\":\"爱情\"},{\"n\":\"恐怖\",\"v\":\"恐怖\"},{\"n\":\"动作\",\"v\":\"动作\"},{\"n\":\"科幻\",\"v\":\"科幻\"},{\"n\":\"剧情\",\"v\":\"剧情\"},{\"n\":\"悬疑\",\"v\":\"悬疑\"},{\"n\":\"惊悚\",\"v\":\"惊悚\"},{\"n\":\"古装\",\"v\":\"古装\"}]},{\"key\":\"area\",\"name\":\"地区\",\"value\":[{\"n\":\"大陆\",\"v\":\"大陆\"},{\"n\":\"香港\",\"v\":\"香港\"},{\"n\":\"台湾\",\"v\":\"台湾\"},{\"n\":\"韩国\",\"v\":\"韩国\"},{\"n\":\"日本\",\"v\":\"日本\"},{\"n\":\"泰国\",\"v\":\"泰国\"}]},{\"key\":\"year\",\"name\":\"年份\",\"value\":[{\"n\":\"2025\",\"v\":\"2025\"},{\"n\":\"2024\",\"v\":\"2024\"},{\"n\":\"2023\",\"v\":\"2023\"},{\"n\":\"2022\",\"v\":\"2022\"},{\"n\":\"2021\",\"v\":\"2021\"},{\"n\":\"2020\",\"v\":\"2020\"}]},{\"key\":\"lang\",\"name\":\"语言\",\"value\":[{\"n\":\"国语\",\"v\":\"国语\"},{\"n\":\"粤语\",\"v\":\"粤语\"},{\"n\":\"韩语\",\"v\":\"韩语\"},{\"n\":\"英语\",\"v\":\"英语\"},{\"n\":\"日语\",\"v\":\"日语\"}]},{\"key\":\"by\",\"name\":\"排序\",\"value\":[{\"n\":\"时间\",\"v\":\"\"},{\"n\":\"人气\",\"v\":\"人气\"},{\"n\":\"评分\",\"v\":\"评分\"}]}],\"13\":[{\"key\":\"class\",\"name\":\"类型\",\"value\":[{\"n\":\"喜剧\",\"v\":\"喜剧\"},{\"n\":\"爱情\",\"v\":\"爱情\"},{\"n\":\"恐怖\",\"v\":\"恐怖\"},{\"n\":\"动作\",\"v\":\"动作\"},{\"n\":\"科幻\",\"v\":\"科幻\"},{\"n\":\"剧情\",\"v\":\"剧情\"},{\"n\":\"悬疑\",\"v\":\"悬疑\"},{\"n\":\"惊悚\",\"v\":\"惊悚\"},{\"n\":\"古装\",\"v\":\"古装\"}]},{\"key\":\"area\",\"name\":\"地区\",\"value\":[{\"n\":\"大陆\",\"v\":\"大陆\"},{\"n\":\"香港\",\"v\":\"香港\"},{\"n\":\"台湾\",\"v\":\"台湾\"},{\"n\":\"韩国\",\"v\":\"韩国\"},{\"n\":\"日本\",\"v\":\"日本\"},{\"n\":\"泰国\",\"v\":\"泰国\"}]},{\"key\":\"year\",\"name\":\"年份\",\"value\":[{\"n\":\"2025\",\"v\":\"2025\"},{\"n\":\"2024\",\"v\":\"2024\"},{\"n\":\"2023\",\"v\":\"2023\"},{\"n\":\"2022\",\"v\":\"2022\"},{\"n\":\"2021\",\"v\":\"2021\"},{\"n\":\"2020\",\"v\":\"2020\"}]},{\"key\":\"lang\",\"name\":\"语言\",\"value\":[{\"n\":\"国语\",\"v\":\"国语\"},{\"n\":\"粤语\",\"v\":\"粤语\"},{\"n\":\"韩语\",\"v\":\"韩语\"},{\"n\":\"英语\",\"v\":\"英语\"},{\"n\":\"日语\",\"v\":\"日语\"}]},{\"key\":\"by\",\"name\":\"排序\",\"value\":[{\"n\":\"时间\",\"v\":\"\"},{\"n\":\"人气\",\"v\":\"人气\"},{\"n\":\"评分\",\"v\":\"评分\"}]}],\"14\":[{\"key\":\"class\",\"name\":\"类型\",\"value\":[{\"n\":\"喜剧\",\"v\":\"喜剧\"},{\"n\":\"爱情\",\"v\":\"爱情\"},{\"n\":\"恐怖\",\"v\":\"恐怖\"},{\"n\":\"动作\",\"v\":\"动作\"},{\"n\":\"科幻\",\"v\":\"科幻\"},{\"n\":\"剧情\",\"v\":\"剧情\"},{\"n\":\"悬疑\",\"v\":\"悬疑\"},{\"n\":\"惊悚\",\"v\":\"惊悚\"},{\"n\":\"古装\",\"v\":\"古装\"}]},{\"key\":\"area\",\"name\":\"地区\",\"value\":[{\"n\":\"大陆\",\"v\":\"大陆\"},{\"n\":\"香港\",\"v\":\"香港\"},{\"n\":\"台湾\",\"v\":\"台湾\"},{\"n\":\"韩国\",\"v\":\"韩国\"},{\"n\":\"日本\",\"v\":\"日本\"},{\"n\":\"泰国\",\"v\":\"泰国\"}]},{\"key\":\"year\",\"name\":\"年份\",\"value\":[{\"n\":\"2025\",\"v\":\"2025\"},{\"n\":\"2024\",\"v\":\"2024\"},{\"n\":\"2023\",\"v\":\"2023\"},{\"n\":\"2022\",\"v\":\"2022\"},{\"n\":\"2021\",\"v\":\"2021\"},{\"n\":\"2020\",\"v\":\"2020\"}]},{\"key\":\"lang\",\"name\":\"语言\",\"value\":[{\"n\":\"国语\",\"v\":\"国语\"},{\"n\":\"粤语\",\"v\":\"粤语\"},{\"n\":\"韩语\",\"v\":\"韩语\"},{\"n\":\"英语\",\"v\":\"英语\"},{\"n\":\"日语\",\"v\":\"日语\"}]},{\"key\":\"by\",\"name\":\"排序\",\"value\":[{\"n\":\"时间\",\"v\":\"\"},{\"n\":\"人气\",\"v\":\"人气\"},{\"n\":\"评分\",\"v\":\"评分\"}]}],\"15\":[{\"key\":\"class\",\"name\":\"类型\",\"value\":[{\"n\":\"喜剧\",\"v\":\"喜剧\"},{\"n\":\"爱情\",\"v\":\"爱情\"},{\"n\":\"恐怖\",\"v\":\"恐怖\"},{\"n\":\"动作\",\"v\":\"动作\"},{\"n\":\"科幻\",\"v\":\"科幻\"},{\"n\":\"剧情\",\"v\":\"剧情\"},{\"n\":\"悬疑\",\"v\":\"悬疑\"},{\"n\":\"惊悚\",\"v\":\"惊悚\"},{\"n\":\"古装\",\"v\":\"古装\"}]},{\"key\":\"area\",\"name\":\"地区\",\"value\":[{\"n\":\"大陆\",\"v\":\"大陆\"},{\"n\":\"香港\",\"v\":\"香港\"},{\"n\":\"台湾\",\"v\":\"台湾\"},{\"n\":\"韩国\",\"v\":\"韩国\"},{\"n\":\"日本\",\"v\":\"日本\"},{\"n\":\"泰国\",\"v\":\"泰国\"}]},{\"key\":\"year\",\"name\":\"年份\",\"value\":[{\"n\":\"2025\",\"v\":\"2025\"},{\"n\":\"2024\",\"v\":\"2024\"},{\"n\":\"2023\",\"v\":\"2023\"},{\"n\":\"2022\",\"v\":\"2022\"},{\"n\":\"2021\",\"v\":\"2021\"},{\"n\":\"2020\",\"v\":\"2020\"}]},{\"key\":\"lang\",\"name\":\"语言\",\"value\":[{\"n\":\"国语\",\"v\":\"国语\"},{\"n\":\"粤语\",\"v\":\"粤语\"},{\"n\":\"韩语\",\"v\":\"韩语\"},{\"n\":\"英语\",\"v\":\"英语\"},{\"n\":\"日语\",\"v\":\"日语\"}]},{\"key\":\"by\",\"name\":\"排序\",\"value\":[{\"n\":\"时间\",\"v\":\"\"},{\"n\":\"人气\",\"v\":\"人气\"},{\"n\":\"评分\",\"v\":\"评分\"}]}],\"20\":[{\"key\":\"class\",\"name\":\"类型\",\"value\":[{\"n\":\"喜剧\",\"v\":\"喜剧\"},{\"n\":\"爱情\",\"v\":\"爱情\"},{\"n\":\"恐怖\",\"v\":\"恐怖\"},{\"n\":\"动作\",\"v\":\"动作\"},{\"n\":\"科幻\",\"v\":\"科幻\"},{\"n\":\"剧情\",\"v\":\"剧情\"},{\"n\":\"悬疑\",\"v\":\"悬疑\"},{\"n\":\"惊悚\",\"v\":\"惊悚\"},{\"n\":\"古装\",\"v\":\"古装\"}]},{\"key\":\"area\",\"name\":\"地区\",\"value\":[{\"n\":\"大陆\",\"v\":\"大陆\"},{\"n\":\"香港\",\"v\":\"香港\"},{\"n\":\"台湾\",\"v\":\"台湾\"},{\"n\":\"韩国\",\"v\":\"韩国\"},{\"n\":\"日本\",\"v\":\"日本\"},{\"n\":\"泰国\",\"v\":\"泰国\"}]},{\"key\":\"year\",\"name\":\"年份\",\"value\":[{\"n\":\"2025\",\"v\":\"2025\"},{\"n\":\"2024\",\"v\":\"2024\"},{\"n\":\"2023\",\"v\":\"2023\"},{\"n\":\"2022\",\"v\":\"2022\"},{\"n\":\"2021\",\"v\":\"2021\"},{\"n\":\"2020\",\"v\":\"2020\"}]},{\"key\":\"lang\",\"name\":\"语言\",\"value\":[{\"n\":\"国语\",\"v\":\"国语\"},{\"n\":\"粤语\",\"v\":\"粤语\"},{\"n\":\"韩语\",\"v\":\"韩语\"},{\"n\":\"英语\",\"v\":\"英语\"},{\"n\":\"日语\",\"v\":\"日语\"}]},{\"key\":\"by\",\"name\":\"排序\",\"value\":[{\"n\":\"时间\",\"v\":\"\"},{\"n\":\"人气\",\"v\":\"人气\"},{\"n\":\"评分\",\"v\":\"评分\"}]}],\"21\":[{\"key\":\"class\",\"name\":\"类型\",\"value\":[{\"n\":\"喜剧\",\"v\":\"喜剧\"},{\"n\":\"爱情\",\"v\":\"爱情\"},{\"n\":\"恐怖\",\"v\":\"恐怖\"},{\"n\":\"动作\",\"v\":\"动作\"},{\"n\":\"科幻\",\"v\":\"科幻\"},{\"n\":\"剧情\",\"v\":\"剧情\"},{\"n\":\"悬疑\",\"v\":\"悬疑\"},{\"n\":\"惊悚\",\"v\":\"惊悚\"},{\"n\":\"古装\",\"v\":\"古装\"}]},{\"key\":\"area\",\"name\":\"地区\",\"value\":[{\"n\":\"大陆\",\"v\":\"大陆\"},{\"n\":\"香港\",\"v\":\"香港\"},{\"n\":\"台湾\",\"v\":\"台湾\"},{\"n\":\"韩国\",\"v\":\"韩国\"},{\"n\":\"日本\",\"v\":\"日本\"},{\"n\":\"泰国\",\"v\":\"泰国\"}]},{\"key\":\"year\",\"name\":\"年份\",\"value\":[{\"n\":\"2025\",\"v\":\"2025\"},{\"n\":\"2024\",\"v\":\"2024\"},{\"n\":\"2023\",\"v\":\"2023\"},{\"n\":\"2022\",\"v\":\"2022\"},{\"n\":\"2021\",\"v\":\"2021\"},{\"n\":\"2020\",\"v\":\"2020\"}]},{\"key\":\"lang\",\"name\":\"语言\",\"value\":[{\"n\":\"国语\",\"v\":\"国语\"},{\"n\":\"粤语\",\"v\":\"粤语\"},{\"n\":\"韩语\",\"v\":\"韩语\"},{\"n\":\"英语\",\"v\":\"英语\"},{\"n\":\"日语\",\"v\":\"日语\"}]},{\"key\":\"by\",\"name\":\"排序\",\"value\":[{\"n\":\"时间\",\"v\":\"\"},{\"n\":\"人气\",\"v\":\"人气\"},{\"n\":\"评分\",\"v\":\"评分\"}]}]}";
}