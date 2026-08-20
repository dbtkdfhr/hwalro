package com.hwalro.simulation.search.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class SearchBudgetTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void a_budget_saved_before_the_flag_existed_still_counts_as_verified() throws Exception {
        // searches.budget에 이미 저장된 JSON에는 verify가 없다. 그때 돌린 탐색은 전부 실제 엔진으로
        // 확인한 것들이라, 없는 값을 "확인 안 함"으로 읽으면 지난 결과의 뜻이 바뀐다.
        String stored = "{\"preset\":\"STANDARD\",\"maxTrials\":6,\"maxRounds\":2,\"trialCapSeconds\":120.0}";

        SearchBudget budget = objectMapper.readValue(stored, SearchBudget.class);

        assertThat(budget.verify()).isNull();
        assertThat(budget.verifies()).isTrue();
    }

    @Test
    void an_explicit_flag_is_kept_through_a_save_and_load() throws Exception {
        for (boolean verify : new boolean[] {true, false}) {
            SearchBudget saved = new SearchBudget("STANDARD", 6, 2, 120.0, verify);

            SearchBudget loaded = objectMapper.readValue(objectMapper.writeValueAsString(saved), SearchBudget.class);

            assertThat(loaded.verifies()).isEqualTo(verify);
        }
    }
}
