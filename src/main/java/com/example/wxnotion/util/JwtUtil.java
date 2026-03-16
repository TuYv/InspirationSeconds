package com.example.wxnotion.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.security.Key;
import java.util.Date;

@Component
public class JwtUtil {

  @Value("${jwt.secret}")
  private String secret;

  private Key key;

  private static final long EXPIRE_MS = 30L * 24 * 60 * 60 * 1000; // 30 days

  @PostConstruct
  public void init() {
    byte[] bytes = secret.getBytes();
    // Ensure key is at least 256 bits for HS256
    if (bytes.length < 32) {
      throw new IllegalStateException("JWT_SECRET must be at least 32 characters");
    }
    key = Keys.hmacShaKeyFor(bytes);
  }

  public String issue(String openId) {
    Date now = new Date();
    return Jwts.builder()
        .setSubject(openId)
        .setIssuedAt(now)
        .setExpiration(new Date(now.getTime() + EXPIRE_MS))
        .signWith(key, SignatureAlgorithm.HS256)
        .compact();
  }

  public String extractOpenId(String token) {
    Claims claims = Jwts.parserBuilder()
        .setSigningKey(key)
        .build()
        .parseClaimsJws(token)
        .getBody();
    return claims.getSubject();
  }

  public boolean isValid(String token) {
    try {
      Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token);
      return true;
    } catch (JwtException | IllegalArgumentException e) {
      return false;
    }
  }
}
