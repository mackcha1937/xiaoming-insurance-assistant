package com.embabel.insurance.xiaoming.conversation;

import com.embabel.insurance.xiaoming.contract.XiaomingEvidence;
import com.embabel.insurance.xiaoming.contract.XiaomingIntent;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeepSeekAnswerGeneratorTest {

    @Test
    void shouldBuildBoundedEvidenceOnlyPromptWithoutConversationMetadata() {
        AnswerRequest request = new AnswerRequest(
                XiaomingIntent.CLAIMS_PATH,
                "发生保险事故后如何报案？",
                List.of("CLAIMS_STEPS=REPORT,SUBMIT,REVIEW,PAY"),
                List.of(
                        new XiaomingEvidence(
                                "evidence-1",
                                "07-投保须知书.pdf",
                                "索赔程序",
                                5,
                                5,
                                "索赔程序：理赔报案、提交理赔申请资料、理赔审核、保险金支付。",
                                "knowledge-36743-test"
                        ),
                        new XiaomingEvidence(
                                "evidence-2",
                                "unrelated.pdf",
                                "无关章节",
                                1,
                                1,
                                "这一段不应在很小的证据预算下完整出现。",
                                "knowledge-36743-test"
                        )
                ),
                120
        );

        String prompt = DeepSeekAnswerGenerator.buildUserPrompt(request);

        assertTrue(prompt.contains("意图：理赔路径"));
        assertTrue(prompt.contains("用户问题：发生保险事故后如何报案？"));
        assertTrue(prompt.contains("CLAIMS_STEPS=REPORT,SUBMIT,REVIEW,PAY"));
        assertTrue(prompt.contains("07-投保须知书.pdf"));
        assertFalse(prompt.contains("conversationId"));
        assertFalse(prompt.contains("requestId"));
        assertFalse(prompt.contains("API_KEY"));
        assertTrue(prompt.length() < 420);
    }
}
