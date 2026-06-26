package com.github.catvod.bean;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.List;

public class Sub {

    @SerializedName("url")
    private String url;
    @SerializedName("name")
    private String name;
    @SerializedName("lang")
    private String lang;
    @SerializedName("format")
    private String format;
    @SerializedName("flag")
    private int flag;

    public static List<Sub> arrayFrom(String str) {
        Type listType = new TypeToken<List<Sub>>() {}.getType();
        return new Gson().fromJson(str, listType);
    }

    public static Sub objectFrom(String str) {
        return new Gson().fromJson(str, Sub.class);
    }

    public static Sub create() {
        return new Sub();
    }

    public Sub() {
    }

    public Sub url(String url) {
        this.url = url;
        return this;
    }

    public Sub name(String name) {
        this.name = name;
        return this;
    }

    public Sub lang(String lang) {
        this.lang = lang;
        return this;
    }

    public Sub format(String format) {
        this.format = format;
        return this;
    }

    public Sub forced() {
        this.flag = 2;
        return this;
    }

    public Sub defaultSub() {
        this.flag = 1;
        return this;
    }

    public Sub normal() {
        this.flag = 0;
        return this;
    }

    public Sub ext(String ext) {
        switch (ext.toLowerCase()) {
            case "vtt":
                return format("text/vtt");
            case "ass":
            case "ssa":
                return format("text/x-ssa");
            case "srt":
                return format("application/x-subrip");
            case "sub":
                return format("text/plain");
            case "smi":
                return format("application/smil");
            case "txt":
                return format("text/plain");
            case "dfxp":
                return format("application/ttaf+xml");
            case "ttml":
                return format("application/ttml+xml");
            default:
                return format("application/x-subrip");
        }
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getLang() {
        return lang;
    }

    public void setLang(String lang) {
        this.lang = lang;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public int getFlag() {
        return flag;
    }

    public void setFlag(int flag) {
        this.flag = flag;
    }

    public boolean hasUrl() {
        return url != null && !url.isEmpty();
    }

    public boolean hasName() {
        return name != null && !name.isEmpty();
    }

    public boolean hasLang() {
        return lang != null && !lang.isEmpty();
    }

    public boolean hasFormat() {
        return format != null && !format.isEmpty();
    }

    public boolean isForced() {
        return flag == 2;
    }

    public boolean isDefault() {
        return flag == 1;
    }

    public boolean isNormal() {
        return flag == 0;
    }

    public boolean isVtt() {
        return "text/vtt".equals(format);
    }

    public boolean isAss() {
        return "text/x-ssa".equals(format);
    }

    public boolean isSrt() {
        return "application/x-subrip".equals(format);
    }

    public boolean isSub() {
        return "text/plain".equals(format);
    }

    public boolean isSmi() {
        return "application/smil".equals(format);
    }

    public boolean isTxt() {
        return "text/plain".equals(format);
    }

    public boolean isDfxp() {
        return "application/ttaf+xml".equals(format);
    }

    public boolean isTtml() {
        return "application/ttml+xml".equals(format);
    }

    @Override
    public String toString() {
        return new GsonBuilder().disableHtmlEscaping().create().toJson(this);
    }
}