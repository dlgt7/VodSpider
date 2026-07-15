package com.github.catvod.spider;

import android.text.TextUtils;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.net.OkHttp;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class KuWo extends Spider {

    private static final String URL_PLAYER = "http://nmobi.kuwo.cn/mobi.s?f=web&user=0&source=kwplayerhd_ar_4.3.0.8_tianbao_T1A_qirui.apk&type=convert_url_with_sign&rid=";
    private static final String URL_PLAYER_SUFFIX = "&br=";
    private static final String URL_DETAIL = "http://nplserver.kuwo.cn/pl.svc?op=getlistinfo&pid=";
    private static final String URL_DETAIL_SUFFIX = "&pn=0&rn=200&encode=utf8&keyset=pl2012&identity=kuwo&pcmp4=1&vipver=MUSIC_9.1.1.2_BCS2&newver=1";
    private static final String URL_CATEGORY = "http://wapi.kuwo.cn/api/pc/classify/playlist/getRcmPlayList?loginUid=0&loginSid=0&appUid=76039576&rn=30&order=";
    private static final String URL_SEARCH = "https://search.kuwo.cn/r.s?client=kt&all=";
    private static final String SEARCH_SUFFIX = "&pn=0&rn=20&vipver=1&ft=music&encoding=utf8&rformat=json&mobi=1";

    private static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36";
    private static final String ACCEPT = "application/json, text/plain, */*";
    private static final String REFERER = "http://www.kuwo.cn/";

    private static final String DEFAULT_PIC = "https://p1.music.126.net/5KJI2mq0G0OQHQaAfAJfwg==/109951173289563385.jpg?param=300y300";
    private static final String DEFAULT_NAME = "酷我单曲";
    private static final String DEFAULT_REMARK = "酷我音乐";
    private static final String DEFAULT_PLAYLIST_NAME = "未命名歌单";
    private static final String PLAY_FROM = "酷我歌单";
    private static final String PLAY_URL_PREFIX = "酷我单曲$";
    private static final String CONTENT = "酷我音乐 · 单曲";

    private static final String FORMAT_320 = "320kmp3";
    private static final String FORMAT_128 = "128kmp3";

    private static final String SEP_ARTIST_OPEN = " [";
    private static final String SEP_ARTIST_CLOSE = "]";
    private static final String SEP_ID = "$";
    private static final String SEP_FIELD = "&&";
    private static final String SEP_EPISODE = "#";
    private static final String SEP_ARTIST = " - ";

    private static final String NUMERIC_REGEX = "\\d+";
    private static final String TRY_PREFIX = "try{";
    private static final String CATCH_SUFFIX = "}catch(e){}";

    private static final String CAT_HOT = "hot";
    private static final String CAT_NEW = "new";
    private static final String CAT_HOT_NAME = "热门歌单";
    private static final String CAT_NEW_NAME = "新歌推荐";

    private static final String COUNT_SUFFIX = "首";

    public static Vod createFallbackVod(String id) {
        Vod vod = new Vod(id, DEFAULT_NAME, DEFAULT_PIC, DEFAULT_REMARK);
        vod.setVodPlayFrom(PLAY_FROM);
        vod.setVodPlayUrl(PLAY_URL_PREFIX + id);
        vod.setVodContent(CONTENT);
        return vod;
    }

    public static String buildPlayItem(JSONObject item) {
        String id = item.optString("id");
        if (TextUtils.isEmpty(id)) {
            return "";
        }
        String name = firstNonEmpty(item.optString("name"), item.optString("SONGNAME"), item.optString("displaysongname"));
        String artist = firstNonEmpty(item.optString("artist"), item.optString("ARTIST"), item.optString("FARTIST"), item.optString("displayartistname"));
        if (!TextUtils.isEmpty(artist)) {
            name = name + SEP_ARTIST_OPEN + artist + SEP_ARTIST_CLOSE;
        }
        StringBuilder sb = new StringBuilder();
        sb.append(name);
        sb.append(SEP_ID);
        sb.append(id);
        sb.append(SEP_FIELD);
        sb.append(item.optString("albumpic"));
        sb.append(SEP_FIELD);
        sb.append(item.optString("artistPic"));
        return sb.toString();
    }

    public static String firstNonEmpty(String... values) {
        for (String value : values) {
            if (!TextUtils.isEmpty(value)) {
                return value;
            }
        }
        return "";
    }

    public final JSONObject fetchJson(String url) {
        try {
            return new JSONObject(OkHttp.string(url, buildHeaders()));
        } catch (Exception unused) {
            return new JSONObject();
        }
    }

    public final String getMusicUrl(String rid, String format) {
        JSONObject data = fetchJson(URL_PLAYER + rid + URL_PLAYER_SUFFIX + format).optJSONObject("data");
        return data != null ? data.optString("url") : "";
    }

    public final ArrayList<Vod> fetchCategoryList(int page, String order) {
        JSONObject json = fetchJson(URL_CATEGORY + order + "&pn=" + page + "&_=" + System.currentTimeMillis());
        String dataKey = "data";
        Object dataObj = json.opt(dataKey);
        JSONArray list;
        if (dataObj instanceof JSONObject) {
            list = ((JSONObject) dataObj).optJSONArray(dataKey);
        } else if (dataObj instanceof JSONArray) {
            list = (JSONArray) dataObj;
        } else {
            list = null;
        }
        ArrayList<Vod> result = new ArrayList<>();
        if (list != null) {
            for (int i = 0; i < list.length(); i++) {
                JSONObject item = list.optJSONObject(i);
                if (item != null) {
                    String id = firstNonEmpty(item.optString("id"), item.optString("pid"));
                    String name = firstNonEmpty(item.optString("name"), item.optString("title"), DEFAULT_PLAYLIST_NAME);
                    String pic = firstNonEmpty(item.optString("img"), item.optString("pic"), item.optString("cover"));
                    String remark = firstNonEmpty(item.optString("info"), item.optString("uname"), item.optString("userName"));
                    if (!TextUtils.isEmpty(id)) {
                        result.add(new Vod(id, name, pic, remark));
                    }
                }
            }
        }
        return result;
    }

    public final Map<String, String> buildHeaders() {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("User-Agent", UA);
        headers.put("Accept", ACCEPT);
        headers.put("Referer", REFERER);
        return headers;
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        ArrayList<Class> classes = new ArrayList<>();
        classes.add(new Class(CAT_HOT, CAT_HOT_NAME));
        classes.add(new Class(CAT_NEW, CAT_NEW_NAME));
        return Result.string(classes, new ArrayList<>());
    }

    @Override
    public String homeVideoContent() throws Exception {
        return categoryContent(CAT_HOT, "1", false, new HashMap<>());
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        int page = 1;
        try {
            page = Math.max(1, Integer.parseInt(pg));
        } catch (Exception unused) {
        }
        try {
            return Result.string(page, 999, 30, 999999, fetchCategoryList(page, tid));
        } catch (Exception e) {
            return Result.string(page, page, 30, 0, new ArrayList<>());
        }
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        if (ids == null || ids.isEmpty()) {
            return Result.string(new ArrayList<>());
        }
        String id = ids.get(0);
        try {
            JSONObject json = fetchJson(URL_DETAIL + id + URL_DETAIL_SUFFIX);
            JSONArray musicList = json.optJSONArray("musiclist");
            ArrayList<String> playItems = new ArrayList<>();
            if (musicList != null) {
                for (int i = 0; i < musicList.length(); i++) {
                    JSONObject item = musicList.optJSONObject(i);
                    if (item != null) {
                        String playItem = buildPlayItem(item);
                        if (!TextUtils.isEmpty(playItem)) {
                            playItems.add(playItem);
                        }
                    }
                }
            }
            if (playItems.isEmpty() && id.matches(NUMERIC_REGEX)) {
                return Result.string(createFallbackVod(id));
            }
            String name = firstNonEmpty(json.optString("name"), json.optString("title"), PLAY_FROM);
            String pic = firstNonEmpty(json.optString("pic"), json.optString("img"), DEFAULT_PIC);
            String content = firstNonEmpty(json.optString("info"), json.optString("desc"));
            Vod vod = new Vod(id, name, pic, playItems.size() + COUNT_SUFFIX);
            vod.setVodPlayFrom(PLAY_FROM);
            vod.setVodPlayUrl(TextUtils.join(SEP_EPISODE, playItems));
            vod.setVodContent(content);
            return Result.string(vod);
        } catch (Exception e) {
            return id.matches(NUMERIC_REGEX) ? Result.string(createFallbackVod(id)) : Result.string(new ArrayList<>());
        }
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        String emptyUrl = "";
        try {
            String rid;
            if (TextUtils.isEmpty(id)) {
                rid = emptyUrl;
            } else {
                if (id.contains(SEP_FIELD)) {
                    id = id.split(SEP_FIELD)[0];
                }
                if (id.contains(SEP_ID)) {
                    id = id.substring(id.lastIndexOf(SEP_ID) + 1);
                }
                rid = id.trim();
            }
            String url = getMusicUrl(rid, FORMAT_320);
            if (TextUtils.isEmpty(url)) {
                url = getMusicUrl(rid, FORMAT_128);
            }
            return Result.get().url(url).parse(0).header(buildHeaders()).string();
        } catch (Exception e) {
            return Result.get().url(emptyUrl).parse(0).header(buildHeaders()).string();
        }
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        return searchContent(key, quick, "1");
    }

    @Override
    public String searchContent(String key, boolean quick, String pg) throws Exception {
        if (TextUtils.isEmpty(key)) {
            return Result.string(new ArrayList<>());
        }
        try {
            String response = OkHttp.string(URL_SEARCH + URLEncoder.encode(key.trim(), "UTF-8") + SEARCH_SUFFIX, buildHeaders()).trim();
            if (response.startsWith(TRY_PREFIX)) {
                response = response.substring(4);
            }
            if (response.endsWith(CATCH_SUFFIX)) {
                response = response.substring(0, response.length() - 11);
            }
            JSONArray abslist = new JSONObject(response).optJSONArray("abslist");
            ArrayList<Vod> list = new ArrayList<>();
            if (abslist != null) {
                for (int i = 0; i < abslist.length(); i++) {
                    JSONObject item = abslist.optJSONObject(i);
                    if (item != null) {
                        String musicrid = item.optString("MUSICRID").replace("MUSIC_", "");
                        if (!TextUtils.isEmpty(musicrid)) {
                            String name = item.optString("NAME");
                            String artist = item.optString("ARTIST");
                            if (!TextUtils.isEmpty(artist)) {
                                name = name + SEP_ARTIST + artist;
                            }
                            list.add(new Vod(musicrid, name, item.optString("hts_MVPIC"), DEFAULT_REMARK));
                        }
                    }
                }
            }
            return Result.string(list);
        } catch (Exception e) {
            return Result.string(new ArrayList<>());
        }
    }
}
