package com.github.catvod.spider;

import android.content.Context;
import android.net.Uri;
import android.text.TextUtils;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Util;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 樱花动漫 Spider
 * <p>
 * 站点镜像：
 *   https://www.dmvvv.com  (主)
 *   https://www.vtt6.com   (备)
 * <p>
 * 模板特征：非标站点，无 MacCMS 标志
 *   - 列表页：{@code <ul><li><a href="/detail/{id}/" title="..."><img data-original="..."></a><div class="txt"><a>name</a><p>remarks</p></div></li></ul>}
 *   - 详情页：{@code <div class="detail">} + {@code <div class="playlist"><div class="tabs">} 多线路 + 多个 {@code <div class="row"><ul class="list6">}
 *   - 播放页：Artplayer 配置中 {@code url: 'https://...index.m3u8'}
 *   - 搜索页：{@code /search/?wd=xxx} 或 {@code /search/?wd=xxx&pageno=N}
 */
public class Yhdm extends Spider {

    private static final String DEFAULT_HOST = "https://www.dmvvv.com";

    private static final Pattern PLAYER_URL_PATTERN = Pattern.compile("url\\s*:\\s*'(https?://[^']+)'");
    private static final Pattern M3U8_FALLBACK_PATTERN = Pattern.compile("(https?://[^\\s'\"]+\\.m3u8(?:\\?[^\\s'\"]*)?)");
    private static final Pattern PAGE_NUMBER_PATTERN = Pattern.compile("/type/[^/]+/(\\d+)/");
    private static final Pattern SEARCH_TOTAL_PATTERN = Pattern.compile("找到\\s*<em>(\\d+)</em>");
    private static final Pattern SEARCH_PAGENO_PATTERN = Pattern.compile("pageno=(\\d+)");

    private String siteUrl = DEFAULT_HOST;

