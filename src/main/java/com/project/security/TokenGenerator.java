package com.project.security;

import com.project.utils.Config;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;

public class TokenGenerator {

    public static String generateToken(int userId, String secretKey, long expirationMillis) {
        long now = System.currentTimeMillis();
        Date expiryDate = new Date(now + expirationMillis);

        byte[] keyBytes = secretKey.getBytes(StandardCharsets.UTF_8);
        Key key = Keys.hmacShaKeyFor(keyBytes);

        return Jwts.builder()
                .claim("userId", userId)
                .setIssuedAt(new Date(now))
                .setExpiration(expiryDate)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public static void main(String[] args) {
        int userId = 42;
        String secretKey = Config.getSecretKey();
        long expirationTime = 1000 * 60 * 60;

        String token = generateToken(userId, secretKey, expirationTime);
        System.out.println("Wygenerowany token:\nBearer " + token);
    }
}



