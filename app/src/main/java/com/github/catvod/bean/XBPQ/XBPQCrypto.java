package com.github.catvod.bean.XBPQ;

import android.util.Base64;

import com.github.catvod.crawler.SpiderDebug;

import java.nio.charset.StandardCharsets;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * XBPQ 加密/解密工具类。
 *
 * <p>包含 AES/CTR/CBC 加解密、Base64 编解码、SHA-1 摘要、HaB 异或解密等纯算法工具。
 * 所有方法均为静态，不依赖 {@link XBPQ} 实例状态，可独立复用。</p>
 *
 * <p>由 {@code XBPQ} 的以下方法提取合并：</p>
 * <ul>
 *   <li>{@code encrypt / decrypt / getToken} — AES/CTR/PKCS5Padding 加解密</li>
 *   <li>{@code aesDecrypt} — 通用 AES 解密（CBC/CTR 等，支持自定义 transformation）</li>
 *   <li>{@code decryptHex} — HaB 异或解密（hex → XOR({@value #XOR_KEY}) → UTF-8）</li>
 * </ul>
 */
public final class XBPQCrypto {

    /** HaB 异或解密密钥。 */
    private static final String XOR_KEY = "wxEesU";

    private XBPQCrypto() {
    }

    /** AES/CTR/PKCS5Padding 加密。 */
    public static String encrypt(String data, String charset, String key, String iv) {
        try {
            Cipher cipher = Cipher.getInstance("AES/CTR/PKCS5Padding");
            SecretKeySpec keySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "AES");
            IvParameterSpec ivSpec = new IvParameterSpec(iv.getBytes(StandardCharsets.UTF_8));
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);
            byte[] encrypted = cipher.doFinal(data.getBytes(charset));
            return Base64.encodeToString(encrypted, Base64.NO_WRAP);
        } catch (Exception e) {
            SpiderDebug.log(e);
            return null;
        }
    }

    /** AES/CTR/PKCS5Padding 解密。 */
    public static String decrypt(String data, String charset, String key, String iv) {
        try {
            Cipher cipher = Cipher.getInstance("AES/CTR/PKCS5Padding");
            SecretKeySpec keySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "AES");
            IvParameterSpec ivSpec = new IvParameterSpec(iv.getBytes(StandardCharsets.UTF_8));
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);
            byte[] decrypted = cipher.doFinal(Base64.decode(data, Base64.DEFAULT));
            return new String(decrypted, charset);
        } catch (Exception e) {
            SpiderDebug.log(e);
            return null;
        }
    }

    public static String getToken(String data, String charset, String key, String iv) {
        return encrypt(data, charset, key, iv);
    }

    /** AES 解密通用方法。 */
    public static String aesDecrypt(String encrypted, String key, String iv, String mode) {
        try {
            String transformation = mode.isEmpty() ? "AES/CBC/PKCS5Padding" : mode;
            Cipher cipher = Cipher.getInstance(transformation);
            SecretKeySpec keySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "AES");
            if (transformation.contains("CBC")) {
                cipher.init(Cipher.DECRYPT_MODE, keySpec, new IvParameterSpec(iv.getBytes(StandardCharsets.UTF_8)));
            } else {
                cipher.init(Cipher.DECRYPT_MODE, keySpec);
            }
            byte[] decoded = Base64.decode(encrypted, Base64.DEFAULT);
            byte[] decrypted = cipher.doFinal(decoded);
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 字符串异或解密：hex → byte[] → 与 {@value #XOR_KEY} 循环 XOR → UTF-8 字符串。
     */
    public static String decryptHex(String hex) {
        try {
            int byteLen = hex.length() / 2;
            byte[] bytes = new byte[byteLen];
            for (int i = 0; i < byteLen; i++) {
                bytes[i] = (byte) ((Character.digit(hex.charAt(i * 2), 16) << 4)
                        + Character.digit(hex.charAt(i * 2 + 1), 16));
            }
            byte[] keyBytes = XOR_KEY.getBytes(StandardCharsets.UTF_8);
            for (int i = 0; i < bytes.length; i++) {
                bytes[i] ^= keyBytes[i % keyBytes.length];
            }
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }
}
