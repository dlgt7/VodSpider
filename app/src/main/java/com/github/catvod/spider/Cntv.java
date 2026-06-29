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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author CatVod
 * @date 2024-10-06
 */
public class Cntv extends Spider {

    private static final Pattern GUID_PATTERN = Pattern.compile("var\\s+guid\\s*=\\s*\"(.+?)\";");
    private static final Pattern DOMAIN_PATTERN = Pattern.compile("(https?://[a-zA-Z0-9.]+)/");
    private static final String[] CATEGORIES = {"电视剧", "动画片", "纪录片", "特别节目", "栏目大全"};

    private static String getExtend(HashMap<String, String> extend, String key) {
        if (extend != null && extend.containsKey(key)) {
            String value = extend.get(key);
            if (value != null) {
                return value;
            }
        }
        return "";
    }

    private static String buildUrl(String type, String page, HashMap<String, String> extend) {
        String area = getExtend(extend, "datadq-area");
        String sc = getExtend(extend, "datafl-sc");
        String year = getExtend(extend, "datanf-year");
        String letter = getExtend(extend, "dataszm-letter");
        String channel = getExtend(extend, "datapd-channel");

        String encodedType;
        try {
            encodedType = URLEncoder.encode(type, "UTF-8");
        } catch (Exception e) {
            encodedType = type;
        }

        StringBuilder url = new StringBuilder();
        if ("动画片".equals(type)) {
            url.append("https://api.cntv.cn/list/getVideoAlbumList?channelid=CHAL1460955899450127&area=")
               .append(area)
               .append("&sc=").append(sc)
               .append("&fc=").append(encodedType)
               .append("&letter=").append(letter)
               .append("&p=").append(page)
               .append("&n=24&serviceId=tvcctv&topv=1&t=json");
        } else if ("纪录片".equals(type)) {
            url.append("https://api.cntv.cn/list/getVideoAlbumList?channelid=CHAL1460955924871139&fc=")
               .append(encodedType)
               .append("&channel=").append(channel)
               .append("&sc=").append(sc)
               .append("&year=").append(year)
               .append("&letter=").append(letter)
               .append("&p=").append(page)
               .append("&n=24&serviceId=tvcctv&topv=1&t=json");
        } else if ("电视剧".equals(type)) {
            url.append("https://api.cntv.cn/list/getVideoAlbumList?channelid=CHAL1460955853485115&area=")
               .append(area)
               .append("&sc=").append(sc)
               .append("&fc=").append(encodedType)
               .append("&year=").append(year)
               .append("&letter=").append(letter)
               .append("&p=").append(page)
               .append("&n=24&serviceId=tvcctv&topv=1&t=json");
        } else if ("特别节目".equals(type)) {
            url.append("https://api.cntv.cn/list/getVideoAlbumList?channelid=CHAL1460955953877151&channel=")
               .append(channel)
               .append("&sc=").append(sc)
               .append("&fc=").append(encodedType)
               .append("&bigday=&letter=").append(letter)
               .append("&p=").append(page)
               .append("&n=24&serviceId=tvcctv&topv=1&t=json");
        } else {
            String cid = getExtend(extend, "cid");
            String fc = getExtend(extend, "fc");
            String fl = getExtend(extend, "fl");
            url.append("https://api.cntv.cn/lanmu/columnSearch?&fl=")
               .append(fl)
               .append("&fc=").append(fc)
               .append("&cid=").append(cid)
               .append("&p=").append(page)
               .append("&n=20&serviceId=tvcctv&t=json&cb=ko");
        }
        return url.toString();
    }

