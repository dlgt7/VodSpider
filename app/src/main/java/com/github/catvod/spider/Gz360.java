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
 * Gz360 源爬虫实现。
 * 使用 AES+RSA 混合加密，支持多域名轮换、搜索、分类列表、详情解析及播放地址获取。
 */
public class Gz360 extends Spider {

    // ==================== 静态常量 ====================

    /** API 域名数组（多域名轮换） */
    private static final String[] API_URLS = {
        "https://apinew.uozvr.com",
        "https://api.w32z7vtd.com",
        "https://api.6a7nnf7.com",
        "https://api.umygrx3.com",
        "https://api.rmedphk.com"
    };

    /** AES 加密密钥（请求体加密） */
    private static final String AES_KEY = "OITxa5OqAYjhswxx";
    /** AES 加密 IV */
    private static final String AES_IV = "rCMNwZASNBKZ8mXV";
    /** AES 加密算法 */
    private static final String AES_ALGORITHM = "AES/CBC/PKCS5Padding";
    /** RSA 加密算法 */
    private static final String RSA_ALGORITHM = "RSA/ECB/PKCS1Padding";

    /** RSA 公钥（Base64，用于加密 AES key/iv） */
    private static final String RSA_PUBLIC_KEY_BASE64 =
        "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDUM5+/y8sPsWkd1/RQS64X259EUwxFXFE5HlA65MqrxnPs0JqoSRojSDy5QhwvROlaD6TwRQHKMY2OAZ6SnQeUJsChTEFIR9qUkwrs3/MVUMxjsv6JS6Oe/juclyJGTgVmDhB55EafXsD0SQYVj/QXXsxR6ewR5E2kL52yAAD4yQIDAQAB";

    /** RSA 私钥（PEM 格式，用于解密响应中的会话密钥） */
    private static final String RSA_PRIVATE_KEY_PEM =
        "-----BEGIN PRIVATE KEY-----\n" +
        "MIICdgIBADANBgkqhkiG9w0BAQEFAASCAmAwggJcAgEAAoGAe6hKrWLi1zQmjTT1\n" +
        "ozbE4QdFeJGNxubxld6GrFGximxfMsMB6BpJhpcTouAqywAFppiKetUBBbXwYsYU\n" +
        "1wNr648XVmPmCMCy4rY8vdliFnbMUj086DU6Z+/oXBdWU3/b1G0DN3E9wULRSwcK\n" +
        "ZT3wj/cCI1vsCm3gj2R5SqkA9Y0CAwEANQKBgAJH+4CxV0/zBVcLiBCHvSANm0l7\n" +
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

    /** 签名后缀（固定盐值） */
    private static final String SIGN_SUFFIX = "*&zvdvdvddbfikkkumtmdwqppp?|4Y!s!2br";
    /** 随机字符表（用于生成 deviceId） */
    private static final String HEX_CHARS = "0123456789ABCDEF";

    // ==================== 端点常量 ====================
    private static final String EP_SIGN_UP = "/App/Authentication/Device/signUp";
    private static final String EP_SIGN_IN = "/App/Authentication/Device/signIn";
    private static final String EP_REFRESH = "/App/Authentication/Authenticator/refresh";
    private static final String EP_INDEX_LIST = "/App/IndexList/indexList";
    private static final String EP_PLAY_INFO = "/App/IndexPlay/playInfo";
    private static final String EP_VURL_SHOW = "/App/Resource/Vurl/show";
    private static final String EP_VURL_DETAIL = "/App/Resource/VurlDetail/showOne";
    private static final String EP_FIND_MORE_VOD = "/App/Index/findMoreVod";

    // ==================== 请求头常量 ====================
    private static final String HEADER_UA = "Lavf/57.83.100";
    private static final String HEADER_CODE = "GZ0369";
    private static final String HEADER_LANG = "zh_cn";
    private static final String HEADER_VERSION = "2604028";
    private static final String HEADER_PACKAGE = "com.ae06aebdbb.y286327f5a.ofe849883320260517";
    private static final String HEADER_VER = "3.0.3.2";
    private static final String PHONE_MODEL = "xiaomi-25031";

    // ==================== 实例字段 ====================

