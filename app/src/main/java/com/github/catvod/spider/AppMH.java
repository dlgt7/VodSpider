package com.github.catvod.spider;

import android.content.Context;
import android.text.TextUtils;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Danmaku;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.net.OkHttp;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 马猴源爬虫实现。
 * 提供视频搜索、分类列表、详情页解析及播放地址获取。
 * 站点接口使用 SHA-256 签名鉴权，并支持播放链接的二次解码与挑战验证。
 */
public class AppMH extends Spider {

    private static final String DEFAULT_HOST = "https://45.150.167.18:8000";
    private static final String API_HOME = "/api.php/app/index/home";
    private static final String API_FILTER = "/api.php/app/filter/vod?type_name=";
    private static final String API_DETAIL = "/api.php/app/vod/get_detail?vod_id=";
    private static final String API_DECODE = "/api.php/app/decode/url/?url=";
    private static final String API_SEARCH = "/api.php/app/search/index?wd=";

    private static final String UTF_8 = "UTF-8";
    private static final String SEP_SOURCE = "$$$";
    private static final String SEP_SOURCE_REGEX = "\\$\\$\\$";
    private static final String SEP_EPISODE = "#";
    private static final String SEP_EP_NAME = "$";
    private static final String SEP_EP_NAME_REGEX = "\\$";
    private static final String SEP_PLAY_FIELD = "@";

    private static final String KEY_DATA = "data";
    private static final String KEY_CODE = "code";
    private static final String KEY_FROM = "from";
    private static final String KEY_SHOW = "show";
    private static final String KEY_CATEGORIES = "categories";
    private static final String KEY_TYPE_NAME = "type_name";
    private static final String KEY_RECOMMEND = "recommend";
    private static final String KEY_CHALLENGE = "challenge";
    private static final String KEY_VODPLAYER = "vodplayer";

    private static final String FIELD_VOD_ID = "vod_id";
    private static final String FIELD_VOD_NAME = "vod_name";
    private static final String FIELD_VOD_PIC = "vod_pic";
    private static final String FIELD_VOD_REMARKS = "vod_remarks";
    private static final String FIELD_VOD_CLASS = "vod_class";
    private static final String FIELD_VOD_CONTENT = "vod_content";
    private static final String FIELD_VOD_ACTOR = "vod_actor";
    private static final String FIELD_VOD_DIRECTOR = "vod_director";
    private static final String FIELD_VOD_PLAY_FROM = "vod_play_from";
    private static final String FIELD_VOD_PLAY_URL = "vod_play_url";

    private static final String HEADER_USER_AGENT = "user-agent";
    private static final String HEADER_ACCEPT = "accept";
    private static final String HEADER_X_PLATFORM = "x-platform";
    private static final String HEADER_X_AVE = "x-ave";
    private static final String HEADER_X_AID = "x-aid";
    private static final String HEADER_X_TIME = "x-time";
    private static final String HEADER_X_NONC = "x-nonc";
    private static final String HEADER_X_SIGN = "x-sign";
    private static final String HEADER_X_DEVICE_ID = "x-device-id";
    private static final String HEADER_X_DEVICE_BRAND = "x-device-brand";
    private static final String HEADER_X_DEVICE_MODEL = "x-device-model";
    private static final String HEADER_X_UPDATE_ID = "x-update-id";

    private static final String VALUE_UA = "okhttp/4.12.0";
    private static final String VALUE_ACCEPT = "application/json";
    private static final String VALUE_PLATFORM = "android";
    private static final String VALUE_AVE = "1";
    private static final String VALUE_AID = "com.damahou.tv";
    private static final String VALUE_DEVICE_ID = "23d5ba9ce57a9508";
    private static final String VALUE_DEVICE_BRAND = "vivo";
    private static final String VALUE_DEVICE_MODEL = "V2309A";
    private static final String VALUE_UPDATE_ID = "43c1ef69-3748-aaeb-317f-c621c77653ee";

    private static final String FINGERPRINT = "SF-A962FEC75DA28D7514F2A16580334272A78AC0A8429F10C94F47C1BAFC876E3F";
    private static final String SECRET_KEY = "SK-woniu-thanks";
    private static final String SIGN_TEMPLATE = "finger=%s&id=%s&nonce=%s&sk=%s&time=%s&v=%s";
    private static final String SHA_ALGORITHM = "SHA-256";

