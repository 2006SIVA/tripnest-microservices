package com.tripnest.shared;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;

public final class JwtTokens {
  private static final String ISSUER = "tripnest";
  private JwtTokens() {}

  private static SecretKey key() {
    String secret = System.getenv("JWT_SECRET");
    if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
      throw new IllegalStateException("JWT_SECRET must contain at least 32 bytes");
    }
    return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
  }

  public static String issue(long userId, String email, String role) {
    Instant now = Instant.now();
    return Jwts.builder().issuer(ISSUER).subject(email).claim("userId", userId).claim("role", role)
        .issuedAt(Date.from(now)).expiration(Date.from(now.plusSeconds(900))).signWith(key()).compact();
  }

  public static Claims verify(String token) {
    return Jwts.parser().requireIssuer(ISSUER).verifyWith(key()).build().parseSignedClaims(token).getPayload();
  }
}
