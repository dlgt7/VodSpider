package com.github.catvod.spider;

import android.content.Context;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.net.OkHttp;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * App3Q Spider - 三秋影视
 */
public class App3Q extends Spider {

    private final ArrayList<String> hosts = new ArrayList<>();
    private final Random random = new Random();
    private String siteUrl;
    private String mode = "app";
    private String time;
    private String nonce;
    private String errorMsg = "";

    @Override
    public void init(Context context, String ext) {
        hosts.clear();
        siteUrl = "https://asd123sx23xdacsx.top";
        mode = "app";
        String pinIp = null;

        if (ext != null && !ext.trim().isEmpty()) {
            ext = ext.trim();
            if (ext.startsWith("{")) {
                try {
                    JSONObject json = new JSONObject(ext);
                    // 解析 mode
                    String m = json.optString("mode", json.optString("api", "app")).trim();
                    if (!m.isEmpty()) {
                        mode = m;
                    } else {
                        mode = "app";
                    }
                    // 解析 pinIp
                    pinIp = json.optString("pinIp", json.optString("ip", "")).trim();
                    // 解析 hosts 数组
                    JSONArray hostsArray = json.optJSONArray("hosts");
                    if (hostsArray != null) {
                        for (int i = 0; i < hostsArray.length(); i++) {
                            addHost(hostsArray.optString(i));
                        }
                    }
                    // 解析单个 host
                    String host = json.optString("host", json.optString("url", ""));
                    if (!host.isEmpty()) {
                        addHost(host);
                    }
                } catch (Exception e) {
                    // JSON 解析失败，忽略
                }
            } else {
                // ext 不是 JSON，直接作为 host
                addHost(ext);
            }
        }

        // 如果没有配置任何 host，使用默认值
        if (hosts.isEmpty()) {
            addHost("https://asd123sx23xdacsx.top");
            addHost("https://bbys.app");
        }

        // 使用第一个 host 作为当前站点
        siteUrl = hosts.get(0);
    }

    private void addHost(String host) {
        if (host == null || host.trim().isEmpty()) {
            return;
        }
        host = host.trim();
        // 移除末尾的 /
        if (host.endsWith("/")) {
            host = host.substring(0, host.length() - 1);
        }
        // 确保以 http 开头
        if (!host.startsWith("http")) {
            host = "https://" + host;
        }
        // 避免重复添加
        if (!hosts.contains(host)) {
            hosts.add(host);
        }
    }

    private String getApiPath() {
        if ("web".equalsIgnoreCase(mode)) {
            return "/api.php/web";
        } else {
            return "/api.php/app";
        }
    }

    private JSONObject requestApi(String path) throws IOException {
        errorMsg = "";
        LinkedHashSet<String> tried = new LinkedHashSet<>();

        for (String host : hosts) {
            if (tried.contains(host)) {
                continue;
            }
            tried.add(host);

            try {
                String url = host + path;
                Map<String, String> headers = getHeaders(host);
                String response = OkHttp.string(url, headers);
                JSONObject json = new JSONObject(response);
                siteUrl = host;

                int code = json.optInt("code", 1);
                if (code != 1 && code != 200) {
                    if (!json.has("msg")) {
                        return json;
                    }
                    throw new IOException("api code=" + code + " msg=" + json.optString("msg"));
                }
                return json;
            } catch (Exception e) {
                errorMsg = host + ": " + e.getMessage();
            }
        }

        // 所有源站都失败
        if (errorMsg.isEmpty()) {
            errorMsg = "三秋源站全部不可达";
        }
        throw new IOException(errorMsg);
    }

    private ArrayList<Vod> parseVodList(JSONArray array) {
        ArrayList<Vod> list = new ArrayList<>();
        if (array == null) {
            return list;
        }

        for (int i = 0; i < array.length(); i++) {
            JSONObject obj = array.optJSONObject(i);
            if (obj == null) {
                continue;
            }
            Vod vod = new Vod(
                    obj.optString("vod_id"),
                    obj.optString("vod_name"),
                    obj.optString("vod_pic"),
                    obj.optString("vod_remarks")
            );
            list.add(vod);
        }
        return list;
    }

    private Map<String, String> getPlayHeaders() {
        Map<String, String> headers = new HashMap<>();
        if ("web".equalsIgnoreCase(mode)) {
            headers.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/142.0.0.0 Safari/537.36");
        } else {
            headers.put("User-Agent", "okhttp/4.12.0");
        }
        headers.put("Referer", siteUrl + "/");
        return headers;
    }

