package com.github.catvod.spider.merge.A;

import com.google.gson.JsonObject;

/** JSON parser helper that converts a stored string into a {@link JsonObject}. */
public class z0 {

    public static JsonObject c(String json) {
        try {
            return com.google.gson.JsonParser.parseString(json).getAsJsonObject();
        } catch (Exception ignored) {
            return new JsonObject();
        }
    }
}
