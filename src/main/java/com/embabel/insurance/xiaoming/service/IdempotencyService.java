package com.embabel.insurance.xiaoming.service;

import com.embabel.insurance.xiaoming.contract.XiaomingResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectWriter;
import tools.jackson.databind.SerializationFeature;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.regex.Pattern;

@Service
public class IdempotencyService {

    private static final Pattern SAFE_KEY = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{15,127}");
    private static final Duration ENTRY_TTL = Duration.ofHours(24);

    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private final ObjectWriter canonicalWriter;

    public IdempotencyService(ObjectMapper objectMapper) {
        this.canonicalWriter = objectMapper.writer(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    }

    public boolean isValidKey(String key) {
        return key != null && SAFE_KEY.matcher(key).matches();
    }

    public String fingerprint(Object payload) {
        try {
            byte[] canonical = canonicalWriter.writeValueAsBytes(payload);
            return sha256(canonical);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("Unable to fingerprint request", exception);
        }
    }

    public <T> IdempotencyResult<T> execute(
            String namespace,
            String key,
            String fingerprint,
            Supplier<StoredResponse<T>> supplier
    ) {
        evictExpired();
        String cacheKey = namespace + ':' + key;
        AtomicReference<IdempotencyResult<T>> result = new AtomicReference<>();

        cache.compute(cacheKey, (ignored, existing) -> {
            if (existing == null) {
                StoredResponse<T> response = supplier.get();
                result.set(new IdempotencyResult<>(response, false, false));
                return new CacheEntry(fingerprint, response, Instant.now());
            }
            if (!existing.fingerprint().equals(fingerprint)) {
                result.set(new IdempotencyResult<>(null, false, true));
                return existing;
            }
            @SuppressWarnings("unchecked")
            StoredResponse<T> replay = (StoredResponse<T>) existing.response();
            result.set(new IdempotencyResult<>(replay, true, false));
            return existing;
        });
        return result.get();
    }

    private void evictExpired() {
        Instant cutoff = Instant.now().minus(ENTRY_TTL);
        cache.entrySet().removeIf(entry -> entry.getValue().createdAt().isBefore(cutoff));
    }

    private String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public record StoredResponse<T>(HttpStatus status, XiaomingResponse<T> body) {
    }

    public record IdempotencyResult<T>(StoredResponse<T> response, boolean replayed, boolean conflict) {
    }

    private record CacheEntry(String fingerprint, StoredResponse<?> response, Instant createdAt) {
    }
}
