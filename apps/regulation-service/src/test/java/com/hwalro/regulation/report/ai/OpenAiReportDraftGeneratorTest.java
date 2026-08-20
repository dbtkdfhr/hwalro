package com.hwalro.regulation.report.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hwalro.regulation.report.client.SimulationReportContextClient.Context;
import com.hwalro.regulation.report.dto.ReportContent;
import com.hwalro.regulation.report.exception.ReportDraftGenerationException;
import java.util.List;
import org.junit.jupiter.api.Test;

class OpenAiReportDraftGeneratorTest {
    private final ReportDraftInput input =
            new ReportDraftInput(new Context(1L, 1L, "현재안", List.of(), List.of()), List.of(), List.of());

    @Test
    void returnsValidatedStructuredContent() {
        ReportContent expected = new ReportContent("개요", "분석", "개선");
        OpenAiReportDraftGenerator generator = new OpenAiReportDraftGenerator(
                new ReportPromptFactory(new com.fasterxml.jackson.databind.ObjectMapper()), prompt -> expected);

        assertThat(generator.generate(input)).isEqualTo(expected);
    }

    @Test
    void rejectsNullOrBlankStructuredContent() {
        assertThatThrownBy(() -> generatorReturning(null).generate(input))
                .isInstanceOf(ReportDraftGenerationException.class);
        assertThatThrownBy(() ->
                        generatorReturning(new ReportContent("", "분석", "개선")).generate(input))
                .isInstanceOf(ReportDraftGenerationException.class);
        assertThatThrownBy(() ->
                        generatorReturning(new ReportContent("개요", " ", "개선")).generate(input))
                .isInstanceOf(ReportDraftGenerationException.class);
        assertThatThrownBy(() ->
                        generatorReturning(new ReportContent("개요", "분석", null)).generate(input))
                .isInstanceOf(ReportDraftGenerationException.class);
    }

    @Test
    void wrapsProviderFailureWithoutExposingItsMessage() {
        OpenAiReportDraftGenerator generator = new OpenAiReportDraftGenerator(
                new ReportPromptFactory(new com.fasterxml.jackson.databind.ObjectMapper()), prompt -> {
                    throw new IllegalStateException("secret provider body");
                });

        assertThatThrownBy(() -> generator.generate(input))
                .isInstanceOf(ReportDraftGenerationException.class)
                .hasMessage("AI 보고서 초안을 생성할 수 없습니다.")
                .hasCauseInstanceOf(IllegalStateException.class);
    }

    private OpenAiReportDraftGenerator generatorReturning(ReportContent content) {
        return new OpenAiReportDraftGenerator(
                new ReportPromptFactory(new com.fasterxml.jackson.databind.ObjectMapper()), prompt -> content);
    }
}
