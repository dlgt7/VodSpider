package com.github.catvod.spider;

import android.content.Context;
import android.text.TextUtils;
import android.util.Base64;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.net.OkHttp;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.math.BigInteger;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * 悦听吧爬虫（有声书）
 * 网站地址：http://www.yuetingba.cn/
 */
public class YueTingBa extends Spider {

    private static final byte[] AES_KEY = Base64.decode("le95G3hnFDJsBE+1/v9eYw==", 0);
    private static final byte[] AES_IV = Base64.decode("IvswQFEUdKYf+d1wKpYLTg==", 0);
    private static final Pattern YEAR_PATTERN = Pattern.compile("(?:19|20)\\d{2}");
    private static final String SITE_URL = "http://www.yuetingba.cn/";
    private static final String SITE_BASE = "http://www.yuetingba.cn";
    private static final String CIPHER_ALGORITHM = "AES/CBC/PKCS5Padding";
    private static final String CIPHER_TYPE = "AES";
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36";
    private static final String PLAY_FROM = "悦听吧";
    private static final String TOKEN_SUFFIX = "|xMiP5W1DHBxC5PwQ5oj5QfRn0tsT5UBk";
    private static final String SECRET_SUFFIX = "xMiP5W1DHBxC5PwQ5oj5QfRn0tsT5UBk";
    private static final String BOOKS_DIR = "/myfiles/host/listen/booksdir/";
    private static final String API_PATH = "/api/app/docs-listen/";
    private static final String DETAIL_PREFIX = "http://www.yuetingba.cn/book/detail/";
    private static final String DETAIL_SUFFIX = "/0";
    private static final String TING_EFI = "/ting-with-efi";

    private static final ConcurrentHashMap<String, BookInfo> cache = new ConcurrentHashMap<>();

    /** 有声书信息（对应 merge.f.f3） */
    private static class BookInfo {
        public String id = "";
        public String url = "";
        public String name = "";
        public String content = "";
        public String pid = "";
        public JSONArray audios;
    }

    /** 播放信息（对应 merge.f.g3） */
    private static class PlayInfo {
        public String url = "";
        public String baseUrl = "";
    }

    /** URL修复（对应原 a 方法） */
    private static String fixUrl(String str) {
        if (TextUtils.isEmpty(str)) {
            return SITE_URL;
        }
        if (str.startsWith("//")) {
            return "http:" + str;
        }
        if (str.startsWith("/")) {
            return SITE_BASE + str;
        }
        return str.startsWith("http") ? str : SITE_URL + str;
    }

