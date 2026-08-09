package com.github.catvod.bean.XBPQ;

import org.json.JSONObject;

/**
 * XBPQ 配置访问器。
 *
 * <p>封装中文/拼音/英文三套命名体系的配置读取，统一为单个
 * {@link #get(String, String...)} 变参方法。依次尝试每个键名，
 * 返回首个非空值；全为空则返回默认值。这是 XBPQ "配置驱动"架构的核心机制。</p>
 *
 * <p>同时提供 {@link #getUrl(String, String, String)} URL 拼接器，
 * 以及布尔/整数类型安全转换。</p>
 *
 * <p>由 {@code XBPQ.XBPQConfig} 内部类提取为顶层类，便于独立复用与测试。</p>
 */
public class XBPQConfig {

    private final JSONObject config;

    public XBPQConfig(JSONObject config) {
        this.config = config == null ? new JSONObject() : config;
    }

    public JSONObject raw() {
        return config;
    }

    public boolean has(String key) {
        return config.has(key) && !config.optString(key).isEmpty();
    }

    /**
     * 多键名配置读取：依次尝试 def 及每个键名（中文→拼音→英文），返回首个非空值。
     *
     * @param def  默认值（也作为首个候选键名检查）
     * @param keys 候选键名列表
     * @return 首个非空配置值，或默认值
     */
    public String get(String def, String... keys) {
        if (def != null && !def.isEmpty()) {
            String value = config.optString(def, "");
            if (!value.isEmpty()) return value;
        }
        if (keys == null) return def;
        for (String key : keys) {
            if (key == null || key.isEmpty()) continue;
            String value = config.optString(key, "");
            if (!value.isEmpty()) return value;
        }
        return def;
    }

    /** 单键名读取，空时返回 ""。 */
    public String get(String key) {
        return get("", key);
    }

    /**
     * URL 拼接器。
     *
     * <p>将 url 模板中的 {@code key} 占位符替换为 value，并按规则拼接 host 前缀：</p>
     * <ul>
     *   <li>value 以 {@code http} 开头 → 直接使用</li>
     *   <li>value 以 {@code //} 开头 → 拼接 {@code https:}</li>
     *   <li>value 以 {@code /} 开头 → 拼接 host</li>
     *   <li>否则 → 拼接 host + "/"</li>
     * </ul>
     */
    public String getUrl(String urlTemplate, String host, String value) {
        if (value == null || value.isEmpty()) return urlTemplate;
        String full;
        if (value.startsWith("http")) {
            full = value;
        } else if (value.startsWith("//")) {
            full = "https:" + value;
        } else if (value.startsWith("/")) {
            full = host + value;
        } else {
            full = host + "/" + value;
        }
        return urlTemplate == null ? full : urlTemplate.replace("{cateId}", full);
    }

    /** 安全布尔转换：值为 "1"/"true"/"是" 时返回 true。 */
    public boolean getBool(String def, String... keys) {
        String value = get(def, keys);
        return "1".equals(value) || "true".equalsIgnoreCase(value) || "是".equals(value);
    }

    /** 安全整数转换：解析失败返回 def。 */
    public int getInt(int def, String... keys) {
        try {
            String value = get(String.valueOf(def), keys);
            return value.isEmpty() ? def : Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
