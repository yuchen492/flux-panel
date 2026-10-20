package com.admin.common.utils;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * MD5工具类
 */
public class Md5Util {

    private static final String MD5_ALGORITHM = "MD5";
    private static final String DEFAULT_SALT = "admin_salt_2024";
    
    /**
     * 基础MD5加密
     *
     * @param input 待加密字符串
     * @return MD5加密后的字符串（32位小写）
     */
    public static String md5(String input) {
        if (input == null || input.isEmpty()) {
            return null;
        }
        
        try {
            MessageDigest md = MessageDigest.getInstance(MD5_ALGORITHM);
            byte[] digest = md.digest(input.getBytes());
            return bytesToHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5算法不可用", e);
        }
    }
    
    /**
     * MD5加密（使用默认盐值）
     *
     * @param input 待加密字符串
     * @return MD5加密后的字符串
     */
    public static String md5WithSalt(String input) {
        return md5WithSalt(input, DEFAULT_SALT);
    }
    
    /**
     * MD5加密（使用自定义盐值）
     *
     * @param input 待加密字符串
     * @param salt  盐值
     * @return MD5加密后的字符串
     */
    public static String md5WithSalt(String input, String salt) {
        if (input == null || input.isEmpty()) {
            return null;
        }
        
        if (salt == null) {
            salt = DEFAULT_SALT;
        }
        
        return md5(input + salt);
    }
    
    /**
     * 生成随机盐值
     *
     * @param length 盐值长度
     * @return 随机盐值
     */
    public static String generateSalt(int length) {
        SecureRandom random = new SecureRandom();
        byte[] salt = new byte[length];
        random.nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt);
    }
    
    /**
     * 生成默认长度（16字节）的随机盐值
     *
     * @return 随机盐值
     */
    public static String generateSalt() {
        return generateSalt(16);
    }
    
    /**
     * 验证密码
     *
     * @param password    原始密码
     * @param hashedPassword 已加密的密码
     * @return 是否匹配
     */
    public static boolean verify(String password, String hashedPassword) {
        if (password == null || hashedPassword == null) {
            return false;
        }
        
        String encrypted = md5WithSalt(password);
        return encrypted.equals(hashedPassword);
    }
    
    /**
     * 验证密码（使用自定义盐值）
     *
     * @param password    原始密码
     * @param salt        盐值
     * @param hashedPassword 已加密的密码
     * @return 是否匹配
     */
    public static boolean verify(String password, String salt, String hashedPassword) {
        if (password == null || hashedPassword == null) {
            return false;
        }
        
        String encrypted = md5WithSalt(password, salt);
        return encrypted.equals(hashedPassword);
    }
    
    /**
     * 多次MD5加密
     *
     * @param input 待加密字符串
     * @param times 加密次数
     * @return 加密后的字符串
     */
    public static String md5Multiple(String input, int times) {
        if (input == null || input.isEmpty() || times <= 0) {
            return input;
        }
        
        String result = input;
        for (int i = 0; i < times; i++) {
            result = md5(result);
        }
        return result;
    }
    
    /**
     * 字节数组转十六进制字符串
     *
     * @param bytes 字节数组
     * @return 十六进制字符串
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }
    
    /**
     * 获取文件的MD5值
     *
     * @param bytes 文件字节数组
     * @return MD5值
     */
    public static String getFileMd5(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        
        try {
            MessageDigest md = MessageDigest.getInstance(MD5_ALGORITHM);
            byte[] digest = md.digest(bytes);
            return bytesToHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5算法不可用", e);
        }
    }
}
