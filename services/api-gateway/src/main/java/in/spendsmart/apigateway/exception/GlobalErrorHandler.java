package in.spendsmart.apigateway.exception;

import java.nio.charset.StandardCharsets;

import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

@Component
@Order(-1)
public class GlobalErrorHandler implements ErrorWebExceptionHandler {

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        ServerHttpResponse response = exchange.getResponse();
        if (response.isCommitted()) {
            return Mono.error(ex);
        }

        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        String code = "INTERNAL_SERVER_ERROR";
        String message = "Something went wrong. Please try again later.";

        if (ex instanceof ResponseStatusException rse) {
            status = HttpStatus.valueOf(rse.getStatusCode().value());
            code = status.name();
            String reason = rse.getReason();
            message = reason != null && !reason.isBlank() ? reason : status.getReasonPhrase();
        } else if (ex instanceof JwtException) {
            status = HttpStatus.UNAUTHORIZED;
            code = "UNAUTHORIZED";
            message = "Invalid or expired token";
        } else if (ex instanceof RuntimeException) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
            code = "INTERNAL_SERVER_ERROR";
            message = "Something went wrong. Please try again later.";
        }

        String body = "{\"error\":{\"code\":\"" + code + "\",\"message\":\"" + escape(message) + "\"}}";
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        return response.writeWith(Mono.just(response.bufferFactory().wrap(bytes)));
    }

    private String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
