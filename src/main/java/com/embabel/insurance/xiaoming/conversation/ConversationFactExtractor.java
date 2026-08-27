package com.embabel.insurance.xiaoming.conversation;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ConversationFactExtractor {

    private static final Set<String> ALLOWED_CONTEXT_FIELDS = Set.of(
            "applicantAgeYears",
            "insuredAgeYears",
            "coverageTerm",
            "paymentYears",
            "occupationClass",
            "diseaseCareSelected",
            "ruleQuery"
    );
    private static final Pattern AGE = Pattern.compile("(\\d{1,2})\\s*岁");
    private static final Pattern PAYMENT = Pattern.compile("(\\d{1,2})\\s*年(?:交|缴|缴费)");

    public Map<String, Object> extract(String message, Map<String, Object> context) {
        Map<String, Object> facts = new LinkedHashMap<>();
        if (context != null) {
            context.forEach((key, value) -> {
                if (ALLOWED_CONTEXT_FIELDS.contains(key) && scalar(value)) {
                    facts.put(key, value);
                }
            });
        }
        Matcher age = AGE.matcher(message);
        if (age.find()) {
            String field = message.contains("投保人") ? "applicantAgeYears" : "insuredAgeYears";
            facts.putIfAbsent(field, Integer.parseInt(age.group(1)));
        }
        Matcher payment = PAYMENT.matcher(message);
        if (payment.find()) {
            facts.putIfAbsent("paymentYears", Integer.parseInt(payment.group(1)));
        } else if (message.contains("趸交")) {
            facts.putIfAbsent("paymentYears", 0);
        }
        if (message.contains("保至70") || message.contains("保到70")) {
            facts.putIfAbsent("coverageTerm", "TO_70");
        } else if (message.contains("终身")) {
            facts.putIfAbsent("coverageTerm", "LIFETIME");
        }
        if (message.contains("等待期")) {
            facts.put("ruleQuery", "WAITING_PERIOD");
        } else if (message.contains("犹豫期")) {
            facts.put("ruleQuery", "COOLING_OFF_PERIOD");
        } else if (message.contains("理赔") || message.contains("索赔") || message.contains("报案")) {
            facts.put("ruleQuery", "CLAIMS_PATH");
        }
        return Map.copyOf(facts);
    }

    private boolean scalar(Object value) {
        return value instanceof String || value instanceof Number || value instanceof Boolean;
    }
}
