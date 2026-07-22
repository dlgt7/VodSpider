package com.github.catvod.spider;

import android.content.Context;
import android.text.TextUtils;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Filter;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Util;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 策驰影院 Spider
 * 站点地址发布页: https://www.cechi.net/
 * 可用域名: cechiv.com / cechiw.com / cechi888.com
 * 播放页内嵌 mac_url 变量(unescape编码)包含全线路m3u8直链，detailContent一次性提取
 */
public class Ccys extends Spider {

    private static final String DEFAULT_HOST = "https://www.cechiv.com";

    private static final Pattern DETAIL_ID_PATTERN = Pattern.compile("/vod-detail-id-(\\d+)\\.html");
    private static final Pattern PAGE_INFO_PATTERN = Pattern.compile("当前:(\\d+)/(\\d+)页");
    private static final Pattern MAC_FROM_PATTERN = Pattern.compile("mac_from\\s*=\\s*['\"]([^'\"]+)['\"]");
    private static final Pattern MAC_URL_PATTERN = Pattern.compile("mac_url\\w*\\s*=\\s*unescape\\(\\s*['\"]([^'\"]+)['\"]\\s*\\)");

    private String siteUrl = DEFAULT_HOST;

    private Map<String, String> getHeader() {
        Map<String, String> header = new HashMap<>();
        header.put("User-Agent", Util.CHROME);
        header.put("Referer", siteUrl + "/");
        return header;
    }

    @Override
    public void init(Context context, String extend) throws Exception {
        super.init(context);
        if (!TextUtils.isEmpty(extend)) {
            String ext = extend.trim();
            if (ext.startsWith("{")) {
                try {
                    org.json.JSONObject json = new org.json.JSONObject(ext);
                    String host = json.optString("host", "");
                    if (!host.isEmpty()) siteUrl = host;
                } catch (Exception ignored) {
                }
            } else if (ext.startsWith("http")) {
                siteUrl = ext;
            }
        }
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        List<Class> classes = new ArrayList<>();
        classes.add(new Class("1", "电影"));
        classes.add(new Class("2", "电视剧"));
        classes.add(new Class("3", "综艺"));
        classes.add(new Class("4", "动漫"));
        classes.add(new Class("34", "短剧"));
        classes.add(new Class("20", "理论片"));
        Document doc = Jsoup.parse(OkHttp.string(siteUrl, getHeader()));
        List<Vod> list = parseList(doc);
        if (filter) {
            return Result.string(classes, list, buildFilters());
        }
        return Result.string(classes, list);
    }

    @Override
    public String homeVideoContent() throws Exception {
        Document doc = Jsoup.parse(OkHttp.string(siteUrl, getHeader()));
        return Result.string(parseList(doc));
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        int page = Util.toInt(pg, 1);
        String by = getExtend(extend, "by", "");
        String year = getExtend(extend, "year", "0");
        String area = getExtend(extend, "area", "");
        String areaEncoded = area.isEmpty() ? "" : URLEncoder.encode(area, "UTF-8");
        String url = siteUrl + String.format("/vod-list-id-%s-pg-%d-order-desc-by-%s-class-0-year-%s-letter--area-%s-lang--.html",
                tid, page, by, year, areaEncoded);
        String html = OkHttp.string(url, getHeader());
        Document doc = Jsoup.parse(html);
        List<Vod> list = parseList(doc);
        int pageCount = 1;
        int total = 0;
        Matcher m = PAGE_INFO_PATTERN.matcher(html);
        if (m.find()) {
            pageCount = Util.toInt(m.group(2), 1);
        }
        Matcher tm = Pattern.compile("共(\\d+)条数据").matcher(html);
        if (tm.find()) {
            total = Util.toInt(tm.group(1), 0);
        }
        int limit = list.size();
        if (limit == 0) limit = 30;
        return Result.string(page, pageCount, limit, total, list);
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        String vid = ids.get(0);
        String detailUrl = siteUrl + "/vod-detail-id-" + vid + ".html";
        String detailHtml = OkHttp.string(detailUrl, getHeader());
        Document doc = Jsoup.parse(detailHtml);

        Vod vod = new Vod();
        vod.setVodId(vid);
        Element h1 = doc.selectFirst("dt.name h1");
        if (h1 != null) vod.setVodName(h1.text());
        Element img = doc.selectFirst("img.lazy[data-original]");
        if (img != null) vod.setVodPic(fixUrl(img.attr("data-original")));

        String actor = extractDtText(doc, "主演");
        if (!actor.isEmpty()) vod.setVodActor(actor);
        String director = extractDtText(doc, "导演");
        if (!director.isEmpty()) vod.setVodDirector(director);
        String type = extractDtLink(doc, "类型");
        if (!type.isEmpty()) vod.setTypeName(type);
        String year = extractDdText(doc, "年份");
        if (!year.isEmpty()) vod.setVodYear(year);
        String area = extractDdText(doc, "发行");
        if (!area.isEmpty()) vod.setVodArea(area);
        Element contentP = doc.selectFirst("div.ee p");
        if (contentP != null) vod.setVodContent(contentP.text());
        String status = extractDtText(doc, "状态");
        if (!status.isEmpty()) vod.setVodRemarks(status);

        List<String> sourceNames = new ArrayList<>();
        for (Element li : doc.select("div.playfrom li")) {
            sourceNames.add(li.text());
        }

        List<String> episodeUrls = new ArrayList<>();
        for (Element a : doc.select("div.playlist a[href*=/vod-play-id-]")) {
            episodeUrls.add(a.attr("href"));
        }
        if (episodeUrls.isEmpty()) {
            episodeUrls.add("/vod-play-id-" + vid + "-src-1-num-1.html");
        }

        if (tryExtractM3u8(episodeUrls.get(0), sourceNames, vod)) {
            // vod播放字段已由 tryExtractM3u8 填充
        } else {
            vod.setVodPlayFrom(String.join("$$$", sourceNames.isEmpty() ? Arrays.asList("默认") : sourceNames));
            vod.setVodPlayUrl(buildPlayUrlFromDetail(doc));
        }

        return Result.string(vod);
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        return searchContent(key, quick, "1");
    }

