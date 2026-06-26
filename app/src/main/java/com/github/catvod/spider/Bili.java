package com.github.catvod.spider;

import android.content.Context;
import android.text.TextUtils;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Danmaku;
import com.github.catvod.bean.Filter;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.bean.bili.Dash;
import com.github.catvod.bean.bili.Data;
import com.github.catvod.bean.bili.Media;
import com.github.catvod.bean.bili.Page;
import com.github.catvod.bean.bili.Resp;
import com.github.catvod.bean.bili.Wbi;
import com.github.catvod.crawler.Spider;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Json;
import com.github.catvod.utils.Path;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.ByteArrayInputStream;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * @author ColaMint & FongMi & 唐三
 */
public class Bili extends Spider {

    private static final String COOKIE = "buvid3=84B0395D-C9F2-C490-E92E-A09AB48FE26E71636infoc";
    private static String cookie;

    private JsonObject extend;
    private boolean login;
    private boolean isVip;
    private Wbi wbi;

    private static String getCookie() {
        return TextUtils.isEmpty(cookie) ? COOKIE : cookie;
    }

    private static void findAudio(Dash dash, StringBuilder sb) {
        for (Media audio : dash.getAudio()) {
            HashMap<String, String> formats = new HashMap<>();
            formats.put("30280", "192000");
            formats.put("30232", "132000");
            formats.put("30216", "64000");
            for (String key : formats.keySet()) {
                if (audio.getId().equals(key)) {
                    sb.append(getMedia(audio));
                }
            }
        }
    }

    private static String getAdaptationSet(Media media, String params) {
        String id = media.getId() + "_" + media.getCodecId();
        String type = media.getMimeType().split("/")[0];
        String baseUrl = media.getBaseUrl().replace("&", "&amp;");
        return String.format(Locale.getDefault(), "<AdaptationSet>\n<ContentComponent contentType=\"%s\"/>\n<Representation id=\"%s\" bandwidth=\"%s\" codecs=\"%s\" mimeType=\"%s\" %s startWithSAP=\"%s\">\n<BaseURL>%s</BaseURL>\n<SegmentBase indexRange=\"%s\">\n<Initialization range=\"%s\"/>\n</SegmentBase>\n</Representation>\n</AdaptationSet>", type, id, media.getBandWidth(), media.getCodecs(), media.getMimeType(), params, media.getStartWithSap(), baseUrl, media.getSegmentBase().getIndexRange(), media.getSegmentBase().getInitialization());
    }

    private static HashMap<String, String> getHeaders() {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36");
        headers.put("Referer", "https://www.bilibili.com");
        String c = getCookie();
        if (c != null) headers.put("cookie", c);
        return headers;
    }

    private static String getMedia(Media media) {
        if (media.getMimeType().startsWith("video")) {
            String params = String.format(Locale.getDefault(), "height='%s' width='%s' frameRate='%s' sar='%s'", media.getHeight(), media.getWidth(), media.getFrameRate(), media.getSar());
            return getAdaptationSet(media, params);
        } else if (media.getMimeType().startsWith("audio")) {
            HashMap<String, String> formats = new HashMap<>();
            formats.put("30280", "192000");
            formats.put("30232", "132000");
            formats.put("30216", "64000");
            String params = String.format("numChannels='2' sampleRate='%s'", formats.get(media.getId()));
            return getAdaptationSet(media, params);
        } else {
            return "";
        }
    }

    private static HashMap<String, String> getSearchHeaders() {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/117.0.0.0 Safari/537.36");
        headers.put("Referer", "https://search.bilibili.com");
        headers.put("cookie", getCookie());
        return headers;
    }

    private static String parseCookie(String c) {
        if (TextUtils.isEmpty(c)) return "";
        c = c.trim();
        if (c.startsWith("{")) {
            try {
                JsonObject obj = Json.safeObject(c);
                if (obj.has("cookie") && !obj.get("cookie").isJsonNull()) {
                    c = obj.get("cookie").getAsString().trim();
                }
            } catch (Exception e) {
            }
        }
        return c;
    }

