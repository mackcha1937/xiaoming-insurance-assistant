package com.embabel.insurance.xiaoming;

import com.embabel.insurance.xiaoming.contract.ActionType;
import com.embabel.insurance.xiaoming.contract.BusinessStatus;
import com.embabel.insurance.xiaoming.contract.CardType;
import com.embabel.insurance.xiaoming.contract.XiaomingErrorCode;
import com.embabel.insurance.xiaoming.contract.XiaomingIntent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;

class ContractSourceTest {

    private static JsonNode contract;

    @BeforeAll
    static void loadContract() throws IOException {
        contract = new ObjectMapper().readTree(Path.of("contracts/xiaoming-contract.json").toFile());
    }

    @Test
    void shouldMatchBusinessStatuses() {
        assertThat(enumNames(BusinessStatus.values()))
                .containsExactlyInAnyOrderElementsOf(textValues(contract.get("statuses")));
    }

    @Test
    void shouldMatchIntentCodesAndLabels() {
        Map<String, String> source = StreamSupport.stream(contract.get("intents").spliterator(), false)
                .collect(Collectors.toMap(node -> node.get("code").asText(), node -> node.get("label").asText()));
        Map<String, String> javaValues = Arrays.stream(XiaomingIntent.values())
                .collect(Collectors.toMap(Enum::name, XiaomingIntent::getLabel));

        assertThat(javaValues).containsExactlyInAnyOrderEntriesOf(source);
        assertThat(javaValues).hasSize(9);
    }

    @Test
    void shouldMatchErrorCodesAndHttpStatuses() {
        Map<String, Integer> source = StreamSupport.stream(contract.get("errorCodes").spliterator(), false)
                .collect(Collectors.toMap(node -> node.get("code").asText(), node -> node.get("httpStatus").asInt()));
        Map<String, Integer> javaValues = Arrays.stream(XiaomingErrorCode.values())
                .collect(Collectors.toMap(Enum::name, code -> code.getHttpStatus().value()));

        assertThat(javaValues).containsExactlyInAnyOrderEntriesOf(source);
    }

    @Test
    void shouldMatchFoundationActionAndCardTypes() {
        assertThat(enumNames(ActionType.values()))
                .containsExactlyInAnyOrderElementsOf(textValues(contract.get("actionTypes")));
        assertThat(enumNames(CardType.values()))
                .containsExactlyInAnyOrderElementsOf(textValues(contract.get("cardTypes")));
    }

    @Test
    void shouldFreezeProductAndBasePath() {
        assertThat(contract.get("apiBasePath").asText()).isEqualTo("/api/v1/xiaoming");
        assertThat(contract.at("/product/productId").asText()).isEqualTo("36743");
        assertThat(contract.at("/product/name").asText()).contains("达尔文12号");
    }

    private static Set<String> textValues(JsonNode array) {
        return StreamSupport.stream(array.spliterator(), false)
                .map(JsonNode::asText)
                .collect(Collectors.toSet());
    }

    private static Set<String> enumNames(Enum<?>[] values) {
        return Arrays.stream(values).map(Enum::name).collect(Collectors.toSet());
    }
}
