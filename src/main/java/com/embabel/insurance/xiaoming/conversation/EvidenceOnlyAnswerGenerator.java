package com.embabel.insurance.xiaoming.conversation;

import com.embabel.insurance.xiaoming.contract.XiaomingEvidence;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.stream.Collectors;

@Service
@ConditionalOnProperty(prefix = "xiaoming.conversation", name = "model-enabled", havingValue = "false", matchIfMissing = true)
public class EvidenceOnlyAnswerGenerator implements AnswerGenerator {

    @Override
    public String generate(AnswerRequest request) {
        String rules = request.ruleResults().isEmpty()
                ? ""
                : "规则结果：" + request.ruleResults().stream()
                        .map(this::humanReadableRule)
                        .collect(Collectors.joining("；")) + "。";
        String guidance = request.intent() == com.embabel.insurance.xiaoming.contract.XiaomingIntent.APPLICATION_PROCESS
                ? "投保前请先阅读正式条款和提示，确认责任与投保方案；随后如实填写投保及告知信息。"
                        + "如选择身故或全残责任，还需按要求完成被保险人同意和身份核验；提交核保后，审核通过再按页面时效支付。"
                : "";
        String sources = request.evidence().stream()
                .limit(2)
                .map(evidence -> summarize(request.intent(), evidence))
                .collect(Collectors.joining("；"));
        return rules + guidance + "根据正式资料：" + sources + "。请以展开的原文依据和适用条件为准。";
    }

    private String summarize(com.embabel.insurance.xiaoming.contract.XiaomingIntent intent, XiaomingEvidence evidence) {
        String quote = evidence.quote().replaceAll("\\s+", " ").trim();
        String anchor = switch (intent) {
            case COVERAGE_RESPONSIBILITY -> "2.3 保险责任";
            case EXCLUSIONS_AND_LIMITATIONS -> "责任免除";
            case APPLICATION_PROCESS -> "投保";
            case CLAIMS_PATH -> "索赔程序";
            default -> "";
        };
        int anchorIndex = anchor.isEmpty() ? -1 : quote.indexOf(anchor);
        if (anchorIndex > 0) {
            quote = quote.substring(anchorIndex);
        }
        if (quote.length() > 180) {
            quote = quote.substring(0, 180) + "…";
        }
        return evidence.documentName() + "第" + evidence.pdfPage() + "页载明：“" + quote + "”";
    }

    private String humanReadableRule(String result) {
        if (result.startsWith("MAX_BASIC_SUM_INSURED_CNY=")) {
            String[] values = result.split(";");
            String basic = "该年龄段基本保额上限为" + value(values[0]) + "元";
            return values.length > 1
                    ? basic + "，累计风险保额上限为" + value(values[1]) + "元"
                    : basic;
        }
        if (result.startsWith("MAX_INSURED_AGE_YEARS=")) {
            return "对应条件下被保险人最高年龄为" + value(result) + "周岁";
        }
        if (result.startsWith("WAITING_PERIOD_DAYS=")) {
            return "等待期为" + value(result) + "日";
        }
        if (result.startsWith("COOLING_OFF_DAYS=")) {
            return "犹豫期为" + value(result) + "日";
        }
        if (result.startsWith("CLAIMS_STEPS=")) {
            return "理赔步骤为报案、提交资料、审核和给付";
        }
        if (result.startsWith("TO_70_OPTIONAL_RESPONSIBILITIES=")) {
            return "保至70岁时，可选责任受正式投保规则限制";
        }
        return result;
    }

    private String value(String assignment) {
        int separator = assignment.indexOf('=');
        return separator < 0 ? assignment : assignment.substring(separator + 1);
    }
}
