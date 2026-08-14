package com.github.catvod.utils;

import android.util.Base64;

import com.github.catvod.crawler.SpiderDebug;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Random;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class Crypto {

    private static final String AES = "AES";
    private static final String RSA = "RSA";
    private static final String AES_CBC_PKCS5 = "AES/CBC/PKCS5Padding";
    private static final String AES_ECB_PKCS5 = "AES/ECB/PKCS5Padding";
    private static final String RSA_ECB_PKCS1 = "RSA/ECB/PKCS1Padding";

    public static String md5(String src) {
        return md5(src, "UTF-8");
    }

    public static String md5(String src, String charset) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] messageDigest = md.digest(src.getBytes(charset));
            BigInteger no = new BigInteger(1, messageDigest);
            StringBuilder sb = new StringBuilder(no.toString(16));
            while (sb.length() < 32) sb.insert(0, "0");
            return sb.toString().toLowerCase();
        } catch (Exception e) {
            return "";
        }
    }

    public static String md5ToBase64(String src) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(src.getBytes(StandardCharsets.UTF_8));
            return Base64.encodeToString(digest, Base64.NO_WRAP);
        } catch (Exception e) {
            return "";
        }
    }

    public static String sha1(String src) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] digest = md.digest(src.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(digest);
        } catch (Exception e) {
            return "";
        }
    }

    public static String sha256(String src) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(src.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(digest);
        } catch (Exception e) {
            return "";
        }
    }

    public static String sha512(String src) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-512");
            byte[] digest = md.digest(src.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(digest);
        } catch (Exception e) {
            return "";
        }
    }

    public static String hmacMd5(String src, String key) {
        try {
            Mac mac = Mac.getInstance("HmacMD5");
            SecretKeySpec keySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacMD5");
            mac.init(keySpec);
            byte[] digest = mac.doFinal(src.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(digest);
        } catch (Exception e) {
            return "";
        }
    }

    public static String hmacSha1(String src, String key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            SecretKeySpec keySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA1");
            mac.init(keySpec);
            byte[] digest = mac.doFinal(src.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(digest);
        } catch (Exception e) {
            return "";
        }
    }

    public static String hmacSha256(String src, String key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(keySpec);
            byte[] digest = mac.doFinal(src.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(digest);
        } catch (Exception e) {
            return "";
        }
    }

    public static String CBC(String src, String KEY, String IV) {
        try {
            src = src.replace("\\", "");
            Cipher cipher = Cipher.getInstance(AES_CBC_PKCS5);
            SecretKeySpec keySpec = new SecretKeySpec(KEY.getBytes(StandardCharsets.UTF_8), AES);
            AlgorithmParameterSpec paramSpec = new IvParameterSpec(IV.getBytes(StandardCharsets.UTF_8));
            cipher.init(Cipher.DECRYPT_MODE, keySpec, paramSpec);
            byte[] decrypted = cipher.doFinal(Base64.decode(src, Base64.DEFAULT));
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            return "";
        }
    }

    public static String aesDecrypt(String src, String key, String iv) {
        return CBC(src, key, iv);
    }

    public static String aesEncrypt(String data, String key, String iv) throws Exception {
        SecretKeySpec secretKey = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), AES);
        IvParameterSpec ivSpec = new IvParameterSpec(iv.getBytes(StandardCharsets.UTF_8));
        Cipher cipher = Cipher.getInstance(AES_CBC_PKCS5);
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivSpec);
        byte[] encrypted = cipher.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return Base64.encodeToString(encrypted, Base64.NO_WRAP);
    }

    public static String aesEcbEncrypt(String data, String key) throws Exception {
        SecretKeySpec secretKey = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), AES);
        Cipher cipher = Cipher.getInstance(AES_ECB_PKCS5);
        cipher.init(Cipher.ENCRYPT_MODE, secretKey);
        byte[] encrypted = cipher.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return Base64.encodeToString(encrypted, Base64.NO_WRAP);
    }

    public static String aesEcbDecrypt(String src, String key) throws Exception {
        SecretKeySpec secretKey = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), AES);
        Cipher cipher = Cipher.getInstance(AES_ECB_PKCS5);
        cipher.init(Cipher.DECRYPT_MODE, secretKey);
        byte[] decrypted = cipher.doFinal(Base64.decode(src, Base64.DEFAULT));
        return new String(decrypted, StandardCharsets.UTF_8);
    }

    public static String rsaEncrypt(String data, String publicKeyPem) throws Exception {
        String publicKeyPEM = publicKeyPem.replace("-----BEGIN PUBLIC KEY-----", "").replace("-----END PUBLIC KEY-----", "").replaceAll("\\s+", "");
        byte[] decoded = Base64.decode(publicKeyPEM, Base64.DEFAULT);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(decoded);
        KeyFactory keyFactory = KeyFactory.getInstance(RSA);
        PublicKey publicKey = keyFactory.generatePublic(spec);
        Cipher cipher = Cipher.getInstance(RSA_ECB_PKCS1);
        cipher.init(Cipher.ENCRYPT_MODE, publicKey);
        byte[] encrypted = cipher.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return Base64.encodeToString(encrypted, Base64.DEFAULT);
    }

    public static String rsaDecrypt(String encryptedKey, String privateKeyPem) throws Exception {
        String privateKeyPEM = privateKeyPem.replace("-----BEGIN PRIVATE KEY-----", "").replace("-----END PRIVATE KEY-----", "").replaceAll("\\s", "");
        byte[] privateKeyBytes = Base64.decode(privateKeyPEM, Base64.DEFAULT);
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(privateKeyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance(RSA);
        PrivateKey privateKey = keyFactory.generatePrivate(keySpec);
        Cipher cipher = Cipher.getInstance(RSA_ECB_PKCS1);
        cipher.init(Cipher.DECRYPT_MODE, privateKey);
        byte[] decrypted = cipher.doFinal(Base64.decode(encryptedKey, Base64.DEFAULT));
        return new String(decrypted, StandardCharsets.UTF_8);
    }

    public static String randomKey(int size) {
        StringBuilder key = new StringBuilder();
        String keys = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        for (int i = 0; i < size; i++) key.append(keys.charAt((int) Math.floor(Math.random() * keys.length())));
        return key.toString();
    }

    public static String randomKey(int size, String chars) {
        StringBuilder key = new StringBuilder();
        for (int i = 0; i < size; i++) key.append(chars.charAt((int) Math.floor(Math.random() * chars.length())));
        return key.toString();
    }

    public static String randomHex(int size) {
        StringBuilder key = new StringBuilder();
        String hex = "0123456789abcdef";
        for (int i = 0; i < size; i++) key.append(hex.charAt((int) Math.floor(Math.random() * hex.length())));
        return key.toString();
    }

    public static String randomNum(int size) {
        StringBuilder key = new StringBuilder();
        String nums = "0123456789";
        for (int i = 0; i < size; i++) key.append(nums.charAt((int) Math.floor(Math.random() * nums.length())));
        return key.toString();
    }

    public static String randomUuid() {
        return java.util.UUID.randomUUID().toString();
    }

    public static String randomUuidNoDash() {
        return java.util.UUID.randomUUID().toString().replace("-", "");
    }

    public static String base64Encode(String src) {
        return Base64.encodeToString(src.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
    }

    public static String base64Decode(String src) {
        return new String(Base64.decode(src, Base64.DEFAULT), StandardCharsets.UTF_8);
    }

    public static String base64UrlEncode(String src) {
        return Base64.encodeToString(src.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP | Base64.URL_SAFE);
    }

    public static String base64UrlDecode(String src) {
        return new String(Base64.decode(src, Base64.NO_WRAP | Base64.URL_SAFE), StandardCharsets.UTF_8);
    }

    public static String xor(String data, String key) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < data.length(); i++) {
            sb.append((char) (data.charAt(i) ^ key.charAt(i % key.length())));
        }
        return sb.toString();
    }

    public static String rc4(String data, String key) {
        int[] S = new int[256];
        for (int i = 0; i < 256; i++) {
            S[i] = i;
        }
        int j = 0;
        for (int i = 0; i < 256; i++) {
            j = (j + S[i] + key.charAt(i % key.length())) % 256;
            int temp = S[i];
            S[i] = S[j];
            S[j] = temp;
        }
        StringBuilder sb = new StringBuilder();
        int i = 0;
        j = 0;
        for (int k = 0; k < data.length(); k++) {
            i = (i + 1) % 256;
            j = (j + S[i]) % 256;
            int temp = S[i];
            S[i] = S[j];
            S[j] = temp;
            sb.append((char) (data.charAt(k) ^ S[(S[i] + S[j]) % 256]));
        }
        return sb.toString();
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    public static byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4) + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }

    /**
     * 解密XOR加密的十六进制字符串。
     * <p>等价于 HaB.d() / paA.m420d()，用于解密混淆的硬编码字符串。</p>
     *
     * @param encrypted 加密的十六进制字符串
     * @param key       XOR密钥
     * @return 解密后的明文字符串
     */
    public static String xorDecodeHex(String encrypted, String key) {
        if (encrypted == null || encrypted.isEmpty()) {
            return "";
        }
        byte[] bytes = hexToBytes(encrypted);
        int keyLen = key.length();
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) (bytes[i] ^ key.charAt(i % keyLen));
        }
        return new String(bytes);
    }

    /**
     * 通用哈希算法（支持任意算法名，如 MD5、SHA-1、SHA-256 等）
     */
    public static String hash(String algorithm, String src) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance(algorithm);
            byte[] digest = md.digest(src.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                String hex = Integer.toHexString(b & 0xFF);
                if (hex.length() == 1) sb.append('0');
                sb.append(hex);
            }
            return sb.toString();
        } catch (Exception e) {
            return src;
        }
    }

    // ==================== AES GCM（FengYe 风格） ====================

    private static final char[] FENGYE_BASE64_UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".toCharArray();
    private static final char[] FENGYE_BASE64_LOWER = "ZYXWVUTSRQPONMLKJIHGFEDCBAzyxwvutsrqponmlkjihgfedcba9876543210+/".toCharArray();
    private static final char[] FENGYE_SORTED_CHARSET;

    static {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
        char[] arr = chars.toCharArray();
        Arrays.sort(arr);
        FENGYE_SORTED_CHARSET = arr;
    }

    /**
     * FengYe 风格 AES 加密（密钥对方式）
     */
    public static String fengyeEncrypt(String plaintext, String keyPair) {
        return fengyeCrypt(plaintext, keyPair, true);
    }

    /**
     * FengYe 风格 AES 解密（密钥对方式）
     */
    public static String fengyeDecrypt(String ciphertext, String keyPair) {
        return fengyeCrypt(ciphertext, keyPair, false);
    }

    /**
     * 生成 FengYe 密钥对
     */
    public static String fengyeGenerateKeyPair() {
        String keyMaterial = "lywkxC";
        String md5 = md5(keyMaterial);
        String encoded = Base64.encodeToString(md5.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
        String substring = encoded.substring(5, encoded.length() - 7);
        if (substring.length() < 48) {
            substring = Base64.encodeToString(substring.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
        }
        return substring.substring(3, 19) + ":" + substring.substring(substring.length() - 27, substring.length() - 11);
    }

    private static String fengyeCrypt(String input, String keyPair, boolean encrypt) {
        try {
            String[] parts = keyPair.split(":");
            if (parts.length != 2) return "";
            return encrypt ? doFengyeEncrypt(input, parts[0], parts[1]) : doFengyeDecrypt(input, parts[0], parts[1]);
        } catch (Exception e) {
            SpiderDebug.log(e);
            return "";
        }
    }

    /**
     * 密文末尾结构：
     *   [...加密数据|rotate(1字节)|timeMod(1字节)]
     *   rotate: 字节循环偏移量  1-5
     *   timeMod: System.currentTimeMillis() % 15  0-14
     */
    private static String doFengyeEncrypt(String plaintext, String keyPart1, String keyPart2) {
        int shift = new Random().nextInt(36) + 1;
        String shifted = shiftCharset(plaintext, shift, true);
        byte[] bytes = shifted.getBytes(StandardCharsets.UTF_8);

        byte[] padding = new byte[bytes.length];
        byte[] combined = new byte[bytes.length * 2];
        long timeMod = System.currentTimeMillis() % 15;
        for (int i = 0; i < bytes.length; i++) {
            padding[i] = (byte) (timeMod + 1);
            combined[i] = (byte) (bytes[i] + padding[i]);
            combined[bytes.length + i] = padding[i];
        }

        String prefix = String.format("%02X", shift);
        String base64Str = Base64.encodeToString(combined, Base64.NO_WRAP);
        String transformed = shiftBase64Chars(base64Str, true);
        byte[] data = (prefix + transformed).getBytes(StandardCharsets.UTF_8);

        int rotate = new Random().nextInt(5) + 1;
        byte[] rotated = new byte[data.length];
        for (int i = 0; i < data.length; i++) {
            rotated[(i + rotate) % data.length] = data[i];
        }
        byte[] withRotate = Arrays.copyOf(rotated, rotated.length + 1);
        withRotate[rotated.length] = (byte) rotate;

        // AES offset 用 rotate（解密侧从密文末尾读取同一值），而非 charset shift
        byte[] encrypted = aesFengyeEncrypt(withRotate, keyPart1.getBytes(StandardCharsets.UTF_8),
                keyPart2.getBytes(StandardCharsets.UTF_8), rotate);
        if (encrypted == null) return "";

        byte[] result = Arrays.copyOf(encrypted, encrypted.length + 2);
        result[encrypted.length] = (byte) rotate;
        result[encrypted.length + 1] = (byte) (timeMod + 1);
        return Base64.encodeToString(result, Base64.NO_WRAP);
    }

    private static String doFengyeDecrypt(String ciphertext, String keyPart1, String keyPart2) {
        byte[] decoded = Base64.decode(ciphertext, Base64.NO_WRAP);
        if (decoded == null || decoded.length < 2) return "";

        // 密文末尾：rotate(1字节) | timeMod+1(1字节)
        // rotate 是 AES key offset，timeMod 不参与 AES
        int rotate = decoded[decoded.length - 2] & 0xFF;
        // int timeModPlus1 = decoded[decoded.length - 1] & 0xFF;

        byte[] core = Arrays.copyOfRange(decoded, 0, decoded.length - 2);
        // AES offset 用 rotate（密文尾部已知），与加密侧传入的 rotate 完全一致
        byte[] decrypted = aesFengyeDecrypt(core, keyPart1.getBytes(StandardCharsets.UTF_8),
                keyPart2.getBytes(StandardCharsets.UTF_8), rotate);
        if (decrypted == null || decrypted.length < 2) return "";

        // 逆循环：decrypted 末尾 1 字节是 rotate，用于还原字节移位
        int len = decrypted.length - 1;
        byte[] unrotated = new byte[len];
        int rot = decrypted[decrypted.length - 1] & 0xFF;
        for (int i = 0; i < len; i++) {
            unrotated[(i - rot + len) % len] = decrypted[i];
        }

        // 前缀 2 字节是 charsetShift（十六进制），加密时写入，解密时从此读取并还原
        int charsetShift;
        try {
            charsetShift = Integer.parseInt(new String(unrotated, 0, 2, StandardCharsets.UTF_8), 16);
        } catch (Exception e) {
            return "";
        }

        String base64Data = new String(unrotated, 2, unrotated.length - 2, StandardCharsets.UTF_8);
        String restored = shiftBase64Chars(base64Data, false);
        byte[] restoredBytes = Base64.decode(restored, Base64.NO_WRAP);
        if (restoredBytes == null || restoredBytes.length < 2) return "";

        byte[] padded = new byte[restoredBytes.length / 2];
        for (int i = 0; i < padded.length; i++) {
            padded[i] = (byte) (restoredBytes[i] - restoredBytes[restoredBytes.length / 2 + i]);
        }
        return shiftCharset(new String(padded, StandardCharsets.UTF_8), charsetShift, false);
    }

    /**
     * AES-ECB 仅用 key，不传 IV（ECB 模式下 IvParameterSpec 会抛 InvalidAlgorithmParameterException）
     * @param shift 用于截断 key1/key2 并做字节偏移的偏移量
     */
    private static byte[] aesFengyeEncrypt(byte[] data, byte[] key1, byte[] key2, int shift) {
        try {
            SecretKeySpec key = new SecretKeySpec(truncateTo16(key1, shift), "AES");
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, key);
            return cipher.doFinal(data);
        } catch (Exception e) {
            SpiderDebug.log(e);
            return null;
        }
    }

    private static byte[] aesFengyeDecrypt(byte[] data, byte[] key1, byte[] key2, int shift) {
        try {
            SecretKeySpec key = new SecretKeySpec(truncateTo16(key1, shift), "AES");
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, key);
            return cipher.doFinal(data);
        } catch (Exception e) {
            SpiderDebug.log(e);
            return null;
        }
    }

    private static byte[] truncateTo16(byte[] bytes, int shift) {
        byte[] result = new byte[16];
        System.arraycopy(bytes, 0, result, 0, Math.min(bytes.length, 16));
        for (int i = 0; i < result.length; i++) {
            result[i] = (byte) (result[i] + shift);
        }
        return result;
    }

    private static String shiftCharset(String str, int shift, boolean encrypt) {
        if (str == null || str.isEmpty()) return "";
        int len = FENGYE_SORTED_CHARSET.length;
        StringBuilder sb = new StringBuilder();
        for (char c : str.toCharArray()) {
            int idx = Arrays.binarySearch(FENGYE_SORTED_CHARSET, c);
            if (idx >= 0) {
                c = FENGYE_SORTED_CHARSET[(encrypt ? (idx + shift) : ((idx - shift) + len)) % len];
            }
            sb.append(c);
        }
        return sb.toString();
    }

    private static String shiftBase64Chars(String str, boolean uppercase) {
        if (str == null || str.isEmpty()) return "";
        char[] from = uppercase ? FENGYE_BASE64_UPPER : FENGYE_BASE64_LOWER;
        char[] to = uppercase ? FENGYE_BASE64_LOWER : FENGYE_BASE64_UPPER;
        StringBuilder sb = new StringBuilder();
        for (char c : str.toCharArray()) {
            if (c == '=') {
                sb.append(c);
                continue;
            }
            int idx = -1;
            for (int i = 0; i < from.length; i++) {
                if (from[i] == c) { idx = i; break; }
            }
            if (idx >= 0) c = to[idx];
            sb.append(c);
        }
        return sb.toString();
    }
}