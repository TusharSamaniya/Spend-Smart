package in.spendsmart.expense.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JwtUtil {

    private static final String TOKEN_TYPE = "token_type";
    private static final String ACCESS = "access";
    private static final String REFRESH = "refresh";

    private final Key signingKey;

    public JwtUtil(@Value("${jwt.secret}") String jwtSecret) {
        this.signingKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(UUID userId, UUID orgId, String email, String role) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(1, ChronoUnit.HOURS);

        return buildToken(now, expiresAt, userId, orgId, email, role, ACCESS);
    }

    public String generateRefreshToken(UUID userId, UUID orgId, String email, String role) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(30, ChronoUnit.DAYS);

        return buildToken(now, expiresAt, userId, orgId, email, role, REFRESH);
    }

    private String buildToken(
            Instant now,
            Instant expiresAt,
            UUID userId,
            UUID orgId,
            String email,
            String role,
            String tokenType
    ) {

        return Jwts.builder()
                .subject(email)
                .claim("userId", userId.toString())
                .claim("orgId", orgId.toString())
                .claim("user_id", userId.toString())
                .claim("org_id", orgId.toString())
                .claim("email", email)
                .claim("role", role)
                .claim(TOKEN_TYPE, tokenType)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey)
                .compact();
    }

    public boolean isRefreshToken(String token) {
        try {
            return REFRESH.equals(extractClaims(token).get(TOKEN_TYPE, String.class));
        } catch (JwtException | IllegalArgumentException exception) {
            return false;
        }
    }

    public boolean validateToken(String token) {
        try {
            Claims claims = extractClaims(token);
            Date expiration = claims.getExpiration();
            return expiration != null && expiration.after(new Date());
        } catch (JwtException | IllegalArgumentException exception) {
            return false;
        }
    }

    public Claims extractClaims(String token) {
        return Jwts.parser()
                .verifyWith((javax.crypto.SecretKey) signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
