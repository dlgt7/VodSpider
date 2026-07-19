package com.github.catvod.spider.merge.u;

import com.github.catvod.crawler.SpiderDebug;

/**
 * Short[] 字符串解码器。
 * <p>
 * 原 smali 类 {@code com.github.catvod.spider.merge.U.琴弹流水送孤舟人生几何欢}
 * （大写 U 包），提供 short[] 数组到字符串的解码能力，是 moyu/fucking 混淆框架
 * 的字符串还原核心。被 FishConfig.smali 等多处通过
 * {@code 执笔漫书半生梦青山依旧在夕阳([SIII)Ljava/lang/String;} 调用。
 * <p>
 * 此处包名降级为小写 {@code merge.u}（Windows 大小写不敏感合并目录），
 * 类名保留中文以便与原始 smali 对照。
 */
public final class 琴弹流水送孤舟人生几何欢 {

    /** 静态混淆常量（原 smali: {@code 且将新茶烹旧雪热血荐轩辕:I = -0x1cd}）。 */
    public static int 且将新茶烹旧雪热血荐轩辕 = -0x1cd;

    private 琴弹流水送孤舟人生几何欢() {}

    /**
     * 返回混淆常量（原 smali: {@code 且将新茶烹旧雪浮生皆如梦()I}）。
     * <p>计算 {@code -0x390 ^ merge.E.琴弹流水送孤舟不破楼兰誓不还.琴弹流水送孤舟冬雪压松挺千尺}。
     * 由于依赖未转换类，返回固定值。
     */
    public static int 且将新茶烹旧雪浮生皆如梦() {
        // TODO: 待 merge.E.琴弹流水送孤舟不破楼兰誓不还 转换后接入真实 XOR 计算
        return -0x390;
    }

    /**
     * 返回对象 hashCode（原 smali: {@code 执笔漫书半生梦对酒当歌时(Object)I}）。
     */
    public static int 执笔漫书半生梦对酒当歌时(Object obj) {
        return obj == null ? 0 : obj.hashCode();
    }

    /**
     * Hex 字符串解码 + XOR 解密（原 smali: {@code 执笔漫书半生梦绿绮清音弹未休(String)String}）。
     * <p>算法：构造 15 位的 hex 字母表（含随机扰动），将输入字符串每两位 hex 解码为字节，
     * 再用随机生成的密钥字符串逐字节 XOR 解密。
     * <p>由于算法含 {@link Math#random()} 扰动，无法稳定还原，仅保留方法签名。
     *
     * @return 解码后的字符串，失败时返回空串
     */
    public static String 执笔漫书半生梦绿绮清音弹未休(String input) {
        // TODO: 原 smali 含 Math.random() 扰动，需结合调用上下文还原密钥流后才能稳定解码
        try {
            return input == null ? "" : input;
        } catch (Exception e) {
            SpiderDebug.log(e);
            return "";
        }
    }

    /**
     * Short[] 数组解码为字符串（核心方法）。
     * <p>原 smali: {@code 执笔漫书半生梦青山依旧在夕阳([SIII)Ljava/lang/String;}
     * <p>算法：
     * <ol>
     *     <li>创建长度为 {@code length} 的 char 数组</li>
     *     <li>遍历 {@code 0 <= i < length}，取 {@code data[offset + i]}</li>
     *     <li>与 {@code key} 异或后转为 char</li>
     *     <li>用 char 数组构造字符串返回</li>
     * </ol>
     *
     * @param data   short[] 数据源
     * @param offset 起始偏移
     * @param length 解码长度
     * @param key    XOR 密钥
     * @return 解码后的字符串
     */
    public static String 执笔漫书半生梦青山依旧在夕阳(short[] data, int offset, int length, int key) {
        if (data == null || length <= 0) return "";
        try {
            char[] chars = new char[length];
            for (int i = 0; i < length; i++) {
                int idx = offset + i;
                if (idx < 0 || idx >= data.length) break;
                chars[i] = (char) (data[idx] ^ key);
            }
            return new String(chars);
        } catch (Exception e) {
            SpiderDebug.log(e);
            return "";
        }
    }
}
