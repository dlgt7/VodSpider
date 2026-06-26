package com.github.catvod.bean;

import com.github.catvod.utils.Util;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;

import org.json.JSONObject;

import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Result {

    @SerializedName("class")
    private List<Class> classes;
    @SerializedName("list")
    private List<Vod> list;
    @SerializedName("filters")
    private LinkedHashMap<String, List<Filter>> filters;
    @SerializedName("header")
    private String header;
    @SerializedName("format")
    private String format;
    @SerializedName("danmaku")
    private List<Danmaku> danmaku;
    @SerializedName("click")
    private String click;
    @SerializedName("msg")
    private String msg;
    @SerializedName("url")
    private Object url;
    @SerializedName("subs")
    private List<Sub> subs;
    @SerializedName("parse")
    private int parse;
    @SerializedName("jx")
    private int jx;
    @SerializedName("page")
    private Integer page;
    @SerializedName("pagecount")
    private Integer pagecount;
    @SerializedName("limit")
    private Integer limit;
    @SerializedName("total")
    private Integer total;
    @SerializedName("code")
    private Integer code;
    @SerializedName("drm")
    private Object drm;
    @SerializedName("ext")
    private Map<String, String> ext;
    @SerializedName("extra")
    private Map<String, Object> extra;

    public static Result objectFrom(String str) {
        return new Gson().fromJson(str, Result.class);
    }

    public static String string(List<Class> classes, List<Vod> list, LinkedHashMap<String, List<Filter>> filters) {
        return Result.get().classes(classes).vod(list).filters(filters).string();
    }

    public static String string(List<Class> classes, List<Vod> list, JSONObject filters) {
        return Result.get().classes(classes).vod(list).filters(filters).string();
    }

    public static String string(List<Class> classes, List<Vod> list, JsonElement filters) {
        return Result.get().classes(classes).vod(list).filters(filters).string();
    }

    public static String string(List<Class> classes, LinkedHashMap<String, List<Filter>> filters) {
        return Result.get().classes(classes).filters(filters).string();
    }

    public static String string(List<Class> classes, JsonElement filters) {
        return Result.get().classes(classes).filters(filters).string();
    }

    public static String string(List<Class> classes, JSONObject filters) {
        return Result.get().classes(classes).filters(filters).string();
    }

    public static String string(List<Class> classes, List<Vod> list) {
        return Result.get().classes(classes).vod(list).string();
    }

    public static String string(List<?> list) {
        if (list == null || list.isEmpty()) return "";
        if (list.get(0) instanceof Vod) return Result.get().vod((List<Vod>) list).string();
        if (list.get(0) instanceof Class) return Result.get().classes((List<Class>) list).string();
        return "";
    }

    public static String string(Vod item) {
        return Result.get().vod(item).string();
    }

    public static String error(String msg) {
        return Result.get().vod(Collections.emptyList()).msg(msg).code(500).string();
    }

    public static String notify(String msg) {
        return Result.get().msg(msg).code(200).string();
    }

    public static Result get() {
        return new Result();
    }

    public Result classes(List<Class> classes) {
        this.classes = classes;
        return this;
    }

    public Result vod(List<Vod> list) {
        this.list = list;
        return this;
    }

    public Result vod(Vod item) {
        this.list = Arrays.asList(item);
        return this;
    }

    public Result filters(LinkedHashMap<String, List<Filter>> filters) {
        this.filters = filters;
        return this;
    }

    public Result filters(JSONObject object) {
        if (object == null) return this;
        Type listType = new TypeToken<LinkedHashMap<String, List<Filter>>>() {}.getType();
        this.filters = new Gson().fromJson(object.toString(), listType);
        return this;
    }

    public Result filters(JsonElement element) {
        if (element == null) return this;
        Type listType = new TypeToken<LinkedHashMap<String, List<Filter>>>() {}.getType();
        this.filters = new Gson().fromJson(element.toString(), listType);
        return this;
    }

    public Result header(Map<String, String> header) {
        if (header.isEmpty()) return this;
        this.header = new Gson().toJson(header);
        return this;
    }

    public Result chrome() {
        Map<String, String> header = new HashMap<>();
        header.put("User-Agent", Util.CHROME);
        header(header);
        return this;
    }

    public Result parse() {
        this.parse = 1;
        return this;
    }

    public Result parse(int parse) {
        this.parse = parse;
        return this;
    }

    public Result jx() {
        this.jx = 1;
        return this;
    }

    public Result url(String url) {
        this.url = url;
        return this;
    }

    public Result url(List<String> url) {
        this.url = url;
        return this;
    }

    public Result danmaku(List<Danmaku> danmaku) {
        this.danmaku = danmaku;
        return this;
    }

    public Result click(String click) {
        this.click = click;
        return this;
    }

    public Result msg(String msg) {
        this.msg = msg;
        return this;
    }

    public Result format(String format) {
        this.format = format;
        return this;
    }

    public Result subs(List<Sub> subs) {
        this.subs = subs;
        return this;
    }

    public Result dash() {
        this.format = "application/dash+xml";
        return this;
    }

    public Result m3u8() {
        this.format = "application/x-mpegURL";
        return this;
    }

    public Result rtsp() {
        this.format = "application/x-rtsp";
        return this;
    }

    public Result octet() {
        this.format = "application/octet-stream";
        return this;
    }

    public Result mp4() {
        this.format = "video/mp4";
        return this;
    }

    public Result mkv() {
        this.format = "video/x-matroska";
        return this;
    }

    public Result webm() {
        this.format = "video/webm";
        return this;
    }

    public Result flv() {
        this.format = "video/x-flv";
        return this;
    }

    public Result hls() {
        this.format = "application/vnd.apple.mpegurl";
        return this;
    }

    public Result mp3() {
        this.format = "audio/mpeg";
        return this;
    }

    public Result wav() {
        this.format = "audio/wav";
        return this;
    }

    public Result ogg() {
        this.format = "audio/ogg";
        return this;
    }

    public Result aac() {
        this.format = "audio/aac";
        return this;
    }

    public Result flac() {
        this.format = "audio/flac";
        return this;
    }

    public Result m4a() {
        this.format = "audio/mp4";
        return this;
    }

    public Result page() {
        return page(1, 1, 0, 1);
    }

    public Result page(int page, int count, int limit, int total) {
        this.page = page > 0 ? page : Integer.MAX_VALUE;
        this.limit = limit > 0 ? limit : Integer.MAX_VALUE;
        this.total = total > 0 ? total : Integer.MAX_VALUE;
        this.pagecount = count > 0 ? count : Integer.MAX_VALUE;
        return this;
    }

    public Result code(int code) {
        this.code = code;
        return this;
    }

    public Result drm(Object drm) {
        this.drm = drm;
        return this;
    }

    public Result ext(Map<String, String> ext) {
        this.ext = ext;
        return this;
    }

    public Result extra(Map<String, Object> extra) {
        this.extra = extra;
        return this;
    }

    public List<Vod> getList() {
        return list == null ? Collections.emptyList() : list;
    }

    public List<Class> getClasses() {
        return classes == null ? Collections.emptyList() : classes;
    }

    public LinkedHashMap<String, List<Filter>> getFilters() {
        return filters == null ? new LinkedHashMap<>() : filters;
    }

    public String getHeader() {
        return header == null ? "" : header;
    }

    public String getFormat() {
        return format == null ? "" : format;
    }

    public List<Danmaku> getDanmaku() {
        return danmaku == null ? Collections.emptyList() : danmaku;
    }

    public String getClick() {
        return click == null ? "" : click;
    }

    public String getMsg() {
        return msg == null ? "" : msg;
    }

    public Object getUrl() {
        return url;
    }

    public List<Sub> getSubs() {
        return subs == null ? Collections.emptyList() : subs;
    }

    public int getParse() {
        return parse;
    }

    public int getJx() {
        return jx;
    }

    public Integer getPage() {
        return page == null ? 1 : page;
    }

    public Integer getPageCount() {
        return pagecount == null ? 1 : pagecount;
    }

    public Integer getLimit() {
        return limit == null ? 0 : limit;
    }

    public Integer getTotal() {
        return total == null ? 0 : total;
    }

    public Integer getCode() {
        return code == null ? 0 : code;
    }

    public Object getDrm() {
        return drm;
    }

    public Map<String, String> getExt() {
        return ext == null ? new HashMap<>() : ext;
    }

    public Map<String, Object> getExtra() {
        return extra == null ? new HashMap<>() : extra;
    }

    public boolean hasMsg() {
        return !getMsg().isEmpty();
    }

    public boolean hasUrl() {
        return getUrl() != null && !getUrl().toString().isEmpty();
    }

    public boolean hasSubs() {
        return !getSubs().isEmpty();
    }

    public boolean hasDanmaku() {
        return !getDanmaku().isEmpty();
    }

    public boolean hasFilters() {
        return !getFilters().isEmpty();
    }

    public boolean hasHeader() {
        return !getHeader().isEmpty();
    }

    public boolean hasExt() {
        return !getExt().isEmpty();
    }

    public boolean hasExtra() {
        return !getExtra().isEmpty();
    }

    public boolean isSuccess() {
        return getCode() == 0 || getCode() == 200;
    }

    public boolean isError() {
        return getCode() != 0 && getCode() != 200;
    }

    public String string() {
        return toString();
    }

    @Override
    public String toString() {
        return new Gson().newBuilder().disableHtmlEscaping().create().toJson(this);
    }
}
