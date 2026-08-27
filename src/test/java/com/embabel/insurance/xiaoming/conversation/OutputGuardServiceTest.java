package com.embabel.insurance.xiaoming.conversation;

import com.embabel.insurance.xiaoming.contract.XiaomingEvidence;
import com.embabel.insurance.xiaoming.contract.XiaomingIntent;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OutputGuardServiceTest {

    private final OutputGuardService service = new OutputGuardService();
    private final AnswerRequest request = new AnswerRequest(
            XiaomingIntent.APPLICATION_RULES,
            "30岁最高保额是多少",
            List.of("MAX_BASIC_SUM_INSURED_CNY=500000"),
            List.of(new XiaomingEvidence(
                    "evidence-1", "11-保额规则.pdf", "第2节 保额规则", 3, 1,
                    "0至40周岁累计基本保额不超过500000元。", "version-1"
            )),
            6000
    );

    @Test
    void shouldAllowOnlySupportedNumbersAndMetadataPages() {
        assertTrue(service.validate("11-保额规则第2节载明30岁适用500000元上限，见PDF第3页。", request, 1200).allowed());
        assertFalse(service.validate("规则载明上限为600000元。", request, 1200).allowed());
    }

    @Test
    void shouldRejectAbsoluteMedicalLinksAndOverlongAnswers() {
        assertFalse(service.validate("保证核保通过。", request, 1200).allowed());
        assertFalse(service.validate("治疗建议请访问https://example.com", request, 1200).allowed());
        assertFalse(service.validate("a".repeat(1201), request, 1200).allowed());
    }
}
