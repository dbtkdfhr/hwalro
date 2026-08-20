package com.hwalro.regulation.report.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.hwalro.regulation.report.dto.ReportContent;
import org.junit.jupiter.api.Test;
import org.springframework.ai.converter.BeanOutputConverter;

class ReportContentStructuredOutputTest {
    private final BeanOutputConverter<ReportContent> outputConverter = new BeanOutputConverter<>(ReportContent.class);

    @Test
    void exposesTheThreeReportSectionsInTheOutputSchema() {
        String format = outputConverter.getFormat();

        assertThat(format).contains("overview").contains("analysis").contains("improvements");
    }

    @Test
    void convertsStructuredJsonIntoReportContent() {
        String response =
                """
                {
                  "overview": "현재안과 비교안의 시뮬레이션 조건을 검토했다.",
                  "analysis": "현재안의 총 대피 시간이 비교안보다 짧았다.",
                  "improvements": "병목 구간의 유효 통로 폭 확보를 검토한다."
                }
                """;

        ReportContent content = outputConverter.convert(response);

        assertThat(content).isNotNull();
        assertThat(content.overview()).isEqualTo("현재안과 비교안의 시뮬레이션 조건을 검토했다.");
        assertThat(content.analysis()).isEqualTo("현재안의 총 대피 시간이 비교안보다 짧았다.");
        assertThat(content.improvements()).isEqualTo("병목 구간의 유효 통로 폭 확보를 검토한다.");
    }
}
