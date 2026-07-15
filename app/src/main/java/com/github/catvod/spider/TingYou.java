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

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import okhttp3.Headers;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * 听友爬虫（有声书）
 * API地址：https://tingyou.fm/api/
 */
public class TingYou extends Spider {

    private static final String API_BASE = "https://tingyou.fm/api/";
    private static final String REFERER = "https://tingyou.fm/";
    private static final String ORIGIN = "https://tingyou.fm";
    private static final String CONTENT_TYPE = "text/plain";
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    private static final String PLAY_FROM = "听友";

    private static final String[] JSON_URLS = {
            "https://json.hgeuz.cn/tyfm/json_v1",
            "https://json.fmfm.pro/tyfm/json_v1",
            "https://json.tingyou8.vip/tyfm/json_v1"
    };

    /** XChaCha20-Poly1305静态密钥（对应 I2.a） */
    private static final byte[] CRYPTO_KEY = hexToBytes("ea9d9d4f9a983fe6f6382f29c7b46b8d6dc47abc6da36662e6ddff8c78902f65");

    /** ChaCha20常量 "expand 32-byte k" */
    private static final int SIGMA_0 = 0x61707865;
    private static final int SIGMA_1 = 0x3320646e;
    private static final int SIGMA_2 = 0x79622d32;
    private static final int SIGMA_3 = 0x6b206574;

    private static final String BASE36_CHARS = "0123456789abcdefghijklmnopqrstuvwxyz";

    private static volatile OkHttpClient httpClient;
    private static volatile boolean initialized = false;
    private static final Object initLock = new Object();
    private static String dfpCookie = null;

    private final HashMap<String, String> categoryNames = new HashMap<>();

    // ==================== 加密/解密方法 ====================

