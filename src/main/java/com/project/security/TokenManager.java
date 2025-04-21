package com.project.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;

public class TokenManager {

    private final Key signingKey;
    private final long expirationTime;

    public TokenManager(String secretKey, long expirationTime) {
        this.signingKey = Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
        this.expirationTime = expirationTime;
    }

    public String generateToken(String userId) {
        long now = System.currentTimeMillis();
        Date expiryDate = new Date(now + expirationTime);

        return Jwts.builder()
                .claim("userId", Integer.parseInt(userId))
                .setIssuedAt(new Date(now))
                .setExpiration(expiryDate)
                .signWith(signingKey, SignatureAlgorithm.HS256)
                .compact();
    }

    public Integer validateTokenAndGetUserId(String token) {
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(signingKey)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();

            return claims.get("userId", Integer.class);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
