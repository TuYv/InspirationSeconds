package com.example.wxnotion.service;

import com.example.wxnotion.AbstractSpringTest;
import com.example.wxnotion.util.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.*;

public class JwtUtilTest extends AbstractSpringTest {

    @Autowired
    private JwtUtil jwtUtil;

    @Test
    void issueAndExtract_roundtrip() {
        String openId = "test_open_id_001";
        String token = jwtUtil.issue(openId);

        assertNotNull(token);
        assertEquals(openId, jwtUtil.extractOpenId(token));
    }

    @Test
    void isValid_validToken_returnsTrue() {
        String token = jwtUtil.issue("test_open_id_002");
        assertTrue(jwtUtil.isValid(token));
    }

    @Test
    void isValid_tamperedToken_returnsFalse() {
        String token = jwtUtil.issue("test_open_id_003");
        String tampered = token.substring(0, token.length() - 4) + "XXXX";
        assertFalse(jwtUtil.isValid(tampered));
    }

    @Test
    void isValid_emptyToken_returnsFalse() {
        assertFalse(jwtUtil.isValid(""));
        assertFalse(jwtUtil.isValid("not.a.jwt"));
    }
}