    /** 十六进制字符串转字节数组（对应 I2.c） */
    private static byte[] hexToBytes(String hex) {
        int len = hex.length() / 2;
        byte[] bytes = new byte[len];
        for (int i = 0; i < len; i++) {
            bytes[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return bytes;
    }

    /** 小端读取4字节整数（对应 I2.e） */
    private static int littleEndianInt(byte[] bytes, int offset) {
        return (bytes[offset] & 0xff) | ((bytes[offset + 1] & 0xff) << 8)
                | ((bytes[offset + 2] & 0xff) << 16) | ((bytes[offset + 3] & 0xff) << 24);
    }

    /** ChaCha20四分之一轮（对应 I2.d） */
    private static void quarterRound(int[] state, int a, int b, int c, int d) {
        state[a] += state[b];
        state[d] = Integer.rotateLeft(state[a] ^ state[d], 16);
        state[c] += state[d];
        state[b] = Integer.rotateLeft(state[c] ^ state[b], 12);
        state[a] += state[b];
        state[d] = Integer.rotateLeft(state[a] ^ state[d], 8);
        state[c] += state[d];
        state[b] = Integer.rotateLeft(state[c] ^ state[b], 7);
    }

    /**
     * HChaCha20密钥派生（从XChaCha20的24字节nonce中提取前16字节作为HChaCha20 nonce）
     * 返回32字节子密钥
     */
    private static byte[] hChaCha20(byte[] key, byte[] nonce16) {
        int[] state = new int[16];
        state[0] = SIGMA_0;
        state[1] = SIGMA_1;
        state[2] = SIGMA_2;
        state[3] = SIGMA_3;
        for (int i = 0; i < 8; i++) {
            state[4 + i] = littleEndianInt(key, i * 4);
        }
        for (int i = 0; i < 4; i++) {
            state[12 + i] = littleEndianInt(nonce16, i * 4);
        }
        for (int i = 0; i < 10; i++) {
            quarterRound(state, 0, 4, 8, 12);
            quarterRound(state, 1, 5, 9, 13);
            quarterRound(state, 2, 6, 10, 14);
            quarterRound(state, 3, 7, 11, 15);
            quarterRound(state, 0, 5, 10, 15);
            quarterRound(state, 1, 6, 11, 12);
            quarterRound(state, 2, 7, 8, 13);
            quarterRound(state, 3, 4, 9, 14);
        }
        byte[] output = new byte[32];
        for (int i = 0; i < 4; i++) {
            int val = state[i];
            output[i * 4] = (byte) val;
            output[i * 4 + 1] = (byte) (val >>> 8);
            output[i * 4 + 2] = (byte) (val >>> 16);
            output[i * 4 + 3] = (byte) (val >>> 24);
        }
        for (int i = 0; i < 4; i++) {
            int val = state[12 + i];
            output[16 + i * 4] = (byte) val;
            output[16 + i * 4 + 1] = (byte) (val >>> 8);
            output[16 + i * 4 + 2] = (byte) (val >>> 16);
            output[16 + i * 4 + 3] = (byte) (val >>> 24);
        }
        return output;
    }

    /**
     * XChaCha20-Poly1305解密（对应 I2.a）
     * 载荷格式：1字节版本 + 24字节nonce + 密文（含16字节Poly1305 MAC）
     */
    private static String decryptPayload(String hex) throws Exception {
        byte[] data = hexToBytes(hex);
        if (data.length < 41) {
            throw new IllegalArgumentException("payload too short");
        }
        int version = data[0] & 0xff;
        byte[] nonce24 = new byte[24];
        System.arraycopy(data, 1, nonce24, 0, 24);
        int cipherLen = data.length - 25;
        byte[] ciphertext = new byte[cipherLen];
        System.arraycopy(data, 25, ciphertext, 0, cipherLen);

        // 版本2：反转密文
        if (version == 2) {
            int left = 0;
            int right = cipherLen - 1;
            while (left < right) {
                byte tmp = ciphertext[left];
                ciphertext[left] = ciphertext[right];
                ciphertext[right] = tmp;
                left++;
                right--;
            }
        }

        // HChaCha20密钥派生：使用24字节nonce的前16字节
        byte[] subKey = hChaCha20(CRYPTO_KEY, nonce24);

        // 构造12字节AEAD nonce：4字节0 + nonce24的后8字节
        byte[] aeadNonce = new byte[12];
        System.arraycopy(nonce24, 16, aeadNonce, 4, 8);

        // 使用标准JCA的ChaCha20-Poly1305解密
        Cipher cipher = Cipher.getInstance("ChaCha20-Poly1305");
        SecretKeySpec keySpec = new SecretKeySpec(subKey, "ChaCha20");
        GCMParameterSpec paramSpec = new GCMParameterSpec(128, aeadNonce);
        cipher.init(Cipher.DECRYPT_MODE, keySpec, paramSpec);
        byte[] plaintext = cipher.doFinal(ciphertext);
        return new String(plaintext, StandardCharsets.UTF_8);
    }

    /**
     * AES-GCM加密（对应 I2.b）
     * 输出格式：1字节版本(1) + 12字节IV + 密文（含16字节MAC）→ 十六进制字符串
     */
    private static String encryptPayload(String plain) throws Exception {
        byte[] bytes = plain.getBytes(StandardCharsets.UTF_8);
        byte[] iv = new byte[12];
        new SecureRandom().nextBytes(iv);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(CRYPTO_KEY, "AES"), new GCMParameterSpec(128, iv));
        byte[] encrypted = cipher.doFinal(bytes);
        int totalLen = encrypted.length + 13;
        byte[] result = new byte[totalLen];
        result[0] = 1;
        System.arraycopy(iv, 0, result, 1, 12);
        System.arraycopy(encrypted, 0, result, 13, encrypted.length);
        StringBuilder sb = new StringBuilder(totalLen * 2);
        for (byte b : result) {
            sb.append(String.format("%02x", b & 0xff));
        }
        return sb.toString();
    }

    // ==================== API请求方法 ====================

    /** 构建通用请求头 */
    private static HashMap<String, String> buildHeaders() {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("User-Agent", USER_AGENT);
        headers.put("Referer", REFERER);
        headers.put("Origin", ORIGIN);
        headers.put("Content-Type", CONTENT_TYPE);
        headers.put("X-Payload-Version", "1");
        return headers;
    }

    /** 获取带Cookie的OkHttpClient */
    private static OkHttpClient getClient() {
        if (httpClient == null) {
            synchronized (TingYou.class) {
                if (httpClient == null) {
                    httpClient = new OkHttpClient.Builder()
                            .connectTimeout(30, TimeUnit.SECONDS)
                            .readTimeout(30, TimeUnit.SECONDS)
                            .writeTimeout(30, TimeUnit.SECONDS)
                            .build();
                }
            }
        }
        return httpClient;
    }

    /**
     * 发送加密POST请求（对应原 a 方法）
     * @param path API路径（如 "play_token"、"search"）
     * @param body JSON请求体
     * @return 解密后的JSON响应
     */
    private static JSONObject apiRequest(String path, JSONObject body) throws Exception {
        String encryptedBody = encryptPayload(body.toString());
        String url = API_BASE + path;
        RequestBody requestBody = RequestBody.create(MediaType.get(CONTENT_TYPE), encryptedBody);
        HashMap<String, String> headers = buildHeaders();
        if (dfpCookie != null) {
            headers.put("Cookie", "dfp=" + dfpCookie);
        }
        Request request = new Request.Builder()
                .url(url)
                .post(requestBody)
                .headers(Headers.of(headers))
                .build();
        try (Response response = getClient().newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            if (TextUtils.isEmpty(responseBody)) {
                return new JSONObject();
            }
            JSONObject json = new JSONObject(responseBody);
            if (!json.has("payload")) {
                return json;
            }
            return new JSONObject(decryptPayload(json.getString("payload")));
        }
    }

    /**
     * 生成dfp Cookie（对应原 b 方法）
     * 格式：f-{timestamp_base36}:f-{base64_random}
     */
    private static void generateDfpCookie() {
        StringBuilder sb = new StringBuilder();
        String timestamp = String.format(Locale.US, "%tY%<tm%<td", System.currentTimeMillis());
        long tsNum = Long.parseLong(timestamp);
        while (tsNum > 0) {
            sb.insert(0, BASE36_CHARS.charAt((int) (tsNum % 36)));
            tsNum /= 36;
        }
        byte[] randomBytes = new byte[48];
        new SecureRandom().nextBytes(randomBytes);
        dfpCookie = "f-" + sb + ":f-" + Base64.encodeToString(randomBytes, Base64.NO_WRAP);
    }

    /**
     * 从静态JSON服务器获取数据（对应原 d 方法）
     * 依次尝试3个URL，返回解密后的JSON
     */
    private static JSONObject fetchStaticJson(String path) throws Exception {
        Exception lastError = null;
        for (int i = 0; i < 3; i++) {
            try {
                String url = JSON_URLS[i] + "/" + path;
                HashMap<String, String> headers = new HashMap<>();
                headers.put("User-Agent", USER_AGENT);
                headers.put("Referer", REFERER);
                String response = OkHttp.string(url, headers);
                if (!TextUtils.isEmpty(response)) {
                    JSONObject json = new JSONObject(response);
                    if (json.has("payload")) {
                        return new JSONObject(decryptPayload(json.getString("payload")));
                    }
                }
            } catch (Exception e) {
                lastError = e;
            }
        }
        if (lastError != null) {
            throw lastError;
        }
        throw new Exception("staticJson empty: " + path);
    }

    /**
     * 解析列表（对应原 c 方法）
     */
    private static ArrayList<Vod> parseList(JSONArray jsonArray) {
        ArrayList<Vod> list = new ArrayList<>();
        if (jsonArray == null) {
            return list;
        }
        for (int i = 0; i < jsonArray.length(); i++) {
            JSONObject item = jsonArray.optJSONObject(i);
            if (item != null) {
                String id = String.valueOf(item.optLong("id", 0L));
                if ("0".equals(id)) {
                    id = item.optString("id", "");
                }
                String coverUrl = item.optString("cover_url", "");
                String remarks = item.optInt("status", 0) == 1 ? "完结" : "连载";
                if (item.has("count") && item.optInt("count") > 0) {
                    remarks = item.optInt("count") + "集 · " + remarks;
                }
                list.add(new Vod(id, item.optString("title", ""), coverUrl, remarks, Vod.Style.list()));
            }
        }
        return list;
    }

    // ==================== Spider方法 ====================

    @Override
    public void init(Context context, String extend) throws Exception {
        Init.init(context);
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        JSONObject homeJson = fetchStaticJson("filters");
        ArrayList<Class> classes = new ArrayList<>();
        JSONArray categoriesArray = homeJson.optJSONArray("categories");
        if (categoriesArray != null) {
            for (int i = 0; i < categoriesArray.length(); i++) {
                JSONArray typesArray = categoriesArray.getJSONObject(i).optJSONArray("types");
                if (typesArray != null) {
                    for (int j = 0; j < typesArray.length(); j++) {
                        JSONObject type = typesArray.getJSONObject(j);
                        String id = String.valueOf(type.optInt("id"));
                        String name = type.optString("name");
                        classes.add(new Class(id, name));
                        categoryNames.put(id, name);
                    }
                }
            }
        }
        if (!filter) {
            return Result.string(classes, new ArrayList<>());
        }
        LinkedHashMap<String, List<Filter>> filters = new LinkedHashMap<>();
        JSONArray sortsArray = homeJson.optJSONArray("sorts");
        JSONArray statusesArray = homeJson.optJSONArray("statuses");
        JSONArray categoriesForFilters = homeJson.optJSONArray("categories");
        if (categoriesForFilters == null) {
            return Result.string(classes, new ArrayList<>(), filters);
        }
        ArrayList<Filter.Value> sortValues = new ArrayList<>();
        if (sortsArray != null) {
            for (int i = 0; i < sortsArray.length(); i++) {
                JSONObject sort = sortsArray.getJSONObject(i);
                sortValues.add(new Filter.Value(sort.optString("name"), sort.optString("key")));
            }
        }
        ArrayList<Filter.Value> statusValues = new ArrayList<>();
        if (statusesArray != null) {
            for (int i = 0; i < statusesArray.length(); i++) {
                JSONObject status = statusesArray.getJSONObject(i);
                statusValues.add(new Filter.Value(status.optString("name"), String.valueOf(status.optInt("key", 0))));
            }
        }
        for (int i = 0; i < categoriesForFilters.length(); i++) {
            JSONArray typesArray = categoriesForFilters.getJSONObject(i).optJSONArray("types");
            if (typesArray != null) {
                for (int j = 0; j < typesArray.length(); j++) {
                    JSONObject type = typesArray.getJSONObject(j);
                    String id = String.valueOf(type.optInt("id"));
                    categoryNames.put(id, type.optString("name"));
                    ArrayList<Filter> filterList = new ArrayList<>();
                    if (!sortValues.isEmpty()) {
                        filterList.add(new Filter("sorts", "排序", sortValues));
                        filterList.add(new Filter("statuses", "状态", statusValues));
                    }
                    filters.put(id, filterList);
                }
            }
        }
        return Result.string(classes, new ArrayList<>(), filters);
    }

    @Override
    public String homeVideoContent() throws Exception {
        JSONObject homeJson = fetchStaticJson("homepage");
        JSONArray listArray = homeJson.optJSONArray("recommends");
        if (listArray == null) {
            listArray = homeJson.optJSONArray("recent_updates");
        }
        return Result.string(parseList(listArray));
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        int page;
        try {
            page = Math.max(Integer.parseInt(pg), 1);
        } catch (Exception e) {
            page = 1;
        }
        String sort = extend != null ? extend.get("sorts") : null;
        if (TextUtils.isEmpty(sort) && extend != null) {
            sort = extend.get("sort");
        }
        if (TextUtils.isEmpty(sort)) {
            sort = "comprehensive";
        }
        String status = extend != null ? extend.get("statuses") : null;
        if (TextUtils.isEmpty(status) && extend != null) {
            status = extend.get("status");
        }
        if (TextUtils.isEmpty(status)) {
            status = "0";
        }
        JSONObject json = fetchStaticJson(String.format(Locale.US, "types/%s/%s/%s/p%d", tid, status, sort, page));
        ArrayList<Vod> list = parseList(json.optJSONArray("data"));
        int currentPage = json.optInt("page", page);
        int totalPages = json.optInt("pages", currentPage);
        if (totalPages < 1) {
            totalPages = currentPage;
        }
        return Result.string(currentPage, totalPages, list.size(), list.size(), list);
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        String bookId = ids.get(0);
        JSONObject info = fetchStaticJson("album_info/" + bookId);
        JSONArray chapters = fetchStaticJson("album_chapters/" + bookId).optJSONArray("chapters");
        ArrayList<String> episodes = new ArrayList<>();
        if (chapters != null) {
            for (int i = 0; i < chapters.length(); i++) {
                JSONObject chapter = chapters.getJSONObject(i);
                int index = chapter.optInt("index", i + 1);
                String title = chapter.optString("title", String.format(Locale.US, "%03d", index));
                if (TextUtils.isEmpty(title)) {
                    title = String.format(Locale.US, "%03d", index);
                }
                episodes.add(title + "$" + bookId + "@" + index);
            }
        }
        Vod vod = new Vod();
        vod.setVodId(bookId);
        vod.setVodName(info.optString("title", ""));
        vod.setVodPic(info.optString("cover_url", ""));
        vod.setVodContent(info.optString("synopsis", info.optString("description", "")));
        vod.setVodActor(info.optString("author", ""));
        vod.setVodDirector(info.optString("teller", ""));
        vod.setTypeName(info.optString("type", ""));
        vod.setVodRemarks(info.optInt("status", 0) == 1 ? "完结" : "连载");
        vod.setVodPlayFrom(PLAY_FROM);
        vod.setVodPlayUrl(TextUtils.join("#", episodes));
        return Result.string(vod);
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        if (id.contains("$")) {
            id = id.substring(id.lastIndexOf('$') + 1);
        }
        String[] parts = id.split("@");
        if (parts.length < 2) {
            return Result.error("playId 格式应为 bookId@集号");
        }
        try {
            long albumId = Long.parseLong(parts[0]);
            int chapterIdx = Integer.parseInt(parts[1]);
            JSONObject requestBody = new JSONObject();
            requestBody.put("album_id", albumId);
            requestBody.put("chapter_idx", chapterIdx);
            if (!initialized) {
                synchronized (initLock) {
                    if (!initialized) {
                        generateDfpCookie();
                        apiRequest("me", new JSONObject());
                        initialized = true;
                    }
                }
            }
            JSONObject response = apiRequest("play_token", requestBody);
            String playUrl = response.optString("play_url", "");
            if (TextUtils.isEmpty(playUrl)) {
                playUrl = response.optString("url", "");
            }
            if (TextUtils.isEmpty(playUrl)) {
                return Result.error(response.optString("detail", "play_token 无 play_url"));
            }
            HashMap<String, String> headers = new HashMap<>();
            headers.put("User-Agent", USER_AGENT);
            headers.put("Referer", REFERER);
            return Result.get().url(playUrl).header(headers).parse(0).string();
        } catch (Exception e) {
            return Result.error(PLAY_FROM + " play_token 失败: " + e.getMessage());
        }
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        JSONObject requestBody = new JSONObject();
        requestBody.put("keyword", key);
        requestBody.put("page", 1);
        JSONObject response = apiRequest("search", requestBody);
        return Result.string(parseList(response.optJSONArray("results")));
    }
}
