package com.ps.apigateway.security;


import io.jsonwebtoken.Claims;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.List;

@Component
public class JwtAuthenticationFilter implements WebFilter {

    private final JwtUtil jwtUtil;

    public JwtAuthenticationFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        //1. First we will allow our Public endpoints throught our filter so we got to check that
        if (isPublicPath(path))
            return chain.filter(exchange);

        //2. Now we get Authorization header
        String authHeader  = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if(authHeader == null||!authHeader.startsWith("Bearer "))
        {
            exchange.getResponse().setStatusCode(HttpStatusCode.valueOf(403));
            return exchange.getResponse().setComplete();
        }

        String token = authHeader.substring(7);

        // 3 . Now we going to validate our Token, lessgo btw token se bar bar toeless keeri yaad ajata hai hahaaha
        if(!jwtUtil.isTokenValid(token))
        {
            exchange.getResponse().setStatusCode(HttpStatusCode.valueOf(403));
            return exchange.getResponse().setComplete();
        }

        //4. Extracting identity and role
        Claims claims = jwtUtil.extractAllClaims(token);
        String email = claims.getSubject();
        String role = claims.get("role",String.class);

        //5. Building Authorities : ROLE_USER / ROLE_ADMIN
        SimpleGrantedAuthority authority = new  SimpleGrantedAuthority("ROLE_"+role);

        UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(
                email,
                null,
                List.of(authority)
        );

        //6. Putting our token in reactive Security Context
        return chain.filter(exchange).contextWrite(ReactiveSecurityContextHolder.withAuthentication(authenticationToken));
    }


    public boolean isPublicPath(String path)
    {
        return path.startsWith("/auth/")
                || path.startsWith("/api-docs/")
                || path.startsWith("/swagger-ui/")
                || path.startsWith("/actuator/health");
    }
}
