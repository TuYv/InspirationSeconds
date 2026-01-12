package com.example.wxnotion.util;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES 加解密工具。
 *
 * 算法：AES/CBC/PKCS5Padding
 * - 每次加密随机生成 16 字节 IV
 * - 输出格式：Base64(iv) + ':' + Base64(cipherText)
 * - 密钥以十六进制字符串传入（16 或 32 字节）
 */
public class AesUtil {
  private static final String ALG = "AES/CBC/PKCS5Padding";

  /**
   * 使用给定 hex 密钥加密明文。
   * @param keyHex 十六进制密钥
   * @param plainText 明文
   * @return 形如 iv:cipher 的 Base64 字符串
   */
  public static String encrypt(String keyHex, String plainText) {
    byte[] key = hexToBytes(keyHex);
    byte[] iv = new byte[16];
    new SecureRandom().nextBytes(iv);
    try {
      Cipher cipher = Cipher.getInstance(ALG);
      cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));
      byte[] out = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
      return Base64.getEncoder().encodeToString(iv) + ":" + Base64.getEncoder().encodeToString(out);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * 使用给定 hex 密钥解密密文。
   * @param keyHex 十六进制密钥
   * @param cipherText 形如 iv:cipher 的 Base64 字符串
   */
  public static String decrypt(String keyHex, String cipherText) {
    String[] parts = cipherText.split(":", 2);
    byte[] iv = Base64.getDecoder().decode(parts[0]);
    byte[] data = Base64.getDecoder().decode(parts[1]);
    byte[] key = hexToBytes(keyHex);
    try {
      Cipher cipher = Cipher.getInstance(ALG);
      cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));
      return new String(cipher.doFinal(data), StandardCharsets.UTF_8);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * 将十六进制字符串转换为字节数组。
   */
  private static byte[] hexToBytes(String hex) {
    int len = hex.length();
    byte[] data = new byte[len / 2];
    for (int i = 0; i < len; i += 2) {
      data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
          + Character.digit(hex.charAt(i + 1), 16));
    }
    return data;
  }
}
