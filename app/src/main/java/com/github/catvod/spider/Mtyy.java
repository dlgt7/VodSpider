package com.github.catvod.spider;

import android.content.Context;
import android.text.TextUtils;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Filter;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Util;

import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 麦田影院爬虫。
 * MacCMS v10 非标准模板，页面爬取型实现。
 * 站点地址：https://www.mtyy1.cc （发布网址：https://www.mtyy.tv/）
 * 支持镜像：www.mtyy1.cc ~ www.mtyy9.cc，可通过 extend 参数自定义主域名。
 * 搜索使用站内 ajax/suggest JSON 接口，播放地址交由客户端解析器处理（parse=1）。
 */
public class Mtyy extends Spider {

    private static final String SITE_URL = "https://www.mtyy1.cc";
    private static final String API_SEARCH = "/index.php/ajax/suggest?mid=1&wd=";

    /** 分类配置：电影(1) 电视剧(2) 动漫(4) 综艺(3) 短剧(26) */
    private static final String[] TYPE_IDS = {"1", "2", "4", "3", "26"};
    private static final String[] TYPE_NAMES = {"电影", "电视剧", "动漫", "综艺", "短剧"};

    /** 排序选项：name + value（嵌入分类 URL 的 by 槽位） */
    private static final String[][] SORT_OPTIONS = {
            {"最新", "time"},
            {"最热", "hits_week"},
            {"高分", "score"}
    };

    // 详情信息字段标签（位于 .info-parameter > ul > li > em）
    private static final String FIELD_TITLE = "片名";
    private static final String FIELD_STATUS = "状态";
    private static final String FIELD_ACTOR = "主演";
    private static final String FIELD_DIRECTOR = "导演";
    private static final String FIELD_YEAR = "年份";
    private static final String FIELD_AREA = "地区";
    private static final String FIELD_TYPE = "类型";
    private static final String FIELD_LANG = "语言";
    private static final String FIELD_INTRO = "简介";

    // 选择器常量
    private static final String SEL_LIST_ITEM = "div.public-list-box";
    private static final String SEL_LIST_LINK = "a.public-list-exp";
    private static final String SEL_LIST_REMARK = "span.public-list-prb";
    private static final String SEL_LIST_RATING = "span.public-prt";
    private static final String SEL_DETAIL_TITLE = "div.this-desc-title";
    private static final String SEL_DETAIL_PIC = "div.this-pic-bj";
    private static final String SEL_DETAIL_INFO = "div.info-parameter li";
    private static final String SEL_SOURCE_TAB = "div.anthology-tab a.swiper-slide";
    private static final String SEL_SOURCE_LIST = "div.anthology-list-box";
    private static final String SEL_EPISODE = "ul.anthology-list-play li a";
    private static final String SEL_PAGE_LINK = "div.pages a.page-link";

    // 正则：抽取背景图 URL 与总数
    private static final Pattern BG_URL_PATTERN = Pattern.compile("url\\(['\"]?([^'\"\\)]+)");
    private static final Pattern TOTAL_PATTERN = Pattern.compile("\\$\\(\\.hl-total\\)\\.html\\('?(\\d+)'?\\)");

    private String siteUrl = SITE_URL;
    private final HashMap<String, String> headers = new HashMap<>();

    public Mtyy() {
        headers.put("User-Agent", Util.CHROME);
        headers.put("Referer", SITE_URL);
    }

    @Override
    public void init(Context context, String extend) throws Exception {
        super.init(context);
        if (TextUtils.isEmpty(extend)) return;
        String ext = extend.trim();
        if (ext.startsWith("http")) {
            siteUrl = ext;
            headers.put("Referer", ext);
        }
    }

    public String getName() {
        return "麦田影院";
    }

    // ====================== Spider 接口实现 ======================