    private static Object[] buildError(int code, String msg) {
        Object[] result = new Object[3];
        result[0] = code;
        result[1] = "text/plain; charset=utf-8";
        result[2] = new ByteArrayInputStream(msg.getBytes());
        return result;
    }

    private List<Filter> getFilter() {
        List<Filter> items = new ArrayList<>();
        items.add(new Filter("order", "排序", Arrays.asList(new Filter.Value("預設", "totalrank"), new Filter.Value("最多點擊", "click"), new Filter.Value("最新發布", "pubdate"), new Filter.Value("最多彈幕", "dm"), new Filter.Value("最多收藏", "stow"))));
        items.add(new Filter("duration", "時長", Arrays.asList(new Filter.Value("全部時長", "0"), new Filter.Value("60分鐘以上", "4"), new Filter.Value("30~60分鐘", "3"), new Filter.Value("10~30分鐘", "2"), new Filter.Value("10分鐘以下", "1"))));
        return items;
    }

    @Override
    public void init(Context context, String extend) throws Exception {
        String ext = extend == null ? "" : extend.trim();
        if (ext.startsWith("http://") || ext.startsWith("https://")) {
            JsonObject obj = new JsonObject();
            obj.addProperty("json", ext);
            this.extend = obj;
        } else {
            this.extend = Json.safeObject(ext);
        }
        try {
            if (this.extend.has("cookie")) {
                String c = this.extend.get("cookie").getAsString();
                if (!c.startsWith("http")) {
                    String parsed = parseCookie(c);
                    cookie = parsed;
                    if (!TextUtils.isEmpty(parsed)) return;
                }
            }
            String parsed = parseCookie(Path.read(Path.tv("bilibili")));
            cookie = parsed;
            if (TextUtils.isEmpty(parsed)) cookie = COOKIE;
        } catch (Throwable t) {
            cookie = COOKIE;
        }
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        if (extend.has("json")) {
            JsonElement elem = extend.get("json");
            if (elem.isJsonObject() || elem.isJsonArray()) {
                return elem.toString();
            }
            String str = elem.getAsString().trim();
            if (str.startsWith("{")) return str;
            return OkHttp.string(str);
        }
        if (extend.has("class")) {
            return extend.toString();
        }
        List<Class> classes = new ArrayList<>();
        LinkedHashMap<String, List<Filter>> filters = new LinkedHashMap<>();
        String[] types = extend.get("type").getAsString().split("#");
        for (String type : types) {
            classes.add(new Class(type));
            filters.put(type, getFilter());
        }
        return Result.string(classes, filters);
    }

