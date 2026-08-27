package com.embabel.insurance.xiaoming.knowledge;

import com.embabel.insurance.xiaoming.config.XiaomingProductProperties;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

public class KnowledgeVersionRegistry {

    private final String productId;
    private final Clock clock;
    private final Map<String, KnowledgeVersion> versions = new LinkedHashMap<>();
    private String currentVersionId;

    public KnowledgeVersionRegistry(XiaomingProductProperties properties, Clock clock) {
        this.productId = properties.productId();
        this.clock = clock;
        KnowledgeVersion baseline = new KnowledgeVersion(
                properties.currentKnowledgeVersion(),
                properties.productId(),
                KnowledgeVersionStatus.PUBLISHED,
                properties.currentKnowledgeVersion() + "-rules",
                null,
                clock.instant(),
                List.of()
        );
        versions.put(baseline.versionId(), baseline);
        currentVersionId = baseline.versionId();
    }

    public synchronized KnowledgeVersion registerDraft(KnowledgeVersion draft) {
        requireProduct(draft.productId());
        if (draft.status() != KnowledgeVersionStatus.DRAFT) {
            throw new KnowledgeReleaseException("new knowledge version must start as DRAFT");
        }
        if (versions.containsKey(draft.versionId())) {
            throw new KnowledgeReleaseException("knowledge version already exists");
        }
        versions.put(draft.versionId(), draft);
        return draft;
    }

    public synchronized KnowledgeVersion submitForReview(String versionId) {
        KnowledgeVersion draft = requireVersion(versionId);
        if (draft.status() != KnowledgeVersionStatus.DRAFT) {
            throw new KnowledgeReleaseException("only DRAFT can move to REVIEW");
        }
        KnowledgeVersion review = draft.withStatus(KnowledgeVersionStatus.REVIEW, null);
        versions.put(versionId, review);
        return review;
    }

    public synchronized KnowledgeVersion publish(
            String versionId,
            KnowledgeReleaseValidator validator
    ) {
        return publish(versionId, validator, ignored -> {
        });
    }

    public synchronized KnowledgeVersion publish(
            String versionId,
            KnowledgeReleaseValidator validator,
            Consumer<KnowledgeVersion> activation
    ) {
        KnowledgeVersion candidate = requireVersion(versionId);
        if (candidate.status() != KnowledgeVersionStatus.REVIEW) {
            throw new KnowledgeReleaseException("only REVIEW can be published");
        }
        List<String> failures = List.copyOf(validator.validate(candidate));
        if (!failures.isEmpty()) {
            throw new KnowledgeReleaseException("release validation failed: " + String.join(", ", failures));
        }

        Instant publishedAt = clock.instant();
        KnowledgeVersion published = candidate.withStatus(KnowledgeVersionStatus.PUBLISHED, publishedAt);
        try {
            activation.accept(published);
        } catch (RuntimeException exception) {
            throw new KnowledgeReleaseException("release activation failed", exception);
        }

        KnowledgeVersion previous = versions.get(currentVersionId);
        if (previous != null) {
            versions.put(previous.versionId(), previous.withStatus(
                    KnowledgeVersionStatus.DISABLED,
                    previous.publishedAt()
            ));
        }
        versions.put(versionId, published);
        currentVersionId = versionId;
        return published;
    }

    public synchronized KnowledgeVersion rollbackTo(String versionId) {
        KnowledgeVersion target = requireVersion(versionId);
        if (target.status() != KnowledgeVersionStatus.DISABLED) {
            throw new KnowledgeReleaseException("rollback target must be DISABLED");
        }
        KnowledgeVersion current = current();
        versions.put(current.versionId(), current.withStatus(
                KnowledgeVersionStatus.DISABLED,
                current.publishedAt()
        ));
        KnowledgeVersion restored = target.withStatus(KnowledgeVersionStatus.PUBLISHED, clock.instant());
        versions.put(versionId, restored);
        currentVersionId = versionId;
        return restored;
    }

    public synchronized KnowledgeVersion current() {
        return requireVersion(currentVersionId);
    }

    public synchronized Optional<KnowledgeVersion> find(String productId, String versionId) {
        requireProduct(productId);
        return Optional.ofNullable(versions.get(versionId));
    }

    public synchronized List<KnowledgeVersion> history() {
        return List.copyOf(versions.values());
    }

    private KnowledgeVersion requireVersion(String versionId) {
        KnowledgeVersion version = versions.get(versionId);
        if (version == null) {
            throw new KnowledgeReleaseException("knowledge version not found");
        }
        return version;
    }

    private void requireProduct(String candidateProductId) {
        if (!productId.equals(candidateProductId)) {
            throw new KnowledgeReleaseException("cross-product knowledge access is forbidden");
        }
    }
}
