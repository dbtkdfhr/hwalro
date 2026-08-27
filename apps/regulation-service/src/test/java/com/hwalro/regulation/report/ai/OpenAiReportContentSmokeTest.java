package com.hwalro.regulation.report.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.hwalro.regulation.report.client.SimulationReportContextClient.Bottleneck;
import com.hwalro.regulation.report.client.SimulationReportContextClient.Context;
import com.hwalro.regulation.report.client.SimulationReportContextClient.Metric;
import com.hwalro.regulation.report.dto.ReportContent;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;

@Tag("openai")
class OpenAiReportContentSmokeTest {
    private static final String DEFAULT_MODEL = "gpt-5.4-nano";

    @Test
    void generatesStructuredReportContentFromSimulationFacts() {
        String apiKey = requireEnvironmentVariable("OPENAI_API_KEY");
        String modelName = System.getenv().getOrDefault("OPENAI_MODEL", DEFAULT_MODEL);
        int maxCompletionTokens =
                Integer.parseInt(System.getenv().getOrDefault("OPENAI_MAX_COMPLETION_TOKENS", "1800"));
        OpenAiApi openAiApi = OpenAiApi.builder().apiKey(apiKey).build();
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(modelName)
                .maxCompletionTokens(maxCompletionTokens)
                .reasoningEffort("none")
                .build();
        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(options)
                .build();

        ReportPromptFactory.Prompt prompt =
                new ReportPromptFactory(new com.fasterxml.jackson.databind.ObjectMapper()).create(createInput());
        ReportContent content = ChatClient.create(chatModel)
                .prompt()
                .system(prompt.system())
                .user(prompt.user())
                .call()
                .entity(ReportContent.class);

        assertThat(content).isNotNull();
        assertThat(content.overview()).isNotBlank();
        assertThat(content.analysis())
                .contains("264", "302")
                .doesNotContain("TOTAL_EVACUATION_TIME", "MAX_DENSITY", "PERSON_PER_M2");
        assertThat(content.improvements()).isNotBlank();

        System.out.printf(
                """

                === %s AI 보고서 초안 ===
                [검토 개요]
                %s

                [핵심 분석 결과]
                %s

                [개선 조치]
                %s
                ================================
                """,
                modelName, content.overview(), content.analysis(), content.improvements());
    }

    private ReportDraftInput createInput() {
        Context source = new Context(
                9301L,
                9201L,
                9101L,
                "더현대 서울 B2 팝업 행사장 현재 배치안",
                List.of(
                        new Metric("TOTAL_EVACUATION_TIME", 264, "SECOND"),
                        new Metric("MAX_DENSITY", 4.8, "PERSON_PER_M2"),
                        new Metric("TOTAL_PEOPLE", 100, "PERSON"),
                        new Metric("EVACUATED_PEOPLE", 100, "PERSON"),
                        new Metric("BOTTLENECK_COUNT", 2, "COUNT")),
                List.of(new Bottleneck(1, 68, 140, 4.8, 3.5), new Bottleneck(2, 148, 206, 4.1, 3.5)));
        Context comparison = new Context(
                9302L,
                9202L,
                9102L,
                "중앙 통로 확장 배치안",
                List.of(
                        new Metric("TOTAL_EVACUATION_TIME", 302, "SECOND"),
                        new Metric("MAX_DENSITY", 5.2, "PERSON_PER_M2")),
                List.of());
        return new ReportDraftInput(
                source,
                List.of(comparison),
                List.of(
                        new ReportDraftInput.Risk(9101L, "중앙 행사 집기 인접 구역", "사용자 지정", "HIGH"),
                        new ReportDraftInput.Risk(9101L, "남측 출구 대기 구역", "사용자 지정", "MEDIUM")));
    }

    private String requireEnvironmentVariable(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " 환경 변수가 필요합니다.");
        }
        return value;
    }
}
