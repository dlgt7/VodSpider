package com.github.catvod.spider;

import android.content.Context;
import android.net.Uri;
import android.text.TextUtils;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.net.OkHttp;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

public class HHkk extends Spider {

    private static final String TAGS_FEED_URL = "https://sv.baidu.com/haokan/ui-feed/playletTagsFeed?";
    private static final String API_URL = "https://sv.baidu.com/appui/api?";
    private static final String DETAIL_URL = "https://sv.baidu.com/haokan/ui-video/playlet/rec/detail?";
    private static final String SHELF_FEED_URL = "https://sv.baidu.com/haokan/ui-feed/playletShelfFeed?";
    private static final String SEARCH_URL = "https://sv.baidu.com/haokan/ui-interact/playlet/search/sugs?";

    private static final String INIT_UA = "Mozilla/5.0 (Linux; Android 11; M2012K10C Build/RP1A.200720.011; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/87.0.4280.141 Mobile Safari/537.36 haokan/7.80.0.18 (Baidu; P1 11)/imoaiX_03_11_C01K2102M/1043677m/5ACDB023CFB9D64743B08E51953F7C76%7CVSAJ32AVA/1/7.80.0.18/780001/1/immersiveMode/modeV4PlusWhite/isFirstInstall/bbqMode/bbqModeV2/blackStyle/isPlaylet Talos/1.8.7";
    private static final String PLAYER_UA = "Mozilla/5.0 (Linux; Android 4.4.2; Nexus 5 Build/KOT49H) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/30.0.0.0 Mobile Safari/537.36  dumedia/7.74.1.3";
    private static final String CONTENT_TYPE = "application/x-www-form-urlencoded";
    private static final String TALOS_MODULE_NAME = "shortDrama";
    private static final String TALOS_MODULE_VERSION = "1.0.71.1";
    private static final String COOKIE = "BAIDUCUID=giHCu0azv80G8SfQ0avU8gaaH8jfiv86ju2MugiR2i8-k3a35avAa1_mA";

    private static final String COMMONLIST_BODY = "enable_enter_playlet=0&seek_time=0&hotspot=0&auto_show_hot_point_panel=0&type=playlet&commonlist_id=%d&scene=&vid=&enable_atlas=0&mark_pn=&uk=&ctime=0&from=playlet_new&id=%s&rn=10&pn=1&direction=3";
    private static final String RELATE_PREFIX = "method=post&vid=";
    private static final String RELATE_MIDDLE = "&immersive_mode=v4_5&tplname=feed_small_video&tag=playlet_talos&tab=detail&external_from=&is_dp_video=0&immersive_square_type=3&video_set_id=";
    private static final String RELATE_SUFFIX = "&play_screen_type=1&play_volume_type=2&play_external_device_type=1";

    private static final int TIMEOUT_MS = 30000;
    private static final int PAGE_SIZE = 10;
    private static final int TOTAL_COUNT = 9999;

    public HashMap<String, String> headers;

    public HHkk() {
        this.headers = new HashMap<>();
    }

    public static HashMap<String, String> buildCommonParams() {
        HashMap<String, String> map = new HashMap<>();
        map.put("osbranch", "a0");
        return map;
    }

