package com.github.catvod.spider.merge.b;

/** Site class bean: type key, display name and optional filter flags, used by homeContent. */
public class b {

    public String typeKey;
    public String name;
    public String filters;

    public b(String typeKey, String name, String filters) {
        this.typeKey = typeKey;
        this.name = name;
        this.filters = filters;
    }
}
