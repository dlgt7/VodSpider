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
 * 爬虫名称：壹秀影视
 * 爬虫类型：MacCMS v10 非标准 HTML解析型
 * 网站地址：https://www.yixiuwang.com/
 * 分类：电影(1) 电视剧(2) 综艺(3) 动漫(4) 国产动漫(35)
 * 搜索：已禁用（站点搜索接口返回nginx错误）
 */
public class Yixiuwang extends Spider {

    private static final String SITE_URL = "https://www.yixiuwang.com";
    private String siteUrl = SITE_URL;

    private final HashMap<String, String> headers = new HashMap<String, String>() {{
        put("User-Agent", Util.CHROME);
        put("Referer", SITE_URL);
    }};

    // 分类配置：电影$1#电视剧$2#综艺$3#动漫$4#国产动漫$35
    private static final String[] TYPE_IDS = {"1", "2", "3", "4", "35"};
    private static final String[] TYPE_NAMES = {"电影", "电视剧", "综艺", "动漫", "国产动漫"};

    // 分页正则：从尾页链接提取总页数
    private static final Pattern PAGE_NUM_PATTERN = Pattern.compile("--------(\\d+)---");

    @Override
    public void init(Context context, String extend) throws Exception {
        super.init(context);

        if (!TextUtils.isEmpty(extend)) {
            extend = extend.trim();
            if (extend.startsWith("http")) {
                siteUrl = extend;
                headers.put("Referer", extend);
            }
        }
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        List<Class> classes = new ArrayList<>();
        List<Vod> videos = new ArrayList<>();

        for (int i = 0; i < TYPE_IDS.length; i++) {
            classes.add(new Class(TYPE_IDS[i], TYPE_NAMES[i]));
        }

        LinkedHashMap<String, List<Filter>> filters = filter ? buildFilters() : null;

        // 获取首页推荐
        try {
            String html = OkHttp.string(siteUrl, headers);
            videos = parseListItems(html);
        } catch (Exception e) {
            SpiderDebug.log(e);
        }

        return Result.string(classes, videos, filters);
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
            SpiderDebug.log("Category URL: " + url);

            String html = OkHttp.string(url, headers);
            Document doc = Jsoup.parse(html);

            list = parseListItems(doc);

            // 解析分页：从尾页链接提取总页数
            Element lastPageLink = doc.selectFirst("a.page-link[title=尾页]");
            if (lastPageLink != null) {
                String href = lastPageLink.attr("href");
                Matcher m = PAGE_NUM_PATTERN.matcher(href);
                if (m.find()) {
                    pageCount = Util.toInt(m.group(1), 1);
                }
            } else {
                // 没有尾页链接，尝试从页码按钮获取
                Elements pageNumbers = doc.select(".page-number");
                for (Element pn : pageNumbers) {
                    String text = pn.text().trim();
                    if (text.matches("\\d+")) {
                        pageCount = Math.max(pageCount, Util.toInt(text, 1));
                    }
                }
            }

            // 估算总数
            if (total == 0) {
                total = list.size() < limit ? list.size() : list.size() * page;
                if (pageCount > 0) {
                    total = pageCount * limit;
                }
            }
        } catch (Exception e) {
            SpiderDebug.log(e);
        }

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

                String name = "";
                String pic = "";
                String director = "";
                String actor = "";
                String area = "";
                String year = "";
                String state = "";
                String type = "";
                String desc = "";
                String remarks = "";

                // 标题：h1标签
                Element titleElem = doc.selectFirst(".module-info-heading h1");
                if (titleElem == null) {
                    titleElem = doc.selectFirst("h1");
                }
                if (titleElem != null) {
                    name = titleElem.text().trim();
                }

                // 封面：module-info-poster 下的 img
                Element picElem = doc.selectFirst(".module-info-poster img");
                if (picElem != null) {
                    pic = firstNonEmpty(picElem.attr("data-original"), picElem.attr("src"));
                }
                if (TextUtils.isEmpty(pic)) {
                    picElem = doc.selectFirst(".module-item-pic img");
                    if (picElem != null) {
                        pic = firstNonEmpty(picElem.attr("data-original"), picElem.attr("src"));
                    }
                }

