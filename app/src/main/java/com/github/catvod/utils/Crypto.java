package com.github.catvod.utils;

import android.util.Base64;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

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
            SecretKeySpec keySpec = new SecretKeySpec(KEY.getBytes(), AES);
            AlgorithmParameterSpec paramSpec = new IvParameterSpec(IV.getBytes());
            cipher.init(Cipher.DECRYPT_MODE, keySpec, paramSpec);
            byte[] decrypted = cipher.doFinal(Base64.decode(src, Base64.DEFAULT));
            return new String(decrypted);
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
        return Base64.encodeToString(encrypted, Base64.NO_PADDING);
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
}