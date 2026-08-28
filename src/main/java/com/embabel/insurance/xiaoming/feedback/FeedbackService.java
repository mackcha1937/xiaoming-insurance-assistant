package com.embabel.insurance.xiaoming.feedback;

import com.embabel.insurance.xiaoming.api.FeedbackRequest;
import com.embabel.insurance.xiaoming.config.XiaomingFeedbackProperties;
import com.embabel.insurance.xiaoming.contract.XiaomingIntent;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class FeedbackService {

    private static final Pattern PHONE = Pattern.compile("(?<!\\d)1[3-9]\\d{9}(?!\\d)");
    private static final Pattern ID_CARD = Pattern.compile("(?<!\\d)\\d{17}[0-9Xx](?!\\d)");
    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern BANK_CARD = Pattern.compile("(?<!\\d)\\d{16,19}(?!\\d)");
    private static final Pattern URL_QUERY = Pattern.compile("https?://\\S*[?&][^\\s]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern HEALTH = Pattern.compile(
            "病历|诊断|高血压|糖尿病|肿瘤|住院|手术|用药|体检|检查异常|健康告知|身份证|手机号");
    private static final Pattern IDENTITY_OR_ADDRESS = Pattern.compile(
            "(?:姓名|名字|我叫|联系人|住址|家庭地址|联系地址|收件地址|街道|小区|门牌号)\\s*[:：]?\\s*[\\p{IsHan}A-Za-z0-9-]{2,}");

    private final XiaomingFeedbackProperties properties;
    private final FeedbackStore store;
    private final MeterRegistry meterRegistry;
    private final Clock clock;

    @Autowired
    public FeedbackService(
            XiaomingFeedbackProperties properties,
            FeedbackStore store,
            MeterRegistry meterRegistry
    ) {
        this(properties, store, meterRegistry, Clock.systemUTC());
    }

    FeedbackService(
            XiaomingFeedbackProperties properties,
            FeedbackStore store,
            MeterRegistry meterRegistry,
            Clock clock
    ) {
        this.properties = properties;
        this.store = store;
        this.meterRegistry = meterRegistry;
        this.clock = clock;
    }

    public FeedbackResult submit(FeedbackRequest request) {
        if (!properties.enabled()) throw new IllegalStateException("反馈功能当前未开启。");
        XiaomingIntent intent;
        FeedbackReason reason;
        try {
            intent = XiaomingIntent.valueOf(request.getIntent());
            reason = FeedbackReason.valueOf(request.getReason());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("反馈枚举值无效。");
        }
        if (request.isIncludeContext() && intent == XiaomingIntent.HEALTH_DISCLOSURE) {
            throw new IllegalArgumentException("健康告知反馈不得附带问题或回答内容。");
        }

        String question = request.isIncludeContext() ? normalize(request.getQuestionSummary()) : null;
        String answer = request.isIncludeContext() ? normalize(request.getAnswerSummary()) : null;
        ensureSafe(question);
        ensureSafe(answer);
        Instant acceptedAt = clock.instant();
        String feedbackId = "feedback-" + UUID.randomUUID();
        FeedbackRecord record = new FeedbackRecord(
                feedbackId,
                request.getProductId(),
                anonymize(request.getConversationId()),
                request.getKnowledgeVersion(),
                intent,
                reason,
                question,
                answer,
                request.getEvidenceIds(),
                acceptedAt
        );
        store.save(record);
        meterRegistry.counter("xiaoming.feedback.accepted", "intent", intent.name(),
                "reason", reason.name()).increment();
        return new FeedbackResult(feedbackId, "ACCEPTED", acceptedAt);
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim().replaceAll("\\s+", " ");
    }

    private void ensureSafe(String value) {
        if (value == null) return;
        if (PHONE.matcher(value).find() || ID_CARD.matcher(value).find() || EMAIL.matcher(value).find()
                || BANK_CARD.matcher(value).find() || URL_QUERY.matcher(value).find() || HEALTH.matcher(value).find()
                || IDENTITY_OR_ADDRESS.matcher(value).find()) {
            throw new IllegalArgumentException("反馈摘要包含不允许提交的敏感或健康信息。");
        }
    }

    private String anonymize(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return "anon-" + HexFormat.of().formatHex(digest).substring(0, 24).toLowerCase(Locale.ROOT);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
