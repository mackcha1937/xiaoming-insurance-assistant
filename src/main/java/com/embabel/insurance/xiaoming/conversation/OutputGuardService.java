package com.embabel.insurance.xiaoming.conversation;

import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class OutputGuardService {

    private static final Pattern NUMBER = Pattern.compile("\\d+(?:\\.\\d+)?%?");
    private static final Pattern FORBIDDEN = Pattern.compile(
            "(保证|一定|肯定).{0,8}(承保|核保|赔付|理赔)|"
                    + "(已经|可以).{0,6}(确诊|诊断)|治疗建议|用药建议|"
                    + "核保通过|确认拒保|实际赔付结论|https?://",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );

    public GuardedOutput validate(String answer, AnswerRequest request, int maxAnswerCharacters) {
        if (answer == null || answer.isBlank()) {
            return new GuardedOutput(false, "模型未返回可用内容。");
        }
        if (answer.length() > maxAnswerCharacters) {
            return new GuardedOutput(false, "模型回答超过安全长度限制。");
        }
        if (FORBIDDEN.matcher(answer).find()) {
            return new GuardedOutput(false, "模型回答包含禁止的绝对性或越界结论。");
        }
        Set<String> allowedNumbers = numbers(request.question());
        request.ruleResults().forEach(result -> allowedNumbers.addAll(numbers(result)));
        request.evidence().forEach(evidence -> {
            allowedNumbers.addAll(numbers(evidence.quote()));
            allowedNumbers.addAll(numbers(evidence.documentName()));
            allowedNumbers.addAll(numbers(evidence.section()));
            allowedNumbers.add(Integer.toString(evidence.pdfPage()));
            if (evidence.printedPage() != null) {
                allowedNumbers.add(Integer.toString(evidence.printedPage()));
            }
        });
        allowedNumbers.add("12");
        allowedNumbers.add("36743");
        Set<String> answerNumbers = numbers(answer);
        if (!allowedNumbers.containsAll(answerNumbers)) {
            return new GuardedOutput(false, "模型回答包含未经证据或规则支持的数字。");
        }
        return new GuardedOutput(true, "");
    }

    private Set<String> numbers(String value) {
        Set<String> result = new LinkedHashSet<>();
        if (value == null) {
            return result;
        }
        Matcher matcher = NUMBER.matcher(value);
        while (matcher.find()) {
            result.add(matcher.group());
        }
        return result;
    }

    public record GuardedOutput(boolean allowed, String reason) {
    }
}
