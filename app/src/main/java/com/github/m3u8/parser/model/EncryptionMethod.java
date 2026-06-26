package com.m3u8.parser.model;

import java.util.HashMap;
import java.util.Map;

/**
 * 加密方法枚举
 */
public enum EncryptionMethod {
    NONE("NONE"),
    AES("AES-128"),
    SAMPLE_AES("SAMPLE-AES");

    private static final Map<String, EncryptionMethod> sMap = new HashMap<>();

    private final String value;

    static {
        for (EncryptionMethod method : EncryptionMethod.values()) {
            sMap.put(method.value, method);
        }
    }

    private EncryptionMethod(String value) {
        this.value = value;
    }

    public static EncryptionMethod fromValue(String value) {
        return sMap.get(value);
    }

    public String getValue() {
        return this.value;
    }
}
