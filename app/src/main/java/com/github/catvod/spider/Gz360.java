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
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Gz360 Spider - 加密型视频源
 * 使用 AES + RSA 混合加密，支持多域名轮换
 */
public class Gz360 extends Spider {

    // ==================== 静态常量 ====================

    // API URL 数组（多域名备份）
    private static final String[] API_URLS = {
        "https://m.82mao.com",
        "https://m.82mao.xyz",
        "https://m.82mao.fun",
        "https://m.82mao.top",
        "https://m.82mao.org"
    };

    // AES 加密密钥和 IV（从 smali 解码验证）
    private static final String AES_KEY = "OITxa5OqAYjhswxx";
    private static final String AES_IV = "rCMNwZASNBKZ8mXV";

    // RSA 公钥（Base64，从 smali 解码验证）
    private static final String RSA_PUBLIC_KEY_BASE64 = "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDUM5+/y8sPsWkd1/RQS64X259EUwxFXFE5HlA65MqrxnPs0JqoSRojSDy5QhwvROlaD6TwRQHKMY2OAZ6SnQeUJsChTEFIR9qUkwrs3/MVUMxjsv6JS6Oe/juclyJGTgVmDhB55EafXsD0SQYVj/QXXsxR6ewR5E2kL52yAAD4yQIDAQAB";

    // RSA 私钥（Base64，从 smali 解密获取）
    private static final String RSA_PRIVATE_KEY_TEMPLATE =
        "-----BEGIN PRIVATE KEY-----\n" +
        "MIICdgIBADANBgkqhkiG9w0BAQEFAASCAmAwggJcAgEAAoGAe6hKrWLi1zQmjTT1\n" +
        "ozbE4QdFeJGNxubxld6GrFGximxfMsMB6BpJhpcTouAqywAFppiKetUBBbXwYsYU\n" +
        "1wNr648XVmPmCMCy4rY8vdliFnbMUj086DU6Z+/oXBdWU3/b1G0DN3E9wULRSwcK\n" +
        "ZT3wj/cCI1vsCm3gj2R5SqkA9Y0CAwEAAQKBgAJH+4CxV0/zBVcLiBCHvSANm0l7\n" +
        "HetybTh/j2p0Y1sTXro4ALwAaCTUeqdBjWiLSo9lNwDHFyq8zX90+gNxa7c5EqcW\n" +
        "V9FmlVXr8VhfBzcZo1nXeNdXFT7tQ2yah/odtdcx+vRMSGJd1t/5k5bDd9wAvYdI\n" +
        "DblMAg+wiKKZ5KcdAkEA1cCakEN4NexkF5tHPRrR6XOY/XHfkqXxEhMqmNbB9U34\n" +
        "saTJnLWIHC8IXys6Qmzz30TtzCjuOqKRRy+FMM4TdwJBAJQZFPjsGC+RqcG5UvVM\n" +
        "iMPhnwe/bXEehShK86yJK/g/UiKrO87h3aEu5gcJqBygTq3BBBoH2md3pr/W+hUM\n" +
        "WBsCQQChfhTIrdDinKi6lRxrdBnn0Ohjg2cwuqK5zzU9p/N+S9x7Ck8wUI53DKm8\n" +
        "jUJE8WAG7WLj/oCOWEh+ic6NIwTdAkEAj0X8nhx6AXsgCYRql1klbqtVmL8+95KZ\n" +
        "K7PnLWG/IfjQUy3pPGoSaZ7fdquG8bq8oyf5+dzjE/oTXcByS+6XRQJAP/5ciy1b\n" +
        "L3NhUhsaOVy55MHXnPjdcTX0FaLi+ybXZIfIQ2P4rb19mVq1feMbCXhz+L1rG8oa\n" +
        "t5lYKfpe8k83ZA==\n" +
        "-----END PRIVATE KEY-----";

    // 随机字符表（用于生成随机字符串）
    private static final String RANDOM_CHARS = "0123456789abcdef";

    // ==================== 实例字段 ====================

    // 重命名同名字段
    private final HashMap<String, String> headersMap = new HashMap<>();
    private int urlIndex = 0;
    private String currentUrl = API_URLS[0];
    private boolean initialized = false;

    // b/c/d/e 字段
    private String timestamp;      // b - 时间戳
    private String randomStr;      // c - 随机字符串
    private String token;          // d - 令牌
    private String userId;         // e - 用户ID

    // ==================== 构造函数 ====================

    public Gz360() {
        headersMap.put("1", "电影");
        headersMap.put("2", "电视剧");
        headersMap.put("3", "综艺");
        headersMap.put("4", "动漫");
        headersMap.put("ext", "");
    }