    @Override
    public String searchContent(String key, boolean quick, String pg) throws Exception {
        int page = Util.toInt(pg, 1);
        String encodedKey = URLEncoder.encode(key, "UTF-8");
        String url = siteUrl + "/vod-search-pg-" + page + "-wd-" + encodedKey + ".html";
        String html = OkHttp.string(url, getHeader());
        Document doc = Jsoup.parse(html);
        List<Vod> list = parseList(doc);
        return Result.string(list);
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        if (id.startsWith("http")) {
            return Result.get().parse(0).url(id).header(getHeader()).string();
        }
        return Result.get().parse(1).url(fixUrl(id)).header(getHeader()).string();
    }

    // ==================== Helper Methods ====================

    private String getExtend(HashMap<String, String> extend, String key, String def) {
        if (extend == null) return def;
        String val = extend.get(key);
        return TextUtils.isEmpty(val) ? def : val;
    }

    private String fixUrl(String url) {
        if (TextUtils.isEmpty(url)) return "";
        if (url.startsWith("http")) return url;
        if (url.startsWith("//")) return "https:" + url;
        return siteUrl + url;
    }

    /**
     * 解析视频列表，自动按 vod_id 去重(翻转卡片有正反两面重复项)
     */
    private List<Vod> parseList(Document doc) {
        List<Vod> list = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Element a : doc.select("a.link-hover[href*=/vod-detail-id-]")) {
            Matcher m = DETAIL_ID_PATTERN.matcher(a.attr("href"));
            if (!m.find()) continue;
            String vid = m.group(1);
            if (seen.contains(vid)) continue;
            seen.add(vid);
            String name = a.attr("title");
            if (name.isEmpty()) name = a.text();
            Element img = a.selectFirst("img[data-original]");
            String pic = img != null ? img.attr("data-original") : "";
            Element other = a.selectFirst(".other");
            String remark = other != null ? other.text() : "";
            list.add(new Vod(vid, name, fixUrl(pic), remark));
        }
        return list;
    }

    /**
     * 提取 dt 中某标签后的纯文本(主演、导演等)，去除标签前缀
     */
    private String extractDtText(Document doc, String label) {
        for (Element dt : doc.select("dt")) {
            Element span = dt.selectFirst("span");
            if (span != null && span.text().contains(label)) {
                return dt.text().replace(span.text(), "").trim();
            }
        }
        return "";
    }

    /**
     * 提取 dt 中某标签后的链接文本(类型等)
     */
    private String extractDtLink(Document doc, String label) {
        for (Element dt : doc.select("dt")) {
            Element span = dt.selectFirst("span");
            if (span != null && span.text().contains(label)) {
                Element a = dt.selectFirst("a");
                return a != null ? a.text() : "";
            }
        }
        return "";
    }

    /**
     * 提取 dd 中某标签后的纯文本(年份、发行等)，去除标签前缀
     */
    private String extractDdText(Document doc, String label) {
        for (Element dd : doc.select("dd")) {
            Element span = dd.selectFirst("span");
            if (span != null && span.text().contains(label)) {
                return dd.text().replace(span.text(), "").trim();
            }
        }
        return "";
    }

    /**
     * 从详情页构建播放列表(回退方案：使用播放页URL，parse=1)
     */
    private String buildPlayUrlFromDetail(Document doc) {
        List<String> sources = new ArrayList<>();
        Elements playlists = doc.select("div[id^=stab][class=playlist]");
        if (playlists.isEmpty()) playlists = doc.select("div.playlist");
        for (Element playlist : playlists) {
            List<String> eps = new ArrayList<>();
            for (Element a : playlist.select("a[href*=/vod-play-id-]")) {
                eps.add(a.text() + "$" + a.attr("href"));
            }
            if (!eps.isEmpty()) sources.add(String.join("#", eps));
        }
        return String.join("$$$", sources);
    }

    /**
     * 尝试从播放页提取全部线路m3u8直链，成功返回true并填充vod的播放字段
     */
    private boolean tryExtractM3u8(String firstPlayHref, List<String> tabNames, Vod vod) {
        try {
            String playUrl = fixUrl(firstPlayHref);
            String html = OkHttp.string(playUrl, getHeader());

            Matcher urlMatcher = MAC_URL_PATTERN.matcher(html);
            if (!urlMatcher.find()) return false;
            String decoded = unescape(urlMatcher.group(1));

            Matcher fromMatcher = MAC_FROM_PATTERN.matcher(html);
            List<String> fromNames = new ArrayList<>();
            if (fromMatcher.find()) {
                fromNames = Arrays.asList(fromMatcher.group(1).split("\\$\\$\\$"));
            }

            String[] sourceGroups = decoded.split("\\$\\$\\$", -1);
            List<String> playFromList = new ArrayList<>();
            List<String> playUrlList = new ArrayList<>();

            for (int i = 0; i < sourceGroups.length; i++) {
                String group = sourceGroups[i];
                if (TextUtils.isEmpty(group)) continue;
                String[] episodes = group.split("#", -1);
                List<String> epList = new ArrayList<>();
                for (String ep : episodes) {
                    if (TextUtils.isEmpty(ep)) continue;
                    int dollarIdx = ep.indexOf('$');
                    if (dollarIdx < 0) continue;
                    String epName = ep.substring(0, dollarIdx);
                    String epUrl = ep.substring(dollarIdx + 1);
                    if (epUrl.contains(".m3u8") || epUrl.contains(".mp4")) {
                        epList.add(epName + "$" + epUrl);
                    }
                }
                if (epList.isEmpty()) continue;
                String name;
                if (i < tabNames.size() && !tabNames.get(i).isEmpty()) {
                    name = tabNames.get(i);
                } else if (i < fromNames.size()) {
                    name = fromNames.get(i);
                } else {
                    name = "线路" + (i + 1);
                }
                playFromList.add(name);
                playUrlList.add(String.join("#", epList));
            }
            if (playFromList.isEmpty()) return false;
            vod.setVodPlayFrom(String.join("$$$", playFromList));
            vod.setVodPlayUrl(String.join("$$$", playUrlList));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 解码 JavaScript unescape 格式: %uXXXX → Unicode字符, %XX → ASCII字符
     */
    private String unescape(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '%' && i + 1 < s.length()) {
                if (s.charAt(i + 1) == 'u' && i + 5 < s.length()) {
                    try {
                        sb.append((char) Integer.parseInt(s.substring(i + 2, i + 6), 16));
                        i += 6;
                        continue;
                    } catch (NumberFormatException ignored) {
                    }
                } else if (i + 2 < s.length()) {
                    try {
                        sb.append((char) Integer.parseInt(s.substring(i + 1, i + 3), 16));
                        i += 3;
                        continue;
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
            sb.append(c);
            i++;
        }
        return sb.toString();
    }

    /**
     * 构建分类筛选条件(地区/年份/排序)
     */
    private LinkedHashMap<String, List<Filter>> buildFilters() {
        List<Filter.Value> areas = new ArrayList<>();
        areas.add(new Filter.Value("全部", ""));
        for (String area : Arrays.asList("大陆", "香港", "台湾", "美国", "韩国", "日本", "泰国", "英国", "法国", "印度", "其它")) {
            areas.add(new Filter.Value(area, area));
        }
        List<Filter.Value> years = new ArrayList<>();
        years.add(new Filter.Value("全部", "0"));
        for (int y = 2025; y >= 2013; y--) {
            years.add(new Filter.Value(String.valueOf(y), String.valueOf(y)));
        }
        List<Filter.Value> sorts = new ArrayList<>();
        sorts.add(new Filter.Value("最新", "time"));
        sorts.add(new Filter.Value("人气", "hits"));
        sorts.add(new Filter.Value("评分", "score"));

        LinkedHashMap<String, List<Filter>> filters = new LinkedHashMap<>();
        for (String tid : Arrays.asList("1", "2", "3", "4", "34", "20")) {
            List<Filter> filterList = new ArrayList<>();
            filterList.add(new Filter("area", "地区", areas));
            filterList.add(new Filter("year", "年份", years));
            filterList.add(new Filter("by", "排序", sorts));
            filters.put(tid, filterList);
        }
        return filters;
    }
}