    @Override
    public String homeContent(boolean filter) throws Exception {
        List<Class> classes = new ArrayList<>();
        for (int i = 0; i < TYPE_IDS.length; i++) {
            classes.add(new Class(TYPE_IDS[i], TYPE_NAMES[i]));
        }
        LinkedHashMap<String, List<Filter>> filters = buildHomeFilters();
        List<Vod> videos = new ArrayList<>();
        try {
            String html = OkHttp.string(siteUrl, headers);
            videos = parseListItems(html);
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        return Result.string(classes, videos, filters);
    }

    @Override
    public String homeVideoContent() throws Exception {
        List<Vod> videos = new ArrayList<>();
        try {
            String html = OkHttp.string(siteUrl, headers);
            videos = parseListItems(html);
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        return Result.string(videos);
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        List<Vod> list = new ArrayList<>();
        int page = Util.toInt(pg, 1);
        int pageCount = 1;
        int total = 0;
        int limit = 36;

        try {
            String url = buildCategoryUrl(tid, page, extend);
            String html = OkHttp.string(url, headers);
            list = parseListItems(html);

            total = extractTotal(html);
            pageCount = extractPageCount(html, page);
        } catch (Exception e) {
            SpiderDebug.log(e);
        }

        if (total <= 0) {
            total = list.isEmpty() ? 0 : list.size() * Math.max(page, 1);
        }
        if (pageCount <= 0) pageCount = 1;

        return Result.get().page(page, pageCount, limit, total).vod(list).string();
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        List<Vod> result = new ArrayList<>();
        for (String vid : ids) {
            try {
                String detailUrl = fixUrl(vid);
                String html = OkHttp.string(detailUrl, headers);
                Document doc = Jsoup.parse(html);

                Vod vod = new Vod();
                vod.setVodId(vid);
                vod.setVodName(textOf(doc, SEL_DETAIL_TITLE));

                String pic = extractBackgroundUrl(doc.selectFirst(SEL_DETAIL_PIC));
                vod.setVodPic(pic);

                applyInfoParameter(doc, vod);

                parsePlaySources(doc, vod);

                result.add(vod);
            } catch (Exception e) {
                SpiderDebug.log(e);
            }
        }
        return Result.string(result);
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        return searchContent(key, quick, "1");
    }

    @Override
    public String searchContent(String key, boolean quick, String pg) throws Exception {
        List<Vod> list = new ArrayList<>();
        if (TextUtils.isEmpty(key)) return Result.string(list);
        try {
            String url = siteUrl + API_SEARCH + Util.encode(key) + "&limit=20";
            String json = OkHttp.string(url, headers);
            JSONObject root = new JSONObject(json);
            if (root.optInt("code") != 1) return Result.string(list);
            JSONArray arr = root.optJSONArray("list");
            if (arr == null) return Result.string(list);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject item = arr.optJSONObject(i);
                if (item == null) continue;
                String id = String.valueOf(item.optInt("id"));
                String name = item.optString("name");
                String pic = item.optString("pic");
                if (TextUtils.isEmpty(name) || "0".equals(id)) continue;
                list.add(new Vod("/voddetail/" + id + ".html", name, fixUrl(pic), ""));
            }
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        return Result.string(list);
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        String playUrl = fixUrl(id);
        return Result.get().url(playUrl).parse(1).header(headers).string();
    }

    // ====================== 私有辅助方法 ======================

    /**
     * 构建首页筛选器：每个分类均提供"排序"选项。
     */
    private LinkedHashMap<String, List<Filter>> buildHomeFilters() {
        LinkedHashMap<String, List<Filter>> filters = new LinkedHashMap<>();
        List<Filter.Value> sortValues = new ArrayList<>();
        sortValues.add(new Filter.Value("全部", ""));
        for (String[] opt : SORT_OPTIONS) {
            sortValues.add(new Filter.Value(opt[0], opt[1]));
        }
        for (String tid : TYPE_IDS) {
            List<Filter> group = new ArrayList<>();
            group.add(new Filter("by", "排序", sortValues, ""));
            filters.put(tid, group);
        }
        return filters;
    }

    /**
     * 构造分类页 URL：/vodshow/{cateId}-{area}-{by}-{class}-{lang}-{letter}---{pg}---{year}.html
     * 其中 by 来自 extend["by"]，其它筛选位留空（站点暂未提供筛选枚举，留作扩展）。
     */
    private String buildCategoryUrl(String tid, int page, HashMap<String, String> extend) {
        String area = getExtend(extend, "area");
        String by = getExtend(extend, "by");
        String lang = getExtend(extend, "lang");
        String year = getExtend(extend, "year");
        String letter = getExtend(extend, "letter");
        String klass = getExtend(extend, "class");

        StringBuilder sb = new StringBuilder(siteUrl);
        sb.append("/vodshow/").append(tid).append('-');
        sb.append(nullToEmpty(area)).append('-');
        sb.append(nullToEmpty(by)).append('-');
        sb.append(nullToEmpty(klass)).append('-');
        sb.append(nullToEmpty(lang)).append('-');
        sb.append(nullToEmpty(letter)).append("---");
        sb.append(page).append("---");
        sb.append(nullToEmpty(year)).append(".html");
        return sb.toString();
    }

    private static String getExtend(HashMap<String, String> extend, String key) {
        if (extend == null) return "";
        String v = extend.get(key);
        return v == null ? "" : v;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    /**
     * 解析列表页中的视频卡片：使用 public-list-box 容器，提取详情链接/封面/备注。
     */
    private List<Vod> parseListItems(String html) {
        List<Vod> list = new ArrayList<>();
        if (TextUtils.isEmpty(html)) return list;
        Document doc = Jsoup.parse(html);
        Elements items = doc.select(SEL_LIST_ITEM);
        for (Element item : items) {
            try {
                Element link = item.selectFirst(SEL_LIST_LINK);
                if (link == null) continue;
                String href = link.attr("href");
                if (TextUtils.isEmpty(href) || !href.contains("voddetail")) continue;

                String title = link.attr("title");
                String pic = "";
                Element img = item.selectFirst("img");
                if (img != null) {
                    pic = firstNonEmpty(img.attr("data-src"), img.attr("data-original"), img.attr("src"));
                    if (TextUtils.isEmpty(title)) title = img.attr("alt");
                }
                String remark = textOf(item, SEL_LIST_REMARK);
                if (TextUtils.isEmpty(remark)) {
                    remark = textOf(item, SEL_LIST_RATING);
                }

                if (TextUtils.isEmpty(title)) continue;
                list.add(new Vod(href, title, fixUrl(pic), remark));
            } catch (Exception e) {
                SpiderDebug.log(e);
            }
        }
        return list;
    }

    /**
     * 抽取页面尾部 <code>$('.hl-total').html('N')</code> 的总数。
     */
    private int extractTotal(String html) {
        if (TextUtils.isEmpty(html)) return 0;
        Matcher m = TOTAL_PATTERN.matcher(html);
        if (m.find()) {
            try {
                return Integer.parseInt(m.group(1));
            } catch (NumberFormatException ignored) {
            }
        }
        return 0;
    }

    /**
     * 解析分页：取最大页码，兜底估算。
     */
    private int extractPageCount(String html, int currentPage) {
        if (TextUtils.isEmpty(html)) return currentPage;
        int maxPage = currentPage;
        Document doc = Jsoup.parse(html);
        for (Element a : doc.select(SEL_PAGE_LINK)) {
            String title = a.attr("title");
            Matcher m = Pattern.compile("第(\\d+)页").matcher(title);
            if (m.find()) {
                maxPage = Math.max(maxPage, Integer.parseInt(m.group(1)));
            }
            String text = a.text().trim();
            if (text.matches("\\d+")) {
                maxPage = Math.max(maxPage, Integer.parseInt(text));
            }
        }
        return maxPage;
    }

    /**
     * 应用 .info-parameter 区域的字段到 Vod 对象。
     */
    private void applyInfoParameter(Document doc, Vod vod) {
        Elements lis = doc.select(SEL_DETAIL_INFO);
        for (Element li : lis) {
            Element em = li.selectFirst("em");
            if (em == null) continue;
            String label = em.text().trim();
            String value = textAfterEm(em);
            if (TextUtils.isEmpty(value)) continue;
            switch (label) {
                case FIELD_TITLE:
                    if (TextUtils.isEmpty(vod.getVodName())) vod.setVodName(value);
                    break;
                case FIELD_STATUS:
                    vod.setVodRemarks(value);
                    break;
                case FIELD_ACTOR:
                    vod.setVodActor(value);
                    break;
                case FIELD_DIRECTOR:
                    vod.setVodDirector(value);
                    break;
                case FIELD_YEAR:
                    vod.setVodYear(value);
                    break;
                case FIELD_AREA:
                    vod.setVodArea(value);
                    break;
                case FIELD_TYPE:
                    if (TextUtils.isEmpty(vod.getVodContent())) vod.setVodContent(value);
                    break;
                case FIELD_LANG:
                    // 语言暂无对应字段，合并到 area 备注避免丢失
                    break;
                case FIELD_INTRO:
                    vod.setVodContent(value);
                    break;
                default:
                    break;
            }
        }
    }

    /**
     * 获取 em 标签后的所有兄弟节点文本（保留链接之间的分隔）。
     */
    private String textAfterEm(Element em) {
        StringBuilder sb = new StringBuilder();
        org.jsoup.nodes.Node next = em.nextSibling();
        while (next != null) {
            if (next instanceof Element) {
                String t = ((Element) next).text();
                if (!TextUtils.isEmpty(t)) {
                    if (sb.length() > 0) sb.append(' ');
                    sb.append(t);
                }
            } else if (next.nodeName().equals("#text")) {
                String t = next.toString().trim();
                if (!t.isEmpty() && !t.equals(",")) {
                    if (sb.length() > 0) sb.append(' ');
                    sb.append(t);
                }
            }
            next = next.nextSibling();
        }
        return sb.toString().replaceAll("[,，]+\\s*", " ").trim();
    }

    /**
     * 解析线路与剧集：anthology-tab 与 anthology-list-box 按顺序一一对应。
     */
    private void parsePlaySources(Document doc, Vod vod) {
        Elements tabs = doc.select(SEL_SOURCE_TAB);
        Elements lists = doc.select(SEL_SOURCE_LIST);
        int count = Math.min(tabs.size(), lists.size());
        if (count == 0) return;

        List<String> playFrom = new ArrayList<>();
        List<String> playUrl = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            Element tab = tabs.get(i);
            String sourceName = tab.ownText().trim();
            if (TextUtils.isEmpty(sourceName)) sourceName = "线路" + (i + 1);

            Element box = lists.get(i);
            Elements eps = box.select(SEL_EPISODE);
            if (eps.isEmpty()) continue;

            List<String> episodes = new ArrayList<>();
            for (Element ep : eps) {
                String name = ep.text().trim();
                String href = ep.attr("href");
                if (TextUtils.isEmpty(name) || TextUtils.isEmpty(href)) continue;
                episodes.add(name + "$" + fixUrl(href));
            }
            if (!episodes.isEmpty()) {
                playFrom.add(sourceName);
                playUrl.add(join(episodes, "#"));
            }
        }

        if (!playFrom.isEmpty()) {
            vod.setVodPlayFrom(join(playFrom, "$$$"));
            vod.setVodPlayUrl(join(playUrl, "$$$"));
        }
    }

    /**
     * 从 style="background-image: url('xxx')" 中抽取封面 URL。
     */
    private String extractBackgroundUrl(Element elem) {
        if (elem == null) return "";
        String style = elem.attr("style");
        if (TextUtils.isEmpty(style)) return "";
        Matcher m = BG_URL_PATTERN.matcher(style);
        if (m.find()) return m.group(1);
        return "";
    }

    private static String textOf(Document doc, String selector) {
        Element e = doc.selectFirst(selector);
        return e == null ? "" : e.text().trim();
    }

    private static String textOf(Element parent, String selector) {
        Element e = parent.selectFirst(selector);
        return e == null ? "" : e.text().trim();
    }

    private static String firstNonEmpty(String... values) {
        for (String v : values) {
            if (!TextUtils.isEmpty(v)) return v;
        }
        return "";
    }

    /**
     * URL 修复：补全协议与主域名，处理 // 与 / 开头的相对路径。
     */
    private String fixUrl(String url) {
        if (TextUtils.isEmpty(url)) return "";
        String u = url.trim();
        if (u.startsWith("http")) return u;
        if (u.startsWith("//")) return "https:" + u;
        if (u.startsWith("/")) return siteUrl + u;
        return siteUrl + "/" + u;
    }

    private static String join(List<String> list, String delimiter) {
        if (list == null || list.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(delimiter);
            sb.append(list.get(i));
        }
        return sb.toString();
    }
}