    /** 分类默认值映射（cateId -> 默认 area 值） */
    private final HashMap<String, String> defaultAreaMap = new HashMap<>();
    /** 当前域名索引（用于轮换） */
    private int urlIndex = 0;
    /** 当前 API URL */
    private String currentUrl = API_URLS[0];
    /** 是否已完成注册（signUp） */
    private boolean registered = false;
    /** 设备 ID（时间戳偏移） */
    private String deviceId;
    /** 随机字符串（40 位） */
    private String randomStr;
    /** 认证令牌 */
    private String token = "";
    /** 用户 ID */
    private String userId = "";

    // ==================== 构造函数 ====================

    public Gz360() {
        defaultAreaMap.put("1", "5");
        defaultAreaMap.put("2", "12");
        defaultAreaMap.put("3", "30");
        defaultAreaMap.put("4", "22");
        defaultAreaMap.put("64", "");
    }

    // ==================== Spider 标准方法 ====================

    @Override
    public void init(Context context, String extend) throws Exception {
        super.init(context, extend);
        Random random = new Random();
        // 生成 deviceId（固定基准值 + 随机偏移）
        deviceId = String.valueOf(8639954892000L + random.nextInt(10000));
        // 生成 40 位随机字符串
        StringBuilder sb = new StringBuilder(40);
        for (int i = 0; i < 40; i++) {
            sb.append(HEX_CHARS.charAt(random.nextInt(16)));
        }
        randomStr = sb.toString();
        // 尝试注册和刷新令牌（失败忽略，后续请求会重试）
        try {
            signUp();
            refresh();
        } catch (Exception ignored) {
        }
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        List<Class> classes = Arrays.asList(
            new Class("1", "电影"),
            new Class("2", "国产剧"),
            new Class("3", "综艺"),
            new Class("4", "动漫"),
            new Class("64", "短剧")
        );
        LinkedHashMap<String, List<Filter>> filters = buildFilters();
        return Result.string(classes, new ArrayList<>(), filter ? filters : null);
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        ArrayList<Vod> list = new ArrayList<>();
        try {
            JsonObject params = new JsonObject();
            params.addProperty("tid", tid);
            params.addProperty("page", pg);
            // sort：默认 "d_id"（综合）
            String sort = (extend != null && extend.containsKey("sort")) ? extend.get("sort") : "d_id";
            params.addProperty("sort", sort);
            // sub：默认 "0"（全部）
            String sub = (extend != null && extend.containsKey("sub")) ? extend.get("sub") : "0";
            params.addProperty("sub", sub);
            // area：默认从分类映射取
            String area = (extend != null && extend.containsKey("area")) ? extend.get("area") : defaultAreaMap.get(tid);
            params.addProperty("area", area);
            // year：默认 "0"（全部）
            String year = (extend != null && extend.containsKey("year")) ? extend.get("year") : "0";
            params.addProperty("year", year);
            params.addProperty("pageSize", "30");

            JsonObject data = fetchWithRetry(params, EP_INDEX_LIST);
            if (data != null && data.has("list")) {
                JsonArray items = data.getAsJsonArray("list");
                for (JsonElement element : items) {
                    list.add(toVod(element.getAsJsonObject()));
                }
            }
        } catch (Exception ignored) {
        }
        int page = Integer.parseInt(pg);
        return Result.get().vod(list).page(page, 9999, 30, list.size()).string();
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        String vodId = ids.get(0);
        try {
            // 第一次请求：获取详情信息
            JsonObject params1 = new JsonObject();
            params1.addProperty("token_id", userId);
            params1.addProperty("vod_id", vodId);
            params1.addProperty("mobile_time", String.valueOf(System.currentTimeMillis() / 1000));
            params1.addProperty("token", token);

            JsonObject data1 = fetchWithRetry(params1, EP_PLAY_INFO);
            if (data1 == null || !data1.has("vodInfo")) {
                return Result.error("详情获取失败");
            }
            JsonObject vodInfo = data1.getAsJsonObject("vodInfo");

            // 第二次请求：获取播放列表（注意参数名为 vod_d_id，与第一次的 vod_id 不同）
            JsonObject params2 = new JsonObject();
            params2.addProperty("vurl_cloud_id", "2");
            params2.addProperty("vod_d_id", vodId);

            JsonObject data2 = fetchWithRetry(params2, EP_VURL_SHOW);

            // 解析播放列表（线路名 -> 集数列表）
            LinkedHashMap<String, List<String>> playMap = new LinkedHashMap<>();
            if (data2 != null && data2.has("list")) {
                JsonArray playList = data2.getAsJsonArray("list");
                for (JsonElement element : playList) {
                    JsonObject episode = element.getAsJsonObject();
                    String title = getString(episode, "title");
                    if (TextUtils.isEmpty(title) || !episode.has("play")) {
                        continue;
                    }
                    JsonObject playObj = episode.getAsJsonObject("play");
                    for (String lineName : playObj.keySet()) {
                        JsonObject lineData = playObj.getAsJsonObject(lineName);
                        // 跳过 show_type == "2" 的线路
                        if ("2".equals(getString(lineData, "show_type"))) {
                            continue;
                        }
                        String playUrl = getString(lineData, "param");
                        if (TextUtils.isEmpty(playUrl)) {
                            continue;
                        }
                        if (!playMap.containsKey(lineName)) {
                            playMap.put(lineName, new ArrayList<>());
                        }
                        playMap.get(lineName).add(title + "$" + playUrl);
                    }
                }
            }

            // 构建 Vod 对象
            Vod vod = new Vod();
            vod.setVodId(vodId);
            vod.setVodName(getString(vodInfo, "vod_name"));
            vod.setVodPic(getString(vodInfo, "vod_pic"));
            vod.setVodContent(getString(vodInfo, "vod_use_content"));
            vod.setVodActor(getString(vodInfo, "vod_actor"));
            vod.setVodDirector(getString(vodInfo, "vod_director"));
            vod.setVodArea(getString(vodInfo, "vod_area"));
            vod.setVodYear(getString(vodInfo, "vod_year"));
            vod.setVodRemarks(getString(vodInfo, "vod_scroe"));

            if (!playMap.isEmpty()) {
                ArrayList<String> playFrom = new ArrayList<>();
                ArrayList<String> playUrl = new ArrayList<>();
                for (Map.Entry<String, List<String>> entry : playMap.entrySet()) {
                    playFrom.add(entry.getKey());
                    playUrl.add(TextUtils.join("#", entry.getValue()));
                }
                vod.setVodPlayFrom(TextUtils.join("$$$", playFrom));
                vod.setVodPlayUrl(TextUtils.join("$$$", playUrl));
            }
            return Result.string(vod);
        } catch (Exception e) {
            return Result.error("详情获取失败");
        }
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipHeaders) throws Exception {
        // 解析 id 中的 key=value 对（以 & 分隔）
        JsonObject params = new JsonObject();
        String[] pairs = id.split("&");
        for (String pair : pairs) {
            int idx = pair.indexOf('=');
            if (idx > 0) {
                params.addProperty(pair.substring(0, idx), pair.substring(idx + 1));
            }
        }

        JsonObject data = fetchWithRetry(params, EP_VURL_DETAIL);
        String playUrl = "";
        if (data != null) {
            playUrl = getString(data, "url");
        }
        if (TextUtils.isEmpty(playUrl)) {
            return Result.error("播放链接解析失败");
        }

        // 构建播放请求头
        HashMap<String, String> headers = new HashMap<>();
        headers.put("User-Agent", HEADER_UA);
        headers.put("Referer", "http://WJiZxLXA2.com/");

        return Result.get().url(playUrl).parse(0).header(headers).string();
    }

