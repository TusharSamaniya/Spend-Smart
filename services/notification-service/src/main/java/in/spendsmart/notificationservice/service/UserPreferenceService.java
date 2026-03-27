package in.spendsmart.notificationservice.service;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class UserPreferenceService {

    private static final Duration CACHE_TTL = Duration.ofMinutes(5);

    private final RestClient expenseServiceClient;
    private final Map<UUID, CacheEntry> contactCache = new ConcurrentHashMap<>();

    public UserPreferenceService(RestClient.Builder restClientBuilder) {
        this.expenseServiceClient = restClientBuilder.baseUrl("http://expense-service:8081").build();
    }

    public Set<Channel> getPreferredChannels(UUID userId, String eventType) {
        return EnumSet.allOf(Channel.class);
    }

    public ContactInfo getContactInfo(UUID userId) {
        CacheEntry cached = contactCache.get(userId);
        Instant now = Instant.now();
        if (cached != null && cached.expiresAt().isAfter(now)) {
            return cached.contactInfo();
        }

        UserResponse user = expenseServiceClient.get()
                .uri("/v1/users/{id}", userId)
                .retrieve()
                .body(UserResponse.class);

        if (user == null) {
            throw new IllegalStateException("User not found for id: " + userId);
        }

        ContactInfo contactInfo = new ContactInfo(user.email(), user.phone(), user.deviceToken());
        contactCache.put(userId, new CacheEntry(contactInfo, now.plus(CACHE_TTL)));
        return contactInfo;
    }

    public enum Channel {
        EMAIL,
        WHATSAPP,
        PUSH
    }

    public record ContactInfo(String email, String phone, String deviceToken) {
    }

    private record UserResponse(String email, String phone, String deviceToken) {
    }

    private record CacheEntry(ContactInfo contactInfo, Instant expiresAt) {
    }
}
