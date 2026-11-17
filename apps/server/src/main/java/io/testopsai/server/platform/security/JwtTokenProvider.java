package io.testopsai.server.platform.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenProvider {
  private final SecretKey key;
  private final long expirationMillis;

  JwtTokenProvider(JwtProperties props) {
    byte[] keyBytes = props.secret().getBytes(StandardCharsets.UTF_8);
    if (keyBytes.length < 32) {
      throw new IllegalArgumentException("JWT_SECRET must be at least 32 bytes");
    }
    this.key = Keys.hmacShaKeyFor(keyBytes);
    this.expirationMillis = props.expirationMinutes() * 60_000L;
  }

  public String generateToken(String username, List<String> roles) {
    Date now = new Date();
    return Jwts.builder()
        .subject(username)
        .claim("roles", roles)
        .issuedAt(now)
        .expiration(new Date(now.getTime() + expirationMillis))
        .signWith(key)
        .compact();
  }

  public Claims parseToken(String token) {
    try {
      return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    } catch (JwtException e) {
      return null;
    }
  }
}
