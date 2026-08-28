package com.embabel.insurance.xiaoming.conversation;

import com.embabel.insurance.xiaoming.api.ChatRequest;
import com.embabel.insurance.xiaoming.contract.BusinessStatus;
import com.embabel.insurance.xiaoming.contract.XiaomingIntent;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("e2e")
@EnabledIfEnvironmentVariable(named = "DEEPSEEK_API_KEY", matches = ".+")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = {
        "spring.main.web-application-type=none",
        "embabel.agent.shell.interactive.enabled=false",
        "xiaoming.conversation.model-enabled=true",
        "xiaoming.knowledge-import.enabled=true",
        "xiaoming.knowledge-import.source-directory=docs/source-materials/product-36743/raw",
        "xiaoming.knowledge-import.spreadsheet-scan-mode=STRUCTURE_ONLY"
})
class XiaomingDeepSeekLiveE2ETest {

    @Autowired
    private XiaomingConversationService conversationService;

    @Test
    void answersOneEvidenceBoundedClaimsQuestionThroughRealDeepSeek() {
        ChatRequest request = new ChatRequest();
        request.setProductId("36743");
        request.setConversationId("conversation-live-e2e");
        request.setMessage("发生保险事故后如何报案？");
        request.setContext(Map.of());

        ConversationResult result = conversationService.answer(
                request,
                "trace-live-e2e",
                "request-live-e2e"
        );

        assertEquals(BusinessStatus.SUCCESS, result.response().status());
        assertEquals(XiaomingIntent.CLAIMS_PATH, result.response().intent());
        assertNull(result.response().error());
        assertFalse(result.response().cards().isEmpty());
        assertEquals(1, result.response().evidence().size());
        assertTrue(result.response().evidence().getFirst().documentName().contains("投保须知书"));
        assertEquals(5, result.response().evidence().getFirst().pdfPage());
        String answer = String.valueOf(result.response().cards().getFirst().data().get("answer"));
        assertTrue(answer.contains("报案"));
        assertTrue(answer.contains("提交"));
        assertTrue(answer.contains("审核"));
        assertTrue(answer.contains("支付") || answer.contains("给付"));
        assertFalse(answer.contains("一定赔") || answer.contains("保证赔"));
    }
}
