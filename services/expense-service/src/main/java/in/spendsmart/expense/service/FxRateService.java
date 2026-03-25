package in.spendsmart.expense.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Service
@RequiredArgsConstructor
public class FxRateService {

    private static final Duration CACHE_TTL = Duration.ofHours(4);
    private static final MathContext DIVISION_CONTEXT = new MathContext(12, RoundingMode.HALF_UP);

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${openexchangerates.app-id:${OPEN_EXCHANGE_RATES_APP_ID:}}")
    private String appId;

    @Value("${openexchangerates.base-url:https://openexchangerates.org/api}")
    private String baseUrl;

    public BigDecimal getRate(String fromCurrency, String toCurrency) {
        String from = normalizeCurrency(fromCurrency);
        String to = normalizeCurrency(toCurrency);

        if (from.equals(to)) {
            return BigDecimal.ONE;
        }

        String cacheKey = buildCacheKey(from, to);
        String cachedValue = stringRedisTemplate.opsForValue().get(cacheKey);
        if (cachedValue != null && !cachedValue.isBlank()) {
            return new BigDecimal(cachedValue);
        }

        BigDecimal liveRate = fetchRateFromApi(from, to);
        stringRedisTemplate.opsForValue().set(cacheKey, liveRate.toPlainString(), CACHE_TTL);
        return liveRate;
    }

    private BigDecimal fetchRateFromApi(String from, String to) {
        if (appId == null || appId.isBlank()) {
            throw new IllegalStateException("Open Exchange Rates app id is not configured");
        }

        String url = String.format(
                "%s/latest.json?app_id=%s&symbols=%s,%s",
                baseUrl,
                appId,
                from,
                to
        );

        try {
            String response = restTemplate.getForObject(url, String.class);
            JsonNode root = objectMapper.readTree(response);
            JsonNode rates = root.path("rates");

            BigDecimal fromRate = from.equals("USD")
                    ? BigDecimal.ONE
                    : readRate(rates, from);
            BigDecimal toRate = to.equals("USD")
                    ? BigDecimal.ONE
                    : readRate(rates, to);

            return toRate.divide(fromRate, DIVISION_CONTEXT);
        } catch (RestClientException | IOException exception) {
            throw new IllegalStateException("Failed to fetch exchange rate from Open Exchange Rates", exception);
        }
    }

    private BigDecimal readRate(JsonNode rates, String currency) {
        JsonNode node = rates.path(currency);
        if (node.isMissingNode() || node.isNull()) {
            throw new IllegalStateException("Exchange rate not available for currency: " + currency);
        }
        return node.decimalValue();
    }

    private String buildCacheKey(String from, String to) {
        return "fx:" + from + ":" + to;
    }

    private String normalizeCurrency(String currency) {
        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("Currency must not be blank");
        }
        return currency.trim().toUpperCase(Locale.ROOT);
    }
}
