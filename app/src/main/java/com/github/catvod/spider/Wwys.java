package com.github.catvod.spider;

import android.content.Context;
import android.text.TextUtils;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.net.OkHttp;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import org.json.JSONObject;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 糯米影视 Spider - HTML 爬虫型
 * 解析视频列表、详情、播放链接
 */
public class Wwys extends Spider {

    private static final String BASE_URL = "https://vip.wwgz.cn:5200";
    private static final String USER_AGENT = "Mozilla/5.0 (Linux; Android 13; SM-A037U) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/112.0.0.0 Mobile Safari/537.36  uacq";
    private static final String PLAY_FROM = "糯米";

    private static final Pattern VOD_ID_PATTERN = Pattern.compile("vod-(?:type|list)-id-(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern MAC_URL_PATTERN = Pattern.compile("mac_url='([^']*)'");

    private String baseUrl = BASE_URL;

    public static boolean isValidUrl(String url) {
        if (TextUtils.isEmpty(url)) return false;
        return url.startsWith("http://") || url.startsWith("https://");
    }

    public static Vod extractVodFromElement(Element element, boolean isSearchResult) {
        // 提取图片
        String imgSelector = isSearchResult ? "div:nth-child(1) > a:nth-child(1) > img:nth-child(1)" : "div > img";
        Element imgElement = element.selectFirst(imgSelector);
        String pic = "";
        if (imgElement != null) {
            String attrKey = imgElement.hasAttr("data-src") ? "data-src" : "src";
            pic = imgElement.attr(attrKey);
        }

        // 提取链接和标题
        String linkSelector = isSearchResult ? "div:nth-child(1) > a:nth-child(1)" : "a";
        Element linkElement = element.selectFirst(linkSelector);
        if (linkElement == null) return null;

        String href = linkElement.attr("href");
        if (TextUtils.isEmpty(href)) href = "";
        else href = href.replaceAll("\\D+", "");

        // 提取标题
        String title;
        if (linkElement.hasAttr("title")) {
            title = linkElement.attr("title");
        } else {
            title = linkElement.text();
        }

        // 提取备注
        String remark = "";
        if (isSearchResult) {
            Element span1 = element.selectFirst("div:nth-child(2) > span:nth-child(1)");
            Element span3 = element.selectFirst("div:nth-child(2) > span:nth-child(3)");
            if (span1 != null && !TextUtils.isEmpty(span1.text())) {
                title = span1.text();
            }
            if (span3 != null) {
                remark = span3.text();
            }
        } else {
            Element sBottom = element.selectFirst("span.sBottom");
            if (sBottom == null) {
                sBottom = element.selectFirst("a > div > span > span");
            }
            if (sBottom != null) {
                remark = sBottom.text();
            }
            Element aWithTitle = element.selectFirst("a[title]");
            if (aWithTitle != null && aWithTitle.hasAttr("title")) {
                title = aWithTitle.attr("title");
            }
        }

        if (TextUtils.isEmpty(href)) return null;

        return new Vod(href, title, pic, remark);
    }

    public static String extractElementText(Element element) {
        if (element == null) return "";
        return element.text().trim();
    }

    private Map<String, String> buildHeader() {
        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", USER_AGENT);
        return headers;
    }

    public final Document fetchHtmlDocument(String url) {
        Map<String, String> headers = buildHeader();
        String html = OkHttp.string(url, headers);
        return Jsoup.parse(html);
    }

    public final String buildUrl(String path, String param) {
        if (TextUtils.isEmpty(path)) return "";
        if (TextUtils.isEmpty(param)) return path;

        String encodedParam = URLEncoder.encode(param, StandardCharsets.UTF_8.name());

        if (path.endsWith("=") || path.endsWith("&")) {
            return path + encodedParam;
        }

        if (path.startsWith("http")) {
            return path + encodedParam;
        }

        if (path.startsWith("/")) {
            return baseUrl + path + encodedParam;
        }

        return baseUrl + "/" + path + encodedParam;
    }

    public final String extractPatternGroup(Pattern pattern, String input) {
        if (TextUtils.isEmpty(input)) return "";
        Matcher matcher = pattern.matcher(input);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return "";
    }

    @Override
    public void init(Context context, String extend) throws Exception {
        baseUrl = BASE_URL;
        if (!TextUtils.isEmpty(extend)) {
            baseUrl = extend.trim();
            if (baseUrl.endsWith("/")) {
                baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
            }
        }
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        Document doc = fetchHtmlDocument(baseUrl);

        ArrayList<Class> classes = new ArrayList<>();
        Elements topNavItems = doc.select("#topnav > ul:nth-child(1) li");
        for (Element item : topNavItems) {
            Element link = item.selectFirst("a");
            if (link == null) continue;

            String href = link.attr("href");
            if (TextUtils.isEmpty(href)) continue;

            String cateId = "";
            Matcher matcher = VOD_ID_PATTERN.matcher(href);
            if (matcher.find()) {
                cateId = matcher.group(1);
            }

            if (TextUtils.isEmpty(cateId)) continue;

            String name = link.text();
            classes.add(new Class(cateId, name));
        }

        ArrayList<Vod> list = new ArrayList<>();
        Elements resizeLists = doc.select("section.mod:nth-child(3) > div:nth-child(2) ul.resize_list");
        if (resizeLists.isEmpty()) {
            resizeLists = doc.select("section.mod ul.resize_list");
        }

        for (Element ul : resizeLists) {
            Element img = ul.selectFirst("a div.pic img");
            if (img == null) {
                img = ul.selectFirst("div > img");
            }
            String pic = "";
            if (img != null) {
                String attrKey = img.hasAttr("data-src") ? "data-src" : "src";
                pic = img.attr(attrKey);
            }

            Element link = ul.selectFirst("a");
            if (link == null) continue;

            String href = link.attr("href");
            if (TextUtils.isEmpty(href)) href = "";
            else href = href.replaceAll("\\D+", "");

            String title;
            if (link.hasAttr("title")) {
                title = link.attr("title");
            } else {
                title = link.text();
            }

            Element span = ul.selectFirst("a > div > span > span");
            if (span == null) {
                span = ul.selectFirst("span.sBottom");
            }
            String remark = (span != null) ? span.text() : "";

            if (TextUtils.isEmpty(href)) continue;

            list.add(new Vod(href, title, pic, remark));
        }

        if (list.isEmpty()) {
            Elements items = doc.select(".resize_list > li");
            for (Element item : items) {
                Vod vod = extractVodFromElement(item, false);
                if (vod != null) {
                    list.add(vod);
                }
            }
        }

        if (filter) {
            String filtersJson = "{\"1\":[{\"key\":\"cateId\",\"name\":\"类型\",\"value\":[{\"n\":\"全部\",\"v\":\"1\"},{\"n\":\"动作片\",\"v\":\"5\"},{\"n\":\"喜剧片\",\"v\":\"6\"},{\"n\":\"爱情片\",\"v\":\"7\"},{\"n\":\"科幻片\",\"v\":\"8\"},{\"n\":\"恐怖片\",\"v\":\"9\"},{\"n\":\"剧情片\",\"v\":\"10\"},{\"n\":\"战争片\",\"v\":\"11\"},{\"n\":\"惊悚片\",\"v\":\"16\"},{\"n\":\"奇幻片\",\"v\":\"17\"}]}],\"2\":[{\"key\":\"cateId\",\"name\":\"类型\",\"value\":[{\"n\":\"全部\",\"v\":\"2\"},{\"n\":\"国产剧\",\"v\":\"12\"},{\"n\":\"港台剧\",\"v\":\"13\"},{\"n\":\"日韩剧\",\"v\":\"14\"},{\"n\":\"欧美剧\",\"v\":\"15\"}]}]}";
            JSONObject filters = new JSONObject(filtersJson);
            return Result.string(classes, list, filters);
        }

        return Result.string(classes, list);
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        if (extend != null && extend.containsKey("cateId")) {
            tid = extend.get("cateId");
        }

        String url = baseUrl + String.format("/vod-list-id-%s-pg-%s-order--by-time-class-0-year-0-letter--area--lang-.html", tid, pg);
        Document doc = fetchHtmlDocument(url);

        ArrayList<Vod> list = new ArrayList<>();
        Elements items = doc.select(".resize_list > li");
        for (Element item : items) {
            Vod vod = extractVodFromElement(item, false);
            if (vod != null) {
                list.add(vod);
            }
        }

        if (list.isEmpty()) {
            url = baseUrl + String.format("/vod-type-id-%s-pg-%s.html", tid, pg);
            doc = fetchHtmlDocument(url);
            items = doc.select(".resize_list > li");
            for (Element item : items) {
                Vod vod = extractVodFromElement(item, false);
                if (vod != null) {
                    list.add(vod);
                }
            }
        }

        return Result.string(list);
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        String vodId = ids.get(0);
        String url = baseUrl + "/vod-detail-id-" + vodId + ".html";
        Document doc = fetchHtmlDocument(url);

        // 提取标题
        Element titleElement = doc.selectFirst(".title > a:nth-child(1)");
        String title = (titleElement != null) ? titleElement.attr("title") : "";

        // 提取图片
        Element imgElement = doc.selectFirst(".page-hd > a:nth-child(1) > img:nth-child(1)");
        String pic = (imgElement != null) ? imgElement.attr("src") : "";

        // 提取详细信息
        String content = extractElementText(doc.selectFirst("div.desc_item:nth-child(2) > font:nth-child(2)"));
        String year = extractElementText(doc.selectFirst("div.desc_item:nth-child(3) > a"));
        String area = extractElementText(doc.selectFirst(".detail-con > p:nth-child(3)"));
        String typeName = extractElementText(doc.selectFirst("div.desc_item:nth-child(4) > a"));

        // 获取播放列表
        String playUrl = baseUrl + "/vod-play-id-" + vodId + "-src-1-num-1.html";
        Document playDoc = fetchHtmlDocument(playUrl);

        ArrayList<String> playList = new ArrayList<>();
        Elements episodeLinks = playDoc.select("ul.num-list li a, .playlist li a, div.num-tab a, .num-tabs a");

        if (episodeLinks.isEmpty()) {
            // 单片模式，从 HTML 中提取 mac_url
            String html = playDoc.html();
            String macUrl = extractPatternGroup(MAC_URL_PATTERN, html);
            if (!TextUtils.isEmpty(macUrl)) {
                playList.add("正片$" + macUrl);
            }
        } else {
            // 多集模式
            int episodeNum = 0;
            for (Element link : episodeLinks) {
                episodeNum++;

                // 提取集名
                String episodeName;
                if (link.hasAttr("title")) {
                    episodeName = link.attr("title");
                } else {
                    episodeName = link.text();
                }

                if (TextUtils.isEmpty(episodeName)) {
                    episodeName = "第" + episodeNum + "集";
                }

                // 清理集名中的特殊字符
                episodeName = episodeName.replace("$", "").replace("#", "").trim();

                // 提取播放 URL
                String href = link.attr("href");
                if (!TextUtils.isEmpty(href)) {
                    if (!href.startsWith("http") && !href.startsWith("/")) {
                        href = baseUrl + "/" + href;
                    } else if (href.startsWith("/")) {
                        href = baseUrl + href;
                    }

                    // 重新构建播放 URL
                    if (!href.startsWith("http")) {
                        href = baseUrl + "/vod-play-id-" + vodId + "-src-1-num-" + episodeNum + ".html";
                    }

                    Document episodeDoc = fetchHtmlDocument(href);
                    String episodeHtml = episodeDoc.html();
                    String episodeMacUrl = extractPatternGroup(MAC_URL_PATTERN, episodeHtml);

                    if (!TextUtils.isEmpty(episodeMacUrl)) {
                        playList.add(episodeName + "$" + episodeMacUrl);
                    }
                }
            }
        }

        String playUrls = (playList == null || playList.isEmpty()) ? "" : TextUtils.join("#", playList);

        if (TextUtils.isEmpty(playUrls)) {
            return Result.error("无 mac_url");
        }

        Vod vod = new Vod();
        vod.setVodId(vodId);
        vod.setVodName(title);
        vod.setVodPic(pic);
        vod.setVodContent(content);
        vod.setVodYear(year);
        vod.setVodArea(area);
        vod.setTypeName(typeName);
        vod.setVodPlayFrom(PLAY_FROM);
        vod.setVodPlayUrl(playUrls);

        return Result.string(vod);
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        if (TextUtils.isEmpty(id)) {
            return Result.error("播放 id 为空");
        }

        String playId = id.trim();
        // 处理 $ 分隔符（从详情页传递的播放地址）
        if (playId.contains("$")) {
            int lastDollarIndex = playId.lastIndexOf('$');
            playId = playId.substring(lastDollarIndex + 1);
        }
        playId = playId.trim();

        if (TextUtils.isEmpty(playId)) {
            return Result.error("播放 id 为空");
        }

        // 尝试解析播放地址
        String playUrl = "";

        // 第一种解析方式：wwgz.js
        String jsUrl1 = baseUrl + "/player/wwgz.js";
        String jsContent1 = OkHttp.string(jsUrl1, buildHeader());
        Pattern pattern1 = Pattern.compile("src=\"(.*?)'");
        String src1 = extractPatternGroup(pattern1, jsContent1);
        if (!TextUtils.isEmpty(src1)) {
            String fullUrl1 = buildUrl(src1, playId);
            String jsContent2 = OkHttp.string(fullUrl1, buildHeader());
            Pattern pattern2 = Pattern.compile("src\\s*=\\s*'([^']+)'\s*\\+\\s*videoUrl");
            String src2 = extractPatternGroup(pattern2, jsContent2);
            if (!TextUtils.isEmpty(src2)) {
                String fullUrl2 = buildUrl(src2, playId);
                String jsContent3 = OkHttp.string(fullUrl2, buildHeader());
                Pattern pattern3 = Pattern.compile("url: '([^']*)'");
                String finalUrl = extractPatternGroup(pattern3, jsContent3);
                if (isValidUrl(finalUrl)) {
                    playUrl = finalUrl;
                }
            }
        }

        // 第二种解析方式：lzm3u8.js（第一种失败时）
        if (TextUtils.isEmpty(playUrl)) {
            String jsUrl2 = baseUrl + "/player/lzm3u8.js";
            String jsContent4 = OkHttp.string(jsUrl2, buildHeader());
            Pattern pattern4 = Pattern.compile("src=\"(.*?)'");
            String src4 = extractPatternGroup(pattern4, jsContent4);
            if (!TextUtils.isEmpty(src4)) {
                String fullUrl4 = buildUrl(src4, playId);
                String jsContent5 = OkHttp.string(fullUrl4, buildHeader());
                Pattern pattern5 = Pattern.compile("\"url\"\\s*:\\s*\"([^\"]+)\"");
                String finalUrl2 = extractPatternGroup(pattern5, jsContent5);
                if (isValidUrl(finalUrl2)) {
                    playUrl = finalUrl2;
                }
            }
        }

        if (!isValidUrl(playUrl)) {
            return Result.error("解析播放地址失败");
        }

        Map<String, String> headers = buildHeader();
        Result result = Result.get()
                .url(playUrl)
                .parse(0)
                .header(headers);

        if (playUrl.contains(".m3u8")) {
            result.format("application/x-mpegURL");
        }

        return result.string();
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        String url = baseUrl + "/index.php?m=vod-search&wd=" + URLEncoder.encode(key, StandardCharsets.UTF_8.name());
        Document doc = fetchHtmlDocument(url);

        ArrayList<Vod> list = new ArrayList<>();
        Elements items = doc.select("#data_list > li");
        for (Element item : items) {
            Vod vod = extractVodFromElement(item, true);
            if (vod != null) {
                list.add(vod);
            }
        }

        return Result.string(list);
    }
}