    // ==================== Spider 标准方法 ====================

    @Override
    public void init(Context context, String extend) throws Exception {
        super.init(context, extend);

        // 初始化时间戳（随机偏移）
        Random random = new Random();
        long baseTime = 8639954892000L;
        timestamp = String.valueOf(baseTime + random.nextInt(10000));

        // 生成随机字符串（40位）
        StringBuilder sb = new StringBuilder(40);
        for (int i = 0; i < 40; i++) {
            sb.append(RANDOM_CHARS.charAt(random.nextInt(16)));
        }
        randomStr = sb.toString();

        // 初始化请求
        try {
            fetchInitData();
            fetchToken();
        } catch (Exception e) {
            // 忽略初始化失败
        }
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        // 5个分类（从 smali 解码验证）
        List<Class> classes = Arrays.asList(
            new Class("1", "电影"),
            new Class("2", "电视剧"),
            new Class("3", "综艺"),
            new Class("4", "动漫"),
            new Class("5", "少儿")
        );

        // 首页不获取推荐列表，返回空列表
        ArrayList<Vod> list = new ArrayList<>();

        // 添加过滤器
        LinkedHashMap<String, List<Filter>> filters = buildFilters();

        return Result.string(classes, list, filter ? filters : null);
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        ArrayList<Vod> list = new ArrayList<>();

        try {
            JsonObject params = new JsonObject();
            params.addProperty("tid", tid);                     // 正确字段名
            params.addProperty("page", pg);
            params.addProperty("sort", extend != null ? extend.getOrDefault("sort", "0") : "0");  // 正确字段名
            params.addProperty("area", extend != null ? extend.getOrDefault("area", "0") : "0");  // 正确字段名

            // sub 字段：子类（从 extend 获取或使用默认）
            String sub = extend != null ? extend.getOrDefault("class", (String) headersMap.get(tid)) : headersMap.get(tid);
            params.addProperty("sub", sub);

            // year 字段
            params.addProperty("year", extend != null ? extend.getOrDefault("year", "0") : "0");

            // pageSize 固定为 30
            params.addProperty("pageSize", "30");

            JsonObject data = fetchEncryptedApi("/App/IndexList/indexList", params, false);
            if (data == null || !data.has("list")) {
                return Result.get().vod(list).page(Integer.parseInt(pg), 9999, 30, 0).string();
            }

            JsonArray items = data.getAsJsonArray("list");
            for (int i = 0; i < items.size(); i++) {
                JsonObject item = items.get(i).getAsJsonObject();
                Vod vod = parseVodItem(item);
                if (vod != null) {
                    list.add(vod);
                }
            }

            return Result.get().vod(list).page(Integer.parseInt(pg), 9999, 30, list.size()).string();
        } catch (Exception e) {
            return Result.get().vod(list).page(Integer.parseInt(pg), 9999, 30, 0).string();
        }
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        try {
            String vodId = ids.get(0);

            // 第一次请求：获取详情信息
            JsonObject params1 = new JsonObject();
            params1.addProperty("token_id", userId);       // 正确字段名
            params1.addProperty("vod_id", vodId);          // 正确字段名（解码验证：仓乓业乆仌乘 → vod_id）
            params1.addProperty("mobile_time", String.valueOf(System.currentTimeMillis() / 1000)); // 正确字段名
            params1.addProperty("token", token);

            JsonObject data1 = fetchEncryptedApi("/App/IndexPlay/playInfo", params1, false);
            if (data1 == null || !data1.has("vodInfo")) {
                return Result.error("详情获取失败");
            }

            JsonObject detailData = data1.getAsJsonObject("vodInfo");

            // 第二次请求：获取播放列表
            JsonObject params2 = new JsonObject();
            params2.addProperty("vurl_cloud_id", "2");     // 正确字段名和值
            params2.addProperty("vod_id", vodId);          // 正确字段名

            JsonObject data2 = fetchEncryptedApi("/App/Resource/Vurl/show", params2, false);

            // 构建播放列表映射（线路名 -> 集数列表）
            LinkedHashMap<String, List<String>> playMap = new LinkedHashMap<>();
            if (data2 != null && data2.has("list")) {
                JsonArray playList = data2.getAsJsonArray("list");

                for (int i = 0; i < playList.size(); i++) {
                    JsonObject episode = playList.get(i).getAsJsonObject();

                    // 跳过特定类型（show_type 匹配时）
                    String showType = getString(episode, "show_type");
                    if ("2".equals(showType)) {
                        continue;
                    }

                    String title = getString(episode, "title");
                    String url = getString(episode, "param");  // 使用 param 字段

                    if (TextUtils.isEmpty(title) || TextUtils.isEmpty(url)) {
                        continue;
                    }

                    // 解析线路名和播放URL
                    // play 字段包含实际的URL信息（可能有多个清晰度）
                    String playUrl = getString(episode, "play");
                    if (TextUtils.isEmpty(playUrl)) {
                        playUrl = url;
                    }

                    // 使用默认线路
                    String lineName = "默认";
                    if (!playMap.containsKey(lineName)) {
                        playMap.put(lineName, new ArrayList<>());
                    }

                    // 格式：title$url
                    playMap.get(lineName).add(title + "$" + playUrl);
                }
            }

            // 构建Vod对象
            Vod vod = new Vod();
            vod.setVodId(vodId);
            vod.setVodName(getString(detailData, "vod_name"));         // 正确字段名
            vod.setVodPic(getString(detailData, "vod_pic"));           // 正确字段名
            vod.setVodYear(getString(detailData, "vod_year"));         // 正确字段名
            vod.setVodArea(getString(detailData, "vod_area"));         // 正确字段名
            vod.setVodDirector(getString(detailData, "vod_director")); // 正确字段名
            vod.setVodActor(getString(detailData, "vod_actor"));       // 正确字段名
            vod.setVodContent(getString(detailData, "vod_use_content")); // 正确字段名
            vod.setVodRemarks(getString(detailData, "vod_scroe"));     // 评分字段

            // 设置播放信息
            if (!playMap.isEmpty()) {
                ArrayList<String> playFrom = new ArrayList<>();
                ArrayList<String> playUrl = new ArrayList<>();

                for (Map.Entry<String, List<String>> entry : playMap.entrySet()) {
                    playFrom.add(entry.getKey());
                    playUrl.add(join("#", entry.getValue()));
                }

                vod.setVodPlayFrom(join("$$$", playFrom));
                vod.setVodPlayUrl(join("$$$", playUrl));
            }

            return Result.string(vod);
        } catch (Exception e) {
            return Result.error("详情获取失败: " + e.getMessage());
        }
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        // 解析播放URL
        String[] parts = id.split("@");
        String playUrl = parts[0];

        // 如果URL包含多个清晰度，选择最佳
        if (parts.length > 1) {
            playUrl = parts[parts.length - 1];
        }

        JsonObject result = new JsonObject();
        result.addProperty("url", playUrl);
        result.addProperty("parse", 1);
        result.addProperty("jx", 1);

        return result.toString();
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        ArrayList<Vod> list = new ArrayList<>();

        try {
            JsonObject params = new JsonObject();
            params.addProperty("keywords", key);     // 正确字段名
            params.addProperty("order_val", "1");    // 固定值

            JsonObject data = fetchEncryptedApi("/App/Index/findMoreVod", params, false);
            if (data == null || !data.has("list")) {
                return Result.string(list);
            }

            JsonArray items = data.getAsJsonArray("list");
            for (int i = 0; i < items.size(); i++) {
                JsonObject item = items.get(i).getAsJsonObject();
                Vod vod = parseVodItem(item);
                if (vod != null) {
                    list.add(vod);
                }
            }
        } catch (Exception e) {
            // 忽略搜索失败
        }

        return Result.string(list);
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 获取首页推荐列表
     */
    private ArrayList<Vod> getHomeVideoList() throws Exception {
        ArrayList<Vod> list = new ArrayList<>();
        try {
            JsonObject params = new JsonObject();
            params.addProperty("type", "recommend");
            params.addProperty("page", "1");

            JsonObject data = fetchEncryptedApi("/api/v1/home", params, false);
            if (data == null || !data.has("list")) {
                return list;
            }

            JsonArray items = data.getAsJsonArray("list");
            for (int i = 0; i < items.size(); i++) {
                JsonObject item = items.get(i).getAsJsonObject();
                Vod vod = parseVodItem(item);
                if (vod != null) {
                    list.add(vod);
                }
            }
        } catch (Exception e) {
            // 忽略失败
        }
        return list;
    }

    /**
     * 构建过滤器
     */
    private static LinkedHashMap<String, List<Filter>> buildFilters() {
        LinkedHashMap<String, List<Filter>> filters = new LinkedHashMap<>();

        // 分类筛选
        filters.put("class", Arrays.asList(
            new Filter("class", "类型", Arrays.asList(
                new Filter.Value("全部", "全部"),
                new Filter.Value("动作", "动作"),
                new Filter.Value("喜剧", "喜剧"),
                new Filter.Value("爱情", "爱情"),
                new Filter.Value("科幻", "科幻"),
                new Filter.Value("悬疑", "悬疑")
            ))
        ));

        // 年份筛选
        filters.put("year", Arrays.asList(
            new Filter("year", "年份", Arrays.asList(
                new Filter.Value("全部", "全部"),
                new Filter.Value("2024", "2024"),
                new Filter.Value("2023", "2023"),
                new Filter.Value("2022", "2022"),
                new Filter.Value("2021", "2021")
            ))
        ));

        // 地区筛选
        filters.put("area", Arrays.asList(
            new Filter("area", "地区", Arrays.asList(
                new Filter.Value("全部", "全部"),
                new Filter.Value("中国大陆", "中国大陆"),
                new Filter.Value("中国香港", "中国香港"),
                new Filter.Value("美国", "美国"),
                new Filter.Value("韩国", "韩国")
            ))
        ));

        // 排序
        filters.put("sort", Arrays.asList(
            new Filter("sort", "排序", Arrays.asList(
                new Filter.Value("最新", "最新"),
                new Filter.Value("最热", "最热"),
                new Filter.Value("评分", "评分")
            ))
        ));

        return filters;
    }

    /**
     * 初始化请求（获取 userId）- 解码验证的 API 路径
     */
    private void fetchInitData() throws Exception {
        JsonObject params = new JsonObject();
        params.addProperty("random", randomStr);                          // 解码验证：介乙三乆从乙万 → random
        params.addProperty("old_key", "aLFBMWpxBrIDAD1Si/KVvm41");        // 解码验证：今乐业乆从乙万 → old_key
        params.addProperty("phone_type", 1);                              // 解码验证：仕乔丑乷什乣上习仕乙 → phone_type
        params.addProperty("code", "");                                   // 解码验证：仆乓业乼 → code

        JsonObject data = fetchEncryptedApi("/App/Authentication/Device/signUp", params, true);
        if (data != null) {
            userId = getString(data, "userId");
            initialized = true;
        }
    }

    /**
     * 获取 token - 解码验证的 API 路径
     */
    private void fetchToken() throws Exception {
        JsonObject params = new JsonObject();
        JsonObject data = fetchEncryptedApi("/App/Authentication/Authenticator/refresh", params, true);
        if (data != null) {
            token = getString(data, "token");
        }
    }

    /**
     * 执行加密 API 请求
     */
    private JsonObject fetchEncryptedApi(String path, JsonObject params, boolean forceInit) throws Exception {
        // 检查是否需要初始化
        if (!forceInit && (TextUtils.isEmpty(userId) || TextUtils.isEmpty(token))) {
            if (initialized) {
                fetchInitData();
                fetchToken();
            } else {
                fetchInitData();
                fetchToken();
            }
        }

        // AES 加密请求体
        String plainBody = params.toString();
        String encryptedBody = aesEncrypt(plainBody, AES_KEY, AES_IV);

        // RSA 加密 AES 密钥
        JsonObject keyInfo = new JsonObject();
        keyInfo.addProperty("key", AES_KEY);
        keyInfo.addProperty("iv", AES_IV);
        String rsaEncrypted = rsaEncrypt(keyInfo.toString(), RSA_PUBLIC_KEY_BASE64);

        // SHA-256 签名
        String signData = encryptedBody + ":" + timestamp + ":" + randomStr + ":" + userId;
        String sign = sha256Hex(signData).toUpperCase(Locale.ROOT);

        // 构建请求参数
        LinkedHashMap<String, String> bodyParams = new LinkedHashMap<>();
        bodyParams.put("data", userId);
        bodyParams.put("timestamp", "");
        bodyParams.put("version", "1");
        bodyParams.put("time", timestamp);
        bodyParams.put("platform", "Android");
        bodyParams.put("key", rsaEncrypted);
        bodyParams.put("body", encryptedBody);
        bodyParams.put("sign", sign);
        bodyParams.put("ext", "1");
        bodyParams.put("token", token);

        // 构建请求头
        HashMap<String, String> headers = new HashMap<>();
        headers.put("User-Agent", "Mozilla/5.0");
        headers.put("Content-Type", "application/x-www-form-urlencoded");
        headers.put("Accept", "application/json");
        headers.put("token", timestamp);
        headers.put("random", randomStr);

        // 执行 HTTP POST 请求
        String url = currentUrl + path;
        String response = OkHttp.post(url, bodyParams, headers);

        if (TextUtils.isEmpty(response)) {
            return null;
        }

        // 解析响应
        JsonObject json = JsonParser.parseString(response).getAsJsonObject();

        // 检查状态码
        if (json.has("code")) {
            int code = json.get("code").getAsInt();
            if (code != 200) {
                throw new Exception("API返回错误码: " + code);
            }
        }

        // 检查是否有加密数据
        if (json.has("data")) {
            JsonObject dataObj = json.getAsJsonObject("data");

            // 如果有加密的密钥和数据，需要解密
            if (dataObj.has("key") && dataObj.has("body")) {
                String encryptedKey = getString(dataObj, "key");
                String encryptedData = getString(dataObj, "body");

                // 使用 RSA 私钥解密 AES 密钥
                byte[] keyBytes = Base64.decode(encryptedKey, Base64.DEFAULT);
                PrivateKey privateKey = loadPrivateKey(RSA_PRIVATE_KEY_TEMPLATE);

                Cipher rsaCipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
                rsaCipher.init(Cipher.DECRYPT_MODE, privateKey);
                byte[] decryptedKey = rsaCipher.doFinal(keyBytes);
                String aesKeyJson = new String(decryptedKey, StandardCharsets.UTF_8);

                // 解析 AES 密钥
                JsonObject keyJson = JsonParser.parseString(aesKeyJson).getAsJsonObject();
                String aesKey = getString(keyJson, "key");
                String aesIv = getString(keyJson, "iv");

                // 使用 AES 解密数据
                byte[] encryptedBytes = Base64.decode(encryptedData, Base64.DEFAULT);
                byte[] decryptedBytes = aesDecrypt(encryptedBytes, aesKey, aesIv);
                String decryptedData = new String(decryptedBytes, StandardCharsets.UTF_8);

                return JsonParser.parseString(decryptedData).getAsJsonObject();
            }

            return dataObj;
        }

        return json;
    }

    /**
     * AES 加密
     */
    private String aesEncrypt(String data, String key, String iv) throws Exception {
        SecretKeySpec keySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "AES");
        IvParameterSpec ivSpec = new IvParameterSpec(iv.getBytes(StandardCharsets.UTF_8));

        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);