                // 从标签提取年份、地区、类型
                Elements tagLinks = doc.select(".module-info-tag-link");
                if (tagLinks.size() >= 1) {
                    Element yearLink = tagLinks.first().selectFirst("a");
                    if (yearLink != null) {
                        year = yearLink.text().trim();
                    }
                }
                if (tagLinks.size() >= 2) {
                    Element areaLink = tagLinks.get(1).selectFirst("a");
                    if (areaLink != null) {
                        area = areaLink.text().trim();
                    }
                }
                if (tagLinks.size() >= 3) {
                    type = tagLinks.get(2).text().trim();
                }

                // 从详情信息项提取导演、主演、状态、简介
                Elements infoItems = doc.select(".module-info-item");
                for (Element item : infoItems) {
                    Element titleSpan = item.selectFirst(".module-info-item-title");
                    if (titleSpan == null) continue;
                    String label = titleSpan.text().trim();
                    Element contentElem = item.selectFirst(".module-info-item-content");
                    String value = contentElem != null ? contentElem.text().trim() : "";

                    if (label.contains("导演")) {
                        director = value;
                    } else if (label.contains("主演")) {
                        actor = value;
                    } else if (label.contains("集数") || label.contains("状态")) {
                        remarks = value;
                    } else if (label.contains("上映")) {
                        if (TextUtils.isEmpty(year)) {
                            year = value;
                        }
                        state = value;
                    }
                }

                // 简介
                Element descElem = doc.selectFirst(".module-info-introduction-content p");
                if (descElem != null) {
                    desc = descElem.text().trim();
                }

                // 解析线路和播放列表
                List<String> playFromList = new ArrayList<>();
                List<String> playUrlList = new ArrayList<>();

                // 线路标签：module-tab-item 的 data-dropdown-value 属性
                Elements tabItems = doc.select(".module-tab-item");
                // 播放列表容器：module-list sort-list（每个对应一个线路）
                Elements playLists = doc.select("div.module-list.sort-list");

                int count = Math.min(tabItems.size(), playLists.size());
                for (int i = 0; i < count; i++) {
                    String sourceName = tabItems.get(i).attr("data-dropdown-value");
                    if (TextUtils.isEmpty(sourceName)) {
                        sourceName = tabItems.get(i).text().trim();
                    }
                    if (TextUtils.isEmpty(sourceName)) {
                        sourceName = "线路" + (i + 1);
                    }

                    Element playList = playLists.get(i);
                    Elements episodes = playList.select("a.module-play-list-link");
                    List<String> episodeList = new ArrayList<>();

                    for (Element ep : episodes) {
                        String epName = ep.text().trim();
                        String epUrl = ep.attr("href");
                        if (!TextUtils.isEmpty(epName) && !TextUtils.isEmpty(epUrl)) {
                            episodeList.add(epName + "$" + fixUrl(epUrl));
                        }
                    }

                    if (!episodeList.isEmpty()) {
                        playFromList.add(sourceName);
                        playUrlList.add(join(episodeList, "#"));
                    }
                }

                // 如果没有通过tab解析到线路，尝试直接解析所有播放链接
                if (playFromList.isEmpty()) {
                    Elements allLinks = doc.select("a.module-play-list-link");
                    List<String> episodeList = new ArrayList<>();
                    for (Element ep : allLinks) {
                        String epName = ep.text().trim();
                        String epUrl = ep.attr("href");
                        if (!TextUtils.isEmpty(epName) && !TextUtils.isEmpty(epUrl)) {
                            episodeList.add(epName + "$" + fixUrl(epUrl));
                        }
                    }
                    if (!episodeList.isEmpty()) {
                        playFromList.add("默认线路");
                        playUrlList.add(join(episodeList, "#"));
                    }
                }

                Vod vod = new Vod();
                vod.setVodId(vid);
                vod.setVodName(name);
                vod.setVodPic(fixUrl(pic));
                vod.setVodRemarks(remarks);
                vod.setVodYear(year);
                vod.setVodArea(area);
                vod.setVodDirector(director);
                vod.setVodActor(actor);
                vod.setVodContent(desc);
                vod.setVodPlayFrom(join(playFromList, "$$$"));
                vod.setVodPlayUrl(join(playUrlList, "$$$"));

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
        // 搜索已禁用：站点搜索URL全部返回nginx错误
        return Result.string(new ArrayList<>());
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        // MacCMS站点黄金法则：直接返回parse=1，让客户端解析器处理解密
        String playUrl = fixUrl(id);

