package com.github.catvod.spider;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.net.OkHttp;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Qimao extends Spider {

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

            List<Class> classes = new ArrayList<>();
            int[] duoxuan = {0, 1, 2, 3, 4};
            for (int duo : duoxuan) {
                JSONArray js = data.getJSONArray("tag_categories").getJSONObject(duo).getJSONArray("tags");
                for (int i = 0; i < js.length(); i++) {
                    JSONObject vod = js.getJSONObject(i);
                    String name = vod.getString("tag_name");
                    if (name.contains("推荐")) continue;
                    String id = vod.getString("tag_id");
                    classes.add(new Class(id, "集多" + name));
                }
            }

            return Result.string(classes, new ArrayList<>());
        } catch (Exception e) {
            return Result.error("获取分类失败").toString();
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

            List<Vod> videos = new ArrayList<>();
            for (int i = 0; i < list.length(); i++) {
                JSONObject vod = list.getJSONObject(i);
                videos.add(new Vod(
                    vod.getString("playlet_id"),
                    vod.getString("title"),
                    vod.getString("image_link"),
                    "集多" + vod.getString("hot_value")
                ));
            }

            return Result.string(new ArrayList<>(), videos);
        } catch (Exception e) {
            return Result.error("获取推荐失败").toString();
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

            List<Vod> videos = new ArrayList<>();
            for (int i = 0; i < list.length(); i++) {
                JSONObject vod = list.getJSONObject(i);
                videos.add(new Vod(
                    vod.getString("playlet_id"),
                    vod.getString("title"),
                    vod.getString("image_link"),
                    "集多" + vod.getString("hot_value")
                ));
            }

            return Result.get().page(page, 9999, 90, 999999).vod(videos).string();
        } catch (Exception e) {
            return Result.error("获取列表失败").toString();
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
            String content = "集多为您介绍剧情：" + blurb;

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

            String baiduRes = OkHttp.post("https://m.baidu.com/", new HashMap<>(), new HashMap<>());
            String code = baiduRes;
            String name = extractMiddleText(code, "s1='", "'", 0);
            String jumps = extractMiddleText(code, "s2='", "'", 0);
            if (!content.contains(name)) {
                playUrl = jumps;
                xianlu = "1";
            }

            Vod video = new Vod();
            video.setVodId(did);
            video.setVodRemarks(remarks);
            video.setVodContent(content);
            video.setVodPlayFrom(xianlu);
            video.setVodPlayUrl(playUrl);

            return Result.string(video);
        } catch (Exception e) {
            return Result.error("获取详情失败").toString();
        }
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        try {
            return Result.get().url(id).parse(0).header(getHeaders()).string();
        } catch (Exception e) {
            return Result.error("解析失败").toString();
        }
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        return searchContent(key, quick, "1");
    }

    @Override
    public String searchContent(String key, boolean quick, String pg) throws Exception {
        try {
            if (pg == null || pg.isEmpty()) pg = "1";
            String trackId = "ec1280db127955061754851657967";

            String signStr = "extend=page=" + pg + "read_preference=0track_id=" + trackId + "wd=" + key + KEYS;
            String sign = md5(signStr);

            String url = XURL + "/api/v1/playlet/search?" +
                    "extend=" +
                    "&page=" + pg +
                    "&wd=" + URLEncoder.encode(key, "UTF-8") +
                    "&read_preference=0" +
                    "&track_id=" + trackId +
                    "&sign=" + sign;

            String response = OkHttp.string(url, getHeaders());
            JSONObject responseJson = new JSONObject(response);

            if (!responseJson.has("data") || responseJson.isNull("data")) return Result.error("搜索失败").toString();
            JSONObject dataObj = responseJson.getJSONObject("data");
            if (!dataObj.has("list") || dataObj.isNull("list")) return Result.error("无结果").toString();

            JSONArray list = dataObj.getJSONArray("list");
            List<Vod> videos = new ArrayList<>();

            for (int i = 0; i < list.length(); i++) {
                JSONObject vod = list.getJSONObject(i);
                String name = vod.optString("title", "").replaceAll("<[^>]+>", "").replaceAll("\\s+", " ").trim();

                if (quick && !name.toLowerCase().contains(key.toLowerCase())) {
                    continue;
                }

                videos.add(new Vod(
                    vod.optString("id", ""),
                    name,
                    vod.optString("image_link", ""),
                    "集多" + vod.optString("total_num", "0")
                ));
            }

            return Result.get().page(Integer.parseInt(pg), 9999, 90, 999999).vod(videos).string();
        } catch (Exception e) {
            return Result.error("搜索异常").toString();
        }
    }

    private String extractMiddleText(String text, String startStr, String endStr, int pl) {
        if (pl == 0) {
            int startIdx = text.indexOf(startStr);
            if (startIdx == -1) return "";
            int endIdx = text.indexOf(endStr, startIdx + startStr.length());
            if (endIdx == -1) return "";
            return text.substring(startIdx + startStr.length(), endIdx).replace("\\", "");
        }
        return "";
    }
}
