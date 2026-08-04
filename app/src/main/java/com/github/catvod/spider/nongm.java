package com.github.catvod.spider;

import android.content.Context;
import android.text.TextUtils;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Json;
import com.github.catvod.utils.Util;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.ByteArrayInputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 农民影视 Spider
 * 站点：https://vip.wwgz.cn:5200
 * 基于 HTML 解析（Jsoup），播放使用本地代理解析
 * 图片域名替换：pic.lzzypic.com → img.lzzyimg.com
 */
public class nongm extends Spider {

    private static final String DEFAULT_HOST = "https://vip.wwgz.cn:5200";
    private static final String USER_AGENT = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36";
    private static final String PIC_OLD_DOMAIN = "pic.lzzypic.com";
    private static final String PIC_NEW_DOMAIN = "img.lzzyimg.com";

    private static final Pattern JX_URL_PATTERN = Pattern.compile("http.*?url=");
    private static final Pattern URL_ASSIGN_PATTERN = Pattern.compile("url='(.*?)'");
    private static final Pattern SRC_PATTERN = Pattern.compile("src=\"(.*?)\"");

    private String host;
    private Map<String, String> headers;

    @Override
    public void init(Context context, String extend) throws Exception {
        super.init(context);
        host = DEFAULT_HOST;
        if (!TextUtils.isEmpty(extend)) {
            extend = extend.trim();
            if (extend.startsWith("http")) {
                host = extend;
                while (host.endsWith("/")) host = host.substring(0, host.length() - 1);
            }
        }
        headers = new HashMap<>();
        headers.put("User-Agent", USER_AGENT);
        headers.put("Referer", host + "/");
        headers.put("Accept", "text/html");
    }

    /** 替换图片域名 */
    private String fixPic(String pic) {
        if (TextUtils.isEmpty(pic)) return "";
        if (pic.contains(PIC_OLD_DOMAIN)) {
            pic = pic.replace("https://" + PIC_OLD_DOMAIN, "https://" + PIC_NEW_DOMAIN);
        }
        return pic;
    }

