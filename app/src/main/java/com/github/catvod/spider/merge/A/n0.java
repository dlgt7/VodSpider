package com.github.catvod.spider.merge.a;

import com.github.catvod.crawler.SpiderDebug;

import java.util.HashMap;

/**
 * 弹幕操作提示工具。
 * <p>
 * 原始 smali 通过 moyu/fucking 重度混淆（sparse-switch + 中文诗名反射调用器）
 * 实现 Toast 弹窗与日志输出。此处简化为：静态工厂方法记录消息并输出日志，
 * 保留字段结构便于后续接入实际 UI 反馈。
 * <p>
 * 被引用为 {@code merge.A.N0}（大写），实际类名为 {@code merge.a.n0}（小写）。
 * 因 Windows 文件系统大小写不敏感，两者在 smali 中合并为同一文件。
 */
public final class n0 {

    /** 提示类型标识（原 smali 通过混淆常量传入）。 */
    public final int a;

    /** 提示主文本。 */
    public final String b;

    public String c;
    public String d;
    public String e;
    public String f;
    public String g;
    public String h;
    public String i;
    public String j;
    public int k;
    public boolean l;
    public HashMap m;

    public n0(int type, String message) {
        this.a = type;
        this.b = message == null ? "" : message;
    }

    /**
     * 创建提示并输出日志。
     * <p>原 smali: {@code merge.A.N0.a(String)} 通过反射调用器显示 Toast；
     * 此处简化为日志输出，避免在非 UI 线程触发 Toast 异常。
     *
     * @param message 提示文本
     * @return 提示实例
     */
    public static n0 a(String message) {
        n0 tip = new n0(0, message);
        try {
            if (message != null && !message.isEmpty()) {
                SpiderDebug.log(message);
            }
        } catch (Exception ex) {
            SpiderDebug.log(ex);
        }
        return tip;
    }
}
