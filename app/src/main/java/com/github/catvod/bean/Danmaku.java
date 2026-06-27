package com.github.catvod.bean;

import android.text.TextUtils;

import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Json;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Type;
import java.net.URLEncoder;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.Inflater;

public class Danmaku {

    @SerializedName("name")
    private String name;

    @SerializedName("url")
    private String url;

    private transient boolean selected;

    public static List<Danmaku> arrayFrom(String str) {
        Type listType = new TypeToken<List<Danmaku>>() {}.getType();
        List<Danmaku> items = Json.fromJson(str, listType);
        return items == null ? Collections.emptyList() : items;
    }

    public static Danmaku from(String path) {
        Danmaku danmaku = new Danmaku();
        danmaku.setName(path);
        danmaku.setUrl(path);
        return danmaku;
    }

    public static Danmaku empty() {
        return new Danmaku();
    }

    /**
     * 创建B站弹幕对象
     * @param cid B站视频cid
     * @return 已配置好URL的Danmaku对象
     */
    public static Danmaku bili(String cid) {
        Danmaku danmaku = new Danmaku();
        danmaku.setName("B站");
        danmaku.setUrl("https://api.bilibili.com/x/v1/dm/list.so?oid=" + cid);
        return danmaku;
    }

    /**
     * 拉取弹幕原始字节数据（最底层，调用方自行处理解压/解码）
     * 适用于任何弹幕接口
     * @param url 弹幕接口URL
     * @param headers 请求头
     * @return 原始字节数组，失败返回空数组
     */
    public static byte[] fetchBytes(String url, Map<String, String> headers) {
        try {
            byte[] bytes = OkHttp.bytes(url, headers);
            return bytes == null ? new byte[0] : bytes;
        } catch (Exception e) {
            return new byte[0];
        }
    }

