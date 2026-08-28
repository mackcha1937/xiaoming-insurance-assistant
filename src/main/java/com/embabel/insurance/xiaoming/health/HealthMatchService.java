package com.embabel.insurance.xiaoming.health;

import com.embabel.insurance.xiaoming.api.HealthFactRequest;
import com.embabel.insurance.xiaoming.api.HealthMatchRequest;
import com.embabel.insurance.xiaoming.config.XiaomingHealthProperties;
import com.embabel.insurance.xiaoming.contract.XiaomingEvidence;
import com.embabel.insurance.xiaoming.knowledge.ClauseChunk;
import com.embabel.insurance.xiaoming.knowledge.importer.KnowledgeSnapshotStore;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;

@Service
public class HealthMatchService {

    private static final String PRODUCT_ID = "36743";
    private static final String MATCH_VERSION = "health-match-v1";
    private static final Pattern SENSITIVE = Pattern.compile("(?<!\\d)(?:1\\d{10}|\\d{17}[0-9Xx])(?!\\d)");
    private static final Set<String> QUESTION_1_CODES = Set.of("HOSPITALIZATION", "SURGERY");
    private static final Set<String> QUESTION_3_CODES = Set.of(
            "SYMPTOM_HEADACHE", "SYMPTOM_CHEST_PAIN", "SYMPTOM_BLEEDING", "SYMPTOM_FEVER", "WEIGHT_LOSS"
    );
    private static final Set<String> QUESTION_5_CODES = Set.of(
            "LOW_BIRTH_WEIGHT", "PREMATURE_BIRTH", "DEVELOPMENTAL_DELAY"
    );
    private static final Set<String> QUESTION_6_CODES = Set.of("FEMALE_CONDITION", "PREGNANCY_RISK");

    private final KnowledgeSnapshotStore snapshotStore;
    private final XiaomingHealthProperties properties;
    private final Clock clock;

    @Autowired
    public HealthMatchService(KnowledgeSnapshotStore snapshotStore, XiaomingHealthProperties properties) {
        this(snapshotStore, properties, Clock.systemDefaultZone());
    }

    HealthMatchService(
            KnowledgeSnapshotStore snapshotStore,
            XiaomingHealthProperties properties,
            Clock clock
    ) {
        this.snapshotStore = snapshotStore;
        this.properties = properties;
        this.clock = clock;
    }

