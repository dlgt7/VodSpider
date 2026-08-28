package com.github.catvod.spider.xbpq.parser;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * JSON parser utility.
 * Supports JSONP unwrapping, nested object lookup, and comment stripping.
 */
public class JsonParser {

    /** JSONP wrapper pattern: callback({...}) */
    private static final Pattern P_JSONP_WRAP = Pattern.compile(
            "^[^(]+\\((.*)\\)\\s*;?\\s*$",
            Pattern.DOTALL
    );

    /**
     * Remove comments (double-slash line comments and slash-star block comments)
     * from a JSON string.
     * Does NOT remove double-slash or slash-star sequences inside string literals.
     */
    public static String stripComments(String json) {
        if (json == null || json.isEmpty()) return json;
        StringBuilder sb = new StringBuilder();
        boolean inString = false;
        char quoteChar = 0;
        boolean esc = false;
        int i = 0;
        while (i < json.length()) {
            char c = json.charAt(i);
            if (esc) {
                esc = false;
                if (inString) sb.append(c);
                i++;
                continue;
            }
            if (c == '\\') {
                esc = true;
                if (inString) sb.append(c);
                i++;
                continue;
            }
            if (!inString) {
                if (c == '"' || c == '\'') {
                    inString = true;
                    quoteChar = c;
                    sb.append(c);
                    // 修复：原实现此处缺少 continue，落到底部 sb.append(c) 又追加一次，
                    // 每个字符串的【开引号被双写】（"站名" → ""站名"），任何含字符串的
                    // 规则 JSON 经 stripComments 后全部损坏，解析得空对象——表现为
                    // 全站分类/首页/搜索空白。现开引号只追加一次。
                    i++;
                    continue;
                } else if (c == '/' && i + 1 < json.length()) {
                    if (json.charAt(i + 1) == '/') {
                        // line comment: skip to end of line
                        while (i < json.length() && json.charAt(i) != '\n') i++;
                        continue;
                    } else if (json.charAt(i + 1) == '*') {
                        // block comment: skip to */
                        i += 2;
                        while (i + 1 < json.length() && !(json.charAt(i) == '*' && json.charAt(i + 1) == '/')) i++;
                        i += 2;
                        continue;
                    }
                }
                sb.append(c);
            } else {
                sb.append(c);
                if (c == quoteChar) inString = false;
            }
            i++;
        }
        return sb.toString();
    }

    /**
     * Strip JSONP wrapper, returning pure JSON string.
     *
     * @param jsonp JSONP-formatted string
     * @return raw JSON string
     */
    public static String stripJsonp(String jsonp) {
        if (jsonp == null || jsonp.isEmpty()) return jsonp;
        try {
            Matcher m = P_JSONP_WRAP.matcher(jsonp.trim());
            if (m.find()) {
                return m.group(1);
            }
        } catch (Exception e) {
            // not JSONP format, return as-is
        }
        return jsonp;
    }

    /**
     * Parse a JSON string into a JSONObject.
     *
     * @param json JSON string
     * @return parsed JSONObject
     * @throws JSONException if parsing fails
     */
    public static JSONObject parseObject(String json) throws JSONException {
        if (json == null || json.isEmpty()) return new JSONObject();
        // 修复：原先遗漏 stripComments，而 XBPQ 规则文件普遍采用带 // 注释的 JSONC 写法
        //（xBPQ 目录下 10+ 个规则因此整体加载失败，表现为"站点打不开/空白"）。
        // parseArray 早已调用 stripComments，此处补齐以保持一致。
        return new JSONObject(stripJsonp(stripComments(json.trim())));
    }

