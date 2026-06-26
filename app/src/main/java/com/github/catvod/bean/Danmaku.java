package com.github.catvod.bean;

import android.text.TextUtils;

import com.github.catvod.utils.Json;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.Collections;
import java.util.List;

public class Danmaku {

    @SerializedName("name")
    private String name;

    @SerializedName("url")
    private String url;

    private transient boolean selected;

    public static List<Danmaku> arrayFrom(String str) {
        Type listType = new TypeToken<List<Danmaku>>() {}.getType();
        List<Danmaku> items = Json.fromJson(str, listType);
        return items == null ? Collections.emptyList() : items;
    }

    public static Danmaku from(String path) {
        Danmaku danmaku = new Danmaku();
        danmaku.setName(path);
        danmaku.setUrl(path);
        return danmaku;
    }

    public static Danmaku empty() {
        return new Danmaku();
    }

    public String getName() {
        return TextUtils.isEmpty(name) ? getUrl() : name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUrl() {
        return TextUtils.isEmpty(url) ? "" : url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    public boolean isEmpty() {
        return getUrl().isEmpty();
    }

    public String getRealUrl() {
        String u = getUrl();
        if (u.startsWith("/")) u = "file:/" + u;
        return u;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Danmaku)) return false;
        return getUrl().equals(((Danmaku) obj).getUrl());
    }

    @Override
    public String toString() {
        return Json.toJson(this);
    }
}
