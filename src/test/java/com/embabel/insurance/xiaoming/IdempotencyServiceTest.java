package com.embabel.insurance.xiaoming;

import com.embabel.insurance.xiaoming.contract.XiaomingResponse;
import com.embabel.insurance.xiaoming.service.IdempotencyService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class IdempotencyServiceTest {

    @Test
    void shouldExecuteConcurrentDuplicateOnlyOnce() throws Exception {
        IdempotencyService service = new IdempotencyService(new ObjectMapper());
        AtomicInteger executions = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(12);
        List<Future<IdempotencyService.IdempotencyResult<String>>> futures = new ArrayList<>();

        try {
            for (int index = 0; index < 12; index++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return service.execute("chat", "idem-000000000001", "same-fingerprint", () -> {
                        executions.incrementAndGet();
                        return new IdempotencyService.StoredResponse<>(
                                HttpStatus.OK,
                                XiaomingResponse.success("ok", "value", "v1", "trace", "request")
                        );
                    });
                }));
            }
            start.countDown();

            List<IdempotencyService.IdempotencyResult<String>> results = new ArrayList<>();
            for (Future<IdempotencyService.IdempotencyResult<String>> future : futures) {
                results.add(future.get());
            }

            assertThat(executions).hasValue(1);
            assertThat(results).filteredOn(result -> !result.replayed()).hasSize(1);
            assertThat(results).filteredOn(IdempotencyService.IdempotencyResult::replayed).hasSize(11);
            assertThat(results).allSatisfy(result -> {
                assertThat(result.conflict()).isFalse();
                assertThat(result.response().body().data()).isEqualTo("value");
            });
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void shouldRejectSameKeyWithDifferentFingerprint() {
        IdempotencyService service = new IdempotencyService(new ObjectMapper());
        service.execute("chat", "idem-000000000002", "first", () -> stored("first"));

        IdempotencyService.IdempotencyResult<String> result = service.execute(
                "chat",
                "idem-000000000002",
                "different",
                () -> stored("should-not-run")
        );

        assertThat(result.conflict()).isTrue();
        assertThat(result.response()).isNull();
    }

    @Test
    void shouldCreateStableCanonicalFingerprint() {
        IdempotencyService service = new IdempotencyService(new ObjectMapper());
        String first = service.fingerprint(Map.of("b", 2, "a", 1));
        String second = service.fingerprint(Map.of("a", 1, "b", 2));

        assertThat(first).isEqualTo(second).hasSize(64);
    }

    @Test
    void shouldValidateIdempotencyKeyFormat() {
        IdempotencyService service = new IdempotencyService(new ObjectMapper());

        assertThat(service.isValidKey("idem-000000000003")).isTrue();
        assertThat(service.isValidKey("short")).isFalse();
        assertThat(service.isValidKey("contains whitespace 000")).isFalse();
        assertThat(service.isValidKey(null)).isFalse();
    }

    private IdempotencyService.StoredResponse<String> stored(String value) {
        return new IdempotencyService.StoredResponse<>(
                HttpStatus.OK,
                XiaomingResponse.success("ok", value, "v1", "trace", "request")
        );
    }
}
