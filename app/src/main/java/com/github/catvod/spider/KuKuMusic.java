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
import java.util.regex.Pattern;

public class KuKuMusic extends Spider {

    private static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36";
    private static final String REFERER = "http://www.kuwo.cn/";
    private static final String MUSIC_UA = "Mozilla/5.0 (Linux; Android 10)";
    private static final String MUSIC_REFERER = "https://www.kuwo.cn/";
    private static final String PLAY_FROM = "酷酷";
    private static final String CATEGORY_URL = "http://wapi.kuwo.cn/api/www/artist/artistInfo?category=";
    private static final String DETAIL_URL = "http://wapi.kuwo.cn/api/www/artist/artist?artistid=";
    private static final String MUSIC_LIST_URL = "http://wapi.kuwo.cn/api/www/artist/artistMusic?artistid=";
    private static final String SEARCH_URL = "https://search.kuwo.cn/r.s?client=kt&pn=";
    private static final String MUSIC_URL = "https://nmobi.kuwo.cn/mobi.s?f=web&user=0&source=kwplayer_ar_4.4.2.7_B_nuoweida_vh.apk&type=convert_url_with_sign&rid=";
    private static final String DEFAULT_PIC = "http://img1.kuwo.cn/star/starheads/";

    private static final Pattern TAG_PATTERN = Pattern.compile("<[^>]+>");
    private static final Pattern CLEAN_PATTERN = Pattern.compile("[$#]");

    private Map<String, String> buildHeader() {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("User-Agent", UA);
        headers.put("Referer", REFERER);
        return headers;
    }

    private Map<String, String> buildMusicHeader() {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("User-Agent", MUSIC_UA);
        headers.put("Referer", MUSIC_REFERER);
        return headers;
    }