    public static String encode(HashMap<String, String> params) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (sb.length() > 0) {
                sb.append('&');
            }
            sb.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8));
            sb.append('=');
            sb.append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
        }
        return sb.toString();
    }

    public static ArrayList<Vod> parseVod(JSONArray array) {
        ArrayList<Vod> list = new ArrayList<>();
        if (array == null) {
            return list;
        }
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            String id = item.optString("id");
            id = item.optString("playlet_id", id);
            String title = item.optString("title");
            title = item.optString("playlet_title", title);
            String cover = item.optString("cover_url");
            cover = item.optString("playlet_poster", cover);
            String hot = item.optString("hot_value");
            String remark = item.optString("episodes_num_text", hot);
            if (!TextUtils.isEmpty(id) && !TextUtils.isEmpty(title)) {
                list.add(new Vod(id, title, cover, remark));
            }
        }
        return list;
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        HashMap<String, String> urlParams = buildCommonParams();
        HashMap<String, String> params = new HashMap<>();
        params.put("tag_id", tid);
        params.put("pn", pg);
        params.put("rn", "10");
        String url = new StringBuilder(TAGS_FEED_URL).append(encode(urlParams)).toString();
        JSONObject response = new JSONObject(OkHttp.post(url, params, headers));
        JSONObject data = response.optJSONObject("data");
        JSONArray array = null;
        if (data != null) {
            array = data.optJSONArray("list");
            if (array == null) {
                array = data.optJSONArray("video_list");
            }
        }
        ArrayList<Vod> list = parseVod(array);
        int page = Integer.parseInt(pg);
        return Result.get().vod(list).page(page, page + 1, PAGE_SIZE, TOTAL_COUNT).string();
    }

    public final String fetch(String path, String body) {
        try {
            String url = new StringBuilder(API_URL).append(encode(buildCommonParams())).toString();
            String requestBody = new StringBuilder().append(path).append(Uri.encode(body)).toString();
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                conn.setRequestProperty(entry.getKey(), entry.getValue());
            }
            byte[] bytes = requestBody.getBytes(StandardCharsets.UTF_8);
            OutputStream os = conn.getOutputStream();
            os.write(bytes);
            int code = conn.getResponseCode();
            java.io.InputStream is;
            if (code >= 400) {
                is = conn.getErrorStream();
            } else {
                is = conn.getInputStream();
            }
            if (is == null) {
                return "";
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            reader.close();
            conn.disconnect();
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        String vodId = ids.get(0);
        Object[] args = new Object[]{System.currentTimeMillis(), vodId};
        String commonlistBody = String.format(COMMONLIST_BODY, args);
        JSONObject commonlistResp = new JSONObject(fetch("video/commonlist=", commonlistBody));
        JSONObject commonlist = commonlistResp.optJSONObject("video/commonlist");
        if (commonlist == null) {
            commonlist = commonlistResp.optJSONObject("video\\/commonlist");
        }
        JSONObject data = commonlist != null ? commonlist.optJSONObject("data") : commonlistResp.optJSONObject("data");
        String vid = "";
        if (data != null) {
            JSONArray results = data.optJSONArray("results");
            if (results != null && results.length() != 0) {
                for (int i = 0; i < results.length(); i++) {
                    JSONObject result = results.optJSONObject(i);
                    if (result == null) continue;
                    JSONObject content = result.optJSONObject("content");
                    if (content == null) continue;
                    String candidate = content.optString("vid");
                    if (!TextUtils.isEmpty(candidate)) {
                        vid = candidate;
                        break;
                    }
                }
            }
        }
        HashMap<String, String> params = new HashMap<>();
        params.put("playlet_id", vodId);
        if (!TextUtils.isEmpty(vid)) {
            params.put("vid", vid);
        }
        String detailUrl = new StringBuilder(DETAIL_URL).append(encode(buildCommonParams())).toString();
        JSONObject detailResp = new JSONObject(OkHttp.post(detailUrl, params, headers));
        JSONObject detailData = detailResp.optJSONObject("data");
        String title = null;
        if (data != null) {
            JSONArray results = data.optJSONArray("results");
            if (results != null && results.length() != 0) {
                JSONObject result = results.optJSONObject(0);
                if (result != null) {
                    JSONObject content = result.optJSONObject("content");
                    if (content != null) {
                        title = content.optString("title");
                        int dashIdx = title.lastIndexOf("-第");
                        if (dashIdx > 0) {
                            title = title.substring(0, dashIdx);
                        }
                    }
                }
            }
        }
        String cover = "";
        String remark = "";
        String description = "";
        if (detailData != null) {
            title = detailData.optString("playlet_title", title);
            cover = detailData.optString("playlet_poster");
            remark = detailData.optString("hot_value");
            description = detailData.optString("description");
        }
        if (TextUtils.isEmpty(title)) {
            title = vodId;
        }
        Vod vod = new Vod(vodId, title, cover);
        vod.setVodRemarks(remark);
        vod.setVodContent(description);
        ArrayList<String> playList = new ArrayList<>();
        JSONArray vidList = detailData != null ? detailData.optJSONArray("vid_list") : null;
        if (vidList != null && vidList.length() > 0) {
            for (int i = 0; i < vidList.length(); i++) {
                Object obj = vidList.opt(i);
                String itemVid;
                if (obj instanceof String) {
                    itemVid = (String) obj;
                } else {
                    JSONObject vidObj = vidList.optJSONObject(i);
                    itemVid = vidObj.optString("vid");
                }
                if (TextUtils.isEmpty(itemVid)) continue;
                playList.add(new StringBuilder("第").append(i + 1).append("集$").append(vodId).append("@").append(itemVid).toString());
            }
        }
        if (playList.isEmpty() && data != null) {
            JSONArray results = data.optJSONArray("results");
            if (results != null) {
                ArrayList<String> fallback = new ArrayList<>();
                for (int i = 0; i < results.length(); i++) {
                    JSONObject result = results.optJSONObject(i);
                    if (result == null) continue;
                    JSONObject content = result.optJSONObject("content");
                    if (content == null) continue;
                    String itemVid = content.optString("vid");
                    if (TextUtils.isEmpty(itemVid)) continue;
                    fallback.add(new StringBuilder("第").append(i + 1).append("集$").append(vodId).append("@").append(itemVid).toString());
                }
                playList.addAll(fallback);
            }
        }
        vod.setVodPlayFrom("默认");
        vod.setVodPlayUrl(TextUtils.join("#", playList));
        return Result.string(vod);
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        HashMap<String, String> urlParams = buildCommonParams();
        HashMap<String, String> params = new HashMap<>();
        params.put("from", "feed");
        params.put("osbranch", "a0");
        String url = new StringBuilder(SHELF_FEED_URL).append(encode(urlParams)).toString();
        JSONObject response = new JSONObject(OkHttp.post(url, params, headers));
        JSONObject data = response.optJSONObject("data");
        ArrayList<Class> classes = new ArrayList<>();
        ArrayList<Vod> list = new ArrayList<>();
        LinkedHashSet<String> seenTagIds = new LinkedHashSet<>();
        if (data != null) {
            JSONArray tags = data.optJSONArray("playlet_tags");
            if (tags != null) {
                for (int i = 0; i < tags.length(); i++) {
                    JSONObject tag = tags.getJSONObject(i);
                    String tagId = String.valueOf(tag.optInt("tag_id"));
                    if (seenTagIds.add(tagId)) {
                        classes.add(new Class(tagId, tag.optString("name")));
                    }
                }
            }
            JSONArray filterPanels = data.optJSONArray("playlet_shelf_filter_panel");
            if (filterPanels != null) {
                for (int i = 0; i < filterPanels.length(); i++) {
                    JSONObject panel = filterPanels.optJSONObject(i);
                    if (panel == null) continue;
                    JSONArray tagList = panel.optJSONArray("tag_list");
                    if (tagList == null) continue;
                    for (int j = 0; j < tagList.length(); j++) {
                        JSONObject tag = tagList.getJSONObject(j);
                        String tagId = String.valueOf(tag.optInt("tag_id"));
                        if (seenTagIds.add(tagId)) {
                            classes.add(new Class(tagId, tag.optString("name")));
                        }
                    }
                }
            }
            JSONArray banner = data.optJSONArray("playlet_banner");
            if (banner != null) {
                for (int i = 0; i < banner.length(); i++) {
                    JSONObject item = banner.optJSONObject(i);
                    if (item == null) continue;
                    JSONArray videoList = item.optJSONArray("video_list");
                    ArrayList<Vod> vods;
                    if (videoList != null) {
                        vods = parseVod(videoList);
                    } else {
                        String playletId = item.optString("playlet_id", item.optString("id"));
                        if (TextUtils.isEmpty(playletId)) continue;
                        JSONArray single = new JSONArray();
                        single.put(item);
                        vods = parseVod(single);
                    }
                    list.addAll(vods);
                }
            }
        }
        return Result.string(classes, list);
    }

    @Override
    public void init(Context context, String config) throws Exception {
        super.init(context, config);
        headers = new HashMap<>();
        headers.put("User-Agent", INIT_UA);
        headers.put("Content-Type", CONTENT_TYPE);
        headers.put("Talos-Module-Name", TALOS_MODULE_NAME);
        headers.put("Talos-Module-Version", TALOS_MODULE_VERSION);
        headers.put("Cookie", COOKIE);
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        int atIdx = id.indexOf('@');
        String urlResult = "";
        if (atIdx <= 0 || atIdx >= id.length() - 1) {
            return Result.get().url(urlResult).string();
        }
        String vodId = id.substring(0, atIdx);
        String vid = id.substring(atIdx + 1);
        String body = new StringBuilder(RELATE_PREFIX).append(vid).append(RELATE_MIDDLE).append(vodId).append(RELATE_SUFFIX).toString();
        JSONObject response = new JSONObject(fetch("video/relate=", body));
        JSONObject relate = response.optJSONObject("video/relate");
        if (relate == null) {
            relate = response.optJSONObject("video\\/relate");
        }
        JSONObject root = relate != null ? relate : response;
        JSONObject data = root.optJSONObject("data");
        JSONObject curVideo = data != null ? data.optJSONObject("cur_video") : null;
        if (curVideo != null) {
            JSONArray clarityUrl = curVideo.optJSONArray("clarityUrl");
            if (clarityUrl != null && clarityUrl.length() > 0) {
                if (TextUtils.isEmpty(flag)) {
                    JSONObject last = clarityUrl.optJSONObject(clarityUrl.length() - 1);
                    if (last != null) {
                        String lastUrl = last.optString("url");
                        if (!TextUtils.isEmpty(lastUrl)) {
                            urlResult = lastUrl;
                        }
                    }
                } else {
                    for (int i = 0; i < clarityUrl.length(); i++) {
                        JSONObject item = clarityUrl.optJSONObject(i);
                        if (item == null) continue;
                        String title = item.optString("title");
                        String key = item.optString("key", title);
                        String itemUrl = item.optString("url");
                        if (TextUtils.isEmpty(itemUrl)) continue;
                        if (TextUtils.isEmpty(key)) continue;
                        if (key.contains(flag) || flag.contains(key)) {
                            urlResult = itemUrl;
                            break;
                        }
                    }
                    if (TextUtils.isEmpty(urlResult)) {
                        JSONObject last = clarityUrl.optJSONObject(clarityUrl.length() - 1);
                        if (last != null) {
                            String lastUrl = last.optString("url");
                            if (!TextUtils.isEmpty(lastUrl)) {
                                urlResult = lastUrl;
                            }
                        }
                    }
                }
            }
            if (TextUtils.isEmpty(urlResult)) {
                urlResult = curVideo.optString("url");
            }
        }
        HashMap<String, String> playerHeaders = new HashMap<>();
        playerHeaders.put("User-Agent", PLAYER_UA);
        return Result.get().url(urlResult).header(playerHeaders).string();
    }

    @Override
    public String searchContent(String key, boolean quick, String pg) throws Exception {
        HashMap<String, String> urlParams = buildCommonParams();
        urlParams.put("search_word", key);
        HashMap<String, String> params = new HashMap<>();
        params.put("osbranch", "a0");
        params.put("search_word", key);
        String url = new StringBuilder(SEARCH_URL).append(encode(urlParams)).toString();
        JSONObject response = new JSONObject(OkHttp.post(url, params, headers));
        JSONObject data = response.optJSONObject("data");
        ArrayList<Vod> list = new ArrayList<>();
        if (data != null) {
            JSONArray array = data.optJSONArray("list");
            if (array != null) {
                list.addAll(parseVod(array));
            }
        }
        return Result.string(list);
    }
}
