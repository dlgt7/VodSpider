package com.github.catvod.spider.merge.g;

import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.spider.FishCrypto;
import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 弹幕配置主类（单例）。
 * <p>
 * 管理弹幕功能总开关、自动启动/推送、显示对话框、随机位置/颜色、
 * 启用的弹幕源平台列表、弹幕颜色列表，以及 AI 匹配相关配置
 * （API Key / Base URL / 模型名）。
 * <p>
 * 配置以 JSON 形式持久化到 {@code .danmu_config} 存储键（原 smali 通过
 * {@code merge.A.l1.f/g} 读写，此处替换为 {@link FishCrypto#ssRead} /
 * {@link FishCrypto#ssWrite} 等价安全存储）。
 */
public class a {

    /** 存储键。 */
    private static final String STORAGE_KEY = ".danmu_config";

    /** 默认弹幕服务端口。 */
    private static final int DEFAULT_PORT = 0x9dd;

    /** 默认 AI 服务地址。 */
    private static final String DEFAULT_AI_BASE_URL = "https://api.deepseek.com";

    /** 默认 AI 模型名。 */
    private static final String DEFAULT_AI_MODEL = "deepseek-chat";

    /** 默认启用的弹幕源平台列表。 */
    private static final List<String> DEFAULT_PLATFORMS = Arrays.asList(
            "BL", "QY", "TX", "YK", "MG", "HJ", "RR", "XG", "LS", "MDD"
    );

    /** 默认弹幕颜色列表。 */
    private static final List<String> DEFAULT_COLORS = Arrays.asList(
            "#FF0000", "#00FF00", "#0000FF", "#FFFF00", "#FF00FF", "#00FFFF", "#FFA500"
    );

    /** 单例实例。 */
    public static a n;

    /** 是否已从存储加载过配置。 */
    public static boolean o;

    @SerializedName("port")
    private int a = DEFAULT_PORT;

    @SerializedName("enabled")
    private boolean b = true;

    @SerializedName("auto_start")
    private boolean c = true;

    @SerializedName("auto_push")
    private boolean d = true;

    @SerializedName("show_dialog")
    private boolean e = false;

    @SerializedName("random_position")
    private boolean f = true;

    @SerializedName("random_color")
    private boolean g = false;

    @SerializedName("enabled_platforms")
    private List<String> h = new ArrayList<>(DEFAULT_PLATFORMS);

    @SerializedName("colors")
    private List<String> i = new ArrayList<>(DEFAULT_COLORS);

    @SerializedName("ai_enabled")
    private boolean j = false;

    @SerializedName("ai_api_key")
    private String k = "";

    @SerializedName("ai_base_url")
    private String l = DEFAULT_AI_BASE_URL;

    @SerializedName("ai_model")
    private String m = DEFAULT_AI_MODEL;

    public a() {
        // 字段默认值已在声明处给出，构造器保持空实现以兼容 Gson 反序列化
    }

    /**
     * 获取单例。
     * <p>首次调用时从 {@code .danmu_config} 加载；加载失败或为空时返回默认实例。
     */
    public static a a() {
        if (n == null || !o) {
            try {
                String json = FishCrypto.ssRead(STORAGE_KEY);
                if (json != null && !json.isEmpty()) {
                    o = true;
                    n = Json.fromJson(json, a.class);
                }
            } catch (Exception ex) {
                SpiderDebug.log(ex);
            }
            if (n == null) {
                n = new a();
            }
        }
        return n;
    }

    // =====================================================================
    // 端口
    // =====================================================================

    /** 重置端口为默认值并持久化。 */
    public final void y() {
        a = DEFAULT_PORT;
        o();
    }

    // =====================================================================
    // 弹幕总开关
    // =====================================================================

    /** 弹幕功能是否启用。 */
    public final boolean j() {
        return b;
    }

    /** 设置弹幕功能开关并持久化。 */
    public final void w(boolean enabled) {
        b = enabled;
        o();
    }

    // =====================================================================
    // 自动启动 / 自动推送
    // =====================================================================

    /** 启用自动启动并持久化。 */
    public final void u() {
        c = true;
        o();
    }

    /** 是否启用自动推送。 */
    public final boolean i() {
        return d;
    }

    /** 设置自动推送开关并持久化。 */
    public final void t(boolean autoPush) {
        d = autoPush;
        o();
    }

    // =====================================================================
    // 显示对话框 / 随机位置 / 随机颜色
    // =====================================================================

    /** 是否显示对话框。 */
    public final boolean n() {
        return e;
    }

    /** 设置是否显示对话框并持久化。 */
    public final void B(boolean showDialog) {
        e = showDialog;
        o();
    }

    /** 是否随机位置。 */
    public final boolean m() {
        return f;
    }

    /** 设置随机位置开关并持久化。 */
    public final void A(boolean randomPosition) {
        f = randomPosition;
        o();
    }

    /** 是否随机颜色。 */
    public final boolean l() {
        return g;
    }

    /** 设置随机颜色开关并持久化。 */
    public final void z(boolean randomColor) {
        g = randomColor;
        o();
    }

    // =====================================================================
    // 启用的弹幕源平台
    // =====================================================================

    /** 返回启用的弹幕源平台列表。 */
    public final List<String> g() {
        return h;
    }

    /** 设置启用的弹幕源平台列表并持久化。 */
    public final void x(List<String> platforms) {
        h = platforms;
        o();
    }

    /** 判断指定平台是否已启用。 */
    public final boolean k(String platform) {
        return h != null && h.contains(platform);
    }

    // =====================================================================
    // 弹幕颜色
    // =====================================================================

    /** 设置弹幕颜色列表并持久化。 */
    public final void v(List<String> colors) {
        i = colors;
        o();
    }

    // =====================================================================
    // AI 匹配配置
    // =====================================================================

    /** 是否启用 AI 匹配。 */
    public final boolean h() {
        return j;
    }

    /** 设置 AI 匹配开关并持久化。 */
    public final void r(boolean aiEnabled) {
        j = aiEnabled;
        o();
    }

    /** 获取 AI API Key（可能为 null，调用方应判空）。 */
    public final String b() {
        return k == null ? "" : k;
    }

    /** 设置 AI API Key 并持久化。 */
    public final void p(String apiKey) {
        k = apiKey;
        o();
    }

    /** 获取 AI Base URL（可能为 null，调用方应判空）。 */
    public final String c() {
        return l == null ? "" : l;
    }

    /** 获取 AI Base URL，为空时返回默认值。 */
    public final String e() {
        return (l != null && !l.isEmpty()) ? l : DEFAULT_AI_BASE_URL;
    }

    /** 设置 AI Base URL 并持久化。 */
    public final void q(String baseUrl) {
        l = baseUrl;
        o();
    }

    /** 获取 AI 模型名（可能为 null，调用方应判空）。 */
    public final String d() {
        return m == null ? "" : m;
    }

    /** 获取 AI 模型名，为空时返回默认值。 */
    public final String f() {
        return (m != null && !m.isEmpty()) ? m : DEFAULT_AI_MODEL;
    }

    /** 设置 AI 模型名并持久化。 */
    public final void s(String model) {
        m = model;
        o();
    }

    // =====================================================================
    // 持久化
    // =====================================================================

    /**
     * 将当前配置序列化为 JSON 并写入存储。
     * <p>原 smali: {@code merge.A.l1.g(".danmu_config", new Gson().toJson(this))}
     * 此处用 {@link Json#toJson(Object)} 统一封装，含异常兜底。
     */
    public final void o() {
        try {
            String json = Json.toJson(this);
            FishCrypto.ssWrite(STORAGE_KEY, json);
            o = true;
        } catch (Exception ex) {
            SpiderDebug.log(ex);
        }
    }
}
