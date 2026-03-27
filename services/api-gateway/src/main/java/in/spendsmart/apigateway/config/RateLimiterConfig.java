package in.spendsmart.apigateway.config;

import java.net.InetSocketAddress;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

@Configuration
public class RateLimiterConfig {

    @Bean
    public KeyResolver userKeyResolver() {
        return exchange -> exchange.getPrincipal()
                .ofType(JwtAuthenticationToken.class)
                .map(token -> token.getToken().getClaimAsString("user_id"))
                .filter(StringUtils::hasText)
                .switchIfEmpty(Mono.fromSupplier(() -> {
                    InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
                    return remoteAddress != null && remoteAddress.getAddress() != null
                            ? remoteAddress.getAddress().getHostAddress()
                            : "anonymous";
                }));
    }
}