    private Map<String, String> getHeaders(String host) {
        if ("web".equalsIgnoreCase(mode)) {
            Map<String, String> headers = new HashMap<>();
            headers.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36");
            headers.put("Accept", "application/json");
            headers.put("X-Client", "8f3d2a1c7b6e5d4c9a0b1f2e3d4c5b6a");
            headers.put("web-sign", "f65f3a83d6d9ad6f");
            headers.put("accept-language", "zh-CN,zh;q=0.9");
            headers.put("Referer", host + "/");
            return headers;
        } else {
            // App 模式的签名逻辑
            time = String.valueOf(System.currentTimeMillis() / 1000);
            nonce = String.valueOf(random.nextInt(999) + 1);

            Map<String, String> headers = new HashMap<>();
            headers.put("user-agent", "okhttp/4.12.0");
            headers.put("x-ave", "4");

            // 计算签名
            String finger = "SF-C3B2B41F6EFFFF9869176CF68F6790E8F07506FC88632C94B4F5F0430D5498CA";
            String id = "com.sunshine.tv";
            String sk = "SK-thanks";
            String v = "4";

            String signStr = String.format("finger=%s&id=%s&nonce=%s&sk=%s&time=%s&v=%s",
                    finger, id, nonce, sk, time, v);

            try {
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                byte[] digest = md.digest(signStr.getBytes());
                StringBuilder sb = new StringBuilder();
                for (byte b : digest) {
                    sb.append(String.format("%02x", b));
                }
                headers.put("x-sign", sb.toString().toUpperCase());
            } catch (Exception e) {
                // SHA-256 计算失败，忽略
            }

            headers.put("x-aid", id);
            headers.put("x-time", time);
            headers.put("x-nonc", nonce);
            headers.put("x-device-id", "0b4328287a5d953e");
            headers.put("x-device-brand", "OnePlus");
            headers.put("x-device-model", "HD1900");
            headers.put("x-update-id", "73dc2ffc-8350-c022-fac9-da982c95f513");

            return headers;
        }
    }

    private static String joinList(ArrayList<String> list) {
        StringBuilder sb = new StringBuilder();
        for (String item : list) {
            if (sb.length() > 0) {
                sb.append("$$$");
            }
            sb.append(item);
        }
        return sb.toString();
    }

