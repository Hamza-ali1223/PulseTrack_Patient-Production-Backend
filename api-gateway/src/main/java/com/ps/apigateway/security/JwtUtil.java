package com.ps.apigateway.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

@Component
public class JwtUtil {
    private static final Logger log = LoggerFactory.getLogger(JwtUtil.class);
    private final SecretKey secretKey;

    public JwtUtil(@Value("${jwt.secret}") String secretKey) {
        this.secretKey = Keys.hmacShaKeyFor(secretKey.getBytes());
    }

    public Claims extractAllClaims(String token)
    {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token).getPayload();
    }

    public String extractSubject (String token)
    {
        return extractAllClaims(token).getSubject();
    }

    public String extractRole(String token)
    {
        return extractAllClaims(token).get("role", String.class);
    }

    public boolean isTokenExpired(String token)
    {
        Date expiration = extractAllClaims(token).getExpiration();
        return expiration.before(new Date());
    }

    public boolean isTokenValid(String token)
    {
        try
        {
            Claims claims = extractAllClaims(token);
            if(!isTokenExpired(token))
            {
                if(!claims.getSubject().isEmpty())
                {
                    log.info("Token is Valid with this Subject : {}", claims.getSubject());
                    return true;
                }
                else
                {
                    log.info("Token is empty with this Subject : {}", claims.getSubject());
                            return false;
                }
            }
            else
                log.info("Token is Expired with this Subject : {}", claims.getSubject());
            return false;

        }
        catch (JwtException e)
        {
            return  false;
        }
    }
}
