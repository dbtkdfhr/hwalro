package com.hwalro.regulation.report.ai;

import com.hwalro.regulation.report.dto.ReportContent;
import com.hwalro.regulation.report.exception.ReportDraftGenerationException;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!ai")
public class UnavailableReportDraftGenerator implements ReportDraftGenerator {
    @Override
    public ReportContent generate(ReportDraftInput input) {
        throw new ReportDraftGenerationException("AI 프로필이 활성화되지 않았습니다.");
    }
}
