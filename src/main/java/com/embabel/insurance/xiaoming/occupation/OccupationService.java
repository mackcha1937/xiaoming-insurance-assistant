package com.embabel.insurance.xiaoming.occupation;

import com.embabel.insurance.xiaoming.knowledge.importer.KnowledgeSnapshotStore;
import com.embabel.insurance.xiaoming.knowledge.importer.OccupationEntry;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class OccupationService {

    public static final int MAX_CANDIDATES = 5;

    private static final String PRODUCT_ID = "36743";
    private static final Pattern MULTIPLE_OCCUPATIONS = Pattern.compile(
            ".{2,}(?:同时|兼任|兼职|又做|以及|、|，|,|和).{2,}"
    );
    private static final Set<String> INSUFFICIENT_QUERIES = Set.of(
            "工作", "职业", "上班", "干活", "员工", "人员", "其他", "普通工作"
    );
    private static final Map<String, List<String>> SAFE_EXPANSIONS = Map.ofEntries(
            Map.entry("程序员", List.of("计算机程序设计员")),
            Map.entry("软件工程师", List.of("计算机软件工程技术人员")),
            Map.entry("写代码", List.of("计算机程序设计员")),
            Map.entry("老师", List.of("教师")),
            Map.entry("教书", List.of("教师")),
            Map.entry("出租车", List.of("出租车司机")),
            Map.entry("开出租车", List.of("出租车司机")),
            Map.entry("厨师", List.of("厨师")),
            Map.entry("快递员", List.of("快递人员"))
    );

    private final KnowledgeSnapshotStore snapshotStore;

    public OccupationService(KnowledgeSnapshotStore snapshotStore) {
        this.snapshotStore = snapshotStore;
    }

    public OccupationSearchResult search(
            String productId,
            String query,
            String knowledgeVersionHint,
            String queryToken
    ) {
        KnowledgeSnapshotStore.Snapshot snapshot = currentSnapshot(productId, knowledgeVersionHint);
        String normalizedQuery = normalize(query);
        String normalizedForBoundary = Normalizer.normalize(query, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .trim();
        if (MULTIPLE_OCCUPATIONS.matcher(normalizedForBoundary).matches()) {
            return needsMoreDetail(
                    queryToken,
                    snapshot,
                    "检测到多个职业。请一次输入一个职业并分别查询，系统不会自行合并或采用就高原则。"
            );
        }
        if (normalizedQuery.length() < 2 || INSUFFICIENT_QUERIES.contains(normalizedQuery)) {
            return needsMoreDetail(
                    queryToken,
                    snapshot,
                    "请补充行业、工作场所、使用工具，以及是否涉及高空、高压或驾驶等日常工作内容。"
            );
        }

        Set<String> searchTerms = new LinkedHashSet<>();
        searchTerms.add(normalizedQuery);
        SAFE_EXPANSIONS.forEach((alias, expansions) -> {
            if (normalizedQuery.contains(normalize(alias))) {
                expansions.stream().map(OccupationService::normalize).forEach(searchTerms::add);
            }
        });

        List<OccupationCandidate> candidates = snapshot.occupations().stream()
                .map(entry -> new ScoredEntry(entry, score(entry, searchTerms)))
                .filter(scored -> scored.score() >= 0.35d)
                .sorted(Comparator.comparingDouble(ScoredEntry::score).reversed()
                        .thenComparing(scored -> displayName(scored.entry()))
                        .thenComparing(scored -> scored.entry().occupationId()))
                .limit(MAX_CANDIDATES)
                .map(scored -> candidate(scored.entry()))
                .toList();

        if (candidates.isEmpty()) {
            return needsMoreDetail(
                    queryToken,
                    snapshot,
                    "暂未找到足够接近的职业候选。请补充具体行业、场所、工具和日常工作内容后重新查询。"
            );
        }
        return new OccupationSearchResult(
                queryToken,
                candidates,
                false,
                candidates.size() == 1
                        ? "找到一个待确认职业。确认前不会显示职业类别或可承保结论。"
                        : "找到多个候选，请选择最符合实际工作内容的一项后确认。",
                snapshot.knowledgeVersion().versionId(),
                snapshot.knowledgeVersion().occupationVersion()
        );
    }

    public OccupationConfirmationResult confirm(
            String productId,
            String occupationId,
            String knowledgeVersionHint
    ) {
        KnowledgeSnapshotStore.Snapshot snapshot = currentSnapshot(productId, knowledgeVersionHint);
        return snapshot.occupations().stream()
                .filter(entry -> entry.occupationId().equals(occupationId))
                .findFirst()
                .map(entry -> new OccupationConfirmationResult(
                        true,
                        entry.occupationId(),
                        displayName(entry),
                        entry.classCode(),
                        path(entry),
                        snapshot.knowledgeVersion().versionId(),
                        snapshot.knowledgeVersion().occupationVersion(),
                        "该结果仅表示当前职业表中的精确分类，不代表保险公司最终承保结论；最终以正式投保页面和保险公司审核为准。"
                ))
                .orElseGet(() -> new OccupationConfirmationResult(
                        false,
                        occupationId,
                        "",
                        null,
                        List.of(),
                        snapshot.knowledgeVersion().versionId(),
                        snapshot.knowledgeVersion().occupationVersion(),
                        "该 occupationId 不在当前职业表中，请重新查询并确认候选；最终以正式投保页面为准。"
                ));
    }

    private KnowledgeSnapshotStore.Snapshot currentSnapshot(String productId, String knowledgeVersionHint) {
        if (!PRODUCT_ID.equals(productId)) {
            throw new IllegalArgumentException("当前仅支持产品 36743");
        }
        KnowledgeSnapshotStore.Snapshot snapshot = snapshotStore.current().orElseThrow(() ->
                new OccupationServiceException(
                        OccupationServiceException.Reason.UNAVAILABLE,
                        "当前正式职业表尚未发布或加载。",
                        null
                )
        );
        String currentVersion = snapshot.knowledgeVersion().versionId();
        if (knowledgeVersionHint != null
                && !knowledgeVersionHint.isBlank()
                && !knowledgeVersionHint.equals(currentVersion)) {
            throw new OccupationServiceException(
                    OccupationServiceException.Reason.VERSION_CONFLICT,
                    "职业查询所用知识版本已变化，请重新搜索并确认。",
                    currentVersion
            );
        }
        if (!PRODUCT_ID.equals(snapshot.knowledgeVersion().productId())
                || snapshot.occupations().isEmpty()
                || snapshot.knowledgeVersion().occupationVersion() == null
                || snapshot.knowledgeVersion().occupationVersion().isBlank()) {
            throw new OccupationServiceException(
                    OccupationServiceException.Reason.UNAVAILABLE,
                    "当前正式职业表不可用。",
                    currentVersion
            );
        }
        return snapshot;
    }

    private OccupationSearchResult needsMoreDetail(
            String queryToken,
            KnowledgeSnapshotStore.Snapshot snapshot,
            String prompt
    ) {
        return new OccupationSearchResult(
                queryToken,
                List.of(),
                true,
                prompt,
                snapshot.knowledgeVersion().versionId(),
                snapshot.knowledgeVersion().occupationVersion()
        );
    }

    private static double score(OccupationEntry entry, Set<String> searchTerms) {
        String name = normalize(displayName(entry));
        String fullPath = normalize(String.join(" ", path(entry)));
        double best = 0.0d;
        for (String term : searchTerms) {
            if (name.equals(term)) {
                best = Math.max(best, 10.0d);
            } else if (name.startsWith(term)) {
                best = Math.max(best, 8.0d);
            } else if (name.contains(term)) {
                best = Math.max(best, 7.0d);
            } else if (fullPath.contains(term)) {
                best = Math.max(best, 6.0d);
            } else if (term.length() >= 3 && term.contains(name)) {
                best = Math.max(best, 5.0d);
            }
            best = Math.max(best, bigramDice(term, fullPath));
        }
        return best;
    }

    private static double bigramDice(String left, String right) {
        Set<String> leftBigrams = bigrams(left);
        Set<String> rightBigrams = bigrams(right);
        if (leftBigrams.isEmpty() || rightBigrams.isEmpty()) {
            return 0.0d;
        }
        long intersection = leftBigrams.stream().filter(rightBigrams::contains).count();
        double dice = (2.0d * intersection) / (leftBigrams.size() + rightBigrams.size());
        return dice >= 0.35d ? dice : 0.0d;
    }

    private static Set<String> bigrams(String value) {
        Set<String> result = new LinkedHashSet<>();
        for (int index = 0; index + 1 < value.length(); index++) {
            result.add(value.substring(index, index + 2));
        }
        return result;
    }

    private static OccupationCandidate candidate(OccupationEntry entry) {
        return new OccupationCandidate(entry.occupationId(), displayName(entry), path(entry));
    }

    private static String displayName(OccupationEntry entry) {
        return entry.fourthCategory() == null || entry.fourthCategory().isBlank()
                ? entry.minorCategory()
                : entry.fourthCategory();
    }

    private static List<String> path(OccupationEntry entry) {
        List<String> result = new ArrayList<>();
        addDistinct(result, entry.bigCategory());
        addDistinct(result, entry.secondCategory());
        addDistinct(result, entry.minorCategory());
        addDistinct(result, entry.fourthCategory());
        return List.copyOf(result);
    }

    private static void addDistinct(List<String> values, String value) {
        if (value != null && !value.isBlank() && !values.contains(value)) {
            values.add(value);
        }
    }

    static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return Normalizer.normalize(value, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[\\p{P}\\p{Z}\\s]+", "")
                .trim();
    }

    private record ScoredEntry(OccupationEntry entry, double score) {
    }
}
