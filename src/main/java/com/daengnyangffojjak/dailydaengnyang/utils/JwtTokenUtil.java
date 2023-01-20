package com.daengnyangffojjak.dailydaengnyang.utils;

import com.daengnyangffojjak.dailydaengnyang.domain.dto.user.UserRole;
import com.daengnyangffojjak.dailydaengnyang.exception.ErrorCode;
import com.daengnyangffojjak.dailydaengnyang.exception.SecurityCustomException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;

@RequiredArgsConstructor
@Slf4j
@Component
public class JwtTokenUtil {
    private final UserDetailsService userDetailsService;

    @Value("${jwt.token.secret}")
    private String secretKey;

    //secretKey는 256bit보다 커야 한다. 영어 한단어당 8bit 이므로 32글자 이상이어야 한다는 뜻이다.
    private Key makeKey() {
        return Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
    }

    public String createToken(String userName, UserRole userRole, long expireTimeMs) {
        Claims claims = Jwts.claims();
        claims.put("userName", userName);
        claims.put("role", userRole.name());

        return Jwts.builder()
                .setClaims(claims)
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + expireTimeMs)) //토큰 만료 시간
                .signWith(makeKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    public String getUserName(String token) {
        return extractClaims(token).get("userName", String.class);
    }

    public boolean isExpired(String token) {
        Date expirationDate = extractClaims(token).getExpiration();
        return expirationDate.before(new Date());
    }

    private Claims extractClaims(String token) {
        try {
            return Jwts.parserBuilder().setSigningKey(makeKey()).build().parseClaimsJws(token).getBody();
        } catch (IllegalArgumentException e) {
            throw new SecurityCustomException(ErrorCode.INVALID_TOKEN);
        } catch (
                ExpiredJwtException e) {
            log.info("만료된 토큰입니다.");
            throw new SecurityCustomException(ErrorCode.INVALID_TOKEN, "토큰 기한 만료");
        } catch (
                SignatureException e) {
            log.info("서명이 일치하지 않습니다.");
            throw new SecurityCustomException(ErrorCode.INVALID_TOKEN, "서명 불일치");
        }
    }

    public UserDetails getUserDetails(String token) {
        String userName = getUserName(token);
        //UserName Token에서 꺼내기
        log.info("userName : {}", userName);
        return userDetailsService.loadUserByUsername(userName);
    }
}