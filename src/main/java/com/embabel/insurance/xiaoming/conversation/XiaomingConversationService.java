package com.embabel.insurance.xiaoming.conversation;

import com.embabel.insurance.xiaoming.api.ChatRequest;
import com.embabel.insurance.xiaoming.config.XiaomingConversationProperties;
import com.embabel.insurance.xiaoming.config.XiaomingKnowledgeProperties;
import com.embabel.insurance.xiaoming.contract.BusinessStatus;
import com.embabel.insurance.xiaoming.contract.CardType;
import com.embabel.insurance.xiaoming.contract.MissingField;
import com.embabel.insurance.xiaoming.contract.XiaomingCard;
import com.embabel.insurance.xiaoming.contract.XiaomingError;
import com.embabel.insurance.xiaoming.contract.XiaomingErrorCode;
import com.embabel.insurance.xiaoming.contract.XiaomingEvidence;
import com.embabel.insurance.xiaoming.contract.XiaomingIntent;
import com.embabel.insurance.xiaoming.contract.XiaomingResponse;
import com.embabel.insurance.xiaoming.contract.XiaomingSuggestion;
import com.embabel.insurance.xiaoming.evidence.EvidenceDecision;
import com.embabel.insurance.xiaoming.evidence.EvidenceGateStatus;
import com.embabel.insurance.xiaoming.evidence.EvidenceService;
import com.embabel.insurance.xiaoming.guardrail.IntentGuardDecision;
import com.embabel.insurance.xiaoming.guardrail.IntentGuardService;
import com.embabel.insurance.xiaoming.guardrail.IntentGuardStatus;
import com.embabel.insurance.xiaoming.knowledge.ClauseChunk;
import com.embabel.insurance.xiaoming.knowledge.DocumentType;
import com.embabel.insurance.xiaoming.knowledge.importer.KnowledgeSnapshotStore;
import com.embabel.insurance.xiaoming.retrieval.HybridRetrievalService;
import com.embabel.insurance.xiaoming.retrieval.InMemoryBm25SearchBackend;
import com.embabel.insurance.xiaoming.retrieval.RankedChunk;
import com.embabel.insurance.xiaoming.retrieval.RetrievalDependencyException;
import com.embabel.insurance.xiaoming.retrieval.RetrievalResult;
import com.embabel.insurance.xiaoming.retrieval.RrfFusionService;
import com.embabel.insurance.xiaoming.retrieval.SearchFilter;
import com.embabel.insurance.xiaoming.retrieval.SearchBackend;
import com.embabel.insurance.xiaoming.rules.RuleEngine;
import com.embabel.insurance.xiaoming.rules.RuleEvaluation;
import com.embabel.insurance.xiaoming.rules.RuleMatchStatus;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class XiaomingConversationService {

    private static final String PRODUCT_ID = "36743";
    private static final SearchBackend UNAVAILABLE_VECTOR = (query, filter, topK) -> {
        throw new RetrievalDependencyException("vector retrieval is not configured for Phase 03");
    };
    private static final Map<XiaomingIntent, Set<DocumentType>> DOCUMENT_TYPES = documentTypes();
    private static final Map<XiaomingIntent, Set<String>> DOCUMENT_IDS = documentIds();

    private final IntentGuardService intentGuardService;
    private final KnowledgeSnapshotStore snapshotStore;
    private final RuleEngine ruleEngine;
    private final EvidenceService evidenceService;
    private final RrfFusionService fusionService;
    private final XiaomingKnowledgeProperties knowledgeProperties;
    private final XiaomingConversationProperties conversationProperties;
    private final ConversationFactExtractor factExtractor;
    private final AnswerGenerator answerGenerator;
    private final OutputGuardService outputGuardService;

    public XiaomingConversationService(
            IntentGuardService intentGuardService,
            KnowledgeSnapshotStore snapshotStore,
            RuleEngine ruleEngine,
            EvidenceService evidenceService,
            RrfFusionService fusionService,
            XiaomingKnowledgeProperties knowledgeProperties,
            XiaomingConversationProperties conversationProperties,
            ConversationFactExtractor factExtractor,
            AnswerGenerator answerGenerator,
            OutputGuardService outputGuardService
    ) {
        this.intentGuardService = intentGuardService;
        this.snapshotStore = snapshotStore;
        this.ruleEngine = ruleEngine;
        this.evidenceService = evidenceService;
        this.fusionService = fusionService;
        this.knowledgeProperties = knowledgeProperties;
        this.conversationProperties = conversationProperties;
        this.factExtractor = factExtractor;
        this.answerGenerator = answerGenerator;
        this.outputGuardService = outputGuardService;
    }

    public ConversationResult answer(ChatRequest request, String traceId, String requestId) {
        IntentGuardDecision guard = intentGuardService.evaluate(request.getMessage());
        if (guard.status() == IntentGuardStatus.REFUSED) {
            return refused(guard.refusalReason(), traceId, requestId, null);
        }
        if (guard.status() == IntentGuardStatus.NOT_AVAILABLE) {
            XiaomingIntent intent = guard.unavailableIntents().isEmpty()
                    ? null
                    : guard.unavailableIntents().getFirst();
            return refused(guard.refusalReason(), traceId, requestId, intent);
        }

        KnowledgeSnapshotStore.Snapshot snapshot = snapshotStore.current().orElse(null);
        if (snapshot == null) {
            return dependencyUnavailable(traceId, requestId);
        }
        String knowledgeVersion = snapshot.knowledgeVersion().versionId();
        if (request.getKnowledgeVersionHint() != null
                && !request.getKnowledgeVersionHint().isBlank()
                && !request.getKnowledgeVersionHint().equals(knowledgeVersion)) {
            return conflict("请求的知识版本与当前已发布版本不一致。", knowledgeVersion, traceId, requestId);
        }
        if (guard.allowedIntents().size() == 1
                && guard.allowedIntents().contains(XiaomingIntent.OCCUPATION_QUERY)) {
            return occupationHandoff(knowledgeVersion, traceId, requestId);
        }
        if (guard.allowedIntents().size() == 1
                && guard.allowedIntents().contains(XiaomingIntent.MATERIAL_CHECKLIST)) {
            return checklistHandoff(knowledgeVersion, traceId, requestId);
        }

        Map<String, Object> facts = factExtractor.extract(request.getMessage(), request.getContext());
        if (needsInsuredAge(request.getMessage(), facts, guard.allowedIntents())) {
            return needInsuredAge(knowledgeVersion, traceId, requestId);
        }

        List<XiaomingCard> cards = new ArrayList<>();
        Map<String, XiaomingEvidence> evidenceById = new LinkedHashMap<>();
        List<String> failedReasons = new ArrayList<>();
        for (XiaomingIntent intent : guard.allowedIntents()) {
            if (intent == XiaomingIntent.OCCUPATION_QUERY) {
                cards.add(new XiaomingCard(
                        "occupation-confirmation-required",
                        "1.0",
                        CardType.QUESTION,
                        "职业需要先确认",
                        Map.of("message", "请使用职业查询面板搜索并选择候选；确认前不会判断职业类别。")
                ));
                continue;
            }
            if (intent == XiaomingIntent.MATERIAL_CHECKLIST) {
                cards.add(new XiaomingCard(
                        "checklist-input-required",
                        "1.0",
                        CardType.QUESTION,
                        "资料清单需要补充条件",
                        Map.of("message", "请使用动态资料清单面板补充年龄、投保关系、证件、受益人和健康状态。")
                ));
                continue;
            }
            IntentAnswer intentAnswer = answerIntent(intent, request.getMessage(), facts, snapshot);
            if (!intentAnswer.success()) {
                failedReasons.add(intentAnswer.failureReason());
                continue;
            }
            intentAnswer.evidence().forEach(evidence -> evidenceById.putIfAbsent(evidence.evidenceId(), evidence));
            cards.add(new XiaomingCard(
                    "answer-" + intent.name().toLowerCase(),
                    "1.0",
                    CardType.SUMMARY,
                    intent.getLabel(),
                    Map.of(
                            "answer", intentAnswer.answer(),
                            "ruleIds", intentAnswer.ruleIds(),
                            "retrievalMode", intentAnswer.retrievalMode(),
                            "degraded", intentAnswer.degraded()
                    )
            ));
        }

        if (cards.isEmpty()) {
            String reason = failedReasons.isEmpty() ? "没有找到可核验的正式依据。" : failedReasons.getFirst();
            return insufficient(reason, knowledgeVersion, traceId, requestId);
        }

        boolean mixed = guard.status() == IntentGuardStatus.MIXED;
        String message = mixed
                ? "已回答可支持的部分；其余部分因超出当前能力边界未作答。"
                : "已根据当前正式资料生成回答。";
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("conversationId", request.getConversationId());
        data.put("answeredIntents", guard.allowedIntents().stream().map(Enum::name).toList());
        data.put("partial", mixed || !failedReasons.isEmpty());
        if (mixed) {
            data.put("boundaryNotice", guard.refusalReason());
        }
        XiaomingIntent singleIntent = guard.allowedIntents().size() == 1
                ? guard.allowedIntents().getFirst()
                : null;
        XiaomingResponse<Map<String, Object>> response = new XiaomingResponse<>(
                BusinessStatus.SUCCESS,
                singleIntent,
                message,
                Map.copyOf(data),
                cards,
                List.copyOf(evidenceById.values()),
                suggestions(),
                List.of(),
                List.of(),
                knowledgeVersion,
                traceId,
                requestId,
                null
        );
        return new ConversationResult(HttpStatus.OK, response);
    }

    private IntentAnswer answerIntent(
            XiaomingIntent intent,
            String question,
            Map<String, Object> facts,
            KnowledgeSnapshotStore.Snapshot snapshot
    ) {
        String version = snapshot.knowledgeVersion().versionId();
        List<ClauseChunk> intentChunks = selectIntentChunks(intent, question, snapshot.chunks());
        HybridRetrievalService retrievalService = new HybridRetrievalService(
                new InMemoryBm25SearchBackend(intentChunks),
                UNAVAILABLE_VECTOR,
                fusionService,
                knowledgeProperties.rrfK(),
                knowledgeProperties.evidenceThreshold(),
                knowledgeProperties.degradedVectorThreshold()
        );
        RetrievalResult retrieval;
        try {
            retrieval = retrievalService.search(
                    canonicalQuery(intent, question),
                    new SearchFilter(PRODUCT_ID, version, DOCUMENT_TYPES.get(intent)),
                    conversationProperties.topK()
            );
        } catch (RetrievalDependencyException exception) {
            return IntentAnswer.failure("正式资料检索服务暂不可用。");
        }

        List<RuleEvaluation> matchedRules = relevantMatchedRules(intent, snapshot, facts);
        RetrievalResult withRuleEvidence = includeRuleEvidence(retrieval, matchedRules, snapshot.chunks());
        EvidenceDecision decision = evidenceService.evaluate(PRODUCT_ID, version, withRuleEvidence, matchedRules);
        if (decision.status() != EvidenceGateStatus.SUFFICIENT) {
            return IntentAnswer.failure(decision.reason());
        }

        List<String> ruleResults = matchedRules.stream()
                .map(evaluation -> evaluation.rule().result())
                .toList();
        AnswerRequest answerRequest = new AnswerRequest(
                intent,
                question,
                ruleResults,
                decision.evidence(),
                conversationProperties.maxEvidenceCharacters()
        );
        String answer;
        try {
            answer = answerGenerator.generate(answerRequest);
        } catch (AnswerGenerationException exception) {
            return IntentAnswer.failure("模型暂时无法生成回答，请稍后重试。");
        }
        OutputGuardService.GuardedOutput guarded = outputGuardService.validate(
                answer,
                answerRequest,
                conversationProperties.maxAnswerCharacters()
        );
        if (!guarded.allowed()) {
            return IntentAnswer.failure(guarded.reason());
        }
        return IntentAnswer.success(
                answer,
                decision.evidence(),
                decision.ruleIds(),
                decision.retrievalMode().name(),
                decision.degraded()
        );
    }

    private List<RuleEvaluation> relevantMatchedRules(
            XiaomingIntent intent,
            KnowledgeSnapshotStore.Snapshot snapshot,
            Map<String, Object> facts
    ) {
        if (intent != XiaomingIntent.APPLICATION_RULES
                && intent != XiaomingIntent.CLAIMS_PATH
                && intent != XiaomingIntent.COVERAGE_RESPONSIBILITY) {
            return List.of();
        }
        return ruleEngine.evaluate(PRODUCT_ID, snapshot.knowledgeVersion().versionId(), snapshot.rules(), facts)
                .stream()
                .filter(evaluation -> evaluation.status() == RuleMatchStatus.MATCHED)
                .filter(evaluation -> relevantRule(intent, evaluation))
                .toList();
    }

    private boolean relevantRule(XiaomingIntent intent, RuleEvaluation evaluation) {
        String id = evaluation.rule().ruleId();
        return switch (intent) {
            case CLAIMS_PATH -> id.equals("claims-path");
            case COVERAGE_RESPONSIBILITY -> id.equals("to-70-limited-responsibilities");
            case APPLICATION_RULES -> !id.equals("claims-path") && !id.equals("to-70-limited-responsibilities");
            default -> false;
        };
    }

    private RetrievalResult includeRuleEvidence(
            RetrievalResult retrieval,
            List<RuleEvaluation> rules,
            List<ClauseChunk> allChunks
    ) {
        Set<String> requiredIds = new LinkedHashSet<>();
        rules.forEach(rule -> requiredIds.addAll(rule.rule().evidenceIds()));
        if (requiredIds.isEmpty()) {
            return retrieval;
        }
        Map<String, RankedChunk> chunks = new LinkedHashMap<>();
        allChunks.stream()
                .filter(chunk -> requiredIds.contains(chunk.chunkId()))
                .forEach(chunk -> chunks.putIfAbsent(
                        chunk.chunkId(),
                        new RankedChunk(chunk, 1.0d, true, false)
                ));
        retrieval.chunks().forEach(chunk -> chunks.putIfAbsent(chunk.chunk().chunkId(), chunk));
        return new RetrievalResult(
                List.copyOf(chunks.values()),
                retrieval.mode(),
                retrieval.degraded(),
                retrieval.evidenceThreshold()
        );
    }

    private boolean needsInsuredAge(String message, Map<String, Object> facts, List<XiaomingIntent> intents) {
        if (!intents.contains(XiaomingIntent.APPLICATION_RULES) || facts.containsKey("insuredAgeYears")) {
            return false;
        }
        return message.contains("最高保额") || message.contains("保额上限") || message.contains("最多能买多少");
    }

    private ConversationResult needInsuredAge(String version, String traceId, String requestId) {
        XiaomingResponse<Map<String, Object>> response = new XiaomingResponse<>(
                BusinessStatus.NEED_INPUT,
                XiaomingIntent.APPLICATION_RULES,
                "为了核对对应年龄段的保额规则，还需要被保险人的周岁年龄。",
                Map.of("question", "被保险人目前多少周岁？"),
                List.of(new XiaomingCard(
                        "question-insured-age",
                        "1.0",
                        CardType.QUESTION,
                        "补充一项信息",
                        Map.of("field", "insuredAgeYears", "prompt", "被保险人目前多少周岁？")
                )),
                List.of(),
                List.of(),
                List.of(),
                List.of(new MissingField("insuredAgeYears", "被保险人年龄", "number", true, List.of())),
                version,
                traceId,
                requestId,
                null
        );
        return new ConversationResult(HttpStatus.OK, response);
    }

    private ConversationResult refused(String reason, String traceId, String requestId, XiaomingIntent intent) {
        String version = snapshotStore.current()
                .map(snapshot -> snapshot.knowledgeVersion().versionId())
                .orElse(null);
        XiaomingResponse<Map<String, Object>> response = new XiaomingResponse<>(
                BusinessStatus.REFUSED,
                intent,
                reason,
                Map.of("boundary", "DARWIN_12_NINE_INTENTS"),
                List.of(new XiaomingCard(
                        "boundary-notice",
                        "1.0",
                        CardType.NOTICE,
                        "能力边界",
                        Map.of("message", reason)
                )),
                List.of(),
                suggestions(),
                List.of(),
                List.of(),
                version,
                traceId,
                requestId,
                new XiaomingError(XiaomingErrorCode.INTENT_NOT_ALLOWED, reason, false, List.of())
        );
        return new ConversationResult(HttpStatus.OK, response);
    }

    private ConversationResult insufficient(String reason, String version, String traceId, String requestId) {
        XiaomingResponse<Map<String, Object>> response = new XiaomingResponse<>(
                BusinessStatus.INSUFFICIENT_EVIDENCE,
                null,
                "当前正式资料不足，无法安全生成确定答案。",
                Map.of("reason", reason),
                List.of(),
                List.of(),
                suggestions(),
                List.of(),
                List.of(),
                version,
                traceId,
                requestId,
                new XiaomingError(XiaomingErrorCode.EVIDENCE_INSUFFICIENT, reason, false, List.of())
        );
        return new ConversationResult(HttpStatus.OK, response);
    }

    private ConversationResult conflict(String reason, String version, String traceId, String requestId) {
        XiaomingResponse<Map<String, Object>> response = new XiaomingResponse<>(
                BusinessStatus.CONFLICT,
                null,
                "知识版本存在冲突，本次未生成回答。",
                Map.of("reason", reason),
                List.of(),
                List.of(),
                suggestions(),
                List.of(),
                List.of(),
                version,
                traceId,
                requestId,
                new XiaomingError(XiaomingErrorCode.KNOWLEDGE_CONFLICT, reason, false, List.of())
        );
        return new ConversationResult(HttpStatus.OK, response);
    }

    private ConversationResult dependencyUnavailable(String traceId, String requestId) {
        String reason = "正式知识库尚未发布或加载。";
        XiaomingResponse<Map<String, Object>> response = new XiaomingResponse<>(
                BusinessStatus.ERROR,
                null,
                reason,
                Map.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                null,
                traceId,
                requestId,
                new XiaomingError(XiaomingErrorCode.DEPENDENCY_UNAVAILABLE, reason, true, List.of())
        );
        return new ConversationResult(HttpStatus.SERVICE_UNAVAILABLE, response);
    }

    private List<XiaomingSuggestion> suggestions() {
        return List.of(
                new XiaomingSuggestion("coverage", "保障责任", "这款产品主要保什么？", XiaomingIntent.COVERAGE_RESPONSIBILITY),
                new XiaomingSuggestion("exclusions", "免责限制", "哪些情况不承担保险责任？", XiaomingIntent.EXCLUSIONS_AND_LIMITATIONS),
                new XiaomingSuggestion("rules", "投保规则", "投保年龄和缴费期限有什么要求？", XiaomingIntent.APPLICATION_RULES),
                new XiaomingSuggestion("occupation", "职业查询", "我想查询职业分类", XiaomingIntent.OCCUPATION_QUERY),
                new XiaomingSuggestion("process", "投保流程", "投保前需要经过哪些步骤？", XiaomingIntent.APPLICATION_PROCESS),
                new XiaomingSuggestion("checklist", "资料清单", "投保前需要准备什么资料？", XiaomingIntent.MATERIAL_CHECKLIST),
                new XiaomingSuggestion("claims", "理赔路径", "发生保险事故后如何报案？", XiaomingIntent.CLAIMS_PATH)
        );
    }

    private ConversationResult occupationHandoff(String version, String traceId, String requestId) {
        String message = "职业需要先搜索并由顾问确认候选；确认前不会显示职业类别或可承保结论。";
        XiaomingResponse<Map<String, Object>> response = new XiaomingResponse<>(
                BusinessStatus.NEED_INPUT,
                XiaomingIntent.OCCUPATION_QUERY,
                message,
                Map.of("target", "occupation-search"),
                List.of(new XiaomingCard(
                        "occupation-search-required",
                        "1.0",
                        CardType.QUESTION,
                        "打开职业查询",
                        Map.of("message", "请在职业查询面板输入一个职业名称或日常工作内容。")
                )),
                List.of(),
                suggestions(),
                List.of(),
                List.of(),
                version,
                traceId,
                requestId,
                null
        );
        return new ConversationResult(HttpStatus.OK, response);
    }

    private ConversationResult checklistHandoff(String version, String traceId, String requestId) {
        String message = "动态资料清单需要先补充非敏感条件；清单不会要求您在本 POC 中填写身份号码。";
        XiaomingResponse<Map<String, Object>> response = new XiaomingResponse<>(
                BusinessStatus.NEED_INPUT,
                XiaomingIntent.MATERIAL_CHECKLIST,
                message,
                Map.of("target", "material-checklist"),
                List.of(new XiaomingCard(
                        "checklist-generate-required",
                        "1.0",
                        CardType.QUESTION,
                        "打开动态资料清单",
                        Map.of("message", "请在清单面板选择年龄、投保关系、证件类型、受益人设置和健康状态。")
                )),
                List.of(),
                suggestions(),
                List.of(),
                List.of(),
                version,
                traceId,
                requestId,
                null
        );
        return new ConversationResult(HttpStatus.OK, response);
    }

    private static Map<XiaomingIntent, Set<DocumentType>> documentTypes() {
        Map<XiaomingIntent, Set<DocumentType>> result = new EnumMap<>(XiaomingIntent.class);
        result.put(XiaomingIntent.COVERAGE_RESPONSIBILITY, Set.of(DocumentType.POLICY_TERMS, DocumentType.APPLICATION_RULES));
        result.put(XiaomingIntent.EXCLUSIONS_AND_LIMITATIONS, Set.of(DocumentType.POLICY_TERMS));
        result.put(XiaomingIntent.APPLICATION_RULES, Set.of(DocumentType.APPLICATION_RULES, DocumentType.APPLICATION_GUIDE));
        result.put(XiaomingIntent.APPLICATION_PROCESS, Set.of(DocumentType.APPLICATION_GUIDE));
        result.put(XiaomingIntent.CLAIMS_PATH, Set.of(DocumentType.APPLICATION_GUIDE));
        return Map.copyOf(result);
    }

    private static Map<XiaomingIntent, Set<String>> documentIds() {
        Map<XiaomingIntent, Set<String>> result = new EnumMap<>(XiaomingIntent.class);
        result.put(XiaomingIntent.COVERAGE_RESPONSIBILITY, Set.of("doc-01", "doc-03", "doc-13"));
        result.put(XiaomingIntent.EXCLUSIONS_AND_LIMITATIONS, Set.of("doc-04"));
        result.put(XiaomingIntent.APPLICATION_RULES, Set.of(
                "doc-06", "doc-07", "doc-11", "doc-12", "doc-13", "doc-20"
        ));
        result.put(XiaomingIntent.APPLICATION_PROCESS, Set.of("doc-20"));
        result.put(XiaomingIntent.CLAIMS_PATH, Set.of("doc-07"));
        return Map.copyOf(result);
    }

    private List<ClauseChunk> selectIntentChunks(
            XiaomingIntent intent,
            String question,
            List<ClauseChunk> allChunks
    ) {
        List<ClauseChunk> allowed = allChunks.stream()
                .filter(chunk -> DOCUMENT_IDS.get(intent).contains(chunk.documentId()))
                .toList();
        if (intent == XiaomingIntent.CLAIMS_PATH) {
            List<ClauseChunk> claims = allowed.stream()
                    .filter(chunk -> {
                        String text = chunk.text().replaceAll("\\s+", "");
                        return text.contains("索赔程序") || text.contains("报案");
                    })
                    .toList();
            return claims.isEmpty() ? allowed : claims;
        }
        if (intent == XiaomingIntent.APPLICATION_PROCESS) {
            List<ClauseChunk> process = allowed.stream()
                    .filter(chunk -> {
                        String text = chunk.text().replaceAll("\\s+", "");
                        return text.contains("投保本保险前")
                                || text.contains("如实填写投保信息")
                                || text.contains("进行人脸识别或者上传自拍")
                                || text.contains("待支付");
                    })
                    .toList();
            return process.isEmpty() ? allowed : process;
        }
        if (intent != XiaomingIntent.COVERAGE_RESPONSIBILITY || !isCoverageOverviewQuestion(question)) {
            return allowed;
        }
        List<ClauseChunk> overview = allowed.stream()
                .filter(this::isCoverageOverviewChunk)
                .toList();
        return overview.isEmpty() ? allowed : overview;
    }

    private boolean isCoverageOverviewQuestion(String question) {
        return question.contains("主要保什么")
                || question.contains("保障责任是什么")
                || question.contains("保险责任是什么")
                || question.equals("保障责任")
                || question.equals("保险责任");
    }

    private boolean isCoverageOverviewChunk(ClauseChunk chunk) {
        String section = chunk.section().replaceAll("\\s+", "");
        String content = chunk.text().replaceAll("\\s+", "");
        if (chunk.documentId().equals("doc-13")) {
            return true;
        }
        if (!chunk.documentId().equals("doc-01")) {
            return false;
        }
        return section.startsWith("2.3保险责任")
                || content.contains("序号保险责任必选/可选");
    }

    private String canonicalQuery(XiaomingIntent intent, String question) {
        String expansion = switch (intent) {
            case COVERAGE_RESPONSIBILITY -> "保险责任 基本责任 可选责任 重大疾病 中症疾病 轻症疾病";
            case EXCLUSIONS_AND_LIMITATIONS -> "责任免除 免责 不承担保险责任";
            case APPLICATION_RULES -> "投保规则 投保年龄 保额 缴费期限 保障期限";
            case APPLICATION_PROCESS -> "投保前 阅读条款 确认责任 投保方案 如实填写 告知 人脸识别 核保 支付";
            case CLAIMS_PATH -> "索赔程序 报案 理赔 客户服务";
            default -> "";
        };
        return question + " " + expansion;
    }

    private record IntentAnswer(
            boolean success,
            String answer,
            List<XiaomingEvidence> evidence,
            List<String> ruleIds,
            String retrievalMode,
            boolean degraded,
            String failureReason
    ) {
        private static IntentAnswer success(
                String answer,
                List<XiaomingEvidence> evidence,
                List<String> ruleIds,
                String retrievalMode,
                boolean degraded
        ) {
            return new IntentAnswer(true, answer, evidence, ruleIds, retrievalMode, degraded, "");
        }

        private static IntentAnswer failure(String reason) {
            return new IntentAnswer(false, "", List.of(), List.of(), "", false, reason);
        }
    }
}