    private static List<Vod> parseList(String response, String type) {
        List<Vod> list = new ArrayList<>();
        try {
            JSONObject object = new JSONObject(response);
            JSONObject data = object.optJSONObject("data");
            if (data == null) return list;

            JSONArray array = data.optJSONArray("list");
            if (array == null) return list;

            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.getJSONObject(i);
                String url = item.optString("url");
                if (TextUtils.isEmpty(url)) continue;

                String title = item.optString("title");
                String image = item.optString("image");
                String id = item.optString("id");
                String year = item.optString("year");
                String actors = item.optString("actors");
                String brief = item.optString("brief");

                StringBuilder sb = new StringBuilder();
                sb.append(type).append("###")
                  .append(title).append("###")
                  .append(url).append("###")
                  .append(image).append("###")
                  .append(id).append("###")
                  .append(year).append("###")
                  .append(actors).append("###")
                  .append(brief);
                String vodId = sb.toString();

                list.add(new Vod(vodId, title, image, year));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    private Map<String, String> getHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/94.0.4606.54 Safari/537.36");
        headers.put("Referer", "https://tv.cctv.com/");
        return headers;
    }

    private ArrayList<String> getVideoList(String guid) {
        ArrayList<String> list = new ArrayList<>();
        if (TextUtils.isEmpty(guid)) return list;

        String url = "https://api.cntv.cn/video/videoinfoByGuid?guid=" + guid + "&serviceId=tvcctv";
        try {
            String response = OkHttp.string(url, getHeaders());
            JSONObject object = new JSONObject(response);

            String ctid = object.optString("ctid");
            if (TextUtils.isEmpty(ctid)) {
                JSONObject data = object.optJSONObject("data");
                if (data != null) {
                    ctid = data.optString("ctid");
                }
            }

            if (!TextUtils.isEmpty(ctid)) {
                String listUrl = "https://api.cntv.cn/NewVideo/getVideoListByColumn?id=" + ctid + "&serviceId=tvcctv&p=1&n=100&mode=0&pub=1";
                String listResponse = OkHttp.string(listUrl, getHeaders());
                JSONObject listObject = new JSONObject(listResponse);
                JSONObject listData = listObject.optJSONObject("data");

                if (listData != null) {
                    JSONArray videoList = listData.optJSONArray("list");
                    if (videoList != null && videoList.length() > 0) {
                        for (int i = 0; i < videoList.length(); i++) {
                            JSONObject video = videoList.getJSONObject(i);
                            String videoGuid = video.optString("guid");
                            String title = video.optString("title");
                            if (!TextUtils.isEmpty(videoGuid)) {
                                list.add(title + "$" + videoGuid);
                            }
                        }
                        return list;
                    }
                }
            }

            String title = object.optString("title", "正片");
            list.add(title + "$" + guid);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    private String getVideoUrl(String guid) {
        String url = "https://vdn.apps.cntv.cn/api/getHttpVideoInfo.do?pid=" + guid;
        try {
            String response = OkHttp.string(url, getHeaders());
            JSONObject object = new JSONObject(response);
            String hlsUrl = object.optString("hls_url").trim();

            if (TextUtils.isEmpty(hlsUrl)) return "";

            String hlsContent = OkHttp.string(hlsUrl, getHeaders()).trim();
            String[] lines = hlsContent.split("\n");
            if (lines.length < 1) return hlsUrl;

            Matcher matcher = DOMAIN_PATTERN.matcher(hlsUrl);
            if (!matcher.find()) return hlsUrl;

            String domain = matcher.group(1);
            String lastLine = lines[lines.length - 1];
            String[] parts = lastLine.split("/");

            if (parts.length > 3) {
                parts[3] = "1200";
                parts[parts.length - 1] = "1200.m3u8";
                String newUrl = domain + "/" + TextUtils.join("/", parts);
                try {
                    OkHttp.string(newUrl, getHeaders());
                    return newUrl;
                } catch (Exception e) {
                    return domain + "/" + lastLine;
                }
            } else {
                return domain + "/" + lastLine;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "";
    }

    @Override
    public String homeContent(boolean filter) {
        List<Class> classes = new ArrayList<>();
        for (String category : CATEGORIES) {
            classes.add(new Class(category, category));
        }

        List<Vod> videos = new ArrayList<>();
        try {
            String url = buildUrl("电视剧", "1", new HashMap<>());
            String response = OkHttp.string(url, getHeaders());
            videos = parseList(response, "电视剧");
        } catch (Exception e) {
            e.printStackTrace();
        }

        return Result.string(classes, videos);
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) {
        String page = TextUtils.isEmpty(pg) ? "1" : pg;
        String url = buildUrl(tid, page, extend);

        List<Vod> videos = new ArrayList<>();
        try {
            String response = OkHttp.string(url, getHeaders());
            boolean isColumn = "栏目大全".equals(tid);

            if (!isColumn && response.trim().startsWith("ko(")) {
                isColumn = true;
            }

            if (isColumn) {
                int endIndex = response.lastIndexOf(");");
                if (endIndex > 0) {
                    response = response.substring(response.indexOf("(") + 1, endIndex);
                }

                JSONObject object = new JSONObject(response);
                JSONObject resp = object.optJSONObject("response");
                if (resp != null) {
                    JSONArray docs = resp.optJSONArray("docs");
                    if (docs != null) {
                        for (int i = 0; i < docs.length(); i++) {
                            JSONObject doc = docs.getJSONObject(i);
                            JSONObject lastVideo = doc.optJSONObject("lastVIDE");
                            String videoId = lastVideo != null ? lastVideo.optString("videoSharedCode") : "";

                            String name = doc.optString("column_name");
                            String website = doc.optString("column_website");
                            String logo = doc.optString("column_logo");
                            String playDate = doc.optString("column_playdate");
                            String brief = doc.optString("column_brief");

                            if (TextUtils.isEmpty(website)) continue;

                            String vodId = "栏目大全" + "###" + name + "###" + website + "###" + logo + "###" + videoId + "###" + playDate + "######" + brief;
                            videos.add(new Vod(vodId, name, logo, ""));
                        }
                    }
                }
            } else {
                videos = parseList(response, tid);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return Result.string(videos);
    }

    @Override
    public String detailContent(List<String> ids) {
        String id = ids.get(0);
        String[] parts = id.split("###", 8);

        String type = parts[0];
        String name = parts.length > 1 ? parts[1] : "央视";
        String pic = parts.length > 3 ? parts[3] : "";
        String videoId = parts.length > 4 ? parts[4] : "";
        String year = parts.length > 5 ? parts[5] : "";
        String actor = parts.length > 6 ? parts[6] : "";
        String content = parts.length > 7 ? parts[7] : "";

        Vod vod = new Vod();
        vod.setVodId(id);
        vod.setVodName(name);
        vod.setVodPic(pic);
        vod.setTypeName(type);
        vod.setVodYear(year);
        vod.setVodActor(actor);
        vod.setVodContent(content);

        ArrayList<String> playUrls = new ArrayList<>();
        if ("栏目大全".equals(type)) {
            playUrls = getVideoList(videoId);
        } else {
            try {
                if (!TextUtils.isEmpty(videoId)) {
                    String url = "https://api.cntv.cn/NewVideo/getVideoListByAlbumIdNew?id=" + videoId + "&serviceId=tvcctv&p=1&n=100&mode=0&pub=1";
                    String response = OkHttp.string(url, getHeaders());
                    JSONObject object = new JSONObject(response);
                    JSONObject data = object.optJSONObject("data");

                    if (data != null) {
                        JSONArray list = data.optJSONArray("list");
                        if (list != null) {
                            for (int i = 0; i < list.length(); i++) {
                                JSONObject item = list.getJSONObject(i);
                                String guid = item.optString("guid");
                                String title = item.optString("title");
                                if (!TextUtils.isEmpty(guid)) {
                                    playUrls.add(title + "$" + guid);
                                }
                            }
                        }
                    }
                }

                if (playUrls.isEmpty() && !TextUtils.isEmpty(videoId) && videoId.matches("[0-9a-fA-F]{32}")) {
                    playUrls = getVideoList(videoId);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        vod.setVodPlayFrom("CCTV");
        vod.setVodPlayUrl(TextUtils.join("#", playUrls));

        return Result.string(vod);
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) {
        String playUrl = "";
        if ("CCTV".equals(flag)) {
            playUrl = getVideoUrl(id);
        } else if (id.startsWith("http")) {
            try {
                String content = OkHttp.string(id, getHeaders());
                Matcher matcher = GUID_PATTERN.matcher(content);
                if (matcher.find()) {
                    playUrl = getVideoUrl(matcher.group(1));
                } else {
                    playUrl = id;
                }
            } catch (Exception e) {
                playUrl = id;
            }
        } else {
            playUrl = getVideoUrl(id);
        }

        if (TextUtils.isEmpty(playUrl)) {
            playUrl = id;
        }

        Map<String, String> headers = getHeaders();
        headers.put("User-Agent", "Mozilla/5.0 (iPhone; CPU iPhone OS 9_1 like Mac OS X) AppleWebKit/601.1.46 Mobile/13B143 Safari/601.1");

        return Result.get().url(playUrl).header(headers).string();
    }

    @Override
    public String searchContent(String key, boolean quick) {
        return Result.string(new ArrayList<>());
    }
}