        return Result.get()
            .url(playUrl)
            .parse(1)
            .header(headers)
            .string();
    }

    // ====================== 私有辅助方法 ======================

    /**
     * 构建首页筛选器：为每个分类提供 area/by/class/lang/letter/year 六组筛选。
     */
    private LinkedHashMap<String, List<Filter>> buildFilters() {
        LinkedHashMap<String, List<Filter>> filters = new LinkedHashMap<>();

        for (String tid : TYPE_IDS) {
            List<Filter> group = new ArrayList<>();

            // 地区
            group.add(new Filter("area", "地区", Arrays.asList(
                new Filter.Value("全部", ""),
                new Filter.Value("内地", "内地"),
                new Filter.Value("韩国", "韩国"),
                new Filter.Value("香港", "香港"),
                new Filter.Value("台湾", "台湾"),
                new Filter.Value("日本", "日本"),
                new Filter.Value("美国", "美国"),
                new Filter.Value("泰国", "泰国"),
                new Filter.Value("英国", "英国"),
                new Filter.Value("新加坡", "新加坡"),
                new Filter.Value("其他", "其他")
            )));

            // 排序
            group.add(new Filter("by", "排序", Arrays.asList(
                new Filter.Value("默认", ""),
                new Filter.Value("时间", "time"),
                new Filter.Value("人气", "hits"),
                new Filter.Value("评分", "score")
            )));

            // 剧情/类型
            group.add(new Filter("class", "剧情", Arrays.asList(
                new Filter.Value("全部", ""),
                new Filter.Value("古装", "古装"),
                new Filter.Value("战争", "战争"),
                new Filter.Value("青春偶像", "青春偶像"),
                new Filter.Value("喜剧", "喜剧"),
                new Filter.Value("家庭", "家庭"),
                new Filter.Value("犯罪", "犯罪"),
                new Filter.Value("动作", "动作"),
                new Filter.Value("奇幻", "奇幻"),
                new Filter.Value("剧情", "剧情"),
                new Filter.Value("历史", "历史"),
                new Filter.Value("经典", "经典"),
                new Filter.Value("乡村", "乡村"),
                new Filter.Value("情景", "情景"),
                new Filter.Value("商战", "商战"),
                new Filter.Value("网剧", "网剧"),
                new Filter.Value("其他", "其他")
            )));

            // 语言
            group.add(new Filter("lang", "语言", Arrays.asList(
                new Filter.Value("全部", ""),
                new Filter.Value("国语", "国语"),
                new Filter.Value("英语", "英语"),
                new Filter.Value("粤语", "粤语"),
                new Filter.Value("闽南语", "闽南语"),
                new Filter.Value("韩语", "韩语"),
                new Filter.Value("日语", "日语"),
                new Filter.Value("其它", "其它")
            )));

            // 字母
            group.add(new Filter("letter", "字母", Arrays.asList(
                new Filter.Value("全部", ""),
                new Filter.Value("A", "A"),
                new Filter.Value("B", "B"),
                new Filter.Value("C", "C"),
                new Filter.Value("D", "D"),
                new Filter.Value("E", "E"),
                new Filter.Value("F", "F"),
                new Filter.Value("G", "G"),
                new Filter.Value("H", "H"),
                new Filter.Value("I", "I"),
                new Filter.Value("J", "J"),
                new Filter.Value("K", "K"),
                new Filter.Value("L", "L"),
                new Filter.Value("M", "M"),
                new Filter.Value("N", "N"),
                new Filter.Value("O", "O"),
                new Filter.Value("P", "P"),
                new Filter.Value("Q", "Q"),
                new Filter.Value("R", "R"),
                new Filter.Value("S", "S"),
                new Filter.Value("T", "T"),
                new Filter.Value("U", "U"),
                new Filter.Value("V", "V"),
                new Filter.Value("W", "W"),
                new Filter.Value("X", "X"),
                new Filter.Value("Y", "Y"),
                new Filter.Value("Z", "Z"),
                new Filter.Value("0-9", "0-9")
            )));

            // 年份
            group.add(new Filter("year", "年份", buildYearValues()));

            filters.put(tid, group);
        }

        return filters;
    }

    /**
     * 构建年份筛选项：2000-2026
     */
    private List<Filter.Value> buildYearValues() {
        List<Filter.Value> values = new ArrayList<>();
        values.add(new Filter.Value("全部", ""));
        for (int y = 2026; y >= 2000; y--) {
            values.add(new Filter.Value(String.valueOf(y), String.valueOf(y)));
        }
        return values;
    }

    /**
     * 构造分类页 URL：
     * /vodshow/{typeId}-{area}-{by}-{class}-{lang}-{letter}---{page}---{year}/
     * 第1页的 page 段为空（与站点实际URL一致）。
     */
    private String buildCategoryUrl(String tid, int page, HashMap<String, String> extend) {
        String area = getExtend(extend, "area");
        String by = getExtend(extend, "by");
        String klass = getExtend(extend, "class");
        String lang = getExtend(extend, "lang");
        String letter = getExtend(extend, "letter");
        String year = getExtend(extend, "year");

        StringBuilder sb = new StringBuilder(siteUrl);
        sb.append("/vodshow/").append(tid).append('-');
        sb.append(enc(area)).append('-');
        sb.append(enc(by)).append('-');
        sb.append(enc(klass)).append('-');
        sb.append(enc(lang)).append('-');
        sb.append(enc(letter)).append("---");
        sb.append(page > 1 ? String.valueOf(page) : "").append("---");
        sb.append(enc(year)).append('/');
        return sb.toString();
    }

    /**
     * 从 extend 参数安全取值
     */
    private static String getExtend(HashMap<String, String> extend, String key) {
        if (extend == null) return "";
        String v = extend.get(key);
        return v == null ? "" : v;
    }

    /**
     * URL编码（空值安全）
     */
    private static String enc(String s) {
        return TextUtils.isEmpty(s) ? "" : Util.encode(s);
    }

    /**
     * 解析列表页视频卡片。
     * 结构：a.module-poster-item[href*=/voddetail/]
     *   - title 属性 → 标题
     *   - .module-item-note → 备注
     *   - img[data-original] → 封面
     */
    private List<Vod> parseListItems(Document doc) {
        List<Vod> list = new ArrayList<>();
        Elements items = doc.select("a.module-poster-item");
        if (items.isEmpty()) {
            items = doc.select("a.module-item[href*=/voddetail/]");
        }

        for (Element item : items) {
            try {
                String href = item.attr("href");
                if (TextUtils.isEmpty(href) || !href.contains("voddetail")) continue;

                String title = item.attr("title");
                String pic = "";
                String remark = "";

                // 封面图
                Element img = item.selectFirst("img");
                if (img != null) {
                    pic = firstNonEmpty(img.attr("data-original"), img.attr("data-src"), img.attr("src"));
                    if (TextUtils.isEmpty(title)) {
                        title = img.attr("alt");
                    }
                }

                // 备注
                Element remarkElem = item.selectFirst(".module-item-note");
                if (remarkElem != null) {
                    remark = remarkElem.text().trim();
                }

                if (TextUtils.isEmpty(title)) {
                    Element titleElem = item.selectFirst(".module-poster-item-title");
                    if (titleElem != null) {
                        title = titleElem.text().trim();
                    }
                }

                if (!TextUtils.isEmpty(href) && !TextUtils.isEmpty(title)) {
                    list.add(new Vod(href, title, fixUrl(pic), remark));
                }
            } catch (Exception e) {
                SpiderDebug.log(e);
            }
        }
        return list;
    }

    /**
     * 从 HTML 字符串解析列表项
     */
    private List<Vod> parseListItems(String html) {
        if (TextUtils.isEmpty(html)) return new ArrayList<>();
        return parseListItems(Jsoup.parse(html));
    }

    /**
     * 取第一个非空字符串
     */
    private static String firstNonEmpty(String... values) {
        for (String v : values) {
            if (!TextUtils.isEmpty(v)) return v;
        }
        return "";
    }

    /**
     * URL修复：补全协议与主域名
     */
    private String fixUrl(String url) {
        if (TextUtils.isEmpty(url)) return "";
        String u = url.trim();
        if (u.startsWith("http")) return u;
        if (u.startsWith("//")) return "https:" + u;
        if (u.startsWith("/")) return siteUrl + u;
        return siteUrl + "/" + u;
    }

    /**
     * 字符串连接
     */
    private static String join(List<String> list, String delimiter) {
        if (list == null || list.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(delimiter);
            sb.append(list.get(i));
        }
        return sb.toString();
    }

    public String getName() {
        return "壹秀影视";
    }
}
