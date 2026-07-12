package com.github.catvod.spider;

import android.content.Context;
import android.text.TextUtils;
import android.util.Base64;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.net.OkHttp;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * 七猫短剧源爬虫实现。
 * 提供短剧搜索、分类列表、详情页解析及播放地址获取。
 */
public class Qmdj extends Spider {

    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]+>");

    private static final String DEFAULT_API_HOST = "https://api-store.qmplaylet.com";
    private static final String DEFAULT_READ_HOST = "https://api-read.qmplaylet.com";
    private static final String CONFIG_URL = "https://neptune.qmplaylet.com/playlet-domain-android.json";
    private static final String DIGITS = "MUlErYWbdJ";
    private static final String UPPER = "9saI0oy_HGitgNA8Fk3hfRqC4p";
    private static final String LOWER = "mBOuc6Kx5T-2zSZ1VvjQ7DwnLe";
    private static final String PLAYER_UA = "Dalvik/2.1.0 (Linux; U; Android 11; M2012K10C Build/RP1A.200720.011)        ";
    private static final String SIGN_TEMPLATE = "AUTHORIZATION=app-version=10001application-id=com.duoduo.readchannel=va-vivo_lfis-white=net-env=1platform=androidqm-params=%sreg=";
    private static final String SIGN_SUFFIX = "d3dGiJc651gSQ8w1";
    private static final String PLAY_FROM = "七猫";
    private static final String EPISODE_SEPARATOR = "#";
    private static final String TRAILING_SLASH_REGEX = "/$";

    private String apiHost = DEFAULT_API_HOST;
    private String readHost = DEFAULT_READ_HOST;

    @Override
    public void init(Context context, String extend) throws Exception {
        super.init(context, extend);
        try {
            HashMap<String, String> headers = new HashMap<>();
            headers.put("User-Agent", "webviewversion/0");
            String json = OkHttp.string(CONFIG_URL, headers);
            JSONObject config = new JSONObject(json);
            JSONObject data = config.optJSONObject("data");
            if (data != null) {
                String host = data.optString("bc");
                String read = data.optString("ks");
                if (!TextUtils.isEmpty(host)) {
                    apiHost = host.replaceAll(TRAILING_SLASH_REGEX, "");
                }
                if (!TextUtils.isEmpty(read)) {
                    readHost = read.replaceAll(TRAILING_SLASH_REGEX, "");
                }
            }
        } catch (Exception e) {
            // 与 smali 一致：异常时不修改 host 字段
        }
    }

    /**
     * 构建签名请求并调用 API。
     *
     * @param host     API 主机地址
     * @param apiPath  API 路径
     * @param params   请求参数
     * @return API 响应的 JSONObject
     */
    private static JSONObject fetchApi(String host, String apiPath, LinkedHashMap<String, String> params) throws Exception {
        LinkedHashMap<String, String> bodyParams = new LinkedHashMap<>();
        bodyParams.put("static_score", "0.8");
        bodyParams.put("uuid", "00000000-6f7c-e347-0000-000000000000");
        bodyParams.put("device-id", "202504012213236fa2ed536aed584e0cc8a6a09fe2f2d4016cdc5bc74f2d5f");
        bodyParams.put("mac", "");
        bodyParams.put("sourceuid", "9494817a02a93435");
        bodyParams.put("refresh-type", "0");
        bodyParams.put("model", "M2012K10C");
        bodyParams.put("wlb-imei", "");
        bodyParams.put("AUTHORIZATION", "6bcc46919d10d06a");
        bodyParams.put("brand", "Redmi");
        bodyParams.put("oaid", "");
        bodyParams.put("oaid-no-cache", "");
        bodyParams.put("sys-ver", "11");
        bodyParams.put("trusted-id", "");
        bodyParams.put("phone-level", "H");
        bodyParams.put("imei", "");
        bodyParams.put("wlb-uid", "");
        bodyParams.put("session-id", String.valueOf(System.currentTimeMillis()));
        bodyParams.put("sign", "");

        // TreeMap 按字典序排序参数，拼接 key=value
        StringBuilder signBuilder = new StringBuilder();
        TreeMap<String, String> treeMap = new TreeMap<>(params);
        for (Map.Entry<String, String> entry : treeMap.entrySet()) {
            String value = entry.getValue() == null ? "" : entry.getValue();
            signBuilder.append(entry.getKey()).append("=").append(value);
        }
        signBuilder.append(SIGN_SUFFIX);
        String sign = md5(signBuilder.toString());
        params.put("sign", sign);

        // qm-params: Base64 编码 body JSON，自定义字符替换
        String bodyJson = new com.google.gson.Gson().toJson(bodyParams);
        byte[] bodyBytes = bodyJson.getBytes(StandardCharsets.UTF_8);
        String base64 = Base64.encodeToString(bodyBytes, Base64.NO_WRAP);

        StringBuilder replaced = new StringBuilder(base64.length());
        for (int i = 0; i < base64.length(); i++) {
            char c = base64.charAt(i);
            if (c == '+') {
                replaced.append('P');
            } else if (c == '/') {
                replaced.append('X');
            } else if (c >= '0' && c <= '9') {
                replaced.append(DIGITS.charAt(c - '0'));
            } else if (c >= 'A' && c <= 'Z') {
                replaced.append(UPPER.charAt(c - 'A'));
            } else if (c >= 'a' && c <= 'z') {
                replaced.append(LOWER.charAt(c - 'a'));
            } else {
                replaced.append(c);
            }
        }
        String qmParams = replaced.toString();

        // 第二轮签名：String.format + MD5
        String signSource2 = String.format(SIGN_TEMPLATE, qmParams) + sign;
        String sign2 = md5(signSource2);
        params.put("sign", sign2);

        // 构建 headers
        HashMap<String, String> headers = new HashMap<>();
        headers.put("authorization", "");
        headers.put("reg", "");
        headers.put("is-white", "");
        headers.put("user-agent", "webviewversion/0");
        headers.put("net-env", "1");
        headers.put("channel", "va-vivo_lf");
        headers.put("platform", "android");
        headers.put("application-id", "com.duoduo.read");
        headers.put("app-version", "10001");
        headers.put("qm-params", qmParams);
        headers.put("no-permiss", "3");

        String fullUrl = host + apiPath;
        return new JSONObject(OkHttp.string(fullUrl, params, headers));
    }

    /**
     * 将 JSONArray 解析为 Vod 列表。
     */
    private ArrayList<Vod> parseVodList(JSONArray array) throws Exception {
        ArrayList<Vod> list = new ArrayList<>();
        if (array == null) return list;
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item == null) continue;

            String id = item.optString("id");
            String title = item.optString("playlet_id", id);
            if (TextUtils.isEmpty(title)) continue;

            String pic = item.optString("title");
            if (!TextUtils.isEmpty(pic)) {
                pic = HTML_TAG_PATTERN.matcher(pic).replaceAll("");
            }

            // 封面图：5 个字段按优先级取第一个非空值
            String[] coverKeys = {"image_link", "image", "cover", "vertical_cover", "playlet_cover"};
            String cover = "";
            for (String key : coverKeys) {
                String val = item.optString(key);
                if (!TextUtils.isEmpty(val)) {
                    cover = val;
                    break;
                }
            }

            // 副标题回退链
            String remark = item.optString("total_episode_num");
            if (TextUtils.isEmpty(remark)) {
                remark = item.optString("total_num");
            }
            if (TextUtils.isEmpty(remark)) {
                remark = item.optString("sub_title");
            }

            Vod vod = new Vod(id, title, pic, cover);
            vod.setVodRemarks(remark);
            list.add(vod);
        }
        return list;
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        LinkedHashMap<String, String> params = new LinkedHashMap<>();
        params.put("tag_id", "0");
        params.put("playlet_privacy", "1");
        params.put("operation", "1");

        JSONObject response = fetchApi(apiHost, "/api/v1/playlet/index", params);
        JSONObject data = response.optJSONObject("data");

        ArrayList<Class> classes = new ArrayList<>();
        if (data != null) {
            JSONArray tagArray = data.optJSONArray("tag_list");
            if (tagArray != null) {
                for (int i = 0; i < tagArray.length(); i++) {
                    JSONObject tag = tagArray.optJSONObject(i);
                    if (tag == null) continue;
                    String tagId = tag.optString("tag_id");
                    String tagName = tag.optString("tag_name");
                    if (!TextUtils.isEmpty(tagId)) {
                        classes.add(new Class(tagId, tagName));
                    }
                }
            }
        }
        return Result.string(classes, new ArrayList<>());
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        LinkedHashMap<String, String> params = new LinkedHashMap<>();
        params.put("tag_id", tid);
        String page = TextUtils.isEmpty(pg) ? "1" : pg;
        params.put("next_id", page);
        String privacy = "0".equals(tid) ? "0" : "1";
        params.put("playlet_privacy", privacy);

        JSONObject response = fetchApi(apiHost, "/api/v1/playlet/index", params);
        JSONObject data = response.optJSONObject("data");

        JSONArray listArray = null;
        if (data != null && data.has("list")) {
            listArray = data.optJSONArray("list");
        }
        ArrayList<Vod> list = parseVodList(listArray);

        int pageInt;
        try {
            pageInt = Integer.parseInt(pg);
        } catch (Exception e) {
            pageInt = 1;
        }
        int pageCount = list.isEmpty() ? pageInt : pageInt + 1;
        return Result.get().page(pageInt, pageCount, 20, list.size()).vod(list).string();
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        String vodId = ids.get(0);

        LinkedHashMap<String, String> params = new LinkedHashMap<>();
        params.put("playlet_id", vodId);

        JSONObject response = fetchApi(readHost, "/player/api/v1/playlet/info", params);
        JSONObject data = response.optJSONObject("data");

        if (data == null) {
            return Result.string(new Vod(vodId, vodId, "", ""));
        }

        String title = data.optString("title");

        // 封面图：3 个字段按优先级取第一个非空值
        String[] coverKeys = {"image_link", "image", "cover"};
        String pic = "";
        for (String key : coverKeys) {
            String val = data.optString(key);
            if (!TextUtils.isEmpty(val)) {
                pic = val;
                break;
            }
        }

        Vod vod = new Vod(vodId, title, pic, "");

        String intro = data.optString("intro");
        vod.setVodContent(intro);

        JSONArray playList = data.optJSONArray("play_list");
        if (playList != null && playList.length() != 0) {
            ArrayList<String> episodes = new ArrayList<>();
            for (int i = 0; i < playList.length(); i++) {
                JSONObject ep = playList.optJSONObject(i);
                if (ep == null) continue;

                String sort = ep.optString("sort");
                if (TextUtils.isEmpty(sort)) {
                    sort = String.valueOf(i + 1);
                }

                String videoUrl = ep.optString("video_url");
                if (TextUtils.isEmpty(videoUrl)) continue;

                episodes.add(new StringBuilder("第").append(sort).append("集$").append(videoUrl).toString());
            }

            vod.setVodPlayFrom(PLAY_FROM);
            vod.setVodPlayUrl(TextUtils.join(EPISODE_SEPARATOR, episodes));
            return Result.string(vod);
        }

        return Result.string(vod);
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        if (TextUtils.isEmpty(id)) {
            return Result.get().url("").parse(0).string();
        }

        HashMap<String, String> headers = new HashMap<>();
        headers.put("User-Agent", PLAYER_UA);
        headers.put("Referer", "https://api-store.qmplaylet.com");

        return Result.get().url(id).header(headers).parse(0).string();
    }

    @Override
    public String searchContent(String key, boolean quick, String pg) throws Exception {
        if (TextUtils.isEmpty(key)) {
            return Result.string(new ArrayList<>());
        }

        LinkedHashMap<String, String> params = new LinkedHashMap<>();
        params.put("extend", "");
        String page = TextUtils.isEmpty(pg) ? "1" : pg;
        params.put("page", page);
        params.put("wd", key.trim());
        params.put("read_preference", "0");

        // smali 中 key 为 XOR 解码字符串，反编译后为 "0"，值格式为 AUTHORIZATION + 时间戳
        params.put("0", new StringBuilder("6bcc46919d10d06a").append(System.currentTimeMillis()).toString());

        JSONObject response = fetchApi(apiHost, "/api/v1/playlet/search", params);
        JSONObject data = response.optJSONObject("data");

        JSONArray listArray = null;
        if (data != null && data.has("list")) {
            listArray = data.optJSONArray("list");
        }
        ArrayList<Vod> list = parseVodList(listArray);

        int pageInt;
        try {
            pageInt = Integer.parseInt(pg);
        } catch (Exception e) {
            pageInt = 1;
        }
        int pageCount = pageInt + 1;
        return Result.get().page(pageInt, pageCount, 20, list.size()).vod(list).string();
    }

    private static String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
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
}
