package in.spendsmart.upiservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Slf4j
public class VpaResolver {

    private static final String VPA_KEY_PREFIX = "vpa:";
    private static final String UNKNOWN_MERCHANT = "Unknown Merchant";
    private static final Duration VPA_CACHE_TTL = Duration.ofDays(7);

    private static final Map<String, String> DOMAIN_PATTERNS = new LinkedHashMap<>();

    static {
        DOMAIN_PATTERNS.put("okicici", "ICICI Bank Merchant");
        DOMAIN_PATTERNS.put("icici", "ICICI Bank Merchant");
        DOMAIN_PATTERNS.put("okaxis", "Axis Bank Merchant");
        DOMAIN_PATTERNS.put("axis", "Axis Bank Merchant");
        DOMAIN_PATTERNS.put("okhdfcbank", "HDFC Bank Merchant");
        DOMAIN_PATTERNS.put("hdfcbank", "HDFC Bank Merchant");
        DOMAIN_PATTERNS.put("hdfc", "HDFC Bank Merchant");
        DOMAIN_PATTERNS.put("paytm", "Paytm Merchant");
        DOMAIN_PATTERNS.put("ybl", "PhonePe Merchant");
        DOMAIN_PATTERNS.put("sbi", "SBI Merchant");
        DOMAIN_PATTERNS.put("ibl", "IBL Network Merchant");
        DOMAIN_PATTERNS.put("upi", "UPI Merchant");
    }

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    @PostConstruct
    public void warmVpaMerchantCache() {
        try {
            JsonNode root = objectMapper.readTree(new ClassPathResource("data/vpa_merchants.json").getInputStream());
            if (root == null || !root.isObject()) {
                log.warn("vpa_merchants.json is not a valid object. Skipping warm cache.");
                return;
            }

            ValueOperations<String, String> valueOperations = stringRedisTemplate.opsForValue();
            int loaded = 0;
            var fields = root.fields();
            while (fields.hasNext()) {
                var field = fields.next();
                String vpa = normalize(field.getKey());
                String merchantName = field.getValue().path("merchant_name").asText("");

                if (!StringUtils.hasText(vpa) || !StringUtils.hasText(merchantName)) {
                    continue;
                }

                valueOperations.set(cacheKey(vpa), merchantName, VPA_CACHE_TTL);
                loaded++;
            }

            log.info("Loaded {} VPA merchant mappings into Redis cache.", loaded);
        } catch (IOException exception) {
            log.error("Failed to load classpath resource data/vpa_merchants.json", exception);
        } catch (Exception exception) {
            log.error("Failed to warm VPA merchant cache in Redis", exception);
        }
    }

    public String resolveMerchant(String vpa) {
        String normalizedVpa = normalize(vpa);
        if (!StringUtils.hasText(normalizedVpa)) {
            return UNKNOWN_MERCHANT;
        }

        ValueOperations<String, String> valueOperations = stringRedisTemplate.opsForValue();
        String cachedMerchant = valueOperations.get(cacheKey(normalizedVpa));
        if (StringUtils.hasText(cachedMerchant)) {
            return cachedMerchant;
        }

        String domain = extractDomain(normalizedVpa);
        String patternMatch = resolveByDomainPattern(domain);
        if (StringUtils.hasText(patternMatch)) {
            valueOperations.set(cacheKey(normalizedVpa), patternMatch, VPA_CACHE_TTL);
            return patternMatch;
        }

        String fallback = StringUtils.hasText(domain)
                ? UNKNOWN_MERCHANT + " (" + domain + ")"
                : UNKNOWN_MERCHANT;
        valueOperations.set(cacheKey(normalizedVpa), fallback, VPA_CACHE_TTL);
        return fallback;
    }

    private String resolveByDomainPattern(String domain) {
        if (!StringUtils.hasText(domain)) {
            return null;
        }

        for (Map.Entry<String, String> entry : DOMAIN_PATTERNS.entrySet()) {
            if (domain.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    private String extractDomain(String vpa) {
        int delimiterIndex = vpa.indexOf('@');
        if (delimiterIndex < 0 || delimiterIndex == vpa.length() - 1) {
            return "";
        }
        return vpa.substring(delimiterIndex + 1);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String cacheKey(String vpa) {
        return VPA_KEY_PREFIX + vpa;
    }
}
