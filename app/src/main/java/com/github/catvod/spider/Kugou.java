package com.github.catvod.spider;

import android.text.TextUtils;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.net.OkHttp;

import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Kugou extends Spider {

    private static final String DEFAULT_PIC = "https://p1.music.126.net/5KJI2mq0G0OQHQaAfAJfwg==/109951173289563385.jpg?param=300y300";
    private static final String KUGOU_UA = "KuGou2012-9108-Expand133ManagerReview-117000-Android2010-9108-AddPlatinumDeviceExpand1-0";
    private static final String CHROME_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36";
    private static final String RANK_LIST_URL = "http://mobilecdnbj.kugou.com/api/v3/rank/list?version=9108&plat=0&showtype=2&parentid=0&apiver=6&area_code=1&withsong=0&with_res_tag=0";
    private static final String RANK_SONG_URL = "http://mobilecdnbj.kugou.com/api/v3/rank/song?version=9108&ranktype=0&plat=0&pagesize=200&area_code=1&page=1&volid=35050&rankid=%s&with_res_tag=0";
    private static final String SONG_INFO_URL = "https://m.kugou.com/app/i/getSongInfo.php?cmd=playInfo&hash=%s";
    private static final String MV_URL = "https://m.kugou.com/app/i/mv.php?cmd=100&hash=%s&ismp3=1&ext=mp4";
    private static final String SEARCH_URL = "http://mobilecdn.kugou.com/api/v3/search/song?format=json&keyword=%s&page=%s&pagesize=30&showtype=1";
    private static final String SQ0527_SEARCH = "https://www.sq0527.cn/search?ac=";
    private static final String SQ0527_BASE = "https://www.sq0527.cn";
    private static final String SQ0527_REFERER = "https://www.sq0527.cn/";
    private static final String GEQUBAO_SEARCH = "https://www.gequbao.com/s/";
    private static final String GEQUBAO_BASE = "https://www.gequbao.com";
    private static final String GEQUBAO_REFERER = "https://www.gequbao.com/";
    private static final String GEQUBAO_PLAY_URL = "https://www.gequbao.com/member/common-play-url";
    private static final String UTF_8 = "UTF-8";

    private static final Pattern PLAY_ID_PATTERN = Pattern.compile("play_id\\\\u0022:\\\\u0022([^\\\\]+)");

    public static String b(String pic) {
        if (TextUtils.isEmpty(pic)) return DEFAULT_PIC;
        return pic.replace("{size}", "400");
    }

    public static String c(JSONObject item) {
        String hash = item.optString("sqhash");
        if (TextUtils.isEmpty(hash)) hash = item.optString("hash");
        if (TextUtils.isEmpty(hash)) return "";
        return new StringBuilder("kugou-mp3_").append(hash).append("_").append(item.optString("album_id")).append("_").append(item.optString("album_audio_id")).toString();
    }

    public static String d(JSONObject item) {
        String cover = item.optString("album_sizable_cover");
        if (TextUtils.isEmpty(cover)) cover = item.optString("imgurl");
        if (TextUtils.isEmpty(cover)) {
            JSONObject transParam = item.optJSONObject("trans_param");
            if (transParam != null) cover = transParam.optString("union_cover");
        }
        return b(cover);
    }

    public static String e(JSONObject item) {
        String name = item.optString("filename");
        if (TextUtils.isEmpty(name)) name = item.optString("songname");
        if (TextUtils.isEmpty(name)) {
            name = new StringBuilder().append(item.optString("singername")).append(" - ").append(item.optString("songname")).toString();
        }
        return name.trim();
    }

    public final JSONObject a(String url) {
        try {
            HashMap<String, String> headers = new HashMap<>();
            headers.put("User-Agent", KUGOU_UA);
            return new JSONObject(OkHttp.string(url, null, headers));
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    public final String f(String url, String referer) {
        try {
            return OkHttp.string(url, null, g(referer));
        } catch (Exception e) {
            return "";
        }
    }

    public final Map<String, String> g(String referer) {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("User-Agent", CHROME_UA);
        if (!TextUtils.isEmpty(referer)) {
            headers.put("Referer", referer);
        }
        return headers;
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        ArrayList<Class> classes = new ArrayList<>();
        classes.add(new Class("hot", "热门榜"));
        classes.add(new Class("special", "特色榜"));
        classes.add(new Class("global", "全球榜"));
        return Result.string(classes, new ArrayList<>());
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        if (!"1".equals(pg)) return Result.string(new ArrayList<>());
        ArrayList<Vod> list = new ArrayList<>();
        JSONObject resp = a(RANK_LIST_URL);
        JSONArray info = null;
        JSONObject data = resp.optJSONObject("data");
        if (data != null) info = data.optJSONArray("info");
        if (info == null) return Result.string(list);
        for (int i = 0; i < info.length(); i++) {
            JSONObject item = info.optJSONObject(i);
            if (item == null) continue;
            String classify = item.optString("classify");
            boolean match = false;
            if ("hot".equals(tid)) {
                match = "1".equals(classify) || "2".equals(classify);
            } else if ("special".equals(tid)) {
                match = "3".equals(classify) || "5".equals(classify);
            } else if ("global".equals(tid)) {
                match = "4".equals(classify) || "2".equals(classify);
            }
            if (!match) continue;
            String rankid = item.optString("rankid");
            String rankname = item.optString("rankname");
            String imgurl = b(item.optString("imgurl"));
            list.add(new Vod(new StringBuilder("kugou_").append(rankid).toString(), rankname, imgurl, "榜单"));
        }
        return Result.string(list);
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        if (ids == null || ids.isEmpty()) return Result.string(new ArrayList<>());
        String id = ids.get(0);
        String pic = DEFAULT_PIC;
        if (id.startsWith("kugou-mp3_") || id.startsWith("kugou-mv_")) {
            String name = "酷狗单曲";
            if (id.startsWith("kugou-mp3_")) {
                String hashPart = id.substring(10);
                String[] parts = hashPart.split("_");
                if (parts.length > 0 && !TextUtils.isEmpty(parts[0])) {
                    JSONObject resp = a(String.format(SONG_INFO_URL, parts[0]));
                    String fileName = resp.optString("fileName");
                    if (!TextUtils.isEmpty(fileName)) name = fileName;
                    String albumImg = resp.optString("album_img");
                    if (!TextUtils.isEmpty(albumImg)) pic = b(albumImg);
                }
            }
            Vod vod = new Vod(id, name, pic, "单曲");
            vod.setVodPlayFrom("MP3");
            vod.setVodPlayUrl(new StringBuilder().append(name).append("$").append(id).toString());
            vod.setVodContent("酷狗音乐 · 单曲");
            return Result.string(vod);
        } else if (id.startsWith("kugou_")) {
            String rankid = id.substring(6);
            String url = String.format(RANK_SONG_URL, rankid);
            JSONObject resp = a(url);
            JSONArray info = null;
            JSONObject data = resp.optJSONObject("data");
            if (data != null) info = data.optJSONArray("info");
            ArrayList<String> mp3List = new ArrayList<>();
            ArrayList<String> mvList = new ArrayList<>();
            if (info != null) {
                String cover = pic;
                for (int i = 0; i < info.length(); i++) {
                    JSONObject item = info.optJSONObject(i);
                    if (item == null) continue;
                    String filename = e(item);
                    String playId = c(item);
                    if (!TextUtils.isEmpty(playId)) {
                        mp3List.add(new StringBuilder().append(filename).append("$").append(playId).toString());
                    }
                    String mvhash = item.optString("mvhash");
                    String mvId = TextUtils.isEmpty(mvhash) ? "" : new StringBuilder("kugou-mv_").append(mvhash).toString();
                    if (!TextUtils.isEmpty(mvId)) {
                        mvList.add(new StringBuilder().append(filename).append("$").append(mvId).toString());
                    }
                    if (pic.equals(cover)) {
                        cover = d(item);
                    }
                }
                pic = cover;
            }
            Vod vod = new Vod(new StringBuilder("kugou_").append(rankid).toString(),
                    new StringBuilder("酷狗榜单 ").append(rankid).toString(),
                    pic,
                    new StringBuilder().append(mp3List.size()).append("首").toString());
            vod.setVodPlayFrom("MP3$$$MV");
            vod.setVodPlayUrl(new StringBuilder().append(TextUtils.join("#", mp3List)).append("$$$").append(TextUtils.join("#", mvList)).toString());
            vod.setVodContent("酷狗音乐 · 榜单");
            return Result.string(vod);
        }
        return Result.string(new ArrayList<>());
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        if (TextUtils.isEmpty(id)) return Result.string(new ArrayList<>());
        if (id.contains("-")) {
            String[] parts = id.split("-", 2);
            if (parts.length == 2) id = parts[1];
        }
        String url = "";
        if (id.startsWith("mp3_")) {
            String[] hashParts = id.split("_");
            String hash = hashParts[1];
            JSONObject resp = a(String.format(SONG_INFO_URL, hash));
            url = resp.optString("url");
            if (TextUtils.isEmpty(url)) {
                Object backup = resp.opt("backup_url");
                if (backup instanceof JSONArray && ((JSONArray) backup).length() > 0) {
                    url = ((JSONArray) backup).optString(0);
                } else if (backup instanceof String && !TextUtils.isEmpty((String) backup)) {
                    url = (String) backup;
                } else {
                    url = "";
                }
            }
            if (TextUtils.isEmpty(url)) {
                String error = resp.optString("error");
                boolean paid = !TextUtils.isEmpty(error) && error.contains("付费");
                if (paid || TextUtils.isEmpty(url)) {
                    String songName = resp.optString("songName");
                    String author = resp.optString("author_name");
                    if (TextUtils.isEmpty(author)) author = resp.optString("singerName");
                    if (!TextUtils.isEmpty(songName)) {
                        String foundUrl = "";
                        try {
                            String searchUrl = SQ0527_SEARCH + URLEncoder.encode(songName, UTF_8);
                            String html = f(searchUrl, SQ0527_REFERER);
                            Document doc = Jsoup.parse(html);
                            String href = "";
                            for (Element link : doc.select("ul.mul li a")) {
                                String text = link.text();
                                if (!text.contains(songName)) continue;
                                if (!TextUtils.isEmpty(author) && !text.contains(author)) continue;
                                href = link.attr("href");
                                break;
                            }
                            if (!TextUtils.isEmpty(href)) {
                                if (!href.startsWith("http")) {
                                    href = new StringBuilder().append(SQ0527_BASE).append(href).toString();
                                }
                                String detailHtml = f(href, searchUrl);
                                Document detailDoc = Jsoup.parse(detailHtml);
                                Element downloadBtn = detailDoc.selectFirst("#btn-download-mp3");
                                if (downloadBtn != null) {
                                    String downloadHref = downloadBtn.attr("href");
                                    if (downloadHref.startsWith("http")) {
                                        foundUrl = downloadHref;
                                    } else {
                                        foundUrl = new StringBuilder().append(SQ0527_BASE).append(downloadHref).toString();
                                    }
                                }
                            }
                        } catch (Exception e) {
                        }
                        if (TextUtils.isEmpty(foundUrl)) {
                            try {
                                String searchUrl = new StringBuilder(GEQUBAO_SEARCH).append(URLEncoder.encode(songName, UTF_8)).toString();
                                String html = f(searchUrl, GEQUBAO_REFERER);
                                Document doc = Jsoup.parse(html);
                                String href = "";
                                for (Element link : doc.select("a[href^=/music/]")) {
                                    String title = link.attr("title");
                                    if (TextUtils.isEmpty(title)) title = link.text();
                                    if (!title.contains(songName)) continue;
                                    if (!TextUtils.isEmpty(author) && !title.contains(author)) continue;
                                    href = link.attr("href");
                                    break;
                                }
                                if (!TextUtils.isEmpty(href)) {
                                    String detailUrl = new StringBuilder().append(GEQUBAO_BASE).append(href).toString();
                                    String detailHtml = f(detailUrl, searchUrl);
                                    Matcher matcher = PLAY_ID_PATTERN.matcher(detailHtml);
                                    if (matcher.find()) {
                                        String playId = matcher.group(1);
                                        String postBody = new StringBuilder().append("id=").append(URLEncoder.encode(playId, UTF_8)).toString();
                                        Map<String, String> postHeaders = g(detailUrl);
                                        postHeaders.put("Content-Type", "application/x-www-form-urlencoded");
                                        String postResp = OkHttp.post(GEQUBAO_PLAY_URL, postBody, postHeaders);
                                        JSONObject postJson = new JSONObject(postResp);
                                        if (postJson.optInt("code") == 1) {
                                            JSONObject postData = postJson.optJSONObject("data");
                                            if (postData != null) {
                                                foundUrl = postData.optString("url");
                                            }
                                        }
                                    }
                                }
                            } catch (Exception e) {
                            }
                        }
                        url = foundUrl;
                    }
                }
            }
        } else if (id.startsWith("mv_")) {
            String[] hashParts = id.split("_");
            String hash = hashParts[1];
            JSONObject resp = a(String.format(MV_URL, hash));
            JSONObject mvdata = resp.optJSONObject("mvdata");
            if (mvdata != null) {
                String qualityKey = null;
                if (mvdata.has("sq")) qualityKey = "sq";
                else if (mvdata.has("le")) qualityKey = "le";
                if (qualityKey != null) {
                    url = mvdata.optJSONObject(qualityKey).optString("downurl");
                }
            }
        }
        if (TextUtils.isEmpty(url)) {
            HashMap<String, String> header = new HashMap<>();
            header.put("User-Agent", KUGOU_UA);
            return Result.get().url("").parse(0).header(header).string();
        } else {
            Map<String, String> header;
            if (url.contains("gequbao") || url.contains("kuwo.cn") || url.contains("sq0527")) {
                header = g(GEQUBAO_REFERER);
            } else {
                header = new HashMap<>();
                header.put("User-Agent", KUGOU_UA);
            }
            return Result.get().url(url).parse(0).header(header).string();
        }
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        return searchContent(key, quick, "1");
    }

    public String searchContent(String key, boolean quick, String pg) throws Exception {
        if (TextUtils.isEmpty(key)) return Result.string(new ArrayList<>());
        if (TextUtils.isEmpty(pg)) pg = "1";
        key = key.trim();
        ArrayList<Vod> list = new ArrayList<>();
        try {
            String url = String.format(SEARCH_URL, URLEncoder.encode(key, UTF_8), pg);
            JSONObject resp = a(url);
            JSONArray info = null;
            JSONObject data = resp.optJSONObject("data");
            if (data != null) info = data.optJSONArray("info");
            if (info == null) return Result.string(list);
            for (int i = 0; i < info.length(); i++) {
                JSONObject item = info.optJSONObject(i);
                if (item == null) continue;
                String playId = c(item);
                if (TextUtils.isEmpty(playId)) continue;
                String filename = e(item);
                String cover = d(item);
                String singer = item.optString("singername");
                list.add(new Vod(playId, filename, cover, singer));
            }
        } catch (Exception e) {
        }
        return Result.string(list);
    }
}