    @Override
    public String homeVideoContent() throws Exception {
        try {
            String api = "https://api.bilibili.com/x/web-interface/popular?ps=20";
            String json = OkHttp.string(api, getSearchHeaders());
            Resp resp = Resp.objectFrom(json);
            List<Vod> list = new ArrayList<>();
            for (Resp.Result item : Resp.Result.arrayFrom(resp.getData().getList())) list.add(item.getVod());
            return Result.string(list);
        } catch (Throwable t) {
            return Result.string(new ArrayList<>());
        }
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        try {
            if (tid.endsWith("/{pg}")) {
                LinkedHashMap<String, Object> params = new LinkedHashMap<>();
                params.put("mid", tid.split("/")[0]);
                params.put("pn", pg);
                List<Vod> list = new ArrayList<>();
                String json = OkHttp.string("https://api.bilibili.com/x/space/wbi/arc/search?" + wbi.getQuery(params), getHeaders());
                for (Resp.Result item : Resp.Result.arrayFrom(Resp.objectFrom(json).getData().getList().getAsJsonObject().get("vlist"))) list.add(item.getVod());
                return Result.string(list);
            } else {
                String order = extend.containsKey("order") ? extend.get("order") : "totalrank";
                String duration = extend.containsKey("duration") ? extend.get("duration") : "0";
                if (extend.containsKey("tid")) tid = tid + " " + extend.get("tid");
                String api = "https://api.bilibili.com/x/web-interface/search/type?search_type=video&keyword=" + URLEncoder.encode(tid, "UTF-8") + "&order=" + order + "&duration=" + duration + "&page=" + pg;
                String json = OkHttp.string(api, getSearchHeaders());
                if (TextUtils.isEmpty(json) || !json.trim().startsWith("{")) {
                    return Result.string(new ArrayList<>());
                }
                Resp resp = Resp.objectFrom(json);
                List<Vod> list = new ArrayList<>();
                for (Resp.Result item : Resp.Result.videoArrayFrom(resp.getData().getResult())) list.add(item.getVod());
                return Result.string(list);
            }
        } catch (Throwable t) {
            return Result.string(new ArrayList<>());
        }
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        if (!login) {
            String json = OkHttp.string("https://api.bilibili.com/x/web-interface/nav", getHeaders());
            Data data = Resp.objectFrom(json).getData();
            login = data.isLogin();
            isVip = data.isVip();
            wbi = data.getWbi();
        }

        String[] split = ids.get(0).split("@");
        String bvid = split[0];
        String aid = split[1];

        String api = "https://api.bilibili.com/x/web-interface/view?aid=" + aid;
        String json = OkHttp.string(api, getHeaders());
        Data detail = Resp.objectFrom(json).getData();
        Vod vod = new Vod();
        vod.setVodId(ids.get(0));
        vod.setVodPic(detail.getPic());
        vod.setVodName(detail.getTitle());
        vod.setTypeName(detail.getType());
        vod.setVodContent(detail.getDesc());
        vod.setVodDirector(detail.getOwner().getFormat());
        vod.setVodRemarks(detail.getDuration() / 60 + "分鐘");

        List<String> acceptDesc = new ArrayList<>();
        List<Integer> acceptQuality = new ArrayList<>();
        api = "https://api.bilibili.com/x/player/playurl?avid=" + aid + "&cid=" + detail.getCid() + "&qn=127&fnval=4048&fourk=1";
        Resp resp = Resp.objectFrom(OkHttp.string(api, getHeaders()));
        Data play = resp.getData();
        for (int i = 0; i < play.getAcceptQuality().size(); i++) {
            int qn = play.getAcceptQuality().get(i);
            if (!login && qn > 32) continue;
            if (!isVip && qn > 80) continue;
            acceptQuality.add(play.getAcceptQuality().get(i));
            acceptDesc.add(play.getAcceptDescription().get(i));
        }

        if (acceptQuality.isEmpty()) {
            int defaultQn = login ? 64 : 32;
            String defaultDesc = login ? "720P" : "480P";
            acceptQuality.add(defaultQn);
            acceptDesc.add(defaultDesc);
            String message = resp.getMessage();
            if (!TextUtils.isEmpty(message)) {
                vod.setVodRemarks("需登录Cookie:" + message);
            } else if (!login) {
                vod.setVodRemarks("未登录B站，请在Gate填Cookie");
            }
        }

        List<String> episode = new ArrayList<>();
        LinkedHashMap<String, String> flag = new LinkedHashMap<>();
        for (Page page : detail.getPages())
            episode.add(page.getPart() + "$" + aid + "+" + page.getCid() + "+" + TextUtils.join(":", acceptQuality) + "+" + TextUtils.join(":", acceptDesc));
        flag.put("B站", TextUtils.join("#", episode));

        episode = new ArrayList<>();
        api = "https://api.bilibili.com/x/web-interface/archive/related?bvid=" + bvid;
        json = OkHttp.string(api, getHeaders());
        JsonArray array = Json.parse(json).getAsJsonObject().getAsJsonArray("data");
        for (int i = 0; i < array.size(); i++) {
            JsonObject object = array.get(i).getAsJsonObject();
            episode.add(object.get("title").getAsString() + "$" + object.get("aid").getAsInt() + "+" + object.get("cid").getAsInt() + "+" + TextUtils.join(":", acceptQuality) + "+" + TextUtils.join(":", acceptDesc));
        }
        flag.put("相关", TextUtils.join("#", episode));

        vod.setVodPlayFrom(TextUtils.join("$$$", flag.keySet()));
        vod.setVodPlayUrl(TextUtils.join("$$$", flag.values()));
        return Result.string(vod);
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        return categoryContent(key, "1", true, new HashMap<>());
    }

