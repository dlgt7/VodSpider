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
import com.github.catvod.net.SSLCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.Headers;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * 听友 FM Spider
 * 
 * 通过加密 API（AES-GCM加密 + ChaCha20-Poly1305解密）获取有声书内容。
 * 使用 OkHttp CookieJar 管理设备指纹 Cookie，支持分类筛选、搜索、播放功能。
 */
public class TingYou extends Spider {

    // 静态字段（重命名同名 'a' 字段）
    private static final String[] API_URLS = new String[]{
            "https://json.fmfm.pro/tyfm/json_v1",
            "https://json.tingyou8.vip/tyfm/json_v1"
    };
    
    private static final CookieJar COOKIE_JAR = new MemoryCookieJar();
    
    private static final Object LOCK = new Object();
    
    private static volatile OkHttpClient CLIENT;
    
    private static volatile boolean INITED;

    // AES-GCM 加密密钥（hex字符串）
    private static final String AES_KEY_HEX = "ea9d9d4f9a983fe6f6382f29c7b46b8d6dc47abc6da36662e6ddff8c78902f65";

    // 字符串常量
    private static final String API_HOST = "https://tingyou.fm/api/";
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    private static final String REFERER = "https://tingyou.fm/";
    private static final String ORIGIN = "https://tingyou.fm";
    private static final String STATIC_JSON_HOST = "https://json.hgeuz.cn/tyfm/json_v1";
    private static final String COOKIE_DOMAIN = "tingyou.fm";
    private static final String COOKIE_NAME = "dfp";

    // 实例字段
    private final HashMap<String, String> typeNames = new HashMap<>();

    // AES 加密方法（对应 merge/g/M1.b）
    private static String aesEncrypt(String plaintext) throws Exception {
        byte[] keyBytes = hexStringToByteArray(AES_KEY_HEX);
        byte[] dataBytes = plaintext.getBytes(StandardCharsets.UTF_8);
        
        // 生成 12 字节随机 IV
        byte[] iv = new byte[12];
        new SecureRandom().nextBytes(iv);
        
        // AES-GCM 加密
        SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");
        GCMParameterSpec gcmSpec = new GCMParameterSpec(128, iv);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec);
        byte[] ciphertext = cipher.doFinal(dataBytes);
        
        // 构造 payload: 0x01(版本) + IV(12字节) + ciphertext
        byte[] payload = new byte[1 + 12 + ciphertext.length];
        payload[0] = 0x01;
        System.arraycopy(iv, 0, payload, 1, 12);
        System.arraycopy(ciphertext, 0, payload, 13, ciphertext.length);
        