    /** 解析列表项 */
    private List<Vod> parseList(Elements items) {
        List<Vod> list = new ArrayList<>();
        for (Element item : items) {
            try {
                Element a = item.selectFirst("a");
                if (a == null) continue;
                String href = a.attr("href");
                if (TextUtils.isEmpty(href)) continue;
                String vodId = host + href;

                Element img = item.selectFirst("img");
                String pic = "";
                if (img != null) {
                    pic = img.attr("data-echo");
                    if (TextUtils.isEmpty(pic)) pic = img.attr("data-src");
                    if (TextUtils.isEmpty(pic)) pic = img.attr("src");
                    pic = fixPic(pic);
                }

                String name = "";
                Element title = item.selectFirst(".sTit");
                if (title != null) name = title.text();

                String remark = "";
                Element bottom = item.selectFirst(".sBottom");
                if (bottom != null) remark = bottom.text();

                list.add(new Vod(vodId, name, pic, remark));
            } catch (Exception e) {
                SpiderDebug.log(e);
            }
        }
        return list;
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        List<Class> classes = new ArrayList<>();
        classes.add(new Class("12", "国产剧"));
        classes.add(new Class("4", "动漫"));
        classes.add(new Class("1", "电影"));
        classes.add(new Class("2", "电视剧"));
        classes.add(new Class("3", "综艺"));
        classes.add(new Class("26", "短剧"));

        List<Vod> list = new ArrayList<>();
        try {
            String html = OkHttp.string(host, headers);
            Document doc = Jsoup.parse(html);
            list = parseList(doc.select(".globalPicList li:has(img)"));
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        return Result.string(classes, list);
    }

    @Override
    public String homeVideoContent() throws Exception {
        return Result.string(new ArrayList<>());
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        if (TextUtils.isEmpty(pg)) pg = "1";
        List<Vod> list = new ArrayList<>();
        try {
            String url;
            if ("4-dm".equals(tid)) {
                // 大陆人气动漫分类
                url = "https://www.wwgz.cn/vod-list-id-4-pg-" + pg + "-order--by-hits-class-0-year-0-letter--area-大陆-lang-.html";
            } else {
                url = host + "/vod-list-id-" + tid + "-pg-" + pg + ".html";
            }
            String html = OkHttp.string(url, headers);
            Document doc = Jsoup.parse(html);
            list = parseList(doc.select(".globalPicList li"));
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        int page = Util.toInt(pg, 1);
        return Result.get().page(page, 9999, 90, 999999).vod(list).string();
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        String url = ids.get(0);
        List<Vod> list = new ArrayList<>();
        try {
            String html = OkHttp.string(url, headers);
            Document doc = Jsoup.parse(html);

            List<String> playFrom = new ArrayList<>();
            List<String> playUrl = new ArrayList<>();

            Element tabBox = doc.selectFirst("#leftTabBox");
            if (tabBox != null) {
                // 线路名称
                for (Element tab : tabBox.select("ul li")) {
                    playFrom.add(tab.text());
                }
                // 剧集列表（反转顺序）
                for (Element numList : tabBox.select(".numList")) {
                    List<String> episodes = new ArrayList<>();
                    List<Element> eps = new ArrayList<>(numList.select("li"));
                    // 反转列表顺序
                    for (int i = eps.size() - 1; i >= 0; i--) {
                        Element ep = eps.get(i);
                        Element a = ep.selectFirst("a");
                        if (a != null) {
                            String epName = a.text();
                            String epUrl = host + a.attr("href");
                            episodes.add(epName + "$" + epUrl);
                        }
                    }
                    playUrl.add(TextUtils.join("#", episodes));
                }
            }

            Vod vod = new Vod();
            vod.setVodId(url);
            Element h1a = doc.selectFirst("h1 a");
            if (h1a != null) vod.setVodName(h1a.text());

            // 年代
            Element yearElem = doc.selectFirst("span:containsOwn(年代：)");
            if (yearElem != null) vod.setVodYear(yearElem.text().replace("年代：", ""));

            // 主演
            Element actorElem = doc.selectFirst(".sDes:containsOwn(主演:)");
            if (actorElem != null) vod.setVodActor(actorElem.text().replace("主演:", ""));

            // 简介
            Element descElem = doc.selectFirst(".detail-con p");
            if (descElem != null) vod.setVodContent(descElem.text().replace("简介:", ""));

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
        List<Vod> list = new ArrayList<>();
        try {
            String url = host + "/index.php?m=vod-search";
            Map<String, String> params = new HashMap<>();
            params.put("wd", key);
            String html = OkHttp.post(url, params, headers);
            Document doc = Jsoup.parse(html);
            for (Element item : doc.select("#data_list li")) {
                Element a = item.selectFirst("a");
                if (a == null) continue;
                String vodId = host + a.attr("href");
                String name = "";
                Element title = item.selectFirst(".sTit");
                if (title != null) name = title.text();

                String pic = "";
                Element lazy = item.selectFirst(".lazyload");
                if (lazy != null) {
                    pic = fixPic(lazy.attr("data-src"));
                }

                String remark = "";
                Elements descs = item.select(".sDes");
                if (!descs.isEmpty()) remark = descs.last().text();

                list.add(new Vod(vodId, name, pic, remark));
            }
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        return Result.string(list);
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        try {
            Object urlResult;
            int parse;
            if (id.contains("@")) {
                String[] ids = id.split("@", 2);
                String jsId = ids[0];
                String videoId = ids[1];
                if (TextUtils.isEmpty(jsId)) {
                    return Result.get().parse(1).url(id).header(headers).string();
                }

                // 获取 player JS 文件，提取解析接口 URL
                String jsUrl = host + "/player/" + jsId + ".js";
                String jsData = OkHttp.string(jsUrl, headers);
                Matcher jxMatcher = JX_URL_PATTERN.matcher(jsData);
                if (!jxMatcher.find()) {
                    return Result.get().parse(1).url(id).header(headers).string();
                }
                String jxurl = jxMatcher.group();

                // 请求解析接口
                String data = OkHttp.string(jxurl + videoId, headers);
                Matcher matcher = JX_URL_PATTERN.matcher(data);

                if (matcher.find()) {
                    // 多线路：构建代理 URL 列表
                    List<String> urlList = new ArrayList<>();
                    int i = 1;
                    do {
                        String x = matcher.group();
                        String json = new Gson().toJson(new JxPayload(x, videoId));
                        String encoded = Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
                        String purl = Proxy.getUrl(siteKey, "&wdict=" + URLEncoder.encode(encoded, "UTF-8"));
                        urlList.add("线路" + i);
                        urlList.add(purl);
                        i++;
                    } while (matcher.find());
                    urlResult = urlList;
                    parse = 0;
                } else {
                    // 单线路：提取 url='...'
                    Matcher urlMatcher = URL_ASSIGN_PATTERN.matcher(data);
                    if (urlMatcher.find()) {
                        urlResult = urlMatcher.group(1);
                        parse = 0;
                    } else {
                        return Result.get().parse(1).url(id).header(headers).string();
                    }
                }
            } else {
                urlResult = id;
                parse = 1;
            }
            Result result = Result.get().parse(parse);
            if (urlResult instanceof String) {
                result.url((String) urlResult);
            } else if (urlResult instanceof List) {
                @SuppressWarnings("unchecked")
                List<String> urls = (List<String>) urlResult;
                result.url(urls);
            }
            return result.header(headers).string();
        } catch (Exception e) {
            SpiderDebug.log(e);
            return Result.get().parse(1).url(id).header(headers).string();
        }
    }

    /** 代理负载（JSON 编码后 Base64 作为 wdict 参数） */
    private static class JxPayload {
        final String jx;
        final String id;

        JxPayload(String jx, String id) {
            this.jx = jx;
            this.id = id;
        }
    }

    @Override
    public Object[] proxy(Map<String, String> params) throws Exception {
        try {
            String wdict = params.get("wdict");
            if (TextUtils.isEmpty(wdict)) {
                return new Object[]{500, "text/plain", new ByteArrayInputStream("missing wdict".getBytes(StandardCharsets.UTF_8))};
            }
            String json = new String(Base64.getDecoder().decode(wdict), StandardCharsets.UTF_8);
            JsonObject obj = Json.parse(json).getAsJsonObject();
            String jx = obj.get("jx").getAsString();
            String id = obj.get("id").getAsString();
            String url = jx + id;
            String data = OkHttp.string(url, headers);
            Document doc = Jsoup.parse(data);
            // 取最后一个 script 标签内容
            Elements scripts = doc.select("script");
            if (scripts.isEmpty()) {
                return new Object[]{500, "text/plain", new ByteArrayInputStream("no script".getBytes(StandardCharsets.UTF_8))};
            }
            String scriptText = scripts.last().html();
            Matcher m = SRC_PATTERN.matcher(scriptText);
            if (m.find()) {
                String realUrl = m.group(1);
                // 302 重定向
                return new Object[]{302, "text/html", null, new HashMap<String, String>() {{ put("Location", realUrl); }}};
            }
            return new Object[]{500, "text/plain", new ByteArrayInputStream("no src".getBytes(StandardCharsets.UTF_8))};
        } catch (Exception e) {
            SpiderDebug.log(e);
            return new Object[]{500, "text/plain", new ByteArrayInputStream(e.getMessage().getBytes(StandardCharsets.UTF_8))};
        }
    }
}
