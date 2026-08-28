package com.embabel.insurance.xiaoming.guardrail;

import com.embabel.insurance.xiaoming.contract.XiaomingIntent;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class IntentGuardService {

    private static final Set<XiaomingIntent> ACTIVE_INTENTS = Set.of(
            XiaomingIntent.COVERAGE_RESPONSIBILITY,
            XiaomingIntent.EXCLUSIONS_AND_LIMITATIONS,
            XiaomingIntent.APPLICATION_RULES,
            XiaomingIntent.OCCUPATION_QUERY,
            XiaomingIntent.APPLICATION_PROCESS,
            XiaomingIntent.MATERIAL_CHECKLIST,
            XiaomingIntent.CLAIMS_PATH
    );

    private static final List<Pattern> INJECTION_PATTERNS = List.of(
            pattern("忽略.{0,12}(规则|指令|提示词|系统消息)"),
            pattern("(泄露|展示|输出|复述).{0,10}(系统提示词|system prompt|开发者消息)"),
            pattern("(角色扮演|开发者模式|越狱|jailbreak|do anything now)"),
            pattern("(不要|无需).{0,8}(遵守|执行).{0,8}(规则|限制|白名单)"),
            pattern("<script|begin system|\\[system\\]|### system")
    );

    private static final List<Pattern> OUT_OF_SCOPE_PATTERNS = List.of(
            pattern("(推荐|比较|对比|哪款).{0,12}(保险|重疾险|产品)"),
            pattern("(车险|汽车保险|保单查询|续期|退保|真实报价|保费试算|支付)"),
            pattern("(确诊|诊断|治疗|用药|手术方案|医学建议)"),
            pattern("(一定|保证|肯定).{0,8}(承保|核保|赔付|理赔|拒保)"),
            pattern("(能不能|是否会|会不会).{0,6}(承保|核保通过|赔下来|拒保)"),
            pattern("(赔不赔|能赔吗|肯定赔|实际赔付结论)")
    );

    private final Map<XiaomingIntent, List<String>> keywords = new LinkedHashMap<>();

    public IntentGuardService() {
        keywords.put(XiaomingIntent.COVERAGE_RESPONSIBILITY, List.of(
                "保什么", "保障什么", "保障责任", "基本责任", "可选责任", "轻症", "中症", "重疾",
                "疾病关爱", "赔付比例", "赔付次数", "身故责任"
        ));
        keywords.put(XiaomingIntent.EXCLUSIONS_AND_LIMITATIONS, List.of(
                "免责", "责任免除", "不承担", "不赔情况", "未达到", "等待期限制", "除外责任"
        ));
        keywords.put(XiaomingIntent.APPLICATION_RULES, List.of(
                "投保规则", "投保条件", "年龄", "保额", "缴费", "保障期", "投保人",
                "被保人", "销售地区", "犹豫期", "等待期"
        ));
        keywords.put(XiaomingIntent.OCCUPATION_QUERY, List.of("职业", "工种", "工作内容"));
        keywords.put(XiaomingIntent.APPLICATION_PROCESS, List.of(
                "投保流程", "怎么投保", "如何投保", "投保步骤", "投保前检查", "投保前"
        ));
        keywords.put(XiaomingIntent.MATERIAL_CHECKLIST, List.of("资料清单", "需要什么资料", "材料清单"));
        keywords.put(XiaomingIntent.HEALTH_DISCLOSURE, List.of("健康告知", "健康异常", "病史告知"));
        keywords.put(XiaomingIntent.CLAIMS_PATH, List.of(
                "理赔路径", "怎么理赔", "如何理赔", "报案", "索赔", "客服电话", "康有唯"
        ));
        keywords.put(XiaomingIntent.FORMAL_APPLICATION, List.of(
                "正式投保", "投保链接", "立即投保", "投保入口"
        ));
    }

    public IntentGuardDecision evaluate(String message) {
        String normalized = normalize(message);
        if (INJECTION_PATTERNS.stream().anyMatch(pattern -> pattern.matcher(normalized).find())) {
            return new IntentGuardDecision(
                    IntentGuardStatus.REFUSED,
                    List.of(),
                    List.of(),
                    "检测到试图绕过限定域或索取系统指令的内容。"
            );
        }

        List<XiaomingIntent> detected = keywords.entrySet().stream()
                .filter(entry -> entry.getValue().stream().anyMatch(normalized::contains))
                .map(Map.Entry::getKey)
                .toList();
        List<XiaomingIntent> allowed = detected.stream().filter(ACTIVE_INTENTS::contains).toList();
        List<XiaomingIntent> unavailable = detected.stream().filter(intent -> !ACTIVE_INTENTS.contains(intent)).toList();
        boolean refusedPart = OUT_OF_SCOPE_PATTERNS.stream().anyMatch(pattern -> pattern.matcher(normalized).find());

        if (refusedPart && onlyIncidentalCoverageKeyword(normalized, allowed)) {
            allowed = List.of();
        }

        if (!allowed.isEmpty() && (refusedPart || !unavailable.isEmpty())) {
            return new IntentGuardDecision(
                    IntentGuardStatus.MIXED,
                    allowed,
                    unavailable,
                    refusedPart ? "问题中包含超出九类能力或要求确定性核保、医疗、赔付结论的部分。" : "部分能力将在后续阶段开放。"
            );
        }
        if (!allowed.isEmpty()) {
            return new IntentGuardDecision(IntentGuardStatus.ALLOWED, allowed, List.of(), "");
        }
        if (!unavailable.isEmpty()) {
            return new IntentGuardDecision(
                    IntentGuardStatus.NOT_AVAILABLE,
                    List.of(),
                    unavailable,
                    "该能力属于产品范围，但将在后续阶段开放。"
            );
        }
        return new IntentGuardDecision(
                IntentGuardStatus.REFUSED,
                List.of(),
                List.of(),
                refusedPart ? "该问题要求了超出产品助手边界的结论。" : "问题不属于达尔文12号的九类限定域能力。"
        );
    }

    private static Pattern pattern(String expression) {
        return Pattern.compile(expression, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    }

    private boolean onlyIncidentalCoverageKeyword(String normalized, List<XiaomingIntent> allowed) {
        if (!allowed.equals(List.of(XiaomingIntent.COVERAGE_RESPONSIBILITY))) {
            return false;
        }
        return List.of("保什么", "保障", "责任", "赔付", "次数")
                .stream()
                .noneMatch(normalized::contains);
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .trim();
        List<Character> safe = new ArrayList<>(normalized.length());
        for (char character : normalized.toCharArray()) {
            if (!Character.isISOControl(character)) {
                safe.add(character);
            }
        }
        StringBuilder result = new StringBuilder(safe.size());
        safe.forEach(result::append);
        return result.toString();
    }
}