    private Map<String, String> getHeader() {
        Map<String, String> header = new HashMap<>();
        header.put("User-Agent", Util.CHROME);
        header.put("Referer", siteUrl + "/");
        header.put("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
        header.put("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");
        return header;
    }

    @Override
    public void init(Context context, String extend) throws Exception {
        super.init(context);
        if (TextUtils.isEmpty(extend)) return;
        String ext = extend.trim();
        if (ext.startsWith("http")) {
            siteUrl = ext.replaceAll("/+$", "");
        }
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        List<Class> classes = new ArrayList<>();
        List<String> typeIds = Arrays.asList("guoman", "riman", "oman", "dmfilm");
        List<String> typeNames = Arrays.asList("国产动漫", "日本动漫", "欧美动漫", "动漫电影");
        for (int i = 0; i < typeIds.size(); i++) {
            classes.add(new Class(typeIds.get(i), typeNames.get(i)));
        }
        List<Vod> list = parseHomeList(OkHttp.string(siteUrl, getHeader()));
        return Result.string(classes, list);
    }

    @Override
    public String homeVideoContent() throws Exception {
        List<Vod> list = parseHomeList(OkHttp.string(siteUrl, getHeader()));
        return Result.string(list);
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        int page = Util.toInt(pg, 1);
        String cateUrl = page <= 1
                ? String.format("%s/type/%s/", siteUrl, tid)
                : String.format("%s/type/%s/%d/", siteUrl, tid, page);
        String html = OkHttp.string(cateUrl, getHeader());
        List<Vod> list = parseListItems(html);
        int pageCount = parseCategoryPageCount(html, page);
        int limit = 36;
        int total = list.size() >= limit ? pageCount * limit : list.size();
        return Result.get().vod(list).page(page, pageCount, limit, total).string();
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        String detailUrl = fixUrl(ids.get(0));
        String html = OkHttp.string(detailUrl, getHeader());
        Document doc = Jsoup.parse(html);

        Element coverImg = doc.selectFirst(".detail .cover img");
        String vodPic = coverImg != null ? coverImg.attr("data-original") : "";

        String vodName = "";
        Element h2 = doc.selectFirst(".detail h2");
        if (h2 != null) vodName = h2.text().trim();
        if (TextUtils.isEmpty(vodName)) {
            Element titleEl = doc.selectFirst("title");
            if (titleEl != null) vodName = titleEl.text().split("-")[0].trim();
        }

        String state = getInfoByLabel(html, "状态", true);
        String year = getInfoByLabel(html, "年份", false);
        String area = getInfoByLabel(html, "地区", false);
        String type = getInfoByLabel(html, "类型", false);
        String actor = getInfoByLabel(html, "主演", false);

        String vodContent = "";
        Element blurb = doc.selectFirst("li.blurb");
        if (blurb != null) vodContent = blurb.text().replace("简介：", "").trim();

        // 解析多线路播放列表
        Elements tabs = doc.select(".playlist .tabs a");
        Elements rows = doc.select(".playlist .row");
        StringBuilder vodPlayFrom = new StringBuilder();
        StringBuilder vodPlayUrl = new StringBuilder();
        int lineCount = Math.min(tabs.size(), rows.size());
        for (int i = 0; i < lineCount; i++) {
            String lineName = tabs.get(i).text().trim();
            if (TextUtils.isEmpty(lineName)) lineName = "线路" + (i + 1);
            vodPlayFrom.append(lineName).append("$$$");
            Elements episodes = rows.get(i).select("ul.list6 li a");
            for (int j = 0; j < episodes.size(); j++) {
                Element a = episodes.get(j);
                String epName = a.text().trim();
                String epUrl = a.attr("href");
                vodPlayUrl.append(epName).append("$").append(epUrl);
                vodPlayUrl.append(j < episodes.size() - 1 ? "#" : "$$$");
            }
            if (episodes.isEmpty()) {
                // 空线路也要补 $$$ 与 vodPlayFrom 对齐
                vodPlayUrl.append("$$$");
            }
        }

        Vod vod = new Vod();
        vod.setVodId(ids.get(0));
        vod.setVodName(vodName);
        vod.setVodPic(vodPic);
        vod.setVodYear(year);
        vod.setVodArea(area);
        vod.setVodRemarks(state);
        vod.setTypeName(type);
        vod.setVodActor(actor);
        vod.setVodContent(vodContent);
        vod.setVodPlayFrom(vodPlayFrom.toString());
        vod.setVodPlayUrl(vodPlayUrl.toString());
        return Result.string(vod);
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        return searchContent(key, quick, "1");
    }

    @Override
    public String searchContent(String key, boolean quick, String pg) throws Exception {
        int page = Util.toInt(pg, 1);
        String encodedKey = Uri.encode(key);
        String searchUrl = page <= 1
                ? String.format("%s/search/?wd=%s", siteUrl, encodedKey)
                : String.format("%s/search/?wd=%s&pageno=%d", siteUrl, encodedKey, page);
        String html = OkHttp.string(searchUrl, getHeader());
        List<Vod> list = new ArrayList<>();
        Document doc = Jsoup.parse(html);
        for (Element li : doc.select("ul > li")) {
            Element cover = li.selectFirst("a.cover");
            if (cover == null) continue;
            String href = cover.attr("href");
            String title = cover.attr("title");
            if (TextUtils.isEmpty(href) || !href.contains("/detail/") || TextUtils.isEmpty(title)) continue;

            String pic = "";
            Element img = li.selectFirst("img");
            if (img != null) pic = img.attr("data-original");
            if (TextUtils.isEmpty(pic)) pic = img != null ? img.attr("src") : "";

            String remarks = "";
            Element stateItem = li.selectFirst("div.item");
            if (stateItem != null) {
                remarks = stateItem.text().replace("状态：", "").trim();
            }
            list.add(new Vod(href, title.trim(), pic, remarks));
        }

        int pageCount = parseSearchPageCount(html, page, list.size());
        int limit = 12;
        int total = parseSearchTotal(html);
        if (total <= 0) total = list.size() >= limit ? pageCount * limit : list.size();
        return Result.get().vod(list).page(page, pageCount, limit, total).string();
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        String playUrl = fixUrl(id);
        String html = OkHttp.string(playUrl, getHeader());

        String realUrl = "";
        Matcher m = PLAYER_URL_PATTERN.matcher(html);
        if (m.find()) {
            realUrl = m.group(1);
        } else {
            Matcher m3u8 = M3U8_FALLBACK_PATTERN.matcher(html);
            if (m3u8.find()) realUrl = m3u8.group(1);
        }

        Map<String, String> header = new HashMap<>();
        header.put("User-Agent", Util.CHROME);
        header.put("Referer", siteUrl + "/");
        return Result.get().url(realUrl).header(header).string();
    }

    // ==================== 辅助方法 ====================

    /**
     * 解析首页/分类页通用列表项
     * 结构：{@code <li><a href="/detail/{id}/" title="..."><img data-original="..."></a><div class="txt"><a>name</a><p>remarks</p></div></li>}
     */
    private List<Vod> parseHomeList(String html) {
        return parseListItems(html);
    }

    private List<Vod> parseListItems(String html) {
        List<Vod> list = new ArrayList<>();
        Document doc = Jsoup.parse(html);
        for (Element li : doc.select("ul > li")) {
            Element link = li.selectFirst("a[href*=/detail/]");
            if (link == null) continue;
            String href = link.attr("href");
            String title = link.attr("title");
            if (TextUtils.isEmpty(title)) title = link.text().trim();
            if (TextUtils.isEmpty(href) || TextUtils.isEmpty(title)) continue;

            String pic = "";
            Element img = li.selectFirst("img");
            if (img != null) {
                pic = img.attr("data-original");
                if (TextUtils.isEmpty(pic)) pic = img.attr("src");
            }
            String remark = "";
            Element p = li.selectFirst("p");
            if (p != null) remark = p.text().trim();
            list.add(new Vod(href, title, pic, remark));
        }
        return list;
    }

    /**
     * 详情页信息项提取
     *
     * @param label 标签名（如"状态"、"年份"）
     * @param useEm 状态项使用 {@code <em>} 包裹值，其他项为纯文本
     */
    private String getInfoByLabel(String html, String label, boolean useEm) {
        String pattern = useEm
                ? "<span>" + label + "：</span>\\s*<em>([^<]+)</em>"
                : "<span>" + label + "：</span>([^<]+)";
        Matcher m = Pattern.compile(pattern).matcher(html);
        return m.find() ? m.group(1).trim() : "";
    }

    /**
     * 分类页总页数：从分页链接中提取最大页码
     */
    private int parseCategoryPageCount(String html, int currentPage) {
        int maxPage = currentPage;
        Matcher m = PAGE_NUMBER_PATTERN.matcher(html);
        while (m.find()) {
            try {
                maxPage = Math.max(maxPage, Integer.parseInt(m.group(1)));
            } catch (NumberFormatException ignored) {
            }
        }
        return maxPage;
    }

    /**
     * 搜索结果总数
     */
    private int parseSearchTotal(String html) {
        Matcher m = SEARCH_TOTAL_PATTERN.matcher(html);
        if (m.find()) {
            try {
                return Integer.parseInt(m.group(1));
            } catch (NumberFormatException ignored) {
            }
        }
        return 0;
    }

    /**
     * 搜索结果总页数：优先从 pageno 链接提取，否则根据当前页+条目数推算
     */
    private int parseSearchPageCount(String html, int currentPage, int listSize) {
        int maxPage = currentPage;
        Matcher m = SEARCH_PAGENO_PATTERN.matcher(html);
        while (m.find()) {
            try {
                maxPage = Math.max(maxPage, Integer.parseInt(m.group(1)));
            } catch (NumberFormatException ignored) {
            }
        }
        if (maxPage == currentPage && listSize >= 12) {
            maxPage = currentPage + 1;
        }
        return maxPage;
    }

    /**
     * 补全相对 URL 为绝对 URL
     */
    private String fixUrl(String url) {
        if (TextUtils.isEmpty(url)) return "";
        if (url.startsWith("http")) return url;
        if (url.startsWith("//")) return "https:" + url;
        return siteUrl + (url.startsWith("/") ? url : "/" + url);
    }
}