    @Override
    public String searchContent(String keyword, boolean quick) throws Exception {
        JsonObject params = new JsonObject();
        params.addProperty("keywords", keyword);
        params.addProperty("order_val", "1");

        JsonObject data = fetchWithRetry(params, EP_FIND_MORE_VOD);
        ArrayList<Vod> list = new ArrayList<>();
        if (data != null && data.has("list")) {
            for (JsonElement element : data.getAsJsonArray("list")) {
                list.add(toVod(element.getAsJsonObject()));
            }
        }
        return Result.string(list);
    }

    // ==================== 核心加密与请求方法 ====================

    /**
     * 带重试的 API 请求（3 次外循环 × 5 次域名轮换）。
     * 失败后调用 signUp()+refresh() 重新认证。
     */
    private JsonObject fetchWithRetry(JsonObject params, String endpoint) {
        for (int outer = 0; outer < 3; outer++) {
            for (int inner = 0; inner < 5; inner++) {
                currentUrl = API_URLS[urlIndex];
                try {
                    JsonObject result = encryptAndFetch(params, endpoint, false);
                    if (result != null) {
                        return result;
                    }
                } catch (Exception ignored) {
                }
                urlIndex = (urlIndex + 1) % 5;
            }
            // 前 2 次外循环失败后重新认证
            if (outer < 2) {
                try {
                    registered = false;
                    signUp();
                    refresh();
                } catch (Exception ignored) {
                }
                urlIndex = 0;
            }
        }
        return null;
    }