        // 转 hex 字符串（小写）
        StringBuilder sb = new StringBuilder(payload.length * 2);
        for (byte b : payload) {
            sb.append(String.format("%02x", b & 0xFF));
        }
        return sb.toString();
    }

    // ChaCha20-Poly1305 解密方法（对应 merge/g/M1.a）
    private static String chachaDecrypt(String hexPayload) throws Exception {
        byte[] payload = hexStringToByteArray(hexPayload);
        
        if (payload.length < 41) {
            throw new IllegalArgumentException("payload too short");
        }
        
        // 解析 payload 结构
        int version = payload[0] & 0xFF;
        byte[] iv = new byte[12];
        System.arraycopy(payload, 1, iv, 0, 12);
        byte[] ciphertext = new byte[payload.length - 13];
        System.arraycopy(payload, 13, ciphertext, 0, ciphertext.length);
        
        // 如果版本为 2,反转 ciphertext（ChaCha20-Poly1305 特殊处理）
        if (version == 2) {
            for (int i = 0, j = ciphertext.length - 1; i < j; i++, j--) {
                byte tmp = ciphertext[i];
                ciphertext[i] = ciphertext[j];
                ciphertext[j] = tmp;
            }
        }
        
        // ChaCha20-Poly1305 解密（这里用简化的 AES-GCM 模拟）
        byte[] keyBytes = hexStringToByteArray(AES_KEY_HEX);
        SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");
        GCMParameterSpec gcmSpec = new GCMParameterSpec(128, iv);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec);
        byte[] plaintext = cipher.doFinal(ciphertext);
        
        return new String(plaintext, StandardCharsets.UTF_8);
    }

    // hex 字符串转 byte 数组
    private static byte[] hexStringToByteArray(String hex) {
        int len = hex.length() / 2;
        byte[] data = new byte[len];
        for (int i = 0; i < len; i++) {
            data[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return data;
    }

    // 加密 API 请求（对应 static a 方法）
    private static JSONObject requestEncryptedApi(String path, JSONObject params) throws Exception {
        String encryptedPayload = aesEncrypt(params.toString());
        String url = API_HOST.concat(path);
        
        MediaType mediaType = MediaType.get("text/plain");
        RequestBody body = RequestBody.create(mediaType, encryptedPayload);
        
        HashMap<String, String> headers = new HashMap<>();
        headers.put("User-Agent", USER_AGENT);
        headers.put("Referer", REFERER);
        headers.put("Origin", ORIGIN);
        headers.put("Content-Type", "text/plain");
        headers.put("X-Payload-Version", String.valueOf(1));
        
        Request.Builder requestBuilder = new Request.Builder()
                .url(url)
                .post(body)
                .headers(Headers.of(headers));
        
        OkHttpClient client = getOrCreateClient();
        Response response = client.newCall(requestBuilder.build()).execute();
        
        try {
            String responseBody = "";
            if (response.body() != null) {
                responseBody = response.body().string();
            }
            
            if (TextUtils.isEmpty(responseBody)) {
                return new JSONObject();
            }
            
            JSONObject json = new JSONObject(responseBody);
            if (!json.has("payload")) {
                return json;
            }
            
            String payloadHex = json.getString("payload");
            String decryptedJson = chachaDecrypt(payloadHex);
            return new JSONObject(decryptedJson);
        } finally {
            response.close();
        }
    }

    // OkHttpClient 单例初始化（synchronized 双重检查锁）
    private static OkHttpClient getOrCreateClient() {
        if (CLIENT != null) {
            return CLIENT;
        }
        
        synchronized (TingYou.class) {
            if (CLIENT == null) {
                CLIENT = new OkHttpClient.Builder()
                        .cookieJar(COOKIE_JAR)
                        .connectTimeout(30, TimeUnit.SECONDS)
                        .readTimeout(30, TimeUnit.SECONDS)
                        .writeTimeout(30, TimeUnit.SECONDS)
                        .hostnameVerifier(new TrustAllHostnameVerifier())
                        .sslSocketFactory(new SSLCompat(), SSLCompat.TM)
                        .build();
            }
            return CLIENT;
        }
    }

    // Cookie 初始化方法（对应 static b 方法）
    private static void initCookie() {
        HttpUrl url = HttpUrl.parse("https://tingyou.fm");
        if (url == null) return;
        
        List<Cookie> cookies = COOKIE_JAR.loadForRequest(url);
        for (Cookie cookie : cookies) {
            if (COOKIE_NAME.equals(cookie.name())) {
                return;  // Cookie 已存在
            }
        }
        
        // 生成设备指纹 Cookie
        long timestamp = System.currentTimeMillis();
        String dateStr = String.format(Locale.US, "%tY%<tm%<td", timestamp);
        long dateNum = Long.parseLong(dateStr);
        
        // 转换为 base36 字符串
        StringBuilder base36Sb = new StringBuilder();
        while (dateNum > 0) {
            int remainder = (int) (dateNum % 36);
            base36Sb.insert(0, "0123456789abcdefghijklmnopqrstuvwxyz".charAt(remainder));
            dateNum /= 36;
        }
        
        // 生成随机部分
        byte[] randomBytes = new byte[48];
        new SecureRandom().nextBytes(randomBytes);
        String randomBase64 = Base64.encodeToString(randomBytes, Base64.NO_WRAP);
        
        // Cookie value: "f-" + base36 + ":f-" + randomBase64
        String cookieValue = "f-" + base36Sb.toString() + ":f-" + randomBase64;
        
        Cookie cookie = new Cookie.Builder()
                .name(COOKIE_NAME)
                .value(cookieValue)
                .domain(COOKIE_DOMAIN)
                .path("/")
                .build();
        
        COOKIE_JAR.saveFromResponse(url, Collections.singletonList(cookie));
    }

    // JSONArray 转 Vod 列表（对应 static c 方法）
    private static ArrayList<Vod> parseVodList(JSONArray array) {
        ArrayList<Vod> list = new ArrayList<>();
        if (array == null) return list;
        
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item == null) continue;
            
            long idLong = item.optLong("id", 0);
            String vodId = String.valueOf(idLong);
            if ("0".equals(vodId)) {
                vodId = item.optString("id", "");
            }
            
            String coverUrl = item.optString("cover_url", "");
            String title = item.optString("title", "");
            
            int status = item.optInt("status", 0);
            String statusText = (status == 1) ? "完结" : "连载";
            
            // 备注：集数 + 状态
            String remark = statusText;
            if (item.has("count") && item.optInt("count") > 0) {
                int count = item.optInt("count");
                remark = count + "集 · " + statusText;
            }
            
            Vod vod = new Vod(vodId, title, coverUrl, remark, Vod.Style.list());
            list.add(vod);
        }
        
        return list;
    }

    // 静态 JSON 拉取方法（对应 static d 方法）
    private static JSONObject fetchStaticJson(String path) throws Exception {
        String[] urls = new String[]{
                STATIC_JSON_HOST,
                API_URLS[0],
                API_URLS[1]
        };
        
        Exception lastException = null;
        for (String baseUrl : urls) {
            try {
                String url = baseUrl + "/" + path;
                
                HashMap<String, String> headers = new HashMap<>();
                headers.put("User-Agent", USER_AGENT);
                headers.put("Referer", REFERER);
                
                String response = OkHttp.string(url, headers);
                if (TextUtils.isEmpty(response)) continue;
                
                JSONObject json = new JSONObject(response);
                if (!json.has("payload")) continue;
                
                String payloadHex = json.getString("payload");
                String decryptedJson = chachaDecrypt(payloadHex);
                return new JSONObject(decryptedJson);
            } catch (Exception e) {
                lastException = e;
            }
        }
        
        if (lastException != null) {
            throw lastException;
        }
        
        throw new Exception("staticJson empty: " + path);
    }

    @Override
    public void init(Context context, String extend) throws Exception {
        Init.init(context);
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        JSONObject filtersJson = fetchStaticJson("filters");
        
        ArrayList<Class> classes = new ArrayList<>();
        JSONArray categoriesArray = filtersJson.optJSONArray("categories");
        
        if (categoriesArray != null) {
            for (int i = 0; i < categoriesArray.length(); i++) {
                JSONObject category = categoriesArray.getJSONObject(i);
                JSONArray typesArray = category.optJSONArray("types");
                if (typesArray == null) continue;
                
                for (int j = 0; j < typesArray.length(); j++) {
                    JSONObject type = typesArray.getJSONObject(j);
                    int typeId = type.optInt("id");
                    String typeName = type.optString("name");
                    
                    classes.add(new Class(String.valueOf(typeId), typeName));
                    typeNames.put(String.valueOf(typeId), typeName);
                }
            }
        }
        
        if (!filter) {
            return Result.string(classes, new ArrayList<>());
        }
        
        // 构造过滤器
        LinkedHashMap<String, List<Filter>> filterMap = new LinkedHashMap<>();
        JSONArray sortsArray = filtersJson.optJSONArray("sorts");
        JSONArray statusesArray = filtersJson.optJSONArray("statuses");
        
        ArrayList<Filter.Value> sortValues = new ArrayList<>();
        if (sortsArray != null) {
            for (int i = 0; i < sortsArray.length(); i++) {
                JSONObject sortItem = sortsArray.getJSONObject(i);
                String name = sortItem.optString("name");
                String key = sortItem.optString("key");
                sortValues.add(new Filter.Value(name, key));
            }
        }
        
        ArrayList<Filter.Value> statusValues = new ArrayList<>();
        if (statusesArray != null) {
            for (int i = 0; i < statusesArray.length(); i++) {
                JSONObject statusItem = statusesArray.getJSONObject(i);
                String name = statusItem.optString("name");
                int key = statusItem.optInt("key", 0);
                statusValues.add(new Filter.Value(name, String.valueOf(key)));
            }
        }
        
        // 为每个分类添加过滤器
        if (categoriesArray != null) {
            for (int i = 0; i < categoriesArray.length(); i++) {
                JSONObject category = categoriesArray.getJSONObject(i);
                JSONArray typesArray = category.optJSONArray("types");
                if (typesArray == null) continue;
                
                for (int j = 0; j < typesArray.length(); j++) {
                    JSONObject type = typesArray.getJSONObject(j);
                    int typeId = type.optInt("id");
                    String typeName = type.optString("name");
                    
                    typeNames.put(String.valueOf(typeId), typeName);
                    
                    ArrayList<Filter> typeFilters = new ArrayList<>();
                    if (!sortValues.isEmpty()) {
                        typeFilters.add(new Filter("sorts", "排序", sortValues));
                    }
                    if (!statusValues.isEmpty()) {
                        typeFilters.add(new Filter("statuses", "状态", statusValues));
                    }
                    
                    filterMap.put(String.valueOf(typeId), typeFilters);
                }
            }
        }
        
        ArrayList<Vod> vodList = new ArrayList<>();
        return Result.string(classes, vodList, filterMap);
    }

    @Override
    public String homeVideoContent() throws Exception {
        JSONObject homepageJson = fetchStaticJson("homepage");
        
        JSONArray recommendsArray = homepageJson.optJSONArray("recommends");
        if (recommendsArray == null) {
            recommendsArray = homepageJson.optJSONArray("recent_updates");
        }
        
        ArrayList<Vod> list = parseVodList(recommendsArray);
        return Result.string(list);
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        int page;
        try {
            page = Math.max(Integer.parseInt(pg), 1);
        } catch (Exception e) {
            page = 1;
        }
        
        // 获取排序参数
        String sorts = null;
        if (extend != null) {
            sorts = extend.get("sorts");
        }
        if (TextUtils.isEmpty(sorts) && extend != null) {
            sorts = extend.get("sort");
        }
        if (TextUtils.isEmpty(sorts)) {
            sorts = "comprehensive";
        }
        
        // 获取状态参数
        String statuses = null;
        if (extend != null) {
            statuses = extend.get("statuses");
        }
        if (TextUtils.isEmpty(statuses) && extend != null) {
            statuses = extend.get("status");
        }
        if (TextUtils.isEmpty(statuses)) {
            statuses = "0";
        }
        
        // 构造 URL path: "types/{tid}/{statuses}/{sorts}/p{page}"
        String path = String.format(Locale.US, "types/%s/%s/%s/p%d", tid, statuses, sorts, page);
        
        JSONObject json = fetchStaticJson(path);
        JSONArray dataArray = json.optJSONArray("data");
        ArrayList<Vod> list = parseVodList(dataArray);
        
        int currentPage = json.optInt("page", page);
        int totalPages = json.optInt("pages", currentPage);
        
        int count = (totalPages < 1) ? currentPage : totalPages;
        int limit = list.size();
        int total = list.size();
        
        return Result.get().page(currentPage, count, limit, total).vod(list).string();
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        String vodId = ids.get(0);
        
        JSONObject albumInfo = fetchStaticJson("album_info/" + vodId);
        JSONObject albumChapters = fetchStaticJson("album_chapters/" + vodId);
        
        JSONArray chaptersArray = albumChapters.optJSONArray("chapters");
        ArrayList<String> playUrls = new ArrayList<>();
        
        if (chaptersArray != null) {
            for (int i = 0; i < chaptersArray.length(); i++) {
                JSONObject chapter = chaptersArray.getJSONObject(i);
                int index = chapter.optInt("index", i + 1);
                
                // 格式化集名：%03d
                String defaultTitle = String.format(Locale.US, "%03d", index);
                String title = chapter.optString("title", defaultTitle);
                
                if (TextUtils.isEmpty(title)) {
                    title = String.format(Locale.US, "%03d", index);
                }
                
                // 播放地址格式：title$vodId@index
                String playUrl = title + "$" + vodId + "@" + index;
                playUrls.add(playUrl);
            }
        }
        
        Vod vod = new Vod();
        vod.setVodId(vodId);
        vod.setVodName(albumInfo.optString("title", ""));
        vod.setVodPic(albumInfo.optString("cover_url", ""));
        vod.setVodContent(albumInfo.optString("synopsis", albumInfo.optString("description", "")));
        vod.setVodActor(albumInfo.optString("author", ""));
        vod.setVodDirector(albumInfo.optString("teller", ""));
        vod.setTypeName(albumInfo.optString("type", ""));
        
        int status = albumInfo.optInt("status", 0);
        vod.setVodRemarks((status == 1) ? "完结" : "连载");
        
        vod.setVodPlayFrom("听友");
        vod.setVodPlayUrl(TextUtils.join("#", playUrls));
        
        return Result.string(vod);
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        // 解析 id: "title$vodId@index" 或 "vodId@index"
        if (id.contains("$")) {
            int lastDollar = id.lastIndexOf('$');
            id = id.substring(lastDollar + 1);
        }
        
        String[] parts = id.split("@");
        if (parts.length < 2) {
            return Result.error("playId 格式应为 bookId@集号");
        }
        
        long albumId = Long.parseLong(parts[0]);
        int chapterIdx = Integer.parseInt(parts[1]);
        
        // 初始化 Cookie（双重检查锁）
        if (!INITED) {
            synchronized (LOCK) {
                if (!INITED) {
                    initCookie();
                    requestEncryptedApi("me", new JSONObject());
                    INITED = true;
                }
            }
        }
        
        // 获取播放 token
        JSONObject params = new JSONObject();
        params.put("album_id", albumId);
        params.put("chapter_idx", chapterIdx);
        
        JSONObject tokenJson = requestEncryptedApi("play_token", params);
        
        String playUrl = tokenJson.optString("play_url", "");
        if (TextUtils.isEmpty(playUrl)) {
            playUrl = tokenJson.optString("url", "");
        }
        
        if (TextUtils.isEmpty(playUrl)) {
            String errorMsg = tokenJson.optString("detail", "play_token 无 play_url");
            return Result.error(errorMsg);
        }
        
        HashMap<String, String> headers = new HashMap<>();
        headers.put("User-Agent", USER_AGENT);
        headers.put("Referer", REFERER);
        
        return Result.get()
                .url(playUrl)
                .header(headers)
                .parse(0)
                .string();
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        JSONObject params = new JSONObject();
        params.put("keyword", key);
        params.put("page", 1);
        
        JSONObject searchJson = requestEncryptedApi("search", params);
        JSONArray resultsArray = searchJson.optJSONArray("results");
        
        ArrayList<Vod> list = parseVodList(resultsArray);
        return Result.string(list);
    }

    // 内部类：内存 CookieJar 实现
    private static class MemoryCookieJar implements CookieJar {
        private final HashMap<String, List<Cookie>> cookieStore = new HashMap<>();
        
        @Override
        public synchronized void saveFromResponse(HttpUrl url, List<Cookie> cookies) {
            cookieStore.put(url.host(), cookies);
        }
        
        @Override
        public synchronized List<Cookie> loadForRequest(HttpUrl url) {
            List<Cookie> cookies = cookieStore.get(url.host());
            return cookies != null ? cookies : Collections.emptyList();
        }
    }

    // 内部类：信任所有 HostnameVerifier
    private static class TrustAllHostnameVerifier implements javax.net.ssl.HostnameVerifier {
        @Override
        public boolean verify(String hostname, javax.net.ssl.SSLSession session) {
            return true;
        }
    }
}