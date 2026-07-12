package com.github.catvod.spider;

import com.github.catvod.crawler.Spider;
import com.github.catvod.net.OkHttp;
import com.github.catvod.net.OkResult;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Qmdj extends Spider {

    private static final String XURL = "https://api-store.qmplaylet.com";
    private static final String XURL1 = "https://api-read.qmplaylet.com";
    private static final String KEYS = "d3dGiJc651gSQ8w1";

    private Map<String, String> getHeaders() {
        Map<String, Object> data = new HashMap<>();
        data.put("static_score", "0.8");
        data.put("uuid", "00000000-7fc7-08dc-0000-000000000000");
        data.put("device-id", "20250220125449b9b8cac84c2dd3d035c9052a2572f7dd0122edde3cc42a70");
        data.put("mac", "");
        data.put("sourceuid", "aa7de295aad621a6");
        data.put("refresh-type", "0");
        data.put("model", "22021211RC");
        data.put("wlb-imei", "");
        data.put("client-id", "aa7de295aad621a6");
        data.put("brand", "Redmi");
        data.put("oaid", "");
        data.put("oaid-no-cache", "");
        data.put("sys-ver", "12");
        data.put("trusted-id", "");
        data.put("phone-level", "H");
        data.put("imei", "");
        data.put("wlb-uid", "aa7de295aad621a6");
        data.put("session-id", String.valueOf(System.currentTimeMillis()));

        JSONObject jsonData = new JSONObject(data);
        String jsonStr = jsonData.toString();
        String encoded = Base64.getEncoder().encodeToString(jsonStr.getBytes());

        Map<Character, Character> charMap = new HashMap<>();
        charMap.put('+', 'P'); charMap.put('/', 'X'); charMap.put('0', 'M'); charMap.put('1', 'U');
        charMap.put('2', 'l'); charMap.put('3', 'E'); charMap.put('4', 'r'); charMap.put('5', 'Y');
        charMap.put('6', 'W'); charMap.put('7', 'b'); charMap.put('8', 'd'); charMap.put('9', 'J');
        charMap.put('A', '9'); charMap.put('B', 's'); charMap.put('C', 'a'); charMap.put('D', 'I');
        charMap.put('E', '0'); charMap.put('F', 'o'); charMap.put('G', 'y'); charMap.put('H', '_');
        charMap.put('I', 'H'); charMap.put('J', 'G'); charMap.put('K', 'i'); charMap.put('L', 't');
        charMap.put('M', 'g'); charMap.put('N', 'N'); charMap.put('O', 'A'); charMap.put('P', '8');
        charMap.put('Q', 'F'); charMap.put('R', 'k'); charMap.put('S', '3'); charMap.put('T', 'h');
        charMap.put('U', 'f'); charMap.put('V', 'R'); charMap.put('W', 'q'); charMap.put('X', 'C');
        charMap.put('Y', '4'); charMap.put('Z', 'p'); charMap.put('a', 'm'); charMap.put('b', 'B');
        charMap.put('c', 'O'); charMap.put('d', 'u'); charMap.put('e', 'c'); charMap.put('f', '6');
        charMap.put('g', 'K'); charMap.put('h', 'x'); charMap.put('i', '5'); charMap.put('j', 'T');
        charMap.put('k', '-'); charMap.put('l', '2'); charMap.put('m', 'z'); charMap.put('n', 'S');
        charMap.put('o', 'Z'); charMap.put('p', '1'); charMap.put('q', 'V'); charMap.put('r', 'v');
        charMap.put('s', 'j'); charMap.put('t', 'Q'); charMap.put('u', '7'); charMap.put('v', 'D');
        charMap.put('w', 'w'); charMap.put('x', 'n'); charMap.put('y', 'L'); charMap.put('z', 'e');

        StringBuilder qmParams = new StringBuilder();
        for (char c : encoded.toCharArray()) {
            qmParams.append(charMap.getOrDefault(c, c));
        }

        String paramsStr = "AUTHORIZATION=" +
                "app-version=10001" +
                "application-id=com.duoduo.read" +
                "channel=unknown" +
                "is-white=" +
                "net-env=5" +
                "platform=android" +
                "qm-params=" + qmParams +
                "reg=" + KEYS;

        String sign = md5(paramsStr);

        Map<String, String> headers = new HashMap<>();
        headers.put("net-env", "5");
        headers.put("reg", "");
        headers.put("channel", "unknown");
        headers.put("is-white", "");
        headers.put("platform", "android");
        headers.put("application-id", "com.duoduo.read");
        headers.put("authorization", "");
        headers.put("app-version", "10001");
        headers.put("user-agent", "webviewversion/0");
        headers.put("qm-params", qmParams.toString());
        headers.put("sign", sign);
        return headers;
    }

    private String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b & 0xff));
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        try {
            String signStr = "operation=1playlet_privacy=1tag_id=0" + KEYS;
            String sign = md5(signStr);
            String url = XURL + "/api/v1/playlet/index?tag_id=0&playlet_privacy=1&operation=1&sign=" + sign;

            String response = OkHttp.string(url, getHeaders());
            JSONObject data = new JSONObject(response).getJSONObject("data");

            JSONArray classes = new JSONArray();
            int[] duoxuan = {0, 1, 2, 3, 4};
            for (int duo : duoxuan) {
                JSONArray js = data.getJSONArray("tag_categories").getJSONObject(duo).getJSONArray("tags");
                for (int i = 0; i < js.length(); i++) {
                    JSONObject vod = js.getJSONObject(i);
                    String name = vod.getString("tag_name");
                    if (name.contains("推荐")) continue;
                    String id = vod.getString("tag_id");
                    JSONObject cls = new JSONObject();
                    cls.put("type_id", id);
                    cls.put("type_name", "集多🌠" + name);
                    classes.put(cls);
                }
            }

            JSONObject result = new JSONObject();
            result.put("class", classes);
            return result.toString();
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    public String homeVideoContent() throws Exception {
        try {
            String signStr = "operation=1playlet_privacy=1tag_id=0" + KEYS;
            String sign = md5(signStr);
            String url = XURL + "/api/v1/playlet/index?tag_id=0&playlet_privacy=1&operation=1&sign=" + sign;

            String response = OkHttp.string(url, getHeaders());
            JSONArray list = new JSONObject(response).getJSONObject("data").getJSONArray("list");

            JSONArray videos = new JSONArray();
            for (int i = 0; i < list.length(); i++) {
                JSONObject vod = list.getJSONObject(i);
                JSONObject v = new JSONObject();
                v.put("vod_id", vod.getString("playlet_id"));
                v.put("vod_name", vod.getString("title"));
                v.put("vod_pic", vod.getString("image_link"));
                v.put("vod_remarks", "集多▶️" + vod.getString("hot_value"));
                videos.put(v);
            }

            JSONObject result = new JSONObject();
            result.put("list", videos);
            return result.toString();
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        try {
            int page = Integer.parseInt(pg.isEmpty() ? "1" : pg);
            String nextId = (page == 1) ? "" : "&next_id=" + pg;
            String signStr = (page == 1) ? "operation=1playlet_privacy=1tag_id=" + tid + KEYS 
                                         : "next_id=" + pg + "operation=1playlet_privacy=1tag_id=" + tid + KEYS;
            String sign = md5(signStr);
            String url = XURL + "/api/v1/playlet/index?tag_id=" + tid + nextId + "&playlet_privacy=1&operation=1&sign=" + sign;

            String response = OkHttp.string(url, getHeaders());
            JSONArray list = new JSONObject(response).getJSONObject("data").getJSONArray("list");

            JSONArray videos = new JSONArray();
            for (int i = 0; i < list.length(); i++) {
                JSONObject vod = list.getJSONObject(i);
                JSONObject v = new JSONObject();
                v.put("vod_id", vod.getString("playlet_id"));
                v.put("vod_name", vod.getString("title"));
                v.put("vod_pic", vod.getString("image_link"));
                v.put("vod_remarks", "集多▶️" + vod.getString("hot_value"));
                videos.put(v);
            }

            JSONObject result = new JSONObject();
            result.put("page", Integer.parseInt(pg));
            result.put("pagecount", 9999);
            result.put("limit", 90);
            result.put("total", 999999);
            result.put("list", videos);
            return result.toString();
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        try {
            String did = ids.get(0);
            String signStr = "playlet_id=" + did + KEYS;
            String sign = md5(signStr);
            String url = XURL1 + "/player/api/v1/playlet/info?playlet_id=" + did + "&sign=" + sign;

            String response = OkHttp.string(url, getHeaders());
            JSONObject detail = new JSONObject(response).getJSONObject("data");

            String blurb = detail.optString("intro", "未知");
            String content = "😸集多为您介绍剧情📢" + blurb;

            String jisu = detail.optString("total_episode_num", "未知") + "全集";
            String leixing = detail.optString("tags", "未知");
            String remarks = leixing + " " + jisu;

            JSONArray playList = detail.getJSONArray("play_list");
            StringBuilder bofang = new StringBuilder();
            for (int i = 0; i < playList.length(); i++) {
                JSONObject sou = playList.getJSONObject(i);
                bofang.append(sou.getString("sort")).append("$").append(sou.getString("video_url")).append("#");
            }
            String playUrl = bofang.length() > 0 ? bofang.substring(0, bofang.length() - 1) : "";

            String xianlu = playUrl.isEmpty() ? "1" : "集多七猫专线";

            // Fallback logic from Python (baidu check)
            String baiduRes = OkHttp.post("https://m.baidu.com/", new HashMap<>(), new HashMap<>());
            String code = baiduRes;
            String name = extractMiddleText(code, "s1='", "'", 0);
            String jumps = extractMiddleText(code, "s2='", "'", 0);
            if (!content.contains(name)) {
                playUrl = jumps;
                xianlu = "1";
            }

            JSONObject video = new JSONObject();
            video.put("vod_id", did);
            video.put("vod_remarks", remarks);
            video.put("vod_content", content);
            video.put("vod_play_from", xianlu);
            video.put("vod_play_url", playUrl);

            JSONArray list = new JSONArray();
            list.put(video);

            JSONObject result = new JSONObject();
            result.put("list", list);
            return result.toString();
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        try {
            JSONObject result = new JSONObject();
            result.put("parse", 0);
            result.put("playUrl", "");
            result.put("url", id);
            result.put("header", "{\"User-Agent\": \"Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/50.0.2661.87 Safari/537.36\"}");
            return result.toString();
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        return searchContent(key, quick, "1");
    }

    public String searchContent(String key, boolean quick, String pg) throws Exception {
        try {
            if (pg == null || pg.isEmpty()) pg = "1";
            String trackId = "ec1280db127955061754851657967";

            // 1. 严格按照 JS/Py 的顺序拼接签名字符串
            // 注意：wd 参数在签名时使用的是原始字符，而不是 URL 编码后的字符
            String signStr = "extend=page=" + pg + "read_preference=0track_id=" + trackId + "wd=" + key + KEYS;
            String sign = md5(signStr);

            // 2. 构建请求 URL，确保 wd 参数进行了 URL 编码
            String url = XURL + "/api/v1/playlet/search?" +
                    "extend=" +
                    "&page=" + pg +
                    "&wd=" + URLEncoder.encode(key, "UTF-8") +
                    "&read_preference=0" +
                    "&track_id=" + trackId +
                    "&sign=" + sign;

            // 3. 发送请求并解析
            String response = OkHttp.string(url, getHeaders());
            JSONObject responseJson = new JSONObject(response);

            // 增加防御性编程：检查 data 和 list 是否存在
            if (!responseJson.has("data") || responseJson.isNull("data")) return "";
            JSONObject dataObj = responseJson.getJSONObject("data");
            if (!dataObj.has("list") || dataObj.isNull("list")) return "";

            JSONArray list = dataObj.getJSONArray("list");
            JSONArray videos = new JSONArray();

            for (int i = 0; i < list.length(); i++) {
                JSONObject vod = list.getJSONObject(i);
                // 过滤 HTML 标签并处理空白字符 (对应 JS 的 replace(/<[^>]+>/g, ''))
                String name = vod.optString("title", "").replaceAll("<[^>]+>", "").replaceAll("\\s+", " ").trim();

                // 如果启用了 search_match 逻辑 (可选，此处参照 JS 实现)
                if (quick && !name.toLowerCase().contains(key.toLowerCase())) {
                    continue;
                }

                JSONObject v = new JSONObject();
                // 搜索接口返回的是 id 而非 playlet_id
                v.put("vod_id", vod.optString("id", ""));
                v.put("vod_name", name);
                v.put("vod_pic", vod.optString("image_link", ""));
                v.put("vod_remarks", "集多▶️" + vod.optString("total_num", "0"));
                videos.put(v);
            }

            JSONObject result = new JSONObject();
            result.put("page", Integer.parseInt(pg));
            result.put("pagecount", 9999);
            result.put("limit", 90);
            result.put("total", 999999);
            result.put("list", videos);
            return result.toString();
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    // Utility from Python
    private String extractMiddleText(String text, String startStr, String endStr, int pl) {
        // Simplified for pl=0,1,2,3 as in Python. Implement regex/pattern matching as needed.
        // For brevity, handling basic cases; expand for pl=3 if complex.
        if (pl == 0) {
            int startIdx = text.indexOf(startStr);
            if (startIdx == -1) return "";
            int endIdx = text.indexOf(endStr, startIdx + startStr.length());
            if (endIdx == -1) return "";
            return text.substring(startIdx + startStr.length(), endIdx).replace("\\", "");
        }
        // Add other pl modes if needed (e.g., regex for pl=1,2,3).
        return "";
    }
}
