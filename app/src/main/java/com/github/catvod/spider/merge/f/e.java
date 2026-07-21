package com.github.catvod.spider.merge.f;

import java.util.LinkedHashMap;
import java.util.Map;

/** WBI signature provider interface used by Bilibili-style requests. */
public interface e {

    void ensureWbi();

    boolean hasWbi();

    Map<String, String> headers();

    String wbiQuery(LinkedHashMap<String, Object> params);
}
