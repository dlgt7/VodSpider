package com.github.catvod.spider;

import android.content.Context;
import android.text.TextUtils;
import android.util.Base64;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Crypto;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * 奇奇影视 (AppQi) 站点 Spider。
 * <p>
 * 接口特征：所有业务接口走 {@code /api.php} + 路径形式，POST body 为 JSON 字符串，
 * header 携带 app-* 系列签名字段；响应中 {@code data} 字段经 AES/CBC/PKCS5Padding 加密，
 * 需用配置中的 {@code dataKey}/{@code dataIv} 解密。播放链接解析走 {@code /api.php/qijiappapi.index/vodParse}，
 * POST 原始表单 body（Content-Type: application/x-www-form-urlencoded），并附加 AES 加密的
 * {@code app-api-verify-sign} 签名。
 */
public class AppQi extends Spider {

    /** playerContent 返回 Result.header 时使用的浏览器 UA。 */
    private static final String LONG_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/117.0.0.0 Safari/537.36";
    /** 匹配 txt/json 配置文件 URL，用于 init 中判断 host 字段是否为远程配置地址。 */
    private static final Pattern URL_PATTERN = Pattern.compile("^https?://.*\\.(txt|json)$", Pattern.CASE_INSENSITIVE);
    /** 匹配视频直链（m3u8/mp4/mkv），用于 playerContent 中判断是否直链播放。 */
    private static final Pattern M3U8_PATTERN = Pattern.compile(".*(m3u8|mp4|mkv).*", Pattern.CASE_INSENSITIVE);
    /** 匹配 url 参数（前缀无 &），用于 playerContent 中替换 url= 参数值为 URL 编码形式。 */
    private static final Pattern PARSE_API_PATTERN = Pattern.compile("(parse_api=)(.*?)(?=&token)(&token)");

    /** init 接口返回的整站配置 JSON（已解密）。 */
    private JsonObject configJson;
    /** 站点 API 根 URL，形如 {@code https://xxx.com}。 */
    private String apiUrl;
    /** 请求头基础集合（含 UA 等），构造时初始化。 */
    private final HashMap<String, String> headers = new HashMap<>();
    /** AES 解密密钥，来自配置 dataKey/datakey。 */
    private String aesKey;
    /** AES 解密 IV，来自配置 dataIv/dataiv，缺省时回退到 aesKey。 */
    private String aesIv;
    /** 设备 ID，写入 app-user-device-id 头。 */
    private String deviceId;
    /** 版本号，写入 app-version-code 头。 */
    private String versionCode;
    /** init 接口路径名，默认 initV120。 */
    private String initFlag;
    /** 搜索接口路径名，默认 searchList。 */
    private String searchList;

    /**
     * 将 map 中指定 key 的值（非空字符串）写入 JsonObject 同名属性。
     * <p>对应 smali 静态方法 {@code a(JsonObject, HashMap, String)}。
     */
    private static void putIfPresent(JsonObject json, HashMap<String, String> map, String key) {
        if (map.containsKey(key)) {
            String value = map.get(key);
            if (value != null && !value.isEmpty()) {
                json.addProperty(key, value);
            }
        }
    }

    /**
     * 从 JsonObject 中依次尝试多个候选 key，返回首个非空、非 "空" 占位的字符串值。
     * <p>对应 smali 静态 varargs 方法 {@code pick(JsonObject, String[])}。
     */
    private static String pick(JsonObject json, String... keys) {
        for (String key : keys) {
            if (json.has(key)) {
                JsonElement el = json.get(key);
                if (el.isJsonNull()) continue;
                String value = el.getAsString();
                if (value != null && !value.isEmpty() && !"空".equals(value)) {
                    return value;
                }
            }
        }
        return null;
    }

    /**
     * 构造请求头：复制基础 headers，附加 device-id/version-code/ui-mode，
     * 当 {@code skipVerifyTime} 为 false 时附加秒级时间戳到 app-api-verify-time。
     * <p>postJson 调用 b(false) 自带时间戳；playerContent 调用 b(true)，随后自行附加时间戳与 AES 签名。
     */
    private HashMap<String, String> buildHeaders(boolean skipVerifyTime) {
        HashMap<String, String> copy = new HashMap<>(headers);
        copy.put("app-user-device-id", deviceId);
        copy.put("app-version-code", versionCode);
        copy.put("app-ui-mode", "light");
        if (!skipVerifyTime) {
            copy.put("app-api-verify-time", String.valueOf(System.currentTimeMillis() / 1000));
        }
        return copy;
    }

