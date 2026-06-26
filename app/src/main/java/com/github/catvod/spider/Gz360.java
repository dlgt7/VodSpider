package com.github.catvod.spider;

import android.content.Context;
import android.util.Base64;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Filter;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Json;
import com.github.catvod.utils.Util;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class Gz360 extends Spider {

    private static final String BASE_URL = "https://api.w32z7vtd.com";
    private static final String AES_KEY = "U823n8pKnAAbWOST";
    private static final String AES_IV = "wgr8N6BCs7426wf1";
    private static final String TOKEN = "97630f5f85d9f3c639fb7790ca881ef2.4cccf48dc340fe8bded39cfe4ef9ac2adb27425a9069e6cd121210fc7ba518ea8c1cc5629261e94bb6ccb66d8548449c72076c956a2fb46c253008909a6c66347eb458fe3c06d1fcc993ca03a298328f9229f1994a608250c7d1ae124c4520e6e14ce8bf9f4404119a6bbf53cf592a8df2e9145de92ec43ec87cf4bdc563f6e919fe32861b0e93b118ec37d8035fbb3c.473433979755ccd5ec1b4581ccef76e8209b9e0c6ff819917f12dffad47d0d5e";
    private static final String KEYS = "bMTqITVqBsbq9UjLufsQuBvRiIyfqHLqAWUx0gj0ZUe9DMNDTmJDVZzAh45AZ5LtkC39Y0DU4Ufqm/9gliIJaj7cI/dhmoM5fib5HcslzyGONEwZY5fHBvokBreGaT8bPoaxmnWdTRjRfJzYZV6T06O7GsYVa6DuKTVArb0g48Q=";
    private static final String SIGN_SUFFIX = "*&zvdvdvddbfikkkumtmdwqppp?|4Y!s!2br";
    private static final String RSA_PRIVATE_KEY = "-----BEGIN PRIVATE KEY-----\nMIICdgIBADANBgkqhkiG9w0BAQEFAASCAmAwggJcAgEAAoGAe6hKrWLi1zQmjTT1\nozbE4QdFeJGNxubxld6GrFGximxfMsMB6BpJhpcTouAqywAFppiKetUBBbXwYsYU\n1wNr648XVmPmCMCy4rY8vdliFnbMUj086DU6Z+/oXBdWU3/b1G0DN3E9wULRSwcK\nZT3wj/cCI1vsCm3gj2R5SqkA9Y0CAwEAAQKBgAJH+4CxV0/zBVcLiBCHvSANm0l7\nHetybTh/j2p0Y1sTXro4ALwAaCTUeqdBjWiLSo9lNwDHFyq8zX90+gNxa7c5EqcW\nV9FmlVXr8VhfBzcZo1nXeNdXFT7tQ2yah/odtdcx+vRMSGJd1t/5k5bDd9wAvYdI\nDblMAg+wiKKZ5KcdAkEA1cCakEN4NexkF5tHPRrR6XOY/XHfkqXxEhMqmNbB9U34\nsaTJnLWIHC8IXys6Qmzz30TtzCjuOqKRRy+FMM4TdwJBAJQZFPjsGC+RqcG5UvVM\niMPhnwe/bXEehShK86yJK/g/UiKrO87h3aEu5gcJqBygTq3BBBoH2md3pr/W+hUM\nWBsCQQChfhTIrdDinKi6lRxrdBnn0Ohjg2cwuqK5zzU9p/N+S9x7Ck8wUI53DKm8\njUJE8WAG7WLj/oCOWEh+ic6NIwTdAkEAj0X8nhx6AXsgCYRql1klbqtVmL8+95KZ\nK7PnLWG/IfjQUy3pPGoSaZ7fdquG8bq8oyf5+dzjE/oTXcByS+6XRQJAP/5ciy1b\nL3NhUhsaOVy55MHXnPjdcTX0FaLi+ybXZIfIQ2P4rb19mVq1feMbCXhz+L1rG8oa\nt5lYKfpe8k83ZA==\n-----END PRIVATE KEY-----";

    private final Map<String, String> typeMap = new HashMap<>();

    private static String aesDecryptHex(String data, String key, String iv) {
        try {
            int len = data.length() / 2;
            byte[] bytes = new byte[len];
            for (int i = 0; i < len; i++) {
                int pos = i * 2;
                String str = data.substring(pos, pos + 2);
                bytes[i] = (byte) Integer.parseInt(str, 16);
            }
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            SecretKeySpec keySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "AES");
            IvParameterSpec ivSpec = new IvParameterSpec(iv.getBytes(StandardCharsets.UTF_8));
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);
            return new String(cipher.doFinal(bytes), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }

    private static String aesEncryptHex(String data) {
        try {
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            SecretKeySpec keySpec = new SecretKeySpec(AES_KEY.getBytes(StandardCharsets.UTF_8), "AES");
            IvParameterSpec ivSpec = new IvParameterSpec(AES_IV.getBytes(StandardCharsets.UTF_8));
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);
            byte[] encrypted = cipher.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : encrypted) {
                sb.append(String.format("%02X", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private static String md5Upper(String data) {
        return Util.md5(data).toUpperCase();
    }

    private static String rsaDecrypt(String data) {
        try {
            byte[] encrypted = Base64.decode(data, Base64.DEFAULT);
            String privateKeyStr = RSA_PRIVATE_KEY
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s", "");
            byte[] keyBytes = Base64.decode(privateKeyStr, Base64.DEFAULT);
            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            PrivateKey privateKey = keyFactory.generatePrivate(keySpec);
            Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            cipher.init(Cipher.DECRYPT_MODE, privateKey);
            StringBuilder sb = new StringBuilder();
            int offset = 0;
            while (offset < encrypted.length) {
                int chunkLen = Math.min(128, encrypted.length - offset);
                byte[] chunk = Arrays.copyOfRange(encrypted, offset, offset + chunkLen);
                byte[] decryptedChunk = cipher.doFinal(chunk);
                sb.append(new String(decryptedChunk, StandardCharsets.UTF_8));
                offset += 128;
            }
            return sb.toString().trim();
        } catch (Exception e) {
            return "";
        }
    }

    private static Map<String, String> getHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("Version", "2406025");
        headers.put("PackageName", "com.j64f4b21072.ha69699879.dfea0a9826ba.ibf50c9b1d");
        headers.put("Ver", "1.9.2");
        headers.put("Referer", BASE_URL);
        headers.put("Content-Type", "application/x-www-form-urlencoded");
        headers.put("User-Agent", "okhttp/3.12.0");
        return headers;
    }

    private static JsonObject apiRequest(JsonObject params, String path) {
        try {
            String time = String.valueOf(System.currentTimeMillis() / 1000);
            String requestKey = aesEncryptHex(Json.toJson(params));

            StringBuilder signBuilder = new StringBuilder();
            signBuilder.append("token_id=,token=").append(TOKEN).append(",phone_type=1,request_key=");
            signBuilder.append(requestKey);
            signBuilder.append(",app_id=1,time=").append(time);
            signBuilder.append(",keys=").append(KEYS).append(SIGN_SUFFIX);

            String signature = md5Upper(signBuilder.toString());

            LinkedHashMap<String, String> formData = new LinkedHashMap<>();
            formData.put("token", TOKEN);
            formData.put("token_id", "");
            formData.put("phone_type", "1");
            formData.put("time", time);
            formData.put("phone_model", "xiaomi-2206123sc");
            formData.put("keys", KEYS);
            formData.put("request_key", requestKey);
            formData.put("signature", signature);
            formData.put("app_id", "1");
            formData.put("ad_version", "1");

            String url = BASE_URL + path;
            String response = OkHttp.post(url, formData, getHeaders());
            JsonObject result = Json.safeObject(response);

            if (Json.isNull(result, "data")) return null;

            JsonObject data = Json.getJsonObject(result, "data");
            if (Json.isNotNull(data, "keys") && Json.isNotNull(data, "response_key")) {
                String keysStr = Json.getString(data, "keys");
                String decryptedKeys = rsaDecrypt(keysStr);
                JsonObject keysObj = Json.safeObject(decryptedKeys);
                String responseKey = Json.getString(data, "response_key");
                String aesKey = Json.getString(keysObj, "key");
                String aesIv = Json.getString(keysObj, "iv");
                String decrypted = aesDecryptHex(responseKey, aesKey, aesIv);
                if (Util.isNotEmpty(decrypted)) {
                    return Json.safeObject(decrypted);
                }
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private static List<Filter> getCommonFilters() {
        List<Filter> filters = new ArrayList<>();
        filters.add(new Filter("area", "地区", Arrays.asList(
                new Filter.Value("地区", "0"),
                new Filter.Value("大陆", "大陆"),
                new Filter.Value("香港", "香港"),
                new Filter.Value("台湾", "台湾"),
                new Filter.Value("日本", "日本"),
                new Filter.Value("韩国", "韩国")
        )));
        filters.add(new Filter("year", "年份", Arrays.asList(
                new Filter.Value("年份", "0"),
                new Filter.Value("2026", "2026"),
                new Filter.Value("2025", "2025"),
                new Filter.Value("2024", "2024"),
                new Filter.Value("2023", "2023")
        )));
        filters.add(new Filter("sort", "排序", Arrays.asList(
                new Filter.Value("综合", "d_id"),
                new Filter.Value("最新", "d_addtime"),
                new Filter.Value("最热", "d_score"),
                new Filter.Value("高分", "d_score")
        )));
        return filters;
    }

    @Override
    public void init(Context context, String extend) {
        typeMap.put("1", "5");
        typeMap.put("2", "12");
        typeMap.put("3", "30");
        typeMap.put("4", "22");
        typeMap.put("64", "");
    }

    @Override
    public String homeContent(boolean filter) {
        List<Class> classes = Arrays.asList(
                new Class("1", "电影"),
                new Class("2", "国产剧"),
                new Class("3", "动漫"),
                new Class("4", "综艺"),
                new Class("64", "短剧")
        );

        LinkedHashMap<String, List<Filter>> filters = new LinkedHashMap<>();

        List<Filter> movieFilters = new ArrayList<>(getCommonFilters());
        movieFilters.add(2, new Filter("sub", "类型", Arrays.asList(
                new Filter.Value("动作片", "5"),
                new Filter.Value("喜剧片", "6"),
                new Filter.Value("爱情片", "7"),
                new Filter.Value("科幻片", "8"),
                new Filter.Value("恐怖片", "9"),
                new Filter.Value("剧情片", "10")
        )));
        filters.put("1", movieFilters);

        List<Filter> dramaFilters = new ArrayList<>(getCommonFilters());
        dramaFilters.add(2, new Filter("sub", "类型", Arrays.asList(
                new Filter.Value("国产剧", "12"),
                new Filter.Value("香港剧", "13"),
                new Filter.Value("台湾剧", "14"),
                new Filter.Value("欧美剧", "15"),
                new Filter.Value("日本剧", "16"),
                new Filter.Value("韩国剧", "17")
        )));
        filters.put("2", dramaFilters);

        List<Filter> animeFilters = new ArrayList<>(getCommonFilters());
        animeFilters.add(2, new Filter("sub", "类型", Arrays.asList(
                new Filter.Value("中国动漫", "30"),
                new Filter.Value("日本动漫", "31"),
                new Filter.Value("欧美动漫", "33")
        )));
        filters.put("3", animeFilters);

        List<Filter> varietyFilters = new ArrayList<>(getCommonFilters());
        varietyFilters.add(2, new Filter("sub", "类型", Arrays.asList(
                new Filter.Value("大陆综艺", "22"),
                new Filter.Value("港台综艺", "23"),
                new Filter.Value("日韩综艺", "24"),
                new Filter.Value("欧美综艺", "25")
        )));
        filters.put("4", varietyFilters);

        filters.put("64", Arrays.asList(
                new Filter("sort", "排序", Arrays.asList(
                        new Filter.Value("综合", "d_id"),
                        new Filter.Value("最新", "d_addtime"),
                        new Filter.Value("最热", "d_score"),
                        new Filter.Value("高分", "d_score")
                ))
        ));

        return Result.string(classes, filters);
    }

    private Vod parseVod(JsonObject item) {
        Vod vod = new Vod();
        vod.setVodId(Json.getString(item, "vod_id"));
        vod.setVodName(Json.getString(item, "vod_name"));
        vod.setVodPic(Json.getString(item, "vod_pic"));
        vod.setVodYear(Json.getString(item, "vod_year"));
        String continu = Json.getString(item, "vod_continu");
        String score = Json.getString(item, "vod_scroe");
        String remark;
        if (Util.isNotEmpty(continu) && !"0".equals(continu)) {
            remark = "更新至" + continu + "集";
        } else if (Util.isNotEmpty(score)) {
            remark = score;
        } else {
            remark = "暂无备注";
        }
        vod.setVodRemarks(remark);
        return vod;
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) {
        JsonObject params = new JsonObject();
        params.addProperty("tid", tid);
        params.addProperty("page", pg);

        String sort = "d_id";
        String area = "0";
        String sub = typeMap.get(tid);
        String year = "0";

        if (extend != null && !extend.isEmpty()) {
            if (extend.containsKey("sort")) sort = extend.get("sort");
            if (extend.containsKey("area")) area = extend.get("area");
            if (extend.containsKey("sub")) sub = extend.get("sub");
            if (extend.containsKey("year")) year = extend.get("year");
        }

        params.addProperty("sort", sort);
        params.addProperty("area", area);
        params.addProperty("sub", sub);
        params.addProperty("year", year);
        params.addProperty("pageSize", "30");

        JsonObject result = apiRequest(params, "/App/IndexList/indexList");

        List<Vod> list = new ArrayList<>();
        if (result != null && result.has("list")) {
            JsonArray array = Json.getJsonArray(result, "list");
            for (JsonElement element : array) {
                list.add(parseVod(element.getAsJsonObject()));
            }
        }

        int page = Integer.parseInt(pg);
        return Result.get().vod(list).page(page, 9999, 30, 999999).string();
    }

    @Override
    public String detailContent(List<String> ids) {
        String vodId = ids.get(0);

        JsonObject params = new JsonObject();
        params.addProperty("token_id", "1009464");
        params.addProperty("vod_id", vodId);
        params.addProperty("mobile_time", String.valueOf(System.currentTimeMillis() / 1000));
        params.addProperty("token", TOKEN);

        JsonObject vodInfo = apiRequest(params, "/App/IndexPlay/playInfo");
        if (vodInfo == null || Json.isNull(vodInfo, "vodInfo")) {
            return Result.error("详情获取失败");
        }

        JsonObject vodData = Json.getJsonObject(vodInfo, "vodInfo");

        JsonObject playParams = new JsonObject();
        playParams.addProperty("vurl_cloud_id", "2");
        playParams.addProperty("vod_d_id", vodId);

        JsonObject playResult = apiRequest(playParams, "/App/Resource/Vurl/show");

        LinkedHashMap<String, List<String>> playMap = new LinkedHashMap<>();
        if (playResult != null && playResult.has("list")) {
            JsonArray playArray = Json.getJsonArray(playResult, "list");
            for (JsonElement element : playArray) {
                JsonObject playItem = element.getAsJsonObject();
                String title = Json.getString(playItem, "title");
                if (Json.isNull(playItem, "play")) continue;
                JsonObject play = Json.getJsonObject(playItem, "play");
                for (String playKey : play.keySet()) {
                    JsonObject playData = Json.getJsonObject(play, playKey);
                    String showType = Json.getString(playData, "show_type");
                    if ("2".equals(showType)) continue;
                    String param = Json.getString(playData, "param");
                    if (Util.isEmpty(param)) continue;
                    if (!playMap.containsKey(playKey)) {
                        playMap.put(playKey, new ArrayList<>());
                    }
                    playMap.get(playKey).add(title + "$" + param);
                }
            }
        }

        List<String> playFrom = new ArrayList<>();
        List<String> playUrl = new ArrayList<>();
        for (String key : playMap.keySet()) {
            playFrom.add(key);
            playUrl.add(Util.join("#", playMap.get(key)));
        }

        Vod vod = new Vod();
        vod.setVodId(vodId);
        vod.setVodName(Json.getString(vodData, "vod_name"));
        vod.setVodPic(Json.getString(vodData, "vod_pic"));
        vod.setVodContent(Json.getString(vodData, "vod_use_content"));
        vod.setVodActor(Json.getString(vodData, "vod_actor"));
        vod.setVodDirector(Json.getString(vodData, "vod_director"));
        vod.setVodArea(Json.getString(vodData, "vod_area"));
        vod.setVodYear(Json.getString(vodData, "vod_year"));
        vod.setVodRemarks(Json.getString(vodData, "vod_scroe"));
        vod.setVodPlayFrom(Util.join("$$$", playFrom));
        vod.setVodPlayUrl(Util.join("$$$", playUrl));

        return Result.string(vod);
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) {
        JsonObject params = new JsonObject();
        String[] parts = id.split("&");
        for (String part : parts) {
            int idx = part.indexOf('=');
            if (idx > 0) {
                String key = part.substring(0, idx);
                String value = part.substring(idx + 1);
                params.addProperty(key, value);
            }
        }

        JsonObject result = apiRequest(params, "/App/Resource/VurlDetail/showOne");
        String url = result != null ? Json.getString(result, "url") : "";

        if (Util.isEmpty(url)) {
            return Result.error("播放链接解析失败");
        }

        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", "Lavf/57.83.100");

        return Result.get().url(url).parse(0).header(headers).string();
    }

    @Override
    public String searchContent(String key, boolean quick) {
        JsonObject params = new JsonObject();
        params.addProperty("keywords", key);
        params.addProperty("order_val", "1");

        JsonObject result = apiRequest(params, "/App/Index/findMoreVod");

        List<Vod> list = new ArrayList<>();
        if (result != null && result.has("list")) {
            JsonArray array = Json.getJsonArray(result, "list");
            for (JsonElement element : array) {
                list.add(parseVod(element.getAsJsonObject()));
            }
        }

        return Result.string(list);
    }
}