    private JSONObject fetch(String url) {
        try {
            return new JSONObject(OkHttp.string(url, buildHeader()));
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    private String fixPic(String pic) {
        if (TextUtils.isEmpty(pic)) return "";
        if (pic.startsWith("//")) {
            pic = "https:" + pic;
        }
        if (!pic.startsWith("http://")) {
            return pic;
        }
        return "https://" + pic.substring(7);
    }

    private String getMusicUrl(int bitrate, String rid) {
        try {
            String url = MUSIC_URL + rid + "&bitrate=" + bitrate + "&format=mp3";
            JSONObject resp = new JSONObject(OkHttp.string(url, buildMusicHeader()));
            if (resp.optInt("code", 0) != 200) return "";
            JSONObject data = resp.optJSONObject("data");
            if (data == null) return "";
            String musicUrl = data.optString("url");
            return musicUrl.startsWith("http") ? musicUrl : "";
        } catch (Exception e) {
            return "";
        }
    }

    private JSONArray fetchMusicList(String artistId) throws Exception {
        JSONArray result = new JSONArray();
        for (int page = 1; page <= 10; page++) {
            String url = MUSIC_LIST_URL + artistId + "&pn=" + page + "&rn=30";
            JSONObject resp = fetch(url);
            if (resp.optInt("code", 0) != 200) break;
            JSONObject data = resp.optJSONObject("data");
            JSONArray list = data != null ? data.optJSONArray("list") : null;
            if (list == null || list.length() == 0) break;
            for (int i = 0; i < list.length(); i++) {
                JSONObject item = list.getJSONObject(i);
                if (!TextUtils.isEmpty(item.optString("name", "").trim())) {
                    result.put(item);
                    if (result.length() >= 300) return result;
                }
            }
        }
        return result;
    }

    private String buildPlayUrl(JSONArray musicList) {
        ArrayList<String> episodes = new ArrayList<>();
        int limit = Math.min(musicList.length(), 300);
        for (int i = 0; i < limit; i++) {
            JSONObject item = musicList.optJSONObject(i);
            if (item == null) continue;
            String name = item.optString("name");
            String cleanedName = "";
            if (!TextUtils.isEmpty(name)) {
                cleanedName = CLEAN_PATTERN.matcher(name).replaceAll("").trim();
            }
            String rid = item.optString("rid");
            if (TextUtils.isEmpty(cleanedName) || TextUtils.isEmpty(rid)) continue;
            String album = item.optString("album");
            if (TextUtils.isEmpty(album)) {
                episodes.add(cleanedName + "$" + rid);
            } else {
                episodes.add(cleanedName + " - " + album + "$" + rid);
            }
        }
        return TextUtils.join("#", episodes);
    }

    private String buildDetail(String id, String name, String pic, String remark,
                               String actor, String content, String playUrl) {
        Vod vod = new Vod(id, name, pic, remark);
        if (!TextUtils.isEmpty(actor)) vod.setVodActor(actor);
        if (!TextUtils.isEmpty(content)) vod.setVodContent(content);
        vod.setVodPlayFrom(PLAY_FROM);
        vod.setVodPlayUrl(playUrl);
        return Result.string(vod);
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        ArrayList<Class> classes = new ArrayList<>();
        classes.add(new Class("1", "华语男"));
        classes.add(new Class("2", "华语女"));
        classes.add(new Class("3", "华语组合"));
        classes.add(new Class("4", "日韩男"));
        classes.add(new Class("5", "日韩女"));
        classes.add(new Class("6", "日韩组合"));
        classes.add(new Class("7", "欧美男"));
        classes.add(new Class("8", "欧美女"));
        classes.add(new Class("9", "欧美组合"));
        classes.add(new Class("0", "其他"));
        return Result.string(classes, new ArrayList<>());
    }

    @Override
    public String homeVideoContent() throws Exception {
        return categoryContent("1", "1", false, new HashMap<>());
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        int page;
        try {
            page = Math.max(1, Integer.parseInt(pg));
        } catch (Exception e) {
            page = 1;
        }
        JSONObject data = fetch(CATEGORY_URL + tid + "&prefix=&pn=" + page + "&rn=30").optJSONObject("data");
        JSONArray artistList = data != null ? data.optJSONArray("artistList") : null;
        ArrayList<Vod> list = new ArrayList<>();
        if (artistList != null) {
            for (int i = 0; i < artistList.length(); i++) {
                JSONObject item = artistList.getJSONObject(i);
                String id = item.optString("id");
                String name = item.optString("name");
                if (TextUtils.isEmpty(id) || TextUtils.isEmpty(name)) continue;
                String pic = item.optString("pic300");
                if (TextUtils.isEmpty(pic)) pic = item.optString("pic");
                if (TextUtils.isEmpty(pic)) pic = item.optString("pic120");
                Vod vod = new Vod(id, name, fixPic(pic), "");
                vod.setStyle(Vod.Style.oval());
                list.add(vod);
            }
        }
        return Result.get().vod(list).page(page, 9999, 90, 999999).string();
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        if (ids == null || ids.isEmpty()) return Result.string(new ArrayList<>());
        String id = ids.get(0);
        try {
            JSONObject data = fetch(DETAIL_URL + id).optJSONObject("data");
            if (data == null) {
                return buildDetail(id, "加载失败", "", "加载失败", "未知", "加载歌手信息失败", "");
            }
            String name = data.optString("name");
            String pic = data.optString("pic300");
            if (TextUtils.isEmpty(pic)) pic = data.optString("pic");
            pic = fixPic(pic);
            String info = data.optString("info");
            String content = "";
            if (!TextUtils.isEmpty(info)) {
                content = TAG_PATTERN.matcher(info).replaceAll("").replace("&nbsp;", " ");
                content = content.replace("\r\n", "\n").replace("\r", "\n").trim();
            }
            if (TextUtils.isEmpty(content)) content = "暂无歌手简介";
            JSONArray musicList = fetchMusicList(id);
            String remark = "歌曲 :   " + Math.min(musicList.length(), 300) + "首";
            String playUrl = buildPlayUrl(musicList);
            return buildDetail(id, name, pic, remark, name, content, playUrl);
        } catch (Exception e) {
            return buildDetail(id, "加载失败", "", "加载失败", "未知", "加载歌手信息失败: " + e.getMessage(), "");
        }
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        String rid = id;
        if (rid != null && rid.contains("$")) {
            String[] parts = rid.split("\\$", 2);
            rid = parts[parts.length - 1];
        }
        String musicUrl = getMusicUrl(320, rid);
        if (TextUtils.isEmpty(musicUrl)) {
            musicUrl = getMusicUrl(128, rid);
        }
        return Result.get().url(musicUrl).parse(0).header(buildMusicHeader()).string();
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        return searchContent(key, quick, "1");
    }

    @Override
    public String searchContent(String key, boolean quick, String pg) throws Exception {
        if (TextUtils.isEmpty(key)) return Result.string(new ArrayList<>());
        int page;
        try {
            page = Math.max(1, Integer.parseInt(pg));
        } catch (Exception e) {
            page = 1;
        }
        StringBuilder sb = new StringBuilder();
        sb.append(SEARCH_URL).append((page - 1) * 30).append("&rn=30&all=");
        sb.append(URLEncoder.encode(key.trim(), "UTF-8"));
        sb.append("&vipver=1&ft=artist&encoding=utf8&rformat=json&mobi=1");
        ArrayList<Vod> list = new ArrayList<>();
        try {
            JSONObject resp = fetch(sb.toString());
            JSONArray abslist = resp.optJSONArray("abslist");
            if (abslist != null) {
                String basePic = resp.optString("BASEPICPATH", DEFAULT_PIC);
                for (int i = 0; i < abslist.length(); i++) {
                    JSONObject item = abslist.getJSONObject(i);
                    String artistId = item.optString("ARTISTID");
                    if (TextUtils.isEmpty(artistId)) artistId = item.optString("DC_TARGETID");
                    if (TextUtils.isEmpty(artistId)) continue;
                    String pic = item.optString("hts_PICPATH");
                    if (TextUtils.isEmpty(pic) && !TextUtils.isEmpty(item.optString("PICPATH"))) {
                        pic = basePic + item.optString("PICPATH");
                    }
                    String songNum = item.optString("SONGNUM", "0");
                    Vod vod = new Vod(artistId, item.optString("ARTIST"), fixPic(pic), "歌曲 :  " + songNum + "首");
                    vod.setStyle(Vod.Style.oval());
                    list.add(vod);
                }
            }
        } catch (Exception e) {
        }
        return Result.get().vod(list).page(page, 9999, 30, 999999).string();
    }
}