    /**
     * 核心加密请求方法。
     * 1. 检查认证状态（必要时自动注册/刷新）
     * 2. AES 加密请求参数为十六进制字符串
     * 3. RSA 加密 AES key/iv 为 Base64
     * 4. 构建 MD5 签名
     * 5. 发送 POST 请求
     * 6. RSA 解密响应中的会话密钥
     * 7. AES 解密响应数据
     *
     * @param params   请求参数（JSON）
     * @param endpoint API 端点
     * @param forceAuth 是否强制重新认证（忽略已有 token）
     * @return 解密后的响应 JSON
     */
    private JsonObject encryptAndFetch(JsonObject params, String endpoint, boolean forceAuth) throws Exception {
        // 认证检查（非强制认证时）
        if (!forceAuth) {
            if (TextUtils.isEmpty(token) || TextUtils.isEmpty(userId)) {
                if (registered) {
                    // 已注册过，调用 signIn
                    JsonObject signInParams = new JsonObject();
                    signInParams.addProperty("new_key", randomStr);
                    signInParams.addProperty("old_key", PHONE_MODEL);
                    JsonObject signInResult = encryptAndFetch(signInParams, EP_SIGN_IN, true);
                    saveToken(signInResult);
                } else {
                    signUp();
                }
                refresh();
            }
        }

        // AES 加密请求参数
        String jsonStr = params.toString();
        byte[] aesEncrypted = aesEncrypt(jsonStr.getBytes(StandardCharsets.UTF_8));
        String aesHex = bytesToHex(aesEncrypted, "%02X");

        // RSA 加密 AES key/iv
        JsonObject keyIvJson = new JsonObject();
        keyIvJson.addProperty("iv", AES_IV);
        keyIvJson.addProperty("key", AES_KEY);
        byte[] rsaEncrypted = rsaEncrypt(keyIvJson.toString().getBytes(StandardCharsets.UTF_8));
        String rsaKey = Base64.encodeToString(rsaEncrypted, Base64.NO_WRAP);

        // 构建签名
        String time = String.valueOf(System.currentTimeMillis() / 1000);
        StringBuilder signBuilder = new StringBuilder();
        signBuilder.append("token_id=,token=").append(token);
        signBuilder.append(",phone_type=1,request_key=").append(aesHex);
        signBuilder.append(",app_id=1,time=").append(time);
        signBuilder.append(",keys=").append(rsaKey).append(SIGN_SUFFIX);
        String signature = md5Hex(signBuilder.toString()).toUpperCase(Locale.ROOT);

        // 构建请求体（LinkedHashMap 保持顺序）
        LinkedHashMap<String, String> body = new LinkedHashMap<>();
        body.put("token", token == null ? "" : token);
        body.put("token_id", "");
        body.put("phone_type", "1");
        body.put("time", time);
        body.put("phone_model", PHONE_MODEL);
        body.put("keys", rsaKey);
        body.put("response_key", aesHex);
        body.put("signature", signature);
        body.put("app_id", "1");
        body.put("ad_version", "1");

        // 构建请求 URL
        String url = currentUrl + endpoint;

        // 构建请求头
        HashMap<String, String> headers = new HashMap<>();
        headers.put("User-Agent", HEADER_UA);
        headers.put("code", HEADER_CODE);
        headers.put("deviceId", deviceId);
        headers.put("lang", HEADER_LANG);
        headers.put("Cache-Control", "no-cache");
        headers.put("Content-Type", "application/x-www-form-urlencoded");
        headers.put("Version", HEADER_VERSION);
        headers.put("PackageName", HEADER_PACKAGE);
        headers.put("Ver", HEADER_VER);
        headers.put("api-ver", HEADER_VER);
        headers.put("Referer", currentUrl);

        // 发送 POST 请求
        String response = OkHttp.post(url, body, headers);
        if (TextUtils.isEmpty(response)) {
            throw new Exception("空响应");
        }

        JsonObject respJson = JsonParser.parseString(response).getAsJsonObject();
        // 检查业务码
        if (respJson.has("code") && respJson.get("code").getAsInt() != 200) {
            throw new Exception("业务错误 code=" + respJson.get("code"));
        }
        if (!respJson.has("data")) {
            throw new Exception("无 data");
        }
        JsonObject data = respJson.getAsJsonObject("data");
        if (!data.has("keys") || !data.has("response_key")) {
            throw new Exception("缺加密封装");
        }

        // RSA 解密会话密钥
        String keysBase64 = data.get("keys").getAsString();
        String privKeyStr = RSA_PRIVATE_KEY_PEM
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replaceAll("\n", "");
        byte[] privKeyBytes = Base64.decode(privKeyStr, Base64.DEFAULT);
        PKCS8EncodedKeySpec privSpec = new PKCS8EncodedKeySpec(privKeyBytes);
        PrivateKey privateKey = KeyFactory.getInstance("RSA").generatePrivate(privSpec);

        // 分块解密 RSA（每块 128 字节）
        byte[] keysBytes = Base64.decode(keysBase64, Base64.DEFAULT);
        Cipher rsaCipher = Cipher.getInstance(RSA_ALGORITHM);
        rsaCipher.init(Cipher.DECRYPT_MODE, privateKey);
        StringBuilder keysDecrypted = new StringBuilder();
        for (int i = 0; i < keysBytes.length; i += 128) {
            int len = Math.min(128, keysBytes.length - i);
            byte[] block = Arrays.copyOfRange(keysBytes, i, i + len);
            byte[] decrypted = rsaCipher.doFinal(block);
            keysDecrypted.append(new String(decrypted, StandardCharsets.UTF_8));
        }
        JsonObject sessionKeys = JsonParser.parseString(keysDecrypted.toString().trim()).getAsJsonObject();
        String sessionKey = sessionKeys.get("key").getAsString();
        String sessionIv = sessionKeys.get("iv").getAsString();

        // AES 解密响应数据
        String responseHex = data.get("response_key").getAsString();
        byte[] responseBytes = hexToBytes(responseHex);
        byte[] decrypted = aesDecrypt(responseBytes, sessionKey, sessionIv);
        String decryptedStr = new String(decrypted, StandardCharsets.UTF_8);
        if (TextUtils.isEmpty(decryptedStr)) {
            throw new Exception("解密为空");
        }
        return JsonParser.parseString(decryptedStr).getAsJsonObject();
    }