    /**
     * 从 configJson 的 recommend_list 数组构造首页/搜索推荐 Vod 列表。
     */
    private ArrayList<Vod> recommendList() {
        ArrayList<Vod> list = new ArrayList<>();
        if (configJson != null && configJson.has("recommend_list")) {
            Iterator<JsonElement> it = configJson.getAsJsonArray("recommend_list").iterator();
            while (it.hasNext()) {
                list.add(toVod(it.next().getAsJsonObject()));
            }
        }
        return list;
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        JsonObject body = new JsonObject();
        body.addProperty("type_id", tid);
        body.addProperty("page", pg);
        if (extend != null) {
            putIfPresent(body, extend, "class");
            putIfPresent(body, extend, "lang");
            putIfPresent(body, extend, "area");
            putIfPresent(body, extend, "year");
            if (extend.containsKey("by") && extend.get("by") != null && !extend.get("by").isEmpty()) {
                body.addProperty("sort", extend.get("by"));
            }
        }
        String path = "/qijiappapi.index/typeFilterVodList?page=" + pg;
        String resp = postJson(path, body.toString());
        JsonObject root = JsonParser.parseString(resp).getAsJsonObject();
        ArrayList<Vod> list = new ArrayList<>();
        if (root.has("recommend_list")) {
            Iterator<JsonElement> it = root.getAsJsonArray("recommend_list").iterator();
            while (it.hasNext()) {
                list.add(toVod(it.next().getAsJsonObject()));
            }
        }
        int page = Integer.parseInt(pg);
        return Result.get().vod(list).page(page, 9999, 90, 999999).string();
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        JsonObject body = new JsonObject();
        body.addProperty("vod_id", ids.get(0));
        String resp = postJson("/qijiappapi.index/vodDetail", body.toString());
        JsonObject root = JsonParser.parseString(resp).getAsJsonObject();
        JsonObject vod = root.getAsJsonObject("vod");
        String vodName = vod.get("vod_name").getAsString();
        // vod_play_from 用 $$$ 分隔多线路名；vod_play_url 用 $$$ 分隔多线路剧集、用 # 分隔单线路内多集
        // 剧集条目格式：epName$playEntry，其中 playEntry = parseApiUrl|vodName|nid 或 parse_api=...&url=...&token=...|vodName|nid
        StringBuilder playFrom = new StringBuilder();
        StringBuilder playUrl = new StringBuilder();
        Iterator<JsonElement> it = vod.getAsJsonArray("vod_play_list").iterator();
        while (it.hasNext()) {
            JsonObject source = it.next().getAsJsonObject();
            JsonObject playerInfo = source.getAsJsonObject("player_info");
            String show = playerInfo.get("show").getAsString();
            String parse = playerInfo.get("parse").getAsString();
            if (playFrom.length() > 0) playFrom.append("$$$");
            playFrom.append(show);
            StringBuilder episodes = new StringBuilder();
            Iterator<JsonElement> epIt = source.getAsJsonArray("urls").iterator();
            while (epIt.hasNext()) {
                JsonObject ep = epIt.next().getAsJsonObject();
                if (episodes.length() > 0) episodes.append("#");
                String epName = ep.get("name").getAsString();
                String epUrl = ep.get("url").getAsString();
                String parseApiUrl = ep.has("parse_api_url") ? ep.get("parse_api_url").getAsString() : "";
                String token = ep.get("token").getAsString();
                String nid = ep.get("nid").getAsString();
                String playEntry;
                if (parseApiUrl.matches("^https?://.*")) {
                    // 直链 parse_api_url：直接使用，format = url|vod_name|nid
                    playEntry = parseApiUrl + "|" + vodName + "|" + nid;
                } else {
                    // 需加密的剧集 URL：AES/CBC/PKCS5Padding 加密后拼接 parse_api=...&url=...&token=...
                    Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
                    SecretKeySpec keySpec = new SecretKeySpec(aesKey.getBytes(StandardCharsets.UTF_8), "AES");
                    IvParameterSpec ivSpec = new IvParameterSpec(aesIv.getBytes(StandardCharsets.UTF_8));
                    cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);
                    byte[] encrypted = cipher.doFinal(epUrl.getBytes(StandardCharsets.UTF_8));
                    String encryptedUrl = Base64.encodeToString(encrypted, Base64.NO_WRAP);
                    playEntry = "parse_api=" + parse + "&url=" + encryptedUrl + "&token=" + token + "|" + vodName + "|" + nid;
                }
                episodes.append(epName).append("$").append(playEntry);
            }
            if (playUrl.length() > 0) playUrl.append("$$$");
            playUrl.append(episodes);
        }
        Vod item = new Vod(ids.get(0), vodName, vod.get("vod_pic").getAsString());
        item.setVodRemarks(vod.get("vod_remarks").getAsString());
        item.setVodContent(vod.get("vod_content").getAsString());
        item.setVodActor(vod.get("vod_actor").getAsString());
        item.setVodDirector(vod.get("vod_director").getAsString());
        item.setTypeName(vod.get("vod_class").getAsString());
        item.setVodPlayFrom(playFrom.toString());
        item.setVodPlayUrl(playUrl.toString());
        ArrayList<Vod> list = new ArrayList<>();
        list.add(item);
        return Result.string(list);
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        ArrayList<Class> classes = new ArrayList<>();
        JsonObject filters = new JsonObject();
        // 英文 API key -> 中文显示名映射，用于构造筛选器 name 字段
        HashMap<String, String> keyDisplay = new HashMap<>();
        keyDisplay.put("class", "类型");
        keyDisplay.put("area", "地区");
        keyDisplay.put("lang", "语言");
        keyDisplay.put("year", "年份");
        keyDisplay.put("sort", "排序");
        Iterator<JsonElement> it = configJson.getAsJsonArray("type_list").iterator();
        while (it.hasNext()) {
            JsonObject type = it.next().getAsJsonObject();
            String typeName = type.get("type_name").getAsString();
            // 跳过敏感分类
            if ("伦理".equals(typeName) || "福利".equals(typeName) || "小影院".equals(typeName)) {
                continue;
            }
            String typeId = type.get("type_id").getAsString();
            classes.add(new Class(typeId, typeName));
            if (!filter || !type.has("filter_type_list")) {
                continue;
            }
            JsonArray filterArray = new JsonArray();
            Iterator<JsonElement> fit = type.getAsJsonArray("filter_type_list").iterator();
            while (fit.hasNext()) {
                JsonObject ft = fit.next().getAsJsonObject();
                if (!ft.has("list") || ft.getAsJsonArray("list").size() == 0) {
                    continue;
                }
                String name = ft.get("name").getAsString();
                // 仅保留 keyDisplay 中已知的筛选项
                if (!keyDisplay.containsKey(name)) {
                    continue;
                }
                JsonArray values = new JsonArray();
                Iterator<JsonElement> vit = ft.getAsJsonArray("list").iterator();
                while (vit.hasNext()) {
                    String v = vit.next().getAsString();
                    JsonObject kv = new JsonObject();
                    kv.addProperty("n", v);
                    kv.addProperty("v", v);
                    values.add(kv);
                }
                JsonObject filterObj = new JsonObject();
                // sort 在外发 JSON 中需映射为 by
                filterObj.addProperty("key", "sort".equals(name) ? "by" : name);
                filterObj.addProperty("name", keyDisplay.getOrDefault(name, name));
                filterObj.add("value", values);
                filterArray.add(filterObj);
            }
            if (filterArray.size() > 0) {
                filters.add(typeId, filterArray);
            }
        }
        return Result.string(classes, recommendList(), filters);
    }

    @Override
    public String homeVideoContent() throws Exception {
        return Result.string(recommendList());
    }

    @Override
    public void init(Context context, String ext) throws Exception {
        JsonObject cfg;
        if (ext != null) {
            String trimmed = ext.trim();
            if (trimmed.isEmpty()) {
                cfg = new JsonObject();
            } else if (trimmed.startsWith("{")) {
                cfg = JsonParser.parseString(trimmed).getAsJsonObject();
            } else {
                cfg = new JsonObject();
            }
        } else {
            cfg = new JsonObject();
        }
        String empty = "";
        // 优先取 url，其次 site 字段作为站点根 URL
        String site = pick(cfg, "url", "site");
        boolean siteEmpty = TextUtils.isEmpty(site);
        if (!siteEmpty) {
            // url/site 直接使用，仅去尾部斜杠
            site = site.trim().replaceAll("/+$", empty);
        } else {
            // site 为空时再尝试 host 字段：host 可能是 txt/json 配置 URL（需远程拉取），也可能是多行 URL
            site = pick(cfg, "host");
            if (!TextUtils.isEmpty(site)) {
                site = site.trim().replaceAll("/+$", empty);
                // 若 host 是 txt/json 配置文件 URL，则拉取远程配置（可能返回多行 URL）
                if (URL_PATTERN.matcher(site).find()) {
                    HashMap<String, String> headerCopy = new HashMap<>();
                    headerCopy.put("User-Agent", "okhttp/3.10.0");
                    site = OkHttp.string(site, headerCopy).trim();
                }
                // 按 \r?\n 拆分多行，逐行做 HEAD 校验取首个可达地址
                String[] lines = Pattern.compile("\\r?\\n").split(site);
                String firstValid = empty;
                for (String line : lines) {
                    String candidate = line.trim();
                    if (candidate.isEmpty() || !candidate.matches("^https?://.*")) {
                        continue;
                    }
                    candidate = candidate.replaceAll("/+$", empty);
                    if (firstValid.isEmpty()) {
                        firstValid = candidate;
                    }
                    int code = 0;
                    try {
                        HttpURLConnection conn = (HttpURLConnection) new java.net.URL(candidate).openConnection();
                        conn.setInstanceFollowRedirects(true);
                        conn.setRequestMethod("HEAD");
                        conn.setConnectTimeout(10000);
                        conn.setReadTimeout(10000);
                        code = conn.getResponseCode();
                    } catch (Exception ignored) {
                    }
                    // 跳过 404/502/503/504 等错误码，其它 code>0 视为可达
                    if (code > 0 && code != 404 && code != 502 && code != 503 && code != 504) {
                        site = candidate;
                        break;
                    }
                }
                if (TextUtils.isEmpty(site) && !firstValid.isEmpty()) {
                    // 所有行均未通过 HEAD 校验时回退到首个候选
                    site = firstValid;
                }
            }
        }
        // 读取 AES 密钥与 IV
        aesKey = pick(cfg, "dataKey", "datakey", "key");
        aesIv = pick(cfg, "dataIv", "dataiv");
        if (TextUtils.isEmpty(aesIv)) {
            aesIv = aesKey;
        }
        if (TextUtils.isEmpty(site) || TextUtils.isEmpty(aesKey)) {
            throw new Exception("AppQi ext 缺少 url/site 或 dataKey");
        }
        deviceId = pick(cfg, "deviceId");
        if (deviceId == null) deviceId = empty;
        versionCode = pick(cfg, "version");
        if (versionCode == null) versionCode = empty;
        // 公共 UA：配置中有 ua 则使用配置值，否则默认 okhttp/3.10.0
        String ua = pick(cfg, "ua");
        headers.put("User-Agent", ua != null ? ua : "okhttp/3.10.0");
        initFlag = pick(cfg, "init");
        if (TextUtils.isEmpty(initFlag)) initFlag = "initV120";
        searchList = pick(cfg, "search");
        if (TextUtils.isEmpty(searchList)) searchList = "searchList";
        apiUrl = site.replaceAll("/+$", empty);
        // 拉取 init 接口，得到整站配置 JSON
        String initPath = "/qijiappapi.index/" + initFlag;
        String resp = postJson(initPath, "{}");
        configJson = JsonParser.parseString(resp).getAsJsonObject();
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        // id 形如 url1|name|nid|url2，先用 \| 拆分，若长度为 4 则取 0|2|3 拼成 url1|name|nid 再拆
        String[] parts = id.split("\\|");
        if (parts.length == 4) {
            StringBuilder sb = new StringBuilder();
            sb.append(parts[0]).append("|").append(parts[2]).append("|").append(parts[3]);
            parts = sb.toString().split("\\|");
        }
        String playUrl = parts[0];
        // Result.header 固定使用浏览器 UA（不同于请求头中默认的 okhttp/3.10.0）
        HashMap<String, String> header = new HashMap<>();
        header.put("User-Agent", LONG_UA);

        boolean isHttpUrl = playUrl.matches("^https?://.*");
        String keyUrl = "?url=";
        String keyKey = "?key=";
        String keyHtml = "html";
        String keyJsonUrl = "url";

        // 路径1：HTTP URL 且含 ?url= / ?key= 参数，直接 GET 取 url 字段
        if (isHttpUrl && (playUrl.contains(keyKey) || playUrl.contains(keyUrl))) {
            String resp = OkHttp.string(playUrl, headers);
            JsonObject root = JsonParser.parseString(resp).getAsJsonObject();
            playUrl = root.get(keyJsonUrl).getAsString();
            return Result.get().parse(0).url(playUrl).header(header).string();
        }
        // 路径2：直链匹配 m3u8/mp4/mkv
        if (M3U8_PATTERN.matcher(playUrl).matches()) {
            return Result.get().parse(0).url(playUrl).header(header).string();
        }
        // 路径3：含 ?url= / ?key= / html 的 URL，先做 regex 替换（AES 解密 url= 参数值），再尝试 PARSE_API_PATTERN
        if (playUrl.contains(keyKey) || playUrl.contains(keyUrl) || playUrl.contains(keyHtml)) {
            // (url=)(.*?)(?=&token)(&token) -> group1 + Crypto.CBC(group2) + group3
            // 注意：第一段 regex 用 AES 解密 group2，不是 URL 编码
            Pattern p1 = Pattern.compile("(url=)(.*?)(?=&token)(&token)");
            Matcher m1 = p1.matcher(playUrl);
            StringBuffer sb1 = new StringBuffer();
            boolean firstRegexOk = true;
            while (m1.find()) {
                try {
                    m1.appendReplacement(sb1, m1.group(1) + Crypto.CBC(m1.group(2), aesKey, aesIv) + m1.group(3));
                } catch (Exception e) {
                    firstRegexOk = false;
                    break;
                }
            }
            if (firstRegexOk) {
                m1.appendTail(sb1);
                playUrl = sb1.toString();
            }
            // 尝试 PARSE_API_PATTERN 提取 parse_api 值并 GET 解析
            Matcher m2 = PARSE_API_PATTERN.matcher(playUrl);
            if (m2.find()) {
                String parseApiUrl = m2.group(2);
                String resp = OkHttp.string(parseApiUrl, headers);
                JsonObject root = JsonParser.parseString(resp).getAsJsonObject();
                if (root.has("data")) {
                    String dataUrl = root.getAsJsonObject("data").get(keyJsonUrl).getAsString();
                    if (!TextUtils.isEmpty(dataUrl)) {
                        return Result.get().parse(0).url(dataUrl).header(header).string();
                    }
                }
                if (root.has(keyJsonUrl)) {
                    // smali 中找到 url 字段即立即返回，不回退到 vodParse
                    playUrl = root.get(keyJsonUrl).getAsString();
                    return Result.get().parse(0).url(playUrl).header(header).string();
                }
            }
        }
        // 路径4：vodParse POST 接口——先做第二段 regex 替换（URL 编码 &url= 参数值），再构造签名请求
        // (&url=)(.*?)(?=&token)(&token) -> group1 + URLEncoder.encode(group2) + group3
        Pattern p2 = Pattern.compile("(&url=)(.*?)(?=&token)(&token)");
        Matcher m3 = p2.matcher(playUrl);
        StringBuffer sb2 = new StringBuffer();
        boolean secondRegexOk = true;
        while (m3.find()) {
            try {
                m3.appendReplacement(sb2, m3.group(1) + URLEncoder.encode(m3.group(2), "UTF-8") + m3.group(3));
            } catch (Exception e) {
                secondRegexOk = false;
                break;
            }
        }
        if (secondRegexOk) {
            m3.appendTail(sb2);
            playUrl = sb2.toString();
        }
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
        HashMap<String, String> signHeaders = buildHeaders(true);
        signHeaders.put("Connection", "Keep-Alive");
        signHeaders.put("Content-Type", "application/x-www-form-urlencoded");
        signHeaders.put("app-api-verify-time", timestamp);
        // 用 AES/CBC/PKCS5Padding 加密时间戳作为签名
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        SecretKeySpec keySpec = new SecretKeySpec(aesKey.getBytes(StandardCharsets.UTF_8), "AES");
        IvParameterSpec ivSpec = new IvParameterSpec(aesIv.getBytes(StandardCharsets.UTF_8));
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);
        byte[] encSign = cipher.doFinal(timestamp.getBytes(StandardCharsets.UTF_8));
        signHeaders.put("app-api-verify-sign", Base64.encodeToString(encSign, Base64.NO_WRAP));
        String parseUrl = apiUrl + "/api.php/qijiappapi.index/vodParse";
        String resp = OkHttp.postFormRaw(parseUrl, playUrl, signHeaders);
        JsonObject root = JsonParser.parseString(resp).getAsJsonObject();
        String encryptedData = root.get("data").getAsString();
        String decrypted = Crypto.CBC(encryptedData, aesKey, aesIv);
        // 两层 JSON：解密后取 "json" 字段（其值为 JSON 字符串），再解析取 "url"
        JsonObject dataObj = JsonParser.parseString(decrypted).getAsJsonObject();
        String innerJson = dataObj.get("json").getAsString();
        JsonObject innerObj = JsonParser.parseString(innerJson).getAsJsonObject();
        String finalUrl = innerObj.get(keyJsonUrl).getAsString();
        if (!TextUtils.isEmpty(finalUrl)) {
            return Result.get().parse(0).url(finalUrl).header(header).string();
        }
        return Result.get().parse(0).url(playUrl).header(header).string();
    }

    /**
     * 业务接口统一 POST：URL = apiUrl + "/api.php" + path，body 为 JSON 字符串，
     * 响应中 {@code data} 字段经 AES/CBC/PKCS5Padding 加密，需解密后返回。
     */
    private String postJson(String path, String body) {
        HashMap<String, String> header = buildHeaders(false);
        header.put("Content-Type", "application/x-www-form-urlencoded");
        String url = apiUrl + "/api.php" + path;
        // 使用 postFormRaw 以尊重 header 中的 Content-Type（application/x-www-form-urlencoded），
        // OkHttp.post 会强制 application/json 导致服务器拒绝请求
        String resp = OkHttp.postFormRaw(url, body, header);
        JsonObject root = JsonParser.parseString(resp).getAsJsonObject();
        String data = root.get("data").getAsString();
        return Crypto.CBC(data, aesKey, aesIv);
    }

    @Override
    public String searchContent(String key, boolean quick, String pg) throws Exception {
        JsonObject body = new JsonObject();
        body.addProperty("type_id", 0);
        body.addProperty("keywords", key);
        body.addProperty("page", Integer.parseInt(pg));
        String path = "/qijiappapi.index/" + searchList;
        String resp = postJson(path, body.toString());
        JsonObject root = JsonParser.parseString(resp).getAsJsonObject();
        ArrayList<Vod> list = new ArrayList<>();
        if (root.has("search_list")) {
            Iterator<JsonElement> it = root.getAsJsonArray("search_list").iterator();
            while (it.hasNext()) {
                list.add(toVod(it.next().getAsJsonObject()));
            }
        }
        int page = Integer.parseInt(pg);
        return Result.get().vod(list).page(page, 9999, 90, 999999).string();
    }

    /**
     * 从列表项 JSON 构造 Vod（4 参构造：vod_id/vod_name/vod_pic/vod_remarks）。
     */
    private Vod toVod(JsonObject item) {
        String vodId = item.get("vod_id").getAsString();
        String vodName = item.get("vod_name").getAsString();
        String vodPic = item.get("vod_pic").getAsString();
        String remarks = item.has("vod_remarks") ? item.get("vod_remarks").getAsString() : "";
        return new Vod(vodId, vodName, vodPic, remarks);
    }
}
