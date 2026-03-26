package in.spendsmart.upiservice.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.spendsmart.upiservice.repository.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/v1/upi")
@RequiredArgsConstructor
public class UpiLinkController {

    private static final String USER_ID_HEADER = "X-User-Id";

    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    @PostMapping("/link")
    public ResponseEntity<Void> linkUpiId(
            @Valid @RequestBody LinkUpiRequest request,
            @RequestHeader(name = USER_ID_HEADER, required = false) String userIdHeader,
            @RequestHeader(name = "Authorization", required = false) String authorizationHeader
    ) {
        UUID userId = resolveCurrentUserId(userIdHeader, authorizationHeader);
        int updatedRows = userRepository.appendUpiId(userId, request.upiId().trim());
        if (updatedRows == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/link/{upiId}")
    public ResponseEntity<Void> unlinkUpiId(
            @PathVariable String upiId,
            @RequestHeader(name = USER_ID_HEADER, required = false) String userIdHeader,
            @RequestHeader(name = "Authorization", required = false) String authorizationHeader
    ) {
        UUID userId = resolveCurrentUserId(userIdHeader, authorizationHeader);
        int updatedRows = userRepository.removeUpiId(userId, upiId.trim());
        if (updatedRows == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }
        return ResponseEntity.noContent().build();
    }

    private UUID resolveCurrentUserId(String userIdHeader, String authorizationHeader) {
        if (StringUtils.hasText(userIdHeader)) {
            try {
                return UUID.fromString(userIdHeader.trim());
            } catch (IllegalArgumentException exception) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid user context");
            }
        }

        if (!StringUtils.hasText(authorizationHeader) || !authorizationHeader.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing user context");
        }

        String token = authorizationHeader.substring("Bearer ".length()).trim();
        String[] tokenParts = token.split("\\.");
        if (tokenParts.length < 2) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid JWT token");
        }

        try {
            String payloadJson = new String(Base64.getUrlDecoder().decode(tokenParts[1]), StandardCharsets.UTF_8);
            JsonNode claims = objectMapper.readTree(payloadJson);
            String userId = firstNonBlank(
                    claims.path("user_id").asText(""),
                    claims.path("userId").asText(""),
                    claims.path("sub").asText("")
            );

            if (!StringUtils.hasText(userId)) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User id not found in token");
            }

            return UUID.fromString(userId);
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unable to resolve user from token");
        }
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    public record LinkUpiRequest(@NotBlank String upiId) {
    }
}
