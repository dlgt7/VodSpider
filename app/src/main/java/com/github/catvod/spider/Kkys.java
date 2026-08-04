package com.github.catvod.spider;

import android.content.Context;
import android.text.TextUtils;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Util;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 可可影视 Spider
 * 站点：https://103.51.147.112:51120
 * 基于 HTML 解析（Jsoup），提取列表/详情/播放/搜索
 * 图片 CDN：https://vres.zyxpedu.com
 */
public class Kkys extends Spider {

    private static final String SITE_URL = "https://103.51.147.112:51120";
    private static final String IMG_CDN = "https://vres.zyxpedu.com";
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    /** 从 href 中提取 sid/nid 的正则 */
    private static final Pattern PLAY_ID_PATTERN = Pattern.compile("/play/(\\d+)-(\\d+)-(\\d+)\\.html");
    private static final Pattern TITLE_TAG_PATTERN = Pattern.compile("<title>(.+?)</title>", Pattern.DOTALL);
    private static final Pattern OG_IMAGE_PATTERN = Pattern.compile("<meta\\s+property=\"og:image\"\\s+content=\"([^\"]+)\"");
    private static final Pattern META_DESC_PATTERN = Pattern.compile("<meta\\s+name=\"description\"\\s+content=\"([^\"]+)\"");
    private static final Pattern TOKEN_PATTERN = Pattern.compile("name=\"t\" value=\"([^\"]+)\"");
    private static final Pattern VIDEO_URL_PATTERNS[] = {
            Pattern.compile("src:\\s*['\"]([^'\"]+\\.(?:m3u8|mp4)[^'\"]*)['\"]", Pattern.DOTALL),
            Pattern.compile("\"url\"\\s*:\\s*\"([^\"]+\\.(?:m3u8|mp4)[^\"]*)\"", Pattern.DOTALL),
            Pattern.compile("url\\s*:\\s*'([^']+\\.(?:m3u8|mp4)[^']*)'", Pattern.DOTALL)
    };
    private static final Pattern HTTP_MEDIA_PATTERN = Pattern.compile("https?://[^\\s\"'<>]+\\.(?:m3u8|mp4)[^\\s\"'<>]*");

    private String site = SITE_URL;

    @Override
    public void init(Context context, String extend) throws Exception {
        super.init(context);
        if (!TextUtils.isEmpty(extend)) {
            extend = extend.trim();
            if (extend.startsWith("http")) {
                site = extend;
                while (site.endsWith("/")) site = site.substring(0, site.length() - 1);
            }
        }
    }

