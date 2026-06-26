package com.github.catvod.js;

import android.content.Context;
import android.net.Uri;
import android.text.TextUtils;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Filter;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.js.bean.Info;
import com.github.catvod.js.utils.JSUtil;
import com.github.catvod.js.utils.Parser;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Json;

import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Function {

    private static final String[] EMPTY = new String[0];
    private static final String[] FILTER = {"class", "area", "lang", "year"};

    private final Info info;

    public Function(Info info) {
        this.info = info;
    }

    public String homeContent(boolean filter) {
        try {
            SpiderDebug.logMethod("homeContent");
            List<Class> classes = new ArrayList<>();
            LinkedHashMap<String, List<Filter>> filters = new LinkedHashMap<>();
            
            Spider spider = info.getSpider();
            if (spider != null) {
                String result = spider.homeContent(filter);
                if (!TextUtils.isEmpty(result)) {
                    JSONObject json = new JSONObject(result);
                    classes = parseClasses(json.optJSONArray("class"));
                    if (filter) {
                        filters = parseFilters(json.optJSONObject("filters"));
                    }
                }
            }
            
            return Result.string(classes, filters);
        } catch (Exception e) {
            SpiderDebug.log(e);
            return Result.error("加载首页内容失败: " + e.getMessage());
        }
    }

    public String homeVideoContent() {
        try {
            SpiderDebug.logMethod("homeVideoContent");
            List<Vod> list = new ArrayList<>();
            
            Spider spider = info.getSpider();
            if (spider != null) {
                String result = spider.homeVideoContent();
                if (!TextUtils.isEmpty(result)) {
                    list = parseVods(new JSONObject(result).optJSONArray("list"));
                }
            }
            
            return Result.string(list);
        } catch (Exception e) {
            SpiderDebug.log(e);
            return Result.error("加载首页视频失败: " + e.getMessage());
        }
    }

    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) {
        try {
            SpiderDebug.logMethod("categoryContent");
            List<Vod> list = new ArrayList<>();
            int page = 1, count = 1, limit = 0, total = 0;
            
            Spider spider = info.getSpider();
            if (spider != null) {
                String result = spider.categoryContent(tid, pg, filter, extend);
                if (!TextUtils.isEmpty(result)) {
                    JSONObject json = new JSONObject(result);
                    list = parseVods(json.optJSONArray("list"));
                    page = json.optInt("page", 1);
                    count = json.optInt("pagecount", 1);
                    limit = json.optInt("limit", 0);
                    total = json.optInt("total", 0);
                }
            }
            
            return Result.get().vod(list).page(page, count, limit, total).string();
        } catch (Exception e) {
            SpiderDebug.log(e);
            return Result.error("加载分类内容失败: " + e.getMessage());
        }
    }

    public String detailContent(List<String> ids) {
        try {
            SpiderDebug.logMethod("detailContent");
            if (ids == null || ids.isEmpty()) {
                return Result.error("缺少视频ID");
            }
            
            Spider spider = info.getSpider();
            if (spider != null) {
                String result = spider.detailContent(ids);
                if (!TextUtils.isEmpty(result)) {
                    JSONObject json = new JSONObject(result);
                    JSONArray array = json.optJSONArray("list");
                    if (array != null && array.length() > 0) {
                        Vod vod = parseVod(array.getJSONObject(0));
                        return Result.string(vod);
                    }
                }
            }
            
            return Result.error("未找到视频详情");
        } catch (Exception e) {
            SpiderDebug.log(e);
            return Result.error("加载详情内容失败: " + e.getMessage());
        }
    }

    public String searchContent(String key, boolean quick) {
        return searchContent(key, quick, "1");
    }

    public String searchContent(String key, boolean quick, String pg) {
        try {
            SpiderDebug.logMethod("searchContent");
            if (TextUtils.isEmpty(key)) {
                return Result.error("搜索关键词不能为空");
            }
            
            List<Vod> list = new ArrayList<>();
            Spider spider = info.getSpider();
            if (spider != null) {
                String result = spider.searchContent(key, quick, pg);
                if (!TextUtils.isEmpty(result)) {
                    list = parseVods(new JSONObject(result).optJSONArray("list"));
                }
            }
            
            return Result.string(list);
        } catch (Exception e) {
            SpiderDebug.log(e);
            return Result.error("搜索失败: " + e.getMessage());
        }
    }

    public String playerContent(String flag, String id, List<String> vipFlags) {
        try {
            SpiderDebug.logMethod("playerContent");
            if (TextUtils.isEmpty(id)) {
                return Result.error("缺少播放ID");
            }
            
            Spider spider = info.getSpider();
            if (spider != null) {
                String jsonStr = spider.playerContent(flag, id, vipFlags);
                if (!TextUtils.isEmpty(jsonStr)) {
                    JSONObject json = new JSONObject(jsonStr);
                    Result res = Result.get()
                            .url(json.optString("url"))
                            .parse(json.optInt("parse", 0))
                            .header(parseHeader(json.optString("header")))
                            .format(json.optString("format"))
                            .subs(parseSubs(json.optJSONArray("subs")));
                    if (json.optInt("jx", 0) == 1) res.jx();
                    return res.string();
                }
            }
            
            return Result.error("未找到播放地址");
        } catch (Exception e) {
            SpiderDebug.log(e);
            return Result.error("加载播放内容失败: " + e.getMessage());
        }
    }

    public String liveContent(String url) {
        try {
            SpiderDebug.logMethod("liveContent");
            Spider spider = info.getSpider();
            if (spider != null) {
                return spider.liveContent(url);
            }
            return Result.error("未实现直播功能");
        } catch (Exception e) {
            SpiderDebug.log(e);
            return Result.error("加载直播内容失败: " + e.getMessage());
        }
    }

    public Object[] proxy(Map<String, String> params) {
        try {
            SpiderDebug.logMethod("proxy");
            Spider spider = info.getSpider();
            if (spider != null) {
                return spider.proxy(params);
            }
            return null;
        } catch (Exception e) {
            SpiderDebug.log(e);
            return null;
        }
    }

    public String action(String action) {
        try {
            SpiderDebug.logMethod("action");
            Spider spider = info.getSpider();
            if (spider != null) {
                return spider.action(action);
            }
            return Result.error("未实现动作功能");
        } catch (Exception e) {
            SpiderDebug.log(e);
            return Result.error("执行动作失败: " + e.getMessage());
        }
    }

    private List<Class> parseClasses(JSONArray array) {
        List<Class> list = new ArrayList<>();
        if (array == null) return list;
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item != null) {
                list.add(new Class(
                        item.optString("type_id"),
                        item.optString("type_name"),
                        item.optString("type_flag")
                ));
            }
        }
        return list;
    }

    private LinkedHashMap<String, List<Filter>> parseFilters(JSONObject object) {
        LinkedHashMap<String, List<Filter>> map = new LinkedHashMap<>();
        if (object == null) return map;
        JSONArray names = object.names();
        if (names == null) return map;
        for (int i = 0; i < names.length(); i++) {
            String key = names.optString(i);
            JSONArray array = object.optJSONArray(key);
            if (array != null) {
                List<Filter> filters = new ArrayList<>();
                for (int j = 0; j < array.length(); j++) {
                    JSONObject item = array.optJSONObject(j);
                    if (item != null) {
                        filters.add(new Filter(
                                item.optString("key"),
                                item.optString("name"),
                                parseFilterValues(item.optJSONArray("value")),
                                item.optString("init")
                        ));
                    }
                }
                map.put(key, filters);
            }
        }
        return map;
    }

    private List<Filter.Value> parseFilterValues(JSONArray array) {
        List<Filter.Value> list = new ArrayList<>();
        if (array == null) return list;
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item != null) {
                list.add(new Filter.Value(item.optString("n"), item.optString("v")));
            }
        }
        return list;
    }

    private List<Vod> parseVods(JSONArray array) {
        List<Vod> list = new ArrayList<>();
        if (array == null) return list;
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item != null) {
                list.add(parseVod(item));
            }
        }
        return list;
    }

    private Vod parseVod(JSONObject item) {
        Vod vod = new Vod();
        vod.setTypeName(item.optString("type_name"));
        vod.setVodId(item.optString("vod_id"));
        vod.setVodName(item.optString("vod_name"));
        vod.setVodPic(item.optString("vod_pic"));
        vod.setVodRemarks(item.optString("vod_remarks"));
        vod.setVodYear(item.optString("vod_year"));
        vod.setVodArea(item.optString("vod_area"));
        vod.setVodActor(item.optString("vod_actor"));
        vod.setVodDirector(item.optString("vod_director"));
        vod.setVodContent(item.optString("vod_content"));
        vod.setVodPlayFrom(item.optString("vod_play_from"));
        vod.setVodPlayUrl(item.optString("vod_play_url"));
        vod.setVodTag(item.optString("vod_tag"));
        return vod;
    }

    private Map<String, String> parseHeader(String header) {
        Map<String, String> map = new HashMap<>();
        if (TextUtils.isEmpty(header)) return map;
        try {
            JSONObject json = new JSONObject(header);
            JSONArray names = json.names();
            if (names != null) {
                for (int i = 0; i < names.length(); i++) {
                    String key = names.optString(i);
                    map.put(key, json.optString(key));
                }
            }
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        return map;
    }

    private List<com.github.catvod.bean.Sub> parseSubs(JSONArray array) {
        List<com.github.catvod.bean.Sub> list = new ArrayList<>();
        if (array == null) return list;
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item != null) {
                list.add(com.github.catvod.bean.Sub.create()
                        .url(item.optString("url"))
                        .name(item.optString("name"))
                        .lang(item.optString("lang"))
                        .format(item.optString("format")));
            }
        }
        return list;
    }

    public String pdfh(String html, String rule) {
        if (TextUtils.isEmpty(html) || TextUtils.isEmpty(rule)) return "";
        try {
            Document doc = info.getCache().getPdfh(html);
            if (doc == null) return "";
            
            String[] rules = rule.split("&&");
            Element element = doc;
            
            for (String r : rules) {
                r = r.trim();
                if (r.startsWith("body&&")) {
                    element = doc.body();
                    r = r.substring(6);
                }
                
                if (r.startsWith(".")) {
                    element = element.selectFirst(r);
                } else if (r.startsWith("#")) {
                    element = element.selectFirst(r);
                } else if (r.startsWith("Text")) {
                    return element.text();
                } else if (r.startsWith("Html")) {
                    return element.html();
                } else if (r.startsWith("href")) {
                    return element.attr("href");
                } else if (r.startsWith("src")) {
                    return element.attr("src");
                } else if (r.startsWith("data-")) {
                    return element.attr(r);
                } else if (r.startsWith("style")) {
                    return element.attr("style");
                } else if (r.startsWith("title")) {
                    return element.attr("title");
                } else if (r.startsWith("alt")) {
                    return element.attr("alt");
                } else if (r.startsWith("class")) {
                    return element.attr("class");
                } else if (r.startsWith("id")) {
                    return element.attr("id");
                } else if (r.startsWith("width")) {
                    return element.attr("width");
                } else if (r.startsWith("height")) {
                    return element.attr("height");
                } else {
                    element = element.selectFirst(r);
                }
                
                if (element == null) return "";
            }
            
            return element.text();
        } catch (Exception e) {
            SpiderDebug.log(e);
            return "";
        }
    }

    public List<String> pdfa(String html, String rule) {
        List<String> list = new ArrayList<>();
        if (TextUtils.isEmpty(html) || TextUtils.isEmpty(rule)) return list;
        try {
            Document doc = info.getCache().getPdfa(html);
            if (doc == null) return list;
            
            String[] parts = rule.split("&&");
            if (parts.length == 0) return list;
            
            String selector = parts[0].trim();
            Elements elements = doc.select(selector);
            
            if (parts.length > 1) {
                String attr = parts[1].trim();
                for (Element e : elements) {
                    if (attr.equals("Text")) {
                        list.add(e.text());
                    } else if (attr.equals("Html")) {
                        list.add(e.html());
                    } else {
                        list.add(e.attr(attr));
                    }
                }
            } else {
                for (Element e : elements) {
                    list.add(e.outerHtml());
                }
            }
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        return list;
    }

    public String pd(String html, String rule) {
        if (TextUtils.isEmpty(html) || TextUtils.isEmpty(rule)) return "";
        try {
            String result = pdfh(html, rule);
            if (TextUtils.isEmpty(result)) return "";
            
            if (result.startsWith("http")) return result;
            if (result.startsWith("//")) return "https:" + result;
            if (result.startsWith("/")) {
                String domain = Parser.extractDomain(info.getApi());
                return "https://" + domain + result;
            }
            
            return result;
        } catch (Exception e) {
            SpiderDebug.log(e);
            return "";
        }
    }

    public String pq(String html, String rule) {
        if (TextUtils.isEmpty(html) || TextUtils.isEmpty(rule)) return "";
        try {
            List<String> list = pdfa(html, rule);
            return TextUtils.join("$$$", list);
        } catch (Exception e) {
            SpiderDebug.log(e);
            return "";
        }
    }

    public String dh(String html, String rule) {
        if (TextUtils.isEmpty(html) || TextUtils.isEmpty(rule)) return "";
        try {
            String[] parts = rule.split(";");
            if (parts.length == 0) return "";
            
            String result = pdfh(html, parts[0]);
            if (parts.length > 1) {
                for (int i = 1; i < parts.length; i++) {
                    result = JSUtil.regexReplace(result, parts[i], "");
                }
            }
            
            return result.trim();
        } catch (Exception e) {
            SpiderDebug.log(e);
            return "";
        }
    }

    public String dq(String html, String rule) {
        if (TextUtils.isEmpty(html) || TextUtils.isEmpty(rule)) return "";
        try {
            String[] parts = rule.split(";");
            if (parts.length == 0) return "";
            
            List<String> list = pdfa(html, parts[0]);
            if (parts.length > 1) {
                for (int i = 1; i < parts.length; i++) {
                    for (int j = 0; j < list.size(); j++) {
                        list.set(j, JSUtil.regexReplace(list.get(j), parts[i], "").trim());
                    }
                }
            }
            
            return TextUtils.join("$$$", list);
        } catch (Exception e) {
            SpiderDebug.log(e);
            return "";
        }
    }

    public String[] split(String str, String delimiter) {
        if (TextUtils.isEmpty(str)) return EMPTY;
        return str.split(delimiter);
    }

    public String join(String delimiter, String[] array) {
        if (array == null || array.length == 0) return "";
        return TextUtils.join(delimiter, array);
    }

    public String join(String delimiter, List<String> list) {
        if (list == null || list.isEmpty()) return "";
        return TextUtils.join(delimiter, list);
    }

    public boolean isEmpty(String str) {
        return TextUtils.isEmpty(str);
    }

    public boolean isNotEmpty(String str) {
        return !TextUtils.isEmpty(str);
    }

    public String trim(String str) {
        return str == null ? "" : str.trim();
    }

    public String substring(String str, int start, int end) {
        if (TextUtils.isEmpty(str)) return "";
        if (start < 0) start = 0;
        if (end > str.length()) end = str.length();
        return str.substring(start, end);
    }

    public String replace(String str, String target, String replacement) {
        if (TextUtils.isEmpty(str)) return "";
        return str.replace(target, replacement);
    }

    public String replaceAll(String str, String regex, String replacement) {
        if (TextUtils.isEmpty(str)) return "";
        return str.replaceAll(regex, replacement);
    }

    public boolean contains(String str, String search) {
        if (TextUtils.isEmpty(str) || TextUtils.isEmpty(search)) return false;
        return str.contains(search);
    }

    public boolean startsWith(String str, String prefix) {
        if (TextUtils.isEmpty(str) || TextUtils.isEmpty(prefix)) return false;
        return str.startsWith(prefix);
    }

    public boolean endsWith(String str, String suffix) {
        if (TextUtils.isEmpty(str) || TextUtils.isEmpty(suffix)) return false;
        return str.endsWith(suffix);
    }

    public int indexOf(String str, String search) {
        if (TextUtils.isEmpty(str) || TextUtils.isEmpty(search)) return -1;
        return str.indexOf(search);
    }

    public int lastIndexOf(String str, String search) {
        if (TextUtils.isEmpty(str) || TextUtils.isEmpty(search)) return -1;
        return str.lastIndexOf(search);
    }

    public int length(String str) {
        return str == null ? 0 : str.length();
    }

    public String toLowerCase(String str) {
        return str == null ? "" : str.toLowerCase();
    }

    public String toUpperCase(String str) {
        return str == null ? "" : str.toUpperCase();
    }
}