    /** AES解密（对应原 b 方法） */
    private static String aesDecrypt(String str, byte[] key, byte[] iv) throws Exception {
        Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, CIPHER_TYPE), new IvParameterSpec(iv));
        return new String(cipher.doFinal(Base64.decode(str.replace("\n", "").trim(), 0)), StandardCharsets.UTF_8);
    }

    /** 构建播放URL（对应原 c 方法）：bookId@@pid@@epId */
    private static String buildPlayUrl(BookInfo info, String epId) {
        return info.id + "@@" + info.pid + "@@" + epId;
    }

    /** 解析选集列表（对应原 d 方法） */
    private static void parseEpisodes(Document doc, BookInfo info, LinkedHashMap<String, String> map) {
        for (Element item : doc.select(".ting-list-content-item")) {
            String epId = extractRegex(item.attr("id"), "^item_([\\w-]+)$");
            if (TextUtils.isEmpty(epId)) {
                epId = extractRegex(item.attr("onclick"), "testFn\\('([^']+)'\\)");
            }
            Element linkEl = item.selectFirst("a[title][onclick*=testFn]");
            String title = "";
            if (linkEl != null) {
                title = firstNonEmpty(linkEl.attr("title"), linkEl.text());
            }
            String cleanTitle = cleanText(title);
            if (!TextUtils.isEmpty(epId)) {
                String playUrl = buildPlayUrl(info, epId);
                if (!map.containsValue(playUrl)) {
                    map.put(firstNonEmpty(cleanTitle, String.valueOf(map.size() + 1)), playUrl);
                }
            }
        }
    }

    /** 派生密钥和IV（对应原 e 方法） */
    private static byte[][] deriveKeyIv(String str, String str2) {
        int i;
        String key = str.replace("-", "");
        String salt = str2.replace("-", "").replace(":", "").replace("T", "").replace(".", "").replace(" ", "");
        while (true) {
            i = 20;
            if (salt.length() >= 20) {
                break;
            }
            salt = salt.concat("0");
        }
        byte[] keyBytes = new byte[key.length()];
        for (int i2 = 0; i2 < 20; i2++) {
            keyBytes[i2] = (byte) (key.charAt(i2) + getDigit(i2, salt));
        }
        for (int i3 = 20; i3 < key.length(); i3++) {
            keyBytes[i3] = (byte) (key.charAt(i3) + getDigit(i3 - 20, salt));
        }
        byte[] ivBytes = new byte[16];
        int j = 0;
        while (i > 4) {
            ivBytes[j] = (byte) (key.charAt(i) + getDigit(i - 1, salt));
            i--;
            j++;
        }
        return new byte[][]{keyBytes, ivBytes};
    }

    /** 提取数字字符（对应原 f 方法） */
    private static int getDigit(int i, String str) {
        char c;
        if (i < 0 || i >= str.length() || (c = str.charAt(i)) < '0' || c > '9') {
            return 0;
        }
        return c - '0';
    }

    /** 返回第一个非空字符串（对应原 g 方法） */
    private static String firstNonEmpty(String... strs) {
        for (String str : strs) {
            if (!TextUtils.isEmpty(str)) {
                return str;
            }
        }
        return "";
    }

    /** 构建请求头（对应原 h 方法） */
    private static HashMap<String, String> buildHeaders(String referer) {
        HashMap<String, String> map = new HashMap<>();
        map.put("User-Agent", USER_AGENT);
        map.put("Accept", "*/*");
        if (TextUtils.isEmpty(referer)) {
            referer = SITE_URL;
        }
        map.put("Referer", referer);
        return map;
    }

    /** 提取JS变量值（对应原 i 方法） */
    private static String extractVar(String str, String varName) {
        if (str == null) {
            str = "";
        }
        Matcher matcher = Pattern.compile("var\\s+" + Pattern.quote(varName) + "\\s*=\\s*'([^']*)'").matcher(str);
        return matcher.find() ? matcher.group(1) : "";
    }

    /** MD5哈希（对应原 j 方法） */
    private static String md5(String str) {
        try {
            StringBuilder sb = new StringBuilder(new BigInteger(1, MessageDigest.getInstance("MD5").digest(str.getBytes(StandardCharsets.UTF_8))).toString(16));
            while (sb.length() < 32) {
                sb.insert(0, '0');
            }
            return sb.toString().toLowerCase();
        } catch (Exception e) {
            return "";
        }
    }

    /** 清理文本（对应原 k 方法） */
    private static String cleanText(String str) {
        return str == null ? "" : str.replace((char) 160, ' ').replaceAll("\\s+", " ").trim();
    }

    /** 解析详情页（对应原 l 方法） */
    private static BookInfo parseDetail(String url, Document doc) {
        BookInfo info = new BookInfo();
        String html = doc.outerHtml();
        info.id = firstNonEmpty(extractRegex(url, "/book/detail/([^/]+)/"), extractVar(html, "bookId"));
        info.url = extractVar(html, "tingId");
        info.name = extractVar(html, "tingTitle");
        info.content = extractVar(html, "assl");
        info.pid = extractVar(html, "py");
        String encrypted = info.content.replace(SECRET_SUFFIX, "");
        if (!TextUtils.isEmpty(encrypted)) {
            try {
                info.audios = new JSONArray(aesDecrypt(encrypted, AES_KEY, AES_IV));
            } catch (Exception e) {
            }
        }
        return info;
    }

    /** 选择最佳音频（对应原 n 方法） */
    private static JSONObject selectBestAudio(ArrayList<JSONObject> list, boolean preferNamed) {
        JSONObject result = null;
        for (JSONObject obj : list) {
            String name = obj.optString("Name");
            if (!preferNamed || (name != null && (name.endsWith("_b") || name.endsWith("_p")))) {
                result = obj;
            }
        }
        return result;
    }

    /** 查找播放信息（对应原 o 方法） */
    private static PlayInfo findPlayInfo(JSONArray audios, String bookId) {
        if (audios == null) {
            return null;
        }
        String searchId = bookId;
        if (searchId.contains("-")) {
            searchId = searchId.substring(searchId.lastIndexOf('-') + 1);
        }
        ArrayList<JSONObject> all = new ArrayList<>();
        ArrayList<JSONObject> matched = new ArrayList<>();
        ArrayList<JSONObject> empty = new ArrayList<>();
        for (int i = 0; i < audios.length(); i++) {
            JSONObject obj = audios.optJSONObject(i);
            if (obj != null) {
                if ("1".equals(obj.optString("AsType"))) {
                    if ("A".equals(obj.optString("Type"))) {
                        all.add(obj);
                        String bookIds = obj.optString("BookIds").trim();
                        if (!TextUtils.isEmpty(bookIds) && bookIds.contains(searchId)) {
                            matched.add(obj);
                        } else if (TextUtils.isEmpty(bookIds)) {
                            empty.add(obj);
                        }
                    }
                }
            }
        }
        JSONObject best = selectBestAudio(matched, true);
        if (best == null) best = selectBestAudio(empty, true);
        if (best == null) best = selectBestAudio(matched, false);
        if (best == null) best = selectBestAudio(empty, false);
        if (best == null) best = selectBestAudio(all, false);
        if (best == null) {
            return null;
        }
        PlayInfo info = new PlayInfo();
        info.url = best.optString("Name");
        info.baseUrl = best.optString("Scheme") + "://" + best.optString("Value") + ":" + best.optString("Port");
        return info;
    }

    /** 正则提取第一个分组（对应原 p 方法） */
    private static String extractRegex(String str, String regex) {
        if (str == null) {
            str = "";
        }
        Matcher matcher = Pattern.compile(regex).matcher(str);
        return matcher.find() ? matcher.group(1) : "";
    }

    /** 查找标签值（对应原 q 方法）：在 li/p/div 中查找以 "label：" 或 "label:" 开头的文本 */
    private static String findLabel(String label, Document doc) {
        for (Element el : doc.select("li, p, div")) {
            String text = cleanText(el.text());
            if (text.startsWith(label + "：") || text.startsWith(label + ":")) {
                return cleanText(text.substring(label.length() + 1));
            }
        }
        return "";
    }

    /** 选择第一个有文本的元素文本（对应原 r 方法） */
    private static String selectText(Element el, String... selectors) {
        for (String sel : selectors) {
            Element found = el.selectFirst(sel);
            if (found != null && !TextUtils.isEmpty(found.text())) {
                return cleanText(found.text());
            }
        }
        return "";
    }

    /** 获取并解析页面（对应原 s 方法） */
    private static Document fetchAndParse(String url) {
        try {
            String fixedUrl = fixUrl(url);
            return Jsoup.parse(OkHttp.string(fixedUrl, buildHeaders(url)), fixedUrl);
        } catch (Exception e) {
            return Jsoup.parse("");
        }
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        int page;
        try {
            page = Math.max(1, Integer.parseInt(pg));
        } catch (Exception e) {
            page = 1;
        }
        String url;
        if ("recommend".equals(tid)) {
            url = "/top/recommend/" + page;
        } else if ("latest".equals(tid) || TextUtils.isEmpty(tid)) {
            url = "/top/latest/" + page;
        } else {
            url = "/book/" + tid + "/" + page;
        }
        ArrayList<Vod> list = parseList(fetchAndParse(url));
        int pageCount = list.isEmpty() ? page : page + 1;
        int limit = Math.max(list.size(), 1);
        return Result.string(page, pageCount, limit, Integer.MAX_VALUE, list);
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        if (ids == null || ids.isEmpty()) {
            return Result.string(new ArrayList<>());
        }
        String detailUrl = fixUrl(ids.get(0));
        Document doc = fetchAndParse(detailUrl);
        BookInfo info = parseDetail(detailUrl, doc);
        if (!TextUtils.isEmpty(info.id)) {
            cache.put(info.id, info);
        }
        String name = firstNonEmpty(selectText(doc, ".book-detail-title", "h1", "title"));
        Element imgEl = doc.selectFirst("img.img-thumbnail, .book-detail-img img, .book-img img, meta[property=og:image]");
        String pic = imgEl != null ? fixUrl(firstNonEmpty(imgEl.attr("src"), imgEl.attr("content"))) : "";
        String remarks = firstNonEmpty(findLabel("状态", doc), findLabel("集数", doc));
        String area = firstNonEmpty(findLabel("类型", doc), findLabel("分类", doc));
        String actor = firstNonEmpty(findLabel("主播", doc), findLabel("播音", doc));
        String director = findLabel("作者", doc);
        Matcher matcher = YEAR_PATTERN.matcher(doc.text());
        String year = matcher.find() ? matcher.group() : "";
        Element metaDesc = doc.selectFirst("meta[name=description]");
        String metaContent = "";
        if (metaDesc != null) {
            metaContent = metaDesc.attr("content");
        }
        String content = firstNonEmpty(metaContent, selectText(doc, ".book-detail-intro", ".book-detail-content", ".intro"));

        LinkedHashMap<String, String> episodes = new LinkedHashMap<>();
        LinkedHashSet<String> urls = new LinkedHashSet<>();
        urls.add(detailUrl);
        for (Element link : doc.select("a[href*=/book/detail/]")) {
            String href = fixUrl(link.attr("href")).split("\\?")[0];
            if (href.matches(".*/book/detail/[^/]+/\\d+.*")) {
                urls.add(href);
            }
        }
        for (String u : urls) {
            parseEpisodes(fetchAndParse(u), info, episodes);
        }
        if (episodes.isEmpty()) {
            parseEpisodes(doc, info, episodes);
        }
        if (episodes.isEmpty() && !TextUtils.isEmpty(info.url)) {
            episodes.put(firstNonEmpty(info.name, "播放"), buildPlayUrl(info, info.url));
        }
        ArrayList<String> playList = new ArrayList<>();
        for (Map.Entry<String, String> entry : episodes.entrySet()) {
            playList.add(entry.getKey() + "$" + entry.getValue());
        }
        Vod vod = new Vod(detailUrl, name, pic, remarks);
        vod.setVodYear(year);
        vod.setVodArea(area);
        vod.setVodActor(actor);
        vod.setVodDirector(director);
        vod.setVodContent(content);
        vod.setVodPlayFrom(PLAY_FROM);
        vod.setVodPlayUrl(TextUtils.join("#", playList));
        return Result.string(vod);
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        ArrayList<Class> classes = new ArrayList<>();
        classes.add(new Class("latest", "最新"));
        classes.add(new Class("recommend", "推荐"));
        classes.add(new Class("1", "玄幻"));
        classes.add(new Class("4", "都市"));
        classes.add(new Class("2", "历史"));
        classes.add(new Class("6", "名著"));
        classes.add(new Class("7", "女频"));
        classes.add(new Class("5", "科幻"));
        classes.add(new Class("3", "武侠"));
        classes.add(new Class("a", "评书"));
        classes.add(new Class("8", "社科"));
        return Result.string(classes, parseList(fetchAndParse("/top/latest/1")));
    }

    @Override
    public String homeVideoContent() throws Exception {
        return categoryContent("latest", "1", false, new HashMap<>());
    }

    @Override
    public void init(Context context, String extend) throws Exception {
        super.init(context, extend);
        Init.init(context);
    }

    /** 解析列表页（对应原 m 方法） */
    private ArrayList<Vod> parseList(Document doc) {
        ArrayList<Vod> list = new ArrayList<>();
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        String hrefKey = "href";
        String detailSel = "a[href*=/book/detail/]";
        String pic = "";
        for (Element next : doc.select(".section-box-list-item")) {
            Element itemEl = next.selectFirst(detailSel);
            if (itemEl != null) {
                String itemUrl = fixUrl(itemEl.attr(hrefKey));
                if (seen.add(itemUrl)) {
                    Element imgEl = next.selectFirst("img");
                    String[] titleSources = new String[3];
                    titleSources[0] = selectText(next, ".box-list-item-text-title a");
                    titleSources[1] = imgEl != null ? imgEl.attr("alt") : pic;
                    titleSources[2] = itemEl.text();
                    String title = firstNonEmpty(titleSources);
                    if (imgEl != null) {
                        pic = fixUrl(imgEl.attr("src"));
                    }
                    String remarks = firstNonEmpty(
                            selectText(next, ".box-list-item-text-title span"),
                            selectText(next, ".box-list-item-text-intro"),
                            selectText(next, ".box-list-item-text-author"),
                            selectText(next, ".box-list-item-text"));
                    if (!TextUtils.isEmpty(title)) {
                        list.add(new Vod(itemUrl, title, pic, remarks));
                    }
                }
            }
        }
        if (!list.isEmpty()) {
            return list;
        }
        for (Element el : doc.select(detailSel)) {
            String itemUrl = fixUrl(el.attr(hrefKey));
            if (seen.add(itemUrl)) {
                String title = cleanText(el.text());
                if (!TextUtils.isEmpty(title)) {
                    list.add(new Vod(itemUrl, title, "", ""));
                }
            }
        }
        return list;
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        String emptyUrl = "";
        try {
            if (id.contains("$")) {
                id = id.substring(id.lastIndexOf('$') + 1);
            }
            String[] parts = id.split("@@", 3);
            if (parts.length < 3) {
                return Result.get().url(emptyUrl).parse(0).header(buildHeaders(SITE_URL)).string();
            }
            String bookId = parts[0];
            String pid = parts[1];
            String epId = parts[2];
            BookInfo info = cache.get(bookId);
            if (info == null || TextUtils.isEmpty(info.content)) {
                String detailUrl = DETAIL_PREFIX + bookId + DETAIL_SUFFIX;
                info = parseDetail(detailUrl, fetchAndParse(detailUrl));
                if (TextUtils.isEmpty(info.pid)) {
                    info.pid = pid;
                }
                cache.put(bookId, info);
            }
            JSONObject json;
            try {
                json = new JSONObject(OkHttp.string(fixUrl(API_PATH + epId + TING_EFI), buildHeaders(SITE_URL)));
            } catch (Exception e) {
                json = new JSONObject();
            }
            String efi = json.optString("efi");
            String creationTime = json.optString("creationTime");
            if (!TextUtils.isEmpty(efi) && !TextUtils.isEmpty(creationTime)) {
                byte[][] keyIv = deriveKeyIv(epId, creationTime);
                String decrypted = aesDecrypt(efi, keyIv[0], keyIv[1]);
                if (decrypted.endsWith("/")) {
                    decrypted = decrypted.substring(0, decrypted.length() - 1);
                }
                int lastSlash = decrypted.lastIndexOf('/');
                String fileName = lastSlash >= 0 ? decrypted.substring(lastSlash + 1) : decrypted;
                PlayInfo playInfo = findPlayInfo(info.audios, bookId);
                if (playInfo == null) {
                    return Result.get().url(emptyUrl).parse(0).header(buildHeaders(SITE_URL)).string();
                }
                if (!playInfo.url.endsWith("_p")) {
                    long expireTime = (System.currentTimeMillis() / 1000) + 600;
                    return Result.get().url(playInfo.baseUrl + decrypted + "?token=" + md5(fileName + "|" + expireTime + TOKEN_SUFFIX) + "&expire=" + expireTime).parse(0).header(buildHeaders(SITE_URL)).string();
                }
                decrypted = "/" + info.pid + "_" + bookId + "/" + fileName;
                long expireTime = (System.currentTimeMillis() / 1000) + 600;
                return Result.get().url(playInfo.baseUrl + decrypted + "?token=" + md5(fileName + "|" + expireTime + TOKEN_SUFFIX) + "&expire=" + expireTime).parse(0).header(buildHeaders(SITE_URL)).string();
            }
            return Result.get().url(emptyUrl).parse(0).header(buildHeaders(SITE_URL)).string();
        } catch (Exception e) {
            e.getMessage();
            return Result.get().url(emptyUrl).parse(0).header(buildHeaders(SITE_URL)).string();
        }
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        return searchContent(key, quick, "1");
    }

    @Override
    public String searchContent(String key, boolean quick, String pg) throws Exception {
        if (TextUtils.isEmpty(key)) {
            return Result.string(new ArrayList<>());
        }
        try {
            ArrayList<Vod> list = parseList(fetchAndParse("/Search?type=1&name=" + URLEncoder.encode(key.trim(), "UTF-8")));
            return Result.string(1, list.isEmpty() ? 1 : 2, Math.max(list.size(), 1), Integer.MAX_VALUE, list);
        } catch (Exception e) {
            e.getMessage();
            return Result.string(new ArrayList<>());
        }
    }
}
