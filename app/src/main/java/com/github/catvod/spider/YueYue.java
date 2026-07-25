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

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * 悦悦源爬虫实现。
 * 支持搜索、分类列表、详情页解析及播放源获取。
 * 接口数据使用 AES-CBC 加密，本地解密后解析 JSON。
 */
public class YueYue extends Spider {

    private static final byte[] AES_KEY = "aZ9$kU5%qI7=yC2=zH2#gM0@pX7^wF3a".getBytes(StandardCharsets.UTF_8);
    private static final byte[] AES_IV = "hY2&tN3]kF7,dL7=".getBytes(StandardCharsets.UTF_8);

    private static final String DEFAULT_API_URL = "https://u.yyxdmn.com/api";
    private static final String DEFAULT_CHANNEL_CODE = "ltsp_sp02";
    private static final String DEFAULT_APP_ID = "lantianshipin";
    private static final String SIGN_SALT = "zD9[bM4~sF4~uY2)";
    private static final String UA_OKHTTP = "okhttp/4.9.0";
    private static final String UA_MOBILE = "Mozilla/5.0 (Linux; Android 11; M2012K10C Build/RP1A.200720.011; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/87.0.4280.141 Mobile Safari/537.36";
    private static final String PLAY_FROM = "悦悦";
    private static final String EPISODE_SEP = "#";
    private static final String FIELD_SEP = "|||";
    private static final String KV_SEP = "$";
    /** Base64 标志位：URL_SAFE | NO_WRAP | NO_PADDING（与 smali 中 0xb 一致，用于代理 URL 中的 domain/path 编码） */
    private static final int BASE64_URL_FLAGS = Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING;

    private String apiUrl = DEFAULT_API_URL;
    private String token = "";
    private String deviceId = "";
    private String channelCode = DEFAULT_CHANNEL_CODE;
    private String appId = DEFAULT_APP_ID;
    private String signature = "";
    private final Object lock = new Object();

    /**
     * 生成时间戳签名。
     * 格式：10-{rand1}-{seconds}-{millis}-{seconds}-{base}-{seconds}-{base+rand2}
     * 最终返回该字符串的 MD5 哈希。
     */
    public static String generateSignature() {
        long currentTime = System.currentTimeMillis();
        long seconds = currentTime / 1000L;
        long millis = (currentTime % 1000L) * 1000L;
        int rand1 = new Random().nextInt(0x74143dff) + 0x5f5e100;
        int rand2 = new Random().nextInt(0x1f40) + 0x1b58;
        long base = millis + rand2;
        StringBuilder sb = new StringBuilder("10-")
                .append(rand1).append("-")
                .append(seconds).append("-")
                .append(millis).append("-")
                .append(seconds).append("-")
                .append(base).append("-")
                .append(seconds).append("-")
                .append(base + rand2);
        return md5Hex(sb.toString());
    }

