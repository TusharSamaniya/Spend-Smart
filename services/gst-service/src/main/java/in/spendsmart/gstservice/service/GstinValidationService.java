package in.spendsmart.gstservice.service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class GstinValidationService {

    private static final Pattern GSTIN_PATTERN = Pattern.compile("^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][0-9]Z[0-9A-Z]$");
    private static final Duration CACHE_TTL = Duration.ofHours(24);

    private final RestClient restClient;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final String gstPortalApiKey;

    public GstinValidationService(
            RestClient.Builder restClientBuilder,
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            @Value("${gst.portal.api-url}") String gstPortalApiUrl,
            @Value("${gst.portal.api-key:}") String gstPortalApiKey
    ) {
        this.restClient = restClientBuilder.baseUrl(gstPortalApiUrl).build();
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.gstPortalApiKey = gstPortalApiKey;
    }

    public GstinResult validateGstin(String gstin) {
        String normalizedGstin = gstin == null ? "" : gstin.trim().toUpperCase();
        if (!GSTIN_PATTERN.matcher(normalizedGstin).matches()) {
            return new GstinResult(false, null, null, null, "INVALID_FORMAT");
        }

        String cacheKey = "gstin:" + normalizedGstin;
        String cachedValue = redisTemplate.opsForValue().get(cacheKey);
        if (cachedValue != null && !cachedValue.isBlank()) {
            GstinResult cachedResult = fromCache(cachedValue);
            if (cachedResult != null) {
                return cachedResult;
            }
        }

        try {
            JsonNode response = restClient.get()
                    .uri(uriBuilder -> uriBuilder.queryParam("gstin", normalizedGstin).build())
                    .accept(MediaType.APPLICATION_JSON)
                    .header("x-api-key", gstPortalApiKey)
                    .retrieve()
                    .body(JsonNode.class);

            GstinResult result = toResult(response);
            cacheResult(cacheKey, result);
            return result;
        } catch (Exception ex) {
            return new GstinResult(false, null, null, null, "validation temporarily unavailable");
        }
    }

    private GstinResult toResult(JsonNode root) {
        JsonNode dataNode = firstPresentNode(root, "data", "result", "gstinData");
        JsonNode source = dataNode != null && !dataNode.isMissingNode() ? dataNode : root;

        boolean valid = booleanValue(source, "valid", true);
        String legalName = textValue(source, "legalName", "lgnm", "legal_name");
        String tradeName = textValue(source, "tradeName", "tradeNam", "trade_name");
        LocalDate registrationDate = dateValue(source, "registrationDate", "rgdt", "registration_date");
        String status = textValue(source, "status", "sts", "registrationStatus");

        return new GstinResult(valid, legalName, tradeName, registrationDate, status);
    }

    private void cacheResult(String cacheKey, GstinResult result) {
        try {
            String cachePayload = objectMapper.writeValueAsString(result);
            redisTemplate.opsForValue().set(cacheKey, cachePayload, CACHE_TTL);
        } catch (JsonProcessingException ignored) {
        }
    }

    private GstinResult fromCache(String cachedValue) {
        try {
            return objectMapper.readValue(cachedValue, GstinResult.class);
        } catch (JsonProcessingException ex) {
            return null;
        }
    }

    private JsonNode firstPresentNode(JsonNode node, String... keys) {
        if (node == null) {
            return null;
        }
        for (String key : keys) {
            JsonNode child = node.get(key);
            if (child != null && !child.isNull()) {
                return child;
            }
        }
        return null;
    }

    private String textValue(JsonNode node, String... keys) {
        JsonNode valueNode = firstPresentNode(node, keys);
        return valueNode == null ? null : valueNode.asText(null);
    }

    private boolean booleanValue(JsonNode node, String key, boolean defaultValue) {
        JsonNode valueNode = node == null ? null : node.get(key);
        return valueNode == null || valueNode.isNull() ? defaultValue : valueNode.asBoolean(defaultValue);
    }

    private LocalDate dateValue(JsonNode node, String... keys) {
        String rawDate = textValue(node, keys);
        if (rawDate == null || rawDate.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(rawDate);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    public record GstinResult(
            boolean valid,
            String legalName,
            String tradeName,
            LocalDate registrationDate,
            String status
    ) {
    }
}