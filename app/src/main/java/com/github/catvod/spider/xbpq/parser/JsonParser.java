package com.github.catvod.spider.xbpq.parser;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * JSON提取器
 * <p>
 * 提供JSON数据的解析和提取能力，支持JSONP包装响应、嵌套对象递归查找等。
 */
public class JsonParser {

    /** JSONP包装模式：callback({...}) */
    private static final Pattern P_JSONP_WRAP = Pattern.compile(
            "^[^(]+\\((.*)\\)\\s*;?\\s*$",
            Pattern.DOTALL
    );

    /**
     * Strip // line comments and /* block comments from JSON string.
     * Does not touch // or /* inside string literals.
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
                } else if (c == '/' && i + 1 < json.length()) {
                    if (json.charAt(i + 1) == '/') {
                        // 行注释：跳过至行尾
                        while (i < json.length() && json.charAt(i) != '\n') i++;
                        continue;
                    } else if (json.charAt(i + 1) == '*') {
                        // 块注释：跳过至 */
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
     * 去除JSONP包装
     *
     * @param jsonp JSONP格式字符串
     * @return 纯JSON字符串
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
     * 解析JSON字符串
     *
     * @param json JSON字符串
     * @return JSONObject
     * @throws JSONException 解析失败时抛出
     */
    public static JSONObject parseObject(String json) throws JSONException {
        if (json == null || json.isEmpty()) return new JSONObject();
        return new JSONObject(stripJsonp(json));
    }

