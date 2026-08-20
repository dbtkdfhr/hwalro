package com.hwalro.regulation.law.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hwalro.regulation.law.api.LawApiClient;
import com.hwalro.regulation.law.api.LawApiProperties;
import com.hwalro.regulation.law.dto.RelatedRegulation;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RegulationServiceRelatedLawTest {
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
    void returnsOfficialRelatedLaw() throws Exception {
        when(lawApiClient.searchRelatedLaws("001823"))
                .thenReturn(
                        objectMapper.readTree(
                                """
                                {
                                  "lsRltSearch": {
                                    "법령": {
                                      "관련법령": {
                                        "관련법령ID": "006189",
                                        "관련법령명": "건축물의 피난ㆍ방화구조 등의 기준에 관한 규칙",
                                        "법령간관계": "6유형(하위법)",
                                        "관련법령본문조회": "https://www.law.go.kr/법령/건축물의 피난ㆍ방화구조 등의 기준에 관한 규칙"
                                      }
                                    }
                                  }
                                }
                                """));

        assertThat(regulationService.getRelatedLaws("001823"))
                .singleElement()
                .extracting(RelatedRegulation::lawId, RelatedRegulation::name, RelatedRegulation::relationship)
                .containsExactly("006189", "건축물의 피난ㆍ방화구조 등의 기준에 관한 규칙", "6유형(하위법)");
    }
}
