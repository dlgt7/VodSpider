package com.github.catvod.bean;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.List;

public class Class {

    @SerializedName("type_id")
    private String typeId;
    @SerializedName("type_name")
    private String typeName;
    @SerializedName("type_flag")
    private String typeFlag;

    public static List<Class> arrayFrom(String str) {
        Type listType = new TypeToken<List<Class>>() {}.getType();
        return new Gson().fromJson(str, listType);
    }

    public static Class objectFrom(String str) {
        return new Gson().fromJson(str, Class.class);
    }

    public Class() {
    }

    public Class(String typeId) {
        this(typeId, typeId);
    }

    public Class(String typeId, String typeName) {
        this(typeId, typeName, null);
    }

    public Class(String typeId, String typeName, String typeFlag) {
        this.typeId = typeId;
        this.typeName = typeName;
        this.typeFlag = typeFlag;
    }

    public String getTypeId() {
        return typeId;
    }

    public void setTypeId(String typeId) {
        this.typeId = typeId;
    }

    public String getTypeName() {
        return typeName;
    }

    public void setTypeName(String typeName) {
        this.typeName = typeName;
    }

    public String getTypeFlag() {
        return typeFlag;
    }

    public void setTypeFlag(String typeFlag) {
        this.typeFlag = typeFlag;
    }

    public boolean hasTypeFlag() {
        return typeFlag != null && !typeFlag.isEmpty();
    }

    public boolean isFolder() {
        return "1".equals(typeFlag);
    }

    public boolean isFile() {
        return "0".equals(typeFlag);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Class)) return false;
        Class it = (Class) obj;
        return getTypeId().equals(it.getTypeId());
    }

    @Override
    public int hashCode() {
        return getTypeId() != null ? getTypeId().hashCode() : 0;
    }

    @Override
    public String toString() {
        return new Gson().toJson(this);
    }
}