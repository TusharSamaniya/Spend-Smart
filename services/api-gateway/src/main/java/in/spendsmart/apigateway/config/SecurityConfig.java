package in.spendsmart.apigateway.config;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

import javax.crypto.spec.SecretKeySpec;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    @Order(0)
    public SecurityWebFilterChain publicSecurityWebFilterChain(ServerHttpSecurity http) {
        return http
                .securityMatcher(ServerWebExchangeMatchers.pathMatchers(
                        "/v1/auth/register",
                        "/v1/auth/login",
                        "/v1/auth/refresh",
                        "/v1/auth/**",
                        "/actuator/health",
                        "/actuator/info",
                        "/v1/webhooks/whatsapp",
                        "/v3/api-docs/**"))
                .cors(corsSpec -> {})
                .csrf(csrfSpec -> csrfSpec.disable())
                .authorizeExchange(exchanges -> exchanges
                        .anyExchange()
                        .permitAll())
                .build();
    }

    @Bean
    @Order(1)
    public SecurityWebFilterChain apiSecurityWebFilterChain(ServerHttpSecurity http) {
        return http
                .cors(corsSpec -> {})
                .csrf(csrfSpec -> csrfSpec.disable())
                .authorizeExchange(exchanges -> exchanges
                        .anyExchange()
                        .authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtDecoder(reactiveJwtDecoder())))
                .build();
    }

    @Bean
    public ReactiveJwtDecoder reactiveJwtDecoder() {
        String jwtSecret = Objects.requireNonNull(
                System.getenv("JWT_SECRET"),
                "JWT_SECRET environment variable is required for JWT validation");

        SecretKeySpec secretKey = new SecretKeySpec(
                jwtSecret.getBytes(StandardCharsets.UTF_8),
                "HmacSHA256");

        return NimbusReactiveJwtDecoder.withSecretKey(secretKey).build();
    }
}
