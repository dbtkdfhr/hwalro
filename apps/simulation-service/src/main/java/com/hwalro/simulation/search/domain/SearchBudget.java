package com.hwalro.simulation.search.domain;

/**
 * 탐색 예산. 이 레코드는 searches.budget JSON 칼럼에 그대로 저장된다.
 *
 * <p>{@code verify}는 뒤에 추가된 항목이라 이 칼럼을 쓴 기존 행에는 없다. null은 "확인함"으로 읽는다 - 그때
 * 저장된 탐색은 전부 실제 엔진으로 확인한 것들이다.
 */
public record SearchBudget(String preset, int maxTrials, int maxRounds, double trialCapSeconds, Boolean verify) {
    public boolean verifies() {
        return verify == null || verify;
    }
}