    /**
     * 注册设备（signUp）。
     */
    private void signUp() throws Exception {
        JsonObject params = new JsonObject();
        params.addProperty("new_key", randomStr);
        params.addProperty("old_key", PHONE_MODEL);
        params.addProperty("phone_type", 1);
        params.addProperty("time", "");
        JsonObject result = encryptAndFetch(params, EP_SIGN_UP, true);
        saveToken(result);
        registered = true;
    }

    /**
     * 刷新令牌（refresh）。
     */
    private void refresh() throws Exception {
        JsonObject params = new JsonObject();
        JsonObject result = encryptAndFetch(params, EP_REFRESH, true);
        saveToken(result);
    }

    /**
     * 保存认证响应中的 token 和 userId。
     */
    private void saveToken(JsonObject response) throws Exception {
        if (response == null) {
            throw new Exception("认证响应为空");
        }
        String newToken = getString(response, "token");
        if (TextUtils.isEmpty(newToken)) {
            throw new Exception("认证失败，无 token");
        }
        token = newToken;
        String newUserId = getString(response, "app_user_id");
        if (!TextUtils.isEmpty(newUserId)) {
            userId = newUserId;
        }
    }

    // ==================== 工具方法 ====================

    /**
     * 构建首页过滤器。
     */
    private LinkedHashMap<String, List<Filter>> buildFilters() {
        LinkedHashMap<String, List<Filter>> filters = new LinkedHashMap<>();

        // 类型过滤器（sub）
        filters.put("sub", Arrays.asList(
            new Filter("sub", "类型", Arrays.asList(
                new Filter.Value("全部", "0"),
                new Filter.Value("动作片", "动作片"),
                new Filter.Value("喜剧片", "喜剧片"),
                new Filter.Value("爱情片", "爱情片"),
                new Filter.Value("科幻片", "科幻片"),
                new Filter.Value("恐怖片", "恐怖片"),
                new Filter.Value("剧情片", "剧情片")
            ))
        ));

        // 年份过滤器（year）
        filters.put("year", Arrays.asList(
            new Filter("year", "年份", Arrays.asList(
                new Filter.Value("全部", "0"),
                new Filter.Value("2026", "10"),
                new Filter.Value("2025", "13"),
                new Filter.Value("2024", "14"),
                new Filter.Value("2023", "15")
            ))
        ));

        // 排序过滤器（sort）
        filters.put("sort", Arrays.asList(
            new Filter("sort", "排序", Arrays.asList(
                new Filter.Value("综合", "d_id"),
                new Filter.Value("最新", "d_addtime"),
                new Filter.Value("最热", "d_score"),
                new Filter.Value("高分", "d_score")
            ))
        ));

        return filters;
    }

