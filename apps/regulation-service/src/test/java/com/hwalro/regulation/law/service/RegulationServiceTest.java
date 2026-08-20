package com.hwalro.regulation.law.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hwalro.regulation.law.api.LawApiClient;
import com.hwalro.regulation.law.api.LawApiProperties;
import com.hwalro.regulation.law.dto.RegulationArticle;
import com.hwalro.regulation.law.dto.RegulationDetail;
import com.hwalro.regulation.law.dto.RegulationSearchResponse;
import com.hwalro.regulation.law.dto.RegulationSummary;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
/** 외부 API 호출 없이 법령 응답 변환과 기본 목록 병합 규칙을 확인한다. */
class RegulationServiceTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private LawApiClient lawApiClient;

    private RegulationService regulationService;

    @BeforeEach
    void setUp() {
        LawApiProperties properties = new LawApiProperties("https://www.law.go.kr", "test", List.of("소방", "피난"));
        regulationService = new RegulationService(lawApiClient, properties, Runnable::run);
    }

    @Test
    void returnsCurrentLawSearchResults() throws Exception {
        when(lawApiClient.searchCurrentLaws("소방", 1, 20))
                .thenReturn(
                        objectMapper.readTree(
                                """
                                {
                                  "LawSearch": {
                                    "totalCnt": 1,
                                    "law": {
                                      "법령일련번호": "283705",
                                      "법령ID": "014189",
                                      "법령명한글": "화재의 예방 및 안전관리에 관한 법률",
                                      "법령구분명": "법률",
                                      "소관부처명": "소방청",
                                      "시행일자": "20260227"
                                    }
                                  }
                                }
                                """));

        RegulationSearchResponse response = regulationService.search("소방", 1, 20);

        assertThat(response.totalCount()).isEqualTo(1);
        assertThat(response.hasNext()).isFalse();
        assertThat(response.items())
                .singleElement()
                .extracting(RegulationSummary::serialNumber, RegulationSummary::name)
                .containsExactly("283705", "화재의 예방 및 안전관리에 관한 법률");
    }

    @Test
    void mergesDefaultSafetyLawResultsWithoutDuplicates() throws Exception {
        when(lawApiClient.searchCurrentLaws(anyString(), anyInt(), anyInt()))
                .thenReturn(
                        objectMapper.readTree(
                                """
                                {
                                  "LawSearch": {
                                    "law": [
                                      {
                                        "법령일련번호": "2",
                                        "법령ID": "2",
                                        "법령명한글": "피난 법령",
                                        "법령구분명": "법률",
                                        "소관부처명": "소방청",
                                        "시행일자": "20260101"
                                      },
                                      {
                                        "법령일련번호": "1",
                                        "법령ID": "1",
                                        "법령명한글": "소방 법령",
                                        "법령구분명": "법률",
                                        "소관부처명": "소방청",
                                        "시행일자": "20260101"
                                      }
                                    ]
                                  }
                                }
                                """));

        RegulationSearchResponse response = regulationService.search(null, 1, 20);

        assertThat(response.totalCount()).isEqualTo(2);
        assertThat(response.items()).extracting(RegulationSummary::serialNumber).containsExactly("1", "2");
    }

    @Test
    void returnsLawArticlesAndMarksChapterHeadersAsSections() throws Exception {
        when(lawApiClient.getCurrentLaw("283705"))
                .thenReturn(
                        objectMapper.readTree(
                                """
                                {
                                  "법령": {
                                    "기본정보": {
                                      "법령ID": "014189",
                                      "법령명_한글": "화재의 예방 및 안전관리에 관한 법률",
                                      "법종구분": { "content": "법률" },
                                      "소관부처": { "content": "소방청" },
                                      "공포일자": "20260227",
                                      "시행일자": "20260227"
                                    },
                                    "조문": {
                                      "조문단위": [
                                        {
                                          "조문번호": "",
                                          "조문제목": "",
                                          "조문내용": "제1장 총칙",
                                          "조문시행일자": "20260227"
                                        },
                                        {
                                          "조문번호": "1",
                                          "조문제목": "목적",
                                          "조문내용": "제1조(목적) 이 법은 화재를 예방한다.",
                                          "항": {
                                            "항": [
                                              { "항내용": "① 세부 내용을 정한다." }
                                            ]
                                          },
                                          "조문시행일자": "20260227"
                                        }
                                      ]
                                    }
                                  }
                                }
                                """));

        RegulationDetail response = regulationService.getDetail("283705");

        assertThat(response.name()).isEqualTo("화재의 예방 및 안전관리에 관한 법률");
        assertThat(response.lawType()).isEqualTo("법률");
        assertThat(response.competentAuthority()).isEqualTo("소방청");
        assertThat(response.articles()).hasSize(2);
        assertThat(response.articles().get(0))
                .extracting(RegulationArticle::section, RegulationArticle::content)
                .containsExactly(true, "제1장 총칙");
        assertThat(response.articles().get(1).content()).isEqualTo("제1조(목적) 이 법은 화재를 예방한다.\n① 세부 내용을 정한다.");
    }
}
