package com.github.catvod.utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
}