    /**
     * 将 JsonObject 转换为 Vod 对象（用于列表项）。
     */
    private Vod toVod(JsonObject item) {
        Vod vod = new Vod();
        vod.setVodId(getString(item, "vod_id"));
        vod.setVodName(getString(item, "vod_name"));
        vod.setVodPic(getString(item, "vod_pic"));
        vod.setVodYear(getString(item, "vod_year"));
        String continu = getString(item, "vod_continu");
        String remarks = getString(item, "vod_scroe");
        if (!TextUtils.isEmpty(continu) && !"0".equals(continu)) {
            remarks = "更新至" + continu + "集";
        } else if (TextUtils.isEmpty(remarks)) {
            remarks = "暂无备注";
        }
        vod.setVodRemarks(remarks);
        return vod;
    }

    /**
     * 安全地从 JsonObject 获取字符串值。
     */
    private static String getString(JsonObject obj, String key) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) {
            return "";
        }
        try {
            return obj.get(key).getAsString();
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * AES 加密（CBC/PKCS5Padding，使用固定 key 和 iv）。
     */
    private byte[] aesEncrypt(byte[] data) throws Exception {
        Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
        SecretKeySpec keySpec = new SecretKeySpec(AES_KEY.getBytes(StandardCharsets.UTF_8), "AES");
        IvParameterSpec ivSpec = new IvParameterSpec(AES_IV.getBytes(StandardCharsets.UTF_8));
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);
        return cipher.doFinal(data);
    }

    /**
     * AES 解密（CBC/PKCS5Padding，使用指定 key 和 iv）。
     */
    private byte[] aesDecrypt(byte[] data, String key, String iv) throws Exception {
        Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
        SecretKeySpec keySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "AES");
        IvParameterSpec ivSpec = new IvParameterSpec(iv.getBytes(StandardCharsets.UTF_8));
        cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);
        return cipher.doFinal(data);
    }

    /**
     * RSA 加密（ECB/PKCS1Padding，使用公钥）。
     */
    private byte[] rsaEncrypt(byte[] data) throws Exception {
        byte[] keyBytes = Base64.decode(RSA_PUBLIC_KEY_BASE64, Base64.DEFAULT);
        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(keyBytes);
        PublicKey publicKey = KeyFactory.getInstance("RSA").generatePublic(keySpec);
        Cipher cipher = Cipher.getInstance(RSA_ALGORITHM);
        cipher.init(Cipher.ENCRYPT_MODE, publicKey);
        return cipher.doFinal(data);
    }

    /**
     * 字节数组转十六进制字符串。
     *
     * @param bytes   字节数组
     * @param format  格式化模板（如 "%02X" 大写或 "%02x" 小写）
     */
    private static String bytesToHex(byte[] bytes, String format) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format(format, b & 0xFF));
        }
        return sb.toString();
    }

    /**
     * 十六进制字符串转字节数组。
     */
    private static byte[] hexToBytes(String hex) {
        int len = hex.length() / 2;
        byte[] bytes = new byte[len];
        for (int i = 0; i < len; i++) {
            bytes[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return bytes;
    }

    /**
     * 计算 MD5 哈希（返回小写十六进制字符串）。
     */
    private static String md5Hex(String input) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(digest, "%02x");
        } catch (Exception e) {
            return "";
        }
    }
}
