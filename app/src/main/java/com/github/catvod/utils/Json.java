package com.github.catvod.utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;

import java.lang.reflect.Type;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Json {

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final JsonParser PARSER = new JsonParser();

    public static JsonElement parse(String json) {
        try {
            return PARSER.parseString(json);
        } catch (Throwable e) {
            return new JsonParser().parse(json);
        }
    }

    public static JsonObject safeObject(String json) {
        try {
            JsonObject obj = parse(json).getAsJsonObject();
            return obj == null ? new JsonObject() : obj;
        } catch (Throwable e) {
            return new JsonObject();
        }
    }

    public static JsonArray safeArray(String json) {
        try {
            JsonArray array = parse(json).getAsJsonArray();
            return array == null ? new JsonArray() : array;
        } catch (Throwable e) {
            return new JsonArray();
        }
    }

    public static String toJson(Object obj) {
        return GSON.toJson(obj);
    }

    public static String toJson(Object obj, boolean pretty) {
        if (pretty) {
            return new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(obj);
        }
        return GSON.toJson(obj);
    }

    public static <T> T fromJson(String json, Class<T> classOfT) {
        try {
            return GSON.fromJson(json, classOfT);
        } catch (Exception e) {
            return null;
        }
    }

    public static <T> T fromJson(String json, Type typeOfT) {
        try {
            return GSON.fromJson(json, typeOfT);
        } catch (Exception e) {
            return null;
        }
    }

    public static <T> List<T> fromJsonArray(String json, Class<T> classOfT) {
        try {
            Type listType = TypeToken.getParameterized(List.class, classOfT).getType();
            return GSON.fromJson(json, listType);
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public static <T> List<T> fromJsonArray(String json, Type typeOfT) {
        try {
            return GSON.fromJson(json, typeOfT);
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public static <K, V> Map<K, V> fromJsonMap(String json, Class<K> keyClass, Class<V> valueClass) {
        try {
            Type mapType = TypeToken.getParameterized(Map.class, keyClass, valueClass).getType();
            return GSON.fromJson(json, mapType);
        } catch (Exception e) {
            return new HashMap<>();
        }
    }

    public static Map<String, Object> toMap(String json) {
        try {
            Type type = new TypeToken<Map<String, Object>>() {}.getType();
            return GSON.fromJson(json, type);
        } catch (Exception e) {
            return new HashMap<>();
        }
    }

    public static List<Object> toList(String json) {
        try {
            Type type = new TypeToken<List<Object>>() {}.getType();
            return GSON.fromJson(json, type);
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public static String getString(JsonObject obj, String memberName) {
        return getString(obj, memberName, "");
    }

    public static String getString(JsonObject obj, String memberName, String defaultValue) {
        try {
            if (obj.has(memberName) && !obj.get(memberName).isJsonNull()) {
                return obj.get(memberName).getAsString();
            }
        } catch (Exception e) {
        }
        return defaultValue;
    }

    public static int getInt(JsonObject obj, String memberName) {
        return getInt(obj, memberName, 0);
    }

    public static int getInt(JsonObject obj, String memberName, int defaultValue) {
        try {
            if (obj.has(memberName) && !obj.get(memberName).isJsonNull()) {
                return obj.get(memberName).getAsInt();
            }
        } catch (Exception e) {
        }
        return defaultValue;
    }

    public static long getLong(JsonObject obj, String memberName) {
        return getLong(obj, memberName, 0L);
    }

    public static long getLong(JsonObject obj, String memberName, long defaultValue) {
        try {
            if (obj.has(memberName) && !obj.get(memberName).isJsonNull()) {
                return obj.get(memberName).getAsLong();
            }
        } catch (Exception e) {
        }
        return defaultValue;
    }

    public static double getDouble(JsonObject obj, String memberName) {
        return getDouble(obj, memberName, 0.0);
    }

    public static double getDouble(JsonObject obj, String memberName, double defaultValue) {
        try {
            if (obj.has(memberName) && !obj.get(memberName).isJsonNull()) {
                return obj.get(memberName).getAsDouble();
            }
        } catch (Exception e) {
        }
        return defaultValue;
    }

    public static boolean getBoolean(JsonObject obj, String memberName) {
        return getBoolean(obj, memberName, false);
    }

    public static boolean getBoolean(JsonObject obj, String memberName, boolean defaultValue) {
        try {
            if (obj.has(memberName) && !obj.get(memberName).isJsonNull()) {
                return obj.get(memberName).getAsBoolean();
            }
        } catch (Exception e) {
        }
        return defaultValue;
    }

    public static JsonObject getJsonObject(JsonObject obj, String memberName) {
        try {
            if (obj.has(memberName) && !obj.get(memberName).isJsonNull()) {
                return obj.get(memberName).getAsJsonObject();
            }
        } catch (Exception e) {
        }
        return new JsonObject();
    }

    public static JsonArray getJsonArray(JsonObject obj, String memberName) {
        try {
            if (obj.has(memberName) && !obj.get(memberName).isJsonNull()) {
                return obj.get(memberName).getAsJsonArray();
            }
        } catch (Exception e) {
        }
        return new JsonArray();
    }

    public static boolean has(JsonObject obj, String memberName) {
        return obj != null && obj.has(memberName);
    }

    public static boolean isNull(JsonObject obj, String memberName) {
        return obj == null || !obj.has(memberName) || obj.get(memberName).isJsonNull();
    }

    public static boolean isNotNull(JsonObject obj, String memberName) {
        return obj != null && obj.has(memberName) && !obj.get(memberName).isJsonNull();
    }

    public static JsonObject merge(JsonObject obj1, JsonObject obj2) {
        JsonObject result = new JsonObject();
        if (obj1 != null) {
            for (Map.Entry<String, JsonElement> entry : obj1.entrySet()) {
                result.add(entry.getKey(), entry.getValue());
            }
        }
        if (obj2 != null) {
            for (Map.Entry<String, JsonElement> entry : obj2.entrySet()) {
                result.add(entry.getKey(), entry.getValue());
            }
        }
        return result;
    }

    public static JsonArray merge(JsonArray arr1, JsonArray arr2) {
        JsonArray result = new JsonArray();
        if (arr1 != null) {
            for (JsonElement element : arr1) {
                result.add(element);
            }
        }
        if (arr2 != null) {
            for (JsonElement element : arr2) {
                result.add(element);
            }
        }
        return result;
    }

    public static String format(String json) {
        try {
            JsonElement element = parse(json);
            return new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(element);
        } catch (Exception e) {
            return json;
        }
    }

    public static String minify(String json) {
        try {
            JsonElement element = parse(json);
            return new GsonBuilder().disableHtmlEscaping().create().toJson(element);
        } catch (Exception e) {
            return json;
        }
    }

    public static boolean isValid(String json) {
        try {
            parse(json);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isObject(String json) {
        try {
            return parse(json).isJsonObject();
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isArray(String json) {
        try {
            return parse(json).isJsonArray();
        } catch (Exception e) {
            return false;
        }
    }

    public static JsonObject createObject() {
        return new JsonObject();
    }

    public static JsonArray createArray() {
        return new JsonArray();
    }

    public static JsonObject createObject(Map<String, Object> map) {
        JsonObject obj = new JsonObject();
        if (map != null) {
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                obj.add(entry.getKey(), GSON.toJsonTree(entry.getValue()));
            }
        }
        return obj;
    }

    public static JsonArray createArray(List<?> list) {
        JsonArray arr = new JsonArray();
        if (list != null) {
            for (Object item : list) {
                arr.add(GSON.toJsonTree(item));
            }
        }
        return arr;
    }

    public static String escape(String text) {
        return GSON.toJson(text);
    }

    public static String unescape(String text) {
        return GSON.fromJson(text, String.class);
    }

    // ==================== JSON路径解析 ====================

    /**
     * 按JSON路径获取值，返回字符串列表
     * 支持点分路径、通配符*、数组索引过滤如 [0]、[1:3]
     */
    public static ArrayList<String> pathGet(String json, String path, String pattern) {
        ArrayList<String> result = new ArrayList<>();
        if (json == null || path == null) return result;
        try {
            JsonElement element = pathParseSafe(json);
            if (element != null) {
                JsonElement found = pathFindBy(element, path);
                if (found != null) {
                    return pathConvertToList(found, pattern, 1);
                }
            }
        } catch (Exception e) {
            com.github.catvod.crawler.SpiderDebug.log("Parse json error: " + e.getMessage());
            json = pathFix(json);
            try {
                JsonElement element = pathParseSafe(json);
                if (element != null) {
                    JsonElement found = pathFindBy(element, path);
                    if (found != null) {
                        return pathConvertToList(found, pattern, 1);
                    }
                }
            } catch (Exception e2) {
                com.github.catvod.crawler.SpiderDebug.log("Parse json error 2: " + e2.getMessage());
            }
        }
        return result;
    }

    /**
     * 修复JSON格式（检查是否可解析）
     */
    public static String pathFix(String json) {
        if (json == null) return "";
        try {
            PARSER.parse(json);
            return json;
        } catch (Exception ignored) {
        }
        return json;
    }

    /**
     * 按路径查找JSON元素
     */
    public static JsonElement pathFindBy(JsonElement root, String path) {
        if (path == null || root == null) return null;
        String SEP = ".";
        // * 通配符替换为点分隔
        String p = path;
        if (p.indexOf(SEP) < 0 && p.indexOf('*') >= 0) {
            p = p.replace("*", SEP);
        }
        // 规范化：去首尾分隔符
        if (p.startsWith(SEP)) p = p.substring(1);
        if (p.endsWith(SEP)) p = p.substring(0, p.length() - 1);
        // 同时兼容 . / | 作为分组分隔符（| 为旧版 Spider 规则）
        String[] parts = p.split("[./|]");
        JsonElement current = root;
        for (String part : parts) {
            if (current == null) return null;
            if (current.isJsonObject()) {
                JsonObject obj = current.getAsJsonObject();
                // 先拆分字段名与下标（如 items[0] → fieldName=items, filter=[0]）
                int bracketIdx = part.indexOf('[');
                String fieldName = bracketIdx >= 0 ? part.substring(0, bracketIdx) : part;
                String filterPart = bracketIdx >= 0 ? part.substring(bracketIdx) : "";
                if ("*".equals(fieldName) && filterPart.isEmpty()) {
                    JsonArray arr = new JsonArray();
                    for (Map.Entry<String, JsonElement> entry : obj.entrySet()) arr.add(entry.getValue());
                    current = arr;
                } else if (!obj.has(fieldName)) {
                    return null;
                } else {
                    current = obj.get(fieldName);
                }
                // 若字段值是数组，继续处理后缀过滤
                if (!filterPart.isEmpty() && current != null && current.isJsonArray()) {
                    current = applyIndexFilter(current.getAsJsonArray(), filterPart);
                }
            } else if (current.isJsonArray()) {
                JsonArray arr = current.getAsJsonArray();
                // 检查是否含字段名过滤（如 items[0].name）
                int bracketIdx = part.indexOf('[');
                String fieldName = bracketIdx >= 0 ? part.substring(0, bracketIdx) : part;
                String filterPart = bracketIdx >= 0 ? part.substring(bracketIdx) : "";
                // 先按字段名过滤数组元素
                if (!fieldName.isEmpty()) {
                    JsonArray filtered = new JsonArray();
                    for (JsonElement elem : arr) {
                        if (elem.isJsonObject() && elem.getAsJsonObject().has(fieldName)) {
                            filtered.add(elem.getAsJsonObject().get(fieldName));
                        }
                    }
                    if (!filtered.isEmpty()) current = filtered;
                }
                // 再处理索引/范围过滤
                if (!filterPart.isEmpty() && current != null && current.isJsonArray()) {
                    current = applyIndexFilter(current.getAsJsonArray(), filterPart);
                }
            } else {
                return null;
            }
        }
        return current;
    }

    /**
     * 对 JsonArray 应用 [n] 或 [n:m] 下标过滤
     */
    private static JsonElement applyIndexFilter(JsonArray arr, String filterPart) {
        Matcher m = Pattern.compile("\\[(\\d+)(:(\\d+))?\\]").matcher(filterPart);
        JsonArray result = new JsonArray();
        if (m.find()) {
            int index = parseIntIndex(arr, m.group(1));
            if (m.group(2) != null) {
                int end = parseIntIndex(arr, m.group(3));
                for (int j = 0; j < arr.size(); j++) {
                    if (index >= 0 && end >= 0 && j >= index && j <= end)
                        result.add(arr.get(j));
                }
            } else if (index >= 0) {
                if (index < arr.size()) result.add(arr.get(index));
            } else {
                result = arr;
            }
        } else {
            result = arr;
        }
        return result;
    }

    /**
     * 将JSON元素转换为字符串列表
     * 支持 JsonArray、JsonObject（序列化为JSON字符串）、JsonPrimitive
     */
    public static ArrayList<String> pathConvertToList(JsonElement element, String pattern, int index) {
        ArrayList<String> list = new ArrayList<>();
        if (element == null) { list.add(""); return list; }
        if (element.isJsonArray()) {
            JsonArray arr = element.getAsJsonArray();
            for (int i = 0; i < arr.size(); i++) {
                ArrayList<String> sub = pathConvertToList(arr.get(i), pattern, i + 1);
                for (String s : sub) {
                    String trimmed = s.trim();
                    if (!trimmed.isEmpty()) list.add(trimmed);
                }
            }
            if (list.isEmpty()) list.add("");
            return list;
        }
        if (element.isJsonObject()) {
            list.add(GSON.toJson(element));
            return list;
        }
        if (!element.isJsonPrimitive()) { list.add(""); return list; }
        String value = element.getAsString();
        // pattern 支持：正则提取，如 ".*?([\\w]+)" 取第一个捕获组
        if (pattern != null && !pattern.isEmpty() && !pattern.equals("$0")) {
            try {
                java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
                java.util.regex.Matcher m = p.matcher(value);
                if (m.find()) {
                    // 有捕获组则取第一组，否则取全文
                    value = m.groupCount() > 0 ? m.group(1) : m.group(0);
                }
            } catch (Exception ignored) { }
        }
        if (!value.isEmpty()) list.add(value);
        if (list.isEmpty()) list.add("");
        return list;
    }

    private static int parseIntIndex(JsonArray arr, String str) {
        try {
            int size = Integer.parseInt(str);
            if (size > 0) size--;
            else if (size < 0) size += arr.size();
            return size;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static JsonElement pathParseSafe(String json) {
        try {
            JsonReader reader = new JsonReader(new StringReader(json));
            reader.setLenient(true);
            return JsonParser.parseReader(reader);
        } catch (Exception e) {
            try {
                return PARSER.parse(json);
            } catch (Exception e2) {
                return null;
            }
        }
    }
}