    /**
     * 拉取弹幕文本内容（适用于未压缩的JSON/XML/ASS接口，如爱奇艺/优酷/AcFun等）
     * @param url 弹幕接口URL
     * @param headers 请求头
     * @return UTF-8字符串，失败返回空字符串
     */
    public static String fetchText(String url, Map<String, String> headers) {
        byte[] bytes = fetchBytes(url, headers);
        if (bytes.length == 0) return "";
        try {
            return new String(bytes, "UTF-8");
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 拉取弹幕并解压deflate数据（适用于B站等使用raw deflate压缩的接口）
     * @param url 弹幕接口URL
     * @param headers 请求头
     * @return 解压后的UTF-8字符串，失败返回空字符串
     */
    public static String fetchDeflate(String url, Map<String, String> headers) {
        byte[] bytes = fetchBytes(url, headers);
        return deflateToString(bytes);
    }

    /**
     * 解压raw deflate数据（无zlib头）
     * @param compressed 压缩字节数组
     * @return 解压后的UTF-8字符串，失败返回空字符串
     */
    public static String deflateToString(byte[] compressed) {
        if (compressed == null || compressed.length == 0) return "";
        Inflater inflater = new Inflater(true);
        try {
            inflater.setInput(compressed);
            ByteArrayOutputStream out = new ByteArrayOutputStream(compressed.length);
            byte[] buffer = new byte[4096];
            while (!inflater.finished()) {
                int count = inflater.inflate(buffer);
                if (count == 0) {
                    if (inflater.needsInput() || inflater.needsDictionary()) break;
                }
                out.write(buffer, 0, count);
            }
            return out.toString("UTF-8");
        } catch (Exception e) {
            return "";
        } finally {
            inflater.end();
        }
    }

    // ===== DanDanPlay (弹弹play) 第三方弹幕匹配 =====

    private static final String DANDANPLAY_API = "https://api.dandanplay.net/api/v2";
    private static final String DANDANPLAY_UA = "Dalvik/2.1.0 (Linux; U; Android 13)";
    // 在 https://dev.dandanplay.com/ 注册申请，填入你的 AppId 和 AppSecret
    private static final String DANDANPLAY_APP_ID = "";
    private static final String DANDANPLAY_APP_SECRET = "";

    /**
     * 生成DanDanPlay签名
     * 算法：Base64(SHA256(AppId + Timestamp + Path + AppSecret))
     */
    private static String dandanplaySign(String path, long timestamp) {
        try {
            String data = DANDANPLAY_APP_ID + timestamp + path + DANDANPLAY_APP_SECRET;
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data.getBytes("UTF-8"));
            return android.util.Base64.encodeToString(hash, android.util.Base64.NO_WRAP);
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 构建DanDanPlay请求头（含签名认证）
     * @param path API路径，如 /api/v2/search/episodes
     */
    private static Map<String, String> dandanplayHeaders(String path) {
        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", DANDANPLAY_UA);
        headers.put("Accept", "application/json");
        if (!TextUtils.isEmpty(DANDANPLAY_APP_ID) && !TextUtils.isEmpty(DANDANPLAY_APP_SECRET)) {
            long timestamp = System.currentTimeMillis() / 1000;
            headers.put("X-AppId", DANDANPLAY_APP_ID);
            headers.put("X-Timestamp", String.valueOf(timestamp));
            headers.put("X-Signature", dandanplaySign(path, timestamp));
        }
        return headers;
    }

    /**
     * 创建弹弹play弹幕对象
     * @param episodeId 弹弹play的episodeId
     * @return Danmaku对象，URL格式为 dandanplay://comment/{episodeId}
     */
    public static Danmaku dandanplay(String episodeId) {
        Danmaku danmaku = new Danmaku();
        danmaku.setName("弹弹play");
        danmaku.setUrl("dandanplay://comment/" + episodeId);
        return danmaku;
    }

    /**
     * 按标题搜索弹弹play动漫
     * @param title 视频标题
     * @return animes数组，每个元素含 animeId/title/type/episodes
     */
    public static JsonArray dandanplaySearch(String title) {
        if (TextUtils.isEmpty(title)) return new JsonArray();
        try {
            String path = "/api/v2/search/episodes";
            String url = DANDANPLAY_API + "/search/episodes?anime=" + URLEncoder.encode(title, "UTF-8");
            String json = fetchText(url, dandanplayHeaders(path));
            if (TextUtils.isEmpty(json)) return new JsonArray();
            JsonObject obj = Json.parse(json).getAsJsonObject();
            return Json.getJsonArray(obj, "animes");
        } catch (Exception e) {
            return new JsonArray();
        }
    }

    /**
     * 拉取弹弹play弹幕并转换为播放器兼容的XML格式
     * @param episodeId 弹弹play的episodeId
     * @return XML字符串（<i><d p="time,type,color,size">text</d>...</i>），失败返回空字符串
     */
    public static String fetchDandanplay(String episodeId) {
        if (TextUtils.isEmpty(episodeId)) return "";
        try {
            String path = "/api/v2/comment/" + episodeId;
            String url = DANDANPLAY_API + "/comment/" + episodeId + "?withRelated=true&chConvert=0";
            String json = fetchText(url, dandanplayHeaders(path));
            return dandanplayJsonToXml(json);
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 将弹弹play JSON弹幕转换为播放器XML格式
     * 弹弹play: {"comments":[{"cid":1,"p":"10.5,1,16777215,25","m":"文字"}]}
     * XML: <i><d p="10.5,1,16777215,25">文字</d></i>
     */
    public static String dandanplayJsonToXml(String json) {
        if (TextUtils.isEmpty(json)) return "";
        try {
            JsonObject obj = Json.parse(json).getAsJsonObject();
            JsonArray comments = Json.getJsonArray(obj, "comments");
            if (comments == null || comments.size() == 0) return "";
            StringBuilder sb = new StringBuilder();
            sb.append("<i>");
            for (JsonElement elem : comments) {
                JsonObject c = elem.getAsJsonObject();
                String p = Json.getString(c, "p");
                String m = Json.getString(c, "m");
                if (TextUtils.isEmpty(p)) continue;
                sb.append("<d p=\"").append(p).append("\">");
                sb.append(escapeXml(m));
                sb.append("</d>");
            }
            sb.append("</i>");
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private static String escapeXml(String text) {
        if (TextUtils.isEmpty(text)) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    public String getName() {
        return TextUtils.isEmpty(name) ? getUrl() : name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUrl() {
        return TextUtils.isEmpty(url) ? "" : url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    public boolean isEmpty() {
        return getUrl().isEmpty();
    }

    public String getRealUrl() {
        String u = getUrl();
        if (u.startsWith("/")) u = "file:/" + u;
        return u;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Danmaku)) return false;
        return getUrl().equals(((Danmaku) obj).getUrl());
    }

    @Override
    public String toString() {
        return Json.toJson(this);
    }
}
