package com.github.catvod.bean;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.List;

public class Filter {

    @SerializedName("key")
    private String key;
    @SerializedName("name")
    private String name;
    @SerializedName("init")
    private String init;
    @SerializedName("value")
    private List<Value> value;
    @SerializedName("type")
    private String type;
    @SerializedName("required")
    private Boolean required;
    @SerializedName("multiple")
    private Boolean multiple;

    public static List<Filter> arrayFrom(String str) {
        Type listType = new TypeToken<List<Filter>>() {}.getType();
        return new Gson().fromJson(str, listType);
    }

    public static Filter objectFrom(String str) {
        return new Gson().fromJson(str, Filter.class);
    }

    public Filter() {
    }

    public Filter(String key, String name) {
        this.key = key;
        this.name = name;
    }

    public Filter(String key, String name, List<Value> value) {
        this.key = key;
        this.name = name;
        this.value = value;
    }

    public Filter(String key, String name, List<Value> value, String init) {
        this.key = key;
        this.name = name;
        this.value = value;
        this.init = init;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getInit() {
        return init;
    }

    public void setInit(String init) {
        this.init = init;
    }

    public List<Value> getValue() {
        return value;
    }

    public void setValue(List<Value> value) {
        this.value = value;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Boolean getRequired() {
        return required;
    }

    public void setRequired(Boolean required) {
        this.required = required;
    }

    public Boolean getMultiple() {
        return multiple;
    }

    public void setMultiple(Boolean multiple) {
        this.multiple = multiple;
    }

    public boolean hasKey() {
        return key != null && !key.isEmpty();
    }

    public boolean hasName() {
        return name != null && !name.isEmpty();
    }

    public boolean hasInit() {
        return init != null && !init.isEmpty();
    }

    public boolean hasValue() {
        return value != null && !value.isEmpty();
    }

    public boolean hasType() {
        return type != null && !type.isEmpty();
    }

    public boolean isRequired() {
        return required != null && required;
    }

    public boolean isMultiple() {
        return multiple != null && multiple;
    }

    public boolean isSingle() {
        return multiple != null && !multiple;
    }

    public boolean isOptional() {
        return required == null || !required;
    }

    @Override
    public String toString() {
        return new GsonBuilder().disableHtmlEscaping().create().toJson(this);
    }

    public static class Value {

        @SerializedName("n")
        private String n;
        @SerializedName("v")
        private String v;

        public Value() {
        }

        public Value(String value) {
            this.n = value;
            this.v = value;
        }

        public Value(String n, String v) {
            this.n = n;
            this.v = v;
        }

        public String getN() {
            return n;
        }

        public void setN(String n) {
            this.n = n;
        }

        public String getV() {
            return v;
        }

        public void setV(String v) {
            this.v = v;
        }

        public String getName() {
            return n;
        }

        public void setName(String name) {
            this.n = name;
        }

        public String getValue() {
            return v;
        }

        public void setValue(String value) {
            this.v = value;
        }

        public boolean hasName() {
            return n != null && !n.isEmpty();
        }

        public boolean hasValue() {
            return v != null && !v.isEmpty();
        }

        @Override
        public String toString() {
            return new GsonBuilder().disableHtmlEscaping().create().toJson(this);
        }
    }
}