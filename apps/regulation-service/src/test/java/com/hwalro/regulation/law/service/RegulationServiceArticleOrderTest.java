package com.hwalro.regulation.law.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hwalro.regulation.law.api.LawApiClient;
import com.hwalro.regulation.law.api.LawApiProperties;
import com.hwalro.regulation.law.dto.RegulationDetail;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RegulationServiceArticleOrderTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private LawApiClient lawApiClient;

    private RegulationService regulationService;

    @BeforeEach
    void setUp() {
        regulationService = new RegulationService(
                lawApiClient, new LawApiProperties("https://www.law.go.kr", "test", List.of("소방")));
    }

    @Test
    void putsArticleTextBeforeNestedParagraphsAndKeepsDeletedArticleAsArticle() throws Exception {
        when(lawApiClient.getCurrentLaw("1"))
                .thenReturn(
                        objectMapper.readTree(
                                """
                                {
                                  "법령": {
                                    "기본정보": {
                                      "법령ID": "1",
                                      "법령명_한글": "테스트 법령",
                                      "법종구분": { "content": "법률" },
                                      "소관부처": { "content": "소방청" }
                                    },
                                    "조문": {
                                      "조문단위": [
                                        {
                                          "조문번호": "2",
                                          "조문제목": "",
                                          "항": { "항": [{ "항내용": "① 세부 내용" }] },
                                          "조문내용": "제2조(적용 범위) 본문",
                                          "조문시행일자": "20260101"
                                        },
                                        {
                                          "조문번호": "3",
                                          "조문제목": "",
                                          "조문내용": "제3조 삭제",
                                          "조문시행일자": "20260101"
                                        }
                                      ]
                                    }
                                  }
                                }
                                """));

        RegulationDetail response = regulationService.getDetail("1");

        assertThat(response.articles().get(0).content()).isEqualTo("제2조(적용 범위) 본문\n① 세부 내용");
        assertThat(response.articles().get(1).section()).isFalse();
    }
}
