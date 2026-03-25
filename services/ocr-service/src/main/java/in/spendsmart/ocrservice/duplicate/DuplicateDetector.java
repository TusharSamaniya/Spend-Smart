package in.spendsmart.ocrservice.duplicate;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.imageio.ImageIO;
import lombok.RequiredArgsConstructor;
import org.imgscalr.Scalr;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DuplicateDetector {

    private static final int HASH_DISTANCE_THRESHOLD = 8;
    private static final Duration HASH_TTL = Duration.ofDays(30);

    private final StringRedisTemplate stringRedisTemplate;

    public Optional<UUID> isDuplicate(UUID orgId, byte[] imageBytes) {
        long newHash = computeAverageHash(imageBytes);
        String redisKey = String.format("receipt_hashes:{%s}", orgId);
        HashOperations<String, String, String> hashOps = stringRedisTemplate.opsForHash();

        Map<String, String> existingHashes = hashOps.entries(redisKey);
        for (Map.Entry<String, String> entry : existingHashes.entrySet()) {
            long existingHash;
            try {
                existingHash = Long.parseUnsignedLong(entry.getKey());
            } catch (NumberFormatException exception) {
                continue;
            }

            if (hammingDistance(existingHash, newHash) <= HASH_DISTANCE_THRESHOLD) {
                try {
                    return Optional.of(UUID.fromString(entry.getValue()));
                } catch (IllegalArgumentException exception) {
                    continue;
                }
            }
        }

        String hashField = Long.toUnsignedString(newHash);
        UUID syntheticOriginalId = UUID.nameUUIDFromBytes((orgId + ":" + hashField).getBytes(StandardCharsets.UTF_8));
        hashOps.put(redisKey, hashField, syntheticOriginalId.toString());
        stringRedisTemplate.expire(redisKey, HASH_TTL);

        return Optional.empty();
    }

    private long computeAverageHash(byte[] imageBytes) {
        BufferedImage originalImage;
        try {
            originalImage = ImageIO.read(new ByteArrayInputStream(imageBytes));
        } catch (Exception exception) {
            throw new IllegalArgumentException("Unable to decode image bytes", exception);
        }

        if (originalImage == null) {
            throw new IllegalArgumentException("Image bytes could not be decoded");
        }

        BufferedImage resized = Scalr.resize(originalImage, Scalr.Method.QUALITY, Scalr.Mode.FIT_EXACT, 32, 32);

        int[] blockValues = new int[64];
        int index = 0;
        long blockSum = 0;

        for (int blockY = 0; blockY < 8; blockY++) {
            for (int blockX = 0; blockX < 8; blockX++) {
                int luminanceSum = 0;
                for (int y = blockY * 4; y < (blockY + 1) * 4; y++) {
                    for (int x = blockX * 4; x < (blockX + 1) * 4; x++) {
                        int rgb = resized.getRGB(x, y);
                        int red = (rgb >> 16) & 0xFF;
                        int green = (rgb >> 8) & 0xFF;
                        int blue = rgb & 0xFF;
                        int luminance = (red * 299 + green * 587 + blue * 114) / 1000;
                        luminanceSum += luminance;
                    }
                }

                int averageBlockLuminance = luminanceSum / 16;
                blockValues[index++] = averageBlockLuminance;
                blockSum += averageBlockLuminance;
            }
        }

        long average = blockSum / 64;
        long hash = 0L;
        for (int i = 0; i < blockValues.length; i++) {
            if (blockValues[i] >= average) {
                hash |= (1L << i);
            }
        }

        return hash;
    }

    private int hammingDistance(long left, long right) {
        return Long.bitCount(left ^ right);
    }
}