    /**
     * 解析JSON字符串（不抛异常）
     *
     * @param json JSON字符串
     * @return JSONObject，解析失败返回null
     */
    public static JSONObject safeParseObject(String json) {
        try {
            return parseObject(json);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 解析JSON数组字符串
     *
     * @param json JSON字符串
     * @return JSONArray
     * @throws JSONException 解析失败时抛出
     */
    public static JSONArray parseArray(String json) throws JSONException {
        if (json == null || json.isEmpty()) return new JSONArray();
        String cleaned = stripJsonp(stripComments(json.trim()));
        if (cleaned.startsWith("[")) {
            return new JSONArray(cleaned);
        }
        // 如果是对象，尝试提取第一个数组值
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
     * 视频字段候选别名表（借鉴 HHkk 的 id→playlet_id、Gold 的 episodeList→episodelist
     * 字段别名回退思路）。当规则 JSON 使用非标字段名时，仍能映射到标准语义。
     * 顺序即优先级，命中第一个非空值。
     */
    private static final String[][] FIELD_ALIASES = {
            {"vod_id", "id", "vodId", "playlet_id", "book_id", "ent_id", "vid", "movie_id", "oneId", "roomId", "series_id", "bookId", "duanjuId"},
            {"vod_name", "name", "title", "vodName", "playlet_title", "book_name", "titleTxt", "movie_name", "book_name", "video_name", "show_name"},
            {"vod_pic", "pic", "cover", "img", "image", "poster", "cover_url", "playlet_poster", "vodPic", "cdncover", "horzPoster", "vertPoster", "thumb_url", "image_link", "big_pic", "thumbnail", "big_pic", "verticalPic"},
            {"vod_remarks", "remarks", "remark", "note", "subtitle", "tag", "episodes_num_text", "upinfo", "total_episode_num", "totalChapterNum", "vod_total", "episodeCount", "viewCount"},
            {"vod_content", "content", "desc", "description", "intro", "introduce", "abstract", "book_abstract_v2", "vod_blurb", "vod_content", "blurb", "synopsis", "plot", "info", "detail", "introduction", "book_intro"},
            {"vod_director", "director", "vod_director", "dz", "daoyan", "direct"},
            {"vod_actor", "actor", "vod_actor", "zy", "zhuyan", "actors", "cast", "starring", "author"},
            {"vod_area", "area", "vod_area", "region", "country", "dq", "diqu"},
            {"vod_year", "year", "vod_year", "yd", "yand", "releaseDate", "release_date", "publish_year"},
            {"type_name", "type", "vod_type", "typename", "class", "classify", "category", "genre", "kind", "type_name"}
    };

    /** 取字段值：按语义别名依次回退（借鉴 HHkk/Gold 的多键回退） */
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
        // 未知语义：直接取键
        return obj.optString(semantic, "");
    }

    /** 递归深度上限（防御异常深层 JSON 导致栈溢出） */
    private static final int FIND_MAX_DEPTH = 20;
    /** 访问节点数上限（防御超大 JSON 对象） */
    private static final int FIND_MAX_NODES = 5000;

    /**
     * 在 JSON 树中递归查找同时含 idKey 和 nameKey 的 JSONObject（或包含该对象的 JSONArray）。
     * <p>支持字段别名回退（HHkk/Gold 思路）。</p>
     * <p>为防止外部响应极大/极深，本方法内部使用 20 层深度 + 5000 节点上限；
     * 业务侧若需要自定义阈值请使用 {@link #findTarget(Object, String, String, int, int)}。</p>
     *
     * @param obj 入口对象（JSONObject/JSONArray/其他）
     * @param idKey   必含的 id 字段名
     * @param nameKey 必含的 name 字段名
     * @return 包含id和name字段的对象，未找到返回null
     */
    public static Object findTarget(Object obj, String idKey, String nameKey) {
        return findTarget(obj, idKey, nameKey, 0, 0);
    }

    /**
     * 带深度/节点数限制的递归查找，避免外部响应巨大/嵌套过深导致 OOM/栈溢出。
     *
     * @param depth 当前递归深度（首次调用传 0）
     * @param nodes 已访问节点数（首次调用传 0）
     */
    public static Object findTarget(Object obj, String idKey, String nameKey, int depth, int nodes) {
        try {
            if (obj == null) return null;
            if (depth > FIND_MAX_DEPTH) return null;
            if (nodes > FIND_MAX_NODES) return null;
            if (obj instanceof JSONObject) {
                JSONObject object = (JSONObject) obj;
                // 优先精确匹配，其次语义别名回退（借鉴 HHkk/Gold）
                boolean idOk = object.has(idKey) || hasAlias(object, idKey);
                boolean nameOk = object.has(nameKey) || hasAlias(object, nameKey);
                if (idOk && nameOk) return object;
                JSONArray nk = object.names();
                if (nk != null) {
                    for (int i = 0; i < nk.length(); i++) {
                        String key = nk.optString(i);
                        if (key == null) continue;
                        Object r = findTarget(object.opt(key), idKey, nameKey, depth + 1, nodes + 1);
                        if (r != null) return r;
                    }
                }
            } else if (obj instanceof JSONArray) {
                JSONArray array = (JSONArray) obj;
                for (int i = 0; i < array.length(); i++) {
                    Object r = findTarget(array.get(i), idKey, nameKey, depth + 1, nodes + 1);
                    if (r != null) return array;
                }
            }
        } catch (Exception e) {
            // ignore
        }
        return null;
    }

    /** 对象是否含有某语义的任一别名键（借鉴 HHkk 字段别名回退） */
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

    /**
     * 从JSON对象中安全获取字符串值
     *
     * @param json         JSON对象
     * @param key          键名
     * @param defaultValue 默认值
     * @return 字符串值
     */
    public static String getString(JSONObject json, String key, String defaultValue) {
        if (json == null || key == null) return defaultValue;
        try {
            String value = json.optString(key, null);
            return value != null ? value : defaultValue;
        } catch (Exception e) {
            return defaultValue;
        }
    }

    /**
     * 从JSON对象中安全获取整数值
     *
     * @param json         JSON对象
     * @param key          键名
     * @param defaultValue 默认值
     * @return 整数值
     */
    public static int getInt(JSONObject json, String key, int defaultValue) {
        if (json == null || key == null) return defaultValue;
        try {
            return json.optInt(key, defaultValue);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    /**
     * 从JSON对象中安全获取布尔值
     *
     * @param json         JSON对象
     * @param key          键名
     * @param defaultValue 默认值
     * @return 布尔值
     */
    public static boolean getBoolean(JSONObject json, String key, boolean defaultValue) {
        if (json == null || key == null) return defaultValue;
        try {
            return json.optBoolean(key, defaultValue);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    /**
     * 从JSON对象中安全获取嵌套对象
     *
     * @param json JSON对象
     * @param key  键名
     * @return 嵌套对象，不存在返回null
     */
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

    /**
     * 从JSON对象中安全获取数组
     *
     * @param json JSON对象
     * @param key  键名
     * @return 数组，不存在返回null
     */
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

    /**
     * 扁平化JSON对象（将所有键值对展开到顶层）
     *
     * @param json JSON对象
     * @return 扁平化的JSON对象
     */
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
