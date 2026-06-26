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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class App99 extends Spider {

    private static final String DEFAULT_VERSION = "0b4328287a5d953e";
    private static final String DEFAULT_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.6299.95 Safari/537.36";
    private static final String DEVICE_XIAOMI = "xiaomi";
    private static final String DEVICE_PRODUCT = "b0q";
    private static final String DEVICE_ANDROID_ID = "V417IR";
    private static final String DEVICE_FINGER = "xiaomi/b0q/b0q:15/V619IR/613:user/release-keys";
    private static final String DEVICE_DISPLAY = "V417IR release-keys";
    private static final String DEVICE_HOST = "a11-gz01-test";
    private static final int SDK_INT = 0x20;

    private final JSONObject a;
    public String b;
    public String c;
    public String d;
    public String e;
    public String f;
    public String g;

    public App99() {
        this.b = "";
        this.c = "";
        this.d = "";
        this.e = "";
        this.f = "";
        this.g = DEFAULT_VERSION;
        this.a = new JSONObject();
    }

    public static ArrayList<Filter.Value> b(JSONArray array) {
        ArrayList<Filter.Value> values = new ArrayList<>();
        if (array == null) return values;
        for (int i = 0; i < array.length(); i++) {
            String value = array.optString(i);
            if (!TextUtils.isEmpty(value)) {
                values.add(new Filter.Value(value, value));
            }
        }
        return values;
    }

    private String nonce() {
        byte[] bytes = new byte[16];
        new SecureRandom().nextBytes(bytes);
        return Base64.encodeToString(bytes, Base64.NO_WRAP);
    }

    private String aesEncrypt(String data, String key) throws Exception {
        byte[] keyBytes = key.replace("-", "").getBytes(StandardCharsets.UTF_8);
        SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, keySpec);
        byte[] ciphertext = cipher.doFinal(data.getBytes(StandardCharsets.UTF_8));
        byte[] iv = cipher.getIV();
        byte[] result = new byte[iv.length + ciphertext.length];
        System.arraycopy(iv, 0, result, 0, iv.length);
        System.arraycopy(ciphertext, 0, result, iv.length, ciphertext.length);
        return Base64.encodeToString(result, Base64.NO_WRAP);
    }

    private String aesDecrypt(String data, String key) throws Exception {
        if (data == null || data.isEmpty()) return "";
        byte[] decoded = Base64.decode(data, Base64.DEFAULT);
        byte[] iv = new byte[16];
        byte[] ciphertext = new byte[decoded.length - 16];
        System.arraycopy(decoded, 0, iv, 0, 16);
        System.arraycopy(decoded, 16, ciphertext, 0, ciphertext.length);
        byte[] keyBytes = key.replace("-", "").getBytes(StandardCharsets.UTF_8);
        SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, keySpec, new IvParameterSpec(iv));
        byte[] decrypted = cipher.doFinal(ciphertext);
        if (decrypted.length > 0 && decrypted[0] == 0x7b) {
            return new String(decrypted, StandardCharsets.UTF_8);
        }
        try {
            InflaterInputStream iis = new InflaterInputStream(new ByteArrayInputStream(decrypted));
            ByteArrayOutputStream baos = new ByteArrayOutputStream(decrypted.length);
            byte[] buffer = new byte[1024];
            int len;
            while ((len = iis.read(buffer)) > 0) {
                baos.write(buffer, 0, len);
            }
            iis.close();
            return baos.toString(StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            try {
                Inflater inflater = new Inflater(true);
                inflater.setInput(decrypted);
                ByteArrayOutputStream baos = new ByteArrayOutputStream(decrypted.length);
                byte[] buffer = new byte[1024];
                while (!inflater.finished()) {
                    int count = inflater.inflate(buffer);
                    baos.write(buffer, 0, count);
                }
                inflater.end();
                return baos.toString(StandardCharsets.UTF_8.name());
            } catch (Exception e2) {
                return new String(decrypted, StandardCharsets.UTF_8);
            }
        }
    }

    public HashMap<String, String> a(String nonce, String timestamp, String encryptedBody, String p4) {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("User-Agent", this.f);
        headers.put("Accept", "application/json");
        headers.put("Content-Type", "application/json");
        headers.put("client_type", "android");
        headers.put("uuid", this.d);
        headers.put("timestamp", timestamp);
        String sign;
        try {
            String signSrc = encryptedBody + ":" + timestamp + ":" + nonce + ":" + p4 + ":" + this.e;
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(signSrc.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                String hex = Integer.toHexString(b & 0xff);
                if (hex.length() == 1) sb.append((char) 0x30);
                sb.append(hex);
            }
            sign = sb.toString();
        } catch (Exception e) {
            sign = "";
        }
        headers.put("sign", sign);
        headers.put("nonce", nonce);
        headers.put("appkey", this.e);
        headers.put("version", this.g);
        headers.put("api_version", "v1");
        return headers;
    }

    public void c(String version, String name, String buildSignature) throws Exception {
        String nonce = nonce();
        String timestamp = String.valueOf(System.currentTimeMillis());
        JSONObject body = new JSONObject();
        body.put("v", version);
        body.put("n", name);
        body.put("s", buildSignature);
        body.put("pl", "1");
        body.put("apiVersion", "v2");
        body.put("token", "");
        body.put("timestamp", timestamp);
        body.put("nonce", nonce);
        String bodyStr = body.toString();
        String encrypted = aesEncrypt(bodyStr, this.d);
        String url = this.b + "/app/systemInit";
        HashMap<String, String> headers = a(nonce, timestamp, encrypted, "");
        String response = OkHttp.post(url, encrypted, headers);
        if (TextUtils.isEmpty(response)) return;
        String decrypted = aesDecrypt(response, this.d);
        if (TextUtils.isEmpty(decrypted)) return;
        JSONObject json = new JSONObject(decrypted);
        if (json.has("player")) {
            this.a.put("player", json.getJSONObject("player"));
        }
        if (json.has("parser_api")) {
            this.a.put("parses", json.getJSONArray("parser_api"));
        }
        if (json.has("categorys")) {
            JSONObject categorys = json.getJSONObject("categorys");
            if (categorys.has("data")) {
                this.a.put("categories", categorys.getJSONArray("data"));
            }
        }
    }

    public void d(String path, String version, String name, String packageName, String buildNumber, String buildSignature) throws Exception {
        long now = System.currentTimeMillis();
        String did = UUID.randomUUID().toString();
        String nonce = nonce();
        String timestamp = String.valueOf(System.currentTimeMillis());
        JSONObject app = new JSONObject();
        app.put("version", version);
        app.put("name", name);
        app.put("package", packageName);
        app.put("buildNumber", buildNumber);
        app.put("buildSignature", buildSignature);
        app.put("install", now);
        app.put("update", now);
        JSONObject body = new JSONObject();
        body.put("os", "android");
        body.put("name", DEVICE_XIAOMI);
        body.put("version", "15");
        body.put("sdkInt", SDK_INT);
        body.put("device", DEVICE_XIAOMI);
        body.put("brand", DEVICE_XIAOMI);
        body.put("manufacturer", DEVICE_XIAOMI);
        body.put("product", DEVICE_PRODUCT);
        body.put("hardware", DEVICE_XIAOMI);
        body.put("isPhysicalDevice", true);
        body.put("androidId", DEVICE_ANDROID_ID);
        body.put("bootloader", "unknown");
        body.put("display", DEVICE_DISPLAY);
        body.put("host", DEVICE_HOST);
        body.put("tags", "release-keys");
        body.put("type", "user");
        body.put("finger", DEVICE_FINGER);
        body.put("app", app);
        body.put("did", did);
        body.put("apiVersion", "v2");
        body.put("channel", "");
        body.put("token", "");
        body.put("timestamp", timestamp);
        body.put("nonce", nonce);
        String bodyStr = body.toString();
        String encrypted = aesEncrypt(bodyStr, this.d);
        String url = this.b + path;
        HashMap<String, String> headers = a(nonce, timestamp, encrypted, "");
        String response = OkHttp.post(url, encrypted, headers);
        if (TextUtils.isEmpty(response)) return;
        String decrypted = aesDecrypt(response, this.d);
        if (TextUtils.isEmpty(decrypted)) return;
        JSONObject json = new JSONObject(decrypted);
        if (json.has("userInfo")) {
            JSONObject userInfo = json.getJSONObject("userInfo");
            if (userInfo.has("user_token")) {
                this.c = userInfo.optString("user_token");
            }
        }
    }

    public void e(String loginPath, String version, String name, String packageName, String buildNumber, String buildSignature) {
        ArrayList<String> paths = new ArrayList<>();
        if (!TextUtils.isEmpty(loginPath)) {
            paths.add(loginPath);
        }
        if (!paths.contains("/app/userInfo")) {
            paths.add("/app/userInfo");
        }
        if (!paths.contains("/app/log")) {
            paths.add("/app/log");
        }
        for (String path : paths) {
            try {
                d(path, version, name, packageName, buildNumber, buildSignature);
                if (!TextUtils.isEmpty(this.c)) return;
            } catch (Exception ignored) {
            }
        }
    }

    public ArrayList<Vod> f(JSONArray array) throws Exception {
        ArrayList<Vod> list = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.getJSONObject(i);
            Vod vod = new Vod(item.optString("id"), item.optString("name"), item.optString("pic"), item.optString("remarks"));
            vod.setVodYear(item.optString("year"));
            vod.setVodArea(item.optString("area"));
            vod.setVodActor(item.optString("actor"));
            vod.setVodDirector(item.optString("director"));
            vod.setVodContent(item.optString("blurb"));
            vod.setTypeName(item.optString("class"));
            list.add(vod);
        }
        return list;
    }

    public JSONObject g(JSONObject body, String path, String token) throws Exception {
        String nonce = nonce();
        String timestamp = String.valueOf(System.currentTimeMillis());
        body.put("timestamp", timestamp);
        body.put("nonce", nonce);
        if (!body.has("token")) {
            body.put("token", token);
        }
        String bodyStr = body.toString();
        String encrypted = aesEncrypt(bodyStr, this.d);
        String url = this.b + path;
        HashMap<String, String> headers = a(nonce, timestamp, encrypted, token);
        String response = OkHttp.post(url, encrypted, headers);
        if (TextUtils.isEmpty(response)) return new JSONObject();
        String decrypted = aesDecrypt(response, this.d);
        if (TextUtils.isEmpty(decrypted)) return new JSONObject();
        return new JSONObject(decrypted);
    }

    public String h(JSONObject playerConfig, String url) {
        if (!this.a.has("parses")) return "";
        JSONArray parses = this.a.optJSONArray("parses");
        if (parses == null) return "";
        String parseUrlStr = playerConfig.optString("parseUrl", "");
        String[] parseUrls = parseUrlStr.split(",");
        List<String> allowedIds = null;
        if (parseUrls.length != 0 && !TextUtils.isEmpty(parseUrls[0])) {
            allowedIds = Arrays.asList(parseUrls);
        }
        HashMap<String, String> headers = new HashMap<>();
        headers.put("User-Agent", this.f);
        headers.put("Accept", "application/json");
        for (int i = 0; i < parses.length(); i++) {
            JSONObject parse = parses.optJSONObject(i);
            if (parse == null) continue;
            String id = String.valueOf(parse.optInt("id"));
            if (allowedIds != null && !allowedIds.contains(id)) continue;
            String apiUrl = parse.optString("api_url");
            if (TextUtils.isEmpty(apiUrl)) continue;
            try {
                String fullUrl = apiUrl + url;
                String response = "";
                if (fullUrl != null && fullUrl.startsWith("http")) {
                    response = OkHttp.string(fullUrl, null, headers);
                }
                JSONObject json = new JSONObject(response);
                String parsedUrl = json.optString("url", "");
                if (!TextUtils.isEmpty(parsedUrl)) return parsedUrl;
            } catch (Exception ignored) {
            }
        }
        return "";
    }

    @Override
    public void init(Context context, String config) throws Exception {
        if (TextUtils.isEmpty(config)) return;
        try {
            JSONObject json = new JSONObject(config);
            this.b = json.optString("host");
            this.e = json.optString("appkey");
            String name = json.optString("name");
            String buildSignature = json.optString("buildSignature");
            String buildNumber = json.optString("buildNumber");
            String versionName = json.optString("versionName");
            String packageName = json.optString("package");
            if (TextUtils.isEmpty(this.b) || TextUtils.isEmpty(this.e) || TextUtils.isEmpty(name)
                    || TextUtils.isEmpty(buildSignature) || TextUtils.isEmpty(buildNumber)
                    || TextUtils.isEmpty(versionName) || TextUtils.isEmpty(packageName)) {
                return;
            }
            this.d = json.optString("uuid", UUID.randomUUID().toString());
            this.f = json.optString("ua", DEFAULT_UA);
            this.g = json.optString("version", this.g);
            String loginPath = json.optString("LoginPath", "/app/userInfo");
            c(versionName, name, buildSignature);
            if (TextUtils.isEmpty(this.c)) {
                e(loginPath, versionName, name, packageName, buildNumber, buildSignature);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        ArrayList<Class> classes = new ArrayList<>();
        LinkedHashMap<String, List<Filter>> filters = new LinkedHashMap<>();
        try {
            if (this.a.has("categories")) {
                JSONArray categories = this.a.getJSONArray("categories");
                for (int i = 0; i < categories.length(); i++) {
                    JSONObject category = categories.getJSONObject(i);
                    String id = category.optString("id");
                    classes.add(new Class(id, category.optString("name")));
                    JSONObject typeExtend = category.optJSONObject("type_extend");
                    if (typeExtend != null) {
                        ArrayList<Filter> filterList = new ArrayList<>();
                        filterList.add(new Filter("class", "类型", b(typeExtend.optJSONArray("class"))));
                        filterList.add(new Filter("area", "地区", b(typeExtend.optJSONArray("areas"))));
                        filterList.add(new Filter("lang", "语言", b(typeExtend.optJSONArray("lang"))));
                        filterList.add(new Filter("year", "年份", b(typeExtend.optJSONArray("years"))));
                        filters.put(id, filterList);
                    }
                }
            }
            JSONObject body = new JSONObject();
            body.put("kw", "");
            body.put("page", "1");
            body.put("limit", 0x15);
            String pid = "1";
            if (this.a.has("categories")) {
                JSONArray categories = this.a.getJSONArray("categories");
                if (categories.length() > 0) {
                    String firstId = categories.getJSONObject(0).optString("id");
                    if (!TextUtils.isEmpty(firstId)) {
                        pid = firstId;
                    }
                }
            }
            body.put("pid", pid);
            body.put("orderBy", "time");
            body.put("isCategory", 1);
            body.put("token", "");
            JSONObject response = g(body, "/vod/search", "");
            ArrayList<Vod> vodList = new ArrayList<>();
            if (response.has("data")) {
                vodList = f(response.getJSONArray("data"));
            }
            return Result.string(classes, vodList, filters);
        } catch (Exception e) {
            return Result.string(classes, new ArrayList<>(), filters);
        }
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        try {
            JSONObject body = new JSONObject();
            body.put("kw", "");
            body.put("page", pg);
            body.put("limit", 0x15);
            body.put("pid", tid);
            body.put("orderBy", "time");
            body.put("isCategory", 1);
            body.put("token", this.c);
            JSONObject response = g(body, "/vod/search", this.c);
            if (response.has("data")) {
                int pageCount = response.optInt("page_count", 1);
                int page = Integer.parseInt(pg);
                return Result.get().page(page, pageCount, 0, 0).vod(f(response.getJSONArray("data"))).string();
            }
        } catch (Exception e) {
        }
        return Result.get().page(1, 1, 0, 0).vod(new ArrayList<>()).string();
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        Vod vod = new Vod();
        try {
            HashMap<String, String> playerMap = new HashMap<>();
            if (this.a.has("player")) {
                JSONObject player = this.a.getJSONObject("player");
                Iterator<String> keys = player.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    JSONObject playerConfig = player.getJSONObject(key);
                    String code = playerConfig.optString("code").trim();
                    String name = playerConfig.optString("name").trim();
                    playerMap.put(code, name);
                }
            }
            JSONObject body = new JSONObject();
            body.put("id", ids.get(0));
            body.put("eps", "1");
            body.put("v", "2.0.0");
            body.put("pl", 1);
            body.put("token", this.c);
            JSONObject response = g(body, "/vod/detail", this.c);
            if (!response.has("data")) {
                return Result.string(vod);
            }
            JSONObject data = response.getJSONObject("data");
            vod.setVodId(data.optString("id"));
            vod.setVodName(data.optString("name"));
            vod.setVodPic(data.optString("pic"));
            vod.setVodRemarks(data.optString("remarks"));
            vod.setVodYear(data.optString("year"));
            vod.setVodArea(data.optString("area"));
            vod.setVodActor(data.optString("actor"));
            vod.setVodDirector(data.optString("director"));
            vod.setVodContent(data.optString("content"));
            vod.setTypeName(data.optString("class"));
            String vodName = data.optString("name");
            String[] playFromArr = data.optString("play_from").split("\\$\\$\\$");
            String[] playUrlArr = data.optString("play_url").split("\\$\\$\\$");
            StringBuilder playFromSb = new StringBuilder();
            for (String key : playFromArr) {
                String name = playerMap.get(key);
                if (!TextUtils.isEmpty(name)) {
                    key = name;
                }
                playFromSb.append(key);
                playFromSb.append("$$$");
            }
            if (playFromSb.length() > 3) {
                playFromSb.delete(playFromSb.length() - 3, playFromSb.length());
            }
            StringBuilder playUrlSb = new StringBuilder();
            for (int i = 0; i < playUrlArr.length; i++) {
                String playFromKey = (i < playFromArr.length) ? playFromArr[i] : "";
                String[] episodes = playUrlArr[i].split("#");
                StringBuilder groupSb = new StringBuilder();
                for (String episode : episodes) {
                    String[] parts = episode.split("\\$");
                    if (parts.length < 2) continue;
                    String epName = parts[0];
                    String epUrl = parts[1];
                    String digits = epName.replaceAll("\\D+", "");
                    if (TextUtils.isEmpty(digits)) {
                        digits = "1";
                    }
                    String ep = epName + "$" + epUrl + "@" + playFromKey + "@" + vodName + "@" + digits;
                    if (groupSb.length() > 0) {
                        groupSb.append("#");
                    }
                    groupSb.append(ep);
                }
                if (playUrlSb.length() > 0 && groupSb.length() > 0) {
                    playUrlSb.append("$$$");
                }
                playUrlSb.append(groupSb);
            }
            vod.setVodPlayFrom(playFromSb.toString());
            vod.setVodPlayUrl(playUrlSb.toString());
        } catch (Exception e) {
            e.printStackTrace();
        }
        return Result.string(vod);
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        String errorMsg = "播放链接解析失败,请更换其他源播放";
        try {
            String[] parts = id.split("@");
            if (parts.length < 2) {
                return Result.error("播放参数无效");
            }
            String url = parts[0];
            String playerKey = parts[1];
            if (url.startsWith("http://") || url.startsWith("https://")) {
                return Result.get().url(url).string();
            }
            if (!this.a.has("player")) {
                return Result.error("播放器配置缺失");
            }
            JSONObject playerConfig = this.a.getJSONObject("player").getJSONObject(playerKey);
            int type = playerConfig.optInt("type");
            if (type != 0) {
                String parsed = h(playerConfig, url);
                if (TextUtils.isEmpty(parsed)) {
                    return Result.error(errorMsg);
                }
                url = parsed;
            }
            return Result.get().url(url).string();
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error(errorMsg);
        }
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        try {
            JSONObject body = new JSONObject();
            body.put("kw", key);
            body.put("page", 1);
            body.put("limit", 0x15);
            body.put("orderBy", "vod_hits_month");
            body.put("sort", "desc");
            body.put("token", this.c);
            JSONObject response = g(body, "/vod/search", this.c);
            if (response.has("data")) {
                return Result.string(f(response.getJSONArray("data")));
            }
        } catch (Exception e) {
        }
        return Result.string(new ArrayList<>());
    }
}