    private static final String VIDEO_REGEX = ".*(m3u8|mp4|flv|avi|mov|mkv).*";
    private static final String QUOTE_REGEX = "['\"]";
    private static final String ARRAY_REGEX = "_0x1\\s*=\\s*\\[(.*?)\\];";
    private static final String NON_DIGIT_REGEX = "\\D+";

    private static final String MSG_EMPTY_RESPONSE = "空响应";
    private static final String MSG_PLAY_PARAM_ERROR = "播放参数错误";
    private static final String MSG_PLAY_DECODE_FAIL = "播放链接解析失败，请换源";
    private static final String MSG_PLAY_DECODE_FAIL_ALT = "播放链接解析失败，请更换其他源播放";
    private static final String MSG_HOME_FAIL = "马猴首页失败: ";
    private static final String MSG_CATEGORY_FAIL = "马猴分类失败: ";
    private static final String MSG_DETAIL_FAIL = "马猴详情失败: ";
    private static final String MSG_SEARCH_FAIL = "马猴搜索失败: ";

    private static final int MAX_DECODE_RETRY = 3;
    private static final int CODE_CHALLENGE = 2;

    public String siteUrl;
    public final Random random;

    public AppMH() {
        this.siteUrl = DEFAULT_HOST;
        this.random = new Random();
    }

    /**
     * 解析挑战字符串中的 _0x1 数组并生成签名。
     * 格式：_0x1 = ['a','b','c','d'] → 拼接为 a:b:c:d 后计算哈希。
     * 返回 a:哈希十六进制:b 前 8 位。
     */
    private static String parseChallenge(String input) {
        try {
            Pattern pattern = Pattern.compile(ARRAY_REGEX);
            Matcher matcher = pattern.matcher(input);
            if (!matcher.find()) return "";
            String[] parts = matcher.group(1).split(",");
            if (parts.length < 4) return "";
            String part0 = parts[0].replaceAll(QUOTE_REGEX, "").trim();
            String part1 = parts[1].replaceAll(QUOTE_REGEX, "").trim();
            String part2 = parts[2].replaceAll(QUOTE_REGEX, "").trim();
            String part3 = parts[3].replaceAll(QUOTE_REGEX, "").trim();
            String combined = part0 + ":" + part1 + ":" + part2 + ":" + part3;
            long hash = 0;
            for (int i = 0; i < combined.length(); i++) {
                hash = ((hash << 5) - hash + combined.charAt(i)) & 0xFFFFFFFFL;
            }
            String hex = Long.toHexString(Math.abs(hash));
            String prefix = part1.substring(0, Math.min(8, part1.length()));
            return part0 + ":" + hex + ":" + prefix;
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 将 JSONArray 解析为 Vod 列表。
     */
    private static ArrayList<Vod> parseVodList(JSONArray array) {
        ArrayList<Vod> list = new ArrayList<>();
        if (array == null) return list;
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item == null) continue;
            Vod vod = new Vod(
                    item.optString(FIELD_VOD_ID),
                    item.optString(FIELD_VOD_NAME),
                    item.optString(FIELD_VOD_PIC),
                    item.optString(FIELD_VOD_REMARKS));
            list.add(vod);
        }
        return list;
    }