    /**
     * 计算字符串的 MD5 哈希（小写十六进制）。
     */
    public static String md5Hex(String str) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(str.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 若 value 为空则返回 defaultValue，否则返回 value。
     */
    public static String getOrDefault(String value, String defaultValue) {
        return TextUtils.isEmpty(value) ? defaultValue : value;
    }

    /**
     * 去除 URL 末尾的连续 "/"。
     */
    public static String trimTrailingSlash(String url) {
        url = url.trim();
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        return url;
    }

    /**
     * 发起 API 请求并解密响应。
     *
     * @param path        接口路径（如 /new_public/init_v2）
     * @param params      表单参数
     * @param skipAutoInit 是否跳过自动初始化（true 表示本次调用由 ensureInitialized() 自身发起，
     *                     需跳过递归初始化；false 表示如需可触发自动初始化）
     * @return 解密后的 JSON 响应
     */
    public final JSONObject fetchApi(String path, HashMap<String, String> params, boolean skipAutoInit) throws Exception {
        if (!skipAutoInit && TextUtils.isEmpty(signature)) {
            ensureInitialized();
        }
        String timestamp = String.valueOf(System.currentTimeMillis());
        HashMap<String, String> headers = new HashMap<>();
        headers.put("User-Agent", UA_OKHTTP);
        headers.put("sys_platform", "2");
        headers.put("device_id", deviceId);
        headers.put("sysrelease", "11");
        headers.put("sign", md5Hex(SIGN_SALT + deviceId + timestamp).toUpperCase(Locale.US));
        headers.put("cur_time", timestamp);
        headers.put("channel_code", channelCode);
        headers.put("version", "50000");
        headers.put("mobmodel", "localhost");
        headers.put("log-header", "I am the log request header.");
        headers.put("mob_mfr", "Linux");
        headers.put("package_name", "com.lightmemory.simon");
        headers.put("app_id", appId);
        headers.put("Content-Type", "application/x-www-form-urlencoded");
        headers.put("token", token == null ? "" : token);

        String url = apiUrl + path;
        String response = OkHttp.post(url, params, headers);

        if (TextUtils.isEmpty(response)) {
            throw new Exception("悦悦请求失败 ");
        }

        String decrypted = null;
        try {
            byte[] decoded = Base64.decode(response.trim(), 0);
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            SecretKeySpec keySpec = new SecretKeySpec(AES_KEY, "AES");
            IvParameterSpec ivSpec = new IvParameterSpec(AES_IV);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);
            byte[] decryptedBytes = cipher.doFinal(decoded);
            decrypted = new String(decryptedBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            // 解密失败则使用空串
        }

        if (TextUtils.isEmpty(decrypted)) {
            throw new Exception("悦悦配置解密失败");
        }
        return new JSONObject(decrypted);
    }

    /**
     * 初始化 token 与签名（线程安全，双重检查锁）。
     */
    public final void ensureInitialized() {
        if (!TextUtils.isEmpty(token) && !TextUtils.isEmpty(signature)) {
            return;
        }
        synchronized (lock) {
            if (!TextUtils.isEmpty(token) && !TextUtils.isEmpty(signature)) {
                return;
            }
            try {
                HashMap<String, String> params = new HashMap<>();
                params.put("invited_by", "");
                params.put("ua", UA_MOBILE);
                params.put("is_install", "1");
                JSONObject result = fetchApi("/new_public/init_v2", params, true).optJSONObject("result");
                if (result != null) {
                    JSONObject userInfo = result.optJSONObject("user_info");
                    if (userInfo != null) {
                        String userToken = userInfo.optString("token");
                        if (!TextUtils.isEmpty(userToken)) {
                            token = userToken;
                        }
                        String userAppId = userInfo.optString("app_id");
                        if (!TextUtils.isEmpty(userAppId)) {
                            appId = userAppId;
                        }
                        String userChannelCode = userInfo.optString("channel_code");
                        if (!TextUtils.isEmpty(userChannelCode)) {
                            channelCode = userChannelCode;
                        }
                    }
                }
            } catch (Exception e) {
                // 忽略初始化异常
            }
            signature = generateSignature();
        }
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        try {
            ensureInitialized();
            String cateIdKey = "cateId";
            String sortKey = "sort";
            String areaKey = "area";
            String empty = "";

            String cateId = tid;
            String classValue = empty;
            String typeValue = empty;
            String areaValue = empty;
            String sortValue = empty;
            String yearValue = empty;

            if (extend != null) {
                try {
                    if (!TextUtils.isEmpty(extend.get(cateIdKey))) {
                        cateId = extend.get(cateIdKey);
                    }
                    // 注意：classValue 默认回退到 typeValue（与 smali 中 e(class_value, type_value) 一致）
                    typeValue = getOrDefault(extend.get("type"), empty);
                    classValue = getOrDefault(extend.get("class"), typeValue);
                    areaValue = getOrDefault(extend.get(areaKey), empty);
                    yearValue = getOrDefault(extend.get("year"), empty);
                    sortValue = getOrDefault(extend.get(sortKey), empty);
                } catch (Exception e) {
                    // 忽略参数解析异常
                }
            }

            int page;
            try {
                page = Integer.parseInt(pg);
            } catch (Exception e) {
                page = 1;
            }
            if (page <= 0) page = 1;

            HashMap<String, String> params = new HashMap<>();
            params.put(areaKey, areaValue);
            params.put(sortKey, sortValue);
            params.put("type", classValue);
            params.put("year", yearValue);
            params.put("pn", String.valueOf(page));
            params.put("type_id", getOrDefault(cateId, empty));

            JSONObject response = fetchApi("/new_search/screen_v2", params, false);
            ArrayList<Vod> list = parseVodList(response);
            return Result.get().page(page, page + 1, 20, 0).vod(list).string();
        } catch (Exception e) {
            return Result.string(new ArrayList<>());
        }
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        ensureInitialized();
        String vodId = ids.get(0);
        String empty = "";
        HashMap<String, String> params = new HashMap<>();
        params.put("sig", empty);
        params.put("nc_token", empty);
        params.put("code", empty);
        params.put("phone", empty);
        params.put("session_id", empty);
        params.put("vod_id", vodId);

        JSONObject response = fetchApi("/new_video/result_v2", params, false);
        JSONObject result = response.optJSONObject("result");
        if (result == null) {
            throw new Exception("悦悦详情获取失败");
        }

        Vod vod = new Vod(vodId, result.optString("vod_name"), result.optString("vod_pic"));
        vod.setVodYear(result.optString("vod_year"));
        vod.setVodArea(result.optString("vod_area"));
        vod.setTypeName(result.optString("vod_tag"));
        vod.setVodActor(result.optString("vod_actor"));
        vod.setVodDirector(result.optString("vod_director"));

        String blurb = result.optString("vod_blurb");
        if (!TextUtils.isEmpty(blurb)) {
            blurb = blurb.replaceAll("<[^>]*>", empty).trim();
        } else {
            blurb = empty;
        }
        vod.setVodContent(blurb);

        JSONArray collections = result.optJSONArray("vod_collection");
        ArrayList<String> episodes = new ArrayList<>();
        if (collections != null) {
            for (int i = 0; i < collections.length(); i++) {
                JSONObject item = collections.optJSONObject(i);
                if (item == null) continue;
                String title = item.optString("title").trim();
                String collectionId = item.optString("id").trim();
                if (TextUtils.isEmpty(title) || TextUtils.isEmpty(collectionId)) continue;
                String vodToken = item.optString("vod_token");
                String curTime = item.optString("cur_time");
                String playUrl = new StringBuilder(collectionId)
                        .append(FIELD_SEP).append(vodToken)
                        .append(FIELD_SEP).append(curTime)
                        .append(FIELD_SEP).append(vodId).toString();
                episodes.add(title + KV_SEP + playUrl);
            }
        }

        String playFrom = episodes.isEmpty() ? empty : PLAY_FROM;
        vod.setVodPlayFrom(playFrom);
        vod.setVodPlayUrl(TextUtils.join(EPISODE_SEP, episodes));
        return Result.string(vod);
    }

    /**
     * 解析筛选条件数组。
     * name 字段映射：area→地区、sort→排序、type→类型、year→年份
     */
    public final ArrayList<Filter> parseFilters(JSONArray array) {
        ArrayList<Filter> filters = new ArrayList<>();
        if (array == null) return filters;
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item == null) continue;
            String name = item.optString("name");
            String title;
            String key;
            switch (name) {
                case "area":
                    title = "地区";
                    key = "area";
                    break;
                case "sort":
                    title = "排序";
                    key = "sort";
                    break;
                case "type":
                    title = "类型";
                    key = "class";
                    break;
                case "year":
                    title = "年份";
                    key = "year";
                    break;
                default:
                    continue;
            }
            JSONArray data = item.optJSONArray("data");
            if (data == null) continue;
            ArrayList<Filter.Value> values = new ArrayList<>();
            for (int j = 0; j < data.length(); j++) {
                String value = String.valueOf(data.opt(j)).trim();
                if (TextUtils.isEmpty(value) || "全部".equals(value) || "排序".equals(value)) continue;
                values.add(new Filter.Value(value, value));
            }
            if (!values.isEmpty()) {
                filters.add(new Filter(key, title, values));
            }
        }
        return filters;
    }

    /**
     * 从 JSON 响应解析 VOD 列表。
     */
    public final ArrayList<Vod> parseVodList(JSONObject response) {
        ArrayList<Vod> list = new ArrayList<>();
        if (response == null) return list;
        JSONArray result = response.optJSONArray("result");
        if (result == null) return list;
        for (int i = 0; i < result.length(); i++) {
            JSONObject item = result.optJSONObject(i);
            if (item == null) continue;
            String id = item.optString("id");
            String name = item.optString("vod_name");
            if (TextUtils.isEmpty(id) || TextUtils.isEmpty(name)) continue;
            String pic = item.optString("vod_pic");
            String remarks = item.optString("remarks");
            list.add(new Vod(id, name, pic, remarks));
        }
        return list;
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        try {
            ensureInitialized();
            JSONObject response = fetchApi("/new_type/list_v2", new HashMap<>(), false);
            JSONArray result = response.optJSONArray("result");
            ArrayList<Class> classes = new ArrayList<>();
            LinkedHashMap<String, List<Filter>> filterMap = new LinkedHashMap<>();
            if (result != null) {
                for (int i = 0; i < result.length(); i++) {
                    JSONObject item = result.optJSONObject(i);
                    if (item == null) continue;
                    String id = item.optString("id").trim();
                    String name = item.optString("name").trim();
                    if (TextUtils.isEmpty(id) || TextUtils.isEmpty(name)) continue;
                    classes.add(new Class(id, name));
                    ArrayList<Filter> filters = parseFilters(item.optJSONArray("msg"));
                    if (!filters.isEmpty()) {
                        filterMap.put(id, filters);
                    }
                }
            }
            return Result.string(classes, filterMap);
        } catch (Exception e) {
            return Result.string(new ArrayList<>(), new LinkedHashMap<>());
        }
    }

    @Override
    public void init(Context context, String extend) throws Exception {
        super.init(context, extend);
        if (!TextUtils.isEmpty(extend)) {
            String ext = extend.trim();
            if (ext.startsWith("{")) {
                try {
                    JSONObject json = new JSONObject(ext);
                    String url = json.optString("url", "");
                    if (!TextUtils.isEmpty(url)) {
                        apiUrl = trimTrailingSlash(url);
                    }
                } catch (Exception e) {
                    // 忽略 JSON 解析异常
                }
            } else if (ext.startsWith("http")) {
                apiUrl = trimTrailingSlash(ext);
            }
        }
        if (TextUtils.isEmpty(deviceId)) {
            byte[] bytes = new byte[8];
            new Random().nextBytes(bytes);
            StringBuilder sb = new StringBuilder(16);
            for (byte b : bytes) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            deviceId = sb.toString();
        }
        ensureInitialized();
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        ensureInitialized();
        String[] parts = id.split("\\|\\|\\|", -1);
        if (parts.length < 4) {
            throw new Exception("悦悦播放参数错误");
        }
        String empty = "";
        HashMap<String, String> params = new HashMap<>();
        params.put("sig", empty);
        params.put("nc_token", empty);
        params.put("code", empty);
        params.put("phone", empty);
        params.put("session_id", empty);
        params.put("collection_id", parts[0]);
        params.put("vod_token", parts[1]);
        params.put("cur_time", parts[2]);
        params.put("vod_id", parts[3]);

        JSONObject response = fetchApi("/new_video/collection_v2", params, false);
        JSONObject result = response.optJSONObject("result");
        if (result == null) {
            throw new Exception("悦悦播放地址为空");
        }
        String vodUrl = result.optString("vod_url").trim();
        String ck = result.optString("ck").trim();
        if (TextUtils.isEmpty(vodUrl) || TextUtils.isEmpty(ck)) {
            throw new Exception("悦悦播放地址为空");
        }

        URL url = new URL(vodUrl);
        StringBuilder domain = new StringBuilder();
        domain.append(url.getProtocol()).append("://").append(url.getHost());
        if (url.getPort() > 0) {
            domain.append(":").append(url.getPort());
        }
        String path = url.getPath();

        StringBuilder proxyUrl = new StringBuilder();
        proxyUrl.append(Proxy.getUrl());
        proxyUrl.append("?do=yueyue&type=m3u8&domain=");
        proxyUrl.append(Base64.encodeToString(domain.toString().getBytes(StandardCharsets.UTF_8), BASE64_URL_FLAGS));
        proxyUrl.append("&path=");
        proxyUrl.append(Base64.encodeToString(path.getBytes(StandardCharsets.UTF_8), BASE64_URL_FLAGS));
        proxyUrl.append("&ck=");
        proxyUrl.append(URLEncoder.encode(ck, "UTF-8"));

        HashMap<String, String> header = new HashMap<>();
        header.put("User-Agent", "Mozi");
        header.put("Accept", "*/*");
        header.put("Badci", TextUtils.isEmpty(signature) ? generateSignature() : signature);

        return Result.get()
                .url(proxyUrl.toString())
                .parse(0)
                .header(header)
                .string();
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        return searchContent(key, quick, "1");
    }

    @Override
    public String searchContent(String key, boolean quick, String pg) throws Exception {
        ensureInitialized();
        if (TextUtils.isEmpty(key)) {
            return Result.string(new ArrayList<>());
        }
        try {
            int page;
            try {
                page = Integer.parseInt(pg);
            } catch (Exception e) {
                page = 1;
            }
            if (page <= 0) page = 1;

            HashMap<String, String> params = new HashMap<>();
            params.put("kw", key.trim());
            params.put("pn", String.valueOf(page));

            JSONObject response = fetchApi("/new_search/result_v2", params, false);
            ArrayList<Vod> list = parseVodList(response);
            return Result.get().page(page, page + 1, 20, 0).vod(list).string();
        } catch (Exception e) {
            return Result.string(new ArrayList<>());
        }
    }
}
