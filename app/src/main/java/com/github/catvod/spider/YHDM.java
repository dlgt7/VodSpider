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
 *   - 列表页：{@code <ul><li><a href="/detail/{id}/" title="..."><img data-original="..."></a><p>remarks</p></li></ul>}
 *   - 详情页：多线路结构，线路索引从播放URL中提取
 *   - 播放页：Artplayer 配置中 {@code url: 'https://...index.m3u8'}
 *   - 搜索页：{@code /search/?wd=xxx} 或 {@code /search/?wd=xxx&pageno=N}
 */
public class YHDM extends Spider {

    private static final String DEFAULT_HOST = "https://www.dmvvv.com";
    private static final List<String> SOURCE_NAMES = Arrays.asList("高清", "ikun", "非凡", "量子");

    private static final Pattern PLAYER_URL_PATTERN = Pattern.compile("url\\s*:\\s*['\"]([^'\"]+\\.m3u8[^'\"]*)['\"]");
    private static final Pattern M3U8_FALLBACK_PATTERN = Pattern.compile("(https?://[^\\s'\"]+\\.m3u8(?:\\?[^\\s'\"]*)?)");
    private static final Pattern PAGE_NUMBER_PATTERN = Pattern.compile("/type/[^/]+/(\\d+)/");
    private static final Pattern SEARCH_TOTAL_PATTERN = Pattern.compile("找到\\s*<em>(\\d+)</em>");
    private static final Pattern EPISODE_COUNT_PATTERN = Pattern.compile("[共全更新至第]*(\\d+)[集话章]");
    private static final Pattern VOD_ID_PATTERN = Pattern.compile("/(\\d+)/?$");

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
        String videoId = ids.get(0);
        String detailUrl = fixUrl(videoId);
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

        // 提取视频数字ID
        String vodIdNum = extractVodId(videoId);

        // 解析总集数
        int totalEpisodes = parseEpisodeCount(state);

        // 尝试从HTML解析线路（优先）
        List<String> playFromList = new ArrayList<>();
        List<String> playUrlList = new ArrayList<>();
        parsePlayListFromHtml(doc, playFromList, playUrlList);

        // 如果HTML解析失败，使用URL测试方式
        if (playFromList.isEmpty() && !TextUtils.isEmpty(vodIdNum)) {
            testAndBuildPlayList(vodIdNum, totalEpisodes, playFromList, playUrlList);
        }

