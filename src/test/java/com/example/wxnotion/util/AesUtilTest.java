package com.example.wxnotion.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class AesUtilTest {

    private static final String KEY = "0123456789abcdef0123456789abcdef";

    @Test
    void encryptDecrypt_roundtrip() {
        String plain = "secret-notion-token";
        String cipher = AesUtil.encrypt(KEY, plain);
        assertEquals(plain, AesUtil.decrypt(KEY, cipher));
    }

    @Test
    void encrypt_differentCiphertextEachTime() {
        String plain = "same-text";
        String c1 = AesUtil.encrypt(KEY, plain);
        String c2 = AesUtil.encrypt(KEY, plain);
        assertNotEquals(c1, c2, "每次加密应产生不同的密文（随机 IV）");
    }

    @Test
    void ciphertext_containsIvAndCipherSeparatedByColon() {
        String cipher = AesUtil.encrypt(KEY, "data");
        assertTrue(cipher.contains(":"), "密文格式应为 iv:cipher");
        assertEquals(2, cipher.split(":", 2).length);
    }

    @Test
    void decrypt_wrongKey_throws() {
        String cipher = AesUtil.encrypt(KEY, "data");
        String wrongKey = "ffffffffffffffffffffffffffffffff";
        assertThrows(RuntimeException.class, () -> AesUtil.decrypt(wrongKey, cipher));
    }
}
