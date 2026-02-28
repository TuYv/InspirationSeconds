package com.example.wxnotion.service;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class WxOAuthSessionService {
  private final Map<String, Session> sessions = new ConcurrentHashMap<>();
  private final Duration ttl = Duration.ofMinutes(5);

  public String createState() {
    String state = UUID.randomUUID().toString().replace("-", "");
    sessions.put(state, new Session(Instant.now().plus(ttl)));
    return state;
  }

  public SessionStatus getStatus(String state) {
    Session session = sessions.get(state);
    if (session == null) {
      return SessionStatus.expired();
    }
    if (session.isExpired()) {
      sessions.remove(state);
      return SessionStatus.expired();
    }
    if (session.openId != null) {
      return SessionStatus.success(session.openId);
    }
    return SessionStatus.pending();
  }

  public void markAuthed(String state, String openId) {
    if (state == null || openId == null) return;
    Session session = sessions.get(state);
    if (session == null || session.isExpired()) return;
    session.openId = openId;
  }

  public long ttlSeconds() {
    return ttl.getSeconds();
  }

  public void consume(String state) {
    if (state != null) sessions.remove(state);
  }

  private static class Session {
    private final Instant expiresAt;
    private volatile String openId;

    private Session(Instant expiresAt) {
      this.expiresAt = expiresAt;
    }

    private boolean isExpired() {
      return Instant.now().isAfter(expiresAt);
    }
  }

  public static class SessionStatus {
    public final String status;
    public final String openId;

    private SessionStatus(String status, String openId) {
      this.status = status;
      this.openId = openId;
    }

    public static SessionStatus pending() { return new SessionStatus("PENDING", null); }
    public static SessionStatus success(String openId) { return new SessionStatus("SUCCESS", openId); }
    public static SessionStatus expired() { return new SessionStatus("EXPIRED", null); }
  }
}