        byte[] encrypted = cipher.doFinal(data.getBytes(StandardCharsets.UTF_8));

        // 转为十六进制字符串
        StringBuilder sb = new StringBuilder();
        for (byte b : encrypted) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /**
     * AES 解密
     */
    private byte[] aesDecrypt(byte[] encrypted, String key, String iv) throws Exception {
        SecretKeySpec keySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "AES");
        IvParameterSpec ivSpec = new IvParameterSpec(iv.getBytes(StandardCharsets.UTF_8));

        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);

        return cipher.doFinal(encrypted);
    }

    /**
     * RSA 加密
     */
    private String rsaEncrypt(String data, String publicKeyStr) throws Exception {
        byte[] keyBytes = Base64.decode(publicKeyStr, Base64.DEFAULT);
        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(keyBytes);
        PublicKey publicKey = KeyFactory.getInstance("RSA").generatePublic(keySpec);

        Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        cipher.init(Cipher.ENCRYPT_MODE, publicKey);

        byte[] encrypted = cipher.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return Base64.encodeToString(encrypted, Base64.NO_WRAP);
    }

    /**
     * 加载 RSA 私钥
     */
    private PrivateKey loadPrivateKey(String privateKeyStr) throws Exception {
        // 去除 PEM 头尾标记和换行符
        String cleanKey = privateKeyStr
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replaceAll("\\s", "");

        byte[] keyBytes = Base64.decode(cleanKey, Base64.DEFAULT);
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);
        return KeyFactory.getInstance("RSA").generatePrivate(keySpec);
    }

    /**
     * SHA-256 哈希（十六进制）
     */
    private String sha256Hex(String data) throws Exception {
        java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
        byte[] digest = md.digest(data.getBytes(StandardCharsets.UTF_8));

        StringBuilder sb = new StringBuilder();
        for (byte b : digest) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /**
     * 解析 Vod 项
     */
    private Vod parseVodItem(JsonObject item) {
        try {
            String id = getString(item, "id");
            String name = getString(item, "name");
            String pic = getString(item, "pic");
            String remark = getString(item, "remark");

            if (TextUtils.isEmpty(id) || TextUtils.isEmpty(name)) {
                return null;
            }

            return new Vod(id, name, pic, remark);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 从 JsonObject 获取字符串
     */
    private static String getString(JsonObject obj, String key) {
        if (obj == null || !obj.has(key)) {
            return "";
        }
        try {
            JsonElement element = obj.get(key);
            if (element.isJsonNull()) {
                return "";
            }
            return element.getAsString();
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 连接字符串数组
     */
    private static String join(String delimiter, List<String> list) {
        if (list == null || list.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) {
                sb.append(delimiter);
            }
            sb.append(list.get(i));
        }
        return sb.toString();
    }
}