package in.spendsmart.apigateway.filter;

import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class RequestLoggingFilter implements GlobalFilter, Ordered {
    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String requestId = UUID.randomUUID().toString();
        String method = String.valueOf(exchange.getRequest().getMethod());
        String path = exchange.getRequest().getURI().getPath();
        exchange.getAttributes().put("startTime", System.currentTimeMillis());
        exchange.getAttributes().put("requestId", requestId);
        exchange.getResponse().getHeaders().set("X-Request-Id", requestId);

        log.info("Inbound request timestamp={} method={} path={} requestId={}", Instant.now(), method, path, requestId);
        return chain.filter(exchange).doFinally(signal -> {
            HttpStatusCode status = exchange.getResponse().getStatusCode();
            long startTime = (long) exchange.getAttributes().getOrDefault("startTime", System.currentTimeMillis());
            long durationMs = System.currentTimeMillis() - startTime;
            log.info("Outbound response requestId={} status={} durationMs={}", requestId, status != null ? status.value() : 500, durationMs);
        });
    }

    @Override
    public int getOrder() { return -2; }
}