    @Override
    public String homeContent(boolean filter) {
        try {
            String path = getApiPath() + "/index/home";
            JSONObject json = requestApi(path);
            JSONObject data = json.getJSONObject("data");

            ArrayList<Class> classes = new ArrayList<>();
            JSONArray categories = data.optJSONArray("categories");
            if (categories != null) {
                for (int i = 0; i < categories.length(); i++) {
                    JSONObject cat = categories.getJSONObject(i);
                    String typeName = cat.optString("type_name");
                    if (!typeName.isEmpty()) {
                        classes.add(new Class(typeName, typeName));
                    }
                }
            }

            if (classes.isEmpty()) {
                return Result.error("三秋首页无分类（源站返回空 categories）");
            }

            JSONArray recommend = data.optJSONArray("recommend");
            ArrayList<Vod> list = parseVodList(recommend);

            return Result.string(classes, list);
        } catch (Exception e) {
            return Result.error("三秋源站不可达: " + e.getMessage() + "；可配置 ext.host 为可用镜像");
        }
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append(getApiPath());
            sb.append("/filter/vod?type_name=");
            sb.append(tid);
            sb.append("&page=");
            sb.append(pg);
            sb.append("&sort=hits");

            String path = sb.toString();
            if ("web".equalsIgnoreCase(mode)) {
                path = path + "&limit=20";
            }

            JSONObject json = requestApi(path);
            JSONArray data = json.optJSONArray("data");
            ArrayList<Vod> list = parseVodList(data);

            int page = Integer.parseInt(pg);
            return Result.get().vod(list).page(page, 0, 0, 0).string();
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @Override
    public String detailContent(List<String> ids) {
        try {
            String path = getApiPath() + "/vod/get_detail?vod_id=" + ids.get(0);
            JSONObject json = requestApi(path);

            JSONArray dataArray = json.getJSONArray("data");
            JSONObject data = dataArray.getJSONObject(0);

            // 解析播放器映射
            HashMap<String, String> playerMap = new HashMap<>();
            JSONArray vodplayer = json.optJSONArray("vodplayer");
            if (vodplayer != null) {
                for (int i = 0; i < vodplayer.length(); i++) {
                    JSONObject player = vodplayer.getJSONObject(i);
                    String from = player.optString("from");
                    String show = player.optString("show");
                    playerMap.put(from, show);
                }
            }

            // 解析播放列表
            String vodPlayFrom = data.optString("vod_play_from");
            String[] froms = vodPlayFrom.split("\\$\\$\\$");
            String[] urls = data.optString("vod_play_url").split("\\$\\$\\$", -1);

            ArrayList<String> playFrom = new ArrayList<>();
            ArrayList<String> playUrl = new ArrayList<>();

            String vodName = data.optString("vod_name");

            for (int i = 0; i < froms.length; i++) {
                String from = froms[i];
                String showName = playerMap.containsKey(from) ? playerMap.get(from) : from;
                playFrom.add(showName);

                if (i >= urls.length) {
                    playUrl.add("");
                    continue;
                }

                StringBuilder urlBuilder = new StringBuilder();
                String[] episodes = urls[i].split("#");
                for (String episode : episodes) {
                    String[] parts = episode.split("\\$", 2);
                    if (parts.length < 2) {
                        continue;
                    }

                    String name = parts[0];
                    String url = parts[1];

                    // 提取集数编号
                    String episodeNum = name.replaceAll("\\D+", "");
                    if (episodeNum.isEmpty()) {
                        episodeNum = "1";
                    }

                    if (urlBuilder.length() > 0) {
                        urlBuilder.append("#");
                    }
                    urlBuilder.append(name);
                    urlBuilder.append("$");
                    urlBuilder.append(url);
                    urlBuilder.append("@");
                    urlBuilder.append(from);
                    urlBuilder.append("@");
                    urlBuilder.append(vodName);
                    urlBuilder.append("@");
                    urlBuilder.append(episodeNum);
                }
                playUrl.add(urlBuilder.toString());
            }

            Vod vod = new Vod(ids.get(0), vodName, data.optString("vod_pic"), data.optString("vod_remarks"));
            vod.setTypeName(data.optString("vod_class"));
            vod.setVodContent(data.optString("vod_content").trim());
            vod.setVodActor(data.optString("vod_actor"));
            vod.setVodDirector(data.optString("vod_director"));
            vod.setVodPlayFrom(joinList(playFrom));
            vod.setVodPlayUrl(joinList(playUrl));

            return Result.string(vod);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) {
        try {
            String[] parts = id.split("@");
            if (parts.length < 2) {
                return Result.error("播放参数错误");
            }

            String url = parts[0].trim();
            String vodFrom = parts[1].trim();

            // 如果 URL 已经是直接播放链接
            if (url.matches(".*(m3u8|mp4|flv|avi|mov|mkv).*")) {
                return Result.get()
                        .parse(0)
                        .url(url)
                        .header(getPlayHeaders())
                        .string();
            }

            // 需要解析播放链接
            String path = getApiPath() + "/decode/url/?url=" + URLEncoder.encode(url, "UTF-8") + "&vodFrom=" + vodFrom;
            String token = "";

            // 尝试最多 3 次
            for (int i = 0; i < 3; i++) {
                String requestUrl = siteUrl + path + token;
                Map<String, String> headers = getHeaders(siteUrl);
                String response = OkHttp.string(requestUrl, headers);
                JSONObject json = new JSONObject(response);

                // App 模式可能需要处理 challenge
                if (!"web".equalsIgnoreCase(mode)) {
                    int code = json.optInt("code", -1);
                    if (code == 2 && json.has("challenge")) {
                        // 处理 challenge
                        String challenge = json.optString("challenge").trim();
                        try {
                            Pattern pattern = Pattern.compile("_0x1\\s*=\\s*\\[(.*?)\\];");
                            Matcher matcher = pattern.matcher(challenge);
                            if (matcher.find()) {
                                String[] values = matcher.group(1).split(",");
                                String v0 = values[0].replaceAll("[\'\"]", "").trim();
                                String v1 = values[1].replaceAll("[\'\"]", "").trim();
                                String v2 = values[2].replaceAll("[\'\"]", "").trim();
                                String v3 = values[3].replaceAll("[\'\"]", "").trim();

                                // 计算 hash
                                String combined = String.format("%s:%s:%s:%s", v0, v1, v2, v3);
                                long hash = 0;
                                for (int j = 0; j < combined.length(); j++) {
                                    hash = (hash << 5) - hash + combined.charAt(j);
                                    hash = hash & 0xffffffffL;
                                }

                                // 生成 token
                                token = "&token=" + String.format("%s:%s:%s",
                                        v0,
                                        Long.toHexString(Math.abs(hash)),
                                        v1.substring(0, Math.min(8, v1.length())));
                            }
                        } catch (Exception e) {
                            // challenge 解析失败，继续尝试
                        }
                        continue;
                    }
                }

                // 检查返回的播放链接
                String playUrl = json.optString("data").trim();
                if (playUrl.startsWith("http")) {
                    return Result.get()
                            .parse(0)
                            .url(playUrl)
                            .header(getPlayHeaders())
                            .string();
                }

                // 如果返回的是 JSON 格式
                if (playUrl.startsWith("{")) {
                    try {
                        JSONObject playJson = new JSONObject(playUrl);
                        if (playJson.has("url")) {
                            return Result.get()
                                    .parse(playJson.optInt("parse", 0))
                                    .url(playJson.optString("url"))
                                    .header(getPlayHeaders())
                                    .string();
                        }
                    } catch (Exception e) {
                        // JSON 解析失败，继续
                    }
                }

                // 如果返回非空，直接返回
                if (!playUrl.isEmpty()) {
                    return playUrl;
                }
            }

            return Result.error("播放链接解析失败，请换源");
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @Override
    public String searchContent(String key, boolean quick) {
        try {
            String path = getApiPath() + "/search/index?wd=" + URLEncoder.encode(key, "UTF-8") + "&page=1&limit=15";
            JSONObject json = requestApi(path);
            JSONArray data = json.optJSONArray("data");
            ArrayList<Vod> list = parseVodList(data);
            return Result.string(list);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }
}