package com.project.security;

import com.project.utils.Config;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;


public class TokenManager {

    private static TokenManager instance;

    private final Key signingKey;
    private final long expirationTime;

    public TokenManager(String secretKey, long expirationTime) {
        this.signingKey = Keys.hmacShaKeyFor(secretKey.getBytes());
        this.expirationTime = expirationTime;
    }

    public String generateToken(String subject) {
        return Jwts.builder()
                .setSubject(subject)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + expirationTime))
                .signWith(signingKey, SignatureAlgorithm.HS256)
                .compact();
    }

    public Integer validateTokenAndGetUserId(String token) {
        try {
            Key secretKey = new SecretKeySpec(Config.getSecretKey().getBytes(StandardCharsets.UTF_8), "HMACSHA256");

            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(secretKey)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
            return (Integer) claims.get("userId");
        } catch (Exception e) {
            return null;
        }
    }

}