    /**
     * Parse a JSON string into a JSONObject (non-throwing).
     *
     * @param json JSON string
     * @return parsed JSONObject, or null on failure
     */
    public static JSONObject safeParseObject(String json) {
        try {
            return parseObject(json);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Parse a JSON string into a JSONArray.
     *
     * @param json JSON string
     * @return parsed JSONArray
     * @throws JSONException if parsing fails
     */
    public static JSONArray parseArray(String json) throws JSONException {
        if (json == null || json.isEmpty()) return new JSONArray();
        String cleaned = stripJsonp(stripComments(json.trim()));
        if (cleaned.startsWith("[")) {
            return new JSONArray(cleaned);
        }
        // if it is an object, try to extract the first array value
        JSONObject obj = new JSONObject(cleaned);
        JSONArray nk = obj.names();
        if (nk != null) {
            for (int i = 0; i < nk.length(); i++) {
                String key = nk.optString(i);
                if (key == null) continue;
                Object val = obj.opt(key);
                if (val instanceof JSONArray) {
                    return (JSONArray) val;
                }
            }
        }
        return new JSONArray();
    }

    /**
     * Video field alias table.
     * Non-standard field names (id, name, pic, etc.) map to standard vod_* keys.
     * Order = priority; first non-empty match wins.
     */
    private static final String[][] FIELD_ALIASES = {
            {"vod_id", "id", "vodId", "playlet_id", "book_id", "ent_id", "vid", "movie_id", "oneId", "roomId", "series_id", "bookId", "duanjuId"},
            {"vod_name", "name", "title", "vodName", "playlet_title", "book_name", "titleTxt", "movie_name", "book_name", "video_name", "show_name"},
            {"vod_pic", "pic", "cover", "img", "image", "poster", "cover_url", "playlet_poster", "vodPic", "cdncover", "horzPoster", "vertPoster", "thumb_url", "image_link", "big_pic", "thumbnail", "verticalPic"},
            {"vod_remarks", "remarks", "remark", "note", "subtitle", "tag", "episodes_num_text", "upinfo", "total_episode_num", "totalChapterNum", "vod_total", "episodeCount", "viewCount"},
            {"vod_content", "content", "desc", "description", "intro", "introduce", "abstract", "book_abstract_v2", "vod_blurb", "blurb", "synopsis", "plot", "info", "detail", "introduction", "book_intro"},
            {"vod_director", "director", "vod_director", "dz", "daoyan", "direct"},
            {"vod_actor", "actor", "vod_actor", "zy", "zhuyan", "actors", "cast", "starring", "author"},
            {"vod_area", "area", "vod_area", "region", "country", "dq", "diqu"},
            {"vod_year", "year", "vod_year", "yd", "yand", "releaseDate", "release_date", "publish_year"},
            {"type_name", "type", "vod_type", "typename", "class", "classify", "category", "genre", "kind", "type_name"}
    };

    /** Pick field value by semantic key, falling back to aliases (HHkk/Gold approach). */
    public static String pickField(JSONObject obj, String semantic) {
        for (String[] group : FIELD_ALIASES) {
            if (group[0].equals(semantic)) {
                for (String key : group) {
                    if (obj.has(key)) {
                        String v = obj.optString(key, "");
                        if (!v.isEmpty()) return v;
                    }
                }
                return "";
            }
        }
        // unknown semantic: direct lookup
        return obj.optString(semantic, "");
    }

    /** Recursion depth limit (guard against stack overflow on deeply nested JSON). */
    private static final int FIND_MAX_DEPTH = 20;
    /** Max visited nodes (guard against OOM on huge JSON). */
    private static final int FIND_MAX_NODES = 5000;

    /**
     * Recursively find a JSONObject (or JSONArray containing one) that has both idKey and nameKey.
     *
     * @param obj     root object/array
     * @param idKey   required id field name
     * @param nameKey required name field name
     * @return matching object, or null
     */
    public static Object findTarget(Object obj, String idKey, String nameKey) {
        return findTarget(obj, idKey, nameKey, 0, 0);
    }

    /**
     * Recursively find with depth/node limits.
     *
     * @param depth current recursion depth (pass 0 on first call)
     * @param nodes current visited node count (pass 0 on first call)
     */
    public static Object findTarget(Object obj, String idKey, String nameKey, int depth, int nodes) {
        // 用共享计数器承载 nodes：若继续以 nodes + 1 传值，兄弟节点之间不会累加，
        // 计数器恒等于 depth，FIND_MAX_NODES 形同虚设（宽 JSON 只能靠 depth 兜底）。
        return findTarget(obj, idKey, nameKey, depth, new int[]{nodes});
    }

    /**
     * Recursively find with depth/node limits.
     *
     * @param depth current recursion depth (pass 0 on first call)
     * @param nodes shared visited-node counter (pass {@code new int[]{0}} on first call)
     */
    private static Object findTarget(Object obj, String idKey, String nameKey, int depth, int[] nodes) {
        try {
            if (obj == null) return null;
            if (depth > FIND_MAX_DEPTH) return null;
            if (++nodes[0] > FIND_MAX_NODES) return null;
            if (obj instanceof JSONObject) {
                JSONObject object = (JSONObject) obj;
                boolean idOk = object.has(idKey) || hasAlias(object, idKey);
                boolean nameOk = object.has(nameKey) || hasAlias(object, nameKey);
                if (idOk && nameOk) return object;
                JSONArray nk = object.names();
                if (nk != null) {
                    for (int i = 0; i < nk.length(); i++) {
                        String key = nk.optString(i);
                        if (key == null) continue;
                        Object r = findTarget(object.opt(key), idKey, nameKey, depth + 1, nodes);
                        if (r != null) return r;
                    }
                }
            } else if (obj instanceof JSONArray) {
                JSONArray array = (JSONArray) obj;
                for (int i = 0; i < array.length(); i++) {
                    Object elem = array.opt(i);
                    if (elem == null) continue;
                    Object r = findTarget(elem, idKey, nameKey, depth + 1, nodes);
                    if (r != null) return array;
                }
            }
        } catch (Exception e) {
            // ignore
        }
        return null;
    }

    /** Check if obj has any alias of the given semantic key with a non-empty value. */
    private static boolean hasAlias(JSONObject obj, String semantic) {
        for (String[] group : FIELD_ALIASES) {
            if (group[0].equals(semantic)) {
                for (String key : group) {
                    if (obj.has(key) && !obj.optString(key, "").isEmpty()) return true;
                }
                return false;
            }
        }
        return false;
    }

    /** Get string value safely. */
    public static String getString(JSONObject json, String key, String defaultValue) {
        if (json == null || key == null) return defaultValue;
        try {
            String value = json.optString(key, null);
            return value != null ? value : defaultValue;
        } catch (Exception e) {
            return defaultValue;
        }
    }

    /** Get int value safely. */
    public static int getInt(JSONObject json, String key, int defaultValue) {
        if (json == null || key == null) return defaultValue;
        try {
            return json.optInt(key, defaultValue);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    /** Get boolean value safely. */
    public static boolean getBoolean(JSONObject json, String key, boolean defaultValue) {
        if (json == null || key == null) return defaultValue;
        try {
            return json.optBoolean(key, defaultValue);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    /** Get nested JSONObject safely. */
    public static JSONObject getObject(JSONObject json, String key) {
        if (json == null || key == null) return null;
        try {
            Object val = json.opt(key);
            if (val instanceof JSONObject) return (JSONObject) val;
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    /** Get nested JSONArray safely. */
    public static JSONArray getArray(JSONObject json, String key) {
        if (json == null || key == null) return null;
        try {
            Object val = json.opt(key);
            if (val instanceof JSONArray) return (JSONArray) val;
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    /** Flatten a JSONObject into a single-level map with dot-notation keys. */
    public static JSONObject flatten(JSONObject json) {
        JSONObject result = new JSONObject();
        if (json == null) return result;
        try {
            flattenRecursive(json, "", result);
        } catch (Exception e) {
            // ignore
        }
        return result;
    }

    private static void flattenRecursive(JSONObject source, String prefix, JSONObject target) throws JSONException {
        JSONArray nk = source.names();
        if (nk != null) {
            for (int i = 0; i < nk.length(); i++) {
                String key = nk.optString(i);
                if (key == null) continue;
                String fullKey = prefix.isEmpty() ? key : prefix + "." + key;
                Object val = source.opt(key);
                if (val instanceof JSONObject) {
                    flattenRecursive((JSONObject) val, fullKey, target);
                } else if (val instanceof JSONArray) {
                    flattenRecursive((JSONArray) val, fullKey, target);
                } else {
                    target.put(fullKey, val);
                }
            }
        }
    }

    private static void flattenRecursive(JSONArray array, String prefix, JSONObject target) throws JSONException {
        for (int i = 0; i < array.length(); i++) {
            String fullKey = prefix + "[" + i + "]";
            Object val = array.get(i);
            if (val instanceof JSONObject) {
                flattenRecursive((JSONObject) val, fullKey, target);
            } else if (val instanceof JSONArray) {
                flattenRecursive((JSONArray) val, fullKey, target);
            } else {
                target.put(fullKey, val);
            }
        }
    }
}