    public HealthMatchResult match(HealthMatchRequest request) {
        KnowledgeSnapshotStore.Snapshot snapshot = snapshotStore.current().orElseThrow(() ->
                new HealthServiceException(
                        HealthServiceException.Reason.SNAPSHOT_UNAVAILABLE,
                        "当前没有可用的已发布健康告知版本。",
                        ""
                ));
        String currentVersion = snapshot.knowledgeVersion().versionId();
        validate(request, currentVersion);
        List<HealthFactDraft> facts = request.getFacts().stream().map(this::toFact).toList();
        List<HealthQuestionMatch> findings = new ArrayList<>();

        Set<String> conflicts = conflictingCodes(facts);
        if (!conflicts.isEmpty()) {
            findings.add(insufficient(
                    definition("INPUT", "健康事实存在互相矛盾的肯定与否定描述。", "健康告知仅供预览"),
                    facts.stream().filter(fact -> conflicts.contains(fact.code())).toList(),
                    "同一事实同时存在肯定和否定描述，不能自动选择其一。",
                    List.of("请确认事实的实际状态"),
                    snapshot,
                    currentVersion
            ));
        }

        List<HealthFactDraft> lowConfidence = facts.stream()
                .filter(fact -> fact.confidence() < properties.getMinimumConfidence())
                .toList();
        if (!lowConfidence.isEmpty()) {
            findings.add(insufficient(
                    definition("INPUT", "低置信度事实需要人工核对。", "健康告知仅供预览"),
                    lowConfidence,
                    "存在低置信度事实，不能进入确定性命中判断。",
                    List.of("请核对低置信度事实"),
                    snapshot,
                    currentVersion
            ));
        }
        List<HealthFactDraft> eligibleFacts = facts.stream()
                .filter(fact -> !conflicts.contains(fact.code()))
                .filter(fact -> fact.confidence() >= properties.getMinimumConfidence())
                .toList();

        evaluateWindowQuestion(
                findings,
                definition("1", "过去2年内的住院、手术、相关建议及保险情况。", "就医行为及保险情况"),
                present(eligibleFacts, fact -> QUESTION_1_CODES.contains(fact.code())),
                LocalDate.now(clock).minusYears(2),
                snapshot,
                currentVersion
        );

        List<HealthFactDraft> diseases = present(eligibleFacts, fact -> fact.type() == HealthFactType.DISEASE);
        if (!diseases.isEmpty()) {
            findings.add(hit(
                    definition("2", "目前或曾经患有健康告知列明的疾病或症状。", "目前或曾经患有"),
                    diseases,
                    "确认事实中出现当前健康告知列明的疾病或症状。",
                    snapshot,
                    currentVersion
            ));
        }

        List<HealthFactDraft> symptoms = present(eligibleFacts, fact -> QUESTION_3_CODES.contains(fact.code()));
        List<HealthFactDraft> q3Applicable = new ArrayList<>();
        for (HealthFactDraft fact : symptoms) {
            if ("WEIGHT_LOSS".equals(fact.code()) && (fact.numericValue() == null || fact.unit() == null)) {
                findings.add(insufficient(
                        definition("3", "最近1年内的不明原因症状或体重减轻。", "最近1年内是否存在过"),
                        List.of(fact),
                        "体重减轻缺少可比较的数值或单位。",
                        List.of("体重减轻数值", "单位"),
                        snapshot,
                        currentVersion
                ));
            } else if (!"WEIGHT_LOSS".equals(fact.code()) || fact.numericValue() > 5.0) {
                q3Applicable.add(fact);
            }
        }
        evaluateWindowQuestion(
                findings,
                definition("3", "最近1年内的不明原因症状或体重减轻。", "最近1年内是否存在过"),
                q3Applicable,
                LocalDate.now(clock).minusYears(1),
                snapshot,
                currentVersion
        );

        evaluateQuestion4(findings, eligibleFacts, snapshot, currentVersion);
        evaluateQuestion5(findings, eligibleFacts, request.getInsuredAgeYears(), snapshot, currentVersion);
        evaluateQuestion6(findings, eligibleFacts, request.getInsuredAgeYears(), request.getInsuredSex(), snapshot, currentVersion);

        List<HealthFactDraft> activities = present(eligibleFacts, fact -> "HIGH_RISK_ACTIVITY".equals(fact.code()));
        if (!activities.isEmpty()) {
            findings.add(hit(
                    definition("7", "当前或计划参加列明的高风险活动或前往高风险地区。", "职业情况"),
                    activities,
                    "确认事实中出现健康告知列明的高风险活动。",
                    snapshot,
                    currentVersion
            ));
        }

        List<HealthFactDraft> unevaluatedPositive = present(eligibleFacts, fact -> !isKnownCode(fact));
        if (!unevaluatedPositive.isEmpty()) {
            findings.add(insufficient(
                    definition("INPUT", "其他健康事实需要补充与健康问题的对应条件。", "健康告知仅供预览"),
                    unevaluatedPositive,
                    "事实存在，但缺少可执行健康问题所需的对应条件。",
                    List.of("事实对应的检查、治疗、时间或当前状态"),
                    snapshot,
                    currentVersion
            ));
        }

        HealthMatchState state = findings.stream().anyMatch(item -> item.state() == HealthMatchState.HIT)
                ? HealthMatchState.HIT
                : findings.stream().anyMatch(item -> item.state() == HealthMatchState.INSUFFICIENT)
                ? HealthMatchState.INSUFFICIENT
                : HealthMatchState.NO_HIT_FOUND;
        String notice = switch (state) {
            case HIT -> "发现可能命中的健康告知项。正式投保时请完整阅读并如实回答；本结果不是核保结论。";
            case INSUFFICIENT -> "当前确认事实缺少关键条件，请补充后重新匹配；系统不会猜测。";
            case NO_HIT_FOUND -> "仅在当前确认资料中暂未发现命中，仍需由客户完整阅读并如实回答全部健康问题。";
        };
        return new HealthMatchResult(
                MATCH_VERSION + "@" + currentVersion,
                currentVersion,
                state,
                findings,
                notice
        );
    }

    private void evaluateWindowQuestion(
            List<HealthQuestionMatch> findings,
            QuestionDefinition definition,
            List<HealthFactDraft> candidates,
            LocalDate cutoff,
            KnowledgeSnapshotStore.Snapshot snapshot,
            String version
    ) {
        List<HealthFactDraft> missingDate = candidates.stream().filter(fact -> fact.date() == null).toList();
        if (!missingDate.isEmpty()) {
            findings.add(insufficient(definition, missingDate,
                    "该健康问题有明确时间窗，但确认事实缺少日期。", List.of("发生日期"), snapshot, version));
        }
        List<HealthFactDraft> futureDate = candidates.stream()
                .filter(fact -> fact.date() != null && fact.date().isAfter(LocalDate.now(clock)))
                .toList();
        if (!futureDate.isEmpty()) {
            findings.add(insufficient(definition, futureDate,
                    "事实日期晚于当前日期，无法匹配时间窗。", List.of("有效发生日期"), snapshot, version));
        }
        List<HealthFactDraft> hits = candidates.stream()
                .filter(fact -> fact.date() != null)
                .filter(fact -> !fact.date().isAfter(LocalDate.now(clock)))
                .filter(fact -> !fact.date().isBefore(cutoff))
                .toList();
        if (!hits.isEmpty()) {
            findings.add(hit(definition, hits, "确认事实落在该健康问题的时间窗内。", snapshot, version));
        }
    }