    @Override
    public String searchContent(String key, boolean quick, String pg) throws Exception {
        return categoryContent(key, pg, true, new HashMap<>());
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        if (TextUtils.isEmpty(id) || !id.contains("+")) {
            return Result.error("B站播放参数无效");
        }
        String[] parts = id.split("\\+", 4);
        if (parts.length < 4) {
            return Result.error("B站播放参数不完整");
        }
        String aid = parts[0];
        String cid = parts[1];
        String[] acceptQuality;
        if (TextUtils.isEmpty(parts[2])) {
            acceptQuality = new String[]{"32"};
        } else {
            acceptQuality = parts[2].split(":");
        }
        String[] acceptDesc;
        if (TextUtils.isEmpty(parts[3])) {
            acceptDesc = new String[]{"480P"};
        } else {
            acceptDesc = parts[3].split(":");
        }
        List<String> url = new ArrayList<>();
        String dan = "https://api.bilibili.com/x/v1/dm/list.so?oid=".concat(cid);
        int min = Math.min(acceptDesc.length, acceptQuality.length);
        for (int i = 0; i < min; i++) {
            url.add(acceptDesc[i]);
            url.add(Proxy.getUrl() + "?do=bili&aid=" + aid + "&cid=" + cid + "&qn=" + acceptQuality[i] + "&type=mpd");
        }
        if (url.isEmpty()) {
            return Result.error("B站无可用清晰度");
        }
        Danmaku danmaku = new Danmaku();
        danmaku.setName("B站");
        danmaku.setUrl(dan);
        return Result.get().url(url).danmaku(Arrays.asList(danmaku)).dash().header(getHeaders()).string();
    }

    @Override
    public Object[] proxy(Map<String, String> params) throws Exception {
        try {
            String aid = params.get("aid");
            String cid = params.get("cid");
            String qn = params.get("qn");
            if (TextUtils.isEmpty(aid) || TextUtils.isEmpty(cid) || TextUtils.isEmpty(qn)) {
                return buildError(400, "missing aid/cid/qn");
            }
            String api = "https://api.bilibili.com/x/player/playurl?avid=" + aid + "&cid=" + cid + "&qn=" + qn + "&fnval=4048&fourk=1";
            Resp resp = Resp.objectFrom(OkHttp.string(api, getHeaders()));
            if (resp.getCode() != 0) {
                String msg = resp.getMessage();
                if (TextUtils.isEmpty(msg)) msg = "playurl失败 code=" + resp.getCode();
                return buildError(502, msg);
            }
            Dash dash = resp.getData().getDash();
            StringBuilder video = new StringBuilder();
            StringBuilder audio = new StringBuilder();
            findAudio(dash, audio);
            for (Media m : dash.getVideo()) {
                if (m.getId().equals(qn)) {
                    video.append(getMedia(m));
                }
            }
            if (video.length() == 0 || audio.length() == 0) {
                return buildError(502, "无DASH音视频轨，请更新Gate哔哩Cookie");
            }
            String mpd = String.format(Locale.getDefault(), "<MPD xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xmlns=\"urn:mpeg:dash:schema:mpd:2011\" xsi:schemaLocation=\"urn:mpeg:dash:schema:mpd:2011 DASH-MPD.xsd\" type=\"static\" mediaPresentationDuration=\"PT%sS\" minBufferTime=\"PT%sS\" profiles=\"urn:mpeg:dash:profile:isoff-on-demand:2011\">\n<Period duration=\"PT%sS\" start=\"PT0S\">\n%s\n%s\n</Period>\n</MPD>", dash.getDuration(), dash.getMinBufferTime(), dash.getDuration(), video.toString(), audio.toString());
            Object[] result = new Object[3];
            result[0] = 200;
            result[1] = "application/dash+xml";
            result[2] = new ByteArrayInputStream(mpd.getBytes());
            return result;
        } catch (Throwable t) {
            String msg = t.getMessage();
            if (msg == null) msg = "proxy error";
            return buildError(500, msg);
        }
    }
}