    private Map<String, String> getHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", USER_AGENT);
        headers.put("Referer", site + "/");
        return headers;
    }

    /** 从 href 提取视频 ID */
    private String getVid(String url) {
        if (TextUtils.isEmpty(url)) return "";
        Matcher m = Pattern.compile("/detail/(\\d+)\\.html").matcher(url);
        if (m.find()) return m.group(1);
        m = Pattern.compile("/play/(\\d+)-").matcher(url);
        if (m.find()) return m.group(1);
        return "";
    }

    /** 修复图片地址 */
    private String fixPic(String pic) {
        if (TextUtils.isEmpty(pic)) return "";
        if (pic.contains("placeholder") || pic.contains("logo_placeholder")) return "";
        if (pic.startsWith("/")) return IMG_CDN + pic;
        return pic;
    }

    /** 清理标题水印 */
    private String cleanTitle(String title) {
        if (TextUtils.isEmpty(title)) return "";
        title = title.replace("可可影视-kekys.com", "").trim();
        title = title.replaceAll("[\\s]+", " ").trim();
        return title;
    }

    /** 解析列表项（.module-item） */
    private List<Vod> parseList(Elements items) {
        List<Vod> list = new ArrayList<>();
        for (Element item : items) {
            try {
                Element a = item.selectFirst(".v-item");
                if (a == null) continue;
                String href = a.attr("href");
                String vid = getVid(href);
                if (TextUtils.isEmpty(vid)) continue;

                String title = "";
                Elements titles = item.select(".v-item-title");
                for (Element t : titles) {
                    String text = t.text().trim();
                    if (!TextUtils.isEmpty(text) && !text.equals("可可影视-kekys.com")) {
                        title = text;
                        break;
                    }
                }

                String pic = "";
                Elements imgs = item.select("img");
                for (Element img : imgs) {
                    String src = img.attr("data-original");
                    if (TextUtils.isEmpty(src)) src = img.attr("data-src");
                    if (TextUtils.isEmpty(src)) src = img.attr("src");
                    if (!TextUtils.isEmpty(src) && !src.contains("placeholder") && !src.contains("logo_placeholder")) {
                        pic = src;
                        break;
                    }
                }
                pic = fixPic(pic);

                String note = "";
                Element bottom = item.selectFirst(".v-item-bottom span");
                if (bottom != null) note = bottom.text().trim();

                if (!TextUtils.isEmpty(title)) {
                    list.add(new Vod(vid, title, pic, note));
                }
            } catch (Exception e) {
                SpiderDebug.log(e);
            }
        }
        return list;
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        List<Class> classes = new ArrayList<>();
        classes.add(new Class("1", "电影"));
        classes.add(new Class("2", "连续剧"));
        classes.add(new Class("3", "动漫"));
        classes.add(new Class("4", "综艺纪录"));
        classes.add(new Class("6", "短剧"));
        return Result.string(classes, new ArrayList<>());
    }

    @Override
    public String homeVideoContent() throws Exception {
        List<Vod> list = new ArrayList<>();
        try {
            String html = OkHttp.string(site + "/channel/1.html", getHeaders());
            Document doc = Jsoup.parse(html);
            list = parseList(doc.select(".module-item"));
            if (list.size() > 24) list = new ArrayList<>(list.subList(0, 24));
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        return Result.string(list);
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        int page = Util.toInt(pg, 1);
        List<Vod> list = new ArrayList<>();
        try {
            String url = site + "/channel/" + tid + ".html?page=" + page;
            String html = OkHttp.string(url, getHeaders());
            Document doc = Jsoup.parse(html);
            list = parseList(doc.select(".module-item"));
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        int pageCount = list.size() >= 24 ? page + 1 : page;
        return Result.get().page(page, pageCount, list.size(), list.size()).vod(list).string();
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        String vid = ids.get(0);
        List<Vod> list = new ArrayList<>();
        if (TextUtils.isEmpty(vid)) return Result.string(list);

        try {
            String html = OkHttp.string(site + "/detail/" + vid + ".html", getHeaders());
            Document playDoc = Jsoup.parse(html);

            String title = "";
            Matcher tm = TITLE_TAG_PATTERN.matcher(html);
            if (tm.find()) {
                title = tm.group(1).split("-")[0].trim();
                title = cleanTitle(title);
            }

            String pic = "";
            Element ogImg = playDoc.selectFirst("meta[property=og:image]");
            if (ogImg != null) {
                pic = ogImg.attr("content");
                if (pic.startsWith("/")) pic = IMG_CDN + pic;
            }

            String desc = "";
            Matcher dm = META_DESC_PATTERN.matcher(html);
            if (dm.find()) desc = dm.group(1).trim();

            // 提取播放线路和剧集（使用 Jsoup 替代正则，更鲁棒）
            Map<String, List<String>> episodesBySid = new LinkedHashMap<>();
            List<String> sidsInOrder = new ArrayList<>();

            for (Element ep : playDoc.select("a.episode-item")) {
                String href = ep.attr("href");
                Matcher pidm = PLAY_ID_PATTERN.matcher(href);
                if (!pidm.find()) continue;
                String sid = pidm.group(2);
                String text = ep.text().trim();
                if (TextUtils.isEmpty(text)) continue;
                episodesBySid.computeIfAbsent(sid, k -> new ArrayList<>()).add(text + "$" + href);
                if (!sidsInOrder.contains(sid)) sidsInOrder.add(sid);
            }

            // 备用：如果 episode-item 未匹配到，尝试所有 href 匹配 /play/xxx-xxx-xxx.html 的 <a> 标签
            if (sidsInOrder.isEmpty()) {
                for (Element ep : playDoc.select("a[href]")) {
                    String href = ep.attr("href");
                    Matcher pidm = PLAY_ID_PATTERN.matcher(href);
                    if (!pidm.find()) continue;
                    String sid = pidm.group(2);
                    String text = ep.text().trim();
                    if (TextUtils.isEmpty(text)) continue;
                    episodesBySid.computeIfAbsent(sid, k -> new ArrayList<>()).add(text + "$" + href);
                    if (!sidsInOrder.contains(sid)) sidsInOrder.add(sid);
                }
            }

            // 提取线路名称
            List<String> sourceLabels = new ArrayList<>();
            for (Element label : playDoc.select(".source-item-label")) {
                String l = label.text().trim();
                if (!TextUtils.isEmpty(l)) sourceLabels.add(l);
            }

            List<String> playFrom = new ArrayList<>();
            List<String> playUrl = new ArrayList<>();
            for (int i = 0; i < sidsInOrder.size(); i++) {
                String sid = sidsInOrder.get(i);
                List<String> eps = episodesBySid.get(sid);
                if (eps == null || eps.isEmpty()) continue;
                String lineName = i < sourceLabels.size() ? sourceLabels.get(i) : "线路" + sid;
                if ("4K".equals(lineName)) continue; // 跳过 4K 线路（仅 APP 可用）
                playFrom.add(lineName);
                playUrl.add(TextUtils.join("#", eps));
            }

            Vod vod = new Vod();
            vod.setVodId(vid);
            vod.setVodName(title);
            vod.setVodPic(pic);
            vod.setVodContent(desc);
            vod.setVodPlayFrom(TextUtils.join("$$$", playFrom));
            vod.setVodPlayUrl(TextUtils.join("$$$", playUrl));
            list.add(vod);
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        return Result.string(list);
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        return searchContent(key, quick, "1");
    }

    @Override
    public String searchContent(String key, boolean quick, String pg) throws Exception {
        int page = Util.toInt(pg, 1);
        List<Vod> list = new ArrayList<>();
        try {
            // 先访问搜索页获取 token
            String searchUrl = site + "/search?k=" + URLEncoder.encode(key, "UTF-8");
            String html = OkHttp.string(searchUrl, getHeaders());
            Matcher tm = TOKEN_PATTERN.matcher(html);
            String t = tm.find() ? tm.group(1) : "";

            String url = searchUrl;
            if (!TextUtils.isEmpty(t)) {
                url = site + "/search?k=" + URLEncoder.encode(key, "UTF-8") + "&t=" + URLEncoder.encode(t, "UTF-8");
                if (page > 1) url += "&page=" + page;
                html = OkHttp.string(url, getHeaders());
            }

            Document doc = Jsoup.parse(html);
            Elements items = doc.select(".search-result-item");
            for (Element item : items) {
                String href = item.attr("href");
                if (TextUtils.isEmpty(href)) {
                    Element aTag = item.selectFirst("a");
                    if (aTag != null) href = aTag.attr("href");
                }
                String vid = getVid(href);
                if (TextUtils.isEmpty(vid)) continue;

                String title = "";
                Element titleElem = item.selectFirst(".title");
                if (titleElem != null) title = titleElem.text().trim();
                if (TextUtils.isEmpty(title)) {
                    Element img = item.selectFirst("img");
                    if (img != null) {
                        title = img.attr("alt");
                        if (TextUtils.isEmpty(title)) title = img.attr("title");
                        title = title.trim();
                    }
                }

                String pic = "";
                Elements imgs = item.select("img");
                for (Element img : imgs) {
                    String src = img.attr("data-original");
                    if (TextUtils.isEmpty(src)) src = img.attr("data-src");
                    if (TextUtils.isEmpty(src)) src = img.attr("src");
                    if (!TextUtils.isEmpty(src) && !src.contains("placeholder") && !src.contains("logo_placeholder")) {
                        pic = src;
                        break;
                    }
                }
                pic = fixPic(pic);

                if (!TextUtils.isEmpty(title)) {
                    list.add(new Vod(vid, title, pic));
                }
            }
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        return Result.string(list);
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        try {
            String playUrl = id.startsWith("http") ? id : site + id;
            String html = OkHttp.string(playUrl, getHeaders());

            String videoUrl = "";
            // 多正则尝试
            for (Pattern pat : VIDEO_URL_PATTERNS) {
                Matcher m = pat.matcher(html);
                if (m.find()) {
                    videoUrl = m.group(1);
                    break;
                }
            }
            // 通用 URL 提取
            if (TextUtils.isEmpty(videoUrl)) {
                Matcher m = HTTP_MEDIA_PATTERN.matcher(html);
                while (m.find()) {
                    String u = m.group();
                    if (u.contains("index.m3u8") || u.contains("video.m3u8") || u.contains(".mp4")) {
                        videoUrl = u;
                        break;
                    }
                }
                if (TextUtils.isEmpty(videoUrl) && m.find()) videoUrl = m.group();
            }

            // 处理相对路径
            if (!TextUtils.isEmpty(videoUrl) && !videoUrl.startsWith("http")) {
                videoUrl = videoUrl.startsWith("/") ? site + videoUrl : site + "/" + videoUrl;
            }

            Map<String, String> headers = new HashMap<>();
            headers.put("User-Agent", USER_AGENT);
            headers.put("Referer", site + "/");

            if (!TextUtils.isEmpty(videoUrl)) {
                return Result.get().parse(0).url(videoUrl).header(headers).string();
            } else {
                return Result.get().parse(1).url(playUrl).header(headers).string();
            }
        } catch (Exception e) {
            SpiderDebug.log(e);
            Map<String, String> headers = new HashMap<>();
            headers.put("User-Agent", USER_AGENT);
            headers.put("Referer", site + "/");
            String url = id.startsWith("http") ? id : site + id;
            return Result.get().parse(1).url(url).header(headers).string();
        }
    }
}