    private void evaluateQuestion4(
            List<HealthQuestionMatch> findings,
            List<HealthFactDraft> facts,
            KnowledgeSnapshotStore.Snapshot snapshot,
            String version
    ) {
        QuestionDefinition definition = definition("4", "最近1年内的检查异常及当前 BMI。", "以下一项或几项检查异常");
        List<HealthFactDraft> bmiFacts = present(facts, fact -> "BMI".equals(fact.code()));
        for (HealthFactDraft fact : bmiFacts) {
            if (fact.numericValue() == null || fact.unit() == null) {
                findings.add(insufficient(definition, List.of(fact), "BMI 缺少数值或单位。",
                        List.of("BMI 数值", "BMI 单位"), snapshot, version));
            } else if (fact.numericValue() > 30.0) {
                findings.add(hit(definition, List.of(fact), "确认的当前 BMI 大于 30。", snapshot, version));
            }
        }

        List<HealthFactDraft> examinations = present(facts, fact -> "ABNORMAL_EXAM".equals(fact.code()));
        for (HealthFactDraft fact : examinations) {
            if (!fact.value().matches(".*(?:建议|治疗|进一步检查|复查|住院|手术).*$")) {
                findings.add(insufficient(definition, List.of(fact),
                        "检查异常缺少医生是否建议治疗或进一步检查。",
                        List.of("医生建议"), snapshot, version));
            } else {
                evaluateWindowQuestion(findings, definition, List.of(fact),
                        LocalDate.now(clock).minusYears(1), snapshot, version);
            }
        }
    }

    private void evaluateQuestion5(
            List<HealthQuestionMatch> findings,
            List<HealthFactDraft> facts,
            Integer age,
            KnowledgeSnapshotStore.Snapshot snapshot,
            String version
    ) {
        List<HealthFactDraft> birth = present(facts, fact -> QUESTION_5_CODES.contains(fact.code()));
        if (birth.isEmpty()) return;
        QuestionDefinition definition = definition("5", "两周岁（含）以下儿童的出生和发育情况。", "两周岁（含）以下儿童告知");
        if (age == null) {
            findings.add(insufficient(definition, birth, "缺少被保险人周岁年龄。",
                    List.of("被保险人周岁年龄"), snapshot, version));
        } else if (age <= 2) {
            findings.add(hit(definition, birth, "确认事实适用于两周岁（含）以下儿童告知。", snapshot, version));
        }
    }

    private void evaluateQuestion6(
            List<HealthQuestionMatch> findings,
            List<HealthFactDraft> facts,
            Integer age,
            String sex,
            KnowledgeSnapshotStore.Snapshot snapshot,
            String version
    ) {
        List<HealthFactDraft> female = present(facts, fact -> QUESTION_6_CODES.contains(fact.code()));
        if (female.isEmpty()) return;
        QuestionDefinition definition = definition("6", "16周岁（含）以上女性健康及妊娠情况。", "16周岁（含）以上女性告知");
        if (age == null || sex == null || "UNSPECIFIED".equals(sex)) {
            findings.add(insufficient(definition, female, "缺少适用人群条件。",
                    List.of("被保险人周岁年龄", "被保险人性别"), snapshot, version));
        } else if (age >= 16 && "FEMALE".equals(sex)) {
            findings.add(hit(definition, female, "确认事实适用于16周岁（含）以上女性告知。", snapshot, version));
        }
    }

    private HealthQuestionMatch hit(
            QuestionDefinition definition,
            List<HealthFactDraft> facts,
            String reason,
            KnowledgeSnapshotStore.Snapshot snapshot,
            String version
    ) {
        return new HealthQuestionMatch(
                definition.id(), definition.text(), HealthMatchState.HIT,
                facts.stream().map(HealthFactDraft::factId).toList(), reason, List.of(),
                evidence(definition, snapshot, version)
        );
    }

    private HealthQuestionMatch insufficient(
            QuestionDefinition definition,
            List<HealthFactDraft> facts,
            String reason,
            List<String> missing,
            KnowledgeSnapshotStore.Snapshot snapshot,
            String version
    ) {
        return new HealthQuestionMatch(
                definition.id(), definition.text(), HealthMatchState.INSUFFICIENT,
                facts.stream().map(HealthFactDraft::factId).toList(), reason, missing,
                evidence(definition, snapshot, version)
        );
    }