        Vod vod = new Vod();
        vod.setVodId(videoId);
        vod.setVodName(vodName);
        vod.setVodPic(vodPic);
        vod.setVodYear(year);
        vod.setVodArea(area);
        vod.setVodRemarks(state);
        vod.setTypeName(type);
        vod.setVodActor(actor);
        vod.setVodContent(vodContent);
        if (!playFromList.isEmpty()) {
            vod.setVodPlayFrom(TextUtils.join("$$$", playFromList));
            vod.setVodPlayUrl(TextUtils.join("$$$", playUrlList));
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
        String encodedKey = Uri.encode(key);
        String searchUrl = page <= 1
                ? String.format("%s/search/?wd=%s", siteUrl, encodedKey)
                : String.format("%s/search/?wd=%s&pageno=%d", siteUrl, encodedKey, page);
        String html = OkHttp.string(searchUrl, getHeader());
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

    private String getInfoByLabel(String html, String label, boolean useEm) {
        String pattern = useEm
                ? "<span>" + label + "：</span>\\s*<em>([^<]+)</em>"
                : "<span>" + label + "：</span>([^<]+)";
        Matcher m = Pattern.compile(pattern).matcher(html);
        return m.find() ? m.group(1).trim() : "";
    }

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

    private int parseSearchPageCount(String html, int currentPage, int listSize) {
        int maxPage = currentPage;
        Matcher m = Pattern.compile("pageno=(\\d+)").matcher(html);
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

    private String fixUrl(String url) {
        if (TextUtils.isEmpty(url)) return "";
        if (url.startsWith("http")) return url;
        if (url.startsWith("//")) return "https:" + url;
        return siteUrl + (url.startsWith("/") ? url : "/" + url);
    }

    private String extractVodId(String videoId) {
        Matcher m = VOD_ID_PATTERN.matcher(videoId);
        return m.find() ? m.group(1) : "";
    }

    private int parseEpisodeCount(String state) {
        if (TextUtils.isEmpty(state)) return 24;
        Matcher m = EPISODE_COUNT_PATTERN.matcher(state);
        return m.find() ? Integer.parseInt(m.group(1)) : 24;
    }

    /**
     * 从HTML解析播放列表（优先方案）
     * 使用正则表达式直接提取，避免 Jsoup 选择器可能的兼容性问题
     */
    private void parsePlayListFromHtml(Document doc, List<String> playFromList, List<String> playUrlList) {
        // 方法1：尝试 Jsoup 选择器
        Elements tabs = doc.select(".playlist .tabs a");
        Elements rows = doc.select(".playlist .row");
        
        if (!tabs.isEmpty() && !rows.isEmpty()) {
            int lineCount = Math.min(tabs.size(), rows.size());
            for (int i = 0; i < lineCount; i++) {
                String lineName = tabs.get(i).text().trim();
                if (TextUtils.isEmpty(lineName)) lineName = "线路" + (i + 1);
                playFromList.add(lineName);
                Elements episodes = rows.get(i).select("ul.list6 li a");
                StringBuilder eps = new StringBuilder();
                for (int j = 0; j < episodes.size(); j++) {
                    Element a = episodes.get(j);
                    String epName = a.text().trim();
                    String epUrl = a.attr("href");
                    if (j > 0) eps.append("#");
                    eps.append(epName).append("$").append(epUrl);
                }
                playUrlList.add(eps.toString());
            }
            return;
        }
        
        // 方法2：直接从 Document 的 HTML 字符串中用正则提取
        String html = doc.html();
        
        // 提取线路名（处理嵌套的 <i> 标签）
        Pattern tabPattern = Pattern.compile("<div[^>]+class=\"tabs\"[^>]*>(.*?)</div>", Pattern.DOTALL);
        Matcher tabMatcher = tabPattern.matcher(html);
        if (tabMatcher.find()) {
            String tabsHtml = tabMatcher.group(1);
            // 匹配 <a> 标签，提取其所有文本（包括嵌套标签后的文本）
            Pattern aPattern = Pattern.compile("<a[^>]*>(.*?)</a>", Pattern.DOTALL);
            Matcher aMatcher = aPattern.matcher(tabsHtml);
            while (aMatcher.find()) {
                String aContent = aMatcher.group(1);
                // 移除所有 HTML 标签，只保留文本
                String lineName = aContent.replaceAll("<[^>]+>", "").trim();
                if (!TextUtils.isEmpty(lineName)) {
                    playFromList.add(lineName);
                }
            }
        }
        
        // 提取剧集列表
        Pattern rowPattern = Pattern.compile("<div[^>]+class=\"row\"[^>]*>.*?<ul[^>]+class=\"list6\"[^>]*>(.*?)</ul>.*?</div>", Pattern.DOTALL);
        Matcher rowMatcher = rowPattern.matcher(html);
        while (rowMatcher.find()) {
            String rowHtml = rowMatcher.group(1);
            Pattern epPattern = Pattern.compile("<a[^>]+href=\"([^\"]+)\"[^>]*>([^<]+)</a>");
            Matcher epMatcher = epPattern.matcher(rowHtml);
            StringBuilder eps = new StringBuilder();
            while (epMatcher.find()) {
                String epUrl = epMatcher.group(1);
                String epName = epMatcher.group(2).trim();
                if (eps.length() > 0) eps.append("#");
                eps.append(epName).append("$").append(epUrl);
            }
            if (eps.length() > 0) {
                playUrlList.add(eps.toString());
            }
        }
        
        // 对齐线路名和剧集列表数量
        while (playFromList.size() > playUrlList.size()) {
            playUrlList.add("");
        }
        while (playUrlList.size() > playFromList.size()) {
            playFromList.add("线路" + (playFromList.size() + 1));
        }
    }

    /**
     * 通过URL测试方式构建播放列表（备用方案）
     * 参考樱花动漫.js的实现逻辑
     */
    private void testAndBuildPlayList(String vodId, int totalEpisodes, List<String> playFromList, List<String> playUrlList) {
        int[] sourceIndices = {1, 3, 4, 2}; // 播放URL中的实际线路索引顺序
        for (int i = 0; i < SOURCE_NAMES.size(); i++) {
            int sourceIdx = sourceIndices[i];
            String testName = SOURCE_NAMES.get(i);

            // 测试第一集是否可访问
            String testUrl = String.format("%s/play/%s-%d-1/", siteUrl, vodId, sourceIdx);
            try {
                String testHtml = OkHttp.string(testUrl, getHeader());
                // 检查是否包含播放器配置
                if (!TextUtils.isEmpty(testHtml) && (testHtml.contains("url:") || testHtml.contains(".m3u8"))) {
                    // 构造剧集列表
                    StringBuilder eps = new StringBuilder();
                    for (int ep = 1; ep <= totalEpisodes; ep++) {
                        String epName = ep < 10 ? "第0" + ep + "集" : "第" + ep + "集";
                        String epUrl = String.format("/play/%s-%d-%d/", vodId, sourceIdx, ep);
                        if (eps.length() > 0) eps.append("#");
                        eps.append(epName).append("$").append(epUrl);
                    }
                    playFromList.add(testName);
                    playUrlList.add(eps.toString());
                }
            } catch (Exception ignored) {
            }
        }
    }
}