    /**
     * 构建弹幕 URL。
     * 仅当 vodUrl 以 http 开头时附加 vodUrl 参数。
     */
    private static String buildDanmakuUrl(String vodName, String vodIndex, String vodUrl) {
        if (TextUtils.isEmpty(vodName)) return "";
        try {
            StringBuilder sb = new StringBuilder(Proxy.getUrl());
            sb.append("?do=appdanmu&vodName=").append(URLEncoder.encode(vodName.trim(), UTF_8));
            String idx = TextUtils.isEmpty(vodIndex) ? "1" : vodIndex.trim();
            sb.append("&vodIndex=").append(URLEncoder.encode(idx, UTF_8));
            if (!TextUtils.isEmpty(vodUrl) && vodUrl.startsWith("http")) {
                sb.append("&vodUrl=").append(URLEncoder.encode(vodUrl, UTF_8));
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 计算 SHA-256 并返回大写十六进制字符串。
     */
    private static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance(SHA_ALGORITHM);
            byte[] bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString().toUpperCase(Locale.ROOT);
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 请求站点 API 并返回 JSON 数据。
     */
    private JSONObject fetchApi(String path) throws Exception {
        String url = siteUrl + path;
        String response = OkHttp.string(url, buildHeaders());
        if (TextUtils.isEmpty(response)) {
            throw new Exception(MSG_EMPTY_RESPONSE);
        }
        return new JSONObject(response);
    }

    /**
     * 构建带 SHA-256 签名的请求头。
     */
    private Map<String, String> buildHeaders() {
        String time = String.valueOf(System.currentTimeMillis() / 1000);
        String nonce = String.valueOf(random.nextInt(999) + 1);
        HashMap<String, String> headers = new HashMap<>();
        headers.put(HEADER_USER_AGENT, VALUE_UA);
        headers.put(HEADER_ACCEPT, VALUE_ACCEPT);
        headers.put(HEADER_X_PLATFORM, VALUE_PLATFORM);
        headers.put(HEADER_X_AVE, VALUE_AVE);
        headers.put(HEADER_X_AID, VALUE_AID);
        headers.put(HEADER_X_TIME, time);
        headers.put(HEADER_X_NONC, nonce);
        String signSource = String.format(Locale.US, SIGN_TEMPLATE, FINGERPRINT, VALUE_AID, nonce, SECRET_KEY, time, VALUE_AVE);
        headers.put(HEADER_X_SIGN, sha256Hex(signSource));
        headers.put(HEADER_X_DEVICE_ID, VALUE_DEVICE_ID);
        headers.put(HEADER_X_DEVICE_BRAND, VALUE_DEVICE_BRAND);
        headers.put(HEADER_X_DEVICE_MODEL, VALUE_DEVICE_MODEL);
        headers.put(HEADER_X_UPDATE_ID, VALUE_UPDATE_ID);
        return headers;
    }

    @Override
    public void init(Context context, String extend) throws Exception {
        if (TextUtils.isEmpty(extend)) {
            siteUrl = DEFAULT_HOST;
            return;
        }
        String url = extend.trim();
        if (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        if (!url.startsWith("http")) {
            url = "https://" + url;
        }
        siteUrl = url;
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        try {
            JSONObject data = fetchApi(API_HOME).getJSONObject(KEY_DATA);
            ArrayList<Class> classes = new ArrayList<>();
            JSONArray categories = data.optJSONArray(KEY_CATEGORIES);
            if (categories != null) {
                for (int i = 0; i < categories.length(); i++) {
                    JSONObject item = categories.getJSONObject(i);
                    String typeName = item.optString(KEY_TYPE_NAME);
                    if (TextUtils.isEmpty(typeName) || "电影".equals(typeName)) continue;
                    classes.add(new Class(typeName, typeName));
                }
            }
            JSONArray recommend = data.optJSONArray(KEY_RECOMMEND);
            ArrayList<Vod> list = parseVodList(recommend);
            return Result.string(classes, list);
        } catch (Exception e) {
            return Result.error(MSG_HOME_FAIL + e.getMessage());
        }
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        try {
            String url = API_FILTER + URLEncoder.encode(tid, UTF_8) + "&page=" + pg + "&sort=hits";
            JSONArray data = fetchApi(url).optJSONArray(KEY_DATA);
            ArrayList<Vod> list = parseVodList(data);
            int page = Integer.parseInt(pg);
            return Result.string(page, 0, 0, 0, list);
        } catch (Exception e) {
            return Result.error(MSG_CATEGORY_FAIL + e.getMessage());
        }
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        try {
            String id = ids.get(0);
            JSONObject data = fetchApi(API_DETAIL + id).getJSONArray(KEY_DATA).getJSONObject(0);
            HashMap<String, String> fromMap = new HashMap<>();
            JSONArray playerArr = data.optJSONArray(KEY_VODPLAYER);
            if (playerArr != null) {
                for (int i = 0; i < playerArr.length(); i++) {
                    JSONObject player = playerArr.getJSONObject(i);
                    fromMap.put(player.optString(KEY_FROM), player.optString(KEY_SHOW));
                }
            }
            String vodName = data.optString(FIELD_VOD_NAME);
            String[] fromArray = data.optString(FIELD_VOD_PLAY_FROM).split(SEP_SOURCE_REGEX);
            String[] playUrlArray = data.optString(FIELD_VOD_PLAY_URL).split(SEP_SOURCE_REGEX, -1);
            ArrayList<String> fromList = new ArrayList<>();
            ArrayList<String> urlList = new ArrayList<>();
            for (int i = 0; i < fromArray.length; i++) {
                String fromName = fromMap.containsKey(fromArray[i]) ? fromMap.get(fromArray[i]) : fromArray[i];
                fromList.add(fromName);
                if (i >= playUrlArray.length) {
                    urlList.add("");
                    continue;
                }
                String[] episodes = playUrlArray[i].split(SEP_EPISODE);
                StringBuilder sb = new StringBuilder();
                for (String episode : episodes) {
                    String[] parts = episode.split(SEP_EP_NAME_REGEX, 2);
                    if (parts.length < 2) continue;
                    String playUrl = parts[0];
                    String epNum = playUrl.replaceAll(NON_DIGIT_REGEX, "");
                    if (epNum.isEmpty()) epNum = "1";
                    if (sb.length() > 0) sb.append(SEP_EPISODE);
                    sb.append(playUrl).append(SEP_EP_NAME).append(parts[1])
                            .append(SEP_PLAY_FIELD).append(fromName)
                            .append(SEP_PLAY_FIELD).append(vodName)
                            .append(SEP_PLAY_FIELD).append(epNum);
                }
                urlList.add(sb.toString());
            }
            Vod vod = new Vod(id, vodName, data.optString(FIELD_VOD_PIC), data.optString(FIELD_VOD_REMARKS));
            vod.setTypeName(data.optString(FIELD_VOD_CLASS));
            vod.setVodContent(data.optString(FIELD_VOD_CONTENT).trim());
            vod.setVodActor(data.optString(FIELD_VOD_ACTOR));
            vod.setVodDirector(data.optString(FIELD_VOD_DIRECTOR));
            vod.setVodPlayFrom(TextUtils.join(SEP_SOURCE, fromList));
            vod.setVodPlayUrl(TextUtils.join(SEP_SOURCE, urlList));
            return Result.string(vod);
        } catch (Exception e) {
            return Result.error(MSG_DETAIL_FAIL + e.getMessage());
        }
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        try {
            String[] parts = id.split(SEP_PLAY_FIELD);
            if (parts.length < 2) {
                return Result.error(MSG_PLAY_PARAM_ERROR);
            }
            String playUrl = parts[0].trim();
            String fromName = parts[1].trim();
            String vodName = parts.length > 2 ? parts[2].trim() : "";
            String epNum = parts.length > 3 ? parts[3].trim() : "1";
            String danmakuUrl = buildDanmakuUrl(vodName, epNum, "");
            if (playUrl.matches(VIDEO_REGEX)) {
                Result result = Result.get().parse(0).url(playUrl);
                if (!TextUtils.isEmpty(danmakuUrl)) {
                    result.danmaku(Arrays.asList(Danmaku.from(danmakuUrl)));
                }
                return result.string();
            }
            String decodePath = API_DECODE + URLEncoder.encode(playUrl, UTF_8) + "&vodFrom=" + URLEncoder.encode(fromName, UTF_8);
            for (int i = 0; i < MAX_DECODE_RETRY; i++) {
                String url = siteUrl + decodePath;
                String response = OkHttp.string(url, buildHeaders());
                if (TextUtils.isEmpty(response)) continue;
                JSONObject json = new JSONObject(response);
                if (json.optInt(KEY_CODE, -1) == CODE_CHALLENGE && json.has(KEY_CHALLENGE)) {
                    String challenge = parseChallenge(json.optString(KEY_CHALLENGE).trim());
                    if (!TextUtils.isEmpty(challenge)) {
                        decodePath = decodePath + "&token=" + URLEncoder.encode(challenge, UTF_8);
                    }
                    continue;
                }
                String data = json.optString(KEY_DATA).trim();
                if (data.startsWith("http")) {
                    String realDanmakuUrl = buildDanmakuUrl(vodName, epNum, data);
                    Result result = Result.get().parse(0).url(data);
                    if (!TextUtils.isEmpty(realDanmakuUrl)) {
                        result.danmaku(Arrays.asList(Danmaku.from(realDanmakuUrl)));
                    }
                    return result.string();
                }
            }
            return Result.error(MSG_PLAY_DECODE_FAIL);
        } catch (Exception e) {
            return Result.error(MSG_PLAY_DECODE_FAIL_ALT);
        }
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        try {
            String url = API_SEARCH + URLEncoder.encode(key, UTF_8) + "&page=1&limit=15";
            JSONArray data = fetchApi(url).optJSONArray(KEY_DATA);
            ArrayList<Vod> list = parseVodList(data);
            return Result.string(list);
        } catch (Exception e) {
            return Result.error(MSG_SEARCH_FAIL + e.getMessage());
        }
    }
}