    private XiaomingEvidence evidence(
            QuestionDefinition definition,
            KnowledgeSnapshotStore.Snapshot snapshot,
            String version
    ) {
        ClauseChunk chunk = snapshot.chunks().stream()
                .filter(item -> item.documentName().contains("健康告知"))
                .filter(item -> item.text().contains(definition.marker()))
                .findFirst()
                .orElseGet(() -> snapshot.chunks().stream()
                        .filter(item -> item.documentName().contains("健康告知"))
                        .findFirst()
                        .orElseThrow(() -> new HealthServiceException(
                                HealthServiceException.Reason.SNAPSHOT_UNAVAILABLE,
                                "当前已发布版本缺少健康告知原文依据。",
                                version
                        )));
        return new XiaomingEvidence(
                chunk.chunkId(), chunk.documentName(), chunk.section(), chunk.pdfPage(), chunk.printedPage(),
                limit(chunk.text(), 420), version
        );
    }

    private void validate(HealthMatchRequest request, String version) {
        if (!PRODUCT_ID.equals(request.getProductId())) {
            throw new HealthServiceException(HealthServiceException.Reason.VALIDATION,
                    "当前仅支持产品 36743。", version);
        }
        if (!version.equals(request.getKnowledgeVersion())) {
            throw new HealthServiceException(HealthServiceException.Reason.VERSION_CONFLICT,
                    "健康告知资料版本已更新，请重新确认后匹配。", version);
        }
        if (!request.isConfirmed() || request.getFacts().stream().anyMatch(fact -> !fact.isConfirmed())) {
            throw new HealthServiceException(HealthServiceException.Reason.VALIDATION,
                    "事实未全部确认，不得执行健康告知匹配。", version);
        }
        Set<String> ids = new HashSet<>();
        for (HealthFactRequest fact : request.getFacts()) {
            if (!ids.add(fact.getFactId())) {
                throw new HealthServiceException(HealthServiceException.Reason.VALIDATION,
                        "factId 不能重复。", version);
            }
            if (SENSITIVE.matcher(fact.getValue()).find()
                    || SENSITIVE.matcher(fact.getSourceLocation().getExcerpt()).find()) {
                throw new HealthServiceException(HealthServiceException.Reason.VALIDATION,
                        "确认事实不能包含手机号或身份证号。", version);
            }
        }
    }

    private HealthFactDraft toFact(HealthFactRequest request) {
        HealthFactRequest.HealthSourceLocationRequest source = request.getSourceLocation();
        return new HealthFactDraft(
                request.getFactId(), HealthFactType.valueOf(request.getType()), request.getCode(),
                request.getLabel(), request.getValue(), request.getNumericValue(), request.getUnit(),
                request.getDate(), HealthFactPolarity.valueOf(request.getPolarity()),
                new HealthSourceLocation(source.getSourceId(), source.getSourceLabel(), source.getPage(),
                        source.getLine(), source.getExcerpt()),
                request.getConfidence(), request.isConfirmed()
        );
    }

    private Set<String> conflictingCodes(List<HealthFactDraft> facts) {
        Map<String, Set<HealthFactPolarity>> polarities = new LinkedHashMap<>();
        facts.forEach(fact -> polarities.computeIfAbsent(fact.code(), ignored -> new HashSet<>()).add(fact.polarity()));
        Set<String> conflicts = new HashSet<>();
        polarities.forEach((code, values) -> {
            if (values.contains(HealthFactPolarity.PRESENT) && values.contains(HealthFactPolarity.ABSENT)) {
                conflicts.add(code);
            }
        });
        return conflicts;
    }

    private List<HealthFactDraft> present(List<HealthFactDraft> facts, Predicate<HealthFactDraft> predicate) {
        return facts.stream()
                .filter(fact -> fact.polarity() == HealthFactPolarity.PRESENT)
                .filter(predicate)
                .toList();
    }

    private boolean isKnownCode(HealthFactDraft fact) {
        return fact.type() == HealthFactType.DISEASE
                || QUESTION_1_CODES.contains(fact.code())
                || QUESTION_3_CODES.contains(fact.code())
                || "ABNORMAL_EXAM".equals(fact.code())
                || "BMI".equals(fact.code())
                || QUESTION_5_CODES.contains(fact.code())
                || QUESTION_6_CODES.contains(fact.code())
                || "HIGH_RISK_ACTIVITY".equals(fact.code())
                || fact.type() == HealthFactType.CURRENT_STATUS;
    }

    private QuestionDefinition definition(String id, String text, String marker) {
        return new QuestionDefinition(id, text, marker);
    }

    private String limit(String value, int max) {
        String compact = value.replaceAll("\\s+", " ").trim();
        return compact.length() <= max ? compact : compact.substring(0, max) + "…";
    }

    private record QuestionDefinition(String id, String text, String marker) {
    }
}
